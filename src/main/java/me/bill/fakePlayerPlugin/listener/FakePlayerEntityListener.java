package me.bill.fakePlayerPlugin.listener;

import me.bill.fakePlayerPlugin.FakePlayerPlugin;
import me.bill.fakePlayerPlugin.config.Config;
import me.bill.fakePlayerPlugin.fakeplayer.BotBroadcast;
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
import org.bukkit.entity.Mannequin;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.EntityDamageEvent;
import org.bukkit.event.entity.EntityDeathEvent;
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

        // Cancel damage types that shouldn't affect a player-like entity
        switch (event.getCause()) {
            case VOID, SUFFOCATION, CRAMMING,
                 STARVATION, FREEZE, MELTING,
                 FLY_INTO_WALL, DRYOUT,
                 POISON, WITHER, MAGIC,
                 LIGHTNING -> {
                event.setCancelled(true);
                return;
            }
            default -> {}
        }

        // On fatal hit — remove nametag before death animation plays
        if (event.getEntity() instanceof Mannequin m) {
            double remaining = m.getHealth() - event.getFinalDamage();
            if (remaining <= 0) {
                FakePlayer fp = manager.getByEntity(m);
                if (fp != null) FakePlayerBody.removeNametag(fp);
            }
        }

        // Play player hurt sound
        if (Config.hurtSound()) {
            Location loc = event.getEntity().getLocation();
            for (Player p : Bukkit.getOnlinePlayers())
                p.playSound(loc, Sound.ENTITY_PLAYER_HURT, SoundCategory.PLAYERS, 1.0f, 1.0f);
        }
    }

    // ── Death ─────────────────────────────────────────────────────────────────

    @EventHandler(priority = EventPriority.MONITOR)
    public void onEntityDeath(EntityDeathEvent event) {
        if (!isFakeBotBody(event.getEntity())) return;

        if (Config.suppressDrops()) {
            event.getDrops().clear();
            event.setDroppedExp(0);
        }

        FakePlayer fp = manager.getByEntity(event.getEntity());
        if (fp == null) return;

        Player killer = event.getEntity().getKiller();
        if (killer != null) {
            BotBroadcast.broadcastKill(killer.getName(), fp.getDisplayName());
        }

        final Location respawnLoc = fp.getSpawnLocation() != null
                ? fp.getSpawnLocation().clone()
                : event.getEntity().getLocation().clone();
        final String name        = fp.getName();
        final String displayName = fp.getDisplayName();

        // Remove nametag immediately (entity is dead but ArmorStand is not)
        FakePlayerBody.removeNametag(fp);
        // Clear physics entity reference — the Mannequin is now dead/invalid
        fp.setPhysicsEntity(null);
        // Remove from entity-id index so stale lookups don't return this fp
        manager.removeFromEntityIndex(event.getEntity().getEntityId());

        if (Config.respawnOnDeath()) {
            int delay = Math.max(1, Config.respawnDelay());
            if (chunkLoader != null) chunkLoader.releaseForBot(fp);

            Bukkit.getScheduler().runTaskLater(plugin, () -> {
                // Remove from tab list while dead
                for (Player p : Bukkit.getOnlinePlayers()) PacketHelper.sendTabListRemove(p, fp);

                // Double-check no orphaned nametag survived
                FakePlayerBody.removeOrphanedNametags(name);

                if (Config.spawnBody()) {
                    Entity newBody = FakePlayerBody.spawn(fp, respawnLoc);
                    if (newBody == null) {
                        broadcastLeave(displayName);
                        manager.removeByName(name);
                        return;
                    }
                    fp.setPhysicsEntity(newBody);
                    manager.registerEntityIndex(newBody.getEntityId(), fp);
                        Bukkit.getScheduler().runTaskLater(plugin, () -> {
                            if (!newBody.isValid()) return;
                            FakePlayerBody.applyResolvedSkin(plugin, fp, newBody);
                            fp.setNametagEntity(FakePlayerBody.spawnNametag(fp, newBody));
                            Bukkit.getScheduler().runTaskLater(plugin, () -> {
                                if (Config.tabListEnabled())
                                    for (Player p : Bukkit.getOnlinePlayers())
                                        PacketHelper.sendTabListAdd(p, fp);
                            }, 20L);
                        }, 1L);
                    } else {
                        // Body disabled but bot should "respawn" — re-add to tab list
                        fp.setNametagEntity(null);
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
                // World-scan cleanup — catches any entity the direct ref missed
                FakePlayerBody.removeOrphanedNametags(name);
                FakePlayerBody.removeOrphanedBodies(name);
                broadcastLeave(displayName);
                manager.removeByName(name);
            }, 1L);
        }
    }

    // ── Helpers ───────────────────────────────────────────────────────────────

    private void broadcastLeave(String displayName) {
        BotBroadcast.broadcastLeaveByDisplayName(displayName);
    }

    /** True only for Mannequin entities tagged as an FPP physics body. */
    private boolean isFakeBotBody(Entity entity) {
        if (!(entity instanceof Mannequin)) return false;
        if (FakePlayerManager.FAKE_PLAYER_KEY == null) return false;
        String val = entity.getPersistentDataContainer()
                .get(FakePlayerManager.FAKE_PLAYER_KEY, PersistentDataType.STRING);
        return val != null
                && !val.startsWith(FakePlayerBody.NAMETAG_PDC_VALUE)
                && !val.startsWith(FakePlayerBody.VISUAL_PDC_VALUE);
    }
}
