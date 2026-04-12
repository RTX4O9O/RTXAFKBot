# ꜰᴀᴋᴇ ᴘʟᴀʏᴇʀ ᴘʟᴜɢɪɴ (FPP)

> Spawn realistic fake players on your Paper server — with tab list presence, server list count, join/leave messages, in-world bodies, guaranteed skins, chunk loading, bot swap/rotation, fake chat, AI conversations, area mining, block placing, pathfinding, per-bot settings GUI, LuckPerms integration, proxy network support, and full hot-reload.

[![Version](https://img.shields.io/modrinth/v/fake-player-plugin-%28fpp%29?style=flat-square&label=version&color=0079FF&logo=modrinth)](https://modrinth.com/plugin/fake-player-plugin-(fpp))
![MC](https://img.shields.io/badge/Minecraft-1.21.x-0079FF?style=flat-square)
![Platform](https://img.shields.io/badge/platform-Paper-0079FF?style=flat-square)
![Java](https://img.shields.io/badge/Java-21-0079FF?style=flat-square)
[![Modrinth](https://img.shields.io/badge/Modrinth-FPP-00AF5C?style=flat-square&logo=modrinth)](https://modrinth.com/plugin/fake-player-plugin-(fpp))
[![Discord](https://img.shields.io/badge/Discord-Join%20Server-5865F2?style=flat-square&logo=discord&logoColor=white)](https://discord.gg/QSN7f67nkJ)
[![Wiki](https://img.shields.io/badge/Wiki-fakeplayerplugin.xyz-7B8EF0?style=flat-square)](https://fakeplayerplugin.xyz)

---

## What It Does

FPP adds fake players to your server that look and behave like real ones:

- Show up in the **tab list** and **server list player count**
- Broadcast **join, leave, and kill messages**
- Spawn as **physical NMS ServerPlayer entities** — pushable, damageable, solid
- Always have a **real skin** (guaranteed fallback chain — never Steve/Alex unless you want it)
- **Load chunks** around them exactly like a real player
- **Rotate their head** to face nearby players
- **Swim automatically** in water and lava — mimics a real player holding spacebar
- **Send fake chat messages** from a configurable message pool (with LP prefix/suffix support, typing delays, burst messages, mention replies, and event reactions)
- **Swap in and out** automatically with fresh names and personalities
- **Persist across restarts** — they come back where they left off
- **Freeze** any bot in place with `/fpp freeze`
- **Open bot inventory** — 54-slot GUI with equipment slots; right-click any bot entity to open
- **Pathfind to players** — A* grid navigation with WALK, ASCEND, DESCEND, PARKOUR, BREAK, PLACE move types
- **Mine blocks** — continuous or one-shot block breaking; area selection with pos1/pos2 cuboid mode
- **Place blocks** — continuous block placing with per-bot supply container support
- **Right-click automation** — assign a command to any bot; right-clicking it runs the command
- **Transfer XP** — drain a bot's entire XP pool to yourself with `/fpp xp`
- **Named waypoint routes** — save patrol routes; bots walk them on a loop with `/fpp move --wp`
- **Rename bots** — rename any active bot with full state preservation (inventory, XP, LP group, tasks)
- **Per-bot settings GUI** — shift+right-click any bot to open a 6-row settings chest (General · Chat · PvP · Cmds · Danger)
- **AI conversations** — bots respond to `/msg` with AI-generated replies; 7 providers (OpenAI, Groq, Anthropic, Gemini, Ollama, Copilot, Custom); per-bot personalities via `personalities/` folder
- **Badword filter** — case-insensitive with leet-speak normalization, auto-rename bad names, remote word list
- **LuckPerms** — per-bot group assignment, weighted tab-list ordering, prefix/suffix in chat and nametags
- **Proxy/network support** — Velocity & BungeeCord cross-server chat, alerts, and shared database
- **Config sync** — push/pull configuration files across your proxy network
- **PlaceholderAPI** — 29+ placeholders including per-world bot counts, network state, spawn cooldown, and new proxy-aware counts
- Fully **hot-reloadable** — no restarts needed

---

## Requirements

| Requirement | Version |
|-------------|---------|
| [Paper](https://papermc.io/downloads/paper) | 1.21.x |
| Java | 21+ |
| [LuckPerms](https://luckperms.net) | Optional — auto-detected |
| [PlaceholderAPI](https://www.spigotmc.org/resources/placeholderapi.6245/) | Optional — auto-detected (29+ placeholders) |
| [WorldGuard](https://dev.bukkit.org/projects/worldguard) | Optional — auto-detected (no-PvP region protection) |

> **PlaceholderAPI Integration:** FPP provides 29+ placeholders including per-world bot counts, player-relative stats, network state, and system status. See [PLACEHOLDERAPI.md](PLACEHOLDERAPI.md) for the complete reference.

> **Compatibility:** Supports all Paper 1.21.x versions (1.21.0 through 1.21.11). Check the server console after startup for any version-specific notes.

> SQLite is bundled — no database setup required. MySQL is available for multi-server/proxy setups.

---

## Installation

1. Download the latest `fpp-*.jar` from [![Modrinth](https://img.shields.io/modrinth/v/fake-player-plugin-%28fpp%29?style=flat-square&label=Modrinth&color=00AF5C&logo=modrinth)](https://modrinth.com/plugin/fake-player-plugin-(fpp)/versions) and place it in your `plugins/` folder.
2. Restart your server — config files are created automatically.
3. Edit `plugins/FakePlayerPlugin/config.yml` to your liking.
4. Run `/fpp reload` to apply changes at any time.

> **Updating?** FPP automatically migrates your config on first start and creates a timestamped backup before changing anything.

---

## Commands

All commands are under `/fpp` (aliases: `/fakeplayer`, `/fp`).

| Command | Description |
|---------|-------------|
| `/fpp` | Plugin info — version, active bots, download links |
| `/fpp help [page]` | Interactive GUI help menu — paginated, permission-filtered, click-navigable |
| `/fpp spawn [amount] [--name <name>]` | Spawn fake player(s) at your location |
| `/fpp despawn <name\|all\|random [n]>` | Remove a bot by name, remove all, or remove a random set |
| `/fpp list` | List all active bots with uptime and location |
| `/fpp freeze <name\|all> [on\|off]` | Freeze or unfreeze bots — frozen bots are immovable |
| `/fpp inventory <bot>` | Open the bot's full 54-slot inventory GUI (alias: `/fpp inv`) |
| `/fpp move <bot> <player>` | Navigate a bot to an online player using A* pathfinding |
| `/fpp move <bot> --wp <route>` | Patrol a named waypoint route on a loop |
| `/fpp move <bot> --stop` | Stop the bot's current navigation |
| `/fpp mine <bot> [once\|stop]` | Continuous or one-shot block mining |
| `/fpp mine <bot> --pos1\|--pos2\|--start\|--status\|--stop` | Area-selection cuboid mining mode |
| `/fpp place <bot> [once\|stop]` | Continuous or one-shot block placing |
| `/fpp storage <bot> [name\|--list\|--remove\|--clear]` | Register supply containers for mine/place restocking |
| `/fpp use <bot>` | Bot right-clicks / activates the block it's looking at |
| `/fpp waypoint <name> [add\|remove\|list\|clear]` | Manage named patrol route waypoints |
| `/fpp xp <bot>` | Transfer all of a bot's XP to yourself |
| `/fpp cmd <bot> <command>` | Execute a command on a bot (or `--add`/`--clear`/`--show` for stored right-click command) |
| `/fpp rename <old> <new>` | Rename a bot preserving all state (inventory, XP, LP group, tasks) |
| `/fpp personality <bot> set\|reset\|show` | Assign or clear AI personality per bot |
| `/fpp personality list\|reload` | List available personality files or reload them |
| `/fpp badword add\|remove\|list\|reload` | Manage the runtime badword list |
| `/fpp chat [on\|off\|status]` | Toggle the fake chat system |
| `/fpp swap [on\|off\|status\|now <bot>\|list\|info <bot>]` | Toggle / manage the bot swap/rotation system |
| `/fpp peaks [on\|off\|status\|next\|force\|list\|wake <name>\|sleep <name>]` | Time-based bot pool scheduler |
| `/fpp rank <bot> <group>` | Assign a specific bot to a LuckPerms group |
| `/fpp rank random <group> [num\|all]` | Assign random bots to a LuckPerms group |
| `/fpp rank list` | List all active bots with their current LuckPerms group |
| `/fpp lpinfo [bot-name]` | LuckPerms diagnostic info — prefix, weight, rank, ordering |
| `/fpp stats` | Live statistics panel — bots, frozen, system status, DB totals, TPS |
| `/fpp info [bot <name> \| spawner <name>]` | Query the session database |
| `/fpp tp <name>` | Teleport yourself to a bot |
| `/fpp tph [name]` | Teleport your bot to yourself |
| `/fpp settings` | Open the in-game settings GUI — toggle config values live |
| `/fpp alert <message>` | Broadcast an admin message network-wide (proxy) |
| `/fpp sync push [file]` | Upload config file(s) to the proxy network |
| `/fpp sync pull [file]` | Download config file(s) from the proxy network |
| `/fpp sync status [file]` | Show sync status and version info |
| `/fpp sync check [file]` | Check for local changes vs network version |
| `/fpp migrate` | Backup, migration, and export tools |
| `/fpp reload` | Hot-reload all config, language, skins, name/message pools |

---

## Permissions

### Admin (`fpp.op` — default: op)

| Permission | Description |
|------------|-------------|
| `fpp.op` | All admin commands (admin wildcard, default: op) |
| `fpp.spawn` | Spawn bots (unlimited, supports `--name` and multi-spawn) |
| `fpp.delete` | Remove bots |
| `fpp.list` | List all active bots |
| `fpp.freeze` | Freeze / unfreeze any bot or all bots |
| `fpp.chat` | Toggle fake chat |
| `fpp.swap` | Toggle bot swap |
| `fpp.rank` | Assign bots to LuckPerms groups |
| `fpp.lpinfo` | View LuckPerms diagnostic info for any bot |
| `fpp.stats` | View the `/fpp stats` live statistics panel |
| `fpp.info` | Query the database |
| `fpp.reload` | Reload configuration |
| `fpp.tp` | Teleport to bots |
| `fpp.tph` | Teleport any bot to you |
| `fpp.bypass.maxbots` | Bypass the global bot cap |
| `fpp.peaks` | Manage the peak-hours bot pool scheduler |
| `fpp.settings` | Open the in-game settings GUI |
| `fpp.inventory` | Open any bot's inventory GUI |
| `fpp.move` | Navigate bots with A* pathfinding |
| `fpp.cmd` | Execute or store commands on bots |
| `fpp.mine` | Enable/stop bot block mining |
| `fpp.place` | Enable/stop bot block placing |
| `fpp.storage` | Register supply containers for bots |
| `fpp.useitem` | Bot right-click / use-item automation |
| `fpp.waypoint` | Manage named patrol route waypoints |
| `fpp.rename` | Rename any bot (with full state preservation) |
| `fpp.rename.own` | Rename only bots the sender personally spawned |
| `fpp.personality` | Assign AI personalities to bots |
| `fpp.badword` | Manage the runtime badword filter list |
| `fpp.migrate` | Data migration and backup utilities |
| `fpp.alert` | Broadcast network-wide admin alerts |
| `fpp.sync` | Push/pull config across proxy network |

### User (`fpp.use` — default: true for all players)

| Permission | Description |
|------------|-------------|
| `fpp.use` | All user-tier commands (granted by default) |
| `fpp.user.spawn` | Spawn your own bot (limited by `fpp.spawn.limit.<num>`) |
| `fpp.user.tph` | Teleport your bot to you |
| `fpp.user.xp` | Transfer a bot's XP to yourself |
| `fpp.info.user` | View your bot's location and uptime |

### Bot Limits

Grant players a `fpp.spawn.limit.<num>` node to set how many bots they can spawn. FPP picks the highest one they have.

`fpp.spawn.limit.1` · `fpp.spawn.limit.2` · `fpp.spawn.limit.3` · `fpp.spawn.limit.5` · `fpp.spawn.limit.10` · `fpp.spawn.limit.15` · `fpp.spawn.limit.20` · `fpp.spawn.limit.50` · `fpp.spawn.limit.100`

> **LuckPerms example** — give VIPs 5 bots:
> ```
> /lp group vip permission set fpp.use true
> /lp group vip permission set fpp.spawn.limit.5 true
> ```

---

## Configuration Overview

Located at `plugins/FakePlayerPlugin/config.yml`. Run `/fpp reload` after any change.

| Section | What it controls |
|---------|-----------------|
| `language` | Language file to load (`language/en.yml`) |
| `debug` | Legacy master debug switch; per-subsystem toggles under `logging.debug.*` |
| `update-checker` | Enable/disable startup version check |
| `metrics` | Opt-out toggle for anonymous FastStats usage statistics |
| `limits` | Global bot cap, per-user limit, spawn tab-complete presets |
| `spawn-cooldown` | Seconds between `/fpp spawn` uses per player (`0` = off) |
| `bot-name` | Display name format for admin/user bots (`admin-format`, `user-format`) |
| `luckperms` | `default-group` — LP group assigned to every new bot at spawn |
| `skin` | Skin mode (`auto` / `custom` / `off`), `guaranteed-skin` toggle, pool, `skins/` folder |
| `badword-filter` | Name profanity filter — leet-speak normalization, remote word list, auto-rename |
| `bot-interaction` | Right-click / shift-right-click settings GUI toggles |
| `body` | Physical entity (`enabled`), `pushable`, `damageable`, `pick-up-items`, `pick-up-xp`, `drop-items-on-despawn` |
| `persistence` | Whether bots rejoin on server restart; task state (mine/place/patrol) also persisted |
| `join-delay` / `leave-delay` | Random delay range (ticks) for natural join/leave timing |
| `messages` | Toggle join, leave, and kill broadcast messages; admin compatibility notifications |
| `combat` | Bot HP and hurt sound |
| `death` | Respawn on death, respawn delay, item drop suppression |
| `chunk-loading` | Radius, update interval |
| `head-ai` | Enable/disable, look range, turn speed |
| `swim-ai` | Automatic swimming in water/lava (`enabled`, default `true`) |
| `collision` | Push physics — walk strength, hit strength, bot separation |
| `pathfinding` | A* options — parkour, break-blocks, place-blocks, place-material, arrival distances, node limits |
| `fake-chat` | Enable, chance, interval, typing delays, burst messages, bot-to-bot chat, mention replies, event reactions |
| `ai-conversations` | AI DM system — provider config, personality, typing delay, conversation history |
| `swap` | Auto rotation — session length, absence duration, min-online floor, retry-on-fail, farewell/greeting chat |
| `peak-hours` | Time-based bot pool scheduler — schedule, day-overrides, stagger-seconds, min-online |
| `performance` | Position sync distance culling (`position-sync-distance`) |
| `tab-list` | Show/hide bots in the player tab list |
| `config-sync` | Cross-server config push/pull mode (`DISABLED` / `MANUAL` / `AUTO_PULL` / `AUTO_PUSH`) |
| `database` | `mode` (`LOCAL` / `NETWORK`), `server-id`, SQLite (default) or MySQL |

---

## AI Conversations

Bots can respond to `/msg`, `/tell`, and `/whisper` commands with AI-generated replies matching their personality.

**Setup:**
1. Edit `plugins/FakePlayerPlugin/secrets.yml` and add your API key
2. Set `ai-conversations.enabled: true` in `config.yml`
3. Bots will automatically respond — no restart needed

**Supported Providers** (picked in priority order — first key that works wins):

| Provider | Key in secrets.yml |
|----------|-------------------|
| OpenAI | `openai-api-key` |
| Anthropic | `anthropic-api-key` |
| Groq | `groq-api-key` |
| Google Gemini | `google-gemini-api-key` |
| Ollama | `ollama-base-url` (local, no key needed) |
| Copilot / Azure | `copilot-api-key` |
| Custom OpenAI-compatible | `custom-openai-base-url` |

**Personalities:** Drop `.txt` files into `plugins/FakePlayerPlugin/personalities/` to create custom personality prompts. Assign per-bot with `/fpp personality <bot> set <name>`.

Bundled personalities: `friendly`, `grumpy`, `noob`.

---



Three modes — set with `skin.mode`:

| Mode | Behaviour |
|------|-----------|
| `auto` *(default)* | Fetches a real Mojang skin matching the bot's name |
| `custom` | Full control — per-bot overrides, a `skins/` PNG folder, and a random pool |
| `off` | No skin — bots use the default Steve/Alex appearance |

**Skin fallback** (`skin.guaranteed-skin`, default `false`) — when `false`, bots whose name has no matching Mojang account use the default Steve/Alex appearance. Set to `true` to attempt a skin fetch even for generated names.

In `custom` mode the resolution pipeline is: per-bot override → `skins/<name>.png` → random PNG from `skins/` folder → random entry from `pool` → Mojang API for the bot's own name.

---

## LuckPerms Integration

FPP treats bots as real NMS ServerPlayer entities — LuckPerms detects them as online players automatically.

- **`luckperms.default-group`** — assigns every new bot to an LP group at spawn (blank = LP's built-in `default`)
- **`/fpp rank <bot> <group>`** — change an individual bot's LP group at runtime, no respawn needed
- **`/fpp rank random <group> [num|all]`** — assign a group to random bots
- **`/fpp rank list`** — see each bot's current group at a glance
- **`/fpp lpinfo [bot]`** — diagnose prefix, weight, rank index, and packet profile name
- **Tab-list ordering** — `~fpp` scoreboard team keeps all bots below real players regardless of LP weight
- **Prefix/suffix** — bots use LuckPerms prefix/suffix automatically (real NMS entities — LP detects them natively)

```yaml
luckperms:
  default-group: ""   # e.g. "default", "vip", "admin"
```

---

## Proxy & Network Support

FPP supports multi-server **Velocity** and **BungeeCord** proxy networks.

Enable NETWORK mode on every backend server:

```yaml
database:
  enabled: true
  mode: "NETWORK"
  server-id: "survival"   # unique per server
  mysql-enabled: true
  mysql:
    host: "mysql.example.com"
    database: "fpp_network"
    username: "fpp_user"
    password: "your_password"
```

**Cross-server features in NETWORK mode:**
- Fake chat messages broadcast to all servers on the proxy
- `/fpp alert <message>` — network-wide admin alert
- Bot join/leave messages visible network-wide
- Remote bot tab-list entries synced across servers
- Per-server isolation — each server only manages its own bots

---

## Config Sync

Keep all servers' configurations in sync automatically:

```yaml
config-sync:
  mode: "AUTO_PULL"   # DISABLED | MANUAL | AUTO_PULL | AUTO_PUSH
```

| Mode | Behaviour |
|------|-----------|
| `DISABLED` | No syncing (default) |
| `MANUAL` | Only sync via `/fpp sync` commands |
| `AUTO_PULL` | Auto-pull latest config on every startup/reload |
| `AUTO_PUSH` | Push local changes to the network automatically |

Files synced: `config.yml`, `bot-names.yml`, `bot-messages.yml`, `language/en.yml`

Server-specific keys that NEVER sync: `database.server-id`, `database.mysql.*`, `debug`

---

## PlaceholderAPI

When [PlaceholderAPI](https://www.spigotmc.org/resources/placeholderapi.6245/) is installed, FPP registers its placeholders automatically — no restart needed.

> Full Documentation: [PLACEHOLDERAPI.md](PLACEHOLDERAPI.md)

FPP provides **29+ placeholders** organized into five categories:

### Server-Wide

| Placeholder | Value |
|-------------|-------|
| `%fpp_count%` | Number of currently active bots (local + remote in NETWORK mode) |
| `%fpp_local_count%` | Bots running on **this** server only |
| `%fpp_network_count%` | Bots running on **other** proxy servers (NETWORK mode) |
| `%fpp_max%` | Global max-bots limit (or `∞`) |
| `%fpp_real%` | Real (non-bot) players online |
| `%fpp_total%` | Total players (real + bots) |
| `%fpp_online%` | Alias for `%fpp_total%` |
| `%fpp_frozen%` | Number of currently frozen bots |
| `%fpp_names%` | Comma-separated list of bot display names (local + remote in NETWORK mode) |
| `%fpp_network_names%` | Display names of bots on **other** proxy servers only |
| `%fpp_version%` | Plugin version string |

### Config State

| Placeholder | Values | Config Key |
|-------------|--------|------------|
| `%fpp_chat%` | `on` / `off` | `fake-chat.enabled` |
| `%fpp_swap%` | `on` / `off` | `swap.enabled` |
| `%fpp_body%` | `on` / `off` | `body.enabled` |
| `%fpp_pushable%` | `on` / `off` | `body.pushable` |
| `%fpp_damageable%` | `on` / `off` | `body.damageable` |
| `%fpp_tab%` | `on` / `off` | `tab-list.enabled` |
| `%fpp_skin%` | `auto` / `custom` / `off` | `skin.mode` |
| `%fpp_max_health%` | number | `combat.max-health` |
| `%fpp_persistence%` | `on` / `off` | `persistence.enabled` |

### Network / Proxy

| Placeholder | Value |
|-------------|-------|
| `%fpp_network%` | `on` when `database.mode: NETWORK`, otherwise `off` |
| `%fpp_server_id%` | Value of `database.server-id` |
| `%fpp_spawn_cooldown%` | Configured cooldown in seconds (`0` = off) |

### Per-World

| Placeholder | Value |
|-------------|-------|
| `%fpp_count_<world>%` | Bots in world (e.g. `%fpp_count_world_nether%`) |
| `%fpp_real_<world>%` | Real players in world |
| `%fpp_total_<world>%` | Total (real + bots) in world |

### Player-Relative

| Placeholder | Value |
|-------------|-------|
| `%fpp_user_count%` | Bots owned by the player |
| `%fpp_user_max%` | Bot limit for the player |
| `%fpp_user_names%` | Comma-separated names of player's bots |

---

## Bot Names & Chat

| File | Purpose |
|------|---------|
| `bot-names.yml` | Random name pool. 1–16 chars, letters/digits/underscores. `/fpp reload` to update. |
| `bot-messages.yml` | Random chat messages. Supports `{name}` and `{random_player}` placeholders. |

When the name pool runs out, FPP generates names automatically (`Bot1234`, etc.).

Bot chat uses the server's real chat pipeline (`Player.chat()`), so formatting is handled by your existing chat plugin (LuckPerms, EssentialsX, etc.). For bodyless or proxy-remote bots, the `fake-chat.remote-format` key controls how messages appear (MiniMessage, supports `{name}` and `{message}` placeholders).

---

## Changelog

### v1.6.2 *(2026-04-12)*

**AI Conversations**
- New AI DM system — bots respond to `/msg`, `/tell`, `/whisper` with AI-generated replies
- 7 provider support: OpenAI, Anthropic, Groq, Google Gemini, Ollama, Copilot/Azure, Custom OpenAI-compatible
- API keys stored in `plugins/FakePlayerPlugin/secrets.yml` (never in `config.yml`)
- Per-bot personality assignment via `/fpp personality <bot> set <name>`; personalities stored as `.txt` files in `personalities/` folder
- Bundled sample personalities: `friendly`, `grumpy`, `noob`
- `BotConversationManager` — per-player conversation history, rate limiting, typing delay simulation

**New Commands**
- `/fpp place <bot> [once|stop]` — continuous or one-shot block placing with supply-container restocking. Permission: `fpp.place`
- `/fpp storage <bot> [name|--list|--remove|--clear]` — register supply containers for mine/place jobs. Permission: `fpp.storage`
- `/fpp use <bot>` — bot right-clicks / activates the block it's looking at. Permission: `fpp.useitem`
- `/fpp waypoint <name> [add|remove|list|clear]` — manage named patrol routes; bots walk them via `/fpp move --wp`. Permission: `fpp.waypoint`
- `/fpp personality [list|reload|<bot> set <name>|reset|show]` — assign AI personalities to bots. Permission: `fpp.personality`
- `/fpp badword add|remove|list|reload` — manage the runtime badword filter list. Permission: `fpp.badword`
- `/fpp rename <old> <new>` — rename any bot with full state preservation (inventory, XP, LP group, tasks). Permission: `fpp.rename` (any bot), `fpp.rename.own` (own bots only)
- `/fpp mine --pos1/--pos2/--start/--stop` — area-selection cuboid mining mode

**Per-Bot Settings GUI**
- Shift+right-click any bot to open a 6-row settings chest — no command needed
- Categories: ⚙ General · 💬 Chat · ⚔ PvP · 📋 Cmds · ⚠ Danger
- Toggle freeze, head-AI, chat tier, AI personality, stored commands, and bot deletion
- Controlled by `bot-interaction.shift-right-click-settings` config key

**Area Mining Mode**
- `/fpp mine <bot> --pos1` / `--pos2` — select a cuboid mining region
- `/fpp mine <bot> --start` — begin mining the selected area continuously
- Auto-restocks from nearest registered `StorageStore` container when inventory fills
- Selections persisted to `data/mine-selections.yml` — survive restarts and auto-resume

**Task Persistence (DB Schema v13)**
- Active tasks (mine/use/place/patrol) now saved to `fpp_bot_tasks` DB table on shutdown
- YAML fallback: `data/bot-tasks.yml` when database is disabled
- Bots automatically resume their job after server restart

**Navigation & Interaction Engine**
- `PathfindingService` — centralised shared navigation service
- `NavigationRequest` with `lockOnArrival` for atomic nav→action lock handoff
- `BotNavUtil` — static utilities: `findStandLocation`, `faceToward`, `isAtActionLocation`, `useStorageBlock`
- `StorageInteractionHelper` — shared lock→open-container→transfer→unlock lifecycle

**Per-Bot Item & XP Pickup Toggles**
- `body.pick-up-items` and `body.pick-up-xp` global defaults
- Per-bot overrides in `BotSettingGui` — toggling off immediately drops current inventory / XP to ground
- `BotXpPickupListener` gates both pickup events per-bot

**Config v47 → v53**
- Added `bot-interaction`, `ai-conversations`, `badword-filter` sections
- Added `body.drop-items-on-despawn` key
- Config reorganized into **10 clearly numbered sections** with better flow and organization
- `pathfinding` moved into section 4 (AI & Navigation)

---

### v1.6.0 *(2026-04-09)*

**Interactive Help GUI**
- `/fpp help` now opens a **54-slot double-chest GUI** instead of text output — paginated, permission-filtered, click-navigable
- Each command gets a semantically meaningful Material icon (compass for move, chest for inventory, diamond pickaxe for mine, etc.)
- Displays command name, description, usage modes, and permission node per item
- Up to 45 commands per page; previous/next arrows; close button; adapts live to your permission level

**New Commands**
- `/fpp inventory <bot>` (alias `inv`) — 54-slot bot inventory GUI with equipment slots (boots/leggings/chestplate/helmet/offhand) and type enforcement; right-click a bot entity to open without a command. Permission: `fpp.inventory`
- `/fpp move <bot> <player>` — navigate a bot to an online player using server-side A* pathfinding; supports WALK, ASCEND, DESCEND, PARKOUR, BREAK, PLACE move types; stuck detection + auto-recalculation; max 64-block range, 2000-node search. Permission: `fpp.move`
- `/fpp xp <bot>` — transfer the bot's entire XP pool to yourself; 30-second post-collection cooldown on bot XP pickup. Permission: `fpp.user.xp` (user-tier)
- `/fpp cmd <bot> <command>` — execute a command dispatched as the bot; `--add <command>` stores a right-click command on the bot; `--clear` removes it; `--show` displays it; right-clicking a bot with a stored command runs it instead of opening inventory GUI. Permission: `fpp.cmd`
- `/fpp mine <bot> [once|stop]` — continuous block mining at the bot's look-target; `once` breaks a single block; `stop` cancels; creative mode = instant break, survival = progressive mining with destroy progress. Permission: `fpp.mine`

**Settings GUI Expanded**
- Settings GUI now has **7 categories**: General, Body, Chat, Swap, Peak Hours, PvP, Pathfinding (up from 5)
- New pathfinding toggles: `pathfinding.parkour`, `pathfinding.break-blocks`, `pathfinding.place-blocks`, `pathfinding.place-material`
- New PvP AI settings: difficulty, defensive-mode, detect-range

**WorldGuard Integration**
- Bots are now protected from player-sourced PvP damage inside WorldGuard no-PvP regions
- `WorldGuardHelper.isPvpAllowed(location)` — fail-open: only regions with explicit DENY block bot damage

**Config**
- Config version bumped from **v47 → v51** — adds pathfinding section, XP pickup gate, and cmd/mine subsystem keys
- `body.pick-up-xp` — gate orb pickup globally (`true` by default); XpCommand post-collection cooldown also honours this flag
- `pathfinding.*` section with `parkour`, `break-blocks`, `place-blocks`, `place-material` keys

### v1.5.17 *(2026-04-07)*

**Swap System — Critical Fix & Major Enhancements**
- **Critical bug fix:** bots now actually rejoin after swapping out. The rejoin timer was being silently cancelled by `delete()` calling `cancel(uuid)` — bots left but never came back. Fixed by registering the rejoin task *after* `delete()` runs so `cancel()` finds nothing to cancel.
- New `swap.min-online: 0` — minimum bots that must stay online; swap skips if removing one would go below this floor
- New `swap.retry-rejoin: true` / `swap.retry-delay: 60` — auto-retry failed rejoins (e.g. when max-bots cap is temporarily full)
- Better bot identification on rejoin: same-name rejoins use `getByName()` (reliable even with stable UUIDs); random-name rejoins use UUID diff
- New `Personality.SPORADIC` type — unpredictable session variance for more natural patterns
- Expanded farewell/greeting message pools (~50 entries each)
- New `/fpp swap info <bot>` — shows personality, cycle count, time until next leave, and offline-waiting count
- `/fpp swap list` now shows **time remaining** in each session
- `/fpp swap status` now shows the `min-online` floor setting
- New `logging.debug.swap: false` — dedicated swap lifecycle debug channel

**Performance Optimizations**
- O(1) bot name lookup via secondary `nameIndex` map — `getByName()` was O(n) linear scan, now O(1) `ConcurrentHashMap` lookup maintained at all add/remove sites
- Position sync distance culling — position packets only broadcast to players within `performance.position-sync-distance: 128.0` blocks (0 = unlimited); saves significant packet overhead on large servers

**Log Cleanup**
- NmsPlayerSpawner per-spawn/despawn log messages demoted from INFO → DEBUG; no more log spam on every bot cycle

**Config Reorganization**
- `config.yml` restructured into 9 clearly labelled sections: Spawning · Appearance · Body & Combat · AI Systems · Bot Chat · Scheduling · Database & Network · Performance · Debug & Logging
- Config version → **v47**

### v1.5.15 *(2026-04-06)*

**Config Clarity Improvements**
- All timing-related values in `config.yml` now clearly state their unit (ticks or seconds) with human-readable conversion examples
- `join-delay` / `leave-delay` section header updated: *"Values are in TICKS — 20 ticks = 1 second"* with a quick-reference line; both `min`/`max` keys now carry inline `# ticks (20 ticks = 1 second)` comments
- `death.respawn-delay` comment now shows seconds equivalents: `15 = 0.75 s · 60 = 3 s · 100 = 5 s`
- `chunk-loading.update-interval` comment clarified to *"in ticks (20 ticks = 1 second). Lower = more responsive, higher = less overhead."*
- `swap.session` / `swap.absence` inline comments updated to show real-world time examples (e.g. `60 = 1 min, 300 = 5 min`)

**Build Pipeline Fixes**
- ProGuard obfuscation: removed `**.yml` from `-adaptresourcefilecontents` — prevents charset corruption of `plugin.yml` and language files on Windows builds
- ProGuard obfuscation: removed `-dontpreverify` — `StackMapTable` attributes are now preserved so the JVM verifier accepts the obfuscated jar
- ProGuard obfuscation: MySQL / SQLite shaded classes excluded from preverification to prevent `IncompleteClassHierarchyException`; merged back verbatim into the final jar

### v1.5.12 *(2026-04-05)*

**Stable Bot UUID Identity**
- `BotIdentityCache` — each bot name is permanently tied to a stable UUID; LuckPerms data, inventory, and session history persist across restarts
- Storage: in-memory cache → `fpp_bot_identities` DB table → `data/bot-identities.yml` YAML fallback

**In-Game Settings GUI**
- `/fpp settings` opens a 3-row chest GUI; 5 categories (General, Body, Chat, Swap, Peak Hours)
- Toggle booleans instantly; numeric values via chat-input prompt; reset page to JAR defaults; all changes apply live
- Permission: `fpp.settings`

**Peak Hours Scheduler**
- `PeakHoursManager` scales the bot pool by time-of-day windows (`peak-hours.schedule`, `day-overrides`, `stagger-seconds`)
- Crash-safe: sleeping-bot state persisted in `fpp_sleeping_bots` DB table, restored at startup
- New command: `/fpp peaks [on|off|status|next|force|list|wake <name>|sleep <name>]` — requires `swap.enabled: true`

**Per-Bot Chat Control**
- Random activity tier per bot: quiet / passive / normal / active / chatty
- `/fpp chat <bot> tier|mute|info` per-bot controls; `/fpp chat all <on|off|tier|mute>` for bulk operations
- Event-triggered chat (`event-triggers.*`) and keyword reactions (`keyword-reactions.*`)

**Bodyless Bot Mode & Bot Types**
- `bodyless` flag — bots without a world location exist in tab-list/chat only, no world entity
- `BotType`: `AFK` (passive) and `PVP` (combat via `BotPvpAI`)

**Config Migration v41 → v44**
- v41→v42: Added `peak-hours` section · v42→v43: Added `min-online`, `notify-transitions` · v43→v44: Removed `auto-enable-swap`

### v1.5.10 *(2026-04-05)*

**`/fpp swap` Toggle Fix**
- Running `/fpp swap` with no arguments now toggles swap on/off — exactly like `/fpp chat`
- `swap-enabled` and `swap-disabled` messages redesigned to match chat toggle style (`session rotation has been enabled/disabled`)
- `swap-status-on` / `swap-status-off` now follow the same `is enabled / is disabled` pattern as chat status messages

**Bot Chat Interval Fix**
- Bot chat loops are now restarted on `/fpp reload` so changes to `fake-chat.interval.min/max`, `fake-chat.chance`, and `fake-chat.stagger-interval` take effect immediately instead of waiting for each bot's old scheduled task to naturally expire
- `/fpp reload` output shows the new interval range as confirmation

**Fake Chat Realism Enhancements**
- `typing-delay` — simulates a 0–2.5 s typing pause before each message
- `burst-chance` / `burst-delay` — bots occasionally send a quick follow-up message
- `reply-to-mentions` / `mention-reply-chance` / `reply-delay` — bots can reply when a player says their name in chat
- `activity-variation` — random per-bot chat frequency tier (quiet/normal/active/very-active)
- `history-size` — bots avoid repeating their own recent messages
- `remote-format` — MiniMessage format for bodyless / proxy-remote bot broadcasts

**Swim AI**
- New `swim-ai.enabled` config key (default `true`) — bots automatically swim upward when submerged in water or lava, mimicking a player holding spacebar. Set to `false` to let bots sink.

**Language & Compatibility**
- `Biome.name()` deprecated call replaced with `Biome.getKey().getKey()` — compatible with Paper 1.22+
- `sync-usage` and `swap-now-usage` messages now end with a period for consistency
- Startup banner now shows **Bot swap** status in the Features section
- Startup banner now shows actual **Skin mode** (`auto`/`custom`/`off`) instead of `disabled`
- Config version bumped to `41` — adds fake-chat realism keys, remote-format, event-triggers, keyword-reactions; removes `tab-list-format` and `chat-format` (now handled by server chat pipeline)

### v1.5.8 *(2026-04-03)*

**Ghost Player / "Anonymous User" Fix**
- Replaced reflection-based `Connection` injection with a proper `FakeConnection` subclass whose `send()` methods are clean no-op overrides
- Eliminated the phantom "Anonymous User" entry with UUID 0 that appeared in the tab list when bots connected
- Eliminated `NullPointerException` and `ClassCastException` spam in server logs related to bot connections

**`%fpp_real%` / `%fpp_total%` Accuracy Fix**
- `%fpp_real%` now correctly subtracts bot count from `Bukkit.getOnlinePlayers()` — bots go through `placeNewPlayer()` and appear in the online list
- `%fpp_real_<world>%` similarly now excludes bots from per-world real-player counts
- `%fpp_total%` fixed to avoid double-counting; accurately reports real players + local bots (+ remote bots in NETWORK mode)

**Proxy `/fpp list` Improvements (NETWORK mode)**
- `/fpp list` now shows a `[server-id]` tag next to each local bot so admins can identify which server they belong to
- Remote bots from other proxy servers are now listed in a dedicated "Remote bots" section showing their server, name, and skin status
- Total counts include both local and remote bots

**New Proxy Placeholders**
- `%fpp_local_count%` — bots on this server only
- `%fpp_network_count%` — bots on other proxy servers (NETWORK mode)
- `%fpp_network_names%` — comma-separated display names from remote servers
- `%fpp_count%` and `%fpp_names%` now include remote bots in NETWORK mode

**LuckPerms ClassLoader Guard**
- Fixed `NoClassDefFoundError: net/luckperms/api/node/Node` crash on servers without LuckPerms installed
- All LP-dependent code is now properly gated behind `LuckPermsHelper.isAvailable()` checks; no LP classes are loaded unless LP is present

**Config Migration**
- Config version bumped to `37` (no structural key changes — version stamp only)
- Automatic migration on first startup from any previous version

### v1.5.6 *(2026-04-03)*

**Knockback fix (1.21.9–1.21.11)**
- Bots now correctly receive knockback on 1.21.9+ servers
- Tiered strategy system auto-detects the correct MC version API at startup (zero reflection overhead per hit)
- GET_MOVEMENT (1.21.9+): uses `packet.getMovement()` → `Vec3` → `player.lerpMotion(Vec3)`
- GET_XA (≤1.21.8): uses `packet.getXa/Ya/Za()` → `lerpMotion(double,double,double)` or `setDeltaMovement(Vec3)` fallback

**Double-disconnect crash fix (Paper 1.21+)**
- Fixed `IllegalStateException: Already retired` spam when bots are slain
- `injectPacketListenerIntoConnection()` now updates both `ServerPlayer.connection` AND `Connection.packetListener` fields
- Ensures our `onDisconnect` override handles double-retirement gracefully

**Bot Protection System**
- Command blocking — bots can no longer execute commands from ANY source (4-layer protection)
- Lobby spawn fix — 5-tick grace period prevents lobby plugins from teleporting bots
- New `BotCommandBlocker` and `BotSpawnProtectionListener`

### v1.5.4 *(2026-04-03)*

**PlaceholderAPI Expansion**
- 26+ placeholders across 5 categories (up from 18+)
- Fixed `%fpp_skin%` incorrectly returning `"disabled"` instead of actual mode
- Added `%fpp_persistence%` placeholder (shows `on`/`off` for persistence.enabled)
- New Network/Proxy category: `%fpp_network%`, `%fpp_server_id%`, `%fpp_spawn_cooldown%`

**Skin System Simplified**
- Removed `skin.fallback-pool` and `fallback-name` (eliminates API rate-limiting)
- Changed `guaranteed-skin` default from `true` → `false`
- Bots with non-Mojang names now use Steve/Alex skins by default
- Config section reduced from ~60 lines to ~18 lines

**Config Migration v35→v36**
- Auto-cleanup of orphaned LuckPerms keys (`weight-offset`, `use-prefix`, etc.)
- Removes old `skin.custom` section and `server:` section
- Automatic backup created before migration runs

**New Features**
- `/fpp` info screen includes Discord support link
- Full support for Leaf server (Paper fork)

**Technical**
- Config version bumped to 36
- Automatic migration on first startup
- Fully backward compatible


### v1.5.0 *(2026-03-31)*
- **Proxy/network mode** — full Velocity & BungeeCord support with NETWORK database mode; cross-server chat, alerts, bot join/leave broadcasts, and remote bot tab-list sync via `fpp:main` plugin messaging channel
- **Config sync** — `/fpp sync push/pull/status/check` commands; modes: `DISABLED`, `MANUAL`, `AUTO_PULL`, `AUTO_PUSH`; syncs `config.yml`, `bot-names.yml`, `bot-messages.yml`, `language/en.yml`; server-specific keys are never uploaded
- **Remote bot cache** — bots on other proxy servers tracked in thread-safe registry for tab-list sync (NETWORK mode)
- **BotTabTeam** — scoreboard team `~fpp` places all bots below real players in tab list regardless of LP weight
- **Per-bot LuckPerms groups** — `/fpp rank <bot> <group>`, `/fpp rank random <group> [num|all]`, `/fpp rank list`; no respawn needed
- **`/fpp lpinfo [bot]`** — in-game LP diagnostic: prefix, weight, rank index, packet profile name
- **`/fpp alert <message>`** — broadcast admin message to all servers on the proxy
- **Body pushable/damageable toggles** — `body.pushable` and `body.damageable`; live-reloadable; BotCollisionListener guards all push paths
- **Fake-chat format** — `fake-chat.chat-format` supports `{prefix}`, `{bot_name}`, `{suffix}`, `{message}`; full LP gradient and color support
- **Tab-list name format** — `bot-name.tab-list-format` supports `{prefix}`, `{bot_name}`, `{suffix}`, and any PAPI placeholder
- **LuckPerms default group** — `luckperms.default-group` config key; bots explicitly assigned `default` even when blank
- **Spawn cooldown** — `spawn-cooldown` config key; `fpp.bypass.cooldown` permission
- **Per-subsystem debug logging** — `logging.debug.startup/nms/packets/luckperms/network/config-sync/skin/database`
- **YAML auto-sync** — missing keys merged into `en.yml`, `bot-names.yml`, `bot-messages.yml` on every startup and reload
- **`/fpp migrate`** enhancements — `status`, `backup`, `backups`, `lang`, `names`, `messages`, `config`, `db merge`, `db export`, `db tomysql`
- **Config version** bumped to `33`

### v1.4.28 *(2026-03-26)*
- **Skin diversity fix** — guaranteed-skin fallback pool uses on-demand random selection at startup
- **Vanilla skin pool** — 27 official Minecraft system accounts (Mojang devs + MHF_* skins)
- **Per-world placeholders** — `%fpp_count_<world>%`, `%fpp_real_<world>%`, `%fpp_total_<world>%`
- **`%fpp_online%`** — alias for `%fpp_total%`
- **Fake chat prefix/suffix** — `{prefix}` and `{suffix}` in `chat-format` for full LP integration
- **Spawn race condition fixed** — `/fpp despawn all` during spawn no longer leaves ghost entries
- **Portal/teleport bug fixed** — PDC-based entity recovery for bots pushed through portals
- **Body damageable toggle fixed** — event-level cancellation replaces entity-flag-only approach
- **Body config live reload** — `/fpp reload` immediately applies body pushable/damageable changes

### v1.4.27 *(2026-03-25)*
- **Unified spawn syntax** — `/fpp spawn` supports `[count] [world] [x y z] [--name <name>]`
- **Improved `/fpp reload` output** — box-drawing lines, per-step detail, timing line
- **`/fpp reload` canUse fix** — operators can now reload without explicit permission nodes

### v1.4.26 *(2026-03-25)*
- **Tab-list weight ordering overhauled** — bots perfectly respect LP group weights
- **Rank command system** — `/fpp rank <bot> <group>` and `/fpp rank random`
- **Restoration bug fixed** — bots restored after restart maintain correct weights and ranks
- **Auto-update on group change** — prefixes and tab ordering update in real-time

### v1.4.24 *(2026-03-24)*
- YAML file syncer — missing keys auto-merged on startup and `/fpp reload`
- `/fpp migrate lang|names|messages` — force-sync YAML files from JAR

### v1.4.23 *(2026-03-23)*
- Fixed bot name colours lost after server restart
- Fixed join/leave delays 20x longer than configured
- `/fpp reload` refreshes bot prefixes from LuckPerms immediately
- Added `/fpp despawn random [amount]`

### v1.4.22 *(2026-03-22)*
- `tab-list.enabled` — toggle bot visibility in the tab list
- Multi-platform download links in update notifications
- Enhanced `/fpp reload` with step-by-step progress

### v1.2.7 *(2026-03-14)*
- `/fpp freeze`, `/fpp stats`, PlaceholderAPI expansion, spawn cooldown, animated tab-list header/footer, metrics toggle

### v1.2.2 *(2026-03-14)*
- Guaranteed Skin system, `skin.fallback-name`, Mojang API rate-limit fix, config auto-migration

### v1.0.0-rc1 *(2026-03-08)*
- First stable release: full permission system, user-tier commands, bot persistence

### v0.1.0
- Initial release: tab list, join/leave messages, in-world body, head AI, collision/push system

---

## Support the Project

[![Ko-fi](https://img.shields.io/badge/Ko--fi-Support%20FPP-FF5E5B?style=flat-square&logo=ko-fi&logoColor=white)](https://ko-fi.com/fakeplayerplugin)

Donations are completely optional. Every contribution goes directly toward improving the plugin.

Thank you for using Fake Player Plugin. Without you, it wouldn't be where it is today.

---

## Links

- [Modrinth](https://modrinth.com/plugin/fake-player-plugin-(fpp)) — download
- [SpigotMC](https://www.spigotmc.org/resources/fake-player-plugin-fpp.133572/) — download
- [PaperMC Hangar](https://hangar.papermc.io/Pepe-tf/FakePlayerPlugin) — download
- [BuiltByBit](https://builtbybit.com/resources/fake-player-plugin.98704/) — download
- [Wiki](https://fakeplayerplugin.xyz) — documentation
- [Ko-fi](https://ko-fi.com/fakeplayerplugin) — support the project
- [Discord](https://discord.gg/QSN7f67nkJ) — support & feedback
- [GitHub](https://github.com/Pepe-tf/Fake-Player-Plugin-Public-) — source & issues

---

*Built for Paper 1.21.x · Java 21 · FPP v1.6.2 · [Modrinth](https://modrinth.com/plugin/fake-player-plugin-(fpp)) · [SpigotMC](https://www.spigotmc.org/resources/fake-player-plugin-fpp.133572/) · [PaperMC](https://hangar.papermc.io/Pepe-tf/FakePlayerPlugin) · [BuiltByBit](https://builtbybit.com/resources/fake-player-plugin.98704/) · [Wiki](https://fakeplayerplugin.xyz)*
