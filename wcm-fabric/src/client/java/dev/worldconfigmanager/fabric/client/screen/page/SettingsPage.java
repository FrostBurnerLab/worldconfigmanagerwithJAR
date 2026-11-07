package dev.worldconfigmanager.fabric.client.screen.page;

import com.google.gson.JsonObject;
import dev.worldconfigmanager.core.storage.StorageException;
import dev.worldconfigmanager.fabric.client.ClientSettings;
import dev.worldconfigmanager.fabric.client.backend.J;
import dev.worldconfigmanager.fabric.client.screen.Page;
import dev.worldconfigmanager.fabric.client.ui.Theme;
import dev.worldconfigmanager.fabric.client.ui.Ui;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.components.Tooltip;
import net.minecraft.network.chat.Component;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * General settings: client preferences (this computer) and the retention/permission rules of the active backend
 * (editable by the owner only; the server enforces this). The page is a scrollable list of fixed-height rows, so labels
 * and controls can never overlap at any GUI scale.
 */
public final class SettingsPage extends Page {
	private static final int ROW = 22;
	private static final String[] ROLES = {"PLAYER", "OPERATOR", "ADMIN", "OWNER"};
	private static final String[][] NUMBERS = {
		{"maxBackups", "wcm.settings.max_backups"}, {"maxBackupAgeDays", "wcm.settings.max_backup_age"},
		{"maxHistoryEntries", "wcm.settings.max_history"}, {"maxHistoryAgeDays", "wcm.settings.max_history_age"}};

	private enum Kind { HEADER, CLIENT, NUMBER, AUTO_APPLY, PERMISSION, NOTE }

	private record Row(Kind kind, String key, Component label) {
	}

	private JsonObject settings;
	private final Map<String, String> numbers = new LinkedHashMap<>();
	private final Map<String, String> permissions = new LinkedHashMap<>();
	private boolean autoApply;
	private boolean dirty;
	private int scroll;
	private final List<Row> rows = new ArrayList<>();

	@Override
	public Component title() {
		return Ui.tr("wcm.nav.settings");
	}

	@Override
	public void refresh() {
		rebuildRows();
		if (backend() == null || !can("VIEW_SETTINGS")) {
			return;
		}
		loading = true;
		request("wcm.settings.get", new JsonObject(), r -> {
			loading = false;
			load(r.body());
			screen.rebuild();
		});
	}

	private void load(JsonObject body) {
		settings = body;
		numbers.clear();
		for (String[] n : NUMBERS) {
			numbers.put(n[0], Integer.toString(J.integer(body, n[0], 0)));
		}
		autoApply = J.bool(body, "autoApplyDefault", false);
		permissions.clear();
		J.obj(body, "permissions").entrySet().forEach(e -> permissions.put(e.getKey(), e.getValue().getAsString()));
		dirty = false;
		rebuildRows();
	}

	private void rebuildRows() {
		rows.clear();
		rows.add(new Row(Kind.HEADER, null, Ui.tr("wcm.settings.client")));
		rows.add(new Row(Kind.CLIENT, "pause", null));
		rows.add(new Row(Kind.CLIENT, "confirm", null));
		if (backend() == null) {
			return;
		}
		rows.add(new Row(Kind.HEADER, null, Ui.tr(backend().isServer() ? "wcm.settings.server" : "wcm.settings.local")));
		if (!can("VIEW_SETTINGS")) {
			rows.add(new Row(Kind.NOTE, null, Ui.tr("wcm.common.no_permission")));
			return;
		}
		if (settings == null) {
			rows.add(new Row(Kind.NOTE, null, Ui.tr("wcm.common.loading")));
			return;
		}
		if (!editable()) {
			rows.add(new Row(Kind.NOTE, null, Ui.tr("wcm.settings.owner_only")));
		}
		for (String[] n : NUMBERS) {
			rows.add(new Row(Kind.NUMBER, n[0], Ui.tr(n[1])));
		}
		rows.add(new Row(Kind.AUTO_APPLY, null, null));
		rows.add(new Row(Kind.HEADER, null, Ui.tr("wcm.settings.permissions")));
		for (String action : permissions.keySet()) {
			rows.add(new Row(Kind.PERMISSION, action, Ui.tr("wcm.permission." + action.toLowerCase(Locale.ROOT))));
		}
	}

	private boolean editable() {
		return can("MANAGE_WCM_SETTINGS");
	}

	private int visibleRows() {
		return Math.max(1, (area.h() - 38) / ROW);
	}

	@Override
	protected void layout() {
		scroll = Math.max(0, Math.min(scroll, rows.size() - visibleRows()));
		boolean edit = editable();
		int controlW = Math.max(60, Math.min(110, area.w() / 3));
		int y = area.y();
		for (int i = scroll; i < Math.min(rows.size(), scroll + visibleRows()); i++, y += ROW) {
			Row row = rows.get(i);
			switch (row.kind()) {
				case CLIENT -> {
					ClientSettings c = ClientSettings.get();
					boolean pause = row.key().equals("pause");
					String key = pause ? (c.pauseMenuButton() ? "wcm.settings.pause_button.on" : "wcm.settings.pause_button.off")
						: (c.confirmApply() ? "wcm.settings.confirm.on" : "wcm.settings.confirm.off");
					button(Component.literal(Ui.ellipsize(font, Ui.tr(key).getString(), area.w() - 10)), area.x(), y, area.w(),
						b -> saveClient(pause ? new ClientSettings(!c.pauseMenuButton(), c.confirmApply()) : new ClientSettings(c.pauseMenuButton(), !c.confirmApply())));
				}
				case NUMBER -> {
					EditBox box = new EditBox(font, area.right() - controlW, y + 1, controlW, 18, row.label());
					box.setValue(numbers.get(row.key()));
					box.setMaxLength(6);
					box.setEditable(edit);
					box.setResponder(v -> {
						if (!v.chars().allMatch(Character::isDigit)) {
							box.setValue(v.replaceAll("\\D", ""));
							return;
						}
						numbers.put(row.key(), v);
						markDirty();
					});
					screen.add(box);
				}
				case AUTO_APPLY -> {
					Button b = button(Component.literal(Ui.ellipsize(font, Ui.tr(autoApply ? "wcm.settings.auto_apply.on" : "wcm.settings.auto_apply.off").getString(),
						area.w() - 10)), Ui.tr("wcm.settings.auto_apply.tooltip"), area.x(), y, area.w(), edit, btn -> {
							autoApply = !autoApply;
							markDirty();
							screen.rebuild();
						});
					b.active = edit;
				}
				case PERMISSION -> {
					String role = permissions.get(row.key());
					Button b = button(Ui.tr("wcm.role." + role.toLowerCase(Locale.ROOT)), area.right() - controlW, y, controlW, btn -> {
						int idx = Arrays.asList(ROLES).indexOf(role);
						permissions.put(row.key(), ROLES[(idx + 1) % ROLES.length]);
						markDirty();
						screen.rebuild();
					});
					b.active = edit && !row.key().equals("MANAGE_WCM_SETTINGS");
					if (row.key().equals("MANAGE_WCM_SETTINGS")) {
						b.setTooltip(Tooltip.create(Ui.tr("wcm.settings.owner_fixed")));
					}
				}
				default -> {
					// Headers and notes are drawn, not widgets.
				}
			}
		}
		if (settings != null) {
			int half = (area.w() - 4) / 2;
			Button save = button(Ui.tr("wcm.settings.save"), area.x(), area.bottom() - 20, half, b -> save());
			save.active = edit && dirty;
			Button revert = button(Ui.tr("wcm.config.discard"), area.x() + half + 4, area.bottom() - 20, half, b -> {
				load(settings);
				screen.rebuild();
			});
			revert.active = dirty;
		}
	}

	private void markDirty() {
		dirty = true;
	}

	private void saveClient(ClientSettings updated) {
		try {
			ClientSettings.save(updated);
			screen.toast(Ui.tr("wcm.toast.saved"), Theme.SUCCESS);
		} catch (StorageException e) {
			screen.toast(Component.literal(e.getMessage()), Theme.ERROR);
		}
		screen.rebuild();
	}

	private void save() {
		JsonObject body = new JsonObject();
		for (String[] n : NUMBERS) {
			try {
				body.addProperty(n[0], Integer.parseInt(numbers.get(n[0])));
			} catch (NumberFormatException e) {
				screen.toast(Ui.tr("wcm.settings.invalid_number", Ui.tr(n[1])), Theme.ERROR);
				return;
			}
		}
		body.addProperty("autoApplyDefault", autoApply);
		JsonObject perms = new JsonObject();
		permissions.forEach(perms::addProperty);
		body.add("permissions", perms);
		request("wcm.settings.set", body, r -> {
			load(r.body());
			screen.toast(Ui.tr("wcm.toast.saved"), Theme.SUCCESS);
			screen.rebuild();
		});
	}

	@Override
	public void draw(GuiGraphicsExtractor g, int mouseX, int mouseY, float delta) {
		int controlW = Math.max(60, Math.min(110, area.w() / 3));
		int y = area.y();
		for (int i = scroll; i < Math.min(rows.size(), scroll + visibleRows()); i++, y += ROW) {
			Row row = rows.get(i);
			switch (row.kind()) {
				case HEADER -> {
					Ui.text(g, font, row.label(), area.x(), y + 10, area.w(), Theme.ACCENT);
					g.fill(area.x(), y + 20, area.right(), y + 21, Theme.BORDER);
				}
				case NOTE -> Ui.text(g, font, row.label(), area.x(), y + 7, area.w(), Theme.TEXT_FAINT);
				case NUMBER, PERMISSION -> Ui.text(g, font, row.label(), area.x(), y + 6, area.w() - controlW - 8, Theme.TEXT_MUTED);
				default -> {
				}
			}
		}
		if (rows.size() > visibleRows()) {
			Ui.text(g, font, Ui.tr("wcm.settings.scroll_hint", scroll + 1, Math.min(rows.size(), scroll + visibleRows()), rows.size()),
				area.x(), area.bottom() - (settings != null ? 33 : 10), area.w(), Theme.TEXT_FAINT);
		}
	}

	@Override
	public boolean mouseScrolled(double x, double y, double amount) {
		if (!area.contains(x, y) || rows.size() <= visibleRows()) {
			return false;
		}
		scroll = Math.max(0, Math.min(rows.size() - visibleRows(), scroll - (int) Math.signum(amount)));
		screen.rebuild();
		return true;
	}
}
