package dev.worldconfigmanager.fabric.client.screen.dialog;

import com.google.gson.JsonObject;
import dev.worldconfigmanager.fabric.client.backend.J;
import dev.worldconfigmanager.fabric.client.screen.Dialog;
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

/**
 * Shows exactly what an apply/restore operation would change before anything happens: changed, unchanged, invalid
 * and unavailable entries plus compatibility findings. The confirm button is disabled when anything is invalid.
 */
public final class PreviewDialog extends Dialog {
	private final Component title;
	private final List<JsonObject> rows;
	private final List<JsonObject> issues;
	private final Component confirmLabel;
	private final Runnable onConfirm;
	private final ScrollList<JsonObject> list;
	private boolean showUnchanged;
	private final int changes;
	private final int invalid;
	private final int unavailable;
	private final int unchanged;

	public PreviewDialog(Component title, JsonObject plan, Component confirmLabel, Runnable onConfirm) {
		this.title = title;
		this.rows = J.objects(plan, "rows");
		this.issues = J.objects(J.obj(plan, "compatibility"), "issues");
		this.confirmLabel = confirmLabel;
		this.onConfirm = onConfirm;
		this.changes = count("CHANGE");
		this.invalid = count("INVALID");
		this.unavailable = count("UNAVAILABLE");
		this.unchanged = count("UNCHANGED");
		this.list = new ScrollList<>(22, this::drawRow);
		list.setEmptyMessage(Component.translatable("wcm.preview.nothing"));
		updateItems();
	}

	private int count(String status) {
		return (int) rows.stream().filter(r -> status.equals(J.str(r, "status", ""))).count();
	}

	private void updateItems() {
		List<JsonObject> items = new ArrayList<>();
		for (String status : List.of("INVALID", "CHANGE", "UNAVAILABLE", "UNCHANGED")) {
			if (status.equals("UNCHANGED") && !showUnchanged) {
				continue;
			}
			rows.stream().filter(r -> status.equals(J.str(r, "status", ""))).forEach(items::add);
		}
		list.setItems(items);
	}

	@Override
	protected Component title() {
		return title;
	}

	@Override
	protected int preferredWidth() {
		return 420;
	}

	@Override
	protected int preferredHeight() {
		return 300;
	}

	@Override
	protected void layout(Rect content) {
		int issuesH = issues.isEmpty() ? 0 : Math.min(40, 4 + issues.size() * 10);
		list.setArea(new Rect(content.x(), content.y() + 14 + issuesH, content.w(), content.h() - 40 - issuesH));
		int bw = (content.w() - 8) / 3;
		Button confirm = screen.add(Button.builder(confirmLabel, b -> {
			close();
			onConfirm.run();
		}).bounds(content.x(), content.bottom() - 20, bw, 20).build());
		confirm.active = onConfirm != null && invalid == 0 && changes > 0;
		screen.add(Button.builder(Component.translatable(showUnchanged ? "wcm.preview.hide_unchanged" : "wcm.preview.show_unchanged"), b -> {
			showUnchanged = !showUnchanged;
			updateItems();
			screen.rebuild();
		}).bounds(content.x() + bw + 4, content.bottom() - 20, bw, 20).build());
		screen.add(Button.builder(Component.translatable("gui.cancel"), b -> close()).bounds(content.x() + 2 * (bw + 4), content.bottom() - 20, bw, 20).build());
	}

	@Override
	protected void draw(GuiGraphicsExtractor g, Rect content, int mouseX, int mouseY, float delta) {
		Component summary = Component.translatable("wcm.preview.summary", changes, unchanged, invalid, unavailable);
		Ui.text(g, font, summary, content.x(), content.y() + 2, content.w(), invalid > 0 ? Theme.ERROR : Theme.TEXT_MUTED);
		int y = content.y() + 14;
		for (JsonObject issue : issues) {
			if (y > list.area().y() - 10) {
				break;
			}
			int color = switch (J.str(issue, "severity", "INFO")) {
				case "ERROR" -> Theme.ERROR;
				case "WARNING" -> Theme.WARNING;
				default -> Theme.INFO;
			};
			Ui.text(g, font, Ui.error(J.str(issue, "key", ""), J.strings(issue, "args")), content.x(), y, content.w(), color);
			y += 10;
		}
		list.draw(g, font, mouseX, mouseY);
	}

	private void drawRow(GuiGraphicsExtractor g, JsonObject row, Rect r, boolean hovered, boolean selected) {
		String status = J.str(row, "status", "");
		int color = switch (status) {
			case "CHANGE" -> Theme.CHANGED;
			case "INVALID" -> Theme.ERROR;
			case "UNAVAILABLE" -> Theme.TEXT_FAINT;
			default -> Theme.TEXT_MUTED;
		};
		String name = row.has("name") ? Ui.plain(J.obj(row, "name")) : J.str(row, "key", "?");
		int badgeW = font.width(Component.translatable("wcm.preview.status." + status.toLowerCase(Locale.ROOT))) + 8;
		Ui.text(g, font, name, r.x() + 6, r.y() + 2, r.w() - badgeW - 14, Theme.TEXT);
		Ui.badge(g, font, Component.translatable("wcm.preview.status." + status.toLowerCase(Locale.ROOT)).getString(), r.right() - badgeW - 4, r.y() + 2, color);
		String detail;
		if (status.equals("INVALID")) {
			JsonObject v = J.obj(row, "validation");
			detail = Ui.error(J.str(v, "key", ""), J.strings(v, "args")).getString();
		} else {
			detail = Values.display(Values.read(row, "current")) + " → " + Values.display(Values.read(row, "target"))
				+ (J.bool(row, "restart", false) ? "  ⟳ " + Component.translatable("wcm.common.restart").getString() : "");
		}
		Ui.text(g, font, J.str(row, "provider", "") + " · " + detail, r.x() + 6, r.y() + 12, r.w() - 12, color);
	}

	@Override
	public boolean mouseClicked(double x, double y, boolean doubleClick) {
		return list.mouseClicked(x, y, doubleClick);
	}

	@Override
	public boolean mouseScrolled(double x, double y, double amount) {
		return list.mouseScrolled(x, y, amount);
	}

	@Override
	public boolean mouseDragged(double x, double y) {
		return list.mouseDragged(y);
	}

	@Override
	public void mouseReleased() {
		list.mouseReleased();
	}

	@Override
	public boolean keyPressed(int key) {
		return list.keyPressed(key);
	}
}
