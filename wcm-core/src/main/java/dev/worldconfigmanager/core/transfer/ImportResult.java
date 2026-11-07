package dev.worldconfigmanager.core.transfer;

import java.util.List;
import java.util.UUID;

/** Outcome of committing an import. */
public record ImportResult(List<UUID> created, List<UUID> merged, List<UUID> replaced, List<String> blocked) {
	public ImportResult {
		created = List.copyOf(created);
		merged = List.copyOf(merged);
		replaced = List.copyOf(replaced);
		blocked = List.copyOf(blocked);
	}

	public int total() {
		return created.size() + merged.size() + replaced.size();
	}
}
