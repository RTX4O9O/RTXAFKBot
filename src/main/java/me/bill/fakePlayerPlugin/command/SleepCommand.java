package me.bill.fakePlayerPlugin.command;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import me.bill.fakePlayerPlugin.FakePlayerPlugin;
import me.bill.fakePlayerPlugin.config.Config;
import me.bill.fakePlayerPlugin.fakeplayer.FakePlayer;
import me.bill.fakePlayerPlugin.fakeplayer.FakePlayerManager;
import me.bill.fakePlayerPlugin.fakeplayer.PathfindingService;
import me.bill.fakePlayerPlugin.lang.Lang;
import me.bill.fakePlayerPlugin.permission.Perm;
import me.bill.fakePlayerPlugin.util.FppScheduler;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerPlayer;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.Tag;
import org.bukkit.World;
import org.bukkit.block.Block;
import org.bukkit.block.BlockFace;
import org.bukkit.block.data.type.Bed;
import org.bukkit.command.CommandSender;
import org.bukkit.craftbukkit.entity.CraftPlayer;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/**
 * /fpp sleep &lt;bot|all&gt; &lt;x&gt; &lt;y&gt; &lt;z&gt; &lt;radius&gt;
 *   — Set the sleep origin and radius for a bot. The bot will automatically walk to
 *     the nearest bed within the radius (measured from the given origin) when night
 *     falls and wake up when dawn arrives. Active tasks (mine/use/place/attack/follow/
 *     move/find) are paused before sleeping and automatically resumed after waking.
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
  /** Ticks to wait after waking before resuming the previous task. */
  private static final long WAKE_RESUME_DELAY_TICKS = 40L;

  private final FakePlayerPlugin plugin;
  private final FakePlayerManager manager;
  private final PathfindingService pathfinding;

  @Nullable private MineCommand    mineCommand;
  @Nullable private UseCommand     useCommand;
  @Nullable private PlaceCommand   placeCommand;
  @Nullable private AttackCommand  attackCommand;
  @Nullable private FollowCommand  followCommand;
  @Nullable private MoveCommand    moveCommand;
  @Nullable private FindCommand    findCommand;

  /** Activity types that can be paused and resumed around sleep. */
  private enum Activity {
    NONE, MINE, USE, PLACE, ATTACK, FOLLOW, ROAM, WAYPOINT, FIND
  }

  // Per-bot state for activity capture/restore
  private final Map<UUID, Activity>  previousActivity     = new ConcurrentHashMap<>();
  private final Map<UUID, String>    previousWaypointRoute = new ConcurrentHashMap<>();
  private final Map<UUID, Location>  previousRoamCenter   = new ConcurrentHashMap<>();
  private final Map<UUID, Double>    previousRoamRadius   = new ConcurrentHashMap<>();

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

  private final Map<UUID, Location> temporaryBeds = new ConcurrentHashMap<>();

  /** The single global repeating task that checks all configured bots. null = not running. */
  @Nullable private Integer nightWatchTaskId = null;

  // ── Constructor ──────────────────────────────────────────────────────────

  public SleepCommand(
      @NotNull FakePlayerPlugin plugin,
      @NotNull FakePlayerManager manager,
      @NotNull PathfindingService pathfinding) {
    this.plugin      = plugin;
    this.manager     = manager;
    this.pathfinding = pathfinding;
  }

  // ── Dependency injection ─────────────────────────────────────────────────

  public void setMineCommand(@Nullable MineCommand cmd)       { this.mineCommand    = cmd; }
  public void setUseCommand(@Nullable UseCommand cmd)         { this.useCommand     = cmd; }
  public void setPlaceCommand(@Nullable PlaceCommand cmd)     { this.placeCommand   = cmd; }
  public void setAttackCommand(@Nullable AttackCommand cmd)   { this.attackCommand  = cmd; }
  public void setFollowCommand(@Nullable FollowCommand cmd)   { this.followCommand  = cmd; }
  public void setMoveCommand(@Nullable MoveCommand cmd)       { this.moveCommand    = cmd; }
  public void setFindCommand(@Nullable FindCommand cmd)       { this.findCommand    = cmd; }

  // ── FppCommand metadata ──────────────────────────────────────────────────

  @Override public String getName()        { return "sleep"; }
  @Override public String getPermission()  { return Perm.SLEEP; }
  @Override public boolean canUse(CommandSender sender) { return Perm.has(sender, Perm.SLEEP); }

  @Override
  public String getUsage() {
    return "<bot|all> <x y z> <radius>  |  <bot|all> --stop";
  }

  @Override
  public String getDescription() {
    return "Set a sleep-origin for a bot so it auto-sleeps at night.";
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
    if (args.length < 5) {
      sender.sendMessage(Lang.get("sleep-usage"));
      return true;
    }

    double x, y, z, radius;
    try {
      x      = Double.parseDouble(args[1]);
      y      = Double.parseDouble(args[2]);
      z      = Double.parseDouble(args[3]);
      radius = Double.parseDouble(args[4]);
    } catch (NumberFormatException e) {
      sender.sendMessage(Lang.get("sleep-invalid-args"));
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
          "count",   String.valueOf(started),
          "radius",  String.valueOf((int) radius),
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
    cachedBeds.remove(fp.getUuid());
    ensureNightWatchRunning();
  }

  private void disableSleep(@NotNull FakePlayer fp) {
    UUID uuid = fp.getUuid();
    fp.setSleepOrigin(null);
    fp.setSleepRadius(0.0);
    cachedBeds.remove(uuid);

    if (navigatingToBed.remove(uuid)) {
      pathfinding.cancel(uuid);
    }

    if (fp.isSleeping()) {
      wakeBot(fp, /* resumeTask= */ false);
    }
    breakTemporaryBed(uuid);

    checkNightWatchNeeded();
  }

  // ── Night-watch task management ───────────────────────────────────────────

  private void ensureNightWatchRunning() {
    if (!plugin.isEnabled()) return;
    if (nightWatchTaskId != null) return;
    nightWatchTaskId =
        FppScheduler.runSyncRepeatingWithId(plugin, this::nightWatchTick, 40L, CHECK_INTERVAL_TICKS);
  }

  private void checkNightWatchNeeded() {
    if (nightWatchTaskId == null) return;
    boolean anyConfigured = manager.getActivePlayers().stream()
        .anyMatch(fp -> fp.getSleepRadius() > 0);
    if (!anyConfigured) {
      FppScheduler.cancelTask(nightWatchTaskId);
      nightWatchTaskId = null;
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

      if (isNight && !fp.isSleeping() && !navigatingToBed.contains(uuid)) {
        startSleepNavigation(fp);
      } else if (!isNight && fp.isSleeping()) {
        wakeBot(fp, /* resumeTask= */ true);
      } else if (isNight && fp.isSleeping()) {
        // FakePlayerManager clears fp.sleeping the same tick NMS wakes the bot,
        // so this branch is a safety-net for edge cases where that sync is missed
        // (e.g. the bot was woken by a plugin event outside our control).
        try {
          ServerPlayer nmsPlayer = ((CraftPlayer) bot).getHandle();
          if (!nmsPlayer.isSleeping()) {
            Config.debugChat("[Sleep] NMS woke " + fp.getName()
                + " mid-night (monsters/plugin) — clearing sleep flag and re-queueing");
            fp.setSleeping(false);
            manager.unlockAction(uuid);
            // previousActivity is still captured; next nightWatchTick will re-navigate.
          }
        } catch (Exception ignored) {}
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

    // Pause any currently-running task and record it for later resumption.
    captureAndPauseTask(uuid);

    // Use cached bed if still valid; otherwise scan for a new one.
    Location bedLoc = cachedBeds.get(uuid);
      if (bedLoc == null || !isBedBlock(bedLoc) || isBedOccupied(bedLoc, uuid)) {
        bedLoc = findBed(bot.getWorld(), origin, radius);
      if (bedLoc == null) {
        bedLoc = tryPlaceTemporaryBed(fp, bot);
        if (bedLoc == null) {
          // No bed in range — restore the paused task immediately.
          resumePreviousTask(fp);
          return;
        }
      }
      cachedBeds.put(uuid, bedLoc);
    }

    navigatingToBed.add(uuid);

    final Location finalBedLoc = bedLoc.clone();

    pathfinding.navigate(fp,
        new PathfindingService.NavigationRequest(
            PathfindingService.Owner.SLEEP,
            () -> finalBedLoc,
            ARRIVE_DISTANCE,
            0.0,
            MAX_NULL_RECALCS,
            () -> {
              navigatingToBed.remove(uuid);
              attemptNmsSleep(fp, finalBedLoc);
            },
            () -> {
              // Navigation was cancelled externally (bot despawned etc.)
              navigatingToBed.remove(uuid);
              cachedBeds.remove(uuid);
            },
            () -> {
              // No path found — restore the paused task.
              navigatingToBed.remove(uuid);
              cachedBeds.remove(uuid);
              resumePreviousTask(fp);
            }));
  }

  // ── Task capture / restore ────────────────────────────────────────────────

  /**
   * Stops whatever task a bot is currently running and records it so
   * {@link #resumePreviousTask} can restart it after waking.
   */
  private void captureAndPauseTask(@NotNull UUID uuid) {
    if (previousActivity.containsKey(uuid)) return; // already captured

    Config.debugChat("[Sleep] captureAndPauseTask for " + uuid);

    if (mineCommand != null && mineCommand.isMining(uuid)) {
      previousActivity.put(uuid, Activity.MINE);
      mineCommand.stopMining(uuid);
    } else if (useCommand != null && useCommand.isUsing(uuid)) {
      previousActivity.put(uuid, Activity.USE);
      useCommand.stopUsing(uuid);
    } else if (placeCommand != null && placeCommand.isPlacing(uuid)) {
      previousActivity.put(uuid, Activity.PLACE);
      placeCommand.stopPlacing(uuid);
    } else if (attackCommand != null && attackCommand.isAttacking(uuid)) {
      previousActivity.put(uuid, Activity.ATTACK);
      attackCommand.stopAttacking(uuid);
    } else if (followCommand != null && followCommand.isFollowing(uuid)) {
      previousActivity.put(uuid, Activity.FOLLOW);
      followCommand.stopFollowing(uuid);
    } else if (findCommand != null && findCommand.isFinding(uuid)) {
      previousActivity.put(uuid, Activity.FIND);
      findCommand.cleanupBot(uuid);
    } else if (moveCommand != null && moveCommand.isRoaming(uuid)) {
      Location center = moveCommand.getRoamCenter(uuid);
      Double   radius = moveCommand.getRoamRadius(uuid);
      if (center != null && radius != null) {
        previousActivity.put(uuid, Activity.ROAM);
        previousRoamCenter.put(uuid, center.clone());
        previousRoamRadius.put(uuid, radius);
        moveCommand.cleanupBot(uuid);
      } else {
        previousActivity.put(uuid, Activity.NONE);
      }
    } else if (moveCommand != null) {
      String route = moveCommand.getActiveRouteForBot(uuid);
      if (route != null) {
        previousActivity.put(uuid, Activity.WAYPOINT);
        previousWaypointRoute.put(uuid, route);
        // Stop the current patrol nav so the bot can navigate to bed.
        pathfinding.cancel(uuid);
      } else {
        previousActivity.put(uuid, Activity.NONE);
      }
    } else {
      previousActivity.put(uuid, Activity.NONE);
    }

    Config.debugChat("[Sleep] captured activity: " + previousActivity.get(uuid));
  }

  private void resumePreviousTask(@NotNull FakePlayer fp) {
    UUID uuid = fp.getUuid();
    Activity act = previousActivity.remove(uuid);
    if (act == null || act == Activity.NONE) return;

    Config.debugChat("[Sleep] resuming " + act + " for " + fp.getName());

    FppScheduler.runSyncLater(plugin, () -> {
      switch (act) {
        case MINE    -> { if (mineCommand   != null) mineCommand.resumeMining(fp); }
        case USE     -> { if (useCommand    != null) useCommand.resumeUsing(fp); }
        case PLACE   -> { if (placeCommand  != null) placeCommand.resumePlacing(fp); }
        case ATTACK  -> { if (attackCommand != null) attackCommand.resumeAttacking(fp); }
        case FOLLOW  -> { if (followCommand != null) followCommand.resumeFollowing(fp); }
        case ROAM    -> {
          Location center = previousRoamCenter.remove(uuid);
          Double   radius = previousRoamRadius.remove(uuid);
          if (center != null && radius != null && moveCommand != null) {
            moveCommand.resumeRoaming(fp, center, radius);
          }
        }
        case WAYPOINT -> {
          String route = previousWaypointRoute.remove(uuid);
          if (route != null && moveCommand != null) {
            moveCommand.resumePatrol(fp, route);
          }
        }
        default -> {}
      }
      // Clean up any leftover map entries
      previousRoamCenter.remove(uuid);
      previousRoamRadius.remove(uuid);
      previousWaypointRoute.remove(uuid);
    }, WAKE_RESUME_DELAY_TICKS);
  }

  // ── NMS sleep / wake ──────────────────────────────────────────────────────

  /**
   * Attempts to put the bot to sleep via NMS {@code ServerPlayer#startSleepInBed}.
   * Falls back to {@code Player#sleep} (Bukkit API) if NMS fails.
   */
  private void attemptNmsSleep(@NotNull FakePlayer fp, @NotNull Location bedLoc) {
    if (!plugin.isEnabled()) return;
    UUID uuid = fp.getUuid();

    Player bot = fp.getPlayer();
    if (bot == null || !bot.isOnline()) { resumePreviousTask(fp); return; }
    if (fp.getSleepRadius() <= 0)        { resumePreviousTask(fp); return; }

    long time = bot.getWorld().getTime();
    if (time < NIGHT_START || time > NIGHT_END) { resumePreviousTask(fp); return; }

    bedLoc = normalizeBedFoot(bedLoc);
    if (!isBedBlock(bedLoc) || isBedOccupied(bedLoc, uuid)) {
      cachedBeds.remove(uuid);
      resumePreviousTask(fp);
      return;
    }

    // Zero any residual navigation momentum so the bot lies flat on the bed
    // rather than appearing to slide or crouch when startSleepInBed fires.
    bot.setVelocity(new org.bukkit.util.Vector(0, 0, 0));

    // Try NMS first (bypasses vanilla distance/monster checks).
    boolean slept = false;
    try {
      ServerPlayer nms = ((CraftPlayer) bot).getHandle();
      BlockPos pos = new BlockPos(bedLoc.getBlockX(), bedLoc.getBlockY(), bedLoc.getBlockZ());
      var result = nms.startSleepInBed(pos, /* force= */ true);
      slept = result.right().isPresent(); // Right = success (Unit)
    } catch (Exception e) {
      Config.debugChat("[Sleep] NMS startSleepInBed failed for " + fp.getName() + ": " + e.getMessage());
    }

    // Bukkit fallback
    if (!slept) {
      try {
        slept = bot.sleep(bedLoc, /* force= */ true);
      } catch (Exception e) {
        Config.debugChat("[Sleep] Bukkit sleep fallback failed for " + fp.getName() + ": " + e.getMessage());
      }
    }

    if (slept) {
      fp.setSleeping(true);
      // Do NOT call lockForAction here. The action lock records the navigation
      // arrival location (≈1.5 blocks from the bed) and then teleports the bot
      // back to that standing spot every tick, overriding the NMS sleep pose.
      // Instead, FakePlayerManager suppresses physics/head-AI for sleeping bots
      // and zeroes their velocity directly.
      cachedBeds.remove(uuid);
      Config.debugChat("[Sleep] Bot " + fp.getName() + " is now SLEEPING");
    } else {
      Config.debugChat("[Sleep] Bot " + fp.getName() + " could not sleep – resuming task");
      resumePreviousTask(fp);
    }
  }

  /**
   * Wakes a sleeping bot via NMS {@code ServerPlayer#stopSleepInBed}.
   *
   * @param resumeTask if true, schedules the previous task to restart after waking.
   */
  private void wakeBot(@NotNull FakePlayer fp, boolean resumeTask) {
    if (!fp.isSleeping()) return;
    UUID uuid = fp.getUuid();

    fp.setSleeping(false);
    manager.unlockAction(uuid);

    Player bot = fp.getPlayer();
    if (bot != null && bot.isOnline()) {
      try {
        ServerPlayer nms = ((CraftPlayer) bot).getHandle();
        if (nms.isSleeping()) {
          nms.stopSleepInBed(/* wakeImmediately= */ true, /* updateLevelForSleepingPlayers= */ false);
        }
      } catch (Exception e) {
        Config.debugChat("[Sleep] NMS stopSleepInBed failed for " + fp.getName() + ": " + e.getMessage());
        // Bukkit fallback
        if (bot.isSleeping()) {
          try { bot.wakeup(false); } catch (Exception ignored) {}
        }
      }
    }

    breakTemporaryBed(uuid);

    Config.debugChat("[Sleep] Bot " + fp.getName() + " woke up, resumeTask=" + resumeTask);

    if (resumeTask) {
      resumePreviousTask(fp);
    } else {
      // Discard captured task state
      previousActivity.remove(uuid);
      previousRoamCenter.remove(uuid);
      previousRoamRadius.remove(uuid);
      previousWaypointRoute.remove(uuid);
    }
  }

  // ── Bed-search logic ──────────────────────────────────────────────────────

  /**
   * Scans a cylinder around {@code origin} for the nearest bed block.
   * The Y range checked is origin.y ± 2 blocks.
   *
   * @return the centre of the closest bed block (foot position), or null if none found.
   */
  @Nullable
  private static Location findBed(@NotNull World world, @NotNull Location origin, double radius) {
    int r  = (int) Math.ceil(radius);
    int ox = origin.getBlockX();
    int oy = origin.getBlockY();
    int oz = origin.getBlockZ();

    Location closest    = null;
    double closestDistSq = Double.MAX_VALUE;

    for (int dx = -r; dx <= r; dx++) {
      for (int dz = -r; dz <= r; dz++) {
        double distSq = (double) dx * dx + (double) dz * dz;
        if (distSq > radius * radius) continue;

        for (int dy = -2; dy <= 2; dy++) {
          Block block = world.getBlockAt(ox + dx, oy + dy, oz + dz);
          if (Tag.BEDS.isTagged(block.getType())) {
            Location foot = normalizeBedFoot(block.getLocation());
            if (isBedOccupiedStatic(foot, null)) break;
            if (distSq < closestDistSq) {
              closestDistSq = distSq;
              closest = foot;
            }
            break;
          }
        }
      }
    }
    return closest;
  }

  /** Returns true if the given location contains a bed block. */
  private static boolean isBedBlock(@NotNull Location loc) {
    World world = loc.getWorld();
    if (world == null) return false;
    return Tag.BEDS.isTagged(world.getBlockAt(loc).getType());
  }

  private static Location normalizeBedFoot(@NotNull Location loc) {
    World world = loc.getWorld();
    if (world == null) return loc;
    Block block = world.getBlockAt(loc);
    if (!Tag.BEDS.isTagged(block.getType())) return loc;
    Bed bed = (Bed) block.getBlockData();
    if (bed.getPart() == Bed.Part.FOOT) return block.getLocation();
    return block.getRelative(bed.getFacing().getOppositeFace()).getLocation();
  }

  private boolean isBedOccupied(@NotNull Location bedLoc, UUID allowedUuid) {
    return isBedOccupiedStatic(bedLoc, allowedUuid);
  }

  private static boolean isBedOccupiedStatic(@NotNull Location bedLoc, @Nullable UUID allowedUuid) {
    Location foot = normalizeBedFoot(bedLoc);
    World world = foot.getWorld();
    if (world == null) return false;
    Block footBlock = foot.getBlock();
    if (!Tag.BEDS.isTagged(footBlock.getType())) return false;
    Bed bed = (Bed) footBlock.getBlockData();
    Block head = footBlock.getRelative(bed.getFacing());
    for (Player player : world.getPlayers()) {
      if (allowedUuid != null && allowedUuid.equals(player.getUniqueId())) continue;
      if (!player.isSleeping()) continue;
      Location loc = player.getLocation();
      if (loc.getBlock().equals(footBlock) || loc.getBlock().equals(head)) return true;
      if (loc.distanceSquared(foot.clone().add(0.5, 0.0, 0.5)) < 4.0) return true;
    }
    return false;
  }

  @Nullable
  private Location tryPlaceTemporaryBed(@NotNull FakePlayer fp, @NotNull Player bot) {
    if (!fp.isAutoPlaceBedEnabled()) return null;
    int bedSlot = findBedItemSlot(bot);
    if (bedSlot < 0) return null;
    Block foot = findTemporaryBedFoot(bot);
    if (foot == null) return null;
    BlockFace facing = BlockFace.NORTH;
    Block head = foot.getRelative(facing);
    if (!head.getType().isAir()) return null;

    Material bedType = bot.getInventory().getItem(bedSlot).getType();
    foot.setType(bedType, false);
    head.setType(bedType, false);
    Bed footData = (Bed) foot.getBlockData();
    footData.setPart(Bed.Part.FOOT);
    footData.setFacing(facing);
    foot.setBlockData(footData, false);
    Bed headData = (Bed) head.getBlockData();
    headData.setPart(Bed.Part.HEAD);
    headData.setFacing(facing);
    head.setBlockData(headData, false);

    ItemStack item = bot.getInventory().getItem(bedSlot);
    item.setAmount(item.getAmount() - 1);
    if (item.getAmount() <= 0) bot.getInventory().setItem(bedSlot, null);
    else bot.getInventory().setItem(bedSlot, item);
    temporaryBeds.put(fp.getUuid(), foot.getLocation());
    return foot.getLocation();
  }

  private int findBedItemSlot(Player bot) {
    for (int i = 0; i < bot.getInventory().getSize(); i++) {
      ItemStack item = bot.getInventory().getItem(i);
      if (item != null && Tag.BEDS.isTagged(item.getType())) return i;
    }
    return -1;
  }

  @Nullable
  private Block findTemporaryBedFoot(Player bot) {
    Location base = bot.getLocation();
    World world = bot.getWorld();
    for (int dx = -2; dx <= 2; dx++) {
      for (int dz = -2; dz <= 2; dz++) {
        Block foot = world.getBlockAt(base.getBlockX() + dx, base.getBlockY(), base.getBlockZ() + dz);
        Block head = foot.getRelative(BlockFace.NORTH);
        if (foot.getType().isAir()
            && head.getType().isAir()
            && foot.getRelative(BlockFace.DOWN).getType().isSolid()
            && head.getRelative(BlockFace.DOWN).getType().isSolid()) return foot;
      }
    }
    return null;
  }

  private void breakTemporaryBed(UUID uuid) {
    Location loc = temporaryBeds.remove(uuid);
    if (loc == null || loc.getWorld() == null) return;
    Block foot = loc.getBlock();
    if (Tag.BEDS.isTagged(foot.getType())) {
      Bed bed = (Bed) foot.getBlockData();
      Block other = foot.getRelative(bed.getPart() == Bed.Part.FOOT ? bed.getFacing() : bed.getFacing().getOppositeFace());
      foot.setType(Material.AIR, false);
      if (Tag.BEDS.isTagged(other.getType())) other.setType(Material.AIR, false);
    }
  }

  // ── Public API ────────────────────────────────────────────────────────────

  /**
   * Must be called by {@code FakePlayerManager.delete()} when a bot is being removed.
   * Cleans up all sleep state for the removed bot without attempting to resume tasks.
   */
  public void cleanupBot(@NotNull UUID botUuid) {
    if (navigatingToBed.remove(botUuid)) {
      pathfinding.cancel(botUuid);
    }

    FakePlayer fp = manager.getByUuid(botUuid);
    if (fp != null) {
      wakeBot(fp, /* resumeTask= */ false);
      fp.setSleepOrigin(null);
      fp.setSleepRadius(0.0);
    }

    cachedBeds.remove(botUuid);
    temporaryBeds.remove(botUuid);
    previousActivity.remove(botUuid);
    previousWaypointRoute.remove(botUuid);
    previousRoamCenter.remove(botUuid);
    previousRoamRadius.remove(botUuid);
    checkNightWatchNeeded();
  }

  /** Stops all sleep sessions; called on plugin disable. */
  public void stopAll() {
    for (FakePlayer fp : new ArrayList<>(manager.getActivePlayers())) {
      disableSleep(fp);
    }
    if (nightWatchTaskId != null) {
      FppScheduler.cancelTask(nightWatchTaskId);
      nightWatchTaskId = null;
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
      if (org.bukkit.command.CommandSender.class.isInstance(sender)
          && sender instanceof Player p) {
        Location loc = p.getLocation();
        String coords = loc.getBlockX() + " " + loc.getBlockY() + " " + loc.getBlockZ();
        if (coords.startsWith(in)) out.add(coords);
      }
    } else if (args.length == 5) {
      for (String preset : List.of("10", "20", "30", "50", "100")) out.add(preset);
    }
    return out;
  }
}
