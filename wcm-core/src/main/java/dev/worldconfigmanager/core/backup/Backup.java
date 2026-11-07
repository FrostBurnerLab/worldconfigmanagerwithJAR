package dev.worldconfigmanager.core.backup;

import dev.worldconfigmanager.api.ConfigKey;
import dev.worldconfigmanager.api.ConfigSnapshot;
import dev.worldconfigmanager.api.ConfigValue;

import java.time.Instant;
import java.util.Map;
import java.util.Objects;
import java.util.TreeMap;
import java.util.UUID;

/**
 * A stored copy of provider values. Backups contain only configuration snapshots, never other backups.
 *
 * @param id        unique id
 * @param createdAt timestamp
 * @param worldId   world the snapshot belongs to
 * @param reason    why it was created
 * @param label     user label or automatic description
 * @param profileId profile being applied when the backup was taken (may be {@code null})
 * @param snapshots provider id to snapshot
 */
public record Backup(UUID id, Instant createdAt, UUID worldId, BackupReason reason, String label, UUID profileId,
	Map<String, ConfigSnapshot> snapshots) {
	public Backup {
		Objects.requireNonNull(id, "id");
		Objects.requireNonNull(createdAt, "createdAt");
		Objects.requireNonNull(worldId, "worldId");
		Objects.requireNonNull(reason, "reason");
		label = label == null ? "" : label.strip();
		if (label.length() > 64) {
			label = label.substring(0, 64);
		}
		snapshots = java.util.Collections.unmodifiableMap(new TreeMap<>(snapshots));
	}

	/** @return all values flattened to global keys */
	public Map<ConfigKey, ConfigValue> values() {
		Map<ConfigKey, ConfigValue> map = new TreeMap<>();
		snapshots.forEach((provider, snapshot) -> snapshot.values().forEach((k, v) -> map.put(new ConfigKey(provider, k), v)));
		return map;
	}

	public int valueCount() {
		return snapshots.values().stream().mapToInt(s -> s.values().size()).sum();
	}

	/** Lightweight summary used for listings. */
	public BackupSummary summary() {
		return new BackupSummary(id, createdAt, reason, label, profileId, valueCount(), false, null);
	}
}
