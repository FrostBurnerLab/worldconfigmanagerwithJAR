package dev.worldconfigmanager.fabric.client;

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
import net.minecraft.client.Minecraft;
import net.minecraft.client.OptionInstance;
import net.minecraft.client.Options;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.function.Function;

/**
 * CLIENT-scoped provider for a curated set of vanilla video/accessibility/control options. Values are validated by the
 * options' own value sets and applied on the render thread; {@code options.txt} is saved after each change.
 */
public final class ClientOptionsProvider implements ConfigProvider {
	public static final String ID = "minecraft:client_options";

	private record Option(String key, String translationKey, ConfigCategory category, Function<Options, OptionInstance<?>> getter,
		double min, double max) {
	}

	private static final List<Option> OPTIONS = List.of(
		new Option("fov", "options.fov", ConfigCategory.VANILLA, Options::fov, 30, 110),
		new Option("render_distance", "options.renderDistance", ConfigCategory.PERFORMANCE, Options::renderDistance, 2, 32),
		new Option("simulation_distance", "options.simulationDistance", ConfigCategory.PERFORMANCE, Options::simulationDistance, 5, 32),
		new Option("entity_distance_scaling", "options.entityDistanceScaling", ConfigCategory.PERFORMANCE, Options::entityDistanceScaling, 0.5, 5.0),
		new Option("gamma", "options.gamma", ConfigCategory.ENVIRONMENT, Options::gamma, 0, 1),
		new Option("auto_jump", "options.autoJump", ConfigCategory.GAMEPLAY, Options::autoJump, Double.NaN, Double.NaN),
		new Option("bob_view", "options.viewBobbing", ConfigCategory.VANILLA, Options::bobView, Double.NaN, Double.NaN),
		new Option("show_subtitles", "options.showSubtitles", ConfigCategory.VANILLA, Options::showSubtitles, Double.NaN, Double.NaN),
		new Option("toggle_crouch", "key.sneak", ConfigCategory.GAMEPLAY, Options::toggleCrouch, Double.NaN, Double.NaN),
		new Option("toggle_sprint", "key.sprint", ConfigCategory.GAMEPLAY, Options::toggleSprint, Double.NaN, Double.NaN),
		new Option("chat_opacity", "options.chat.opacity", ConfigCategory.MULTIPLAYER, Options::chatOpacity, 0, 1));

	private final ConfigMetadata metadata;

	public ClientOptionsProvider(String minecraftVersion) {
		this.metadata = new ConfigMetadata(ID, "minecraft", LocalizedText.translatable("wcm.provider.client_options", "Client Options"),
			minecraftVersion, 1, 1, Set.of(ConfigScope.CLIENT), false);
	}

	@Override
	public ConfigMetadata metadata() {
		return metadata;
	}

	@Override
	public boolean isAvailable(ProviderContext context) {
		return ConfigProvider.super.isAvailable(context) && context.service(Minecraft.class).isPresent();
	}

	private static Options options() {
		return Minecraft.getInstance().options;
	}

	private static Map<String, Option> byKey() {
		Map<String, Option> map = new LinkedHashMap<>();
		OPTIONS.forEach(o -> map.put(o.key(), o));
		return map;
	}

	@Override
	public List<ConfigDefinition> definitions(ProviderContext context) {
		List<ConfigDefinition> list = new ArrayList<>();
		for (Option o : OPTIONS) {
			Object current = o.getter().apply(options()).get();
			ConfigDefinition.Builder b;
			switch (current) {
				case Boolean ignored -> b = ConfigDefinition.builder(o.key(), ValueType.BOOLEAN, ConfigValue.of(false));
				case Integer ignored -> b = ConfigDefinition.builder(o.key(), ValueType.INTEGER, ConfigValue.of((long) o.min()))
					.constraints(ValueConstraints.range(o.min(), o.max()));
				case Double ignored -> b = ConfigDefinition.builder(o.key(), ValueType.DECIMAL, ConfigValue.of(o.min()))
					.constraints(ValueConstraints.range(o.min(), o.max()));
				default -> {
					continue;
				}
			}
			ConfigValue defaultValue = defaults().get(o.key());
			if (defaultValue != null && defaultValue.type() == b.build().type()) {
				b = rebuildWithDefault(b.build(), defaultValue);
			}
			list.add(b.displayName(LocalizedText.translatable(o.translationKey(), o.key()))
				.description(LocalizedText.translatable("wcm.client_option.description", "Client option, stored in options.txt."))
				.category(o.category()).scope(ConfigScope.CLIENT).reloadBehavior(ReloadBehavior.HOT_RELOAD)
				.validator(v -> validateWithOption(o, v)).build());
		}
		return list;
	}

	private static ConfigDefinition.Builder rebuildWithDefault(ConfigDefinition def, ConfigValue defaultValue) {
		return ConfigDefinition.builder(def.key(), def.type(), defaultValue).constraints(def.constraints());
	}

	/** Vanilla defaults for the managed options. */
	private static Map<String, ConfigValue> defaults() {
		Map<String, ConfigValue> d = new HashMap<>();
		d.put("fov", ConfigValue.of(70L));
		d.put("render_distance", ConfigValue.of(12L));
		d.put("simulation_distance", ConfigValue.of(12L));
		d.put("entity_distance_scaling", ConfigValue.of(1.0));
		d.put("gamma", ConfigValue.of(0.5));
		d.put("auto_jump", ConfigValue.of(false));
		d.put("bob_view", ConfigValue.of(true));
		d.put("show_subtitles", ConfigValue.of(false));
		d.put("toggle_crouch", ConfigValue.of(false));
		d.put("toggle_sprint", ConfigValue.of(false));
		d.put("chat_opacity", ConfigValue.of(1.0));
		return d;
	}

	private static Object toRaw(ConfigValue value) {
		return switch (value) {
			case ConfigValue.BoolValue(boolean b) -> b;
			case ConfigValue.IntValue(long l) when l >= Integer.MIN_VALUE && l <= Integer.MAX_VALUE -> (int) l;
			case ConfigValue.DecimalValue(double d) -> d;
			default -> null;
		};
	}

	private static ValidationResult validateWithOption(Option o, ConfigValue value) {
		Object raw = toRaw(value);
		if (raw == null) {
			return ValidationResult.error("wcm.validation.type", "number", value.type().id());
		}
		return accepts(o.getter().apply(options()), raw) ? ValidationResult.ok() : ValidationResult.error("wcm.validation.rejected_by_game", value.display());
	}

	@SuppressWarnings("unchecked")
	private static <T> boolean accepts(OptionInstance<T> option, Object raw) {
		Object current = option.get();
		if (current == null || !current.getClass().isInstance(raw)) {
			return false;
		}
		return option.values().validateValue((T) raw).isPresent();
	}

	@Override
	public Optional<ConfigValue> read(String key, ProviderContext context) {
		Option o = byKey().get(key);
		if (o == null) {
			return Optional.empty();
		}
		return switch (o.getter().apply(options()).get()) {
			case Boolean b -> Optional.of(ConfigValue.of(b));
			case Integer i -> Optional.of(ConfigValue.of((long) i));
			case Double d -> Optional.of(ConfigValue.of(d));
			default -> Optional.empty();
		};
	}

	@Override
	public ConfigApplyResult apply(List<ConfigChange> changes, ProviderContext context) {
		Map<String, Option> options = byKey();
		List<String> applied = new ArrayList<>();
		Map<String, String> failures = new HashMap<>();
		for (ConfigChange change : changes) {
			Option o = options.get(change.key());
			Object raw = toRaw(change.newValue());
			if (o == null || raw == null || !set(o.getter().apply(options()), raw)) {
				failures.put(change.key(), "Option rejected the value");
			} else {
				applied.add(change.key());
			}
		}
		if (!applied.isEmpty()) {
			options().save();
		}
		return new ConfigApplyResult(applied, failures, Set.of());
	}

	@SuppressWarnings("unchecked")
	private static <T> boolean set(OptionInstance<T> option, Object raw) {
		if (!accepts(option, raw)) {
			return false;
		}
		option.set((T) raw);
		return raw.equals(option.get());
	}
}
