package me.bill.fakePlayerPlugin.config;

import me.bill.fakePlayerPlugin.FakePlayerPlugin;
import me.bill.fakePlayerPlugin.util.YamlFileSyncer;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.configuration.file.YamlConfiguration;

import java.io.File;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.Arrays;
import java.util.List;

/**
 * Loads and exposes the bot name pool from {@code bot-names.yml}.
 * Kept separate from {@link Config} so users can edit names without
 * touching the main configuration file.
 */
public final class BotNameConfig {

    private static FakePlayerPlugin plugin;
    private static FileConfiguration cfg;

    private BotNameConfig() {}

    // ── Lifecycle ────────────────────────────────────────────────────────────

    public static void init(FakePlayerPlugin instance) {
        plugin = instance;
        reload();
    }

    public static void reload() {
        // Sync missing keys (ensures root 'name' list is present on disk).
        YamlFileSyncer.syncMissingKeys(plugin, "bot-names.yml", "bot-names.yml");

        File file = new File(plugin.getDataFolder(), "bot-names.yml");
        if (!file.exists()) {
            plugin.saveResource("bot-names.yml", false);
        }

        FileConfiguration disk = YamlConfiguration.loadConfiguration(file);
        disk.options().copyDefaults(true);

        InputStream jarStream = plugin.getResource("bot-names.yml");
        if (jarStream != null) {
            YamlConfiguration jarDefaults = YamlConfiguration.loadConfiguration(
                    new InputStreamReader(jarStream, StandardCharsets.UTF_8));
            disk.setDefaults(jarDefaults);
        }

        cfg = disk;
        int count = cfg.getStringList("name").size();
        Config.debug("BotNameConfig loaded: " + count + " name(s) from " + file.getPath());
    }

    // ── Accessor ─────────────────────────────────────────────────────────────

    /**
     * Returns the list of bot names from {@code bot-names.yml}.
     * Falls back to a small built-in list if the file is empty or missing.
     */
    public static List<String> getNames() {
        if (cfg == null) return Arrays.asList("Alex", "Steve", "Notch");
        // YAML key is "name" (singular) — not "names"
        List<String> names = cfg.getStringList("name");
        if (names.isEmpty()) {
            // fallback: try "names" in case user renamed the key
            names = cfg.getStringList("names");
        }
        if (names.isEmpty()) {
            return Arrays.asList("Alex", "Steve", "Notch", "Herobrine", "Jeb_");
        }
        return names;
    }
}

