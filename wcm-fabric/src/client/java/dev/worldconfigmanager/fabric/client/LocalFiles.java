package dev.worldconfigmanager.fabric.client;

import dev.worldconfigmanager.core.storage.AtomicFiles;
import dev.worldconfigmanager.core.storage.SafePaths;
import dev.worldconfigmanager.core.storage.StorageException;
import dev.worldconfigmanager.core.transfer.WcmpFormat;
import net.fabricmc.loader.api.FabricLoader;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Base64;
import java.util.Comparator;
import java.util.List;
import java.util.stream.Stream;

/**
 * Client-side package files. Exports received from the server are written only into
 * {@code config/worldconfigmanager/exports}; imports are read from {@code imports} or {@code exports} (or a dropped
 * file), with a size limit. Paths never come from the server.
 */
public final class LocalFiles {
	private LocalFiles() {
	}

	public static Path exportsDir() {
		return FabricLoader.getInstance().getConfigDir().resolve("worldconfigmanager").resolve("exports");
	}

	public static Path importsDir() {
		return FabricLoader.getInstance().getConfigDir().resolve("worldconfigmanager").resolve("imports");
	}

	/** Writes base64 package data under a sanitized, non-clashing name. Returns the written file. */
	public static Path saveExport(String suggestedName, String base64) throws StorageException {
		byte[] data;
		try {
			data = Base64.getDecoder().decode(base64);
		} catch (IllegalArgumentException e) {
			throw new StorageException("Server sent invalid export data", e);
		}
		if (data.length > WcmpFormat.MAX_PACKAGE_BYTES) {
			throw new StorageException("Export is larger than the package limit");
		}
		String base = suggestedName.endsWith(WcmpFormat.EXTENSION)
			? suggestedName.substring(0, suggestedName.length() - WcmpFormat.EXTENSION.length()) : suggestedName;
		base = base.replaceAll("[^A-Za-z0-9._-]+", "_").replaceAll("^[._-]+", "");
		if (base.isEmpty()) {
			base = "export";
		}
		Path dir = exportsDir();
		String name = base + WcmpFormat.EXTENSION;
		int n = 2;
		while (Files.exists(dir.resolve(name))) {
			name = base + "-" + n++ + WcmpFormat.EXTENSION;
		}
		Path target = SafePaths.resolveChild(dir, name);
		AtomicFiles.writeBytes(target, data);
		return target;
	}

	/** Lists package files in the imports and exports folders, newest first. */
	public static List<Path> listPackages() {
		List<Path> result = new ArrayList<>();
		for (Path dir : List.of(importsDir(), exportsDir())) {
			if (!Files.isDirectory(dir)) {
				continue;
			}
			try (Stream<Path> files = Files.list(dir)) {
				files.filter(p -> p.getFileName().toString().endsWith(WcmpFormat.EXTENSION) && Files.isRegularFile(p)).forEach(result::add);
			} catch (IOException ignored) {
				// An unreadable folder simply contributes no files.
			}
		}
		result.sort(Comparator.comparing(LocalFiles::modified).reversed());
		return result;
	}

	private static long modified(Path p) {
		try {
			return Files.getLastModifiedTime(p).toMillis();
		} catch (IOException e) {
			return 0;
		}
	}

	/** Reads a package file as base64, refusing files larger than the package limit. */
	public static String readPackage(Path file) throws StorageException {
		try {
			long size = Files.size(file);
			if (size > WcmpFormat.MAX_PACKAGE_BYTES) {
				throw new StorageException("File is larger than " + WcmpFormat.MAX_PACKAGE_BYTES + " bytes");
			}
			return Base64.getEncoder().encodeToString(Files.readAllBytes(file));
		} catch (IOException e) {
			throw new StorageException("Could not read " + file.getFileName() + ": " + e.getMessage(), e);
		}
	}

	public static void ensureDirs() {
		try {
			Files.createDirectories(importsDir());
			Files.createDirectories(exportsDir());
		} catch (IOException ignored) {
			// Folders are created again on the next attempt.
		}
	}
}
