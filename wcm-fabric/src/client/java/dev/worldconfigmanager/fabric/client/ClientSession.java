package dev.worldconfigmanager.fabric.client;

import com.google.gson.JsonObject;
import dev.worldconfigmanager.core.json.Json;
import dev.worldconfigmanager.core.json.JsonFormatException;
import dev.worldconfigmanager.core.protocol.WcmProtocol;
import dev.worldconfigmanager.core.storage.StorageException;
import dev.worldconfigmanager.core.world.WorldDescriptor;
import dev.worldconfigmanager.core.world.WorldKind;
import dev.worldconfigmanager.fabric.WcmFabricMod;
import dev.worldconfigmanager.fabric.client.backend.Backend;
import dev.worldconfigmanager.fabric.client.backend.BackendInfo;
import dev.worldconfigmanager.fabric.client.backend.J;
import dev.worldconfigmanager.fabric.client.backend.LocalBackend;
import dev.worldconfigmanager.fabric.client.backend.Response;
import dev.worldconfigmanager.fabric.client.backend.ServerBackend;
import dev.worldconfigmanager.fabric.client.screen.WcmScreen;
import dev.worldconfigmanager.fabric.net.WcmPayloads;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.SharedConstants;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ServerData;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.nio.charset.StandardCharsets;
import java.util.Optional;
import java.util.UUID;

/**
 * Client connection state: performs the handshake with WCM servers, detects servers without WCM, and opens the local
 * client-profile store for the current world or server. Render-thread only.
 */
public final class ClientSession {
	private static final Logger LOGGER = LoggerFactory.getLogger("WorldConfigManager");
	private static final long HELLO_TIMEOUT_NANOS = 10_000_000_000L;

	/** Connection status shown in the UI. */
	public enum Status {
		DISCONNECTED,
		HANDSHAKING,
		READY,
		INCOMPATIBLE,
		NO_WCM
	}

	private static Status status = Status.DISCONNECTED;
	private static ServerBackend server;
	private static LocalBackend local;
	private static long helloSentAt;
	private static String localKey;

	private ClientSession() {
	}

	public static Status status() {
		return status;
	}

	public static Optional<Backend> serverBackend() {
		return status == Status.READY || status == Status.INCOMPATIBLE ? Optional.ofNullable(server) : Optional.empty();
	}

	public static Optional<Backend> localBackend() {
		return Optional.ofNullable(local);
	}

	public static void onJoin(Minecraft mc) {
		reset();
		if (ClientPlayNetworking.canSend(WcmPayloads.Request.TYPE)) {
			status = Status.HANDSHAKING;
			server = new ServerBackend();
			helloSentAt = System.nanoTime();
			server.request("hello", J.of("protocol", WcmProtocol.VERSION, "minProtocol", WcmProtocol.MIN_VERSION,
				"wcmVersion", dev.worldconfigmanager.core.WcmVersion.MOD_VERSION), ClientSession::onHello);
		} else {
			status = Status.NO_WCM;
			openLocal(mc, null, null);
		}
	}

	private static void onHello(Response response) {
		Minecraft mc = Minecraft.getInstance();
		if (!response.ok()) {
			LOGGER.warn("WCM handshake failed: {}", response.errorKey());
			status = Status.NO_WCM;
			openLocal(mc, null, null);
			return;
		}
		BackendInfo info = BackendInfo.fromHello(response.body());
		server.setInfo(info);
		status = info.compatible() ? Status.READY : Status.INCOMPATIBLE;
		LOGGER.info("Connected to WorldConfig Manager {} (protocol {}, role {}, {})", info.serverVersion(), info.serverProtocol(), info.role(),
			status);
		openLocal(mc, info.worldId().isEmpty() ? null : info.worldId(), info.worldName());
	}

	private static void openLocal(Minecraft mc, String worldId, String worldName) {
		ServerData data = mc.getCurrentServer();
		String name = worldName != null && !worldName.isEmpty() ? worldName : data != null ? data.name : "World";
		String key;
		WorldKind kind;
		if (worldId != null) {
			key = UUID.fromString(worldId).toString();
			kind = mc.hasSingleplayerServer() ? WorldKind.SINGLEPLAYER : WorldKind.REMOTE_WCM;
		} else {
			String address = data != null ? data.ip : "unknown";
			key = UUID.nameUUIDFromBytes(("server:" + address).getBytes(StandardCharsets.UTF_8)).toString();
			kind = WorldKind.REMOTE_VANILLA;
		}
		if (key.equals(localKey) && local != null) {
			return;
		}
		try {
			local = LocalBackend.open(FabricLoader.getInstance().getConfigDir().resolve("worldconfigmanager"), key,
				new WorldDescriptor(name, kind, "", false, SharedConstants.getCurrentVersion().name()), WcmFabricMod.registry());
			localKey = key;
		} catch (StorageException e) {
			LOGGER.error("Could not open local client profiles", e);
			local = null;
		}
	}

	/** Dispatches a server message. */
	public static void onMessage(String raw) {
		JsonObject message;
		try {
			message = Json.parseObject(raw);
		} catch (JsonFormatException e) {
			LOGGER.warn("Ignoring malformed WCM message from server");
			return;
		}
		String type = J.str(message, "type", "");
		long id = message.has("id") && message.get("id").isJsonPrimitive() ? message.get("id").getAsLong() : 0;
		if (id == 0) {
			Minecraft mc = Minecraft.getInstance();
			switch (type) {
				case "event.open" -> openScreen(mc);
				case "event.changed" -> {
					if (mc.gui.screen() instanceof WcmScreen screen) {
						screen.onRemoteChange();
					}
				}
				default -> LOGGER.debug("Ignoring WCM event {}", type);
			}
			return;
		}
		if (server != null) {
			server.complete(id, Response.parse(message));
		}
	}

	public static void tick(Minecraft mc) {
		if (server != null) {
			server.tick();
			if (status == Status.HANDSHAKING && System.nanoTime() - helloSentAt > HELLO_TIMEOUT_NANOS) {
				status = Status.NO_WCM;
				openLocal(mc, null, null);
			}
		}
	}

	public static void openScreen(Minecraft mc) {
		if (mc.player == null) {
			return;
		}
		mc.gui.setScreen(new WcmScreen(mc.gui.screen()));
	}

	public static void reset() {
		if (server != null) {
			server.failAll();
		}
		server = null;
		local = null;
		localKey = null;
		status = Status.DISCONNECTED;
	}
}
