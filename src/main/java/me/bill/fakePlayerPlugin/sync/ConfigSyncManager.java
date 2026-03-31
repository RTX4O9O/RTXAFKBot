package me.bill.fakePlayerPlugin.sync;

import me.bill.fakePlayerPlugin.FakePlayerPlugin;
import me.bill.fakePlayerPlugin.config.Config;
import me.bill.fakePlayerPlugin.database.DatabaseManager;
import me.bill.fakePlayerPlugin.util.FppLogger;
import org.bukkit.Bukkit;
import org.bukkit.configuration.file.YamlConfiguration;

import java.io.*;
import java.nio.file.Files;
import java.nio.file.StandardCopyOption;
import java.sql.*;
import java.time.Instant;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Manages configuration file synchronization across proxy networks.
 *
 * <h3>Features</h3>
 * <ul>
 *   <li><b>Central storage</b> — Configs stored in MySQL database</li>
 *   <li><b>Push/Pull</b> — Manually sync configs between servers</li>
 *   <li><b>Auto-sync</b> — Optional automatic pulling on startup/reload</li>
 *   <li><b>Server overrides</b> — Per-server settings that never sync</li>
 *   <li><b>Conflict detection</b> — Prevents accidental overwrites</li>
 * </ul>
 *
 * <h3>Sync Modes</h3>
 * <ul>
 *   <li>{@code DISABLED} — No syncing (default for LOCAL mode)</li>
 *   <li>{@code MANUAL} — Only sync via commands</li>
 *   <li>{@code AUTO_PULL} — Pull latest on startup/reload</li>
 *   <li>{@code AUTO_PUSH} — Push changes automatically</li>
 * </ul>
 *
 * <h3>Server-Specific Settings</h3>
 * These keys are NEVER synced (always server-local):
 * <ul>
 *   <li>{@code database.server-id} (preferred) and legacy {@code server.id}</li>
 *   <li>{@code database.mysql.*} (credentials are server-specific)</li>
 *   <li>{@code debug} (per-server debug toggle)</li>
 * </ul>
 */
public final class ConfigSyncManager {

    private final FakePlayerPlugin plugin;
    private final DatabaseManager  db;

    /** Cache of known config versions per config file. */
    private final Map<String, Long> knownVersions = new ConcurrentHashMap<>();

    /** Files that can be synced. */
    private static final List<String> SYNCABLE_FILES = List.of(
            "config.yml",
            "bot-names.yml",
            "bot-messages.yml",
            "language/en.yml"
    );

    /** Keys in config.yml that should NEVER be synced (server-specific). */
    private static final Set<String> SERVER_SPECIFIC_KEYS = Set.of(
            "database.server-id",
            "server.id",
            "database.mysql.host",
            "database.mysql.port",
            "database.mysql.database",
            "database.mysql.username",
            "database.mysql.password",
            "database.mysql.use-ssl",
            "database.mysql.pool-size",
            "database.mysql.connection-timeout",
            "debug"
    );

    public ConfigSyncManager(FakePlayerPlugin plugin, DatabaseManager db) {
        this.plugin = plugin;
        this.db = db;
    }

    /**
     * Initializes the config sync system. Creates database tables if needed.
     * Should be called during plugin startup after database connection.
     */
    public void init() {
        if (db == null || !Config.isNetworkMode()) {
            Config.debugConfigSync("[ConfigSync] Disabled (not in NETWORK mode or DB unavailable).");
            return;
        }

        createTables();
        Config.debugConfigSync("[ConfigSync] Initialized. Mode: " + Config.configSyncMode());

        // Auto-pull on startup if enabled
        if (Config.configSyncMode().equalsIgnoreCase("AUTO_PULL") 
                || Config.configSyncMode().equalsIgnoreCase("AUTO_PUSH")) {
            Bukkit.getScheduler().runTaskLater(plugin, () -> {
                try {
                    pullAll(true); // silent = true (no chat spam)
                    Config.debugConfigSync("[ConfigSync] Auto-pulled latest configs from network.");
                } catch (Exception e) {
                    FppLogger.warn("[ConfigSync] Auto-pull failed: " + e.getMessage());
                }
            }, 40L); // 2 seconds after startup
        }
    }

    /**
     * Creates the {@code fpp_config_sync} table if it doesn't exist.
     */
    private void createTables() {
        String sql = db.isMysql()
                ? "CREATE TABLE IF NOT EXISTS fpp_config_sync ("
                + "  id            BIGINT NOT NULL AUTO_INCREMENT PRIMARY KEY,"
                + "  config_file   VARCHAR(128) NOT NULL,"
                + "  server_id     VARCHAR(64)  NOT NULL,"
                + "  content_hash  VARCHAR(64)  NOT NULL,"
                + "  content       LONGTEXT     NOT NULL,"
                + "  pushed_at     BIGINT       NOT NULL,"
                + "  pushed_by     VARCHAR(64)  NOT NULL,"
                + "  INDEX idx_config_file (config_file),"
                + "  INDEX idx_server_id (server_id),"
                + "  INDEX idx_pushed_at (pushed_at)"
                + ")"
                : "CREATE TABLE IF NOT EXISTS fpp_config_sync ("
                + "  id            INTEGER PRIMARY KEY AUTOINCREMENT,"
                + "  config_file   VARCHAR(128) NOT NULL,"
                + "  server_id     VARCHAR(64)  NOT NULL,"
                + "  content_hash  VARCHAR(64)  NOT NULL,"
                + "  content       TEXT         NOT NULL,"
                + "  pushed_at     BIGINT       NOT NULL,"
                + "  pushed_by     VARCHAR(64)  NOT NULL"
                + ")";

        try (Connection conn = db.getConnection();
             Statement stmt = conn.createStatement()) {
            stmt.execute(sql);
            Config.debugConfigSync("[ConfigSync] Database table created/verified.");
        } catch (SQLException e) {
            FppLogger.error("[ConfigSync] Failed to create table: " + e.getMessage());
        }
    }

    // ── Push (Upload) ─────────────────────────────────────────────────────────

    /**
     * Pushes a single config file to the network.
     *
     * @param fileName relative path (e.g., "config.yml", "language/en.yml")
     * @param pushedBy who triggered the push (player name or "AUTO")
     * @return {@code true} if successful
     */
    public boolean push(String fileName, String pushedBy) {
        if (!SYNCABLE_FILES.contains(fileName)) {
            FppLogger.warn("[ConfigSync] File '" + fileName + "' is not syncable.");
            return false;
        }

        File file = new File(plugin.getDataFolder(), fileName);
        if (!file.exists()) {
            FppLogger.warn("[ConfigSync] File not found: " + fileName);
            return false;
        }

        try {
            String content = readFile(file);
            
            // If config.yml, strip server-specific keys
            if (fileName.equals("config.yml")) {
                content = stripServerSpecificKeys(content);
            }

            String hash = computeHash(content);
            long now = Instant.now().toEpochMilli();

            String sql = "INSERT INTO fpp_config_sync (config_file, server_id, content_hash, content, pushed_at, pushed_by) "
                    + "VALUES (?, ?, ?, ?, ?, ?)";

            try (Connection conn = db.getConnection();
                 PreparedStatement ps = conn.prepareStatement(sql)) {
                ps.setString(1, fileName);
                ps.setString(2, Config.serverId());
                ps.setString(3, hash);
                ps.setString(4, content);
                ps.setLong(5, now);
                ps.setString(6, pushedBy);
                ps.executeUpdate();
            }

            knownVersions.put(fileName, now);
            FppLogger.info("Config sync: pushed '" + fileName + "'.");
            return true;

        } catch (Exception e) {
            FppLogger.error("[ConfigSync] Push failed for '" + fileName + "': " + e.getMessage());
            return false;
        }
    }

    /**
     * Pushes all syncable configs to the network.
     *
     * @param pushedBy who triggered the push
     * @return number of files successfully pushed
     */
    public int pushAll(String pushedBy) {
        int count = 0;
        for (String fileName : SYNCABLE_FILES) {
            if (push(fileName, pushedBy)) count++;
        }
        return count;
    }

    // ── Pull (Download) ───────────────────────────────────────────────────────

    /**
     * Pulls the latest version of a config file from the network.
     *
     * @param fileName relative path
     * @param silent   if {@code true}, don't log to console
     * @return {@code true} if successful
     */
    public boolean pull(String fileName, boolean silent) {
        if (!SYNCABLE_FILES.contains(fileName)) {
            if (!silent) FppLogger.warn("[ConfigSync] File '" + fileName + "' is not syncable.");
            return false;
        }

        try {
            // Get latest version from any server
            String sql = "SELECT content, content_hash, pushed_at, server_id, pushed_by "
                    + "FROM fpp_config_sync "
                    + "WHERE config_file = ? "
                    + "ORDER BY pushed_at DESC LIMIT 1";

            String content = null;
            String hash = null;
            long pushedAt = 0;
            String sourceServerId = null;
            String pushedBy = null;

            try (Connection conn = db.getConnection();
                 PreparedStatement ps = conn.prepareStatement(sql)) {
                ps.setString(1, fileName);
                try (ResultSet rs = ps.executeQuery()) {
                    if (rs.next()) {
                        content = rs.getString("content");
                        hash = rs.getString("content_hash");
                        pushedAt = rs.getLong("pushed_at");
                        sourceServerId = rs.getString("server_id");
                        pushedBy = rs.getString("pushed_by");
                    }
                }
            }

            if (content == null) {
                if (!silent) FppLogger.info("Config sync: no network version found for '" + fileName + "'.");
                return false;
            }

            // Check if we already have this version
            Long known = knownVersions.get(fileName);
            if (known != null && known >= pushedAt) {
                if (!silent) Config.debugConfigSync("[ConfigSync] Already have latest version of '" + fileName + "'.");
                return false;
            }

            // Backup current file
            File file = new File(plugin.getDataFolder(), fileName);
            if (file.exists()) {
                File backup = new File(plugin.getDataFolder(), fileName + ".sync-backup");
                Files.copy(file.toPath(), backup.toPath(), StandardCopyOption.REPLACE_EXISTING);
            }

            // If config.yml, merge with local server-specific keys
            if (fileName.equals("config.yml")) {
                content = mergeServerSpecificKeys(content, file);
            }

            // Write new content
            file.getParentFile().mkdirs();
            try (FileWriter writer = new FileWriter(file)) {
                writer.write(content);
            }

            knownVersions.put(fileName, pushedAt);
            if (!silent) {
                FppLogger.info("Config sync: pulled '" + fileName + "' from " + sourceServerId
                        + " (pushed by " + pushedBy + ").");
            }
            return true;

        } catch (Exception e) {
            FppLogger.error("[ConfigSync] Pull failed for '" + fileName + "': " + e.getMessage());
            return false;
        }
    }

    /**
     * Pulls all syncable configs from the network.
     *
     * @param silent if {@code true}, don't log to console
     * @return number of files successfully pulled
     */
    public int pullAll(boolean silent) {
        int count = 0;
        for (String fileName : SYNCABLE_FILES) {
            if (pull(fileName, silent)) count++;
        }
        return count;
    }

    // ── Status & Info ─────────────────────────────────────────────────────────

    /**
     * Returns sync status for a config file.
     *
     * @param fileName relative path
     * @return status info or {@code null} if not found
     */
    public SyncStatus getStatus(String fileName) {
        try {
            String sql = "SELECT content_hash, pushed_at, server_id, pushed_by "
                    + "FROM fpp_config_sync "
                    + "WHERE config_file = ? "
                    + "ORDER BY pushed_at DESC LIMIT 1";

            try (Connection conn = db.getConnection();
                 PreparedStatement ps = conn.prepareStatement(sql)) {
                ps.setString(1, fileName);
                try (ResultSet rs = ps.executeQuery()) {
                    if (rs.next()) {
                        return new SyncStatus(
                                fileName,
                                rs.getString("content_hash"),
                                rs.getLong("pushed_at"),
                                rs.getString("server_id"),
                                rs.getString("pushed_by")
                        );
                    }
                }
            }
        } catch (SQLException e) {
            FppLogger.error("[ConfigSync] Status check failed: " + e.getMessage());
        }
        return null;
    }

    /**
     * Returns status for all syncable files.
     */
    public List<SyncStatus> getAllStatus() {
        List<SyncStatus> list = new ArrayList<>();
        for (String fileName : SYNCABLE_FILES) {
            SyncStatus status = getStatus(fileName);
            if (status != null) list.add(status);
        }
        return list;
    }

    /**
     * Checks if local file differs from network version.
     *
     * @param fileName relative path
     * @return {@code true} if local differs from network
     */
    public boolean hasLocalChanges(String fileName) {
        File file = new File(plugin.getDataFolder(), fileName);
        if (!file.exists()) return false;

        try {
            String localContent = readFile(file);
            if (fileName.equals("config.yml")) {
                localContent = stripServerSpecificKeys(localContent);
            }
            String localHash = computeHash(localContent);

            SyncStatus status = getStatus(fileName);
            if (status == null) return true; // No network version

            return !localHash.equals(status.hash);

        } catch (IOException e) {
            return false;
        }
    }

    // ── Helpers ───────────────────────────────────────────────────────────────

    private String readFile(File file) throws IOException {
        StringBuilder sb = new StringBuilder();
        try (BufferedReader reader = new BufferedReader(new FileReader(file))) {
            String line;
            while ((line = reader.readLine()) != null) {
                sb.append(line).append("\n");
            }
        }
        return sb.toString();
    }

    /**
     * Strips server-specific keys from config.yml content before pushing.
     */
    private String stripServerSpecificKeys(String yamlContent) {
        try {
            YamlConfiguration yaml = new YamlConfiguration();
            yaml.loadFromString(yamlContent);

            for (String key : SERVER_SPECIFIC_KEYS) {
                yaml.set(key, null);
            }

            return yaml.saveToString();
        } catch (Exception e) {
            FppLogger.warn("[ConfigSync] Failed to strip keys: " + e.getMessage());
            return yamlContent;
        }
    }

    /**
     * Merges pulled config with local server-specific keys.
     */
    private String mergeServerSpecificKeys(String networkContent, File localFile) {
        try {
            // Load network config
            YamlConfiguration networkYaml = new YamlConfiguration();
            networkYaml.loadFromString(networkContent);

            // Load local config (if exists)
            if (localFile.exists()) {
                YamlConfiguration localYaml = YamlConfiguration.loadConfiguration(localFile);

                // Preserve local server-specific keys
                for (String key : SERVER_SPECIFIC_KEYS) {
                    Object localValue = localYaml.get(key);
                    if (localValue != null) {
                        networkYaml.set(key, localValue);
                    }
                }
            }

            return networkYaml.saveToString();
        } catch (Exception e) {
            FppLogger.warn("[ConfigSync] Failed to merge keys: " + e.getMessage());
            return networkContent;
        }
    }

    /**
     * Computes SHA-256 hash of content.
     */
    private String computeHash(String content) {
        try {
            java.security.MessageDigest md = java.security.MessageDigest.getInstance("SHA-256");
            byte[] hash = md.digest(content.getBytes(java.nio.charset.StandardCharsets.UTF_8));
            StringBuilder hexString = new StringBuilder();
            for (byte b : hash) {
                String hex = Integer.toHexString(0xff & b);
                if (hex.length() == 1) hexString.append('0');
                hexString.append(hex);
            }
            return hexString.toString();
        } catch (Exception e) {
            return "error";
        }
    }

    /**
     * Sync status for a config file.
     */
    public record SyncStatus(
            String fileName,
            String hash,
            long pushedAt,
            String serverId,
            String pushedBy
    ) {
        public String shortHash() {
            return hash.length() > 8 ? hash.substring(0, 8) : hash;
        }

        public String formattedTime() {
            Instant instant = Instant.ofEpochMilli(pushedAt);
            return instant.toString();
        }
    }
}

