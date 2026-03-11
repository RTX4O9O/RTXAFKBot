package me.bill.fakePlayerPlugin.config;

import me.bill.fakePlayerPlugin.FakePlayerPlugin;
import me.bill.fakePlayerPlugin.util.FppLogger;
import org.bukkit.configuration.file.FileConfiguration;

import java.util.List;

/**
 * Central accessor for {@code config.yml}.
 * All methods read live from the cached {@link FileConfiguration} so values
 * are always up-to-date after {@code /fpp reload}.
 *
 * <p>Key paths mirror the structure of {@code config.yml} exactly — every
 * section header in the YAML maps to the prefix used here.
 */
public final class Config {

    private static FakePlayerPlugin  plugin;
    private static FileConfiguration cfg;

    private Config() {}

    // ── Lifecycle ─────────────────────────────────────────────────────────────

    public static void init(FakePlayerPlugin instance) {
        plugin = instance;
        reload();
    }

    public static void reload() {
        plugin.saveDefaultConfig();
        plugin.reloadConfig();
        cfg = plugin.getConfig();
        cfg.options().copyDefaults(true);
        plugin.saveConfig();
    }

    // ── Language & Debug ──────────────────────────────────────────────────────

    /** Language file identifier, e.g. {@code "en"}. Maps to {@code language}. */
    public static String getLanguage() {
        return cfg.getString("language", "en");
    }

    /** Whether verbose debug logging is enabled. Maps to {@code debug}. */
    public static boolean isDebug() {
        return cfg.getBoolean("debug", false);
    }

    // ── Bot Limits  (limits.*) ────────────────────────────────────────────────

    /** Maximum bots allowed at once. 0 = unlimited. */
    public static int maxBots() {
        return cfg.getInt("limits.max-bots", 1000);
    }

    /** Default personal bot limit for {@code fpp.user.spawn} players. */
    public static int userBotLimit() {
        return cfg.getInt("limits.user-bot-limit", 1);
    }

    /** Spawn-count presets shown in tab-complete for admin spawn. */
    public static List<String> spawnCountPresetsAdmin() {
        List<?> raw = cfg.getList("limits.spawn-presets", List.of(1, 5, 10, 15, 20));
        return raw.stream().map(Object::toString).toList();
    }

    // ── Bot Names  (bot-name.*) ───────────────────────────────────────────────

    /** MiniMessage display-name format for admin-spawned bots. */
    public static String adminBotNameFormat() {
        return cfg.getString("bot-name.admin-format", "<#0079FF>[bot-{bot_name}]</#0079FF>");
    }

    /** MiniMessage display-name format for user-spawned bots. */
    public static String userBotNameFormat() {
        return cfg.getString("bot-name.user-format", "<gray>[bot-{spawner}-{num}]</gray>");
    }

    // ── LuckPerms  (luckperms.*) ──────────────────────────────────────────────

    /**
     * When {@code true} the LuckPerms default-group prefix is prepended to
     * every bot display name. Set to {@code false} to use format colors only.
     */
    public static boolean luckpermsUsePrefix() {
        return cfg.getBoolean("luckperms.use-prefix", true);
    }

    // ── Skin  (skin.*) ────────────────────────────────────────────────────────

    /**
     * Skin rendering mode: {@code "auto"}, {@code "fetch"}, or {@code "disabled"}.
     */
    public static String skinMode() {
        return cfg.getString("skin.mode", "auto").toLowerCase();
    }

    /** Clear the skin-fetch cache on {@code /fpp reload} (fetch mode only). */
    public static boolean skinClearCacheOnReload() {
        return cfg.getBoolean("skin.clear-cache-on-reload", true);
    }

    // ── Body  (body.*) ────────────────────────────────────────────────────────

    /** Whether bots spawn a visible Mannequin body in the world. */
    public static boolean spawnBody() {
        return cfg.getBoolean("body.enabled", true);
    }

    // ── Persistence  (persistence.*) ─────────────────────────────────────────

    /** Save bots on shutdown and restore them on next startup. */
    public static boolean persistOnRestart() {
        return cfg.getBoolean("persistence.enabled", true);
    }

    // ── Name pool (sourced from bot-names.yml) ────────────────────────────────

    /** Random name pool used when spawning bots without a custom name. */
    public static List<String> namePool() {
        return BotNameConfig.getNames();
    }

    // ── Join / Leave Delays  (join-delay.* / leave-delay.*) ──────────────────

    /** Minimum join-delay in ticks. */
    public static int joinDelayMin()  { return cfg.getInt("join-delay.min", 0); }

    /** Maximum join-delay in ticks. */
    public static int joinDelayMax()  { return cfg.getInt("join-delay.max", 40); }

    /** Minimum leave-delay in ticks. */
    public static int leaveDelayMin() { return cfg.getInt("leave-delay.min", 0); }

    /** Maximum leave-delay in ticks. */
    public static int leaveDelayMax() { return cfg.getInt("leave-delay.max", 40); }

    // ── Messages  (messages.*) ────────────────────────────────────────────────

    /** Broadcast a vanilla-style join message when a bot is spawned. */
    public static boolean joinMessage()  { return cfg.getBoolean("messages.join-message", true); }

    /** Broadcast a vanilla-style leave message when a bot is removed. */
    public static boolean leaveMessage() { return cfg.getBoolean("messages.leave-message", true); }

    /** Broadcast a kill message when a player kills a bot. */
    public static boolean killMessage()  { return cfg.getBoolean("messages.kill-message", false); }

    // ── Combat  (combat.*) ────────────────────────────────────────────────────

    /** Base health bots spawn with. */
    public static double maxHealth()   { return cfg.getDouble("combat.max-health", 20.0); }

    /** Play the player hurt sound when a bot takes damage. */
    public static boolean hurtSound() { return cfg.getBoolean("combat.hurt-sound", true); }

    // ── Death & Respawn  (death.*) ────────────────────────────────────────────

    /** Whether bots respawn on death. */
    public static boolean respawnOnDeath() { return cfg.getBoolean("death.respawn-on-death", false); }

    /** Ticks to wait before a dead bot respawns. */
    public static int respawnDelay()       { return cfg.getInt("death.respawn-delay", 60); }

    /** Suppress item drops on bot death. */
    public static boolean suppressDrops()  { return cfg.getBoolean("death.suppress-drops", true); }

    // ── Chunk Loading  (chunk-loading.*) ─────────────────────────────────────

    /** Whether bots keep chunks loaded around them. */
    public static boolean chunkLoadingEnabled() { return cfg.getBoolean("chunk-loading.enabled", true); }

    /** Chunk radius kept loaded around each bot. */
    public static int chunkLoadingRadius()      { return cfg.getInt("chunk-loading.radius", 6); }

    // ── Head AI  (head-ai.*) ──────────────────────────────────────────────────

    /** Radius in blocks within which a bot looks at the nearest player. */
    public static double headAiLookRange() { return cfg.getDouble("head-ai.look-range", 8.0); }

    /** Head rotation interpolation speed (0.0–1.0). */
    public static float headAiTurnSpeed()  { return (float) cfg.getDouble("head-ai.turn-speed", 0.3); }

    // ── Collision / Push  (collision.*) ──────────────────────────────────────

    public static double collisionWalkRadius()   { return cfg.getDouble("collision.walk-radius", 0.85); }
    public static double collisionWalkStrength() { return cfg.getDouble("collision.walk-strength", 0.22); }
    public static double collisionMaxHoriz()     { return cfg.getDouble("collision.max-horizontal-speed", 0.30); }
    public static double collisionHitStrength()  { return cfg.getDouble("collision.hit-strength", 0.45); }
    public static double collisionBotRadius()    { return cfg.getDouble("collision.bot-radius", 0.90); }
    public static double collisionBotStrength()  { return cfg.getDouble("collision.bot-strength", 0.14); }

    // ── Bot Swap  (swap.*) ────────────────────────────────────────────────────

    public static boolean swapEnabled()        { return cfg.getBoolean("swap.enabled", false); }
    public static int swapSessionMin()         { return cfg.getInt("swap.session-min", 120); }
    public static int swapSessionMax()         { return cfg.getInt("swap.session-max", 600); }
    public static int swapRejoinDelayMin()     { return cfg.getInt("swap.rejoin-delay-min", 5); }
    public static int swapRejoinDelayMax()     { return cfg.getInt("swap.rejoin-delay-max", 45); }
    public static int swapJitter()             { return cfg.getInt("swap.jitter", 30); }
    public static double swapReconnectChance() { return cfg.getDouble("swap.reconnect-chance", 0.15); }
    public static int swapAfkKickChance()      { return cfg.getInt("swap.afk-kick-chance", 5); }
    public static boolean swapFarewellChat()   { return cfg.getBoolean("swap.farewell-chat", true); }
    public static boolean swapGreetingChat()   { return cfg.getBoolean("swap.greeting-chat", true); }
    public static boolean swapTimeOfDayBias()  { return cfg.getBoolean("swap.time-of-day-bias", true); }

    // ── Fake Chat  (fake-chat.*) ──────────────────────────────────────────────

    public static boolean fakeChatEnabled()       { return cfg.getBoolean("fake-chat.enabled", false); }
    public static boolean fakeChatRequirePlayer() { return cfg.getBoolean("fake-chat.require-player-online", true); }
    public static double  fakeChatChance()         { return cfg.getDouble("fake-chat.chance", 0.75); }
    public static int     fakeChatIntervalMin()    { return cfg.getInt("fake-chat.interval.min", 5); }
    public static int     fakeChatIntervalMax()    { return cfg.getInt("fake-chat.interval.max", 10); }

    /** Messages bots can randomly send — loaded from bot-messages.yml. */
    public static List<String> fakeChatMessages() { return BotMessageConfig.getMessages(); }

    // ── Database  (database.*) ────────────────────────────────────────────────

    /** Use MySQL instead of the built-in SQLite. */
    public static boolean mysqlEnabled()      { return cfg.getBoolean("database.mysql-enabled", false); }
    public static String  mysqlHost()         { return cfg.getString("database.mysql.host", "localhost"); }
    public static int     mysqlPort()         { return cfg.getInt("database.mysql.port", 3306); }
    public static String  mysqlDatabase()     { return cfg.getString("database.mysql.database", "fpp"); }
    public static String  mysqlUsername()     { return cfg.getString("database.mysql.username", "root"); }
    public static String  mysqlPassword()     { return cfg.getString("database.mysql.password", ""); }
    public static boolean mysqlUseSSL()       { return cfg.getBoolean("database.mysql.use-ssl", false); }
    public static int     mysqlPoolSize()     { return cfg.getInt("database.mysql.pool-size", 5); }
    public static int     mysqlConnTimeout()  { return cfg.getInt("database.mysql.connection-timeout", 30000); }

    // ── Utility ───────────────────────────────────────────────────────────────

    /** Log a message to console only when debug mode is on. */
    public static void debug(String message) { FppLogger.debug(message); }
}
