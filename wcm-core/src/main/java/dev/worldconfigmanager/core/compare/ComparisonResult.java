package dev.worldconfigmanager.core.compare;

import java.util.EnumMap;
import java.util.List;
import java.util.Map;

/** Full comparison of two value sources. */
public record ComparisonResult(String leftLabel, String rightLabel, List<ComparisonRow> rows) {
	public ComparisonResult {
		rows = List.copyOf(rows);
	}

	public Map<DiffStatus, Integer> counts() {
		Map<DiffStatus, Integer> counts = new EnumMap<>(DiffStatus.class);
		for (DiffStatus s : DiffStatus.values()) {
			counts.put(s, 0);
		}
		rows.forEach(r -> counts.merge(r.status(), 1, Integer::sum));
		return counts;
	}

	public List<ComparisonRow> differencesOnly() {
		return rows.stream().filter(r -> r.status().isDifference()).toList();
	}
}
