package dev.worldconfigmanager.api;

import java.util.Objects;

/**
 * Player-facing text: a translation key with a plain fallback that is shown when the key is unknown to the client.
 *
 * @param translationKey translation key, may be {@code null}
 * @param fallback       plain text used when no translation exists
 */
public record LocalizedText(String translationKey, String fallback) {
	public LocalizedText {
		Objects.requireNonNull(fallback, "fallback");
	}

	public static LocalizedText literal(String text) {
		return new LocalizedText(null, text);
	}

	public static LocalizedText translatable(String key, String fallback) {
		return new LocalizedText(key, fallback);
	}
}
