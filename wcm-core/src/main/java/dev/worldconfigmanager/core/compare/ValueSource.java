package dev.worldconfigmanager.core.compare;

import dev.worldconfigmanager.api.ConfigKey;
import dev.worldconfigmanager.api.ConfigValue;
import dev.worldconfigmanager.core.backup.Backup;
import dev.worldconfigmanager.core.profile.Profile;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * A comparable set of values: a profile, a backup or the live configuration.
 *
 * @param label            display label
 * @param values           typed values by key
 * @param providerVersions provider versions recorded with the values
 */
public record ValueSource(String label, Map<ConfigKey, ConfigValue> values, Map<String, String> providerVersions) {
	public ValueSource {
		values = Map.copyOf(values);
		providerVersions = Map.copyOf(providerVersions);
	}

	public static ValueSource of(Profile profile) {
		return new ValueSource(profile.name(), profile.entries(), profile.providerVersions());
	}

	public static ValueSource of(Backup backup, String label) {
		Map<String, String> versions = new LinkedHashMap<>();
		backup.snapshots().forEach((id, s) -> versions.put(id, s.providerVersion()));
		return new ValueSource(label, backup.values(), versions);
	}
}
