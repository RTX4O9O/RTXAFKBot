package me.bill.fakePlayerPlugin.util;

import me.bill.fakePlayerPlugin.FakePlayerPlugin;
import me.bill.fakePlayerPlugin.database.BotRecord;
import me.bill.fakePlayerPlugin.database.DatabaseManager;

import java.io.*;
import java.sql.*;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.List;

/**
 * Handles database migration, merging, and export/import operations across
 * all supported FPP versions.
 *
 * <h3>Features</h3>
 * <ul>
 *   <li><b>Merge old SQLite database</b> — pull records from any previous
 *       {@code fpp.db} file into the current database without overwriting
 *       existing rows (deduplicated by primary key).</li>
 *   <li><b>CSV export</b> — dump all session history to a CSV file in the
 *       {@code exports/} folder for external analysis.</li>
 *   <li><b>SQLite → MySQL migration</b> — promote local data to a shared
 *       MySQL backend when switching deployment topology.</li>
 * </ul>
 *
 * <p>All write-heavy operations should be dispatched asynchronously by the
 * caller (e.g. via {@link org.bukkit.scheduler.BukkitScheduler}).
 */
public final class DataMigrator {

    private static final DateTimeFormatter EXPORT_FMT =
            DateTimeFormatter.ofPattern("yyyy-MM-dd_HH-mm-ss");

    private DataMigrator() {}

    // ── Public API ────────────────────────────────────────────────────────────

    /**
     * Merges all session and active-bot rows from an old SQLite {@code fpp.db}
     * into the currently active database (SQLite or MySQL).
     *
     * <p>The operation is safe to run while the server is live:
     * <ul>
     *   <li>Only inserts rows — never updates or deletes existing data.</li>
     *   <li>Deduplication is handled by {@code INSERT OR IGNORE} / {@code INSERT IGNORE}.</li>
     *   <li>A full backup is created before any rows are written.</li>
     * </ul>
     *
     * @param plugin  Plugin instance.
     * @param manager Current active {@link DatabaseManager}.
     * @param srcFile Path to the source {@code fpp.db} file to read from.
     * @return Total number of rows inserted, or {@code -1} on failure.
     */
    public static int mergeFromSQLite(FakePlayerPlugin plugin,
                                      DatabaseManager manager,
                                      File srcFile) {
        if (!srcFile.exists()) {
            FppLogger.error("DataMigrator: source file not found: " + srcFile.getAbsolutePath());
            return -1;
        }
        if (srcFile.getAbsolutePath().equals(
                new File(plugin.getDataFolder(), "data/fpp.db").getAbsolutePath())) {
            FppLogger.warn("DataMigrator: source file is the same as the active database — aborted.");
            return -1;
        }

        FppLogger.info("DataMigrator: creating pre-merge backup…");
        BackupManager.createFullBackup(plugin, "pre-db-merge");

        Connection oldConn = null;
        try {
            Class.forName("org.sqlite.JDBC");
            oldConn = DriverManager.getConnection("jdbc:sqlite:" + srcFile.getAbsolutePath());

            int sessions = mergeSessions(oldConn, manager);
            int active   = mergeActiveBots(oldConn, manager);
            int total    = sessions + active;

            FppLogger.success("DataMigrator: merged " + sessions + " session(s) + "
                    + active + " active-bot row(s) from " + srcFile.getName() + ".");
            return total;

        } catch (ClassNotFoundException e) {
            FppLogger.error("DataMigrator: SQLite JDBC driver not found — " + e.getMessage());
            return -1;
        } catch (SQLException e) {
            FppLogger.error("DataMigrator: SQL error during merge — " + e.getMessage());
            return -1;
        } finally {
            if (oldConn != null) {
                try { oldConn.close(); } catch (SQLException ignored) {}
            }
        }
    }

    /**
     * Exports all session history to a CSV file in
     * {@code plugins/FakePlayerPlugin/exports/}.
     *
     * @param plugin  Plugin instance.
     * @param manager Active {@link DatabaseManager}.
     * @return The exported file, or {@code null} on failure.
     */
    public static File exportSessionsCsv(FakePlayerPlugin plugin, DatabaseManager manager) {
        File exportDir = new File(plugin.getDataFolder(), "exports");
        exportDir.mkdirs();

        String ts  = LocalDateTime.now().format(EXPORT_FMT);
        File   csv = new File(exportDir, "sessions_" + ts + ".csv");

        List<BotRecord> rows;
        try {
            rows = manager.getRecentSessions(Integer.MAX_VALUE);
        } catch (Exception e) {
            FppLogger.error("DataMigrator: failed to load sessions for export: " + e.getMessage());
            return null;
        }

        try (PrintWriter pw = new PrintWriter(new FileWriter(csv))) {
            pw.println("id,bot_name,bot_uuid,spawned_by,spawned_by_uuid," +
                       "world_name,spawn_x,spawn_y,spawn_z," +
                       "last_world,last_x,last_y,last_z," +
                       "spawned_at_ms,removed_at_ms,remove_reason");

            for (BotRecord r : rows) {
                pw.printf("%d,%s,%s,%s,%s,%s,%.4f,%.4f,%.4f,%s,%.4f,%.4f,%.4f,%d,%s,%s%n",
                        r.getId(),
                        csv(r.getBotName()), csv(r.getBotUuid().toString()),
                        csv(r.getSpawnedBy()), csv(r.getSpawnedByUuid().toString()),
                        csv(r.getWorldName()),
                        r.getSpawnX(), r.getSpawnY(), r.getSpawnZ(),
                        csv(r.getLastWorld()),
                        r.getLastX(), r.getLastY(), r.getLastZ(),
                        r.getSpawnedAt().toEpochMilli(),
                        r.getRemovedAt() != null ? r.getRemovedAt().toEpochMilli() : "",
                        csv(r.getRemoveReason() != null ? r.getRemoveReason() : ""));
            }

            FppLogger.success("DataMigrator: exported " + rows.size()
                    + " session(s) → exports/" + csv.getName());
            return csv;

        } catch (IOException e) {
            FppLogger.error("DataMigrator: CSV export failed — " + e.getMessage());
            return null;
        }
    }

    /**
     * Migrates all data from the local SQLite database into the MySQL database
     * currently configured in {@code config.yml}.
     *
     * <p>Prerequisites:
     * <ol>
     *   <li>Set {@code database.mysql-enabled: true} and fill in credentials.</li>
     *   <li>Run {@code /fpp reload} so the DatabaseManager reconnects to MySQL.</li>
     *   <li>Then run {@code /fpp migrate db tomysql}.</li>
     * </ol>
     *
     * @return Rows migrated, or {@code -1} on failure.
     */
    public static int migrateToMysql(FakePlayerPlugin plugin, DatabaseManager manager) {
        File sqliteFile = new File(plugin.getDataFolder(), "data/fpp.db");
        if (!sqliteFile.exists()) {
            FppLogger.warn("DataMigrator: no local SQLite database found to migrate.");
            return 0;
        }
        FppLogger.info("DataMigrator: starting SQLite → MySQL migration…");
        return mergeFromSQLite(plugin, manager, sqliteFile);
    }

    // ── Internal helpers ──────────────────────────────────────────────────────

    private static int mergeSessions(Connection src, DatabaseManager dst) throws SQLException {
        // Check table exists
        try {
            src.createStatement().executeQuery("SELECT COUNT(*) FROM fpp_bot_sessions").close();
        } catch (SQLException e) {
            FppLogger.debug("DataMigrator: source has no fpp_bot_sessions table.");
            return 0;
        }

        int count = 0;
        try (Statement st = src.createStatement();
             ResultSet rs = st.executeQuery(
                     "SELECT * FROM fpp_bot_sessions ORDER BY spawned_at ASC")) {
            while (rs.next()) {
                try {
                    dst.mergeSessionRow(
                            rs.getString("bot_name"),
                            safeStr(rs, "bot_display"),
                            rs.getString("bot_uuid"),
                            rs.getString("spawned_by"),
                            rs.getString("spawned_by_uuid"),
                            rs.getString("world_name"),
                            rs.getDouble("spawn_x"), rs.getDouble("spawn_y"), rs.getDouble("spawn_z"),
                            safeFloat(rs, "spawn_yaw"), safeFloat(rs, "spawn_pitch"),
                            safeStr(rs, "last_world"),
                            safeDouble(rs, "last_x"), safeDouble(rs, "last_y"), safeDouble(rs, "last_z"),
                            safeFloat(rs, "last_yaw"), safeFloat(rs, "last_pitch"),
                            safeStr(rs, "entity_type"),
                            rs.getLong("spawned_at"),
                            safeNullableLong(rs, "removed_at"),
                            safeStr(rs, "remove_reason"),
                            safeStr(rs, "server_id"));   // null → mergeSessionRow defaults to Config.serverId()
                    count++;
                } catch (Exception e) {
                    FppLogger.debug("DataMigrator: skipped session row: " + e.getMessage());
                }
            }
        }
        return count;
    }

    private static int mergeActiveBots(Connection src, DatabaseManager dst) throws SQLException {
        try {
            src.createStatement().executeQuery("SELECT COUNT(*) FROM fpp_active_bots").close();
        } catch (SQLException e) {
            FppLogger.debug("DataMigrator: source has no fpp_active_bots table.");
            return 0;
        }

        int count = 0;
        try (Statement st = src.createStatement();
             ResultSet rs = st.executeQuery("SELECT * FROM fpp_active_bots")) {
            while (rs.next()) {
                try {
                    dst.mergeActiveBotRow(
                            rs.getString("bot_uuid"),
                            rs.getString("bot_name"),
                            safeStr(rs, "bot_display"),
                            rs.getString("spawned_by"),
                            rs.getString("spawned_by_uuid"),
                            rs.getString("world_name"),
                            rs.getDouble("pos_x"), rs.getDouble("pos_y"), rs.getDouble("pos_z"),
                            safeFloat(rs, "pos_yaw"), safeFloat(rs, "pos_pitch"),
                            rs.getLong("updated_at"),
                            safeStr(rs, "server_id"));   // null → mergeActiveBotRow defaults to Config.serverId()
                    count++;
                } catch (Exception e) {
                    FppLogger.debug("DataMigrator: skipped active_bot row: " + e.getMessage());
                }
            }
        }
        return count;
    }

    // ── ResultSet helpers (absorb missing columns gracefully) ─────────────────

    private static String safeStr(ResultSet rs, String col) {
        try { return rs.getString(col); } catch (SQLException e) { return null; }
    }

    private static double safeDouble(ResultSet rs, String col) {
        try { double v = rs.getDouble(col); return rs.wasNull() ? 0.0 : v; }
        catch (SQLException e) { return 0.0; }
    }

    private static float safeFloat(ResultSet rs, String col) {
        try { float v = rs.getFloat(col); return rs.wasNull() ? 0f : v; }
        catch (SQLException e) { return 0f; }
    }

    private static Long safeNullableLong(ResultSet rs, String col) {
        try {
            long v = rs.getLong(col);
            return rs.wasNull() ? null : v;
        } catch (SQLException e) { return null; }
    }

    /** Minimal CSV escaping: wraps value in quotes if it contains commas or quotes. */
    private static String csv(String value) {
        if (value == null) return "";
        if (value.contains(",") || value.contains("\"") || value.contains("\n")) {
            return '"' + value.replace("\"", "\"\"") + '"';
        }
        return value;
    }
}

