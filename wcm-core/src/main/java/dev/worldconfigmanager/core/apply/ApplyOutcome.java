package dev.worldconfigmanager.core.apply;

import dev.worldconfigmanager.api.ConfigKey;

import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

/**
 * Result of executing an apply operation.
 *
 * @param backupId         safety backup taken before changing anything ({@code null} if none)
 * @param applied          keys that are now set to the requested values
 * @param failures         keys a provider failed to apply, with explanation
 * @param rollbackFailures provider ids whose rollback failed, with explanation
 * @param restartRequired  applied keys that need a reload/restart to take effect
 * @param skipped          unavailable keys that were skipped
 * @param invalid          invalid entries (only for {@link ApplyStatus#VALIDATION_FAILED})
 * @param errorMessage     explanation for {@link ApplyStatus#BACKUP_FAILED}
 */
public record ApplyOutcome(ApplyStatus status, UUID backupId, List<ConfigKey> applied, Map<ConfigKey, String> failures,
	Map<String, String> rollbackFailures, Set<ConfigKey> restartRequired, List<ConfigKey> skipped,
	List<PlannedChange> invalid, String errorMessage) {

	public ApplyOutcome {
		applied = List.copyOf(applied);
		failures = Map.copyOf(failures);
		rollbackFailures = Map.copyOf(rollbackFailures);
		restartRequired = Set.copyOf(restartRequired);
		skipped = List.copyOf(skipped);
		invalid = List.copyOf(invalid);
		errorMessage = errorMessage == null ? "" : errorMessage;
	}

	static ApplyOutcome simple(ApplyStatus status, List<ConfigKey> skipped, List<PlannedChange> invalid, String error) {
		return new ApplyOutcome(status, null, List.of(), Map.of(), Map.of(), Set.of(), skipped, invalid, error);
	}
}
