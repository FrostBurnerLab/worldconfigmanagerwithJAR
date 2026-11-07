package dev.worldconfigmanager.core;

import dev.worldconfigmanager.api.ConfigApplyResult;
import dev.worldconfigmanager.api.ConfigCategory;
import dev.worldconfigmanager.api.ConfigChange;
import dev.worldconfigmanager.api.ConfigDefinition;
import dev.worldconfigmanager.api.ConfigMetadata;
import dev.worldconfigmanager.api.ConfigProvider;
import dev.worldconfigmanager.api.ConfigScope;
import dev.worldconfigmanager.api.ConfigValue;
import dev.worldconfigmanager.api.LocalizedText;
import dev.worldconfigmanager.api.ProviderContext;
import dev.worldconfigmanager.api.ReloadBehavior;
import dev.worldconfigmanager.api.ValueConstraints;
import dev.worldconfigmanager.api.ValueType;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

/** In-memory provider used in tests. Can be told to fail or throw for specific keys. */
public final class TestProvider implements ConfigProvider {
	public final Map<String, ConfigValue> values = new LinkedHashMap<>();
	public final Set<String> failOnApply = new HashSet<>();
	public final Set<String> throwOnApply = new HashSet<>();
	public boolean failRestore;
	public boolean available = true;
	public int applyCalls;
	private final ConfigMetadata metadata;
	private final List<ConfigDefinition> definitions = new ArrayList<>();

	public TestProvider(String id, String version, boolean transactional) {
		this.metadata = new ConfigMetadata(id, "testmod", LocalizedText.literal(id), version, 1, 1,
			Set.of(ConfigScope.WORLD, ConfigScope.SERVER, ConfigScope.CLIENT), transactional);
	}

	/** Creates a provider with a boolean {@code flag}, an integer {@code count} (0-100) and an enum {@code mode}. */
	public static TestProvider standard(String id) {
		TestProvider p = new TestProvider(id, "1.0.0", false);
		p.define(ConfigDefinition.builder("flag", ValueType.BOOLEAN, ConfigValue.of(false)).category(ConfigCategory.GAMEPLAY).build());
		p.define(ConfigDefinition.builder("count", ValueType.INTEGER, ConfigValue.of(10L)).constraints(ValueConstraints.range(0, 100))
			.category(ConfigCategory.PERFORMANCE).build());
		p.define(ConfigDefinition.builder("mode", ValueType.ENUM, ConfigValue.ofEnum("easy"))
			.constraints(ValueConstraints.oneOf(List.of("easy", "hard"))).reloadBehavior(ReloadBehavior.GAME_RESTART).build());
		return p;
	}

	public TestProvider define(ConfigDefinition definition) {
		definitions.add(definition);
		values.put(definition.key(), definition.defaultValue());
		return this;
	}

	@Override
	public ConfigMetadata metadata() {
		return metadata;
	}

	@Override
	public List<ConfigDefinition> definitions(ProviderContext context) {
		return List.copyOf(definitions);
	}

	@Override
	public Optional<ConfigValue> read(String key, ProviderContext context) {
		return Optional.ofNullable(values.get(key));
	}

	@Override
	public boolean isAvailable(ProviderContext context) {
		return available && ConfigProvider.super.isAvailable(context);
	}

	@Override
	public ConfigApplyResult apply(List<ConfigChange> changes, ProviderContext context) {
		applyCalls++;
		List<String> applied = new ArrayList<>();
		Map<String, String> failures = new HashMap<>();
		for (ConfigChange change : changes) {
			if (throwOnApply.contains(change.key())) {
				throw new IllegalStateException("boom " + change.key());
			}
			if (failOnApply.contains(change.key())) {
				failures.put(change.key(), "refused");
				continue;
			}
			values.put(change.key(), change.newValue());
			applied.add(change.key());
		}
		return new ConfigApplyResult(applied, failures, Set.of());
	}

	@Override
	public ConfigApplyResult restore(dev.worldconfigmanager.api.ConfigSnapshot snapshot, ProviderContext context) {
		if (failRestore) {
			return ConfigApplyResult.failure(List.of(), Map.of("*", "restore refused"));
		}
		values.putAll(snapshot.values());
		return ConfigApplyResult.success(List.copyOf(snapshot.values().keySet()), Set.of());
	}
}
