package dev.worldconfigmanager.core.storage;

import dev.worldconfigmanager.core.json.JsonLimits;

import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.channels.FileChannel;
import java.nio.charset.StandardCharsets;
import java.nio.file.AtomicMoveNotSupportedException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.nio.file.StandardOpenOption;

/**
 * Crash-safe file writes: data is written to a temporary sibling, flushed to disk and then atomically moved over the
 * target. An interrupted write therefore never leaves a truncated target file behind.
 */
public final class AtomicFiles {
	private AtomicFiles() {
	}

	public static void writeString(Path target, String content) throws StorageException {
		writeBytes(target, content.getBytes(StandardCharsets.UTF_8));
	}

	public static void writeBytes(Path target, byte[] content) throws StorageException {
		Path dir = target.toAbsolutePath().getParent();
		Path temp = null;
		try {
			Files.createDirectories(dir);
			temp = Files.createTempFile(dir, "." + target.getFileName() + ".", ".tmp");
			try (FileChannel channel = FileChannel.open(temp, StandardOpenOption.WRITE, StandardOpenOption.TRUNCATE_EXISTING)) {
				ByteBuffer buffer = ByteBuffer.wrap(content);
				while (buffer.hasRemaining()) {
					channel.write(buffer);
				}
				channel.force(true);
			}
			try {
				Files.move(temp, target, StandardCopyOption.ATOMIC_MOVE, StandardCopyOption.REPLACE_EXISTING);
			} catch (AtomicMoveNotSupportedException e) {
				Files.move(temp, target, StandardCopyOption.REPLACE_EXISTING);
			}
			temp = null;
		} catch (IOException e) {
			throw new StorageException("Failed to write " + target.getFileName() + ": " + e.getMessage(), e);
		} finally {
			if (temp != null) {
				try {
					Files.deleteIfExists(temp);
				} catch (IOException ignored) {
					// Best effort cleanup of a temp file; the target was not modified.
				}
			}
		}
	}

	/** Reads a UTF-8 file, refusing files larger than {@link JsonLimits#MAX_FILE_BYTES}. */
	public static String readString(Path file) throws StorageException {
		try {
			long size = Files.size(file);
			if (size > JsonLimits.MAX_FILE_BYTES) {
				throw new StorageException("File too large: " + file.getFileName() + " (" + size + " bytes)");
			}
			return Files.readString(file, StandardCharsets.UTF_8);
		} catch (IOException e) {
			throw new StorageException("Failed to read " + file.getFileName() + ": " + e.getMessage(), e);
		}
	}

	/**
	 * Moves an unreadable file aside (to {@code name.corrupt-<timestamp>}) so it is preserved for inspection but no
	 * longer loaded.
	 */
	public static Path quarantine(Path file) throws StorageException {
		Path target = file.resolveSibling(file.getFileName() + ".corrupt-" + System.currentTimeMillis());
		try {
			return Files.move(file, target);
		} catch (IOException e) {
			throw new StorageException("Failed to quarantine " + file.getFileName(), e);
		}
	}
}
