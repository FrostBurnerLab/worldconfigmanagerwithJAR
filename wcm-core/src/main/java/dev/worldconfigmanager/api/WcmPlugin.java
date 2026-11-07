package dev.worldconfigmanager.api;

/**
 * Integration entry point for third-party mods. Declare the implementation in {@code fabric.mod.json}:
 *
 * <pre>{@code
 * "entrypoints": { "worldconfigmanager": ["com.example.MyWcmPlugin"] }
 * }</pre>
 *
 * The plugin is loaded on both client and server.
 */
@FunctionalInterface
public interface WcmPlugin {
	void registerProviders(ProviderRegistrar registrar);
}
