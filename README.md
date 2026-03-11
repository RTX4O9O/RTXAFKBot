# ꜰᴀᴋᴇ ᴘʟᴀʏᴇʀ ᴘʟᴜɢɪɴ (FPP)

> Spawn realistic fake players on your Paper server — complete with tab list, server list count, join/leave/kill messages, staggered join/leave delays, in-world physics bodies, skin support, bot swap/rotation, fake chat, session database tracking, LuckPerms integration, and full hot-reload configuration.

![Version](https://img.shields.io/badge/version-1.0.15-0079FF?style=flat-square)
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
# ─────────────────────────────────────────────────────────────────────────────
#  ꜰᴀᴋᴇ ᴘʟᴀʏᴇʀ ᴘʟᴜɢɪɴ  ·  config.yml  ·  v1.0.15
#  Run /fpp reload to apply changes without restarting the server.
#  Colors use MiniMessage syntax: <#0079FF>text</#0079FF>  <gray>text</gray>
# ─────────────────────────────────────────────────────────────────────────────

# ── Language ──────────────────────────────────────────────────────────────────
# Language file to load from plugins/FakePlayerPlugin/language/<lang>.yml
language: en

# ── Debug ─────────────────────────────────────────────────────────────────────
# Print verbose diagnostics to console. Useful for troubleshooting.
debug: false

# ─────────────────────────────────────────────────────────────────────────────
#  BOT LIMITS
# ─────────────────────────────────────────────────────────────────────────────
limits:
  # Maximum number of fake players allowed on the server at once. 0 = unlimited.
  max-bots: 1000

  # Default personal bot limit for players with fpp.user.spawn permission.
  # Override per-player/group via fpp.bot.<num> (e.g. fpp.bot.5 = 5 bots).
  user-bot-limit: 1

  # Count presets shown in tab-complete for the admin spawn command.
  spawn-presets: [1, 5, 10, 15, 20]

# ─────────────────────────────────────────────────────────────────────────────
#  BOT NAMES
#  Placeholders:  {bot_name} = bot's Minecraft name
#                 {spawner}  = player who spawned the bot  (user bots only)
#                 {num}      = bot number for that player  (user bots only)
#  Colors: full MiniMessage tags — <#RRGGBB>  <gray>  <bold>  etc.
# ─────────────────────────────────────────────────────────────────────────────
bot-name:
  # Display name shown in tab list and nametag for admin-spawned bots.
  admin-format: '{bot_name}'

  # Display name for bots spawned by normal users (fpp.user.spawn).
  user-format: 'bot-{spawner}-{num}'

# ─────────────────────────────────────────────────────────────────────────────
#  LUCKPERMS INTEGRATION
# ─────────────────────────────────────────────────────────────────────────────
luckperms:
  # Prepend the LuckPerms default-group prefix to every bot display name
  # Example with use-prefix: true  →  §7[bot-Steve]
  # Example with use-prefix: false →  [bot-Steve]   (colors from format only)
  use-prefix: true

# ─────────────────────────────────────────────────────────────────────────────
#  SKIN SYSTEM
# ─────────────────────────────────────────────────────────────────────────────
skin:
  # auto     — Paper resolves the skin from Mojang automatically (recommended).
  # fetch    — Plugin manually fetches texture data from Mojang API.
  # disabled — No skin; bots use the default Steve / Alex appearance.
  mode: auto

  # Clear the skin fetch cache when /fpp reload is run (fetch mode only).
  clear-cache-on-reload: true

# ─────────────────────────────────────────────────────────────────────────────
#  BOT BODY
# ─────────────────────────────────────────────────────────────────────────────
body:
  # Spawn a visible Mannequin entity as the bot's physical body.
  # false = bot exists only in the tab list and join/leave messages.
  enabled: true

# ─────────────────────────────────────────────────────────────────────────────
#  PERSISTENCE
# ─────────────────────────────────────────────────────────────────────────────
persistence:
  # Save active bots on shutdown and restore them when the server restarts.
  # Bots rejoin at the location they were at when the server stopped.
  enabled: true

# ─────────────────────────────────────────────────────────────────────────────
#  JOIN / LEAVE TIMING  (values are in ticks, 20 ticks = 1 second)
#  A random delay between min and max is chosen independently for each bot,
#  making multiple simultaneous spawns look like staggered real player joins.
# ─────────────────────────────────────────────────────────────────────────────
join-delay:
  min: 0    # Minimum ticks before a bot appears after being queued
  max: 40   # Maximum ticks  (set both to 0 for instant)

leave-delay:
  min: 0    # Minimum ticks before a bot disappears after being removed
  max: 40   # Maximum ticks

# ─────────────────────────────────────────────────────────────────────────────
#  MESSAGES
# ─────────────────────────────────────────────────────────────────────────────
messages:
  # Broadcast a vanilla-style "X joined the game" when a bot is spawned.
  join-message: true

  # Broadcast a vanilla-style "X left the game" when a bot is removed.
  leave-message: true

  # Broadcast "<player> was slain by <bot>" when a player kills a bot.
  kill-message: false

# ─────────────────────────────────────────────────────────────────────────────
#  COMBAT
# ─────────────────────────────────────────────────────────────────────────────
combat:
  # Base health points for bots (20.0 = default player health).
  max-health: 20.0

  # Play the vanilla player hurt sound when a bot takes damage.
  hurt-sound: true

# ─────────────────────────────────────────────────────────────────────────────
#  DEATH & RESPAWN
# ─────────────────────────────────────────────────────────────────────────────
death:
  # true  — bot respawns at its last known location after dying.
  # false — bot leaves the server permanently on death.
  respawn-on-death: false

  # Ticks to wait before a dead bot respawns (20 ticks = 1 second).
  respawn-delay: 60

  # Prevent bots from dropping items when they die.
  suppress-drops: true

# ─────────────────────────────────────────────────────────────────────────────
#  CHUNK LOADING
#  Bots keep chunks loaded around them the same way a real player would,
#  preventing mobs from despawning and farms from stopping near bots.
# ─────────────────────────────────────────────────────────────────────────────
chunk-loading:
  enabled: true

  # Chunk radius kept loaded around each bot (vanilla player default ≈ 10).
  radius: 6

# ─────────────────────────────────────────────────────────────────────────────
#  HEAD AI
#  Bots rotate their head to look at the nearest real player within range.
# ─────────────────────────────────────────────────────────────────────────────
head-ai:
  # Distance in blocks. Set to 0 to disable head tracking entirely.
  look-range: 8.0

  # Rotation interpolation speed (0.0 = frozen, 1.0 = instant snap).
  turn-speed: 0.3

# ─────────────────────────────────────────────────────────────────────────────
#  COLLISION & PUSH
#  Controls physical interaction between bots, players, and each other.
# ─────────────────────────────────────────────────────────────────────────────
collision:
  # Radius (blocks) at which walking into a bot triggers a push impulse.
  walk-radius: 0.85

  # Impulse strength when a player walks into a bot.
  walk-strength: 0.22

  # Maximum horizontal speed any push can impart on a bot.
  max-horizontal-speed: 0.30

  # Knockback impulse when a player punches a bot.
  hit-strength: 0.45

  # Radius at which two bots push each other apart (prevents stacking).
  bot-radius: 0.90

  # Impulse strength for bot-vs-bot separation.
  bot-strength: 0.14

# ─────────────────────────────────────────────────────────────────────────────
#  BOT SWAP / ROTATION
#  Simulates realistic session turnover — bots leave and rejoin with new
#  names after a configurable session length, mimicking real player activity.
# ─────────────────────────────────────────────────────────────────────────────
swap:
  enabled: false

  # Session duration range in seconds before a bot swaps out.
  session-min: 120
  session-max: 600

  # Gap between a bot leaving and its replacement joining (seconds).
  rejoin-delay-min: 5
  rejoin-delay-max: 45

  # Random ±jitter added to each bot's session timer (seconds).
  jitter: 30

  # Chance (0.0–1.0) that a rejoining bot reconnects with the same name.
  reconnect-chance: 0.15

  # Percent chance (0–100) a rejoin gap is extended 1–3 min (AFK-kick sim).
  afk-kick-chance: 5

  # Bot sends a farewell chat message before leaving.
  farewell-chat: true

  # Replacement bot sends a greeting chat message after joining.
  greeting-chat: true

  # Bias session lengths by server time-of-day (longer during peak hours).
  time-of-day-bias: true

# ─────────────────────────────────────────────────────────────────────────────
#  FAKE CHAT
#  Bots occasionally send random messages from bot-messages.yml.
# ─────────────────────────────────────────────────────────────────────────────
fake-chat:
  enabled: false

  # Only send messages when at least one real player is online.
  require-player-online: true

  # Probability (0.0–1.0) that a scheduled interval actually fires a message.
  chance: 0.75

  # Seconds between each bot's chat attempts (chosen randomly per bot).
  interval:
    min: 5
    max: 10

# ─────────────────────────────────────────────────────────────────────────────
#  DATABASE
#  Stores bot spawn history, last locations, and session data.
#  Default: SQLite — no setup required, stored in plugins/FakePlayerPlugin/data/
#  Optional: switch to MySQL for multi-server setups or external dashboards.
# ─────────────────────────────────────────────────────────────────────────────
database:
  # Set to true to use MySQL instead of the built-in SQLite.
  mysql-enabled: false

  mysql:
    host: "localhost"
    port: 3306
    database: "fpp"
    username: "root"
    password: ""
    use-ssl: false
    # Maximum number of pooled connections (HikariCP).
    pool-size: 5
    # Connection timeout in milliseconds.
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

### v1.0.15 *(2026-03-11)*
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

*Built for Paper 1.21.x · Java 21 · FPP v1.0.15*
