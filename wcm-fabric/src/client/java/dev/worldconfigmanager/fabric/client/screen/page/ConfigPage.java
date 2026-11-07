package dev.worldconfigmanager.fabric.client.screen.page;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import dev.worldconfigmanager.api.ConfigCategory;
import dev.worldconfigmanager.api.ConfigDefinition;
import dev.worldconfigmanager.api.ConfigValue;
import dev.worldconfigmanager.api.ValidationResult;
import dev.worldconfigmanager.api.ValueType;
import dev.worldconfigmanager.fabric.client.backend.J;
import dev.worldconfigmanager.fabric.client.screen.Page;
import dev.worldconfigmanager.fabric.client.screen.dialog.PreviewDialog;
import dev.worldconfigmanager.fabric.client.ui.Outcomes;
import dev.worldconfigmanager.fabric.client.ui.Rect;
import dev.worldconfigmanager.fabric.client.ui.ScrollList;
import dev.worldconfigmanager.fabric.client.ui.Theme;
import dev.worldconfigmanager.fabric.client.ui.Ui;
import dev.worldconfigmanager.fabric.client.ui.Values;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.components.Tooltip;
import net.minecraft.network.chat.Component;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.TreeSet;

/**
 * Configuration browser: search and filter all managed settings, edit values with typed editors and live validation,
 * stage changes, preview them and apply them in one transaction.
 */
public final class ConfigPage extends Page {
	private final ScrollList<JsonObject> list = new ScrollList<>(22, this::drawRow);
	private final Map<String, ConfigValue> pending = new LinkedHashMap<>();
	private final Set<String> resets = new HashSet<>();
	private List<JsonObject> all = List.of();
	private boolean editable;
	private String search = "";
	private ConfigCategory category;
	private String provider;
	private boolean modifiedOnly;
	private String selectedKey;
	private String editText = "";
	private Component editError;
	private Rect editorRect = new Rect(0, 0, 0, 0);
	private int editorFieldsTop;
	private boolean narrow;
	private int footerTextWidth = 80;
	private boolean editorOpen;

	public ConfigPage() {
		list.onSelect(e -> {
			selectedKey = id(e);
			editError = null;
			editText = Values.editable(effective(e).orElse(ConfigValue.of("")));
			editorOpen = true;
			screen.rebuild();
		});
	}

	private static String id(JsonObject e) {
		return J.str(e, "provider", "") + "|" + J.str(e, "key", "");
	}

	@Override
	public Component title() {
		return Ui.tr("wcm.nav.config");
	}

	@Override
	public void refresh() {
		if (backend() == null || !can("VIEW_SETTINGS")) {
			return;
		}
		loading = true;
		request("settings.list", new JsonObject(), r -> {
			loading = false;
			List<JsonObject> loaded = new ArrayList<>(J.objects(r.body(), "entries"));
			loaded.sort(java.util.Comparator.comparing((JsonObject e) -> J.str(e, "provider", ""))
				.thenComparing(e -> Ui.plain(J.obj(e, "name")), String.CASE_INSENSITIVE_ORDER));
			all = loaded;
			editable = J.bool(r.body(), "editable", false);
			applyFilter();
			screen.rebuild();
		});
	}

	private void applyFilter() {
		String q = search.toLowerCase(Locale.ROOT).strip();
		List<JsonObject> filtered = new ArrayList<>();
		for (JsonObject e : all) {
			if (category != null && !category.name().equals(J.str(e, "category", ""))) {
				continue;
			}
			if (provider != null && !provider.equals(J.str(e, "provider", ""))) {
				continue;
			}
			if (modifiedOnly && !pending.containsKey(id(e)) && !isNonDefault(e)) {
				continue;
			}
			if (!q.isEmpty()) {
				String hay = (Ui.plain(J.obj(e, "name")) + " " + J.str(e, "key", "") + " " + J.str(e, "provider", "") + " "
					+ Ui.plain(J.obj(e, "description"))).toLowerCase(Locale.ROOT);
				if (!hay.contains(q)) {
					continue;
				}
			}
			filtered.add(e);
		}
		list.setItems(filtered);
		list.setEmptyMessage(all.isEmpty() ? Ui.tr("wcm.config.none") : Ui.tr("wcm.config.no_match"));
		if (selectedKey != null) {
			filtered.stream().filter(e -> id(e).equals(selectedKey)).findFirst().ifPresent(list::select);
		}
	}

	private static boolean isNonDefault(JsonObject e) {
		Optional<ConfigValue> current = Values.read(e, "current");
		return current.isPresent() && !current.equals(Values.read(e, "default"));
	}

	private Optional<ConfigValue> effective(JsonObject e) {
		ConfigValue p = pending.get(id(e));
		return p != null ? Optional.of(p) : Values.read(e, "current");
	}

	private JsonObject selectedEntry() {
		if (selectedKey == null) {
			return null;
		}
		return all.stream().filter(e -> id(e).equals(selectedKey)).findFirst().orElse(null);
	}

	private List<String> providers() {
		TreeSet<String> set = new TreeSet<>();
		all.forEach(e -> set.add(J.str(e, "provider", "")));
		return new ArrayList<>(set);
	}

	@Override
	protected void layout() {
		if (backend() == null || !can("VIEW_SETTINGS")) {
			return;
		}
		narrow = area.w() < 380;
		boolean twoRowFilters = area.w() < 440;
		int barY = area.y();
		int searchW = twoRowFilters ? area.w() : Math.max(80, area.w() * 35 / 100);
		EditBox searchBox = new EditBox(font, area.x(), barY, searchW, 20, Ui.tr("wcm.config.search"));
		searchBox.setHint(Ui.tr("wcm.config.search.hint"));
		searchBox.setValue(search);
		searchBox.setResponder(v -> {
			search = v;
			applyFilter();
		});
		screen.add(searchBox);
		int bx = twoRowFilters ? area.x() : searchBox.getRight() + 4;
		int by = twoRowFilters ? barY + 22 : barY;
		int bw = (area.right() - bx - 8) / 3;
		Button categoryButton = screen.add(Button.builder(Component.literal(Ui.ellipsize(font,
			(category == null ? Ui.tr("wcm.config.all_categories") : Ui.tr(category.translationKey())).getString(), bw - 8)), b -> {
			ConfigCategory[] values = ConfigCategory.values();
			category = category == null ? values[0] : category.ordinal() + 1 < values.length ? values[category.ordinal() + 1] : null;
			applyFilter();
			screen.rebuild();
		}).bounds(bx, by, bw, 20).tooltip(Tooltip.create(Ui.tr("wcm.config.category.tooltip"))).build());
		screen.add(Button.builder(Component.literal(Ui.ellipsize(font, provider == null ? Ui.tr("wcm.config.all_providers").getString() : provider, bw - 8)), b -> {
			List<String> ps = providers();
			int i = provider == null ? -1 : ps.indexOf(provider);
			provider = i + 1 < ps.size() ? ps.get(i + 1) : null;
			applyFilter();
			screen.rebuild();
		}).bounds(categoryButton.getRight() + 4, by, bw, 20).tooltip(Tooltip.create(Ui.tr("wcm.config.provider.tooltip"))).build());
		int mx = categoryButton.getRight() + 4 + bw + 4;
		screen.add(Button.builder(Component.literal(Ui.ellipsize(font, Ui.tr(modifiedOnly ? "wcm.config.modified_only.on" : "wcm.config.modified_only.off").getString(),
			area.right() - mx - 8)), b -> {
			modifiedOnly = !modifiedOnly;
			applyFilter();
			screen.rebuild();
		}).bounds(mx, by, area.right() - mx, 20).tooltip(Tooltip.create(Ui.tr("wcm.config.modified.tooltip"))).build());

		int bodyTop = by + 24;
		Rect body = new Rect(area.x(), bodyTop, area.w(), area.bottom() - 24 - bodyTop);
		if (!narrow) {
			Rect[] cols = body.splitH(body.w() * 55 / 100, 6);
			list.setArea(cols[0]);
			editorRect = cols[1];
		} else if (editorOpen && selectedEntry() != null) {
			list.setArea(new Rect(0, 0, 0, 0));
			editorRect = body;
		} else {
			list.setArea(body);
			editorRect = new Rect(0, 0, 0, 0);
		}

		// Footer: status text on the left, buttons sized to their labels on the right.
		int footerY = area.bottom() - 20;
		Component[] labels = {Ui.tr("wcm.config.preview"), Ui.tr("wcm.config.apply"), Ui.tr("wcm.config.discard")};
		int x = area.right();
		int[] widths = new int[3];
		for (int i = 2; i >= 0; i--) {
			widths[i] = Math.max(44, font.width(labels[i]) + 14);
			x -= widths[i] + (i < 2 ? 4 : 0);
		}
		footerTextWidth = x - area.x() - 6;
		Button preview = button(labels[0], x, footerY, widths[0], b -> preview());
		preview.active = !pending.isEmpty();
		boolean canApply = !pending.isEmpty() && editable && can("EDIT_SETTINGS");
		button(labels[1], Ui.tr("wcm.config.apply.tooltip"), x + widths[0] + 4, footerY, widths[1], canApply, b -> apply());
		Button discard = button(labels[2], x + widths[0] + widths[1] + 8, footerY, widths[2], b -> {
			pending.clear();
			resets.clear();
			applyFilter();
			screen.rebuild();
		});
		discard.active = !pending.isEmpty();

		if (editorRect.w() > 0) {
			layoutEditor();
		}
	}

	private void layoutEditor() {
		JsonObject e = selectedEntry();
		if (e == null) {
			return;
		}
		Rect inner = editorRect.inset(6);
		int bottom = inner.bottom();
		if (narrow) {
			Component back = Ui.tr("wcm.config.back");
			int w = font.width(back) + 14;
			button(back, inner.right() - w, inner.y(), w, b -> {
				editorOpen = false;
				screen.rebuild();
			});
		}
		boolean canEdit = editable && can("EDIT_SETTINGS") && J.bool(e, "available", true) && !J.bool(e, "masked", false);
		int half = (inner.w() - 4) / 2;
		Optional<ConfigValue> def = Values.read(e, "default");
		button(Ui.tr("wcm.config.reset"), Ui.tr("wcm.config.reset.tooltip"), inner.x(), bottom - 20, half, canEdit && def.isPresent(), b -> {
			def.ifPresent(v -> stage(e, v, true));
			editText = def.map(Values::editable).orElse("");
			screen.rebuild();
		});
		button(Ui.tr("wcm.config.revert"), Ui.tr("wcm.config.revert.tooltip"), inner.x() + half + 4, bottom - 20, half,
			pending.containsKey(id(e)), b -> {
				pending.remove(id(e));
				resets.remove(id(e));
				editText = Values.editable(Values.read(e, "current").orElse(ConfigValue.of("")));
				editError = null;
				applyFilter();
				screen.rebuild();
			});
		int editorY = bottom - 46;
		editorFieldsTop = editorY;
		ValueType type;
		try {
			type = ValueType.fromId(J.str(e, "type", ""));
		} catch (IllegalArgumentException ex) {
			return;
		}
		Optional<ConfigValue> value = effective(e);
		switch (type) {
			case BOOLEAN -> {
				boolean v = value.orElse(ConfigValue.of(false)) instanceof ConfigValue.BoolValue(boolean b) && b;
				Button toggle = button(Component.translatable(v ? "options.on" : "options.off"), inner.x(), editorY, inner.w(),
					b -> stage(e, ConfigValue.of(!v), false));
				toggle.active = canEdit;
			}
			case ENUM -> {
				List<String> allowed = J.strings(J.obj(e, "constraints"), "allowed");
				String current = value.map(ConfigValue::display).orElse(allowed.isEmpty() ? "" : allowed.getFirst());
				Button cycle = button(Component.literal(current), inner.x(), editorY, inner.w(), b -> {
					int i = allowed.indexOf(current);
					if (!allowed.isEmpty()) {
						stage(e, ConfigValue.ofEnum(allowed.get((i + 1) % allowed.size())), false);
					}
				});
				cycle.active = canEdit && allowed.size() > 1;
			}
			case STRUCTURED -> {
				// Structured values are shown read-only; edit them through their owning mod or a profile import.
			}
			default -> {
				EditBox box = new EditBox(font, inner.x(), editorY, inner.w(), 20, Ui.tr("wcm.config.value"));
				box.setMaxLength(4096);
				box.setValue(editText);
				box.setEditable(canEdit);
				ValueType element = elementType(e);
				box.setResponder(text -> {
					editText = text;
					Optional<ConfigValue> parsed = Values.parse(type, element, text);
					if (parsed.isEmpty()) {
						editError = Ui.tr("wcm.config.parse_error", type.id());
						return;
					}
					ValidationResult result = Values.validate(Values.definition(e), parsed.get());
					if (!result.valid()) {
						editError = Ui.error(result.messageKey(), result.messageArgs());
						return;
					}
					editError = null;
					stageQuiet(e, parsed.get());
				});
				screen.add(box);
			}
		}
	}

	private static ValueType elementType(JsonObject e) {
		String t = J.str(J.obj(e, "constraints"), "elementType", null);
		try {
			return t == null ? null : ValueType.fromId(t);
		} catch (IllegalArgumentException ex) {
			return null;
		}
	}

	private void stage(JsonObject e, ConfigValue value, boolean reset) {
		stageQuiet(e, value);
		if (reset) {
			resets.add(id(e));
		}
		editText = Values.editable(value);
		applyFilter();
		screen.rebuild();
	}

	/** Stages without rebuilding widgets (keeps edit box focus while typing). */
	private void stageQuiet(JsonObject e, ConfigValue value) {
		resets.remove(id(e));
		if (Values.read(e, "current").map(value::equals).orElse(false)) {
			pending.remove(id(e));
		} else {
			pending.put(id(e), value);
		}
	}

	private JsonArray changes() {
		JsonArray array = new JsonArray();
		pending.forEach((k, v) -> {
			String[] parts = k.split("\\|", 2);
			array.add(J.of("provider", parts[0], "key", parts[1], "value", Values.write(v)));
		});
		return array;
	}

	private void preview() {
		request("settings.preview", J.of("changes", changes()), r -> screen.openDialog(new PreviewDialog(Ui.tr("wcm.config.preview.title"),
			r.body(), Ui.tr("wcm.config.apply"), editable && can("EDIT_SETTINGS") ? this::apply : null)));
	}

	private void apply() {
		boolean allResets = !pending.isEmpty() && resets.containsAll(pending.keySet());
		request("settings.apply", J.of("changes", changes(), "reset", allResets), r -> {
			Outcomes.applyResult(screen, r.body());
			if ("SUCCESS".equals(J.str(r.body(), "status", "")) || "NO_CHANGES".equals(J.str(r.body(), "status", ""))) {
				pending.clear();
				resets.clear();
			}
			refresh();
		});
	}

	@Override
	public void draw(GuiGraphicsExtractor g, int mouseX, int mouseY, float delta) {
		if (backend() == null) {
			return;
		}
		if (!can("VIEW_SETTINGS")) {
			g.centeredText(font, Ui.tr("wcm.common.no_permission"), area.x() + area.w() / 2, area.y() + 40, Theme.TEXT_MUTED);
			return;
		}
		if (list.area().w() > 0) {
			list.draw(g, font, mouseX, mouseY);
			drawLoading(g, list.area());
		}
		Component footer = pending.isEmpty()
			? Ui.tr(editable ? "wcm.config.footer.clean" : "wcm.config.footer.readonly", list.items().size(), all.size())
			: Ui.tr("wcm.config.footer.pending", pending.size());
		Ui.text(g, font, footer, area.x(), area.bottom() - 14, footerTextWidth, pending.isEmpty() ? Theme.TEXT_FAINT : Theme.CHANGED);
		if (editorRect.w() > 0) {
			drawEditor(g);
		}
	}

	private void drawEditor(GuiGraphicsExtractor g) {
		Ui.panel(g, editorRect, Theme.SURFACE);
		Rect inner = editorRect.inset(6);
		JsonObject e = selectedEntry();
		if (e == null) {
			Ui.wrapped(g, font, Ui.tr("wcm.config.select_hint"), inner.x(), inner.y() + 2, inner.w(), Theme.TEXT_MUTED, 8);
			return;
		}
		int y = inner.y();
		int nameW = narrow ? inner.w() - font.width(Ui.tr("wcm.config.back")) - 20 : inner.w();
		Ui.text(g, font, Ui.text(J.obj(e, "name")), inner.x(), y, nameW, Theme.TEXT);
		y += 10;
		Ui.text(g, font, J.str(e, "provider", "") + " · " + J.str(e, "key", ""), inner.x(), y, inner.w(), Theme.TEXT_FAINT);
		y += 12;
		Component description = Ui.text(J.obj(e, "description"));
		int maxLines = Math.max(0, (editorFieldsTop - y - 12 - 3 * 12) / (font.lineHeight + 1));
		if (!description.getString().isEmpty() && maxLines > 0) {
			y += Ui.wrapped(g, font, description, inner.x(), y, inner.w(), Theme.TEXT_MUTED, Math.min(4, maxLines)) + 3;
		}
		String type = J.str(e, "type", "");
		String scope = J.str(e, "scope", "WORLD").toLowerCase(Locale.ROOT);
		String reload = J.str(e, "reload", "HOT_RELOAD").toLowerCase(Locale.ROOT);
		String current = J.bool(e, "masked", false) ? Ui.tr("wcm.config.hidden").getString() : Values.display(Values.read(e, "current"));
		ConfigValue staged = pending.get(id(e));
		int limit = editorFieldsTop - 12;
		// Most important first, so the lines that fit are the useful ones.
		List<Object[]> rows = new ArrayList<>();
		rows.add(new Object[] {Ui.tr("wcm.config.current"), current, J.bool(e, "available", true) ? Theme.TEXT : Theme.ERROR});
		if (staged != null) {
			rows.add(new Object[] {Ui.tr("wcm.config.new"), Values.display(staged), Theme.CHANGED});
		}
		rows.add(new Object[] {Ui.tr("wcm.config.default"), Values.display(Values.read(e, "default")), Theme.TEXT_MUTED});
		rows.add(new Object[] {Ui.tr("wcm.config.type"), Ui.tr("wcm.type." + type).getString() + constraintText(e), Theme.TEXT_MUTED});
		rows.add(new Object[] {Ui.tr("wcm.config.scope"), Ui.tr("wcm.scope." + scope).getString() + " · " + Ui.tr("wcm.reload." + reload).getString(),
			reload.equals("hot_reload") ? Theme.TEXT_MUTED : Theme.WARNING});
		for (Object[] row : rows) {
			if (y + font.lineHeight > limit) {
				break;
			}
			y = Ui.field(g, font, (Component) row[0], (String) row[1], inner.x(), y, inner.w(), (Integer) row[2]);
		}
		if (editError != null) {
			Ui.text(g, font, editError, inner.x(), editorFieldsTop - 11, inner.w(), Theme.ERROR);
		} else if ("structured".equals(type)) {
			Ui.text(g, font, Ui.tr("wcm.config.structured_readonly"), inner.x(), editorFieldsTop + 4, inner.w(), Theme.TEXT_FAINT);
		} else if (!editable || !can("EDIT_SETTINGS")) {
			Ui.text(g, font, Ui.tr("wcm.config.readonly_hint"), inner.x(), editorFieldsTop - 11, inner.w(), Theme.TEXT_FAINT);
		}
	}

	private String constraintText(JsonObject e) {
		Optional<ConfigDefinition> def = Values.definition(e);
		if (def.isEmpty()) {
			return "";
		}
		var c = def.get().constraints();
		StringBuilder sb = new StringBuilder();
		if (c.min() != null || c.max() != null) {
			sb.append(" [").append(c.min() == null ? "…" : trim(c.min())).append(" – ").append(c.max() == null ? "…" : trim(c.max())).append(']');
		}
		if (c.maxLength() != null) {
			sb.append(" ≤").append(c.maxLength());
		}
		return sb.toString();
	}

	private static String trim(double d) {
		if (d >= Integer.MAX_VALUE) {
			return "max";
		}
		if (d <= Integer.MIN_VALUE) {
			return "min";
		}
		return d == Math.rint(d) ? Long.toString((long) d) : Double.toString(d);
	}

	private void drawRow(GuiGraphicsExtractor g, JsonObject e, Rect r, boolean hovered, boolean selected) {
		boolean staged = pending.containsKey(id(e));
		String value = J.bool(e, "masked", false) ? "***" : Values.display(effective(e));
		int vw = Math.min(r.w() / 3, font.width(value) + 6);
		Ui.text(g, font, Ui.text(J.obj(e, "name")), r.x() + 6, r.y() + 2, r.w() - vw - 18, staged ? Theme.CHANGED : Theme.TEXT);
		Ui.text(g, font, value, r.right() - vw - 4, r.y() + 2, vw, staged ? Theme.CHANGED : J.bool(e, "available", true) ? Theme.ACCENT : Theme.ERROR);
		String meta = J.str(e, "provider", "") + " · " + Ui.tr(ConfigCategory.valueOf(safeCategory(e)).translationKey()).getString();
		Ui.text(g, font, meta, r.x() + 6, r.y() + 12, r.w() - 40, Theme.TEXT_FAINT);
		int mx = r.right() - 8;
		if (!"HOT_RELOAD".equals(J.str(e, "reload", "HOT_RELOAD"))) {
			g.text(font, "⟳", mx, r.y() + 12, Theme.WARNING, false);
			mx -= 9;
		}
		if (isNonDefault(e)) {
			g.fill(mx + 2, r.y() + 14, mx + 6, r.y() + 18, Theme.INFO);
		}
	}

	private static String safeCategory(JsonObject e) {
		String c = J.str(e, "category", "OTHER_MODS");
		try {
			ConfigCategory.valueOf(c);
			return c;
		} catch (IllegalArgumentException ex) {
			return "OTHER_MODS";
		}
	}

	@Override
	public boolean back() {
		if (narrow && editorOpen) {
			editorOpen = false;
			screen.rebuild();
			return true;
		}
		return false;
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
