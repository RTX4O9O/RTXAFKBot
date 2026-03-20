package me.bill.fakePlayerPlugin.util;

import me.bill.fakePlayerPlugin.config.Config;
import org.bukkit.Bukkit;
import org.bukkit.plugin.Plugin;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.net.HttpURLConnection;
import java.net.URI;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Checks a remote update API for a newer version of FakePlayerPlugin.
 *
 * <p>The check runs asynchronously once on startup (and optionally on {@code /fpp reload}).
 * It can be disabled in {@code config.yml} via {@code update-checker.enabled: false}.
 *
 * <p>By default this uses the project's update API endpoint hosted on Vercel.
 */
public final class UpdateChecker {

    /** Update API endpoint (replaceable). */
    private static final String API_URL =
            "https://fake-player-plugin-12tbdipae-pepe-tfs-projects.vercel.app";
    private static final String MODRINTH_URL = "https://modrinth.com/plugin/fake-player-plugin-(fpp)";

    private UpdateChecker() {}

    /**
     * Runs the update check asynchronously.
     * Emits a nicely-formatted warning block if a newer version is available.
     * Does nothing if {@code update-checker.enabled} is {@code false} in config.
     */
    public static void check(Plugin plugin) {
        if (!Config.updateCheckerEnabled()) {
            Config.debug("Update checker disabled in config.");
            return;
        }

        Bukkit.getScheduler().runTaskAsynchronously(plugin, () -> {
            try {
                HttpURLConnection conn = (HttpURLConnection)
                        URI.create(API_URL).toURL().openConnection();
                conn.setRequestMethod("GET");
                conn.setConnectTimeout(5_000);
                conn.setReadTimeout(5_000);
                conn.setRequestProperty("Accept", "application/json");
                conn.setRequestProperty("User-Agent", "FakePlayerPlugin-UpdateChecker/" +
                        plugin.getPluginMeta().getVersion());

                int code = conn.getResponseCode();
                if (code != 200) {
                    Config.debug("UpdateChecker: HTTP " + code + " — skipping.");
                    return;
                }

                StringBuilder sb = new StringBuilder();
                try (BufferedReader r = new BufferedReader(
                        new InputStreamReader(conn.getInputStream()))) {
                    String line;
                    while ((line = r.readLine()) != null) sb.append(line);
                }

                String json    = sb.toString();
                String latest  = extractLatestVersion(json);
                String current = plugin.getPluginMeta().getVersion();

                if (latest == null || latest.isBlank()) {
                    Config.debug("UpdateChecker: could not parse tag_name from response.");
                    return;
                }

                // Normalise — strip leading 'v' if present
                String latestClean  = latest.startsWith("v")  ? latest.substring(1)  : latest;
                String currentClean = current.startsWith("v") ? current.substring(1) : current;

                if (!latestClean.equals(currentClean)) {
                    // Print a clearly-visible update notice using logger helpers
                    FppLogger.warn("┌────────────────────────────────────────────────┐");
                    FppLogger.warn("│     ꜰᴀᴋᴇ ᴘʟᴀʏᴇʀ ᴘʟᴜɢɪɴ  Update Available!    │");
                    FppLogger.warn("│                                                │");
                    FppLogger.warn("│  Running : v" + padRight(currentClean, 35) + "│");
                    FppLogger.warn("│  Latest  : v" + padRight(latestClean,  35) + "│");
                    FppLogger.warn("│                                                │");
                    FppLogger.warn("│  Download: " + padRight(MODRINTH_URL, 36) + "│");
                    FppLogger.warn("└────────────────────────────────────────────────┘");
                } else {
                    FppLogger.success("Running the latest version: v" + currentClean + "  ✔");
                }

            } catch (java.net.SocketTimeoutException e) {
                Config.debug("UpdateChecker timed out (no internet / GitHub unreachable).");
            } catch (Exception e) {
                Config.debug("UpdateChecker failed: " + e.getClass().getSimpleName() + ": " + e.getMessage());
            }
        });
    }

    // ── Helpers ───────────────────────────────────────────────────────────────

    /**
     * Attempts to extract a version string from the response body.
     *
     * <p>The remote API may return different JSON shapes. We try several common keys
     * ({@code tag_name}, {@code version}, {@code latest}, {@code name}) and fall back to
     * a version-like regex (e.g. "1.2.3" or "v1.2.3"). Returns {@code null} when
     * no plausible version could be found.
     */
    private static String extractLatestVersion(String body) {
        if (body == null || body.isBlank()) return null;

        // Try common JSON keys
        String[] keys = {"tag_name", "version", "latest", "name"};
        for (String key : keys) {
            int idx = body.indexOf('"' + key + '"');
            if (idx == -1) continue;
            int colon = body.indexOf(':', idx);
            if (colon == -1) continue;
            int q1 = body.indexOf('"', colon + 1);
            if (q1 == -1) continue;
            int q2 = body.indexOf('"', q1 + 1);
            if (q2 == -1) continue;
            String candidate = body.substring(q1 + 1, q2).trim();
            if (!candidate.isBlank()) return candidate;
        }

        // Fallback: find first version-like token using regex
        Pattern p = Pattern.compile("v?\\d+(?:\\.\\d+)+");
        Matcher m = p.matcher(body);
        if (m.find()) return m.group();

        // As a last resort return trimmed body if it's short and looks reasonable
        String trimmed = body.trim();
        if (trimmed.length() <= 100) return trimmed;
        return null;
    }

    private static String padRight(String s, int len) {
        if (s.length() >= len) return s.substring(0, len);
        return s + " ".repeat(len - s.length());
    }
}
