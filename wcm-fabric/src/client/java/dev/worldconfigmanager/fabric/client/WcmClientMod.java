package dev.worldconfigmanager.fabric.client;

import com.mojang.blaze3d.platform.InputConstants;
import dev.worldconfigmanager.fabric.WcmFabricMod;
import dev.worldconfigmanager.fabric.net.WcmPayloads;
import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.keymapping.v1.KeyMappingHelper;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayConnectionEvents;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.fabricmc.fabric.api.client.screen.v1.ScreenEvents;
import net.fabricmc.fabric.api.client.screen.v1.Screens;
import net.minecraft.SharedConstants;
import net.minecraft.client.KeyMapping;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.Tooltip;
import net.minecraft.client.gui.screens.PauseScreen;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;

/** Client entry point: networking, the key binding, the pause-menu shortcut and the client options provider. */
public final class WcmClientMod implements ClientModInitializer {
	private static final KeyMapping.Category CATEGORY = KeyMapping.Category.register(Identifier.fromNamespaceAndPath(WcmFabricMod.MOD_ID, "main"));

	@Override
	public void onInitializeClient() {
		WcmFabricMod.registry().register(new ClientOptionsProvider(SharedConstants.getCurrentVersion().name()));
		ClientPlayNetworking.registerGlobalReceiver(WcmPayloads.Response.TYPE, (payload, context) -> context.client().execute(() -> ClientSession.onMessage(payload.json())));
		ClientPlayConnectionEvents.JOIN.register((handler, sender, client) -> ClientSession.onJoin(client));
		ClientPlayConnectionEvents.DISCONNECT.register((handler, client) -> client.execute(ClientSession::reset));

		// Unbound by default to avoid conflicts; players can bind it in Controls. The pause menu button and /wcm open also work.
		KeyMapping open = KeyMappingHelper.registerKeyMapping(new KeyMapping("key.worldconfigmanager.open", InputConstants.Type.KEYBOARD,
			InputConstants.UNKNOWN.getValue(), CATEGORY));
		ClientTickEvents.END_CLIENT_TICK.register(client -> {
			ClientSession.tick(client);
			while (open.consumeClick()) {
				ClientSession.openScreen(client);
			}
		});

		ScreenEvents.AFTER_INIT.register((client, screen, width, height) -> {
			if (screen instanceof PauseScreen && ClientSettings.get().pauseMenuButton()) {
				Button button = Button.builder(Component.translatable("wcm.pause.button"), b -> ClientSession.openScreen(client))
					.bounds(width - 54, 4, 50, 20)
					.tooltip(Tooltip.create(Component.translatable("wcm.pause.tooltip")))
					.build();
				Screens.getWidgets(screen).add(button);
			}
		});
	}
}
