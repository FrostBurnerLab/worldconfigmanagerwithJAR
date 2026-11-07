package dev.worldconfigmanager.api;

import java.util.List;
import java.util.Optional;

/**
 * Declarative validation rules for a setting. All fields are optional.
 *
 * @param min           inclusive minimum for numeric values
 * @param max           inclusive maximum for numeric values
 * @param allowedValues allowed enum constants (required for {@link ValueType#ENUM})
 * @param maxLength     maximum string length or list size
 * @param pattern       regular expression a string value must fully match
 * @param elementType   required element type for list values
 */
public record ValueConstraints(Double min, Double max, List<String> allowedValues, Integer maxLength, String pattern,
	ValueType elementType) {
	public static final ValueConstraints NONE = new ValueConstraints(null, null, List.of(), null, null, null);

	public ValueConstraints {
		allowedValues = allowedValues == null ? List.of() : List.copyOf(allowedValues);
		if (min != null && max != null && min > max) {
			throw new IllegalArgumentException("min > max");
		}
	}

	public static ValueConstraints range(double min, double max) {
		return new ValueConstraints(min, max, List.of(), null, null, null);
	}

	public static ValueConstraints oneOf(List<String> values) {
		return new ValueConstraints(null, null, values, null, null, null);
	}

	public static ValueConstraints maxLength(int maxLength) {
		return new ValueConstraints(null, null, List.of(), maxLength, null, null);
	}

	public static ValueConstraints listOf(ValueType elementType, int maxSize) {
		return new ValueConstraints(null, null, List.of(), maxSize, null, elementType);
	}

	public Optional<Double> minimum() {
		return Optional.ofNullable(min);
	}

	public Optional<Double> maximum() {
		return Optional.ofNullable(max);
	}
}
