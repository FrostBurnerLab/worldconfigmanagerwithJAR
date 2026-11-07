package dev.worldconfigmanager.api;

import java.util.Objects;

/**
 * A requested change of a single setting.
 *
 * @param key           provider-local key
 * @param previousValue value before the change ({@code null} if unknown)
 * @param newValue      value to apply
 */
public record ConfigChange(String key, ConfigValue previousValue, ConfigValue newValue) {
	public ConfigChange {
		Objects.requireNonNull(key, "key");
		Objects.requireNonNull(newValue, "newValue");
	}
}
