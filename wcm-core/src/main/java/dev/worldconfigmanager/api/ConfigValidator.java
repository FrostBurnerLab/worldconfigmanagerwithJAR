package dev.worldconfigmanager.api;

/** Custom validation hook for a single setting, applied after the built-in constraint checks. */
@FunctionalInterface
public interface ConfigValidator {
	ConfigValidator ACCEPT_ALL = value -> ValidationResult.ok();

	ValidationResult validate(ConfigValue value);
}
