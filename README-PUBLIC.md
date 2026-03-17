# ꜰᴀᴋᴇ ᴘʟᴀʏᴇʀ ᴘʟᴜɢɪɴ (FPP)

> Spawn realistic fake players on your Paper server — with tab list presence, server list count, join/leave messages, in-world bodies, guaranteed skins, chunk loading, bot swap/rotation, fake chat, and full hot-reload support.

![Version](https://img.shields.io/badge/version-1.3.0%20HF%20--%201-0079FF?style=flat-square)
![MC](https://img.shields.io/badge/Minecraft-1.21.x-0079FF?style=flat-square)
![Platform](https://img.shields.io/badge/platform-Paper-0079FF?style=flat-square)
![Java](https://img.shields.io/badge/Java-21-0079FF?style=flat-square)
[![Modrinth](https://img.shields.io/badge/Modrinth-FPP-00AF5C?style=flat-square&logo=modrinth)](https://modrinth.com/plugin/fake-player-plugin-(fpp))
[![Discord](https://img.shields.io/badge/Discord-Join%20Server-5865F2?style=flat-square&logo=discord&logoColor=white)](https://discord.gg/ZhsstSJb)

---

## ✦ What It Does

FPP adds fake players to your server that look and behave like real ones:

- Show up in the **tab list** and **server list player count**
- Broadcast **join, leave, and kill messages**
- Spawn as **physical entities** in the world — pushable, damageable, solid
- Always have a **real skin** (no Steve/Alex unless you want it)
- **Load chunks** around them exactly like a real player
- **Rotate their head** to face nearby players
- **Send fake chat messages** from a configurable message pool
- **Swap in and out** automatically with fresh names and personalities
- **Persist across restarts** — they come back where they left off
- **Freeze** any bot in place with `/fpp freeze`
- **PlaceholderAPI** support — display bot count and status anywhere
- Fully **hot-reloadable** — no restarts needed

---

## ✦ Requirements

| Requirement | Version |
|---|---|
| [Paper](https://papermc.io/downloads/paper) | 1.21.x |
| Java | 21+ |
| [PacketEvents](https://modrinth.com/plugin/packetevents) | 2.x |
| [LuckPerms](https://luckperms.net) | Optional — auto-detected |
| [PlaceholderAPI](https://www.spigotmc.org/resources/placeholderapi.6245/) | Optional — auto-detected |

> SQLite is bundled — no database setup required. MySQL is available for multi-server setups.

---

## ✦ Installation

1. Download `fpp-1.3.0.jar` from [Modrinth](https://modrinth.com/plugin/fake-player-plugin-(fpp)) and place it in your `plugins/` folder.
2. Download [PacketEvents](https://modrinth.com/plugin/packetevents) and place it in `plugins/` too.
3. Restart your server — config files are created automatically.
4. Edit `plugins/FakePlayerPlugin/config.yml` to your liking.
5. Run `/fpp reload` to apply changes at any time.

> **Updating?** FPP automatically migrates your config on first start and creates a timestamped backup before changing anything.

---

## ✦ Commands

All commands are under `/fpp` (aliases: `/fakeplayer`, `/fp`).

| Command | Description |
|---|---|
| `/fpp` | Plugin info — version, active bots, Modrinth link |
| `/fpp help [page]` | Paginated help with clickable navigation |
| `/fpp spawn [amount] [--name <name>]` | Spawn fake player(s) at your location |
| `/fpp delete <name\|all>` | Remove a bot by name, or remove all |
| `/fpp list` | List all active bots with uptime and location |
| `/fpp chat [on\|off\|status]` | Toggle the fake chat system |
| `/fpp swap [on\|off\|status]` | Toggle the bot swap/rotation system |
| `/fpp freeze <name\|all> [on\|off]` | Freeze or unfreeze a bot — body becomes immovable; shown with ❄ in list/stats |
| `/fpp stats` | Live statistics panel — bots, frozen count, system status, DB totals, TPS |
| `/fpp reload` | Hot-reload all config, language, skins, and name/message pools |
| `/fpp info [bot <name> \| spawner <name>]` | Query the session database |
| `/fpp tp <name>` | Teleport yourself to a bot |
| `/fpp tph` | Teleport your bot to yourself |

---

## ✦ Permissions

### Admin

| Permission | Description |
|---|---|
| `fpp.*` | All permissions |
| `fpp.spawn` | Spawn bots (unlimited, supports `--name` and multi-spawn) |
| `fpp.delete` | Remove bots |
| `fpp.list` | List all active bots |
| `fpp.chat` | Toggle fake chat |
| `fpp.swap` | Toggle bot swap |
| `fpp.freeze` | Freeze / unfreeze any bot or all bots |
| `fpp.stats` | View the `/fpp stats` live statistics panel |
| `fpp.reload` | Reload configuration |
| `fpp.info` | Query the database |
| `fpp.tp` | Teleport to bots |
| `fpp.bypass.maxbots` | Bypass the global bot cap |
| `fpp.bypass.cooldown` | Bypass the per-player spawn cooldown |
| `fpp.admin.migrate` | Backup, migrate, and export database |

### User (enabled for all players by default)

| Permission | Description |
|---|---|
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

## ✦ Configuration Overview

Located at `plugins/FakePlayerPlugin/config.yml`. Run `/fpp reload` after any change.

| Section | What it controls |
|---|---|
| `language` | Language file to load (`language/en.yml`) |
| `limits` | Global bot cap, per-user limit |
| `spawn-cooldown` | Seconds between `/fpp spawn` uses per player (`0` = off) |
| `bot-name` | Display name format for admin and user bots |
| `luckperms` | Whether to prepend the default-group prefix to bot names |
| `skin` | Skin mode (`auto` / `custom` / `off`), guaranteed skin, fallback account |
| `body` | Whether bots have a physical entity in the world |
| `persistence` | Whether bots rejoin on server restart |
| `join-delay` / `leave-delay` | Random delay range (ticks) for natural join/leave timing |
| `messages` | Toggle join, leave, and kill broadcast messages |
| `combat` | Bot HP and hurt sound |
| `death` | Respawn on death, respawn delay, item drop suppression |
| `chunk-loading` | Radius, update interval |
| `head-ai` | Enable/disable, look range, turn speed |
| `swap` | Auto rotation — session length, farewell/greeting chat, AFK simulation |
| `fake-chat` | Enable, message chance, interval |
| `tab-list` | Optional animated tab-list header/footer with bot count placeholders |
| `metrics` | Opt-out toggle for anonymous FastStats usage statistics |
| `database` | SQLite (default) or MySQL |

---

## ✦ Skin System

Three modes — set with `skin.mode`:

| Mode | Behaviour |
|---|---|
| `auto` *(default)* | Fetches a real Mojang skin matching the bot's name |
| `custom` | Full control — per-bot overrides, a `skins/` PNG folder, and a random pool |
| `off` | No skin — bots use the default Steve/Alex appearance |

**Guaranteed Skin** (`skin.guaranteed-skin: true`, on by default) ensures every bot always gets a real skin, even if its name isn't a Mojang account. When the primary lookup fails, FPP falls back automatically:

> Bot name → folder skins → pool skins → `skin.fallback-name` (pre-fetched at startup)

Set `skin.fallback-name` to any valid Minecraft username (default: `Notch`).

---

## ✦ PlaceholderAPI

When [PlaceholderAPI](https://www.spigotmc.org/resources/placeholderapi.6245/) is installed, FPP registers its placeholders automatically — no restart needed.

| Placeholder | Value |
|---|---|
| `%fpp_count%` | Number of currently active bots |
| `%fpp_max%` | Global max-bots limit (or `∞`) |
| `%fpp_chat%` | `on` / `off` — fake-chat state |
| `%fpp_swap%` | `on` / `off` — bot-swap state |
| `%fpp_skin%` | Current skin mode (`auto` / `custom` / `off`) |
| `%fpp_body%` | `on` / `off` — body-spawn state |
| `%fpp_frozen%` | Number of currently frozen bots |
| `%fpp_version%` | Plugin version string |

---

## ✦ Bot Names & Chat

| File | Purpose |
|---|---|
| `bot-names.yml` | Random name pool. 1–16 chars, letters/digits/underscores. `/fpp reload` to update. |
| `bot-messages.yml` | Random chat messages. Supports `{name}` and `{random_player}` placeholders. |

When the name pool runs out, FPP generates names automatically (`Bot1234`, etc.).

---

## ✦ LuckPerms Integration

When LuckPerms is installed and `luckperms.use-prefix: true`:

- The **default group prefix** is automatically prepended to every bot's display name in the tab list, nametag, and messages
- Makes bots blend naturally with real players
- Disable any time with `luckperms.use-prefix: false`

---

## ✦ Changelog


### v1.3.0 HF - 1 *(2026-03-17)*
#### Hotfix
- Fixed NullPointerException in BotSwapAI when bot entity is null.
- Fixed StackOverflowError in FakePlayerManager.visualChain by adding a recursion base case.

### v1.3.0 *(2026-03-15)*
#### Changes
- Config reload now updates all config files, including language, bot-names, and bot-messages.
- Updated plugin info and documentation for Modrinth link and version 1.3.0.
- Removed `/fpp setpos` command and all references.

---

### v1.2.7 *(2026-03-14)*
- **`/fpp freeze <bot|all> [on|off]`** — freeze any bot in place; the Mannequin body becomes immovable and gravity is disabled. Frozen bots show ❄ in `/fpp list` and `/fpp stats`
- **`/fpp stats`** — live statistics panel: active / frozen bots, uptime breakdown, system status, database lifetime totals, and server TPS
- **PlaceholderAPI** — FPP registers a PAPI expansion automatically when PAPI is installed; 8 placeholders available (`%fpp_count%`, `%fpp_frozen%`, etc.)
- **`spawn-cooldown`** — per-player spawn cooldown in seconds (`0` = off); bypass with `fpp.bypass.cooldown`
- **`tab-list`** — optional animated tab-list header and footer with `{bot_count}`, `{real_count}`, `{total_count}`, `{max_bots}` placeholders
- **`metrics.enabled`** — opt-out toggle for anonymous FastStats usage statistics
- Config auto-migration handles the jump from any previous version; timestamped backup created before any changes

### v1.2.2 *(2026-03-14)*
- **Guaranteed Skin** — bots always spawn with a real skin; configurable fallback chain (folder → pool → fallback account). Steve/Alex only appears if you set `skin.mode: off`
- **`skin.fallback-name`** — set any valid Mojang username as the last-resort skin (default: `Notch`)
- **Rate-limit fix** — Mojang HTTP 429 responses are no longer cached as null; next spawn retries automatically
- **`head-ai.enabled`** — explicit toggle for head rotation; set `false` instead of `look-range: 0`
- **Config auto-migration** — upgrading from any previous version is fully automatic; a timestamped backup is created first
- **Bug fix** — startup crash (NPE in ConfigMigrator) when upgrading from older versions is resolved

### v1.1.4 *(2026-03-12)*
- **Chunk loading rewrite** — spiral ticket order, movement-delta detection, world-border clamping, `update-interval` config, instant release on bot removal
- Bot session stats: uptime, damage taken, death count, live location tracking
- DB location flush works correctly for body-less bots

### v1.0.15 *(2026-03-11)*
- Join/leave messages now broadcast correctly to all players
- LuckPerms prefix pipeline fixed — no more legacy `§`-code parse failures
- `luckperms.use-prefix` toggle added to config
- Modrinth link added to `/fpp` info screen (clickable)

### v1.0.0-rc1 *(2026-03-08)*
- First stable release candidate
- Full permission system: `fpp.user.*`, `fpp.bot.<num>` limit nodes, LuckPerms display name support
- User-tier commands: `/fpp spawn`, `/fpp tph`, `/fpp info` (own bots only)
- Bot persistence across server restarts

### v0.1.5
- Bot swap / rotation with personality archetypes, time-of-day bias, AFK-kick simulation
- MySQL + SQLite database backend
- `/fpp info` database query command
- `bot-messages.yml` fake-chat message pool
- Staggered join/leave delays

### v0.1.0
- Initial release: tab list, join/leave messages, in-world body, head AI, collision/push system

---

## ✦ Links

- [Modrinth](https://modrinth.com/plugin/fake-player-plugin-(fpp)) — download
- [Discord](https://discord.gg/ZhsstSJb) — support & feedback
- [GitHub](https://github.com/Pepe-tf/Fake-Player-Plugin-Public-) — source & issues

---

*Built for Paper 1.21.x · Java 21 · FPP v1.3.0 HF - 1 · [Modrinth](https://modrinth.com/plugin/fake-player-plugin-(fpp))*
