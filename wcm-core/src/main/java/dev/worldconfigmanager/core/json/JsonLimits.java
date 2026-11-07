package dev.worldconfigmanager.core.json;

/** Bounds applied to every untrusted JSON document. */
public final class JsonLimits {
	/** Maximum nesting depth of configuration values. */
	public static final int MAX_VALUE_DEPTH = 16;
	/** Maximum number of elements in a list or fields in a structure. */
	public static final int MAX_COLLECTION_SIZE = 4096;
	/** Maximum length of a single string. */
	public static final int MAX_STRING_LENGTH = 32_768;
	/** Maximum size of a single JSON file read from disk. */
	public static final long MAX_FILE_BYTES = 4L * 1024 * 1024;

	private JsonLimits() {
	}
}
