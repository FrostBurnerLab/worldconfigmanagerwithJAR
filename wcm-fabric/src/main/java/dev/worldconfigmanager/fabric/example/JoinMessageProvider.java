package dev.worldconfigmanager.fabric.example;

import com.google.gson.Gson;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParseException;
import com.google.gson.JsonParser;
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
import net.minecraft.world.level.storage.LevelResource;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.AtomicMoveNotSupportedException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

/**
 * Example integration showing how a third-party mod exposes its own settings to WCM, using only the public API
 * ({@code dev.worldconfigmanager.api}). It manages a per-world join announcement that is shown to players when they
 * join. Values are stored in {@code <world>/worldconfigmanager/example_join_message.json}.
 *
 * <p>The provider is transactional: all changes are written in one atomic file replacement, so either every change
 * is stored or none is.
 */
public final class JoinMessageProvider implements ConfigProvider {
	public static final String ID = "worldconfigmanager:join_message";
	private static final Logger LOGGER = LoggerFactory.getLogger("WorldConfigManager/Example");
	private static final Gson GSON = new Gson();
	private static final List<String> COLORS = List.of("white", "gold", "aqua", "green", "light_purple", "red");

	private final ConfigMetadata metadata = new ConfigMetadata(ID, "worldconfigmanager",
		LocalizedText.translatable("wcm.example.provider", "Join Announcement (example integration)"), "1.0.0", 1, 1,
		Set.of(ConfigScope.WORLD), true);
	private final List<ConfigDefinition> definitions = List.of(
		ConfigDefinition.builder("enabled", ValueType.BOOLEAN, ConfigValue.of(false))
			.displayName(LocalizedText.translatable("wcm.example.enabled", "Show join announcement"))
			.description(LocalizedText.translatable("wcm.example.enabled.description", "Send the message below to players when they join."))
			.category(ConfigCategory.MULTIPLAYER).build(),
		ConfigDefinition.builder("message", ValueType.STRING, ConfigValue.of("Welcome to this world!"))
			.displayName(LocalizedText.translatable("wcm.example.message", "Announcement text"))
			.description(LocalizedText.translatable("wcm.example.message.description", "Up to 120 characters."))
			.constraints(ValueConstraints.maxLength(120))
			.validator(v -> ((ConfigValue.StringValue) v).value().isBlank()
				? ValidationResult.error("wcm.example.validation.blank") : ValidationResult.ok())
			.category(ConfigCategory.MULTIPLAYER).build(),
		ConfigDefinition.builder("color", ValueType.ENUM, ConfigValue.ofEnum("gold"))
			.displayName(LocalizedText.translatable("wcm.example.color", "Announcement colour"))
			.constraints(ValueConstraints.oneOf(COLORS))
			.category(ConfigCategory.MULTIPLAYER).build(),
		ConfigDefinition.builder("extraLines", ValueType.LIST, new ConfigValue.ListValue(List.of()))
			.displayName(LocalizedText.translatable("wcm.example.lines", "Extra lines"))
			.description(LocalizedText.translatable("wcm.example.lines.description", "Additional lines (max 3), e.g. server rules."))
			.constraints(new ValueConstraints(null, null, List.of(), 3, null, ValueType.STRING))
			.validator(v -> ((ConfigValue.ListValue) v).values().stream()
				.anyMatch(e -> ((ConfigValue.StringValue) e).value().length() > 120)
				? ValidationResult.error("wcm.validation.max_length", "120") : ValidationResult.ok())
			.category(ConfigCategory.MULTIPLAYER).reloadBehavior(ReloadBehavior.HOT_RELOAD).build());

	private Path loadedFile;
	private Map<String, ConfigValue> values;

	@Override
	public ConfigMetadata metadata() {
		return metadata;
	}

	@Override
	public boolean isAvailable(ProviderContext context) {
		return ConfigProvider.super.isAvailable(context) && context.service(MinecraftServer.class).isPresent();
	}

	@Override
	public List<ConfigDefinition> definitions(ProviderContext context) {
		return definitions;
	}

	@Override
	public Optional<ConfigValue> read(String key, ProviderContext context) {
		return Optional.ofNullable(values(context).get(key));
	}

	@Override
	public ConfigApplyResult apply(List<ConfigChange> changes, ProviderContext context) {
		Map<String, ConfigValue> updated = new LinkedHashMap<>(values(context));
		List<String> keys = new ArrayList<>();
		for (ConfigChange change : changes) {
			updated.put(change.key(), change.newValue());
			keys.add(change.key());
		}
		try {
			write(file(context), updated);
		} catch (IOException e) {
			Map<String, String> failures = new LinkedHashMap<>();
			keys.forEach(k -> failures.put(k, "Could not save: " + e.getMessage()));
			return ConfigApplyResult.failure(List.of(), failures);
		}
		values = updated;
		return ConfigApplyResult.success(keys, Set.of());
	}

	/** @return the announcement lines to send, or an empty list when disabled */
	public List<String> announcement(ProviderContext context) {
		Map<String, ConfigValue> v = values(context);
		if (!(v.get("enabled") instanceof ConfigValue.BoolValue(boolean enabled)) || !enabled) {
			return List.of();
		}
		List<String> lines = new ArrayList<>();
		lines.add(v.get("message").display());
		if (v.get("extraLines") instanceof ConfigValue.ListValue(List<ConfigValue> extra)) {
			extra.forEach(e -> lines.add(e.display()));
		}
		return lines;
	}

	public String color(ProviderContext context) {
		return values(context).get("color").display();
	}

	private static Path file(ProviderContext context) {
		MinecraftServer server = context.service(MinecraftServer.class).orElseThrow();
		return server.getWorldPath(LevelResource.ROOT).resolve("worldconfigmanager").resolve("example_join_message.json");
	}

	private synchronized Map<String, ConfigValue> values(ProviderContext context) {
		Path file = file(context);
		if (values != null && file.equals(loadedFile)) {
			return values;
		}
		Map<String, ConfigValue> loaded = new LinkedHashMap<>();
		definitions.forEach(d -> loaded.put(d.key(), d.defaultValue()));
		if (Files.exists(file)) {
			try {
				JsonObject json = JsonParser.parseString(Files.readString(file, StandardCharsets.UTF_8)).getAsJsonObject();
				readInto(json, loaded);
			} catch (IOException | JsonParseException | IllegalStateException | ClassCastException e) {
				LOGGER.warn("Ignoring unreadable {}: {}", file.getFileName(), e.getMessage());
			}
		}
		loadedFile = file;
		values = loaded;
		return values;
	}

	private void readInto(JsonObject json, Map<String, ConfigValue> target) {
		for (ConfigDefinition def : definitions) {
			JsonElement e = json.get(def.key());
			if (e == null) {
				continue;
			}
			ConfigValue value = switch (def.type()) {
				case BOOLEAN -> ConfigValue.of(e.getAsBoolean());
				case STRING -> ConfigValue.of(e.getAsString());
				case ENUM -> ConfigValue.ofEnum(e.getAsString());
				case LIST -> {
					List<ConfigValue> list = new ArrayList<>();
					e.getAsJsonArray().forEach(x -> list.add(ConfigValue.of(x.getAsString())));
					yield new ConfigValue.ListValue(list);
				}
				default -> null;
			};
			if (value != null && def.validate(value).valid()) {
				target.put(def.key(), value);
			}
		}
	}

	private static void write(Path file, Map<String, ConfigValue> values) throws IOException {
		JsonObject json = new JsonObject();
		values.forEach((k, v) -> {
			switch (v) {
				case ConfigValue.BoolValue(boolean b) -> json.addProperty(k, b);
				case ConfigValue.ListValue(List<ConfigValue> list) -> {
					JsonArray array = new JsonArray();
					list.forEach(x -> array.add(x.display()));
					json.add(k, array);
				}
				default -> json.addProperty(k, v.display());
			}
		});
		Files.createDirectories(file.getParent());
		Path temp = Files.createTempFile(file.getParent(), ".join_message", ".tmp");
		try {
			Files.writeString(temp, GSON.toJson(json), StandardCharsets.UTF_8);
			try {
				Files.move(temp, file, StandardCopyOption.ATOMIC_MOVE, StandardCopyOption.REPLACE_EXISTING);
			} catch (AtomicMoveNotSupportedException e) {
				Files.move(temp, file, StandardCopyOption.REPLACE_EXISTING);
			}
		} finally {
			Files.deleteIfExists(temp);
		}
	}
}
