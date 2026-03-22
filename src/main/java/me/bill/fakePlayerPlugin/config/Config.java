package me.bill.fakePlayerPlugin.config;

import me.bill.fakePlayerPlugin.FakePlayerPlugin;
import me.bill.fakePlayerPlugin.util.FppLogger;
import org.bukkit.Bukkit;
import org.bukkit.configuration.file.FileConfiguration;

import java.util.List;
import java.util.Map;

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

    /** The config-version stamped by the migration system. */
    public static int configVersion() {
        return cfg.getInt("config-version", 0);
    }

    /** Language file identifier, e.g. {@code "en"}. Maps to {@code language}. */
    public static String getLanguage() {
        return cfg.getString("language", "en");
    }

    /** Whether verbose debug logging is enabled. Maps to {@code debug}. */
    public static boolean isDebug() {
        return cfg.getBoolean("debug", false);
    }

    /** Whether the startup update checker is enabled. Maps to {@code update-checker.enabled}. */
    public static boolean updateCheckerEnabled() {
        return cfg.getBoolean("update-checker.enabled", true);
    }

    /** Whether anonymous FastStats metrics are enabled. Maps to {@code metrics.enabled}. */
    public static boolean metricsEnabled() {
        return cfg.getBoolean("metrics.enabled", true);
    }

    // ── Spawn cooldown  (spawn-cooldown) ──────────────────────────────────────

    /**
     * Seconds a user must wait between spawn commands.
     * 0 = no cooldown (default). Admins with {@code fpp.bypass.cooldown} are exempt.
     */
    public static int spawnCooldown() {
        return Math.max(0, cfg.getInt("spawn-cooldown", 0));
    }

    // ── Tab list  (tab-list.*) ────────────────────────────────────────────────

    /**
     * Whether bots appear as entries in the player tab list.
     * {@code true}  = bots are visible in the tab list (default).
     * {@code false} = bots are hidden from the tab list; they still count
     * toward the server player count shown in the multiplayer screen.
     */
    public static boolean tabListEnabled() {
        return cfg.getBoolean("tab-list.enabled", true);
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

    /**
     * When {@code true} the LuckPerms group weight is used to influence
     * tab-list ordering for bot entries. Set to {@code false} to disable
     * weight-aware ordering (tab ordering will be unmodified).
     */
    public static boolean luckpermsWeightOrderingEnabled() {
        return cfg.getBoolean("luckperms.weight-ordering-enabled", true);
    }

    /** Optional explicit LuckPerms group to treat bots as. If empty, plugin uses default/auto logic. */
    public static String luckpermsBotGroup() {
        return cfg.getString("luckperms.bot-group", "");
    }

    /** Optional single character prefixed before the numeric weight index in packet names. */
    public static String luckpermsPacketPrefixChar() {
        String s = cfg.getString("luckperms.packet-prefix-char", "");
        if (s == null) return "";
        return s;
    }

    /**
     * Weight offset applied to bot weights for tab-list ordering.
     * Negative values (default -10) demote bots below players of the same group.
     * Set to 0 to rank bots exactly with their group weight.
     */
    public static int luckpermsWeightOffset() {
        return cfg.getInt("luckperms.weight-offset", -10);
    }


    // ── Skin  (skin.*) ────────────────────────────────────────────────────────

    /**
     * Skin rendering mode:
     * <ul>
     *   <li>{@code "auto"}   — Paper resolves skin from Mojang automatically (recommended).
     *                          When {@code skin.guaranteed-skin} is true, bots whose names
     *                          have no Mojang account receive a fallback skin instead of Steve.</li>
     *   <li>{@code "custom"} — Plugin manages skin resolution via SkinRepository
     *                          (name-overrides, skin folder, config pool, Mojang fallback).</li>
     *   <li>{@code "off"}    — No skin; bots display the default Steve / Alex appearance.</li>
     * </ul>
     */
    public static String skinMode() {
        return cfg.getString("skin.mode", "auto").toLowerCase();
    }

    /** Clear the skin cache when {@code /fpp reload} is run. */
    public static boolean skinClearCacheOnReload() {
        return cfg.getBoolean("skin.clear-cache-on-reload", true);
    }

    /**
     * When {@code true}, bots always receive a skin — even if their name has no
     * Mojang account (generated names, user bots, etc.). The system falls back
     * through: folder skins → pool skins → {@link #skinFallbackName()} skin.
     * Config path: {@code skin.guaranteed-skin}.
     */
    public static boolean skinGuaranteed() {
        return cfg.getBoolean("skin.guaranteed-skin", true);
    }

    /**
     * A real Mojang username used as the absolute last-resort skin when all other
     * resolution fails and {@code skin.guaranteed-skin} is {@code true}.
     * Must be a valid, existing Minecraft account.
     * Config path: {@code skin.fallback-name}.
     */
    public static String skinFallbackName() {
        return cfg.getString("skin.fallback-name", "Notch");
    }

    /**
     * Pool of Minecraft player names whose skins will be randomly assigned
     * to bots in {@code custom} mode.
     * Config path: {@code skin.pool}
     */
    public static List<String> skinCustomPool() {
        // new key: skin.pool — fallback to old skin.custom.pool for compatibility
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

    /**
     * Exact-name overrides: bot-name → Minecraft-player-name.
     * Config path: {@code skin.overrides}
     */
    public static Map<String, String> skinCustomByName() {
        // new key: skin.overrides — fallback to old skin.custom.by-name
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

    /** Whether to scan the skins/ folder for PNG files. Config path: {@code skin.use-skin-folder}. */
    public static boolean skinUseSkinFolder() {
        return cfg.getBoolean("skin.use-skin-folder", true);
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

    /** Whether compatibility warnings should be sent to ops/admins when they join. */
    public static boolean warningsNotifyAdmins() { return cfg.getBoolean("messages.notify-admins-on-join", true); }

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

    /** Chunk radius kept loaded around each bot (mirrors server view-distance if set to 0). */
    public static int chunkLoadingRadius() {
        int r = cfg.getInt("chunk-loading.radius", 6);
        if (r <= 0) {
            // Use the server's actual simulation-distance as a sensible default
            r = Bukkit.getSimulationDistance();
        }
        return Math.max(1, r);
    }

    /**
     * How often (in ticks) the chunk-loader refreshes tickets.
     * Default 20 (once per second) — lower = more responsive to bot movement,
     * higher = less overhead for static bots.
     */
    public static int chunkLoadingUpdateInterval() { return cfg.getInt("chunk-loading.update-interval", 20); }

    // ── Head AI  (head-ai.*) ──────────────────────────────────────────────────

    /** Whether the head-AI rotation system is active. Set false to fully disable it. */
    public static boolean headAiEnabled() {
        return cfg.getBoolean("head-ai.enabled", true);
    }

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
    /** Master toggle to disable database I/O entirely. When false, persistence and DB stats are off. */
    public static boolean databaseEnabled()   { return cfg.getBoolean("database.enabled", true); }
    public static String  mysqlHost()         { return cfg.getString("database.mysql.host", "localhost"); }
    public static int     mysqlPort()         { return cfg.getInt("database.mysql.port", 3306); }
    public static String  mysqlDatabase()     { return cfg.getString("database.mysql.database", "fpp"); }
    public static String  mysqlUsername()     { return cfg.getString("database.mysql.username", "root"); }
    public static String  mysqlPassword()     { return cfg.getString("database.mysql.password", ""); }
    public static boolean mysqlUseSSL()       { return cfg.getBoolean("database.mysql.use-ssl", false); }
    public static int     mysqlPoolSize()     { return cfg.getInt("database.mysql.pool-size", 5); }
    public static int     mysqlConnTimeout()  { return cfg.getInt("database.mysql.connection-timeout", 30000); }

    /** Seconds between batch location flushes to DB. */
    public static int dbLocationFlushInterval() { return cfg.getInt("database.location-flush-interval", 30); }

    /** Max rows returned per DB history query. */
    public static int dbMaxHistoryRows() { return cfg.getInt("database.session-history.max-rows", 20); }

    // ── Utility ───────────────────────────────────────────────────────────────

    /** Log a message to console only when debug mode is on. */
    public static void debug(String message) { FppLogger.debug(message); }
}
