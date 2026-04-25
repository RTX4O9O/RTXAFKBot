package me.bill.fakePlayerPlugin.fakeplayer;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
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
import me.bill.fakePlayerPlugin.util.FppLogger;

public final class SkinFetcher {

  private SkinFetcher() {}

  private static final Map<String, String[]> cache = new ConcurrentHashMap<>();

  private static final Map<String, List<BiConsumer<String, String>>> pending =
      new ConcurrentHashMap<>();

  private static final ScheduledExecutorService executor =
      Executors.newSingleThreadScheduledExecutor(
          r -> {
            Thread t = new Thread(r, "FPP-SkinFetcher");
            t.setDaemon(true);
            return t;
          });

  private static final long REQUEST_GAP_MS = 300;
  private static final String USER_AGENT = "FakePlayerPlugin/1.5.0";
  private static final long RATE_LIMIT_COOLDOWN_MS = TimeUnit.MINUTES.toMillis(5);
  private static final long RATE_LIMIT_LOG_INTERVAL_MS = TimeUnit.MINUTES.toMillis(1);
  private static long nextSlotMs = 0;
  private static volatile long mojangRateLimitedUntilMs = 0;
  private static volatile long mineskinRateLimitedUntilMs = 0;
  private static volatile long lastRateLimitLogMs = 0;
  private static final java.util.concurrent.atomic.AtomicInteger suppressedRateLimitLogs =
      new java.util.concurrent.atomic.AtomicInteger();

  private static final class RateLimitException extends RuntimeException {
    RateLimitException(String source) {
      super(source + " rate limited (429)");
    }
  }

  public static synchronized void fetchAsync(
      String playerName, BiConsumer<String, String> callback) {
    String cacheKey = normalizePlayerName(playerName);
    if (cacheKey == null) {
      callback.accept(null, null);
      return;
    }
    String requestName = playerName.trim();

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

    long now = System.currentTimeMillis();
    long delay = Math.max(0, nextSlotMs - now);
    nextSlotMs = Math.max(now, nextSlotMs) + REQUEST_GAP_MS;
    executor.schedule(() -> doFetch(cacheKey, requestName), delay, TimeUnit.MILLISECONDS);
  }

  @SuppressWarnings("unused")
  public static synchronized String[] getCached(String playerName) {
    String cacheKey = normalizePlayerName(playerName);
    return cacheKey != null ? cache.get(cacheKey) : null;
  }

  @SuppressWarnings("unused")
  public static synchronized boolean isCached(String playerName) {
    String cacheKey = normalizePlayerName(playerName);
    return cacheKey != null && cache.containsKey(cacheKey);
  }

  public static synchronized void clearCache() {
    cache.clear();
    FppLogger.debug("SkinFetcher: cache cleared.");
  }

  @SuppressWarnings("unused")
  public static int cacheSize() {
    return cache.size();
  }

  public static void fetchByUrl(String url, BiConsumer<String, String> callback) {
    String normalizedUrl = normalizeUrl(url);
    if (normalizedUrl == null) {
      callback.accept(null, null);
      return;
    }

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

      long now = System.currentTimeMillis();
      long delay = Math.max(0, nextSlotMs - now);
      nextSlotMs = Math.max(now, nextSlotMs) + REQUEST_GAP_MS;
      executor.schedule(() -> doFetchByUrl(cacheKey, normalizedUrl), delay, TimeUnit.MILLISECONDS);
    }
  }

  private static void doFetchByUrl(String cacheKey, String url) {
    String value = null, signature = null;
    try {

      if (url.contains("textures.minecraft.net")) {
        String json = "{\"textures\":{\"SKIN\":{\"url\":\"" + url + "\"}}}";
        value = Base64.getEncoder().encodeToString(json.getBytes(StandardCharsets.UTF_8));

        FppLogger.debug("SkinFetcher: built texture payload from CDN URL.");
      } else {

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
        try {
          cb.accept(value, signature);
        } catch (Exception e) {
          FppLogger.warn("SkinFetcher URL callback error: " + e.getMessage());
        }
      }
    }
  }

  private static void doFetch(String cacheKey, String playerName) {
    String value = null, signature = null;
    boolean shouldCache = true;
    boolean allowFallback = true;

    try {
      long now = System.currentTimeMillis();
      if (now < mojangRateLimitedUntilMs) {
        allowFallback = false;
        ConfigDebugRateLimit("Mojang API", playerName, mojangRateLimitedUntilMs - now);
      } else {
      String uuidResponse = get("https://api.mojang.com/users/profiles/minecraft/" + playerName);
      if (uuidResponse != null) {
        JsonObject uuidJson = parseJsonObject(uuidResponse);
        String uuidStr = getString(uuidJson, "id");
        if (uuidStr != null && !uuidStr.isBlank()) {

          String uuidNoDashes = uuidStr.replace("-", "");
          String profileResponse =
              get(
                  "https://sessionserver.mojang.com/session/minecraft/profile/"
                      + uuidNoDashes
                      + "?unsigned=false");
          if (profileResponse != null) {
            JsonObject profileJson = parseJsonObject(profileResponse);
            String[] tex = extractTexturePayload(profileJson);
            if (tex != null) {
              value = tex[0];
              signature = tex[1];
              FppLogger.debug(
                  "SkinFetcher: fetched skin from Mojang API for '" + playerName + "'.");
            }
          }
        }
      }
      }
    } catch (RateLimitException e) {
      mojangRateLimitedUntilMs = System.currentTimeMillis() + RATE_LIMIT_COOLDOWN_MS;
      allowFallback = false;
      logRateLimited("Mojang API", playerName);
    } catch (Exception e) {
      FppLogger.debug("SkinFetcher: Mojang API error for '" + playerName + "': " + e.getMessage());
    }

    if (value == null && shouldCache && allowFallback) {
      try {
        long now = System.currentTimeMillis();
        if (now < mineskinRateLimitedUntilMs) {
          ConfigDebugRateLimit("mineskin.eu", playerName, mineskinRateLimitedUntilMs - now);
          throw new SkipSkinFetchException();
        }
        String response = get("https://mineskin.eu/profile/" + playerName);
        JsonObject json = parseJsonObject(response);
        String[] tex = extractTexturePayload(json);
        if (tex != null) {
          value = tex[0];
          signature = tex[1];
          FppLogger.debug("SkinFetcher: fetched skin from mineskin.eu for '" + playerName + "'.");
        } else {
          FppLogger.debug(
              "SkinFetcher: no skin found for '" + playerName + "' on mineskin.eu either.");
        }
      } catch (RateLimitException e) {
        mineskinRateLimitedUntilMs = System.currentTimeMillis() + RATE_LIMIT_COOLDOWN_MS;
        logRateLimited("mineskin.eu", playerName);
      } catch (SkipSkinFetchException ignored) {
      } catch (Exception e) {
        FppLogger.warn(
            "SkinFetcher: mineskin.eu error for '" + playerName + "': " + e.getMessage());
      }
    }

    if (shouldCache) {
      cache.put(cacheKey, new String[] {value, signature});
    }

    List<BiConsumer<String, String>> cbs = pending.remove(cacheKey);
    if (cbs != null) {
      for (BiConsumer<String, String> cb : cbs) {
        try {
          cb.accept(value, signature);
        } catch (Exception e) {
          FppLogger.warn("SkinFetcher callback error for '" + playerName + "': " + e.getMessage());
        }
      }
    }
  }

  private static final class SkipSkinFetchException extends RuntimeException {}

  private static void ConfigDebugRateLimit(String source, String playerName, long remainingMs) {
    suppressedRateLimitLogs.incrementAndGet();
    FppLogger.debug(
        "SkinFetcher: skipping "
            + source
            + " lookup for '"
            + playerName
            + "' during rate-limit cooldown ("
            + Math.max(1, TimeUnit.MILLISECONDS.toSeconds(remainingMs))
            + "s left).");
  }

  private static void logRateLimited(String source, String playerName) {
    long now = System.currentTimeMillis();
    int suppressed = suppressedRateLimitLogs.getAndSet(0);
    if (now - lastRateLimitLogMs < RATE_LIMIT_LOG_INTERVAL_MS) {
      suppressedRateLimitLogs.incrementAndGet();
      return;
    }
    lastRateLimitLogMs = now;
    FppLogger.warn(
        "SkinFetcher: "
            + source
            + " rate-limited while fetching '"
            + playerName
            + "'. Suppressing skin lookups for "
            + TimeUnit.MILLISECONDS.toMinutes(RATE_LIMIT_COOLDOWN_MS)
            + " minutes."
            + (suppressed > 0 ? " Suppressed " + suppressed + " similar message(s)." : ""));
  }

  private static String get(String urlStr) throws Exception {
    HttpURLConnection conn = (HttpURLConnection) URI.create(urlStr).toURL().openConnection();
    conn.setRequestMethod("GET");
    conn.setConnectTimeout(5_000);
    conn.setReadTimeout(5_000);
    conn.setRequestProperty("User-Agent", USER_AGENT);
    int code = conn.getResponseCode();
    if (code == 429) {
      conn.disconnect();

      String host;
      try {
        host = URI.create(urlStr).getHost();
      } catch (Exception ignored) {
        host = urlStr;
      }
      throw new RateLimitException(host);
    }
    if (code == 204 || code == 404) return null;
    try (BufferedReader br =
        new BufferedReader(new InputStreamReader(conn.getInputStream(), StandardCharsets.UTF_8))) {
      StringBuilder sb = new StringBuilder();
      String line;
      while ((line = br.readLine()) != null) sb.append(line);
      return sb.toString();
    } finally {
      conn.disconnect();
    }
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
      return new String[] {directValue, getString(json, "signature")};
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
          return new String[] {value, getString(property, "signature")};
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
