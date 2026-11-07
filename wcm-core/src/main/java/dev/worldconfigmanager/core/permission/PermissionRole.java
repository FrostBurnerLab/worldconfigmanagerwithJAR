package dev.worldconfigmanager.core.permission;

/** Permission groups in ascending order of authority. */
public enum PermissionRole {
	/** Read-only access to permitted information. */
	PLAYER,
	/** Access according to configurable server rules (vanilla game-master level). */
	OPERATOR,
	/** Authorized configuration management (vanilla admin level). */
	ADMIN,
	/** Full server configuration access (vanilla owner level or singleplayer host with cheats). */
	OWNER;

	public boolean atLeast(PermissionRole other) {
		return ordinal() >= other.ordinal();
	}
}
