package dev.worldconfigmanager.fabric.client.screen.page;

import com.google.gson.JsonObject;
import com.mojang.blaze3d.Blaze3D;
import dev.worldconfigmanager.fabric.client.LocalFiles;
import dev.worldconfigmanager.fabric.client.backend.J;
import dev.worldconfigmanager.fabric.client.screen.Page;
import dev.worldconfigmanager.fabric.client.ui.Rect;
import dev.worldconfigmanager.fabric.client.ui.ScrollList;
import dev.worldconfigmanager.fabric.client.ui.Theme;
import dev.worldconfigmanager.fabric.client.ui.Ui;
import net.fabricmc.loader.api.FabricLoader;
import net.fabricmc.loader.api.ModContainer;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.network.chat.Component;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * Integrations: registered configuration providers with their capabilities, and installed mods without a WCM
 * integration (shown as metadata-only; WCM never edits their files).
 */
public final class IntegrationsPage extends Page {
	private static final Set<String> IGNORED = Set.of("minecraft", "java", "fabricloader", "fabric-api", "mixinextras", "worldconfigmanager");

	private final ScrollList<JsonObject> providers = new ScrollList<>(34, this::drawProvider);
	private final ScrollList<ModContainer> unsupported = new ScrollList<>(12, this::drawMod);

	public IntegrationsPage() {
		providers.setEmptyMessage(Ui.tr("wcm.integrations.none"));
		unsupported.setEmptyMessage(Ui.tr("wcm.integrations.all_supported"));
	}

	@Override
	public Component title() {
		return Ui.tr("wcm.nav.integrations");
	}

	@Override
	public void refresh() {
		if (backend() == null) {
			return;
		}
		loading = true;
		request("providers.list", new JsonObject(), r -> {
			loading = false;
			List<JsonObject> list = J.objects(r.body(), "providers");
			providers.setItems(list);
			Set<String> integrated = new HashSet<>();
			list.forEach(p -> integrated.add(J.str(p, "modId", "")));
			List<ModContainer> mods = new ArrayList<>();
			for (ModContainer mod : FabricLoader.getInstance().getAllMods()) {
				String id = mod.getMetadata().getId();
				if (!IGNORED.contains(id) && !integrated.contains(id) && !id.startsWith("fabric-") && mod.getContainingMod().isEmpty()) {
					mods.add(mod);
				}
			}
			mods.sort(Comparator.comparing(m -> m.getMetadata().getName(), String.CASE_INSENSITIVE_ORDER));
			unsupported.setItems(mods);
			screen.rebuild();
		});
	}

	@Override
	protected void layout() {
		Rect[] parts = new Rect(area.x(), area.y() + 12, area.w(), area.h() - 12 - 24).splitV((area.h() - 36) * 62 / 100, 16);
		providers.setArea(parts[0]);
		unsupported.setArea(parts[1]);
		int w = (area.w() - 4) / 2;
		button(Ui.tr("wcm.integrations.open_exports"), area.x(), area.bottom() - 20, w, b -> {
			LocalFiles.ensureDirs();
			Blaze3D.openPath(LocalFiles.exportsDir());
		});
		button(Ui.tr("wcm.import.open_folder"), area.x() + w + 4, area.bottom() - 20, w, b -> {
			LocalFiles.ensureDirs();
			Blaze3D.openPath(LocalFiles.importsDir());
		});
	}

	@Override
	public void draw(GuiGraphicsExtractor g, int mouseX, int mouseY, float delta) {
		if (backend() == null) {
			return;
		}
		Ui.text(g, font, Ui.tr("wcm.integrations.providers", providers.items().size()), area.x(), area.y(), area.w(), Theme.TEXT_MUTED);
		providers.draw(g, font, mouseX, mouseY);
		drawLoading(g, providers.area());
		Ui.text(g, font, Ui.tr("wcm.integrations.unsupported", unsupported.items().size()), area.x(), unsupported.area().y() - 12, area.w(), Theme.TEXT_MUTED);
		unsupported.draw(g, font, mouseX, mouseY);
	}

	private void drawProvider(GuiGraphicsExtractor g, JsonObject p, Rect r, boolean hovered, boolean selected) {
		boolean available = J.bool(p, "available", false);
		Ui.text(g, font, Ui.text(J.obj(p, "name")), r.x() + 6, r.y() + 3, r.w() / 2, Theme.TEXT);
		String status = Ui.tr(available ? "wcm.integrations.available" : "wcm.integrations.unavailable").getString();
		int sw = font.width(status) + 8;
		Ui.badge(g, font, status, r.right() - sw - 4, r.y() + 3, available ? Theme.SUCCESS : Theme.TEXT_FAINT);
		Ui.text(g, font, J.str(p, "id", "") + " · " + J.str(p, "modId", "") + " v" + J.str(p, "version", "?"), r.x() + 6, r.y() + 13,
			r.w() - 12, Theme.TEXT_FAINT);
		String scopes = String.join(", ", J.strings(p, "scopes").stream().map(s -> Ui.tr("wcm.scope." + s.toLowerCase(java.util.Locale.ROOT)).getString()).toList());
		Ui.text(g, font, Ui.tr("wcm.integrations.details", J.integer(p, "settings", 0), scopes,
			Ui.tr(J.bool(p, "transactional", false) ? "wcm.integrations.transactional" : "wcm.integrations.rollback"), J.integer(p, "apiMajor", 0)),
			r.x() + 6, r.y() + 23, r.w() - 12, Theme.TEXT_MUTED);
	}

	private void drawMod(GuiGraphicsExtractor g, ModContainer mod, Rect r, boolean hovered, boolean selected) {
		String text = mod.getMetadata().getName() + " (" + mod.getMetadata().getId() + " " + mod.getMetadata().getVersion().getFriendlyString() + ")";
		Ui.text(g, font, text, r.x() + 6, r.y() + 2, r.w() - 120, Theme.TEXT_MUTED);
		Ui.text(g, font, Ui.tr("wcm.integrations.metadata_only"), r.right() - 112, r.y() + 2, 108, Theme.TEXT_FAINT);
	}

	@Override
	public boolean mouseClicked(double x, double y, boolean doubleClick) {
		return providers.mouseClicked(x, y, doubleClick) || unsupported.mouseClicked(x, y, doubleClick);
	}

	@Override
	public boolean mouseScrolled(double x, double y, double amount) {
		return providers.mouseScrolled(x, y, amount) || unsupported.mouseScrolled(x, y, amount);
	}

	@Override
	public boolean mouseDragged(double x, double y) {
		return providers.mouseDragged(y) || unsupported.mouseDragged(y);
	}

	@Override
	public void mouseReleased() {
		providers.mouseReleased();
		unsupported.mouseReleased();
	}

	@Override
	public boolean keyPressed(int key) {
		return providers.keyPressed(key) || unsupported.keyPressed(key);
	}
}
