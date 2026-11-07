package dev.worldconfigmanager.core.transfer;

/** How imported profiles are combined with existing profiles of the same id. */
public enum ImportMode {
	/** Add new entries to the existing profile; existing values are kept on conflict. */
	MERGE,
	/** Replace the existing profile completely. Refused if the local copy is newer unless explicitly allowed. */
	REPLACE
}
