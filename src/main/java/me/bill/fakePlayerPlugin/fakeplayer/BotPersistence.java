package me.bill.fakePlayerPlugin.fakeplayer;

import me.bill.fakePlayerPlugin.FakePlayerPlugin;
import me.bill.fakePlayerPlugin.config.Config;
import me.bill.fakePlayerPlugin.util.FppLogger;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.World;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.PlayerInventory;
import org.bukkit.persistence.PersistentDataType;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Base64;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Saves the list of active fake players to disk on shutdown and reloads
 * them on the next startup, so bots persist across server restarts.
 *
 * <p>Storage:
 * <ul>
 *   <li>{@code plugins/FakePlayerPlugin/data/active-bots.yml} — bot identity, location, type</li>
 *   <li>{@code plugins/FakePlayerPlugin/data/bot-inventories.yml} — full inventory per bot UUID</li>
 * </ul>
 *
 * <p>Each entry stores:
 * <ul>
 *   <li>Bot name</li>
 *   <li>Spawn world + coordinates + yaw/pitch</li>
 *   <li>Who originally spawned it</li>
 *   <li>The bot's UUID (reused on restore so DB records stay linked)</li>
 *   <li>Full inventory contents (all 41 slots, base64-encoded NBT)</li>
 * </ul>
 */
public final class BotPersistence {

    private static final String FILE_NAME      = "active-bots.yml";
    private static final String INV_FILE_NAME  = "bot-inventories.yml";

    private final File            dataFile;
    private final File            inventoryFile;
    private final FakePlayerPlugin plugin;

    /**
     * Populated once at the start of a restore cycle and cleared when done.
     * Key = UUID string, Value = map of slot-index → base64-encoded ItemStack bytes.
     */
    private Map<String, Map<String, String>> loadedInventories = null;

    public BotPersistence(FakePlayerPlugin plugin) {
        this.plugin   = plugin;
        File dataDir  = new File(plugin.getDataFolder(), "data");
        if (!dataDir.exists() && !dataDir.mkdirs()) {
            FppLogger.warn("BotPersistence: could not create data directory: " + dataDir.getAbsolutePath());
        }
        this.dataFile      = new File(dataDir, FILE_NAME);
        this.inventoryFile = new File(dataDir, INV_FILE_NAME);
    }

    // ── Save ─────────────────────────────────────────────────────────────────

    /**
     * Writes all currently active fake players to {@code active-bots.yml}
     * and their inventories to {@code bot-inventories.yml}.
     * Called from {@code onDisable} before entities are removed.
     */
    public void save(Iterable<FakePlayer> players) {
        saveInternal(players);
        saveInventoriesInternal(players);
    }

    /**
     * Saves the active bot list asynchronously so it can be called after every
     * spawn/remove without stalling the main thread. This ensures the file is
     * always up-to-date even if the server crashes.
     */
    public void saveAsync(Iterable<FakePlayer> players) {
        // Snapshot locations and inventories on the main thread first
        // (entity access is not thread-safe)
        List<Object> list          = buildList(players);
        Map<String, Map<String, String>> invSnap = snapshotInventories(players);
        Bukkit.getScheduler().runTaskAsynchronously(plugin, () -> {
            YamlConfiguration yaml = new YamlConfiguration();
            yaml.set("bots", list);
            try {
                yaml.save(dataFile);
            } catch (IOException e) {
                FppLogger.error("Failed to auto-save active bots: " + e.getMessage());
            }
            writeInventorySnapshot(invSnap);
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

    /**
     * Serialises every active bot's inventory (main, armour, offhand) to
     * {@code bot-inventories.yml}.  Each item is stored as a base64-encoded NBT
     * byte string under {@code inventories.<UUID>.<slot>}.
     * Called on the main thread so entity access is safe.
     */
    private void saveInventoriesInternal(Iterable<FakePlayer> players) {
        writeInventorySnapshot(snapshotInventories(players));
    }

    /**
     * Builds an in-memory snapshot of all bot inventories on the <em>calling</em>
     * (main) thread, then returns it so the data can be written on a background
     * thread without touching live entities.
     */
    private Map<String, Map<String, String>> snapshotInventories(Iterable<FakePlayer> players) {
        Map<String, Map<String, String>> snap = new LinkedHashMap<>();
        for (FakePlayer fp : players) {
            Player bot = fp.getPlayer();
            if (bot == null || !bot.isValid()) continue;
            Map<String, String> slots = serializeInventory(bot.getInventory());
            if (!slots.isEmpty()) {
                snap.put(fp.getUuid().toString(), slots);
            }
        }
        return snap;
    }

    /** Writes a pre-built snapshot map to {@code bot-inventories.yml}. */
    private void writeInventorySnapshot(Map<String, Map<String, String>> snap) {
        YamlConfiguration yaml = new YamlConfiguration();
        for (Map.Entry<String, Map<String, String>> entry : snap.entrySet()) {
            String uuidKey = entry.getKey();
            for (Map.Entry<String, String> slot : entry.getValue().entrySet()) {
                yaml.set("inventories." + uuidKey + "." + slot.getKey(), slot.getValue());
            }
        }
        try {
            yaml.save(inventoryFile);
            Config.debug("Saved inventories for " + snap.size() + " bot(s).");
        } catch (IOException e) {
            FppLogger.error("Failed to save bot inventories: " + e.getMessage());
        }
    }

    /**
     * Serialises a {@link PlayerInventory} to a map of {@code slot → base64}.
     * Slots: 0-35 main/hotbar, 36 boots, 37 leggings, 38 chestplate, 39 helmet, 40 offhand.
     * Only non-null / non-AIR items are included.
     */
    private static Map<String, String> serializeInventory(PlayerInventory inv) {
        Map<String, String> slots = new LinkedHashMap<>();
        // Main inventory + hotbar (slots 0-35)
        ItemStack[] contents = inv.getContents();
        for (int i = 0; i < contents.length; i++) {
            if (contents[i] != null && contents[i].getType() != Material.AIR) {
                try {
                    slots.put(String.valueOf(i),
                            Base64.getEncoder().encodeToString(contents[i].serializeAsBytes()));
                } catch (Exception ignored) {}
            }
        }
        // Armour (36-39): getArmorContents() = [boots, leggings, chestplate, helmet]
        ItemStack[] armour = inv.getArmorContents();
        for (int i = 0; i < armour.length; i++) {
            if (armour[i] != null && armour[i].getType() != Material.AIR) {
                try {
                    slots.put(String.valueOf(36 + i),
                            Base64.getEncoder().encodeToString(armour[i].serializeAsBytes()));
                } catch (Exception ignored) {}
            }
        }
        // Offhand (slot 40)
        ItemStack offhand = inv.getItemInOffHand();
        if (offhand != null && offhand.getType() != Material.AIR) {
            try {
                slots.put("40", Base64.getEncoder().encodeToString(offhand.serializeAsBytes()));
            } catch (Exception ignored) {}
        }
        return slots;
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
            section.put("bot-type",        fp.getBotType().name());
            section.put("chat-enabled",    fp.isChatEnabled());
            if (fp.getChatTier() != null) {
                section.put("chat-tier", fp.getChatTier());
            }
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

        // Load saved inventories once so each bot can pick up its items after spawning.
        loadInventoryFile();

        // ── Primary: restore from database fpp_active_bots ───────────────────
        me.bill.fakePlayerPlugin.database.DatabaseManager db =
                plugin.getDatabaseManager();
        if (db != null) {
            // ----------------------------------------------------------------
            // DESIGN RULE — Bots are per-server only.
            // The database may be shared across multiple servers (NETWORK mode),
            // but Minecraft entities (NMS ServerPlayers, PlayerProfiles)
            // are strictly local to the server that originally spawned them.
            // We therefore ALWAYS restore only rows whose server_id matches
            // Config.serverId(), regardless of the database mode.
            // Never attempt to spawn another server's bots on this instance.
            // ----------------------------------------------------------------
            List<me.bill.fakePlayerPlugin.database.DatabaseManager.ActiveBotRow> rows =
                    db.getActiveBotsForThisServer();
            if (!rows.isEmpty()) {
                FppLogger.info("Restoring " + rows.size() + " bot(s) from database (server='"
                        + me.bill.fakePlayerPlugin.config.Config.serverId() + "')...");
                // Clear only this server's rows immediately — bots are now being restored.
                // Other servers' rows in a shared DB are left untouched.
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
                            null,  // No LP group from DB - LP handles natively now
                            // Infer PVP type from name prefix (covers admin PVP bots)
                            row.botName().startsWith("pvp_") ? BotType.PVP : BotType.AFK,
                            true, null  // chat-enabled defaults; DB doesn't persist these
                    ));
                    } catch (Exception e) {
                        FppLogger.warn("Skipping malformed DB active-bot row: " + e.getMessage());
                    }
                }
                if (!saved.isEmpty()) {
                    Bukkit.getScheduler().runTaskLater(plugin,
                            () -> restoreChain(manager, saved, 0), 40L);
                } else {
                    // All DB rows were malformed — nothing to restore.
                    manager.setRestorationInProgress(false);
                }
                return;
            }
        }

        // ── Fallback: restore from YAML file (no DB or empty active_bots) ────
        if (!dataFile.exists()) {
            manager.setRestorationInProgress(false);
            return;
        }

        YamlConfiguration yaml = YamlConfiguration.loadConfiguration(dataFile);
        List<?> raw = yaml.getList("bots");
        if (raw == null || raw.isEmpty()) {
            deleteFile(dataFile);
            manager.setRestorationInProgress(false);
            return;
        }

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
                Object btRaw         = map.get("bot-type");
                BotType botType      = btRaw instanceof String bts
                        ? BotType.parse(bts) : BotType.AFK;
                Object ceRaw         = map.get("chat-enabled");
                boolean chatEnabled  = !(ceRaw instanceof Boolean b) || b;
                Object ctRaw         = map.get("chat-tier");
                String chatTier      = ctRaw instanceof String s2 ? s2 : null;
                if (name == null || worldName == null) continue;
                saved.add(new SavedBot(name, uuid, displayName, spawnedBy, spawnedByUuid,
                        worldName, x, y, z, yaw, pitch, null, botType, chatEnabled, chatTier));
            } catch (Exception e) {
                FppLogger.warn("Skipping malformed bot entry in " + FILE_NAME + ": " + e.getMessage());
            }
        }
        deleteFile(dataFile);
        if (saved.isEmpty()) {
            manager.setRestorationInProgress(false);
            return;
        }

        FppLogger.info("Restoring " + saved.size() + " bot(s) from YAML fallback...");
        Bukkit.getScheduler().runTaskLater(plugin,
                () -> restoreChain(manager, saved, 0), 40L);
    }

    /** Spawns one saved bot and schedules the next with a random join-delay. */
    private void restoreChain(FakePlayerManager manager, List<SavedBot> saved, int index) {
        if (index >= saved.size()) {
            // All bots have been queued — signal that restoration is complete.
            manager.setRestorationInProgress(false);
            loadedInventories = null; // free memory — no longer needed
            FppLogger.info("Bot restoration complete: " + saved.size() + " bot(s) restored.");
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

        // Spawn with restored UUID and display name
        manager.spawnRestored(sb.name, sb.uuid, sb.displayName, sb.spawnedBy, sb.spawnedByUuid, loc, sb.botType);

        // Restore per-bot chat state
        FakePlayer fp = manager.getByName(sb.name);
        if (fp != null) {
            fp.setChatEnabled(sb.chatEnabled);
            if (sb.chatTier != null) fp.setChatTier(sb.chatTier);
        }

        // Restore inventory — delayed 10 ticks to ensure the NMS body is fully spawned
        // (LP async pre-assignment + body spawn both complete well within this window).
        if (loadedInventories != null) {
            Map<String, String> invSlots = loadedInventories.get(sb.uuid.toString());
            if (invSlots != null && !invSlots.isEmpty()) {
                final UUID restoredUuid = sb.uuid;
                final String restoredName = sb.name;
                Bukkit.getScheduler().runTaskLater(plugin, () -> {
                    FakePlayer restored = manager.getByUuid(restoredUuid);
                    if (restored == null) return;
                    Player bot = restored.getPlayer();
                    if (bot == null || !bot.isValid()) return;
                    applyInventory(bot.getInventory(), invSlots);
                    Config.debug("Restored inventory for bot '" + restoredName + "'.");
                }, 10L);
            }
        }

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

    // ── Inventory file I/O ───────────────────────────────────────────────────

    /**
     * Loads {@code bot-inventories.yml} into {@link #loadedInventories}.
     * Called once at the start of a restore cycle. Silently does nothing if
     * the file does not exist (first run or persistence disabled).
     */
    private void loadInventoryFile() {
        loadedInventories = new HashMap<>();
        if (!inventoryFile.exists()) return;
        YamlConfiguration yaml = YamlConfiguration.loadConfiguration(inventoryFile);
        ConfigurationSection invSection = yaml.getConfigurationSection("inventories");
        if (invSection == null) return;
        for (String uuidKey : invSection.getKeys(false)) {
            ConfigurationSection botSection = invSection.getConfigurationSection(uuidKey);
            if (botSection == null) continue;
            Map<String, String> slots = new LinkedHashMap<>();
            for (String slot : botSection.getKeys(false)) {
                String val = botSection.getString(slot);
                if (val != null && !val.isEmpty()) slots.put(slot, val);
            }
            if (!slots.isEmpty()) loadedInventories.put(uuidKey, slots);
        }
        Config.debug("Loaded inventories for " + loadedInventories.size() + " bot(s) from " + INV_FILE_NAME + ".");
    }

    /**
     * Applies a slot-map of {@code slot → base64} to the given inventory.
     * Slot numbering: 0-35 main/hotbar, 36 boots, 37 leggings, 38 chestplate,
     * 39 helmet, 40 offhand.  Errors on individual items are logged and skipped.
     */
    private static void applyInventory(PlayerInventory inv, Map<String, String> slots) {
        for (Map.Entry<String, String> entry : slots.entrySet()) {
            try {
                int slot = Integer.parseInt(entry.getKey());
                ItemStack item = ItemStack.deserializeBytes(
                        Base64.getDecoder().decode(entry.getValue()));
                if      (slot <= 35) inv.setItem(slot, item);
                else if (slot == 36) inv.setBoots(item);
                else if (slot == 37) inv.setLeggings(item);
                else if (slot == 38) inv.setChestplate(item);
                else if (slot == 39) inv.setHelmet(item);
                else if (slot == 40) inv.setItemInOffHand(item);
            } catch (Exception e) {
                FppLogger.warn("Failed to restore item in slot " + entry.getKey()
                        + ": " + e.getMessage());
            }
        }
    }

    // ── Purge + Restore ───────────────────────────────────────────────────────

    /**
     * Entry point called from {@code onEnable}.
     * <ol>
     *   <li>Sweeps every loaded world for NMS ServerPlayer entities tagged with
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
     * Removes every NMS ServerPlayer in all loaded worlds that carries the FPP PDC tag
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
                // Matches NMS ServerPlayer bodies
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
            String displayName,  // Saved display name for restoration
            String spawnedBy, UUID spawnedByUuid,
            String worldName,
            double x, double y, double z,
            float yaw, float pitch,
            String luckpermsGroup,  // Kept for compatibility, ignored in new system
            BotType botType,
            boolean chatEnabled,
            String chatTier         // nullable
    ) {}
}

