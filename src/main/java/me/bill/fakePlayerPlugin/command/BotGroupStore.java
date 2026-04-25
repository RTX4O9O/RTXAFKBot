package me.bill.fakePlayerPlugin.command;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.UUID;
import me.bill.fakePlayerPlugin.FakePlayerPlugin;
import me.bill.fakePlayerPlugin.fakeplayer.FakePlayer;
import me.bill.fakePlayerPlugin.fakeplayer.FakePlayerManager;
import me.bill.fakePlayerPlugin.util.BotAccess;
import me.bill.fakePlayerPlugin.util.FppLogger;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.Player;

public final class BotGroupStore {

  public static final String DEFAULT_GROUP = "default";

  private final FakePlayerPlugin plugin;
  private final FakePlayerManager manager;
  private final File file;
  private YamlConfiguration yaml;

  public BotGroupStore(FakePlayerPlugin plugin, FakePlayerManager manager) {
    this.plugin = plugin;
    this.manager = manager;
    this.file = new File(plugin.getDataFolder(), "data/bot-groups.yml");
  }

  public void load() {
    if (!file.getParentFile().exists()) file.getParentFile().mkdirs();
    yaml = YamlConfiguration.loadConfiguration(file);
  }

  public void save() {
    try {
      yaml.save(file);
    } catch (IOException e) {
      FppLogger.warn("BotGroupStore save failed: " + e.getMessage());
    }
  }

  public List<String> getGroups(Player owner) {
    if (owner == null) return List.of();
    Set<String> names = new LinkedHashSet<>();
    names.add(DEFAULT_GROUP);
    ConfigurationSection sec = yaml.getConfigurationSection(path(owner.getUniqueId()));
    if (sec != null) names.addAll(sec.getKeys(false));
    List<String> out = new ArrayList<>(names);
    out.sort(String.CASE_INSENSITIVE_ORDER);
    return out;
  }

  public boolean create(Player owner, String group) {
    String key = normalize(group);
    if (owner == null || key == null || DEFAULT_GROUP.equals(key)) return false;
    String path = path(owner.getUniqueId()) + "." + key;
    if (yaml.isList(path)) return false;
    yaml.set(path, new ArrayList<String>());
    save();
    return true;
  }

  public boolean delete(Player owner, String group) {
    String key = normalize(group);
    if (owner == null || key == null || DEFAULT_GROUP.equals(key)) return false;
    String path = path(owner.getUniqueId()) + "." + key;
    if (!yaml.contains(path)) return false;
    yaml.set(path, null);
    save();
    return true;
  }

  public boolean add(Player owner, String group, FakePlayer bot) {
    String key = normalize(group);
    if (owner == null || key == null || bot == null || DEFAULT_GROUP.equals(key)) return false;
    if (!BotAccess.canAdminister(owner, bot)) return false;
    String path = path(owner.getUniqueId()) + "." + key;
    List<String> names = new ArrayList<>(yaml.getStringList(path));
    if (names.stream().anyMatch(n -> n.equalsIgnoreCase(bot.getName()))) return false;
    names.add(bot.getName());
    names.sort(String.CASE_INSENSITIVE_ORDER);
    yaml.set(path, names);
    save();
    return true;
  }

  public boolean remove(Player owner, String group, String botName) {
    String key = normalize(group);
    if (owner == null || key == null || botName == null || DEFAULT_GROUP.equals(key)) return false;
    String path = path(owner.getUniqueId()) + "." + key;
    List<String> names = new ArrayList<>(yaml.getStringList(path));
    boolean removed = names.removeIf(n -> n.equalsIgnoreCase(botName));
    if (!removed) return false;
    yaml.set(path, names);
    save();
    return true;
  }

  public List<FakePlayer> resolve(Player owner, String group) {
    if (owner == null) return List.of();
    String key = normalize(group);
    if (key == null) return List.of();
    List<FakePlayer> out = new ArrayList<>();
    if (DEFAULT_GROUP.equals(key) || "owned".equals(key)) {
      for (FakePlayer fp : manager.getActivePlayers()) {
        if (BotAccess.isOwner(owner, fp)) out.add(fp);
      }
    } else {
      for (String botName : yaml.getStringList(path(owner.getUniqueId()) + "." + key)) {
        FakePlayer fp = manager.getByName(botName);
        if (fp != null && BotAccess.canAdminister(owner, fp)) out.add(fp);
      }
    }
    out.sort((a, b) -> String.CASE_INSENSITIVE_ORDER.compare(a.getName(), b.getName()));
    return out;
  }

  public static String normalize(String group) {
    if (group == null) return null;
    String key = group.trim().toLowerCase(Locale.ROOT);
    if (!key.matches("[a-z0-9_-]{1,32}")) return null;
    return key;
  }

  private static String path(UUID owner) {
    return "players." + owner;
  }
}
