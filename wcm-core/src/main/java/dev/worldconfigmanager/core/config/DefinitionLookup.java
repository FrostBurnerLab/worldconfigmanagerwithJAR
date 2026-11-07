package dev.worldconfigmanager.core.config;

import dev.worldconfigmanager.api.ConfigDefinition;
import dev.worldconfigmanager.api.ConfigKey;

import java.util.Optional;

/** Read-only lookup of installed setting definitions and provider versions. */
public interface DefinitionLookup {
	Optional<ConfigDefinition> definition(ConfigKey key);

	/** @return the installed version of a provider, empty if it is not installed or unavailable */
	Optional<String> providerVersion(String providerId);
}
