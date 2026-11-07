package dev.worldconfigmanager.fabric.client.ui;

/** Integer rectangle in GUI coordinates. */
public record Rect(int x, int y, int w, int h) {
	public int right() {
		return x + w;
	}

	public int bottom() {
		return y + h;
	}

	public boolean contains(double px, double py) {
		return px >= x && px < x + w && py >= y && py < y + h;
	}

	public Rect inset(int d) {
		return new Rect(x + d, y + d, Math.max(0, w - 2 * d), Math.max(0, h - 2 * d));
	}

	/** Splits horizontally: the left part gets {@code leftWidth}, separated by {@code gap}. */
	public Rect[] splitH(int leftWidth, int gap) {
		int lw = Math.max(0, Math.min(leftWidth, w - gap));
		return new Rect[] {new Rect(x, y, lw, h), new Rect(x + lw + gap, y, Math.max(0, w - lw - gap), h)};
	}

	public Rect[] splitV(int topHeight, int gap) {
		int th = Math.max(0, Math.min(topHeight, h - gap));
		return new Rect[] {new Rect(x, y, w, th), new Rect(x, y + th + gap, w, Math.max(0, h - th - gap))};
	}
}
