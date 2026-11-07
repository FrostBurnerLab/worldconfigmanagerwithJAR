package dev.worldconfigmanager.core.protocol;

/** Per-player protocol state kept by the server. */
public final class ConnectionState {
	private final RateLimiter limiter;
	private Integer negotiatedVersion;
	private boolean helloReceived;

	public ConnectionState(RateLimiter limiter) {
		this.limiter = limiter;
	}

	public RateLimiter limiter() {
		return limiter;
	}

	public boolean helloReceived() {
		return helloReceived;
	}

	/** @return negotiated version, or {@code null} when negotiation failed (read-only/incompatible) */
	public Integer negotiatedVersion() {
		return negotiatedVersion;
	}

	void onHello(Integer version) {
		helloReceived = true;
		negotiatedVersion = version;
	}
}
