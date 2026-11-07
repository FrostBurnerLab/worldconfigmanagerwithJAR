package dev.worldconfigmanager.core.backup;

/** Why a backup was created. */
public enum BackupReason {
	MANUAL,
	BEFORE_PROFILE_APPLY,
	BEFORE_CHANGES,
	BEFORE_RESTORE
}
