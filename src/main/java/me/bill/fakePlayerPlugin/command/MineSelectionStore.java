package me.bill.fakePlayerPlugin.command;

import java.io.File;
import java.io.IOException;
import java.util.Locale;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import me.bill.fakePlayerPlugin.util.BotDataYaml;
import me.bill.fakePlayerPlugin.util.FppLogger;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.plugin.java.JavaPlugin;

public final class MineSelectionStore {

  private static final String FILE_NAME = "mine-selections.yml";
  private static final String ROOT = "mine-selections.by-bot";

  private final JavaPlugin plugin;

  private final Map<String, BotEntry> entries = new ConcurrentHashMap<>();
  private File file;

  public MineSelectionStore(JavaPlugin plugin) {
    this.plugin = plugin;
  }

  public void load() {
    file = new File(plugin.getDataFolder(), "data/" + FILE_NAME);
    YamlConfiguration unified = BotDataYaml.load(plugin);
    ConfigurationSection root = unified.getConfigurationSection(ROOT);
    if (root == null && file.exists()) {
      YamlConfiguration legacy = YamlConfiguration.loadConfiguration(file);
      root = legacy;
      if (!legacy.getKeys(false).isEmpty()) {
        final YamlConfiguration migrated = legacy;
        try {
          BotDataYaml.replaceSection(
              plugin,
              ROOT,
              section -> {
                for (String botKey : migrated.getKeys(false)) {
                  ConfigurationSection sec = migrated.getConfigurationSection(botKey);
                  if (sec == null) continue;
                  for (String key : sec.getKeys(false)) {
                    Object val = sec.get(key);
                    if (val instanceof ConfigurationSection locSec) {
                      for (String lk : locSec.getKeys(false)) {
                        section.set(botKey + "." + key + "." + lk, locSec.get(lk));
                      }
                    } else {
                      section.set(botKey + "." + key, val);
                    }
                  }
                }
              });
          if (file.exists()) file.delete();
        } catch (IOException ex) {
          FppLogger.warn("Failed to migrate " + FILE_NAME + " to " + BotDataYaml.FILE_NAME + ": " + ex.getMessage());
        }
      }
    }
    if (root == null) return;

    int loaded = 0;
    for (String botKey : root.getKeys(false)) {
      ConfigurationSection sec = root.getConfigurationSection(botKey);
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
    try {
      BotDataYaml.replaceSection(
          plugin,
          ROOT,
          section -> {
            for (Map.Entry<String, BotEntry> e : entries.entrySet()) {
              String k = e.getKey();
              BotEntry entry = e.getValue();
              writeLoc(section, k + ".pos1", entry.pos1());
              writeLoc(section, k + ".pos2", entry.pos2());
              section.set(k + ".active", entry.active());
            }
          });
      if (file != null && file.exists()) file.delete();
    } catch (IOException ex) {
      FppLogger.warn("Could not save " + FILE_NAME + ": " + ex.getMessage());
    }
  }

  public void setPos1(String botName, Location pos) {
    String k = botName.toLowerCase(Locale.ROOT);
    BotEntry cur = entries.get(k);
    entries.put(
        k, new BotEntry(pos.clone(), cur != null ? cur.pos2() : null, cur != null && cur.active()));
    save();
  }

  public void setPos2(String botName, Location pos) {
    String k = botName.toLowerCase(Locale.ROOT);
    BotEntry cur = entries.get(k);
    entries.put(
        k, new BotEntry(cur != null ? cur.pos1() : null, pos.clone(), cur != null && cur.active()));
    save();
  }

  public Location getPos1(String botName) {
    BotEntry e = entries.get(botName.toLowerCase(Locale.ROOT));
    return e != null ? e.pos1() : null;
  }

  public Location getPos2(String botName) {
    BotEntry e = entries.get(botName.toLowerCase(Locale.ROOT));
    return e != null ? e.pos2() : null;
  }

  public boolean hasCompleteSelection(String botName) {
    BotEntry e = entries.get(botName.toLowerCase(Locale.ROOT));
    return e != null
        && e.pos1() != null
        && e.pos2() != null
        && e.pos1().getWorld() != null
        && e.pos2().getWorld() != null;
  }

  public void setActive(String botName, boolean active) {
    String k = botName.toLowerCase(Locale.ROOT);
    BotEntry cur = entries.get(k);
    if (cur == null) return;
    entries.put(k, new BotEntry(cur.pos1(), cur.pos2(), active));
    save();
  }

  public boolean isActive(String botName) {
    BotEntry e = entries.get(botName.toLowerCase(Locale.ROOT));
    return e != null && e.active();
  }

  public void clearSelection(String botName) {
    if (entries.remove(botName.toLowerCase(Locale.ROOT)) != null) save();
  }

  private static Location readLoc(ConfigurationSection parent, String key) {
    ConfigurationSection sec = parent.getConfigurationSection(key);
    if (sec == null) return null;
    String worldName = sec.getString("world");
    if (worldName == null) return null;
    World world = Bukkit.getWorld(worldName);
    if (world == null) return null;
    return new Location(world, sec.getDouble("x"), sec.getDouble("y"), sec.getDouble("z"));
  }

  private static void writeLoc(ConfigurationSection yaml, String path, Location loc) {
    if (loc == null || loc.getWorld() == null) return;
    yaml.set(path + ".world", loc.getWorld().getName());
    yaml.set(path + ".x", loc.getBlockX());
    yaml.set(path + ".y", loc.getBlockY());
    yaml.set(path + ".z", loc.getBlockZ());
  }

  public record BotEntry(Location pos1, Location pos2, boolean active) {}
}
