package me.bill.fakePlayerPlugin.command;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;
import me.bill.fakePlayerPlugin.api.FppBotBlockPlaceEvent;
import me.bill.fakePlayerPlugin.api.impl.FppApiImpl;
import me.bill.fakePlayerPlugin.api.impl.FppBotImpl;
import me.bill.fakePlayerPlugin.FakePlayerPlugin;
import me.bill.fakePlayerPlugin.config.Config;
import me.bill.fakePlayerPlugin.fakeplayer.BotNavUtil;
import me.bill.fakePlayerPlugin.fakeplayer.BotPathfinder;
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
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.Vec3;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.World;
import org.bukkit.block.Block;
import org.bukkit.block.BlockFace;
import org.bukkit.command.CommandSender;
import org.bukkit.craftbukkit.entity.CraftPlayer;
import org.bukkit.entity.Player;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.InventoryHolder;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.PlayerInventory;

public final class PlaceCommand implements FppCommand {

  private static final boolean AREA_MODE_ENABLED = false;

  private enum Phase {
    FILLING,

    RECHECKING,

    CLEANING_SCAFFOLD
  }

  private static final int CONTROLLER_PERIOD = 1;
  private static final int PROGRESS_INTERVAL = 10;
  private static final int SKIP_RETRY_LIMIT = 3;

  private static final double PLACE_REACH = 4.5;

  private static final int PLACE_COOLDOWN = 5;

  private static final int SCAFFOLD_MAX_RETRIES = 4;

  private static final Material[] SCAFFOLD_PREF = {
    Material.DIRT, Material.COBBLESTONE, Material.STONE, Material.SAND,
    Material.GRAVEL, Material.NETHERRACK, Material.DIORITE, Material.ANDESITE,
    Material.GRANITE, Material.COBBLED_DEEPSLATE
  };

  private final FakePlayerPlugin plugin;
  private final FakePlayerManager manager;
  private final PathfindingService pathfinding;
  private final StorageStore storageStore;

  private final Map<UUID, Integer> placingTasks = new ConcurrentHashMap<>();
  private final Map<UUID, PlaceState> placeStates = new ConcurrentHashMap<>();

  private final Map<UUID, AreaSelection> selections = new ConcurrentHashMap<>();
  private final Map<UUID, List<BlockEntry>> blockSpecs = new ConcurrentHashMap<>();
  private final Map<UUID, PlaceJob> placeJobs = new ConcurrentHashMap<>();
  private final Map<UUID, Integer> placeTasks = new ConcurrentHashMap<>();

  public PlaceCommand(
      FakePlayerPlugin plugin,
      FakePlayerManager manager,
      StorageStore storageStore,
      PathfindingService pathfinding) {
    this.plugin = plugin;
    this.manager = manager;
    this.storageStore = storageStore;
    this.pathfinding = pathfinding;
  }

  @Override
  public String getName() {
    return "place";
  }

  @Override
   public String getUsage() {
      return "<bot> [--once|--stop]  |  --stop";
   }

  @Override
  public String getDescription() {
    return "Bot continuously places blocks it is looking at, like /fpp mine but placing.";
  }

  @Override
  public String getPermission() {
    return Perm.PLACE;
  }

  @Override
  public boolean canUse(CommandSender sender) {
    return Perm.has(sender, Perm.PLACE);
  }

  @Override
  public boolean execute(CommandSender sender, String[] args) {
    if (args.length == 0) {
      sender.sendMessage(Lang.get("place-usage"));
      return true;
    }

    if ((args[0].equalsIgnoreCase("stop") || args[0].equalsIgnoreCase("--stop"))
        && args.length == 1) {
      stopAll();
      sender.sendMessage(Lang.get("place-stopped-all"));
      return true;
    }

    FakePlayer fp = manager.getByName(args[0]);
    if (fp == null) {
      sender.sendMessage(Lang.get("place-not-found", "name", args[0]));
      return true;
    }
    Player bot = fp.getPlayer();
    if (bot == null || !bot.isOnline()) {
      sender.sendMessage(Lang.get("place-bot-offline", "name", fp.getDisplayName()));
      return true;
    }

    if (args.length >= 2) {
      String action = args[1].toLowerCase(Locale.ROOT);

      if (!AREA_MODE_ENABLED) {
        java.util.Set<String> areaActions =
            java.util.Set.of(
                "--pos1", "pos1", "--pos2", "pos2", "--block", "block", "--clear", "clear",
                "start");
        if (areaActions.contains(action)) {
          sender.sendMessage(Lang.get("place-usage"));
          return true;
        }
      }

      switch (action) {
        case "stop", "--stop" -> {
          cleanupBot(fp.getUuid());
          sender.sendMessage(Lang.get("place-stopped", "name", fp.getDisplayName()));
          return true;
        }

        case "--pos1", "pos1" -> {
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
            posLoc = player.getLocation().getBlock().getLocation();
          }
          AreaSelection sel = selections.computeIfAbsent(fp.getUuid(), k -> new AreaSelection());
          sel.pos1 = posLoc;
          sender.sendMessage(
              Lang.get(
                  "place-pos1-set",
                  "name",
                  fp.getDisplayName(),
                  "x",
                  String.valueOf(posLoc.getBlockX()),
                  "y",
                  String.valueOf(posLoc.getBlockY()),
                  "z",
                  String.valueOf(posLoc.getBlockZ())));
          if (sel.isComplete())
            sender.sendMessage(
                Lang.get(
                    "place-selection-ready",
                    "name",
                    fp.getDisplayName(),
                    "count",
                    String.valueOf(sel.blockCount())));
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
            posLoc = player.getLocation().getBlock().getLocation();
          }
          AreaSelection sel = selections.computeIfAbsent(fp.getUuid(), k -> new AreaSelection());
          sel.pos2 = posLoc;
          sender.sendMessage(
              Lang.get(
                  "place-pos2-set",
                  "name",
                  fp.getDisplayName(),
                  "x",
                  String.valueOf(posLoc.getBlockX()),
                  "y",
                  String.valueOf(posLoc.getBlockY()),
                  "z",
                  String.valueOf(posLoc.getBlockZ())));
          if (sel.isComplete())
            sender.sendMessage(
                Lang.get(
                    "place-selection-ready",
                    "name",
                    fp.getDisplayName(),
                    "count",
                    String.valueOf(sel.blockCount())));
          return true;
        }

        case "--block", "block" -> {
          if (args.length < 3) {
            sender.sendMessage(Lang.get("place-block-usage"));
            return true;
          }
          String specStr = String.join("", Arrays.copyOfRange(args, 2, args.length));
          List<BlockEntry> spec = parseBlockSpec(specStr);
          if (spec.isEmpty()) {
            sender.sendMessage(Lang.get("place-block-invalid", "spec", specStr));
            return true;
          }
          blockSpecs.put(fp.getUuid(), spec);
          String summary =
              spec.stream()
                  .map(
                      e ->
                          formatMaterial(e.material())
                              + (spec.size() > 1 ? " " + e.weight() + "%" : ""))
                  .collect(Collectors.joining(", "));
          sender.sendMessage(
              Lang.get("place-block-set", "name", fp.getDisplayName(), "spec", summary));
          return true;
        }

        case "--clear", "clear" -> {
          selections.remove(fp.getUuid());
          blockSpecs.remove(fp.getUuid());
          cleanupBot(fp.getUuid());
          sender.sendMessage(Lang.get("place-cleared", "name", fp.getDisplayName()));
          return true;
        }

        case "status", "info" -> {
          sendStatus(sender, fp);
          return true;
        }

        case "start" -> {
          startAreaPlacing(sender, fp);
          return true;
        }
      }
    }

    boolean once =
        args.length >= 2
            && (args[1].equalsIgnoreCase("once") || args[1].equalsIgnoreCase("--once"));
    if (args.length == 1 || once) {
      cleanupBot(fp.getUuid());

      final Location dest;
      final float capturedYaw;
      final float capturedPitch;
      if (sender instanceof Player sp) {
        Location spLoc = sp.getLocation();
        dest =
            new Location(
                spLoc.getWorld(), spLoc.getBlockX() + 0.5, spLoc.getY(), spLoc.getBlockZ() + 0.5);
        capturedYaw = spLoc.getYaw();
        capturedPitch = spLoc.getPitch();
      } else {
        dest = bot.getLocation().clone();
        capturedYaw = dest.getYaw();
        capturedPitch = dest.getPitch();
      }

      double xzDist = PathfindingService.xzDist(bot.getLocation(), dest);
      if (xzDist <= 0.35) {
        lockAndStartPlacing(fp, once, dest, capturedYaw, capturedPitch);
        sender.sendMessage(
            once
                ? Lang.get("place-started-once", "name", fp.getDisplayName())
                : Lang.get("place-started", "name", fp.getDisplayName()));
      } else {

        Location lockOnArrival = dest.clone();
        lockOnArrival.setYaw(capturedYaw);
        lockOnArrival.setPitch(capturedPitch);
        startNavigation(
            fp,
            dest,
            lockOnArrival,
            () -> lockAndStartPlacing(fp, once, dest, capturedYaw, capturedPitch));
        sender.sendMessage(Lang.get("place-walking", "name", fp.getDisplayName()));
      }
      return true;
    }

    sender.sendMessage(Lang.get("place-usage"));
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
      for (FakePlayer fp : manager.getActivePlayers())
        if (fp.getName().toLowerCase(Locale.ROOT).startsWith(prefix)) out.add(fp.getName());
      return out;
    }

    if (args.length == 2
        && !args[0].equalsIgnoreCase("stop")
        && !args[0].equalsIgnoreCase("--stop")) {
      String prefix = args[1].toLowerCase(Locale.ROOT);
      List<String> out = new ArrayList<>();
      List<String> opts =
          AREA_MODE_ENABLED
              ? List.of(
                  "--pos1",
                  "--pos2",
                  "--block",
                  "--clear",
                  "--start",
                  "--status",
                  "--stop",
                  "--once",
                  "stop",
                  "once")
              : List.of("--once", "--stop", "once", "stop");
      for (String opt : opts) if (opt.startsWith(prefix)) out.add(opt);
      return out;
    }

    if (args.length >= 3 && AREA_MODE_ENABLED && args[1].equalsIgnoreCase("--block")) {

      String full = args[2].toUpperCase(Locale.ROOT);
      int lastComma = full.lastIndexOf(',');
      String prefix = lastComma >= 0 ? full.substring(lastComma + 1) : full;
      String already = lastComma >= 0 ? args[2].substring(0, lastComma + 1) : "";

      if (prefix.isEmpty() && lastComma < 0) return List.of();
      final String alreadyFinal = already;
      return Arrays.stream(Material.values())
          .filter(m -> !m.isAir() && m.isBlock() && m.isSolid() && m.name().startsWith(prefix))
          .map(m -> alreadyFinal + m.name())
          .sorted()
          .limit(20)
          .collect(Collectors.toList());
    }

    return List.of();
  }

  private void startNavigation(FakePlayer fp, Location dest, Runnable onArrive) {
    // Force placeBlocks=true so the bot can bridge gaps en-route to its target.
    me.bill.fakePlayerPlugin.fakeplayer.BotPathfinder.PathOptions baseOpts =
        me.bill.fakePlayerPlugin.fakeplayer.PathfindingService.resolvePathOptions(fp);
    me.bill.fakePlayerPlugin.fakeplayer.BotPathfinder.PathOptions opts =
        new me.bill.fakePlayerPlugin.fakeplayer.BotPathfinder.PathOptions(
            fp.isNavParkour(),
            fp.isNavBreakBlocks(),
            true,
            baseOpts.avoidWater(),
            baseOpts.avoidLava());
    pathfinding.navigate(
        fp,
        new PathfindingService.NavigationRequest(
            PathfindingService.Owner.PLACE,
            () -> dest,
            Config.pathfindingArrivalDistance(),
            0.0,
            Integer.MAX_VALUE,
            onArrive,
            null,
            null,
            null,
            opts));
  }

  private void lockAndStartPlacing(
      FakePlayer fp, boolean once, Location dest, float capturedYaw, float capturedPitch) {
    FppApiImpl.fireTaskEvent(fp, "place", me.bill.fakePlayerPlugin.api.event.FppBotTaskEvent.Action.START);
    UUID uuid = fp.getUuid();
    Player bot = fp.getPlayer();
    if (bot == null) return;

    Location lockLoc = dest.clone();
    lockLoc.setYaw(capturedYaw);
    lockLoc.setPitch(capturedPitch);
    FppScheduler.teleportAsync(bot, lockLoc);
    bot.setRotation(capturedYaw, capturedPitch);
    NmsPlayerSpawner.setHeadYaw(bot, capturedYaw);
    NmsPlayerSpawner.setMovementForward(bot, 0f);
    bot.setSprinting(false);

    manager.lockForAction(uuid, lockLoc);

    PlaceState state = new PlaceState();
    state.once = once;
    state.capturedYaw = capturedYaw;
    state.capturedPitch = capturedPitch;
    state.destination = lockLoc.clone();
    placeStates.put(uuid, state);

    int taskId =
        FppScheduler.runSyncRepeatingWithId(
            plugin,
            () -> {
              Player b = fp.getPlayer();
              if (b == null || !b.isOnline()) {
                stopPlacing(uuid);
                return;
              }
              tickPlacing(fp, state);
            },
            0L,
            1L);

    placingTasks.put(uuid, taskId);
  }

  private void tickPlacing(FakePlayer fp, PlaceState state) {
    Player bot = fp.getPlayer();
    if (bot == null || !bot.isOnline()) {
      stopPlacing(fp.getUuid());
      return;
    }

    if (state.freeze > 0) {
      state.freeze--;
      return;
    }

    bot.setRotation(state.capturedYaw, state.capturedPitch);
    NmsPlayerSpawner.setHeadYaw(bot, state.capturedYaw);
    Location updatedLock = bot.getLocation().clone();
    updatedLock.setYaw(state.capturedYaw);
    updatedLock.setPitch(state.capturedPitch);
    manager.lockForAction(fp.getUuid(), updatedLock);

    Material mat = findPlaceableMaterial(bot.getInventory());
    if (mat != null) equipMaterial(bot, mat);

    var hit = bot.rayTraceBlocks(5.0);
    if (hit != null && hit.getHitBlock() != null && hit.getHitBlockFace() != null) {
      Block hitBlock = hit.getHitBlock();
      BlockFace face = hit.getHitBlockFace();
      placeBlockNms(
          fp,
          bot,
          new BlockPos(hitBlock.getX(), hitBlock.getY(), hitBlock.getZ()),
          toNmsDirection(face));
    } else {

      if (state.once) {
        stopPlacing(fp.getUuid());
        return;
      }
      return;
    }

    if (state.once) {
      stopPlacing(fp.getUuid());
      return;
    }
    state.freeze = PLACE_COOLDOWN;
  }

  private void placeBlockNms(FakePlayer fp, Player bot, BlockPos faceBlockPos, Direction faceDir) {
    try {
      ServerPlayer nms = ((CraftPlayer) bot).getHandle();
      if (!fireBlockPlaceHook(fp, faceBlockPos)) return;
      Vec3 hitVec =
          new Vec3(
              faceBlockPos.getX() + 0.5 + faceDir.getStepX() * 0.5,
              faceBlockPos.getY() + 0.5 + faceDir.getStepY() * 0.5,
              faceBlockPos.getZ() + 0.5 + faceDir.getStepZ() * 0.5);
      BlockHitResult hit = new BlockHitResult(hitVec, faceDir, faceBlockPos, false);
      nms.resetLastActionTime();
      var result =
          nms.gameMode.useItemOn(
              nms,
              nms.level(),
              nms.getItemInHand(InteractionHand.MAIN_HAND),
              InteractionHand.MAIN_HAND,
              hit);

      if (result.consumesAction()) nms.swing(InteractionHand.MAIN_HAND);
    } catch (Throwable ignored) {
    }
  }

  private static Direction toNmsDirection(BlockFace face) {
    return switch (face) {
      case UP -> Direction.UP;
      case DOWN -> Direction.DOWN;
      case NORTH -> Direction.NORTH;
      case SOUTH -> Direction.SOUTH;
      case EAST -> Direction.EAST;
      case WEST -> Direction.WEST;
      default -> Direction.UP;
    };
  }

  private boolean fireBlockPlaceHook(FakePlayer fp, BlockPos pos) {
    if (fp == null || pos == null) return false;
    Player bot = fp.getPlayer();
    if (bot == null || bot.getWorld() == null) return false;
    var event =
        new FppBotBlockPlaceEvent(
            new FppBotImpl(fp), bot.getWorld().getBlockAt(pos.getX(), pos.getY(), pos.getZ()));
    Bukkit.getPluginManager().callEvent(event);
    return !event.isCancelled();
  }

  private PlacementInfo findBestPlacement(
      Player bot, @org.jetbrains.annotations.Nullable Location preferred) {
    Location eye = bot.getEyeLocation();
    World world = bot.getWorld();
    int ex = (int) Math.floor(eye.getX());
    int ey = (int) Math.floor(eye.getY());
    int ez = (int) Math.floor(eye.getZ());
    int reach = (int) Math.ceil(PLACE_REACH);
    double reachSq = PLACE_REACH * PLACE_REACH;

    int botX = bot.getLocation().getBlockX();
    int botY = bot.getLocation().getBlockY();
    int botZ = bot.getLocation().getBlockZ();

    int[][] offsets = {{0, -1, 0}, {1, 0, 0}, {-1, 0, 0}, {0, 0, 1}, {0, 0, -1}, {0, 1, 0}};
    Direction[] faces = {
      Direction.UP, Direction.WEST, Direction.EAST, Direction.NORTH, Direction.SOUTH, Direction.DOWN
    };

    PlacementInfo best = null;
    double bestScore = Double.MAX_VALUE;

    for (int dx = -reach; dx <= reach; dx++) {
      for (int dy = -reach; dy <= reach; dy++) {
        for (int dz = -reach; dz <= reach; dz++) {
          double distSq = dx * (double) dx + dy * (double) dy + dz * (double) dz;
          if (distSq > reachSq) continue;

          int bx = ex + dx, by = ey + dy, bz = ez + dz;

          if (bx == botX && bz == botZ && (by == botY || by == botY + 1)) continue;

          Block b = world.getBlockAt(bx, by, bz);
          Material bm = b.getType();

          if (!bm.isAir() && bm != Material.WATER && bm != Material.LAVA) continue;

          for (int i = 0; i < offsets.length; i++) {
            int nx = bx + offsets[i][0], ny = by + offsets[i][1], nz = bz + offsets[i][2];
            Block nb = world.getBlockAt(nx, ny, nz);
            if (!nb.getType().isSolid() || nb.getType().isAir()) continue;

            double prefDist =
                preferred != null
                    ? Math.abs(bx - preferred.getBlockX())
                        + Math.abs(by - preferred.getBlockY())
                        + Math.abs(bz - preferred.getBlockZ())
                    : 0;
            double score = prefDist * 100.0 + distSq;

            if (score < bestScore) {
              bestScore = score;
              Location faceCenter =
                  new Location(
                      world,
                      nx + 0.5 + faces[i].getStepX() * 0.5,
                      ny + 0.5 + faces[i].getStepY() * 0.5,
                      nz + 0.5 + faces[i].getStepZ() * 0.5);
              best =
                  new PlacementInfo(
                      new BlockPos(bx, by, bz), new BlockPos(nx, ny, nz), faces[i], faceCenter);
            }
            break;
          }
        }
      }
    }
    return best;
  }

  private Material findPlaceableMaterial(PlayerInventory inv) {
    for (int slot = 0; slot < 36; slot++) {
      ItemStack item = inv.getItem(slot);
      if (item == null || item.getType().isAir()) continue;
      if (item.getType().isBlock() && item.getType().isSolid() && !isLikelyTool(item))
        return item.getType();
    }
    return null;
  }

  public void stopPlacing(UUID botUuid) {
    FakePlayer fp = manager.getByUuid(botUuid);
    if (fp != null) {
      FppApiImpl.fireTaskEvent(fp, "place", me.bill.fakePlayerPlugin.api.event.FppBotTaskEvent.Action.STOP);
    }
    Integer taskId = placingTasks.remove(botUuid);
    if (taskId != null) FppScheduler.cancelTask(taskId);
    manager.unlockAction(botUuid);
    manager.unlockNavigation(botUuid);
    placeStates.remove(botUuid);
  }

  @org.jetbrains.annotations.Nullable
  public Location getActivePlaceLocation(UUID botUuid) {
    PlaceState state = placeStates.get(botUuid);
    return state != null ? state.destination : null;
  }

  public boolean isActivePlaceOnce(UUID botUuid) {
    PlaceState state = placeStates.get(botUuid);
    return state != null && state.once;
  }

  public boolean isPlacing(UUID botUuid) {
    return placeStates.containsKey(botUuid);
  }

  public void resumePlacing(FakePlayer fp) {
    UUID uuid = fp.getUuid();
    Location placeLoc = getActivePlaceLocation(uuid);
    boolean once = isActivePlaceOnce(uuid);
    if (placeLoc != null) {
      resumePlacing(fp, once, placeLoc);
    }
  }

  public void resumePlacing(FakePlayer fp, boolean once, Location loc) {
    if (fp == null || loc == null) return;
    Player bot = fp.getPlayer();
    if (bot == null || !bot.isOnline()) return;
    stopPlacing(fp.getUuid());
    float capturedYaw = loc.getYaw();
    float capturedPitch = loc.getPitch();
    double xzDist = PathfindingService.xzDist(bot.getLocation(), loc);
    if (xzDist <= Config.pathfindingArrivalDistance()) {
      lockAndStartPlacing(fp, once, loc, capturedYaw, capturedPitch);
    } else {
      Location lockOnArrival = loc.clone();
      startNavigation(
          fp,
          loc,
          lockOnArrival,
          () -> lockAndStartPlacing(fp, once, loc, capturedYaw, capturedPitch));
    }
  }

  private void sendStatus(CommandSender sender, FakePlayer fp) {
    AreaSelection sel = selections.get(fp.getUuid());
    List<BlockEntry> spec = blockSpecs.get(fp.getUuid());
    PlaceJob job = placeJobs.get(fp.getUuid());

    if (sel == null || !sel.isComplete()) {
      sender.sendMessage(Lang.get("place-status-no-selection", "name", fp.getDisplayName()));
    } else {
      sender.sendMessage(
          Lang.get(
              "place-status-selection",
              "name",
              fp.getDisplayName(),
              "x1",
              String.valueOf(sel.pos1.getBlockX()),
              "y1",
              String.valueOf(sel.pos1.getBlockY()),
              "z1",
              String.valueOf(sel.pos1.getBlockZ()),
              "x2",
              String.valueOf(sel.pos2.getBlockX()),
              "y2",
              String.valueOf(sel.pos2.getBlockY()),
              "z2",
              String.valueOf(sel.pos2.getBlockZ()),
              "count",
              String.valueOf(sel.blockCount())));
    }

    if (spec != null && !spec.isEmpty()) {
      String summary =
          spec.stream()
              .map(
                  e ->
                      formatMaterial(e.material())
                          + (spec.size() > 1 ? " " + e.weight() + "%" : ""))
              .collect(Collectors.joining(", "));
      sender.sendMessage(
          Lang.get("place-status-spec", "name", fp.getDisplayName(), "spec", summary));
    } else {
      sender.sendMessage(Lang.get("place-status-spec-auto", "name", fp.getDisplayName()));
    }

    if (job != null) {
      sender.sendMessage(
          Lang.get(
              "place-status-active",
              "name",
              fp.getDisplayName(),
              "placed",
              String.valueOf(job.blocksPlaced),
              "total",
              String.valueOf(job.totalFillable)));
    } else {
      sender.sendMessage(Lang.get("place-status-no-job", "name", fp.getDisplayName()));
    }
  }

  private void startAreaPlacing(CommandSender sender, FakePlayer fp) {
    AreaSelection sel = selections.get(fp.getUuid());
    if (sel == null || !sel.isComplete()) {
      sender.sendMessage(Lang.get("place-area-missing-selection", "name", fp.getDisplayName()));
      return;
    }
    if (!sel.sameWorld()) {
      sender.sendMessage(Lang.get("mine-area-world-mismatch"));
      return;
    }
    Player bot = fp.getPlayer();
    if (bot == null || !bot.isOnline()) {
      sender.sendMessage(Lang.get("place-bot-offline", "name", fp.getDisplayName()));
      return;
    }
    if (bot.getWorld() != Objects.requireNonNull(sel.pos1.getWorld())) {
      sender.sendMessage(
          Lang.get(
              "mine-area-bot-world",
              "name",
              fp.getDisplayName(),
              "world",
              sel.pos1.getWorld().getName()));
      return;
    }

    List<BlockEntry> spec = blockSpecs.get(fp.getUuid());
    if (spec == null || spec.isEmpty()) {
      spec = buildSpecFromInventoryAndStorage(fp, bot);
      if (spec.isEmpty()) {
        sender.sendMessage(Lang.get("place-no-blocks", "name", fp.getDisplayName()));
        return;
      }
    }

    World world = Objects.requireNonNull(sel.pos1.getWorld());
    int fillable = countFillableBlocks(world, sel);
    if (fillable == 0) {
      sender.sendMessage(Lang.get("place-area-already-filled", "name", fp.getDisplayName()));
      return;
    }
    if (!checkAndReportMissing(sender, fp, bot, spec, fillable)) return;

    cleanupBot(fp.getUuid());
    PlaceJob job = new PlaceJob(sel.copy(), spec, fillable, sender);
    placeJobs.put(fp.getUuid(), job);
    int taskId =
        FppScheduler.runSyncRepeatingWithId(
            plugin, () -> tickPlaceJob(fp.getUuid()), 0L, CONTROLLER_PERIOD);
    placeTasks.put(fp.getUuid(), taskId);
    sender.sendMessage(
        Lang.get(
            "place-area-started", "name", fp.getDisplayName(), "count", String.valueOf(fillable)));
  }

  public void applyWorldEditSelection(FakePlayer fp, Location pos1, Location pos2, CommandSender sender) {
    AreaSelection weAreaSel = selections.computeIfAbsent(fp.getUuid(), k -> new AreaSelection());
    weAreaSel.pos1 = pos1;
    weAreaSel.pos2 = pos2;
    startAreaPlacing(sender, fp);
  }

  private void tickPlaceJob(UUID botUuid) {
    PlaceJob job = placeJobs.get(botUuid);
    if (job == null) {
      stopPlaceJob(botUuid, false);
      return;
    }
    FakePlayer fp = manager.getByUuid(botUuid);
    if (fp == null) {
      stopPlaceJob(botUuid, false);
      return;
    }
    Player bot = fp.getPlayer();
    if (bot == null || !bot.isOnline()) {
      stopPlaceJob(botUuid, false);
      return;
    }

    if (pathfinding.isNavigating(botUuid) || job.fetchingFromStorage) return;

    if (job.phase == Phase.RECHECKING) {
      recheckBuild(fp, job, bot);
      return;
    }
    if (job.phase == Phase.CLEANING_SCAFFOLD) {
      cleanupScaffold(botUuid, fp, job, bot);
      return;
    }

    if (job.placeCooldown > 0) {
      job.placeCooldown--;
      return;
    }

    {
      Location botLoc = bot.getLocation();
      int bx = botLoc.getBlockX(), by = botLoc.getBlockY(), bz = botLoc.getBlockZ();
      if (job.selection.contains(bx, by, bz) || job.selection.contains(bx, by - 1, bz)) {
        startNavigation(
            fp, findOutsideNavDest(bot.getWorld(), job.selection, bx, by, bz), () -> {});
        return;
      }
    }

    World tickWorld = bot.getWorld();
    while (job.currentLayer <= job.selection.maxY()
        && isLayerExhausted(tickWorld, job, job.currentLayer)) {
      job.currentLayer++;
      job.skipped.clear();
      job.skipRetries = 0;
      job.scaffoldRetries = 0;
    }
    if (job.currentLayer > job.selection.maxY()) {

      job.phase = Phase.RECHECKING;
      return;
    }

    Material mat = pickAvailableMaterial(bot.getInventory(), job.spec);
    if (mat == null) {
      if (!storageStore.getStorages(fp.getName()).isEmpty()) {
        Map<Material, Integer> toFetch = computeToFetch(fp, job);
        if (toFetch.isEmpty() || !startStorageFetch(fp, job, toFetch)) {
          notifyOutOfMaterials(fp, job);
          stopPlaceJob(botUuid, false);
        }
      } else {
        notifyOutOfMaterials(fp, job);
        stopPlaceJob(botUuid, false);
      }
      return;
    }

    NmsPlayerSpawner.setMovementForward(bot, 0f);
    bot.setSprinting(false);
    if (tryPlaceReachable(fp, job) > 0) return;

    AreaBlock next = findNextAreaTarget(bot, job.selection, job);
    if (next == null) {

      if (!job.skipped.isEmpty() && job.skipRetries < SKIP_RETRY_LIMIT) {
        job.skipped.clear();
        job.skipRetries++;
        return;
      }

      if (job.scaffoldRetries < SCAFFOLD_MAX_RETRIES) {
        if (tryScaffoldStep(fp, job)) {
          job.scaffoldRetries++;
          return;
        }
      }

      advanceLayer(job);
      return;
    }

    job.currentTarget = next;
    Location dest = findNavigationDest(bot.getWorld(), job.selection, next);
    startNavigation(
        fp,
        dest,
        () -> {
          job.skipped.clear();
          job.skipRetries = 0;
        });
  }

  private int tryPlaceReachable(FakePlayer fp, PlaceJob job) {
    Player bot = fp.getPlayer();
    if (bot == null || !bot.isOnline()) return 0;

    Location botLoc = bot.getLocation();
    double eyeX = botLoc.getX();
    double eyeY = botLoc.getY() + 1.62;
    double eyeZ = botLoc.getZ();
    World world = bot.getWorld();
    AreaSelection sel = job.selection;
    double reachSq = PLACE_REACH * PLACE_REACH;

    int y = job.currentLayer;
    if (y < sel.minY() || y > sel.maxY()) return 0;

    for (int x = sel.minX(); x <= sel.maxX(); x++) {
      for (int z = sel.minZ(); z <= sel.maxZ(); z++) {
        AreaBlock ab = new AreaBlock(x, y, z);
        if (job.completed.contains(ab) || job.skipped.contains(ab)) continue;

        Block block = world.getBlockAt(x, y, z);
        if (!needsFilling(block)) {
          job.completed.add(ab);
          continue;
        }

        double dx = eyeX - (x + 0.5), dy = eyeY - (y + 0.5), dz = eyeZ - (z + 0.5);
        if (dx * dx + dy * dy + dz * dz > reachSq) continue;

        PlacementTarget pt = findPlacementTarget(world, ab);
        if (pt == null) {

          job.skipped.add(ab);
          continue;
        }

        Material mat = pickAvailableMaterial(bot.getInventory(), job.spec);
        if (mat == null) return 0;
        if (!equipMaterial(bot, mat)) continue;

        Location faceCenter =
            new Location(
                world,
                pt.faceBlockPos().getX() + 0.5 + pt.faceDir().getStepX() * 0.5,
                pt.faceBlockPos().getY() + 0.5 + pt.faceDir().getStepY() * 0.5,
                pt.faceBlockPos().getZ() + 0.5 + pt.faceDir().getStepZ() * 0.5);
        Location faceLoc = BotNavUtil.faceToward(botLoc, faceCenter);
        bot.setRotation(faceLoc.getYaw(), faceLoc.getPitch());
        NmsPlayerSpawner.setHeadYaw(bot, faceLoc.getYaw());

        placeBlockNms(fp, bot, pt.faceBlockPos(), pt.faceDir());

        Block after = world.getBlockAt(x, y, z);
        if (!needsFilling(after)) {
          job.blocksPlaced++;
          job.completed.add(ab);

          job.placeCooldown = PLACE_COOLDOWN;
          if (job.blocksPlaced % PROGRESS_INTERVAL == 0)
            notifyStarter(
                job,
                "place-progress",
                "name",
                fp.getDisplayName(),
                "placed",
                String.valueOf(job.blocksPlaced));
          return 1;
        } else {

          job.skipped.add(ab);
        }
      }
    }
    return 0;
  }

  private boolean startStorageFetch(FakePlayer fp, PlaceJob job, Map<Material, Integer> needed) {
    List<StorageStore.StoragePoint> storages = storageStore.getStorages(fp.getName());
    if (storages.isEmpty()) return false;
    Player bot = fp.getPlayer();
    if (bot == null) return false;
    for (int attempt = 0; attempt < storages.size(); attempt++) {
      int idx = (job.preferredStorageIndex + attempt) % storages.size();
      StorageStore.StoragePoint point = storages.get(idx);
      if (point.location().getWorld() != bot.getWorld()) continue;
      Block block = point.location().getBlock();
      if (!(block.getState() instanceof InventoryHolder holder)) continue;
      if (!storageHasAnyNeeded(holder.getInventory(), needed)) continue;
      Location standLoc =
          BotNavUtil.findStandLocation(
              bot.getWorld(), null, block.getX(), block.getY(), block.getZ());
      if (standLoc == null) continue;
      Location faceLoc = BotNavUtil.faceToward(standLoc, block.getLocation().add(0.5, 0.5, 0.5));
      final int targetIdx = idx;
      job.fetchingFromStorage = true;
      startNavigation(fp, faceLoc, () -> fetchFromStorageBlock(fp, job, targetIdx, point, needed));
      return true;
    }
    return false;
  }

  private void fetchFromStorageBlock(
      FakePlayer fp,
      PlaceJob job,
      int storageIndex,
      StorageStore.StoragePoint point,
      Map<Material, Integer> needed) {
    Player bot = fp.getPlayer();
    if (bot == null || !bot.isOnline()) {
      job.fetchingFromStorage = false;
      return;
    }
    Block block = point.location().getBlock();
    if (!(block.getState() instanceof InventoryHolder)) {
      job.fetchingFromStorage = false;
      job.preferredStorageIndex =
          (storageIndex + 1) % Math.max(1, storageStore.getStorages(fp.getName()).size());
      return;
    }
    Location standLoc =
        BotNavUtil.findStandLocation(
            bot.getWorld(), null, block.getX(), block.getY(), block.getZ());
    if (standLoc == null) {
      job.fetchingFromStorage = false;
      return;
    }
    Location faceLoc = BotNavUtil.faceToward(standLoc, block.getLocation().add(0.5, 0.5, 0.5));
    if (!BotNavUtil.isAtActionLocation(bot, faceLoc)) {

      startNavigation(
          fp, faceLoc, () -> fetchFromStorageBlock(fp, job, storageIndex, point, needed));
      return;
    }

    final int finalIdx = storageIndex;
    StorageInteractionHelper.interact(
        fp,
        faceLoc,
        block,
        plugin,
        manager,
        (holder, liveBot) -> {
          moveStorageToBot(holder.getInventory(), liveBot.getInventory(), needed);
          job.preferredStorageIndex = finalIdx;
        },
        () -> job.fetchingFromStorage = false);
  }

  private void startNavigation(
      FakePlayer fp,
      Location dest,
      @org.jetbrains.annotations.Nullable Location lockOnArrival,
      Runnable onArrive) {
    // Force placeBlocks=true so the bot can bridge gaps en-route to its target.
    me.bill.fakePlayerPlugin.fakeplayer.BotPathfinder.PathOptions baseOpts =
        me.bill.fakePlayerPlugin.fakeplayer.PathfindingService.resolvePathOptions(fp);
    me.bill.fakePlayerPlugin.fakeplayer.BotPathfinder.PathOptions opts =
        new me.bill.fakePlayerPlugin.fakeplayer.BotPathfinder.PathOptions(
            fp.isNavParkour(),
            fp.isNavBreakBlocks(),
            true,
            baseOpts.avoidWater(),
            baseOpts.avoidLava());
    pathfinding.navigate(
        fp,
        new PathfindingService.NavigationRequest(
            PathfindingService.Owner.PLACE,
            () -> dest,
            0.35,
            0.0,
            Integer.MAX_VALUE,
            onArrive,
            null,
            null,
            lockOnArrival,
            opts));
  }

  private Location findNavigationDest(World world, AreaSelection sel, AreaBlock target) {
    return findOutsideNavDest(world, sel, target.x(), target.y(), target.z());
  }

  private Location findOutsideNavDest(World world, AreaSelection sel, int tx, int ty, int tz) {
    for (int r = 1; r <= 8; r++) {
      for (int dx = -r; dx <= r; dx++) {
        for (int dz = -r; dz <= r; dz++) {
          if (Math.abs(dx) < r && Math.abs(dz) < r) continue;
          int cx = tx + dx, cz = tz + dz;
          if (sel != null && sel.contains(cx, ty, cz)) continue;
          for (int dy : new int[] {0, -1, 1}) {
            if (BotPathfinder.walkable(world, cx, ty + dy, cz))
              return new Location(world, cx + 0.5, ty + dy, cz + 0.5);
          }
        }
      }
    }

    if (sel != null) {
      double dMinX = tx - sel.minX(), dMaxX = sel.maxX() - tx;
      double dMinZ = tz - sel.minZ(), dMaxZ = sel.maxZ() - tz;
      double best = Math.min(Math.min(dMinX, dMaxX), Math.min(dMinZ, dMaxZ));
      int ex, ez;
      if (best == dMinX) {
        ex = sel.minX() - 1;
        ez = tz;
      } else if (best == dMaxX) {
        ex = sel.maxX() + 1;
        ez = tz;
      } else if (best == dMinZ) {
        ex = tx;
        ez = sel.minZ() - 1;
      } else {
        ex = tx;
        ez = sel.maxZ() + 1;
      }
      return new Location(world, ex + 0.5, ty, ez + 0.5);
    }
    return new Location(world, tx + 1.5, ty, tz + 0.5);
  }

  private boolean needsFilling(Block block) {
    Material m = block.getType();
    return m.isAir()
        || m == Material.WATER
        || m == Material.LAVA
        || m == Material.CAVE_AIR
        || m == Material.VOID_AIR
        || !m.isSolid();
  }

  private void recheckBuild(FakePlayer fp, PlaceJob job, Player bot) {
    World world = bot.getWorld();
    AreaSelection sel = job.selection;
    int missedLayer = -1;
    outer:
    for (int y = sel.minY(); y <= sel.maxY(); y++) {
      for (int x = sel.minX(); x <= sel.maxX(); x++) {
        for (int z = sel.minZ(); z <= sel.maxZ(); z++) {
          if (needsFilling(world.getBlockAt(x, y, z))) {
            missedLayer = y;
            break outer;
          }
        }
      }
    }
    if (missedLayer >= 0) {

      job.phase = Phase.FILLING;
      job.currentLayer = missedLayer;
      job.skipped.clear();
      job.skipRetries = 0;
      job.scaffoldRetries = 0;
      notifyStarter(job, "place-recheck-filling", "name", fp.getDisplayName());
    } else {

      notifyStarter(job, "place-recheck-clean", "name", fp.getDisplayName());
      job.phase = Phase.CLEANING_SCAFFOLD;
    }
  }

  private void cleanupScaffold(UUID botUuid, FakePlayer fp, PlaceJob job, Player bot) {
    World world = bot.getWorld();
    int cleaned = 0;
    for (AreaBlock ab : new HashSet<>(job.scaffoldBlocks)) {
      Block block = world.getBlockAt(ab.x(), ab.y(), ab.z());
      if (!block.getType().isAir()
          && block.getType().isSolid()
          && !job.selection.contains(ab.x(), ab.y(), ab.z())) {
        ItemStack drop = new ItemStack(block.getType(), 1);
        block.setType(Material.AIR);
        Map<Integer, ItemStack> leftover = bot.getInventory().addItem(drop);
        if (!leftover.isEmpty())
          world.dropItemNaturally(
              block.getLocation().add(0.5, 0.5, 0.5), leftover.values().iterator().next());
        cleaned++;
      }
    }
    job.scaffoldBlocks.clear();
    if (cleaned > 0)
      notifyStarter(
          job,
          "place-scaffold-cleaned",
          "name",
          fp.getDisplayName(),
          "count",
          String.valueOf(cleaned));
    notifyStarter(
        job,
        "place-area-finished",
        "name",
        fp.getDisplayName(),
        "count",
        String.valueOf(job.blocksPlaced));
    stopPlaceJob(botUuid, false);
  }

  private AreaBlock findNextAreaTarget(Player bot, AreaSelection sel, PlaceJob job) {
    World world = bot.getWorld();
    Location botLoc = bot.getLocation();
    int y = job.currentLayer;
    if (y < sel.minY() || y > sel.maxY()) return null;

    AreaBlock best = null;
    double bestDist = Double.MAX_VALUE;
    for (int x = sel.minX(); x <= sel.maxX(); x++) {
      for (int z = sel.minZ(); z <= sel.maxZ(); z++) {
        AreaBlock ab = new AreaBlock(x, y, z);
        if (job.completed.contains(ab) || job.skipped.contains(ab)) continue;
        Block block = world.getBlockAt(x, y, z);
        if (!needsFilling(block)) {
          job.completed.add(ab);
          continue;
        }
        double dist =
            (botLoc.getX() - (x + 0.5)) * (botLoc.getX() - (x + 0.5))
                + (botLoc.getZ() - (z + 0.5)) * (botLoc.getZ() - (z + 0.5));
        if (dist < bestDist) {
          bestDist = dist;
          best = ab;
        }
      }
    }
    return best;
  }

  private PlacementTarget findPlacementTarget(World world, AreaBlock target) {
    int x = target.x(), y = target.y(), z = target.z();
    int[][] offsets = {{0, -1, 0}, {1, 0, 0}, {-1, 0, 0}, {0, 0, 1}, {0, 0, -1}, {0, 1, 0}};
    Direction[] faces = {
      Direction.UP, Direction.WEST, Direction.EAST, Direction.NORTH, Direction.SOUTH, Direction.DOWN
    };
    for (int i = 0; i < offsets.length; i++) {
      int nx = x + offsets[i][0], ny = y + offsets[i][1], nz = z + offsets[i][2];
      Block nb = world.getBlockAt(nx, ny, nz);
      if (!nb.getType().isSolid() || nb.getType().isAir()) continue;
      return new PlacementTarget(new BlockPos(nx, ny, nz), faces[i]);
    }
    return null;
  }

  private void moveStorageToBot(
      Inventory storage, PlayerInventory bot, Map<Material, Integer> needed) {
    Map<Material, Integer> remaining = new HashMap<>(needed);
    for (int slot = 0; slot < storage.getSize(); slot++) {
      ItemStack item = storage.getItem(slot);
      if (item == null || item.getType().isAir()) continue;
      Integer stillNeeded = remaining.get(item.getType());
      if (stillNeeded == null || stillNeeded <= 0) continue;
      int take = Math.min(stillNeeded, item.getAmount());
      ItemStack toAdd = item.clone();
      toAdd.setAmount(take);
      Map<Integer, ItemStack> leftovers = bot.addItem(toAdd);
      int added =
          take - (leftovers.isEmpty() ? 0 : leftovers.values().iterator().next().getAmount());
      if (added > 0) {
        item.setAmount(item.getAmount() - added);
        if (item.getAmount() == 0) storage.setItem(slot, null);
        remaining.merge(item.getType(), -added, Integer::sum);
      }
    }
  }

  private boolean equipMaterial(Player bot, Material mat) {
    PlayerInventory inv = bot.getInventory();
    int held = inv.getHeldItemSlot();
    ItemStack cur = inv.getItem(held);
    if (cur != null && cur.getType() == mat && cur.getAmount() > 0) return true;
    for (int slot = 0; slot <= 8; slot++) {
      ItemStack item = inv.getItem(slot);
      if (item != null && item.getType() == mat && item.getAmount() > 0) {
        inv.setHeldItemSlot(slot);
        return true;
      }
    }
    for (int slot = 9; slot < 36; slot++) {
      ItemStack item = inv.getItem(slot);
      if (item != null && item.getType() == mat && item.getAmount() > 0) {
        ItemStack heldItem = inv.getItem(held);
        inv.setItem(held, item.clone());
        inv.setItem(slot, heldItem);
        return true;
      }
    }
    return false;
  }

  private Material pickAvailableMaterial(PlayerInventory inv, List<BlockEntry> spec) {
    List<BlockEntry> available =
        spec.stream().filter(e -> countInInventory(inv, e.material()) > 0).toList();
    if (available.isEmpty()) return null;
    if (available.size() == 1) return available.getFirst().material();
    int total = available.stream().mapToInt(BlockEntry::weight).sum();
    int r = (int) (Math.random() * total), cumulative = 0;
    for (BlockEntry e : available) {
      cumulative += e.weight();
      if (r < cumulative) return e.material();
    }
    return available.getLast().material();
  }

  private int countInInventory(PlayerInventory inv, Material mat) {
    int count = 0;
    for (int slot = 0; slot < 36; slot++) {
      ItemStack item = inv.getItem(slot);
      if (item != null && item.getType() == mat) count += item.getAmount();
    }
    return count;
  }

  private int countAvailable(FakePlayer fp, Player bot, Material mat) {
    int count = countInInventory(bot.getInventory(), mat);
    for (StorageStore.StoragePoint pt : storageStore.getStorages(fp.getName())) {
      Block b = pt.location().getBlock();
      if (b.getState() instanceof InventoryHolder holder)
        for (ItemStack item : holder.getInventory().getContents())
          if (item != null && item.getType() == mat) count += item.getAmount();
    }
    return count;
  }

  private int countFillableBlocks(World world, AreaSelection sel) {
    int count = 0;
    for (int y = sel.minY(); y <= sel.maxY(); y++)
      for (int x = sel.minX(); x <= sel.maxX(); x++)
        for (int z = sel.minZ(); z <= sel.maxZ(); z++)
          if (needsFilling(world.getBlockAt(x, y, z))) count++;
    return count;
  }

  private Map<Material, Integer> computeNeededFromSpec(List<BlockEntry> spec, int total) {
    int totalWeight = spec.stream().mapToInt(BlockEntry::weight).sum();
    Map<Material, Integer> result = new LinkedHashMap<>();
    int assigned = 0;
    for (int i = 0; i < spec.size(); i++) {
      BlockEntry e = spec.get(i);
      int n =
          (i == spec.size() - 1)
              ? (total - assigned)
              : (int) Math.round((double) e.weight() / totalWeight * total);
      result.merge(e.material(), n, Integer::sum);
      assigned += n;
    }
    return result;
  }

  private Map<Material, Integer> computeToFetch(FakePlayer fp, PlaceJob job) {
    int remaining =
        Math.max(1, job.selection.blockCount() - job.completed.size() - job.skipped.size());
    int fetchAmt = Math.clamp(remaining, 1, 64);
    Map<Material, Integer> toFetch = new LinkedHashMap<>();
    for (BlockEntry e : job.spec) {
      int inStorage = 0;
      for (StorageStore.StoragePoint pt : storageStore.getStorages(fp.getName())) {
        Block b = pt.location().getBlock();
        if (b.getState() instanceof InventoryHolder holder)
          for (ItemStack item : holder.getInventory().getContents())
            if (item != null && item.getType() == e.material()) inStorage += item.getAmount();
      }
      if (inStorage > 0) toFetch.put(e.material(), Math.min(fetchAmt, inStorage));
    }
    return toFetch;
  }

  private boolean checkAndReportMissing(
      CommandSender sender, FakePlayer fp, Player bot, List<BlockEntry> spec, int fillable) {
    Map<Material, Integer> needed = computeNeededFromSpec(spec, fillable);
    Map<Material, Integer> missing = new LinkedHashMap<>();
    for (Map.Entry<Material, Integer> e : needed.entrySet()) {
      int deficit = e.getValue() - countAvailable(fp, bot, e.getKey());
      if (deficit > 0) missing.put(e.getKey(), deficit);
    }
    if (missing.isEmpty()) return true;
    sender.sendMessage(
        Lang.get(
            "place-missing-header",
            "name",
            fp.getDisplayName(),
            "count",
            String.valueOf(fillable)));
    for (Map.Entry<Material, Integer> e : missing.entrySet())
      sender.sendMessage(
          Lang.get(
              "place-missing-entry",
              "material",
              formatMaterial(e.getKey()),
              "amount",
              String.valueOf(e.getValue())));
    return false;
  }

  private List<BlockEntry> buildSpecFromInventoryAndStorage(FakePlayer fp, Player bot) {
    Map<Material, Integer> counts = new LinkedHashMap<>();
    PlayerInventory inv = bot.getInventory();
    for (int slot = 0; slot < 36; slot++) {
      ItemStack item = inv.getItem(slot);
      if (item == null || item.getType().isAir() || !item.getType().isBlock() || isLikelyTool(item))
        continue;
      counts.merge(item.getType(), item.getAmount(), Integer::sum);
    }
    for (StorageStore.StoragePoint point : storageStore.getStorages(fp.getName())) {
      Block block = point.location().getBlock();
      if (!(block.getState() instanceof InventoryHolder holder)) continue;
      for (ItemStack item : holder.getInventory().getContents()) {
        if (item == null
            || item.getType().isAir()
            || !item.getType().isBlock()
            || isLikelyTool(item)) continue;
        counts.merge(item.getType(), item.getAmount(), Integer::sum);
      }
    }
    return counts.entrySet().stream()
        .filter(e -> e.getValue() > 0)
        .map(e -> new BlockEntry(e.getKey(), e.getValue()))
        .collect(Collectors.toList());
  }

  private List<BlockEntry> parseBlockSpec(String spec) {
    List<BlockEntry> result = new ArrayList<>();
    String[] parts = spec.split(",");
    Pattern p = Pattern.compile("^([A-Z_]+?)(\\d+)?%?$");
    int defaultWeight = Math.max(1, 100 / parts.length);
    for (String part : parts) {
      part = part.trim().toUpperCase(Locale.ROOT).replaceAll("\\s+", "");
      Matcher m = p.matcher(part);
      if (!m.matches()) continue;
      String matName = m.group(1);
      int weight = m.group(2) != null ? Math.max(1, Integer.parseInt(m.group(2))) : defaultWeight;
      Material mat = Material.matchMaterial(matName);
      if (mat == null || mat.isAir() || !mat.isBlock()) continue;
      result.add(new BlockEntry(mat, weight));
    }
    return result;
  }

  private boolean isLikelyTool(ItemStack item) {
    String n = item.getType().name();
    return n.endsWith("_PICKAXE")
        || n.endsWith("_AXE")
        || n.endsWith("_SHOVEL")
        || n.endsWith("_HOE")
        || n.endsWith("_SWORD")
        || item.getType() == Material.SHEARS;
  }

  private boolean storageHasAnyNeeded(Inventory inv, Map<Material, Integer> needed) {
    for (ItemStack item : inv.getContents())
      if (item != null && !item.getType().isAir() && needed.containsKey(item.getType()))
        return true;
    return false;
  }

  private String formatMaterial(Material mat) {
    String raw = mat.name().toLowerCase(Locale.ROOT).replace('_', ' ');
    String[] words = raw.split(" ");
    StringBuilder sb = new StringBuilder();
    for (String w : words) {
      if (!sb.isEmpty()) sb.append(' ');
      sb.append(Character.toUpperCase(w.charAt(0))).append(w.substring(1));
    }
    return sb.toString();
  }

  private boolean isLayerExhausted(World world, PlaceJob job, int y) {
    AreaSelection sel = job.selection;
    if (y < sel.minY() || y > sel.maxY()) return true;
    for (int x = sel.minX(); x <= sel.maxX(); x++) {
      for (int z = sel.minZ(); z <= sel.maxZ(); z++) {
        AreaBlock ab = new AreaBlock(x, y, z);
        if (job.completed.contains(ab)) continue;
        Block block = world.getBlockAt(x, y, z);
        if (!needsFilling(block)) {
          job.completed.add(ab);
          continue;
        }
        return false;
      }
    }
    return true;
  }

  private void advanceLayer(PlaceJob job) {
    if (job.currentLayer < job.selection.maxY()) {
      job.currentLayer++;
      job.skipped.clear();
      job.skipRetries = 0;
      job.scaffoldRetries = 0;
    } else {

      job.phase = Phase.RECHECKING;
    }
  }

  private boolean tryScaffoldStep(FakePlayer fp, PlaceJob job) {
    Player bot = fp.getPlayer();
    if (bot == null) return false;
    World world = bot.getWorld();
    AreaSelection sel = job.selection;

    Material mat = getScaffoldMaterial(bot, job.spec);
    if (mat == null) return false;

    int targetY = job.currentLayer;
    Location botLoc = bot.getLocation();
    double eyeY = botLoc.getY() + 1.62;
    if (eyeY + PLACE_REACH >= targetY + 0.5) return false;

    int bestPx = Integer.MIN_VALUE, bestPz = Integer.MIN_VALUE;
    int bestBaseY = -1;
    double bestScore = Double.MAX_VALUE;

    for (int px = sel.minX() - 2; px <= sel.maxX() + 2; px++) {
      for (int pz = sel.minZ() - 2; pz <= sel.maxZ() + 2; pz++) {
        if (sel.contains(px, targetY, pz)) continue;
        int baseY = -1;
        for (int cy = Math.min(targetY - 1, botLoc.getBlockY() + 4);
            cy >= botLoc.getBlockY() - 4;
            cy--) {
          if (BotPathfinder.walkable(world, px, cy, pz)) {
            baseY = cy;
            break;
          }
        }
        if (baseY < 0) continue;
        int pillarHeight = targetY - baseY - 1;
        if (pillarHeight <= 0) continue;
        int clearCount = 0;
        for (int cy = baseY + 1; cy < targetY; cy++)
          if (world.getBlockAt(px, cy, pz).getType().isAir()) clearCount++;
        if (clearCount < pillarHeight / 2) continue;
        double dist =
            (botLoc.getX() - px) * (botLoc.getX() - px)
                + (botLoc.getZ() - pz) * (botLoc.getZ() - pz);
        boolean onEdge =
            (px == sel.minX() - 1
                || px == sel.maxX() + 1
                || pz == sel.minZ() - 1
                || pz == sel.maxZ() + 1);
        double score = dist - (onEdge ? 4.0 : 0.0);
        if (score < bestScore) {
          bestScore = score;
          bestPx = px;
          bestPz = pz;
          bestBaseY = baseY;
        }
      }
    }
    if (bestPx == Integer.MIN_VALUE) return false;

    final int fpx = bestPx, fpz = bestPz, fbaseY = bestBaseY;
    final Material fmat = mat;
    Location baseNav = new Location(world, bestPx + 0.5, bestBaseY, bestPz + 0.5);
    startNavigation(
        fp, baseNav, () -> buildScaffoldPillar(fp, job, fpx, fpz, fbaseY, targetY, fmat));
    return true;
  }

  private void buildScaffoldPillar(
      FakePlayer fp, PlaceJob job, int px, int pz, int baseY, int targetY, Material mat) {
    Player bot = fp.getPlayer();
    if (bot == null || !bot.isOnline()) return;
    World world = bot.getWorld();
    double eyeY = bot.getLocation().getY() + 1.62;

    for (int y = baseY; y < targetY - 1; y++) {
      Block support = world.getBlockAt(px, y, pz);
      Block space = world.getBlockAt(px, y + 1, pz);
      if (!support.getType().isSolid() || !space.getType().isAir()) continue;
      if (Math.abs(eyeY - (y + 1.5)) > PLACE_REACH) break;
      if (!equipMaterial(bot, mat)) break;
      Location faceCenter = new Location(world, px + 0.5, y + 0.5, pz + 0.5);
      Location faceLoc = BotNavUtil.faceToward(bot.getLocation(), faceCenter);
      bot.setRotation(faceLoc.getYaw(), faceLoc.getPitch());
      NmsPlayerSpawner.setHeadYaw(bot, faceLoc.getYaw());
      placeBlockNms(fp, bot, new BlockPos(px, y, pz), Direction.UP);
      Block placed = world.getBlockAt(px, y + 1, pz);
      if (!placed.getType().isAir()) job.scaffoldBlocks.add(new AreaBlock(px, y + 1, pz));
    }

    int topSolid = baseY;
    for (int y = targetY - 1; y > baseY; y--) {
      if (world.getBlockAt(px, y, pz).getType().isSolid()) {
        topSolid = y;
        break;
      }
    }
    Location topNav = new Location(world, px + 0.5, topSolid + 1, pz + 0.5);
    startNavigation(
        fp,
        topNav,
        () -> {
          PlaceJob liveJob = placeJobs.get(fp.getUuid());
          if (liveJob != null) {
            liveJob.skipped.clear();
            liveJob.skipRetries = 0;
          }
        });
  }

  private Material getScaffoldMaterial(Player bot, List<BlockEntry> spec) {
    Set<Material> specMats =
        spec.stream().map(BlockEntry::material).collect(java.util.stream.Collectors.toSet());
    PlayerInventory inv = bot.getInventory();
    for (Material preferred : SCAFFOLD_PREF) {
      if (!specMats.contains(preferred) && countInInventory(inv, preferred) > 0) return preferred;
    }
    for (int slot = 0; slot < 36; slot++) {
      ItemStack item = inv.getItem(slot);
      if (item == null || item.getType().isAir()) continue;
      Material m = item.getType();
      if (m.isBlock() && m.isSolid() && !specMats.contains(m)) return m;
    }
    return null;
  }

  public void cleanupBot(UUID botUuid) {
    pathfinding.cancel(botUuid);
    stopPlacing(botUuid);
    stopPlaceJob(botUuid, false);
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

  public void stopAll() {
    pathfinding.cancelAll(PathfindingService.Owner.PLACE);
    new HashSet<>(placingTasks.keySet()).forEach(this::stopPlacing);
    new HashSet<>(placeJobs.keySet()).forEach(this::cleanupBot);
  }

  private void stopPlaceJob(UUID botUuid, boolean notify) {
    Integer taskId = placeTasks.remove(botUuid);
    if (taskId != null) FppScheduler.cancelTask(taskId);
    PlaceJob job = placeJobs.remove(botUuid);
    manager.unlockAction(botUuid);
    manager.unlockNavigation(botUuid);
    if (notify && job != null) {
      FakePlayer fp = manager.getByUuid(botUuid);
      notifyStarter(
          job, "place-area-stopped", "name", fp != null ? fp.getDisplayName() : botUuid.toString());
    }
  }

  private void notifyOutOfMaterials(FakePlayer fp, PlaceJob job) {
    notifyStarter(job, "place-out-of-materials", "name", fp.getDisplayName());
  }

  private void notifyStarter(PlaceJob job, String key, String... args) {
    if (job.starterUuid != null) {
      Player p = Bukkit.getPlayer(job.starterUuid);
      if (p != null && p.isOnline()) {
        p.sendMessage(Lang.get(key, args));
        return;
      }
    }
    if (job.consoleStarted) plugin.getLogger().info(Lang.raw(key, args));
  }

  private static final class AreaSelection {
    Location pos1, pos2;

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
      AreaSelection c = new AreaSelection();
      c.pos1 = pos1.clone();
      c.pos2 = pos2.clone();
      return c;
    }
  }

  private static final class PlaceJob {
    final AreaSelection selection;
    final List<BlockEntry> spec;
    final int totalFillable;
    final UUID starterUuid;
    final boolean consoleStarted;
    final Set<AreaBlock> completed = new HashSet<>();

    final Set<AreaBlock> skipped = new HashSet<>();

    final Set<AreaBlock> scaffoldBlocks = new HashSet<>();
    AreaBlock currentTarget;
    int blocksPlaced;
    int preferredStorageIndex;
    boolean fetchingFromStorage;
    int skipRetries;

    int currentLayer;

    int scaffoldRetries;

    int placeCooldown;

    Phase phase = Phase.FILLING;

    PlaceJob(
        AreaSelection selection, List<BlockEntry> spec, int totalFillable, CommandSender sender) {
      this.selection = selection;
      this.spec = new ArrayList<>(spec);
      this.totalFillable = totalFillable;
      this.starterUuid = sender instanceof Player p ? p.getUniqueId() : null;
      this.consoleStarted = !(sender instanceof Player);
      this.currentLayer = selection.minY();
    }
  }

  private record BlockEntry(Material material, int weight) {}

  private record AreaBlock(int x, int y, int z) {}

  private record PlacementInfo(
      BlockPos targetPos, BlockPos faceBlockPos, Direction faceDir, Location faceCenter) {}

  private record PlacementTarget(BlockPos faceBlockPos, Direction faceDir) {}

  private static final class PlaceState {
    boolean once;
    int freeze;

    float capturedYaw;

    float capturedPitch;

    Location destination;
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
