package dev.worldconfigmanager.fabric.client.screen.page;

import com.google.gson.JsonObject;
import dev.worldconfigmanager.fabric.client.ClientSession;
import dev.worldconfigmanager.fabric.client.backend.Backend;
import dev.worldconfigmanager.fabric.client.backend.J;
import dev.worldconfigmanager.fabric.client.screen.Page;
import dev.worldconfigmanager.fabric.client.screen.WcmScreen;
import dev.worldconfigmanager.fabric.client.ui.Rect;
import dev.worldconfigmanager.fabric.client.ui.Theme;
import dev.worldconfigmanager.fabric.client.ui.Ui;
import dev.worldconfigmanager.fabric.client.ui.Values;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.network.chat.Component;

import java.util.List;
import java.util.Locale;

/** Overview of the current world. Every number is live data from the backend. */
public final class DashboardPage extends Page {
	private JsonObject data;
	private Rect worldCard;
	private Rect profileCard;
	private Rect statsCard;
	private Rect warningsCard;
	private Rect recentCard;

	@Override
	public Component title() {
		return Ui.tr("wcm.nav.dashboard");
	}

	@Override
	public void refresh() {
		if (backend() == null || (backend().isServer() && !backend().info().compatible())) {
			return;
		}
		loading = true;
		request("dashboard", new JsonObject(), r -> {
			data = r.body();
			loading = false;
			screen.rebuild();
		});
	}

	private int scroll;
	private int contentHeight;

	@Override
	protected void layout() {
		boolean wide = area.w() >= 280;
		int gap = 6;
		int topH = 74;
		int y = area.y() - scroll;
		if (wide) {
			Rect[] top = new Rect(area.x(), y, area.w(), topH).splitH((area.w() - gap) / 2, gap);
			worldCard = top[0];
			profileCard = top[1];
		} else {
			worldCard = new Rect(area.x(), y, area.w(), topH);
			profileCard = new Rect(area.x(), y + topH + gap, area.w(), topH);
		}
		statsCard = new Rect(area.x(), profileCard.bottom() + gap, area.w(), 42);
		int restTop = statsCard.bottom() + gap;
		int restH = Math.max(70, area.bottom() - restTop);
		if (wide) {
			Rect[] bottom = new Rect(area.x(), restTop, area.w(), restH).splitH((area.w() - gap) / 2, gap);
			warningsCard = bottom[0];
			recentCard = bottom[1];
		} else {
			warningsCard = new Rect(area.x(), restTop, area.w(), 70);
			recentCard = new Rect(area.x(), warningsCard.bottom() + gap, area.w(), 90);
		}
		contentHeight = (wide ? warningsCard.bottom() : recentCard.bottom()) - (area.y() - scroll);
		if (data == null) {
			return;
		}
		int buttonY = profileCard.bottom() - 24;
		if (buttonY < area.y() || buttonY + 20 > area.bottom()) {
			return; // scrolled out of view: do not place widgets outside the page area
		}
		int bw = Math.max(40, (profileCard.w() - 16) / 2);
		button(Ui.tr("wcm.dashboard.create_backup"), Ui.tr("wcm.dashboard.create_backup.tooltip"), profileCard.x() + 6, buttonY, bw,
			can("CREATE_BACKUPS"), b -> request("backups.create", J.of("label", ""), r -> {
				screen.toast(Ui.tr("wcm.toast.backup_created"), Theme.SUCCESS);
				refresh();
			}));
		button(Ui.tr("wcm.dashboard.manage_profiles"), profileCard.right() - bw - 6, buttonY, bw, b -> screen.navigate(WcmScreen.Nav.PROFILES));
	}

	@Override
	public boolean mouseScrolled(double x, double y, double amount) {
		if (!area.contains(x, y) || contentHeight <= area.h()) {
			return false;
		}
		scroll = Math.max(0, Math.min(contentHeight - area.h(), scroll - (int) (amount * 20)));
		screen.rebuild();
		return true;
	}

	@Override
	public void draw(GuiGraphicsExtractor g, int mouseX, int mouseY, float delta) {
		Backend backend = backend();
		if (backend == null) {
			emptyState(g, Ui.tr("wcm.dashboard.unavailable"), Ui.tr("wcm.dashboard.unavailable.hint"));
			return;
		}
		if (backend.isServer() && !backend.info().compatible()) {
			emptyState(g, Ui.tr("wcm.dashboard.incompatible"), Ui.tr("wcm.dashboard.incompatible.hint", backend.info().serverVersion()));
			return;
		}
		if (data == null) {
			drawLoading(g, area);
			return;
		}
		g.enableScissor(area.x(), area.y(), area.right(), area.bottom());
		drawCards(g, backend);
		g.disableScissor();
		if (contentHeight > area.h()) {
			int barH = Math.max(12, area.h() * area.h() / contentHeight);
			int barY = area.y() + (area.h() - barH) * scroll / Math.max(1, contentHeight - area.h());
			g.fill(area.right() + 2, barY, area.right() + 4, barY + barH, Theme.BORDER);
		}
	}

	private void drawCards(GuiGraphicsExtractor g, Backend backend) {
		JsonObject world = J.obj(data, "world");
		Rect c = Ui.card(g, font, worldCard, Ui.tr("wcm.dashboard.world"));
		int y = c.y();
		y = Ui.field(g, font, Ui.tr("wcm.dashboard.world.name"), J.str(world, "name", "—"), c.x(), y, c.w(), Theme.TEXT);
		String id = J.str(world, "id", "");
		y = Ui.field(g, font, Ui.tr("wcm.dashboard.world.id"), id.length() > 8 ? id.substring(0, 8) + "…" : id, c.x(), y, c.w(), Theme.TEXT_MUTED);
		y = Ui.field(g, font, Ui.tr("wcm.dashboard.world.type"), Ui.tr("wcm.world.kind." + J.str(world, "kind", "singleplayer").toLowerCase(Locale.ROOT)).getString(),
			c.x(), y, c.w(), Theme.TEXT);
		String mode = J.str(world, "gameMode", "");
		String modeText = mode.isEmpty() ? "—" : Component.translatable("gameMode." + mode).getString()
			+ (J.bool(world, "hardcore", false) ? " (" + Component.translatable("gameMode.hardcore").getString() + ")" : "");
		Ui.field(g, font, Ui.tr("wcm.dashboard.world.mode"), modeText, c.x(), y, c.w(), Theme.TEXT);
		if (!backend.isServer()) {
			Ui.text(g, font, Ui.tr("wcm.dashboard.local_note"), c.x(), c.bottom() - 6, c.w(), Theme.SUCCESS);
		}

		c = Ui.card(g, font, profileCard, Ui.tr("wcm.dashboard.profile"));
		y = c.y();
		String active = J.str(data, "activeProfile", null);
		String associated = J.str(data, "associatedProfile", null);
		y = Ui.field(g, font, Ui.tr("wcm.dashboard.profile.active"), active == null ? Ui.tr("wcm.common.none").getString() : active, c.x(), y, c.w(),
			active == null ? Theme.TEXT_FAINT : Theme.SUCCESS);
		Ui.field(g, font, Ui.tr("wcm.dashboard.profile.associated"), associated == null ? Ui.tr("wcm.common.none").getString() : associated,
			c.x(), y, c.w(), associated == null ? Theme.TEXT_FAINT : Theme.INFO);

		Ui.panel(g, statsCard, Theme.SURFACE);
		String[][] stats = {
			{"wcm.dashboard.stat.settings", Integer.toString(J.integer(data, "managedSettings", 0))},
			{"wcm.dashboard.stat.providers", Integer.toString(J.integer(data, "providers", 0))},
			{"wcm.dashboard.stat.changes", Integer.toString(J.integer(data, "changes", 0))},
			{"wcm.dashboard.stat.backups", Integer.toString(J.integer(data, "backups", 0))}};
		int cellW = statsCard.w() / stats.length;
		for (int i = 0; i < stats.length; i++) {
			int cx = statsCard.x() + i * cellW + 8;
			g.text(font, stats[i][1], cx, statsCard.y() + 6, Theme.ACCENT, false);
			Ui.text(g, font, Ui.tr(stats[i][0]), cx, statsCard.y() + 18, cellW - 12, Theme.TEXT_MUTED);
		}
		String latest = J.str(data, "latestBackup", null);
		Ui.text(g, font, latest == null ? Ui.tr("wcm.dashboard.no_backup") : Ui.tr("wcm.dashboard.latest_backup", Ui.time(latest)),
			statsCard.x() + 3 * cellW + 8, statsCard.y() + 30, cellW - 12, latest == null ? Theme.WARNING : Theme.TEXT_FAINT);

		c = Ui.card(g, font, warningsCard, Ui.tr("wcm.dashboard.warnings"));
		List<JsonObject> warnings = J.objects(data, "warnings");
		if (warnings.isEmpty()) {
			Ui.text(g, font, Ui.tr("wcm.dashboard.no_warnings"), c.x(), c.y(), c.w(), Theme.SUCCESS);
		}
		y = c.y();
		for (JsonObject w : warnings) {
			if (y + font.lineHeight > c.bottom()) {
				break;
			}
			y += Ui.wrapped(g, font, Component.literal("⚠ ").append(Ui.error(J.str(w, "key", ""), J.strings(w, "args"))), c.x(), y, c.w(),
				Theme.WARNING, Math.max(1, (c.bottom() - y) / (font.lineHeight + 1)));
		}

		{
			c = Ui.card(g, font, recentCard, Ui.tr("wcm.dashboard.recent"));
			List<JsonObject> recent = J.objects(data, "recent");
			if (recent.isEmpty()) {
				Ui.wrapped(g, font, Ui.tr("wcm.dashboard.no_changes"), c.x(), c.y(), c.w(), Theme.TEXT_FAINT, 3);
			}
			y = c.y();
			for (JsonObject e : recent) {
				if (y + 2 * font.lineHeight > c.bottom()) {
					break;
				}
				Ui.text(g, font, J.str(e, "key", "?"), c.x(), y, c.w(), Theme.TEXT);
				String change = J.bool(e, "masked", false) ? "***"
					: Values.display(Values.read(e, "old")) + " → " + Values.display(Values.read(e, "new"));
				Ui.text(g, font, Ui.time(J.str(e, "timestamp", "")) + "  " + change, c.x(), y + font.lineHeight + 1, c.w(), Theme.TEXT_FAINT);
				y += 2 * font.lineHeight + 4;
			}
		}
	}

	private void emptyState(GuiGraphicsExtractor g, Component title, Component hint) {
		int cy = area.y() + area.h() / 3;
		g.centeredText(font, title, area.x() + area.w() / 2, cy, Theme.TEXT);
		Ui.wrapped(g, font, hint, area.x() + 20, cy + 16, area.w() - 40, Theme.TEXT_MUTED, 6);
		if (ClientSession.status() == ClientSession.Status.HANDSHAKING) {
			g.centeredText(font, Ui.tr("wcm.common.connecting"), area.x() + area.w() / 2, cy + 60, Theme.TEXT_FAINT);
		}
	}
}
