package dev.worldconfigmanager.fabric.client.backend;

import com.google.gson.JsonObject;

import java.util.function.Consumer;

/** Where the UI sends protocol requests: the connected server or the local client-profile store. */
public interface Backend {
	/** Sends a request; the callback is always invoked on the render thread. */
	void request(String type, JsonObject body, Consumer<Response> callback);

	BackendInfo info();

	/** @return {@code true} for the server backend, {@code false} for local client profiles */
	boolean isServer();
}
