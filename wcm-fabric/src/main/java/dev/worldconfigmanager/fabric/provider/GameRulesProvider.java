package dev.worldconfigmanager.fabric.provider;

import com.mojang.brigadier.arguments.ArgumentType;
import com.mojang.brigadier.arguments.IntegerArgumentType;
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
import dev.worldconfigmanager.api.ValidationResult;
import dev.worldconfigmanager.api.ValueConstraints;
import dev.worldconfigmanager.api.ValueType;
import net.minecraft.server.MinecraftServer;
import net.minecraft.world.level.gamerules.GameRule;
import net.minecraft.world.level.gamerules.GameRuleCategory;
import net.minecraft.world.level.gamerules.GameRuleType;
import net.minecraft.world.level.gamerules.GameRules;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

/**
 * Built-in provider for vanilla game rules. Rules are discovered at runtime from the server's {@link GameRules}
 * (which already respects enabled feature flags), so no list of rules is hard-coded. Keys are the rules' namespaced
 * identifiers, e.g. {@code minecraft:keep_inventory}.
 */
public final class GameRulesProvider implements ConfigProvider {
	public static final String ID = "minecraft:gamerules";

	private final ConfigMetadata metadata;

	public GameRulesProvider(String minecraftVersion) {
		this.metadata = new ConfigMetadata(ID, "minecraft", LocalizedText.translatable("wcm.provider.gamerules", "Game Rules"),
			minecraftVersion, 1, 1, Set.of(ConfigScope.WORLD), false);
	}

	@Override
	public ConfigMetadata metadata() {
		return metadata;
	}

	@Override
	public boolean isAvailable(ProviderContext context) {
		return ConfigProvider.super.isAvailable(context) && context.service(MinecraftServer.class).isPresent();
	}

	private static MinecraftServer server(ProviderContext context) {
		return context.service(MinecraftServer.class).orElseThrow(() -> new IllegalStateException("No server available"));
	}

	private static Map<String, GameRule<?>> rules(ProviderContext context) {
		Map<String, GameRule<?>> map = new LinkedHashMap<>();
		server(context).getGameRules().availableRules().forEach(rule -> map.put(rule.getIdentifier().toString(), rule));
		return map;
	}

	@Override
	public List<ConfigDefinition> definitions(ProviderContext context) {
		List<ConfigDefinition> list = new ArrayList<>();
		for (Map.Entry<String, GameRule<?>> e : rules(context).entrySet()) {
			definition(e.getKey(), e.getValue()).ifPresent(list::add);
		}
		return list;
	}

	private static Optional<ConfigDefinition> definition(String key, GameRule<?> rule) {
		ConfigDefinition.Builder builder;
		if (rule.gameRuleType() == GameRuleType.BOOL && rule.defaultValue() instanceof Boolean b) {
			builder = ConfigDefinition.builder(key, ValueType.BOOLEAN, ConfigValue.of(b));
		} else if (rule.gameRuleType() == GameRuleType.INT && rule.defaultValue() instanceof Integer i) {
			builder = ConfigDefinition.builder(key, ValueType.INTEGER, ConfigValue.of((long) i));
			ArgumentType<?> argument = rule.argument();
			double min = Integer.MIN_VALUE;
			double max = Integer.MAX_VALUE;
			if (argument instanceof IntegerArgumentType range) {
				min = range.getMinimum();
				max = range.getMaximum();
			}
			builder.constraints(ValueConstraints.range(min, max));
		} else {
			return Optional.empty();
		}
		String descriptionId = rule.getDescriptionId();
		return Optional.of(builder
			.displayName(LocalizedText.translatable(descriptionId, key))
			.description(LocalizedText.translatable(descriptionId + ".description", ""))
			.category(category(rule.category()))
			.scope(ConfigScope.WORLD)
			.reloadBehavior(ReloadBehavior.HOT_RELOAD)
			.validator(value -> validateWithGame(rule, value))
			.build());
	}

	private static ConfigCategory category(GameRuleCategory category) {
		if (category.equals(GameRuleCategory.MOBS) || category.equals(GameRuleCategory.SPAWNING)) {
			return ConfigCategory.MOBS_AND_ENTITIES;
		}
		if (category.equals(GameRuleCategory.PLAYER) || category.equals(GameRuleCategory.DROPS)) {
			return ConfigCategory.GAMEPLAY;
		}
		if (category.equals(GameRuleCategory.UPDATES)) {
			return ConfigCategory.ENVIRONMENT;
		}
		if (category.equals(GameRuleCategory.CHAT)) {
			return ConfigCategory.MULTIPLAYER;
		}
		return ConfigCategory.VANILLA;
	}

	/** Lets the game's own codec confirm the value, so WCM never accepts something {@code /gamerule} would reject. */
	private static ValidationResult validateWithGame(GameRule<?> rule, ConfigValue value) {
		String serialized = serialize(value);
		if (serialized == null) {
			return ValidationResult.error("wcm.validation.type", rule.gameRuleType().getSerializedName(), value.type().id());
		}
		return rule.deserialize(serialized).result().isPresent()
			? ValidationResult.ok()
			: ValidationResult.error("wcm.validation.rejected_by_game", serialized);
	}

	private static String serialize(ConfigValue value) {
		return switch (value) {
			case ConfigValue.BoolValue(boolean b) -> Boolean.toString(b);
			case ConfigValue.IntValue(long l) when l >= Integer.MIN_VALUE && l <= Integer.MAX_VALUE -> Long.toString(l);
			default -> null;
		};
	}

	@Override
	public Optional<ConfigValue> read(String key, ProviderContext context) {
		GameRule<?> rule = rules(context).get(key);
		if (rule == null) {
			return Optional.empty();
		}
		Object value = server(context).getGameRules().get(rule);
		if (value instanceof Boolean b) {
			return Optional.of(ConfigValue.of(b));
		}
		if (value instanceof Integer i) {
			return Optional.of(ConfigValue.of((long) i));
		}
		return Optional.empty();
	}

	@Override
	public ConfigApplyResult apply(List<ConfigChange> changes, ProviderContext context) {
		MinecraftServer server = server(context);
		GameRules gameRules = server.getGameRules();
		Map<String, GameRule<?>> rules = rules(context);
		List<String> applied = new ArrayList<>();
		Map<String, String> failures = new HashMap<>();
		for (ConfigChange change : changes) {
			GameRule<?> rule = rules.get(change.key());
			if (rule == null) {
				failures.put(change.key(), "Unknown game rule");
				continue;
			}
			if (set(gameRules, rule, change.newValue(), server)) {
				applied.add(change.key());
			} else {
				failures.put(change.key(), "The game rejected the value");
			}
		}
		return new ConfigApplyResult(applied, failures, Set.of());
	}

	private static <T> boolean set(GameRules gameRules, GameRule<T> rule, ConfigValue value, MinecraftServer server) {
		String serialized = serialize(value);
		if (serialized == null) {
			return false;
		}
		Optional<T> parsed = rule.deserialize(serialized).result();
		if (parsed.isEmpty()) {
			return false;
		}
		gameRules.set(rule, parsed.get(), server);
		return parsed.get().equals(gameRules.get(rule));
	}
}
