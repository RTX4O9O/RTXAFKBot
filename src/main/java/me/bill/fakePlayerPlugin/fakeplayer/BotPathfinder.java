package me.bill.fakePlayerPlugin.fakeplayer;

import me.bill.fakePlayerPlugin.config.Config;
import org.bukkit.World;

import java.util.*;

/**
 * Server-side A* grid pathfinder for bot navigation.
 *
 * <h3>Grid model</h3>
 * <ul>
 *   <li>All Y values use <em>feet-level</em> coordinates.</li>
 *   <li>A position is <b>walkable</b> when the floor at {@code y-1} is solid
 *       and both {@code y} and {@code y+1} are passable.</li>
 * </ul>
 *
 * <h3>Movement types</h3>
 * <ol>
 *   <li><b>Traverse</b>  - same Y, horizontal step (cardinal + diagonal).</li>
 *   <li><b>Ascend</b>    - step up 1 block.</li>
 *   <li><b>Descend</b>   - step off an edge and fall 1–3 blocks.</li>
 *   <li><b>Parkour</b>   - sprint-jump across a 1–2 block gap (cardinal, same Y).</li>
 *   <li><b>Break</b>     - traverse/ascend through a blocking block (cardinal only).</li>
 *   <li><b>Place</b>     - bridge a gap by placing a block below the target (cardinal only).</li>
 * </ol>
 */
public final class BotPathfinder {

    private BotPathfinder() {}

    // ── Limits ────────────────────────────────────────────────────────────────

    /** Max nodes for the default (no extras) search. */
    public static final int MAX_NODES          = 2000;
    /** Max nodes when advanced options (break/parkour/place) are active. */
    public static final int MAX_NODES_EXTENDED = 4000;

    /** Straight-line XYZ sum beyond which pathfinding is not attempted. */
    public static final int MAX_RANGE = 64;

    // ── Grid costs ────────────────────────────────────────────────────────────

    private static final int WALK       = 10;
    private static final int DIAGONAL   = 14;
    private static final int ASCEND     = 10;
    private static final int FALL_PER   = 3;
    private static final int PARKOUR_C  = 25;  // extra per parkour gap
    private static final int BREAK_C    = 30;  // extra per blocked block to mine
    private static final int PLACE_C    = 20;  // extra per gap block to place

    // ── Direction table ───────────────────────────────────────────────────────

    /** {dx, dz, base cost} - cardinal first, then diagonals. */
    private static final int[][] DIRS = {
            { 1,  0, WALK},  {-1,  0, WALK},
            { 0,  1, WALK},  { 0, -1, WALK},
            { 1,  1, DIAGONAL}, { 1, -1, DIAGONAL},
            {-1,  1, DIAGONAL}, {-1, -1, DIAGONAL}
    };

    // ── Public types ──────────────────────────────────────────────────────────

    /** Immutable grid position; Y is feet-level. */
    public record Pos(int x, int y, int z) {}

    /** Action the bot must perform to reach this waypoint. */
    public enum MoveType { WALK, ASCEND, DESCEND, PARKOUR, BREAK, PLACE }

    /** A path waypoint with the action needed to reach it. */
    public record Move(int x, int y, int z, MoveType type) {
        public Pos toPos() { return new Pos(x, y, z); }
    }

    /**
     * Feature flags passed to {@link #findPathMoves}.
     * Use {@link #DEFAULT} for vanilla walk/jump/fall only.
     */
    public record PathOptions(boolean parkour, boolean breakBlocks, boolean placeBlocks) {
        public static final PathOptions DEFAULT = new PathOptions(false, false, false);
        public boolean anyEnabled() { return parkour || breakBlocks || placeBlocks; }
    }

    // ── Internal node ─────────────────────────────────────────────────────────

    private record Node(Pos pos, Node parent, int g, int h, MoveType action) {
        int f() { return g + h; }
    }

    // ── Public API ────────────────────────────────────────────────────────────


    /**
     * Full A* search with optional advanced movement types.
     *
     * @param opts  feature flags; use {@link PathOptions#DEFAULT} for plain navigation
     * @return ordered {@link Move} list from start → near-goal, or {@code null} if unreachable
     */
    public static List<Move> findPathMoves(World world,
                                           int sx, int sy, int sz,
                                           int tx, int ty, int tz,
                                           PathOptions opts) {
        // Quick range gate
        int configuredMaxRange = Config.pathfindingMaxRange();
        if (Math.abs(sx - tx) + Math.abs(sy - ty) + Math.abs(sz - tz) > configuredMaxRange * 3) {
            return null;
        }

        Pos start = snap(world, sx, sy, sz);
        Pos goal  = snap(world, tx, ty, tz);
        if (start == null || goal == null) return null;
        if (start.equals(goal)) return List.of(new Move(start.x(), start.y(), start.z(), MoveType.WALK));

        int nodeLimit = opts.anyEnabled()
                ? Config.pathfindingMaxNodesExtended()
                : Config.pathfindingMaxNodes();

        PriorityQueue<Node> open = new PriorityQueue<>(Comparator.comparingInt(Node::f));
        Map<Pos, Integer>   best = new HashMap<>();

        open.add(new Node(start, null, 0, heuristic(start, goal), MoveType.WALK));
        best.put(start, 0);

        int explored = 0;
        while (!open.isEmpty() && explored++ < nodeLimit) {
            Node cur = open.poll();

            Integer bestG = best.get(cur.pos());
            if (bestG != null && cur.g() > bestG) continue;

            // Goal reached (within 1 XZ, exact Y)
            if (Math.abs(cur.pos().x() - goal.x()) <= 1
                    && cur.pos().y() == goal.y()
                    && Math.abs(cur.pos().z() - goal.z()) <= 1) {
                return buildPathMoves(cur);
            }

            for (int[] nb : neighbors(world, cur.pos().x(), cur.pos().y(), cur.pos().z(), opts)) {
                Pos np    = new Pos(nb[0], nb[1], nb[2]);
                int  newG = cur.g() + nb[3];
                MoveType mt = MoveType.values()[nb[4]];

                Integer existing = best.get(np);
                if (existing == null || newG < existing) {
                    best.put(np, newG);
                    open.add(new Node(np, cur, newG, heuristic(np, goal), mt));
                }
            }
        }

        return null;
    }

    // ── Neighbour generation ──────────────────────────────────────────────────

    /**
     * Returns neighbours as {@code {nx, ny, nz, cost, MoveType.ordinal()}} arrays.
     * BREAK and PLACE are restricted to cardinal directions only.
     * PARKOUR is restricted to cardinal directions only.
     */
    private static List<int[]> neighbors(World world, int x, int y, int z, PathOptions opts) {
        List<int[]> out = new ArrayList<>(32);

        // Ordinal shortcuts (avoids repeated enum.ordinal() calls)
        final int WK = MoveType.WALK.ordinal(),    AS = MoveType.ASCEND.ordinal(),
                  DE = MoveType.DESCEND.ordinal(), PK = MoveType.PARKOUR.ordinal(),
                  BK = MoveType.BREAK.ordinal(),   PL = MoveType.PLACE.ordinal();

        for (int[] d : DIRS) {
            int dx = d[0], dz = d[1], base = d[2];
            int nx = x + dx, nz = z + dz;
            boolean isDiag = (dx != 0 && dz != 0);

            // Diagonal corner-cut guard (Baritone-style)
            if (isDiag) {
                if (!passable(world, x + dx, y,   z) || !passable(world, x + dx, y + 1, z)
                 || !passable(world, x,       y,   z + dz) || !passable(world, x, y + 1, z + dz))
                    continue;
            }

            boolean feetClear  = passable(world, nx, y,     nz);
            boolean headClear  = passable(world, nx, y + 1, nz);
            boolean floorSolid = !passable(world, nx, y - 1, nz);

            // ── TRAVERSE (same Y) ─────────────────────────────────────────────
            if (walkable(world, nx, y, nz)) {
                out.add(new int[]{nx, y, nz, base, WK});
            } else if (!isDiag && opts.breakBlocks() && floorSolid) {
                // BREAK traverse - floor exists but feet/head is blocked (wall)
                int cost = base;
                if (!feetClear) cost += BREAK_C;
                if (!headClear) cost += BREAK_C;
                if (cost > base) out.add(new int[]{nx, y, nz, cost, BK});
            }

            // ── PLACE over gap (same Y, cardinal only) ────────────────────────
            if (!isDiag && opts.placeBlocks() && !floorSolid && feetClear && headClear) {
                out.add(new int[]{nx, y, nz, base + PLACE_C, PL});
            }

            // ── ASCEND +1 ─────────────────────────────────────────────────────
            boolean srcHeadClear = passable(world, x, y + 2, z);
            boolean tgtWalkable  = walkable(world, nx, y + 1, nz);
            boolean tgtHeadClear = passable(world, nx, y + 2, nz);

            if (srcHeadClear && tgtWalkable && tgtHeadClear) {
                out.add(new int[]{nx, y + 1, nz, base + ASCEND, AS});
            } else if (!isDiag && opts.breakBlocks() && tgtWalkable && tgtHeadClear && !srcHeadClear) {
                // BREAK ascend - source head is blocked; mine it to enable the step-up
                out.add(new int[]{nx, y + 1, nz, base + ASCEND + BREAK_C, BK});
            }

            // ── DESCEND / fall ────────────────────────────────────────────────
            if (feetClear && headClear) {
                for (int drop = 1; drop <= 3; drop++) {
                    int ny = y - drop;
                    if (!inBounds(world, ny)) break;
                    if (walkable(world, nx, ny, nz)) {
                        out.add(new int[]{nx, ny, nz, base + drop * FALL_PER, DE});
                        break;
                    }
                    if (!passable(world, nx, ny, nz)) break;
                }
            }

            // ── PARKOUR - cardinal only, same Y, gap of 1 or 2 blocks ─────────
            if (!isDiag && opts.parkour()) {
                int gx1 = nx, gz1 = nz;  // first gap position (1 step ahead)

                boolean gap1NoFloor = passable(world, gx1, y - 1, gz1);   // gap = no floor
                boolean gap1Clear   = feetClear && headClear;               // can pass through

                // ── 1-block gap → land 2 ahead ────────────────────────────────
                int lx2 = x + 2 * dx, lz2 = z + 2 * dz;
                if (gap1NoFloor && gap1Clear && walkable(world, lx2, y, lz2)) {
                    out.add(new int[]{lx2, y, lz2, WALK * 2 + PARKOUR_C, PK});
                }

                // ── 2-block gap → land 3 ahead ────────────────────────────────
                int gx2 = x + 2 * dx, gz2 = z + 2 * dz;  // second gap block
                int lx3 = x + 3 * dx, lz3 = z + 3 * dz;  // landing block
                boolean gap2NoFloor = passable(world, gx2, y - 1, gz2);
                boolean gap2Clear   = passable(world, gx2, y, gz2) && passable(world, gx2, y + 1, gz2);
                if (gap1NoFloor && gap1Clear && gap2NoFloor && gap2Clear && walkable(world, lx3, y, lz3)) {
                    out.add(new int[]{lx3, y, lz3, WALK * 3 + PARKOUR_C * 2, PK});
                }
            }
        }

        return out;
    }

    // ── World queries ─────────────────────────────────────────────────────────

    public static boolean walkable(World world, int x, int y, int z) {
        if (!inBounds(world, y) || !inBounds(world, y + 1)) return false;
        return !passable(world, x, y - 1, z)
                && passable(world, x, y, z)
                && passable(world, x, y + 1, z);
    }

    public static boolean passable(World world, int x, int y, int z) {
        if (y < world.getMinHeight() || y > world.getMaxHeight()) return true;
        try {
            if (!world.isChunkLoaded(x >> 4, z >> 4)) return false;
            return world.getBlockAt(x, y, z).isPassable();
        } catch (Exception e) {
            return false;
        }
    }

    // ── A* helpers ────────────────────────────────────────────────────────────

    private static Pos snap(World world, int x, int y, int z) {
        for (int dy = 0; dy <= 3; dy++) {
            if (dy == 0 && walkable(world, x, y, z)) return new Pos(x, y, z);
            if (dy > 0  && walkable(world, x, y + dy, z)) return new Pos(x, y + dy, z);
            if (dy > 0  && walkable(world, x, y - dy, z)) return new Pos(x, y - dy, z);
        }
        return null;
    }

    private static int heuristic(Pos a, Pos b) {
        int dx = Math.abs(a.x() - b.x());
        int dy = Math.abs(a.y() - b.y());
        int dz = Math.abs(a.z() - b.z());
        int maxXZ = Math.max(dx, dz), minXZ = Math.min(dx, dz);
        return (WALK * maxXZ) + ((DIAGONAL - WALK) * minXZ) + dy * (WALK + ASCEND);
    }

    private static boolean inBounds(World world, int y) {
        return y > world.getMinHeight() && y < world.getMaxHeight() - 1;
    }

    /** Walks the parent chain and builds a forward-ordered {@link Move} list. */
    private static List<Move> buildPathMoves(Node end) {
        List<Move> path = new ArrayList<>();
        for (Node n = end; n != null; n = n.parent()) {
            path.add(0, new Move(n.pos().x(), n.pos().y(), n.pos().z(), n.action()));
        }
        return path;
    }
}

