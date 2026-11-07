package dev.worldconfigmanager.core;

import dev.worldconfigmanager.api.ConfigKey;
import dev.worldconfigmanager.api.ConfigValue;
import dev.worldconfigmanager.core.apply.ApplyOutcome;
import dev.worldconfigmanager.core.apply.ApplyStatus;
import dev.worldconfigmanager.core.backup.Backup;
import dev.worldconfigmanager.core.backup.BackupReason;
import dev.worldconfigmanager.core.backup.BackupSummary;
import dev.worldconfigmanager.core.history.HistoryEntry;
import dev.worldconfigmanager.core.history.HistoryFilter;
import dev.worldconfigmanager.core.history.HistoryOperation;
import dev.worldconfigmanager.core.history.HistoryResult;
import dev.worldconfigmanager.core.profile.Profile;
import dev.worldconfigmanager.core.registry.ProviderRegistry;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ApplyAndBackupTest {
	@TempDir
	Path dir;
	TestSupport.MutableClock clock;
	ProviderRegistry registry;
	TestProvider a;
	TestProvider b;
	WorldSession session;

	@BeforeEach
	void setUp() throws Exception {
		clock = TestSupport.clock();
		registry = new ProviderRegistry();
		a = TestProvider.standard("a");
		b = TestProvider.standard("b");
		registry.register(a);
		registry.register(b);
		session = TestSupport.session(dir, registry, clock);
	}

	private Profile profile(Map<ConfigKey, ConfigValue> entries) throws Exception {
		Profile p = session.profiles().create("P" + System.nanoTime(), "");
		return session.profiles().updateEntries(p.id(), entries, Map.of(), "26.3");
	}

	@Test
	void applyingProfileCreatesBackupAndHistory() throws Exception {
		Profile p = profile(Map.of(new ConfigKey("a", "flag"), ConfigValue.of(true), new ConfigKey("b", "count"), ConfigValue.of(50L),
			new ConfigKey("a", "mode"), ConfigValue.ofEnum("hard")));
		ApplyOutcome outcome = session.applyProfile(p.id(), "tester");
		assertEquals(ApplyStatus.SUCCESS, outcome.status());
		assertEquals(ConfigValue.of(true), a.values.get("flag"));
		assertEquals(ConfigValue.of(50L), b.values.get("count"));
		assertEquals(3, outcome.applied().size());
		assertTrue(outcome.restartRequired().contains(new ConfigKey("a", "mode")));
		assertNotNull(outcome.backupId());

		Backup backup = session.backups().load(outcome.backupId());
		assertEquals(BackupReason.BEFORE_PROFILE_APPLY, backup.reason());
		assertEquals(ConfigValue.of(false), backup.values().get(new ConfigKey("a", "flag")));

		List<HistoryEntry> history = session.history().query(HistoryFilter.ALL, 10);
		assertEquals(3, history.size());
		assertTrue(history.stream().allMatch(h -> h.result() == HistoryResult.SUCCESS && h.operation() == HistoryOperation.APPLY_PROFILE));
		assertTrue(history.stream().allMatch(h -> outcome.backupId().equals(h.backupId()) && "tester".equals(h.actor())));
		assertEquals(p.id(), session.metadata().activeProfileId());
	}

	@Test
	void invalidValueAbortsBeforeAnyChangeOrBackup() throws Exception {
		Profile p = profile(Map.of(new ConfigKey("a", "flag"), ConfigValue.of(true), new ConfigKey("a", "count"), ConfigValue.of(500L)));
		ApplyOutcome outcome = session.applyProfile(p.id(), "t");
		assertEquals(ApplyStatus.VALIDATION_FAILED, outcome.status());
		assertEquals(1, outcome.invalid().size());
		assertEquals(ConfigValue.of(false), a.values.get("flag"));
		assertEquals(0, a.applyCalls);
		assertTrue(session.backups().list().isEmpty());
		assertNull(session.metadata().activeProfileId());
	}

	@Test
	void providerFailureRollsBackAllTouchedProviders() throws Exception {
		b.failOnApply.add("count");
		Profile p = profile(Map.of(new ConfigKey("a", "flag"), ConfigValue.of(true), new ConfigKey("b", "flag"), ConfigValue.of(true),
			new ConfigKey("b", "count"), ConfigValue.of(77L)));
		ApplyOutcome outcome = session.applyProfile(p.id(), "t");
		assertEquals(ApplyStatus.ROLLED_BACK, outcome.status());
		assertTrue(outcome.failures().containsKey(new ConfigKey("b", "count")));
		assertTrue(outcome.applied().isEmpty());
		assertEquals(ConfigValue.of(false), a.values.get("flag"), "provider a must be restored");
		assertEquals(ConfigValue.of(false), b.values.get("flag"), "partially applied provider b must be restored");
		List<HistoryEntry> history = session.history().query(HistoryFilter.ALL, 10);
		assertTrue(history.stream().anyMatch(h -> h.result() == HistoryResult.ROLLED_BACK));
		assertTrue(history.stream().anyMatch(h -> h.result() == HistoryResult.FAILED));
	}

	@Test
	void throwingProviderIsRolledBackAndRollbackFailureIsReported() throws Exception {
		b.throwOnApply.add("flag");
		a.failRestore = true;
		Profile p = profile(Map.of(new ConfigKey("a", "flag"), ConfigValue.of(true), new ConfigKey("b", "flag"), ConfigValue.of(true)));
		ApplyOutcome outcome = session.applyProfile(p.id(), "t");
		assertEquals(ApplyStatus.ROLLBACK_INCOMPLETE, outcome.status());
		assertTrue(outcome.rollbackFailures().containsKey("a"));
		assertNotNull(outcome.backupId(), "a backup must exist for manual recovery");
	}

	@Test
	void unavailableEntriesAreSkippedAndNoChangeMeansNoBackup() throws Exception {
		Profile p = profile(Map.of(new ConfigKey("ghost", "x"), ConfigValue.of(true), new ConfigKey("a", "count"), ConfigValue.of(10L)));
		ApplyOutcome outcome = session.applyProfile(p.id(), "t");
		assertEquals(ApplyStatus.NO_CHANGES, outcome.status());
		assertEquals(List.of(new ConfigKey("ghost", "x")), outcome.skipped());
		assertTrue(session.backups().list().isEmpty());
		assertEquals(2, session.profiles().require(p.id()).entries().size(), "unavailable entries stay in the profile");
	}

	@Test
	void createListAndRestoreBackup() throws Exception {
		a.values.put("count", ConfigValue.of(5L));
		Backup manual = session.createManualBackup("before experiments");
		assertEquals(1, session.backups().list().size());
		assertEquals("before experiments", session.backups().list().getFirst().label());

		session.applyChanges(Map.of(new ConfigKey("a", "count"), ConfigValue.of(90L)), "t", false);
		assertEquals(ConfigValue.of(90L), a.values.get("count"));

		ApplyOutcome restore = session.restoreBackup(manual.id(), "t");
		assertEquals(ApplyStatus.SUCCESS, restore.status());
		assertEquals(ConfigValue.of(5L), a.values.get("count"));
		List<BackupSummary> list = session.backups().list();
		assertEquals(3, list.size(), "manual + before changes + before restore");
		assertTrue(list.stream().anyMatch(s -> s.reason() == BackupReason.BEFORE_RESTORE));
		assertEquals(HistoryOperation.RESTORE_BACKUP, session.history().query(HistoryFilter.ALL, 1).getFirst().operation());
		assertNull(session.metadata().activeProfileId(), "a restore means no profile is in effect any more");
	}

	@Test
	void corruptBackupIsListedAndRefused() throws Exception {
		Backup backup = session.createManualBackup("x");
		Path file = dir.resolve("world/backups/" + backup.id() + ".json");
		Files.writeString(file, "{\"schemaVersion\":1, truncated");
		WorldSession reopened = TestSupport.session(dir, registry, clock);
		BackupSummary summary = reopened.backups().list().getFirst();
		assertTrue(summary.corrupt());
		OperationException e = assertThrows(OperationException.class, () -> reopened.restoreBackup(backup.id(), "t"));
		assertEquals("wcm.error.backup_corrupt", e.messageKey());
		assertEquals(1, reopened.dashboard().corruptBackups());
	}

	@Test
	void retentionKeepsNewestBackups() throws Exception {
		session.updateSettings(session.settings().withRetention(3, 0, 5000, 90));
		for (int i = 0; i < 6; i++) {
			clock.advanceSeconds(10);
			session.createManualBackup("b" + i);
		}
		List<BackupSummary> list = session.backups().list();
		assertEquals(3, list.size());
		assertEquals("b5", list.getFirst().label());
		assertFalse(list.stream().anyMatch(s -> s.label().equals("b0")));
	}

	@Test
	void backupsNeverContainOtherBackups() throws Exception {
		Backup first = session.createManualBackup("one");
		Backup second = session.createManualBackup("two");
		assertEquals(first.snapshots().keySet(), second.snapshots().keySet());
		assertEquals(first.valueCount(), second.valueCount());
	}

	@Test
	void worldIdentityIsPersistentAndIndependentOfName() throws Exception {
		var id = session.identity().worldId();
		WorldSession again = TestSupport.session(dir, registry, clock);
		assertEquals(id, again.identity().worldId());
	}

	@Test
	void defaultProfileIsAssociatedAndOptionallyAppliedToNewWorlds() throws Exception {
		Profile p = profile(Map.of(new ConfigKey("a", "flag"), ConfigValue.of(true)));
		session.profiles().setDefault(p.id());
		session.updateSettings(session.settings().withAutoApplyDefault(true));
		WorldSession fresh = new WorldSession(dir.resolve("config"), dir.resolve("world2"),
			new dev.worldconfigmanager.core.world.WorldDescriptor("New", dev.worldconfigmanager.core.world.WorldKind.SINGLEPLAYER,
				"survival", false, "26.3"), registry, TestSupport::serverContext, clock);
		var outcome = fresh.onWorldLoaded();
		assertTrue(outcome.isPresent());
		assertEquals(ApplyStatus.SUCCESS, outcome.get().status());
		assertEquals(p.id(), fresh.metadata().associatedProfileId());
		assertEquals(ConfigValue.of(true), a.values.get("flag"));
		assertTrue(TestSupport.session(dir, registry, clock).onWorldLoaded().isEmpty(), "existing worlds are not touched");
	}
}
