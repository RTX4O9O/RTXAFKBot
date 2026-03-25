package me.bill.fakePlayerPlugin.fakeplayer;

import me.bill.fakePlayerPlugin.FakePlayerPlugin;
import me.bill.fakePlayerPlugin.config.Config;
import me.bill.fakePlayerPlugin.util.FppLogger;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.NamespacedKey;
import org.bukkit.World;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.persistence.PersistentDataType;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/**
 * Saves the list of active fake players to disk on shutdown and reloads
 * them on the next startup, so bots persist across server restarts.
 *
 * <p>Storage: {@code plugins/FakePlayerPlugin/data/active-bots.yml}
 *
 * <p>Each entry stores:
 * <ul>
 *   <li>Bot name</li>
 *   <li>Spawn world + coordinates + yaw/pitch</li>
 *   <li>Who originally spawned it</li>
 *   <li>The bot's UUID (reused on restore so DB records stay linked)</li>
 * </ul>
 */
public final class BotPersistence {

    private static final String FILE_NAME = "active-bots.yml";

    private final File           dataFile;
    private final FakePlayerPlugin plugin;

    public BotPersistence(FakePlayerPlugin plugin) {
        this.plugin   = plugin;
        File dataDir  = new File(plugin.getDataFolder(), "data");
        if (!dataDir.exists() && !dataDir.mkdirs()) {
            FppLogger.warn("BotPersistence: could not create data directory: " + dataDir.getAbsolutePath());
        }
        this.dataFile = new File(dataDir, FILE_NAME);
    }

    // ── Save ─────────────────────────────────────────────────────────────────

    /**
     * Writes all currently active fake players to {@code active-bots.yml}.
     * Called from {@code onDisable} before entities are removed.
     */
    public void save(Iterable<FakePlayer> players) {
        saveInternal(players);
    }

    /**
     * Saves the active bot list asynchronously so it can be called after every
     * spawn/remove without stalling the main thread. This ensures the file is
     * always up-to-date even if the server crashes.
     */
    public void saveAsync(Iterable<FakePlayer> players) {
        // Snapshot locations on the main thread first (entity access is not thread-safe)
        List<Object> list = buildList(players);
        Bukkit.getScheduler().runTaskAsynchronously(plugin, () -> {
            YamlConfiguration yaml = new YamlConfiguration();
            yaml.set("bots", list);
            try {
                yaml.save(dataFile);
            } catch (IOException e) {
                FppLogger.error("Failed to auto-save active bots: " + e.getMessage());
            }
        });
    }

    private void saveInternal(Iterable<FakePlayer> players) {
        YamlConfiguration yaml = new YamlConfiguration();
        yaml.set("bots", buildList(players));
        try {
            yaml.save(dataFile);
            FppLogger.info("Saved bot list to " + FILE_NAME + ".");
        } catch (IOException e) {
            FppLogger.error("Failed to save active bots: " + e.getMessage());
        }
    }

    private List<Object> buildList(Iterable<FakePlayer> players) {
        List<Object> list = new ArrayList<>();
        for (FakePlayer fp : players) {
            org.bukkit.entity.Entity body = fp.getPhysicsEntity();
            Location loc = (body != null && body.isValid())
                    ? body.getLocation()
                    : fp.getSpawnLocation();
            if (loc == null || loc.getWorld() == null) continue;

            var section = new java.util.LinkedHashMap<String, Object>();
            section.put("name",            fp.getName());
            section.put("uuid",            fp.getUuid().toString());
            section.put("display-name",    fp.getDisplayName());
            section.put("spawned-by",      fp.getSpawnedBy());
            section.put("spawned-by-uuid", fp.getSpawnedByUuid().toString());
            section.put("world",           loc.getWorld().getName());
            section.put("x",               loc.getX());
            section.put("y",               loc.getY());
            section.put("z",               loc.getZ());
            section.put("yaw",             (double) loc.getYaw());
            section.put("pitch",           (double) loc.getPitch());
            if (fp.getLuckpermsGroup() != null)
                section.put("luckperms-group", fp.getLuckpermsGroup());
            list.add(section);
        }
        return list;
    }

    // ── Restore ───────────────────────────────────────────────────────────────

    /**
     * Reads {@code active-bots.yml} and schedules a staggered restore of all
     * saved bots. The file is deleted after loading so bots only restore once.
     *
     * <p>Called from {@code onEnable} — deferred by 40 ticks (2 seconds) to
     * ensure the world is fully loaded before spawning entities.
     */
    public void restore(FakePlayerManager manager) {
        if (!Config.persistOnRestart()) {
            deleteFile(dataFile);
            FppLogger.info("Bot persistence is disabled — skipping restore.");
            return;
        }

        // Set flag to indicate restoration is in progress — players joining during this window
        // will defer tab-list syncing until the post-restore prefix refresh completes.
        manager.setRestorationInProgress(true);

        // ── Primary: restore from database fpp_active_bots ───────────────────
        me.bill.fakePlayerPlugin.database.DatabaseManager db =
                plugin.getDatabaseManager();
        if (db != null) {
            List<me.bill.fakePlayerPlugin.database.DatabaseManager.ActiveBotRow> rows =
                    db.getActiveBots();
            if (!rows.isEmpty()) {
                FppLogger.info("Restoring " + rows.size() + " bot(s) from database...");
                // Clear the DB table immediately — bots are now being restored
                db.clearActiveBots();
                // Also discard the YAML file so we don't double-restore
                deleteFile(dataFile);

                List<SavedBot> saved = new ArrayList<>();
                for (var row : rows) {
                    try {
                        saved.add(new SavedBot(
                                row.botName(),
                                UUID.fromString(row.botUuid()),
                                row.botDisplay(),
                                row.spawnedBy(),
                                UUID.fromString(row.spawnedByUuid()),
                                row.world(),
                                row.x(), row.y(), row.z(),
                                row.yaw(), row.pitch(),
                                row.luckpermsGroup()   // restore LP group override
                        ));
                    } catch (Exception e) {
                        FppLogger.warn("Skipping malformed DB active-bot row: " + e.getMessage());
                    }
                }
                if (!saved.isEmpty()) {
                    Bukkit.getScheduler().runTaskLater(plugin,
                            () -> restoreChain(manager, saved, 0), 40L);
                }
                return;
            }
        }

        // ── Fallback: restore from YAML file (no DB or empty active_bots) ────
        if (!dataFile.exists()) return;

        YamlConfiguration yaml = YamlConfiguration.loadConfiguration(dataFile);
        List<?> raw = yaml.getList("bots");
        if (raw == null || raw.isEmpty()) { deleteFile(dataFile); return; }

        List<SavedBot> saved = new ArrayList<>();
        for (Object obj : raw) {
            if (!(obj instanceof java.util.Map<?, ?> map)) continue;
            try {
                String name          = (String) map.get("name");
                UUID   uuid          = UUID.fromString((String) map.get("uuid"));
                String displayName   = (String) map.get("display-name");  // Load saved display name
                Object sbRaw         = map.get("spawned-by");
                String spawnedBy     = sbRaw instanceof String s ? s : "SERVER";
                Object sbuRaw        = map.get("spawned-by-uuid");
                UUID   spawnedByUuid = sbuRaw instanceof String str
                        ? UUID.fromString(str) : new UUID(0, 0);
                String worldName     = (String) map.get("world");
                double x             = toDouble(map.get("x"));
                double y             = toDouble(map.get("y"));
                double z             = toDouble(map.get("z"));
                float  yaw           = (float) toDouble(map.get("yaw"));
                float  pitch         = (float) toDouble(map.get("pitch"));
                String luckpermsGroup = (String) map.get("luckperms-group");  // Load LP group override
                if (name == null || worldName == null) continue;
                saved.add(new SavedBot(name, uuid, displayName, spawnedBy, spawnedByUuid,
                        worldName, x, y, z, yaw, pitch, luckpermsGroup));
            } catch (Exception e) {
                FppLogger.warn("Skipping malformed bot entry in " + FILE_NAME + ": " + e.getMessage());
            }
        }
        deleteFile(dataFile);
        if (saved.isEmpty()) return;

        FppLogger.info("Restoring " + saved.size() + " bot(s) from YAML fallback...");
        Bukkit.getScheduler().runTaskLater(plugin,
                () -> restoreChain(manager, saved, 0), 40L);
    }

    /** Spawns one saved bot and schedules the next with a random join-delay. */
    private void restoreChain(FakePlayerManager manager, List<SavedBot> saved, int index) {
        if (index >= saved.size()) {
            // All bots have been queued — schedule one final prefix refresh 1 second later.
            // This re-runs LP data resolution with a clean cache so every restored bot
            // gets its correct coloured rank even if LP hadn't fully settled at restore time.
            Bukkit.getScheduler().runTaskLater(plugin, () -> {
                me.bill.fakePlayerPlugin.util.LuckPermsHelper.invalidateCache();
                int n = manager.updateAllBotPrefixes();
                if (n > 0)
                    FppLogger.info("Post-restore prefix refresh: " + n + " bot(s) updated.");
                // Signal that restoration is complete so players can now see bots correctly
                manager.setRestorationInProgress(false);
            }, 20L);
            return;
        }

        SavedBot sb = saved.get(index);

        World world = Bukkit.getWorld(sb.worldName);
        if (world == null) {
            FppLogger.warn("Cannot restore bot '" + sb.name
                    + "' — world '" + sb.worldName + "' not found. Skipping.");
            restoreChain(manager, saved, index + 1);
            return;
        }

        Location loc = new Location(world, sb.x, sb.y, sb.z, sb.yaw, sb.pitch);

        // Spawn with restored UUID, display name, and LP group override
        manager.spawnRestored(sb.name, sb.uuid, sb.displayName, sb.spawnedBy, sb.spawnedByUuid, loc, sb.luckpermsGroup);

        // Stagger: use the configured join-delay range — values are TICKS (20 = 1 second)
        int delayMinTicks = Config.joinDelayMin();
        int delayMaxTicks = Math.max(delayMinTicks, Config.joinDelayMax());
        long delayTicks;
        if (delayMaxTicks <= 0) {
            delayTicks = 1L; // at least 1 tick so world updates propagate
        } else {
            int spread = delayMaxTicks - delayMinTicks;
            int t = delayMinTicks + (spread > 0
                    ? java.util.concurrent.ThreadLocalRandom.current().nextInt(spread + 1)
                    : 0);
            delayTicks = Math.max(1L, (long) t);
        }

        Bukkit.getScheduler().runTaskLater(plugin,
                () -> restoreChain(manager, saved, index + 1), delayTicks);
    }

    // ── Purge + Restore ───────────────────────────────────────────────────────

    /**
     * Entry point called from {@code onEnable}.
     * <ol>
     *   <li>Sweeps every loaded world for Mannequin entities tagged with
     *       {@link FakePlayerManager#FAKE_PLAYER_KEY} and removes them.
     *       This cleans up bodies left by a crash before restoration runs.</li>
     *   <li>Then calls {@link #restore(FakePlayerManager)} to spawn bots from
     *       the persistence file.</li>
     * </ol>
     */
    public void purgeOrphanedBodiesAndRestore(FakePlayerManager manager) {
        // Defer so worlds are fully loaded (same as restore)
        Bukkit.getScheduler().runTaskLater(plugin, () -> {
            purgeOrphanedBodies();
            // Restore after a further tick so the purge entity removal is flushed
            Bukkit.getScheduler().runTaskLater(plugin, () ->
                    restore(manager), 5L);
        }, 40L);
    }

    /**
     * Removes every Mannequin in all loaded worlds that carries the FPP PDC tag
     * but is not registered in the active bot map (i.e. a crash remnant).
     */
    private void purgeOrphanedBodies() {
        NamespacedKey key = FakePlayerManager.FAKE_PLAYER_KEY;
        if (key == null) return;

        int removed = 0;
        for (org.bukkit.World world : org.bukkit.Bukkit.getWorlds()) {
            for (org.bukkit.entity.Entity entity : world.getEntities()) {
                if (!entity.getPersistentDataContainer().has(key, PersistentDataType.STRING)) continue;
                String val = entity.getPersistentDataContainer().get(key, PersistentDataType.STRING);
                // Matches both Mannequin bodies and nametag ArmorStands
                if (val != null) {
                    entity.remove();
                    removed++;
                    Config.debug("Purged orphaned entity: " + val);
                }
            }
        }
        if (removed > 0) {
            FppLogger.info("Purged " + removed + " orphaned bot entity/entities from previous session.");
        }
    }

    // ── Helpers ───────────────────────────────────────────────────────────────

    private static double toDouble(Object o) {
        if (o instanceof Number n) return n.doubleValue();
        return 0.0;
    }

    /** Deletes a file, logging a warning if it fails. */
    private static void deleteFile(File f) {
        if (f.exists() && !f.delete()) {
            FppLogger.warn("BotPersistence: could not delete " + f.getName());
        }
    }

    // ── Inner record ─────────────────────────────────────────────────────────

    private record SavedBot(
            String name, UUID uuid,
            String displayName,  // Saved display name (with prefix) for restoration
            String spawnedBy, UUID spawnedByUuid,
            String worldName,
            double x, double y, double z,
            float yaw, float pitch,
            String luckpermsGroup  // LP group override, null for default
    ) {}
}

