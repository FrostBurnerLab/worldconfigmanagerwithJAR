package dev.worldconfigmanager.core;

import dev.worldconfigmanager.api.ConfigDefinition;
import dev.worldconfigmanager.api.ConfigKey;
import dev.worldconfigmanager.api.ConfigScope;
import dev.worldconfigmanager.api.ConfigValue;
import dev.worldconfigmanager.api.ValidationResult;
import dev.worldconfigmanager.api.ValueConstraints;
import dev.worldconfigmanager.api.ValueType;
import dev.worldconfigmanager.core.apply.ApplyPlan;
import dev.worldconfigmanager.core.apply.ChangeStatus;
import dev.worldconfigmanager.core.config.ConfigService;
import dev.worldconfigmanager.core.json.JsonFormatException;
import dev.worldconfigmanager.core.json.ValueJson;
import dev.worldconfigmanager.core.registry.ProviderRegistry;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Path;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ConfigValidationTest {
	@TempDir
	Path dir;

	@Test
	void typeValidation() {
		ConfigDefinition def = ConfigDefinition.builder("n", ValueType.INTEGER, ConfigValue.of(1L)).build();
		assertTrue(def.validate(ConfigValue.of(5L)).valid());
		ValidationResult wrong = def.validate(ConfigValue.of("5"));
		assertFalse(wrong.valid());
		assertEquals("wcm.validation.type", wrong.messageKey());
		assertFalse(def.validate(ConfigValue.of(5.0)).valid());
		assertFalse(def.validate(null).valid());
	}

	@Test
	void valueConstraints() {
		ConfigDefinition range = ConfigDefinition.builder("r", ValueType.INTEGER, ConfigValue.of(5L)).constraints(ValueConstraints.range(0, 10)).build();
		assertTrue(range.validate(ConfigValue.of(10L)).valid());
		assertEquals("wcm.validation.max", range.validate(ConfigValue.of(11L)).messageKey());
		assertEquals("wcm.validation.min", range.validate(ConfigValue.of(-1L)).messageKey());

		ConfigDefinition en = ConfigDefinition.builder("e", ValueType.ENUM, ConfigValue.ofEnum("a")).constraints(ValueConstraints.oneOf(List.of("a", "b"))).build();
		assertTrue(en.validate(ConfigValue.ofEnum("b")).valid());
		assertFalse(en.validate(ConfigValue.ofEnum("c")).valid());

		ConfigDefinition str = ConfigDefinition.builder("s", ValueType.STRING, ConfigValue.of("ab"))
			.constraints(new ValueConstraints(null, null, List.of(), 5, "[a-z]+", null)).build();
		assertTrue(str.validate(ConfigValue.of("abc")).valid());
		assertEquals("wcm.validation.pattern", str.validate(ConfigValue.of("ab1")).messageKey());
		assertEquals("wcm.validation.max_length", str.validate(ConfigValue.of("abcdef")).messageKey());

		ConfigDefinition list = ConfigDefinition.builder("l", ValueType.LIST, new ConfigValue.ListValue(List.of()))
			.constraints(ValueConstraints.listOf(ValueType.STRING, 2)).build();
		assertTrue(list.validate(new ConfigValue.ListValue(List.of(ConfigValue.of("x")))).valid());
		assertFalse(list.validate(new ConfigValue.ListValue(List.of(ConfigValue.of(1L)))).valid());
		assertFalse(list.validate(new ConfigValue.ListValue(List.of(ConfigValue.of("a"), ConfigValue.of("b"), ConfigValue.of("c")))).valid());

		ConfigDefinition custom = ConfigDefinition.builder("c", ValueType.INTEGER, ConfigValue.of(2L))
			.validator(v -> ((ConfigValue.IntValue) v).value() % 2 == 0 ? ValidationResult.ok() : ValidationResult.error("test.even")).build();
		assertEquals("test.even", custom.validate(ConfigValue.of(3L)).messageKey());
	}

	@Test
	void definitionsRejectInconsistentDeclarations() {
		assertThrows(IllegalArgumentException.class, () -> ConfigDefinition.builder("x", ValueType.INTEGER, ConfigValue.of(true)).build());
		assertThrows(IllegalArgumentException.class, () -> ConfigDefinition.builder("x", ValueType.ENUM, ConfigValue.ofEnum("a")).build());
		assertThrows(IllegalArgumentException.class, () -> new ConfigValue.DecimalValue(Double.NaN));
	}

	@Test
	void valueJsonRejectsMismatchedOrHostileInput() throws Exception {
		assertThrows(JsonFormatException.class, () -> ValueJson.read(com.google.gson.JsonParser.parseString("{\"type\":\"boolean\",\"value\":\"true\"}")));
		assertThrows(JsonFormatException.class, () -> ValueJson.read(com.google.gson.JsonParser.parseString("{\"type\":\"integer\",\"value\":1.5}")));
		assertThrows(JsonFormatException.class, () -> ValueJson.read(com.google.gson.JsonParser.parseString("{\"type\":\"nope\",\"value\":1}")));
		String deep = "{\"type\":\"list\",\"value\":[".repeat(40) + "]}".repeat(40);
		assertThrows(JsonFormatException.class, () -> ValueJson.read(com.google.gson.JsonParser.parseString(deep)));
	}

	@Test
	void scopeFilteringHidesClientSettingsOnServer() {
		ProviderRegistry registry = new ProviderRegistry();
		TestProvider p = TestProvider.standard("p");
		p.define(ConfigDefinition.builder("clientOnly", ValueType.BOOLEAN, ConfigValue.of(true)).scope(ConfigScope.CLIENT).build());
		registry.register(p);
		ConfigService service = new ConfigService(registry, TestSupport.serverContext(null));
		assertEquals(3, service.entries().size());
		assertTrue(service.definition(new ConfigKey("p", "clientOnly")).isEmpty());
		assertTrue(service.definition(new ConfigKey("p", "flag")).isPresent());
	}

	@Test
	void duplicateProviderIdsAreRejected() {
		ProviderRegistry registry = new ProviderRegistry();
		registry.register(TestProvider.standard("p"));
		assertThrows(IllegalArgumentException.class, () -> registry.register(TestProvider.standard("p")));
	}

	@Test
	void missingProviderAndChangedValueDetection() throws Exception {
		ProviderRegistry registry = new ProviderRegistry();
		registry.register(TestProvider.standard("p"));
		WorldSession session = TestSupport.session(dir, registry, TestSupport.clock());
		ConfigService service = session.config();
		assertEquals("wcm.validation.provider_unavailable", service.validate(new ConfigKey("missing", "x"), ConfigValue.of(true)).messageKey());

		ApplyPlan plan = session.engine().plan(Map.of(
			new ConfigKey("p", "flag"), ConfigValue.of(true),
			new ConfigKey("p", "count"), ConfigValue.of(10L),
			new ConfigKey("p", "mode"), ConfigValue.ofEnum("impossible"),
			new ConfigKey("missing", "x"), ConfigValue.of(1L)));
		assertEquals(1, plan.count(ChangeStatus.CHANGE));
		assertEquals(1, plan.count(ChangeStatus.UNCHANGED));
		assertEquals(1, plan.count(ChangeStatus.INVALID));
		assertEquals(1, plan.count(ChangeStatus.UNAVAILABLE));
	}
}
