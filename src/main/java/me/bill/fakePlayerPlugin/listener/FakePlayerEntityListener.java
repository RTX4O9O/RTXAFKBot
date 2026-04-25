package me.bill.fakePlayerPlugin.listener;

import java.util.UUID;
import me.bill.fakePlayerPlugin.FakePlayerPlugin;
import me.bill.fakePlayerPlugin.config.Config;
import me.bill.fakePlayerPlugin.fakeplayer.BotBroadcast;
import me.bill.fakePlayerPlugin.fakeplayer.ChunkLoader;
import me.bill.fakePlayerPlugin.fakeplayer.FakePlayer;
import me.bill.fakePlayerPlugin.fakeplayer.FakePlayerBody;
import me.bill.fakePlayerPlugin.fakeplayer.FakePlayerManager;
import me.bill.fakePlayerPlugin.fakeplayer.PacketHelper;
import me.bill.fakePlayerPlugin.api.event.FppBotDamageEvent;
import me.bill.fakePlayerPlugin.api.impl.FppBotImpl;
import me.bill.fakePlayerPlugin.util.FppScheduler;
import me.bill.fakePlayerPlugin.util.WorldGuardHelper;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.Sound;
import org.bukkit.SoundCategory;
import me.bill.fakePlayerPlugin.util.AttributeCompat;
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
import org.bukkit.event.entity.PlayerDeathEvent;
import org.bukkit.event.player.PlayerPortalEvent;
import org.bukkit.event.player.PlayerRespawnEvent;
import org.bukkit.persistence.PersistentDataType;

public class FakePlayerEntityListener implements Listener {

  private final FakePlayerPlugin plugin;
  private final FakePlayerManager manager;
  private final ChunkLoader chunkLoader;

  public FakePlayerEntityListener(
      FakePlayerPlugin plugin, FakePlayerManager manager, ChunkLoader chunkLoader) {
    this.plugin = plugin;
    this.manager = manager;
    this.chunkLoader = chunkLoader;
  }

  /** Suppress the vanilla death message when messages.death-message is false. */
  @EventHandler(priority = EventPriority.LOWEST)
  public void onBotDeathMessage(PlayerDeathEvent event) {
    if (!isFakeBotBody(event.getEntity())) return;
    if (!Config.deathMessage()) {
      event.deathMessage(null);
    }
  }

  @EventHandler(priority = EventPriority.LOW, ignoreCancelled = true)
  public void onEntityDamage(EntityDamageEvent event) {
    if (!isFakeBotBody(event.getEntity())) return;
    if (!(event.getEntity() instanceof Player p)) return;
    FakePlayer fp = manager.getByEntity(p);
    if (fp == null) return;

    Entity damager = null;
    if (event instanceof EntityDamageByEntityEvent byEntity) {
      damager = byEntity.getDamager();
    }

    var damageEvent = new FppBotDamageEvent(
        new FppBotImpl(fp), event.getFinalDamage(), event.getCause(), damager);
    Bukkit.getPluginManager().callEvent(damageEvent);
    if (damageEvent.isCancelled()) {
      event.setCancelled(true);
      return;
    }
    if (damageEvent.getDamage() != event.getFinalDamage()) {
      event.setDamage(damageEvent.getDamage());
    }

    if (event instanceof EntityDamageByEntityEvent byEntity
        && byEntity.getDamager() instanceof Player attacker) {
      if (plugin.isWorldGuardAvailable()
          && !WorldGuardHelper.isPvpAllowed(event.getEntity().getLocation())) {
        event.setCancelled(true);
        return;
      }
    }

    if (!Config.bodyDamageable()) {
      if (event instanceof EntityDamageByEntityEvent) {
        event.setCancelled(true);
        return;
      }
    }

    if (!event.isCancelled()) {
      fp.addDamageTaken(event.getFinalDamage());
    }
  }

  @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
  public void onEntityTarget(org.bukkit.event.entity.EntityTargetLivingEntityEvent event) {
    if (!(event.getTarget() instanceof Player targetPlayer)) return;
    FakePlayer fp = manager.getByEntity(targetPlayer);
    if (fp == null) return;
    var targetEvt = new me.bill.fakePlayerPlugin.api.event.FppBotTargetEvent(
        new FppBotImpl(fp), event.getEntity());
    Bukkit.getPluginManager().callEvent(targetEvt);
    if (targetEvt.isCancelled()) event.setCancelled(true);
  }

  @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
  public void onEntityDamageConfirmed(EntityDamageEvent event) {
    if (!(event.getEntity() instanceof Player bot)) return;
    FakePlayer fp = manager.getByEntity(bot);
    if (fp == null) return;
    manager.playHurtFeedback(fp, bot);
  }

  @EventHandler(priority = EventPriority.NORMAL, ignoreCancelled = true)
  public void onEntityPortal(EntityPortalEvent event) {
    if (!isFakeBotBody(event.getEntity())) return;
    event.setCancelled(true);
    FakePlayer fp = manager.getByEntityId(event.getEntity().getEntityId());
    Config.debug(
        "Blocked portal traversal for bot body: " + (fp != null ? fp.getName() : "unknown"));
  }

  @EventHandler(priority = EventPriority.LOWEST, ignoreCancelled = true)
  public void onPlayerPortal(PlayerPortalEvent event) {
    if (!isFakeBotBody(event.getPlayer())) return;
    event.setCancelled(true);
    Config.debug("Blocked portal traversal for bot: " + event.getPlayer().getName());
  }

  @EventHandler(priority = EventPriority.NORMAL, ignoreCancelled = true)
  public void onEntityTeleport(EntityTeleportEvent event) {
    if (!isFakeBotBody(event.getEntity())) return;
    org.bukkit.Location from = event.getFrom();
    org.bukkit.Location to = event.getTo();
    if (to == null || from.getWorld() == null || to.getWorld() == null) return;
    if (!from.getWorld().equals(to.getWorld())) {

      event.setCancelled(true);
      Config.debug("Blocked cross-world teleport for bot body: " + event.getEntity().getName());
    }
  }

  @EventHandler(priority = EventPriority.MONITOR)
  public void onEntityDeath(EntityDeathEvent event) {
    if (!isFakeBotBody(event.getEntity())) return;

    FakePlayer fp = manager.getByEntity(event.getEntity());
    if (fp == null) return;

    if (Config.suppressDrops()) {
      event.getDrops().clear();
      event.setDroppedExp(0);
    }

    Player killer = event.getEntity().getKiller();
    if (killer != null) {

      String displayName =
          fp.getRawDisplayName() != null ? fp.getRawDisplayName() : fp.getDisplayName();
      BotBroadcast.broadcastKill(killer.getName(), displayName);
    }

    fp.incrementDeathCount();
    fp.setAlive(false);

    // Fire API death event.
    var fppApi = plugin.getFppApi();
    if (fppApi != null) {
      me.bill.fakePlayerPlugin.api.event.FppBotDeathEvent deathEvt =
          new me.bill.fakePlayerPlugin.api.event.FppBotDeathEvent(
              new me.bill.fakePlayerPlugin.api.impl.FppBotImpl(fp), killer);
      org.bukkit.Bukkit.getPluginManager().callEvent(deathEvt);
    }

    final String name = fp.getName();

    fp.setPlayer(null);
    manager.removeFromEntityIndex(event.getEntity().getEntityId());

    if (Config.respawnOnDeath()) {

      int delay = Math.max(1, Config.respawnDelay());
      if (chunkLoader != null) chunkLoader.releaseForBot(fp);

      fp.setRespawning(true);
      final Player deadPlayer = (event.getEntity() instanceof Player p2) ? p2 : null;
      final UUID botUuid = fp.getUuid();

      FppScheduler.runSyncLater(
          plugin,
          () -> {
            if (deadPlayer == null || !deadPlayer.isOnline()) {
              fp.setRespawning(false);
              manager.removeByName(name);
              return;
            }

            deadPlayer.spigot().respawn();

            FppScheduler.runSyncLater(
                plugin,
                () -> {
                  Player newEntity = Bukkit.getPlayer(botUuid);
                  if (newEntity == null || newEntity.isDead()) {

                    fp.setRespawning(false);
                    if (newEntity == null) manager.removeByName(name);
                    return;
                  }

                  fp.setPlayer(newEntity);
                  fp.setAlive(true);
                  manager.registerEntityIndex(newEntity.getEntityId(), fp);

                  try {
                    if (FakePlayerManager.FAKE_PLAYER_KEY != null) {
                      newEntity
                          .getPersistentDataContainer()
                          .set(
                              FakePlayerManager.FAKE_PLAYER_KEY,
                              PersistentDataType.STRING,
                              FakePlayerBody.VISUAL_PDC_VALUE + ":" + fp.getName());
                    }
                  } catch (Exception ignored) {
                  }

                  try {
                    var attr = newEntity.getAttribute(AttributeCompat.MAX_HEALTH);
                    if (attr != null) {
                      double hp = Config.maxHealth();
                      attr.setBaseValue(hp);
                      newEntity.setHealth(hp);
                    }
                  } catch (Exception ignored) {
                  }

                  fp.setRespawning(false);
                },
                2L);
          },
          delay);

    } else {

      if (chunkLoader != null) chunkLoader.releaseForBot(fp);
      if (event.getEntity() instanceof Player deadPlayer) {
        FppScheduler.runSyncLater(
            plugin,
            () -> {
              me.bill.fakePlayerPlugin.fakeplayer.NmsPlayerSpawner.removeFakePlayer(deadPlayer);
            },
            20L);
      }
      FppScheduler.runSyncLater(
          plugin,
          () -> {
            for (Player p : Bukkit.getOnlinePlayers()) PacketHelper.sendTabListRemove(p, fp);

            manager.removeByName(name);
          },
          20L);
    }
  }

  @EventHandler(priority = EventPriority.HIGH)
  public void onBotRespawn(PlayerRespawnEvent event) {
    FakePlayer fp = manager.getByName(event.getPlayer().getName());
    if (fp == null || !fp.isRespawning()) return;
    Location spawnLoc = fp.getSpawnLocation();
    if (spawnLoc != null && spawnLoc.getWorld() != null) {
      event.setRespawnLocation(spawnLoc);
    }
  }

  @EventHandler(priority = EventPriority.NORMAL)
  public void onEntityPickupItem(EntityPickupItemEvent event) {
    if (!isFakeBotBody(event.getEntity())) return;

    FakePlayer fp = manager.getByUuid(event.getEntity().getUniqueId());
    if (fp == null) return;

    var invEvt = new me.bill.fakePlayerPlugin.api.event.FppBotInventoryEvent(
        new FppBotImpl(fp),
        me.bill.fakePlayerPlugin.api.event.FppBotInventoryEvent.Action.PICKUP,
        event.getItem().getItemStack(),
        -1);
    Bukkit.getPluginManager().callEvent(invEvt);
    if (invEvt.isCancelled()) {
      event.setCancelled(true);
      return;
    }

    if (!Config.bodyPickUpItems() || !fp.isPickUpItemsEnabled()) {
      event.setCancelled(true);
      return;
    }

    // Bot inventory is viewed natively; no manual refresh needed.
  }

  private boolean isFakeBotBody(Entity entity) {
    if (!(entity instanceof Player)) return false;

    return manager.getByEntityId(entity.getEntityId()) != null;
  }
}
