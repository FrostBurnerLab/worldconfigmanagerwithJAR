package dev.worldconfigmanager.fabric;

import dev.worldconfigmanager.core.permission.PermissionRole;
import net.minecraft.server.permissions.PermissionSet;
import net.minecraft.server.permissions.Permissions;

/** Maps vanilla permission sets (computed by the server) to WCM roles. */
public final class PermissionResolver {
	private PermissionResolver() {
	}

	public static PermissionRole role(PermissionSet permissions) {
		if (permissions.hasPermission(Permissions.COMMANDS_OWNER)) {
			return PermissionRole.OWNER;
		}
		if (permissions.hasPermission(Permissions.COMMANDS_ADMIN)) {
			return PermissionRole.ADMIN;
		}
		if (permissions.hasPermission(Permissions.COMMANDS_GAMEMASTER)) {
			return PermissionRole.OPERATOR;
		}
		return PermissionRole.PLAYER;
	}
}
