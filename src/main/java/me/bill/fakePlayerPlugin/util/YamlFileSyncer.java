package me.bill.fakePlayerPlugin.util;

import me.bill.fakePlayerPlugin.FakePlayerPlugin;
import org.bukkit.configuration.file.YamlConfiguration;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * Non-destructive YAML file syncer.
 *
 * <p>Compares a disk config file against the bundled JAR default and writes any
 * keys that exist in the JAR but are absent from the disk file.
 * <b>No existing user value is ever overwritten.</b>
 *
 * <h3>Covered files</h3>
 * <ul>
 *   <li>{@code language/en.yml}  — message strings; new keys added by plugin updates
 *       are automatically merged into the user's file.</li>
 *   <li>{@code bot-names.yml}    — ensures the root {@code name} list key is present.</li>
 *   <li>{@code bot-messages.yml} — ensures the root {@code messages} list key is present.</li>
 * </ul>
 *
 * <h3>Integration points</h3>
 * <ul>
 *   <li>{@link me.bill.fakePlayerPlugin.lang.Lang#reload()} — runs automatically on
 *       startup and every {@code /fpp reload}.</li>
 *   <li>{@link me.bill.fakePlayerPlugin.config.BotNameConfig#reload()} — same.</li>
 *   <li>{@link me.bill.fakePlayerPlugin.config.BotMessageConfig#reload()} — same.</li>
 *   <li>{@code /fpp migrate lang|names|messages} — force-sync from the command line.</li>
 * </ul>
 */
public final class YamlFileSyncer {

    private YamlFileSyncer() {}

    // ── Public types ──────────────────────────────────────────────────────────

    /**
     * Immutable result of a {@link #syncMissingKeys} call.
     *
     * @param fileName  Short name of the file that was processed.
     * @param keysAdded Unmodifiable list of keys that were added to the disk file.
     */
    public record SyncResult(String fileName, List<String> keysAdded) {
        /** {@code true} when at least one key was added. */
        public boolean hasChanges() { return !keysAdded.isEmpty(); }
        /** Number of keys that were added. */
        public int count()          { return keysAdded.size(); }
    }

    // ── Public API ────────────────────────────────────────────────────────────

    /**
     * Synchronises a disk YAML file against the bundled JAR resource.
     *
     * <p>Algorithm:
     * <ol>
     *   <li>If the disk file does not exist it is extracted from the JAR unchanged
     *       and an empty result is returned.</li>
     *   <li>Both versions are loaded into memory.</li>
     *   <li>Every leaf key present in the JAR but absent from disk is collected.</li>
     *   <li>Missing keys are set on the disk config with their JAR default values
     *       and the file is saved.</li>
     *   <li>Existing user values are never touched.</li>
     * </ol>
     *
     * @param plugin          Plugin instance (used for data folder and resource access).
     * @param diskRelPath     Path relative to the plugin data folder,
     *                        e.g. {@code "language/en.yml"}.
     * @param jarResourcePath Path of the bundled resource inside the JAR,
     *                        e.g. {@code "language/en.yml"}.
     * @return A {@link SyncResult} describing any changes that were made.
     */
    public static SyncResult syncMissingKeys(FakePlayerPlugin plugin,
                                              String diskRelPath,
                                              String jarResourcePath) {
        File diskFile = new File(plugin.getDataFolder(), diskRelPath);
        String fileName = diskFile.getName();

        // ── First run: file does not exist yet ────────────────────────────────
        if (!diskFile.exists()) {
            diskFile.getParentFile().mkdirs();
            plugin.saveResource(jarResourcePath, false);
            FppLogger.debug("YamlFileSyncer: extracted " + fileName + " from JAR (first run).");
            return new SyncResult(fileName, List.of());
        }

        // ── Load JAR defaults ─────────────────────────────────────────────────
        InputStream jarStream = plugin.getResource(jarResourcePath);
        if (jarStream == null) {
            FppLogger.warn("YamlFileSyncer: JAR resource not found: " + jarResourcePath);
            return new SyncResult(fileName, List.of());
        }

        YamlConfiguration jarCfg;
        YamlConfiguration diskCfg;
        try (jarStream) {
            jarCfg  = YamlConfiguration.loadConfiguration(
                    new InputStreamReader(jarStream, StandardCharsets.UTF_8));
            diskCfg = YamlConfiguration.loadConfiguration(diskFile);
        } catch (IOException e) {
            FppLogger.warn("YamlFileSyncer: failed to read files for " + fileName
                    + ": " + e.getMessage());
            return new SyncResult(fileName, List.of());
        } catch (RuntimeException e) {
            // YAMLException (RuntimeException) is thrown when the disk file contains
            // unknown Bukkit-serialized types (e.g. !!MVSpawnLocation from Multiverse).
            // Log a warning and skip the sync rather than crashing the plugin.
            FppLogger.warn("YamlFileSyncer: could not parse disk file '" + fileName
                    + "' — it may be corrupted or contain unknown serializable types"
                    + " (e.g. from another plugin). Skipping sync. Cause: " + e.getMessage());
            return new SyncResult(fileName, List.of());
        }

        // ── Find missing leaf keys ────────────────────────────────────────────
        List<String> missing = new ArrayList<>();
        for (String key : jarCfg.getKeys(true)) {
            if (jarCfg.isConfigurationSection(key)) continue; // skip section headers
            if (!diskCfg.contains(key)) missing.add(key);
        }

        if (missing.isEmpty()) {
            FppLogger.debug("YamlFileSyncer: " + fileName + " is up to date — no keys missing.");
            return new SyncResult(fileName, List.of());
        }

        // ── Apply and save ────────────────────────────────────────────────────
        for (String key : missing) {
            diskCfg.set(key, jarCfg.get(key));
        }

        try {
            diskCfg.save(diskFile);
        } catch (IOException e) {
            FppLogger.warn("YamlFileSyncer: failed to save " + fileName + ": " + e.getMessage());
            return new SyncResult(fileName, List.of());
        }

        FppLogger.info("YamlFileSyncer: " + fileName + " — added " + missing.size()
                + " new key(s) from JAR defaults: " + String.join(", ", missing));
        return new SyncResult(fileName, Collections.unmodifiableList(missing));
    }

    /**
     * Counts how many JAR keys are absent from the disk file without writing anything.
     * Useful for status reporting.
     *
     * @return Number of missing keys, or {@code -1} if the disk file does not exist.
     */
    public static int countMissingKeys(FakePlayerPlugin plugin,
                                        String diskRelPath,
                                        String jarResourcePath) {
        File diskFile = new File(plugin.getDataFolder(), diskRelPath);
        if (!diskFile.exists()) return -1;

        InputStream jarStream = plugin.getResource(jarResourcePath);
        if (jarStream == null) return 0;

        YamlConfiguration jarCfg;
        YamlConfiguration diskCfg;
        try (jarStream) {
            jarCfg  = YamlConfiguration.loadConfiguration(
                    new InputStreamReader(jarStream, StandardCharsets.UTF_8));
            diskCfg = YamlConfiguration.loadConfiguration(diskFile);
        } catch (IOException e) {
            return 0;
        } catch (RuntimeException e) {
            FppLogger.warn("YamlFileSyncer: could not parse disk file for countMissingKeys"
                    + " — skipping. Cause: " + e.getMessage());
            return 0;
        }

        int count = 0;
        for (String key : jarCfg.getKeys(true)) {
            if (jarCfg.isConfigurationSection(key)) continue;
            if (!diskCfg.contains(key)) count++;
        }
        return count;
    }

    /**
     * Returns the total number of leaf keys defined in the JAR resource.
     * Used to display "X / Y keys present" in status output.
     *
     * @return Total key count in the JAR version, or {@code 0} if resource not found.
     */
    public static int countJarKeys(FakePlayerPlugin plugin, String jarResourcePath) {
        InputStream jarStream = plugin.getResource(jarResourcePath);
        if (jarStream == null) return 0;
        try (jarStream) {
            YamlConfiguration jarCfg = YamlConfiguration.loadConfiguration(
                    new InputStreamReader(jarStream, StandardCharsets.UTF_8));
            int count = 0;
            for (String key : jarCfg.getKeys(true)) {
                if (!jarCfg.isConfigurationSection(key)) count++;
            }
            return count;
        } catch (IOException e) {
            return 0;
        }
    }
}

