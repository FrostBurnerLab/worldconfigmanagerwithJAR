package dev.worldconfigmanager.core.util;

/** Lenient comparison of dotted version strings such as {@code 1.2.0} or {@code 26.3}. */
public final class Versions {
	private Versions() {
	}

	/** @return negative, zero or positive like {@link Comparable#compareTo} */
	public static int compare(String a, String b) {
		int[] x = parse(a);
		int[] y = parse(b);
		for (int i = 0; i < Math.max(x.length, y.length); i++) {
			int xi = i < x.length ? x[i] : 0;
			int yi = i < y.length ? y[i] : 0;
			if (xi != yi) {
				return Integer.compare(xi, yi);
			}
		}
		return 0;
	}

	/** @return the first numeric component (major version), or 0 when absent */
	public static int major(String version) {
		int[] parts = parse(version);
		return parts.length == 0 ? 0 : parts[0];
	}

	private static int[] parse(String version) {
		if (version == null || version.isBlank()) {
			return new int[0];
		}
		String core = version.split("[+-]", 2)[0];
		String[] pieces = core.split("\\.");
		int[] result = new int[pieces.length];
		for (int i = 0; i < pieces.length; i++) {
			String digits = pieces[i].replaceAll("\\D.*$", "");
			try {
				result[i] = digits.isEmpty() ? 0 : Integer.parseInt(digits);
			} catch (NumberFormatException e) {
				result[i] = Integer.MAX_VALUE;
			}
		}
		return result;
	}
}
