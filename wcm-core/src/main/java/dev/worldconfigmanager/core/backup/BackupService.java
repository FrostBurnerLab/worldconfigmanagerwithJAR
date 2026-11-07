package dev.worldconfigmanager.core.backup;

import dev.worldconfigmanager.api.ConfigProvider;
import dev.worldconfigmanager.api.ConfigSnapshot;
import dev.worldconfigmanager.api.ProviderContext;
import dev.worldconfigmanager.core.OperationException;
import dev.worldconfigmanager.core.json.JsonFormatException;
import dev.worldconfigmanager.core.registry.ProviderRegistry;
import dev.worldconfigmanager.core.settings.WcmSettings;
import dev.worldconfigmanager.core.storage.StorageException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.function.Supplier;

/** Creates, lists, loads and prunes backups. Restoring goes through the apply engine. */
public final class BackupService {
	private static final Logger LOGGER = LoggerFactory.getLogger("WorldConfigManager");

	private final BackupRepository repository;
	private final ProviderRegistry registry;
	private final Supplier<WcmSettings> settings;
	private final Clock clock;
	private final UUID worldId;

	public BackupService(BackupRepository repository, ProviderRegistry registry, Supplier<WcmSettings> settings, Clock clock,
		UUID worldId) {
		this.repository = repository;
		this.registry = registry;
		this.settings = settings;
		this.clock = clock;
		this.worldId = worldId;
	}

	/**
	 * Snapshots the given providers (or all available providers when {@code providers} is {@code null}) and stores
	 * the backup.
	 *
	 * @throws OperationException if any snapshot or the write fails; nothing is stored in that case
	 */
	public Backup create(BackupReason reason, String label, UUID profileId, Collection<ConfigProvider> providers,
		ProviderContext context) throws OperationException {
		Collection<ConfigProvider> targets = providers == null ? registry.available(context) : providers;
		Map<String, ConfigSnapshot> snapshots = new LinkedHashMap<>();
		for (ConfigProvider provider : targets) {
			String id = provider.metadata().providerId();
			try {
				ConfigSnapshot full = provider.snapshot(context);
				Map<String, dev.worldconfigmanager.api.ConfigValue> scoped = new LinkedHashMap<>();
				var definitions = registry.definitions(provider, context);
				full.values().forEach((k, v) -> {
					if (definitions.containsKey(k)) {
						scoped.put(k, v);
					}
				});
				snapshots.put(id, new ConfigSnapshot(id, full.providerVersion(), full.capturedAt(), scoped));
			} catch (RuntimeException e) {
				LOGGER.error("Snapshot of provider {} failed", id, e);
				throw new OperationException(e, "wcm.error.snapshot_failed", id, String.valueOf(e.getMessage()));
			}
		}
		Backup backup = new Backup(UUID.randomUUID(), clock.instant(), worldId, reason, label, profileId, snapshots);
		try {
			repository.save(backup);
		} catch (StorageException e) {
			throw new OperationException(e, "wcm.error.backup_write_failed", e.getMessage());
		}
		prune(backup.id());
		return backup;
	}

	public List<BackupSummary> list() {
		return repository.list();
	}

	public Backup load(UUID id) throws OperationException {
		BackupSummary summary = repository.summary(id).orElseThrow(() -> new OperationException("wcm.error.backup_not_found", id.toString()));
		if (summary.corrupt()) {
			throw new OperationException("wcm.error.backup_corrupt", id.toString(), String.valueOf(summary.problem()));
		}
		try {
			return repository.load(id);
		} catch (StorageException | JsonFormatException e) {
			throw new OperationException(e, "wcm.error.backup_corrupt", id.toString(), e.getMessage());
		}
	}

	public void delete(UUID id) throws OperationException {
		if (repository.summary(id).isEmpty()) {
			throw new OperationException("wcm.error.backup_not_found", id.toString());
		}
		try {
			repository.delete(id);
		} catch (StorageException e) {
			throw new OperationException(e, "wcm.error.storage", e.getMessage());
		}
	}

	/** Applies the retention policy. The backup with id {@code keep} is never deleted. */
	public int prune(UUID keep) {
		WcmSettings s = settings.get();
		List<BackupSummary> all = repository.list();
		Instant cutoff = s.maxBackupAgeDays() > 0 ? clock.instant().minus(Duration.ofDays(s.maxBackupAgeDays())) : null;
		int kept = 0;
		int deleted = 0;
		for (BackupSummary summary : all) {
			if (summary.id().equals(keep)) {
				kept++;
				continue;
			}
			boolean tooOld = cutoff != null && summary.createdAt().isBefore(cutoff);
			boolean tooMany = kept >= s.maxBackups();
			if (tooOld || tooMany) {
				try {
					repository.delete(summary.id());
					deleted++;
				} catch (StorageException e) {
					LOGGER.warn("Could not prune backup {}: {}", summary.id(), e.getMessage());
				}
			} else {
				kept++;
			}
		}
		return deleted;
	}
}
