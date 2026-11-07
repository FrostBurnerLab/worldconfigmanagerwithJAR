package dev.worldconfigmanager.core;

import dev.worldconfigmanager.api.ConfigKey;
import dev.worldconfigmanager.api.ConfigValue;
import dev.worldconfigmanager.core.compare.ComparisonResult;
import dev.worldconfigmanager.core.compare.ComparisonRow;
import dev.worldconfigmanager.core.compare.DiffStatus;
import dev.worldconfigmanager.core.compare.ValueSource;
import dev.worldconfigmanager.core.compat.CompatibilityIssue;
import dev.worldconfigmanager.core.compat.CompatibilityReport;
import dev.worldconfigmanager.core.profile.Profile;
import dev.worldconfigmanager.core.registry.ProviderRegistry;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Path;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class CompatibilityTest {
	@TempDir
	Path dir;
	WorldSession session;

	@BeforeEach
	void setUp() throws Exception {
		ProviderRegistry registry = new ProviderRegistry();
		registry.register(TestProvider.standard("p"));
		TestProvider v2 = new TestProvider("v2", "2.1.0", true);
		v2.define(dev.worldconfigmanager.api.ConfigDefinition.builder("x", dev.worldconfigmanager.api.ValueType.BOOLEAN, ConfigValue.of(false)).build());
		registry.register(v2);
		session = TestSupport.session(dir, registry, TestSupport.clock());
	}

	private Profile profile(Map<ConfigKey, ConfigValue> entries, Map<String, String> versions, String mc) throws Exception {
		Profile p = session.profiles().create("C" + System.nanoTime(), "");
		return session.profiles().updateEntries(p.id(), entries, versions, mc);
	}

	private static boolean has(CompatibilityReport report, CompatibilityIssue.Code code) {
		return report.issues().stream().anyMatch(i -> i.code() == code);
	}

	@Test
	void missingModAndMissingSetting() throws Exception {
		CompatibilityReport report = session.compatibility(profile(Map.of(
			new ConfigKey("notinstalled", "a"), ConfigValue.of(true),
			new ConfigKey("notinstalled", "b"), ConfigValue.of(true),
			new ConfigKey("p", "removedSetting"), ConfigValue.of(1L)), Map.of(), "26.3"));
		assertEquals(1, report.issues().stream().filter(i -> i.code() == CompatibilityIssue.Code.MISSING_PROVIDER).count(), "reported once per provider");
		assertTrue(has(report, CompatibilityIssue.Code.MISSING_SETTING));
		assertEquals("warnings", report.status());
	}

	@Test
	void changedValueTypeAndInvalidValue() throws Exception {
		CompatibilityReport report = session.compatibility(profile(Map.of(
			new ConfigKey("p", "flag"), ConfigValue.of(1L),
			new ConfigKey("p", "count"), ConfigValue.of(1000L)), Map.of(), "26.3"));
		assertTrue(has(report, CompatibilityIssue.Code.TYPE_CHANGED));
		assertTrue(has(report, CompatibilityIssue.Code.INVALID_VALUE));
		assertTrue(report.hasErrors());
	}

	@Test
	void incompatibleProviderVersions() throws Exception {
		CompatibilityReport major = session.compatibility(profile(Map.of(new ConfigKey("v2", "x"), ConfigValue.of(true)), Map.of("v2", "1.4.0"), "26.3"));
		assertTrue(has(major, CompatibilityIssue.Code.PROVIDER_MAJOR_CHANGED));
		CompatibilityReport newer = session.compatibility(profile(Map.of(new ConfigKey("v2", "x"), ConfigValue.of(true)), Map.of("v2", "2.5.0"), "26.3"));
		assertTrue(has(newer, CompatibilityIssue.Code.PROVIDER_NEWER_IN_PROFILE));
	}

	@Test
	void restartRequirementAndMinecraftVersion() throws Exception {
		CompatibilityReport report = session.compatibility(profile(Map.of(new ConfigKey("p", "mode"), ConfigValue.ofEnum("hard")), Map.of(), "1.21.11"));
		assertTrue(has(report, CompatibilityIssue.Code.RESTART_REQUIRED));
		assertTrue(has(report, CompatibilityIssue.Code.MINECRAFT_VERSION_DIFFERS));
		assertFalse(report.hasErrors());
	}

	@Test
	void comparisonClassifiesRowsByKeyAndTypedValue() throws Exception {
		Profile left = profile(Map.of(
			new ConfigKey("p", "flag"), ConfigValue.of(true),
			new ConfigKey("p", "count"), ConfigValue.of(10L),
			new ConfigKey("p", "mode"), ConfigValue.ofEnum("easy"),
			new ConfigKey("gone", "z"), ConfigValue.of(1L),
			new ConfigKey("v2", "x"), ConfigValue.of(true)), Map.of("v2", "1.0.0"), "26.3");
		Profile right = profile(Map.of(
			new ConfigKey("p", "flag"), ConfigValue.of(true),
			new ConfigKey("p", "count"), ConfigValue.of(11L),
			new ConfigKey("p", "mode"), ConfigValue.of("easy"),
			new ConfigKey("v2", "x"), ConfigValue.of(false)), Map.of("v2", "2.0.0"), "26.3");
		ComparisonResult result = session.comparator().compare(ValueSource.of(left), ValueSource.of(right));
		Map<ConfigKey, DiffStatus> byKey = new java.util.HashMap<>();
		for (ComparisonRow row : result.rows()) {
			byKey.put(row.key(), row.status());
		}
		assertEquals(DiffStatus.IDENTICAL, byKey.get(new ConfigKey("p", "flag")));
		assertEquals(DiffStatus.CHANGED, byKey.get(new ConfigKey("p", "count")));
		assertEquals(DiffStatus.INCOMPATIBLE, byKey.get(new ConfigKey("p", "mode")), "string vs enum is a type difference");
		assertEquals(DiffStatus.UNAVAILABLE, byKey.get(new ConfigKey("gone", "z")));
		assertEquals(DiffStatus.VERSION_CONFLICT, byKey.get(new ConfigKey("v2", "x")));
		assertEquals(4, result.differencesOnly().size());
	}
}
