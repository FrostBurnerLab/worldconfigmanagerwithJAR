package dev.worldconfigmanager.core.world;

import com.google.gson.JsonObject;
import dev.worldconfigmanager.core.json.Json;
import dev.worldconfigmanager.core.json.JsonFormatException;
import dev.worldconfigmanager.core.storage.AtomicFiles;
import dev.worldconfigmanager.core.storage.StorageException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Clock;
import java.util.UUID;

/** Loads or creates {@code world.json}, the persistent identity record inside a world's WCM folder. */
public final class WorldMetadataStore {
	private static final Logger LOGGER = LoggerFactory.getLogger("WorldConfigManager");
	private static final int SCHEMA = 1;

	private final Path file;
	private final Clock clock;
	private WorldMetadata current;
	private boolean created;

	public WorldMetadataStore(Path file, Clock clock) throws StorageException {
		this.file = file;
		this.clock = clock;
		this.current = loadOrCreate();
	}

	private WorldMetadata loadOrCreate() throws StorageException {
		if (Files.exists(file)) {
			try {
				return parse(Json.parseObject(AtomicFiles.readString(file)));
			} catch (JsonFormatException | StorageException e) {
				Path moved = AtomicFiles.quarantine(file);
				LOGGER.error("World metadata was unreadable and has been moved to {}; a new world identity is generated: {}",
					moved.getFileName(), e.getMessage());
			}
		}
		WorldMetadata fresh = new WorldMetadata(UUID.randomUUID(), clock.instant(), null, null, null);
		write(fresh);
		created = true;
		LOGGER.info("Assigned new persistent world id {}", fresh.worldId());
		return fresh;
	}

	private static WorldMetadata parse(JsonObject json) throws JsonFormatException {
		long schema = Json.integer(json, "schemaVersion");
		if (schema > SCHEMA) {
			throw new JsonFormatException("World metadata schema " + schema + " is newer than supported");
		}
		String associated = Json.optString(json, "associatedProfile", null);
		String active = Json.optString(json, "activeProfile", null);
		String applied = Json.optString(json, "lastAppliedAt", null);
		try {
			return new WorldMetadata(Json.uuid(json, "worldId"), Json.instant(json, "createdAt"),
				associated == null ? null : UUID.fromString(associated), active == null ? null : UUID.fromString(active),
				applied == null ? null : java.time.Instant.parse(applied));
		} catch (IllegalArgumentException | java.time.format.DateTimeParseException e) {
			throw new JsonFormatException("Invalid world metadata: " + e.getMessage(), e);
		}
	}

	private void write(WorldMetadata metadata) throws StorageException {
		JsonObject json = new JsonObject();
		json.addProperty("schemaVersion", SCHEMA);
		json.addProperty("worldId", metadata.worldId().toString());
		json.addProperty("createdAt", metadata.createdAt().toString());
		if (metadata.associatedProfileId() != null) {
			json.addProperty("associatedProfile", metadata.associatedProfileId().toString());
		}
		if (metadata.activeProfileId() != null) {
			json.addProperty("activeProfile", metadata.activeProfileId().toString());
		}
		if (metadata.lastAppliedAt() != null) {
			json.addProperty("lastAppliedAt", metadata.lastAppliedAt().toString());
		}
		AtomicFiles.writeString(file, Json.pretty(json));
	}

	/** @return {@code true} if the identity was generated during this load (WCM sees the world for the first time) */
	public boolean isNewlyCreated() {
		return created;
	}

	public synchronized WorldMetadata get() {
		return current;
	}

	/** Persists new metadata; the in-memory value only changes when the write succeeded. */
	public synchronized void update(WorldMetadata metadata) throws StorageException {
		if (!metadata.worldId().equals(current.worldId())) {
			throw new IllegalArgumentException("World id cannot change");
		}
		write(metadata);
		current = metadata;
	}
}
