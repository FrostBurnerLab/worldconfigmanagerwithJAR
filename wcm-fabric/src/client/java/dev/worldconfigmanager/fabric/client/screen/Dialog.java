package dev.worldconfigmanager.fabric.client.screen;

import dev.worldconfigmanager.fabric.client.ui.Rect;
import dev.worldconfigmanager.fabric.client.ui.Theme;
import dev.worldconfigmanager.fabric.client.ui.Ui;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.network.chat.Component;

/** A modal panel drawn above the current page. Escape closes it. */
public abstract class Dialog {
	protected WcmScreen screen;
	protected Rect panel = new Rect(0, 0, 0, 0);
	protected Font font;

	protected abstract Component title();

	/** Preferred size; the dialog is clamped to the screen. */
	protected int preferredWidth() {
		return 300;
	}

	protected int preferredHeight() {
		return 140;
	}

	final void init(WcmScreen screen, int screenWidth, int screenHeight) {
		this.screen = screen;
		this.font = screen.font();
		int w = Math.min(preferredWidth(), screenWidth - 16);
		int h = Math.min(preferredHeight(), screenHeight - 16);
		panel = new Rect((screenWidth - w) / 2, (screenHeight - h) / 2, w, h);
		layout(new Rect(panel.x() + 8, panel.y() + 22, panel.w() - 16, panel.h() - 30));
	}

	protected abstract void layout(Rect content);

	final void drawFrame(GuiGraphicsExtractor g, int mouseX, int mouseY, float delta) {
		Ui.panel(g, panel, Theme.SURFACE_RAISED);
		g.fill(panel.x(), panel.y(), panel.right(), panel.y() + 18, Theme.SIDEBAR);
		g.fill(panel.x(), panel.y() + 18, panel.right(), panel.y() + 19, Theme.ACCENT_DIM);
		Ui.text(g, font, title(), panel.x() + 8, panel.y() + 5, panel.w() - 16, Theme.TEXT);
		draw(g, new Rect(panel.x() + 8, panel.y() + 22, panel.w() - 16, panel.h() - 30), mouseX, mouseY, delta);
	}

	protected void draw(GuiGraphicsExtractor g, Rect content, int mouseX, int mouseY, float delta) {
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

	protected void close() {
		screen.closeDialog();
	}
}
