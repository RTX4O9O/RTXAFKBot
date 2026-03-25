package me.bill.fakePlayerPlugin;

import me.bill.fakePlayerPlugin.command.*;
import me.bill.fakePlayerPlugin.config.BotMessageConfig;
import me.bill.fakePlayerPlugin.config.BotNameConfig;
import me.bill.fakePlayerPlugin.config.Config;
import me.bill.fakePlayerPlugin.database.DatabaseManager;
import me.bill.fakePlayerPlugin.fakeplayer.BotChatAI;
import me.bill.fakePlayerPlugin.fakeplayer.BotHeadAI;
import me.bill.fakePlayerPlugin.fakeplayer.BotPersistence;
import me.bill.fakePlayerPlugin.fakeplayer.BotSwapAI;
import me.bill.fakePlayerPlugin.fakeplayer.ChunkLoader;
import me.bill.fakePlayerPlugin.fakeplayer.FakePlayerManager;
import me.bill.fakePlayerPlugin.fakeplayer.SkinRepository;
import me.bill.fakePlayerPlugin.lang.Lang;
import me.bill.fakePlayerPlugin.listener.BotCollisionListener;
import me.bill.fakePlayerPlugin.listener.FakePlayerEntityListener;
import me.bill.fakePlayerPlugin.listener.PlayerJoinListener;
import me.bill.fakePlayerPlugin.listener.PlayerWorldChangeListener;
import me.bill.fakePlayerPlugin.listener.ServerListListener;
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
import org.bukkit.entity.Player;
import net.kyori.adventure.text.Component;
import me.bill.fakePlayerPlugin.permission.Perm;

public final class FakePlayerPlugin extends JavaPlugin {

    private static FakePlayerPlugin instance;
    @SuppressWarnings("unused")
    public static FakePlayerPlugin getInstance() { return instance; }

    private CommandManager    commandManager;
    private FakePlayerManager fakePlayerManager;
    private ChunkLoader       chunkLoader;
    private DatabaseManager   databaseManager;
    private BotPersistence    botPersistence;
    private BotSwapAI         botSwapAI;
    private FppMetrics        fppMetrics;
    private TabListManager    tabListManager;
    private BotTabTeam        botTabTeam;
    private me.bill.fakePlayerPlugin.listener.LuckPermsUpdateListener luckPermsUpdateListener;
    /** When true the plugin detected an unsupported server/runtime and restrains some features */
    private boolean compatibilityRestricted = false;

    /** Single concise compatibility warning message (Adventure Component) to send to ops/admins when configured. */
    private Component compatibilityWarningMessage = null;

    /** Update notification Component stored when an update is detected so it can be
     * delivered to admins who log in after startup. */
    private Component updateNotificationMessage = null;

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
        FppLogger.section("Loading Config");
        Config.init(this);
        FppLogger.debug("config.yml loaded.");

        Lang.init(this);
        FppLogger.debug("Language file loaded (lang=" + Config.getLanguage() + ").");

        BotNameConfig.init(this);
        FppLogger.debug("Bot name pool: " + BotNameConfig.getNames().size() + " names.");

        BotMessageConfig.init(this);
        FppLogger.debug("Bot message pool: " + BotMessageConfig.getMessages().size() + " messages.");

        // Ensure plugin data directories exist regardless of config settings
        ensureDataDirectories();

        // ── Skin repository ───────────────────────────────────────────────────
        FppLogger.section("Loading Skin Repository");
        SkinRepository.get().init(this);
        FppLogger.debug("Skin repository initialised (mode=" + Config.skinMode() + ").");

        // ── Server compatibility check ────────────────────────────────────────
        // Logs detailed console warnings for each issue; returns an immutable result.
        CompatibilityChecker.Result compatResult = CompatibilityChecker.check();
        compatibilityRestricted      = compatResult.restricted;
        compatibilityWarningMessage  = CompatibilityChecker.buildWarningComponent(compatResult);

        // ── Database ──────────────────────────────────────────────────────────
        FppLogger.section("Connecting Database");
        boolean dbOk = false;
        if (Config.databaseEnabled()) {
            databaseManager = new DatabaseManager();
            dbOk = databaseManager.init(getDataFolder());
            if (!dbOk) {
                FppLogger.warn("Database could not be initialised — session tracking disabled.");
                databaseManager = null;
            }
        } else {
            FppLogger.info("Database disabled in config — skipping database initialisation.");
            databaseManager = null;
        }

        // ── Fake player manager + chunk loader ────────────────────────────────
        FppLogger.section("Initialising Subsystems");
        fakePlayerManager = new FakePlayerManager(this);
        if (databaseManager != null) fakePlayerManager.setDatabaseManager(databaseManager);

        if (!compatibilityRestricted) {
            chunkLoader = new ChunkLoader(this, fakePlayerManager);
            fakePlayerManager.setChunkLoader(chunkLoader);
        } else {
            FppLogger.debug("Skipping ChunkLoader initialisation due to compatibility restrictions.");
            chunkLoader = null;
            // fakePlayerManager.setChunkLoader(null); // no-op, leave unset
        }

        botPersistence = new BotPersistence(this);
        fakePlayerManager.setBotPersistence(botPersistence);

        botSwapAI = new BotSwapAI(this, fakePlayerManager);
        fakePlayerManager.setSwapAI(botSwapAI);

        // ── Commands ──────────────────────────────────────────────────────────
        FppLogger.section("Registering Commands");
        commandManager = new CommandManager(this);
        commandManager.register(new SpawnCommand(fakePlayerManager));
        commandManager.register(new DeleteCommand(fakePlayerManager));
        commandManager.register(new ListCommand(fakePlayerManager));
        commandManager.register(new TphCommand(fakePlayerManager));
        commandManager.register(new TpCommand(fakePlayerManager));
        commandManager.register(new ChatCommand(this));
        commandManager.register(new SwapCommand(this));
        commandManager.register(new ReloadCommand(this));
        commandManager.register(new InfoCommand(databaseManager, fakePlayerManager));
        commandManager.register(new MigrateCommand(this));
        commandManager.register(new StatsCommand(fakePlayerManager, databaseManager));
        commandManager.register(new FreezeCommand(fakePlayerManager));
        commandManager.register(new LpInfoCommand(this, fakePlayerManager));
        commandManager.register(new RankCommand(this, fakePlayerManager));
        FppLogger.debug("Commands registered: " + commandManager.getCommands().size() + " total.");

        var fppCmd = getCommand("fpp");
        if (fppCmd != null) {
            fppCmd.setExecutor(commandManager);
            fppCmd.setTabCompleter(commandManager);
        }

        // ── Listeners ─────────────────────────────────────────────────────────
        FppLogger.section("Registering Listeners");
        getServer().getPluginManager().registerEvents(new PlayerJoinListener(this, fakePlayerManager), this);
        getServer().getPluginManager().registerEvents(new PlayerWorldChangeListener(this, fakePlayerManager), this);

        if (!compatibilityRestricted) {
            getServer().getPluginManager().registerEvents(new FakePlayerEntityListener(this, fakePlayerManager, chunkLoader), this);
            getServer().getPluginManager().registerEvents(new BotCollisionListener(this, fakePlayerManager), this);
            new BotHeadAI(this, fakePlayerManager);
        } else {
            FppLogger.info("Skipping Mannequin-dependent listeners and BotHeadAI due to compatibility restrictions.");
        }

        getServer().getPluginManager().registerEvents(new ServerListListener(fakePlayerManager), this);
        new BotChatAI(this, fakePlayerManager);

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
            FppLogger.success("Bot tab team initialized — bots will appear in ~fpp section.");
        } catch (Exception e) {
            FppLogger.warn("Bot tab team init failed — " + e.getMessage());
        }

        // ── PlaceholderAPI soft-dependency ────────────────────────────────────
        if (Bukkit.getPluginManager().getPlugin("PlaceholderAPI") != null) {
            try {
                new me.bill.fakePlayerPlugin.util.FppPlaceholderExpansion(this, fakePlayerManager).register();
                FppLogger.success("PlaceholderAPI: expansion registered (%fpp_count%, %fpp_max%, …).");
            } catch (Exception e) {
                FppLogger.warn("PlaceholderAPI: failed to register expansion — " + e.getMessage());
            }
        }

        // ── Detect LuckPerms prefix (deferred 1 tick so LP is fully loaded) ──
        boolean luckPermsInstalled = Bukkit.getPluginManager().getPlugin("LuckPerms") != null;
        if (luckPermsInstalled) {
            Bukkit.getScheduler().runTask(this, () -> {
                try {
                    net.luckperms.api.LuckPerms lp = net.luckperms.api.LuckPermsProvider.get();
                    luckPermsUpdateListener = new me.bill.fakePlayerPlugin.listener.LuckPermsUpdateListener(this, fakePlayerManager, lp);
                    luckPermsUpdateListener.register();
                    FppLogger.success("LuckPerms: auto-update listener registered (prefix changes apply instantly).");
                } catch (Exception e) {
                    FppLogger.warn("LuckPerms: failed to register auto-update listener — " + e.getMessage());
                }
            });
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
            FppLogger.info("Metrics: disabled in config.yml — skipping FastStats init.");
        }

        String dbLabel = databaseManager == null ? "none"
                : Config.mysqlEnabled()          ? "MySQL"
                                                 : "SQLite (local)";

        // Build skin mode label — include guaranteed-skin status for clarity
        String skinLabel = Config.skinMode()
                + (Config.skinGuaranteed() && !"off".equals(Config.skinMode())
                        ? " (guaranteed → " + Config.skinFallbackName() + ")" : "");

        boolean effectiveSpawnBody    = Config.spawnBody() && !compatibilityRestricted;
        boolean effectiveChunkLoading = Config.chunkLoadingEnabled() && !compatibilityRestricted;

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
                dbLabel,
                dbOk,
                skinLabel,
                effectiveSpawnBody,
                Config.persistOnRestart(),
                luckPermsInstalled,
                Config.swapEnabled(),
                Config.fakeChatEnabled(),
                effectiveChunkLoading,
                Config.maxBots(),
                fppMetrics.isActive(),
                compatibilityRestricted,
                configVersion,
                backupCount,
                startupMs
        );


        // ── Bot persistence restore ───────────────────────────────────────────
        botPersistence.purgeOrphanedBodiesAndRestore(fakePlayerManager);

        // Log LuckPerms prefix detection after server fully starts (1-tick delay)
        getServer().getScheduler().runTaskLater(this, () -> {
            String detected = fakePlayerManager.detectLuckPermsPrefix();
            if (luckPermsInstalled) {
                if (detected.isEmpty()) {
                    FppLogger.info("LuckPerms: no prefix found on default group — using config format only.");
                } else {
                    String readable = detected.replaceAll("<[^>]+>", "").strip();
                    String display  = readable.isEmpty() ? "(formatting/whitespace only)" : "\"" + readable + "\"";
                    FppLogger.success("LuckPerms: default group prefix active — " + display);
                }
                // Log group summary when debug is on
                if (Config.isDebug()) {
                    Config.debug("LuckPerms groups: " + me.bill.fakePlayerPlugin.util.LuckPermsHelper.buildGroupSummary());
                }
            }
        }, 1L);

        FppLogger.debug("onEnable complete.");

        // Deliver a single compatibility warning to currently-online ops/admins if configured
        try {
            if (Config.warningsNotifyAdmins() && compatibilityWarningMessage != null) {
                for (Player p : Bukkit.getOnlinePlayers()) {
                    if (Perm.hasOrOp(p, Perm.ALL)) {
                        p.sendMessage(compatibilityWarningMessage);
                    }
                }
            }
        } catch (Throwable ignored) {}
    }

    @Override
    public void onDisable() {
        Config.debug("onDisable called.");

        int botsRemoved = fakePlayerManager != null ? fakePlayerManager.getCount() : 0;

        // Save active bots BEFORE removing them so the file is written with real locations
        if (botPersistence != null && fakePlayerManager != null) {
            if (Config.persistOnRestart()) {
                FppLogger.debug("Saving " + botsRemoved + " bot(s) for persistence...");
                botPersistence.save(fakePlayerManager.getActivePlayers());
            }
        }

        if (chunkLoader  != null) chunkLoader.releaseAll();
        // Cancel swap timers before sync removal to prevent ghost rejoin tasks
        if (botSwapAI    != null) botSwapAI.cancelAll();
        // Unregister LuckPerms listener
        if (luckPermsUpdateListener != null) luckPermsUpdateListener.unregister();
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

    /** Returns true when the plugin detected an unsupported server/runtime. */
    public boolean isCompatibilityRestricted() { return compatibilityRestricted; }

    /** Returns the (possibly null) compatibility warning message collected during startup. */
    public Component getCompatibilityWarning() { return compatibilityWarningMessage; }

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
                FppLogger.debug("Created directory: " + d.getPath() + (ok ? " ✔" : " (already exists or failed)"));
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
                FppLogger.debug("Could not write skins/README.txt: " + e.getMessage());
            }
        }
    }
}
