package me.bill.fakePlayerPlugin.api;

import org.bukkit.command.CommandSender;
import org.jetbrains.annotations.NotNull;

public interface FppAddonCommand {
  @NotNull String getName();
  @NotNull String getDescription();
  @NotNull String getUsage();
  @NotNull String getPermission();
  default @NotNull java.util.List<String> getAliases() { return java.util.Collections.emptyList(); }
  boolean execute(@NotNull CommandSender sender, @NotNull String[] args);
  default @NotNull java.util.List<String> tabComplete(@NotNull CommandSender sender, @NotNull String[] args) { return java.util.Collections.emptyList(); }
}
