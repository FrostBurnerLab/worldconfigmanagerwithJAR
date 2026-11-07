package dev.worldconfigmanager.core.history;

/** Kind of operation that produced a history entry. */
public enum HistoryOperation {
	SET_VALUE,
	RESET_DEFAULT,
	APPLY_PROFILE,
	RESTORE_BACKUP
}
