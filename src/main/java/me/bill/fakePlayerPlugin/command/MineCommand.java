package me.bill.fakePlayerPlugin.command;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import me.bill.fakePlayerPlugin.api.FppBotBlockBreakEvent;
import me.bill.fakePlayerPlugin.api.impl.FppApiImpl;
import me.bill.fakePlayerPlugin.api.impl.FppBotImpl;
import me.bill.fakePlayerPlugin.FakePlayerPlugin;
import me.bill.fakePlayerPlugin.config.Config;
import me.bill.fakePlayerPlugin.fakeplayer.BotNavUtil;
import me.bill.fakePlayerPlugin.fakeplayer.FakePlayer;
import me.bill.fakePlayerPlugin.fakeplayer.FakePlayerManager;
import me.bill.fakePlayerPlugin.fakeplayer.NmsPlayerSpawner;
import me.bill.fakePlayerPlugin.fakeplayer.PathfindingService;
import me.bill.fakePlayerPlugin.fakeplayer.StorageInteractionHelper;
import me.bill.fakePlayerPlugin.lang.Lang;
import me.bill.fakePlayerPlugin.permission.Perm;
import me.bill.fakePlayerPlugin.util.FppScheduler;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.network.protocol.game.ServerboundPlayerActionPacket;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.Vec3;
import org.bukkit.Bukkit;
import org.bukkit.GameMode;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.Tag;
import org.bukkit.World;
import org.bukkit.block.Block;
import org.bukkit.command.CommandSender;
import org.bukkit.craftbukkit.entity.CraftPlayer;
import org.bukkit.entity.Item;
import org.bukkit.entity.Player;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.InventoryHolder;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.PlayerInventory;
import org.bukkit.util.BlockIterator;

public final class MineCommand implements FppCommand {

  private static final boolean AREA_MODE_ENABLED = false;

  private static final int LOOK_BLOCK_RANGE = 8;
  private static final int AREA_CONTROLLER_PERIOD = 5;
  private static final int AREA_PICKUP_WAIT_TICKS = 8;
  private static final int AREA_PICKUP_EXTRA_TICKS = 20;

  private static final long DROP_LOITER_MS = 8_000L;
  private final FakePlayerPlugin plugin;
  private final FakePlayerManager manager;
  private final PathfindingService pathfinding;
  private final StorageStore storageStore;
  private final MineSelectionStore selectionStore;
  private FindCommand findCommand;

  private final Map<UUID, Integer> miningTasks = new ConcurrentHashMap<>();
  private final Map<UUID, MiningState> miningStates = new ConcurrentHashMap<>();
  private final Map<UUID, Location> activeMineLocations = new ConcurrentHashMap<>();
  private final Map<UUID, Boolean> activeMineOnceFlags = new ConcurrentHashMap<>();

  private final Map<UUID, AreaSelection> selections = new ConcurrentHashMap<>();
  private final Map<UUID, AreaMineJob> areaJobs = new ConcurrentHashMap<>();
  private final Map<UUID, Integer> areaTasks = new ConcurrentHashMap<>();

  public MineCommand(
      FakePlayerPlugin plugin,
      FakePlayerManager manager,
      StorageStore storageStore,
      MineSelectionStore selectionStore,
      PathfindingService pathfinding) {
    this.plugin = plugin;
    this.manager = manager;
    this.pathfinding = pathfinding;
    this.storageStore = storageStore;
    this.selectionStore = selectionStore;
  }

  public void setFindCommand(FindCommand findCommand) {
    this.findCommand = findCommand;
  }

  @Override
  public String getName() {
    return "mine";
  }

  @Override
  public String getUsage() {
    return "<bot> [--once|--stop|--pos1|--pos2|--start|--wesel]  |  --stop";
  }

  @Override
  public String getDescription() {
    return "Walk a bot to mine one block, mine continuously, or clear a selected area with"
        + " storage offloading.";
  }

  @Override
  public String getPermission() {
    return Perm.MINE;
  }

  @Override
  public boolean canUse(CommandSender sender) {
    return Perm.has(sender, Perm.MINE);
  }

  @Override
  public boolean execute(CommandSender sender, String[] args) {
    if (args.length == 0) {
      sender.sendMessage(Lang.get("mine-usage"));
      return true;
    }

    if ((args[0].equalsIgnoreCase("stop") || args[0].equalsIgnoreCase("--stop"))
        && args.length == 1) {
      stopAll();
      sender.sendMessage(Lang.get("mine-stopped-all"));
      return true;
    }

    String botName = args[0];
    FakePlayer fp = manager.getByName(botName);
    if (fp == null) {
      sender.sendMessage(Lang.get("mine-not-found", "name", botName));
      return true;
    }

    Player bot = fp.getPlayer();
    if (bot == null || !bot.isOnline()) {
      sender.sendMessage(Lang.get("mine-bot-offline", "name", fp.getDisplayName()));
      return true;
    }

    if (args.length >= 2) {
      String action = args[1].toLowerCase(Locale.ROOT);

      if (action.equals("--find") || action.equals("find")) {
        if (findCommand == null || args.length < 3) {
          sender.sendMessage(Lang.get("find-usage"));
          return true;
        }
        boolean started =
            findCommand.startFindTask(sender, fp, java.util.Arrays.copyOfRange(args, 2, args.length));
        if (started) {
          sender.sendMessage(
              Lang.get(
                  "find-started",
                  "name", fp.getDisplayName(),
                  "block", args[2].toLowerCase(Locale.ROOT).replace('_', ' '),
                  "radius", String.valueOf(32),
                  "count", args.length >= 4 && !args[3].startsWith("-") ? args[3] : "∞"));
        }
        return true;
      }

      if (action.equals("--stripmine") || action.equals("stripmine")) {
        sender.sendMessage(Lang.get("mine-usage"));
        return true;
      }

      if (!AREA_MODE_ENABLED) {
        java.util.Set<String> areaActions =
            java.util.Set.of(
                "--pos1",
                "pos1",
                "--pos2",
                "pos2",
                "start",
                "--status",
                "status",
                "--clear",
                "clear");
        if (areaActions.contains(action)) {
          sender.sendMessage(Lang.get("mine-usage"));
          return true;
        }
      }

      switch (action) {
        case "stop", "--stop" -> {
          cleanupBot(fp.getUuid());
          sender.sendMessage(Lang.get("mine-stopped", "name", fp.getDisplayName()));
          return true;
        }
        case "--pos1", "pos1" -> {
          Location posLoc;
          if (args.length >= 5) {
            // Explicit coordinates: --pos1 <x> <y> <z>  (supports ~ notation)
            Location senderLoc = (sender instanceof Player pl) ? pl.getLocation() : bot.getLocation();
            try {
              int px = (int) Math.floor(parseCoord(args[2], senderLoc.getX()));
              int py = (int) Math.floor(parseCoord(args[3], senderLoc.getY()));
              int pz = (int) Math.floor(parseCoord(args[4], senderLoc.getZ()));
              posLoc = new Location(bot.getWorld(), px, py, pz);
            } catch (NumberFormatException e) {
              sender.sendMessage(Lang.get("mine-coords-invalid"));
              return true;
            }
          } else {
            if (!(sender instanceof Player player)) {
              sender.sendMessage(Lang.get("player-only"));
              return true;
            }
            Block target = player.getTargetBlockExact(LOOK_BLOCK_RANGE);
            if (target == null) {
              sender.sendMessage(Lang.get("mine-look-at-block"));
              return true;
            }
            posLoc = target.getLocation();
          }
          AreaSelection selection =
              selections.computeIfAbsent(fp.getUuid(), k -> new AreaSelection());
          selection.pos1 = posLoc;
          if (selectionStore != null) selectionStore.setPos1(fp.getName(), selection.pos1);
          sender.sendMessage(
              Lang.get(
                  "mine-pos1-set",
                  "name",
                  fp.getDisplayName(),
                  "x",
                  String.valueOf(posLoc.getBlockX()),
                  "y",
                  String.valueOf(posLoc.getBlockY()),
                  "z",
                  String.valueOf(posLoc.getBlockZ())));
          return true;
        }
        case "--pos2", "pos2" -> {
          Location posLoc;
          if (args.length >= 5) {
            Location senderLoc = (sender instanceof Player pl) ? pl.getLocation() : bot.getLocation();
            try {
              int px = (int) Math.floor(parseCoord(args[2], senderLoc.getX()));
              int py = (int) Math.floor(parseCoord(args[3], senderLoc.getY()));
              int pz = (int) Math.floor(parseCoord(args[4], senderLoc.getZ()));
              posLoc = new Location(bot.getWorld(), px, py, pz);
            } catch (NumberFormatException e) {
              sender.sendMessage(Lang.get("mine-coords-invalid"));
              return true;
            }
          } else {
            if (!(sender instanceof Player player)) {
              sender.sendMessage(Lang.get("player-only"));
              return true;
            }
            Block target = player.getTargetBlockExact(LOOK_BLOCK_RANGE);
            if (target == null) {
              sender.sendMessage(Lang.get("mine-look-at-block"));
              return true;
            }
            posLoc = target.getLocation();
          }
          AreaSelection selection =
              selections.computeIfAbsent(fp.getUuid(), k -> new AreaSelection());
          selection.pos2 = posLoc;
          if (selectionStore != null) selectionStore.setPos2(fp.getName(), selection.pos2);
          sender.sendMessage(
              Lang.get(
                  "mine-pos2-set",
                  "name",
                  fp.getDisplayName(),
                  "x",
                  String.valueOf(posLoc.getBlockX()),
                  "y",
                  String.valueOf(posLoc.getBlockY()),
                  "z",
                  String.valueOf(posLoc.getBlockZ())));
          return true;
        }
        case "start" -> {
          if (!selections.containsKey(fp.getUuid())
              && selectionStore != null
              && selectionStore.hasCompleteSelection(fp.getName())) {
            AreaSelection loaded = new AreaSelection();
            loaded.pos1 = selectionStore.getPos1(fp.getName());
            loaded.pos2 = selectionStore.getPos2(fp.getName());
            selections.put(fp.getUuid(), loaded);
          }
          startAreaMining(sender, fp);
          return true;
        }
        case "--wesel" -> {
          if (!(sender instanceof Player player)) {
            sender.sendMessage(Lang.get("player-only"));
            return true;
          }
          if (!plugin.isWorldEditAvailable()) {
            sender.sendMessage(Lang.get("worldedit-not-available"));
            return true;
          }
          Location[] corners = me.bill.fakePlayerPlugin.util.WorldEditHelper.getSelection(player);
          if (corners == null) {
            sender.sendMessage(Lang.get("worldedit-no-selection"));
            return true;
          }
          AreaSelection weSelection = new AreaSelection();
          weSelection.pos1 = corners[0];
          weSelection.pos2 = corners[1];
          selections.put(fp.getUuid(), weSelection);
          if (selectionStore != null) {
            selectionStore.setPos1(fp.getName(), weSelection.pos1);
            selectionStore.setPos2(fp.getName(), weSelection.pos2);
          }
          startAreaMining(sender, fp);
          sender.sendMessage(Lang.get("mine-wesel-applied", "name", fp.getDisplayName()));
          return true;
        }
      }
    }

    boolean once =
        args.length >= 2
            && (args[1].equalsIgnoreCase("once") || args[1].equalsIgnoreCase("--once"));
    stopAreaJob(fp.getUuid(), false);
    cancelAll(fp.getUuid());

    Location dest =
        (sender instanceof Player sp) ? sp.getLocation().clone() : bot.getLocation().clone();

    double xzDist = PathfindingService.xzDist(bot.getLocation(), dest);
    if (xzDist <= Config.pathfindingArrivalDistance()) {
      lockAndStartMining(fp, once, dest, null, false);
      sender.sendMessage(
          once
              ? Lang.get("mine-started-once", "name", fp.getDisplayName())
              : Lang.get("mine-started", "name", fp.getDisplayName()));
    } else {
      startNavigation(fp, dest, () -> lockAndStartMining(fp, once, dest, null, false));
      sender.sendMessage(Lang.get("mine-walking", "name", fp.getDisplayName()));
    }
    return true;
  }

  @Override
  public List<String> tabComplete(CommandSender sender, String[] args) {
    if (!canUse(sender)) return List.of();

    if (args.length == 1) {
      String prefix = args[0].toLowerCase(Locale.ROOT);
      List<String> out = new ArrayList<>();
      if ("--stop".startsWith(prefix)) out.add("--stop");
      if ("stop".startsWith(prefix)) out.add("stop");
      for (FakePlayer fp : manager.getActivePlayers()) {
        if (fp.getName().toLowerCase(Locale.ROOT).startsWith(prefix)) out.add(fp.getName());
      }
      return out;
    }

    if (args.length == 2
        && !args[0].equalsIgnoreCase("stop")
        && !args[0].equalsIgnoreCase("--stop")) {
      String prefix = args[1].toLowerCase(Locale.ROOT);
      List<String> out = new ArrayList<>();
      List<String> options =
          AREA_MODE_ENABLED
              ? List.of(
                  "--once",
                  "--stop",
                  "once",
                  "stop",
                  "--pos1",
                  "--pos2",
                  "--start",
                  "--wesel",
                  "--status",
                  "--clear")
              : List.of("--once", "--stop", "--wesel", "once", "stop");
      for (String option : options) {
        if (option.startsWith(prefix)) out.add(option);
      }
      return out;
    }

    return List.of();
  }

  private void startAreaMining(CommandSender sender, FakePlayer fp) {
    AreaSelection selection = selections.get(fp.getUuid());
    if (selection == null || !selection.isComplete()) {
      sender.sendMessage(Lang.get("mine-area-missing-selection", "name", fp.getDisplayName()));
      return;
    }
    if (!selection.sameWorld()) {
      sender.sendMessage(Lang.get("mine-area-world-mismatch"));
      return;
    }
    Player bot = fp.getPlayer();
    if (bot == null || !bot.isOnline()) {
      sender.sendMessage(Lang.get("mine-bot-offline", "name", fp.getDisplayName()));
      return;
    }
    if (bot.getWorld() != Objects.requireNonNull(selection.pos1.getWorld())) {
      sender.sendMessage(
          Lang.get(
              "mine-area-bot-world",
              "name",
              fp.getDisplayName(),
              "world",
              selection.pos1.getWorld().getName()));
      return;
    }

    cleanupBot(fp.getUuid());
    AreaMineJob job = new AreaMineJob(selection.copy(), sender);
    areaJobs.put(fp.getUuid(), job);
    int taskId =
        FppScheduler.runSyncRepeatingWithId(
            plugin, () -> tickAreaJob(fp.getUuid()), 0L, AREA_CONTROLLER_PERIOD);
    areaTasks.put(fp.getUuid(), taskId);

    if (selectionStore != null) selectionStore.setActive(fp.getName(), true);
    sender.sendMessage(
        Lang.get(
            "mine-area-started",
            "name",
            fp.getDisplayName(),
            "count",
            String.valueOf(selection.blockCount())));
  }

  private void startNavigation(
      FakePlayer fp,
      Location dest,
      @org.jetbrains.annotations.Nullable Location lockOnArrival,
      Runnable onArrive) {
    // Force breakBlocks=true so the bot can punch through obstructions en-route to its target.
    me.bill.fakePlayerPlugin.fakeplayer.BotPathfinder.PathOptions opts =
        new me.bill.fakePlayerPlugin.fakeplayer.BotPathfinder.PathOptions(
            fp.isNavParkour(), true, fp.isNavPlaceBlocks(),
            fp.isNavAvoidWater(), fp.isNavAvoidLava());
    pathfinding.navigate(
        fp,
        new PathfindingService.NavigationRequest(
            PathfindingService.Owner.MINE,
            () -> dest,
            Config.pathfindingArrivalDistance(),
            0.0,
            Integer.MAX_VALUE,
            onArrive,
            null,
            null,
            lockOnArrival,
            opts));
  }

  private void startNavigation(FakePlayer fp, Location dest, Runnable onArrive) {
    startNavigation(fp, dest, null, onArrive);
  }

  private void lockAndStartMining(
      FakePlayer fp,
      boolean once,
      Location lockLoc,
      BlockPos forcedTarget,
      boolean stopAfterForcedTarget) {
    FppApiImpl.fireTaskEvent(fp, "mine", me.bill.fakePlayerPlugin.api.event.FppBotTaskEvent.Action.START);
    UUID uuid = fp.getUuid();
    Player bot = fp.getPlayer();
    if (bot == null) return;

    // Apply the face-toward rotation without teleporting — the bot already arrived
    // at the stand location via PathfindingService; teleporting would cause a visible
    // snap/teleport on the client.
    bot.setRotation(lockLoc.getYaw(), lockLoc.getPitch());
    NmsPlayerSpawner.setHeadYaw(bot, lockLoc.getYaw());
    NmsPlayerSpawner.setMovementForward(bot, 0f);
    bot.setSprinting(false);

    Location actualLoc = bot.getLocation();
    actualLoc.setYaw(lockLoc.getYaw());
    actualLoc.setPitch(lockLoc.getPitch());
    manager.lockForAction(uuid, actualLoc);

    activeMineLocations.put(uuid, actualLoc.clone());
    activeMineOnceFlags.put(uuid, once);

    MiningState state = new MiningState();
    state.once = once;
    state.forcedTarget = forcedTarget;
    state.stopAfterForcedTarget = stopAfterForcedTarget;
    miningStates.put(uuid, state);

    int taskId =
        FppScheduler.runSyncRepeatingWithId(
            plugin,
            () -> {
              Player b = fp.getPlayer();
              if (b == null || !b.isOnline()) {
                stopMining(uuid);
                return;
              }
              tickMining(fp, state);
            },
            0L,
            1L);

    miningTasks.put(uuid, taskId);
  }

  private void cancelAll(UUID botUuid) {

    pathfinding.cancel(botUuid);

    stopMining(botUuid);

    FakePlayer fp = manager.getByUuid(botUuid);
    if (fp != null) {
      Player bot = fp.getPlayer();
      if (bot != null && bot.isOnline()) {
        NmsPlayerSpawner.setMovementForward(bot, 0f);
        NmsPlayerSpawner.setJumping(bot, false);
        bot.setSprinting(false);
      }
    }
  }

  public void stopMining(UUID botUuid) {
    FakePlayer fp = manager.getByUuid(botUuid);
    if (fp != null) {
      FppApiImpl.fireTaskEvent(fp, "mine", me.bill.fakePlayerPlugin.api.event.FppBotTaskEvent.Action.STOP);
    }
    Integer taskId = miningTasks.remove(botUuid);
    if (taskId != null) FppScheduler.cancelTask(taskId);

    manager.unlockAction(botUuid);
    manager.unlockNavigation(botUuid);

    activeMineLocations.remove(botUuid);
    activeMineOnceFlags.remove(botUuid);

    MiningState state = miningStates.remove(botUuid);
    if (state != null) {
      if (state.currentPos != null) {
        FakePlayer fp = manager.getByUuid(botUuid);
        if (fp != null) {
          Player bot = fp.getPlayer();
          if (bot != null && bot.isOnline()) {
            ServerPlayer nms = ((CraftPlayer) bot).getHandle();
            nms.level().destroyBlockProgress(-1, state.currentPos, -1);
            if (fireBlockBreakHook(fp, state.currentPos)) {
              nms.gameMode.handleBlockBreakAction(
                  state.currentPos,
                  ServerboundPlayerActionPacket.Action.ABORT_DESTROY_BLOCK,
                  Direction.DOWN,
                  nms.level().getMaxY(),
                  -1);
            }
          }
        }
      }
      if (state.completeForcedTargetOnStop
          && state.stopAfterForcedTarget
          && state.forcedTarget != null) {
        AreaMineJob job = areaJobs.get(botUuid);
        if (job != null && job.currentTarget != null) {
          job.completed.add(job.currentTarget);
          job.currentTarget = null;
          job.blocksMined++;
          FppScheduler.runSyncLater(plugin, () -> tickAreaJob(botUuid), 1L);
        }
      }
    }
  }

  public void stopAll() {
    pathfinding.cancelAll(PathfindingService.Owner.MINE);
    new HashSet<>(miningTasks.keySet()).forEach(this::cleanupBot);
    new HashSet<>(areaTasks.keySet()).forEach(this::cleanupBot);
  }

  public void cleanupBot(UUID botUuid) {
    cancelAll(botUuid);
    stopAreaJob(botUuid, false);
  }

  public void clearSelection(UUID botUuid) {
    selections.remove(botUuid);
    if (selectionStore != null) {
      FakePlayer fp = manager.getByUuid(botUuid);
      if (fp != null) selectionStore.clearSelection(fp.getName());
    }
  }

  public boolean isNavigating(UUID botUuid) {
    return pathfinding.isNavigating(botUuid);
  }

  public boolean isMining(UUID botUuid) {
    return miningTasks.containsKey(botUuid);
  }

  @org.jetbrains.annotations.Nullable
  public Location getActiveMineLocation(UUID botUuid) {
    return activeMineLocations.get(botUuid);
  }

  public boolean isActiveMineOnce(UUID botUuid) {
    Boolean v = activeMineOnceFlags.get(botUuid);
    return v != null && v;
  }

  @org.jetbrains.annotations.Nullable
  public Location getSelectionPos1(UUID botUuid) {
    AreaSelection s = selections.get(botUuid);
    return s != null ? s.pos1 : null;
  }

  @org.jetbrains.annotations.Nullable
  public Location getSelectionPos2(UUID botUuid) {
    AreaSelection s = selections.get(botUuid);
    return s != null ? s.pos2 : null;
  }

  public boolean hasActiveAreaJob(UUID botUuid) {
    return areaJobs.containsKey(botUuid);
  }

  public void restoreAreaJob(FakePlayer fp, Location pos1, Location pos2) {
    if (!AREA_MODE_ENABLED) return;
    if (fp == null || pos1 == null || pos2 == null) return;
    Player bot = fp.getPlayer();
    if (bot == null || !bot.isOnline()) return;
    if (pos1.getWorld() == null || pos2.getWorld() == null) return;
    if (!pos1.getWorld().equals(pos2.getWorld())) return;
    if (!pos1.getWorld().equals(bot.getWorld())) return;

    AreaSelection sel = new AreaSelection();
    sel.pos1 = pos1.clone();
    sel.pos2 = pos2.clone();
    selections.put(fp.getUuid(), sel);

    if (selectionStore != null) {
      selectionStore.setPos1(fp.getName(), sel.pos1);
      selectionStore.setPos2(fp.getName(), sel.pos2);
      selectionStore.setActive(fp.getName(), true);
    }

    cleanupBot(fp.getUuid());
    AreaMineJob job = new AreaMineJob(sel.copy(), Bukkit.getConsoleSender());
    areaJobs.put(fp.getUuid(), job);
    int taskId =
        FppScheduler.runSyncRepeatingWithId(
            plugin, () -> tickAreaJob(fp.getUuid()), 0L, AREA_CONTROLLER_PERIOD);
    areaTasks.put(fp.getUuid(), taskId);
    Config.debug(
        "Restored area mining for bot '" + fp.getName() + "' (" + sel.blockCount() + " blocks).");
  }

  public void resumeMining(FakePlayer fp) {
    UUID uuid = fp.getUuid();
    Location mineLoc = getActiveMineLocation(uuid);
    boolean once = isActiveMineOnce(uuid);
    if (mineLoc != null) {
      resumeMining(fp, once, mineLoc);
    }
  }

  public void resumeMining(FakePlayer fp, boolean once, Location loc) {
    if (fp == null || loc == null) return;
    Player bot = fp.getPlayer();
    if (bot == null || !bot.isOnline()) return;
    cancelAll(fp.getUuid());
    double xzDist = PathfindingService.xzDist(bot.getLocation(), loc);
    if (xzDist <= Config.pathfindingArrivalDistance()) {
      lockAndStartMining(fp, once, loc, null, false);
    } else {
      startNavigation(fp, loc, () -> lockAndStartMining(fp, once, loc, null, false));
    }
  }

  private void tickAreaJob(UUID botUuid) {
    AreaMineJob job = areaJobs.get(botUuid);
    if (job == null) {
      stopAreaJob(botUuid, false);
      return;
    }
    FakePlayer fp = manager.getByUuid(botUuid);
    if (fp == null) {
      stopAreaJob(botUuid, false);
      return;
    }
    Player bot = fp.getPlayer();
    if (bot == null || !bot.isOnline()) {
      stopAreaJob(botUuid, false);
      return;
    }

    if (miningTasks.containsKey(botUuid)) {
      if (job.currentTarget != null) {
        Location lockLoc = activeMineLocations.get(botUuid);
        if (lockLoc != null && !BotNavUtil.isAtActionLocation(bot, lockLoc)) {

          interruptMining(botUuid);
          Location retryFace =
              BotNavUtil.faceToward(lockLoc.clone(), job.currentTarget.center(bot.getWorld()));
          startNavigation(
              fp,
              retryFace,
              retryFace,
              () -> lockAndStartMining(fp, false, retryFace, job.currentTarget.toBlockPos(), true));
        }
      }
      return;
    }

    if (pathfinding.isNavigating(botUuid)) return;
    if (job.depositingToStorage) return;

    boolean hasStorage = !storageStore.getStorages(fp.getName()).isEmpty();
    if (hasStorage && isStorageOffloadNeeded(bot.getInventory())) {
      if (!startStorageOffload(fp, job)) {
        notifyStarter(job, "mine-storage-unavailable", "name", fp.getDisplayName());
        stopAreaJob(botUuid, false);
      }
      return;
    }

    if (job.finishingDeposit) {
      notifyStarter(
          job,
          "mine-area-finished",
          "name",
          fp.getDisplayName(),
          "count",
          String.valueOf(job.blocksMined));
      stopAreaJob(botUuid, false);
      return;
    }

    if (fp.isPickUpItemsEnabled()) {
      Location dropTarget = nearestAnticipatedDrop(bot, job);
      if (dropTarget != null && bot.getLocation().distanceSquared(dropTarget) > 1.5 * 1.5) {
        startNavigation(fp, dropTarget, () -> {});
        return;
      }
    } else if (!job.anticipatedDrops.isEmpty()) {
      job.anticipatedDrops.clear();
    }

    AreaBlock next = findNextAreaTarget(bot, job.selection, job);
    if (next == null) {

      if (!job.skipped.isEmpty() && job.skipRetries < 2) {
        job.skipped.clear();
        job.skipRetries++;
        return;
      }

      if (job.currentLayer > job.selection.minY()) {
        job.currentLayer--;
        job.skipped.clear();
        job.skipRetries = 0;
        return;
      }

      if (hasStorage && hasDepositableLoot(bot.getInventory())) {
        job.finishingDeposit = true;
        if (!startStorageOffload(fp, job)) {

          notifyStarter(job, "mine-storage-unavailable", "name", fp.getDisplayName());
          notifyStarter(
              job,
              "mine-area-finished",
              "name",
              fp.getDisplayName(),
              "count",
              String.valueOf(job.blocksMined));
          stopAreaJob(botUuid, false);
        }
        return;
      }
      notifyStarter(
          job,
          "mine-area-finished",
          "name",
          fp.getDisplayName(),
          "count",
          String.valueOf(job.blocksMined));
      stopAreaJob(botUuid, false);
      return;
    }
    job.skipRetries = 0;
    job.finishingDeposit = false;

    Location standLoc =
        BotNavUtil.findStandLocation(
            bot.getWorld(), job.selection::contains, next.x(), next.y(), next.z());
    if (standLoc == null) {
      job.skipped.add(next);
      return;
    }

    Location targetCenter = next.center(bot.getWorld());
    Location faceLoc = BotNavUtil.faceToward(standLoc, targetCenter);
    job.currentTarget = next;

    startNavigation(
        fp,
        faceLoc,
        faceLoc,
        () -> lockAndStartMining(fp, false, faceLoc, next.toBlockPos(), true));
  }

  private boolean startStorageOffload(FakePlayer fp, AreaMineJob job) {
    List<StorageStore.StoragePoint> storages = storageStore.getStorages(fp.getName());
    if (storages.isEmpty()) return false;
    Player bot = fp.getPlayer();
    if (bot == null) return false;

    for (int attempt = 0; attempt < storages.size(); attempt++) {
      int idx = (job.preferredStorageIndex + attempt) % storages.size();
      StorageStore.StoragePoint point = storages.get(idx);
      if (point.location().getWorld() != bot.getWorld()) continue;
      Block block = point.location().getBlock();
      if (!(block.getState() instanceof org.bukkit.inventory.InventoryHolder holder)) continue;
      if (!containerCanAcceptAny(bot.getInventory(), holder.getInventory())) continue;
      Location standLoc =
          BotNavUtil.findStandLocation(
              bot.getWorld(), null, block.getX(), block.getY(), block.getZ());
      if (standLoc == null) continue;
      Location faceLoc = BotNavUtil.faceToward(standLoc, block.getLocation().add(0.5, 0.5, 0.5));
      final int targetIndex = idx;
      startNavigation(fp, faceLoc, () -> depositToStorage(fp, job, targetIndex, point));
      return true;
    }
    return false;
  }

  private void depositToStorage(
      FakePlayer fp, AreaMineJob job, int storageIndex, StorageStore.StoragePoint point) {
    Player bot = fp.getPlayer();
    if (bot == null || !bot.isOnline()) return;
    Block block = point.location().getBlock();
    if (!(block.getState() instanceof org.bukkit.inventory.InventoryHolder)) {
      job.preferredStorageIndex =
          (storageIndex + 1) % Math.max(1, storageStore.getStorages(fp.getName()).size());
      return;
    }
    Location standLoc =
        BotNavUtil.findStandLocation(
            bot.getWorld(), null, block.getX(), block.getY(), block.getZ());
    if (standLoc == null) {
      job.preferredStorageIndex =
          (storageIndex + 1) % Math.max(1, storageStore.getStorages(fp.getName()).size());
      return;
    }
    Location faceLoc = BotNavUtil.faceToward(standLoc, block.getLocation().add(0.5, 0.5, 0.5));
    if (!BotNavUtil.isAtActionLocation(bot, faceLoc)) {
      startNavigation(fp, faceLoc, () -> depositToStorage(fp, job, storageIndex, point));
      return;
    }

    final int sizes = Math.max(1, storageStore.getStorages(fp.getName()).size());
    job.depositingToStorage = true;
    StorageInteractionHelper.interact(
        fp,
        faceLoc,
        block,
        plugin,
        manager,
        (holder, liveBot) -> {
          int moved = moveBotInventoryToStorage(liveBot.getInventory(), holder.getInventory());
          job.preferredStorageIndex = moved > 0 ? storageIndex : (storageIndex + 1) % sizes;
        },
        () -> job.depositingToStorage = false);
  }

  private void tickMining(FakePlayer fp, MiningState state) {
    Player bot = fp.getPlayer();
    if (bot == null || !bot.isOnline()) {
      stopMining(fp.getUuid());
      return;
    }

    ServerPlayer nms = ((CraftPlayer) bot).getHandle();
    if (state.freeze > 0) {
      state.freeze--;
      return;
    }

    if (state.waitingForDrops) {
      if (state.pickupWaitTicks > 0) {
        state.pickupWaitTicks--;
        return;
      }
      if (fp.isPickUpItemsEnabled()
          && state.pickupWaitExtraTicks > 0
          && hasNearbyDrops(bot, state.forcedTarget)) {
        state.pickupWaitExtraTicks--;
        return;
      }
      state.waitingForDrops = false;
      state.pickupWaitExtraTicks = 0;
      if (state.stopAfterForcedTarget) {
        stopMining(fp.getUuid());
        return;
      }
    }

    BlockPos targetPos = state.forcedTarget != null ? state.forcedTarget : getTargetBlock(bot);
    if (targetPos == null) {
      if (state.currentPos != null) abortMining(fp, nms, state);
      if (state.stopAfterForcedTarget) stopMining(fp.getUuid());
      return;
    }

    BlockState blockState = nms.level().getBlockState(targetPos);
    if (blockState.isAir()) {
      if (state.currentPos != null && state.currentPos.equals(targetPos)) {
        state.currentPos = null;
        state.progress = 0;
      }
      if (state.stopAfterForcedTarget) stopMining(fp.getUuid());
      return;
    }

    if (nms.blockActionRestricted(nms.level(), targetPos, nms.gameMode.getGameModeForPlayer()))
      return;

    equipBestMiningTool(bot, targetPos);

    Direction side = Direction.DOWN;
    if (bot.getGameMode() == GameMode.CREATIVE) {
      if (fireBlockBreakHook(fp, targetPos)) {
        nms.gameMode.handleBlockBreakAction(
            targetPos,
            ServerboundPlayerActionPacket.Action.START_DESTROY_BLOCK,
            side,
            nms.level().getMaxY(),
            -1);
      }
      nms.swing(InteractionHand.MAIN_HAND);
      if (state.once || state.stopAfterForcedTarget) {
        beginPickupWait(fp, state, targetPos);
      } else {
        state.freeze = 5;
        state.currentPos = null;
      }
      return;
    }

    if (state.currentPos == null || !state.currentPos.equals(targetPos)) {
      if (state.currentPos != null) {
        if (fireBlockBreakHook(fp, state.currentPos)) {
          nms.gameMode.handleBlockBreakAction(
              state.currentPos,
              ServerboundPlayerActionPacket.Action.ABORT_DESTROY_BLOCK,
              side,
              nms.level().getMaxY(),
              -1);
        }
      }

      if (fireBlockBreakHook(fp, targetPos)) {
        nms.gameMode.handleBlockBreakAction(
            targetPos,
            ServerboundPlayerActionPacket.Action.START_DESTROY_BLOCK,
            side,
            nms.level().getMaxY(),
            -1);
      }

      if (state.progress == 0) blockState.attack(nms.level(), targetPos, nms);

      float speed = blockState.getDestroyProgress(nms, nms.level(), targetPos);
      if (speed >= 1.0F) {
        nms.swing(InteractionHand.MAIN_HAND);
        if (state.once || state.stopAfterForcedTarget) {
          beginPickupWait(fp, state, targetPos);
        } else {
          state.currentPos = null;
          state.freeze = 5;
        }
        return;
      }
      state.currentPos = targetPos;
      state.progress = 0;
    } else {
      float speed = blockState.getDestroyProgress(nms, nms.level(), targetPos);
      state.progress += speed;
      if (state.progress >= 1.0F) {
        if (fireBlockBreakHook(fp, targetPos)) {
          nms.gameMode.handleBlockBreakAction(
              targetPos,
              ServerboundPlayerActionPacket.Action.STOP_DESTROY_BLOCK,
              side,
              nms.level().getMaxY(),
              -1);
        }
        nms.swing(InteractionHand.MAIN_HAND);
        if (state.once || state.stopAfterForcedTarget) {
          beginPickupWait(fp, state, targetPos);
        } else {
          state.currentPos = null;
          state.progress = 0;
          state.freeze = 5;
        }
        return;
      }
      nms.level().destroyBlockProgress(-1, targetPos, (int) (state.progress * 10));
    }

    nms.swing(InteractionHand.MAIN_HAND);
    nms.resetLastActionTime();
  }

  private void abortMining(FakePlayer fp, ServerPlayer nms, MiningState state) {
    if (state.currentPos == null) return;
    nms.level().destroyBlockProgress(-1, state.currentPos, -1);
    if (fp != null && fireBlockBreakHook(fp, state.currentPos)) {
      nms.gameMode.handleBlockBreakAction(
          state.currentPos,
          ServerboundPlayerActionPacket.Action.ABORT_DESTROY_BLOCK,
          Direction.DOWN,
          nms.level().getMaxY(),
          -1);
    }
    state.currentPos = null;
    state.progress = 0;
  }

  private boolean fireBlockBreakHook(FakePlayer fp, BlockPos pos) {
    if (fp == null || pos == null) return false;
    Player bot = fp.getPlayer();
    if (bot == null || bot.getWorld() == null) return false;
    var event =
        new FppBotBlockBreakEvent(
            new FppBotImpl(fp), bot.getWorld().getBlockAt(pos.getX(), pos.getY(), pos.getZ()));
    Bukkit.getPluginManager().callEvent(event);
    return !event.isCancelled();
  }

  private BlockPos getTargetBlock(Player bot) {
    try {
      Location eye = bot.getEyeLocation();
      BlockIterator iter =
          new BlockIterator(bot.getWorld(), eye.toVector(), eye.getDirection(), 0, 5);
      while (iter.hasNext()) {
        Block b = iter.next();
        if (!b.getType().isAir() && b.getType().isSolid()) {
          return new BlockPos(b.getX(), b.getY(), b.getZ());
        }
      }
    } catch (IllegalStateException ignored) {
    }
    return null;
  }

  private AreaBlock findNextAreaTarget(Player bot, AreaSelection selection, AreaMineJob job) {
    World world = bot.getWorld();
    AreaBlock best = null;
    double bestDist = Double.MAX_VALUE;

    int y = job.currentLayer;
    if (y < selection.minY() || y > selection.maxY()) return null;

    for (int x = selection.minX(); x <= selection.maxX(); x++) {
      for (int z = selection.minZ(); z <= selection.maxZ(); z++) {
        AreaBlock candidate = new AreaBlock(x, y, z);
        if (job.completed.contains(candidate) || job.skipped.contains(candidate)) continue;
        Block block = world.getBlockAt(x, y, z);
        if (!isMineable(block)) {
          job.completed.add(candidate);
          continue;
        }
        double dist = block.getLocation().add(0.5, 0.5, 0.5).distanceSquared(bot.getLocation());
        if (dist < bestDist) {
          bestDist = dist;
          best = candidate;
        }
      }
    }
    return best;
  }

  private boolean isMineable(Block block) {
    if (block == null) return false;
    Material type = block.getType();
    if (type.isAir() || !type.isSolid()) return false;
    if (block.getState() instanceof InventoryHolder) return false;
    return switch (type) {
      case BEDROCK,
          BARRIER,
          END_PORTAL,
          END_PORTAL_FRAME,
          NETHER_PORTAL,
          COMMAND_BLOCK,
          CHAIN_COMMAND_BLOCK,
          REPEATING_COMMAND_BLOCK,
          STRUCTURE_BLOCK,
          JIGSAW,
          LIGHT,
          REINFORCED_DEEPSLATE ->
          false;
      default -> true;
    };
  }

  private boolean isStorageOffloadNeeded(PlayerInventory inv) {
    return inv.firstEmpty() == -1;
  }

  private boolean hasDepositableLoot(PlayerInventory inv) {
    int heldSlot = inv.getHeldItemSlot();
    for (int slot = 0; slot < 36; slot++) {
      if (isDepositCandidate(slot, heldSlot, inv.getItem(slot))) return true;
    }
    return false;
  }

  private int moveBotInventoryToStorage(PlayerInventory source, Inventory target) {
    int moved = 0;
    int heldSlot = source.getHeldItemSlot();
    for (int slot = 0; slot < 36; slot++) {
      ItemStack item = source.getItem(slot);
      if (!isDepositCandidate(slot, heldSlot, item)) continue;
      ItemStack original = item.clone();
      Map<Integer, ItemStack> leftovers = target.addItem(original);
      if (leftovers.isEmpty()) {
        moved += original.getAmount();
        source.setItem(slot, null);
      } else {
        ItemStack left = leftovers.values().iterator().next();
        int movedNow = original.getAmount() - left.getAmount();
        if (movedNow > 0) {
          moved += movedNow;
          source.setItem(slot, left);
        }
      }
    }
    return moved;
  }

  private boolean containerCanAcceptAny(PlayerInventory source, Inventory target) {
    int heldSlot = source.getHeldItemSlot();
    for (int slot = 0; slot < 36; slot++) {
      ItemStack item = source.getItem(slot);
      if (!isDepositCandidate(slot, heldSlot, item)) continue;
      if (inventoryCanFit(target, item)) return true;
    }
    return false;
  }

  private boolean inventoryCanFit(Inventory inv, ItemStack item) {
    for (ItemStack content : inv.getContents()) {
      if (content == null || content.getType() == Material.AIR) return true;
      if (content.isSimilar(item) && content.getAmount() < content.getMaxStackSize()) return true;
    }
    return false;
  }

  private boolean isDepositCandidate(int slot, int heldSlot, ItemStack item) {
    if (item == null || item.getType() == Material.AIR) return false;
    if (slot == heldSlot && isLikelyMiningTool(item)) return false;
    return !isLikelyMiningTool(item);
  }

  private boolean isLikelyMiningTool(ItemStack item) {
    Material type = item.getType();
    return type.name().endsWith("_PICKAXE")
        || type.name().endsWith("_AXE")
        || type.name().endsWith("_SHOVEL")
        || type.name().endsWith("_HOE")
        || type.name().endsWith("_SWORD")
        || type == Material.SHEARS;
  }

  @org.jetbrains.annotations.Nullable
  private Location nearestAnticipatedDrop(Player bot, AreaMineJob job) {

    long now = System.currentTimeMillis();
    job.anticipatedDrops.entrySet().removeIf(e -> e.getValue() < now);
    if (job.anticipatedDrops.isEmpty()) return null;

    Location botLoc = bot.getLocation();
    Location nearest = null;
    double nearestDistSq = Double.MAX_VALUE;

    for (Map.Entry<BlockPos, Long> entry : new ArrayList<>(job.anticipatedDrops.entrySet())) {
      BlockPos pos = entry.getKey();
      Location center =
          new Location(bot.getWorld(), pos.getX() + 0.5, pos.getY() + 0.5, pos.getZ() + 0.5);

      Location itemTarget = nearestItemNearTrackedDrop(bot.getWorld(), center);
      if (itemTarget != null) {
        double distSq = botLoc.distanceSquared(itemTarget);
        if (distSq < nearestDistSq) {
          nearestDistSq = distSq;
          nearest = itemTarget;
        }
        continue;
      }

      double centerDistSq = botLoc.distanceSquared(center);
      if (centerDistSq <= 1.5 * 1.5) {
        job.anticipatedDrops.remove(pos);
        continue;
      }

      if (centerDistSq < nearestDistSq) {
        nearestDistSq = centerDistSq;
        nearest = center;
      }
    }
    return nearest;
  }

  @org.jetbrains.annotations.Nullable
  private Location nearestItemNearTrackedDrop(World world, Location center) {
    Location nearest = null;
    double nearestDistSq = Double.MAX_VALUE;
    for (org.bukkit.entity.Entity entity : world.getNearbyEntities(center, 4.0, 2.5, 4.0)) {
      if (!(entity instanceof Item item) || item.isDead() || item.getPickupDelay() > 0) continue;
      Location itemLoc = item.getLocation();
      double distSq = itemLoc.distanceSquared(center);
      if (distSq < nearestDistSq) {
        nearestDistSq = distSq;
        nearest = itemLoc.clone();
      }
    }
    return nearest;
  }

  private void beginPickupWait(FakePlayer fp, MiningState state, BlockPos targetPos) {
    state.currentPos = null;
    state.progress = 0;
    state.freeze = 0;
    state.forcedTarget = targetPos;
    state.completeForcedTargetOnStop = state.stopAfterForcedTarget;
    state.waitingForDrops = true;

    AreaMineJob activeJob = areaJobs.get(fp.getUuid());
    if (activeJob != null && targetPos != null && fp.isPickUpItemsEnabled()) {
      activeJob.anticipatedDrops.put(targetPos, System.currentTimeMillis() + DROP_LOITER_MS);
    }

    boolean hasStorage = !storageStore.getStorages(fp.getName()).isEmpty();
    if (!hasStorage) {
      state.pickupWaitTicks = 0;
      state.pickupWaitExtraTicks = 0;
    } else {
      state.pickupWaitTicks = fp.isPickUpItemsEnabled() ? AREA_PICKUP_WAIT_TICKS : 1;
      state.pickupWaitExtraTicks = fp.isPickUpItemsEnabled() ? AREA_PICKUP_EXTRA_TICKS : 0;
    }
  }

  private void interruptMining(UUID botUuid) {
    Integer taskId = miningTasks.remove(botUuid);
    if (taskId != null) FppScheduler.cancelTask(taskId);

    manager.unlockAction(botUuid);
    manager.unlockNavigation(botUuid);
    activeMineLocations.remove(botUuid);
    activeMineOnceFlags.remove(botUuid);

    MiningState state = miningStates.remove(botUuid);
    if (state == null) return;

    FakePlayer fp = manager.getByUuid(botUuid);
    if (fp == null) return;
    Player bot = fp.getPlayer();
    if (bot == null || !bot.isOnline()) return;

    ServerPlayer nms = ((CraftPlayer) bot).getHandle();
    abortMining(fp, nms, state);
  }

  private boolean isAtLockLocation(Player bot, Location lockLoc) {
    if (bot == null || lockLoc == null) return false;
    if (bot.getWorld() != lockLoc.getWorld()) return false;
    double xz = PathfindingService.xzDist(bot.getLocation(), lockLoc);
    double dy = Math.abs(bot.getLocation().getY() - lockLoc.getY());
    return xz <= Config.pathfindingArrivalDistance() && dy < 1.25;
  }

  private void useStorageBlock(Player bot, Block block) {
    try {
      ServerPlayer nms = ((CraftPlayer) bot).getHandle();
      BlockPos pos = new BlockPos(block.getX(), block.getY(), block.getZ());
      Vec3 hitVec = new Vec3(block.getX() + 0.5, block.getY() + 0.5, block.getZ() + 0.5);
      BlockHitResult hit = new BlockHitResult(hitVec, Direction.UP, pos, false);
      nms.resetLastActionTime();
      var result =
          nms.gameMode.useItemOn(
              nms,
              nms.level(),
              nms.getItemInHand(InteractionHand.MAIN_HAND),
              InteractionHand.MAIN_HAND,
              hit);
      if (result.consumesAction()) {
        nms.swing(InteractionHand.MAIN_HAND);
      }
    } catch (Throwable ignored) {
    }
  }

  private boolean hasNearbyDrops(Player bot, BlockPos targetPos) {
    if (targetPos == null) return false;
    Location center =
        new Location(
            bot.getWorld(), targetPos.getX() + 0.5, targetPos.getY() + 0.5, targetPos.getZ() + 0.5);
    for (org.bukkit.entity.Entity entity : bot.getNearbyEntities(2.0, 1.5, 2.0)) {
      if (!(entity instanceof Item item) || item.isDead()) continue;
      if (item.getLocation().distanceSquared(center) <= 6.25) return true;
    }
    return false;
  }

  private void equipBestMiningTool(Player bot, BlockPos targetPos) {
    // Handled by the addon auto-equipment tick handler.
  }

  private int toolScore(ItemStack item, ToolClass preferred) {
    if (item == null || item.getType() == Material.AIR) return Integer.MIN_VALUE;
    Material type = item.getType();
    ToolClass actual = classifyTool(type);
    if (actual == ToolClass.NONE) return Integer.MIN_VALUE;

    int score = toolTierScore(type);
    if (actual == preferred) score += 10_000;
    else if (preferred == ToolClass.SHEARS && type == Material.SHEARS) score += 10_000;
    else if (preferred == ToolClass.NONE) score += 100;
    else score += 1_000;

    if (type == Material.SHEARS && preferred != ToolClass.SHEARS) score -= 500;
    return score;
  }

  private ToolClass determineToolClass(Material blockType) {
    if (blockType == Material.COBWEB) return ToolClass.SWORD;
    if (blockType.name().contains("LEAVES")
        || blockType == Material.VINE
        || blockType == Material.GLOW_LICHEN
        || blockType.name().endsWith("_WOOL")) {
      return ToolClass.SHEARS;
    }
    if (Tag.MINEABLE_PICKAXE.isTagged(blockType)) return ToolClass.PICKAXE;
    if (Tag.MINEABLE_AXE.isTagged(blockType)) return ToolClass.AXE;
    if (Tag.MINEABLE_SHOVEL.isTagged(blockType)) return ToolClass.SHOVEL;
    if (Tag.MINEABLE_HOE.isTagged(blockType)) return ToolClass.HOE;
    return ToolClass.NONE;
  }

  private ToolClass classifyTool(Material toolType) {
    String name = toolType.name();
    if (toolType == Material.SHEARS) return ToolClass.SHEARS;
    if (name.endsWith("_PICKAXE")) return ToolClass.PICKAXE;
    if (name.endsWith("_AXE")) return ToolClass.AXE;
    if (name.endsWith("_SHOVEL")) return ToolClass.SHOVEL;
    if (name.endsWith("_HOE")) return ToolClass.HOE;
    if (name.endsWith("_SWORD")) return ToolClass.SWORD;
    return ToolClass.NONE;
  }

  private int toolTierScore(Material toolType) {
    String name = toolType.name();
    if (toolType == Material.SHEARS) return 650;
    if (name.startsWith("NETHERITE_")) return 900;
    if (name.startsWith("DIAMOND_")) return 800;
    if (name.startsWith("IRON_")) return 700;
    if (name.startsWith("GOLDEN_")) return 600;
    if (name.startsWith("STONE_")) return 500;
    if (name.startsWith("WOODEN_")) return 400;
    return 100;
  }

  private void stopAreaJob(UUID botUuid, boolean notifyStop) {
    Integer taskId = areaTasks.remove(botUuid);
    if (taskId != null) FppScheduler.cancelTask(taskId);
    AreaMineJob job = areaJobs.remove(botUuid);

    if (selectionStore != null) {
      FakePlayer fp = manager.getByUuid(botUuid);
      if (fp != null) selectionStore.setActive(fp.getName(), false);
    }
    if (notifyStop && job != null) {
      FakePlayer fp = manager.getByUuid(botUuid);
      notifyStarter(
          job, "mine-area-stopped", "name", fp != null ? fp.getDisplayName() : botUuid.toString());
    }
  }

  private void notifyStarter(AreaMineJob job, String key, String... args) {
    if (job.starterUuid != null) {
      Player p = Bukkit.getPlayer(job.starterUuid);
      if (p != null && p.isOnline()) {
        p.sendMessage(Lang.get(key, args));
        return;
      }
    }
    if (job.consoleStarted) {
      plugin.getLogger().info(Lang.raw(key, args));
    }
  }

  private static final class MiningState {
    BlockPos currentPos;
    float progress;
    int freeze;
    boolean once;
    BlockPos forcedTarget;
    boolean stopAfterForcedTarget;
    boolean completeForcedTargetOnStop;
    boolean waitingForDrops;
    int pickupWaitTicks;
    int pickupWaitExtraTicks;
  }

  private enum ToolClass {
    PICKAXE,
    AXE,
    SHOVEL,
    HOE,
    SHEARS,
    SWORD,
    NONE
  }

  private static final class AreaSelection {
    Location pos1;
    Location pos2;

    boolean isComplete() {
      return pos1 != null && pos2 != null;
    }

    boolean sameWorld() {
      return isComplete() && pos1.getWorld() != null && pos1.getWorld().equals(pos2.getWorld());
    }

    int minX() {
      return Math.min(pos1.getBlockX(), pos2.getBlockX());
    }

    int maxX() {
      return Math.max(pos1.getBlockX(), pos2.getBlockX());
    }

    int minY() {
      return Math.min(pos1.getBlockY(), pos2.getBlockY());
    }

    int maxY() {
      return Math.max(pos1.getBlockY(), pos2.getBlockY());
    }

    int minZ() {
      return Math.min(pos1.getBlockZ(), pos2.getBlockZ());
    }

    int maxZ() {
      return Math.max(pos1.getBlockZ(), pos2.getBlockZ());
    }

    int blockCount() {
      return (maxX() - minX() + 1) * (maxY() - minY() + 1) * (maxZ() - minZ() + 1);
    }

    boolean contains(int x, int y, int z) {
      return isComplete()
          && x >= minX()
          && x <= maxX()
          && y >= minY()
          && y <= maxY()
          && z >= minZ()
          && z <= maxZ();
    }

    AreaSelection copy() {
      AreaSelection copy = new AreaSelection();
      copy.pos1 = pos1.clone();
      copy.pos2 = pos2.clone();
      return copy;
    }
  }

  private static final class AreaMineJob {
    final AreaSelection selection;
    final UUID starterUuid;
    final boolean consoleStarted;
    final Set<AreaBlock> completed = new HashSet<>();
    final Set<AreaBlock> skipped = new HashSet<>();

    final Map<BlockPos, Long> anticipatedDrops = new ConcurrentHashMap<>();
    AreaBlock currentTarget;
    int preferredStorageIndex = 0;
    int blocksMined = 0;

    boolean finishingDeposit = false;

    int currentLayer;

    int skipRetries = 0;

    boolean depositingToStorage = false;

    AreaMineJob(AreaSelection selection, CommandSender sender) {
      this.selection = selection;
      this.starterUuid = sender instanceof Player p ? p.getUniqueId() : null;
      this.consoleStarted = !(sender instanceof Player);
      this.currentLayer = selection.maxY();
    }
  }

  private record AreaBlock(int x, int y, int z) {
    BlockPos toBlockPos() {
      return new BlockPos(x, y, z);
    }

    Location center(World world) {
      return new Location(world, x + 0.5, y + 0.5, z + 0.5);
    }
  }

  /**
   * Parses a single coordinate token.  Supports:
   *   "~"         → {@code base}
   *   "~<offset>" → {@code base + offset}
   *   "<number>"  → absolute value
   * Throws {@link NumberFormatException} if the token is unrecognisable.
   */
  static double parseCoord(String token, double base) {
    if (token.startsWith("~")) {
      String rest = token.substring(1);
      return rest.isEmpty() ? base : base + Double.parseDouble(rest);
    }
    return Double.parseDouble(token);
  }
}
