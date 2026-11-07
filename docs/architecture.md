# WorldConfig Manager — Architecture

This document describes how WorldConfig Manager (WCM) 0.1.0 is structured and why. It reflects the code in this
repository; if the two disagree, the code is authoritative and this file should be fixed.

## Goals that shaped the design

1. **Data safety first.** No configuration change happens without validation, a prior backup and a recorded
   history entry. Failed operations roll back and say so.
2. **Server authority.** The server decides everything about world and server settings. Clients only send requests;
   the server re-checks permissions and validates every value.
3. **Loader independence.** All business logic lives in a plain-Java module (`wcm-core`) with no Minecraft or Fabric
   dependency, so a NeoForge front end can reuse it and so it can be unit-tested quickly.
4. **Extensibility.** Third-party mods integrate through a small, versioned public API (`dev.worldconfigmanager.api`).

## Modules

```
worldconfigmanager/
├── wcm-core/      plain Java 25 library: API + domain + services + storage + protocol (unit tested, no Minecraft)
└── wcm-fabric/    Fabric mod for Minecraft 26.3: platform glue, vanilla providers, commands, networking, GUI
    ├── src/main      common/server side code
    ├── src/client    client-only code (GUI, client session, client options provider)
    └── src/gametest  in-game tests (server game tests + client game tests)
```

`wcm-core` is nested into the mod JAR (Fabric jar-in-jar). It compiles against Gson and SLF4J, which Minecraft ships
at runtime.

### Package map (`wcm-core`)

| Package | Responsibility |
|---|---|
| `dev.worldconfigmanager.api` | **Public API** (versioned, `WcmApi.API_VERSION` = 1.0.0): `ConfigProvider`, `ConfigDefinition`, `ConfigValue`, `ConfigScope`, `ConfigValidator`, `ConfigSnapshot`, `ConfigChange`, `ConfigApplyResult`, `ConfigMetadata`, `ConfigEntry`, `ProviderContext`, `WcmPlugin` |
| `core.json` | Strict JSON parsing with size/depth limits, typed value codec |
| `core.storage` | Atomic file writes, safe path resolution, quarantine of unreadable files |
| `core.registry` | Provider registry with per-provider definition cache |
| `core.config` | `ConfigService`: live view of all settings available in a context |
| `core.profile` | Profile model, validation, codec with schema migration, repository, CRUD service |
| `core.world` | Persistent world identity (`world.json`) and associations |
| `core.backup` | Backup model, repository, creation and retention |
| `core.history` | Append-only JSON-lines history with filters and retention |
| `core.apply` | **Transaction engine**: plan → validate → backup → apply → rollback → history |
| `core.compare` | Key/typed-value comparison of profiles, backups and the live state |
| `core.compat` | Compatibility checks and schema migration framework |
| `core.transfer` | Hardened `.wcmp` package reader/writer, import preview and commit |
| `core.permission` | Roles, actions and the configurable policy |
| `core.settings` | Global WCM settings (retention, permissions, rate limits) |
| `core.protocol` | Wire protocol: negotiation, rate limiting, server-side request dispatcher, JSON encoders |
| `core` | `WorldSession` (composition root per world), `DashboardSnapshot`, `OperationException`, versions |

### Package map (`wcm-fabric`)

| Package | Responsibility |
|---|---|
| `fabric` | `WcmFabricMod` entry point, `WcmServerRuntime` (per running server), provider context, permission mapping, starter profiles, server-side translation fallback |
| `fabric.provider` | Built-in providers: `GameRulesProvider` (`minecraft:gamerules`), `WorldSettingsProvider` (`minecraft:world`, difficulty) |
| `fabric.example` | Example third-party integration (`worldconfigmanager:join_message`) registered through the public plugin entrypoint |
| `fabric.net` | The two custom payloads carrying protocol JSON |
| `fabric.command` | `/wcm` command tree |
| `fabric.client` | Client entry point, `ClientSession` (handshake/state), `ClientOptionsProvider` (`minecraft:client_options`), client settings, local files |
| `fabric.client.backend` | `Backend` abstraction: `ServerBackend` (network) and `LocalBackend` (local client profiles) |
| `fabric.client.screen` | `WcmScreen`, pages and dialogs |
| `fabric.client.ui` | Theme, scroll list, drawing/format helpers |

## Key decisions

### One protocol for everything

The GUI never touches storage. It talks to a `Backend`. The server backend sends JSON requests over a custom payload;
the server's `ServerProtocolHandler` checks size → parses strictly → requires the handshake → rate-limits → checks the
permission policy with the **server-computed** role → calls services. The *same* handler class also powers local client
profiles (`LocalBackend`), running in-process with `CLIENT` scope. This means the GUI is backend-agnostic and the
permission/validation path is unit tested (`ProtocolTest`) without Minecraft.

Wire format:

```json
{"id": 7, "type": "profiles.apply", "body": {"id": "…uuid…"}}
{"id": 7, "type": "profiles.apply", "ok": true, "body": { … }}
{"id": 7, "type": "profiles.apply", "ok": false, "error": {"key": "wcm.error.permission_denied", "args": ["OPERATOR"]}}
```

Server pushes use `id: 0` (`event.open`, `event.changed`). Limits: client→server 1.5 MiB (a base64 encoded maximum-size
package), server→client 4 MiB; Fabric's large-payload splitting is used. Default rate limit: 5 requests/s, burst 30;
imports cost 10 tokens, exports 3.

### Handshake and version negotiation

The client sends `hello` with `[minProtocol, protocol]`. The server answers with the highest common version, the world
identity, available providers, supported scopes, the player's role and allowed actions (a UI hint only) and a
`compatible` flag. If the ranges do not overlap, the session is marked incompatible: every further request is refused
with `wcm.error.protocol_incompatible` and the client shows a read-only state. Servers without WCM are detected because
the client cannot send the WCM channel (or the hello times out); then only local client profiles are offered and
nothing is sent to the server.

### Scopes

* `WORLD` – stored with the world (game rules, difficulty, the example integration). Edited through the server.
* `SERVER` – controlled by the server independent of one world. The architecture supports it (contexts include it);
  no built-in provider uses it yet.
* `CLIENT` – local player settings. Managed only by `LocalBackend` on the client, per world/server key. Never sent.

A `ProviderContext` exposes the set of scopes it manages and platform services (the `MinecraftServer` on the server,
`Minecraft` on the client). The registry filters definitions by scope.

### World identity

A random UUID is generated the first time WCM sees a world and stored in `<world>/worldconfigmanager/world.json`. The
display name is never used as a key. WCM servers send this id in the handshake; clients key their local profiles by it.
For servers without WCM, the local key is a name-based UUID of the server address.

### Transactions, backups and rollback

`ApplyEngine.execute()` re-plans the request against the current state (so a stale preview cannot apply wrong data):

1. Classify each value: `CHANGE`, `UNCHANGED`, `INVALID`, `UNAVAILABLE`.
2. Any `INVALID` → refuse everything (`VALIDATION_FAILED`). No backup, no change.
3. No changes → `NO_CHANGES`.
4. Snapshot every affected provider into a backup. If the backup cannot be written → `BACKUP_FAILED`, no change.
5. Apply provider by provider. On a failure or exception, restore every touched provider from the snapshot.
   Rollback failures are reported per provider (`ROLLBACK_INCOMPLETE`) and the backup id is returned so it can be
   restored manually.
6. Record a history entry for every attempted change (`SUCCESS`, `FAILED`, `ROLLED_BACK`), each referencing the
   backup, so history entries can be undone through the backup system.

Unavailable entries (provider missing, setting removed) are skipped but **kept in the profile**.

### Storage layout

```
config/worldconfigmanager/settings.json          global WCM settings (server side)
config/worldconfigmanager/profiles/<uuid>.json   profile library shared by all worlds of this game/server
config/worldconfigmanager/profiles/library.json  default profile
config/worldconfigmanager/starter-profiles.done  marker so deleted starter profiles stay deleted
config/worldconfigmanager/client.json            client UI preferences
config/worldconfigmanager/client/...             local client profiles (same layout, CLIENT scope)
config/worldconfigmanager/exports|imports/       .wcmp packages on the client
<world>/worldconfigmanager/world.json            world identity, association, last applied profile
<world>/worldconfigmanager/backups/<uuid>.json   backups
<world>/worldconfigmanager/history.jsonl         change history (JSON lines)
<world>/worldconfigmanager/example_join_message.json   data of the example integration
```

All writes go through `AtomicFiles`: write to a temp file in the same directory, `fsync`, atomic move. A failed write
throws, so services never report success for an unsaved change and in-memory caches only change after a successful
write. Unreadable files are moved aside as `*.corrupt-<timestamp>` and reported, never deleted. History is append-only
with `SYNC`; a torn last line after a crash is skipped on load.

### Schema versions and migration

Every persisted document has a `schemaVersion`. `SchemaMigrator` upgrades step by step (profile schema v1 → v2 is
implemented and tested); documents newer than supported are refused with `SchemaTooNewException`, never downgraded.

### Compatibility

`CompatibilityChecker` reports: missing provider/mod, missing setting, changed type, invalid value, provider major
version change, profile saved with a newer provider, restart required, different Minecraft version, profile saved with
a newer WCM. Comparison (`ProfileComparator`) classifies rows as identical, changed, only-left/right, unavailable,
incompatible or version conflict, always by key and typed value.

### `.wcmp` packages

ZIP archive with `manifest.json` (format, versions, kind, profile ids, SHA-256 checksums) and `profiles/<uuid>.json`.
The reader enforces: 1 MiB package size, 64 entries, 1 MiB per entry and 8 MiB total uncompressed (bounded streaming
reads, so zip bombs cannot expand), an allow-list of entry names (no traversal, absolute paths, backslashes, colons or
control characters), no duplicates, manifest/content agreement, checksums, strict JSON and full profile validation.
Package content is data only and never executed. Exports are produced by the server and saved by the client into its own
exports folder; a remote player can never make the server write a file at a chosen path.

### Permissions

Roles: `PLAYER < OPERATOR < ADMIN < OWNER`, derived on the server from vanilla permission levels (gamemaster, admin,
owner). Each `PermissionAction` has a configurable minimum role in `settings.json`; `MANAGE_WCM_SETTINGS` is pinned to
`OWNER`. Commands and network requests use the same policy. Defaults: players may view the dashboard; operators may
view/edit settings, manage/apply profiles, view history/backups, create backups and export; admins may restore/delete
backups and import.

### Threading and performance

* All world/server operations run on the server thread (network handlers hop onto it if needed).
* Client requests complete on the render thread.
* No directory scanning in normal operation: profiles and backup summaries are loaded once per session; definitions
  are cached per provider and invalidated when the server stops.
* History pruning rewrites the file only when the size exceeds the limit by 100 entries.
* The GUI list draws only visible rows.

### Static state

Fabric's lifecycle requires static access points: the provider registry and the current server runtime
(`WcmFabricMod`), and the client connection state (`ClientSession`). Everything else is constructed with explicit
dependencies (see `WorldSession`).

## Known architectural limitations

* The client GUI manages one backend at a time (server **or** local), switchable from the sidebar.
* No built-in provider uses the `SERVER` scope yet.
* Third-party configuration files are never edited generically; mods without a WCM integration are listed as
  "metadata only".
