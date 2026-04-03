package me.bill.fakePlayerPlugin.util;

import net.kyori.adventure.text.Component;

import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Compatibility utilities for FakePlayerPlugin.
 *
 * <p>The old version-gating system (Paper detection, minimum MC version check)
 * has been removed. The NMS fake-player system now supports all server versions
 * without restricting features. {@link #check()} always returns a non-restricted
 * result.
 *
 * <p>The static helpers {@link #extractMcVersion()} and {@link #isVersionAtLeast}
 * are retained as general-purpose utilities.
 */
public final class CompatibilityChecker {

    private CompatibilityChecker() {}

    // ── Result ────────────────────────────────────────────────────────────────

    /**
     * Immutable result of a single compatibility pass.
     */
    public static final class Result {

        /** Always {@code false} — no checks fail in the current implementation. */
        public final boolean      restricted;
        /** Always {@code true}. */
        public final boolean      isPaper;
        /** Always {@code true}. */
        public final boolean      isVersionSupported;
        /** Detected Minecraft version, e.g. {@code "1.21.11"}. {@code "unknown"} if undetectable. */
        public final String       detectedVersion;
        /** Always empty — no checks fail. */
        public final List<String> failureLangKeys;

        Result(boolean restricted, boolean isPaper, boolean isVersionSupported,
               String detectedVersion, List<String> failureLangKeys) {
            this.restricted         = restricted;
            this.isPaper            = isPaper;
            this.isVersionSupported = isVersionSupported;
            this.detectedVersion    = detectedVersion;
            this.failureLangKeys    = List.copyOf(failureLangKeys);
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
     * Always returns a non-restricted result.
     *
     * <p>The old Paper-detection and minimum-version gate has been removed.
     * The NMS fake-player system now supports all server versions without
     * restricting any features.
     */
    public static Result check() {
        String detectedVersion = "unknown";
        try {
            detectedVersion = extractMcVersion();
        } catch (Throwable ignored) {}
        FppLogger.debug("Compatibility check skipped — all features enabled (MC " + detectedVersion + ").");
        return new Result(false, true, true, detectedVersion, List.of());
    }

    /**
     * Always returns {@code null} since {@code result.restricted} is always {@code false}.
     *
     * @return {@code null}
     */
    @SuppressWarnings("unused") // Kept for API compatibility
    public static Component buildWarningComponent(Result result) {
        return null;
    }

    // ── Version utilities ─────────────────────────────────────────────────────

    /**
     * Extracts the plain Minecraft version from Bukkit's version string.
     * Example: {@code "1.21.11-R0.1-SNAPSHOT"} → {@code "1.21.11"}.
     */
    public static String extractMcVersion() {
        try {
            String bv = org.bukkit.Bukkit.getBukkitVersion();
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
        return true;
    }

    // ── Internals ─────────────────────────────────────────────────────────────

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
}
