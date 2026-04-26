package me.bill.fakePlayerPlugin.fppaddon.command;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import me.bill.fakePlayerPlugin.FakePlayerPlugin;
import me.bill.fakePlayerPlugin.api.FppAddonCommand;
import me.bill.fakePlayerPlugin.api.FppBot;
import me.bill.fakePlayerPlugin.config.Config;
import me.bill.fakePlayerPlugin.fakeplayer.FakePlayer;
import me.bill.fakePlayerPlugin.fakeplayer.NmsPlayerSpawner;
import me.bill.fakePlayerPlugin.fakeplayer.PathfindingService;
import me.bill.fakePlayerPlugin.permission.Perm;
import me.bill.fakePlayerPlugin.util.FppScheduler;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.block.Block;
import org.bukkit.block.data.Ageable;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.PlayerDeathEvent;
import org.jetbrains.annotations.NotNull;

public final class FarmCommand implements FppAddonCommand, Listener {

  private static final Set<Material> CROPS =
      Set.of(Material.WHEAT, Material.CARROTS, Material.POTATOES, Material.BEETROOTS, Material.NETHER_WART);

  private final FakePlayerPlugin plugin;
  private final PathfindingService pathfinding;
  private final Map<UUID, Integer> tasks = new ConcurrentHashMap<>();

  public FarmCommand(FakePlayerPlugin plugin) {
    this.plugin = plugin;
    this.pathfinding = plugin.getPathfindingService();
  }

  @Override public @NotNull String getName() { return "farm"; }
  @Override public @NotNull String getUsage() { return "<bot|all|--group <group>> [radius|--stop]"; }
  @Override public @NotNull String getDescription() { return "Harvest and replant nearby mature crops."; }
  @Override public @NotNull String getPermission() { return Perm.FARM; }

  @Override
  public boolean execute(@NotNull CommandSender sender, @NotNull String[] args) {
    if (args.length == 0) {
      sender.sendMessage(Component.text("Usage: /fpp farm " + getUsage(), NamedTextColor.RED));
      return true;
    }

    ResolvedTargets targets = resolveTargets(sender, args);
    if (targets == null || targets.bots.isEmpty()) {
      sender.sendMessage(Component.text("No eligible bots found.", NamedTextColor.RED));
      return true;
    }

    if (targets.rest.length > 0 && targets.rest[0].equalsIgnoreCase("--stop")) {
      for (FakePlayer fp : targets.bots) stopFarming(fp.getUuid());
      sender.sendMessage(Component.text("Stopped farming for " + targets.bots.size() + " bot(s).", NamedTextColor.YELLOW));
      return true;
    }

    int radius = 12;
    if (targets.rest.length > 0) {
      try { radius = Math.max(2, Math.min(48, Integer.parseInt(targets.rest[0]))); } catch (NumberFormatException ignored) {}
    }

    int started = 0;
    for (FakePlayer fp : targets.bots) {
      if (fp.getPlayer() == null || !fp.getPlayer().isOnline() || fp.isBodyless()) continue;
      startFarming(fp, radius);
      started++;
    }

    sender.sendMessage(Component.text("Started farming for " + started + " bot(s).", NamedTextColor.YELLOW));
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
      String prefix = args[1].toLowerCase();
      return plugin.getBotGroupStore().getGroups(player).stream().filter(g -> g.toLowerCase().startsWith(prefix)).toList();
    }
    if (args.length == 2) return List.of("8", "12", "24", "--stop");
    return List.of();
  }

  public void shutdown() {
    for (Integer id : new ArrayList<>(tasks.values())) FppScheduler.cancelTask(id);
    tasks.clear();
  }

  @EventHandler(priority = EventPriority.MONITOR)
  public void onBotDeath(PlayerDeathEvent event) {
    FakePlayer fp = plugin.getFakePlayerManager().getByEntity(event.getEntity());
    if (fp != null) stopFarming(fp.getUuid());
  }

  private void startFarming(FakePlayer fp, int radius) {
    stopFarming(fp.getUuid());
    int id = FppScheduler.runAtEntityRepeatingWithId(plugin, fp.getPlayer(), () -> tick(fp, radius), 1L, 20L);
    tasks.put(fp.getUuid(), id);
  }

  private void tick(FakePlayer fp, int radius) {
    Player bot = fp.getPlayer();
    if (bot == null || !bot.isOnline() || fp.isBodyless()) { stopFarming(fp.getUuid()); return; }
    Block crop = nearestMatureCrop(bot.getLocation(), radius);
    if (crop == null) return;
    Location dest = crop.getLocation().add(0.5, 0, 0.5);
    if (bot.getLocation().distanceSquared(dest) > 6.25) {
      pathfinding.navigate(fp, new PathfindingService.NavigationRequest(PathfindingService.Owner.SYSTEM, () -> dest, Config.pathfindingArrivalDistance(), 0.0, 5, () -> harvest(bot, crop), null, null));
    } else {
      harvest(bot, crop);
    }
  }

  private Block nearestMatureCrop(Location origin, int radius) {
    if (origin.getWorld() == null) return null;
    Block best = null;
    double bestSq = Double.MAX_VALUE;
    for (int x = -radius; x <= radius; x++) for (int y = -3; y <= 3; y++) for (int z = -radius; z <= radius; z++) {
      Block block = origin.getWorld().getBlockAt(origin.getBlockX() + x, origin.getBlockY() + y, origin.getBlockZ() + z);
      if (!CROPS.contains(block.getType()) || !(block.getBlockData() instanceof Ageable age) || age.getAge() < age.getMaximumAge()) continue;
      double dist = block.getLocation().distanceSquared(origin);
      if (dist < bestSq) { bestSq = dist; best = block; }
    }
    return best;
  }

  private void harvest(Player bot, Block crop) {
    if (!(crop.getBlockData() instanceof Ageable age) || age.getAge() < age.getMaximumAge()) return;
    Material type = crop.getType();
    equipBestTool(bot, crop);
    bot.swingMainHand();
    crop.breakNaturally(bot.getInventory().getItemInMainHand());
    FppScheduler.runAtLocation(plugin, crop.getLocation(), () -> {
      crop.setType(type);
      if (crop.getBlockData() instanceof Ageable replanted) { replanted.setAge(0); crop.setBlockData(replanted); }
    });
    NmsPlayerSpawner.setMovementForward(bot, 0f);
  }

  private void stopFarming(UUID uuid) {
    Integer id = tasks.remove(uuid);
    if (id != null) FppScheduler.cancelTask(id);
    if (pathfinding != null) pathfinding.cancel(uuid);
  }

  private static void equipBestTool(Player bot, Block block) {
    if (bot == null || block == null) return;
    ToolClass preferred = determineToolClass(block.getType());
    var inv = bot.getInventory();
    int heldSlot = inv.getHeldItemSlot();
    int bestSlot = -1;
    int bestScore = Integer.MIN_VALUE;

    for (int slot = 0; slot < 36; slot++) {
      var item = inv.getItem(slot);
      int score = toolScore(item, preferred);
      if (score > bestScore) {
        bestScore = score;
        bestSlot = slot;
      }
    }

    if (bestSlot < 0 || bestScore <= 0 || bestSlot == heldSlot) return;
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

  private List<FppBot> apiBots(CommandSender sender) {
    if (sender instanceof Player player) return plugin.getFppApi().getBotsControllableBy(player).stream().toList();
    return plugin.getFppApi().getBots().stream().toList();
  }

  private ResolvedTargets resolveTargets(CommandSender sender, String[] args) {
    if (args[0].equalsIgnoreCase("--group")) {
      if (!(sender instanceof Player player) || plugin.getBotGroupStore() == null || args.length < 2) return null;
      List<FakePlayer> bots = plugin.getBotGroupStore().resolve(player, args[1]);
      return new ResolvedTargets(bots, slice(args, 2));
    }
    if (args[0].equalsIgnoreCase("all")) {
      List<FakePlayer> bots = new ArrayList<>();
      for (FppBot bot : apiBots(sender)) {
        FakePlayer fp = plugin.getFakePlayerManager().getByUuid(bot.getUuid());
        if (fp != null) bots.add(fp);
      }
      return new ResolvedTargets(bots, slice(args, 1));
    }
    return plugin.getFppApi().getBot(args[0]).map(bot -> {
      if (sender instanceof Player player && !plugin.getFppApi().canControlBot(player, bot)) return null;
      FakePlayer fp = plugin.getFakePlayerManager().getByUuid(bot.getUuid());
      return fp == null ? null : new ResolvedTargets(List.of(fp), slice(args, 1));
    }).orElse(null);
  }

  private static String[] slice(String[] args, int start) {
    if (start >= args.length) return new String[0];
    String[] out = new String[args.length - start];
    System.arraycopy(args, start, out, 0, out.length);
    return out;
  }

  private record ResolvedTargets(List<FakePlayer> bots, String[] rest) {}
}
