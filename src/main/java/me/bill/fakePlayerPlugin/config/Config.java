package me.bill.fakePlayerPlugin.config;

import java.util.List;
import java.util.Map;
import me.bill.fakePlayerPlugin.FakePlayerPlugin;
import me.bill.fakePlayerPlugin.util.FppLogger;
import org.bukkit.Bukkit;
import org.bukkit.configuration.file.FileConfiguration;

public final class Config {

  private static FakePlayerPlugin plugin;
  private static FileConfiguration cfg;

  private Config() {}

  public static void init(FakePlayerPlugin instance) {
    plugin = instance;
    reload();
  }

  public static void reload() {
    plugin.saveDefaultConfig();
    plugin.reloadConfig();
    cfg = plugin.getConfig();
    cfg.options().copyDefaults(true);

    cfg.set("body.enabled", true);
    plugin.saveConfig();
  }

  public static int configVersion() {
    return cfg.getInt("config-version", 0);
  }

  public static String getLanguage() {
    return cfg.getString("language", "en");
  }

  public static boolean isDebug() {
    return cfg != null && cfg.getBoolean("debug", false);
  }

  private static boolean debugFlag(String path) {
    return cfg != null && cfg.getBoolean(path, false);
  }

  public static boolean debugStartup() {
    return isDebug() || debugFlag("logging.debug.startup");
  }

  public static boolean debugNms() {
    return isDebug() || debugFlag("logging.debug.nms");
  }

  public static boolean debugPackets() {
    return isDebug() || debugFlag("logging.debug.packets");
  }

  public static boolean debugLuckPerms() {
    return isDebug() || debugFlag("logging.debug.luckperms");
  }

  public static boolean debugNetwork() {
    return isDebug() || debugFlag("logging.debug.network");
  }

  public static boolean debugConfigSync() {
    return isDebug() || debugFlag("logging.debug.config-sync");
  }

  public static boolean debugSkin() {
    return isDebug() || debugFlag("logging.debug.skin");
  }

  public static boolean debugDatabase() {
    return isDebug() || debugFlag("logging.debug.database");
  }

  public static boolean debugChat() {
    return isDebug() || debugFlag("logging.debug.chat");
  }

  public static boolean debugSwap() {
    return isDebug() || debugFlag("logging.debug.swap");
  }

  public static boolean updateCheckerEnabled() {
    return cfg.getBoolean("update-checker.enabled", true);
  }

  /**
   * Help display mode. "gui" (default) opens the HelpGui chest for players; "text" always uses
   * the paginated chat renderer. Controlled by the "help.mode" config key.
   */
  public static String helpMode() {
    return cfg.getString("help.mode", "gui").toLowerCase();
  }

  public static boolean metricsEnabled() {
    return cfg.getBoolean("metrics.enabled", true);
  }

  public static int spawnCooldown() {
    return Math.max(0, cfg.getInt("spawn-cooldown", 0));
  }

  public static boolean tabListEnabled() {
    return cfg.getBoolean("tab-list.enabled", true);
  }

  public static boolean serverListCountBots() {
    return cfg.getBoolean("server-list.count-bots", true);
  }

  public static boolean serverListIncludeRemote() {
    return cfg.getBoolean("server-list.include-remote-bots", false);
  }

  public static int maxBots() {
    return cfg.getInt("limits.max-bots", 1000);
  }

  public static int userBotLimit() {
    return cfg.getInt("limits.user-bot-limit", 1);
  }

  public static List<String> spawnCountPresetsAdmin() {
    List<?> raw = cfg.getList("limits.spawn-presets", List.of(1, 5, 10, 15, 20));
    return raw.stream().map(Object::toString).toList();
  }

  public static String adminBotNameFormat() {
    return cfg.getString("bot-name.admin-format", "<#0079FF>[bot-{bot_name}]</#0079FF>");
  }

  public static String userBotNameFormat() {
    return cfg.getString("bot-name.user-format", "<gray>[bot-{spawner}-{num}]</gray>");
  }

  public static String botNameMode() {
    return cfg.getString("bot-name.mode", "random").toLowerCase();
  }

  public static String luckpermsDefaultGroup() {
    return cfg.getString("luckperms.default-group", "");
  }

  public static String skinMode() {
    return cfg.getString("skin.mode", "player").toLowerCase();
  }

  public static boolean skinClearCacheOnReload() {
    return cfg.getBoolean("skin.clear-cache-on-reload", true);
  }

  public static boolean skinGuaranteed() {
    return cfg.getBoolean("skin.guaranteed-skin", false);
  }

  public static List<String> skinCustomPool() {

    Object raw = cfg.get("skin.pool");
    if (raw == null) raw = cfg.get("skin.custom.pool");
    if (raw instanceof List<?> list) {
      return list.stream()
          .filter(o -> o instanceof String)
          .map(o -> (String) o)
          .filter(s -> !s.isBlank())
          .toList();
    }
    return List.of();
  }

  public static Map<String, String> skinCustomByName() {

    Object section = cfg.get("skin.overrides");
    if (section == null) section = cfg.get("skin.custom.by-name");
    if (section instanceof Map<?, ?> raw) {
      Map<String, String> result = new java.util.LinkedHashMap<>();
      for (Map.Entry<?, ?> e : raw.entrySet()) {
        if (e.getKey() instanceof String k && e.getValue() instanceof String v) {
          result.put(k.toLowerCase(), v);
        }
      }
      return result;
    }
    return Map.of();
  }

  public static boolean skinUseSkinFolder() {
    return cfg.getBoolean("skin.use-skin-folder", true);
  }

  public static boolean spawnBody() {
    return cfg.getBoolean("body.enabled", true);
  }

  public static boolean bodyPushable() {
    return cfg.getBoolean("body.pushable", true);
  }

  public static boolean bodyDamageable() {
    return cfg.getBoolean("body.damageable", true);
  }

  public static boolean bodyPickUpItems() {
    return cfg.getBoolean("body.pick-up-items", false);
  }

  public static boolean bodyPickUpXp() {
    return cfg.getBoolean("body.pick-up-xp", true);
  }

  public static boolean autoEatEnabled() {
    return cfg.getBoolean("automation.auto-eat", true);
  }

  public static boolean autoPlaceBedEnabled() {
    return cfg.getBoolean("automation.auto-place-bed", true);
  }

  public static boolean dropItemsOnDespawn() {
    return cfg.getBoolean("body.drop-items-on-despawn", false);
  }

  public static boolean persistOnRestart() {
    return cfg.getBoolean("persistence.enabled", true);
  }

  public static List<String> namePool() {
    return BotNameConfig.getNames();
  }

  public static int joinDelayMin() {
    return cfg.getInt("join-delay.min", 0);
  }

  public static int joinDelayMax() {
    return cfg.getInt("join-delay.max", 40);
  }

  public static int leaveDelayMin() {
    return cfg.getInt("leave-delay.min", 0);
  }

  public static int leaveDelayMax() {
    return cfg.getInt("leave-delay.max", 40);
  }

  public static boolean swapEnabled() {
    return cfg.getBoolean("swap.enabled", false);
  }

  public static int swapSessionMin() {
    return cfg.getInt("swap.session.min", 60);
  }

  public static int swapSessionMax() {
    return cfg.getInt("swap.session.max", 300);
  }

  public static int swapAbsenceMin() {
    return cfg.getInt("swap.absence.min", 30);
  }

  public static int swapAbsenceMax() {
    return cfg.getInt("swap.absence.max", 120);
  }

  public static int swapMaxSwappedOut() {
    return cfg.getInt("swap.max-swapped-out", 0);
  }

  public static boolean swapFarewellChat() {
    return cfg.getBoolean("swap.farewell-chat", true);
  }

  public static boolean swapGreetingChat() {
    return cfg.getBoolean("swap.greeting-chat", true);
  }

  public static boolean swapSameNameOnRejoin() {
    return cfg.getBoolean("swap.same-name-on-rejoin", true);
  }

  public static int swapMinOnline() {
    return cfg.getInt("swap.min-online", 0);
  }

  public static boolean swapRetryRejoin() {
    return cfg.getBoolean("swap.retry-rejoin", true);
  }

  public static int swapRetryDelay() {
    return cfg.getInt("swap.retry-delay", 60);
  }

  public static boolean peakHoursEnabled() {
    return cfg.getBoolean("peak-hours.enabled", false);
  }

  public static String peakHoursTimezone() {
    return cfg.getString("peak-hours.timezone", "UTC");
  }

  public static int peakHoursStaggerSeconds() {
    return cfg.getInt("peak-hours.stagger-seconds", 30);
  }

  public static java.util.List<java.util.Map<?, ?>> peakHoursSchedule() {
    return cfg.getMapList("peak-hours.schedule");
  }

  public static org.bukkit.configuration.ConfigurationSection peakHoursDayOverrides() {
    return cfg.getConfigurationSection("peak-hours.day-overrides");
  }

  public static int peakHoursMinOnline() {
    return Math.max(0, cfg.getInt("peak-hours.min-online", 0));
  }

  public static boolean peakHoursNotifyTransitions() {
    return cfg.getBoolean("peak-hours.notify-transitions", false);
  }

  public static boolean joinMessage() {
    return cfg.getBoolean("messages.join-message", true);
  }

  public static boolean leaveMessage() {
    return cfg.getBoolean("messages.leave-message", true);
  }

  public static boolean deathMessage() {
    return cfg.getBoolean("messages.death-message", true);
  }

  public static boolean killMessage() {
    return cfg.getBoolean("messages.kill-message", false);
  }

  public static boolean warningsNotifyAdmins() {
    return cfg.getBoolean("messages.notify-admins-on-join", true);
  }

  public static double maxHealth() {
    return cfg.getDouble("combat.max-health", 20.0);
  }

  public static boolean hurtSound() {
    return cfg.getBoolean("combat.hurt-sound", true);
  }

  public static boolean respawnOnDeath() {
    return cfg.getBoolean("death.respawn-on-death", false);
  }

  public static int respawnDelay() {
    return cfg.getInt("death.respawn-delay", 60);
  }

  public static boolean suppressDrops() {
    return cfg.getBoolean("death.suppress-drops", false);
  }

  public static boolean chunkLoadingEnabled() {
    return cfg.getBoolean("chunk-loading.enabled", true);
  }

  public static int chunkLoadingRadius() {
    Object raw = cfg.get("chunk-loading.radius");
    if (raw instanceof Number n) {
      return Math.max(0, n.intValue());
    }

    return Bukkit.getSimulationDistance();
  }

  public static int chunkLoadingUpdateInterval() {
    return cfg.getInt("chunk-loading.update-interval", 20);
  }

  public static int chunkLoadingMassDisableThreshold() {
    return cfg.getInt("chunk-loading.mass-disable-threshold", 100);
  }

  public static boolean headAiEnabled() {
    return cfg.getBoolean("head-ai.enabled", true);
  }

  public static double headAiLookRange() {
    return cfg.getDouble("head-ai.look-range", 8.0);
  }

  public static float headAiTurnSpeed() {
    return (float) cfg.getDouble("head-ai.turn-speed", 0.3);
  }

  public static int headAiTickRate() {
    return Math.max(1, cfg.getInt("head-ai.tick-rate", 3));
  }

  public static boolean swimAiEnabled() {
    return cfg.getBoolean("swim-ai.enabled", true);
  }

  public static boolean pathfindingParkour() {
    return cfg.getBoolean("pathfinding.parkour", false);
  }

  public static boolean pathfindingBreakBlocks() {
    return cfg.getBoolean("pathfinding.break-blocks", false);
  }

  public static boolean pathfindingPlaceBlocks() {
    return cfg.getBoolean("pathfinding.place-blocks", false);
  }

  public static String pathfindingPlaceMaterial() {
    return cfg.getString("pathfinding.place-material", "DIRT");
  }

  public static double pathfindingArrivalDistance() {
    return cfg.getDouble("pathfinding.arrival-distance", 1.2);
  }

  public static double pathfindingPatrolArrivalDistance() {
    return cfg.getDouble("pathfinding.patrol-arrival-distance", 1.5);
  }

  public static double pathfindingWaypointArrivalDistance() {
    return cfg.getDouble("pathfinding.waypoint-arrival-distance", 0.65);
  }

  public static double pathfindingSprintDistance() {
    return cfg.getDouble("pathfinding.sprint-distance", 6.0);
  }

  public static double pathfindingFollowRecalcDistance() {
    return cfg.getDouble("pathfinding.follow-recalc-distance", 3.5);
  }

  public static int pathfindingFollowRecalcInterval() {
    return Math.max(1, cfg.getInt("pathfinding.follow-recalc-interval", 100));
  }

  public static int pathfindingRecalcInterval() {
    return Math.max(1, cfg.getInt("pathfinding.recalc-interval", 60));
  }

  public static int pathfindingStuckTicks() {
    return Math.max(1, cfg.getInt("pathfinding.stuck-ticks", 5));
  }

  public static double pathfindingStuckThreshold() {
    return Math.max(0.001, cfg.getDouble("pathfinding.stuck-threshold", 0.04));
  }

  public static int pathfindingBreakTicks() {
    return Math.max(1, cfg.getInt("pathfinding.break-ticks", 15));
  }

  public static int pathfindingPlaceTicks() {
    return Math.max(1, cfg.getInt("pathfinding.place-ticks", 5));
  }

  public static int pathfindingMaxFall() {
    return Math.max(1, Math.min(cfg.getInt("pathfinding.max-fall", 3), 16));
  }

  public static int pathfindingMaxRange() {
    return Math.max(8, cfg.getInt("pathfinding.max-range", 64));
  }

  public static int pathfindingMaxNodes() {
    return Math.max(100, cfg.getInt("pathfinding.max-nodes", 900));
  }

  public static int pathfindingMaxNodesExtended() {
    return Math.max(pathfindingMaxNodes(), cfg.getInt("pathfinding.max-nodes-extended", 1800));
  }


  /** Number of lateral sweep steps tried on each side when searching for a detour waypoint. */
  public static int pathfindingDetourAttempts() {
    return Math.max(1, Math.min(cfg.getInt("pathfinding.detour-attempts", 5), 20));
  }

  /** Total lateral radius (in blocks) spread across detour-attempts steps. */
  public static double pathfindingDetourRadius() {
    return Math.max(2.0, Math.min(cfg.getDouble("pathfinding.detour-radius", 16.0), 64.0));
  }

  public static double collisionWalkRadius() {
    return cfg.getDouble("collision.walk-radius", 0.85);
  }

  public static double collisionWalkStrength() {
    return cfg.getDouble("collision.walk-strength", 0.22);
  }

  public static double collisionMaxHoriz() {
    return cfg.getDouble("collision.max-horizontal-speed", 0.30);
  }

  public static double collisionHitStrength() {
    return cfg.getDouble("collision.hit-strength", 0.45);
  }

  public static double collisionHitMaxHoriz() {
    return cfg.getDouble("collision.hit-max-horizontal-speed", 0.80);
  }

  public static double collisionBotRadius() {
    return cfg.getDouble("collision.bot-radius", 0.90);
  }

  public static double collisionBotStrength() {
    return cfg.getDouble("collision.bot-strength", 0.14);
  }

  public static boolean fakeChatEnabled() {
    return cfg.getBoolean("fake-chat.enabled", false);
  }

  public static boolean fakeChatRequirePlayer() {
    return cfg.getBoolean("fake-chat.require-player-online", true);
  }

  public static double fakeChatChance() {
    return cfg.getDouble("fake-chat.chance", 0.75);
  }

  public static int fakeChatIntervalMin() {
    return cfg.getInt("fake-chat.interval.min", 5);
  }

  public static int fakeChatIntervalMax() {
    return cfg.getInt("fake-chat.interval.max", 10);
  }

  public static boolean fakeChatTypingDelay() {
    return cfg.getBoolean("fake-chat.typing-delay", true);
  }

  public static double fakeChatBurstChance() {
    return cfg.getDouble("fake-chat.burst-chance", 0.12);
  }

  public static int fakeChatBurstDelayMin() {
    return cfg.getInt("fake-chat.burst-delay.min", 2);
  }

  public static int fakeChatBurstDelayMax() {
    return cfg.getInt("fake-chat.burst-delay.max", 5);
  }

  public static boolean fakeChatReplyToMentions() {
    return cfg.getBoolean("fake-chat.reply-to-mentions", true);
  }

  public static double fakeChatMentionReplyChance() {
    return cfg.getDouble("fake-chat.mention-reply-chance", 0.65);
  }

  public static int fakeChatReplyDelayMin() {
    return cfg.getInt("fake-chat.reply-delay.min", 2);
  }

  public static int fakeChatReplyDelayMax() {
    return cfg.getInt("fake-chat.reply-delay.max", 8);
  }

  public static int fakeChatStaggerInterval() {
    return cfg.getInt("fake-chat.stagger-interval", 3);
  }

  public static boolean fakeChatActivityVariation() {
    return cfg.getBoolean("fake-chat.activity-variation", true);
  }

  public static int fakeChatHistorySize() {
    return cfg.getInt("fake-chat.history-size", 5);
  }

  public static List<String> fakeChatMessages() {
    return BotMessageConfig.getMessages();
  }

  public static List<String> chatReplyMessages() {
    return BotMessageConfig.getReplyMessages();
  }

  public static List<String> chatBurstMessages() {
    return BotMessageConfig.getBurstMessages();
  }

  public static List<String> chatJoinReactionMessages() {
    return BotMessageConfig.getJoinReactionMessages();
  }

  public static List<String> chatDeathReactionMessages() {
    return BotMessageConfig.getDeathReactionMessages();
  }

  public static List<String> chatLeaveReactionMessages() {
    return BotMessageConfig.getLeaveReactionMessages();
  }

  public static List<String> chatKeywordReactionMessages(String key) {
    return BotMessageConfig.getKeywordReactionMessages(key);
  }

  public static String fakeChatRemoteFormat() {
    return cfg.getString("fake-chat.remote-format", "<yellow>{name}<dark_gray>: <white>{message}");
  }

  public static boolean fakeChatEventTriggersEnabled() {
    return cfg.getBoolean("fake-chat.event-triggers.enabled", true);
  }

  public static boolean fakeChatOnJoinEnabled() {
    return cfg.getBoolean("fake-chat.event-triggers.on-player-join.enabled", true);
  }

  public static double fakeChatOnJoinChance() {
    return cfg.getDouble("fake-chat.event-triggers.on-player-join.chance", 0.40);
  }

  public static int fakeChatOnJoinDelayMin() {
    return cfg.getInt("fake-chat.event-triggers.on-player-join.delay.min", 2);
  }

  public static int fakeChatOnJoinDelayMax() {
    return cfg.getInt("fake-chat.event-triggers.on-player-join.delay.max", 6);
  }

  public static boolean fakeChatOnDeathEnabled() {
    return cfg.getBoolean("fake-chat.event-triggers.on-death.enabled", true);
  }

  public static boolean fakeChatOnDeathPlayersOnly() {
    return cfg.getBoolean("fake-chat.event-triggers.on-death.players-only", false);
  }

  public static double fakeChatOnDeathChance() {
    return cfg.getDouble("fake-chat.event-triggers.on-death.chance", 0.30);
  }

  public static int fakeChatOnDeathDelayMin() {
    return cfg.getInt("fake-chat.event-triggers.on-death.delay.min", 1);
  }

  public static int fakeChatOnDeathDelayMax() {
    return cfg.getInt("fake-chat.event-triggers.on-death.delay.max", 4);
  }

  public static boolean fakeChatOnLeaveEnabled() {
    return cfg.getBoolean("fake-chat.event-triggers.on-player-leave.enabled", true);
  }

  public static double fakeChatOnLeaveChance() {
    return cfg.getDouble("fake-chat.event-triggers.on-player-leave.chance", 0.30);
  }

  public static int fakeChatOnLeaveDelayMin() {
    return cfg.getInt("fake-chat.event-triggers.on-player-leave.delay.min", 1);
  }

  public static int fakeChatOnLeaveDelayMax() {
    return cfg.getInt("fake-chat.event-triggers.on-player-leave.delay.max", 4);
  }

  public static boolean fakeChatOnPlayerChatEnabled() {
    return cfg.getBoolean("fake-chat.event-triggers.on-player-chat.enabled", false);
  }

  public static boolean fakeChatOnPlayerChatUseAi() {
    return cfg.getBoolean("fake-chat.event-triggers.on-player-chat.use-ai", true);
  }

  public static int fakeChatOnPlayerChatAiCooldown() {
    return cfg.getInt("fake-chat.event-triggers.on-player-chat.ai-cooldown", 30);
  }

  public static double fakeChatOnPlayerChatChance() {
    return cfg.getDouble("fake-chat.event-triggers.on-player-chat.chance", 0.25);
  }

  public static int fakeChatOnPlayerChatMaxBots() {
    return cfg.getInt("fake-chat.event-triggers.on-player-chat.max-bots", 1);
  }

  public static boolean fakeChatOnPlayerChatIgnoreShort() {
    return cfg.getBoolean("fake-chat.event-triggers.on-player-chat.ignore-short", true);
  }

  public static boolean fakeChatOnPlayerChatIgnoreCommands() {
    return cfg.getBoolean("fake-chat.event-triggers.on-player-chat.ignore-commands", true);
  }

  public static double fakeChatOnPlayerChatMentionChance() {
    return cfg.getDouble("fake-chat.event-triggers.on-player-chat.mention-player", 0.50);
  }

  public static int fakeChatOnPlayerChatDelayMin() {
    return cfg.getInt("fake-chat.event-triggers.on-player-chat.delay.min", 2);
  }

  public static int fakeChatOnPlayerChatDelayMax() {
    return cfg.getInt("fake-chat.event-triggers.on-player-chat.delay.max", 8);
  }

  public static boolean fakeChatBotToBotEnabled() {
    return cfg.getBoolean("fake-chat.bot-to-bot.enabled", true);
  }

  public static double fakeChatBotToBotReplyChance() {
    return cfg.getDouble("fake-chat.bot-to-bot.reply-chance", 0.35);
  }

  public static double fakeChatBotToBotChainChance() {
    return cfg.getDouble("fake-chat.bot-to-bot.chain-chance", 0.40);
  }

  public static int fakeChatBotToBotMaxChain() {
    return cfg.getInt("fake-chat.bot-to-bot.max-chain", 3);
  }

  public static int fakeChatBotToBotDelayMin() {
    return cfg.getInt("fake-chat.bot-to-bot.delay.min", 4);
  }

  public static int fakeChatBotToBotDelayMax() {
    return cfg.getInt("fake-chat.bot-to-bot.delay.max", 14);
  }

  public static int fakeChatBotToBotCooldown() {
    return cfg.getInt("fake-chat.bot-to-bot.cooldown", 8);
  }

  public static boolean fakeChatOnAdvancementEnabled() {
    return cfg.getBoolean("fake-chat.event-triggers.on-advancement.enabled", true);
  }

  public static double fakeChatOnAdvancementChance() {
    return cfg.getDouble("fake-chat.event-triggers.on-advancement.chance", 0.45);
  }

  public static int fakeChatOnAdvancementDelayMin() {
    return cfg.getInt("fake-chat.event-triggers.on-advancement.delay.min", 1);
  }

  public static int fakeChatOnAdvancementDelayMax() {
    return cfg.getInt("fake-chat.event-triggers.on-advancement.delay.max", 5);
  }

  public static boolean fakeChatOnFirstJoinEnabled() {
    return cfg.getBoolean("fake-chat.event-triggers.on-first-join.enabled", true);
  }

  public static double fakeChatOnFirstJoinChance() {
    return cfg.getDouble("fake-chat.event-triggers.on-first-join.chance", 0.70);
  }

  public static boolean fakeChatOnKillEnabled() {
    return cfg.getBoolean("fake-chat.event-triggers.on-kill.enabled", true);
  }

  public static double fakeChatOnKillChance() {
    return cfg.getDouble("fake-chat.event-triggers.on-kill.chance", 0.35);
  }

  public static int fakeChatOnKillDelayMin() {
    return cfg.getInt("fake-chat.event-triggers.on-kill.delay.min", 1);
  }

  public static int fakeChatOnKillDelayMax() {
    return cfg.getInt("fake-chat.event-triggers.on-kill.delay.max", 4);
  }

  public static boolean fakeChatOnHighLevelEnabled() {
    return cfg.getBoolean("fake-chat.event-triggers.on-high-level.enabled", true);
  }

  public static int fakeChatOnHighLevelMinLevel() {
    return cfg.getInt("fake-chat.event-triggers.on-high-level.min-level", 30);
  }

  public static double fakeChatOnHighLevelChance() {
    return cfg.getDouble("fake-chat.event-triggers.on-high-level.chance", 0.35);
  }

  public static int fakeChatOnHighLevelDelayMin() {
    return cfg.getInt("fake-chat.event-triggers.on-high-level.delay.min", 1);
  }

  public static int fakeChatOnHighLevelDelayMax() {
    return cfg.getInt("fake-chat.event-triggers.on-high-level.delay.max", 5);
  }

  public static List<String> chatBotToBotReplyMessages() {
    return BotMessageConfig.getBotToBotReplyMessages();
  }

  public static List<String> chatAdvancementReactionMessages() {
    return BotMessageConfig.getAdvancementReactionMessages();
  }

  public static List<String> chatFirstJoinReactionMessages() {
    return BotMessageConfig.getFirstJoinReactionMessages();
  }

  public static List<String> chatKillReactionMessages() {
    return BotMessageConfig.getKillReactionMessages();
  }

  public static List<String> chatHighLevelReactionMessages() {
    return BotMessageConfig.getHighLevelReactionMessages();
  }

  public static List<String> chatPlayerChatReactionMessages() {
    return BotMessageConfig.getPlayerChatReactionMessages();
  }

  public static boolean fakeChatKeywordReactionsEnabled() {
    return cfg.getBoolean("fake-chat.keyword-reactions.enabled", false);
  }

  @SuppressWarnings("unchecked")
  public static java.util.Map<String, String> fakeChatKeywordMap() {
    Object raw = cfg.get("fake-chat.keyword-reactions.keywords");
    if (raw instanceof java.util.Map<?, ?> m) {
      java.util.Map<String, String> result = new java.util.LinkedHashMap<>();
      for (java.util.Map.Entry<?, ?> e : m.entrySet()) {
        if (e.getKey() instanceof String k && e.getValue() instanceof String v) {
          result.put(k.toLowerCase(), v);
        }
      }
      return result;
    }
    return java.util.Map.of();
  }

  public static boolean mysqlEnabled() {
    return cfg.getBoolean("database.mysql-enabled", false);
  }

  public static boolean databaseEnabled() {
    return cfg.getBoolean("database.enabled", true);
  }

  public static String databaseMode() {
    String raw = cfg.getString("database.mode", "LOCAL");
    return raw.trim().equalsIgnoreCase("NETWORK") ? "NETWORK" : "LOCAL";
  }

  public static boolean isNetworkMode() {
    return databaseEnabled() && databaseMode().equalsIgnoreCase("NETWORK");
  }

  public static String configSyncMode() {
    String raw = cfg.getString("config-sync.mode", "DISABLED");
    return raw.trim().toUpperCase();
  }

  public static String mysqlHost() {
    return cfg.getString("database.mysql.host", "localhost");
  }

  public static int mysqlPort() {
    return cfg.getInt("database.mysql.port", 3306);
  }

  public static String mysqlDatabase() {
    return cfg.getString("database.mysql.database", "fpp");
  }

  public static String mysqlUsername() {
    return cfg.getString("database.mysql.username", "root");
  }

  public static String mysqlPassword() {
    return cfg.getString("database.mysql.password", "");
  }

  public static boolean mysqlUseSSL() {
    return cfg.getBoolean("database.mysql.use-ssl", false);
  }

  public static int mysqlPoolSize() {
    return cfg.getInt("database.mysql.pool-size", 5);
  }

  public static int mysqlConnTimeout() {
    return cfg.getInt("database.mysql.connection-timeout", 30000);
  }

  public static int dbLocationFlushInterval() {
    return cfg.getInt("database.location-flush-interval", 30);
  }

  public static int dbMaxHistoryRows() {
    return cfg.getInt("database.session-history.max-rows", 20);
  }

  public static String serverId() {

    String id = cfg.getString("database.server-id", null);

    if (id == null || id.isBlank()) {
      id = cfg.getString("server.id", "default");
    }
    return (id == null || id.isBlank()) ? "default" : id.trim();
  }

  public static double positionSyncDistance() {
    return cfg.getDouble("performance.position-sync-distance", 128.0);
  }

  public static boolean isBadwordFilterEnabled() {
    return cfg.getBoolean("badword-filter.enabled", true);
  }

  public static List<String> getBadwords() {
    Object raw = cfg.get("badword-filter.words");
    if (raw instanceof List<?> list) {
      return list.stream()
          .filter(o -> o instanceof String)
          .map(o -> (String) o)
          .filter(s -> !s.isBlank())
          .toList();
    }
    return List.of();
  }

  public static boolean isBadwordGlobalListEnabled() {
    return cfg.getBoolean("badword-filter.use-global-list", true);
  }

  public static String badwordGlobalListUrl() {
    return cfg.getString(
        "badword-filter.global-list-url", "https://www.cs.cmu.edu/~biglou/resources/bad-words.txt");
  }

  public static int badwordGlobalListTimeoutMs() {
    return Math.max(1000, cfg.getInt("badword-filter.global-list-timeout-ms", 5000));
  }

  public static List<String> getBadwordWhitelist() {
    Object raw = cfg.get("badword-filter.whitelist");
    if (raw instanceof List<?> list) {
      return list.stream()
          .filter(o -> o instanceof String)
          .map(o -> (String) o)
          .filter(s -> !s.isBlank())
          .toList();
    }
    return List.of();
  }

  public static boolean isBadwordAutoRenameEnabled() {
    return cfg.getBoolean("badword-filter.auto-rename", true);
  }

  public static boolean isBadwordAutoDetectionEnabled() {
    return cfg.getBoolean("badword-filter.auto-detection.enabled", true);
  }

  public static String getBadwordAutoDetectionMode() {
    return cfg.getString("badword-filter.auto-detection.mode", "normal").toLowerCase();
  }

  public static boolean isBotRightClickEnabled() {
    return cfg.getBoolean("bot-interaction.right-click-enabled", true);
  }

  public static boolean isBotShiftRightClickSettingsEnabled() {
    return cfg.getBoolean("bot-interaction.shift-right-click-settings", true);
  }

  public static boolean aiConversationsEnabled() {
    return cfg.getBoolean("ai-conversations.enabled", true);
  }

  public static String aiConversationsDefaultPersonality() {
    return cfg.getString(
        "ai-conversations.default-personality",
        "You are {bot_name}, a real Minecraft player chatting on a survival server. STRICT"
            + " RULES: Reply in 2-6 words max. Lowercase only, no full stops. Make 1-2"
            + " typos per message. Never use full sentences. Only talk about real vanilla"
            + " Minecraft things. Match the energy: 'hi' gets 'hey' not a paragraph.");
  }

  public static boolean aiTypingDelayEnabled() {
    return cfg.getBoolean("ai-conversations.typing-delay.enabled", true);
  }

  public static double aiTypingDelayBase() {
    return cfg.getDouble("ai-conversations.typing-delay.base", 1.0);
  }

  public static double aiTypingDelayPerChar() {
    return cfg.getDouble("ai-conversations.typing-delay.per-char", 0.07);
  }

  public static double aiTypingDelayMax() {
    return cfg.getDouble("ai-conversations.typing-delay.max", 5.0);
  }

  public static int aiConversationsMaxHistory() {
    return cfg.getInt("ai-conversations.max-history", 10);
  }

  public static int aiConversationsCooldown() {
    return cfg.getInt("ai-conversations.cooldown", 3);
  }

  public static boolean aiConversationsDebug() {
    return cfg.getBoolean("ai-conversations.debug", false) || isDebug();
  }

  public static void debug(String message) {
    FppLogger.debug(message);
  }

  public static void debugStartup(String message) {
    FppLogger.debug("STARTUP", debugStartup(), message);
  }

  public static void debugNms(String message) {
    FppLogger.debug("NMS", debugNms(), message);
  }

  public static void debugPackets(String message) {
    FppLogger.debug("PACKETS", debugPackets(), message);
  }

  public static void debugLuckPerms(String message) {
    FppLogger.debug("LP", debugLuckPerms(), message);
  }

  public static void debugNetwork(String message) {
    FppLogger.debug("NETWORK", debugNetwork(), message);
  }

  public static void debugConfigSync(String message) {
    FppLogger.debug("CONFIG_SYNC", debugConfigSync(), message);
  }

  public static void debugSkin(String message) {
    FppLogger.debug("SKIN", debugSkin(), message);
  }

  public static void debugDatabase(String message) {
    FppLogger.debug("DATABASE", debugDatabase(), message);
  }

  public static void debugChat(String message) {
    FppLogger.debug("CHAT", debugChat(), message);
  }

  public static void debugSwap(String message) {
    FppLogger.debug("SWAP", debugSwap(), message);
  }

  public static boolean nameTagBlockNickConflicts() {
    return cfg.getBoolean("nametag-integration.block-nick-conflicts", true);
  }

  public static boolean nameTagIsolation() {
    return cfg.getBoolean("nametag-integration.bot-isolation", true);
  }

  public static boolean nameTagSyncNickAsRename() {
    return cfg.getBoolean("nametag-integration.sync-nick-as-rename", false);
  }

  public static double attackMobDefaultRange() {
    return cfg.getDouble("attack-mob.default-range", 8.0);
  }

  public static String attackMobDefaultPriority() {
    return cfg.getString("attack-mob.default-priority", "nearest");
  }

  public static double attackMobSmoothRotationSpeed() {
    return cfg.getDouble("attack-mob.smooth-rotation-speed", 12.0);
  }

  public static int attackMobRetargetInterval() {
    return cfg.getInt("attack-mob.retarget-interval", 10);
  }

  public static boolean attackMobLineOfSight() {
    return cfg.getBoolean("attack-mob.line-of-sight", true);
  }
}
