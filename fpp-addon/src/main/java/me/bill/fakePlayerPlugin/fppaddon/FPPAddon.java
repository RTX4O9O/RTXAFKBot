package me.bill.fakePlayerPlugin.fppaddon;

import me.bill.fakePlayerPlugin.FakePlayerPlugin;
import me.bill.fakePlayerPlugin.api.FppApi;
import me.bill.fakePlayerPlugin.fppaddon.command.FarmCommand;
import me.bill.fakePlayerPlugin.fppaddon.command.PingCommand;
import me.bill.fakePlayerPlugin.fppaddon.feature.AutoEquipmentHandler;
import me.bill.fakePlayerPlugin.fppaddon.feature.SprintJumpHandler;
import me.bill.fakePlayerPlugin.fppaddon.extension.PlaceWorldEditExtension;
import me.bill.fakePlayerPlugin.fppaddon.extension.StripMineExtension;
import me.bill.fakePlayerPlugin.fppaddon.settings.AutomationSettingsTab;
import me.bill.fakePlayerPlugin.fppaddon.settings.PathfindingSettingsTab;
import org.bukkit.Bukkit;
import org.bukkit.event.Listener;
import org.bukkit.plugin.java.JavaPlugin;

public final class FPPAddon extends JavaPlugin implements Listener {

  private FppApi api;
  private FarmCommand farmCommand;
  private PingCommand pingCommand;
  private StripMineExtension stripMineExtension;
  private PlaceWorldEditExtension placeWorldEditExtension;
  private AutomationSettingsTab automationSettingsTab;
  private PathfindingSettingsTab pathfindingSettingsTab;
  private AutoEquipmentHandler autoEquipmentHandler;
  private SprintJumpHandler sprintJumpHandler;

  @Override
  public void onEnable() {
    var plugin = Bukkit.getPluginManager().getPlugin("FakePlayerPlugin");
    if (!(plugin instanceof FakePlayerPlugin fpp)) {
      getLogger().severe("FakePlayerPlugin is required.");
      Bukkit.getPluginManager().disablePlugin(this);
      return;
    }

    api = fpp.getFppApi();
    if (api == null) {
      getLogger().severe("FPP API is not ready.");
      Bukkit.getPluginManager().disablePlugin(this);
      return;
    }

    farmCommand = new FarmCommand(fpp);
    api.registerCommand(farmCommand);
    Bukkit.getPluginManager().registerEvents(farmCommand, this);

    pingCommand = new PingCommand(fpp);
    api.registerCommand(pingCommand);

    stripMineExtension = new StripMineExtension(fpp);
    api.registerCommandExtension(stripMineExtension);

    placeWorldEditExtension = new PlaceWorldEditExtension(fpp);
    api.registerCommandExtension(placeWorldEditExtension);

    automationSettingsTab = new AutomationSettingsTab(fpp);
    api.registerSettingsTab(automationSettingsTab);

    pathfindingSettingsTab = new PathfindingSettingsTab(fpp);
    api.registerSettingsTab(pathfindingSettingsTab);

    autoEquipmentHandler = new AutoEquipmentHandler(fpp);
    api.registerTickHandler(autoEquipmentHandler);

    sprintJumpHandler = new SprintJumpHandler(fpp);
    api.registerTickHandler(sprintJumpHandler);

    getLogger().info("FPP Addon enabled.");
  }

  @Override
  public void onDisable() {
    if (api != null) {
      if (farmCommand != null) api.unregisterCommand(farmCommand);
      if (pingCommand != null) api.unregisterCommand(pingCommand);
      if (stripMineExtension != null) api.unregisterCommandExtension(stripMineExtension);
      if (placeWorldEditExtension != null) api.unregisterCommandExtension(placeWorldEditExtension);
      if (automationSettingsTab != null) api.unregisterSettingsTab(automationSettingsTab);
      if (pathfindingSettingsTab != null) api.unregisterSettingsTab(pathfindingSettingsTab);
      if (autoEquipmentHandler != null) api.unregisterTickHandler(autoEquipmentHandler);
      if (sprintJumpHandler != null) api.unregisterTickHandler(sprintJumpHandler);
    }
    if (farmCommand != null) farmCommand.shutdown();
    if (stripMineExtension != null) stripMineExtension.shutdown();
  }
}
