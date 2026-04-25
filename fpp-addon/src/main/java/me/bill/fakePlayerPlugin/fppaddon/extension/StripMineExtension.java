package me.bill.fakePlayerPlugin.fppaddon.extension;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import me.bill.fakePlayerPlugin.FakePlayerPlugin;
import me.bill.fakePlayerPlugin.api.FppBot;
import me.bill.fakePlayerPlugin.api.FppCommandExtension;
import me.bill.fakePlayerPlugin.config.Config;
import me.bill.fakePlayerPlugin.fakeplayer.FakePlayer;
import me.bill.fakePlayerPlugin.fakeplayer.PathfindingService;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.block.Block;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.jetbrains.annotations.NotNull;

public final class StripMineExtension implements FppCommandExtension {

  private final FakePlayerPlugin plugin;
  private final Set<UUID> active = ConcurrentHashMap.newKeySet();

  public StripMineExtension(FakePlayerPlugin plugin) {
    this.plugin = plugin;
  }

  @Override public @NotNull String getCommandName() { return "mine"; }

  @Override
  public boolean execute(@NotNull CommandSender sender, @NotNull String[] args) {
    if (!matches(args)) return false;

    ResolvedTargets targets = resolveTargets(sender, args);
    if (targets == null || targets.bots.isEmpty()) {
      sender.sendMessage(Component.text("No eligible bots found.", NamedTextColor.RED));
      return true;
    }

    try {
      int y = Integer.parseInt(targets.yLevel);
      int distance = Math.max(1, Math.min(512, Integer.parseInt(targets.distance)));
      int started = 0;
      for (FakePlayer fp : targets.bots) {
        if (fp.getPlayer() == null || !fp.getPlayer().isOnline() || fp.isBodyless()) continue;
        startStripMine(fp, y, distance);
        started++;
      }
      sender.sendMessage(Component.text("Started stripmine for " + started + " bot(s).", NamedTextColor.YELLOW));
    } catch (NumberFormatException e) {
      sender.sendMessage(Component.text("Invalid y-level or distance.", NamedTextColor.RED));
    }
    return true;
  }

  @Override
  public @NotNull List<String> tabComplete(@NotNull CommandSender sender, @NotNull String[] args) {
    if (args.length == 1) {
      String prefix = args[0].toLowerCase();
      List<String> out = new ArrayList<>();
      if ("--group".startsWith(prefix)) out.add("--group");
      if ("all".startsWith(prefix)) out.add("all");
      for (FppBot bot : apiBots(sender)) if (bot.getName().toLowerCase().startsWith(prefix)) out.add(bot.getName());
      return out;
    }
    if (args.length == 2 && "--group".equalsIgnoreCase(args[0])) {
      if (!(sender instanceof Player player) || plugin.getBotGroupStore() == null) return List.of();
      return plugin.getBotGroupStore().getGroups(player).stream().filter(g -> g.toLowerCase().startsWith(args[1].toLowerCase())).toList();
    }
    if (args.length == 2) return List.of("--stop");
    if (args.length == 3 || args.length == 4) return List.of("64", "128", "256");
    return List.of();
  }

  public void shutdown() {
    for (UUID uuid : new ArrayList<>(active)) plugin.getPathfindingService().cancel(uuid);
    active.clear();
  }

  private boolean matches(String[] args) {
    if (args.length < 4) return false;
    return args[0].equalsIgnoreCase("--group") ? args.length >= 5 && isStripmineToken(args[2]) : isStripmineToken(args[1]);
  }

  private boolean isStripmineToken(String token) {
    return token.equalsIgnoreCase("--stripmine") || token.equalsIgnoreCase("stripmine");
  }

  private ResolvedTargets resolveTargets(CommandSender sender, String[] args) {
    if (args[0].equalsIgnoreCase("--group")) {
      if (!(sender instanceof Player player) || plugin.getBotGroupStore() == null || args.length < 5) return null;
      return new ResolvedTargets(plugin.getBotGroupStore().resolve(player, args[1]), args[3], args[4]);
    }
    if (args[0].equalsIgnoreCase("all")) {
      List<FakePlayer> bots = new ArrayList<>();
      for (FppBot bot : apiBots(sender)) {
        FakePlayer fp = plugin.getFakePlayerManager().getByUuid(bot.getUuid());
        if (fp != null) bots.add(fp);
      }
      return new ResolvedTargets(bots, args[2], args[3]);
    }
    return plugin.getFppApi().getBot(args[0]).map(bot -> {
      if (sender instanceof Player player && !plugin.getFppApi().canControlBot(player, bot)) return null;
      FakePlayer fp = plugin.getFakePlayerManager().getByUuid(bot.getUuid());
      return fp == null ? null : new ResolvedTargets(List.of(fp), args[2], args[3]);
    }).orElse(null);
  }

  private List<FppBot> apiBots(CommandSender sender) {
    if (sender instanceof Player player) return plugin.getFppApi().getBotsControllableBy(player).stream().toList();
    return plugin.getFppApi().getBots().stream().toList();
  }

  private void startStripMine(FakePlayer fp, int yLevel, int distance) {
    Player bot = fp.getPlayer();
    if (bot == null || !bot.isOnline() || fp.isBodyless()) return;
    active.add(fp.getUuid());
    List<Location> targets = new ArrayList<>();
    Location start = bot.getLocation().clone();
    start.setY(yLevel);
    float yaw = bot.getLocation().getYaw();
    double rad = Math.toRadians(yaw);
    int dx = (int) Math.round(-Math.sin(rad));
    int dz = (int) Math.round(Math.cos(rad));
    if (dx == 0 && dz == 0) dz = 1;
    for (int i = 1; i <= distance; i++) targets.add(new Location(bot.getWorld(), start.getBlockX() + dx * i, yLevel, start.getBlockZ() + dz * i));
    runStripStep(fp, targets, 0);
  }

  private void runStripStep(FakePlayer fp, List<Location> targets, int idx) {
    if (idx >= targets.size()) { active.remove(fp.getUuid()); return; }
    Player bot = fp.getPlayer();
    if (bot == null || !bot.isOnline() || fp.isBodyless()) { active.remove(fp.getUuid()); return; }

    Location target = targets.get(idx);
    plugin.getPathfindingService().navigate(fp, new PathfindingService.NavigationRequest(
        PathfindingService.Owner.MINE,
        () -> target,
        Config.pathfindingArrivalDistance(),
        0.0,
        10,
        () -> {
          Block block = target.getBlock();
          if (!block.getType().isAir()) {
            equipBestTool(bot, block);
            block.breakNaturally(bot.getInventory().getItemInMainHand());
          }
          runStripStep(fp, targets, idx + 1);
        },
        () -> runStripStep(fp, targets, idx + 1),
        () -> runStripStep(fp, targets, idx + 1)));
  }

  private static void equipBestTool(Player bot, Block block) {
    if (bot == null || block == null) return;
    ToolClass preferred = determineToolClass(block.getType());
    var inv = bot.getInventory();
    int heldSlot = inv.getHeldItemSlot();
    int bestSlot = heldSlot;
    int bestScore = Integer.MIN_VALUE;

    for (int slot = 0; slot < 36; slot++) {
      var item = inv.getItem(slot);
      int score = toolScore(item, preferred);
      if (score > bestScore) {
        bestScore = score;
        bestSlot = slot;
      }
    }

    if (bestSlot == heldSlot || bestScore <= 0) return;
    if (bestSlot <= 8) inv.setHeldItemSlot(bestSlot);
    else {
      var held = inv.getItem(heldSlot);
      inv.setItem(heldSlot, inv.getItem(bestSlot));
      inv.setItem(bestSlot, held);
    }
  }

  private static int toolScore(org.bukkit.inventory.ItemStack item, ToolClass preferred) {
    if (item == null || item.getType() == Material.AIR) return Integer.MIN_VALUE;
    Material type = item.getType();
    ToolClass actual = classifyTool(type);
    if (actual == ToolClass.NONE) return Integer.MIN_VALUE;
    int score = toolTierScore(type);
    if (actual == preferred) score += 10_000;
    else score += 1_000;
    return score;
  }

  private static ToolClass determineToolClass(Material blockType) {
    if (blockType == Material.COBWEB) return ToolClass.SWORD;
    if (blockType.name().contains("LEAVES")
        || blockType == Material.VINE
        || blockType == Material.GLOW_LICHEN
        || blockType.name().endsWith("_WOOL")) return ToolClass.SHEARS;
    if (org.bukkit.Tag.MINEABLE_PICKAXE.isTagged(blockType)) return ToolClass.PICKAXE;
    if (org.bukkit.Tag.MINEABLE_AXE.isTagged(blockType)) return ToolClass.AXE;
    if (org.bukkit.Tag.MINEABLE_SHOVEL.isTagged(blockType)) return ToolClass.SHOVEL;
    if (org.bukkit.Tag.MINEABLE_HOE.isTagged(blockType)) return ToolClass.HOE;
    return ToolClass.NONE;
  }

  private static ToolClass classifyTool(Material toolType) {
    String name = toolType.name();
    if (toolType == Material.SHEARS) return ToolClass.SHEARS;
    if (name.endsWith("_PICKAXE")) return ToolClass.PICKAXE;
    if (name.endsWith("_AXE")) return ToolClass.AXE;
    if (name.endsWith("_SHOVEL")) return ToolClass.SHOVEL;
    if (name.endsWith("_HOE")) return ToolClass.HOE;
    if (name.endsWith("_SWORD")) return ToolClass.SWORD;
    return ToolClass.NONE;
  }

  private static int toolTierScore(Material toolType) {
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

  private enum ToolClass { PICKAXE, AXE, SHOVEL, HOE, SHEARS, SWORD, NONE }

  private record ResolvedTargets(List<FakePlayer> bots, String yLevel, String distance) {}
}
