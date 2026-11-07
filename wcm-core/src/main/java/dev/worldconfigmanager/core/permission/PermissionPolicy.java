package dev.worldconfigmanager.core.permission;

import java.util.EnumMap;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * Minimum role required per action. Loaded from the server-side WCM settings; never from the client.
 * {@link PermissionAction#MANAGE_WCM_SETTINGS} is always restricted to {@link PermissionRole#OWNER}.
 */
public final class PermissionPolicy {
	private final Map<PermissionAction, PermissionRole> required;

	public PermissionPolicy(Map<PermissionAction, PermissionRole> overrides) {
		EnumMap<PermissionAction, PermissionRole> map = new EnumMap<>(PermissionAction.class);
		for (PermissionAction action : PermissionAction.values()) {
			map.put(action, overrides.getOrDefault(action, action.defaultRole()));
		}
		map.put(PermissionAction.MANAGE_WCM_SETTINGS, PermissionRole.OWNER);
		this.required = map;
	}

	public static PermissionPolicy defaults() {
		return new PermissionPolicy(Map.of());
	}

	public PermissionRole requiredRole(PermissionAction action) {
		return required.get(action);
	}

	public boolean allows(PermissionRole role, PermissionAction action) {
		return role.atLeast(required.get(action));
	}

	/** @return all actions the role may perform (sent to clients purely as a UI hint) */
	public Set<PermissionAction> allowedActions(PermissionRole role) {
		return required.keySet().stream().filter(a -> allows(role, a)).collect(Collectors.toUnmodifiableSet());
	}

	public Map<PermissionAction, PermissionRole> asMap() {
		return Map.copyOf(required);
	}
}
