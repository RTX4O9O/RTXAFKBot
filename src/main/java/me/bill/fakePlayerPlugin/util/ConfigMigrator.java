package me.bill.fakePlayerPlugin.util;

import me.bill.fakePlayerPlugin.FakePlayerPlugin;
import org.bukkit.configuration.file.YamlConfiguration;

import java.io.*;
import java.util.Arrays;

/**
 * Handles automatic, non-destructive upgrades of {@code config.yml} from any
 * previous plugin version to the current one.
 *
 * <h3>How it works</h3>
 * <ol>
 *   <li>On every startup, {@link #migrateIfNeeded(FakePlayerPlugin)} is called
 *       <em>before</em> {@code Config.init()} so migrations run on the raw YAML.</li>
 *   <li>The key {@code config-version} in {@code config.yml} records which
 *       format version was last written. If it is missing the config is treated
 *       as version&nbsp;0 (very old / first run without this system).</li>
 *   <li>Each numbered migration step is applied in sequence - keys are added,
 *       renamed, or restructured as needed while existing user values are
 *       preserved.</li>
 *   <li>A full backup is created via {@link BackupManager} before any changes
 *       are written to disk.</li>
 *   <li>After all steps run the file is saved and {@code config-version} is
 *       set to {@link #CURRENT_VERSION}.</li>
 *   <li>Bukkit's own {@code copyDefaults} mechanism fills in any remaining
 *       missing keys when {@code Config.init()} runs immediately afterwards.</li>
 * </ol>
 *
 * <h3>Adding a new migration</h3>
 * <ol>
 *   <li>Increment {@link #CURRENT_VERSION}.</li>
 *   <li>Add a new {@code migrateVnToVm()} private method.</li>
 *   <li>Add a call to it in {@link #migrateIfNeeded} inside the version chain.</li>
 *   <li>Update {@code config.yml} in resources with the new keys / comments.</li>
 * </ol>
 */
public final class ConfigMigrator {

    /**
     * The config-version value written by this build.
     * <b>Increment this whenever config.yml structure changes.</b>
     */
    public static final int CURRENT_VERSION = 55;

    /**
     * Mirrors the {@code debug} flag read directly from the raw YAML during migration.
     * Used by {@link #log} so it never touches {@code Config.cfg} (which is null
     * during migration - {@code Config.init()} runs after {@code migrateIfNeeded}).
     */
    private static boolean rawDebug = false;

    private ConfigMigrator() {}

    // ── Public API ────────────────────────────────────────────────────────────

    /**
     * Runs the full migration chain if the stored {@code config-version} is
     * below {@link #CURRENT_VERSION}.
     *
     * @param plugin Plugin instance (used to locate the data folder).
     * @return {@code true} if at least one migration step was applied and the
     *         file was saved; {@code false} if the config was already current.
     */
    public static boolean migrateIfNeeded(FakePlayerPlugin plugin) {
        File configFile = new File(plugin.getDataFolder(), "config.yml");

        // First run - Bukkit hasn't saved defaults yet; nothing to migrate.
        if (!configFile.exists()) {
            return false;
        }

        YamlConfiguration cfg = YamlConfiguration.loadConfiguration(configFile);

        // Read debug flag directly from raw YAML - Config.cfg is still null at this point.
        rawDebug = cfg.getBoolean("debug", false)
                || cfg.getBoolean("logging.debug.startup", false);

        int stored = cfg.getInt("config-version", 0);

        if (stored >= CURRENT_VERSION) {
            log("config is current (v" + stored + "). No migration needed.");
            return false;
        }

        // ── Pre-migration backup ───────────────────────────────────────────────
        String fromLabel = stored == 0 ? "legacy" : "v" + stored;
        if (rawDebug) {
            FppLogger.section("Config Migration");
            FppLogger.info("Upgrading config from " + fromLabel + " → v" + CURRENT_VERSION + "…");
            FppLogger.info("A backup will be created before any changes are written.");
        }
        BackupManager.createFullBackup(plugin, "pre-migration-" + fromLabel, rawDebug);

        // ── Apply migrations in order ──────────────────────────────────────────
        boolean anyChange = false;
        if (stored < 2)  anyChange |= v1to2(cfg);
        if (stored < 3)  anyChange |= v2to3(cfg);
        if (stored < 4)  anyChange |= v3to4(cfg);
        if (stored < 5)  anyChange |= v4to5(cfg);
        if (stored < 6)  anyChange |= v5to6(cfg);
        if (stored < 7)  anyChange |= v6to7(cfg);
        if (stored < 8)  anyChange |= v7to8(cfg);
        if (stored < 9)  anyChange |= v8to9(cfg);
        if (stored < 10) anyChange |= v9to10(cfg);
        if (stored < 11) anyChange |= v10to11(cfg);
        if (stored < 12) anyChange |= v11to12(cfg);
        if (stored < 13) anyChange |= v12to13(cfg);
        if (stored < 14) anyChange |= v13to14(cfg);
        if (stored < 15) anyChange |= v14to15(cfg);
        if (stored < 16) anyChange |= v15to16(cfg);
        if (stored < 17) anyChange |= v16to17(cfg);
        if (stored < 18) anyChange |= v17to18(cfg);
        if (stored < 19) anyChange |= v18to19(cfg);
        if (stored < 20) anyChange |= v19to20(cfg);
        if (stored < 22) anyChange |= v21to22(cfg);
        if (stored < 23) anyChange |= v22to23(cfg);
        if (stored < 24) anyChange |= v23to24(cfg);
        if (stored < 25) anyChange |= v24to25(cfg);
        if (stored < 26) anyChange |= v25to26(cfg);
        if (stored < 27) anyChange |= v26to27(cfg);
        if (stored < 28) anyChange |= v27to28(cfg);
        if (stored < 30) anyChange |= v29to30(cfg);
        if (stored < 31) anyChange |= v30to31(cfg);
        if (stored < 32) anyChange |= v31to32(cfg);
        if (stored < 33) anyChange |= v32to33(cfg);
        if (stored < 34) anyChange |= v33to34(cfg);
        if (stored < 35) anyChange |= v34to35(cfg);
        if (stored < 36) anyChange |= v35to36(cfg);
        if (stored < 37) anyChange |= v36to37(cfg);
        if (stored < 38) anyChange |= v37to38(cfg);
        if (stored < 39) anyChange |= v38to39(cfg);
        if (stored < 40) anyChange |= v39to40(cfg);
        if (stored < 41) anyChange |= v40to41(cfg);
        if (stored < 42) anyChange |= v41to42(cfg);
        if (stored < 43) anyChange |= v42to43(cfg);
        if (stored < 44) anyChange |= v43to44(cfg);
        if (stored < 46) anyChange |= v45to46(cfg);
        if (stored < 47) anyChange |= v46to47(cfg);
        if (stored < 48) anyChange |= v47to48(cfg);
        if (stored < 49) anyChange |= v48to49(cfg);
        if (stored < 50) anyChange |= v49to50(cfg);
        if (stored < 51) anyChange |= v50to51(cfg);
        if (stored < 52) anyChange |= v51to52(cfg);
        if (stored < 53) anyChange |= v52to53(cfg);
        if (stored < 54) anyChange |= v53to54(cfg);
        if (stored < 55) anyChange |= v54to55(cfg);

        // ── Fill any remaining missing keys from jar defaults ──────────────────
        fillDefaults(plugin, cfg);

        // ── Stamp the version and save ─────────────────────────────────────────
        cfg.set("config-version", CURRENT_VERSION);

        try {
            cfg.save(configFile);
            if (anyChange) {
                if (rawDebug) {
                    FppLogger.success("Config migrated to v" + CURRENT_VERSION + " successfully.");
                } else {
                    FppLogger.info("Config migration applied (v" + fromLabel + " → v" + CURRENT_VERSION + ").");
                }
            } else {
                if (rawDebug) {
                    FppLogger.success("Config stamped as v" + CURRENT_VERSION
                            + " (no structural changes needed; defaults filled).");
                }
            }
        } catch (IOException e) {
            FppLogger.error("ConfigMigrator: failed to save migrated config: " + e.getMessage());
            return false;
        }

        return true;
    }

    /**
     * Forces a re-run of the migration chain from version 0, regardless of the
     * stored version. Also fills any missing keys from the jar defaults.
     * Useful as part of {@code /fpp migrate config}.
     */
    public static void forceMigrate(FakePlayerPlugin plugin) {
        File configFile = new File(plugin.getDataFolder(), "config.yml");
        if (!configFile.exists()) return;

        YamlConfiguration cfg = YamlConfiguration.loadConfiguration(configFile);
        cfg.set("config-version", 0);   // reset so the chain runs fully
        try { cfg.save(configFile); } catch (IOException ignored) {}

        migrateIfNeeded(plugin);
    }

    public static int getCurrentVersion() { return CURRENT_VERSION; }

    // ── Migration steps ────────────────────────────────────────────────────────
    // Each method returns true if it changed anything.

    /** v1 → v2: Added update-checker section. */
    private static boolean v1to2(YamlConfiguration cfg) {
        if (cfg.contains("update-checker")) return false;
        cfg.set("update-checker.enabled", true);
        log("v1→v2", "added update-checker section");
        return true;
    }

    /** v2 → v3: Added luckperms section. */
    private static boolean v2to3(YamlConfiguration cfg) {
        if (cfg.contains("luckperms")) return false;
        cfg.set("luckperms.use-prefix", true);
        log("v2→v3", "added luckperms section");
        return true;
    }

    /**
     * v3 → v4: Skin system overhaul.
     * <ul>
     *   <li>{@code skin-enabled} (boolean) → {@code skin.mode} (string)</li>
     *   <li>{@code skin.enabled} (boolean) → {@code skin.mode} (string)</li>
     *   <li>{@code skin.custom.pool}    → {@code skin.pool}</li>
     *   <li>{@code skin.custom.by-name} → {@code skin.overrides}</li>
     * </ul>
     */
    private static boolean v3to4(YamlConfiguration cfg) {
        boolean changed = false;

        // Top-level boolean flag (very old format)
        if (cfg.contains("skin-enabled")) {
            boolean was = cfg.getBoolean("skin-enabled", true);
            cfg.set("skin.mode", was ? "auto" : "off");
            cfg.set("skin-enabled", null);
            log("v3→v4", "skin-enabled → skin.mode=" + (was ? "auto" : "off"));
            changed = true;
        }

        // skin.enabled (intermediate format)
        if (cfg.contains("skin.enabled")) {
            boolean was = cfg.getBoolean("skin.enabled", true);
            if (!cfg.contains("skin.mode")) cfg.set("skin.mode", was ? "auto" : "off");
            cfg.set("skin.enabled", null);
            log("v3→v4", "skin.enabled → skin.mode");
            changed = true;
        }

        // Ensure skin.mode always exists
        if (!cfg.contains("skin.mode")) {
            cfg.set("skin.mode", "auto");
            changed = true;
        }

        // skin.custom.pool → skin.pool
        if (cfg.contains("skin.custom.pool") && !cfg.contains("skin.pool")) {
            cfg.set("skin.pool", cfg.get("skin.custom.pool"));
            log("v3→v4", "skin.custom.pool → skin.pool");
            changed = true;
        }

        // skin.custom.by-name → skin.overrides
        if (cfg.contains("skin.custom.by-name") && !cfg.contains("skin.overrides")) {
            cfg.set("skin.overrides", cfg.get("skin.custom.by-name"));
            log("v3→v4", "skin.custom.by-name → skin.overrides");
            changed = true;
        }

        // Remove deprecated nested section if now empty
        if (!cfg.contains("skin.custom.pool") && !cfg.contains("skin.custom.by-name")) {
            cfg.set("skin.custom", null);
        }

        return changed;
    }

    /** v4 → v5: Added body section (replaces top-level {@code spawn-body}). */
    private static boolean v4to5(YamlConfiguration cfg) {
        if (cfg.contains("body")) return false;
        boolean prev = cfg.getBoolean("spawn-body", true);
        cfg.set("body.enabled", prev);
        cfg.set("spawn-body", null);
        log("v4→v5", "spawn-body → body.enabled");
        return true;
    }

    /** v5 → v6: Added chunk-loading section. */
    private static boolean v5to6(YamlConfiguration cfg) {
        if (cfg.contains("chunk-loading")) return false;
        cfg.set("chunk-loading.enabled", true);
        cfg.set("chunk-loading.radius", 6);
        cfg.set("chunk-loading.update-interval", 20);
        log("v5→v6", "added chunk-loading section");
        return true;
    }

    /** v6 → v7: Added head-ai section. */
    private static boolean v6to7(YamlConfiguration cfg) {
        if (cfg.contains("head-ai")) return false;
        cfg.set("head-ai.look-range", 8.0);
        cfg.set("head-ai.turn-speed", 0.3);
        log("v6→v7", "added head-ai section");
        return true;
    }

    /** v7 → v8: Added collision / push section. */
    private static boolean v7to8(YamlConfiguration cfg) {
        if (cfg.contains("collision")) return false;
        cfg.set("collision.walk-radius", 0.85);
        cfg.set("collision.walk-strength", 0.22);
        cfg.set("collision.max-horizontal-speed", 0.30);
        cfg.set("collision.hit-strength", 0.45);
        cfg.set("collision.bot-radius", 0.90);
        cfg.set("collision.bot-strength", 0.14);
        log("v7→v8", "added collision section");
        return true;
    }

    /** v8 → v9: Added swap and fake-chat sections. */
    private static boolean v8to9(YamlConfiguration cfg) {
        boolean changed = false;

        if (!cfg.contains("swap")) {
            cfg.set("swap.enabled",           false);
            cfg.set("swap.session-min",        120);
            cfg.set("swap.session-max",        600);
            cfg.set("swap.rejoin-delay-min",   5);
            cfg.set("swap.rejoin-delay-max",   45);
            cfg.set("swap.jitter",             30);
            cfg.set("swap.reconnect-chance",   0.15);
            cfg.set("swap.afk-kick-chance",    5);
            cfg.set("swap.farewell-chat",      true);
            cfg.set("swap.greeting-chat",      true);
            cfg.set("swap.time-of-day-bias",   true);
            log("v8→v9", "added swap section");
            changed = true;
        }

        if (!cfg.contains("fake-chat")) {
            cfg.set("fake-chat.enabled",                 false);
            cfg.set("fake-chat.require-player-online",   true);
            cfg.set("fake-chat.chance",                  0.75);
            cfg.set("fake-chat.interval.min",            5);
            cfg.set("fake-chat.interval.max",            10);
            log("v8→v9", "added fake-chat section");
            changed = true;
        }

        return changed;
    }

    /**
     * v9 → v10: Comprehensive restructure.
     * <ul>
     *   <li>{@code max-bots}           → {@code limits.max-bots}</li>
     *   <li>{@code persist-on-restart} → {@code persistence.enabled}</li>
     *   <li>Added {@code limits} section with user-bot-limit + spawn-presets</li>
     *   <li>Added {@code bot-name} section</li>
     *   <li>Added {@code death} section</li>
     *   <li>Added {@code database} section</li>
     *   <li>Normalised join-delay / leave-delay flat keys → nested sections</li>
     * </ul>
     */
    private static boolean v9to10(YamlConfiguration cfg) {
        boolean changed = false;

        // limits section
        if (!cfg.contains("limits")) {
            int prev = cfg.getInt("max-bots", 1000);
            cfg.set("limits.max-bots",       prev);
            cfg.set("limits.user-bot-limit", 1);
            cfg.set("limits.spawn-presets",  Arrays.asList(1, 5, 10, 15, 20));
            cfg.set("max-bots", null);
            log("v9→v10", "max-bots → limits section");
            changed = true;
        } else if (cfg.contains("max-bots")) {
            // Merge leftover key
            if (!cfg.contains("limits.max-bots")) cfg.set("limits.max-bots", cfg.getInt("max-bots", 1000));
            cfg.set("max-bots", null);
            changed = true;
        }

        // bot-name section
        if (!cfg.contains("bot-name")) {
            cfg.set("bot-name.admin-format", "{bot_name}");
            cfg.set("bot-name.user-format",  "bot-{spawner}-{num}");
            log("v9→v10", "added bot-name section");
            changed = true;
        }

        // persistence section
        if (!cfg.contains("persistence")) {
            boolean prev = cfg.getBoolean("persist-on-restart", true);
            cfg.set("persistence.enabled", prev);
            cfg.set("persist-on-restart", null);
            log("v9→v10", "persist-on-restart → persistence.enabled");
            changed = true;
        }

        // death section
        if (!cfg.contains("death")) {
            cfg.set("death.respawn-on-death", false);
            cfg.set("death.respawn-delay",    60);
            cfg.set("death.suppress-drops",   true);
            log("v9→v10", "added death section");
            changed = true;
        }

        // join-delay flat keys → nested section
        if (cfg.contains("join-delay-min") && !cfg.contains("join-delay.min")) {
            cfg.set("join-delay.min", cfg.getInt("join-delay-min", 0));
            cfg.set("join-delay.max", cfg.getInt("join-delay-max", 40));
            cfg.set("join-delay-min", null);
            cfg.set("join-delay-max", null);
            log("v9→v10", "join-delay-min/max → join-delay section");
            changed = true;
        }
        if (cfg.contains("leave-delay-min") && !cfg.contains("leave-delay.min")) {
            cfg.set("leave-delay.min", cfg.getInt("leave-delay-min", 0));
            cfg.set("leave-delay.max", cfg.getInt("leave-delay-max", 40));
            cfg.set("leave-delay-min", null);
            cfg.set("leave-delay-max", null);
            log("v9→v10", "leave-delay-min/max → leave-delay section");
            changed = true;
        }

        // database section
        if (!cfg.contains("database")) {
            cfg.set("database.mysql-enabled",                   false);
            cfg.set("database.mysql.host",                      "localhost");
            cfg.set("database.mysql.port",                      3306);
            cfg.set("database.mysql.database",                  "fpp");
            cfg.set("database.mysql.username",                  "root");
            cfg.set("database.mysql.password",                  "");
            cfg.set("database.mysql.use-ssl",                   false);
            cfg.set("database.mysql.pool-size",                 5);
            cfg.set("database.mysql.connection-timeout",        30000);
            cfg.set("database.location-flush-interval",         30);
            cfg.set("database.session-history.max-rows",        20);
            log("v9→v10", "added database section");
            changed = true;
        }

        return changed;
    }

    /**
     * v10 → v11: Enhanced skin system - guaranteed skin + fallback name.
     * <ul>
     *   <li>Added {@code skin.guaranteed-skin} - always apply a skin (default: true)</li>
     *   <li>Added {@code skin.fallback-name}   - last-resort Mojang name (default: Notch)</li>
     * </ul>
     */
    private static boolean v10to11(YamlConfiguration cfg) {
        boolean changed = false;
        if (!cfg.contains("skin.guaranteed-skin")) {
            cfg.set("skin.guaranteed-skin", true);
            log("v10→v11", "added skin.guaranteed-skin = true");
            changed = true;
        }
        if (!cfg.contains("skin.fallback-name")) {
            cfg.set("skin.fallback-name", "Notch");
            log("v10→v11", "added skin.fallback-name = Notch");
            changed = true;
        }
        return changed;
    }

    /**
     * v11 → v12: Config simplification pass.
     * <ul>
     *   <li>Added {@code head-ai.enabled} - explicit on/off toggle for head AI (default: true)</li>
     * </ul>
     */
    private static boolean v11to12(YamlConfiguration cfg) {
        if (cfg.contains("head-ai.enabled")) return false;
        cfg.set("head-ai.enabled", true);
        log("v11→v12", "added head-ai.enabled = true");
        return true;
    }

    /**
     * v12 → v13: Added metrics opt-out toggle.
     */
    private static boolean v12to13(YamlConfiguration cfg) {
        if (cfg.contains("metrics.enabled")) return false;
        cfg.set("metrics.enabled", true);
        log("v12→v13", "added metrics.enabled = true");
        return true;
    }

    /**
     * v13 → v14: Added spawn-cooldown and tab-list header/footer.
     * <ul>
     *   <li>Added {@code spawn-cooldown} - per-user spawn delay in seconds (default: 0)</li>
     *   <li>Added {@code tab-list.enabled} - tab-list header/footer toggle (default: false)</li>
     *   <li>Added {@code tab-list.update-interval}, {@code tab-list.header}, {@code tab-list.footer}</li>
     * </ul>
     */
    private static boolean v13to14(YamlConfiguration cfg) {
        boolean changed = false;
        if (!cfg.contains("spawn-cooldown")) {
            cfg.set("spawn-cooldown", 0);
            log("v13→v14", "added spawn-cooldown = 0");
            changed = true;
        }
        if (!cfg.contains("tab-list.enabled")) {
            cfg.set("tab-list.enabled", false);
            cfg.set("tab-list.update-interval", 40);
            cfg.set("tab-list.header", "");
            cfg.set("tab-list.footer", "");
            log("v13→v14", "added tab-list section");
            changed = true;
        }
        return changed;
    }

    /** v14 -> v15: Add database.enabled master toggle and messages.notify-admins-on-join default */
    private static boolean v14to15(YamlConfiguration cfg) {
        boolean changed = false;
        if (!cfg.contains("database.enabled")) {
            cfg.set("database.enabled", true);
            log("v14→v15", "added database.enabled = true");
            changed = true;
        }
        if (!cfg.contains("messages.notify-admins-on-join")) {
            cfg.set("messages.notify-admins-on-join", true);
            log("v14→v15", "added messages.notify-admins-on-join = true");
            changed = true;
        }
        return changed;
    }

    /** v15 -> v16: Add luckperms.weight-ordering-enabled default */
    private static boolean v15to16(YamlConfiguration cfg) {
        boolean changed = false;
        if (!cfg.contains("luckperms.weight-ordering-enabled")) {
            cfg.set("luckperms.weight-ordering-enabled", true);
            log("v15→v16", "added luckperms.weight-ordering-enabled = true");
            changed = true;
        }
        return changed;
    }

    /** v16 -> v17: Add luckperms.bot-group and luckperms.packet-prefix-char defaults */
    private static boolean v16to17(YamlConfiguration cfg) {
        boolean changed = false;
        if (!cfg.contains("luckperms.bot-group")) {
            cfg.set("luckperms.bot-group", "");
            log("v16→v17", "added luckperms.bot-group = ''");
            changed = true;
        }
        if (!cfg.contains("luckperms.packet-prefix-char")) {
            cfg.set("luckperms.packet-prefix-char", "{");
            log("v16→v17", "added luckperms.packet-prefix-char = '{'");
            changed = true;
        }
        return changed;
    }

    /**
     * v17 → v18: Added skin folder integration controls.
     * <ul>
     *   <li>{@code skin.use-skin-folder}      - scan plugins/FakePlayerPlugin/skins/ for PNGs</li>
     *   <li>{@code skin.clear-cache-on-reload} - clear resolved skin cache on /fpp reload</li>
     * </ul>
     */
    private static boolean v17to18(YamlConfiguration cfg) {
        boolean changed = false;
        if (!cfg.contains("skin.use-skin-folder")) {
            cfg.set("skin.use-skin-folder", true);
            log("v17→v18", "added skin.use-skin-folder = true");
            changed = true;
        }
        if (!cfg.contains("skin.clear-cache-on-reload")) {
            cfg.set("skin.clear-cache-on-reload", true);
            log("v17→v18", "added skin.clear-cache-on-reload = true");
            changed = true;
        }
        return changed;
    }

    /**
     * v18 → v19: Internal restructure pass - no config-key changes in this bump.
     * {@link #fillDefaults(FakePlayerPlugin, YamlConfiguration)} fills any missing entries.
     */
    private static boolean v18to19(YamlConfiguration cfg) {
        // No structural config changes - version bump was driven by code/plugin.yml changes.
        return false;
    }

    // ── Helpers ────────────────────────────────────────────────────────────────
    private static void fillDefaults(FakePlayerPlugin plugin, YamlConfiguration cfg) {
        try (InputStream stream = plugin.getResource("config.yml")) {
            if (stream == null) return;
            YamlConfiguration defaults = YamlConfiguration.loadConfiguration(
                    new InputStreamReader(stream));
            cfg.setDefaults(defaults);
            cfg.options().copyDefaults(true);
        } catch (IOException e) {
            // Safe: does not use Config.cfg
            FppLogger.warn("ConfigMigrator.fillDefaults: " + e.getMessage());
        }
    }

    /** v19 -> v20: Add luckperms.weight-offset default */
    private static boolean v19to20(YamlConfiguration cfg) {
        boolean any = false;
        if (!cfg.contains("luckperms.weight-offset")) {
            cfg.set("luckperms.weight-offset", -10);
            log("v19→v20", "added luckperms.weight-offset = -10");
            any = true;
        }
        return any;
    }

    /**
     * v21 → v22: Add {@code tab-list.show-bots} (now superseded by v22→v23).
     */
    private static boolean v21to22(YamlConfiguration cfg) {
        boolean any = false;
        if (!cfg.contains("tab-list.show-bots")) {
            cfg.set("tab-list.show-bots", true);
            log("v21→v22", "added tab-list.show-bots = true");
            any = true;
        }
        return any;
    }

    /**
     * v22 → v23: Simplify tab-list config.
     * <ul>
     *   <li>Header/footer/update-interval feature removed - drop those keys.</li>
     *   <li>{@code tab-list.enabled} now controls bot tab-list visibility.
     *       The old value (which toggled header/footer, default {@code false}) is
     *       <b>ignored</b> - we always write the correct new value.</li>
     *   <li>If {@code show-bots: false} was explicitly set, honour it by setting
     *       {@code enabled: false} so bots stay hidden after migration.</li>
     * </ul>
     */
    private static boolean v22to23(YamlConfiguration cfg) {
        boolean any = false;

        // The old tab-list.enabled meant "show header/footer" (default: false).
        // The new meaning is "show bots in tab list" (default: true).
        // ALWAYS overwrite with the correct new value - the old value is meaningless here.
        boolean wantHidden = cfg.contains("tab-list.show-bots")
                && !cfg.getBoolean("tab-list.show-bots", true);
        cfg.set("tab-list.enabled", !wantHidden);
        log("v22→v23", "set tab-list.enabled = " + !wantHidden
                + (wantHidden ? " (show-bots was false)" : " (new default)"));
        any = true;

        // Remove deprecated keys
        for (String dead : new String[]{"tab-list.show-bots", "tab-list.header",
                                        "tab-list.footer", "tab-list.update-interval"}) {
            if (cfg.contains(dead)) {
                cfg.set(dead, null);
                log("v22→v23", "removed deprecated key: " + dead);
            }
        }
        return any;
    }

    /**
     * v23 → v24: Corrective pass - ensures {@code tab-list.enabled} is {@code true}
     * for any installation where the v22→v23 migration left the old {@code false}
     * (header/footer toggle) value in place.  Only changes the value when it is
     * currently {@code false} AND the deprecated {@code show-bots} key no longer
     * exists (meaning the user never explicitly hid bots).
     */
    private static boolean v23to24(YamlConfiguration cfg) {
        boolean any = false;
        // If enabled is still false but show-bots is gone (meaning the old header/footer
        // default was never intentionally "hide bots"), correct it to true.
        if (!cfg.getBoolean("tab-list.enabled", true)
                && !cfg.contains("tab-list.show-bots")) {
            cfg.set("tab-list.enabled", true);
            log("v23→v24", "corrected tab-list.enabled false→true (old header/footer default)");
            any = true;
        }
        return any;
    }

    /**
     * v24 → v25: Housekeeping stamp for v1.4.23.
     * <ul>
     *   <li>No structural changes - version bump only.</li>
     *   <li>Ensures existing installations are stamped at the current schema version
     *       so future migrations have a clean baseline.</li>
     * </ul>
     */
    private static boolean v24to25(YamlConfiguration cfg) {
        // No structural changes in this release - stamp only.
        log("v24→v25", "housekeeping stamp for v1.4.23 (no structural changes)");
        return false;
    }

    /**
     * v25 → v26: Housekeeping stamp for v1.4.24.
     * <ul>
     *   <li>No structural changes - version bump only.</li>
     * </ul>
     */
    private static boolean v25to26(YamlConfiguration cfg) {
        // No structural changes in this release - stamp only.
        log("v25→v26", "housekeeping stamp for v1.4.24 (no structural changes)");
        return false;
    }

    /**
     * v26 → v27: Customizable tab-list / nametag display format.
     * <ul>
     *   <li>Adds {@code bot-name.tab-list-format} with default {@code "{prefix}{bot_name}{suffix}"}.</li>
     * </ul>
     */
    private static boolean v26to27(YamlConfiguration cfg) {
        if (cfg.contains("bot-name.tab-list-format")) return false;
        cfg.set("bot-name.tab-list-format", "{prefix}{bot_name}{suffix}");
        log("v26→v27", "added bot-name.tab-list-format");
        return true;
    }

    /**
     * v27 → v28: Body pushable / damageable toggles.
     * <ul>
     *   <li>Adds {@code body.pushable} (default {@code true}) - controls whether
     *       players can push bot bodies and whether knockback applies on hit.</li>
     *   <li>Adds {@code body.damageable} (default {@code true}) - controls whether
     *       bot bodies can receive damage.</li>
     * </ul>
     */
    private static boolean v27to28(YamlConfiguration cfg) {
        boolean changed = false;
        if (!cfg.contains("body.pushable")) {
            cfg.set("body.pushable", true);
            log("v27→v28", "added body.pushable = true");
            changed = true;
        }
        if (!cfg.contains("body.damageable")) {
            cfg.set("body.damageable", true);
            log("v27→v28", "added body.damageable = true");
            changed = true;
        }
        return changed;
    }

    /**
     * v29 → v30: Database mode and server identity.
     * <ul>
     *   <li>Adds {@code database.mode} (default {@code "LOCAL"}).</li>
     *   <li>Adds {@code server.id} (default {@code "default"}) - superseded in v31.</li>
     * </ul>
     */
    private static boolean v29to30(YamlConfiguration cfg) {
        boolean changed = false;
        if (!cfg.contains("database.mode")) {
            cfg.set("database.mode", "LOCAL");
            log("v29→v30", "added database.mode = LOCAL");
            changed = true;
        }
        if (!cfg.contains("server.id")) {
            cfg.set("server.id", "default");
            log("v29→v30", "added server.id = default");
            changed = true;
        }
        return changed;
    }

    /**
     * v30 → v31: Consolidate server identity into the database section.
     * <ul>
     *   <li>Moves {@code server.id} → {@code database.server-id}, preserving the
     *       user's existing value (e.g. "survival", "skyblock").</li>
     *   <li>Removes the now-unused {@code server} top-level section entirely.</li>
     * </ul>
     */
    private static boolean v30to31(YamlConfiguration cfg) {
        boolean changed = false;

        // Only migrate if the old key is present and the new one is not yet set
        if (cfg.contains("server.id") && !cfg.contains("database.server-id")) {
            String oldId = cfg.getString("server.id", "default");
            cfg.set("database.server-id", oldId);
            log("v30→v31", "moved server.id → database.server-id = " + oldId);
            changed = true;
        } else if (!cfg.contains("database.server-id")) {
            cfg.set("database.server-id", "default");
            log("v30→v31", "added database.server-id = default");
            changed = true;
        }

        // Remove the old server: section entirely
        if (cfg.contains("server")) {
            cfg.set("server", null);
            log("v30→v31", "removed unused server: section");
            changed = true;
        }

        return changed;
    }

    private static boolean v31to32(YamlConfiguration cfg) {
        boolean changed = false;

        // Migrate old LuckPerms system to new simplified approach
        if (cfg.contains("luckperms.use-prefix") || 
            cfg.contains("luckperms.weight-ordering-enabled") ||
            cfg.contains("luckperms.bot-group") ||
            cfg.contains("luckperms.packet-prefix-char")) {
            
            // Preserve bot-group value as default-group if it was set
            String oldBotGroup = cfg.getString("luckperms.bot-group", "");
            
            // Remove all old LP settings
            cfg.set("luckperms", null);
            log("v31→v32", "removed old luckperms section (weight-ordering, use-prefix, etc.)");
            
            // Set up new simplified LP section
            cfg.set("luckperms.default-group", oldBotGroup);
            log("v31→v32", "migrated luckperms.bot-group → luckperms.default-group = '" + oldBotGroup + "'");
            
            changed = true;
        }
        
        // Also update bot-name.tab-list-format to remove prefix/suffix placeholders
        String tabFormat = cfg.getString("bot-name.tab-list-format", "");
        if (tabFormat.contains("{prefix}") || tabFormat.contains("{suffix}")) {
            cfg.set("bot-name.tab-list-format", "{bot_name}");
            log("v31→v32", "updated bot-name.tab-list-format to '{bot_name}' (LP handles prefix/suffix natively)");
            changed = true;
        }

        return changed;
    }

    private static boolean v32to33(YamlConfiguration cfg) {
        boolean changed = false;

        String[] loggingKeys = {
                "logging.debug.startup",
                "logging.debug.nms",
                "logging.debug.packets",
                "logging.debug.luckperms",
                "logging.debug.network",
                "logging.debug.config-sync",
                "logging.debug.skin",
                "logging.debug.database"
        };

        for (String key : loggingKeys) {
            if (!cfg.contains(key)) {
                cfg.set(key, false);
                changed = true;
            }
        }

        if (changed) {
            log("v32→v33", "added granular logging.debug.* toggles (all default false)");
        }
        return changed;
    }

    /** v33 → v34: Added collision.hit-max-horizontal-speed (separate cap for hit/explosion knockback). */
    private static boolean v33to34(YamlConfiguration cfg) {
        if (cfg.contains("collision.hit-max-horizontal-speed")) return false;
        cfg.set("collision.hit-max-horizontal-speed", 0.80);
        log("v33→v34", "added collision.hit-max-horizontal-speed (default 0.80)");
        return true;
    }

    /** v34 → v35: Added swim-ai section - bots swim up in water/lava like a real player holding spacebar. */
    private static boolean v34to35(YamlConfiguration cfg) {
        if (cfg.contains("swim-ai")) return false;
        cfg.set("swim-ai.enabled", true);
        log("v34→v35", "added swim-ai.enabled = true");
        return true;
    }

    /**
     * v35 → v36: Comprehensive orphaned-key cleanup pass.
     * <ul>
     *   <li>Removes any surviving old LuckPerms keys that should have been cleaned
     *       by v31→v32 but may remain on certain upgrade paths:
     *       {@code weight-offset}, {@code weight-ordering-enabled}, {@code use-prefix},
     *       {@code packet-prefix-char}, {@code bot-group}.</li>
     *   <li>Removes the deprecated {@code skin.custom} sub-section left over from
     *       pre-v4 installations (should have been removed by v3→v4).</li>
     *   <li>Removes the orphaned top-level {@code server} section that should have
     *       been migrated to {@code database.server-id} by v30→v31.</li>
     * </ul>
     */
    private static boolean v35to36(YamlConfiguration cfg) {
        boolean changed = false;

        // ── Orphaned LuckPerms keys ────────────────────────────────────────────
        for (String deadKey : new String[]{
                "luckperms.weight-offset",
                "luckperms.weight-ordering-enabled",
                "luckperms.use-prefix",
                "luckperms.packet-prefix-char",
                "luckperms.bot-group"}) {
            if (cfg.contains(deadKey)) {
                cfg.set(deadKey, null);
                log("v35→v36", "removed orphaned key: " + deadKey);
                changed = true;
            }
        }

        // ── Leftover skin.custom section (pre-v4 installations) ───────────────
        if (cfg.contains("skin.custom")) {
            cfg.set("skin.custom", null);
            log("v35→v36", "removed leftover skin.custom section");
            changed = true;
        }

        // ── Removed skin fallback-pool and fallback-name (simplified in v1.5.4) ──
        // skin.guaranteed-skin default changed to false - no Mojang API fallback pool needed.
        if (cfg.contains("skin.fallback-pool")) {
            cfg.set("skin.fallback-pool", null);
            log("v35→v36", "removed skin.fallback-pool (fallback is now Steve/Alex by default)");
            changed = true;
        }
        if (cfg.contains("skin.fallback-name")) {
            cfg.set("skin.fallback-name", null);
            log("v35→v36", "removed skin.fallback-name (fallback is now Steve/Alex by default)");
            changed = true;
        }
        // Reset guaranteed-skin to false if it was explicitly true (old default was true)
        if (cfg.contains("skin.guaranteed-skin") && cfg.getBoolean("skin.guaranteed-skin", false)) {
            cfg.set("skin.guaranteed-skin", false);
            log("v35→v36", "reset skin.guaranteed-skin to false (new default: Steve/Alex fallback)");
            changed = true;
        }

        // ── Orphaned top-level server: section (should have moved to database.server-id in v30→v31) ──
        if (cfg.contains("server") && cfg.contains("database.server-id")) {
            cfg.set("server", null);
            log("v35→v36", "removed leftover server: section (already in database.server-id)");
            changed = true;
        }

        return changed;
    }

    /**
     * v36 → v37 (FPP 1.5.8):
     * <ul>
     *   <li>No structural config changes - this is a version stamp migration.</li>
     *   <li>Fixes included in this release: LP ClassLoader guard, ghost "Anonymous
     *       User" fix via FakeConnection subclass, %fpp_real% / %fpp_total% accuracy,
     *       NETWORK-mode /fpp list improvements, new proxy placeholders.</li>
     * </ul>
     */
    private static boolean v36to37(YamlConfiguration cfg) {
        // No config keys changed in this release - stamp only.
        log("v36→v37", "version stamp updated to 37 (FPP 1.5.8 - no structural config changes)");
        return false;
    }

    /**
     * v37 → v38: Remove {@code bot-name.tab-list-format}.
     * <ul>
     *   <li>The key is no longer used - LuckPerms manages prefix/suffix natively
     *       for real NMS ServerPlayer entities; the display name packet carries
     *       only the bot name itself.</li>
     *   <li>Any custom value (e.g. {@code "{prefix}{bot_name}{suffix}"}) the user
     *       may have set has had no effect since v31→v32 reset it to {@code "{bot_name}"},
     *       so removing it is safe and non-destructive.</li>
     * </ul>
     */
    private static boolean v37to38(YamlConfiguration cfg) {
        if (!cfg.contains("bot-name.tab-list-format")) return false;
        cfg.set("bot-name.tab-list-format", null);
        log("v37→v38", "removed bot-name.tab-list-format (key no longer used)");
        return true;
    }

    /**
     * v38 → v39: Remove {@code fake-chat.chat-format}.
     * <p>
     * Bots now send chat via {@code Player.chat()} - the server's real chat pipeline
     * handles formatting. The config key was the last remnant of the old fake-broadcast
     * approach and serves no purpose now that local bots route through
     * {@code AsyncChatEvent}.  Bodyless / remote bots use a hardcoded
     * {@code <name> message} Component built directly in code.
     */
    private static boolean v38to39(YamlConfiguration cfg) {
        if (!cfg.contains("fake-chat.chat-format")) return false;
        cfg.set("fake-chat.chat-format", null);
        log("v38→v39", "removed fake-chat.chat-format (key no longer used - chat goes through real pipeline)");
        return true;
    }

    /**
     * v39 → v40: Enhanced bot-chat realism features.
     * <ul>
     *   <li>Adds {@code fake-chat.typing-delay} (default {@code true}) - simulate typing pause.</li>
     *   <li>Adds {@code fake-chat.burst-chance} (default {@code 0.12}) - follow-up message chance.</li>
     *   <li>Adds {@code fake-chat.burst-delay.min/max} (default {@code 2/5}).</li>
     *   <li>Adds {@code fake-chat.reply-to-mentions} (default {@code true}) - bot replies when mentioned.</li>
     *   <li>Adds {@code fake-chat.reply-delay.min/max} (default {@code 2/8}).</li>
     *   <li>Adds {@code fake-chat.stagger-interval} (default {@code 3}) - min gap between bot chats.</li>
     *   <li>Adds {@code fake-chat.activity-variation} (default {@code true}) - per-bot frequency multiplier.</li>
     *   <li>Adds {@code fake-chat.history-size} (default {@code 5}) - no-repeat window per bot.</li>
     * </ul>
     */
    private static boolean v39to40(YamlConfiguration cfg) {
        boolean changed = false;
        if (!cfg.contains("fake-chat.typing-delay")) {
            cfg.set("fake-chat.typing-delay", true);
            log("v39→v40", "added fake-chat.typing-delay = true");
            changed = true;
        }
        if (!cfg.contains("fake-chat.burst-chance")) {
            cfg.set("fake-chat.burst-chance", 0.12);
            log("v39→v40", "added fake-chat.burst-chance = 0.12");
            changed = true;
        }
        if (!cfg.contains("fake-chat.burst-delay.min")) {
            cfg.set("fake-chat.burst-delay.min", 2);
            cfg.set("fake-chat.burst-delay.max", 5);
            log("v39→v40", "added fake-chat.burst-delay section");
            changed = true;
        }
        if (!cfg.contains("fake-chat.reply-to-mentions")) {
            cfg.set("fake-chat.reply-to-mentions", true);
            log("v39→v40", "added fake-chat.reply-to-mentions = true");
            changed = true;
        }
        if (!cfg.contains("fake-chat.reply-delay.min")) {
            cfg.set("fake-chat.reply-delay.min", 2);
            cfg.set("fake-chat.reply-delay.max", 8);
            log("v39→v40", "added fake-chat.reply-delay section");
            changed = true;
        }
        if (!cfg.contains("fake-chat.stagger-interval")) {
            cfg.set("fake-chat.stagger-interval", 3);
            log("v39→v40", "added fake-chat.stagger-interval = 3");
            changed = true;
        }
        if (!cfg.contains("fake-chat.activity-variation")) {
            cfg.set("fake-chat.activity-variation", true);
            log("v39→v40", "added fake-chat.activity-variation = true");
            changed = true;
        }
        if (!cfg.contains("fake-chat.history-size")) {
            cfg.set("fake-chat.history-size", 5);
            log("v39→v40", "added fake-chat.history-size = 5");
            changed = true;
        }
        return changed;
    }

    /**
     * v40 → v41: Add chat debug flag, remote-format, event-triggers, and keyword-reactions.
     */
    private static boolean v40to41(YamlConfiguration cfg) {
        boolean changed = false;
        if (!cfg.contains("logging.debug.chat")) {
            cfg.set("logging.debug.chat", false);
            log("v40→v41", "added logging.debug.chat = false");
            changed = true;
        }
        if (!cfg.contains("fake-chat.remote-format")) {
            cfg.set("fake-chat.remote-format", "<yellow>{name}<dark_gray>: <white>{message}");
            log("v40→v41", "added fake-chat.remote-format");
            changed = true;
        }
        if (!cfg.contains("fake-chat.event-triggers.enabled")) {
            cfg.set("fake-chat.event-triggers.enabled", true);
            log("v40→v41", "added fake-chat.event-triggers.enabled = true");
            changed = true;
        }
        if (!cfg.contains("fake-chat.event-triggers.on-player-join.enabled")) {
            cfg.set("fake-chat.event-triggers.on-player-join.enabled", true);
            cfg.set("fake-chat.event-triggers.on-player-join.chance", 0.40);
            cfg.set("fake-chat.event-triggers.on-player-join.delay.min", 2);
            cfg.set("fake-chat.event-triggers.on-player-join.delay.max", 6);
            log("v40→v41", "added fake-chat.event-triggers.on-player-join section");
            changed = true;
        }
        if (!cfg.contains("fake-chat.event-triggers.on-death.enabled")) {
            cfg.set("fake-chat.event-triggers.on-death.enabled", true);
            cfg.set("fake-chat.event-triggers.on-death.chance", 0.30);
            cfg.set("fake-chat.event-triggers.on-death.delay.min", 1);
            cfg.set("fake-chat.event-triggers.on-death.delay.max", 4);
            log("v40→v41", "added fake-chat.event-triggers.on-death section");
            changed = true;
        }
        if (!cfg.contains("fake-chat.keyword-reactions.enabled")) {
            cfg.set("fake-chat.keyword-reactions.enabled", false);
            log("v40→v41", "added fake-chat.keyword-reactions.enabled = false");
            changed = true;
        }
        if (!cfg.contains("fake-chat.keyword-reactions.keywords")) {
            cfg.set("fake-chat.keyword-reactions.keywords",
                    new java.util.LinkedHashMap<String, String>());
            log("v40→v41", "added fake-chat.keyword-reactions.keywords = {}");
            changed = true;
        }
        return changed;
    }

    /**
     * v41 → v42: Add peak-hours configuration section.
     * All keys default to safe, opt-in values so existing servers are unaffected.
     */
    private static boolean v41to42(YamlConfiguration cfg) {
        boolean changed = false;
        if (!cfg.contains("peak-hours.enabled")) {
            cfg.set("peak-hours.enabled", false);
            log("v41→v42", "added peak-hours.enabled = false");
            changed = true;
        }
        if (!cfg.contains("peak-hours.timezone")) {
            cfg.set("peak-hours.timezone", "UTC");
            log("v41→v42", "added peak-hours.timezone = UTC");
            changed = true;
        }
        if (!cfg.contains("peak-hours.stagger-seconds")) {
            cfg.set("peak-hours.stagger-seconds", 30);
            log("v41→v42", "added peak-hours.stagger-seconds = 30");
            changed = true;
        }
        if (!cfg.contains("peak-hours.schedule")) {
            // Default schedule: early morning → day → evening peak → night sleep
            java.util.List<java.util.Map<String, Object>> schedule = new java.util.ArrayList<>();

            java.util.Map<String, Object> morning = new java.util.LinkedHashMap<>();
            morning.put("start", "06:00"); morning.put("end", "09:00"); morning.put("fraction", 0.30);
            schedule.add(morning);

            java.util.Map<String, Object> day = new java.util.LinkedHashMap<>();
            day.put("start", "09:00"); day.put("end", "18:00"); day.put("fraction", 0.75);
            schedule.add(day);

            java.util.Map<String, Object> peak = new java.util.LinkedHashMap<>();
            peak.put("start", "18:00"); peak.put("end", "22:00"); peak.put("fraction", 1.00);
            schedule.add(peak);

            java.util.Map<String, Object> night = new java.util.LinkedHashMap<>();
            night.put("start", "22:00"); night.put("end", "06:00"); night.put("fraction", 0.05);
            schedule.add(night);

            cfg.set("peak-hours.schedule", schedule);
            log("v41→v42", "added peak-hours.schedule with 4 default windows");
            changed = true;
        }
        return changed;
    }

    /**
     * v42 → v43: Add peak-hours behaviour keys (min-online, auto-enable-swap,
     * notify-transitions) introduced in the dynamic swap integration update.
     */
    private static boolean v42to43(YamlConfiguration cfg) {
        boolean changed = false;
        if (!cfg.contains("peak-hours.min-online")) {
            cfg.set("peak-hours.min-online", 0);
            log("v42→v43", "added peak-hours.min-online = 0");
            changed = true;
        }
        if (!cfg.contains("peak-hours.auto-enable-swap")) {
            cfg.set("peak-hours.auto-enable-swap", true);
            log("v42→v43", "added peak-hours.auto-enable-swap = true");
            changed = true;
        }
        if (!cfg.contains("peak-hours.notify-transitions")) {
            cfg.set("peak-hours.notify-transitions", false);
            log("v42→v43", "added peak-hours.notify-transitions = false");
            changed = true;
        }
        return changed;
    }

    /**
     * v43 → v44: Remove peak-hours.auto-enable-swap (feature removed - users
     * must enable swap manually before using peak-hours).
     */
    private static boolean v43to44(YamlConfiguration cfg) {
        if (cfg.contains("peak-hours.auto-enable-swap")) {
            cfg.set("peak-hours.auto-enable-swap", null); // null = remove key
            log("v43→v44", "removed peak-hours.auto-enable-swap (feature removed)");
            return true;
        }
        return false;
    }

    /**
     * v45 → v46: Add the {@code performance.position-sync-distance} key.
     * This enables per-tick position-packet distance culling (default 128 blocks).
     * Setting the key to 0 restores the old behaviour (send to all players).
     */
    private static boolean v45to46(YamlConfiguration cfg) {
        boolean changed = false;
        if (!cfg.contains("performance.position-sync-distance")) {
            cfg.set("performance.position-sync-distance", 128.0);
            log("v45→v46", "added performance.position-sync-distance = 128.0");
            changed = true;
        }
        return changed;
    }

    /**
     * v46 → v47: adds swap system enhancements - min-online floor, retry config,
     * and a new swap debug category.
     */
    private static boolean v46to47(YamlConfiguration cfg) {
        boolean changed = false;
        if (!cfg.contains("swap.min-online")) {
            cfg.set("swap.min-online", 0);
            log("v46→v47", "added swap.min-online = 0");
            changed = true;
        }
        if (!cfg.contains("swap.retry-rejoin")) {
            cfg.set("swap.retry-rejoin", true);
            log("v46→v47", "added swap.retry-rejoin = true");
            changed = true;
        }
        if (!cfg.contains("swap.retry-delay")) {
            cfg.set("swap.retry-delay", 60);
            log("v46→v47", "added swap.retry-delay = 60");
            changed = true;
        }
        if (!cfg.contains("logging.debug.swap")) {
            cfg.set("logging.debug.swap", false);
            log("v46→v47", "added logging.debug.swap = false");
            changed = true;
        }
        return changed;
    }


    /**
     * v47 → v48: adds body.pick-up-items toggle (default false - bots do not
     * pick up items by default, preserving existing server behaviour).
     */
    private static boolean v47to48(YamlConfiguration cfg) {
        boolean changed = false;
        if (!cfg.contains("body.pick-up-items")) {
            cfg.set("body.pick-up-items", false);
            log("v47→v48", "added body.pick-up-items = false");
            changed = true;
        }
        return changed;
    }

    /**
     * v48 → v49: adds body.pick-up-xp toggle (default true - bots pick up XP
     * orbs by default, matching natural player behaviour).
     * Also resets any existing false value to true since this is a new feature
     * whose default was previously mis-set to false.
     */
    private static boolean v48to49(YamlConfiguration cfg) {
        boolean changed = false;
        // Always set to true - the key was initially shipped with a wrong default of false.
        if (!cfg.contains("body.pick-up-xp") || !cfg.getBoolean("body.pick-up-xp", false)) {
            cfg.set("body.pick-up-xp", true);
            log("v48→v49", "set body.pick-up-xp = true");
            changed = true;
        }
        return changed;
    }

    private static boolean v49to50(YamlConfiguration cfg) {
        boolean changed = false;
        if (!cfg.contains("pathfinding")) {
            cfg.set("pathfinding.parkour",        false);
            cfg.set("pathfinding.break-blocks",   false);
            cfg.set("pathfinding.place-blocks",   false);
            cfg.set("pathfinding.place-material", "DIRT");
            log("v49→v50", "added pathfinding section (parkour / break-blocks / place-blocks / place-material)");
            changed = true;
        }
        return changed;
    }

    /**
     * v50 → v51: reset {@code death.suppress-drops} to {@code false}.
     *
     * <p>The previous default was {@code true}, which caused bots to silently
     * discard all inventory contents on death - making them behave unlike real
     * players and making it impossible to farm or recover bot gear. The new
     * default is {@code false} so bots drop items normally, matching vanilla
     * player behaviour.  Admins who explicitly want to keep the old behaviour
     * can re-enable "Suppress Drops" via {@code /fpp settings} → Body category.
     */
    private static boolean v50to51(YamlConfiguration cfg) {
        cfg.set("death.suppress-drops", false);
        log("v50→v51", "reset death.suppress-drops → false (bots now drop items on death like real players)");
        return true;
    }

    /**
     * v51 → v52: add {@code fake-chat.event-triggers.on-player-chat} section.
     *
     * <p>New feature: bots can spontaneously react when a real player sends any
     * chat message (not just a bot-name mention). Disabled by default so existing
     * servers are not surprised by extra bot activity.
     */
    private static boolean v51to52(YamlConfiguration cfg) {
        boolean changed = false;
        String base = "fake-chat.event-triggers.on-player-chat.";
        if (!cfg.isSet(base + "enabled")) {
            cfg.set(base + "enabled", false);
            changed = true;
        }
        if (!cfg.isSet(base + "use-ai")) {
            cfg.set(base + "use-ai", true);
            changed = true;
        }
        if (!cfg.isSet(base + "ai-cooldown")) {
            cfg.set(base + "ai-cooldown", 30);
            changed = true;
        }
        if (!cfg.isSet(base + "chance")) {
            cfg.set(base + "chance", 0.25);
            changed = true;
        }
        if (!cfg.isSet(base + "max-bots")) {
            cfg.set(base + "max-bots", 1);
            changed = true;
        }
        if (!cfg.isSet(base + "ignore-short")) {
            cfg.set(base + "ignore-short", true);
            changed = true;
        }
        if (!cfg.isSet(base + "ignore-commands")) {
            cfg.set(base + "ignore-commands", true);
            changed = true;
        }
        if (!cfg.isSet(base + "mention-player")) {
            cfg.set(base + "mention-player", 0.50);
            changed = true;
        }
        if (!cfg.isSet(base + "delay.min")) {
            cfg.set(base + "delay.min", 2);
            changed = true;
        }
        if (!cfg.isSet(base + "delay.max")) {
            cfg.set(base + "delay.max", 8);
            changed = true;
        }
        if (changed) {
            log("v51→v52", "added fake-chat.event-triggers.on-player-chat section (AI-powered player chat reactions — disabled by default)");
        }
        return changed;
    }


    /**
     * v52 → v53: chunk-loading.radius semantics changed.
     * Old: {@code 0} = use server simulation-distance (auto).
     * New: {@code 0} = disable chunk loading; {@code "auto"} = server simulation-distance.
     * Any existing {@code radius: 0} is migrated to {@code radius: "auto"} so that
     * existing servers keep the same behaviour (chunks loaded at simulation-distance).
     */
    private static boolean v52to53(YamlConfiguration cfg) {
        Object raw = cfg.get("chunk-loading.radius");
        // Migrate integer 0 → "auto"
        if (raw instanceof Number n && n.intValue() == 0) {
            cfg.set("chunk-loading.radius", "auto");
            log("v52→v53", "chunk-loading.radius 0 → \"auto\" (0 now means no chunk loading; \"auto\" = server simulation-distance)");
            return true;
        }
        return false;
    }

    /**
     * v53 → v54: Added {@code body.drop-items-on-despawn} option.
     * Defaults to {@code false} — no behaviour change for existing installs.
     */
    private static boolean v53to54(YamlConfiguration cfg) {
        if (cfg.isSet("body.drop-items-on-despawn")) return false;
        cfg.set("body.drop-items-on-despawn", false);
        log("v53→v54", "added body.drop-items-on-despawn (default false)");
        return true;
    }

    /**
     * v54 → v55: Add shared global pathfinding tuning keys used by the plugin-wide
     * navigation service.
     */
    private static boolean v54to55(YamlConfiguration cfg) {
        boolean changed = false;
        changed |= setIfMissing(cfg, "pathfinding.arrival-distance", 1.2);
        changed |= setIfMissing(cfg, "pathfinding.patrol-arrival-distance", 1.5);
        changed |= setIfMissing(cfg, "pathfinding.waypoint-arrival-distance", 0.65);
        changed |= setIfMissing(cfg, "pathfinding.sprint-distance", 6.0);
        changed |= setIfMissing(cfg, "pathfinding.follow-recalc-distance", 3.5);
        changed |= setIfMissing(cfg, "pathfinding.recalc-interval", 60);
        changed |= setIfMissing(cfg, "pathfinding.stuck-ticks", 8);
        changed |= setIfMissing(cfg, "pathfinding.stuck-threshold", 0.04);
        changed |= setIfMissing(cfg, "pathfinding.break-ticks", 15);
        changed |= setIfMissing(cfg, "pathfinding.place-ticks", 5);
        changed |= setIfMissing(cfg, "pathfinding.max-range", 64);
        changed |= setIfMissing(cfg, "pathfinding.max-nodes", 2000);
        changed |= setIfMissing(cfg, "pathfinding.max-nodes-extended", 4000);
        if (changed) {
            log("v54→v55", "added shared global pathfinding tuning keys");
        }
        return changed;
    }

    private static boolean setIfMissing(YamlConfiguration cfg, String path, Object value) {
        if (cfg.isSet(path)) return false;
        cfg.set(path, value);
        return true;
    }


    /**
     * Logs a migration step message.
     * Uses {@link #rawDebug} read from the raw YAML - never touches {@code Config.cfg}
     * because this runs before {@code Config.init()}
     */
    private static void log(String step, String message) {
        if (rawDebug) {
            FppLogger.info("[ConfigMigrator][" + step + "] " + message);
        }
    }

    /** Single-arg variant used for non-step messages (e.g. "config is current"). */
    private static void log(String message) {
        if (rawDebug) {
            FppLogger.info("[ConfigMigrator] " + message);
        }
    }
}




