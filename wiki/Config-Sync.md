# Config Synchronization - Complete Guide

> 🎉 **FakePlayerPlugin is now Open Source** — [https://github.com/Pepe-tf/fake-player-plugin](https://github.com/Pepe-tf/fake-player-plugin)

## 🔄 Overview

**Config Sync** allows you to share configuration files across all servers in a proxy network. Instead of manually editing `config.yml` on every backend server, you can push changes from one server and pull them on all others.

---

## ✨ Features

| Feature | Description |
|---------|-------------|
| **Central Storage** | Configs stored in MySQL database |
| **Push/Pull** | Upload or download configs on demand |
| **Auto-Sync** | Optional automatic pulling on startup |
| **Server Overrides** | Certain settings never sync (server-specific) |
| **Conflict Detection** | Know when local files differ from network |
| **Version Tracking** | SHA-256 hash + timestamps for each config |

---

## 🚀 Quick Setup

### Prerequisites

- ✅ NETWORK mode enabled (`database.mode: "NETWORK"`)
- ✅ MySQL database configured
- ✅ All servers connected to same MySQL instance

### Enable Config Sync

Edit `config.yml` on **all** servers:

```yaml
config-sync:
  mode: "MANUAL"    # Or AUTO_PULL / AUTO_PUSH
```

**Modes:**
- `DISABLED` - No syncing (default for LOCAL mode)
- `MANUAL` - Only sync via commands
- `AUTO_PULL` - Auto-pull latest on startup/reload
- `AUTO_PUSH` - Auto-push changes (use with caution!)

---

## 📋 Syncable Files

These files can be synchronized:

| File | What syncs | What DOESN'T sync |
|------|------------|-------------------|
| **config.yml** | Most settings | `server.id`, `database.mysql.*`, `debug` |
| **bot-names.yml** | Everything | - |
| **bot-messages.yml** | Everything | - |
| **language/en.yml** | Everything | - |

---

## 🎯 Commands

### `/fpp sync push [file]`

**Upload config(s) to the network.**

**Examples:**
```
/fpp sync push                    # Push all files
/fpp sync push config.yml         # Push single file
/fpp sync push bot-names.yml      # Push bot names
```

**What happens:**
1. Reads file from local disk
2. For `config.yml`, strips server-specific keys
3. Computes SHA-256 hash
4. Uploads to MySQL `fpp_config_sync` table
5. Tags with your server ID and username

---

### `/fpp sync pull [file]`

**Download config(s) from the network.**

**Examples:**
```
/fpp sync pull                    # Pull all files
/fpp sync pull config.yml         # Pull single file
/fpp sync pull language/en.yml    # Pull language file
```

**What happens:**
1. Queries MySQL for latest version
2. Downloads content
3. Creates `.sync-backup` file (local backup)
4. For `config.yml`, merges with local server-specific keys
5. Writes new file to disk

⚠️ **Important:** Run `/fpp reload` after pulling to apply changes!

---

### `/fpp sync status [file]`

**Show sync status for config(s).**

**Examples:**
```
/fpp sync status                  # Status for all files
/fpp sync status config.yml       # Status for single file
```

**Output:**
```
━━━ SYNC STATUS - config.yml ━━━
  HASH: a3f2c8b1
  PUSHED BY: survival (Admin)
  PUSHED AT: 2026-03-26T18:30:00Z
  ✓ Local file matches network
```

Or if changes exist:
```
  ⚠ Local file has uncommitted changes
```

---

### `/fpp sync check [file]`

**Check if local files have uncommitted changes.**

**Examples:**
```
/fpp sync check                   # Check all files
/fpp sync check config.yml        # Check single file
```

**Output:**
```
⚠ 2 file(s) have local changes:
  ⚠ config.yml
  ⚠ bot-names.yml
```

---

## 🔧 Typical Workflows

### Scenario 1: Update Config Across Network

**Goal:** Change max bots from 100 to 200 on all servers.

**Steps:**
1. **Edit config.yml on Server A** (your main server):
   ```yaml
   limits:
     max-bots: 200
   ```

2. **Push to network:**
   ```
   /fpp sync push config.yml
   ```

3. **On Server B, C, D... pull the update:**
   ```
   /fpp sync pull config.yml
   /fpp reload
   ```

✅ **All servers now have max-bots: 200**

---

### Scenario 2: Share Bot Name Pool

**Goal:** Use the same bot names on all servers.

**Steps:**
1. **Edit bot-names.yml on Server A:**
   ```yaml
   names:
     - Alex
     - Steve
     - Notch
     - Jeb
   ```

2. **Push:**
   ```
   /fpp sync push bot-names.yml
   ```

3. **On other servers:**
   ```
   /fpp sync pull bot-names.yml
   /fpp reload
   ```

✅ **All servers now use the same bot name pool**

---

### Scenario 3: Auto-Pull on Startup

**Goal:** Automatically get latest configs when a server starts.

**Setup:**
```yaml
# config.yml on ALL servers
config-sync:
  mode: "AUTO_PULL"
```

**Behavior:**
- On every `/fpp reload` or server restart
- Plugin automatically pulls latest configs from network
- Applies them silently (2 seconds after startup)
- Logs to console: `[FPP] Auto-pulled latest configs from network.`

⚠️ **Warning:** This can overwrite local changes! Use `AUTO_PULL` only if one server is the "master."

---

### Scenario 4: Centralized Management

**Best Practice Setup:**

**Server A (Main/Hub):**
```yaml
config-sync:
  mode: "MANUAL"    # Admins push from here
```

**Servers B, C, D (Backend):**
```yaml
config-sync:
  mode: "AUTO_PULL"    # Auto-sync from Server A
```

**Workflow:**
1. Admins edit configs on Server A only
2. Run `/fpp sync push` on Server A
3. Backend servers auto-pull on next restart/reload

✅ **Single point of configuration + automatic distribution**

---

## 🛡️ Server-Specific Settings (Never Synced)

These config keys are **always** server-local:

| Key | Why? |
|-----|------|
| `server.id` | Each server must have unique ID |
| `database.mysql.host` | DB host may differ per server |
| `database.mysql.port` | Port may differ |
| `database.mysql.database` | DB name may differ |
| `database.mysql.username` | Credentials are server-specific |
| `database.mysql.password` | Security: never sync passwords! |
| `database.mysql.use-ssl` | SSL settings may differ |
| `database.mysql.pool-size` | Per-server resource tuning |
| `database.mysql.connection-timeout` | Per-server network config |
| `debug` | Debug mode is per-server |

**How it works:**

When pushing `config.yml`, these keys are **stripped** before upload.

When pulling `config.yml`, these keys are **preserved** from the local file.

**Example:**

**Server A pushes:**
```yaml
server:
  id: "survival"    # REMOVED before push
limits:
  max-bots: 200     # SYNCED
```

**Server B pulls:**
```yaml
server:
  id: "skyblock"    # KEPT from local file
limits:
  max-bots: 200     # UPDATED from network
```

---

## 🔍 Under the Hood

### Database Table

```sql
CREATE TABLE fpp_config_sync (
  id            BIGINT AUTO_INCREMENT PRIMARY KEY,
  config_file   VARCHAR(128) NOT NULL,      -- e.g., "config.yml"
  server_id     VARCHAR(64)  NOT NULL,      -- who pushed
  content_hash  VARCHAR(64)  NOT NULL,      -- SHA-256 hash
  content       LONGTEXT     NOT NULL,      -- YAML content
  pushed_at     BIGINT       NOT NULL,      -- timestamp
  pushed_by     VARCHAR(64)  NOT NULL,      -- player name
  INDEX idx_config_file (config_file),
  INDEX idx_server_id (server_id),
  INDEX idx_pushed_at (pushed_at)
);
```

### Push Process

```
1. Read local file
   ↓
2. Strip server-specific keys (if config.yml)
   ↓
3. Compute SHA-256 hash
   ↓
4. INSERT INTO fpp_config_sync
   ↓
5. Tagged with server_id + username + timestamp
```

### Pull Process

```
1. SELECT latest version (ORDER BY pushed_at DESC LIMIT 1)
   ↓
2. Check hash vs local (skip if same)
   ↓
3. Create .sync-backup file
   ↓
4. Merge server-specific keys (if config.yml)
   ↓
5. Write to disk
```

---

## 🐛 Troubleshooting

### ❌ "Config sync is only available in NETWORK mode"

**Cause:** Database not enabled or mode is LOCAL.

**Fix:**
```yaml
database:
  enabled: true
  mode: "NETWORK"
```

---

### ❌ "Failed to push config.yml"

**Check:**
- MySQL connection is working
- You have write permissions
- Config file exists and is valid YAML

**Debug:**
```yaml
debug: true    # in config.yml
```

Look for `[ConfigSync]` messages in console.

---

### ❌ "No network version found"

**Cause:** No one has pushed this file yet.

**Fix:**
```
/fpp sync push config.yml    # Someone needs to push first
```

---

### ❌ Local changes not showing

**Cause:** Hash comparison might be failing.

**Check:**
```
/fpp sync check config.yml
```

If it says "matches network" but you know you changed it, try:
```
/fpp sync push config.yml    # Force push
```

---

### ❌ Auto-pull not working

**Check:**
1. `config-sync.mode` is `AUTO_PULL` or `AUTO_PUSH`
2. Database is in NETWORK mode
3. At least one config file exists in network
4. Console shows: `[ConfigSync] Auto-pulled latest configs from network.`

---

## 💡 Best Practices

### 1. Designate a Master Server

Choose one server as the "source of truth" for configs.

**Master (Server A):**
```yaml
config-sync:
  mode: "MANUAL"
```

**Slaves (Servers B, C, D):**
```yaml
config-sync:
  mode: "AUTO_PULL"
```

Admins only edit on Server A, others auto-sync.

---

### 2. Test Changes Before Syncing

1. Edit config locally
2. Run `/fpp reload`
3. Verify it works
4. **Then** push to network

Don't push untested configs!

---

### 3. Use Version Control

For production networks, consider:
- Keep configs in Git
- Push from Git → Server A → Network
- Rollback capability if needed

---

### 4. Schedule Sync Windows

For large networks:
- Push changes during off-peak hours
- Notify other server admins before pushing
- Coordinate restarts after pulling

---

### 5. Backup Before Major Changes

```bash
# Before pushing major config changes
cd plugins/FakePlayerPlugin
tar -czf config-backup-$(date +%Y%m%d).tar.gz *.yml language/
```

---

## 📊 Comparison: Manual vs Sync

| Task | Without Sync | With Sync |
|------|-------------|-----------|
| **Update max-bots** | Edit on 5 servers, restart 5x | Edit once, push, pull on others |
| **Add bot names** | Copy-paste to 5 servers | Push from one, auto-pull on others |
| **Fix typo in message** | Edit 5 files | Edit once, sync across network |
| **Disaster recovery** | Manually restore each server | Pull from network, instant restore |

---

## 🎯 Command Reference

| Command | Purpose | Permission |
|---------|---------|------------|
| `/fpp sync push [file]` | Upload to network | `fpp.all` |
| `/fpp sync pull [file]` | Download from network | `fpp.all` |
| `/fpp sync status [file]` | Show sync status | `fpp.all` |
| `/fpp sync check [file]` | Check for local changes | `fpp.all` |

---

## 🔒 Security Notes

### Passwords Never Sync

MySQL passwords in `database.mysql.password` are **never** uploaded to the network.

Each server keeps its own credentials.

### Access Control

Only admins with `fpp.all` permission can push/pull.

Regular users cannot access sync commands.

### Backup on Pull

Every pull creates a `.sync-backup` file before overwriting.

If something goes wrong:
```bash
cd plugins/FakePlayerPlugin
mv config.yml.sync-backup config.yml
/fpp reload
```

---

## 📚 Related Documentation

- [Proxy Support](Proxy-Support.md) - Network setup guide
- [Database](Database.md) - MySQL configuration
- [Configuration](Configuration.md) - All config options

---

← [Proxy Support](Proxy-Support.md) · [Database](Database.md) →

