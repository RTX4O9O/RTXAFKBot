package me.bill.fakePlayerPlugin.fakeplayer;

import java.io.File;
import java.io.IOException;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import me.bill.fakePlayerPlugin.FakePlayerPlugin;
import me.bill.fakePlayerPlugin.config.Config;
import me.bill.fakePlayerPlugin.database.DatabaseManager;
import me.bill.fakePlayerPlugin.util.BotDataYaml;
import me.bill.fakePlayerPlugin.util.FppLogger;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.YamlConfiguration;

public final class BotIdentityCache {

  private static final String YAML_FILE = "bot-identities.yml";
  private static final String ROOT = "identities.by-name";

  private final FakePlayerPlugin pluginRef;
  private final DatabaseManager db;
  private final File yamlFile;

  private final Map<String, UUID> cache = new ConcurrentHashMap<>();

  private YamlConfiguration yamlConfig = null;

  public BotIdentityCache(FakePlayerPlugin plugin, DatabaseManager db) {
    this.pluginRef = plugin;
    this.db = db;
    this.yamlFile = new File(new File(plugin.getDataFolder(), "data"), YAML_FILE);

    if (db == null) {

      loadYaml();
    }
  }

  public UUID lookupOrCreate(String botName) {
    String key = botName.toLowerCase();

    UUID cached = cache.get(key);
    if (cached != null) return cached;

    UUID resolved;
    if (db != null) {
      resolved = lookupOrCreateDb(botName);
    } else {
      resolved = lookupOrCreateYaml(botName, key);
    }

    cache.put(key, resolved);
    return resolved;
  }

  public void prime(String botName, UUID uuid) {
    if (botName == null || uuid == null) return;
    cache.put(botName.toLowerCase(), uuid);
  }

  private UUID lookupOrCreateDb(String botName) {
    String serverId = Config.serverId();

    UUID fromDb = db.lookupBotUuid(botName, serverId);
    if (fromDb != null) {
      Config.debugDatabase("BotIdentityCache: DB hit for '" + botName + "' → " + fromDb);
      return fromDb;
    }

    UUID fresh = UUID.randomUUID();
    db.registerBotUuid(botName, fresh, serverId);
    Config.debugDatabase("BotIdentityCache: new identity for '" + botName + "' → " + fresh);
    return fresh;
  }

  private void loadYaml() {
    yamlConfig = BotDataYaml.load(pluginRef);
    ConfigurationSection root = yamlConfig.getConfigurationSection(ROOT);
    if (root == null && yamlFile.exists()) {
      YamlConfiguration legacy = YamlConfiguration.loadConfiguration(yamlFile);
      if (!legacy.getKeys(false).isEmpty()) {
        root = legacy;
        try {
          BotDataYaml.replaceSection(
              pluginRef,
              ROOT,
              section -> {
                for (String key : legacy.getKeys(false)) {
                  section.set(key, legacy.getString(key));
                }
              });
          if (yamlFile.exists()) yamlFile.delete();
          yamlConfig = BotDataYaml.load(pluginRef);
          root = yamlConfig.getConfigurationSection(ROOT);
        } catch (IOException e) {
          FppLogger.warn("BotIdentityCache: failed to migrate " + YAML_FILE + ": " + e.getMessage());
        }
      }
    }
    if (root == null) {
      yamlConfig = new YamlConfiguration();
      Config.debugDatabase("BotIdentityCache: no YAML data yet - will create on first spawn.");
      return;
    }
    int loaded = 0;
    for (String key : root.getKeys(false)) {
      String val = root.getString(key);
      if (val == null || val.isBlank()) continue;
      try {
        cache.put(key, UUID.fromString(val));
        loaded++;
      } catch (IllegalArgumentException e) {
        FppLogger.warn("BotIdentityCache: skipping malformed entry '" + key + "': " + val);
      }
    }
    if (loaded > 0) {
      FppLogger.info(
          "BotIdentityCache: loaded " + loaded + " name→UUID mapping(s) from " + YAML_FILE + ".");
    }
  }

  private UUID lookupOrCreateYaml(String botName, String cacheKey) {
    if (yamlConfig == null) yamlConfig = new YamlConfiguration();

    String stored = yamlConfig.getString(ROOT + "." + cacheKey);
    if (stored != null && !stored.isBlank()) {
      try {
        UUID fromYaml = UUID.fromString(stored);
        Config.debugDatabase("BotIdentityCache: YAML hit for '" + botName + "' → " + fromYaml);
        return fromYaml;
      } catch (IllegalArgumentException e) {
        FppLogger.warn(
            "BotIdentityCache: malformed YAML entry for '" + botName + "' - regenerating UUID.");
      }
    }

    UUID fresh = UUID.randomUUID();
    yamlConfig.set(ROOT + "." + cacheKey, fresh.toString());
    saveYaml();
    Config.debugDatabase("BotIdentityCache: new YAML identity for '" + botName + "' → " + fresh);
    return fresh;
  }

  private void saveYaml() {
    try {

      File parent = yamlFile.getParentFile();
      if (parent != null && !parent.exists()) parent.mkdirs();
      BotDataYaml.save(pluginRef, yamlConfig);
      if (yamlFile.exists()) yamlFile.delete();
    } catch (IOException e) {
      FppLogger.warn("BotIdentityCache: failed to save " + YAML_FILE + ": " + e.getMessage());
    }
  }
}
