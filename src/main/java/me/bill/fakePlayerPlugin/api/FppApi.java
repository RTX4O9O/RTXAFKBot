package me.bill.fakePlayerPlugin.api;

import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.bukkit.Location;
import org.bukkit.entity.Player;
import org.bukkit.plugin.Plugin;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

public interface FppApi {
  @NotNull Collection<FppBot> getBots();
  @NotNull Collection<FppBot> getBotsControllableBy(@NotNull Player player);
  @NotNull Collection<FppBot> getBotsOwnedBy(@NotNull Player player);
  @NotNull Optional<FppBot> getBot(@NotNull String name);
  @NotNull Optional<FppBot> getBot(@NotNull UUID uuid);
  boolean isBot(@NotNull Player player);
  @NotNull Optional<FppBot> asBot(@NotNull Player player);
  boolean canControlBot(@NotNull Player player, @NotNull FppBot bot);
  int getBotCount();
  @NotNull Optional<FppBot> spawnBot(@NotNull Location location, @Nullable Player spawner, @Nullable String name);
  boolean despawnBot(@NotNull String name);
  boolean despawnBot(@NotNull FppBot bot);
  void registerCommand(@NotNull FppAddonCommand command);
  void unregisterCommand(@NotNull FppAddonCommand command);
  void registerCommandExtension(@NotNull FppCommandExtension extension);
  void unregisterCommandExtension(@NotNull FppCommandExtension extension);
  void registerTickHandler(@NotNull FppBotTickHandler handler);
  void unregisterTickHandler(@NotNull FppBotTickHandler handler);
  void registerSettingsTab(@NotNull FppSettingsTab tab);
  void unregisterSettingsTab(@NotNull FppSettingsTab tab);
  void registerBotSettingsTab(@NotNull FppSettingsTab tab);
  void unregisterBotSettingsTab(@NotNull FppSettingsTab tab);
  void registerAddon(@NotNull FppAddon addon);
  void unregisterAddon(@NotNull FppAddon addon);
  void sayAsBot(@NotNull FppBot bot, @NotNull String message);
  void navigateTo(@NotNull FppBot bot, @NotNull Location destination, @Nullable Runnable onArrive);
  void navigateTo(@NotNull FppBot bot, @NotNull Location destination, @Nullable Runnable onArrive, @Nullable Runnable onFail, @Nullable Runnable onCancel);
  void navigateTo(@NotNull FppBot bot, @NotNull Location destination, @Nullable Runnable onArrive, @Nullable Runnable onFail, @Nullable Runnable onCancel, double arrivalDistance);
  void cancelNavigation(@NotNull FppBot bot);
  boolean isNavigating(@NotNull FppBot bot);
  void setNavigationGoal(@NotNull FppBot bot, @NotNull FppNavigationGoal goal);
  void clearNavigationGoal(@NotNull FppBot bot);
  boolean runAsBot(@NotNull FppBot bot, @NotNull String command);
  boolean isBotOnline(@NotNull UUID uuid);
  @NotNull String getVersion();
  @NotNull Plugin getPlugin();
  @Nullable Player getOnlinePlayer(@NotNull String name);
  int getOnlineCount();

  // ── Service registry ────────────────────────────────────────────────────────
  <T> void registerService(@NotNull Class<T> serviceClass, @NotNull T instance);
  <T> @Nullable T getService(@NotNull Class<T> serviceClass);
  boolean hasService(@NotNull Class<?> serviceClass);
}
