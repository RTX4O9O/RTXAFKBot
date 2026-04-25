package me.bill.fakePlayerPlugin.command;

import java.util.List;
import java.util.stream.Collectors;
import me.bill.fakePlayerPlugin.FakePlayerPlugin;
import me.bill.fakePlayerPlugin.config.Config;
import me.bill.fakePlayerPlugin.fakeplayer.BotSwapAI;
import me.bill.fakePlayerPlugin.fakeplayer.BotType;
import me.bill.fakePlayerPlugin.fakeplayer.FakePlayer;
import me.bill.fakePlayerPlugin.fakeplayer.FakePlayerManager;
import me.bill.fakePlayerPlugin.fakeplayer.PeakHoursManager;
import me.bill.fakePlayerPlugin.lang.Lang;
import me.bill.fakePlayerPlugin.permission.Perm;
import me.bill.fakePlayerPlugin.util.FppScheduler;
import org.bukkit.Location;
import org.bukkit.command.CommandSender;

public class PeaksCommand implements FppCommand {

  private final FakePlayerPlugin plugin;
  private final FakePlayerManager manager;

  public PeaksCommand(FakePlayerPlugin plugin, FakePlayerManager manager) {
    this.plugin = plugin;
    this.manager = manager;
  }

  @Override
  public String getName() {
    return "peaks";
  }

  @Override
  public String getUsage() {
    return "[on|off|status|next|force|list|wake [name]|sleep <name>]";
  }

  @Override
  public String getDescription() {
    return "Manage peak-hours bot pool scheduling.";
  }

  @Override
  public String getPermission() {
    return Perm.PEAKS;
  }

  @Override
  public boolean execute(CommandSender sender, String[] args) {

    if (args.length == 0) {
      if (Config.peakHoursEnabled()) disablePeaks(sender);
      else enablePeaks(sender);
      return true;
    }

    switch (args[0].toLowerCase()) {
      case "on", "true", "yes", "1" -> enablePeaks(sender);
      case "off", "false", "no", "0" -> disablePeaks(sender);
      case "status", "info" -> sendStatus(sender);
      case "next" -> sendNext(sender);
      case "force", "check" -> forceCheck(sender);
      case "list" -> sendList(sender);
      case "wake" -> {
        String name = args.length >= 2 ? args[1] : null;
        doWake(sender, name);
      }
      case "sleep" -> {
        if (args.length < 2) {
          sender.sendMessage(Lang.get("peaks-invalid"));
          return true;
        }
        doSleep(sender, args[1]);
      }
      default -> sender.sendMessage(Lang.get("peaks-invalid"));
    }
    return true;
  }

  private void enablePeaks(CommandSender sender) {

    if (!Config.swapEnabled()) {
      sender.sendMessage(Lang.get("peaks-requires-swap"));
      return;
    }

    plugin.getConfig().set("peak-hours.enabled", true);
    plugin.saveConfig();

    PeakHoursManager ph = plugin.getPeakHoursManager();
    if (ph != null) {
      if (!ph.isRunning()) ph.start();

      FppScheduler.runSyncLater(plugin, ph::forceCheck, 5L);
    }

    sender.sendMessage(Lang.get("peaks-enabled"));
    Config.debug("[PeakHours] enabled by " + sender.getName());
  }

  private void disablePeaks(CommandSender sender) {
    plugin.getConfig().set("peak-hours.enabled", false);
    plugin.saveConfig();

    PeakHoursManager ph = plugin.getPeakHoursManager();
    if (ph != null) ph.wakeAll();

    sender.sendMessage(Lang.get("peaks-disabled"));
    Config.debug("[PeakHours] disabled by " + sender.getName());
  }

  private void forceCheck(CommandSender sender) {
    if (!Config.swapEnabled()) {
      sender.sendMessage(Lang.get("peaks-requires-swap"));
      return;
    }
    PeakHoursManager ph = plugin.getPeakHoursManager();
    if (ph != null) ph.forceCheck();
    sender.sendMessage(Lang.get("peaks-force-check"));
  }

  private void sendStatus(CommandSender sender) {
    if (!Config.peakHoursEnabled()) {
      sender.sendMessage(Lang.get("peaks-status-off"));
      return;
    }

    PeakHoursManager ph = plugin.getPeakHoursManager();
    if (ph == null) {
      sender.sendMessage(Lang.get("peaks-status-off"));
      return;
    }

    double fraction = ph.computeTargetFraction();
    int sleeping = ph.getSleepingCount();
    int online =
        (int)
            manager.getActivePlayers().stream()
                .filter(fp -> fp.getBotType() == BotType.AFK)
                .count();
    BotSwapAI swapAI = plugin.getBotSwapAI();
    int swapping = swapAI != null ? swapAI.getSwappedOutCount() : 0;
    int total = ph.getTotalPool();
    int minOnline = Config.peakHoursMinOnline();
    int target = Math.max(minOnline, (int) Math.round(fraction * total));
    String window = ph.getCurrentWindowLabel();
    String timezone = Config.peakHoursTimezone();

    sender.sendMessage(
        Lang.get(
            "peaks-status-on",
            "window",
            window,
            "fraction",
            String.format("%.0f%%", fraction * 100),
            "target",
            String.valueOf(target),
            "sleeping",
            String.valueOf(sleeping),
            "online",
            String.valueOf(online),
            "swapping",
            String.valueOf(swapping),
            "total",
            String.valueOf(total),
            "timezone",
            timezone));
  }

  private void sendNext(CommandSender sender) {
    PeakHoursManager ph = plugin.getPeakHoursManager();
    if (ph == null || !Config.peakHoursEnabled()) {
      sender.sendMessage(Lang.get("peaks-status-off"));
      return;
    }

    long seconds = ph.getSecondsToNextWindow();
    if (seconds < 0) {
      sender.sendMessage(Lang.get("peaks-no-windows"));
      return;
    }

    double nextFrac = ph.getNextWindowFraction();
    String timeLabel;
    if (seconds >= 3600) {
      timeLabel = (seconds / 3600) + "h " + ((seconds % 3600) / 60) + "m";
    } else if (seconds >= 60) {
      timeLabel = (seconds / 60) + "m " + (seconds % 60) + "s";
    } else {
      timeLabel = seconds + "s";
    }
    String nextFracLabel = nextFrac >= 0 ? String.format("%.0f%%", nextFrac * 100) : "?";

    sender.sendMessage(
        Lang.get("peaks-next-window", "time", timeLabel, "next_fraction", nextFracLabel));
  }

  private void sendList(CommandSender sender) {
    PeakHoursManager ph = plugin.getPeakHoursManager();
    if (ph == null || ph.getSleepingCount() == 0) {
      sender.sendMessage(Lang.get("peaks-list-empty"));
      return;
    }

    List<PeakHoursManager.SleepingBot> entries = ph.getSleepingEntries();
    sender.sendMessage(Lang.get("peaks-list-header", "count", String.valueOf(entries.size())));

    for (PeakHoursManager.SleepingBot sb : entries) {
      Location loc = sb.loc();
      String world = loc != null && loc.getWorld() != null ? loc.getWorld().getName() : "?";
      String x = loc != null ? String.valueOf((int) loc.getX()) : "?";
      String y = loc != null ? String.valueOf((int) loc.getY()) : "?";
      String z = loc != null ? String.valueOf((int) loc.getZ()) : "?";
      sender.sendMessage(
          Lang.get("peaks-list-entry", "name", sb.name(), "world", world, "x", x, "y", y, "z", z));
    }
  }

  private void doWake(CommandSender sender, String name) {
    PeakHoursManager ph = plugin.getPeakHoursManager();
    if (ph == null) {
      sender.sendMessage(Lang.get("peaks-status-off"));
      return;
    }

    if (name == null || name.equalsIgnoreCase("all")) {
      int count = ph.getSleepingCount();
      ph.wakeAll();
      sender.sendMessage(Lang.get("peaks-wake-all", "count", String.valueOf(count)));
    } else {
      if (ph.wakeBotByName(name)) {
        sender.sendMessage(Lang.get("peaks-wake-success", "name", name));
      } else {
        sender.sendMessage(Lang.get("peaks-sleep-failed", "name", name));
      }
    }
  }

  private void doSleep(CommandSender sender, String name) {
    if (!Config.swapEnabled()) {
      sender.sendMessage(Lang.get("peaks-requires-swap"));
      return;
    }
    PeakHoursManager ph = plugin.getPeakHoursManager();
    if (ph == null) {
      sender.sendMessage(Lang.get("peaks-status-off"));
      return;
    }
    if (ph.putBotToSleepByName(name)) {
      sender.sendMessage(Lang.get("peaks-sleep-success", "name", name));
    } else {
      sender.sendMessage(Lang.get("peaks-sleep-failed", "name", name));
    }
  }

  @Override
  public List<String> tabComplete(CommandSender sender, String[] args) {
    if (args.length == 1) {
      String pfx = args[0].toLowerCase();
      return List.of("on", "off", "status", "next", "force", "list", "wake", "sleep").stream()
          .filter(s -> s.startsWith(pfx))
          .collect(Collectors.toList());
    }
    if (args.length == 2) {
      String sub = args[0].toLowerCase();
      String pfx = args[1].toLowerCase();
      if (sub.equals("wake")) {

        PeakHoursManager ph = plugin.getPeakHoursManager();
        List<String> opts = ph != null ? ph.getSleepingNames() : List.of();
        return java.util.stream.Stream.concat(java.util.stream.Stream.of("all"), opts.stream())
            .filter(s -> s.toLowerCase().startsWith(pfx))
            .collect(Collectors.toList());
      }
      if (sub.equals("sleep")) {

        return manager.getActivePlayers().stream()
            .filter(fp -> fp.getBotType() == BotType.AFK)
            .map(FakePlayer::getName)
            .filter(n -> n.toLowerCase().startsWith(pfx))
            .collect(Collectors.toList());
      }
    }
    return List.of();
  }
}
