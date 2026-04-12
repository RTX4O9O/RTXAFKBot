package me.bill.fakePlayerPlugin.fakeplayer;

import me.bill.fakePlayerPlugin.FakePlayerPlugin;
import me.bill.fakePlayerPlugin.config.Config;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.World;
import org.bukkit.block.Block;
import org.bukkit.entity.Player;
import org.bukkit.scheduler.BukkitRunnable;
import org.bukkit.scheduler.BukkitTask;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Supplier;

/**
 * Global shared pathfinding / navigation service for all bot systems.
 *
 * <p>This centralises the repeated navigation loops previously duplicated across
 * MoveCommand, MineCommand, PlaceCommand, and UseCommand.  Commands keep ownership
 * of their high-level task logic, but all low-level path execution now runs here.</p>
 */
public final class PathfindingService {

    public enum Owner {
        MOVE,
        MINE,
        PLACE,
        USE,
        SYSTEM
    }

    /**
     * Describes a single navigation job submitted to {@link PathfindingService#navigate}.
     *
     * <p>{@code lockOnArrival} — when non-null, {@link FakePlayerManager#lockForAction} is
     * called atomically (on the same tick, before {@code onArrive}) with this location when
     * the bot reaches the destination.  This eliminates the one-tick gap between nav-lock
     * release and action-lock acquisition, which is relevant for mining/placing start spots.
     * Pass {@code null} (or use the 8-arg constructor) for navigation that does not need an
     * immediate action lock on arrival (e.g. {@code MoveCommand} follow, drop collection).</p>
     */
    public record NavigationRequest(
            @NotNull Owner owner,
            @NotNull Supplier<@Nullable Location> destinationSupplier,
            double arrivalDistance,
            double recalcDistance,
            int maxNullPathRecalculations,
            @Nullable Runnable onArrive,
            @Nullable Runnable onCancel,
            @Nullable Runnable onPathFailure,
            @Nullable Location lockOnArrival
    ) {
        /** Backward-compatible constructor — {@code lockOnArrival} defaults to {@code null}. */
        public NavigationRequest(
                @NotNull Owner owner,
                @NotNull Supplier<@Nullable Location> destinationSupplier,
                double arrivalDistance,
                double recalcDistance,
                int maxNullPathRecalculations,
                @Nullable Runnable onArrive,
                @Nullable Runnable onCancel,
                @Nullable Runnable onPathFailure) {
            this(owner, destinationSupplier, arrivalDistance, recalcDistance,
                    maxNullPathRecalculations, onArrive, onCancel, onPathFailure, null);
        }

        public NavigationRequest {
            if (owner == null) throw new IllegalArgumentException("owner");
            if (destinationSupplier == null) throw new IllegalArgumentException("destinationSupplier");
            if (arrivalDistance <= 0) throw new IllegalArgumentException("arrivalDistance must be > 0");
            if (recalcDistance < 0) throw new IllegalArgumentException("recalcDistance must be >= 0");
            if (maxNullPathRecalculations <= 0) maxNullPathRecalculations = Integer.MAX_VALUE;
        }
    }

    private record Session(Owner owner, BukkitTask task) {}

    private final FakePlayerPlugin plugin;
    private final FakePlayerManager manager;
    private final Map<UUID, Session> sessions = new ConcurrentHashMap<>();

    public PathfindingService(FakePlayerPlugin plugin, FakePlayerManager manager) {
        this.plugin = plugin;
        this.manager = manager;
    }

    public boolean isNavigating(@NotNull UUID botUuid) {
        return sessions.containsKey(botUuid);
    }

    public boolean isNavigating(@NotNull UUID botUuid, @NotNull Owner owner) {
        Session session = sessions.get(botUuid);
        return session != null && session.owner() == owner;
    }

    @Nullable
    public Owner getOwner(@NotNull UUID botUuid) {
        Session session = sessions.get(botUuid);
        return session != null ? session.owner() : null;
    }

    public void cancel(@NotNull UUID botUuid) {
        Session session = sessions.remove(botUuid);
        if (session != null && !session.task().isCancelled()) {
            session.task().cancel();
        }
        manager.clearNavJump(botUuid);
        manager.unlockNavigation(botUuid);
        Player bot = Bukkit.getPlayer(botUuid);
        if (bot != null && bot.isOnline()) {
            NmsPlayerSpawner.setMovementForward(bot, 0f);
            NmsPlayerSpawner.setJumping(bot, false);
            bot.setSprinting(false);
        }
    }

    public void cancelAll() {
        for (UUID uuid : List.copyOf(sessions.keySet())) {
            cancel(uuid);
        }
    }

    public void cancelAll(@NotNull Owner owner) {
        for (Map.Entry<UUID, Session> entry : List.copyOf(sessions.entrySet())) {
            if (entry.getValue().owner() == owner) {
                cancel(entry.getKey());
            }
        }
    }

    public void navigate(@NotNull FakePlayer fp, @NotNull NavigationRequest request) {
        UUID botUuid = fp.getUuid();
        cancel(botUuid);
        manager.lockForNavigation(botUuid);
        manager.clearNavJump(botUuid);

        Player initialBot = fp.getPlayer();
        if (initialBot == null || !initialBot.isOnline()) {
            manager.unlockNavigation(botUuid);
            if (request.onCancel() != null) request.onCancel().run();
            return;
        }

        Location initialDest = safeDestination(request.destinationSupplier().get());
        if (initialDest == null || initialDest.getWorld() == null || !initialDest.getWorld().equals(initialBot.getWorld())) {
            manager.unlockNavigation(botUuid);
            if (request.onCancel() != null) request.onCancel().run();
            return;
        }

        primeInitialMovement(initialBot, initialDest, request.arrivalDistance());

        final List<BotPathfinder.Move>[] pathRef = new List[]{null};
        final int[] wpIdx = {0};
        final Location[] lastCalc = {initialDest.clone()};
        final int[] recalcIn = {0};
        final int[] stuckFor = {0};
        final int[] nullPathRecalcs = {0};
        final double[] prevX = {initialBot.getLocation().getX()};
        final double[] prevZ = {initialBot.getLocation().getZ()};

        final boolean[] isBreaking = {false};
        final int[] breakLeft = {0};
        final Location[] breakLoc = {null};

        final boolean[] isPlacing = {false};
        final int[] placeLeft = {0};

        BukkitTask task = new BukkitRunnable() {
            @Override
            public void run() {
                Player bot = Bukkit.getPlayer(botUuid);
                if (bot == null || !bot.isOnline()) {
                    cleanup(null, false, false);
                    return;
                }

                Location dest = safeDestination(request.destinationSupplier().get());
                if (dest == null || dest.getWorld() == null || !dest.getWorld().equals(bot.getWorld())) {
                    cleanup(bot, false, false);
                    return;
                }

                Location botLoc = bot.getLocation();
                double distToTarget = xzDist(botLoc, dest);
                if (distToTarget <= request.arrivalDistance()) {
                    cleanup(bot, true, false);
                    return;
                }

                boolean targetMoved = request.recalcDistance() > 0
                        && lastCalc[0] != null
                        && lastCalc[0].distanceSquared(dest) > request.recalcDistance() * request.recalcDistance();
                boolean pathExhausted = (pathRef[0] == null || wpIdx[0] >= pathRef[0].size());
                boolean heartbeat = (--recalcIn[0] <= 0);

                if (targetMoved || pathExhausted || heartbeat) {
                    recalcIn[0] = Config.pathfindingRecalcInterval();
                    lastCalc[0] = dest.clone();
                    isBreaking[0] = false;
                    breakLoc[0] = null;
                    isPlacing[0] = false;

                    BotPathfinder.PathOptions opts = new BotPathfinder.PathOptions(
                            fp.isNavParkour(),
                            fp.isNavBreakBlocks(),
                            fp.isNavPlaceBlocks());

                    List<BotPathfinder.Move> newPath = BotPathfinder.findPathMoves(
                            botLoc.getWorld(),
                            botLoc.getBlockX(), botLoc.getBlockY(), botLoc.getBlockZ(),
                            dest.getBlockX(), dest.getBlockY(), dest.getBlockZ(),
                            opts);

                    if (newPath == null) {
                        if (++nullPathRecalcs[0] >= request.maxNullPathRecalculations()) {
                            cleanup(bot, false, true);
                            return;
                        }
                    } else {
                        nullPathRecalcs[0] = 0;
                    }

                    pathRef[0] = newPath;
                    wpIdx[0] = (newPath != null && newPath.size() > 1) ? 1 : 0;
                    stuckFor[0] = 0;
                }

                List<BotPathfinder.Move> path = pathRef[0];
                if (path == null || path.isEmpty() || wpIdx[0] >= path.size()) {
                    walkToward(bot, dest, distToTarget);
                    return;
                }

                BotPathfinder.Move wp = path.get(wpIdx[0]);
                double wpCX = wp.x() + 0.5;
                double wpCZ = wp.z() + 0.5;

                if (wp.type() == BotPathfinder.MoveType.BREAK) {
                    if (!isBreaking[0]) {
                        Location breakTarget = findBreakTarget(botLoc, wp);
                        if (breakTarget != null) {
                            isBreaking[0] = true;
                            breakLeft[0] = Config.pathfindingBreakTicks();
                            breakLoc[0] = breakTarget;
                        } else {
                            recalcIn[0] = 0;
                            return;
                        }
                    }
                    stuckFor[0] = 0;
                    NmsPlayerSpawner.setMovementForward(bot, 0f);
                    bot.setSprinting(false);
                    Location blk = breakLoc[0];
                    if (blk != null) {
                        double bdx = blk.getX() - botLoc.getX();
                        double bdz = blk.getZ() - botLoc.getZ();
                        double bdy = blk.getY() - botLoc.getY();
                        float bYaw = (float) Math.toDegrees(Math.atan2(-bdx, bdz));
                        float bPitch = (float) -Math.toDegrees(Math.atan2(bdy, Math.sqrt(bdx * bdx + bdz * bdz)));
                        bot.setRotation(bYaw, bPitch);
                        NmsPlayerSpawner.setHeadYaw(bot, bYaw);
                    }
                    if (--breakLeft[0] <= 0) {
                        if (breakLoc[0] != null) {
                            breakLoc[0].getBlock().breakNaturally();
                            breakLoc[0] = null;
                        }
                        isBreaking[0] = false;
                        recalcIn[0] = 0;
                        stuckFor[0] = 0;
                    }
                    prevX[0] = botLoc.getX();
                    prevZ[0] = botLoc.getZ();
                    return;
                }

                if (wp.type() == BotPathfinder.MoveType.PLACE) {
                    if (!isPlacing[0]) {
                        isPlacing[0] = true;
                        placeLeft[0] = Config.pathfindingPlaceTicks();
                    }
                    stuckFor[0] = 0;
                    NmsPlayerSpawner.setMovementForward(bot, 0f);
                    bot.setSprinting(false);
                    double pdx = (wp.x() + 0.5) - botLoc.getX();
                    double pdz = (wp.z() + 0.5) - botLoc.getZ();
                    float pYaw = (float) Math.toDegrees(Math.atan2(-pdx, pdz));
                    bot.setRotation(pYaw, 70f);
                    NmsPlayerSpawner.setHeadYaw(bot, pYaw);
                    if (--placeLeft[0] <= 0) {
                        Block gapBlock = bot.getWorld().getBlockAt(wp.x(), wp.y() - 1, wp.z());
                        if (gapBlock.isPassable()) {
                            gapBlock.setType(resolvePlaceMaterial());
                        }
                        isPlacing[0] = false;
                        recalcIn[0] = 0;
                        stuckFor[0] = 0;
                    }
                    prevX[0] = botLoc.getX();
                    prevZ[0] = botLoc.getZ();
                    return;
                }

                double wpXZDist = xzDistRaw(botLoc.getX(), botLoc.getZ(), wpCX, wpCZ);
                boolean wpYClose = Math.abs(botLoc.getY() - wp.y()) < 1.2;
                if (wpXZDist < Config.pathfindingWaypointArrivalDistance() && wpYClose) {
                    wpIdx[0]++;
                    if (wpIdx[0] >= path.size()) {
                        recalcIn[0] = 0;
                        return;
                    }
                    wp = path.get(wpIdx[0]);
                    wpCX = wp.x() + 0.5;
                    wpCZ = wp.z() + 0.5;
                    wpXZDist = xzDistRaw(botLoc.getX(), botLoc.getZ(), wpCX, wpCZ);
                }

                double dx = wpCX - botLoc.getX();
                double dz = wpCZ - botLoc.getZ();
                float yaw = (float) Math.toDegrees(Math.atan2(-dx, dz));
                bot.setRotation(yaw, 0f);
                NmsPlayerSpawner.setHeadYaw(bot, yaw);

                bot.setSprinting(distToTarget > Config.pathfindingSprintDistance()
                        || wp.type() == BotPathfinder.MoveType.PARKOUR);
                NmsPlayerSpawner.setMovementForward(bot, 1.0f);

                if (!bot.isInWater() && !bot.isInLava()) {
                    if (wp.y() > botLoc.getBlockY()) {
                        manager.requestNavJump(botUuid);
                    } else if (wp.type() == BotPathfinder.MoveType.PARKOUR
                            && wpXZDist >= 1.0 && wpXZDist <= 3.5) {
                        manager.requestNavJump(botUuid);
                    }
                }

                double moved = xzDistRaw(botLoc.getX(), botLoc.getZ(), prevX[0], prevZ[0]);
                if (moved < Config.pathfindingStuckThreshold()) {
                    if (++stuckFor[0] >= Config.pathfindingStuckTicks()) {
                        if (!bot.isInWater() && !bot.isInLava()) {
                            manager.requestNavJump(botUuid);
                        }
                        recalcIn[0] = 0;
                        stuckFor[0] = 0;
                    }
                } else {
                    stuckFor[0] = 0;
                }

                prevX[0] = botLoc.getX();
                prevZ[0] = botLoc.getZ();
            }

            private void cleanup(@Nullable Player bot, boolean arrived, boolean pathFailure) {
                manager.clearNavJump(botUuid);
                manager.unlockNavigation(botUuid);
                if (bot != null) {
                    NmsPlayerSpawner.setMovementForward(bot, 0f);
                    NmsPlayerSpawner.setJumping(bot, false);
                    bot.setSprinting(false);
                }
                cancel();
                sessions.remove(botUuid);
                if (arrived) {
                    // Atomically acquire the action lock before firing onArrive so there
                    // is no tick where neither nav-lock nor action-lock is held.
                    if (request.lockOnArrival() != null) {
                        manager.lockForAction(botUuid, request.lockOnArrival());
                    }
                    if (request.onArrive() != null) request.onArrive().run();
                } else if (pathFailure) {
                    if (request.onPathFailure() != null) request.onPathFailure().run();
                    else if (request.onCancel() != null) request.onCancel().run();
                } else if (request.onCancel() != null) {
                    request.onCancel().run();
                }
            }

            private void walkToward(Player bot, Location target, double dist) {
                Location bl = bot.getLocation();
                float yaw = (float) Math.toDegrees(Math.atan2(-(target.getX() - bl.getX()), target.getZ() - bl.getZ()));
                bot.setRotation(yaw, 0f);
                NmsPlayerSpawner.setHeadYaw(bot, yaw);
                bot.setSprinting(dist > Config.pathfindingSprintDistance());
                NmsPlayerSpawner.setMovementForward(bot, 1.0f);
            }
        }.runTaskTimer(plugin, 0L, 1L);

        sessions.put(botUuid, new Session(request.owner(), task));
    }

    private void primeInitialMovement(Player bot, Location dest, double arrivalDistance) {
        Location bl = bot.getLocation();
        double initDist = xzDist(bl, dest);
        if (initDist <= arrivalDistance) return;
        float initYaw = (float) Math.toDegrees(Math.atan2(-(dest.getX() - bl.getX()), dest.getZ() - bl.getZ()));
        bot.setRotation(initYaw, 0f);
        NmsPlayerSpawner.setHeadYaw(bot, initYaw);
        bot.setSprinting(initDist > Config.pathfindingSprintDistance());
        NmsPlayerSpawner.setMovementForward(bot, 1.0f);
    }

    @Nullable
    private static Location safeDestination(@Nullable Location loc) {
        return loc != null ? loc.clone() : null;
    }

    @Nullable
    private static Location findBreakTarget(Location botLoc, BotPathfinder.Move wp) {
        World w = botLoc.getWorld();
        int wx = wp.x(), wy = wp.y(), wz = wp.z();
        if (!w.getBlockAt(wx, wy, wz).isPassable()) return new Location(w, wx + 0.5, wy + 0.5, wz + 0.5);
        if (!w.getBlockAt(wx, wy + 1, wz).isPassable()) return new Location(w, wx + 0.5, wy + 1.5, wz + 0.5);
        int by = botLoc.getBlockY();
        int bx = botLoc.getBlockX(), bz = botLoc.getBlockZ();
        if (!w.getBlockAt(bx, by + 2, bz).isPassable()) return new Location(w, bx + 0.5, by + 2.5, bz + 0.5);
        return null;
    }

    private static Material resolvePlaceMaterial() {
        String raw = Config.pathfindingPlaceMaterial();
        Material mat = Material.matchMaterial(raw.toUpperCase());
        if (mat != null && mat.isBlock() && mat.isSolid()) return mat;
        return Material.DIRT;
    }

    /** XZ-plane distance between two locations (ignores Y). */
    public static double xzDist(Location a, Location b) {
        return xzDistRaw(a.getX(), a.getZ(), b.getX(), b.getZ());
    }

    /** XZ-plane distance from raw coordinates (ignores Y). */
    public static double xzDistRaw(double ax, double az, double bx, double bz) {
        double dx = ax - bx;
        double dz = az - bz;
        return Math.sqrt(dx * dx + dz * dz);
    }
}



