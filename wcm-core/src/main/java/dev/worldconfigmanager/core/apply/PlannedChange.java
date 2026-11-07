package dev.worldconfigmanager.core.apply;

import dev.worldconfigmanager.api.ConfigDefinition;
import dev.worldconfigmanager.api.ConfigKey;
import dev.worldconfigmanager.api.ConfigValue;
import dev.worldconfigmanager.api.ValidationResult;

/**
 * One line of an apply preview.
 *
 * @param definition installed definition, {@code null} when unavailable
 * @param current    current value, {@code null} when unknown
 */
public record PlannedChange(ConfigKey key, ConfigDefinition definition, ConfigValue current, ConfigValue target,
	ChangeStatus status, ValidationResult validation) {

	public boolean restartRequired() {
		return definition != null && definition.reloadBehavior().requiresRestart();
	}
}
