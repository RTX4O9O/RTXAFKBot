package me.bill.fakePlayerPlugin.database;

import me.bill.fakePlayerPlugin.config.Config;
import me.bill.fakePlayerPlugin.util.FppLogger;

import java.io.File;
import java.sql.*;
import java.time.Instant;
import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * Handles all database I/O for FakePlayerPlugin.
 *
 * <h3>Backends</h3>
 * <ol>
 *   <li><b>MySQL</b>  — when {@code database.mysql.enabled: true}</li>
 *   <li><b>SQLite</b> — automatic local fallback; WAL mode for crash-safety</li>
 * </ol>
 *
 * <h3>Key features</h3>
 * <ul>
 *   <li>Write queue — all writes are serialised on a dedicated thread.</li>
 *   <li>Batch location flush — position updates are deduplicated and sent in one
 *       batch statement instead of one UPDATE per bot per tick.</li>
 *   <li>Auto-reconnect health check — silently reconnects on connection loss.</li>
 *   <li>{@code fpp_active_bots} table — authoritative source for restart
 *       persistence; always reflects live bot state, even after a crash.</li>
 *   <li>Yaw/pitch tracked in last location — bots face the correct direction
 *       after a restart.</li>
 *   <li>Schema version table — safe incremental migrations.</li>
 *   <li>Indices on hot query columns — bot_name, spawned_by, removed_at.</li>
 *   <li>Display name stored alongside internal name for audit trail.</li>
 *   <li>Stats API — total sessions, unique bots, top spawners, uptime.</li>
 * </ul>
 */
public class DatabaseManager {

    // ── Schema version ────────────────────────────────────────────────────────
    private static final int SCHEMA_VERSION = 5;

    // ── DDL — session history ─────────────────────────────────────────────────
    private static final String CREATE_SESSIONS_SQLITE =
            "CREATE TABLE IF NOT EXISTS fpp_bot_sessions (" +
            "  id              INTEGER PRIMARY KEY AUTOINCREMENT," +
            "  bot_name        VARCHAR(16)  NOT NULL," +
            "  bot_display     VARCHAR(128) DEFAULT NULL," +
            "  bot_uuid        VARCHAR(36)  NOT NULL," +
            "  spawned_by      VARCHAR(16)  NOT NULL," +
            "  spawned_by_uuid VARCHAR(36)  NOT NULL," +
            "  world_name      VARCHAR(64)  NOT NULL," +
            "  spawn_x         DOUBLE NOT NULL," +
            "  spawn_y         DOUBLE NOT NULL," +
            "  spawn_z         DOUBLE NOT NULL," +
            "  spawn_yaw       FLOAT  NOT NULL DEFAULT 0," +
            "  spawn_pitch     FLOAT  NOT NULL DEFAULT 0," +
            "  last_world      VARCHAR(64)," +
            "  last_x          DOUBLE," +
            "  last_y          DOUBLE," +
            "  last_z          DOUBLE," +
            "  last_yaw        FLOAT," +
            "  last_pitch      FLOAT," +
            "  entity_type     VARCHAR(32)  NOT NULL DEFAULT 'MANNEQUIN'," +
            "  spawned_at      BIGINT NOT NULL," +
            "  removed_at      BIGINT," +
            "  remove_reason   VARCHAR(32)" +
            ")";

    private static final String CREATE_SESSIONS_MYSQL =
            "CREATE TABLE IF NOT EXISTS fpp_bot_sessions (" +
            "  id              BIGINT NOT NULL AUTO_INCREMENT PRIMARY KEY," +
            "  bot_name        VARCHAR(16)  NOT NULL," +
            "  bot_display     VARCHAR(128) DEFAULT NULL," +
            "  bot_uuid        VARCHAR(36)  NOT NULL," +
            "  spawned_by      VARCHAR(16)  NOT NULL," +
            "  spawned_by_uuid VARCHAR(36)  NOT NULL," +
            "  world_name      VARCHAR(64)  NOT NULL," +
            "  spawn_x         DOUBLE NOT NULL," +
            "  spawn_y         DOUBLE NOT NULL," +
            "  spawn_z         DOUBLE NOT NULL," +
            "  spawn_yaw       FLOAT  NOT NULL DEFAULT 0," +
            "  spawn_pitch     FLOAT  NOT NULL DEFAULT 0," +
            "  last_world      VARCHAR(64)," +
            "  last_x          DOUBLE," +
            "  last_y          DOUBLE," +
            "  last_z          DOUBLE," +
            "  last_yaw        FLOAT," +
            "  last_pitch      FLOAT," +
            "  entity_type     VARCHAR(32)  NOT NULL DEFAULT 'MANNEQUIN'," +
            "  spawned_at      BIGINT NOT NULL," +
            "  removed_at      BIGINT," +
            "  remove_reason   VARCHAR(32)" +
            ")";

    // ── DDL — active bots (restart persistence source of truth) ───────────────
    private static final String CREATE_ACTIVE_SQLITE =
            "CREATE TABLE IF NOT EXISTS fpp_active_bots (" +
            "  bot_uuid        VARCHAR(36)  NOT NULL PRIMARY KEY," +
            "  bot_name        VARCHAR(16)  NOT NULL," +
            "  bot_display     VARCHAR(128) DEFAULT NULL," +
            "  spawned_by      VARCHAR(16)  NOT NULL," +
            "  spawned_by_uuid VARCHAR(36)  NOT NULL," +
            "  world_name      VARCHAR(64)  NOT NULL," +
            "  pos_x           DOUBLE NOT NULL," +
            "  pos_y           DOUBLE NOT NULL," +
            "  pos_z           DOUBLE NOT NULL," +
            "  pos_yaw         FLOAT  NOT NULL DEFAULT 0," +
            "  pos_pitch       FLOAT  NOT NULL DEFAULT 0," +
            "  updated_at      BIGINT NOT NULL," +
            "  luckperms_group VARCHAR(64)  DEFAULT NULL" +
            ")";

    private static final String CREATE_ACTIVE_MYSQL =
            "CREATE TABLE IF NOT EXISTS fpp_active_bots (" +
            "  bot_uuid        VARCHAR(36)  NOT NULL PRIMARY KEY," +
            "  bot_name        VARCHAR(16)  NOT NULL," +
            "  bot_display     VARCHAR(128) DEFAULT NULL," +
            "  spawned_by      VARCHAR(16)  NOT NULL," +
            "  spawned_by_uuid VARCHAR(36)  NOT NULL," +
            "  world_name      VARCHAR(64)  NOT NULL," +
            "  pos_x           DOUBLE NOT NULL," +
            "  pos_y           DOUBLE NOT NULL," +
            "  pos_z           DOUBLE NOT NULL," +
            "  pos_yaw         FLOAT  NOT NULL DEFAULT 0," +
            "  pos_pitch       FLOAT  NOT NULL DEFAULT 0," +
            "  updated_at      BIGINT NOT NULL," +
            "  luckperms_group VARCHAR(64)  DEFAULT NULL" +
            ")";

    // ── DDL — schema version ──────────────────────────────────────────────────
    private static final String CREATE_META =
            "CREATE TABLE IF NOT EXISTS fpp_meta (" +
            "  key_name VARCHAR(64)  NOT NULL PRIMARY KEY," +
            "  value    VARCHAR(256) NOT NULL" +
            ")";

    // ── Migrations (index = fromVersion - 1) ──────────────────────────────────
    private static final String[][] MIGRATIONS = {
        // v1 → v2: add last_yaw / last_pitch
        { "ALTER TABLE fpp_bot_sessions ADD COLUMN last_yaw   FLOAT",
          "ALTER TABLE fpp_bot_sessions ADD COLUMN last_pitch FLOAT" },
        // v2 → v3: fpp_active_bots already created by CREATE_ACTIVE_* above
        {},
        // v3 → v4: add display name columns + performance indices
        { "ALTER TABLE fpp_bot_sessions ADD COLUMN bot_display VARCHAR(128) DEFAULT NULL",
          "ALTER TABLE fpp_active_bots  ADD COLUMN bot_display VARCHAR(128) DEFAULT NULL",
          "CREATE INDEX IF NOT EXISTS idx_sessions_bot_name    ON fpp_bot_sessions(bot_name)",
          "CREATE INDEX IF NOT EXISTS idx_sessions_spawned_by  ON fpp_bot_sessions(spawned_by)",
          "CREATE INDEX IF NOT EXISTS idx_sessions_removed_at  ON fpp_bot_sessions(removed_at)",
          "CREATE INDEX IF NOT EXISTS idx_sessions_spawned_at  ON fpp_bot_sessions(spawned_at)",
          "CREATE INDEX IF NOT EXISTS idx_sessions_bot_uuid    ON fpp_bot_sessions(bot_uuid)" },
        // v4 → v5: add luckperms_group column for per-bot rank persistence
        { "ALTER TABLE fpp_bot_sessions ADD COLUMN luckperms_group VARCHAR(64) DEFAULT NULL",
          "ALTER TABLE fpp_active_bots  ADD COLUMN luckperms_group VARCHAR(64) DEFAULT NULL" }
    };

    // ── State ─────────────────────────────────────────────────────────────────
    private volatile Connection connection;
    private boolean             isMysql = false;
    private File                dataFolder;

    private final ExecutorService      writer     = Executors.newSingleThreadExecutor(r -> {
        Thread t = new Thread(r, "FPP-DB-Writer"); t.setDaemon(true); return t;
    });
    private final BlockingQueue<Runnable> writeQueue = new LinkedBlockingQueue<>();
    private final AtomicBoolean           running    = new AtomicBoolean(true);

    private final Map<String, BotRecord>       activeRecords    = new ConcurrentHashMap<>();
    private final Map<String, PendingLocation> pendingLocations = new ConcurrentHashMap<>();

    // ── Lifecycle ─────────────────────────────────────────────────────────────

    public boolean init(File pluginDataFolder) {
        this.dataFolder = pluginDataFolder;
        if (!connect()) return false;
        createTables();
        migrate();
        startWriteLoop();
        startHealthCheck();
        return true;
    }

    private boolean connect() {
        if (Config.mysqlEnabled()) {
            if (tryMysql()) { isMysql = true; return true; }
            FppLogger.warn("MySQL connection failed — falling back to SQLite.");
        }
        return trySqlite();
    }

    private boolean tryMysql() {
        try {
            Class.forName("com.mysql.cj.jdbc.Driver");
            int connTimeout = Config.mysqlConnTimeout();
            String url = "jdbc:mysql://" + Config.mysqlHost() + ":" + Config.mysqlPort()
                    + "/" + Config.mysqlDatabase()
                    + "?useSSL=" + Config.mysqlUseSSL()
                    + "&autoReconnect=true&characterEncoding=utf8"
                    + "&connectionTimeout=" + connTimeout
                    + "&socketTimeout=" + (connTimeout * 2);
            connection = DriverManager.getConnection(url, Config.mysqlUsername(), Config.mysqlPassword());
            Config.debug("MySQL pool-size advisory: " + Config.mysqlPoolSize());
            FppLogger.success("Database connected via MySQL ("
                    + Config.mysqlHost() + ":" + Config.mysqlPort()
                    + "/" + Config.mysqlDatabase() + ").");
            return true;
        } catch (Exception e) {
            FppLogger.warn("MySQL init error: " + e.getMessage());
            return false;
        }
    }

    private boolean trySqlite() {
        try {
            Class.forName("org.sqlite.JDBC");
            File dbDir = new File(dataFolder, "data");
            dbDir.mkdirs();
            File dbFile = new File(dbDir, "fpp.db");
            connection = DriverManager.getConnection("jdbc:sqlite:" + dbFile.getAbsolutePath());
            try (Statement st = connection.createStatement()) {
                st.execute("PRAGMA journal_mode=WAL");
                st.execute("PRAGMA synchronous=NORMAL");
                st.execute("PRAGMA foreign_keys=ON");
                st.execute("PRAGMA busy_timeout=5000");
                st.execute("PRAGMA cache_size=-8000");   // 8 MB page cache
                st.execute("PRAGMA temp_store=MEMORY");
            }
            isMysql = false;
            FppLogger.success("Database connected via SQLite (" + dbFile.getPath() + ").");
            return true;
        } catch (Exception e) {
            FppLogger.error("SQLite init error: " + e.getMessage());
            return false;
        }
    }

    private void createTables() {
        exec(isMysql ? CREATE_SESSIONS_MYSQL : CREATE_SESSIONS_SQLITE);
        exec(isMysql ? CREATE_ACTIVE_MYSQL   : CREATE_ACTIVE_SQLITE);
        exec(CREATE_META);
    }

    private void migrate() {
        int current = getSchemaVersion();
        for (int v = current; v < SCHEMA_VERSION; v++) {
            for (String sql : MIGRATIONS[v - 1]) {
                if (!sql.isEmpty()) execSilent(sql);
            }
            setSchemaVersion(v + 1);
            Config.debug("DB migrated v" + v + " → v" + (v + 1));
        }
    }

    private int getSchemaVersion() {
        try (PreparedStatement ps = connection.prepareStatement(
                "SELECT value FROM fpp_meta WHERE key_name='schema_version'");
             ResultSet rs = ps.executeQuery()) {
            if (rs.next()) return Integer.parseInt(rs.getString(1));
        } catch (Exception ignored) {}
        return 1;
    }

    private void setSchemaVersion(int v) {
        String sql = isMysql
                ? "INSERT INTO fpp_meta(key_name,value) VALUES('schema_version',?) ON DUPLICATE KEY UPDATE value=?"
                : "INSERT OR REPLACE INTO fpp_meta(key_name,value) VALUES('schema_version',?)";
        try (PreparedStatement ps = connection.prepareStatement(sql)) {
            ps.setString(1, String.valueOf(v));
            if (isMysql) ps.setString(2, String.valueOf(v));
            ps.executeUpdate();
        } catch (SQLException e) {
            FppLogger.error("DB setSchemaVersion: " + e.getMessage());
        }
    }

    private void startWriteLoop() {
        writer.submit(() -> {
            while (running.get() || !writeQueue.isEmpty()) {
                try {
                    Runnable task = writeQueue.poll(100, TimeUnit.MILLISECONDS);
                    if (task != null) task.run();
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                    break;
                } catch (Exception e) {
                    FppLogger.error("DB write error: " + e.getMessage());
                }
            }
        });
    }

    private void startHealthCheck() {
        Executors.newSingleThreadScheduledExecutor(r -> {
            Thread t = new Thread(r, "FPP-DB-Health"); t.setDaemon(true); return t;
        }).scheduleAtFixedRate(() -> {
            if (!isAlive()) {
                FppLogger.warn("DB connection lost — reconnecting...");
                if (connect()) { createTables(); FppLogger.success("DB reconnected."); }
                else            { FppLogger.error("DB reconnect failed."); }
            }
        }, 60, 60, TimeUnit.SECONDS);
    }

    public void close() {
        running.set(false);
        flushPendingLocations();       // drain pending updates
        writer.shutdown();
        try {
            if (!writer.awaitTermination(8, TimeUnit.SECONDS)) writer.shutdownNow();
        } catch (InterruptedException ignored) { writer.shutdownNow(); }
        try {
            if (connection != null && !connection.isClosed()) connection.close();
            FppLogger.info("Database connection closed.");
        } catch (SQLException e) {
            FppLogger.error("Error closing DB: " + e.getMessage());
        }
    }

    private boolean isAlive() {
        try {
            if (connection == null || connection.isClosed()) return false;
            if (isMysql) {
                try (Statement st = connection.createStatement()) { st.execute("SELECT 1"); }
            }
            return true;
        } catch (SQLException e) { return false; }
    }

    // ── Write API ─────────────────────────────────────────────────────────────

    /**
     * Records a new bot spawn. Accepts an optional display name (MiniMessage stripped to plain)
     * for the audit trail.
     */
    public void recordSpawn(BotRecord record) {
        recordSpawn(record, null, null);
    }

    public void recordSpawn(BotRecord record, String displayName) {
        recordSpawn(record, displayName, null);
    }

    public void recordSpawn(BotRecord record, String displayName, String luckpermsGroup) {
        activeRecords.put(record.getBotUuid().toString(), record);
        final String display = displayName;
        final String lpGroup = luckpermsGroup;
        enqueue(() -> {
            if (!isAlive()) return;
            // 1. Session history row
            String sql = "INSERT INTO fpp_bot_sessions " +
                    "(bot_name,bot_display,bot_uuid,spawned_by,spawned_by_uuid,world_name," +
                    "spawn_x,spawn_y,spawn_z,spawn_yaw,spawn_pitch,entity_type,spawned_at) " +
                    "VALUES (?,?,?,?,?,?,?,?,?,?,?,?,?)";
            try (PreparedStatement ps = connection.prepareStatement(sql)) {
                ps.setString(1, record.getBotName());
                ps.setString(2, display);
                ps.setString(3, record.getBotUuid().toString());
                ps.setString(4, record.getSpawnedBy());
                ps.setString(5, record.getSpawnedByUuid().toString());
                ps.setString(6, record.getWorldName());
                ps.setDouble(7, record.getSpawnX());
                ps.setDouble(8, record.getSpawnY());
                ps.setDouble(9, record.getSpawnZ());
                ps.setFloat(10, record.getSpawnYaw());
                ps.setFloat(11, record.getSpawnPitch());
                ps.setString(12, "MANNEQUIN");
                ps.setLong(13, record.getSpawnedAt().toEpochMilli());
                ps.executeUpdate();
            } catch (SQLException e) { FppLogger.error("DB recordSpawn: " + e.getMessage()); }

            // 2. Active bots row — source of truth for restart
            upsertActiveBotSync(record.getBotUuid().toString(), record.getBotName(), display,
                    record.getSpawnedBy(), record.getSpawnedByUuid().toString(),
                    record.getWorldName(),
                    record.getSpawnX(), record.getSpawnY(), record.getSpawnZ(),
                    record.getSpawnYaw(), record.getSpawnPitch(), lpGroup);
        });
    }

    /**
     * Persists a bot's LuckPerms group override to the database.
     * Call this whenever {@code FakePlayer.setLuckpermsGroup()} is changed at runtime
     * (e.g. via {@code /fpp rank set}) so the value survives a server restart.
     *
     * @param botUuid      the bot's UUID
     * @param luckpermsGroup the LP group name, or {@code null} to clear the override
     */
    public void updateBotLpGroup(UUID botUuid, String luckpermsGroup) {
        if (botUuid == null) return;
        String uuidStr = botUuid.toString();
        enqueue(() -> {
            if (!isAlive()) return;
            String sql = "UPDATE fpp_active_bots SET luckperms_group=? WHERE bot_uuid=?";
            try (PreparedStatement ps = connection.prepareStatement(sql)) {
                if (luckpermsGroup != null) ps.setString(1, luckpermsGroup);
                else ps.setNull(1, java.sql.Types.VARCHAR);
                ps.setString(2, uuidStr);
                ps.executeUpdate();
                Config.debug("DB updateBotLpGroup: " + uuidStr + " → " + luckpermsGroup);
            } catch (SQLException e) { FppLogger.error("DB updateBotLpGroup: " + e.getMessage()); }
        });
    }

    /**
     * Queues a position update. Updates are deduplicated per UUID — if a new
     * update arrives before the previous one is flushed, only the latest is kept.
     */
    public void updateLastLocation(UUID botUuid, String world,
                                   double x, double y, double z,
                                   float yaw, float pitch) {
        String key = botUuid.toString();
        pendingLocations.put(key, new PendingLocation(world, x, y, z, yaw, pitch));
        BotRecord rec = activeRecords.get(key);
        if (rec != null) rec.setLastLocation(world, x, y, z, yaw, pitch);
    }

    /** Flushes all pending location updates in a single batch. */
    public void flushPendingLocations() {
        if (pendingLocations.isEmpty()) return;
        Map<String, PendingLocation> snap = new HashMap<>(pendingLocations);
        pendingLocations.clear();
        enqueue(() -> batchUpdateLocationsSync(snap));
    }

    public void recordRemoval(UUID botUuid, String reason) {
        Instant now = Instant.now();
        String key = botUuid.toString();
        BotRecord rec = activeRecords.remove(key);
        if (rec != null) { rec.setRemovedAt(now); rec.setRemoveReason(reason); }
        pendingLocations.remove(key);
        enqueue(() -> {
            if (!isAlive()) return;
            long ts = now.toEpochMilli();
            try (PreparedStatement ps = connection.prepareStatement(
                    "UPDATE fpp_bot_sessions SET removed_at=?,remove_reason=? WHERE bot_uuid=? AND removed_at IS NULL")) {
                ps.setLong(1, ts); ps.setString(2, reason); ps.setString(3, key);
                ps.executeUpdate();
            } catch (SQLException e) { FppLogger.error("DB recordRemoval (session): " + e.getMessage()); }
            try (PreparedStatement ps = connection.prepareStatement(
                    "DELETE FROM fpp_active_bots WHERE bot_uuid=?")) {
                ps.setString(1, key);
                ps.executeUpdate();
            } catch (SQLException e) { FppLogger.error("DB recordRemoval (active): " + e.getMessage()); }
        });
    }

    /**
     * Closes all open sessions as SHUTDOWN. Called synchronously on disable.
     * Does NOT clear fpp_active_bots — that table is used by the next startup
     * to restore bots after a clean shutdown.
     */
    public void recordAllShutdown() {
        if (!pendingLocations.isEmpty()) {
            batchUpdateLocationsSync(new HashMap<>(pendingLocations));
            pendingLocations.clear();
        }
        long now = Instant.now().toEpochMilli();
        activeRecords.values().forEach(r -> r.setRemovedAt(Instant.ofEpochMilli(now)));
        activeRecords.clear();
        if (!isAlive()) return;
        try (PreparedStatement ps = connection.prepareStatement(
                "UPDATE fpp_bot_sessions SET removed_at=?,remove_reason='SHUTDOWN' WHERE removed_at IS NULL")) {
            ps.setLong(1, now);
            int rows = ps.executeUpdate();
            Config.debug("DB shutdown: closed " + rows + " open session(s).");
        } catch (SQLException e) { FppLogger.error("DB recordAllShutdown: " + e.getMessage()); }
    }

    /**
     * Called after bots are successfully restored on startup so stale rows
     * from a previous crash don't re-restore on the next restart.
     */
    public void clearActiveBots() {
        enqueue(() -> {
            if (!isAlive()) return;
            try (Statement st = connection.createStatement()) {
                st.execute("DELETE FROM fpp_active_bots");
                Config.debug("DB cleared active_bots.");
            } catch (SQLException e) { FppLogger.error("DB clearActiveBots: " + e.getMessage()); }
        });
    }

    // ── Read API ──────────────────────────────────────────────────────────────

    /**
     * Returns all rows from {@code fpp_active_bots}.
     * Used on startup to restore bots — works even after a crash because
     * fpp_active_bots is kept current throughout the session.
     */
    public List<ActiveBotRow> getActiveBots() {
        List<ActiveBotRow> list = new ArrayList<>();
        if (!isAlive()) return list;
        try (Statement st = connection.createStatement();
             ResultSet rs = st.executeQuery("SELECT * FROM fpp_active_bots ORDER BY updated_at ASC")) {
            while (rs.next()) {
                // luckperms_group may be absent on pre-v5 DBs; catch gracefully
                String lpGroup = null;
                try { lpGroup = rs.getString("luckperms_group"); } catch (SQLException ignored) {}
                list.add(new ActiveBotRow(
                        rs.getString("bot_uuid"), rs.getString("bot_name"),
                        rs.getString("bot_display"),
                        rs.getString("spawned_by"), rs.getString("spawned_by_uuid"),
                        rs.getString("world_name"),
                        rs.getDouble("pos_x"), rs.getDouble("pos_y"), rs.getDouble("pos_z"),
                        rs.getFloat("pos_yaw"), rs.getFloat("pos_pitch"),
                        lpGroup
                ));
            }
        } catch (SQLException e) { FppLogger.error("DB getActiveBots: " + e.getMessage()); }
        return list;
    }

    public List<BotRecord> getActiveSessions() {
        List<BotRecord> list = new ArrayList<>();
        if (!isAlive()) return list;
        try (Statement st = connection.createStatement();
             ResultSet rs = st.executeQuery(
                "SELECT * FROM fpp_bot_sessions WHERE removed_at IS NULL ORDER BY spawned_at DESC")) {
            while (rs.next()) list.add(mapSession(rs));
        } catch (SQLException e) { FppLogger.error("DB getActiveSessions: " + e.getMessage()); }
        return list;
    }

    public List<BotRecord> getRecentSessions(int limit) {
        List<BotRecord> list = new ArrayList<>();
        if (!isAlive()) return list;
        try (PreparedStatement ps = connection.prepareStatement(
                "SELECT * FROM fpp_bot_sessions ORDER BY spawned_at DESC LIMIT ?")) {
            ps.setInt(1, limit);
            try (ResultSet rs = ps.executeQuery()) { while (rs.next()) list.add(mapSession(rs)); }
        } catch (SQLException e) { FppLogger.error("DB getRecentSessions: " + e.getMessage()); }
        return list;
    }

    public List<BotRecord> getSessionsBySpawner(String playerName) {
        return getSessionsBySpawner(playerName, Integer.MAX_VALUE);
    }

    public List<BotRecord> getSessionsBySpawner(String playerName, int limit) {
        List<BotRecord> list = new ArrayList<>();
        if (!isAlive()) return list;
        try (PreparedStatement ps = connection.prepareStatement(
                "SELECT * FROM fpp_bot_sessions WHERE spawned_by=? ORDER BY spawned_at DESC LIMIT ?")) {
            ps.setString(1, playerName);
            ps.setInt(2, limit);
            try (ResultSet rs = ps.executeQuery()) { while (rs.next()) list.add(mapSession(rs)); }
        } catch (SQLException e) { FppLogger.error("DB getSessionsBySpawner: " + e.getMessage()); }
        return list;
    }

    public List<BotRecord> getSessionsByBot(String botName) {
        return getSessionsByBot(botName, Integer.MAX_VALUE);
    }

    public List<BotRecord> getSessionsByBot(String botName, int limit) {
        List<BotRecord> list = new ArrayList<>();
        if (!isAlive()) return list;
        try (PreparedStatement ps = connection.prepareStatement(
                "SELECT * FROM fpp_bot_sessions WHERE bot_name=? ORDER BY spawned_at DESC LIMIT ?")) {
            ps.setString(1, botName);
            ps.setInt(2, limit);
            try (ResultSet rs = ps.executeQuery()) { while (rs.next()) list.add(mapSession(rs)); }
        } catch (SQLException e) { FppLogger.error("DB getSessionsByBot: " + e.getMessage()); }
        return list;
    }

    /** Returns all sessions for a bot identified by UUID. */
    public List<BotRecord> getSessionsByUuid(UUID botUuid) {
        List<BotRecord> list = new ArrayList<>();
        if (!isAlive()) return list;
        try (PreparedStatement ps = connection.prepareStatement(
                "SELECT * FROM fpp_bot_sessions WHERE bot_uuid=? ORDER BY spawned_at DESC")) {
            ps.setString(1, botUuid.toString());
            try (ResultSet rs = ps.executeQuery()) { while (rs.next()) list.add(mapSession(rs)); }
        } catch (SQLException e) { FppLogger.error("DB getSessionsByUuid: " + e.getMessage()); }
        return list;
    }

    /** Returns sessions that ended with a specific reason (e.g. "KILLED", "SHUTDOWN"). */
    public List<BotRecord> getSessionsByReason(String reason, int limit) {
        List<BotRecord> list = new ArrayList<>();
        if (!isAlive()) return list;
        try (PreparedStatement ps = connection.prepareStatement(
                "SELECT * FROM fpp_bot_sessions WHERE remove_reason=? ORDER BY removed_at DESC LIMIT ?")) {
            ps.setString(1, reason);
            ps.setInt(2, limit);
            try (ResultSet rs = ps.executeQuery()) { while (rs.next()) list.add(mapSession(rs)); }
        } catch (SQLException e) { FppLogger.error("DB getSessionsByReason: " + e.getMessage()); }
        return list;
    }

    public int getTotalSessionCount() {
        if (!isAlive()) return 0;
        try (Statement st = connection.createStatement();
             ResultSet rs = st.executeQuery("SELECT COUNT(*) FROM fpp_bot_sessions")) {
            if (rs.next()) return rs.getInt(1);
        } catch (SQLException e) { FppLogger.error("DB getTotalSessionCount: " + e.getMessage()); }
        return 0;
    }

    public int getActiveSessionCount() {
        if (!isAlive()) return 0;
        try (Statement st = connection.createStatement();
             ResultSet rs = st.executeQuery(
                "SELECT COUNT(*) FROM fpp_bot_sessions WHERE removed_at IS NULL")) {
            if (rs.next()) return rs.getInt(1);
        } catch (SQLException e) { FppLogger.error("DB getActiveSessionCount: " + e.getMessage()); }
        return 0;
    }

    /** Returns the number of distinct bot names ever recorded. */
    public int getUniqueBotCount() {
        if (!isAlive()) return 0;
        try (Statement st = connection.createStatement();
             ResultSet rs = st.executeQuery("SELECT COUNT(DISTINCT bot_name) FROM fpp_bot_sessions")) {
            if (rs.next()) return rs.getInt(1);
        } catch (SQLException e) { FppLogger.error("DB getUniqueBotCount: " + e.getMessage()); }
        return 0;
    }

    /** Returns the number of distinct players who have ever spawned a bot. */
    public int getUniqueSpawnerCount() {
        if (!isAlive()) return 0;
        try (Statement st = connection.createStatement();
             ResultSet rs = st.executeQuery("SELECT COUNT(DISTINCT spawned_by) FROM fpp_bot_sessions")) {
            if (rs.next()) return rs.getInt(1);
        } catch (SQLException e) { FppLogger.error("DB getUniqueSpawnerCount: " + e.getMessage()); }
        return 0;
    }

    /**
     * Returns the top {@code limit} spawners by total bot spawns, as
     * {@code Map<playerName, spawnCount>} in descending order.
     */
    public Map<String, Integer> getTopSpawners(int limit) {
        Map<String, Integer> result = new LinkedHashMap<>();
        if (!isAlive()) return result;
        try (PreparedStatement ps = connection.prepareStatement(
                "SELECT spawned_by, COUNT(*) AS cnt FROM fpp_bot_sessions " +
                "GROUP BY spawned_by ORDER BY cnt DESC LIMIT ?")) {
            ps.setInt(1, limit);
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) result.put(rs.getString("spawned_by"), rs.getInt("cnt"));
            }
        } catch (SQLException e) { FppLogger.error("DB getTopSpawners: " + e.getMessage()); }
        return result;
    }

    /**
     * Returns aggregated plugin statistics.
     * All values are read in a single DB roundtrip where possible.
     */
    public DbStats getStats() {
        if (!isAlive()) return new DbStats(0, 0, 0, 0, 0L, isMysql ? "MySQL" : "SQLite");
        int total = 0, active = 0, unique = 0, uniqueSpawners = 0;
        long totalUptimeMs = 0L;
        try (Statement st = connection.createStatement()) {
            try (ResultSet rs = st.executeQuery("SELECT COUNT(*) FROM fpp_bot_sessions")) {
                if (rs.next()) total = rs.getInt(1);
            }
            try (ResultSet rs = st.executeQuery(
                    "SELECT COUNT(*) FROM fpp_bot_sessions WHERE removed_at IS NULL")) {
                if (rs.next()) active = rs.getInt(1);
            }
            try (ResultSet rs = st.executeQuery(
                    "SELECT COUNT(DISTINCT bot_name) FROM fpp_bot_sessions")) {
                if (rs.next()) unique = rs.getInt(1);
            }
            try (ResultSet rs = st.executeQuery(
                    "SELECT COUNT(DISTINCT spawned_by) FROM fpp_bot_sessions")) {
                if (rs.next()) uniqueSpawners = rs.getInt(1);
            }
            try (ResultSet rs = st.executeQuery(
                    "SELECT SUM(removed_at - spawned_at) FROM fpp_bot_sessions WHERE removed_at IS NOT NULL")) {
                if (rs.next()) totalUptimeMs = rs.getLong(1);
            }
        } catch (SQLException e) { FppLogger.error("DB getStats: " + e.getMessage()); }
        return new DbStats(total, active, unique, uniqueSpawners, totalUptimeMs,
                isMysql ? "MySQL" : "SQLite");
    }

    // ── Helpers ───────────────────────────────────────────────────────────────

    private void upsertActiveBotSync(String uuid, String name, String display,
                                     String spawnedBy, String spawnedByUuid, String world,
                                     double x, double y, double z,
                                     float yaw, float pitch, String luckpermsGroup) {
        long now = Instant.now().toEpochMilli();
        String sql = isMysql
                ? "INSERT INTO fpp_active_bots(bot_uuid,bot_name,bot_display,spawned_by,spawned_by_uuid," +
                  "world_name,pos_x,pos_y,pos_z,pos_yaw,pos_pitch,updated_at,luckperms_group) VALUES(?,?,?,?,?,?,?,?,?,?,?,?,?) " +
                  "ON DUPLICATE KEY UPDATE bot_name=VALUES(bot_name),bot_display=VALUES(bot_display)," +
                  "spawned_by=VALUES(spawned_by),spawned_by_uuid=VALUES(spawned_by_uuid)," +
                  "world_name=VALUES(world_name),pos_x=VALUES(pos_x),pos_y=VALUES(pos_y)," +
                  "pos_z=VALUES(pos_z),pos_yaw=VALUES(pos_yaw),pos_pitch=VALUES(pos_pitch)," +
                  "updated_at=VALUES(updated_at),luckperms_group=VALUES(luckperms_group)"
                : "INSERT OR REPLACE INTO fpp_active_bots(bot_uuid,bot_name,bot_display,spawned_by,spawned_by_uuid," +
                  "world_name,pos_x,pos_y,pos_z,pos_yaw,pos_pitch,updated_at,luckperms_group) VALUES(?,?,?,?,?,?,?,?,?,?,?,?,?)";
        try (PreparedStatement ps = connection.prepareStatement(sql)) {
            ps.setString(1, uuid);  ps.setString(2, name);  ps.setString(3, display);
            ps.setString(4, spawnedBy); ps.setString(5, spawnedByUuid);
            ps.setString(6, world); ps.setDouble(7, x); ps.setDouble(8, y); ps.setDouble(9, z);
            ps.setFloat(10, yaw);   ps.setFloat(11, pitch); ps.setLong(12, now);
            if (luckpermsGroup != null) ps.setString(13, luckpermsGroup);
            else ps.setNull(13, java.sql.Types.VARCHAR);
            ps.executeUpdate();
        } catch (SQLException e) { FppLogger.error("DB upsertActiveBot: " + e.getMessage()); }
    }

    private void batchUpdateLocationsSync(Map<String, PendingLocation> snapshot) {
        if (!isAlive() || snapshot.isEmpty()) return;
        long now = Instant.now().toEpochMilli();
        String s1 = "UPDATE fpp_bot_sessions SET last_world=?,last_x=?,last_y=?,last_z=?,last_yaw=?,last_pitch=? WHERE bot_uuid=? AND removed_at IS NULL";
        String s2 = "UPDATE fpp_active_bots SET world_name=?,pos_x=?,pos_y=?,pos_z=?,pos_yaw=?,pos_pitch=?,updated_at=? WHERE bot_uuid=?";
        try (PreparedStatement ps1 = connection.prepareStatement(s1);
             PreparedStatement ps2 = connection.prepareStatement(s2)) {
            for (Map.Entry<String, PendingLocation> e : snapshot.entrySet()) {
                PendingLocation l = e.getValue();
                ps1.setString(1,l.world); ps1.setDouble(2,l.x); ps1.setDouble(3,l.y);
                ps1.setDouble(4,l.z);     ps1.setFloat(5,l.yaw); ps1.setFloat(6,l.pitch);
                ps1.setString(7,e.getKey()); ps1.addBatch();
                ps2.setString(1,l.world); ps2.setDouble(2,l.x); ps2.setDouble(3,l.y);
                ps2.setDouble(4,l.z);     ps2.setFloat(5,l.yaw); ps2.setFloat(6,l.pitch);
                ps2.setLong(7,now); ps2.setString(8,e.getKey()); ps2.addBatch();
            }
            ps1.executeBatch();
            ps2.executeBatch();
            Config.debug("DB flushed " + snapshot.size() + " location(s).");
        } catch (SQLException e) { FppLogger.error("DB batchUpdateLocations: " + e.getMessage()); }
    }

    private void exec(String sql) {
        try (Statement st = connection.createStatement()) { st.execute(sql); }
        catch (SQLException e) { FppLogger.error("DB exec: " + e.getMessage()); }
    }
    private void execSilent(String sql) {
        try (Statement st = connection.createStatement()) { st.execute(sql); }
        catch (SQLException ignored) {}
    }
    private void enqueue(Runnable task) { if (running.get()) writeQueue.offer(task); }

    private BotRecord mapSession(ResultSet rs) throws SQLException {
        long    rm = rs.getLong("removed_at");
        Instant removedAt = rs.wasNull() ? null : Instant.ofEpochMilli(rm);

        double lx = rs.getDouble("last_x"); if (rs.wasNull()) lx = rs.getDouble("spawn_x");
        double ly = rs.getDouble("last_y"); if (rs.wasNull()) ly = rs.getDouble("spawn_y");
        double lz = rs.getDouble("last_z"); if (rs.wasNull()) lz = rs.getDouble("spawn_z");
        String lw = rs.getString("last_world"); if (lw == null) lw = rs.getString("world_name");
        float lyaw, lpitch;
        try { lyaw   = rs.getFloat("last_yaw");   if (rs.wasNull()) lyaw   = rs.getFloat("spawn_yaw");   }
        catch (SQLException ignored) { lyaw = 0f; }
        try { lpitch = rs.getFloat("last_pitch"); if (rs.wasNull()) lpitch = rs.getFloat("spawn_pitch"); }
        catch (SQLException ignored) { lpitch = 0f; }

        return new BotRecord(
                rs.getLong("id"),
                rs.getString("bot_name"), UUID.fromString(rs.getString("bot_uuid")),
                rs.getString("spawned_by"), UUID.fromString(rs.getString("spawned_by_uuid")),
                rs.getString("world_name"),
                rs.getDouble("spawn_x"), rs.getDouble("spawn_y"), rs.getDouble("spawn_z"),
                rs.getFloat("spawn_yaw"), rs.getFloat("spawn_pitch"),
                lw, lx, ly, lz, lyaw, lpitch,
                Instant.ofEpochMilli(rs.getLong("spawned_at")),
                removedAt, rs.getString("remove_reason")
        );
    }

    // ── Merge API (used by DataMigrator) ─────────────────────────────────────

    /**
     * Inserts a session row from an external/old database without overwriting
     * an existing row that has the same {@code bot_uuid} and {@code spawned_at}.
     * Safe to call from any thread (runs synchronously on the calling thread,
     * intended for use from the async DataMigrator task).
     */
    public void mergeSessionRow(
            String botName, String botDisplay, String botUuid,
            String spawnedBy, String spawnedByUuid,
            String worldName,
            double spawnX, double spawnY, double spawnZ,
            float spawnYaw, float spawnPitch,
            String lastWorld, double lastX, double lastY, double lastZ,
            float lastYaw, float lastPitch,
            String entityType,
            long spawnedAtMs, Long removedAtMs, String removeReason) {

        if (!isAlive()) return;

        // Resolve nulls
        if (lastWorld == null) lastWorld = worldName;
        if (entityType == null) entityType = "MANNEQUIN";

        // Use INSERT OR IGNORE (SQLite) / INSERT IGNORE (MySQL) so existing rows are untouched.
        String sql = isMysql
                ? "INSERT IGNORE INTO fpp_bot_sessions" +
                  "(bot_name,bot_display,bot_uuid,spawned_by,spawned_by_uuid,world_name," +
                  "spawn_x,spawn_y,spawn_z,spawn_yaw,spawn_pitch," +
                  "last_world,last_x,last_y,last_z,last_yaw,last_pitch," +
                  "entity_type,spawned_at,removed_at,remove_reason) " +
                  "VALUES(?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?)"
                : "INSERT OR IGNORE INTO fpp_bot_sessions" +
                  "(bot_name,bot_display,bot_uuid,spawned_by,spawned_by_uuid,world_name," +
                  "spawn_x,spawn_y,spawn_z,spawn_yaw,spawn_pitch," +
                  "last_world,last_x,last_y,last_z,last_yaw,last_pitch," +
                  "entity_type,spawned_at,removed_at,remove_reason) " +
                  "VALUES(?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?)";

        try (PreparedStatement ps = connection.prepareStatement(sql)) {
            ps.setString(1,  botName);       ps.setString(2,  botDisplay);
            ps.setString(3,  botUuid);       ps.setString(4,  spawnedBy);
            ps.setString(5,  spawnedByUuid); ps.setString(6,  worldName);
            ps.setDouble(7,  spawnX);        ps.setDouble(8,  spawnY);     ps.setDouble(9,  spawnZ);
            ps.setFloat(10,  spawnYaw);      ps.setFloat(11,  spawnPitch);
            ps.setString(12, lastWorld);
            ps.setDouble(13, lastX);         ps.setDouble(14, lastY);      ps.setDouble(15, lastZ);
            ps.setFloat(16,  lastYaw);       ps.setFloat(17,  lastPitch);
            ps.setString(18, entityType);    ps.setLong(19,   spawnedAtMs);
            if (removedAtMs != null) ps.setLong(20, removedAtMs); else ps.setNull(20, java.sql.Types.BIGINT);
            ps.setString(21, removeReason);
            ps.executeUpdate();
        } catch (SQLException e) {
            FppLogger.error("DB mergeSessionRow: " + e.getMessage());
        }
    }

    /**
     * Inserts an active-bot row from an external/old database without overwriting
     * an existing row that has the same {@code bot_uuid}.
     */
    public void mergeActiveBotRow(
            String botUuid, String botName, String botDisplay,
            String spawnedBy, String spawnedByUuid,
            String worldName, double posX, double posY, double posZ,
            float posYaw, float posPitch, long updatedAt) {

        if (!isAlive()) return;

        String sql = isMysql
                ? "INSERT IGNORE INTO fpp_active_bots" +
                  "(bot_uuid,bot_name,bot_display,spawned_by,spawned_by_uuid," +
                  "world_name,pos_x,pos_y,pos_z,pos_yaw,pos_pitch,updated_at) VALUES(?,?,?,?,?,?,?,?,?,?,?,?)"
                : "INSERT OR IGNORE INTO fpp_active_bots" +
                  "(bot_uuid,bot_name,bot_display,spawned_by,spawned_by_uuid," +
                  "world_name,pos_x,pos_y,pos_z,pos_yaw,pos_pitch,updated_at) VALUES(?,?,?,?,?,?,?,?,?,?,?,?)";

        try (PreparedStatement ps = connection.prepareStatement(sql)) {
            ps.setString(1,  botUuid);       ps.setString(2,  botName);
            ps.setString(3,  botDisplay);    ps.setString(4,  spawnedBy);
            ps.setString(5,  spawnedByUuid); ps.setString(6,  worldName);
            ps.setDouble(7,  posX);          ps.setDouble(8,  posY);  ps.setDouble(9,  posZ);
            ps.setFloat(10,  posYaw);        ps.setFloat(11,  posPitch);
            ps.setLong(12,   updatedAt);
            ps.executeUpdate();
        } catch (SQLException e) {
            FppLogger.error("DB mergeActiveBotRow: " + e.getMessage());
        }
    }

    /**
     * Returns the number of rows in {@code fpp_bot_sessions}.
     * Used by DataMigrator / MigrateCommand to report totals.
     */
    public int countSessions() {
        return getTotalSessionCount();
    }

    /**
     * Returns the number of rows in {@code fpp_active_bots}.
     */
    public int countActiveBotRows() {
        if (!isAlive()) return 0;
        try (Statement st = connection.createStatement();
             ResultSet rs = st.executeQuery("SELECT COUNT(*) FROM fpp_active_bots")) {
            if (rs.next()) return rs.getInt(1);
        } catch (SQLException e) { FppLogger.error("DB countActiveBotRows: " + e.getMessage()); }
        return 0;
    }

    // ── Accessors ─────────────────────────────────────────────────────────────
    public boolean isMysql() { return isMysql; }
    public Map<String, BotRecord> getActiveRecords() { return Collections.unmodifiableMap(activeRecords); }

    // ── Inner types ───────────────────────────────────────────────────────────
    private record PendingLocation(String world, double x, double y, double z, float yaw, float pitch) {}

    /** Row from fpp_active_bots — used for startup restore. */
    public record ActiveBotRow(
            String botUuid, String botName, String botDisplay,
            String spawnedBy, String spawnedByUuid,
            String world, double x, double y, double z,
            float yaw, float pitch,
            String luckpermsGroup
    ) {}

    /**
     * Snapshot of aggregated database statistics.
     *
     * @param totalSessions    total bot sessions ever recorded
     * @param activeSessions   currently open sessions (no removed_at)
     * @param uniqueBots       distinct bot names ever seen
     * @param uniqueSpawners   distinct players who ever spawned a bot
     * @param totalUptimeMs    sum of all completed session durations in milliseconds
     * @param backend          "MySQL" or "SQLite"
     */
    public record DbStats(
            int    totalSessions,
            int    activeSessions,
            int    uniqueBots,
            int    uniqueSpawners,
            long   totalUptimeMs,
            String backend
    ) {
        /** Total uptime of all bots combined, formatted as {@code Xd Xh Xm}. */
        public String formattedUptime() {
            long secs = totalUptimeMs / 1000;
            long days  = secs / 86400;
            long hours = (secs % 86400) / 3600;
            long mins  = (secs % 3600) / 60;
            if (days > 0)  return days  + "d " + hours + "h " + mins + "m";
            if (hours > 0) return hours + "h " + mins + "m";
            return mins + "m";
        }
    }
}
