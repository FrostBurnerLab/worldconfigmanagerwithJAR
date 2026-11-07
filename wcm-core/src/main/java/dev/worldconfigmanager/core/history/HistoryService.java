package dev.worldconfigmanager.core.history;

import dev.worldconfigmanager.core.settings.WcmSettings;
import dev.worldconfigmanager.core.storage.StorageException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.function.Supplier;

/** Records and queries configuration history and enforces its retention policy. */
public final class HistoryService {
	private static final Logger LOGGER = LoggerFactory.getLogger("WorldConfigManager");
	/** Prune at most once every this many appended entries, to avoid rewriting the file on every change. */
	private static final int PRUNE_SLACK = 100;

	private final HistoryRepository repository;
	private final Supplier<WcmSettings> settings;
	private final Clock clock;

	public HistoryService(HistoryRepository repository, Supplier<WcmSettings> settings, Clock clock) {
		this.repository = repository;
		this.settings = settings;
		this.clock = clock;
	}

	/**
	 * Records entries. A failure to record history is logged but does not undo the already applied configuration.
	 *
	 * @return {@code true} if the entries were persisted
	 */
	public boolean record(List<HistoryEntry> entries) {
		try {
			repository.append(entries);
		} catch (StorageException e) {
			LOGGER.error("Could not record {} history entr(y/ies): {}", entries.size(), e.getMessage());
			return false;
		}
		if (repository.size() > settings.get().maxHistoryEntries() + PRUNE_SLACK) {
			prune();
		}
		return true;
	}

	public List<HistoryEntry> query(HistoryFilter filter, int limit) {
		return repository.query(filter::matches, Math.max(1, Math.min(limit, 1000)));
	}

	public int size() {
		return repository.size();
	}

	public int prune() {
		WcmSettings s = settings.get();
		Instant cutoff = s.maxHistoryAgeDays() > 0 ? clock.instant().minus(Duration.ofDays(s.maxHistoryAgeDays())) : null;
		try {
			return repository.prune(s.maxHistoryEntries(), cutoff);
		} catch (StorageException e) {
			LOGGER.error("Could not prune history: {}", e.getMessage());
			return 0;
		}
	}
}
