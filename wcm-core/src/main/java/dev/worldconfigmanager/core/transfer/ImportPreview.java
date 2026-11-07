package dev.worldconfigmanager.core.transfer;

import dev.worldconfigmanager.api.ConfigKey;
import dev.worldconfigmanager.api.ConfigValue;
import dev.worldconfigmanager.core.compat.CompatibilityReport;

import java.util.List;
import java.util.UUID;

/** Detailed preview of an import, computed before anything is written. */
public record ImportPreview(ImportMode mode, String packageWcmVersion, String packageMinecraftVersion,
	List<ProfilePreview> profiles) {
	public ImportPreview {
		profiles = List.copyOf(profiles);
	}

	/** What will happen to one profile. */
	public enum Action {
		CREATE,
		MERGE,
		REPLACE,
		/** REPLACE refused because the local copy was modified more recently. */
		BLOCKED_LOCAL_NEWER
	}

	/**
	 * @param finalName name the profile will have after import (renamed if the name is used by another profile)
	 */
	public record ProfilePreview(UUID id, String name, String finalName, Action action, String existingName,
		List<EntryPreview> entries, CompatibilityReport compatibility) {
		public ProfilePreview {
			entries = List.copyOf(entries);
		}

		public long count(EntryStatus status) {
			return entries.stream().filter(e -> e.status() == status).count();
		}
	}

	/** One imported entry. */
	public record EntryPreview(ConfigKey key, ConfigValue incoming, ConfigValue existing, EntryStatus status) {
	}
}
