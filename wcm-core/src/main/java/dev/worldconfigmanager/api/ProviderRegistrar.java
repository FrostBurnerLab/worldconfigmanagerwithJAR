package dev.worldconfigmanager.api;

/** Registration entry point handed to {@link WcmPlugin}s. */
public interface ProviderRegistrar {
	/**
	 * Registers a provider.
	 *
	 * @throws IllegalArgumentException if a provider with the same id is already registered
	 */
	void register(ConfigProvider provider);
}
