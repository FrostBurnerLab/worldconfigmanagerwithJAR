package dev.worldconfigmanager.fabric.client.screen.page;

import com.google.gson.JsonObject;
import dev.worldconfigmanager.api.ConfigCategory;
import dev.worldconfigmanager.fabric.client.ClientSettings;
import dev.worldconfigmanager.fabric.client.backend.J;
import dev.worldconfigmanager.fabric.client.screen.Page;
import dev.worldconfigmanager.fabric.client.screen.WcmScreen;
import dev.worldconfigmanager.fabric.client.screen.dialog.ConfirmDialog;
import dev.worldconfigmanager.fabric.client.screen.dialog.FormDialog;
import dev.worldconfigmanager.fabric.client.screen.dialog.PreviewDialog;
import dev.worldconfigmanager.fabric.client.ui.Outcomes;
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
import java.util.TreeSet;

/** History & Backups: filterable change history and the backup manager. */
public final class HistoryPage extends Page {
	private static final String[] OPERATIONS = {null, "SET_VALUE", "RESET_DEFAULT", "APPLY_PROFILE", "RESTORE_BACKUP"};
	private static final String[] PERIODS = {null, "P1D", "P7D", "P30D"};

	private final ScrollList<JsonObject> history = new ScrollList<>(22, this::drawHistory);
	private final ScrollList<JsonObject> backups = new ScrollList<>(22, this::drawBackup);
	private boolean showBackups;
	private int operation;
	private int period;
	private ConfigCategory category;
	private String provider;
	private final List<String> providers = new ArrayList<>();
	private int total;

	public HistoryPage() {
		history.setEmptyMessage(Ui.tr("wcm.history.empty"));
		backups.setEmptyMessage(Ui.tr("wcm.backups.empty"));
		history.onSelect(e -> screen.rebuild());
		backups.onSelect(e -> screen.rebuild());
	}

	@Override
	public Component title() {
		return Ui.tr("wcm.nav.history");
	}

	@Override
	public void refresh() {
		if (backend() == null) {
			return;
		}
		loading = true;
		if (showBackups) {
			if (!can("VIEW_BACKUPS")) {
				loading = false;
				return;
			}
			request("backups.list", new JsonObject(), r -> {
				loading = false;
				backups.setItems(J.objects(r.body(), "backups"));
				screen.rebuild();
			});
			return;
		}
		if (!can("VIEW_HISTORY")) {
			loading = false;
			return;
		}
		JsonObject filter = J.of("operation", OPERATIONS[operation], "category", category == null ? null : category.name(), "provider", provider,
			"from", PERIODS[period] == null ? null : java.time.Instant.now().minus(java.time.Duration.parse(PERIODS[period])).toString());
		request("history.query", J.of("filter", filter, "limit", 500), r -> {
			loading = false;
			List<JsonObject> entries = J.objects(r.body(), "entries");
			total = J.integer(r.body(), "total", entries.size());
			TreeSet<String> ps = new TreeSet<>(providers);
			entries.forEach(e -> ps.add(J.str(e, "provider", "")));
			providers.clear();
			providers.addAll(ps);
			history.setItems(entries);
			screen.rebuild();
		});
	}

	@Override
	protected void layout() {
		if (backend() == null) {
			return;
		}
		int half = (area.w() - 4) / 2;
		Button tabHistory = button(Ui.tr("wcm.history.tab.history"), area.x(), area.y(), half, b -> {
			showBackups = false;
			refresh();
			screen.rebuild();
		});
		tabHistory.active = showBackups;
		Button tabBackups = button(Ui.tr("wcm.history.tab.backups"), area.x() + half + 4, area.y(), half, b -> {
			showBackups = true;
			refresh();
			screen.rebuild();
		});
		tabBackups.active = !showBackups;
		Rect body = new Rect(area.x(), area.y() + 26, area.w(), area.h() - 26);
		if (showBackups && can("VIEW_BACKUPS")) {
			layoutBackups(body);
		} else if (!showBackups && can("VIEW_HISTORY")) {
			layoutHistory(body);
		}
	}

	private void layoutHistory(Rect body) {
		boolean twoRows = body.w() < 440;
		int perRow = twoRows ? 2 : 4;
		int w = (body.w() - (perRow - 1) * 4) / perRow;
		int[][] pos = new int[4][2];
		for (int i = 0; i < 4; i++) {
			pos[i][0] = body.x() + (i % perRow) * (w + 4);
			pos[i][1] = body.y() + (i / perRow) * 22;
		}
		int filtersH = twoRows ? 44 : 22;
		filterButton(Ui.tr(OPERATIONS[operation] == null ? "wcm.history.filter.all_operations" : "wcm.history.op." + OPERATIONS[operation].toLowerCase(Locale.ROOT)),
			pos[0][0], pos[0][1], w, b -> {
				operation = (operation + 1) % OPERATIONS.length;
				refresh();
			});
		filterButton(category == null ? Ui.tr("wcm.config.all_categories") : Ui.tr(category.translationKey()), pos[1][0], pos[1][1], w, b -> {
			ConfigCategory[] values = ConfigCategory.values();
			category = category == null ? values[0] : category.ordinal() + 1 < values.length ? values[category.ordinal() + 1] : null;
			refresh();
		});
		filterButton(Component.literal(provider == null ? Ui.tr("wcm.config.all_providers").getString() : provider), pos[2][0], pos[2][1], w, b -> {
			int i = provider == null ? -1 : providers.indexOf(provider);
			provider = i + 1 < providers.size() ? providers.get(i + 1) : null;
			refresh();
		});
		filterButton(Ui.tr(PERIODS[period] == null ? "wcm.history.period.all" : "wcm.history.period." + PERIODS[period].toLowerCase(Locale.ROOT)),
			pos[3][0], pos[3][1], w, b -> {
				period = (period + 1) % PERIODS.length;
				refresh();
			});
		history.setArea(new Rect(body.x(), body.y() + filtersH + 2, body.w(), body.h() - filtersH - 2 - 36));
		JsonObject sel = history.getSelected();
		boolean restorable = sel != null && !J.str(sel, "backupId", "").isEmpty() && can("RESTORE_BACKUPS");
		button(Ui.tr("wcm.history.restore_before"), Ui.tr("wcm.history.restore_before.tooltip"), body.x(), body.bottom() - 20,
			Math.min(220, body.w()), restorable, b -> previewRestore(J.str(sel, "backupId", "")));
	}

	private void filterButton(Component label, int x, int y, int w, Button.OnPress onPress) {
		button(Component.literal(Ui.ellipsize(font, label.getString(), w - 10)), x, y, w, onPress);
	}

	private void layoutBackups(Rect body) {
		JsonObject sel = backups.getSelected();
		boolean usable = sel != null && !J.bool(sel, "corrupt", false);
		String id = sel == null ? "" : J.str(sel, "id", "");
		List<ButtonSpec> specs = List.of(
			ButtonSpec.of(Ui.tr("wcm.backups.create"), can("CREATE_BACKUPS"), b -> screen.openDialog(new FormDialog(Ui.tr("wcm.backups.create"),
				List.of(new FormDialog.Field(Ui.tr("wcm.backups.label"), Ui.tr("wcm.backups.default_label").getString(), 64)),
				values -> request("backups.create", J.of("label", values.getFirst()), r -> {
					screen.toast(Ui.tr("wcm.toast.backup_created"), Theme.SUCCESS);
					refresh();
				})))),
			ButtonSpec.of(Ui.tr("wcm.backups.restore"), usable && can("RESTORE_BACKUPS"), b -> previewRestore(id)),
			ButtonSpec.of(Ui.tr("wcm.backups.compare"), usable && can("VIEW_PROFILES"), b -> {
				ComparePage.preset(J.of("kind", "current"), J.of("kind", "backup", "id", id));
				screen.navigate(WcmScreen.Nav.COMPARE);
			}),
			ButtonSpec.of(Ui.tr("wcm.backups.export"), usable && can("EXPORT"), b -> request("export.backup", J.of("id", id),
				r -> Outcomes.saveExport(screen, r.body()))),
			ButtonSpec.of(Ui.tr("wcm.backups.delete"), sel != null && can("DELETE_BACKUPS"), b -> screen.openDialog(new ConfirmDialog(
				Ui.tr("wcm.backups.delete"), Ui.tr("wcm.backups.delete.message"), Ui.tr("wcm.backups.delete"),
				() -> request("backups.delete", J.of("id", id), r -> {
					screen.toast(Ui.tr("wcm.toast.deleted"), Theme.SUCCESS);
					refresh();
				})))));
		int rowsH = buttonRowHeight(body.w(), specs);
		backups.setArea(new Rect(body.x(), body.y(), body.w(), body.h() - rowsH - 16));
		buttonRow(body.x(), body.bottom() - rowsH, body.w(), specs);
	}

	private void previewRestore(String backupId) {
		request("backups.preview", J.of("id", backupId), r -> screen.openDialog(new PreviewDialog(Ui.tr("wcm.backups.restore.title"), r.body(),
			Ui.tr("wcm.backups.restore"), () -> {
				Runnable run = () -> request("backups.restore", J.of("id", backupId), res -> {
					Outcomes.applyResult(screen, res.body());
					refresh();
				});
				if (ClientSettings.get().confirmApply() && backend().isServer()) {
					screen.openDialog(new ConfirmDialog(Ui.tr("wcm.backups.restore.title"), Ui.tr("wcm.backups.restore.warning"),
						Ui.tr("wcm.backups.restore"), run));
				} else {
					run.run();
				}
			})));
	}

	@Override
	public void draw(GuiGraphicsExtractor g, int mouseX, int mouseY, float delta) {
		if (backend() == null) {
			return;
		}
		boolean allowed = showBackups ? can("VIEW_BACKUPS") : can("VIEW_HISTORY");
		if (!allowed) {
			g.centeredText(font, Ui.tr("wcm.common.no_permission"), area.x() + area.w() / 2, area.y() + 60, Theme.TEXT_MUTED);
			return;
		}
		if (showBackups) {
			backups.draw(g, font, mouseX, mouseY);
			drawLoading(g, backups.area());
			Ui.text(g, font, Ui.tr("wcm.backups.footer", backups.items().size()), area.x(), backups.area().bottom() + 3, area.w(), Theme.TEXT_FAINT);
		} else {
			history.draw(g, font, mouseX, mouseY);
			drawLoading(g, history.area());
			Ui.text(g, font, Ui.tr("wcm.history.footer", history.items().size(), total), area.x(), history.area().bottom() + 3, area.w(), Theme.TEXT_FAINT);
		}
	}

	private void drawHistory(GuiGraphicsExtractor g, JsonObject e, Rect r, boolean hovered, boolean selected) {
		String result = J.str(e, "result", "SUCCESS");
		int color = switch (result) {
			case "SUCCESS" -> Theme.SUCCESS;
			case "ROLLED_BACK" -> Theme.WARNING;
			default -> Theme.ERROR;
		};
		String time = Ui.time(J.str(e, "timestamp", ""));
		int tw = font.width(time) + 8;
		Ui.text(g, font, J.str(e, "key", "?"), r.x() + 6, r.y() + 2, r.w() - tw - 12, Theme.TEXT);
		g.text(font, time, r.right() - tw, r.y() + 2, Theme.TEXT_FAINT, false);
		String change = J.bool(e, "masked", false) ? "***" : Values.display(Values.read(e, "old")) + " → " + Values.display(Values.read(e, "new"));
		String op = Ui.tr("wcm.history.op." + J.str(e, "operation", "").toLowerCase(Locale.ROOT)).getString();
		String actor = J.str(e, "actor", "");
		Ui.text(g, font, change + "  · " + op + (actor.isEmpty() ? "" : " · " + actor) + " · "
			+ Ui.tr("wcm.history.result." + result.toLowerCase(Locale.ROOT)).getString(), r.x() + 6, r.y() + 12, r.w() - 12, color);
	}

	private void drawBackup(GuiGraphicsExtractor g, JsonObject b, Rect r, boolean hovered, boolean selected) {
		boolean corrupt = J.bool(b, "corrupt", false);
		String label = J.str(b, "label", "");
		Ui.text(g, font, Ui.time(J.str(b, "createdAt", "")) + (label.isEmpty() ? "" : "  " + label), r.x() + 6, r.y() + 2, r.w() - 12,
			corrupt ? Theme.ERROR : Theme.TEXT);
		Component meta = corrupt ? Ui.tr("wcm.backups.corrupt", J.str(b, "problem", ""))
			: Ui.tr("wcm.backups.meta", Ui.tr("wcm.backup.reason." + J.str(b, "reason", "manual").toLowerCase(Locale.ROOT)), J.integer(b, "valueCount", 0),
				J.str(b, "id", "").length() >= 8 ? J.str(b, "id", "").substring(0, 8) : "");
		Ui.text(g, font, meta, r.x() + 6, r.y() + 12, r.w() - 12, corrupt ? Theme.ERROR : Theme.TEXT_FAINT);
	}

	private ScrollList<JsonObject> active() {
		return showBackups ? backups : history;
	}

	@Override
	public boolean mouseClicked(double x, double y, boolean doubleClick) {
		return active().mouseClicked(x, y, doubleClick);
	}

	@Override
	public boolean mouseScrolled(double x, double y, double amount) {
		return active().mouseScrolled(x, y, amount);
	}

	@Override
	public boolean mouseDragged(double x, double y) {
		return active().mouseDragged(y);
	}

	@Override
	public void mouseReleased() {
		active().mouseReleased();
	}

	@Override
	public boolean keyPressed(int key) {
		return active().keyPressed(key);
	}
}
