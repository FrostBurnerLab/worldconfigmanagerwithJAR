package dev.worldconfigmanager.core.protocol;

import java.util.Optional;

/** Protocol constants and version negotiation. */
public final class WcmProtocol {
	/** Highest protocol version spoken by this build. */
	public static final int VERSION = 1;
	/** Lowest protocol version this build still accepts. */
	public static final int MIN_VERSION = 1;
	/** Maximum client-to-server message size in bytes (fits a base64 encoded maximum-size package). */
	public static final int MAX_C2S_BYTES = 1536 * 1024;
	/** Maximum server-to-client message size in bytes. */
	public static final int MAX_S2C_BYTES = 4 * 1024 * 1024;

	private WcmProtocol() {
	}

	/** @return the highest version supported by both sides, or empty if the ranges do not overlap */
	public static Optional<Integer> negotiate(int clientMin, int clientMax, int serverMin, int serverMax) {
		if (clientMin > clientMax || serverMin > serverMax) {
			return Optional.empty();
		}
		int best = Math.min(clientMax, serverMax);
		return best >= Math.max(clientMin, serverMin) ? Optional.of(best) : Optional.empty();
	}
}
