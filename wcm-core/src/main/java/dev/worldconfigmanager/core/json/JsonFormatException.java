package dev.worldconfigmanager.core.json;

/** Thrown when a JSON document is malformed or violates the expected schema. */
public class JsonFormatException extends Exception {
	public JsonFormatException(String message) {
		super(message);
	}

	public JsonFormatException(String message, Throwable cause) {
		super(message, cause);
	}
}
