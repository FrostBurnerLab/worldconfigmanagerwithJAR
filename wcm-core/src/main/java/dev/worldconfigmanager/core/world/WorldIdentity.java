package dev.worldconfigmanager.core.world;

import java.util.Objects;
import java.util.UUID;

/**
 * Identity and descriptive information of the current world. The {@code worldId} is a persistent generated UUID and
 * never derived from the display name.
 *
 * @param worldId          persistent world UUID
 * @param displayName      world name (display only)
 * @param kind             hosting kind
 * @param gameMode         default game mode id, e.g. {@code survival}
 * @param hardcore         hardcore flag
 * @param minecraftVersion running Minecraft version
 */
public record WorldIdentity(UUID worldId, String displayName, WorldKind kind, String gameMode, boolean hardcore,
	String minecraftVersion) {
	public WorldIdentity {
		Objects.requireNonNull(worldId, "worldId");
		Objects.requireNonNull(displayName, "displayName");
		Objects.requireNonNull(kind, "kind");
		gameMode = gameMode == null ? "" : gameMode;
		minecraftVersion = minecraftVersion == null ? "" : minecraftVersion;
	}
}
