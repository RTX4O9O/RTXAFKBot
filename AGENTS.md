# AGENTS.md — Guide for AI coding agents

Purpose: help automated agents quickly understand, modify, and verify the FakePlayerPlugin codebase.

1) Quick repo map
- `src/main/java/me/bill/fakePlayerPlugin/` — main code and entry points
- `src/main/resources/` — default `config.yml`, `plugin.yml`, `bot-names.yml`, `bot-messages.yml`
- `src/main/resources/language/en.yml` — all user-facing messages (loaded by `Lang.java`)
- `pom.xml` — build + jar metadata (`target/fpp-*.jar` produced)
- `frontend/`, root `package.json`, and `vercel.json` — separate Node/Vercel status + update API sidecar used by the plugin’s update-check fallback; not part of the Maven plugin jar build.
- `wiki/` — feature docs (Commands.md, Configuration.md, Getting-Started.md, Database.md, Language.md, Permissions.md, Skin-System.md, Swap-System.md, Bot-Behaviour.md, Bot-Messages.md, Bot-Names.md, Fake-Chat.md, FAQ.md, Migration.md, Home.md, Proxy-Support.md, Config-Sync.md)
- `src/main/java/me/bill/fakePlayerPlugin/util/` — helper subsystems and utilities (e.g. `FppMetrics`, `FppLogger`, `FppPlaceholderExpansion`, `TabListManager`, `BackupManager`, `UpdateChecker`, `ConfigValidator`, `DataMigrator`, `TextUtil`, `ConfigMigrator`, `LuckPermsHelper`, `CompatibilityChecker`, `YamlFileSyncer`, `BotTabTeam`).
- `src/main/java/me/bill/fakePlayerPlugin/listener/` — Bukkit event listeners (`PlayerJoinListener`, `FakePlayerEntityListener`, `FakePlayerKickListener`, `BotCollisionListener`, `PlayerWorldChangeListener`, `ServerListListener`). Note: `LuckPermsUpdateListener` was removed — LP now detects NMS bots as real players automatically.
- `src/main/java/me/bill/fakePlayerPlugin/fakeplayer/` — bot domain objects and subsystems (e.g. `FakePlayer`, `FakePlayerBody`, `FakePlayerManager`, `BotPersistence`, `ChunkLoader`, `SkinRepository`, `SkinFetcher`, `SkinProfile`, `NmsHelper`, `NmsPlayerSpawner`, `NmsReflection`, `BotSwapAI`, `BotChatAI`, `BotBroadcast`, `PacketHelper`, `RemoteBotCache`, `RemoteBotEntry`). Note: `BotHeadAI` has been removed.
- `src/main/java/me/bill/fakePlayerPlugin/messaging/` — proxy communication (`VelocityChannel`): registers `fpp:main` plugin-messaging channel; wraps outbound messages in a BungeeCord `Forward ALL` envelope so Velocity/BungeeCord re-delivers to every backend. Subchannels: `BOT_SPAWN`, `BOT_DESPAWN`, `CHAT`, `ALERT`, `JOIN`, `LEAVE`, `SYNC`. Echo suppression via per-message unique IDs.
- `src/main/java/me/bill/fakePlayerPlugin/sync/` — proxy config sync (`ConfigSyncManager`): push/pull config files to/from MySQL in NETWORK mode. Supports `DISABLED`, `MANUAL`, `AUTO_PULL`, `AUTO_PUSH` modes. Server-specific keys (`database.server-id`, `database.mysql.*`, `debug`) are never synced.
- `src/main/java/me/bill/fakePlayerPlugin/database/` — session history and analytics (`DatabaseManager`, `BotRecord`).
- `src/main/java/me/bill/fakePlayerPlugin/lang/` — message rendering (`Lang`).
- `src/main/java/me/bill/fakePlayerPlugin/permission/` — centralised permission registry (`Perm`).

2) Where to start (entry points)
- `FakePlayerPlugin.java` — plugin lifecycle (`onEnable` / `onDisable`) and orchestration.
- `command/CommandManager.java` and command classes (`SpawnCommand`, `DeleteCommand` (registers as `despawn`), `ListCommand`, `HelpCommand`, `InfoCommand`, `ChatCommand`, `SwapCommand`, `ReloadCommand`, `FreezeCommand`, `StatsCommand`, `MigrateCommand`, `TpCommand`, `TphCommand`, `LpInfoCommand`, `RankCommand`, `AlertCommand`, `SyncCommand`) — command wiring and tab completion.
- `command/FppCommand.java` — interface every sub-command implements (`getName`, `getUsage`, `getDescription`, `getPermission`, `canUse`, `execute`, `tabComplete`).
- `config/Config.java`, `util/ConfigMigrator.java` — configuration pattern and automatic migration.
- `fakeplayer/FakePlayerManager.java`, `fakeplayer/BotPersistence.java` — bot lifecycle, persistence and restore.
- `fakeplayer/FakePlayer.java` — domain object for a single active bot (name, UUID, profile, physicsEntity, nametagEntity, spawnLocation, displayName, skinName, frozen state). Additional fields: `luckpermsGroup` (per-bot LP group override, `null` = use global config; get/set via `getLuckpermsGroup()`/`setLuckpermsGroup()`), `botIndex` (sequential index for user bots; `-1` for admin bots; used to rebuild display names after LP group changes), `bodyless` (when `true` the bot exists in tab-list/chat only, no entity in world — used for console spawns without location data).
- `fakeplayer/FakePlayerBody.java` — NMS ServerPlayer spawning/removal. Entities tagged via PDC keys for identification.
- `fakeplayer/NmsPlayerSpawner.java` — reflection-based NMS `ServerPlayer` factory (cached class/method/constructor lookups); bypasses Mojang auth by creating the player directly in JVM memory.
- `fakeplayer/NmsReflection.java` — shared NMS reflection bootstrap (CraftBukkit/NMS class resolution); initialised once, thread-safe.
- `fakeplayer/RemoteBotCache.java` — thread-safe registry of bots physically running on other proxy servers. Always initialised (even in LOCAL mode). Populated from DB at startup (NETWORK mode) and updated via `BOT_SPAWN`/`BOT_DESPAWN` plugin messages. Exposed via `FakePlayerPlugin.getRemoteBotCache()`.
- `fakeplayer/RemoteBotEntry.java` — immutable record (`serverId`, `uuid`, `name`, `displayName`, `packetProfileName`, `skinValue`, `skinSignature`) for a remote bot; `hasSkin()` checks whether texture data is present.
- `fakeplayer/SkinProfile.java` — immutable snapshot of a resolved Mojang skin (base64 value + signature + source label).
- `config/BotNameConfig.java`, `config/BotMessageConfig.java` — bot name & message pools which are initialised in `onEnable`.
- `fakeplayer/SkinRepository.java` / `fakeplayer/SkinFetcher.java` — skin-loading subsystem (called during startup).
- `lang/Lang.java` — message rendering. Loads `language/en.yml`, supports named and positional placeholders. Use `Lang.get("key", "name", "value")` for an Adventure `Component` or `Lang.raw("key", ...)` for a raw string. Hot-reloadable via `/fpp reload`.
- `permission/Perm.java` — central permission registry. All permission nodes declared as constants (e.g. `Perm.SPAWN`, `Perm.DELETE`, `Perm.USER_SPAWN`). Use `Perm.has()`, `Perm.hasOrOp()`, `Perm.resolveUserBotLimit()` helpers. Never hard-code permission strings in commands.
- `database/DatabaseManager.java`, `database/BotRecord.java` — session history and analytics. Supports SQLite (default, WAL mode) and MySQL. Writes are serialised on a background thread; positions are batch-flushed on a configurable interval. Controlled by `Config.databaseEnabled()` and `Config.mysqlEnabled()`.
- `util/TabListManager.java`, `util/UpdateChecker.java` — tab header/footer management and async update checker initialized during startup.

3) Key patterns & concrete examples (copy-ready pointers)
- Migration runs before config init: `ConfigMigrator.migrateIfNeeded(this)` in `onEnable` (see `FakePlayerPlugin.java`).
- Config usage: call `Config.init(this)` then use accessors like `Config.fakeChatEnabled()` in code. Tab-list visibility accessor: `Config.tabListEnabled()` (key `tab-list.enabled`, default `true`) — when `false`, bots are never added to any player's tab list; the 20-tick display-name refresh loop, `syncToPlayer`, `resendTab` in `FakePlayerBody`, and the respawn re-add in `FakePlayerEntityListener` all check this flag before sending any `sendTabListAdd` packets. Toggling via `/fpp reload` calls `FakePlayerManager.applyTabListConfig()` which immediately removes or re-adds all active bots' tab entries for all online players. **New accessors:** `Config.fakeChatFormat()` (`fake-chat.chat-format`, default `"&7{bot_name}: {message}"`) — chat line format for `BotChatAI`, supports MiniMessage and `&` codes, placeholders `{prefix}` / `{bot_name}` / `{message}` / `{suffix}` (prefix and suffix only when `Config.luckpermsUsePrefix()`); `Config.tabListNameFormat()` (`bot-name.tab-list-format`, default `"{prefix}{bot_name}{suffix}"`) — full display name format applied by `FakePlayerManager.finalizeDisplayName()`; `Config.bodyPushable()` (`body.pushable`, default `true`); `Config.bodyDamageable()` (`body.damageable`, default `true`). **Network/proxy accessors:** `Config.databaseMode()` (`database.mode`, `"LOCAL"` or `"NETWORK"`); `Config.isNetworkMode()` — shorthand for `databaseEnabled() && databaseMode()=="NETWORK"`; `Config.serverId()` (`database.server-id`, falls back to legacy `server.id`); `Config.configSyncMode()` (`config-sync.mode`, one of `DISABLED` / `MANUAL` / `AUTO_PULL` / `AUTO_PUSH`). Server-specific keys (`database.server-id`, `database.mysql.*`, `debug`) are never pushed/pulled by `ConfigSyncManager`.
- Language messages: always use `Lang.get("key")` (returns Adventure `Component`) or `Lang.raw("key")` (returns `String`) — never hard-code user-facing text. Named placeholders: `Lang.get("freeze-frozen", "name", fp.getDisplayName())`. All keys live in `src/main/resources/language/en.yml`.
- Permission checks: use `Perm.has(sender, Perm.SPAWN)` or `Perm.hasOrOp(sender, Perm.ALL)` — never hard-code node strings. Per-user bot limits resolved via `Perm.resolveUserBotLimit(sender)` (scans `fpp.bot.1` … `fpp.bot.100`; returns -1 if none set, caller falls back to `Config.userBotLimit()`).
- Command registration: plugin registers commands via `commandManager.register(new SpawnCommand(...))`; `HelpCommand` is auto-registered inside `CommandManager`, and `/fpp help` reads live from the registered command list, so new sub-commands need no separate help-menu wiring. The plugin then calls `getCommand("fpp").setExecutor(commandManager)` (see `onEnable`).
- Listener registration: `getServer().getPluginManager().registerEvents(new PlayerJoinListener(this, fakePlayerManager), this)` — listeners receive plugin + manager references.
- Persistence lifecycle: restore on enable via `botPersistence.purgeOrphanedBodiesAndRestore(fakePlayerManager)` (purges orphaned entities then calls `restore()` 5 ticks later) and save on disable via `botPersistence.save(fakePlayerManager.getActivePlayers())`. `BotPersistence.saveAsync()` also available for non-shutdown saves (snapshots locations on main thread, writes async). When DB is enabled, the `fpp_active_bots` table is the **primary** restore source (`DatabaseManager.getActiveBots()`); `data/active-bots.yml` is the fallback for no-DB or empty-table cases.
- Metrics & soft-deps: metrics are initialized with `FppMetrics` when `Config.metricsEnabled()`; PlaceholderAPI expansion registered conditionally in `onEnable`.
- Compatibility checks: `CompatibilityChecker.check()` (in `util/CompatibilityChecker.java`) runs at startup and returns an immutable `Result` (Paper server detection + Minecraft minimum version `1.21.9` + NMS reflection support). When unsupported the plugin sets `compatibilityRestricted = true` and disables chunk loading, physical bodies and NMS-dependent listeners — look for `compatibilityRestricted` and `compatibilityWarningMessage` in `FakePlayerPlugin.java`. `Result.failureLangKeys` lists per-failure lang keys. `CompatibilityChecker.buildWarningComponent(result)` produces the in-game admin warning.
- Bot pools: `BotNameConfig.init(this)` and `BotMessageConfig.init(this)` are called during startup — the plugin loads `bot-names.yml` and `bot-messages.yml` early.
- Metrics / FastStats packaging: `FppMetrics` is initialized before the startup banner to reflect metrics status. FastStats jars are bundled under `src/main/resources/faststats/` (the POM treats them as binary resources, not shaded). Note: building requires JDK 21 (see `pom.xml` <java.version>21).</p>
- Entity stack: each bot is a single NMS ServerPlayer entity (created via `NmsPlayerSpawner`) with full player functionality including chat, tab-list presence, and realistic movement. Use PDC keys for identification during event handling.
- Database gate: check `Config.databaseEnabled()` before any `DatabaseManager` call; `databaseManager` may be `null` at runtime (disabled or failed to init). `databaseManager.recordAllShutdown()` must be called in `onDisable` before `databaseManager.close()`.
- Text formatting: `TextUtil.colorize(string)` parses MiniMessage tags and legacy `&`/`§` codes into an Adventure `Component`. `TextUtil.format(string)` additionally applies small-caps Unicode conversion. **Supports all modern color formats:** MiniMessage tags (`<rainbow>`, `<gradient:#FF0000:#0000FF>`), hex colors (`<#9782ff>`), LuckPerms gradient shorthand (`{#fffff>}text{#00000<}`), and mixed formats (`&7[<#9782ff>Phantom</#9782ff>&7]`).
- LuckPerms integration: `LuckPermsHelper` resolves bot prefixes, weights, **and suffixes** from LP groups. Fully supports all LuckPerms color formats including gradients, rainbow, hex, and legacy codes. `LpData` is a 3-field record `(String prefix, int weight, String suffix)` — use `lpData.suffix()` for the suffix string and `lpData.hasSuffix()` to check presence. Call `LuckPermsHelper.getBotLpData(botSpecificGroup, ownerUuid)` to get all three fields (pass `null` as `botSpecificGroup` to use global config/default; backward-compat overload `getBotLpData(ownerUuid)` also exists), or `LuckPermsHelper.invalidateCache()` on reload. Validate or enumerate LP groups via `LuckPermsHelper.groupExists(groupName)` and `LuckPermsHelper.getAllGroupNames()`. Use `LuckPermsHelper.invalidateGroup(groupName)` + `LuckPermsHelper.refreshRankList()` to selectively bust the cache after a bot-specific group change. To update a single bot's prefix, display name, and tab-list entry after a group change call `FakePlayerManager.refreshLpDisplayName(FakePlayer)`. Tab ordering and packet-name behaviour are controlled via `Config.luckpermsWeightOrderingEnabled()`, `Config.luckpermsBotGroup()`, `Config.luckpermsPacketPrefixChar()`, and `Config.luckpermsWeightOffset()` (legacy option, config default `0`; the `~fpp` scoreboard team now guarantees bots appear below real players, so this no longer affects ordering). Use `/fpp lpinfo [bot-name]` as the in-game diagnostic command for prefix / weight / ordering issues. Check LP availability at runtime via `LuckPermsHelper.isAvailable()` (used in `RankCommand` before any LP API call). **LP group assignment:** `Config.luckpermsDefaultGroup()` (`luckperms.default-group`, default `""`) — optional LP group assigned to every new bot at spawn; when blank, bots receive LP's built-in "default" group. **Note:** `LuckPermsUpdateListener` has been removed — bots are real NMS `ServerPlayer` entities and LP detects them as online players automatically; no manual listener is needed for prefix updates. **LP injection flow:** `finishSpawn` is split into two methods. First, `finishSpawn` calls `LuckPermsHelper.setPlayerGroup()` **asynchronously before the NMS body spawns** — this writes the group to LP storage so that when `FakePlayerBody.spawn()` fires `PlayerJoinEvent`, LP and tab-list plugins (e.g. TAB) load the user from storage and see the correct group immediately. If no `luckperms.default-group` is configured, bots are still explicitly assigned to `"default"` to prevent LP reporting group=`(none)` at join time. Once LP completes, it calls `spawnBodyAndFinish(fp, spawnLoc)` back on the main thread — this runs the skin resolution and body spawn pipeline. After the body spawns, `refreshLpDisplayName(fp)` is scheduled 5 ticks later to re-read `CachedMetaData.getPrefix()/getSuffix()` and rebuild the tab-list display name with LP prefix/suffix. This same refresh is triggered by `/fpp rank` assignment. Raw display content (the `{bot_name}` slot value before LP prefix/suffix are added) is stored on `FakePlayer.rawDisplayName` at every spawn/restore path and is used as the stable base for rebuilds. **`finalizeDisplayName` substitutes `{prefix}` and `{suffix}` with empty strings** at initial spawn time (so they're not mangled by `sanitizeDisplayName`); they are filled in properly by `refreshLpDisplayName` after LP data is available.
  - **Tab-list ordering model:** `LuckPermsHelper.buildPacketProfileName(weight, name)` produces a hidden packet profile name like `{00_BotName`. The prefix character (`packet-prefix-char`, default `{` ASCII 123) pushes the entire bot section after all real player names (a–z). When `packet-prefix-char` is empty the plugin falls back to `~` automatically — never leave it blank if you want bots at the bottom. **Rank ordering within the bot section:** Higher LP weight → lower rank index → appears HIGHER in bot section. Example: Admin bot (weight=100) gets rank=00 (`{00_AdminBot`), default bot (weight=1) gets rank=02 (`{02_DefaultBot`). This matches standard LuckPerms ordering where higher weight = higher priority. `LpData.weight()` returned by `getBotLpData` is the **original unpenalised** LP group weight. When LP data changes, `updateAllBotPrefixes()` sends `sendTabListRemove` + `sendTabListAdd` (not just `UPDATE_DISPLAY_NAME`) so the client correctly repositions the entry in the tab list. **Scoreboard team override:** All bots are placed in the `~fpp` scoreboard team (managed by `BotTabTeam`) which sorts AFTER all other teams, ensuring bots always appear below real players regardless of their individual rank.
- Listener registration: `getServer().getPluginManager().registerEvents(new PlayerJoinListener(this, fakePlayerManager), this)` — listeners receive plugin + manager references. `FakePlayerKickListener` (guards all bots from automated kicks like anti-idle/anti-AFK) is always registered; NMS-dependent listeners (`FakePlayerEntityListener`, `BotCollisionListener`) are skipped when `compatibilityRestricted`.
- `onDisable` sequence: save persistence → `chunkLoader.releaseAll()` → `botSwapAI.cancelAll()` → `fakePlayerManager.removeAllSync()` → `tabListManager.shutdown()` → `botTabTeam.destroy()` → `databaseManager.recordAllShutdown()` + `close()` → `fppMetrics.shutdown()` → unregister VelocityChannel messaging channels.
- Update notifications to late-joining admins: stored as a `Component` on `FakePlayerPlugin.getUpdateNotification()` / `setUpdateNotification()`; `PlayerJoinListener` delivers it to players with `Perm.ALL`.
- YAML file sync: `YamlFileSyncer.syncMissingKeys(plugin, diskRelPath, jarResourcePath)` — non-destructive; adds keys present in the JAR but absent from disk, never overwrites user values. Returns a `SyncResult` record (`fileName`, `keysAdded`, `hasChanges()`, `count()`). Called automatically inside `Lang.reload()`, `BotNameConfig.reload()`, and `BotMessageConfig.reload()` on every startup and `/fpp reload`. Force-run from the command line with `/fpp migrate lang`, `/fpp migrate names`, or `/fpp migrate messages`. When adding new keys to `en.yml`, `bot-names.yml`, or `bot-messages.yml`, they will be automatically synced to existing user files on next reload — no manual migration step needed.
- Spawn cooldown: `Config.spawnCooldown()` (key `spawn-cooldown`, default `0` = disabled). Applies to both admin and user `/fpp spawn`. Players with `Perm.BYPASS_COOLDOWN` (`fpp.bypass.cooldown`) are exempt. Cooldown state lives in `FakePlayerManager.spawnCooldowns` (per-UUID map). Helpers: `manager.isOnCooldown(uuid)`, `manager.getRemainingCooldown(uuid)` (seconds), `manager.recordSpawnCooldown(uuid)`.
- Backup variants: `BackupManager.createFullBackup(plugin, reason)` — backs up all plugin files including SQLite; used by `ConfigMigrator` before migrations. `BackupManager.createConfigFilesBackup(plugin, reason)` — backs up YAML files only (`config.yml`, `bot-names.yml`, `bot-messages.yml`, `language/`); used before lightweight sync operations (e.g. `/fpp migrate lang`). Both methods prune to the most recent `MAX_BACKUPS` (10) automatically.
- Additional `Perm` helpers: `Perm.missing(sender, node)` returns `true` when the sender lacks the node (negation of `has()`); `Perm.hasAny(sender, node1, node2, ...)` returns `true` when the sender has at least one of the listed nodes.
- Physical-body API: `FakePlayerManager.physicalBodiesEnabled()` — returns `Config.spawnBody() && !plugin.isCompatibilityRestricted()`. Use this instead of checking both conditions inline when deciding whether NMS ServerPlayer bodies are active.
- Display-name pipeline: `FakePlayerManager.finalizeDisplayName(rawName, botName, lpData)` — central helper that applies `Config.tabListNameFormat()`, substitutes `{prefix}` / `{bot_name}` / `{suffix}` (prefix and suffix only when `Config.luckpermsUsePrefix()`), expands PAPI `%placeholders%` via `PlaceholderAPI.setPlaceholders(null, ...)` (null player = server-wide), then calls `sanitizeDisplayName()`. Called at every display-name build site: `spawnUserBot`, `spawn`, `spawnRestored`, `validateUserBotNames`, `updateAllBotPrefixes`, `updateBotPrefix`.
- Body damageable/pushable live-reload: `FakePlayerManager.applyBodyConfig()` — called on `/fpp reload`; handles damage/pushable configuration for all active NMS ServerPlayer entities. `BotCollisionListener` also guards all three push paths (`onEntityDamageByEntity`, `onPlayerMove`, `tickBotSeparation`) with `if (!Config.bodyPushable()) return;`.

4) Common tasks & minimal steps (how to implement typical changes)
- Add a new command:
  - Create `src/main/java/me/bill/fakePlayerPlugin/command/MyCommand.java` implementing the command interface used by `CommandManager`.
  - Register it in `FakePlayerPlugin.onEnable()` with `commandManager.register(new MyCommand(...))`.
  - No manual help update is needed — `HelpCommand` reads live from `CommandManager`.
  - Ensure `plugin.yml` contains the `fpp` command or sub-command docs (command parsing done by `CommandManager`).
- Add a config option:
  - Add default to `src/main/resources/config.yml`.
  - If needed, update `Config` accessor and `ConfigMigrator` to preserve old configs.
  - New options should be validated in `util/ConfigValidator.java` if they can be misconfigured.
- Add a language message:
  - Add the key and default text to `src/main/resources/language/en.yml`.
  - Access it in code with `Lang.get("your-key")` or `Lang.raw("your-key", "placeholder", "value")`.
  - The file is hot-reloadable — `/fpp reload` picks up changes without restart.
- Add a permission node:
  - Declare a new `public static final String` constant in `permission/Perm.java`.
  - Add the node under `permissions:` in `plugin.yml` as a child of the appropriate parent (`fpp.*` or `fpp.user.*`).
  - Use `Perm.has(sender, Perm.MY_NODE)` in commands — never the raw string.

    - If changing skin behaviour or adding skins: ensure `SkinRepository`/`SkinFetcher` are respected — add skin files to `src/main/resources/skins/` for defaults or `plugins/FakePlayerPlugin/skins/` at runtime and call `/fpp reload` to refresh. The startup code also drops a `skins/README.txt` inside the plugin data folder on first run.

5) Build / run / verify (Windows PowerShell)
Build, obfuscate, AND auto-deploy to `%USERPROFILE%\Desktop\dmc\plugins\fpp.jar` in one command:
```powershell
mvn -DskipTests clean package
```
The `maven-antrun-plugin` in `pom.xml` automatically copies `target/fpp-*-obfuscated.jar` to the deploy folder after ProGuard finishes. To deploy to a different path override the property:
```powershell
mvn -DskipTests clean package "-Ddeploy.dir=C:\path\to\server\plugins"
```
Note: requires JDK 21+ to compile (`pom.xml` `<java.version>21`). FastStats jars are bundled as binary resources in `src/main/resources/faststats/` (not shaded).

**IntelliJ build tool ("Build Artifacts"):** IntelliJ's native artifact builder bypasses Maven and ProGuard. Treat `target/fpp-*-obfuscated.jar` as the source artifact and use `pom.xml` as the source of truth for the current version; some repo docs/scripts still mention `1.4.22`. Workflow:
1. Run the **"Build Plugin (Obfuscated)"** Maven run configuration (or `mvn clean package`) — this runs ProGuard and auto-deploys
2. Optionally run **Build › Build Artifacts** to re-pack the already-obfuscated JAR to the output path without recompiling
- ⚠️ "Build Artifacts" alone (without Maven running first) will fail if `target/fpp-*-obfuscated.jar` does not exist

Double-click `BUILD.bat` in the project root as an alternative to Maven in terminal.

For the optional Node/Vercel sidecar (`frontend/`), use the root `package.json` scripts: `npm install`, then `npm start` (local Express server via `frontend/index.js`) or `npm run dev`.

Start server (attach debugger):
```powershell
java -agentlib:jdwp=transport=dt_socket,server=y,suspend=n,address=*:5005 -Xms512M -Xmx2G -jar .\paper-1.21.11.jar nogui
```
Tail logs:
```powershell
Get-Content .\logs\latest.log -Wait
```
In-server quick checks: `/fpp list`, `/fpp spawn`, `/fpp reload` and watch console for startup banner messages printed by `FppLogger`.

6) Debugging tips
- To reproduce startup ordering bugs, inspect `onEnable()` in `FakePlayerPlugin.java` — migration → config → Lang → BotNameConfig/BotMessageConfig → ensureDataDirectories → SkinRepository → compatibility check → database → remote bot cache (NETWORK mode: pre-populate from DB) → ConfigSyncManager (NETWORK mode only) → FakePlayerManager+subsystems → commands → listeners → VelocityChannel (plugin messaging) → periodic health check (6000 ticks) → ConfigValidator → TabListManager → BotTabTeam → PlaceholderAPI → LuckPerms (deferred 1 tick) → UpdateChecker (purely async, never blocks startup) → Metrics → startup banner → persistence restore.
- To debug config migrations, read `util/ConfigMigrator.java` and check backups under `plugins/FakePlayerPlugin/backups/` created by `BackupManager`.
- For packet-level issues (spawn / entity problems) inspect `fakeplayer/PacketHelper.java` and `fakeplayer/FakePlayerManager.java`.
- For database issues: SQLite file is at `plugins/FakePlayerPlugin/data/fpp.db`; run `/fpp migrate status` to check schema version, YAML sync health, and backup count. Full `/fpp migrate` subcommands: `backup` (create manual backup), `backups` (list stored backups), `status` (config version + file sync health + DB stats), `config` (re-run config migration chain), `lang` / `names` / `messages` (force-sync the respective YAML file from JAR), `db merge [file]` / `db export` / `db tomysql`. Set `database.enabled: false` in config to disable all DB I/O.
- Startup compatibility checks (Paper / MC version / NMS reflection) can set `compatibilityRestricted` and silently skip chunk-loading & some listeners — inspect `FakePlayerPlugin.onEnable()` for `compatibilityRestricted` usage and the generated `compatibilityWarningMessage`.
- Tab list header/footer and update checker: `TabListManager.start()` and `UpdateChecker.check(this)` are run during `onEnable()` — useful when diagnosing missing tab headers or unexpected update-checker output.

7) Integration & soft-dependencies
- PlaceholderAPI: `util.FppPlaceholderExpansion` registered if plugin present. **Full placeholder list** — server-wide: `%fpp_count%` (bot count), `%fpp_max%` (global cap, `∞` if 0), `%fpp_real%` (non-bot players online), `%fpp_total%` (real + bots), `%fpp_frozen%`, `%fpp_names%` (comma-joined display names), `%fpp_chat%`, `%fpp_swap%`, `%fpp_skin%`, `%fpp_body%`, `%fpp_pushable%`, `%fpp_damageable%`, `%fpp_tab%`, `%fpp_max_health%`, `%fpp_version%`. Player-relative: `%fpp_user_count%` (bots owned by that player), `%fpp_user_max%` (personal limit), `%fpp_user_names%` (comma-joined names of that player's bots). `%fpp_real%` uses `Bukkit.getOnlinePlayers().size()` — bots are not Bukkit `Player` objects. `%fpp_total%` = `real + bots`.
- LuckPerms: detected for prefix formatting (`FakePlayerPlugin` defers detection 1 tick).
- Vercel / Node API: `UpdateChecker` falls back from Modrinth to `https://fake-player-plugin.vercel.app`; `frontend/api/check-update.js` and `frontend/api/status.js` intentionally emit Java-compatible keys such as `remoteVersion`, `version`, `version_number`, and `downloadUrl`.
- Metrics: `util/FppMetrics` wraps FastStats and is conditional via `config.yml` flag (`Config.metricsEnabled()`).

8) Useful repo search queries (quick semantic/grep targets)
- "onEnable(" — find startup flow
- "Config.init(" — find config usage
- "getCommand(\"fpp\")" — command wiring
- "registerEvents(" — listener registration
- "purgeOrphanedBodiesAndRestore(" — persistence restore
- "spawnFakePlayer(" or "PacketHelper" — low-level spawn
- "Lang.get(" — message rendering pattern
- "Perm." — permission check usages
- "FakePlayerBody.spawn" — entity spawning entry point
- "implements FppCommand" — all sub-command implementations
- "DatabaseManager" — database call sites
- "YamlFileSyncer.syncMissingKeys" — YAML auto-sync call sites (Lang, BotNameConfig, BotMessageConfig)
- "isOnCooldown" — spawn cooldown enforcement
- "updateBotPrefix(" or "refreshLpDisplayName(" — single-bot LP prefix refresh (called by finishSpawn and RankCommand)
- "implements FppCommand" or "RankCommand" — find rank/LP group assignment logic
- "VelocityChannel" — proxy plugin-messaging send/receive
- "RemoteBotCache" — remote bot tab-list entries from other servers
- "isNetworkMode" — NETWORK mode guard sites
- "ConfigSyncManager" — config sync push/pull

9) Where to read docs
- `docs/` — internal build/setup/release notes (`BUILD_INSTRUCTIONS.md`, `INTELLIJ_BUILD_SETUP.md`, `OBFUSCATION_CORRECTED.md`, `PROJECT_ORGANIZATION.md`, `RELEASE-1.4.23.md`, `SKIN-SYSTEM-FIX.md`); current plugin version is `1.5.0`, current `config-version` stamped by `ConfigMigrator.CURRENT_VERSION` is `32` — always use `pom.xml` as the source of truth because some docs still reference older versions.
- `wiki/` — developer-facing docs (Commands.md, Configuration.md, Getting-Started.md, Database.md, Language.md, Permissions.md, Skin-System.md, Swap-System.md, Bot-Behaviour.md, Bot-Messages.md, Bot-Names.md, Fake-Chat.md, FAQ.md, Migration.md, Home.md, Placeholders.md, Proxy-Support.md, Config-Sync.md)
- `README.md` — general usage and release notes

Contact / notes
- This file focuses on discoverable, actionable patterns only. For design rationale, see `wiki/Migration.md` and issues in the project tracker.

