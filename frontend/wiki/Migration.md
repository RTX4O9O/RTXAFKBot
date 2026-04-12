# 🔧 Migration & Backups

> **Current plugin line:** 1.6.2  
> **Bundled config stamp:** 53  
> **Current migration target:** 55

This page covers:
- automatic config migration
- backup behavior
- `/fpp migrate` tools
- DB export / merge / MySQL migration
- current config version history

---

## Overview

When FPP starts, it can migrate three kinds of state:

| What changed | How FPP handles it |
|--------------|--------------------|
| New config keys | Added automatically |
| Renamed/restructured config paths | Migrated through the version chain |
| Database schema changes | Applied incrementally in the DB layer |

The normal update flow is automatic — you only use `/fpp migrate` when you want manual control.

---

## Automatic Config Migration

FPP reads `config-version` from `config.yml` and upgrades older configs step-by-step.

Important rule:
- your existing values are preserved whenever possible
- missing keys are added from defaults
- renamed paths are moved forward

Before risky migration work, FPP creates a backup.

---

## Backup System

Backups are stored under:

```text
plugins/FakePlayerPlugin/backups/
```

Two backup styles exist:

### Full backups

Used for heavier migration actions.

Can include:
- `config.yml`
- `bot-names.yml`
- `bot-messages.yml`
- `language/`
- SQLite DB files
- other important plugin data

### Config-file backups

Used for lighter YAML sync/update actions.

### Retention

FPP keeps the most recent backup sets and prunes older ones automatically.

---

## `/fpp migrate` Command

Permission:

```text
fpp.migrate
```

Not the older `fpp.admin.migrate` wording.

### Subcommands

| Command | Description |
|---------|-------------|
| `/fpp migrate status` | Show config version, DB status, backup count, sync health |
| `/fpp migrate backup` | Create a manual backup now |
| `/fpp migrate backups` | List stored backups |
| `/fpp migrate config` | Re-run the config migration chain |
| `/fpp migrate lang` | Force-sync language keys from the bundled jar |
| `/fpp migrate names` | Force-sync bot names from the bundled jar |
| `/fpp migrate messages` | Force-sync bot messages from the bundled jar |
| `/fpp migrate db export [file]` | Export session data to **CSV** |
| `/fpp migrate db merge <file>` | Merge older DB data into the current DB |
| `/fpp migrate db tomysql` | Move/copy SQLite data into configured MySQL |

---

## Exporting Data

Current export format:

- **CSV**

Use:

```text
/fpp migrate db export
```

This is the current documented behavior — older JSON wording is outdated.

---

## Merging Old Data

If you have an older DB file or export, use:

```text
/fpp migrate db merge <file>
```

Typical use cases:
- moving hosts
- restoring archived session history
- re-importing an old SQLite file

FPP creates a backup first.

---

## SQLite → MySQL Migration

Example workflow:

1. Configure MySQL in `config.yml`
2. Enable:

```yaml
database:
  enabled: true
  mysql-enabled: true
```

3. Reload:

```text
/fpp reload
```

4. Run:

```text
/fpp migrate db tomysql
```

---

## YAML Sync Helpers

FPP can also sync bundled YAML defaults into existing files without overwriting user values.

Important files:
- `language/en.yml`
- `bot-names.yml`
- `bot-messages.yml`

Commands:

```text
/fpp migrate lang
/fpp migrate names
/fpp migrate messages
```

These add missing keys while preserving user-edited values.

---

## Config Version History

The current config history important to modern installs is:

| Version | What changed |
|---------|--------------|
| 46 | Added `performance.position-sync-distance` |
| 47 | Swap enhancements such as `swap.min-online`, retry behavior, and better logging alignment |
| 48 | Added `body.pick-up-items` |
| 49 | Added `body.pick-up-xp` |
| 50 | Added / expanded `pathfinding` section |
| 51 | XP/cmd-era config finalization used by the earlier 1.6.0 line |
| 52 | Added player-chat reaction era fake-chat improvements |
| 53 | Reworked chunk-loading radius semantics (`"auto"` vs `0`) in the bundled config line |
| 54 | Added `body.drop-items-on-despawn` |
| 55 | Added shared pathfinding tuning keys and latest migration target updates |

### Additional 1.6.x-era structural additions

These are also important in the current docs/config structure:
- `bot-interaction`
- `badword-filter`
- `ai-conversations`

Depending on when a user upgraded from, those sections may be added by migration even if their older config never had them.

---

## Bundled Config vs Migration Target

One confusing but normal detail:

- the shipped `config.yml` in resources can be stamped at `53`
- the runtime migrator can target `55`

That does **not** mean the install is broken.

It just means:
- the bundled file reflects a stable packaged default
- runtime migration code can still add/reshape newer keys on first boot or reload

---

## Manual Rollback

If an update goes wrong:

1. stop the server
2. open `plugins/FakePlayerPlugin/backups/`
3. restore the desired backup copy of your files
4. restore the SQLite DB if needed
5. start the server again

Tip:

```text
/fpp migrate backup
```

Run this manually before any big production update.

---

## Troubleshooting

### My values changed after migration

Check:
- whether the key moved to a new path
- whether you are looking at the latest generated config copy
- whether the backup folder contains your previous state

### `db merge` says file not found

Make sure:
- the file exists where you expect it
- you passed the correct filename/path

### DB schema / runtime mismatch

Check:
- startup logs
- `/fpp migrate status`
- DB connectivity if using MySQL

---

## Related Pages

- [Configuration](Configuration.md)
- [Database](Database.md)
- [Proxy-Support](Proxy-Support.md)
- [Changelog](Changelog.md)
