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
 *   <li><b>MySQL</b>  - when {@code database.mysql.enabled: true}</li>
 *   <li><b>SQLite</b> - automatic local fallback; WAL mode for crash-safety</li>
 * </ol>
 *
 * <h3>Key features</h3>
 * <ul>
 *   <li>Write queue - all writes are serialised on a dedicated thread.</li>
 *   <li>Batch location flush - position updates are deduplicated and sent in one
 *       batch statement instead of one UPDATE per bot per tick.</li>
 *   <li>Auto-reconnect health check - silently reconnects on connection loss.</li>
 *   <li>{@code fpp_active_bots} table - authoritative source for restart
 *       persistence; always reflects live bot state, even after a crash.</li>
 *   <li>Yaw/pitch tracked in last location - bots face the correct direction
 *       after a restart.</li>
 *   <li>Schema version table - safe incremental migrations.</li>
 *   <li>Indices on hot query columns - bot_name, spawned_by, removed_at.</li>
 *   <li>Display name stored alongside internal name for audit trail.</li>
 *   <li>Stats API - total sessions, unique bots, top spawners, uptime.</li>
 * </ul>
 */
public class DatabaseManager {

    // ── Schema version ────────────────────────────────────────────────────────
    private static final int SCHEMA_VERSION = 14;

    /** Returns the latest DB schema version this build requires. */
    public static int getCurrentSchemaVersion() { return SCHEMA_VERSION; }

    // ── DDL - session history ─────────────────────────────────────────────────
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
            "  remove_reason   VARCHAR(32)," +
            "  server_id       VARCHAR(64)  NOT NULL DEFAULT 'default'" +
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
            "  remove_reason   VARCHAR(32)," +
            "  server_id       VARCHAR(64)  NOT NULL DEFAULT 'default'" +
            ")";

    // ── DDL - active bots (restart persistence source of truth) ───────────────
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
            "  luckperms_group VARCHAR(64)  DEFAULT NULL," +
            "  server_id       VARCHAR(64)  NOT NULL DEFAULT 'default'," +
            "  frozen          BOOLEAN DEFAULT 0," +
            "  chat_enabled    BOOLEAN DEFAULT 1," +
            "  chat_tier       VARCHAR(16)  DEFAULT NULL," +
            "  right_click_cmd VARCHAR(256) DEFAULT NULL," +
            "  ai_personality  VARCHAR(64)  DEFAULT NULL," +
            "  pickup_items    BOOLEAN DEFAULT 0," +
            "  pickup_xp       BOOLEAN DEFAULT 1," +
            "  head_ai_enabled BOOLEAN DEFAULT 1," +
            "  nav_parkour     BOOLEAN DEFAULT 0," +
            "  nav_break_blocks BOOLEAN DEFAULT 0," +
            "  nav_place_blocks BOOLEAN DEFAULT 0," +
            "  swim_ai_enabled  BOOLEAN DEFAULT 1," +
            "  chunk_load_radius INT     DEFAULT -1" +
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
            "  luckperms_group VARCHAR(64)  DEFAULT NULL," +
            "  server_id       VARCHAR(64)  NOT NULL DEFAULT 'default'," +
            "  frozen          BOOLEAN DEFAULT 0," +
            "  chat_enabled    BOOLEAN DEFAULT 1," +
            "  chat_tier       VARCHAR(16)  DEFAULT NULL," +
            "  right_click_cmd VARCHAR(256) DEFAULT NULL," +
            "  ai_personality  VARCHAR(64)  DEFAULT NULL," +
            "  pickup_items    BOOLEAN DEFAULT 0," +
            "  pickup_xp       BOOLEAN DEFAULT 1," +
            "  head_ai_enabled BOOLEAN DEFAULT 1," +
            "  nav_parkour     BOOLEAN DEFAULT 0," +
            "  nav_break_blocks BOOLEAN DEFAULT 0," +
            "  nav_place_blocks BOOLEAN DEFAULT 0," +
            "  swim_ai_enabled  BOOLEAN DEFAULT 1," +
            "  chunk_load_radius INT     DEFAULT -1" +
            ")";

    // ── DDL - sleeping bots (peak-hours crash-safe persistence) ──────────────
    private static final String CREATE_SLEEPING_SQLITE =
            "CREATE TABLE IF NOT EXISTS fpp_sleeping_bots (" +
            "  sleep_order INTEGER NOT NULL," +
            "  bot_name    VARCHAR(16)  NOT NULL," +
            "  world_name  VARCHAR(64)  NOT NULL," +
            "  pos_x       DOUBLE NOT NULL," +
            "  pos_y       DOUBLE NOT NULL," +
            "  pos_z       DOUBLE NOT NULL," +
            "  pos_yaw     FLOAT  NOT NULL DEFAULT 0," +
            "  pos_pitch   FLOAT  NOT NULL DEFAULT 0," +
            "  server_id   VARCHAR(64)  NOT NULL DEFAULT 'default'," +
            "  PRIMARY KEY (server_id, sleep_order)" +
            ")";

    private static final String CREATE_SLEEPING_MYSQL =
            "CREATE TABLE IF NOT EXISTS fpp_sleeping_bots (" +
            "  sleep_order INT NOT NULL," +
            "  bot_name    VARCHAR(16)  NOT NULL," +
            "  world_name  VARCHAR(64)  NOT NULL," +
            "  pos_x       DOUBLE NOT NULL," +
            "  pos_y       DOUBLE NOT NULL," +
            "  pos_z       DOUBLE NOT NULL," +
            "  pos_yaw     FLOAT  NOT NULL DEFAULT 0," +
            "  pos_pitch   FLOAT  NOT NULL DEFAULT 0," +
            "  server_id   VARCHAR(64)  NOT NULL DEFAULT 'default'," +
            "  PRIMARY KEY (server_id, sleep_order)" +
            ")";

    // ── DDL - bot identity (stable name→UUID mapping per server) ─────────────
    private static final String CREATE_IDENTITIES_SQLITE =
            "CREATE TABLE IF NOT EXISTS fpp_bot_identities (" +
            "  bot_name   VARCHAR(16) NOT NULL," +
            "  server_id  VARCHAR(64) NOT NULL DEFAULT 'default'," +
            "  bot_uuid   VARCHAR(36) NOT NULL," +
            "  created_at BIGINT      NOT NULL," +
            "  PRIMARY KEY (bot_name, server_id)" +
            ")";

    private static final String CREATE_IDENTITIES_MYSQL =
            "CREATE TABLE IF NOT EXISTS fpp_bot_identities (" +
            "  bot_name   VARCHAR(16) NOT NULL," +
            "  server_id  VARCHAR(64) NOT NULL DEFAULT 'default'," +
            "  bot_uuid   VARCHAR(36) NOT NULL," +
            "  created_at BIGINT      NOT NULL," +
            "  PRIMARY KEY (bot_name, server_id)" +
            ")";

    // ── DDL - bot tasks (mine/use/place/patrol restart persistence) ──────────
    private static final String CREATE_TASKS_SQLITE =
            "CREATE TABLE IF NOT EXISTS fpp_bot_tasks (" +
            "  bot_uuid    VARCHAR(36)  NOT NULL," +
            "  server_id   VARCHAR(64)  NOT NULL DEFAULT 'default'," +
            "  task_type   VARCHAR(16)  NOT NULL," +
            "  world_name  VARCHAR(64)  DEFAULT NULL," +
            "  pos_x       DOUBLE       DEFAULT 0," +
            "  pos_y       DOUBLE       DEFAULT 0," +
            "  pos_z       DOUBLE       DEFAULT 0," +
            "  pos_yaw     FLOAT        DEFAULT 0," +
            "  pos_pitch   FLOAT        DEFAULT 0," +
            "  once_flag   BOOLEAN      DEFAULT 0," +
            "  extra_str   VARCHAR(256) DEFAULT NULL," +
            "  extra_bool  BOOLEAN      DEFAULT 0," +
            "  PRIMARY KEY (bot_uuid, server_id, task_type)" +
            ")";

    private static final String CREATE_TASKS_MYSQL =
            "CREATE TABLE IF NOT EXISTS fpp_bot_tasks (" +
            "  bot_uuid    VARCHAR(36)  NOT NULL," +
            "  server_id   VARCHAR(64)  NOT NULL DEFAULT 'default'," +
            "  task_type   VARCHAR(16)  NOT NULL," +
            "  world_name  VARCHAR(64)  DEFAULT NULL," +
            "  pos_x       DOUBLE       DEFAULT 0," +
            "  pos_y       DOUBLE       DEFAULT 0," +
            "  pos_z       DOUBLE       DEFAULT 0," +
            "  pos_yaw     FLOAT        DEFAULT 0," +
            "  pos_pitch   FLOAT        DEFAULT 0," +
            "  once_flag   BOOLEAN      DEFAULT 0," +
            "  extra_str   VARCHAR(256) DEFAULT NULL," +
            "  extra_bool  BOOLEAN      DEFAULT 0," +
            "  PRIMARY KEY (bot_uuid, server_id, task_type)" +
            ")";

    // ── DDL - schema version ──────────────────────────────────────────────────
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
          "ALTER TABLE fpp_active_bots  ADD COLUMN luckperms_group VARCHAR(64) DEFAULT NULL" },
        // v5 → v6: add server_id column for multi-server / NETWORK mode awareness
        // execSilent is used - safe to run on DBs that already have the column (error is swallowed)
        { "ALTER TABLE fpp_bot_sessions ADD COLUMN server_id VARCHAR(64) NOT NULL DEFAULT 'default'",
          "ALTER TABLE fpp_active_bots  ADD COLUMN server_id VARCHAR(64) NOT NULL DEFAULT 'default'",
          "CREATE INDEX IF NOT EXISTS idx_sessions_server_id ON fpp_bot_sessions(server_id)",
          "CREATE INDEX IF NOT EXISTS idx_active_server_id   ON fpp_active_bots(server_id)" },
        // v6 → v7: add fpp_sleeping_bots table for peak-hours crash-safe persistence
        // CREATE TABLE IF NOT EXISTS is used so it is safe whether or not createTables() already ran it.
        { "CREATE TABLE IF NOT EXISTS fpp_sleeping_bots (" +
          "  sleep_order INTEGER NOT NULL," +
          "  bot_name    VARCHAR(16)  NOT NULL," +
          "  world_name  VARCHAR(64)  NOT NULL," +
          "  pos_x       DOUBLE NOT NULL," +
          "  pos_y       DOUBLE NOT NULL," +
          "  pos_z       DOUBLE NOT NULL," +
          "  pos_yaw     FLOAT  NOT NULL DEFAULT 0," +
          "  pos_pitch   FLOAT  NOT NULL DEFAULT 0," +
          "  server_id   VARCHAR(64)  NOT NULL DEFAULT 'default'," +
          "  PRIMARY KEY (server_id, sleep_order)" +
          ")" },
        // v7 → v8: add fpp_bot_identities for stable name→UUID mapping per server.
        // Bots with the same name on the same server will always reuse the same UUID across
        // restarts, keeping LuckPerms data, inventory files, and session history consistent.
        { "CREATE TABLE IF NOT EXISTS fpp_bot_identities (" +
          "  bot_name   VARCHAR(16) NOT NULL," +
          "  server_id  VARCHAR(64) NOT NULL DEFAULT 'default'," +
          "  bot_uuid   VARCHAR(36) NOT NULL," +
          "  created_at BIGINT      NOT NULL," +
          "  PRIMARY KEY (bot_name, server_id)" +
          ")" },
        // v8 → v9: add bot-specific settings columns (frozen, chat_enabled, chat_tier, right_click_cmd)
        { "ALTER TABLE fpp_active_bots ADD COLUMN frozen            BOOLEAN DEFAULT 0",
          "ALTER TABLE fpp_active_bots ADD COLUMN chat_enabled      BOOLEAN DEFAULT 1",
          "ALTER TABLE fpp_active_bots ADD COLUMN chat_tier         VARCHAR(16) DEFAULT NULL",
          "ALTER TABLE fpp_active_bots ADD COLUMN right_click_cmd   VARCHAR(256) DEFAULT NULL" },
        // v9 → v10: add per-bot AI personality column for persistent random personality assignment
        { "ALTER TABLE fpp_active_bots ADD COLUMN ai_personality    VARCHAR(64) DEFAULT NULL" },
        // v10 → v11: add per-bot pickup toggles
        { "ALTER TABLE fpp_active_bots ADD COLUMN pickup_items      BOOLEAN DEFAULT 0",
          "ALTER TABLE fpp_active_bots ADD COLUMN pickup_xp         BOOLEAN DEFAULT 1" },
        // v11 → v12: add head-AI and per-bot navigation overrides
        { "ALTER TABLE fpp_active_bots ADD COLUMN head_ai_enabled   BOOLEAN DEFAULT 1",
          "ALTER TABLE fpp_active_bots ADD COLUMN nav_parkour       BOOLEAN DEFAULT 0",
          "ALTER TABLE fpp_active_bots ADD COLUMN nav_break_blocks  BOOLEAN DEFAULT 0",
          "ALTER TABLE fpp_active_bots ADD COLUMN nav_place_blocks  BOOLEAN DEFAULT 0" },
        // v12 → v13: add fpp_bot_tasks for persistent mine/use/place/patrol task state.
        // Using CREATE TABLE IF NOT EXISTS — safe whether or not createTables() already ran.
        { "CREATE TABLE IF NOT EXISTS fpp_bot_tasks (" +
          "  bot_uuid    VARCHAR(36)  NOT NULL," +
          "  server_id   VARCHAR(64)  NOT NULL DEFAULT 'default'," +
          "  task_type   VARCHAR(16)  NOT NULL," +
          "  world_name  VARCHAR(64)  DEFAULT NULL," +
          "  pos_x       DOUBLE       DEFAULT 0," +
          "  pos_y       DOUBLE       DEFAULT 0," +
          "  pos_z       DOUBLE       DEFAULT 0," +
          "  pos_yaw     FLOAT        DEFAULT 0," +
          "  pos_pitch   FLOAT        DEFAULT 0," +
          "  once_flag   BOOLEAN      DEFAULT 0," +
          "  extra_str   VARCHAR(256) DEFAULT NULL," +
          "  extra_bool  BOOLEAN      DEFAULT 0," +
          "  PRIMARY KEY (bot_uuid, server_id, task_type)" +
          ")" },
        // v13 → v14: add per-bot swim-AI toggle and chunk-load radius override.
        { "ALTER TABLE fpp_active_bots ADD COLUMN swim_ai_enabled   BOOLEAN DEFAULT 1",
          "ALTER TABLE fpp_active_bots ADD COLUMN chunk_load_radius  INT     DEFAULT -1" }
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
        backfillIdentities();
        startWriteLoop();
        startHealthCheck();
        return true;
    }

    private boolean connect() {
        if (Config.mysqlEnabled()) {
            if (tryMysql()) { isMysql = true; return true; }
            FppLogger.warn("MySQL connection failed - falling back to SQLite.");
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
            FppLogger.debug("Database connected via MySQL ("
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
            FppLogger.debug("Database connected via SQLite (" + dbFile.getPath() + ").");
            return true;
        } catch (Exception e) {
            FppLogger.error("SQLite init error: " + e.getMessage());
            return false;
        }
    }

    private void createTables() {
        exec(isMysql ? CREATE_SESSIONS_MYSQL : CREATE_SESSIONS_SQLITE);
        exec(isMysql ? CREATE_ACTIVE_MYSQL   : CREATE_ACTIVE_SQLITE);
        exec(isMysql ? CREATE_SLEEPING_MYSQL : CREATE_SLEEPING_SQLITE);
        exec(isMysql ? CREATE_IDENTITIES_MYSQL : CREATE_IDENTITIES_SQLITE);
        exec(isMysql ? CREATE_TASKS_MYSQL : CREATE_TASKS_SQLITE);
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
                FppLogger.warn("DB connection lost - reconnecting...");
                if (connect()) { createTables(); FppLogger.success("DB reconnected."); }
                else            { FppLogger.error("DB reconnect failed."); }
            }
        }, 60, 60, TimeUnit.SECONDS);
    }

    // ── Server-filter helpers ─────────────────────────────────────────────────

    /**
     * Returns a parameterised condition that restricts rows to the current
     * server in LOCAL mode, or {@code "1=1"} in NETWORK mode (all rows).
     * Append to SQL and bind with {@link #bindServer(PreparedStatement, int)}.
     */
    private String serverCond() {
        return Config.isNetworkMode() ? "1=1" : "server_id=?";
    }

    /**
     * Binds the current {@code server.id} into {@code ps} at {@code idx} when
     * in LOCAL mode.  No-op in NETWORK mode.
     * @return next free parameter index
     */
    private int bindServer(PreparedStatement ps, int idx) throws SQLException {
        if (!Config.isNetworkMode()) ps.setString(idx++, Config.serverId());
        return idx;
    }

    /**
     * Returns a literal {@code " WHERE server_id='...' "} fragment for
     * {@link Statement}-based aggregation queries.  Empty string in NETWORK mode.
     * Value is admin-controlled and single-quote-escaped before interpolation.
     */
    private String serverWhere() {
        if (Config.isNetworkMode()) return "";
        return " WHERE server_id='" + Config.serverId().replace("'", "''") + "'";
    }

    /** {@code AND server_id='...'} variant of {@link #serverWhere()} for appending to existing WHERE clauses. */
    private String serverAnd() {
        if (Config.isNetworkMode()) return "";
        return " AND server_id='" + Config.serverId().replace("'", "''") + "'";
    }

    public void close() {
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

    /**
     * Returns the raw JDBC {@link Connection}.
     * Intended for use by sub-systems (e.g. {@code ConfigSyncManager}) that need
     * to issue SQL not covered by the normal write API.
     * <b>Do not close the returned connection.</b>
     */
    public Connection getConnection() { return connection; }

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
        recordSpawn(record, null);
    }

    public void recordSpawn(BotRecord record, String displayName) {
        activeRecords.put(record.getBotUuid().toString(), record);
        final String display = displayName;
        enqueue(() -> {
            if (!isAlive()) return;
            // 1. Session history row
            String sql = "INSERT INTO fpp_bot_sessions " +
                    "(bot_name,bot_display,bot_uuid,spawned_by,spawned_by_uuid,world_name," +
                    "spawn_x,spawn_y,spawn_z,spawn_yaw,spawn_pitch,entity_type,spawned_at,server_id) " +
                    "VALUES (?,?,?,?,?,?,?,?,?,?,?,?,?,?)";
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
                ps.setString(14, record.getServerId());
                ps.executeUpdate();
            } catch (SQLException e) { FppLogger.error("DB recordSpawn: " + e.getMessage()); }

            // 2. Active bots row - source of truth for restart
            upsertActiveBotSync(record.getBotUuid().toString(), record.getBotName(), display,
                    record.getSpawnedBy(), record.getSpawnedByUuid().toString(),
                    record.getWorldName(),
                    record.getSpawnX(), record.getSpawnY(), record.getSpawnZ(),
                    record.getSpawnYaw(), record.getSpawnPitch(), null,
                    false, true, null, null, null,
                    Config.bodyPickUpItems(), Config.bodyPickUpXp(),
                    true, Config.pathfindingParkour(), Config.pathfindingBreakBlocks(), Config.pathfindingPlaceBlocks());
        });
    }

    /**
     * Queues a position update. Updates are deduplicated per UUID - if a new
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
     * Does NOT clear fpp_active_bots - that table is used by the next startup
     * to restore bots after a clean shutdown.
     * Always scoped to the current {@code server.id}.
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
                "UPDATE fpp_bot_sessions SET removed_at=?,remove_reason='SHUTDOWN' " +
                "WHERE removed_at IS NULL AND server_id=?")) {
            ps.setLong(1, now);
            ps.setString(2, Config.serverId());
            int rows = ps.executeUpdate();
            Config.debug("DB shutdown: closed " + rows + " open session(s).");
        } catch (SQLException e) { FppLogger.error("DB recordAllShutdown: " + e.getMessage()); }
    }

    /**
     * Called after bots are successfully restored on startup so stale rows
     * from a previous crash don't re-restore on the next restart.
     * Always scoped to the current {@code server.id} - never touches other servers' rows.
     */
    public void clearActiveBots() {
        final String sid = Config.serverId();
        enqueue(() -> {
            if (!isAlive()) return;
            try (PreparedStatement ps = connection.prepareStatement(
                    "DELETE FROM fpp_active_bots WHERE server_id=?")) {
                ps.setString(1, sid);
                int rows = ps.executeUpdate();
                Config.debug("DB cleared " + rows + " active_bot(s) for server='" + sid + "'.");
            } catch (SQLException e) { FppLogger.error("DB clearActiveBots: " + e.getMessage()); }
        });
    }

    // ── Read API ──────────────────────────────────────────────────────────────

    /**
     * Returns rows from {@code fpp_active_bots} scoped by the current database mode:
     * <ul>
     *   <li><b>LOCAL</b> - only this server's rows ({@code server_id = Config.serverId()}).</li>
     *   <li><b>NETWORK</b> - all rows across all servers (useful for admin queries).</li>
     * </ul>
     *
     * <p><b>Do NOT use this method for bot restoration.</b>
     * Use {@link #getActiveBotsForThisServer()} instead, which always hard-scopes to
     * the current server regardless of mode.
     */
    public List<ActiveBotRow> getActiveBots() {
        List<ActiveBotRow> list = new ArrayList<>();
        if (!isAlive()) return list;
        String sql = "SELECT * FROM fpp_active_bots WHERE " + serverCond() + " ORDER BY updated_at ASC";
        try (PreparedStatement ps = connection.prepareStatement(sql)) {
            bindServer(ps, 1);
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) list.add(mapActiveBotRow(rs));
            }
        } catch (SQLException e) { FppLogger.error("DB getActiveBots: " + e.getMessage()); }
        return list;
    }

    /**
     * Returns only the rows from {@code fpp_active_bots} that belong to THIS server,
     * always filtering by {@code server_id = Config.serverId()}.
     *
     * <p><b>Design rule - bots are per-server only:</b> the database may be shared
     * across multiple servers in NETWORK mode, but Minecraft entities (NMS ServerPlayers,
     * ArmorStands, PlayerProfiles) are strictly local to the server that spawned them.
     * This method intentionally ignores {@link Config#isNetworkMode()} so the restore
     * path can never accidentally spawn another server's bots on this instance.
     *
     * <p>This is the method that {@code BotPersistence} must call during startup restore.
     */
    public List<ActiveBotRow> getActiveBotsForThisServer() {
        List<ActiveBotRow> list = new ArrayList<>();
        if (!isAlive()) return list;
        // Hard-coded server_id filter - never bypassed, even in NETWORK mode.
        // Bots are per-server only. Database may be shared, but entities are not.
        String sql = "SELECT * FROM fpp_active_bots WHERE server_id=? ORDER BY updated_at ASC";
        try (PreparedStatement ps = connection.prepareStatement(sql)) {
            ps.setString(1, Config.serverId());
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) list.add(mapActiveBotRow(rs));
            }
        } catch (SQLException e) { FppLogger.error("DB getActiveBotsForThisServer: " + e.getMessage()); }
        return list;
    }

    /**
     * Returns active bots from <em>all other</em> servers in the shared database
     * (i.e. rows where {@code server_id != Config.serverId()}).
     *
     * <p>Called once at startup in NETWORK mode to pre-populate the local
     * {@link me.bill.fakePlayerPlugin.fakeplayer.RemoteBotCache} so that players
     * already online on this server see virtual tab-list entries for bots that
     * were spawned on other servers before this server started.
     *
     * <p>Note: skin texture data is NOT stored in the DB, so entries returned
     * here will have empty skin fields.  They will be updated automatically
     * when the originating server's next {@code BOT_SPAWN} message arrives.
     *
     * <p>Returns an empty list when not in NETWORK mode or when the DB is unavailable.
     */
    public List<ActiveBotRow> getActiveBotsFromOtherServers() {
        List<ActiveBotRow> list = new ArrayList<>();
        if (!Config.isNetworkMode() || !isAlive()) return list;
        String sql = "SELECT * FROM fpp_active_bots WHERE server_id != ? ORDER BY updated_at ASC";
        try (PreparedStatement ps = connection.prepareStatement(sql)) {
            ps.setString(1, Config.serverId());
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) list.add(mapActiveBotRow(rs));
            }
        } catch (SQLException e) { FppLogger.error("DB getActiveBotsFromOtherServers: " + e.getMessage()); }
        return list;
    }

    /** Maps a ResultSet row from {@code fpp_active_bots} to an {@link ActiveBotRow}. */
    private ActiveBotRow mapActiveBotRow(ResultSet rs) throws SQLException {
        String lpGroup = null;
        try { lpGroup = rs.getString("luckperms_group"); } catch (SQLException ignored) {}
        String sid = null;
        try { sid = rs.getString("server_id"); } catch (SQLException ignored) {}
        if (sid == null || sid.isBlank()) sid = Config.serverId();
        String aiPers = null;
        try { aiPers = rs.getString("ai_personality"); } catch (SQLException ignored) {}
        boolean pickUpItems = Config.bodyPickUpItems();
        try { pickUpItems = rs.getBoolean("pickup_items"); } catch (SQLException ignored) {}
        boolean pickUpXp = Config.bodyPickUpXp();
        try { pickUpXp = rs.getBoolean("pickup_xp"); } catch (SQLException ignored) {}
        boolean frozen = false;
        try { frozen = rs.getBoolean("frozen"); } catch (SQLException ignored) {}
        boolean chatEnabled = true;
        try { chatEnabled = rs.getBoolean("chat_enabled"); } catch (SQLException ignored) {}
        String chatTier = null;
        try { chatTier = rs.getString("chat_tier"); } catch (SQLException ignored) {}
        String rightClickCmd = null;
        try { rightClickCmd = rs.getString("right_click_cmd"); } catch (SQLException ignored) {}
        boolean headAiEnabled = true;
        try { headAiEnabled = rs.getBoolean("head_ai_enabled"); } catch (SQLException ignored) {}
        boolean navParkour = Config.pathfindingParkour();
        try { navParkour = rs.getBoolean("nav_parkour"); } catch (SQLException ignored) {}
        boolean navBreakBlocks = Config.pathfindingBreakBlocks();
        try { navBreakBlocks = rs.getBoolean("nav_break_blocks"); } catch (SQLException ignored) {}
        boolean navPlaceBlocks = Config.pathfindingPlaceBlocks();
        try { navPlaceBlocks = rs.getBoolean("nav_place_blocks"); } catch (SQLException ignored) {}
        boolean swimAiEnabled = Config.swimAiEnabled();
        try { swimAiEnabled = rs.getBoolean("swim_ai_enabled"); } catch (SQLException ignored) {}
        int chunkLoadRadius = -1;
        try { chunkLoadRadius = rs.getInt("chunk_load_radius"); } catch (SQLException ignored) {}
        return new ActiveBotRow(
                rs.getString("bot_uuid"), rs.getString("bot_name"),
                rs.getString("bot_display"),
                rs.getString("spawned_by"), rs.getString("spawned_by_uuid"),
                rs.getString("world_name"),
                rs.getDouble("pos_x"), rs.getDouble("pos_y"), rs.getDouble("pos_z"),
                rs.getFloat("pos_yaw"), rs.getFloat("pos_pitch"),
                lpGroup, sid, aiPers, pickUpItems, pickUpXp,
                frozen, chatEnabled, chatTier, rightClickCmd,
                headAiEnabled, navParkour, navBreakBlocks, navPlaceBlocks,
                swimAiEnabled, chunkLoadRadius
        );
    }

    public List<BotRecord> getActiveSessions() {
        List<BotRecord> list = new ArrayList<>();
        if (!isAlive()) return list;
        try (PreparedStatement ps = connection.prepareStatement(
                "SELECT * FROM fpp_bot_sessions WHERE ended_at IS NULL AND " + serverCond())) {
            bindServer(ps, 1);
            try (ResultSet rs = ps.executeQuery()) { while (rs.next()) list.add(mapSession(rs)); }
        } catch (SQLException e) { FppLogger.error("DB getActiveSessions: " + e.getMessage()); }
        return list;
    }

    public List<BotRecord> getRecentSessions(int limit) {
        List<BotRecord> list = new ArrayList<>();
        if (!isAlive()) return list;
        try (PreparedStatement ps = connection.prepareStatement(
                "SELECT * FROM fpp_bot_sessions WHERE " + serverCond() +
                " ORDER BY spawned_at DESC LIMIT ?")) {
            int next = bindServer(ps, 1);
            ps.setInt(next, limit);
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
                "SELECT * FROM fpp_bot_sessions WHERE spawned_by=?" +
                serverAnd() + " ORDER BY spawned_at DESC LIMIT ?")) {
            ps.setString(1, playerName);
            int next = bindServer(ps, 2);
            ps.setInt(next, limit);
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
                "SELECT * FROM fpp_bot_sessions WHERE bot_name=?" +
                serverAnd() + " ORDER BY spawned_at DESC LIMIT ?")) {
            ps.setString(1, botName);
            int next = bindServer(ps, 2);
            ps.setInt(next, limit);
            try (ResultSet rs = ps.executeQuery()) { while (rs.next()) list.add(mapSession(rs)); }
        } catch (SQLException e) { FppLogger.error("DB getSessionsByBot: " + e.getMessage()); }
        return list;
    }

    /** Returns all sessions for a bot identified by UUID. */
    public List<BotRecord> getSessionsByUuid(UUID botUuid) {
        List<BotRecord> list = new ArrayList<>();
        if (!isAlive()) return list;
        try (PreparedStatement ps = connection.prepareStatement(
                "SELECT * FROM fpp_bot_sessions WHERE bot_uuid=?" +
                serverAnd() + " ORDER BY spawned_at DESC")) {
            ps.setString(1, botUuid.toString());
            bindServer(ps, 2);
            try (ResultSet rs = ps.executeQuery()) { while (rs.next()) list.add(mapSession(rs)); }
        } catch (SQLException e) { FppLogger.error("DB getSessionsByUuid: " + e.getMessage()); }
        return list;
    }

    /** Returns sessions that ended with a specific reason (e.g. "KILLED", "SHUTDOWN"). */
    public List<BotRecord> getSessionsByReason(String reason, int limit) {
        List<BotRecord> list = new ArrayList<>();
        if (!isAlive()) return list;
        try (PreparedStatement ps = connection.prepareStatement(
                "SELECT * FROM fpp_bot_sessions WHERE remove_reason=?" +
                serverAnd() + " ORDER BY removed_at DESC LIMIT ?")) {
            ps.setString(1, reason);
            int next = bindServer(ps, 2);
            ps.setInt(next, limit);
            try (ResultSet rs = ps.executeQuery()) { while (rs.next()) list.add(mapSession(rs)); }
        } catch (SQLException e) { FppLogger.error("DB getSessionsByReason: " + e.getMessage()); }
        return list;
    }

    public int getTotalSessionCount() {
        if (!isAlive()) return 0;
        try (Statement st = connection.createStatement();
             ResultSet rs = st.executeQuery(
                "SELECT COUNT(*) FROM fpp_bot_sessions" + serverWhere())) {
            if (rs.next()) return rs.getInt(1);
        } catch (SQLException e) { FppLogger.error("DB getTotalSessionCount: " + e.getMessage()); }
        return 0;
    }

    public int getActiveSessionCount() {
        if (!isAlive()) return 0;
        try (Statement st = connection.createStatement();
             ResultSet rs = st.executeQuery(
                "SELECT COUNT(*) FROM fpp_bot_sessions WHERE removed_at IS NULL" + serverAnd())) {
            if (rs.next()) return rs.getInt(1);
        } catch (SQLException e) { FppLogger.error("DB getActiveSessionCount: " + e.getMessage()); }
        return 0;
    }

    /** Returns the number of distinct bot names ever recorded. */
    public int getUniqueBotCount() {
        if (!isAlive()) return 0;
        try (Statement st = connection.createStatement();
             ResultSet rs = st.executeQuery(
                "SELECT COUNT(DISTINCT bot_name) FROM fpp_bot_sessions" + serverWhere())) {
            if (rs.next()) return rs.getInt(1);
        } catch (SQLException e) { FppLogger.error("DB getUniqueBotCount: " + e.getMessage()); }
        return 0;
    }

    /** Returns the number of distinct players who have ever spawned a bot. */
    public int getUniqueSpawnerCount() {
        if (!isAlive()) return 0;
        try (Statement st = connection.createStatement();
             ResultSet rs = st.executeQuery(
                "SELECT COUNT(DISTINCT spawned_by) FROM fpp_bot_sessions" + serverWhere())) {
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
                "SELECT spawned_by, COUNT(*) AS cnt FROM fpp_bot_sessions WHERE " + serverCond() +
                " GROUP BY spawned_by ORDER BY cnt DESC LIMIT ?")) {
            int next = bindServer(ps, 1);
            ps.setInt(next, limit);
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) result.put(rs.getString("spawned_by"), rs.getInt("cnt"));
            }
        } catch (SQLException e) { FppLogger.error("DB getTopSpawners: " + e.getMessage()); }
        return result;
    }

    /**
     * Returns aggregated plugin statistics.
     * All values are scoped to the current server (LOCAL mode) or global (NETWORK mode).
     */
    public DbStats getStats() {
        if (!isAlive()) return new DbStats(0, 0, 0, 0, 0L, isMysql ? "MySQL" : "SQLite");
        int total = 0, active = 0, unique = 0, uniqueSpawners = 0;
        long totalUptimeMs = 0L;
        try (Statement st = connection.createStatement()) {
            try (ResultSet rs = st.executeQuery(
                    "SELECT COUNT(*) FROM fpp_bot_sessions" + serverWhere())) {
                if (rs.next()) total = rs.getInt(1);
            }
            try (ResultSet rs = st.executeQuery(
                    "SELECT COUNT(*) FROM fpp_bot_sessions WHERE removed_at IS NULL" + serverAnd())) {
                if (rs.next()) active = rs.getInt(1);
            }
            try (ResultSet rs = st.executeQuery(
                    "SELECT COUNT(DISTINCT bot_name) FROM fpp_bot_sessions" + serverWhere())) {
                if (rs.next()) unique = rs.getInt(1);
            }
            try (ResultSet rs = st.executeQuery(
                    "SELECT COUNT(DISTINCT spawned_by) FROM fpp_bot_sessions" + serverWhere())) {
                if (rs.next()) uniqueSpawners = rs.getInt(1);
            }
            try (ResultSet rs = st.executeQuery(
                    "SELECT SUM(removed_at - spawned_at) FROM fpp_bot_sessions " +
                    "WHERE removed_at IS NOT NULL" + serverAnd())) {
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
                                     float yaw, float pitch, String luckpermsGroup,
                                     boolean frozen, boolean chatEnabled, String chatTier, String rightClickCmd,
                                     String aiPersonality, boolean pickUpItems, boolean pickUpXp,
                                     boolean headAiEnabled, boolean navParkour, boolean navBreakBlocks, boolean navPlaceBlocks) {
        long now = Instant.now().toEpochMilli();
        String serverId = Config.serverId();
        String sql = isMysql
                ? "INSERT INTO fpp_active_bots(bot_uuid,bot_name,bot_display,spawned_by,spawned_by_uuid," +
                  "world_name,pos_x,pos_y,pos_z,pos_yaw,pos_pitch,updated_at,luckperms_group,server_id," +
                  "frozen,chat_enabled,chat_tier,right_click_cmd,ai_personality,pickup_items,pickup_xp," +
                  "head_ai_enabled,nav_parkour,nav_break_blocks,nav_place_blocks) VALUES(?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?) " +
                  "ON DUPLICATE KEY UPDATE bot_name=VALUES(bot_name),bot_display=VALUES(bot_display)," +
                  "spawned_by=VALUES(spawned_by),spawned_by_uuid=VALUES(spawned_by_uuid)," +
                  "world_name=VALUES(world_name),pos_x=VALUES(pos_x),pos_y=VALUES(pos_y)," +
                  "pos_z=VALUES(pos_z),pos_yaw=VALUES(pos_yaw),pos_pitch=VALUES(pos_pitch)," +
                  "updated_at=VALUES(updated_at),luckperms_group=VALUES(luckperms_group)," +
                  "server_id=VALUES(server_id),frozen=VALUES(frozen),chat_enabled=VALUES(chat_enabled)," +
                  "chat_tier=VALUES(chat_tier),right_click_cmd=VALUES(right_click_cmd)," +
                  "ai_personality=VALUES(ai_personality)," +
                  "pickup_items=VALUES(pickup_items),pickup_xp=VALUES(pickup_xp)," +
                  "head_ai_enabled=VALUES(head_ai_enabled),nav_parkour=VALUES(nav_parkour)," +
                  "nav_break_blocks=VALUES(nav_break_blocks),nav_place_blocks=VALUES(nav_place_blocks)"
                : "INSERT OR REPLACE INTO fpp_active_bots(bot_uuid,bot_name,bot_display,spawned_by,spawned_by_uuid," +
                  "world_name,pos_x,pos_y,pos_z,pos_yaw,pos_pitch,updated_at,luckperms_group,server_id," +
                  "frozen,chat_enabled,chat_tier,right_click_cmd,ai_personality,pickup_items,pickup_xp," +
                  "head_ai_enabled,nav_parkour,nav_break_blocks,nav_place_blocks) VALUES(?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?)";
        try (PreparedStatement ps = connection.prepareStatement(sql)) {
            ps.setString(1, uuid);  ps.setString(2, name);  ps.setString(3, display);
            ps.setString(4, spawnedBy); ps.setString(5, spawnedByUuid);
            ps.setString(6, world); ps.setDouble(7, x); ps.setDouble(8, y); ps.setDouble(9, z);
            ps.setFloat(10, yaw);   ps.setFloat(11, pitch); ps.setLong(12, now);
            if (luckpermsGroup != null) ps.setString(13, luckpermsGroup);
            else ps.setNull(13, java.sql.Types.VARCHAR);
            ps.setString(14, serverId);
            ps.setBoolean(15, frozen);
            ps.setBoolean(16, chatEnabled);
            if (chatTier != null) ps.setString(17, chatTier);
            else ps.setNull(17, java.sql.Types.VARCHAR);
            if (rightClickCmd != null) ps.setString(18, rightClickCmd);
            else ps.setNull(18, java.sql.Types.VARCHAR);
            if (aiPersonality != null) ps.setString(19, aiPersonality);
            else ps.setNull(19, java.sql.Types.VARCHAR);
            ps.setBoolean(20, pickUpItems);
            ps.setBoolean(21, pickUpXp);
            ps.setBoolean(22, headAiEnabled);
            ps.setBoolean(23, navParkour);
            ps.setBoolean(24, navBreakBlocks);
            ps.setBoolean(25, navPlaceBlocks);
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

        // server_id - absent on pre-v6 DBs; fall back to current server
        String sid = null;
        try { sid = rs.getString("server_id"); } catch (SQLException ignored) {}
        if (sid == null || sid.isBlank()) sid = Config.serverId();

        return new BotRecord(
                rs.getLong("id"),
                rs.getString("bot_name"), UUID.fromString(rs.getString("bot_uuid")),
                rs.getString("spawned_by"), UUID.fromString(rs.getString("spawned_by_uuid")),
                rs.getString("world_name"),
                rs.getDouble("spawn_x"), rs.getDouble("spawn_y"), rs.getDouble("spawn_z"),
                rs.getFloat("spawn_yaw"), rs.getFloat("spawn_pitch"),
                lw, lx, ly, lz, lyaw, lpitch,
                Instant.ofEpochMilli(rs.getLong("spawned_at")),
                removedAt, rs.getString("remove_reason"),
                sid
        );
    }

    // ── Bot identity API (stable name→UUID per server) ───────────────────────

    /**
     * Backfills {@code fpp_bot_identities} from {@code fpp_bot_sessions} on first
     * startup after upgrading to schema v8, so existing bots keep their original UUID.
     * Uses the earliest recorded UUID for each {@code (bot_name, server_id)} pair.
     * Safe to re-run - INSERT OR IGNORE / INSERT IGNORE is a no-op when the row exists.
     */
    private void backfillIdentities() {
        if (!isAlive()) return;
        String sql = isMysql
                ? "INSERT IGNORE INTO fpp_bot_identities(bot_name,server_id,bot_uuid,created_at) " +
                  "SELECT bot_name, server_id, " +
                  "(SELECT s2.bot_uuid FROM fpp_bot_sessions s2 " +
                  " WHERE s2.bot_name=s1.bot_name AND s2.server_id=s1.server_id " +
                  " ORDER BY s2.spawned_at ASC LIMIT 1), " +
                  "MIN(spawned_at) FROM fpp_bot_sessions s1 GROUP BY bot_name, server_id"
                : "INSERT OR IGNORE INTO fpp_bot_identities(bot_name,server_id,bot_uuid,created_at) " +
                  "SELECT bot_name, server_id, " +
                  "(SELECT s2.bot_uuid FROM fpp_bot_sessions s2 " +
                  " WHERE s2.bot_name=s1.bot_name AND s2.server_id=s1.server_id " +
                  " ORDER BY s2.spawned_at ASC LIMIT 1), " +
                  "MIN(spawned_at) FROM fpp_bot_sessions s1 GROUP BY bot_name, server_id";
        try (Statement st = connection.createStatement()) {
            int rows = st.executeUpdate(sql);
            if (rows > 0) {
                FppLogger.info("Bot identity registry: backfilled " + rows
                        + " identit" + (rows == 1 ? "y" : "ies") + " from session history.");
            }
        } catch (SQLException e) {
            // Non-fatal - identity registry builds up naturally as bots spawn
            FppLogger.debug("Bot identity backfill skipped (no session history yet): " + e.getMessage());
        }
    }

    /**
     * Looks up the stored UUID for {@code botName} on the given server.
     * Returns {@code null} when no mapping exists yet.
     *
     * <p>Synchronous read - safe to call on the main thread for single-bot spawns.
     */
    public UUID lookupBotUuid(String botName, String serverId) {
        if (!isAlive()) return null;
        try (PreparedStatement ps = connection.prepareStatement(
                "SELECT bot_uuid FROM fpp_bot_identities WHERE bot_name=? AND server_id=?")) {
            ps.setString(1, botName);
            ps.setString(2, serverId);
            try (ResultSet rs = ps.executeQuery()) {
                if (rs.next()) {
                    String raw = rs.getString(1);
                    if (raw != null && !raw.isBlank()) return UUID.fromString(raw);
                }
            }
        } catch (SQLException e) {
            FppLogger.error("DB lookupBotUuid(" + botName + "): " + e.getMessage());
        }
        return null;
    }

    /**
     * Persists a new {@code botName → uuid} mapping for the given server.
     * Uses INSERT OR IGNORE / INSERT IGNORE so the first UUID registered for a name
     * is always the one that sticks - re-registering with a different UUID is a no-op.
     * Enqueued to the write thread (non-blocking from the caller's perspective).
     */
    public void registerBotUuid(String botName, UUID uuid, String serverId) {
        final String sid = serverId;
        final long   now = Instant.now().toEpochMilli();
        enqueue(() -> {
            if (!isAlive()) return;
            String sql = isMysql
                    ? "INSERT IGNORE INTO fpp_bot_identities(bot_name,server_id,bot_uuid,created_at) VALUES(?,?,?,?)"
                    : "INSERT OR IGNORE INTO fpp_bot_identities(bot_name,server_id,bot_uuid,created_at) VALUES(?,?,?,?)";
            try (PreparedStatement ps = connection.prepareStatement(sql)) {
                ps.setString(1, botName);
                ps.setString(2, sid);
                ps.setString(3, uuid.toString());
                ps.setLong(4,   now);
                ps.executeUpdate();
                Config.debug("DB registered identity: " + botName + " → " + uuid + " (server=" + sid + ")");
            } catch (SQLException e) {
                FppLogger.error("DB registerBotUuid(" + botName + "): " + e.getMessage());
            }
        });
    }

    // ── Sleeping bots API (peak-hours crash-safe persistence) ────────────────

    /**
     * Replaces all sleeping-bot rows for the current server with the supplied list.
     * Called from the main thread whenever the peak-hours sleep queue changes;
     * the actual SQL is enqueued to the write thread (non-blocking).
     *
     * @param bots ordered list of sleeping bots (index = sleep_order); may be empty to clear.
     */
    public void saveSleepingBots(List<SleepingBotRow> bots) {
        final List<SleepingBotRow> snap = new ArrayList<>(bots);
        final String sid = Config.serverId();
        enqueue(() -> {
            if (!isAlive()) return;
            // Atomic replace: delete old rows then insert current queue
            try (PreparedStatement del = connection.prepareStatement(
                    "DELETE FROM fpp_sleeping_bots WHERE server_id=?")) {
                del.setString(1, sid);
                del.executeUpdate();
            } catch (SQLException e) {
                FppLogger.error("DB saveSleepingBots (delete): " + e.getMessage()); return;
            }
            if (snap.isEmpty()) return;
            String sql = "INSERT INTO fpp_sleeping_bots" +
                    "(sleep_order,bot_name,world_name,pos_x,pos_y,pos_z,pos_yaw,pos_pitch,server_id)" +
                    " VALUES(?,?,?,?,?,?,?,?,?)";
            try (PreparedStatement ps = connection.prepareStatement(sql)) {
                for (SleepingBotRow row : snap) {
                    ps.setInt(1,    row.sleepOrder());
                    ps.setString(2, row.botName());
                    ps.setString(3, row.world());
                    ps.setDouble(4, row.x());
                    ps.setDouble(5, row.y());
                    ps.setDouble(6, row.z());
                    ps.setFloat(7,  row.yaw());
                    ps.setFloat(8,  row.pitch());
                    ps.setString(9, sid);
                    ps.addBatch();
                }
                ps.executeBatch();
                Config.debug("DB saved " + snap.size() + " sleeping bot(s) for server='" + sid + "'.");
            } catch (SQLException e) { FppLogger.error("DB saveSleepingBots (insert): " + e.getMessage()); }
        });
    }

    /**
     * Loads sleeping-bot rows for the current server.
     * Intended for startup restore - called synchronously on the main thread.
     *
     * @return ordered list (by sleep_order ASC) of sleeping bot snapshots, never null.
     */
    public List<SleepingBotRow> loadSleepingBots() {
        List<SleepingBotRow> list = new ArrayList<>();
        if (!isAlive()) return list;
        String sql = "SELECT * FROM fpp_sleeping_bots WHERE server_id=? ORDER BY sleep_order ASC";
        try (PreparedStatement ps = connection.prepareStatement(sql)) {
            ps.setString(1, Config.serverId());
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    list.add(new SleepingBotRow(
                            rs.getInt("sleep_order"),
                            rs.getString("bot_name"),
                            rs.getString("world_name"),
                            rs.getDouble("pos_x"), rs.getDouble("pos_y"), rs.getDouble("pos_z"),
                            rs.getFloat("pos_yaw"), rs.getFloat("pos_pitch")
                    ));
                }
            }
        } catch (SQLException e) { FppLogger.error("DB loadSleepingBots: " + e.getMessage()); }
        return list;
    }

    /**
     * Removes all sleeping-bot rows for the current server.
     * Enqueued to the write thread (non-blocking).
     */
    public void clearSleepingBots() {
        final String sid = Config.serverId();
        enqueue(() -> {
            if (!isAlive()) return;
            try (PreparedStatement ps = connection.prepareStatement(
                    "DELETE FROM fpp_sleeping_bots WHERE server_id=?")) {
                ps.setString(1, sid);
                int rows = ps.executeUpdate();
                Config.debug("DB cleared " + rows + " sleeping bot(s) for server='" + sid + "'.");
            } catch (SQLException e) { FppLogger.error("DB clearSleepingBots: " + e.getMessage()); }
        });
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
            long spawnedAtMs, Long removedAtMs, String removeReason,
            String serverId) {

        if (!isAlive()) return;

        if (lastWorld == null) lastWorld = worldName;
        if (entityType == null) entityType = "MANNEQUIN";
        if (serverId == null || serverId.isBlank()) serverId = Config.serverId();

        String sql = isMysql
                ? "INSERT IGNORE INTO fpp_bot_sessions" +
                  "(bot_name,bot_display,bot_uuid,spawned_by,spawned_by_uuid,world_name," +
                  "spawn_x,spawn_y,spawn_z,spawn_yaw,spawn_pitch," +
                  "last_world,last_x,last_y,last_z,last_yaw,last_pitch," +
                  "entity_type,spawned_at,removed_at,remove_reason,server_id) " +
                  "VALUES(?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?)"
                : "INSERT OR IGNORE INTO fpp_bot_sessions" +
                  "(bot_name,bot_display,bot_uuid,spawned_by,spawned_by_uuid,world_name," +
                  "spawn_x,spawn_y,spawn_z,spawn_yaw,spawn_pitch," +
                  "last_world,last_x,last_y,last_z,last_yaw,last_pitch," +
                  "entity_type,spawned_at,removed_at,remove_reason,server_id) " +
                  "VALUES(?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?)";

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
            ps.setString(22, serverId);
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
            float posYaw, float posPitch, long updatedAt,
            String serverId) {

        if (!isAlive()) return;
        if (serverId == null || serverId.isBlank()) serverId = Config.serverId();

        String sql = isMysql
                ? "INSERT IGNORE INTO fpp_active_bots" +
                  "(bot_uuid,bot_name,bot_display,spawned_by,spawned_by_uuid," +
                  "world_name,pos_x,pos_y,pos_z,pos_yaw,pos_pitch,updated_at,server_id) VALUES(?,?,?,?,?,?,?,?,?,?,?,?,?)"
                : "INSERT OR IGNORE INTO fpp_active_bots" +
                  "(bot_uuid,bot_name,bot_display,spawned_by,spawned_by_uuid," +
                  "world_name,pos_x,pos_y,pos_z,pos_yaw,pos_pitch,updated_at,server_id) VALUES(?,?,?,?,?,?,?,?,?,?,?,?,?)";

        try (PreparedStatement ps = connection.prepareStatement(sql)) {
            ps.setString(1,  botUuid);       ps.setString(2,  botName);
            ps.setString(3,  botDisplay);    ps.setString(4,  spawnedBy);
            ps.setString(5,  spawnedByUuid); ps.setString(6,  worldName);
            ps.setDouble(7,  posX);          ps.setDouble(8,  posY);  ps.setDouble(9,  posZ);
            ps.setFloat(10,  posYaw);        ps.setFloat(11,  posPitch);
            ps.setLong(12,   updatedAt);     ps.setString(13, serverId);
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

    /**
     * Persists a bot's AI personality name to {@code fpp_active_bots}.
     * Enqueued on the background writer thread so it is safe to call on the main thread.
     *
     * @param uuid        the bot's UUID string
     * @param personality the personality name (file name without {@code .txt}), or {@code null} to clear
     */
    public void updateBotAiPersonality(String uuid, String personality) {
        if (!isAlive()) return;
        final String p = personality;
        enqueue(() -> {
            if (!isAlive()) return;
            String sql = "UPDATE fpp_active_bots SET ai_personality=? WHERE bot_uuid=?";
            try (PreparedStatement ps = connection.prepareStatement(sql)) {
                if (p != null) ps.setString(1, p);
                else ps.setNull(1, java.sql.Types.VARCHAR);
                ps.setString(2, uuid);
                ps.executeUpdate();
            } catch (SQLException e) { FppLogger.error("DB updateBotAiPersonality: " + e.getMessage()); }
        });
    }

    /**
     * Persists a bot's per-bot pickup toggles to {@code fpp_active_bots}.
     * Enqueued on the background writer thread so it is safe to call on the main thread.
     */
    public void updateBotPickupSettings(String uuid, boolean pickUpItems, boolean pickUpXp) {
        if (!isAlive()) return;
        enqueue(() -> {
            if (!isAlive()) return;
            String sql = "UPDATE fpp_active_bots SET pickup_items=?, pickup_xp=? WHERE bot_uuid=?";
            try (PreparedStatement ps = connection.prepareStatement(sql)) {
                ps.setBoolean(1, pickUpItems);
                ps.setBoolean(2, pickUpXp);
                ps.setString(3, uuid);
                ps.executeUpdate();
            } catch (SQLException e) { FppLogger.error("DB updateBotPickupSettings: " + e.getMessage()); }
        });
    }

    /**
     * Persists a bot's frozen state to {@code fpp_active_bots}.
     * Enqueued on the background writer thread.
     */
    public void updateBotFrozen(String uuid, boolean frozen) {
        if (!isAlive()) return;
        enqueue(() -> {
            if (!isAlive()) return;
            String sql = "UPDATE fpp_active_bots SET frozen=? WHERE bot_uuid=?";
            try (PreparedStatement ps = connection.prepareStatement(sql)) {
                ps.setBoolean(1, frozen);
                ps.setString(2, uuid);
                ps.executeUpdate();
            } catch (SQLException e) { FppLogger.error("DB updateBotFrozen: " + e.getMessage()); }
        });
    }

    /**
     * Persists a bot's chat settings (enabled flag + tier) to {@code fpp_active_bots}.
     * Enqueued on the background writer thread.
     */
    public void updateBotChatSettings(String uuid, boolean chatEnabled, String chatTier) {
        if (!isAlive()) return;
        final String tier = chatTier;
        enqueue(() -> {
            if (!isAlive()) return;
            String sql = "UPDATE fpp_active_bots SET chat_enabled=?, chat_tier=? WHERE bot_uuid=?";
            try (PreparedStatement ps = connection.prepareStatement(sql)) {
                ps.setBoolean(1, chatEnabled);
                if (tier != null) ps.setString(2, tier);
                else ps.setNull(2, java.sql.Types.VARCHAR);
                ps.setString(3, uuid);
                ps.executeUpdate();
            } catch (SQLException e) { FppLogger.error("DB updateBotChatSettings: " + e.getMessage()); }
        });
    }

    /**
     * Persists a bot's right-click command to {@code fpp_active_bots}.
     * Enqueued on the background writer thread.
     *
     * @param cmd the command string (without leading '/'), or {@code null} to clear
     */
    public void updateBotRightClickCommand(String uuid, String cmd) {
        if (!isAlive()) return;
        final String c = cmd;
        enqueue(() -> {
            if (!isAlive()) return;
            String sql = "UPDATE fpp_active_bots SET right_click_cmd=? WHERE bot_uuid=?";
            try (PreparedStatement ps = connection.prepareStatement(sql)) {
                if (c != null) ps.setString(1, c);
                else ps.setNull(1, java.sql.Types.VARCHAR);
                ps.setString(2, uuid);
                ps.executeUpdate();
            } catch (SQLException e) { FppLogger.error("DB updateBotRightClickCommand: " + e.getMessage()); }
        });
    }

    /**
     * Persists a bot's head-AI enabled flag to {@code fpp_active_bots}.
     * Enqueued on the background writer thread.
     */
    public void updateBotHeadAiEnabled(String uuid, boolean enabled) {
        if (!isAlive()) return;
        enqueue(() -> {
            if (!isAlive()) return;
            String sql = "UPDATE fpp_active_bots SET head_ai_enabled=? WHERE bot_uuid=?";
            try (PreparedStatement ps = connection.prepareStatement(sql)) {
                ps.setBoolean(1, enabled);
                ps.setString(2, uuid);
                ps.executeUpdate();
            } catch (SQLException e) { FppLogger.error("DB updateBotHeadAiEnabled: " + e.getMessage()); }
        });
    }

    /**
     * Persists a bot's per-bot navigation override flags to {@code fpp_active_bots}.
     * Enqueued on the background writer thread.
     */
    public void updateBotNavSettings(String uuid, boolean navParkour, boolean navBreakBlocks, boolean navPlaceBlocks) {
        if (!isAlive()) return;
        enqueue(() -> {
            if (!isAlive()) return;
            String sql = "UPDATE fpp_active_bots SET nav_parkour=?, nav_break_blocks=?, nav_place_blocks=? WHERE bot_uuid=?";
            try (PreparedStatement ps = connection.prepareStatement(sql)) {
                ps.setBoolean(1, navParkour);
                ps.setBoolean(2, navBreakBlocks);
                ps.setBoolean(3, navPlaceBlocks);
                ps.setString(4, uuid);
                ps.executeUpdate();
            } catch (SQLException e) { FppLogger.error("DB updateBotNavSettings: " + e.getMessage()); }
        });
    }

    /**
     * Persists <em>all</em> per-bot mutable settings in a single UPDATE.
     * Use this when multiple fields change at once (e.g. restore from snapshot).
     * Enqueued on the background writer thread.
     */
    public void updateBotAllSettings(String uuid,
                                     boolean frozen, boolean chatEnabled, String chatTier,
                                     String rightClickCmd, String aiPersonality,
                                     boolean pickUpItems, boolean pickUpXp,
                                     boolean headAiEnabled,
                                     boolean navParkour, boolean navBreakBlocks, boolean navPlaceBlocks,
                                     boolean swimAiEnabled, int chunkLoadRadius) {
        if (!isAlive()) return;
        final String tier = chatTier, rcc = rightClickCmd, pers = aiPersonality;
        enqueue(() -> {
            if (!isAlive()) return;
            String sql = "UPDATE fpp_active_bots SET frozen=?,chat_enabled=?,chat_tier=?,right_click_cmd=?," +
                         "ai_personality=?,pickup_items=?,pickup_xp=?,head_ai_enabled=?," +
                         "nav_parkour=?,nav_break_blocks=?,nav_place_blocks=?," +
                         "swim_ai_enabled=?,chunk_load_radius=? WHERE bot_uuid=?";
            try (PreparedStatement ps = connection.prepareStatement(sql)) {
                ps.setBoolean(1, frozen);
                ps.setBoolean(2, chatEnabled);
                if (tier != null) ps.setString(3, tier); else ps.setNull(3, java.sql.Types.VARCHAR);
                if (rcc  != null) ps.setString(4, rcc);  else ps.setNull(4, java.sql.Types.VARCHAR);
                if (pers != null) ps.setString(5, pers);  else ps.setNull(5, java.sql.Types.VARCHAR);
                ps.setBoolean(6, pickUpItems);
                ps.setBoolean(7, pickUpXp);
                ps.setBoolean(8, headAiEnabled);
                ps.setBoolean(9, navParkour);
                ps.setBoolean(10, navBreakBlocks);
                ps.setBoolean(11, navPlaceBlocks);
                ps.setBoolean(12, swimAiEnabled);
                ps.setInt(13, chunkLoadRadius);
                ps.setString(14, uuid);
                ps.executeUpdate();
            } catch (SQLException e) { FppLogger.error("DB updateBotAllSettings: " + e.getMessage()); }
        });
    }

    // ── Accessors ─────────────────────────────────────────────────────────────
    public boolean isMysql() { return isMysql; }
    public Map<String, BotRecord> getActiveRecords() { return Collections.unmodifiableMap(activeRecords); }

    // ── Bot task API (fpp_bot_tasks) ──────────────────────────────────────────

    /**
     * Saves a snapshot of all active bot tasks for this server to {@code fpp_bot_tasks}.
     * Replaces all existing rows for this server in one write — call once on shutdown
     * and on demand (e.g. from {@code BotPersistence.saveAsync}).
     *
     * <p>Each element in {@code rows} represents one running task type for one bot
     * (e.g. MINE, USE, PLACE, PATROL).  Multiple rows can exist per bot UUID.</p>
     */
    public void saveBotTasks(List<BotTaskRow> rows) {
        if (!isAlive()) return;
        final List<BotTaskRow> snap = new ArrayList<>(rows);
        final String sid = Config.serverId();
        enqueue(() -> {
            if (!isAlive()) return;
            // 1. Clear existing task rows for this server
            try (PreparedStatement ps = connection.prepareStatement(
                    "DELETE FROM fpp_bot_tasks WHERE server_id=?")) {
                ps.setString(1, sid);
                ps.executeUpdate();
            } catch (SQLException e) { FppLogger.error("DB saveBotTasks (clear): " + e.getMessage()); return; }
            if (snap.isEmpty()) return;
            // 2. Insert fresh rows
            String sql = isMysql
                    ? "INSERT INTO fpp_bot_tasks " +
                      "(bot_uuid,server_id,task_type,world_name,pos_x,pos_y,pos_z,pos_yaw,pos_pitch,once_flag,extra_str,extra_bool) " +
                      "VALUES (?,?,?,?,?,?,?,?,?,?,?,?) " +
                      "ON DUPLICATE KEY UPDATE world_name=VALUES(world_name),pos_x=VALUES(pos_x)," +
                      "pos_y=VALUES(pos_y),pos_z=VALUES(pos_z),pos_yaw=VALUES(pos_yaw)," +
                      "pos_pitch=VALUES(pos_pitch),once_flag=VALUES(once_flag)," +
                      "extra_str=VALUES(extra_str),extra_bool=VALUES(extra_bool)"
                    : "INSERT OR REPLACE INTO fpp_bot_tasks " +
                      "(bot_uuid,server_id,task_type,world_name,pos_x,pos_y,pos_z,pos_yaw,pos_pitch,once_flag,extra_str,extra_bool) " +
                      "VALUES (?,?,?,?,?,?,?,?,?,?,?,?)";
            for (BotTaskRow row : snap) {
                try (PreparedStatement ps = connection.prepareStatement(sql)) {
                    ps.setString(1, row.botUuid());
                    ps.setString(2, row.serverId());
                    ps.setString(3, row.taskType());
                    if (row.worldName() != null) ps.setString(4, row.worldName()); else ps.setNull(4, java.sql.Types.VARCHAR);
                    ps.setDouble(5, row.posX());
                    ps.setDouble(6, row.posY());
                    ps.setDouble(7, row.posZ());
                    ps.setFloat(8, row.posYaw());
                    ps.setFloat(9, row.posPitch());
                    ps.setBoolean(10, row.onceFlag());
                    if (row.extraStr() != null) ps.setString(11, row.extraStr()); else ps.setNull(11, java.sql.Types.VARCHAR);
                    ps.setBoolean(12, row.extraBool());
                    ps.executeUpdate();
                } catch (SQLException e) { FppLogger.error("DB saveBotTask(" + row.taskType() + "): " + e.getMessage()); }
            }
            Config.debug("DB saved " + snap.size() + " bot task row(s) for server='" + sid + "'.");
        });
    }

    /**
     * Returns all task rows from {@code fpp_bot_tasks} scoped to this server.
     * Used during startup restore to resume active tasks.
     * Runs synchronously — call from the main thread or a dedicated read thread.
     */
    public List<BotTaskRow> loadBotTasksForThisServer() {
        List<BotTaskRow> list = new ArrayList<>();
        if (!isAlive()) return list;
        String sql = "SELECT * FROM fpp_bot_tasks WHERE server_id=?";
        try (PreparedStatement ps = connection.prepareStatement(sql)) {
            ps.setString(1, Config.serverId());
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) list.add(mapBotTaskRow(rs));
            }
        } catch (SQLException e) { FppLogger.error("DB loadBotTasksForThisServer: " + e.getMessage()); }
        return list;
    }

    /**
     * Clears all task rows for this server.
     * Called after tasks have been loaded on startup so they don't re-restore
     * on the next restart (same pattern as {@link #clearActiveBots()}).
     */
    public void clearBotTasks() {
        final String sid = Config.serverId();
        enqueue(() -> {
            if (!isAlive()) return;
            try (PreparedStatement ps = connection.prepareStatement(
                    "DELETE FROM fpp_bot_tasks WHERE server_id=?")) {
                ps.setString(1, sid);
                int rows = ps.executeUpdate();
                Config.debug("DB cleared " + rows + " bot task row(s) for server='" + sid + "'.");
            } catch (SQLException e) { FppLogger.error("DB clearBotTasks: " + e.getMessage()); }
        });
    }

    private BotTaskRow mapBotTaskRow(ResultSet rs) throws SQLException {
        String worldName = null;
        try { worldName = rs.getString("world_name"); } catch (SQLException ignored) {}
        String extraStr = null;
        try { extraStr = rs.getString("extra_str"); } catch (SQLException ignored) {}
        boolean extraBool = false;
        try { extraBool = rs.getBoolean("extra_bool"); } catch (SQLException ignored) {}
        return new BotTaskRow(
                rs.getString("bot_uuid"),
                rs.getString("server_id"),
                rs.getString("task_type"),
                worldName,
                rs.getDouble("pos_x"),
                rs.getDouble("pos_y"),
                rs.getDouble("pos_z"),
                rs.getFloat("pos_yaw"),
                rs.getFloat("pos_pitch"),
                rs.getBoolean("once_flag"),
                extraStr,
                extraBool
        );
    }

    // ── Inner types ───────────────────────────────────────────────────────────
    private record PendingLocation(String world, double x, double y, double z, float yaw, float pitch) {}

    /** Row from fpp_active_bots - used for startup restore. */
    public record ActiveBotRow(
            String botUuid, String botName, String botDisplay,
            String spawnedBy, String spawnedByUuid,
            String world, double x, double y, double z,
            float yaw, float pitch,
            String luckpermsGroup,
            String serverId,
            String aiPersonality,  // nullable — null means no personality assigned yet
            boolean pickUpItems,
            boolean pickUpXp,
            boolean frozen,
            boolean chatEnabled,
            String chatTier,       // nullable
            String rightClickCmd,  // nullable
            boolean headAiEnabled,
            boolean navParkour,
            boolean navBreakBlocks,
            boolean navPlaceBlocks,
            boolean swimAiEnabled,
            int chunkLoadRadius    // -1 = use global config
    ) {}

    /**
     * Row from {@code fpp_sleeping_bots} - used for peak-hours crash-recovery restore.
     *
     * @param sleepOrder insertion order (FIFO index, 0-based)
     * @param botName    internal bot name
     * @param world      world name
     * @param x          X coordinate
     * @param y          Y coordinate
     * @param z          Z coordinate
     * @param yaw        yaw (degrees)
     * @param pitch      pitch (degrees)
     */
    public record SleepingBotRow(
            int    sleepOrder,
            String botName,
            String world,
            double x, double y, double z,
            float  yaw, float pitch
    ) {}

    /**
     * Row from {@code fpp_bot_tasks} — one row per running task type per bot.
     *
     * <p>Task types: {@code MINE}, {@code USE}, {@code PLACE}, {@code PATROL}.</p>
     *
     * <ul>
     *   <li>{@code worldName}/{@code posX/Y/Z}/{@code posYaw/Pitch} — target location
     *       (null/zero for PATROL tasks which use extra fields instead)</li>
     *   <li>{@code onceFlag} — true = run once then stop (MINE/USE/PLACE)</li>
     *   <li>{@code extraStr} — patrol route name (PATROL) or reserved</li>
     *   <li>{@code extraBool} — patrol random flag (PATROL) or reserved</li>
     * </ul>
     */
    public record BotTaskRow(
            String  botUuid,
            String  serverId,
            String  taskType,
            String  worldName,
            double  posX, double posY, double posZ,
            float   posYaw, float posPitch,
            boolean onceFlag,
            String  extraStr,
            boolean extraBool
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

