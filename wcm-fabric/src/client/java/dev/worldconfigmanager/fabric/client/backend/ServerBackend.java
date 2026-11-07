package dev.worldconfigmanager.fabric.client.backend;

import com.google.gson.JsonObject;
import dev.worldconfigmanager.core.json.Json;
import dev.worldconfigmanager.core.protocol.WcmProtocol;
import dev.worldconfigmanager.fabric.net.WcmPayloads;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;

import java.nio.charset.StandardCharsets;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.function.Consumer;

/** Sends requests to a WCM-enabled server and matches responses by id. Requests time out after 15 seconds. */
public final class ServerBackend implements Backend {
	private static final long TIMEOUT_NANOS = 15_000_000_000L;

	private record Pending(Consumer<Response> callback, long sentAt) {
	}

	private final Map<Long, Pending> pending = new LinkedHashMap<>();
	private long nextId = 1;
	private BackendInfo info;

	public void setInfo(BackendInfo info) {
		this.info = info;
	}

	@Override
	public BackendInfo info() {
		return info;
	}

	@Override
	public boolean isServer() {
		return true;
	}

	@Override
	public void request(String type, JsonObject body, Consumer<Response> callback) {
		long id = nextId++;
		JsonObject msg = new JsonObject();
		msg.addProperty("id", id);
		msg.addProperty("type", type);
		msg.add("body", body);
		String json = Json.compact(msg);
		if (json.getBytes(StandardCharsets.UTF_8).length > WcmProtocol.MAX_C2S_BYTES) {
			callback.accept(Response.error("wcm.error.payload_too_large"));
			return;
		}
		if (!ClientPlayNetworking.canSend(WcmPayloads.Request.TYPE)) {
			callback.accept(Response.error("wcm.error.not_connected"));
			return;
		}
		pending.put(id, new Pending(callback, System.nanoTime()));
		ClientPlayNetworking.send(new WcmPayloads.Request(json));
	}

	/** @return {@code true} if the message answered a pending request */
	public boolean complete(long id, Response response) {
		Pending p = pending.remove(id);
		if (p == null) {
			return false;
		}
		p.callback().accept(response);
		return true;
	}

	/** Fails requests that have not been answered in time. Called every client tick. */
	public void tick() {
		long now = System.nanoTime();
		Iterator<Map.Entry<Long, Pending>> it = pending.entrySet().iterator();
		while (it.hasNext()) {
			Pending p = it.next().getValue();
			if (now - p.sentAt() > TIMEOUT_NANOS) {
				it.remove();
				p.callback().accept(Response.error("wcm.error.timeout"));
			}
		}
	}

	public void failAll() {
		Map<Long, Pending> copy = new LinkedHashMap<>(pending);
		pending.clear();
		copy.values().forEach(p -> p.callback().accept(Response.error("wcm.error.not_connected")));
	}
}
