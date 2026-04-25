package me.bill.fakePlayerPlugin.fakeplayer;

import java.util.*;
import me.bill.fakePlayerPlugin.config.Config;
import org.bukkit.Material;
import org.bukkit.Tag;
import org.bukkit.World;
import org.bukkit.block.Block;
import org.bukkit.block.data.Waterlogged;
import org.bukkit.block.data.type.Door;
import org.bukkit.block.data.type.Fence;
import org.bukkit.block.data.type.Gate;
import org.bukkit.block.data.type.Slab;
import org.bukkit.block.data.type.TrapDoor;
import org.bukkit.block.data.type.Wall;

public final class BotPathfinder {

  private BotPathfinder() {}

  // Cost model adapted from Baritone's 1.21 movement timing math.
  private static final double WALK = 20.0 / 4.317;
  private static final double DIAGONAL = WALK * Math.sqrt(2.0);
  private static final double CLIMB_UP = 20.0 / 2.35;
  private static final double CLIMB_DOWN = 20.0 / 3.0;
  private static final double SWIM = 20.0 / 2.2;
  private static final double WALK_OFF_BLOCK_COST = WALK * 0.8;
  private static final double CENTER_AFTER_FALL_COST = WALK - WALK_OFF_BLOCK_COST;
  private static final double FALL_1_25_BLOCKS_COST = distanceToTicks(1.25);
  private static final double FALL_0_25_BLOCKS_COST = distanceToTicks(0.25);
  private static final double ASCEND = FALL_1_25_BLOCKS_COST - FALL_0_25_BLOCKS_COST + 2.0;
  private static final double PARKOUR_C = ASCEND + WALK * 2.0;
  private static final double BREAK_C = WALK * 6.0;
  private static final double PLACE_C = WALK * 4.0;
  private static final double PILLAR_C = ASCEND + PLACE_C;
  private static final double WATER_PEN = SWIM - WALK;
  private static final double SLOW_BLOCK_PEN = WALK;

  private static final Set<Material> HAZARDS =
      EnumSet.of(
          Material.LAVA,
          Material.FIRE,
          Material.SOUL_FIRE,
          Material.CACTUS,
          Material.SWEET_BERRY_BUSH,
          Material.MAGMA_BLOCK,
          Material.CAMPFIRE,
          Material.SOUL_CAMPFIRE,
          Material.WITHER_ROSE,
          Material.POWDER_SNOW,
          Material.POINTED_DRIPSTONE);

  private static final Set<Material> SLOW_BLOCKS =
      EnumSet.of(Material.SOUL_SAND, Material.HONEY_BLOCK, Material.COBWEB);

  private static final int[][] DIRS = {
    {1, 0}, {-1, 0},
    {0, 1}, {0, -1},
    {1, 1}, {1, -1},
    {-1, 1}, {-1, -1}
  };

  private static final int[][] CARDINAL = {{1, 0}, {-1, 0}, {0, 1}, {0, -1}};

  public record Pos(int x, int y, int z) {}

  public enum MoveType {
    WALK,
    ASCEND,
    CLIMB,
    DESCEND,
    ELEVATOR_UP,
    ELEVATOR_DOWN,
    PARKOUR,
    BREAK,
    PLACE,
    PILLAR,
    SWIM
  }

  public record Move(int x, int y, int z, MoveType type) {
    public Pos toPos() {
      return new Pos(x, y, z);
    }
  }

  public record PathOptions(
      boolean parkour,
      boolean breakBlocks,
      boolean placeBlocks,
      boolean avoidWater,
      boolean avoidLava) {
    public static final PathOptions DEFAULT = new PathOptions(false, false, false, false, false);

    public boolean anyEnabled() {
      return parkour || breakBlocks || placeBlocks;
    }
  }

  private record Node(Pos pos, Node parent, double g, double h, MoveType action) {
    double f() {
      return g + h;
    }
  }

  private record Neighbor(int x, int y, int z, double cost, MoveType type) {}

  /**
   * Attempts to find a detour waypoint when the direct A* path between (sx,sy,sz) and (tx,ty,tz)
   * fails (e.g. blocked by a wall). Sweeps perpendicular offsets left and right of the direct line
   * at increasing radii, then tries a combination of forward-offset + lateral, returning the first
   * walkable candidate that A* can actually reach. Returns null if no detour is found.
   *
   * @param attempts number of lateral offset steps to try on each side (config: pathfinding.detour-attempts)
   * @param stepSize lateral step size in blocks per attempt (config: pathfinding.detour-radius / attempts)
   */
  public static int[] findDetourGoal(
      World world,
      int sx, int sy, int sz,
      int tx, int ty, int tz,
      int attempts,
      double stepSize,
      PathOptions opts) {

    double dx = tx - sx;
    double dz = tz - sz;
    double dist = Math.sqrt(dx * dx + dz * dz);
    if (dist < 0.001) return null;

    // Unit vector toward target, and perpendicular (right = rotate 90° CW)
    double ux = dx / dist;
    double uz = dz / dist;
    double rx = uz;   // perpendicular right
    double rz = -ux;

    // How far forward to place the detour goal (60-80 % of the distance, capped at max-range*0.8)
    int configuredMaxRange = Config.pathfindingMaxRange();
    double fwdDist = Math.min(dist * 0.7, configuredMaxRange * 0.8);

    int fwdX = sx + (int) Math.round(ux * fwdDist);
    int fwdZ = sz + (int) Math.round(uz * fwdDist);
    int fwdY = sy + (int) Math.round((ty - sy) * 0.7);

    for (int attempt = 1; attempt <= attempts; attempt++) {
      double offset = attempt * stepSize;

      // Try right then left
      for (int sign : new int[]{1, -1}) {
        int cx = fwdX + (int) Math.round(rx * offset * sign);
        int cz = fwdZ + (int) Math.round(rz * offset * sign);

        // Snap candidate to a walkable Y within ±4 of projected Y
        Integer cy = snapWalkableY(world, cx, fwdY, cz);
        if (cy == null) continue;

        // Verify A* can actually path from start to this candidate
        List<Move> testPath = findPathMoves(world, sx, sy, sz, cx, cy, cz, opts);
        if (testPath != null && testPath.size() > 1) {
          return new int[]{cx, cy, cz};
        }
      }

      // Also try pure lateral (no forward component) at larger offsets
      if (attempt >= 2) {
        double latOffset = attempt * stepSize * 1.5;
        for (int sign : new int[]{1, -1}) {
          int cx = sx + (int) Math.round(rx * latOffset * sign);
          int cz = sz + (int) Math.round(rz * latOffset * sign);
          Integer cy = snapWalkableY(world, cx, sy, cz);
          if (cy == null) continue;
          List<Move> testPath = findPathMoves(world, sx, sy, sz, cx, cy, cz, opts);
          if (testPath != null && testPath.size() > 1) {
            return new int[]{cx, cy, cz};
          }
        }
      }
    }
    return null;
  }

  /**
   * Snaps the given (x, z) to the nearest walkable Y within ±4 blocks of baseY.
   * Returns null if no walkable position found.
   */
  private static Integer snapWalkableY(World world, int x, int baseY, int z) {
    for (int off = 0; off <= 4; off++) {
      for (int sign : (off == 0 ? new int[]{0} : new int[]{off, -off})) {
        int cy = baseY + sign;
        if (!inBounds(world, cy)) continue;
        if (walkable(world, x, cy, z)) return cy;
      }
    }
    return null;
  }

  /**
   * Projects an intermediate waypoint toward (tx,ty,tz) that is within max-range of the bot.
   * Used by PathfindingService when the true destination is farther than the A* search radius.
   * Returns null if neither the projected point nor any fallback in a ±4-block Y sweep is walkable.
   */
  public static int[] intermediateGoal(
      World world, int sx, int sy, int sz, int tx, int ty, int tz) {
    int configuredMaxRange = Config.pathfindingMaxRange();
    double dx = tx - sx, dy = ty - sy, dz = tz - sz;
    double dist = Math.sqrt(dx * dx + dz * dz);
    if (dist <= configuredMaxRange) {
      return new int[] {tx, ty, tz};
    }
    // Scale back to 80% of max-range so A* has room to manoeuvre.
    double scale = (configuredMaxRange * 0.80) / dist;
    int ix = sx + (int) Math.round(dx * scale);
    int iz = sz + (int) Math.round(dz * scale);

    // Use the real surface Y at the projected XZ instead of linear interpolation.
    // Linear interpolation produces wildly wrong Y values when terrain changes height,
    // causing snap() to fail and the bot to path to an underground/airborne waypoint.
    int iy;
    if (world.isChunkLoaded(ix >> 4, iz >> 4)) {
      iy = world.getHighestBlockYAt(ix, iz);
    } else {
      // Chunk not loaded — fall back to linear interpolation; snap() will correct within ±3.
      iy = sy + (int) Math.round(dy * scale);
    }

    // Try surface Y first, then ±4 blocks for overhangs/caves.
    for (int off = 0; off <= 4; off++) {
      for (int sign : (off == 0 ? new int[] {0} : new int[] {off, -off})) {
        int cy = iy + sign;
        if (walkable(world, ix, cy, iz)) return new int[] {ix, cy, iz};
      }
    }
    // Last resort: return the surface Y anyway so snap() has the best starting point.
    return new int[] {ix, iy, iz};
  }

  public static List<Move> findPathMoves(
      World world, int sx, int sy, int sz, int tx, int ty, int tz, PathOptions opts) {

    int configuredMaxRange = Config.pathfindingMaxRange();

    // If destination is out of A* range, redirect to an intermediate waypoint.
    double xzDist = Math.sqrt((double)(sx-tx)*(sx-tx) + (double)(sz-tz)*(sz-tz));
    if (xzDist > configuredMaxRange) {
      int[] inter = intermediateGoal(world, sx, sy, sz, tx, ty, tz);
      tx = inter[0]; ty = inter[1]; tz = inter[2];
    }

    Pos start = snap(world, sx, sy, sz, opts, true);
    if (start == null) return null;

    int nodeLimit =
        opts.anyEnabled() ? Config.pathfindingMaxNodesExtended() : Config.pathfindingMaxNodes();

    for (Pos goal : prioritizedGoals(world, tx, ty, tz, opts)) {
      if (start.equals(goal)) {
        return List.of(new Move(start.x(), start.y(), start.z(), MoveType.WALK));
      }
      List<Move> path = findPathMovesInternal(world, start, goal, opts, nodeLimit);
      if (path != null) {
        return path;
      }
    }

    return null;
  }

  public static List<Pos> findPath(
      World world, int sx, int sy, int sz, int tx, int ty, int tz, PathOptions opts) {
    List<Move> moves = findPathMoves(world, sx, sy, sz, tx, ty, tz, opts);
    if (moves == null) return null;
    List<Pos> result = new ArrayList<>(moves.size());
    for (Move m : moves) result.add(m.toPos());
    return result;
  }

  // Key layout (64 bits):
  //   bits 63-38  : x + 33554432   (26 bits, covers ±30M blocks)
  //   bits 37-12  : z + 33554432   (26 bits, covers ±30M blocks)
  //   bits 11-0   : y + 2048       (12 bits, covers y -2048..+2047)
  private static final long X_BIAS = 33_554_432L; // 2^25
  private static final long Z_BIAS = 33_554_432L;
  private static final long Y_BIAS = 2048L;

  private static long posKey(int x, int y, int z) {
    return ((long) (x + X_BIAS) << 38) | ((long) (z + Z_BIAS) << 12) | (long) (y + Y_BIAS);
  }

  private static List<Neighbor> neighbors(World world, int x, int y, int z, PathOptions opts) {
    List<Neighbor> out = new ArrayList<>(48);

    int maxFall = Config.pathfindingMaxFall();

    for (int[] d : DIRS) {
      int dx = d[0], dz = d[1];
      int nx = x + dx, nz = z + dz;
      boolean isDiag = (dx != 0 && dz != 0);
      double base = isDiag ? DIAGONAL : WALK;

      if (isDiag) {
        if (blocksDiagonal(world, x + dx, y, z)
            || blocksDiagonal(world, x + dx, y + 1, z)
            || blocksDiagonal(world, x, y, z + dz)
            || blocksDiagonal(world, x, y + 1, z + dz)) continue;
      }

      boolean feetClear = canPassThrough(world, nx, y, nz);
      boolean headClear = canPassThrough(world, nx, y + 1, nz);
      boolean floorSolid = canStandOn(world, nx, y - 1, nz);
      boolean srcInWater = isWater(world, x, y, z) || isWater(world, x, y + 1, z);
      boolean srcInLava = isLava(world, x, y, z) || isLava(world, x, y + 1, z);
      boolean destInWater = isWater(world, nx, y, nz) || isWater(world, nx, y + 1, nz);
      boolean destInLava =
          isLava(world, nx, y, nz) || isLava(world, nx, y + 1, nz) || isLava(world, nx, y - 1, nz);
      boolean srcClimbable = isClimbablePosition(world, x, y, z);
      boolean destClimbable = isClimbablePosition(world, nx, y, nz);
      boolean destClimbableUp = isClimbablePosition(world, nx, y + 1, nz);
      boolean destClimbableDown = isClimbablePosition(world, nx, y - 1, nz);
      boolean srcBubbleUp = isBubbleElevatorUp(world, x, y, z);
      boolean srcBubbleDown = isBubbleElevatorDown(world, x, y, z);
      boolean destBubble = isBubbleColumn(world, nx, y, nz);

      if (feetClear && headClear && floorSolid) {
        if (opts.avoidWater() && destInWater && !srcInWater) continue;
        if (opts.avoidLava() && destInLava && !srcInLava) continue;
        if (opts.avoidWater() && isNearWater(world, nx, y, nz) && !srcInWater && !destBubble) continue;
        if (opts.avoidLava() && isNearLava(world, nx, y, nz) && !srcInLava) continue;
        double cost = base;

        if (isWater(world, nx, y, nz)) cost += WATER_PEN;
        else if (isSlowBlock(world, nx, y, nz)) cost += SLOW_BLOCK_PEN;

        if (isSafeStandPosition(world, nx, y, nz)) {
          out.add(new Neighbor(nx, y, nz, cost, MoveType.WALK));
        }
      } else if (!isDiag && opts.breakBlocks() && floorSolid) {
        double cost = base;
        if (!feetClear && canBreak(world, nx, y, nz)) cost += BREAK_C;
        else if (!feetClear) continue;
        if (!headClear && canBreak(world, nx, y + 1, nz)) cost += BREAK_C;
        else if (!headClear) continue;
        if (cost > base) {
          out.add(new Neighbor(nx, y, nz, cost, MoveType.BREAK));
        }
      }

      if (!isDiag && (srcClimbable || destClimbable)) {
        if (destClimbable && isSafeStandPosition(world, nx, y, nz)) {
          out.add(new Neighbor(nx, y, nz, base + WALK, MoveType.CLIMB));
        }
      }
      if (!isDiag && destClimbableUp && canOccupyClimbNode(world, nx, y + 1, nz)) {
        out.add(new Neighbor(nx, y + 1, nz, base + CLIMB_UP, MoveType.CLIMB));
      }
      if (!isDiag && srcClimbable && destClimbableDown && canOccupyClimbNode(world, nx, y - 1, nz)) {
        out.add(new Neighbor(nx, y - 1, nz, base + CLIMB_DOWN, MoveType.CLIMB));
      }

      if (!isDiag && opts.placeBlocks() && !floorSolid && feetClear && headClear) {

        if (hasAdjacentSolid(world, nx, y - 1, nz)) {
          if (isSafeStandPosition(world, nx, y, nz)) {
            out.add(new Neighbor(nx, y, nz, base + PLACE_C, MoveType.PLACE));
          }
        }
      }

      boolean srcHeadClear = canPassThrough(world, x, y + 2, z);
      boolean tgtFeetClear = canPassThrough(world, nx, y + 1, nz);
      boolean tgtHeadClear = canPassThrough(world, nx, y + 2, nz);
      boolean tgtFloorSolid = canStandOn(world, nx, y, nz);

      if (srcHeadClear && tgtFeetClear && tgtHeadClear && tgtFloorSolid) {
        if (isSafeStandPosition(world, nx, y + 1, nz)) {
          double cost = base + ASCEND;
          if (isSlowBlock(world, x, y, z)) cost += SLOW_BLOCK_PEN;
          out.add(new Neighbor(nx, y + 1, nz, cost, MoveType.ASCEND));
        }
      } else if (!isDiag
          && opts.breakBlocks()
          && tgtFeetClear
          && tgtFloorSolid
          && !srcHeadClear
          && canBreak(world, x, y + 2, z)
          && tgtHeadClear) {
        out.add(new Neighbor(nx, y + 1, nz, base + ASCEND + BREAK_C, MoveType.BREAK));
      }

      if (feetClear && headClear) {
        for (int drop = 1; drop <= maxFall; drop++) {
          int ny = y - drop;
          if (!inBounds(world, ny)) break;
          if (canStandOn(world, nx, ny - 1, nz)
              && canPassThrough(world, nx, ny, nz)
              && canPassThrough(world, nx, ny + 1, nz)) {
            if (isSafeStandPosition(world, nx, ny, nz)) {
              out.add(new Neighbor(nx, ny, nz, base + fallCost(drop), MoveType.DESCEND));
            }
            break;
          }
          if (!canPassThrough(world, nx, ny, nz)) break;
        }
      }

      if ((!opts.avoidWater() || srcInWater)
          && isWater(world, nx, y, nz)
          && isWater(world, nx, y + 1, nz)) {

        out.add(new Neighbor(nx, y, nz, SWIM, MoveType.SWIM));

        if (canPassThrough(world, nx, y + 2, nz) || isWater(world, nx, y + 2, nz)) {
          out.add(new Neighbor(nx, y + 1, nz, SWIM + CLIMB_UP * 0.5, MoveType.SWIM));
        }

        if (isWater(world, nx, y - 1, nz) || canPassThrough(world, nx, y - 1, nz)) {
          out.add(new Neighbor(nx, y - 1, nz, Math.max(1.0, SWIM - 1.0), MoveType.SWIM));
        }
      }

      if (!isDiag && opts.parkour()) {
        tryParkour(world, x, y, z, dx, dz, out, feetClear, headClear);
      }
    }

    if (opts.placeBlocks() && canPassThrough(world, x, y + 2, z)) {

      if (canPassThrough(world, x, y + 1, z) && canPassThrough(world, x, y + 2, z)) {
        out.add(new Neighbor(x, y + 1, z, PILLAR_C, MoveType.PILLAR));
      }
    }

    if ((!opts.avoidWater() || isWater(world, x, y + 1, z)) && isWater(world, x, y, z)) {
      if (isWater(world, x, y + 1, z) || canPassThrough(world, x, y + 1, z)) {
        out.add(new Neighbor(x, y + 1, z, SWIM, MoveType.SWIM));
      }
      if (isWater(world, x, y - 1, z) || canPassThrough(world, x, y - 1, z)) {
        out.add(new Neighbor(x, y - 1, z, Math.max(1.0, SWIM - 1.0), MoveType.SWIM));
      }
    }

    boolean srcClimbable = isClimbablePosition(world, x, y, z);
    if (srcClimbable) {
      if (canOccupyClimbNode(world, x, y + 1, z)) {
        out.add(new Neighbor(x, y + 1, z, CLIMB_UP, MoveType.CLIMB));
      }
      if (canOccupyClimbNode(world, x, y - 1, z)) {
        out.add(new Neighbor(x, y - 1, z, CLIMB_DOWN, MoveType.CLIMB));
      }
    }

    if (isBubbleElevatorUp(world, x, y, z)) {
      if (canOccupyBubbleNode(world, x, y + 1, z)) {
        out.add(new Neighbor(x, y + 1, z, CLIMB_UP * 0.8, MoveType.ELEVATOR_UP));
      }
      if (canOccupyBubbleNode(world, x, y + 2, z)) {
        out.add(new Neighbor(x, y + 2, z, CLIMB_UP * 1.4, MoveType.ELEVATOR_UP));
      }
    }
    if (isBubbleElevatorDown(world, x, y, z)) {
      if (canOccupyBubbleNode(world, x, y - 1, z)) {
        out.add(new Neighbor(x, y - 1, z, CLIMB_DOWN * 0.8, MoveType.ELEVATOR_DOWN));
      }
      if (canOccupyBubbleNode(world, x, y - 2, z)) {
        out.add(new Neighbor(x, y - 2, z, CLIMB_DOWN * 1.4, MoveType.ELEVATOR_DOWN));
      }
    }

    return out;
  }

  private static void tryParkour(
      World world,
      int x,
      int y,
      int z,
      int dx,
      int dz,
      List<Neighbor> out,
      boolean gap1FeetClear,
      boolean gap1HeadClear) {

    if (isSlowBlock(world, x, y - 1, z)) return;
    if (isWater(world, x, y, z)) return;

    if (!canPassThrough(world, x, y + 2, z)) return;

    int maxJump = 4;

    for (int dist = 2; dist <= maxJump; dist++) {
      int gx = x + dx * (dist - 1), gz = z + dz * (dist - 1);

      if (!canPassThrough(world, gx, y, gz)
          || !canPassThrough(world, gx, y + 1, gz)
          || !canPassThrough(world, gx, y + 2, gz)) break;

      if (dist == 2 && !gap1FeetClear) break;

      int lx = x + dx * dist, lz = z + dz * dist;

      if (!canPassThrough(world, lx, y, lz) || !canPassThrough(world, lx, y + 1, lz)) {

        if (dist <= 3
            && canStandOn(world, lx, y, lz)
            && canPassThrough(world, lx, y + 1, lz)
            && canPassThrough(world, lx, y + 2, lz)) {
          if (!isHazard(world, lx, y + 1, lz)) {
            out.add(new Neighbor(lx, y + 1, lz, WALK * dist + PARKOUR_C + ASCEND, MoveType.PARKOUR));
          }
        }
        break;
      }

      if (canStandOn(world, lx, y - 1, lz)) {
        if (!isHazard(world, lx, y, lz) && !isHazard(world, lx, y - 1, lz)) {
          out.add(new Neighbor(lx, y, lz, WALK * dist + PARKOUR_C, MoveType.PARKOUR));
        }
      }
    }
  }

  public static boolean canPassThrough(World world, int x, int y, int z) {
    if (y < world.getMinHeight() || y > world.getMaxHeight()) return true;
    try {
      if (!world.isChunkLoaded(x >> 4, z >> 4)) {
        return true;
      }
      Block block = world.getBlockAt(x, y, z);
      Material mat = block.getType();

      if (mat.isAir()) return true;
      if (Tag.CLIMBABLE.isTagged(mat)) return true;

      if (mat == Material.WATER) return true;

      if (mat == Material.LAVA) return false;

      if (block.getBlockData() instanceof Door) return true;

      if (block.getBlockData() instanceof Fence) return false;
      if (block.getBlockData() instanceof Wall) return false;
      if (mat.name().contains("_WALL")
          || mat == Material.COBBLESTONE_WALL
          || mat == Material.MOSSY_COBBLESTONE_WALL) return false;

      if (block.getBlockData() instanceof Gate gate) {
        return true;
      }

      if (block.getBlockData() instanceof TrapDoor trapDoor) {
        return trapDoor.isOpen();
      }

      if (block.getBlockData() instanceof Slab slab) {
        return false;
      }

      if (mat == Material.COBWEB) return false;

      return block.isPassable();
    } catch (Exception e) {
      return false;
    }
  }

  public static boolean canStandOn(World world, int x, int y, int z) {
    if (y < world.getMinHeight() || y > world.getMaxHeight()) return false;
    try {
      if (!world.isChunkLoaded(x >> 4, z >> 4)) {
        return false;
      }
      Block block = world.getBlockAt(x, y, z);
      Material mat = block.getType();

      if (mat.isAir()) return false;

      if (mat.isSolid() && mat.isOccluding()) return true;

      if (block.getBlockData() instanceof Slab slab) {
        return true;
      }

      if (block.getBlockData() instanceof Gate) return false;

      if (mat.name().contains("CARPET")) return true;

      if (mat.name().contains("STAIRS")) return true;

      // Fences/walls are too tall to "step onto" from ground level; treating them as standable
      // makes A* try to climb/jump them instead of routing around.
      if (block.getBlockData() instanceof Fence) return false;
      if (block.getBlockData() instanceof Wall) return false;

      if (mat == Material.GLASS
          || mat.name().contains("STAINED_GLASS") && !mat.name().contains("PANE")) return true;

      if (mat == Material.CHEST
          || mat == Material.TRAPPED_CHEST
          || mat == Material.ENDER_CHEST
          || mat == Material.BARREL) return true;

      if (mat.name().contains("LEAVES")) return true;

      if (mat == Material.FARMLAND || mat == Material.DIRT_PATH || mat == Material.SOUL_SAND)
        return true;

      if (mat == Material.HONEY_BLOCK) return true;

      if (mat.name().contains("_BED")) return true;

      if (mat == Material.SCAFFOLDING) return true;

      if (block.getBlockData() instanceof TrapDoor trapDoor) {
        return !trapDoor.isOpen() && trapDoor.getHalf() == org.bukkit.block.data.Bisected.Half.TOP;
      }

      if (mat == Material.WATER) return false;

      if (mat == Material.MAGMA_BLOCK) return true;

      return !block.isPassable();
    } catch (Exception e) {
      return false;
    }
  }

  public static boolean walkable(World world, int x, int y, int z) {
    if (!inBounds(world, y) || !inBounds(world, y + 1)) return false;
    return (canStandOn(world, x, y - 1, z)
            && canPassThrough(world, x, y, z)
            && canPassThrough(world, x, y + 1, z)
            && isSafeStandPosition(world, x, y, z))
        || isClimbablePosition(world, x, y, z);
  }

  public static boolean passable(World world, int x, int y, int z) {
    return canPassThrough(world, x, y, z);
  }

  private static boolean isHazard(World world, int x, int y, int z) {
    if (y < world.getMinHeight() || y > world.getMaxHeight()) return false;
    try {
      if (!world.isChunkLoaded(x >> 4, z >> 4)) {
        return false;
      }
      return HAZARDS.contains(world.getBlockAt(x, y, z).getType());
    } catch (Exception e) {
      return true;
    }
  }

  public static boolean isClimbablePosition(World world, int x, int y, int z) {
    if (!inBounds(world, y - 1) || !inBounds(world, y + 1)) return false;
    return isClimbable(world, x, y - 1, z)
        || isClimbable(world, x, y, z)
        || isClimbable(world, x, y + 1, z);
  }

  private static boolean isSafeStandPosition(World world, int x, int y, int z) {
    if (isHazard(world, x, y, z) || isHazard(world, x, y + 1, z) || isHazard(world, x, y - 1, z)) {
      return false;
    }
    for (int dx = -1; dx <= 1; dx++) {
      for (int dz = -1; dz <= 1; dz++) {
        if (dx == 0 && dz == 0) continue;
        if (isHazard(world, x + dx, y, z + dz) || isHazard(world, x + dx, y + 1, z + dz)) {
          return false;
        }
      }
    }
    return true;
  }

  private static boolean isWater(World world, int x, int y, int z) {
    if (y < world.getMinHeight() || y > world.getMaxHeight()) return false;
    try {
      if (!world.isChunkLoaded(x >> 4, z >> 4)) {
        return false;
      }
      Block block = world.getBlockAt(x, y, z);
      if (block.getType() == Material.WATER) return true;

      if (block.getBlockData() instanceof Waterlogged wl) {
        return wl.isWaterlogged();
      }
      return false;
    } catch (Exception e) {
      return false;
    }
  }

  private static boolean isBubbleColumn(World world, int x, int y, int z) {
    if (y < world.getMinHeight() || y > world.getMaxHeight()) return false;
    try {
      if (!world.isChunkLoaded(x >> 4, z >> 4)) {
        return false;
      }
      return world.getBlockAt(x, y, z).getType() == Material.BUBBLE_COLUMN;
    } catch (Exception e) {
      return false;
    }
  }

  private static boolean isBubbleElevatorUp(World world, int x, int y, int z) {
    if (!isBubbleColumn(world, x, y, z)) return false;
    return world.getBlockAt(x, y - 1, z).getType() == Material.SOUL_SAND
        || isBubbleColumn(world, x, y - 1, z);
  }

  private static boolean isBubbleElevatorDown(World world, int x, int y, int z) {
    if (!isBubbleColumn(world, x, y, z)) return false;
    return world.getBlockAt(x, y - 1, z).getType() == Material.MAGMA_BLOCK
        || isBubbleColumn(world, x, y - 1, z);
  }

  private static boolean isLava(World world, int x, int y, int z) {
    if (y < world.getMinHeight() || y > world.getMaxHeight()) return false;
    try {
      if (!world.isChunkLoaded(x >> 4, z >> 4)) {
        return false;
      }
      return world.getBlockAt(x, y, z).getType() == Material.LAVA;
    } catch (Exception e) {
      return false;
    }
  }

  private static boolean isNearWater(World world, int x, int y, int z) {
    for (int dx = -1; dx <= 1; dx++) {
      for (int dz = -1; dz <= 1; dz++) {
        for (int dy = -1; dy <= 2; dy++) {
          if (isWater(world, x + dx, y + dy, z + dz)) return true;
        }
      }
    }
    return false;
  }

  private static boolean isNearLava(World world, int x, int y, int z) {
    for (int dx = -1; dx <= 1; dx++) {
      for (int dz = -1; dz <= 1; dz++) {
        if (isLava(world, x + dx, y, z + dz) || isLava(world, x + dx, y - 1, z + dz)) return true;
      }
    }
    return false;
  }

  private static boolean isSlowBlock(World world, int x, int y, int z) {
    if (y < world.getMinHeight() || y > world.getMaxHeight()) return false;
    try {
      if (!world.isChunkLoaded(x >> 4, z >> 4)) {
        return false;
      }
      return SLOW_BLOCKS.contains(world.getBlockAt(x, y, z).getType());
    } catch (Exception e) {
      return false;
    }
  }

  private static boolean canOccupyClimbNode(World world, int x, int y, int z) {
    if (!inBounds(world, y) || !inBounds(world, y + 1)) return false;
    if (walkable(world, x, y, z)) return true;
    return isClimbablePosition(world, x, y, z);
  }

  private static boolean canOccupyBubbleNode(World world, int x, int y, int z) {
    if (!inBounds(world, y) || !inBounds(world, y + 1)) return false;
    return isBubbleColumn(world, x, y, z)
        || (canPassThrough(world, x, y, z) && (isBubbleColumn(world, x, y + 1, z) || isWater(world, x, y, z)));
  }

  private static boolean blocksDiagonal(World world, int x, int y, int z) {
    if (y < world.getMinHeight() || y > world.getMaxHeight()) return false;
    try {
      if (!world.isChunkLoaded(x >> 4, z >> 4)) {
        return false;
      }
      Block block = world.getBlockAt(x, y, z);
      Material mat = block.getType();
      if (mat.isAir() || mat == Material.WATER || mat == Material.LAVA) return false;
      if (block.getBlockData() instanceof Door || block.getBlockData() instanceof Gate) return false;
      if (block.getBlockData() instanceof Fence || block.getBlockData() instanceof Wall) return true;
      if (mat.name().contains("_WALL")) return true;
      if (isClimbable(world, x, y, z)) return false;
      if (block.getBlockData() instanceof TrapDoor trapDoor) return !trapDoor.isOpen();
      return !block.isPassable();
    } catch (Exception e) {
      return true;
    }
  }

  private static boolean isClimbable(World world, int x, int y, int z) {
    if (y < world.getMinHeight() || y > world.getMaxHeight()) return false;
    try {
      if (!world.isChunkLoaded(x >> 4, z >> 4)) {
        return false;
      }
      return Tag.CLIMBABLE.isTagged(world.getBlockAt(x, y, z).getType());
    } catch (Exception e) {
      return false;
    }
  }

  private static boolean canBreak(World world, int x, int y, int z) {
    if (y < world.getMinHeight() || y > world.getMaxHeight()) return false;
    try {
      if (!world.isChunkLoaded(x >> 4, z >> 4)) {
        return false;
      }
      Material mat = world.getBlockAt(x, y, z).getType();
      if (mat.isAir()) return false;

      if (mat == Material.BEDROCK
          || mat == Material.END_PORTAL_FRAME
          || mat == Material.END_PORTAL
          || mat == Material.BARRIER
          || mat == Material.COMMAND_BLOCK
          || mat == Material.CHAIN_COMMAND_BLOCK
          || mat == Material.REPEATING_COMMAND_BLOCK
          || mat == Material.STRUCTURE_BLOCK
          || mat == Material.JIGSAW
          || mat == Material.REINFORCED_DEEPSLATE) return false;

      if (mat == Material.OBSIDIAN
          || mat == Material.CRYING_OBSIDIAN
          || mat == Material.RESPAWN_ANCHOR
          || mat == Material.ANCIENT_DEBRIS
          || mat == Material.NETHERITE_BLOCK
          || mat == Material.ENDER_CHEST) return false;
      return true;
    } catch (Exception e) {
      return false;
    }
  }

  private static boolean hasAdjacentSolid(World world, int x, int y, int z) {
    return canStandOn(world, x, y - 1, z)
        || canStandOn(world, x + 1, y, z)
        || canStandOn(world, x - 1, y, z)
        || canStandOn(world, x, y, z + 1)
        || canStandOn(world, x, y, z - 1);
  }

  private static Pos snap(World world, int x, int y, int z, PathOptions opts, boolean allowFluidStart) {
    for (int dy = 0; dy <= 8; dy++) {
      if (dy == 0 && walkable(world, x, y, z)) return new Pos(x, y, z);
      if (dy == 0 && isClimbablePosition(world, x, y, z)) return new Pos(x, y, z);
      if (dy > 0 && walkable(world, x, y + dy, z)) return new Pos(x, y + dy, z);
      if (dy > 0 && isClimbablePosition(world, x, y + dy, z)) return new Pos(x, y + dy, z);
      if (dy > 0 && walkable(world, x, y - dy, z)) return new Pos(x, y - dy, z);
      if (dy > 0 && isClimbablePosition(world, x, y - dy, z)) return new Pos(x, y - dy, z);
    }

    if (isWater(world, x, y, z) && (allowFluidStart || !opts.avoidWater())) return new Pos(x, y, z);
    if (isLava(world, x, y, z) && (allowFluidStart || !opts.avoidLava())) return new Pos(x, y, z);
    return null;
  }

  private static List<Pos> prioritizedGoals(World world, int x, int y, int z, PathOptions opts) {
    LinkedHashMap<Long, Pos> ordered = new LinkedHashMap<>();

    addPreferredGoal(ordered, snapExactGoal(world, x, y, z, opts));

    for (int radius = 1; radius <= 2; radius++) {
      for (int[] off : offsetsForRadius(radius)) {
        addPreferredGoal(ordered, snapExactGoal(world, x + off[0], y, z + off[1], opts));
      }
    }

    addPreferredGoal(ordered, snap(world, x, y, z, opts, false));

    return new ArrayList<>(ordered.values());
  }

  private static int[][] offsetsForRadius(int radius) {
    if (radius == 1) {
      return new int[][] {
        {1, 0}, {-1, 0}, {0, 1}, {0, -1},
        {1, 1}, {1, -1}, {-1, 1}, {-1, -1}
      };
    }
    return new int[][] {
      {2, 0}, {-2, 0}, {0, 2}, {0, -2},
      {2, 1}, {2, -1}, {-2, 1}, {-2, -1},
      {1, 2}, {-1, 2}, {1, -2}, {-1, -2},
      {2, 2}, {2, -2}, {-2, 2}, {-2, -2}
    };
  }

  private static void addPreferredGoal(LinkedHashMap<Long, Pos> ordered, Pos pos) {
    if (pos == null) return;
    ordered.putIfAbsent(posKey(pos.x(), pos.y(), pos.z()), pos);
  }

  private static Pos snapExactGoal(World world, int x, int y, int z, PathOptions opts) {
    if (walkable(world, x, y, z) || isClimbablePosition(world, x, y, z)) {
      return new Pos(x, y, z);
    }
    if (isWater(world, x, y, z) && !opts.avoidWater()) {
      return new Pos(x, y, z);
    }
    if (isLava(world, x, y, z) && !opts.avoidLava()) {
      return new Pos(x, y, z);
    }
    return null;
  }

  private static List<Move> findPathMovesInternal(
      World world, Pos start, Pos goal, PathOptions opts, int nodeLimit) {
    PriorityQueue<Node> open = new PriorityQueue<>(Comparator.comparingDouble(Node::f));
    Map<Long, Double> best = new HashMap<>(nodeLimit);

    open.add(new Node(start, null, 0, heuristic(start, goal), MoveType.WALK));
    best.put(posKey(start.x(), start.y(), start.z()), 0.0);

    int explored = 0;
    while (!open.isEmpty() && explored++ < nodeLimit) {
      Node cur = open.poll();

      long curKey = posKey(cur.pos().x(), cur.pos().y(), cur.pos().z());
      Double bestG = best.get(curKey);
      if (bestG != null && cur.g() > bestG) continue;

      if (Math.abs(cur.pos().x() - goal.x()) <= 1
          && cur.pos().y() == goal.y()
          && Math.abs(cur.pos().z() - goal.z()) <= 1) {
        return buildPathMoves(cur);
      }

      for (Neighbor nb : neighbors(world, cur.pos().x(), cur.pos().y(), cur.pos().z(), opts)) {
        Pos np = new Pos(nb.x(), nb.y(), nb.z());
        long npKey = posKey(nb.x(), nb.y(), nb.z());
        double newG = cur.g() + nb.cost();
        MoveType mt = nb.type();

        Double existing = best.get(npKey);
        if (existing == null || newG < existing) {
          best.put(npKey, newG);
          open.add(new Node(np, cur, newG, heuristic(np, goal), mt));
        }
      }
    }

    return null;
  }

  private static double heuristic(Pos a, Pos b) {
    int dx = Math.abs(a.x() - b.x());
    int dy = Math.abs(a.y() - b.y());
    int dz = Math.abs(a.z() - b.z());
    double xz = octileHeuristic(dx, dz);
    double vertical =
        a.y() > b.y()
            ? (FALL_1_25_BLOCKS_COST / 2.0) * dy
            : ASCEND * dy;
    return xz + vertical;
  }

  private static double octileHeuristic(int dx, int dz) {
    int straight = Math.abs(dx - dz);
    int diagonal = Math.min(dx, dz);
    return straight * WALK + diagonal * DIAGONAL;
  }

  private static double distanceToTicks(double distance) {
    if (distance <= 0.0) return 0.0;
    double remaining = distance;
    int tickCount = 0;
    while (true) {
      double fallDistance = velocity(tickCount);
      if (remaining <= fallDistance) {
        return tickCount + remaining / fallDistance;
      }
      remaining -= fallDistance;
      tickCount++;
    }
  }

  private static double velocity(int ticks) {
    return (Math.pow(0.98, ticks) - 1.0) * -3.92;
  }

  private static double fallCost(int drop) {
    return WALK_OFF_BLOCK_COST + distanceToTicks(drop) + CENTER_AFTER_FALL_COST;
  }

  private static boolean inBounds(World world, int y) {
    return y > world.getMinHeight() && y < world.getMaxHeight() - 1;
  }

  private static List<Move> buildPathMoves(Node end) {
    List<Move> path = new ArrayList<>();
    for (Node n = end; n != null; n = n.parent()) {
      path.addFirst(new Move(n.pos().x(), n.pos().y(), n.pos().z(), n.action()));
    }
    return path;
  }
}
