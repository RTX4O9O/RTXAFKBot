package me.bill.fakePlayerPlugin.ai;

import me.bill.fakePlayerPlugin.FakePlayerPlugin;
import me.bill.fakePlayerPlugin.util.FppLogger;

import java.io.*;
import java.nio.file.Files;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Loads and manages AI personality prompts from the
 * {@code plugins/FakePlayerPlugin/personalities/} folder.
 *
 * <p>Each {@code .txt} file in that folder defines one personality.
 * The file name (without {@code .txt}) is the personality's display name.
 * Personality names are case-insensitive.
 *
 * <p>Sample files bundled in the JAR ({@code friendly.txt}, {@code grumpy.txt},
 * {@code noob.txt}) are extracted on first run if the folder is empty.
 * Run {@code /fpp personality reload} or {@code /fpp reload} to pick up
 * file changes without restarting the server.
 *
 * <p>Supported placeholder in personality text: {@code {bot_name}} — replaced
 * at dispatch time with the bot's actual Minecraft username.
 */
public final class PersonalityRepository {

    /** Names of sample personality files bundled in the plugin JAR. */
    private static final String[] SAMPLE_FILES = {
            "friendly.txt",
            "grumpy.txt",
            "noob.txt"
    };

    private final FakePlayerPlugin plugin;

    /** name (lowercase) → personality prompt text */
    private final Map<String, String> personalities = new ConcurrentHashMap<>();

    public PersonalityRepository(FakePlayerPlugin plugin) {
        this.plugin = plugin;
    }

    // ── Lifecycle ─────────────────────────────────────────────────────────────

    /**
     * Creates the personalities folder, extracts bundled sample files on first
     * run, then loads all {@code .txt} files into memory.
     * Call once during {@code onEnable} after {@code ensureDataDirectories()}.
     */
    public void init() {
        File folder = getFolder();
        if (!folder.exists()) {
            boolean ok = folder.mkdirs();
            FppLogger.debug("PersonalityRepository: created personalities/ folder → " + ok);
        }
        extractSamples(folder);
        load(folder);
    }

    /**
     * Discards all cached personalities and re-reads the folder from disk.
     * Call from {@code /fpp reload}.
     */
    public void reload() {
        personalities.clear();
        File folder = getFolder();
        if (folder.exists()) {
            load(folder);
            FppLogger.debug("PersonalityRepository: reloaded — " + personalities.size() + " personalities.");
        } else {
            FppLogger.warn("PersonalityRepository: personalities/ folder not found during reload.");
        }
    }

    // ── Public API ────────────────────────────────────────────────────────────

    /**
     * Returns the prompt text for the given personality name, or {@code null}
     * if no personality with that name is loaded.
     *
     * @param name case-insensitive personality name (filename without {@code .txt})
     */
    public String get(String name) {
        if (name == null) return null;
        return personalities.get(name.toLowerCase(Locale.ROOT));
    }

    /**
     * Returns {@code true} when a personality with the given name is loaded.
     *
     * @param name case-insensitive
     */
    public boolean has(String name) {
        if (name == null) return false;
        return personalities.containsKey(name.toLowerCase(Locale.ROOT));
    }

    /**
     * Returns an unmodifiable snapshot of all loaded personality names
     * (lower-cased, sorted alphabetically).
     */
    public List<String> getNames() {
        List<String> names = new ArrayList<>(personalities.keySet());
        Collections.sort(names);
        return Collections.unmodifiableList(names);
    }

    /** Number of loaded personalities. */
    public int size() {
        return personalities.size();
    }

    // ── Internal helpers ──────────────────────────────────────────────────────

    /** Returns the {@code personalities/} folder under the plugin data directory. */
    private File getFolder() {
        return new File(plugin.getDataFolder(), "personalities");
    }

    /**
     * Copies bundled sample files from the JAR into {@code folder} only if they
     * don't already exist there.  This preserves any user edits.
     */
    private void extractSamples(File folder) {
        for (String sample : SAMPLE_FILES) {
            File target = new File(folder, sample);
            if (!target.exists()) {
                try (InputStream in = plugin.getResource("personalities/" + sample)) {
                    if (in != null) {
                        Files.copy(in, target.toPath());
                        FppLogger.debug("PersonalityRepository: extracted sample → " + sample);
                    }
                } catch (IOException e) {
                    FppLogger.warn("PersonalityRepository: could not extract " + sample
                            + " — " + e.getMessage());
                }
            }
        }
    }

    /**
     * Scans {@code folder} for {@code .txt} files and loads their content.
     * Files with an empty body are skipped with a warning.
     */
    private void load(File folder) {
        File[] files = folder.listFiles((dir, name) ->
                name.toLowerCase(Locale.ROOT).endsWith(".txt"));

        if (files == null || files.length == 0) {
            FppLogger.debug("PersonalityRepository: personalities/ folder is empty.");
            return;
        }

        int loaded = 0;
        for (File file : files) {
            String rawName = file.getName();
            // Strip .txt extension
            String name = rawName.substring(0, rawName.length() - 4).toLowerCase(Locale.ROOT);
            try {
                String content = Files.readString(file.toPath()).trim();
                if (content.isEmpty()) {
                    FppLogger.warn("PersonalityRepository: '" + rawName
                            + "' is empty — skipped.");
                    continue;
                }
                personalities.put(name, content);
                loaded++;
                FppLogger.debug("PersonalityRepository: loaded '" + name
                        + "' (" + content.length() + " chars)");
            } catch (IOException e) {
                FppLogger.warn("PersonalityRepository: failed to read '" + rawName
                        + "' — " + e.getMessage());
            }
        }
        FppLogger.debug("PersonalityRepository: " + loaded + " personality file(s) loaded.");
    }
}



