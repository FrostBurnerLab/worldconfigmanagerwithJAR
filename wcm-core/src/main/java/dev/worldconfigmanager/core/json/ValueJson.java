package dev.worldconfigmanager.core.json;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonPrimitive;
import dev.worldconfigmanager.api.ConfigValue;
import dev.worldconfigmanager.api.ValueType;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Typed JSON representation of {@link ConfigValue}: {@code {"type":"integer","value":5}}. Lists and structures nest
 * the same representation, so the type of every element survives a round trip.
 */
public final class ValueJson {
	private ValueJson() {
	}

	public static JsonObject write(ConfigValue value) {
		JsonObject json = new JsonObject();
		json.addProperty("type", value.type().id());
		json.add("value", writeRaw(value));
		return json;
	}

	private static JsonElement writeRaw(ConfigValue value) {
		return switch (value) {
			case ConfigValue.BoolValue(boolean v) -> new JsonPrimitive(v);
			case ConfigValue.IntValue(long v) -> new JsonPrimitive(v);
			case ConfigValue.DecimalValue(double v) -> new JsonPrimitive(v);
			case ConfigValue.StringValue(String v) -> new JsonPrimitive(v);
			case ConfigValue.EnumValue(String v) -> new JsonPrimitive(v);
			case ConfigValue.ListValue(List<ConfigValue> values) -> {
				JsonArray array = new JsonArray();
				values.forEach(v -> array.add(write(v)));
				yield array;
			}
			case ConfigValue.StructValue(Map<String, ConfigValue> fields) -> {
				JsonObject object = new JsonObject();
				fields.forEach((k, v) -> object.add(k, write(v)));
				yield object;
			}
		};
	}

	public static ConfigValue read(JsonElement element) throws JsonFormatException {
		return read(element, 0);
	}

	private static ConfigValue read(JsonElement element, int depth) throws JsonFormatException {
		if (depth > JsonLimits.MAX_VALUE_DEPTH) {
			throw new JsonFormatException("Value nesting too deep");
		}
		if (element == null || !element.isJsonObject()) {
			throw new JsonFormatException("Value must be an object with 'type' and 'value'");
		}
		JsonObject json = element.getAsJsonObject();
		ValueType type;
		try {
			type = ValueType.fromId(Json.string(json, "type"));
		} catch (IllegalArgumentException e) {
			throw new JsonFormatException(e.getMessage(), e);
		}
		JsonElement raw = json.get("value");
		if (raw == null || raw.isJsonNull()) {
			throw new JsonFormatException("Value is missing");
		}
		return switch (type) {
			case BOOLEAN -> new ConfigValue.BoolValue(primitive(raw, type).getAsBoolean());
			case INTEGER -> new ConfigValue.IntValue(integerValue(primitive(raw, type)));
			case DECIMAL -> decimalValue(primitive(raw, type));
			case STRING -> new ConfigValue.StringValue(Json.asString(raw, "string value"));
			case ENUM -> new ConfigValue.EnumValue(Json.asString(raw, "enum value"));
			case LIST -> readList(raw, depth);
			case STRUCTURED -> readStruct(raw, depth);
		};
	}

	private static JsonPrimitive primitive(JsonElement raw, ValueType type) throws JsonFormatException {
		if (!raw.isJsonPrimitive()) {
			throw new JsonFormatException("Expected primitive for " + type.id());
		}
		JsonPrimitive p = raw.getAsJsonPrimitive();
		boolean ok = switch (type) {
			case BOOLEAN -> p.isBoolean();
			case INTEGER, DECIMAL -> p.isNumber();
			default -> p.isString();
		};
		if (!ok) {
			throw new JsonFormatException("Value does not match declared type " + type.id());
		}
		return p;
	}

	private static long integerValue(JsonPrimitive p) throws JsonFormatException {
		try {
			return p.getAsBigDecimal().longValueExact();
		} catch (ArithmeticException | NumberFormatException e) {
			throw new JsonFormatException("Integer value out of range or fractional", e);
		}
	}

	private static ConfigValue decimalValue(JsonPrimitive p) throws JsonFormatException {
		try {
			BigDecimal big = p.getAsBigDecimal();
			return new ConfigValue.DecimalValue(big.doubleValue());
		} catch (IllegalArgumentException e) {
			throw new JsonFormatException("Invalid decimal value", e);
		}
	}

	private static ConfigValue readList(JsonElement raw, int depth) throws JsonFormatException {
		if (!raw.isJsonArray()) {
			throw new JsonFormatException("List value must be an array");
		}
		JsonArray array = raw.getAsJsonArray();
		if (array.size() > JsonLimits.MAX_COLLECTION_SIZE) {
			throw new JsonFormatException("List value too large");
		}
		List<ConfigValue> values = new ArrayList<>(array.size());
		for (JsonElement e : array) {
			values.add(read(e, depth + 1));
		}
		return new ConfigValue.ListValue(values);
	}

	private static ConfigValue readStruct(JsonElement raw, int depth) throws JsonFormatException {
		if (!raw.isJsonObject()) {
			throw new JsonFormatException("Structured value must be an object");
		}
		JsonObject object = raw.getAsJsonObject();
		if (object.size() > JsonLimits.MAX_COLLECTION_SIZE) {
			throw new JsonFormatException("Structured value too large");
		}
		Map<String, ConfigValue> fields = new LinkedHashMap<>();
		for (Map.Entry<String, JsonElement> e : object.entrySet()) {
			fields.put(e.getKey(), read(e.getValue(), depth + 1));
		}
		return new ConfigValue.StructValue(fields);
	}
}
