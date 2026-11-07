package dev.worldconfigmanager.fabric.client.screen.dialog;

import com.google.gson.JsonObject;
import dev.worldconfigmanager.core.storage.StorageException;
import dev.worldconfigmanager.fabric.client.LocalFiles;
import dev.worldconfigmanager.fabric.client.backend.Backend;
import dev.worldconfigmanager.fabric.client.backend.J;
import dev.worldconfigmanager.fabric.client.screen.Dialog;
import dev.worldconfigmanager.fabric.client.ui.Rect;
import dev.worldconfigmanager.fabric.client.ui.ScrollList;
import dev.worldconfigmanager.fabric.client.ui.Theme;
import dev.worldconfigmanager.fabric.client.ui.Ui;
import dev.worldconfigmanager.fabric.client.ui.Values;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.Tooltip;
import net.minecraft.network.chat.Component;
import com.mojang.blaze3d.Blaze3D;

import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/**
 * Two-step import: pick a .wcmp file and a mode, review the detailed server-side preview (new, existing, conflicting,
 * missing-provider and incompatible entries), then import. Nothing is written before the final confirmation.
 */
public final class ImportDialog extends Dialog {
	private final Runnable onDone;
	private final ScrollList<Path> files = new ScrollList<>(14, this::drawFile);
	private final ScrollList<JsonObject> previewList = new ScrollList<>(22, this::drawEntry);
	private Path selected;
	private String data;
	private boolean replace;
	private boolean allowNewer;
	private JsonObject preview;
	private boolean busy;

	public ImportDialog(Path initial, Runnable onDone) {
		this.onDone = onDone;
		LocalFiles.ensureDirs();
		List<Path> list = new ArrayList<>(LocalFiles.listPackages());
		if (initial != null && !list.contains(initial)) {
			list.addFirst(initial);
		}
		files.setItems(list);
		files.setEmptyMessage(Component.translatable("wcm.import.no_files"));
		files.onSelect(p -> {
			selected = p;
			preview = null;
		});
		Path preselect = initial != null ? initial : list.isEmpty() ? null : list.getFirst();
		if (preselect != null) {
			files.select(preselect);
			selected = preselect;
		}
		previewList.setEmptyMessage(Component.translatable("wcm.import.empty_package"));
	}

	@Override
	protected Component title() {
		return Component.translatable("wcm.import.title");
	}

	@Override
	protected int preferredWidth() {
		return 440;
	}

	@Override
	protected int preferredHeight() {
		return 310;
	}

	private Backend backend() {
		return screen.backend();
	}

	@Override
	protected void layout(Rect content) {
		int bottom = content.bottom() - 20;
		if (preview == null) {
			files.setArea(new Rect(content.x(), content.y() + 12, content.w(), content.h() - 72));
			int bw = (content.w() - 12) / 4;
			Button mode = screen.add(Button.builder(Component.translatable(replace ? "wcm.import.mode.replace" : "wcm.import.mode.merge"), b -> {
				replace = !replace;
				screen.rebuild();
			}).bounds(content.x(), bottom - 24, content.w(), 20)
				.tooltip(Tooltip.create(Component.translatable(replace ? "wcm.import.mode.replace.tooltip" : "wcm.import.mode.merge.tooltip"))).build());
			mode.active = !busy;
			Button previewButton = screen.add(Button.builder(Component.translatable("wcm.import.preview"), b -> loadPreview())
				.bounds(content.x(), bottom, bw, 20).build());
			previewButton.active = selected != null && !busy;
			screen.add(Button.builder(Component.translatable("wcm.import.open_folder"), b -> Blaze3D.openPath(LocalFiles.importsDir()))
				.bounds(content.x() + bw + 4, bottom, bw * 2 + 4, 20).build());
			screen.add(Button.builder(Component.translatable("gui.cancel"), b -> close()).bounds(content.x() + 3 * (bw + 4), bottom, bw, 20).build());
		} else {
			previewList.setArea(new Rect(content.x(), content.y() + 26, content.w(), content.h() - 74));
			boolean blocked = J.objects(preview, "profiles").stream().anyMatch(p -> "BLOCKED_LOCAL_NEWER".equals(J.str(p, "action", "")));
			int bw = (content.w() - 8) / 3;
			if (blocked) {
				screen.add(Button.builder(Component.translatable(allowNewer ? "wcm.import.overwrite_newer.on" : "wcm.import.overwrite_newer.off"), b -> {
					allowNewer = !allowNewer;
					screen.rebuild();
				}).bounds(content.x(), bottom - 24, content.w(), 20)
					.tooltip(Tooltip.create(Component.translatable("wcm.import.overwrite_newer.tooltip"))).build());
			}
			Button importButton = screen.add(Button.builder(Component.translatable("wcm.import.confirm"), b -> commit())
				.bounds(content.x(), bottom, bw, 20).build());
			importButton.active = !busy;
			screen.add(Button.builder(Component.translatable("gui.back"), b -> {
				preview = null;
				screen.rebuild();
			}).bounds(content.x() + bw + 4, bottom, bw, 20).build());
			screen.add(Button.builder(Component.translatable("gui.cancel"), b -> close()).bounds(content.x() + 2 * (bw + 4), bottom, bw, 20).build());
		}
	}

	private void loadPreview() {
		if (selected == null || busy) {
			return;
		}
		try {
			data = LocalFiles.readPackage(selected);
		} catch (StorageException e) {
			screen.toast(Component.literal(e.getMessage()), Theme.ERROR);
			return;
		}
		busy = true;
		screen.rebuild();
		backend().request("import.preview", J.of("data", data, "mode", replace ? "REPLACE" : "MERGE"), r -> {
			busy = false;
			if (!r.ok()) {
				screen.toastError(r);
				screen.rebuild();
				return;
			}
			preview = r.body();
			List<JsonObject> rows = new ArrayList<>();
			for (JsonObject p : J.objects(preview, "profiles")) {
				JsonObject header = p.deepCopy();
				header.addProperty("_header", true);
				rows.add(header);
				rows.addAll(J.objects(p, "entries"));
			}
			previewList.setItems(rows);
			screen.rebuild();
		});
	}

	private void commit() {
		if (data == null || busy) {
			return;
		}
		busy = true;
		screen.rebuild();
		backend().request("import.commit", J.of("data", data, "mode", replace ? "REPLACE" : "MERGE", "allowOverwriteNewer", allowNewer), r -> {
			busy = false;
			if (!r.ok()) {
				screen.toastError(r);
				screen.rebuild();
				return;
			}
			int total = J.strings(r.body(), "created").size() + J.strings(r.body(), "merged").size() + J.strings(r.body(), "replaced").size();
			List<String> blocked = J.strings(r.body(), "blocked");
			close();
			screen.toast(blocked.isEmpty() ? Component.translatable("wcm.import.done", total)
				: Component.translatable("wcm.import.done_blocked", total, String.join(", ", blocked)), blocked.isEmpty() ? Theme.SUCCESS : Theme.WARNING);
			onDone.run();
		});
	}

	@Override
	protected void draw(GuiGraphicsExtractor g, Rect content, int mouseX, int mouseY, float delta) {
		if (preview == null) {
			Ui.text(g, font, Component.translatable("wcm.import.choose"), content.x(), content.y(), content.w(), Theme.TEXT_MUTED);
			files.draw(g, font, mouseX, mouseY);
			Ui.text(g, font, Component.translatable("wcm.import.drop_hint"), content.x(), files.area().bottom() + 3, content.w(), Theme.TEXT_FAINT);
			return;
		}
		Ui.text(g, font, Component.translatable("wcm.import.package_info", J.str(preview, "wcmVersion", "?"), J.str(preview, "minecraftVersion", "?")),
			content.x(), content.y(), content.w(), Theme.TEXT_MUTED);
		Ui.text(g, font, Component.translatable("wcm.import.legend"), content.x(), content.y() + 12, content.w(), Theme.TEXT_FAINT);
		previewList.draw(g, font, mouseX, mouseY);
	}

	private void drawFile(GuiGraphicsExtractor g, Path p, Rect r, boolean hovered, boolean sel) {
		Ui.text(g, font, p.getFileName().toString(), r.x() + 6, r.y() + 3, r.w() - 70, Theme.TEXT);
		String folder = p.getParent() == null ? "" : p.getParent().getFileName().toString();
		Ui.text(g, font, folder, r.right() - 64, r.y() + 3, 60, Theme.TEXT_FAINT);
	}

	private void drawEntry(GuiGraphicsExtractor g, JsonObject row, Rect r, boolean hovered, boolean sel) {
		if (J.bool(row, "_header", false)) {
			String action = J.str(row, "action", "");
			int color = action.equals("BLOCKED_LOCAL_NEWER") ? Theme.WARNING : Theme.ACCENT;
			Ui.text(g, font, J.str(row, "finalName", J.str(row, "name", "?")), r.x() + 6, r.y() + 2, r.w() - 100, Theme.TEXT);
			Ui.badge(g, font, Component.translatable("wcm.import.action." + action.toLowerCase(Locale.ROOT)).getString(), r.x() + 6, r.y() + 11, color);
			String compat = J.str(J.obj(row, "compatibility"), "status", "compatible");
			Ui.text(g, font, Component.translatable("wcm.compat.status." + compat), r.right() - 96, r.y() + 2, 92,
				compat.equals("compatible") ? Theme.SUCCESS : compat.equals("errors") ? Theme.ERROR : Theme.WARNING);
			return;
		}
		String status = J.str(row, "status", "");
		int color = switch (status) {
			case "NEW" -> Theme.SUCCESS;
			case "CONFLICT" -> Theme.CHANGED;
			case "MISSING_PROVIDER", "UNSUPPORTED_SETTING" -> Theme.TEXT_FAINT;
			case "INCOMPATIBLE_VALUE" -> Theme.ERROR;
			default -> Theme.TEXT_MUTED;
		};
		String label = Component.translatable("wcm.import.entry." + status.toLowerCase(Locale.ROOT)).getString();
		int bw = font.width(label) + 8;
		Ui.text(g, font, "   " + J.str(row, "key", "?"), r.x() + 6, r.y() + 2, r.w() - bw - 14, Theme.TEXT);
		Ui.badge(g, font, label, r.right() - bw - 4, r.y() + 2, color);
		String detail = status.equals("CONFLICT")
			? Values.display(Values.read(row, "existing")) + " → " + Values.display(Values.read(row, "incoming"))
			: Values.display(Values.read(row, "incoming"));
		Ui.text(g, font, "   " + J.str(row, "provider", "") + " · " + detail, r.x() + 6, r.y() + 12, r.w() - 12, color);
	}

	@Override
	public boolean mouseClicked(double x, double y, boolean doubleClick) {
		if (preview == null) {
			boolean handled = files.mouseClicked(x, y, doubleClick);
			if (handled) {
				screen.rebuild();
			}
			return handled;
		}
		return previewList.mouseClicked(x, y, doubleClick);
	}

	@Override
	public boolean mouseScrolled(double x, double y, double amount) {
		return preview == null ? files.mouseScrolled(x, y, amount) : previewList.mouseScrolled(x, y, amount);
	}

	@Override
	public boolean mouseDragged(double x, double y) {
		return preview == null ? files.mouseDragged(y) : previewList.mouseDragged(y);
	}

	@Override
	public void mouseReleased() {
		files.mouseReleased();
		previewList.mouseReleased();
	}

	@Override
	public boolean keyPressed(int key) {
		boolean handled = preview == null ? files.keyPressed(key) : previewList.keyPressed(key);
		if (handled && preview == null) {
			screen.rebuild();
		}
		return handled;
	}
}
