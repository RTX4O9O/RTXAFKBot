package me.bill.fakePlayerPlugin.command;

import java.io.File;
import java.io.IOException;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import me.bill.fakePlayerPlugin.util.BotDataYaml;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.plugin.java.JavaPlugin;

public final class WaypointStore {

  private static final String ROOT = "waypoints.routes";

  private final JavaPlugin plugin;

  private final Map<String, List<Location>> routes = new ConcurrentHashMap<>();
  private File file;

  public WaypointStore(JavaPlugin plugin) {
    this.plugin = plugin;
  }

  public void load() {
    file = new File(plugin.getDataFolder(), "data/waypoints.yml");
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
                for (String routeName : migrated.getKeys(false)) {
                  ConfigurationSection routeSection = migrated.getConfigurationSection(routeName);
                  if (routeSection == null) continue;
                  for (String i : routeSection.getKeys(false)) {
                    ConfigurationSection pos = routeSection.getConfigurationSection(i);
                    if (pos == null) continue;
                    for (String key : pos.getKeys(false)) {
                      section.set(routeName + "." + i + "." + key, pos.get(key));
                    }
                  }
                }
              });
          if (file.exists()) file.delete();
        } catch (IOException ex) {
          plugin.getLogger().warning("[FPP] Failed to migrate waypoints.yml: " + ex.getMessage());
        }
      }
    }
    if (root == null) return;

    int routeCount = 0, posCount = 0;

    for (String routeName : root.getKeys(false)) {
      ConfigurationSection routeSection = root.getConfigurationSection(routeName);
      if (routeSection == null) continue;

      List<Location> positions = new ArrayList<>();

      int i = 0;
      while (routeSection.contains(String.valueOf(i))) {
        ConfigurationSection pos = routeSection.getConfigurationSection(String.valueOf(i));
        if (pos != null) {
          String worldName = pos.getString("world", "world");
          double x = pos.getDouble("x");
          double y = pos.getDouble("y");
          double z = pos.getDouble("z");
          float yaw = (float) pos.getDouble("yaw");
          float pitch = (float) pos.getDouble("pitch");
          World w = Bukkit.getWorld(worldName != null ? worldName : "world");
          if (w != null) {
            positions.add(new Location(w, x, y, z, yaw, pitch));
            posCount++;
          }
        }
        i++;
      }
      if (!positions.isEmpty()) {
        routes.put(routeName.toLowerCase(), positions);
        routeCount++;
      }
    }
    if (routeCount > 0) {
      plugin
          .getLogger()
          .info(
              "[FPP] Loaded "
                  + routeCount
                  + " waypoint route(s) with "
                  + posCount
                  + " total position(s).");
    }
  }

  public void save() {
    try {
      BotDataYaml.replaceSection(
          plugin,
          ROOT,
          section -> {
            for (Map.Entry<String, List<Location>> e : routes.entrySet()) {
              String routeName = e.getKey();
              List<Location> positions = e.getValue();
              for (int i = 0; i < positions.size(); i++) {
                Location loc = positions.get(i);
                String prefix = routeName + "." + i + ".";
                section.set(prefix + "world", loc.getWorld() != null ? loc.getWorld().getName() : "world");
                section.set(prefix + "x", loc.getX());
                section.set(prefix + "y", loc.getY());
                section.set(prefix + "z", loc.getZ());
                section.set(prefix + "yaw", (double) loc.getYaw());
                section.set(prefix + "pitch", (double) loc.getPitch());
              }
            }
          });
      if (file != null && file.exists()) file.delete();
    } catch (IOException ex) {
      plugin.getLogger().warning("[FPP] Could not save " + BotDataYaml.FILE_NAME + " waypoints section: " + ex.getMessage());
    }
  }

  public boolean createRoute(String name) {
    String key = name.toLowerCase();
    if (routes.containsKey(key)) return false;
    routes.put(key, new ArrayList<>());

    return true;
  }

  public int addPos(String name, Location loc) {
    List<Location> positions = routes.computeIfAbsent(name.toLowerCase(), k -> new ArrayList<>());
    positions.add(loc.clone());
    save();
    return positions.size();
  }

  public boolean removePos(String name, int index) {
    List<Location> positions = routes.get(name.toLowerCase());
    if (positions == null || index < 0 || index >= positions.size()) return false;
    positions.remove(index);
    if (positions.isEmpty()) routes.remove(name.toLowerCase());
    save();
    return true;
  }

  public boolean delete(String name) {
    boolean removed = routes.remove(name.toLowerCase()) != null;
    if (removed) save();
    return removed;
  }

  public boolean clear(String name) {
    List<Location> positions = routes.remove(name.toLowerCase());
    boolean had = positions != null && !positions.isEmpty();
    if (had) save();
    return had;
  }

  public List<Location> getRoute(String name) {
    List<Location> positions = routes.get(name.toLowerCase());
    if (positions == null || positions.isEmpty()) return null;
    return Collections.unmodifiableList(new ArrayList<>(positions));
  }

  public boolean exists(String name) {
    List<Location> positions = routes.get(name.toLowerCase());
    return positions != null && !positions.isEmpty();
  }

  public boolean hasRoute(String name) {
    return routes.containsKey(name.toLowerCase());
  }

  public Set<String> getNames() {
    return Collections.unmodifiableSet(new TreeSet<>(routes.keySet()));
  }

  public int size() {
    return routes.size();
  }

  public int getPositionCount(String name) {
    List<Location> positions = routes.get(name.toLowerCase());
    return positions != null ? positions.size() : 0;
  }
}
