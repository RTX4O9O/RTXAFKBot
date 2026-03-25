# AGENTS.md — Guide for AI coding agents

Purpose: help automated agents quickly understand, modify, and verify the FakePlayerPlugin codebase.

1) Quick repo map
- `src/main/java/me/bill/fakePlayerPlugin/` — main code and entry points
- `src/main/resources/` — default `config.yml`, `plugin.yml`, `bot-names.yml`, `bot-messages.yml`
- `src/main/resources/language/en.yml` — all user-facing messages (loaded by `Lang.java`)
- `pom.xml` — build + jar metadata (`target/fpp-*.jar` produced)
- `frontend/`, root `package.json`, and `vercel.json` — separate Node/Vercel status + update API sidecar used by the plugin’s update-check fallback; not part of the Maven plugin jar build.
- `wiki/` — feature docs (Commands.md, Configuration.md, Getting-Started.md, Database.md, Language.md, Permissions.md, Skin-System.md, Swap-System.md, Bot-Behaviour.md, Bot-Messages.md, Bot-Names.md, Fake-Chat.md, FAQ.md, Migration.md, Home.md)
- `src/main/java/me/bill/fakePlayerPlugin/util/` — helper subsystems and utilities (e.g. `FppMetrics`, `FppLogger`, `FppPlaceholderExpansion`, `TabListManager`, `BackupManager`, `UpdateChecker`, `ConfigValidator`, `DataMigrator`, `TextUtil`, `ConfigMigrator`, `LuckPermsHelper`, `CompatibilityChecker`, `YamlFileSyncer`).
- `src/main/java/me/bill/fakePlayerPlugin/listener/` — Bukkit event listeners (`PlayerJoinListener`, `FakePlayerEntityListener`, `LuckPermsUpdateListener`, `BotCollisionListener`, `PlayerWorldChangeListener`, `ServerListListener`).
- `src/main/java/me/bill/fakePlayerPlugin/fakeplayer/` — bot domain objects and subsystems (e.g. `FakePlayer`, `FakePlayerBody`, `FakePlayerManager`, `BotPersistence`, `ChunkLoader`, `SkinRepository`, `SkinFetcher`, `SkinProfile`, `NmsHelper`, `BotSwapAI`, `BotHeadAI`, `BotChatAI`, `BotBroadcast`, `PacketHelper`).
- `src/main/java/me/bill/fakePlayerPlugin/database/` — session history and analytics (`DatabaseManager`, `BotRecord`).
- `src/main/java/me/bill/fakePlayerPlugin/lang/` — message rendering (`Lang`).
- `src/main/java/me/bill/fakePlayerPlugin/permission/` — centralised permission registry (`Perm`).

2) Where to start (entry points)
- `FakePlayerPlugin.java` — plugin lifecycle (`onEnable` / `onDisable`) and orchestration.
- `command/CommandManager.java` and command classes (`SpawnCommand`, `DeleteCommand` (registers as `despawn`), `ListCommand`, `HelpCommand`, `InfoCommand`, `ChatCommand`, `SwapCommand`, `ReloadCommand`, `FreezeCommand`, `StatsCommand`, `MigrateCommand`, `TpCommand`, `TphCommand`, `LpInfoCommand`) — command wiring and tab completion.
- `command/FppCommand.java` — interface every sub-command implements (`getName`, `getUsage`, `getDescription`, `getPermission`, `canUse`, `execute`, `tabComplete`).
- `config/Config.java`, `util/ConfigMigrator.java` — configuration pattern and automatic migration.
- `fakeplayer/FakePlayerManager.java`, `fakeplayer/BotPersistence.java` — bot lifecycle, persistence and restore.
- `fakeplayer/FakePlayer.java` — domain object for a single active bot (name, UUID, profile, physicsEntity, nametagEntity, spawnLocation, displayName, skinName, frozen state).
- `fakeplayer/FakePlayerBody.java` — two-entity spawning/removal: Mannequin (physics + skin) + ArmorStand riding it (nametag). Entities tagged via PDC keys `FakePlayerBody.NAMETAG_PDC_VALUE` / `VISUAL_PDC_VALUE`.
- `fakeplayer/SkinProfile.java` — immutable snapshot of a resolved Mojang skin (base64 value + signature + source label).
- `config/BotNameConfig.java`, `config/BotMessageConfig.java` — bot name & message pools which are initialised in `onEnable`.
- `fakeplayer/SkinRepository.java` / `fakeplayer/SkinFetcher.java` — skin-loading subsystem (called during startup).
- `lang/Lang.java` — message rendering. Loads `language/en.yml`, supports named and positional placeholders. Use `Lang.get("key", "name", "value")` for an Adventure `Component` or `Lang.raw("key", ...)` for a raw string. Hot-reloadable via `/fpp reload`.
- `permission/Perm.java` — central permission registry. All permission nodes declared as constants (e.g. `Perm.SPAWN`, `Perm.DELETE`, `Perm.USER_SPAWN`). Use `Perm.has()`, `Perm.hasOrOp()`, `Perm.resolveUserBotLimit()` helpers. Never hard-code permission strings in commands.
- `database/DatabaseManager.java`, `database/BotRecord.java` — session history and analytics. Supports SQLite (default, WAL mode) and MySQL. Writes are serialised on a background thread; positions are batch-flushed on a configurable interval. Controlled by `Config.databaseEnabled()` and `Config.mysqlEnabled()`.
- `util/TabListManager.java`, `util/UpdateChecker.java` — tab header/footer management and async update checker initialized during startup.

3) Key patterns & concrete examples (copy-ready pointers)
- Migration runs before config init: `ConfigMigrator.migrateIfNeeded(this)` in `onEnable` (see `FakePlayerPlugin.java`).
- Config usage: call `Config.init(this)` then use accessors like `Config.fakeChatEnabled()` in code. Tab-list visibility accessor: `Config.tabListEnabled()` (key `tab-list.enabled`, default `true`) — when `false`, bots are never added to any player's tab list; the 20-tick display-name refresh loop, `syncToPlayer`, `resendTab` in `FakePlayerBody`, and the respawn re-add in `FakePlayerEntityListener` all check this flag before sending any `sendTabListAdd` packets. Toggling via `/fpp reload` calls `FakePlayerManager.applyTabListConfig()` which immediately removes or re-adds all active bots' tab entries for all online players.
- Language messages: always use `Lang.get("key")` (returns Adventure `Component`) or `Lang.raw("key")` (returns `String`) — never hard-code user-facing text. Named placeholders: `Lang.get("freeze-frozen", "name", fp.getDisplayName())`. All keys live in `src/main/resources/language/en.yml`.
- Permission checks: use `Perm.has(sender, Perm.SPAWN)` or `Perm.hasOrOp(sender, Perm.ALL)` — never hard-code node strings. Per-user bot limits resolved via `Perm.resolveUserBotLimit(sender)` (scans `fpp.bot.1` … `fpp.bot.100`; returns -1 if none set, caller falls back to `Config.userBotLimit()`).
- Command registration: plugin registers commands via `commandManager.register(new SpawnCommand(...))`; `HelpCommand` is auto-registered inside `CommandManager`, and `/fpp help` reads live from the registered command list, so new sub-commands need no separate help-menu wiring. The plugin then calls `getCommand("fpp").setExecutor(commandManager)` (see `onEnable`).
- Listener registration: `getServer().getPluginManager().registerEvents(new PlayerJoinListener(this, fakePlayerManager), this)` — listeners receive plugin + manager references.
- Persistence lifecycle: restore on enable via `botPersistence.purgeOrphanedBodiesAndRestore(fakePlayerManager)` and save on disable via `botPersistence.save(fakePlayerManager.getActivePlayers())`.
- Metrics & soft-deps: metrics are initialized with `FppMetrics` when `Config.metricsEnabled()`; PlaceholderAPI expansion registered conditionally in `onEnable`.
- Compatibility checks: `CompatibilityChecker.check()` (in `util/CompatibilityChecker.java`) runs at startup and returns an immutable `Result` (Paper server detection + Minecraft minimum version `1.21.9` + Mannequin class presence). When unsupported the plugin sets `compatibilityRestricted = true` and disables chunk loading, physical bodies and some listeners / AI (`BotHeadAI`, Mannequin-dependent listeners) — look for `compatibilityRestricted` and `compatibilityWarningMessage` in `FakePlayerPlugin.java`. `Result.failureLangKeys` lists per-failure lang keys. `CompatibilityChecker.buildWarningComponent(result)` produces the in-game admin warning.
- Bot pools: `BotNameConfig.init(this)` and `BotMessageConfig.init(this)` are called during startup — the plugin loads `bot-names.yml` and `bot-messages.yml` early.
- Metrics / FastStats packaging: `FppMetrics` is initialized before the startup banner to reflect metrics status. FastStats jars are bundled under `src/main/resources/faststats/` (the POM treats them as binary resources, not shaded). Note: building requires JDK 21 (see `pom.xml` <java.version>21).</p>
- Entity stack: each bot is two entities — a `Mannequin` (physics body + skin, tagged `FakePlayerBody.VISUAL_PDC_VALUE`) with an invisible `ArmorStand` riding it (nametag, tagged `FakePlayerBody.NAMETAG_PDC_VALUE`). Use these PDC values to identify bot entities during event handling.
- Database gate: check `Config.databaseEnabled()` before any `DatabaseManager` call; `databaseManager` may be `null` at runtime (disabled or failed to init). `databaseManager.recordAllShutdown()` must be called in `onDisable` before `databaseManager.close()`.
- Text formatting: `TextUtil.colorize(string)` parses MiniMessage tags and legacy `&`/`§` codes into an Adventure `Component`. `TextUtil.format(string)` additionally applies small-caps Unicode conversion. **Supports all modern color formats:** MiniMessage tags (`<rainbow>`, `<gradient:#FF0000:#0000FF>`), hex colors (`<#9782ff>`), LuckPerms gradient shorthand (`{#fffff>}text{#00000<}`), and mixed formats (`&7[<#9782ff>Phantom</#9782ff>&7]`).
- LuckPerms integration: `LuckPermsHelper` resolves bot prefixes and weights from LP groups. Fully supports all LuckPerms color formats including gradients, rainbow, hex, and legacy codes. Call `LuckPermsHelper.getBotLpData(ownerUuid)` to get prefix + weight, or `LuckPermsHelper.invalidateCache()` on reload. Tab ordering and packet-name behaviour are controlled via `Config.luckpermsWeightOrderingEnabled()`, `Config.luckpermsBotGroup()`, `Config.luckpermsPacketPrefixChar()`, and `Config.luckpermsWeightOffset()`. Use `/fpp lpinfo [bot-name]` as the in-game diagnostic command for prefix / weight / ordering issues. **Auto-update feature:** `LuckPermsUpdateListener` automatically updates all bot prefixes in real-time when LuckPerms group data changes (no reload/respawn needed). Updates bot nametags, tab-list entries, and ordering instantly.
  - **Tab-list ordering model:** `LuckPermsHelper.buildPacketProfileName(weight, name)` produces a hidden packet profile name like `{00_BotName`. The prefix character (`packet-prefix-char`, default `{` ASCII 123) pushes the entire bot section after all real player names (a–z). When `packet-prefix-char` is empty the plugin falls back to `~` automatically — never leave it blank if you want bots at the bottom. **Rank ordering within the bot section:** Higher LP weight → lower rank index → appears HIGHER in bot section. Example: Admin bot (weight=100) gets rank=00 (`{00_AdminBot`), default bot (weight=1) gets rank=02 (`{02_DefaultBot`). This matches standard LuckPerms ordering where higher weight = higher priority. `LpData.weight()` returned by `getBotLpData` is the **original unpenalised** LP group weight. When LP data changes, `updateAllBotPrefixes()` sends `sendTabListRemove` + `sendTabListAdd` (not just `UPDATE_DISPLAY_NAME`) so the client correctly repositions the entry in the tab list. **Scoreboard team override:** All bots are placed in the `~fpp` scoreboard team (managed by `BotTabTeam`) which sorts AFTER all other teams, ensuring bots always appear below real players regardless of their individual rank.
- Listener registration: `getServer().getPluginManager().registerEvents(new PlayerJoinListener(this, fakePlayerManager), this)` — listeners receive plugin + manager references. LuckPerms listener registered conditionally via `luckPermsUpdateListener.register()` when LP is detected.
- `onDisable` sequence: save persistence → `chunkLoader.releaseAll()` → `botSwapAI.cancelAll()` → `luckPermsUpdateListener.unregister()` → `fakePlayerManager.removeAllSync()` → `tabListManager.shutdown()` → `databaseManager.recordAllShutdown()` + `close()` → `fppMetrics.shutdown()`.
- Update notifications to late-joining admins: stored as a `Component` on `FakePlayerPlugin.getUpdateNotification()` / `setUpdateNotification()`; `PlayerJoinListener` delivers it to players with `Perm.ALL`.
- YAML file sync: `YamlFileSyncer.syncMissingKeys(plugin, diskRelPath, jarResourcePath)` — non-destructive; adds keys present in the JAR but absent from disk, never overwrites user values. Returns a `SyncResult` record (`fileName`, `keysAdded`, `hasChanges()`, `count()`). Called automatically inside `Lang.reload()`, `BotNameConfig.reload()`, and `BotMessageConfig.reload()` on every startup and `/fpp reload`. Force-run from the command line with `/fpp migrate lang`, `/fpp migrate names`, or `/fpp migrate messages`. When adding new keys to `en.yml`, `bot-names.yml`, or `bot-messages.yml`, they will be automatically synced to existing user files on next reload — no manual migration step needed.
- Spawn cooldown: `Config.spawnCooldown()` (key `spawn-cooldown`, default `0` = disabled). Applies to both admin and user `/fpp spawn`. Players with `Perm.BYPASS_COOLDOWN` (`fpp.bypass.cooldown`) are exempt. Cooldown state lives in `FakePlayerManager.spawnCooldowns` (per-UUID map). Helpers: `manager.isOnCooldown(uuid)`, `manager.getRemainingCooldown(uuid)` (seconds), `manager.recordSpawnCooldown(uuid)`.
- Backup variants: `BackupManager.createFullBackup(plugin, reason)` — backs up all plugin files including SQLite; used by `ConfigMigrator` before migrations. `BackupManager.createConfigFilesBackup(plugin, reason)` — backs up YAML files only (`config.yml`, `bot-names.yml`, `bot-messages.yml`, `language/`); used before lightweight sync operations (e.g. `/fpp migrate lang`). Both methods prune to the most recent `MAX_BACKUPS` (10) automatically.
- Additional `Perm` helpers: `Perm.missing(sender, node)` returns `true` when the sender lacks the node (negation of `has()`); `Perm.hasAny(sender, node1, node2, ...)` returns `true` when the sender has at least one of the listed nodes.
- Physical-body API: `FakePlayerManager.physicalBodiesEnabled()` — returns `Config.spawnBody() && !plugin.isCompatibilityRestricted()`. Use this instead of checking both conditions inline when deciding whether Mannequin bodies are active.

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
- To reproduce startup ordering bugs, inspect `onEnable()` in `FakePlayerPlugin.java` — migration → config → Lang → BotNameConfig/BotMessageConfig → ensureDataDirectories → SkinRepository → compatibility check → database → FakePlayerManager+subsystems → commands → listeners → periodic health check (6000 ticks) → ConfigValidator → TabListManager → PlaceholderAPI → LuckPerms (deferred 1 tick) → UpdateChecker (purely async, never blocks startup) → Metrics → startup banner → persistence restore.
- To debug config migrations, read `util/ConfigMigrator.java` and check backups under `plugins/FakePlayerPlugin/backups/` created by `BackupManager`.
- For packet-level issues (spawn / entity problems) inspect `fakeplayer/PacketHelper.java` and `fakeplayer/FakePlayerManager.java`.
- For database issues: SQLite file is at `plugins/FakePlayerPlugin/data/fpp.db`; run `/fpp migrate status` to check schema version, YAML sync health, and backup count. Full `/fpp migrate` subcommands: `backup` (create manual backup), `backups` (list stored backups), `status` (config version + file sync health + DB stats), `config` (re-run config migration chain), `lang` / `names` / `messages` (force-sync the respective YAML file from JAR), `db merge [file]` / `db export` / `db tomysql`. Set `database.enabled: false` in config to disable all DB I/O.
- Startup compatibility checks (Paper / MC version / Mannequin) can set `compatibilityRestricted` and silently skip chunk-loading & some listeners — inspect `FakePlayerPlugin.onEnable()` for `compatibilityRestricted` usage and the generated `compatibilityWarningMessage`.
- Tab list header/footer and update checker: `TabListManager.start()` and `UpdateChecker.check(this)` are run during `onEnable()` — useful when diagnosing missing tab headers or unexpected update-checker output.

7) Integration & soft-dependencies
- PlaceholderAPI: `util.FppPlaceholderExpansion` registered if plugin present.
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

9) Where to read docs
- `docs/` — internal build/setup/release notes (`BUILD_INSTRUCTIONS.md`, `INTELLIJ_BUILD_SETUP.md`, `OBFUSCATION_CORRECTED.md`, `PROJECT_ORGANIZATION.md`, `RELEASE-1.4.23.md`); current plugin version is `1.4.26` — always use `pom.xml` as the source of truth because some docs still reference older versions.
- `wiki/` — developer-facing docs (Commands.md, Configuration.md, Getting-Started.md, Database.md, Language.md, Permissions.md, Skin-System.md, Swap-System.md, Bot-Behaviour.md, Bot-Messages.md, Bot-Names.md, Fake-Chat.md, FAQ.md, Migration.md, Home.md)
- `README.md` — general usage and release notes

Contact / notes
- This file focuses on discoverable, actionable patterns only. For design rationale, see `wiki/Migration.md` and issues in the project tracker.

