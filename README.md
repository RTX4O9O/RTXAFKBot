# ꜰᴀᴋᴇ ᴘʟᴀʏᴇʀ ᴘʟᴜɢɪɴ (FPP)

> Spawn realistic fake players on your Paper server — with tab list presence, server list count, join/leave messages, in-world bodies, guaranteed skins, chunk loading, bot swap/rotation, fake chat, AI conversations, area mining, block placing, pathfinding, follow-target automation, per-bot settings GUI, per-bot swim AI & chunk-radius overrides, per-bot PvE attack settings, per-bot XP & item pickup control, tab-list ping simulation, NameTag plugin integration, LuckPerms integration, proxy network support, Velocity companion plugin, BungeeCord companion plugin, full Paper 1.21.x compatibility (1.21.0–1.21.11), and full hot-reload.

[![Version](https://img.shields.io/modrinth/v/fake-player-plugin-%28fpp%29?style=flat-square&label=version&color=0079FF&logo=modrinth)](https://modrinth.com/plugin/fake-player-plugin-(fpp))
![MC](https://img.shields.io/badge/Minecraft-1.21.x-0079FF?style=flat-square)
![Platform](https://img.shields.io/badge/platform-Paper-0079FF?style=flat-square)
![Java](https://img.shields.io/badge/Java-21-0079FF?style=flat-square)
[![License: MIT](https://img.shields.io/badge/License-MIT-green?style=flat-square)](https://github.com/Pepe-tf/fake-player-plugin/blob/main/LICENSE)
[![GitHub](https://img.shields.io/badge/GitHub-Open%20Source-181717?style=flat-square&logo=github)](https://github.com/Pepe-tf/fake-player-plugin)
[![Modrinth](https://img.shields.io/badge/Modrinth-FPP-00AF5C?style=flat-square&logo=modrinth)](https://modrinth.com/plugin/fake-player-plugin-(fpp))
[![Discord](https://img.shields.io/badge/Discord-Join%20Server-5865F2?style=flat-square&logo=discord&logoColor=white)](https://discord.gg/QSN7f67nkJ)
[![Wiki](https://img.shields.io/badge/Wiki-fakeplayerplugin.xyz-7B8EF0?style=flat-square)](https://fakeplayerplugin.xyz)
[![GitHub Sponsors](https://img.shields.io/badge/GitHub%20Sponsors-Sponsor-EA4AAA?style=flat-square&logo=githubsponsors&logoColor=white)](https://github.com/sponsors/Pepe-tf)
[![Patreon](https://img.shields.io/badge/Patreon-Support%20FPP-FF424D?style=flat-square&logo=patreon&logoColor=white)](https://www.patreon.com/c/F_PP?utm_medium=unknown&utm_source=join_link&utm_campaign=creatorshare_creator&utm_content=copyLink)

---

> 🎉 **FakePlayerPlugin is now Open Source!** The full source code is available on [GitHub](https://github.com/Pepe-tf/fake-player-plugin) under the **MIT License**. Contributions, bug reports, and pull requests are welcome!

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
- **Per-bot settings GUI** — shift+right-click any bot to open a 6-row settings chest (General · Chat · PvP · Cmds · Danger) — now available to all users with `fpp.settings` permission
- **AI conversations** — bots respond to `/msg` with AI-generated replies; 7 providers (OpenAI, Groq, Anthropic, Gemini, Ollama, Copilot, Custom); per-bot personalities via `personalities/` folder
- **Badword filter** — case-insensitive with leet-speak normalization, auto-rename bad names, remote word list
- **Set bot ping** — simulate realistic tab-list latency per bot with `/fpp ping`; fixed, random, or bulk modes
- **PvE attack automation** — bots walk to the sender and attack nearby entities or track mob targets with `/fpp attack`
- **Follow-target automation** — bots continuously follow any online player with `/fpp follow`; path recalculates as target moves, persists across restarts
- **Per-bot PvE settings** — `pveEnabled`, `pveRange`, `pvePriority`, `pveMobTypes` configurable per-bot via `BotSettingGui`
- **Skin persistence** — resolved skins saved to DB and re-applied on restart without a new Mojang API round-trip
- **NameTag integration** — nick-conflict guard, bot isolation from nick cache, skin sync, auto-rename via nick
- **LuckPerms** — per-bot group assignment, weighted tab-list ordering, prefix/suffix in chat and nametags
- **Proxy/network support** — Velocity & BungeeCord cross-server chat, alerts, and shared database
- **Velocity companion** (`fpp-velocity.jar`) — drop this into your Velocity proxy's `plugins/` folder to inflate the server-list player count and hover list with FPP bots; includes an anti-scam startup warning
- **BungeeCord companion** (`fpp-bungee.jar`) — identical feature set for BungeeCord/Waterfall networks; drop into your BungeeCord `plugins/` folder; no configuration needed
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
| [NameTag](https://lode.gg) | Optional — auto-detected (nick-conflict guard, skin sync) |

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
| `/fpp despawn <name\|all\|--random [n]\|--num <n>>` | Remove a bot by name, remove all, remove random N, or remove N oldest (blocked during persistence restore) |
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
| `/fpp waypoint <name> [create\|add\|remove\|list\|clear]` | Manage named patrol route waypoints (`add` auto-creates the route) |
| `/fpp xp <bot>` | Transfer all of a bot's XP to yourself |
| `/fpp cmd <bot> <command>` | Execute a command on a bot (or `--add`/`--clear`/`--show` for stored right-click command) |
| `/fpp rename <old> <new>` | Rename a bot preserving all state (inventory, XP, LP group, tasks) |
| `/fpp personality <bot> set\|reset\|show` | Assign or clear AI personality per bot |
| `/fpp personality list\|reload` | List available personality files or reload them |
| `/fpp ping [<bot>] [--ping <ms>\|--random] [--count <n>]` | Set simulated tab-list ping for one or all bots |
| `/fpp attack <bot> [--stop]` | Bot walks to sender and attacks nearby entities (PvE); `--mob` for stationary mob-targeting mode |
| `/fpp follow <bot\|all> <player>` | Bot continuously follows an online player; path recalculates as target moves |
| `/fpp follow <bot\|all> --stop` | Stop the bot's current follow loop |
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
| `fpp.ping` | View/set simulated tab-list ping for bots |
| `fpp.attack` | PvE attack automation (classic & mob-targeting modes) |
| `fpp.follow` | Follow-target bot automation (persistent across restarts) |
| `fpp.migrate` | Data migration and backup utilities |
| `fpp.alert` | Broadcast network-wide admin alerts |
| `fpp.sync` | Push/pull config across proxy network |

### User (`fpp.use` — default: true for all players)

| Permission | Description |
|------------|-------------|
| `fpp.use` | All user-tier commands (granted by default) |
| `fpp.spawn.user` | Spawn your own bot (limited by `fpp.spawn.limit.<num>`) |
| `fpp.tph` | Teleport your bot to you |
| `fpp.xp` | Transfer a bot's XP to yourself |
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
| `skin` | Skin mode (`player` / `random` / `none`), `guaranteed-skin` toggle, pool, `skins/` folder |
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
| `pathfinding` | A* options — parkour, break-blocks, place-blocks, place-material, arrival distances, node limits, max-fall |
| `fake-chat` | Enable, chance, interval, typing delays, burst messages, bot-to-bot chat, mention replies, event reactions |
| `ai-conversations` | AI DM system — provider config, personality, typing delay, conversation history |
| `swap` | Auto rotation — session length, absence duration, min-online floor, retry-on-fail, farewell/greeting chat |
| `peak-hours` | Time-based bot pool scheduler — schedule, day-overrides, stagger-seconds, min-online |
| `performance` | Position sync distance culling (`position-sync-distance`) |
| `tab-list` | Show/hide bots in the player tab list |
| `server-list` | Whether bots count in the server-list player total; `count-bots`, `include-remote-bots` |
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

## Skin System

Three modes — set with `skin.mode`:

| Mode | Behaviour |
|------|-----------|
| `player` *(default)* | Fetches a real Mojang skin matching the bot's name |
| `random` | Full control — per-bot overrides, a `skins/` PNG folder, and a random pool |
| `none` | No skin — bots use the default Steve/Alex appearance |

> **Legacy aliases:** `auto` = `player`, `custom` = `random`, `off` = `none` — all still accepted.

**Skin fallback** (`skin.guaranteed-skin`, default `true`) — bots whose name has no matching Mojang account get a random skin from the built-in 1000-player fallback pool. Set to `false` to use the default Steve/Alex appearance instead.

In `random` mode the resolution pipeline is: per-bot override → `skins/<name>.png` → random PNG from `skins/` folder → random entry from `pool` → Mojang API for the bot's own name.

---

## Proxy Companions

FPP ships two optional companion plugins that inflate the **proxy-level** server-list player count to include FPP bots.

### Velocity Companion (`fpp-velocity.jar`)

A lightweight standalone Velocity plugin.

**What it does:**
- Registers the `fpp:proxy` plugin-messaging channel and listens for `BOT_SPAWN` / `BOT_DESPAWN` / `SERVER_OFFLINE` messages from backend servers
- Maintains a live bot registry; pings all backend servers every 5 seconds and caches their player counts
- Intercepts `ProxyPingEvent` to inflate the proxy-level player count and hover sample list with bot names (up to 12 shown)

**Installation:**
1. Drop `fpp-velocity.jar` into your Velocity proxy's `plugins/` folder — no config file needed
2. Restart Velocity

**Requirements:** Velocity 3.3.0+

---

### BungeeCord Companion (`fpp-bungee.jar`)

Identical feature set for BungeeCord/Waterfall networks.

**What it does:**
- Registers the `fpp:proxy` plugin-messaging channel and listens for `BOT_SPAWN` / `BOT_DESPAWN` / `SERVER_OFFLINE` messages from backend servers
- Maintains a live bot registry; pings all backend servers every 5 seconds and caches their player counts
- Intercepts `ProxyPingEvent` to inflate the proxy-level player count and hover sample list with bot names (up to 12 shown)

**Installation:**
1. Drop `fpp-bungee.jar` into your BungeeCord/Waterfall proxy's `plugins/` folder — no config file needed
2. Restart BungeeCord

**Requirements:** BungeeCord or any Waterfall fork

---

> ⚠️ **FPP and both companion plugins are 100% FREE & open-source.** If you or your server paid money for any of them, you were **scammed by a reseller**. Always download from the official sources:
> - **Modrinth:** https://modrinth.com/plugin/fake-player-plugin-(fpp)
> - **GitHub:** https://github.com/Pepe-tf/fake-player-plugin
> - **Discord:** https://discord.gg/QSN7f67nkJ

---

## Changelog

### v1.6.6.1 *(2026-04-20)*

**FPP BungeeCord Companion (`fpp-bungee.jar`)**
- New standalone BungeeCord/Waterfall proxy plugin — drop `fpp-bungee.jar` into your BungeeCord `plugins/` folder; no config needed
- Registers `fpp:proxy` plugin-messaging channel; listens for `BOT_SPAWN`, `BOT_DESPAWN`, `SERVER_OFFLINE` messages from backend servers
- Maintains a live bot registry; pings all backend servers every 5 s and caches total player counts
- Intercepts `ProxyPingEvent` to inflate the proxy-level server-list player count and hover sample list (up to 12 bot names shown)
- Prints a prominent **anti-scam warning** on every startup — FPP and this companion are 100% free; if you paid for them you were scammed
- Source: `bungee-companion/` module in the FPP repository

**Bug Fixes**
- **Bot join/leave message color fix** — `BotBroadcast` now parses display names with full MiniMessage + legacy `&`/`§` color support. Previously, color tags in bot display names could render as raw text in join/leave broadcasts; display names now render exactly as defined in `en.yml`

---

### v1.6.6 *(2026-04-20)*

**FPP Velocity Companion (`fpp-velocity.jar`)**
- New standalone Velocity proxy plugin — drop `fpp-velocity.jar` into your Velocity `plugins/` folder; no config needed
- Registers `fpp:proxy` plugin-messaging channel; listens for `BOT_SPAWN`, `BOT_DESPAWN`, `SERVER_OFFLINE` messages from backend servers
- Maintains a live bot registry; pings all backend servers every 5 s and caches total player counts
- Intercepts `ProxyPingEvent` to inflate the proxy-level server-list player count and hover sample list (up to 12 bot names shown)
- Prints a prominent **anti-scam warning** on every startup — FPP and this companion are 100% free; if you paid for them you were scammed
- ⚠️ See the [Proxy Companions](#proxy-companions) section above for install steps and official download links

**Follow-Target Automation (`/fpp follow`)**
- New `/fpp follow <bot|all> <player> [--stop]` command — bot continuously follows an online player; path recalculates whenever the target moves >3.5 blocks
- `--stop` cancels following on one or all bots
- FOLLOW task type persisted in `fpp_bot_tasks` — bot resumes following after server restart if the target is online
- Permission: `fpp.follow`

**Per-Bot PvE Settings (now fully live)**
- `BotSettingGui` PvP tab now has live-editable per-bot PvE controls: `pveEnabled` toggle, `pveRange`, `pvePriority` (`nearest`/`lowest-health`), `pveMobTypes` (entity-type whitelist — empty = all hostile)
- Settings persisted in `fpp_active_bots` (DB schema v15→v16)
- Config keys: `attack-mob.default-range`, `attack-mob.default-priority`, `attack-mob.smooth-rotation-speed`, `attack-mob.retarget-interval`, `attack-mob.line-of-sight`

**Skin Persistence Across Restarts (DB v16→v17)**
- Resolved bot skins are now saved to `fpp_active_bots` (`skin_texture` + `skin_signature` columns)
- Bots reload their cached skin on server restart — no additional Mojang API round-trip needed

**Server-List Config Keys**
- New `server-list.count-bots` (default `true`) — controls whether bots are included in the displayed server-list player count
- New `server-list.include-remote-bots` (default `false`) — include remote proxy bots in the server-list count (NETWORK mode)
- Config v60→v61 migration adds both keys with no behaviour change for existing installs

**`pathfinding.max-fall`**
- New `pathfinding.max-fall` key (default `3`) — A* pathfinder will not descend more than this many blocks in a single unbroken fall

**Bug Fixes & Build**
- **`Attribute.MAX_HEALTH` compatibility** — fixed `NoSuchFieldError` crash on Paper/Purpur 1.21.1 and older. New `AttributeCompat` utility resolves the correct enum constant at class-load time (`MAX_HEALTH` on 1.21.3+, `GENERIC_MAX_HEALTH` on older builds) — all Paper 1.21.x versions are now fully supported
- **FPP Velocity banner** — replaced `█` block characters in the anti-scam section with `═` double-line rules matching the rest of the console banner style; version bumped to 1.6.6
- **IntelliJ build** — `fpp-velocity.iml` was missing, causing the IntelliJ artifact builder to output an empty `fpp-velocity.jar`; the module file is now committed with the correct source root and full Velocity API transitive classpath

**DB Schema v15 → v16 → v17**
- v15→v16: `fpp_active_bots` gains `pve_enabled BOOLEAN DEFAULT 0`, `pve_range DOUBLE DEFAULT 16.0`, `pve_priority VARCHAR(16)`, `pve_mob_type VARCHAR(64)` — per-bot PvE settings
- v16→v17: `fpp_active_bots` gains `skin_texture TEXT`, `skin_signature TEXT` — persists resolved skin data across restarts

**Config v60 → v61 → v62 → v63**
- v60→v61: `server-list` section added (`count-bots`, `include-remote-bots`)
- v61→v62: `pathfinding.max-fall` added
- v62→v63: `attack-mob.*` default config keys added

---

### v1.6.5.1 *(2026-04-17)*

**BotSettingGui Now Publicly Available**
- Per-bot settings GUI (shift+right-click any bot) is no longer dev-only — available to all users with `fpp.settings` permission
- Removed developer UUID gate; any player with `fpp.settings` now opens the 6-row settings chest (General · Chat · PvP · Cmds · Danger)
- Grant `fpp.settings` via LuckPerms to allow non-op users to manage their own bots' per-bot settings

---

### v1.6.5 *(2026-04-17)*

**Tab-List Ping Simulation (`/fpp ping`)**
- New `/fpp ping [<bot>] [--ping <ms>|--random] [--count <n>]` command — set the visible tab-list latency for one or all bots
- `--ping <ms>` sets a specific latency (0–9999); `--random` assigns random realistic values; no flag shows current ping
- `--count <n>` targets N random bots for bulk operations
- 4 granular permissions: `fpp.ping` (view), `fpp.ping.set` (set), `fpp.ping.random` (random), `fpp.ping.bulk` (bulk `--count`)

**PvE Attack Automation (`/fpp attack`)**
- New `/fpp attack <bot> [--stop]` command — bot walks to the command sender and continuously attacks nearby entities
- Respects 1.9+ attack cooldown and item-specific cooldown timers dynamically
- Permission: `fpp.attack`

**Permission System Restructure**
- New `fpp.admin` node as preferred alias for `fpp.op` — both grant full access identically
- New `fpp.despawn` node as preferred alias for `fpp.delete`; new `fpp.despawn.bulk` and `fpp.despawn.own` sub-nodes
- Granular sub-nodes for chat (`fpp.chat.global`, `.tier`, `.mute`, `.say`), move (`fpp.move.to`, `.waypoint`, `.stop`), mine (`fpp.mine.start`, `.once`, `.stop`, `.area`), place (`fpp.place.start`, `.once`, `.stop`), use (`fpp.useitem.start`, `.once`, `.stop`), rank (`fpp.rank.set`, `.clear`, `.bulk`), inventory (`fpp.inventory.cmd`, `.rightclick`), ping (`fpp.ping.set`, `.random`, `.bulk`)
- New `fpp.command` (controls `/fpp` visibility), `fpp.plugininfo` (full info panel), `fpp.spawn.multiple`/`.mass`/`.coords`, `fpp.notify` (update notifications)
- All nodes declared in both `Perm.java` and `plugin.yml` for LuckPerms tab-completion

**Skin Mode Rename**
- `skin.mode` values renamed: `auto` → `player`, `custom` → `random`, `off` → `none`
- Legacy values still accepted as aliases — no migration needed for existing configs

**FlagParser Utility**
- New reusable command argument/flag parser with deprecation aliases, duplicate detection, and conflict detection
- Used by `/fpp ping` and available for future commands

**UpdateChecker Beta Detection**
- `latestKnownVersion` and `isRunningBeta` fields on plugin — detects when running a build newer than the latest published release

---

### v1.6.4 *(2026-04-16)*

**NameTag Plugin Integration**
- New **soft-dependency** on the [NameTag](https://lode.gg) plugin — fully optional, auto-detected at startup
- **Nick-conflict guard** — prevents spawning a bot whose `--name` matches a real player's current NameTag nickname (`nametag-integration.block-nick-conflicts: true`)
- **Bot isolation** — after each bot spawns, FPP removes it from NameTag's internal player cache to prevent NameTag from treating bots as real players (`nametag-integration.bot-isolation: true`)
- **Sync-nick-as-rename** — when a bot has a NameTag nick set (e.g. via `/nick BotA Steve`), FPP auto-triggers a full rename so the bot's actual MC name becomes the nick (`nametag-integration.sync-nick-as-rename: false` — opt-in)
- **NameTag skin sync** — bots inherit skins assigned via NameTag; `SkinManager.getPreferredSkin()` checks NameTag-assigned skins first
- New `NameTagHelper` utility class: nick reading, skin reading, cache isolation, formatting strip, nick-conflict checks
- New `FakePlayer.nameTagNick` field tracks the cached nick from NameTag
- New lang key `spawn-name-taken-nick` shown when a bot name conflicts with a real player's nick

**Skin System Overhaul**
- New `SkinManager` class — centralised skin lifecycle: resolve, apply, cache, fallback, NameTag priority
- **Hardcoded 1000-player fallback skin pool** — replaces the old `skin.fallback-pool` and `skin.fallback-name` config keys; bots with non-Mojang names always get a real-looking skin from the built-in pool
- **DB skin cache** — new `fpp_skin_cache` table with 7-day TTL and auto-cleanup; resolved skins cached to database to avoid repeated Mojang API lookups
- `skin.mode` default enforced as `player` for existing installs that had it disabled (v58→v59 migration)
- `guaranteed-skin` default enforced as `true` for existing installs (v58→v59 migration)
- `skin.fallback-pool` and `skin.fallback-name` config keys removed — now hardcoded in SkinManager (v59→v60 migration)
- Exposed via `plugin.getSkinManager()` — public API: `resolveEffectiveSkin`, `applySkinByPlayerName`, `applySkinFromProfile`, `applyNameTagSkin`, `resetToDefaultSkin`, `preloadSkin`, `clearCache`

**Per-Bot Swim AI & Chunk Load Radius**
- Each bot now has an individual **swim AI toggle** — override the global `swim-ai.enabled` per-bot without restarting
- Each bot now has an individual **chunk load radius** — `-1` = follow global `chunk-loading.radius`, `0` = disable chunk loading for this bot, `1-N` = fixed radius (capped at global max)
- Both fields are initialised from the global config at spawn, fully persisted across restarts (DB column + YAML key), and editable at runtime

**BotSettingGui General Tab Expanded**
- General tab now has **7 action slots**: Frozen · Head-AI · Swim-AI *(new)* · Chunk-Load-Radius *(new, numeric prompt)* · Pick-Up-Items · Pick-Up-XP · Rename
- Chunk-load-radius uses a chat-input numeric prompt (same interaction model as `/fpp settings` numeric fields); type a number or `-1` to reset to global

**BotSettingGui PvP Tab**
- PvP category now shows full coming-soon override previews: difficulty, combat-mode, critting, s-tapping, strafing, shielding, speed-buffs, jump-reset, random, gear, defensive-mode

**DB Schema v14 → v15**
- v14: `fpp_active_bots` gains `swim_ai_enabled BOOLEAN DEFAULT 1`, `chunk_load_radius INT DEFAULT -1`
- v15: new `fpp_skin_cache` table (skin name → texture/signature/source/cached_at) with expiry index
- Fully backward-compatible — existing rows receive safe defaults on schema upgrade

**Config v53 → v60**
- v53→v54: `body.drop-items-on-despawn: false` injected into existing installs
- v54→v55: shared global pathfinding tuning keys added
- v55→v56: `nametag-integration` section added (block-nick-conflicts, bot-isolation)
- v56→v57: `nametag-integration.sync-nick-as-rename` added
- v58→v59: `skin.mode=player`, `guaranteed-skin=true`, `logging.debug.skin=true` enforced for existing installs
- v59→v60: removed `skin.fallback-pool` and `skin.fallback-name` (hardcoded in SkinManager)

---

### v1.6.3 *(2026-04-14)*

**Despawn Safety Guard**
- `despawn all`, `--random <n>`, and `--num <n>` are now blocked while bot persistence restoration is in progress at startup — prevents startup-queued console commands from killing bots mid-restore during the ~2–3 second restore window
- New lang key `delete-restore-in-progress` shown to sender when the operation is blocked
- Single-bot despawn (`/fpp despawn <name>`) is not affected — only bulk operations

**Waypoint Auto-Create**
- `/fpp wp add <route>` now auto-creates the route if it doesn't exist — no separate `create` step needed
- In-chat tip shown via new `wp-route-auto-created` lang key when a route is implicitly created
- `/fpp wp create` still exists and is valid, but is now optional
- `wp-usage` updated so `add` leads the usage string; `wp-list-empty` hint updated to point directly to `/fpp wp add <route>`

---

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
- Controlled by `bot-interaction.shift-right-click-settings` config key.

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
[![GitHub Sponsors](https://img.shields.io/badge/GitHub%20Sponsors-Sponsor-EA4AAA?style=flat-square&logo=githubsponsors&logoColor=white)](https://github.com/sponsors/Pepe-tf)
[![Patreon](https://img.shields.io/badge/Patreon-Support%20FPP-FF424D?style=flat-square&logo=patreon&logoColor=white)](https://www.patreon.com/c/F_PP?utm_medium=unknown&utm_source=join_link&utm_campaign=creatorshare_creator&utm_content=copyLink)

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
- [GitHub Sponsors](https://github.com/sponsors/Pepe-tf) — support the project
- [Patreon](https://www.patreon.com/c/F_PP?utm_medium=unknown&utm_source=join_link&utm_campaign=creatorshare_creator&utm_content=copyLink) — support the project
- [Discord](https://discord.gg/QSN7f67nkJ) — support & feedback
- [GitHub](https://github.com/Pepe-tf/fake-player-plugin) — **open-source repository · source, issues & pull requests**

---

*Built for Paper 1.21.x · Java 21 · FPP v1.6.6 · [Modrinth](https://modrinth.com/plugin/fake-player-plugin-(fpp)) · [SpigotMC](https://www.spigotmc.org/resources/fake-player-plugin-fpp.133572/) · [PaperMC](https://hangar.papermc.io/Pepe-tf/FakePlayerPlugin) · [BuiltByBit](https://builtbybit.com/resources/fake-player-plugin.98704/) · [Wiki](https://fakeplayerplugin.xyz) · [GitHub](https://github.com/Pepe-tf/fake-player-plugin)*
