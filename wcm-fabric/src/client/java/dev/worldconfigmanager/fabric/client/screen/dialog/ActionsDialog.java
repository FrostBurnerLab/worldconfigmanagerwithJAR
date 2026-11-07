package dev.worldconfigmanager.fabric.client.screen.dialog;

import dev.worldconfigmanager.fabric.client.screen.Dialog;
import dev.worldconfigmanager.fabric.client.screen.Page;
import dev.worldconfigmanager.fabric.client.ui.Rect;
import dev.worldconfigmanager.fabric.client.ui.Theme;
import dev.worldconfigmanager.fabric.client.ui.Ui;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.Tooltip;
import net.minecraft.network.chat.Component;

import java.util.List;

/**
 * A menu of actions for one item, laid out in one or two columns so it fits small GUI sizes. Choosing an action
 * closes the menu first, so the action may open its own dialog.
 */
public final class ActionsDialog extends Dialog {
	private final Component title;
	private final Component subtitle;
	private final List<Page.ButtonSpec> actions;

	public ActionsDialog(Component title, Component subtitle, List<Page.ButtonSpec> actions) {
		this.title = title;
		this.subtitle = subtitle;
		this.actions = List.copyOf(actions);
	}

	@Override
	protected Component title() {
		return title;
	}

	@Override
	protected int preferredWidth() {
		return 320;
	}

	@Override
	protected int preferredHeight() {
		return 60 + ((actions.size() + 1) / 2) * 22 + 22;
	}

	@Override
	protected void layout(Rect content) {
		int available = content.h() - 12 - 24;
		int columns = actions.size() * 22 > available ? 2 : 1;
		int colW = (content.w() - (columns - 1) * 4) / columns;
		int rows = (actions.size() + columns - 1) / columns;
		for (int i = 0; i < actions.size(); i++) {
			Page.ButtonSpec spec = actions.get(i);
			int col = i / rows;
			int row = i % rows;
			Button b = Button.builder(spec.label(), btn -> {
				close();
				spec.onPress().onPress(btn);
			}).bounds(content.x() + col * (colW + 4), content.y() + 12 + row * 22, colW, 20)
				.tooltip(spec.tooltip() == null ? null : Tooltip.create(spec.tooltip())).build();
			b.active = spec.active();
			screen.add(b);
		}
		screen.add(Button.builder(Component.translatable("gui.cancel"), b -> close()).bounds(content.x(), content.bottom() - 20, content.w(), 20).build());
	}

	@Override
	protected void draw(GuiGraphicsExtractor g, Rect content, int mouseX, int mouseY, float delta) {
		Ui.text(g, font, subtitle, content.x(), content.y(), content.w(), Theme.TEXT_MUTED);
	}
}
