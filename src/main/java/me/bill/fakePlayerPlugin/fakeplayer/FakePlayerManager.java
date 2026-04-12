package me.bill.fakePlayerPlugin.fakeplayer;

import com.destroystokyo.paper.profile.PlayerProfile;
import me.bill.fakePlayerPlugin.FakePlayerPlugin;
import me.bill.fakePlayerPlugin.config.Config;
import me.bill.fakePlayerPlugin.database.BotRecord;
import me.bill.fakePlayerPlugin.database.DatabaseManager;
import me.bill.fakePlayerPlugin.util.BadwordFilter;
import me.bill.fakePlayerPlugin.util.BotTabTeam;
import me.bill.fakePlayerPlugin.util.FppLogger;
import net.kyori.adventure.text.Component;
import org.bukkit.Bukkit;
import org.bukkit.GameMode;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.World;
import org.bukkit.util.BlockIterator;
import org.bukkit.entity.Entity;
import org.bukkit.entity.ExperienceOrb;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;

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
    /**
     * Secondary index: lowercase bot name → FakePlayer for O(1) getByName().
     * Maintained in sync with {@link #activePlayers} at every put/remove site.
     */
    private final Map<String, FakePlayer> nameIndex = new ConcurrentHashMap<>();
    private final Set<String> usedNames = new HashSet<>();
    /** Per-player spawn cooldown: UUID → last spawn timestamp (ms). */
    private final Map<UUID, Long> spawnCooldowns = new ConcurrentHashMap<>();
    /**
     * Smooth head rotation state for the head-AI system.
     * float[0] = current yaw, float[1] = current pitch (both in degrees).
     * Initialised lazily on first head-AI tick for each bot.
     */
    private final Map<UUID, float[]> botHeadRotation = new ConcurrentHashMap<>();
    /**
     * The rotation a bot faces when idle (i.e. no visible target nearby).
     * Captured from the spawn location on first head-AI tick so the bot always
     * returns to its original facing direction instead of freezing mid-track.
     */
    private final Map<UUID, float[]> botSpawnRotation = new ConcurrentHashMap<>();
    /** Flag set to true during bot restoration, cleared after prefix refresh completes. */
    private volatile boolean restorationInProgress = false;

    /**
     * Pre-filtered name pool: names from bot-names.yml that passed the badword filter,
     * length, and charset checks.  Built once at startup and after every {@code /fpp reload}
     * via {@link #refreshCleanNamePool()}.  Using a cached list avoids running the expensive
     * regex-based badword passes on every single {@link #generateName()} call, which would
     * cause severe main-thread lag when spawning 100+ bots simultaneously.
     *
     * <p>Pool names are admin-curated; only {@code --name} custom names entered by users
     * are checked on-the-fly in {@code SpawnCommand}.
     */
    private volatile List<String> cleanNamePool = Collections.emptyList();

    // ── Per-tick performance caches ───────────────────────────────────────────

    /**
     * Snapshot of online players taken at the start of every physics tick (1L,1L).
     * Shared with the display-name timer (20L,20L) so both timers pay one
     * {@code Bukkit.getOnlinePlayers()} copy per physics tick instead of two.
     */
    private List<Player> cachedOnlinePlayers = new ArrayList<>();

    /**
     * Incremented every physics tick.  Used to throttle the head-AI raycast loop
     * to every {@link me.bill.fakePlayerPlugin.config.Config#headAiTickRate()} ticks
     * instead of every game tick.
     */
    private int headAiTickCounter = 0;
    /**
     * UUIDs of bots currently mid body-transition (hide or show via applyBodyConfig).
     * Checked by {@link me.bill.fakePlayerPlugin.listener.PlayerJoinListener} to
     * suppress vanilla join/quit messages during the transition.
     */
    private final Set<UUID> bodyTransitionBots = new HashSet<>();
    /**
     * Navigation jump-hold timer.  When a navigation system (e.g. {@code MoveCommand})
     * needs the bot to jump on land, it calls {@link #requestNavJump(UUID)} which sets
     * a hold-count here.  The swim-AI block in the physics tick loop reads this map and
     * keeps {@code jumping = true} for that many ticks, preventing the normal
     * {@code setJumping(false)} reset from cancelling the jump before physics fires.
     * <p>
     * Using a countdown (rather than a one-shot boolean) ensures the jump flag stays
     * set long enough for {@code LivingEntity.travel()} to call {@code jumpFromGround()}
     * even if the bot is momentarily airborne when the first tick fires.
     */
    private final Map<UUID, Integer> navJumpHolding = new ConcurrentHashMap<>();

    /**
     * Action-locked bots.  When a bot is locked for a sustained action (mining, storage
     * interaction, right-click loop), its position {@link Location} (including yaw + pitch)
     * is stored here.
     * <ul>
     *   <li>The head-AI loop <b>skips</b> bots in this map so they never auto-rotate.</li>
     *   <li>The physics tick enforces the locked position every tick so physics or
     *       knockback cannot push the bot off its action spot.</li>
     *   <li>The locked rotation is sent to nearby players every tick so the action
     *       direction is visible.</li>
     * </ul>
     * Populated by {@link #lockForAction(UUID, Location)} (called from MineCommand,
     * PlaceCommand, UseCommand, and {@link StorageInteractionHelper});
     * removed by {@link #unlockAction(UUID)} when the action stops or the bot despawns.
     */
    private final ConcurrentHashMap<UUID, Location> actionLockedBots = new ConcurrentHashMap<>();

    /**
     * Navigation-locked bots.  When a bot is actively navigating via {@code MoveCommand}
     * (player-follow or waypoint-patrol), its UUID is added here so the head-AI loop
     * does not override the direction being set every tick by the navigation task.
     * Populated by {@link #lockForNavigation(UUID)}; removed by {@link #unlockNavigation(UUID)}.
     */
    private final Set<UUID> navLockedBots = ConcurrentHashMap.newKeySet();

    /**
     * UUIDs of bots that are being permanently despawned via {@link #delete} or
     * {@link #removeAll}.  Populated immediately before {@code FakePlayerBody.removeAll()}
     * fires {@code PlayerQuitEvent}, cleared right after.  Lets
     * {@link me.bill.fakePlayerPlugin.listener.PlayerJoinListener} suppress the vanilla
     * quit message (Paper sets none for programmatic removals anyway, but this guards
     * against double-messages on servers where it does).
     * Value = the bot's display name at despawn time so the MONITOR quit handler can build
     * the leave message even after the bot has been removed from {@code activePlayers}.
     */
    private final ConcurrentHashMap<UUID, String> despawningBotIds = new ConcurrentHashMap<>();

    /**
     * UUIDs of bots involved in an active rename operation (both the old UUID being
     * deleted and the new UUID being spawned).  While a UUID is in this set, the
     * standard join/leave broadcasts are suppressed; a single "renamed" message is
     * sent instead once the respawn completes.
     */
    private final Set<UUID> renamingBotIds = ConcurrentHashMap.newKeySet();

    private ChunkLoader     chunkLoader;
    private DatabaseManager db;
    private BotPersistence  persistence;
    private BotTabTeam      botTabTeam;
    /** PVP bot AI - movement + attack loop for all {@link BotType#PVP} bots. */
    private final BotPvpAI  pvpAI;
    /** Swap AI - session rotation system for all {@link BotType#AFK} bots. */
    private BotSwapAI       botSwapAI;
    /**
     * Stable name→UUID registry.  When set, replaces {@link UUID#randomUUID()} so every
     * bot with a given name on this server always gets the same UUID across restarts.
     * Injected by {@link me.bill.fakePlayerPlugin.FakePlayerPlugin} after DB init.
     */
    private BotIdentityCache identityCache;

    public void setChunkLoader(ChunkLoader cl) { this.chunkLoader = cl; }
    public void setDatabaseManager(DatabaseManager db) { this.db = db; }
    public void setBotPersistence(BotPersistence p) { this.persistence = p; }
    public void setBotTabTeam(BotTabTeam t) { this.botTabTeam = t; }
    public void setBotSwapAI(BotSwapAI ai) { this.botSwapAI = ai; }
    /** Injects the identity cache so UUID generation uses the stable registry. */
    public void setIdentityCache(BotIdentityCache ic) { this.identityCache = ic; }

    /**
     * Pre-filters the name pool against the badword filter and caches the result.
     *
     * <p>Must be called once at startup (after both {@code BotNameConfig} and
     * {@code BadwordFilter} are initialised) and again after every {@code /fpp reload}.
     * After this call, {@link #generateName()} uses the cached list directly, so the
     * expensive regex-based badword detection never runs on the main thread during a
     * batch spawn — no matter how many bots are spawned at once.
     *
     * <p>Only custom names supplied via {@code --name} are checked on-the-fly in
     * {@code SpawnCommand}; pool names are admin-curated and filtered here instead.
     */
    public void refreshCleanNamePool() {
        List<String> raw   = me.bill.fakePlayerPlugin.config.Config.namePool();
        List<String> clean = new ArrayList<>(raw.size());
        for (String n : raw) {
            if (n == null || n.isEmpty() || n.length() > 16
                    || !n.matches("[a-zA-Z0-9_]+")) continue;
            if (!me.bill.fakePlayerPlugin.util.BadwordFilter.isAllowed(n)) continue;
            clean.add(n);
        }
        cleanNamePool = Collections.unmodifiableList(clean);
        me.bill.fakePlayerPlugin.config.Config.debugStartup(
                "Clean name pool refreshed: " + clean.size() + "/" + raw.size()
                + " names pass the badword filter.");
    }

    /** Returns the {@link BotChatAI} instance, or {@code null} if not yet initialised. */
    public BotChatAI getBotChatAI() { return plugin.getBotChatAI(); }
    /** Returns the {@link BotSwapAI} instance, or {@code null} if not yet initialised. */
    public BotSwapAI getBotSwapAI() { return botSwapAI; }


    public FakePlayerManager(FakePlayerPlugin plugin) {
        this.plugin = plugin;
        FAKE_PLAYER_KEY = new NamespacedKey(plugin, "fake_player_name");
        this.pvpAI      = new BotPvpAI(this);

        // Flush each bot's current position to the DB on the configured interval
        long flushTicks = Math.max(20L, Config.dbLocationFlushInterval() * 20L);
        Bukkit.getScheduler().runTaskTimer(plugin, () -> {
            if (activePlayers.isEmpty()) return;
            for (FakePlayer fp : activePlayers.values()) {
                // Use getLiveLocation() - prefers NMS Player body over stored spawn pos
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
        // Reads cachedOnlinePlayers (populated by the 1-tick physics timer below) so no
        // additional Bukkit.getOnlinePlayers() copy is needed here.
        Bukkit.getScheduler().runTaskTimer(plugin, () -> {
            if (!Config.tabListEnabled()) return;
            if (activePlayers.isEmpty()) return;
            List<Player> online = cachedOnlinePlayers;
            if (online.isEmpty()) return;
            for (FakePlayer fp : activePlayers.values()) {
                for (Player p : online) {
                    PacketHelper.sendTabListDisplayNameUpdate(p, fp);
                }
            }
        }, 20L, 20L);

        // Every tick: run manual physics for each bot (gravity, velocity decay, knockback
        // integration) then sync the updated position to nearby real players via packets.
        // NMS ServerPlayer movement is client-authoritative - the server never moves a player
        // on its own; without this loop bots float, ignore gravity, and can't be knocked back.
        Bukkit.getScheduler().runTaskTimer(plugin, () -> {
            if (activePlayers.isEmpty()) return;

            // ── Snapshot online players ONCE per tick ────────────────────────
            // Shared with the display-name timer and all inner loops so we pay
            // one ArrayList copy per game tick regardless of bot or player count.
            cachedOnlinePlayers = new ArrayList<>(Bukkit.getOnlinePlayers());
            List<Player> online = cachedOnlinePlayers;

            // ── Hoist all per-tick config reads outside the per-bot loop ─────
            // Config reads involve YAML map lookups; hoisting them here cuts
            // that cost from O(bots) to O(1) per tick.
            headAiTickCounter++;
            final boolean headAiOn  = Config.headAiEnabled();
            final int     headAiRate = Config.headAiTickRate();
            // Only run the expensive raycast loop every N ticks.
            final boolean doHeadAi  = headAiOn && (headAiTickCounter % headAiRate == 0);
            final double  rangeSq   = doHeadAi ? Config.headAiLookRange() * Config.headAiLookRange() : 0;
            final float   speed     = doHeadAi ? Config.headAiTurnSpeed() : 0f;
            // Position-sync distance culling: only send to players within this range.
            // 0 = disabled (send to all players). Saves O(distant_players) packets/tick.
            final double  psd       = Config.positionSyncDistance();
            final double  posSyncDistSq = psd > 0 ? psd * psd : -1;

            for (FakePlayer fp : activePlayers.values()) {
                Player bot = fp.getPlayer();
                // Skip bots that are dead, offline, or invalid - they cannot be ticked.
                // Dead bots are handled by FakePlayerEntityListener.onEntityDeath and will
                // be removed shortly; ticking them would cause NMS errors.
                if (bot == null || !bot.isValid() || !bot.isOnline() || bot.isDead()) continue;
                Location before = bot.getLocation();

                // Physics tick: apply gravity + integrate velocity → moves bot server-side
                if (!fp.isFrozen()) {
                    // Swim AI: mimic a player holding spacebar in water so the bot swims
                    // up to the surface instead of sinking.  We set the NMS `jumping` flag
                    // before doTick() so the vanilla fluid-travel code applies the upward
                    // impulse (+0.04 m/s) on this same tick.
                    // The flag MUST be explicitly cleared when outside fluid - bots have no
                    // real client to reset it, so it would persist and cause endless jumping
                    // on land after exiting water.
                    //
                    // Navigation jump override: process the navJumpHolding countdown
                    // unconditionally so the timer always drains (even if swimAi=false).
                    // While count > 0 the bot must jump on land - we keep jumping=true so
                    // the swim-AI reset doesn't cancel it before tickPhysics fires.
                    Integer navHold = navJumpHolding.get(fp.getUuid());
                    boolean navJump = navHold != null && navHold > 0;
                    if (navHold != null) {
                        if (navHold <= 1) navJumpHolding.remove(fp.getUuid());
                        else navJumpHolding.put(fp.getUuid(), navHold - 1);
                    }

                    if (fp.isSwimAiEnabled()) {
                        NmsPlayerSpawner.setJumping(bot, navJump || bot.isInWater() || bot.isInLava());
                    } else if (navJump) {
                        // swimAi=false means we normally never touch the jumping flag,
                        // but navigation still needs to set it explicitly.
                        NmsPlayerSpawner.setJumping(bot, true);
                    }

                    // PVP AI: set movement velocity toward target BEFORE tickPhysics
                    // so the physics integration step consumes it this tick.
                    if (fp.getBotType() == BotType.PVP) {
                        pvpAI.tickBot(fp, bot, online);
                    }

                    NmsPlayerSpawner.tickPhysics(bot);

                    // Head AI: skip for PVP bots (pvpAI handles rotation)
                    //          skip for action-locked bots (direction must stay fixed for mining/use/storage)
                    //          skip for navigation-locked bots (MoveCommand sets rotation each tick).
                    //          skip for bots with per-bot head-AI disabled.
                    // Only fires every headAiRate ticks to reduce O(bots×players) raycast cost.
                    if (doHeadAi && fp.getBotType() != BotType.PVP
                            && fp.isHeadAiEnabled()
                            && !actionLockedBots.containsKey(fp.getUuid())
                            && !navLockedBots.contains(fp.getUuid())) {

                        // Find the nearest real (non-bot) player within range AND line-of-sight.
                        // Reuse `before` (already captured above) for the distance check so we
                        // don't allocate an extra Location per player per bot per tick.
                        Player target = null;
                        double bestSq = rangeSq;
                        for (Player p : online) {
                            if (activePlayers.containsKey(p.getUniqueId())) continue; // skip bots
                            if (p.getGameMode() == GameMode.SPECTATOR) continue;       // skip spectators
                            if (!p.getWorld().equals(bot.getWorld())) continue;
                            double dSq = before.distanceSquared(p.getLocation()); // reuse 'before'
                            if (dSq > bestSq) continue;                               // outside range
                            if (!hasLineOfSightIgnoringGlass(bot, p)) continue;       // opaque wall in the way
                            bestSq = dSq;
                            target = p;
                        }

                        // Retrieve (or lazily initialise) this bot's current smooth rotation.
                        // Lambda captures 'before' (a Location captured above - effectively final).
                        final Location beforeCapture = before;
                        float[] rot = botHeadRotation.computeIfAbsent(fp.getUuid(), k ->
                                new float[]{beforeCapture.getYaw(), beforeCapture.getPitch()});

                        // Capture the spawn (idle) rotation once per bot - used as the
                        // target when no visible player is within range.
                        float[] spawnRot = botSpawnRotation.computeIfAbsent(fp.getUuid(), k -> {
                            Location sl = fp.getSpawnLocation();
                            if (sl != null) return new float[]{sl.getYaw(), sl.getPitch()};
                            return new float[]{beforeCapture.getYaw(), beforeCapture.getPitch()};
                        });

                        float prevYaw   = rot[0];
                        float prevPitch = rot[1];

                        if (target != null) {
                            // Calculate the yaw/pitch required to face the target's eye
                            Location eye = bot.getEyeLocation();
                            Location tgt = target.getEyeLocation();
                            double dx    = tgt.getX() - eye.getX();
                            double dy    = tgt.getY() - eye.getY();
                            double dz    = tgt.getZ() - eye.getZ();
                            double horiz = Math.sqrt(dx * dx + dz * dz);
                            float targetYaw   = (float) (-Math.toDegrees(Math.atan2(dx, dz)));
                            float targetPitch = (float) (-Math.toDegrees(Math.atan2(dy, horiz)));
                            rot[0] = lerpAngle(rot[0], targetYaw,   speed);
                            rot[1] = lerpAngle(rot[1], targetPitch, speed);
                        } else {
                            // No visible target - smoothly return to spawn-facing direction.
                            rot[0] = lerpAngle(rot[0], spawnRot[0], speed);
                            rot[1] = lerpAngle(rot[1], spawnRot[1], speed);
                        }

                        // Only send packets when rotation actually changed (avoids 20 pkt/s spam)
                        if (Math.abs(rot[0] - prevYaw) > 0.01f || Math.abs(rot[1] - prevPitch) > 0.01f) {
                            bot.setRotation(rot[0], rot[1]);
                            NmsPlayerSpawner.setHeadYaw(bot, rot[0]);
                            for (Player p : online) {
                                if (p.getUniqueId().equals(fp.getUuid())) continue;
                                PacketHelper.sendRotation(p, fp, rot[0], rot[1], rot[0]);
                            }
                        }
                    }

                    // Action lock: enforce exact position + rotation every tick.
                    // Physics (gravity, knockback) can displace the bot; this snaps
                    // it back so it stays on its action spot and keeps the right facing.
                    Location miningLock = actionLockedBots.get(fp.getUuid());
                    if (miningLock != null) {
                        Location cur = bot.getLocation();
                        boolean outOfPlace = !cur.getWorld().equals(miningLock.getWorld())
                                || cur.distanceSquared(miningLock) > 0.0001;
                        if (outOfPlace) {
                            bot.teleport(miningLock);
                        }
                        // Always enforce the locked rotation so the bot faces the correct
                        // mining direction. We do this every tick (not just on change) so
                        // head-AI remnants or knockback cannot shift the facing direction.
                        float ly = miningLock.getYaw();
                        float lp = miningLock.getPitch();
                        bot.setRotation(ly, lp);
                        NmsPlayerSpawner.setHeadYaw(bot, ly);
                        for (Player p : online) {
                            if (p.getUniqueId().equals(fp.getUuid())) continue;
                            PacketHelper.sendRotation(p, fp, ly, lp, ly);
                        }
                        // Zero out velocity so physics does not push the bot next tick.
                        bot.setVelocity(new org.bukkit.util.Vector(0, 0, 0));
                    }
                }

                // Broadcast updated position to nearby real players.
                // Sync on real displacement OR remaining velocity. Using only velocity misses
                // cases where the bot moved this tick (falling, landing, knockback) but friction
                // or collision reduced velocity to ~0 by the end of the integration step.
                Location after = bot.getLocation();
                boolean moved = before.getWorld() == after.getWorld()
                        && before.distanceSquared(after) > 1e-8;
                org.bukkit.util.Vector vel = bot.getVelocity();
                if (moved || vel.lengthSquared() > 1e-6) {
                    if (!online.isEmpty()) {
                        for (Player p : online) {
                            if (p.equals(bot)) continue;
                            // Skip players in a different world or beyond the sync range.
                            // This is the hottest per-player path in the entire tick loop, so
                            // keeping the condition tight (no allocation, no sqrt) matters.
                            if (posSyncDistSq > 0) {
                                if (!p.getWorld().equals(after.getWorld())) continue;
                                if (p.getLocation().distanceSquared(after) > posSyncDistSq) continue;
                            }
                            PacketHelper.sendPositionSync(p, bot);
                        }
                    }
                }
            }
        }, 1L, 1L);
    }

    /**
     * Returns true when the plugin is configured to spawn physical bodies.
     */
    public boolean physicalBodiesEnabled() {
        return Config.spawnBody();
    }

    /** Returns true when restoration is currently in progress (bots being restored from persistence). */
    public boolean isRestorationInProgress() {
        return restorationInProgress;
    }

    /** Internal: set when restoration chain starts, cleared after prefix refresh completes. */
    public void setRestorationInProgress(boolean inProgress) {
        this.restorationInProgress = inProgress;
    }

    /**
     * Returns {@code true} while a bot's body is being shown/hidden by
     * {@link #applyBodyConfig()}.  Checked by
     * {@link me.bill.fakePlayerPlugin.listener.PlayerJoinListener} to
     * suppress vanilla join/quit messages during the body transition.
     */
    public boolean isBodyTransitioning(UUID uuid) {
        return bodyTransitionBots.contains(uuid);
    }

    /**
     * Returns {@code true} if the bot with this UUID is currently being permanently
     * despawned via {@link #delete} or {@link #removeAll}.  Used by
     * {@link me.bill.fakePlayerPlugin.listener.PlayerJoinListener} to suppress the
     * vanilla quit message so the MONITOR quit handler can send the custom leave message.
     */
    public boolean isDespawning(UUID uuid) {
        return despawningBotIds.containsKey(uuid);
    }

    /**
     * Returns the display name stored for a bot being despawned, or {@code null}
     * if the UUID is not in the despawning set.  Used by the MONITOR quit handler
     * to build the leave message after the bot has left {@code activePlayers}.
     */
    public String getDespawningDisplayName(UUID uuid) {
        return despawningBotIds.get(uuid);
    }

    /** Marks a bot UUID as being mid-rename so join/leave broadcasts are suppressed. */
    public void markRenaming(UUID uuid) { renamingBotIds.add(uuid); }

    /** Removes a bot UUID from the rename-in-progress set. */
    public void unmarkRenaming(UUID uuid) { renamingBotIds.remove(uuid); }

    /** Returns {@code true} while the UUID is part of an active rename operation. */
    public boolean isRenaming(UUID uuid) { return renamingBotIds.contains(uuid); }

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
     * client via the NMS ServerPlayer's GameProfile - no HTTP calls.
     *
     * @param spawner the player who issued the command (may be null for console)
     * @return number of bots queued (-1 if at limit)
     */
    public int spawn(Location location, int count, Player spawner) {
        return spawn(location, count, spawner, null, false, BotType.AFK);
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
        return spawn(location, count, spawner, customName, false, BotType.AFK);
    }

    /**
     * User-tier spawn - bot names are always forced to the
     * {@code "[bot] PlayerName"} / {@code "[bot] PlayerName #N"} format.
     * Internal Minecraft names are generated as valid identifiers.
     *
     * @return number of bots queued, or {@code -1} if the global cap was hit
     */
    public int spawnUserBot(Location location, int count, Player spawner, boolean bypassMax) {
        return spawnUserBot(location, count, spawner, bypassMax, BotType.AFK);
    }

    /**
     * User-tier spawn with explicit bot type.
     * PVP bots receive a {@code pvp_} prefix on their display name.
     *
     * @param botType {@link BotType#AFK} for default or {@link BotType#PVP} for PvP
     * @return number of bots queued, or {@code -1} if the global cap was hit
     */
    public int spawnUserBot(Location location, int count, Player spawner, boolean bypassMax, BotType botType) {
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
            UUID uuid = resolveUuid(ubn.internalName());
            PlayerProfile profile = Bukkit.createProfile(uuid, ubn.internalName());
            FakePlayer fp = new FakePlayer(uuid, ubn.internalName(), profile);
            fp.setBotType(botType);
            // For user bots the internal name (ubot_*) has no Mojang skin -
            // pick a random name from the pool to use for skin lookup instead.
            fp.setSkinName(pickRandomSkinName());
            // Build display name from format - LP prefix/suffix injected later by refreshLpDisplayName.
            // Create a clean bot name without the "ubot_" prefix for the {bot_name} placeholder
            String cleanBotName = "bot" + (alreadyOwned + i + 1);
            String rawUserName = Config.userBotNameFormat()
                    .replace("{spawner}", spawnerName)
                    .replace("{num}", String.valueOf(alreadyOwned + i + 1))
                    .replace("{bot_name}", cleanBotName);
            // PVP bots get a recognisable pvp_ prefix on their display name
            if (botType == BotType.PVP) rawUserName = "pvp_" + rawUserName;
            fp.setRawDisplayName(rawUserName);
            String userDisplay = finalizeDisplayName(rawUserName, ubn.internalName());
            fp.setDisplayName(userDisplay);
            fp.setSpawnLocation(location);
            fp.setSpawnedBy(spawnerName, spawnerUuid);
            activePlayers.put(uuid, fp);
            nameIndex.put(ubn.internalName().toLowerCase(), fp);
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
     * Delegates to the BotType overload with {@link BotType#AFK}.
     *
     * @param bypassMax when {@code true} the configured {@code max-bots} cap is ignored
     * @return number of bots queued, 0 if name taken, -1 if at limit, -2 if name invalid
     */
    public int spawn(Location location, int count, Player spawner, String customName, boolean bypassMax) {
        return spawn(location, count, spawner, customName, bypassMax, BotType.AFK);
    }

    /**
     * Full spawn overload with bot-type support.
     * PVP bots receive a {@code pvp_} prefix on both the MC username and display name.
     *
     * @param botType   {@link BotType#AFK} for default or {@link BotType#PVP} for PvP
     * @param bypassMax when {@code true} the configured {@code max-bots} cap is ignored
     * @return number of bots queued, 0 if name taken, -1 if at limit, -2 if name invalid
     */
    public int spawn(Location location, int count, Player spawner, String customName, boolean bypassMax, BotType botType) {
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
            // For PVP bots the effective MC name will be pvp_<customName>
            String effectiveName = (botType == BotType.PVP) ? "pvp_" + customName : customName;
            // Minecraft player name: 1-16 chars, letters/digits/underscore only
            if (effectiveName.isEmpty() || effectiveName.length() > 16
                    || !effectiveName.matches("[a-zA-Z0-9_]+")) return -2;
            // Badword check is handled upstream in SpawnCommand (with auto-rename + leet detection)
            if (usedNames.contains(effectiveName)) return 0; // already active as a bot
            // Block if a real (non-bot) player with this name is currently online
            Player realPlayer = Bukkit.getPlayerExact(effectiveName);
            if (realPlayer != null && !activePlayers.containsKey(realPlayer.getUniqueId())) return -4;
            count = 1; // custom name always spawns exactly one bot
        }

        // ── Step 1: pre-generate names & FakePlayer objects ──────────────────
        List<FakePlayer> batch = new ArrayList<>();
        for (int i = 0; i < count; i++) {
            String baseName; // raw name from pool (no pvp_ prefix)
            String name;     // actual MC username

            if (customName != null) {
                baseName = customName;
                name = (botType == BotType.PVP) ? "pvp_" + customName : customName;
            } else if (botType == BotType.PVP) {
                name = generatePvpName();
                baseName = (name != null && name.startsWith("pvp_")) ? name.substring(4) : name;
            } else {
                name = generateName();
                baseName = name;
            }

            if (name == null) break;
            UUID uuid = resolveUuid(name);
            PlayerProfile profile = Bukkit.createProfile(uuid, name);
            FakePlayer fp = new FakePlayer(uuid, name, profile);
            fp.setBotType(botType);
            // Skin lookup: for PVP bots use the base name (without pvp_ prefix) so Mojang resolves it
            fp.setSkinName(baseName != null ? baseName : name);
            // Build display name from format - LP prefix/suffix injected later by refreshLpDisplayName.
            String rawAdminName = Config.adminBotNameFormat().replace("{bot_name}", name);
            fp.setRawDisplayName(rawAdminName);
            String displayName  = finalizeDisplayName(rawAdminName, name);
            fp.setDisplayName(displayName);
            fp.setSpawnLocation(location);
            fp.setSpawnedBy(spawnerName, spawnerUuid);
            activePlayers.put(uuid, fp);
            nameIndex.put(name.toLowerCase(), fp);
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
        // NmsPlayerSpawner creates ServerPlayer with GameProfile containing skin data
        // automatically - no HTTP skin fetch needed. Start visual chain immediately.
        Bukkit.getScheduler().runTask(plugin, () -> visualChain(batch, 0, location));
        return total;
    }

    /**
     * Spawn with bodyless flag - used by console to spawn bots without physical bodies.
     * Delegates to the BotType overload with {@link BotType#AFK}.
     *
     * @param spawnBodyless when {@code true}, bot has no physical body (tab-list only)
     * @return number of bots queued, 0 if name taken, -1 if at limit, -2 if name invalid
     */
    public int spawnBodyless(Location location, int count, Player spawner, String customName, boolean bypassMax, boolean spawnBodyless) {
        return spawnBodyless(location, count, spawner, customName, bypassMax, spawnBodyless, BotType.AFK);
    }

    /**
     * Spawn with bodyless flag and explicit bot type.
     *
     * @param spawnBodyless when {@code true}, bot has no physical body (tab-list only)
     * @param botType       {@link BotType#AFK} for default or {@link BotType#PVP} for PvP
     * @return number of bots queued, 0 if name taken, -1 if at limit, -2 if name invalid
     */
    public int spawnBodyless(Location location, int count, Player spawner, String customName, boolean bypassMax, boolean spawnBodyless, BotType botType) {
        int result = spawn(location, count, spawner, customName, bypassMax, botType);
        if (result > 0 && spawnBodyless) {
            // Mark the newly spawned bots as bodyless
            String spawnerName = spawner != null ? spawner.getName() : "CONSOLE";
            UUID   spawnerUuid = spawner != null ? spawner.getUniqueId() : new UUID(0, 0);
            
            // Get the bots that were just spawned (find by spawner + recent spawn time)
            long now = System.currentTimeMillis();
            activePlayers.values().stream()
                    .filter(fp -> fp.getSpawnedBy().equals(spawnerName) 
                            && fp.getSpawnedByUuid().equals(spawnerUuid)
                            && (now - fp.getSpawnTime().toEpochMilli()) < 1000) // spawned within last second
                    .limit(result)
                    .forEach(fp -> fp.setBodyless(true));
        }
        return result;
    }

    /**
     * Visually spawns the bot at {@code index} in {@code batch}, then
     * schedules the next one after a random join delay.
     *
     * <p><b>Stack-safety:</b> deleted bots are skipped with a {@code while} loop
     * (not recursion) so a mass-delete cannot cause a {@link StackOverflowError}.
     * Continuation is always via {@link org.bukkit.scheduler.BukkitScheduler#runTask} or
     * {@link org.bukkit.scheduler.BukkitScheduler#runTaskLater} - never a direct call.
     */
    private void visualChain(List<FakePlayer> batch, int index, Location location) {
        if (batch == null) return;

        // Skip any bots that were deleted while skins were loading - use a loop,
        // not recursion, to avoid StackOverflowError on large batch deletions.
        while (index < batch.size() && !activePlayers.containsKey(batch.get(index).getUuid())) {
            index++;
        }
        if (index >= batch.size()) return;

        FakePlayer fp = batch.get(index);
        finishSpawn(fp, location);

        // join-delay values in config are in TICKS (20 ticks = 1 second)
        int delayMinTicks = Config.joinDelayMin();
        int delayMaxTicks = Math.max(delayMinTicks, Config.joinDelayMax());
        long delayTicks;
        if (delayMaxTicks <= 0) {
            delayTicks = 0L;
        } else {
            int spread = delayMaxTicks - delayMinTicks;
            delayTicks = delayMinTicks + (spread > 0
                    ? ThreadLocalRandom.current().nextInt(spread + 1)
                    : 0);
            if (delayTicks < 1) delayTicks = 0L; // allow truly instant (0 ticks)
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

        // ── LuckPerms pre-assignment ──────────────────────────────────────────
        // We MUST assign the LP group BEFORE the NMS ServerPlayer body spawns.
        // When FakePlayerBody.spawn() is called it fires PlayerJoinEvent; LuckPerms
        // and tab-list plugins (e.g. TAB) read the player's group inside that event.
        // If we assign the group afterwards (as before) those plugins always see
        // group=(none) at join time and sort/display the bot incorrectly.
        //
        // ensureGroupBeforeSpawn() intelligently preserves a bot's existing rank
        // across restarts (unless overridden by luckperms.default-group).
        if (plugin.isLuckPermsAvailable()) {
            String cfgGroup = Config.luckpermsDefaultGroup();
            UUID botUuid = fp.getUuid();

            me.bill.fakePlayerPlugin.util.LuckPermsHelper.ensureGroupBeforeSpawn(botUuid, cfgGroup)
                .thenAccept(appliedGroup -> {
                    // Record the group in the bot object for diagnostics + consistency
                    fp.setLuckpermsGroup(appliedGroup);
                    Bukkit.getScheduler().runTask(plugin, () -> {
                        if (!activePlayers.containsKey(botUuid)) return;
                        spawnBodyAndFinish(fp, spawnLoc);
                    });
                })
                .exceptionally(ex -> {
                    FppLogger.warn("[LP] Pre-assign failed for '" + fp.getName() + "': " + ex.getMessage());
                    // Still spawn the bot (LP might be temporarily unreachable)
                    Bukkit.getScheduler().runTask(plugin, () -> {
                        if (!activePlayers.containsKey(botUuid)) return;
                        spawnBodyAndFinish(fp, spawnLoc);
                    });
                    return null;
                });
        } else {
            spawnBodyAndFinish(fp, spawnLoc);
        }
    }

    /**
     * Second half of the spawn pipeline: completes any final pre-spawn work and
     * spawns the NMS body. Called after the LP group has been pre-assigned in
     * storage (or immediately when LP is not installed).
     */
    private void spawnBodyAndFinish(FakePlayer fp, Location spawnLoc) {
        // ── Final spawn pipeline ──────────────────────────────────────────────
        // Fast path: if the skin is already cached the lambda fires on this tick.
        // Async path: the lambda fires immediately (bot spawns without skin), and
        //   once the Mojang/Mineskin fetch completes the skin is pushed to the live
        //   entity - onSkinApplied refreshes display names for all online players.
        FakePlayerBody.resolveAndFinish(plugin, fp, spawnLoc, () -> {
            // Guard: bot may have been removed before the body spawn callback fired.
            // Abort entirely so no stale tab-list add, scoreboard entry, or join
            // message is ever sent.
            if (!activePlayers.containsKey(fp.getUuid())) {
                Config.debug("finishSpawn aborted for '" + fp.getName()
                        + "' - removed before body spawn callback fired.");
                return;
            }

            // ── Assign random AI personality if none yet ──────────────────────
            // This runs once per fresh spawn (restored bots already have aiPersonality set
            // from persistence before this point). A randomly-chosen personality is then
            // stable for the bot's lifetime and stored in DB + YAML so it survives restarts.
            if (fp.getAiPersonality() == null) {
                me.bill.fakePlayerPlugin.ai.PersonalityRepository repo =
                        plugin.getPersonalityRepository();
                if (repo != null && repo.size() > 0) {
                    java.util.List<String> names = repo.getNames();
                    String randomName = names.get(
                            java.util.concurrent.ThreadLocalRandom.current().nextInt(names.size()));
                    fp.setAiPersonality(randomName);
                    if (db != null) db.updateBotAiPersonality(fp.getUuid().toString(), randomName);
                    Config.debugChat("Assigned random AI personality '" + randomName
                            + "' to bot '" + fp.getName() + "'.");
                }
            }

            // 1. Spawn body - NMS ServerPlayer entity
            // Skip if bodyless flag is set (console spawn without location data)
            if (!fp.isBodyless() && Config.spawnBody()) {
                Player body = FakePlayerBody.spawn(fp, spawnLoc);
                if (body != null) {
                    fp.setPhysicsEntity(body);
                    entityIdIndex.put(body.getEntityId(), fp);
                    fp.setPacketProfileName(fp.getName());

                    // WorldGuard: if the bot spawned inside a no-pvp region, teleport it
                    // to a safe location (world spawn) after the 5-tick spawn grace period
                    // expires (BotSpawnProtectionListener blocks PLUGIN teleports during that
                    // window). We use 10 ticks to ensure the protection window is fully clear.
                    if (plugin.isWorldGuardAvailable()
                            && !me.bill.fakePlayerPlugin.util.WorldGuardHelper.isPvpAllowed(spawnLoc)) {
                        org.bukkit.Location wgSafeLoc =
                                me.bill.fakePlayerPlugin.util.WorldGuardHelper.findSafeLocation(spawnLoc.getWorld());
                        if (wgSafeLoc != null) {
                            final Player safeBody    = body;
                            final org.bukkit.Location safeTarget = wgSafeLoc;
                            Bukkit.getScheduler().runTaskLater(plugin, () -> {
                                if (!activePlayers.containsKey(fp.getUuid())) return;
                                if (!safeBody.isOnline()) return;
                                safeBody.teleport(safeTarget,
                                        org.bukkit.event.player.PlayerTeleportEvent.TeleportCause.PLUGIN);
                                fp.setSpawnLocation(safeTarget);
                                Config.debug("WorldGuard: teleported bot '" + fp.getName()
                                        + "' out of no-pvp region to world spawn "
                                        + safeTarget.getBlockX() + "," + safeTarget.getBlockY()
                                        + "," + safeTarget.getBlockZ());
                            }, 10L);
                        } else {
                            me.bill.fakePlayerPlugin.util.FppLogger.warn(
                                    "WorldGuard: bot '" + fp.getName()
                                    + "' spawned in a no-pvp region but world spawn is also"
                                    + " in a no-pvp region - cannot find a safe location.");
                        }
                    }
                    // Persist playerdata 2 ticks after spawn.  This writes
                    // world/playerdata/<uuid>.dat to disk immediately so that
                    // PlayerIo.load() inside the next placeNewPlayer() finds it
                    // and Paper sets isFirstJoin=false - preventing CMI, Essentials,
                    // and other plugins from flagging the bot as a new player every
                    // time it is despawned and respawned with the same name/UUID.
                    final Player savedBody = body;
                    final String savedName = fp.getName();
                    final java.util.UUID savedUuid = fp.getUuid();
                    Bukkit.getScheduler().runTaskLater(plugin, () -> {
                        if (!savedBody.isOnline()) return;
                        try {
                            // Stamp firstPlayed/lastPlayed with real timestamps if still 0.
                            // This ensures the .dat file written below contains non-zero
                            // values, so on the NEXT spawn Paper loads hasPlayedBefore=true
                            // naturally - without FPP needing to override Bukkit-layer flags.
                            me.bill.fakePlayerPlugin.listener.PlayerJoinListener.stampFirstPlayed(savedBody);
                            savedBody.saveData();
                            FppLogger.debug("FakePlayerManager: initial playerdata saved for '"
                                    + savedName + "' uuid=" + savedUuid);
                        } catch (Exception e) {
                            FppLogger.warn("FakePlayerManager: initial saveData failed for '"
                                    + savedName + "' uuid=" + savedUuid
                                    + ": " + e.getMessage());
                        }
                    }, 2L);
                }
            } else if (fp.isBodyless()) {
                // Bodyless spawn: no physical body, tab-list only (console spawn without location)
                Config.debug("Bodyless spawn: skipping physical body for " + fp.getName());
            } else {
                // body.enabled = false - mark as intentionally bodyless so validateEntities()
                // and applyBodyConfig() don't mistakenly try to re-spawn this bot's body.
                fp.setBodyless(true);
                Config.debug("Body spawn skipped (body.enabled=false) for " + fp.getName());
            }

            // 2. Send tab list
            List<Player> online = new ArrayList<>(Bukkit.getOnlinePlayers());
            if (Config.tabListEnabled()) {
                boolean isNmsPlayer = fp.getPlayer() != null;
                Config.debug("Sending tab-list for '" + fp.getName() + "' display='" + fp.getDisplayName()
                        + "' packet='" + fp.getPacketProfileName() + "' nms=" + isNmsPlayer);

                if (isNmsPlayer) {
                    // NMS player: the server already sent a PlayerInfo ADD packet with the real
                    // name when placeNewPlayer() was called. Sending another ADD with a modified
                    // profile name would overwrite it and show the {_ prefix in command suggestions.
                    // Only update the display name shown in the tab list.
                    for (Player p : online) PacketHelper.sendTabListDisplayNameUpdate(p, fp);
                } else {
                    // Bodyless bot: no server-side PlayerInfo entry - send full ADD.
                    for (Player p : online) PacketHelper.sendTabListAdd(p, fp);
                    for (Player p : online) PacketHelper.sendTabListDisplayNameUpdate(p, fp);
                }

                // Re-send after 3 ticks and again after 20 ticks in case TAB plugin overwrites it
                Bukkit.getScheduler().runTaskLater(plugin, () -> {
                    if (!activePlayers.containsKey(fp.getUuid())) return;
                    boolean nms = fp.getPlayer() != null;
                    Config.debug("Re-sending tab-list (3t) for '" + fp.getName() + "' nms=" + nms);
                    if (!nms) for (Player p : Bukkit.getOnlinePlayers()) PacketHelper.sendTabListAdd(p, fp);
                    for (Player p : Bukkit.getOnlinePlayers()) PacketHelper.sendTabListDisplayNameUpdate(p, fp);
                }, 3L);

                Bukkit.getScheduler().runTaskLater(plugin, () -> {
                    if (!activePlayers.containsKey(fp.getUuid())) return;
                    boolean nms = fp.getPlayer() != null;
                    Config.debug("Re-sending tab-list (20t) for '" + fp.getName() + "' nms=" + nms);
                    if (!nms) for (Player p : Bukkit.getOnlinePlayers()) PacketHelper.sendTabListAdd(p, fp);
                    for (Player p : Bukkit.getOnlinePlayers()) PacketHelper.sendTabListDisplayNameUpdate(p, fp);

                    // Add bot to ~fpp scoreboard team AFTER tab packets are sent
                    if (botTabTeam != null) botTabTeam.addBot(fp);

                    // Broadcast full bot profile to all other servers in the proxy network
                    var vc = plugin.getVelocityChannel();
                    if (vc != null) vc.broadcastBotSpawn(fp);
                }, 20L);
            } else {
                Config.debug("Tab-list disabled - skipping tab add for '" + fp.getName() + "'");
            }

            // 4. Broadcast join message.
            // • Bodied bots: PlayerJoinEvent fires during FakePlayerBody.spawn(); the MONITOR
            //   handler in PlayerJoinListener sets event.joinMessage() to the custom bot-join
            //   component so Paper broadcasts it automatically - no extra call needed here.
            // • Bodyless bots: no PlayerJoinEvent fires, so we must broadcast manually.
            Bukkit.getScheduler().runTaskLater(plugin, () -> {
                if (!activePlayers.containsKey(fp.getUuid())) return; // removed mid-spawn
                if (fp.isBodyless()) {
                    BotBroadcast.broadcastJoin(fp);
                }
                // Forward to proxy network for cross-server join message.
                var vc = plugin.getVelocityChannel();
                if (vc != null) vc.broadcastJoinToNetwork(fp);
            }, 2L);

            if (persistence != null && Config.persistOnRestart()) {
                persistence.saveAsync(activePlayers.values());
            }

            // 5. LP prefix - apply group directly to the online User object LP loaded at
            // PlayerJoinEvent, then save. saveUser() on an online User fires
            // UserDataRecalculateEvent which our subscriber handles to refresh the display name.
            if (plugin.isLuckPermsAvailable()) {
                UUID botUuid = fp.getUuid();
                String group  = fp.getLuckpermsGroup() != null && !fp.getLuckpermsGroup().isBlank()
                        ? fp.getLuckpermsGroup()
                        : (!me.bill.fakePlayerPlugin.config.Config.luckpermsDefaultGroup().isBlank()
                                ? me.bill.fakePlayerPlugin.config.Config.luckpermsDefaultGroup()
                                : "default");

                // Wait 5 ticks for LP to finish loading the user at PlayerJoinEvent,
                // then apply the group to the live online User instance.
                Bukkit.getScheduler().runTaskLater(plugin, () -> {
                    if (!activePlayers.containsKey(botUuid)) return;
                    me.bill.fakePlayerPlugin.util.LuckPermsHelper
                            .applyGroupToOnlineUser(botUuid, group)
                            .thenRun(() -> Bukkit.getScheduler().runTaskLater(plugin, () -> {
                                if (!activePlayers.containsKey(botUuid)) return;
                                refreshLpDisplayName(fp);
                                // Schedule swap session after LP group is applied
                                if (botSwapAI != null && fp.getBotType() != BotType.PVP) {
                                    botSwapAI.schedule(fp);
                                }
                            }, 2L));
                }, 5L);
            } else {
                // No LP - schedule swap immediately after spawn completes
                if (botSwapAI != null && fp.getBotType() != BotType.PVP) {
                    Bukkit.getScheduler().runTaskLater(plugin, () -> {
                        if (!activePlayers.containsKey(fp.getUuid())) return;
                        botSwapAI.schedule(fp);
                    }, 10L);
                }
            }
        }, () -> {
            // onSkinApplied - called on the main thread after an async skin fetch completes
            // and Paper's setPlayerProfile() has been applied to the live entity.
            // Re-send display name packets so our custom format overwrites whatever
            // Paper's internal ADD_PLAYER packet set.
            if (!activePlayers.containsKey(fp.getUuid())) return;
            if (Config.tabListEnabled()) {
                for (Player p : Bukkit.getOnlinePlayers()) {
                    PacketHelper.sendTabListDisplayNameUpdate(p, fp);
                }
            }
        });
    }

    // ── Remove all ───────────────────────────────────────────────────────────

    /**
     * Restores a single fake player from persisted data (called on startup).
     * The original UUID and display name are reused so database records stay linked
     * and bots maintain their original appearance (prefix, format, etc.).
     *
     * <p><b>Design rule - bots are per-server only.</b>
     * The database may be shared (NETWORK mode), but this method must only be called
     * with rows that belong to THIS server (filtered by {@code server_id} before
     * reaching here). FakePlayer instances and NMS ServerPlayer entities
     * are never shared or teleported across servers - they exist only on the server
     * that originally spawned them.
     *
     */
    public void spawnRestored(String name, UUID uuid, String savedDisplayName,
                              String spawnedBy, UUID spawnedByUuid, Location location) {
        spawnRestored(name, uuid, savedDisplayName, spawnedBy, spawnedByUuid, location, BotType.AFK);
    }

    public void spawnRestored(String name, UUID uuid, String savedDisplayName,
                              String spawnedBy, UUID spawnedByUuid, Location location, BotType botType) {
        // Skip if name already active (e.g. duplicate in save file)
        if (usedNames.contains(name)) return;

        // Register this UUID in the identity cache so future fresh spawns of the same
        // name reuse it - even if the bot is later explicitly despawned and re-spawned.
        if (identityCache != null) identityCache.prime(name, uuid);

        // Auto-detect PVP type from MC name prefix if not explicitly provided
        if (botType == BotType.AFK && name.startsWith("pvp_")) botType = BotType.PVP;

        PlayerProfile profile = Bukkit.createProfile(uuid, name);
        FakePlayer fp = new FakePlayer(uuid, name, profile);
        fp.setBotType(botType);

        // User bots (ubot_*) have no Mojang skin - pick a random pool name for skin resolution.
        // Admin bots use their own name for skin lookup; PVP admin bots strip the pvp_ prefix.
        boolean isUserBot = name.startsWith("ubot_");
        if (isUserBot) {
            fp.setSkinName(pickRandomSkinName());
        } else if (botType == BotType.PVP && name.startsWith("pvp_")) {
            fp.setSkinName(name.substring(4)); // "pvp_Steve" → skin lookup as "Steve"
        } else {
            fp.setSkinName(name);
        }

        // Resolve display name from the current format strings.
        // LP will apply its own prefix/suffix natively once the player entity is online.
        String effectiveSpawner = (spawnedBy != null && !spawnedBy.isBlank()) ? spawnedBy : "Unknown";
        String displayName;

        if (isUserBot) {
            int lastUs = name.lastIndexOf('_');
            int botIdx = 1;
            if (lastUs > 0 && lastUs < name.length() - 1) {
                try { botIdx = Integer.parseInt(name.substring(lastUs + 1)); }
                catch (NumberFormatException ignored) { botIdx = 1; }
            }
            String rawName = Config.userBotNameFormat()
                    .replace("{spawner}", effectiveSpawner)
                    .replace("{num}",     String.valueOf(botIdx))
                    .replace("{bot_name}", name);
            // Re-apply pvp_ prefix on display name for restored PVP user bots
            if (botType == BotType.PVP && !rawName.startsWith("pvp_")) rawName = "pvp_" + rawName;
            Config.debug("[Restore] user-bot '" + name + "' type=" + botType + " spawner='" + effectiveSpawner + "' num=" + botIdx);
            fp.setRawDisplayName(rawName);
            displayName = finalizeDisplayName(rawName, name);
        } else {
            String rawName = Config.adminBotNameFormat().replace("{bot_name}", name);
            Config.debug("[Restore] admin-bot '" + name + "' type=" + botType);
            fp.setRawDisplayName(rawName);
            displayName = finalizeDisplayName(rawName, name);
        }

        fp.setDisplayName(displayName);
        fp.setSpawnLocation(location);
        fp.setSpawnedBy(effectiveSpawner, spawnedByUuid);
        usedNames.add(name);
        activePlayers.put(uuid, fp);
        nameIndex.put(name.toLowerCase(), fp);

        // Record to database
        if (db != null) {
            BotRecord record = new BotRecord(
                    0, name, uuid,
                    effectiveSpawner, spawnedByUuid,
                    location.getWorld() != null ? location.getWorld().getName() : "unknown",
                    location.getX(), location.getY(), location.getZ(),
                    location.getYaw(), location.getPitch(),
                    Instant.now(), null, null
            );
            fp.setDbRecord(record);
            String plainDisplay = net.kyori.adventure.text.serializer.plain.PlainTextComponentSerializer
                    .plainText().serialize(me.bill.fakePlayerPlugin.util.TextUtil.colorize(displayName));
            db.recordSpawn(record, plainDisplay);
        }

        // Visual spawn (no skin fetch on restore - keeps startup fast)
        finishSpawn(fp, location);
        Config.debug("Restored bot: " + name + " at " + location);
    }

    /**
     * Validates and repairs display names for all user bots owned by {@code spawnerUuid}.
     * Called when a spawner disconnects as a safety net to catch any bots that still
     * carry unresolved {@code {placeholder}} tokens in their display name.
     *
     * <p>This should never be needed if the spawn/restore paths are correct, but acts as
     * a defensive last-resort fix so players never see "bot-{spawner}-{num}" in game.
     *
     * @param spawnerUuid UUID of the disconnecting player
     * @param spawnerName display name of the disconnecting player
     */
    public void validateUserBotNames(UUID spawnerUuid, String spawnerName) {
        if (activePlayers.isEmpty()) return;
        List<Player> online = new ArrayList<>(Bukkit.getOnlinePlayers());
        for (FakePlayer fp : activePlayers.values()) {
            if (!spawnerUuid.equals(fp.getSpawnedByUuid())) continue;
            if (!fp.getName().startsWith("ubot_")) continue;

            String current = fp.getDisplayName();
            // Only act when there are still unresolved placeholders
            if (!PLACEHOLDER_PATTERN.matcher(current).find()) continue;

            // Reconstruct from current format (LP prefix applied natively by LP)
            String botName = fp.getName();
            int lastUs = botName.lastIndexOf('_');
            int idx = 1;
            if (lastUs > 0 && lastUs < botName.length() - 1) {
                try { idx = Integer.parseInt(botName.substring(lastUs + 1)); }
                catch (NumberFormatException ignored) { idx = 1; }
            }
            String rawDisplay = Config.userBotNameFormat()
                    .replace("{spawner}", spawnerName)
                    .replace("{num}",     String.valueOf(idx))
                    .replace("{bot_name}", fp.getName());
            fp.setRawDisplayName(rawDisplay);
            String newDisplay = finalizeDisplayName(rawDisplay, fp.getName());
            fp.setDisplayName(newDisplay);

            // Refresh tab-list entry
            if (Config.tabListEnabled()) {
                for (Player p : online) PacketHelper.sendTabListDisplayNameUpdate(p, fp);
            }
            FppLogger.warn("[FPP] Repaired placeholder name for bot '"
                    + fp.getName() + "' (owner: " + spawnerName + ") → '" + newDisplay + "'");
        }
    }

    public void removeAll() {
        if (activePlayers.isEmpty()) return;

        // Snapshot and clear registry immediately - prevents double-removal
        List<FakePlayer> toRemove = new ArrayList<>(activePlayers.values());
        activePlayers.clear();
        usedNames.clear();
        entityIdIndex.clear();
        // Clear PVP AI state for all bots being removed
        pvpAI.cancelAll();
        // Clear head AI rotation state to prevent memory leaks
        botHeadRotation.clear();
        botSpawnRotation.clear();

        // Calculate max possible delay for scheduling the final orphan sweep
        long maxDelay = 0;

        for (int i = 0; i < toRemove.size(); i++) {
            FakePlayer fp = toRemove.get(i);

            // Use a minimal per-bot stagger (1 tick each) so bots leave in a
            // natural sequence without being delayed by the leave-delay config.
            // The leave-delay config is reserved for simulated swap-AI departures.
            long leaveDelayTicks = (long) i; // 0 for first bot, 1 for second, etc.
            maxDelay = Math.max(maxDelay, leaveDelayTicks);

            final FakePlayer target = fp;
            Runnable doVisualRemove = () -> {
                // Mark as despawning so quit handlers (onQuitEarly/onQuit) suppress
                // the vanilla "X left the game" message - the MONITOR quit handler
                // builds and broadcasts the custom bot-leave message from en.yml.
                despawningBotIds.put(target.getUuid(), target.getDisplayName());
                try {
                    FakePlayerBody.removeAll(target);   // fires PlayerQuitEvent synchronously
                } finally {
                    despawningBotIds.remove(target.getUuid());
                }
                if (chunkLoader != null) chunkLoader.releaseForBot(target);

                // Remove from scoreboard team before removing tab entry
                if (botTabTeam != null) botTabTeam.removeBot(target);

                List<Player> snapshot = new ArrayList<>(Bukkit.getOnlinePlayers());
                for (Player online : snapshot) PacketHelper.sendTabListRemove(online, target);

                // Local leave message is broadcast by PlayerJoinListener.onQuit (MONITOR)
                // via event.quitMessage() - no manual broadcast needed here.
                // Forward to proxy network for cross-server leave message.
                if (Config.leaveMessage()) {
                    var vc = plugin.getVelocityChannel();
                    if (vc != null) vc.broadcastLeaveToNetwork(target.getDisplayName());
                }
                if (db != null) db.recordRemoval(target.getUuid(), "DELETED");
                // Notify other servers to remove this bot's virtual tab entry
                var vc2 = plugin.getVelocityChannel();
                if (vc2 != null) vc2.broadcastBotDespawn(target.getUuid());
                Config.debug("Removed bot: " + target.getName());
            };

            if (leaveDelayTicks <= 0L) {
                Bukkit.getScheduler().runTask(plugin, doVisualRemove);
            } else {
                Bukkit.getScheduler().runTaskLater(plugin, doVisualRemove, leaveDelayTicks);
            }
        }

        // Final persistence save after all bots are removed
        final long saveDelay = maxDelay + 20L;
        Bukkit.getScheduler().runTaskLater(plugin, () -> {
            if (persistence != null && Config.persistOnRestart()) {
                persistence.saveAsync(activePlayers.values());
            }
        }, saveDelay);

        Config.debug("Staggered visual removal of " + toRemove.size() + " fake player(s).");
    }

    // ── Delete one ───────────────────────────────────────────────────────────

    /**
     * Drops all inventory contents and XP from a bot entity onto the ground at its
     * current location, exactly as if the bot died naturally.  Called by {@link #delete}
     * when {@code body.drop-items-on-despawn} is {@code true}.
     */
    private static void dropBotContents(FakePlayer target) {
        Player bot = target.getPhysicsEntity();
        if (bot == null || !bot.isOnline()) return;

        Location loc = bot.getLocation();
        World world = loc.getWorld();
        if (world == null) return;

        // Drop every non-air item from all inventory slots (main, armour, offhand)
        for (ItemStack item : bot.getInventory().getContents()) {
            if (item != null && item.getType() != Material.AIR) {
                world.dropItemNaturally(loc, item);
            }
        }
        bot.getInventory().clear();

        // Drop XP as an experience orb
        int xp = bot.getTotalExperience();
        if (xp > 0) {
            world.spawn(loc, ExperienceOrb.class, orb -> orb.setExperience(xp));
            bot.setTotalExperience(0);
            bot.setLevel(0);
            bot.setExp(0f);
        }
    }

    /**
     * Deletes a single fake player by name - kills the physics body, removes
     * from tab list, despawns the visual, broadcasts leave message.
     *
     * @return true if a bot with that name was found and removed, false otherwise
     */
    public boolean delete(String name) {
        FakePlayer fp = getByName(name);
        if (fp == null) return false;

        final FakePlayer target   = fp;
        final String     botName  = target.getName();

        // Remove from registry immediately - prevents double-delete and clears tab-complete
        activePlayers.remove(target.getUuid());
        nameIndex.remove(botName.toLowerCase());
        usedNames.remove(botName);
        // Remove entity-id index entries now so no stale lookups remain
        if (target.getPhysicsEntity() != null)
            entityIdIndex.remove(target.getPhysicsEntity().getEntityId());
        // Clear PVP AI state for this bot
        pvpAI.stopBot(target.getUuid());
        // Cancel swap timer for this bot
        if (botSwapAI != null) botSwapAI.cancel(target.getUuid());
        // Clear head AI rotation state to prevent memory leaks
        botHeadRotation.remove(target.getUuid());
        botSpawnRotation.remove(target.getUuid());
        // Clear action lock so the tick loop doesn't hold a stale reference
        actionLockedBots.remove(target.getUuid());
        // Clear navigation lock
        navLockedBots.remove(target.getUuid());
        var pathfinding = plugin.getPathfindingService();
        if (pathfinding != null) pathfinding.cancel(target.getUuid());
        // Clear navigation state (prevents memory leak)
        var moveCmd = plugin.getMoveCommand();
        if (moveCmd != null) moveCmd.cleanupBot(target.getUuid());
        var mineCmd = plugin.getMineCommand();
        if (mineCmd != null) {
            mineCmd.cleanupBot(target.getUuid());
            mineCmd.clearSelection(target.getUuid());
        }
        var placeCmd = plugin.getPlaceCommand();
        if (placeCmd != null) placeCmd.cleanupBot(target.getUuid());
        var useCmd = plugin.getUseCommand();
        if (useCmd != null) useCmd.stopUsing(target.getUuid());


        // Defer body removal, tab-list, despawn and leave message by a hardcoded 1-tick
        // so the current tick finishes before cleanup runs. The leave-delay config is
        // intentionally NOT used here - it only applies to simulated swap-AI departures
        // (see BotSwapAI.doLeave). This ensures /fpp despawn and combat-death despawns
        // are always instant regardless of how large leave-delay is set.
        long leaveDelay = 1L;

        Runnable doVisualRemove = () -> {
            // Drop inventory + XP before body removal when configured to do so
            if (Config.dropItemsOnDespawn()) {
                dropBotContents(target);
            }
            // Mark as despawning so quit handlers (onQuitEarly/onQuit) suppress
            // the vanilla "X left the game" message - the MONITOR quit handler
            // builds and broadcasts the custom bot-leave message from en.yml.
            despawningBotIds.put(target.getUuid(), target.getDisplayName());
            try {
                // Primary removal via entity reference
                FakePlayerBody.removeAll(target);   // fires PlayerQuitEvent synchronously
            } finally {
                despawningBotIds.remove(target.getUuid());
            }
            if (chunkLoader != null) chunkLoader.releaseForBot(target);

            // Remove from scoreboard team before removing tab entry
            if (botTabTeam != null) botTabTeam.removeBot(target);

            List<Player> snapshot = new ArrayList<>(Bukkit.getOnlinePlayers());
            for (Player online : snapshot) PacketHelper.sendTabListRemove(online, target);

            // Local leave message is broadcast by PlayerJoinListener.onQuit (MONITOR)
            // via event.quitMessage() - no manual broadcast needed here.
            // Forward to proxy network for cross-server leave message.
            // Skip proxy broadcast during renames — a dedicated rename message is sent instead.
            if (Config.leaveMessage() && !renamingBotIds.contains(target.getUuid())) {
                var vc = plugin.getVelocityChannel();
                if (vc != null) vc.broadcastLeaveToNetwork(target.getDisplayName());
            }
            if (db != null) db.recordRemoval(target.getUuid(), "DELETED");
            // Clean up LuckPerms user data for this bot
            if (plugin.isLuckPermsAvailable()) {
                me.bill.fakePlayerPlugin.util.LuckPermsHelper.setPlayerGroup(target.getUuid(), "default")
                    .thenRun(() -> Config.debug("Cleaned up LP data for bot: " + botName))
                    .exceptionally(throwable -> {
                        me.bill.fakePlayerPlugin.util.FppLogger.warn("Failed to cleanup LP data for bot " + botName + ": " + throwable.getMessage());
                        return null;
                    });
            }
            // Notify other servers to remove this bot's virtual tab entry
            var vc2 = plugin.getVelocityChannel();
            if (vc2 != null) vc2.broadcastBotDespawn(target.getUuid());
            Config.debug("Deleted fake player: " + botName);
            if (persistence != null && Config.persistOnRestart()) {
                persistence.saveAsync(activePlayers.values());
            }
        };

        Bukkit.getScheduler().runTaskLater(plugin, doVisualRemove, leaveDelay);

        return true;
    }

    /**
     * Synchronous removal of all bots - called exclusively from {@code onDisable}
     * when the Bukkit scheduler is already stopped.
     *
     * <h3>Shutdown optimisations</h3>
     * <ul>
     *   <li><b>No sleep</b> - leave-delay stagger is meaningless during shutdown;
     *       no real clients are present to observe gradual departures.</li>
     *   <li><b>No per-bot {@code db.recordRemoval()} enqueues</b> -
     *       {@code DatabaseManager.recordAllShutdown()} closes all sessions with a
     *       single bulk SQL UPDATE called immediately after this method returns.
     *       Skipping the per-bot enqueues keeps the write queue empty so
     *       {@code db.close()} drains in milliseconds instead of seconds.</li>
     *   <li><b>{@code fpp_active_bots} rows are left intact</b> - they serve as the
     *       primary DB restore source on the next startup, avoiding YAML fallback.</li>
     * </ul>
     */
    public void removeAllSync() {
        if (activePlayers.isEmpty()) return;

        List<FakePlayer> toRemove = new ArrayList<>(activePlayers.values());
        activePlayers.clear();
        nameIndex.clear();
        usedNames.clear();
        entityIdIndex.clear();
        pvpAI.cancelAll();
        // Clear head AI rotation state to prevent memory leaks
        botHeadRotation.clear();
        botSpawnRotation.clear();

        List<Player> snapshot = new ArrayList<>(Bukkit.getOnlinePlayers());

        for (FakePlayer fp : toRemove) {
            // 1. Remove NMS entity and release chunk tickets
            FakePlayerBody.removeAll(fp);
            if (chunkLoader != null) chunkLoader.releaseForBot(fp);

            // 2. Tab-list cleanup for any still-connected players
            if (botTabTeam != null) botTabTeam.removeBot(fp);
            for (Player online : snapshot) PacketHelper.sendTabListRemove(online, fp);

            // DB session closure is handled by DatabaseManager.recordAllShutdown()
            // called by FakePlayerPlugin.onDisable() immediately after this method.
            // fpp_active_bots rows are left intact for DB-primary restore on next startup.
            Config.debug("Shutdown removed bot: " + fp.getName());
        }

        FppLogger.info("Shutdown: removed " + toRemove.size() + " bot(s).");
    }

    /**
     * Called after {@code body.enabled}, {@code body.damageable}, {@code body.pushable},
     * or {@code combat.max-health} changes (live from the settings GUI or {@code /fpp reload}).
     *
     * <ul>
     *   <li><b>body.enabled → true</b>: spawns a new NMS body for every intentionally-bodyless
     *       bot and re-adds their tab-list entry.</li>
     *   <li><b>body.enabled → false</b>: removes the NMS body via the proper
     *       {@code PlayerList.remove()} path (suppresses vanilla quit message), marks the
     *       bot as bodyless, then re-adds the tab-list entry so the bot remains visible.</li>
     *   <li><b>other keys</b>: updates runtime flags (invulnerable, collidable, max-health)
     *       on existing bodies only.</li>
     * </ul>
     */
    public void applyBodyConfig() {
        List<Player> online = new ArrayList<>(Bukkit.getOnlinePlayers());

        if (physicalBodiesEnabled()) {
            // ── Bodies enabled ─────────────────────────────────────────────────
            for (FakePlayer fp : new ArrayList<>(activePlayers.values())) {
                Player body = fp.getPlayer();

                if (body != null && body.isValid()) {
                    // Already has a body - just refresh runtime flags
                    // CRITICAL: Always set invulnerable to FALSE - damage type filtering
                    // is handled via event cancellation in FakePlayerEntityListener.
                    // This ensures environmental damage (fall, fire, etc.) always applies.
                    body.setInvulnerable(false);
                    body.setCollidable(Config.bodyPushable());
                    try {
                        var attr = body.getAttribute(org.bukkit.attribute.Attribute.MAX_HEALTH);
                        if (attr != null) {
                            double hp = Config.maxHealth();
                            attr.setBaseValue(hp);
                            if (body.getHealth() > hp) body.setHealth(hp);
                        }
                    } catch (Exception ignored) {}

                } else if (fp.isBodyless()) {
                    // Intentionally bodyless - spawn a body now
                    Location loc = fp.getSpawnLocation();
                    if (loc == null || loc.getWorld() == null) continue;

                    bodyTransitionBots.add(fp.getUuid());
                    fp.setBodyless(false);
                    try {
                        Player newBody = FakePlayerBody.spawn(fp, loc); // fires PlayerJoinEvent (suppressed)
                        if (newBody != null) {
                            fp.setPhysicsEntity(newBody);
                            entityIdIndex.put(newBody.getEntityId(), fp);
                            fp.setPacketProfileName(fp.getName());
                            // Remove the manual ghost tab entry - the NMS join packet already
                            // sent an ADD for the real entity. Re-add to force display-name sync.
                            if (Config.tabListEnabled()) {
                                for (Player p : online) {
                                    PacketHelper.sendTabListRemove(p, fp);
                                    PacketHelper.sendTabListAdd(p, fp);
                                }
                            }
                            // Stamp firstPlayed so next saveData() writes real timestamps
                            me.bill.fakePlayerPlugin.listener.PlayerJoinListener.stampFirstPlayed(newBody);
                            FppLogger.info("BodyConfig: body shown for '" + fp.getName() + "'");
                        } else {
                            fp.setBodyless(true); // revert - spawn failed
                            FppLogger.warn("BodyConfig: failed to show body for '" + fp.getName() + "'");
                        }
                    } finally {
                        bodyTransitionBots.remove(fp.getUuid());
                    }
                }
            }
            // Refresh scoreboard team collision rule
            var btt = plugin.getBotTabTeam();
            if (btt != null) btt.applyCollisionRule(Config.bodyPushable());
            return;
        }

        // ── Bodies disabled - remove NMS entities, keep bots tab-list-only ────
        for (FakePlayer fp : new ArrayList<>(activePlayers.values())) {
            Player body = fp.getPlayer();
            if (body == null || !body.isOnline()) continue;

            // Save the live position so the bot can be restored at the right spot
            fp.setSpawnLocation(body.getLocation());
            int entityId = body.getEntityId();

            // Clear FakePlayer references BEFORE removal so event handlers during
            // PlayerQuitEvent don't find a stale body reference.
            entityIdIndex.remove(entityId);
            fp.setPhysicsEntity(null);
            fp.setBodyless(true);

            // Suppress the vanilla quit message - the bot is NOT leaving FPP management
            bodyTransitionBots.add(fp.getUuid());
            try {
                NmsPlayerSpawner.removeFakePlayer(body); // fires PlayerQuitEvent (suppressed)
            } finally {
                bodyTransitionBots.remove(fp.getUuid());
            }

            // PlayerQuitEvent cleared the client tab-list entry - re-add as a ghost entry
            // so the bot remains visible in the tab list despite having no physical body.
            if (Config.tabListEnabled()) {
                for (Player p : online) PacketHelper.sendTabListAdd(p, fp);
            }
            FppLogger.info("BodyConfig: body hidden for '" + fp.getName() + "', now tab-list only");
        }
    }

    // ── Remove by name (post-death cleanup) ──────────────────────────────────

    /** Removes a fake player by name (called after permanent death). */
    public void removeByName(String name) {
        activePlayers.values().removeIf(fp -> {
            if (!fp.getName().equals(name)) return false;
            nameIndex.remove(fp.getName().toLowerCase());
            usedNames.remove(fp.getName());
            botHeadRotation.remove(fp.getUuid());
            botSpawnRotation.remove(fp.getUuid());
            actionLockedBots.remove(fp.getUuid());
            navLockedBots.remove(fp.getUuid());
            pvpAI.stopBot(fp.getUuid());
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
     * <p>The NMS ServerPlayer body is a real server-side entity
     * kept alive by chunk tickets - no respawn needed. Only the packet-based
     * tab-list entries must be re-sent (Minecraft clears them on world transitions).
     * 
     * <p><b>Important:</b> For NMS players (bots with physical bodies), we only send
     * UPDATE_DISPLAY_NAME because the vanilla server already sent a PlayerInfo ADD
     * packet for them when the joining player connected. Sending another ADD would
     * create duplicate tab list entries.
     */
    public void syncToPlayer(Player player) {
        if (!Config.tabListEnabled()) return;
        for (FakePlayer fp : activePlayers.values()) {
            // Check if this bot has an NMS ServerPlayer body
            boolean isNmsPlayer = fp.getPlayer() != null;
            
            if (isNmsPlayer) {
                // NMS player: server already sent ADD packet, only update display name
                PacketHelper.sendTabListDisplayNameUpdate(player, fp);
            } else {
                // Bodyless bot: no server-side entity, must send full ADD packet
                PacketHelper.sendTabListAdd(player, fp);
            }
        }
    }

    /**
     * Called after {@code /fpp reload} to apply the {@code tab-list.enabled} toggle immediately.
     * <ul>
     *   <li>If {@code enabled} is now {@code true}  - re-adds all active bots to every
     *       online player's tab list so they appear instantly without needing to respawn.</li>
     *   <li>If {@code enabled} is now {@code false} - removes all active bots from every
     *       online player's tab list.</li>
     * </ul>
     */
    public void applyTabListConfig() {
        List<Player> online = new ArrayList<>(Bukkit.getOnlinePlayers());
        if (online.isEmpty() || activePlayers.isEmpty()) return;

        if (Config.tabListEnabled()) {
            for (FakePlayer fp : activePlayers.values()) {
                for (Player p : online) {
                    // Remove first to clear any stale entry with wrong profile name,
                    // then re-add with the current packet profile name (sort prefix).
                    PacketHelper.sendTabListRemove(p, fp);
                    PacketHelper.sendTabListAdd(p, fp);
                }
            }
            // Rebuild team membership so bots are in the correct section
            if (botTabTeam != null) botTabTeam.rebuild(activePlayers.values());
            Config.debug("applyTabListConfig: re-added " + activePlayers.size() + " bots to tab list.");
        } else {
            for (FakePlayer fp : activePlayers.values()) {
                for (Player p : online) {
                    PacketHelper.sendTabListRemove(p, fp);
                }
            }
            // Clear team entries - hidden bots should not influence tab sections
            if (botTabTeam != null) botTabTeam.clearAll();
            Config.debug("applyTabListConfig: removed " + activePlayers.size() + " bots from tab list.");
        }
    }

    /**
     * Performs a periodic validation pass across all active bots:
     * <ol>
     *   <li>Checks that each bot's physics entity is still valid.</li>
     *   <li>If the body has become invalid (e.g., NMS ServerPlayer was removed
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

            // Bodies disabled: remove any that still exist and skip all respawn logic.
            if (!physicalBodiesEnabled()) {
                if (body != null && body.isValid()) {
                    try { body.remove(); } catch (Exception ignored) {}
                    entityIdIndex.remove(body.getEntityId());
                    fp.setPhysicsEntity(null);
                }
                continue;
            }

            // Bot is intentionally bodyless (body.enabled was false when it spawned,
            // or body was hidden via the settings GUI) - skip respawn logic.
            if (fp.isBodyless()) continue;

            // Body is valid - nothing to do
            if (body != null && body.isValid()) continue;

            // Body became invalid (DYING pose removed by Minecraft, or other cause)
            Config.debug("validateEntities: body of '" + fp.getName() + "' invalid - attempting respawn.");

            fp.setPhysicsEntity(null);

            // Try to re-spawn at last known location
            org.bukkit.Location loc = fp.getSpawnLocation();
            if (loc == null || loc.getWorld() == null) continue;

            Player newBody = FakePlayerBody.spawn(fp, loc);
            if (newBody == null) continue;

            fp.setPhysicsEntity(newBody);
            entityIdIndex.put(newBody.getEntityId(), fp);

            // Re-send tab list so skin + name update for everyone
            final FakePlayer target = fp;
            Bukkit.getScheduler().runTaskLater(plugin, () -> {
                for (Player p : Bukkit.getOnlinePlayers()) PacketHelper.sendTabListAdd(p, target);
            }, 2L);
        }
    }

    // ── Queries ──────────────────────────────────────────────────────────────

    /** Returns the FakePlayer whose physics body has the given entity id, or null. O(1). */
    public FakePlayer getByEntity(Entity entity) {
        // Fast path: entity-id index (O(1))
        FakePlayer fp = entityIdIndex.get(entity.getEntityId());
        if (fp != null) return fp;

        // Fallback: PDC bot-name lookup - handles the case where an entity crossed
        // worlds via a portal and Paper recreated it with a new entity-id and a new
        // Java object.  PDC data is copied during recreation so the bot-name tag
        // survives the transition.  We heal the index and the physicsEntity reference
        // so all subsequent lookups (damage, death cleanup, etc.) work normally again.
        if (FAKE_PLAYER_KEY == null) return null;
        String botName = entity.getPersistentDataContainer()
                .get(FAKE_PLAYER_KEY, org.bukkit.persistence.PersistentDataType.STRING);
        if (botName == null || botName.isBlank()) return null;

        FakePlayer candidate = getByName(botName);
        if (candidate == null) return null;

        // Remove the now-stale old entity-id entry before registering the new one
        Entity oldBody = candidate.getPhysicsEntity();
        if (oldBody != null && oldBody.getEntityId() != entity.getEntityId()) {
            entityIdIndex.remove(oldBody.getEntityId());
        }

        // Update the entity reference and re-register with the new id
        if (entity instanceof org.bukkit.entity.Player player) {
            candidate.setPhysicsEntity(player);
            entityIdIndex.put(entity.getEntityId(), candidate);
            Config.debug("getByEntity: recovered '" + botName
                    + "' via PDC after world-change - new entityId=" + entity.getEntityId());
            return candidate;
        }
        
        Config.debug("getByEntity: entity is not a Player, cannot recover bot: " + botName);
        return null;
    }

    /**
     * Returns the active {@link FakePlayer} with the given UUID, or {@code null}
     * if no bot with that UUID is currently registered.
     * O(1) - delegates directly to the UUID-keyed {@link #activePlayers} map.
     */
    public FakePlayer getByUuid(UUID uuid) {
        if (uuid == null) return null;
        return activePlayers.get(uuid);
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
     * Returns {@code true} if the given bot name is currently in use.
     * Used by swap system to check name availability on rejoin.
     */
    public boolean isNameUsed(String name) {
        return usedNames.contains(name);
    }

    /**
     * Snapshot of all currently active bot UUIDs (unmodifiable).
     * Used by swap system to identify newly spawned bots.
     */
    public Set<UUID> getActiveUUIDs() {
        return Collections.unmodifiableSet(new HashSet<>(activePlayers.keySet()));
    }

    /**
     * Returns the active {@link FakePlayer} with the given internal name (case-insensitive),
     * or {@code null} if no matching bot is active.
     * O(1) - delegates to the lowercase {@link #nameIndex} map.
     */
    public FakePlayer getByName(String name) {
        if (name == null || name.isBlank()) return null;
        return nameIndex.get(name.toLowerCase());
    }

    /**
     * Renames a bot's display name (internal name remains unchanged since it's final).
     * Updates tab-list packets to reflect the new name.
     *
     * @param bot     the bot to rename
     * @param newName the new display name
     */
    public void renameBot(FakePlayer bot, String newName) {
        String oldDisplay = bot.getDisplayName();
        if (oldDisplay.equalsIgnoreCase(newName)) return;  // no-op

        // Update display name and raw display name
        bot.setDisplayName(newName);
        bot.setRawDisplayName(newName);

        // Update tab-list for all players
        if (Config.tabListEnabled() && bot.getPlayer() != null) {
            for (Player p : Bukkit.getOnlinePlayers()) {
                PacketHelper.sendTabListRemove(p, bot);
                PacketHelper.sendTabListAdd(p, bot);
            }
        }
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
     * Requests that {@code botUuid}'s bot jump on the next physics tick.
     *
     * <p>The jump is held for 5 ticks so the swim-AI reset (which runs before
     * {@code tickPhysics} every tick) cannot cancel it before
     * {@code LivingEntity.travel()} fires {@code jumpFromGround()}.
     * Safe to call every tick - the counter is simply refreshed.
     *
     * <p>Used by the navigation system ({@code MoveCommand}) to jump over blocks
     * without fighting the swim-AI's unconditional {@code setJumping(false)} reset.
     */
    public void requestNavJump(UUID botUuid) {
        navJumpHolding.put(botUuid, 5);
    }

    /** Clears any pending navigation jump for this bot (called on despawn). */
    public void clearNavJump(UUID botUuid) {
        navJumpHolding.remove(botUuid);
    }

    // ── Mining lock ───────────────────────────────────────────────────────────

    /**
     * Locks a bot to the given location + rotation for mining mode.
     * <ul>
     *   <li>Head-AI is suppressed for this bot - it will not rotate to look at players.</li>
     *   <li>The physics tick enforces the locked position every tick so the bot stays on its spot.</li>
     * </ul>
     * Call {@link #unlockAction(UUID)} when the action stops.
     *
     * @param botUuid the bot to lock
     * @param loc     the exact position + facing direction to lock to (yaw + pitch included)
     */
    public void lockForAction(UUID botUuid, Location loc) {
        actionLockedBots.put(botUuid, loc.clone());
        // Initialise head rotation state to the locked direction so the head-AI
        // position snapshot never overwrites the locked rotation.
        botHeadRotation.put(botUuid, new float[]{loc.getYaw(), loc.getPitch()});
        botSpawnRotation.put(botUuid, new float[]{loc.getYaw(), loc.getPitch()});
    }

    /**
     * Removes the action-position lock for this bot, re-enabling head-AI.
     *
     * @param botUuid the bot to unlock
     */
    public void unlockAction(UUID botUuid) {
        actionLockedBots.remove(botUuid);
    }

    /**
     * Returns {@code true} if the bot is currently action-locked
     * (head-AI disabled, position enforced every tick).
     */
    public boolean isActionLocked(UUID botUuid) {
        return actionLockedBots.containsKey(botUuid);
    }

    /**
     * Locks a bot for navigation (disables head-AI so it doesn't look at players
     * while MoveCommand sets the rotation every tick).
     *
     * @param botUuid the bot to lock
     */
    public void lockForNavigation(UUID botUuid) {
        navLockedBots.add(botUuid);
    }

    /**
     * Removes the navigation lock for this bot, re-enabling head-AI.
     *
     * @param botUuid the bot to unlock
     */
    public void unlockNavigation(UUID botUuid) {
        navLockedBots.remove(botUuid);
    }

    /**
     * Returns {@code true} if the bot is currently navigation-locked
     * (head-AI disabled; MoveCommand manages its rotation).
     */
    public boolean isNavigationLocked(UUID botUuid) {
        return navLockedBots.contains(botUuid);
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
    
    /** Returns the PVP AI instance for defensive mode tracking. */
    public BotPvpAI getPvpAI() {
        return pvpAI;
    }

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
    /**
     * Moves the physics body of {@code fp} to {@code destination} immediately.
     * Used by the tph / tp commands.
     */
    public boolean teleportBot(FakePlayer fp, org.bukkit.Location destination) {
        Player body = fp.getPlayer();
        if (body == null || !body.isValid()) return false;
        body.teleport(destination);
        fp.setSpawnLocation(destination.clone());
        return true;
    }

    // ── Display name ──────────────────────────────────────────────────────────

    /**
     * Builds the final display name for a bot.
     *
     * <p>Bots are now real NMS ServerPlayer entities - LuckPerms applies their
     * prefix and suffix natively in chat and the tab list. This method
     * sanitizes and PAPI-expands the raw name to produce the FPP-side custom
     * name shown via the name packet.
     *
     * @param rawName base name resolved from admin/user format
     * @param botName internal bot name used for sanitize-log context
     */
    private String finalizeDisplayName(String rawName, String botName) {
        String display = rawName;

        // Expand PlaceholderAPI placeholders (server-wide, null player context)
        if (display.contains("%")) {
            try {
                if (org.bukkit.Bukkit.getPluginManager().getPlugin("PlaceholderAPI") != null) {
                    display = me.clip.placeholderapi.PlaceholderAPI.setPlaceholders(null, display);
                }
            } catch (Exception ignored) {}
        }

        return sanitizeDisplayName(display, botName);
    }

    /**
     * Re-reads the bot's LuckPerms prefix and suffix from LP's in-memory meta cache
     * and rebuilds its display name.
     *
     * <p>Call this after a LP group assignment completes so the prefix/suffix appear
     * correctly in the tab list and nametag. Must be called on the main server thread.
     *
     * <p>No-op when LP is unavailable or the bot is no longer active.
     *
     * @param fp the bot whose display name should be refreshed
     */
    public void refreshLpDisplayName(FakePlayer fp) {
        if (!activePlayers.containsKey(fp.getUuid())) return;
        if (!plugin.isLuckPermsAvailable()) return;


        // Use the raw display content stored at spawn/restore time
        String rawContent = fp.getRawDisplayName();
        if (rawContent == null || rawContent.isBlank()) rawContent = fp.getName();

        String display = rawContent;

        // PAPI expansion
        if (display.contains("%")) {
            try {
                if (org.bukkit.Bukkit.getPluginManager().getPlugin("PlaceholderAPI") != null) {
                    display = me.clip.placeholderapi.PlaceholderAPI.setPlaceholders(null, display);
                }
            } catch (Exception ignored) {}
        }

        display = sanitizeDisplayName(display, fp.getName());
        // Guard (Trigger 3): if LP prefix/suffix contain only colour tags and no visible text,
        // the MiniMessage-stripped display may be blank, which causes the vanilla client to show
        // "Anonymous Player". Fall back to the raw bot name to guarantee a visible tab entry.
        if (display == null || display.isBlank()) {
            display = fp.getName();
            Config.debug("[LP] Display name was blank after sanitise for '"
                    + fp.getName() + "' — falling back to raw bot name.");
        }
        fp.setDisplayName(display);

        // Update the entity's display name for death messages and chat.
        // Use plain colorize (not colorizeOrYellow) so the entity display name stays
        // neutral — LP applies prefix/suffix in chat natively. Yellow is only added by
        // BotBroadcast for join/leave messages via its own parseDisplayName helper.
        Player body = fp.getPlayer();
        if (body != null && body.isValid()) {
            try {
                body.displayName(me.bill.fakePlayerPlugin.util.TextUtil.colorize(rawContent));
            } catch (Exception ignored) {}
        }

        // Resend tab-list display name to all online players
        if (Config.tabListEnabled()) {
            List<Player> online = new ArrayList<>(Bukkit.getOnlinePlayers());
            for (Player p : online) PacketHelper.sendTabListDisplayNameUpdate(p, fp);
        }

        // Notify remote servers so their RemoteBotCache and tab-list entries update too
        if (Config.isNetworkMode()) {
            var vc = plugin.getVelocityChannel();
            if (vc != null) vc.broadcastBotDisplayNameUpdate(fp);
        }

        Config.debug("[LP] Refreshed display name for '" + fp.getName() + "': '" + display + "'");
    }

    /**
     * Refreshes LP display name with retry logic. If the prefix is still empty after the
     * first attempt, forces LP to refresh cached metadata and retries after a delay.
     * This handles the race condition where LP hasn't finished calculating metadata yet.
     *
     * @param fp the bot whose display name should be refreshed
     * @param attempt current retry attempt (0-indexed, max 2 retries)
     */
    private void refreshLpDisplayNameWithRetry(FakePlayer fp, int attempt) {
        if (!activePlayers.containsKey(fp.getUuid())) return;
        if (!plugin.isLuckPermsAvailable()) return;

        String prefix = me.bill.fakePlayerPlugin.util.LuckPermsHelper.getResolvedPrefix(fp.getUuid());
        String suffix = me.bill.fakePlayerPlugin.util.LuckPermsHelper.getResolvedSuffix(fp.getUuid());

        // If we still have no prefix/suffix after initial delay and haven't exceeded retry limit,
        // force LP to refresh cached data and retry
        if (prefix.isEmpty() && suffix.isEmpty() && attempt < 2) {
            Config.debug("[LP] No prefix/suffix for '" + fp.getName() + "' on attempt " + (attempt + 1) 
                    + ", forcing LP refresh...");
            me.bill.fakePlayerPlugin.util.LuckPermsHelper.refreshUserCache(fp.getUuid());
            
            UUID botUuid = fp.getUuid();
            Bukkit.getScheduler().runTaskLater(plugin, () -> {
                if (!activePlayers.containsKey(botUuid)) return;
                refreshLpDisplayNameWithRetry(fp, attempt + 1);
            }, 10L); // Wait 10 more ticks after forcing refresh
            return;
        }

        // Apply the display name (even if prefix/suffix are empty on final attempt)
        refreshLpDisplayName(fp);
    }

    /**
     * Compiled pattern that matches unreplaced {@code {placeholder}} tokens in
     * display-name strings. Excludes LuckPerms gradient shorthand
     * ({@code {#rrggbb>}text{#rrggbb<}}) by requiring the first char to be a
     * letter or underscore (not {@code #}).
     */
    private static final java.util.regex.Pattern PLACEHOLDER_PATTERN =
            java.util.regex.Pattern.compile("\\{[a-zA-Z_][a-zA-Z0-9_]*\\}");

    /**
     * Replaces any unreplaced {@code {placeholder}} patterns with a fallback name.
     */
    private String sanitizeDisplayName(String displayName, String context) {
        if (displayName == null || !displayName.contains("{")) {
            // Still guard against blank even if no placeholders were found.
            return (displayName == null || displayName.isBlank()) ? context : displayName;
        }
        java.util.regex.Matcher m = PLACEHOLDER_PATTERN.matcher(displayName);
        if (!m.find()) {
            return displayName.isBlank() ? context : displayName;
        }
        String fallback  = pickRandomSkinName();
        String sanitized = PLACEHOLDER_PATTERN.matcher(displayName).replaceAll(fallback);
        FppLogger.warn("Unreplaced placeholder(s) in display name for '"
                + context + "': '" + displayName + "' - replaced with '" + fallback
                + "'. Check bot-name.user-format / bot-name.admin-format in config.yml.");
        // Final guard: if stripping somehow produces a blank string, fall back to the bot name.
        return sanitized.isBlank() ? context : sanitized;
    }

    // ── Name generation ──────────────────────────────────────────────────────

    /**
     * Picks a random name from the bot-names pool for skin lookup.
     */
    private String pickRandomSkinName() {
        List<String> pool = Config.namePool();
        if (pool.isEmpty()) return "Steve";
        return pool.get(ThreadLocalRandom.current().nextInt(pool.size()));
    }

    // ── UUID resolution ───────────────────────────────────────────────────────

    /**
     * Returns the stable UUID for {@code botName} on this server.
     *
     * <p>Delegates to {@link BotIdentityCache#lookupOrCreate(String)} when the cache
     * is available; falls back to {@link UUID#randomUUID()} when the cache has not yet
     * been injected (should not happen in normal usage, but guards against ordering issues
     * during tests or incomplete initialisation).
     *
     * <p>This replaces every bare {@code UUID.randomUUID()} call at spawn sites so bots
     * always reconnect with the same UUID they had on previous sessions.
     */
    private UUID resolveUuid(String botName) {
        if (identityCache != null) return identityCache.lookupOrCreate(botName);
        return UUID.randomUUID();
    }

    private String generateName() {
        // Use ONLY the pre-filtered clean pool — never fall back to the raw name list here.
        // If the clean pool is empty, generate a generic Bot#### fallback instead of re-running
        // expensive badword checks or accidentally using a filtered-out pool entry.
        List<String> pool = cleanNamePool;
        if (pool.isEmpty()) return fallbackName();

        String chosen = null;
        int    count  = 0;
        for (String n : pool) {
            // Basic validity (already guaranteed by cleanNamePool, but kept as a cheap guard)
            if (n == null || n.isEmpty() || n.length() > 16
                    || !n.matches("[a-zA-Z0-9_]+")) continue;
            if (usedNames.contains(n) || Bukkit.getPlayerExact(n) != null) continue;
            count++;
            if (ThreadLocalRandom.current().nextInt(count) == 0) chosen = n;
        }
        if (chosen != null) {
            usedNames.add(chosen);
            return chosen;
        }
        return fallbackName();
    }

    /**
     * Generates a PvP-bot MC username from the name pool, prefixed with {@code pvp_}.
     * The prefix occupies 4 characters, so pool names longer than 12 chars are
     * truncated to ensure the result is a valid Minecraft name (≤ 16 chars).
     * Falls back to {@code pvp_Bot<random>} if the pool is exhausted.
     */
    private String generatePvpName() {
        // Same rule as generateName(): only use the pre-filtered clean pool.
        // If it is empty, fall straight back to pvp_Bot####.
        List<String> pool = cleanNamePool;
        if (!pool.isEmpty()) {
            String chosen = null;
            int count = 0;
            for (String n : pool) {
                if (n == null || n.isEmpty() || !n.matches("[a-zA-Z0-9_]+")) continue;
                // Truncate base name to 12 chars so pvp_ prefix fits in 16
                String base = n.length() > 12 ? n.substring(0, 12) : n;
                // Pool is pre-filtered against badwords — no need to re-check here.
                String candidate = "pvp_" + base;
                if (usedNames.contains(candidate) || Bukkit.getPlayerExact(candidate) != null) continue;
                count++;
                if (ThreadLocalRandom.current().nextInt(count) == 0) chosen = candidate;
            }
            if (chosen != null) {
                usedNames.add(chosen);
                return chosen;
            }
        }
        // Fallback: pvp_Bot<1000-9998>
        String generated;
        int attempts = 0;
        do {
            generated = "pvp_Bot" + ThreadLocalRandom.current().nextInt(1000, 9999);
            if (generated.length() > 16) generated = generated.substring(0, 16);
            if (++attempts > 200) return null;
        } while (usedNames.contains(generated) || Bukkit.getPlayerExact(generated) != null);
        usedNames.add(generated);
        return generated;
    }

    private String fallbackName() {
        String generated;
        int attempts = 0;
        do {
            generated = "Bot" + ThreadLocalRandom.current().nextInt(1000, 9999);
            if (++attempts > 200) return null;
        } while (usedNames.contains(generated) || Bukkit.getPlayerExact(generated) != null);
        usedNames.add(generated);
        return generated;
    }

    // ── User-bot naming ───────────────────────────────────────────────────────

    /**
     * Result holder for a generated user-bot name pair.
     *
     * @param internalName valid Minecraft identifier used for the GameProfile
     * @param displayName  display text shown in nametag and tab list
     */
    public record UserBotName(String internalName, String displayName) {}

    /**
     * Generates a valid internal Minecraft name and a display name for a user-tier bot.
     *
     * <p>Internal name format: {@code ubot_<spawner>_<N>} (max 16 chars).
     *
     * @param spawnerName   the spawning player's Minecraft name
     * @param existingCount bots this player already owns
     */
    public UserBotName generateUserBotName(String spawnerName, int existingCount) {
        String suffix    = String.valueOf(existingCount + 1);
        final String PREFIX = "ubot_";
        final String SEP    = "_";
        int maxSpawnerLen = 16 - PREFIX.length() - SEP.length() - suffix.length();
        String truncated  = spawnerName.length() > maxSpawnerLen
                ? spawnerName.substring(0, Math.max(1, maxSpawnerLen))
                : spawnerName;
        String internal   = PREFIX + truncated + SEP + suffix;
        if (internal.length() > 16) internal = internal.substring(0, 16);
        usedNames.add(internal);

        String display = sanitizeDisplayName(
                Config.userBotNameFormat()
                        .replace("{spawner}", spawnerName)
                        .replace("{num}",     suffix)
                        .replace("{bot_name}", internal),
                internal);
        return new UserBotName(internal, display);
    }

    /**
     * Legacy compatibility method for updating bot prefixes.
     * Since bots are now real NMS ServerPlayer entities, LuckPerms applies
     * prefixes/suffixes natively without manual intervention.
     */
    public void updateAllBotPrefixes() {
        // No-op - LP handles prefix updates natively for real players
        Config.debug("updateAllBotPrefixes: skipped (bots are real players, LP handles natively)");
    }

    // ── Head-AI helpers ───────────────────────────────────────────────────────

    /**
     * Like {@link Player#hasLineOfSight} but treats glass and other visually
     * transparent blocks as if they were air.  Non-solid blocks (flowers,
     * torches, etc.) are always ignored.  Falls back to the vanilla method on
     * any unexpected error.
     *
     * <p>Covered by {@link #isGlassLike}: any {@link Material} whose name
     * contains {@code "GLASS"} (plain glass, stained glass, glass panes,
     * tinted glass, etc.).
     */
    private static boolean hasLineOfSightIgnoringGlass(Player from, Player to) {
        Location start = from.getEyeLocation();
        Location end   = to.getEyeLocation();
        if (start.getWorld() == null || !start.getWorld().equals(end.getWorld())) return false;

        org.bukkit.util.Vector dir = end.toVector().subtract(start.toVector());
        double distance = dir.length();
        if (distance < 1e-6) return true;
        dir.normalize();

        try {
            BlockIterator iter = new BlockIterator(
                    start.getWorld(), start.toVector(), dir, 0.0, (int) Math.ceil(distance) + 1);
            while (iter.hasNext()) {
                org.bukkit.block.Block block = iter.next();
                Material type = block.getType();
                if (!type.isSolid()) continue;        // air, flowers, carpet, etc.
                if (isGlassLike(type)) continue;      // glass is transparent to head-AI
                // Opaque solid - only counts if it is actually between the two points.
                double blockDistSq = block.getLocation().add(0.5, 0.5, 0.5).distanceSquared(start);
                if (blockDistSq < distance * distance) return false;
            }
        } catch (Exception e) {
            return from.hasLineOfSight(to); // safe fallback
        }
        return true;
    }

    /**
     * Returns {@code true} when the material is glass-like and should be
     * treated as transparent for bot line-of-sight checks.
     * Matches: {@code GLASS}, {@code STAINED_GLASS}, {@code GLASS_PANE},
     * {@code STAINED_GLASS_PANE}, {@code TINTED_GLASS}, and any future
     * glass-variant added by Mojang whose name contains {@code "GLASS"}.
     */
    private static boolean isGlassLike(Material mat) {
        return mat.name().contains("GLASS");
    }

    /**
     * Linearly interpolates between two angles in degrees, always taking the
     * shortest arc across the 360°/−180° boundary.
     *
     * @param from  current angle in degrees
     * @param to    target angle in degrees
     * @param t     blend factor – 0 = stay, 1 = snap immediately
     * @return interpolated angle
     */
    private static float lerpAngle(float from, float to, float t) {
        float diff = to - from;
        while (diff >  180f) diff -= 360f;
        while (diff < -180f) diff += 360f;
        return from + diff * t;
    }

    // ── Per-bot settings persistence ──────────────────────────────────────────

    /**
     * Persists all mutable per-bot settings for the given bot to the database
     * (frozen, chat, head-AI, nav overrides, pickup toggles, personality, right-click cmd).
     * Safe to call on the main thread; writes are enqueued on the DB writer thread.
     * No-op when the database is disabled or unavailable.
     */
    public void persistBotSettings(FakePlayer fp) {
        DatabaseManager db = plugin.getDatabaseManager();
        if (db == null || fp == null) return;
        db.updateBotAllSettings(
                fp.getUuid().toString(),
                fp.isFrozen(),
                fp.isChatEnabled(),
                fp.getChatTier(),
                fp.getRightClickCommand(),
                fp.getAiPersonality(),
                fp.isPickUpItemsEnabled(),
                fp.isPickUpXpEnabled(),
                fp.isHeadAiEnabled(),
                fp.isNavParkour(),
                fp.isNavBreakBlocks(),
                fp.isNavPlaceBlocks(),
                fp.isSwimAiEnabled(),
                fp.getChunkLoadRadius()
        );
    }
}
