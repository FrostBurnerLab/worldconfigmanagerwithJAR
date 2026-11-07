# Development Status — WorldConfig Manager 0.1.0

Last updated: 2026-11-07

## Last successful build

`./gradlew clean build` — **BUILD SUCCESSFUL**, 0 compiler warnings (`-Xlint:all`, see developer guide), 0 Gradle
deprecation warnings.

| Artifact | Location |
|---|---|
| Mod JAR | `wcm-fabric/build/libs/worldconfigmanager-0.1.0.jar` |
| Sources JAR | `wcm-fabric/build/libs/worldconfigmanager-0.1.0-sources.jar` |
| Core library | `wcm-core/build/libs/worldconfigmanager-core-0.1.0.jar` (also nested inside the mod JAR) |

Target: Minecraft 26.3, Fabric Loader 0.20.1, Fabric API 0.165.2+26.3, Fabric Loom 1.19.4, Gradle 9.8.2, Java 25.

## Test results

| Suite | Result | Notes |
|---|---|---|
| Unit tests (`:wcm-core:test`) | **65 passed, 0 failed, 0 skipped** | ProfileTest 10, ConfigValidationTest 7, ApplyAndBackupTest 11, HistoryTest 4, TransferTest 10, CompatibilityTest 5, ProtocolTest 13, StorageTest 5 |
| Server game tests (`:wcm-fabric:runGameTest`) | **4/4 passed** (+ vanilla `always_pass`) | Report: `docs/test-results-server-gametest.xml`. Run twice in a row to confirm repeatability. |
| Client game test (`:wcm-fabric:runClientGameTest`) | **passed** | Real client under Xvfb with Mesa lavapipe (software Vulkan). 23 screenshots. |
| Production smoke test | **passed** | Release JAR on the official Fabric server launcher (26.3 / 0.20.1) with Fabric API, driven by console commands, including a server restart. |

### What the in-game tests verified

Server game tests (real server):
* every game rule available in the running game is managed (59 in 26.3), with correct types;
* applying a game rule changes the game's value; restoring the backup reverts it;
* an invalid value (out of range, wrong type) refuses the whole change set: nothing applied, no backup;
* the example integration applies and persists through the public API; its custom validator is enforced.

Client game test (real client):
* singleplayer handshake → role OWNER, protocol 1;
* every page renders (Dashboard, Profiles, Configurations, Comparison, History & Backups, Integrations, Settings);
* dialogs open through their real buttons (Profile actions, New profile, Import);
* export through the GUI writes `config/worldconfigmanager/exports/Creative_Builder.wcmp`; importing it through the
  Import dialog (preview → import) succeeds on the server;
* applying a profile over the network changes the server's game rule;
* local client profiles: changing FOV through the local backend changes the client option;
* Escape leaves the narrow configuration editor before closing the screen;
* layouts at GUI sizes 320×240 (minimum), 426×240 and 640×360;
* dedicated server: non-op player → role PLAYER, `settings.apply` **denied server-side** and the game rule unchanged;
  after `op`, the same request is **allowed** and changes the game rule.

Production smoke test (release JAR, dedicated server, console):
* starter profiles created; `wcm status`, `profile list`, `profile preview`, `profile apply` verified against vanilla
  `/gamerule` and `/difficulty`; history and automatic backups recorded; `backup restore` reverted the values;
  profile creation and association; after restart: same world UUID, 6 profiles, history and backups intact.

## Completed modules

| Module | Status |
|---|---|
| Main dashboard | Done |
| World profile manager | Done |
| Configuration manager (search, filters, typed editors, staging, preview) | Done |
| Configuration scopes (CLIENT, WORLD, SERVER) | Done (no built-in SERVER-scope provider yet) |
| Vanilla integration (game rules, difficulty, client options) | Done |
| Backup manager (create, list, restore, compare, export, delete, retention) | Done |
| Configuration history (filters, retention, restore-before) | Done |
| Configuration comparison | Done |
| Import & export (`.wcmp`, preview, merge/replace, hardening) | Done |
| Mod integration API + example provider | Done |
| Multiplayer & networking (negotiation, read-only fallback, limits) | Done |
| Compatibility system + schema migrations | Done |
| Permissions (roles, configurable policy, server-side checks) | Done |
| WCM general settings | Done |
| Commands | Done |
| Localization (en_us, nl_nl) | Done (427 keys each) |
| Documentation | Done |

## Acceptance criteria

| # | Criterion | Status | Evidence |
|---|---|---|---|
| 1 | Mod builds | ✅ | `./gradlew build` |
| 2 | Launches in the dev environment | ✅ | client and server game tests |
| 3 | WCM interface can be opened | ✅ | client game test screenshots |
| 4 | Dashboard shows real world information | ✅ | screenshots; `DashboardSnapshot` from live services |
| 5 | Create and manage profiles | ✅ | ProfileTest, ProtocolTest, commands, GUI dialogs |
| 6 | Profiles persist across restarts | ✅ | production server restart; ProfileTest |
| 7 | Profiles associated with worlds | ✅ | commands + restart; protocol |
| 8 | Vanilla game rules managed | ✅ | server game tests; production smoke test |
| 9 | Preview before application | ✅ | `profiles.preview`, PreviewDialog, `/wcm profile preview` |
| 10 | Backup before applying | ✅ | ApplyAndBackupTest; production smoke test |
| 11 | Previous configuration restorable | ✅ | game test; production smoke test |
| 12 | Profiles exportable | ✅ | TransferTest; client game test (file written) |
| 13 | Valid profiles importable | ✅ | TransferTest; client game test (GUI import) |
| 14 | Invalid imports rejected safely | ✅ | TransferTest (not a zip, corrupt, traversal, unexpected entries, oversize/zip bomb, checksum, version) |
| 15 | Configuration history works | ✅ | HistoryTest; screenshots; `/wcm history` |
| 16 | Incompatible settings reported | ✅ | CompatibilityTest; import preview; comparison |
| 17 | Integration API demonstrably functional | ✅ | example provider game test |
| 18 | Multiplayer permissions checked server-side | ✅ | ProtocolTest; dedicated-server client test |
| 19 | No placeholder buttons | ✅ | every button is wired to a working action |
| 20 | README accurate | ✅ | README lists only verified features and states limitations |

## Known bugs

None open. Bugs found and fixed during verification:
* Layout overlaps at the minimum GUI size (sidebar, dashboard, profile actions, config editor, settings) — fixed and
  re-verified at 320×240.
* Import dialog could throw a `NullPointerException` when Preview was triggered without a selected file — fixed (guard
  plus the newest package is pre-selected).
* After restoring a backup the dashboard still showed the previously applied profile — restore now clears it.
* Sensitive values could reach users without edit permission through `profiles.get`, `compare` and exports — now
  redacted server-side (ProtocolTest).

## Remaining work / ideas

* A built-in `SERVER`-scope provider (e.g. selected `server.properties` values that can change at runtime).
* Managing server and client settings side by side in one view (currently a Server/Client switch).
* Editing structured values in the GUI.
* More languages.
* A NeoForge front end on top of `wcm-core`.
* Optional: publish the API as a separate Maven artifact.

## Technical notes and blockers encountered

* The environment's network policy initially blocked `maven.fabricmc.net`, `meta.fabricmc.net` and Mojang's hosts; they
  were added to the environment's allowed hosts (with approval) so Loom could download Minecraft.
* Minecraft 26.3 renders through SDL3 with OpenGL or Vulkan. Under Xvfb, OpenGL failed ("Couldn't find matching GLX
  visual"); Vulkan works with Mesa's lavapipe (`mesa-vulkan-drivers`). The client was therefore tested with software
  rendering, not on GPU hardware.
* Not verified in game: a WCM client joining a server **without** WCM (local-only mode). The code path is implemented
  and the local backend is exercised in singleplayer, but no vanilla server was used in testing.
* Mojang authentication services are unreachable in the test environment; the resulting log warnings are unrelated to
  WCM.
