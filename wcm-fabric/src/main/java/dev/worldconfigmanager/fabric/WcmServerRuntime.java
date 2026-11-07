package dev.worldconfigmanager.fabric;

import com.google.gson.JsonObject;
import dev.worldconfigmanager.core.WorldSession;
import dev.worldconfigmanager.core.json.Json;
import dev.worldconfigmanager.core.json.JsonFormatException;
import dev.worldconfigmanager.core.protocol.ConnectionState;
import dev.worldconfigmanager.core.protocol.PlayerAuth;
import dev.worldconfigmanager.core.protocol.ServerProtocolHandler;
import dev.worldconfigmanager.core.registry.ProviderRegistry;
import dev.worldconfigmanager.core.storage.StorageException;
import dev.worldconfigmanager.core.world.WorldDescriptor;
import dev.worldconfigmanager.core.world.WorldKind;
import dev.worldconfigmanager.fabric.net.WcmPayloads;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.storage.LevelResource;
import net.minecraft.world.level.storage.WorldData;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.nio.file.Path;
import java.time.Clock;
import java.util.HashMap;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

/**
 * Server-side runtime for one running server: owns the {@link WorldSession}, the protocol handler and per-player
 * connection state. Created when the server has started and discarded when it stops. All methods run on the server
 * thread.
 */
public final class WcmServerRuntime {
	private static final Logger LOGGER = LoggerFactory.getLogger("WorldConfigManager");
	private static final Set<String> BROADCAST_ON = Set.of("settings.apply", "profiles.apply", "backups.restore", "profiles.create",
		"profiles.rename", "profiles.delete", "profiles.duplicate", "profiles.associate", "profiles.setDefault", "profiles.capture",
		"profiles.editEntries", "profiles.describe", "backups.create", "backups.delete", "import.commit", "wcm.settings.set");

	private final MinecraftServer server;
	private final WorldSession session;
	private final ServerProtocolHandler handler;
	private final Map<UUID, ConnectionState> connections = new HashMap<>();

	private WcmServerRuntime(MinecraftServer server, WorldSession session) {
		this.server = server;
		this.session = session;
		this.handler = new ServerProtocolHandler(() -> Optional.ofNullable(this.session), FabricProviderContext.SERVER_SCOPES);
	}

	/** Opens the WCM session for the server's world. Returns a runtime without session if storage cannot be opened. */
	public static WcmServerRuntime start(MinecraftServer server, ProviderRegistry registry) {
		WorldData data = server.getWorldData();
		WorldDescriptor descriptor = new WorldDescriptor(data.getLevelName(),
			server.isDedicatedServer() ? WorldKind.DEDICATED : WorldKind.SINGLEPLAYER, data.getGameType().getName(),
			data.isHardcore(), server.getServerVersion());
		Path configDir = FabricLoader.getInstance().getConfigDir().resolve("worldconfigmanager");
		Path worldDir = server.getWorldPath(LevelResource.ROOT).resolve("worldconfigmanager");
		WorldSession session = null;
		try {
			session = new WorldSession(configDir, worldDir, descriptor, registry, id -> new FabricProviderContext(server, id),
				Clock.systemUTC());
			LOGGER.info("WorldConfig Manager ready for world '{}' ({}), {} provider(s), {} profile(s)", descriptor.displayName(),
				session.identity().worldId(), session.config().availableProviders().size(), session.profiles().list().size());
			StarterProfiles.ensure(session, configDir);
			session.onWorldLoaded();
		} catch (StorageException e) {
			LOGGER.error("WorldConfig Manager could not open its storage; configuration management is disabled for this world", e);
		}
		return new WcmServerRuntime(server, session);
	}

	public Optional<WorldSession> session() {
		return Optional.ofNullable(session);
	}

	public MinecraftServer server() {
		return server;
	}

	/** Handles a message from a player. Always executed on the server thread. */
	public void onMessage(ServerPlayer player, String json) {
		if (!server.isSameThread()) {
			server.execute(() -> onMessage(player, json));
			return;
		}
		PlayerAuth auth = new PlayerAuth(player.getUUID(), player.getName().getString(), PermissionResolver.role(player.permissions()));
		ConnectionState state = connections.computeIfAbsent(player.getUUID(), id -> handler.newConnection(System::nanoTime));
		String response = handler.handle(auth, state, json);
		if (ServerPlayNetworking.canSend(player, WcmPayloads.Response.TYPE)) {
			ServerPlayNetworking.send(player, new WcmPayloads.Response(response));
		}
		broadcastChangeIfNeeded(player, response);
	}

	private void broadcastChangeIfNeeded(ServerPlayer origin, String response) {
		try {
			JsonObject json = Json.parseObject(response);
			if (!Json.bool(json, "ok", false) || !BROADCAST_ON.contains(Json.optString(json, "type", ""))) {
				return;
			}
		} catch (JsonFormatException e) {
			return;
		}
		broadcastChanged(origin == null ? null : origin.getUUID());
	}

	/** Tells connected WCM clients (except {@code exclude}) that data changed, so open screens refresh. */
	public void broadcastChanged(UUID exclude) {
		String event = "{\"id\":0,\"type\":\"event.changed\",\"ok\":true,\"body\":{}}";
		for (ServerPlayer p : server.getPlayerList().getPlayers()) {
			if (!p.getUUID().equals(exclude) && connections.containsKey(p.getUUID()) && ServerPlayNetworking.canSend(p, WcmPayloads.Response.TYPE)) {
				ServerPlayNetworking.send(p, new WcmPayloads.Response(event));
			}
		}
	}

	/** Asks a player's client to open the WCM screen. */
	public boolean requestOpenScreen(ServerPlayer player) {
		if (!ServerPlayNetworking.canSend(player, WcmPayloads.Response.TYPE)) {
			return false;
		}
		ServerPlayNetworking.send(player, new WcmPayloads.Response("{\"id\":0,\"type\":\"event.open\",\"ok\":true,\"body\":{}}"));
		return true;
	}

	public void onDisconnect(UUID player) {
		connections.remove(player);
	}
}
