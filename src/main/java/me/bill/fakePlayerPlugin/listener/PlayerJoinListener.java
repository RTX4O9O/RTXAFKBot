package me.bill.fakePlayerPlugin.listener;

import java.lang.reflect.Field;
import me.bill.fakePlayerPlugin.FakePlayerPlugin;
import me.bill.fakePlayerPlugin.config.Config;
import me.bill.fakePlayerPlugin.fakeplayer.FakePlayer;
import me.bill.fakePlayerPlugin.fakeplayer.FakePlayerManager;
import me.bill.fakePlayerPlugin.util.FppScheduler;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerQuitEvent;

public class PlayerJoinListener implements Listener {

  private final FakePlayerPlugin plugin;
  private final FakePlayerManager manager;

  private static volatile Field hasPlayedBeforeField = null;

  private static volatile Field firstPlayedField = null;
  private static volatile Field lastPlayedField = null;

  public PlayerJoinListener(FakePlayerPlugin plugin, FakePlayerManager manager) {
    this.plugin = plugin;
    this.manager = manager;
  }

  @EventHandler(priority = EventPriority.LOWEST)
  public void onJoinEarly(PlayerJoinEvent event) {

    FakePlayer fp = manager.getByName(event.getPlayer().getName());
    if (fp == null) fp = manager.getByUuid(event.getPlayer().getUniqueId());
    if (fp == null) return;

    if (event.getPlayer().getFirstPlayed() != 0L) {
      forceHasPlayedBefore(event.getPlayer());
    }

    if (plugin.isNameTagAvailable() && me.bill.fakePlayerPlugin.config.Config.nameTagIsolation()) {
      final java.util.UUID botUuid = event.getPlayer().getUniqueId();
      FppScheduler.runSyncLater(
          plugin,
          () -> {
            me.bill.fakePlayerPlugin.util.NameTagHelper.NickData nickData =
                me.bill.fakePlayerPlugin.util.NameTagHelper.clearBotFromCache(botUuid);

            me.bill.fakePlayerPlugin.fakeplayer.FakePlayer botFp = manager.getByUuid(botUuid);
            if (botFp != null && nickData != null) {

              botFp.setNameTagNick(nickData.nick());
              if (plugin.getSkinManager() != null && nickData.skin() != null) {
                plugin.getSkinManager().applyNameTagSkin(botFp, nickData.skin(), nickData.nick());
              }

              if (me.bill.fakePlayerPlugin.config.Config.nameTagSyncNickAsRename()
                  && nickData.canRename()
                  && !nickData.plainNick().equalsIgnoreCase(botFp.getName())) {
                final me.bill.fakePlayerPlugin.util.NameTagHelper.BotSkin savedSkin = nickData.skin();
                final String targetName = nickData.plainNick();
                new me.bill.fakePlayerPlugin.util.BotRenameHelper(plugin, manager)
                    .rename(org.bukkit.Bukkit.getConsoleSender(), botFp, targetName);

                if (savedSkin != null) {
                  schedulePostRenameSkinApply(targetName, savedSkin, botUuid);
                }
              }
            }
          },
          2L);
    }
  }

  @EventHandler(priority = EventPriority.LOWEST)
  public void onQuitEarly(PlayerQuitEvent event) {

    if (manager.isDespawning(event.getPlayer().getUniqueId())) {
      event.quitMessage(null);
      return;
    }

    if (manager.getCount() == 0) return;

    FakePlayer fp = manager.getByName(event.getPlayer().getName());
    if (fp == null) fp = manager.getByUuid(event.getPlayer().getUniqueId());
    if (fp == null) return;

    if (fp.isRespawning() || manager.isBodyTransitioning(fp.getUuid()) || !fp.isAlive()) {
      event.quitMessage(null);
    }
  }

  private static void forceHasPlayedBefore(Player player) {
    try {
      if (hasPlayedBeforeField == null) {
        hasPlayedBeforeField = findField(player.getClass(), "hasPlayedBefore");
      }
      if (hasPlayedBeforeField != null) {
        hasPlayedBeforeField.setBoolean(player, true);
      }
    } catch (Throwable ignored) {
    }
  }

  public static void stampFirstPlayed(Player player) {
    try {
      if (firstPlayedField == null) {
        firstPlayedField = findField(player.getClass(), "firstPlayed");
      }
      if (lastPlayedField == null) {
        lastPlayedField = findField(player.getClass(), "lastPlayed");
      }
      long now = System.currentTimeMillis();
      if (firstPlayedField != null) {
        long fp = firstPlayedField.getLong(player);
        if (fp == 0L) firstPlayedField.setLong(player, now - 60_000L);
      }
      if (lastPlayedField != null) {
        long lp = lastPlayedField.getLong(player);
        if (lp == 0L) lastPlayedField.setLong(player, now - 1_000L);
      }
    } catch (Throwable ignored) {
    }
  }

  private static Field findField(Class<?> clazz, String name) {
    Class<?> cur = clazz;
    while (cur != null && cur != Object.class) {
      try {
        Field f = cur.getDeclaredField(name);
        f.setAccessible(true);
        return f;
      } catch (NoSuchFieldException ignored) {
        cur = cur.getSuperclass();
      }
    }
    return null;
  }

  private void schedulePostRenameSkinApply(
      String renamedBotName,
      me.bill.fakePlayerPlugin.util.NameTagHelper.BotSkin skin,
      java.util.UUID oldUuid) {
    final int[] elapsed = {0};
    final int[] taskId = {-1};
    taskId[0] =
        FppScheduler.runSyncRepeatingWithId(
            plugin,
            () -> {
              elapsed[0] += 5;
              if (elapsed[0] > 120) {
                FppScheduler.cancelTask(taskId[0]);
                return;
              }
              me.bill.fakePlayerPlugin.fakeplayer.FakePlayer newBot = manager.getByName(renamedBotName);
              if (newBot == null) return;
              FppScheduler.cancelTask(taskId[0]);
              if (plugin.getSkinManager() != null) {
                plugin.getSkinManager().applyNameTagSkin(newBot, skin, renamedBotName);
              }
            },
            20L,
            5L);
  }

  @EventHandler(priority = EventPriority.MONITOR)
  public void onJoin(PlayerJoinEvent event) {

    FakePlayer fp = manager.getByName(event.getPlayer().getName());
    if (fp == null) fp = manager.getByUuid(event.getPlayer().getUniqueId());
    if (fp != null) {

      if (!Config.joinMessage()
          || fp.isRespawning()
          || manager.isBodyTransitioning(fp.getUuid())
          || manager.isRenaming(fp.getUuid())) {
        event.joinMessage(null);
      }

      return;
    }

    try {
      var upd = plugin.getUpdateNotification();
      if (upd != null) {
        var p = event.getPlayer();
        if (me.bill.fakePlayerPlugin.permission.Perm.hasOrOp(
                p, me.bill.fakePlayerPlugin.permission.Perm.OP)
            || me.bill.fakePlayerPlugin.permission.Perm.has(
                p, me.bill.fakePlayerPlugin.permission.Perm.NOTIFY)) {
          try {
            p.sendMessage(upd);
          } catch (NoSuchMethodError | NoClassDefFoundError e) {
            p.sendMessage(upd.toString());
          }
        }
      }
    } catch (Throwable ignored) {
    }

    try {
      if (plugin.isVersionUnsupported()
          && me.bill.fakePlayerPlugin.permission.Perm.hasOrOp(
              event.getPlayer(), me.bill.fakePlayerPlugin.permission.Perm.OP)) {
        event
            .getPlayer()
            .sendMessage(
                me.bill.fakePlayerPlugin.lang.Lang.get(
                    "version-unsupported-admin", "version", plugin.getDetectedMcVersion()));
      }
    } catch (Throwable ignored) {
    }

    if (manager.getCount() == 0 && plugin.getRemoteBotCache().count() == 0) return;

    try {
      var vc = plugin.getVelocityChannel();
      if (vc != null && vc.hasPendingResync()) {
        vc.clearPendingResync();
        FppScheduler.runSyncLater(plugin, vc::broadcastResyncRequest, 5L);
      }
    } catch (Throwable ignored) {
    }

    try {
      var vc = plugin.getVelocityChannel();
      if (vc != null && vc.hasPendingProxyBroadcast()) {
        vc.clearPendingProxyBroadcast();
        FppScheduler.runSyncLater(
            plugin,
            () -> {
              for (me.bill.fakePlayerPlugin.fakeplayer.FakePlayer botFp : manager.getActivePlayers()) {
                vc.broadcastBotSpawn(botFp);
              }
            },
            10L);
      }
    } catch (Throwable ignored) {
    }

    long delayTicks = manager.isRestorationInProgress() ? 40L : 5L;

    FppScheduler.runSyncLater(
        plugin,
        () -> {
          try {
            manager.syncToPlayer(event.getPlayer());

            var btt = plugin.getBotTabTeam();
            if (btt != null) btt.syncToPlayer(event.getPlayer());
          } catch (Throwable ignored) {
          }

          if (me.bill.fakePlayerPlugin.config.Config.isNetworkMode()
              && me.bill.fakePlayerPlugin.config.Config.tabListEnabled()) {
            try {
              var cache = plugin.getRemoteBotCache();
              if (cache != null) {
                for (var entry : cache.getAll()) {
                  me.bill.fakePlayerPlugin.fakeplayer.PacketHelper.sendTabListAddRaw(
                      event.getPlayer(),
                      entry.uuid(),
                      entry.packetProfileName(),
                      entry.displayName(),
                      entry.skinValue(),
                      entry.skinSignature());
                }
              }
            } catch (Throwable ignored) {
            }
          }
        },
        delayTicks);
  }

  @EventHandler(priority = EventPriority.MONITOR)
  public void onQuit(PlayerQuitEvent event) {
    java.util.UUID uuid = event.getPlayer().getUniqueId();

    if (manager.isDespawning(uuid)) {

      if (!Config.leaveMessage() || manager.isRenaming(uuid)) event.quitMessage(null);
      return;
    }

    if (manager.getCount() == 0) return;

    FakePlayer fp = manager.getByName(event.getPlayer().getName());
    if (fp == null) fp = manager.getByUuid(uuid);
    if (fp != null) {
      if (fp.isRespawning() || manager.isBodyTransitioning(fp.getUuid())) {

        event.quitMessage(null);
      } else if (!Config.leaveMessage() || !fp.isAlive()) {

        if (!Config.leaveMessage()) event.quitMessage(null);
      }

      return;
    }

    String name = event.getPlayer().getName();

    FppScheduler.runSyncLater(
        plugin,
        () -> {
          try {
            manager.validateUserBotNames(uuid, name);
          } catch (Throwable ignored) {
          }
        },
        2L);
  }
}
