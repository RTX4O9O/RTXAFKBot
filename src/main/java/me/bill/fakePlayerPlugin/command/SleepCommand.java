package me.bill.fakePlayerPlugin.command;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import me.bill.fakePlayerPlugin.FakePlayerPlugin;
import me.bill.fakePlayerPlugin.fakeplayer.FakePlayer;
import me.bill.fakePlayerPlugin.fakeplayer.FakePlayerManager;
import me.bill.fakePlayerPlugin.fakeplayer.PathfindingService;
import me.bill.fakePlayerPlugin.lang.Lang;
import me.bill.fakePlayerPlugin.permission.Perm;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.Tag;
import org.bukkit.World;
import org.bukkit.block.Block;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.bukkit.scheduler.BukkitTask;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/**
 * /fpp sleep &lt;bot|all&gt; &lt;x&gt; &lt;y&gt; &lt;z&gt; &lt;radius&gt;
 *   — Set the sleep origin and radius for a bot. The bot will automatically walk to
 *     the nearest bed within the radius (measured from the given origin) when night
 *     falls and wake up when dawn arrives.
 *
 * /fpp sleep &lt;bot|all&gt; --stop
 *   — Disable the sleep system for one or all bots. Also disables via radius &lt;= 0.
 */
public final class SleepCommand implements FppCommand {

  // ── Night/day thresholds (Minecraft ticks within a 24 000-tick day) ─────────
  private static final long NIGHT_START = 12541L;
  private static final long NIGHT_END   = 23999L;

  // ── Navigation / timer tuning ─────────────────────────────────────────────
  /** Ticks between each night-watch sweep. */
  private static final long CHECK_INTERVAL_TICKS = 40L;
  /** How close (XZ) a bot must get to a bed to attempt sleep. */
  private static final double ARRIVE_DISTANCE = 1.5;
  /** Maximum null-path recalculations before giving up navigation to the bed. */
  private static final int MAX_NULL_RECALCS = 5;

  private final FakePlayerPlugin plugin;
  private final FakePlayerManager manager;
  private final PathfindingService pathfinding;

  @Nullable private MineCommand mineCommand;
  @Nullable private UseCommand useCommand;
  @Nullable private PlaceCommand placeCommand;
  @Nullable private AttackCommand attackCommand;
  @Nullable private FollowCommand followCommand;
  @Nullable private MoveCommand moveCommand;

  /** Activity types that can be resumed after waking up. */
  private enum Activity {
    NONE,
    MINE,
    USE,
    PLACE,
    ATTACK,
    FOLLOW,
    ROAM,
    WAYPOINT
  }

  /** Stores the previous waypoint route name for a bot. */
  private final Map<UUID, String> previousWaypointRoute = new ConcurrentHashMap<>();

  /** Stores the previous roam center for a bot. */
  private final Map<UUID, Location> previousRoamCenter = new ConcurrentHashMap<>();

  /** Stores the previous roam radius for a bot. */
  private final Map<UUID, Double> previousRoamRadius = new ConcurrentHashMap<>();

  /** Tracks which bots were doing what activity before sleeping. */
  private final Map<UUID, Activity> previousActivity = new ConcurrentHashMap<>();

  /**
   * Tracks which bots are currently navigating toward a bed (SLEEP pathfinding owner).
   * Prevents the night-watch tick from issuing duplicate navigation requests.
   */
  private final Set<UUID> navigatingToBed = ConcurrentHashMap.newKeySet();

  /**
   * Caches the last successfully-found bed location per bot. Cleared when:
   *   - sleep is disabled (/fpp sleep ... --stop)
   *   - navigation to the bed fails / is cancelled
   *   - sleep attempt fails (bed no longer valid)
   */
  private final Map<UUID, Location> cachedBeds = new ConcurrentHashMap<>();

  /** The single global repeating task that checks all configured bots. null = not running. */
  @Nullable private BukkitTask nightWatchTask = null;

  public SleepCommand(
      @NotNull FakePlayerPlugin plugin,
      @NotNull FakePlayerManager manager,
      @NotNull PathfindingService pathfinding) {
    this.plugin = plugin;
    this.manager = manager;
    this.pathfinding = pathfinding;
  }

  public void setMineCommand(@Nullable MineCommand cmd) { this.mineCommand = cmd; }
  public void setUseCommand(@Nullable UseCommand cmd) { this.useCommand = cmd; }
  public void setPlaceCommand(@Nullable PlaceCommand cmd) { this.placeCommand = cmd; }
  public void setAttackCommand(@Nullable AttackCommand cmd) { this.attackCommand = cmd; }
  public void setFollowCommand(@Nullable FollowCommand cmd) { this.followCommand = cmd; }
  public void setMoveCommand(@Nullable MoveCommand cmd) { this.moveCommand = cmd; }

  // ── FppCommand metadata ──────────────────────────────────────────────────

  @Override public String getName() { return "sleep"; }

  @Override
  public String getUsage() {
    return "<bot|all> <x y z> <radius>  |  <bot|all> --stop";
  }

  @Override
  public String getDescription() {
    return "Set a sleep-origin for a bot so it auto-sleeps at night.";
  }

  @Override public String getPermission() { return Perm.SLEEP; }

  @Override
  public boolean canUse(CommandSender sender) {
    return Perm.has(sender, Perm.SLEEP);
  }

  // ── Command execution ─────────────────────────────────────────────────────

  @Override
  public boolean execute(CommandSender sender, String[] args) {
    if (args.length < 2) {
      sender.sendMessage(Lang.get("sleep-usage"));
      return true;
    }

    boolean isAll = args[0].equalsIgnoreCase("all");

    // ── --stop variant ────────────────────────────────────────────────────
    if (args[1].equalsIgnoreCase("--stop")) {
      if (isAll) {
        int count = 0;
        for (FakePlayer fp : manager.getActivePlayers()) {
          if (fp.getSleepRadius() > 0 || fp.getSleepOrigin() != null) {
            disableSleep(fp);
            count++;
          }
        }
        sender.sendMessage(Lang.get("sleep-all-stopped", "count", String.valueOf(count)));
      } else {
        FakePlayer fp = manager.getByName(args[0]);
        if (fp == null) {
          sender.sendMessage(Lang.get("sleep-not-found", "name", args[0]));
          return true;
        }
        disableSleep(fp);
        sender.sendMessage(Lang.get("sleep-stopped", "name", fp.getDisplayName()));
      }
      return true;
    }

    // ── Set origin + radius variant ───────────────────────────────────────
    if (args.length < 4) {
      sender.sendMessage(Lang.get("sleep-usage"));
      return true;
    }

    double x, y, z, radius;

    if (args.length == 4 && args[1].contains(" ")) {
      String[] parts = args[1].split(" ");
      if (parts.length != 3) {
        sender.sendMessage(Lang.get("sleep-invalid-args"));
        return true;
      }
      try {
        x = Double.parseDouble(parts[0]);
        y = Double.parseDouble(parts[1]);
        z = Double.parseDouble(parts[2]);
        radius = Double.parseDouble(args[3]);
      } catch (NumberFormatException e) {
        sender.sendMessage(Lang.get("sleep-invalid-args"));
        return true;
      }
    } else if (args.length >= 5) {
      try {
        x      = Double.parseDouble(args[1]);
        y      = Double.parseDouble(args[2]);
        z      = Double.parseDouble(args[3]);
        radius = Double.parseDouble(args[4]);
      } catch (NumberFormatException e) {
        sender.sendMessage(Lang.get("sleep-invalid-args"));
        return true;
      }
    } else {
      sender.sendMessage(Lang.get("sleep-usage"));
      return true;
    }

    // radius <= 0 → disable
    if (radius <= 0) {
      if (isAll) {
        int count = 0;
        for (FakePlayer fp : manager.getActivePlayers()) {
          if (fp.getSleepRadius() > 0 || fp.getSleepOrigin() != null) {
            disableSleep(fp);
            count++;
          }
        }
        sender.sendMessage(Lang.get("sleep-all-stopped", "count", String.valueOf(count)));
      } else {
        FakePlayer fp = manager.getByName(args[0]);
        if (fp == null) {
          sender.sendMessage(Lang.get("sleep-not-found", "name", args[0]));
          return true;
        }
        disableSleep(fp);
        sender.sendMessage(Lang.get("sleep-stopped", "name", fp.getDisplayName()));
      }
      return true;
    }

    // Configure sleep for bot(s)
    if (isAll) {
      int started = 0, skipped = 0;
      for (FakePlayer fp : manager.getActivePlayers()) {
        Player bot = fp.getPlayer();
        if (bot == null || !bot.isOnline()) { skipped++; continue; }
        Location origin = new Location(bot.getWorld(), x, y, z);
        configureSleep(fp, origin, radius);
        started++;
      }
      sender.sendMessage(Lang.get("sleep-all-configured",
          "count",  String.valueOf(started),
          "radius", String.valueOf((int) radius),
          "skipped", String.valueOf(skipped)));
    } else {
      FakePlayer fp = manager.getByName(args[0]);
      if (fp == null) {
        sender.sendMessage(Lang.get("sleep-not-found", "name", args[0]));
        return true;
      }
      Player bot = fp.getPlayer();
      if (bot == null || !bot.isOnline()) {
        sender.sendMessage(Lang.get("sleep-bot-offline", "name", args[0]));
        return true;
      }
      Location origin = new Location(bot.getWorld(), x, y, z);
      configureSleep(fp, origin, radius);
      sender.sendMessage(Lang.get("sleep-configured",
          "name",   fp.getDisplayName(),
          "x",      String.valueOf((int) x),
          "y",      String.valueOf((int) y),
          "z",      String.valueOf((int) z),
          "radius", String.valueOf((int) radius)));
    }
    return true;
  }

  // ── Configuration helpers ─────────────────────────────────────────────────

  private void configureSleep(@NotNull FakePlayer fp, @NotNull Location origin, double radius) {
    fp.setSleepOrigin(origin);
    fp.setSleepRadius(radius);
    cachedBeds.remove(fp.getUuid());      // force bed re-scan with new config
    ensureNightWatchRunning();
  }

  private void disableSleep(@NotNull FakePlayer fp) {
    UUID uuid = fp.getUuid();
    fp.setSleepOrigin(null);
    fp.setSleepRadius(0.0);
    cachedBeds.remove(uuid);

    // Cancel any in-progress navigation toward a bed
    if (navigatingToBed.remove(uuid)) {
      pathfinding.cancel(uuid);
    }

    // Wake if currently asleep
    if (fp.isSleeping()) {
      wakeBot(fp);
    }

    checkNightWatchNeeded();
  }

  // ── Night-watch task management ───────────────────────────────────────────

  private void ensureNightWatchRunning() {
    if (!plugin.isEnabled()) return;
    if (nightWatchTask != null && !nightWatchTask.isCancelled()) return;
    nightWatchTask = Bukkit.getScheduler().runTaskTimer(
        plugin, this::nightWatchTick, 40L, CHECK_INTERVAL_TICKS);
  }

  /**
   * Stops the night-watch task if no bots have sleep configured.
   */
  private void checkNightWatchNeeded() {
    if (nightWatchTask == null || nightWatchTask.isCancelled()) return;

    // Scan active players for any that still have sleep configured
    boolean anyConfigured = manager.getActivePlayers().stream()
        .anyMatch(fp -> fp.getSleepRadius() > 0);
    if (!anyConfigured) {
      nightWatchTask.cancel();
      nightWatchTask = null;
    }
  }

  // ── Main night-watch tick (runs every 40 ticks on the main thread) ────────

  private void nightWatchTick() {
    if (!plugin.isEnabled()) return;

    for (FakePlayer fp : manager.getActivePlayers()) {
      if (fp.getSleepRadius() <= 0) continue;

      Player bot = fp.getPlayer();
      if (bot == null || !bot.isOnline()) continue;

      UUID uuid = fp.getUuid();
      long time = bot.getWorld().getTime();
      boolean isNight = time >= NIGHT_START && time <= NIGHT_END;

      me.bill.fakePlayerPlugin.config.Config.debugChat("[Sleep] Tick for " + fp.getName() + ": time=" + time + ", isNight=" + isNight + ", sleeping=" + fp.isSleeping() + ", navigating=" + navigatingToBed.contains(uuid));

      if (isNight && !fp.isSleeping() && !navigatingToBed.contains(uuid)) {
        me.bill.fakePlayerPlugin.config.Config.debugChat("[Sleep] Starting sleep navigation for " + fp.getName());
        startSleepNavigation(fp);
      } else if (!isNight && fp.isSleeping()) {
        me.bill.fakePlayerPlugin.config.Config.debugChat("[Sleep] Waking bot " + fp.getName() + " (daytime)");
        wakeBot(fp);
      }
    }
  }

  // ── Navigation to bed ─────────────────────────────────────────────────────

  private void startSleepNavigation(@NotNull FakePlayer fp) {
    UUID uuid = fp.getUuid();
    Location origin = fp.getSleepOrigin();
    double radius   = fp.getSleepRadius();
    if (origin == null || radius <= 0) return;

    Player bot = fp.getPlayer();
    if (bot == null || !bot.isOnline()) return;

    cancelOtherTasks(uuid);

    // Use cached bed if still valid; otherwise scan for a new one
    Location bedLoc = cachedBeds.get(uuid);
    if (bedLoc == null || !isBedBlock(bedLoc)) {
      bedLoc = findBed(bot.getWorld(), origin, radius);
      if (bedLoc == null) return;   // no beds in range — skip silently
      cachedBeds.put(uuid, bedLoc);
    }

    navigatingToBed.add(uuid);

    final Location finalBedLoc = bedLoc.clone();

    pathfinding.navigate(fp,
        new PathfindingService.NavigationRequest(
            PathfindingService.Owner.SLEEP,
            () -> finalBedLoc,
            ARRIVE_DISTANCE,
            0.0,              // static destination — no dynamic recalc distance
            MAX_NULL_RECALCS,
            () -> {
              // Arrived near the bed — attempt to enter sleep
              navigatingToBed.remove(uuid);
              attemptSleep(fp, finalBedLoc);
            },
            () -> {
              // Navigation was cancelled externally (e.g. bot despawned)
              navigatingToBed.remove(uuid);
              cachedBeds.remove(uuid);   // bed might have been obstructed; re-scan later
            },
            () -> {
              // Path-finding gave up (no valid path found)
              navigatingToBed.remove(uuid);
              cachedBeds.remove(uuid);
            }));
  }

  private void cancelOtherTasks(@NotNull UUID uuid) {
    pathfinding.cancel(uuid);
    boolean captured = false;

    me.bill.fakePlayerPlugin.config.Config.debugChat("[Sleep] Checking activities for UUID " + uuid);

    if (mineCommand != null && mineCommand.isMining(uuid)) {
      me.bill.fakePlayerPlugin.config.Config.debugChat("[Sleep] Detected MINE activity");
      previousActivity.put(uuid, Activity.MINE);
      mineCommand.stopMining(uuid);
      captured = true;
    } else if (useCommand != null && useCommand.isUsing(uuid)) {
      me.bill.fakePlayerPlugin.config.Config.debugChat("[Sleep] Detected USE activity");
      previousActivity.put(uuid, Activity.USE);
      useCommand.stopUsing(uuid);
      captured = true;
    } else if (placeCommand != null && placeCommand.isPlacing(uuid)) {
      me.bill.fakePlayerPlugin.config.Config.debugChat("[Sleep] Detected PLACE activity");
      previousActivity.put(uuid, Activity.PLACE);
      placeCommand.stopPlacing(uuid);
      captured = true;
    } else if (attackCommand != null && attackCommand.isAttacking(uuid)) {
      me.bill.fakePlayerPlugin.config.Config.debugChat("[Sleep] Detected ATTACK activity");
      previousActivity.put(uuid, Activity.ATTACK);
      attackCommand.stopAttacking(uuid);
      captured = true;
    } else if (followCommand != null && followCommand.isFollowing(uuid)) {
      me.bill.fakePlayerPlugin.config.Config.debugChat("[Sleep] Detected FOLLOW activity");
      previousActivity.put(uuid, Activity.FOLLOW);
      followCommand.stopFollowing(uuid);
      captured = true;
    } else if (moveCommand != null) {
      if (moveCommand.isRoaming(uuid)) {
        me.bill.fakePlayerPlugin.config.Config.debugChat("[Sleep] Detected ROAM activity");
        Location center = moveCommand.getRoamCenter(uuid);
        Double radius = moveCommand.getRoamRadius(uuid);
        if (center != null && radius != null) {
          previousActivity.put(uuid, Activity.ROAM);
          previousRoamCenter.put(uuid, center.clone());
          previousRoamRadius.put(uuid, radius);
          moveCommand.cleanupBot(uuid);
          captured = true;
        }
      } else {
        String wpRoute = moveCommand.getActiveRouteForBot(uuid);
        if (wpRoute != null) {
          me.bill.fakePlayerPlugin.config.Config.debugChat("[Sleep] Detected WAYPOINT activity: " + wpRoute);
          previousActivity.put(uuid, Activity.WAYPOINT);
          previousWaypointRoute.put(uuid, wpRoute);
          captured = true;
        }
      }
    }

    if (!captured) {
      me.bill.fakePlayerPlugin.config.Config.debugChat("[Sleep] No active activity detected");
      previousActivity.put(uuid, Activity.NONE);
    }
  }

  /**
   * Checks if a bot is currently performing a given activity.
   */
  private boolean checkActivity(@NotNull UUID uuid, @NotNull Activity activity) {
    return switch (activity) {
      case MINE -> mineCommand != null && mineCommand.isMining(uuid);
      case USE -> useCommand != null && useCommand.isUsing(uuid);
      case PLACE -> placeCommand != null && placeCommand.isPlacing(uuid);
      case ATTACK -> attackCommand != null && attackCommand.isAttacking(uuid);
      case FOLLOW -> followCommand != null && followCommand.isFollowing(uuid);
      default -> false;
    };
  }

  // ── Sleep / wake helpers ──────────────────────────────────────────────────

  private void attemptSleep(@NotNull FakePlayer fp, @NotNull Location bedLoc) {
    if (!plugin.isEnabled()) return;
    UUID uuid = fp.getUuid();

    Player bot = fp.getPlayer();
    if (bot == null || !bot.isOnline()) return;
    if (fp.getSleepRadius() <= 0) return;

    long time = bot.getWorld().getTime();
    if (time < NIGHT_START || time > NIGHT_END) return;

    if (!isBedBlock(bedLoc)) {
      cachedBeds.remove(uuid);
      return;
    }

    // Capture previous activity before sleeping
    if (!previousActivity.containsKey(uuid)) {
      me.bill.fakePlayerPlugin.config.Config.debugChat("[Sleep] Capturing previous activity for " + fp.getName());
      cancelOtherTasks(uuid);
    }

    me.bill.fakePlayerPlugin.config.Config.debugChat("[Sleep] Activity stored before sleep for " + fp.getName() + ": " + previousActivity.get(uuid));

    // Call sleep directly - Minecraft handles positioning automatically
    boolean slept = bot.sleep(bedLoc, /* force */ true);
    if (slept) {
      fp.setSleeping(true);
      manager.lockForAction(uuid, bot.getLocation());
      cachedBeds.remove(uuid);
      me.bill.fakePlayerPlugin.config.Config.debugChat("[Sleep] Bot " + fp.getName() + " is now SLEEPING");
    } else {
      me.bill.fakePlayerPlugin.config.Config.debugChat("[Sleep] Bot " + fp.getName() + " FAILED to sleep");
    }
  }

  private void wakeBot(@NotNull FakePlayer fp) {
    if (!fp.isSleeping()) {
      me.bill.fakePlayerPlugin.config.Config.debugChat("[Sleep] wakeBot called but bot " + fp.getName() + " is not marked as sleeping");
      return;
    }
    UUID uuid = fp.getUuid();
    Activity prevActivity = previousActivity.remove(uuid);

    me.bill.fakePlayerPlugin.config.Config.debugChat("[Sleep] wakeBot: prevActivity=" + prevActivity + ", map=" + previousActivity);

    fp.setSleeping(false);

    Player bot = fp.getPlayer();
    if (bot != null && bot.isOnline()) {
      if (bot.isSleeping()) {
        me.bill.fakePlayerPlugin.config.Config.debugChat("[Sleep] Bot " + fp.getName() + " is sleeping, calling wakeup()");
        bot.wakeup(false);
      } else {
        me.bill.fakePlayerPlugin.config.Config.debugChat("[Sleep] Bot " + fp.getName() + " was not detected as sleeping by Minecraft");
      }
    } else {
      me.bill.fakePlayerPlugin.config.Config.debugChat("[Sleep] Bot " + fp.getName() + " is null or offline");
    }
    manager.unlockAction(uuid);

    me.bill.fakePlayerPlugin.config.Config.debugChat("[Sleep] Waking bot " + fp.getName() + ", previous activity: " + prevActivity);

    if (prevActivity == null || prevActivity == Activity.NONE) {
      me.bill.fakePlayerPlugin.config.Config.debugChat("[Sleep] No activity to resume for " + fp.getName());
      return;
    }

    // Verify bot is awake after wakeup call
    bot = fp.getPlayer();
    me.bill.fakePlayerPlugin.config.Config.debugChat("[Sleep] After wakeup: bot=" + (bot != null) + ", isOnline=" + (bot != null && bot.isOnline()) + ", isSleeping=" + (bot != null && bot.isSleeping()));

    // Delay before resuming activity (2 seconds = 40 ticks)
    plugin.getServer().getScheduler().runTaskLater(
      plugin,
      () -> {
        me.bill.fakePlayerPlugin.config.Config.debugChat("[Sleep] Resuming activity: " + prevActivity + " for " + fp.getName());

        switch (prevActivity) {
          case MINE -> {
            if (mineCommand != null) {
              mineCommand.resumeMining(fp);
            }
            break;
          }
          case USE -> {
            if (useCommand != null) {
              useCommand.resumeUsing(fp);
            }
            break;
          }
          case PLACE -> {
            if (placeCommand != null) {
              placeCommand.resumePlacing(fp);
            }
            break;
          }
          case ATTACK -> {
            if (attackCommand != null) {
              attackCommand.resumeAttacking(fp);
            }
            break;
          }
          case FOLLOW -> {
            if (followCommand != null) {
              followCommand.resumeFollowing(fp);
            }
            break;
          }
          case ROAM -> {
            Location center = previousRoamCenter.remove(uuid);
            Double radius = previousRoamRadius.remove(uuid);
            if (center != null && radius != null && moveCommand != null) {
              moveCommand.resumeRoaming(fp, center, radius);
            }
            break;
          }
          case WAYPOINT -> {
            String routeName = previousWaypointRoute.remove(uuid);
            if (routeName != null && moveCommand != null) {
              moveCommand.resumePatrol(fp, routeName);
            }
            break;
          }
          default -> {
            break;
          }
        }

        previousWaypointRoute.remove(uuid);
        previousRoamCenter.remove(uuid);
        previousRoamRadius.remove(uuid);
      },
      40L
    );
  }

  // ── Bed-search logic ──────────────────────────────────────────────────────

  /**
   * Scans a cylinder around {@code origin} for the nearest bed block.
   * The Y range checked is origin.y ± 2 blocks.
   *
   * @return centre of the closest bed block (foot position), or null if none found.
   */
  @Nullable
  private static Location findBed(@NotNull World world, @NotNull Location origin, double radius) {
    int r  = (int) Math.ceil(radius);
    int ox = origin.getBlockX();
    int oy = origin.getBlockY();
    int oz = origin.getBlockZ();

    Location closest = null;
    double closestDistSq = Double.MAX_VALUE;

    for (int dx = -r; dx <= r; dx++) {
      for (int dz = -r; dz <= r; dz++) {
        double distSq = (double) dx * dx + (double) dz * dz;
        if (distSq > radius * radius) continue;

        for (int dy = -2; dy <= 2; dy++) {
          Block block = world.getBlockAt(ox + dx, oy + dy, oz + dz);
          if (Tag.BEDS.isTagged(block.getType())) {
            if (distSq < closestDistSq) {
              closestDistSq = distSq;
              closest = new Location(world, ox + dx, oy + dy, oz + dz);
            }
            break;
          }
        }
      }
    }
    return closest;
  }

  /**
   * Returns the sleep position (head location) for a bed at footLoc.
   * In Minecraft, beds have foot + head parts. The bot should be positioned
   * at the head side to sleep correctly.
   */
  @Nullable
  private static Location getSleepPosition(@NotNull Location footLoc) {
    World world = footLoc.getWorld();
    if (world == null) return null;

    int bx = footLoc.getBlockX();
    int by = footLoc.getBlockY();
    int bz = footLoc.getBlockZ();

    int headX = bx, headZ = bz;
    float yaw = 0f;

    Block footBlock = world.getBlockAt(bx, by, bz);
    org.bukkit.block.BlockState state = footBlock.getState();
    if (state instanceof org.bukkit.block.Bed bed) {
      org.bukkit.block.data.BlockData data = bed.getBlockData();
      if (data instanceof org.bukkit.block.data.type.Bed bedData) {
        org.bukkit.util.Vector dir = bedData.getFacing().getDirection();
        headX += dir.getBlockX();
        headZ += dir.getBlockZ();

        switch (bedData.getFacing()) {
          case NORTH -> yaw = 180f;
          case SOUTH -> yaw = 0f;
          case EAST -> yaw = -90f;
          case WEST -> yaw = 90f;
        }
      }
    }

    return new Location(world, headX + 0.5, by, headZ + 0.5, yaw, 0f);
  }

  /** Returns true if the given location contains a bed block. */
  private static boolean isBedBlock(@NotNull Location loc) {
    World world = loc.getWorld();
    if (world == null) return false;
    return Tag.BEDS.isTagged(world.getBlockAt(loc).getType());
  }

  // ── Public API (used by FakePlayerManager.delete() and onDisable) ─────────

  /**
   * Must be called by {@code FakePlayerManager.delete()} when a bot is being removed.
   * Cleans up all sleep state and scheduled tasks for the removed bot.
   */
  public void cleanupBot(@NotNull UUID botUuid) {
    FakePlayer fp = manager.getByUuid(botUuid);

    // Cancel navigation first (before clearing state so callbacks see updated state)
    if (navigatingToBed.remove(botUuid)) {
      pathfinding.cancel(botUuid);
    }

    if (fp != null) {
      wakeBot(fp);
      fp.setSleepOrigin(null);
      fp.setSleepRadius(0.0);
    }

    cachedBeds.remove(botUuid);
    previousActivity.remove(botUuid);
    previousWaypointRoute.remove(botUuid);
    previousRoamCenter.remove(botUuid);
    previousRoamRadius.remove(botUuid);
    checkNightWatchNeeded();
  }

  /**
   * Stops all sleep sessions; called on plugin disable.
   */
  public void stopAll() {
    for (FakePlayer fp : new ArrayList<>(manager.getActivePlayers())) {
      disableSleep(fp);
    }
    if (nightWatchTask != null && !nightWatchTask.isCancelled()) {
      nightWatchTask.cancel();
      nightWatchTask = null;
    }
  }

  public boolean isSleeping(@NotNull UUID botUuid) {
    FakePlayer fp = manager.getByUuid(botUuid);
    return fp != null && fp.isSleeping();
  }

  public boolean hasSleepConfig(@NotNull UUID botUuid) {
    FakePlayer fp = manager.getByUuid(botUuid);
    return fp != null && fp.getSleepRadius() > 0;
  }

  // ── Tab-completion ────────────────────────────────────────────────────────

  @Override
  public List<String> tabComplete(CommandSender sender, String[] args) {
    List<String> out = new ArrayList<>();
    if (args.length == 1) {
      String in = args[0].toLowerCase();
      if ("all".startsWith(in)) out.add("all");
      for (FakePlayer fp : manager.getActivePlayers())
        if (fp.getName().toLowerCase().startsWith(in)) out.add(fp.getName());
    } else if (args.length == 2) {
      String in = args[1].toLowerCase();
      if ("--stop".startsWith(in)) out.add("--stop");
      if (sender instanceof Player p) {
        Location loc = p.getLocation();
        out.add(loc.getBlockX() + " " + loc.getBlockY() + " " + loc.getBlockZ());
      }
    } else if (args.length == 3) {
      out.add("10");
      out.add("20");
      out.add("30");
      out.add("50");
      out.add("100");
    }
    return out;
  }
}

