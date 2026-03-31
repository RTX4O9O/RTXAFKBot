package me.bill.fakePlayerPlugin.fakeplayer;

import me.bill.fakePlayerPlugin.config.Config;
import me.bill.fakePlayerPlugin.util.FppLogger;
import org.bukkit.Location;
import org.bukkit.entity.Player;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.plugin.Plugin;

/**
 * NMS-based fake player spawner.
 *
 * <p>Each bot is a single NMS {@code ServerPlayer} entity created via
 * {@link NmsPlayerSpawner}.  It appears to clients as a genuine player with
 * a real body, nametag, tab-list entry, and full physics interaction
 * (damage, knockback, death, drowning, mob targeting, etc.).
 *
 * <p>The entity is backed by a fake {@code EmbeddedChannel} connection that
 * discards all outbound packets without touching a real socket.  The channel
 * is wrapped in a discard-proxy (see {@link DiscardProxyChannel}) that safely ignores all writes and flushes
 * to prevent the {@code ChannelOutboundBuffer.addFlush()} NPE that Netty 4.2.x
 * would otherwise throw during every server tick.
 */
public final class FakePlayerBody {

    /** PDC value stored on the NMS Player entity for fast bot detection in listeners. */
    public static final String VISUAL_PDC_VALUE  = "fpp-visual";

    /** Kept for backward-compat; not written on new entities in this implementation. */
    public static final String NAMETAG_PDC_VALUE = "fpp-nametag";

    private FakePlayerBody() {}

    // ── Spawn ─────────────────────────────────────────────────────────────────

    /**
     * Spawns a real NMS {@code ServerPlayer} entity for {@code fp} at {@code loc}.
     *
     * @return the Bukkit {@link Player} wrapper, or {@code null} if spawn failed
     */
    public static Player spawn(FakePlayer fp, Location loc) {
        if (loc == null || loc.getWorld() == null) return null;
        try {
            Player player = NmsPlayerSpawner.spawnFakePlayer(
                    fp.getUuid(), fp.getName(),
                    loc.getWorld(), loc.getX(), loc.getY(), loc.getZ());

            if (player == null) {
                FppLogger.warn("FakePlayerBody.spawn: NmsPlayerSpawner returned null for " + fp.getName());
                return null;
            }

            // Tag the entity via PDC so listeners can identify it as an FPP bot
            try {
                if (FakePlayerManager.FAKE_PLAYER_KEY != null) {
                    player.getPersistentDataContainer().set(
                            FakePlayerManager.FAKE_PLAYER_KEY,
                            PersistentDataType.STRING,
                            VISUAL_PDC_VALUE + ":" + fp.getName());
                }
            } catch (Exception e) {
                FppLogger.debug("FakePlayerBody.spawn: PDC tag failed for " + fp.getName() + ": " + e.getMessage());
            }

            // Enable physics and collision
            player.setGravity(true);
            player.setInvulnerable(!Config.bodyDamageable());
            player.setCollidable(true);

            // Seed a small downward impulse only when spawning in air so the physics tick
            // immediately starts gravity from tick 1. Skip this in fluids so bots don't get
            // an artificial "sink" nudge when spawning into water/lava.
            boolean fluidSpawn = loc.getBlock().isLiquid()
                    || (loc.clone().add(0, 1, 0).getBlock().isLiquid());
            if (!fluidSpawn) {
                player.setVelocity(new org.bukkit.util.Vector(0, -0.001, 0));
            }

            Config.debug("FakePlayerBody: spawned " + fp.getName()
                    + " (gravity=true, damageable=" + Config.bodyDamageable() + ")");

            return player;

        } catch (Exception e) {
            FppLogger.error("FakePlayerBody.spawn failed for " + fp.getName() + ": " + e.getMessage());
            return null;
        }
    }

    // ── Remove ────────────────────────────────────────────────────────────────

    /**
     * Removes the NMS player entity for {@code fp} from the world.
     * Uses a kick to trigger the normal server-side removal path.
     */
    public static void removeAll(FakePlayer fp) {
        if (fp == null) return;
        try {
            Player player = fp.getPlayer();
            if (player != null && player.isOnline()) {
                NmsPlayerSpawner.removeFakePlayer(player);
            }
        } catch (Exception e) {
            FppLogger.error("FakePlayerBody.removeAll failed for "
                    + (fp.getName() != null ? fp.getName() : "?") + ": " + e.getMessage());
        }
    }

    // ── Spawn pipeline helpers ────────────────────────────────────────────────

    /**
     * Completes immediately — skins are disabled, so spawning no longer waits on
     * any async skin lookup or profile mutation step.
     */
    public static void resolveAndFinish(Plugin plugin, FakePlayer fp, Location loc, Runnable onReady) {
        fp.setResolvedSkin(null);
        onReady.run();
    }

    /**
     * No-op in NMS mode.
     * Kept so call-sites in FakePlayerManager compile without changes.
     */
    public static void applyResolvedSkin(Plugin plugin, FakePlayer fp,
                                         org.bukkit.entity.Entity body) {
        // Skins are disabled.
    }

    // ── Legacy nametag methods (no-ops for NMS players) ─────────────────────

    /**
     * No-op - NMS players have built-in nametags managed by the client.
     */
    public static void removeNametag(FakePlayer fp) {
        // No-op - NMS players manage their own nametags
    }

    /**
     * No-op - NMS players have built-in nametags managed by the client.
     */
    public static org.bukkit.entity.Entity spawnNametag(FakePlayer fp, org.bukkit.entity.Entity body) {
        // No-op - NMS players have built-in nametags
        return null;
    }

    /**
     * No-op - NMS players don't create separate nametag entities.
     */
    public static void removeOrphanedNametags(String reason) {
        // No-op - NMS players don't have separate nametag entities
    }

    /**
     * No-op - body cleanup is handled by normal player entity removal.
     */
    public static void removeOrphanedBodies(String reason) {
        // No-op - NMS player entities are cleaned up normally
    }
}


