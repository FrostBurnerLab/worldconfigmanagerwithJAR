package dev.worldconfigmanager.core.protocol;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import dev.worldconfigmanager.api.ConfigDefinition;
import dev.worldconfigmanager.api.ConfigEntry;
import dev.worldconfigmanager.api.ConfigKey;
import dev.worldconfigmanager.api.ConfigProvider;
import dev.worldconfigmanager.api.ConfigScope;
import dev.worldconfigmanager.api.ConfigValue;
import dev.worldconfigmanager.core.OperationException;
import dev.worldconfigmanager.core.WcmVersion;
import dev.worldconfigmanager.core.WorldSession;
import dev.worldconfigmanager.core.apply.ApplyOutcome;
import dev.worldconfigmanager.core.backup.Backup;
import dev.worldconfigmanager.core.compare.ValueSource;
import dev.worldconfigmanager.core.history.HistoryFilter;
import dev.worldconfigmanager.core.history.HistoryOperation;
import dev.worldconfigmanager.api.ConfigCategory;
import dev.worldconfigmanager.core.json.Json;
import dev.worldconfigmanager.core.json.JsonFormatException;
import dev.worldconfigmanager.core.permission.PermissionAction;
import dev.worldconfigmanager.core.permission.PermissionPolicy;
import dev.worldconfigmanager.core.permission.PermissionRole;
import dev.worldconfigmanager.core.profile.Profile;
import dev.worldconfigmanager.core.settings.WcmSettings;
import dev.worldconfigmanager.core.transfer.ImportMode;
import dev.worldconfigmanager.core.transfer.WcmpFormat;
import dev.worldconfigmanager.core.transfer.WcmpPackage;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.time.format.DateTimeParseException;
import java.util.ArrayList;
import java.util.Base64;
import java.util.EnumMap;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.function.Supplier;

/**
 * Server-authoritative request dispatcher. Every request is size-checked, parsed strictly, rate-limited and
 * permission-checked on the server using the server-computed {@link PlayerAuth}; nothing the client claims about its
 * own permissions is trusted. Must be called on the server thread.
 *
 * <p>Wire format: request {@code {"id":n,"type":"...","body":{...}}}, response
 * {@code {"id":n,"type":"...","ok":true,"body":{...}}} or {@code {"id":n,"ok":false,"error":{"key":"...","args":[]}}}.
 */
public final class ServerProtocolHandler {
	private static final Logger LOGGER = LoggerFactory.getLogger("WorldConfigManager");
	private static final int MAX_BASE64_PACKAGE = (WcmpFormat.MAX_PACKAGE_BYTES + 2) / 3 * 4;

	@FunctionalInterface
	private interface Handler {
		JsonObject handle(Request request) throws OperationException, JsonFormatException;
	}

	private record Route(PermissionAction action, boolean write, int cost, Handler handler) {
	}

	/** Parsed request with its context. */
	private record Request(PlayerAuth auth, WorldSession session, JsonObject body, PermissionPolicy policy) {
	}

	private final Supplier<Optional<WorldSession>> sessions;
	private final Set<ConfigScope> scopes;
	private final Map<String, Route> routes = new HashMap<>();

	public ServerProtocolHandler(Supplier<Optional<WorldSession>> sessions, Set<ConfigScope> scopes) {
		this.sessions = sessions;
		this.scopes = Set.copyOf(scopes);
		registerRoutes();
	}

	private void route(String type, PermissionAction action, boolean write, Handler handler) {
		routes.put(type, new Route(action, write, 1, handler));
	}

	private void route(String type, PermissionAction action, boolean write, int cost, Handler handler) {
		routes.put(type, new Route(action, write, cost, handler));
	}

	private void registerRoutes() {
		route("dashboard", PermissionAction.VIEW_DASHBOARD, false, r -> ProtocolCodec.dashboard(r.session.dashboard()));
		route("providers.list", PermissionAction.VIEW_DASHBOARD, false, this::providers);
		route("settings.list", PermissionAction.VIEW_SETTINGS, false, this::listSettings);
		route("settings.preview", PermissionAction.VIEW_SETTINGS, false, r -> ProtocolCodec.plan(r.session.engine().plan(changes(r))));
		route("settings.apply", PermissionAction.EDIT_SETTINGS, true, r -> ProtocolCodec.outcome(
			r.session.applyChanges(changes(r), r.auth.name(), Json.bool(r.body, "reset", false))));
		route("profiles.list", PermissionAction.VIEW_PROFILES, false, this::listProfiles);
		route("profiles.get", PermissionAction.VIEW_PROFILES, false, this::getProfile);
		route("profiles.create", PermissionAction.MANAGE_PROFILES, true, this::createProfile);
		route("profiles.rename", PermissionAction.MANAGE_PROFILES, true, r -> summary(r, r.session.profiles().rename(id(r), Json.string(r.body, "name"))));
		route("profiles.describe", PermissionAction.MANAGE_PROFILES, true, this::describeProfile);
		route("profiles.duplicate", PermissionAction.MANAGE_PROFILES, true, r -> summary(r, r.session.profiles().duplicate(id(r))));
		route("profiles.delete", PermissionAction.MANAGE_PROFILES, true, this::deleteProfile);
		route("profiles.setDefault", PermissionAction.MANAGE_PROFILES, true, r -> {
			r.session.profiles().setDefault(optId(r.body, "id"));
			return new JsonObject();
		});
		route("profiles.associate", PermissionAction.MANAGE_PROFILES, true, r -> {
			r.session.associate(optId(r.body, "id"));
			return new JsonObject();
		});
		route("profiles.capture", PermissionAction.MANAGE_PROFILES, true, r -> summary(r, r.session.updateProfileFromCurrent(id(r))));
		route("profiles.editEntries", PermissionAction.MANAGE_PROFILES, true, this::editEntries);
		route("profiles.preview", PermissionAction.VIEW_PROFILES, false, r -> {
			JsonObject json = ProtocolCodec.plan(r.session.previewProfile(id(r)));
			json.add("compatibility", ProtocolCodec.compatibility(r.session.compatibility(r.session.profiles().require(id(r)))));
			return json;
		});
		route("profiles.apply", PermissionAction.APPLY_PROFILES, true, r -> ProtocolCodec.outcome(r.session.applyProfile(id(r), r.auth.name())));
		route("compare", PermissionAction.VIEW_PROFILES, false, this::compare);
		route("backups.list", PermissionAction.VIEW_BACKUPS, false, r -> {
			JsonArray list = new JsonArray();
			r.session.backups().list().forEach(b -> list.add(ProtocolCodec.backup(b)));
			JsonObject json = new JsonObject();
			json.add("backups", list);
			return json;
		});
		route("backups.create", PermissionAction.CREATE_BACKUPS, true, r -> ProtocolCodec.backup(
			r.session.createManualBackup(Json.optString(r.body, "label", "")).summary()));
		route("backups.preview", PermissionAction.VIEW_BACKUPS, false, r -> ProtocolCodec.plan(r.session.previewBackup(id(r))));
		route("backups.restore", PermissionAction.RESTORE_BACKUPS, true, r -> ProtocolCodec.outcome(r.session.restoreBackup(id(r), r.auth.name())));
		route("backups.delete", PermissionAction.DELETE_BACKUPS, true, r -> {
			r.session.backups().delete(id(r));
			return new JsonObject();
		});
		route("history.query", PermissionAction.VIEW_HISTORY, false, ServerProtocolHandler::history);
		route("export.profiles", PermissionAction.EXPORT, false, 3, this::exportProfiles);
		route("export.selection", PermissionAction.EXPORT, false, 3, this::exportSelection);
		route("export.current", PermissionAction.EXPORT, false, 3, this::exportCurrent);
		route("export.backup", PermissionAction.EXPORT, false, 3, this::exportBackup);
		route("import.preview", PermissionAction.IMPORT, false, 10, r -> ProtocolCodec.importPreview(
			r.session.transfer().preview(readPackage(r), importMode(r))));
		route("import.commit", PermissionAction.IMPORT, true, 10, r -> ProtocolCodec.importResult(
			r.session.transfer().commit(readPackage(r), importMode(r), Json.bool(r.body, "allowOverwriteNewer", false))));
		route("wcm.settings.get", PermissionAction.VIEW_SETTINGS, false, r -> ProtocolCodec.settings(r.session.settings()));
		route("wcm.settings.set", PermissionAction.MANAGE_WCM_SETTINGS, true, this::updateSettings);
	}

	/** Creates the per-connection state using the current rate-limit settings. */
	public ConnectionState newConnection(java.util.function.LongSupplier nanoTime) {
		WcmSettings s = sessions.get().map(WorldSession::settings).orElse(WcmSettings.defaults());
		return new ConnectionState(new RateLimiter(s.requestsPerSecond(), s.requestBurst(), nanoTime));
	}

	/** Handles one raw client message and returns the response message. */
	public String handle(PlayerAuth auth, ConnectionState state, String raw) {
		long id = 0;
		String type = "";
		try {
			if (raw == null || raw.getBytes(StandardCharsets.UTF_8).length > WcmProtocol.MAX_C2S_BYTES) {
				return error(id, type, "wcm.error.payload_too_large");
			}
			JsonObject message = Json.parseObject(raw);
			id = Json.integer(message, "id");
			type = Json.string(message, "type");
			JsonObject body = Json.optObject(message, "body").orElseGet(JsonObject::new);
			if (type.equals("hello")) {
				return ok(id, type, hello(auth, state, body));
			}
			if (!state.helloReceived()) {
				return error(id, type, "wcm.error.handshake_required");
			}
			if (state.negotiatedVersion() == null) {
				return error(id, type, "wcm.error.protocol_incompatible");
			}
			Route route = routes.get(type);
			if (route == null) {
				return error(id, type, "wcm.error.unknown_request", type);
			}
			if (!state.limiter().tryAcquire(route.cost())) {
				return error(id, type, "wcm.error.rate_limited");
			}
			Optional<WorldSession> session = sessions.get();
			if (session.isEmpty()) {
				return error(id, type, "wcm.error.no_world");
			}
			PermissionPolicy policy = session.get().settings().permissions();
			if (!policy.allows(auth.role(), route.action())) {
				LOGGER.info("Denied {} for {} (role {}, requires {})", type, auth.name(), auth.role(), policy.requiredRole(route.action()));
				return error(id, type, "wcm.error.permission_denied", policy.requiredRole(route.action()).name());
			}
			JsonObject result = route.handler().handle(new Request(auth, session.get(), body, policy));
			if (route.write()) {
				LOGGER.info("{} performed {}", auth.name(), type);
			}
			return ok(id, type, result);
		} catch (JsonFormatException e) {
			return error(id, type, "wcm.error.malformed_request", e.getMessage());
		} catch (OperationException e) {
			return error(id, type, e.messageKey(), e.args().toArray(String[]::new));
		} catch (RuntimeException e) {
			LOGGER.error("Unexpected error handling WCM request {}", type, e);
			return error(id, type, "wcm.error.internal");
		}
	}

	private JsonObject hello(PlayerAuth auth, ConnectionState state, JsonObject body) throws JsonFormatException {
		int clientMax = (int) Json.integer(body, "protocol");
		int clientMin = (int) Json.optInteger(body, "minProtocol", clientMax);
		Optional<Integer> version = WcmProtocol.negotiate(clientMin, clientMax, WcmProtocol.MIN_VERSION, WcmProtocol.VERSION);
		state.onHello(version.orElse(null));
		JsonObject json = new JsonObject();
		json.addProperty("serverProtocol", WcmProtocol.VERSION);
		json.addProperty("serverMinProtocol", WcmProtocol.MIN_VERSION);
		json.addProperty("wcmVersion", WcmVersion.MOD_VERSION);
		json.addProperty("compatible", version.isPresent());
		version.ifPresent(v -> json.addProperty("protocol", v));
		JsonArray scopeArray = new JsonArray();
		scopes.stream().sorted().forEach(s -> scopeArray.add(s.name()));
		json.add("scopes", scopeArray);
		json.addProperty("role", auth.role().name());
		Optional<WorldSession> session = sessions.get();
		session.ifPresent(s -> {
			JsonObject world = new JsonObject();
			world.addProperty("id", s.identity().worldId().toString());
			world.addProperty("name", s.identity().displayName());
			world.addProperty("kind", s.identity().kind().name());
			json.add("world", world);
			JsonArray allowed = new JsonArray();
			s.settings().permissions().allowedActions(auth.role()).stream().sorted().forEach(a -> allowed.add(a.name()));
			json.add("permissions", allowed);
			JsonArray providers = new JsonArray();
			s.config().availableProviders().forEach(p -> providers.add(p.metadata().providerId()));
			json.add("providers", providers);
		});
		if (session.isEmpty()) {
			json.add("permissions", new JsonArray());
			json.add("providers", new JsonArray());
		}
		return json;
	}

	private JsonObject providers(Request r) {
		JsonArray list = new JsonArray();
		Set<String> available = new HashSet<>();
		for (ConfigProvider p : r.session.config().availableProviders()) {
			available.add(p.metadata().providerId());
		}
		for (ConfigProvider p : r.session.config().registry().all()) {
			boolean isAvailable = available.contains(p.metadata().providerId());
			int count = isAvailable ? r.session.config().registry().definitions(p, r.session.config().context()).size() : 0;
			list.add(ProtocolCodec.provider(p, count, isAvailable));
		}
		JsonObject json = new JsonObject();
		json.add("providers", list);
		return json;
	}

	private JsonObject listSettings(Request r) {
		boolean reveal = r.policy.allows(r.auth.role(), PermissionAction.EDIT_SETTINGS);
		JsonArray list = new JsonArray();
		for (ConfigEntry entry : r.session.config().entries()) {
			list.add(ProtocolCodec.entry(entry, reveal));
		}
		JsonObject json = new JsonObject();
		json.add("entries", list);
		json.addProperty("editable", reveal);
		return json;
	}

	private static Map<ConfigKey, ConfigValue> changes(Request r) throws JsonFormatException {
		return ProtocolCodec.readValues(Json.array(r.body, "changes"));
	}

	private JsonObject listProfiles(Request r) {
		var meta = r.session.metadata();
		Optional<UUID> defaultId = r.session.profiles().defaultProfileId();
		JsonArray list = new JsonArray();
		for (Profile p : r.session.profiles().list()) {
			list.add(ProtocolCodec.profileSummary(p, defaultId.map(p.id()::equals).orElse(false), p.id().equals(meta.activeProfileId()),
				p.id().equals(meta.associatedProfileId())));
		}
		JsonObject json = new JsonObject();
		json.add("profiles", list);
		return json;
	}

	private JsonObject summary(Request r, Profile p) {
		var meta = r.session.metadata();
		boolean isDefault = r.session.profiles().defaultProfileId().map(p.id()::equals).orElse(false);
		return ProtocolCodec.profileSummary(p, isDefault, p.id().equals(meta.activeProfileId()), p.id().equals(meta.associatedProfileId()));
	}

	private JsonObject getProfile(Request r) throws OperationException, JsonFormatException {
		Profile p = r.session.profiles().require(id(r));
		JsonObject json = summary(r, p);
		json.add("entries", ProtocolCodec.values(redact(r, p.entries())));
		JsonObject versions = new JsonObject();
		p.providerVersions().forEach(versions::addProperty);
		json.add("providerVersions", versions);
		json.add("compatibility", ProtocolCodec.compatibility(r.session.compatibility(p)));
		return json;
	}

	private JsonObject createProfile(Request r) throws OperationException, JsonFormatException {
		String name = Json.string(r.body, "name");
		String description = Json.optString(r.body, "description", "");
		Profile p = Json.bool(r.body, "capture", false)
			? r.session.captureProfile(name, description)
			: r.session.profiles().create(name, description);
		return summary(r, p);
	}

	private JsonObject describeProfile(Request r) throws OperationException, JsonFormatException {
		UUID id = id(r);
		Profile p = r.session.profiles().updateDescription(id, Json.optString(r.body, "description", ""));
		if (r.body.has("tags")) {
			List<String> tags = new ArrayList<>();
			for (JsonElement e : Json.array(r.body, "tags")) {
				tags.add(Json.asString(e, "tag"));
			}
			p = r.session.profiles().updateTags(id, tags);
		}
		return summary(r, p);
	}

	private JsonObject deleteProfile(Request r) throws OperationException, JsonFormatException {
		UUID id = id(r);
		var meta = r.session.metadata();
		r.session.profiles().delete(id);
		if (id.equals(meta.associatedProfileId())) {
			r.session.associate(null);
		}
		return new JsonObject();
	}

	/** Sets and/or removes entries of a stored profile without applying it. Values are validated when known. */
	private JsonObject editEntries(Request r) throws OperationException, JsonFormatException {
		Profile p = r.session.profiles().require(id(r));
		Map<ConfigKey, ConfigValue> entries = new LinkedHashMap<>(p.entries());
		Map<ConfigKey, ConfigValue> set = ProtocolCodec.readValues(Json.optArray(r.body, "set"));
		for (Map.Entry<ConfigKey, ConfigValue> e : set.entrySet()) {
			Optional<ConfigDefinition> def = r.session.config().definition(e.getKey());
			if (def.isPresent()) {
				var result = def.get().validate(e.getValue());
				if (!result.valid()) {
					throw new OperationException("wcm.error.invalid_value", e.getKey().toString(), result.describe());
				}
			}
			entries.put(e.getKey(), e.getValue());
		}
		for (JsonElement e : Json.optArray(r.body, "remove")) {
			if (!e.isJsonObject()) {
				throw new JsonFormatException("remove entries must be objects");
			}
			entries.remove(ProtocolCodec.readKey(e.getAsJsonObject()));
		}
		Map<String, String> versions = new LinkedHashMap<>(p.providerVersions());
		for (ConfigKey key : set.keySet()) {
			r.session.config().providerVersion(key.providerId()).ifPresent(v -> versions.put(key.providerId(), v));
		}
		return summary(r, r.session.profiles().updateEntries(p.id(), entries, versions, p.minecraftVersion()));
	}

	private JsonObject compare(Request r) throws OperationException, JsonFormatException {
		ValueSource left = source(r, Json.object(r.body, "left"));
		ValueSource right = source(r, Json.object(r.body, "right"));
		return ProtocolCodec.comparison(r.session.comparator().compare(left, right));
	}

	private static ValueSource source(Request r, JsonObject spec) throws OperationException, JsonFormatException {
		String kind = Json.string(spec, "kind");
		ValueSource source = switch (kind) {
			case "current" -> new ValueSource(r.session.identity().displayName(), r.session.config().currentValues(),
				r.session.config().providerVersions());
			case "profile" -> ValueSource.of(r.session.profiles().require(uuid(spec, "id")));
			case "backup" -> {
				Backup b = r.session.backups().load(uuid(spec, "id"));
				yield ValueSource.of(b, b.label().isEmpty() ? b.createdAt().toString() : b.label());
			}
			default -> throw new JsonFormatException("Unknown source kind " + kind);
		};
		return new ValueSource(source.label(), redact(r, source.values()), source.providerVersions());
	}

	private static JsonObject history(Request r) throws JsonFormatException {
		JsonObject f = Json.optObject(r.body, "filter").orElseGet(JsonObject::new);
		HistoryFilter filter;
		try {
			String from = Json.optString(f, "from", null);
			String to = Json.optString(f, "to", null);
			String category = Json.optString(f, "category", null);
			String operation = Json.optString(f, "operation", null);
			filter = new HistoryFilter(from == null ? null : Instant.parse(from), to == null ? null : Instant.parse(to),
				optId(f, "profileId"), Json.optString(f, "provider", null),
				category == null ? null : ConfigCategory.valueOf(category.toUpperCase(Locale.ROOT)),
				operation == null ? null : HistoryOperation.valueOf(operation.toUpperCase(Locale.ROOT)));
		} catch (DateTimeParseException | IllegalArgumentException e) {
			throw new JsonFormatException("Invalid history filter: " + e.getMessage(), e);
		}
		int limit = (int) Math.max(1, Math.min(500, Json.optInteger(r.body, "limit", 200)));
		JsonArray list = new JsonArray();
		r.session.history().query(filter, limit).forEach(e -> list.add(ProtocolCodec.history(e)));
		JsonObject json = new JsonObject();
		json.add("entries", list);
		json.addProperty("total", r.session.history().size());
		return json;
	}

	private static JsonObject exported(String name, byte[] data) {
		JsonObject json = new JsonObject();
		json.addProperty("fileName", safeFileName(name) + WcmpFormat.EXTENSION);
		json.addProperty("data", Base64.getEncoder().encodeToString(data));
		json.addProperty("size", data.length);
		return json;
	}

	/** Reduces a display name to a safe file name stem. */
	public static String safeFileName(String name) {
		String cleaned = name.replaceAll("[^A-Za-z0-9._-]+", "_").replaceAll("^[._-]+", "");
		if (cleaned.length() > 48) {
			cleaned = cleaned.substring(0, 48);
		}
		return cleaned.isEmpty() ? "export" : cleaned;
	}

	private JsonObject exportProfiles(Request r) throws OperationException, JsonFormatException {
		List<UUID> ids = new ArrayList<>();
		for (JsonElement e : Json.array(r.body, "ids")) {
			ids.add(parseUuid(Json.asString(e, "id")));
		}
		if (ids.isEmpty()) {
			throw new OperationException("wcm.error.nothing_selected");
		}
		String name = ids.size() == 1 ? r.session.profiles().require(ids.getFirst()).name() : "profiles-" + ids.size();
		if (r.policy.allows(r.auth.role(), PermissionAction.EDIT_SETTINGS)) {
			return exported(name, r.session.transfer().exportProfiles(ids));
		}
		List<Profile> redacted = new ArrayList<>();
		for (UUID id : ids) {
			Profile p = r.session.profiles().require(id);
			redacted.add(p.withEntries(redact(r, p.entries()), p.providerVersions(), p.minecraftVersion(), p.modifiedAt()));
		}
		return exported(name, r.session.transfer().exportProfileCopies(redacted));
	}

	private JsonObject exportSelection(Request r) throws OperationException, JsonFormatException {
		UUID id = id(r);
		Set<ConfigKey> keys = new HashSet<>();
		for (JsonElement e : Json.array(r.body, "keys")) {
			if (!e.isJsonObject()) {
				throw new JsonFormatException("key must be an object");
			}
			keys.add(ProtocolCodec.readKey(e.getAsJsonObject()));
		}
		return exported(r.session.profiles().require(id).name() + "-selection", r.session.transfer().exportSelection(id, keys));
	}

	private JsonObject exportCurrent(Request r) throws OperationException, JsonFormatException {
		String name = Json.optString(r.body, "name", r.session.identity().displayName());
		return exported(name, r.session.transfer().exportValues(name, "Configuration of " + r.session.identity().displayName(),
			redact(r, r.session.config().currentValues()), r.session.config().providerVersions(), "world-state"));
	}

	private JsonObject exportBackup(Request r) throws OperationException, JsonFormatException {
		Backup b = r.session.backups().load(id(r));
		Map<String, String> versions = new LinkedHashMap<>();
		b.snapshots().forEach((p, s) -> versions.put(p, s.providerVersion()));
		String name = "Backup " + b.createdAt().toString().replace(':', '-').substring(0, 19);
		return exported(name, r.session.transfer().exportValues(name, b.label(), redact(r, b.values()), versions, "backup"));
	}

	private static WcmpPackage readPackage(Request r) throws OperationException, JsonFormatException {
		String data = Json.string(r.body, "data");
		if (data.length() > MAX_BASE64_PACKAGE) {
			throw new OperationException("wcm.package.too_large", Integer.toString(WcmpFormat.MAX_PACKAGE_BYTES));
		}
		byte[] bytes;
		try {
			bytes = Base64.getDecoder().decode(data);
		} catch (IllegalArgumentException e) {
			throw new JsonFormatException("Package data is not valid base64", e);
		}
		return r.session.transfer().read(bytes);
	}

	private static ImportMode importMode(Request r) throws JsonFormatException {
		try {
			return ImportMode.valueOf(Json.optString(r.body, "mode", "MERGE").toUpperCase(Locale.ROOT));
		} catch (IllegalArgumentException e) {
			throw new JsonFormatException("Invalid import mode", e);
		}
	}

	private JsonObject updateSettings(Request r) throws OperationException, JsonFormatException {
		WcmSettings current = r.session.settings();
		WcmSettings updated = current.withRetention(
			(int) Json.optInteger(r.body, "maxBackups", current.maxBackups()),
			(int) Json.optInteger(r.body, "maxBackupAgeDays", current.maxBackupAgeDays()),
			(int) Json.optInteger(r.body, "maxHistoryEntries", current.maxHistoryEntries()),
			(int) Json.optInteger(r.body, "maxHistoryAgeDays", current.maxHistoryAgeDays()))
			.withAutoApplyDefault(Json.bool(r.body, "autoApplyDefault", current.autoApplyDefault()));
		Optional<JsonObject> perms = Json.optObject(r.body, "permissions");
		if (perms.isPresent()) {
			Map<PermissionAction, PermissionRole> overrides = new EnumMap<>(current.permissions().asMap());
			for (Map.Entry<String, JsonElement> e : perms.get().entrySet()) {
				try {
					overrides.put(PermissionAction.valueOf(e.getKey()), PermissionRole.valueOf(Json.asString(e.getValue(), e.getKey())));
				} catch (IllegalArgumentException ex) {
					throw new JsonFormatException("Invalid permission rule " + e.getKey(), ex);
				}
			}
			updated = updated.withPermissions(new PermissionPolicy(overrides));
		}
		r.session.updateSettings(updated);
		return ProtocolCodec.settings(r.session.settings());
	}

	/**
	 * Removes values of sensitive settings unless the requester may edit settings. Applied to everything that sends
	 * stored values to a client (profiles, comparisons, exports).
	 */
	private static Map<ConfigKey, ConfigValue> redact(Request r, Map<ConfigKey, ConfigValue> values) {
		if (r.policy.allows(r.auth.role(), PermissionAction.EDIT_SETTINGS)) {
			return values;
		}
		Map<ConfigKey, ConfigValue> result = new LinkedHashMap<>();
		values.forEach((k, v) -> {
			boolean sensitive = r.session.config().definition(k).map(ConfigDefinition::sensitive).orElse(false);
			if (!sensitive) {
				result.put(k, v);
			}
		});
		return result;
	}

	private static UUID id(Request r) throws JsonFormatException {
		return uuid(r.body, "id");
	}

	private static UUID uuid(JsonObject json, String name) throws JsonFormatException {
		return parseUuid(Json.string(json, name));
	}

	private static UUID optId(JsonObject json, String name) throws JsonFormatException {
		String raw = Json.optString(json, name, null);
		return raw == null ? null : parseUuid(raw);
	}

	private static UUID parseUuid(String raw) throws JsonFormatException {
		try {
			return UUID.fromString(raw);
		} catch (IllegalArgumentException e) {
			throw new JsonFormatException("Invalid id", e);
		}
	}

	private static String ok(long id, String type, JsonObject body) {
		JsonObject json = new JsonObject();
		json.addProperty("id", id);
		json.addProperty("type", type);
		json.addProperty("ok", true);
		json.add("body", body);
		return Json.compact(json);
	}

	private static String error(long id, String type, String key, String... args) {
		JsonObject json = new JsonObject();
		json.addProperty("id", id);
		json.addProperty("type", type);
		json.addProperty("ok", false);
		JsonObject error = new JsonObject();
		error.addProperty("key", key);
		JsonArray a = new JsonArray();
		for (String arg : args) {
			a.add(arg == null ? "" : arg);
		}
		error.add("args", a);
		json.add("error", error);
		return Json.compact(json);
	}
}
