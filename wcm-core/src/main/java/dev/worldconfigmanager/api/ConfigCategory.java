package dev.worldconfigmanager.api;

/** Top-level categories used to organise settings in the configuration browser. */
public enum ConfigCategory {
	VANILLA,
	GAMEPLAY,
	WORLD_GENERATION,
	PERFORMANCE,
	MOBS_AND_ENTITIES,
	ENVIRONMENT,
	MULTIPLAYER,
	OTHER_MODS;

	/** @return the translation key for this category. */
	public String translationKey() {
		return "wcm.category." + name().toLowerCase(java.util.Locale.ROOT);
	}
}
