package dev.worldconfigmanager.core.storage;

import java.nio.file.Path;
import java.util.regex.Pattern;

/** Path resolution that refuses anything escaping a storage root. */
public final class SafePaths {
	private static final Pattern SAFE_NAME = Pattern.compile("[A-Za-z0-9][A-Za-z0-9._-]{0,127}");

	private SafePaths() {
	}

	/**
	 * Resolves a single file name inside {@code root}.
	 *
	 * @throws StorageException if the name contains separators, traversal or unusual characters
	 */
	public static Path resolveChild(Path root, String fileName) throws StorageException {
		if (fileName == null || !SAFE_NAME.matcher(fileName).matches() || fileName.contains("..")) {
			throw new StorageException("Unsafe file name: " + fileName);
		}
		Path normalizedRoot = root.toAbsolutePath().normalize();
		Path resolved = normalizedRoot.resolve(fileName).normalize();
		if (!resolved.getParent().equals(normalizedRoot)) {
			throw new StorageException("Path escapes storage directory: " + fileName);
		}
		return resolved;
	}

	/** @return {@code true} if the name is acceptable for {@link #resolveChild} */
	public static boolean isSafeName(String fileName) {
		return fileName != null && SAFE_NAME.matcher(fileName).matches() && !fileName.contains("..");
	}
}
