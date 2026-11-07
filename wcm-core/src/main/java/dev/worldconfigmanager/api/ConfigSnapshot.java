package dev.worldconfigmanager.api;

import java.time.Instant;
import java.util.Map;
import java.util.Objects;
import java.util.TreeMap;

/**
 * Point-in-time copy of a provider's values.
 *
 * @param providerId      provider the values belong to
 * @param providerVersion provider version at capture time
 * @param capturedAt      capture timestamp
 * @param values          key to value
 */
public record ConfigSnapshot(String providerId, String providerVersion, Instant capturedAt, Map<String, ConfigValue> values) {
	public ConfigSnapshot {
		Objects.requireNonNull(providerId, "providerId");
		Objects.requireNonNull(providerVersion, "providerVersion");
		Objects.requireNonNull(capturedAt, "capturedAt");
		values = java.util.Collections.unmodifiableMap(new TreeMap<>(values));
	}
}
