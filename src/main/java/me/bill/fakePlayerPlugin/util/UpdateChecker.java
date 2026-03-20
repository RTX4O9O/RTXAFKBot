package me.bill.fakePlayerPlugin.util;

import me.bill.fakePlayerPlugin.config.Config;
import org.bukkit.Bukkit;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.entity.Player;
import org.bukkit.plugin.Plugin;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.net.HttpURLConnection;
import java.net.URI;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;

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
            "https://fake-player-plugin.vercel.app";
    private static final String MODRINTH_URL = "https://modrinth.com/plugin/fake-player-plugin-(fpp)";
    // Strict version-like pattern: v?1.2.3 etc. Only accept these as valid versions.
    private static final Pattern VERSION_REGEX = Pattern.compile("v?\\d+(?:\\.\\d+)+");

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

        // Run the fetch asynchronously and handle result on the main thread
        Bukkit.getScheduler().runTaskAsynchronously(plugin, () -> {
            UpdateInfo info = fetchUpdateInfo(plugin);
            Bukkit.getScheduler().runTask(plugin, () -> handleResultOnMainThread(plugin, info));
        });
    }

    /**
     * Performs the update check asynchronously but blocks the caller up to {@code timeoutMs}
     * milliseconds waiting for a result. Returns {@code null} on timeout or if the check is
     * disabled.
     */
    public static UpdateInfo checkBlocking(Plugin plugin, long timeoutMs) {
        if (!Config.updateCheckerEnabled()) {
            Config.debug("Update checker disabled in config.");
            return null;
        }
        final UpdateInfo[] out = new UpdateInfo[1];
        CountDownLatch latch = new CountDownLatch(1);
        Bukkit.getScheduler().runTaskAsynchronously(plugin, () -> {
            out[0] = fetchUpdateInfo(plugin);
            latch.countDown();
        });
        try {
            boolean ok = latch.await(Math.max(100, timeoutMs), TimeUnit.MILLISECONDS);
            if (ok) return out[0];
        } catch (InterruptedException ignored) {
            Thread.currentThread().interrupt();
        }
        return null;
    }

    /** Handle the fetched result on the main server thread: log and notify players if needed. */
    public static void handleResultOnMainThread(Plugin plugin, UpdateInfo info) {
        if (info == null) return;
        if (info.error != null) {
            Config.debug("UpdateChecker failed: " + info.error);
            return;
        }
        String latestClean  = info.latestStartsWithV ? info.latest.substring(1) : info.latest;
        String currentClean = info.currentStartsWithV ? info.current.substring(1) : info.current;
        if (!latestClean.equals(currentClean)) {
            // Concise console warning instead of large ASCII banner
            FppLogger.warn("Update available: running v" + currentClean + ", latest v" + latestClean + ". Download: " + MODRINTH_URL);

            Component msg = Component.text("[FPP] ").color(NamedTextColor.BLUE)
                    .append(Component.text("Update available: running v" + currentClean + ", latest v" + latestClean + ". "))
                    .append(Component.text("Download: ").color(NamedTextColor.YELLOW))
                    .append(Component.text(MODRINTH_URL).color(NamedTextColor.AQUA));

            boolean notified = false;
            for (Player p : plugin.getServer().getOnlinePlayers()) {
                if (p.isOp() || p.hasPermission("fakeplayer.notify")) {
                    p.sendMessage(msg);
                    notified = true;
                }
            }
            if (!notified) Config.debug("UpdateChecker: no online ops/admins to notify.");
        } else {
            FppLogger.success("Running the latest version: v" + currentClean + "  ✔");
        }
    }

    /** Holds a parsed update check result. */
    public static final class UpdateInfo {
        public String latest;
        public boolean latestStartsWithV;
        public String current;
        public boolean currentStartsWithV;
        public String error;
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
        Matcher m = VERSION_REGEX.matcher(body);
        if (m.find()) return m.group();
        return null;
    }

    /** Performs the HTTP fetch and returns an UpdateInfo with parsed fields or error. */
    private static UpdateInfo fetchUpdateInfo(Plugin plugin) {
        UpdateInfo info = new UpdateInfo();
        String[] candidates = new String[] {
                API_URL,
                API_URL + "/api/check-update",
                API_URL + "/api/status",
                API_URL + "/latest",
                API_URL + "/api/latest",
        };
        for (String url : candidates) {
            try {
                HttpURLConnection conn = (HttpURLConnection)
                        URI.create(url).toURL().openConnection();
                conn.setRequestMethod("GET");
                conn.setConnectTimeout(5_000);
                conn.setReadTimeout(5_000);
                conn.setRequestProperty("Accept", "application/json");
                conn.setRequestProperty("User-Agent", "FakePlayerPlugin-UpdateChecker/" +
                        plugin.getPluginMeta().getVersion());

                int code = conn.getResponseCode();
                if (code != 200) {
                    Config.debug("UpdateChecker: HTTP " + code + " for " + url + " — trying next.");
                    continue;
                }

                StringBuilder sb = new StringBuilder();
                try (BufferedReader r = new BufferedReader(
                        new InputStreamReader(conn.getInputStream()))) {
                    String line;
                    while ((line = r.readLine()) != null) sb.append(line);
                }

                String body = sb.toString();
                // Try common JSON keys and common remote API shapes (e.g. remoteVersion)
                Pattern kvPattern = Pattern.compile("\"(tag_name|version|latest|name|remoteVersion|remote_version)\"\\s*:\\s*\"([^\"]+)\"", Pattern.CASE_INSENSITIVE);
                Matcher kvm = kvPattern.matcher(body);
                String latest = null;
                if (kvm.find()) {
                    String candidate = kvm.group(2).trim();
                    // Only accept if it matches a version-like token (avoid names like plugin title)
                    if (VERSION_REGEX.matcher(candidate).matches()) {
                        latest = candidate;
                    }
                }

                if (latest == null || latest.isBlank()) {
                    // Fallback to generic extraction (looks for version-like token)
                    latest = extractLatestVersion(body);
                }

                if (latest == null || latest.isBlank()) {
                    Config.debug("UpdateChecker: could not find version in response from " + url + ".");
                    continue; // try next candidate
                }

                String current = plugin.getPluginMeta().getVersion();
                info.latest = latest;
                info.latestStartsWithV = latest.startsWith("v");
                info.current = current;
                info.currentStartsWithV = current.startsWith("v");
                return info;
            } catch (java.net.SocketTimeoutException e) {
                Config.debug("UpdateChecker timed out for " + url + " (no internet / unreachable).");
                // try next candidate
            } catch (Exception e) {
                Config.debug("UpdateChecker failed for " + url + ": " + e.getClass().getSimpleName() + ": " + e.getMessage());
                // try next candidate
            }
        }
        info.error = "no successful response from update API";
        return info;
    }

    // padRight removed — no longer needed after concise messages
}
