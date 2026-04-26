package me.bill.fakePlayerPlugin.command;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import me.bill.fakePlayerPlugin.FakePlayerPlugin;
import me.bill.fakePlayerPlugin.fakeplayer.FakePlayer;
import me.bill.fakePlayerPlugin.fakeplayer.FakePlayerManager;
import me.bill.fakePlayerPlugin.lang.Lang;
import me.bill.fakePlayerPlugin.permission.Perm;
import me.bill.fakePlayerPlugin.util.LuckPermsHelper;
import me.bill.fakePlayerPlugin.util.FppScheduler;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.command.CommandSender;

public final class RankCommand implements FppCommand {

  private final FakePlayerPlugin plugin;
  private final FakePlayerManager manager;

  public RankCommand(FakePlayerPlugin plugin, FakePlayerManager manager) {
    this.plugin = plugin;
    this.manager = manager;
  }

  @Override
  public String getName() {
    return "rank";
  }

  @Override
  public String getUsage() {
    return "/fpp rank <bot> <group|clear> | /fpp rank random <group> [num] | /fpp rank list";
  }

  @Override
  public String getDescription() {
    return "Assign LuckPerms groups to one bot or random bots.";
  }

  @Override
  public String getPermission() {
    return Perm.RANK;
  }

  @Override
  public boolean canUse(CommandSender sender) {
    return Perm.has(sender, Perm.RANK);
  }

  @Override
  public boolean execute(CommandSender sender, String[] args) {

    if (!plugin.isLuckPermsAvailable()) {
      sender.sendMessage(Lang.get("rank-no-luckperms"));
      return true;
    }

    if (args.length == 0) {
      sender.sendMessage(Lang.get("rank-usage"));
      return true;
    }

    if (args[0].equalsIgnoreCase("list")) {
      executeList(sender);
      return true;
    }

    if (args[0].equalsIgnoreCase("random")) {
      executeRandom(sender, args);
      return true;
    }

    if (args.length < 2) {
      sender.sendMessage(Lang.get("rank-usage"));
      return true;
    }

    String botName = args[0];
    String groupArg = args[1];

    FakePlayer fp = manager.getByName(botName);
    if (fp == null) {
      sender.sendMessage(Lang.get("rank-bot-not-found", "name", botName));
      return true;
    }

    if (groupArg.equalsIgnoreCase("clear")) {
      executeClear(sender, fp);
    } else {
      executeSet(sender, fp, groupArg);
    }
    return true;
  }

  private CompletableFuture<Void> applyGroup(FakePlayer fp, String groupName) {
    return LuckPermsHelper.setPlayerGroup(fp.getUuid(), groupName)
        .thenRun(
            () -> {
              fp.setLuckpermsGroup(groupName);
              FppScheduler.runSyncLater(
                  plugin,
                  () -> {
                    if (manager.getByName(fp.getName()) == null) return;
                    manager.refreshLpDisplayName(fp);
                  },
                  2L);
            });
  }

  private void executeSet(CommandSender sender, FakePlayer fp, String groupName) {
    if (!LuckPermsHelper.groupExists(groupName)) {
      sender.sendMessage(Lang.get("rank-group-not-found", "group", groupName));
      return;
    }

    sender.sendMessage(
        Component.text("Assigning ", NamedTextColor.GRAY)
            .append(Component.text(fp.getName(), NamedTextColor.AQUA))
            .append(Component.text(" → ", NamedTextColor.GRAY))
            .append(Component.text(groupName, NamedTextColor.GREEN))
            .append(Component.text("…", NamedTextColor.GRAY)));

    applyGroup(fp, groupName)
        .thenRun(
            () -> {
              sender.sendMessage(
                  Component.text("✔ ", NamedTextColor.GREEN)
                      .append(Component.text(fp.getName(), NamedTextColor.AQUA))
                      .append(Component.text(" → ", NamedTextColor.GRAY))
                      .append(Component.text(groupName, NamedTextColor.GREEN)));
            })
        .exceptionally(
            throwable -> {
              sender.sendMessage(
                  Component.text("✘ Failed: " + throwable.getMessage(), NamedTextColor.RED));
              return null;
            });
  }

  private void executeClear(CommandSender sender, FakePlayer fp) {
    String defaultGroup = me.bill.fakePlayerPlugin.config.Config.luckpermsDefaultGroup();
    String targetGroup =
        (defaultGroup != null && !defaultGroup.trim().isEmpty()) ? defaultGroup.trim() : "default";

    applyGroup(fp, targetGroup)
        .thenRun(
            () -> {
              sender.sendMessage(
                  Component.text("✔ ", NamedTextColor.GREEN)
                      .append(Component.text(fp.getName(), NamedTextColor.AQUA))
                      .append(Component.text(" reset to group ", NamedTextColor.GRAY))
                      .append(Component.text(targetGroup, NamedTextColor.GREEN)));
            })
        .exceptionally(
            throwable -> {
              sender.sendMessage(
                  Component.text("✘ Failed: " + throwable.getMessage(), NamedTextColor.RED));
              return null;
            });
  }

  private void executeRandom(CommandSender sender, String[] args) {
    if (args.length < 2) {
      sender.sendMessage(Lang.get("rank-usage"));
      return;
    }

    String groupName = args[1];
    if (!LuckPermsHelper.groupExists(groupName)) {
      sender.sendMessage(Lang.get("rank-group-not-found", "group", groupName));
      return;
    }

    int requested = 1;
    if (args.length >= 3) {
      try {
        requested = Integer.parseInt(args[2]);
      } catch (NumberFormatException ignored) {
        sender.sendMessage(Component.text("✘ Invalid number: " + args[2], NamedTextColor.RED));
        return;
      }
    }

    if (requested <= 0) {
      sender.sendMessage(Component.text("✘ Number must be at least 1.", NamedTextColor.RED));
      return;
    }

    List<FakePlayer> candidates = new ArrayList<>(manager.getActivePlayers());
    if (candidates.isEmpty()) {
      sender.sendMessage(Component.text("No active bots.", NamedTextColor.GRAY));
      return;
    }

    Collections.shuffle(candidates);
    int count = Math.min(requested, candidates.size());
    List<FakePlayer> selected = new ArrayList<>(candidates.subList(0, count));

    sender.sendMessage(
        Component.text("Assigning ", NamedTextColor.GRAY)
            .append(Component.text(groupName, NamedTextColor.GREEN))
            .append(Component.text(" to ", NamedTextColor.GRAY))
            .append(Component.text(count + " random bot(s)", NamedTextColor.AQUA))
            .append(Component.text("…", NamedTextColor.GRAY)));

    List<CompletableFuture<Void>> futures = new ArrayList<>();
    for (FakePlayer fp : selected) {
      futures.add(applyGroup(fp, groupName));
    }

    CompletableFuture.allOf(futures.toArray(new CompletableFuture[0]))
        .thenRun(
            () ->
                sender.sendMessage(
                    Component.text("✔ ", NamedTextColor.GREEN)
                        .append(Component.text("Assigned ", NamedTextColor.GRAY))
                        .append(Component.text(groupName, NamedTextColor.GREEN))
                        .append(Component.text(" to ", NamedTextColor.GRAY))
                        .append(Component.text(count + " bot(s)", NamedTextColor.AQUA))))
        .exceptionally(
            throwable -> {
              sender.sendMessage(
                  Component.text("✘ Failed: " + throwable.getMessage(), NamedTextColor.RED));
              return null;
            });
  }

  private void executeList(CommandSender sender) {
    Collection<FakePlayer> bots = manager.getActivePlayers();
    if (bots.isEmpty()) {
      sender.sendMessage(Component.text("No active bots.", NamedTextColor.GRAY));
      return;
    }

    sender.sendMessage(Component.text("─── Bot LP Groups ───", NamedTextColor.DARK_GRAY));
    for (FakePlayer fp : bots) {
      LuckPermsHelper.getStoredPrimaryGroup(fp.getUuid())
          .thenAccept(
              group -> {
                sender.sendMessage(
                    Component.text("  " + fp.getName(), NamedTextColor.AQUA)
                        .append(Component.text(" → ", NamedTextColor.GRAY))
                        .append(Component.text(group, NamedTextColor.GREEN)));
              });
    }
  }

  @Override
  public List<String> tabComplete(CommandSender sender, String[] args) {
    if (!plugin.isLuckPermsAvailable()) return List.of();
    if (args.length == 1) {
      List<String> options = new ArrayList<>();
      options.add("list");
      options.add("random");
      manager.getActivePlayers().forEach(fp -> options.add(fp.getName()));
      return filter(options, args[0]);
    }
    if (args.length == 2 && !args[0].equalsIgnoreCase("list")) {
      List<String> options = new ArrayList<>(LuckPermsHelper.getAllGroupNames());
      if (!args[0].equalsIgnoreCase("random")) {
        options.add("clear");
      }
      return filter(options, args[1]);
    }
    if (args.length == 3 && args[0].equalsIgnoreCase("random")) {
      return filter(new ArrayList<>(List.of("1", "2", "3", "5", "10", "25", "50", "100")), args[2]);
    }
    return List.of();
  }

  private static List<String> filter(List<String> list, String prefix) {
    if (prefix.isBlank()) return list;
    String lower = prefix.toLowerCase();
    return list.stream().filter(s -> s.toLowerCase().startsWith(lower)).toList();
  }
}
