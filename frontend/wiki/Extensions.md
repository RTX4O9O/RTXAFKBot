# 📦 Extensions

> **Addon API for third-party developers — v1.6.6.7**

FPP provides a lightweight extension API that lets other plugins hook into the bot system without modifying FPP itself. Drop compiled `.jar` files into `plugins/FakePlayerPlugin/extensions/` and they are loaded automatically on startup.

---

## 🏗️ Extension Loader

The `ExtensionLoader` scans `plugins/FakePlayerPlugin/extensions/` for `.jar` files on every startup and reload (`/fpp reload`). Valid extensions implement the `FppExtension` interface and declare a service provider.

### Loading order

1. JAR files are discovered alphabetically.
2. Each JAR is inspected for a `META-INF/services/` provider declaration.
3. Valid extensions are instantiated and passed a reference to the running `FakePlayerPlugin` instance.
4. The extension can then register listeners, commands, or interact with the bot registry.

### Directory layout

```text
plugins/FakePlayerPlugin/
└── extensions/
    ├── my-extension.jar
    └── another-addon.jar
```

> Extensions are **not** Bukkit plugins. They are FPP-specific addons that live inside the FPP extensions folder.

---

## 🧩 `FppExtension` Interface

The entry point every extension must implement:

```java
public interface FppExtension {

    /** Called once when the extension is loaded. */
    void onLoad(FakePlayerPlugin plugin);

    /** Called during full plugin reload. */
    default void onReload(FakePlayerPlugin plugin) {}

    /** Called when the extension is disabled (server stop or reload). */
    default void onDisable(FakePlayerPlugin plugin) {}
}
```

### Service Provider Declaration

Create `META-INF/services/me.billhub.fakeplayer.api.FppExtension` inside your JAR:

```text
com.example.MyExtension
```

Each line is a fully-qualified class name implementing `FppExtension`.

---

## 📚 What Extensions Can Do

Because the extension receives the live `FakePlayerPlugin` instance, it can:

- Access the active bot registry (`BotManager`)
- Register custom Bukkit event listeners
- Start / stop bot tasks programmatically
- Hook into the bot lifecycle (spawn, despawn, death)
- Read and react to configuration values

### Common use cases

| Use case | Approach |
|----------|----------|
| Custom bot AI | Listen for spawn events and attach custom runnable logic |
| External data sync | On spawn, push bot state to an external dashboard or database |
| Custom commands | Register subcommands through Bukkit's command map or intercept existing ones |
| Economy integration | Reward players when their bots reach milestones |
| Discord bridging | Mirror bot chat messages to a Discord channel |

---

## 🛠️ Building an Extension

### Gradle setup

```groovy
repositories {
    mavenCentral()
    maven { url = 'https://repo.papermc.io/repository/maven-public/' }
}

dependencies {
    compileOnly 'io.papermc.paper:paper-api:1.21.4-R0.1-SNAPSHOT'
    compileOnly files('path/to/FakePlayerPlugin.jar')
}
```

### Minimal example

```java
package com.example;

import me.billhub.fakeplayer.FakePlayerPlugin;
import me.billhub.fakeplayer.api.FppExtension;

public class MyExtension implements FppExtension {

    @Override
    public void onLoad(FakePlayerPlugin plugin) {
        plugin.getLogger().info("MyExtension loaded!");
    }
}
```

---

## 🔄 Reloading

Extensions are fully reloaded when `/fpp reload` runs:

1. All existing extensions receive `onDisable()`.
2. JARs are re-scanned.
3. Valid extensions are re-instantiated and receive `onLoad()`.

---

## 📝 Notes

- Extensions must be compiled for **Java 21+** to match FPP's runtime.
- Paper API classes used by extensions should target **1.21.x**.
- FPP does **not** currently expose a Maven repository; compile against the FPP JAR directly.
- Errors during extension load are logged but do **not** prevent FPP from starting.

---

## 🔗 Related Pages

- [Home](Home)
- [Commands](Commands)
- [Configuration](Configuration)
