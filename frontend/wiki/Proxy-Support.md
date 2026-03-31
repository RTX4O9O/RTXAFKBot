# Proxy Support (Velocity & BungeeCord)

FakePlayerPlugin fully supports **multi-server proxy networks** using **Velocity** or **BungeeCord**.

---

## 🌐 Features

| Feature | Description |
|---------|-------------|
| **Shared Database** | All servers write to the same MySQL database |
| **Per-Server Isolation** | Each server only spawns/manages its own bots |
| **Cross-Server Chat** | Bot messages broadcast to all connected servers |
| **Global Alerts** | `/fpp alert` sends admin messages network-wide |
| **Server Identity** | Each server has a unique ID for tracking |
| **Network Stats** | View combined bot counts and stats across all servers |

---

## 🚀 Quick Setup

### 1. Configure Each Server

On **every** backend server, edit `config.yml`:

```yaml
server:
  id: "survival"    # Unique per server: "survival", "skyblock", "creative", etc.

database:
  enabled: true
  mode: "NETWORK"   # Enable multi-server mode
  mysql-enabled: true
  mysql:
    host: "mysql.example.com"
    port: 3306
    database: "fpp_network"
    username: "fpp_user"
    password: "your_password"
```

### 2. Configure Proxy

#### Velocity

No configuration needed — Velocity auto-forwards plugin messages between servers.

#### BungeeCord

Add to `config.yml` on your BungeeCord proxy:

```yaml
servers:
  survival:
    motd: 'Survival Server'
    address: localhost:25565
    restricted: false
  skyblock:
    motd: 'Skyblock Server'
    address: localhost:25566
    restricted: false
```

### 3. Verify

1. Start all servers + proxy
2. Check console logs on each server:
   ```
   [FPP] Database mode: NETWORK | Server ID: survival
   ```
3. Run `/fpp info` on any server to see database stats

---

## 📊 How It Works

### Database Architecture

```
┌──────────────────────────────────────────┐
│        MySQL Database (Shared)           │
│  ┌────────────────────────────────────┐  │
│  │  fpp_bot_sessions                  │  │
│  │  - All bot spawn/remove events     │  │
│  │  - Tagged with server_id           │  │
│  │                                    │  │
│  │  fpp_active_bots                   │  │
│  │  - Currently spawned bots          │  │
│  │  - Used for crash recovery         │  │
│  └────────────────────────────────────┘  │
└──────────────────────────────────────────┘
         ▲           ▲           ▲
         │           │           │
    ┌────┴───┐  ┌────┴───┐  ┌────┴───┐
    │Survival│  │Skyblock│  │Creative│
    │  (ID:  │  │  (ID:  │  │  (ID:  │
    │survival)  │skyblock)  │creative)│
    └────────┘  └────────┘  └────────┘
```

### Plugin Messaging Flow

```
Server A                   Proxy                  Server B
   │                         │                        │
   │  1. Bot sends chat      │                        │
   ├────────────────────────►│                        │
   │  [CHAT] subchannel      │                        │
   │                         │  2. Forward to all     │
   │                         ├───────────────────────►│
   │                         │  3. Broadcast locally  │
   │                         │                        ├─► Players
   │  4. Echo back (skipped) │                        │
   │◄────────────────────────┤                        │
```

---

## 🛠️ Configuration Reference

### Server Identity

```yaml
server:
  id: "default"   # MUST be unique per server
```

**Examples:**
- `"survival"`, `"skyblock"`, `"creative"`
- `"hub"`, `"lobby-1"`, `"lobby-2"`
- `"dev"`, `"staging"`, `"production"`

**Rules:**
- ✅ Alphanumeric, dashes, underscores
- ✅ Case-sensitive (use lowercase)
- ❌ Spaces or special characters
- ❌ Same ID on multiple servers

### Database Modes

| Mode | Use Case | Behavior |
|------|----------|----------|
| **LOCAL** | Single server | No server_id filtering |
| **NETWORK** | Proxy network | Rows tagged with server.id |

**When to use NETWORK mode:**
- Running 2+ backend servers
- Using Velocity or BungeeCord
- Shared MySQL database
- Want cross-server features

**When to use LOCAL mode:**
- Single standalone server
- Testing/development
- No proxy

### MySQL vs SQLite

| Backend | Single Server | Proxy Network |
|---------|---------------|---------------|
| **SQLite** | ✅ Recommended | ❌ Not supported |
| **MySQL** | ✅ Supported | ✅ Required |

**Why MySQL for networks?**
- SQLite is file-based (one server only)
- MySQL supports concurrent connections
- Shared database across all servers

---

## 📡 Cross-Server Features

### 1. Fake Chat Sync

When a bot sends a chat message on one server, it broadcasts to **all** servers.

**How it works:**
1. Bot on Server A sends message
2. Plugin formats message with bot's prefix/name
3. Sends to proxy via `fpp:main` channel (subchannel: `CHAT`)
4. Proxy forwards to all connected servers
5. Each server broadcasts locally

**Loop prevention:**
- Messages have a `isRemoteBroadcast` flag
- Remote messages are NOT re-sent

**Enable:**
```yaml
fake-chat:
  enabled: true
```

### 2. Global Alerts

Admins can send alerts to **all servers** in the network.

**Command:**
```
/fpp alert Server restart in 5 minutes!
```

**Behavior:**
- Broadcasts on local server immediately
- Sends to proxy with unique message ID
- Proxy echoes to all servers (including sender)
- Duplicate detection prevents double-broadcast

**Permissions:**
- `fpp.all` (admin only)

**Language customization:**
```yaml
# language/en.yml
alert-received: "{prefix}<red><bold>ALERT</bold> <dark_gray>» <white>{message}"
alert-sent: "{prefix}<#66CC66>✓</#66CC66> <gray>Alert broadcasted to <#0079FF>all servers</#0079FF>."
```

### 3. Network Stats

View combined stats across all servers with `/fpp info`:

```yaml
━━━ DATABASE STATS (MySQL) ━━━
  MODE: NETWORK
  SERVER ID: survival
  TOTAL SESSIONS: 1,234
  UNIQUE BOTS: 89
  UNIQUE SPAWNERS: 42
  TOTAL UPTIME: 123h 45m
```

**Database mode indicators:**
- `LOCAL` — single-server filtering active
- `NETWORK` — global stats, no filtering

---

## 🔧 Plugin Messaging API

### Supported Channels

| Channel | Direction | Purpose |
|---------|-----------|---------|
| `fpp:main` | Bidirectional | Main plugin messaging channel |

### Subchannel: CHAT

**Format:**
```
[CHAT] [botName] [botDisplayName] [message] [prefix] [suffix]
```

**Example:**
```java
VelocityChannel vc = plugin.getVelocityChannel();
vc.sendPluginMessage("CHAT", "Notch", "§7[§bVIP§7] Notch", "Hello!", "§7[§bVIP§7] ", "");
```

### Subchannel: ALERT

**Format:**
```
[ALERT] [messageId] [message]
```

**Example:**
```java
vc.sendPluginMessage("ALERT", "1234567890-12345", "Server restart!");
```

### Subchannel: SYNC

**Format:**
```
[SYNC] [key] [value]
```

**Future use:** State synchronization, bot counts, etc.

---

## 🐛 Troubleshooting

### Problem: Bots don't sync across servers

**Check:**
1. All servers have `database.mode: "NETWORK"`
2. All servers connect to the **same** MySQL database
3. Each server has a **unique** `server.id`
4. MySQL credentials are correct

**Verify:**
```sql
SELECT server_id, COUNT(*) FROM fpp_active_bots GROUP BY server_id;
```

### Problem: Chat messages don't cross servers

**Check:**
1. `fake-chat.enabled: true` on all servers
2. Plugin messaging channel registered:
   ```
   [FPP] Plugin messaging channel registered: fpp:main
   ```
3. At least one player online (required for Bukkit plugin messaging)

**Debug:**
```yaml
debug: true  # Enable in config.yml
```

Look for:
```
[VelocityChannel] Sent 'CHAT' (123 bytes).
[VelocityChannel] Received subchannel='CHAT' via player=Steve.
```

### Problem: Duplicate alerts

This should **not** happen — the plugin uses unique message IDs.

If you see duplicates:
1. Check server clocks are synchronized (NTP)
2. Verify `server.id` is unique per server
3. Check proxy is not duplicating packets

### Problem: Database shows wrong server_id

**Symptom:**
- Bots from Server A appear in Server B's `/fpp list`

**Fix:**
1. Each server must have a **different** `server.id`
2. Run `/fpp reload` after changing `server.id`
3. Restart all servers

---

## 💡 Best Practices

### Production Deployment

1. **Use MySQL 8.0+** with connection pooling
2. **Set unique server IDs** before first boot
3. **Enable database backups** (MySQL dumps)
4. **Monitor connection health** via logs
5. **Use strong MySQL passwords**

### Server ID Naming

```yaml
# ✅ Good
server:
  id: "survival-1"

# ✅ Good
server:
  id: "hub"

# ❌ Bad (not unique)
server:
  id: "default"    # Same on all servers!

# ❌ Bad (spaces)
server:
  id: "sky block"
```

### MySQL Configuration

**Recommended `my.cnf` settings:**
```ini
[mysqld]
max_connections = 200
innodb_buffer_pool_size = 1G
character_set_server = utf8mb4
collation_server = utf8mb4_unicode_ci
```

**FPP connection pool:**
```yaml
database:
  mysql:
    pool-size: 5              # Connections per server
    connection-timeout: 30000 # 30 seconds
    use-ssl: false            # Enable if using remote MySQL
```

### Database Migrations

**SQLite → MySQL:**
```bash
# 1. Enable MySQL in config.yml
# 2. Reload plugin
/fpp reload

# 3. Migrate data
/fpp migrate db tomysql

# 4. Verify
/fpp info
```

**Between servers:**
```bash
# Export from Server A
/fpp migrate db export

# Import to Server B
/fpp migrate db merge server-a-export.csv
```

---

## 📚 Related Documentation

- [Database](Database.md) — Schema, queries, and stats
- [Configuration](Configuration.md) — All config options
- [Commands](Commands.md) — Full command reference
- [Migration](Migration.md) — Data migration tools

---

## 🎯 Quick Reference

| Task | Command / Config |
|------|------------------|
| Enable network mode | `database.mode: "NETWORK"` |
| Set server ID | `server.id: "survival"` |
| Enable MySQL | `database.mysql-enabled: true` |
| Send global alert | `/fpp alert <message>` |
| Check network status | `/fpp info` |
| View server ID | Console on startup |
| Migrate to MySQL | `/fpp migrate db tomysql` |

---

← [Database](Database.md) · [Home](Home.md) →

