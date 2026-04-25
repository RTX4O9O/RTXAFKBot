package me.bill.fakePlayerPlugin.api.impl;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.CopyOnWriteArrayList;
import me.bill.fakePlayerPlugin.FakePlayerPlugin;
import me.bill.fakePlayerPlugin.api.FppAddonCommand;
import me.bill.fakePlayerPlugin.api.FppApi;
import me.bill.fakePlayerPlugin.api.FppBot;
import me.bill.fakePlayerPlugin.api.FppBotBlockBreakEvent;
import me.bill.fakePlayerPlugin.api.FppBotBlockPlaceEvent;
import me.bill.fakePlayerPlugin.api.FppBotSaveEvent;
import me.bill.fakePlayerPlugin.api.FppCommandExtension;
import me.bill.fakePlayerPlugin.api.FppBotTickHandler;
import me.bill.fakePlayerPlugin.api.FppSettingsTab;
import me.bill.fakePlayerPlugin.fakeplayer.FakePlayer;
import java.util.function.Supplier;
import me.bill.fakePlayerPlugin.fakeplayer.FakePlayerManager;
import me.bill.fakePlayerPlugin.fakeplayer.PathfindingService;
import me.bill.fakePlayerPlugin.fakeplayer.PathfindingService.NavigationRequest;
import me.bill.fakePlayerPlugin.fakeplayer.PathfindingService.Owner;
import me.bill.fakePlayerPlugin.util.BotAccess;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.entity.Player;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/**
 * Internal implementation of {@link FppApi}.
 * Obtained via {@link FakePlayerPlugin#getFppApi()}.
 */
public final class FppApiImpl implements FppApi {

  private final FakePlayerPlugin plugin;
  private final FakePlayerManager manager;

  /** Registered addon tick handlers — thread-safe iterate, rare write. */
  private final CopyOnWriteArrayList<FppBotTickHandler> tickHandlers = new CopyOnWriteArrayList<>();

  /** Registered addon commands — iterated by CommandManager. */
  private final CopyOnWriteArrayList<FppAddonCommand> addonCommands = new CopyOnWriteArrayList<>();

  /** Registered addon command extensions for built-in /fpp subcommands. */
  private final CopyOnWriteArrayList<FppCommandExtension> commandExtensions = new CopyOnWriteArrayList<>();

  /** Registered addon settings tabs for /fpp settings. */
  private final CopyOnWriteArrayList<FppSettingsTab> settingsTabs = new CopyOnWriteArrayList<>();

  /** Registered addon lifecycle instances, ordered by priority (lower = earlier). */
  private final java.util.concurrent.ConcurrentSkipListSet<me.bill.fakePlayerPlugin.api.FppAddon> addons =
      new java.util.concurrent.ConcurrentSkipListSet<>(
          java.util.Comparator
              .comparingInt(me.bill.fakePlayerPlugin.api.FppAddon::getPriority)
              .thenComparing(me.bill.fakePlayerPlugin.api.FppAddon::getName));

  public FppApiImpl(@NotNull FakePlayerPlugin plugin, @NotNull FakePlayerManager manager) {
    this.plugin  = plugin;
    this.manager = manager;
  }

  // ── Bot queries ───────────────────────────────────────────────────────────

  @Override
  public @NotNull Collection<FppBot> getBots() {
    Collection<FakePlayer> raw = manager.getActivePlayers();
    List<FppBot> result = new ArrayList<>(raw.size());
    for (FakePlayer fp : raw) result.add(new FppBotImpl(fp));
    return result;
  }

  @Override
  public @NotNull Collection<FppBot> getBotsControllableBy(@NotNull Player player) {
    Collection<FakePlayer> raw = manager.getActivePlayers();
    List<FppBot> result = new ArrayList<>(raw.size());
    for (FakePlayer fp : raw) {
      if (BotAccess.canAdminister(player, fp)) result.add(new FppBotImpl(fp));
    }
    return result;
  }

  @Override
  public @NotNull Optional<FppBot> getBot(@NotNull String name) {
    FakePlayer fp = manager.getByName(name);
    return fp == null ? Optional.empty() : Optional.of(new FppBotImpl(fp));
  }

  @Override
  public @NotNull Optional<FppBot> getBot(@NotNull UUID uuid) {
    FakePlayer fp = manager.getByUuid(uuid);
    return fp == null ? Optional.empty() : Optional.of(new FppBotImpl(fp));
  }

  @Override
  public boolean isBot(@NotNull Player player) {
    return manager.getByUuid(player.getUniqueId()) != null;
  }

  @Override
  public @NotNull Optional<FppBot> asBot(@NotNull Player player) {
    return getBot(player.getUniqueId());
  }

  @Override
  public boolean canControlBot(@NotNull Player player, @NotNull FppBot bot) {
    FakePlayer fp = manager.getByUuid(bot.getUuid());
    return fp != null && BotAccess.canAdminister(player, fp);
  }

  @Override
  public int getBotCount() {
    return manager.getActivePlayers().size();
  }

  // ── Spawn / despawn ───────────────────────────────────────────────────────

  @Override
  public @NotNull Optional<FppBot> spawnBot(
      @NotNull Location location,
      @Nullable Player spawner,
      @Nullable String name) {

    int result = manager.spawn(location, 1, spawner, name, /* bypassMax */ false);
    if (result <= 0) return Optional.empty();

    // If a custom name was given we can look it up immediately.
    if (name != null) {
      FakePlayer fp = manager.getByName(name);
      return fp == null ? Optional.empty() : Optional.of(new FppBotImpl(fp));
    }

    // Random name: find the most-recently added bot at this location owned by this spawner.
    String spawnerName = spawner != null ? spawner.getName() : "CONSOLE";
    long now = System.currentTimeMillis();
    FakePlayer newest = null;
    for (FakePlayer fp : manager.getActivePlayers()) {
      if (fp.getSpawnedBy().equals(spawnerName)
          && (now - fp.getSpawnTime().toEpochMilli()) < 2000) {
        if (newest == null
            || fp.getSpawnTime().isAfter(newest.getSpawnTime())) {
          newest = fp;
        }
      }
    }
    return newest == null ? Optional.empty() : Optional.of(new FppBotImpl(newest));
  }

  @Override
  public boolean despawnBot(@NotNull String name) {
    return manager.delete(name);
  }

  @Override
  public boolean despawnBot(@NotNull FppBot bot) {
    return manager.delete(bot.getName());
  }

  // ── Command registration ──────────────────────────────────────────────────

  @Override
  public void registerCommand(@NotNull FppAddonCommand command) {
    String nameLower = command.getName().toLowerCase();
    for (FppAddonCommand existing : addonCommands) {
      if (existing.getName().equalsIgnoreCase(nameLower)) return; // duplicate — ignore
    }
    addonCommands.add(command);
    // Tell CommandManager to include this command (if already initialised).
    var cmdManager = plugin.getCommandManager();
    if (cmdManager != null) cmdManager.registerAddonCommand(command);
  }

  @Override
  public void unregisterCommand(@NotNull FppAddonCommand command) {
    addonCommands.removeIf(existing -> existing.getName().equalsIgnoreCase(command.getName()));
    var cmdManager = plugin.getCommandManager();
    if (cmdManager != null) cmdManager.unregisterAddonCommand(command);
  }

  @Override
  public void registerCommandExtension(@NotNull FppCommandExtension extension) {
    commandExtensions.addIfAbsent(extension);
    var cmdManager = plugin.getCommandManager();
    if (cmdManager != null) cmdManager.registerCommandExtension(extension);
  }

  @Override
  public void unregisterCommandExtension(@NotNull FppCommandExtension extension) {
    commandExtensions.remove(extension);
    var cmdManager = plugin.getCommandManager();
    if (cmdManager != null) cmdManager.unregisterCommandExtension(extension);
  }

  @Override
  public void registerSettingsTab(@NotNull FppSettingsTab tab) {
    settingsTabs.addIfAbsent(tab);
    var gui = plugin.getSettingGui();
    if (gui != null) gui.registerExtensionTab(tab);
  }

  @Override
  public void unregisterSettingsTab(@NotNull FppSettingsTab tab) {
    settingsTabs.remove(tab);
    var gui = plugin.getSettingGui();
    if (gui != null) gui.unregisterExtensionTab(tab);
  }

  /** Returns all registered addon commands (used by CommandManager). */
  public @NotNull List<FppAddonCommand> getAddonCommands() {
    return addonCommands;
  }

  // ── Tick hooks ────────────────────────────────────────────────────────────

  @Override
  public void registerTickHandler(@NotNull FppBotTickHandler handler) {
    tickHandlers.addIfAbsent(handler);
  }

  @Override
  public void unregisterTickHandler(@NotNull FppBotTickHandler handler) {
    tickHandlers.remove(handler);
  }

  /**
   * Called by {@link FakePlayerManager}'s tick loop for each active, non-frozen, bodied bot.
   * Runs on the main thread.
   */
  public void fireTickHandlers(@NotNull FakePlayer fp, @NotNull Player entity) {
    if (tickHandlers.isEmpty()) return;
    FppBotImpl view = new FppBotImpl(fp);
    for (FppBotTickHandler h : tickHandlers) {
      try {
        h.onTick(view, entity);
      } catch (Throwable t) {
        me.bill.fakePlayerPlugin.util.FppLogger.warn(
            "[FppApi] Tick handler threw an exception for bot '"
                + fp.getName()
                + "': "
                + t.getMessage());
      }
    }
  }

  /** Fire a task lifecycle event for a bot. Convenience for commands. */
  public static void fireTaskEvent(@NotNull FakePlayer fp, @NotNull String taskType, @NotNull me.bill.fakePlayerPlugin.api.event.FppBotTaskEvent.Action action) {
    Bukkit.getPluginManager().callEvent(new me.bill.fakePlayerPlugin.api.event.FppBotTaskEvent(new FppBotImpl(fp), taskType, action));
  }

  // ── Navigation ────────────────────────────────────────────────────────────

  @Override
  public void navigateTo(
      @NotNull FppBot bot,
      @NotNull Location destination,
      @Nullable Runnable onArrive) {

    FakePlayer fp = manager.getByUuid(bot.getUuid());
    PathfindingService svc = plugin.getPathfindingService();
    if (fp == null || svc == null) return;

    final Location dest = destination.clone();
    svc.navigate(
        fp,
        new NavigationRequest(
            Owner.SYSTEM,
            () -> dest,
            /* arrivalDistance      */ 1.5,
            /* recalcDistance       */ 3.5,
            /* maxNullRecalcs       */ 5,
            /* onArrive             */ () -> { if (onArrive != null) onArrive.run(); },
            /* onCancel             */ () -> {},
            /* onPathFailure        */ () -> {}));
  }

  @Override
  public void navigateTo(
      @NotNull FppBot bot,
      @NotNull Location destination,
      @Nullable Runnable onArrive,
      @Nullable Runnable onFail,
      @Nullable Runnable onCancel) {
    navigateTo(bot, destination, onArrive, onFail, onCancel, 1.5);
  }

  @Override
  public void navigateTo(
      @NotNull FppBot bot,
      @NotNull Location destination,
      @Nullable Runnable onArrive,
      @Nullable Runnable onFail,
      @Nullable Runnable onCancel,
      double arrivalDistance) {

    FakePlayer fp = manager.getByUuid(bot.getUuid());
    PathfindingService svc = plugin.getPathfindingService();
    if (fp == null || svc == null) return;

    final Location dest = destination.clone();
    svc.navigate(
        fp,
        new NavigationRequest(
            Owner.SYSTEM,
            () -> dest,
            arrivalDistance,
            /* recalcDistance */ 3.5,
            /* maxNullRecalcs */ 5,
            /* onArrive */ () -> { if (onArrive != null) onArrive.run(); },
            /* onCancel */ () -> { if (onCancel != null) onCancel.run(); },
            /* onPathFailure */ () -> { if (onFail != null) onFail.run(); }));
  }

  @Override
  public void cancelNavigation(@NotNull FppBot bot) {
    PathfindingService svc = plugin.getPathfindingService();
    if (svc != null) svc.cancel(bot.getUuid());
  }

  @Override
  public void setNavigationGoal(@NotNull FppBot bot, @NotNull me.bill.fakePlayerPlugin.api.FppNavigationGoal goal) {
    FakePlayer fp = manager.getByUuid(bot.getUuid());
    PathfindingService svc = plugin.getPathfindingService();
    if (fp == null || svc == null) return;
    final me.bill.fakePlayerPlugin.api.FppNavigationGoal g = goal;
    svc.navigate(
        fp,
        new NavigationRequest(
            Owner.SYSTEM,
            () -> g.getNextWaypoint(new FppBotImpl(fp)),
            g.getArrivalDistance(),
            g.getRecalcDistance(),
            /* maxNullRecalcs */ 5,
            /* onArrive */ () -> {
              if (g.isComplete(new FppBotImpl(fp))) {
                cancelNavigation(bot);
              }
            },
            /* onCancel */ () -> {},
            /* onPathFailure */ () -> {}));
  }

  @Override
  public void clearNavigationGoal(@NotNull FppBot bot) {
    cancelNavigation(bot);
  }

  @Override
  public boolean isNavigating(@NotNull FppBot bot) {
    PathfindingService svc = plugin.getPathfindingService();
    return svc != null && svc.isNavigating(bot.getUuid());
  }

  @Override
  public void sayAsBot(@NotNull FppBot bot, @NotNull String message) {
    FakePlayer fp = manager.getByUuid(bot.getUuid());
    if (fp == null) return;
    Player entity = fp.getPlayer();
    if (entity != null && entity.isOnline() && !fp.isBodyless()) {
      entity.chat(message);
      return;
    }
    me.bill.fakePlayerPlugin.fakeplayer.BotChatAI.broadcastRemote(
        fp.getName(), fp.getDisplayName(), message, "", "");
  }

  // ── Plugin info ───────────────────────────────────────────────────────────

  @Override
  public @NotNull String getVersion() {
    return plugin.getDescription().getVersion();
  }

  @Override
  public @Nullable Player getOnlinePlayer(@NotNull String name) {
    return Bukkit.getPlayer(name);
  }

  @Override
  public int getOnlineCount() {
    return Bukkit.getOnlinePlayers().size();
  }

  @Override
  public @NotNull Collection<FppBot> getBotsOwnedBy(@NotNull Player player) {
    Collection<FakePlayer> raw = manager.getActivePlayers();
    List<FppBot> result = new ArrayList<>(raw.size());
    UUID uuid = player.getUniqueId();
    for (FakePlayer fp : raw) {
      if (uuid.equals(fp.getSpawnedByUuid())) result.add(new FppBotImpl(fp));
    }
    return result;
  }

  @Override
  public void registerBotSettingsTab(@NotNull FppSettingsTab tab) {
    settingsTabs.addIfAbsent(tab);
    var gui = plugin.getBotSettingGui();
    if (gui != null) gui.registerExtensionTab(tab);
  }

  @Override
  public void unregisterBotSettingsTab(@NotNull FppSettingsTab tab) {
    settingsTabs.remove(tab);
    var gui = plugin.getBotSettingGui();
    if (gui != null) gui.unregisterExtensionTab(tab);
  }

  @Override
  public boolean runAsBot(@NotNull FppBot bot, @NotNull String command) {
    FakePlayer fp = manager.getByUuid(bot.getUuid());
    if (fp == null) return false;
    Player entity = fp.getPlayer();
    if (entity != null && entity.isOnline() && !fp.isBodyless()) {
      return Bukkit.dispatchCommand(entity, command);
    }
    return false;
  }

  @Override
  public boolean isBotOnline(@NotNull UUID uuid) {
    FakePlayer fp = manager.getByUuid(uuid);
    return fp != null && fp.getPlayer() != null && fp.getPlayer().isOnline();
  }

  @Override
  public @NotNull me.bill.fakePlayerPlugin.FakePlayerPlugin getPlugin() {
    return plugin;
  }

  @Override
  public void registerAddon(@NotNull me.bill.fakePlayerPlugin.api.FppAddon addon) {
    // Validate hard dependencies
    java.util.Set<String> loaded = new java.util.HashSet<>();
    for (me.bill.fakePlayerPlugin.api.FppAddon a : addons) loaded.add(a.getName());
    for (String dep : addon.getDependencies()) {
      if (!loaded.contains(dep)) {
        me.bill.fakePlayerPlugin.util.FppLogger.warn(
            "[FppApi] Addon '" + addon.getName() + "' requires '" + dep + "' which is not loaded.");
        return;
      }
    }
    if (addons.add(addon)) {
      try {
        addon.onEnable(this);
      } catch (Throwable t) {
        me.bill.fakePlayerPlugin.util.FppLogger.warn(
            "[FppApi] Addon '" + addon.getName() + "' onEnable threw: " + t.getMessage());
      }
    }
  }

  @Override
  public void unregisterAddon(@NotNull me.bill.fakePlayerPlugin.api.FppAddon addon) {
    if (addons.remove(addon)) {
      try {
        addon.onDisable();
      } catch (Throwable t) {
        me.bill.fakePlayerPlugin.util.FppLogger.warn(
            "[FppApi] Addon '" + addon.getName() + "' onDisable threw: " + t.getMessage());
      }
    }
  }

  // ── Service registry ──────────────────────────────────────────────────────

  private final java.util.concurrent.ConcurrentHashMap<Class<?>, Object> services = new java.util.concurrent.ConcurrentHashMap<>();

  @Override
  @SuppressWarnings("unchecked")
  public <T> void registerService(@NotNull Class<T> serviceClass, @NotNull T instance) {
    services.put(serviceClass, instance);
  }

  @Override
  @SuppressWarnings("unchecked")
  public <T> @Nullable T getService(@NotNull Class<T> serviceClass) {
    return (T) services.get(serviceClass);
  }

  @Override
  public boolean hasService(@NotNull Class<?> serviceClass) {
    return services.containsKey(serviceClass);
  }

  /** Called by FakePlayerPlugin#onDisable to shut down all registered addons. */
  public void disableAllAddons() {
    for (me.bill.fakePlayerPlugin.api.FppAddon addon : addons) {
      try {
        addon.onDisable();
      } catch (Throwable t) {
        me.bill.fakePlayerPlugin.util.FppLogger.warn(
            "[FppApi] Addon '" + addon.getName() + "' onDisable threw: " + t.getMessage());
      }
    }
    addons.clear();
  }
}
