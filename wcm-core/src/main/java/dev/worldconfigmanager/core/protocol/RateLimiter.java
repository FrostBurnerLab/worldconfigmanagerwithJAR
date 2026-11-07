package dev.worldconfigmanager.core.protocol;

import java.util.function.LongSupplier;

/** Token-bucket rate limiter. Not thread-safe; one instance per player session on the server thread. */
public final class RateLimiter {
	private final double ratePerNano;
	private final double capacity;
	private final LongSupplier nanoTime;
	private double tokens;
	private long last;

	public RateLimiter(double perSecond, int burst, LongSupplier nanoTime) {
		this.ratePerNano = perSecond / 1_000_000_000d;
		this.capacity = burst;
		this.nanoTime = nanoTime;
		this.tokens = burst;
		this.last = nanoTime.getAsLong();
	}

	/** @return {@code true} if {@code cost} tokens were available and consumed */
	public boolean tryAcquire(int cost) {
		long now = nanoTime.getAsLong();
		tokens = Math.min(capacity, tokens + (now - last) * ratePerNano);
		last = now;
		if (tokens >= cost) {
			tokens -= cost;
			return true;
		}
		return false;
	}
}
