# Changelog

All notable changes to WorldConfig Manager are documented here. The format follows
[Keep a Changelog](https://keepachangelog.com/en/1.1.0/) and the project uses semantic versioning.

## [0.1.0] - 2026-11-07

First release, for Minecraft 26.3 (Fabric Loader 0.20.1, Fabric API 0.165.2+26.3, Java 25).

### Added
- Plain-Java core library (`wcm-core`) with the public integration API `dev.worldconfigmanager.api` 1.0.0.
- Profiles with UUID identity, metadata, tags, typed entries, provider versions; CRUD, duplicate, default profile,
  world association; five starter profiles.
- Transactional apply engine: validation, safety backup, per-provider application, automatic rollback, history.
- Backups (automatic and manual) with retention, restore, compare and export; corrupt backups are listed and refused.
- Bounded, filterable change history (JSON lines) with masking of sensitive values.
- Comparison of profiles, backups and the live configuration.
- `.wcmp` package format v1 with hardened reader, import preview, merge/replace, protection of newer local profiles.
- Compatibility checker and schema migration framework (profile schema v1 → v2).
- Permission roles and configurable per-action policy; server-side checks for every request and command.
- Network protocol v1: handshake/negotiation, read-only mode on mismatch, rate and size limits, change broadcasts.
- Built-in providers: vanilla game rules (runtime discovery), world difficulty, client options (local profiles).
- Example third-party integration (join announcement) using only the public API.
- Native Minecraft GUI with seven modules, dialogs, previews, keyboard navigation and layouts that fit down to a
  320×240 GUI.
- `/wcm` command tree, optional key binding, pause-menu button.
- English and Dutch translations.
- 65 unit tests, 4 server game tests and an end-to-end client game test.
