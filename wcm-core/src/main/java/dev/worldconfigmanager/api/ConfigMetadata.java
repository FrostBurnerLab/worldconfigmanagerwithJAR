package dev.worldconfigmanager.api;

import java.util.EnumSet;
import java.util.Objects;
import java.util.Set;
import java.util.regex.Pattern;

/**
 * Declaration of a provider's identity and capabilities.
 *
 * @param providerId      unique id, lower case, e.g. {@code mymod:main}
 * @param modId           id of the mod that owns the provider
 * @param displayName     player-facing name
 * @param version         provider version (semantic version recommended, e.g. {@code 1.2.0})
 * @param schemaVersion   version of the provider's key/value schema; increase when keys or types change
 * @param apiMajor        WCM API major version the provider was built against
 * @param supportedScopes scopes this provider offers settings in
 * @param transactional   {@code true} if {@link ConfigProvider#apply} is all-or-nothing
 */
public record ConfigMetadata(String providerId, String modId, LocalizedText displayName, String version, int schemaVersion,
	int apiMajor, Set<ConfigScope> supportedScopes, boolean transactional) {
	private static final Pattern ID = Pattern.compile("[a-z0-9_.-]+(:[a-z0-9_./-]+)?");

	public ConfigMetadata {
		Objects.requireNonNull(providerId, "providerId");
		Objects.requireNonNull(modId, "modId");
		Objects.requireNonNull(displayName, "displayName");
		Objects.requireNonNull(version, "version");
		if (!ID.matcher(providerId).matches()) {
			throw new IllegalArgumentException("Invalid provider id: " + providerId);
		}
		if (supportedScopes == null || supportedScopes.isEmpty()) {
			throw new IllegalArgumentException("Provider " + providerId + " must support at least one scope");
		}
		supportedScopes = Set.copyOf(EnumSet.copyOf(supportedScopes));
	}
}
