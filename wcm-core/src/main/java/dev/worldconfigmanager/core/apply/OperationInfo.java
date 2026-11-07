package dev.worldconfigmanager.core.apply;

import dev.worldconfigmanager.core.backup.BackupReason;
import dev.worldconfigmanager.core.history.HistoryOperation;

import java.util.UUID;

/**
 * Describes who performs an apply operation and why, for backups and history.
 *
 * @param profileId profile being applied, if any
 * @param actor     display name of the player or console performing the operation
 */
public record OperationInfo(HistoryOperation operation, UUID profileId, String actor, BackupReason backupReason,
	String backupLabel) {
}
