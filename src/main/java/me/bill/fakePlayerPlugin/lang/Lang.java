package me.bill.fakePlayerPlugin.lang;

import me.bill.fakePlayerPlugin.FakePlayerPlugin;
import me.bill.fakePlayerPlugin.config.Config;
import me.bill.fakePlayerPlugin.util.TextUtil;
import me.bill.fakePlayerPlugin.util.YamlFileSyncer;
import net.kyori.adventure.text.Component;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.configuration.file.YamlConfiguration;

import java.io.File;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;

/**
 * Loads and exposes all messages defined in {@code language/en.yml}.
 * <p>
 * Call {@link #reload()} to hot-reload the file at runtime.
 */
public final class Lang {

    private static FakePlayerPlugin plugin;
    private static FileConfiguration cfg;

    private Lang() {}

    // ── Lifecycle ────────────────────────────────────────────────────────────

    public static void init(FakePlayerPlugin instance) {
        plugin = instance;
        reload();
    }

    public static void reload() {
        // Sync missing keys from the JAR into the disk file before loading.
        // New message keys added by plugin updates are automatically written to
        // the user's en.yml so they become visible and customisable.
        // Existing user values are never overwritten.
        YamlFileSyncer.syncMissingKeys(plugin, "language/en.yml", "language/en.yml");

        // Save the default from the JAR if the file doesn't exist on disk yet
        File file = new File(plugin.getDataFolder(), "language/en.yml");
        if (!file.exists()) {
            plugin.saveResource("language/en.yml", false);
        }

        // Load disk file, then overlay the JAR defaults so nothing is ever missing
        FileConfiguration disk = YamlConfiguration.loadConfiguration(file);
        disk.options().copyDefaults(true);

        InputStream jarStream = plugin.getResource("language/en.yml");
        if (jarStream != null) {
            YamlConfiguration jarDefaults = YamlConfiguration.loadConfiguration(
                    new InputStreamReader(jarStream, StandardCharsets.UTF_8));
            disk.setDefaults(jarDefaults);
        }

        cfg = disk;
        Config.debug("Lang loaded from: " + file.getPath());
    }

    // ── Accessor ─────────────────────────────────────────────────────────────

    /**
     * Returns the raw string for {@code key}, resolving {@code {prefix}} automatically.
     * <p>
     * Supports two placeholder styles:
     * <ul>
     *   <li><b>Positional</b> – pass values only; {@code {0}}, {@code {1}}, … are replaced in order.</li>
     *   <li><b>Named</b> – pass alternating name/value pairs; e.g. {@code "page","1","total","3"}
     *       replaces {@code {page}} and {@code {total}}.</li>
     * </ul>
     */
    public static String raw(String key, String... args) {
        String value = cfg.getString(key, "&c[FPP] Missing lang key: " + key);

        // Resolve nested {prefix} reference
        String prefix = cfg.getString("prefix", "&f[FPP] ");
        value = value.replace("{prefix}", prefix);

        if (args.length > 0) {
            // Named placeholder mode: args are key-value pairs ("page","1","total","3")
            if (args.length % 2 == 0 && !args[0].chars().allMatch(Character::isDigit)) {
                for (int i = 0; i < args.length - 1; i += 2) {
                    value = value.replace("{" + args[i] + "}", args[i + 1]);
                }
            } else {
                // Positional placeholder mode: {0}, {1}, …
                for (int i = 0; i < args.length; i++) {
                    value = value.replace("{" + i + "}", args[i]);
                }
            }
        }
        return value;
    }

    /**
     * Convenience: returns a colourised {@link Component} for {@code key}.
     * Accepts both positional and named placeholders — see {@link #raw(String, String...)}.
     */
    public static Component get(String key, String... args) {
        return TextUtil.colorize(raw(key, args));
    }

    /** Exposes the underlying config (e.g. for iterating keys). */
    public static FileConfiguration config() {
        return cfg;
    }
}

