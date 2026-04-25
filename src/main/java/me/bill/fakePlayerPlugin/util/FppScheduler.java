package me.bill.fakePlayerPlugin.util;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.Consumer;
import io.papermc.paper.threadedregions.scheduler.AsyncScheduler;
import io.papermc.paper.threadedregions.scheduler.GlobalRegionScheduler;
import io.papermc.paper.threadedregions.scheduler.RegionScheduler;
import io.papermc.paper.threadedregions.scheduler.ScheduledTask;
import org.bukkit.Location;
import org.bukkit.entity.Entity;
import org.bukkit.event.player.PlayerTeleportEvent;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.entity.Entity;
import org.bukkit.plugin.Plugin;

public final class FppScheduler {

  private static final AtomicInteger FOLIA_TASK_IDS = new AtomicInteger(2_000_000);
  private static final Map<Integer, ScheduledTask> FOLIA_TASKS = new ConcurrentHashMap<>();
  private static volatile Boolean foliaDetected;

  private FppScheduler() {}

  public static boolean isFolia() {
    Boolean cached = foliaDetected;
    if (cached != null) return cached;
    try {
      Bukkit.getServer().getGlobalRegionScheduler();
      foliaDetected = Boolean.TRUE;
    } catch (Throwable e) {
      foliaDetected = Boolean.FALSE;
    }
    return foliaDetected;
  }

  private static GlobalRegionScheduler globalScheduler() {
    try {
      return Bukkit.getServer().getGlobalRegionScheduler();
    } catch (Throwable ignored) {
      return null;
    }
  }

  private static RegionScheduler regionScheduler() {
    try {
      return Bukkit.getServer().getRegionScheduler();
    } catch (Throwable ignored) {
      return null;
    }
  }

  private static AsyncScheduler asyncScheduler() {
    try {
      return Bukkit.getServer().getAsyncScheduler();
    } catch (Throwable ignored) {
      return null;
    }
  }

  private static long foliaDelay(long delayTicks) {
    return Math.max(1L, delayTicks);
  }

  private static long foliaPeriod(long periodTicks) {
    return Math.max(1L, periodTicks);
  }

  /**
   * Run on the entity's owning region thread (Folia). On Paper/Spigot, runs on the main
   * thread. Use this for any entity mutation (teleport, setVelocity, inventory, etc.) that
   * might be dispatched from a global/async scheduler thread on Folia.
   */
  public static void runAtEntity(Plugin plugin, Entity entity, Runnable runnable) {
    if (entity == null) return;
    try {
      var entityScheduler = entity.getScheduler();
      if (entityScheduler != null) {
        entityScheduler.run(plugin, task -> runnable.run(), () -> {});
        return;
      }
    } catch (Throwable ignored) {
    }
    if (isFolia()) {
      try {
        runAtLocation(plugin, entity.getLocation(), runnable);
      } catch (Throwable ignored) {
      }
      return;
    }
    runSync(plugin, runnable);
  }

  public static int runAtEntityRepeatingWithId(
      Plugin plugin, Entity entity, Runnable runnable, long delayTicks, long periodTicks) {
    if (entity != null) {
      try {
        var entityScheduler = entity.getScheduler();
        if (entityScheduler != null) {
          int id = FOLIA_TASK_IDS.incrementAndGet();
          ScheduledTask task =
              entityScheduler.runAtFixedRate(
                  plugin,
                  scheduledTask -> runnable.run(),
                  () -> FOLIA_TASKS.remove(id),
                  foliaDelay(delayTicks),
                  foliaPeriod(periodTicks));
          if (task != null) FOLIA_TASKS.put(id, task);
          return id;
        }
      } catch (Throwable ignored) {
      }
    }
    if (isFolia() && entity != null) {
      try {
        int id = FOLIA_TASK_IDS.incrementAndGet();
        ScheduledTask task =
            regionScheduler()
                .runAtFixedRate(
                    plugin,
                    entity.getLocation(),
                    scheduledTask -> runnable.run(),
                    foliaDelay(delayTicks),
                    foliaPeriod(periodTicks));
        if (task != null) FOLIA_TASKS.put(id, task);
        return id;
      } catch (Throwable ignored) {
        return FOLIA_TASK_IDS.incrementAndGet();
      }
    }
    return runSyncRepeatingWithId(plugin, runnable, delayTicks, periodTicks);
  }

  public static int runAtEntityLaterWithId(
      Plugin plugin, Entity entity, Runnable runnable, long delayTicks) {
    if (entity != null) {
      try {
        var entityScheduler = entity.getScheduler();
        if (entityScheduler != null) {
          int id = FOLIA_TASK_IDS.incrementAndGet();
          ScheduledTask task =
              entityScheduler.runDelayed(
                  plugin,
                  scheduledTask -> {
                    try {
                      runnable.run();
                    } finally {
                      FOLIA_TASKS.remove(id);
                    }
                  },
                  () -> FOLIA_TASKS.remove(id),
                  foliaDelay(delayTicks));
          if (task != null) FOLIA_TASKS.put(id, task);
          return id;
        }
      } catch (Throwable ignored) {
      }
    }
    if (isFolia() && entity != null) {
      try {
        RegionScheduler scheduler = regionScheduler();
        if (scheduler == null) return FOLIA_TASK_IDS.incrementAndGet();
        int id = FOLIA_TASK_IDS.incrementAndGet();
        ScheduledTask task =
            scheduler.runDelayed(
                plugin,
                entity.getLocation(),
                scheduledTask -> {
                  try {
                    runnable.run();
                  } finally {
                    FOLIA_TASKS.remove(id);
                  }
                },
                foliaDelay(delayTicks));
        if (task != null) FOLIA_TASKS.put(id, task);
        return id;
      } catch (Throwable ignored) {
        return FOLIA_TASK_IDS.incrementAndGet();
      }
    }
    return runSyncLaterWithId(plugin, runnable, delayTicks);
  }

  /** Run on the region that owns the given location's chunk (Folia). Falls back to sync. */
  public static void runAtLocation(Plugin plugin, Location location, Runnable runnable) {
    if (location == null || location.getWorld() == null) {
      runSync(plugin, runnable);
      return;
    }
    RegionScheduler scheduler = regionScheduler();
    World world = location.getWorld();
    if (scheduler != null) {
      scheduler.execute(plugin, world, location.getBlockX() >> 4, location.getBlockZ() >> 4, runnable);
      return;
    }
    runSync(plugin, runnable);
  }

  public static void runSync(Plugin plugin, Runnable runnable) {
    GlobalRegionScheduler scheduler = globalScheduler();
    if (scheduler != null) {
      scheduler.execute(plugin, runnable);
      return;
    }
    Bukkit.getScheduler().runTask(plugin, runnable);
  }

  public static void runSyncRepeating(Plugin plugin, Runnable runnable, long delayTicks, long periodTicks) {
    GlobalRegionScheduler scheduler = globalScheduler();
    if (scheduler != null) {
      scheduler.runAtFixedRate(
          plugin, task -> runnable.run(), foliaDelay(delayTicks), foliaPeriod(periodTicks));
      return;
    }
    Bukkit.getScheduler().runTaskTimer(plugin, runnable, delayTicks, periodTicks);
  }

  public static int runSyncLaterWithId(Plugin plugin, Runnable runnable, long delayTicks) {
    GlobalRegionScheduler scheduler = globalScheduler();
    if (scheduler != null) {
      int id = FOLIA_TASK_IDS.incrementAndGet();
      ScheduledTask task =
          scheduler.runDelayed(
              plugin,
              scheduledTask -> {
                try {
                  runnable.run();
                } finally {
                  FOLIA_TASKS.remove(id);
                }
              },
              foliaDelay(delayTicks));
      if (task != null) FOLIA_TASKS.put(id, task);
      return id;
    }
    return Bukkit.getScheduler().runTaskLater(plugin, runnable, delayTicks).getTaskId();
  }

  public static int runSyncRepeatingWithId(
      Plugin plugin, Runnable runnable, long delayTicks, long periodTicks) {
    GlobalRegionScheduler scheduler = globalScheduler();
    if (scheduler != null) {
      int id = FOLIA_TASK_IDS.incrementAndGet();
      ScheduledTask task =
          scheduler.runAtFixedRate(
              plugin,
              scheduledTask -> runnable.run(),
              foliaDelay(delayTicks),
              foliaPeriod(periodTicks));
      if (task != null) FOLIA_TASKS.put(id, task);
      return id;
    }
    return Bukkit.getScheduler().runTaskTimer(plugin, runnable, delayTicks, periodTicks).getTaskId();
  }

  public static void runSyncLater(Plugin plugin, Runnable runnable, long delayTicks) {
    GlobalRegionScheduler scheduler = globalScheduler();
    if (scheduler != null) {
      scheduler.runDelayed(plugin, task -> runnable.run(), foliaDelay(delayTicks));
      return;
    }
    Bukkit.getScheduler().runTaskLater(plugin, runnable, delayTicks);
  }

  public static void runAsync(Plugin plugin, Runnable runnable) {
    AsyncScheduler scheduler = asyncScheduler();
    if (scheduler != null) {
      scheduler.runNow(plugin, task -> runnable.run());
      return;
    }
    Bukkit.getScheduler().runTaskAsynchronously(plugin, runnable);
  }

  public static void teleportAsync(Entity entity, Location dest) {
    if (entity instanceof org.bukkit.entity.Player p) {
      p.teleportAsync(dest, PlayerTeleportEvent.TeleportCause.PLUGIN);
    } else {
      entity.teleport(dest);
    }
  }

  public static void cancelTask(int taskId) {
    ScheduledTask foliaTask = FOLIA_TASKS.remove(taskId);
    if (foliaTask != null) {
      foliaTask.cancel();
      return;
    }

    if (taskId >= 2_000_000) {
      return;
    }

    Bukkit.getScheduler().cancelTask(taskId);
  }
}
