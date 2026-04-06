# 📋 Changelog

> **Full version history for Fake Player Plugin**  
> Latest version: **v1.5.15** · Released: 2026-04-06

---

## v1.5.15 *(2026-04-06)*

### 📝 Config Clarity Improvements
- All timing-related values in `config.yml` now clearly state their unit (ticks or seconds) with human-readable conversion examples
- `join-delay` / `leave-delay` section header updated to *"Values are in TICKS — 20 ticks = 1 second"* with a quick-reference line; `min`/`max` keys now carry inline tick-unit comments
- `death.respawn-delay` comment now shows seconds equivalents: `15 = 0.75 s · 60 = 3 s · 100 = 5 s`
- `chunk-loading.update-interval` comment clarified: *"in ticks (20 ticks = 1 second). Lower = more responsive, higher = less overhead."*
- `swap.session` / `swap.absence` inline comments updated with real-world time examples (e.g. `60 = 1 min`, `300 = 5 min`)

### 🔧 Build Pipeline Fixes
- ProGuard: removed `**.yml` from `-adaptresourcefilecontents` — prevents charset corruption of `plugin.yml` and language files on Windows builds
- ProGuard: removed `-dontpreverify` — `StackMapTable` attributes preserved; obfuscated jar passes JVM verifier without `VerifyError`
- ProGuard: MySQL / SQLite shaded classes excluded from preverification to prevent `IncompleteClassHierarchyException`; merged back verbatim into final jar

---

## v1.5.12 *(2026-04-05)*

### 🔒 Stable Bot UUID Identity
- `BotIdentityCache` — each bot name is permanently tied to a stable UUID; LuckPerms data, inventory, and session history persist across restarts
- Storage: in-memory cache → `fpp_bot_identities` DB table → `data/bot-identities.yml` YAML fallback

### ⚙️ In-Game Settings GUI
- `/fpp settings` opens a 3-row chest GUI; 5 categories (General, Body, Chat, Swap, Peak Hours)
- Toggle booleans instantly; numeric values via chat-input prompt; reset page to JAR defaults; all changes apply live
- Permission: `fpp.settings`

### ⏰ Peak Hours Scheduler
- `PeakHoursManager` scales the bot pool by time-of-day windows (`peak-hours.schedule`, `day-overrides`, `stagger-seconds`)
- Crash-safe: sleeping-bot state persisted in `fpp_sleeping_bots` DB table, restored at startup
- New command: `/fpp peaks [on|off|status|next|force|list|wake <name>|sleep <name>]` — requires `swap.enabled: true`

### 💬 Per-Bot Chat Control
- Random activity tier per bot: quiet / passive / normal / active / chatty
- `/fpp chat <bot> tier|mute|info` per-bot controls; `/fpp chat all <on|off|tier|mute>` for bulk operations
- Event-triggered chat (`event-triggers.*`) and keyword reactions (`keyword-reactions.*`)

### 👻 Bodyless Bot Mode & Bot Types
- `bodyless` flag — bots without a world location exist in tab-list/chat only, no world entity
- `BotType`: `AFK` (passive) and `PVP` (combat via `BotPvpAI`)

### 🔧 Config Migration v41 → v44
- v41→v42: Added `peak-hours` section · v42→v43: Added `min-online`, `notify-transitions` · v43→v44: Removed `auto-enable-swap`

---

## v1.5.10 *(2026-04-05)*

### 🔄 `/fpp swap` Toggle Fix
- Running `/fpp swap` with no arguments now toggles swap on/off — exactly like `/fpp chat`
- `swap-enabled` and `swap-disabled` messages redesigned to match the chat toggle style (`session rotation has been enabled/disabled`)
- `swap-status-on` / `swap-status-off` now follow the same `is enabled / is disabled` pattern as chat status messages

### 💬 Bot Chat Interval Fix
- Bot chat loops are now restarted on `/fpp reload` so changes to `fake-chat.interval.min/max`, `fake-chat.chance`, and `fake-chat.stagger-interval` take effect **immediately** instead of waiting for each bot's old queued task to naturally expire
- `/fpp reload` output now shows the new interval range as confirmation

### 🛠️ Language & Compatibility
- `Biome.name()` deprecated call replaced with `Biome.getKey().getKey()` — compatible with Paper 1.22+
- `sync-usage` and `swap-now-usage` messages now end with a period, matching the rest of the file
- Startup banner now shows **Bot swap** status in the Features section

---

## v1.5.8 *(2026-04-03)*

### 🔧 Ghost Player / "Anonymous User" Fix
- Replaced reflection-based `Connection` injection with a proper `FakeConnection` subclass whose `send()` methods are clean no-op overrides
- Eliminated the phantom **"Anonymous User"** entry with UUID `0` that appeared in the tab list when bots connected
- Eliminated `NullPointerException` and `ClassCastException` spam in server logs related to bot connections

### 📊 `%fpp_real%` / `%fpp_total%` Accuracy Fix
- `%fpp_real%` now correctly subtracts bot count from `Bukkit.getOnlinePlayers()` — bots go through `placeNewPlayer()` and appear in the online list
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
- Config version bumped to `37` (no structural key changes — version stamp only)
- Automatic migration on first startup from any previous version

---

## v1.5.6 *(2026-04-03)*

### ⚔️ Knockback Fix (1.21.9–1.21.11)
- Bots now correctly receive knockback on 1.21.9+ servers
- Tiered strategy system auto-detects the correct MC version API at startup (zero reflection overhead per hit)
- `GET_MOVEMENT` (1.21.9+): uses `packet.getMovement()` → `Vec3` → `player.lerpMotion(Vec3)`
- `GET_XA` (≤1.21.8): uses `packet.getXa/Ya/Za()` → `lerpMotion(double,double,double)` or `setDeltaMovement(Vec3)` fallback

### 💥 Double-Disconnect Crash Fix (Paper 1.21+)
- Fixed `IllegalStateException: Already retired` spam when bots are slain
- `injectPacketListenerIntoConnection()` now updates both `ServerPlayer.connection` AND `Connection.packetListener` fields
- Ensures our `onDisconnect` override handles double-retirement gracefully

### 🛡️ Bot Protection System
- **Command blocking** — bots can no longer execute commands from ANY source (4-layer protection)
- **Lobby spawn fix** — 5-tick grace period prevents lobby plugins from teleporting bots at spawn
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
- Remote bot cache — thread-safe registry of bots on other proxy servers, populated from DB at startup

### 🔄 Config Sync
- `/fpp sync push/pull/status/check` commands
- Modes: `DISABLED`, `MANUAL`, `AUTO_PULL`, `AUTO_PUSH`
- Syncs `config.yml`, `bot-names.yml`, `bot-messages.yml`, `language/en.yml`
- Server-specific keys (`database.server-id`, `database.mysql.*`, `debug`) are **never** uploaded

### 🏷️ BotTabTeam
- Scoreboard team `~fpp` places all bots **below** real players in the tab list regardless of LP group weight

### 🎖️ Per-Bot LuckPerms Groups
- `/fpp rank <bot> <group>` — change a bot's LP group at runtime, no respawn needed
- `/fpp rank random <group> [num|all]` — assign a group to random bots
- `/fpp rank list` — see each bot's current group

### 🔍 New Commands
- `/fpp lpinfo [bot]` — in-game LP diagnostic: prefix, weight, rank index, packet profile name
- `/fpp alert <message>` — broadcast admin message to all servers on the proxy

### 💬 Formatting & Config
- **Fake-chat format** — `fake-chat.chat-format` supports `{prefix}`, `{bot_name}`, `{suffix}`, `{message}`; full LP gradient + color support
- **Tab-list name format** — `bot-name.tab-list-format` supports `{prefix}`, `{bot_name}`, `{suffix}`, and any PAPI placeholder
- **`luckperms.default-group`** config key; bots explicitly assigned `default` even when blank
- **Body toggles** — `body.pushable` and `body.damageable`; live-reloadable via `/fpp reload`
- **Spawn cooldown** — `spawn-cooldown` config key; `fpp.bypass.cooldown` permission

### 🛠️ Technical
- Per-subsystem debug flags: `logging.debug.startup/nms/packets/luckperms/network/config-sync/skin/database`
- YAML auto-sync — missing keys merged into `en.yml`, `bot-names.yml`, `bot-messages.yml` on every startup and reload
- `/fpp migrate` enhancements: `status`, `backup`, `backups`, `lang`, `names`, `messages`, `config`, `db merge`, `db export`, `db tomysql`
- Config version → `33`

---

## v1.4.28 *(2026-03-26)*

- **Skin diversity fix** — guaranteed-skin fallback pool uses on-demand random selection at startup
- **Vanilla skin pool** — 27 official Minecraft system accounts (Mojang devs + MHF_* skins)
- **Per-world placeholders** — `%fpp_count_<world>%`, `%fpp_real_<world>%`, `%fpp_total_<world>%`
- **`%fpp_online%`** — alias for `%fpp_total%`
- **Fake chat prefix/suffix** — `{prefix}` and `{suffix}` in `chat-format` for full LP integration
- **Spawn race condition fixed** — `/fpp despawn all` during spawn no longer leaves ghost entries
- **Portal/teleport bug fixed** — PDC-based entity recovery for bots pushed through portals
- **Body damageable toggle fixed** — event-level cancellation replaces entity-flag-only approach
- **Body config live reload** — `/fpp reload` immediately applies `body.pushable` / `body.damageable` changes

---

## v1.4.27 *(2026-03-25)*

- **Unified spawn syntax** — `/fpp spawn` supports `[count] [world] [x y z] [--name <name>]`
- **Improved `/fpp reload` output** — box-drawing lines, per-step detail, timing line
- **`/fpp reload` canUse fix** — operators can now reload without explicit permission nodes

---

## v1.4.26 *(2026-03-25)*

- **Tab-list weight ordering overhauled** — bots perfectly respect LP group weights
- **Rank command system** — `/fpp rank <bot> <group>` and `/fpp rank random`
- **Restoration bug fixed** — bots restored after restart maintain correct weights and ranks
- **Auto-update on group change** — prefixes and tab-list ordering update in real-time

---

## v1.4.24 *(2026-03-24)*

- YAML file syncer — missing keys auto-merged on startup and `/fpp reload`
- `/fpp migrate lang|names|messages` — force-sync individual YAML files from the bundled JAR

---

## v1.4.23 *(2026-03-23)*

- Fixed bot name colours lost after server restart
- Fixed join/leave delays being **20× longer** than configured
- `/fpp reload` now refreshes bot prefixes from LuckPerms immediately
- Added `/fpp despawn random [amount]`

---

## v1.4.22 *(2026-03-22)*

- `tab-list.enabled` — toggle bot visibility in the player tab list
- Multi-platform download links in update notifications
- Enhanced `/fpp reload` with step-by-step progress output

---

## v1.2.7 *(2026-03-14)*

- `/fpp freeze` — freeze / unfreeze any bot or all bots
- `/fpp stats` — live statistics panel (bots, frozen, TPS, DB totals)
- PlaceholderAPI expansion registered automatically
- Spawn cooldown system (`spawn-cooldown` config key)
- Animated tab-list header / footer
- Metrics toggle (`metrics.enabled`)

---

## v1.2.2 *(2026-03-14)*

- **Guaranteed Skin system** — fallback chain ensures bots always have a real skin
- `skin.fallback-name` config key for manual fallback
- Mojang API rate-limit fix
- Config auto-migration system introduced

---

## v1.0.0-rc1 *(2026-03-08)*

- First stable release
- Full permission system (`fpp.*`, `fpp.admin.*`, `fpp.user.*`)
- User-tier commands (`/fpp spawn`, `/fpp despawn`, `/fpp list`)
- Bot persistence — bots rejoin on server restart

---

## v0.1.0

- **Initial release**
- Tab list presence — bots appear as real players
- Join / leave messages
- In-world physical body (mannequin entity)
- Head AI — tracks nearby players
- Collision / push system

---

> 📥 **Download the latest version:** [Modrinth](https://modrinth.com/plugin/fake-player-plugin-(fpp)) · [SpigotMC](https://www.spigotmc.org/resources/fake-player-plugin-fpp.133572/) · [Hangar](https://hangar.papermc.io/Pepe-tf/FakePlayerPlugin) · [BuiltByBit](https://builtbybit.com/resources/fake-player-plugin.98704/)  
> 💬 **Support:** [Discord](https://discord.gg/QSN7f67nkJ)

