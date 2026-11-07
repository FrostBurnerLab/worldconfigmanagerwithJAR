package dev.worldconfigmanager.fabric.client.screen;

import dev.worldconfigmanager.fabric.client.ClientSession;
import dev.worldconfigmanager.fabric.client.backend.Backend;
import dev.worldconfigmanager.fabric.client.backend.Response;
import dev.worldconfigmanager.fabric.client.screen.dialog.ImportDialog;
import dev.worldconfigmanager.fabric.client.screen.page.ComparePage;
import dev.worldconfigmanager.fabric.client.screen.page.ConfigPage;
import dev.worldconfigmanager.fabric.client.screen.page.DashboardPage;
import dev.worldconfigmanager.fabric.client.screen.page.HistoryPage;
import dev.worldconfigmanager.fabric.client.screen.page.IntegrationsPage;
import dev.worldconfigmanager.fabric.client.screen.page.ProfilesPage;
import dev.worldconfigmanager.fabric.client.screen.page.SettingsPage;
import dev.worldconfigmanager.fabric.client.ui.Rect;
import dev.worldconfigmanager.fabric.client.ui.Theme;
import dev.worldconfigmanager.fabric.client.ui.Ui;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.Tooltip;
import net.minecraft.client.gui.components.events.GuiEventListener;
import net.minecraft.client.gui.narration.NarratableEntry;
import net.minecraft.client.gui.components.Renderable;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.input.KeyEvent;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.network.chat.Component;

import java.nio.file.Path;
import java.util.List;
import java.util.Optional;
import java.util.function.Supplier;

/**
 * The WorldConfig Manager control centre: a sidebar with the seven modules, a header with world and connection
 * status, the active page and optional modal dialogs. All data comes from the active {@link Backend}.
 */
public final class WcmScreen extends Screen {
	/** Main navigation entries. */
	public enum Nav {
		DASHBOARD("wcm.nav.dashboard", DashboardPage::new),
		PROFILES("wcm.nav.profiles", ProfilesPage::new),
		CONFIG("wcm.nav.config", ConfigPage::new),
		COMPARE("wcm.nav.compare", ComparePage::new),
		HISTORY("wcm.nav.history", HistoryPage::new),
		INTEGRATIONS("wcm.nav.integrations", IntegrationsPage::new),
		SETTINGS("wcm.nav.settings", SettingsPage::new);

		private final String key;
		private final Supplier<Page> factory;

		Nav(String key, Supplier<Page> factory) {
			this.key = key;
			this.factory = factory;
		}
	}

	private static Nav lastNav = Nav.DASHBOARD;

	private final Screen parent;
	private Nav nav = lastNav;
	private Page page;
	private Dialog dialog;
	private boolean preferLocal;
	private Component toast;
	private int toastColor;
	private long toastUntil;
	private Rect sidebar = new Rect(0, 0, 0, 0);
	private Rect header = new Rect(0, 0, 0, 0);
	private Rect content = new Rect(0, 0, 0, 0);
	private static final int NAV_TOP = 30;
	private int navStep = 22;
	private int headerActionsX;

	public WcmScreen(Screen parent) {
		super(Component.translatable("wcm.title"));
		this.parent = parent;
		this.preferLocal = ClientSession.serverBackend().isEmpty();
	}

	public Font font() {
		return font;
	}

	/** @return the area in which the active page is drawn */
	public Rect contentArea() {
		return content;
	}

	/** @return the backend the UI talks to: the server when available (unless local is selected), else local */
	public Backend backend() {
		Optional<Backend> server = ClientSession.serverBackend();
		if (!preferLocal && server.isPresent()) {
			return server.get();
		}
		return ClientSession.localBackend().orElse(server.orElse(null));
	}

	public boolean isActive(Page p) {
		return p == page && minecraft.gui.screen() == this;
	}

	public <T extends GuiEventListener & Renderable & NarratableEntry> T add(T widget) {
		return addRenderableWidget(widget);
	}

	/** Gives keyboard focus to a widget. */
	public void focus(GuiEventListener widget) {
		setInitialFocus(widget);
	}

	public void navigate(Nav target) {
		nav = target;
		lastNav = target;
		page = target.factory.get();
		dialog = null;
		rebuildWidgets();
		page.refresh();
	}

	public void openDialog(Dialog d) {
		dialog = d;
		rebuildWidgets();
	}

	public void closeDialog() {
		dialog = null;
		rebuildWidgets();
	}

	public void rebuild() {
		rebuildWidgets();
	}

	/** A change happened elsewhere (another player or a command): reload the current page. */
	public void onRemoteChange() {
		if (page != null && dialog == null) {
			page.refresh();
		}
	}

	public void toast(Component message, int color) {
		toast = message;
		toastColor = color;
		toastUntil = System.currentTimeMillis() + 6000;
	}

	public void toastError(Response response) {
		toast(Ui.error(response.errorKey(), response.errorArgs()), Theme.ERROR);
	}

	@Override
	protected void init() {
		int widest = 0;
		for (Nav n : Nav.values()) {
			widest = Math.max(widest, font.width(Component.translatable(n.key)));
		}
		int sideW = Math.max(92, Math.min(widest + 20, width / 3));
		sidebar = new Rect(0, 0, sideW, height);
		header = new Rect(sideW, 0, width - sideW, 30);
		content = new Rect(sideW + 8, 36, Math.max(40, width - sideW - 16), Math.max(40, height - 36 - 18));
		if (page == null) {
			page = nav.factory.get();
			page.init(this, content);
			page.refresh();
		} else {
			page.init(this, content);
		}
		if (dialog != null) {
			clearWidgets();
			dialog.init(this, width, height);
			return;
		}
		int navTop = NAV_TOP;
		int step = 22;
		if (navTop + Nav.values().length * step > height - 4) {
			step = Math.max(14, (height - navTop - 4) / Nav.values().length);
		}
		navStep = step;
		int y = navTop;
		for (Nav n : Nav.values()) {
			Button b = Button.builder(Component.translatable(n.key), btn -> navigate(n)).bounds(4, y, sideW - 8, step - 2).build();
			b.active = n != nav;
			addRenderableWidget(b);
			y += step;
		}
		// Header actions (right to left): Done, backend switch.
		Component done = Component.translatable("gui.done");
		int doneW = font.width(done) + 12;
		int hx = header.right() - doneW - 5;
		addRenderableWidget(Button.builder(done, b -> onClose()).bounds(hx, 5, doneW, 20).build());
		boolean hasServer = ClientSession.serverBackend().isPresent();
		boolean local = preferLocal || !hasServer;
		Component modeLabel = Component.translatable(local ? "wcm.backend.local" : "wcm.backend.server");
		int modeW = font.width(modeLabel) + 12;
		hx -= modeW + 4;
		Button toggle = Button.builder(modeLabel, b -> {
			preferLocal = !preferLocal;
			navigate(nav);
		}).bounds(hx, 5, modeW, 20).tooltip(Tooltip.create(Component.translatable("wcm.backend.tooltip"))).build();
		toggle.active = hasServer && ClientSession.localBackend().isPresent();
		addRenderableWidget(toggle);
		headerActionsX = hx;
	}

	@Override
	public void extractBackground(GuiGraphicsExtractor g, int mouseX, int mouseY, float delta) {
		g.fill(0, 0, width, height, Theme.BACKGROUND);
		g.fill(sidebar.x(), sidebar.y(), sidebar.right(), sidebar.bottom(), Theme.SIDEBAR);
		g.fill(sidebar.right() - 1, 0, sidebar.right(), height, Theme.BORDER);
		Ui.text(g, font, Component.translatable("wcm.title.short"), 6, 6, sidebar.w() - 12, Theme.ACCENT);
		Ui.text(g, font, Component.translatable("wcm.title"), 6, 17, sidebar.w() - 12, Theme.TEXT_FAINT);
		int y = NAV_TOP;
		for (Nav n : Nav.values()) {
			if (n == nav) {
				g.fill(0, y, 3, y + navStep - 2, Theme.ACCENT);
			}
			y += navStep;
		}
		drawHeader(g);
		if (page != null) {
			page.draw(g, dialog == null ? mouseX : -1, dialog == null ? mouseY : -1, delta);
		}
		if (dialog != null) {
			g.fill(0, 0, width, height, Theme.OVERLAY);
			dialog.drawFrame(g, mouseX, mouseY, delta);
		}
	}

	private void drawHeader(GuiGraphicsExtractor g) {
		g.fill(header.x(), header.y(), header.right(), header.bottom(), Theme.SURFACE);
		g.fill(header.x(), header.bottom() - 1, header.right(), header.bottom(), Theme.BORDER);
		Backend backend = backend();
		Component status = statusLabel(backend);
		int sw = font.width(status) + 8;
		int sx = headerActionsX - sw - 6;
		int titleW = Math.max(20, sx - header.x() - 12);
		Component title = page == null ? Component.empty() : page.title();
		Ui.text(g, font, title, header.x() + 8, header.y() + 6, titleW, Theme.TEXT);
		String world = backend == null ? "" : backend.info().worldName();
		Ui.text(g, font, world, header.x() + 8, header.y() + 17, titleW, Theme.TEXT_MUTED);
		if (sx < header.x() + 60) {
			return;
		}
		int color = statusColor(backend);
		g.fill(sx, header.y() + 9, sx + sw, header.y() + 21, (color & 0x00FFFFFF) | 0x30000000);
		g.outline(sx, header.y() + 9, sw, 12, color);
		g.text(font, status, sx + 4, header.y() + 11, color, false);
	}

	private Component statusLabel(Backend backend) {
		if (backend == null) {
			return Component.translatable("wcm.status.offline");
		}
		if (!backend.isServer()) {
			return Component.translatable("wcm.status.local");
		}
		if (!backend.info().compatible()) {
			return Component.translatable("wcm.status.incompatible");
		}
		return Component.translatable("wcm.status.role", Component.translatable("wcm.role." + backend.info().role().toLowerCase(java.util.Locale.ROOT)));
	}

	private int statusColor(Backend backend) {
		if (backend == null || (backend.isServer() && !backend.info().compatible())) {
			return Theme.ERROR;
		}
		return backend.isServer() ? Theme.ACCENT : Theme.SUCCESS;
	}

	@Override
	public void extractRenderState(GuiGraphicsExtractor g, int mouseX, int mouseY, float delta) {
		super.extractRenderState(g, mouseX, mouseY, delta);
		// Toasts are hidden while a dialog is open so they never cover dialog content.
		if (toast != null && dialog == null && System.currentTimeMillis() < toastUntil) {
			int tw = Math.min(content.w(), font.width(toast) + 12);
			int tx = content.x();
			int ty = height - 19;
			g.fill(tx, ty, tx + tw, ty + 14, Theme.SURFACE_RAISED);
			g.fill(tx, ty, tx + 2, ty + 14, toastColor);
			Ui.text(g, font, toast, tx + 6, ty + 3, tw - 10, Theme.TEXT);
		}
	}

	@Override
	public boolean mouseClicked(MouseButtonEvent event, boolean doubleClick) {
		if (super.mouseClicked(event, doubleClick)) {
			return true;
		}
		setFocused(null);
		if (dialog != null) {
			return dialog.mouseClicked(event.x(), event.y(), doubleClick);
		}
		return page != null && page.mouseClicked(event.x(), event.y(), doubleClick);
	}

	@Override
	public boolean mouseDragged(MouseButtonEvent event, double dx, double dy) {
		if (dialog != null ? dialog.mouseDragged(event.x(), event.y()) : page != null && page.mouseDragged(event.x(), event.y())) {
			return true;
		}
		return super.mouseDragged(event, dx, dy);
	}

	@Override
	public boolean mouseReleased(MouseButtonEvent event) {
		if (dialog != null) {
			dialog.mouseReleased();
		} else if (page != null) {
			page.mouseReleased();
		}
		return super.mouseReleased(event);
	}

	@Override
	public boolean mouseScrolled(double x, double y, double scrollX, double scrollY) {
		if (dialog != null ? dialog.mouseScrolled(x, y, scrollY) : page != null && page.mouseScrolled(x, y, scrollY)) {
			return true;
		}
		return super.mouseScrolled(x, y, scrollX, scrollY);
	}

	@Override
	public boolean keyPressed(KeyEvent event) {
		if (getFocused() == null || !getFocused().isFocused()) {
			boolean handled = dialog != null ? dialog.keyPressed(event.key()) : page != null && page.keyPressed(event.key());
			if (handled) {
				return true;
			}
		}
		return super.keyPressed(event);
	}

	@Override
	public void onClose() {
		if (dialog != null) {
			closeDialog();
			return;
		}
		if (page != null && page.back()) {
			return;
		}
		minecraft.gui.setScreen(parent);
	}

	@Override
	public void onFilesDrop(List<Path> files) {
		files.stream().filter(p -> p.getFileName().toString().endsWith(".wcmp")).findFirst()
			.ifPresent(p -> openDialog(new ImportDialog(p, () -> {
				if (page != null) {
					page.refresh();
				}
			})));
	}

	@Override
	public boolean isPauseScreen() {
		return true;
	}
}
