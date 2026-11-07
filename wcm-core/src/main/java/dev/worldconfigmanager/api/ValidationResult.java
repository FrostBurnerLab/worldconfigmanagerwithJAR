package dev.worldconfigmanager.api;

import java.util.List;

/**
 * Outcome of validating a value.
 *
 * @param valid          whether the value is acceptable
 * @param messageKey     translation key describing the problem ({@code null} when valid)
 * @param messageArgs    plain-text arguments for the translation
 */
public record ValidationResult(boolean valid, String messageKey, List<String> messageArgs) {
	private static final ValidationResult OK = new ValidationResult(true, null, List.of());

	public ValidationResult {
		messageArgs = List.copyOf(messageArgs);
	}

	public static ValidationResult ok() {
		return OK;
	}

	public static ValidationResult error(String messageKey, String... args) {
		return new ValidationResult(false, messageKey, List.of(args));
	}

	/** @return an English fallback description, used in logs and command output */
	public String describe() {
		return valid ? "valid" : messageKey + (messageArgs.isEmpty() ? "" : " " + messageArgs);
	}
}
