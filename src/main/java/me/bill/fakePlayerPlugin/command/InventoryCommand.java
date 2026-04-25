package me.bill.fakePlayerPlugin.command;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import me.bill.fakePlayerPlugin.api.event.FppBotDespawnEvent;
import me.bill.fakePlayerPlugin.fakeplayer.FakePlayer;
import me.bill.fakePlayerPlugin.fakeplayer.FakePlayerManager;
import me.bill.fakePlayerPlugin.gui.BotSettingGui;
import me.bill.fakePlayerPlugin.lang.Lang;
import me.bill.fakePlayerPlugin.permission.Perm;
import me.bill.fakePlayerPlugin.util.BotAccess;
import org.bukkit.*;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.PlayerDeathEvent;
import org.bukkit.event.inventory.*;
import org.bukkit.event.player.PlayerInteractAtEntityEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.inventory.Inventory;
import org.bukkit.plugin.Plugin;

public class InventoryCommand implements FppCommand, Listener {

  private final FakePlayerManager manager;
  private final Plugin plugin;
  private final BotSettingGui botSettingGui;
  private final Map<UUID, UUID> sessions = new ConcurrentHashMap<>();
  private final Map<UUID, UUID> botLocks = new ConcurrentHashMap<>();

  public InventoryCommand(FakePlayerManager manager, Plugin plugin, BotSettingGui botSettingGui) {
    this.manager = manager;
    this.plugin = plugin;
    this.botSettingGui = botSettingGui;
  }

  @Override
  public String getName() {
    return "inventory";
  }

  @Override
  public List<String> getAliases() {
    return List.of("inv");
  }

  @Override
  public String getUsage() {
    return "/fpp inventory <bot>";
  }

  @Override
  public String getDescription() {
    return "Open a bot's full inventory";
  }

  @Override
  public String getPermission() {
    return Perm.INVENTORY_CMD;
  }

  @Override
  public boolean canUse(CommandSender sender) {
    return Perm.has(sender, Perm.INVENTORY_CMD);
  }

  @Override
  public boolean execute(CommandSender sender, String[] args) {
    if (!(sender instanceof Player player)) {
      sender.sendMessage(Lang.get("inv-player-only"));
      return false;
    }
    if (args.length == 0) {
      sender.sendMessage(Lang.get("inv-usage"));
      return false;
    }
    FakePlayer fp = manager.getByName(args[0]);
    if (fp == null) {
      sender.sendMessage(Lang.get("inv-not-found", "name", args[0]));
      return false;
    }
    if (fp.getPlayer() == null || fp.isBodyless()) {
      sender.sendMessage(Lang.get("inv-bodyless", "name", fp.getDisplayName()));
      return false;
    }
    if (!BotAccess.canAdminister(player, fp)) {
      sender.sendMessage(Lang.get("no-permission"));
      return false;
    }
    openGui(player, fp);
    player.sendMessage(Lang.get("inv-opened", "name", fp.getDisplayName()));
    return true;
  }

  @Override
  public List<String> tabComplete(CommandSender sender, String[] args) {
    if (args.length == 1) {
      String lower = args[0].toLowerCase();
      return manager.getActivePlayers().stream()
          .map(FakePlayer::getName)
          .filter(n -> n.toLowerCase().startsWith(lower))
          .toList();
    }
    return List.of();
  }

  public void openGui(Player viewer, FakePlayer fp) {
    if (sessions.containsKey(viewer.getUniqueId())) {
      viewer.sendMessage(Lang.get("inv-viewer-busy"));
      return;
    }
    Player botPlayer = fp.getPlayer();
    UUID owner = botLocks.putIfAbsent(fp.getUuid(), viewer.getUniqueId());
    if (owner != null) {
      viewer.sendMessage(Lang.get("inv-busy", "name", fp.getDisplayName()));
      return;
    }
    if (botPlayer == null || !botPlayer.isOnline() || fp.isBodyless()) {
      botLocks.remove(fp.getUuid(), viewer.getUniqueId());
      viewer.sendMessage(Lang.get("inv-bodyless", "name", fp.getDisplayName()));
      return;
    }
    sessions.put(viewer.getUniqueId(), fp.getUuid());
    viewer.openInventory(botPlayer.getInventory());
    botPlayer
        .getLocation()
        .getWorld()
        .playSound(
            botPlayer.getLocation(), Sound.BLOCK_CHEST_OPEN, SoundCategory.BLOCKS, 0.5f, 1.0f);
  }

  @EventHandler(priority = EventPriority.LOW)
  public void onInventoryClick(InventoryClickEvent event) {
    if (!(event.getWhoClicked() instanceof Player player)) return;
    UUID botUuid = sessions.get(player.getUniqueId());
    if (botUuid == null) return;

    FakePlayer fp = manager.getByUuid(botUuid);
    if (fp == null || fp.getPlayer() == null || fp.isBodyless()) {
      event.setCancelled(true);
      player.closeInventory();
      return;
    }

    Inventory top = event.getView().getTopInventory();
    if (top != fp.getPlayer().getInventory()) return;

    InventoryAction action = event.getAction();
    if (action == InventoryAction.CLONE_STACK
        || action == InventoryAction.COLLECT_TO_CURSOR
        || action == InventoryAction.UNKNOWN) {
      event.setCancelled(true);
    }
  }

  @EventHandler(priority = EventPriority.LOW, ignoreCancelled = true)
  public void onInventoryDrag(InventoryDragEvent event) {
    if (!(event.getWhoClicked() instanceof Player player)) return;
    UUID botUuid = sessions.get(player.getUniqueId());
    if (botUuid == null) return;

    FakePlayer fp = manager.getByUuid(botUuid);
    if (fp == null || fp.getPlayer() == null || fp.isBodyless()) {
      event.setCancelled(true);
      player.closeInventory();
      return;
    }

    Inventory top = event.getView().getTopInventory();
    if (top != fp.getPlayer().getInventory()) return;
  }

  @EventHandler(priority = EventPriority.MONITOR)
  public void onInventoryClose(InventoryCloseEvent event) {
    if (!(event.getPlayer() instanceof Player viewer)) return;
    UUID botUuid = sessions.remove(viewer.getUniqueId());
    if (botUuid == null) return;
    botLocks.remove(botUuid, viewer.getUniqueId());

    FakePlayer fp = manager.getByUuid(botUuid);
    if (fp != null && fp.getPlayer() != null && !fp.isBodyless()) {
      fp.getPlayer()
          .getLocation()
          .getWorld()
          .playSound(
              fp.getPlayer().getLocation(),
              Sound.BLOCK_CHEST_CLOSE,
              SoundCategory.BLOCKS,
              0.5f,
              1.0f);
    }
  }

  @EventHandler(priority = EventPriority.NORMAL, ignoreCancelled = true)
  public void onRightClickBot(PlayerInteractAtEntityEvent event) {
    if (event.getHand() != org.bukkit.inventory.EquipmentSlot.HAND) return;
    if (!(event.getRightClicked() instanceof Player botPlayer)) return;
    FakePlayer fp = manager.getByEntity(botPlayer);
    if (fp == null || fp.isBodyless()) return;

    Player player = event.getPlayer();

    if (player.isSneaking()
        && me.bill.fakePlayerPlugin.config.Config.isBotShiftRightClickSettingsEnabled()
        && Perm.has(player, Perm.SETTINGS)) {
      event.setCancelled(true);
      if (!BotAccess.canAdminister(player, fp)) {
        player.sendMessage(Lang.get("no-permission"));
        return;
      }
      botSettingGui.open(player, fp);
      return;
    }

    if (!me.bill.fakePlayerPlugin.config.Config.isBotRightClickEnabled()) return;

    if (fp.hasRightClickCommand()) {
      if (!BotAccess.canAdminister(player, fp)) return;
      event.setCancelled(true);

      Bukkit.dispatchCommand(fp.getPlayer(), fp.getRightClickCommand());
      return;
    }

    if (!Perm.has(player, Perm.INVENTORY_RIGHTCLICK)) return;
    if (!BotAccess.canAdminister(player, fp)) return;
    event.setCancelled(true);
    openGui(player, fp);
    player.sendMessage(Lang.get("inv-opened", "name", fp.getDisplayName()));
  }

  @EventHandler(priority = EventPriority.MONITOR)
  public void onViewerQuit(PlayerQuitEvent event) {
    UUID viewerUuid = event.getPlayer().getUniqueId();
    UUID botUuid = sessions.remove(viewerUuid);
    if (botUuid != null) botLocks.remove(botUuid, viewerUuid);
  }

  @EventHandler(priority = EventPriority.MONITOR)
  public void onBotDespawn(FppBotDespawnEvent event) {
    UUID botUuid = event.getBot().getUuid();
    botLocks.remove(botUuid);
    for (Map.Entry<UUID, UUID> entry : new HashMap<>(sessions).entrySet()) {
      if (!botUuid.equals(entry.getValue())) continue;
      Player viewer = Bukkit.getPlayer(entry.getKey());
      if (viewer != null) viewer.closeInventory();
      sessions.remove(entry.getKey());
    }
  }

  @EventHandler(priority = EventPriority.MONITOR)
  public void onBotDeath(PlayerDeathEvent event) {
    FakePlayer fp = manager.getByEntity(event.getEntity());
    if (fp == null) return;
    UUID botUuid = fp.getUuid();
    botLocks.remove(botUuid);
    for (Map.Entry<UUID, UUID> entry : new HashMap<>(sessions).entrySet()) {
      if (!botUuid.equals(entry.getValue())) continue;
      Player viewer = Bukkit.getPlayer(entry.getKey());
      if (viewer != null) viewer.closeInventory();
      sessions.remove(entry.getKey());
    }
  }

  public void refreshOpenGui(UUID botUuid) {
    // Native inventory updates automatically in the client; no manual refresh needed.
  }
}
