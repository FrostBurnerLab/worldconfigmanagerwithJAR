package dev.worldconfigmanager.core.apply;

/** Final result of an apply operation. */
public enum ApplyStatus {
	/** All changes were applied. */
	SUCCESS,
	/** Nothing differed from the current state; no backup was taken. */
	NO_CHANGES,
	/** At least one value was invalid; nothing was changed. */
	VALIDATION_FAILED,
	/** The safety backup could not be created; nothing was changed. */
	BACKUP_FAILED,
	/** A provider failed; all changes were rolled back successfully. */
	ROLLED_BACK,
	/** A provider failed and rolling back failed for at least one provider. Manual restore may be needed. */
	ROLLBACK_INCOMPLETE;

	public boolean isSuccess() {
		return this == SUCCESS || this == NO_CHANGES;
	}
}
