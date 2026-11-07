package dev.worldconfigmanager.core;

import dev.worldconfigmanager.api.ConfigKey;
import dev.worldconfigmanager.api.ConfigValue;
import dev.worldconfigmanager.core.json.Json;
import dev.worldconfigmanager.core.profile.Profile;
import dev.worldconfigmanager.core.profile.ProfileCodec;
import dev.worldconfigmanager.core.registry.ProviderRegistry;
import dev.worldconfigmanager.core.transfer.EntryStatus;
import dev.worldconfigmanager.core.transfer.ImportMode;
import dev.worldconfigmanager.core.transfer.ImportPreview;
import dev.worldconfigmanager.core.transfer.ImportResult;
import dev.worldconfigmanager.core.transfer.PackageException;
import dev.worldconfigmanager.core.transfer.WcmpFormat;
import dev.worldconfigmanager.core.transfer.WcmpPackage;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.ByteArrayOutputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class TransferTest {
	@TempDir
	Path dir;
	TestSupport.MutableClock clock;
	WorldSession source;
	WorldSession target;

	@BeforeEach
	void setUp() throws Exception {
		clock = TestSupport.clock();
		ProviderRegistry registry = new ProviderRegistry();
		registry.register(TestProvider.standard("p"));
		source = TestSupport.session(dir.resolve("src"), registry, clock);
		target = TestSupport.session(dir.resolve("dst"), registry, clock);
	}

	private Profile sample(WorldSession session, String name) throws Exception {
		Profile p = session.profiles().create(name, "exported");
		return session.profiles().updateEntries(p.id(), Map.of(
			new ConfigKey("p", "flag"), ConfigValue.of(true),
			new ConfigKey("p", "count"), ConfigValue.of(20L),
			new ConfigKey("othermod", "speed"), ConfigValue.of(2.5)), Map.of("p", "1.0.0", "othermod", "3.1.0"), "26.3");
	}

	@Test
	void exportAndImportValidProfile() throws Exception {
		Profile p = sample(source, "Hardcore Challenge");
		byte[] data = source.transfer().exportProfiles(List.of(p.id()));
		WcmpPackage pkg = target.transfer().read(data);
		assertEquals(1, pkg.profiles().size());
		ImportPreview preview = target.transfer().preview(pkg, ImportMode.MERGE);
		ImportPreview.ProfilePreview pp = preview.profiles().getFirst();
		assertEquals(ImportPreview.Action.CREATE, pp.action());
		assertEquals(2, pp.count(EntryStatus.NEW));
		assertEquals(1, pp.count(EntryStatus.MISSING_PROVIDER), "unknown provider is flagged, not dropped");

		ImportResult result = target.transfer().commit(pkg, ImportMode.MERGE, false);
		assertEquals(List.of(p.id()), result.created());
		Profile imported = target.profiles().require(p.id());
		assertEquals(p.entries(), imported.entries(), "entries of missing providers are preserved");
	}

	@Test
	void mergeKeepsExistingValuesAndAddsNewOnes() throws Exception {
		Profile p = sample(source, "Shared");
		target.transfer().commit(target.transfer().read(source.transfer().exportProfiles(List.of(p.id()))), ImportMode.MERGE, false);
		clock.advanceSeconds(60);
		Map<ConfigKey, ConfigValue> local = new java.util.HashMap<>(target.profiles().require(p.id()).entries());
		local.put(new ConfigKey("p", "count"), ConfigValue.of(99L));
		local.remove(new ConfigKey("p", "flag"));
		target.profiles().updateEntries(p.id(), local, Map.of(), "26.3");

		WcmpPackage pkg = target.transfer().read(source.transfer().exportProfiles(List.of(p.id())));
		ImportPreview.ProfilePreview pp = target.transfer().preview(pkg, ImportMode.MERGE).profiles().getFirst();
		assertEquals(ImportPreview.Action.MERGE, pp.action());
		assertEquals(1, pp.count(EntryStatus.CONFLICT));
		assertEquals(1, pp.count(EntryStatus.NEW));
		target.transfer().commit(pkg, ImportMode.MERGE, false);
		Profile merged = target.profiles().require(p.id());
		assertEquals(ConfigValue.of(99L), merged.entries().get(new ConfigKey("p", "count")), "existing value wins on merge");
		assertEquals(ConfigValue.of(true), merged.entries().get(new ConfigKey("p", "flag")), "missing entry added");
	}

	@Test
	void replaceOverwritesButRefusesNewerLocalCopy() throws Exception {
		Profile p = sample(source, "Shared");
		byte[] data = source.transfer().exportProfiles(List.of(p.id()));
		target.transfer().commit(target.transfer().read(data), ImportMode.MERGE, false);
		clock.advanceSeconds(60);
		target.profiles().updateEntries(p.id(), Map.of(new ConfigKey("p", "count"), ConfigValue.of(1L)), Map.of(), "26.3");

		WcmpPackage pkg = target.transfer().read(data);
		assertEquals(ImportPreview.Action.BLOCKED_LOCAL_NEWER, target.transfer().preview(pkg, ImportMode.REPLACE).profiles().getFirst().action());
		ImportResult blocked = target.transfer().commit(pkg, ImportMode.REPLACE, false);
		assertEquals(List.of("Shared"), blocked.blocked());
		assertEquals(1, target.profiles().require(p.id()).entries().size(), "newer local data not overwritten");

		ImportResult forced = target.transfer().commit(pkg, ImportMode.REPLACE, true);
		assertEquals(List.of(p.id()), forced.replaced());
		assertEquals(p.entries(), target.profiles().require(p.id()).entries());
	}

	@Test
	void nameConflictsAreResolvedByRenaming() throws Exception {
		target.profiles().create("Hardcore Challenge", "");
		Profile p = sample(source, "Hardcore Challenge");
		WcmpPackage pkg = target.transfer().read(source.transfer().exportProfiles(List.of(p.id())));
		assertEquals("Hardcore Challenge (2)", target.transfer().preview(pkg, ImportMode.MERGE).profiles().getFirst().finalName());
		target.transfer().commit(pkg, ImportMode.MERGE, false);
		assertEquals("Hardcore Challenge (2)", target.profiles().require(p.id()).name());
	}

	@Test
	void exportSelectionAndMultipleProfiles() throws Exception {
		Profile a = sample(source, "A");
		Profile b = sample(source, "B");
		WcmpPackage multi = WcmpFormat.read(source.transfer().exportProfiles(List.of(a.id(), b.id())));
		assertEquals(2, multi.profiles().size());
		WcmpPackage sel = WcmpFormat.read(source.transfer().exportSelection(a.id(), Set.of(new ConfigKey("p", "flag"))));
		assertEquals(1, sel.profiles().getFirst().entries().size());
		assertEquals("selection", sel.manifest().kind());
	}

	private static byte[] zip(Map<String, String> entries) throws Exception {
		ByteArrayOutputStream out = new ByteArrayOutputStream();
		try (ZipOutputStream zip = new ZipOutputStream(out)) {
			for (var e : entries.entrySet()) {
				zip.putNextEntry(new ZipEntry(e.getKey()));
				zip.write(e.getValue().getBytes(StandardCharsets.UTF_8));
				zip.closeEntry();
			}
		}
		return out.toByteArray();
	}

	private static String manifest(int version, String... ids) {
		StringBuilder list = new StringBuilder();
		for (String id : ids) {
			list.append(list.isEmpty() ? "" : ",").append('"').append(id).append('"');
		}
		return "{\"format\":\"wcmp\",\"formatVersion\":" + version + ",\"createdAt\":\"2026-01-01T00:00:00Z\",\"profiles\":[" + list + "]}";
	}

	private static PackageException.Reason rejection(byte[] data) {
		return assertThrows(PackageException.class, () -> WcmpFormat.read(data)).reason();
	}

	@Test
	void invalidPackagesAreRejected() throws Exception {
		assertEquals(PackageException.Reason.NOT_A_PACKAGE, rejection("hello".getBytes(StandardCharsets.UTF_8)));
		assertEquals(PackageException.Reason.NOT_A_PACKAGE, rejection(new byte[0]));
		assertEquals(PackageException.Reason.MISSING_MANIFEST, rejection(zip(Map.of())));
		assertEquals(PackageException.Reason.INVALID_MANIFEST, rejection(zip(Map.of("manifest.json", "{oops"))));
		assertEquals(PackageException.Reason.UNEXPECTED_ENTRY, rejection(zip(Map.of("manifest.json", manifest(1), "run.sh", "rm -rf /"))));
		String id = "5b1d2c4e-0000-4000-8000-000000000009";
		assertEquals(PackageException.Reason.INVALID_MANIFEST, rejection(zip(Map.of("manifest.json", manifest(1, id)))));
		assertEquals(PackageException.Reason.INVALID_PROFILE,
			rejection(zip(Map.of("manifest.json", manifest(1, id), "profiles/" + id + ".json", "{\"schemaVersion\":2}"))));
		byte[] truncated = java.util.Arrays.copyOf(source.transfer().exportValues("x", "", Map.of(new ConfigKey("p", "flag"), ConfigValue.of(true)), Map.of(), "profiles"), 60);
		PackageException.Reason r = rejection(truncated);
		assertTrue(r == PackageException.Reason.CORRUPT || r == PackageException.Reason.MISSING_MANIFEST, r.name());
	}

	@Test
	void pathTraversalIsRejected() throws Exception {
		assertEquals(PackageException.Reason.UNSAFE_PATH, rejection(zip(Map.of("manifest.json", manifest(1), "../evil.json", "{}"))));
		assertEquals(PackageException.Reason.UNSAFE_PATH, rejection(zip(Map.of("/etc/passwd", "x"))));
		assertEquals(PackageException.Reason.UNSAFE_PATH, rejection(zip(Map.of("profiles\\..\\x.json", "x"))));
	}

	@Test
	void oversizedPackagesAreRejected() throws Exception {
		assertEquals(PackageException.Reason.TOO_LARGE, rejection(new byte[WcmpFormat.MAX_PACKAGE_BYTES + 1]));
		String id = "5b1d2c4e-0000-4000-8000-000000000009";
		// A highly compressible entry larger than the per-entry limit (zip bomb style).
		String bomb = " ".repeat(WcmpFormat.MAX_ENTRY_BYTES + 10);
		assertEquals(PackageException.Reason.TOO_LARGE, rejection(zip(Map.of("manifest.json", manifest(1, id), "profiles/" + id + ".json", bomb))));
	}

	@Test
	void unsupportedVersionAndChecksumMismatch() throws Exception {
		assertEquals(PackageException.Reason.UNSUPPORTED_VERSION, rejection(zip(Map.of("manifest.json", manifest(99)))));
		Profile p = Profile.create("C", "", Instant.parse("2026-01-01T00:00:00Z"));
		String profileJson = Json.pretty(ProfileCodec.toJson(p));
		String m = "{\"format\":\"wcmp\",\"formatVersion\":1,\"createdAt\":\"2026-01-01T00:00:00Z\",\"profiles\":[\"" + p.id()
			+ "\"],\"checksums\":{\"profiles/" + p.id() + ".json\":\"0000\"}}";
		assertEquals(PackageException.Reason.CHECKSUM_MISMATCH, rejection(zip(Map.of("manifest.json", m, "profiles/" + p.id() + ".json", profileJson))));
	}

	@Test
	void exportIsDeterministicForSameInput() throws Exception {
		Profile p = sample(source, "Det");
		assertArrayEquals(source.transfer().exportProfiles(List.of(p.id())), source.transfer().exportProfiles(List.of(p.id())));
	}
}
