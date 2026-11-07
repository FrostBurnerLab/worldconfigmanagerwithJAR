# WorldConfig Manager — Developer Guide

How to build, test and extend WCM. See `architecture.md` for the design.

## Toolchain

| Component | Version |
|---|---|
| Java | 25 (toolchain; set `JAVA_HOME` to a JDK 25) |
| Gradle | 9.8.2 (wrapper included) |
| Fabric Loom | 1.19.4 (`net.fabricmc.fabric-loom`, Minecraft 26.x is unobfuscated — no mappings) |
| Minecraft | 26.3 |
| Fabric Loader | 0.20.1 |
| Fabric API | 0.165.2+26.3 |
| Gson / SLF4J | 2.14.0 / 2.0.17 (provided by Minecraft at runtime) |
| JUnit | 5.13.4 |

Versions were checked against `meta.fabricmc.net` and `maven.fabricmc.net` on 2026-11-07. The official example mod
uses a Loom snapshot; this project pins the latest stable Loom release instead.

## Build

```bash
./gradlew build
```

Outputs:

* `wcm-fabric/build/libs/worldconfigmanager-0.1.0.jar` — the mod (contains the core as nested jar)
* `wcm-fabric/build/libs/worldconfigmanager-0.1.0-sources.jar`
* `wcm-core/build/libs/worldconfigmanager-core-0.1.0.jar`

The build uses `-Xlint:all` (minus `processing`, `serial` and `classfile`; the latter only reports missing
error-prone annotation classes referenced by Gson). The build is warning-free.

## Tests

| Suite | Command | What it covers |
|---|---|---|
| Unit tests (65) | `./gradlew :wcm-core:test` | profiles, validation, scopes, apply/rollback, backups, history, import/export hardening, compatibility, migration, protocol negotiation, permissions, rate limiting, redaction, storage |
| Server game tests (4) | `./gradlew :wcm-fabric:runGameTest` | real dedicated test server: game rule discovery, apply + restore, invalid values, example integration. JUnit report: `wcm-fabric/build/reports/gametest/server-gametest.xml` |
| Client game test | see below | real client: singleplayer handshake, all pages, network apply, local client profiles, GUI scaling, dedicated server permissions (deny → op → allow). Screenshots in `wcm-fabric/build/run/clientGameTest/screenshots/` |

The client game test needs a display. On a headless Linux machine:

```bash
XDG_RUNTIME_DIR=/tmp/xdg VK_ICD_FILENAMES=/usr/share/vulkan/icd.d/lvp_icd.json xvfb-run -a -s "-screen 0 1920x1080x24" ./gradlew :wcm-fabric:runClientGameTest
```

Minecraft 26.3 needs OpenGL or Vulkan through SDL3. Under Xvfb the OpenGL path failed ("Couldn't find matching GLX
visual"); the Vulkan backend works with Mesa's software driver lavapipe (`mesa-vulkan-drivers`).

Manual testing:

```bash
./gradlew :wcm-fabric:runClient
```

```bash
./gradlew :wcm-fabric:runServer
```

## Project layout

```
wcm-core/src/main/java/dev/worldconfigmanager/api     public API
wcm-core/src/main/java/dev/worldconfigmanager/core    domain, services, storage, protocol
wcm-core/src/test/java                                 unit tests
wcm-fabric/src/main                                    Fabric common/server code + resources (lang, icon, fabric.mod.json)
wcm-fabric/src/client                                  client code (GUI)
wcm-fabric/src/gametest                                in-game tests
tools/generate_lang.py                                 source of en_us.json / nl_nl.json
```

## Adding a translation key

Edit `tools/generate_lang.py` (each entry has English and Dutch), then:

```bash
python3 tools/generate_lang.py wcm-fabric/src/main/resources/assets/worldconfigmanager/lang
```

To add a language, add a column to the generator (or a new JSON file with the same keys).

## Adding a protocol request

1. Add a route in `ServerProtocolHandler.registerRoutes()` with the required `PermissionAction`, whether it writes, and
   its rate-limit cost.
2. Encode results with `ProtocolCodec`.
3. Add a unit test in `ProtocolTest` for both the allowed and the denied case.
4. If it changes state that other players may be looking at, add the type to `WcmServerRuntime.BROADCAST_ON`.

Bump `WcmProtocol.VERSION` for incompatible changes; keep `MIN_VERSION` as low as you can still serve.

## Adding a persisted field or format change

Increase the schema version of the document, add a `SchemaMigrator` step from the previous version and a test that a
document of the previous version loads correctly.

## Releasing

1. Update `mod_version` in `gradle.properties` **and** `WcmVersion.MOD_VERSION` (a unit test checks they match).
2. Update `CHANGELOG.md`.
3. Run all three test suites and `./gradlew build`.
