package me.bill.fakePlayerPlugin.fppaddon.feature;

import java.util.Comparator;
import me.bill.fakePlayerPlugin.FakePlayerPlugin;
import me.bill.fakePlayerPlugin.api.FppBot;
import me.bill.fakePlayerPlugin.api.FppBotTickHandler;
import me.bill.fakePlayerPlugin.fakeplayer.FakePlayer;
import net.kyori.adventure.text.Component;
import org.bukkit.Material;
import org.bukkit.Tag;
import org.bukkit.block.Block;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.PlayerInventory;

public final class AutoEquipmentHandler implements FppBotTickHandler {

  private final FakePlayerPlugin plugin;

  public AutoEquipmentHandler(FakePlayerPlugin plugin) {
    this.plugin = plugin;
  }

  @Override
  public void onTick(FppBot bot, Player entity) {
    if (entity == null || !entity.isOnline()) return;
    FakePlayer fp = plugin.getFakePlayerManager().getByUuid(bot.getUuid());
    if (fp == null || fp.isBodyless()) return;

    equipBestArmor(entity);
    equipBestTool(entity);
  }

  private static void equipBestArmor(Player bot) {
    PlayerInventory inv = bot.getInventory();
    equipArmorSlot(inv, 36, new Material[] {Material.LEATHER_BOOTS, Material.CHAINMAIL_BOOTS, Material.IRON_BOOTS, Material.DIAMOND_BOOTS, Material.NETHERITE_BOOTS});
    equipArmorSlot(inv, 37, new Material[] {Material.LEATHER_LEGGINGS, Material.CHAINMAIL_LEGGINGS, Material.IRON_LEGGINGS, Material.DIAMOND_LEGGINGS, Material.NETHERITE_LEGGINGS});
    equipArmorSlot(inv, 38, new Material[] {Material.LEATHER_CHESTPLATE, Material.CHAINMAIL_CHESTPLATE, Material.IRON_CHESTPLATE, Material.DIAMOND_CHESTPLATE, Material.NETHERITE_CHESTPLATE, Material.ELYTRA});
    equipArmorSlot(inv, 39, new Material[] {Material.LEATHER_HELMET, Material.CHAINMAIL_HELMET, Material.IRON_HELMET, Material.DIAMOND_HELMET, Material.NETHERITE_HELMET, Material.TURTLE_HELMET});
  }

  private static void equipArmorSlot(PlayerInventory inv, int armorSlot, Material[] order) {
    ItemStack current = inv.getItem(armorSlot);
    Material currentType = current == null ? Material.AIR : current.getType();
    int currentScore = armorScore(currentType, order);
    int bestSlot = -1;
    int bestScore = currentScore;
    for (int slot = 0; slot < 36; slot++) {
      ItemStack item = inv.getItem(slot);
      if (item == null || item.getType() == Material.AIR) continue;
      int score = armorScore(item.getType(), order);
      if (score > bestScore) {
        bestScore = score;
        bestSlot = slot;
      }
    }
    if (bestSlot < 0) return;
    ItemStack swap = inv.getItem(bestSlot);
    inv.setItem(bestSlot, current);
    inv.setItem(armorSlot, swap);
  }

  private static int armorScore(Material type, Material[] order) {
    for (int i = 0; i < order.length; i++) if (order[i] == type) return i + 1;
    return 0;
  }

  private static void equipBestTool(Player bot) {
    Block target = bot.getTargetBlockExact(5);
    if (target == null || target.getType().isAir()) return;
    PlayerInventory inv = bot.getInventory();
    int heldSlot = inv.getHeldItemSlot();
    int bestSlot = heldSlot;
    int bestScore = Integer.MIN_VALUE;
    ToolClass preferred = determineToolClass(target.getType());

    for (int slot = 0; slot < 36; slot++) {
      ItemStack item = inv.getItem(slot);
      int score = toolScore(item, preferred);
      if (score > bestScore) {
        bestScore = score;
        bestSlot = slot;
      }
    }

    if (bestSlot == heldSlot || bestScore <= 0) return;
    if (bestSlot <= 8) inv.setHeldItemSlot(bestSlot);
    else {
      ItemStack held = inv.getItem(heldSlot);
      inv.setItem(heldSlot, inv.getItem(bestSlot));
      inv.setItem(bestSlot, held);
    }
  }

  private static int toolScore(ItemStack item, ToolClass preferred) {
    if (item == null || item.getType() == Material.AIR) return Integer.MIN_VALUE;
    Material type = item.getType();
    ToolClass actual = classifyTool(type);
    if (actual == ToolClass.NONE) return Integer.MIN_VALUE;
    int score = toolTierScore(type);
    if (actual == preferred) score += 10_000;
    else if (preferred == ToolClass.SHEARS && type == Material.SHEARS) score += 10_000;
    else if (preferred == ToolClass.NONE) score += 100;
    else score += 1_000;
    if (type == Material.SHEARS && preferred != ToolClass.SHEARS) score -= 500;
    return score;
  }

  private static ToolClass determineToolClass(Material blockType) {
    if (blockType == Material.COBWEB) return ToolClass.SWORD;
    if (blockType.name().contains("LEAVES")
        || blockType == Material.VINE
        || blockType == Material.GLOW_LICHEN
        || blockType.name().endsWith("_WOOL")) return ToolClass.SHEARS;
    if (Tag.MINEABLE_PICKAXE.isTagged(blockType)) return ToolClass.PICKAXE;
    if (Tag.MINEABLE_AXE.isTagged(blockType)) return ToolClass.AXE;
    if (Tag.MINEABLE_SHOVEL.isTagged(blockType)) return ToolClass.SHOVEL;
    if (Tag.MINEABLE_HOE.isTagged(blockType)) return ToolClass.HOE;
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
}
