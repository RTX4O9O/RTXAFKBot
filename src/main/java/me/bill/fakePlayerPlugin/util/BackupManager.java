package me.bill.fakePlayerPlugin.util;

import me.bill.fakePlayerPlugin.FakePlayerPlugin;

import java.io.*;
import java.nio.file.*;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.*;

/**
 * Creates timestamped, self-cleaning backups of all plugin data files.
 *
 * <h3>What is backed up</h3>
 * <ul>
 *   <li>{@code config.yml}</li>
 *   <li>{@code bot-names.yml}</li>
 *   <li>{@code bot-messages.yml}</li>
 *   <li>{@code language/en.yml} (and any other lang files present)</li>
 *   <li>{@code data/active-bots.yml}</li>
 *   <li>{@code data/fpp.db} (SQLite — copied as-is, safe because we're
 *       only reading; no WAL-torn state possible with a plain file copy
 *       on shutdown where the DB is already closed)</li>
 * </ul>
 *
 * <h3>Retention</h3>
 * A rolling window of {@value #MAX_BACKUPS} backup sets is kept.
 * The oldest sets are pruned automatically after every new backup.
 *
 * <h3>Layout</h3>
 * {@code plugins/FakePlayerPlugin/backups/<timestamp>_<reason>/}
 */
public final class BackupManager {

    private static final DateTimeFormatter DATE_FMT =
            DateTimeFormatter.ofPattern("yyyy-MM-dd_HH-mm-ss");

    /** Maximum number of backup directories to keep before pruning the oldest. */
    private static final int MAX_BACKUPS = 10;

    private BackupManager() {}

    /**
     * Creates a lightweight backup of only the YAML config files —
     * {@code config.yml}, {@code bot-names.yml}, {@code bot-messages.yml}, and
     * all files under {@code language/}.
     *
     * <p>Use this before non-destructive sync operations (e.g. {@code /fpp migrate lang})
     * where a full database backup is unnecessary.
     *
     * @param plugin Plugin instance.
     * @param reason Short label appended to the directory name.
     * @return The  backup directory that was created.
     */
    public static File createConfigFilesBackup(FakePlayerPlugin plugin, String reason) {
        String safeReason = reason.replaceAll("[^a-zA-Z0-9_\\-]", "_");
        String timestamp  = LocalDateTime.now().format(DATE_FMT);
        File backupDir    = new File(plugin.getDataFolder(), "backups/" + timestamp + "_" + safeReason);
        backupDir.mkdirs();

        File dataFolder = plugin.getDataFolder();

        copyFile(new File(dataFolder, "config.yml"),       new File(backupDir, "config.yml"));
        copyFile(new File(dataFolder, "bot-names.yml"),    new File(backupDir, "bot-names.yml"));
        copyFile(new File(dataFolder, "bot-messages.yml"), new File(backupDir, "bot-messages.yml"));

        File langDir = new File(dataFolder, "language");
        if (langDir.isDirectory()) {
            File langBackup = new File(backupDir, "language");
            langBackup.mkdirs();
            File[] langFiles = langDir.listFiles((d, n) -> n.endsWith(".yml"));
            if (langFiles != null) {
                for (File lf : langFiles) copyFile(lf, new File(langBackup, lf.getName()));
            }
        }

        writeManifest(backupDir, plugin, reason);
        pruneOldBackups(new File(dataFolder, "backups"));
        FppLogger.success("Config-files backup created → backups/" + backupDir.getName() + "/");
        return backupDir;
    }

    // ── Public API ────────────────────────────────────────────────────────────

    /**
     * Creates a full timestamped backup of all plugin files.
     *
     * @param plugin Plugin instance.
     * @param reason Short label appended to the directory name (e.g. {@code "pre-migration"}).
     * @return The backup directory that was created.
     */
    public static File createFullBackup(FakePlayerPlugin plugin, String reason) {
        return createFullBackup(plugin, reason, true);
    }

    /**
     * Creates a full timestamped backup of all plugin files with optional console announcement.
     */
    public static File createFullBackup(FakePlayerPlugin plugin, String reason, boolean announce) {
        String safeReason = reason.replaceAll("[^a-zA-Z0-9_\\-]", "_");
        String timestamp  = LocalDateTime.now().format(DATE_FMT);
        File backupDir    = new File(plugin.getDataFolder(), "backups/" + timestamp + "_" + safeReason);
        backupDir.mkdirs();

        File dataFolder = plugin.getDataFolder();

        // ── Config files ──────────────────────────────────────────────────────
        copyFile(new File(dataFolder, "config.yml"),          new File(backupDir, "config.yml"));
        copyFile(new File(dataFolder, "bot-names.yml"),       new File(backupDir, "bot-names.yml"));
        copyFile(new File(dataFolder, "bot-messages.yml"),    new File(backupDir, "bot-messages.yml"));

        // ── Language files ────────────────────────────────────────────────────
        File langDir = new File(dataFolder, "language");
        if (langDir.isDirectory()) {
            File langBackup = new File(backupDir, "language");
            langBackup.mkdirs();
            File[] langFiles = langDir.listFiles((d, n) -> n.endsWith(".yml"));
            if (langFiles != null) {
                for (File lf : langFiles) copyFile(lf, new File(langBackup, lf.getName()));
            }
        }

        // ── Persistence YAML ──────────────────────────────────────────────────
        copyFile(new File(dataFolder, "data/active-bots.yml"), new File(backupDir, "active-bots.yml"));

        // ── SQLite database ───────────────────────────────────────────────────
        File dbFile = new File(dataFolder, "data/fpp.db");
        if (dbFile.exists()) {
            copyFile(dbFile, new File(backupDir, "fpp.db"));
            // Also copy WAL and SHM files if present (needed for consistency)
            copyFile(new File(dataFolder, "data/fpp.db-wal"),  new File(backupDir, "fpp.db-wal"));
            copyFile(new File(dataFolder, "data/fpp.db-shm"),  new File(backupDir, "fpp.db-shm"));
        }

        // ── Write a manifest ──────────────────────────────────────────────────
        writeManifest(backupDir, plugin, reason);

        // ── Prune old backups ─────────────────────────────────────────────────
        pruneOldBackups(new File(dataFolder, "backups"));

        if (announce) {
            FppLogger.success("Backup created → backups/" + backupDir.getName() + "/");
        } else {
            FppLogger.debug("Backup created → backups/" + backupDir.getName() + "/");
        }
        return backupDir;
    }

    /**
     * Returns a list of existing backup directory names, newest first.
     */
    public static List<String> listBackups(FakePlayerPlugin plugin) {
        File backupsDir = new File(plugin.getDataFolder(), "backups");
        if (!backupsDir.isDirectory()) return List.of();

        File[] dirs = backupsDir.listFiles(File::isDirectory);
        if (dirs == null || dirs.length == 0) return List.of();

        // Sort descending (newest first — timestamp prefix makes alphabetical == chronological)
        Arrays.sort(dirs, Comparator.comparing(File::getName).reversed());
        List<String> names = new ArrayList<>(dirs.length);
        for (File d : dirs) names.add(d.getName());
        return Collections.unmodifiableList(names);
    }

    /**
     * Returns total size (bytes) of all backups on disk.
     */
    public static long totalBackupSizeBytes(FakePlayerPlugin plugin) {
        File backupsDir = new File(plugin.getDataFolder(), "backups");
        return dirSize(backupsDir);
    }

    // ── Internal helpers ──────────────────────────────────────────────────────

    private static void copyFile(File src, File dst) {
        if (!src.exists()) return;
        try {
            dst.getParentFile().mkdirs();
            Files.copy(src.toPath(), dst.toPath(), StandardCopyOption.REPLACE_EXISTING);
        } catch (IOException e) {
            FppLogger.debug("BackupManager: could not copy " + src.getName() + ": " + e.getMessage());
        }
    }

    private static void writeManifest(File backupDir, FakePlayerPlugin plugin, String reason) {
        File manifest = new File(backupDir, "MANIFEST.txt");
        try (PrintWriter pw = new PrintWriter(new FileWriter(manifest))) {
            pw.println("FakePlayerPlugin Backup Manifest");
            pw.println("================================");
            pw.println("Plugin version : " + plugin.getPluginMeta().getVersion());
            pw.println("Backup reason  : " + reason);
            pw.println("Timestamp      : " + LocalDateTime.now());
            pw.println("Server version : " + plugin.getServer().getVersion());
        } catch (IOException e) {
            FppLogger.debug("BackupManager: could not write manifest: " + e.getMessage());
        }
    }

    private static void pruneOldBackups(File backupsDir) {
        if (!backupsDir.isDirectory()) return;
        File[] dirs = backupsDir.listFiles(File::isDirectory);
        if (dirs == null || dirs.length <= MAX_BACKUPS) return;

        // Sort ascending (oldest first)
        Arrays.sort(dirs, Comparator.comparing(File::getName));
        int toDelete = dirs.length - MAX_BACKUPS;
        for (int i = 0; i < toDelete; i++) {
            deleteDirectory(dirs[i]);
            FppLogger.debug("BackupManager: pruned old backup: " + dirs[i].getName());
        }
    }

    private static void deleteDirectory(File dir) {
        File[] files = dir.listFiles();
        if (files != null) {
            for (File f : files) {
                if (f.isDirectory()) deleteDirectory(f);
                else f.delete();
            }
        }
        dir.delete();
    }

    private static long dirSize(File dir) {
        if (!dir.exists()) return 0L;
        long size = 0L;
        File[] files = dir.listFiles();
        if (files != null) {
            for (File f : files) {
                size += f.isDirectory() ? dirSize(f) : f.length();
            }
        }
        return size;
    }
}

