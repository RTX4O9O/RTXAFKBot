package me.bill.fpphelloaddon;

import java.util.List;
import me.bill.fakePlayerPlugin.FakePlayerPlugin;
import me.bill.fakePlayerPlugin.api.FppAddonCommand;
import me.bill.fakePlayerPlugin.api.FppApi;
import net.kyori.adventure.text.Component;
import org.bukkit.Bukkit;
import org.bukkit.command.CommandSender;
import org.bukkit.plugin.java.JavaPlugin;

public final class HelloWorldAddon extends JavaPlugin {

  private FppApi api;
  private HelloCommand command;

  @Override
  public void onEnable() {
    var plugin = Bukkit.getPluginManager().getPlugin("FakePlayerPlugin");
    if (!(plugin instanceof FakePlayerPlugin fpp) || (api = fpp.getFppApi()) == null) {
      getLogger().severe("FakePlayerPlugin is required.");
      Bukkit.getPluginManager().disablePlugin(this);
      return;
    }
    command = new HelloCommand();
    api.registerCommand(command);
  }

  @Override
  public void onDisable() {
    if (api != null && command != null) api.unregisterCommand(command);
  }

  private static final class HelloCommand implements FppAddonCommand {
    @Override public String getName() { return "hello"; }
    @Override public String getDescription() { return "Prints a hello world sample."; }
    @Override public String getUsage() { return "[world]"; }
    @Override public String getPermission() { return "fpp.helloaddon"; }
    @Override public boolean execute(CommandSender sender, String[] args) {
      sender.sendMessage(Component.text("Hello world from the sample addon!"));
      return true;
    }
    @Override public List<String> tabComplete(CommandSender sender, String[] args) { return List.of(); }
  }
}
