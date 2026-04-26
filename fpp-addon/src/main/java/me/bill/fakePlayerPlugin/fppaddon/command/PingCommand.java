package me.bill.fakePlayerPlugin.fppaddon.command;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.ThreadLocalRandom;
import me.bill.fakePlayerPlugin.FakePlayerPlugin;
import me.bill.fakePlayerPlugin.api.FppAddonCommand;
import me.bill.fakePlayerPlugin.fakeplayer.FakePlayer;
import me.bill.fakePlayerPlugin.permission.Perm;
import org.bukkit.command.CommandSender;
import org.jetbrains.annotations.NotNull;

public final class PingCommand implements FppAddonCommand {

  private static final int DEFAULT_PING = 50;
  private static final int RANDOM_PING_MIN = 20;
  private static final int RANDOM_PING_MAX = 300;

  private final FakePlayerPlugin plugin;

  public PingCommand(FakePlayerPlugin plugin) {
    this.plugin = plugin;
  }

  @Override public @NotNull String getName() { return "ping"; }
  @Override public @NotNull String getUsage() { return "[<bot>|--count <n>] [--ping <ms>|--random|--reset]"; }
  @Override public @NotNull String getDescription() { return "Set a simulated ping (latency) for one or more bots."; }
  @Override public @NotNull String getPermission() { return Perm.PING; }

  @Override
  public boolean execute(@NotNull CommandSender sender, @NotNull String[] args) {
    if (args.length == 0) {
      sender.sendMessage(net.kyori.adventure.text.Component.text("Usage: /fpp ping " + getUsage(), net.kyori.adventure.text.format.NamedTextColor.RED));
      return true;
    }

    String botTarget = null;
    int count = -1;
    int fixedPing = -1;
    boolean random = false;
    boolean reset = false;

    int i = 0;
    while (i < args.length) {
      String arg = args[i];
      if (arg.equalsIgnoreCase("--random")) {
        if (fixedPing >= 0 || reset) { sender.sendMessage(net.kyori.adventure.text.Component.text("Ping options conflict.")); return true; }
        random = true;
        i++;
      } else if (arg.equalsIgnoreCase("--reset")) {
        if (random || fixedPing >= 0) { sender.sendMessage(net.kyori.adventure.text.Component.text("Ping options conflict.")); return true; }
        reset = true;
        i++;
      } else if (arg.equalsIgnoreCase("--ping")) {
        if (random) { sender.sendMessage(net.kyori.adventure.text.Component.text("Ping options conflict.")); return true; }
        if (fixedPing >= 0) { sender.sendMessage(net.kyori.adventure.text.Component.text("Duplicate --ping option.")); return true; }
        if (i + 1 >= args.length) { sender.sendMessage(net.kyori.adventure.text.Component.text("Missing value for --ping.")); return true; }
        try {
          fixedPing = Integer.parseInt(args[++i]);
          if (fixedPing < 0 || fixedPing > 9999) { sender.sendMessage(net.kyori.adventure.text.Component.text("Ping out of range.")); return true; }
        } catch (NumberFormatException e) {
          sender.sendMessage(net.kyori.adventure.text.Component.text("Invalid number: " + args[i]));
          return true;
        }
        i++;
      } else if (arg.equalsIgnoreCase("--count")) {
        if (i + 1 >= args.length) { sender.sendMessage(net.kyori.adventure.text.Component.text("Missing value for --count.")); return true; }
        try {
          count = Integer.parseInt(args[++i]);
          if (count < 1) { sender.sendMessage(net.kyori.adventure.text.Component.text("Invalid count.")); return true; }
        } catch (NumberFormatException e) {
          sender.sendMessage(net.kyori.adventure.text.Component.text("Invalid number: " + args[i]));
          return true;
        }
        i++;
      } else if (!arg.startsWith("--")) {
        if (botTarget != null) { sender.sendMessage(net.kyori.adventure.text.Component.text("Usage: /fpp ping " + getUsage())); return true; }
        botTarget = arg;
        i++;
      } else {
        sender.sendMessage(net.kyori.adventure.text.Component.text("Unknown option: " + arg));
        return true;
      }
    }

    if (botTarget != null && count >= 0) {
      sender.sendMessage(net.kyori.adventure.text.Component.text("Target and count conflict."));
      return true;
    }
    if (!random && !reset && fixedPing < 0) fixedPing = DEFAULT_PING;

    if (botTarget != null) return handleSingleBot(sender, botTarget, fixedPing, random, reset);
    return handleMultipleBots(sender, count, fixedPing, random, reset);
  }

  private boolean handleSingleBot(CommandSender sender, String name, int fixedPing, boolean random, boolean reset) {
    FakePlayer fp = plugin.getFakePlayerManager().getByName(name);
    if (fp == null) {
      sender.sendMessage(net.kyori.adventure.text.Component.text("Bot not found: " + name, net.kyori.adventure.text.format.NamedTextColor.RED));
      return true;
    }
    int ping = reset ? -1 : (random ? randomPing() : fixedPing);
    plugin.getFakePlayerManager().applyPing(fp, ping);
    plugin.getFakePlayerManager().persistBotSettings(fp);
    sender.sendMessage(net.kyori.adventure.text.Component.text("Set " + fp.getDisplayName() + " ping to " + (ping < 0 ? "default" : ping), net.kyori.adventure.text.format.NamedTextColor.YELLOW));
    return true;
  }

  private boolean handleMultipleBots(CommandSender sender, int count, int fixedPing, boolean random, boolean reset) {
    List<FakePlayer> bots = new ArrayList<>(plugin.getFakePlayerManager().getActivePlayers());
    if (bots.isEmpty()) {
      sender.sendMessage(net.kyori.adventure.text.Component.text("No bots are available.", net.kyori.adventure.text.format.NamedTextColor.RED));
      return true;
    }
    if (count >= 0) {
      Collections.shuffle(bots, ThreadLocalRandom.current());
      bots = bots.subList(0, Math.min(count, bots.size()));
    }
    for (FakePlayer fp : bots) {
      int ping = reset ? -1 : (random ? randomPing() : fixedPing);
      plugin.getFakePlayerManager().applyPing(fp, ping);
      plugin.getFakePlayerManager().persistBotSettings(fp);
    }
    sender.sendMessage(net.kyori.adventure.text.Component.text("Set ping for " + bots.size() + " bot(s).", net.kyori.adventure.text.format.NamedTextColor.YELLOW));
    return true;
  }

  private static int randomPing() {
    ThreadLocalRandom rng = ThreadLocalRandom.current();
    int roll = rng.nextInt(100);
    if (roll < 60) return rng.nextInt(RANDOM_PING_MIN, 100);
    if (roll < 85) return rng.nextInt(100, 200);
    return rng.nextInt(200, RANDOM_PING_MAX + 1);
  }
}
