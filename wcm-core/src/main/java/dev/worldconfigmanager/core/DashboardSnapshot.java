package dev.worldconfigmanager.core;

import dev.worldconfigmanager.core.history.HistoryEntry;
import dev.worldconfigmanager.core.world.WorldIdentity;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

/**
 * Real data shown on the dashboard. Every number is computed from live services.
 *
 * @param activeProfileName     name of the profile most recently applied ({@code null} if none)
 * @param associatedProfileName name of the associated profile ({@code null} if none)
 * @param managedSettings       number of settings available in this world
 * @param providerCount         number of available providers
 * @param changeCount           number of history entries for this world
 * @param backupCount           number of backups
 * @param latestBackup          time of the newest backup ({@code null} if none)
 * @param corruptBackups        number of unreadable backups
 * @param warnings              compatibility/storage warnings as translation key + args, flattened as text
 * @param recentChanges         newest history entries
 */
public record DashboardSnapshot(WorldIdentity world, UUID activeProfileId, String activeProfileName, UUID associatedProfileId,
	String associatedProfileName, int managedSettings, int providerCount, int changeCount, int backupCount, Instant latestBackup,
	int corruptBackups, List<Warning> warnings, List<HistoryEntry> recentChanges) {
	public DashboardSnapshot {
		warnings = List.copyOf(warnings);
		recentChanges = List.copyOf(recentChanges);
	}

	/** A warning as translation key and arguments. */
	public record Warning(String key, List<String> args) {
		public Warning {
			args = List.copyOf(args);
		}
	}
}
