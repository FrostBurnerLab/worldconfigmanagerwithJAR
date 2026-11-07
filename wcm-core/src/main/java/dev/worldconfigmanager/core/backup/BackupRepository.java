package dev.worldconfigmanager.core.backup;

import dev.worldconfigmanager.core.json.Json;
import dev.worldconfigmanager.core.json.JsonFormatException;
import dev.worldconfigmanager.core.storage.AtomicFiles;
import dev.worldconfigmanager.core.storage.SafePaths;
import dev.worldconfigmanager.core.storage.StorageException;

import java.io.IOException;
import java.nio.file.DirectoryStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.regex.Pattern;

/**
 * Stores backups as {@code <uuid>.json}. Summaries are cached; full backups are read on demand. Corrupt files are
 * listed as corrupt rather than silently dropped.
 */
public final class BackupRepository {
	private static final Pattern FILE = Pattern.compile("[0-9a-f-]{36}\\.json");

	private final Path directory;
	private final Map<UUID, BackupSummary> summaries = new LinkedHashMap<>();

	public BackupRepository(Path directory) {
		this.directory = directory;
		reload();
	}

	public synchronized void reload() {
		summaries.clear();
		if (!Files.isDirectory(directory)) {
			return;
		}
		try (DirectoryStream<Path> stream = Files.newDirectoryStream(directory, "*.json")) {
			for (Path file : stream) {
				String name = file.getFileName().toString();
				if (!FILE.matcher(name).matches()) {
					continue;
				}
				UUID id;
				try {
					id = UUID.fromString(name.substring(0, 36));
				} catch (IllegalArgumentException e) {
					continue;
				}
				try {
					Backup backup = read(file);
					if (!backup.id().equals(id)) {
						throw new JsonFormatException("File name does not match backup id");
					}
					summaries.put(id, backup.summary());
				} catch (JsonFormatException | StorageException e) {
					summaries.put(id, new BackupSummary(id, fileTime(file), BackupReason.MANUAL, name, null, 0, true, e.getMessage()));
				}
			}
		} catch (IOException e) {
			throw new IllegalStateException("Could not list backups: " + e.getMessage(), e);
		}
	}

	private static java.time.Instant fileTime(Path file) {
		try {
			return Files.getLastModifiedTime(file).toInstant();
		} catch (IOException e) {
			return java.time.Instant.EPOCH;
		}
	}

	private static Backup read(Path file) throws StorageException, JsonFormatException {
		return BackupCodec.fromJson(Json.parseObject(AtomicFiles.readString(file)));
	}

	/** @return summaries, newest first */
	public synchronized List<BackupSummary> list() {
		List<BackupSummary> list = new ArrayList<>(summaries.values());
		list.sort(Comparator.comparing(BackupSummary::createdAt).reversed());
		return List.copyOf(list);
	}

	public synchronized Optional<BackupSummary> summary(UUID id) {
		return Optional.ofNullable(summaries.get(id));
	}

	/** Loads a full backup. */
	public synchronized Backup load(UUID id) throws StorageException, JsonFormatException {
		Path file = SafePaths.resolveChild(directory, id + ".json");
		if (!Files.exists(file)) {
			throw new StorageException("Backup " + id + " does not exist");
		}
		return read(file);
	}

	public synchronized void save(Backup backup) throws StorageException {
		AtomicFiles.writeString(SafePaths.resolveChild(directory, backup.id() + ".json"), Json.pretty(BackupCodec.toJson(backup)));
		summaries.put(backup.id(), backup.summary());
	}

	public synchronized boolean delete(UUID id) throws StorageException {
		Path file = SafePaths.resolveChild(directory, id + ".json");
		try {
			boolean deleted = Files.deleteIfExists(file);
			summaries.remove(id);
			return deleted;
		} catch (IOException e) {
			throw new StorageException("Failed to delete backup " + id, e);
		}
	}
}
