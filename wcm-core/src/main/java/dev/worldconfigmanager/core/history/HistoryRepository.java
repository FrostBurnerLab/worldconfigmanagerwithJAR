package dev.worldconfigmanager.core.history;

import dev.worldconfigmanager.core.json.Json;
import dev.worldconfigmanager.core.json.JsonFormatException;
import dev.worldconfigmanager.core.storage.AtomicFiles;
import dev.worldconfigmanager.core.storage.StorageException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.function.Predicate;

/**
 * Append-only JSON-lines history file with an in-memory copy. Pruning rewrites the file atomically. Unparseable lines
 * (e.g. a torn final line after a crash) are skipped and reported in the log.
 */
public final class HistoryRepository {
	private static final Logger LOGGER = LoggerFactory.getLogger("WorldConfigManager");
	private static final long MAX_FILE_BYTES = 64L * 1024 * 1024;

	private final Path file;
	private final List<HistoryEntry> entries = new ArrayList<>();
	private int skippedLines;

	public HistoryRepository(Path file) {
		this.file = file;
		load();
	}

	private void load() {
		entries.clear();
		skippedLines = 0;
		if (!Files.exists(file)) {
			return;
		}
		try {
			if (Files.size(file) > MAX_FILE_BYTES) {
				LOGGER.error("History file is larger than {} bytes; it is quarantined and history starts empty", MAX_FILE_BYTES);
				AtomicFiles.quarantine(file);
				return;
			}
			for (String line : Files.readAllLines(file, StandardCharsets.UTF_8)) {
				if (line.isBlank()) {
					continue;
				}
				try {
					entries.add(HistoryCodec.fromJson(Json.parseObject(line)));
				} catch (JsonFormatException e) {
					skippedLines++;
				}
			}
		} catch (IOException | StorageException e) {
			LOGGER.error("Could not read history: {}", e.getMessage());
		}
		if (skippedLines > 0) {
			LOGGER.warn("Skipped {} unreadable history line(s)", skippedLines);
		}
	}

	public synchronized void append(List<HistoryEntry> newEntries) throws StorageException {
		if (newEntries.isEmpty()) {
			return;
		}
		StringBuilder sb = new StringBuilder();
		for (HistoryEntry e : newEntries) {
			sb.append(Json.compact(HistoryCodec.toJson(e))).append('\n');
		}
		try {
			Files.createDirectories(file.toAbsolutePath().getParent());
			Files.writeString(file, sb.toString(), StandardCharsets.UTF_8, StandardOpenOption.CREATE, StandardOpenOption.APPEND,
				StandardOpenOption.SYNC);
		} catch (IOException e) {
			throw new StorageException("Failed to append history: " + e.getMessage(), e);
		}
		entries.addAll(newEntries);
	}

	/** @return all entries, oldest first */
	public synchronized List<HistoryEntry> all() {
		return List.copyOf(entries);
	}

	public synchronized List<HistoryEntry> query(Predicate<HistoryEntry> filter, int limit) {
		List<HistoryEntry> result = new ArrayList<>();
		for (int i = entries.size() - 1; i >= 0 && result.size() < limit; i--) {
			HistoryEntry e = entries.get(i);
			if (filter.test(e)) {
				result.add(e);
			}
		}
		return result;
	}

	public synchronized int size() {
		return entries.size();
	}

	public synchronized int skippedLines() {
		return skippedLines;
	}

	/** Removes entries beyond {@code maxEntries} (oldest first) and entries before {@code cutoff}. */
	public synchronized int prune(int maxEntries, Instant cutoff) throws StorageException {
		List<HistoryEntry> kept = new ArrayList<>();
		for (HistoryEntry e : entries) {
			if (cutoff == null || !e.timestamp().isBefore(cutoff)) {
				kept.add(e);
			}
		}
		if (kept.size() > maxEntries) {
			kept = new ArrayList<>(kept.subList(kept.size() - maxEntries, kept.size()));
		}
		int removed = entries.size() - kept.size();
		if (removed == 0 && skippedLines == 0) {
			return 0;
		}
		StringBuilder sb = new StringBuilder();
		for (HistoryEntry e : kept) {
			sb.append(Json.compact(HistoryCodec.toJson(e))).append('\n');
		}
		AtomicFiles.writeString(file, sb.toString());
		entries.clear();
		entries.addAll(kept);
		skippedLines = 0;
		return removed;
	}
}
