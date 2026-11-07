package dev.worldconfigmanager.core.compat;

import com.google.gson.JsonObject;
import dev.worldconfigmanager.core.json.Json;
import dev.worldconfigmanager.core.json.JsonFormatException;

import java.util.Map;
import java.util.TreeMap;

/**
 * Upgrades JSON documents step by step from older schema versions to the current one. Each step converts version
 * {@code n} to {@code n + 1}. Documents newer than the current version are rejected, never downgraded.
 */
public final class SchemaMigrator {
	/** A single migration step from version {@code n} to {@code n + 1}. */
	@FunctionalInterface
	public interface Step {
		JsonObject migrate(JsonObject document) throws JsonFormatException;
	}

	private final String what;
	private final int currentVersion;
	private final String versionField;
	private final Map<Integer, Step> steps = new TreeMap<>();

	public SchemaMigrator(String what, int currentVersion, String versionField) {
		this.what = what;
		this.currentVersion = currentVersion;
		this.versionField = versionField;
	}

	/** Registers the step that upgrades {@code fromVersion} to {@code fromVersion + 1}. */
	public SchemaMigrator step(int fromVersion, Step step) {
		steps.put(fromVersion, step);
		return this;
	}

	/** @return the schema version declared by the document */
	public int versionOf(JsonObject document, String legacyField) throws JsonFormatException {
		if (document.has(versionField)) {
			return (int) Json.integer(document, versionField);
		}
		if (legacyField != null && document.has(legacyField)) {
			return (int) Json.integer(document, legacyField);
		}
		throw new JsonFormatException(what + " has no schema version");
	}

	/** Migrates a document whose version is {@code version} to the current version. */
	public JsonObject migrate(JsonObject document, int version) throws JsonFormatException {
		if (version > currentVersion) {
			throw new SchemaTooNewException(what, version, currentVersion);
		}
		if (version < 1) {
			throw new JsonFormatException(what + " has invalid schema version " + version);
		}
		JsonObject current = document;
		for (int v = version; v < currentVersion; v++) {
			Step step = steps.get(v);
			if (step == null) {
				throw new JsonFormatException("No migration from " + what + " schema " + v);
			}
			current = step.migrate(current);
			current.addProperty(versionField, v + 1);
		}
		return current;
	}

	public int currentVersion() {
		return currentVersion;
	}
}
