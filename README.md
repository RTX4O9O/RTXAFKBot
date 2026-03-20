# ꜰᴀᴋᴇ ᴘʟᴀʏᴇʀ ᴘʟᴜɢɪɴ (FPP)

> Spawn realistic fake players on your Paper server — complete with tab list, server list count, join/leave/kill messages, staggered join/leave delays, in-world physics bodies, real-player-equivalent chunk loading, guaranteed skin support, bot swap/rotation, fake chat, session database tracking, LuckPerms integration, and full hot-reload configuration.

![Version](https://img.shields.io/badge/version-1.4.20-0079FF?style=flat-square)
![MC](https://img.shields.io/badge/Minecraft-1.21.x-0079FF?style=flat-square)
![Platform](https://img.shields.io/badge/platform-Paper-0079FF?style=flat-square)
![Java](https://img.shields.io/badge/Java-21-0079FF?style=flat-square)
[![Modrinth](https://img.shields.io/badge/Modrinth-FPP-00AF5C?style=flat-square&logo=modrinth)](https://modrinth.com/plugin/fake-player-plugin-(fpp))
[![Discord](https://img.shields.io/badge/Discord-Join%20Server-5865F2?style=flat-square&logo=discord&logoColor=white)](https://discord.gg/ZhsstSJb)

---

## ✦ Features

| Feature | Description |
|---|---|
| **Tab list** | Fake players appear in the tab list for every online and future player |
| **Server list count** | Fake players increment the online player count shown on the server list |
| **Join / leave messages** | Broadcast to all players and console — fully customisable in `language/en.yml` |
| **Kill messages** | Broadcast when a real player kills a bot (toggleable) |
| **In-world physics body** | Bots spawn as `Mannequin` entities — pushable, damageable, solid (toggleable) |
| **Custom nametag** | Invisible ArmorStand above the Mannequin displays the bot's display name |
| **Head AI** | Bots smoothly rotate to face the nearest player within configurable range; fully toggleable |
| **Staggered join / leave** | Each bot joins and leaves with a random per-bot delay for a natural feel |
| **Guaranteed skin** | Every bot always spawns with a real skin — even generated names and user bots; configurable fallback chain ensures no Steve/Alex unless `mode: off` |
| **Death & respawn** | Bots can respawn at their last known location, or leave permanently on death |
| **Combat** | Bots take damage, play player hurt sounds, and can be killed; they cannot target or attack |
| **Real-player chunk loading** | Bots load chunks in spiral order exactly like a real player — mobs spawn, redstone ticks, farms run; world-border clamped; movement-delta detection skips redundant updates |
| **Session stats** | Each bot tracks damage taken, death count, uptime, and live position internally |
| **Bot swap / rotation** | Bots automatically leave and rejoin with new names — personality archetypes, time-of-day bias, farewell/greeting chat, and AFK-kick simulation |
| **Fake chat** | Bots send random chat messages from `bot-messages.yml` (toggleable, hot-reloadable) |
| **LuckPerms integration** | Auto-detects LuckPerms and prepends the default-group prefix to every bot display name (toggleable) |
| **Uptime tracking** | `/fpp list` shows each bot's name, formatted uptime, location, and who spawned it |
| **Database** | All bot sessions (who spawned, where, when, removal reason, last position) stored in SQLite or MySQL |
| **Persistence** | Active bots survive server restarts — they leave on shutdown and rejoin on startup at their last position |
| **Dynamic help** | Help command auto-discovers all registered sub-commands |
| **Clickable pagination** | Help pages have clickable ← prev / next → buttons |
| **Plugin info screen** | Bare `/fpp` shows version, author, active bot count, and a clickable Modrinth link |
| **Fully translatable** | All player-facing text in `language/en.yml`; MiniMessage colour support throughout |
| **Hot reload** | `/fpp reload` reloads config, language, name pool, message pool, and skin repository instantly |
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

Note: Semi-support is provided for older 1.21 releases (1.21.0 → 1.21.8). On those versions some features may be restricted or disabled and the plugin will enter a restricted compatibility mode — check server console for exact warnings and details.

> SQLite JDBC is bundled — no setup needed for local storage.  
> MySQL connector is also included if you prefer an external database.

---

## ✦ Installation

1. Download `fpp-1.3.0.jar` from [Modrinth](https://modrinth.com/plugin/fake-player-plugin-(fpp)).
2. Place it in your server's `plugins/` folder.
3. Restart the server — default config files are generated automatically.
4. Edit `plugins/FakePlayerPlugin/config.yml` as desired.
5. Run `/fpp reload` to apply changes without restarting.

> **Updating from an older version?** The migration system upgrades your config automatically on first start and creates a timestamped backup before making any changes.

---

## ✦ Commands

All sub-commands are under `/fpp` (aliases: `/fakeplayer`, `/fp`).

> Type bare `/fpp` to see the plugin info screen — version, active bots, and a Modrinth link.

### Admin Commands

| Command | Permission | Description |
|---|---|---|
| `/fpp help [page]` | `fpp.help` | Paginated help menu with clickable ← / → navigation |
| `/fpp spawn [amount] [--name <name>]` | `fpp.spawn` | Spawn fake player(s) at your location |
| `/fpp delete <name\|all>` | `fpp.delete` | Delete a bot by name, or delete all bots at once |
| `/fpp list` | `fpp.list` | List all active bots with name, uptime, world, coordinates, and spawner |
| `/fpp chat [on\|off\|status]` | `fpp.chat` | Toggle or query the fake-chat system |
| `/fpp swap [on\|off\|status]` | `fpp.swap` | Toggle or query the bot swap/rotation system |
| `/fpp freeze <name\|all> [on\|off]` | `fpp.freeze` | Freeze or unfreeze a bot — body becomes immovable; shown with ❄ in list/stats |
| `/fpp stats` | `fpp.stats` | Rich statistics panel — live bots, frozen count, system status, DB lifetime stats, TPS |
| `/fpp reload` | `fpp.reload` | Hot-reload config, language, name pool, message pool, and skin repository |
| `/fpp info` | `fpp.info` | Show total session count and current active bots from the database |
| `/fpp info bot <name>` | `fpp.info` | Live status + full spawn history for a specific bot name |
| `/fpp info spawner <name>` | `fpp.info` | All bots ever spawned by a specific player |
| `/fpp tp [botname]` | `fpp.tp` | Teleport yourself to any active bot |
| `/fpp migrate <sub>` | `fpp.admin.migrate` | Backup, status, config re-migration, DB export/merge |

### User Commands

| Command | Permission | Description |
|---|---|---|
| `/fpp spawn` | `fpp.user.spawn` | Spawn your personal bot (limited by `fpp.bot.<num>`, default 1) |
| `/fpp tph [botname]` | `fpp.user.tph` | Teleport your own bot(s) to your current position |
| `/fpp info [botname]` | `fpp.user.info` | View world, coordinates, and uptime of your own bots |

### Examples

```
/fpp                        — plugin info screen
/fpp spawn                  — spawn 1 bot at your position
/fpp spawn 10               — spawn 10 bots with staggered join delays
/fpp spawn --name Steve     — spawn 1 bot named "Steve"
/fpp delete Steve           — remove bot "Steve" with a leave message
/fpp delete all             — remove all bots with staggered leave messages
/fpp list                   — show all active bots with uptime and location
/fpp chat on / off / status — toggle or check fake chat
/fpp swap on / off / status — toggle or check bot swap
/fpp freeze Steve           — toggle frozen state on "Steve"
/fpp freeze Steve on        — freeze "Steve" (body becomes immovable)
/fpp freeze all off         — unfreeze every bot
/fpp stats                  — live plugin statistics panel
/fpp reload                 — hot-reload all configuration
/fpp info                   — database stats
/fpp info bot Steve         — live info + history of bot "Steve"
/fpp info spawner El_Pepes  — all bots spawned by El_Pepes
/fpp tp Steve               — teleport to bot "Steve"
/fpp tph                    — teleport your bot to you
/fpp migrate backup         — create a manual backup now
/fpp migrate status         — show config version, DB stats, backup count
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
| `fpp.freeze` | `op` | Freeze / unfreeze any bot or all bots |
| `fpp.stats` | `op` | View the `/fpp stats` live statistics panel |
| `fpp.reload` | `op` | Reload plugin configuration |
| `fpp.info` | `op` | Full database query for any bot or spawner |
| `fpp.tp` | `op` | Teleport yourself to any bot |
| `fpp.bypass.maxbots` | `op` | Bypass the global `limits.max-bots` cap |
| `fpp.bypass.cooldown` | `op` | Bypass the per-player spawn cooldown |
| `fpp.admin.migrate` | `op` | Access `/fpp migrate` — backups, config migration, DB export/merge |

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
> ```
> /lp group vip permission set fpp.user.spawn true
> /lp group vip permission set fpp.bot.5 true
> ```

---

## ✦ Configuration

Located at `plugins/FakePlayerPlugin/config.yml`. Run `/fpp reload` to apply changes without restarting.

```yaml
# ─────────────────────────────────────────────────────────────────────────────
#  ꜰᴀᴋᴇ ᴘʟᴀʏᴇʀ ᴘʟᴜɢɪɴ  ·  config.yml  ·  v1.3.0
#  Run /fpp reload to apply changes without restarting the server.
#  Colors use MiniMessage: <#0079FF>text</#0079FF>  <gray>text</gray>
# ─────────────────────────────────────────────────────────────────────────────

config-version: 14   # Internal — do NOT edit

language: en         # Language file (language/<lang>.yml)
debug: false         # Verbose console logging

update-checker:
  enabled: true

metrics:
  enabled: true      # Anonymous usage stats via FastStats (no personal data)

# ── Bot Limits ─────────────────────────────────────────────────────────────
limits:
  max-bots: 1000           # Global cap. 0 = unlimited.
  user-bot-limit: 1        # Personal limit for fpp.user.spawn players
  spawn-presets: [1, 5, 10, 15, 20]

# ── Spawn Cooldown ─────────────────────────────────────────────────────────
#  Seconds a player must wait between /fpp spawn uses. 0 = no cooldown.
#  Admins with fpp.bypass.cooldown are always exempt.
spawn-cooldown: 0

# ── Bot Display Names ──────────────────────────────────────────────────────
#  Placeholders: {bot_name}  {spawner}  {num}
#  Colors: <#0079FF>{bot_name}</#0079FF>  or  <gray>{bot_name}</gray>
bot-name:
  admin-format: '{bot_name}'
  user-format:  'bot-{spawner}-{num}'

# ── LuckPerms ──────────────────────────────────────────────────────────────
luckperms:
  use-prefix: true   # Prepend default-group prefix to every bot display name

# ── Skin ───────────────────────────────────────────────────────────────────
#  mode: auto   — Mojang skin matching the bot's name. Best for online-mode.
#  mode: custom — Full control: per-bot overrides, pool, skins/ folder.
#  mode: off    — No skin. All bots use Steve / Alex appearance.
skin:
  mode: auto
  guaranteed-skin: true   # Always apply a skin — never spawn as Steve
  fallback-name: Notch    # Real Minecraft account used as last-resort skin

  # custom mode only ↓
  overrides: {}           # bot-name: minecraft-username
  pool: []                # Random skin pool — list of Minecraft usernames
  use-skin-folder: true   # Scan plugins/FakePlayerPlugin/skins/ for PNGs
  clear-cache-on-reload: true

# ── Body ───────────────────────────────────────────────────────────────────
body:
  enabled: true   # false = tab-list/count only, no visible entity

# ── Persistence ────────────────────────────────────────────────────────────
persistence:
  enabled: true   # Bots rejoin at their last position on server restart

# ── Join / Leave Timing (ticks — 20 ticks = 1 second) ─────────────────────
join-delay:
  min: 0
  max: 5

leave-delay:
  min: 0
  max: 5

# ── Messages ───────────────────────────────────────────────────────────────
messages:
  join-message: true    # "<Name> joined the game"
  leave-message: true   # "<Name> left the game"
  kill-message: false   # Broadcast when a player kills a bot

# ── Combat ─────────────────────────────────────────────────────────────────
combat:
  max-health: 20.0   # Bot HP (20.0 = standard player health)
  hurt-sound: true   # Play player hurt sound on damage

# ── Death & Respawn ────────────────────────────────────────────────────────
death:
  respawn-on-death: false   # Respawn bot after death instead of removing it
  respawn-delay: 60         # Ticks before respawn (60 = 3 seconds)
  suppress-drops: true      # Prevent item drops on death

# ── Chunk Loading ──────────────────────────────────────────────────────────
chunk-loading:
  enabled: true
  radius: 6            # Chunk radius. 0 = match server simulation-distance.
  update-interval: 20  # Ticks between checks (20 = 1 s)

# ── Head AI ────────────────────────────────────────────────────────────────
head-ai:
  enabled: true
  look-range: 8.0   # Detection radius in blocks
  turn-speed: 0.3   # Smoothing (0.0 = frozen, 1.0 = instant snap)

# ── Collision & Push  (Advanced — defaults work for most servers) ──────────
collision:
  walk-radius: 0.85
  walk-strength: 0.22
  hit-strength: 0.45
  bot-radius: 0.90
  bot-strength: 0.14
  max-horizontal-speed: 0.30

# ── Bot Swap / Rotation ────────────────────────────────────────────────────
swap:
  enabled: false
  session-min: 120   # Min seconds online before swapping
  session-max: 600
  # Advanced ↓
  rejoin-delay-min: 5
  rejoin-delay-max: 45
  jitter: 30
  reconnect-chance: 0.15
  afk-kick-chance: 5
  farewell-chat: true
  greeting-chat: true
  time-of-day-bias: true

# ── Fake Chat ──────────────────────────────────────────────────────────────
fake-chat:
  enabled: false
  require-player-online: true
  chance: 0.75
  interval:
    min: 5
    max: 10

# ── Tab List Header / Footer ───────────────────────────────────────────────
#  Optional animated tab-list header and footer.
#  Placeholders: {bot_count}  {real_count}  {total_count}  {max_bots}
tab-list:
  enabled: false
  update-interval: 40   # Ticks between refreshes (40 = 2 s)
  header: "<dark_gray>━━━━━━━━━━\n<#0079FF><bold>ꜱᴇʀᴠᴇʀ ɴᴀᴍᴇ</#0079FF>\n<gray>Players: <white>{real_count} <dark_gray>+ <#0079FF>{bot_count} ʙᴏᴛꜱ\n<dark_gray>━━━━━━━━━━"
  footer: "<dark_gray>ᴘᴏᴡᴇʀᴇᴅ ʙʏ <#0079FF>ꜰᴀᴋᴇ ᴘʟᴀʏᴇʀ ᴘʟᴜɢɪɴ</#0079FF>"

# ── Database ───────────────────────────────────────────────────────────────
#  SQLite: zero-config — plugins/FakePlayerPlugin/data/fpp.db
#  MySQL:  multi-server setups — set mysql-enabled: true
database:
  mysql-enabled: false
  mysql:
    host: "localhost"
    port: 3306
    database: "fpp"
    username: "root"
    password: ""
    use-ssl: false
    pool-size: 5
    connection-timeout: 30000
  location-flush-interval: 30
  session-history:
    max-rows: 20
```

---

## ✦ Skin System

### Modes

| Mode | Description |
|---|---|
| `auto` *(default)* | Fetches skin from Mojang by bot name and injects texture data. With `guaranteed-skin: true`, bots whose names have no Mojang account receive the `fallback-name` skin instead of Steve. |
| `custom` | Full pipeline: per-bot override → skins/ folder → random pool → Mojang by name → guaranteed fallback. |
| `off` | No skin — bots use the default Steve/Alex appearance. |

### Guaranteed Skin

`skin.guaranteed-skin: true` *(enabled by default)* ensures **every bot always spawns with a skin**:

| Situation | Result |
|---|---|
| Bot name is a real Mojang account (e.g. `Notch`) | Mojang skin fetched and applied ✔ |
| Bot name is generated (`Bot1234`, `ubot_*`) | Fallback chain resolves a skin ✔ |
| Mojang rate-limited (HTTP 429) | Response not cached — next spawn retries; fallback applied immediately ✔ |
| Pool / folder empty, Mojang unavailable | `skin.fallback-name` fetched on demand ✔ |
| `skin.mode: off` | No skin regardless of this setting |

### Fallback Chain (when primary resolution fails)

1. **Folder skins** — PNG files in `plugins/FakePlayerPlugin/skins/` (`custom` mode)
2. **Pool skins** — names in `skin.pool` (`custom` mode)
3. **Pre-loaded fallback** — `skin.fallback-name` fetched and cached at startup
4. **On-demand fallback** — `skin.fallback-name` fetched live if startup prewarm hasn't completed yet

### Skin Folder (`custom` mode)

Drop standard Minecraft skin PNGs into `plugins/FakePlayerPlugin/skins/`:

| Filename | Behaviour |
|---|---|
| `<botname>.png` | Used exclusively for the bot with that exact name |
| `anything.png` | Added to the random pool for any bot without a specific match |

Run `/fpp reload` after adding or removing skin files.

---

## ✦ Chunk Loading

Bots keep surrounding chunks loaded **exactly like a real player**:

| Behaviour | Detail |
|---|---|
| **Spiral order** | Chunks ticketed closest-first, matching Paper's chunk-send priority queue |
| **Player-equivalent tickets** | `World.addPluginChunkTicket()` — mobs spawn, redstone ticks, crops grow |
| **Movement-delta detection** | Ticket set only recomputed when the bot crosses a chunk boundary — zero wasted work for stationary bots |
| **World-border clamping** | Chunks outside the world border automatically excluded |
| **Configurable radius** | `chunk-loading.radius` (default `6`). Set `0` to auto-match server simulation-distance |
| **Configurable interval** | `chunk-loading.update-interval` (default `20` ticks). Lower = more responsive to knockback |
| **Instant release** | Tickets released immediately on bot deletion, death, or plugin disable |

---

## ✦ Bot Swap / Rotation

When `swap.enabled: true`, bots automatically leave and rejoin with a fresh name after a configurable session length:

| Feature | Detail |
|---|---|
| **Personality archetypes** | VISITOR (short stay), REGULAR (normal), LURKER (long stay) |
| **Session growth** | Bots that have swapped many times gradually stay longer |
| **Time-of-day bias** | Longer sessions during peak hours, shorter overnight |
| **Farewell & greeting chat** | Optional messages before leaving / after rejoining |
| **Reconnect simulation** | Configurable chance the bot rejoins with the same name |
| **AFK-kick simulation** | Small chance of an extended rejoin gap |

---

## ✦ Bot Names & Messages

| File | Purpose |
|---|---|
| `bot-names.yml` | Pool of names randomly assigned to bots. Names must be 1–16 characters, letters/digits/underscore only. Edit freely and run `/fpp reload`. |
| `bot-messages.yml` | Pool of chat messages bots randomly send. Supports `{name}` and `{random_player}` placeholders. |

When the name pool is exhausted, FPP generates names automatically (`Bot1234`).

---

## ✦ Bot Display Names

Fully configurable in `config.yml`:

- **Admin bots** — `bot-name.admin-format` with `{bot_name}`
- **User bots** — `bot-name.user-format` with `{spawner}` and `{num}`
- **LuckPerms prefix** — when `luckperms.use-prefix: true`, the default-group prefix is prepended automatically

| Config value | In-game result |
|---|---|
| `{bot_name}` | `Steve` |
| `<#0079FF>[bot-{bot_name}]</#0079FF>` | `[bot-Steve]` in blue |
| `<gray>[bot-{spawner}-{num}]</gray>` | `[bot-El_Pepes-1]` in gray |
| LuckPerms `§7` prefix + `{bot_name}` | `§7Steve` |

---

## ✦ Database

FPP records every bot session for auditing and analytics:

| Field | Description |
|---|---|
| `bot_name` | Internal Minecraft name |
| `bot_uuid` | UUID assigned to the bot |
| `spawned_by` | Player who ran `/fpp spawn` |
| `world_name` | World where the bot spawned |
| `spawn_x/Y/Z` | Spawn coordinates |
| `last_x/Y/Z` | Last known position (flushed every `location-flush-interval` seconds) |
| `spawned_at` | Spawn timestamp |
| `removed_at` | Removal timestamp |
| `remove_reason` | `DELETED`, `DIED`, `SHUTDOWN`, or `SWAP` |

Use `/fpp info` to query in-game. Backends: **SQLite** (default, zero-config) or **MySQL** (`database.mysql-enabled: true`).

---

## ✦ Language

All player-facing text lives in `plugins/FakePlayerPlugin/language/en.yml`.  
Edit and run `/fpp reload` — no restart required.  
Colors use **MiniMessage**: `<#0079FF>text</#0079FF>`, `<gray>`, `<bold>`, `<reset>`, etc.

| Section | Notes |
|---|---|
| **INTERNAL LAYOUT STRINGS** | `divider`, `help-entry`, `info-screen-header`, etc. — safe to restyle, do not rename |
| **PLAYER-FACING MESSAGES** | All errors, feedback, broadcasts — safe to edit freely |

---

## ✦ LuckPerms Integration

FPP auto-detects LuckPerms at startup. When installed and `luckperms.use-prefix: true`:

- The **default group's prefix** is prepended to every bot display name in the tab list, nametag, and join/leave messages
- Makes bots blend naturally with real players who share the same prefix
- Disable with `luckperms.use-prefix: false` to use only the `bot-name.*` format colours

---

## ✦ Changelog



### v1.4.20 *(2026-03-20)*
#### Release
- Compatibility checks: detect non-Paper servers and Minecraft versions below 1.21.9 and enter a restricted compatibility mode when needed (physical bodies and chunk-loading disabled).
- Added runtime guard for missing server API classes (prevents NoClassDefFoundError when the Mannequin class is absent).
- Admin-facing in-game compatibility warning (configurable) sent on enable and on admin join.
- Teleport commands updated: `/fpp tp` and `/fpp tph` now report when no physical body is available; `/fpp info` shows "No Body" when bodies are disabled.
- Command rename: `/fpp delete` renamed to `/fpp despawn` (permission node `fpp.delete` retained for compatibility).

### v1.3.0 *(2026-03-15)*

#### Changes
- Config reload now updates all config files, including language, bot-names, and bot-messages.
- Updated plugin info and documentation for Modrinth link and version 1.3.0.
- Removed `/fpp setpos` command and all references.

---

### v1.2.7 *(2026-03-14)*

#### New Commands
- **`/fpp freeze <bot|all> [on|off]`** *(new, `fpp.freeze`)* — freeze any bot in place; the Mannequin body becomes immovable and gravity is disabled so it hovers. Toggle, set explicitly, or freeze all at once. Frozen bots are shown with an ❄ indicator in `/fpp list` and `/fpp stats`
- **`/fpp stats`** *(new, `fpp.stats`)* — rich live statistics panel: active / frozen bot count with uptime breakdown, system status (chat, swap, chunk-load, skin mode), database lifetime totals, and server health (TPS, online players)

#### PlaceholderAPI Integration
- FPP now registers a **PlaceholderAPI expansion** automatically when PAPI is installed (soft-dependency — no restart needed if PAPI is added later via `/papi reload`)
- Available placeholders:

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

#### Config
- `config-version` **12 → 14**
- **`metrics.enabled`** *(new)* — opt-out toggle for anonymous FastStats usage statistics
- **`spawn-cooldown`** *(new)* — per-player spawn cooldown in seconds (`0` = no cooldown); admins with `fpp.bypass.cooldown` are always exempt
- **`tab-list`** *(new)* — optional animated tab-list header and footer; supports `{bot_count}`, `{real_count}`, `{total_count}`, `{max_bots}` placeholders and full MiniMessage colouring; disabled by default

#### New Permissions
- `fpp.freeze` — freeze / unfreeze bots (default: `op`)
- `fpp.stats` — view the `/fpp stats` panel (default: `op`)
- `fpp.bypass.cooldown` — bypass per-player spawn cooldown (default: `op`)

#### Bug Fixes & Internals
- Automatic config migration handles the jump from any previous version; a timestamped backup is created before any changes are written

---

### v1.2.2 *(2026-03-14)*

#### Skin System — Guaranteed Skin & Rate-Limit Fix
- **`skin.guaranteed-skin: true`** *(new, default on)* — bots always spawn with a real skin. When the primary lookup fails (generated name, user bot, network error), the system falls through: folder skins → pool skins → pre-loaded `fallback-name` → on-demand fetch. Steve/Alex appearance is only possible with `skin.mode: off`
- **`skin.fallback-name: Notch`** *(new)* — a real Mojang username pre-fetched at startup as last-resort skin; change to any valid account
- **Rate-limit fix** — Mojang HTTP 429 responses are no longer cached as `null`; the next spawn retries the fetch instead of permanently getting Steve
- **Request gap** — increased from 200 ms → **800 ms** between Mojang API calls, safely under the ~1 req/s limit and dramatically reducing 429s during bulk spawns
- **Guaranteed fallback in respawn path** — `resolveAutoAndApply` (bot respawn and entity validation) also uses the guaranteed fallback chain for consistency
- **`SkinRepository.getAnyValidSkin()`** *(new)* — priority-ordered fallback callable from any skin resolution path

#### Head AI
- **`head-ai.enabled`** *(new)* — explicit boolean to enable/disable head rotation; set `false` instead of setting `look-range: 0`
- `BotHeadAI` now exits the tick loop immediately when disabled, eliminating pointless per-tick bot iteration

#### Config
- `config-version` **11 → 12**
- Config fully restructured: inline comments on every key, collision marked "Advanced", swap advanced options grouped, cleaner overall layout
- Automatic migration: existing configs receive new keys on first startup with a backup created first

#### Bug Fixes
- **ConfigMigrator NPE fixed** — migration runs before `Config.init()` so `Config.cfg` is null; the migrator now reads the `debug` flag directly from the raw YAML instead of calling `Config.isDebug()`, preventing a startup crash when upgrading from any previous version

#### Plugin Internals
- Redundant tab-list send removed — `finishSpawn` sent the ADD packet 3 times; now sends exactly 2 (immediate + 5-tick TAB-plugin re-send)
- Startup banner skin line shows guaranteed-skin status and fallback name: `auto (guaranteed → Notch)`
- Removed unused `getActiveNameSet()` method and stale `@SuppressWarnings` annotations

---

### v1.1.4 *(2026-03-12)*

#### Chunk Loading — Complete Rewrite
- Spiral ticket order, movement-delta detection, world-border clamping, `update-interval` config, auto-radius, instant release on removal

#### FakePlayer Model
- `getLiveLocation()`, `getUptime()` / `getUptimeFormatted()`, session stats (`totalDamageTaken`, `deathCount`, `isAlive()`), chunk tracking helpers

#### Config & DB
- DB location flush uses `getLiveLocation()` — handles body-less bots correctly
- `Config.chunkLoadingUpdateInterval()` accessor added

---

### v1.0.15 *(2026-03-11)*
- Join/leave messages broadcast correctly to all players
- LuckPerms prefix pipeline fixed — no more legacy `§`-code parse failures
- Config cleaned up — added `luckperms.use-prefix` toggle
- Modrinth link in `/fpp` info screen (clickable)

### v1.0.0-rc1 *(2026-03-08)*
- First stable release candidate
- Full permission system: `fpp.user.*`, `fpp.bot.<num>` limit nodes, LuckPerms display name support
- User-tier commands: `/fpp spawn`, `/fpp tph`, `/fpp info` (own bots only)
- Bot persistence across server restarts
- O(1) entity lookup via entity-id index

### v0.1.5
- Bot swap / rotation with personality archetypes, time-of-day bias, AFK-kick simulation
- MySQL + SQLite database backend
- `/fpp info` database query command
- `bot-messages.yml` fake-chat message pool (1 000 messages)
- Staggered join/leave delays

### v0.1.0
- Initial release: tab list, join/leave messages, Mannequin body, head AI, collision/push system

---

## ✦ License

© 2026 Bill_Hub — All Rights Reserved.  
See [LICENSE](https://github.com/Pepe-tf/Fake-Player-Plugin-Public-/blob/main/LICENSE) for full terms.  
Contact: [Discord](https://discord.gg/ZhsstSJb) — `Bill_Hub`

---

*Built for Paper 1.21.x · Java 21 · FPP v1.4.20 · [Modrinth](https://modrinth.com/plugin/fake-player-plugin-(fpp)) - [Spigotmc](https://www.spigotmc.org/resources/fake-player-plugin-fpp.133572/) - [Papermc](https://hangar.papermc.io/Pepe-tf/FakePlayerPlugin)*
