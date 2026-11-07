package dev.worldconfigmanager.core.profile;

import com.google.gson.JsonObject;
import dev.worldconfigmanager.core.json.Json;
import dev.worldconfigmanager.core.json.JsonFormatException;
import dev.worldconfigmanager.core.storage.AtomicFiles;
import dev.worldconfigmanager.core.storage.LoadProblem;
import dev.worldconfigmanager.core.storage.SafePaths;
import dev.worldconfigmanager.core.storage.StorageException;

import java.io.IOException;
import java.nio.file.DirectoryStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Stores each profile as {@code <uuid>.json} in a directory, plus {@code library.json} for library metadata.
 * Profiles are cached in memory; the directory is only scanned on {@link #reload()}.
 */
public final class JsonProfileRepository implements ProfileRepository {
	private static final Logger LOGGER = LoggerFactory.getLogger("WorldConfigManager");
	private static final String LIBRARY_FILE = "library.json";

	private final Path directory;
	private final Map<UUID, Profile> cache = new LinkedHashMap<>();
	private final List<LoadProblem> problems = new ArrayList<>();
	private UUID defaultProfileId;

	public JsonProfileRepository(Path directory) {
		this.directory = directory;
		reload();
	}

	@Override
	public synchronized void reload() {
		cache.clear();
		problems.clear();
		defaultProfileId = null;
		if (!Files.isDirectory(directory)) {
			return;
		}
		try (DirectoryStream<Path> stream = Files.newDirectoryStream(directory, "*.json")) {
			for (Path file : stream) {
				String name = file.getFileName().toString();
				if (name.equals(LIBRARY_FILE)) {
					loadLibrary(file);
					continue;
				}
				loadProfile(file, name);
			}
		} catch (IOException e) {
			LOGGER.warn("Could not list profile directory", e);
			problems.add(new LoadProblem(directory.getFileName().toString(), e.getMessage(), null));
		}
		if (defaultProfileId != null && !cache.containsKey(defaultProfileId)) {
			defaultProfileId = null;
		}
	}

	private void loadProfile(Path file, String name) {
		try {
			Profile profile = ProfileCodec.fromJson(Json.parseObject(AtomicFiles.readString(file)));
			if (!name.equals(profile.id() + ".json")) {
				throw new JsonFormatException("File name does not match profile id " + profile.id());
			}
			cache.put(profile.id(), profile);
		} catch (JsonFormatException | StorageException e) {
			String quarantined = null;
			try {
				quarantined = AtomicFiles.quarantine(file).getFileName().toString();
			} catch (StorageException q) {
				LOGGER.warn("Could not quarantine " + name, q);
			}
			LOGGER.warn("Skipped unreadable profile " + name + ": " + e.getMessage());
			problems.add(new LoadProblem(name, e.getMessage(), quarantined));
		}
	}

	private void loadLibrary(Path file) {
		try {
			JsonObject json = Json.parseObject(AtomicFiles.readString(file));
			String id = Json.optString(json, "defaultProfile", null);
			defaultProfileId = id == null ? null : UUID.fromString(id);
		} catch (JsonFormatException | StorageException | IllegalArgumentException e) {
			LOGGER.warn("Ignoring unreadable profile library metadata: " + e.getMessage());
			problems.add(new LoadProblem(LIBRARY_FILE, e.getMessage(), null));
		}
	}

	@Override
	public synchronized List<Profile> findAll() {
		List<Profile> list = new ArrayList<>(cache.values());
		list.sort(Comparator.comparing(Profile::name, String.CASE_INSENSITIVE_ORDER).thenComparing(Profile::id));
		return List.copyOf(list);
	}

	@Override
	public synchronized Optional<Profile> findById(UUID id) {
		return Optional.ofNullable(cache.get(id));
	}

	@Override
	public synchronized void save(Profile profile) throws StorageException {
		Path file = SafePaths.resolveChild(directory, profile.id() + ".json");
		AtomicFiles.writeString(file, Json.pretty(ProfileCodec.toJson(profile)));
		cache.put(profile.id(), profile);
	}

	@Override
	public synchronized boolean delete(UUID id) throws StorageException {
		if (!cache.containsKey(id)) {
			return false;
		}
		Path file = SafePaths.resolveChild(directory, id + ".json");
		try {
			Files.deleteIfExists(file);
		} catch (IOException e) {
			throw new StorageException("Failed to delete profile " + id, e);
		}
		cache.remove(id);
		if (id.equals(defaultProfileId)) {
			setDefaultProfileId(null);
		}
		return true;
	}

	@Override
	public synchronized Optional<UUID> defaultProfileId() {
		return Optional.ofNullable(defaultProfileId);
	}

	@Override
	public synchronized void setDefaultProfileId(UUID id) throws StorageException {
		JsonObject json = new JsonObject();
		json.addProperty("schemaVersion", 1);
		if (id != null) {
			json.addProperty("defaultProfile", id.toString());
		}
		AtomicFiles.writeString(SafePaths.resolveChild(directory, LIBRARY_FILE), Json.pretty(json));
		defaultProfileId = id;
	}

	@Override
	public synchronized List<LoadProblem> loadProblems() {
		return List.copyOf(problems);
	}
}
