package dev.worldconfigmanager.fabric.client.backend;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;

import java.util.ArrayList;
import java.util.List;

/** Lenient accessors for server-provided JSON: missing or mistyped fields yield the fallback instead of throwing. */
public final class J {
	private J() {
	}

	public static String str(JsonObject o, String key, String fallback) {
		JsonElement e = o == null ? null : o.get(key);
		return e != null && e.isJsonPrimitive() ? e.getAsString() : fallback;
	}

	public static int integer(JsonObject o, String key, int fallback) {
		JsonElement e = o == null ? null : o.get(key);
		try {
			return e != null && e.isJsonPrimitive() ? e.getAsInt() : fallback;
		} catch (NumberFormatException ex) {
			return fallback;
		}
	}

	public static boolean bool(JsonObject o, String key, boolean fallback) {
		JsonElement e = o == null ? null : o.get(key);
		return e != null && e.isJsonPrimitive() && e.getAsJsonPrimitive().isBoolean() ? e.getAsBoolean() : fallback;
	}

	public static JsonObject obj(JsonObject o, String key) {
		JsonElement e = o == null ? null : o.get(key);
		return e != null && e.isJsonObject() ? e.getAsJsonObject() : new JsonObject();
	}

	public static List<JsonObject> objects(JsonObject o, String key) {
		List<JsonObject> list = new ArrayList<>();
		JsonElement e = o == null ? null : o.get(key);
		if (e != null && e.isJsonArray()) {
			for (JsonElement x : e.getAsJsonArray()) {
				if (x.isJsonObject()) {
					list.add(x.getAsJsonObject());
				}
			}
		}
		return list;
	}

	public static List<String> strings(JsonObject o, String key) {
		List<String> list = new ArrayList<>();
		JsonElement e = o == null ? null : o.get(key);
		if (e != null && e.isJsonArray()) {
			for (JsonElement x : e.getAsJsonArray()) {
				if (x.isJsonPrimitive()) {
					list.add(x.getAsString());
				}
			}
		}
		return list;
	}

	public static JsonArray array(List<JsonObject> items) {
		JsonArray a = new JsonArray();
		items.forEach(a::add);
		return a;
	}

	public static JsonObject of(Object... kv) {
		JsonObject o = new JsonObject();
		for (int i = 0; i + 1 < kv.length; i += 2) {
			String k = (String) kv[i];
			Object v = kv[i + 1];
			switch (v) {
				case null -> {
				}
				case String s -> o.addProperty(k, s);
				case Number n -> o.addProperty(k, n);
				case Boolean b -> o.addProperty(k, b);
				case JsonElement el -> o.add(k, el);
				default -> o.addProperty(k, v.toString());
			}
		}
		return o;
	}
}
