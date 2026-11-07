package dev.worldconfigmanager.core.storage;

/** Raised when persisted data cannot be read or written safely. */
public final class StorageException extends Exception {
	public StorageException(String message) {
		super(message);
	}

	public StorageException(String message, Throwable cause) {
		super(message, cause);
	}
}
