package dev.worldconfigmanager.core.history;

import dev.worldconfigmanager.api.ConfigCategory;
import dev.worldconfigmanager.api.ConfigValue;

import java.time.Instant;
import java.util.Objects;
import java.util.UUID;

/**
 * One recorded configuration change. Values of sensitive settings are stored masked ({@code null} with
 * {@code masked = true}).
 *
 * @param backupId backup taken before the operation; restoring it undoes the change
 */
public record HistoryEntry(UUID id, Instant timestamp, UUID worldId, UUID profileId, String providerId, String key,
	ConfigCategory category, ConfigValue previousValue, ConfigValue newValue, boolean masked, HistoryOperation operation,
	HistoryResult result, String actor, UUID backupId, String message) {
	public HistoryEntry {
		Objects.requireNonNull(id, "id");
		Objects.requireNonNull(timestamp, "timestamp");
		Objects.requireNonNull(worldId, "worldId");
		Objects.requireNonNull(providerId, "providerId");
		Objects.requireNonNull(key, "key");
		Objects.requireNonNull(operation, "operation");
		Objects.requireNonNull(result, "result");
		category = category == null ? ConfigCategory.OTHER_MODS : category;
		actor = actor == null ? "" : actor;
		message = message == null ? "" : message;
		if (masked) {
			previousValue = null;
			newValue = null;
		}
	}
}
