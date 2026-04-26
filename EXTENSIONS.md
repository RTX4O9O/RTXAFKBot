# FakePlayerPlugin — Extension System

This guide covers the **FPP Addon API** for third-party plugins that want to extend FakePlayerPlugin without modifying its core code.

---

## Table of Contents

1. [Quick Start](#quick-start)
2. [Addon Lifecycle](#addon-lifecycle)
3. [Registering Commands](#registering-commands)
4. [Listening to Bot Events](#listening-to-bot-events)
5. [Per-Bot Tick Handlers](#per-bot-tick-handlers)
6. [Settings GUI Tabs](#settings-gui-tabs)
7. [Bot Metadata](#bot-metadata)
8. [Navigation API](#navigation-api)
9. [Service Registry](#service-registry)
10. [Full Addon Example](#full-addon-example)

---

## Quick Start

Every addon is a standard Bukkit plugin that depends on `FakePlayerPlugin`. The minimum requirement is implementing `FppAddon` and registering yourself during `onEnable`.

### `plugin.yml`

```yaml
name: MyAddon
version: 1.0.0
main: com.example.myaddon.MyAddonPlugin
api-version: '1.21'
depend: [FakePlayerPlugin]
```

### Entry Point

```java
public class MyAddonPlugin extends JavaPlugin implements FppAddon {

    @Override
    public void onEnable() {
        // Register this addon with FPP
        FppApi api = ((FakePlayerPlugin) getServer().getPluginManager().getPlugin("FakePlayerPlugin")).getFppApi();
        api.registerAddon(this);
    }

    @Override
    public void onDisable() {
        // FPP will call your onDisable automatically if you unregister,
        // but you can also unregister manually here.
    }

    // ── FppAddon contract ──
    @Override public @NotNull String getName() { return "MyAddon"; }
    @Override public @NotNull String getVersion() { return "1.0.0"; }
    @Override public @NotNull Plugin getPlugin() { return this; }
    @Override public void onEnable(@NotNull FppApi api) { getLogger().info("Addon enabled!"); }
    @Override public void onDisable() { getLogger().info("Addon disabled!"); }
}
```

### Addon Metadata & Dependencies

```java
@Override public @NotNull String getDescription() { return "Does cool things"; }
@Override public @NotNull List<String> getAuthors() { return List.of("YourName"); }
@Override public @NotNull List<String> getDependencies() { return List.of("AnotherFppAddon"); }
@Override public @NotNull List<String> getSoftDependencies() { return List.of("Vault"); }
@Override public int getPriority() { return 50; } // lower = loaded earlier
```

> **Hard dependencies** prevent your addon from loading if the named addon is missing. **Soft dependencies** are informational only. **Priority** controls load order among addons (default `100`).

---

## Addon Lifecycle

| Phase | When it runs |
|-------|-------------|
| `onEnable(FppApi)` | Immediately after `api.registerAddon(this)` returns. Use this to set up commands, tick handlers, and event listeners. |
| `onDisable()` | Called when the addon is unregistered or when the server shuts down. Clean up here. |

If your `onEnable` throws an exception, the addon is still registered but a warning is printed to the console. Fix initialization bugs eagerly so your addon does not partially load.

---

## Registering Commands

### Standalone Addon Commands

These appear as `/fpp <command>` subcommands and show up in help.

```java
api.registerCommand(new FppAddonCommand() {
    @Override public @NotNull String getName() { return "farm"; }
    @Override public @NotNull String getDescription() { return "Make a bot farm crops"; }
    @Override public @NotNull String getUsage() { return "<bot>"; }
    @Override public @NotNull String getPermission() { return "fpp.addon.farm"; }
    @Override public boolean execute(@NotNull CommandSender sender, @NotNull String[] args) {
        // your logic
        return true;
    }
    @Override public @NotNull List<String> tabComplete(@NotNull CommandSender sender, @NotNull String[] args) {
        return List.of();
    }
});
```

### Command Extensions

If you want to add flags or extra arguments to an **existing** `/fpp` subcommand (e.g. `/fpp spawn --myflag`), use `FppCommandExtension`.

```java
api.registerCommandExtension(new FppCommandExtension() {
    @Override public @NotNull String getCommandName() { return "spawn"; }
    @Override public boolean execute(@NotNull CommandSender sender, @NotNull String[] args) {
        // args contains everything after the subcommand
        // return true if you handled the call, false to let FPP process normally
        return false;
    }
});
```

---

## Listening to Bot Events

All bot events extend `FppBotEvent` and can be listened to with a standard Bukkit `@EventHandler`. They are fired on the **main thread**.

### Lifecycle Events

| Event | When |
|-------|------|
| `FppBotSpawnEvent` | Bot is spawned (check `isRestored()` for restart-persistent bots) |
| `FppBotDespawnEvent` | Bot is removed from the server |
| `FppBotDeathEvent` | Bot dies (`getKiller()` may be null) |
| `FppBotSaveEvent` | Bot state is being saved to disk/database |
| `FppBotRenameEvent` | Bot display name changes |
| `FppBotTeleportEvent` | Bot teleports (cancellable) |
| `FppBotWorldChangeEvent` | Bot changes dimension/world |
| `FppBotFreezeEvent` | Bot frozen/unfrozen (cancellable) |
| `FppBotGameModeChangeEvent` | GameMode changes (cancellable, `setNewMode()` supported) |

### Interaction & Combat Events

| Event | When |
|-------|------|
| `FppBotDamageEvent` | Bot takes damage (cancellable, `setDamage()` supported) |
| `FppBotAttackEvent` | Bot attacks an entity (cancellable, `setDamage()` supported) |
| `FppBotTargetEvent` | Another entity targets the bot (cancellable) |
| `FppBotInteractEvent` | Bot interacts with an entity (cancellable) |

### Navigation & Task Events

| Event | When |
|-------|------|
| `FppBotNavigationEvent` | Path start, recalc, arrive, fail, or cancel |
| `FppBotFollowEvent` | Follow task start/stop |
| `FppBotTaskEvent` | Generic task start/stop (used by mine, place, sleep, etc.) |

### Inventory & Chunk Events

| Event | When |
|-------|------|
| `FppBotInventoryEvent` | Item pickup/drop/equip/unequip (cancellable) |
| `FppBotChunkLoadEvent` | Bot loads a new chunk via chunk loading |

### Example Listener

```java
public class MyAddonListener implements Listener {
    @EventHandler
    public void onBotSpawn(FppBotSpawnEvent event) {
        FppBot bot = event.getBot();
        bot.sendMessage("Hello world! I am " + bot.getName());
    }

    @EventHandler
    public void onBotDamage(FppBotDamageEvent event) {
        if (event.getCause() == DamageCause.FALL) {
            event.setDamage(0); // bots take no fall damage
        }
    }
}
```

Register it normally:

```java
Bukkit.getPluginManager().registerEvents(new MyAddonListener(), myAddonPlugin);
```

---

## Per-Bot Tick Handlers

If you need to run code **every tick** for every active bot body, register a tick handler instead of starting your own repeating task. This integrates cleanly with FPP's tick loop and includes automatic exception handling.

```java
api.registerTickHandler((bot, entity) -> {
    // bot  = FppBot view
    // entity = the actual Bukkit Player entity (never null here)
    if (bot.isInWater() && !bot.isSwimAiEnabled()) {
        bot.setVelocity(new Vector(0, 0.3, 0));
    }
});
```

> Tick handlers are only called for **online, bodied, non-frozen** bots. If a bot is bodyless, despawned, or frozen, your handler will not run.

---

## Settings GUI Tabs

You can inject your own items into the **global** `/fpp settings` GUI or the **per-bot** settings GUI.

```java
api.registerSettingsTab(new FppSettingsTab() {
    @Override public @NotNull String getId() { return "myaddon"; }
    @Override public @NotNull String getLabel() { return "My Addon"; }
    @Override public @NotNull Material getActiveMaterial() { return Material.DIAMOND_SWORD; }
    @Override public @NotNull Material getInactiveMaterial() { return Material.STONE_SWORD; }
    @Override public @NotNull Material getSeparatorGlass() { return Material.BLACK_STAINED_GLASS_PANE; }
    @Override public boolean isVisible(@NotNull Player viewer) { return viewer.hasPermission("myaddon.admin"); }

    @Override public @NotNull List<FppSettingsItem> getItems(@NotNull Player viewer) {
        return List.of(new FppSettingsItem() {
            @Override public @NotNull String getId() { return "toggle"; }
            @Override public @NotNull String getLabel() { return "Toggle Feature"; }
            @Override public @NotNull String getDescription() { return "Click to toggle"; }
            @Override public @NotNull Material getIcon() { return Material.LEVER; }
            @Override public @Nullable String getValue() { return "OFF"; }
            @Override public void onClick(@NotNull Player viewer) {
                viewer.sendMessage("Toggled!");
            }
        });
    }
});
```

Use `registerBotSettingsTab` for the per-bot GUI, and `registerSettingsTab` for the global GUI.

---

## Bot Metadata

The easiest way for an addon to store per-bot state is the built-in metadata map. It is **transient** (not persisted across restarts) and is cleared automatically when the bot despawns.

```java
FppBot bot = api.getBot("Steve").orElseThrow();

bot.setMetadata("myaddon:cropType", "WHEAT");
bot.setMetadata("myaddon:farmStartTime", System.currentTimeMillis());

String crop = (String) bot.getMetadata("myaddon:cropType");
long start = (Long) bot.getMetadata("myaddon:farmStartTime");

if (bot.hasMetadata("myaddon:cropType")) {
    bot.removeMetadata("myaddon:cropType");
}
```

> **Best practice:** prefix your keys with your addon name to avoid collisions.

---

## Navigation API

### Simple Point-to-Point

```java
api.navigateTo(bot, destination, () -> {
    bot.sendMessage("Arrived!");
});
```

### With Failure & Cancel Callbacks

```java
api.navigateTo(bot, destination,
    /* onArrive */ () -> bot.sendMessage("Arrived!"),
    /* onFail   */ () -> bot.sendMessage("Can't find a path."),
    /* onCancel */ () -> bot.sendMessage("Navigation cancelled."),
    /* arrivalDistance */ 2.0
);
```

### Custom Navigation Goal

For dynamic waypoints (e.g. patrolling, chasing a moving target), implement `FppNavigationGoal`:

```java
public class PatrolGoal implements FppNavigationGoal {
    private final List<Location> points;
    private int index = 0;

    public PatrolGoal(List<Location> points) { this.points = points; }

    @Override public @Nullable Location getNextWaypoint(@NotNull FppBot bot) {
        return points.get(index);
    }

    @Override public boolean isComplete(@NotNull FppBot bot) {
        return false; // patrol never ends
    }

    @Override public double getArrivalDistance() { return 1.0; }
}

// Usage
api.setNavigationGoal(bot, new PatrolGoal(waypoints));
// ... later
api.clearNavigationGoal(bot);
```

---

## Service Registry

If your addon exposes its own API to **other** addons, use the lightweight service registry instead of static singletons.

### Exposing a Service

```java
public interface FarmAddonApi {
    void startFarming(FppBot bot);
    void stopFarming(FppBot bot);
}

// In your addon
api.registerService(FarmAddonApi.class, new FarmAddonApiImpl());
```

### Consuming a Service

```java
FarmAddonApi farm = api.getService(FarmAddonApi.class);
if (farm != null) {
    farm.startFarming(bot);
}
```

> Services are stored per-FPP-instance. They do not survive reloads or restarts.

---

## Full Addon Example

```java
package com.example.myaddon;

import me.bill.fakePlayerPlugin.api.*;
import me.bill.fakePlayerPlugin.api.event.*;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.plugin.java.JavaPlugin;
import org.jetbrains.annotations.NotNull;

public class MyAddon extends JavaPlugin implements FppAddon, Listener {

    private FppApi api;

    @Override
    public void onEnable() {
        FakePlayerPlugin fpp = (FakePlayerPlugin) getServer().getPluginManager().getPlugin("FakePlayerPlugin");
        if (fpp == null) { getLogger().severe("FPP not found!"); return; }
        this.api = fpp.getFppApi();
        api.registerAddon(this);
        Bukkit.getPluginManager().registerEvents(this, this);
    }

    @Override public @NotNull String getName() { return "MyAddon"; }
    @Override public @NotNull String getVersion() { return "1.0.0"; }
    @Override public @NotNull Plugin getPlugin() { return this; }

    @Override
    public void onEnable(@NotNull FppApi api) {
        api.registerTickHandler((bot, entity) -> {
            if (bot.isInWater() && Boolean.TRUE.equals(bot.getMetadata("myaddon:swim"))) {
                entity.setSprinting(true);
            }
        });
    }

    @Override public void onDisable() {}

    @EventHandler
    public void onSpawn(FppBotSpawnEvent event) {
        FppBot bot = event.getBot();
        bot.setMetadata("myaddon:swim", true);
        bot.sendMessage("Swim AI enabled by MyAddon!");
    }

    @EventHandler
    public void onDamage(FppBotDamageEvent event) {
        if (event.getCause() == org.bukkit.event.entity.EntityDamageEvent.DamageCause.DROWNING) {
            event.setCancelled(true); // bots with our addon don't drown
        }
    }
}
```

---

## Best Practices

1. **Prefix metadata keys** with your addon name (`myaddon:key`).
2. **Handle exceptions** inside tick handlers and command executors so one addon cannot crash FPP's tick loop.
3. **Unregister** commands, tick handlers, and tabs in `onDisable` if you support reloads.
4. **Use `FppApi.getService()`** instead of `Bukkit.getServicesManager()` for addon-to-addon communication.
5. **Check `bot.isOnline()`** before calling entity-dependent methods if you are in an async context.
6. **Do not assume `bot.getEntity()` is non-null** — it can be null during spawn, despawn, or if the bot is bodyless.

---

## Useful `FppBot` API Reference

| Method | Description |
|--------|-------------|
| `getName()` / `getDisplayName()` | Bot name and display name |
| `getLocation()` / `teleport(Location)` | Position control |
| `getEntity()` | The underlying Bukkit `Player` entity (may be null) |
| `getHealth()` / `setHealth()` | Health management |
| `getGameMode()` / `setGameMode()` | GameMode (fires `FppBotGameModeChangeEvent`) |
| `getInventory()` / `setItemInMainHand()` | Inventory access |
| `getLevel()` / `setLevel()` | Experience |
| `isSleeping()` / `setSleepOrigin()` | Sleep system |
| `setFrozen()` / `isFrozen()` | Freeze toggle (cancellable event) |
| `swingMainHand()` / `swingOffHand()` | Animation triggers |
| `setSneaking()` / `setSprinting()` | Pose control |
| `isOnGround()` / `isClimbing()` / `hasVehicle()` | State queries |
| `getReachDistance()` | Effective block interaction range |
| `performRespawn()` | Force respawn if dead |
| `hasPermission(String)` | LuckPerms / Vault aware |
| `sendMessage(String)` | Chat message to the bot (visible in logs) |

---

## Support

If you run into issues building an addon, open an issue on the FakePlayerPlugin GitHub or join the Discord linked in the main README.
