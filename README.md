# ꜰᴀᴋᴇ ᴘʟᴀʏᴇʀ ᴘʟᴜɢɪɴ (FPP)

> Spawn realistic fake players on your Paper server — with tab list presence, server list count, join/leave messages, in-world bodies, guaranteed skins, chunk loading, bot swap/rotation, fake chat, LuckPerms integration, proxy network support, and full hot-reload.

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
- **Send fake chat messages** from a configurable message pool (with LP prefix/suffix support)
- **Swap in and out** automatically with fresh names and personalities
- **Persist across restarts** — they come back where they left off
- **Freeze** any bot in place with `/fpp freeze`
- **LuckPerms** — per-bot group assignment, weighted tab-list ordering, prefix/suffix in chat and nametags
- **Proxy/network support** — Velocity & BungeeCord cross-server chat, alerts, and shared database
- **Config sync** — push/pull configuration files across your proxy network
- **PlaceholderAPI** — 26+ placeholders including per-world bot counts, network state, and spawn cooldown
- Fully **hot-reloadable** — no restarts needed

---

## Requirements

| Requirement | Version |
|-------------|---------|
| [Paper](https://papermc.io/downloads/paper) | 1.21.x |
| Java | 21+ |
| [PacketEvents](https://modrinth.com/plugin/packetevents) | 2.x |
| [LuckPerms](https://luckperms.net) | Optional — auto-detected |
| [PlaceholderAPI](https://www.spigotmc.org/resources/placeholderapi.6245/) | Optional — auto-detected (26+ placeholders) |

> **PlaceholderAPI Integration:** FPP provides 26+ placeholders including per-world bot counts, player-relative stats, network state, and system status. See [PLACEHOLDERAPI.md](PLACEHOLDERAPI.md) for the complete reference.

> **Compatibility:** Semi-support for older 1.21 releases (1.21.0 to 1.21.8). On those servers some features may be disabled and FPP will run in a restricted compatibility mode — check the server console for warnings.

> SQLite is bundled — no database setup required. MySQL is available for multi-server/proxy setups.

---

## Installation

1. Download the latest `fpp-*.jar` from [![Modrinth](https://img.shields.io/modrinth/v/fake-player-plugin-%28fpp%29?style=flat-square&label=Modrinth&color=00AF5C&logo=modrinth)](https://modrinth.com/plugin/fake-player-plugin-(fpp)/versions) and place it in your `plugins/` folder.
2. Download [PacketEvents](https://modrinth.com/plugin/packetevents) and place it in `plugins/` too.
3. Restart your server — config files are created automatically.
4. Edit `plugins/FakePlayerPlugin/config.yml` to your liking.
5. Run `/fpp reload` to apply changes at any time.

> **Updating?** FPP automatically migrates your config on first start and creates a timestamped backup before changing anything.

---

## Commands

All commands are under `/fpp` (aliases: `/fakeplayer`, `/fp`).

| Command | Description |
|---------|-------------|
| `/fpp` | Plugin info — version, active bots, download links |
| `/fpp help [page]` | Paginated help with clickable navigation |
| `/fpp spawn [amount] [--name <name>]` | Spawn fake player(s) at your location |
| `/fpp despawn <name\|all\|random [n]>` | Remove a bot by name, remove all, or remove a random set |
| `/fpp list` | List all active bots with uptime and location |
| `/fpp freeze <name\|all> [on\|off]` | Freeze or unfreeze bots — frozen bots are immovable; shown with an ice icon in list/stats |
| `/fpp chat [on\|off\|status]` | Toggle the fake chat system |
| `/fpp swap [on\|off\|status]` | Toggle the bot swap/rotation system |
| `/fpp rank <bot> <group>` | Assign a specific bot to a LuckPerms group |
| `/fpp rank random <group> [num\|all]` | Assign random bots to a LuckPerms group |
| `/fpp rank list` | List all active bots with their current LuckPerms group |
| `/fpp lpinfo [bot-name]` | LuckPerms diagnostic info — prefix, weight, rank, ordering |
| `/fpp stats` | Live statistics panel — bots, frozen, system status, DB totals, TPS |
| `/fpp info [bot <name> \| spawner <name>]` | Query the session database |
| `/fpp tp <name>` | Teleport yourself to a bot |
| `/fpp tph [name]` | Teleport your bot to yourself |
| `/fpp alert <message>` | Broadcast an admin message network-wide (proxy) |
| `/fpp sync push [file]` | Upload config file(s) to the proxy network |
| `/fpp sync pull [file]` | Download config file(s) from the proxy network |
| `/fpp sync status [file]` | Show sync status and version info |
| `/fpp sync check [file]` | Check for local changes vs network version |
| `/fpp migrate` | Backup, migration, and export tools |
| `/fpp reload` | Hot-reload all config, language, skins, name/message pools |

---

## Permissions

### Admin

| Permission | Description |
|------------|-------------|
| `fpp.*` | All permissions (admin wildcard) |
| `fpp.spawn` | Spawn bots (unlimited, supports `--name` and multi-spawn) |
| `fpp.spawn.multiple` | Spawn more than 1 bot at a time |
| `fpp.spawn.name` | Use the `--name` flag |
| `fpp.delete` | Remove bots |
| `fpp.delete.all` | Remove all bots at once |
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
| `fpp.bypass.maxbots` | Bypass the global bot cap |
| `fpp.bypass.cooldown` | Bypass the per-player spawn cooldown |
| `fpp.admin.migrate` | Backup, migrate, and export database |

### User (enabled for all players by default)

| Permission | Description |
|------------|-------------|
| `fpp.user.*` | All user commands |
| `fpp.user.spawn` | Spawn your own bot (limited by `fpp.bot.<num>`) |
| `fpp.user.tph` | Teleport your bot to you |
| `fpp.user.info` | View your bot's location and uptime |

### Bot Limits

Grant players a `fpp.bot.<num>` node to set how many bots they can spawn. FPP picks the highest one they have.

`fpp.bot.1` · `fpp.bot.2` · `fpp.bot.3` · `fpp.bot.5` · `fpp.bot.10` · `fpp.bot.15` · `fpp.bot.20` · `fpp.bot.50` · `fpp.bot.100`

> **LuckPerms example** — give VIPs 5 bots:
> ```
> /lp group vip permission set fpp.user.spawn true
> /lp group vip permission set fpp.bot.5 true
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
| `bot-name` | Display name format for admin/user bots; `tab-list-format` with `{prefix}` / `{bot_name}` / `{suffix}` |
| `luckperms` | `default-group` — LP group assigned to every new bot at spawn |
| `skin` | Skin mode (`auto` / `custom` / `off`), `guaranteed-skin` toggle, pool, `skins/` folder |
| `body` | Physical entity (`enabled`), `pushable`, `damageable` |
| `persistence` | Whether bots rejoin on server restart |
| `join-delay` / `leave-delay` | Random delay range (ticks) for natural join/leave timing |
| `messages` | Toggle join, leave, and kill broadcast messages; admin compatibility notifications |
| `combat` | Bot HP and hurt sound |
| `death` | Respawn on death, respawn delay, item drop suppression |
| `chunk-loading` | Radius, update interval |
| `head-ai` | Enable/disable, look range, turn speed |
| `collision` | Push physics — walk strength, hit strength, bot separation |
| `swap` | Auto rotation — session length, farewell/greeting chat, AFK simulation |
| `fake-chat` | Enable, message chance, interval, `chat-format` (supports `{prefix}` / `{bot_name}` / `{suffix}`) |
| `tab-list` | Show/hide bots in the player tab list |
| `config-sync` | Cross-server config push/pull mode (`DISABLED` / `MANUAL` / `AUTO_PULL` / `AUTO_PUSH`) |
| `database` | `mode` (`LOCAL` / `NETWORK`), `server-id`, SQLite (default) or MySQL |

---

## Skin System

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
- **Prefix/suffix** — bot nametags and chat format support `{prefix}` and `{suffix}` placeholders
- **Display name format** — `bot-name.tab-list-format` supports `{prefix}`, `{bot_name}`, `{suffix}`, and PAPI placeholders

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

FPP provides **26+ placeholders** organized into five categories:

### Server-Wide

| Placeholder | Value |
|-------------|-------|
| `%fpp_count%` | Number of currently active bots |
| `%fpp_max%` | Global max-bots limit (or `∞`) |
| `%fpp_real%` | Real (non-bot) players online |
| `%fpp_total%` | Total players (real + bots) |
| `%fpp_online%` | Alias for `%fpp_total%` |
| `%fpp_frozen%` | Number of currently frozen bots |
| `%fpp_names%` | Comma-separated list of bot display names |
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

**Chat format** (`fake-chat.chat-format`):

```yaml
fake-chat:
  chat-format: "&7{prefix}{bot_name}&7: {message}"
```

Placeholders: `{prefix}` (LP prefix), `{bot_name}`, `{suffix}` (LP suffix), `{message}`

---

## Changelog

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

*Built for Paper 1.21.x · Java 21 · FPP v1.5.6 · [Modrinth](https://modrinth.com/plugin/fake-player-plugin-(fpp)) · [SpigotMC](https://www.spigotmc.org/resources/fake-player-plugin-fpp.133572/) · [PaperMC](https://hangar.papermc.io/Pepe-tf/FakePlayerPlugin) · [BuiltByBit](https://builtbybit.com/resources/fake-player-plugin.98704/) · [Wiki](https://fakeplayerplugin.xyz)*
