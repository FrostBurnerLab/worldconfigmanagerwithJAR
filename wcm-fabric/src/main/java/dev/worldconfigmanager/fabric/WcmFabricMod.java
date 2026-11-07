package dev.worldconfigmanager.fabric;

import dev.worldconfigmanager.api.WcmApi;
import dev.worldconfigmanager.api.WcmPlugin;
import dev.worldconfigmanager.core.registry.ProviderRegistry;
import dev.worldconfigmanager.fabric.command.WcmCommands;
import dev.worldconfigmanager.fabric.net.WcmPayloads;
import dev.worldconfigmanager.fabric.provider.GameRulesProvider;
import dev.worldconfigmanager.fabric.provider.WorldSettingsProvider;
import net.fabricmc.api.ModInitializer;
import net.fabricmc.fabric.api.command.v2.CommandRegistrationCallback;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents;
import net.fabricmc.fabric.api.networking.v1.ServerPlayConnectionEvents;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.fabricmc.loader.api.FabricLoader;
import net.fabricmc.loader.api.entrypoint.EntrypointContainer;
import net.minecraft.SharedConstants;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Optional;

/**
 * Common entry point. Fabric's lifecycle requires a static access point for the registry and the running server's
 * runtime; everything else is wired through constructors.
 */
public final class WcmFabricMod implements ModInitializer {
	public static final String MOD_ID = "worldconfigmanager";
	private static final Logger LOGGER = LoggerFactory.getLogger("WorldConfigManager");
	private static final ProviderRegistry REGISTRY = new ProviderRegistry();
	private static volatile WcmServerRuntime runtime;

	public static ProviderRegistry registry() {
		return REGISTRY;
	}

	public static Optional<WcmServerRuntime> runtime() {
		return Optional.ofNullable(runtime);
	}

	@Override
	public void onInitialize() {
		String minecraftVersion = SharedConstants.getCurrentVersion().name();
		REGISTRY.register(new GameRulesProvider(minecraftVersion));
		REGISTRY.register(new WorldSettingsProvider(minecraftVersion));
		loadPlugins();

		WcmPayloads.register();
		ServerPlayNetworking.registerGlobalReceiver(WcmPayloads.Request.TYPE,
			(payload, context) -> runtime().ifPresent(r -> r.onMessage(context.player(), payload.json())));
		ServerPlayConnectionEvents.DISCONNECT.register((handler, server) -> runtime().ifPresent(r -> r.onDisconnect(handler.player.getUUID())));

		ServerLifecycleEvents.SERVER_STARTED.register(server -> runtime = WcmServerRuntime.start(server, REGISTRY));
		ServerLifecycleEvents.SERVER_STOPPED.register(server -> {
			runtime = null;
			REGISTRY.invalidateAll();
		});

		WcmCommands commands = new WcmCommands(WcmFabricMod::runtime);
		CommandRegistrationCallback.EVENT.register((dispatcher, buildContext, selection) -> commands.register(dispatcher));
		LOGGER.info("WorldConfig Manager initialized (API {}, {} provider(s))", WcmApi.API_VERSION, REGISTRY.all().size());
	}

	private static void loadPlugins() {
		for (EntrypointContainer<WcmPlugin> container : FabricLoader.getInstance().getEntrypointContainers(WcmApi.ENTRYPOINT, WcmPlugin.class)) {
			String modId = container.getProvider().getMetadata().getId();
			try {
				container.getEntrypoint().registerProviders(REGISTRY);
			} catch (RuntimeException | LinkageError e) {
				LOGGER.error("WorldConfig Manager integration of mod '{}' failed to register; it is skipped", modId, e);
			}
		}
	}
}
