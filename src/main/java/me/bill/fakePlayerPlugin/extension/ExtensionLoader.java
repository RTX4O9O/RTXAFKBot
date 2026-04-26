package me.bill.fakePlayerPlugin.extension;

import java.io.File;
import java.io.IOException;
import java.lang.reflect.Modifier;
import java.net.URL;
import java.net.URLClassLoader;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.Enumeration;
import java.util.List;
import java.util.jar.JarEntry;
import java.util.jar.JarFile;
import me.bill.fakePlayerPlugin.FakePlayerPlugin;
import me.bill.fakePlayerPlugin.api.FppAddon;
import me.bill.fakePlayerPlugin.api.FppApi;
import me.bill.fakePlayerPlugin.api.FppExtension;
import me.bill.fakePlayerPlugin.util.FppLogger;
import org.bukkit.plugin.Plugin;
import org.jetbrains.annotations.NotNull;

public final class ExtensionLoader {

  private final FakePlayerPlugin plugin;
  private final List<URLClassLoader> classLoaders = new ArrayList<>();
  private final List<ExtensionAddonWrapper> activeWrappers = new ArrayList<>();

  public ExtensionLoader(@NotNull FakePlayerPlugin plugin) {
    this.plugin = plugin;
  }

  public void loadExtensions() {
    File extensionsDir = new File(plugin.getDataFolder(), "extensions");
    if (!extensionsDir.exists()) {
      return;
    }

    File[] jars = extensionsDir.listFiles(f -> f.isFile() && f.getName().endsWith(".jar"));
    if (jars == null || jars.length == 0) {
      return;
    }

    List<ExtensionAddonWrapper> wrappers = new ArrayList<>();

    for (File jar : jars) {
      int found = loadJar(jar, wrappers);
      if (found > 0) {
        FppLogger.info(
            "[Extensions] Scanned " + jar.getName() + " — " + found + " extension(s) found.");
      }
    }

    if (wrappers.isEmpty()) {
      return;
    }

    wrappers.sort(
        Comparator.comparingInt(FppAddon::getPriority)
            .thenComparing(a -> a.getName().toLowerCase()));

    for (ExtensionAddonWrapper wrapper : wrappers) {
      plugin.getFppApi().registerAddon(wrapper);
    }
    activeWrappers.addAll(wrappers);

    FppLogger.info("[Extensions] Loaded " + wrappers.size() + " extension(s) from jar file(s).");
  }

  public void reload() {
    for (ExtensionAddonWrapper wrapper : activeWrappers) {
      try {
        plugin.getFppApi().unregisterAddon(wrapper);
      } catch (Throwable t) {
        FppLogger.warn(
            "[Extensions] Failed to unregister extension '"
                + wrapper.getName()
                + "': "
                + t.getMessage());
      }
    }
    activeWrappers.clear();
    closeClassLoaders();
    loadExtensions();
  }

  private int loadJar(File jar, List<ExtensionAddonWrapper> wrappers) {
    URLClassLoader classLoader = null;
    int found = 0;

    try {
      URL[] urls = {jar.toURI().toURL()};
      classLoader = new URLClassLoader(urls, plugin.getClass().getClassLoader());

      try (JarFile jarFile = new JarFile(jar)) {
        Enumeration<JarEntry> entries = jarFile.entries();
        while (entries.hasMoreElements()) {
          JarEntry entry = entries.nextElement();
          String name = entry.getName();
          if (!name.endsWith(".class") || name.contains("$")) {
            continue;
          }

          String className = name.replace('/', '.').substring(0, name.length() - 6);
          try {
            Class<?> clazz = Class.forName(className, true, classLoader);
            if (FppExtension.class.isAssignableFrom(clazz)
                && !clazz.isInterface()
                && !Modifier.isAbstract(clazz.getModifiers())) {
              FppExtension ext = (FppExtension) clazz.getDeclaredConstructor().newInstance();
              wrappers.add(new ExtensionAddonWrapper(plugin, ext));
              found++;
            }
          } catch (NoClassDefFoundError ignored) {
          } catch (Throwable t) {
            FppLogger.warn(
                "[Extensions] Could not load class "
                    + className
                    + " from "
                    + jar.getName()
                    + ": "
                    + t.getMessage());
          }
        }
      }

      if (found > 0) {
        classLoaders.add(classLoader);
      }
    } catch (IOException e) {
      FppLogger.warn("[Extensions] Failed to load " + jar.getName() + ": " + e.getMessage());
    } finally {
      if (found == 0 && classLoader != null) {
        try {
          classLoader.close();
        } catch (IOException ignored) {
        }
      }
    }

    return found;
  }

  public void closeClassLoaders() {
    for (URLClassLoader cl : classLoaders) {
      try {
        cl.close();
      } catch (IOException ignored) {
      }
    }
    classLoaders.clear();
  }

  private static final class ExtensionAddonWrapper implements FppAddon {

    private final FakePlayerPlugin plugin;
    private final FppExtension extension;

    ExtensionAddonWrapper(@NotNull FakePlayerPlugin plugin, @NotNull FppExtension extension) {
      this.plugin = plugin;
      this.extension = extension;
    }

    @Override
    public @NotNull String getName() {
      return extension.getName();
    }

    @Override
    public @NotNull String getVersion() {
      return extension.getVersion();
    }

    @Override
    public @NotNull Plugin getPlugin() {
      return plugin;
    }

    @Override
    public @NotNull String getDescription() {
      return extension.getDescription();
    }

    @Override
    public @NotNull List<String> getAuthors() {
      return extension.getAuthors();
    }

    @Override
    public @NotNull List<String> getDependencies() {
      return extension.getDependencies();
    }

    @Override
    public @NotNull List<String> getSoftDependencies() {
      return extension.getSoftDependencies();
    }

    @Override
    public int getPriority() {
      return extension.getPriority();
    }

    @Override
    public void onEnable(@NotNull FppApi api) {
      extension.onEnable(api);
    }

    @Override
    public void onDisable() {
      extension.onDisable();
    }
  }
}
