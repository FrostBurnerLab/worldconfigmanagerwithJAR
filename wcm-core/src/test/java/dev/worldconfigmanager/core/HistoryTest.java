package dev.worldconfigmanager.core;

import dev.worldconfigmanager.api.ConfigCategory;
import dev.worldconfigmanager.api.ConfigDefinition;
import dev.worldconfigmanager.api.ConfigKey;
import dev.worldconfigmanager.api.ConfigValue;
import dev.worldconfigmanager.api.ValueType;
import dev.worldconfigmanager.core.history.HistoryEntry;
import dev.worldconfigmanager.core.history.HistoryFilter;
import dev.worldconfigmanager.core.history.HistoryOperation;
import dev.worldconfigmanager.core.history.HistoryRepository;
import dev.worldconfigmanager.core.history.HistoryResult;
import dev.worldconfigmanager.core.history.HistoryService;
import dev.worldconfigmanager.core.registry.ProviderRegistry;
import dev.worldconfigmanager.core.settings.WcmSettings;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class HistoryTest {
	@TempDir
	Path dir;

	private static HistoryEntry entry(TestSupport.MutableClock clock, String provider, ConfigCategory category, HistoryOperation op) {
		return new HistoryEntry(UUID.randomUUID(), clock.instant(), UUID.randomUUID(), null, provider, "k", category,
			ConfigValue.of(1L), ConfigValue.of(2L), false, op, HistoryResult.SUCCESS, "actor", null, "");
	}

	@Test
	void recordsPersistAndFilter() {
		TestSupport.MutableClock clock = TestSupport.clock();
		Path file = dir.resolve("history.jsonl");
		HistoryService service = new HistoryService(new HistoryRepository(file), WcmSettings::defaults, clock);
		var start = clock.instant();
		service.record(List.of(entry(clock, "a", ConfigCategory.GAMEPLAY, HistoryOperation.SET_VALUE)));
		clock.advanceSeconds(3600);
		service.record(List.of(entry(clock, "b", ConfigCategory.PERFORMANCE, HistoryOperation.APPLY_PROFILE)));

		HistoryService reloaded = new HistoryService(new HistoryRepository(file), WcmSettings::defaults, clock);
		assertEquals(2, reloaded.size());
		assertEquals("b", reloaded.query(HistoryFilter.ALL, 10).getFirst().providerId(), "newest first");
		assertEquals(1, reloaded.query(new HistoryFilter(null, null, null, "a", null, null), 10).size());
		assertEquals(1, reloaded.query(new HistoryFilter(null, null, null, null, ConfigCategory.PERFORMANCE, null), 10).size());
		assertEquals(1, reloaded.query(new HistoryFilter(null, null, null, null, null, HistoryOperation.SET_VALUE), 10).size());
		assertEquals(1, reloaded.query(new HistoryFilter(start.plusSeconds(60), null, null, null, null, null), 10).size());
	}

	@Test
	void tornLinesAreSkipped() throws Exception {
		TestSupport.MutableClock clock = TestSupport.clock();
		Path file = dir.resolve("history.jsonl");
		HistoryService service = new HistoryService(new HistoryRepository(file), WcmSettings::defaults, clock);
		service.record(List.of(entry(clock, "a", ConfigCategory.GAMEPLAY, HistoryOperation.SET_VALUE)));
		Files.writeString(file, "{\"id\":\"trunc", StandardOpenOption.APPEND);
		HistoryRepository repo = new HistoryRepository(file);
		assertEquals(1, repo.size());
		assertEquals(1, repo.skippedLines());
	}

	@Test
	void retentionBoundsHistory() {
		TestSupport.MutableClock clock = TestSupport.clock();
		WcmSettings settings = WcmSettings.defaults().withRetention(20, 0, 100, 30);
		HistoryService service = new HistoryService(new HistoryRepository(dir.resolve("h.jsonl")), () -> settings, clock);
		List<HistoryEntry> batch = new ArrayList<>();
		for (int i = 0; i < 250; i++) {
			batch.add(entry(clock, "a", ConfigCategory.GAMEPLAY, HistoryOperation.SET_VALUE));
		}
		service.record(batch);
		assertEquals(100, service.size(), "pruned to max entries once slack exceeded");
		clock.advanceSeconds(31L * 24 * 3600);
		service.record(List.of(entry(clock, "a", ConfigCategory.GAMEPLAY, HistoryOperation.SET_VALUE)));
		service.prune();
		assertEquals(1, service.size(), "entries older than max age are removed");
	}

	@Test
	void sensitiveValuesAreMasked() throws Exception {
		ProviderRegistry registry = new ProviderRegistry();
		TestProvider p = new TestProvider("s", "1.0.0", true);
		p.define(ConfigDefinition.builder("token", ValueType.STRING, ConfigValue.of("")).sensitive(true).build());
		registry.register(p);
		WorldSession session = TestSupport.session(dir, registry, TestSupport.clock());
		session.applyChanges(Map.of(new ConfigKey("s", "token"), ConfigValue.of("secret-123")), "t", false);
		HistoryEntry e = session.history().query(HistoryFilter.ALL, 1).getFirst();
		assertTrue(e.masked());
		assertNull(e.newValue());
		assertFalse(Files.readString(dir.resolve("world/history.jsonl")).contains("secret-123"));
	}
}
