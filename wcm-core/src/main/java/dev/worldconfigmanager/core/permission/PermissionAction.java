package dev.worldconfigmanager.core.permission;

/** Privileged actions; each is checked on the server for every request. */
public enum PermissionAction {
	VIEW_DASHBOARD(PermissionRole.PLAYER),
	VIEW_SETTINGS(PermissionRole.OPERATOR),
	EDIT_SETTINGS(PermissionRole.OPERATOR),
	VIEW_PROFILES(PermissionRole.OPERATOR),
	MANAGE_PROFILES(PermissionRole.OPERATOR),
	APPLY_PROFILES(PermissionRole.OPERATOR),
	VIEW_HISTORY(PermissionRole.OPERATOR),
	VIEW_BACKUPS(PermissionRole.OPERATOR),
	CREATE_BACKUPS(PermissionRole.OPERATOR),
	RESTORE_BACKUPS(PermissionRole.ADMIN),
	DELETE_BACKUPS(PermissionRole.ADMIN),
	EXPORT(PermissionRole.OPERATOR),
	IMPORT(PermissionRole.ADMIN),
	MANAGE_WCM_SETTINGS(PermissionRole.OWNER);

	private final PermissionRole defaultRole;

	PermissionAction(PermissionRole defaultRole) {
		this.defaultRole = defaultRole;
	}

	public PermissionRole defaultRole() {
		return defaultRole;
	}
}
