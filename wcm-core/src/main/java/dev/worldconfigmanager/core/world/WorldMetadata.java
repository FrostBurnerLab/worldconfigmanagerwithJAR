package dev.worldconfigmanager.core.world;

import java.time.Instant;
import java.util.Objects;
import java.util.UUID;

/**
 * WCM data stored inside a world folder.
 *
 * @param worldId             persistent world identity
 * @param createdAt           when WCM first saw the world
 * @param associatedProfileId profile associated with this world (may be {@code null})
 * @param activeProfileId     profile most recently applied (may be {@code null})
 * @param lastAppliedAt       when a profile was last applied (may be {@code null})
 */
public record WorldMetadata(UUID worldId, Instant createdAt, UUID associatedProfileId, UUID activeProfileId,
	Instant lastAppliedAt) {
	public WorldMetadata {
		Objects.requireNonNull(worldId, "worldId");
		Objects.requireNonNull(createdAt, "createdAt");
	}

	public WorldMetadata withAssociatedProfile(UUID profileId) {
		return new WorldMetadata(worldId, createdAt, profileId, activeProfileId, lastAppliedAt);
	}

	public WorldMetadata withActiveProfile(UUID profileId, Instant at) {
		return new WorldMetadata(worldId, createdAt, associatedProfileId, profileId, at);
	}
}
