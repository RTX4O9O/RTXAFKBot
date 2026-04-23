package me.bill.fakePlayerPlugin.fakeplayer;

import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Supplier;
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
import org.bukkit.util.Vector;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

public final class PathfindingService {

  private static final int SWIM_SURFACE_SCAN = 6;
  private static final int SWIM_CEILING_SCAN = 3;
  private static final int SWIM_NEAR_SURFACE_THRESHOLD = 2;

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
      @Nullable Location lockOnArrival) {

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
          null);
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
    if (initialDest == null
        || initialDest.getWorld() == null
        || !initialDest.getWorld().equals(initialBot.getWorld())) {
      manager.unlockNavigation(botUuid);
      if (request.onCancel() != null) request.onCancel().run();
      return;
    }

    primeInitialMovement(initialBot, initialDest, request.arrivalDistance());

    @SuppressWarnings("unchecked")
    final List<BotPathfinder.Move>[] pathRef = (List<BotPathfinder.Move>[]) new List<?>[] {null};
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

    // Sprint-jump counter: fires a jump every 6 ticks while sprint-jumping is enabled.
    final int[] sprintJumpTick = {0};

    // Detour state: when A* returns null AND a wall is directly ahead, we compute a detour
    // waypoint and path to it first before resuming toward the real target.
    @SuppressWarnings("unchecked")
    final List<BotPathfinder.Move>[] detourPath = (List<BotPathfinder.Move>[]) new List<?>[] {null};
    final int[] detourWpIdx = {0};
    final int[] detourNullCount = {0};

    BukkitTask task =
        new BukkitRunnable() {
          @Override
          public void run() {

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

            double arrivalSq = request.arrivalDistance() * request.arrivalDistance();
            double dx0 = botLoc.getX() - dest.getX();
            double dz0 = botLoc.getZ() - dest.getZ();
            double distToTargetSq = dx0 * dx0 + dz0 * dz0;
            if (distToTargetSq <= arrivalSq) {
              cleanup(bot, true, false);
              return;
            }

            double distToTarget = Math.sqrt(distToTargetSq);

            boolean targetMoved =
                request.recalcDistance() > 0
                    && lastCalc[0] != null
                    && lastCalc[0].distanceSquared(dest)
                        > request.recalcDistance() * request.recalcDistance();
            boolean pathExhausted = (pathRef[0] == null || wpIdx[0] >= pathRef[0].size());
            boolean heartbeat = (--recalcIn[0] <= 0);

            if (targetMoved || pathExhausted || heartbeat) {
              recalcIn[0] = Config.pathfindingRecalcInterval();

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

              BotPathfinder.PathOptions opts =
                  new BotPathfinder.PathOptions(
                      fp.isNavParkour(), fp.isNavBreakBlocks(), fp.isNavPlaceBlocks());

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
                // Direct path failed — try to compute a detour around the obstacle.
                BotPathfinder.PathOptions detourOpts =
                    new BotPathfinder.PathOptions(
                        fp.isNavParkour(), fp.isNavBreakBlocks(), fp.isNavPlaceBlocks());
                int[] dg = BotPathfinder.findDetourGoal(
                    botLoc.getWorld(),
                    botLoc.getBlockX(), botLoc.getBlockY(), botLoc.getBlockZ(),
                    dest.getBlockX(), dest.getBlockY(), dest.getBlockZ(),
                    Config.pathfindingDetourAttempts(),
                    Config.pathfindingDetourRadius() / (double) Config.pathfindingDetourAttempts(),
                    detourOpts);
                if (dg != null) {
                  detourPath[0] = BotPathfinder.findPathMoves(
                      botLoc.getWorld(),
                      botLoc.getBlockX(), botLoc.getBlockY(), botLoc.getBlockZ(),
                      dg[0], dg[1], dg[2], detourOpts);
                  detourWpIdx[0] = (detourPath[0] != null && detourPath[0].size() > 1) ? 1 : 0;
                } else {
                  detourPath[0] = null;
                  detourWpIdx[0] = 0;
                }
              } else {
                nullPathRecalcs[0] = 0;
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
                float dYaw = (float) Math.toDegrees(Math.atan2(-ddx, ddz));
                bot.setRotation(dYaw, 0f);
                NmsPlayerSpawner.setHeadYaw(bot, dYaw);
                bot.setSprinting(distToTarget > Config.pathfindingSprintDistance());
                NmsPlayerSpawner.setMovementForward(bot, 1.0f);
                if (!bot.isInWater() && !bot.isInLava() && dwp.y() > botLoc.getBlockY()) {
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
              float coastYaw = (float) Math.toDegrees(Math.atan2(-dx, dz));
              bot.setRotation(coastYaw, 0f);
              NmsPlayerSpawner.setHeadYaw(bot, coastYaw);
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

            if (wp.type() == BotPathfinder.MoveType.SWIM) {
              double sdx = wpCX - botLoc.getX();
              double sdz = wpCZ - botLoc.getZ();
              double swimDist = xzDistRaw(botLoc.getX(), botLoc.getZ(), wpCX, wpCZ);
              double swimYDist = Math.abs(botLoc.getY() - wp.y());
              boolean verticalSwimStep = swimDist < 0.25;
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
                shouldJump = bot.isInWater() || bot.isInLava();
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

              if (swimDist < 0.8 && swimYDist < 1.5) {
                wpIdx[0]++;
              }
              return;
            }

            double wpXZDist = xzDistRaw(botLoc.getX(), botLoc.getZ(), wpCX, wpCZ);
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
                float coastYaw = (float) Math.toDegrees(Math.atan2(-cdx, cdz));
                bot.setRotation(coastYaw, 0f);
                NmsPlayerSpawner.setHeadYaw(bot, coastYaw);
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
            float yaw = (float) Math.toDegrees(Math.atan2(-dx, dz));
            bot.setRotation(yaw, 0f);
            NmsPlayerSpawner.setHeadYaw(bot, yaw);

            bot.setSprinting(
                distToTarget > Config.pathfindingSprintDistance()
                    || wp.type() == BotPathfinder.MoveType.PARKOUR);
            NmsPlayerSpawner.setMovementForward(bot, 1.0f);

            if (!bot.isInWater() && !bot.isInLava()) {
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
              } else if (fp.isNavSprintJump() && bot.isSprinting()) {
                // Sprint-jump: fire a jump every 6 ticks while on flat ground and sprinting.
                // Only jump when the next waypoint is at the same Y (not already ascending).
                if (wp.y() == botLoc.getBlockY() && bot.isOnGround()) {
                  if (++sprintJumpTick[0] >= 6) {
                    sprintJumpTick[0] = 0;
                    manager.requestNavJump(botUuid);
                  }
                } else {
                  sprintJumpTick[0] = 0;
                }
              }
            }

            double moved = xzDistRaw(botLoc.getX(), botLoc.getZ(), prevX[0], prevZ[0]);
            if (moved < Config.pathfindingStuckThreshold()) {
              if (++stuckFor[0] >= Config.pathfindingStuckTicks()) {
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
                  if (feetBlocked && !headBlocked && aboveClear) {
                    // Single-block step — jump over it.
                    manager.requestNavJump(botUuid);
                  }
                  // Whether or not we jumped, force an immediate replan so the detour
                  // logic gets a chance to route around the obstacle.
                  recalcIn[0] = 0;
                }
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
            float yaw =
                (float)
                    Math.toDegrees(
                        Math.atan2(-(target.getX() - bl.getX()), target.getZ() - bl.getZ()));
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
        }.runTaskTimer(plugin, 0L, 1L);

    sessions.put(botUuid, new Session(request.owner(), task));
  }

  public static void tickSwimAi(Player bot, boolean navJump, boolean isNavigating) {
    if (!bot.isInWater() && !bot.isInLava()) return;

    if (navJump) {
      NmsPlayerSpawner.setJumping(bot, true);
      return;
    }

    if (bot.isInLava()) {
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

    if (distToSurface == 0) {
      if (isAtWaterExit(world, bx, by, bz, loc.getYaw())) {
        applySwimExitImpulse(bot);
      }
      NmsPlayerSpawner.setJumping(bot, false);
      return;
    }

    if (distToSurface <= SWIM_NEAR_SURFACE_THRESHOLD && !hasCeiling) {
      if (distToSurface == 1 && isAtWaterExit(world, bx, by, bz, loc.getYaw())) {
        applySwimExitImpulse(bot);
      }
      NmsPlayerSpawner.setJumping(bot, false);
      return;
    }

    NmsPlayerSpawner.setJumping(bot, true);

    if (!isNavigating) {
      Location current = bot.getLocation();
      bot.setRotation(current.getYaw(), -20f);
      NmsPlayerSpawner.setHeadYaw(bot, current.getYaw());
      bot.setSprinting(true);
      NmsPlayerSpawner.setMovementForward(bot, 1.0f);
    }
  }

  private void primeInitialMovement(Player bot, Location dest, double arrivalDistance) {
    Location bl = bot.getLocation();
    double initDist = xzDist(bl, dest);
    if (initDist <= arrivalDistance) return;
    float initYaw =
        (float) Math.toDegrees(Math.atan2(-(dest.getX() - bl.getX()), dest.getZ() - bl.getZ()));
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

  private static void applySwimExitImpulse(Player bot) {
    Vector vel = bot.getVelocity();
    if (vel.getY() < 0.2) {
      vel.setY(0.42);
      bot.setVelocity(vel);
    }
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
