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
 * <p>Key paths mirror the structure of {@code config.yml} exactly - every
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
        // ── Locked / Coming-Soon features ─────────────────────────────────────
        // body.enabled is always forced true - bodyless mode is not yet available.
        // skin.guaranteed-skin is always forced false - skin system is coming soon.
        // pvp-ai.pvp is always forced false - PVP bots are dev-only for now.
        // If a user edits these manually, the next reload auto-reverts them.
        cfg.set("body.enabled", true);
        cfg.set("skin.guaranteed-skin", false);
        cfg.set("pvp-ai.pvp", false);
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
        return cfg != null && cfg.getBoolean("debug", false);
    }

    private static boolean debugFlag(String path) {
        return cfg != null && cfg.getBoolean(path, false);
    }

    public static boolean debugStartup()    { return isDebug() || debugFlag("logging.debug.startup"); }
    public static boolean debugNms()        { return isDebug() || debugFlag("logging.debug.nms"); }
    public static boolean debugPackets()    { return isDebug() || debugFlag("logging.debug.packets"); }
    public static boolean debugLuckPerms()  { return isDebug() || debugFlag("logging.debug.luckperms"); }
    public static boolean debugNetwork()    { return isDebug() || debugFlag("logging.debug.network"); }
    public static boolean debugConfigSync() { return isDebug() || debugFlag("logging.debug.config-sync"); }
    public static boolean debugSkin()       { return isDebug() || debugFlag("logging.debug.skin"); }
    public static boolean debugDatabase()   { return isDebug() || debugFlag("logging.debug.database"); }
    public static boolean debugChat()       { return isDebug() || debugFlag("logging.debug.chat"); }
    public static boolean debugSwap()      { return isDebug() || debugFlag("logging.debug.swap"); }

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
    // Bots are now real NMS ServerPlayer entities. LuckPerms handles prefix,
    // suffix, and tab-list ordering automatically when it detects them as online
    // players. The only config we need is which LP group to assign bots to.

    /**
     * Optional LP group to assign every new bot at spawn time.
     * When blank (default), bots receive LP's built-in "default" group automatically.
     * Example: {@code "bot"} to put all bots in a custom LP group called "bot".
     */
    public static String luckpermsDefaultGroup() {
        return cfg.getString("luckperms.default-group", "");
    }


    // ── Skin  (skin.*) ────────────────────────────────────────────────────────

    /**
     * Skin rendering mode:
     * <ul>
     *   <li>{@code "auto"}   - Paper resolves skin from Mojang automatically (recommended).
     *                          When {@code skin.guaranteed-skin} is true, bots whose names
     *                          have no Mojang account receive a fallback skin instead of Steve.</li>
     *   <li>{@code "custom"} - Plugin manages skin resolution via SkinRepository
     *                          (name-overrides, skin folder, config pool, Mojang fallback).</li>
     *   <li>{@code "off"}    - No skin; bots display the default Steve / Alex appearance.</li>
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
     * When {@code true}, bots always receive a skin - even if their name has no
     * Mojang account (generated names, user bots, etc.).
     * When {@code false} (default), bots with no matching Mojang account display
     * the default Steve / Alex Minecraft skin.
     * Config path: {@code skin.guaranteed-skin}.
     */
    public static boolean skinGuaranteed() {
        return cfg.getBoolean("skin.guaranteed-skin", false);
    }

    /**
     * A real Mojang username used as the absolute last-resort skin when all other
     * resolution fails and {@code skin.guaranteed-skin} is {@code true}.
     * Must be a valid, existing Minecraft account.
     * Config path: {@code skin.fallback-name}.
     */
    public static String skinFallbackName() {
        return cfg.getString("skin.fallback-name", "");
    }

    /**
     * Pool of Minecraft player names whose skins will be randomly assigned
     * when a bot's name has no Mojang account (auto mode fallback).
     * Config path: {@code skin.fallback-pool}
     */
    public static List<String> skinFallbackPool() {
        Object raw = cfg.get("skin.fallback-pool");
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
     * Pool of Minecraft player names whose skins will be randomly assigned
     * to bots in {@code custom} mode.
     * Config path: {@code skin.pool}
     */
    public static List<String> skinCustomPool() {
        // new key: skin.pool - fallback to old skin.custom.pool for compatibility
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
        // new key: skin.overrides - fallback to old skin.custom.by-name
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

    /** Whether bots spawn a visible NMS ServerPlayer body in the world. */
    public static boolean spawnBody() {
        return cfg.getBoolean("body.enabled", true);
    }

    /**
     * Whether players and other entities can push bot bodies.
     * {@code false} = NMS ServerPlayer is immovable; all collision push logic is skipped.
     */
    public static boolean bodyPushable() {
        return cfg.getBoolean("body.pushable", true);
    }

    /**
     * Whether bot bodies can take damage from players and entities.
     * {@code false} = bot is immune to entity/player-sourced damage but
     * still takes environmental damage (fall, fire, drowning, lava, etc.).
     * {@code true} = bot takes all damage types like a real player.
     */
    public static boolean bodyDamageable() {
        return cfg.getBoolean("body.damageable", true);
    }

    /**
     * Whether bot bodies can pick up items from the ground.
     * {@code false} (default) = bots do not pick up items; all item entities pass through.
     * {@code true} = bots pick up items exactly like a real player.
     * Config path: {@code body.pick-up-items}.
     */
    public static boolean bodyPickUpItems() {
        return cfg.getBoolean("body.pick-up-items", false);
    }

    /**
     * Whether bot bodies can pick up XP orbs from the ground.
     * {@code false} (default) = bots do not pick up XP; all XP orbs pass through.
     * {@code true} = bots pick up XP orbs exactly like a real player.
     * Config path: {@code body.pick-up-xp}.
     */
    public static boolean bodyPickUpXp() {
        return cfg.getBoolean("body.pick-up-xp", true);
    }

    /**
     * Whether bots drop their inventory contents and XP to the ground when despawned
     * via {@code /fpp despawn} or the in-game settings GUI delete button.
     * {@code false} (default) = items and XP vanish silently on despawn.
     * {@code true} = all inventory items and XP orbs are dropped at the bot's location.
     * Config path: {@code body.drop-items-on-despawn}.
     */
    public static boolean dropItemsOnDespawn() {
        return cfg.getBoolean("body.drop-items-on-despawn", false);
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

    // ── Swap / Session Rotation  (swap.*) ─────────────────────────────────────

    /** Whether bot session rotation (swap in/out) is enabled. */
    public static boolean swapEnabled() { return cfg.getBoolean("swap.enabled", false); }

    /** Minimum session duration in seconds before a bot leaves. */
    public static int swapSessionMin() { return cfg.getInt("swap.session.min", 60); }

    /** Maximum session duration in seconds before a bot leaves. */
    public static int swapSessionMax() { return cfg.getInt("swap.session.max", 300); }

    /** Minimum absence duration in seconds before a bot rejoins. */
    public static int swapAbsenceMin() { return cfg.getInt("swap.absence.min", 30); }

    /** Maximum absence duration in seconds before a bot rejoins. */
    public static int swapAbsenceMax() { return cfg.getInt("swap.absence.max", 120); }

    /** Maximum number of bots allowed to be offline simultaneously (0 = unlimited). */
    public static int swapMaxSwappedOut() { return cfg.getInt("swap.max-swapped-out", 0); }

    /** Whether bots say farewell messages when leaving. */
    public static boolean swapFarewellChat() { return cfg.getBoolean("swap.farewell-chat", true); }

    /** Whether bots say greeting messages when rejoining. */
    public static boolean swapGreetingChat() { return cfg.getBoolean("swap.greeting-chat", true); }

    /** Whether bots attempt to reuse their same name on rejoin. */
    public static boolean swapSameNameOnRejoin() { return cfg.getBoolean("swap.same-name-on-rejoin", true); }

    /** Minimum bots that must remain online - swap skips if removing one would go below. 0 = disabled. */
    public static int swapMinOnline() { return cfg.getInt("swap.min-online", 0); }

    /** Whether a failed rejoin spawn is automatically retried after a delay. */
    public static boolean swapRetryRejoin() { return cfg.getBoolean("swap.retry-rejoin", true); }

    /** Seconds to wait before retrying a failed rejoin spawn. */
    public static int swapRetryDelay() { return cfg.getInt("swap.retry-delay", 60); }

    // ── Peak Hours  (peak-hours.*) ────────────────────────────────────────────

    /**
     * Master toggle for the peak-hours bot pool scheduling system.
     * When {@code true} the system periodically adjusts how many bots are online
     * based on real-world time windows.  Requires {@link #swapEnabled()}.
     * Maps to {@code peak-hours.enabled}.
     */
    public static boolean peakHoursEnabled() {
        return cfg.getBoolean("peak-hours.enabled", false);
    }

    /**
     * Java {@link java.time.ZoneId} string used to interpret schedule times.
     * Defaults to {@code "UTC"}.  Maps to {@code peak-hours.timezone}.
     */
    public static String peakHoursTimezone() {
        return cfg.getString("peak-hours.timezone", "UTC");
    }

    /**
     * Seconds over which bot join/leave transitions are staggered.
     * Maps to {@code peak-hours.stagger-seconds}.
     */
    public static int peakHoursStaggerSeconds() {
        return cfg.getInt("peak-hours.stagger-seconds", 30);
    }

    /**
     * Default daily schedule - list of {@code {start, end, fraction}} maps.
     * Maps to {@code peak-hours.schedule}.
     */
    public static java.util.List<java.util.Map<?, ?>> peakHoursSchedule() {
        return cfg.getMapList("peak-hours.schedule");
    }

    /**
     * Day-of-week schedule overrides.
     * Keys are uppercase {@link java.time.DayOfWeek} names (e.g. {@code "SATURDAY"}).
     * Each value is the same {@code [{start, end, fraction}]} list as the default schedule.
     * Returns {@code null} when the section is absent.
     * Maps to {@code peak-hours.day-overrides}.
     */
    public static org.bukkit.configuration.ConfigurationSection peakHoursDayOverrides() {
        return cfg.getConfigurationSection("peak-hours.day-overrides");
    }

    /**
     * Absolute minimum number of AFK bots that must remain online at all times,
     * regardless of the computed fraction. {@code 0} = no floor (disabled).
     * Maps to {@code peak-hours.min-online}.
     */
    public static int peakHoursMinOnline() {
        return Math.max(0, cfg.getInt("peak-hours.min-online", 0));
    }


    /**
     * When {@code true}, broadcasts a notification to all online players with
     * the {@code fpp.peaks} permission whenever the active time window changes.
     * Default: {@code false}.
     * Maps to {@code peak-hours.notify-transitions}.
     */
    public static boolean peakHoursNotifyTransitions() {
        return cfg.getBoolean("peak-hours.notify-transitions", false);
    }

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
    public static boolean suppressDrops()  { return cfg.getBoolean("death.suppress-drops", false); }

    // ── Chunk Loading  (chunk-loading.*) ─────────────────────────────────────

    /** Whether bots keep chunks loaded around them. */
    public static boolean chunkLoadingEnabled() { return cfg.getBoolean("chunk-loading.enabled", true); }

    /**
     * Resolved chunk radius to keep loaded around each bot.
     * <ul>
     *   <li>{@code "auto"} (or any non-numeric string) → server simulation-distance</li>
     *   <li>{@code 0} → chunk loading disabled — bots add no tickets at all</li>
     *   <li>{@code N ≥ 1} → fixed radius of N chunks</li>
     * </ul>
     */
    public static int chunkLoadingRadius() {
        Object raw = cfg.get("chunk-loading.radius");
        if (raw instanceof Number n) {
            return Math.max(0, n.intValue()); // 0 = disabled, positive = fixed radius
        }
        // "auto" string (or null / unexpected type) → match server simulation-distance
        return Bukkit.getSimulationDistance();
    }

    /**
     * How often (in ticks) the chunk-loader refreshes tickets.
     * Default 20 (once per second) - lower = more responsive to bot movement,
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

    /**
     * Run the head-AI player-scan and raycast every N game ticks instead of every tick.
     * Higher values reduce the O(bots × players) raycast budget at the cost of slightly
     * less snappy head tracking.  Default: {@code 3} (every 3rd tick = ~6.7 scans/s).
     * Maps to {@code head-ai.tick-rate}.
     */
    public static int headAiTickRate() {
        return Math.max(1, cfg.getInt("head-ai.tick-rate", 3));
    }

    // ── Swim AI  (swim-ai.*) ──────────────────────────────────────────────────

    /**
     * Whether the swim-AI system is active.
     * When {@code true}, bots automatically swim upward whenever they are in water
     * or lava, mimicking a player holding the jump/spacebar key.
     */
    public static boolean swimAiEnabled() { return cfg.getBoolean("swim-ai.enabled", true); }

    // ── Pathfinding  (pathfinding.*) - shared global navigation ──────────────

    /** Allow bots to perform sprint-jumps across 1–2 block gaps during navigation. */
    public static boolean pathfindingParkour() {
        return cfg.getBoolean("pathfinding.parkour", false);
    }

    /** Allow bots to break blocking solid blocks that obstruct their navigation path. */
    public static boolean pathfindingBreakBlocks() {
        return cfg.getBoolean("pathfinding.break-blocks", false);
    }

    /** Allow bots to place blocks to bridge 1-block gaps during navigation. */
    public static boolean pathfindingPlaceBlocks() {
        return cfg.getBoolean("pathfinding.place-blocks", false);
    }

    /**
     * Material name used when placing bridge blocks (requires {@code pathfinding.place-blocks=true}).
     * Falls back to {@code DIRT} if the configured value is not a valid solid block.
     */
    public static String pathfindingPlaceMaterial() {
        return cfg.getString("pathfinding.place-material", "DIRT");
    }

    /** Horizontal distance at which a navigation request is considered arrived. */
    public static double pathfindingArrivalDistance() {
        return cfg.getDouble("pathfinding.arrival-distance", 1.2);
    }

    /** Horizontal distance at which a patrol waypoint is considered reached. */
    public static double pathfindingPatrolArrivalDistance() {
        return cfg.getDouble("pathfinding.patrol-arrival-distance", 1.5);
    }

    /** Horizontal waypoint snap radius used while stepping through an active path. */
    public static double pathfindingWaypointArrivalDistance() {
        return cfg.getDouble("pathfinding.waypoint-arrival-distance", 0.65);
    }

    /** Distance above which bots sprint during navigation. */
    public static double pathfindingSprintDistance() {
        return cfg.getDouble("pathfinding.sprint-distance", 6.0);
    }

    /** Target-movement distance that forces a path recalculation for moving goals. */
    public static double pathfindingFollowRecalcDistance() {
        return cfg.getDouble("pathfinding.follow-recalc-distance", 3.5);
    }

    /** Ticks between heartbeat path recalculations. */
    public static int pathfindingRecalcInterval() {
        return Math.max(1, cfg.getInt("pathfinding.recalc-interval", 60));
    }

    /** Consecutive low-movement ticks before the navigator treats a bot as stuck. */
    public static int pathfindingStuckTicks() {
        return Math.max(1, cfg.getInt("pathfinding.stuck-ticks", 8));
    }

    /** Minimum horizontal movement required per tick before the bot is considered stuck. */
    public static double pathfindingStuckThreshold() {
        return Math.max(0.001, cfg.getDouble("pathfinding.stuck-threshold", 0.04));
    }

    /** Ticks spent breaking a blocking block while following a BREAK path move. */
    public static int pathfindingBreakTicks() {
        return Math.max(1, cfg.getInt("pathfinding.break-ticks", 15));
    }

    /** Ticks spent placing a bridge block while following a PLACE path move. */
    public static int pathfindingPlaceTicks() {
        return Math.max(1, cfg.getInt("pathfinding.place-ticks", 5));
    }

    /** Maximum straight-line range in blocks for attempting A* pathfinding. */
    public static int pathfindingMaxRange() {
        return Math.max(8, cfg.getInt("pathfinding.max-range", 64));
    }

    /** Maximum A* nodes for plain walking/jumping searches. */
    public static int pathfindingMaxNodes() {
        return Math.max(100, cfg.getInt("pathfinding.max-nodes", 2000));
    }

    /** Maximum A* nodes when advanced movement options are enabled. */
    public static int pathfindingMaxNodesExtended() {
        return Math.max(pathfindingMaxNodes(), cfg.getInt("pathfinding.max-nodes-extended", 4000));
    }

    // ── PVP AI  (pvp-ai.*) ────────────────────────────────────────────────────

    /**
     * Master PVP enable flag. Always {@code false} - forced by {@link #reload()}.
     * While {@code false}, only the designated developer UUID may spawn PVP bots.
     * Maps to {@code pvp-ai.pvp}.
     */
    public static boolean pvpAiEnabled() {
        return cfg.getBoolean("pvp-ai.pvp", false);
    }

    /**
     * Difficulty level for the PVP AI.
     * Maps to {@code pvp-ai.difficulty} - one of {@code "easy"}, {@code "medium"},
     * {@code "hard"}, {@code "tier1"}, or {@code "hacker"}.
     * Controls attack reach, timing precision, and crit/s-tap frequency.
     */
    public static String pvpAiDifficulty() {
        return cfg.getString("pvp-ai.difficulty", "medium").toLowerCase();
    }

    /**
     * Combat mode for PVP bots: {@code "crystal"} or {@code "sword"}.
     * Maps to {@code pvp-ai.combat-mode}.
     */
    public static String pvpAiCombatMode() {
        return cfg.getString("pvp-ai.combat-mode", "crystal").toLowerCase();
    }

    /**
     * Gear type the bot receives at spawn: {@code "diamond"} or {@code "netherite"}.
     * Maps to {@code pvp-ai.gear}.
     */
    public static String pvpAiGear() {
        return cfg.getString("pvp-ai.gear", "netherite").toLowerCase();
    }

    /**
     * Whether PVP bots start in defensive mode (retaliate only when attacked first).
     * {@code false} = aggressive - bot immediately attacks any player within detect-range.
     * Maps to {@code pvp-ai.defensive-mode}.
     */
    public static boolean pvpAiDefensiveMode() {
        return cfg.getBoolean("pvp-ai.defensive-mode", true);
    }

    /**
     * Detection radius in blocks - how far the bot scans for player targets.
     * Maps to {@code pvp-ai.detect-range}.
     */
    public static double pvpAiDetectRange() {
        return cfg.getDouble("pvp-ai.detect-range", 32.0);
    }

    /** Kit preset loaded at bot spawn: kit1 / kit2 / kit3 / kit4. */
    public static String pvpAiKit() {
        return cfg.getString("pvp-ai.kit", "kit1").toLowerCase();
    }

    // ── Combat techniques ─────────────────────────────────────────────────

    /** Whether the bot lands critical hits by falling during attacks. */
    public static boolean pvpAiCritting()      { return cfg.getBoolean("pvp-ai.critting",      true);  }

    /** Whether the bot taps S during swing to reset attack cooldown (s-tap). */
    public static boolean pvpAiSTapping()      { return cfg.getBoolean("pvp-ai.s-tapping",     true);  }

    /** Whether the bot strafes (circles) around the target while fighting. */
    public static boolean pvpAiStrafing()      { return cfg.getBoolean("pvp-ai.strafing",      true);  }

    /** Whether the bot equips and uses a shield to block incoming hits. */
    public static boolean pvpAiShielding()     { return cfg.getBoolean("pvp-ai.shielding",     false); }

    /** Whether the bot has Speed and Strength potion effects active. */
    public static boolean pvpAiSpeedBuffs()    { return cfg.getBoolean("pvp-ai.speed-buffs",   true);  }

    /** Whether the bot jump-resets for the W-tap knockback bonus on hits. */
    public static boolean pvpAiJumpReset()     { return cfg.getBoolean("pvp-ai.jump-reset",    true);  }

    /** Whether combat techniques are randomised each round for unpredictability. */
    public static boolean pvpAiRandom()        { return cfg.getBoolean("pvp-ai.random",        false); }

    /** Whether the bot sprints toward the target during combat. */
    public static boolean pvpAiSprint()        { return cfg.getBoolean("pvp-ai.sprint",        true);  }

    /** Whether the bot backs away while swinging to control knockback. */
    public static boolean pvpAiWalkBackwards() { return cfg.getBoolean("pvp-ai.walk-backwards",false); }

    // ── Abilities ─────────────────────────────────────────────────────────

    /** Whether the bot throws ender pearls to close the gap or escape. */
    public static boolean pvpAiPearl()         { return cfg.getBoolean("pvp-ai.pearl",         true);  }

    /** Whether the bot spams pearls in bursts for aggressive gap-closing. */
    public static boolean pvpAiPearlSpam()     { return cfg.getBoolean("pvp-ai.pearl-spam",    false); }

    /** Whether the bot pathfinds to an obsidian hole to protect itself. */
    public static boolean pvpAiHoleMode()      { return cfg.getBoolean("pvp-ai.hole-mode",     false); }

    /** Whether the bot automatically re-equips a totem after popping one. */
    public static boolean pvpAiAutoRefill()    { return cfg.getBoolean("pvp-ai.auto-refill",   true);  }

    /** Whether the bot automatically respawns and rejoins after death. */
    public static boolean pvpAiAutoRespawn()   { return cfg.getBoolean("pvp-ai.auto-respawn",  true);  }

    /** Whether the bot stays invulnerable during a grace period at spawn. */
    public static boolean pvpAiSpawnProtection() { return cfg.getBoolean("pvp-ai.spawn-protection", true); }


    // ── Collision / Push  (collision.*) ──────────────────────────────────────

    public static double collisionWalkRadius()    { return cfg.getDouble("collision.walk-radius", 0.85); }
    public static double collisionWalkStrength()  { return cfg.getDouble("collision.walk-strength", 0.22); }
    public static double collisionMaxHoriz()      { return cfg.getDouble("collision.max-horizontal-speed", 0.30); }
    public static double collisionHitStrength()   { return cfg.getDouble("collision.hit-strength", 0.45); }
    public static double collisionHitMaxHoriz()   { return cfg.getDouble("collision.hit-max-horizontal-speed", 0.80); }
    public static double collisionBotRadius()     { return cfg.getDouble("collision.bot-radius", 0.90); }
    public static double collisionBotStrength()   { return cfg.getDouble("collision.bot-strength", 0.14); }


    // ── Fake Chat  (fake-chat.*) ──────────────────────────────────────────────

    public static boolean fakeChatEnabled()          { return cfg.getBoolean("fake-chat.enabled", false); }
    public static boolean fakeChatRequirePlayer()    { return cfg.getBoolean("fake-chat.require-player-online", true); }
    public static double  fakeChatChance()           { return cfg.getDouble("fake-chat.chance", 0.75); }
    public static int     fakeChatIntervalMin()      { return cfg.getInt("fake-chat.interval.min", 5); }
    public static int     fakeChatIntervalMax()      { return cfg.getInt("fake-chat.interval.max", 10); }

    // ── Realism enhancements ──────────────────────────────────────────────────
    /** Simulate a typing pause (0–2.5 s) before each message fires. */
    public static boolean fakeChatTypingDelay()      { return cfg.getBoolean("fake-chat.typing-delay", true); }
    /** Chance (0–1) a bot sends a short follow-up message after the main one. */
    public static double  fakeChatBurstChance()      { return cfg.getDouble("fake-chat.burst-chance", 0.12); }
    /** Min seconds before a burst follow-up fires. */
    public static int     fakeChatBurstDelayMin()    { return cfg.getInt("fake-chat.burst-delay.min", 2); }
    /** Max seconds before a burst follow-up fires. */
    public static int     fakeChatBurstDelayMax()    { return cfg.getInt("fake-chat.burst-delay.max", 5); }
    /** When a real player mentions a bot's name, that bot may reply. */
    public static boolean fakeChatReplyToMentions()  { return cfg.getBoolean("fake-chat.reply-to-mentions", true); }
    /** Probability (0–1) a named bot actually replies to a mention (default 65 %). */
    public static double  fakeChatMentionReplyChance() { return cfg.getDouble("fake-chat.mention-reply-chance", 0.65); }
    /** Min seconds before a mention reply fires. */
    public static int     fakeChatReplyDelayMin()    { return cfg.getInt("fake-chat.reply-delay.min", 2); }
    /** Max seconds before a mention reply fires. */
    public static int     fakeChatReplyDelayMax()    { return cfg.getInt("fake-chat.reply-delay.max", 8); }
    /** Minimum gap (seconds) between any two bots chatting; 0 = disabled. */
    public static int     fakeChatStaggerInterval()  { return cfg.getInt("fake-chat.stagger-interval", 3); }
    /** Each bot gets a random chat-frequency multiplier. */
    public static boolean fakeChatActivityVariation(){ return cfg.getBoolean("fake-chat.activity-variation", true); }
    /** How many recent messages per bot to remember and avoid repeating. */
    public static int     fakeChatHistorySize()      { return cfg.getInt("fake-chat.history-size", 5); }


    /** Messages bots can randomly send - loaded from bot-messages.yml. */
    public static List<String> fakeChatMessages()   { return BotMessageConfig.getMessages(); }
    /** Reply messages used when a player mentions a bot's name. */
    public static List<String> chatReplyMessages()  { return BotMessageConfig.getReplyMessages(); }
    /** Short burst follow-up messages. */
    public static List<String> chatBurstMessages()  { return BotMessageConfig.getBurstMessages(); }
    /** Join-reaction messages (player joins the server). */
    public static List<String> chatJoinReactionMessages()  { return BotMessageConfig.getJoinReactionMessages(); }
    /** Death-reaction messages (someone dies). */
    public static List<String> chatDeathReactionMessages() { return BotMessageConfig.getDeathReactionMessages(); }
    /** Leave-reaction messages (player leaves the server). */
    public static List<String> chatLeaveReactionMessages() { return BotMessageConfig.getLeaveReactionMessages(); }
    /** Keyword-specific reaction messages for a given keyword-pool key. */
    public static List<String> chatKeywordReactionMessages(String key) { return BotMessageConfig.getKeywordReactionMessages(key); }

    // ── Remote chat format ────────────────────────────────────────────────────

    /**
     * MiniMessage format used when broadcasting a bodyless or remote bot's message.
     * Supports {@code {name}} (display name) and {@code {message}} (the resolved text).
     * Config path: {@code fake-chat.remote-format}.
     */
    public static String fakeChatRemoteFormat() {
        return cfg.getString("fake-chat.remote-format",
                "<yellow>{name}<dark_gray>: <white>{message}");
    }

    // ── Event-triggered chat ──────────────────────────────────────────────────

    /** Master switch for event-triggered chat reactions. */
    public static boolean fakeChatEventTriggersEnabled() {
        return cfg.getBoolean("fake-chat.event-triggers.enabled", true);
    }

    /** React when a real player joins the server. */
    public static boolean fakeChatOnJoinEnabled() {
        return cfg.getBoolean("fake-chat.event-triggers.on-player-join.enabled", true);
    }
    /** Probability (0–1) a bot reacts to a player join. */
    public static double  fakeChatOnJoinChance() {
        return cfg.getDouble("fake-chat.event-triggers.on-player-join.chance", 0.40);
    }
    /** Min seconds before a join-reaction fires. */
    public static int     fakeChatOnJoinDelayMin() {
        return cfg.getInt("fake-chat.event-triggers.on-player-join.delay.min", 2);
    }
    /** Max seconds before a join-reaction fires. */
    public static int     fakeChatOnJoinDelayMax() {
        return cfg.getInt("fake-chat.event-triggers.on-player-join.delay.max", 6);
    }

    /** React when a player or bot dies. */
    public static boolean fakeChatOnDeathEnabled() {
        return cfg.getBoolean("fake-chat.event-triggers.on-death.enabled", true);
    }
    /** Only react to player deaths, not mob/animal deaths. */
    public static boolean fakeChatOnDeathPlayersOnly() {
        return cfg.getBoolean("fake-chat.event-triggers.on-death.players-only", false);
    }
    /** Probability (0–1) a bot reacts to a death event. */
    public static double  fakeChatOnDeathChance() {
        return cfg.getDouble("fake-chat.event-triggers.on-death.chance", 0.30);
    }
    /** Min seconds before a death-reaction fires. */
    public static int     fakeChatOnDeathDelayMin() {
        return cfg.getInt("fake-chat.event-triggers.on-death.delay.min", 1);
    }
    /** Max seconds before a death-reaction fires. */
    public static int     fakeChatOnDeathDelayMax() {
        return cfg.getInt("fake-chat.event-triggers.on-death.delay.max", 4);
    }

    /** React when a real player leaves the server. */
    public static boolean fakeChatOnLeaveEnabled() {
        return cfg.getBoolean("fake-chat.event-triggers.on-player-leave.enabled", true);
    }
    /** Probability (0–1) a bot reacts to a player leaving. */
    public static double  fakeChatOnLeaveChance() {
        return cfg.getDouble("fake-chat.event-triggers.on-player-leave.chance", 0.30);
    }
    /** Min seconds before a leave-reaction fires. */
    public static int     fakeChatOnLeaveDelayMin() {
        return cfg.getInt("fake-chat.event-triggers.on-player-leave.delay.min", 1);
    }
    /** Max seconds before a leave-reaction fires. */
    public static int     fakeChatOnLeaveDelayMax() {
        return cfg.getInt("fake-chat.event-triggers.on-player-leave.delay.max", 4);
    }

    // ── Player chat reactions (fake-chat.event-triggers.on-player-chat.*) ──────

    /** Master switch — bots may spontaneously react when a real player sends a chat message. */
    public static boolean fakeChatOnPlayerChatEnabled() {
        return cfg.getBoolean("fake-chat.event-triggers.on-player-chat.enabled", false);
    }
    /** When true, use the AI provider to generate contextual replies instead of the static pool. */
    public static boolean fakeChatOnPlayerChatUseAi() {
        return cfg.getBoolean("fake-chat.event-triggers.on-player-chat.use-ai", true);
    }
    /** Per-bot cooldown (seconds) between AI-powered player-chat reactions. */
    public static int fakeChatOnPlayerChatAiCooldown() {
        return cfg.getInt("fake-chat.event-triggers.on-player-chat.ai-cooldown", 30);
    }
    /** Probability (0–1) a bot reacts to a player's chat message. */
    public static double fakeChatOnPlayerChatChance() {
        return cfg.getDouble("fake-chat.event-triggers.on-player-chat.chance", 0.25);
    }
    /** Maximum number of bots that can react to a single player message. */
    public static int fakeChatOnPlayerChatMaxBots() {
        return cfg.getInt("fake-chat.event-triggers.on-player-chat.max-bots", 1);
    }
    /** When true, skip messages shorter than 3 characters. */
    public static boolean fakeChatOnPlayerChatIgnoreShort() {
        return cfg.getBoolean("fake-chat.event-triggers.on-player-chat.ignore-short", true);
    }
    /** When true, skip messages that start with '/' (commands). */
    public static boolean fakeChatOnPlayerChatIgnoreCommands() {
        return cfg.getBoolean("fake-chat.event-triggers.on-player-chat.ignore-commands", true);
    }
    /** Probability (0–1) the reaction directly mentions the player's name. */
    public static double fakeChatOnPlayerChatMentionChance() {
        return cfg.getDouble("fake-chat.event-triggers.on-player-chat.mention-player", 0.50);
    }
    /** Min seconds before a player-chat reaction fires. */
    public static int fakeChatOnPlayerChatDelayMin() {
        return cfg.getInt("fake-chat.event-triggers.on-player-chat.delay.min", 2);
    }
    /** Max seconds before a player-chat reaction fires. */
    public static int fakeChatOnPlayerChatDelayMax() {
        return cfg.getInt("fake-chat.event-triggers.on-player-chat.delay.max", 8);
    }

    // ── Bot-to-bot conversations (fake-chat.bot-to-bot.*) ────────────────────

    /** Master switch — bots can react to each other's messages forming conversations. */
    public static boolean fakeChatBotToBotEnabled() {
        return cfg.getBoolean("fake-chat.bot-to-bot.enabled", true);
    }
    /** Probability (0–1) another bot replies to a chatting bot (depth-0 trigger). */
    public static double fakeChatBotToBotReplyChance() {
        return cfg.getDouble("fake-chat.bot-to-bot.reply-chance", 0.35);
    }
    /** Probability (0–1) the conversation continues after each reply (depth 1+). */
    public static double fakeChatBotToBotChainChance() {
        return cfg.getDouble("fake-chat.bot-to-bot.chain-chance", 0.40);
    }
    /** Maximum number of exchanges in a single bot-to-bot conversation. */
    public static int fakeChatBotToBotMaxChain() {
        return cfg.getInt("fake-chat.bot-to-bot.max-chain", 3);
    }
    /** Min seconds before a bot-to-bot reply fires. */
    public static int fakeChatBotToBotDelayMin() {
        return cfg.getInt("fake-chat.bot-to-bot.delay.min", 4);
    }
    /** Max seconds before a bot-to-bot reply fires. */
    public static int fakeChatBotToBotDelayMax() {
        return cfg.getInt("fake-chat.bot-to-bot.delay.max", 14);
    }
    /** Server-wide cooldown (seconds) between separate bot-to-bot conversation starts. */
    public static int fakeChatBotToBotCooldown() {
        return cfg.getInt("fake-chat.bot-to-bot.cooldown", 8);
    }

    // ── Event trigger: advancement ─────────────────────────────────────────────
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

    // ── Event trigger: first join ──────────────────────────────────────────────
    public static boolean fakeChatOnFirstJoinEnabled() {
        return cfg.getBoolean("fake-chat.event-triggers.on-first-join.enabled", true);
    }
    public static double fakeChatOnFirstJoinChance() {
        return cfg.getDouble("fake-chat.event-triggers.on-first-join.chance", 0.70);
    }

    // ── Event trigger: player-kills-player ────────────────────────────────────
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

    // ── Event trigger: high XP level ──────────────────────────────────────────
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

    // ── New message-pool shorthands ────────────────────────────────────────────
    /** Bot-to-bot reply messages used in bot conversations. */
    public static List<String> chatBotToBotReplyMessages()     { return BotMessageConfig.getBotToBotReplyMessages(); }
    /** Advancement-reaction messages. */
    public static List<String> chatAdvancementReactionMessages(){ return BotMessageConfig.getAdvancementReactionMessages(); }
    /** First-join welcome messages. */
    public static List<String> chatFirstJoinReactionMessages() { return BotMessageConfig.getFirstJoinReactionMessages(); }
    /** Player-kills-player reaction messages. */
    public static List<String> chatKillReactionMessages()      { return BotMessageConfig.getKillReactionMessages(); }
    /** High-level milestone reaction messages. */
    public static List<String> chatHighLevelReactionMessages() { return BotMessageConfig.getHighLevelReactionMessages(); }
    /** Player-chat reaction messages (bots react to any real player message). */
    public static List<String> chatPlayerChatReactionMessages() { return BotMessageConfig.getPlayerChatReactionMessages(); }

    // ── Keyword reactions ─────────────────────────────────────────────────────

    /** Master switch for keyword-triggered reactions. */
    public static boolean fakeChatKeywordReactionsEnabled() {
        return cfg.getBoolean("fake-chat.keyword-reactions.enabled", false);
    }

    /**
     * Map of keyword → pool-key for keyword-triggered reactions.
     * Config path: {@code fake-chat.keyword-reactions.keywords}.
     * Example: {@code pvp: "pvp-reactions"}, {@code trade: "trade-reactions"}.
     */
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

    // ── Database  (database.*) ────────────────────────────────────────────────

    /** Use MySQL instead of the built-in SQLite. */
    public static boolean mysqlEnabled()      { return cfg.getBoolean("database.mysql-enabled", false); }
    /** Master toggle to disable database I/O entirely. When false, persistence and DB stats are off. */
    public static boolean databaseEnabled()   { return cfg.getBoolean("database.enabled", true); }

    /**
     * Database mode: {@code "LOCAL"} (default, single-server) or {@code "NETWORK"}
     * (shared database across multiple servers, rows tagged by {@link #serverId()}).
     * Any value other than {@code "NETWORK"} (case-insensitive) is treated as {@code "LOCAL"}.
     */
    public static String databaseMode() {
        String raw = cfg.getString("database.mode", "LOCAL");
        return raw.trim().equalsIgnoreCase("NETWORK") ? "NETWORK" : "LOCAL";
    }

    /**
     * {@code true} when the database is enabled and {@link #databaseMode()} is {@code "NETWORK"}.
     * Use this to enable cross-server features such as per-server row filtering.
     */
    public static boolean isNetworkMode() {
        return databaseEnabled() && databaseMode().equalsIgnoreCase("NETWORK");
    }

    /**
     * Config sync mode: {@code "DISABLED"} (default), {@code "MANUAL"}, {@code "AUTO_PULL"}, or {@code "AUTO_PUSH"}.
     * Controls how config files are synchronized across network servers.
     * Valid values:
     * <ul>
     *   <li>{@code DISABLED} - No syncing (default for LOCAL mode)</li>
     *   <li>{@code MANUAL} - Only sync via /fpp sync commands</li>
     *   <li>{@code AUTO_PULL} - Pull latest on startup/reload</li>
     *   <li>{@code AUTO_PUSH} - Push changes automatically</li>
     * </ul>
     */
    public static String configSyncMode() {
        String raw = cfg.getString("config-sync.mode", "DISABLED");
        return raw.trim().toUpperCase();
    }

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

    // ── Server identity  (database.server-id) ────────────────────────────────

    /**
     * Unique server identifier stamped on database rows when
     * {@link #isNetworkMode()} is {@code true}.
     *
     * <p>Reads {@code database.server-id} (current path since config v31).
     * Falls back to the legacy {@code server.id} path for configs that have
     * not yet been migrated, so the value is never lost on the first reload
     * after an upgrade.  Blank / null values fall back to {@code "default"}.
     */
    public static String serverId() {
        // New path (v31+)
        String id = cfg.getString("database.server-id", null);
        // Legacy fallback (v29-v30 configs not yet migrated)
        if (id == null || id.isBlank()) {
            id = cfg.getString("server.id", "default");
        }
        return (id == null || id.isBlank()) ? "default" : id.trim();
    }

    // ── Utility ───────────────────────────────────────────────────────────────

    // ── Performance  (performance.*) ─────────────────────────────────────────

    /**
     * Maximum distance (in blocks) at which a moving bot sends position-sync packets
     * to a real player.  Players farther than this value will not receive per-tick
     * movement updates - they cannot render the bot anyway beyond their view distance.
     *
     * <p>Set to {@code 0} to disable distance culling (legacy behaviour - sends to all
     * online players regardless of distance).  The recommended value is {@code 128},
     * which covers the standard Minecraft player-entity tracking range.</p>
     *
     * Maps to {@code performance.position-sync-distance}.
     */
    public static double positionSyncDistance() {
        return cfg.getDouble("performance.position-sync-distance", 128.0);
    }


    // ── Badword Filter (badword-filter.*) ──────────────────────────────────────

    /**
     * Whether badword filtering is enabled for bot names.
     * Maps to {@code badword-filter.enabled}.
     */
    public static boolean isBadwordFilterEnabled() {
        return cfg.getBoolean("badword-filter.enabled", true);
    }

    /**
     * List of forbidden words that cannot appear in bot names (case-insensitive matching).
     * This list is additive on top of the remote global baseline when that source
     * is enabled.
     * Maps to {@code badword-filter.words}.
     */
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

    /**
     * Whether the remote global badword baseline is enabled.
     * When {@code true}, FPP fetches the configured remote profanity list and merges it
     * with user additions from config.yml and bad-words.yml.
     * Maps to {@code badword-filter.use-global-list}.
     */
    public static boolean isBadwordGlobalListEnabled() {
        return cfg.getBoolean("badword-filter.use-global-list", true);
    }

    /**
     * URL of the remote global badword list.
     * Maps to {@code badword-filter.global-list-url}.
     */
    public static String badwordGlobalListUrl() {
        return cfg.getString("badword-filter.global-list-url",
                "https://www.cs.cmu.edu/~biglou/resources/bad-words.txt");
    }

    /**
     * Timeout in milliseconds used when fetching the remote global badword list.
     * Maps to {@code badword-filter.global-list-timeout-ms}.
     */
    public static int badwordGlobalListTimeoutMs() {
        return Math.max(1000, cfg.getInt("badword-filter.global-list-timeout-ms", 5000));
    }

    /**
     * List of bot names that are explicitly allowed even if they contain a badword.
     * Maps to {@code badword-filter.whitelist}.
     */
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

    /**
     * Whether the badword auto-rename feature is enabled.
     * When {@code true} a bad name is sanitised by replacing it with a random clean
     * name from {@code bot-names.yml} and the sender is notified, instead of
     * hard-blocking the action.
     * Maps to {@code badword-filter.auto-rename}.
     */
    public static boolean isBadwordAutoRenameEnabled() {
        return cfg.getBoolean("badword-filter.auto-rename", true);
    }

    /**
     * Whether the auto-detection enhancement is enabled.
     * When {@code true}, additional normalization and regex passes are applied on top
     * of the base leet-speak substitution, catching more evasion attempts.
     * Maps to {@code badword-filter.auto-detection.enabled}.
     */
    public static boolean isBadwordAutoDetectionEnabled() {
        return cfg.getBoolean("badword-filter.auto-detection.enabled", true);
    }

    /**
     * The auto-detection mode: {@code "off"}, {@code "normal"}, or {@code "strict"}.
     * Strict mode enables the most aggressive regex checks, including alternating
     * single-character filler detection.
     * Maps to {@code badword-filter.auto-detection.mode}.
     */
    public static String getBadwordAutoDetectionMode() {
        return cfg.getString("badword-filter.auto-detection.mode", "normal").toLowerCase();
    }

    // ── Bot Interaction ───────────────────────────────────────────────────────

    /**
     * Whether right-click interaction with bots is enabled.
     * When {@code false}, all right-click events on bots are ignored.
     * Maps to {@code bot-interaction.right-click-enabled}.
     */
    public static boolean isBotRightClickEnabled() {
        return cfg.getBoolean("bot-interaction.right-click-enabled", true);
    }

    /**
     * Whether shift+right-click opens the bot settings GUI.
     * Maps to {@code bot-interaction.shift-right-click-settings}.
     */
    public static boolean isBotShiftRightClickSettingsEnabled() {
        return cfg.getBoolean("bot-interaction.shift-right-click-settings", true);
    }

    // ── AI Conversations  (ai-conversations.*) ────────────────────────────────

    /**
     * Master toggle for AI-powered bot conversations via /msg commands.
     * Maps to {@code ai-conversations.enabled}.
     */
    public static boolean aiConversationsEnabled() {
        return cfg.getBoolean("ai-conversations.enabled", true);
    }

    /**
     * Default personality prompt used for all bots unless overridden per-bot.
     * Supports {@code {bot_name}} placeholder.
     * Maps to {@code ai-conversations.default-personality}.
     */
    public static String aiConversationsDefaultPersonality() {
        return cfg.getString("ai-conversations.default-personality",
                "You are {bot_name}, a real Minecraft player chatting on a survival server. " +
                "STRICT RULES: Reply in 2-6 words max. Lowercase only, no full stops. " +
                "Make 1-2 typos per message. Never use full sentences. " +
                "Only talk about real vanilla Minecraft things. Match the energy: 'hi' gets 'hey' not a paragraph.");
    }

    /**
     * Whether the bot simulates a typing delay before replying.
     * Maps to {@code ai-conversations.typing-delay.enabled}.
     */
    public static boolean aiTypingDelayEnabled() {
        return cfg.getBoolean("ai-conversations.typing-delay.enabled", true);
    }

    /**
     * Base delay in seconds before any reply (before per-char is added).
     * Maps to {@code ai-conversations.typing-delay.base}.
     */
    public static double aiTypingDelayBase() {
        return cfg.getDouble("ai-conversations.typing-delay.base", 1.0);
    }

    /**
     * Extra delay in seconds per character in the response.
     * Maps to {@code ai-conversations.typing-delay.per-char}.
     */
    public static double aiTypingDelayPerChar() {
        return cfg.getDouble("ai-conversations.typing-delay.per-char", 0.07);
    }

    /**
     * Maximum total typing delay in seconds (cap).
     * Maps to {@code ai-conversations.typing-delay.max}.
     */
    public static double aiTypingDelayMax() {
        return cfg.getDouble("ai-conversations.typing-delay.max", 5.0);
    }

    /**
     * Maximum conversation history (message pairs) to remember per bot-player pair.
     * Maps to {@code ai-conversations.max-history}.
     */
    public static int aiConversationsMaxHistory() {
        return cfg.getInt("ai-conversations.max-history", 10);
    }

    /**
     * Cooldown in seconds before a bot can respond again to the same player.
     * Maps to {@code ai-conversations.cooldown}.
     */
    public static int aiConversationsCooldown() {
        return cfg.getInt("ai-conversations.cooldown", 3);
    }

    /**
     * Whether to log all AI requests and responses to console.
     * Maps to {@code ai-conversations.debug}.
     */
    public static boolean aiConversationsDebug() {
        return cfg.getBoolean("ai-conversations.debug", false) || isDebug();
    }

    // ── Debug helpers ─────────────────────────────────────────────────────────

    /** Log a message to console only when the legacy global debug mode is on. */
    public static void debug(String message) { FppLogger.debug(message); }

    public static void debugStartup(String message)    { FppLogger.debug("STARTUP", debugStartup(), message); }
    public static void debugNms(String message)        { FppLogger.debug("NMS", debugNms(), message); }
    public static void debugPackets(String message)    { FppLogger.debug("PACKETS", debugPackets(), message); }
    public static void debugLuckPerms(String message)  { FppLogger.debug("LP", debugLuckPerms(), message); }
    public static void debugNetwork(String message)    { FppLogger.debug("NETWORK", debugNetwork(), message); }
    public static void debugConfigSync(String message) { FppLogger.debug("CONFIG_SYNC", debugConfigSync(), message); }
    public static void debugSkin(String message)       { FppLogger.debug("SKIN", debugSkin(), message); }
    public static void debugDatabase(String message)   { FppLogger.debug("DATABASE", debugDatabase(), message); }
    public static void debugChat(String message)       { FppLogger.debug("CHAT", debugChat(), message); }
    public static void debugSwap(String message)      { FppLogger.debug("SWAP", debugSwap(), message); }
}

