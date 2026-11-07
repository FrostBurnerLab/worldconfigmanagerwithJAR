package dev.worldconfigmanager.fabric.client.screen.page;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import dev.worldconfigmanager.fabric.client.ClientSettings;
import dev.worldconfigmanager.fabric.client.backend.J;
import dev.worldconfigmanager.fabric.client.screen.Page;
import dev.worldconfigmanager.fabric.client.screen.WcmScreen;
import dev.worldconfigmanager.fabric.client.screen.dialog.ActionsDialog;
import dev.worldconfigmanager.fabric.client.screen.dialog.ConfirmDialog;
import dev.worldconfigmanager.fabric.client.screen.dialog.FormDialog;
import dev.worldconfigmanager.fabric.client.screen.dialog.ImportDialog;
import dev.worldconfigmanager.fabric.client.screen.dialog.PreviewDialog;
import dev.worldconfigmanager.fabric.client.ui.Outcomes;
import dev.worldconfigmanager.fabric.client.ui.Rect;
import dev.worldconfigmanager.fabric.client.ui.ScrollList;
import dev.worldconfigmanager.fabric.client.ui.Theme;
import dev.worldconfigmanager.fabric.client.ui.Ui;
import dev.worldconfigmanager.fabric.client.ui.Values;
import dev.worldconfigmanager.core.profile.Profile;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.network.chat.Component;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

/** World Profile Manager: list, create, edit, apply, export and associate profiles. */
public final class ProfilesPage extends Page {
	private final ScrollList<JsonObject> list = new ScrollList<>(30, this::drawProfile);
	private final ScrollList<JsonObject> entries = new ScrollList<>(11, this::drawEntry);
	private String selectedId;
	private JsonObject detail;
	private Rect detailRect = new Rect(0, 0, 0, 0);

	public ProfilesPage() {
		list.onSelect(p -> {
			selectedId = J.str(p, "id", null);
			detail = null;
			loadDetail();
			screen.rebuild();
		});
		list.setEmptyMessage(Ui.tr("wcm.profiles.empty"));
		entries.setEmptyMessage(Ui.tr("wcm.profiles.no_entries"));
	}

	@Override
	public Component title() {
		return Ui.tr("wcm.nav.profiles");
	}

	@Override
	public void refresh() {
		if (backend() == null || !can("VIEW_PROFILES")) {
			return;
		}
		loading = true;
		request("profiles.list", new JsonObject(), r -> {
			loading = false;
			List<JsonObject> profiles = J.objects(r.body(), "profiles");
			list.setItems(profiles);
			profiles.stream().filter(p -> J.str(p, "id", "").equals(selectedId)).findFirst().ifPresentOrElse(list::select, () -> {
				selectedId = null;
				detail = null;
			});
			loadDetail();
			screen.rebuild();
		});
	}

	private void loadDetail() {
		if (selectedId == null) {
			return;
		}
		String id = selectedId;
		request("profiles.get", J.of("id", id), r -> {
			if (id.equals(selectedId)) {
				detail = r.body();
				entries.setItems(J.objects(detail, "entries"));
				screen.rebuild();
			}
		});
	}

	private JsonObject selected() {
		return list.getSelected();
	}

	@Override
	protected void layout() {
		if (backend() == null || !can("VIEW_PROFILES")) {
			return;
		}
		int listW = Math.max(120, Math.min(220, area.w() * 42 / 100));
		Rect[] cols = area.splitH(listW, 6);
		list.setArea(new Rect(cols[0].x(), cols[0].y() + 24, cols[0].w(), cols[0].h() - 24));
		detailRect = cols[1];
		boolean manage = can("MANAGE_PROFILES");
		int half = (cols[0].w() - 4) / 2;
		button(Ui.tr("wcm.profiles.new"), Ui.tr("wcm.profiles.new.tooltip"), cols[0].x(), cols[0].y(), half, manage, b -> createDialog());
		button(Ui.tr("wcm.profiles.import"), Ui.tr("wcm.profiles.import.tooltip"), cols[0].x() + half + 4, cols[0].y(), half, can("IMPORT"),
			b -> screen.openDialog(new ImportDialog(null, this::refresh)));

		JsonObject p = selected();
		Rect inner = detailRect.inset(6);
		int entriesTop = inner.y() + 94;
		if (p == null) {
			entries.setArea(new Rect(inner.x(), entriesTop, inner.w(), Math.max(20, inner.bottom() - 26 - entriesTop)));
			return;
		}
		int bw = (inner.w() - 4) / 2;
		button(Ui.tr("wcm.profiles.apply"), Ui.tr("wcm.profiles.apply.tooltip"), inner.x(), inner.bottom() - 20, bw, can("APPLY_PROFILES"),
			b -> previewAndApply());
		button(Ui.tr("wcm.profiles.actions"), Ui.tr("wcm.profiles.actions.tooltip"), inner.x() + bw + 4, inner.bottom() - 20, bw, true,
			b -> screen.openDialog(new ActionsDialog(Ui.tr("wcm.profiles.actions.title"), Component.literal(J.str(p, "name", "")), actions(p))));
		entries.setArea(new Rect(inner.x(), entriesTop, inner.w(), Math.max(20, inner.bottom() - 26 - entriesTop)));
	}

	private List<ButtonSpec> actions(JsonObject p) {
		boolean manage = can("MANAGE_PROFILES");
		boolean isDefault = J.bool(p, "default", false);
		boolean associated = J.bool(p, "associated", false);
		JsonObject entry = entries.getSelected();
		List<ButtonSpec> specs = new ArrayList<>(Arrays.asList(
			ButtonSpec.of(Ui.tr("wcm.profiles.rename"), manage, b -> renameDialog()),
			ButtonSpec.of(Ui.tr("wcm.profiles.edit"), manage, b -> editDialog()),
			ButtonSpec.of(Ui.tr("wcm.profiles.duplicate"), manage, b -> request("profiles.duplicate", J.of("id", selectedId), r -> {
				selectedId = J.str(r.body(), "id", selectedId);
				screen.toast(Ui.tr("wcm.toast.duplicated", J.str(r.body(), "name", "")), Theme.SUCCESS);
				refresh();
			})),
			new ButtonSpec(Ui.tr(associated ? "wcm.profiles.unassociate" : "wcm.profiles.associate"), Ui.tr("wcm.profiles.associate.tooltip"),
				manage, b -> request("profiles.associate", J.of("id", associated ? null : selectedId), r -> refresh())),
			new ButtonSpec(Ui.tr(isDefault ? "wcm.profiles.clear_default" : "wcm.profiles.set_default"), Ui.tr("wcm.profiles.default.tooltip"),
				manage, b -> request("profiles.setDefault", J.of("id", isDefault ? null : selectedId), r -> refresh())),
			new ButtonSpec(Ui.tr("wcm.profiles.update"), Ui.tr("wcm.profiles.update.tooltip"), manage, b -> confirmUpdate()),
			ButtonSpec.of(Ui.tr("wcm.profiles.compare"), can("VIEW_PROFILES"), b -> {
				ComparePage.preset(J.of("kind", "current"), J.of("kind", "profile", "id", selectedId));
				screen.navigate(WcmScreen.Nav.COMPARE);
			}),
			ButtonSpec.of(Ui.tr("wcm.profiles.export"), can("EXPORT"), b -> request("export.profiles",
				J.of("ids", singleton(selectedId)), r -> Outcomes.saveExport(screen, r.body()))),
			new ButtonSpec(Ui.tr("wcm.profiles.remove_entry"), Ui.tr("wcm.profiles.remove_entry.tooltip"), manage && entry != null,
				b -> removeEntry(entry)),
			ButtonSpec.of(Ui.tr("wcm.profiles.delete"), manage, b -> confirmDelete())));
		return specs;
	}

	private static JsonArray singleton(String id) {
		JsonArray a = new JsonArray();
		a.add(id);
		return a;
	}

	private void createDialog() {
		screen.openDialog(new FormDialog(Ui.tr("wcm.profiles.new.title"), List.of(
			new FormDialog.Field(Ui.tr("wcm.profiles.field.name"), "", Profile.MAX_NAME),
			new FormDialog.Field(Ui.tr("wcm.profiles.field.description"), "", Profile.MAX_DESCRIPTION)),
			values -> request("profiles.create", J.of("name", values.get(0), "description", values.get(1), "capture",
				Boolean.parseBoolean(values.get(2))), r -> {
				selectedId = J.str(r.body(), "id", null);
				screen.toast(Ui.tr("wcm.toast.profile_created", J.str(r.body(), "name", ""), J.integer(r.body(), "entryCount", 0)), Theme.SUCCESS);
				refresh();
			})).withToggle(Ui.tr("wcm.profiles.capture.toggle"), true));
	}

	private void renameDialog() {
		screen.openDialog(new FormDialog(Ui.tr("wcm.profiles.rename.title"),
			List.of(new FormDialog.Field(Ui.tr("wcm.profiles.field.name"), J.str(selected(), "name", ""), Profile.MAX_NAME)),
			values -> request("profiles.rename", J.of("id", selectedId, "name", values.getFirst()), r -> {
				screen.toast(Ui.tr("wcm.toast.renamed"), Theme.SUCCESS);
				refresh();
			})));
	}

	private void editDialog() {
		String tags = String.join(", ", J.strings(selected(), "tags"));
		screen.openDialog(new FormDialog(Ui.tr("wcm.profiles.edit.title"), List.of(
			new FormDialog.Field(Ui.tr("wcm.profiles.field.description"), J.str(selected(), "description", ""), Profile.MAX_DESCRIPTION),
			new FormDialog.Field(Ui.tr("wcm.profiles.field.tags"), tags, 300)),
			values -> {
				JsonArray tagArray = new JsonArray();
				Arrays.stream(values.get(1).split(",")).map(String::strip).filter(s -> !s.isEmpty()).distinct().forEach(tagArray::add);
				request("profiles.describe", J.of("id", selectedId, "description", values.get(0), "tags", tagArray), r -> {
					screen.toast(Ui.tr("wcm.toast.saved"), Theme.SUCCESS);
					refresh();
				});
			}) {
			@Override
			protected int preferredHeight() {
				return 126;
			}
		});
	}

	private void previewAndApply() {
		String id = selectedId;
		String name = J.str(selected(), "name", "");
		request("profiles.preview", J.of("id", id), r -> screen.openDialog(new PreviewDialog(Ui.tr("wcm.profiles.apply.title", name), r.body(),
			Ui.tr("wcm.profiles.apply.confirm"), () -> apply(id, name))));
	}

	private void apply(String id, String name) {
		Runnable run = () -> request("profiles.apply", J.of("id", id), r -> {
			Outcomes.applyResult(screen, r.body());
			refresh();
		});
		if (ClientSettings.get().confirmApply() && backend().isServer()) {
			screen.openDialog(new ConfirmDialog(Ui.tr("wcm.profiles.apply.title", name), Ui.tr("wcm.profiles.apply.warning"),
				Ui.tr("wcm.profiles.apply.confirm"), run));
		} else {
			run.run();
		}
	}

	private void confirmDelete() {
		String id = selectedId;
		screen.openDialog(new ConfirmDialog(Ui.tr("wcm.profiles.delete.title"), Ui.tr("wcm.profiles.delete.message", J.str(selected(), "name", "")),
			Ui.tr("wcm.profiles.delete"), () -> request("profiles.delete", J.of("id", id), r -> {
				screen.toast(Ui.tr("wcm.toast.deleted"), Theme.SUCCESS);
				selectedId = null;
				detail = null;
				refresh();
			})));
	}

	private void confirmUpdate() {
		String id = selectedId;
		screen.openDialog(new ConfirmDialog(Ui.tr("wcm.profiles.update"), Ui.tr("wcm.profiles.update.message", J.str(selected(), "name", "")),
			Ui.tr("wcm.profiles.update"), () -> request("profiles.capture", J.of("id", id), r -> {
				screen.toast(Ui.tr("wcm.toast.updated", J.integer(r.body(), "entryCount", 0)), Theme.SUCCESS);
				refresh();
			})));
	}

	private void removeEntry(JsonObject entry) {
		if (entry == null) {
			return;
		}
		JsonArray remove = new JsonArray();
		remove.add(J.of("provider", J.str(entry, "provider", ""), "key", J.str(entry, "key", "")));
		request("profiles.editEntries", J.of("id", selectedId, "remove", remove), r -> {
			entries.clearSelection();
			refresh();
		});
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
		list.draw(g, font, mouseX, mouseY);
		drawLoading(g, list.area());
		Ui.panel(g, detailRect, Theme.SURFACE);
		JsonObject p = selected();
		Rect inner = detailRect.inset(6);
		if (p == null) {
			Ui.wrapped(g, font, Ui.tr("wcm.profiles.select_hint"), inner.x(), inner.y() + 4, inner.w(), Theme.TEXT_MUTED, 6);
			return;
		}
		int y = inner.y();
		Ui.text(g, font, J.str(p, "name", ""), inner.x(), y, inner.w(), Theme.TEXT);
		y += 11;
		int bx = inner.x();
		if (J.bool(p, "active", false)) {
			bx += Ui.badge(g, font, Ui.tr("wcm.profile.badge.active").getString(), bx, y, Theme.SUCCESS) + 3;
		}
		if (J.bool(p, "associated", false)) {
			bx += Ui.badge(g, font, Ui.tr("wcm.profile.badge.associated").getString(), bx, y, Theme.INFO) + 3;
		}
		if (J.bool(p, "default", false)) {
			bx += Ui.badge(g, font, Ui.tr("wcm.profile.badge.default").getString(), bx, y, Theme.ACCENT) + 3;
		}
		for (String tag : J.strings(p, "tags")) {
			if (bx > inner.right() - 30) {
				break;
			}
			bx += Ui.badge(g, font, "#" + tag, bx, y, Theme.TEXT_MUTED) + 3;
		}
		y += 13;
		String description = J.str(p, "description", "");
		Ui.wrapped(g, font, Component.literal(description.isEmpty() ? Ui.tr("wcm.profiles.no_description").getString() : description),
			inner.x(), y, inner.w(), description.isEmpty() ? Theme.TEXT_FAINT : Theme.TEXT_MUTED, 2);
		y += 20;
		int half = inner.w() / 2;
		Ui.field(g, font, Ui.tr("wcm.profiles.created"), Ui.time(J.str(p, "createdAt", "")), inner.x(), y + 2, half, Theme.TEXT_MUTED);
		Ui.field(g, font, Ui.tr("wcm.profiles.modified"), Ui.time(J.str(p, "modifiedAt", "")), inner.x() + half, y + 2, half, Theme.TEXT_MUTED);
		y += 12;
		Ui.field(g, font, Ui.tr("wcm.profiles.versions"), "WCM " + J.str(p, "wcmVersion", "?") + " · MC " + nonEmpty(J.str(p, "minecraftVersion", ""))
			+ " · schema " + J.integer(p, "schemaVersion", 0), inner.x(), y + 2, inner.w(), Theme.TEXT_MUTED);
		y += 12;
		if (detail != null) {
			JsonObject compat = J.obj(detail, "compatibility");
			String status = J.str(compat, "status", "compatible");
			int color = status.equals("compatible") ? Theme.SUCCESS : status.equals("errors") ? Theme.ERROR : Theme.WARNING;
			List<JsonObject> issues = J.objects(compat, "issues").stream().filter(i -> !"INFO".equals(J.str(i, "severity", ""))).toList();
			Component line = Ui.tr("wcm.compat.status." + status);
			if (!issues.isEmpty()) {
				line = line.copy().append(": ").append(Ui.error(J.str(issues.getFirst(), "key", ""), J.strings(issues.getFirst(), "args")));
				if (issues.size() > 1) {
					line = line.copy().append(" (+" + (issues.size() - 1) + ")");
				}
			}
			Ui.text(g, font, line, inner.x(), y + 2, inner.w(), color);
		}
		Ui.text(g, font, Ui.tr("wcm.profiles.entries", J.integer(p, "entryCount", 0)), inner.x(), entries.area().y() - 10, inner.w(), Theme.TEXT_FAINT);
		entries.draw(g, font, mouseX, mouseY);
	}

	private static String nonEmpty(String s) {
		return s.isEmpty() ? "?" : s;
	}

	private void drawProfile(GuiGraphicsExtractor g, JsonObject p, Rect r, boolean hovered, boolean selected) {
		Ui.text(g, font, J.str(p, "name", "?"), r.x() + 6, r.y() + 4, r.w() - 30, Theme.TEXT);
		int marks = r.right() - 6;
		if (J.bool(p, "active", false)) {
			marks -= 6;
			g.fill(marks, r.y() + 5, marks + 4, r.y() + 9, Theme.SUCCESS);
		}
		if (J.bool(p, "default", false)) {
			marks -= 6;
			g.fill(marks, r.y() + 5, marks + 4, r.y() + 9, Theme.ACCENT);
		}
		if (J.bool(p, "associated", false)) {
			marks -= 6;
			g.fill(marks, r.y() + 5, marks + 4, r.y() + 9, Theme.INFO);
		}
		String tags = String.join(" ", J.strings(p, "tags").stream().map(t -> "#" + t).toList());
		Ui.text(g, font, Ui.tr("wcm.profiles.entry_count", J.integer(p, "entryCount", 0)).getString() + (tags.isEmpty() ? "" : "  " + tags),
			r.x() + 6, r.y() + 16, r.w() - 12, Theme.TEXT_FAINT);
	}

	private void drawEntry(GuiGraphicsExtractor g, JsonObject e, Rect r, boolean hovered, boolean selected) {
		String value = Values.display(Values.read(e, "value"));
		int vw = Math.min(r.w() / 3, font.width(value) + 4);
		Ui.text(g, font, J.str(e, "key", "?"), r.x() + 4, r.y() + 1, r.w() - vw - 10, Theme.TEXT_MUTED);
		Ui.text(g, font, value, r.right() - vw - 2, r.y() + 1, vw, Theme.ACCENT);
	}

	@Override
	public boolean mouseClicked(double x, double y, boolean doubleClick) {
		if (list.mouseClicked(x, y, doubleClick)) {
			entries.setFocused(false);
			return true;
		}
		if (entries.mouseClicked(x, y, doubleClick)) {
			list.setFocused(false);
			screen.rebuild();
			return true;
		}
		return false;
	}

	@Override
	public boolean mouseScrolled(double x, double y, double amount) {
		return list.mouseScrolled(x, y, amount) || entries.mouseScrolled(x, y, amount);
	}

	@Override
	public boolean mouseDragged(double x, double y) {
		return list.mouseDragged(y) || entries.mouseDragged(y);
	}

	@Override
	public void mouseReleased() {
		list.mouseReleased();
		entries.mouseReleased();
	}

	@Override
	public boolean keyPressed(int key) {
		if (list.keyPressed(key)) {
			return true;
		}
		if (entries.keyPressed(key)) {
			screen.rebuild();
			return true;
		}
		return false;
	}
}
