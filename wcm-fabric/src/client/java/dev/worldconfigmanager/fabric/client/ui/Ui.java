package dev.worldconfigmanager.fabric.client.ui;

import com.google.gson.JsonObject;
import dev.worldconfigmanager.fabric.client.backend.J;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.locale.Language;
import net.minecraft.network.chat.Component;
import net.minecraft.util.FormattedCharSequence;

import java.time.Instant;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;
import java.util.List;

/** Drawing and text helpers shared by all pages. */
public final class Ui {
	private static final DateTimeFormatter TIME = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm").withZone(ZoneId.systemDefault());

	private Ui() {
	}

	public static Component tr(String key, Object... args) {
		return Component.translatable(key, args);
	}

	/** Resolves a server-provided {@code {"key":..,"text":..}} object, falling back to the plain text. */
	public static Component text(JsonObject localized) {
		String key = J.str(localized, "key", null);
		String text = J.str(localized, "text", "");
		if (key != null && Language.getInstance().has(key)) {
			return Component.translatable(key);
		}
		return Component.literal(text);
	}

	public static String plain(JsonObject localized) {
		String key = J.str(localized, "key", null);
		if (key != null && Language.getInstance().has(key)) {
			return Language.getInstance().getOrDefault(key);
		}
		return J.str(localized, "text", "");
	}

	/** Translates an error key with arguments; unknown keys fall back to the key itself plus arguments. */
	public static Component error(String key, List<String> args) {
		if (key != null && Language.getInstance().has(key)) {
			return Component.translatable(key, args.toArray());
		}
		return Component.literal(key + (args.isEmpty() ? "" : " " + String.join(", ", args)));
	}

	public static String time(String iso) {
		if (iso == null || iso.isEmpty()) {
			return "—";
		}
		try {
			return TIME.format(Instant.parse(iso));
		} catch (DateTimeParseException e) {
			return iso;
		}
	}

	/** Trims text with an ellipsis so it never exceeds {@code width}. */
	public static String ellipsize(Font font, String text, int width) {
		if (font.width(text) <= width) {
			return text;
		}
		int ell = font.width("…");
		return font.plainSubstrByWidth(text, Math.max(0, width - ell)) + "…";
	}

	public static void text(GuiGraphicsExtractor g, Font font, String text, int x, int y, int maxWidth, int color) {
		g.text(font, ellipsize(font, text, maxWidth), x, y, color, false);
	}

	public static void text(GuiGraphicsExtractor g, Font font, Component text, int x, int y, int maxWidth, int color) {
		g.text(font, ellipsize(font, text.getString(), maxWidth), x, y, color, false);
	}

	/** Draws word-wrapped text and returns the height used. Stops at {@code maxLines}. */
	public static int wrapped(GuiGraphicsExtractor g, Font font, Component text, int x, int y, int width, int color, int maxLines) {
		List<FormattedCharSequence> lines = font.split(text, Math.max(10, width));
		int n = Math.min(lines.size(), maxLines);
		for (int i = 0; i < n; i++) {
			g.text(font, lines.get(i), x, y + i * (font.lineHeight + 1), color, false);
		}
		return n * (font.lineHeight + 1);
	}

	public static void panel(GuiGraphicsExtractor g, Rect r, int fill) {
		g.fill(r.x(), r.y(), r.right(), r.bottom(), fill);
		g.outline(r.x(), r.y(), r.w(), r.h(), Theme.BORDER);
	}

	/** Small rounded-looking badge; returns its width. */
	public static int badge(GuiGraphicsExtractor g, Font font, String label, int x, int y, int color) {
		int w = font.width(label) + 6;
		g.fill(x, y, x + w, y + 10, (color & 0x00FFFFFF) | 0x40000000);
		g.outline(x, y, w, 10, color);
		g.text(font, label, x + 3, y + 1, color, false);
		return w;
	}

	/** Draws a card with a title row; returns the content rectangle below the title. */
	public static Rect card(GuiGraphicsExtractor g, Font font, Rect r, Component title) {
		panel(g, r, Theme.SURFACE);
		g.fill(r.x() + 1, r.y() + 1, r.right() - 1, r.y() + 14, Theme.SURFACE_RAISED);
		text(g, font, title, r.x() + 6, r.y() + 4, r.w() - 12, Theme.TEXT_MUTED);
		return new Rect(r.x() + 6, r.y() + 18, r.w() - 12, r.h() - 22);
	}

	/** Draws a label/value pair on one line; returns the next y. */
	public static int field(GuiGraphicsExtractor g, Font font, Component label, String value, int x, int y, int w, int valueColor) {
		int lw = Math.min(w / 2, Math.max(50, font.width(label) + 6));
		text(g, font, label, x, y, lw - 4, Theme.TEXT_FAINT);
		text(g, font, value, x + lw, y, w - lw, valueColor);
		return y + font.lineHeight + 3;
	}

	/** Keeps a widget dimension positive so tiny windows never produce negative sizes. */
	public static int atLeast(int value, int min) {
		return Math.max(min, value);
	}
}
