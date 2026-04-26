package me.bill.fakePlayerPlugin.api;

import org.jetbrains.annotations.NotNull;

@FunctionalInterface
public interface FppBotTickHandler {
  void onTick(@NotNull FppBot bot, @NotNull org.bukkit.entity.Player entity);
}
