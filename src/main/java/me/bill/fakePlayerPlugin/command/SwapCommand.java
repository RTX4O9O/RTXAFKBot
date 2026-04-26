package me.bill.fakePlayerPlugin.command;

import java.util.List;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;
import me.bill.fakePlayerPlugin.FakePlayerPlugin;
import me.bill.fakePlayerPlugin.config.Config;
import me.bill.fakePlayerPlugin.fakeplayer.BotSwapAI;
import me.bill.fakePlayerPlugin.fakeplayer.FakePlayer;
import me.bill.fakePlayerPlugin.fakeplayer.FakePlayerManager;
import me.bill.fakePlayerPlugin.lang.Lang;
import me.bill.fakePlayerPlugin.permission.Perm;
import org.bukkit.command.CommandSender;

public class SwapCommand implements FppCommand {

  private final FakePlayerPlugin plugin;
  private final FakePlayerManager manager;

  public SwapCommand(FakePlayerPlugin plugin, FakePlayerManager manager) {
    this.plugin = plugin;
    this.manager = manager;
  }

  @Override
  public String getName() {
    return "swap";
  }

  @Override
  public String getUsage() {
    return "[on|off|status|now <bot>|list|info <bot>]";
  }

  @Override
  public String getDescription() {
    return "Toggle bot session rotation (swap in / out).";
  }

  @Override
  public String getPermission() {
    return Perm.SWAP;
  }

  @Override
  public boolean execute(CommandSender sender, String[] args) {

    if (args.length == 0) {
      boolean enable = !Config.swapEnabled();
      if (enable) enableSwap(sender);
      else disableSwap(sender);
      Config.debug("swap toggled to " + enable + " by " + sender.getName());
      return true;
    }

    switch (args[0].toLowerCase()) {
      case "on", "true", "yes", "1" -> enableSwap(sender);

      case "off", "false", "no", "0" -> disableSwap(sender);

      case "status" -> sendStatus(sender);

      case "now" -> {
        if (args.length < 2) {
          sender.sendMessage(Lang.get("swap-now-usage"));
          return true;
        }
        String botName = args[1];
        BotSwapAI ai = plugin.getBotSwapAI();
        if (ai == null) {
          sender.sendMessage(Lang.get("swap-not-available"));
          return true;
        }
        if (ai.triggerNow(botName)) {
          sender.sendMessage(Lang.get("swap-now-success", "name", botName));
        } else {
          sender.sendMessage(Lang.get("swap-now-failed", "name", botName));
        }
      }

      case "list" -> sendList(sender);

      case "info" -> {
        if (args.length < 2) {
          sender.sendMessage(Lang.get("swap-info-usage"));
          return true;
        }
        sendBotInfo(sender, args[1]);
      }

      default -> sender.sendMessage(Lang.get("swap-invalid"));
    }

    return true;
  }

  private void enableSwap(CommandSender sender) {
    plugin.getConfig().set("swap.enabled", true);
    plugin.saveConfig();

    BotSwapAI ai = plugin.getBotSwapAI();
    if (ai != null) {
      for (FakePlayer fp : manager.getActivePlayers()) {
        ai.schedule(fp);
      }
    }
    sender.sendMessage(Lang.get("swap-enabled"));
    Config.debug("swap.enabled set to true by enableSwap()");
  }

  private void disableSwap(CommandSender sender) {
    plugin.getConfig().set("swap.enabled", false);
    plugin.saveConfig();

    BotSwapAI ai = plugin.getBotSwapAI();
    if (ai != null) ai.cancelAll();
    sender.sendMessage(Lang.get("swap-disabled"));
    Config.debug("swap.enabled set to false by disableSwap()");
  }

  private void sendStatus(CommandSender sender) {
    boolean on = Config.swapEnabled();
    BotSwapAI ai = plugin.getBotSwapAI();

    if (on && ai != null) {
      long nextSec = ai.getNextSwapSeconds();
      String nextLabel =
          nextSec >= 0
              ? (nextSec >= 60 ? (nextSec / 60) + "m " + (nextSec % 60) + "s" : nextSec + "s")
              : "none";
      int minOnline = Config.swapMinOnline();
      String minLabel = minOnline > 0 ? String.valueOf(minOnline) : "off";
      sender.sendMessage(
          Lang.get(
              "swap-status-on",
              "sessions",
              String.valueOf(ai.getActiveSessionCount()),
              "offline",
              String.valueOf(ai.getSwappedOutCount()),
              "next",
              nextLabel,
              "min",
              minLabel));
    } else {
      sender.sendMessage(Lang.get("swap-status-off"));
    }
  }

  private void sendList(CommandSender sender) {
    BotSwapAI ai = plugin.getBotSwapAI();
    if (ai == null || ai.getActiveSessions().isEmpty()) {
      sender.sendMessage(Lang.get("swap-list-empty"));
      return;
    }

    Set<UUID> sessions = ai.getActiveSessions();
    List<FakePlayer> scheduled =
        manager.getActivePlayers().stream()
            .filter(fp -> sessions.contains(fp.getUuid()))
            .collect(Collectors.toList());

    if (scheduled.isEmpty()) {
      sender.sendMessage(Lang.get("swap-list-empty"));
      return;
    }

    sender.sendMessage(Lang.get("swap-list-header", "count", String.valueOf(scheduled.size())));

    long now = System.currentTimeMillis();
    for (FakePlayer fp : scheduled) {
      long expiry = ai.getSessionExpiry(fp.getUuid());
      long remainSec = expiry > 0 ? Math.max(0, (expiry - now) / 1000L) : -1;
      String timeLabel =
          remainSec >= 0
              ? (remainSec >= 60
                  ? (remainSec / 60) + "m" + (remainSec % 60) + "s"
                  : remainSec + "s")
              : "?";
      sender.sendMessage(
          Lang.get(
              "swap-list-entry",
              "name",
              fp.getDisplayName(),
              "personality",
              ai.getPersonalityLabel(fp.getUuid()),
              "swaps",
              String.valueOf(ai.getSwapCount(fp.getUuid())),
              "time",
              timeLabel));
    }
  }

  private void sendBotInfo(CommandSender sender, String botName) {
    FakePlayer fp = manager.getByName(botName);
    BotSwapAI ai = plugin.getBotSwapAI();

    if (fp == null) {
      sender.sendMessage(Lang.get("swap-info-not-found", "name", botName));
      return;
    }
    if (ai == null) {
      sender.sendMessage(Lang.get("swap-not-available"));
      return;
    }

    UUID id = fp.getUuid();
    long expiry = ai.getSessionExpiry(id);
    long now = System.currentTimeMillis();
    long remainSec = expiry > 0 ? Math.max(0, (expiry - now) / 1000L) : -1;
    String timeLabel =
        remainSec >= 0
            ? (remainSec >= 60 ? (remainSec / 60) + "m " + (remainSec % 60) + "s" : remainSec + "s")
            : "not scheduled";

    sender.sendMessage(
        Lang.get(
            "swap-info",
            "name",
            fp.getDisplayName(),
            "personality",
            ai.getPersonalityLabel(id),
            "swaps",
            String.valueOf(ai.getSwapCount(id)),
            "time",
            timeLabel,
            "offline",
            String.valueOf(ai.getSwappedOutCount())));
  }

  @Override
  public List<String> tabComplete(CommandSender sender, String[] args) {
    if (args.length == 1) {
      String pfx = args[0].toLowerCase();
      return List.of("on", "off", "status", "now", "list", "info").stream()
          .filter(s -> s.startsWith(pfx))
          .collect(Collectors.toList());
    }
    if (args.length == 2 && (args[0].equalsIgnoreCase("now") || args[0].equalsIgnoreCase("info"))) {
      String pfx = args[1].toLowerCase();
      return manager.getActiveNames().stream()
          .filter(n -> n.toLowerCase().startsWith(pfx))
          .collect(Collectors.toList());
    }
    return List.of();
  }
}
