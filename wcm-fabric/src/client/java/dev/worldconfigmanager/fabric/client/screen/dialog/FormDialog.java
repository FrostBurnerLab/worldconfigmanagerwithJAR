package dev.worldconfigmanager.fabric.client.screen.dialog;

import dev.worldconfigmanager.fabric.client.screen.Dialog;
import dev.worldconfigmanager.fabric.client.ui.Rect;
import dev.worldconfigmanager.fabric.client.ui.Theme;
import dev.worldconfigmanager.fabric.client.ui.Ui;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.network.chat.Component;
import com.mojang.blaze3d.platform.InputConstants;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Consumer;

/** A small form with one or more text fields. Enter confirms, Escape cancels. */
public class FormDialog extends Dialog {
	/** One field of the form. */
	public record Field(Component label, String initial, int maxLength) {
	}

	private final Component title;
	private final List<Field> fields;
	private final List<String> values = new ArrayList<>();
	private final Consumer<List<String>> onConfirm;
	private final List<EditBox> boxes = new ArrayList<>();
	private Button confirm;
	private Component toggleLabel;
	private boolean toggle;

	/**
	 * Adds an on/off option below the fields. Its state is appended to the result as {@code "true"}/{@code "false"}.
	 */
	public FormDialog withToggle(Component label, boolean initial) {
		this.toggleLabel = label;
		this.toggle = initial;
		return this;
	}

	public FormDialog(Component title, List<Field> fields, Consumer<List<String>> onConfirm) {
		this.title = title;
		this.fields = List.copyOf(fields);
		this.onConfirm = onConfirm;
		fields.forEach(f -> values.add(f.initial()));
	}

	@Override
	protected Component title() {
		return title;
	}

	@Override
	protected int preferredHeight() {
		return 54 + fields.size() * 36 + (toggleLabel == null ? 0 : 24);
	}

	@Override
	protected void layout(Rect content) {
		boxes.clear();
		int y = content.y() + 12;
		for (int i = 0; i < fields.size(); i++) {
			Field f = fields.get(i);
			int index = i;
			EditBox box = new EditBox(font, content.x(), y, content.w(), 18, f.label());
			box.setMaxLength(f.maxLength());
			box.setValue(values.get(i));
			box.setResponder(v -> {
				values.set(index, v);
				updateConfirm();
			});
			screen.add(box);
			boxes.add(box);
			y += 36;
		}
		if (toggleLabel != null) {
			screen.add(Button.builder(Component.translatable(toggle ? "wcm.common.toggle.on" : "wcm.common.toggle.off", toggleLabel), b -> {
				toggle = !toggle;
				screen.rebuild();
			}).bounds(content.x(), y - 12, content.w(), 20).build());
		}
		int bw = (content.w() - 4) / 2;
		confirm = screen.add(Button.builder(Component.translatable("gui.done"), b -> submit()).bounds(content.x(), content.bottom() - 20, bw, 20).build());
		screen.add(Button.builder(Component.translatable("gui.cancel"), b -> close()).bounds(content.x() + bw + 4, content.bottom() - 20, bw, 20).build());
		if (!boxes.isEmpty()) {
			screen.focus(boxes.getFirst());
		}
		updateConfirm();
	}

	private void updateConfirm() {
		if (confirm != null) {
			confirm.active = !values.getFirst().isBlank();
		}
	}

	private void submit() {
		if (values.getFirst().isBlank()) {
			return;
		}
		List<String> result = new ArrayList<>(values);
		if (toggleLabel != null) {
			result.add(Boolean.toString(toggle));
		}
		close();
		onConfirm.accept(result);
	}

	@Override
	protected void draw(GuiGraphicsExtractor g, Rect content, int mouseX, int mouseY, float delta) {
		int y = content.y();
		for (Field f : fields) {
			Ui.text(g, font, f.label(), content.x(), y, content.w(), Theme.TEXT_MUTED);
			y += 36;
		}
	}

	@Override
	public boolean keyPressed(int key) {
		if (key == InputConstants.KEY_RETURN || key == InputConstants.KEY_NUMPADENTER) {
			submit();
			return true;
		}
		return false;
	}
}
