package dev.worldconfigmanager.api;

import java.util.Objects;

/**
 * Globally unique identifier of a setting: the provider id plus the provider-local key.
 *
 * @param providerId identifier of the owning provider, e.g. {@code minecraft:gamerules}
 * @param key        provider-local key, e.g. {@code minecraft:keep_inventory}
 */
public record ConfigKey(String providerId, String key) implements Comparable<ConfigKey> {
	public ConfigKey {
		Objects.requireNonNull(providerId, "providerId");
		Objects.requireNonNull(key, "key");
		if (providerId.isBlank() || key.isBlank()) {
			throw new IllegalArgumentException("providerId and key must not be blank");
		}
	}

	@Override
	public int compareTo(ConfigKey other) {
		int cmp = providerId.compareTo(other.providerId);
		return cmp != 0 ? cmp : key.compareTo(other.key);
	}

	@Override
	public String toString() {
		return providerId + "/" + key;
	}
}
