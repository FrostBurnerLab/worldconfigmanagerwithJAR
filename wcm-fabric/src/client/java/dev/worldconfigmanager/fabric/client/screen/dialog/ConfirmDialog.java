package dev.worldconfigmanager.fabric.client.screen.dialog;

import dev.worldconfigmanager.fabric.client.screen.Dialog;
import dev.worldconfigmanager.fabric.client.ui.Rect;
import dev.worldconfigmanager.fabric.client.ui.Theme;
import dev.worldconfigmanager.fabric.client.ui.Ui;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.Button;
import net.minecraft.network.chat.Component;

/** Yes/no confirmation for destructive or state-changing operations. */
public final class ConfirmDialog extends Dialog {
	private final Component title;
	private final Component message;
	private final Component confirmLabel;
	private final Runnable onConfirm;

	public ConfirmDialog(Component title, Component message, Component confirmLabel, Runnable onConfirm) {
		this.title = title;
		this.message = message;
		this.confirmLabel = confirmLabel;
		this.onConfirm = onConfirm;
	}

	@Override
	protected Component title() {
		return title;
	}

	@Override
	protected int preferredHeight() {
		return 120;
	}

	@Override
	protected void layout(Rect content) {
		int bw = (content.w() - 4) / 2;
		screen.add(Button.builder(confirmLabel, b -> {
			close();
			onConfirm.run();
		}).bounds(content.x(), content.bottom() - 20, bw, 20).build());
		Button cancel = screen.add(Button.builder(Component.translatable("gui.cancel"), b -> close())
			.bounds(content.x() + bw + 4, content.bottom() - 20, bw, 20).build());
		screen.focus(cancel);
	}

	@Override
	protected void draw(GuiGraphicsExtractor g, Rect content, int mouseX, int mouseY, float delta) {
		Ui.wrapped(g, font, message, content.x(), content.y() + 4, content.w(), Theme.TEXT, 6);
	}
}
