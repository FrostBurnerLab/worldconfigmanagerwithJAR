package dev.worldconfigmanager.core.profile;

import dev.worldconfigmanager.api.ConfigKey;
import dev.worldconfigmanager.api.ConfigValue;

import java.util.Objects;

/** A single stored setting in a profile. */
public record ProfileEntry(ConfigKey key, ConfigValue value) {
	public ProfileEntry {
		Objects.requireNonNull(key, "key");
		Objects.requireNonNull(value, "value");
	}
}
