package dev.worldconfigmanager.core.apply;

import java.util.List;

/** Preview of the effect of applying a set of values. */
public record ApplyPlan(List<PlannedChange> entries) {
	public ApplyPlan {
		entries = List.copyOf(entries);
	}

	public List<PlannedChange> withStatus(ChangeStatus status) {
		return entries.stream().filter(e -> e.status() == status).toList();
	}

	public long count(ChangeStatus status) {
		return entries.stream().filter(e -> e.status() == status).count();
	}

	public boolean hasInvalid() {
		return count(ChangeStatus.INVALID) > 0;
	}

	public boolean hasChanges() {
		return count(ChangeStatus.CHANGE) > 0;
	}
}
