package dev.worldconfigmanager.fabric.client.backend;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;

import java.util.HashSet;
import java.util.Set;

/**
 * Result of the handshake: identity, role and the actions the server says the player may perform. The permission set
 * is only used to decide which controls are enabled; the server checks every request again.
 */
public record BackendInfo(boolean compatible, String worldName, String worldId, String kind, String role, Set<String> permissions,
	String serverVersion, int serverProtocol) {

	public static BackendInfo fromHello(JsonObject body) {
		Set<String> perms = new HashSet<>();
		if (body.has("permissions") && body.get("permissions").isJsonArray()) {
			for (JsonElement e : body.getAsJsonArray("permissions")) {
				if (e.isJsonPrimitive()) {
					perms.add(e.getAsString());
				}
			}
		}
		JsonObject world = body.has("world") && body.get("world").isJsonObject() ? body.getAsJsonObject("world") : new JsonObject();
		return new BackendInfo(J.bool(body, "compatible", false), J.str(world, "name", ""), J.str(world, "id", ""),
			J.str(world, "kind", ""), J.str(body, "role", "PLAYER"), Set.copyOf(perms), J.str(body, "wcmVersion", "?"),
			J.integer(body, "serverProtocol", 0));
	}

	public boolean can(String action) {
		return compatible && permissions.contains(action);
	}
}
