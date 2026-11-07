package dev.worldconfigmanager.core;

/** Version constants of this WorldConfig Manager build and its data formats. */
public final class WcmVersion {
	/** Mod version; kept in sync with {@code mod_version} in gradle.properties (verified by a unit test). */
	public static final String MOD_VERSION = "0.1.0";
	/** Current profile schema version written by this build. */
	public static final int PROFILE_SCHEMA = 2;
	/** Current .wcmp package format version. */
	public static final int PACKAGE_FORMAT = 1;
	/** Current backup file schema version. */
	public static final int BACKUP_SCHEMA = 1;

	private WcmVersion() {
	}
}
