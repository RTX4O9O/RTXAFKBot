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

    /**
     * Returns the list of reply messages used when a real player mentions
     * a bot's name in chat ({@code replies:} section in {@code bot-messages.yml}).
     * Falls back to an empty list — callers should fall back to {@link #getMessages()}.
     */
    public static List<String> getReplyMessages() {
        if (cfg == null) return fallbackReplies();
        List<String> msgs = cfg.getStringList("replies");
        return msgs.isEmpty() ? fallbackReplies() : msgs;
    }

    /**
     * Returns the list of short burst follow-up messages
     * ({@code burst-followups:} section in {@code bot-messages.yml}).
     * Falls back to a small built-in list if the section is missing.
     */
    public static List<String> getBurstMessages() {
        if (cfg == null) return fallbackBursts();
        List<String> msgs = cfg.getStringList("burst-followups");
        return msgs.isEmpty() ? fallbackBursts() : msgs;
    }

    /**
     * Returns the list of messages bots say when a player joins
     * ({@code join-reactions:} section in {@code bot-messages.yml}).
     */
    public static List<String> getJoinReactionMessages() {
        if (cfg == null) return List.of();
        return cfg.getStringList("join-reactions");
    }

    /**
     * Returns the list of messages bots say when someone dies nearby
     * ({@code death-reactions:} section in {@code bot-messages.yml}).
     */
    public static List<String> getDeathReactionMessages() {
        if (cfg == null) return List.of();
        return cfg.getStringList("death-reactions");
    }

    /**
     * Returns the list of messages bots say when a real player leaves the server
     * ({@code leave-reactions:} section in {@code bot-messages.yml}).
     */
    public static List<String> getLeaveReactionMessages() {
        if (cfg == null) return List.of();
        return cfg.getStringList("leave-reactions");
    }

    /**
     * Returns the keyword-reaction message pool for the given key
     * ({@code keyword-reactions.<key>:} section in {@code bot-messages.yml}).
     * Returns an empty list when the key is absent.
     */
    public static List<String> getKeywordReactionMessages(String key) {
        if (cfg == null || key == null) return List.of();
        return cfg.getStringList("keyword-reactions." + key.toLowerCase());
    }

    private static List<String> fallback() {
        return Arrays.asList("gg", "let's go!", "hey everyone", "what's up", "nice server");
    }

    private static List<String> fallbackReplies() {
        return Arrays.asList("yeah?", "sup", "what?", "hm?", "here!");
    }

    private static List<String> fallbackBursts() {
        return Arrays.asList("lol", "fr", "ngl", "no cap", "lmao");
    }
}

