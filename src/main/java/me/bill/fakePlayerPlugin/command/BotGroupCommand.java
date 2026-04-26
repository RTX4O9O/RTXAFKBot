package me.bill.fakePlayerPlugin.command;

import java.util.List;
import me.bill.fakePlayerPlugin.fakeplayer.FakePlayer;
import me.bill.fakePlayerPlugin.fakeplayer.FakePlayerManager;
import me.bill.fakePlayerPlugin.lang.Lang;
import me.bill.fakePlayerPlugin.permission.Perm;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.Material;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.Bukkit;

public final class BotGroupCommand implements FppCommand {

  private final FakePlayerManager manager;
  private final BotGroupStore groups;

  public BotGroupCommand(FakePlayerManager manager, BotGroupStore groups) {
    this.manager = manager;
    this.groups = groups;
  }

  @Override public String getName() { return "groups"; }
  @Override public List<String> getAliases() { return List.of("group", "botgroups"); }
  @Override public String getUsage() { return "[create|delete|add|remove|list]"; }
  @Override public String getDescription() { return "Manage personal bot groups."; }
  @Override public String getPermission() { return Perm.SETTINGS; }
  @Override public boolean canUse(CommandSender sender) { return Perm.has(sender, Perm.SETTINGS); }

  @Override
  public boolean execute(CommandSender sender, String[] args) {
    if (!(sender instanceof Player player)) {
      sender.sendMessage(Lang.get("player-only"));
      return true;
    }
    if (args.length == 0 || args[0].equalsIgnoreCase("gui")) {
      openGui(player);
      return true;
    }
    String action = args[0].toLowerCase();
    if (action.equals("list")) {
      player.sendMessage(Component.text("Bot groups: " + String.join(", ", groups.getGroups(player)), NamedTextColor.AQUA));
      return true;
    }
    if (args.length < 2) {
      player.sendMessage(Component.text("Usage: /fpp groups create|delete <group>, add|remove <group> <bot>", NamedTextColor.RED));
      return true;
    }
    String group = args[1];
    switch (action) {
      case "create" -> player.sendMessage(Component.text(groups.create(player, group) ? "Group created." : "Could not create group.", NamedTextColor.YELLOW));
      case "delete" -> player.sendMessage(Component.text(groups.delete(player, group) ? "Group deleted." : "Could not delete group.", NamedTextColor.YELLOW));
      case "add" -> {
        if (args.length < 3) {
          player.sendMessage(Component.text("Usage: /fpp groups add <group> <bot>", NamedTextColor.RED));
          return true;
        }
        FakePlayer fp = manager.getByName(args[2]);
        player.sendMessage(Component.text(groups.add(player, group, fp) ? "Bot added." : "Could not add bot.", NamedTextColor.YELLOW));
      }
      case "remove" -> {
        if (args.length < 3) {
          player.sendMessage(Component.text("Usage: /fpp groups remove <group> <bot>", NamedTextColor.RED));
          return true;
        }
        player.sendMessage(Component.text(groups.remove(player, group, args[2]) ? "Bot removed." : "Could not remove bot.", NamedTextColor.YELLOW));
      }
      default -> player.sendMessage(Component.text("Usage: /fpp groups create|delete|add|remove|list", NamedTextColor.RED));
    }
    return true;
  }

  private void openGui(Player player) {
    Inventory inv = Bukkit.createInventory(null, 54, Component.text("FPP Bot Groups", NamedTextColor.AQUA));
    int slot = 0;
    for (String group : groups.getGroups(player)) {
      ItemStack item = new ItemStack(group.equals(BotGroupStore.DEFAULT_GROUP) ? Material.NETHER_STAR : Material.CHEST);
      ItemMeta meta = item.getItemMeta();
      List<FakePlayer> bots = groups.resolve(player, group);
      meta.displayName(Component.text(group, NamedTextColor.AQUA));
      meta.lore(List.of(Component.text(bots.size() + " bot(s)", NamedTextColor.GRAY), Component.text("Use /fpp groups add/remove to edit", NamedTextColor.YELLOW)));
      item.setItemMeta(meta);
      inv.setItem(slot++, item);
      if (slot >= 54) break;
    }
    player.openInventory(inv);
  }

  @Override
  public List<String> tabComplete(CommandSender sender, String[] args) {
    if (!(sender instanceof Player player)) return List.of();
    if (args.length == 1) return List.of("gui", "list", "create", "delete", "add", "remove").stream().filter(s -> s.startsWith(args[0].toLowerCase())).toList();
    if (args.length == 2 && !args[0].equalsIgnoreCase("create")) return groups.getGroups(player).stream().filter(s -> s.startsWith(args[1].toLowerCase())).toList();
    if (args.length == 3 && (args[0].equalsIgnoreCase("add") || args[0].equalsIgnoreCase("remove"))) return manager.getActiveNames().stream().filter(s -> s.toLowerCase().startsWith(args[2].toLowerCase())).toList();
    return List.of();
  }
}
