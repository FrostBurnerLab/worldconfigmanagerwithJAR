package dev.worldconfigmanager.api;

import java.util.Locale;

/** Data types supported for configuration values. */
public enum ValueType {
	BOOLEAN,
	INTEGER,
	DECIMAL,
	STRING,
	ENUM,
	LIST,
	STRUCTURED;

	/** @return the stable lower-case identifier used in serialized data. */
	public String id() {
		return name().toLowerCase(Locale.ROOT);
	}

	/**
	 * Parses a serialized identifier.
	 *
	 * @throws IllegalArgumentException if the identifier is unknown
	 */
	public static ValueType fromId(String id) {
		for (ValueType type : values()) {
			if (type.id().equals(id)) {
				return type;
			}
		}
		throw new IllegalArgumentException("Unknown value type: " + id);
	}
}
