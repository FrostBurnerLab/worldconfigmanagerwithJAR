package dev.worldconfigmanager.core.history;

import com.google.gson.JsonObject;
import dev.worldconfigmanager.api.ConfigCategory;
import dev.worldconfigmanager.core.json.Json;
import dev.worldconfigmanager.core.json.JsonFormatException;
import dev.worldconfigmanager.core.json.ValueJson;

import java.util.UUID;

/** JSON-lines form of {@link HistoryEntry}. */
public final class HistoryCodec {
	private HistoryCodec() {
	}

	public static JsonObject toJson(HistoryEntry e) {
		JsonObject json = new JsonObject();
		json.addProperty("id", e.id().toString());
		json.addProperty("ts", e.timestamp().toString());
		json.addProperty("world", e.worldId().toString());
		if (e.profileId() != null) {
			json.addProperty("profile", e.profileId().toString());
		}
		json.addProperty("provider", e.providerId());
		json.addProperty("key", e.key());
		json.addProperty("category", e.category().name());
		if (e.previousValue() != null) {
			json.add("old", ValueJson.write(e.previousValue()));
		}
		if (e.newValue() != null) {
			json.add("new", ValueJson.write(e.newValue()));
		}
		if (e.masked()) {
			json.addProperty("masked", true);
		}
		json.addProperty("op", e.operation().name());
		json.addProperty("result", e.result().name());
		if (!e.actor().isEmpty()) {
			json.addProperty("actor", e.actor());
		}
		if (e.backupId() != null) {
			json.addProperty("backup", e.backupId().toString());
		}
		if (!e.message().isEmpty()) {
			json.addProperty("msg", e.message());
		}
		return json;
	}

	public static HistoryEntry fromJson(JsonObject json) throws JsonFormatException {
		try {
			String profile = Json.optString(json, "profile", null);
			String backup = Json.optString(json, "backup", null);
			return new HistoryEntry(Json.uuid(json, "id"), Json.instant(json, "ts"), Json.uuid(json, "world"),
				profile == null ? null : UUID.fromString(profile), Json.string(json, "provider"), Json.string(json, "key"),
				ConfigCategory.valueOf(Json.optString(json, "category", ConfigCategory.OTHER_MODS.name())),
				json.has("old") ? ValueJson.read(json.get("old")) : null, json.has("new") ? ValueJson.read(json.get("new")) : null,
				Json.bool(json, "masked", false), HistoryOperation.valueOf(Json.string(json, "op")),
				HistoryResult.valueOf(Json.string(json, "result")), Json.optString(json, "actor", ""),
				backup == null ? null : UUID.fromString(backup), Json.optString(json, "msg", ""));
		} catch (IllegalArgumentException e) {
			throw new JsonFormatException("Invalid history entry: " + e.getMessage(), e);
		}
	}
}
