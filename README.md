# ꜰᴀᴋᴇ ᴘʟᴀʏᴇʀ ᴘʟᴜɢɪɴ (FPP)

> Spawn realistic fake players on your Paper server — complete with tab list, server list count, join/leave/kill messages, staggered join/leave delays, in-world physics bodies, skin support, bot swap/rotation, fake chat, session database tracking, LuckPerms integration, and full hot-reload configuration.

![Version](https://img.shields.io/badge/version-1.0.0-0079FF?style=flat-square)
![MC](https://img.shields.io/badge/Minecraft-1.21.x-0079FF?style=flat-square)
![Platform](https://img.shields.io/badge/platform-Paper-0079FF?style=flat-square)
![Java](https://img.shields.io/badge/Java-21-0079FF?style=flat-square)

---

## ✦ Features

| Feature | Description |
|---|---|
| **Tab list** | Fake players appear in the tab list for every online and future player |
| **Server list count** | Fake players increment the online player count shown on the server list |
| **Join / leave messages** | Broadcast to all players and console — fully customisable in `language/en.yml` |
| **Kill messages** | Broadcast when a real player kills a bot (toggleable) |
| **In-world physics body** | Bots spawn as `Mannequin` entities — pushable, damageable, solid (toggleable) |
| **Custom nametag** | ArmorStand above the Mannequin displays the bot's display name |
| **Head AI** | Bot body faces the nearest player within configurable range using smooth interpolation |
| **Staggered join / leave** | Each bot joins and leaves with a random per-bot delay so it looks like real players |
| **Skin support** | `auto` mode — Paper resolves the real Mojang skin from the bot's name automatically |
| **Default skin** | When skins are disabled, bots use the default Steve/Alex appearance |
| **Death & respawn** | Bots can respawn at their last known location, or leave the server permanently |
| **Combat** | Bots take damage and play player hurt sounds; they cannot target or attack |
| **Chunk loading** | Bots keep chunks loaded around them like a real player (toggleable) |
| **Bot swap / rotation** | Bots automatically leave and rejoin with new names — with personality archetypes, time-of-day bias, farewell/greeting chat, and AFK-kick simulation |
| **Fake chat** | Bots send random chat messages from `bot-messages.yml` (toggleable, hot-reloadable) |
| **LuckPerms integration** | Detects installed LuckPerms and prepends the default-group prefix to every bot display name (toggleable) |
| **Uptime tracking** | `/fpp list` shows each bot's name, uptime, location, and who spawned it |
| **Database** | All bot sessions (who spawned, where, when, removal reason, last position) stored in SQLite or MySQL |
| **Persistence** | Active bots survive server restarts — they leave on shutdown and rejoin on startup at their last position |
| **Dynamic help** | Help command auto-discovers all registered sub-commands — no manual update needed when adding commands |
| **Clickable pagination** | Help pages have clickable ← prev / next → buttons |
| **Hex colour + small-caps** | Styled with `#0079FF` accent and Unicode small-caps throughout |
| **Fully translatable** | All player-facing text in `language/en.yml` |
| **Hot reload** | `/fpp reload` reloads config, language, name pool, and message pool instantly |
| **Bot name pool** | Names loaded from `bot-names.yml` — falls back to `Bot<number>` when the pool is exhausted |
| **User-tier commands** | Non-admins can spawn their own limited bots and teleport them with `fpp.user.*` |
| **Permission-based limits** | Per-player bot limits via `fpp.bot.<num>` permission nodes |

---

## ✦ Requirements

| Requirement | Version |
|---|---|
| [Paper](https://papermc.io/downloads/paper) | 1.21.x (tested on 1.21.11) |
| Java | 21+ |
| [PacketEvents](https://github.com/retrooper/packetevents) | 2.x (shaded / provided) |
| [LuckPerms](https://luckperms.net) *(optional)* | 5.x — auto-detected, not required |

> SQLite JDBC is bundled — no setup needed for local storage.  
> MySQL connector is also included if you prefer an external database.

---

## ✦ Installation

1. Download `fpp.jar` from the releases page.
2. Place it in your server's `plugins/` folder.
3. Restart the server — default config files are generated automatically.
4. Edit `plugins/FakePlayerPlugin/config.yml`, `language/en.yml`, `bot-names.yml`, and `bot-messages.yml` as desired.
5. Run `/fpp reload` to apply changes without restarting.

---

## ✦ Commands

All sub-commands are under `/fpp` (aliases: `/fakeplayer`, `/fp`).

### Admin Commands

| Command | Permission | Description |
|---|---|---|
| `/fpp help [page]` | `fpp.help` | Paginated help menu with clickable ← / → navigation |
| `/fpp spawn [amount] [--name <name>]` | `fpp.spawn` | Spawn fake player(s) at your location; optional count and custom name |
| `/fpp delete <name\|all>` | `fpp.delete` | Delete a bot by name, or delete all bots at once |
| `/fpp list` | `fpp.list` | List all active bots with name, uptime, world, coordinates, and spawner |
| `/fpp chat [on\|off\|status]` | `fpp.chat` | Toggle or query the bot fake-chat system |
| `/fpp swap [on\|off\|status]` | `fpp.swap` | Toggle or query the bot swap/rotation system |
| `/fpp reload` | `fpp.reload` | Hot-reload config, language, name pool, and message pool |
| `/fpp info` | `fpp.info` | Show total session count and current active bots from the database |
| `/fpp info bot <name>` | `fpp.info` | Live status + full spawn history for a specific bot name |
| `/fpp info spawner <name>` | `fpp.info` | All bots ever spawned by a specific player |
| `/fpp tp [botname]` | `fpp.tp` | Teleport yourself to any active bot |

### User Commands

| Command | Permission | Description |
|---|---|---|
| `/fpp spawn` | `fpp.user.spawn` | Spawn your personal bot (limited by `fpp.bot.<num>`, default 1) |
| `/fpp tph [botname]` | `fpp.user.tph` | Teleport your own bot(s) to your current position |
| `/fpp info [botname]` | `fpp.user.info` | View world, coordinates, and uptime of your own bots |

### Examples

```
/fpp spawn                  — spawn 1 fake player at your position
/fpp spawn 10               — spawn 10 fake players with staggered join delays
/fpp spawn --name Steve     — spawn 1 bot named "Steve"
/fpp spawn 5 --name Steve   — spawn 5 bots; first is "Steve", rest are random
/fpp delete Steve           — remove the bot named Steve with a leave message
/fpp delete all             — remove all bots with staggered leave messages
/fpp list                   — show all active bots with uptime and location
/fpp chat                   — show current fake-chat status
/fpp chat on                — enable fake chat
/fpp chat off               — disable fake chat
/fpp swap on                — enable the bot swap/rotation system
/fpp swap off               — disable the bot swap/rotation system
/fpp help                   — show page 1 of the help menu
/fpp help 2                 — show page 2
/fpp reload                 — hot-reload all configuration
/fpp info                   — show database stats (total sessions, active bots)
/fpp info bot Steve         — show live info + spawn history of the bot "Steve"
/fpp info spawner El_Pepes  — show all bots spawned by El_Pepes
/fpp tp Steve               — teleport yourself to the bot named Steve
/fpp tph                    — teleport your bot to you (if you own exactly one)
/fpp tph Steve              — teleport your bot named Steve to you
```

---

## ✦ Permissions

### Admin Permissions

| Permission | Default | Description |
|---|---|---|
| `fpp.*` | `op` | Grant ALL FPP permissions (admin wildcard) |
| `fpp.help` | `true` | View the help menu |
| `fpp.spawn` | `op` | Admin spawn — no bot limit, supports `--name` and multi-spawn |
| `fpp.spawn.multiple` | `op` | Spawn more than one bot at a time |
| `fpp.spawn.name` | `op` | Use `--name` to spawn with a custom name |
| `fpp.delete` | `op` | Delete bots by name |
| `fpp.delete.all` | `op` | Delete all bots at once |
| `fpp.list` | `op` | List all active bots |
| `fpp.chat` | `op` | Toggle bot fake-chat |
| `fpp.swap` | `op` | Toggle bot swap/rotation |
| `fpp.reload` | `op` | Reload plugin configuration |
| `fpp.info` | `op` | Full database query for any bot or spawner |
| `fpp.tp` | `op` | Teleport yourself to any bot |
| `fpp.bypass.maxbots` | `op` | Bypass the global `limits.max-bots` cap |

### User Permissions

| Permission | Default | Description |
|---|---|---|
| `fpp.user.*` | `true` | Grant all user-facing commands (all players by default) |
| `fpp.user.spawn` | `true` | Spawn bots up to the player's personal limit |
| `fpp.user.tph` | `true` | Teleport own bots to yourself |
| `fpp.user.info` | `true` | View own bot location and uptime |

### Bot Limit Nodes

Assign the highest node the player should receive. FPP picks the highest matching `fpp.bot.<num>` the player holds.

| Permission | Limit |
|---|---|
| `fpp.bot.1` | 1 bot *(default via `fpp.user.*`)* |
| `fpp.bot.2` | 2 bots |
| `fpp.bot.3` | 3 bots |
| `fpp.bot.5` | 5 bots |
| `fpp.bot.10` | 10 bots |
| `fpp.bot.15` | 15 bots |
| `fpp.bot.20` | 20 bots |
| `fpp.bot.50` | 50 bots |
| `fpp.bot.100` | 100 bots |

> **LuckPerms example** — give a VIP group 5 bots:  
> `lp group vip permission set fpp.user.spawn true`  
> `lp group vip permission set fpp.bot.5 true`

---

## ✦ Configuration

Located at `plugins/FakePlayerPlugin/config.yml`. Run `/fpp reload` to apply changes without restarting.

```yaml
# language file to load (language/<lang>.yml)
language: en

# verbose diagnostics — disable in production
debug: false

limits:
  max-bots: 1000          # 0 = unlimited
  user-bot-limit: 1       # default personal limit for fpp.user.spawn players
  spawn-presets: [1, 5, 10, 15, 20]  # tab-complete presets for admin spawn

bot-name:
  # MiniMessage format for admin-spawned bots. Placeholder: {bot_name}
  admin-format: '<#0079FF>[bot-{bot_name}]</#0079FF>'
  # MiniMessage format for user-spawned bots. Placeholders: {spawner}, {num}
  user-format: '<gray>[bot-{spawner}-{num}]</gray>'

luckperms:
  # Prepend the LuckPerms default-group prefix to every bot display name.
  use-prefix: true

skin:
  mode: auto              # auto | fetch | disabled
  clear-cache-on-reload: true

body:
  enabled: true           # spawn Mannequin physics body

persistence:
  enabled: true           # restore bots on restart at their last position

join-delay:
  min: 0                  # ticks (20 ticks = 1 second)
  max: 40

leave-delay:
  min: 0
  max: 40

messages:
  join-message: true
  leave-message: true
  kill-message: false

combat:
  max-health: 20.0
  hurt-sound: true

death:
  respawn-on-death: false  # false = bot leaves permanently on death
  respawn-delay: 60        # ticks before respawning
  suppress-drops: true

chunk-loading:
  enabled: true
  radius: 6               # chunk radius kept loaded around each bot

head-ai:
  look-range: 8.0         # blocks; 0 = disabled
  turn-speed: 0.3         # 0.0 = frozen, 1.0 = instant snap

collision:
  walk-radius: 0.85
  walk-strength: 0.22
  max-horizontal-speed: 0.30
  hit-strength: 0.45
  bot-radius: 0.90
  bot-strength: 0.14

swap:
  enabled: false
  session-min: 120        # seconds before a bot swaps out
  session-max: 600
  rejoin-delay-min: 5     # seconds between leave and rejoin
  rejoin-delay-max: 45
  jitter: 30              # ± random seconds added to session timer
  reconnect-chance: 0.15  # probability of rejoining with the same name
  afk-kick-chance: 5      # % chance of an extended rejoin gap (AFK simulation)
  farewell-chat: true
  greeting-chat: true
  time-of-day-bias: true  # longer sessions during peak evening hours

fake-chat:
  enabled: false
  require-player-online: true
  chance: 0.75
  interval:
    min: 5                # seconds between chat attempts
    max: 10

database:
  mysql-enabled: false    # set true to use MySQL instead of SQLite
  mysql:
    host: "localhost"
    port: 3306
    database: "fpp"
    username: "root"
    password: ""
    use-ssl: false
    pool-size: 5
    connection-timeout: 30000
```

> **SQLite fallback:** When MySQL is disabled or unreachable, FPP automatically uses SQLite stored at `plugins/FakePlayerPlugin/data/fpp.db` — no setup required.

---

## ✦ Bot Names & Messages

| File | Purpose |
|---|---|
| `bot-names.yml` | Pool of names randomly assigned to bots. Edit freely and run `/fpp reload`. |
| `bot-messages.yml` | Pool of chat messages bots randomly send. Supports `{name}` and `{random_player}` placeholders. |

When the name pool is exhausted, FPP generates names automatically (`Bot1234`).

---

## ✦ Bot Display Names

Bot display names (shown in the tab list, nametag, and join/leave messages) are fully configurable in `config.yml`:

- **Admin bots** — use `bot-name.admin-format` with `{bot_name}` placeholder
- **User bots** — use `bot-name.user-format` with `{spawner}` and `{num}` placeholders
- **LuckPerms prefix** — when `luckperms.use-prefix: true` and LuckPerms is installed, the default-group prefix is automatically prepended to every bot display name

### Examples

| Config | Ingame result (tab list / nametag) |
|---|---|
| `<#0079FF>[bot-{bot_name}]</#0079FF>` | `[bot-Steve]` in blue |
| `<gray>[bot-{spawner}-{num}]</gray>` | `[bot-El_Pepes-1]` in gray |
| LuckPerms prefix `§7` + admin format | `§7[bot-Steve]` |

---

## ✦ Bot Swap / Rotation System

When `swap.enabled: true`, each bot automatically leaves and rejoins with a fresh name after a configurable session length, creating organic-looking server activity:

- **Personality archetypes** — VISITOR (short stay), REGULAR (normal), LURKER (long stay)
- **Session growth** — bots that have swapped many times gradually stay longer
- **Time-of-day bias** — longer sessions during peak evening hours, shorter overnight
- **Farewell & greeting chat** — optional messages before leaving / after rejoining
- **Reconnect simulation** — configurable probability the bot rejoins with the same name
- **AFK-kick simulation** — small chance of an extended rejoin gap

---

## ✦ Skin Modes

| Mode | Description |
|---|---|
| `auto` *(default)* | `Mannequin.setProfile(name)` — Paper + client resolve the real skin automatically. Requires online-mode. |
| `fetch` | Plugin fetches texture value + signature from Mojang API asynchronously. Cached per session. Works on offline-mode servers. |
| `disabled` | No skin applied — bots use the default Steve/Alex appearance. |

---

## ✦ Database

FPP records every bot session for auditing and analytics:

| Field | Description |
|---|---|
| `bot_name` | Internal Minecraft name of the bot |
| `bot_uuid` | UUID assigned to the bot |
| `spawned_by` | Player who ran `/fpp spawn` |
| `world_name` | World where the bot was spawned |
| `spawn_x/Y/Z` | Spawn coordinates |
| `last_x/Y/Z` | Last known position (updated periodically) |
| `spawned_at` | Timestamp of spawn |
| `removed_at` | Timestamp of removal |
| `remove_reason` | `DELETED`, `DIED`, `SHUTDOWN`, or `SWAP` |

Use `/fpp info` to query the database in-game.

**Backends (priority order):**
1. **MySQL** — set `database.mysql-enabled: true` and fill in connection details
2. **SQLite** — automatic local fallback; stored at `plugins/FakePlayerPlugin/data/fpp.db`

---

## ✦ Language

All player-facing text lives in `plugins/FakePlayerPlugin/language/en.yml`.  
Edit and run `/fpp reload` — no restart required.  
Colours use **MiniMessage** format: `<#0079FF>text</#0079FF>`, `<gray>text</gray>`, `<bold>`, etc.

Key entries:

| Key | Description |
|---|---|
| `prefix` | Plugin prefix prepended to command feedback messages |
| `bot-join` | Join broadcast template — `{name}` = bot display name |
| `bot-leave` | Leave broadcast template — `{name}` = bot display name |
| `bot-kill` | Kill broadcast template — `{killer}`, `{name}` |
| `spawn-success` | Feedback to the player who ran `/fpp spawn` |
| `delete-success` | Feedback to the player who ran `/fpp delete` |

---

## ✦ LuckPerms Integration

FPP auto-detects LuckPerms at startup. When installed and `luckperms.use-prefix: true`:

- The **default group's prefix** (e.g. `§7`) is prepended to every bot's display name in the tab list, nametag, and join/leave messages
- This makes bots blend in naturally with real players who share the same prefix
- Disable with `luckperms.use-prefix: false` to use only the `bot-name.*` format colours

---

## ✦ Changelog

### v1.0.0 *(2026-03-11)*
- **Join/leave messages** now broadcast to all online players and console via direct delivery, matching vanilla Paper join/leave message behaviour
- `BotBroadcast` utility safely handles display names containing LuckPerms `§`-codes alongside MiniMessage tags — no more parse failures or blank messages
- Fully updated `config.yml` — clean flat structure, removed `admin-prefix`/`user-prefix`, added `luckperms.use-prefix` toggle, added MySQL `pool-size` and `connection-timeout`
- `Config.java` rewritten to match all new config key paths exactly
- Bot display name formatting cleaned up — internal names use `bot_<name>` / `ubot_<spawner>_<num>` prefix pattern (not configurable, keeps Minecraft name valid)
- `BotSwapAI` leave messages migrated to `BotBroadcast`
- All entity-death and manual delete paths unified through `BotBroadcast`

### v1.0.0-rc1 *(2026-03-08)*
- First stable release candidate
- Full permission system with `fpp.user.*`, `fpp.bot.<num>` limit nodes, and LuckPerms display name support
- User-tier commands: `/fpp spawn`, `/fpp tph`, `/fpp info` (own bots only)
- Bot persistence across server restarts (leave on shutdown, rejoin at last position on startup)
- O(1) entity lookup via entity-id index
- Reflection hot-path caching in `PacketHelper`
- Reservoir-sampling name picker — no full candidate list allocation per spawn

### v0.1.5
- Bot swap / rotation system with personality archetypes, time-of-day bias, AFK-kick simulation
- MySQL + SQLite database backend for full session tracking
- `/fpp info` database query command
- `bot-messages.yml` fake-chat message pool (1 000 messages)
- Staggered join/leave delays for realistic server activity
- Chunk loading around bots like a real player

### v0.1.0
- Initial release: tab list, join/leave messages, Mannequin body, head AI, collision/push system

---

## ✦ License

© 2026 Bill_Hub — All Rights Reserved.  
See [LICENSE](LICENSE) for full terms.  
Contact: Discord `Bill_Hub`

---

*Built for Paper 1.21.x · Java 21 · FPP v1.0.0*
