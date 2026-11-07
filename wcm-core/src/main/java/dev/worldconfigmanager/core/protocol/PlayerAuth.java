package dev.worldconfigmanager.core.protocol;

import dev.worldconfigmanager.core.permission.PermissionRole;

import java.util.Objects;
import java.util.UUID;

/**
 * Server-computed identity and role of the requesting player. Never constructed from client-provided data.
 */
public record PlayerAuth(UUID playerId, String name, PermissionRole role) {
	public PlayerAuth {
		Objects.requireNonNull(playerId, "playerId");
		Objects.requireNonNull(name, "name");
		Objects.requireNonNull(role, "role");
	}
}
