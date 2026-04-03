package me.bill.fakePlayerPlugin.fakeplayer;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import me.bill.fakePlayerPlugin.util.FppLogger;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.net.HttpURLConnection;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.util.Base64;
import java.util.List;
import java.util.Map;
import java.util.concurrent.*;
import java.util.function.BiConsumer;

/**
 * Fetches skin textures for a given player name or URL asynchronously via
 * <b>mineskin.eu</b> ({@code https://mineskin.eu/profile/<name>}).
 *
 * <p>A single HTTP call returns a signed Mojang-compatible texture payload —
 * no UUID lookup, no Mojang session-server call.
 *
 * <h3>Capabilities</h3>
 * <ul>
 *   <li>{@link #fetchAsync(String, BiConsumer)} — resolve skin by Minecraft player name.</li>
 *   <li>{@link #fetchByUrl(String, BiConsumer)} — build a skin profile from a raw
 *       texture URL (e.g. {@code https://textures.minecraft.net/texture/…}).</li>
 * </ul>
 *
 * <h3>Reliability features</h3>
 * <ul>
 *   <li><b>Per-name cache</b> — fetched once per session; subsequent calls return
 *       instantly from cache.</li>
 *   <li><b>Callback deduplication</b> — simultaneous requests for the same name
 *       share one HTTP call.</li>
 *   <li><b>Rate-limited queue</b> — 300 ms between requests.</li>
 * </ul>
 */
public final class SkinFetcher {

    private SkinFetcher() {}

    // ── Cache & queue ─────────────────────────────────────────────────────────

    /** name → [value, signature] (null values = no skin / not found). */
    private static final Map<String, String[]> cache = new ConcurrentHashMap<>();

    /** name → callbacks waiting for the in-flight fetch. */
    private static final Map<String, List<BiConsumer<String, String>>> pending =
            new ConcurrentHashMap<>();

    private static final ScheduledExecutorService executor =
            Executors.newSingleThreadScheduledExecutor(r -> {
                Thread t = new Thread(r, "FPP-SkinFetcher");
                t.setDaemon(true);
                return t;
            });

    /** Gap between consecutive skin API requests (ms). */
    private static final long REQUEST_GAP_MS = 300;
    private static final String USER_AGENT = "FakePlayerPlugin/1.5.0";
    private static long nextSlotMs = 0;

    /** Thrown internally when the skin API returns HTTP 429 (rate limited). Never cached. */
    private static final class RateLimitException extends RuntimeException {
        RateLimitException(String source) { super(source + " rate limited (429)"); }
    }

    // ── Public API ────────────────────────────────────────────────────────────

    /**
     * Fetches skin data for {@code playerName} via mineskin.eu and calls
     * {@code callback(value, signature)} on the FPP-SkinFetcher thread.
     * Both args are {@code null} if the name has no skin (not a real account).
     * Safe to call from any thread.
     */
    public static synchronized void fetchAsync(String playerName,
                                               BiConsumer<String, String> callback) {
        String cacheKey = normalizePlayerName(playerName);
        if (cacheKey == null) {
            callback.accept(null, null);
            return;
        }
        String requestName = playerName.trim();

        // 1. Cached — fire immediately
        if (cache.containsKey(cacheKey)) {
            String[] r = cache.get(cacheKey);
            callback.accept(r[0], r[1]);
            return;
        }
        // 2. Already in-flight — queue callback
        if (pending.containsKey(cacheKey)) {
            pending.get(cacheKey).add(callback);
            return;
        }
        // 3. New fetch
        List<BiConsumer<String, String>> cbs = new CopyOnWriteArrayList<>();
        cbs.add(callback);
        pending.put(cacheKey, cbs);

        long now   = System.currentTimeMillis();
        long delay = Math.max(0, nextSlotMs - now);
        nextSlotMs = Math.max(now, nextSlotMs) + REQUEST_GAP_MS;
        executor.schedule(() -> doFetch(cacheKey, requestName), delay, TimeUnit.MILLISECONDS);
    }

    /** Returns cached skin data immediately, or {@code null} if not yet cached. */
    @SuppressWarnings("unused")
    public static synchronized String[] getCached(String playerName) {
        String cacheKey = normalizePlayerName(playerName);
        return cacheKey != null ? cache.get(cacheKey) : null;
    }

    /** Returns {@code true} if this name has already been resolved. */
    @SuppressWarnings("unused")
    public static synchronized boolean isCached(String playerName) {
        String cacheKey = normalizePlayerName(playerName);
        return cacheKey != null && cache.containsKey(cacheKey);
    }

    /**
     * Clears the entire skin cache — call on /fpp reload so bots
     * get fresh skins after a name-pool change.
     */
    public static synchronized void clearCache() {
        cache.clear();
        FppLogger.debug("SkinFetcher: cache cleared.");
    }

    /** Returns the number of names currently in the cache. */
    @SuppressWarnings("unused")
    public static int cacheSize() { return cache.size(); }

    // ── URL-based fetching ────────────────────────────────────────────────────

    /**
     * Builds a skin profile from a raw Mojang CDN texture URL such as
     * {@code https://textures.minecraft.net/texture/<hash>}.
     *
     * <p>Constructs the base64 texture-payload JSON locally — no extra HTTP
     * call needed. The resulting profile has no RSA signature ({@code null})
     * which is fine for display purposes on Paper servers.
     *
     * <p>If the URL is a full player profile URL that returns JSON containing
     * a {@code "value"} field, that field is used directly (signed).
     *
     * @param url      texture URL
     * @param callback receives (value, signature) — both non-null on success
     */
    public static void fetchByUrl(String url, BiConsumer<String, String> callback) {
        String normalizedUrl = normalizeUrl(url);
        if (normalizedUrl == null) {
            callback.accept(null, null);
            return;
        }

        // Cache key based on URL
        String cacheKey = "url:" + normalizedUrl;
        synchronized (SkinFetcher.class) {
            if (cache.containsKey(cacheKey)) {
                String[] r = cache.get(cacheKey);
                callback.accept(r[0], r[1]);
                return;
            }
            if (pending.containsKey(cacheKey)) {
                pending.get(cacheKey).add(callback);
                return;
            }
            List<BiConsumer<String, String>> cbs = new CopyOnWriteArrayList<>();
            cbs.add(callback);
            pending.put(cacheKey, cbs);

            long now   = System.currentTimeMillis();
            long delay = Math.max(0, nextSlotMs - now);
            nextSlotMs = Math.max(now, nextSlotMs) + REQUEST_GAP_MS;
            executor.schedule(() -> doFetchByUrl(cacheKey, normalizedUrl), delay, TimeUnit.MILLISECONDS);
        }
    }

    private static void doFetchByUrl(String cacheKey, String url) {
        String value = null, signature = null;
        try {
            // Strategy 1: if it's a textures.minecraft.net URL, wrap it in JSON directly
            if (url.contains("textures.minecraft.net")) {
                String json = "{\"textures\":{\"SKIN\":{\"url\":\"" + url + "\"}}}";
                value = Base64.getEncoder().encodeToString(json.getBytes(StandardCharsets.UTF_8));
                // No signature available without Mojang signing it
                FppLogger.debug("SkinFetcher: built texture payload from CDN URL.");
            } else {
                // Strategy 2: try fetching it as a JSON endpoint (Mineskin API or similar)
                String response = get(url);
                JsonObject json = parseJsonObject(response);
                String[] texture = extractTexturePayload(json);
                if (texture != null) {
                    value = texture[0];
                    signature = texture[1];
                    FppLogger.debug("SkinFetcher: extracted value+sig from URL response.");
                }
            }
        } catch (Exception e) {
            FppLogger.warn("SkinFetcher URL error for '" + url + "': " + e.getMessage());
        }

        String[] result = {value, signature};
        cache.put(cacheKey, result);

        List<BiConsumer<String, String>> cbs = pending.remove(cacheKey);
        if (cbs != null) {
            for (BiConsumer<String, String> cb : cbs) {
                try { cb.accept(value, signature); }
                catch (Exception e) {
                    FppLogger.warn("SkinFetcher URL callback error: " + e.getMessage());
                }
            }
        }
    }

    // ── Internal fetch ────────────────────────────────────────────────────────

    private static void doFetch(String cacheKey, String playerName) {
        String value = null, signature = null;
        boolean shouldCache = true;
        try {
            String response = get("https://mineskin.eu/profile/" + playerName);
            JsonObject json = parseJsonObject(response);
            String[] tex = extractTexturePayload(json);
            if (tex != null) {
                value = tex[0];
                signature = tex[1];
                FppLogger.debug("SkinFetcher: fetched skin from mineskin.eu for '" + playerName + "'.");
            } else {
                FppLogger.debug("SkinFetcher: no skin found for '" + playerName + "' on mineskin.eu.");
            }
        } catch (RateLimitException e) {
            shouldCache = false;
            FppLogger.warn("SkinFetcher: mineskin.eu rate-limited for '" + playerName + "'. Will retry on next request.");
        } catch (Exception e) {
            FppLogger.warn("SkinFetcher: mineskin.eu error for '" + playerName + "': " + e.getMessage());
        }

        if (shouldCache) {
            cache.put(cacheKey, new String[]{value, signature});
        }

        List<BiConsumer<String, String>> cbs = pending.remove(cacheKey);
        if (cbs != null) {
            for (BiConsumer<String, String> cb : cbs) {
                try { cb.accept(value, signature); }
                catch (Exception e) {
                    FppLogger.warn("SkinFetcher callback error for '" + playerName + "': " + e.getMessage());
                }
            }
        }
    }

    // ── HTTP helpers ──────────────────────────────────────────────────────────

    private static String get(String urlStr) throws Exception {
        HttpURLConnection conn =
                (HttpURLConnection) URI.create(urlStr).toURL().openConnection();
        conn.setRequestMethod("GET");
        conn.setConnectTimeout(5_000);
        conn.setReadTimeout(5_000);
        conn.setRequestProperty("User-Agent", USER_AGENT);
        int code = conn.getResponseCode();
        if (code == 429) {
            conn.disconnect();
            // Extract hostname to identify which API was rate-limited in the log
            String host;
            try { host = URI.create(urlStr).getHost(); } catch (Exception ignored) { host = urlStr; }
            throw new RateLimitException(host); // caller must NOT cache this result
        }
        if (code == 204 || code == 404) return null;
        try (BufferedReader br = new BufferedReader(
                new InputStreamReader(conn.getInputStream(), StandardCharsets.UTF_8))) {
            StringBuilder sb = new StringBuilder();
            String line;
            while ((line = br.readLine()) != null) sb.append(line);
            return sb.toString();
        } finally { conn.disconnect(); }
    }

    private static String normalizePlayerName(String playerName) {
        if (playerName == null) return null;
        String trimmed = playerName.trim();
        return trimmed.isEmpty() ? null : trimmed.toLowerCase(java.util.Locale.ROOT);
    }

    private static String normalizeUrl(String url) {
        if (url == null) return null;
        String trimmed = url.trim();
        return trimmed.isEmpty() ? null : trimmed;
    }

    private static JsonObject parseJsonObject(String json) {
        if (json == null || json.isBlank()) return null;
        try {
            JsonElement element = JsonParser.parseString(json);
            return element != null && element.isJsonObject() ? element.getAsJsonObject() : null;
        } catch (Exception e) {
            FppLogger.debug("SkinFetcher JSON parse failed: " + e.getMessage());
            return null;
        }
    }

    private static String[] extractTexturePayload(JsonObject json) {
        if (json == null) return null;

        String directValue = getString(json, "value");
        if (directValue != null && !directValue.isBlank()) {
            return new String[]{directValue, getString(json, "signature")};
        }

        JsonArray properties = getArray(json, "properties");
        if (properties != null) {
            for (JsonElement element : properties) {
                if (!element.isJsonObject()) continue;
                JsonObject property = element.getAsJsonObject();
                String propertyName = getString(property, "name");
                if (propertyName != null && !"textures".equalsIgnoreCase(propertyName)) continue;

                String value = getString(property, "value");
                if (value != null && !value.isBlank()) {
                    return new String[]{value, getString(property, "signature")};
                }
            }
        }

        JsonObject data = getObject(json, "data");
        if (data != null) {
            String[] nested = extractTexturePayload(data);
            if (nested != null) return nested;
        }

        JsonObject texture = getObject(json, "texture");
        if (texture != null) {
            String[] nested = extractTexturePayload(texture);
            if (nested != null) return nested;
        }

        return null;
    }

    private static JsonArray getArray(JsonObject json, String key) {
        JsonElement element = json.get(key);
        return element != null && element.isJsonArray() ? element.getAsJsonArray() : null;
    }

    private static JsonObject getObject(JsonObject json, String key) {
        JsonElement element = json.get(key);
        return element != null && element.isJsonObject() ? element.getAsJsonObject() : null;
    }

    private static String getString(JsonObject json, String key) {
        if (json == null) return null;
        JsonElement element = json.get(key);
        return element != null && !element.isJsonNull() ? element.getAsString() : null;
    }
}
