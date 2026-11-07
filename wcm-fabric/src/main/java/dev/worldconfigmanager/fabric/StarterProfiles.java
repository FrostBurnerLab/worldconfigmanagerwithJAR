package dev.worldconfigmanager.fabric;

import dev.worldconfigmanager.api.ConfigDefinition;
import dev.worldconfigmanager.api.ConfigEntry;
import dev.worldconfigmanager.api.ConfigKey;
import dev.worldconfigmanager.api.ConfigValue;
import dev.worldconfigmanager.core.OperationException;
import dev.worldconfigmanager.core.WorldSession;
import dev.worldconfigmanager.core.profile.Profile;
import dev.worldconfigmanager.fabric.provider.GameRulesProvider;
import dev.worldconfigmanager.fabric.provider.WorldSettingsProvider;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * Creates the example profiles once per profile library. A marker file prevents deleted starters from coming back.
 * Only settings that exist and validate in the running Minecraft version are included.
 */
public final class StarterProfiles {
	private static final Logger LOGGER = LoggerFactory.getLogger("WorldConfigManager");
	private static final String MARKER = "starter-profiles.done";

	private StarterProfiles() {
	}

	private record Template(String name, String description, List<String> tags, boolean allDefaults, Map<String, Object> values) {
	}

	private static final List<Template> TEMPLATES = List.of(
		new Template("Survival Default", "Vanilla defaults for every managed setting.", List.of("survival"), true,
			Map.of("difficulty", "normal")),
		new Template("Hardcore Challenge", "Harder survival: hard difficulty, no kept inventory, full sleep vote.",
			List.of("survival", "challenge"), false, Map.of(
				"difficulty", "hard", "keep_inventory", false, "immediate_respawn", false, "players_sleeping_percentage", 100,
				"natural_health_regeneration", true, "spawn_phantoms", true, "show_death_messages", true)),
		new Template("Creative Builder", "Frozen time and weather, no mobs or griefing, no fall damage.",
			List.of("creative", "building"), false, Map.of(
				"difficulty", "peaceful", "advance_time", false, "advance_weather", false, "spawn_mobs", false, "mob_griefing", false,
				"keep_inventory", true, "fall_damage", false, "tnt_explodes", false, "spawn_wandering_traders", false)),
		new Template("Performance Optimized", "Fewer background spawns and simulations for weaker servers.",
			List.of("performance"), false, Map.of(
				"max_entity_cramming", 12, "spawn_wandering_traders", false, "spawn_phantoms", false, "spawn_patrols", false,
				"spread_vines", false, "spectators_generate_chunks", false)),
		new Template("Multiplayer Default", "Friendly server defaults: PvP on, half the players can skip the night.",
			List.of("multiplayer"), false, Map.of(
				"pvp", true, "players_sleeping_percentage", 50, "show_death_messages", true, "show_advancement_messages", true,
				"log_admin_commands", true, "spectators_generate_chunks", false)));

	public static void ensure(WorldSession session, Path configDir) {
		Path marker = configDir.resolve(MARKER);
		if (Files.exists(marker)) {
			return;
		}
		int created = 0;
		for (Template template : TEMPLATES) {
			Map<ConfigKey, ConfigValue> entries = build(session, template);
			if (entries.isEmpty()) {
				continue;
			}
			try {
				Profile p = session.profiles().create(session.profiles().uniqueName(template.name(), null), template.description());
				session.profiles().updateTags(p.id(), template.tags());
				session.profiles().updateEntries(p.id(), entries, session.config().providerVersions(),
					session.identity().minecraftVersion());
				created++;
			} catch (OperationException e) {
				LOGGER.warn("Could not create starter profile {}: {}", template.name(), e.getMessage());
			}
		}
		try {
			Files.createDirectories(configDir);
			Files.writeString(marker, "Starter profiles were created. Delete this file to recreate them.\n");
		} catch (IOException e) {
			LOGGER.warn("Could not write starter profile marker: {}", e.getMessage());
		}
		LOGGER.info("Created {} starter profile(s)", created);
	}

	private static Map<ConfigKey, ConfigValue> build(WorldSession session, Template template) {
		Map<ConfigKey, ConfigValue> entries = new LinkedHashMap<>();
		if (template.allDefaults()) {
			for (ConfigEntry entry : session.config().entries()) {
				if (entry.providerId().equals(GameRulesProvider.ID)) {
					entries.put(entry.key(), entry.definition().defaultValue());
				}
			}
		}
		template.values().forEach((name, raw) -> {
			ConfigKey key = name.equals("difficulty")
				? new ConfigKey(WorldSettingsProvider.ID, WorldSettingsProvider.DIFFICULTY)
				: new ConfigKey(GameRulesProvider.ID, "minecraft:" + name);
			Optional<ConfigDefinition> def = session.config().definition(key);
			if (def.isEmpty()) {
				return;
			}
			ConfigValue value = switch (raw) {
				case Boolean b -> ConfigValue.of(b);
				case Integer i -> ConfigValue.of((long) i);
				case String s -> ConfigValue.ofEnum(s);
				default -> null;
			};
			if (value != null && def.get().validate(value).valid()) {
				entries.put(key, value);
			}
		});
		return entries;
	}
}
