package me.bill.fakePlayerPlugin.fakeplayer;

import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ThreadLocalRandom;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.Supplier;
import me.bill.fakePlayerPlugin.FakePlayerPlugin;
import me.bill.fakePlayerPlugin.config.Config;
import me.bill.fakePlayerPlugin.util.FppScheduler;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.Sound;
import org.bukkit.SoundCategory;
import org.bukkit.Tag;
import org.bukkit.World;
import org.bukkit.block.Block;
import org.bukkit.block.BlockFace;
import org.bukkit.block.data.Bisected;
import org.bukkit.block.data.Directional;
import org.bukkit.block.data.MultipleFacing;
import org.bukkit.block.data.Openable;
import org.bukkit.block.data.type.Door;
import org.bukkit.block.data.type.Gate;
import org.bukkit.entity.Player;
import org.bukkit.util.Vector;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

public final class PathfindingService {

  private static final int SWIM_SURFACE_SCAN = 6;
  private static final int SWIM_CEILING_SCAN = 3;
  private static final int SWIM_NEAR_SURFACE_THRESHOLD = 2;
  private static final double MIN_TURN_XZ_DIST = 0.2;
  private static final AtomicInteger NAV_START_SEQUENCE = new AtomicInteger();

  public enum Owner {
    MOVE,
    MINE,
    PLACE,
    USE,
    ATTACK,
    FOLLOW,
    SLEEP,
    SYSTEM
  }

  public record NavigationRequest(
      @NotNull Owner owner,
      @NotNull Supplier<@Nullable Location> destinationSupplier,
      double arrivalDistance,
      double recalcDistance,
      int maxNullPathRecalculations,
      @Nullable Runnable onArrive,
      @Nullable Runnable onCancel,
      @Nullable Runnable onPathFailure,
      @Nullable Location lockOnArrival,
      @Nullable BotPathfinder.PathOptions overrideOpts) {

    /** Backward-compatible 9-arg constructor (no overrideOpts). */
    public NavigationRequest(
        @NotNull Owner owner,
        @NotNull Supplier<@Nullable Location> destinationSupplier,
        double arrivalDistance,
        double recalcDistance,
        int maxNullPathRecalculations,
        @Nullable Runnable onArrive,
        @Nullable Runnable onCancel,
        @Nullable Runnable onPathFailure,
        @Nullable Location lockOnArrival) {
      this(
          owner,
          destinationSupplier,
          arrivalDistance,
          recalcDistance,
          maxNullPathRecalculations,
          onArrive,
          onCancel,
          onPathFailure,
          lockOnArrival,
          null);
    }

    /** Backward-compatible 8-arg constructor (no lockOnArrival, no overrideOpts). */
    public NavigationRequest(
        @NotNull Owner owner,
        @NotNull Supplier<@Nullable Location> destinationSupplier,
        double arrivalDistance,
        double recalcDistance,
        int maxNullPathRecalculations,
        @Nullable Runnable onArrive,
        @Nullable Runnable onCancel,
        @Nullable Runnable onPathFailure) {
      this(
          owner,
          destinationSupplier,
          arrivalDistance,
          recalcDistance,
          maxNullPathRecalculations,
          onArrive,
          onCancel,
          onPathFailure,
          null,
          null);
    }
  }

  private record Session(Owner owner, int taskId) {}

  private record OpenableRef(UUID worldId, int x, int y, int z) {}

  private final FakePlayerPlugin plugin;
  private final FakePlayerManager manager;
  private final Map<UUID, Session> sessions = new ConcurrentHashMap<>();
  private final Map<UUID, Set<OpenableRef>> openedDoorRefs = new ConcurrentHashMap<>();

  private Material cachedPlaceMaterial = null;
  private String cachedPlaceMaterialName = null;

  public PathfindingService(FakePlayerPlugin plugin, FakePlayerManager manager) {
    this.plugin = plugin;
    this.manager = manager;

    me.bill.fakePlayerPlugin.util.AttributionManager.quickAuthorCheck();
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
    if (session != null) {
      FppScheduler.cancelTask(session.taskId());
    }
    Set<OpenableRef> opened = openedDoorRefs.remove(botUuid);
    if (opened != null) {
      closeOpenedDoors(opened, null, true);
    }
    manager.clearNavJump(botUuid);
    manager.unlockNavigation(botUuid);
    Player bot = Bukkit.getPlayer(botUuid);
    if (bot != null && bot.isOnline()) {
      NmsPlayerSpawner.setMovementForward(bot, 0f);
      NmsPlayerSpawner.setMovementStrafe(bot, 0f);
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
    Session existing = sessions.get(botUuid);
    if (existing != null && existing.owner() != request.owner()) {
      manager.interruptNavigationOwner(botUuid, existing.owner());
    }
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
    if (initialDest == null
        || initialDest.getWorld() == null
        || !initialDest.getWorld().equals(initialBot.getWorld())) {
      manager.unlockNavigation(botUuid);
      if (request.onCancel() != null) request.onCancel().run();
      return;
    }

    var navStartEvt = new me.bill.fakePlayerPlugin.api.event.FppBotNavigationEvent(
        new me.bill.fakePlayerPlugin.api.impl.FppBotImpl(fp),
        me.bill.fakePlayerPlugin.api.event.FppBotNavigationEvent.Action.START,
        initialDest);
    org.bukkit.Bukkit.getPluginManager().callEvent(navStartEvt);

    primeInitialMovement(initialBot, initialDest, request.arrivalDistance());

    @SuppressWarnings("unchecked")
    final List<BotPathfinder.Move>[] pathRef = (List<BotPathfinder.Move>[]) new List<?>[] {null};
    final int[] wpIdx = {0};
    final Location[] lastCalc = {initialDest.clone()};
    final int recalcInterval = recalcIntervalTicks(request.owner());
    int startOffset =
        Math.floorMod(NAV_START_SEQUENCE.getAndIncrement(), Math.min(recalcInterval, 10));
    final int[] recalcIn = {startOffset};
    final int[] stuckFor = {0};
    final int[] hardWallStuckFor = {0};
    final int effectiveStuckTicks =
        request.owner() == Owner.MOVE && request.maxNullPathRecalculations() <= 3
            ? 1
            : Config.pathfindingStuckTicks();
    final int hardWallThreshold =
        request.owner() == Owner.MOVE && request.maxNullPathRecalculations() <= 3 ? 1 : 3;
    final int[] nullPathRecalcs = {0};
    final int[] nullPathRetryIn = {0};
    final double[] prevX = {initialBot.getLocation().getX()};
    final double[] prevZ = {initialBot.getLocation().getZ()};
    final boolean[] cleaningUp = {false};
    final int[] taskIdRef = {-1};
    final boolean[] isBreaking = {false};
    final int[] breakLeft = {0};
    final Location[] breakLoc = {null};

    final boolean[] isPlacing = {false};
    final int[] placeLeft = {0};

    // Wobble state: slow sine-wave yaw drift that gives bots a slightly
    // imprecise, human-like movement path instead of laser-straight lines.
    // Phase advances ~1 radian per second (every ~50 ticks); amplitude ±5°.
    final double[] wobblePhase = {ThreadLocalRandom.current().nextDouble(Math.PI * 2)};
    final int[] wobbleTick = {0};

    // Detour state: when A* returns null AND a wall is directly ahead, we compute a detour
    // waypoint and path to it first before resuming toward the real target.
    @SuppressWarnings("unchecked")
    final List<BotPathfinder.Move>[] detourPath = (List<BotPathfinder.Move>[]) new List<?>[] {null};
    final int[] detourWpIdx = {0};
    final int[] detourNullCount = {0};

    final Set<OpenableRef> openedDoors = ConcurrentHashMap.newKeySet();
    openedDoorRefs.put(botUuid, openedDoors);

    Runnable tick =
        new Runnable() {
          @Override
          public void run() {
            if (cleaningUp[0]) {
              return;
            }

            Player bot = fp.getPlayer();
            if (bot == null || !bot.isOnline()) {
              cleanup(null, false, false);
              return;
            }

            Location dest = safeDestination(request.destinationSupplier().get());
            if (dest == null
                || dest.getWorld() == null
                || !dest.getWorld().equals(bot.getWorld())) {
              cleanup(bot, false, false);
              return;
            }

            Location botLoc = bot.getLocation();
            closeOpenedDoors(openedDoors, botLoc, false);

            // Consume nav jump state on the entity's region thread — no cross-thread race.
            // The velocity impulse fires on tick 1 of the 5-tick window, then the counter
            // decrements for the remaining 4 ticks. This keeps jump logic entirely
            // on the same thread as the nav tick that called requestNavJump().
            Integer navHold = manager.getNavJumpHolding(botUuid);
            if (navHold != null) {
              NmsPlayerSpawner.setJumping(bot, true);
              if (((org.bukkit.entity.Entity) bot).isOnGround()
                  && !bot.isInWater()
                  && !bot.isInLava()) {
                org.bukkit.util.Vector v = bot.getVelocity();
                v.setY(Math.max(v.getY(), 0.42));
                NmsPlayerSpawner.applyServerVelocity(bot, v);
              }
              if (navHold <= 1) {
                manager.clearNavJump(botUuid);
                if (!bot.isInWater() && !bot.isInLava()) {
                  NmsPlayerSpawner.setJumping(bot, false);
                }
              } else {
                manager.setNavJumpHolding(botUuid, navHold - 1);
              }
            }

            double arrivalSq = request.arrivalDistance() * request.arrivalDistance();
            double dx0 = botLoc.getX() - dest.getX();
            double dz0 = botLoc.getZ() - dest.getZ();
            double distToTargetSq = dx0 * dx0 + dz0 * dz0;
            if (distToTargetSq <= arrivalSq) {
              if (request.recalcDistance() > 0) {
                NmsPlayerSpawner.setMovementForward(bot, 0f);
                NmsPlayerSpawner.setMovementStrafe(bot, 0f);
                NmsPlayerSpawner.setJumping(bot, false);
                bot.setSprinting(false);
                return;
              }
              cleanup(bot, true, false);
              return;
            }

            double distToTarget = Math.sqrt(distToTargetSq);

            boolean targetMovedFromLastCalc =
                request.recalcDistance() > 0
                    && lastCalc[0] != null
                    && lastCalc[0].distanceSquared(dest)
                        > request.recalcDistance() * request.recalcDistance();
            boolean targetMoved =
                targetMovedFromLastCalc
                    && shouldRecalculateForTargetShift(request, pathRef[0], wpIdx[0], dest);
            if (nullPathRetryIn[0] > 0) {
              nullPathRetryIn[0]--;
            }
            boolean pathExhausted =
                (pathRef[0] == null && nullPathRetryIn[0] <= 0)
                    || (pathRef[0] != null && wpIdx[0] >= pathRef[0].size());
            boolean heartbeat = request.recalcDistance() > 0 && --recalcIn[0] <= 0;

            if (targetMoved || pathExhausted || heartbeat) {
              recalcIn[0] = recalcInterval;
              var navRecalcEvt = new me.bill.fakePlayerPlugin.api.event.FppBotNavigationEvent(
                  new me.bill.fakePlayerPlugin.api.impl.FppBotImpl(fp),
                  me.bill.fakePlayerPlugin.api.event.FppBotNavigationEvent.Action.RECALC,
                  dest);
              org.bukkit.Bukkit.getPluginManager().callEvent(navRecalcEvt);

              if (lastCalc[0] == null) {
                lastCalc[0] = dest.clone();
              } else {
                lastCalc[0].setX(dest.getX());
                lastCalc[0].setY(dest.getY());
                lastCalc[0].setZ(dest.getZ());
                lastCalc[0].setWorld(dest.getWorld());
              }
              isBreaking[0] = false;
              breakLoc[0] = null;
              isPlacing[0] = false;

              BotPathfinder.PathOptions opts = resolvePathOptions(fp, request.overrideOpts());

              List<BotPathfinder.Move> newPath =
                  BotPathfinder.findPathMoves(
                      botLoc.getWorld(),
                      botLoc.getBlockX(),
                      botLoc.getBlockY(),
                      botLoc.getBlockZ(),
                      dest.getBlockX(),
                      dest.getBlockY(),
                      dest.getBlockZ(),
                      opts);

              if (newPath == null) {
                if (++nullPathRecalcs[0] >= request.maxNullPathRecalculations()) {
                  cleanup(bot, false, true);
                  return;
                }
                nullPathRetryIn[0] = Math.max(10, recalcInterval);
                NmsPlayerSpawner.setMovementForward(bot, 0f);
                bot.setSprinting(false);
                // Direct path failed — try to compute a detour around the obstacle.
                int[] dg = BotPathfinder.findDetourGoal(
                    botLoc.getWorld(),
                    botLoc.getBlockX(), botLoc.getBlockY(), botLoc.getBlockZ(),
                    dest.getBlockX(), dest.getBlockY(), dest.getBlockZ(),
                    Config.pathfindingDetourAttempts(),
                    Config.pathfindingDetourRadius() / (double) Config.pathfindingDetourAttempts(),
                    opts);
                if (dg != null) {
                  detourPath[0] = BotPathfinder.findPathMoves(
                      botLoc.getWorld(),
                      botLoc.getBlockX(), botLoc.getBlockY(), botLoc.getBlockZ(),
                      dg[0], dg[1], dg[2], opts);
                  detourWpIdx[0] = (detourPath[0] != null && detourPath[0].size() > 1) ? 1 : 0;
                } else {
                  detourPath[0] = null;
                  detourWpIdx[0] = 0;
                }
              } else {
                nullPathRecalcs[0] = 0;
                nullPathRetryIn[0] = 0;
              }

              pathRef[0] = newPath;
              wpIdx[0] = (newPath != null && newPath.size() > 1) ? 1 : 0;
              stuckFor[0] = 0;
            }

            List<BotPathfinder.Move> path = pathRef[0];

            // If we have an active detour path, follow it until exhausted.
            if ((path == null || path.isEmpty() || wpIdx[0] >= path.size())
                && detourPath[0] != null && detourWpIdx[0] < detourPath[0].size()) {
              path = detourPath[0];
              // Use detourWpIdx inline below by aliasing path and index
              BotPathfinder.Move dwp = detourPath[0].get(detourWpIdx[0]);
              double dwpCX = dwp.x() + 0.5;
              double dwpCZ = dwp.z() + 0.5;
              double dwpXZDist = xzDistRaw(botLoc.getX(), botLoc.getZ(), dwpCX, dwpCZ);
              boolean dwpYClose = Math.abs(botLoc.getY() - dwp.y()) < 1.2;
              if (dwpXZDist < Config.pathfindingWaypointArrivalDistance() && dwpYClose) {
                detourWpIdx[0]++;
                if (detourWpIdx[0] >= detourPath[0].size()) {
                  detourPath[0] = null;
                  recalcIn[0] = 0; // detour done — immediately replan toward real target
                  // Fall through to coast/replan logic below rather than returning here.
                } else {
                  dwp = detourPath[0].get(detourWpIdx[0]);
                  dwpCX = dwp.x() + 0.5;
                  dwpCZ = dwp.z() + 0.5;
                  dwpXZDist = xzDistRaw(botLoc.getX(), botLoc.getZ(), dwpCX, dwpCZ);
                }
              }
              if (detourPath[0] != null) {
                double ddx = dwpCX - botLoc.getX();
                double ddz = dwpCZ - botLoc.getZ();
                float dYaw = stableYaw(bot.getLocation().getYaw(), ddx, ddz);
                bot.setRotation(dYaw, 0f);
                NmsPlayerSpawner.setHeadYaw(bot, dYaw);
                maybeOpenDoorAhead(bot, dYaw, openedDoors);
                bot.setSprinting(distToTarget > Config.pathfindingSprintDistance());
                NmsPlayerSpawner.setMovementForward(bot, 1.0f);
                if (!bot.isInWater()
                    && !bot.isInLava()
                    && dwp.y() > botLoc.getBlockY()) {
                  manager.requestNavJump(botUuid);
                }
                prevX[0] = botLoc.getX();
                prevZ[0] = botLoc.getZ();
                return;
              }
              // detour just finished — fall through to coast/replan below
            }

            if (path == null || path.isEmpty() || wpIdx[0] >= path.size()) {
              // Path exhausted or null — force an immediate replan next tick.
              // Do NOT walk toward the target directly; that causes wall-walking.
              if (distToTargetSq > request.arrivalDistance() * request.arrivalDistance() * 4) {
                recalcIn[0] = 0;
              }
              // Keep momentum toward target so we don't visibly stutter between segments.
              double dx = dest.getX() - botLoc.getX();
              double dz = dest.getZ() - botLoc.getZ();
              float coastYaw = stableYaw(bot.getLocation().getYaw(), dx, dz);
              bot.setRotation(coastYaw, 0f);
              NmsPlayerSpawner.setHeadYaw(bot, coastYaw);
              maybeOpenDoorAhead(bot, coastYaw, openedDoors);
              bot.setSprinting(distToTarget > Config.pathfindingSprintDistance());
              NmsPlayerSpawner.setMovementForward(bot, 1.0f);
              return;
            }

            BotPathfinder.Move wp = path.get(wpIdx[0]);
            boolean finalWaypoint = wpIdx[0] >= path.size() - 1;
            // Keep intermediate nodes block-centered, but let the final leg aim at the real
            // requested destination so navigation no longer visibly snaps onto the snapped goal.
            double wpCX = finalWaypoint ? dest.getX() : wp.x() + 0.5;
            double wpCZ = finalWaypoint ? dest.getZ() : wp.z() + 0.5;

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
                float bPitch =
                    (float) -Math.toDegrees(Math.atan2(bdy, Math.sqrt(bdx * bdx + bdz * bdz)));
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

            if (wp.type() == BotPathfinder.MoveType.PILLAR) {
              stuckFor[0] = 0;
              NmsPlayerSpawner.setMovementForward(bot, 0f);
              bot.setSprinting(false);

              manager.requestNavJump(botUuid);

              if (botLoc.getY() - botLoc.getBlockY() > 0.4) {
                Block below =
                    bot.getWorld()
                        .getBlockAt(botLoc.getBlockX(), botLoc.getBlockY() - 1, botLoc.getBlockZ());
                if (below.isPassable()) {
                  below.setType(resolvePlaceMaterial());
                }

                wpIdx[0]++;
                recalcIn[0] = 0;
              }
              prevX[0] = botLoc.getX();
              prevZ[0] = botLoc.getZ();
              return;
            }

            double wpXZDist = xzDistRaw(botLoc.getX(), botLoc.getZ(), wpCX, wpCZ);

            if (wp.type() == BotPathfinder.MoveType.SWIM) {
              double sdx = wpCX - botLoc.getX();
              double sdz = wpCZ - botLoc.getZ();
              double swimDist = xzDistRaw(botLoc.getX(), botLoc.getZ(), wpCX, wpCZ);
              double swimYDist = Math.abs(botLoc.getY() - wp.y());
              if (swimDist < Math.max(0.55, Config.pathfindingWaypointArrivalDistance())
                  && swimYDist < 1.5) {
                wpIdx[0]++;
                prevX[0] = botLoc.getX();
                prevZ[0] = botLoc.getZ();
                return;
              }

              boolean verticalSwimStep = swimDist < 0.45;
              float sYaw = verticalSwimStep
                  ? bot.getLocation().getYaw()
                  : (float) Math.toDegrees(Math.atan2(-sdx, sdz));

              int targetY = wp.y();
              int currentY = botLoc.getBlockY();
              float swimPitch;
              boolean shouldJump;

              if (targetY > currentY) {
                // Rising — pitch slightly upward, jump to ascend.
                swimPitch = -25f;
                shouldJump = true;
              } else if (targetY < currentY) {
                // Diving — pitch downward, no jump so bot sinks.
                swimPitch = 45f;
                shouldJump = false;
              } else {
                // Horizontal swim — pitch near 0, jump to maintain depth.
                swimPitch = -10f;
                shouldJump =
                    bot.isInLava()
                        || (bot.isInWater()
                            && distanceToSurface(
                                    bot.getWorld(),
                                    botLoc.getBlockX(),
                                    botLoc.getBlockY(),
                                    botLoc.getBlockZ())
                                > SWIM_NEAR_SURFACE_THRESHOLD);
              }

              bot.setRotation(sYaw, swimPitch);
              NmsPlayerSpawner.setHeadYaw(bot, sYaw);
              NmsPlayerSpawner.setMovementForward(bot, verticalSwimStep ? 0f : 1.0f);
              // Sprint-swimming in Minecraft requires the sprinting flag.
              bot.setSprinting(!verticalSwimStep);
              NmsPlayerSpawner.setJumping(bot, shouldJump);

              double swimMoved = xzDistRaw(botLoc.getX(), botLoc.getZ(), prevX[0], prevZ[0]);
              if (!verticalSwimStep && swimMoved < Config.pathfindingStuckThreshold()) {
                if (++stuckFor[0] >= Math.max(12, Config.pathfindingStuckTicks() * 2)) {
                  // Swimming in place usually means the current water path is bad or the bot is
                  // fighting the shoreline/waterline. Force an immediate replan instead of
                  // endlessly rotating around a nearly-zero X/Z target.
                  recalcIn[0] = 0;
                  stuckFor[0] = 0;
                }
              } else {
                stuckFor[0] = 0;
              }

              prevX[0] = botLoc.getX();
              prevZ[0] = botLoc.getZ();
              return;
            }

            if (wp.type() == BotPathfinder.MoveType.ELEVATOR_UP
                || wp.type() == BotPathfinder.MoveType.ELEVATOR_DOWN) {
              double elevYDist = Math.abs(botLoc.getY() - wp.y());
              if (wpXZDist < 0.38 && elevYDist < 0.7) {
                wpIdx[0]++;
                prevX[0] = botLoc.getX();
                prevZ[0] = botLoc.getZ();
                return;
              }

              applyBubbleElevatorMovement(bot, botLoc, wpCX, wpCZ, wp.y(), wp.type());
              prevX[0] = botLoc.getX();
              prevZ[0] = botLoc.getZ();
              return;
            }

            if (wp.type() == BotPathfinder.MoveType.CLIMB) {
              double climbYDist = Math.abs(botLoc.getY() - wp.y());
              if (wpXZDist < 0.4 && climbYDist < 0.7) {
                wpIdx[0]++;
                prevX[0] = botLoc.getX();
                prevZ[0] = botLoc.getZ();
                return;
              }

              // Don't attempt climb movement until we're actually inside the climbable column;
              // being slightly offset can leave the bot stuck at the bottom of ladders/vines.
              if (!isInClimbableColumn(botLoc)) {
                walkToward(bot, new Location(botLoc.getWorld(), wpCX, botLoc.getY(), wpCZ), wpXZDist);
                prevX[0] = botLoc.getX();
                prevZ[0] = botLoc.getZ();
                return;
              }

              applyClimbMovement(bot, botLoc, wpCX, wpCZ, wp.y());

              double climbMoved = xzDistRaw(botLoc.getX(), botLoc.getZ(), prevX[0], prevZ[0]);
              if (climbMoved < Config.pathfindingStuckThreshold()
                  && Math.abs(botLoc.getY() - wp.y()) > 0.5) {
                if (++stuckFor[0] >= Math.max(10, Config.pathfindingStuckTicks())) {
                  recalcIn[0] = 0;
                  stuckFor[0] = 0;
                }
              } else {
                stuckFor[0] = 0;
              }

              prevX[0] = botLoc.getX();
              prevZ[0] = botLoc.getZ();
              return;
            }

            boolean wpYClose = Math.abs(botLoc.getY() - wp.y()) < 1.2;
            // For ASCEND steps, don't consume the waypoint until the bot has actually
            // reached the upper block — prevents the jump from being skipped when the
            // waypoint arrival radius is satisfied while the bot is still at the lower level.
            boolean wpAscendReady =
                wp.type() != BotPathfinder.MoveType.ASCEND
                    || botLoc.getY() >= wp.y() - 0.3;
            if (wpXZDist < Config.pathfindingWaypointArrivalDistance() && wpYClose && wpAscendReady) {
              wpIdx[0]++;
              if (wpIdx[0] >= path.size()) {
                // Segment complete — keep coasting toward destination while replan fires.
                recalcIn[0] = 0;
                double cdx = dest.getX() - botLoc.getX();
                double cdz = dest.getZ() - botLoc.getZ();
                float coastYaw = stableYaw(bot.getLocation().getYaw(), cdx, cdz);
                bot.setRotation(coastYaw, 0f);
                NmsPlayerSpawner.setHeadYaw(bot, coastYaw);
                maybeOpenDoorAhead(bot, coastYaw, openedDoors);
                bot.setSprinting(distToTarget > Config.pathfindingSprintDistance());
                NmsPlayerSpawner.setMovementForward(bot, 1.0f);
                return;
              }
              wp = path.get(wpIdx[0]);
              finalWaypoint = wpIdx[0] >= path.size() - 1;
              wpCX = finalWaypoint ? dest.getX() : wp.x() + 0.5;
              wpCZ = finalWaypoint ? dest.getZ() : wp.z() + 0.5;
              wpXZDist = xzDistRaw(botLoc.getX(), botLoc.getZ(), wpCX, wpCZ);
            }

            double dx = wpCX - botLoc.getX();
            double dz = wpCZ - botLoc.getZ();
            float yaw = stableYaw(bot.getLocation().getYaw(), dx, dz);

            // Humanising wobble: advance the sine phase slowly and add a small
            // yaw drift (±5°).  Only applied when moving in a straight line
            // (not during ascend, parkour, or final approach) to avoid throwing
            // the bot off ledges or making it miss narrow paths.
            if (wp.type() == BotPathfinder.MoveType.WALK && !finalWaypoint) {
              wobbleTick[0]++;
              if (wobbleTick[0] >= 3) {
                wobbleTick[0] = 0;
                wobblePhase[0] += 0.06 + ThreadLocalRandom.current().nextDouble(0.04);
              }
              float wobble = (float) (Math.sin(wobblePhase[0]) * 5.0);
              yaw += wobble;
            }

            bot.setRotation(yaw, 0f);
            NmsPlayerSpawner.setHeadYaw(bot, yaw);
            maybeOpenDoorAhead(bot, yaw, openedDoors);

            bot.setSprinting(
                distToTarget > Config.pathfindingSprintDistance()
                    || wp.type() == BotPathfinder.MoveType.PARKOUR);
            NmsPlayerSpawner.setMovementForward(bot, 1.0f);

            if (bot.isInWater() || bot.isInLava()) {
              // When the next waypoint is on land, keep holding jump while pushing forward.
              // Without this, bots reach the shoreline but fail to climb out of the fluid.
              boolean exitingFluid = applyShorelineExitAssist(bot, yaw, botLoc, wp.y());
              NmsPlayerSpawner.setJumping(bot, exitingFluid || wp.y() >= botLoc.getBlockY());
            } else {
              if (wp.y() > botLoc.getBlockY()) {
                // Fire jump whenever the next waypoint is above us and we're within 2.5 blocks XZ,
                // not just when we're at the waypoint — the bot needs to be jumping on approach.
                if (wpXZDist <= 2.5) {
                  manager.requestNavJump(botUuid);
                }
              } else if (wp.type() == BotPathfinder.MoveType.PARKOUR
                  && wpXZDist >= 1.0
                  && wpXZDist <= 3.5) {
                manager.requestNavJump(botUuid);
              }
            }

            double moved = xzDistRaw(botLoc.getX(), botLoc.getZ(), prevX[0], prevZ[0]);
            if (moved < Config.pathfindingStuckThreshold()) {
              if (++stuckFor[0] >= effectiveStuckTicks) {
                if (!bot.isInWater() && !bot.isInLava()) {
                  // Only jump if the block ahead is a single-block step (not a full wall).
                  // A full wall means jumping won't help — trigger a replan/detour instead.
                  World w = botLoc.getWorld();
                  float facingYaw = bot.getLocation().getYaw();
                  int fx = (int) Math.floor(botLoc.getX() + Math.sin(-Math.toRadians(facingYaw)) * 0.8);
                  int fz = (int) Math.floor(botLoc.getZ() + Math.cos(-Math.toRadians(facingYaw)) * 0.8);
                  int fy = botLoc.getBlockY();
                  boolean feetBlocked = !BotPathfinder.canPassThrough(w, fx, fy, fz);
                  boolean headBlocked = !BotPathfinder.canPassThrough(w, fx, fy + 1, fz);
                  boolean aboveClear = BotPathfinder.canPassThrough(w, fx, fy + 2, fz);
                  if (feetBlocked && headBlocked) {
                    if (++hardWallStuckFor[0] >= hardWallThreshold) {
                      cleanup(bot, false, true);
                      return;
                    }
                  } else if (feetBlocked && !headBlocked && aboveClear) {
                    // Single-block step — jump over it.
                    manager.requestNavJump(botUuid);
                    hardWallStuckFor[0] = 0;
                  } else {
                    hardWallStuckFor[0] = 0;
                  }
                  // Whether or not we jumped, force an immediate replan so the detour
                  // logic gets a chance to route around the obstacle.
                  recalcIn[0] = 0;
                }
                stuckFor[0] = 0;
              }
            } else {
              stuckFor[0] = 0;
              hardWallStuckFor[0] = 0;
            }

            prevX[0] = botLoc.getX();
            prevZ[0] = botLoc.getZ();
          }

          private void cleanup(@Nullable Player bot, boolean arrived, boolean pathFailure) {
            if (cleaningUp[0]) {
              return;
            }
            cleaningUp[0] = true;
            manager.clearNavJump(botUuid);
            manager.unlockNavigation(botUuid);
            Set<OpenableRef> opened = openedDoorRefs.remove(botUuid);
            if (opened != null) {
              closeOpenedDoors(opened, bot != null ? bot.getLocation() : null, true);
            }
            if (bot != null) {
              NmsPlayerSpawner.setMovementForward(bot, 0f);
              NmsPlayerSpawner.setMovementStrafe(bot, 0f);
              NmsPlayerSpawner.setJumping(bot, false);
              bot.setSprinting(false);
            }
            sessions.remove(botUuid);
            int currentTaskId = taskIdRef[0];
            if (currentTaskId != -1) {
              FppScheduler.cancelTask(currentTaskId);
              taskIdRef[0] = -1;
            }
            if (arrived) {
              FakePlayer navFp = manager.getByUuid(botUuid);
              if (navFp != null) {
                var navArriveEvt = new me.bill.fakePlayerPlugin.api.event.FppBotNavigationEvent(
                    new me.bill.fakePlayerPlugin.api.impl.FppBotImpl(navFp),
                    me.bill.fakePlayerPlugin.api.event.FppBotNavigationEvent.Action.ARRIVE,
                    bot != null ? bot.getLocation() : null);
                org.bukkit.Bukkit.getPluginManager().callEvent(navArriveEvt);
              }
              if (request.lockOnArrival() != null) {
                manager.lockForAction(botUuid, request.lockOnArrival());
              }
              if (request.onArrive() != null) request.onArrive().run();
            } else if (pathFailure) {
              FakePlayer navFp = manager.getByUuid(botUuid);
              if (navFp != null) {
                var navFailEvt = new me.bill.fakePlayerPlugin.api.event.FppBotNavigationEvent(
                    new me.bill.fakePlayerPlugin.api.impl.FppBotImpl(navFp),
                    me.bill.fakePlayerPlugin.api.event.FppBotNavigationEvent.Action.FAIL,
                    bot != null ? bot.getLocation() : null);
                org.bukkit.Bukkit.getPluginManager().callEvent(navFailEvt);
              }
              if (request.onPathFailure() != null) request.onPathFailure().run();
              else if (request.onCancel() != null) request.onCancel().run();
            } else {
              FakePlayer navFp = manager.getByUuid(botUuid);
              if (navFp != null) {
                var navCancelEvt = new me.bill.fakePlayerPlugin.api.event.FppBotNavigationEvent(
                    new me.bill.fakePlayerPlugin.api.impl.FppBotImpl(navFp),
                    me.bill.fakePlayerPlugin.api.event.FppBotNavigationEvent.Action.CANCEL,
                    bot != null ? bot.getLocation() : null);
                org.bukkit.Bukkit.getPluginManager().callEvent(navCancelEvt);
              }
              if (request.onCancel() != null) request.onCancel().run();
            }
          }

          private void walkToward(Player bot, Location target, double dist) {
            Location bl = bot.getLocation();
            float yaw = stableYaw(bot.getLocation().getYaw(), target.getX() - bl.getX(), target.getZ() - bl.getZ());
            bot.setRotation(yaw, 0f);
            NmsPlayerSpawner.setHeadYaw(bot, yaw);
            // Check for a wall directly ahead — if foot AND head are blocked, stop moving
            // rather than walking into the wall.
            World w = bl.getWorld();
            int fx = (int) Math.floor(bl.getX() + Math.sin(-Math.toRadians(yaw)) * 0.8);
            int fz = (int) Math.floor(bl.getZ() + Math.cos(-Math.toRadians(yaw)) * 0.8);
            int fy = bl.getBlockY();
            boolean feetBlocked = !BotPathfinder.canPassThrough(w, fx, fy, fz);
            boolean headBlocked = !BotPathfinder.canPassThrough(w, fx, fy + 1, fz);
            if (feetBlocked && headBlocked) {
              // Full wall ahead — stop and let the replan/detour handle it.
              NmsPlayerSpawner.setMovementForward(bot, 0f);
              bot.setSprinting(false);
              return;
            }
            bot.setSprinting(dist > Config.pathfindingSprintDistance());
            NmsPlayerSpawner.setMovementForward(bot, 1.0f);
            // Single-block step ahead — jump over it.
            if (!bot.isInWater() && !bot.isInLava()) {
              if (feetBlocked && !headBlocked) {
                manager.requestNavJump(botUuid);
              }
            }
          }
        };

    int taskId = FppScheduler.runAtEntityRepeatingWithId(plugin, initialBot, tick, startOffset, 1L);
    taskIdRef[0] = taskId;
    sessions.put(botUuid, new Session(request.owner(), taskId));
  }

  public static void tickSwimAi(Player bot, boolean navJump, boolean isNavigating) {
    try {
      tickSwimAiUnsafe(bot, navJump, isNavigating);
    } catch (NullPointerException e) {
      if (isFoliaWorldDataNotReady(e)) {
        NmsPlayerSpawner.setJumping(bot, bot.isInWater() || bot.isInLava());
        return;
      }
      throw e;
    }
  }

  private static void tickSwimAiUnsafe(Player bot, boolean navJump, boolean isNavigating) {
    if (!bot.isInWater() && !bot.isInLava()) return;

    if (isNavigating) {
      if (navJump) {
        NmsPlayerSpawner.setJumping(bot, true);
      }
      return;
    }

    if (navJump) {
      NmsPlayerSpawner.setJumping(bot, true);
      return;
    }

    if (bot.isInLava()) {
      bot.setSprinting(true);
      NmsPlayerSpawner.setJumping(bot, true);
      return;
    }

    Location loc = bot.getLocation();
    World world = bot.getWorld();
    int bx = loc.getBlockX();
    int by = loc.getBlockY();
    int bz = loc.getBlockZ();

    int distToSurface = distanceToSurface(world, bx, by, bz);
    boolean hasCeiling = hasSolidCeiling(world, bx, by, bz);

    // In shallow water while touching the bottom, passive swim-AI causes jittery
    // jump/sprint toggles. Let normal ground movement handle these transitions.
    if (((org.bukkit.entity.Entity) bot).isOnGround() && distToSurface <= 1) {
      NmsPlayerSpawner.setJumping(bot, false);
      bot.setSprinting(false);
      return;
    }

    if (distToSurface == 0) {
      if (isAtWaterExit(world, bx, by, bz, loc.getYaw())) {
        applySwimExitImpulse(bot);
      }
      NmsPlayerSpawner.setJumping(bot, false);
      bot.setSprinting(false);
      return;
    }

    if (distToSurface <= SWIM_NEAR_SURFACE_THRESHOLD && !hasCeiling) {
      if (distToSurface == 1 && isAtWaterExit(world, bx, by, bz, loc.getYaw())) {
        applySwimExitImpulse(bot);
      }
      NmsPlayerSpawner.setJumping(bot, false);
      bot.setSprinting(false);
      return;
    }

    NmsPlayerSpawner.setJumping(bot, true);
    bot.setSprinting(true);

    if (!isNavigating) {
      Location current = bot.getLocation();
      bot.setRotation(current.getYaw(), -20f);
      NmsPlayerSpawner.setHeadYaw(bot, current.getYaw());
      NmsPlayerSpawner.setMovementForward(bot, 1.0f);
    }
  }

  private static boolean isFoliaWorldDataNotReady(NullPointerException e) {
    String msg = e.getMessage();
    return msg != null && msg.contains("getCurrentWorldData()");
  }

  private void primeInitialMovement(Player bot, Location dest, double arrivalDistance) {
    Location bl = bot.getLocation();
    double initDist = xzDist(bl, dest);
    if (initDist <= arrivalDistance) return;
    float initYaw = stableYaw(bot.getLocation().getYaw(), dest.getX() - bl.getX(), dest.getZ() - bl.getZ());
    bot.setRotation(initYaw, 0f);
    NmsPlayerSpawner.setHeadYaw(bot, initYaw);
    bot.setSprinting(initDist > Config.pathfindingSprintDistance());
    NmsPlayerSpawner.setMovementForward(bot, 1.0f);
  }

  private static float stableYaw(float currentYaw, double dx, double dz) {
    if (dx * dx + dz * dz < MIN_TURN_XZ_DIST * MIN_TURN_XZ_DIST) {
      return currentYaw;
    }
    return (float) Math.toDegrees(Math.atan2(-dx, dz));
  }

  @Nullable
  private static Location safeDestination(@Nullable Location loc) {
    return loc != null ? loc.clone() : null;
  }

  @Nullable
  private static Location findBreakTarget(Location botLoc, BotPathfinder.Move wp) {
    World w = botLoc.getWorld();
    int wx = wp.x(), wy = wp.y(), wz = wp.z();
    if (!w.getBlockAt(wx, wy, wz).isPassable())
      return new Location(w, wx + 0.5, wy + 0.5, wz + 0.5);
    if (!w.getBlockAt(wx, wy + 1, wz).isPassable())
      return new Location(w, wx + 0.5, wy + 1.5, wz + 0.5);
    int by = botLoc.getBlockY();
    int bx = botLoc.getBlockX(), bz = botLoc.getBlockZ();
    if (!w.getBlockAt(bx, by + 2, bz).isPassable())
      return new Location(w, bx + 0.5, by + 2.5, bz + 0.5);
    return null;
  }

  private static void maybeOpenDoorAhead(Player bot, float yaw, Set<OpenableRef> openedDoors) {
    if (bot.isInWater() || bot.isInLava()) return;
    Location bl = bot.getLocation();
    World w = bl.getWorld();
    int fy = bl.getBlockY();
    int bx = bl.getBlockX();
    int bz = bl.getBlockZ();
    tryOpenPassage(w.getBlockAt(bx, fy, bz), openedDoors);
    tryOpenPassage(w.getBlockAt(bx, fy + 1, bz), openedDoors);
    for (double dist : new double[] {0.8, 1.2}) {
      int fx = (int) Math.floor(bl.getX() + Math.sin(-Math.toRadians(yaw)) * dist);
      int fz = (int) Math.floor(bl.getZ() + Math.cos(-Math.toRadians(yaw)) * dist);
      tryOpenPassage(w.getBlockAt(fx, fy, fz), openedDoors);
      tryOpenPassage(w.getBlockAt(fx, fy + 1, fz), openedDoors);
    }
  }

  private static void tryOpenPassage(Block rawBlock, Set<OpenableRef> openedDoors) {
    Block managed = normalizeManagedOpenable(rawBlock);
    if (managed == null) return;
    if (!(managed.getBlockData() instanceof Openable openable)) return;
    if (!openable.isOpen()) {
      setManagedOpenable(managed, true);
    }
    openedDoors.add(new OpenableRef(managed.getWorld().getUID(), managed.getX(), managed.getY(), managed.getZ()));
  }

  @Nullable
  private static Block normalizeManagedOpenable(Block block) {
    if (block.getBlockData() instanceof Gate) return block;
    if (!(block.getBlockData() instanceof Door doorData)) return null;
    if (doorData.getHalf() == Bisected.Half.TOP) {
      Block lower = block.getRelative(0, -1, 0);
      return lower.getBlockData() instanceof Door ? lower : null;
    }
    return block;
  }

  private static void setManagedOpenable(Block base, boolean open) {
    if (base.getBlockData() instanceof Gate gateData) {
      if (gateData.isOpen() != open) {
        gateData.setOpen(open);
        base.setBlockData(gateData, true);
        playOpenableSound(base, open);
      }
      return;
    }
    if (!(base.getBlockData() instanceof Door lowerData)) return;
    if (lowerData.isOpen() != open) {
      lowerData.setOpen(open);
      base.setBlockData(lowerData, true);
      playOpenableSound(base, open);
    }
    Block upper = base.getRelative(0, 1, 0);
    if (upper.getBlockData() instanceof Door upperData && upperData.isOpen() != open) {
      upperData.setOpen(open);
      upper.setBlockData(upperData, true);
    }
  }

  private static void closeOpenedDoors(Set<OpenableRef> openedDoors, @Nullable Location botLoc, boolean force) {
    if (openedDoors.isEmpty()) return;
    for (OpenableRef ref : Set.copyOf(openedDoors)) {
      World w = Bukkit.getWorld(ref.worldId());
      if (w == null) {
        openedDoors.remove(ref);
        continue;
      }
      Block managed = w.getBlockAt(ref.x(), ref.y(), ref.z());
      if (!(managed.getBlockData() instanceof Openable openable)) {
        openedDoors.remove(ref);
        continue;
      }
      if (!openable.isOpen()) {
        openedDoors.remove(ref);
        continue;
      }

      if (!force && botLoc != null && botLoc.getWorld() != null) {
        if (!botLoc.getWorld().getUID().equals(ref.worldId())) {
          continue;
        }
        double dx = (ref.x() + 0.5) - botLoc.getX();
        double dy = (ref.y() + 0.5) - botLoc.getY();
        double dz = (ref.z() + 0.5) - botLoc.getZ();
        if (dx * dx + dy * dy + dz * dz < 9.0) {
          continue;
        }
      }

      setManagedOpenable(managed, false);
      openedDoors.remove(ref);
    }
  }

  private static void applySwimExitImpulse(Player bot) {
    Vector vel = bot.getVelocity();
    if (vel.getY() < 0.2) {
      vel.setY(0.42);
      bot.setVelocity(vel);
    }
  }

  private static void applyClimbMovement(
      Player bot, Location botLoc, double targetX, double targetZ, int targetBlockY) {
    ClimbAnchor anchor = resolveClimbAnchor(botLoc, targetX, targetZ);
    double dx = anchor.x() - botLoc.getX();
    double dz = anchor.z() - botLoc.getZ();
    double xzDist = xzDistRaw(botLoc.getX(), botLoc.getZ(), anchor.x(), anchor.z());
    float yaw = anchor.yaw();

    bot.setRotation(yaw, 0f);
    NmsPlayerSpawner.setHeadYaw(bot, yaw);
    bot.setSprinting(false);
    NmsPlayerSpawner.setMovementForward(bot, xzDist > 0.12 ? 1.0f : 0.7f);
    NmsPlayerSpawner.setMovementStrafe(bot, 0f);

    Vector vel = bot.getVelocity();
    vel.setX(clamp(dx * 0.72, -0.18, 0.18));
    vel.setZ(clamp(dz * 0.72, -0.18, 0.18));

    double currentY = botLoc.getY();
    if (targetBlockY > currentY + 0.2) {
      vel.setY(Math.max(vel.getY(), 0.28));
      NmsPlayerSpawner.setJumping(bot, true);
    } else if (targetBlockY < currentY - 0.2) {
      vel.setY(Math.min(vel.getY(), -0.18));
      NmsPlayerSpawner.setJumping(bot, false);
    } else {
      vel.setY(Math.max(vel.getY(), 0.10));
      NmsPlayerSpawner.setJumping(bot, xzDist > 0.14);
    }

    bot.setVelocity(vel);
  }

  private static void applyBubbleElevatorMovement(
      Player bot,
      Location botLoc,
      double targetX,
      double targetZ,
      int targetBlockY,
      BotPathfinder.MoveType type) {
    double dx = targetX - botLoc.getX();
    double dz = targetZ - botLoc.getZ();
    float yaw =
        (float) Math.toDegrees(Math.atan2(-dx, dz));
    bot.setRotation(yaw, type == BotPathfinder.MoveType.ELEVATOR_UP ? -18f : 22f);
    NmsPlayerSpawner.setHeadYaw(bot, yaw);
    bot.setSprinting(false);
    NmsPlayerSpawner.setMovementForward(bot, 0f);
    NmsPlayerSpawner.setMovementStrafe(bot, 0f);

    Vector vel = bot.getVelocity();
    vel.setX(clamp(dx * 0.60, -0.12, 0.12));
    vel.setZ(clamp(dz * 0.60, -0.12, 0.12));
    if (type == BotPathfinder.MoveType.ELEVATOR_UP) {
      vel.setY(Math.max(vel.getY(), botLoc.getY() < targetBlockY ? 0.26 : 0.12));
      NmsPlayerSpawner.setJumping(bot, false);
    } else {
      vel.setY(Math.min(vel.getY(), botLoc.getY() > targetBlockY ? -0.30 : -0.10));
      NmsPlayerSpawner.setJumping(bot, false);
    }
    bot.setVelocity(vel);
  }

  public static BotPathfinder.PathOptions resolvePathOptions(@NotNull FakePlayer fp) {
    return resolvePathOptions(fp, null);
  }

  public static BotPathfinder.PathOptions resolvePathOptions(
      @NotNull FakePlayer fp, @Nullable BotPathfinder.PathOptions overrideOpts) {
    if (overrideOpts != null) {
      return overrideOpts;
    }
    return new BotPathfinder.PathOptions(
        fp.isNavParkour(),
        fp.isNavBreakBlocks(),
        fp.isNavPlaceBlocks(),
        fp.isNavAvoidWater() || fp.isDefaultWaterPathAvoidanceEnabled(),
        fp.isNavAvoidLava() || !fp.isSwimAiEnabled());
  }

  private static int recalcIntervalTicks(@NotNull Owner owner) {
    return owner == Owner.FOLLOW
        ? Config.pathfindingFollowRecalcInterval()
        : Config.pathfindingRecalcInterval();
  }

  private static boolean shouldRecalculateForTargetShift(
      @NotNull NavigationRequest request,
      @Nullable List<BotPathfinder.Move> currentPath,
      int waypointIndex,
      @NotNull Location dest) {
    if (request.owner() != Owner.FOLLOW
        || currentPath == null
        || currentPath.isEmpty()
        || waypointIndex >= currentPath.size()) {
      return true;
    }

    BotPathfinder.Move finalMove = currentPath.get(currentPath.size() - 1);
    double endX = finalMove.x() + 0.5;
    double endZ = finalMove.z() + 0.5;
    double toleratedShift =
        Math.max(request.arrivalDistance() + 0.75, request.recalcDistance() * 0.5);
    double dx = endX - dest.getX();
    double dz = endZ - dest.getZ();
    return dx * dx + dz * dz > toleratedShift * toleratedShift;
  }

  private static boolean isInClimbableColumn(Location loc) {
    World world = loc.getWorld();
    if (world == null) return false;
    int x = loc.getBlockX();
    int y = loc.getBlockY();
    int z = loc.getBlockZ();
    return isClimbable(world, x, y, z)
        || isClimbable(world, x, y + 1, z)
        || isClimbable(world, x, y - 1, z);
  }

  private static boolean isClimbable(World world, int x, int y, int z) {
    if (y < world.getMinHeight() || y > world.getMaxHeight()) return false;
    return Tag.CLIMBABLE.isTagged(world.getBlockAt(x, y, z).getType());
  }

  private record ClimbAnchor(double x, double z, float yaw) {}

  private static ClimbAnchor resolveClimbAnchor(Location botLoc, double fallbackX, double fallbackZ) {
    World world = botLoc.getWorld();
    if (world == null) {
      float yaw =
          (float) Math.toDegrees(Math.atan2(-(fallbackX - botLoc.getX()), fallbackZ - botLoc.getZ()));
      return new ClimbAnchor(fallbackX, fallbackZ, yaw);
    }

    Block climbBlock = findNearestClimbBlock(world, botLoc.getBlockX(), botLoc.getBlockY(), botLoc.getBlockZ());
    if (climbBlock == null) {
      float yaw =
          (float) Math.toDegrees(Math.atan2(-(fallbackX - botLoc.getX()), fallbackZ - botLoc.getZ()));
      return new ClimbAnchor(fallbackX, fallbackZ, yaw);
    }

    double anchorX = climbBlock.getX() + 0.5;
    double anchorZ = climbBlock.getZ() + 0.5;
    BlockFace supportFace = findClimbSupportFace(climbBlock);
    float yaw;
    if (supportFace != null) {
      anchorX += supportFace.getModX() * 0.31;
      anchorZ += supportFace.getModZ() * 0.31;
      yaw =
          (float)
              Math.toDegrees(
                  Math.atan2(
                      -supportFace.getModX(),
                      -supportFace.getModZ()));
    } else {
      yaw = (float) Math.toDegrees(Math.atan2(-(anchorX - botLoc.getX()), anchorZ - botLoc.getZ()));
    }
    return new ClimbAnchor(anchorX, anchorZ, yaw);
  }

  @Nullable
  private static Block findNearestClimbBlock(World world, int x, int y, int z) {
    Block best = null;
    double bestScore = Double.MAX_VALUE;
    for (int dy = -1; dy <= 2; dy++) {
      for (int dx = -1; dx <= 2; dx++) {
        for (int dz = -1; dz <= 2; dz++) {
          Block block = world.getBlockAt(x + dx, y + dy, z + dz);
          if (!Tag.CLIMBABLE.isTagged(block.getType())) {
            continue;
          }
          double score = Math.abs(dx) * Math.abs(dx) + Math.abs(dz) * Math.abs(dz) + (Math.abs(dy) * 0.18);
          if (score < bestScore) {
            bestScore = score;
            best = block;
          }
        }
      }
    }
    return best;
  }

  @Nullable
  private static BlockFace findClimbSupportFace(Block climbBlock) {
    if (climbBlock.getBlockData() instanceof Directional directional) {
      BlockFace facing = directional.getFacing();
      if (facing == BlockFace.NORTH
          || facing == BlockFace.SOUTH
          || facing == BlockFace.EAST
          || facing == BlockFace.WEST) {
        return facing.getOppositeFace();
      }
    }
    if (climbBlock.getBlockData() instanceof MultipleFacing multipleFacing) {
      for (BlockFace face : new BlockFace[] {BlockFace.NORTH, BlockFace.SOUTH, BlockFace.EAST, BlockFace.WEST}) {
        if (multipleFacing.hasFace(face)) {
          return face;
        }
      }
    }
    BlockFace best = null;
    double bestScore = Double.MAX_VALUE;
    for (BlockFace face : new BlockFace[] {BlockFace.NORTH, BlockFace.SOUTH, BlockFace.EAST, BlockFace.WEST}) {
      Block adjacent = climbBlock.getRelative(face);
      if (!adjacent.getType().isSolid()) {
        continue;
      }
      double score = adjacent.getType().isOccluding() ? 0.0 : 1.0;
      if (score < bestScore) {
        bestScore = score;
        best = face;
      }
    }
    return best;
  }

  private static void playOpenableSound(Block base, boolean open) {
    World world = base.getWorld();
    Location soundLoc = base.getLocation().add(0.5, 0.5, 0.5);
    Material type = base.getType();

    Sound sound;
    if (type.name().contains("FENCE_GATE")) {
      sound = open ? Sound.BLOCK_FENCE_GATE_OPEN : Sound.BLOCK_FENCE_GATE_CLOSE;
    } else if (type == Material.IRON_DOOR) {
      sound = open ? Sound.BLOCK_IRON_DOOR_OPEN : Sound.BLOCK_IRON_DOOR_CLOSE;
    } else if (type.name().contains("COPPER_DOOR")) {
      sound = open ? Sound.BLOCK_COPPER_DOOR_OPEN : Sound.BLOCK_COPPER_DOOR_CLOSE;
    } else {
      sound = open ? Sound.BLOCK_WOODEN_DOOR_OPEN : Sound.BLOCK_WOODEN_DOOR_CLOSE;
    }

    world.playSound(soundLoc, sound, SoundCategory.BLOCKS, 1.0f, 1.0f);
  }

  private static double clamp(double value, double min, double max) {
    return Math.max(min, Math.min(max, value));
  }

  private static boolean applyShorelineExitAssist(Player bot, float yaw, Location botLoc, int targetY) {
    World world = botLoc.getWorld();
    if (world == null) return false;

    double rad = Math.toRadians(yaw);
    int fx = (int) Math.floor(botLoc.getX() + Math.sin(-rad) * 0.9);
    int fz = (int) Math.floor(botLoc.getZ() + Math.cos(rad) * 0.9);
    int fy = botLoc.getBlockY();

    boolean walkableSameLevel = BotPathfinder.walkable(world, fx, fy, fz);
    boolean walkableOneUp = BotPathfinder.walkable(world, fx, fy + 1, fz);
    boolean solidLip = !BotPathfinder.canPassThrough(world, fx, fy, fz)
        && BotPathfinder.canPassThrough(world, fx, fy + 1, fz)
        && BotPathfinder.canPassThrough(world, fx, fy + 2, fz);

    if (!walkableSameLevel && !walkableOneUp && !solidLip && targetY <= fy) return false;

    Vector vel = bot.getVelocity();
    if (vel.getY() < 0.28) {
      vel.setY(0.36);
      bot.setVelocity(vel);
    }
    return true;
  }

  private static boolean isAtWaterExit(World world, int bx, int by, int bz, float yaw) {
    double rad = Math.toRadians(yaw);
    int dx = (int) Math.round(-Math.sin(rad));
    int dz = (int) Math.round(Math.cos(rad));
    Material front = world.getBlockAt(bx + dx, by, bz + dz).getType();
    Material frontAbove = world.getBlockAt(bx + dx, by + 1, bz + dz).getType();
    return front.isSolid() && !frontAbove.isSolid();
  }

  private static int distanceToSurface(World world, int x, int y, int z) {
    for (int dy = 1; dy <= SWIM_SURFACE_SCAN; dy++) {
      int cy = y + dy;
      if (cy >= world.getMaxHeight()) return SWIM_SURFACE_SCAN;
      Material mat = world.getBlockAt(x, cy, z).getType();
      if (mat.isAir()) {
        return dy;
      }
      if (mat != Material.WATER
          && mat != Material.KELP_PLANT
          && mat != Material.KELP
          && mat != Material.SEAGRASS
          && mat != Material.TALL_SEAGRASS
          && mat != Material.BUBBLE_COLUMN) {
        return SWIM_SURFACE_SCAN;
      }
    }
    return SWIM_SURFACE_SCAN;
  }

  private static boolean hasSolidCeiling(World world, int x, int y, int z) {
    for (int dy = 1; dy <= SWIM_CEILING_SCAN; dy++) {
      int cy = y + dy;
      if (cy >= world.getMaxHeight()) return true;
      Material mat = world.getBlockAt(x, cy, z).getType();
      if (mat.isSolid()) return true;
    }
    return false;
  }

  private Material resolvePlaceMaterial() {
    String raw = Config.pathfindingPlaceMaterial();
    if (cachedPlaceMaterial != null && raw.equals(cachedPlaceMaterialName)) {
      return cachedPlaceMaterial;
    }
    cachedPlaceMaterialName = raw;
    Material mat = Material.matchMaterial(raw.toUpperCase());
    cachedPlaceMaterial = (mat != null && mat.isBlock() && mat.isSolid()) ? mat : Material.DIRT;
    return cachedPlaceMaterial;
  }

  public static double xzDist(Location a, Location b) {
    return xzDistRaw(a.getX(), a.getZ(), b.getX(), b.getZ());
  }

  public static double xzDistRaw(double ax, double az, double bx, double bz) {
    double dx = ax - bx;
    double dz = az - bz;
    return Math.sqrt(dx * dx + dz * dz);
  }
}
