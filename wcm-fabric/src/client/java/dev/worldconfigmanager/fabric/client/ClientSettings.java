package dev.worldconfigmanager.fabric.client;

import com.google.gson.JsonObject;
import dev.worldconfigmanager.core.json.Json;
import dev.worldconfigmanager.core.json.JsonFormatException;
import dev.worldconfigmanager.core.storage.AtomicFiles;
import dev.worldconfigmanager.core.storage.StorageException;
import net.fabricmc.loader.api.FabricLoader;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.nio.file.Files;
import java.nio.file.Path;

/**
 * Client-only WCM preferences stored in {@code config/worldconfigmanager/client.json}.
 *
 * @param pauseMenuButton show the WCM button in the pause menu
 * @param confirmApply    ask for confirmation before applying profiles or restoring backups
 */
public record ClientSettings(boolean pauseMenuButton, boolean confirmApply) {
	private static final Logger LOGGER = LoggerFactory.getLogger("WorldConfigManager");
	private static ClientSettings current;

	private static Path file() {
		return FabricLoader.getInstance().getConfigDir().resolve("worldconfigmanager").resolve("client.json");
	}

	public static ClientSettings get() {
		if (current == null) {
			current = load();
		}
		return current;
	}

	private static ClientSettings load() {
		Path file = file();
		if (Files.exists(file)) {
			try {
				JsonObject json = Json.parseObject(AtomicFiles.readString(file));
				return new ClientSettings(Json.bool(json, "pauseMenuButton", true), Json.bool(json, "confirmApply", true));
			} catch (JsonFormatException | StorageException e) {
				LOGGER.warn("Ignoring unreadable client settings: {}", e.getMessage());
			}
		}
		return new ClientSettings(true, true);
	}

	public static void save(ClientSettings settings) throws StorageException {
		JsonObject json = new JsonObject();
		json.addProperty("schemaVersion", 1);
		json.addProperty("pauseMenuButton", settings.pauseMenuButton());
		json.addProperty("confirmApply", settings.confirmApply());
		AtomicFiles.writeString(file(), Json.pretty(json));
		current = settings;
	}
}
