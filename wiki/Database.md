# Database

FPP logs every bot session to a database for auditing, analytics, and the `/fpp info` command.

---

## Storage Backends

FPP supports two backends:

| Backend | When used | Location |
|---------|----------|----------|
| **SQLite** | Default — always available | `plugins/FakePlayerPlugin/data/fpp.db` |
| **MySQL** | When `database.mysql.enabled: true` and server is reachable | Remote MySQL server |

If MySQL is enabled but unreachable at startup, FPP automatically falls back to SQLite and logs a warning.

---

## SQLite (Default)

No configuration required. The database file is created automatically at:

```
plugins/FakePlayerPlugin/data/fpp.db
```

SQLite is ideal for single-server setups. It is zero-config and has no external dependencies.

---

## MySQL Setup

To use MySQL, edit `config.yml`:

```yaml
database:
  mysql:
    enabled: true
    host:     "localhost"
    port:     3306
    database: "fpp"
    username: "your_user"
    password: "your_password"
    use-ssl:  false
```

Then restart the server or run `/fpp reload`.

### Creating the MySQL Database

Run this on your MySQL server before starting FPP:

```sql
CREATE DATABASE fpp CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci;
CREATE USER 'fpp_user'@'%' IDENTIFIED BY 'your_password';
GRANT ALL PRIVILEGES ON fpp.* TO 'fpp_user'@'%';
FLUSH PRIVILEGES;
```

FPP will create all necessary tables automatically.

---

## Schema

### `bot_sessions` table

| Column | Type | Description |
|--------|------|-------------|
| `id` | INTEGER (PK) | Auto-increment row ID |
| `bot_name` | TEXT | Internal bot name (unique per session) |
| `display_name` | TEXT | Display name (may differ for user-tier bots) |
| `spawner_uuid` | TEXT | UUID of the player who spawned the bot |
| `spawner_name` | TEXT | Name of the spawner at spawn time |
| `world` | TEXT | World name where the bot was spawned |
| `spawn_x` | REAL | X coordinate at spawn |
| `spawn_y` | REAL | Y coordinate at spawn |
| `spawn_z` | REAL | Z coordinate at spawn |
| `last_x` | REAL | X coordinate at last save / despawn |
| `last_y` | REAL | Y coordinate at last save / despawn |
| `last_z` | REAL | Z coordinate at last save / despawn |
| `spawned_at` | TEXT | ISO-8601 timestamp of when the bot was spawned |
| `despawned_at` | TEXT | ISO-8601 timestamp of when the bot was removed (null if still active) |
| `removal_reason` | TEXT | Why the bot was removed: `COMMAND`, `DEATH`, `RESTART`, `SERVER_STOP`, `SWAP` |
| `active` | INTEGER | `1` if the bot is currently active, `0` if it has been removed |

---

## Querying Records — `/fpp info`

### Admin tier (`fpp.info`)

```
/fpp info                        → all live bots + DB summary
/fpp info <botname>              → full session history for the bot
/fpp info bot <name>             → same as above
/fpp info spawner <playername>   → all bots ever spawned by that player
```

Shown fields: spawn time, despawn time, uptime, world, coordinates, spawner, removal reason.

### User tier (`fpp.user.info`)

```
/fpp info                → your own active bots
/fpp info <botname>      → limited view (world, coords, uptime) for a bot you own
```

---

## Persistence & the Database

When `fake-player.persist-on-restart: true`, the plugin:

1. On **shutdown** — marks all active bot sessions as `removal_reason: SERVER_STOP` and records their last position.
2. On **startup** — queries the database for sessions with `active = 1` that were saved during the last shutdown and respawns those bots at their last-known position.

This ensures bots rejoin exactly where they were when the server stopped, not at their original spawn point.

---

## Backup

For SQLite, back up the database by copying the file while the server is stopped:

```
plugins/FakePlayerPlugin/data/fpp.db
```

For MySQL, use standard `mysqldump`:

```bash
mysqldump -u fpp_user -p fpp > fpp_backup.sql
```

