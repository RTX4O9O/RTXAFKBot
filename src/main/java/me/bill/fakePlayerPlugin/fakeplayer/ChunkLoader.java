package me.bill.fakePlayerPlugin.fakeplayer;

import me.bill.fakePlayerPlugin.FakePlayerPlugin;
import me.bill.fakePlayerPlugin.config.Config;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.entity.Player;

import java.util.*;

/**
 * Keeps chunks loaded around each active bot <em>exactly like a real player</em>.
 *
 * <h3>How vanilla chunk loading works</h3>
 * <p>When a real player is at chunk (cx, cz), the server adds ticket-level tickets
 * for every chunk within the <em>view-distance</em> radius, and <em>simulation-distance</em>
 * tickets for a smaller inner radius where mobs/redstone actually tick.
 * Beyond simulation-distance, chunks are sent to the client (kept loaded) but mobs
 * do not spawn and redstone does not tick.
 *
 * <h3>This implementation</h3>
 * <ul>
 *   <li>Uses {@link World#addPluginChunkTicket} - Paper counts these identically to
 *       player chunk-load tickets (mobs spawn, redstone runs, crops grow).</li>
 *   <li>Tickets are added in spiral order so nearby chunks are prioritised.</li>
 *   <li>Movement detection skips costly set-diff when the bot hasn't moved
 *       to a new chunk since the last tick.</li>
 *   <li>Supports per-bot configurable radius, world-border clamping, and
 *       instant release on bot removal.</li>
 *   <li>Refreshes every {@code chunk-loading.update-interval} ticks (default 20).</li>
 * </ul>
 */
public final class ChunkLoader {

    private final FakePlayerPlugin  plugin;
    private final FakePlayerManager manager;

    /**
     * Per-bot state: the last chunk-centre we computed tickets from, plus
     * the world name so we can release across world-changes without needing
     * a live entity reference.
     */
    private final Map<UUID, BotChunkState> states = new HashMap<>();

    public ChunkLoader(FakePlayerPlugin plugin, FakePlayerManager manager) {
        this.plugin  = plugin;
        this.manager = manager;

        long interval = Math.max(1L, Config.chunkLoadingUpdateInterval());
        Bukkit.getScheduler().runTaskTimer(plugin, this::tick, interval, interval);
    }

    // ── Per-interval tick ────────────────────────────────────────────────────

    private void tick() {
        if (!Config.chunkLoadingEnabled()) {
            if (!states.isEmpty()) releaseAll();
            return;
        }

        int globalRadius = Config.chunkLoadingRadius();
        if (globalRadius == 0) {
            // radius: 0 means "no chunk loading" — release any held tickets and skip
            if (!states.isEmpty()) releaseAll();
            return;
        }

        Set<UUID> activeUuids = new HashSet<>();

        for (FakePlayer fp : manager.getActivePlayers()) {
            UUID botId = fp.getUuid();
            activeUuids.add(botId);

            // ── Resolve effective radius for this bot ─────────────────────────
            // Per-bot override: -1 = use global. 0 = disabled for this bot.
            // Always capped at globalRadius so bots can never load more than
            // the server-wide maximum configured by the admin.
            int botR = fp.getChunkLoadRadius();
            int radius = (botR < 0) ? globalRadius : Math.min(botR, globalRadius);

            // Per-bot radius of 0 = chunk loading disabled for this bot
            if (radius == 0) {
                BotChunkState existing = states.remove(botId);
                if (existing != null) releaseState(existing);
                continue;
            }

            // ── Resolve current chunk-centre ──────────────────────────────────
            Location pos = resolvePosition(fp);
            if (pos == null || pos.getWorld() == null) continue;

            World  world = pos.getWorld();
            String wName = world.getName();
            int    cx    = pos.getBlockX() >> 4;
            int    cz    = pos.getBlockZ() >> 4;

            // Clamp to world border
            int[] clamped = clampToWorldBorder(world, cx, cz);
            cx = clamped[0];
            cz = clamped[1];

            BotChunkState state = states.get(botId);

            // ── Skip if bot hasn't moved to a new chunk ───────────────────────
            if (state != null
                    && state.worldName.equals(wName)
                    && state.cx == cx
                    && state.cz == cz) {
                continue; // no update needed
            }

            // ── Release old tickets if world changed ──────────────────────────
            if (state != null && !state.worldName.equals(wName)) {
                releaseState(state);
                state = null;
            }

            // ── Build desired ticket set in spiral order ───────────────────────
            // Spiral ensures nearby chunks are prioritised in chunk generation.
            List<long[]> spiral = buildSpiral(cx, cz, radius, world);
            Set<Long> desired = new HashSet<>(spiral.size());
            for (long[] coord : spiral) desired.add(packKey((int) coord[0], (int) coord[1]));

            if (state == null) {
                state = new BotChunkState(wName, cx, cz, new HashSet<>());
                states.put(botId, state);
            }

            // Add new tickets
            for (long[] coord : spiral) {
                long key = packKey((int) coord[0], (int) coord[1]);
                if (state.keys.add(key)) {
                    world.addPluginChunkTicket((int) coord[0], (int) coord[1], plugin);
                }
            }

            // Remove stale tickets (bot moved, old chunks no longer needed)
            Iterator<Long> it = state.keys.iterator();
            while (it.hasNext()) {
                long key = it.next();
                if (!desired.contains(key)) {
                    world.removePluginChunkTicket(unpackX(key), unpackZ(key), plugin);
                    it.remove();
                }
            }

            // Update centre
            state.cx = cx;
            state.cz = cz;
            state.worldName = wName;
        }

        // Release tickets for bots that are no longer active
        states.entrySet().removeIf(entry -> {
            if (activeUuids.contains(entry.getKey())) return false;
            releaseState(entry.getValue());
            return true;
        });
    }

    // ── Public API ────────────────────────────────────────────────────────────

    /**
     * Immediately releases all chunk tickets held for a specific bot.
     * Safe to call after the body entity has been removed.
     */
    public void releaseForBot(FakePlayer fp) {
        BotChunkState state = states.remove(fp.getUuid());
        if (state != null) releaseState(state);
    }

    /** Releases ALL chunk tickets on plugin disable. */
    public void releaseAll() {
        states.values().forEach(this::releaseState);
        states.clear();
    }

    /** Returns the number of plugin chunk tickets currently held across all bots. */
    @SuppressWarnings("unused") // Public diagnostic API - used by /fpp info and addons
    public int totalTickets() {
        return states.values().stream().mapToInt(s -> s.keys.size()).sum();
    }

    // ── Helpers ───────────────────────────────────────────────────────────────

    /** Resolves the most accurate live position for a bot. */
    private static Location resolvePosition(FakePlayer fp) {
        // Use live NMS Player position
        Player player = fp.getPlayer();
        if (player != null && player.isValid()) return player.getLocation();
        // Fall back to last known spawn / DB location
        Location loc = fp.getSpawnLocation();
        if (loc != null && loc.getWorld() != null) return loc;
        return null;
    }

    /**
     * Builds a chunk list in spiral order from the centre outward.
     * Spiral order ensures close chunks are loaded before far ones, matching
     * Paper's vanilla chunk-send priority queue behaviour.
     * Chunks are also world-border clamped here.
     */
    private static List<long[]> buildSpiral(int cx, int cz, int radius, World world) {
        int diameter = radius * 2 + 1;
        List<long[]> result = new ArrayList<>(diameter * diameter);

        // Manhattan-distance spiral: 0,0 first, then ring by ring
        result.add(new long[]{cx, cz});
        for (int r = 1; r <= radius; r++) {
            // Top row (left to right)
            for (int dx = -r; dx <= r; dx++) addIfInBorder(result, cx + dx, cz - r, world);
            // Right col (top+1 to bottom)
            for (int dz = -r + 1; dz <= r; dz++) addIfInBorder(result, cx + r, cz + dz, world);
            // Bottom row (right-1 to left)
            for (int dx = r - 1; dx >= -r; dx--) addIfInBorder(result, cx + dx, cz + r, world);
            // Left col (bottom-1 to top+1)
            for (int dz = r - 1; dz >= -r + 1; dz--) addIfInBorder(result, cx - r, cz + dz, world);
        }
        return result;
    }

    private static void addIfInBorder(List<long[]> list, int x, int z, World world) {
        double borderRadius = world.getWorldBorder().getSize() / 2.0;
        double bx = world.getWorldBorder().getCenter().getX();
        double bz = world.getWorldBorder().getCenter().getZ();
        double chunkCenterX = x * 16.0 + 8;
        double chunkCenterZ = z * 16.0 + 8;
        if (Math.abs(chunkCenterX - bx) <= borderRadius && Math.abs(chunkCenterZ - bz) <= borderRadius) {
            list.add(new long[]{x, z});
        }
    }

    /** Clamps a chunk coordinate pair to within the world border. */
    private static int[] clampToWorldBorder(World world, int cx, int cz) {
        double borderHalf = world.getWorldBorder().getSize() / 2.0;
        double bx = world.getWorldBorder().getCenter().getX();
        double bz = world.getWorldBorder().getCenter().getZ();
        double minChunkX = Math.floor((bx - borderHalf) / 16.0);
        double maxChunkX = Math.floor((bx + borderHalf) / 16.0);
        double minChunkZ = Math.floor((bz - borderHalf) / 16.0);
        double maxChunkZ = Math.floor((bz + borderHalf) / 16.0);
        return new int[]{
            (int) Math.max(minChunkX, Math.min(maxChunkX, cx)),
            (int) Math.max(minChunkZ, Math.min(maxChunkZ, cz))
        };
    }

    private void releaseState(BotChunkState state) {
        World world = Bukkit.getWorld(state.worldName);
        if (world == null || state.keys.isEmpty()) { state.keys.clear(); return; }
        for (long key : state.keys) {
            world.removePluginChunkTicket(unpackX(key), unpackZ(key), plugin);
        }
        state.keys.clear();
    }

    // ── Key packing ───────────────────────────────────────────────────────────

    private static long packKey(int x, int z) {
        return ((long) x & 0xFFFFFFFFL) | (((long) z & 0xFFFFFFFFL) << 32);
    }
    private static int unpackX(long key) { return (int) (key & 0xFFFFFFFFL); }
    private static int unpackZ(long key) { return (int) ((key >>> 32) & 0xFFFFFFFFL); }

    // ── Inner state record ────────────────────────────────────────────────────

    private static final class BotChunkState {
        String    worldName;
        int       cx, cz;
        Set<Long> keys;

        BotChunkState(String worldName, int cx, int cz, Set<Long> keys) {
            this.worldName = worldName;
            this.cx        = cx;
            this.cz        = cz;
            this.keys      = keys;
        }
    }
}

