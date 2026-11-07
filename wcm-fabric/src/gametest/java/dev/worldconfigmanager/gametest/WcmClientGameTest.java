package dev.worldconfigmanager.gametest;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import dev.worldconfigmanager.api.ConfigValue;
import dev.worldconfigmanager.core.json.ValueJson;
import dev.worldconfigmanager.fabric.client.ClientSession;
import dev.worldconfigmanager.fabric.client.backend.Backend;
import dev.worldconfigmanager.fabric.client.backend.J;
import dev.worldconfigmanager.fabric.client.backend.Response;
import dev.worldconfigmanager.fabric.client.screen.WcmScreen;
import net.fabricmc.fabric.api.client.gametest.v1.FabricClientGameTest;
import net.fabricmc.fabric.api.client.gametest.v1.context.ClientGameTestContext;
import net.fabricmc.fabric.api.client.gametest.v1.context.TestDedicatedServerConnection;
import net.fabricmc.fabric.api.client.gametest.v1.context.TestDedicatedServerContext;
import net.fabricmc.fabric.api.client.gametest.v1.context.TestSingleplayerContext;
import net.minecraft.server.MinecraftServer;
import net.minecraft.world.level.gamerules.GameRule;

import java.util.Optional;
import java.util.concurrent.atomic.AtomicReference;

/**
 * End-to-end client test: singleplayer handshake, every GUI page, real network requests (profile apply), GUI
 * scaling, and server-side permission enforcement on a dedicated server with a non-operator player.
 */
public final class WcmClientGameTest implements FabricClientGameTest {
	private static void check(boolean condition, String message) {
		if (!condition) {
			throw new AssertionError(message);
		}
	}

	private static Response call(ClientGameTestContext ctx, boolean server, String type, JsonObject body) {
		AtomicReference<Response> result = new AtomicReference<>();
		ctx.runOnClient(mc -> {
			Optional<Backend> backend = server ? ClientSession.serverBackend() : ClientSession.localBackend();
			backend.orElseThrow(() -> new AssertionError("no backend")).request(type, body, result::set);
		});
		ctx.waitFor(mc -> result.get() != null, 200);
		return result.get();
	}

	private static Object rule(MinecraftServer server, String id) {
		GameRule<?> rule = server.getGameRules().availableRules().filter(r -> r.getIdentifier().toString().equals(id)).findFirst().orElseThrow();
		return server.getGameRules().get(rule);
	}

	private static void openWcm(ClientGameTestContext ctx) {
		ctx.runOnClient(ClientSession::openScreen);
		ctx.waitForScreen(WcmScreen.class);
		ctx.waitTicks(10);
	}

	private static void showPage(ClientGameTestContext ctx, WcmScreen.Nav nav, String screenshot) {
		ctx.runOnClient(mc -> ((WcmScreen) mc.gui.screen()).navigate(nav));
		ctx.waitTicks(12);
		ctx.takeScreenshot(screenshot);
	}

	/** Clicks at GUI coordinates. */
	private static void click(ClientGameTestContext ctx, double guiX, double guiY) {
		double scale = ctx.computeOnClient(mc -> (double) mc.getWindow().getGuiScale());
		ctx.getInput().setCursorPos(guiX * scale, guiY * scale);
		ctx.getInput().pressMouse(0);
		ctx.waitTicks(6);
	}

	private static int[] content(ClientGameTestContext ctx) {
		return ctx.computeOnClient(mc -> {
			var r = ((WcmScreen) mc.gui.screen()).contentArea();
			return new int[] {r.x(), r.y(), r.w(), r.h()};
		});
	}

	private static void resize(ClientGameTestContext ctx, int width, int height, int guiScale) {
		ctx.getInput().resizeWindow(width, height);
		ctx.runOnClient(mc -> {
			mc.options.guiScale().set(guiScale);
			mc.resizeGui();
		});
		ctx.waitTicks(5);
	}

	private static long countFiles(java.nio.file.Path dir) {
		try (var files = java.nio.file.Files.list(dir)) {
			return files.filter(p -> p.toString().endsWith(".wcmp")).count();
		} catch (java.io.IOException e) {
			return 0;
		}
	}

	@Override
	public void runTest(ClientGameTestContext ctx) {
		ctx.getInput().resizeWindow(1280, 720);
		singleplayer(ctx);
		dedicatedServer(ctx);
	}

	private void singleplayer(ClientGameTestContext ctx) {
		try (TestSingleplayerContext sp = ctx.worldBuilder().adjustSettings(s -> s.setAllowCommands(true)).create()) {
			ctx.waitFor(mc -> ClientSession.status() == ClientSession.Status.READY, 400);
			String role = ctx.computeOnClient(mc -> ClientSession.serverBackend().orElseThrow().info().role());
			check(role.equals("OWNER"), "singleplayer host with cheats should be OWNER, was " + role);

			openWcm(ctx);
			ctx.takeScreenshot("01-dashboard");
			showPage(ctx, WcmScreen.Nav.PROFILES, "02-profiles");
			int[] c = content(ctx);
			click(ctx, c[0] + 30, c[1] + 24 + 1 + 15);
			ctx.waitTicks(10);
			ctx.takeScreenshot("03-profile-selected");

			// Dialogs, opened through their real buttons.
			ctx.clickScreenButton("wcm.profiles.actions");
			ctx.waitTicks(4);
			ctx.takeScreenshot("03a-actions-dialog");
			java.nio.file.Path exports = dev.worldconfigmanager.fabric.client.LocalFiles.exportsDir();
			long before = countFiles(exports);
			ctx.clickScreenButton("wcm.profiles.export");
			ctx.waitFor(mc -> countFiles(exports) > before, 200);
			ctx.clickScreenButton("wcm.profiles.new");
			ctx.waitTicks(4);
			ctx.takeScreenshot("03b-new-profile-dialog");
			ctx.getInput().pressKey(com.mojang.blaze3d.platform.InputConstants.KEY_ESCAPE);
			ctx.waitTicks(4);
			ctx.clickScreenButton("wcm.profiles.import");
			ctx.waitTicks(4);
			ctx.takeScreenshot("03c-import-dialog");
			ctx.clickScreenButton("wcm.import.preview");
			ctx.waitTicks(10);
			ctx.takeScreenshot("03d-import-preview");
			// Importing the just-exported profile merges into the existing one (same id).
			ctx.clickScreenButton("wcm.import.confirm");
			ctx.waitTicks(10);
			check(ctx.computeOnClient(mc -> mc.gui.screen() instanceof WcmScreen), "import closes the dialog and returns to WCM");
			showPage(ctx, WcmScreen.Nav.CONFIG, "04-config");
			int filterRows = c[2] < 440 ? 46 : 24;
			click(ctx, c[0] + 30, c[1] + filterRows + 1 + 11);
			ctx.waitTicks(6);
			ctx.takeScreenshot("05-config-selected");
			ctx.getInput().pressKey(com.mojang.blaze3d.platform.InputConstants.KEY_ESCAPE);
			ctx.waitTicks(4);
			check(ctx.computeOnClient(mc -> mc.gui.screen() instanceof WcmScreen), "Escape in the editor goes back to the list first");
			showPage(ctx, WcmScreen.Nav.COMPARE, "06-compare");
			showPage(ctx, WcmScreen.Nav.HISTORY, "07-history");
			showPage(ctx, WcmScreen.Nav.INTEGRATIONS, "08-integrations");
			showPage(ctx, WcmScreen.Nav.SETTINGS, "09-settings");

			// Real network round trip: apply a starter profile through the protocol.
			Response list = call(ctx, true, "profiles.list", new JsonObject());
			check(list.ok(), "profiles.list failed: " + list.errorKey());
			String creative = J.objects(list.body(), "profiles").stream().filter(p -> J.str(p, "name", "").equals("Creative Builder"))
				.map(p -> J.str(p, "id", "")).findFirst().orElseThrow(() -> new AssertionError("starter profile missing"));
			Response preview = call(ctx, true, "profiles.preview", J.of("id", creative));
			check(preview.ok() && !J.objects(preview.body(), "rows").isEmpty(), "preview failed");
			Response applied = call(ctx, true, "profiles.apply", J.of("id", creative));
			check(applied.ok() && J.str(applied.body(), "status", "").equals("SUCCESS"), "apply failed: " + applied.body());
			boolean advanceTime = sp.getServer().computeOnServer(server -> (Boolean) rule(server, "minecraft:advance_time"));
			check(!advanceTime, "Creative Builder should stop the daylight cycle");

			showPage(ctx, WcmScreen.Nav.DASHBOARD, "10-dashboard-after-apply");
			showPage(ctx, WcmScreen.Nav.HISTORY, "11-history-after-apply");

			// Local client profiles (CLIENT scope): the client options provider is available.
			Response local = call(ctx, false, "settings.list", new JsonObject());
			check(local.ok() && J.objects(local.body(), "entries").stream().anyMatch(e -> J.str(e, "key", "").equals("fov")), "client options missing");
			JsonArray fovChange = new JsonArray();
			fovChange.add(J.of("provider", "minecraft:client_options", "key", "fov", "value", ValueJson.write(ConfigValue.of(85L))));
			Response fov = call(ctx, false, "settings.apply", J.of("changes", fovChange));
			check(fov.ok() && J.str(fov.body(), "status", "").equals("SUCCESS"), "local apply failed: " + fov.body());
			int fovNow = ctx.computeOnClient(mc -> mc.options.fov().get());
			check(fovNow == 85, "fov should be 85, was " + fovNow);

			// GUI scaling: the minimum supported GUI size (320x240) and a large one (640x360).
			resize(ctx, 640, 480, 2);
			showPage(ctx, WcmScreen.Nav.DASHBOARD, "12-dashboard-320x240");
			showPage(ctx, WcmScreen.Nav.PROFILES, "13-profiles-320x240");
			showPage(ctx, WcmScreen.Nav.CONFIG, "14-config-320x240");
			showPage(ctx, WcmScreen.Nav.SETTINGS, "15-settings-320x240");
			resize(ctx, 1920, 1080, 3);
			showPage(ctx, WcmScreen.Nav.CONFIG, "16-config-640x360");
			showPage(ctx, WcmScreen.Nav.PROFILES, "17-profiles-640x360");
			resize(ctx, 1280, 720, 0);
			ctx.runOnClient(mc -> mc.gui.setScreen(null));
		}
	}

	private void dedicatedServer(ClientGameTestContext ctx) {
		try (TestDedicatedServerContext server = ctx.worldBuilder().createServer();
			TestDedicatedServerConnection connection = server.connect()) {
			ctx.waitFor(mc -> ClientSession.status() == ClientSession.Status.READY, 400);
			String role = ctx.computeOnClient(mc -> ClientSession.serverBackend().orElseThrow().info().role());
			check(role.equals("PLAYER"), "non-op player on a dedicated server should be PLAYER, was " + role);

			JsonArray change = new JsonArray();
			change.add(J.of("provider", "minecraft:gamerules", "key", "minecraft:keep_inventory", "value", ValueJson.write(ConfigValue.of(true))));
			Response denied = call(ctx, true, "settings.apply", J.of("changes", change));
			check(!denied.ok() && "wcm.error.permission_denied".equals(denied.errorKey()), "apply must be denied for players, got " + denied);
			boolean unchanged = server.computeOnServer(s -> (Boolean) rule(s, "minecraft:keep_inventory"));
			check(!unchanged, "denied change must not alter the game rule");
			check(call(ctx, true, "dashboard", new JsonObject()).ok(), "players may view the dashboard");

			openWcm(ctx);
			ctx.takeScreenshot("20-dedicated-player-dashboard");
			showPage(ctx, WcmScreen.Nav.CONFIG, "21-dedicated-player-config");
			ctx.runOnClient(mc -> mc.gui.setScreen(null));

			String name = ctx.computeOnClient(mc -> mc.player.getName().getString());
			server.runCommand("op " + name);
			connection.waitForClientboundPackets();
			ctx.waitTicks(10);
			Response allowed = call(ctx, true, "settings.apply", J.of("changes", change));
			check(allowed.ok() && J.str(allowed.body(), "status", "").equals("SUCCESS"), "operator apply should succeed: " + allowed);
			boolean changed = server.computeOnServer(s -> (Boolean) rule(s, "minecraft:keep_inventory"));
			check(changed, "operator change must alter the game rule");
		}
	}
}
