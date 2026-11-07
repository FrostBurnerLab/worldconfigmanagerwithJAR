package dev.worldconfigmanager.core.compat;

import java.util.List;

/**
 * A single compatibility finding.
 *
 * @param providerId affected provider ({@code null} for profile-level issues)
 * @param key        affected key ({@code null} for provider/profile-level issues)
 * @param args       plain-text arguments for the translation key {@code wcm.compat.<code>}
 */
public record CompatibilityIssue(Severity severity, Code code, String providerId, String key, List<String> args) {
	public CompatibilityIssue {
		args = List.copyOf(args);
	}

	/** Issue severity. */
	public enum Severity {
		INFO,
		WARNING,
		ERROR
	}

	/** Issue codes; each maps to translation key {@code wcm.compat.<lower-case code>}. */
	public enum Code {
		MISSING_PROVIDER(Severity.WARNING),
		MISSING_SETTING(Severity.WARNING),
		TYPE_CHANGED(Severity.ERROR),
		INVALID_VALUE(Severity.ERROR),
		PROVIDER_MAJOR_CHANGED(Severity.WARNING),
		PROVIDER_NEWER_IN_PROFILE(Severity.WARNING),
		RESTART_REQUIRED(Severity.INFO),
		MINECRAFT_VERSION_DIFFERS(Severity.INFO),
		WCM_NEWER_IN_PROFILE(Severity.WARNING);

		private final Severity severity;

		Code(Severity severity) {
			this.severity = severity;
		}

		public Severity severity() {
			return severity;
		}

		public String translationKey() {
			return "wcm.compat." + name().toLowerCase(java.util.Locale.ROOT);
		}
	}

	public static CompatibilityIssue of(Code code, String providerId, String key, String... args) {
		return new CompatibilityIssue(code.severity(), code, providerId, key, List.of(args));
	}
}
