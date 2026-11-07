package dev.worldconfigmanager.fabric;

import dev.worldconfigmanager.api.ConfigScope;
import dev.worldconfigmanager.api.ProviderContext;
import net.minecraft.server.MinecraftServer;

import java.util.Optional;
import java.util.Set;
import java.util.UUID;

/** Provider context for the (integrated or dedicated) server. Exposes the {@link MinecraftServer} as a service. */
public record FabricProviderContext(MinecraftServer server, UUID world) implements ProviderContext {
	public static final Set<ConfigScope> SERVER_SCOPES = Set.of(ConfigScope.WORLD, ConfigScope.SERVER);

	@Override
	public Set<ConfigScope> scopes() {
		return SERVER_SCOPES;
	}

	@Override
	public Optional<String> worldId() {
		return Optional.of(world.toString());
	}

	@Override
	public <T> Optional<T> service(Class<T> type) {
		return type.isInstance(server) ? Optional.of(type.cast(server)) : Optional.empty();
	}
}
