package dev.worldconfigmanager.api;

import java.util.Optional;
import java.util.Set;

/**
 * Environment handed to a provider for every operation. Platform objects (for example the Minecraft server) are
 * exposed through {@link #service(Class)} so that providers do not need global state.
 *
 * <p>World and server scoped operations are always invoked on the server thread.
 */
public interface ProviderContext {
	/**
	 * @return the scopes managed in this context: {@code WORLD} and {@code SERVER} on a server, {@code CLIENT} for
	 * local client profiles
	 */
	Set<ConfigScope> scopes();

	/** @return the persistent identity of the current world, if any */
	Optional<String> worldId();

	/** Looks up a platform service, such as {@code net.minecraft.server.MinecraftServer}. */
	<T> Optional<T> service(Class<T> type);
}
