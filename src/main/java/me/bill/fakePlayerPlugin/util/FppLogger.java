package me.bill.fakePlayerPlugin.util;

import me.bill.fakePlayerPlugin.config.Config;

import java.util.logging.Logger;

/**
 * Enhanced coloured console logger for ꜰᴀᴋᴇ ᴘʟᴀʏᴇʀ ᴘʟᴜɢɪɴ.
 *
 * <p>Uses ANSI escape codes supported by Paper's JLine-backed console.
 * Falls back gracefully on terminals that strip ANSI (the text is still readable).
 *
 * <h3>Log levels</h3>
 * <ul>
 *   <li>{@link #info}    — plain white  — general information</li>
 *   <li>{@link #success} — bright green — positive confirmation</li>
 *   <li>{@link #warn}    — gold/amber   — non-fatal warnings</li>
 *   <li>{@link #error}   — bright red   — errors that need attention</li>
 *   <li>{@link #debug}   — yellow+grey  — verbose, only when a matching debug toggle is enabled</li>
 *   <li>{@link #highlight} — cyan bold  — important state changes (enable/disable)</li>
 * </ul>
 *
 * <h3>Formatting helpers</h3>
 * <ul>
 *   <li>{@link #section}  — labelled separator line</li>
 *   <li>{@link #rule}     — plain separator line</li>
 *   <li>{@link #kv}       — "  key ....... value" row</li>
 *   <li>{@link #statusRow} — "  [✔/✘] label : value" row</li>
 *   <li>{@link #blank}    — empty line</li>
 * </ul>
 */
public final class FppLogger {

    // ── ANSI codes ────────────────────────────────────────────────────────────
    private static final String RESET    = "\u001B[0m";
    private static final String BOLD     = "\u001B[1m";

    // Main accent: #0079FF
    private static final String BLUE     = "\u001B[38;2;0;121;255m";
    // Bright white for normal info
    private static final String WHITE    = "\u001B[97m";
    // Yellow for debug
    private static final String YELLOW   = "\u001B[93m";
    // Green for success / OK
    private static final String GREEN    = "\u001B[92m";
    // Gold/amber for warn
    private static final String GOLD     = "\u001B[33m";
    // Red for error / FAIL
    private static final String RED      = "\u001B[91m";
    // Grey for decoration / muted text
    private static final String GRAY     = "\u001B[90m";
    // Cyan for highlight
    private static final String CYAN     = "\u001B[96m";
    // Dark grey for rule lines
    private static final String DARK     = "\u001B[38;5;240m";

    /** FPP tag shown at the start of every line. */
    private static final String TAG      = BOLD + BLUE + "[ꜰᴘᴘ]" + RESET;

    /** Width of the separator rule (printable characters). */
    private static final int RULE_WIDTH  = 50;
    /** Width of the key column in kv() rows. */
    private static final int KEY_WIDTH   = 18;

    private static Logger logger;

    private FppLogger() {}

    // ── Lifecycle ─────────────────────────────────────────────────────────────

    public static void init(Logger javaLogger) {
        logger = javaLogger;
    }

    // ── Core log methods ──────────────────────────────────────────────────────

    /** General info — white. */
    public static void info(String message) {
        logger.info(TAG + " " + WHITE + message + RESET);
    }

    /** Positive confirmation — bright green. */
    public static void success(String message) {
        logger.info(TAG + " " + GREEN + message + RESET);
    }

    /** Warning — gold/amber. Appears in console as a WARNING-level entry. */
    public static void warn(String message) {
        logger.warning(TAG + " " + GOLD + message + RESET);
    }

    /** Error — bright red. Appears in console as a SEVERE-level entry. */
    public static void error(String message) {
        logger.severe(TAG + " " + RED + message + RESET);
    }

    /**
     * Debug — yellow, only emitted when {@code debug: true} in config.
     * Prefixed with a grey [DEBUG] badge.
     */
    public static void debug(String message) {
        debug("GENERAL", Config.isDebug(), message);
    }

    /**
     * Category debug — only emitted when the provided toggle is enabled.
     * Example badge: {@code [DEBUG/NMS]} or {@code [DEBUG/NETWORK]}.
     */
    public static void debug(String topic, boolean enabled, String message) {
        if (!enabled) return;
        String label = (topic == null || topic.isBlank()) ? "DEBUG" : topic.trim().toUpperCase();
        logger.info(TAG + " " + GRAY + "[" + YELLOW + "DEBUG" + GRAY + "/"
                + CYAN + label + GRAY + "] " + YELLOW + message + RESET);
    }

    /**
     * Highlight — bold cyan, for important state transitions such as
     * plugin enable/disable. Use sparingly so it stands out.
     */
    public static void highlight(String message) {
        logger.info(TAG + " " + BOLD + CYAN + message + RESET);
    }

    // ── Formatting helpers ────────────────────────────────────────────────────

    /**
     * Prints a horizontal rule (separator line).
     * Example: {@code ── ── ── ── ── ── ── ── ── ── ── ── ── ── ── ─}
     */
    public static void rule() {
        logger.info(TAG + " " + DARK + "─".repeat(RULE_WIDTH) + RESET);
    }

    /**
     * Prints a bold rule — used for the very top and bottom of banners.
     */
    public static void boldRule() {
        logger.info(TAG + " " + GRAY + BOLD + "═".repeat(RULE_WIDTH) + RESET);
    }

    /**
     * Prints a labelled section header inside a banner.
     * Example: {@code ── Config ───────────────────────────────────────}
     *
     * @param label the section label (short, plain text)
     */
    public static void section(String label) {
        String dashes = "─".repeat(Math.max(0, RULE_WIDTH - label.length() - 4));
        logger.info(TAG + " " + DARK + "── " + RESET + BOLD + WHITE + label
                + " " + DARK + dashes + RESET);
    }

    /**
     * Prints a key→value row with dot-padding between key and value.
     * <pre>
     *   Language ............ en
     *   Debug ............... false
     * </pre>
     *
     * @param key   the label (left side)
     * @param value the value (right side, rendered in accent blue)
     */
    public static void kv(String key, Object value) {
        int dots = Math.max(1, KEY_WIDTH - key.length());
        String dotStr = DARK + ".".repeat(dots) + RESET;
        logger.info(TAG + " " + GRAY + "  " + WHITE + key + " " + dotStr + " " + BLUE + value + RESET);
    }

    /**
     * Prints a status row showing an OK (+) or FAIL (✘) badge.
     * <pre>
     *   [+] Database ......... SQLite (local)
     *   [✘] MySQL ........... disabled
     * </pre>
     *
     * @param ok    whether the subsystem is healthy / enabled
     * @param label short label
     * @param detail extra detail string shown after a colon
     */
    public static void statusRow(boolean ok, String label, String detail) {
        String badge  = ok ? GREEN + "[+]" + RESET : RED + "[✘]" + RESET;
        int dots      = Math.max(1, KEY_WIDTH - label.length());
        String dotStr = DARK + ".".repeat(dots) + RESET;
        String valueColor = ok ? GREEN : GRAY;
        logger.info(TAG + " " + GRAY + "  " + badge + " " + WHITE + label
                + " " + dotStr + " " + valueColor + detail + RESET);
    }

    /** Status row with explicit state (OK/WARN/OFF) for startup summaries. */
    private static void stateRow(RowState state, String label, String detail) {
        String badge;
        String valueColor;
        switch (state) {
            case OK -> {
                badge = GREEN + "[+]" + RESET;
                valueColor = GREEN;
            }
            case WARN -> {
                badge = GOLD + "[!]" + RESET;
                valueColor = GOLD;
            }
            default -> {
                badge = GRAY + "[-]" + RESET;
                valueColor = GRAY;
            }
        }

        int dots = Math.max(1, KEY_WIDTH - label.length());
        String dotStr = DARK + ".".repeat(dots) + RESET;
        logger.info(TAG + " " + GRAY + "  " + badge + " " + WHITE + label
                + " " + dotStr + " " + valueColor + detail + RESET);
    }

    /**
     * Prints an empty (blank) separator line — useful between sections.
     */
    @SuppressWarnings("unused") // Public API — available for callers outside this class
    public static void blank() {
        logger.info("");
    }

    // ── Banner helpers ────────────────────────────────────────────────────────

    /**
     * Prints the full FPP startup banner including version, author, and a
     * subsystem status table.
     *
     * <p>Call after ALL subsystems have been initialised so statuses are accurate.
     *
     * @param version          plugin version string
     * @param authors          comma-joined author list
     * @param namePoolSize     number of names in the name pool
     * @param msgPoolSize      number of chat messages in the pool
     * @param dbState          resolved DB state text, e.g. "SQLite (local)", "MySQL", "disabled", "MySQL (failed)"
     * @param skinMode         value from Config.skinMode()
     * @param bodyEnabled      whether physical bodies are spawned
     * @param persistEnabled   whether bot persistence is on
     * @param luckPermsFound   whether LuckPerms is installed
     * @param swapEnabled      whether bot swap/rotation is on
     * @param fakeChatEnable   whether fake chat is on
     * @param chunkLoading     whether chunk loading is on
     * @param maxBots          global bot limit (0 = unlimited)
     * @param metricsActive    whether FastStats metrics are running
     * @param compatRestricted whether the server failed a compatibility check
     * @param configVersion    formatted config version string, e.g. {@code "v19 ✔"}
     * @param backupCount      number of config backups on disk (0 = none)
     * @param startupMs        plugin enable time in milliseconds
     */
    public static void printStartupBanner(
            String  version,
            String  authors,
            int     namePoolSize,
            int     msgPoolSize,
            String  dbState,
            String  skinMode,
            boolean bodyEnabled,
            boolean persistEnabled,
            boolean luckPermsFound,
            boolean swapEnabled,
            boolean fakeChatEnable,
            boolean chunkLoading,
            int     maxBots,
            boolean metricsActive,
            boolean compatRestricted,
            String  configVersion,
            int     backupCount,
            long    startupMs) {
        boldRule();
        info("  " + BOLD + BLUE + "FakePlayerPlugin" + RESET + WHITE + " v" + version + RESET);
        rule();

        section("Runtime");
        stateRow(compatRestricted ? RowState.WARN : RowState.OK,
                "Compatibility", compatRestricted ? "restricted" : "ok");
        stateRow(resolveDbState(dbState), "Database", dbState);
        kv("Config version", configVersion);
        kv("Backups", backupCount);
        kv("Startup time", startupMs + "ms");

        section("Features");
        stateRow(bodyEnabled ? RowState.OK : RowState.OFF, "Physical bodies", onOff(bodyEnabled));
        stateRow(persistEnabled ? RowState.OK : RowState.OFF, "Persistence", onOff(persistEnabled));
        stateRow(chunkLoading ? RowState.OK : RowState.OFF, "Chunk loading", onOff(chunkLoading));
        stateRow(swapEnabled ? RowState.OK : RowState.OFF, "Swap AI", onOff(swapEnabled));
        stateRow(fakeChatEnable ? RowState.OK : RowState.OFF, "Fake chat", onOff(fakeChatEnable));

        section("Integrations");
        stateRow(luckPermsFound ? RowState.OK : RowState.OFF, "LuckPerms", onOff(luckPermsFound));
        stateRow(metricsActive ? RowState.OK : RowState.OFF, "Metrics", onOff(metricsActive));

        section("Pools & Limits");
        kv("Name pool", namePoolSize);
        kv("Message pool", msgPoolSize);
        kv("Skin mode", skinMode);
        kv("Max bots", maxBots == 0 ? "unlimited" : maxBots);

        if (Config.isDebug()) {
            section("Debug");
            kv("Authors", authors);
        }

        rule();
        success("  Ready: /fpp help");
        boldRule();
    }

    /**
     * Prints the shutdown banner summarising what was cleaned up.
     *
     * @param botsRemoved number of bots that were cleanly removed
     * @param dbFlushed   whether DB sessions were flushed
     * @param uptimeMs    server uptime since plugin enable, in milliseconds
     */
    public static void printShutdownBanner(int botsRemoved, boolean dbFlushed, long uptimeMs) {
        boldRule();
        highlight("  ꜰᴀᴋᴇ ᴘʟᴀʏᴇʀ ᴘʟᴜɢɪɴ  —  shutting down");
        rule();
        kv("Bots removed",   botsRemoved);
        kv("DB sessions",    dbFlushed ? "flushed ✔" : "skipped (no DB)");
        kv("Session uptime", formatUptime(uptimeMs));
        boldRule();
        info("  Goodbye! ꜰᴀᴋᴇ ᴘʟᴀʏᴇʀ ᴘʟᴜɢɪɴ has been disabled.");
        boldRule();
    }

    // ── Internal helpers ──────────────────────────────────────────────────────

    /**
     * Formats a millisecond duration as {@code Xh Ym Zs}.
     * Used in the shutdown banner to show how long the plugin ran.
     */
    private static String formatUptime(long ms) {
        long totalSec = ms / 1_000;
        long hours    = totalSec / 3600;
        long minutes  = (totalSec % 3600) / 60;
        long seconds  = totalSec % 60;
        if (hours > 0) return hours + "h " + minutes + "m " + seconds + "s";
        if (minutes > 0) return minutes + "m " + seconds + "s";
        return seconds + "s";
    }

    private static String onOff(boolean enabled) {
        return enabled ? "enabled" : "disabled";
    }

    private static RowState resolveDbState(String dbState) {
        if (dbState == null) return RowState.WARN;
        String s = dbState.toLowerCase();
        if (s.contains("failed")) return RowState.WARN;
        if (s.contains("disabled") || s.contains("none")) return RowState.OFF;
        return RowState.OK;
    }

    private enum RowState {
        OK,
        WARN,
        OFF
    }
}
