package me.bill.fakePlayerPlugin.fakeplayer;

import com.destroystokyo.paper.profile.PlayerProfile;
import me.bill.fakePlayerPlugin.FakePlayerPlugin;
import me.bill.fakePlayerPlugin.config.Config;
import me.bill.fakePlayerPlugin.database.BotRecord;
import me.bill.fakePlayerPlugin.database.DatabaseManager;
import me.bill.fakePlayerPlugin.util.FppLogger;
import me.bill.fakePlayerPlugin.util.LuckPermsHelper;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.NamespacedKey;
import org.bukkit.entity.ArmorStand;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Player;

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
    /** Per-player spawn cooldown: UUID → last spawn timestamp (ms). */
    private final Map<UUID, Long> spawnCooldowns = new ConcurrentHashMap<>();
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

        // Flush each bot's current position to the DB on the configured interval
        long flushTicks = Math.max(20L, Config.dbLocationFlushInterval() * 20L);
        Bukkit.getScheduler().runTaskTimer(plugin, () -> {
            if (activePlayers.isEmpty()) return;
            for (FakePlayer fp : activePlayers.values()) {
                // Use getLiveLocation() — prefers Mannequin body over stored spawn pos
                org.bukkit.Location loc = fp.getLiveLocation();
                if (loc == null || loc.getWorld() == null) continue;
                String world = loc.getWorld().getName();
                // Update FakePlayer's stored position so ChunkLoader also has fresh data
                fp.setSpawnLocation(loc.clone());
                if (db != null) {
                    db.updateLastLocation(fp.getUuid(), world,
                            loc.getX(), loc.getY(), loc.getZ(),
                            loc.getYaw(), loc.getPitch());
                }
            }
            if (db != null) db.flushPendingLocations();
        }, flushTicks, flushTicks);

        // Every 20 ticks (1 s) re-send display names for all bots to all players.
        // Uses the lighter UPDATE_DISPLAY_NAME-only packet to override TAB plugin resets.
        Bukkit.getScheduler().runTaskTimer(plugin, () -> {
            if (!Config.tabListEnabled()) return;
            if (activePlayers.isEmpty()) return;
            List<Player> online = new ArrayList<>(Bukkit.getOnlinePlayers());
            if (online.isEmpty()) return;
            for (FakePlayer fp : activePlayers.values()) {
                for (Player p : online) {
                    PacketHelper.sendTabListDisplayNameUpdate(p, fp);
                }
            }
        }, 20L, 20L);
    }

    /**
     * Returns true when the plugin is configured to spawn physical bodies
     * and the runtime compatibility check did not disable them.
     */
    public boolean physicalBodiesEnabled() {
        return Config.spawnBody() && !plugin.isCompatibilityRestricted();
    }

    /**
     * Returns a human-friendly location string for display in /fpp info
     * taking into account whether physical bodies are enabled.
     */
    public String formatLocationForDisplay(FakePlayer fp) {
        if (!physicalBodiesEnabled()) {
            return "No Body";
        }
        var body = fp.getPhysicsEntity();
        if (body != null && body.isValid()) {
            var l = body.getLocation();
            return (l.getWorld() != null ? l.getWorld().getName() : "?")
                    + " " + l.getBlockX() + "," + l.getBlockY() + "," + l.getBlockZ();
        }
        var sl = fp.getSpawnLocation();
        if (sl != null) return (sl.getWorld() != null ? sl.getWorld().getName() : "?")
                + " " + sl.getBlockX() + "," + sl.getBlockY() + "," + sl.getBlockZ();
        return "unknown";
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
            // For user bots the internal name (ubot_*) has no Mojang skin —
            // pick a random name from the pool to use for skin lookup instead.
            fp.setSkinName(pickRandomSkinName());
            // Resolve LP data using the spawner's UUID so user bots mirror their owner's rank.
            LuckPermsHelper.LpData lpData = LuckPermsHelper.getBotLpData(spawnerUuid);
            String lpPrefix = Config.luckpermsUsePrefix() ? lpData.prefix() : "";
            String userDisplay = lpPrefix + Config.userBotNameFormat()
                    .replace("{spawner}", spawnerName)
                    .replace("{num}", String.valueOf(alreadyOwned + i + 1))
                    .replace("{bot_name}", ubn.internalName());
            fp.setDisplayName(userDisplay);
            String pktName = LuckPermsHelper.buildPacketProfileName(lpData.weight(), ubn.internalName());
            fp.setPacketProfileName(pktName);
            Config.debug("[LP] user-bot '" + ubn.internalName() + "' owner=" + spawnerUuid
                    + " weight=" + lpData.weight() + " pkt='" + pktName + "'");
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
                db.recordSpawn(record, net.kyori.adventure.text.serializer.plain.PlainTextComponentSerializer.plainText()
                        .serialize(me.bill.fakePlayerPlugin.util.TextUtil.colorize(ubn.displayName())));
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
            // For admin bots the internal name IS the skin name (e.g. "Notch", "Dream")
            fp.setSkinName(name);
            // Resolve LP data (prefix + weight) for this bot.
            // Admin bots use the bot-group config or the 'default' group — no owner UUID.
            LuckPermsHelper.LpData lpData = LuckPermsHelper.getBotLpData(null);
            String lpPrefix = Config.luckpermsUsePrefix() ? lpData.prefix() : "";
            String displayName = lpPrefix + Config.adminBotNameFormat().replace("{bot_name}", name);
            fp.setDisplayName(displayName);
            String pktAdmin = LuckPermsHelper.buildPacketProfileName(lpData.weight(), name);
            fp.setPacketProfileName(pktAdmin);
            Config.debug("[LP] admin-bot '" + name + "' display='" + displayName
                    + "' pkt='" + pktAdmin + "' weight=" + lpData.weight());
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
                db.recordSpawn(record, net.kyori.adventure.text.serializer.plain.PlainTextComponentSerializer.plainText()
                        .serialize(me.bill.fakePlayerPlugin.util.TextUtil.colorize(displayName)));
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
     *
     * <p><b>Stack-safety:</b> deleted bots are skipped with a {@code while} loop
     * (not recursion) so a mass-delete cannot cause a {@link StackOverflowError}.
     * Continuation is always via {@link org.bukkit.scheduler.BukkitScheduler#runTask} or
     * {@link org.bukkit.scheduler.BukkitScheduler#runTaskLater} — never a direct call.
     */
    private void visualChain(List<FakePlayer> batch, int index, Location location) {
        if (batch == null) return;

        // Skip any bots that were deleted while skins were loading — use a loop,
        // not recursion, to avoid StackOverflowError on large batch deletions.
        while (index < batch.size() && !activePlayers.containsKey(batch.get(index).getUuid())) {
            index++;
        }
        if (index >= batch.size()) return;

        FakePlayer fp = batch.get(index);
        finishSpawn(fp, location);

        // join-delay values in config are in seconds — convert to ticks
        int delayMinSecs = Config.joinDelayMin();
        int delayMaxSecs = Math.max(delayMinSecs, Config.joinDelayMax());
        long delayTicks;
        if (delayMaxSecs <= 0) {
            delayTicks = 0L;
        } else {
            int spread = delayMaxSecs - delayMinSecs;
            int secs = delayMinSecs + (spread > 0
                    ? ThreadLocalRandom.current().nextInt(spread + 1)
                    : 0);
            delayTicks = Math.max(1L, (long) secs * 20L);
        }

        final int next = index + 1;
        if (delayTicks <= 0) {
            // Use runTask so continuation is always a fresh stack frame, never a direct call.
            Bukkit.getScheduler().runTask(plugin, () -> visualChain(batch, next, location));
        } else {
            Bukkit.getScheduler().runTaskLater(plugin, () -> visualChain(batch, next, location), delayTicks);
        }
    }

    private void finishSpawn(FakePlayer fp, Location spawnLoc) {
        fp.setSpawnTime(java.time.Instant.now());

        // ── Skin-first pipeline ───────────────────────────────────────────────
        // We resolve the skin BEFORE sending the tab-list packet so the client
        // never sees a "default Steve" flash.  Everything downstream fires from
        // the skin callback — guaranteed to run on the main thread.
        FakePlayerBody.resolveAndFinish(plugin, fp, spawnLoc, () -> {
            // At this point fp.getResolvedSkin() is populated (or null for off/auto).

            // 1. Spawn body (Mannequin) — profile already has texture data
            if (Config.spawnBody() && !plugin.isCompatibilityRestricted()) {
                Entity body = FakePlayerBody.spawn(fp, spawnLoc);
                if (body != null) {
                    fp.setPhysicsEntity(body);
                    entityIdIndex.put(body.getEntityId(), fp);
                    Bukkit.getScheduler().runTaskLater(plugin, () -> {
                        if (!body.isValid()) return;
                        FakePlayerBody.applyResolvedSkin(plugin, fp, body);
                        fp.setNametagEntity(FakePlayerBody.spawnNametag(fp, body));
                    }, 1L);
                }
            } else if (plugin.isCompatibilityRestricted()) {
                // Compatibility mode: don't spawn physical bodies, keep tab-list only
                Config.debug("Compatibility restricted: skipping physical body spawn for " + fp.getName());
            }

            // 2. Send tab list — now carries the correct skin texture
            List<Player> online = new ArrayList<>(Bukkit.getOnlinePlayers());
            if (Config.tabListEnabled()) {
                Config.debug("Sending tab-list add for '" + fp.getName() + "' display='" + fp.getDisplayName() + "' packet='" + fp.getPacketProfileName() + "'");
                for (Player p : online) PacketHelper.sendTabListAdd(p, fp);

                // Send immediate UPDATE_DISPLAY_NAME to try and override TAB plugins
                for (Player p : online) PacketHelper.sendTabListDisplayNameUpdate(p, fp);

                // 3. Re-send after 3 ticks and again after 20 ticks in case TAB plugin overwrites it
                Bukkit.getScheduler().runTaskLater(plugin, () -> {
                    Config.debug("Re-sending tab-list add (3t) for '" + fp.getName() + "' display='" + fp.getDisplayName() + "' packet='" + fp.getPacketProfileName() + "'");
                    for (Player p : Bukkit.getOnlinePlayers()) PacketHelper.sendTabListAdd(p, fp);
                    for (Player p : Bukkit.getOnlinePlayers()) PacketHelper.sendTabListDisplayNameUpdate(p, fp);
                }, 3L);

                Bukkit.getScheduler().runTaskLater(plugin, () -> {
                    Config.debug("Re-sending tab-list add (20t) for '" + fp.getName() + "' display='" + fp.getDisplayName() + "' packet='" + fp.getPacketProfileName() + "'");
                    for (Player p : Bukkit.getOnlinePlayers()) PacketHelper.sendTabListAdd(p, fp);
                    for (Player p : Bukkit.getOnlinePlayers()) PacketHelper.sendTabListDisplayNameUpdate(p, fp);
                }, 20L);
            } else {
                Config.debug("Tab-list disabled — skipping tab add for '" + fp.getName() + "'");
            }

            // 4. Broadcast join message
            if (Config.joinMessage()) {
                Bukkit.getScheduler().runTaskLater(plugin, () -> BotBroadcast.broadcastJoin(fp), 2L);
            }

            if (persistence != null && Config.persistOnRestart()) {
                persistence.saveAsync(activePlayers.values());
            }

            if (swapAI != null) swapAI.schedule(fp);
        });
    }

    // ── Remove all ───────────────────────────────────────────────────────────

    /**
     * Restores a single fake player from persisted data (called on startup).
     * The original UUID and display name are reused so database records stay linked
     * and bots maintain their original appearance (prefix, format, etc.).
     */
    public void spawnRestored(String name, UUID uuid, String savedDisplayName, 
                              String spawnedBy, UUID spawnedByUuid, Location location) {
        // Skip if name already active (e.g. duplicate in save file)
        if (usedNames.contains(name)) return;

        PlayerProfile profile = Bukkit.createProfile(uuid, name);
        FakePlayer fp = new FakePlayer(uuid, name, profile);
        // For restored bots: if name is a valid Minecraft identifier (pool name), use it directly.
        // If it looks like a user-bot internal name (ubot_*), pick a random pool name.
        fp.setSkinName(name.startsWith("ubot_") ? pickRandomSkinName() : name);
        
        // Determine if this is a user bot or admin bot based on internal name
        boolean isUserBot = name.startsWith("ubot_");
        
        // Use saved display name if available and valid, otherwise reconstruct it
        String displayName = null;
        
        // Check if saved display name is usable
        if (savedDisplayName != null && !savedDisplayName.isBlank()) {
            // Check if saved display name contains unreplaced placeholders (from old buggy saves)
            if (savedDisplayName.contains("{spawner}") || savedDisplayName.contains("{num}") || savedDisplayName.contains("{bot_name}")) {
                // Saved display has placeholder syntax - needs reconstruction
                Config.debug("[Restore] Saved display contains placeholders - reconstructing: '" + savedDisplayName + "'");
                // Don't use it, will reconstruct below
            } else {
                // Additional check: verify display format matches bot type
                // This prevents admin bots from incorrectly using user format (bot-{spawner}-{num})
                String userFormatPrefix = "bot-" + spawnedBy + "-";
                boolean looksLikeUserFormat = savedDisplayName.contains(userFormatPrefix);
                
                if (!isUserBot && looksLikeUserFormat) {
                    // Admin bot but saved display uses user format - force reconstruction
                    Config.debug("[Restore] Admin bot '" + name + "' has user-format display '" + savedDisplayName + "' - reconstructing with admin format");
                    // Don't use it, will reconstruct below
                } else {
                    // Display format is appropriate for bot type - use it
                    displayName = savedDisplayName;
                    Config.debug("[Restore] Using saved display name: '" + displayName + "'");
                }
            }
        }
        
        // If display name wasn't set above, reconstruct it
        if (displayName == null) {
            // Fallback: reconstruct display name (legacy support for old persistence files or broken saves)
            // Detect if this was a user bot (name starts with ubot_) or admin bot
            if (name.startsWith("ubot_")) {
                // User bot - try to reconstruct user format
                LuckPermsHelper.LpData lpData = LuckPermsHelper.getBotLpData(spawnedByUuid);
                String lpPrefix = Config.luckpermsUsePrefix() ? lpData.prefix() : "";
                // Extract bot number from internal name if possible
                // ubot_PlayerName_1 -> extract "1"
                String botNum = "1"; // default
                if (name.matches("^ubot_.+_(\\d+)$")) {
                    botNum = name.replaceFirst("^ubot_.+_(\\d+)$", "$1");
                }
                displayName = lpPrefix + Config.userBotNameFormat()
                        .replace("{spawner}", spawnedBy)
                        .replace("{num}", botNum)
                        .replace("{bot_name}", name);
                Config.debug("[Restore] Reconstructed user-bot display: '" + displayName + "'");
            } else {
                // Admin bot - use admin format
                LuckPermsHelper.LpData lpData = LuckPermsHelper.getBotLpData(null);
                String lpPrefix = Config.luckpermsUsePrefix() ? lpData.prefix() : "";
                displayName = lpPrefix + Config.adminBotNameFormat().replace("{bot_name}", name);
                Config.debug("[Restore] Reconstructed admin-bot display: '" + displayName + "'");
            }
        }
        
        fp.setDisplayName(displayName);
        
        // Build packet name for tab-list ordering (re-resolve weight on restore)
        LuckPermsHelper.LpData lpData = LuckPermsHelper.getBotLpData(
            name.startsWith("ubot_") ? spawnedByUuid : null
        );
        String pktRestored = LuckPermsHelper.buildPacketProfileName(lpData.weight(), name);
        fp.setPacketProfileName(pktRestored);
        Config.debug("[LP] restored-bot '" + name + "' weight=" + lpData.weight()
                + " pkt='" + pktRestored + "' display='" + displayName + "'");
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

        // leave-delay values are in seconds; we use stagger when max > 0
        int delayMinSecs = Config.leaveDelayMin();
        int delayMaxSecs = Math.max(delayMinSecs, Config.leaveDelayMax());
        boolean stagger = delayMaxSecs > 0;

        // Snapshot and clear registry immediately — prevents double-removal
        List<FakePlayer> toRemove = new ArrayList<>(activePlayers.values());
        activePlayers.clear();
        usedNames.clear();
        entityIdIndex.clear();

        // Cancel all swap timers
        if (swapAI != null) swapAI.cancelAll();

        // Calculate max possible delay for scheduling the final orphan sweep
        long maxDelay = 0;

        for (int i = 0; i < toRemove.size(); i++) {
            FakePlayer fp = toRemove.get(i);

            // Each bot gets a random delay; index offset guarantees stagger
            long leaveDelayTicks;
            if (!stagger) {
                leaveDelayTicks = 0L;
            } else {
                int spread = delayMaxSecs - delayMinSecs;
                int secs = delayMinSecs + (spread > 0
                        ? ThreadLocalRandom.current().nextInt(spread + 1)
                        : 0);
                // convert seconds to ticks and add a small index-based tick jitter so removals are staggered
                leaveDelayTicks = Math.max(1L, (long) secs * 20L) + i; // +i ticks guarantees order without large extra delay
            }
            maxDelay = Math.max(maxDelay, leaveDelayTicks);

            final FakePlayer target = fp;
            Runnable doVisualRemove = () -> {
                FakePlayerBody.removeAll(target);
                if (chunkLoader != null) chunkLoader.releaseForBot(target);

                List<Player> snapshot = new ArrayList<>(Bukkit.getOnlinePlayers());
                for (Player online : snapshot) PacketHelper.sendTabListRemove(online, target);

                if (Config.leaveMessage()) {
                    BotBroadcast.broadcastLeave(target);
                }
                if (db != null) db.recordRemoval(target.getUuid(), "DELETED");
                Config.debug("Removed bot: " + target.getName());
            };

            if (leaveDelayTicks <= 0L) {
                Bukkit.getScheduler().runTask(plugin, doVisualRemove);
            } else {
                Bukkit.getScheduler().runTaskLater(plugin, doVisualRemove, leaveDelayTicks);
            }
        }

        // Final orphan sweep: runs 20 ticks after all bots should be removed.
        // Catches any entity that didn't clean up properly (cross-world, chunk issues).
        final long sweepDelay = maxDelay + 20L;
        Bukkit.getScheduler().runTaskLater(plugin, () -> {
            int swept = FakePlayerBody.sweepOrphans(java.util.Collections.emptySet());
            if (swept > 0) FppLogger.info("Post-removeAll orphan sweep: removed " + swept + " entities.");
            if (persistence != null && Config.persistOnRestart()) {
                persistence.saveAsync(activePlayers.values());
            }
        }, sweepDelay);

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

        final FakePlayer target   = fp;
        final String     botName  = target.getName();

        // Remove from registry immediately — prevents double-delete and clears tab-complete
        activePlayers.remove(target.getUuid());
        usedNames.remove(botName);
        // Remove entity-id index entries now so no stale lookups remain
        if (target.getPhysicsEntity() != null)
            entityIdIndex.remove(target.getPhysicsEntity().getEntityId());

        // Cancel any pending swap timer so it doesn't rejoin after deletion
        if (swapAI != null) swapAI.cancel(target.getUuid());

        // Defer body removal, tab-list, despawn and leave message together
        int delayMinSecs = Config.leaveDelayMin();
        int delayMaxSecs = Math.max(delayMinSecs, Config.leaveDelayMax());
        long leaveDelay;
        if (delayMaxSecs <= 0) {
            leaveDelay = 0L;
        } else {
            int spread = delayMaxSecs - delayMinSecs;
            int secs = delayMinSecs + (spread > 0
                    ? ThreadLocalRandom.current().nextInt(spread + 1)
                    : 0);
            leaveDelay = Math.max(1L, (long) secs * 20L);
        }

        Runnable doVisualRemove = () -> {
            // Primary removal via entity reference
            FakePlayerBody.removeAll(target);
            if (chunkLoader != null) chunkLoader.releaseForBot(target);

            List<Player> snapshot = new ArrayList<>(Bukkit.getOnlinePlayers());
            for (Player online : snapshot) PacketHelper.sendTabListRemove(online, target);

            if (Config.leaveMessage()) {
                BotBroadcast.broadcastLeave(target);
            }
            if (db != null) db.recordRemoval(target.getUuid(), "DELETED");
            Config.debug("Deleted fake player: " + botName);
            if (persistence != null && Config.persistOnRestart()) {
                persistence.saveAsync(activePlayers.values());
            }

            // Deferred world-scan to catch any entity that survived the direct remove
            // (e.g., entity in a chunk that was loading at delete-time)
            Bukkit.getScheduler().runTaskLater(plugin, () -> {
                FakePlayerBody.removeOrphanedBodies(botName);
                FakePlayerBody.removeOrphanedNametags(botName);
            }, 10L);
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

        // leave-delay in config is seconds; convert to ticks->ms below
        int delayMinSecs = Config.leaveDelayMin();
        int delayMaxSecs = Math.max(delayMinSecs, Config.leaveDelayMax());
        int spreadSecs   = delayMaxSecs - delayMinSecs;

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
            if (delayMaxSecs > 0 && i < toRemove.size() - 1) {
                int secs = delayMinSecs + (spreadSecs > 0
                        ? ThreadLocalRandom.current().nextInt(spreadSecs + 1)
                        : 0);
                if (secs > 0) sleepMs((long) secs * 1000L);
            }
        }
    }

    /** Sleeps the current thread for {@code ms} milliseconds, handling interruption cleanly. */
    private static void sleepMs(long ms) {
        try {
            Thread.sleep(ms);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
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

    /**
     * Cancels all pending swap session timers.
     * Call after disabling swap via config reload or {@code /fpp swap off}
     * so no in-flight doLeave tasks fire after swap is turned off.
     */
    public void cancelAllSwap() {
        if (swapAI != null) swapAI.cancelAll();
    }

    /**
     * Immediately removes the physical Mannequin body and nametag ArmorStand
     * from every active bot when {@code body.enabled} is {@code false}.
     * Safe to call on the main thread after a config reload.
     * Has no effect when bodies are enabled.
     */
    public void applyBodyConfig() {
        if (physicalBodiesEnabled()) return;
        for (FakePlayer fp : activePlayers.values()) {
            Entity body = fp.getPhysicsEntity();
            if (body != null) {
                try { body.remove(); } catch (Exception ignored) {}
                entityIdIndex.remove(body.getEntityId());
                fp.setPhysicsEntity(null);
            }
            ArmorStand as = fp.getNametagEntity();
            if (as != null) {
                try { as.remove(); } catch (Exception ignored) {}
                fp.setNametagEntity(null);
            }
        }
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
     * Syncs all existing fake players' tab list entries to a real player.
     * Called after initial join AND after world changes.
     *
     * <p>The Mannequin body and ArmorStand nametag are real server-side entities
     * kept alive by chunk tickets — no respawn needed. Only the packet-based
     * tab-list entries must be re-sent (Minecraft clears them on world transitions).
     */
    public void syncToPlayer(Player player) {
        if (!Config.tabListEnabled()) return;
        for (FakePlayer fp : activePlayers.values()) {
            PacketHelper.sendTabListAdd(player, fp);
        }
    }

    /**
     * Called after {@code /fpp reload} to apply the {@code tab-list.enabled} toggle immediately.
     * <ul>
     *   <li>If {@code enabled} is now {@code true}  — re-adds all active bots to every
     *       online player's tab list so they appear instantly without needing to respawn.</li>
     *   <li>If {@code enabled} is now {@code false} — removes all active bots from every
     *       online player's tab list.</li>
     * </ul>
     */
    public void applyTabListConfig() {
        List<Player> online = new ArrayList<>(Bukkit.getOnlinePlayers());
        if (online.isEmpty() || activePlayers.isEmpty()) return;

        if (Config.tabListEnabled()) {
            for (FakePlayer fp : activePlayers.values()) {
                for (Player p : online) {
                    PacketHelper.sendTabListAdd(p, fp);
                    PacketHelper.sendTabListDisplayNameUpdate(p, fp);
                }
            }
            Config.debug("applyTabListConfig: re-added " + activePlayers.size() + " bots to tab list.");
        } else {
            for (FakePlayer fp : activePlayers.values()) {
                for (Player p : online) {
                    PacketHelper.sendTabListRemove(p, fp);
                }
            }
            Config.debug("applyTabListConfig: removed " + activePlayers.size() + " bots from tab list.");
        }
    }

    /**
     * Performs a periodic validation pass across all active bots:
     * <ol>
     *   <li>Checks that each bot's physics entity is still valid.</li>
     *   <li>If the body has become invalid (e.g., Mannequin entered DYING pose
     *       and was removed by Minecraft), attempts to respawn it.</li>
     *   <li>Sweeps all loaded worlds for orphaned FPP entities that belong to
     *       bots no longer in the active map and removes them.</li>
     * </ol>
     * Called every few minutes by the scheduler in FakePlayerPlugin.
     */
    public void validateEntities() {
        // ── Collect active bot names for orphan detection ────────────────────
        java.util.Set<String> activeNames = activePlayers.values().stream()
                .map(FakePlayer::getName)
                .collect(java.util.stream.Collectors.toSet());

        // ── Check each active bot's entity state ─────────────────────────────
        for (FakePlayer fp : activePlayers.values()) {
            Entity body = fp.getPhysicsEntity();

            // Bodies disabled: remove any that still exist (e.g. after a config reload)
            // and skip all respawn logic — this handles body.enabled toggled via /fpp reload.
            if (!physicalBodiesEnabled()) {
                if (body != null && body.isValid()) {
                    try { body.remove(); } catch (Exception ignored) {}
                    entityIdIndex.remove(body.getEntityId());
                    fp.setPhysicsEntity(null);
                }
                ArmorStand as = fp.getNametagEntity();
                if (as != null) {
                    try { as.remove(); } catch (Exception ignored) {}
                    fp.setNametagEntity(null);
                }
                continue;
            }

            // Body is valid — nothing to do
            if (body != null && body.isValid()) continue;

            // Body became invalid (DYING pose removed by Minecraft, or other cause)
            Config.debug("validateEntities: body of '" + fp.getName() + "' invalid — attempting respawn.");

            fp.setPhysicsEntity(null);
            ArmorStand as = fp.getNametagEntity();
            if (as != null) {
                try { as.remove(); } catch (Exception ignored) {}
                fp.setNametagEntity(null);
            }


            // Try to re-spawn at last known location
            org.bukkit.Location loc = fp.getSpawnLocation();
            if (loc == null || loc.getWorld() == null) continue;

            Entity newBody = FakePlayerBody.spawn(fp, loc);
            if (newBody == null) continue;

            fp.setPhysicsEntity(newBody);
            entityIdIndex.put(newBody.getEntityId(), fp);

            final FakePlayer target = fp;
            final Entity bodyRef   = newBody;
            Bukkit.getScheduler().runTaskLater(plugin, () -> {
                if (!bodyRef.isValid()) return;
                FakePlayerBody.applyResolvedSkin(plugin, target, bodyRef);
                target.setNametagEntity(FakePlayerBody.spawnNametag(target, bodyRef));
                // Re-send tab list so skin + name update for everyone
                for (Player p : Bukkit.getOnlinePlayers()) PacketHelper.sendTabListAdd(p, target);
            }, 2L);
        }

        // ── Sweep orphaned entities ──────────────────────────────────────────
        int swept = FakePlayerBody.sweepOrphans(activeNames);
        if (swept > 0) {
            FppLogger.info("Orphan sweep: removed " + swept + " stale FPP entity/entities.");
        }
    }

    // ── Queries ──────────────────────────────────────────────────────────────

    /** Returns the FakePlayer whose physics body has the given entity id, or null. O(1). */
    public FakePlayer getByEntity(Entity entity) {
        return entityIdIndex.get(entity.getEntityId());
    }

    /**
     * Returns the active {@link FakePlayer} with the given UUID, or {@code null}
     * if no bot with that UUID is currently registered.
     */
    public FakePlayer getByUuid(UUID uuid) {
        if (uuid == null) return null;
        for (FakePlayer fp : activePlayers.values()) {
            if (uuid.equals(fp.getUuid())) return fp;
        }
        return null;
    }

    /** Removes a stale entry from the entity-ID index. Called by the death handler. */
    public void removeFromEntityIndex(int entityId) {
        entityIdIndex.remove(entityId);
    }

    /** Registers or updates an entity-ID → FakePlayer mapping. Called after respawn. */
    public void registerEntityIndex(int entityId, FakePlayer fp) {
        entityIdIndex.put(entityId, fp);
    }

    /** Returns a list of all active bot names (for tab-completion). */
    public List<String> getActiveNames() {
        return activePlayers.values().stream().map(FakePlayer::getName).collect(Collectors.toList());
    }

    /**
     * Returns the active {@link FakePlayer} with the given internal name (case-insensitive),
     * or {@code null} if no matching bot is active. O(n) — use sparingly.
     */
    public FakePlayer getByName(String name) {
        if (name == null || name.isBlank()) return null;
        for (FakePlayer fp : activePlayers.values()) {
            if (fp.getName().equalsIgnoreCase(name)) return fp;
        }
        return null;
    }

    // ── Spawn cooldown helpers ────────────────────────────────────────────────

    /**
     * Returns {@code true} if the given player is currently on spawn cooldown.
     * Always returns {@code false} when {@code spawn-cooldown} is 0.
     */
    public boolean isOnCooldown(UUID playerUuid) {
        int secs = Config.spawnCooldown();
        if (secs <= 0) return false;
        Long last = spawnCooldowns.get(playerUuid);
        if (last == null) return false;
        return (System.currentTimeMillis() - last) / 1000L < secs;
    }

    /**
     * Returns the remaining cooldown seconds for the given player (0 if none).
     */
    public long getRemainingCooldown(UUID playerUuid) {
        int secs = Config.spawnCooldown();
        if (secs <= 0) return 0;
        Long last = spawnCooldowns.get(playerUuid);
        if (last == null) return 0;
        long elapsed = (System.currentTimeMillis() - last) / 1000L;
        return Math.max(0, secs - elapsed);
    }

    /** Records the current time as the last spawn instant for {@code playerUuid}. */
    public void recordSpawnCooldown(UUID playerUuid) {
        spawnCooldowns.put(playerUuid, System.currentTimeMillis());
    }

    /** Clears the spawn cooldown for a player (e.g. after an admin bypass). */
    public void clearCooldown(UUID playerUuid) {
        spawnCooldowns.remove(playerUuid);
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
     * Updates display names and tab-list entries for all active bots.
     * Called automatically when LuckPerms group data changes.
     * @return number of bots updated
     */
    public int updateAllBotPrefixes() {
        if (activePlayers.isEmpty()) return 0;

        int updated = 0;
        List<Player> online = new ArrayList<>(Bukkit.getOnlinePlayers());

        for (FakePlayer fp : activePlayers.values()) {
            try {
                // Re-resolve LP data for this bot
                UUID ownerUuid = fp.getSpawnedByUuid();
                LuckPermsHelper.LpData lpData = LuckPermsHelper.getBotLpData(ownerUuid);
                
                // Build new display name with updated prefix
                String lpPrefix = Config.luckpermsUsePrefix() ? lpData.prefix() : "";
                String botName = fp.getName();
                String nameFormat = ownerUuid != null 
                    ? Config.userBotNameFormat() 
                    : Config.adminBotNameFormat();
                String newDisplayName = lpPrefix + nameFormat.replace("{bot_name}", botName);
                
                // Only update if changed
                if (!newDisplayName.equals(fp.getDisplayName())) {
                    fp.setDisplayName(newDisplayName);
                    
                    // Update packet profile name for tab-list ordering
                    String newPacketName = LuckPermsHelper.buildPacketProfileName(lpData.weight(), botName);
                    fp.setPacketProfileName(newPacketName);
                    
                    // Update nametag entity if it exists
                    ArmorStand nametag = fp.getNametagEntity();
                    if (nametag != null && nametag.isValid()) {
                        nametag.customName(me.bill.fakePlayerPlugin.util.TextUtil.colorize(newDisplayName));
                    }
                    
                    // Re-send tab-list update to all online players
                    for (Player p : online) {
                        PacketHelper.sendTabListDisplayNameUpdate(p, fp);
                    }
                    
                    updated++;
                    Config.debug("[LP-Auto-Update] Updated bot '" + botName + "' -> '" + newDisplayName + "'");
                }
            } catch (Exception e) {
                Config.debug("[LP-Auto-Update] Failed to update bot: " + e.getMessage());
            }
        }

        return updated;
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

    /**
     * Picks a random name from the bot-names pool for use as a skin source.
     * Unlike {@link #generateName()}, this does NOT reserve the name —
     * it is used only for skin lookup (Mojang API / auto resolution).
     * Falls back to a random entry without worrying about uniqueness.
     */
    private String pickRandomSkinName() {
        List<String> pool = Config.namePool();
        if (pool.isEmpty()) return "Steve"; // absolute fallback
        return pool.get(ThreadLocalRandom.current().nextInt(pool.size()));
    }

    private String generateName() {
        List<String> pool = Config.namePool();
        if (pool.isEmpty()) return fallbackName();

        String chosen  = null;
        int    count   = 0;
        for (String n : pool) {
            // Enforce Minecraft username constraints: 1-16 chars, letters/digits/underscore
            if (n == null || n.isEmpty() || n.length() > 16
                    || !n.matches("[a-zA-Z0-9_]+")) continue;
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
     * Generates a valid internal Minecraft name and a display name for a user-tier bot.
     *
     * @param spawnerName   the spawning player's Minecraft name
     * @param existingCount bots this player already owns (used for the numeric suffix)
     */
    public UserBotName generateUserBotName(String spawnerName, int existingCount) {
        String suffix   = String.valueOf(existingCount + 1);
        String internal = "ubot_" + spawnerName + "_" + suffix;
        if (internal.length() > 16) internal = internal.substring(0, 16);
        usedNames.add(internal);
        // Use LP helper for consistency — display name built by caller with the same helper
        String lpPrefix = LuckPermsHelper.getBotPrefix();
        String display  = lpPrefix + Config.userBotNameFormat()
                .replace("{spawner}", spawnerName)
                .replace("{num}",     suffix)
                .replace("{bot_name}", internal);
        return new UserBotName(internal, display);
    }

    // ── LuckPerms public accessor ─────────────────────────────────────────────

    /**
     * Returns the detected LP prefix for startup/reload diagnostic logging.
     * Delegates entirely to {@link LuckPermsHelper#detectDefaultPrefix()}.
     */
    public String detectLuckPermsPrefix() {
        return LuckPermsHelper.detectDefaultPrefix();
    }

}
