package dev.worldconfigmanager.gametest;

import dev.worldconfigmanager.api.ConfigDefinition;
import dev.worldconfigmanager.api.ConfigKey;
import dev.worldconfigmanager.api.ConfigValue;
import dev.worldconfigmanager.api.ValueType;
import dev.worldconfigmanager.core.WorldSession;
import dev.worldconfigmanager.core.apply.ApplyOutcome;
import dev.worldconfigmanager.core.apply.ApplyStatus;
import dev.worldconfigmanager.fabric.WcmFabricMod;
import dev.worldconfigmanager.fabric.example.JoinMessageProvider;
import dev.worldconfigmanager.fabric.provider.GameRulesProvider;
import dev.worldconfigmanager.fabric.provider.WorldSettingsProvider;
import net.fabricmc.fabric.api.gametest.v1.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.server.MinecraftServer;
import net.minecraft.world.level.gamerules.GameRule;
import net.minecraft.world.level.storage.LevelResource;

import java.nio.file.Files;
import java.util.List;
import java.util.Map;

/** Server game tests: the vanilla providers and the example integration against a real running server. */
public final class WcmServerGameTests {
	private static final ConfigKey KEEP_INVENTORY = new ConfigKey(GameRulesProvider.ID, "minecraft:keep_inventory");
	private static final ConfigKey RANDOM_TICK = new ConfigKey(GameRulesProvider.ID, "minecraft:random_tick_speed");

	private static WorldSession session(GameTestHelper helper) {
		return WcmFabricMod.runtime().flatMap(r -> r.session()).orElseThrow(() -> new AssertionError("WCM session not running"));
	}

	private static Object vanillaValue(MinecraftServer server, String id) {
		GameRule<?> rule = server.getGameRules().availableRules().filter(r -> r.getIdentifier().toString().equals(id)).findFirst()
			.orElseThrow(() -> new AssertionError("rule " + id + " missing"));
		return server.getGameRules().get(rule);
	}

	@GameTest
	public void gameRulesAreDiscoveredFromTheGame(GameTestHelper helper) {
		WorldSession session = session(helper);
		long vanillaCount = helper.getLevel().getServer().getGameRules().availableRules().count();
		long managed = session.config().entries().stream().filter(e -> e.providerId().equals(GameRulesProvider.ID)).count();
		helper.assertValueEqual(managed, vanillaCount, "every available game rule is managed");
		ConfigDefinition keep = session.config().definition(KEEP_INVENTORY).orElseThrow();
		helper.assertValueEqual(keep.type(), ValueType.BOOLEAN, "keep_inventory type");
		helper.assertTrue(session.config().definition(new ConfigKey(WorldSettingsProvider.ID, "difficulty")).isPresent(), "difficulty managed");
		helper.succeed();
	}

	@GameTest
	public void applyingGameRulesChangesTheGameAndCanBeRestored(GameTestHelper helper) {
		WorldSession session = session(helper);
		MinecraftServer server = helper.getLevel().getServer();
		boolean before = (Boolean) vanillaValue(server, KEEP_INVENTORY.key());
		ApplyOutcome outcome = session.applyChanges(Map.of(KEEP_INVENTORY, ConfigValue.of(!before)), "gametest", false);
		helper.assertValueEqual(outcome.status(), ApplyStatus.SUCCESS, "apply status");
		helper.assertValueEqual(vanillaValue(server, KEEP_INVENTORY.key()), !before, "game rule changed in the game");
		try {
			ApplyOutcome restore = session.restoreBackup(outcome.backupId(), "gametest");
			helper.assertValueEqual(restore.status(), ApplyStatus.SUCCESS, "restore status");
		} catch (Exception e) {
			helper.fail("restore failed: " + e);
		}
		helper.assertValueEqual(vanillaValue(server, KEEP_INVENTORY.key()), before, "game rule restored");
		helper.succeed();
	}

	@GameTest
	public void invalidGameRuleValuesAreRejectedBeforeAnyChange(GameTestHelper helper) {
		WorldSession session = session(helper);
		MinecraftServer server = helper.getLevel().getServer();
		Object tickBefore = vanillaValue(server, RANDOM_TICK.key());
		boolean keepBefore = (Boolean) vanillaValue(server, KEEP_INVENTORY.key());
		int backupsBefore = session.backups().list().size();
		ApplyOutcome outcome = session.applyChanges(Map.of(
			KEEP_INVENTORY, ConfigValue.of(!keepBefore),
			RANDOM_TICK, ConfigValue.of(5_000_000_000L)), "gametest", false);
		helper.assertValueEqual(outcome.status(), ApplyStatus.VALIDATION_FAILED, "status");
		helper.assertValueEqual(vanillaValue(server, KEEP_INVENTORY.key()), keepBefore, "valid entry not applied either");
		helper.assertValueEqual(vanillaValue(server, RANDOM_TICK.key()), tickBefore, "invalid entry not applied");
		helper.assertValueEqual(session.backups().list().size(), backupsBefore, "no backup for refused operation");
		ApplyOutcome wrongType = session.applyChanges(Map.of(KEEP_INVENTORY, ConfigValue.of(1L)), "gametest", false);
		helper.assertValueEqual(wrongType.status(), ApplyStatus.VALIDATION_FAILED, "type mismatch refused");
		helper.succeed();
	}

	@GameTest
	public void exampleIntegrationPersistsThroughTheApi(GameTestHelper helper) {
		WorldSession session = session(helper);
		ConfigKey enabled = new ConfigKey(JoinMessageProvider.ID, "enabled");
		ConfigKey message = new ConfigKey(JoinMessageProvider.ID, "message");
		ConfigKey lines = new ConfigKey(JoinMessageProvider.ID, "extraLines");
		// Use a unique message so the test does not depend on state left by an earlier run.
		String text = "Hello from WCM " + System.nanoTime();
		ApplyOutcome outcome = session.applyChanges(Map.of(enabled, ConfigValue.of(true), message, ConfigValue.of(text),
			lines, new ConfigValue.ListValue(List.of(ConfigValue.of("Be nice")))), "gametest", false);
		helper.assertValueEqual(outcome.status(), ApplyStatus.SUCCESS, "example apply");
		helper.assertValueEqual(session.config().read(message).orElse(null), ConfigValue.of(text), "read back");
		helper.assertTrue(Files.exists(helper.getLevel().getServer().getWorldPath(LevelResource.ROOT)
			.resolve("worldconfigmanager/example_join_message.json")), "example file written");
		ApplyOutcome blank = session.applyChanges(Map.of(message, ConfigValue.of("   ")), "gametest", false);
		helper.assertValueEqual(blank.status(), ApplyStatus.VALIDATION_FAILED, "custom validator enforced");
		helper.succeed();
	}
}
