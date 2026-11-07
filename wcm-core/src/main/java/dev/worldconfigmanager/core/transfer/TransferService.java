package dev.worldconfigmanager.core.transfer;

import dev.worldconfigmanager.api.ConfigKey;
import dev.worldconfigmanager.api.ConfigValue;
import dev.worldconfigmanager.core.OperationException;
import dev.worldconfigmanager.core.compat.CompatibilityChecker;
import dev.worldconfigmanager.core.compat.CompatibilityIssue;
import dev.worldconfigmanager.core.compat.CompatibilityReport;
import dev.worldconfigmanager.core.config.DefinitionLookup;
import dev.worldconfigmanager.core.profile.Profile;
import dev.worldconfigmanager.core.profile.ProfileService;

import java.time.Clock;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

/** Export of profiles to .wcmp packages and preview/commit of imports. */
public final class TransferService {
	private final ProfileService profiles;
	private final DefinitionLookup lookup;
	private final String minecraftVersion;
	private final Clock clock;

	public TransferService(ProfileService profiles, DefinitionLookup lookup, String minecraftVersion, Clock clock) {
		this.profiles = profiles;
		this.lookup = lookup;
		this.minecraftVersion = minecraftVersion;
		this.clock = clock;
	}

	public byte[] exportProfiles(Collection<UUID> ids) throws OperationException {
		List<Profile> list = new ArrayList<>();
		for (UUID id : ids) {
			list.add(profiles.require(id));
		}
		return write(list, "profiles");
	}

	/** Exports the given profile objects as they are (used for redacted copies). */
	public byte[] exportProfileCopies(List<Profile> copies) throws OperationException {
		return write(copies, "profiles");
	}

	/** Exports only the selected keys of a profile, as a new profile with a fresh id. */
	public byte[] exportSelection(UUID profileId, Set<ConfigKey> keys) throws OperationException {
		Profile source = profiles.require(profileId);
		Map<ConfigKey, ConfigValue> selected = new LinkedHashMap<>();
		source.entries().forEach((k, v) -> {
			if (keys.contains(k)) {
				selected.put(k, v);
			}
		});
		if (selected.isEmpty()) {
			throw new OperationException("wcm.error.nothing_selected");
		}
		Profile subset = source.duplicate(source.name(), clock.instant())
			.withEntries(selected, source.providerVersions(), source.minecraftVersion(), clock.instant());
		return write(List.of(subset), "selection");
	}

	/** Exports an arbitrary value set (e.g. the live world state or a backup) as a single profile. */
	public byte[] exportValues(String name, String description, Map<ConfigKey, ConfigValue> values,
		Map<String, String> providerVersions, String kind) throws OperationException {
		Profile profile;
		try {
			profile = Profile.create(name, description, clock.instant())
				.withEntries(values, providerVersions, minecraftVersion, clock.instant());
		} catch (IllegalArgumentException e) {
			throw new OperationException("wcm.error.invalid_profile", e.getMessage());
		}
		return write(List.of(profile), kind);
	}

	private byte[] write(List<Profile> list, String kind) throws OperationException {
		try {
			return WcmpFormat.write(list, kind, minecraftVersion, clock.instant());
		} catch (PackageException e) {
			throw new OperationException(e, e.translationKey(), e.getMessage());
		}
	}

	/** Parses a package, rejecting it entirely if anything is invalid. */
	public WcmpPackage read(byte[] data) throws OperationException {
		try {
			return WcmpFormat.read(data);
		} catch (PackageException e) {
			throw new OperationException(e, e.translationKey(), e.getMessage());
		}
	}

	public ImportPreview preview(WcmpPackage pkg, ImportMode mode) {
		CompatibilityChecker checker = new CompatibilityChecker(lookup, minecraftVersion);
		List<ImportPreview.ProfilePreview> previews = new ArrayList<>();
		Set<String> reservedNames = new HashSet<>();
		for (Profile incoming : pkg.profiles()) {
			Optional<Profile> existing = profiles.find(incoming.id());
			ImportPreview.Action action;
			if (existing.isEmpty()) {
				action = ImportPreview.Action.CREATE;
			} else if (mode == ImportMode.MERGE) {
				action = ImportPreview.Action.MERGE;
			} else if (existing.get().modifiedAt().isAfter(incoming.modifiedAt())) {
				action = ImportPreview.Action.BLOCKED_LOCAL_NEWER;
			} else {
				action = ImportPreview.Action.REPLACE;
			}
			String finalName = existing.map(Profile::name).orElse(incoming.name());
			if (action == ImportPreview.Action.CREATE || action == ImportPreview.Action.REPLACE) {
				finalName = uniqueName(incoming.name(), incoming.id(), reservedNames);
			}
			reservedNames.add(finalName.toLowerCase(java.util.Locale.ROOT));
			CompatibilityReport report = checker.check(incoming);
			previews.add(new ImportPreview.ProfilePreview(incoming.id(), incoming.name(), finalName, action,
				existing.map(Profile::name).orElse(null), entries(incoming, existing.orElse(null), report), report));
		}
		return new ImportPreview(mode, pkg.manifest().wcmVersion(), pkg.manifest().minecraftVersion(), previews);
	}

	private String uniqueName(String desired, UUID id, Set<String> reserved) {
		String candidate = profiles.uniqueName(desired, id);
		int n = 2;
		while (reserved.contains(candidate.toLowerCase(java.util.Locale.ROOT))) {
			candidate = profiles.uniqueName(desired + " (" + n++ + ")", id);
		}
		return candidate;
	}

	private static List<ImportPreview.EntryPreview> entries(Profile incoming, Profile existing, CompatibilityReport report) {
		Map<ConfigKey, EntryStatus> compat = new LinkedHashMap<>();
		Set<String> missingProviders = new HashSet<>();
		for (CompatibilityIssue issue : report.issues()) {
			switch (issue.code()) {
				case MISSING_PROVIDER -> missingProviders.add(issue.providerId());
				case MISSING_SETTING -> compat.put(new ConfigKey(issue.providerId(), issue.key()), EntryStatus.UNSUPPORTED_SETTING);
				case TYPE_CHANGED, INVALID_VALUE ->
					compat.put(new ConfigKey(issue.providerId(), issue.key()), EntryStatus.INCOMPATIBLE_VALUE);
				default -> {
					// Informational issues do not change entry status.
				}
			}
		}
		List<ImportPreview.EntryPreview> list = new ArrayList<>();
		incoming.entries().forEach((key, value) -> {
			ConfigValue old = existing == null ? null : existing.entries().get(key);
			EntryStatus status;
			if (missingProviders.contains(key.providerId())) {
				status = EntryStatus.MISSING_PROVIDER;
			} else if (compat.containsKey(key)) {
				status = compat.get(key);
			} else if (old == null) {
				status = EntryStatus.NEW;
			} else if (old.equals(value)) {
				status = EntryStatus.SAME;
			} else {
				status = EntryStatus.CONFLICT;
			}
			list.add(new ImportPreview.EntryPreview(key, value, old, status));
		});
		return list;
	}

	/**
	 * Commits an import. The package is re-previewed so the decision is based on the current state.
	 *
	 * @param allowOverwriteNewer if {@code true}, REPLACE also overwrites profiles modified locally after the export
	 */
	public ImportResult commit(WcmpPackage pkg, ImportMode mode, boolean allowOverwriteNewer) throws OperationException {
		ImportPreview preview = preview(pkg, mode);
		List<UUID> created = new ArrayList<>();
		List<UUID> merged = new ArrayList<>();
		List<UUID> replaced = new ArrayList<>();
		List<String> blocked = new ArrayList<>();
		for (int i = 0; i < pkg.profiles().size(); i++) {
			Profile incoming = pkg.profiles().get(i);
			ImportPreview.ProfilePreview p = preview.profiles().get(i);
			switch (p.action()) {
				case CREATE -> {
					profiles.store(rename(incoming, p.finalName()), false);
					created.add(incoming.id());
				}
				case MERGE -> {
					Profile existing = profiles.require(incoming.id());
					profiles.store(existing.mergedWith(incoming, true, clock.instant()), true);
					merged.add(incoming.id());
				}
				case REPLACE -> {
					profiles.store(rename(incoming, p.finalName()), true);
					replaced.add(incoming.id());
				}
				case BLOCKED_LOCAL_NEWER -> {
					if (allowOverwriteNewer) {
						profiles.store(rename(incoming, profiles.uniqueName(incoming.name(), incoming.id())), true);
						replaced.add(incoming.id());
					} else {
						blocked.add(incoming.name());
					}
				}
			}
		}
		return new ImportResult(created, merged, replaced, blocked);
	}

	private Profile rename(Profile profile, String name) {
		return name.equals(profile.name()) ? profile : profile.withName(name, profile.modifiedAt());
	}
}
