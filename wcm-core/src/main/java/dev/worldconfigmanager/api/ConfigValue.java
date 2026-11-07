package dev.worldconfigmanager.api;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.stream.Collectors;

/**
 * An immutable, typed configuration value. Comparison between values is always performed on the typed
 * representation, never on display strings.
 */
public sealed interface ConfigValue {
	/** @return the data type of this value */
	ValueType type();

	/** @return a short human-readable representation (for display only, never for comparison) */
	String display();

	static ConfigValue of(boolean value) {
		return new BoolValue(value);
	}

	static ConfigValue of(long value) {
		return new IntValue(value);
	}

	static ConfigValue of(double value) {
		return new DecimalValue(value);
	}

	static ConfigValue of(String value) {
		return new StringValue(value);
	}

	static ConfigValue ofEnum(String constant) {
		return new EnumValue(constant);
	}

	/** Boolean value. */
	record BoolValue(boolean value) implements ConfigValue {
		@Override
		public ValueType type() {
			return ValueType.BOOLEAN;
		}

		@Override
		public String display() {
			return Boolean.toString(value);
		}
	}

	/** Integer value (64-bit). */
	record IntValue(long value) implements ConfigValue {
		@Override
		public ValueType type() {
			return ValueType.INTEGER;
		}

		@Override
		public String display() {
			return Long.toString(value);
		}
	}

	/** Decimal value. NaN and infinities are rejected. */
	record DecimalValue(double value) implements ConfigValue {
		public DecimalValue {
			if (!Double.isFinite(value)) {
				throw new IllegalArgumentException("Decimal values must be finite");
			}
		}

		@Override
		public ValueType type() {
			return ValueType.DECIMAL;
		}

		@Override
		public String display() {
			return Double.toString(value);
		}
	}

	/** String value. */
	record StringValue(String value) implements ConfigValue {
		public StringValue {
			Objects.requireNonNull(value, "value");
		}

		@Override
		public ValueType type() {
			return ValueType.STRING;
		}

		@Override
		public String display() {
			return value;
		}
	}

	/** Enum constant, stored by its serialized name. */
	record EnumValue(String constant) implements ConfigValue {
		public EnumValue {
			Objects.requireNonNull(constant, "constant");
		}

		@Override
		public ValueType type() {
			return ValueType.ENUM;
		}

		@Override
		public String display() {
			return constant;
		}
	}

	/** Ordered list of values. */
	record ListValue(List<ConfigValue> values) implements ConfigValue {
		public ListValue {
			values = List.copyOf(values);
		}

		@Override
		public ValueType type() {
			return ValueType.LIST;
		}

		@Override
		public String display() {
			return values.stream().map(ConfigValue::display).collect(Collectors.joining(", ", "[", "]"));
		}
	}

	/** Structured value: an ordered map of named fields. */
	record StructValue(Map<String, ConfigValue> fields) implements ConfigValue {
		public StructValue {
			fields = java.util.Collections.unmodifiableMap(new LinkedHashMap<>(fields));
		}

		@Override
		public ValueType type() {
			return ValueType.STRUCTURED;
		}

		@Override
		public String display() {
			return fields.entrySet().stream()
				.map(e -> e.getKey() + "=" + e.getValue().display())
				.collect(Collectors.joining(", ", "{", "}"));
		}
	}
}
