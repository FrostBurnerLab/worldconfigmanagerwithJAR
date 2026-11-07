package dev.worldconfigmanager.core.transfer;

/** Status of a single imported entry in the preview. Every entry is preserved, whatever its status. */
public enum EntryStatus {
	NEW,
	SAME,
	CONFLICT,
	MISSING_PROVIDER,
	UNSUPPORTED_SETTING,
	INCOMPATIBLE_VALUE
}
