package dev.worldconfigmanager.core;

import com.google.gson.JsonObject;
import dev.worldconfigmanager.api.ConfigKey;
import dev.worldconfigmanager.api.ConfigValue;
import dev.worldconfigmanager.core.compat.SchemaTooNewException;
import dev.worldconfigmanager.core.json.Json;
import dev.worldconfigmanager.core.json.JsonFormatException;
import dev.worldconfigmanager.core.profile.JsonProfileRepository;
import dev.worldconfigmanager.core.profile.Profile;
import dev.worldconfigmanager.core.profile.ProfileCodec;
import dev.worldconfigmanager.core.profile.ProfileService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ProfileTest {
	@TempDir
	Path dir;
	TestSupport.MutableClock clock;
	ProfileService service;

	@BeforeEach
	void setUp() {
		clock = TestSupport.clock();
		service = new ProfileService(new JsonProfileRepository(dir), clock);
	}

	@Test
	void createProfilePersistsAcrossReload() throws Exception {
		Profile created = service.create("Survival Default", "Standard rules");
		assertEquals("Survival Default", created.name());
		assertTrue(Files.exists(dir.resolve(created.id() + ".json")));

		ProfileService reloaded = new ProfileService(new JsonProfileRepository(dir), clock);
		Profile loaded = reloaded.require(created.id());
		assertEquals(created, loaded);
	}

	@Test
	void renameUpdatesTimestampAndEnforcesUniqueNames() throws Exception {
		Profile a = service.create("Alpha", "");
		service.create("Beta", "");
		clock.advanceSeconds(60);
		Profile renamed = service.rename(a.id(), "Gamma");
		assertEquals("Gamma", renamed.name());
		assertTrue(renamed.modifiedAt().isAfter(a.modifiedAt()));
		assertEquals(a.createdAt(), renamed.createdAt());
		OperationException e = assertThrows(OperationException.class, () -> service.rename(a.id(), "beta"));
		assertEquals("wcm.error.name_taken", e.messageKey());
	}

	@Test
	void duplicateCreatesNewIdAndUniqueName() throws Exception {
		Profile a = service.create("Creative Builder", "desc");
		Profile withEntries = service.updateEntries(a.id(), Map.of(new ConfigKey("p", "k"), ConfigValue.of(true)), Map.of("p", "1.0"), "26.3");
		Profile copy = service.duplicate(a.id());
		Profile copy2 = service.duplicate(a.id());
		assertNotEquals(a.id(), copy.id());
		assertEquals("Creative Builder (copy)", copy.name());
		assertEquals("Creative Builder (copy 2)", copy2.name());
		assertEquals(withEntries.entries(), copy.entries());
		assertEquals(3, service.list().size());
	}

	@Test
	void deleteRemovesFileAndClearsDefault() throws Exception {
		Profile a = service.create("Temp", "");
		service.setDefault(a.id());
		assertEquals(a.id(), service.defaultProfileId().orElseThrow());
		service.delete(a.id());
		assertTrue(service.find(a.id()).isEmpty());
		assertFalse(Files.exists(dir.resolve(a.id() + ".json")));
		assertTrue(service.defaultProfileId().isEmpty());
		assertThrows(OperationException.class, () -> service.delete(a.id()));
	}

	@Test
	void invalidNamesAreRejected() {
		assertEquals("wcm.error.invalid_name", assertThrows(OperationException.class, () -> service.create("   ", "")).messageKey());
		assertThrows(OperationException.class, () -> service.create("x".repeat(65), ""));
		assertThrows(OperationException.class, () -> service.create("bad\u0000name", ""));
	}

	@Test
	void serializationRoundTripPreservesTypedValues() throws Exception {
		Profile p = Profile.create("Typed", "d", clock.instant()).withEntries(Map.of(
			new ConfigKey("a", "bool"), ConfigValue.of(true),
			new ConfigKey("a", "int"), ConfigValue.of(42L),
			new ConfigKey("a", "dec"), ConfigValue.of(1.5),
			new ConfigKey("a", "str"), ConfigValue.of("hello"),
			new ConfigKey("a", "enum"), ConfigValue.ofEnum("HARD"),
			new ConfigKey("a", "list"), new ConfigValue.ListValue(List.of(ConfigValue.of(1L), ConfigValue.of(2L))),
			new ConfigKey("a", "struct"), new ConfigValue.StructValue(Map.of("x", ConfigValue.of("y")))),
			Map.of("a", "2.0.0"), "26.3", clock.instant()).withTags(Set.of("pvp", "hard"), clock.instant());
		Profile back = ProfileCodec.fromJson(Json.parseObject(Json.pretty(ProfileCodec.toJson(p))));
		assertEquals(p, back);
		// Integer 1 and decimal 1.0 must stay distinct types.
		assertNotEquals(ConfigValue.of(1L), ConfigValue.of(1.0));
	}

	@Test
	void invalidProfilesAreRejected() throws Exception {
		assertThrows(JsonFormatException.class, () -> ProfileCodec.fromJson(Json.parseObject("{\"schemaVersion\":2}")));
		assertThrows(JsonFormatException.class, () -> Json.parseObject("{not json"));
		JsonObject good = ProfileCodec.toJson(Profile.create("Ok", "", clock.instant()));
		JsonObject badId = good.deepCopy();
		badId.addProperty("id", "../../etc");
		assertThrows(JsonFormatException.class, () -> ProfileCodec.fromJson(badId));
		JsonObject badValue = Json.parseObject("""
			{"schemaVersion":2,"id":"5b1d2c4e-0000-4000-8000-000000000001","name":"X","createdAt":"2026-01-01T00:00:00Z",
			 "modifiedAt":"2026-01-01T00:00:00Z","entries":[{"provider":"p","key":"k","value":{"type":"integer","value":"seven"}}]}
			""".replace("\n", "").replace("\t", ""));
		assertThrows(JsonFormatException.class, () -> ProfileCodec.fromJson(badValue));
		JsonObject duplicate = Json.parseObject(("{\"schemaVersion\":2,\"id\":\"5b1d2c4e-0000-4000-8000-000000000001\",\"name\":\"X\","
			+ "\"createdAt\":\"2026-01-01T00:00:00Z\",\"modifiedAt\":\"2026-01-01T00:00:00Z\",\"entries\":["
			+ "{\"provider\":\"p\",\"key\":\"k\",\"value\":{\"type\":\"boolean\",\"value\":true}},"
			+ "{\"provider\":\"p\",\"key\":\"k\",\"value\":{\"type\":\"boolean\",\"value\":false}}]}"));
		assertThrows(JsonFormatException.class, () -> ProfileCodec.fromJson(duplicate));
	}

	@Test
	void schemaV1IsMigratedWithInferredTypes() throws Exception {
		JsonObject v1 = Json.parseObject("""
			{"schema":1,"id":"5b1d2c4e-0000-4000-8000-000000000002","name":"Legacy","created":"2025-06-01T00:00:00Z",
			 "settings":[{"provider":"minecraft:gamerules","key":"minecraft:keep_inventory","value":true},
			             {"provider":"minecraft:gamerules","key":"minecraft:random_tick_speed","value":3},
			             {"provider":"x","key":"ratio","value":0.5},{"provider":"x","key":"name","value":"abc"}]}
			""");
		Profile p = ProfileCodec.fromJson(v1);
		assertEquals("Legacy", p.name());
		assertEquals(ConfigValue.of(true), p.entries().get(new ConfigKey("minecraft:gamerules", "minecraft:keep_inventory")));
		assertEquals(ConfigValue.of(3L), p.entries().get(new ConfigKey("minecraft:gamerules", "minecraft:random_tick_speed")));
		assertEquals(ConfigValue.of(0.5), p.entries().get(new ConfigKey("x", "ratio")));
		assertEquals(ConfigValue.of("abc"), p.entries().get(new ConfigKey("x", "name")));
		assertEquals(2, ProfileCodec.toJson(p).get("schemaVersion").getAsInt());
	}

	@Test
	void newerSchemaIsRefusedNotDowngraded() {
		JsonObject json = ProfileCodec.toJson(Profile.create("Future", "", clock.instant()));
		json.addProperty("schemaVersion", 99);
		SchemaTooNewException e = assertThrows(SchemaTooNewException.class, () -> ProfileCodec.fromJson(json));
		assertEquals(99, e.found());
	}

	@Test
	void corruptProfileFileIsQuarantinedAndReported() throws Exception {
		Profile ok = service.create("Fine", "");
		Path bad = dir.resolve("5b1d2c4e-0000-4000-8000-000000000003.json");
		Files.writeString(bad, "{ broken");
		JsonProfileRepository repo = new JsonProfileRepository(dir);
		assertEquals(1, repo.findAll().size());
		assertEquals(ok.id(), repo.findAll().getFirst().id());
		assertEquals(1, repo.loadProblems().size());
		assertFalse(Files.exists(bad));
		try (var files = Files.list(dir)) {
			assertTrue(files.anyMatch(f -> f.getFileName().toString().contains(".corrupt-")));
		}
	}
}
