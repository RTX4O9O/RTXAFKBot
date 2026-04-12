package me.bill.fakePlayerPlugin.listener;

import me.bill.fakePlayerPlugin.FakePlayerPlugin;
import me.bill.fakePlayerPlugin.config.Config;
import me.bill.fakePlayerPlugin.fakeplayer.BotBroadcast;
import me.bill.fakePlayerPlugin.fakeplayer.BotType;
import me.bill.fakePlayerPlugin.fakeplayer.FakePlayer;
import me.bill.fakePlayerPlugin.fakeplayer.ChunkLoader;
import me.bill.fakePlayerPlugin.fakeplayer.FakePlayerBody;
import me.bill.fakePlayerPlugin.fakeplayer.FakePlayerManager;
import me.bill.fakePlayerPlugin.fakeplayer.PacketHelper;
import me.bill.fakePlayerPlugin.util.WorldGuardHelper;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.Sound;
import org.bukkit.SoundCategory;
import org.bukkit.attribute.Attribute;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.EntityDamageByEntityEvent;
import org.bukkit.event.entity.EntityDamageEvent;
import org.bukkit.event.entity.EntityDeathEvent;
import org.bukkit.event.entity.EntityPickupItemEvent;
import org.bukkit.event.entity.EntityPortalEvent;
import org.bukkit.event.entity.EntityTeleportEvent;
import org.bukkit.event.player.PlayerRespawnEvent;
import org.bukkit.persistence.PersistentDataType;

import java.util.UUID;

public class FakePlayerEntityListener implements Listener {

    private final FakePlayerPlugin  plugin;
    private final FakePlayerManager manager;
    private final ChunkLoader       chunkLoader;

    public FakePlayerEntityListener(FakePlayerPlugin plugin, FakePlayerManager manager, ChunkLoader chunkLoader) {
        this.plugin      = plugin;
        this.manager     = manager;
        this.chunkLoader = chunkLoader;
    }

    // ── Damage filter ─────────────────────────────────────────────────────────

    @EventHandler(priority = EventPriority.LOWEST)
    public void onEntityDamage(EntityDamageEvent event) {
        if (!isFakeBotBody(event.getEntity())) return;

        // Primary damageable guard - only blocks entity/player-sourced damage.
        // Environmental damage (fall, fire, drowning, lava, etc.) is ALWAYS allowed
        // so bots behave like real players. Checked live from config so /fpp reload
        // takes effect without respawn.
        if (!Config.bodyDamageable()) {
            // Only cancel if this is entity/player-sourced damage
            if (event instanceof EntityDamageByEntityEvent) {
                event.setCancelled(true);
                return;
            }
            // Allow all environmental damage to pass through
        }

        // WorldGuard PvP region check - cancel PvP damage to bots inside protected zones.
        // Cancelling at LOWEST prevents the damage, knockback, invincibility frames, and
        // the red damage tint from ever being applied (all downstream at higher priorities).
        // We only block attacker-sourced (PvP) damage - environment damage (fall, fire, etc.)
        // is unaffected.
        if (event instanceof EntityDamageByEntityEvent byEntity
                && byEntity.getDamager() instanceof Player
                && plugin.isWorldGuardAvailable()
                && !WorldGuardHelper.isPvpAllowed(event.getEntity().getLocation())) {
            event.setCancelled(true);
            return;
        }

        // Track damage for statistics
        if (!event.isCancelled() && event.getEntity() instanceof Player p) {
            FakePlayer fp = manager.getByEntity(p);
            if (fp != null) {
                fp.addDamageTaken(event.getFinalDamage());
            }
        }
    }

    /**
     * Plays the hurt sound for bots only when damage is confirmed (not cancelled).
     * Running at MONITOR with ignoreCancelled=true ensures WorldGuard-cancelled events
     * (and any other plugin that cancels damage) do not trigger a phantom hurt sound.
     * Uses world-level sound emission so the server handles range culling automatically,
     * avoiding an O(players) loop.
     */
    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onEntityDamageConfirmed(EntityDamageEvent event) {
        if (!isFakeBotBody(event.getEntity())) return;
        if (!Config.hurtSound()) return;
        Location loc = event.getEntity().getLocation();
        if (loc.getWorld() != null) {
            loc.getWorld().playSound(loc, Sound.ENTITY_PLAYER_HURT, SoundCategory.PLAYERS, 1.0f, 1.0f);
        }
    }

    // ── PVP Bot Damage Tracking (for defensive mode) ──────────────────────────

    /**
     * Tracks when a PVP bot is damaged by a player.
     * This enables defensive mode retaliation.
     */
    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onBotDamagedByPlayer(EntityDamageByEntityEvent event) {
        if (!isFakeBotBody(event.getEntity())) return;
        if (!(event.getEntity() instanceof Player victim)) return;
        if (!(event.getDamager() instanceof Player attacker)) return;

        FakePlayer fp = manager.getByEntity(victim);
        if (fp == null) return;

        // Only track for PVP bots
        if (fp.getBotType() != BotType.PVP) return;

        // Notify PVP AI that this bot was attacked
        if (manager.getPvpAI() != null) {
            manager.getPvpAI().onBotAttacked(
                victim.getUniqueId(),
                attacker.getUniqueId()
            );
        }
    }

    // ── Portal / teleport guard ───────────────────────────────────────────────

    /**
     * Prevents bot Mannequin bodies from traversing portals.
     *
     * <p>When an entity goes through a portal Minecraft ejects its passengers
     * (the ArmorStand nametag) at the portal entrance, orphaning it in the
     * original world.  The entity also receives a new entity-id in the target
     * world, breaking {@code entityIdIndex} lookups and causing the death
     * handler to silently skip cleanup.  Blocking the portal event prevents
     * all of these cascading issues.
     */
    @EventHandler(priority = EventPriority.NORMAL, ignoreCancelled = true)
    public void onEntityPortal(EntityPortalEvent event) {
        if (!isFakeBotBody(event.getEntity())) return;
        event.setCancelled(true);
        Config.debug("Blocked portal traversal for bot body: "
                + event.getEntity().getPersistentDataContainer()
                        .get(FakePlayerManager.FAKE_PLAYER_KEY, PersistentDataType.STRING));
    }

    /**
     * Prevents bot Mannequin bodies from being teleported to a different world
     * via commands, plugins, or other mechanics (chorus fruit, etc.).
     *
     * <p>Same root cause as the portal case: cross-world teleports eject
     * passengers and break the entity-id index.  Same-world teleports are
     * allowed (they don't change the entity id or eject riders).
     */
    @EventHandler(priority = EventPriority.NORMAL, ignoreCancelled = true)
    public void onEntityTeleport(EntityTeleportEvent event) {
        if (!isFakeBotBody(event.getEntity())) return;
        org.bukkit.Location from = event.getFrom();
        org.bukkit.Location to   = event.getTo();
        if (to == null || from.getWorld() == null || to.getWorld() == null) return;
        if (from.getWorld().equals(to.getWorld())) return; // same world - fine
        event.setCancelled(true);
        Config.debug("Blocked cross-world teleport for bot body: "
                + event.getEntity().getPersistentDataContainer()
                        .get(FakePlayerManager.FAKE_PLAYER_KEY, PersistentDataType.STRING));
    }

    // ── Death ─────────────────────────────────────────────────────────────────

    @EventHandler(priority = EventPriority.MONITOR)
    public void onEntityDeath(EntityDeathEvent event) {
        if (!isFakeBotBody(event.getEntity())) return;

        FakePlayer fp = manager.getByEntity(event.getEntity());
        if (fp == null) return;

        // PVP bots always drop their inventory
        // Other bots respect the config setting
        if (fp.getBotType() != me.bill.fakePlayerPlugin.fakeplayer.BotType.PVP && Config.suppressDrops()) {
            event.getDrops().clear();
            event.setDroppedExp(0);
        }

        Player killer = event.getEntity().getKiller();
        if (killer != null) {
            // Use raw display name to preserve color codes in kill messages
            String displayName = fp.getRawDisplayName() != null ? fp.getRawDisplayName() : fp.getDisplayName();
            BotBroadcast.broadcastKill(killer.getName(), displayName);
        }

        // Increment death counter
        fp.incrementDeathCount();
        fp.setAlive(false);

        final String name = fp.getName();

        // Clear entity-index references immediately - the body is dead
        fp.setPlayer(null);
        manager.removeFromEntityIndex(event.getEntity().getEntityId());

        if (Config.respawnOnDeath()) {
            // ── In-place respawn (no disconnect / reconnect) ──────────────────
            // spigot().respawn() fires PlayerQuitEvent (old entity removed),
            // PlayerRespawnEvent (location override handled by onBotRespawn),
            // and PlayerJoinEvent (join message suppressed by PlayerJoinListener).
            // ALL entity re-registration is done in the 2-tick follow-up task
            // below - this eliminates the race condition where a 5-tick delayed
            // setRespawning(false) could be overwritten by a quick second death.
            int delay = Math.max(1, Config.respawnDelay());
            if (chunkLoader != null) chunkLoader.releaseForBot(fp);

            fp.setRespawning(true);
            final Player deadPlayer = (event.getEntity() instanceof Player p2) ? p2 : null;
            final UUID   botUuid    = fp.getUuid();

            Bukkit.getScheduler().runTaskLater(plugin, () -> {
                if (deadPlayer == null || !deadPlayer.isOnline()) {
                    fp.setRespawning(false);
                    manager.removeByName(name);
                    return;
                }

                // Trigger server-side respawn.  PlayerJoinListener only suppresses
                // the join message; all entity setup is done in the task below.
                deadPlayer.spigot().respawn();

                // 2 ticks later: find the NEW entity created by spigot().respawn()
                // and fully re-register it so future death/damage events work.
                Bukkit.getScheduler().runTaskLater(plugin, () -> {
                    Player newEntity = Bukkit.getPlayer(botUuid);
                    if (newEntity == null || newEntity.isDead()) {
                        // respawn failed or bot died again immediately
                        fp.setRespawning(false);
                        if (newEntity == null) manager.removeByName(name);
                        return;
                    }

                    // Re-register the new NMS entity
                    fp.setPlayer(newEntity);
                    fp.setAlive(true);
                    manager.registerEntityIndex(newEntity.getEntityId(), fp);

                    // Re-apply PDC identification tag (lost when new ServerPlayer is created)
                    try {
                        if (FakePlayerManager.FAKE_PLAYER_KEY != null) {
                            newEntity.getPersistentDataContainer().set(
                                FakePlayerManager.FAKE_PLAYER_KEY,
                                PersistentDataType.STRING,
                                FakePlayerBody.VISUAL_PDC_VALUE + ":" + fp.getName());
                        }
                    } catch (Exception ignored) {}

                    // Restore configured max health (vanilla respawn resets to 20 HP)
                    try {
                        var attr = newEntity.getAttribute(Attribute.MAX_HEALTH);
                        if (attr != null) {
                            double hp = Config.maxHealth();
                            attr.setBaseValue(hp);
                            newEntity.setHealth(hp);
                        }
                    } catch (Exception ignored) {}

                    // Clear the flag IMMEDIATELY - not in a delayed task - so a
                    // rapid second death won't see a stale flag and break the cycle.
                    fp.setRespawning(false);
                }, 2L);
            }, delay);

        } else {
            // ── Permanent removal (default) ────────────────────────────────────
            if (chunkLoader != null) chunkLoader.releaseForBot(fp);
            if (event.getEntity() instanceof Player deadPlayer) {
                Bukkit.getScheduler().runTaskLater(plugin, () -> {
                    me.bill.fakePlayerPlugin.fakeplayer.NmsPlayerSpawner.removeFakePlayer(deadPlayer);
                }, 20L);
            }
            Bukkit.getScheduler().runTaskLater(plugin, () -> {
                for (Player p : Bukkit.getOnlinePlayers()) PacketHelper.sendTabListRemove(p, fp);
                // NMS player's quit event fired naturally - no custom leave message needed
                manager.removeByName(name);
            }, 20L); // 1 second delay
        }
    }

    /**
     * Sets the respawn location for a bot that is mid in-place respawn.
     * Runs at HIGH priority so it overrides any default (world spawn / bed).
     */
    @EventHandler(priority = EventPriority.HIGH)
    public void onBotRespawn(PlayerRespawnEvent event) {
        FakePlayer fp = manager.getByName(event.getPlayer().getName());
        if (fp == null || !fp.isRespawning()) return;
        Location spawnLoc = fp.getSpawnLocation();
        if (spawnLoc != null && spawnLoc.getWorld() != null) {
            event.setRespawnLocation(spawnLoc);
        }
    }

    /**
     * Prevents bots from picking up items unless both the global {@code body.pick-up-items}
     * config flag AND the per-bot {@code pickUpItemsEnabled} flag are {@code true}.
     *
     * <p>Checking both guards here means:
     * <ul>
     *   <li>Global {@code false} → all bots blocked, regardless of per-bot value
     *       (covers bots spawned before the global was changed).</li>
     *   <li>Global {@code true} + per-bot {@code false} → only this bot blocked.</li>
     *   <li>Global {@code true} + per-bot {@code true} → pickup allowed.</li>
     * </ul>
     * Runs at NORMAL priority so anti-grief plugins still get first say.
     */
    @EventHandler(priority = EventPriority.NORMAL)
    public void onEntityPickupItem(EntityPickupItemEvent event) {
        if (!isFakeBotBody(event.getEntity())) return;

        FakePlayer fp = manager.getByUuid(event.getEntity().getUniqueId());
        if (fp == null) return;

        // Block pickup when the global config gate is off OR the per-bot flag is off.
        if (!Config.bodyPickUpItems() || !fp.isPickUpItemsEnabled()) {
            event.setCancelled(true);
            return;
        }

        // Refresh any open bot inventory GUI next tick, after Bukkit has actually inserted
        // the picked-up item into the bot inventory. Without this, viewers keep a stale
        // snapshot and closing the GUI can overwrite the newly picked-up item.
        Bukkit.getScheduler().runTask(plugin, () -> {
            var invCmd = plugin.getInventoryCommand();
            if (invCmd != null) invCmd.refreshOpenGui(fp.getUuid());
        });
    }


    /** True only for Player entities tagged as an FPP bot. */
    private boolean isFakeBotBody(Entity entity) {
        if (!(entity instanceof Player)) return false;
        if (FakePlayerManager.FAKE_PLAYER_KEY == null) return false;
        String val = entity.getPersistentDataContainer()
                .get(FakePlayerManager.FAKE_PLAYER_KEY, PersistentDataType.STRING);
        return val != null && val.startsWith(FakePlayerBody.VISUAL_PDC_VALUE);
    }
}

