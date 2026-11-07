package dev.worldconfigmanager.fabric.client.screen;

import com.google.gson.JsonObject;
import dev.worldconfigmanager.fabric.client.backend.Backend;
import dev.worldconfigmanager.fabric.client.backend.Response;
import dev.worldconfigmanager.fabric.client.ui.Rect;
import dev.worldconfigmanager.fabric.client.ui.Theme;
import dev.worldconfigmanager.fabric.client.ui.Ui;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.Tooltip;
import net.minecraft.network.chat.Component;

import java.util.function.Consumer;

/**
 * One module of the WCM screen. Pages keep their state in fields and recreate their widgets in {@link #layout()},
 * which runs on every screen (re)initialisation, e.g. after a resize or when a dialog closes.
 */
public abstract class Page {
	protected WcmScreen screen;
	protected Rect area = new Rect(0, 0, 0, 0);
	protected Font font;
	protected boolean loading;

	public abstract Component title();

	/** Called once when the page becomes active and whenever data may have changed. */
	public void refresh() {
	}

	final void init(WcmScreen screen, Rect area) {
		this.screen = screen;
		this.area = area;
		this.font = screen.font();
		layout();
	}

	/** Adds widgets for the current {@link #area}. */
	protected abstract void layout();

	public void draw(GuiGraphicsExtractor g, int mouseX, int mouseY, float delta) {
	}

	public boolean mouseClicked(double x, double y, boolean doubleClick) {
		return false;
	}

	public boolean mouseScrolled(double x, double y, double amount) {
		return false;
	}

	public boolean mouseDragged(double x, double y) {
		return false;
	}

	public void mouseReleased() {
	}

	public boolean keyPressed(int key) {
		return false;
	}

	/** Handles Escape/back inside the page (e.g. leaving a sub-view). Returns {@code true} if it was consumed. */
	public boolean back() {
		return false;
	}

	protected Backend backend() {
		return screen.backend();
	}

	protected boolean can(String action) {
		return screen.backend() != null && screen.backend().info().can(action);
	}

	/** Sends a request and delivers the response only if this page is still showing. Errors are shown as toast. */
	protected void request(String type, JsonObject body, Consumer<Response> onSuccess) {
		Backend backend = backend();
		if (backend == null) {
			return;
		}
		backend.request(type, body, response -> {
			if (!screen.isActive(this)) {
				return;
			}
			if (response.ok()) {
				onSuccess.accept(response);
			} else {
				loading = false;
				screen.toastError(response);
			}
		});
	}

	protected Button button(Component label, int x, int y, int w, Button.OnPress onPress) {
		return screen.add(Button.builder(label, onPress).bounds(x, y, Math.max(20, w), 20).build());
	}

	protected Button button(Component label, Component tooltip, int x, int y, int w, boolean active, Button.OnPress onPress) {
		Button b = Button.builder(label, onPress).bounds(x, y, Math.max(20, w), 20).tooltip(Tooltip.create(tooltip)).build();
		b.active = active;
		return screen.add(b);
	}

	protected void drawLoading(GuiGraphicsExtractor g, Rect r) {
		if (loading) {
			g.centeredText(font, Ui.tr("wcm.common.loading"), r.x() + r.w() / 2, r.y() + r.h() / 2 - 4, Theme.TEXT_MUTED);
		}
	}

	/** Lays out buttons in rows that wrap to fit {@code width}; returns the total height used. */
	protected int buttonRow(int x, int y, int width, java.util.List<ButtonSpec> specs) {
		int cx = x;
		int cy = y;
		for (ButtonSpec spec : specs) {
			int w = Math.max(40, font.width(spec.label()) + 12);
			if (cx + w > x + width && cx > x) {
				cx = x;
				cy += 22;
			}
			Button b = Button.builder(spec.label(), spec.onPress()).bounds(cx, cy, Math.min(w, width), 20)
				.tooltip(spec.tooltip() == null ? null : Tooltip.create(spec.tooltip())).build();
			b.active = spec.active();
			screen.add(b);
			cx += w + 4;
		}
		return cy - y + 20;
	}

	/** @return the height {@link #buttonRow} would use for these buttons */
	protected int buttonRowHeight(int width, java.util.List<ButtonSpec> specs) {
		int rows = 1;
		int cx = 0;
		for (ButtonSpec s : specs) {
			int w = Math.max(40, font.width(s.label()) + 12);
			if (cx + w > width && cx > 0) {
				rows++;
				cx = 0;
			}
			cx += w + 4;
		}
		return rows * 22 - 2;
	}

	/** Declarative button description for {@link #buttonRow}. */
	public record ButtonSpec(Component label, Component tooltip, boolean active, Button.OnPress onPress) {
		public static ButtonSpec of(Component label, boolean active, Button.OnPress onPress) {
			return new ButtonSpec(label, null, active, onPress);
		}
	}
}
