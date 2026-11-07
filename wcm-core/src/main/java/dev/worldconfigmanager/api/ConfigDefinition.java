package dev.worldconfigmanager.api;

import java.util.Objects;
import java.util.regex.Pattern;
import java.util.regex.PatternSyntaxException;

/**
 * Immutable description of a single setting offered by a {@link ConfigProvider}.
 *
 * @param key            provider-local key
 * @param displayName    player-facing name
 * @param description    player-facing description
 * @param category       browser category
 * @param type           value type
 * @param defaultValue   default value (must have {@code type})
 * @param constraints    declarative validation rules
 * @param validator      additional custom validation
 * @param scope          configuration scope
 * @param reloadBehavior how a change takes effect
 * @param sensitive      if {@code true}, values are masked in history and never synchronized to unauthorized clients
 */
public record ConfigDefinition(String key, LocalizedText displayName, LocalizedText description, ConfigCategory category,
	ValueType type, ConfigValue defaultValue, ValueConstraints constraints, ConfigValidator validator, ConfigScope scope,
	ReloadBehavior reloadBehavior, boolean sensitive) {

	public ConfigDefinition {
		Objects.requireNonNull(key, "key");
		Objects.requireNonNull(displayName, "displayName");
		Objects.requireNonNull(description, "description");
		Objects.requireNonNull(category, "category");
		Objects.requireNonNull(type, "type");
		Objects.requireNonNull(defaultValue, "defaultValue");
		Objects.requireNonNull(scope, "scope");
		Objects.requireNonNull(reloadBehavior, "reloadBehavior");
		constraints = constraints == null ? ValueConstraints.NONE : constraints;
		validator = validator == null ? ConfigValidator.ACCEPT_ALL : validator;
		if (defaultValue.type() != type) {
			throw new IllegalArgumentException("Default value of " + key + " has type " + defaultValue.type() + ", expected " + type);
		}
		if (type == ValueType.ENUM && constraints.allowedValues().isEmpty()) {
			throw new IllegalArgumentException("Enum setting " + key + " must declare allowed values");
		}
	}

	/**
	 * Validates a candidate value against the declared type, constraints and custom validator.
	 */
	public ValidationResult validate(ConfigValue value) {
		if (value == null) {
			return ValidationResult.error("wcm.validation.missing");
		}
		if (value.type() != type) {
			return ValidationResult.error("wcm.validation.type", type.id(), value.type().id());
		}
		ValidationResult builtIn = validateConstraints(value);
		return builtIn.valid() ? validator.validate(value) : builtIn;
	}

	private ValidationResult validateConstraints(ConfigValue value) {
		ValueConstraints c = constraints;
		switch (value) {
			case ConfigValue.IntValue(long v) -> {
				if (c.min() != null && v < c.min()) {
					return ValidationResult.error("wcm.validation.min", formatNumber(c.min()));
				}
				if (c.max() != null && v > c.max()) {
					return ValidationResult.error("wcm.validation.max", formatNumber(c.max()));
				}
			}
			case ConfigValue.DecimalValue(double v) -> {
				if (c.min() != null && v < c.min()) {
					return ValidationResult.error("wcm.validation.min", formatNumber(c.min()));
				}
				if (c.max() != null && v > c.max()) {
					return ValidationResult.error("wcm.validation.max", formatNumber(c.max()));
				}
			}
			case ConfigValue.StringValue(String v) -> {
				if (c.maxLength() != null && v.length() > c.maxLength()) {
					return ValidationResult.error("wcm.validation.max_length", Integer.toString(c.maxLength()));
				}
				if (c.pattern() != null && !matches(c.pattern(), v)) {
					return ValidationResult.error("wcm.validation.pattern", c.pattern());
				}
			}
			case ConfigValue.EnumValue(String constant) -> {
				if (!c.allowedValues().contains(constant)) {
					return ValidationResult.error("wcm.validation.enum", String.join(", ", c.allowedValues()));
				}
			}
			case ConfigValue.ListValue(var values) -> {
				if (c.maxLength() != null && values.size() > c.maxLength()) {
					return ValidationResult.error("wcm.validation.max_size", Integer.toString(c.maxLength()));
				}
				if (c.elementType() != null) {
					for (ConfigValue element : values) {
						if (element.type() != c.elementType()) {
							return ValidationResult.error("wcm.validation.element_type", c.elementType().id());
						}
					}
				}
			}
			case ConfigValue.BoolValue ignored -> {
				// Booleans have no constraints beyond their type.
			}
			case ConfigValue.StructValue ignored -> {
				// Structured values are validated by the custom validator.
			}
		}
		return ValidationResult.ok();
	}

	private static boolean matches(String regex, String value) {
		try {
			return Pattern.compile(regex).matcher(value).matches();
		} catch (PatternSyntaxException e) {
			return false;
		}
	}

	private static String formatNumber(double d) {
		return d == Math.rint(d) && Math.abs(d) < 1e15 ? Long.toString((long) d) : Double.toString(d);
	}

	/** @return the global key of this definition for the given provider */
	public ConfigKey globalKey(String providerId) {
		return new ConfigKey(providerId, key);
	}

	public static Builder builder(String key, ValueType type, ConfigValue defaultValue) {
		return new Builder(key, type, defaultValue);
	}

	/** Fluent builder for {@link ConfigDefinition}. */
	public static final class Builder {
		private final String key;
		private final ValueType type;
		private final ConfigValue defaultValue;
		private LocalizedText displayName;
		private LocalizedText description = LocalizedText.literal("");
		private ConfigCategory category = ConfigCategory.OTHER_MODS;
		private ValueConstraints constraints = ValueConstraints.NONE;
		private ConfigValidator validator = ConfigValidator.ACCEPT_ALL;
		private ConfigScope scope = ConfigScope.WORLD;
		private ReloadBehavior reloadBehavior = ReloadBehavior.HOT_RELOAD;
		private boolean sensitive;

		private Builder(String key, ValueType type, ConfigValue defaultValue) {
			this.key = key;
			this.type = type;
			this.defaultValue = defaultValue;
			this.displayName = LocalizedText.literal(key);
		}

		public Builder displayName(LocalizedText displayName) {
			this.displayName = displayName;
			return this;
		}

		public Builder description(LocalizedText description) {
			this.description = description;
			return this;
		}

		public Builder category(ConfigCategory category) {
			this.category = category;
			return this;
		}

		public Builder constraints(ValueConstraints constraints) {
			this.constraints = constraints;
			return this;
		}

		public Builder validator(ConfigValidator validator) {
			this.validator = validator;
			return this;
		}

		public Builder scope(ConfigScope scope) {
			this.scope = scope;
			return this;
		}

		public Builder reloadBehavior(ReloadBehavior reloadBehavior) {
			this.reloadBehavior = reloadBehavior;
			return this;
		}

		public Builder sensitive(boolean sensitive) {
			this.sensitive = sensitive;
			return this;
		}

		public ConfigDefinition build() {
			return new ConfigDefinition(key, displayName, description, category, type, defaultValue, constraints, validator,
				scope, reloadBehavior, sensitive);
		}
	}
}
