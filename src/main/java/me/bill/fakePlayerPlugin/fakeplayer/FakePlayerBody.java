package me.bill.fakePlayerPlugin.fakeplayer;

import me.bill.fakePlayerPlugin.config.Config;
import me.bill.fakePlayerPlugin.util.FppLogger;
import org.bukkit.Bukkit;
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
 * is wrapped in a discard-proxy that safely ignores all writes and flushes
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
            // Pass the resolved skin (may be null if skin mode is off) so
            // NmsPlayerSpawner can inject it into the GameProfile before
            // placeNewPlayer() - the initial PlayerInfo ADD packet will then
            // carry the correct texture data to all clients.
            Player player = NmsPlayerSpawner.spawnFakePlayer(
                    fp.getUuid(), fp.getName(), fp.getResolvedSkin(),
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
            // CRITICAL: Explicitly set invulnerable to FALSE so bots take environmental damage.
            // Even when bodyDamageable is false (entity damage blocked via event cancellation),
            // environmental damage (fall, fire, drowning) must still apply.
            player.setInvulnerable(false);
            player.setCollidable(true);

            // Set NMS-level item pickup flag from the per-bot setting.
            // This is a belt-and-suspenders guard alongside EntityPickupItemEvent cancellation.
            player.setCanPickupItems(fp.isPickUpItemsEnabled());

            // Set display name for death messages and chat.
            // Use plain colorize (not colorizeOrYellow) so the entity display name stays
            // neutral — LP applies prefix/suffix in chat natively. Yellow is only added by
            // BotBroadcast for join/leave messages via its own parseDisplayName helper.
            String displayName = fp.getRawDisplayName() != null ? fp.getRawDisplayName() : fp.getDisplayName();
            if (displayName != null && !displayName.isEmpty()) {
                try {
                    player.displayName(me.bill.fakePlayerPlugin.util.TextUtil.colorize(displayName));
                } catch (Exception e) {
                    FppLogger.debug("Failed to set display name for " + fp.getName() + ": " + e.getMessage());
                }
            }

            // Apply configured max health (combat.max-health)
            try {
                var maxHpAttr = player.getAttribute(org.bukkit.attribute.Attribute.MAX_HEALTH);
                if (maxHpAttr != null) {
                    double hp = Config.maxHealth();
                    maxHpAttr.setBaseValue(hp);
                    player.setHealth(hp);
                }
            } catch (Exception ignored) {}

            // Explicitly disable flying abilities to prevent fly animation
            player.setAllowFlight(false);
            player.setFlying(false);

            // Seed a small downward impulse only when spawning in air so the physics tick
            // immediately starts gravity from tick 1. Skip this in fluids so bots don't get
            // an artificial "sink" nudge when spawning into water/lava.
            boolean fluidSpawn = loc.getBlock().isLiquid()
                    || (loc.clone().add(0, 1, 0).getBlock().isLiquid());
            if (!fluidSpawn) {
                player.setVelocity(new org.bukkit.util.Vector(0, -0.001, 0));
            }

            Config.debug("FakePlayerBody: spawned " + fp.getName()
                    + " (gravity=true, damageable=" + Config.bodyDamageable() + ", flying=false)");

            // Apply skin via Paper's setPlayerProfile() - mirrors the approach used by
            // other NMS fake-player implementations.  This updates the profile on the
            // live entity so any clients that process the join packet AFTER our custom
            // tab-list ADD will also receive the correct texture data.
            applyPaperSkin(player, fp.getResolvedSkin());

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
     * Prepares the skin for {@code fp} then fires {@code onReady} so the bot body
     * spawns immediately - the bot is <b>never</b> blocked waiting for a skin API call.
     *
     * <h3>Spawn flow</h3>
     * <ol>
     *   <li><b>Cache hit (instant)</b> - skin is already in the session cache (pre-warmed
     *       at startup or used before).  The skin is attached to {@code fp} so
     *       {@code NmsPlayerSpawner} can inject it into the {@code GameProfile} before
     *       the entity enters the world.  {@code onReady} fires on this tick.</li>
     *   <li><b>Cache miss</b> - bot spawns immediately with the default Steve / Alex skin.
     *       mineskin.eu is queried asynchronously; once it responds the skin is pushed
     *       to the live entity after a short 3-tick delay (~150 ms) via
     *       {@code setPlayerProfile()}.</li>
     *   <li><b>API failure / rate-limit</b> - the resolve callback delivers {@code null}.
     *       No skin is applied; the bot keeps the default Steve / Alex appearance.</li>
     * </ol>
     *
     * @param onReady       called immediately so the bot body can be spawned
     * @param onSkinApplied called after the async skin is pushed to the live entity;
     *                      {@code null} if no follow-up is needed
     */
    public static void resolveAndFinish(Plugin plugin, FakePlayer fp, Location loc,
                                        Runnable onReady, @org.jetbrains.annotations.Nullable Runnable onSkinApplied) {
        String mode = me.bill.fakePlayerPlugin.config.Config.skinMode();

        if ("off".equals(mode) || "disabled".equals(mode)) {
            onReady.run();
            return;
        }

        // Fast path: inject cached skin into GameProfile before the body spawns so
        // the initial PlayerInfo ADD packet already carries the correct texture.
        SkinProfile cached = SkinRepository.get().getSessionCached(fp.getName());
        if (cached != null) {
            fp.setResolvedSkin(cached);
        }

        // Always spawn immediately - never block on a skin API call.
        onReady.run();

        // Resolve skin asynchronously (instant for cache hits; async for misses).
        // Callback is always delivered on the main thread by SkinRepository.deliver().
        // null = API failure / name not found → bot keeps Steve/Alex, no action needed.
        SkinRepository.get().resolve(fp.getName(), skin -> {
            if (skin == null || !skin.isValid()) return;
            fp.setResolvedSkin(skin);
            // 3-tick delay (~150 ms) so the NMS entity is fully settled in the world
            // before setPlayerProfile() is called on it.
            Bukkit.getScheduler().runTaskLater(plugin, () -> {
                Player body = fp.getPlayer();
                if (body == null || !body.isOnline()) return;
                applyPaperSkin(body, skin);
                if (onSkinApplied != null) onSkinApplied.run();
            }, 3L);
        });
    }

    /**
     * Overload kept for call-sites that don't need a post-skin callback.
     */
    public static void resolveAndFinish(Plugin plugin, FakePlayer fp, Location loc, Runnable onReady) {
        resolveAndFinish(plugin, fp, loc, onReady, null);
    }

    /**
     * Applies a resolved skin to a live bot body via Paper's {@code setPlayerProfile()} API.
     * This mirrors the approach used by other NMS fake-player plugins and ensures the skin
     * is pushed to clients even when it was not available at the moment of initial spawn.
     * Safe to call with a {@code null} skin (no-op).
     */
    public static void applyResolvedSkin(Plugin plugin, FakePlayer fp,
                                         org.bukkit.entity.Entity body) {
        if (!(body instanceof Player player)) return;
        applyPaperSkin(player, fp.getResolvedSkin());
    }

    /**
     * Sets the skin texture on a live bot entity using Paper's {@link org.bukkit.entity.Player#setPlayerProfile}
     * API - the same technique used by other NMS fake-player implementations.
     * Copies the base64 texture value + RSA signature from a {@link SkinProfile} into a
     * {@code ProfileProperty("textures", …)} and applies it to the entity's live profile.
     *
     * @param bot  the NMS bot player
     * @param skin resolved skin to apply, or {@code null} to do nothing
     */
    private static void applyPaperSkin(Player bot, SkinProfile skin) {
        if (skin == null || !skin.isValid()) return;
        try {
            var profile = bot.getPlayerProfile();
            profile.removeProperty("textures");
            profile.setProperty(new com.destroystokyo.paper.profile.ProfileProperty(
                    "textures",
                    skin.getValue(),
                    skin.getSignature() != null ? skin.getSignature() : ""));
            bot.setPlayerProfile(profile);
            Config.debugSkin("FakePlayerBody: paper skin applied to " + bot.getName()
                    + " (" + skin.getSource() + ")");
            // Re-apply skin-overlay metadata after Paper's profile refresh, which
            // may internally re-send entity data with default values.
            NmsPlayerSpawner.forceAllSkinParts(bot);
        } catch (Exception e) {
            FppLogger.debug("FakePlayerBody: paper skin apply failed for " + bot.getName()
                    + ": " + e.getMessage());
        }
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



