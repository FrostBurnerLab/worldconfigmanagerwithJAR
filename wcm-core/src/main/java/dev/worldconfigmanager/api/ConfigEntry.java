package dev.worldconfigmanager.api;

import java.util.Objects;

/**
 * A setting together with its current value, as presented in the configuration browser.
 *
 * @param providerId   owning provider
 * @param definition   setting definition
 * @param currentValue current value ({@code null} if it could not be read)
 * @param available    whether the setting can currently be read and written
 */
public record ConfigEntry(String providerId, ConfigDefinition definition, ConfigValue currentValue, boolean available) {
	public ConfigEntry {
		Objects.requireNonNull(providerId, "providerId");
		Objects.requireNonNull(definition, "definition");
	}

	public ConfigKey key() {
		return definition.globalKey(providerId);
	}

	public boolean isDefault() {
		return definition.defaultValue().equals(currentValue);
	}
}
