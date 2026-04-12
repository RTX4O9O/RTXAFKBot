package me.bill.fakePlayerPlugin.fakeplayer;

import me.bill.fakePlayerPlugin.config.Config;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.Vec3;
import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.block.Block;
import org.bukkit.craftbukkit.entity.CraftPlayer;
import org.bukkit.entity.Player;
import org.jetbrains.annotations.Nullable;

/**
 * Shared navigation and interaction utilities used by Mine, Place, and Use commands.
 *
 * <p>These helpers were previously duplicated verbatim in both {@code MineCommand} and
 * {@code PlaceCommand}.  Centralising them here ensures bug-fixes propagate everywhere.</p>
 *
 * <ul>
 *   <li>{@link #findStandLocation} — find a walkable adjacent position with outside-selection preference</li>
 *   <li>{@link #faceToward} — compute yaw/pitch from a stand position toward a target</li>
 *   <li>{@link #isAtActionLocation} — check whether a bot is within action-lock tolerance of a location</li>
 *   <li>{@link #useStorageBlock} — NMS right-click to open a chest/barrel/shulker</li>
 * </ul>
 */
public final class BotNavUtil {

    private BotNavUtil() {}

    // ── Selection bounds ──────────────────────────────────────────────────────

    /**
     * Predicate used by {@link #findStandLocation} to determine whether a candidate position
     * is inside the active area selection.  Pass {@code null} to skip the outside-preference
     * logic (e.g. when finding a stand location for a storage chest).
     */
    @FunctionalInterface
    public interface SelectionBounds {
        boolean contains(int x, int y, int z);
    }

    // ── Stand-location finder ─────────────────────────────────────────────────

    /**
     * Finds a walkable stand position adjacent to the block at {@code (tx, ty, tz)}.
     *
     * <p>Two-pass strategy:</p>
     * <ol>
     *   <li>Pass 1: positions <em>outside</em> {@code sel} — avoids the bot standing in
     *       its own work area and blocking itself (mine area / fill area).</li>
     *   <li>Pass 2: any walkable adjacent position — fallback for confined areas where
     *       all outside positions are inaccessible.</li>
     * </ol>
     *
     * <p>The 16 candidates cover the 4 cardinal + 4 diagonal XZ offsets at {@code ty},
     * {@code ty−1}, and {@code ty+1} — the same set previously embedded in both
     * {@code MineCommand} and {@code PlaceCommand}.</p>
     *
     * @param world  world to query {@link BotPathfinder#walkable} in
     * @param sel    selection to prefer standing outside of, or {@code null}
     * @param tx     target block X
     * @param ty     target block Y
     * @param tz     target block Z
     * @return walkable stand location within 6 blocks, or {@code null} if none found
     */
    @Nullable
    public static Location findStandLocation(World world, @Nullable SelectionBounds sel,
                                              int tx, int ty, int tz) {
        int[][] candidates = {
            {tx+1, ty,   tz},   {tx-1, ty,   tz},   {tx,   ty,   tz+1}, {tx,   ty,   tz-1},
            {tx+2, ty,   tz},   {tx-2, ty,   tz},   {tx,   ty,   tz+2}, {tx,   ty,   tz-2},
            {tx+1, ty-1, tz},   {tx-1, ty-1, tz},   {tx,   ty-1, tz+1}, {tx,   ty-1, tz-1},
            {tx+1, ty+1, tz},   {tx-1, ty+1, tz},   {tx,   ty+1, tz+1}, {tx,   ty+1, tz-1}
        };
        Location targetCenter = new Location(world, tx + 0.5, ty + 0.5, tz + 0.5);

        // Pass 1: prefer positions outside the selection
        if (sel != null) {
            for (int[] c : candidates) {
                if (sel.contains(c[0], c[1], c[2])) continue;
                if (BotPathfinder.walkable(world, c[0], c[1], c[2])) {
                    Location loc = new Location(world, c[0] + 0.5, c[1], c[2] + 0.5);
                    if (loc.distanceSquared(targetCenter) <= 36.0) return loc;
                }
            }
        }

        // Pass 2: any walkable adjacent position
        for (int[] c : candidates) {
            if (BotPathfinder.walkable(world, c[0], c[1], c[2])) {
                Location loc = new Location(world, c[0] + 0.5, c[1], c[2] + 0.5);
                if (loc.distanceSquared(targetCenter) <= 36.0) return loc;
            }
        }
        return null;
    }

    // ── Facing helpers ────────────────────────────────────────────────────────

    /**
     * Clones {@code from} and sets its yaw/pitch so the bot looks from eye-height toward
     * {@code target}.  The eye-height offset (1.62 blocks) is applied to the pitch
     * calculation so the look direction is realistic.
     *
     * @param from   bot's stand location
     * @param target target centre to look at
     * @return new {@link Location} with yaw and pitch set
     */
    public static Location faceToward(Location from, Location target) {
        Location loc = from.clone();
        double dx = target.getX() - loc.getX();
        double dy = target.getY() - (loc.getY() + 1.62);
        double dz = target.getZ() - loc.getZ();
        double xz = Math.sqrt(dx * dx + dz * dz);
        loc.setYaw((float) Math.toDegrees(Math.atan2(-dx, dz)));
        loc.setPitch((float) -Math.toDegrees(Math.atan2(dy, xz)));
        return loc;
    }

    // ── Position guard ────────────────────────────────────────────────────────

    /**
     * Returns {@code true} when {@code bot} is close enough to {@code loc} to be considered
     * "at" the action spot.
     *
     * <p>Tolerances: XZ ≤ {@link Config#pathfindingArrivalDistance()},
     * Y-delta &lt; 1.25 blocks.</p>
     */
    public static boolean isAtActionLocation(@Nullable Player bot, @Nullable Location loc) {
        if (bot == null || loc == null || bot.getWorld() != loc.getWorld()) return false;
        double xz = PathfindingService.xzDist(bot.getLocation(), loc);
        double dy = Math.abs(bot.getLocation().getY() - loc.getY());
        return xz <= Config.pathfindingArrivalDistance() && dy < 1.25;
    }

    // ── NMS storage-block interaction ─────────────────────────────────────────

    /**
     * Right-clicks a storage block (chest, barrel, shulker, etc.) using the NMS game-mode
     * API.  Swings the bot's main hand on success.  All exceptions are silently swallowed —
     * this is a best-effort visual open with no gameplay side-effects if it fails.
     *
     * @param bot   the bot player
     * @param block the block to right-click
     */
    public static void useStorageBlock(Player bot, Block block) {
        try {
            ServerPlayer nms = ((CraftPlayer) bot).getHandle();
            BlockPos pos = new BlockPos(block.getX(), block.getY(), block.getZ());
            Vec3 hitVec = new Vec3(block.getX() + 0.5, block.getY() + 0.5, block.getZ() + 0.5);
            BlockHitResult hit = new BlockHitResult(hitVec, Direction.UP, pos, false);
            nms.resetLastActionTime();
            var result = nms.gameMode.useItemOn(
                    nms, nms.level(),
                    nms.getItemInHand(InteractionHand.MAIN_HAND),
                    InteractionHand.MAIN_HAND, hit);
            if (result.consumesAction()) nms.swing(InteractionHand.MAIN_HAND);
        } catch (Throwable ignored) {}
    }
}

