package dev.worldconfigmanager.core.compat;

import dev.worldconfigmanager.api.ConfigDefinition;
import dev.worldconfigmanager.api.ConfigKey;
import dev.worldconfigmanager.api.ConfigValue;
import dev.worldconfigmanager.api.ValidationResult;
import dev.worldconfigmanager.core.WcmVersion;
import dev.worldconfigmanager.core.config.DefinitionLookup;
import dev.worldconfigmanager.core.profile.Profile;
import dev.worldconfigmanager.core.util.Versions;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

/**
 * Checks a profile against the installed providers, Minecraft version and WCM version. Unsupported entries are
 * reported but never removed from the profile.
 */
public final class CompatibilityChecker {
	private final DefinitionLookup lookup;
	private final String minecraftVersion;

	public CompatibilityChecker(DefinitionLookup lookup, String minecraftVersion) {
		this.lookup = lookup;
		this.minecraftVersion = minecraftVersion == null ? "" : minecraftVersion;
	}

	public CompatibilityReport check(Profile profile) {
		List<CompatibilityIssue> issues = new ArrayList<>();
		if (Versions.compare(profile.wcmVersion(), WcmVersion.MOD_VERSION) > 0) {
			issues.add(CompatibilityIssue.of(CompatibilityIssue.Code.WCM_NEWER_IN_PROFILE, null, null, profile.wcmVersion(),
				WcmVersion.MOD_VERSION));
		}
		if (!profile.minecraftVersion().isEmpty() && !minecraftVersion.isEmpty()
			&& !profile.minecraftVersion().equals(minecraftVersion)) {
			issues.add(CompatibilityIssue.of(CompatibilityIssue.Code.MINECRAFT_VERSION_DIFFERS, null, null,
				profile.minecraftVersion(), minecraftVersion));
		}
		Set<String> reportedProviders = new HashSet<>();
		for (Map.Entry<ConfigKey, ConfigValue> e : profile.entries().entrySet()) {
			ConfigKey key = e.getKey();
			String providerId = key.providerId();
			Optional<String> installed = lookup.providerVersion(providerId);
			if (installed.isEmpty()) {
				if (reportedProviders.add(providerId)) {
					issues.add(CompatibilityIssue.of(CompatibilityIssue.Code.MISSING_PROVIDER, providerId, null, providerId));
				}
				continue;
			}
			if (reportedProviders.add(providerId)) {
				checkProviderVersion(profile, providerId, installed.get(), issues);
			}
			Optional<ConfigDefinition> def = lookup.definition(key);
			if (def.isEmpty()) {
				issues.add(CompatibilityIssue.of(CompatibilityIssue.Code.MISSING_SETTING, providerId, key.key(), key.key()));
				continue;
			}
			ConfigValue value = e.getValue();
			if (value.type() != def.get().type()) {
				issues.add(CompatibilityIssue.of(CompatibilityIssue.Code.TYPE_CHANGED, providerId, key.key(), key.key(),
					value.type().id(), def.get().type().id()));
				continue;
			}
			ValidationResult validation = def.get().validate(value);
			if (!validation.valid()) {
				issues.add(CompatibilityIssue.of(CompatibilityIssue.Code.INVALID_VALUE, providerId, key.key(), key.key(),
					validation.describe()));
				continue;
			}
			if (def.get().reloadBehavior().requiresRestart()) {
				issues.add(CompatibilityIssue.of(CompatibilityIssue.Code.RESTART_REQUIRED, providerId, key.key(), key.key(),
					def.get().reloadBehavior().name()));
			}
		}
		return new CompatibilityReport(issues);
	}

	private static void checkProviderVersion(Profile profile, String providerId, String installed, List<CompatibilityIssue> issues) {
		String recorded = profile.providerVersions().get(providerId);
		if (recorded == null) {
			return;
		}
		if (Versions.major(recorded) != Versions.major(installed)) {
			issues.add(CompatibilityIssue.of(CompatibilityIssue.Code.PROVIDER_MAJOR_CHANGED, providerId, null, providerId, recorded,
				installed));
		} else if (Versions.compare(recorded, installed) > 0) {
			issues.add(CompatibilityIssue.of(CompatibilityIssue.Code.PROVIDER_NEWER_IN_PROFILE, providerId, null, providerId,
				recorded, installed));
		}
	}
}
