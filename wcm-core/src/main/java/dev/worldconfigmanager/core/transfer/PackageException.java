package dev.worldconfigmanager.core.transfer;

/** Raised when a .wcmp package is rejected. Nothing is imported in that case. */
public final class PackageException extends Exception {
	/** Rejection reasons; each maps to translation key {@code wcm.package.<lower-case reason>}. */
	public enum Reason {
		TOO_LARGE,
		NOT_A_PACKAGE,
		CORRUPT,
		UNSAFE_PATH,
		UNEXPECTED_ENTRY,
		DUPLICATE_ENTRY,
		TOO_MANY_ENTRIES,
		MISSING_MANIFEST,
		INVALID_MANIFEST,
		UNSUPPORTED_VERSION,
		CHECKSUM_MISMATCH,
		INVALID_PROFILE
	}

	private final Reason reason;

	public PackageException(Reason reason, String message) {
		super(message);
		this.reason = reason;
	}

	public PackageException(Reason reason, String message, Throwable cause) {
		super(message, cause);
		this.reason = reason;
	}

	public Reason reason() {
		return reason;
	}

	public String translationKey() {
		return "wcm.package." + reason.name().toLowerCase(java.util.Locale.ROOT);
	}
}
