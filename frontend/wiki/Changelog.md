# 📋 Changelog

> **Full version history for Fake Player Plugin**  
> Latest version: **v1.6.6.7** · Released: 2026-04-26 · Config version: **67** · Database schema: **18**  
> 🎉 **Now Open Source** — [https://github.com/Pepe-tf/fake-player-plugin](https://github.com/Pepe-tf/fake-player-plugin)

---

## v1.6.6.7 *(2026-04-26)*

### Extension / Addon API
- New `FppExtension` interface — third-party developers can drop `.jar` files into `plugins/FakePlayerPlugin/extensions/` and FPP will auto-load them on startup
- `ExtensionLoader` scans extension jars for `FppExtension` implementations, instantiates them, and registers them as addons sorted by priority
- Full addon lifecycle: `onEnable(FppApi)` / `onDisable()` with access to commands, events, tick handlers, settings GUI tabs, metadata, navigation API, and service registry
- 20+ API event classes for bot interactions (spawn, despawn, move, mine, place, attack, follow, chat, etc.)
- See `EXTENSIONS.md` in the repository for the complete addon developer guide

### Random Name Generator
- New `bot-name.mode: random` (default) — generates realistic Minecraft-style usernames on the fly when the name pool is empty or when `mode: random` is set
- `bot-name.mode: pool` — legacy behaviour, picks from `bot-names.yml`
- No more `Bot1234` fallback names; every auto-generated name looks like a real player

### New Commands
- **`/fpp find <bot> <block> [--radius <n>] [--count <n>]`** — bot scans nearby chunks for the target block type, reserves matching locations, and mines them one by one. Async chunk snapshot scanning with progressive mining and raytrace visibility check. Permission: `fpp.find`
- **`/fpp groups [gui|list|create <name>|delete <name>|add <group> <bot>|remove <group> <bot>]`** — personal bot groups with GUI management. Group bots together for bulk commands. Permission: `fpp.groups`
- **`/fpp sleep <bot|all> <x y z> <radius>`** — registers a sleep-origin; bot auto-walks to the nearest free bed within radius at night and sleeps. `/fpp sleep <bot|all> --stop` clears the origin. NMS sleep/wake with temporary bed placement. Permission: `fpp.sleep`
- **`/fpp stop [<bot>|all]`** — instantly cancels all active tasks for a bot (move, mine, place, use, attack, follow, find, sleep). Permission: `fpp.stop`
- **`/fpp move <bot> --coords <x> <y> <z>`** — navigate a bot to exact world coordinates; supports `~` relative offsets. Permission: `fpp.move`
- **`/fpp attack <bot> --mob --move`** — PvE mob-targeting mode now supports pursuit; bot chases the target when out of melee range and stops to attack when in reach. Permission: `fpp.attack`

### WorldEdit Integration
- New `--wesel` flag for `/fpp mine` and `/fpp place` — uses the player's current WorldEdit selection as the work area instead of manual `--pos1`/`--pos2`
- Soft-dependency: `WorldEdit` added to `plugin.yml` softdepend list
- Permissions: `fpp.mine.wesel`, `fpp.place.wesel`

### Automation Defaults
- New `automation` config section:
  - `auto-eat: true` — bots eat food from inventory when hunger prevents sprinting
  - `auto-place-bed: true` — bots may place a bed from inventory for auto-sleep, then break it after waking
- Values are copied to newly spawned/restored bots; existing bots keep per-bot overrides

### Pathfinding & Knockback Fixes
- Door handling — bots now correctly open and pass through wooden doors, fence gates, and trapdoors during pathfinding
- Ladder and vine climbing — ASCEND/DESCEND moves now support ladders, vines, and scaffolding
- Knockback fix double-check — resolved residual knockback issues on 1.21.9+ with tiered strategy verification
- Organic walk wobble — subtle sine-wave yaw drift (±5°) on straight WALK segments for more human-like movement
- Sprint-jump naturalness — jump fires on first airborne→ground transition instead of fixed 6-tick timer

### Folia Support
- `folia-supported: true` declared in `plugin.yml`
- Compatible with Folia's regionised threading model

### Proxy & Communication
- Enhanced proxy communication with error handling and pending bot despawn management
- `fpp.tph.all` permission — teleports all accessible bots to the sender at once

### Configuration
- Config version: 65 → 67
- `chunk-loading.mass-disable-threshold: 100` — auto-releases chunk tickets when bot count exceeds this threshold to prevent mass-bot lag
- `bot-name.mode: random` (new default)
- `pathfinding.follow-recalc-interval: 100` (new key)

### Permissions
- New nodes: `fpp.find`, `fpp.sleep`, `fpp.stop`, `fpp.attack.hunt`, `fpp.mine.wesel`, `fpp.place.wesel`, `fpp.tph.all`
- All nodes declared in `plugin.yml` for LuckPerms tab-completion

### Technical
- Database schema updates for bot groups and despawn snapshot persistence
- `BotGroupCommand`, `BotGroupStore` for group management
- `FindCommand` with async chunk snapshot scanning and block reservation system
- `SleepCommand` with NMS sleep/wake and night-watch repeating task
- `StopCommand` with dependency injection of other command instances for bulk cancellation

---

## v1.6.6.2 *(2026-04-21)*

### Critical Bug Fixes

- **`/fpp despawn all` inventory preservation** — Fixed bug where bulk despawn erased all bot inventories and XP. `removeAll()` now implements proper snapshot logic identical to single-bot despawn — captures inventory and XP before clearing any maps.

- **Dimension spawn coordinate fix** — Bots spawned in Nether/End now stay at exact coordinates. `BotSpawnProtectionListener` now blocks all teleport causes (`NETHER_PORTAL`, `END_PORTAL`, `END_GATEWAY`) during the 5-tick spawn grace period.

### Despawn Snapshot Persistence

- **Inventory/XP survival across restarts** — Bot inventory and XP are preserved when you despawn and respawn the same bot name, even after server restart. New `fpp_despawn_snapshots` DB table (schema v17→v18) or `data/despawn-snapshots.yml` fallback.

- **Config migration v64→v65** — Auto-sets `body.drop-items-on-despawn: false` for existing installs to enable snapshot preservation by default.

### Configuration

- **New:** `messages.death-message` (default `true`) — toggle bot death messages
- **SettingGui:** Added toggles for `body.drop-items-on-despawn` and `messages.death-message`

### Technical

- Config version: 63 → 65
- Database schema: 17 → 18
- Language file character fixes
- BotSpawnProtectionListener UUID fallback for early spawn detection

---

## v1.6.6.1 *(2026-04-20)*

### 🚀 FPP BungeeCord Companion (`fpp-bungee.jar`)
- New standalone **BungeeCord/Waterfall proxy plugin** shipped alongside the main Paper plugin as `fpp-bungee.jar`
- Registers the `fpp:proxy` plugin-messaging channel and listens for `BOT_SPAWN`, `BOT_DESPAWN`, and `SERVER_OFFLINE` sub-messages from backend servers
- Maintains a live **bot registry** (`UUID → BotEntry`) populated via plugin messages; each entry stores `uuid`, `name`, `displayName`, and `serverId`
- Pings all registered backend servers every **5 seconds** and caches the total real+bot player count in `cachedBackendTotal`
- Intercepts `ProxyPingEvent` and inflates the displayed **server-list player count** to include FPP bots; merges bot display names into the hover sample list (up to 12 entries)
- Startup and shutdown banners with timing, registry status, and session uptime printed to the BungeeCord console
- Prints a prominent **anti-scam warning** on every startup reminding server owners that FPP and this companion are 100% free and open-source — if you paid for them, you were scammed
- Compatible with **BungeeCord and Waterfall**; drop `fpp-bungee.jar` into your BungeeCord `plugins/` folder — no configuration needed
- Source: `bungee-companion/` module in the FPP repository

### 🐛 Bug Fixes

- **Bot join/leave message color fix** — `BotBroadcast` now parses display names with full MiniMessage + legacy `&`/`§` color support. Previously, color tags in bot display names could render as raw text or apply incorrect fallback colors in join/leave broadcasts; display names now render exactly as configured in `en.yml`

---

## v1.6.6 *(2026-04-20)*

### 🚀 FPP Velocity Companion (`fpp-velocity.jar`)
- New standalone **Velocity proxy plugin** shipped alongside the main Paper plugin as `fpp-velocity.jar`
- Registers the `fpp:proxy` plugin-messaging channel and listens for `BOT_SPAWN`, `BOT_DESPAWN`, and `SERVER_OFFLINE` sub-messages from backend servers
- Maintains a live **bot registry** (`UUID → BotEntry`) populated via plugin messages; each entry stores `uuid`, `name`, `displayName`, and `serverId`
- Pings all registered backend servers every **5 seconds** and caches the total real+bot player count in `cachedBackendTotal`
- Intercepts `ProxyPingEvent` and inflates the displayed **server-list player count** to include FPP bots that already appear in backend counts; merges bot display names into the hover sample list (up to 12 entries)
- Startup and shutdown banners with timing, registry status, and session uptime printed to the Velocity console
- Prints a prominent **anti-scam warning** on every startup reminding server owners that FPP and this companion are 100% free and open-source — if you paid for them, you were scammed
- Requires **Velocity 3.3.0+**; drop `fpp-velocity.jar` into your Velocity `plugins/` folder — no configuration needed
- Source: `velocity-companion/` module in the FPP repository

### 🎯 Follow-Target Automation (`/fpp follow`)
- New `/fpp follow <bot|all> <player> [--stop]` command — bot continuously follows an online player; path recalculates whenever the target moves >3.5 blocks or every 60 ticks
- `--stop` cancels following on one bot or all bots at once
- FOLLOW task type persisted in `fpp_bot_tasks` (DB and `data/bot-tasks.yml` fallback) — bot resumes following after server restart if the target is online
- Arrival distance 2.0 blocks; stutter-free re-navigation fires 5 ticks after arrival to keep continuous following smooth
- Permission: `fpp.follow` (default: true, child of `fpp.op`)

### ⚔️ Per-Bot PvE Settings (now fully live)
- `BotSettingGui` PvP tab now has live-editable per-bot PvE controls:
  - **pveEnabled** toggle — enables/disables the bot's PvE auto-attack loop
  - **pveRange** — mob scan range in blocks
  - **pvePriority** — `nearest` or `lowest-health` targeting strategy
  - **pveMobTypes** — entity-type whitelist (`ZOMBIE`, `SKELETON`, etc.); empty = all hostile mobs
- Settings persisted to `fpp_active_bots` via DB schema v15→v16 and YAML fallback
- New config keys under `attack-mob.*`: `default-range` (8.0), `default-priority` ("nearest"), `smooth-rotation-speed` (12.0 °/tick), `retarget-interval` (10 ticks), `line-of-sight` (true)

### 🎨 Skin Persistence Across Restarts (DB v16→v17)
- Resolved bot skins are now saved to `fpp_active_bots` (`skin_texture TEXT`, `skin_signature TEXT` columns)
- On server restart, bots reload their cached skin directly — no additional Mojang API round-trip needed
- Skin data also stored in `BotPersistence` YAML for no-DB deployments

### 🌐 Server-List Config Keys
- New `server-list.count-bots` (default `true`) — controls whether bots increment the displayed server-list player count
- New `server-list.include-remote-bots` (default `false`) — include remote proxy bots in the server-list count (NETWORK mode only)
- Config v60→v61 migration adds both keys with safe defaults — no behaviour change for existing installs

### 🧭 `pathfinding.max-fall`
- New `pathfinding.max-fall` key (default `3`) — the A* pathfinder will not plan a descent of more than this many blocks in a single unbroken fall
- Prevents bots from choosing high-fall paths that would cause fall damage

### 🐛 Bug Fixes & Build

- **`Attribute.MAX_HEALTH` compatibility fix** — `NoSuchFieldError` on Paper/Purpur 1.21.1 and older is resolved. The new `Attribute.MAX_HEALTH` constant (added in Paper 1.21.3+) is no longer referenced directly in bytecode; a new `AttributeCompat` utility resolves `MAX_HEALTH` → `GENERIC_MAX_HEALTH` via reflection at class-load time, making the plugin fully compatible with all Paper 1.21.x versions (1.21.0 – 1.21.11)
- **FPP Velocity banner polish** — replaced `█` block characters in the anti-scam warning section with `═` double-line rules to match the rest of the console banner style
- **IntelliJ build fix** — `velocity-companion/fpp-velocity.iml` module file was missing, causing the IntelliJ artifact builder to produce an empty `fpp-velocity.jar`; the module path and full Velocity API transitive classpath are now correctly declared

### 💾 DB Schema v15 → v16 → v17
- **v15→v16:** `fpp_active_bots` gains four new columns:
  - `pve_enabled BOOLEAN DEFAULT 0`
  - `pve_range DOUBLE DEFAULT 16.0`
  - `pve_priority VARCHAR(16)`
  - `pve_mob_type VARCHAR(64)`
- **v16→v17:** `fpp_active_bots` gains two new columns:
  - `skin_texture TEXT`
  - `skin_signature TEXT`
- Fully backward-compatible — existing rows receive safe defaults on schema upgrade

### 📋 Config v60 → v61 → v62 → v63
- **v60→v61:** `server-list` section added (`count-bots: true`, `include-remote-bots: false`)
- **v61→v62:** `pathfinding.max-fall: 3` added
- **v62→v63:** `attack-mob.*` default config keys added (`default-range`, `default-priority`, `smooth-rotation-speed`, `retarget-interval`, `line-of-sight`)

---

## v1.6.5.1 *(2026-04-17)*

### ⚙️ BotSettingGui Now Publicly Available
- Per-bot settings GUI (shift+right-click any bot entity) is no longer restricted to the developer — it is now available to **all users with `fpp.settings` permission**
- Removed the developer UUID gate that previously blocked all other players with a "coming soon" message
- Any player granted `fpp.settings` (default: `op`) can now open the 6-row per-bot settings chest: **General · Chat · PvP · Cmds · Danger**
- Grant `fpp.settings` via LuckPerms to non-op players to let them manage their own bots' settings without full admin access
- The `bot-interaction.shift-right-click-settings` config key still controls whether the shift+right-click shortcut is active at all

---

## v1.6.5 *(2026-04-17)*

### 📡 Tab-List Ping Simulation
- New `/fpp ping [<bot>] [--ping <ms>|--random] [--count <n>]` command — set the visible tab-list latency for one or all bots
- `--ping <ms>` sets a specific latency (0–9999); `--random` assigns random realistic values; no flag shows current ping
- `--count <n>` targets N random bots for bulk operations
- 4 granular permissions: `fpp.ping` (view), `fpp.ping.set` (set specific value), `fpp.ping.random` (random distribution), `fpp.ping.bulk` (bulk `--count`)

### ⚔ PvE Attack Automation
- New `/fpp attack <bot> [--stop]` command — bot walks to the command sender and continuously attacks nearby entities
- Respects 1.9+ attack cooldown and item-specific cooldown timers dynamically
- Permission: `fpp.attack`

### 🔐 Permission System Restructure
- New `fpp.admin` node as the preferred alias for `fpp.op` — both grant full access identically
- New `fpp.despawn` node as preferred alias for `fpp.delete`; new `fpp.despawn.bulk` and `fpp.despawn.own` sub-nodes
- Granular sub-nodes for: chat (`fpp.chat.global`, `.tier`, `.mute`, `.say`), move (`fpp.move.to`, `.waypoint`, `.stop`), mine (`fpp.mine.start`, `.once`, `.stop`, `.area`), place (`fpp.place.start`, `.once`, `.stop`), use (`fpp.useitem.start`, `.once`, `.stop`), rank (`fpp.rank.set`, `.clear`, `.bulk`), inventory (`fpp.inventory.cmd`, `.rightclick`), ping (`fpp.ping.set`, `.random`, `.bulk`)
- New `fpp.command` (controls `/fpp` visibility — default `true`; negate to hide FPP from a group), `fpp.plugininfo` (full info panel on bare `/fpp`), `fpp.spawn.multiple`/`.mass`/`.coords`, `fpp.notify` (update notifications on join)
- All nodes declared in both `Perm.java` and `plugin.yml` for LuckPerms tab-completion

### 🎨 Skin Mode Rename
- `skin.mode` values renamed: `auto` → `player`, `custom` → `random`, `off` → `none`
- Legacy values still accepted as aliases — no migration needed for existing configs

### 🔧 FlagParser Utility
- New reusable command argument/flag parser with deprecation aliases, duplicate detection, and conflict detection
- Pattern: `new FlagParser(args).deprecate(...).conflicts(...).parse()` — use in new commands instead of ad-hoc arg scanning
- Used by `/fpp ping`; available for all future commands

### 🔄 UpdateChecker Beta Detection
- `latestKnownVersion` and `isRunningBeta` fields on `FakePlayerPlugin` — detects when running a build newer than the latest published release

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
- *(The dedicated `fpp-velocity.jar` companion plugin was formalised and shipped in v1.6.6 — see above)*

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

