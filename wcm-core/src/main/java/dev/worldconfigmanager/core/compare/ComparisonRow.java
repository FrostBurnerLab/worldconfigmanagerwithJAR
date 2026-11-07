package dev.worldconfigmanager.core.compare;

import dev.worldconfigmanager.api.ConfigKey;
import dev.worldconfigmanager.api.ConfigValue;

/**
 * One compared setting.
 *
 * @param left   value on the left side ({@code null} if absent)
 * @param right  value on the right side ({@code null} if absent)
 * @param detail extra information (e.g. versions or validation message)
 */
public record ComparisonRow(ConfigKey key, ConfigValue left, ConfigValue right, DiffStatus status, String detail) {
}
