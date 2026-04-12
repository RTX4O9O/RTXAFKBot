package me.bill.fakePlayerPlugin.command;

import me.bill.fakePlayerPlugin.util.FppLogger;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.plugin.java.JavaPlugin;

import java.io.File;
import java.io.IOException;
import java.util.Locale;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Persists per-bot area-mining selections (pos1 / pos2) and active-job flag
 * to {@code plugins/FakePlayerPlugin/data/mine-selections.yml}.
 *
 * <p>Selections survive server restarts so operators can run
 * {@code /fpp mine <bot> --start} again after a restart without re-selecting
 * the two corners.  Active jobs are automatically resumed.
 */
public final class MineSelectionStore {

    private static final String FILE_NAME = "mine-selections.yml";

    private final JavaPlugin plugin;
    /** botName(lower) → entry */
    private final Map<String, BotEntry> entries = new ConcurrentHashMap<>();
    private File file;

    public MineSelectionStore(JavaPlugin plugin) {
        this.plugin = plugin;
    }

    // ── Load / Save ───────────────────────────────────────────────────────────

    public void load() {
        file = new File(plugin.getDataFolder(), "data/" + FILE_NAME);
        if (!file.exists()) return;

        YamlConfiguration yaml = YamlConfiguration.loadConfiguration(file);
        int loaded = 0;
        for (String botKey : yaml.getKeys(false)) {
            ConfigurationSection sec = yaml.getConfigurationSection(botKey);
            if (sec == null) continue;

            Location pos1 = readLoc(sec, "pos1");
            Location pos2 = readLoc(sec, "pos2");
            boolean active = sec.getBoolean("active", false);

            if (pos1 != null && pos2 != null) {
                entries.put(botKey.toLowerCase(Locale.ROOT), new BotEntry(pos1, pos2, active));
                loaded++;
            }
        }
        if (loaded > 0)
            FppLogger.info("Loaded " + loaded + " mine selection(s) from " + FILE_NAME + ".");
    }

    public void save() {
        if (file == null) file = new File(plugin.getDataFolder(), "data/" + FILE_NAME);
        file.getParentFile().mkdirs();

        YamlConfiguration yaml = new YamlConfiguration();
        for (Map.Entry<String, BotEntry> e : entries.entrySet()) {
            String k = e.getKey();
            BotEntry entry = e.getValue();
            writeLoc(yaml, k + ".pos1", entry.pos1());
            writeLoc(yaml, k + ".pos2", entry.pos2());
            yaml.set(k + ".active", entry.active());
        }
        try {
            yaml.save(file);
        } catch (IOException ex) {
            FppLogger.warn("Could not save " + FILE_NAME + ": " + ex.getMessage());
        }
    }

    // ── Public API ────────────────────────────────────────────────────────────

    /** Saves pos1 for a bot.  Persists immediately. */
    public void setPos1(String botName, Location pos) {
        String k = botName.toLowerCase(Locale.ROOT);
        BotEntry cur = entries.get(k);
        entries.put(k, new BotEntry(pos.clone(), cur != null ? cur.pos2() : null, cur != null && cur.active()));
        save();
    }

    /** Saves pos2 for a bot.  Persists immediately. */
    public void setPos2(String botName, Location pos) {
        String k = botName.toLowerCase(Locale.ROOT);
        BotEntry cur = entries.get(k);
        entries.put(k, new BotEntry(cur != null ? cur.pos1() : null, pos.clone(), cur != null && cur.active()));
        save();
    }

    /** Returns the saved pos1, or {@code null} if not set. */
    public Location getPos1(String botName) {
        BotEntry e = entries.get(botName.toLowerCase(Locale.ROOT));
        return e != null ? e.pos1() : null;
    }

    /** Returns the saved pos2, or {@code null} if not set. */
    public Location getPos2(String botName) {
        BotEntry e = entries.get(botName.toLowerCase(Locale.ROOT));
        return e != null ? e.pos2() : null;
    }

    /**
     * Returns {@code true} if both pos1 and pos2 are set for this bot
     * and both reference loaded worlds.
     */
    public boolean hasCompleteSelection(String botName) {
        BotEntry e = entries.get(botName.toLowerCase(Locale.ROOT));
        return e != null && e.pos1() != null && e.pos2() != null
                && e.pos1().getWorld() != null && e.pos2().getWorld() != null;
    }

    /** Marks whether an area job was actively running when the server stopped. */
    public void setActive(String botName, boolean active) {
        String k = botName.toLowerCase(Locale.ROOT);
        BotEntry cur = entries.get(k);
        if (cur == null) return;
        entries.put(k, new BotEntry(cur.pos1(), cur.pos2(), active));
        save();
    }

    /** Returns {@code true} if an area job was running when the server stopped. */
    public boolean isActive(String botName) {
        BotEntry e = entries.get(botName.toLowerCase(Locale.ROOT));
        return e != null && e.active();
    }

    /** Removes the selection for this bot (called on despawn / rename). */
    public void clearSelection(String botName) {
        if (entries.remove(botName.toLowerCase(Locale.ROOT)) != null) save();
    }

    // ── Helpers ───────────────────────────────────────────────────────────────

    private static Location readLoc(ConfigurationSection parent, String key) {
        ConfigurationSection sec = parent.getConfigurationSection(key);
        if (sec == null) return null;
        String worldName = sec.getString("world");
        if (worldName == null) return null;
        World world = Bukkit.getWorld(worldName);
        if (world == null) return null;
        return new Location(world,
                sec.getDouble("x"), sec.getDouble("y"), sec.getDouble("z"));
    }

    private static void writeLoc(YamlConfiguration yaml, String path, Location loc) {
        if (loc == null || loc.getWorld() == null) return;
        yaml.set(path + ".world", loc.getWorld().getName());
        yaml.set(path + ".x", loc.getBlockX());
        yaml.set(path + ".y", loc.getBlockY());
        yaml.set(path + ".z", loc.getBlockZ());
    }

    // ── Inner record ─────────────────────────────────────────────────────────

    public record BotEntry(Location pos1, Location pos2, boolean active) {}
}

