package dev.worldconfigmanager.core.apply;

/** Classification of a requested value in an apply plan. */
public enum ChangeStatus {
	/** The value differs from the current value and is valid. */
	CHANGE,
	/** The value equals the current value. */
	UNCHANGED,
	/** The value fails validation; the whole operation is refused. */
	INVALID,
	/** The provider or setting is not available; the entry is skipped but preserved in the profile. */
	UNAVAILABLE
}
