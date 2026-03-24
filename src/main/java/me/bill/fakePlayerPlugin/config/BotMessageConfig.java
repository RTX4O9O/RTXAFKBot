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
 * Loads and exposes the bot chat message pool from {@code bot-messages.yml}.
 * Kept separate from {@link Config} so users can edit messages without
 * touching the main configuration file.
 */
public final class BotMessageConfig {

    private static FakePlayerPlugin  plugin;
    private static FileConfiguration cfg;

    private BotMessageConfig() {}

    // ── Lifecycle ────────────────────────────────────────────────────────────

    public static void init(FakePlayerPlugin instance) {
        plugin = instance;
        reload();
    }

    public static void reload() {
        // Sync missing keys (ensures root 'messages' list is present on disk).
        YamlFileSyncer.syncMissingKeys(plugin, "bot-messages.yml", "bot-messages.yml");

        File file = new File(plugin.getDataFolder(), "bot-messages.yml");
        if (!file.exists()) {
            plugin.saveResource("bot-messages.yml", false);
        }

        FileConfiguration disk = YamlConfiguration.loadConfiguration(file);
        disk.options().copyDefaults(true);

        InputStream jarStream = plugin.getResource("bot-messages.yml");
        if (jarStream != null) {
            YamlConfiguration jarDefaults = YamlConfiguration.loadConfiguration(
                    new InputStreamReader(jarStream, StandardCharsets.UTF_8));
            disk.setDefaults(jarDefaults);
        }

        cfg = disk;
        Config.debug("BotMessageConfig loaded: " + file.getPath()
                + " (" + getMessages().size() + " messages)");
    }

    // ── Accessor ─────────────────────────────────────────────────────────────

    /**
     * Returns the full list of chat messages from {@code bot-messages.yml}.
     * Falls back to a small built-in list if the file is empty or missing.
     */
    public static List<String> getMessages() {
        if (cfg == null) return fallback();
        List<String> msgs = cfg.getStringList("messages");
        return msgs.isEmpty() ? fallback() : msgs;
    }

    private static List<String> fallback() {
        return Arrays.asList("gg", "let's go!", "hey everyone", "what's up", "nice server");
    }
}

