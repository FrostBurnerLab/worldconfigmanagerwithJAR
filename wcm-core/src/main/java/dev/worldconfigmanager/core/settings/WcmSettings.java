package dev.worldconfigmanager.core.settings;

import dev.worldconfigmanager.core.permission.PermissionPolicy;

import java.util.Objects;

/**
 * Global WorldConfig Manager settings (server side).
 *
 * @param maxBackups         maximum number of backups kept per world (1-500)
 * @param maxBackupAgeDays   backups older than this are pruned; 0 disables age-based pruning
 * @param maxHistoryEntries  maximum number of history entries kept per world (100-100000)
 * @param maxHistoryAgeDays  history older than this is pruned; 0 disables age-based pruning
 * @param requestsPerSecond  sustained network request rate allowed per player (1-50)
 * @param requestBurst       burst size of the request rate limiter (1-200)
 * @param permissions        permission policy
 * @param autoApplyDefault   apply the default profile automatically when WCM first sees a new world
 */
public record WcmSettings(int maxBackups, int maxBackupAgeDays, int maxHistoryEntries, int maxHistoryAgeDays,
	int requestsPerSecond, int requestBurst, PermissionPolicy permissions, boolean autoApplyDefault) {

	public WcmSettings {
		maxBackups = clamp(maxBackups, 1, 500);
		maxBackupAgeDays = clamp(maxBackupAgeDays, 0, 3650);
		maxHistoryEntries = clamp(maxHistoryEntries, 100, 100_000);
		maxHistoryAgeDays = clamp(maxHistoryAgeDays, 0, 3650);
		requestsPerSecond = clamp(requestsPerSecond, 1, 50);
		requestBurst = clamp(requestBurst, 1, 200);
		Objects.requireNonNull(permissions, "permissions");
	}

	public static WcmSettings defaults() {
		return new WcmSettings(20, 0, 5000, 90, 5, 30, PermissionPolicy.defaults(), false);
	}

	private static int clamp(int value, int min, int max) {
		return Math.max(min, Math.min(max, value));
	}

	public WcmSettings withRetention(int backups, int backupAgeDays, int historyEntries, int historyAgeDays) {
		return new WcmSettings(backups, backupAgeDays, historyEntries, historyAgeDays, requestsPerSecond, requestBurst, permissions,
			autoApplyDefault);
	}

	public WcmSettings withPermissions(PermissionPolicy policy) {
		return new WcmSettings(maxBackups, maxBackupAgeDays, maxHistoryEntries, maxHistoryAgeDays, requestsPerSecond, requestBurst,
			policy, autoApplyDefault);
	}

	public WcmSettings withAutoApplyDefault(boolean value) {
		return new WcmSettings(maxBackups, maxBackupAgeDays, maxHistoryEntries, maxHistoryAgeDays, requestsPerSecond, requestBurst,
			permissions, value);
	}
}
