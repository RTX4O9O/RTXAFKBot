package me.bill.fakePlayerPlugin;

import me.bill.fakePlayerPlugin.command.ChatCommand;
import me.bill.fakePlayerPlugin.command.CommandManager;
import me.bill.fakePlayerPlugin.command.DeleteCommand;
import me.bill.fakePlayerPlugin.command.InfoCommand;
import me.bill.fakePlayerPlugin.command.ListCommand;
import me.bill.fakePlayerPlugin.command.MigrateCommand;
import me.bill.fakePlayerPlugin.command.ReloadCommand;
import me.bill.fakePlayerPlugin.command.SpawnCommand;
import me.bill.fakePlayerPlugin.command.SwapCommand;
import me.bill.fakePlayerPlugin.command.TpCommand;
import me.bill.fakePlayerPlugin.command.TphCommand;
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
import me.bill.fakePlayerPlugin.util.ConfigMigrator;
import me.bill.fakePlayerPlugin.util.FppLogger;
import me.bill.fakePlayerPlugin.util.FppMetrics;
import me.bill.fakePlayerPlugin.util.UpdateChecker;
import org.bukkit.Bukkit;
import org.bukkit.plugin.java.JavaPlugin;

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

        // ── Database ──────────────────────────────────────────────────────────
        FppLogger.section("Connecting Database");
        databaseManager = new DatabaseManager();
        boolean dbOk = databaseManager.init(getDataFolder());
        if (!dbOk) {
            FppLogger.warn("Database could not be initialised — session tracking disabled.");
            databaseManager = null;
        }

        // ── Fake player manager + chunk loader ────────────────────────────────
        FppLogger.section("Initialising Subsystems");
        fakePlayerManager = new FakePlayerManager(this);
        if (databaseManager != null) fakePlayerManager.setDatabaseManager(databaseManager);

        chunkLoader = new ChunkLoader(this, fakePlayerManager);
        fakePlayerManager.setChunkLoader(chunkLoader);

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
        getServer().getPluginManager().registerEvents(new FakePlayerEntityListener(this, fakePlayerManager, chunkLoader), this);
        getServer().getPluginManager().registerEvents(new BotCollisionListener(this, fakePlayerManager), this);
        getServer().getPluginManager().registerEvents(new ServerListListener(fakePlayerManager), this);

        new BotHeadAI(this, fakePlayerManager);
        new BotChatAI(this, fakePlayerManager);

        // ── Periodic entity health check + orphan sweep (every 5 min) ─────────
        getServer().getScheduler().runTaskTimer(this, () -> {
            if (fakePlayerManager.getCount() > 0) {
                fakePlayerManager.validateEntities();
            }
        }, 6000L, 6000L);

        // ── Detect LuckPerms prefix (deferred 1 tick so LP is fully loaded) ──
        boolean luckPermsInstalled = Bukkit.getPluginManager().getPlugin("LuckPerms") != null;

        // ── Full startup banner ───────────────────────────────────────────────
        String dbLabel = databaseManager == null ? "none"
                : Config.mysqlEnabled()          ? "MySQL"
                                                 : "SQLite (local)";

        // Build skin mode label — include guaranteed-skin status for clarity
        String skinLabel = Config.skinMode()
                + (Config.skinGuaranteed() && !"off".equals(Config.skinMode())
                        ? " (guaranteed → " + Config.skinFallbackName() + ")" : "");

        FppLogger.printStartupBanner(
                getPluginMeta().getVersion(),
                String.join(", ", getPluginMeta().getAuthors()),
                BotNameConfig.getNames().size(),
                BotMessageConfig.getMessages().size(),
                dbLabel,
                dbOk,
                skinLabel,
                Config.spawnBody(),
                Config.persistOnRestart(),
                luckPermsInstalled,
                Config.swapEnabled(),
                Config.fakeChatEnabled(),
                Config.chunkLoadingEnabled(),
                Config.maxBots()
        );

        // ── Migration summary ─────────────────────────────────────────────────
        int cfgVer = Config.configVersion();
        FppLogger.kv("Config version",
                "v" + cfgVer + (cfgVer >= ConfigMigrator.CURRENT_VERSION ? " ✔" : " (migrated)"));
        int backupCount = BackupManager.listBackups(this).size();
        if (backupCount > 0) {
            FppLogger.kv("Backups stored",
                    backupCount + " (see plugins/FakePlayerPlugin/backups/)");
        }

        // ── Update checker (async, non-blocking) ─────────────────────────────
        UpdateChecker.check(this);

        // ── Metrics (FastStats) ───────────────────────────────────────────────
        fppMetrics = new FppMetrics();
        fppMetrics.init(this, fakePlayerManager);

        // ── Bot persistence restore ───────────────────────────────────────────
        botPersistence.purgeOrphanedBodiesAndRestore(fakePlayerManager);

        // Log LuckPerms prefix detection after server fully starts (1-tick delay)
        getServer().getScheduler().runTaskLater(this, () -> {
            String detected = fakePlayerManager.detectLuckPermsPrefix();
            if (luckPermsInstalled) {
                if (detected.isEmpty()) {
                    FppLogger.info("LuckPerms: no prefix found on default group — using config format only.");
                } else {
                    FppLogger.success("LuckPerms: prefix detected → \"" + detected + "\"");
                }
            }
        }, 1L);

        long startupMs = System.currentTimeMillis() - enabledAt;
        FppLogger.info("Enable completed in " + startupMs + " ms.");
        FppLogger.debug("onEnable complete.");
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
        // removeAllSync sends leave messages + cleans up entities
        if (fakePlayerManager != null) fakePlayerManager.removeAllSync();

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
