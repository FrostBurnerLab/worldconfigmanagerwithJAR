package dev.worldconfigmanager.api;

import java.time.Instant;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * A source of managed settings. Implementations expose a fixed set of {@link ConfigDefinition}s, read their current
 * values and apply validated changes.
 *
 * <p>Contract:
 * <ul>
 *     <li>{@link #apply} is only called with values that passed {@link #validate}.</li>
 *     <li>World and server scoped calls happen on the server thread.</li>
 *     <li>Providers must never modify files outside their own configuration.</li>
 *     <li>If {@link ConfigMetadata#transactional()} is {@code true}, {@link #apply} must either apply all changes or
 *     none. Otherwise WCM rolls back partial application by restoring a snapshot.</li>
 * </ul>
 */
public interface ConfigProvider {
	/** @return identity and capabilities */
	ConfigMetadata metadata();

	/**
	 * @return all settings this provider offers. WCM caches the result; call
	 * {@code ProviderRegistry#invalidate} when the set changes.
	 */
	List<ConfigDefinition> definitions(ProviderContext context);

	/** Reads the current value of a setting. */
	Optional<ConfigValue> read(String key, ProviderContext context);

	/** Applies already validated changes. */
	ConfigApplyResult apply(List<ConfigChange> changes, ProviderContext context);

	/** @return whether the provider can currently serve requests in the given context (e.g. a world is loaded) */
	default boolean isAvailable(ProviderContext context) {
		return metadata().supportedScopes().stream().anyMatch(context.scopes()::contains);
	}

	/** Validates a value. The default implementation checks the definition's type and constraints. */
	default ValidationResult validate(String key, ConfigValue value, ProviderContext context) {
		for (ConfigDefinition definition : definitions(context)) {
			if (definition.key().equals(key)) {
				return definition.validate(value);
			}
		}
		return ValidationResult.error("wcm.validation.unknown_key", key);
	}

	/** Captures all current values. */
	default ConfigSnapshot snapshot(ProviderContext context) {
		Map<String, ConfigValue> values = new LinkedHashMap<>();
		for (ConfigDefinition definition : definitions(context)) {
			read(definition.key(), context).ifPresent(v -> values.put(definition.key(), v));
		}
		return new ConfigSnapshot(metadata().providerId(), metadata().version(), Instant.now(), values);
	}

	/** Restores a snapshot previously produced by {@link #snapshot}. */
	default ConfigApplyResult restore(ConfigSnapshot snapshot, ProviderContext context) {
		List<ConfigChange> changes = new ArrayList<>();
		snapshot.values().forEach((key, value) -> {
			ConfigValue current = read(key, context).orElse(null);
			if (!value.equals(current)) {
				changes.add(new ConfigChange(key, current, value));
			}
		});
		return changes.isEmpty() ? ConfigApplyResult.success(List.of(), java.util.Set.of()) : apply(changes, context);
	}
}
