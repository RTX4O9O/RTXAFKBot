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
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.Sound;
import org.bukkit.SoundCategory;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.EntityDamageByEntityEvent;
import org.bukkit.event.entity.EntityDamageEvent;
import org.bukkit.event.entity.EntityDeathEvent;
import org.bukkit.event.entity.EntityPortalEvent;
import org.bukkit.event.entity.EntityTeleportEvent;
import org.bukkit.persistence.PersistentDataType;

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

        // Primary damageable guard — event-level cancellation beats any entity flag.
        // Checked live from config so /fpp reload takes effect without respawn.
        if (!Config.bodyDamageable()) {
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

        // Play player hurt sound
        if (Config.hurtSound()) {
            Location loc = event.getEntity().getLocation();
            for (Player p : Bukkit.getOnlinePlayers())
                p.playSound(loc, Sound.ENTITY_PLAYER_HURT, SoundCategory.PLAYERS, 1.0f, 1.0f);
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
        if (from.getWorld().equals(to.getWorld())) return; // same world — fine
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
            BotBroadcast.broadcastKill(killer.getName(), fp.getDisplayName());
        }

        // Increment death counter
        fp.incrementDeathCount();
        fp.setAlive(false);

        final Location respawnLoc = fp.getSpawnLocation() != null
                ? fp.getSpawnLocation().clone()
                : event.getEntity().getLocation().clone();
        final String name        = fp.getName();
        final String displayName = fp.getDisplayName();

        // Remove the dead player entity after the death animation (~1 second)
        if (event.getEntity() instanceof Player deadPlayer) {
            Bukkit.getScheduler().runTaskLater(plugin, () -> {
                me.bill.fakePlayerPlugin.fakeplayer.NmsPlayerSpawner.removeFakePlayer(deadPlayer);
            }, 20L);
        }

        // Clear references
        fp.setPlayer(null);
        manager.removeFromEntityIndex(event.getEntity().getEntityId());

        if (Config.respawnOnDeath()) {
            int delay = Math.max(20, Config.respawnDelay()); // Minimum 1 second
            if (chunkLoader != null) chunkLoader.releaseForBot(fp);

            Bukkit.getScheduler().runTaskLater(plugin, () -> {
                // Remove from tab list while dead
                for (Player p : Bukkit.getOnlinePlayers()) PacketHelper.sendTabListRemove(p, fp);

                // Respawn the bot with a new NMS ServerPlayer entity
                if (Config.spawnBody()) {
                    Player newBody = FakePlayerBody.spawn(fp, respawnLoc);
                    if (newBody == null) {
                        // Spawn failed — bot already kicked, vanilla quit message shown
                        manager.removeByName(name);
                        return;
                    }
                    fp.setPlayer(newBody);
                    fp.setAlive(true);
                    manager.registerEntityIndex(newBody.getEntityId(), fp);
                    
                    // Re-add to tab list after respawn
                    Bukkit.getScheduler().runTaskLater(plugin, () -> {
                        if (Config.tabListEnabled())
                            for (Player p : Bukkit.getOnlinePlayers())
                                PacketHelper.sendTabListAdd(p, fp);
                    }, 5L);
                } else {
                    // Body disabled but bot should "respawn" — re-add to tab list
                    fp.setAlive(true);
                    if (Config.tabListEnabled())
                        for (Player p : Bukkit.getOnlinePlayers())
                            PacketHelper.sendTabListAdd(p, fp);
                }

                fp.setSpawnLocation(respawnLoc);
            }, delay);

        } else {
            if (chunkLoader != null) chunkLoader.releaseForBot(fp);
            Bukkit.getScheduler().runTaskLater(plugin, () -> {
                for (Player p : Bukkit.getOnlinePlayers()) PacketHelper.sendTabListRemove(p, fp);
                // NMS player's quit event fired naturally — no custom leave message needed
                manager.removeByName(name);
            }, 20L); // 1 second delay
        }
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
