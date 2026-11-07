package dev.worldconfigmanager.core.config;

import dev.worldconfigmanager.api.ConfigDefinition;
import dev.worldconfigmanager.api.ConfigEntry;
import dev.worldconfigmanager.api.ConfigKey;
import dev.worldconfigmanager.api.ConfigProvider;
import dev.worldconfigmanager.api.ConfigValue;
import dev.worldconfigmanager.api.ProviderContext;
import dev.worldconfigmanager.api.ValidationResult;
import dev.worldconfigmanager.core.registry.ProviderRegistry;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.TreeMap;

/** Live view of all managed settings available in a provider context. */
public final class ConfigService implements DefinitionLookup {
	private static final Logger LOGGER = LoggerFactory.getLogger("WorldConfigManager");

	private final ProviderRegistry registry;
	private final ProviderContext context;

	public ConfigService(ProviderRegistry registry, ProviderContext context) {
		this.registry = registry;
		this.context = context;
	}

	public ProviderContext context() {
		return context;
	}

	public ProviderRegistry registry() {
		return registry;
	}

	public List<ConfigProvider> availableProviders() {
		return registry.available(context);
	}

	public Optional<ConfigProvider> availableProvider(String providerId) {
		return availableProviders().stream().filter(p -> p.metadata().providerId().equals(providerId)).findFirst();
	}

	/** @return every setting with its current value; unreadable values are reported as unavailable */
	public List<ConfigEntry> entries() {
		List<ConfigEntry> entries = new ArrayList<>();
		for (ConfigProvider provider : availableProviders()) {
			String id = provider.metadata().providerId();
			for (ConfigDefinition def : registry.definitions(provider, context).values()) {
				ConfigValue value = null;
				boolean available = true;
				try {
					value = provider.read(def.key(), context).orElse(null);
					available = value != null;
				} catch (RuntimeException e) {
					LOGGER.warn("Provider {} failed to read {}", id, def.key(), e);
					available = false;
				}
				entries.add(new ConfigEntry(id, def, value, available));
			}
		}
		return entries;
	}

	public Map<ConfigKey, ConfigValue> currentValues() {
		Map<ConfigKey, ConfigValue> values = new TreeMap<>();
		for (ConfigEntry entry : entries()) {
			if (entry.currentValue() != null) {
				values.put(entry.key(), entry.currentValue());
			}
		}
		return values;
	}

	public Optional<ConfigValue> read(ConfigKey key) {
		return availableProvider(key.providerId()).flatMap(p -> {
			try {
				return p.read(key.key(), context);
			} catch (RuntimeException e) {
				LOGGER.warn("Provider {} failed to read {}", key.providerId(), key.key(), e);
				return Optional.empty();
			}
		});
	}

	@Override
	public Optional<ConfigDefinition> definition(ConfigKey key) {
		return registry.definition(key, context);
	}

	@Override
	public Optional<String> providerVersion(String providerId) {
		return availableProvider(providerId).map(p -> p.metadata().version());
	}

	/** @return provider id to version for all available providers */
	public Map<String, String> providerVersions() {
		Map<String, String> versions = new LinkedHashMap<>();
		availableProviders().forEach(p -> versions.put(p.metadata().providerId(), p.metadata().version()));
		return versions;
	}

	/** Validates a value through its provider; unknown settings and provider errors are reported as invalid. */
	public ValidationResult validate(ConfigKey key, ConfigValue value) {
		Optional<ConfigProvider> provider = availableProvider(key.providerId());
		if (provider.isEmpty()) {
			return ValidationResult.error("wcm.validation.provider_unavailable", key.providerId());
		}
		if (definition(key).isEmpty()) {
			return ValidationResult.error("wcm.validation.unknown_key", key.key());
		}
		try {
			ValidationResult result = provider.get().validate(key.key(), value, context);
			return result == null ? ValidationResult.error("wcm.validation.provider_error", "null result") : result;
		} catch (RuntimeException e) {
			LOGGER.warn("Provider {} failed to validate {}", key.providerId(), key.key(), e);
			return ValidationResult.error("wcm.validation.provider_error", String.valueOf(e.getMessage()));
		}
	}
}
