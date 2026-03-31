package me.bill.fakePlayerPlugin.util;

import net.kyori.adventure.text.Component;
import org.bukkit.Bukkit;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Centralised compatibility checker for FakePlayerPlugin.
 *
 * <h3>Checks performed at startup (in order)</h3>
 * <ol>
 *   <li><b>PaperMC detection</b> — the plugin requires Paper or a Paper fork.</li>
 *   <li><b>Minecraft version</b> — must be &ge; {@value #MIN_MC_VERSION}.</li>
 *   <li><b>Physical body support</b> — checks that the server version supports
 *       physical bot body rendering; required for the NMS ServerPlayer-based system.</li>
 * </ol>
 *
 * <p>Each failing check is logged individually on the console with a specific
 * impact summary, and contributes a matching lang key so the in-game warning
 * shown to admins is precise rather than generic.
 *
 * <p>The overall {@link Result#restricted} flag drives
 * {@link me.bill.fakePlayerPlugin.FakePlayerPlugin#isCompatibilityRestricted()}.
 */
public final class CompatibilityChecker {

    /** Minimum supported Minecraft version. Bump this when a new MC version is required. */
    public static final String MIN_MC_VERSION = "1.21.9";

    private CompatibilityChecker() {}

    // ── Result ────────────────────────────────────────────────────────────────

    /**
     * Immutable result of a single compatibility pass.
     * Access individual check outcomes via the boolean fields, or iterate
     * {@link #failureLangKeys} for all failures in order.
     */
    public static final class Result {

        /** {@code true} when at least one check failed and features are restricted. */
        public final boolean      restricted;
        /** {@code true} when running on PaperMC or a Paper fork. */
        public final boolean      isPaper;
        /** {@code true} when the running Minecraft version meets {@link #MIN_MC_VERSION}. */
        public final boolean      isVersionSupported;
        /** Detected Minecraft version, e.g. {@code "1.21.11"}. {@code "unknown"} if undetectable. */
        public final String       detectedVersion;
        /**
         * Ordered list of lang keys for each failing check.
         * Each key supports {@code {version}} and {@code {required}} placeholders.
         * Empty when all checks pass.
         */
        public final List<String> failureLangKeys;

        Result(boolean restricted, boolean isPaper, boolean isVersionSupported,
               String detectedVersion, List<String> failureLangKeys) {
            this.restricted         = restricted;
            this.isPaper            = isPaper;
            this.isVersionSupported = isVersionSupported;
            this.detectedVersion    = detectedVersion;
            this.failureLangKeys    = Collections.unmodifiableList(failureLangKeys);
        }

        @Override
        public String toString() {
            return "CompatibilityResult{restricted=" + restricted
                    + ", paper=" + isPaper
                    + ", version=" + detectedVersion + (isVersionSupported ? "✔" : "✗") + '}';
        }
    }

    // ── Public API ────────────────────────────────────────────────────────────

    /**
     * Runs all compatibility checks, logs detailed console output for each
     * failure, and returns an immutable {@link Result}.
     *
     * <p>Safe to call at any point after the Bukkit server is initialised.
     * Never throws — all exceptions are caught internally.
     */
    public static Result check() {
        boolean      isPaper            = false;
        boolean      isVersionSupported;
        String       detectedVersion    = "unknown";
        List<String> failureKeys        = new ArrayList<>();

        // ── 1. PaperMC ────────────────────────────────────────────────────────
        try {
            isPaper = detectPaper();
        } catch (Throwable t) {
            FppLogger.debug("CompatibilityChecker: paper detection threw: " + t.getMessage());
        }
        if (!isPaper) {
            FppLogger.warn("━━ Compatibility Issue #1: Non-Paper Server ━━");
            FppLogger.warn("  This plugin targets PaperMC. Detected: " + safeBukkitVersion());
            FppLogger.warn("  Impact: physical bodies, chunk loading and head AI are disabled.");
            FppLogger.warn("  Fix: switch to Paper — https://papermc.io/downloads/paper");
            failureKeys.add("compatibility-not-paper");
        }

        // ── 2. Minecraft version ──────────────────────────────────────────────
        try {
            detectedVersion    = extractMcVersion();
            isVersionSupported = isVersionAtLeast(detectedVersion, MIN_MC_VERSION);
        } catch (Throwable t) {
            FppLogger.debug("CompatibilityChecker: version check threw: " + t.getMessage());
            isVersionSupported = false;
        }
        if (!isVersionSupported) {
            FppLogger.warn("━━ Compatibility Issue #2: Outdated Minecraft Version ━━");
            FppLogger.warn("  Detected: " + detectedVersion
                    + " — minimum required: " + MIN_MC_VERSION);
            FppLogger.warn("  Impact: physical bodies, chunk loading and head AI are disabled.");
            FppLogger.warn("  Fix: update your Paper server to " + MIN_MC_VERSION + " or newer.");
            failureKeys.add("compatibility-version-old");
        }


        boolean restricted = !failureKeys.isEmpty();
        if (restricted) {
            FppLogger.warn("Restricted compatibility mode active — "
                    + failureKeys.size() + " issue(s). Bot tab-list entries still work normally.");
        } else {
            FppLogger.debug("Compatibility check passed (MC " + detectedVersion + ").");
        }

        return new Result(restricted, isPaper, isVersionSupported,
                detectedVersion, failureKeys);
    }

    /**
     * Builds a player-facing {@link Component} that lists every failing check.
     * Each line uses the matching lang key so the message is translatable and
     * server-owner-customisable.
     *
     * @return combined warning {@link Component}, or {@code null} if no failures.
     */
    public static Component buildWarningComponent(Result result) {
        if (!result.restricted) return null;

        Component msg = me.bill.fakePlayerPlugin.lang.Lang.get("compatibility-header");
        for (String key : result.failureLangKeys) {
            msg = msg.append(Component.newline())
                     .append(me.bill.fakePlayerPlugin.lang.Lang.get(key,
                             "version",  result.detectedVersion,
                             "required", MIN_MC_VERSION));
        }
        return msg;
    }

    // ── Version utilities ─────────────────────────────────────────────────────

    /**
     * Extracts the plain Minecraft version from Bukkit's version string.
     * Example: {@code "1.21.11-R0.1-SNAPSHOT"} → {@code "1.21.11"}.
     */
    public static String extractMcVersion() {
        try {
            String bv = Bukkit.getBukkitVersion();
            return bv.contains("-") ? bv.split("-", 2)[0] : bv;
        } catch (Throwable ignored) {}
        return "0.0.0";
    }

    /**
     * Returns {@code true} when {@code version >= required} using
     * component-wise numeric comparison.
     * Handles pre-release suffixes (e.g. {@code "21-SNAPSHOT"} → 21).
     */
    public static boolean isVersionAtLeast(String version, String required) {
        int[] v = parseVersionParts(version);
        int[] r = parseVersionParts(required);
        int len = Math.max(v.length, r.length);
        for (int i = 0; i < len; i++) {
            int vi = i < v.length ? v[i] : 0;
            int ri = i < r.length ? r[i] : 0;
            if (vi != ri) return vi > ri;
        }
        return true; // equal counts as supported
    }

    // ── Internals ─────────────────────────────────────────────────────────────

    private static boolean detectPaper() {
        String ver  = Bukkit.getVersion().toLowerCase();
        String bver = Bukkit.getBukkitVersion().toLowerCase();
        String name = Bukkit.getServer().getName().toLowerCase();
        return ver.contains("paper") || bver.contains("paper") || name.contains("paper");
    }

    private static boolean classExists(String className) {
        try   { Class.forName(className); return true; }
        catch (ClassNotFoundException | LinkageError ignored) { return false; }
    }

    private static int[] parseVersionParts(String v) {
        if (v == null || v.isBlank()) return new int[0];
        String[] raw = v.split("\\.", -1);
        int[] parts = new int[raw.length];
        for (int i = 0; i < raw.length; i++) {
            Matcher m = Pattern.compile("^(\\d+)").matcher(raw[i]);
            parts[i] = m.find() ? Integer.parseInt(m.group(1)) : 0;
        }
        return parts;
    }

    private static String safeBukkitVersion() {
        try { return Bukkit.getVersion(); } catch (Throwable ignored) { return "unknown"; }
    }
}


