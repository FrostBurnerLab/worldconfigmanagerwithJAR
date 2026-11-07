package dev.worldconfigmanager.fabric.example;

import dev.worldconfigmanager.api.ConfigScope;
import dev.worldconfigmanager.api.ProviderContext;
import dev.worldconfigmanager.api.ProviderRegistrar;
import dev.worldconfigmanager.api.WcmPlugin;
import net.fabricmc.fabric.api.networking.v1.ServerPlayConnectionEvents;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;

import java.util.Optional;
import java.util.Set;

/**
 * Example {@link WcmPlugin}. WCM discovers it through the {@code worldconfigmanager} entrypoint declared in
 * {@code fabric.mod.json}, exactly as a third-party mod would.
 */
public final class ExampleWcmPlugin implements WcmPlugin {
	@Override
	public void registerProviders(ProviderRegistrar registrar) {
		JoinMessageProvider provider = new JoinMessageProvider();
		registrar.register(provider);
		ServerPlayConnectionEvents.JOIN.register((handler, sender, server) -> {
			ProviderContext context = context(server);
			ChatFormatting color = colorOf(provider.color(context));
			for (String line : provider.announcement(context)) {
				handler.player.sendSystemMessage(Component.literal(line).withStyle(color));
			}
		});
	}

	private static ChatFormatting colorOf(String name) {
		try {
			return ChatFormatting.valueOf(name.toUpperCase(java.util.Locale.ROOT));
		} catch (IllegalArgumentException e) {
			return ChatFormatting.GOLD;
		}
	}

	private static ProviderContext context(MinecraftServer server) {
		return new ProviderContext() {
			@Override
			public Set<ConfigScope> scopes() {
				return Set.of(ConfigScope.WORLD);
			}

			@Override
			public Optional<String> worldId() {
				return Optional.empty();
			}

			@Override
			public <T> Optional<T> service(Class<T> type) {
				return type.isInstance(server) ? Optional.of(type.cast(server)) : Optional.empty();
			}
		};
	}
}
