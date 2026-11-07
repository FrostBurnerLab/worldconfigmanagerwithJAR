package dev.worldconfigmanager.api;

/**
 * Version information for the public integration API.
 *
 * <p>The API follows semantic versioning: a provider compiled against API {@code 1.x} works with every
 * WorldConfig Manager release that reports API major version {@code 1}.
 */
public final class WcmApi {
	/** Semantic version of this API package. */
	public static final String API_VERSION = "1.0.0";
	/** Major version; incremented only for breaking changes. */
	public static final int API_MAJOR = 1;
	/** Name of the Fabric entrypoint used to discover {@link WcmPlugin} implementations. */
	public static final String ENTRYPOINT = "worldconfigmanager";

	private WcmApi() {
	}
}
