package dev.worldconfigmanager.api;

import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Result of applying changes to a provider.
 *
 * @param applied         keys whose new value is now stored
 * @param failures        keys that could not be applied, mapped to an explanation
 * @param restartRequired applied keys that only take effect after a reload or restart
 */
public record ConfigApplyResult(List<String> applied, Map<String, String> failures, Set<String> restartRequired) {
	public ConfigApplyResult {
		applied = List.copyOf(applied);
		failures = Map.copyOf(failures);
		restartRequired = Set.copyOf(restartRequired);
	}

	public static ConfigApplyResult success(List<String> applied, Set<String> restartRequired) {
		return new ConfigApplyResult(applied, Map.of(), restartRequired);
	}

	public static ConfigApplyResult failure(List<String> applied, Map<String, String> failures) {
		return new ConfigApplyResult(applied, failures, Set.of());
	}

	/** @return {@code true} when every requested change was applied */
	public boolean isSuccess() {
		return failures.isEmpty();
	}
}
