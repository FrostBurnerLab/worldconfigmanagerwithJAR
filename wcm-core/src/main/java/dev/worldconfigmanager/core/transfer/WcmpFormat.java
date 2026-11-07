package dev.worldconfigmanager.core.transfer;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import dev.worldconfigmanager.core.WcmVersion;
import dev.worldconfigmanager.core.json.Json;
import dev.worldconfigmanager.core.json.JsonFormatException;
import dev.worldconfigmanager.core.profile.Profile;
import dev.worldconfigmanager.core.profile.ProfileCodec;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Instant;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.HexFormat;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.regex.Pattern;
import java.util.zip.ZipEntry;
import java.util.zip.ZipException;
import java.util.zip.ZipInputStream;
import java.util.zip.ZipOutputStream;

/**
 * Reader and writer for the versioned {@code .wcmp} package format: a ZIP archive containing {@code manifest.json}
 * and {@code profiles/<uuid>.json}. Packages are treated as untrusted input: sizes, entry names, entry count, JSON
 * structure and checksums are all validated before anything is returned. Package content is never executed.
 */
public final class WcmpFormat {
	public static final String EXTENSION = ".wcmp";
	public static final int MAX_PACKAGE_BYTES = 1024 * 1024;
	public static final int MAX_ENTRY_BYTES = 1024 * 1024;
	public static final long MAX_TOTAL_UNCOMPRESSED = 8L * 1024 * 1024;
	public static final int MAX_ENTRIES = 64;
	private static final String MANIFEST = "manifest.json";
	private static final Pattern PROFILE_ENTRY = Pattern.compile("profiles/([0-9a-f]{8}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{12})\\.json");

	private WcmpFormat() {
	}

	/** Writes profiles into a package. */
	public static byte[] write(List<Profile> profiles, String kind, String minecraftVersion, Instant now) throws PackageException {
		if (profiles.isEmpty()) {
			throw new PackageException(PackageException.Reason.INVALID_PROFILE, "Nothing to export");
		}
		if (profiles.size() > MAX_ENTRIES - 1) {
			throw new PackageException(PackageException.Reason.TOO_MANY_ENTRIES, "Too many profiles for one package");
		}
		Map<String, byte[]> files = new LinkedHashMap<>();
		Map<String, String> checksums = new LinkedHashMap<>();
		JsonArray ids = new JsonArray();
		Set<UUID> seen = new HashSet<>();
		for (Profile profile : profiles) {
			if (!seen.add(profile.id())) {
				throw new PackageException(PackageException.Reason.DUPLICATE_ENTRY, "Duplicate profile " + profile.id());
			}
			String name = "profiles/" + profile.id() + ".json";
			byte[] data = Json.pretty(ProfileCodec.toJson(profile)).getBytes(StandardCharsets.UTF_8);
			files.put(name, data);
			checksums.put(name, sha256(data));
			ids.add(profile.id().toString());
		}
		JsonObject manifest = new JsonObject();
		manifest.addProperty("format", "wcmp");
		manifest.addProperty("formatVersion", WcmVersion.PACKAGE_FORMAT);
		manifest.addProperty("wcmVersion", WcmVersion.MOD_VERSION);
		manifest.addProperty("minecraftVersion", minecraftVersion == null ? "" : minecraftVersion);
		manifest.addProperty("createdAt", now.toString());
		manifest.addProperty("kind", kind);
		manifest.add("profiles", ids);
		JsonObject sums = new JsonObject();
		checksums.forEach(sums::addProperty);
		manifest.add("checksums", sums);

		ByteArrayOutputStream bytes = new ByteArrayOutputStream();
		try (ZipOutputStream zip = new ZipOutputStream(bytes, StandardCharsets.UTF_8)) {
			putEntry(zip, MANIFEST, Json.pretty(manifest).getBytes(StandardCharsets.UTF_8), now);
			for (Map.Entry<String, byte[]> f : files.entrySet()) {
				putEntry(zip, f.getKey(), f.getValue(), now);
			}
		} catch (IOException e) {
			throw new PackageException(PackageException.Reason.CORRUPT, "Failed to write package: " + e.getMessage(), e);
		}
		if (bytes.size() > MAX_PACKAGE_BYTES) {
			throw new PackageException(PackageException.Reason.TOO_LARGE, "Package exceeds " + MAX_PACKAGE_BYTES + " bytes");
		}
		return bytes.toByteArray();
	}

	private static void putEntry(ZipOutputStream zip, String name, byte[] data, Instant time) throws IOException {
		ZipEntry entry = new ZipEntry(name);
		entry.setTime(time.toEpochMilli());
		zip.putNextEntry(entry);
		zip.write(data);
		zip.closeEntry();
	}

	/** Reads and fully validates a package. */
	public static WcmpPackage read(byte[] data) throws PackageException {
		if (data == null || data.length == 0) {
			throw new PackageException(PackageException.Reason.NOT_A_PACKAGE, "Empty file");
		}
		if (data.length > MAX_PACKAGE_BYTES) {
			throw new PackageException(PackageException.Reason.TOO_LARGE, "Package exceeds " + MAX_PACKAGE_BYTES + " bytes");
		}
		if (data.length < 4 || data[0] != 'P' || data[1] != 'K') {
			throw new PackageException(PackageException.Reason.NOT_A_PACKAGE, "Not a ZIP-based .wcmp package");
		}
		Map<String, byte[]> entries = new LinkedHashMap<>();
		long total = 0;
		try (ZipInputStream zip = new ZipInputStream(new ByteArrayInputStream(data), StandardCharsets.UTF_8)) {
			ZipEntry entry;
			while ((entry = zip.getNextEntry()) != null) {
				String name = entry.getName();
				checkName(name);
				if (entry.isDirectory()) {
					if (!name.equals("profiles/")) {
						throw new PackageException(PackageException.Reason.UNEXPECTED_ENTRY, "Unexpected directory " + name);
					}
					continue;
				}
				if (!name.equals(MANIFEST) && !PROFILE_ENTRY.matcher(name).matches()) {
					throw new PackageException(PackageException.Reason.UNEXPECTED_ENTRY, "Unexpected entry " + name);
				}
				if (entries.containsKey(name)) {
					throw new PackageException(PackageException.Reason.DUPLICATE_ENTRY, "Duplicate entry " + name);
				}
				if (entries.size() >= MAX_ENTRIES) {
					throw new PackageException(PackageException.Reason.TOO_MANY_ENTRIES, "Too many entries");
				}
				byte[] content = readBounded(zip, MAX_ENTRY_BYTES);
				total += content.length;
				if (total > MAX_TOTAL_UNCOMPRESSED) {
					throw new PackageException(PackageException.Reason.TOO_LARGE, "Uncompressed content too large");
				}
				entries.put(name, content);
			}
		} catch (ZipException e) {
			throw new PackageException(PackageException.Reason.CORRUPT, "Corrupt archive: " + e.getMessage(), e);
		} catch (IOException e) {
			throw new PackageException(PackageException.Reason.CORRUPT, "Unreadable archive: " + e.getMessage(), e);
		}
		byte[] manifestBytes = entries.get(MANIFEST);
		if (manifestBytes == null) {
			throw new PackageException(PackageException.Reason.MISSING_MANIFEST, "manifest.json is missing");
		}
		WcmpManifest manifest = parseManifest(manifestBytes);
		Set<String> expected = new HashSet<>();
		manifest.profiles().forEach(id -> expected.add("profiles/" + id + ".json"));
		Set<String> actual = new HashSet<>(entries.keySet());
		actual.remove(MANIFEST);
		if (!expected.equals(actual)) {
			throw new PackageException(PackageException.Reason.INVALID_MANIFEST, "Manifest does not match package content");
		}
		for (Map.Entry<String, String> sum : manifest.checksums().entrySet()) {
			byte[] content = entries.get(sum.getKey());
			if (content == null) {
				throw new PackageException(PackageException.Reason.INVALID_MANIFEST, "Checksum for missing entry " + sum.getKey());
			}
			if (!sha256(content).equalsIgnoreCase(sum.getValue())) {
				throw new PackageException(PackageException.Reason.CHECKSUM_MISMATCH, "Checksum mismatch for " + sum.getKey());
			}
		}
		List<Profile> profiles = new ArrayList<>();
		for (UUID id : manifest.profiles()) {
			String name = "profiles/" + id + ".json";
			try {
				Profile profile = ProfileCodec.fromJson(Json.parseObject(new String(entries.get(name), StandardCharsets.UTF_8)));
				if (!profile.id().equals(id)) {
					throw new PackageException(PackageException.Reason.INVALID_PROFILE, "Profile id does not match entry " + name);
				}
				profiles.add(profile);
			} catch (JsonFormatException e) {
				throw new PackageException(PackageException.Reason.INVALID_PROFILE, name + ": " + e.getMessage(), e);
			}
		}
		return new WcmpPackage(manifest, profiles);
	}

	private static void checkName(String name) throws PackageException {
		if (name.isEmpty() || name.startsWith("/") || name.contains("\\") || name.contains("..") || name.contains(":")
			|| name.chars().anyMatch(c -> c < 0x20)) {
			throw new PackageException(PackageException.Reason.UNSAFE_PATH, "Unsafe entry name: " + name);
		}
	}

	private static byte[] readBounded(InputStream in, int max) throws IOException, PackageException {
		ByteArrayOutputStream out = new ByteArrayOutputStream();
		byte[] buffer = new byte[8192];
		int read;
		while ((read = in.read(buffer)) != -1) {
			if (out.size() + read > max) {
				throw new PackageException(PackageException.Reason.TOO_LARGE, "Entry exceeds " + max + " bytes");
			}
			out.write(buffer, 0, read);
		}
		return out.toByteArray();
	}

	private static WcmpManifest parseManifest(byte[] bytes) throws PackageException {
		try {
			JsonObject json = Json.parseObject(new String(bytes, StandardCharsets.UTF_8));
			if (!"wcmp".equals(Json.optString(json, "format", ""))) {
				throw new PackageException(PackageException.Reason.INVALID_MANIFEST, "Not a WCM package manifest");
			}
			int version = (int) Json.integer(json, "formatVersion");
			if (version > WcmVersion.PACKAGE_FORMAT || version < 1) {
				throw new PackageException(PackageException.Reason.UNSUPPORTED_VERSION,
					"Package format " + version + " is not supported (supported: " + WcmVersion.PACKAGE_FORMAT + ")");
			}
			List<UUID> ids = new ArrayList<>();
			Set<UUID> seen = new HashSet<>();
			for (JsonElement e : Json.array(json, "profiles")) {
				UUID id;
				try {
					id = UUID.fromString(Json.asString(e, "profile id"));
				} catch (IllegalArgumentException ex) {
					throw new PackageException(PackageException.Reason.INVALID_MANIFEST, "Invalid profile id in manifest", ex);
				}
				if (!seen.add(id)) {
					throw new PackageException(PackageException.Reason.DUPLICATE_ENTRY, "Duplicate profile id " + id);
				}
				ids.add(id);
			}
			Map<String, String> checksums = new LinkedHashMap<>();
			JsonObject sums = Json.optObject(json, "checksums").orElseGet(JsonObject::new);
			for (Map.Entry<String, JsonElement> e : sums.entrySet()) {
				checksums.put(e.getKey(), Json.asString(e.getValue(), "checksum"));
			}
			return new WcmpManifest(version, Json.optString(json, "wcmVersion", ""), Json.optString(json, "minecraftVersion", ""),
				Json.instant(json, "createdAt"), Json.optString(json, "kind", "profiles"), ids, checksums);
		} catch (JsonFormatException e) {
			throw new PackageException(PackageException.Reason.INVALID_MANIFEST, "Invalid manifest: " + e.getMessage(), e);
		}
	}

	static String sha256(byte[] data) {
		try {
			return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(data));
		} catch (NoSuchAlgorithmException e) {
			throw new IllegalStateException("SHA-256 unavailable", e);
		}
	}
}
