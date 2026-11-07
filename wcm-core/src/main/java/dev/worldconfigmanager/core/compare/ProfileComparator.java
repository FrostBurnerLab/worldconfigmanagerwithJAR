package dev.worldconfigmanager.core.compare;

import dev.worldconfigmanager.api.ConfigDefinition;
import dev.worldconfigmanager.api.ConfigKey;
import dev.worldconfigmanager.api.ConfigValue;
import dev.worldconfigmanager.api.ValidationResult;
import dev.worldconfigmanager.core.config.DefinitionLookup;
import dev.worldconfigmanager.core.util.Versions;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.TreeSet;

/** Compares two value sources by configuration key and typed value. */
public final class ProfileComparator {
	private final DefinitionLookup lookup;

	public ProfileComparator(DefinitionLookup lookup) {
		this.lookup = lookup;
	}

	public ComparisonResult compare(ValueSource left, ValueSource right) {
		TreeSet<ConfigKey> keys = new TreeSet<>(left.values().keySet());
		keys.addAll(right.values().keySet());
		List<ComparisonRow> rows = new ArrayList<>(keys.size());
		for (ConfigKey key : keys) {
			rows.add(compareKey(key, left, right));
		}
		return new ComparisonResult(left.label(), right.label(), rows);
	}

	private ComparisonRow compareKey(ConfigKey key, ValueSource left, ValueSource right) {
		ConfigValue l = left.values().get(key);
		ConfigValue r = right.values().get(key);
		Optional<ConfigDefinition> def = lookup.definition(key);
		if (lookup.providerVersion(key.providerId()).isEmpty()) {
			return new ComparisonRow(key, l, r, DiffStatus.UNAVAILABLE, "provider not installed: " + key.providerId());
		}
		if (def.isEmpty()) {
			return new ComparisonRow(key, l, r, DiffStatus.UNAVAILABLE, "setting not available");
		}
		String problem = incompatibility(def.get(), l);
		if (problem == null) {
			problem = incompatibility(def.get(), r);
		}
		if (problem != null) {
			return new ComparisonRow(key, l, r, DiffStatus.INCOMPATIBLE, problem);
		}
		if (l != null && l.equals(r)) {
			return new ComparisonRow(key, l, r, DiffStatus.IDENTICAL, "");
		}
		String lv = left.providerVersions().get(key.providerId());
		String rv = right.providerVersions().get(key.providerId());
		if (lv != null && rv != null && Versions.major(lv) != Versions.major(rv)) {
			return new ComparisonRow(key, l, r, DiffStatus.VERSION_CONFLICT, lv + " vs " + rv);
		}
		if (l == null) {
			return new ComparisonRow(key, null, r, DiffStatus.ONLY_RIGHT, "");
		}
		if (r == null) {
			return new ComparisonRow(key, l, null, DiffStatus.ONLY_LEFT, "");
		}
		return new ComparisonRow(key, l, r, DiffStatus.CHANGED, "");
	}

	private static String incompatibility(ConfigDefinition def, ConfigValue value) {
		if (value == null) {
			return null;
		}
		ValidationResult result = def.validate(value);
		return result.valid() ? null : result.describe();
	}
}
