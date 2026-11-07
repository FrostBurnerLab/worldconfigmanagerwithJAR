# WorldConfig Manager — Integration API Guide

API version **1.0.0** (`dev.worldconfigmanager.api.WcmApi.API_VERSION`). Everything in `dev.worldconfigmanager.api`
is public and follows semantic versioning: providers built against API 1.x keep working with every WCM release that
reports API major version 1. Everything outside that package is internal.

## Overview

1. Implement `ConfigProvider` for your mod's settings.
2. Implement `WcmPlugin` and register the provider.
3. Declare the plugin under the `worldconfigmanager` entrypoint in `fabric.mod.json`.

WCM then lists your settings in the browser, includes them in profiles, previews, backups, history, comparisons and
`.wcmp` packages, validates values, enforces permissions and rolls back failed changes.

## Dependency

The API classes ship inside `worldconfigmanager-0.1.0.jar` (nested core jar). Compile against the WCM jar
(e.g. `modCompileOnly`/`compileOnly` on a local file or your maven) and make WCM an optional dependency:

```json
"suggests": { "worldconfigmanager": ">=0.1.0" },
"entrypoints": { "worldconfigmanager": ["com.example.mymod.MyWcmPlugin"] }
```

Fabric only loads the entrypoint when WCM is installed, so your mod keeps working without it — just keep all WCM
references inside the plugin and provider classes.

## The plugin

```java
public final class MyWcmPlugin implements WcmPlugin {
    @Override
    public void registerProviders(ProviderRegistrar registrar) {
        registrar.register(new MyModProvider());
    }
}
```

Plugins load on client and server during mod initialization. A plugin that throws is skipped and logged; it never
crashes the game.

## The provider contract

```java
public interface ConfigProvider {
    ConfigMetadata metadata();
    List<ConfigDefinition> definitions(ProviderContext context);
    Optional<ConfigValue> read(String key, ProviderContext context);
    ConfigApplyResult apply(List<ConfigChange> changes, ProviderContext context);
    default boolean isAvailable(ProviderContext context);
    default ValidationResult validate(String key, ConfigValue value, ProviderContext context);
    default ConfigSnapshot snapshot(ProviderContext context);
    default ConfigApplyResult restore(ConfigSnapshot snapshot, ProviderContext context);
}
```

Rules:

* `apply` is only called with values that passed `validate`. Return which keys were applied and which failed (with a
  message). Never throw for expected failures.
* If `metadata().transactional()` is `true`, `apply` must apply all changes or none (e.g. one atomic file write). If it
  is `false`, WCM rolls partial changes back by calling `restore` with the pre-change snapshot.
* World and server scoped calls run **on the server thread**; client-scoped calls on the render thread.
* Only touch your own configuration. Do not modify files of other mods.
* Definitions are cached by WCM until the server stops. If your set of settings changes at runtime, WCM picks up the
  new set on the next server start.

### Metadata

```java
new ConfigMetadata(
    "mymod:main",                 // provider id: lowercase, [a-z0-9_.-], optional ":path"
    "mymod",                      // owning mod id (shown on the Integrations page)
    LocalizedText.translatable("mymod.wcm.provider", "My Mod"),
    "1.2.0",                      // provider version (semver) — major changes are reported as compatibility warnings
    1,                            // schema version of your keys/types
    WcmApi.API_MAJOR,             // API major you built against
    Set.of(ConfigScope.WORLD),    // scopes you offer settings in
    true);                        // transactional apply
```

### Definitions and values

```java
ConfigDefinition.builder("max_spawns", ValueType.INTEGER, ConfigValue.of(10L))
    .displayName(LocalizedText.translatable("mymod.wcm.max_spawns", "Max spawns"))
    .description(LocalizedText.translatable("mymod.wcm.max_spawns.desc", "How many …"))
    .category(ConfigCategory.MOBS_AND_ENTITIES)
    .constraints(ValueConstraints.range(0, 100))
    .validator(v -> ValidationResult.ok())          // optional extra rule
    .scope(ConfigScope.WORLD)
    .reloadBehavior(ReloadBehavior.HOT_RELOAD)       // or WORLD_RELOAD / GAME_RESTART — must be explicit
    .sensitive(false)                                // true: masked in history, hidden from users who cannot edit
    .build();
```

Value types: `BOOLEAN` (`ConfigValue.of(boolean)`), `INTEGER` (`of(long)`), `DECIMAL` (`of(double)`, finite only),
`STRING` (`of(String)`), `ENUM` (`ofEnum(String)`, requires `ValueConstraints.oneOf(...)`), `LIST`
(`new ConfigValue.ListValue(...)`, optional element type and max size) and `STRUCTURED`
(`new ConfigValue.StructValue(map)`, validate with a custom validator; shown read-only in the GUI).

Values are compared by type and value: `1` (integer) and `1.0` (decimal) are different.

### Scopes

| Scope | Stored | Edited through | Synchronized |
|---|---|---|---|
| `CLIENT` | on the player's computer | the player's local client profiles | never |
| `WORLD` | with the world | the server (permission-checked) | values on request only |
| `SERVER` | by the server, independent of a world | the server (permission-checked) | values on request only |

### ProviderContext

`context.scopes()` tells you which scopes are being managed, `context.worldId()` the persistent world UUID and
`context.service(Class)` gives platform objects: `MinecraftServer` on the server, `Minecraft` on the client.

```java
MinecraftServer server = context.service(MinecraftServer.class).orElseThrow();
Path file = server.getWorldPath(LevelResource.ROOT).resolve("mymod-wcm.json");
```

Override `isAvailable` to return `false` when you cannot serve requests (e.g. the service is missing). Entries of an
unavailable provider are preserved in profiles and reported as unavailable — never deleted.

## Complete example

`wcm-fabric/src/main/java/dev/worldconfigmanager/fabric/example/` contains a working integration that uses only the
public API:

* `ExampleWcmPlugin` — registered through the `worldconfigmanager` entrypoint in WCM's own `fabric.mod.json`, exactly
  like a third-party mod.
* `JoinMessageProvider` — a transactional, world-scoped provider with boolean, string (custom validator), enum and
  list settings, persisted with an atomic file write to `<world>/worldconfigmanager/example_join_message.json`. When
  enabled, joining players receive the configured announcement.

It is covered by the server game test `exampleIntegrationPersistsThroughTheApi`.

## Compatibility behaviour your users will see

* Provider not installed → *missing mod* (entries kept, skipped on apply).
* Key no longer offered → *unsupported setting*.
* Stored value of a different type → *type changed* (error, apply refused for that profile).
* Value outside your constraints → *invalid value*.
* Profile saved with a different provider **major** version → *version conflict* warning.
* Profile saved with a newer provider version → warning.

Increase your provider major version when keys or types change incompatibly.

## Versioning of this API

| API | WCM | Notes |
|---|---|---|
| 1.0.0 | 0.1.0 | Initial release |
