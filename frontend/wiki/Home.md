# 🎮 Fake Player Plugin - Wiki

> **The Ultimate Bot Spoofing Plugin for Paper 1.21+**  
> **Version:** 1.6.6.7 · **Platform:** Paper / Folia 1.21.x (up to 1.21.11) · **Author:** Bill_Hub · **License:** [MIT (Open Source)](https://github.com/Pepe-tf/fake-player-plugin)

---

> 🎉 **FakePlayerPlugin is now Open Source!** Browse the code, report issues, and contribute on [GitHub](https://github.com/Pepe-tf/fake-player-plugin).

---

## 🌟 Welcome to FPP

**Fake Player Plugin (FPP)** is the most advanced bot-spoofing plugin for Minecraft Paper servers. Create **realistic fake players** that seamlessly integrate with your server ecosystem - appearing in the tab list, server count, and as physical entities in the world.

### ✨ What Makes FPP Special?

- 🎭 **Indistinguishable from real players** - Complete tab list integration
- 🏃 **Physical presence** - NMS ServerPlayer entities with realistic hitboxes  
- 🎨 **Custom skins** - Use any Minecraft skin or upload your own
- 💬 **Fake chat** - Bots send messages with typing delays, burst replies, bot-to-bot conversations, and event reactions
- 🤖 **AI conversations** - Bots respond to `/msg` with AI-generated replies (7 providers, per-bot personalities)
- 🔄 **Dynamic swapping** - Replace offline players seamlessly with the swap system
- ⏰ **Peak hours scheduler** - Scale your bot pool by time-of-day windows
- 📦 **Bot inventory GUI** - Inspect and modify any bot's full inventory in-game
- ⚙️ **Per-bot settings GUI** - Shift+right-click any bot to open the 6-category settings chest
- 🧭 **A* pathfinding** - Navigate bots to players, waypoint routes, or mine/place destinations
- ⛏️ **Area mining** - Select a cuboid region and mine it continuously with `/fpp mine`
- 🏗️ **Block placing** - Bots place blocks at their look target with supply-container restocking
- 🔑 **Waypoint patrol** - Save named routes and send bots on looping patrols
- 🔤 **Rename bots** - Rename any active bot preserving all state (inventory, XP, LP group, tasks)
- 💻 **Stored commands** - Assign right-click commands to bots with `/fpp cmd`
- 🚫 **Badword filter** - Leet-speak normalization, auto-rename, remote word list
- 🔐 **Two-tier permission system** - `fpp.op` for admins, `fpp.use` for users
- 📊 **PlaceholderAPI** - 29+ placeholders for scoreboards, tab headers, and more
- 🎯 **LuckPerms integration** - Prefix/suffix, group assignment, weighted ordering
- 🏃 **Follow-target automation** - Bots continuously follow any online player with `/fpp follow`; persists across restarts
- ⚔️ **Per-bot PvE settings** - `pveEnabled`, `pveRange`, `pvePriority`, `pveMobTypes` per-bot via `BotSettingGui`
- 📦 **Extension API** - Drop `.jar` files into `plugins/FakePlayerPlugin/extensions/` to load third-party addons
- 🔤 **Random name generator** - `bot-name.mode: random` generates realistic Minecraft-style usernames on the fly
- 🔍 **Find command** - Bots scan nearby chunks for target blocks and mine them progressively
- 👥 **Bot groups** - Personal bot groups with GUI management for bulk commands
- 🧱 **WorldEdit integration** - `--wesel` flag for mine/place uses your WorldEdit selection
- 🤖 **Automation** - `auto-eat` and `auto-place-bed` defaults for realistic bot survival behaviour
- 🍃 **Folia support** - Compatible with Folia's regionised threading model

---

## 🚀 Quick Start

### 📋 Requirements
- **Server:** Paper 1.21+ (latest recommended)
- **Java:** JDK 21+ 
- **RAM:** 2GB+ recommended for optimal performance
- **Plugins:** PlaceholderAPI (optional), LuckPerms (optional)

### ⚙️ Installation
1. Download FPP from [Modrinth](https://modrinth.com/plugin/fake-player-plugin-(fpp)) or build from [source on GitHub](https://github.com/Pepe-tf/fake-player-plugin)
2. Drop the JAR into your `plugins/` folder
3. Restart your server
4. Configure permissions and settings
5. Start spawning bots with `/fpp spawn`!

---

## 📚 Documentation Overview

### 🏁 Getting Started
| Page | Description |
|------|-------------|
| [🚀 Getting Started](Getting-Started) | Complete setup guide and first steps |
| [❓ FAQ & Troubleshooting](FAQ) | Common questions and solutions |
| [📋 Changelog](Changelog) | Full version history and release notes |

### ⚙️ Core Features  
| Page | Description |
|------|-------------|
| [⌨️ Commands](Commands) | All commands with examples and usage |
| [🔐 Permissions](Permissions) | Complete permission system guide |
| [⚙️ Configuration](Configuration) | All config options explained |
| [🌍 Language](Language) | Customizing messages and translations |

### 🤖 Bot Systems
| Page | Description |
|------|-------------|
| [📝 Bot Names](Bot-Names) | Random name generation system |
| [💬 Bot Messages](Bot-Messages) | Chat messages and broadcasts |
| [🤖 Bot Behaviour](Bot-Behaviour) | Physics, AI, and interactions |
| [🎨 Skin System](Skin-System) | Skin management and customization |

### 🔧 Advanced Features
| Page | Description |
|------|-------------|
| [🔄 Swap System](Swap-System) | Replace offline players automatically |
| [💭 Fake Chat](Fake-Chat) | Bot chat system and formatting |
| [📊 Placeholders (PAPI)](Placeholders) | PlaceholderAPI integration |
| [💾 Database](Database) | Analytics and session tracking |
| [🌐 Proxy Support](Proxy-Support) | Velocity & BungeeCord multi-server networks |
| [🔄 Config Sync](./Config-Sync) | Synchronize configs across the proxy network |
| [🔧 Migration](Migration) | Updating and data migration |
| [📦 Extensions](Extensions) | Addon API for third-party developers |
| [🔍 Find Command](Find-Command) | Bot block-finding and progressive mining |
| [👥 Bot Groups](Bot-Groups) | Personal bot groups with GUI management |
| [🍃 Folia Support](Folia-Support) | Folia regionised threading compatibility |
| [😴 Sleep Command](Sleep-Command) | Night auto-sleep with temporary bed placement |
| [🛑 Stop Command](Stop-Command) | Cancel all active bot tasks instantly |

---

## 🎯 Key Features Breakdown

### 🎭 **Realistic Fake Players**
- **Tab List Integration** - Bots appear as real players
- **Server Count** - Increases displayed player count  
- **Join/Leave Messages** - Configurable welcome/goodbye messages
- **Chat Integration** - Bots can send messages and participate

### 🏃 **Physical Bodies (Mannequins)**  
- **Player-Shaped Entities** - Proper hitboxes and collision
- **Skin Support** - Display any Minecraft skin
- **Physics & AI** - Walking, head rotation, collision avoidance
- **Combat** - Take damage, die, respawn (all configurable)

### 🎨 **Skin System**
- **Auto Mode** - Fetch skins from Mojang automatically
- **Custom Skins** - Upload your own skin files  
- **Random Pool** - Rotate through multiple skins
- **Fallback System** - Always have a working skin

### 💬 **Chat & Messaging**
- **Fake Chat** - Bots send realistic messages
- **LuckPerms Support** - Prefix/suffix with gradient colors
- **Message Pools** - Random message selection
- **Broadcast System** - Scheduled announcements

### 🔄 **Swap System**  
- **Offline Replacement** - Replace AFK/offline players
- **Seamless Transition** - Maintain server population
- **Smart Detection** - Automatic player monitoring
- **Whitelist Support** - Protect VIP players

### ⚙️ **Configuration**
- **63 Config Versions** — Automatic migration system with backup before every change- **Hot Reload** - Change settings without restart via `/fpp reload`
- **Backup System** - Automatic timestamped backups before any migration
- **In-Game Settings GUI** - Toggle booleans and tune numbers without touching files

---

## 🆕 What's New in v1.6.6.2

### 🔖 **Version Bump**
- Plugin version updated to 1.6.6.2 for tracking purposes

See [📋 Changelog](Changelog) for full v1.6.6.2 release notes.

---

## 🆕 What's New in v1.6.6.1

### 🚀 **FPP BungeeCord Companion (`fpp-bungee.jar`)**
- New standalone **BungeeCord/Waterfall proxy plugin** — drop `fpp-bungee.jar` into your proxy `plugins/` folder, no config needed
- Inflates server-list player count to include FPP bots; merges bot names into the hover sample list
- Listens for `BOT_SPAWN`, `BOT_DESPAWN`, `SERVER_OFFLINE` messages from FPP backends; maintains a live bot registry
- Prints an anti-scam warning on every startup (FPP is free and open-source)
- Compatible with BungeeCord and Waterfall; source in `bungee-companion/`

### 🐛 **Bug Fixes**
- **Bot join/leave message color fix** — `BotBroadcast` now parses display names with full MiniMessage + legacy `&`/`§` color support; color tags no longer render as raw text
- **`Attribute.MAX_HEALTH` compatibility** — resolved `NoSuchFieldError` on Paper/Purpur 1.21.1 and older via new `AttributeCompat` utility

See [📋 Changelog](Changelog) for full v1.6.6.1 release notes.

---

## 🆕 What's New in v1.6.6

### 🎯 **Follow-Target Automation (`/fpp follow`)**
- New `/fpp follow <bot|all> <player> [--stop]` — bot continuously follows an online player with path recalculation
- FOLLOW task persisted in `fpp_bot_tasks` — bot resumes following after restart
- Permission: `fpp.follow`

### ⚔️ **Per-Bot PvE Settings (now fully live)**
- `BotSettingGui` PvP tab has live-editable per-bot PvE controls: `pveEnabled`, `pveRange`, `pvePriority`, `pveMobTypes`
- Settings persisted via DB schema v15→v16

### 🎨 **Skin Persistence Across Restarts**
- Resolved skins saved to `fpp_active_bots` (DB v16→v17); bots reload their skin on restart without a Mojang API call

### 🌐 **Server-List Config Keys**
- `server-list.count-bots` (default `true`) and `server-list.include-remote-bots` (default `false`)

### 🧭 **`pathfinding.max-fall`**
- A* pathfinder now has a configurable max safe fall distance (default `3` blocks)

### 💾 **DB Schema v15 → v16 → v17 · Config v60 → v63**

See [📋 Changelog](Changelog) for full v1.6.6 release notes and the complete version history.

---

## 🆕 What was New in v1.6.5.1

### ⚙️ **BotSettingGui Now Publicly Available**
- Per-bot settings GUI (shift+right-click any bot) is now available to **all users with `fpp.settings` permission** — no longer dev-only
- Grant `fpp.settings` via LuckPerms to allow non-op players to manage per-bot settings

See [📋 Changelog](Changelog) for full v1.6.5.1 release notes and the complete version history.

---

## 🆕 What was New in v1.6.5

### 📡 **Tab-List Ping Simulation**
- New `/fpp ping [<bot>] [--ping <ms>|--random] [--count <n>]` — set the visible tab-list latency for one or all bots
- 4 granular permissions: `fpp.ping` (view), `fpp.ping.set` (set), `fpp.ping.random` (random), `fpp.ping.bulk` (bulk)

### ⚔ **PvE Attack Automation**
- New `/fpp attack <bot> [--stop]` — bot walks to sender and continuously attacks nearby entities
- Respects 1.9+ attack cooldown and item-specific cooldowns dynamically
- Permission: `fpp.attack`

### 🔐 **Permission System Restructure**
- `fpp.admin` as preferred alias for `fpp.op`; `fpp.despawn` as preferred alias for `fpp.delete`
- Granular sub-nodes for chat, move, mine, place, use, rank, inventory, and ping commands
- New `fpp.command` (visibility), `fpp.plugininfo`, `fpp.spawn.multiple`, `fpp.notify`

### 🎨 **Skin Mode Rename**
- `skin.mode` values: `auto` → `player`, `custom` → `random`, `off` → `none` (legacy aliases still accepted)

### 🔧 **FlagParser & UpdateChecker**
- Reusable command flag parser with deprecation aliases, duplicate/conflict detection
- Beta build detection: `latestKnownVersion` and `isRunningBeta` fields

See [📋 Changelog](Changelog) for full v1.6.5 release notes and the complete version history.

---

## 🔐 Permission System

FPP uses a two-tier permission system:

```
fpp.op            # Admin wildcard — all commands (default: op)
├── fpp.spawn         fpp.delete       fpp.list
├── fpp.freeze        fpp.chat         fpp.swap
├── fpp.rank          fpp.reload       fpp.stats
├── fpp.inventory     fpp.move         fpp.mine
├── fpp.place         fpp.storage      fpp.useitem
├── fpp.waypoint      fpp.rename       fpp.personality
├── fpp.badword       fpp.settings     fpp.peaks
- `fpp.ping`          fpp.attack       fpp.follow       fpp.notify
└── ... (all admin commands)

fpp.use           # User wildcard — basic commands (default: true / all players)
├── fpp.spawn.user    (limited by fpp.spawn.limit.<N>)
├── fpp.tph           fpp.xp           fpp.info.user
└── fpp.spawn.limit.1 (included — 1 personal bot by default)
```

**See [🔐 Permissions](Permissions) for the complete list.**

---

## 📊 PlaceholderAPI Integration

FPP provides **29+ placeholders** for use with other plugins:

**Server-Wide:**
- `%fpp_count%` - Number of bots (local + remote in NETWORK mode)
- `%fpp_local_count%` - Bots on this server only
- `%fpp_network_count%` - Bots on other proxy servers
- `%fpp_real%` - Real players online  
- `%fpp_total%` - Total players (real + bots)
- `%fpp_names%` - Comma-separated bot names

**Per-World:**
- `%fpp_count_<world>%` - Bots in specific world
- `%fpp_real_<world>%` - Real players in world

**Player-Relative:**
- `%fpp_user_count%` - Player's bot count
- `%fpp_user_max%` - Player's bot limit

**See [📊 Placeholders](Placeholders) for the complete list.**

---

## 🛠️ Technical Specifications

### 🏗️ **Architecture**
- **Built for Paper** - Uses Paper-specific APIs for best performance
- **NMS Integration** - Direct packet manipulation for tab list
- **Multi-threaded** - Background processing for heavy operations  
- **Memory Efficient** - Optimized entity management

### 📈 **Performance**
- **Lightweight** - Minimal server impact
- **Scalable** - Handle 100+ bots efficiently
- **Optimized Packets** - Reduced network overhead
- **Chunk Loading** - Smart chunk management

### 🔒 **Security**  
- **Permission-Based** - Granular access control
- **Input Validation** - Prevents exploits and crashes
- **Rate Limiting** - Anti-spam protections
- **Audit Trail** - Full command logging

---

## 🌐 Community & Support

### 💬 **Get Help**
- **Discord Server:** [Join Community](https://discord.gg/QSN7f67nkJ)
- **Discord:** [Report Bugs](https://discord.gg/QSN7f67nkJ)
- **Wiki:** You're reading it! 📚

### 📢 **Stay Updated**  
- **Modrinth:** [Download Updates](https://modrinth.com/plugin/fake-player-plugin-(fpp))
- **Changelog:** [View full version history](Changelog)
- **Discord:** Get notified of new versions

### 🤝 **Contributing**
FPP is open source under the MIT License! Check our [GitHub repository](https://github.com/Pepe-tf/fake-player-plugin) for:
- Bug reports and feature requests via [GitHub Issues](https://github.com/Pepe-tf/fake-player-plugin/issues)
- Code contributions and pull requests  
- Documentation improvements
- Community support

### 💖 **Support the Project**
Donations are completely optional — every contribution goes directly toward improving the plugin:
- [GitHub Sponsors](https://github.com/sponsors/Pepe-tf)
- [Patreon](https://www.patreon.com/c/F_PP?utm_medium=unknown&utm_source=join_link&utm_campaign=creatorshare_creator&utm_content=copyLink)
- [Ko-fi](https://ko-fi.com/fakeplayerplugin)

---

## ⚖️ Legal & Licensing

**Fake Player Plugin** is **open-source software** released under the **MIT License**, developed by **Bill_Hub**.

- ✅ **Free to use** on your server
- ✅ **Open source** — view, fork, and contribute on [GitHub](https://github.com/Pepe-tf/fake-player-plugin)
- ✅ **Modify and redistribute** with attribution (MIT License)
- ✅ **Commercial use** permitted under MIT terms

> **License:** [MIT License](https://github.com/Pepe-tf/fake-player-plugin/blob/main/LICENSE) · [Full Copyright Notice](/legal/copyright)

---

## 🎉 Ready to Start?

1. **📖 Read** [Getting Started](Getting-Started) for setup instructions
2. **⌨️ Learn** [Commands](Commands) to control your bots  
3. **⚙️ Configure** [Configuration](Configuration) to customize behavior
4. **🎨 Customize** [Bot Names](Bot-Names) and [Skins](Skin-System)
5. **🚀 Deploy** and enjoy your enhanced server!

**Welcome to the future of Minecraft server population management!** 🎮
