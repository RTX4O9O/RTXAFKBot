package me.bill.fakePlayerPlugin.fakeplayer;

import com.destroystokyo.paper.profile.PlayerProfile;
import me.bill.fakePlayerPlugin.FakePlayerPlugin;
import me.bill.fakePlayerPlugin.config.Config;
import me.bill.fakePlayerPlugin.database.BotRecord;
import me.bill.fakePlayerPlugin.database.DatabaseManager;
import net.luckperms.api.LuckPerms;
import net.luckperms.api.LuckPermsProvider;
import net.luckperms.api.model.group.Group;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.NamespacedKey;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Player;
import org.bukkit.plugin.Plugin;

import java.time.Instant;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ThreadLocalRandom;
import java.util.stream.Collectors;

public class FakePlayerManager {

    /** PDC key used to tag the physics-body zombie. */
    public static NamespacedKey FAKE_PLAYER_KEY;

    private final FakePlayerPlugin plugin;
    private final Map<UUID, FakePlayer> activePlayers = new ConcurrentHashMap<>();
    /** Secondary index: Bukkit entity id → FakePlayer for O(1) getByEntity(). */
    private final Map<Integer, FakePlayer> entityIdIndex = new ConcurrentHashMap<>();
    private final Set<String> usedNames = new HashSet<>();
    private ChunkLoader     chunkLoader;
    private DatabaseManager db;
    private BotPersistence  persistence;
    private BotSwapAI       swapAI;

    public void setChunkLoader(ChunkLoader cl) { this.chunkLoader = cl; }
    public void setDatabaseManager(DatabaseManager db) { this.db = db; }
    public void setBotPersistence(BotPersistence p) { this.persistence = p; }
    public void setSwapAI(BotSwapAI s) { this.swapAI = s; }


    public FakePlayerManager(FakePlayerPlugin plugin) {
        this.plugin = plugin;
        FAKE_PLAYER_KEY = new NamespacedKey(plugin, "fake_player_name");

        // Every 30 s, flush each bot's current position to the DB
        Bukkit.getScheduler().runTaskTimer(plugin, () -> {
            if (activePlayers.isEmpty()) return;
            for (FakePlayer fp : activePlayers.values()) {
                org.bukkit.entity.Entity body = fp.getPhysicsEntity();
                if (body == null || !body.isValid()) continue;
                org.bukkit.Location loc = body.getLocation();
                if (loc.getWorld() == null) continue;
                String world = loc.getWorld().getName();
                fp.setSpawnLocation(loc.clone());
                if (db != null) {
                    db.updateLastLocation(fp.getUuid(), world,
                            loc.getX(), loc.getY(), loc.getZ(),
                            loc.getYaw(), loc.getPitch());
                }
            }
            if (db != null) db.flushPendingLocations();
        }, 600L, 600L); // every 30 s

        // Every 40 ticks (2 s) re-send display names for all bots to all players.
        // Uses the lighter UPDATE_DISPLAY_NAME-only packet to override TAB plugin resets.
        Bukkit.getScheduler().runTaskTimer(plugin, () -> {
            if (activePlayers.isEmpty()) return;
            List<Player> online = new ArrayList<>(Bukkit.getOnlinePlayers());
            if (online.isEmpty()) return;
            for (FakePlayer fp : activePlayers.values()) {
                for (Player p : online) {
                    PacketHelper.sendTabListDisplayNameUpdate(p, fp);
                }
            }
        }, 40L, 40L);
    }

    // ── Spawn ────────────────────────────────────────────────────────────────

    /**
     * Spawns {@code count} fake players at the given location.
     * <p>
     * Names are pre-generated and reserved immediately, then bots appear one
     * by one with a random join delay. Skin is resolved automatically by the
     * client via the Mannequin's {@code setProfile(name)} — no HTTP calls.
     *
     * @param spawner the player who issued the command (may be null for console)
     * @return number of bots queued (-1 if at limit)
     */
    public int spawn(Location location, int count, Player spawner) {
        return spawn(location, count, spawner, null, false);
    }

    /**
     * Spawns fake players with an optional custom name.
     * When {@code customName} is non-null, count is forced to 1 and that
     * name is used instead of one drawn from the name pool.
     *
     * @param customName bot name to use, or {@code null} to use the name pool
     * @return number of bots queued, 0 if name taken, -1 if at limit, -2 if name invalid
     */
    public int spawn(Location location, int count, Player spawner, String customName) {
        return spawn(location, count, spawner, customName, false);
    }

    /**
     * User-tier spawn — bot names are always forced to the
     * {@code "[bot] PlayerName"} / {@code "[bot] PlayerName #N"} format.
     * Internal Minecraft names are generated as valid identifiers.
     *
     * @return number of bots queued, or {@code -1} if the global cap was hit
     */
    public int spawnUserBot(Location location, int count, Player spawner, boolean bypassMax) {
        int maxBots = Config.maxBots();
        if (!bypassMax && maxBots > 0) {
            int available = maxBots - activePlayers.size();
            if (available <= 0) return -1;
            count = Math.min(count, available);
        }

        String spawnerName = spawner.getName();
        UUID   spawnerUuid = spawner.getUniqueId();

        // Count how many bots this player already owns before this batch
        int alreadyOwned = getBotsOwnedBy(spawnerUuid).size();

        List<FakePlayer> batch = new ArrayList<>();
        for (int i = 0; i < count; i++) {
            UserBotName ubn = generateUserBotName(spawnerName, alreadyOwned + i);
            UUID uuid = UUID.randomUUID();
            PlayerProfile profile = Bukkit.createProfile(uuid, ubn.internalName());
            FakePlayer fp = new FakePlayer(uuid, ubn.internalName(), profile);
            // Store display name as MiniMessage string — NEVER convert to legacy
            fp.setDisplayName(ubn.displayName());
            fp.setSpawnLocation(location);
            fp.setSpawnedBy(spawnerName, spawnerUuid);
            activePlayers.put(uuid, fp);
            batch.add(fp);

            if (db != null) {
                BotRecord record = new BotRecord(
                        0, ubn.internalName(), uuid,
                        spawnerName, spawnerUuid,
                        location.getWorld() != null ? location.getWorld().getName() : "unknown",
                        location.getX(), location.getY(), location.getZ(),
                        location.getYaw(), location.getPitch(),
                        Instant.now(), null, null
                );
                fp.setDbRecord(record);
                db.recordSpawn(record);
            }
        }
        if (batch.isEmpty()) return 0;

        int total = batch.size();
        Bukkit.getScheduler().runTask(plugin, () -> visualChain(batch, 0, location));
        return total;
    }

    /**
     * Full spawn overload with max-bots bypass support.
     *
     * @param bypassMax when {@code true} the configured {@code max-bots} cap is ignored
     * @return number of bots queued, 0 if name taken, -1 if at limit, -2 if name invalid
     */
    public int spawn(Location location, int count, Player spawner, String customName, boolean bypassMax) {
        int maxBots = Config.maxBots();
        if (!bypassMax && maxBots > 0) {
            int available = maxBots - activePlayers.size();
            if (available <= 0) return -1;
            count = Math.min(count, available);
        }

        String spawnerName = spawner != null ? spawner.getName() : "CONSOLE";
        UUID   spawnerUuid = spawner != null ? spawner.getUniqueId() : new UUID(0, 0);

        // ── Custom name validation ────────────────────────────────────────────
        if (customName != null) {
            // Minecraft player name: 1-16 chars, letters/digits/underscore only
            if (customName.isEmpty() || customName.length() > 16
                    || !customName.matches("[a-zA-Z0-9_]+")) return -2;
            if (usedNames.contains(customName)) return 0; // already active
            count = 1; // custom name always spawns exactly one bot
        }

        // ── Step 1: pre-generate names & FakePlayer objects ──────────────────
        List<FakePlayer> batch = new ArrayList<>();
        for (int i = 0; i < count; i++) {
            String name = (customName != null) ? customName : generateName();
            if (name == null) break;
            UUID uuid = UUID.randomUUID();
            PlayerProfile profile = Bukkit.createProfile(uuid, name);
            FakePlayer fp = new FakePlayer(uuid, name, profile);
            // Set display name using admin bot name format + LuckPerms prefix
            String lpPrefix = getLuckPermsPrefix();
            String displayName = lpPrefix + Config.adminBotNameFormat().replace("{bot_name}", name);
            fp.setDisplayName(displayName);
            fp.setSpawnLocation(location);
            fp.setSpawnedBy(spawnerName, spawnerUuid);
            activePlayers.put(uuid, fp);
            batch.add(fp);

            // Record to database immediately on spawn
            if (db != null) {
                BotRecord record = new BotRecord(
                        0, name, uuid,
                        spawnerName, spawnerUuid,
                        location.getWorld() != null ? location.getWorld().getName() : "unknown",
                        location.getX(), location.getY(), location.getZ(),
                        location.getYaw(), location.getPitch(),
                        Instant.now(), null, null
                );
                fp.setDbRecord(record);
                db.recordSpawn(record);
            }
        }
        if (batch.isEmpty()) return 0;

        int total = batch.size();
        // Mannequin.setProfile(name) makes the client resolve the correct skin
        // automatically — no HTTP skin fetch needed. Start visual chain immediately.
        Bukkit.getScheduler().runTask(plugin, () -> visualChain(batch, 0, location));
        return total;
    }

    /**
     * Visually spawns the bot at {@code index} in {@code batch}, then
     * schedules the next one after a random join delay.
     */
    private void visualChain(List<FakePlayer> batch, int index, Location location) {
        if (index >= batch.size()) return;

        FakePlayer fp = batch.get(index);
        // Guard: bot may have been deleted while skins were loading
        if (!activePlayers.containsKey(fp.getUuid())) {
            visualChain(batch, index + 1, location);
            return;
        }

        finishSpawn(fp, location);

        int delayMin = Config.joinDelayMin();
        int delayMax = Math.max(delayMin, Config.joinDelayMax());
        long delay;
        if (delayMax <= 0) {
            delay = 0;
        } else {
            int spread = delayMax - delayMin;
            delay = Math.max(1, delayMin + (spread > 0
                    ? ThreadLocalRandom.current().nextInt(spread + 1)
                    : 0));
        }

        Bukkit.getScheduler().runTaskLater(plugin,
                () -> visualChain(batch, index + 1, location), delay);
    }

    private void finishSpawn(FakePlayer fp, Location spawnLoc) {
        fp.setSpawnTime(java.time.Instant.now());
        if (Config.spawnBody()) {
            Entity body = FakePlayerBody.spawn(fp, spawnLoc);
            if (body != null) {
                fp.setPhysicsEntity(body);
                entityIdIndex.put(body.getEntityId(), fp);
                Bukkit.getScheduler().runTaskLater(plugin, () -> {
                    if (!body.isValid()) return;
                    FakePlayerBody.applySkin(plugin, body, fp.getName());
                    fp.setNametagEntity(FakePlayerBody.spawnNametag(fp, body));
                    // Re-send tab after skin loads (~1 s later)
                    Bukkit.getScheduler().runTaskLater(plugin, () -> {
                        List<Player> online = new ArrayList<>(Bukkit.getOnlinePlayers());
                        for (Player p : online) PacketHelper.sendTabListAdd(p, fp);
                    }, 20L);
                }, 1L);
            }
        }

        // Send tab list immediately
        List<Player> online = new ArrayList<>(Bukkit.getOnlinePlayers());
        for (Player p : online) PacketHelper.sendTabListAdd(p, fp);

        // Re-send after 5 ticks to override TAB plugin or other plugins that may reset it
        Bukkit.getScheduler().runTaskLater(plugin, () -> {
            List<Player> snap = new ArrayList<>(Bukkit.getOnlinePlayers());
            for (Player p : snap) PacketHelper.sendTabListAdd(p, fp);
        }, 5L);

        if (Config.joinMessage()) {
            Bukkit.getScheduler().runTaskLater(plugin, () ->
                BotBroadcast.broadcastJoin(fp), 2L);
        }

        if (persistence != null && Config.persistOnRestart()) {
            persistence.saveAsync(activePlayers.values());
        }

        if (swapAI != null) swapAI.schedule(fp);
        fireVanillaJoin(fp);
    }

    // ── Remove all ───────────────────────────────────────────────────────────

    /**
     * Restores a single fake player from persisted data (called on startup).
     * The original UUID is reused so database records stay linked.
     */
    public void spawnRestored(String name, UUID uuid, String spawnedBy, UUID spawnedByUuid, Location location) {
        // Skip if name already active (e.g. duplicate in save file)
        if (usedNames.contains(name)) return;

        PlayerProfile profile = Bukkit.createProfile(uuid, name);
        FakePlayer fp = new FakePlayer(uuid, name, profile);
        // Set display name using admin bot name format + LuckPerms prefix
        String lpPrefix = getLuckPermsPrefix();
        String displayName = lpPrefix + Config.adminBotNameFormat().replace("{bot_name}", name);
        fp.setDisplayName(displayName);
        fp.setSpawnLocation(location);
        fp.setSpawnedBy(spawnedBy, spawnedByUuid);
        usedNames.add(name);
        activePlayers.put(uuid, fp);

        // Record to database as a fresh spawn
        if (db != null) {
            BotRecord record = new BotRecord(
                    0, name, uuid,
                    spawnedBy, spawnedByUuid,
                    location.getWorld() != null ? location.getWorld().getName() : "unknown",
                    location.getX(), location.getY(), location.getZ(),
                    location.getYaw(), location.getPitch(),
                    Instant.now(), null, null
            );
            fp.setDbRecord(record);
            db.recordSpawn(record);
        }

        // Visual spawn (no skin fetch on restore — keeps startup fast)
        finishSpawn(fp, location);
        Config.debug("Restored bot: " + name + " at " + location);
    }

    public void removeAll() {
        if (activePlayers.isEmpty()) return;

        int delayMin = Config.leaveDelayMin();
        int delayMax = Math.max(delayMin, Config.leaveDelayMax());
        boolean stagger = delayMax > 0;

        // Snapshot and clear registry immediately — prevents double-removal
        List<FakePlayer> toRemove = new ArrayList<>(activePlayers.values());
        activePlayers.clear();
        usedNames.clear();
        entityIdIndex.clear();

        // Cancel all swap timers
        if (swapAI != null) swapAI.cancelAll();

        for (int i = 0; i < toRemove.size(); i++) {
            FakePlayer fp = toRemove.get(i);

            // Each bot gets a random delay; index offset guarantees stagger
            long leaveDelay;
            if (!stagger) {
                leaveDelay = 0;
            } else {
                int spread = delayMax - delayMin;
                leaveDelay = i + delayMin + (spread > 0
                        ? ThreadLocalRandom.current().nextInt(spread + 1)
                        : 0);
            }

            final FakePlayer target = fp;
            Runnable doVisualRemove = () -> {
                FakePlayerBody.removeAll(target);
                if (chunkLoader != null) chunkLoader.releaseForBot(target);
                // Clear entity-id index entry
                if (target.getPhysicsEntity() != null)
                    entityIdIndex.remove(target.getPhysicsEntity().getEntityId());

                List<Player> snapshot = new ArrayList<>(Bukkit.getOnlinePlayers());
                for (Player online : snapshot) PacketHelper.sendTabListRemove(online, target);

                if (Config.leaveMessage()) {
                    BotBroadcast.broadcastLeave(target);
                }
                if (db != null) db.recordRemoval(target.getUuid(), "DELETED");
                Config.debug("Removed bot: " + target.getName());
                if (persistence != null && Config.persistOnRestart()) {
                    persistence.saveAsync(activePlayers.values());
                }
            };

            if (leaveDelay <= 0) {
                Bukkit.getScheduler().runTask(plugin, doVisualRemove);
            } else {
                Bukkit.getScheduler().runTaskLater(plugin, doVisualRemove, leaveDelay);
            }
        }

        Config.debug("Staggered visual removal of " + toRemove.size() + " fake player(s).");
    }

    // ── Delete one ───────────────────────────────────────────────────────────

    /**
     * Deletes a single fake player by name — kills the physics body, removes
     * from tab list, despawns the visual, broadcasts leave message.
     *
     * @return true if a bot with that name was found and removed, false otherwise
     */
    public boolean delete(String name) {
        FakePlayer fp = null;
        for (FakePlayer candidate : activePlayers.values()) {
            if (candidate.getName().equalsIgnoreCase(name)) { fp = candidate; break; }
        }
        if (fp == null) return false;

        final FakePlayer target = fp;

        // Remove from registry immediately — prevents double-delete and clears tab-complete
        activePlayers.remove(target.getUuid());
        usedNames.remove(target.getName());

        // Cancel any pending swap timer so it doesn't rejoin after deletion
        if (swapAI != null) swapAI.cancel(target.getUuid());

        // Defer body removal, tab-list, despawn and leave message together
        int delayMin = Config.leaveDelayMin();
        int delayMax = Math.max(delayMin, Config.leaveDelayMax());
        long leaveDelay;
        if (delayMax <= 0) {
            leaveDelay = 0;
        } else {
            int spread = delayMax - delayMin;
            leaveDelay = Math.max(1, delayMin + (spread > 0
                    ? ThreadLocalRandom.current().nextInt(spread + 1)
                    : 0));
        }

        Runnable doVisualRemove = () -> {
            FakePlayerBody.removeAll(target);
            if (chunkLoader != null) chunkLoader.releaseForBot(target);
            if (target.getPhysicsEntity() != null)
                entityIdIndex.remove(target.getPhysicsEntity().getEntityId());

            List<Player> snapshot = new ArrayList<>(Bukkit.getOnlinePlayers());
            for (Player online : snapshot) PacketHelper.sendTabListRemove(online, target);

            if (Config.leaveMessage()) {
                BotBroadcast.broadcastLeave(target);
            }
            if (db != null) db.recordRemoval(target.getUuid(), "DELETED");
            Config.debug("Deleted fake player: " + name);
            if (persistence != null && Config.persistOnRestart()) {
                persistence.saveAsync(activePlayers.values());
            }
        };

        if (leaveDelay <= 0) {
            Bukkit.getScheduler().runTask(plugin, doVisualRemove);
        } else {
            Bukkit.getScheduler().runTaskLater(plugin, doVisualRemove, leaveDelay);
        }

        return true;
    }

    /**
     * Synchronous staggered removal — called from {@code onDisable}.
     * The Bukkit scheduler is already shut down at this point so we block
     * the main thread between bots using {@link Thread#sleep}, honouring the
     * same {@code leave-delay} config values that the normal scheduler-based
     * removal uses. 1 tick = 50 ms. If both min and max are 0, all bots leave
     * instantly with no sleep.
     */
    public void removeAllSync() {
        if (activePlayers.isEmpty()) return;

        List<FakePlayer> toRemove = new ArrayList<>(activePlayers.values());
        activePlayers.clear();
        usedNames.clear();
        entityIdIndex.clear();

        List<Player> snapshot = new ArrayList<>(Bukkit.getOnlinePlayers());

        int delayMin = Config.leaveDelayMin();
        int delayMax = Math.max(delayMin, Config.leaveDelayMax());
        int spread   = delayMax - delayMin;

        for (int i = 0; i < toRemove.size(); i++) {
            FakePlayer fp = toRemove.get(i);

            // 1. Kill nametag, visual, and physics body
            FakePlayerBody.removeAll(fp);
            if (chunkLoader != null) chunkLoader.releaseForBot(fp);

            // 2. Tab list remove
            for (Player online : snapshot) PacketHelper.sendTabListRemove(online, fp);

            // 3. Leave message
            if (Config.leaveMessage()) {
                BotBroadcast.broadcastLeave(fp);
            }

            // 4. DB record
            if (db != null) db.recordRemoval(fp.getUuid(), "SHUTDOWN");

            Config.debug("Shutdown removed bot: " + fp.getName());

            // 5. Sleep between bots — random delay in the configured range,
            //    converted from ticks to ms (1 tick = 50 ms).
            //    Skip sleep after the last bot.
            if (delayMax > 0 && i < toRemove.size() - 1) {
                long ticks = delayMin + (spread > 0
                        ? ThreadLocalRandom.current().nextInt(spread + 1)
                        : 0);
                if (ticks > 0) {
                    try {
                        Thread.sleep(ticks * 50L);
                    } catch (InterruptedException e) {
                        Thread.currentThread().interrupt();
                        break;
                    }
                }
            }
        }
    }

    // ── Swap support ─────────────────────────────────────────────────────────

    /**
     * Called by {@link BotSwapAI} when a bot's session expires.
     * Removes the bot from the active registry and frees its name so a new
     * bot can claim it — but does NOT do any visual removal (BotSwapAI handles that).
     */
    public void swapRemove(FakePlayer fp) {
        activePlayers.remove(fp.getUuid());
        usedNames.remove(fp.getName());
        if (chunkLoader != null) chunkLoader.releaseForBot(fp);
        if (db != null) db.recordRemoval(fp.getUuid(), "SWAP");
        Config.debug("Swap: removed " + fp.getName() + " from registry.");
    }

    /**
     * Called by {@link BotSwapAI} to spawn a replacement bot at the given location.
     * Pass {@code forcedName} to reconnect with the same name, or {@code null}
     * for a fresh name from the pool.
     *
     * @return number of bots spawned (0 = name taken/pool exhausted, -1 = at limit)
     */
    public int spawnSwap(Location location, String forcedName) {
        return spawn(location, 1, null, forcedName);
    }

    // ── Remove by name (post-death cleanup) ──────────────────────────────────

    /** Removes a fake player by name (called after permanent death). */
    public void removeByName(String name) {
        activePlayers.values().removeIf(fp -> {
            if (!fp.getName().equals(name)) return false;
            usedNames.remove(fp.getName());
            if (swapAI != null) swapAI.cancel(fp.getUuid());
            if (db != null) db.recordRemoval(fp.getUuid(), "DIED");
            Config.debug("Removed from registry: " + name);
            return true;
        });
        if (persistence != null && Config.persistOnRestart()) {
            persistence.saveAsync(activePlayers.values());
        }
    }

    // ── Sync to joining player ────────────────────────────────────────────────

    /**
     * Syncs all existing fake players' tab list entries to a newly joined real player.
     * The Mannequin body is already visible via normal entity tracking.
     */
    public void syncToPlayer(Player player) {
        for (FakePlayer fp : activePlayers.values()) {
            PacketHelper.sendTabListAdd(player, fp);
            // ArmorStand nametag is a real entity — syncs to new players automatically
            // via normal entity tracking. No extra packets needed.
        }
    }

    // ── Queries ──────────────────────────────────────────────────────────────

    /** Returns the FakePlayer whose physics body has the given entity id, or null. O(1). */
    public FakePlayer getByEntity(Entity entity) {
        return entityIdIndex.get(entity.getEntityId());
    }

    /** Returns the FakePlayer with the given UUID, or {@code null} if not found. */
    public FakePlayer getByUuid(UUID uuid) {
        return activePlayers.get(uuid);
    }

    /** Returns a list of all active bot names (for tab-completion). */
    public List<String> getActiveNames() {
        return activePlayers.values().stream().map(FakePlayer::getName).collect(Collectors.toList());
    }

    public Collection<FakePlayer> getActivePlayers() {
        return Collections.unmodifiableCollection(activePlayers.values());
    }

    public int getCount() { return activePlayers.size(); }

    /**
     * Returns all bots spawned by the given player UUID.
     * Used for user-tier commands (tph, info-self, user-spawn limit).
     */
    public List<FakePlayer> getBotsOwnedBy(java.util.UUID ownerUuid) {
        return activePlayers.values().stream()
                .filter(fp -> ownerUuid.equals(fp.getSpawnedByUuid()))
                .collect(Collectors.toList());
    }

    /**
     * Moves the physics body of {@code fp} to {@code destination} immediately.
     * Used by the tph / tp commands.
     */
    public boolean teleportBot(FakePlayer fp, org.bukkit.Location destination) {
        org.bukkit.entity.Entity body = fp.getPhysicsEntity();
        if (body == null || !body.isValid()) return false;

        // Passengers are ejected when an entity is teleported in Bukkit.
        // Eject the nametag ArmorStand first, teleport the body, then re-mount it.
        org.bukkit.entity.ArmorStand nametag = fp.getNametagEntity();
        if (nametag != null && nametag.isValid()) {
            body.removePassenger(nametag);
        }

        body.teleport(destination);

        // Re-add nametag as passenger so it rides the Mannequin again
        if (nametag != null && nametag.isValid()) {
            nametag.teleport(destination);
            body.addPassenger(nametag);
        }

        fp.setSpawnLocation(destination.clone());
        return true;
    }

    // ── Name generation ──────────────────────────────────────────────────────

    private String generateName() {
        List<String> pool = Config.namePool();
        if (pool.isEmpty()) return fallbackName();

        String chosen  = null;
        int    count   = 0;
        for (String n : pool) {
            if (usedNames.contains(n) || Bukkit.getPlayerExact(n) != null) continue;
            count++;
            if (ThreadLocalRandom.current().nextInt(count) == 0) chosen = n;
        }
        if (chosen != null) {
            usedNames.add(chosen);
            // Only return the valid Minecraft identifier for GameProfile
            return chosen;
        }
        return fallbackName();
    }

    private String fallbackName() {
        String generated;
        int attempts = 0;
        do {
            generated = "Bot" + ThreadLocalRandom.current().nextInt(1000, 9999);
            if (++attempts > 200) return null; // safety cap
        } while (usedNames.contains(generated) || Bukkit.getPlayerExact(generated) != null);
        usedNames.add(generated);
        return generated;
    }

    // ── User-bot naming ───────────────────────────────────────────────────────

    /**
     * Result holder for a generated user-bot name pair.
     *
     * @param internalName valid Minecraft identifier used for the GameProfile / skin lookup
     * @param displayName  rich display text shown in nametag and tab list
     */
    public record UserBotName(String internalName, String displayName) {}

    /**
     * Converts a LuckPerms prefix (legacy & codes, §codes, or already MiniMessage) to MiniMessage.
     * Handles plain named colors (&7 → <gray>), hex (&x&0&0&7&9&F&F), and &#RRGGBB formats.
     */
    private String convertLuckPermsPrefixToMiniMessage(String prefix) {
        if (prefix == null || prefix.isEmpty()) return "";
        // Use TextUtil's round-trip conversion which handles all legacy formats correctly
        return me.bill.fakePlayerPlugin.util.TextUtil.legacyToMiniMessage(prefix);
    }

    /** Public accessor for startup diagnostic logging. Returns the LP prefix in MiniMessage, or empty string. */
    public String detectLuckPermsPrefix() {
        return getLuckPermsPrefix();
    }

    private String getLuckPermsPrefix() {
        // Respect the config toggle
        if (!Config.luckpermsUsePrefix()) return "";

        Plugin lpPlugin = Bukkit.getPluginManager().getPlugin("LuckPerms");
        if (lpPlugin == null || !lpPlugin.isEnabled()) return "";
        try {
            LuckPerms lp = LuckPermsProvider.get();
            net.luckperms.api.query.QueryOptions opts =
                    net.luckperms.api.query.QueryOptions.nonContextual();

            // ── Strategy 1: read prefix nodes directly from the default group ──
            Group defaultGroup = lp.getGroupManager().getGroup("default");
            if (defaultGroup != null) {
                // Scan the group's own nodes for PrefixNode — most reliable approach
                String directPrefix = defaultGroup.getNodes(net.luckperms.api.node.NodeType.PREFIX)
                        .stream()
                        .max(java.util.Comparator.comparingInt(
                                n -> n.getPriority()))   // highest priority wins
                        .map(n -> n.getMetaValue())
                        .orElse(null);
                if (directPrefix != null && !directPrefix.isEmpty()) {
                    Config.debug("[LP prefix] Direct node prefix from 'default': " + directPrefix);
                    return convertLuckPermsPrefixToMiniMessage(directPrefix);
                }

                // ── Strategy 2: cached metadata (works when LP has resolved the group) ──
                String cachedPrefix = defaultGroup.getCachedData().getMetaData(opts).getPrefix();
                if (cachedPrefix != null && !cachedPrefix.isEmpty()) {
                    Config.debug("[LP prefix] Cached metadata prefix from 'default': " + cachedPrefix);
                    return convertLuckPermsPrefixToMiniMessage(cachedPrefix);
                }
            }

            // ── Strategy 3: scan ALL groups by weight, pick highest-weight prefix ──
            String bestPrefix = null;
            int bestWeight = Integer.MIN_VALUE;
            for (Group g : lp.getGroupManager().getLoadedGroups()) {
                int weight = g.getWeight().orElse(0);
                if (weight <= bestWeight) continue;

                // Try direct nodes first
                String p = g.getNodes(net.luckperms.api.node.NodeType.PREFIX)
                        .stream()
                        .max(java.util.Comparator.comparingInt(
                                n -> n.getPriority()))
                        .map(n -> n.getMetaValue())
                        .orElse(null);
                if (p == null || p.isEmpty()) {
                    p = g.getCachedData().getMetaData(opts).getPrefix();
                }
                if (p != null && !p.isEmpty()) {
                    bestPrefix = p;
                    bestWeight = weight;
                }
            }
            if (bestPrefix != null) {
                Config.debug("[LP prefix] Best-weight group prefix: " + bestPrefix);
                return convertLuckPermsPrefixToMiniMessage(bestPrefix);
            }

        } catch (Exception e) {
            Config.debug("[LP prefix] Failed to get LuckPerms prefix: " + e.getMessage());
        }
        return "";
    }

    /**
     * Generates a valid internal Minecraft name and a display name for an admin bot.
     *
     * @param botName the bot's actual Minecraft name
     */
    public UserBotName generateAdminBotName(String botName) {
        // Internal name: "bot_<name>" truncated to 16 chars (valid MC identifier)
        String internal = "bot_" + botName;
        if (internal.length() > 16) internal = internal.substring(0, 16);
        usedNames.add(internal);
        String prefix  = getLuckPermsPrefix();
        String display = prefix + Config.adminBotNameFormat().replace("{bot_name}", botName);
        return new UserBotName(internal, display);
    }

    /**
     * Generates a valid internal Minecraft name and a display name for a user-tier bot.
     *
     * @param spawnerName   the spawning player's Minecraft name
     * @param existingCount bots this player already owns (used for the numeric suffix)
     */
    public UserBotName generateUserBotName(String spawnerName, int existingCount) {
        // Internal name: "ubot_<spawner>_<num>" truncated to 16 chars
        String suffix   = String.valueOf(existingCount + 1);
        String internal = "ubot_" + spawnerName + "_" + suffix;
        if (internal.length() > 16) internal = internal.substring(0, 16);
        usedNames.add(internal);
        String prefix  = getLuckPermsPrefix();
        String display = prefix + Config.userBotNameFormat()
                .replace("{spawner}", spawnerName)
                .replace("{num}",     suffix)
                .replace("{bot_name}", internal);
        return new UserBotName(internal, display);
    }

    /**
     * Fires a vanilla join event for a fake player, so it appears in the server player list and triggers vanilla join message.
     */
    private void fireVanillaJoin(FakePlayer fp) {
        for (Player online : Bukkit.getOnlinePlayers()) {
            PacketHelper.sendTabListAdd(online, fp);
        }
    }

    /**
     * Fires a vanilla leave event for a fake player, so it disappears from the server player list and triggers vanilla leave message.
     */
    private void fireVanillaLeave(FakePlayer fp) {
        for (Player online : Bukkit.getOnlinePlayers()) {
            PacketHelper.sendTabListRemove(online, fp);
        }
    }
}
