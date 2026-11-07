package dev.worldconfigmanager.core;

import dev.worldconfigmanager.api.ConfigScope;
import dev.worldconfigmanager.api.ProviderContext;
import dev.worldconfigmanager.core.registry.ProviderRegistry;
import dev.worldconfigmanager.core.world.WorldDescriptor;
import dev.worldconfigmanager.core.world.WorldKind;

import java.nio.file.Path;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

/** Shared helpers for tests. */
public final class TestSupport {
	private TestSupport() {
	}

	/** A clock that can be advanced manually. */
	public static final class MutableClock extends Clock {
		private Instant now;

		public MutableClock(Instant start) {
			this.now = start;
		}

		public void advanceSeconds(long seconds) {
			now = now.plusSeconds(seconds);
		}

		@Override
		public java.time.ZoneId getZone() {
			return ZoneOffset.UTC;
		}

		@Override
		public Clock withZone(java.time.ZoneId zone) {
			return this;
		}

		@Override
		public Instant instant() {
			return now;
		}
	}

	public static MutableClock clock() {
		return new MutableClock(Instant.parse("2026-11-01T12:00:00Z"));
	}

	public static ProviderContext serverContext(UUID worldId) {
		return new ProviderContext() {
			@Override
			public Set<ConfigScope> scopes() {
				return Set.of(ConfigScope.WORLD, ConfigScope.SERVER);
			}

			@Override
			public Optional<String> worldId() {
				return Optional.ofNullable(worldId).map(UUID::toString);
			}

			@Override
			public <T> Optional<T> service(Class<T> type) {
				return Optional.empty();
			}
		};
	}

	public static WorldSession session(Path root, ProviderRegistry registry, Clock clock) throws Exception {
		return new WorldSession(root.resolve("config"), root.resolve("world"),
			new WorldDescriptor("Test World", WorldKind.SINGLEPLAYER, "survival", false, "26.3"), registry,
			TestSupport::serverContext, clock);
	}
}
