package dev.worldconfigmanager.fabric.client.screen.page;

import com.google.gson.JsonObject;
import dev.worldconfigmanager.fabric.client.backend.J;
import dev.worldconfigmanager.fabric.client.screen.Page;
import dev.worldconfigmanager.fabric.client.ui.Rect;
import dev.worldconfigmanager.fabric.client.ui.ScrollList;
import dev.worldconfigmanager.fabric.client.ui.Theme;
import dev.worldconfigmanager.fabric.client.ui.Ui;
import dev.worldconfigmanager.fabric.client.ui.Values;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.Button;
import net.minecraft.network.chat.Component;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/** Side-by-side comparison of two profiles, backups or the live configuration, by key and typed value. */
public final class ComparePage extends Page {
	private static JsonObject presetLeft;
	private static JsonObject presetRight;

	private final ScrollList<JsonObject> rows = new ScrollList<>(13, this::drawRow);
	private final List<JsonObject> sources = new ArrayList<>();
	private int left;
	private int right = 1;
	private boolean differencesOnly = true;
	private JsonObject result;

	/** Pre-selects the two sides for the next time the page opens. */
	public static void preset(JsonObject leftSpec, JsonObject rightSpec) {
		presetLeft = leftSpec;
		presetRight = rightSpec;
	}

	@Override
	public Component title() {
		return Ui.tr("wcm.nav.compare");
	}

	@Override
	public void refresh() {
		if (backend() == null || !can("VIEW_PROFILES")) {
			return;
		}
		sources.clear();
		sources.add(J.of("kind", "current", "label", Ui.tr("wcm.compare.current").getString()));
		loading = true;
		request("profiles.list", new JsonObject(), r -> {
			for (JsonObject p : J.objects(r.body(), "profiles")) {
				sources.add(J.of("kind", "profile", "id", J.str(p, "id", ""), "label", J.str(p, "name", "?")));
			}
			if (can("VIEW_BACKUPS")) {
				request("backups.list", new JsonObject(), b -> {
					for (JsonObject backup : J.objects(b.body(), "backups")) {
						if (!J.bool(backup, "corrupt", false)) {
							String label = J.str(backup, "label", "");
							sources.add(J.of("kind", "backup", "id", J.str(backup, "id", ""), "label",
								Ui.tr("wcm.compare.backup", Ui.time(J.str(backup, "createdAt", "")) + (label.isEmpty() ? "" : " " + label)).getString()));
						}
					}
					afterSources();
				});
			} else {
				afterSources();
			}
		});
	}

	private void afterSources() {
		loading = false;
		if (presetLeft != null) {
			left = indexOf(presetLeft);
			right = indexOf(presetRight);
			presetLeft = null;
			presetRight = null;
		}
		left = Math.min(left, sources.size() - 1);
		right = Math.min(right, sources.size() - 1);
		compare();
	}

	private int indexOf(JsonObject spec) {
		for (int i = 0; i < sources.size(); i++) {
			JsonObject s = sources.get(i);
			if (J.str(s, "kind", "").equals(J.str(spec, "kind", "")) && J.str(s, "id", "").equals(J.str(spec, "id", ""))) {
				return i;
			}
		}
		return 0;
	}

	private void compare() {
		if (sources.size() < 2 && left == right) {
			result = null;
			rows.setItems(List.of());
			screen.rebuild();
			return;
		}
		JsonObject l = spec(sources.get(left));
		JsonObject r = spec(sources.get(right));
		request("compare", J.of("left", l, "right", r), resp -> {
			result = resp.body();
			updateRows();
			screen.rebuild();
		});
	}

	private static JsonObject spec(JsonObject source) {
		return J.of("kind", J.str(source, "kind", "current"), "id", J.str(source, "id", null));
	}

	private void updateRows() {
		List<JsonObject> all = J.objects(result, "rows");
		rows.setItems(differencesOnly ? all.stream().filter(r -> !"IDENTICAL".equals(J.str(r, "status", ""))).toList() : all);
		rows.setEmptyMessage(Ui.tr(all.isEmpty() ? "wcm.compare.empty" : "wcm.compare.identical"));
	}

	@Override
	protected void layout() {
		if (sources.isEmpty() || !can("VIEW_PROFILES")) {
			return;
		}
		int w = (area.w() - 8) / 3;
		screen.add(Button.builder(Component.literal(Ui.ellipsize(font, label(left), w - 10)), b -> {
			left = (left + 1) % sources.size();
			compare();
		}).bounds(area.x(), area.y(), w, 20).build());
		screen.add(Button.builder(Component.literal(Ui.ellipsize(font, label(right), w - 10)), b -> {
			right = (right + 1) % sources.size();
			compare();
		}).bounds(area.x() + w + 4, area.y(), w, 20).build());
		screen.add(Button.builder(Component.literal(Ui.ellipsize(font, Ui.tr(differencesOnly ? "wcm.compare.differences" : "wcm.compare.all").getString(),
			area.right() - (area.x() + 2 * (w + 4)) - 10)), b -> {
			differencesOnly = !differencesOnly;
			if (result != null) {
				updateRows();
			}
			screen.rebuild();
		}).bounds(area.x() + 2 * (w + 4), area.y(), area.right() - (area.x() + 2 * (w + 4)), 20).build());
		rows.setArea(new Rect(area.x(), area.y() + 50, area.w(), area.h() - 50));
	}

	private String label(int index) {
		return index < sources.size() ? J.str(sources.get(index), "label", "?") : "?";
	}

	@Override
	public void draw(GuiGraphicsExtractor g, int mouseX, int mouseY, float delta) {
		if (backend() == null) {
			return;
		}
		if (!can("VIEW_PROFILES")) {
			g.centeredText(font, Ui.tr("wcm.common.no_permission"), area.x() + area.w() / 2, area.y() + 40, Theme.TEXT_MUTED);
			return;
		}
		if (result != null) {
			JsonObject counts = J.obj(result, "counts");
			StringBuilder sb = new StringBuilder();
			for (String s : List.of("IDENTICAL", "CHANGED", "ONLY_LEFT", "ONLY_RIGHT", "UNAVAILABLE", "INCOMPATIBLE", "VERSION_CONFLICT")) {
				int n = J.integer(counts, s, 0);
				if (n > 0) {
					sb.append(sb.isEmpty() ? "" : " · ").append(Ui.tr("wcm.compare.status." + s.toLowerCase(Locale.ROOT)).getString()).append(": ").append(n);
				}
			}
			Ui.text(g, font, sb.toString(), area.x(), area.y() + 25, area.w(), Theme.TEXT_MUTED);
		}
		Rect a = rows.area();
		int colKey = a.x() + 6;
		int colL = a.x() + a.w() * 42 / 100;
		int colR = a.x() + a.w() * 63 / 100;
		int colS = a.x() + a.w() * 84 / 100;
		g.text(font, Ui.tr("wcm.compare.setting"), colKey, a.y() - 11, Theme.TEXT_FAINT, false);
		Ui.text(g, font, label(left), colL, a.y() - 11, colR - colL - 4, Theme.TEXT_FAINT);
		Ui.text(g, font, label(right), colR, a.y() - 11, colS - colR - 4, Theme.TEXT_FAINT);
		rows.draw(g, font, mouseX, mouseY);
		drawLoading(g, a);
	}

	private void drawRow(GuiGraphicsExtractor g, JsonObject row, Rect r, boolean hovered, boolean selected) {
		String status = J.str(row, "status", "");
		int color = switch (status) {
			case "IDENTICAL" -> Theme.TEXT_MUTED;
			case "CHANGED" -> Theme.CHANGED;
			case "ONLY_LEFT", "ONLY_RIGHT" -> Theme.INFO;
			case "INCOMPATIBLE", "VERSION_CONFLICT" -> Theme.ERROR;
			default -> Theme.TEXT_FAINT;
		};
		int colL = r.x() + r.w() * 42 / 100;
		int colR = r.x() + r.w() * 63 / 100;
		int colS = r.x() + r.w() * 84 / 100;
		Ui.text(g, font, J.str(row, "key", "?"), r.x() + 6, r.y() + 2, colL - r.x() - 10, Theme.TEXT);
		Ui.text(g, font, Values.display(Values.read(row, "left")), colL, r.y() + 2, colR - colL - 4, color);
		Ui.text(g, font, Values.display(Values.read(row, "right")), colR, r.y() + 2, colS - colR - 4, color);
		Ui.text(g, font, Ui.tr("wcm.compare.status." + status.toLowerCase(Locale.ROOT)), colS, r.y() + 2, r.right() - colS - 2, color);
		if (hovered && !J.str(row, "detail", "").isEmpty()) {
			g.setTooltipForNextFrame(font, Component.literal(J.str(row, "provider", "") + ": " + J.str(row, "detail", "")), r.x() + 10, r.y() + 14);
		}
	}

	@Override
	public boolean mouseClicked(double x, double y, boolean doubleClick) {
		return rows.mouseClicked(x, y, doubleClick);
	}

	@Override
	public boolean mouseScrolled(double x, double y, double amount) {
		return rows.mouseScrolled(x, y, amount);
	}

	@Override
	public boolean mouseDragged(double x, double y) {
		return rows.mouseDragged(y);
	}

	@Override
	public void mouseReleased() {
		rows.mouseReleased();
	}

	@Override
	public boolean keyPressed(int key) {
		return rows.keyPressed(key);
	}
}
