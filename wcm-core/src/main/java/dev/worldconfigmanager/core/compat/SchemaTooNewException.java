package dev.worldconfigmanager.core.compat;

import dev.worldconfigmanager.core.json.JsonFormatException;

/** Thrown when data was written by a newer WCM with a schema this build cannot read safely. */
public final class SchemaTooNewException extends JsonFormatException {
	private final int found;
	private final int supported;

	public SchemaTooNewException(String what, int found, int supported) {
		super(what + " schema version " + found + " is newer than the supported version " + supported
			+ "; update WorldConfig Manager to use it");
		this.found = found;
		this.supported = supported;
	}

	public int found() {
		return found;
	}

	public int supported() {
		return supported;
	}
}
