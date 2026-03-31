package me.bill.fakePlayerPlugin.fakeplayer;

import me.bill.fakePlayerPlugin.config.Config;
import me.bill.fakePlayerPlugin.util.FppLogger;
import org.bukkit.Bukkit;
import org.bukkit.plugin.Plugin;

import java.io.File;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.ThreadLocalRandom;
import java.util.function.Consumer;

/**
 * Central skin registry for the Fake Player Plugin.
 *
 * <h3>Resolution Priority (when {@code skin.mode = custom})</h3>
 * <ol>
 *   <li><b>Exact-name override</b> — if the bot name matches an entry in
 *       {@code skin.custom.by-name}, that mapping's skin is used.</li>
 *   <li><b>Skin folder</b> — PNG files in
 *       {@code plugins/FakePlayerPlugin/skins/}.  A random file is chosen
 *       unless the file is named {@code <botname>.png}, in which case it is
 *       used exclusively for that bot.</li>
 *   <li><b>Config URL pool</b> — skins defined as Minecraft player names or
 *       direct Mineskin/Mojang texture URLs in {@code skin.custom.pool}.</li>
 *   <li><b>Mojang API fallback</b> — if nothing else matched, resolve by
 *       the bot's actual Minecraft name (same as {@code auto} mode).</li>
 * </ol>
 *
 * <h3>Skin modes</h3>
 * <dl>
 *   <dt>{@code auto}</dt>
 *   <dd>Fetches the signed Mojang texture payload for the bot's skin name so the
 *       same skin data can be applied to the local NMS body, tab-list packets,
 *       and proxy sync payloads.</dd>
 *   <dt>{@code off}</dt>
 *   <dd>No skin — bots always display the default Steve / Alex appearance.</dd>
 *   <dt>{@code custom}</dt>
 *   <dd>Runs the full resolution pipeline described above. Works on both
 *       online-mode and offline-mode servers.</dd>
 * </dl>
 */
public final class SkinRepository {

    // ── Singleton ─────────────────────────────────────────────────────────────

    private static SkinRepository INSTANCE;

    public static synchronized SkinRepository get() {
        if (INSTANCE == null) INSTANCE = new SkinRepository();
        return INSTANCE;
    }

    private SkinRepository() {}

    // ── State ─────────────────────────────────────────────────────────────────

    /** Resolved skins from the skins folder (PNG files). */
    private final List<SkinProfile> folderSkins = new CopyOnWriteArrayList<>();

    /** {@code <botname>.lower → SkinProfile} exact-name overrides. */
    private final Map<String, SkinProfile> namedOverrides = new ConcurrentHashMap<>();

    /** Resolved skins from the config pool (player names + URLs). */
    private final List<SkinProfile> poolSkins = new CopyOnWriteArrayList<>();

    /** Resolved fallback pool skins — loaded from {@code skin.fallback-pool} for auto mode diversity. */
    private final List<SkinProfile> fallbackPoolSkins = new CopyOnWriteArrayList<>();

     /** Session cache: {@code mode:bot-name} → resolved profile (cleared on reload). */
    private final Map<String, SkinProfile> sessionCache = new ConcurrentHashMap<>();

    /**
     * Pre-loaded fallback skin — fetched eagerly at startup from {@code skin.fallback-name}.
     * Used as the last resort when all other resolution fails and {@code skin.guaranteed-skin} is true.
     * Volatile so the async fetch callback is visible to the main thread immediately.
     */
    private volatile SkinProfile fallbackSkin = null;

    private Plugin plugin;

    // ── Lifecycle ─────────────────────────────────────────────────────────────

    /**
     * Initialises / reloads the skin repository.
     * Called on startup and on {@code /fpp reload}.
     */
    public synchronized void init(Plugin plugin) {
        this.plugin = plugin;
        reload();
    }

    /**
     * Clears all caches and rescans all skin sources.
     * Must be called from the main thread (file I/O happens synchronously
     * for folder scans; Mojang fetches remain async).
     */
    public synchronized void reload() {
        folderSkins.clear();
        namedOverrides.clear();
        poolSkins.clear();
        fallbackPoolSkins.clear();
        sessionCache.clear();
        fallbackSkin = null;

        if (Config.skinClearCacheOnReload()) {
            SkinFetcher.clearCache();
        }

        String mode = Config.skinMode();

        if ("off".equals(mode) || "disabled".equals(mode)) {
            FppLogger.debug("SkinRepository: mode=off — repository not loaded.");
            return;
        }

        if ("auto".equals(mode)) {
            // In auto mode, Mojang resolves skins by name automatically.
            // Load fallback pool for diversity when bot names don't exist on Mojang
            // (generated names, user bots, etc.), then prewarm the single fallback-name
            // as the absolute last resort.
            if (Config.skinGuaranteed()) {
                loadFallbackPool();
                loadFallbackSkin();
                FppLogger.debug("SkinRepository: mode=auto + guaranteed-skin=true — fallback pool + fallback skin queued for prewarm.");
            } else {
                FppLogger.debug("SkinRepository: mode=auto — repository not loaded.");
            }
            return;
        }

        // custom mode — load everything
        if (Config.skinUseSkinFolder()) {
            loadFolderSkins();
        }
        loadConfigPool();
        if (Config.skinGuaranteed()) {
            loadFallbackSkin();
        }
        FppLogger.debug("SkinRepository: loaded " + folderSkins.size() + " folder skin(s), "
                + poolSkins.size() + " pool skin(s), "
                + namedOverrides.size() + " named override(s).");
    }

    // ── Public resolution API ─────────────────────────────────────────────────

    /**
     * Resolves a skin for {@code botName} and delivers it to {@code callback}
     * asynchronously. The callback is always called on the <b>main thread</b>.
     *
     * <p>{@code off} returns {@code null}. {@code auto} and {@code custom}
     * both resolve a {@link SkinProfile} when possible, falling back to the
     * guaranteed-skin chain when configured.
     *
     * @param botName  the internal Minecraft name of the bot
     * @param callback receives a {@link SkinProfile}, or {@code null}
     */
    public void resolve(String botName, java.util.function.Consumer<@org.jetbrains.annotations.Nullable SkinProfile> callback) {
        if (botName == null || botName.isBlank()) {
            deliver(callback, null);
            return;
        }

        String normalizedBotName = botName.trim();
        String mode = Config.skinMode();

        if ("off".equals(mode) || "disabled".equals(mode)) {
            deliver(callback, null);
            return;
        }

        String cacheKey = buildCacheKey(mode, normalizedBotName);
        SkinProfile cached = sessionCache.get(cacheKey);
        if (cached != null) {
            deliver(callback, cached);
            return;
        }

        Consumer<SkinProfile> finish = profile -> {
            if (profile != null && profile.isValid()) {
                sessionCache.put(cacheKey, profile);
            }
            deliver(callback, profile);
        };

        if ("auto".equals(mode)) {
            resolveAuto(normalizedBotName, finish);
            return;
        }

        resolveCustom(normalizedBotName, finish);
    }

    // ── Private resolution pipeline ───────────────────────────────────────────

    private void resolveAuto(String botName, Consumer<SkinProfile> callback) {
        FppLogger.debug("SkinRepository: auto-resolving signed skin for '" + botName + "'.");
        SkinFetcher.fetchAsync(botName, (value, sig) -> {
            if (value != null && !value.isBlank()) {
                callback.accept(new SkinProfile(value, sig, "auto:" + botName));
            } else if (Config.skinGuaranteed()) {
                FppLogger.debug("SkinRepository: auto lookup failed for '" + botName + "' — using guaranteed skin.");
                getAnyValidSkin(callback);
            } else {
                callback.accept(null);
            }
        });
    }

    private void resolveCustom(String botName, Consumer<SkinProfile> callback) {
        // 1. Exact-name override from config
        SkinProfile override = namedOverrides.get(botName.toLowerCase());
        if (override != null && override.isValid()) {
            FppLogger.debug("SkinRepository: name-override hit for '" + botName + "' → " + override.getSource());
            callback.accept(override);
            return;
        }

        // 2. File named exactly <botname>.png in the skins folder
        SkinProfile fileExact = findExactFileMatch(botName);
        if (fileExact != null && fileExact.isValid()) {
            FppLogger.debug("SkinRepository: exact-file match for '" + botName + "' → " + fileExact.getSource());
            callback.accept(fileExact);
            return;
        }

        // 3. Random from folder pool
        if (!folderSkins.isEmpty()) {
            SkinProfile random = folderSkins.get(ThreadLocalRandom.current().nextInt(folderSkins.size()));
            FppLogger.debug("SkinRepository: random folder skin for '" + botName + "' → " + random.getSource());
            callback.accept(random);
            return;
        }

        // 4. Random from config pool
        if (!poolSkins.isEmpty()) {
            SkinProfile random = poolSkins.get(ThreadLocalRandom.current().nextInt(poolSkins.size()));
            FppLogger.debug("SkinRepository: random pool skin for '" + botName + "' → " + random.getSource());
            callback.accept(random);
            return;
        }

        // 5. Mojang API fallback — fetch by bot name
        FppLogger.debug("SkinRepository: falling back to Mojang fetch for '" + botName + "'.");
        SkinFetcher.fetchAsync(botName, (value, sig) -> {
            if (value != null) {
                SkinProfile p = new SkinProfile(value, sig, "name:" + botName);
                callback.accept(p);
            } else if (Config.skinGuaranteed()) {
                // Mojang also returned null — use guaranteed fallback
                FppLogger.debug("SkinRepository: Mojang fallback failed for '" + botName + "' — using guaranteed skin.");
                getAnyValidSkin(callback);
            } else {
                callback.accept(null);
            }
        });
    }

    // ── Folder skin loader ────────────────────────────────────────────────────

    /**
     * Scans {@code plugins/FakePlayerPlugin/skins/} for PNG files and
     * pre-encodes them as base64 texture blobs ready for injection into
     * a {@code ResolvableProfile}.
     *
     * <p>The PNG is wrapped in a Minecraft texture JSON payload:
     * <pre>
     * {"textures":{"SKIN":{"url":"<url>"}}}
     * </pre>
     * Because we can't upload the PNG to Mojang's CDN at runtime, we use
     * the Mineskin API endpoint if network is available, otherwise we
     * fall back to a plain base64 data-URI that some clients will render
     * (works on most modern Paper builds with PaperMC's resource-pack proxy
     * feature disabled — standard behaviour for offline skins).
     */
    private void loadFolderSkins() {
        if (plugin == null) return;
        File skinsDir = new File(plugin.getDataFolder(), "skins");
        if (!skinsDir.exists()) {
            boolean created = skinsDir.mkdirs();
            FppLogger.debug("SkinRepository: " + (created ? "created" : "could not create")
                    + " skins folder at " + skinsDir.getPath());
            return;
        }

        File[] pngFiles = skinsDir.listFiles((dir, name) ->
                name.toLowerCase().endsWith(".png") || name.toLowerCase().endsWith(".skin"));
        if (pngFiles == null || pngFiles.length == 0) {
            FppLogger.debug("SkinRepository: skins folder is empty.");
            return;
        }

        for (File file : pngFiles) {
            try {
                byte[] bytes = Files.readAllBytes(file.toPath());
                String base64 = Base64.getEncoder().encodeToString(bytes);

                // Build a minimal Minecraft texture JSON using a data URI
                // Note: Minecraft clients on 1.20+ accept base64 data URIs in the texture URL field
                String textureUrl = "data:image/png;base64," + base64;
                String textureJson = buildTextureJson(textureUrl);
                String textureValue = Base64.getEncoder().encodeToString(textureJson.getBytes(StandardCharsets.UTF_8));

                String displayName = file.getName().replaceAll("\\.(png|skin)$", "");
                SkinProfile profile = new SkinProfile(textureValue, null, "file:" + file.getName());

                folderSkins.add(profile);

                // Also register as named override if name matches a potential bot name
                namedOverrides.put(displayName.toLowerCase(), profile);

                FppLogger.debug("SkinRepository: loaded skin file '" + file.getName() + "'.");
            } catch (Exception e) {
                FppLogger.warn("SkinRepository: failed to load '" + file.getName() + "': " + e.getMessage());
            }
        }
    }

    /** Builds the Minecraft texture payload JSON. */
    private static String buildTextureJson(String skinUrl) {
        return "{\"textures\":{\"SKIN\":{\"url\":\"" + skinUrl + "\"}}}";
    }

    /** Returns the folder-loaded skin whose source file name matches {@code <botName>.png}. */
    private SkinProfile findExactFileMatch(String botName) {
        String target = "file:" + botName.toLowerCase() + ".png";
        String target2 = "file:" + botName.toLowerCase() + ".skin";
        for (SkinProfile p : folderSkins) {
            String src = p.getSource().toLowerCase();
            if (src.equals(target) || src.equals(target2)) return p;
        }
        return null;
    }

    // ── Config pool loader ────────────────────────────────────────────────────

    /**
     * Loads skins from {@code skin.pool} (Minecraft player names) and
     * {@code skin.overrides} (name→player-name mappings).
     *
     * <p>Each entry is fetched asynchronously via {@link SkinFetcher} and
     * stored in the pool / overrides maps when it resolves.
     */
    private void loadConfigPool() {
        // Load pool entries (player names to fetch skins from)
        List<String> pool = Config.skinCustomPool();
        for (String entry : pool) {
            if (entry == null || entry.isBlank()) continue;
            entry = entry.trim();

            if (isUrl(entry)) {
                // Direct URL — try to extract value from a Mojang texture URL
                loadFromUrl(entry);
            } else {
                // Minecraft player name — fetch via Mojang API
                loadFromName(entry, null);
            }
        }

        // Load by-name overrides (botName → skinPlayerName)
        Map<String, String> byName = Config.skinCustomByName();
        for (Map.Entry<String, String> e : byName.entrySet()) {
            String botName  = e.getKey().toLowerCase().trim();
            String skinSrc  = e.getValue() != null ? e.getValue().trim() : "";
            if (skinSrc.isEmpty()) continue;

            if (isUrl(skinSrc)) {
                loadFromUrlForName(botName, skinSrc);
            } else {
                loadFromName(skinSrc, botName);
            }
        }
    }

    /** Returns {@code true} if {@code s} looks like an HTTP(S) URL. */
    private static boolean isUrl(String s) {
        return s.startsWith("http://") || s.startsWith("https://");
    }

    /**
     * Fetches a skin by Minecraft player name (via Mojang API) and adds the
     * result to either the named overrides map (if {@code forBotName} is set)
     * or the general pool.
     */
    private void loadFromName(String playerName, String forBotName) {
        SkinFetcher.fetchAsync(playerName, (value, sig) -> {
            if (value == null) {
                FppLogger.debug("SkinRepository: no Mojang skin for pool entry '" + playerName + "'.");
                return;
            }
            SkinProfile p = new SkinProfile(value, sig, "name:" + playerName);
            if (forBotName != null) {
                namedOverrides.put(forBotName, p);
                FppLogger.debug("SkinRepository: loaded name-override '" + forBotName + "' → " + playerName + ".");
            } else {
                poolSkins.add(p);
                FppLogger.debug("SkinRepository: added pool skin from '" + playerName + "'.");
            }
        });
    }

    /**
     * Fetches a skin from a texture URL (Mojang CDN or Mineskin).
     * Adds to the general pool.
     */
    private void loadFromUrl(String url) {
        SkinFetcher.fetchByUrl(url, (value, sig) -> {
            if (value == null) {
                FppLogger.debug("SkinRepository: no skin from URL '" + url + "'.");
                return;
            }
            SkinProfile p = new SkinProfile(value, sig, "url:" + url);
            poolSkins.add(p);
            FppLogger.debug("SkinRepository: added pool skin from URL.");
        });
    }

    /**
     * Fetches a skin from a texture URL and registers it as a named override.
     */
    private void loadFromUrlForName(String botName, String url) {
        SkinFetcher.fetchByUrl(url, (value, sig) -> {
            if (value == null) {
                FppLogger.debug("SkinRepository: no skin from URL for '" + botName + "'.");
                return;
            }
            SkinProfile p = new SkinProfile(value, sig, "url:" + url);
            namedOverrides.put(botName, p);
            FppLogger.debug("SkinRepository: loaded URL override for '" + botName + "'.");
        });
    }

    // ── Fallback skin (guaranteed-skin safety net) ────────────────────────────

    /**
     * Loads the {@code skin.fallback-pool} — a list of real Minecraft usernames
     * whose skins are fetched and randomly used when a bot's name has no Mojang account.
     * Provides skin diversity in auto mode instead of all unknown-name bots using
     * the same fallback-name skin. Called from {@link #reload()} when
     * {@code skin.guaranteed-skin} is {@code true} and mode is {@code auto}.
     */
    private void loadFallbackPool() {
        List<String> names = Config.skinFallbackPool();
        if (names.isEmpty()) {
            FppLogger.debug("SkinRepository: fallback-pool is empty — will use single fallback-name.");
            return;
        }
        
        FppLogger.debug("SkinRepository: loading " + names.size() + " fallback-pool skins...");
        for (String name : names) {
            if (name == null || name.isBlank()) continue;
            SkinFetcher.fetchAsync(name, (value, sig) -> {
                if (value != null && !value.isBlank()) {
                    SkinProfile p = new SkinProfile(value, sig, "fallback-pool:" + name);
                    fallbackPoolSkins.add(p);
                    FppLogger.debug("SkinRepository: fallback-pool skin '" + name + "' loaded (" 
                            + fallbackPoolSkins.size() + "/" + names.size() + ").");
                } else {
                    FppLogger.debug("SkinRepository: fallback-pool skin '" + name + "' not found on Mojang.");
                }
            });
        }
    }

    /**
     * Eagerly fetches the configured {@code skin.fallback-name} and stores it in
     * {@link #fallbackSkin} for instant use. Called from {@link #reload()} when
     * {@code skin.guaranteed-skin} is {@code true}. The fetch is async — the
     * fallback is available a few hundred ms after startup.
     */
    private void loadFallbackSkin() {
        String name = Config.skinFallbackName().trim();
        if (name.isBlank()) return;
        SkinFetcher.fetchAsync(name, (value, sig) -> {
            if (value != null && !value.isBlank()) {
                fallbackSkin = new SkinProfile(value, sig, "fallback:" + name);
                FppLogger.debug("SkinRepository: fallback skin '" + name + "' pre-loaded.");
            } else {
                FppLogger.warn("SkinRepository: fallback skin '" + name
                        + "' failed to fetch — bots using generated names may appear as Steve.");
            }
        });
    }

    /**
     * Returns any valid skin available from the loaded repository.
     * Resolution priority:
     * <ol>
     *   <li>Folder skins (loaded synchronously at startup)</li>
     *   <li>Pool skins (loaded asynchronously — may be ready by now)</li>
     *   <li>Fallback pool skins ({@code skin.fallback-pool} — pre-loaded, auto mode diversity)</li>
     *   <li>On-demand random pick from {@code skin.fallback-pool} config (if pool not ready yet)</li>
     *   <li>Pre-loaded fallback skin ({@code skin.fallback-name})</li>
     *   <li>On-demand fetch of {@code skin.fallback-name} (last resort)</li>
     * </ol>
     *
     * <p>Used as the final safety net when all other resolution fails and
     * {@code skin.guaranteed-skin} is {@code true}. Callback receives {@code null}
     * only if truly nothing is configured and the on-demand fetch also fails.
     *
     * @param callback receives a valid {@link SkinProfile}, or {@code null} if no skin is available
     */
    public void getAnyValidSkin(Consumer<SkinProfile> callback) {
        // 1. Folder skins — loaded synchronously, always ready instantly
        if (!folderSkins.isEmpty()) {
            SkinProfile p = folderSkins.get(ThreadLocalRandom.current().nextInt(folderSkins.size()));
            FppLogger.debug("SkinRepository: guaranteed-skin → folder skin (" + p.getSource() + ").");
            callback.accept(p);
            return;
        }
        // 2. Pool skins — loaded asynchronously; may be populated by the time bots spawn
        if (!poolSkins.isEmpty()) {
            SkinProfile p = poolSkins.get(ThreadLocalRandom.current().nextInt(poolSkins.size()));
            FppLogger.debug("SkinRepository: guaranteed-skin → pool skin (" + p.getSource() + ").");
            callback.accept(p);
            return;
        }
        // 3. Fallback pool skins — diverse auto mode fallback (pre-loaded asynchronously)
        if (!fallbackPoolSkins.isEmpty()) {
            SkinProfile p = fallbackPoolSkins.get(ThreadLocalRandom.current().nextInt(fallbackPoolSkins.size()));
            FppLogger.debug("SkinRepository: guaranteed-skin → fallback-pool skin (" + p.getSource() + ").");
            callback.accept(p);
            return;
        }
        // 4. On-demand random pick from fallback-pool config (fallback for when prewarm incomplete)
        //    This ensures diversity even if bots spawn before async preloads finish
        List<String> poolConfig = Config.skinFallbackPool();
        if (poolConfig != null && !poolConfig.isEmpty()) {
            String randomName = poolConfig.get(ThreadLocalRandom.current().nextInt(poolConfig.size()));
            if (randomName != null && !randomName.isBlank()) {
                FppLogger.debug("SkinRepository: guaranteed-skin → on-demand random fallback-pool fetch for '" + randomName + "'.");
                SkinFetcher.fetchAsync(randomName, (value, sig) -> {
                    if (value != null && !value.isBlank()) {
                        SkinProfile p = new SkinProfile(value, sig, "fallback-pool-ondemand:" + randomName);
                        // Cache it for next time
                        fallbackPoolSkins.add(p);
                        FppLogger.debug("SkinRepository: on-demand fallback-pool skin '" + randomName + "' fetched and cached.");
                        callback.accept(p);
                    } else {
                        // This specific name failed — fall through to single fallback-name
                        FppLogger.debug("SkinRepository: on-demand fallback-pool fetch failed for '" + randomName + "' — trying fallback-name.");
                        fetchSingleFallback(callback);
                    }
                });
                return;
            }
        }
        // 5. Pre-loaded fallback skin (already cached from startup prewarm)
        if (fallbackSkin != null && fallbackSkin.isValid()) {
            FppLogger.debug("SkinRepository: guaranteed-skin → pre-loaded fallback (" + fallbackSkin.getSource() + ").");
            callback.accept(fallbackSkin);
            return;
        }
        // 6. On-demand fetch of fallback name (last resort — prewarm may not have completed)
        fetchSingleFallback(callback);
    }

    /**
     * Helper method to fetch the single {@code skin.fallback-name} as the absolute last resort.
     * Extracted to avoid duplication in the fallback chain.
     */
    private void fetchSingleFallback(Consumer<SkinProfile> callback) {
        String name = Config.skinFallbackName().trim();
        if (!name.isBlank()) {
            FppLogger.debug("SkinRepository: guaranteed-skin → on-demand fallback fetch for '" + name + "'.");
            SkinFetcher.fetchAsync(name, (value, sig) -> {
                if (value != null && !value.isBlank()) {
                    SkinProfile p = new SkinProfile(value, sig, "fallback:" + name);
                    fallbackSkin = p; // cache for next time
                    FppLogger.debug("SkinRepository: on-demand fallback fetched for '" + name + "'.");
                    callback.accept(p);
                } else {
                    FppLogger.warn("SkinRepository: on-demand fallback fetch also failed for '" + name + "' — bot will use Steve.");
                    callback.accept(null);
                }
            });
        } else {
            callback.accept(null); // No fallback configured at all
        }
    }

    // ── Statistics ────────────────────────────────────────────────────────────

    /** Returns the number of skins loaded from the skins folder. */
    public int getFolderSkinCount() { return folderSkins.size(); }

    /** Returns the number of skins in the config pool. */
    public int getPoolSkinCount()   { return poolSkins.size(); }

    /** Returns the number of exact-name overrides registered. */
    public int getOverrideCount()   { return namedOverrides.size(); }

    /** Returns the number of currently cached session skin resolutions. */
    public int getCacheSize()       { return sessionCache.size(); }

    /** Clears the session cache (resolved per-bot skins). */
    public void clearSessionCache() { sessionCache.clear(); }

    private String buildCacheKey(String mode, String botName) {
        return mode.toLowerCase(Locale.ROOT) + ":" + botName.toLowerCase(Locale.ROOT);
    }

    private void deliver(Consumer<@org.jetbrains.annotations.Nullable SkinProfile> callback,
                         @org.jetbrains.annotations.Nullable SkinProfile profile) {
        if (plugin != null && plugin.isEnabled() && !Bukkit.isPrimaryThread()) {
            Bukkit.getScheduler().runTask(plugin, () -> callback.accept(profile));
            return;
        }
        callback.accept(profile);
    }
}






