package dev.worldconfigmanager.core.profile;

import dev.worldconfigmanager.api.ConfigKey;
import dev.worldconfigmanager.api.ConfigValue;
import dev.worldconfigmanager.core.WcmVersion;

import java.time.Instant;
import java.util.ArrayList;
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.TreeMap;
import java.util.UUID;

/**
 * An immutable named collection of setting values. Profiles are identified by UUID, never by name.
 *
 * @param id               primary key
 * @param name             display name (1-64 characters)
 * @param description      free text (up to 512 characters)
 * @param createdAt        creation time
 * @param modifiedAt       last modification time
 * @param schemaVersion    schema version of the serialized form
 * @param wcmVersion       WCM version that last wrote the profile
 * @param minecraftVersion Minecraft version the values were captured with (may be empty)
 * @param tags             optional tags
 * @param providerVersions provider id to provider version at capture time
 * @param entries          stored values, unique per key; entries of unknown providers are preserved
 */
public record Profile(UUID id, String name, String description, Instant createdAt, Instant modifiedAt, int schemaVersion,
	String wcmVersion, String minecraftVersion, Set<String> tags, Map<String, String> providerVersions,
	Map<ConfigKey, ConfigValue> entries) {

	public static final int MAX_NAME = 64;
	public static final int MAX_DESCRIPTION = 512;
	public static final int MAX_TAGS = 16;
	public static final int MAX_TAG_LENGTH = 32;
	public static final int MAX_ENTRIES = 4096;

	public Profile {
		Objects.requireNonNull(id, "id");
		Objects.requireNonNull(createdAt, "createdAt");
		Objects.requireNonNull(modifiedAt, "modifiedAt");
		name = ProfileValidation.requireValidName(name);
		description = ProfileValidation.requireValidDescription(description == null ? "" : description);
		wcmVersion = wcmVersion == null ? WcmVersion.MOD_VERSION : wcmVersion;
		minecraftVersion = minecraftVersion == null ? "" : minecraftVersion;
		tags = ProfileValidation.requireValidTags(tags == null ? Set.of() : tags);
		providerVersions = java.util.Collections.unmodifiableMap(new TreeMap<>(providerVersions == null ? Map.of() : providerVersions));
		if (entries.size() > MAX_ENTRIES) {
			throw new IllegalArgumentException("Too many entries");
		}
		entries = java.util.Collections.unmodifiableMap(new TreeMap<>(entries));
	}

	/** Creates an empty profile. */
	public static Profile create(String name, String description, Instant now) {
		return new Profile(UUID.randomUUID(), name, description, now, now, WcmVersion.PROFILE_SCHEMA, WcmVersion.MOD_VERSION,
			"", Set.of(), Map.of(), Map.of());
	}

	public Profile withName(String newName, Instant now) {
		return new Profile(id, newName, description, createdAt, now, schemaVersion, WcmVersion.MOD_VERSION, minecraftVersion,
			tags, providerVersions, entries);
	}

	public Profile withDescription(String newDescription, Instant now) {
		return new Profile(id, name, newDescription, createdAt, now, schemaVersion, WcmVersion.MOD_VERSION, minecraftVersion,
			tags, providerVersions, entries);
	}

	public Profile withTags(Collection<String> newTags, Instant now) {
		return new Profile(id, name, description, createdAt, now, schemaVersion, WcmVersion.MOD_VERSION, minecraftVersion,
			new LinkedHashSet<>(newTags), providerVersions, entries);
	}

	public Profile withEntries(Map<ConfigKey, ConfigValue> newEntries, Map<String, String> newProviderVersions,
		String newMinecraftVersion, Instant now) {
		return new Profile(id, name, description, createdAt, now, WcmVersion.PROFILE_SCHEMA, WcmVersion.MOD_VERSION,
			newMinecraftVersion, tags, newProviderVersions, newEntries);
	}

	/** Returns a copy with a new id, the given name and fresh timestamps. */
	public Profile duplicate(String newName, Instant now) {
		return new Profile(UUID.randomUUID(), newName, description, now, now, WcmVersion.PROFILE_SCHEMA, WcmVersion.MOD_VERSION,
			minecraftVersion, tags, providerVersions, entries);
	}

	/** Returns a copy whose entries are merged with {@code other}; existing values win when {@code keepExisting}. */
	public Profile mergedWith(Profile other, boolean keepExisting, Instant now) {
		Map<ConfigKey, ConfigValue> merged = new LinkedHashMap<>(entries);
		other.entries.forEach((k, v) -> {
			if (!keepExisting || !merged.containsKey(k)) {
				merged.put(k, v);
			}
		});
		Map<String, String> versions = new LinkedHashMap<>(providerVersions);
		other.providerVersions.forEach((p, v) -> {
			if (!keepExisting || !versions.containsKey(p)) {
				versions.put(p, v);
			}
		});
		Set<String> mergedTags = new LinkedHashSet<>(tags);
		mergedTags.addAll(other.tags);
		List<String> limitedTags = new ArrayList<>(mergedTags).subList(0, Math.min(MAX_TAGS, mergedTags.size()));
		return new Profile(id, name, description, createdAt, now, WcmVersion.PROFILE_SCHEMA, WcmVersion.MOD_VERSION,
			minecraftVersion.isEmpty() ? other.minecraftVersion : minecraftVersion, new LinkedHashSet<>(limitedTags), versions, merged);
	}
}
