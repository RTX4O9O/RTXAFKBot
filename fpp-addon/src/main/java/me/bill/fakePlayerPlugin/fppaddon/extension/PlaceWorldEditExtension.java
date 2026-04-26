package me.bill.fakePlayerPlugin.fppaddon.extension;

import java.util.ArrayList;
import java.util.List;
import me.bill.fakePlayerPlugin.FakePlayerPlugin;
import me.bill.fakePlayerPlugin.api.FppCommandExtension;
import me.bill.fakePlayerPlugin.api.FppBot;
import me.bill.fakePlayerPlugin.fakeplayer.FakePlayer;
import me.bill.fakePlayerPlugin.util.WorldEditHelper;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.jetbrains.annotations.NotNull;

public final class PlaceWorldEditExtension implements FppCommandExtension {

  private final FakePlayerPlugin plugin;

  public PlaceWorldEditExtension(FakePlayerPlugin plugin) {
    this.plugin = plugin;
  }

  @Override public @NotNull String getCommandName() { return "place"; }

  @Override
  public boolean execute(@NotNull CommandSender sender, @NotNull String[] args) {
    if (!matches(args)) return false;
    if (!(sender instanceof Player player)) { sender.sendMessage(Component.text("Player only.", NamedTextColor.RED)); return true; }
    if (!plugin.isWorldEditAvailable()) { sender.sendMessage(Component.text("WorldEdit not available.", NamedTextColor.RED)); return true; }

    var corners = WorldEditHelper.getSelection(player);
    if (corners == null) { sender.sendMessage(Component.text("No WorldEdit selection.", NamedTextColor.RED)); return true; }

    List<FakePlayer> bots = resolveTargets(sender, args);
    if (bots.isEmpty()) { sender.sendMessage(Component.text("No eligible bots found.", NamedTextColor.RED)); return true; }

    for (FakePlayer fp : bots) {
      if (fp.getPlayer() == null || !fp.getPlayer().isOnline() || fp.isBodyless()) continue;
      plugin.getPlaceCommand().applyWorldEditSelection(fp, corners[0], corners[1], sender);
    }
    sender.sendMessage(Component.text("Applied WorldEdit selection to " + bots.size() + " bot(s).", NamedTextColor.YELLOW));
    return true;
  }

  @Override
  public @NotNull List<String> tabComplete(@NotNull CommandSender sender, @NotNull String[] args) {
    if (args.length == 2 && "--wesel".startsWith(args[1].toLowerCase())) return List.of("--wesel");
    return List.of();
  }

  private boolean matches(String[] args) {
    if (args.length < 2) return false;
    if (args[0].equalsIgnoreCase("--group")) return args.length >= 3 && args[2].equalsIgnoreCase("--wesel");
    return args[1].equalsIgnoreCase("--wesel");
  }

  private List<FakePlayer> resolveTargets(CommandSender sender, String[] args) {
    if (args[0].equalsIgnoreCase("--group")) {
      if (!(sender instanceof Player player) || plugin.getBotGroupStore() == null || args.length < 3) return List.of();
      return plugin.getBotGroupStore().resolve(player, args[1]);
    }
    if (args[0].equalsIgnoreCase("all")) {
      List<FakePlayer> bots = new ArrayList<>();
      List<FppBot> source = sender instanceof Player player ? plugin.getFppApi().getBotsControllableBy(player).stream().toList() : plugin.getFppApi().getBots().stream().toList();
      for (FppBot bot : source) {
        FakePlayer fp = plugin.getFakePlayerManager().getByUuid(bot.getUuid());
        if (fp != null) bots.add(fp);
      }
      return bots;
    }
    return plugin.getFppApi().getBot(args[0]).map(bot -> {
      FakePlayer fp = plugin.getFakePlayerManager().getByUuid(bot.getUuid());
      return fp == null ? List.<FakePlayer>of() : List.of(fp);
    }).orElse(List.of());
  }
}
