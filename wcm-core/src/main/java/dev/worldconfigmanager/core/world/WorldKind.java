package dev.worldconfigmanager.core.world;

/** How the current world is hosted. */
public enum WorldKind {
	/** A singleplayer world running on the integrated server. */
	SINGLEPLAYER,
	/** A world hosted by a dedicated server. */
	DEDICATED,
	/** A remote server with WCM, identified through the protocol. */
	REMOTE_WCM,
	/** A remote server without WCM; only local client profiles are available. */
	REMOTE_VANILLA
}
