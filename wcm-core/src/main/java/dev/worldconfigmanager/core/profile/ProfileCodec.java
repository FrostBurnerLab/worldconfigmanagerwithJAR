package dev.worldconfigmanager.core.profile;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonPrimitive;
import dev.worldconfigmanager.api.ConfigKey;
import dev.worldconfigmanager.api.ConfigValue;
import dev.worldconfigmanager.core.WcmVersion;
import dev.worldconfigmanager.core.compat.SchemaMigrator;
import dev.worldconfigmanager.core.json.Json;
import dev.worldconfigmanager.core.json.JsonFormatException;
import dev.worldconfigmanager.core.json.ValueJson;

import java.math.BigDecimal;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.Map;
import java.util.Set;

/**
 * Serialization of {@link Profile} to and from JSON, including migration of older schema versions.
 *
 * <p>Schema history:
 * <ul>
 *     <li>v1 (pre-release draft): {@code schema}, {@code created}, {@code modified} and untyped {@code settings}.</li>
 *     <li>v2 (0.1.0): typed {@code entries}, provider versions, tags, Minecraft version.</li>
 * </ul>
 */
public final class ProfileCodec {
	private static final SchemaMigrator MIGRATOR = new SchemaMigrator("Profile", WcmVersion.PROFILE_SCHEMA, "schemaVersion")
		.step(1, ProfileCodec::migrateV1);

	private ProfileCodec() {
	}

	public static JsonObject toJson(Profile profile) {
		JsonObject json = new JsonObject();
		json.addProperty("schemaVersion", WcmVersion.PROFILE_SCHEMA);
		json.addProperty("id", profile.id().toString());
		json.addProperty("name", profile.name());
		json.addProperty("description", profile.description());
		json.addProperty("createdAt", profile.createdAt().toString());
		json.addProperty("modifiedAt", profile.modifiedAt().toString());
		json.addProperty("wcmVersion", profile.wcmVersion());
		json.addProperty("minecraftVersion", profile.minecraftVersion());
		JsonArray tags = new JsonArray();
		profile.tags().forEach(tags::add);
		json.add("tags", tags);
		JsonObject providers = new JsonObject();
		profile.providerVersions().forEach(providers::addProperty);
		json.add("providers", providers);
		JsonArray entries = new JsonArray();
		profile.entries().forEach((key, value) -> {
			JsonObject e = new JsonObject();
			e.addProperty("provider", key.providerId());
			e.addProperty("key", key.key());
			e.add("value", ValueJson.write(value));
			entries.add(e);
		});
		json.add("entries", entries);
		return json;
	}

	/** Parses and validates a profile, migrating older schemas. */
	public static Profile fromJson(JsonObject source) throws JsonFormatException {
		JsonObject json = source.deepCopy();
		int version = MIGRATOR.versionOf(json, "schema");
		json = MIGRATOR.migrate(json, version);
		try {
			Set<String> tags = new LinkedHashSet<>();
			for (JsonElement tag : Json.optArray(json, "tags")) {
				tags.add(Json.asString(tag, "tag"));
			}
			Map<String, String> providers = new LinkedHashMap<>();
			JsonObject providerJson = Json.optObject(json, "providers").orElseGet(JsonObject::new);
			for (Map.Entry<String, JsonElement> e : providerJson.entrySet()) {
				providers.put(e.getKey(), Json.asString(e.getValue(), "provider version"));
			}
			Map<ConfigKey, ConfigValue> entries = new LinkedHashMap<>();
			for (JsonElement element : Json.array(json, "entries")) {
				if (!element.isJsonObject()) {
					throw new JsonFormatException("Entry must be an object");
				}
				JsonObject e = element.getAsJsonObject();
				ConfigKey key;
				try {
					key = new ConfigKey(Json.string(e, "provider"), Json.string(e, "key"));
				} catch (IllegalArgumentException ex) {
					throw new JsonFormatException("Invalid entry key: " + ex.getMessage(), ex);
				}
				if (entries.put(key, ValueJson.read(e.get("value"))) != null) {
					throw new JsonFormatException("Duplicate entry " + key);
				}
			}
			return new Profile(Json.uuid(json, "id"), Json.string(json, "name"), Json.optString(json, "description", ""),
				Json.instant(json, "createdAt"), Json.instant(json, "modifiedAt"), WcmVersion.PROFILE_SCHEMA,
				Json.optString(json, "wcmVersion", WcmVersion.MOD_VERSION), Json.optString(json, "minecraftVersion", ""), tags,
				providers, entries);
		} catch (IllegalArgumentException e) {
			throw new JsonFormatException("Invalid profile: " + e.getMessage(), e);
		}
	}

	private static JsonObject migrateV1(JsonObject v1) throws JsonFormatException {
		JsonObject v2 = new JsonObject();
		v2.addProperty("id", Json.string(v1, "id"));
		v2.addProperty("name", Json.string(v1, "name"));
		v2.addProperty("description", Json.optString(v1, "description", ""));
		v2.addProperty("createdAt", Json.string(v1, "created"));
		v2.addProperty("modifiedAt", Json.optString(v1, "modified", Json.string(v1, "created")));
		v2.add("tags", new JsonArray());
		v2.add("providers", new JsonObject());
		JsonArray entries = new JsonArray();
		for (JsonElement element : Json.optArray(v1, "settings")) {
			if (!element.isJsonObject()) {
				throw new JsonFormatException("v1 setting must be an object");
			}
			JsonObject s = element.getAsJsonObject();
			JsonObject entry = new JsonObject();
			entry.addProperty("provider", Json.string(s, "provider"));
			entry.addProperty("key", Json.string(s, "key"));
			entry.add("value", ValueJson.write(inferV1Value(s.get("value"))));
			entries.add(entry);
		}
		v2.add("entries", entries);
		return v2;
	}

	/** v1 stored untyped primitives; infer the closest typed value. */
	private static ConfigValue inferV1Value(JsonElement raw) throws JsonFormatException {
		if (raw == null || !raw.isJsonPrimitive()) {
			throw new JsonFormatException("v1 setting value must be a primitive");
		}
		JsonPrimitive p = raw.getAsJsonPrimitive();
		if (p.isBoolean()) {
			return ConfigValue.of(p.getAsBoolean());
		}
		if (p.isNumber()) {
			BigDecimal number = p.getAsBigDecimal();
			try {
				return ConfigValue.of(number.longValueExact());
			} catch (ArithmeticException e) {
				return ConfigValue.of(number.doubleValue());
			}
		}
		return ConfigValue.of(p.getAsString());
	}
}
