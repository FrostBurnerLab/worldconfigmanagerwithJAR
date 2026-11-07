package dev.worldconfigmanager.core.world;

/**
 * Platform-provided description of the running world (everything except the persistent id).
 */
public record WorldDescriptor(String displayName, WorldKind kind, String gameMode, boolean hardcore, String minecraftVersion) {
}
