package me.bill.fakePlayerPlugin.util;

import me.bill.fakePlayerPlugin.config.BotNameConfig;
import me.bill.fakePlayerPlugin.config.Config;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.plugin.Plugin;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.io.File;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.time.Duration;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.regex.Pattern;
import java.util.regex.PatternSyntaxException;

/**
 * Validates and sanitises bot names against a configurable badword list.
 *
 * <h3>Word sources (merged at load time)</h3>
 * <ol>
 *   <li>Remote global baseline ({@code badword-filter.global-list-url}) — enabled by default</li>
 *   <li>{@code badword-filter.words} in {@code config.yml} — inline quick-list</li>
 *   <li>{@code plugins/FakePlayerPlugin/bad-words.yml} — main local YAML list + custom patterns</li>
 * </ol>
 *
 * <h3>Detection passes (applied in order)</h3>
 * <ol>
 *   <li><b>Raw pass</b> — direct case-insensitive substring match on the original name</li>
 *   <li><b>Leet pass</b> — after {@link #normalize} substitution
 *       ({@code 0→o 1→i 2→z 3→e 4→a 5→s 6→g 7→t 8→b 9→g _→removed})</li>
 *   <li><b>Aggressive pass</b> ({@code auto-detection.enabled: true}) — leet + consecutive
 *       duplicate-character collapse; catches {@code assshole}, {@code aasshole}, etc.</li>
 *   <li><b>Regex pass</b> ({@code auto-detection.enabled: true}) — compiled regex patterns:
 *       auto-generated repeat patterns ({@code f+u+c+k+}) applied to the leet-normalised
 *       string; auto-generated separator patterns ({@code f+[^a-z]*u+...}) applied to the
 *       raw-lowercase string catching digit/symbol insertions; strict-mode single-gap patterns
 *       ({@code f+[a-z0-9_]?u+...}) catching alternating filler chars such as {@code fXuXcXk};
 *       and any custom patterns declared in the {@code patterns:} block of
 *       {@code bad-words.yml}.</li>
 * </ol>
 *
 * <h3>Whitelist</h3>
 * Exact (case-insensitive) matches against the original name always allow the name through,
 * regardless of what the detection passes find.
 *
 * <h3>Auto-rename</h3>
 * When a bad name is detected, the filter can automatically swap it for a random clean
 * name from {@code bot-names.yml} instead of hard-blocking.
 */
public final class BadwordFilter {

    // ── Caches ────────────────────────────────────────────────────────────────
    /** Raw lowercase badwords — Pass 1. */
    private static final Set<String> rawWords       = new HashSet<>();
    /** Leet-normalised badwords — Pass 2. */
    private static final Set<String> normWords      = new HashSet<>();
    /** Aggressively collapsed badwords — Pass 3 (auto-detection). */
    private static final Set<String> collapsedWords = new HashSet<>();
    /** Exact whitelisted names (lowercase). */
    private static final Set<String> whitelist      = new HashSet<>();

    // ── Regex caches (Pass 4) ─────────────────────────────────────────────────
    /**
     * Custom user-defined regex patterns loaded from the {@code patterns:} block
     * in {@code bad-words.yml}. Applied (case-insensitively) to the raw-lowercase
     * name AND to the leet-normalised form.
     */
    private static final List<Pattern> customPatterns   = new ArrayList<>();
    private static final List<String>  customPatternSrc = new ArrayList<>();

    /**
     * Auto-generated "repeat-char" patterns compiled from {@link #rawWords}.
     * Example: {@code "fuck"} → {@code f+u+c+k+}
     * Applied to the leet-normalised string; catches {@code fuuuuck}, {@code fuccck}, etc.
     */
    private static final List<Pattern> autoRepeatPatterns = new ArrayList<>();
    private static final List<String>  autoRepeatWords    = new ArrayList<>();

    /**
     * Auto-generated "digit-separator" patterns compiled from {@link #rawWords}.
     * Example: {@code "fuck"} → {@code f+[^a-z]*u+[^a-z]*c+[^a-z]*k+}
     * Applied to the raw-lowercase string; catches {@code f1uck}, {@code f_u_c_k},
     * {@code f2u3ck} etc. where digits or underscores are inserted between letters.
     */
    private static final List<Pattern> autoSepPatterns = new ArrayList<>();
    private static final List<String>  autoSepWords    = new ArrayList<>();

    /**
     * Auto-generated strict-mode "single-gap" patterns compiled from {@link #rawWords}.
     * Example: {@code "fuck"} → {@code f+[a-z0-9_]?u+[a-z0-9_]?c+[a-z0-9_]?k+}
     * Applied to the raw-lowercase string only in strict mode; catches alternating
     * filler chars like {@code fXuXcXk}, {@code n1i1g1g1e1r}, etc.
     */
    private static final List<Pattern> autoGapPatterns = new ArrayList<>();
    private static final List<String>  autoGapWords    = new ArrayList<>();

    private static final String REMOTE_CACHE_FILE = "bad-words-remote-cache.txt";

    private static boolean initialized = false;

    private BadwordFilter() {}

    // ── Lifecycle ─────────────────────────────────────────────────────────────

    /**
     * Reloads all caches from the remote global baseline, {@code config.yml}, and
     * {@code bad-words.yml}. {@code plugin} is used to locate the external YAML file and
     * the on-disk remote cache; pass {@code null} to skip the external YAML/cache only.
     */
    public static void reload(@Nullable Plugin plugin) {
        rawWords.clear();
        normWords.clear();
        collapsedWords.clear();
        whitelist.clear();
        customPatterns.clear();
        customPatternSrc.clear();
        autoRepeatPatterns.clear();
        autoRepeatWords.clear();
        autoSepPatterns.clear();
        autoSepWords.clear();
        autoGapPatterns.clear();
        autoGapWords.clear();

        // 1. Remote global baseline (enabled by default)
        if (Config.isBadwordGlobalListEnabled()) {
            loadRemoteGlobalBadWords(plugin);
        }

        // 2. Inline config list
        for (String w : Config.getBadwords()) addWord(w);

        // 3. Dedicated bad-words.yml data file (also loads custom patterns:)
        if (plugin != null) loadBadWordsFile(plugin);

        // 4. Whitelist
        Config.getBadwordWhitelist().stream()
                .filter(s -> s != null && !s.isBlank())
                .map(String::toLowerCase)
                .forEach(whitelist::add);

        // 5. Build auto-generated regex patterns from the full word set
        buildAutoPatterns();

        initialized = true;
    }

    /** Convenience overload without file loading (used where no Plugin reference is available). */
    public static void reload() {
        reload(null);
    }

    // ── Public API ────────────────────────────────────────────────────────────

    /**
     * Returns {@code true} when the name is allowed as a bot name.
     *
     * <p>A name is <em>allowed</em> when any of the following is true:
     * <ul>
     *   <li>Badword filtering is disabled</li>
     *   <li>No badwords are configured</li>
     *   <li>The exact name (case-insensitive) is whitelisted</li>
     *   <li>None of the active detection passes find a match</li>
     * </ul>
     */
    public static boolean isAllowed(@NotNull String name) {
        if (!initialized) reload();
        if (!Config.isBadwordFilterEnabled()) return true;
        if (rawWords.isEmpty() && customPatterns.isEmpty()) return true;

        String lower = name.toLowerCase();
        if (whitelist.contains(lower)) return true;

        // Pass 1 — raw substring
        for (String bw : rawWords) {
            if (lower.contains(bw)) return false;
        }

        // Pass 2 — leet-speak normalised
        String leet = normalize(lower);
        for (String bw : normWords) {
            if (leet.contains(bw)) return false;
        }

        // Pass 3 — aggressive (char-repeat collapse) — only when auto-detection is on
        if (Config.isBadwordAutoDetectionEnabled()
                && !Config.getBadwordAutoDetectionMode().equals("off")) {
            String collapsed = collapseRepeats(leet);
            for (String bw : collapsedWords) {
                if (collapsed.contains(bw)) return false;
            }

            // Pass 4 — Regex patterns ───────────────────────────────────────────────
            // 4a: Custom user-defined patterns from bad-words.yml patterns: block.
            //     Applied to both the raw-lowercase name and the leet-normalised form.
            for (Pattern p : customPatterns) {
                if (p.matcher(lower).find() || p.matcher(leet).find()) return false;
            }
            // 4b: Auto-generated repeat patterns (f+u+c+k+) applied to leet form.
            //     Catches fuuuuck, fuccck without needing a full collapseRepeats pass.
            for (Pattern p : autoRepeatPatterns) {
                if (p.matcher(leet).find()) return false;
            }
            // 4c: Auto-generated digit-separator patterns (f+[^a-z]*u+[^a-z]*c+[^a-z]*k+)
            //     applied to the raw-lowercase name.
            //     Catches f1uck, f2u3ck, f_u_c_k-style insertions that leet-normalization
            //     cannot handle (because the inserted digit maps to a different letter).
            for (Pattern p : autoSepPatterns) {
                if (p.matcher(lower).find()) return false;
            }
            // 4d: Strict-mode single-gap patterns applied to the raw-lowercase name.
            //     Catches alternating filler chars like fXuXcXk / n1i1g1g1e1r.
            if (Config.getBadwordAutoDetectionMode().equals("strict")) {
                for (Pattern p : autoGapPatterns) {
                    if (p.matcher(lower).find()) return false;
                }
            }
        }

        return true;
    }

    /**
     * Tries to sanitise a bad name by picking a random clean name from the bot name pool.
     * Returns a clean replacement name when badword filtering is enabled and the original
     * name fails {@link #isAllowed}. Returns {@code null} if no clean names are available
     * or the filter is disabled — the caller must reject.
     */
    public static @Nullable String sanitize(@NotNull String name) {
        if (!initialized) reload();
        if (!Config.isBadwordFilterEnabled()) return null;
        if (rawWords.isEmpty() && customPatterns.isEmpty()) return null;
        if (isAllowed(name)) return null;

        // Pick a random clean name from the bot name pool
        List<String> pool = BotNameConfig.getNames();
        if (pool.isEmpty()) return null;

        // Try up to 10 random names from the pool
        java.util.Random rand = new java.util.Random();
        for (int i = 0; i < Math.min(10, pool.size()); i++) {
            String candidate = pool.get(rand.nextInt(pool.size()));
            if (candidate.length() <= 16
                    && candidate.matches("[a-zA-Z0-9_]+")
                    && isAllowed(candidate)) {
                return candidate;
            }
        }

        // Fallback: no clean names found in 10 tries
        return null;
    }

    /**
     * Returns the first matching badword found in {@code name} (leet-speak + auto-detection
     * aware), or {@code null} if the name is clean / filter is disabled.
     * Used for rejection messages.
     */
    public static @Nullable String findBadword(@NotNull String name) {
        if (!initialized) reload();
        if (!Config.isBadwordFilterEnabled() || (rawWords.isEmpty() && customPatterns.isEmpty())) return null;

        String lower = name.toLowerCase();

        // Pass 1
        for (String bw : rawWords) {
            if (lower.contains(bw)) return bw;
        }

        // Pass 2
        String leet = normalize(lower);
        for (String bw : rawWords) {
            if (leet.contains(normalize(bw))) return bw;
        }

        // Pass 3
        if (Config.isBadwordAutoDetectionEnabled()
                && !Config.getBadwordAutoDetectionMode().equals("off")) {
            String collapsed = collapseRepeats(leet);
            for (String bw : rawWords) {
                if (collapsed.contains(collapseRepeats(normalize(bw)))) return bw;
            }

            // Pass 4 — Regex
            for (int i = 0; i < customPatterns.size(); i++) {
                if (customPatterns.get(i).matcher(lower).find()
                        || customPatterns.get(i).matcher(leet).find()) return customPatternSrc.get(i);
            }
            for (int i = 0; i < autoRepeatPatterns.size(); i++) {
                if (autoRepeatPatterns.get(i).matcher(leet).find()) return autoRepeatWords.get(i);
            }
            for (int i = 0; i < autoSepPatterns.size(); i++) {
                if (autoSepPatterns.get(i).matcher(lower).find()) return autoSepWords.get(i);
            }
            if (Config.getBadwordAutoDetectionMode().equals("strict")) {
                for (int i = 0; i < autoGapPatterns.size(); i++) {
                    if (autoGapPatterns.get(i).matcher(lower).find()) return autoGapWords.get(i);
                }
            }
        }

        return null;
    }

    /** Number of active badwords and custom patterns in the filter (from all sources). */
    public static int getBadwordCount() {
        if (!initialized) reload();
        return rawWords.size() + customPatterns.size();
    }

    /** Number of whitelisted names. */
    public static int getWhitelistCount() {
        if (!initialized) reload();
        return whitelist.size();
    }

    // ── Internal helpers ──────────────────────────────────────────────────────

    /** Adds a single word to all caches. */
    private static void addWord(@Nullable String word) {
        if (word == null || word.isBlank()) return;
        String lower = word.trim().toLowerCase();
        rawWords.add(lower);
        normWords.add(normalize(lower));
        collapsedWords.add(collapseRepeats(normalize(lower)));
    }

    /** Loads the remote global baseline, falling back to the last successful on-disk cache. */
    private static void loadRemoteGlobalBadWords(@Nullable Plugin plugin) {
        String content = fetchRemoteGlobalList();
        if (content != null) {
            loadPlainTextWords(content);
            saveRemoteCache(plugin, content);
            return;
        }

        String cached = loadRemoteCache(plugin);
        if (cached != null) {
            FppLogger.warn("BadwordFilter: using cached remote global badword list (latest fetch failed).");
            loadPlainTextWords(cached);
        }
    }

    /** Fetches the configured remote global list as plain text. Returns {@code null} on failure. */
    private static @Nullable String fetchRemoteGlobalList() {
        String url = Config.badwordGlobalListUrl();
        if (url == null || url.isBlank()) {
            FppLogger.warn("BadwordFilter: global-list-url is blank; skipping remote badword fetch.");
            return null;
        }
        try {
            int timeout = Config.badwordGlobalListTimeoutMs();
            HttpClient client = HttpClient.newBuilder()
                    .connectTimeout(Duration.ofMillis(timeout))
                    .build();
            HttpRequest request = HttpRequest.newBuilder(URI.create(url.trim()))
                    .timeout(Duration.ofMillis(timeout))
                    .header("User-Agent", "FakePlayerPlugin/1.6.0")
                    .GET()
                    .build();
            HttpResponse<String> response = client.send(request, HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8));
            if (response.statusCode() >= 200 && response.statusCode() < 300) {
                return response.body();
            }
            FppLogger.warn("BadwordFilter: failed to fetch remote global list — HTTP " + response.statusCode());
        } catch (Exception e) {
            FppLogger.warn("BadwordFilter: failed to fetch remote global list — " + e.getMessage());
        }
        return null;
    }

    /** Loads plain-text words where each non-blank line is treated as a badword. */
    private static void loadPlainTextWords(@Nullable String content) {
        if (content == null || content.isBlank()) return;
        String[] lines = content.split("\\R");
        for (String line : lines) {
            String word = line == null ? "" : line.trim();
            if (word.isEmpty()) continue;
            if (word.startsWith("#") || word.startsWith(";")) continue;
            addWord(word);
        }
    }

    /** Saves the last successful remote list fetch to disk for offline fallback. */
    private static void saveRemoteCache(@Nullable Plugin plugin, @NotNull String content) {
        if (plugin == null) return;
        try {
            if (!plugin.getDataFolder().exists() && !plugin.getDataFolder().mkdirs()) {
                return;
            }
            File cacheFile = new File(plugin.getDataFolder(), REMOTE_CACHE_FILE);
            Files.writeString(cacheFile.toPath(), content, StandardCharsets.UTF_8);
        } catch (Exception e) {
            FppLogger.warn("BadwordFilter: failed to save remote badword cache — " + e.getMessage());
        }
    }

    /** Loads the last successful remote badword cache from disk, if present. */
    private static @Nullable String loadRemoteCache(@Nullable Plugin plugin) {
        if (plugin == null) return null;
        try {
            File cacheFile = new File(plugin.getDataFolder(), REMOTE_CACHE_FILE);
            if (!cacheFile.exists()) return null;
            return Files.readString(cacheFile.toPath(), StandardCharsets.UTF_8);
        } catch (Exception e) {
            FppLogger.warn("BadwordFilter: failed to load remote badword cache — " + e.getMessage());
            return null;
        }
    }

    /** Loads words from {@code bad-words.yml} in the plugin data folder. */
    private static void loadBadWordsFile(@NotNull Plugin plugin) {
        try {
            File file = new File(plugin.getDataFolder(), "bad-words.yml");
            if (!file.exists()) {
                plugin.saveResource("bad-words.yml", false);
            }
            YamlConfiguration yaml = YamlConfiguration.loadConfiguration(file);
            loadWordsAndPatterns(yaml, file.getName());
        } catch (Exception e) {
            FppLogger.warn("BadwordFilter: failed to load bad-words.yml — " + e.getMessage());
        }
    }

    /** Loads both plain words and optional custom regex patterns from a YAML source. */
    private static void loadWordsAndPatterns(@NotNull YamlConfiguration yaml, @NotNull String sourceName) {
        List<String> fileWords = yaml.getStringList("words");
        for (String w : fileWords) addWord(w);

        List<String> filePatterns = yaml.getStringList("patterns");
        for (String ps : filePatterns) {
            if (ps == null || ps.isBlank()) continue;
            try {
                customPatterns.add(Pattern.compile(ps, Pattern.CASE_INSENSITIVE));
                customPatternSrc.add(ps);
            } catch (PatternSyntaxException e) {
                FppLogger.warn("BadwordFilter: invalid pattern '" + ps + "' in " + sourceName + " — " + e.getMessage());
            }
        }
    }

    // ── Regex pattern builders ────────────────────────────────────────────────

    /**
     * Builds auto-generated regex caches from the current {@link #rawWords} set.
     * Must be called after all word sources are loaded.
     *
     * <ul>
     *   <li>{@link #autoRepeatPatterns} — {@code f+u+c+k+} style (repeated chars per letter)</li>
     *   <li>{@link #autoSepPatterns}    — {@code f+[^a-z]*u+[^a-z]*c+[^a-z]*k+} style
     *       (non-letter separators between letters)</li>
     * </ul>
     *
     * <p>Words whose collapsed-normalised form is shorter than 3 characters are skipped
     * to avoid false positives (e.g. "as" alone would match too broadly).
     */
    private static void buildAutoPatterns() {
        autoRepeatPatterns.clear();
        autoRepeatWords.clear();
        autoSepPatterns.clear();
        autoSepWords.clear();
        autoGapPatterns.clear();
        autoGapWords.clear();

        for (String word : rawWords) {
            String collapsed = collapseRepeats(normalize(word));
            if (collapsed.length() < 3) continue;  // too short — skip
            try {
                autoRepeatPatterns.add(buildRepeatPattern(collapsed));
                autoRepeatWords.add(word);
                autoSepPatterns.add(buildSeparatedPattern(collapsed));
                autoSepWords.add(word);
                autoGapPatterns.add(buildSingleGapPattern(collapsed));
                autoGapWords.add(word);
            } catch (PatternSyntaxException e) {
                FppLogger.warn("BadwordFilter: failed to compile auto-pattern for '" + word + "': " + e.getMessage());
            }
        }
    }

    /**
     * Builds a regex that matches repeated occurrences of each letter in the word.
     * Example: {@code "fuck"} → {@code f+u+c+k+}
     * Applied to the leet-normalised input string.
     */
    private static Pattern buildRepeatPattern(@NotNull String collapsedNorm) {
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < collapsedNorm.length(); i++) {
            sb.append(Pattern.quote(String.valueOf(collapsedNorm.charAt(i)))).append('+');
        }
        return Pattern.compile(sb.toString());
    }

    /**
     * Builds a regex that allows non-letter characters (digits, underscores) between
     * each letter of the word.
     * Example: {@code "fuck"} → {@code f+[^a-z]*u+[^a-z]*c+[^a-z]*k+}
     * Applied to the raw-lowercase input string; catches {@code f1uck}, {@code f_u_c_k},
     * {@code f2u3ck}, etc.
     */
    private static Pattern buildSeparatedPattern(@NotNull String collapsedNorm) {
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < collapsedNorm.length(); i++) {
            if (i > 0) sb.append("[^a-z]*");  // allow non-letter chars (digits, underscores) between letters
            sb.append(Pattern.quote(String.valueOf(collapsedNorm.charAt(i)))).append('+');
        }
        return Pattern.compile(sb.toString());
    }

    /**
     * Builds a regex that allows up to one alpha-numeric / underscore filler between
     * each letter of the word.
     * Example: {@code "fuck"} → {@code f+[a-z0-9_]?u+[a-z0-9_]?c+[a-z0-9_]?k+}
     * Applied to the raw-lowercase input string in strict mode; catches alternating
     * single-character obfuscation while remaining narrower than {@code .*}-style gaps.
     */
    private static Pattern buildSingleGapPattern(@NotNull String collapsedNorm) {
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < collapsedNorm.length(); i++) {
            if (i > 0) sb.append("[a-z0-9_]?");
            sb.append(Pattern.quote(String.valueOf(collapsedNorm.charAt(i)))).append('+');
        }
        return Pattern.compile(sb.toString());
    }

    /**
     * Leet-speak normalization.
     * Converts common digit/letter substitutions and strips underscore spacers.
     * Input should already be lowercased.
     */
    static String normalize(@NotNull String input) {
        StringBuilder sb = new StringBuilder(input.length());
        for (int i = 0; i < input.length(); i++) {
            char c = input.charAt(i);
            switch (c) {
                case '0' -> sb.append('o');
                case '1' -> sb.append('i');
                case '2' -> sb.append('z');
                case '3' -> sb.append('e');
                case '4' -> sb.append('a');
                case '5' -> sb.append('s');
                case '6' -> sb.append('g');
                case '7' -> sb.append('t');
                case '8' -> sb.append('b');
                case '9' -> sb.append('g');
                case '_' -> { /* strip — treated as spacer */ }
                default  -> sb.append(c);
            }
        }
        return sb.toString();
    }

    /**
     * Collapses consecutive identical characters: {@code "niiigger"} → {@code "niger"}.
     * Applied on top of {@link #normalize} for auto-detection mode.
     */
    private static String collapseRepeats(@NotNull String input) {
        if (input.isEmpty()) return input;
        StringBuilder sb = new StringBuilder(input.length());
        char prev = 0;
        for (int i = 0; i < input.length(); i++) {
            char c = input.charAt(i);
            if (c != prev) {
                sb.append(c);
                prev = c;
            }
        }
        return sb.toString();
    }

}
