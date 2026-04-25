package me.bill.fakePlayerPlugin.command;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import me.bill.fakePlayerPlugin.FakePlayerPlugin;
import me.bill.fakePlayerPlugin.fakeplayer.FakePlayer;
import me.bill.fakePlayerPlugin.fakeplayer.FakePlayerManager;
import me.bill.fakePlayerPlugin.fakeplayer.PathfindingService;
import me.bill.fakePlayerPlugin.lang.Lang;
import me.bill.fakePlayerPlugin.permission.Perm;
import org.bukkit.Bukkit;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import me.bill.fakePlayerPlugin.api.impl.FppApiImpl;

public final class FollowCommand implements FppCommand {

  private static final double FOLLOW_DISTANCE = 2.0;

  private static final double RECALC_DISTANCE = 3.5;

  private final FakePlayerPlugin plugin;
  private final FakePlayerManager manager;
  private final PathfindingService pathfinding;

  private final Map<UUID, UUID> activeFollows = new ConcurrentHashMap<>();

  public FollowCommand(
      FakePlayerPlugin plugin, FakePlayerManager manager, PathfindingService pathfinding) {
    this.plugin = plugin;
    this.manager = manager;
    this.pathfinding = pathfinding;
  }

  @Override
  public String getName() {
    return "follow";
  }

  @Override
  public String getUsage() {
    return "<bot|all> <player>  |  <bot|all> --stop";
  }

  @Override
  public String getDescription() {
    return "Make a bot (or all bots) continuously follow a player.";
  }

  @Override
  public String getPermission() {
    return Perm.FOLLOW;
  }

  @Override
  public boolean canUse(CommandSender sender) {
    return Perm.has(sender, Perm.FOLLOW);
  }

  @Override
  public boolean execute(CommandSender sender, String[] args) {
    if (args.length == 0) {
      sender.sendMessage(Lang.get("follow-usage"));
      return true;
    }

    if (args[0].equalsIgnoreCase("all")) {
      return executeAll(sender, args);
    }

    FakePlayer fp = manager.getByName(args[0]);
    if (fp == null) {
      sender.sendMessage(Lang.get("follow-bot-not-found", "name", args[0]));
      return true;
    }

    Player bot = fp.getPlayer();
    if (bot == null || !bot.isOnline()) {
      sender.sendMessage(Lang.get("follow-bot-not-online", "name", args[0]));
      return true;
    }

    if (args.length >= 2 && args[1].equalsIgnoreCase("--stop")) {
      if (!isFollowing(bot.getUniqueId())) {
        sender.sendMessage(Lang.get("follow-not-following", "name", fp.getDisplayName()));
      } else {
        stopFollowing(bot.getUniqueId());
        sender.sendMessage(Lang.get("follow-stopped", "name", fp.getDisplayName()));
      }
      return true;
    }

    Player target;
    if (args.length >= 2 && !args[1].equalsIgnoreCase("--start")) {
      target = Bukkit.getPlayer(args[1]);
      if (target == null) {
        sender.sendMessage(Lang.get("player-not-found", "player", args[1]));
        return true;
      }
    } else {

      if (!(sender instanceof Player)) {
        sender.sendMessage(Lang.get("follow-usage"));
        return true;
      }
      target = (Player) sender;
    }

    if (!bot.getWorld().equals(target.getWorld())) {
      sender.sendMessage(
          Lang.get(
              "follow-different-world", "name", fp.getDisplayName(), "player", target.getName()));
      return true;
    }

    stopFollowing(bot.getUniqueId());
    startFollowing(fp, target);
    sender.sendMessage(
        Lang.get("follow-started", "name", fp.getDisplayName(), "player", target.getName()));
    return true;
  }

  private boolean executeAll(CommandSender sender, String[] args) {
    if (args.length < 2) {
      sender.sendMessage(Lang.get("follow-usage"));
      return true;
    }

    if (args[1].equalsIgnoreCase("--stop")) {
      int stopped = 0;
      for (FakePlayer fp : manager.getActivePlayers()) {
        if (isFollowing(fp.getUuid())) {
          stopFollowing(fp.getUuid());
          stopped++;
        }
      }
      sender.sendMessage(Lang.get("follow-all-stopped", "count", String.valueOf(stopped)));
      return true;
    }

    Player target;
    if (!args[1].equalsIgnoreCase("--start")) {
      target = Bukkit.getPlayer(args[1]);
      if (target == null) {
        sender.sendMessage(Lang.get("player-not-found", "player", args[1]));
        return true;
      }
    } else {
      if (!(sender instanceof Player)) {
        sender.sendMessage(Lang.get("follow-usage"));
        return true;
      }
      target = (Player) sender;
    }

    int started = 0, skipped = 0;
    for (FakePlayer fp : manager.getActivePlayers()) {
      Player bot = fp.getPlayer();
      if (bot == null || !bot.isOnline()) {
        skipped++;
        continue;
      }
      if (!bot.getWorld().equals(target.getWorld())) {
        skipped++;
        continue;
      }
      stopFollowing(bot.getUniqueId());
      startFollowing(fp, target);
      started++;
    }
    sender.sendMessage(
        Lang.get(
            "follow-all-started",
            "player",
            target.getName(),
            "count",
            String.valueOf(started),
            "skipped",
            String.valueOf(skipped)));
    return true;
  }

  private void startFollowing(@NotNull FakePlayer fp, @NotNull Player target) {
    FppApiImpl.fireTaskEvent(fp, "follow", me.bill.fakePlayerPlugin.api.event.FppBotTaskEvent.Action.START);
    var followEvt = new me.bill.fakePlayerPlugin.api.event.FppBotFollowEvent(
        new me.bill.fakePlayerPlugin.api.impl.FppBotImpl(fp),
        me.bill.fakePlayerPlugin.api.event.FppBotFollowEvent.Action.START,
        target);
    org.bukkit.Bukkit.getPluginManager().callEvent(followEvt);
    if (followEvt.isCancelled()) return;
    final UUID botUuid = fp.getUuid();
    final UUID targetUuid = target.getUniqueId();
    activeFollows.put(botUuid, targetUuid);

    scheduleNavigation(fp, botUuid, targetUuid);
  }

  private void scheduleNavigation(
      @NotNull FakePlayer fp, @NotNull UUID botUuid, @NotNull UUID targetUuid) {
    if (!activeFollows.containsKey(botUuid)) return;

    pathfinding.navigate(
        fp,
        new PathfindingService.NavigationRequest(
            PathfindingService.Owner.FOLLOW,
            () -> {
              Player liveTarget = Bukkit.getPlayer(targetUuid);
              if (liveTarget == null || !liveTarget.isOnline()) {
                return null;
              }
              return liveTarget.getLocation();
            },
            FOLLOW_DISTANCE,
            RECALC_DISTANCE,
            Integer.MAX_VALUE,
            null,
            () -> cleanupFollow(botUuid),
            null));
  }

  public void stopFollowing(@NotNull UUID botUuid) {
    FakePlayer fp = manager.getByUuid(botUuid);
    if (fp != null) {
      FppApiImpl.fireTaskEvent(fp, "follow", me.bill.fakePlayerPlugin.api.event.FppBotTaskEvent.Action.STOP);
      var followEvt = new me.bill.fakePlayerPlugin.api.event.FppBotFollowEvent(
          new me.bill.fakePlayerPlugin.api.impl.FppBotImpl(fp),
          me.bill.fakePlayerPlugin.api.event.FppBotFollowEvent.Action.STOP,
          null);
      org.bukkit.Bukkit.getPluginManager().callEvent(followEvt);
    }
    if (activeFollows.remove(botUuid) != null) {
      pathfinding.cancel(botUuid);
    }
  }

  public void stopAll() {
    for (UUID uuid : new ArrayList<>(activeFollows.keySet())) {
      stopFollowing(uuid);
    }
  }

  public boolean isFollowing(@NotNull UUID botUuid) {
    return activeFollows.containsKey(botUuid);
  }

  @Nullable
  public UUID getFollowTarget(@NotNull UUID botUuid) {
    return activeFollows.get(botUuid);
  }

  public void startFollowingFromSettings(@NotNull FakePlayer fp, @NotNull Player target) {
    stopFollowing(fp.getUuid());
    startFollowing(fp, target);
  }

  public void resumeFollowing(@NotNull FakePlayer fp) {
    UUID uuid = fp.getUuid();
    UUID targetUuid = getFollowTarget(uuid);
    if (targetUuid != null) {
      resumeFollowing(fp, targetUuid);
    }
  }

  public void resumeFollowing(@NotNull FakePlayer fp, @NotNull UUID targetUuid) {
    Player target = Bukkit.getPlayer(targetUuid);
    if (target == null || !target.isOnline()) return;
    Player bot = fp.getPlayer();
    if (bot == null || !bot.isOnline()) return;
    if (!bot.getWorld().equals(target.getWorld())) return;
    startFollowing(fp, target);
  }

  public void cleanupBot(@NotNull UUID botUuid) {
    stopFollowing(botUuid);
  }

  private void cleanupFollow(@NotNull UUID botUuid) {
    activeFollows.remove(botUuid);
  }

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
      for (String flag : List.of("--start", "--stop")) if (flag.startsWith(in)) out.add(flag);
      for (Player p : Bukkit.getOnlinePlayers()) {
        if (manager.getByName(p.getName()) == null && p.getName().toLowerCase().startsWith(in))
          out.add(p.getName());
      }
    }
    return out;
  }
}
