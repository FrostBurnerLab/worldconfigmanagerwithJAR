package dev.worldconfigmanager.fabric.provider;

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
import net.minecraft.world.Difficulty;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

/**
 * Built-in provider for world-level vanilla settings that are not game rules. Currently: difficulty. Respects the
 * game's restrictions: hardcore worlds are always hard and a locked difficulty cannot be changed.
 */
public final class WorldSettingsProvider implements ConfigProvider {
	public static final String ID = "minecraft:world";
	public static final String DIFFICULTY = "difficulty";

	private final ConfigMetadata metadata;

	public WorldSettingsProvider(String minecraftVersion) {
		this.metadata = new ConfigMetadata(ID, "minecraft", LocalizedText.translatable("wcm.provider.world", "World Settings"),
			minecraftVersion, 1, 1, Set.of(ConfigScope.WORLD), true);
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

	@Override
	public List<ConfigDefinition> definitions(ProviderContext context) {
		List<String> names = Arrays.stream(Difficulty.values()).map(Difficulty::getSerializedName).toList();
		return List.of(ConfigDefinition.builder(DIFFICULTY, ValueType.ENUM, ConfigValue.ofEnum(Difficulty.NORMAL.getSerializedName()))
			.displayName(LocalizedText.translatable("options.difficulty", "Difficulty"))
			.description(LocalizedText.translatable("wcm.setting.difficulty.description", "The world's difficulty."))
			.category(ConfigCategory.GAMEPLAY)
			.constraints(ValueConstraints.oneOf(names))
			.scope(ConfigScope.WORLD)
			.reloadBehavior(ReloadBehavior.HOT_RELOAD)
			.build());
	}

	@Override
	public Optional<ConfigValue> read(String key, ProviderContext context) {
		if (!DIFFICULTY.equals(key)) {
			return Optional.empty();
		}
		return Optional.of(ConfigValue.ofEnum(server(context).getWorldData().getDifficulty().getSerializedName()));
	}

	@Override
	public ValidationResult validate(String key, ConfigValue value, ProviderContext context) {
		ValidationResult base = ConfigProvider.super.validate(key, value, context);
		if (!base.valid()) {
			return base;
		}
		MinecraftServer server = server(context);
		ConfigValue current = read(key, context).orElse(null);
		if (value.equals(current)) {
			return ValidationResult.ok();
		}
		if (server.isHardcore()) {
			return ValidationResult.error("wcm.validation.difficulty_hardcore");
		}
		if (server.getWorldData().isDifficultyLocked()) {
			return ValidationResult.error("wcm.validation.difficulty_locked");
		}
		return ValidationResult.ok();
	}

	@Override
	public ConfigApplyResult apply(List<ConfigChange> changes, ProviderContext context) {
		MinecraftServer server = server(context);
		List<String> applied = new ArrayList<>();
		Map<String, String> failures = new HashMap<>();
		for (ConfigChange change : changes) {
			if (DIFFICULTY.equals(change.key()) && change.newValue() instanceof ConfigValue.EnumValue(String name)) {
				Difficulty difficulty = Difficulty.byName(name);
				if (difficulty == null) {
					failures.put(change.key(), "Unknown difficulty " + name);
					continue;
				}
				server.setDifficulty(difficulty, false);
				if (server.getWorldData().getDifficulty() == difficulty) {
					applied.add(change.key());
				} else {
					failures.put(change.key(), "The game did not accept the difficulty");
				}
			} else {
				failures.put(change.key(), "Unknown setting");
			}
		}
		return new ConfigApplyResult(applied, failures, Set.of());
	}
}
