package me.bill.fakePlayerPlugin.command;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import me.bill.fakePlayerPlugin.FakePlayerPlugin;
import me.bill.fakePlayerPlugin.config.Config;
import me.bill.fakePlayerPlugin.fakeplayer.FakePlayer;
import me.bill.fakePlayerPlugin.fakeplayer.FakePlayerManager;
import me.bill.fakePlayerPlugin.fakeplayer.NmsPlayerSpawner;
import me.bill.fakePlayerPlugin.fakeplayer.PathfindingService;
import me.bill.fakePlayerPlugin.lang.Lang;
import me.bill.fakePlayerPlugin.permission.Perm;
import me.bill.fakePlayerPlugin.util.FppScheduler;
import net.minecraft.core.Direction;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.decoration.ItemFrame;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.EntityHitResult;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;
import org.bukkit.Location;
import org.bukkit.command.CommandSender;
import org.bukkit.craftbukkit.entity.CraftPlayer;
import org.bukkit.entity.Player;

public final class UseCommand implements FppCommand {

  private final FakePlayerPlugin plugin;
  private final FakePlayerManager manager;
  private final PathfindingService pathfinding;

  private final Map<UUID, Integer> useTasks = new ConcurrentHashMap<>();

  private final Map<UUID, Location> activeUseLocations = new ConcurrentHashMap<>();

  private final Map<UUID, Boolean> activeUseOnceFlags = new ConcurrentHashMap<>();

  public UseCommand(
      FakePlayerPlugin plugin, FakePlayerManager manager, PathfindingService pathfinding) {
    this.plugin = plugin;
    this.manager = manager;
    this.pathfinding = pathfinding;
  }

  @Override
  public String getName() {
    return "use";
  }

  @Override
  public String getUsage() {
    return "<bot> [--once|--stop]  |  --stop";
  }

  @Override
  public String getDescription() {
    return "Walk a bot to your position then right-click what it's looking at.";
  }

  @Override
  public String getPermission() {
    return Perm.USE_CMD;
  }

  @Override
  public boolean canUse(CommandSender sender) {
    return Perm.has(sender, Perm.USE_CMD);
  }

  @Override
  public boolean execute(CommandSender sender, String[] args) {
    if (args.length == 0) {
      sender.sendMessage(Lang.get("use-usage"));
      return true;
    }

    if (args[0].equalsIgnoreCase("stop") || args[0].equalsIgnoreCase("--stop")) {
      if (args.length == 1) {
        stopAll();
        sender.sendMessage(Lang.get("use-stopped-all"));
        return true;
      }
    }

    String botName = args[0];
    FakePlayer fp = manager.getByName(botName);
    if (fp == null) {
      sender.sendMessage(Lang.get("use-not-found", "name", botName));
      return true;
    }

    Player bot = fp.getPlayer();
    if (bot == null || !bot.isOnline()) {
      sender.sendMessage(Lang.get("use-bot-offline", "name", fp.getDisplayName()));
      return true;
    }

    if (args.length >= 2) {
      String action = args[1].toLowerCase();
      if (action.equals("stop") || action.equals("--stop")) {
        cancelAll(fp.getUuid());
        sender.sendMessage(Lang.get("use-stopped", "name", fp.getDisplayName()));
        return true;
      }
    }

    boolean once =
        args.length >= 2
            && (args[1].equalsIgnoreCase("once") || args[1].equalsIgnoreCase("--once"));

    Location dest =
        (sender instanceof Player sp) ? sp.getLocation().clone() : bot.getLocation().clone();

    cancelAll(fp.getUuid());

    double xzDist = PathfindingService.xzDist(bot.getLocation(), dest);
    if (xzDist <= Config.pathfindingArrivalDistance()) {

      lockAndStartUsing(fp, once, dest);
      sender.sendMessage(
          once
              ? Lang.get("use-started-once", "name", fp.getDisplayName())
              : Lang.get("use-started", "name", fp.getDisplayName()));
    } else {

      startNavigation(fp, once, dest);
      sender.sendMessage(Lang.get("use-walking", "name", fp.getDisplayName()));
    }
    return true;
  }

  @Override
  public List<String> tabComplete(CommandSender sender, String[] args) {
    if (!canUse(sender)) return List.of();

    if (args.length == 1) {
      String prefix = args[0].toLowerCase();
      List<String> out = new ArrayList<>();
      if ("--stop".startsWith(prefix)) out.add("--stop");
      if ("stop".startsWith(prefix)) out.add("stop");
      for (FakePlayer fp : manager.getActivePlayers())
        if (fp.getName().toLowerCase().startsWith(prefix)) out.add(fp.getName());
      return out;
    }

    if (args.length == 2
        && !args[0].equalsIgnoreCase("stop")
        && !args[0].equalsIgnoreCase("--stop")) {
      String prefix = args[1].toLowerCase();
      List<String> out = new ArrayList<>();
      if ("--once".startsWith(prefix)) out.add("--once");
      if ("--stop".startsWith(prefix)) out.add("--stop");
      if ("once".startsWith(prefix)) out.add("once");
      if ("stop".startsWith(prefix)) out.add("stop");
      return out;
    }

    return List.of();
  }

  private void startNavigation(FakePlayer fp, boolean once, Location dest) {
    pathfinding.navigate(
        fp,
        new PathfindingService.NavigationRequest(
            PathfindingService.Owner.USE,
            () -> dest,
            Config.pathfindingArrivalDistance(),
            0.0,
            Integer.MAX_VALUE,
            () -> lockAndStartUsing(fp, once, dest),
            null,
            null));
  }

  private void lockAndStartUsing(FakePlayer fp, boolean once, Location lockLoc) {
    UUID uuid = fp.getUuid();
    Player bot = fp.getPlayer();
    if (bot == null) return;

    FppScheduler.teleportAsync(bot, lockLoc);

    manager.lockForAction(uuid, lockLoc);

    activeUseLocations.put(uuid, lockLoc.clone());
    activeUseOnceFlags.put(uuid, once);

    final int[] freeze = {0};

    int taskId =
        FppScheduler.runSyncRepeatingWithId(
            plugin,
            () -> {
                  Player b = fp.getPlayer();
                  if (b == null || !b.isOnline()) {
                    stopUsing(uuid);
                    return;
                  }

                  if (freeze[0] > 0) {
                    freeze[0]--;
                    return;
                  }

                  ServerPlayer nms = ((CraftPlayer) b).getHandle();

                  if (nms.isUsingItem()) {
                    if (once) stopUsing(uuid);
                    return;
                  }

                  HitResult hit = rayTrace(nms);
                  if (hit == null || hit.getType() == HitResult.Type.MISS) return;

                  boolean acted = false;

                  for (InteractionHand hand : InteractionHand.values()) {

                    if (hit.getType() == HitResult.Type.BLOCK) {
                      BlockHitResult blockHit = (BlockHitResult) hit;
                      var pos = blockHit.getBlockPos();
                      Direction side = blockHit.getDirection();

                      if (pos.getY() < nms.level().getMaxY() - (side == Direction.UP ? 1 : 0)
                          && nms.level().mayInteract(nms, pos)) {
                        nms.resetLastActionTime();
                        var result =
                            nms.gameMode.useItemOn(
                                nms, nms.level(), nms.getItemInHand(hand), hand, blockHit);
                        if (result.consumesAction()) {
                          nms.swing(hand);
                          freeze[0] = 3;
                          acted = true;
                          break;
                        }
                      }

                    } else if (hit.getType() == HitResult.Type.ENTITY) {
                      EntityHitResult entityHit = (EntityHitResult) hit;
                      var entity = entityHit.getEntity();

                      boolean handWasEmpty = nms.getItemInHand(hand).isEmpty();
                      boolean itemFrameEmpty =
                          (entity instanceof ItemFrame ife) && ife.getItem().isEmpty();
                      Vec3 relPos =
                          entityHit
                              .getLocation()
                              .subtract(entity.getX(), entity.getY(), entity.getZ());

                      nms.resetLastActionTime();

                      if (entity.interactAt(nms, relPos, hand).consumesAction()) {
                        freeze[0] = 3;
                        acted = true;
                        break;
                      }
                      if (nms.interactOn(entity, hand).consumesAction()
                          && !(handWasEmpty && itemFrameEmpty)) {
                        freeze[0] = 3;
                        acted = true;
                        break;
                      }
                    }

                    if (nms.gameMode
                        .useItem(nms, nms.level(), nms.getItemInHand(hand), hand)
                        .consumesAction()) {
                      nms.resetLastActionTime();
                      freeze[0] = 3;
                      acted = true;
                      break;
                    }
                  }

                  if (once && acted) stopUsing(uuid);
                },
            0L,
            1L);

    useTasks.put(uuid, taskId);
  }

  private void cancelAll(UUID botUuid) {
    pathfinding.cancel(botUuid);

    stopUsing(botUuid);

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

  public void stopUsing(UUID botUuid) {
    Integer taskId = useTasks.remove(botUuid);
    if (taskId != null) FppScheduler.cancelTask(taskId);

    manager.unlockAction(botUuid);

    activeUseLocations.remove(botUuid);
    activeUseOnceFlags.remove(botUuid);

    FakePlayer fp = manager.getByUuid(botUuid);
    if (fp != null) {
      Player bot = fp.getPlayer();
      if (bot != null && bot.isOnline()) ((CraftPlayer) bot).getHandle().releaseUsingItem();
    }
  }

  public void stopAll() {
    pathfinding.cancelAll(PathfindingService.Owner.USE);
    new HashSet<>(useTasks.keySet()).forEach(this::cancelAll);
  }

  public boolean isNavigating(UUID botUuid) {
    return pathfinding.isNavigating(botUuid);
  }

  public boolean isUsing(UUID botUuid) {
    return useTasks.containsKey(botUuid);
  }

  @org.jetbrains.annotations.Nullable
  public Location getActiveUseLocation(UUID botUuid) {
    return activeUseLocations.get(botUuid);
  }

  public boolean isActiveUseOnce(UUID botUuid) {
    Boolean v = activeUseOnceFlags.get(botUuid);
    return v != null && v;
  }

  public void resumeUsing(FakePlayer fp) {
    UUID uuid = fp.getUuid();
    Location useLoc = getActiveUseLocation(uuid);
    boolean once = isActiveUseOnce(uuid);
    if (useLoc != null) {
      resumeUsing(fp, once, useLoc);
    }
  }

  public void resumeUsing(FakePlayer fp, boolean once, Location loc) {
    if (fp == null || loc == null) return;
    Player bot = fp.getPlayer();
    if (bot == null || !bot.isOnline()) return;
    cancelAll(fp.getUuid());
    if (PathfindingService.xzDist(bot.getLocation(), loc) <= Config.pathfindingArrivalDistance()) {
      lockAndStartUsing(fp, once, loc);
    } else {
      startNavigation(fp, once, loc);
    }
  }

  @SuppressWarnings("resource")
  private static HitResult rayTrace(ServerPlayer player) {
    double reach = player.gameMode.isCreative() ? 5.0 : 4.5;
    Vec3 eyePos = player.getEyePosition(1.0f);
    Vec3 viewVec = player.getViewVector(1.0f);
    Vec3 endPos = eyePos.add(viewVec.x * reach, viewVec.y * reach, viewVec.z * reach);

    BlockHitResult blockHit;
    try {
      blockHit =
          player
              .level()
              .clip(
                  new ClipContext(
                      eyePos, endPos, ClipContext.Block.OUTLINE, ClipContext.Fluid.NONE, player));
    } catch (Exception e) {
      return null;
    }

    double maxSqDist = reach * reach;
    if (blockHit.getType() != HitResult.Type.MISS)
      maxSqDist = blockHit.getLocation().distanceToSqr(eyePos);

    AABB searchBox = player.getBoundingBox().expandTowards(viewVec.scale(reach)).inflate(1.0);

    EntityHitResult entityHit = null;
    double entityDistSq = maxSqDist;

    for (var entity :
        player.level().getEntities(player, searchBox, e -> !e.isSpectator() && e.isPickable())) {
      AABB entityBox = entity.getBoundingBox().inflate(entity.getPickRadius());
      var hitOpt = entityBox.clip(eyePos, endPos);
      if (entityBox.contains(eyePos)) {
        if (entityDistSq >= 0) {
          entityHit = new EntityHitResult(entity, hitOpt.orElse(eyePos));
          entityDistSq = 0;
        }
      } else if (hitOpt.isPresent()) {
        double d = eyePos.distanceToSqr(hitOpt.get());
        if (d < entityDistSq || entityDistSq == 0) {
          entityHit = new EntityHitResult(entity, hitOpt.get());
          entityDistSq = d;
        }
      }
    }

    if (entityHit != null) return (HitResult) entityHit;
    return (HitResult) blockHit;
  }
}
