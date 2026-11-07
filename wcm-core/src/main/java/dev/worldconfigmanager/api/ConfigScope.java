package dev.worldconfigmanager.api;

/**
 * Determines where a setting is stored, who may edit it and whether it can be synchronized.
 */
public enum ConfigScope {
	/** Local settings belonging to an individual player. Never sent to a server. */
	CLIENT,
	/** Settings stored with an individual world. Edited through the server that owns the world. */
	WORLD,
	/** Settings controlled by the server, independent of a single world. */
	SERVER;

	/** @return {@code true} if values in this scope are owned by the (integrated or dedicated) server. */
	public boolean isServerAuthoritative() {
		return this != CLIENT;
	}
}
