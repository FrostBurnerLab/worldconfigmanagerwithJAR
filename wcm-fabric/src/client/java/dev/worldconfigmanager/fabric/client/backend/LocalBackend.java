package dev.worldconfigmanager.fabric.client.backend;

import com.google.gson.JsonObject;
import dev.worldconfigmanager.api.ConfigScope;
import dev.worldconfigmanager.api.ProviderContext;
import dev.worldconfigmanager.core.WorldSession;
import dev.worldconfigmanager.core.json.Json;
import dev.worldconfigmanager.core.json.JsonFormatException;
import dev.worldconfigmanager.core.permission.PermissionRole;
import dev.worldconfigmanager.core.protocol.ConnectionState;
import dev.worldconfigmanager.core.protocol.PlayerAuth;
import dev.worldconfigmanager.core.protocol.ServerProtocolHandler;
import dev.worldconfigmanager.core.protocol.WcmProtocol;
import dev.worldconfigmanager.core.registry.ProviderRegistry;
import dev.worldconfigmanager.core.storage.StorageException;
import dev.worldconfigmanager.core.world.WorldDescriptor;
import net.minecraft.client.Minecraft;

import java.nio.file.Path;
import java.time.Clock;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.function.Consumer;

/**
 * Local client profiles: manages CLIENT-scoped settings (e.g. video options) per world or server entirely on this
 * computer. It reuses the same protocol handler as the server, with the local user as owner of their own settings.
 * Nothing from this backend is ever sent to a server.
 */
public final class LocalBackend implements Backend {
	private final ServerProtocolHandler handler;
	private final ConnectionState state;
	private final PlayerAuth auth;
	private final BackendInfo info;

	private LocalBackend(WorldSession session) {
		this.handler = new ServerProtocolHandler(() -> Optional.of(session), Set.of(ConfigScope.CLIENT));
		this.state = new ConnectionState(new dev.worldconfigmanager.core.protocol.RateLimiter(1000, 1000, System::nanoTime));
		Minecraft mc = Minecraft.getInstance();
		String name = mc.player != null ? mc.player.getName().getString() : "local";
		this.auth = new PlayerAuth(new UUID(0, 0), name, PermissionRole.OWNER);
		Response hello = call("hello", dev.worldconfigmanager.fabric.client.backend.J.of("protocol", WcmProtocol.VERSION));
		this.info = BackendInfo.fromHello(hello.body());
	}

	/**
	 * Opens the local store for a world/server key.
	 *
	 * @param key stable key: the server-provided world id, or a hash of the server address
	 */
	public static LocalBackend open(Path configDir, String key, WorldDescriptor descriptor, ProviderRegistry registry) throws StorageException {
		Path clientDir = configDir.resolve("client");
		ProviderContextFactory factory = new ProviderContextFactory();
		WorldSession session = new WorldSession(clientDir, clientDir.resolve("worlds").resolve(key), descriptor, registry, factory::create,
			Clock.systemUTC());
		return new LocalBackend(session);
	}

	private static final class ProviderContextFactory {
		ProviderContext create(UUID worldId) {
			return new ProviderContext() {
				@Override
				public Set<ConfigScope> scopes() {
					return Set.of(ConfigScope.CLIENT);
				}

				@Override
				public Optional<String> worldId() {
					return Optional.of(worldId.toString());
				}

				@Override
				public <T> Optional<T> service(Class<T> type) {
					Minecraft mc = Minecraft.getInstance();
					return type.isInstance(mc) ? Optional.of(type.cast(mc)) : Optional.empty();
				}
			};
		}
	}

	private Response call(String type, JsonObject body) {
		JsonObject msg = new JsonObject();
		msg.addProperty("id", 1);
		msg.addProperty("type", type);
		msg.add("body", body);
		try {
			return Response.parse(Json.parseObject(handler.handle(auth, state, Json.compact(msg))));
		} catch (JsonFormatException e) {
			return Response.error("wcm.error.internal");
		}
	}

	@Override
	public void request(String type, JsonObject body, Consumer<Response> callback) {
		callback.accept(call(type, body));
	}

	@Override
	public BackendInfo info() {
		return info;
	}

	@Override
	public boolean isServer() {
		return false;
	}
}
