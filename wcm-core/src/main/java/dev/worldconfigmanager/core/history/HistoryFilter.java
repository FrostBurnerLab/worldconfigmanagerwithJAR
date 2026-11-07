package dev.worldconfigmanager.core.history;

import dev.worldconfigmanager.api.ConfigCategory;

import java.time.Instant;
import java.util.UUID;

/**
 * Filter for history queries; {@code null} fields match everything.
 */
public record HistoryFilter(Instant from, Instant to, UUID profileId, String providerId, ConfigCategory category,
	HistoryOperation operation) {
	public static final HistoryFilter ALL = new HistoryFilter(null, null, null, null, null, null);

	public boolean matches(HistoryEntry e) {
		return (from == null || !e.timestamp().isBefore(from))
			&& (to == null || !e.timestamp().isAfter(to))
			&& (profileId == null || profileId.equals(e.profileId()))
			&& (providerId == null || providerId.equals(e.providerId()))
			&& (category == null || category == e.category())
			&& (operation == null || operation == e.operation());
	}
}
