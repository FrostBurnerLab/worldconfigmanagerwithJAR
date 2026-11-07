package dev.worldconfigmanager.core;

import dev.worldconfigmanager.core.storage.AtomicFiles;
import dev.worldconfigmanager.core.storage.SafePaths;
import dev.worldconfigmanager.core.storage.StorageException;
import dev.worldconfigmanager.core.util.Versions;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Properties;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class StorageTest {
	@TempDir
	Path dir;

	@Test
	void atomicWriteReplacesWithoutLeavingTempFiles() throws Exception {
		Path target = dir.resolve("a.json");
		AtomicFiles.writeString(target, "one");
		AtomicFiles.writeString(target, "two");
		assertEquals("two", Files.readString(target));
		try (var files = Files.list(dir)) {
			assertEquals(1, files.count());
		}
	}

	@Test
	void failedWriteDoesNotClaimSuccess() throws Exception {
		Path blocker = dir.resolve("file");
		Files.writeString(blocker, "x");
		assertThrows(StorageException.class, () -> AtomicFiles.writeString(blocker.resolve("child.json"), "data"));
	}

	@Test
	void safePathsRejectTraversal() throws Exception {
		assertEquals(dir.resolve("ok.json").toAbsolutePath(), SafePaths.resolveChild(dir, "ok.json"));
		for (String bad : new String[] {"../x", "..", "a/b", "a\\b", "/etc/passwd", "", ".hidden", "x..y"}) {
			assertThrows(StorageException.class, () -> SafePaths.resolveChild(dir, bad), bad);
		}
	}

	@Test
	void versionComparison() {
		assertTrue(Versions.compare("1.10.0", "1.9.9") > 0);
		assertEquals(0, Versions.compare("26.3", "26.3.0"));
		assertTrue(Versions.compare("0.1.0", "0.2.0-beta") < 0);
		assertEquals(2, Versions.major("2.1.0+build"));
	}

	@Test
	void modVersionMatchesGradleProperties() throws Exception {
		Properties props = new Properties();
		Path file = Path.of("..", "gradle.properties");
		try (var in = Files.newInputStream(file)) {
			props.load(in);
		}
		assertEquals(props.getProperty("mod_version"), WcmVersion.MOD_VERSION);
	}
}
