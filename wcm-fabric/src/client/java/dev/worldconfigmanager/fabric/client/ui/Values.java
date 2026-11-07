package dev.worldconfigmanager.fabric.client.ui;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import dev.worldconfigmanager.api.ConfigDefinition;
import dev.worldconfigmanager.api.ConfigValue;
import dev.worldconfigmanager.api.LocalizedText;
import dev.worldconfigmanager.api.ValidationResult;
import dev.worldconfigmanager.api.ValueConstraints;
import dev.worldconfigmanager.api.ValueType;
import dev.worldconfigmanager.core.json.JsonFormatException;
import dev.worldconfigmanager.core.json.ValueJson;
import dev.worldconfigmanager.fabric.client.backend.J;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

/** Conversion between typed values, their JSON form and editable text. */
public final class Values {
	private Values() {
	}

	/** Reads a typed value from a server JSON field; returns empty when missing or malformed. */
	public static Optional<ConfigValue> read(JsonObject parent, String field) {
		JsonElement e = parent.get(field);
		if (e == null || e.isJsonNull()) {
			return Optional.empty();
		}
		try {
			return Optional.of(ValueJson.read(e));
		} catch (JsonFormatException ex) {
			return Optional.empty();
		}
	}

	public static String display(Optional<ConfigValue> value) {
		return value.map(Values::display).orElse("—");
	}

	public static String display(ConfigValue value) {
		return switch (value) {
			case ConfigValue.DecimalValue(double d) -> d == Math.rint(d) ? String.format(java.util.Locale.ROOT, "%.1f", d)
				: String.format(java.util.Locale.ROOT, "%.4g", d).replaceAll("0+$", "");
			default -> value.display();
		};
	}

	/** Text shown in an edit box for a value. */
	public static String editable(ConfigValue value) {
		return switch (value) {
			case ConfigValue.ListValue(List<ConfigValue> list) -> String.join("; ", list.stream().map(ConfigValue::display).toList());
			default -> value.display();
		};
	}

	/**
	 * Parses user input for a type. Lists use {@code ;} as separator.
	 *
	 * @return the parsed value, or empty if the text is not a valid literal of the type
	 */
	public static Optional<ConfigValue> parse(ValueType type, ValueType elementType, String text) {
		String t = text.strip();
		try {
			return switch (type) {
				case BOOLEAN -> t.equalsIgnoreCase("true") || t.equalsIgnoreCase("false")
					? Optional.of(ConfigValue.of(Boolean.parseBoolean(t))) : Optional.empty();
				case INTEGER -> Optional.of(ConfigValue.of(Long.parseLong(t)));
				case DECIMAL -> {
					double d = Double.parseDouble(t.replace(',', '.'));
					yield Double.isFinite(d) ? Optional.of(ConfigValue.of(d)) : Optional.empty();
				}
				case STRING -> Optional.of(ConfigValue.of(text));
				case ENUM -> Optional.of(ConfigValue.ofEnum(t));
				case LIST -> {
					List<ConfigValue> list = new ArrayList<>();
					if (!t.isEmpty()) {
						for (String part : t.split(";")) {
							Optional<ConfigValue> v = parse(elementType == null ? ValueType.STRING : elementType, null, part.strip());
							if (v.isEmpty()) {
								yield Optional.empty();
							}
							list.add(v.get());
						}
					}
					yield Optional.of(new ConfigValue.ListValue(list));
				}
				case STRUCTURED -> Optional.empty();
			};
		} catch (NumberFormatException e) {
			return Optional.empty();
		}
	}

	/**
	 * Rebuilds a definition from server JSON so the editor can give immediate feedback. Only declarative constraints are
	 * checked here; the server re-validates everything, including custom validators.
	 */
	public static Optional<ConfigDefinition> definition(JsonObject json) {
		try {
			ValueType type = ValueType.fromId(J.str(json, "type", ""));
			Optional<ConfigValue> def = read(json, "default");
			if (def.isEmpty()) {
				return Optional.empty();
			}
			JsonObject c = J.obj(json, "constraints");
			Double min = c.has("min") ? c.get("min").getAsDouble() : null;
			Double max = c.has("max") ? c.get("max").getAsDouble() : null;
			Integer maxLength = c.has("maxLength") ? c.get("maxLength").getAsInt() : null;
			String pattern = J.str(c, "pattern", null);
			String element = J.str(c, "elementType", null);
			ValueConstraints constraints = new ValueConstraints(min, max, J.strings(c, "allowed"), maxLength, pattern,
				element == null ? null : ValueType.fromId(element));
			return Optional.of(ConfigDefinition.builder(J.str(json, "key", "?"), type, def.get())
				.displayName(LocalizedText.literal(J.str(json, "key", "?")))
				.constraints(constraints).build());
		} catch (RuntimeException e) {
			return Optional.empty();
		}
	}

	public static ValidationResult validate(Optional<ConfigDefinition> definition, ConfigValue value) {
		return definition.map(d -> d.validate(value)).orElse(ValidationResult.ok());
	}

	public static JsonObject write(ConfigValue value) {
		return ValueJson.write(value);
	}
}
