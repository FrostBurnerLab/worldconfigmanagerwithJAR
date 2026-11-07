package dev.worldconfigmanager.core.transfer;

import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Package manifest ({@code manifest.json}).
 *
 * @param formatVersion    package format version
 * @param wcmVersion       WCM version that wrote the package
 * @param minecraftVersion Minecraft version at export time
 * @param createdAt        export time
 * @param kind             content kind: {@code profiles}, {@code selection}, {@code world-state} or {@code backup}
 * @param profiles         ids of contained profiles, in order
 * @param checksums        optional SHA-256 checksums by entry name
 */
public record WcmpManifest(int formatVersion, String wcmVersion, String minecraftVersion, Instant createdAt, String kind,
	List<UUID> profiles, Map<String, String> checksums) {
	public WcmpManifest {
		profiles = List.copyOf(profiles);
		checksums = Map.copyOf(checksums);
	}
}
