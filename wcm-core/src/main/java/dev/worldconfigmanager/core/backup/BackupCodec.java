package dev.worldconfigmanager.core.backup;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import dev.worldconfigmanager.api.ConfigSnapshot;
import dev.worldconfigmanager.api.ConfigValue;
import dev.worldconfigmanager.core.WcmVersion;
import dev.worldconfigmanager.core.compat.SchemaTooNewException;
import dev.worldconfigmanager.core.json.Json;
import dev.worldconfigmanager.core.json.JsonFormatException;
import dev.worldconfigmanager.core.json.ValueJson;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;

/** JSON form of {@link Backup}. */
public final class BackupCodec {
	private BackupCodec() {
	}

	public static JsonObject toJson(Backup backup) {
		JsonObject json = new JsonObject();
		json.addProperty("schemaVersion", WcmVersion.BACKUP_SCHEMA);
		json.addProperty("id", backup.id().toString());
		json.addProperty("createdAt", backup.createdAt().toString());
		json.addProperty("worldId", backup.worldId().toString());
		json.addProperty("reason", backup.reason().name());
		json.addProperty("label", backup.label());
		if (backup.profileId() != null) {
			json.addProperty("profileId", backup.profileId().toString());
		}
		JsonObject providers = new JsonObject();
		backup.snapshots().forEach((id, snapshot) -> {
			JsonObject s = new JsonObject();
			s.addProperty("version", snapshot.providerVersion());
			s.addProperty("capturedAt", snapshot.capturedAt().toString());
			JsonObject values = new JsonObject();
			snapshot.values().forEach((k, v) -> values.add(k, ValueJson.write(v)));
			s.add("values", values);
			providers.add(id, s);
		});
		json.add("providers", providers);
		return json;
	}

	public static Backup fromJson(JsonObject json) throws JsonFormatException {
		long schema = Json.integer(json, "schemaVersion");
		if (schema > WcmVersion.BACKUP_SCHEMA) {
			throw new SchemaTooNewException("Backup", (int) schema, WcmVersion.BACKUP_SCHEMA);
		}
		BackupReason reason;
		try {
			reason = BackupReason.valueOf(Json.string(json, "reason"));
		} catch (IllegalArgumentException e) {
			throw new JsonFormatException("Unknown backup reason", e);
		}
		String profile = Json.optString(json, "profileId", null);
		Map<String, ConfigSnapshot> snapshots = new LinkedHashMap<>();
		for (Map.Entry<String, JsonElement> p : Json.object(json, "providers").entrySet()) {
			if (!p.getValue().isJsonObject()) {
				throw new JsonFormatException("Invalid provider snapshot " + p.getKey());
			}
			JsonObject s = p.getValue().getAsJsonObject();
			Map<String, ConfigValue> values = new LinkedHashMap<>();
			for (Map.Entry<String, JsonElement> v : Json.object(s, "values").entrySet()) {
				values.put(v.getKey(), ValueJson.read(v.getValue()));
			}
			snapshots.put(p.getKey(), new ConfigSnapshot(p.getKey(), Json.string(s, "version"), Json.instant(s, "capturedAt"), values));
		}
		try {
			return new Backup(Json.uuid(json, "id"), Json.instant(json, "createdAt"), Json.uuid(json, "worldId"), reason,
				Json.optString(json, "label", ""), profile == null ? null : UUID.fromString(profile), snapshots);
		} catch (IllegalArgumentException e) {
			throw new JsonFormatException("Invalid backup: " + e.getMessage(), e);
		}
	}
}
