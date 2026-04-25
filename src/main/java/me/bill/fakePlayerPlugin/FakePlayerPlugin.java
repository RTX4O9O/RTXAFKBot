package me.bill.fakePlayerPlugin;

import me.bill.fakePlayerPlugin.command.*;
import me.bill.fakePlayerPlugin.config.BotMessageConfig;
import me.bill.fakePlayerPlugin.config.BotNameConfig;
import me.bill.fakePlayerPlugin.config.Config;
import me.bill.fakePlayerPlugin.database.DatabaseManager;
import me.bill.fakePlayerPlugin.fakeplayer.BotChatAI;
import me.bill.fakePlayerPlugin.fakeplayer.BotPersistence;
import me.bill.fakePlayerPlugin.fakeplayer.ChunkLoader;
import me.bill.fakePlayerPlugin.fakeplayer.FakePlayerManager;
import me.bill.fakePlayerPlugin.fakeplayer.PathfindingService;
import me.bill.fakePlayerPlugin.gui.BotSettingGui;
import me.bill.fakePlayerPlugin.gui.SettingGui;
import me.bill.fakePlayerPlugin.lang.Lang;
import me.bill.fakePlayerPlugin.listener.BotCollisionListener;
import me.bill.fakePlayerPlugin.listener.FakePlayerEntityListener;
import me.bill.fakePlayerPlugin.listener.FakePlayerKickListener;
import me.bill.fakePlayerPlugin.listener.PlayerJoinListener;
import me.bill.fakePlayerPlugin.listener.PlayerWorldChangeListener;
import me.bill.fakePlayerPlugin.listener.ServerListListener;
import me.bill.fakePlayerPlugin.messaging.VelocityChannel;
import me.bill.fakePlayerPlugin.util.BackupManager;
import me.bill.fakePlayerPlugin.util.BadwordFilter;
import me.bill.fakePlayerPlugin.util.BotTabTeam;
import me.bill.fakePlayerPlugin.util.CompatibilityChecker;
import me.bill.fakePlayerPlugin.util.ConfigMigrator;
import me.bill.fakePlayerPlugin.util.ConfigValidator;
import me.bill.fakePlayerPlugin.util.FppLogger;
import me.bill.fakePlayerPlugin.util.FppMetrics;
import me.bill.fakePlayerPlugin.util.FppScheduler;
import me.bill.fakePlayerPlugin.util.TabListManager;
import me.bill.fakePlayerPlugin.util.UpdateChecker;
import net.kyori.adventure.text.Component;
import org.bukkit.Bukkit;
import org.bukkit.plugin.java.JavaPlugin;

public final class FakePlayerPlugin extends JavaPlugin {

  private static FakePlayerPlugin instance;

  @SuppressWarnings("unused")
  public static FakePlayerPlugin getInstance() {
    return instance;
  }

  private CommandManager commandManager;
  private FakePlayerManager fakePlayerManager;
  private ChunkLoader chunkLoader;
  private DatabaseManager databaseManager;
  private BotPersistence botPersistence;
  private FppMetrics fppMetrics;
  private TabListManager tabListManager;
  private BotTabTeam botTabTeam;
  private VelocityChannel velocityChannel;
  private BotChatAI botChatAI;
  private me.bill.fakePlayerPlugin.fakeplayer.BotSwapAI botSwapAI;
  private me.bill.fakePlayerPlugin.fakeplayer.PeakHoursManager peakHoursManager;
  private me.bill.fakePlayerPlugin.fakeplayer.RemoteBotCache remoteBotCache;
  private me.bill.fakePlayerPlugin.sync.ConfigSyncManager configSyncManager;
  private SettingGui settingGui;
  private me.bill.fakePlayerPlugin.gui.BotSettingGui botSettingGui;
  private me.bill.fakePlayerPlugin.gui.HelpGui helpGui;
  private me.bill.fakePlayerPlugin.fakeplayer.BotIdentityCache botIdentityCache;
  private XpCommand xpCommand;
  private me.bill.fakePlayerPlugin.command.MoveCommand moveCommand;
  private me.bill.fakePlayerPlugin.command.MineCommand mineCommand;
  private me.bill.fakePlayerPlugin.command.PlaceCommand placeCommand;
  private me.bill.fakePlayerPlugin.command.UseCommand useCommand;
  private me.bill.fakePlayerPlugin.command.AttackCommand attackCommand;
  private me.bill.fakePlayerPlugin.command.FollowCommand followCommand;
  private me.bill.fakePlayerPlugin.command.SleepCommand sleepCommand;
  private me.bill.fakePlayerPlugin.command.FindCommand findCommand;
  private me.bill.fakePlayerPlugin.command.StopCommand stopCommand;
  private PathfindingService pathfindingService;
  private me.bill.fakePlayerPlugin.command.WaypointStore waypointStore;
  private me.bill.fakePlayerPlugin.command.StorageStore storageStore;
  private me.bill.fakePlayerPlugin.command.MineSelectionStore mineSelectionStore;
  private me.bill.fakePlayerPlugin.command.BotGroupStore botGroupStore;
  private me.bill.fakePlayerPlugin.command.InventoryCommand inventoryCommand;
  private me.bill.fakePlayerPlugin.fakeplayer.SkinManager skinManager;

  private me.bill.fakePlayerPlugin.ai.AIProviderRegistry aiProviderRegistry;
  private me.bill.fakePlayerPlugin.ai.BotConversationManager botConversationManager;
  private me.bill.fakePlayerPlugin.ai.PersonalityRepository personalityRepository;

  private me.bill.fakePlayerPlugin.api.impl.FppApiImpl fppApi;

  private Component updateNotificationMessage = null;

  private boolean luckPermsAvailable = false;

  public boolean isLuckPermsAvailable() {
    return luckPermsAvailable;
  }

  private boolean worldGuardAvailable = false;

  public boolean isWorldGuardAvailable() {
    return worldGuardAvailable;
  }

  private boolean worldEditAvailable = false;

  public boolean isWorldEditAvailable() {
    return worldEditAvailable;
  }

  private boolean nameTagAvailable = false;

  public boolean isNameTagAvailable() {
    return nameTagAvailable;
  }

  private boolean versionUnsupported = false;

  private String detectedMcVersion = "unknown";

  public boolean isVersionUnsupported() {
    return versionUnsupported;
  }

  public String getDetectedMcVersion() {
    return detectedMcVersion;
  }

  private long enabledAt;

  @Override
  public void onEnable() {
    instance = this;
    enabledAt = System.currentTimeMillis();
    FppLogger.init(getLogger());

    ConfigMigrator.migrateIfNeeded(this);

    Config.init(this);
    Config.debugStartup("config.yml loaded.");

    me.bill.fakePlayerPlugin.util.AttributionManager.validate(this);
    me.bill.fakePlayerPlugin.util.AttributionApiManager.init(this);

    BadwordFilter.reload(this);
    if (Config.isBadwordFilterEnabled() && BadwordFilter.getBadwordCount() == 0) {
      FppLogger.warn("═══════════════════════════════════════════════════════════════════");
      FppLogger.warn("  ⚠  BADWORD FILTER IS ENABLED BUT NO SOURCES ARE ACTIVE  ⚠");
      FppLogger.warn("  Enable 'badword-filter.use-global-list' or add words to");
      FppLogger.warn("  'badword-filter.words' / 'bad-words.yml', then run /fpp reload");
      FppLogger.warn("═══════════════════════════════════════════════════════════════════");
    }

    Lang.init(this);
    Config.debugStartup("Language file loaded (lang=" + Config.getLanguage() + ").");

    detectedMcVersion = CompatibilityChecker.extractMcVersion();
    if (!CompatibilityChecker.isSupportedVersion(detectedMcVersion)) {
      versionUnsupported = true;
      String pv = getPluginMeta().getVersion();
      FppLogger.warn("═══════════════════════════════════════════════════════════════════");
      FppLogger.warn("  ⚠  FakePlayerPlugin - UNSUPPORTED MINECRAFT VERSION  ⚠");
      FppLogger.warn("═══════════════════════════════════════════════════════════════════");
      FppLogger.warn("  Plugin    : FakePlayerPlugin v" + pv);
      FppLogger.warn("  Server MC : " + detectedMcVersion + "  (NOT supported)");
      FppLogger.warn("  Supported : up to MC 1.21.11, and 26.1.x");
      FppLogger.warn("  Action    : All /fpp commands have been DISABLED.");
      FppLogger.warn("  Support   : If you think this is a bug, contact us:");
      FppLogger.warn("              Discord → https://discord.gg/RfjEJDG2TM");
      FppLogger.warn("═══════════════════════════════════════════════════════════════════");
    }

    BotNameConfig.init(this);
    Config.debugStartup("Bot name pool: " + BotNameConfig.getNames().size() + " names.");

    BotMessageConfig.init(this);
    Config.debugStartup(
        "Bot message pool: " + BotMessageConfig.getMessages().size() + " messages.");

    ensureDataDirectories();

    me.bill.fakePlayerPlugin.fakeplayer.SkinRepository.get().init(this);
    skinManager = new me.bill.fakePlayerPlugin.fakeplayer.SkinManager(this);
    Config.debugStartup("SkinRepository + SkinManager initialised.");

    boolean dbOk = false;
    if (Config.databaseEnabled()) {
      databaseManager = new DatabaseManager();
      dbOk = databaseManager.init(getDataFolder());
      if (!dbOk) {
        FppLogger.warn("Database could not be initialised - session tracking disabled.");
        databaseManager = null;
      } else {

        String mode = Config.databaseMode();
        String serverId = Config.serverId();
        Config.debugDatabase("Database mode: " + mode + " | server-id=" + serverId);

        databaseManager.cleanExpiredSkinCache();

        int cacheSize = databaseManager.getSkinCacheSize();
        if (cacheSize > 0) {
          Config.debugSkin(
              "Skin cache loaded: " + cacheSize + " skin(s) available for instant loading");
        } else {
          Config.debugSkin("Skin cache is empty - will be populated as bots spawn");
        }
      }
    } else {
      Config.debugDatabase("Database disabled in config - skipping database initialisation.");
      databaseManager = null;
    }

    botIdentityCache =
        new me.bill.fakePlayerPlugin.fakeplayer.BotIdentityCache(this, databaseManager);
    Config.debugDatabase(
        "BotIdentityCache initialised (backend="
            + (databaseManager != null ? (Config.mysqlEnabled() ? "MySQL" : "SQLite") : "YAML")
            + ").");

    remoteBotCache = new me.bill.fakePlayerPlugin.fakeplayer.RemoteBotCache();
    if (Config.isNetworkMode() && databaseManager != null) {

      java.util.List<me.bill.fakePlayerPlugin.database.DatabaseManager.ActiveBotRow> remoteRows =
          databaseManager.getActiveBotsFromOtherServers();
      for (var row : remoteRows) {
        try {
          java.util.UUID uuid = java.util.UUID.fromString(row.botUuid());
          String display =
              (row.botDisplay() != null && !row.botDisplay().isBlank())
                  ? row.botDisplay()
                  : row.botName();
          remoteBotCache.add(
              new me.bill.fakePlayerPlugin.fakeplayer.RemoteBotEntry(
                  row.serverId(), uuid, row.botName(), display, row.botName(), "", ""));
        } catch (Exception ignored) {
        }
      }
      Config.debugNetwork(
          "Remote bot cache pre-populated from DB: " + remoteBotCache.count() + " bot(s).");
    }

    if (Config.isNetworkMode() && databaseManager != null) {
      configSyncManager =
          new me.bill.fakePlayerPlugin.sync.ConfigSyncManager(this, databaseManager);
      configSyncManager.init();
      Config.debugConfigSync(
          "Config sync manager initialized (mode=" + Config.configSyncMode() + ").");
    } else {
      configSyncManager = null;
    }

    fakePlayerManager = new FakePlayerManager(this);
    if (databaseManager != null) fakePlayerManager.setDatabaseManager(databaseManager);
    fakePlayerManager.setIdentityCache(botIdentityCache);

    fakePlayerManager.refreshCleanNamePool();

    fppApi = new me.bill.fakePlayerPlugin.api.impl.FppApiImpl(this, fakePlayerManager);

    // Load persisted despawn snapshots (DB primary, YAML fallback) so bots that were manually
    // despawned before the restart can have their inventory/XP restored on next spawn.
    fakePlayerManager.initDespawnSnapshots();

    chunkLoader = new ChunkLoader(this, fakePlayerManager);
    fakePlayerManager.setChunkLoader(chunkLoader);

    botPersistence = new BotPersistence(this);
    fakePlayerManager.setBotPersistence(botPersistence);

    pathfindingService = new PathfindingService(this, fakePlayerManager);
    commandManager = new CommandManager(this);
    commandManager.register(new SpawnCommand(fakePlayerManager));
    commandManager.register(new DeleteCommand(fakePlayerManager));
    commandManager.register(new ListCommand(this, fakePlayerManager));
    commandManager.register(new TphCommand(fakePlayerManager));
    commandManager.register(new TpCommand(fakePlayerManager));
    xpCommand = new XpCommand(this, fakePlayerManager);
    commandManager.register(xpCommand);
    commandManager.register(new ChatCommand(this));
    commandManager.register(new ReloadCommand(this));
    commandManager.register(new InfoCommand(databaseManager, fakePlayerManager));
    commandManager.register(new MigrateCommand(this));
    commandManager.register(
        new me.bill.fakePlayerPlugin.command.BadwordCommand(this, fakePlayerManager));
    commandManager.register(new StatsCommand(fakePlayerManager, databaseManager));
    commandManager.register(new FreezeCommand(fakePlayerManager));
    commandManager.register(new LpInfoCommand(this, fakePlayerManager));
    commandManager.register(new RankCommand(this, fakePlayerManager));
    commandManager.register(new AlertCommand(this));
    commandManager.register(new SyncCommand(this));
    commandManager.register(new SwapCommand(this, fakePlayerManager));
    commandManager.register(new PeaksCommand(this, fakePlayerManager));
    commandManager.register(
        new me.bill.fakePlayerPlugin.command.RenameCommand(this, fakePlayerManager));
    commandManager.register(new me.bill.fakePlayerPlugin.command.PersonalityCommand(this));
    moveCommand =
        new me.bill.fakePlayerPlugin.command.MoveCommand(
            this, fakePlayerManager, pathfindingService);
    waypointStore = new me.bill.fakePlayerPlugin.command.WaypointStore(this);
    waypointStore.load();
    storageStore = new me.bill.fakePlayerPlugin.command.StorageStore(this);
    storageStore.load();
    mineSelectionStore = new me.bill.fakePlayerPlugin.command.MineSelectionStore(this);
    mineSelectionStore.load();
    botGroupStore = new me.bill.fakePlayerPlugin.command.BotGroupStore(this, fakePlayerManager);
    botGroupStore.load();
    moveCommand.setWaypointStore(waypointStore);
    commandManager.register(moveCommand);
    commandManager.register(new me.bill.fakePlayerPlugin.command.WaypointCommand(waypointStore));
    commandManager.register(new me.bill.fakePlayerPlugin.command.CmdCommand(fakePlayerManager));
    mineCommand =
        new me.bill.fakePlayerPlugin.command.MineCommand(
            this, fakePlayerManager, storageStore, mineSelectionStore, pathfindingService);
    commandManager.register(mineCommand);
    findCommand =
        new me.bill.fakePlayerPlugin.command.FindCommand(
            this, fakePlayerManager, pathfindingService, mineCommand);
    mineCommand.setFindCommand(findCommand);
    commandManager.register(findCommand);
    commandManager.register(
        new me.bill.fakePlayerPlugin.command.StorageCommand(this, fakePlayerManager, storageStore, pathfindingService));
    placeCommand =
        new me.bill.fakePlayerPlugin.command.PlaceCommand(
            this, fakePlayerManager, storageStore, pathfindingService);
    commandManager.register(placeCommand);
    useCommand =
        new me.bill.fakePlayerPlugin.command.UseCommand(
            this, fakePlayerManager, pathfindingService);
    commandManager.register(useCommand);
    attackCommand =
        new me.bill.fakePlayerPlugin.command.AttackCommand(
            this, fakePlayerManager, pathfindingService);
    commandManager.register(attackCommand);
    followCommand =
        new me.bill.fakePlayerPlugin.command.FollowCommand(
            this, fakePlayerManager, pathfindingService);
    commandManager.register(followCommand);
    sleepCommand =
        new me.bill.fakePlayerPlugin.command.SleepCommand(
            this, fakePlayerManager, pathfindingService);
    commandManager.register(sleepCommand);

    botSettingGui = new BotSettingGui(this, fakePlayerManager);
    inventoryCommand = new InventoryCommand(fakePlayerManager, this, botSettingGui);
    commandManager.register(inventoryCommand);
    var botSelectCommand =
        new me.bill.fakePlayerPlugin.command.BotSelectCommand(fakePlayerManager, botSettingGui);
    commandManager.register(botSelectCommand);
    commandManager.register(new me.bill.fakePlayerPlugin.command.BotGroupCommand(fakePlayerManager, botGroupStore));
    commandManager.register(new me.bill.fakePlayerPlugin.command.SetOwnerCommand(this, fakePlayerManager));
    commandManager.register(new me.bill.fakePlayerPlugin.command.SaveCommand(this));

    settingGui = new SettingGui(this);
    commandManager.register(new SettingCommand(settingGui, botSettingGui, fakePlayerManager));
    Config.debugStartup("Commands registered: " + commandManager.getCommands().size() + " total.");

    botPersistence.setMoveCommand(moveCommand);
    botPersistence.setMineCommand(mineCommand);
    botPersistence.setPlaceCommand(placeCommand);
    botPersistence.setUseCommand(useCommand);
    botPersistence.setAttackCommand(attackCommand);
    botPersistence.setFollowCommand(followCommand);
    botPersistence.setWaypointStore(waypointStore);

    sleepCommand.setMineCommand(mineCommand);
    sleepCommand.setUseCommand(useCommand);
    sleepCommand.setPlaceCommand(placeCommand);
    sleepCommand.setAttackCommand(attackCommand);
    sleepCommand.setFollowCommand(followCommand);
    sleepCommand.setMoveCommand(moveCommand);
    sleepCommand.setFindCommand(findCommand);

    stopCommand = new me.bill.fakePlayerPlugin.command.StopCommand(fakePlayerManager);
    stopCommand.setMoveCommand(moveCommand);
    stopCommand.setMineCommand(mineCommand);
    stopCommand.setUseCommand(useCommand);
    stopCommand.setPlaceCommand(placeCommand);
    stopCommand.setAttackCommand(attackCommand);
    stopCommand.setFollowCommand(followCommand);
    stopCommand.setFindCommand(findCommand);
    stopCommand.setSleepCommand(sleepCommand);
    commandManager.register(stopCommand);

    var fppCmd = getCommand("fpp");
    if (fppCmd != null) {
      fppCmd.setExecutor(commandManager);
      fppCmd.setTabCompleter(commandManager);
    }

    getServer()
        .getPluginManager()
        .registerEvents(new PlayerJoinListener(this, fakePlayerManager), this);
    getServer()
        .getPluginManager()
        .registerEvents(new PlayerWorldChangeListener(this, fakePlayerManager), this);
    getServer()
        .getPluginManager()
        .registerEvents(new FakePlayerEntityListener(this, fakePlayerManager, chunkLoader), this);
    getServer()
        .getPluginManager()
        .registerEvents(new BotCollisionListener(this, fakePlayerManager), this);

    getServer()
        .getPluginManager()
        .registerEvents(new ServerListListener(this, fakePlayerManager), this);
    getServer()
        .getPluginManager()
        .registerEvents(new FakePlayerKickListener(fakePlayerManager), this);
    getServer()
        .getPluginManager()
        .registerEvents(new me.bill.fakePlayerPlugin.listener.BotCommandBlocker(), this);

    getServer().getPluginManager().registerEvents(settingGui, this);
    getServer().getPluginManager().registerEvents(botSettingGui, this);
    getServer().getPluginManager().registerEvents(inventoryCommand, this);
    getServer().getPluginManager().registerEvents(botSelectCommand, this);
    getServer()
        .getPluginManager()
        .registerEvents(
            new me.bill.fakePlayerPlugin.listener.BotSpawnProtectionListener(this), this);
    getServer()
        .getPluginManager()
        .registerEvents(
            new me.bill.fakePlayerPlugin.listener.BotXpPickupListener(this, fakePlayerManager),
            this);

    helpGui = new me.bill.fakePlayerPlugin.gui.HelpGui(this, commandManager);
    getServer().getPluginManager().registerEvents(helpGui, this);
    commandManager.setHelpGui(helpGui);

    botChatAI = new BotChatAI(this, fakePlayerManager);
    botSwapAI = new me.bill.fakePlayerPlugin.fakeplayer.BotSwapAI(this, fakePlayerManager);
    fakePlayerManager.setBotSwapAI(botSwapAI);
    peakHoursManager =
        new me.bill.fakePlayerPlugin.fakeplayer.PeakHoursManager(this, fakePlayerManager);

    if (databaseManager != null) {
      peakHoursManager.setDatabaseManager(databaseManager);
    }
    if (Config.peakHoursEnabled() && Config.swapEnabled()) {
      peakHoursManager.start();
    }

    aiProviderRegistry = new me.bill.fakePlayerPlugin.ai.AIProviderRegistry(this);
    botConversationManager =
        new me.bill.fakePlayerPlugin.ai.BotConversationManager(this, aiProviderRegistry);

    personalityRepository = new me.bill.fakePlayerPlugin.ai.PersonalityRepository(this);
    personalityRepository.init();
    Config.debugStartup(
        "PersonalityRepository initialised — "
            + personalityRepository.size()
            + " personality file(s) loaded.");

    if (Config.aiConversationsEnabled() && aiProviderRegistry.isAvailable()) {
      getServer()
          .getPluginManager()
          .registerEvents(
              new me.bill.fakePlayerPlugin.listener.BotMessageListener(
                  this, fakePlayerManager, botConversationManager),
              this);
      getLogger()
          .info(
              "[AI] Bot conversations enabled - "
                  + aiProviderRegistry.getActiveProvider().getName());
    } else if (Config.aiConversationsEnabled() && !aiProviderRegistry.isAvailable()) {
      getLogger().warning("[AI] Bot conversations are enabled but no API key configured.");
      getLogger().warning("[AI] Add an API key to plugins/FakePlayerPlugin/secrets.yml");
    }

    velocityChannel = new VelocityChannel(this, fakePlayerManager);
    getServer().getMessenger().registerOutgoingPluginChannel(this, VelocityChannel.CHANNEL);
    getServer().getMessenger().registerOutgoingPluginChannel(this, VelocityChannel.PROXY_CHANNEL);
    getServer().getMessenger().registerOutgoingPluginChannel(this, "BungeeCord");
    getServer()
        .getMessenger()
        .registerIncomingPluginChannel(this, VelocityChannel.CHANNEL, velocityChannel);
    Config.debugNetwork(
        "Plugin messaging channels registered: " + VelocityChannel.CHANNEL + " + BungeeCord.");

    FppScheduler.runSyncRepeating(
        this,
        () -> {
          if (fakePlayerManager.getCount() > 0) {
            fakePlayerManager.validateEntities();
          }
        },
        6000L,
        6000L);

    int configIssues = ConfigValidator.validate();
    if (configIssues > 0) {
      FppLogger.warn("Config validation found " + configIssues + " issue(s) - see above.");
    }

    tabListManager = new TabListManager(this, fakePlayerManager);
    tabListManager.start();

    try {
      botTabTeam = new BotTabTeam();
      botTabTeam.init();
      fakePlayerManager.setBotTabTeam(botTabTeam);
      Config.debugPackets("Bot tab team initialized (~fpp).");
    } catch (Exception e) {
      FppLogger.warn("Bot tab team init failed - " + e.getMessage());
    }

    if (Bukkit.getPluginManager().getPlugin("PlaceholderAPI") != null) {
      try {
        new me.bill.fakePlayerPlugin.util.FppPlaceholderExpansion(this, fakePlayerManager)
            .register();
        Config.debugStartup("PlaceholderAPI detected - placeholders registered.");
      } catch (Exception e) {
        FppLogger.warn("PlaceholderAPI: failed to register expansion - " + e.getMessage());
      }
    }

    boolean luckPermsInstalled = Bukkit.getPluginManager().getPlugin("LuckPerms") != null;
    luckPermsAvailable = luckPermsInstalled;
    if (luckPermsInstalled) {
      Config.debugLuckPerms("LuckPerms detected - bot group sync enabled.");
      String defaultGroup = me.bill.fakePlayerPlugin.config.Config.luckpermsDefaultGroup();
      if (!defaultGroup.isBlank()) {
        Config.debugLuckPerms(
            "default-group='" + defaultGroup + "' will be applied to new bots at spawn.");
      }

      me.bill.fakePlayerPlugin.util.LuckPermsHelper.subscribeLpEvents(this, fakePlayerManager);
    }

    worldGuardAvailable = Bukkit.getPluginManager().getPlugin("WorldGuard") != null;
    if (worldGuardAvailable) {
      Config.debugStartup("WorldGuard detected - bot PvP region protection enabled.");
    }

    worldEditAvailable = Bukkit.getPluginManager().getPlugin("WorldEdit") != null;
    if (worldEditAvailable) {
      Config.debugStartup("WorldEdit detected - --wesel flag enabled for /fpp mine and /fpp place.");
    }

    nameTagAvailable = Bukkit.getPluginManager().getPlugin("NameTag") != null;
    if (nameTagAvailable) {
      Config.debugStartup(
          "NameTag detected - nick-conflict guard and bot-isolation enabled"
              + " (block-nick-conflicts="
              + Config.nameTagBlockNickConflicts()
              + ", bot-isolation="
              + Config.nameTagIsolation()
              + ").");
    }

    UpdateChecker.check(this);

    fppMetrics = new FppMetrics();
    if (Config.metricsEnabled()) {
      try {
        fppMetrics.init(this, fakePlayerManager);
      } catch (Throwable t) {
        FppLogger.error(
            "Metrics: unexpected top-level error - "
                + t.getClass().getName()
                + ": "
                + t.getMessage());
        for (StackTraceElement el : t.getStackTrace()) {
          FppLogger.error("  at " + el);
        }
      }
    } else {
      Config.debugStartup("Metrics disabled in config.yml - skipping FastStats init.");
    }

    String dbLabel =
        databaseManager == null ? "none" : Config.mysqlEnabled() ? "MySQL" : "SQLite (local)";
    String dbState =
        !Config.databaseEnabled() ? "disabled" : (dbOk ? dbLabel : dbLabel + " (failed)");
    int dbSchemaVersion = databaseManager != null ? DatabaseManager.getCurrentSchemaVersion() : 0;

    String skinLabel = Config.skinMode();

    boolean effectiveSpawnBody = Config.spawnBody();
    boolean effectiveChunkLoading =
        Config.chunkLoadingEnabled() && Config.chunkLoadingRadius() != 0;
    boolean effectiveTaskPersist = Config.persistOnRestart() && databaseManager != null;

    boolean aiConvEnabled =
        Config.aiConversationsEnabled()
            && aiProviderRegistry != null
            && aiProviderRegistry.isAvailable();
    String aiProviderName =
        aiConvEnabled ? aiProviderRegistry.getActiveProvider().getName() : "none";

    long startupMs = System.currentTimeMillis() - enabledAt;
    int cfgVer = Config.configVersion();
    String configVersion =
        "v" + cfgVer + (cfgVer >= ConfigMigrator.CURRENT_VERSION ? " ✔" : " (migrated)");
    int backupCount = BackupManager.listBackups(this).size();

    FppLogger.printStartupBanner(
        getPluginMeta().getVersion(),
        String.join(", ", getPluginMeta().getAuthors()),
        BotNameConfig.getNames().size(),
        BotMessageConfig.getMessages().size(),
        dbState,
        dbSchemaVersion,
        skinLabel,
        effectiveSpawnBody,
        Config.persistOnRestart(),
        effectiveTaskPersist,
        luckPermsInstalled,
        worldGuardAvailable,
        nameTagAvailable,
        Config.fakeChatEnabled(),
        aiConvEnabled,
        aiProviderName,
        Config.swapEnabled(),
        Config.peakHoursEnabled(),
        effectiveChunkLoading,
        Config.maxBots(),
        fppMetrics.isActive(),
        configVersion,
        backupCount,
        startupMs);

    botPersistence.purgeOrphanedBodiesAndRestore(fakePlayerManager);

    if (databaseManager != null && Config.persistOnRestart()) {
      peakHoursManager.restoreSleepingBotsFromDatabase(databaseManager);
    }

    if (velocityChannel != null) {
      FppScheduler.runSyncLater(this, () -> velocityChannel.broadcastResyncRequest(), 10L);
    }

    Config.debugStartup("onEnable complete.");
  }

  @Override
  public void onDisable() {
    Config.debugStartup("onDisable called.");

    if (fppApi != null) fppApi.disableAllAddons();

    int botsRemoved = fakePlayerManager != null ? fakePlayerManager.getCount() : 0;

    if (chunkLoader != null) chunkLoader.releaseAll();

    if (botChatAI != null) botChatAI.cancelAll();

    if (botConversationManager != null) botConversationManager.clearAll();

    if (peakHoursManager != null) peakHoursManager.shutdown();

    if (botSwapAI != null) botSwapAI.cancelAll();

    if (sleepCommand != null) sleepCommand.stopAll();

    if (botPersistence != null && fakePlayerManager != null) {
      if (Config.persistOnRestart()) {
        Config.debugStartup(
            "Saving " + fakePlayerManager.getCount() + " bot(s) for persistence...");
        botPersistence.save(fakePlayerManager.getActivePlayers());
      }
    }

    if (luckPermsAvailable) {
      me.bill.fakePlayerPlugin.util.LuckPermsHelper.unsubscribeLpEvents();
    }

    if (velocityChannel != null) {
      velocityChannel.broadcastServerOffline();
    }

    if (fakePlayerManager != null) fakePlayerManager.removeAllSyncFast();

    if (tabListManager != null) tabListManager.shutdown();

    if (botTabTeam != null) botTabTeam.destroy();

    boolean dbFlushed = false;
    if (databaseManager != null) {
      databaseManager.recordAllShutdown();
      databaseManager.close();
      dbFlushed = true;
    }

    if (fppMetrics != null) fppMetrics.shutdown();

    getServer().getMessenger().unregisterIncomingPluginChannel(this, VelocityChannel.CHANNEL);
    getServer().getMessenger().unregisterOutgoingPluginChannel(this, VelocityChannel.CHANNEL);
    getServer().getMessenger().unregisterOutgoingPluginChannel(this, VelocityChannel.PROXY_CHANNEL);
    getServer().getMessenger().unregisterOutgoingPluginChannel(this, "BungeeCord");

    long uptimeMs = System.currentTimeMillis() - enabledAt;
    boolean tasksPersisted = Config.persistOnRestart() && databaseManager != null;
    FppLogger.printShutdownBanner(botsRemoved, dbFlushed, tasksPersisted, botsRemoved, uptimeMs);
  }

  @SuppressWarnings("unused")
  public CommandManager getCommandManager() {
    return commandManager;
  }

  /** Returns the public addon API entry point. Available after {@code onEnable} completes. */
  @SuppressWarnings("unused")
  public me.bill.fakePlayerPlugin.api.FppApi getFppApi() {
    return fppApi;
  }

  /** Internal accessor for subsystems that need the concrete impl (e.g. fireTickHandlers). */
  public me.bill.fakePlayerPlugin.api.impl.FppApiImpl getFppApiImpl() {
    return fppApi;
  }

  @SuppressWarnings("unused")
  public FakePlayerManager getFakePlayerManager() {
    return fakePlayerManager;
  }

  public BotPersistence getBotPersistence() {
    return botPersistence;
  }

  public SettingGui getSettingGui() {
    return settingGui;
  }

  public me.bill.fakePlayerPlugin.gui.BotSettingGui getBotSettingGui() {
    return botSettingGui;
  }

  public DatabaseManager getDatabaseManager() {
    return databaseManager;
  }

  public TabListManager getTabListManager() {
    return tabListManager;
  }

  public BotTabTeam getBotTabTeam() {
    return botTabTeam;
  }

  public VelocityChannel getVelocityChannel() {
    return velocityChannel;
  }

  public BotChatAI getBotChatAI() {
    return botChatAI;
  }

  public me.bill.fakePlayerPlugin.fakeplayer.BotSwapAI getBotSwapAI() {
    return botSwapAI;
  }

  public me.bill.fakePlayerPlugin.fakeplayer.PeakHoursManager getPeakHoursManager() {
    return peakHoursManager;
  }

  public me.bill.fakePlayerPlugin.fakeplayer.RemoteBotCache getRemoteBotCache() {
    return remoteBotCache;
  }

  public me.bill.fakePlayerPlugin.sync.ConfigSyncManager getConfigSyncManager() {
    return configSyncManager;
  }

  public me.bill.fakePlayerPlugin.fakeplayer.BotIdentityCache getBotIdentityCache() {
    return botIdentityCache;
  }

  public XpCommand getXpCommand() {
    return xpCommand;
  }

  public me.bill.fakePlayerPlugin.command.MoveCommand getMoveCommand() {
    return moveCommand;
  }

  public me.bill.fakePlayerPlugin.command.MineCommand getMineCommand() {
    return mineCommand;
  }

  public me.bill.fakePlayerPlugin.command.PlaceCommand getPlaceCommand() {
    return placeCommand;
  }

  public me.bill.fakePlayerPlugin.command.UseCommand getUseCommand() {
    return useCommand;
  }

  public me.bill.fakePlayerPlugin.command.AttackCommand getAttackCommand() {
    return attackCommand;
  }

  public me.bill.fakePlayerPlugin.command.FollowCommand getFollowCommand() {
    return followCommand;
  }

  public me.bill.fakePlayerPlugin.command.SleepCommand getSleepCommand() {
    return sleepCommand;
  }

  public PathfindingService getPathfindingService() {
    return pathfindingService;
  }

  public me.bill.fakePlayerPlugin.command.StorageStore getStorageStore() {
    return storageStore;
  }

  public me.bill.fakePlayerPlugin.command.BotGroupStore getBotGroupStore() {
    return botGroupStore;
  }

  public me.bill.fakePlayerPlugin.command.InventoryCommand getInventoryCommand() {
    return inventoryCommand;
  }

  public me.bill.fakePlayerPlugin.fakeplayer.SkinManager getSkinManager() {
    return skinManager;
  }

  public me.bill.fakePlayerPlugin.ai.AIProviderRegistry getAIProviderRegistry() {
    return aiProviderRegistry;
  }

  public me.bill.fakePlayerPlugin.ai.BotConversationManager getBotConversationManager() {
    return botConversationManager;
  }

  public me.bill.fakePlayerPlugin.ai.PersonalityRepository getPersonalityRepository() {
    return personalityRepository;
  }

  public Component getUpdateNotification() {
    return updateNotificationMessage;
  }

  public void setUpdateNotification(Component c) {
    this.updateNotificationMessage = c;
  }

  private volatile String latestKnownVersion = null;

  private volatile boolean runningBeta = false;

  public String getLatestKnownVersion() {
    return latestKnownVersion;
  }

  public void setLatestKnownVersion(String v) {
    this.latestKnownVersion = v;
  }

  public boolean isRunningBeta() {
    return runningBeta;
  }

  public void setRunningBeta(boolean b) {
    this.runningBeta = b;
  }

  private void ensureDataDirectories() {
    java.io.File root = getDataFolder();
    String[] dirs = {"skins", "data", "language", "personalities"};
    for (String dir : dirs) {
      java.io.File d = new java.io.File(root, dir);
      if (!d.exists()) {
        boolean ok = d.mkdirs();
        Config.debugStartup(
            "Created directory: " + d.getPath() + (ok ? " ✔" : " (already exists or failed)"));
      }
    }

    java.io.File skinsReadme = new java.io.File(root, "skins/README.txt");
    if (!skinsReadme.exists()) {
      try (java.io.PrintWriter w = new java.io.PrintWriter(skinsReadme)) {
        w.println("# FakePlayerPlugin - Skin Folder");
        w.println("#");
        w.println("# Place PNG skin files here to use them for bots.");
        w.println("# Requires: skin.mode = custom  in config.yml");
        w.println("#");
        w.println("# Naming rules:");
        w.println("#   <botname>.png  - assigned exclusively to the bot with that name");
        w.println("#   anything.png   - added to the random skin pool");
        w.println("#");
        w.println("# Skin files must be standard 64x64 or 64x32 Minecraft skin PNGs.");
        w.println("# Run /fpp reload after adding or removing skin files.");
      } catch (java.io.IOException e) {
        Config.debugStartup("Could not write skins/README.txt: " + e.getMessage());
      }
    }
  }
}
