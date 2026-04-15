# 📋 Changelog

> **Full version history for Fake Player Plugin**  
> Latest version: **v1.6.4** · Released: 2026-04-16 · Config version: **60**  
> 🎉 **Now Open Source** — [https://github.com/Pepe-tf/fake-player-plugin](https://github.com/Pepe-tf/fake-player-plugin)

---

## v1.6.4 *(2026-04-16)*

### 🏷️ NameTag Plugin Integration
- New **soft-dependency** on the [NameTag](https://lode.gg) plugin — fully optional, auto-detected at startup
- **Nick-conflict guard** — prevents spawning a bot whose `--name` matches a real player's current NameTag nickname (`nametag-integration.block-nick-conflicts: true`)
- **Bot isolation** — after each bot spawns, FPP removes it from NameTag's internal player cache to prevent NameTag from treating bots as real players (`nametag-integration.bot-isolation: true`)
- **Sync-nick-as-rename** — when a bot has a NameTag nick set (e.g. via `/nick BotA Steve`), FPP auto-triggers a full rename so the bot's actual MC name becomes the nick (`nametag-integration.sync-nick-as-rename: false` — opt-in)
- **NameTag skin sync** — bots inherit skins assigned via NameTag; `SkinManager.getPreferredSkin()` checks NameTag-assigned skins first
- New `NameTagHelper` utility class: nick reading, skin reading, cache isolation, formatting strip, nick-conflict checks
- New `FakePlayer.nameTagNick` field tracks the cached nick from NameTag
- New lang key `spawn-name-taken-nick` shown when a bot name conflicts with a real player's nick

### 🎨 Skin System Overhaul
- New `SkinManager` class — centralised skin lifecycle: resolve, apply, cache, fallback, NameTag priority
- **Hardcoded 1000-player fallback skin pool** — replaces the old `skin.fallback-pool` and `skin.fallback-name` config keys; bots with non-Mojang names always get a real-looking skin from the built-in pool
- **DB skin cache** — new `fpp_skin_cache` table with 7-day TTL and auto-cleanup; resolved skins cached to database to avoid repeated Mojang API lookups
- `skin.mode` default enforced as `player` for existing installs that had it disabled (v58→v59 migration)
- `guaranteed-skin` default enforced as `true` for existing installs (v58→v59 migration)
- `skin.fallback-pool` and `skin.fallback-name` config keys removed — now hardcoded in SkinManager (v59→v60 migration)
- Exposed via `plugin.getSkinManager()` — public API: `resolveEffectiveSkin`, `applySkinByPlayerName`, `applySkinFromProfile`, `applyNameTagSkin`, `resetToDefaultSkin`, `preloadSkin`, `clearCache`

### 🏊 Per-Bot Swim AI & Chunk Load Radius
- Each bot now has an individual **swim AI toggle** — override the global `swim-ai.enabled` per-bot without restarting
- Each bot now has an individual **chunk load radius** — `-1` = follow global `chunk-loading.radius`, `0` = disable chunk loading for this bot, `1-N` = fixed radius (capped at global max)
- Both fields are initialised from the global config at spawn, fully persisted across restarts (DB column + YAML key), and editable at runtime via `BotSettingGui` or programmatically

### ⚙️ BotSettingGui General Tab Expanded
- General tab now has **7 action slots**: Frozen · Head-AI · Swim-AI *(new)* · Chunk-Load-Radius *(new, numeric prompt)* · Pick-Up-Items · Pick-Up-XP · Rename
- Chunk-load-radius uses a chat-input numeric prompt (same interaction model as `/fpp settings` numeric fields); type a number or `-1` to reset to global

### ⚔ BotSettingGui PvP Tab
- PvP category now shows full coming-soon override previews: difficulty, combat-mode, critting, s-tapping, strafing, shielding, speed-buffs, jump-reset, random, gear, defensive-mode

### 💾 DB Schema v14 → v15
- v14: `fpp_active_bots` gains two new columns: `swim_ai_enabled BOOLEAN DEFAULT 1`, `chunk_load_radius INT DEFAULT -1`
- v15: new `fpp_skin_cache` table (`skin_name`, `texture_value`, `texture_signature`, `source`, `cached_at`) with expiry index
- `updateBotAllSettings` and `ActiveBotRow` extended with `swimAiEnabled` and `chunkLoadRadius`
- Fully backward-compatible — existing rows receive safe defaults on schema upgrade

### 📋 Config v53 → v60
- v53→v54: `body.drop-items-on-despawn: false` injected into existing installs (preserves pre-1.6.2 behaviour; new installs default `true`)
- v54→v55: shared global pathfinding tuning keys added (`pathfinding.arrival-distance`, `patrol-arrival-distance`, `waypoint-arrival-distance`, `sprint-distance`, `follow-recalc-distance`, `recalc-interval`, `stuck-ticks`, `stuck-threshold`, `break-ticks`, `place-ticks`, `max-range`, `max-nodes`, `max-nodes-extended`)
- v55→v56: `nametag-integration` section added (`block-nick-conflicts`, `bot-isolation`)
- v56→v57: `nametag-integration.sync-nick-as-rename` added
- v57→v58: (no-op placeholder)
- v58→v59: `skin.mode=player`, `guaranteed-skin=true`, `logging.debug.skin=true` enforced for existing installs
- v59→v60: removed `skin.fallback-pool` and `skin.fallback-name` (hardcoded in SkinManager's 1000-player pool)

---

## v1.6.3 *(2026-04-14)*

### 🛡️ Despawn Safety Guard
- `despawn all`, `despawn --random <n>`, and `despawn --num <n>` are now blocked while `FakePlayerManager.isRestorationInProgress()` is true at startup
- Prevents startup-queued console commands from killing bots mid-restore during the ~2–3 second persistence restoration window
- New lang key `delete-restore-in-progress` shown to sender when a bulk despawn is attempted during the restore window
- Single-bot despawn (`/fpp despawn <name>`) is **not** affected — only bulk operations

### 🗺️ Waypoint Auto-Create
- `/fpp wp add <route>` now **auto-creates** the route if it doesn't exist — no separate `/fpp wp create <route>` step required
- When a new route is implicitly created, an in-chat tip is shown via the new `wp-route-auto-created` lang key
- `/fpp wp create` still exists and is valid, but is now optional
- `wp-usage` updated so `add` leads the usage string and `create` is shown as optional
- `wp-list-empty` hint updated to point directly to `/fpp wp add <route>` instead of the two-step create+add flow

---

## v1.6.2 *(2026-04-12)*

### 🤖 AI Conversations
- New AI DM system — bots respond to `/msg`, `/tell`, `/whisper` with AI-generated replies that match their personality
- 7 provider support: **OpenAI · Anthropic · Groq · Google Gemini · Ollama · Copilot/Azure · Custom OpenAI-compatible**
- API keys stored in `plugins/FakePlayerPlugin/secrets.yml` (never in `config.yml`) — template extracted from JAR on first run
- Per-bot personality assignment via `/fpp personality <bot> set <name>`; personalities stored as `.txt` files in `personalities/` folder
- Bundled sample personalities: `friendly`, `grumpy`, `noob`
- `BotConversationManager` — per-player conversation history, rate limiting, typing delay simulation
- `BotMessageListener` auto-registered when `ai-conversations.enabled` and a provider API key is present
- `AIProviderRegistry` picks the first provider with a non-blank key; `isAvailable()` for runtime checks

### 🆕 New Commands
- `/fpp place <bot> [once|stop]` — continuous or one-shot block placing at the bot's look target; bot stays locked at position. Permission: `fpp.place`
- `/fpp storage <bot> [name|--list|--remove <name>|--clear]` — register named supply containers; used by `/fpp mine` and `/fpp place` for automatic restocking. Permission: `fpp.storage`
- `/fpp use <bot>` — bot right-clicks / activates the block it's looking at (chests, buttons, levers, crafting tables, etc.). Permission: `fpp.useitem`
- `/fpp waypoint <name> [add|remove|list|clear]` — manage named patrol waypoint routes; bots walk them on a loop via `/fpp move <bot> --wp <route>`. Permission: `fpp.waypoint`
- `/fpp personality [list|reload|<bot> set <name>|reset|show]` (alias `persona`) — assign AI personalities to bots; persisted to DB. Permission: `fpp.personality`
- `/fpp badword add|remove|list|reload` — manage the runtime badword filter word list. Permission: `fpp.badword`
- `/fpp rename <old> <new>` — rename any active bot with **full state preservation**: inventory (deep-cloned), XP, LP group, AI personality, right-click command, frozen state, tasks. Permission: `fpp.rename` (any) / `fpp.rename.own` (own only). `fpp.rename` is parent of `fpp.rename.own` in `plugin.yml`

### ⛏️ Area Mining Mode
- `/fpp mine <bot> --pos1` / `--pos2` — select a cuboid mining region using the bot's current position
- `/fpp mine <bot> --start` — begin continuously mining the selected cuboid; navigates to each block using `PathfindingService`
- `/fpp mine <bot> --status` — show current area-mine job progress
- `/fpp mine <bot> --stop` — cancel the area-mine job
- Auto-restocks from the nearest registered `StorageStore` container when inventory fills
- Selections persisted to `data/mine-selections.yml` — survive restarts and auto-resume after reboot

### ⚙️ Per-Bot Settings GUI (`BotSettingGui`)
- Shift+right-click any bot entity to open a **6-row chest GUI** with 5 categories:
  - ⚙ **General** — frozen toggle, look-at-player toggle, rename action
  - 💬 **Chat** — chat enabled/disabled, activity tier, AI personality selector
  - ⚔ **PvP** — PvP AI settings (coming soon)
  - 📋 **Cmds** — set/clear stored right-click command
  - ⚠ **Danger** — delete bot with confirmation
- Controlled by `bot-interaction.shift-right-click-settings` config key

### 💾 Task Persistence (DB Schema v13)
- Active tasks (mine/use/place/patrol) now saved to `fpp_bot_tasks` DB table on shutdown
- YAML fallback: `data/bot-tasks.yml` when database is disabled
- `clearBotTasks()` called immediately after load to prevent double-restore on next restart
- `BotPersistence` injection points: `setMineCommand`, `setPlaceCommand`, `setUseCommand`, `setWaypointStore`
- Task columns: `bot_uuid`, `server_id`, `task_type` (MINE/USE/PLACE/PATROL), world, pos, once_flag, extra_str (patrol route), extra_bool (patrol random)

### 🧭 Navigation & Interaction Engine
- `PathfindingService` — centralised shared navigation service; all nav loops previously duplicated across `MoveCommand`, `MineCommand`, `PlaceCommand`, `UseCommand` now delegated here
- `NavigationRequest` — `lockOnArrival` field for atomic nav→action lock handoff (eliminates one-tick gap between navigation arrival and action-lock acquisition)
- `BotNavUtil` — static utilities: `findStandLocation` (16-candidate walkable-adjacent search), `faceToward`, `isAtActionLocation` (XZ ≤ 0.35 proximity), `useStorageBlock`
- `StorageInteractionHelper` — shared lock→open-container→transfer→unlock lifecycle for deposit (mine→storage) and fetch (storage→place) operations; all error paths call `onFinally` so callers can clean up gating flags

### 🎒 Per-Bot Item & XP Pickup Toggles
- `body.pick-up-items` global default (`true`) and `body.pick-up-xp` global default (`true`)
- Per-bot overrides exposed in `BotSettingGui` — toggling off **immediately drops current inventory / XP to ground** (no need to despawn)
- `BotXpPickupListener` gates both `PlayerPickupExperienceEvent` and `PlayerExpChangeEvent` per-bot

### 📋 Config v47 → v53
- v47→v48: Added `pathfinding` section
- v48→v49: Added `body.pick-up-xp`
- v49→v50: Added `pvp-ai` section tweaks
- v50→v51: Finalized XP cooldown and cmd storage keys
- v51→v52: Added `bot-interaction`, `badword-filter` sections
- v52→v53: Added `ai-conversations` section; config reorganized into **10 clearly numbered sections**: Spawning · Appearance · Body & Combat · AI & Navigation · Bot Chat · AI Conversations · Scheduling · Database & Network · Performance · Debug & Logging

---

## v1.6.0 *(2026-04-09)*

### 🖥️ Interactive Help GUI
- `/fpp help` now opens a **54-slot double-chest GUI** - paginated, permission-filtered, click-navigable; replaces text output
- Each command gets a semantically meaningful Material icon (compass → move, chest → inventory, diamond pickaxe → mine, etc.)
- Displays command name, description, usage modes, and permission node per item; up to 45 commands per page

### 📦 `/fpp inventory` *(new)*
- 54-slot double-chest GUI showing the bot's full inventory - main storage (rows 1-3), hotbar (row 4), label bar (row 5), and equipment + offhand (row 6)
- Equipment slots enforce type restrictions (boots/leggings/chestplate or elytra/helmet/offhand)
- Right-click any bot entity to open without a command
- Permission: `fpp.inventory`

### 🧭 `/fpp move` *(new)*
- Navigate a bot to an online player using server-side **A* pathfinding**
- Supports WALK, ASCEND, DESCEND, PARKOUR, BREAK, PLACE move types; max 64-block range, 2000-node search
- Stuck detection (8 ticks without movement) triggers jump + path recalculation; recalculates when target moves >3.5 blocks or every 60 ticks
- New `pathfinding.*` config section: `parkour` (default `false`), `break-blocks` (default `false`), `place-blocks` (default `false`), `place-material` (default `"DIRT"`)
- Permission: `fpp.move`

### ⭐ `/fpp xp` *(new)*
- Transfer the bot's entire XP pool to yourself; clears bot levels and progress
- 30-second post-collection cooldown on bot XP pickup; `body.pick-up-xp` config flag gates orb pickup globally
- Permission: `fpp.xp` (user-tier, included in `fpp.use`)

### 💻 `/fpp cmd` *(new)*
- `/fpp cmd <bot> <command>` - dispatch a command as the bot
- `--add <command>` stores a right-click command on the bot; `--clear` removes it; `--show` displays it
- Right-clicking a bot with a stored command runs it instead of opening the inventory GUI
- Permission: `fpp.cmd`

### ⛏️ `/fpp mine` *(new)*
- `/fpp mine <bot>` - continuous block mining at the bot's look target
- `once` breaks a single block; `stop` cancels mining; `/fpp mine stop` stops all mining bots
- Creative mode = instant break with 5-tick cooldown; survival = progressive mining with `destroyBlockProgress` packets
- Permission: `fpp.mine`

### ⚙️ Settings GUI Expanded
- Settings GUI now has **7 categories**: General, Body, Chat, Swap, Peak Hours, PvP, Pathfinding (up from 5)
- New pathfinding toggles: parkour, break-blocks, place-blocks, place-material
- New PvP AI settings: difficulty, defensive-mode, detect-range

### 🛡️ WorldGuard Integration
- Bots protected from player-sourced PvP damage inside WorldGuard no-PvP regions
- Soft-depend: auto-detected, fully optional; uses ClassLoader guard identical to LuckPerms
- `WorldGuardHelper.isPvpAllowed(location)` - fail-open: only explicit DENY regions block bot damage

### 🔧 Config Migration v47 → v51
- v47→v48: Added `pathfinding` section
- v48→v49: Added `body.pick-up-xp`
- v49→v50: Added `pvp-ai` section tweaks
- v50→v51: Finalized XP cooldown and cmd storage keys

---

## v1.5.17 *(2026-04-07)*

### 🔄 Swap System - Critical Fix & Major Enhancements
- **Critical bug fix:** bots now actually rejoin after swapping out. The rejoin timer was being silently cancelled by `delete()` calling `cancel(uuid)` - bots left but never came back. Fixed by registering the rejoin task *after* `delete()` runs so `cancel()` finds nothing to cancel.
- New `swap.min-online: 0` - minimum bots that must stay online; swap skips if removing one would go below this floor
- New `swap.retry-rejoin: true` / `swap.retry-delay: 60` - auto-retry failed rejoins (e.g. when max-bots cap is temporarily full)
- Better bot identification on rejoin: same-name rejoins use `getByName()` (reliable even with stable UUIDs); random-name rejoins use UUID diff
- New `Personality.SPORADIC` type - unpredictable session variance for more natural patterns
- Expanded farewell/greeting message pools (~50 entries each)
- New `/fpp swap info <bot>` - shows personality, cycle count, time until next leave, and offline-waiting count
- `/fpp swap list` now shows **time remaining** in each session
- `/fpp swap status` now shows the `min-online` floor setting
- New `logging.debug.swap: false` - dedicated swap lifecycle debug channel

### ⚡ Performance Optimizations
- O(1) bot name lookup via secondary `nameIndex` map - `getByName()` was O(n) linear scan, now O(1) `ConcurrentHashMap` lookup
- Position sync distance culling - position packets only broadcast to players within `performance.position-sync-distance: 128.0` blocks (0 = unlimited)

### 🔕 Log Cleanup
- NmsPlayerSpawner per-spawn/despawn log messages demoted from INFO → DEBUG; no more log spam on every bot cycle

### 📋 Config Reorganization
- `config.yml` restructured into 9 clearly labelled sections: Spawning · Appearance · Body & Combat · AI Systems · Bot Chat · Scheduling · Database & Network · Performance · Debug & Logging
- Config version → **v47**

---

## v1.5.15 *(2026-04-06)*

### 📝 Config Clarity Improvements
- All timing-related values in `config.yml` now clearly state their unit (ticks or seconds) with human-readable conversion examples
- `join-delay` / `leave-delay` section header updated to *"Values are in TICKS - 20 ticks = 1 second"* with a quick-reference line; `min`/`max` keys now carry inline tick-unit comments
- `death.respawn-delay` comment now shows seconds equivalents: `15 = 0.75 s · 60 = 3 s · 100 = 5 s`
- `chunk-loading.update-interval` comment clarified: *"in ticks (20 ticks = 1 second). Lower = more responsive, higher = less overhead."*
- `swap.session` / `swap.absence` inline comments updated with real-world time examples (e.g. `60 = 1 min`, `300 = 5 min`)

### 🔧 Build Pipeline Fixes
- ProGuard: removed `**.yml` from `-adaptresourcefilecontents` - prevents charset corruption of `plugin.yml` and language files on Windows builds
- ProGuard: removed `-dontpreverify` - `StackMapTable` attributes preserved; obfuscated jar passes JVM verifier without `VerifyError`
- ProGuard: MySQL / SQLite shaded classes excluded from preverification to prevent `IncompleteClassHierarchyException`; merged back verbatim into final jar

---

## v1.5.12 *(2026-04-05)*

### 🔒 Stable Bot UUID Identity
- `BotIdentityCache` - each bot name is permanently tied to a stable UUID; LuckPerms data, inventory, and session history persist across restarts
- Storage: in-memory cache → `fpp_bot_identities` DB table → `data/bot-identities.yml` YAML fallback

### ⚙️ In-Game Settings GUI
- `/fpp settings` opens a 3-row chest GUI; 5 categories (General, Body, Chat, Swap, Peak Hours)
- Toggle booleans instantly; numeric values via chat-input prompt; reset page to JAR defaults; all changes apply live
- Permission: `fpp.settings`

### ⏰ Peak Hours Scheduler
- `PeakHoursManager` scales the bot pool by time-of-day windows (`peak-hours.schedule`, `day-overrides`, `stagger-seconds`)
- Crash-safe: sleeping-bot state persisted in `fpp_sleeping_bots` DB table, restored at startup
- New command: `/fpp peaks [on|off|status|next|force|list|wake <name>|sleep <name>]` - requires `swap.enabled: true`

### 💬 Per-Bot Chat Control
- Random activity tier per bot: quiet / passive / normal / active / chatty
- `/fpp chat <bot> tier|mute|info` per-bot controls; `/fpp chat all <on|off|tier|mute>` for bulk operations
- Event-triggered chat (`event-triggers.*`) and keyword reactions (`keyword-reactions.*`)

### 👻 Bodyless Bot Mode & Bot Types
- `bodyless` flag - bots without a world location exist in tab-list/chat only, no world entity
- `BotType`: `AFK` (passive) and `PVP` (combat via `BotPvpAI`)

### 🔧 Config Migration v41 → v44
- v41→v42: Added `peak-hours` section · v42→v43: Added `min-online`, `notify-transitions` · v43→v44: Removed `auto-enable-swap`

---

## v1.5.10 *(2026-04-05)*

### 🔄 `/fpp swap` Toggle Fix
- Running `/fpp swap` with no arguments now toggles swap on/off - exactly like `/fpp chat`
- `swap-enabled` and `swap-disabled` messages redesigned to match the chat toggle style (`session rotation has been enabled/disabled`)
- `swap-status-on` / `swap-status-off` now follow the same `is enabled / is disabled` pattern as chat status messages

### 💬 Bot Chat Interval Fix
- Bot chat loops are now restarted on `/fpp reload` so changes to `fake-chat.interval.min/max`, `fake-chat.chance`, and `fake-chat.stagger-interval` take effect **immediately** instead of waiting for each bot's old queued task to naturally expire
- `/fpp reload` output now shows the new interval range as confirmation

### 🤖 Fake Chat Realism Enhancements
- **`typing-delay`** - simulates a 0-2.5 s typing pause before each message is sent
- **`burst-chance` / `burst-delay`** - bots occasionally send a quick follow-up message a few seconds later
- **`reply-to-mentions` / `mention-reply-chance` / `reply-delay`** - bots can reply when a real player says their name in chat
- **`activity-variation`** - each bot gets a random chat-frequency tier (quiet / normal / active / very-active)
- **`history-size`** - bots remember their own recent messages and avoid repeating them
- **`remote-format`** - MiniMessage format for bodyless / proxy-remote bot broadcasts (`{name}` and `{message}` placeholders)

### 🏊 Swim AI
- New `swim-ai.enabled` config key (default `true`) - bots automatically swim upward when submerged in water or lava, mimicking a real player holding the spacebar
- Set to `false` to let bots sink or drown instead

### 🛠️ Language & Compatibility
- `Biome.name()` deprecated call replaced with `Biome.getKey().getKey()` - compatible with Paper 1.22+
- `sync-usage` and `swap-now-usage` messages now end with a period, matching the rest of the file
- Startup banner now shows **Bot swap** state in the Features section alongside Fake chat
- Startup banner now shows actual **Skin mode** (`auto`/`custom`/`off`) instead of the hardcoded `disabled`

---

## v1.5.8 *(2026-04-03)*

### 🔧 Ghost Player / "Anonymous User" Fix
- Replaced reflection-based `Connection` injection with a proper `FakeConnection` subclass whose `send()` methods are clean no-op overrides
- Eliminated the phantom **"Anonymous User"** entry with UUID `0` that appeared in the tab list when bots connected
- Eliminated `NullPointerException` and `ClassCastException` spam in server logs related to bot connections

### 📊 `%fpp_real%` / `%fpp_total%` Accuracy Fix
- `%fpp_real%` now correctly subtracts bot count from `Bukkit.getOnlinePlayers()` - bots go through `placeNewPlayer()` and appear in the online list
- `%fpp_real_<world>%` similarly now excludes bots from per-world real-player counts
- `%fpp_total%` fixed to avoid double-counting; accurately reports real players + local bots (+ remote bots in NETWORK mode)

### 🌐 Proxy `/fpp list` Improvements (NETWORK mode)
- `/fpp list` now shows a `[server-id]` tag next to each local bot so admins can identify which server they belong to
- Remote bots from other proxy servers are now listed in a dedicated "Remote bots" section showing their server, name, and skin status
- Total counts include both local and remote bots

### 🆕 New Proxy Placeholders
| Placeholder | Description |
|-------------|-------------|
| `%fpp_local_count%` | Bots on this server only |
| `%fpp_network_count%` | Bots on other proxy servers (NETWORK mode) |
| `%fpp_network_names%` | Comma-separated display names from remote servers |
- `%fpp_count%` and `%fpp_names%` now include remote bots in NETWORK mode

### 🔐 LuckPerms ClassLoader Guard
- Fixed `NoClassDefFoundError: net/luckperms/api/node/Node` crash on servers without LuckPerms installed
- All LP-dependent code is now properly gated behind `LuckPermsHelper.isAvailable()` checks; no LP classes are loaded unless LP is present

### ⚙️ Config Migration
- Config version bumped to `37` (no structural key changes - version stamp only)
- Automatic migration on first startup from any previous version

---

## v1.5.6 *(2026-04-03)*

### ⚔️ Knockback Fix (1.21.9-1.21.11)
- Bots now correctly receive knockback on 1.21.9+ servers
- Tiered strategy system auto-detects the correct MC version API at startup (zero reflection overhead per hit)
- `GET_MOVEMENT` (1.21.9+): uses `packet.getMovement()` → `Vec3` → `player.lerpMotion(Vec3)`
- `GET_XA` (≤1.21.8): uses `packet.getXa/Ya/Za()` → `lerpMotion(double,double,double)` or `setDeltaMovement(Vec3)` fallback

### 💥 Double-Disconnect Crash Fix (Paper 1.21+)
- Fixed `IllegalStateException: Already retired` spam when bots are slain
- `injectPacketListenerIntoConnection()` now updates both `ServerPlayer.connection` AND `Connection.packetListener` fields
- Ensures our `onDisconnect` override handles double-retirement gracefully

### 🛡️ Bot Protection System
- **Command blocking** - bots can no longer execute commands from ANY source (4-layer protection)
- **Lobby spawn fix** - 5-tick grace period prevents lobby plugins from teleporting bots at spawn
- New listeners: `BotCommandBlocker` and `BotSpawnProtectionListener`

---

## v1.5.4 *(2026-04-03)*

### 📊 PlaceholderAPI Expansion
- 26+ placeholders across 5 categories (up from 18+)
- Fixed `%fpp_skin%` incorrectly returning `"disabled"` instead of the actual mode
- Added `%fpp_persistence%` placeholder (`on`/`off` for `persistence.enabled`)
- New Network/Proxy category: `%fpp_network%`, `%fpp_server_id%`, `%fpp_spawn_cooldown%`

### 🎨 Skin System Simplified
- Removed `skin.fallback-pool` and `fallback-name` (eliminates API rate-limiting)
- Changed `guaranteed-skin` default from `true` → `false`
- Bots with non-Mojang names now use Steve/Alex skins by default
- Config section reduced from ~60 lines to ~18 lines

### ⚙️ Config Migration v35 → v36
- Auto-cleanup of orphaned LuckPerms keys (`weight-offset`, `use-prefix`, etc.)
- Removes old `skin.custom` section and `server:` section
- Automatic backup created before migration runs

### ✨ New Features
- `/fpp` info screen includes Discord support link
- Full support for Leaf server (Paper fork)
- Config version bumped to `36`; fully backward compatible

---

## v1.5.0 *(2026-03-31)*

### 🌐 Proxy / Network Mode
- Full **Velocity & BungeeCord** support with `NETWORK` database mode
- Cross-server chat, alerts, bot join/leave broadcasts, and remote bot tab-list sync via `fpp:main` plugin-messaging channel
- Remote bot cache - thread-safe registry of bots on other proxy servers, populated from DB at startup

### 🔄 Config Sync
- `/fpp sync push/pull/status/check` commands
- Modes: `DISABLED`, `MANUAL`, `AUTO_PULL`, `AUTO_PUSH`
- Syncs `config.yml`, `bot-names.yml`, `bot-messages.yml`, `language/en.yml`
- Server-specific keys (`database.server-id`, `database.mysql.*`, `debug`) are **never** uploaded

### 🏷️ BotTabTeam
- Scoreboard team `~fpp` places all bots **below** real players in the tab list regardless of LP group weight

### 🎖️ Per-Bot LuckPerms Groups
- `/fpp rank <bot> <group>` - change a bot's LP group at runtime, no respawn needed
- `/fpp rank random <group> [num|all]` - assign a group to random bots
- `/fpp rank list` - see each bot's current group

### 🔍 New Commands
- `/fpp lpinfo [bot]` - in-game LP diagnostic: prefix, weight, rank index, packet profile name
- `/fpp alert <message>` - broadcast admin message to all servers on the proxy

### 💬 Formatting & Config
- **Fake-chat format** - `fake-chat.chat-format` supports `{prefix}`, `{bot_name}`, `{suffix}`, `{message}`; full LP gradient + color support
- **Tab-list name format** - `bot-name.tab-list-format` supports `{prefix}`, `{bot_name}`, `{suffix}`, and any PAPI placeholder
- **`luckperms.default-group`** config key; bots explicitly assigned `default` even when blank
- **Body toggles** - `body.pushable` and `body.damageable`; live-reloadable via `/fpp reload`
- **Spawn cooldown** - `spawn-cooldown` config key; `fpp.bypass.cooldown` permission

### 🛠️ Technical
- Per-subsystem debug flags: `logging.debug.startup/nms/packets/luckperms/network/config-sync/skin/database`
- YAML auto-sync - missing keys merged into `en.yml`, `bot-names.yml`, `bot-messages.yml` on every startup and reload
- `/fpp migrate` enhancements: `status`, `backup`, `backups`, `lang`, `names`, `messages`, `config`, `db merge`, `db export`, `db tomysql`
- Config version → `33`

---

## v1.4.28 *(2026-03-26)*

- **Skin diversity fix** - guaranteed-skin fallback pool uses on-demand random selection at startup
- **Vanilla skin pool** - 27 official Minecraft system accounts (Mojang devs + MHF_* skins)
- **Per-world placeholders** - `%fpp_count_<world>%`, `%fpp_real_<world>%`, `%fpp_total_<world>%`
- **`%fpp_online%`** - alias for `%fpp_total%`
- **Fake chat prefix/suffix** - `{prefix}` and `{suffix}` in `chat-format` for full LP integration
- **Spawn race condition fixed** - `/fpp despawn all` during spawn no longer leaves ghost entries
- **Portal/teleport bug fixed** - PDC-based entity recovery for bots pushed through portals
- **Body damageable toggle fixed** - event-level cancellation replaces entity-flag-only approach
- **Body config live reload** - `/fpp reload` immediately applies `body.pushable` / `body.damageable` changes

---

## v1.4.27 *(2026-03-25)*

- **Unified spawn syntax** - `/fpp spawn` supports `[count] [world] [x y z] [--name <name>]`
- **Improved `/fpp reload` output** - box-drawing lines, per-step detail, timing line
- **`/fpp reload` canUse fix** - operators can now reload without explicit permission nodes

---

## v1.4.26 *(2026-03-25)*

- **Tab-list weight ordering overhauled** - bots perfectly respect LP group weights
- **Rank command system** - `/fpp rank <bot> <group>` and `/fpp rank random`
- **Restoration bug fixed** - bots restored after restart maintain correct weights and ranks
- **Auto-update on group change** - prefixes and tab-list ordering update in real-time

---

## v1.4.24 *(2026-03-24)*

- YAML file syncer - missing keys auto-merged on startup and `/fpp reload`
- `/fpp migrate lang|names|messages` - force-sync individual YAML files from the bundled JAR

---

## v1.4.23 *(2026-03-23)*

- Fixed bot name colours lost after server restart
- Fixed join/leave delays being **20× longer** than configured
- `/fpp reload` now refreshes bot prefixes from LuckPerms immediately
- Added `/fpp despawn random [amount]`

---

## v1.4.22 *(2026-03-22)*

- `tab-list.enabled` - toggle bot visibility in the player tab list
- Multi-platform download links in update notifications
- Enhanced `/fpp reload` with step-by-step progress output

---

## v1.2.7 *(2026-03-14)*

- `/fpp freeze` - freeze / unfreeze any bot or all bots
- `/fpp stats` - live statistics panel (bots, frozen, TPS, DB totals)
- PlaceholderAPI expansion registered automatically
- Spawn cooldown system (`spawn-cooldown` config key)
- Animated tab-list header / footer
- Metrics toggle (`metrics.enabled`)

---

## v1.2.2 *(2026-03-14)*

- **Guaranteed Skin system** - fallback chain ensures bots always have a real skin
- `skin.fallback-name` config key for manual fallback
- Mojang API rate-limit fix
- Config auto-migration system introduced

---

## v1.0.0-rc1 *(2026-03-08)*

- First stable release
- Full permission system (`fpp.*`, `fpp.admin.*`, `fpp.user.*`)
- User-tier commands (`/fpp spawn`, `/fpp despawn`, `/fpp list`)
- Bot persistence - bots rejoin on server restart

---

## v0.1.0

- **Initial release**
- Tab list presence - bots appear as real players
- Join / leave messages
- In-world physical body (mannequin entity)
- Head AI - tracks nearby players
- Collision / push system

---

> 📥 **Download the latest version:** [Modrinth](https://modrinth.com/plugin/fake-player-plugin-(fpp)) · [SpigotMC](https://www.spigotmc.org/resources/fake-player-plugin-fpp.133572/) · [Hangar](https://hangar.papermc.io/Pepe-tf/FakePlayerPlugin) · [BuiltByBit](https://builtbybit.com/resources/fake-player-plugin.98704/)  
> 💬 **Support:** [Discord](https://discord.gg/QSN7f67nkJ)

