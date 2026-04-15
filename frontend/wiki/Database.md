# 💾 Database

FPP stores bot sessions, restart state, analytics, and task persistence in a database.

> **Current plugin line:** v1.6.4  
> **Default backend:** SQLite  
> **Optional backend:** MySQL  
> **Important persistence feature:** active mine/use/place/patrol tasks survive restart

---

## Storage Backends

| Backend | When used | Location |
|---------|-----------|----------|
| SQLite | Default / single-server setups | `plugins/FakePlayerPlugin/data/fpp.db` |
| MySQL | When `database.mysql-enabled: true` | External MySQL server |

SQLite is bundled and requires no extra setup.

---

## SQLite (default)

No external dependency is required. FPP creates:

```text
plugins/FakePlayerPlugin/data/fpp.db
```

SQLite is ideal for:
- single-server setups
- testing / development
- small to medium bot counts

---

## MySQL Setup

Example config:

```yaml
database:
  enabled: true
  mysql-enabled: true
  mysql:
    host: "localhost"
    port: 3306
    database: "fpp"
    username: "fpp_user"
    password: "your_password"
    use-ssl: false
    pool-size: 5
    connection-timeout: 30000
```

Example bootstrap SQL:

```sql
CREATE DATABASE fpp CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci;
CREATE USER 'fpp_user'@'%' IDENTIFIED BY 'your_password';
GRANT ALL PRIVILEGES ON fpp.* TO 'fpp_user'@'%';
FLUSH PRIVILEGES;
```

FPP creates or migrates its tables automatically on startup.

---

## `NETWORK` Mode (Proxy / Multi-Server)

Set `database.mode: "NETWORK"` when multiple backend servers share one MySQL database.

```yaml
database:
  enabled: true
  mode: "NETWORK"
  server-id: "survival"
  mysql-enabled: true
  mysql:
    host: "mysql.example.com"
    database: "fpp_network"
    username: "fpp_user"
    password: "your_password"
```

### What `NETWORK` mode changes

- every row is tagged with `database.server-id`
- each backend only restores and manages **its own** bots
- remote bot data is cached for proxy-aware counts and `/fpp list`
- config sync can push/pull shared config files over the DB

### Important key name

Use:

```yaml
database:
  server-id: "survival"
```

Do **not** use the old `server.id` key.

---

## Main Tables

The plugin uses `fpp_*` table names.

### `fpp_bot_sessions`

Historical session log used by `/fpp info`, analytics, and migration/export tools.

Stores:
- bot name / display name
- spawner info
- world / positions
- spawn time / despawn time
- removal reason
- active flag
- server id in network mode

### `fpp_active_bots`

The primary restart-persistence source of truth when DB is enabled.

Stores the bot's last known:
- UUID
- name / display name
- world / coordinates / rotation
- LP group
- frozen/chat/item/XP settings and other persistent bot state (depending on schema level)
- per-bot overrides: head-AI, nav-parkour, nav-break-blocks, nav-place-blocks, swim-AI, chunk-load-radius (schema v14)

### `fpp_skin_cache`

Database-backed skin resolution cache (schema v15).

Caches resolved Mojang skin textures so repeated bot spawns don't hit the Mojang API every time.

- `skin_name` (primary key) — the Minecraft username used for lookup
- `texture_value` / `texture_signature` — base64-encoded skin texture data
- `source` — origin label (e.g. `mojang:PlayerName`)
- `cached_at` — timestamp; entries expire after 7 days and are auto-cleaned

### `fpp_sleeping_bots`

Crash-safe storage for the peak-hours sleeping queue.

Used so peak-hours sleeping state can be restored after a crash/restart.

### `fpp_bot_identities`

Stable `(bot_name, server_id) -> UUID` mapping.

Used so bots keep the same UUID on the same backend server across restarts.

### `fpp_bot_tasks`

Stores active task state for bots.

This is the key table for **task persistence**.

Task types:
- `MINE`
- `USE`
- `PLACE`
- `PATROL`

High-level fields:
- `bot_uuid`
- `server_id`
- `task_type`
- world name
- position / yaw / pitch
- `once_flag`
- `extra_str` (route name, etc.)
- `extra_bool` (extra mode flag)

### `fpp_meta`

Stores schema version metadata.

---

## Task Persistence (Important in v1.6.x)

Active bot jobs now survive restart.

This includes:
- mine jobs
- use-item jobs
- place jobs
- patrol / waypoint jobs

### Save flow

On shutdown, FPP snapshots active tasks into:
- `fpp_bot_tasks` when DB is enabled
- `data/bot-tasks.yml` as YAML fallback

### Restore flow

On startup, tasks are read back and resumed.

After loading, the task records are cleared from the DB snapshot source to prevent accidental double-restore on the next boot.

---

## Persistence Flow

When `persistence.enabled: true`:

### On shutdown
- active bot positions are captured
- sessions are finalized in the DB
- restart-state is written to `fpp_active_bots`
- task snapshot is written to `fpp_bot_tasks`

### On startup
- bots are restored from `fpp_active_bots` (DB primary)
- file fallback is used only when DB is disabled or unavailable
- task state is restored after bot recovery

---

## Session History Queries

### Admin tier (`fpp.info`)

```text
/fpp info
/fpp info bot <name>
/fpp info spawner <name>
```

### User tier (`fpp.info.user`)

```text
/fpp info
```

User-tier access is limited to the sender's own bots.

---

## Location Flush Interval

```yaml
database:
  location-flush-interval: 30
```

Bot positions are batch-flushed on this interval in **seconds**.

This reduces write overhead while still keeping session history reasonably current.

---

## `/fpp migrate` Database Tools

Permission: `fpp.migrate`

| Command | Description |
|---------|-------------|
| `/fpp migrate status` | Show config version, DB schema, backups, and sync state |
| `/fpp migrate backup` | Create a manual backup |
| `/fpp migrate backups` | List backup sets |
| `/fpp migrate db export [file]` | Export session data to **CSV** |
| `/fpp migrate db merge <file>` | Merge an older DB export/file |
| `/fpp migrate db tomysql` | Copy data from SQLite to configured MySQL |

---

## Backup Notes

### SQLite

Back up:

```text
plugins/FakePlayerPlugin/data/fpp.db
```

Or use:

```text
/fpp migrate backup
```

### MySQL

Use standard database backups, for example:

```sql
mysqldump -u fpp_user -p fpp > fpp_backup.sql
```

---

## Disabling the Database

```yaml
database:
  enabled: false
```

When disabled:
- no DB history is written
- persistence falls back to YAML files only
- `/fpp info` is limited to in-memory/YAML-backed data
- task persistence falls back to YAML snapshot files

---

## Quick Troubleshooting

### Bots from one backend appear on another

Check:
- all servers share the same MySQL DB intentionally
- each server has a **unique** `database.server-id`
- all backends are actually using `NETWORK` mode consistently

### MySQL is enabled but nothing loads

Check:
- `database.mysql-enabled: true`
- host/port/database/user/password are correct
- server can reach the DB host
- startup logs for DB initialization failures

### Export format confusion

Current export docs should be treated as:

- **CSV export**, not JSON

---

## Related Pages

- [Configuration](Configuration.md)
- [Proxy-Support](Proxy-Support.md)
- [Migration](Migration.md)
- [Commands](Commands.md)
