package dev.worldconfigmanager.core.registry;

import dev.worldconfigmanager.api.ConfigDefinition;
import dev.worldconfigmanager.api.ConfigKey;
import dev.worldconfigmanager.api.ConfigProvider;
import dev.worldconfigmanager.api.ConfigScope;
import dev.worldconfigmanager.api.ProviderContext;
import dev.worldconfigmanager.api.ProviderRegistrar;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Registry of configuration providers with a per-provider definition cache. Definitions are fetched lazily and
 * cached until {@link #invalidate(String)} or {@link #invalidateAll()} is called (e.g. when a server stops).
 */
public final class ProviderRegistry implements ProviderRegistrar {
	private static final Logger LOGGER = LoggerFactory.getLogger("WorldConfigManager");

	private final Map<String, ConfigProvider> providers = Collections.synchronizedMap(new LinkedHashMap<>());
	private final Map<String, Map<String, ConfigDefinition>> definitionCache = new ConcurrentHashMap<>();

	@Override
	public void register(ConfigProvider provider) {
		String id = provider.metadata().providerId();
		synchronized (providers) {
			if (providers.containsKey(id)) {
				throw new IllegalArgumentException("Duplicate configuration provider id: " + id);
			}
			providers.put(id, provider);
		}
		LOGGER.info("Registered configuration provider {} ({} v{})", id, provider.metadata().modId(), provider.metadata().version());
	}

	public List<ConfigProvider> all() {
		synchronized (providers) {
			return List.copyOf(providers.values());
		}
	}

	public Optional<ConfigProvider> get(String providerId) {
		return Optional.ofNullable(providers.get(providerId));
	}

	/** @return providers that serve at least one scope of the context and report themselves available */
	public List<ConfigProvider> available(ProviderContext context) {
		List<ConfigProvider> result = new ArrayList<>();
		for (ConfigProvider provider : all()) {
			try {
				if (provider.isAvailable(context)) {
					result.add(provider);
				}
			} catch (RuntimeException e) {
				LOGGER.warn("Provider {} failed its availability check", provider.metadata().providerId(), e);
			}
		}
		return result;
	}

	/**
	 * Returns the (cached) definitions of a provider, restricted to the context's scopes. A provider that throws is
	 * treated as offering no settings and the error is logged.
	 */
	public Map<String, ConfigDefinition> definitions(ConfigProvider provider, ProviderContext context) {
		String id = provider.metadata().providerId();
		Map<String, ConfigDefinition> all = definitionCache.computeIfAbsent(id, k -> loadDefinitions(provider, context));
		Map<String, ConfigDefinition> scoped = new LinkedHashMap<>();
		all.forEach((key, def) -> {
			if (context.scopes().contains(def.scope())) {
				scoped.put(key, def);
			}
		});
		return scoped;
	}

	private static Map<String, ConfigDefinition> loadDefinitions(ConfigProvider provider, ProviderContext context) {
		Map<String, ConfigDefinition> map = new LinkedHashMap<>();
		try {
			for (ConfigDefinition def : provider.definitions(context)) {
				if (!provider.metadata().supportedScopes().contains(def.scope())) {
					LOGGER.warn("Provider {} declares {} in undeclared scope {}; ignored", provider.metadata().providerId(),
						def.key(), def.scope());
					continue;
				}
				if (map.putIfAbsent(def.key(), def) != null) {
					LOGGER.warn("Provider {} declares duplicate key {}; ignored", provider.metadata().providerId(), def.key());
				}
			}
		} catch (RuntimeException e) {
			LOGGER.error("Provider {} failed to list its settings", provider.metadata().providerId(), e);
		}
		return Collections.unmodifiableMap(map);
	}

	public Optional<ConfigDefinition> definition(ConfigKey key, ProviderContext context) {
		return get(key.providerId())
			.filter(p -> safeAvailable(p, context))
			.map(p -> definitions(p, context).get(key.key()));
	}

	private static boolean safeAvailable(ConfigProvider provider, ProviderContext context) {
		try {
			return provider.isAvailable(context);
		} catch (RuntimeException e) {
			return false;
		}
	}

	/** @return whether any registered provider supports the scope */
	public boolean supportsScope(ConfigScope scope) {
		return all().stream().anyMatch(p -> p.metadata().supportedScopes().contains(scope));
	}

	public void invalidate(String providerId) {
		definitionCache.remove(providerId);
	}

	public void invalidateAll() {
		definitionCache.clear();
	}
}
