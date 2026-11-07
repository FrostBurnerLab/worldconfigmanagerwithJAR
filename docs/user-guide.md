# WorldConfig Manager — User Guide

*"Every Minecraft world deserves its own configuration."*

WorldConfig Manager (WCM) lets you keep different configuration profiles — for example *Survival*, *Hardcore*,
*Creative Builder* — and apply them to individual worlds safely, with previews, automatic backups, history and undo.

## Requirements

* Minecraft Java Edition **26.3**
* Fabric Loader **0.20.1** or newer
* Fabric API (built and tested with **0.165.2+26.3**)
* Java **25**

## Installation

1. Install Fabric Loader for Minecraft 26.3.
2. Put `fabric-api-…+26.3.jar` and `worldconfigmanager-0.1.0.jar` into your `mods` folder.
3. For multiplayer, install the same files on the server. Players without WCM can still join; they just cannot open the
   WCM screen. A client with WCM on a server without WCM gets **local client profiles** only.

## Opening WCM

* **Pause menu:** the small **WCM** button in the top-right corner (can be turned off in *Settings*).
* **Key binding:** *Open WorldConfig Manager* — unbound by default, set it under *Options → Controls*.
* **Command:** `/wcm open`

## What you see

The header shows the page, the world, your **role** on this server and two buttons:

* **Server / Client** switches between the server's world settings and your **local client settings** for this world
  (video and control options such as FOV, render distance, brightness). Client settings are stored on your computer and
  are never sent to the server.
* **Done** closes the screen. **Escape** closes dialogs first, then the screen.

### Dashboard

World name, persistent world identity, type and game mode; the profile last applied and the profile associated with the
world; the number of managed settings, providers, recorded changes and backups; warnings (compatibility problems,
unreadable files) and recent changes. All numbers are live data.

### World Profiles

* **New…** creates a profile. Leave *Start from the current configuration* on to capture all current values.
* **Import…** imports a `.wcmp` package (see below).
* **Apply…** first shows a **preview** of every change (current → new, invalid and unavailable entries, compatibility
  findings). Applying creates a **safety backup** first. If anything fails, all changes are rolled back.
* **Actions…**: rename, edit description/tags, duplicate, associate with this world, set as default, update from the
  current configuration, compare with the current configuration, export, remove a selected entry, delete.

*Associate* records which profile belongs to a world; it does not apply it. The *default* profile is associated with
worlds WCM sees for the first time — and applied automatically if *Apply default profile to new worlds* is enabled in
*Settings*.

Five starter profiles are created once: *Survival Default* (vanilla defaults), *Hardcore Challenge*, *Creative
Builder*, *Performance Optimized* and *Multiplayer Default*. Only settings that exist in your Minecraft version are
included. Deleted starters do not come back.

### Configurations

Browse every managed setting. Search by name, key, provider or description; filter by category, provider, or
*Modified* (non-default or unsaved). Select a setting to see its description, type and limits, default, scope and when
a change takes effect (⟳ = after reload/restart).

Editing **stages** changes (shown in yellow). Invalid input is explained immediately and never staged. *Reset to
default* stages the default value; *Revert* removes the staged change. **Preview** shows all staged changes;
**Apply** applies them in one transaction (with backup); **Discard** drops them.

On narrow screens the editor opens in place of the list; **← Back** or **Escape** returns to the list.

### Comparison

Pick two sources — the current configuration, any profile or any backup — and compare them side by side. Rows are
*identical*, *changed*, *only left/right*, *unavailable* (mod or setting not installed), *incompatible* (wrong type or
invalid value) or *version conflict*. *Only differences* hides identical rows.

### History & Backups

**History** lists every change with time, old → new value, operation, who did it and the result (*success*, *failed*,
*rolled back*). Filter by operation, category, provider and period. **Restore state before this change** restores the
backup that was taken right before it.

**Backups** are created automatically before every change, and manually with *Create backup*. You can *Restore* (with
preview; a safety backup of the current state is made first), *Compare with current*, *Export* or *Delete*. Unreadable
backups are listed in red and cannot be restored.

### Integrations

Lists every configuration provider (built-in and from other mods) with version, scopes, number of settings and whether
it applies changes transactionally. Installed mods without a WCM integration are listed as *unsupported (metadata
only)* — WCM never edits other mods' files on its own.

### Settings

* **Client preferences:** pause-menu button, confirmation before applying.
* **Server settings** (owner only): backups kept per world, backup age limit, history size and age limit, automatic
  application of the default profile to new worlds, and the **minimum role** for every action.

## Import and export (`.wcmp`)

* Exports are written to `config/worldconfigmanager/exports/` **on your computer**.
* To import, put packages in `config/worldconfigmanager/imports/` (or `exports/`), or **drag and drop** a `.wcmp` file
  onto the WCM window.
* **Merge** adds new settings to existing profiles and keeps existing values; **Replace** replaces profiles completely.
  Profiles you changed after the package was exported are protected unless you explicitly allow overwriting.
* The preview marks every entry as *new*, *same*, *conflict*, *missing mod*, *unsupported* or *incompatible*. All
  entries are kept, even ones your game cannot use yet.
* Invalid packages (not a WCM package, corrupt, unsafe paths, unexpected files, oversized, wrong checksums, unsupported
  versions) are rejected completely; nothing is imported.

## Commands

| Command | Default permission | Description |
|---|---|---|
| `/wcm` or `/wcm status` | everyone | World, profiles, counts, warnings |
| `/wcm open` | everyone (client needs WCM) | Opens the WCM screen |
| `/wcm profile list` | operator | List profiles |
| `/wcm profile create <name>` | operator | Create a profile from the current configuration |
| `/wcm profile preview <name>` | operator | Show what applying would change |
| `/wcm profile apply <name>` | operator | Apply a profile (with backup) |
| `/wcm profile associate <name>` | operator | Associate a profile with this world |
| `/wcm profile delete <name>` | operator | Delete a profile |
| `/wcm backup [create] [label]` | operator | Create a backup |
| `/wcm backup list` | operator | List backups |
| `/wcm backup restore <id>` | admin | Restore a backup (id prefix of at least 4 characters) |
| `/wcm history [count]` | operator | Show recent changes |

Commands use exactly the same permission rules as the screen.

## Roles and permissions

WCM derives your role on the server from vanilla permissions: **Owner** (level 4 / singleplayer host with cheats),
**Admin** (level 3), **Operator** (level 2), **Player**. In a singleplayer world *without cheats* you are a Player and
WCM is read-only — the same restriction as `/gamerule`.

## Where data is stored

* Profiles: `config/worldconfigmanager/profiles/` (shared by all worlds of this game or server)
* Per world: `<world>/worldconfigmanager/` (`world.json`, `backups/`, `history.jsonl`)
* Local client profiles: `config/worldconfigmanager/client/`

Unreadable files are moved aside as `*.corrupt-<time>` and reported on the dashboard; they are never deleted.

## Troubleshooting

* **"Read-only: incompatible"** — the server runs a WCM version with a different protocol. Update client or server.
  Your local client settings still work (Client button).
* **"Permission denied: this requires role …"** — ask the server owner; the required roles are listed in *Settings*.
* **Difficulty cannot be changed** — hardcore worlds are always Hard and a locked difficulty stays locked.
* **A change says ⟳** — it takes effect after a world reload or game restart.
