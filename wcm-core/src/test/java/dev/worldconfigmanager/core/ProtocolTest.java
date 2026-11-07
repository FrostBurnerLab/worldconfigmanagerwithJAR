package dev.worldconfigmanager.core;

import com.google.gson.JsonObject;
import dev.worldconfigmanager.api.ConfigKey;
import dev.worldconfigmanager.api.ConfigScope;
import dev.worldconfigmanager.api.ConfigValue;
import dev.worldconfigmanager.core.json.Json;
import dev.worldconfigmanager.core.permission.PermissionAction;
import dev.worldconfigmanager.core.permission.PermissionPolicy;
import dev.worldconfigmanager.core.permission.PermissionRole;
import dev.worldconfigmanager.core.protocol.ConnectionState;
import dev.worldconfigmanager.core.protocol.PlayerAuth;
import dev.worldconfigmanager.core.protocol.ProtocolCodec;
import dev.worldconfigmanager.core.protocol.RateLimiter;
import dev.worldconfigmanager.core.protocol.ServerProtocolHandler;
import dev.worldconfigmanager.core.protocol.WcmProtocol;
import dev.worldconfigmanager.core.registry.ProviderRegistry;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Path;
import java.util.Base64;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicLong;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ProtocolTest {
	@TempDir
	Path dir;
	TestProvider provider;
	WorldSession session;
	ServerProtocolHandler handler;
	AtomicLong nanos = new AtomicLong();
	int nextId = 1;

	@BeforeEach
	void setUp() throws Exception {
		ProviderRegistry registry = new ProviderRegistry();
		provider = TestProvider.standard("p");
		registry.register(provider);
		session = TestSupport.session(dir, registry, TestSupport.clock());
		handler = new ServerProtocolHandler(() -> Optional.of(session), Set.of(ConfigScope.WORLD, ConfigScope.SERVER));
	}

	private ConnectionState connect() {
		return handler.newConnection(nanos::get);
	}

	private static PlayerAuth player(PermissionRole role) {
		return new PlayerAuth(UUID.randomUUID(), role.name().toLowerCase(), role);
	}

	private JsonObject send(PlayerAuth auth, ConnectionState state, String type, JsonObject body) throws Exception {
		JsonObject msg = new JsonObject();
		msg.addProperty("id", nextId++);
		msg.addProperty("type", type);
		msg.add("body", body);
		return Json.parseObject(handler.handle(auth, state, Json.compact(msg)));
	}

	private JsonObject hello(PlayerAuth auth, ConnectionState state, int min, int max) throws Exception {
		JsonObject body = new JsonObject();
		body.addProperty("protocol", max);
		body.addProperty("minProtocol", min);
		return send(auth, state, "hello", body);
	}

	private static String errorKey(JsonObject response) {
		return response.getAsJsonObject("error").get("key").getAsString();
	}

	@Test
	void negotiationPicksHighestCommonVersion() {
		assertEquals(Optional.of(3), WcmProtocol.negotiate(1, 5, 2, 3));
		assertEquals(Optional.of(1), WcmProtocol.negotiate(1, 1, 1, 4));
		assertTrue(WcmProtocol.negotiate(4, 6, 1, 3).isEmpty());
		assertTrue(WcmProtocol.negotiate(3, 1, 1, 3).isEmpty());
	}

	@Test
	void handshakeEstablishesIdentityProvidersAndPermissions() throws Exception {
		ConnectionState state = connect();
		JsonObject response = hello(player(PermissionRole.OPERATOR), state, 1, WcmProtocol.VERSION);
		assertTrue(response.get("ok").getAsBoolean());
		JsonObject body = response.getAsJsonObject("body");
		assertTrue(body.get("compatible").getAsBoolean());
		assertEquals(WcmProtocol.VERSION, body.get("protocol").getAsInt());
		assertEquals(session.identity().worldId().toString(), body.getAsJsonObject("world").get("id").getAsString());
		assertEquals("p", body.getAsJsonArray("providers").get(0).getAsString());
		assertEquals("OPERATOR", body.get("role").getAsString());
		assertTrue(body.getAsJsonArray("permissions").toString().contains("APPLY_PROFILES"));
	}

	@Test
	void requestsBeforeHandshakeAreRejected() throws Exception {
		JsonObject response = send(player(PermissionRole.OWNER), connect(), "dashboard", new JsonObject());
		assertFalse(response.get("ok").getAsBoolean());
		assertEquals("wcm.error.handshake_required", errorKey(response));
	}

	@Test
	void versionMismatchDegradesToReadOnly() throws Exception {
		ConnectionState state = connect();
		JsonObject response = hello(player(PermissionRole.OWNER), state, 50, 60);
		assertTrue(response.get("ok").getAsBoolean());
		assertFalse(response.getAsJsonObject("body").get("compatible").getAsBoolean());
		assertFalse(response.getAsJsonObject("body").has("protocol"));
		assertEquals("wcm.error.protocol_incompatible", errorKey(send(player(PermissionRole.OWNER), state, "settings.apply", new JsonObject())));
		assertEquals(ConfigValue.of(false), provider.values.get("flag"));
	}

	@Test
	void invalidPayloadsAreRejectedSafely() throws Exception {
		PlayerAuth owner = player(PermissionRole.OWNER);
		ConnectionState state = connect();
		hello(owner, state, 1, 1);
		assertEquals("wcm.error.malformed_request", errorKey(Json.parseObject(handler.handle(owner, state, "{garbage"))));
		assertEquals("wcm.error.malformed_request", errorKey(Json.parseObject(handler.handle(owner, state, "[1,2,3]"))));
		assertEquals("wcm.error.unknown_request", errorKey(send(owner, state, "files.write", new JsonObject())));
		JsonObject bad = new JsonObject();
		bad.add("changes", Json.parseObject("{\"x\":[{\"provider\":\"p\",\"key\":\"flag\",\"value\":{\"type\":\"boolean\",\"value\":\"yes\"}}]}").get("x"));
		assertEquals("wcm.error.malformed_request", errorKey(send(owner, state, "settings.apply", bad)));
		String huge = "{\"id\":1,\"type\":\"dashboard\",\"body\":{\"pad\":\"" + "a".repeat(WcmProtocol.MAX_C2S_BYTES) + "\"}}";
		assertEquals("wcm.error.payload_too_large", errorKey(Json.parseObject(handler.handle(owner, state, huge))));
		JsonObject garbageImport = new JsonObject();
		garbageImport.addProperty("data", Base64.getEncoder().encodeToString("not a zip".getBytes()));
		assertEquals("wcm.package.not_a_package", errorKey(send(owner, state, "import.preview", garbageImport)));
	}

	private static JsonObject changeBody(String key, ConfigValue value) {
		JsonObject body = new JsonObject();
		body.add("changes", ProtocolCodec.values(Map.of(new ConfigKey("p", key), value)));
		return body;
	}

	@Test
	void unauthorizedRequestsAreDeniedServerSide() throws Exception {
		PlayerAuth regular = player(PermissionRole.PLAYER);
		ConnectionState state = connect();
		JsonObject hello = hello(regular, state, 1, 1);
		assertFalse(hello.getAsJsonObject("body").getAsJsonArray("permissions").toString().contains("EDIT_SETTINGS"));
		JsonObject denied = send(regular, state, "settings.apply", changeBody("flag", ConfigValue.of(true)));
		assertEquals("wcm.error.permission_denied", errorKey(denied));
		assertEquals(ConfigValue.of(false), provider.values.get("flag"));
		assertEquals("wcm.error.permission_denied", errorKey(send(regular, state, "settings.list", new JsonObject())));
		assertTrue(send(regular, state, "dashboard", new JsonObject()).get("ok").getAsBoolean(), "players may view the dashboard");
	}

	@Test
	void permissionChecksFollowPolicy() throws Exception {
		PlayerAuth op = player(PermissionRole.OPERATOR);
		ConnectionState state = connect();
		hello(op, state, 1, 1);
		JsonObject ok = send(op, state, "settings.apply", changeBody("count", ConfigValue.of(42L)));
		assertTrue(ok.get("ok").getAsBoolean(), ok.toString());
		assertEquals("SUCCESS", ok.getAsJsonObject("body").get("status").getAsString());
		assertEquals(ConfigValue.of(42L), provider.values.get("count"));

		assertEquals("wcm.error.permission_denied", errorKey(send(op, state, "wcm.settings.set", new JsonObject())), "owner only");
		JsonObject backups = send(op, state, "backups.list", new JsonObject());
		String backupId = backups.getAsJsonObject("body").getAsJsonArray("backups").get(0).getAsJsonObject().get("id").getAsString();
		JsonObject restore = new JsonObject();
		restore.addProperty("id", backupId);
		assertEquals("wcm.error.permission_denied", errorKey(send(op, state, "backups.restore", restore)), "restore requires admin");

		// The server operator tightens the policy: editing now requires ADMIN.
		session.updateSettings(session.settings().withPermissions(new PermissionPolicy(Map.of(PermissionAction.EDIT_SETTINGS, PermissionRole.ADMIN))));
		assertEquals("wcm.error.permission_denied", errorKey(send(op, state, "settings.apply", changeBody("count", ConfigValue.of(43L)))));
		assertTrue(send(player(PermissionRole.ADMIN), helloState(PermissionRole.ADMIN), "settings.apply", changeBody("count", ConfigValue.of(43L))).get("ok").getAsBoolean());
	}

	private ConnectionState helloState(PermissionRole role) throws Exception {
		ConnectionState state = connect();
		hello(player(role), state, 1, 1);
		return state;
	}

	@Test
	void ownerCannotLoseOwnerOnlySettingsControl() {
		PermissionPolicy policy = new PermissionPolicy(Map.of(PermissionAction.MANAGE_WCM_SETTINGS, PermissionRole.PLAYER));
		assertEquals(PermissionRole.OWNER, policy.requiredRole(PermissionAction.MANAGE_WCM_SETTINGS));
	}

	@Test
	void rateLimitingRejectsFloods() throws Exception {
		PlayerAuth owner = player(PermissionRole.OWNER);
		ConnectionState state = connect();
		hello(owner, state, 1, 1);
		int accepted = 0;
		String lastError = "";
		for (int i = 0; i < 100; i++) {
			JsonObject r = send(owner, state, "dashboard", new JsonObject());
			if (r.get("ok").getAsBoolean()) {
				accepted++;
			} else {
				lastError = errorKey(r);
			}
		}
		assertEquals(session.settings().requestBurst(), accepted);
		assertEquals("wcm.error.rate_limited", lastError);
		nanos.addAndGet(2_000_000_000L);
		assertTrue(send(owner, state, "dashboard", new JsonObject()).get("ok").getAsBoolean(), "tokens refill over time");
	}

	@Test
	void rateLimiterRefills() {
		AtomicLong t = new AtomicLong();
		RateLimiter limiter = new RateLimiter(2, 2, t::get);
		assertTrue(limiter.tryAcquire(1));
		assertTrue(limiter.tryAcquire(1));
		assertFalse(limiter.tryAcquire(1));
		t.addAndGet(500_000_000L);
		assertTrue(limiter.tryAcquire(1));
	}

	@Test
	void fullProfileWorkflowOverProtocol() throws Exception {
		PlayerAuth admin = player(PermissionRole.ADMIN);
		ConnectionState state = connect();
		hello(admin, state, 1, 1);
		JsonObject create = new JsonObject();
		create.addProperty("name", "Captured");
		create.addProperty("capture", true);
		JsonObject created = send(admin, state, "profiles.create", create).getAsJsonObject("body");
		assertEquals(3, created.get("entryCount").getAsInt());
		String id = created.get("id").getAsString();

		JsonObject edit = new JsonObject();
		edit.addProperty("id", id);
		edit.add("set", ProtocolCodec.values(Map.of(new ConfigKey("p", "flag"), ConfigValue.of(true))));
		assertTrue(send(admin, state, "profiles.editEntries", edit).get("ok").getAsBoolean());
		JsonObject badEdit = new JsonObject();
		badEdit.addProperty("id", id);
		badEdit.add("set", ProtocolCodec.values(Map.of(new ConfigKey("p", "count"), ConfigValue.of(1000L))));
		assertEquals("wcm.error.invalid_value", errorKey(send(admin, state, "profiles.editEntries", badEdit)));

		JsonObject idBody = new JsonObject();
		idBody.addProperty("id", id);
		JsonObject preview = send(admin, state, "profiles.preview", idBody).getAsJsonObject("body");
		assertTrue(preview.getAsJsonArray("rows").toString().contains("CHANGE"));
		JsonObject applied = send(admin, state, "profiles.apply", idBody).getAsJsonObject("body");
		assertEquals("SUCCESS", applied.get("status").getAsString());
		assertEquals(ConfigValue.of(true), provider.values.get("flag"));

		JsonObject exportBody = new JsonObject();
		exportBody.add("ids", Json.parseObject("{\"a\":[\"" + id + "\"]}").get("a"));
		JsonObject exported = send(admin, state, "export.profiles", exportBody).getAsJsonObject("body");
		assertEquals("Captured.wcmp", exported.get("fileName").getAsString());
		JsonObject importBody = new JsonObject();
		importBody.addProperty("data", exported.get("data").getAsString());
		importBody.addProperty("mode", "MERGE");
		JsonObject importPreview = send(admin, state, "import.preview", importBody).getAsJsonObject("body");
		assertEquals("MERGE", importPreview.getAsJsonArray("profiles").get(0).getAsJsonObject().get("action").getAsString());

		JsonObject history = send(admin, state, "history.query", new JsonObject()).getAsJsonObject("body");
		assertTrue(history.get("total").getAsInt() > 0);
		JsonObject dashboard = send(admin, state, "dashboard", new JsonObject()).getAsJsonObject("body");
		assertEquals("Captured", dashboard.get("activeProfile").getAsString());
		assertEquals(3, dashboard.get("managedSettings").getAsInt());
	}

	@Test
	void sensitiveValuesAreRedactedForUsersWhoCannotEdit() throws Exception {
		provider.define(dev.worldconfigmanager.api.ConfigDefinition.builder("secret", dev.worldconfigmanager.api.ValueType.STRING,
			ConfigValue.of("")).sensitive(true).build());
		provider.values.put("secret", ConfigValue.of("hunter2"));
		session.config().registry().invalidateAll();
		var profile = session.captureProfile("With secret", "");
		// Operators may view profiles but the policy below forbids them to edit settings.
		session.updateSettings(session.settings().withPermissions(new PermissionPolicy(Map.of(PermissionAction.EDIT_SETTINGS, PermissionRole.ADMIN))));
		PlayerAuth op = player(PermissionRole.OPERATOR);
		ConnectionState state = connect();
		hello(op, state, 1, 1);
		JsonObject idBody = new JsonObject();
		idBody.addProperty("id", profile.id().toString());
		String viewed = send(op, state, "profiles.get", idBody).toString();
		assertFalse(viewed.contains("hunter2"), "profile view must not leak sensitive values");
		JsonObject cmp = new JsonObject();
		cmp.add("left", Json.parseObject("{\"kind\":\"current\"}"));
		cmp.add("right", Json.parseObject("{\"kind\":\"profile\",\"id\":\"" + profile.id() + "\"}"));
		assertFalse(send(op, state, "compare", cmp).toString().contains("hunter2"), "comparison must not leak sensitive values");
		JsonObject export = new JsonObject();
		export.add("ids", Json.parseObject("{\"a\":[\"" + profile.id() + "\"]}").get("a"));
		String data = send(op, state, "export.profiles", export).getAsJsonObject("body").get("data").getAsString();
		var pkg = dev.worldconfigmanager.core.transfer.WcmpFormat.read(Base64.getDecoder().decode(data));
		assertFalse(pkg.profiles().getFirst().entries().containsKey(new ConfigKey("p", "secret")), "export must not contain sensitive values");
		// An admin (allowed to edit) sees the value.
		PlayerAuth admin = player(PermissionRole.ADMIN);
		ConnectionState adminState = connect();
		hello(admin, adminState, 1, 1);
		assertTrue(send(admin, adminState, "profiles.get", idBody).toString().contains("hunter2"));
	}

	@Test
	void safeFileNames() {
		assertEquals("evil", ServerProtocolHandler.safeFileName("../evil"));
		assertEquals("a_b", ServerProtocolHandler.safeFileName("a/b"));
		assertEquals("export", ServerProtocolHandler.safeFileName("..."));
	}
}
