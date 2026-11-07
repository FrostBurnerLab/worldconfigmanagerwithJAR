package dev.worldconfigmanager.api;

/** Declares how a change to a setting takes effect. Providers must declare this explicitly. */
public enum ReloadBehavior {
	/** The new value is effective immediately (hot reload). */
	HOT_RELOAD,
	/** The new value is stored but only takes effect after the world is reloaded. */
	WORLD_RELOAD,
	/** The new value is stored but only takes effect after the game or server restarts. */
	GAME_RESTART;

	/** @return {@code true} if a restart or reload is needed before the change is effective. */
	public boolean requiresRestart() {
		return this != HOT_RELOAD;
	}
}
