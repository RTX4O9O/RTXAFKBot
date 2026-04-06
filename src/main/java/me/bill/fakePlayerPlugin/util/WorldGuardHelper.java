package me.bill.fakePlayerPlugin.util;

import com.sk89q.worldedit.bukkit.BukkitAdapter;
import com.sk89q.worldguard.WorldGuard;
import com.sk89q.worldguard.protection.flags.Flags;
import com.sk89q.worldguard.protection.regions.RegionQuery;
import org.bukkit.Location;
import org.bukkit.World;

/**
 * WorldGuard integration helper for FakePlayerPlugin.
 *
 * <h3>ClassLoader guard</h3>
 * <strong>Never reference this class unless
 * {@code FakePlayerPlugin.isWorldGuardAvailable()} returns {@code true}.</strong>
 * Paper eagerly resolves class references at bytecode verification time —
 * loading this class without WorldGuard on the classpath will throw
 * {@code NoClassDefFoundError}. The pattern everywhere WorldGuard is needed:
 * <pre>{@code
 *   if (plugin.isWorldGuardAvailable()) { WorldGuardHelper.doSomething(...); }
 * }</pre>
 */
public final class WorldGuardHelper {

    private WorldGuardHelper() {}

    /**
     * Returns {@code true} if PvP is allowed at the given location according to
     * the active WorldGuard region flags.
     *
     * <p>Treats a missing flag (no explicit deny) as <em>allowed</em>, matching
     * WorldGuard's default-allow behaviour. Returns {@code true} on any error so
     * the integration fails open and never unexpectedly blocks damage.
     *
     * @param location the Bukkit location to check
     * @return {@code true} if PvP is permitted, {@code false} if a region explicitly
     *         disables PvP at this location
     */
    public static boolean isPvpAllowed(Location location) {
        if (location == null || location.getWorld() == null) return true;
        try {
            RegionQuery query = WorldGuard.getInstance()
                    .getPlatform()
                    .getRegionContainer()
                    .createQuery();
            com.sk89q.worldedit.util.Location wgLoc = BukkitAdapter.adapt(location);
            // Use queryState (not testState) so that areas with NO pvp flag set return
            // null (not false).  testState requires an explicit ALLOW to return true,
            // which means completely unprotected areas would return false and block ALL
            // damage even outside any region.  We only deny damage when a region
            // explicitly sets pvp = DENY; null (flag absent) or ALLOW = PvP is permitted.
            com.sk89q.worldguard.protection.flags.StateFlag.State state =
                    query.queryState(wgLoc, null, Flags.PVP);
            return state != com.sk89q.worldguard.protection.flags.StateFlag.State.DENY;
        } catch (Exception e) {
            // Fail-open: allow damage if the WG query throws unexpectedly
            return true;
        }
    }

    /**
     * Tries to find a safe (PvP-allowed) spawn location for a bot that has
     * spawned inside a no-PvP region.
     *
     * <p>Strategy:
     * <ol>
     *   <li>Return the world spawn point if PvP is allowed there.</li>
     *   <li>Return {@code null} if the world spawn is also protected
     *       (caller should log a warning and leave the bot in place).</li>
     * </ol>
     *
     * @param world the world to search
     * @return a PvP-allowed {@link Location}, or {@code null} if none found
     */
    public static Location findSafeLocation(World world) {
        if (world == null) return null;
        Location spawn = world.getSpawnLocation().clone().add(0.5, 0, 0.5);
        if (isPvpAllowed(spawn)) return spawn;
        return null;
    }
}

