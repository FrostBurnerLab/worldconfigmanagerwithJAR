package dev.worldconfigmanager.core;

import dev.worldconfigmanager.api.ConfigEntry;
import dev.worldconfigmanager.api.ConfigKey;
import dev.worldconfigmanager.api.ConfigValue;
import dev.worldconfigmanager.api.ProviderContext;
import dev.worldconfigmanager.core.apply.ApplyEngine;
import dev.worldconfigmanager.core.apply.ApplyOutcome;
import dev.worldconfigmanager.core.apply.ApplyPlan;
import dev.worldconfigmanager.core.apply.ApplyStatus;
import dev.worldconfigmanager.core.apply.OperationInfo;
import dev.worldconfigmanager.core.backup.Backup;
import dev.worldconfigmanager.core.backup.BackupReason;
import dev.worldconfigmanager.core.backup.BackupRepository;
import dev.worldconfigmanager.core.backup.BackupService;
import dev.worldconfigmanager.core.backup.BackupSummary;
import dev.worldconfigmanager.core.compare.ProfileComparator;
import dev.worldconfigmanager.core.compat.CompatibilityChecker;
import dev.worldconfigmanager.core.compat.CompatibilityIssue;
import dev.worldconfigmanager.core.compat.CompatibilityReport;
import dev.worldconfigmanager.core.config.ConfigService;
import dev.worldconfigmanager.core.history.HistoryFilter;
import dev.worldconfigmanager.core.history.HistoryOperation;
import dev.worldconfigmanager.core.history.HistoryRepository;
import dev.worldconfigmanager.core.history.HistoryService;
import dev.worldconfigmanager.core.profile.JsonProfileRepository;
import dev.worldconfigmanager.core.profile.Profile;
import dev.worldconfigmanager.core.profile.ProfileService;
import dev.worldconfigmanager.core.registry.ProviderRegistry;
import dev.worldconfigmanager.core.settings.WcmSettings;
import dev.worldconfigmanager.core.settings.WcmSettingsStore;
import dev.worldconfigmanager.core.storage.LoadProblem;
import dev.worldconfigmanager.core.storage.StorageException;
import dev.worldconfigmanager.core.transfer.TransferService;
import dev.worldconfigmanager.core.world.WorldDescriptor;
import dev.worldconfigmanager.core.world.WorldIdentity;
import dev.worldconfigmanager.core.world.WorldMetadata;
import dev.worldconfigmanager.core.world.WorldMetadataStore;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.nio.file.Path;
import java.time.Clock;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.function.Function;

/**
 * Composition root for one running world: wires repositories and services with constructor dependencies.
 *
 * <p>Storage layout:
 * <pre>
 * config/worldconfigmanager/settings.json        global WCM settings
 * config/worldconfigmanager/profiles/*.json      profile library (shared by all worlds of this game/server)
 * &lt;world&gt;/worldconfigmanager/world.json          world identity and associations
 * &lt;world&gt;/worldconfigmanager/backups/*.json      backups
 * &lt;world&gt;/worldconfigmanager/history.jsonl       change history
 * </pre>
 *
 * <p>All methods must be called on the server thread.
 */
public final class WorldSession {
	private static final Logger LOGGER = LoggerFactory.getLogger("WorldConfigManager");

	private final WcmSettingsStore settingsStore;
	private final JsonProfileRepository profileRepository;
	private final ProfileService profiles;
	private final WorldMetadataStore metadata;
	private final WorldIdentity identity;
	private final ConfigService config;
	private final BackupService backups;
	private final HistoryService history;
	private final ApplyEngine engine;
	private final TransferService transfer;
	private final Clock clock;

	public WorldSession(Path configDir, Path worldDataDir, WorldDescriptor descriptor, ProviderRegistry registry,
		Function<UUID, ProviderContext> contextFactory, Clock clock) throws StorageException {
		this.clock = clock;
		this.settingsStore = new WcmSettingsStore(configDir.resolve("settings.json"));
		this.profileRepository = new JsonProfileRepository(configDir.resolve("profiles"));
		this.profiles = new ProfileService(profileRepository, clock);
		this.metadata = new WorldMetadataStore(worldDataDir.resolve("world.json"), clock);
		UUID worldId = metadata.get().worldId();
		this.identity = new WorldIdentity(worldId, descriptor.displayName(), descriptor.kind(), descriptor.gameMode(),
			descriptor.hardcore(), descriptor.minecraftVersion());
		this.config = new ConfigService(registry, contextFactory.apply(worldId));
		this.backups = new BackupService(new BackupRepository(worldDataDir.resolve("backups")), registry, settingsStore::get, clock,
			worldId);
		this.history = new HistoryService(new HistoryRepository(worldDataDir.resolve("history.jsonl")), settingsStore::get, clock);
		this.engine = new ApplyEngine(config, backups, history, clock, worldId);
		this.transfer = new TransferService(profiles, config, descriptor.minecraftVersion(), clock);
		history.prune();
		backups.prune(null);
	}

	/**
	 * Performs first-run handling: associates the default profile with a world WCM sees for the first time and, if
	 * enabled in the settings, applies it.
	 */
	public Optional<ApplyOutcome> onWorldLoaded() {
		if (!metadata.isNewlyCreated()) {
			return Optional.empty();
		}
		Optional<Profile> defaultProfile = profiles.defaultProfileId().flatMap(profiles::find);
		if (defaultProfile.isEmpty()) {
			return Optional.empty();
		}
		try {
			associate(defaultProfile.get().id());
		} catch (OperationException e) {
			LOGGER.warn("Could not associate default profile: {}", e.getMessage());
			return Optional.empty();
		}
		if (!settingsStore.get().autoApplyDefault()) {
			return Optional.empty();
		}
		try {
			ApplyOutcome outcome = applyProfile(defaultProfile.get().id(), "WorldConfig Manager");
			LOGGER.info("Applied default profile '{}' to new world: {}", defaultProfile.get().name(), outcome.status());
			return Optional.of(outcome);
		} catch (OperationException e) {
			LOGGER.warn("Could not apply default profile: {}", e.getMessage());
			return Optional.empty();
		}
	}

	public WorldIdentity identity() {
		return identity;
	}

	public WorldMetadata metadata() {
		return metadata.get();
	}

	public WcmSettings settings() {
		return settingsStore.get();
	}

	public void updateSettings(WcmSettings settings) throws OperationException {
		try {
			settingsStore.update(settings);
		} catch (StorageException e) {
			throw new OperationException(e, "wcm.error.storage", e.getMessage());
		}
		history.prune();
		backups.prune(null);
	}

	public ProfileService profiles() {
		return profiles;
	}

	public ConfigService config() {
		return config;
	}

	public BackupService backups() {
		return backups;
	}

	public HistoryService history() {
		return history;
	}

	public TransferService transfer() {
		return transfer;
	}

	public ApplyEngine engine() {
		return engine;
	}

	public ProfileComparator comparator() {
		return new ProfileComparator(config);
	}

	public CompatibilityReport compatibility(Profile profile) {
		return new CompatibilityChecker(config, identity.minecraftVersion()).check(profile);
	}

	/** @return profile entries restricted to settings managed in this context (others are reported as unavailable) */
	public ApplyPlan previewProfile(UUID profileId) throws OperationException {
		return engine.plan(profiles.require(profileId).entries());
	}

	public ApplyOutcome applyProfile(UUID profileId, String actor) throws OperationException {
		Profile profile = profiles.require(profileId);
		ApplyOutcome outcome = engine.execute(profile.entries(),
			new OperationInfo(HistoryOperation.APPLY_PROFILE, profileId, actor, BackupReason.BEFORE_PROFILE_APPLY,
				"Before applying " + profile.name()));
		if (outcome.status() == ApplyStatus.SUCCESS || outcome.status() == ApplyStatus.NO_CHANGES) {
			updateMetadata(metadata.get().withActiveProfile(profileId, clock.instant()));
		}
		return outcome;
	}

	/** Applies individual edits from the configuration browser. */
	public ApplyOutcome applyChanges(Map<ConfigKey, ConfigValue> changes, String actor, boolean resetToDefault) {
		return engine.execute(changes, new OperationInfo(resetToDefault ? HistoryOperation.RESET_DEFAULT : HistoryOperation.SET_VALUE,
			null, actor, BackupReason.BEFORE_CHANGES, resetToDefault ? "Before reset to default" : "Before manual changes"));
	}

	public ApplyPlan previewBackup(UUID backupId) throws OperationException {
		return engine.plan(backups.load(backupId).values());
	}

	public ApplyOutcome restoreBackup(UUID backupId, String actor) throws OperationException {
		Backup backup = backups.load(backupId);
		if (!backup.worldId().equals(identity.worldId())) {
			throw new OperationException("wcm.error.backup_other_world", backupId.toString());
		}
		ApplyOutcome outcome = engine.execute(backup.values(), new OperationInfo(HistoryOperation.RESTORE_BACKUP, backup.profileId(), actor,
			BackupReason.BEFORE_RESTORE, "Before restoring backup"));
		if (outcome.status() == ApplyStatus.SUCCESS) {
			// The world no longer reflects the previously applied profile.
			updateMetadata(metadata.get().withActiveProfile(null, clock.instant()));
		}
		return outcome;
	}

	public Backup createManualBackup(String label) throws OperationException {
		return backups.create(BackupReason.MANUAL, label, null, null, config.context());
	}

	/** Creates a new profile containing the current values of all managed settings. */
	public Profile captureProfile(String name, String description) throws OperationException {
		Profile created = profiles.create(name, description);
		return profiles.updateEntries(created.id(), config.currentValues(), config.providerVersions(), identity.minecraftVersion());
	}

	/** Replaces a profile's entries with the current values of all managed settings. */
	public Profile updateProfileFromCurrent(UUID profileId) throws OperationException {
		return profiles.updateEntries(profileId, config.currentValues(), config.providerVersions(), identity.minecraftVersion());
	}

	public void associate(UUID profileId) throws OperationException {
		if (profileId != null) {
			profiles.require(profileId);
		}
		updateMetadata(metadata.get().withAssociatedProfile(profileId));
	}

	private void updateMetadata(WorldMetadata updated) throws OperationException {
		try {
			metadata.update(updated);
		} catch (StorageException e) {
			throw new OperationException(e, "wcm.error.storage", e.getMessage());
		}
	}

	public List<LoadProblem> loadProblems() {
		return profileRepository.loadProblems();
	}

	public DashboardSnapshot dashboard() {
		WorldMetadata meta = metadata.get();
		Optional<Profile> active = Optional.ofNullable(meta.activeProfileId()).flatMap(profiles::find);
		Optional<Profile> associated = Optional.ofNullable(meta.associatedProfileId()).flatMap(profiles::find);
		List<ConfigEntry> entries = config.entries();
		List<BackupSummary> backupList = backups.list();
		List<DashboardSnapshot.Warning> warnings = new ArrayList<>();
		for (LoadProblem problem : loadProblems()) {
			warnings.add(new DashboardSnapshot.Warning("wcm.warning.profile_unreadable", List.of(problem.fileName())));
		}
		int corrupt = (int) backupList.stream().filter(BackupSummary::corrupt).count();
		if (corrupt > 0) {
			warnings.add(new DashboardSnapshot.Warning("wcm.warning.backups_corrupt", List.of(Integer.toString(corrupt))));
		}
		long unavailable = entries.stream().filter(e -> !e.available()).count();
		if (unavailable > 0) {
			warnings.add(new DashboardSnapshot.Warning("wcm.warning.settings_unreadable", List.of(Long.toString(unavailable))));
		}
		Map<UUID, Profile> checked = new LinkedHashMap<>();
		active.ifPresent(p -> checked.put(p.id(), p));
		associated.ifPresent(p -> checked.put(p.id(), p));
		for (Profile p : checked.values()) {
			for (CompatibilityIssue issue : compatibility(p).issues()) {
				if (issue.severity() != CompatibilityIssue.Severity.INFO) {
					List<String> args = new ArrayList<>(issue.args());
					args.addFirst(p.name());
					warnings.add(new DashboardSnapshot.Warning(issue.code().translationKey() + ".profile", args));
				}
			}
		}
		return new DashboardSnapshot(identity, meta.activeProfileId(), active.map(Profile::name).orElse(null),
			meta.associatedProfileId(), associated.map(Profile::name).orElse(null), entries.size(),
			config.availableProviders().size(), history.size(), backupList.size() - corrupt,
			backupList.stream().filter(b -> !b.corrupt()).findFirst().map(BackupSummary::createdAt).orElse(null), corrupt,
			warnings, history.query(HistoryFilter.ALL, 6));
	}
}
