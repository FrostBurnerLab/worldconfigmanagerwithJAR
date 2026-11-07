package dev.worldconfigmanager.fabric.client.ui;

import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.network.chat.Component;
import com.mojang.blaze3d.platform.InputConstants;

import java.util.List;
import java.util.function.Consumer;

/**
 * Lightweight custom-drawn list with selection, mouse-wheel scrolling, scrollbar dragging and keyboard navigation.
 * Rows are drawn by a callback, so lists stay cheap even with thousands of entries (only visible rows are drawn).
 */
public final class ScrollList<T> {
	/** Draws one row. */
	@FunctionalInterface
	public interface RowRenderer<T> {
		void draw(GuiGraphicsExtractor g, T item, Rect row, boolean hovered, boolean selected);
	}

	private final int rowHeight;
	private final RowRenderer<T> renderer;
	private List<T> items = List.of();
	private Rect area = new Rect(0, 0, 0, 0);
	private double scroll;
	private int selected = -1;
	private boolean focused;
	private boolean draggingBar;
	private Component emptyMessage = Component.empty();
	private Consumer<T> onSelect = t -> {
	};
	private Consumer<T> onActivate = t -> {
	};

	public ScrollList(int rowHeight, RowRenderer<T> renderer) {
		this.rowHeight = rowHeight;
		this.renderer = renderer;
	}

	public ScrollList<T> onSelect(Consumer<T> onSelect) {
		this.onSelect = onSelect;
		return this;
	}

	public ScrollList<T> onActivate(Consumer<T> onActivate) {
		this.onActivate = onActivate;
		return this;
	}

	public void setEmptyMessage(Component message) {
		this.emptyMessage = message;
	}

	public void setArea(Rect area) {
		this.area = area;
		clampScroll();
	}

	public Rect area() {
		return area;
	}

	/** Replaces the items, keeping the selection on an equal item if present. */
	public void setItems(List<T> newItems) {
		T previous = getSelected();
		this.items = List.copyOf(newItems);
		selected = previous == null ? -1 : items.indexOf(previous);
		clampScroll();
	}

	public List<T> items() {
		return items;
	}

	public T getSelected() {
		return selected >= 0 && selected < items.size() ? items.get(selected) : null;
	}

	public void select(T item) {
		selected = items.indexOf(item);
		ensureVisible();
	}

	public void clearSelection() {
		selected = -1;
	}

	private int contentHeight() {
		return items.size() * rowHeight;
	}

	private void clampScroll() {
		scroll = Math.max(0, Math.min(scroll, Math.max(0, contentHeight() - area.h())));
	}

	private void ensureVisible() {
		if (selected < 0) {
			return;
		}
		int top = selected * rowHeight;
		if (top < scroll) {
			scroll = top;
		} else if (top + rowHeight > scroll + area.h()) {
			scroll = top + rowHeight - area.h();
		}
		clampScroll();
	}

	public void draw(GuiGraphicsExtractor g, Font font, int mouseX, int mouseY) {
		g.fill(area.x(), area.y(), area.right(), area.bottom(), Theme.SURFACE);
		g.outline(area.x(), area.y(), area.w(), area.h(), focused ? Theme.ACCENT_DIM : Theme.BORDER);
		if (items.isEmpty()) {
			Ui.wrapped(g, font, emptyMessage, area.x() + 8, area.y() + 8, area.w() - 16, Theme.TEXT_MUTED, 6);
			return;
		}
		boolean bar = contentHeight() > area.h();
		int rowWidth = area.w() - 2 - (bar ? 5 : 0);
		g.enableScissor(area.x() + 1, area.y() + 1, area.right() - 1, area.bottom() - 1);
		int first = (int) (scroll / rowHeight);
		int last = Math.min(items.size() - 1, (int) ((scroll + area.h()) / rowHeight));
		for (int i = first; i <= last; i++) {
			int y = area.y() + 1 + i * rowHeight - (int) scroll;
			Rect row = new Rect(area.x() + 1, y, rowWidth, rowHeight);
			boolean hovered = row.contains(mouseX, mouseY) && area.contains(mouseX, mouseY);
			if (i == selected) {
				g.fill(row.x(), row.y(), row.right(), row.bottom(), Theme.SELECTED);
				g.fill(row.x(), row.y(), row.x() + 2, row.bottom(), Theme.ACCENT);
			} else if (hovered) {
				g.fill(row.x(), row.y(), row.right(), row.bottom(), Theme.SURFACE_HOVER);
			}
			renderer.draw(g, items.get(i), row, hovered, i == selected);
		}
		g.disableScissor();
		if (bar) {
			int trackX = area.right() - 5;
			g.fill(trackX, area.y() + 1, area.right() - 1, area.bottom() - 1, 0xFF15181D);
			int thumbH = Math.max(12, area.h() * area.h() / contentHeight());
			int thumbY = area.y() + 1 + (int) ((area.h() - thumbH - 2) * (scroll / Math.max(1, contentHeight() - area.h())));
			g.fill(trackX, thumbY, area.right() - 1, thumbY + thumbH, draggingBar ? Theme.ACCENT : Theme.BORDER);
		}
	}

	public boolean mouseClicked(double mx, double my, boolean doubleClick) {
		focused = area.contains(mx, my);
		if (!focused) {
			return false;
		}
		if (contentHeight() > area.h() && mx >= area.right() - 6) {
			draggingBar = true;
			dragTo(my);
			return true;
		}
		int index = (int) ((my - area.y() - 1 + scroll) / rowHeight);
		if (index >= 0 && index < items.size()) {
			selected = index;
			onSelect.accept(items.get(index));
			if (doubleClick) {
				onActivate.accept(items.get(index));
			}
		}
		return true;
	}

	public boolean mouseDragged(double my) {
		if (draggingBar) {
			dragTo(my);
			return true;
		}
		return false;
	}

	public void mouseReleased() {
		draggingBar = false;
	}

	private void dragTo(double my) {
		double ratio = (my - area.y()) / Math.max(1, area.h());
		scroll = ratio * (contentHeight() - area.h());
		clampScroll();
	}

	public boolean mouseScrolled(double mx, double my, double amount) {
		if (!area.contains(mx, my)) {
			return false;
		}
		scroll -= amount * rowHeight * 2;
		clampScroll();
		return true;
	}

	public boolean keyPressed(int key) {
		if (!focused || items.isEmpty()) {
			return false;
		}
		int page = Math.max(1, area.h() / rowHeight - 1);
		int target = switch (key) {
			case InputConstants.KEY_DOWN -> selected + 1;
			case InputConstants.KEY_UP -> selected <= 0 ? 0 : selected - 1;
			case InputConstants.KEY_PAGEDOWN -> selected + page;
			case InputConstants.KEY_PAGEUP -> selected - page;
			case InputConstants.KEY_HOME -> 0;
			case InputConstants.KEY_END -> items.size() - 1;
			case InputConstants.KEY_RETURN, InputConstants.KEY_NUMPADENTER -> {
				if (getSelected() != null) {
					onActivate.accept(getSelected());
				}
				yield Integer.MIN_VALUE;
			}
			default -> Integer.MIN_VALUE + 1;
		};
		if (target == Integer.MIN_VALUE) {
			return true;
		}
		if (target == Integer.MIN_VALUE + 1) {
			return false;
		}
		selected = Math.max(0, Math.min(items.size() - 1, target));
		ensureVisible();
		onSelect.accept(items.get(selected));
		return true;
	}

	public boolean isFocused() {
		return focused;
	}

	public void setFocused(boolean focused) {
		this.focused = focused;
	}
}
