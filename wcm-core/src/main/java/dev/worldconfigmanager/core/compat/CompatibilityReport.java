package dev.worldconfigmanager.core.compat;

import java.util.List;

/** All compatibility findings for a profile or package. */
public record CompatibilityReport(List<CompatibilityIssue> issues) {
	public CompatibilityReport {
		issues = List.copyOf(issues);
	}

	public boolean hasErrors() {
		return issues.stream().anyMatch(i -> i.severity() == CompatibilityIssue.Severity.ERROR);
	}

	public long count(CompatibilityIssue.Severity severity) {
		return issues.stream().filter(i -> i.severity() == severity).count();
	}

	/** @return overall status: {@code compatible}, {@code warnings} or {@code errors} */
	public String status() {
		if (hasErrors()) {
			return "errors";
		}
		return count(CompatibilityIssue.Severity.WARNING) > 0 ? "warnings" : "compatible";
	}
}
