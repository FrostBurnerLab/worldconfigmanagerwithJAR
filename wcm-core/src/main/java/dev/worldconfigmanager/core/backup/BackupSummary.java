package dev.worldconfigmanager.core.backup;

import java.time.Instant;
import java.util.UUID;

/**
 * Listing information about a backup.
 *
 * @param corrupt     {@code true} when the backup file could not be read
 * @param problem     explanation for a corrupt backup
 */
public record BackupSummary(UUID id, Instant createdAt, BackupReason reason, String label, UUID profileId, int valueCount,
	boolean corrupt, String problem) {
}
