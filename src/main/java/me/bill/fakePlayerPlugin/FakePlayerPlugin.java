package me.bill.fakePlayerPlugin;

import me.bill.fakePlayerPlugin.command.*;
import me.bill.fakePlayerPlugin.gui.SettingGui;
import me.bill.fakePlayerPlugin.config.BotMessageConfig;
import me.bill.fakePlayerPlugin.config.BotNameConfig;
import me.bill.fakePlayerPlugin.config.Config;
import me.bill.fakePlayerPlugin.database.DatabaseManager;
import me.bill.fakePlayerPlugin.fakeplayer.BotChatAI;
import me.bill.fakePlayerPlugin.fakeplayer.BotPersistence;
import me.bill.fakePlayerPlugin.fakeplayer.ChunkLoader;
import me.bill.fakePlayerPlugin.fakeplayer.FakePlayerManager;
import me.bill.fakePlayerPlugin.lang.Lang;
import me.bill.fakePlayerPlugin.listener.BotCollisionListener;
import me.bill.fakePlayerPlugin.listener.FakePlayerEntityListener;
import me.bill.fakePlayerPlugin.listener.FakePlayerKickListener;
import me.bill.fakePlayerPlugin.listener.PlayerJoinListener;
import me.bill.fakePlayerPlugin.listener.PlayerWorldChangeListener;
import me.bill.fakePlayerPlugin.listener.ServerListListener;
import me.bill.fakePlayerPlugin.messaging.VelocityChannel;
import me.bill.fakePlayerPlugin.util.BackupManager;
import me.bill.fakePlayerPlugin.util.BotTabTeam;
import me.bill.fakePlayerPlugin.util.CompatibilityChecker;
import me.bill.fakePlayerPlugin.util.ConfigMigrator;
import me.bill.fakePlayerPlugin.util.ConfigValidator;
import me.bill.fakePlayerPlugin.util.FppLogger;
import me.bill.fakePlayerPlugin.util.FppMetrics;
import me.bill.fakePlayerPlugin.util.TabListManager;
import me.bill.fakePlayerPlugin.util.UpdateChecker;
import org.bukkit.Bukkit;
import org.bukkit.plugin.java.JavaPlugin;
import net.kyori.adventure.text.Component;

public final class FakePlayerPlugin extends JavaPlugin {

    private static FakePlayerPlugin instance;
    @SuppressWarnings("unused")
    public static FakePlayerPlugin getInstance() { return instance; }

    private CommandManager    commandManager;
    private FakePlayerManager fakePlayerManager;
    private ChunkLoader       chunkLoader;
    private DatabaseManager   databaseManager;
    private BotPersistence    botPersistence;
    private FppMetrics        fppMetrics;
    private TabListManager    tabListManager;
    private BotTabTeam        botTabTeam;
    private VelocityChannel   velocityChannel;
    private BotChatAI         botChatAI;
    private me.bill.fakePlayerPlugin.fakeplayer.BotSwapAI botSwapAI;
    private me.bill.fakePlayerPlugin.fakeplayer.PeakHoursManager peakHoursManager;
    private me.bill.fakePlayerPlugin.fakeplayer.RemoteBotCache remoteBotCache;
    private me.bill.fakePlayerPlugin.sync.ConfigSyncManager configSyncManager;
    private SettingGui settingGui;
    private me.bill.fakePlayerPlugin.fakeplayer.BotIdentityCache botIdentityCache;

    /** Update notification Component stored when an update is detected so it can be
     * delivered to admins who log in after startup. */
    private Component updateNotificationMessage = null;

    /**
     * Cached flag: whether LuckPerms is installed and enabled on this server.
     * Set once in {@code onEnable()} via a pure Bukkit check (no LP class loading)
     * so that {@code LuckPermsHelper} is never loaded when LP is absent — prevents
     * {@code NoClassDefFoundError} from LP-API classes that aren't on the classpath.
     */
    private boolean luckPermsAvailable = false;

    /** @return {@code true} when LuckPerms is installed and enabled on this server. */
    public boolean isLuckPermsAvailable() { return luckPermsAvailable; }

    /**
     * Cached flag: whether WorldGuard is installed and enabled on this server.
     * Set once in {@code onEnable()} via a pure Bukkit check (no WG class loading)
     * so that {@link me.bill.fakePlayerPlugin.util.WorldGuardHelper} is never loaded
     * when WorldGuard is absent — prevents {@code NoClassDefFoundError} from WG/WE
     * API classes that aren't on the classpath.
     */
    private boolean worldGuardAvailable = false;

    /** @return {@code true} when WorldGuard is installed and enabled on this server. */
    public boolean isWorldGuardAvailable() { return worldGuardAvailable; }

    /**
     * {@code true} when the server is running a Minecraft version newer than the
     * maximum tested version (1.21.11). Set once in {@code onEnable()}.
     * When {@code true} all /fpp sub-commands are disabled and a persistent
     * warning is shown to operators on join.
     */
    private boolean versionUnsupported = false;

    /** The Minecraft version string detected at startup, e.g. {@code "1.22.0"}. */
    private String detectedMcVersion = "unknown";

    /** @return {@code true} if the server is running an unsupported MC version (&gt; 1.21.11). */
    public boolean isVersionUnsupported() { return versionUnsupported; }

    /** @return The Minecraft version string detected at startup, e.g. {@code "1.21.11"}. */
    public String getDetectedMcVersion() { return detectedMcVersion; }

    /** System.currentTimeMillis() captured at the start of onEnable. */
    private long enabledAt;

    @Override
    public void onEnable() {
        instance  = this;
        enabledAt = System.currentTimeMillis();
        FppLogger.init(getLogger());

        // ── Migration (runs before Config.init so it operates on raw YAML) ────
        // Ensures config.yml is upgraded from any previous version, adds missing
        // keys from defaults, and creates a backup if any changes are applied.
        ConfigMigrator.migrateIfNeeded(this);

        // ── Config ────────────────────────────────────────────────────────────
        Config.init(this);
        Config.debugStartup("config.yml loaded.");

        Lang.init(this);
        Config.debugStartup("Language file loaded (lang=" + Config.getLanguage() + ").");

        // ── Version compatibility gate ────────────────────────────────────────
        // FPP is tested up to MC 1.21.11. Any newer version is flagged as unsupported:
        // all /fpp sub-commands are disabled and a warning is broadcast to operators.
        detectedMcVersion = CompatibilityChecker.extractMcVersion();
        if (CompatibilityChecker.isVersionAtLeast(detectedMcVersion, "1.21.12")) {
            versionUnsupported = true;
            String pv = getPluginMeta().getVersion();
            FppLogger.warn("═══════════════════════════════════════════════════════════════════");
            FppLogger.warn("  ⚠  FakePlayerPlugin — UNSUPPORTED MINECRAFT VERSION  ⚠");
            FppLogger.warn("═══════════════════════════════════════════════════════════════════");
            FppLogger.warn("  Plugin    : FakePlayerPlugin v" + pv);
            FppLogger.warn("  Server MC : " + detectedMcVersion + "  (NOT supported)");
            FppLogger.warn("  Supported : up to MC 1.21.11");
            FppLogger.warn("  Action    : All /fpp commands have been DISABLED.");
            FppLogger.warn("  Support   : If you think this is a bug, contact us:");
            FppLogger.warn("              Discord → https://discord.gg/RfjEJDG2TM");
            FppLogger.warn("═══════════════════════════════════════════════════════════════════");
        }

        BotNameConfig.init(this);
        Config.debugStartup("Bot name pool: " + BotNameConfig.getNames().size() + " names.");

        BotMessageConfig.init(this);
        Config.debugStartup("Bot message pool: " + BotMessageConfig.getMessages().size() + " messages.");

        // Ensure plugin data directories exist regardless of config settings
        ensureDataDirectories();

        // ── Skin repository ───────────────────────────────────────────────────
        // Must be initialised before any bot spawns (including persistence restore)
        // so that SkinRepository.deliver() can dispatch skin callbacks to the
        // main thread instead of running them on the FPP-SkinFetcher async thread.
        me.bill.fakePlayerPlugin.fakeplayer.SkinRepository.get().init(this);
        Config.debugStartup("SkinRepository initialised.");

        // ── Database ──────────────────────────────────────────────────────────
        boolean dbOk = false;
        if (Config.databaseEnabled()) {
            databaseManager = new DatabaseManager();
            dbOk = databaseManager.init(getDataFolder());
            if (!dbOk) {
                FppLogger.warn("Database could not be initialised — session tracking disabled.");
                databaseManager = null;
            } else {
                // Log database mode and server ID
                String mode = Config.databaseMode();
                String serverId = Config.serverId();
                Config.debugDatabase("Database mode: " + mode + " | server-id=" + serverId);
            }
        } else {
            Config.debugDatabase("Database disabled in config — skipping database initialisation.");
            databaseManager = null;
        }

        // ── Bot identity cache ────────────────────────────────────────────────
        // Maps (bot_name, server_id) → stable UUID so bots rejoin with the same UUID
        // every time.  Uses the database when available; falls back to a local YAML file.
        botIdentityCache = new me.bill.fakePlayerPlugin.fakeplayer.BotIdentityCache(this, databaseManager);
        Config.debugDatabase("BotIdentityCache initialised (backend=" +
                (databaseManager != null ? (Config.mysqlEnabled() ? "MySQL" : "SQLite") : "YAML") + ").");

        // ── Remote bot cache ──────────────────────────────────────────────────
        // Holds virtual tab-list entries for bots running on other proxy servers.
        // Always initialised (even in LOCAL mode) so the rest of the code never
        // has to null-check it.
        remoteBotCache = new me.bill.fakePlayerPlugin.fakeplayer.RemoteBotCache();
        if (Config.isNetworkMode() && databaseManager != null) {
            // Pre-populate from the shared DB so players already online see remote
            // bots immediately.  Skin data is not stored in the DB, so these entries
            // will be skin-less until the originating server sends a BOT_SPAWN message.
            java.util.List<me.bill.fakePlayerPlugin.database.DatabaseManager.ActiveBotRow> remoteRows =
                    databaseManager.getActiveBotsFromOtherServers();
            for (var row : remoteRows) {
                try {
                    java.util.UUID uuid = java.util.UUID.fromString(row.botUuid());
                    String display = (row.botDisplay() != null && !row.botDisplay().isBlank())
                            ? row.botDisplay() : row.botName();
                    remoteBotCache.add(new me.bill.fakePlayerPlugin.fakeplayer.RemoteBotEntry(
                            row.serverId(), uuid, row.botName(), display,
                            row.botName(), "", ""));   // no skin from DB
                } catch (Exception ignored) {}
            }
            Config.debugNetwork("Remote bot cache pre-populated from DB: "
                    + remoteBotCache.count() + " bot(s).");
        }

        // ── Config Sync Manager ───────────────────────────────────────────────
        // Initialize config sync if in NETWORK mode with database available
        if (Config.isNetworkMode() && databaseManager != null) {
            configSyncManager = new me.bill.fakePlayerPlugin.sync.ConfigSyncManager(this, databaseManager);
            configSyncManager.init();
            Config.debugConfigSync("Config sync manager initialized (mode=" + Config.configSyncMode() + ").");
        } else {
            configSyncManager = null;
        }

        // ── Fake player manager + chunk loader ────────────────────────────────
        fakePlayerManager = new FakePlayerManager(this);
        if (databaseManager != null) fakePlayerManager.setDatabaseManager(databaseManager);
        fakePlayerManager.setIdentityCache(botIdentityCache);

        chunkLoader = new ChunkLoader(this, fakePlayerManager);
        fakePlayerManager.setChunkLoader(chunkLoader);

        botPersistence = new BotPersistence(this);
        fakePlayerManager.setBotPersistence(botPersistence);


        // ── Commands ──────────────────────────────────────────────────────────
        commandManager = new CommandManager(this);
        commandManager.register(new SpawnCommand(fakePlayerManager));
        commandManager.register(new DeleteCommand(fakePlayerManager));
        commandManager.register(new ListCommand(this, fakePlayerManager));
        commandManager.register(new TphCommand(fakePlayerManager));
        commandManager.register(new TpCommand(fakePlayerManager));
        commandManager.register(new ChatCommand(this));
        commandManager.register(new ReloadCommand(this));
        commandManager.register(new InfoCommand(databaseManager, fakePlayerManager));
        commandManager.register(new MigrateCommand(this));
        commandManager.register(new StatsCommand(fakePlayerManager, databaseManager));
        commandManager.register(new FreezeCommand(fakePlayerManager));
        commandManager.register(new LpInfoCommand(this, fakePlayerManager));
        commandManager.register(new RankCommand(this, fakePlayerManager));
        commandManager.register(new AlertCommand(this));
        commandManager.register(new SyncCommand(this));
        commandManager.register(new SwapCommand(this, fakePlayerManager));
        commandManager.register(new PeaksCommand(this, fakePlayerManager));
        // Settings GUI — create once, register as listener, share with command
        settingGui = new SettingGui(this);
        commandManager.register(new SettingCommand(settingGui));
        Config.debugStartup("Commands registered: " + commandManager.getCommands().size() + " total.");

        var fppCmd = getCommand("fpp");
        if (fppCmd != null) {
            fppCmd.setExecutor(commandManager);
            fppCmd.setTabCompleter(commandManager);
        }

        // ── Listeners ─────────────────────────────────────────────────────────
        getServer().getPluginManager().registerEvents(new PlayerJoinListener(this, fakePlayerManager), this);
        getServer().getPluginManager().registerEvents(new PlayerWorldChangeListener(this, fakePlayerManager), this);
        getServer().getPluginManager().registerEvents(new FakePlayerEntityListener(this, fakePlayerManager, chunkLoader), this);
        getServer().getPluginManager().registerEvents(new BotCollisionListener(this, fakePlayerManager), this);

        getServer().getPluginManager().registerEvents(new ServerListListener(fakePlayerManager), this);
        getServer().getPluginManager().registerEvents(new FakePlayerKickListener(fakePlayerManager), this);
        getServer().getPluginManager().registerEvents(new me.bill.fakePlayerPlugin.listener.BotCommandBlocker(), this);
        getServer().getPluginManager().registerEvents(new me.bill.fakePlayerPlugin.listener.BotSpawnProtectionListener(this), this);
        getServer().getPluginManager().registerEvents(settingGui, this);
        botChatAI = new BotChatAI(this, fakePlayerManager);
        botSwapAI = new me.bill.fakePlayerPlugin.fakeplayer.BotSwapAI(this, fakePlayerManager);
        fakePlayerManager.setBotSwapAI(botSwapAI);
        peakHoursManager = new me.bill.fakePlayerPlugin.fakeplayer.PeakHoursManager(this, fakePlayerManager);
        // Inject DB so sleeping-bot state survives crashes (crash-safe persistence).
        if (databaseManager != null) {
            peakHoursManager.setDatabaseManager(databaseManager);
        }
        if (Config.peakHoursEnabled() && Config.swapEnabled()) {
            peakHoursManager.start();
        }

        // ── Plugin messaging (Velocity / BungeeCord) ─────────────────────────
        // Registers fpp:main for inbound delivery and BungeeCord for outbound
        // "Forward ALL" packets so the proxy re-delivers messages to every server.
        velocityChannel = new VelocityChannel(this, fakePlayerManager);
        getServer().getMessenger().registerOutgoingPluginChannel(this, VelocityChannel.CHANNEL);
        getServer().getMessenger().registerOutgoingPluginChannel(this, "BungeeCord");
        getServer().getMessenger().registerIncomingPluginChannel(this, VelocityChannel.CHANNEL, velocityChannel);
        Config.debugNetwork("Plugin messaging channels registered: " + VelocityChannel.CHANNEL + " + BungeeCord.");

        // ── Periodic entity health check + orphan sweep (every 5 min) ─────────
        getServer().getScheduler().runTaskTimer(this, () -> {
            if (fakePlayerManager.getCount() > 0) {
                fakePlayerManager.validateEntities();
            }
        }, 6000L, 6000L);

        // ── Config validation ─────────────────────────────────────────────────
        int configIssues = ConfigValidator.validate();
        if (configIssues > 0) {
            FppLogger.warn("Config validation found " + configIssues + " issue(s) — see above.");
        }

        // ── Tab list header/footer ────────────────────────────────────────────
        tabListManager = new TabListManager(this, fakePlayerManager);
        tabListManager.start();

        // ── Bot scoreboard team ───────────────────────────────────────────────
        // Places all bots in the ~fpp scoreboard team so they sort below real players
        // in every player's tab list (team ~fpp sorts after all letter/digit teams).
        try {
            botTabTeam = new BotTabTeam();
            botTabTeam.init();
            fakePlayerManager.setBotTabTeam(botTabTeam);
            Config.debugPackets("Bot tab team initialized (~fpp).");
        } catch (Exception e) {
            FppLogger.warn("Bot tab team init failed — " + e.getMessage());
        }

        // ── PlaceholderAPI soft-dependency ────────────────────────────────────
        if (Bukkit.getPluginManager().getPlugin("PlaceholderAPI") != null) {
            try {
                new me.bill.fakePlayerPlugin.util.FppPlaceholderExpansion(this, fakePlayerManager).register();
                Config.debugStartup("PlaceholderAPI detected — placeholders registered.");
            } catch (Exception e) {
                FppLogger.warn("PlaceholderAPI: failed to register expansion — " + e.getMessage());
            }
        }

        // ── LuckPerms soft-dependency ─────────────────────────────────────────
        // Bots are real NMS ServerPlayer entities — LP auto-detects them as players.
        // We just log whether LP is available and apply the configured default group.
        // luckPermsAvailable is cached here so that LuckPermsHelper is NEVER loaded
        // when LP is absent — loading it without LP on the classpath throws
        // NoClassDefFoundError because Paper's PluginClassLoader eagerly resolves
        // LP API class references during bytecode verification.
        boolean luckPermsInstalled = Bukkit.getPluginManager().getPlugin("LuckPerms") != null;
        luckPermsAvailable = luckPermsInstalled;
        if (luckPermsInstalled) {
            Config.debugLuckPerms("LuckPerms detected — bot group sync enabled.");
            String defaultGroup = me.bill.fakePlayerPlugin.config.Config.luckpermsDefaultGroup();
            if (!defaultGroup.isBlank()) {
                Config.debugLuckPerms("default-group='" + defaultGroup + "' will be applied to new bots at spawn.");
            }
            // Subscribe to LP UserDataRecalculateEvent for auto-refresh when LP group changes
            me.bill.fakePlayerPlugin.util.LuckPermsHelper.subscribeLpEvents(this, fakePlayerManager);
        }

        // ── WorldGuard soft-dependency ─────────────────────────────────────────
        // worldGuardAvailable is cached here so WorldGuardHelper is NEVER loaded
        // when WorldGuard is absent — prevents NoClassDefFoundError from WG/WE API
        // classes that aren't on the classpath (same guard as LuckPerms above).
        worldGuardAvailable = Bukkit.getPluginManager().getPlugin("WorldGuard") != null;
        if (worldGuardAvailable) {
            Config.debugStartup("WorldGuard detected — bot PvP region protection enabled.");
        }

        // ── Update checker — purely async, never blocks startup ───────────────
        UpdateChecker.check(this);

        // ── Metrics (FastStats) — init before banner so status is known ───────
        fppMetrics = new FppMetrics();
        if (Config.metricsEnabled()) {
            try {
                fppMetrics.init(this, fakePlayerManager);
            } catch (Throwable t) {
                FppLogger.error("Metrics: unexpected top-level error — " + t.getClass().getName() + ": " + t.getMessage());
                for (StackTraceElement el : t.getStackTrace()) {
                    FppLogger.error("  at " + el);
                }
            }
        } else {
            Config.debugStartup("Metrics disabled in config.yml — skipping FastStats init.");
        }

        String dbLabel = databaseManager == null ? "none"
                : Config.mysqlEnabled()          ? "MySQL"
                                                 : "SQLite (local)";
        String dbState = !Config.databaseEnabled()
                ? "disabled"
                : (dbOk ? dbLabel : dbLabel + " (failed)");

        String skinLabel = Config.skinMode();

        boolean effectiveSpawnBody    = Config.spawnBody();
        boolean effectiveChunkLoading = Config.chunkLoadingEnabled();

        // Compute banner metadata before printing
        long   startupMs    = System.currentTimeMillis() - enabledAt;
        int    cfgVer       = Config.configVersion();
        String configVersion = "v" + cfgVer
                + (cfgVer >= ConfigMigrator.CURRENT_VERSION ? " ✔" : " (migrated)");
        int backupCount = BackupManager.listBackups(this).size();

        FppLogger.printStartupBanner(
                getPluginMeta().getVersion(),
                String.join(", ", getPluginMeta().getAuthors()),
                BotNameConfig.getNames().size(),
                BotMessageConfig.getMessages().size(),
                dbState,
                skinLabel,
                effectiveSpawnBody,
                Config.persistOnRestart(),
                luckPermsInstalled,
                Config.fakeChatEnabled(),
                Config.swapEnabled(),
                Config.peakHoursEnabled(),
                effectiveChunkLoading,
                Config.maxBots(),
                fppMetrics.isActive(),
                configVersion,
                backupCount,
                startupMs
        );


        // ── Bot persistence restore ───────────────────────────────────────────
        botPersistence.purgeOrphanedBodiesAndRestore(fakePlayerManager);

        // ── Peak-hours crash recovery: restore sleeping bots from DB ─────────
        // This is a lightweight deque-population (no entity spawning), safe to run
        // synchronously here. Worlds are loaded; the peak-hours tick has not yet fired.
        if (databaseManager != null && Config.persistOnRestart()) {
            peakHoursManager.restoreSleepingBotsFromDatabase(databaseManager);
        }

        Config.debugStartup("onEnable complete.");
    }

    @Override
    public void onDisable() {
        Config.debugStartup("onDisable called.");

        int botsRemoved = fakePlayerManager != null ? fakePlayerManager.getCount() : 0;

        if (chunkLoader     != null) chunkLoader.releaseAll();
        // Cancel all pending BotChatAI tasks
        if (botChatAI       != null) botChatAI.cancelAll();
        // Shut down peak-hours FIRST — wakes all sleeping bots and clears the DB sleeping table.
        // They are added back into fakePlayerManager.activePlayers so persistence captures them below.
        if (peakHoursManager != null) peakHoursManager.shutdown();
        // Cancel all pending BotSwapAI tasks
        if (botSwapAI       != null) botSwapAI.cancelAll();

        // Save active bots AFTER peak-hours woke sleeping bots, so the persistence
        // file includes the full pool (online + formerly sleeping).
        if (botPersistence != null && fakePlayerManager != null) {
            if (Config.persistOnRestart()) {
                Config.debugStartup("Saving " + fakePlayerManager.getCount() + " bot(s) for persistence...");
                botPersistence.save(fakePlayerManager.getActivePlayers());
            }
        }
        // Unsubscribe from LP events to prevent memory leaks (only if LP was loaded)
        if (luckPermsAvailable) {
            me.bill.fakePlayerPlugin.util.LuckPermsHelper.unsubscribeLpEvents();
        }
        // removeAllSync sends leave messages + cleans up entities
        if (fakePlayerManager != null) fakePlayerManager.removeAllSync();
        // Shut down tab list header/footer
        if (tabListManager != null) tabListManager.shutdown();
        // Destroy bot tab team
        if (botTabTeam != null) botTabTeam.destroy();

        // Flush DB — mark all open sessions as SHUTDOWN before closing
        boolean dbFlushed = false;
        if (databaseManager != null) {
            databaseManager.recordAllShutdown();
            databaseManager.close();
            dbFlushed = true;
        }

        // ── Metrics shutdown ──────────────────────────────────────────────────
        if (fppMetrics != null) fppMetrics.shutdown();


        // ── Plugin messaging cleanup ──────────────────────────────────────────
        getServer().getMessenger().unregisterIncomingPluginChannel(this, VelocityChannel.CHANNEL);
        getServer().getMessenger().unregisterOutgoingPluginChannel(this, VelocityChannel.CHANNEL);
        getServer().getMessenger().unregisterOutgoingPluginChannel(this, "BungeeCord");

        long uptimeMs = System.currentTimeMillis() - enabledAt;
        FppLogger.printShutdownBanner(botsRemoved, dbFlushed, uptimeMs);
    }

    @SuppressWarnings("unused") // Public API — available for addons
    public CommandManager    getCommandManager()    { return commandManager; }
    @SuppressWarnings("unused")
    public FakePlayerManager getFakePlayerManager() { return fakePlayerManager; }
    public DatabaseManager   getDatabaseManager()   { return databaseManager; }
    public TabListManager    getTabListManager()     { return tabListManager; }
    public BotTabTeam        getBotTabTeam()         { return botTabTeam; }
    /** Returns the Velocity plugin-messaging channel handler, or {@code null} if not yet initialised. */
    public VelocityChannel   getVelocityChannel()   { return velocityChannel; }
    /** Returns the BotChatAI instance, or {@code null} if not yet initialised. */
    public BotChatAI         getBotChatAI()          { return botChatAI; }
    /** Returns the BotSwapAI instance, or {@code null} if not yet initialised. */
    public me.bill.fakePlayerPlugin.fakeplayer.BotSwapAI getBotSwapAI() { return botSwapAI; }
    /** Returns the PeakHoursManager instance, or {@code null} if not yet initialised. */
    public me.bill.fakePlayerPlugin.fakeplayer.PeakHoursManager getPeakHoursManager() { return peakHoursManager; }
    /** Returns the cache of bot entries received from other servers in the proxy network. Never null. */
    public me.bill.fakePlayerPlugin.fakeplayer.RemoteBotCache getRemoteBotCache() { return remoteBotCache; }
    /** Returns the ConfigSyncManager, or {@code null} if not in NETWORK mode or database unavailable. */
    public me.bill.fakePlayerPlugin.sync.ConfigSyncManager getConfigSyncManager() { return configSyncManager; }
    /** Returns the stable name→UUID identity cache for this server's bots. Never null. */
    public me.bill.fakePlayerPlugin.fakeplayer.BotIdentityCache getBotIdentityCache() { return botIdentityCache; }


    /** Returns the currently-stored update notification message, or null. */
    public Component getUpdateNotification() { return updateNotificationMessage; }

    /** Store or clear the update notification message (call on main thread). */
    public void setUpdateNotification(Component c) { this.updateNotificationMessage = c; }

    // ── Helpers ───────────────────────────────────────────────────────────────

    /**
     * Creates all plugin data sub-directories on first run (or after deletion).
     * Called before any subsystem reads from these folders so they always exist.
     *
     * <ul>
     *   <li>{@code plugins/FakePlayerPlugin/}          — plugin root</li>
     *   <li>{@code plugins/FakePlayerPlugin/skins/}    — PNG skin files</li>
     *   <li>{@code plugins/FakePlayerPlugin/data/}     — SQLite database</li>
     *   <li>{@code plugins/FakePlayerPlugin/language/} — lang files</li>
     * </ul>
     */
    private void ensureDataDirectories() {
        java.io.File root = getDataFolder();
        String[] dirs = { "skins", "data", "language" };
        for (String dir : dirs) {
            java.io.File d = new java.io.File(root, dir);
            if (!d.exists()) {
                boolean ok = d.mkdirs();
                Config.debugStartup("Created directory: " + d.getPath() + (ok ? " ✔" : " (already exists or failed)"));
            }
        }

        // Drop a README inside skins/ so admins know what to put there
        java.io.File skinsReadme = new java.io.File(root, "skins/README.txt");
        if (!skinsReadme.exists()) {
            try (java.io.PrintWriter w = new java.io.PrintWriter(skinsReadme)) {
                w.println("# FakePlayerPlugin — Skin Folder");
                w.println("#");
                w.println("# Place PNG skin files here to use them for bots.");
                w.println("# Requires: skin.mode = custom  in config.yml");
                w.println("#");
                w.println("# Naming rules:");
                w.println("#   <botname>.png  — assigned exclusively to the bot with that name");
                w.println("#   anything.png   — added to the random skin pool");
                w.println("#");
                w.println("# Skin files must be standard 64x64 or 64x32 Minecraft skin PNGs.");
                w.println("# Run /fpp reload after adding or removing skin files.");
            } catch (java.io.IOException e) {
                Config.debugStartup("Could not write skins/README.txt: " + e.getMessage());
            }
        }
    }
}
