package dev.worldconfigmanager.core.compare;

/** Classification of a comparison row. */
public enum DiffStatus {
	IDENTICAL,
	CHANGED,
	ONLY_LEFT,
	ONLY_RIGHT,
	/** The provider or setting is not installed/available. */
	UNAVAILABLE,
	/** A value does not match the installed definition (type or validation). */
	INCOMPATIBLE,
	/** The two sides were captured with different major provider versions. */
	VERSION_CONFLICT;

	public boolean isDifference() {
		return this != IDENTICAL;
	}
}
