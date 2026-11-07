package dev.worldconfigmanager.fabric.client.backend;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;

import java.util.ArrayList;
import java.util.List;

/**
 * A protocol response as seen by the UI.
 *
 * @param ok        whether the request succeeded
 * @param body      response body (empty object on error)
 * @param errorKey  translation key of the error (when not ok)
 * @param errorArgs arguments of the error
 */
public record Response(boolean ok, JsonObject body, String errorKey, List<String> errorArgs) {
	public static Response error(String key, String... args) {
		return new Response(false, new JsonObject(), key, List.of(args));
	}

	/** Parses a raw response message defensively; malformed server data becomes an error response. */
	public static Response parse(JsonObject message) {
		JsonElement ok = message.get("ok");
		if (ok != null && ok.isJsonPrimitive() && ok.getAsBoolean()) {
			JsonElement body = message.get("body");
			return new Response(true, body != null && body.isJsonObject() ? body.getAsJsonObject() : new JsonObject(), null, List.of());
		}
		JsonElement error = message.get("error");
		if (error != null && error.isJsonObject()) {
			JsonObject e = error.getAsJsonObject();
			String key = e.has("key") && e.get("key").isJsonPrimitive() ? e.get("key").getAsString() : "wcm.error.internal";
			List<String> args = new ArrayList<>();
			if (e.has("args") && e.get("args").isJsonArray()) {
				JsonArray a = e.getAsJsonArray("args");
				for (JsonElement x : a) {
					args.add(x.isJsonPrimitive() ? x.getAsString() : "");
				}
			}
			return new Response(false, new JsonObject(), key, args);
		}
		return error("wcm.error.malformed_response");
	}
}
