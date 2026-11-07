package dev.worldconfigmanager.core.apply;

import dev.worldconfigmanager.api.ConfigApplyResult;
import dev.worldconfigmanager.api.ConfigChange;
import dev.worldconfigmanager.api.ConfigDefinition;
import dev.worldconfigmanager.api.ConfigKey;
import dev.worldconfigmanager.api.ConfigProvider;
import dev.worldconfigmanager.api.ConfigSnapshot;
import dev.worldconfigmanager.api.ConfigValue;
import dev.worldconfigmanager.api.ValidationResult;
import dev.worldconfigmanager.core.OperationException;
import dev.worldconfigmanager.core.backup.Backup;
import dev.worldconfigmanager.core.backup.BackupService;
import dev.worldconfigmanager.core.config.ConfigService;
import dev.worldconfigmanager.core.history.HistoryEntry;
import dev.worldconfigmanager.core.history.HistoryResult;
import dev.worldconfigmanager.core.history.HistoryService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.time.Clock;
import java.time.Instant;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.TreeMap;
import java.util.UUID;

/**
 * Transactional application of setting values.
 *
 * <ol>
 *     <li>Plan: classify each requested value (change, unchanged, invalid, unavailable).</li>
 *     <li>Refuse the whole operation if any value is invalid.</li>
 *     <li>Back up every affected provider. If the backup fails, nothing is changed.</li>
 *     <li>Apply changes provider by provider.</li>
 *     <li>If a provider fails, restore every touched provider from the backup snapshot and report any rollback
 *     failure explicitly.</li>
 *     <li>Record history for every attempted change.</li>
 * </ol>
 */
public final class ApplyEngine {
	private static final Logger LOGGER = LoggerFactory.getLogger("WorldConfigManager");

	private final ConfigService config;
	private final BackupService backups;
	private final HistoryService history;
	private final Clock clock;
	private final UUID worldId;

	public ApplyEngine(ConfigService config, BackupService backups, HistoryService history, Clock clock, UUID worldId) {
		this.config = config;
		this.backups = backups;
		this.history = history;
		this.clock = clock;
		this.worldId = worldId;
	}

	/** Builds a preview without changing anything. */
	public ApplyPlan plan(Map<ConfigKey, ConfigValue> desired) {
		List<PlannedChange> entries = new ArrayList<>();
		for (Map.Entry<ConfigKey, ConfigValue> e : new TreeMap<>(desired).entrySet()) {
			ConfigKey key = e.getKey();
			ConfigValue target = e.getValue();
			Optional<ConfigDefinition> def = config.definition(key);
			if (def.isEmpty()) {
				entries.add(new PlannedChange(key, null, null, target, ChangeStatus.UNAVAILABLE,
					ValidationResult.error("wcm.validation.unavailable", key.toString())));
				continue;
			}
			ConfigValue current = config.read(key).orElse(null);
			ValidationResult validation = config.validate(key, target);
			ChangeStatus status;
			if (!validation.valid()) {
				status = ChangeStatus.INVALID;
			} else if (target.equals(current)) {
				status = ChangeStatus.UNCHANGED;
			} else {
				status = ChangeStatus.CHANGE;
			}
			entries.add(new PlannedChange(key, def.get(), current, target, status, validation));
		}
		return new ApplyPlan(entries);
	}

	/** Re-plans {@code desired} against the current state and applies it transactionally. */
	public ApplyOutcome execute(Map<ConfigKey, ConfigValue> desired, OperationInfo info) {
		ApplyPlan plan = plan(desired);
		List<ConfigKey> skipped = plan.withStatus(ChangeStatus.UNAVAILABLE).stream().map(PlannedChange::key).toList();
		if (plan.hasInvalid()) {
			return ApplyOutcome.simple(ApplyStatus.VALIDATION_FAILED, skipped, plan.withStatus(ChangeStatus.INVALID), null);
		}
		List<PlannedChange> changes = plan.withStatus(ChangeStatus.CHANGE);
		if (changes.isEmpty()) {
			return ApplyOutcome.simple(ApplyStatus.NO_CHANGES, skipped, List.of(), null);
		}

		Map<String, List<PlannedChange>> byProvider = new LinkedHashMap<>();
		for (PlannedChange change : changes) {
			byProvider.computeIfAbsent(change.key().providerId(), k -> new ArrayList<>()).add(change);
		}
		Map<String, ConfigProvider> providers = new LinkedHashMap<>();
		for (String id : byProvider.keySet()) {
			ConfigProvider provider = config.availableProvider(id).orElse(null);
			if (provider == null) {
				return ApplyOutcome.simple(ApplyStatus.VALIDATION_FAILED, skipped, List.of(),
					"Provider " + id + " became unavailable");
			}
			providers.put(id, provider);
		}

		Backup backup;
		try {
			backup = backups.create(info.backupReason(), info.backupLabel(), info.profileId(), providers.values(), config.context());
		} catch (OperationException e) {
			LOGGER.error("Refusing to apply changes: safety backup failed: {}", e.getMessage());
			return ApplyOutcome.simple(ApplyStatus.BACKUP_FAILED, skipped, List.of(), e.getMessage());
		}

		Set<ConfigKey> applied = new LinkedHashSet<>();
		Map<ConfigKey, String> failures = new LinkedHashMap<>();
		Set<ConfigKey> restartRequired = new LinkedHashSet<>();
		List<String> touched = new ArrayList<>();
		boolean failed = false;

		for (Map.Entry<String, List<PlannedChange>> group : byProvider.entrySet()) {
			String providerId = group.getKey();
			ConfigProvider provider = providers.get(providerId);
			List<ConfigChange> requests = group.getValue().stream()
				.map(c -> new ConfigChange(c.key().key(), c.current(), c.target())).toList();
			touched.add(providerId);
			ConfigApplyResult result;
			try {
				result = provider.apply(requests, config.context());
				if (result == null) {
					throw new IllegalStateException("Provider returned no result");
				}
			} catch (RuntimeException e) {
				LOGGER.error("Provider {} threw while applying changes", providerId, e);
				for (PlannedChange c : group.getValue()) {
					failures.put(c.key(), "Provider error: " + e.getMessage());
				}
				failed = true;
				break;
			}
			Set<String> requestedKeys = new HashSet<>();
			requests.forEach(r -> requestedKeys.add(r.key()));
			for (String key : result.applied()) {
				if (requestedKeys.contains(key)) {
					applied.add(new ConfigKey(providerId, key));
				}
			}
			result.failures().forEach((key, message) -> failures.put(new ConfigKey(providerId, key), message));
			for (PlannedChange c : group.getValue()) {
				if (!applied.contains(c.key()) && !failures.containsKey(c.key())) {
					failures.put(c.key(), "Provider did not confirm the change");
				}
				if (applied.contains(c.key()) && (c.restartRequired() || result.restartRequired().contains(c.key().key()))) {
					restartRequired.add(c.key());
				}
			}
			if (!failures.isEmpty()) {
				failed = true;
				break;
			}
		}

		ApplyStatus status = ApplyStatus.SUCCESS;
		Map<String, String> rollbackFailures = new LinkedHashMap<>();
		Set<ConfigKey> rolledBack = new HashSet<>();
		if (failed) {
			status = ApplyStatus.ROLLED_BACK;
			for (String providerId : touched) {
				ConfigSnapshot snapshot = backup.snapshots().get(providerId);
				try {
					ConfigApplyResult restore = providers.get(providerId).restore(snapshot, config.context());
					if (restore == null || !restore.isSuccess()) {
						rollbackFailures.put(providerId, restore == null ? "no result" : String.join("; ", restore.failures().values()));
					}
				} catch (RuntimeException e) {
					LOGGER.error("Rollback of provider {} failed", providerId, e);
					rollbackFailures.put(providerId, String.valueOf(e.getMessage()));
				}
			}
			for (ConfigKey key : applied) {
				if (!rollbackFailures.containsKey(key.providerId())) {
					rolledBack.add(key);
				}
			}
			if (!rollbackFailures.isEmpty()) {
				status = ApplyStatus.ROLLBACK_INCOMPLETE;
				LOGGER.error("Rollback incomplete for providers {}; backup {} can be restored manually", rollbackFailures.keySet(),
					backup.id());
			}
		}

		recordHistory(changes, applied, failures, rolledBack, info, backup.id());
		List<ConfigKey> finalApplied = applied.stream().filter(k -> !rolledBack.contains(k)).toList();
		if (failed) {
			restartRequired.retainAll(new HashSet<>(finalApplied));
		}
		return new ApplyOutcome(status, backup.id(), finalApplied, failures, rollbackFailures, restartRequired, skipped, List.of(),
			null);
	}

	private void recordHistory(List<PlannedChange> changes, Set<ConfigKey> applied, Map<ConfigKey, String> failures,
		Set<ConfigKey> rolledBack, OperationInfo info, UUID backupId) {
		Instant now = clock.instant();
		List<HistoryEntry> entries = new ArrayList<>();
		for (PlannedChange c : changes) {
			HistoryResult result;
			String message = "";
			if (rolledBack.contains(c.key())) {
				result = HistoryResult.ROLLED_BACK;
			} else if (applied.contains(c.key())) {
				result = HistoryResult.SUCCESS;
			} else if (failures.containsKey(c.key())) {
				result = HistoryResult.FAILED;
				message = failures.get(c.key());
			} else {
				// Not attempted because an earlier provider failed.
				continue;
			}
			boolean sensitive = c.definition() != null && c.definition().sensitive();
			entries.add(new HistoryEntry(UUID.randomUUID(), now, worldId, info.profileId(), c.key().providerId(), c.key().key(),
				c.definition() == null ? null : c.definition().category(), c.current(), c.target(), sensitive, info.operation(),
				result, info.actor(), backupId, message));
		}
		history.record(entries);
	}
}
