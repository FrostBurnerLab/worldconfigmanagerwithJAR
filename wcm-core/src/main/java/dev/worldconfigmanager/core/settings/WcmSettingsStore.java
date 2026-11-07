package dev.worldconfigmanager.core.settings;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import dev.worldconfigmanager.core.json.Json;
import dev.worldconfigmanager.core.json.JsonFormatException;
import dev.worldconfigmanager.core.permission.PermissionAction;
import dev.worldconfigmanager.core.permission.PermissionPolicy;
import dev.worldconfigmanager.core.permission.PermissionRole;
import dev.worldconfigmanager.core.storage.AtomicFiles;
import dev.worldconfigmanager.core.storage.StorageException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.EnumMap;
import java.util.Locale;
import java.util.Map;

/** Reads and writes {@code settings.json}. An unreadable file is quarantined and defaults are used. */
public final class WcmSettingsStore {
	private static final Logger LOGGER = LoggerFactory.getLogger("WorldConfigManager");
	private static final int SCHEMA = 1;

	private final Path file;
	private WcmSettings current;

	public WcmSettingsStore(Path file) {
		this.file = file;
		this.current = load();
	}

	private WcmSettings load() {
		if (!Files.exists(file)) {
			WcmSettings defaults = WcmSettings.defaults();
			try {
				write(defaults);
			} catch (StorageException e) {
				LOGGER.warn("Could not write default settings: {}", e.getMessage());
			}
			return defaults;
		}
		try {
			return fromJson(Json.parseObject(AtomicFiles.readString(file)));
		} catch (JsonFormatException | StorageException e) {
			LOGGER.error("WCM settings are unreadable ({}); using defaults", e.getMessage());
			try {
				AtomicFiles.quarantine(file);
			} catch (StorageException q) {
				LOGGER.warn("Could not quarantine settings file: {}", q.getMessage());
			}
			return WcmSettings.defaults();
		}
	}

	public static WcmSettings fromJson(JsonObject json) throws JsonFormatException {
		WcmSettings d = WcmSettings.defaults();
		JsonObject backups = Json.optObject(json, "backups").orElseGet(JsonObject::new);
		JsonObject history = Json.optObject(json, "history").orElseGet(JsonObject::new);
		JsonObject network = Json.optObject(json, "network").orElseGet(JsonObject::new);
		Map<PermissionAction, PermissionRole> overrides = new EnumMap<>(PermissionAction.class);
		JsonObject perms = Json.optObject(json, "permissions").orElseGet(JsonObject::new);
		for (Map.Entry<String, JsonElement> e : perms.entrySet()) {
			try {
				PermissionAction action = PermissionAction.valueOf(e.getKey().toUpperCase(Locale.ROOT));
				PermissionRole role = PermissionRole.valueOf(Json.asString(e.getValue(), e.getKey()).toUpperCase(Locale.ROOT));
				overrides.put(action, role);
			} catch (IllegalArgumentException ex) {
				throw new JsonFormatException("Invalid permission rule '" + e.getKey() + "'", ex);
			}
		}
		return new WcmSettings(
			(int) Json.optInteger(backups, "maxCount", d.maxBackups()),
			(int) Json.optInteger(backups, "maxAgeDays", d.maxBackupAgeDays()),
			(int) Json.optInteger(history, "maxEntries", d.maxHistoryEntries()),
			(int) Json.optInteger(history, "maxAgeDays", d.maxHistoryAgeDays()),
			(int) Json.optInteger(network, "requestsPerSecond", d.requestsPerSecond()),
			(int) Json.optInteger(network, "requestBurst", d.requestBurst()),
			new PermissionPolicy(overrides),
			Json.bool(Json.optObject(json, "profiles").orElseGet(JsonObject::new), "autoApplyDefaultToNewWorlds", d.autoApplyDefault()));
	}

	public static JsonObject toJson(WcmSettings s) {
		JsonObject json = new JsonObject();
		json.addProperty("schemaVersion", SCHEMA);
		JsonObject backups = new JsonObject();
		backups.addProperty("maxCount", s.maxBackups());
		backups.addProperty("maxAgeDays", s.maxBackupAgeDays());
		json.add("backups", backups);
		JsonObject history = new JsonObject();
		history.addProperty("maxEntries", s.maxHistoryEntries());
		history.addProperty("maxAgeDays", s.maxHistoryAgeDays());
		json.add("history", history);
		JsonObject network = new JsonObject();
		network.addProperty("requestsPerSecond", s.requestsPerSecond());
		network.addProperty("requestBurst", s.requestBurst());
		json.add("network", network);
		JsonObject perms = new JsonObject();
		for (PermissionAction action : PermissionAction.values()) {
			perms.addProperty(action.name().toLowerCase(Locale.ROOT), s.permissions().requiredRole(action).name().toLowerCase(Locale.ROOT));
		}
		json.add("permissions", perms);
		JsonObject profiles = new JsonObject();
		profiles.addProperty("autoApplyDefaultToNewWorlds", s.autoApplyDefault());
		json.add("profiles", profiles);
		return json;
	}

	private void write(WcmSettings settings) throws StorageException {
		AtomicFiles.writeString(file, Json.pretty(toJson(settings)));
	}

	public synchronized WcmSettings get() {
		return current;
	}

	public synchronized void update(WcmSettings settings) throws StorageException {
		write(settings);
		current = settings;
	}
}
