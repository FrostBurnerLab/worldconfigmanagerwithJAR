package dev.worldconfigmanager.core.profile;

import dev.worldconfigmanager.api.ConfigKey;
import dev.worldconfigmanager.api.ConfigValue;
import dev.worldconfigmanager.core.OperationException;
import dev.worldconfigmanager.core.storage.StorageException;

import java.time.Clock;
import java.util.Collection;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

/** Profile CRUD operations with validation and unique-name enforcement. */
public final class ProfileService {
	private final ProfileRepository repository;
	private final Clock clock;

	public ProfileService(ProfileRepository repository, Clock clock) {
		this.repository = repository;
		this.clock = clock;
	}

	public List<Profile> list() {
		return repository.findAll();
	}

	public Optional<Profile> find(UUID id) {
		return repository.findById(id);
	}

	public Profile require(UUID id) throws OperationException {
		return repository.findById(id).orElseThrow(() -> new OperationException("wcm.error.profile_not_found", id.toString()));
	}

	public Profile create(String name, String description) throws OperationException {
		String validName = validName(name);
		ensureNameFree(validName, null);
		Profile profile;
		try {
			profile = Profile.create(validName, description, clock.instant());
		} catch (IllegalArgumentException e) {
			throw new OperationException("wcm.error.invalid_profile", e.getMessage());
		}
		save(profile);
		return profile;
	}

	public Profile rename(UUID id, String newName) throws OperationException {
		Profile profile = require(id);
		String validName = validName(newName);
		ensureNameFree(validName, id);
		Profile renamed = profile.withName(validName, clock.instant());
		save(renamed);
		return renamed;
	}

	public Profile updateDescription(UUID id, String description) throws OperationException {
		Profile profile = require(id);
		try {
			Profile updated = profile.withDescription(description, clock.instant());
			save(updated);
			return updated;
		} catch (IllegalArgumentException e) {
			throw new OperationException("wcm.error.invalid_profile", e.getMessage());
		}
	}

	public Profile updateTags(UUID id, Collection<String> tags) throws OperationException {
		Profile profile = require(id);
		try {
			Profile updated = profile.withTags(tags, clock.instant());
			save(updated);
			return updated;
		} catch (IllegalArgumentException e) {
			throw new OperationException("wcm.error.invalid_profile", e.getMessage());
		}
	}

	public Profile updateEntries(UUID id, Map<ConfigKey, ConfigValue> entries, Map<String, String> providerVersions,
		String minecraftVersion) throws OperationException {
		Profile profile = require(id);
		try {
			Profile updated = profile.withEntries(entries, providerVersions, minecraftVersion, clock.instant());
			save(updated);
			return updated;
		} catch (IllegalArgumentException e) {
			throw new OperationException("wcm.error.invalid_profile", e.getMessage());
		}
	}

	public Profile duplicate(UUID id) throws OperationException {
		Profile source = require(id);
		String base = source.name();
		String candidate = truncate(base + " (copy)");
		int n = 2;
		while (nameTaken(candidate, null)) {
			candidate = truncate(base + " (copy " + n++ + ")");
		}
		Profile copy = source.duplicate(candidate, clock.instant());
		save(copy);
		return copy;
	}

	public void delete(UUID id) throws OperationException {
		require(id);
		try {
			repository.delete(id);
		} catch (StorageException e) {
			throw new OperationException(e, "wcm.error.storage", e.getMessage());
		}
	}

	public Optional<UUID> defaultProfileId() {
		return repository.defaultProfileId();
	}

	public void setDefault(UUID id) throws OperationException {
		if (id != null) {
			require(id);
		}
		try {
			repository.setDefaultProfileId(id);
		} catch (StorageException e) {
			throw new OperationException(e, "wcm.error.storage", e.getMessage());
		}
	}

	/** Persists an externally built profile (e.g. from an import), enforcing name rules. */
	public void store(Profile profile, boolean allowExistingId) throws OperationException {
		if (!allowExistingId && repository.findById(profile.id()).isPresent()) {
			throw new OperationException("wcm.error.profile_exists", profile.id().toString());
		}
		ensureNameFree(profile.name(), profile.id());
		save(profile);
	}

	/** @return a name not used by any other profile, derived from {@code desired} */
	public String uniqueName(String desired, UUID ignoreId) {
		String candidate = truncate(desired);
		int n = 2;
		while (nameTaken(candidate, ignoreId)) {
			candidate = truncate(desired + " (" + n++ + ")");
		}
		return candidate;
	}

	private void save(Profile profile) throws OperationException {
		try {
			repository.save(profile);
		} catch (StorageException e) {
			throw new OperationException(e, "wcm.error.storage", e.getMessage());
		}
	}

	private static String validName(String name) throws OperationException {
		try {
			return ProfileValidation.requireValidName(name);
		} catch (IllegalArgumentException e) {
			throw new OperationException("wcm.error.invalid_name", e.getMessage());
		}
	}

	private void ensureNameFree(String name, UUID ignoreId) throws OperationException {
		if (nameTaken(name, ignoreId)) {
			throw new OperationException("wcm.error.name_taken", name);
		}
	}

	private boolean nameTaken(String name, UUID ignoreId) {
		String lower = name.toLowerCase(Locale.ROOT);
		return repository.findAll().stream()
			.anyMatch(p -> !p.id().equals(ignoreId) && p.name().toLowerCase(Locale.ROOT).equals(lower));
	}

	private static String truncate(String name) {
		String stripped = name.strip();
		return stripped.length() <= Profile.MAX_NAME ? stripped : stripped.substring(0, Profile.MAX_NAME).strip();
	}
}
