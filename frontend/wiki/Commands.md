# ⌨️ Commands

> **Master Every FPP Command**  
> **Complete Reference with Examples and Usage Patterns**

---

## 🎯 Command Overview

All FPP commands use the root command `/fpp` with aliases `/fakeplayer` and `/fp`.  
**Tab completion** is available for all commands and parameters.

### 📋 **Command List**

| Command | Permission | Description |
|---------|-----------|-------------|
| [`/fpp help`](#-fpp-help) | `fpp.help` | Interactive help menu with pagination |
| [`/fpp spawn`](#-fpp-spawn) | `fpp.user.spawn` / `fpp.admin.spawn` | Spawn fake player(s) |
| [`/fpp despawn`](#-fpp-despawn) | `fpp.user.delete` / `fpp.admin.delete` | Remove bot(s) |
| [`/fpp list`](#-fpp-list) | `fpp.user.list` | List all active bots |
| [`/fpp info`](#-fpp-info) | `fpp.user.info` | Bot details and statistics |
| [`/fpp chat`](#-fpp-chat) | `fpp.admin.chat` | Toggle fake chat system |
| [`/fpp swap`](#-fpp-swap) | `fpp.admin.swap` | Toggle bot swap system |
| [`/fpp freeze`](#-fpp-freeze) | `fpp.admin.freeze` | Freeze/unfreeze bot movement |
| [`/fpp tp`](#-fpp-tp) | `fpp.admin.tp` | Teleport to bot location |
| [`/fpp tph`](#-fpp-tph) | `fpp.user.tph` | Teleport bot(s) here |
| [`/fpp rank`](#-fpp-rank) | `fpp.admin.rank` | Set bot LuckPerms group |
| [`/fpp reload`](#-fpp-reload) | `fpp.admin.reload` | Hot-reload configurations |
| [`/fpp migrate`](#-fpp-migrate) | `fpp.admin.migrate` | Data migration and backups |
| [`/fpp stats`](#-fpp-stats) | `fpp.admin.stats` | Server statistics |
| [`/fpp lpinfo`](#-fpp-lpinfo) | `fpp.admin.lpinfo` | LuckPerms integration info |

---

## 📖 **Detailed Command Reference**

### 🆘 `/fpp help`

```bash
/fpp help [page]
```

**Description:** Interactive paginated help menu showing commands you have permission to use.

**Parameters:**
- `page` (optional) — Page number to display

**Examples:**
```bash
/fpp help          # Show first page
/fpp help 2        # Show page 2
```

**Features:**
- **Dynamic filtering** — Only shows accessible commands
- **Click navigation** — Previous/Next buttons
- **Permission-aware** — Adapts to your access level
- **Color-coded** — Different colors for user vs admin commands

---

### 🎭 `/fpp spawn`

```bash
/fpp spawn [amount] [--name <botname>] [--skin <skinname>] [--group <groupname>]
```

**Description:** Spawn one or more fake players at your current location.

**Parameters:**
- `amount` (optional, 1-100) — Number of bots to spawn (default: 1)
- `--name <botname>` — Specific name for the bot
- `--skin <skinname>` — Specific skin to use
- `--group <groupname>` — LuckPerms group for the bot

**Permission Requirements:**
- `fpp.user.spawn` — Spawn personal bots (limited by `fpp.bot.<num>`)
- `fpp.admin.spawn` — Spawn unlimited admin bots

**Examples:**
```bash
/fpp spawn                           # Spawn 1 bot with random name
/fpp spawn 5                         # Spawn 5 bots with random names
/fpp spawn --name Steve              # Spawn bot named "Steve"
/fpp spawn 3 --skin Notch           # Spawn 3 bots with Notch's skin
/fpp spawn --name Admin --group staff # Spawn bot with staff group
```

**Bot Limits:**
- **User bots:** Limited by `fpp.bot.<number>` permission (e.g., `fpp.bot.10` = max 10 bots)
- **Admin bots:** No limit with `fpp.admin.spawn`
- **Global limit:** `global-bot-limit` in config (applies to all bots)

**Cooldown:**
- Configurable via `spawn-cooldown` (default: disabled)
- Bypass with `fpp.bypass.cooldown` permission

---

### 🗑️ `/fpp despawn`

```bash
/fpp despawn <bot-name|all|@mine|@admin>
```

**Description:** Remove fake players from the server.

**Parameters:**
- `<bot-name>` — Specific bot name to remove
- `all` — Remove all bots (requires admin permission)
- `@mine` — Remove only your personal bots
- `@admin` — Remove all admin bots (requires admin permission)

**Permission Requirements:**
- `fpp.user.delete` — Remove your own bots
- `fpp.admin.delete` — Remove any bot

**Examples:**
```bash
/fpp despawn Steve        # Remove bot named "Steve"
/fpp despawn all          # Remove all bots (admin only)
/fpp despawn @mine        # Remove only your bots
/fpp despawn @admin       # Remove admin bots (admin only)
```

**Aliases:** `/fpp delete`, `/fpp remove`

---

### 📋 `/fpp list`

```bash
/fpp list [--owner <player>] [--group <groupname>] [--frozen]
```

**Description:** Display all active bots with detailed information.

**Parameters:**
- `--owner <player>` — Show bots owned by specific player
- `--group <groupname>` — Filter by LuckPerms group
- `--frozen` — Show only frozen bots

**Examples:**
```bash
/fpp list                     # Show all bots
/fpp list --owner Notch       # Show Notch's bots
/fpp list --group staff       # Show bots in staff group
/fpp list --frozen            # Show frozen bots only
```

**Display Format:**
```
📊 Active Bots (5/50):
🎭 Steve      │ Owner: Notch    │ World: world    │ Status: Active
🎭 Alex       │ Owner: @admin   │ World: nether   │ Status: Frozen
🎭 Herobrine  │ Owner: Bill     │ World: world    │ Status: Swapped
```

---

### ℹ️ `/fpp info`

```bash
/fpp info <bot-name>
```

**Description:** Detailed information about a specific bot.

**Parameters:**
- `<bot-name>` — Name of the bot to inspect

**Examples:**
```bash
/fpp info Steve           # Show Steve's details
```

**Information Displayed:**
- **Basic Info:** Name, owner, spawn time
- **Location:** World, coordinates, dimension
- **Appearance:** Skin source, display name, LuckPerms group
- **Status:** Frozen, bodyless, swapped state
- **Statistics:** Session duration, messages sent (if fake chat enabled)

---

### 💬 `/fpp chat`

```bash
/fpp chat [on|off|toggle|status]
```

**Description:** Control the fake chat system globally.

**Parameters:**
- `on` — Enable fake chat
- `off` — Disable fake chat
- `toggle` — Switch current state
- `status` — Show current status
- (no args) — Same as `toggle`

**Permission Required:** `fpp.admin.chat`

**Examples:**
```bash
/fpp chat on              # Enable fake chat
/fpp chat off             # Disable fake chat
/fpp chat toggle          # Switch state
/fpp chat status          # Check status
```

---

### 🔄 `/fpp swap`

```bash
/fpp swap [on|off|toggle|status] [--player <playername>]
```

**Description:** Control the bot swap system.

**Parameters:**
- `on` — Enable swap system
- `off` — Disable swap system  
- `toggle` — Switch current state
- `status` — Show current status
- `--player <name>` — Force swap specific player

**Permission Required:** `fpp.admin.swap`

**Examples:**
```bash
/fpp swap on              # Enable swap system
/fpp swap off             # Disable swap system
/fpp swap --player Notch  # Force swap Notch immediately
```

---

### ❄️ `/fpp freeze`

```bash
/fpp freeze <bot-name|all|@mine> [on|off|toggle]
```

**Description:** Freeze or unfreeze bot movement and AI.

**Parameters:**
- `<bot-name>` — Specific bot to freeze
- `all` — Freeze all bots (admin only)
- `@mine` — Freeze your bots only
- Action: `on`, `off`, `toggle` (default: toggle)

**Permission Required:** `fpp.admin.freeze`

**Examples:**
```bash
/fpp freeze Steve         # Toggle Steve's frozen state
/fpp freeze all on        # Freeze all bots
/fpp freeze @mine off     # Unfreeze your bots
```

---

### 📍 `/fpp tp`

```bash
/fpp tp <bot-name>
```

**Description:** Teleport yourself to a bot's location.

**Parameters:**
- `<bot-name>` — Name of bot to teleport to

**Permission Required:** `fpp.admin.tp`

**Examples:**
```bash
/fpp tp Steve             # Teleport to Steve's location
```

---

### 🏠 `/fpp tph` 

```bash
/fpp tph <bot-name|all|@mine>
```

**Description:** Teleport bot(s) to your current location.

**Parameters:**
- `<bot-name>` — Specific bot to teleport
- `all` — Teleport all bots (admin only)
- `@mine` — Teleport your bots only

**Permission Requirements:**
- `fpp.user.tph` — Teleport your own bots
- `fpp.admin.tph` — Teleport any bot

**Examples:**
```bash
/fpp tph Steve            # Bring Steve to you
/fpp tph @mine            # Bring all your bots
/fpp tph all              # Bring all bots (admin)
```

---

### 👑 `/fpp rank`

```bash
/fpp rank <bot-name> <group-name|clear>
/fpp rank random <group-name> [num]
/fpp rank list
```

**Description:** Set LuckPerms groups for one bot, or assign a group to a random selection of active bots.

**Parameters:**
- `<bot-name>` — Bot to modify
- `<group-name>` — LuckPerms group name
- `clear` — Reset one bot back to the default/global group
- `num` — Optional number of random bots to affect (defaults to `1`)

**Permission Required:** `fpp.admin.rank`

**Examples:**
```bash
/fpp rank Steve admin     # Make Steve use admin group
/fpp rank Alex staff      # Make Alex use staff group  
/fpp rank Steve clear     # Reset Steve back to default/global group
/fpp rank random vip      # Give 1 random active bot the vip group
/fpp rank random admin 5  # Give 5 random active bots the admin group
/fpp rank list            # Show current bot LP groups
```

---

### ⚙️ `/fpp reload`

```bash
/fpp reload [config|lang|names|messages|skins|all]
```

**Description:** Hot-reload configuration files without restart.

**Parameters:**
- `config` — Reload main config.yml
- `lang` — Reload language files
- `names` — Reload bot-names.yml
- `messages` — Reload bot-messages.yml
- `skins` — Refresh skin repository
- `all` — Reload everything (default)

**Permission Required:** `fpp.admin.reload`

**Examples:**
```bash
/fpp reload               # Reload everything
/fpp reload config        # Reload just config.yml
/fpp reload lang          # Reload language files
```

**What Gets Reloaded:**
- ✅ Configuration values
- ✅ Language messages  
- ✅ Bot name pools
- ✅ Bot message pools
- ✅ Skin repository
- ✅ LuckPerms integration
- ❌ Database connections (requires restart)

---

### 🔧 `/fpp migrate`

```bash
/fpp migrate <subcommand> [args...]
```

**Description:** Data migration and backup utilities.

**Subcommands:**

#### `/fpp migrate status`
Show configuration and database status.

#### `/fpp migrate backup`
Create manual backup of all plugin data.

#### `/fpp migrate config`
Re-run configuration migration chain.

#### `/fpp migrate lang|names|messages`
Force-sync YAML files from JAR.

#### `/fpp migrate db export [file]`
Export database to CSV format.

#### `/fpp migrate db merge <file>`
Merge external database file.

#### `/fpp migrate db tomysql`
Migrate SQLite data to MySQL.

**Permission Required:** `fpp.admin.migrate`

**Examples:**
```bash
/fpp migrate status       # Check migration status
/fpp migrate backup       # Create manual backup
/fpp migrate db export    # Export to CSV
```

---

### 📊 `/fpp stats`

```bash
/fpp stats [--detailed]
```

**Description:** Display server statistics and performance metrics.

**Parameters:**
- `--detailed` — Show extended statistics

**Permission Required:** `fpp.admin.stats`

**Examples:**
```bash
/fpp stats                # Basic statistics
/fpp stats --detailed     # Extended statistics
```

**Statistics Shown:**
- Bot count and distribution
- Performance metrics
- Database statistics
- Memory usage
- Configuration status

---

### 🔗 `/fpp lpinfo`

```bash
/fpp lpinfo [bot-name]
```

**Description:** LuckPerms integration diagnostic information.

**Parameters:**
- `[bot-name]` — Check specific bot's LP data (optional)

**Permission Required:** `fpp.admin.lpinfo`

**Examples:**
```bash
/fpp lpinfo               # General LP integration info
/fpp lpinfo Steve         # Steve's LP group data
```

**Information Displayed:**
- LuckPerms integration status
- Bot group configuration
- Weight and ordering settings
- Prefix/suffix data
- Tab list integration

---

## 🎮 **Usage Patterns**

### 🚀 **Quick Start Commands**
```bash
/fpp spawn 5              # Spawn 5 random bots
/fpp list                 # Check what was created
/fpp chat on              # Enable chat messages
/fpp despawn all          # Clean up when done
```

### 👤 **Personal Bot Management**
```bash
/fpp spawn --name MyBot   # Create personal bot
/fpp tph MyBot            # Bring bot to you
/fpp freeze MyBot         # Stop bot movement
/fpp despawn @mine        # Remove all your bots
```

### 👑 **Admin Server Management**
```bash
/fpp spawn 20             # Populate server
/fpp swap on              # Auto-replace offline players
/fpp chat on              # Enable realistic chat
/fpp stats --detailed     # Monitor performance
```

### 🎨 **Advanced Configuration**
```bash
/fpp spawn --name VIP --group donor    # Custom group bot
/fpp rank Steve admin                  # Change bot's rank
/fpp reload config                     # Apply config changes
/fpp migrate backup                    # Backup before changes
```

---

## 🎯 **Pro Tips**

### 💡 **Performance Optimization**
- **Start small:** Begin with 5-10 bots, increase gradually
- **Monitor TPS:** Use `/fpp stats` to check server impact
- **Use swap system:** For persistent population without constant spawning
- **Strategic placement:** Spread bots across different worlds/areas

### 🔧 **Best Practices**
- **Regular backups:** Use `/fpp migrate backup` before major changes
- **Permission testing:** Test bot limits with regular users
- **Skin optimization:** Use `custom` mode for better performance
- **Database maintenance:** Monitor database size with large bot counts

### 🎮 **Creative Uses**
- **Events:** Spawn themed bots for special occasions
- **Minigames:** Fill teams with appropriately named bots
- **Roleplay:** Create NPC-like bots with specific groups/prefixes
- **Testing:** Use bots to test plugin compatibility and performance

---

## 🔐 **Permission Reference**

### 👤 **User Permissions**
```yaml
fpp.user.*              # All user commands
├── fpp.user.spawn      # Spawn personal bots  
├── fpp.user.delete     # Delete own bots
├── fpp.user.list       # List all bots
├── fpp.user.info       # Bot information
└── fpp.user.tph        # Teleport bots here
```

### 👑 **Admin Permissions**
```yaml
fpp.admin.*             # All admin commands
├── fpp.admin.spawn     # Unlimited bot spawning
├── fpp.admin.delete    # Delete any bot
├── fpp.admin.chat      # Control fake chat
├── fpp.admin.swap      # Control swap system
├── fpp.admin.freeze    # Freeze any bot
├── fpp.admin.tp        # Teleport to bots
├── fpp.admin.rank      # Manage bot groups
├── fpp.admin.reload    # Hot-reload configs
├── fpp.admin.migrate   # Migration utilities
├── fpp.admin.stats     # Server statistics
└── fpp.admin.lpinfo    # LuckPerms diagnostics
```

### 🎯 **Special Permissions**
```yaml
fpp.bypass.*            # Bypass restrictions
├── fpp.bypass.cooldown # No spawn cooldown
└── fpp.bypass.limit    # Ignore global limits

fpp.bot.*               # Bot quantity limits
├── fpp.bot.1           # Max 1 personal bot
├── fpp.bot.5           # Max 5 personal bots
├── fpp.bot.10          # Max 10 personal bots
└── fpp.bot.50          # Max 50 personal bots
```

---

## 🆘 **Troubleshooting**

### ❌ **Common Issues**

**"You don't have permission to use this command"**
- Check you have the required permission node
- Verify your permission plugin is working
- Ask admin to grant appropriate permissions

**"You have reached your bot limit"**
- Check your `fpp.bot.<number>` permission
- Remove existing bots with `/fpp despawn @mine`
- Ask admin to increase your limit

**"Bot name already exists"**
- Choose a different name with `--name`
- Check existing bots with `/fpp list`
- Remove conflicting bot first

**"No bots found"**
- Verify bots exist with `/fpp list`
- Check you're targeting the right bot name
- Ensure bots haven't been removed by another player

### 🔧 **Debug Commands**
```bash
/fpp stats --detailed    # Performance diagnostics
/fpp lpinfo              # LuckPerms integration status
/fpp migrate status      # Configuration status
/fpp list --frozen       # Check frozen bots
```

---

**🎉 Master these commands and become an FPP expert!**

For more information, see [Configuration](Configuration.md) for detailed setup options and [Permissions](Permissions.md) for complete access control.
