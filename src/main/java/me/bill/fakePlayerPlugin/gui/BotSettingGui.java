package me.bill.fakePlayerPlugin.gui;

import io.papermc.paper.event.player.AsyncChatEvent;
import java.util.*;
import me.bill.fakePlayerPlugin.FakePlayerPlugin;
import me.bill.fakePlayerPlugin.config.Config;
import me.bill.fakePlayerPlugin.fakeplayer.FakePlayer;
import me.bill.fakePlayerPlugin.fakeplayer.FakePlayerManager;
import me.bill.fakePlayerPlugin.fakeplayer.NmsPlayerSpawner;
import me.bill.fakePlayerPlugin.lang.Lang;
import me.bill.fakePlayerPlugin.permission.Perm;
import me.bill.fakePlayerPlugin.util.BotRenameHelper;
import me.bill.fakePlayerPlugin.util.BotAccess;
import me.bill.fakePlayerPlugin.util.FppScheduler;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.format.TextColor;
import net.kyori.adventure.text.format.TextDecoration;
import net.kyori.adventure.text.serializer.plain.PlainTextComponentSerializer;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.Sound;
import org.bukkit.SoundCategory;
import org.bukkit.World;
import org.bukkit.enchantments.Enchantment;
import org.bukkit.entity.EntityType;
import org.bukkit.entity.ExperienceOrb;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.PlayerDeathEvent;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.InventoryCloseEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.InventoryHolder;
import org.bukkit.inventory.ItemFlag;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.jetbrains.annotations.NotNull;
import me.bill.fakePlayerPlugin.api.event.FppBotDespawnEvent;

public final class BotSettingGui implements Listener {

  private static final TextColor ACCENT = TextColor.fromHexString("#0079FF");
  private static final TextColor ON_GREEN = TextColor.fromHexString("#66CC66");
  private static final TextColor OFF_RED = NamedTextColor.RED;
  private static final TextColor VALUE_YELLOW = TextColor.fromHexString("#FFDD57");
  private static final TextColor YELLOW = NamedTextColor.YELLOW;
  private static final TextColor GRAY = NamedTextColor.GRAY;
  private static final TextColor DARK_GRAY = NamedTextColor.DARK_GRAY;
  private static final TextColor WHITE = NamedTextColor.WHITE;
  private static final TextColor DANGER_RED = TextColor.fromHexString("#FF4444");
  private static final TextColor COMING_SOON_COLOR = TextColor.fromHexString("#FFA500");
  private static final TextColor SELECTED_GREEN = TextColor.fromHexString("#55FF55");

  private static final int SIZE = 54;
  private static final int SETTINGS_PER_PAGE = 45;
  private static final int SLOT_RESET = 45;
  private static final int SLOT_CAT_PREV = 46;
  private static final int SLOT_CAT_NEXT = 52;
  private static final int SLOT_CLOSE = 53;
  private static final int CAT_WINDOW = 5;
  private static final int CAT_WINDOW_START = 47;

  private static final int MOB_GUI_SIZE = 54;
  private static final int MOB_SLOTS = 45;
  private static final int MOB_SLOT_BACK = 45;
  private static final int MOB_SLOT_PREV_PAGE = 46;
  private static final int MOB_SLOT_CLEAR = 49;
  private static final int MOB_SLOT_NEXT_PAGE = 52;
  private static final int MOB_SLOT_CLOSE = 53;

  private static final List<MobDisplay> MOB_LIST;

  static {
    List<MobDisplay> list = new ArrayList<>();

    list.add(new MobDisplay(EntityType.ZOMBIE, Material.ZOMBIE_HEAD, "ᴢᴏᴍʙɪᴇ", "ʜᴏꜱᴛɪʟᴇ"));
    list.add(new MobDisplay(EntityType.SKELETON, Material.SKELETON_SKULL, "ꜱᴋᴇʟᴇᴛᴏɴ", "ʜᴏꜱᴛɪʟᴇ"));
    list.add(new MobDisplay(EntityType.CREEPER, Material.CREEPER_HEAD, "ᴄʀᴇᴇᴘᴇʀ", "ʜᴏꜱᴛɪʟᴇ"));
    list.add(new MobDisplay(EntityType.SPIDER, Material.SPIDER_EYE, "ꜱᴘɪᴅᴇʀ", "ʜᴏꜱᴛɪʟᴇ"));
    list.add(
        new MobDisplay(
            EntityType.CAVE_SPIDER, Material.FERMENTED_SPIDER_EYE, "ᴄᴀᴠᴇ ꜱᴘɪᴅᴇʀ", "ʜᴏꜱᴛɪʟᴇ"));
    list.add(new MobDisplay(EntityType.ENDERMAN, Material.ENDER_PEARL, "ᴇɴᴅᴇʀᴍᴀɴ", "ɴᴇᴜᴛʀᴀʟ"));
    list.add(new MobDisplay(EntityType.WITCH, Material.SPLASH_POTION, "ᴡɪᴛᴄʜ", "ʜᴏꜱᴛɪʟᴇ"));
    list.add(new MobDisplay(EntityType.PILLAGER, Material.CROSSBOW, "ᴘɪʟʟᴀɡᴇʀ", "ʜᴏꜱᴛɪʟᴇ"));
    list.add(new MobDisplay(EntityType.VINDICATOR, Material.IRON_AXE, "ᴠɪɴᴅɪᴄᴀᴛᴏʀ", "ʜᴏꜱᴛɪʟᴇ"));
    list.add(new MobDisplay(EntityType.EVOKER, Material.TOTEM_OF_UNDYING, "ᴇᴠᴏᴋᴇʀ", "ʜᴏꜱᴛɪʟᴇ"));
    list.add(new MobDisplay(EntityType.RAVAGER, Material.SADDLE, "ʀᴀᴠᴀɡᴇʀ", "ʜᴏꜱᴛɪʟᴇ"));
    list.add(new MobDisplay(EntityType.VEX, Material.IRON_SWORD, "ᴠᴇx", "ʜᴏꜱᴛɪʟᴇ"));
    list.add(new MobDisplay(EntityType.PHANTOM, Material.PHANTOM_MEMBRANE, "ᴘʜᴀɴᴛᴏᴍ", "ʜᴏꜱᴛɪʟᴇ"));
    list.add(new MobDisplay(EntityType.DROWNED, Material.TRIDENT, "ᴅʀᴏᴡɴᴇᴅ", "ʜᴏꜱᴛɪʟᴇ"));
    list.add(new MobDisplay(EntityType.HUSK, Material.SAND, "ʜᴜꜱᴋ", "ʜᴏꜱᴛɪʟᴇ"));
    list.add(new MobDisplay(EntityType.STRAY, Material.ARROW, "ꜱᴛʀᴀʏ", "ʜᴏꜱᴛɪʟᴇ"));
    list.add(new MobDisplay(EntityType.BLAZE, Material.BLAZE_ROD, "ʙʟᴀᴢᴇ", "ʜᴏꜱᴛɪʟᴇ"));
    list.add(new MobDisplay(EntityType.GHAST, Material.GHAST_TEAR, "ɢʜᴀꜱᴛ", "ʜᴏꜱᴛɪʟᴇ"));
    list.add(new MobDisplay(EntityType.MAGMA_CUBE, Material.MAGMA_CREAM, "ᴍᴀɡᴍᴀ ᴄᴜʙᴇ", "ʜᴏꜱᴛɪʟᴇ"));
    list.add(new MobDisplay(EntityType.SLIME, Material.SLIME_BALL, "ꜱʟɪᴍᴇ", "ʜᴏꜱᴛɪʟᴇ"));
    list.add(new MobDisplay(EntityType.HOGLIN, Material.COOKED_PORKCHOP, "ʜᴏɢʟɪɴ", "ʜᴏꜱᴛɪʟᴇ"));
    list.add(
        new MobDisplay(EntityType.PIGLIN_BRUTE, Material.GOLDEN_AXE, "ᴘɪɡʟɪɴ ʙʀᴜᴛᴇ", "ʜᴏꜱᴛɪʟᴇ"));
    list.add(new MobDisplay(EntityType.WARDEN, Material.SCULK_SHRIEKER, "ᴡᴀʀᴅᴇɴ", "ʜᴏꜱᴛɪʟᴇ"));
    list.add(
        new MobDisplay(
            EntityType.WITHER_SKELETON,
            Material.WITHER_SKELETON_SKULL,
            "ᴡɪᴛʜᴇʀ ꜱᴋᴇʟᴇᴛᴏɴ",
            "ʜᴏꜱᴛɪʟᴇ"));
    list.add(new MobDisplay(EntityType.GUARDIAN, Material.PRISMARINE_SHARD, "ɢᴜᴀʀᴅɪᴀɴ", "ʜᴏꜱᴛɪʟᴇ"));
    list.add(
        new MobDisplay(
            EntityType.ELDER_GUARDIAN, Material.PRISMARINE_CRYSTALS, "ᴇʟᴅᴇʀ ɢᴜᴀʀᴅɪᴀɴ", "ʜᴏꜱᴛɪʟᴇ"));
    list.add(new MobDisplay(EntityType.SHULKER, Material.SHULKER_SHELL, "ꜱʜᴜʟᴋᴇʀ", "ʜᴏꜱᴛɪʟᴇ"));
    list.add(new MobDisplay(EntityType.SILVERFISH, Material.STONE_BRICKS, "ꜱɪʟᴠᴇʀꜰɪꜱʜ", "ʜᴏꜱᴛɪʟᴇ"));
    list.add(new MobDisplay(EntityType.ENDERMITE, Material.ENDER_EYE, "ᴇɴᴅᴇʀᴍɪᴛᴇ", "ʜᴏꜱᴛɪʟᴇ"));
    list.add(new MobDisplay(EntityType.BREEZE, Material.WIND_CHARGE, "ʙʀᴇᴇᴢᴇ", "ʜᴏꜱᴛɪʟᴇ"));
    list.add(new MobDisplay(EntityType.BOGGED, Material.POISONOUS_POTATO, "ʙᴏɢɢᴇᴅ", "ʜᴏꜱᴛɪʟᴇ"));

    list.add(
        new MobDisplay(
            EntityType.ZOMBIFIED_PIGLIN, Material.GOLD_NUGGET, "ᴢᴏᴍʙɪꜰɪᴇᴅ ᴘɪɡʟɪɴ", "ɴᴇᴜᴛʀᴀʟ"));
    list.add(new MobDisplay(EntityType.PIGLIN, Material.GOLD_INGOT, "ᴘɪɡʟɪɴ", "ɴᴇᴜᴛʀᴀʟ"));
    list.add(new MobDisplay(EntityType.WOLF, Material.BONE, "ᴡᴏʟꜰ", "ɴᴇᴜᴛʀᴀʟ"));
    list.add(new MobDisplay(EntityType.IRON_GOLEM, Material.IRON_BLOCK, "ɪʀᴏɴ ɢᴏʟᴇᴍ", "ɴᴇᴜᴛʀᴀʟ"));
    list.add(new MobDisplay(EntityType.BEE, Material.HONEYCOMB, "ʙᴇᴇ", "ɴᴇᴜᴛʀᴀʟ"));
    list.add(new MobDisplay(EntityType.POLAR_BEAR, Material.COD, "ᴘᴏʟᴀʀ ʙᴇᴀʀ", "ɴᴇᴜᴛʀᴀʟ"));
    list.add(new MobDisplay(EntityType.LLAMA, Material.LEAD, "ʟʟᴀᴍᴀ", "ɴᴇᴜᴛʀᴀʟ"));
    list.add(new MobDisplay(EntityType.DOLPHIN, Material.HEART_OF_THE_SEA, "ᴅᴏʟᴘʜɪɴ", "ɴᴇᴜᴛʀᴀʟ"));
    list.add(new MobDisplay(EntityType.GOAT, Material.WHEAT, "ɢᴏᴀᴛ", "ɴᴇᴜᴛʀᴀʟ"));
    list.add(new MobDisplay(EntityType.PANDA, Material.BAMBOO, "ᴘᴀɴᴅᴀ", "ɴᴇᴜᴛʀᴀʟ"));
    list.add(new MobDisplay(EntityType.TRADER_LLAMA, Material.LEAD, "ᴛʀᴀᴅᴇʀ ʟʟᴀᴍᴀ", "ɴᴇᴜᴛʀᴀʟ"));

    list.add(new MobDisplay(EntityType.ENDER_DRAGON, Material.DRAGON_HEAD, "ᴇɴᴅᴇʀ ᴅʀᴀɡᴏɴ", "ʙᴏꜱꜱ"));
    list.add(new MobDisplay(EntityType.WITHER, Material.NETHER_STAR, "ᴡɪᴛʜᴇʀ", "ʙᴏꜱꜱ"));

    list.add(new MobDisplay(EntityType.COW, Material.BEEF, "ᴄᴏᴡ", "ᴘᴀꜱꜱɪᴠᴇ"));
    list.add(new MobDisplay(EntityType.PIG, Material.PORKCHOP, "ᴘɪɡ", "ᴘᴀꜱꜱɪᴠᴇ"));
    list.add(new MobDisplay(EntityType.SHEEP, Material.WHITE_WOOL, "ꜱʜᴇᴇᴘ", "ᴘᴀꜱꜱɪᴠᴇ"));
    list.add(new MobDisplay(EntityType.CHICKEN, Material.FEATHER, "ᴄʜɪᴄᴋᴇɴ", "ᴘᴀꜱꜱɪᴠᴇ"));
    list.add(new MobDisplay(EntityType.RABBIT, Material.RABBIT_FOOT, "ʀᴀʙʙɪᴛ", "ᴘᴀꜱꜱɪᴠᴇ"));
    list.add(new MobDisplay(EntityType.SQUID, Material.INK_SAC, "ꜱQᴜɪᴅ", "ᴘᴀꜱꜱɪᴠᴇ"));
    list.add(new MobDisplay(EntityType.GLOW_SQUID, Material.GLOW_INK_SAC, "ɢʟᴏᴡ ꜱQᴜɪᴅ", "ᴘᴀꜱꜱɪᴠᴇ"));
    list.add(new MobDisplay(EntityType.TURTLE, Material.TURTLE_EGG, "ᴛᴜʀᴛʟᴇ", "ᴘᴀꜱꜱɪᴠᴇ"));
    list.add(new MobDisplay(EntityType.COD, Material.COD, "ᴄᴏᴅ", "ᴘᴀꜱꜱɪᴠᴇ"));
    list.add(new MobDisplay(EntityType.SALMON, Material.SALMON, "ꜱᴀʟᴍᴏɴ", "ᴘᴀꜱꜱɪᴠᴇ"));
    list.add(
        new MobDisplay(
            EntityType.TROPICAL_FISH, Material.TROPICAL_FISH, "ᴛʀᴏᴘɪᴄᴀʟ ꜰɪꜱʜ", "ᴘᴀꜱꜱɪᴠᴇ"));
    list.add(new MobDisplay(EntityType.PUFFERFISH, Material.PUFFERFISH, "ᴘᴜꜰꜰᴇʀꜰɪꜱʜ", "ᴘᴀꜱꜱɪᴠᴇ"));
    list.add(new MobDisplay(EntityType.VILLAGER, Material.EMERALD, "ᴠɪʟʟᴀɡᴇʀ", "ᴘᴀꜱꜱɪᴠᴇ"));
    list.add(
        new MobDisplay(
            EntityType.WANDERING_TRADER, Material.EMERALD_BLOCK, "ᴡᴀɴᴅᴇʀɪɴɢ ᴛʀᴀᴅᴇʀ", "ᴘᴀꜱꜱɪᴠᴇ"));
    list.add(new MobDisplay(EntityType.HORSE, Material.GOLDEN_APPLE, "ʜᴏʀꜱᴇ", "ᴘᴀꜱꜱɪᴠᴇ"));
    list.add(new MobDisplay(EntityType.DONKEY, Material.CHEST, "ᴅᴏɴᴋᴇʏ", "ᴘᴀꜱꜱɪᴠᴇ"));
    list.add(new MobDisplay(EntityType.MULE, Material.CHEST, "ᴍᴜʟᴇ", "ᴘᴀꜱꜱɪᴠᴇ"));
    list.add(new MobDisplay(EntityType.CAT, Material.STRING, "ᴄᴀᴛ", "ᴘᴀꜱꜱɪᴠᴇ"));
    list.add(new MobDisplay(EntityType.PARROT, Material.COOKIE, "ᴘᴀʀʀᴏᴛ", "ᴘᴀꜱꜱɪᴠᴇ"));
    list.add(new MobDisplay(EntityType.FOX, Material.SWEET_BERRIES, "ꜰᴏx", "ᴘᴀꜱꜱɪᴠᴇ"));
    list.add(new MobDisplay(EntityType.OCELOT, Material.COD, "ᴏᴄᴇʟᴏᴛ", "ᴘᴀꜱꜱɪᴠᴇ"));
    list.add(new MobDisplay(EntityType.AXOLOTL, Material.AXOLOTL_BUCKET, "ᴀxᴏʟᴏᴛʟ", "ᴘᴀꜱꜱɪᴠᴇ"));
    list.add(new MobDisplay(EntityType.FROG, Material.SLIME_BALL, "ꜰʀᴏɢ", "ᴘᴀꜱꜱɪᴠᴇ"));
    list.add(new MobDisplay(EntityType.TADPOLE, Material.TADPOLE_BUCKET, "ᴛᴀᴅᴘᴏʟᴇ", "ᴘᴀꜱꜱɪᴠᴇ"));
    list.add(new MobDisplay(EntityType.ALLAY, Material.AMETHYST_SHARD, "ᴀʟʟᴀʏ", "ᴘᴀꜱꜱɪᴠᴇ"));
    list.add(new MobDisplay(EntityType.SNIFFER, Material.TORCHFLOWER_SEEDS, "ꜱɴɪꜰꜰᴇʀ", "ᴘᴀꜱꜱɪᴠᴇ"));
    list.add(new MobDisplay(EntityType.CAMEL, Material.CACTUS, "ᴄᴀᴍᴇʟ", "ᴘᴀꜱꜱɪᴠᴇ"));
    list.add(new MobDisplay(EntityType.ARMADILLO, Material.BRUSH, "ᴀʀᴍᴀᴅɪʟʟᴏ", "ᴘᴀꜱꜱɪᴠᴇ"));
    list.add(new MobDisplay(EntityType.SNOW_GOLEM, Material.SNOW_BLOCK, "ꜱɴᴏᴡ ɢᴏʟᴇᴍ", "ᴘᴀꜱꜱɪᴠᴇ"));
    list.add(new MobDisplay(EntityType.STRIDER, Material.WARPED_FUNGUS, "ꜱᴛʀɪᴅᴇʀ", "ᴘᴀꜱꜱɪᴠᴇ"));
    list.add(new MobDisplay(EntityType.BAT, Material.BLACK_DYE, "ʙᴀᴛ", "ᴘᴀꜱꜱɪᴠᴇ"));
    list.add(new MobDisplay(EntityType.MOOSHROOM, Material.RED_MUSHROOM, "ᴍᴏᴏꜱʜʀᴏᴏᴍ", "ᴘᴀꜱꜱɪᴠᴇ"));
    list.add(
        new MobDisplay(EntityType.SKELETON_HORSE, Material.BONE_BLOCK, "ꜱᴋᴇʟᴇᴛᴏɴ ʜᴏʀꜱᴇ", "ᴜɴᴅᴇᴀᴅ"));
    list.add(
        new MobDisplay(EntityType.ZOMBIE_HORSE, Material.ROTTEN_FLESH, "ᴢᴏᴍʙɪᴇ ʜᴏʀꜱᴇ", "ᴜɴᴅᴇᴀᴅ"));
    list.add(
        new MobDisplay(
            EntityType.ZOMBIE_VILLAGER, Material.GOLDEN_APPLE, "ᴢᴏᴍʙɪᴇ ᴠɪʟʟᴀɡᴇʀ", "ʜᴏꜱᴛɪʟᴇ"));
    list.add(new MobDisplay(EntityType.ZOGLIN, Material.ROTTEN_FLESH, "ᴢᴏɢʟɪɴ", "ʜᴏꜱᴛɪʟᴇ"));

    MOB_LIST = Collections.unmodifiableList(list);
  }

  private final FakePlayerPlugin plugin;
  private final FakePlayerManager manager;
  private final BotRenameHelper renameHelper;

  private final Map<UUID, int[]> sessions = new HashMap<>();

  private final Map<UUID, UUID> botSessions = new HashMap<>();

  private final Map<UUID, UUID> botLocks = new HashMap<>();

  private final Map<UUID, ChatInputSes> chatSessions = new HashMap<>();
  private final Set<UUID> pendingChatInput = new HashSet<>();
  private final Set<UUID> pendingRebuild = new HashSet<>();

  private final Set<UUID> pendingDelete = new HashSet<>();

  private final Map<UUID, Long> pendingResetConfirm = new HashMap<>();

  private final Map<UUID, Integer> mobSelectorPage = new HashMap<>();

  private final Set<UUID> inMobSelector = new HashSet<>();

  private final Map<UUID, Boolean> pausedFrozenState = new HashMap<>();

  private final Map<UUID, Integer> editPauseCounts = new HashMap<>();

  private final BotCategory[] categories;

  public BotSettingGui(FakePlayerPlugin plugin, FakePlayerManager manager) {
    this.plugin = plugin;
    this.manager = manager;
    this.renameHelper = new BotRenameHelper(plugin, manager);
    this.categories = new BotCategory[] {general(), chat(), pve(), automation(), pathfinding(), pvp(), danger()};
  }

  public void open(Player player, FakePlayer bot) {
    if (!BotAccess.canAdminister(player, bot)) {
      player.sendMessage(Lang.get("no-permission"));
      return;
    }
    UUID botUuid = bot.getUuid();
    UUID uuid = player.getUniqueId();
    if (!acquireBotLock(botUuid, uuid)) {
      player.sendMessage(Lang.get("inv-busy", "name", bot.getDisplayName()));
      return;
    }
    if (botUuid.equals(botSessions.get(uuid))) {
      build(player);
      return;
    }
    pauseBotForEditing(bot);
    sessions.put(uuid, new int[] {0, 0, 0});
    botSessions.put(uuid, botUuid);
    build(player);
  }

  public @NotNull List<String> getCategoryNames() {
    List<String> names = new ArrayList<>(categories.length);
    for (BotCategory category : categories) names.add(category.label());
    return Collections.unmodifiableList(names);
  }

  public void shutdown() {
    for (UUID botUuid : new ArrayList<>(pausedFrozenState.keySet())) resumeBotAfterEditing(botUuid);
    sessions.clear();
    botSessions.clear();
    botLocks.clear();
    chatSessions.forEach((uuid, ses) -> FppScheduler.cancelTask(ses.cleanupTaskId));
    chatSessions.clear();
    pendingChatInput.clear();
    pendingRebuild.clear();
    pendingDelete.clear();
    pendingResetConfirm.clear();
    mobSelectorPage.clear();
    inMobSelector.clear();
    pausedFrozenState.clear();
    editPauseCounts.clear();
  }

  private void build(Player player) {
    UUID uuid = player.getUniqueId();
    int[] state = sessions.get(uuid);
    UUID botUuid = botSessions.get(uuid);
    if (state == null || botUuid == null) return;

    FakePlayer bot = manager.getByUuid(botUuid);
    if (bot == null) {
      cleanup(uuid);
      player.sendMessage(Lang.get("chat-bot-not-found", "name", "?"));
      return;
    }
    if (!BotAccess.canAdminister(player, bot)) {
      cleanup(uuid);
      player.closeInventory();
      player.sendMessage(Lang.get("no-permission"));
      return;
    }

    int catIdx = state[0];
    int pageIdx = state[1];
    int catOffset = state[2];
    BotCategory cat = categories[catIdx];
    boolean isOp = isOp(player);

    GuiHolder holder = new GuiHolder(uuid);
    Component title =
        Component.empty()
            .decoration(TextDecoration.ITALIC, false)
            .append(Component.text("[").color(DARK_GRAY))
            .append(Component.text("ꜰᴘᴘ").color(ACCENT))
            .append(Component.text("] ").color(DARK_GRAY))
            .append(Component.text(bot.getName()).color(ACCENT))
            .append(Component.text("  ·  ").color(DARK_GRAY))
            .append(Component.text(cat.label()).color(DARK_GRAY));

    Inventory inv = Bukkit.createInventory(holder, SIZE, title);

    List<BotEntry> entries = visibleEntries(cat, isOp);
    int totalPages = Math.max(1, (int) Math.ceil(entries.size() / (double) SETTINGS_PER_PAGE));
    pageIdx = Math.min(pageIdx, Math.max(0, totalPages - 1));
    state[1] = pageIdx;

    int startIdx = pageIdx * SETTINGS_PER_PAGE;
    int endIdx = Math.min(startIdx + SETTINGS_PER_PAGE, entries.size());
    for (int i = startIdx; i < endIdx; i++) {
      inv.setItem(i - startIdx, buildEntryItem(entries.get(i), bot));
    }

    inv.setItem(SLOT_RESET, buildResetButton());
    inv.setItem(
        SLOT_CAT_PREV,
        catOffset > 0 ? buildCatArrow(false) : glassFiller(Material.GRAY_STAINED_GLASS_PANE));
    for (int i = 0; i < CAT_WINDOW; i++) {
      int ci = catOffset + i;
      inv.setItem(
          CAT_WINDOW_START + i,
          ci < categories.length
              ? buildCategoryTab(ci, ci == catIdx)
              : glassFiller(Material.GRAY_STAINED_GLASS_PANE));
    }
    inv.setItem(
        SLOT_CAT_NEXT,
        catOffset + CAT_WINDOW < categories.length
            ? buildCatArrow(true)
            : glassFiller(Material.GRAY_STAINED_GLASS_PANE));
    inv.setItem(SLOT_CLOSE, buildCloseButton());

    pendingRebuild.add(uuid);
    player.openInventory(inv);
    pendingRebuild.remove(uuid);
    sessions.put(uuid, state);
  }

  @EventHandler(priority = EventPriority.HIGH)
  public void onInventoryClick(InventoryClickEvent event) {

    if (event.getInventory().getHolder() instanceof MobSelectorHolder msh) {
      event.setCancelled(true);
      if (!(event.getWhoClicked() instanceof Player player)) return;
      if (event.getClickedInventory() == null) return;
      if (!event.getClickedInventory().equals(event.getInventory())) return;
      handleMobSelectorClick(player, msh, event.getSlot());
      return;
    }

    if (event.getInventory().getHolder() instanceof ShareSelectorHolder ssh) {
      event.setCancelled(true);
      if (!(event.getWhoClicked() instanceof Player player)) return;
      if (event.getClickedInventory() == null) return;
      if (!event.getClickedInventory().equals(event.getInventory())) return;
      handleShareSelectorClick(player, ssh, event.getSlot());
      return;
    }

    if (!(event.getInventory().getHolder() instanceof GuiHolder holder)) return;
    event.setCancelled(true);

    if (!(event.getWhoClicked() instanceof Player player)) return;
    if (event.getClickedInventory() == null) return;
    if (!event.getClickedInventory().equals(event.getInventory())) return;

    UUID uuid = player.getUniqueId();
    int[] state = sessions.get(holder.uuid);
    UUID botUuid = botSessions.get(uuid);
    if (state == null || botUuid == null) return;

    FakePlayer bot = manager.getByUuid(botUuid);
    if (bot == null) {
      player.closeInventory();
      return;
    }
    if (!BotAccess.canAdminister(player, bot)) {
      player.closeInventory();
      player.sendMessage(Lang.get("no-permission"));
      return;
    }

    boolean isOp = isOp(player);
    int slot = event.getSlot();
    int catIdx = state[0];
    int catOffset = state[2];

    if (slot == SLOT_RESET) {
      playUiClick(player, 0.6f);
      resetBot(player, bot, isOp);
      return;
    }
    if (slot == SLOT_CAT_PREV) {
      if (catOffset > 0) {
        playUiClick(player, 1.0f);
        state[2]--;
      }
      build(player);
      return;
    }
    if (slot == SLOT_CAT_NEXT) {
      if (catOffset + CAT_WINDOW < categories.length) {
        playUiClick(player, 1.0f);
        state[2]++;
      }
      build(player);
      return;
    }
    if (slot == SLOT_CLOSE) {
      playUiClick(player, 0.8f);
      player.closeInventory();
      return;
    }
    if (slot >= CAT_WINDOW_START && slot < CAT_WINDOW_START + CAT_WINDOW) {
      int ci = catOffset + (slot - CAT_WINDOW_START);
      if (ci < categories.length) {
        if (ci != catIdx) playUiClick(player, 1.3f);
        state[0] = ci;
        state[1] = 0;
        build(player);
      }
      return;
    }
    if (slot < 45) {
      List<BotEntry> entries = visibleEntries(categories[catIdx], isOp);
      int entryIdx = state[1] * SETTINGS_PER_PAGE + slot;
      if (entryIdx >= entries.size()) return;
      handleEntryClick(player, bot, entries.get(entryIdx), isOp);
    }
  }

  @EventHandler
  public void onInventoryClose(InventoryCloseEvent event) {
    UUID uuid = event.getPlayer().getUniqueId();

    if (event.getInventory().getHolder() instanceof MobSelectorHolder) {

      if (pendingRebuild.contains(uuid)) return;
      inMobSelector.remove(uuid);
      mobSelectorPage.remove(uuid);

      if (event.getReason() != InventoryCloseEvent.Reason.DISCONNECT
          && sessions.containsKey(uuid)) {
        FppScheduler.runSync(
            plugin,
            () -> {
              Player p = Bukkit.getPlayer(uuid);
              if (p != null && sessions.containsKey(uuid)) build(p);
            });
      }
      return;
    }

    if (event.getInventory().getHolder() instanceof ShareSelectorHolder) {
      if (pendingRebuild.contains(uuid)) return;
      if (event.getReason() != InventoryCloseEvent.Reason.DISCONNECT && sessions.containsKey(uuid)) {
        FppScheduler.runSync(
            plugin,
            () -> {
              Player p = Bukkit.getPlayer(uuid);
              if (p != null && sessions.containsKey(uuid)) build(p);
            });
      }
      return;
    }

    if (!(event.getInventory().getHolder() instanceof GuiHolder)) return;
    if (pendingChatInput.contains(uuid)) return;
    if (pendingRebuild.contains(uuid)) return;
    if (pendingDelete.contains(uuid)) return;
    if (inMobSelector.contains(uuid)) return;
    cleanup(uuid);
    if (event.getReason() != InventoryCloseEvent.Reason.DISCONNECT
        && event.getPlayer() instanceof Player player) {
      player.sendMessage(
          Component.empty()
              .decoration(TextDecoration.ITALIC, false)
              .append(Component.text("✔ ").color(ON_GREEN))
              .append(Component.text("ʙᴏᴛ ꜱᴇᴛᴛɪɴɡꜱ ꜱᴀᴠᴇᴅ.").color(WHITE)));
    }
  }

  @EventHandler(priority = EventPriority.LOWEST)
  public void onPlayerChat(AsyncChatEvent event) {
    UUID uuid = event.getPlayer().getUniqueId();
    ChatInputSes ses = chatSessions.remove(uuid);
    if (ses == null) return;

    event.setCancelled(true);
    FppScheduler.cancelTask(ses.cleanupTaskId);

    String raw = PlainTextComponentSerializer.plainText().serialize(event.message()).trim();

    sessions.put(uuid, ses.guiState);
    FppScheduler.runSync(
        plugin,
        () -> {
          Player p = Bukkit.getPlayer(uuid);
          if (p == null) return;

          if (raw.equalsIgnoreCase("cancel")) {
            p.sendMessage(
                Component.empty()
                    .decoration(TextDecoration.ITALIC, false)
                    .append(Component.text("✦ ").color(ACCENT))
                    .append(
                        Component.text("ᴄᴀɴᴄᴇʟʟᴇᴅ - ʀᴇᴛᴜʀɴɪɴɢ ᴛᴏ" + " ꜱᴇᴛᴛɪɴɡꜱ.").color(GRAY)));
            build(p);
            return;
          }

          FakePlayer bot = manager.getByUuid(ses.botUuid);
          if (bot == null) {
            p.sendMessage(Lang.get("chat-bot-not-found", "name", "?"));
            cleanup(uuid);
            return;
          }

          applyInput(p, bot, ses.inputType, raw);
          build(p);
        });
  }

  @EventHandler
  public void onPlayerQuit(PlayerQuitEvent event) {
    UUID uuid = event.getPlayer().getUniqueId();
    ChatInputSes ses = chatSessions.remove(uuid);
    if (ses != null) FppScheduler.cancelTask(ses.cleanupTaskId);
    inMobSelector.remove(uuid);
    mobSelectorPage.remove(uuid);
    cleanup(uuid);
  }

  @EventHandler(priority = EventPriority.MONITOR)
  public void onBotDespawn(FppBotDespawnEvent event) {
    releaseAllEditors(event.getBot().getUuid());
  }

  @EventHandler(priority = EventPriority.MONITOR)
  public void onBotDeath(PlayerDeathEvent event) {
    FakePlayer bot = manager.getByEntity(event.getEntity());
    if (bot != null) releaseAllEditors(bot.getUuid());
  }

  private void handleEntryClick(Player player, FakePlayer bot, BotEntry entry, boolean isOp) {
    switch (entry.type()) {
      case COMING_SOON -> {
        player.playSound(
            player.getLocation(), Sound.ENTITY_VILLAGER_NO, SoundCategory.MASTER, 0.8f, 1.0f);
        player.sendActionBar(
            Component.empty()
                .decoration(TextDecoration.ITALIC, false)
                .append(Component.text("⊘ ").color(COMING_SOON_COLOR))
                .append(
                    Component.text(entry.label() + "  ")
                        .color(WHITE)
                        .decoration(TextDecoration.BOLD, false))
                .append(
                    Component.text("- ᴄᴏᴍɪɴɢ ꜱᴏᴏɴ")
                        .color(COMING_SOON_COLOR)
                        .decoration(TextDecoration.BOLD, true)));
      }
      case TOGGLE -> {
        boolean newVal = applyToggle(bot, entry.id());

        if (!newVal) {
          if ("pickup_items".equals(entry.id())) {
            dropBotInventory(bot);
          } else if ("pickup_xp".equals(entry.id())) {
            dropBotXp(bot);
          }
        }

        manager.persistBotSettings(bot);
        playUiClick(player, newVal ? 1.2f : 0.85f);
        sendActionBarConfirm(player, entry.label(), newVal ? "✔ ᴇɴᴀʙʟᴇᴅ" : "✘ ᴅɪꜱᴀʙʟᴇᴅ");
        build(player);
      }
      case CYCLE_TIER -> {
        cycleTier(bot);
        manager.persistBotSettings(bot);
        playUiClick(player, 1.0f);
        sendActionBarConfirm(
            player, entry.label(), bot.getChatTier() != null ? bot.getChatTier() : "ʀᴀɴᴅᴏᴍ");
        build(player);
      }
      case CYCLE_PERSONALITY -> {
        cyclePersonality(bot);
        playUiClick(player, 1.0f);
        String pName = bot.getAiPersonality() != null ? bot.getAiPersonality() : "ᴅᴇꜰᴀᴜʟᴛ";
        sendActionBarConfirm(player, entry.label(), pName);
        build(player);
      }
      case CYCLE_PRIORITY -> {
        cyclePriority(bot);
        manager.persistBotSettings(bot);
        restartPveIfActive(bot);
        playUiClick(player, 1.0f);
        sendActionBarConfirm(player, entry.label(), bot.getPvePriority());
        build(player);
      }
      case ACTION -> {
        playUiClick(player, 1.0f);
        openChatInput(player, bot, entry);
      }
      case MOB_SELECTOR -> {
        playUiClick(player, 1.0f);
        openMobSelector(player, bot);
      }
      case IMMEDIATE -> {
        if ("share_control".equals(entry.id())) {
          if (!BotAccess.canShare(player, bot)) {
            player.sendMessage(Lang.get("no-permission"));
            return;
          }
          openShareSelector(player, bot);
          return;
        }
        applyImmediate(player, bot, entry.id());
        playUiClick(player, 0.85f);
        build(player);
      }
      case DANGER -> {
        if (!isOp) return;
        playUiClick(player, 0.6f);
        applyDanger(player, bot, entry.id());
      }
    }
  }

  private boolean applyToggle(FakePlayer bot, String id) {
    return switch (id) {
      case "frozen" -> {
        bot.setFrozen(!bot.isFrozen());
        yield bot.isFrozen();
      }
      case "head_ai_enabled" -> {
        bot.setHeadAiEnabled(!bot.isHeadAiEnabled());
        yield bot.isHeadAiEnabled();
      }
      case "swim_ai_enabled" -> {
        bot.setSwimAiEnabled(!bot.isSwimAiEnabled());
        yield bot.isSwimAiEnabled();
      }
      case "nav_sprint_jump" -> {
        bot.setNavSprintJump(!bot.isNavSprintJump());
        yield bot.isNavSprintJump();
      }
      case "pickup_items" -> {
        boolean v = !bot.isPickUpItemsEnabled();
        bot.setPickUpItemsEnabled(v);

        Player body = bot.getPlayer();
        if (body != null) body.setCanPickupItems(v);
        yield v;
      }
      case "pickup_xp" -> {
        bot.setPickUpXpEnabled(!bot.isPickUpXpEnabled());
        yield bot.isPickUpXpEnabled();
      }
      case "chat_enabled" -> {
        bot.setChatEnabled(!bot.isChatEnabled());
        yield bot.isChatEnabled();
      }
      case "nav_parkour" -> {
        bot.setNavParkour(!bot.isNavParkour());
        yield bot.isNavParkour();
      }
      case "nav_break_blocks" -> {
        bot.setNavBreakBlocks(!bot.isNavBreakBlocks());
        yield bot.isNavBreakBlocks();
      }
      case "nav_place_blocks" -> {
        bot.setNavPlaceBlocks(!bot.isNavPlaceBlocks());
        yield bot.isNavPlaceBlocks();
      }
      case "pve_enabled" -> {
        bot.setPveEnabled(!bot.isPveEnabled());

        var attackCmd = plugin.getAttackCommand();
        if (attackCmd != null) {
          if (bot.isPveEnabled()) {
            attackCmd.startMobModeFromSettings(bot);
          } else {
            attackCmd.stopAttacking(bot.getUuid());
          }
        }
        yield bot.isPveEnabled();
      }
      case "pve_move" -> {
        bot.setPveMoveToTarget(!bot.isPveMoveToTarget());
        restartPveIfActive(bot);
        yield bot.isPveMoveToTarget();
      }
      case "auto_eat" -> {
        bot.setAutoEatEnabled(!bot.isAutoEatEnabled());
        yield bot.isAutoEatEnabled();
      }
      case "auto_place_bed" -> {
        bot.setAutoPlaceBedEnabled(!bot.isAutoPlaceBedEnabled());
        yield bot.isAutoPlaceBedEnabled();
      }
      case "follow_player" -> {
        var followCmd = plugin.getFollowCommand();
        if (followCmd == null) yield false;
        boolean wasFollowing = followCmd.isFollowing(bot.getUuid());
        if (wasFollowing) {
          followCmd.stopFollowing(bot.getUuid());
          yield false;
        } else {

          UUID guiPlayerUuid =
              botSessions.entrySet().stream()
                  .filter(e -> e.getValue().equals(bot.getUuid()))
                  .map(Map.Entry::getKey)
                  .findFirst()
                  .orElse(null);
          if (guiPlayerUuid != null) {
            Player target = Bukkit.getPlayer(guiPlayerUuid);
            if (target != null && target.isOnline()) {
              Player botPlayer = bot.getPlayer();
              if (botPlayer != null && botPlayer.getWorld().equals(target.getWorld())) {
                followCmd.startFollowingFromSettings(bot, target);
                yield true;
              }
            }
          }
          yield false;
        }
      }
      default -> false;
    };
  }

  private void cycleTier(FakePlayer bot) {
    bot.setChatTier(
        switch (bot.getChatTier() == null ? "random" : bot.getChatTier()) {
          case "random" -> "quiet";
          case "quiet" -> "passive";
          case "passive" -> "normal";
          case "normal" -> "active";
          case "active" -> "chatty";
          default -> null;
        });
  }

  private void restartPveIfActive(FakePlayer bot) {
    if (!bot.isPveEnabled()) return;
    var attackCmd = plugin.getAttackCommand();
    if (attackCmd != null && attackCmd.isAttacking(bot.getUuid())) {
      attackCmd.startMobModeFromSettings(bot);
    }
  }

  private void cyclePersonality(FakePlayer bot) {
    me.bill.fakePlayerPlugin.ai.PersonalityRepository repo = plugin.getPersonalityRepository();
    if (repo == null || repo.size() == 0) {
      bot.setAiPersonality(null);
      return;
    }

    List<String> names = repo.getNames();
    String current = bot.getAiPersonality();

    if (current == null) {

      bot.setAiPersonality(names.getFirst());
    } else {
      int idx = names.indexOf(current.toLowerCase(java.util.Locale.ROOT));
      if (idx == -1 || idx == names.size() - 1) {

        bot.setAiPersonality(null);
      } else {

        bot.setAiPersonality(names.get(idx + 1));
      }
    }

    if (plugin.getDatabaseManager() != null) {
      plugin
          .getDatabaseManager()
          .updateBotAiPersonality(bot.getUuid().toString(), bot.getAiPersonality());
    }
  }

  private void cyclePriority(FakePlayer bot) {
    String current = bot.getPvePriority();
    bot.setPvePriority("nearest".equals(current) ? "lowest-health" : "nearest");
  }

  private void applyImmediate(Player player, FakePlayer bot, String id) {}

  private void applyDanger(Player player, FakePlayer bot, String id) {
    if ("reset_all".equals(id)) {
      UUID uuid = player.getUniqueId();
      Long confirmTime = pendingResetConfirm.get(uuid);
      long now = System.currentTimeMillis();

      if (confirmTime == null || now - confirmTime > 5000L) {
        pendingResetConfirm.put(uuid, now);
        player.sendMessage(
            Component.empty()
                .decoration(TextDecoration.ITALIC, false)
                .append(Component.text("⚠ ").color(DANGER_RED))
                .append(Component.text("ᴄʟɪᴄᴋ ᴀɡᴀɪɴ ᴡɪᴛʜɪɴ 5ꜱ ᴛᴏ ᴄᴏɴꜰɪʀᴍ ʀᴇꜱᴇᴛ.").color(YELLOW)));
        player.playSound(
            player.getLocation(), Sound.BLOCK_NOTE_BLOCK_BASS, SoundCategory.MASTER, 0.8f, 0.5f);
        return;
      }

      pendingResetConfirm.remove(uuid);
      resetBot(player, bot, true);
      player.sendMessage(
          Component.empty()
              .decoration(TextDecoration.ITALIC, false)
              .append(Component.text("⟲ ").color(YELLOW))
              .append(Component.text("ᴀʟʟ ꜱᴇᴛᴛɪɴɡꜱ ʀᴇꜱᴇᴛ ꜰᴏʀ  ").color(WHITE))
              .append(Component.text(bot.getName()).color(ACCENT)));
      return;
    }
    if ("delete".equals(id)) {
      String botName = bot.getName();
      UUID playerUuid = player.getUniqueId();

      pendingDelete.add(playerUuid);
      cleanup(playerUuid);
      player.closeInventory();
      pendingDelete.remove(playerUuid);

      manager.delete(botName);
      player.sendMessage(
          Component.empty()
              .decoration(TextDecoration.ITALIC, false)
              .append(Component.text("✕ ").color(DANGER_RED))
              .append(Component.text("ᴅᴇʟᴇᴛᴇᴅ ʙᴏᴛ  ").color(WHITE))
              .append(Component.text(botName).color(ACCENT)));
    }
  }

  private void applyInput(Player player, FakePlayer bot, String inputType, String raw) {
    switch (inputType) {
      case "rename" -> {
        cleanup(player.getUniqueId());
        player.closeInventory();
        FppScheduler.runSyncLater(plugin, () -> renameHelper.rename(player, bot, raw), 1L);
      }
      case "chunk_load_radius" -> {
        int globalMax = Config.chunkLoadingEnabled() ? Config.chunkLoadingRadius() : 0;
        int val;
        try {
          val = Integer.parseInt(raw.trim());
        } catch (NumberFormatException e) {
          player.sendMessage(
              Component.empty()
                  .decoration(TextDecoration.ITALIC, false)
                  .append(Component.text("✘ ").color(OFF_RED))
                  .append(
                      Component.text(
                              "ɪɴᴠᴀʟɪᴅ ɴᴜᴍʙᴇʀ — ᴇɴᴛᴇʀ -1 (ɢʟᴏʙᴀʟ), 0"
                                  + " (ᴏꜰꜰ), ᴏʀ 1-"
                                  + globalMax
                                  + ".")
                          .color(GRAY)));
          return;
        }

        if (val < -1) val = -1;
        if (val > globalMax && globalMax > 0) val = globalMax;
        bot.setChunkLoadRadius(val);
        manager.persistBotSettings(bot);
        String display =
            val == -1 ? "ɢʟᴏʙᴀʟ (" + globalMax + ")" : val == 0 ? "ᴅɪꜱᴀʙʟᴇᴅ" : val + " ᴄʜᴜɴᴋꜱ";
        sendActionBarConfirm(player, "ᴄʜᴜɴᴋ ʀᴀᴅɪᴜꜱ", display);
      }
      case "pve_range" -> {
        double val;
        try {
          val = Double.parseDouble(raw.trim());
        } catch (NumberFormatException e) {
          player.sendMessage(
              Component.empty()
                  .decoration(TextDecoration.ITALIC, false)
                  .append(Component.text("✘ ").color(OFF_RED))
                  .append(Component.text("ɪɴᴠᴀʟɪᴅ ɴᴜᴍʙᴇʀ — ᴇɴᴛᴇʀ 1-64.").color(GRAY)));
          return;
        }
        if (val < 1) val = 1;
        if (val > 64) val = 64;
        bot.setPveRange(val);
        manager.persistBotSettings(bot);
        restartPveIfActive(bot);
        sendActionBarConfirm(player, "ᴘᴠᴇ ʀᴀɴɢᴇ", (int) val + " ʙʟᴏᴄᴋꜱ");
      }
    }
  }

  private void openMobSelector(Player player, FakePlayer bot) {
    UUID uuid = player.getUniqueId();
    inMobSelector.add(uuid);
    mobSelectorPage.put(uuid, 0);

    pendingRebuild.add(uuid);
    buildMobSelector(player, bot, 0);
    pendingRebuild.remove(uuid);
  }

  private void buildMobSelector(Player player, FakePlayer bot, int page) {
    UUID uuid = player.getUniqueId();
    int totalPages = Math.max(1, (int) Math.ceil(MOB_LIST.size() / (double) MOB_SLOTS));
    page = Math.min(page, totalPages - 1);
    mobSelectorPage.put(uuid, page);

    Set<String> selectedTypes = bot.getPveMobTypes();

    MobSelectorHolder holder = new MobSelectorHolder(uuid);
    Component title =
        Component.empty()
            .decoration(TextDecoration.ITALIC, false)
            .append(Component.text("[").color(DARK_GRAY))
            .append(Component.text("ꜰᴘᴘ").color(ACCENT))
            .append(Component.text("] ").color(DARK_GRAY))
            .append(Component.text(bot.getName()).color(ACCENT))
            .append(Component.text("  ·  ").color(DARK_GRAY))
            .append(Component.text("ꜱᴇʟᴇᴄᴛ ᴍᴏʙꜱ").color(DARK_GRAY))
            .append(Component.text("  (" + (page + 1) + "/" + totalPages + ")").color(DARK_GRAY));

    Inventory inv = Bukkit.createInventory(holder, MOB_GUI_SIZE, title);

    int startIdx = page * MOB_SLOTS;
    int endIdx = Math.min(startIdx + MOB_SLOTS, MOB_LIST.size());
    for (int i = startIdx; i < endIdx; i++) {
      MobDisplay mob = MOB_LIST.get(i);
      boolean selected = selectedTypes.contains(mob.type.name());
      inv.setItem(i - startIdx, buildMobItem(mob, selected));
    }

    inv.setItem(MOB_SLOT_BACK, buildMobBarItem(Material.ARROW, "◄  ʙᴀᴄᴋ ᴛᴏ ꜱᴇᴛᴛɪɴɡꜱ", ACCENT));

    inv.setItem(
        MOB_SLOT_PREV_PAGE,
        page > 0
            ? buildMobBarItem(
                Material.MAGENTA_STAINED_GLASS_PANE, "◄  ᴘʀᴇᴠɪᴏᴜꜱ ᴘᴀɡᴇ", COMING_SOON_COLOR)
            : glassFiller(Material.GRAY_STAINED_GLASS_PANE));

    inv.setItem(47, glassFiller(Material.GRAY_STAINED_GLASS_PANE));
    inv.setItem(48, glassFiller(Material.GRAY_STAINED_GLASS_PANE));

    boolean isAllHostile = selectedTypes.isEmpty();
    ItemStack clearItem =
        new ItemStack(isAllHostile ? Material.NETHER_STAR : Material.STRUCTURE_VOID);
    ItemMeta clearMeta = clearItem.getItemMeta();
    if (isAllHostile) {
      clearMeta.addEnchant(Enchantment.UNBREAKING, 1, true);
      clearMeta.addItemFlags(ItemFlag.HIDE_ENCHANTS);
    }
    clearMeta.displayName(
        Component.empty()
            .decoration(TextDecoration.ITALIC, false)
            .append(
                Component.text("✦  ᴀʟʟ ʜᴏꜱᴛɪʟᴇ ᴍᴏʙꜱ")
                    .color(isAllHostile ? SELECTED_GREEN : VALUE_YELLOW)
                    .decoration(TextDecoration.BOLD, true)));
    List<Component> clearLore = new ArrayList<>();
    clearLore.add(Component.empty());
    clearLore.add(
        Component.empty()
            .decoration(TextDecoration.ITALIC, false)
            .append(
                Component.text(isAllHostile ? "◈  ᴄᴜʀʀᴇɴᴛʟʏ ᴀᴄᴛɪᴠᴇ" : "ᴄʟɪᴄᴋ ᴛᴏ ᴄʟᴇᴀʀ ᴀʟʟ ᴛᴀʀɢᴇᴛꜱ")
                    .color(isAllHostile ? SELECTED_GREEN : DARK_GRAY)));
    clearMeta.lore(clearLore);
    clearItem.setItemMeta(clearMeta);
    inv.setItem(MOB_SLOT_CLEAR, clearItem);

    inv.setItem(50, glassFiller(Material.GRAY_STAINED_GLASS_PANE));
    inv.setItem(51, glassFiller(Material.GRAY_STAINED_GLASS_PANE));

    inv.setItem(
        MOB_SLOT_NEXT_PAGE,
        page < totalPages - 1
            ? buildMobBarItem(Material.LIME_STAINED_GLASS_PANE, "▶  ɴᴇxᴛ ᴘᴀɡᴇ", ON_GREEN)
            : glassFiller(Material.GRAY_STAINED_GLASS_PANE));

    inv.setItem(MOB_SLOT_CLOSE, buildCloseButton());

    inMobSelector.add(uuid);
    pendingRebuild.add(uuid);
    player.openInventory(inv);
    pendingRebuild.remove(uuid);
  }

  private void handleMobSelectorClick(Player player, MobSelectorHolder holder, int slot) {
    UUID uuid = player.getUniqueId();
    UUID botUuid = botSessions.get(uuid);
    if (botUuid == null) return;
    FakePlayer bot = manager.getByUuid(botUuid);
    if (bot == null) {
      player.closeInventory();
      return;
    }

    int page = mobSelectorPage.getOrDefault(uuid, 0);

    if (slot == MOB_SLOT_BACK) {
      playUiClick(player, 1.0f);
      inMobSelector.remove(uuid);
      mobSelectorPage.remove(uuid);
      pendingRebuild.add(uuid);
      build(player);
      pendingRebuild.remove(uuid);
      return;
    }

    if (slot == MOB_SLOT_CLOSE) {
      playUiClick(player, 0.8f);
      inMobSelector.remove(uuid);
      mobSelectorPage.remove(uuid);
      player.closeInventory();
      return;
    }

    if (slot == MOB_SLOT_PREV_PAGE && page > 0) {
      playUiClick(player, 1.0f);
      pendingRebuild.add(uuid);
      buildMobSelector(player, bot, page - 1);
      pendingRebuild.remove(uuid);
      return;
    }

    int totalPages = Math.max(1, (int) Math.ceil(MOB_LIST.size() / (double) MOB_SLOTS));
    if (slot == MOB_SLOT_NEXT_PAGE && page < totalPages - 1) {
      playUiClick(player, 1.0f);
      pendingRebuild.add(uuid);
      buildMobSelector(player, bot, page + 1);
      pendingRebuild.remove(uuid);
      return;
    }

    if (slot == MOB_SLOT_CLEAR) {
      bot.setPveMobTypes(new java.util.LinkedHashSet<>());
      manager.persistBotSettings(bot);
      restartPveIfActive(bot);
      playUiClick(player, 1.2f);
      sendActionBarConfirm(player, "ᴍᴏʙ ᴛᴀʀɡᴇᴛ", "ᴀʟʟ ʜᴏꜱᴛɪʟᴇ");
      pendingRebuild.add(uuid);
      buildMobSelector(player, bot, page);
      pendingRebuild.remove(uuid);
      return;
    }

    if (slot >= 0 && slot < MOB_SLOTS) {
      int mobIdx = page * MOB_SLOTS + slot;
      if (mobIdx >= MOB_LIST.size()) return;

      MobDisplay mob = MOB_LIST.get(mobIdx);
      boolean nowSelected = bot.togglePveMobType(mob.type.name());
      manager.persistBotSettings(bot);
      restartPveIfActive(bot);
      playUiClick(player, 1.2f);
      int count = bot.getPveMobTypes().size();
      String label =
          nowSelected
              ? "+" + mob.displayName + " (" + count + " ꜱᴇʟᴇᴄᴛᴇᴅ)"
              : "-"
                  + mob.displayName
                  + " ("
                  + (count == 0 ? "ᴀʟʟ ʜᴏꜱᴛɪʟᴇ" : count + " ꜱᴇʟᴇᴄᴛᴇᴅ")
                  + ")";
      sendActionBarConfirm(player, "ᴍᴏʙ ᴛᴀʀɢᴇᴛ", label);

      pendingRebuild.add(uuid);
      buildMobSelector(player, bot, page);
      pendingRebuild.remove(uuid);
    }
  }

  private void openShareSelector(Player player, FakePlayer bot) {
    UUID uuid = player.getUniqueId();
    pendingRebuild.add(uuid);
    buildShareSelector(player, bot);
    pendingRebuild.remove(uuid);
  }

  private void buildShareSelector(Player player, FakePlayer bot) {
    ShareSelectorHolder holder = new ShareSelectorHolder(player.getUniqueId());
    Component title =
        Component.empty()
            .decoration(TextDecoration.ITALIC, false)
            .append(Component.text("[").color(DARK_GRAY))
            .append(Component.text("ꜰᴘᴘ").color(ACCENT))
            .append(Component.text("] ").color(DARK_GRAY))
            .append(Component.text(bot.getName()).color(ACCENT))
            .append(Component.text("  ·  ").color(DARK_GRAY))
            .append(Component.text("ꜱʜᴀʀᴇ ᴄᴏɴᴛʀᴏʟ").color(DARK_GRAY));

    Inventory inv = Bukkit.createInventory(holder, SIZE, title);
    int slot = 0;
    for (Player candidate : Bukkit.getOnlinePlayers()) {
      if (slot >= 45) break;
      if (candidate.getUniqueId().equals(bot.getSpawnedByUuid())) continue;
      if (candidate.getUniqueId().equals(player.getUniqueId())) continue;
      inv.setItem(slot++, buildSharePlayerItem(candidate, bot.hasSharedController(candidate.getUniqueId())));
    }
    if (slot == 0) {
      ItemStack item = new ItemStack(Material.BARRIER);
      ItemMeta meta = item.getItemMeta();
      meta.displayName(Component.text("ɴᴏ ᴏɴʟɪɴᴇ ᴘʟᴀʏᴇʀꜱ").color(OFF_RED));
      meta.lore(List.of(Component.text("ᴘʟᴀʏᴇʀꜱ ᴍᴜꜱᴛ ʙᴇ ᴏɴʟɪɴᴇ ᴛᴏ ꜱʜᴀʀᴇ ᴄᴏɴᴛʀᴏʟ.").color(GRAY)));
      item.setItemMeta(meta);
      inv.setItem(22, item);
    }
    inv.setItem(45, buildMobBarItem(Material.ARROW, "◄  ʙᴀᴄᴋ ᴛᴏ ꜱᴇᴛᴛɪɴɢꜱ", ACCENT));
    for (int i = 46; i < 53; i++) inv.setItem(i, glassFiller(Material.GRAY_STAINED_GLASS_PANE));
    inv.setItem(53, buildCloseButton());
    player.openInventory(inv);
  }

  private ItemStack buildSharePlayerItem(Player player, boolean shared) {
    ItemStack item = new ItemStack(Material.PLAYER_HEAD);
    ItemMeta meta = item.getItemMeta();
    if (shared) {
      meta.addEnchant(Enchantment.UNBREAKING, 1, true);
      meta.addItemFlags(ItemFlag.HIDE_ENCHANTS);
    }
    meta.displayName(
        Component.text(player.getName())
            .color(shared ? SELECTED_GREEN : ACCENT)
            .decoration(TextDecoration.ITALIC, false));
    meta.lore(
        List.of(
            Component.text(shared ? "✔ ᴄᴀɴ ᴄᴏɴᴛʀᴏʟ ᴛʜɪꜱ ʙᴏᴛ" : "✘ ɴᴏ ᴄᴏɴᴛʀᴏʟ ᴀᴄᴄᴇꜱꜱ")
                .color(shared ? SELECTED_GREEN : GRAY),
            Component.text("ᴄʟɪᴄᴋ ᴛᴏ ᴛᴏɢɢʟᴇ").color(YELLOW)));
    item.setItemMeta(meta);
    return item;
  }

  private void handleShareSelectorClick(Player player, ShareSelectorHolder holder, int slot) {
    UUID uuid = player.getUniqueId();
    UUID botUuid = botSessions.get(uuid);
    if (botUuid == null) return;
    FakePlayer bot = manager.getByUuid(botUuid);
    if (bot == null) {
      player.closeInventory();
      return;
    }
    if (!BotAccess.canShare(player, bot)) {
      player.sendMessage(Lang.get("no-permission"));
      player.closeInventory();
      return;
    }
    if (slot == 45) {
      playUiClick(player, 1.0f);
      pendingRebuild.add(uuid);
      build(player);
      pendingRebuild.remove(uuid);
      return;
    }
    if (slot == 53) {
      playUiClick(player, 0.8f);
      player.closeInventory();
      return;
    }
    if (slot < 0 || slot >= 45) return;
    ItemStack item = player.getOpenInventory().getTopInventory().getItem(slot);
    if (item == null || !item.hasItemMeta() || item.getItemMeta().displayName() == null) return;
    String targetName = PlainTextComponentSerializer.plainText().serialize(item.getItemMeta().displayName());
    Player target = Bukkit.getPlayerExact(targetName);
    if (target == null) return;
    boolean shared = bot.hasSharedController(target.getUniqueId());
    if (shared) bot.removeSharedController(target.getUniqueId());
    else bot.addSharedController(target.getUniqueId());
    playUiClick(player, shared ? 0.85f : 1.2f);
    sendActionBarConfirm(player, "ꜱʜᴀʀᴇ ᴄᴏɴᴛʀᴏʟ", target.getName() + (shared ? " ʀᴇᴠᴏᴋᴇᴅ" : " ɢʀᴀɴᴛᴇᴅ"));
    pendingRebuild.add(uuid);
    buildShareSelector(player, bot);
    pendingRebuild.remove(uuid);
  }

  private ItemStack buildMobItem(MobDisplay mob, boolean selected) {
    ItemStack item = new ItemStack(mob.material);
    ItemMeta meta = item.getItemMeta();

    if (selected) {
      meta.addEnchant(Enchantment.UNBREAKING, 1, true);
      meta.addItemFlags(ItemFlag.HIDE_ENCHANTS);
    }

    TextColor nameColor = selected ? SELECTED_GREEN : WHITE;
    meta.displayName(
        Component.empty()
            .decoration(TextDecoration.ITALIC, false)
            .append(
                Component.text(mob.displayName)
                    .color(nameColor)
                    .decoration(TextDecoration.BOLD, selected)));

    List<Component> lore = new ArrayList<>();
    lore.add(Component.empty());
    lore.add(
        Component.empty()
            .decoration(TextDecoration.ITALIC, false)
            .append(Component.text("ᴛʏᴘᴇ  ").color(DARK_GRAY))
            .append(Component.text(mob.category).color(GRAY)));
    lore.add(
        Component.empty()
            .decoration(TextDecoration.ITALIC, false)
            .append(Component.text("ɪᴅ  ").color(DARK_GRAY))
            .append(Component.text(mob.type.name().toLowerCase()).color(GRAY)));
    lore.add(Component.empty());
    if (selected) {
      lore.add(
          Component.empty()
              .decoration(TextDecoration.ITALIC, false)
              .append(Component.text("◈  ᴛᴀʀɢᴇᴛᴇᴅ").color(SELECTED_GREEN)));
      lore.add(hint("◈ ", "ᴄʟɪᴄᴋ ᴛᴏ ʀᴇᴍᴏᴠᴇ"));
    } else {
      lore.add(hint("◈ ", "ᴄʟɪᴄᴋ ᴛᴏ ᴀᴅᴅ ᴛᴀʀɢᴇᴛ"));
    }

    meta.lore(lore);
    item.setItemMeta(meta);
    return item;
  }

  private static ItemStack buildMobBarItem(Material mat, String label, TextColor color) {
    ItemStack item = new ItemStack(mat);
    ItemMeta meta = item.getItemMeta();
    meta.displayName(
        Component.empty()
            .decoration(TextDecoration.ITALIC, false)
            .append(Component.text(label).color(color).decoration(TextDecoration.BOLD, true)));
    item.setItemMeta(meta);
    return item;
  }

  private void dropBotInventory(FakePlayer fp) {
    Player bot = fp.getPlayer();
    if (bot == null || !bot.isOnline()) return;

    boolean hasItems = false;
    for (ItemStack item : bot.getInventory().getContents()) {
      if (item != null && item.getType() != Material.AIR) {
        hasItems = true;
        break;
      }
    }
    if (!hasItems) return;

    Location loc = bot.getLocation();
    float origYaw = loc.getYaw();
    float origPitch = loc.getPitch();

    bot.setRotation(origYaw, 90f);
    NmsPlayerSpawner.setHeadYaw(bot, origYaw);

    FppScheduler.runSyncLater(
        plugin,
        () -> {
          Player b = fp.getPlayer();
          if (b == null || !b.isOnline()) return;

          ItemStack[] contents = b.getInventory().getContents().clone();
          b.getInventory().clear();
          for (ItemStack item : contents) {
            if (item != null && item.getType() != Material.AIR) {
              b.getWorld().dropItemNaturally(b.getLocation(), item);
            }
          }

          FppScheduler.runSyncLater(
              plugin,
              () -> {
                Player b2 = fp.getPlayer();
                if (b2 == null || !b2.isOnline()) return;
                b2.setRotation(origYaw, origPitch);
                NmsPlayerSpawner.setHeadYaw(b2, origYaw);
              },
              5L);
        },
        3L);
  }

  private void dropBotXp(FakePlayer fp) {
    Player bot = fp.getPlayer();
    if (bot == null || !bot.isOnline()) return;

    int xp = bot.getTotalExperience();
    if (xp <= 0) return;

    World world = bot.getWorld();
    Location loc = bot.getLocation();
    world.spawn(loc, ExperienceOrb.class, orb -> orb.setExperience(xp));

    bot.setTotalExperience(0);
    bot.setLevel(0);
    bot.setExp(0f);
  }

  private void resetBot(Player player, FakePlayer bot, boolean isOp) {

    bot.setFrozen(false);
    bot.setHeadAiEnabled(true);
    bot.setSwimAiEnabled(Config.swimAiEnabled());
    bot.setChunkLoadRadius(-1);
    bot.setPickUpItemsEnabled(Config.bodyPickUpItems());
    bot.setPickUpXpEnabled(Config.bodyPickUpXp());

    bot.setChatEnabled(true);
    bot.setChatTier(null);
    bot.setAiPersonality(null);
    bot.setPing(-1);

    bot.setPveEnabled(false);
    var attackCmd = plugin.getAttackCommand();
    if (attackCmd != null) attackCmd.stopAttacking(bot.getUuid());
    bot.setPveRange(Config.attackMobDefaultRange());
    bot.setPvePriority(Config.attackMobDefaultPriority());
    bot.setPveMoveToTarget(false);
    bot.setPveMobTypes(new java.util.LinkedHashSet<>());

    bot.setNavParkour(Config.pathfindingParkour());
    bot.setNavBreakBlocks(Config.pathfindingBreakBlocks());
    bot.setNavPlaceBlocks(Config.pathfindingPlaceBlocks());
    bot.setNavSprintJump(Config.pathfindingSprintJump());

    if (isOp) bot.setRightClickCommand(null);

    manager.persistBotSettings(bot);
    build(player);
    player.sendActionBar(
        Component.empty()
            .decoration(TextDecoration.ITALIC, false)
            .append(Component.text("⟲ ").color(YELLOW))
            .append(Component.text("ʙᴏᴛ ꜱᴇᴛᴛɪɴɡꜱ  ").color(WHITE))
            .append(
                Component.text("ʀᴇꜱᴇᴛ ᴛᴏ ᴅᴇꜰᴀᴜʟᴛꜱ")
                    .color(YELLOW)
                    .decoration(TextDecoration.BOLD, true)));
  }

  private void openChatInput(Player player, FakePlayer bot, BotEntry entry) {
    UUID uuid = player.getUniqueId();
    int[] guiState = sessions.get(uuid);
    if (guiState == null) return;

    pendingChatInput.add(uuid);
    player.closeInventory();
    pendingChatInput.remove(uuid);

    String promptLabel;
    String currentVal;
    switch (entry.id()) {
      case "rename" -> {
        promptLabel = "ɴᴇᴡ ʙᴏᴛ ɴᴀᴍᴇ";
        currentVal = bot.getName();
      }
      case "chunk_load_radius" -> {
        int gMax = Config.chunkLoadingEnabled() ? Config.chunkLoadingRadius() : 0;
        promptLabel = "ʀᴀᴅɪᴜꜱ (-1=ɢʟᴏʙᴀʟ, 0=ᴏꜰꜰ, 1-" + gMax + ")";
        int cur = bot.getChunkLoadRadius();
        currentVal = cur == -1 ? "ɢʟᴏʙᴀʟ (" + gMax + ")" : cur == 0 ? "ᴅɪꜱᴀʙʟᴇᴅ" : cur + " ᴄʜᴜɴᴋꜱ";
      }
      case "pve_range" -> {
        promptLabel = "ᴅᴇᴛᴇᴄᴛ ʀᴀɴɢᴇ (1-64)";
        currentVal = (int) bot.getPveRange() + " ʙʟᴏᴄᴋꜱ";
      }
      default -> {
        promptLabel = entry.label();
        currentVal = "?";
      }
    }

    player.sendMessage(Component.empty());
    player.sendMessage(
        Component.empty()
            .decoration(TextDecoration.ITALIC, false)
            .append(Component.text("┌─ ").color(DARK_GRAY))
            .append(Component.text("[").color(DARK_GRAY))
            .append(Component.text("ꜰᴘᴘ").color(ACCENT))
            .append(Component.text("]  ").color(DARK_GRAY))
            .append(
                Component.text("ʙᴏᴛ ꜱᴇᴛᴛɪɴɡꜱ").color(WHITE).decoration(TextDecoration.BOLD, true))
            .append(Component.text("  ·  ᴇᴅɪᴛ ᴠᴀʟᴜᴇ").color(DARK_GRAY)));
    player.sendMessage(
        Component.empty()
            .decoration(TextDecoration.ITALIC, false)
            .append(Component.text("│  ").color(DARK_GRAY))
            .append(
                Component.text(entry.label())
                    .color(VALUE_YELLOW)
                    .decoration(TextDecoration.BOLD, true)));
    for (String line : entry.description().split("\\\\n|\n")) {
      if (!line.isBlank())
        player.sendMessage(
            Component.empty()
                .decoration(TextDecoration.ITALIC, false)
                .append(Component.text("│  ").color(DARK_GRAY))
                .append(Component.text(line).color(GRAY)));
    }
    player.sendMessage(
        Component.empty()
            .decoration(TextDecoration.ITALIC, false)
            .append(Component.text("│  ").color(DARK_GRAY)));
    player.sendMessage(
        Component.empty()
            .decoration(TextDecoration.ITALIC, false)
            .append(Component.text("│  ").color(DARK_GRAY))
            .append(Component.text("ᴄᴜʀʀᴇɴᴛ  ").color(DARK_GRAY))
            .append(
                Component.text(currentVal)
                    .color(VALUE_YELLOW)
                    .decoration(TextDecoration.BOLD, true)));
    player.sendMessage(
        Component.empty()
            .decoration(TextDecoration.ITALIC, false)
            .append(Component.text("└─ ").color(DARK_GRAY))
            .append(Component.text("ᴛʏᴘᴇ ᴀ ɴᴇᴡ ᴠᴀʟᴜᴇ, ᴏʀ ").color(GRAY))
            .append(Component.text("ᴄᴀɴᴄᴇʟ").color(OFF_RED).decoration(TextDecoration.BOLD, true))
            .append(Component.text(" ᴛᴏ ɡᴏ ʙᴀᴄᴋ.").color(GRAY)));
    player.sendMessage(Component.empty());

    int taskId =
        FppScheduler.runSyncLaterWithId(
            plugin,
            () -> {
              ChatInputSes stale = chatSessions.remove(uuid);
              if (stale != null) {
                sessions.put(uuid, stale.guiState);
                Player p = Bukkit.getPlayer(uuid);
                if (p != null) {
                  p.sendMessage(
                      Component.empty()
                          .decoration(TextDecoration.ITALIC, false)
                          .append(Component.text("✦ ").color(ACCENT))
                          .append(
                              Component.text(
                                      "ɪɴᴘᴜᴛ ᴛɪᴍᴇᴅ" + " ᴏᴜᴛ -" + " ʀᴇᴛᴜʀɴɪɴɢ" + " ᴛᴏ ꜱᴇᴛᴛɪɴɡꜱ.")
                                  .color(GRAY)));
                  build(p);
                }
              }
            },
            20L * 60);

    chatSessions.put(uuid, new ChatInputSes(entry.id(), bot.getUuid(), guiState.clone(), taskId));
  }

  private ItemStack buildEntryItem(BotEntry entry, FakePlayer bot) {

    if (entry.type() == BotEntryType.COMING_SOON) {
      ItemStack item = new ItemStack(entry.icon());
      ItemMeta meta = item.getItemMeta();
      meta.displayName(
          Component.empty()
              .decoration(TextDecoration.ITALIC, false)
              .append(Component.text("⊘ ").color(COMING_SOON_COLOR))
              .append(
                  Component.text(entry.label())
                      .color(COMING_SOON_COLOR)
                      .decoration(TextDecoration.BOLD, true)));
      List<Component> lore = new ArrayList<>();
      lore.add(Component.empty());
      lore.add(
          Component.empty()
              .decoration(TextDecoration.ITALIC, false)
              .append(Component.text("ᴠᴀʟᴜᴇ  ").color(DARK_GRAY))
              .append(
                  Component.text("⚠ ᴄᴏᴍɪɴɢ ꜱᴏᴏɴ")
                      .color(COMING_SOON_COLOR)
                      .decoration(TextDecoration.BOLD, true)));
      lore.add(Component.empty());
      for (String line : entry.description().split("\\\\n|\n")) {
        if (!line.isBlank())
          lore.add(
              Component.empty()
                  .decoration(TextDecoration.ITALIC, false)
                  .append(Component.text(line).color(GRAY)));
      }
      lore.add(Component.empty());
      lore.add(
          Component.empty()
              .decoration(TextDecoration.ITALIC, false)
              .append(Component.text("⊘ ").color(COMING_SOON_COLOR))
              .append(Component.text("ꜰᴇᴀᴛᴜʀᴇ ᴜɴᴀᴠᴀɪʟᴀʙʟᴇ").color(DARK_GRAY)));
      meta.lore(lore);
      item.setItemMeta(meta);
      return item;
    }
    boolean isToggle = entry.type() == BotEntryType.TOGGLE;
    boolean isDanger = entry.type() == BotEntryType.DANGER;
    boolean isOn = isToggle && getBoolValue(entry.id(), bot);

    TextColor nameColor = isDanger ? DANGER_RED : (isToggle ? (isOn ? ON_GREEN : OFF_RED) : ACCENT);
    ItemStack item = new ItemStack(dynamicIcon(entry, bot));
    ItemMeta meta = item.getItemMeta();

    if (isToggle && isOn) {
      meta.addEnchant(Enchantment.UNBREAKING, 1, true);
      meta.addItemFlags(ItemFlag.HIDE_ENCHANTS);
    }

    meta.displayName(
        Component.empty()
            .decoration(TextDecoration.ITALIC, false)
            .append(
                Component.text(entry.label())
                    .color(nameColor)
                    .decoration(TextDecoration.BOLD, true)));

    List<Component> lore = new ArrayList<>();
    lore.add(Component.empty());
    TextColor valColor =
        isDanger ? DANGER_RED : (isToggle ? (isOn ? ON_GREEN : OFF_RED) : VALUE_YELLOW);
    lore.add(
        Component.empty()
            .decoration(TextDecoration.ITALIC, false)
            .append(Component.text("ᴠᴀʟᴜᴇ  ").color(DARK_GRAY))
            .append(
                Component.text(valueString(entry, bot))
                    .color(valColor)
                    .decoration(TextDecoration.BOLD, true)));
    lore.add(Component.empty());
    for (String line : entry.description().split("\\\\n|\n")) {
      if (!line.isBlank())
        lore.add(
            Component.empty()
                .decoration(TextDecoration.ITALIC, false)
                .append(Component.text(line).color(isDanger ? DANGER_RED : GRAY)));
    }
    lore.add(Component.empty());
    switch (entry.type()) {
      case TOGGLE -> lore.add(hint("◈ ", "ᴄʟɪᴄᴋ ᴛᴏ ᴛᴏɡɡʟᴇ"));
      case CYCLE_TIER, CYCLE_PERSONALITY, CYCLE_PRIORITY -> lore.add(hint("◈ ", "ᴄʟɪᴄᴋ ᴛᴏ ᴄʏᴄʟᴇ"));
      case ACTION -> lore.add(hint("✎ ", "ᴄʟɪᴄᴋ ᴛᴏ ᴇᴅɪᴛ ɪɴ ᴄʜᴀᴛ"));
      case MOB_SELECTOR -> lore.add(hint("◈ ", "ᴄʟɪᴄᴋ ᴛᴏ ᴏᴘᴇɴ ᴍᴏʙ ꜱᴇʟᴇᴄᴛᴏʀ"));
      case IMMEDIATE -> lore.add(hint("◈ ", "ᴄʟɪᴄᴋ ᴛᴏ ᴄʟᴇᴀʀ"));
      case DANGER ->
          lore.add(
              Component.empty()
                  .decoration(TextDecoration.ITALIC, false)
                  .append(Component.text("◈ ").color(DANGER_RED))
                  .append(Component.text("ᴄʟɪᴄᴋ ᴛᴏ ᴄᴏɴꜰɪʀᴍ").color(DARK_GRAY)));
    }
    meta.lore(lore);
    item.setItemMeta(meta);
    return item;
  }

  private static Component hint(String icon, String text) {
    return Component.empty()
        .decoration(TextDecoration.ITALIC, false)
        .append(Component.text(icon).color(ACCENT))
        .append(Component.text(text).color(DARK_GRAY));
  }

  private String valueString(BotEntry entry, FakePlayer bot) {
    return switch (entry.id()) {
      case "frozen" -> bot.isFrozen() ? "✔ ᴇɴᴀʙʟᴇᴅ" : "✘ ᴅɪꜱᴀʙʟᴇᴅ";
      case "head_ai_enabled" -> bot.isHeadAiEnabled() ? "✔ ᴇɴᴀʙʟᴇᴅ" : "✘ ᴅɪꜱᴀʙʟᴇᴅ";
      case "swim_ai_enabled" -> bot.isSwimAiEnabled() ? "✔ ᴇɴᴀʙʟᴇᴅ" : "✘ ᴅɪꜱᴀʙʟᴇᴅ";
      case "pickup_items" -> bot.isPickUpItemsEnabled() ? "✔ ᴇɴᴀʙʟᴇᴅ" : "✘ ᴅɪꜱᴀʙʟᴇᴅ";
      case "pickup_xp" -> bot.isPickUpXpEnabled() ? "✔ ᴇɴᴀʙʟᴇᴅ" : "✘ ᴅɪꜱᴀʙʟᴇᴅ";
      case "chat_enabled" -> bot.isChatEnabled() ? "✔ ᴇɴᴀʙʟᴇᴅ" : "✘ ᴅɪꜱᴀʙʟᴇᴅ";
      case "chat_tier" -> bot.getChatTier() != null ? bot.getChatTier() : "ʀᴀɴᴅᴏᴍ";
      case "ai_personality" -> bot.getAiPersonality() != null ? bot.getAiPersonality() : "ᴅᴇꜰᴀᴜʟᴛ";
      case "nav_parkour" -> bot.isNavParkour() ? "✔ ᴇɴᴀʙʟᴇᴅ" : "✘ ᴅɪꜱᴀʙʟᴇᴅ";
      case "nav_break_blocks" -> bot.isNavBreakBlocks() ? "✔ ᴇɴᴀʙʟᴇᴅ" : "✘ ᴅɪꜱᴀʙʟᴇᴅ";
      case "nav_place_blocks" -> bot.isNavPlaceBlocks() ? "✔ ᴇɴᴀʙʟᴇᴅ" : "✘ ᴅɪꜱᴀʙʟᴇᴅ";
      case "nav_sprint_jump" -> bot.isNavSprintJump() ? "✔ ᴇɴᴀʙʟᴇᴅ" : "✘ ᴅɪꜱᴀʙʟᴇᴅ";
      case "pve_enabled" -> bot.isPveEnabled() ? "✔ ᴇɴᴀʙʟᴇᴅ" : "✘ ᴅɪꜱᴀʙʟᴇᴅ";
      case "pve_move" -> bot.isPveMoveToTarget() ? "✔ ᴄʜᴀꜱɪɴɢ" : "✘ ꜱᴛᴀᴛɪᴏɴᴀʀʏ";
      case "auto_eat" -> bot.isAutoEatEnabled() ? "✔ ᴇɴᴀʙʟᴇᴅ" : "✘ ᴅɪꜱᴀʙʟᴇᴅ";
      case "auto_place_bed" -> bot.isAutoPlaceBedEnabled() ? "✔ ᴇɴᴀʙʟᴇᴅ" : "✘ ᴅɪꜱᴀʙʟᴇᴅ";
      case "share_control" -> bot.getSharedControllers().size() + " ꜱʜᴀʀᴇᴅ";
      case "follow_player" -> {
        var followCmd = plugin.getFollowCommand();
        yield (followCmd != null && followCmd.isFollowing(bot.getUuid()))
            ? "✔ ꜰᴏʟʟᴏᴡɪɴɢ"
            : "✘ ɪᴅʟᴇ";
      }
      case "pve_range" -> (int) bot.getPveRange() + " ʙʟᴏᴄᴋꜱ";
      case "pve_priority" -> bot.getPvePriority() != null ? bot.getPvePriority() : "nearest";
      case "pve_mob_type" -> {
        Set<String> types = bot.getPveMobTypes();
        if (types.isEmpty()) yield "ᴀʟʟ ʜᴏꜱᴛɪʟᴇ";
        if (types.size() == 1) {
          String t = types.iterator().next();
          for (MobDisplay md : MOB_LIST) {
            if (md.type.name().equals(t)) yield md.displayName;
          }
          yield t.toLowerCase();
        }
        yield types.size() + " ᴍᴏʙ ᴛʏᴘᴇꜱ";
      }
      case "rename" -> bot.getName();
      case "chunk_load_radius" -> {
        int r = bot.getChunkLoadRadius();
        int gMax = Config.chunkLoadingEnabled() ? Config.chunkLoadingRadius() : 0;
        yield r == -1 ? "ɢʟᴏʙᴀʟ (" + gMax + ")" : r == 0 ? "ᴅɪꜱᴀʙʟᴇᴅ" : r + " ᴄʜᴜɴᴋꜱ";
      }
      case "reset_all" -> "⚠ ɢᴇɴᴇʀᴀʟ · ᴄʜᴀᴛ · ᴘᴠᴇ · ᴘᴀᴛʜ · ᴄᴍᴅꜱ";
      case "delete" -> bot.getName();
      default -> "?";
    };
  }

  private boolean getBoolValue(String id, FakePlayer bot) {
    return switch (id) {
      case "frozen" -> bot.isFrozen();
      case "head_ai_enabled" -> bot.isHeadAiEnabled();
      case "swim_ai_enabled" -> bot.isSwimAiEnabled();
      case "pickup_items" -> bot.isPickUpItemsEnabled();
      case "pickup_xp" -> bot.isPickUpXpEnabled();
      case "chat_enabled" -> bot.isChatEnabled();
      case "nav_parkour" -> bot.isNavParkour();
      case "nav_break_blocks" -> bot.isNavBreakBlocks();
      case "nav_place_blocks" -> bot.isNavPlaceBlocks();
      case "nav_sprint_jump" -> bot.isNavSprintJump();
      case "pve_enabled" -> bot.isPveEnabled();
      case "pve_move" -> bot.isPveMoveToTarget();
      case "auto_eat" -> bot.isAutoEatEnabled();
      case "auto_place_bed" -> bot.isAutoPlaceBedEnabled();
      case "follow_player" -> {
        var followCmd = plugin.getFollowCommand();
        yield followCmd != null && followCmd.isFollowing(bot.getUuid());
      }
      default -> false;
    };
  }

  private Material dynamicIcon(BotEntry entry, FakePlayer bot) {
    return switch (entry.id()) {
      case "frozen" -> bot.isFrozen() ? Material.BLUE_ICE : Material.PACKED_ICE;
      case "head_ai_enabled" ->
          bot.isHeadAiEnabled() ? Material.PLAYER_HEAD : Material.SKELETON_SKULL;
      case "swim_ai_enabled" -> bot.isSwimAiEnabled() ? Material.WATER_BUCKET : Material.BUCKET;
      case "pickup_items" -> bot.isPickUpItemsEnabled() ? Material.HOPPER : Material.CHEST;
      case "pickup_xp" ->
          bot.isPickUpXpEnabled() ? Material.EXPERIENCE_BOTTLE : Material.GLASS_BOTTLE;
      case "chat_enabled" -> bot.isChatEnabled() ? Material.WRITABLE_BOOK : Material.BOOK;
      case "nav_parkour" -> bot.isNavParkour() ? Material.SLIME_BALL : Material.RABBIT_FOOT;
      case "nav_break_blocks" ->
          bot.isNavBreakBlocks() ? Material.DIAMOND_PICKAXE : Material.IRON_PICKAXE;
      case "nav_place_blocks" -> bot.isNavPlaceBlocks() ? Material.GRASS_BLOCK : Material.DIRT;
      case "nav_sprint_jump" -> bot.isNavSprintJump() ? Material.FEATHER : Material.LEATHER_BOOTS;
      case "pve_enabled" -> bot.isPveEnabled() ? Material.IRON_SWORD : Material.WOODEN_SWORD;
      case "pve_move" -> bot.isPveMoveToTarget() ? Material.GOLDEN_BOOTS : Material.CHAINMAIL_BOOTS;
      case "auto_eat" -> bot.isAutoEatEnabled() ? Material.COOKED_BEEF : Material.ROTTEN_FLESH;
      case "auto_place_bed" -> bot.isAutoPlaceBedEnabled() ? Material.RED_BED : Material.GRAY_BED;
      case "share_control" -> Material.PLAYER_HEAD;
      case "follow_player" -> {
        var followCmd = plugin.getFollowCommand();
        yield (followCmd != null && followCmd.isFollowing(bot.getUuid()))
            ? Material.LEAD
            : Material.STRING;
      }
      case "pve_mob_type" -> {
        Set<String> types = bot.getPveMobTypes();
        if (types.isEmpty()) yield Material.ZOMBIE_HEAD;
        if (types.size() == 1) {
          String t = types.iterator().next();
          for (MobDisplay md : MOB_LIST) {
            if (md.type.name().equals(t)) yield md.material;
          }
        }
        yield Material.ZOMBIE_HEAD;
      }
      case "chunk_load_radius" ->
          bot.getChunkLoadRadius() == 0 ? Material.STRUCTURE_VOID : Material.MAP;
      default -> entry.icon();
    };
  }

  private ItemStack buildCategoryTab(int idx, boolean active) {
    BotCategory cat = categories[idx];
    ItemStack item = new ItemStack(active ? cat.activeMat() : cat.inactiveMat());
    ItemMeta meta = item.getItemMeta();
    if (active) {
      meta.addEnchant(Enchantment.UNBREAKING, 1, true);
      meta.addItemFlags(ItemFlag.HIDE_ENCHANTS);
    }
    meta.displayName(
        Component.empty()
            .decoration(TextDecoration.ITALIC, false)
            .append(
                Component.text(cat.label()).color(ACCENT).decoration(TextDecoration.BOLD, active)));
    meta.lore(
        List.of(
            Component.empty()
                .decoration(TextDecoration.ITALIC, false)
                .append(
                    Component.text(active ? "◈  ᴄᴜʀʀᴇɴᴛʟʏ ᴠɪᴇᴡɪɴɢ" : "ᴄʟɪᴄᴋ ᴛᴏ ꜱᴡɪᴛᴄʜ")
                        .color(active ? ON_GREEN : DARK_GRAY))));
    item.setItemMeta(meta);
    return item;
  }

  private ItemStack buildCatArrow(boolean isNext) {
    Material mat = isNext ? Material.LIME_STAINED_GLASS_PANE : Material.MAGENTA_STAINED_GLASS_PANE;
    TextColor col = isNext ? ON_GREEN : COMING_SOON_COLOR;
    ItemStack item = new ItemStack(mat);
    ItemMeta meta = item.getItemMeta();
    meta.displayName(
        Component.empty()
            .decoration(TextDecoration.ITALIC, false)
            .append(
                Component.text(isNext ? "▶" : "◄")
                    .color(col)
                    .decoration(TextDecoration.BOLD, true)));
    meta.lore(
        List.of(
            Component.empty()
                .decoration(TextDecoration.ITALIC, false)
                .append(
                    Component.text("ꜱᴄʀᴏʟʟ ᴄᴀᴛᴇɡᴏʀɪᴇꜱ " + (isNext ? "ꜰᴏʀᴡᴀʀᴅ" : "ʙᴀᴄᴋᴡᴀʀᴅ") + ".")
                        .color(DARK_GRAY))));
    item.setItemMeta(meta);
    return item;
  }

  private ItemStack buildResetButton() {
    ItemStack item = new ItemStack(Material.REDSTONE_BLOCK);
    ItemMeta meta = item.getItemMeta();
    meta.displayName(
        Component.empty()
            .decoration(TextDecoration.ITALIC, false)
            .append(Component.text("⟲  ʀᴇꜱᴇᴛ ʙᴏᴛ").color(YELLOW)));
    meta.lore(
        List.of(
            Component.empty()
                .decoration(TextDecoration.ITALIC, false)
                .append(Component.text("ʀᴇꜱᴇᴛ ᴀʟʟ ʙᴏᴛ ꜱᴇᴛᴛɪɴɡꜱ").color(GRAY)),
            Component.empty()
                .decoration(TextDecoration.ITALIC, false)
                .append(Component.text("ᴛᴏ ᴅᴇꜰᴀᴜʟᴛ ᴠᴀʟᴜᴇꜱ.").color(GRAY))));
    item.setItemMeta(meta);
    return item;
  }

  private ItemStack buildCloseButton() {
    ItemStack item = new ItemStack(Material.BARRIER);
    ItemMeta meta = item.getItemMeta();
    meta.displayName(
        Component.empty()
            .decoration(TextDecoration.ITALIC, false)
            .append(
                Component.text("✕  ᴄʟᴏꜱᴇ").color(OFF_RED).decoration(TextDecoration.BOLD, true)));
    meta.lore(
        List.of(
            Component.empty()
                .decoration(TextDecoration.ITALIC, false)
                .append(Component.text("ᴄʟᴏꜱᴇ ᴛʜᴇ ʙᴏᴛ ꜱᴇᴛᴛɪɴɡꜱ ᴍᴇɴᴜ.").color(DARK_GRAY))));
    item.setItemMeta(meta);
    return item;
  }

  private static ItemStack glassFiller(Material mat) {
    ItemStack item = new ItemStack(mat);
    ItemMeta meta = item.getItemMeta();
    meta.displayName(Component.empty());
    meta.lore(List.of());
    item.setItemMeta(meta);
    return item;
  }

  private static List<BotEntry> visibleEntries(BotCategory cat, boolean isOp) {
    if (isOp) return cat.entries();
    return cat.entries().stream().filter(e -> !e.opOnly()).toList();
  }

  private void cleanup(UUID uuid) {
    UUID botUuid = botSessions.get(uuid);
    if (botUuid != null) {
      releaseBotLock(botUuid, uuid);
      resumeBotAfterEditing(botUuid);
    }
    sessions.remove(uuid);
    botSessions.remove(uuid);
    pendingResetConfirm.remove(uuid);
  }

  private boolean acquireBotLock(UUID botUuid, UUID viewerUuid) {
    UUID owner = botLocks.putIfAbsent(botUuid, viewerUuid);
    return owner == null || owner.equals(viewerUuid);
  }

  private void releaseBotLock(UUID botUuid, UUID viewerUuid) {
    botLocks.remove(botUuid, viewerUuid);
  }

  private void releaseAllEditors(UUID botUuid) {
    botLocks.remove(botUuid);
    for (Map.Entry<UUID, UUID> entry : new HashMap<>(botSessions).entrySet()) {
      if (!botUuid.equals(entry.getValue())) continue;
      Player viewer = Bukkit.getPlayer(entry.getKey());
      if (viewer != null) {
        pendingDelete.add(entry.getKey());
        viewer.closeInventory();
      }
      cleanup(entry.getKey());
      pendingDelete.remove(entry.getKey());
    }
  }

  private void pauseBotForEditing(FakePlayer bot) {
    UUID botUuid = bot.getUuid();
    editPauseCounts.merge(botUuid, 1, Integer::sum);
    pausedFrozenState.putIfAbsent(botUuid, bot.isFrozen());
    bot.setFrozen(true);
    Player player = bot.getPlayer();
    if (player != null && player.isOnline()) {
      manager.lockForAction(botUuid, player.getLocation());
      NmsPlayerSpawner.setMovementForward(player, 0f);
      player.setSprinting(false);
      player.setVelocity(new org.bukkit.util.Vector(0, 0, 0));
    }
  }

  private void resumeBotAfterEditing(UUID botUuid) {
    Integer count = editPauseCounts.get(botUuid);
    if (count != null && count > 1) {
      editPauseCounts.put(botUuid, count - 1);
      return;
    }
    editPauseCounts.remove(botUuid);
    Boolean wasFrozen = pausedFrozenState.remove(botUuid);
    if (wasFrozen == null) return;
    manager.unlockAction(botUuid);
    FakePlayer bot = manager.getByUuid(botUuid);
    if (bot != null) bot.setFrozen(wasFrozen);
  }

  private boolean isOp(Player player) {
    return player.isOp() || Perm.has(player, Perm.OP);
  }

  private void sendActionBarConfirm(Player player, String label, String value) {
    player.sendActionBar(
        Component.empty()
            .decoration(TextDecoration.ITALIC, false)
            .append(Component.text("✔ ").color(ON_GREEN))
            .append(Component.text(label + "  ").color(WHITE))
            .append(Component.text("→  ").color(DARK_GRAY))
            .append(
                Component.text(value).color(VALUE_YELLOW).decoration(TextDecoration.BOLD, true)));
  }

  private static void playUiClick(Player player, float pitch) {
    player.playSound(
        player.getLocation(), Sound.UI_BUTTON_CLICK, SoundCategory.MASTER, 0.5f, pitch);
  }

  private BotCategory general() {
    int globalMax = Config.chunkLoadingEnabled() ? Config.chunkLoadingRadius() : 0;
    return new BotCategory(
        "⚙ ɢᴇɴᴇʀᴀʟ",
        Material.COMPARATOR,
        Material.GRAY_DYE,
        Material.LIGHT_GRAY_STAINED_GLASS_PANE,
        List.of(
            BotEntry.toggle(
                "frozen",
                "ꜰʀᴏᴢᴇɴ",
                "ʙᴏᴛ ᴄᴀɴɴᴏᴛ ᴍᴏᴠᴇ ᴡʜᴇɴ ꜰʀᴏᴢᴇɴ.\nᴛᴏɡɡʟᴇ ᴛᴏ ᴘᴀᴜꜱᴇ ᴀʟʟ ᴍᴏᴠᴇᴍᴇɴᴛ.",
                Material.PACKED_ICE,
                false),
            BotEntry.toggle(
                "head_ai_enabled",
                "ʜᴇᴀᴅ ᴀɪ (ʟᴏᴏᴋ ᴀᴛ ᴘʟᴀʏᴇʀ)",
                "ʙᴏᴛ ꜱᴍᴏᴏᴛʜʟʏ ʀᴏᴛᴀᴛᴇꜱ ᴛʏᴘᴇ ᴘʟᴀʏᴇʀꜱ ᴡʜᴇɴ ᴇɴᴀʙʟᴇᴅ.\n"
                    + "ᴅɪꜱᴀʙᴇ ᴛᴏ ᴋᴇᴇᴘ ʜᴇᴀᴅ ꜱᴛᴀᴛɪᴏɴᴀʀʏ.",
                Material.PLAYER_HEAD,
                false),
            BotEntry.toggle(
                "swim_ai_enabled",
                "ꜱᴡɪᴍ ᴀɪ",
                "ʙᴏᴛ ᴀᴜᴛᴏ-ꜱᴡɪᴍꜱ ᴜᴘᴡᴀʀᴅ ɪɴ ᴡᴀᴛᴇʀ/ʟᴀᴠᴀ\n"
                    + "ᴡʜᴇɴ ᴇɴᴀʙᴜᴇᴅ (ꜱᴘᴀᴄᴇʙᴀʀ ʜᴏʟᴅ).\n"
                    + "ᴅɪꜱᴀʙᴇ ᴛᴏ ʟᴇᴛ ᴛʜᴇ ʙᴏᴛ ꜱɪɴᴋ.\n"
                    + "ɢʟᴏʙᴀʟ: "
                    + (Config.swimAiEnabled() ? "ᴇɴᴀʙʟᴇᴅ" : "ᴅɪꜱᴀʙʟᴇᴅ"),
                Material.WATER_BUCKET,
                false),
            BotEntry.toggle(
                "nav_sprint_jump",
                "ꜱᴘʀɪɴᴛ-ᴊᴜᴍᴘ",
                "ʙᴏᴛ ᴊᴜᴍᴘꜱ ᴘᴇʀɪᴏᴅɪᴄᴀʟʟʏ ᴡʜɪʟᴇ\n"
                    + "ꜱᴘʀɪɴᴛɪɴɢ ᴅᴜʀɪɴɢ ᴘᴀᴛʜꜰɪɴᴅɪɴɢ.\n"
                    + "ᴅɪꜱᴀʙʟᴇ ᴛᴏ ᴡᴀʟᴋ ᴡɪᴛʜᴏᴜᴛ ᴊᴜᴍᴘɪɴɢ.\n"
                    + "ɢʟᴏʙᴀʟ: "
                    + (Config.pathfindingSprintJump() ? "ᴇɴᴀʙʟᴇᴅ" : "ᴅɪꜱᴀʙʟᴇᴅ"),
                Material.FEATHER,
                false),
            BotEntry.action(
                "chunk_load_radius",
                "ᴄʜᴜɴᴋ ʀᴀᴅɪᴜꜱ",
                "ʜᴏᴡ ᴍᴀɴʏ ᴄʜᴜɴᴋꜱ ᴛʜɪꜱ ʙᴏᴛ ʟᴏᴀᴅꜱ.\n"
                    + "-1 = ꜰᴏʟʟᴡ ɢʟᴏʙᴀʟ ᴄᴏɴꜰɪɡ\n"
                    + "0  = ᴅɪꜱᴀʙʟᴇᴅ ꜰᴏʀ ᴛʜɪꜱ ʙᴏᴛ\n"
                    + "1-"
                    + globalMax
                    + " = ꜰɪʜᴇᴅ ʀᴀᴅɪᴜꜱ (ᴄᴀᴘᴘᴇᴅ ᴀᴛ ɢʟᴏʙᴀʟ ᴍᴀx)",
                Material.MAP,
                false),
            BotEntry.action(
                "tab_ping",
                "ᴛᴀʙ ᴘɪɴɢ",
                "ꜱᴇᴛ ᴛʜᴇ ʙᴏᴛ'ꜱ ᴠɪꜱɪʙʟᴇ ᴘɪɴɢ/ʟᴀᴛᴇɴᴄʏ.\n"
                    + "ᴜꜱᴇ 0-9999 ᴍꜱ ᴏʀ ᴛʏᴘᴇ ʀᴇꜱᴇᴛ ᴛᴏ ᴄʟᴇᴀʀ.",
                Material.CLOCK,
                false),
            BotEntry.toggle(
                "pickup_items",
                "ᴘɪᴄᴋ ᴜᴘ ɪᴛᴇᴍꜱ",
                "ᴛʜɪꜱ ʙᴏᴛ ᴘɪᴄᴋꜱ ᴜᴘ ɪᴛᴇᴍ ᴇɴᴛɪᴛɪᴇꜱ\nɪɴᴛᴏ ɪᴛꜱ ɪɴᴠᴇɴᴛᴏʏ ᴡʜᴇɴ ᴇɴᴀʙʟᴇᴅ.",
                Material.HOPPER,
                false),
            BotEntry.toggle(
                "pickup_xp",
                "ᴘɪᴄᴋ ᴜᴘ xᴘ",
                "ᴛʜɪꜱ ʙᴏᴛ ᴄᴏʟʟᴇᴄᴛꜱ ᴇxᴘᴇʀɪᴇɴᴄᴇ ᴏʀʙꜱ\n"
                    + "ᴡʜᴇɴ ᴇɴᴀʙʟᴇᴅ. /ꜰᴘᴘ xᴘ ᴄᴏᴏʟᴅᴏᴡɴ ꜱᴛɪʟʟ ᴀᴘᴘʟɪᴇꜱ.",
                Material.EXPERIENCE_BOTTLE,
                false),
            BotEntry.action(
                "rename",
                "ʀᴇɴᴀᴍᴇ ʙᴏᴛ",
                "ᴄʜᴀɴɢᴇ ᴛʜᴇ ʙᴏᴛ'ꜱ ᴍɪɴᴇᴄʀᴀꜰᴛ ɴᴀᴍᴇ.\n" + "ɴᴀᴍᴇᴛᴀɡ, ᴛᴀʙ ᴀɴᴅ ᴅᴇᴀᴛʜ ᴍᴇꜱꜱᴀɢᴇꜱ ᴜᴘᴅᴀᴛᴇ.",
                Material.NAME_TAG,
                false)));
  }

  private BotCategory chat() {
    return new BotCategory(
        "💬 ᴄʜᴀᴛ",
        Material.WRITABLE_BOOK,
        Material.BOOK,
        Material.YELLOW_STAINED_GLASS_PANE,
        List.of(
            BotEntry.toggle(
                "chat_enabled",
                "ᴄʜᴀᴛ ᴇɴᴀʙʟᴇᴅ",
                "ʙᴏᴛ ꜱᴇɴᴅꜱ ᴄʜᴀᴛ ᴍᴇꜱꜱᴀɡᴇꜱ ᴡʜᴇɴ ᴇɴᴀʙʟᴇᴅ.\n" + "ꜰᴀʟꜱᴇ = ᴘᴇʀᴍᴀɴᴇɴᴛʟʏ ꜱɪʟɘɴᴄᴇᴅ ʙᴏᴛ.",
                Material.WRITABLE_BOOK,
                false),
            BotEntry.cycleTier(
                "chat_tier",
                "ᴄʜᴀᴛ ᴛɪᴇʀ",
                "ᴛʜᴇ ʙᴏᴛ'ꜱ ᴄʜᴀᴛ ᴀᴄᴛɪᴠɪᴛʏ ʟᴇᴠᴇʟ.\n"
                    + "ʀᴀɴᴅᴏᴍ → Qᴜɪᴇᴛ → ᴘᴀꜱꜱɪᴠᴇ → ɴᴏʀᴍᴀʟ\n"
                    + "→ ᴀᴄᴛɪᴠᴇ → ᴄʜᴏᴛᴛʏ → (ʀᴇꜱᴇᴛꜱ ᴛᴏ ʀᴀɴᴅᴏᴍ).",
                Material.COMPARATOR,
                false),
            BotEntry.cyclePersonality(
                "ai_personality",
                "ᴀɪ ᴘᴇʀꜱᴏɴᴀʟɪᴛʏ",
                "ᴛʜᴇ ʙᴏᴛ'ꜱ ᴄᴏɴᴠᴇʀꜱᴀᴛɪᴏɴ ᴘᴇʀꜱᴏɴᴀʟɪᴛʏ.\n"
                    + "ᴄʏᴄʟᴇꜱ ᴛʜʀᴏᴜɢʜ .ᴛxᴛ ꜰɪʟᴇꜱ ɪɴ\n"
                    + "ᴘʟᴜɢɪɴꜱ/FakePlayerPlugin/personalities/",
                Material.KNOWLEDGE_BOOK,
                false)));
  }

  private BotCategory pve() {
    return new BotCategory(
        "🗡 ᴘᴠᴇ",
        Material.IRON_SWORD,
        Material.STONE_SWORD,
        Material.LIME_STAINED_GLASS_PANE,
        List.of(
            BotEntry.toggle(
                "pve_enabled",
                "ꜱᴍᴀʀᴛ ᴀᴛᴛᴀᴄᴋ",
                "ᴡʜᴇɴ ᴇɴᴀʙʟᴇᴅ, ᴛʜɪꜱ ʙᴏᴛ ᴀᴜᴛᴏ-ᴀᴛᴛᴀᴄᴋꜱ\n"
                    + "ɴᴇᴀʀʙʏ ᴍᴏʙꜱ ᴡɪᴛʜ ᴘʀᴏᴘᴇʀ\n"
                    + "ᴡᴇᴀᴘᴏɴ ᴄᴏᴏʟᴅᴏᴡɴꜱ ᴀɴᴅ ꜱᴍᴏᴏᴛʜ ʀᴏᴛᴀᴛɪᴏɴ.",
                Material.IRON_SWORD,
                false),
            BotEntry.mobSelector(
                "pve_mob_type",
                "ꜱᴇʟᴇᴄᴛ ᴛᴀʀɡᴇᴛ ᴍᴏʙꜱ",
                "ᴏᴘᴇɴ ᴀ ᴠɪꜱᴜᴀʟ ꜱᴇʟᴇᴄᴛᴏʀ ᴛᴏ ᴘɪᴄᴋ\n"
                    + "ᴡʜɪᴄʜ ᴍᴏʙ ᴛʏᴘᴇꜱ ᴛʜᴇ ʙᴏᴛ ᴛᴀʀɢᴇᴛꜱ.\n"
                    + "ᴄʟɪᴄᴋ ᴛᴏ ᴛᴏɢɢʟᴇ ᴍᴜʟᴛɪᴘʟᴇ ᴍᴏʙꜱ.\n"
                    + "'ᴀʟʟ ʜᴏꜱᴛɪʟᴇ' = ᴄʟᴇᴀʀ ᴀʟʟ.",
                Material.ZOMBIE_HEAD,
                false),
            BotEntry.toggle(
                "pve_move",
                "ᴍᴏᴠᴇ ᴡʜɪʟᴇ ᴀᴛᴛᴀᴄᴋɪɴɢ",
                "ᴡʜᴇɴ ᴇɴᴀʙʟᴇᴅ, ꜱᴍᴀʀᴛ ᴀᴛᴛᴀᴄᴋ ᴄʜᴀꜱᴇꜱ\n"
                    + "ᴛᴀʀɢᴇᴛꜱ ᴛʜᴀᴛ ᴀʀᴇ ᴏᴜᴛ ᴏꜰ ᴍᴇʟᴇᴇ ʀᴀɴɢᴇ.\n"
                    + "ᴅɪꜱᴀʙʟᴇᴅ = ꜱᴛᴀɴᴅ ꜱᴛɪʟʟ ᴀɴᴅ ᴀᴛᴛᴀᴄᴋ ɴᴇᴀʀʙʏ ᴍᴏʙꜱ.",
                Material.CHAINMAIL_BOOTS,
                false),
            BotEntry.action(
                "pve_range",
                "ᴅᴇᴛᴇᴄᴛ ʀᴀɴɢᴇ",
                "ʜᴏᴡ ꜰᴀʀ (ɪɴ ʙʟᴏᴄᴋꜱ) ᴛʜɘ ʙᴏᴛ ꜱᴄᴀɴꜱ\n"
                    + "ꜰᴏʀ ᴍᴏʙꜱ ᴛᴏ ᴀᴛᴛᴀᴄᴋ.\n"
                    + "ʀᴀɴɢᴇ: 1 – 64 ʙʟᴏᴄᴋꜱ.",
                Material.SPYGLASS,
                false),
            BotEntry.cyclePriority(
                "pve_priority",
                "ᴛᴀʀɡᴇᴛ ᴘʀɪᴏʀɪᴛʏ",
                "ʜᴏᴡ ᴛʜᴇ ʙᴏᴛ ᴄʜᴏᴏꜱᴇꜱ ɪᴛꜱ ᴛᴀʀɡᴇᴛ.\n" + "ᴄʏᴄʟᴇꜱ: nearest ↔ lowest-health",
                Material.COMPARATOR,
                false)));
  }

  private BotCategory automation() {
    return new BotCategory(
        "⚙ ᴀᴜᴛᴏ",
        Material.REDSTONE,
        Material.REPEATER,
        Material.ORANGE_STAINED_GLASS_PANE,
        List.of(
            BotEntry.toggle(
                "auto_eat",
                "ᴀᴜᴛᴏ ᴇᴀᴛ",
                "ᴡʜᴇɴ ᴇɴᴀʙʟᴇᴅ, ᴛʜᴇ ʙᴏᴛ ᴇᴀᴛꜱ\n"
                    + "ꜰᴏᴏᴅ ꜰʀᴏᴍ ɪᴛꜱ ɪɴᴠᴇɴᴛᴏʀʏ ᴡʜᴇɴ\n"
                    + "ʜᴜɴɢᴇʀ ɢᴇᴛꜱ ʟᴏᴡ.",
                Material.COOKED_BEEF,
                false),
            BotEntry.toggle(
                "auto_place_bed",
                "ᴀᴜᴛᴏ ʙᴇᴅ",
                "ᴡʜᴇɴ ɴᴏ ʙᴇᴅ ɪꜱ ɴᴇᴀʀʙʏ, ᴛʜᴇ ʙᴏᴛ\n"
                    + "ᴄᴀɴ ᴘʟᴀᴄᴇ ᴀ ʙᴇᴅ ꜰʀᴏᴍ ɪɴᴠᴇɴᴛᴏʀʏ\n"
                    + "ᴀɴᴅ ʙʀᴇᴀᴋ ɪᴛ ᴀꜰᴛᴇʀ ꜱʟᴇᴇᴘɪɴɢ.",
                Material.RED_BED,
                false),
            BotEntry.immediate(
                "share_control",
                "ꜱʜᴀʀᴇ ᴄᴏɴᴛʀᴏʟ",
                "ᴏᴘᴇɴ ᴀ ʀᴇᴀʟ-ᴘʟᴀʏᴇʀ ꜱᴇʟᴇᴄᴛᴏʀ\n"
                    + "ᴛᴏ ɢʀᴀɴᴛ ᴏʀ ʀᴇᴠᴏᴋᴇ ᴄᴏɴᴛʀᴏʟ.\n"
                    + "ᴏɴʟʏ ᴏᴡɴᴇʀꜱ ᴀɴᴅ ᴀᴅᴍɪɴꜱ ᴄᴀɴ ꜱʜᴀʀᴇ.",
                Material.PLAYER_HEAD,
                false)));
  }

  private BotCategory pvp() {
    return new BotCategory(
        "⚔ ᴘᴠᴘ",
        Material.NETHERITE_SWORD,
        Material.IRON_SWORD,
        Material.RED_STAINED_GLASS_PANE,
        List.of(
            BotEntry.comingSoon(
                "pvp_difficulty",
                "ᴅɪꜰꜰɪᴄᴜʟᴛʏ",
                "ᴏᴠᴇʀʀɪᴅᴇ ᴛʜɪꜱ ʙᴏᴛ'ꜱ ꜱᴋɪʟʟ ʟᴇᴠᴇʟ.\n"
                    + "ɴᴘᴄ / ᴇᴀꜢʏ / ᴍᴇᴅɪᴜᴍ / ʜᴀʀᴅ / ᴛɪᴇʀ1 / ʜᴀᴄᴋᴇʀ.",
                Material.DIAMOND_SWORD),
            BotEntry.comingSoon(
                "pvp_combat_mode",
                "ᴄᴏᴍʙᴀᴛ ᴍᴏᴅᴇ",
                "ᴘᴇʀ-ʙᴏᴛ ᴄʀʏꜱᴛᴀʟ / ꜱᴡᴏʀᴅ / ꜰɪꜱᴛ\nᴄᴏᴍʙᴀᴛ ꜱᴛʏʟᴇ ꜱᴇʟᴇᴄᴛɪᴏɴ.",
                Material.END_CRYSTAL),
            BotEntry.comingSoon(
                "pvp_critting",
                "ᴄʀɪᴛᴛɪɴɢ",
                "ʙᴏᴛ ʟᴀɴᴅꜱ ᴄʀɪᴛɪᴄᴀʟ ʜɪᴛꜱ ʙʏ\nꜰᴀʟʟɪɴɢ ᴅᴜʀɪɴɢ ᴀᴛᴛᴀᴄᴋꜱ.",
                Material.NETHERITE_SWORD),
            BotEntry.comingSoon(
                "pvp_s_tapping",
                "ꜱ-ᴛᴀᴘᴘɪɴɢ",
                "ʙᴏᴛ ᴛᴀᴘꜱ ꜱ ᴅᴜʀɪɴɢ ꜱᴡɪɴɢ\n" + "ᴛᴏ ʀᴇꜱᴇᴛ ᴀᴛᴛᴀᴄᴋ ᴄᴏᴏʟᴅᴏᴡɴ.",
                Material.CLOCK),
            BotEntry.comingSoon(
                "pvp_strafing",
                "ꜱᴛʀᴀꜰɪɴɢ",
                "ʙᴏᴛ ᴄɪʀᴄʟᴇꜱ ᴀʀᴏᴜɴᴅ ᴛʜᴇ ᴛᴀʀɡᴇᴛ\nᴡʜɪʟᴇ ꜰɪɡʜᴛɪɴɢ.",
                Material.FEATHER),
            BotEntry.comingSoon(
                "pvp_shield",
                "ꜱʜɪᴇʟᴅɪɴɢ",
                "ʙᴏᴛ ᴄᴀʀʀɪᴇꜱ ᴀɴᴅ ᴜꜱᴇꜱ ᴀ ꜱʜɪᴇʟᴅ\nᴛᴏ ʙʟᴏᴄᴋ ɪɴᴄᴏᴍɪɴɢ ᴀᴛᴛᴀᴄᴋꜱ.",
                Material.SHIELD),
            BotEntry.comingSoon(
                "pvp_speed_buffs",
                "ꜱᴘᴇᴇᴅ ʙᴜꜰꜰꜱ",
                "ʙᴏᴛ ʜᴀꜱ ꜱᴘᴇᴇᴅ & ꜱᴛʀᴇɴɡʜ ᴘᴏᴛɪᴏɴ\nᴇ꜀꜀ᴛꜱ ᴀᴄɪᴠᴇ.",
                Material.SUGAR),
            BotEntry.comingSoon(
                "pvp_jump_reset",
                "ᴊᴜᴍᴘ ʀᴇꜱᴇᴛ",
                "ʙᴏᴛ ᴊᴜᴍᴘꜱ ᴊᴜꜱᴛ ʙᴇꜰᴏʀᴇ ꜱᴡɪɴɢɪɴɢ\n" + "ᴛᴏ ɡᴀɪɴ ᴛʜᴇ ᴡ-ᴛᴀᴘ ᴋɴᴏᴄᴋʙᴀᴄᴋ ʙᴏɴᴜꜱ.",
                Material.SLIME_BALL),
            BotEntry.comingSoon(
                "pvp_random",
                "ʀᴀɴᴅᴏᴍ ᴘʟᴀʏꜱᴛʏʟᴇ",
                "ʀᴀɴᴅᴏᴍɪꜱᴇ ᴛᴇᴄʜɴɪQᴜᴇꜱ ᴇᴀᴄʜ ʀᴏᴜɴᴅ\nᴛᴏ ᴋᴇᴇᴘ ᴛʜᴇ ꜰɪɡʜᴛ ᴜɴᴘʀᴇᴅɪᴄᴛᴀʙʟᴇ.",
                Material.COMPARATOR),
            BotEntry.comingSoon(
                "pvp_gear",
                "ɢᴇᴀʀ ᴛʏᴘᴇ",
                "ʙᴏᴛ ᴡᴇᴀʀꜱ ᴅɪᴀᴍᴏɴᴅ ᴏʀ\nɴᴇᴛʜᴇʀɪᴛᴇ ᴀʀᴍᴏᴜʀ.",
                Material.DIAMOND_CHESTPLATE),
            BotEntry.comingSoon(
                "pvp_auto_refill",
                "ᴀᴜᴛᴏ-ʀᴇꜰɪʟʟ ᴛᴏᴛᴇᴍ",
                "ʙᴏᴛ ᴀᴜᴛᴏᴍᴀᴛɪᴄᴀʟʟʏ ʀᴇ-ᴇQᴜɪᴘꜱ ᴀ\nᴛᴏᴍ ᴀꜰᴛᴇʀ ᴘᴏᴘᴘɪɴɢ ᴏɴᴇ.",
                Material.TOTEM_OF_UNDYING),
            BotEntry.comingSoon(
                "pvp_auto_respawn",
                "ᴀᴜᴛᴏ-ʀᴇꜱᴘᴀᴡɴ",
                "ʙᴏᴛ ᴀᴜᴛᴏᴍᴀᴛɪᴄᴀʟʟʏ ʀᴇꜱᴘᴀᴡɴꜱ\nᴀɴᴅ ʀᴇᴊᴏɪɴꜱ ᴀꜰᴛᴇʀ ᴅᴇᴀᴛʜ.",
                Material.RESPAWN_ANCHOR),
            BotEntry.comingSoon(
                "pvp_spawn_prot",
                "ꜱᴘᴀᴡɴ ᴘʀᴏᴛᴇᴄᴛɪᴏɴ",
                "ʙᴏᴛ ꜱᴛᴀʏꜱ ɪɴᴠᴜʟɴᴇʀᴀʙʟᴇ ꜰᴏʀ\nᴀ ꜱʜʏʀᴛ ɡᴀᴄᴇ ᴘᴇʀᴏᴅ ᴀᴛ ꜱᴘᴀᴡɴ.",
                Material.GRASS_BLOCK),
            BotEntry.comingSoon(
                "pvp_target",
                "ᴛᴀʀɡᴇᴛ ᴘʀɪᴏʀɪᴛʏ",
                "ᴄʜᴏᴏꜱᴇ ᴡʜɪᴄʜ ᴘʟᴀʏᴇʀ ᴛʏᴘᴇ ᴛʜᴪꜱ\nʙᴏᴛ ᴘʀɪᴏʀɪᴛɪꜬ ᴀꜱ ᴛᴀʀɡᴇᴛ.",
                Material.ORANGE_DYE),
            BotEntry.comingSoon(
                "pvp_aggression",
                "ᴀɡɡʀᴇꜱꜱɪᴏɴ",
                "ᴄᴏɴᴛʀᴏʟ ʜᴏᴡ ᴀɢɡʀᴇꜱꜱɪᴏɴ ʙᴏᴛ ᴡɪʟʟ\nʙᴀᴄᴋ ᴏ꜡꜡.",
                Material.BLAZE_POWDER),
            BotEntry.comingSoon(
                "pvp_flee_health",
                "ꜰʟᴇᴇ ʜᴇᴀʟᴛʜ",
                "ʙᴏᴛ ʀᴇᴛʀᴇᴀᴛꜱ ᴡʜᴇɴ ɪᴛꜱ ʜᴇᴀʟᴛʜ\nᴅʀᴏᴘꜱ ʙᴀʟᴏᴡ ᴛʜɪꜱ ᴠᴀʟᴜᴇ.",
                Material.RED_DYE),
            BotEntry.comingSoon(
                "pvp_combo_length",
                "ᴄᴏᴍʙᴏ ʟᴇɴɡᴛʜ",
                "ᴍᴀxɪᴍᴜᴍ ʜɪᴛꜱ ɪɴ ᴀ ꜱɪɴɡʟᴇ ʙᴜʀꜱᴛ\nʙᴇꜰᴏʀᴇ ʙᴀᴄᴋɪɴɢ ᴏ꜡꜡.",
                Material.IRON_SWORD)));
  }

  private BotCategory pathfinding() {
    return new BotCategory(
        "🧭 ᴘᴀᴛʜ",
        Material.COMPASS,
        Material.MAP,
        Material.CYAN_STAINED_GLASS_PANE,
        List.of(
            BotEntry.toggle(
                "follow_player",
                "ꜰᴏʟʟᴏᴡ ᴘʟᴀʏᴇʀ",
                "ʙᴏᴛ ᴄᴏɴᴛɪɴᴜᴏᴜꜱʟʏ ꜰᴏʟʟᴏᴡꜱ ᴛʜᴇ\n"
                    + "ᴘʟᴀʏᴇʀ ᴡʜᴏ ᴏᴘᴇɴᴇᴅ ᴛʜɪꜱ ɢᴜɪ.\n"
                    + "ᴜꜱᴇꜱ ᴘᴀᴛʜꜰɪɴᴅɪɴɢ ᴛᴏ ɴᴀᴠɪɡᴀᴛᴇ.",
                Material.LEAD,
                false),
            BotEntry.toggle(
                "nav_parkour",
                "ᴘᴀʀᴋᴏᴜʀ",
                "ʙᴏᴛ ꜱᴘʀɪɴᴛ-ᴊᴜᴍᴘꜱ ᴀᴄʀᴏꜱꜱ 1-2 ʙʟᴏᴄᴋ\n"
                    + "ɢᴀᴘꜱ ᴅᴜʀɪɴɢ ɴᴀᴠɪɡᴀᴛɪᴏɴ.\n"
                    + "ɢʟᴏʙᴀʟ: "
                    + (Config.pathfindingParkour() ? "ᴇɴᴀʙʟᴇᴅ" : "ᴅɪꜱᴀʙʟᴇᴅ"),
                Material.SLIME_BALL,
                false),
            BotEntry.toggle(
                "nav_sprint_jump",
                "ꜱᴘʀɪɴᴛ-ᴊᴜᴍᴘ",
                "ʙᴏᴛ ᴊᴜᴍᴘꜱ ᴘᴇʀɪᴏᴅɪᴄᴀʟʟʏ ᴡʜɪʟᴇ\n"
                    + "ꜱᴘʀɪɴᴛɪɴɢ ᴅᴜʀɪɴɢ ᴘᴀᴛʜꜰɪɴᴅɪɴɢ.\n"
                    + "ɢʟᴏʙᴀʟ: "
                    + (Config.pathfindingSprintJump() ? "ᴇɴᴀʙʟᴇᴅ" : "ᴅɪꜱᴀʙʟᴇᴅ"),
                Material.FEATHER,
                false),
            BotEntry.toggle(
                "nav_break_blocks",
                "ʙʀᴇᴀᴋ ʙʟᴏᴄᴋꜱ",
                "ʙᴏᴛ ʙʀᴇᴀᴋꜱ ᴏʙꜱᴛʀᴜᴄᴛɪɴɢ ʙʟᴏᴄᴋꜱ\n"
                    + "ᴅᴜʀɪɴɢ ɴᴀᴠɪɡᴀᴛɪᴏɴ.\n"
                    + "ɢʟᴏʙᴀʟ: "
                    + (Config.pathfindingBreakBlocks() ? "ᴇɴᴀʙʟᴇᴅ" : "ᴅɪꜱᴀʙʟᴇᴅ"),
                Material.DIAMOND_PICKAXE,
                false),
            BotEntry.toggle(
                "nav_place_blocks",
                "ᴘʟᴀᴄᴇ ʙʟᴏᴄᴋꜱ",
                "ʙᴏᴛ ᴘʟᴀᴄᴇꜱ ʙʟᴏᴄᴋꜱ ᴛᴏ ʙʀɪᴅɢᴇ ɡᴀᴘꜱ\n"
                    + "ᴅᴜʀɪɴɢ ɴᴀᴠɪɡᴀᴛɪᴏɴ.\n"
                    + "ɢʟᴏʙᴀʟ: "
                    + (Config.pathfindingPlaceBlocks() ? "ᴇɴᴀʙʟᴇᴅ" : "ᴅɪꜱᴀʙʟᴇᴅ"),
                Material.GRASS_BLOCK,
                false)));
  }

  private BotCategory danger() {
    return new BotCategory(
        "⚠ ᴅᴀɴɡᴇʀ",
        Material.TNT,
        Material.COAL,
        Material.RED_STAINED_GLASS_PANE,
        List.of(
            BotEntry.danger(
                "reset_all",
                "ʀᴇꜱᴇᴛ ᴀʟʟ ꜱᴇᴛᴛɪɴɡꜱ",
                "⚠ ʀᴇꜱᴇᴛ ᴇᴠᴇʀʏ ꜱᴇᴛᴛɪɴɡ ᴏɴ ᴛʜɪꜱ ʙᴏᴛ\nᴛᴏ ᴅᴇꜰᴀᴜʟᴛ ᴠᴀʟᴜᴇꜱ.\n"
                    + "ɢᴇɴᴇʀᴀʟ, ᴄʜᴀᴛ, ᴘᴠᴇ, ᴘᴀᴛʜꜰɪɴᴅɪɴɡ,\n"
                    + "ᴄᴏᴍᴍᴀɴᴅꜱ — ᴀʟʟ ʀᴇꜱᴇᴛ.",
                Material.REDSTONE_BLOCK,
                true),
            BotEntry.danger(
                "delete",
                "ᴅᴇʟᴇᴛᴇ ʙᴏᴛ",
                "⚠ ᴘᴇʀᴍᴀɴᴇɴᴛʟʏ ʀᴇᴍᴏᴠᴇ ᴛʜɪꜱ ʙᴏᴛ.\nᴛʜɪꜱ ᴀᴄɪᴠᴇ ᴄᴀɴɴᴏᴛ ʙᴇ ᴜɴᴅᴏɴᴇ.",
                Material.TNT,
                true)));
  }

  private record GuiHolder(UUID uuid) implements InventoryHolder {
    @SuppressWarnings("NullableProblems")
    @Override
    public Inventory getInventory() {
      return null;
    }
  }

  private record MobSelectorHolder(UUID playerUuid) implements InventoryHolder {
    @SuppressWarnings("NullableProblems")
    @Override
    public Inventory getInventory() {
      return null;
    }
  }

  private record ShareSelectorHolder(UUID playerUuid) implements InventoryHolder {
    @SuppressWarnings("NullableProblems")
    @Override
    public Inventory getInventory() {
      return null;
    }
  }

  private record MobDisplay(
      EntityType type, Material material, String displayName, String category) {}

  private record BotCategory(
      String label,
      Material activeMat,
      Material inactiveMat,
      Material separatorGlass,
      List<BotEntry> entries) {}

  private enum BotEntryType {
    TOGGLE,
    CYCLE_TIER,
    CYCLE_PERSONALITY,
    CYCLE_PRIORITY,
    ACTION,
    MOB_SELECTOR,
    IMMEDIATE,
    DANGER,
    COMING_SOON
  }

  private record BotEntry(
      String id,
      String label,
      String description,
      Material icon,
      BotEntryType type,
      boolean opOnly) {
    static BotEntry toggle(String id, String label, String desc, Material icon, boolean opOnly) {
      return new BotEntry(id, label, desc, icon, BotEntryType.TOGGLE, opOnly);
    }

    static BotEntry cycleTier(String id, String label, String desc, Material icon, boolean opOnly) {
      return new BotEntry(id, label, desc, icon, BotEntryType.CYCLE_TIER, opOnly);
    }

    static BotEntry cyclePersonality(
        String id, String label, String desc, Material icon, boolean opOnly) {
      return new BotEntry(id, label, desc, icon, BotEntryType.CYCLE_PERSONALITY, opOnly);
    }

    static BotEntry cyclePriority(
        String id, String label, String desc, Material icon, boolean opOnly) {
      return new BotEntry(id, label, desc, icon, BotEntryType.CYCLE_PRIORITY, opOnly);
    }

    static BotEntry action(String id, String label, String desc, Material icon, boolean opOnly) {
      return new BotEntry(id, label, desc, icon, BotEntryType.ACTION, opOnly);
    }

    static BotEntry mobSelector(
        String id, String label, String desc, Material icon, boolean opOnly) {
      return new BotEntry(id, label, desc, icon, BotEntryType.MOB_SELECTOR, opOnly);
    }

    static BotEntry immediate(String id, String label, String desc, Material icon, boolean opOnly) {
      return new BotEntry(id, label, desc, icon, BotEntryType.IMMEDIATE, opOnly);
    }

    static BotEntry danger(String id, String label, String desc, Material icon, boolean opOnly) {
      return new BotEntry(id, label, desc, icon, BotEntryType.DANGER, opOnly);
    }

    static BotEntry comingSoon(String id, String label, String desc, Material icon) {
      return new BotEntry(id, label, desc, icon, BotEntryType.COMING_SOON, false);
    }
  }

  private record ChatInputSes(String inputType, UUID botUuid, int[] guiState, int cleanupTaskId) {}
}
