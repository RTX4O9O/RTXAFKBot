package me.bill.fakePlayerPlugin.fakeplayer;

import java.lang.reflect.Constructor;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.net.InetAddress;
import java.util.*;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import me.bill.fakePlayerPlugin.FakePlayerPlugin;
import me.bill.fakePlayerPlugin.fakeplayer.network.FakeConnection;
import me.bill.fakePlayerPlugin.fakeplayer.network.FakeServerGamePacketListenerImpl;
import me.bill.fakePlayerPlugin.util.FppLogger;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.plugin.Plugin;
import org.bukkit.World;
import org.bukkit.entity.Player;
import org.bukkit.util.Vector;

public final class NmsPlayerSpawner {

  private static volatile boolean initialized = false;
  private static volatile boolean failed = false;

  private static Method craftPlayerGetHandleMethod;
  private static Method craftServerGetServerMethod;
  private static Method craftWorldGetHandleMethod;

  private static Class<?> minecraftServerClass;
  private static Class<?> serverLevelClass;
  private static Class<?> serverPlayerClass;
  private static Class<?> clientInformationClass;
  private static Class<?> connectionClass;
  private static Class<?> commonListenerCookieClass;
  private static Class<?> serverGamePacketListenerClass;
  private static Class<?> packetFlowClass;

  private static Constructor<?> gameProfileConstructor;
  private static Class<?> gameProfileClass;
  private static Method setPosMethod;
  private static Method doTickMethod;
  private static Method getPlayerListMethod;

  private static Field xoField;
  private static Field yoField;
  private static Field zoField;

  private static Field jumpingField;

  private static Field listedField;

  private static Field yHeadRotField;

  private static Field zzaField;

  private static Field xxaField;

  private static Field connectionFieldInPlayer;

  private static Method attackMethod;

  private static Method playerListRemoveMethod;

  private static java.lang.reflect.Field playerDataStorageField;

  private static Method playerDataSaveMethod;

  private static Method getPlayerDirMethod;

  private static Object clientInfoDefault;

  private static final Set<UUID> firstTickSet = Collections.synchronizedSet(new HashSet<>());

  private static Object skinPartsDataAccessor = null;

  private static Method synchedEntityDataSetMethod = null;

  private static Field entityDataFieldForSkinParts = null;

  private static volatile boolean foliaSchedulerResolved = false;
  private static volatile boolean foliaSchedulerAvailable = false;
  private static Method bukkitGetRegionSchedulerMethod = null;
  private static Method regionSchedulerExecuteMethod = null;

  private NmsPlayerSpawner() {}

  public static synchronized void init() {
    if (initialized || failed) return;
    try {

      String packageName = Bukkit.getServer().getClass().getPackage().getName();
      String ver = packageName.substring(packageName.lastIndexOf('.') + 1);
      String cbPkg =
          ver.equals("craftbukkit") ? "org.bukkit.craftbukkit" : "org.bukkit.craftbukkit." + ver;
      FppLogger.debug("NmsPlayerSpawner: CraftBukkit package = " + cbPkg);

      Class<?> craftServerClass = Class.forName(cbPkg + ".CraftServer");
      Class<?> craftWorldClass = Class.forName(cbPkg + ".CraftWorld");
      Class<?> craftPlayerClass = Class.forName(cbPkg + ".entity.CraftPlayer");
      ClassLoader nmsLoader = craftServerClass.getClassLoader();

      craftServerGetServerMethod = craftServerClass.getMethod("getServer");
      craftWorldGetHandleMethod = craftWorldClass.getMethod("getHandle");
      craftPlayerGetHandleMethod = craftPlayerClass.getMethod("getHandle");

      minecraftServerClass = nmsLoader.loadClass("net.minecraft.server.MinecraftServer");
      try {
        serverLevelClass = nmsLoader.loadClass("net.minecraft.server.level.ServerLevel");
        serverPlayerClass = nmsLoader.loadClass("net.minecraft.server.level.ServerPlayer");
        FppLogger.debug("NmsPlayerSpawner: using Mojang-mapped NMS names");
      } catch (ClassNotFoundException e) {
        serverLevelClass = nmsLoader.loadClass("net.minecraft.server.level.WorldServer");
        serverPlayerClass = nmsLoader.loadClass("net.minecraft.server.level.EntityPlayer");
        FppLogger.debug("NmsPlayerSpawner: using Spigot-mapped NMS names");
      }

      try {
        connectionClass = nmsLoader.loadClass("net.minecraft.network.Connection");
      } catch (ClassNotFoundException e) {
        connectionClass = nmsLoader.loadClass("net.minecraft.network.NetworkManager");
      }
      try {
        commonListenerCookieClass =
            nmsLoader.loadClass("net.minecraft.server.network.CommonListenerCookie");
      } catch (ClassNotFoundException ignored) {
      }
      try {
        serverGamePacketListenerClass =
            nmsLoader.loadClass("net.minecraft.server.network.ServerGamePacketListenerImpl");
      } catch (ClassNotFoundException e) {
        try {
          serverGamePacketListenerClass =
              nmsLoader.loadClass("net.minecraft.server.network.PlayerConnection");
        } catch (ClassNotFoundException ignored) {
        }
      }
      try {
        packetFlowClass = nmsLoader.loadClass("net.minecraft.network.protocol.PacketFlow");
      } catch (ClassNotFoundException ignored) {
      }

      try {
        clientInformationClass =
            nmsLoader.loadClass("net.minecraft.server.level.ClientInformation");
        try {
          clientInfoDefault = clientInformationClass.getMethod("createDefault").invoke(null);
          FppLogger.debug("NmsPlayerSpawner: ClientInformation.createDefault() cached");
        } catch (Exception ignored) {
        }
      } catch (ClassNotFoundException ignored) {
      }

      gameProfileClass = Class.forName("com.mojang.authlib.GameProfile");
      gameProfileConstructor = gameProfileClass.getConstructor(UUID.class, String.class);

      getPlayerListMethod = minecraftServerClass.getMethod("getPlayerList");

      for (Method m : serverPlayerClass.getMethods()) {
        if ("setPos".equals(m.getName()) && m.getParameterCount() == 3) {
          Class<?>[] p = m.getParameterTypes();
          if (p[0] == double.class && p[1] == double.class && p[2] == double.class) {
            setPosMethod = m;
            break;
          }
        }
      }
      if (setPosMethod == null)
        setPosMethod =
            findMethodBySignature(serverPlayerClass, 3, double.class, double.class, double.class);

      doTickMethod = findMethod(serverPlayerClass, "doTick", 0);
      if (doTickMethod == null) doTickMethod = findMethod(serverPlayerClass, "tick", 0);
      if (doTickMethod != null) {
        FppLogger.debug("NmsPlayerSpawner: doTick cached as " + doTickMethod.getName() + "()");
      } else {
        FppLogger.warn("NmsPlayerSpawner: doTick() not found - bots will have no physics");
      }

      Class<?> entityClass;
      try {
        entityClass = nmsLoader.loadClass("net.minecraft.world.entity.Entity");
      } catch (ClassNotFoundException e) {
        entityClass = serverPlayerClass;
      }
      xoField = findFieldByName(entityClass, "xo");
      yoField = findFieldByName(entityClass, "yo");
      zoField = findFieldByName(entityClass, "zo");
      FppLogger.debug(
          "NmsPlayerSpawner: xo/yo/zo fields " + (xoField != null ? "cached" : "not found"));

      try {
        Class<?> livingEntityClass = nmsLoader.loadClass("net.minecraft.world.entity.LivingEntity");
        jumpingField = findFieldByName(livingEntityClass, "jumping");
      } catch (ClassNotFoundException ignored) {

        jumpingField = findFieldByName(serverPlayerClass, "jumping");
      }
      FppLogger.debug(
          "NmsPlayerSpawner: jumping field "
              + (jumpingField != null ? "cached" : "not found - swim AI inactive"));

      listedField = findFieldByName(serverPlayerClass, "listed");
      FppLogger.debug(
          "NmsPlayerSpawner: listed field "
              + (listedField != null
                  ? "cached"
                  : "not found - tab unlist will use packet fallback"));

      yHeadRotField = findFieldByName(serverPlayerClass, "yHeadRot");
      FppLogger.debug(
          "NmsPlayerSpawner: yHeadRot field "
              + (yHeadRotField != null
                  ? "cached"
                  : "not found - head AI will rely on setRotation only"));

      zzaField = findFieldByName(serverPlayerClass, "zza");

      xxaField = findFieldByName(serverPlayerClass, "xxa");
      FppLogger.debug(
          "NmsPlayerSpawner: movement input fields "
              + (zzaField != null && xxaField != null
                  ? "cached"
                  : "not found - move command inactive"));

      if (serverGamePacketListenerClass != null) {
        connectionFieldInPlayer = findFieldByName(serverPlayerClass, "connection");
        if (connectionFieldInPlayer == null)
          connectionFieldInPlayer = findFieldByName(serverPlayerClass, "playerConnection");
        if (connectionFieldInPlayer == null)
          connectionFieldInPlayer = findFieldByName(serverPlayerClass, "playerGameConnection");
        if (connectionFieldInPlayer == null) {
          for (Field f : getAllDeclaredFields(serverPlayerClass)) {
            if (serverGamePacketListenerClass.isAssignableFrom(f.getType())
                || f.getType().isAssignableFrom(serverGamePacketListenerClass)) {
              f.setAccessible(true);
              connectionFieldInPlayer = f;
              break;
            }
          }
        }
        if (connectionFieldInPlayer != null) {
          FppLogger.debug(
              "NmsPlayerSpawner: connection field = " + connectionFieldInPlayer.getName());
        } else {
          FppLogger.warn(
              "NmsPlayerSpawner: ServerPlayer.connection field not found"
                  + " - fake listener injection will be skipped");
        }
      }

      try {
        Class<?> entityClassForAttack = nmsLoader.loadClass("net.minecraft.world.entity.Entity");
        attackMethod = findMethod(serverPlayerClass, "attack", 1, entityClassForAttack);
        if (attackMethod != null) {
          FppLogger.debug("NmsPlayerSpawner: attack(Entity) method cached");
        } else {
          FppLogger.warn(
              "NmsPlayerSpawner: attack(Entity) method not found - bots will use"
                  + " fallback damage");
        }
      } catch (Exception e) {
        FppLogger.warn("NmsPlayerSpawner: Failed to cache attack method: " + e.getMessage());
      }

      try {
        Class<?> playerListClass = getPlayerListMethod.getReturnType();
        playerListRemoveMethod = findMethod(playerListClass, "remove", 1);

        for (java.lang.reflect.Field f : playerListClass.getDeclaredFields()) {
          String typeName = f.getType().getSimpleName();
          if (typeName.contains("WorldNBTStorage") || typeName.contains("PlayerDataStorage")) {
            f.setAccessible(true);
            playerDataStorageField = f;
            break;
          }
        }
        if (playerDataStorageField != null) {
          Class<?> storageClass = playerDataStorageField.getType();

          try {
            getPlayerDirMethod = storageClass.getMethod("getPlayerDir");
          } catch (Exception ignored) {
          }

          for (java.lang.reflect.Method m : storageClass.getDeclaredMethods()) {
            if ("a".equals(m.getName())
                && m.getParameterCount() == 1
                && m.getReturnType() == void.class) {
              m.setAccessible(true);
              playerDataSaveMethod = m;
              break;
            }
          }
        }
        FppLogger.debug(
            "NmsPlayerSpawner: PlayerList lifecycle - remove="
                + (playerListRemoveMethod != null ? "ok" : "missing")
                + " storage="
                + (playerDataStorageField != null ? "ok" : "missing")
                + " save="
                + (playerDataSaveMethod != null ? "ok" : "missing")
                + " getPlayerDir="
                + (getPlayerDirMethod != null ? "ok" : "missing"));
      } catch (Exception e) {
        FppLogger.debug("NmsPlayerSpawner: PlayerList lifecycle init failed: " + e.getMessage());
      }

      initialized = true;
      FppLogger.info(
          "NmsPlayerSpawner initialised (doTick="
              + (doTickMethod != null)
              + ", connectionField="
              + (connectionFieldInPlayer != null)
              + ", attack="
              + (attackMethod != null)
              + ", playerDataDir="
              + (getPlayerDirMethod != null)
              + ")");

      try {
        Class<?> playerNmsClass;
        try {
          playerNmsClass = nmsLoader.loadClass("net.minecraft.world.entity.player.Player");
        } catch (ClassNotFoundException ignored) {
          playerNmsClass = serverPlayerClass;
        }

        Field spField = findFieldByName(playerNmsClass, "DATA_PLAYER_MODE_CUSTOMISATION");
        if (spField != null && java.lang.reflect.Modifier.isStatic(spField.getModifiers())) {
          spField.setAccessible(true);
          skinPartsDataAccessor = spField.get(null);
        }

        Class<?> syncDataClass =
            nmsLoader.loadClass("net.minecraft.network.syncher.SynchedEntityData");
        for (Method m : syncDataClass.getDeclaredMethods()) {
          if ("set".equals(m.getName()) && m.getParameterCount() == 2) {
            m.setAccessible(true);
            synchedEntityDataSetMethod = m;
            break;
          }
        }

        entityDataFieldForSkinParts = findFieldByName(serverPlayerClass, "entityData");

        FppLogger.debug(
            "NmsPlayerSpawner: skin-parts init - accessor="
                + (skinPartsDataAccessor != null)
                + " entityData="
                + (entityDataFieldForSkinParts != null)
                + " setMethod="
                + (synchedEntityDataSetMethod != null));
      } catch (Exception e) {
        FppLogger.debug("NmsPlayerSpawner: skin-parts init failed (non-fatal): " + e.getMessage());
      }

    } catch (Exception e) {
      failed = true;
      FppLogger.error("NmsPlayerSpawner.init() failed: " + e.getMessage());
    }
  }

  public static boolean isAvailable() {
    if (!initialized && !failed) init();
    return initialized;
  }

  public static Player spawnFakePlayer(
      UUID uuid, String name, World world, double x, double y, double z) {
    return spawnFakePlayer(uuid, name, null, world, x, y, z);
  }

  public static Player spawnFakePlayer(
      UUID uuid, String name, SkinProfile skin, World world, double x, double y, double z) {
    if (!isAvailable()) {
      FppLogger.warn("NmsPlayerSpawner not available - cannot spawn " + name);
      return null;
    }
    try {

      Object gameProfile = gameProfileConstructor.newInstance(uuid, name);
      if (skin != null && skin.isValid()) {
        try {
          gameProfile = SkinProfileInjector.createGameProfile(gameProfileClass, uuid, name, skin);
          FppLogger.debug("NmsPlayerSpawner: injected skin for '" + name + "'");
        } catch (Exception e) {
          FppLogger.warn("NmsPlayerSpawner: skin injection failed: " + e.getMessage());
        }
      }

      Object minecraftServer = craftServerGetServerMethod.invoke(Bukkit.getServer());
      Object serverLevel = craftWorldGetHandleMethod.invoke(world);
      Object clientInfo = getClientInformation();

      Object serverPlayer =
          createServerPlayer(minecraftServer, serverLevel, gameProfile, clientInfo);
      if (serverPlayer == null) {
        FppLogger.warn("NmsPlayerSpawner: failed to create ServerPlayer for " + name);
        return null;
      }

      if (setPosMethod != null) setPosMethod.invoke(serverPlayer, x, y, z);
      initPreviousPosition(serverPlayer, x, y, z);

      Object conn = createFakeConnection();
      if (conn == null) {
        FppLogger.warn("NmsPlayerSpawner: failed to create fake connection for " + name);
        return null;
      }

      FppLogger.debug("NmsPlayerSpawner: spawning '" + name + "' uuid=" + uuid);
      ensurePlayerDataExists(minecraftServer, serverPlayer, name, uuid);

      boolean placed = placePlayer(minecraftServer, conn, serverPlayer, gameProfile, clientInfo);
      if (!placed) {
        placed =
            placePlayerOnRegionThread(
                world, x, z, minecraftServer, conn, serverPlayer, gameProfile, clientInfo);
      }
      if (!placed) {
        cleanupFailedSpawn(minecraftServer, serverPlayer, name);
        FppLogger.warn("NmsPlayerSpawner: placeNewPlayer failed for " + name);
        return null;
      }

      if (setPosMethod != null) setPosMethod.invoke(serverPlayer, x, y, z);
      initPreviousPosition(serverPlayer, x, y, z);

      injectFakeListener(minecraftServer, conn, serverPlayer, gameProfile, clientInfo);

      Method getBukkitEntity = serverPlayerClass.getMethod("getBukkitEntity");
      Object entity = getBukkitEntity.invoke(serverPlayer);
      if (entity instanceof Player result) {
        result.setGameMode(org.bukkit.GameMode.SURVIVAL);
        setListed(result, true);

        forceAllSkinParts(result);
        firstTickSet.add(uuid);
        FppLogger.debug("NmsPlayerSpawner: spawned " + name + " (" + uuid + ")");
        return result;
      }

      FppLogger.warn("NmsPlayerSpawner: getBukkitEntity did not return a Player for " + name);
      return null;

    } catch (Exception e) {
      FppLogger.error(
          "NmsPlayerSpawner.spawnFakePlayer failed for " + name + ": " + e.getMessage());
      FppLogger.debug(Arrays.toString(e.getStackTrace()));
      return null;
    }
  }

  public static void tickPhysics(Player bot) {
    if (!initialized || doTickMethod == null || craftPlayerGetHandleMethod == null) return;
    if (!bot.isOnline() || !bot.isValid() || bot.isDead()) return;

    if (dispatchTickPhysicsToRegionThread(bot)) {
      return;
    }

    tickPhysicsInternal(bot);
  }

  private static void tickPhysicsInternal(Player bot) {
    if (!initialized || doTickMethod == null || craftPlayerGetHandleMethod == null) return;
    if (!bot.isOnline() || !bot.isValid() || bot.isDead()) return;
    try {
      Object nmsPlayer = craftPlayerGetHandleMethod.invoke(bot);

      if (firstTickSet.remove(bot.getUniqueId())) {

        org.bukkit.Location loc = bot.getLocation();
        double x = loc.getX(), y = loc.getY(), z = loc.getZ();

        initPreviousPosition(nmsPlayer, x, y, z);
        doTickMethod.invoke(nmsPlayer);

        if (setPosMethod != null) setPosMethod.invoke(nmsPlayer, x, y, z);
        initPreviousPosition(nmsPlayer, x, y, z);

      } else {

        doTickMethod.invoke(nmsPlayer);
      }

    } catch (Exception e) {
      FppLogger.debug(
          "NmsPlayerSpawner.tickPhysics failed for " + bot.getName() + ": " + e.getMessage());
    }
  }

  private static boolean dispatchTickPhysicsToRegionThread(Player bot) {
    try {
      if (!foliaSchedulerResolved) {
        synchronized (NmsPlayerSpawner.class) {
          if (!foliaSchedulerResolved) {
            try {
              bukkitGetRegionSchedulerMethod = Bukkit.getServer().getClass().getMethod("getRegionScheduler");
              Object rs = bukkitGetRegionSchedulerMethod.invoke(Bukkit.getServer());
              if (rs != null) {
                regionSchedulerExecuteMethod =
                    rs.getClass().getMethod(
                        "execute", Plugin.class, World.class, int.class, int.class, Runnable.class);
                foliaSchedulerAvailable = true;
              }
            } catch (Throwable ignored) {
              foliaSchedulerAvailable = false;
            }
            foliaSchedulerResolved = true;
          }
        }
      }

      if (!foliaSchedulerAvailable || bukkitGetRegionSchedulerMethod == null || regionSchedulerExecuteMethod == null) {
        return false;
      }

      Plugin plugin = FakePlayerPlugin.getInstance();
      if (plugin == null) return false;

      Location loc = bot.getLocation();
      World world = loc.getWorld();
      if (world == null) return false;
      int cx = loc.getBlockX() >> 4;
      int cz = loc.getBlockZ() >> 4;

      Object regionScheduler = bukkitGetRegionSchedulerMethod.invoke(Bukkit.getServer());
      if (regionScheduler == null) return false;

      regionSchedulerExecuteMethod.invoke(
          regionScheduler,
          plugin,
          world,
          cx,
          cz,
          (Runnable) () -> tickPhysicsInternal(bot));
      return true;
    } catch (Throwable e) {
      FppLogger.debug("NmsPlayerSpawner: region-thread dispatch failed: " + e.getMessage());
      return false;
    }
  }

  public static void setPosition(Player bot, double x, double y, double z) {
    if (!initialized || setPosMethod == null || craftPlayerGetHandleMethod == null) return;
    try {
      Object nmsPlayer = craftPlayerGetHandleMethod.invoke(bot);
      setPosMethod.invoke(nmsPlayer, x, y, z);
    } catch (Exception e) {
      FppLogger.debug("NmsPlayerSpawner.setPosition failed: " + e.getMessage());
    }
  }

  public static void setJumping(Player bot, boolean jumping) {
    if (!initialized || jumpingField == null || craftPlayerGetHandleMethod == null) return;
    try {
      Object nmsPlayer = craftPlayerGetHandleMethod.invoke(bot);
      jumpingField.setBoolean(nmsPlayer, jumping);
    } catch (Exception e) {
      FppLogger.debug("NmsPlayerSpawner.setJumping failed: " + e.getMessage());
    }
  }

  public static boolean setListed(Player bot, boolean listed) {
    if (!initialized || listedField == null || craftPlayerGetHandleMethod == null) return false;
    try {
      Object nmsPlayer = craftPlayerGetHandleMethod.invoke(bot);
      listedField.setBoolean(nmsPlayer, listed);
      return true;
    } catch (Exception e) {
      FppLogger.debug("NmsPlayerSpawner.setListed failed: " + e.getMessage());
      return false;
    }
  }

  public static void setHeadYaw(Player bot, float yaw) {
    if (!initialized || craftPlayerGetHandleMethod == null) return;
    try {
      Object nmsPlayer = craftPlayerGetHandleMethod.invoke(bot);
      if (yHeadRotField != null) {
        yHeadRotField.setFloat(nmsPlayer, yaw);
      }
    } catch (Exception e) {
      FppLogger.debug("NmsPlayerSpawner.setHeadYaw failed: " + e.getMessage());
    }
  }

  public static void performAttack(Player bot, org.bukkit.entity.Entity target, double damage) {
    if (!initialized || craftPlayerGetHandleMethod == null) {

      if (target instanceof org.bukkit.entity.Damageable damageable) {
        damageable.damage(damage, bot);
      }
      return;
    }

    try {
      Object nmsBot = craftPlayerGetHandleMethod.invoke(bot);

      Object nmsTarget = target.getClass().getMethod("getHandle").invoke(target);

      if (attackMethod != null && nmsTarget != null) {

        attackMethod.invoke(nmsBot, nmsTarget);
      } else {

        if (target instanceof org.bukkit.entity.Damageable damageable) {
          damageable.damage(damage, bot);
        }
      }
    } catch (Exception e) {
      FppLogger.debug("NmsPlayerSpawner.performAttack failed: " + e.getMessage());

      if (target instanceof org.bukkit.entity.Damageable damageable) {
        damageable.damage(damage, bot);
      }
    }
  }

  public static void setMovementForward(Player bot, float forward) {
    if (!initialized || zzaField == null || craftPlayerGetHandleMethod == null) return;
    try {
      Object nmsPlayer = craftPlayerGetHandleMethod.invoke(bot);
      zzaField.setFloat(nmsPlayer, forward);
    } catch (Exception e) {
      FppLogger.debug("NmsPlayerSpawner.setMovementForward failed: " + e.getMessage());
    }
  }

  public static void setMovementStrafe(Player bot, float strafe) {
    if (!initialized || xxaField == null || craftPlayerGetHandleMethod == null) return;
    try {
      Object nmsPlayer = craftPlayerGetHandleMethod.invoke(bot);
      xxaField.setFloat(nmsPlayer, strafe);
    } catch (Exception e) {
      FppLogger.debug("NmsPlayerSpawner.setMovementStrafe failed: " + e.getMessage());
    }
  }

  public static void applyServerVelocity(Player bot, org.bukkit.util.Vector velocity) {
    if (!initialized || craftPlayerGetHandleMethod == null || bot == null || velocity == null) return;
    try {
      Object nmsPlayer = craftPlayerGetHandleMethod.invoke(bot);
      Class<?> vec3Class = Class.forName("net.minecraft.world.phys.Vec3");
      Object vec3 =
          vec3Class
              .getConstructor(double.class, double.class, double.class)
              .newInstance(velocity.getX(), velocity.getY(), velocity.getZ());
      Method setDelta = findMethod(nmsPlayer.getClass(), "setDeltaMovement", 1, vec3Class);
      if (setDelta != null) {
        setDelta.invoke(nmsPlayer, vec3);
      } else {
        bot.setVelocity(velocity);
      }

      Field hurtMarked = findFieldInHierarchy(nmsPlayer.getClass(), "hurtMarked");
      if (hurtMarked != null && hurtMarked.getType() == boolean.class) {
        hurtMarked.setBoolean(nmsPlayer, true);
      }
      Field hasImpulse = findFieldInHierarchy(nmsPlayer.getClass(), "hasImpulse");
      if (hasImpulse != null && hasImpulse.getType() == boolean.class) {
        hasImpulse.setBoolean(nmsPlayer, true);
      }
    } catch (Exception e) {
      try {
        bot.setVelocity(velocity);
      } catch (Exception ignored) {
      }
      FppLogger.debug("NmsPlayerSpawner.applyServerVelocity failed: " + e.getMessage());
    }
  }

  public static void removeFakePlayer(Player player) {
    removeFakePlayer(player, true);
  }

  public static void removeFakePlayerFast(Player player) {
    removeFakePlayer(player, false);
  }

  private static void removeFakePlayer(Player player, boolean saveData) {
    if (player == null) return;
    try {
      firstTickSet.remove(player.getUniqueId());
      if (player.isOnline()) {
        final String name = player.getName();
        final UUID uuid = player.getUniqueId();

        FppLogger.debug("NmsPlayerSpawner: removing '" + name + "' uuid=" + uuid);

        if (saveData) {
          try {
            player.saveData();
            FppLogger.debug("NmsPlayerSpawner: saved playerdata for '" + name + "' uuid=" + uuid);
          } catch (Exception e) {
            FppLogger.warn(
                "NmsPlayerSpawner: saveData failed for '"
                    + name
                    + "' uuid="
                    + uuid
                    + ": "
                    + e.getMessage());
          }
        }

        boolean removedViaPlayerList = false;
        if (initialized
            && craftPlayerGetHandleMethod != null
            && craftServerGetServerMethod != null
            && getPlayerListMethod != null
            && playerListRemoveMethod != null) {
          try {
            Object nmsPlayer = craftPlayerGetHandleMethod.invoke(player);
            Object minecraftServer =
                craftServerGetServerMethod.invoke(org.bukkit.Bukkit.getServer());
            Object playerList = getPlayerListMethod.invoke(minecraftServer);
            playerListRemoveMethod.invoke(playerList, nmsPlayer);
            removedViaPlayerList = true;
            FppLogger.debug(
                "NmsPlayerSpawner: removed '" + name + "' via PlayerList.remove() uuid=" + uuid);
          } catch (Exception e) {
            FppLogger.debug(
                "NmsPlayerSpawner: PlayerList.remove failed for '"
                    + name
                    + "' uuid="
                    + uuid
                    + ": "
                    + e.getMessage()
                    + " - falling back to kick");
          }
        }

        if (!removedViaPlayerList && player.isOnline()) {
          player.kick(net.kyori.adventure.text.Component.empty());
        }
      }
    } catch (Exception e) {
      FppLogger.debug(
          "NmsPlayerSpawner.removeFakePlayer failed for "
              + player.getName()
              + ": "
              + e.getMessage());
    }
  }

  private static void ensurePlayerDataExists(
      Object minecraftServer, Object serverPlayer, String name, UUID uuid) {
    if (playerDataStorageField == null) {
      FppLogger.debug(
          "NmsPlayerSpawner: ensurePlayerDataExists skipped"
              + " - WorldNBTStorage field not cached (name="
              + name
              + " uuid="
              + uuid
              + ")");
      return;
    }
    try {
      Object playerList = getPlayerListMethod.invoke(minecraftServer);
      Object playerDataStorage = playerDataStorageField.get(playerList);

      if (getPlayerDirMethod != null) {
        java.io.File playerDir = (java.io.File) getPlayerDirMethod.invoke(playerDataStorage);
        java.io.File playerFile = new java.io.File(playerDir, uuid + ".dat");
        if (playerFile.exists()) {
          FppLogger.debug(
              "NmsPlayerSpawner: playerdata found for '"
                  + name
                  + "' uuid="
                  + uuid
                  + " - returning player");
          return;
        }
      }

      if (playerDataSaveMethod != null) {
        playerDataSaveMethod.invoke(playerDataStorage, serverPlayer);
        FppLogger.debug(
            "NmsPlayerSpawner: created initial playerdata for '"
                + name
                + "' uuid="
                + uuid
                + " - will be treated as returning player on next spawn");
      } else {
        FppLogger.debug(
            "NmsPlayerSpawner: playerdata file missing but save method"
                + " not cached - first-join message may appear (name="
                + name
                + ")");
      }
    } catch (Exception e) {

      FppLogger.warn(
          "NmsPlayerSpawner: ensurePlayerDataExists failed for '"
              + name
              + "' uuid="
              + uuid
              + ": "
              + e.getMessage());
    }
  }

  public static void reInjectFakeListener(Player bot) {
    if (!initialized || craftPlayerGetHandleMethod == null || connectionFieldInPlayer == null) {
      FppLogger.debug("NmsPlayerSpawner.reInjectFakeListener: not available");
      return;
    }
    try {
      Object nmsPlayer = craftPlayerGetHandleMethod.invoke(bot);
      if (nmsPlayer == null) return;

      Object currentConn = connectionFieldInPlayer.get(nmsPlayer);
      if (currentConn instanceof FakeServerGamePacketListenerImpl) {

        FppLogger.debug(
            "NmsPlayerSpawner.reInjectFakeListener: "
                + bot.getName()
                + " already has FakeServerGamePacketListenerImpl");
        return;
      }

      Object networkConn = null;
      if (currentConn != null && connectionClass != null) {
        for (Field f : getAllDeclaredFields(currentConn.getClass())) {
          if (java.lang.reflect.Modifier.isStatic(f.getModifiers())) continue;
          if (connectionClass.isAssignableFrom(f.getType())) {
            f.setAccessible(true);
            networkConn = f.get(currentConn);
            break;
          }
        }
      }
      if (networkConn == null) {

        networkConn = createFakeConnection();
      }
      if (networkConn == null) {
        FppLogger.warn(
            "NmsPlayerSpawner.reInjectFakeListener: cannot get connection for " + bot.getName());
        return;
      }

      Object gameProfile = null;
      try {
        Method gpMethod = findMethodByName(nmsPlayer.getClass(), "getGameProfile", 0);
        if (gpMethod != null) {
          gpMethod.setAccessible(true);
          gameProfile = gpMethod.invoke(nmsPlayer);
        }
      } catch (Exception ignored) {
      }
      if (gameProfile == null) {

        for (Field f : getAllDeclaredFields(nmsPlayer.getClass())) {
          if (f.getType().getSimpleName().equals("GameProfile")) {
            f.setAccessible(true);
            gameProfile = f.get(nmsPlayer);
            break;
          }
        }
      }

      Object clientInfo = getClientInformation();
      injectFakeListener(
          craftServerGetServerMethod.invoke(org.bukkit.Bukkit.getServer()),
          networkConn,
          nmsPlayer,
          gameProfile,
          clientInfo);

      clearAwaitingPosition(nmsPlayer);

      firstTickSet.add(bot.getUniqueId());

      FppLogger.debug("NmsPlayerSpawner.reInjectFakeListener: success for " + bot.getName());
    } catch (Exception e) {
      FppLogger.warn(
          "NmsPlayerSpawner.reInjectFakeListener failed for "
              + bot.getName()
              + ": "
              + e.getMessage());
    }
  }

  private static void clearAwaitingPosition(Object nmsPlayer) {
    try {

      Object sgpl = connectionFieldInPlayer.get(nmsPlayer);
      if (sgpl == null) return;
      for (Field f : getAllDeclaredFields(sgpl.getClass())) {
        if (java.lang.reflect.Modifier.isStatic(f.getModifiers())) continue;

        if (f.getName().equals("awaitingPositionFromClient") || f.getName().contains("awaiting")) {
          f.setAccessible(true);
          if (!f.getType().isPrimitive()) {
            f.set(sgpl, null);
            FppLogger.debug("NmsPlayerSpawner: cleared " + f.getName() + " on SGPL");
            return;
          }
        }
      }

      for (Field f : getAllDeclaredFields(sgpl.getClass())) {
        if (java.lang.reflect.Modifier.isStatic(f.getModifiers())) continue;
        if (f.getType().getSimpleName().equals("Vec3")) {
          f.setAccessible(true);
          Object val = f.get(sgpl);
          if (val != null) {
            f.set(sgpl, null);
            FppLogger.debug(
                "NmsPlayerSpawner: cleared Vec3 field '"
                    + f.getName()
                    + "' on SGPL (likely awaitingPositionFromClient)");
            return;
          }
        }
      }
    } catch (Exception e) {
      FppLogger.debug("NmsPlayerSpawner.clearAwaitingPosition failed: " + e.getMessage());
    }
  }

  private static Method findMethodByName(Class<?> clazz, String name, int paramCount) {
    Class<?> cur = clazz;
    while (cur != null && cur != Object.class) {
      for (Method m : cur.getDeclaredMethods()) {
        if (m.getName().equals(name) && m.getParameterCount() == paramCount) {
          return m;
        }
      }
      cur = cur.getSuperclass();
    }
    return null;
  }

  public static void setPing(Player bot, int pingMs) {
    if (bot == null || !initialized) return;
    try {
      Object nmsPlayer = craftBukkitGetHandle(bot);
      if (nmsPlayer == null) return;
      Field latencyField = findFieldByType(nmsPlayer.getClass(), int.class, "latency");
      if (latencyField != null) {
        latencyField.setAccessible(true);
        latencyField.set(nmsPlayer, Math.max(0, pingMs));
      }
    } catch (Exception e) {
      me.bill.fakePlayerPlugin.config.Config.debugNms("setPing failed: " + e.getMessage());
    }
  }

  private static Object craftBukkitGetHandle(Player player) {
    try {
      return player.getClass().getMethod("getHandle").invoke(player);
    } catch (Exception e) {
      return null;
    }
  }

  private static Field findFieldByType(Class<?> clazz, Class<?> type, String preferredName) {
    for (Class<?> c = clazz; c != null && c != Object.class; c = c.getSuperclass()) {
      for (Field f : c.getDeclaredFields()) {
        if (f.getType() == type && f.getName().equals(preferredName)) {
          return f;
        }
      }
    }
    for (Class<?> c = clazz; c != null && c != Object.class; c = c.getSuperclass()) {
      for (Field f : c.getDeclaredFields()) {
        if (f.getType() == type
            && (f.getName().contains("latency")
                || f.getName().contains("ping")
                || f.getName().contains("ping")
                || f.getName().contains("Latency")
                || f.getName().contains("Ping"))) {
          return f;
        }
      }
    }
    return null;
  }

  public static void startUsingMainHandItem(Player bot) {
    if (!initialized || craftPlayerGetHandleMethod == null) return;
    try {
      Object nmsPlayer = craftPlayerGetHandleMethod.invoke(bot);
      ClassLoader cl = nmsPlayer.getClass().getClassLoader();

      Class<?> interactionHandClass = cl.loadClass("net.minecraft.world.InteractionHand");
      Object[] hands = interactionHandClass.getEnumConstants();
      if (hands == null || hands.length == 0) return;
      Object mainHand = hands[0];

      for (Method m : nmsPlayer.getClass().getMethods()) {
        if (m.getParameterCount() == 1 && m.getParameterTypes()[0] == interactionHandClass) {
          String name = m.getName();
          if (name.equals("startUsingItem") || name.equals("c")) {
            m.setAccessible(true);
            m.invoke(nmsPlayer, mainHand);
            return;
          }
        }
      }

      for (Method m : nmsPlayer.getClass().getMethods()) {
        if (m.getParameterCount() == 1 && m.getParameterTypes()[0] == interactionHandClass) {
          m.setAccessible(true);
          m.invoke(nmsPlayer, mainHand);
          return;
        }
      }
    } catch (Exception e) {
      FppLogger.debug("NmsPlayerSpawner.startUsingMainHandItem failed: " + e.getMessage());
    }
  }

  public static void interactBlock(Player bot, org.bukkit.block.Block block) {
    if (!initialized || craftPlayerGetHandleMethod == null) return;
    try {
      Object nmsPlayer = craftPlayerGetHandleMethod.invoke(bot);
      ClassLoader cl = nmsPlayer.getClass().getClassLoader();

      Class<?> interactionHandClass = cl.loadClass("net.minecraft.world.InteractionHand");
      Object[] hands = interactionHandClass.getEnumConstants();
      if (hands == null || hands.length == 0) return;
      Object mainHand = hands[0];

      Class<?> blockPosClass = cl.loadClass("net.minecraft.core.BlockPos");
      Class<?> directionClass = cl.loadClass("net.minecraft.core.Direction");
      Class<?> blockHitResultClass = cl.loadClass("net.minecraft.world.phys.BlockHitResult");

      Object blockPos = blockPosClass.getConstructor(int.class, int.class, int.class)
          .newInstance(block.getX(), block.getY(), block.getZ());

      Object direction = directionClass.getMethod("getNearest", float.class, float.class, float.class)
          .invoke(null, 0f, -1f, 0f);

      Object blockHit = blockHitResultClass.getConstructor(
              Vector.class, directionClass, blockPosClass, boolean.class)
          .newInstance(new Vector(0.5, 0.5, 0.5), direction, blockPos, false);

      Object gameMode = nmsPlayer.getClass().getMethod("gameMode").invoke(nmsPlayer);
      Object level = nmsPlayer.getClass().getMethod("level").invoke(nmsPlayer);
      Object itemStack = nmsPlayer.getClass().getMethod("getItemInHand", interactionHandClass)
          .invoke(nmsPlayer, mainHand);

      Object result = gameMode.getClass().getMethod("useItemOn",
              nmsPlayer.getClass(), level.getClass(),
              cl.loadClass("net.minecraft.world.item.ItemStack"), interactionHandClass, blockHitResultClass)
          .invoke(gameMode, nmsPlayer, level, itemStack, mainHand, blockHit);

      if (result != null) {
        Method consumesAction = result.getClass().getMethod("consumesAction");
        if ((boolean) consumesAction.invoke(result)) {
          Method swing = nmsPlayer.getClass().getMethod("swing", interactionHandClass);
          swing.invoke(nmsPlayer, mainHand);
        }
      }
    } catch (Exception e) {
      FppLogger.debug("NmsPlayerSpawner.interactBlock failed: " + e.getMessage());
    }
  }

  public static void forceAllSkinParts(Player bot) {
    if (!initialized
        || skinPartsDataAccessor == null
        || entityDataFieldForSkinParts == null
        || synchedEntityDataSetMethod == null
        || craftPlayerGetHandleMethod == null) return;
    try {
      Object nmsPlayer = craftPlayerGetHandleMethod.invoke(bot);
      Object entityData = entityDataFieldForSkinParts.get(nmsPlayer);

      synchedEntityDataSetMethod.invoke(entityData, skinPartsDataAccessor, (byte) 0x7F);
      FppLogger.debug("NmsPlayerSpawner: skin-parts forced to 0x7F for " + bot.getName());
    } catch (Exception e) {
      FppLogger.debug(
          "NmsPlayerSpawner.forceAllSkinParts failed for " + bot.getName() + ": " + e.getMessage());
    }
  }

  private static void injectFakeListener(
      Object minecraftServer,
      Object conn,
      Object serverPlayer,
      Object gameProfile,
      Object clientInfo) {
    if (connectionFieldInPlayer == null) {
      FppLogger.warn("NmsPlayerSpawner: cannot inject fake listener - connection field not found");
      return;
    }
    try {
      Object cookie = createCookieDynamic(gameProfile, clientInfo);
      if (cookie == null) {
        FppLogger.warn("NmsPlayerSpawner: cannot inject fake listener - cookie creation failed");
        return;
      }

      FakeServerGamePacketListenerImpl fakeListener =
          FakeServerGamePacketListenerImpl.create(minecraftServer, conn, serverPlayer, cookie);

      connectionFieldInPlayer.set(serverPlayer, fakeListener);
      FppLogger.debug(
          "NmsPlayerSpawner: FakeServerGamePacketListenerImpl injected into"
              + " serverPlayer.connection");

      injectPacketListenerIntoConnection(conn, fakeListener);

    } catch (Exception e) {
      FppLogger.warn("NmsPlayerSpawner: fake listener injection failed: " + e.getMessage());
      FppLogger.debug(Arrays.toString(e.getStackTrace()));
    }
  }

  private static void injectPacketListenerIntoConnection(
      Object conn, FakeServerGamePacketListenerImpl fakeListener) {
    if (conn == null || serverGamePacketListenerClass == null) return;
    try {
      for (Field f : getAllDeclaredFields(conn.getClass())) {
        if (java.lang.reflect.Modifier.isStatic(f.getModifiers())) continue;
        try {
          f.setAccessible(true);
          Object val = f.get(conn);
          if (val != null && serverGamePacketListenerClass.isInstance(val)) {
            f.set(conn, fakeListener);
            FppLogger.debug(
                "NmsPlayerSpawner: Connection."
                    + f.getName()
                    + " updated to FakeServerGamePacketListenerImpl"
                    + " (was "
                    + val.getClass().getSimpleName()
                    + ")");
            return;
          }
        } catch (Exception ignored) {
        }
      }
      FppLogger.debug(
          "NmsPlayerSpawner: Connection packetListener field not found"
              + " - onDisconnect override may not fire on double-disconnect");
    } catch (Exception e) {
      FppLogger.debug(
          "NmsPlayerSpawner: injectPacketListenerIntoConnection failed: " + e.getMessage());
    }
  }

  private static Object createFakeConnection() {
    try {
      FakeConnection conn = new FakeConnection(InetAddress.getLoopbackAddress());
      FppLogger.debug("NmsPlayerSpawner: FakeConnection created (direct Connection subclass)");
      return conn;

    } catch (Exception e) {
      FppLogger.warn("NmsPlayerSpawner.createFakeConnection failed: " + e.getMessage());
      return null;
    }
  }

  private static Object getClientInformation() {
    if (clientInfoDefault != null) return clientInfoDefault;
    if (clientInformationClass == null) return null;
    try {
      return clientInformationClass.getMethod("createDefault").invoke(null);
    } catch (Exception e) {
      return null;
    }
  }

  private static Object createServerPlayer(
      Object minecraftServer, Object serverLevel, Object gameProfile, Object clientInfo) {

    if (clientInfo != null && clientInformationClass != null) {
      try {
        Constructor<?> ctor =
            serverPlayerClass.getConstructor(
                minecraftServerClass,
                serverLevelClass,
                gameProfile.getClass(),
                clientInformationClass);
        return ctor.newInstance(minecraftServer, serverLevel, gameProfile, clientInfo);
      } catch (NoSuchMethodException ignored) {
      } catch (Exception e) {
        FppLogger.debug("4-arg ServerPlayer ctor failed: " + e.getMessage());
      }
    }

    try {
      Constructor<?> ctor =
          serverPlayerClass.getConstructor(
              minecraftServerClass, serverLevelClass, gameProfile.getClass());
      return ctor.newInstance(minecraftServer, serverLevel, gameProfile);
    } catch (Exception e) {
      FppLogger.error("NmsPlayerSpawner: no ServerPlayer constructor matched: " + e.getMessage());
      return null;
    }
  }

  private static boolean placePlayer(
      Object minecraftServer,
      Object conn,
      Object serverPlayer,
      Object gameProfile,
      Object clientInfo) {
    try {
      Object playerList = getPlayerListMethod.invoke(minecraftServer);
      if (conn == null || commonListenerCookieClass == null) {
        FppLogger.debug("placeNewPlayer skipped (conn=" + conn + ")");
        return false;
      }
      Object cookie = createCookieDynamic(gameProfile, clientInfo);
      if (cookie == null) return false;

      Method placeMethod = findMethod(playerList.getClass(), "placeNewPlayer", 3);
      if (placeMethod != null) {
        placeMethod.setAccessible(true);
        placeMethod.invoke(playerList, conn, serverPlayer, cookie);
        return true;
      }
      FppLogger.warn("NmsPlayerSpawner: placeNewPlayer(3-arg) not found on PlayerList");
    } catch (Exception e) {
      Throwable cause = e.getCause() != null ? e.getCause() : e;
      if (isWorldDataNotReadyFailure(cause)) {
        FppLogger.warn(
            "NmsPlayerSpawner.placePlayer deferred: world data not ready on this thread yet");
      } else {
        FppLogger.warn(
            "NmsPlayerSpawner.placePlayer failed: "
                + cause.getClass().getSimpleName()
                + ": "
                + cause.getMessage());
      }
    }
    return false;
  }

  private static boolean isWorldDataNotReadyFailure(Throwable cause) {
    if (cause == null) return false;
    String msg = cause.getMessage();
    return cause instanceof NullPointerException
        && msg != null
        && msg.contains("getCurrentWorldData()")
        && msg.contains("connections");
  }

  private static void cleanupFailedSpawn(Object minecraftServer, Object serverPlayer, String name) {
    try {
      Method getBukkitEntity = serverPlayerClass.getMethod("getBukkitEntity");
      Object entity = getBukkitEntity.invoke(serverPlayer);
      if (entity instanceof Player player) {
        FppLogger.warn("NmsPlayerSpawner: cleaning up partial failed spawn for " + name);
        removeFakePlayer(player);
        return;
      }
    } catch (Exception ignored) {
    }

    try {
      if (minecraftServer != null && getPlayerListMethod != null && playerListRemoveMethod != null) {
        Object playerList = getPlayerListMethod.invoke(minecraftServer);
        playerListRemoveMethod.invoke(playerList, serverPlayer);
      }
    } catch (Exception ignored) {
    }
  }

  private static boolean placePlayerOnRegionThread(
      World world,
      double x,
      double z,
      Object minecraftServer,
      Object conn,
      Object serverPlayer,
      Object gameProfile,
      Object clientInfo) {
    try {
      Plugin plugin = FakePlayerPlugin.getInstance();
      if (plugin == null) return false;
      if (world == null) return false;

      Method getRegionScheduler = Bukkit.getServer().getClass().getMethod("getRegionScheduler");
      Object regionScheduler = getRegionScheduler.invoke(Bukkit.getServer());
      if (regionScheduler == null) return false;

      int cx = ((int) Math.floor(x)) >> 4;
      int cz = ((int) Math.floor(z)) >> 4;

      // We can't safely block a Folia scheduler thread waiting for another region.
      // Callers on a Folia scheduler thread must use dispatchPlacePlayerAsync() instead.
      String threadName = Thread.currentThread().getName();
      boolean onSchedulerThread =
          threadName != null
              && (threadName.startsWith("Folia Region Scheduler Thread")
                  || threadName.startsWith("Folia Async Scheduler Thread"));
      if (onSchedulerThread) {
        return false;
      }

      AtomicBoolean placed = new AtomicBoolean(false);
      CountDownLatch latch = new CountDownLatch(1);

      Runnable task =
          () -> {
            try {
              placed.set(placePlayer(minecraftServer, conn, serverPlayer, gameProfile, clientInfo));
            } finally {
              latch.countDown();
            }
          };

      Method execute =
          regionScheduler
              .getClass()
              .getMethod("execute", Plugin.class, World.class, int.class, int.class, Runnable.class);
      execute.invoke(regionScheduler, plugin, world, cx, cz, task);

      if (!latch.await(5, TimeUnit.SECONDS)) {
        FppLogger.warn("NmsPlayerSpawner: region-thread placeNewPlayer timed out");
        return false;
      }
      if (placed.get()) {
        FppLogger.debug("NmsPlayerSpawner: placeNewPlayer succeeded on region thread");
      }
      return placed.get();
    } catch (NoSuchMethodException ignored) {
      return false;
    } catch (Exception e) {
      Throwable cause = e.getCause() != null ? e.getCause() : e;
      FppLogger.warn(
          "NmsPlayerSpawner: region-thread placeNewPlayer failed: "
              + cause.getClass().getSimpleName()
              + ": "
              + cause.getMessage());
      return false;
    }
  }

  /**
   * Async Folia-compatible placement. Dispatches placeNewPlayer to the destination chunk's
   * region thread and invokes the callback once placement has been attempted. Must be used
   * when the caller is already on a Folia scheduler thread.
   */
  private static boolean dispatchPlacePlayerAsync(
      World world,
      double x,
      double z,
      Object minecraftServer,
      Object conn,
      Object serverPlayer,
      Object gameProfile,
      Object clientInfo,
      java.util.function.Consumer<Boolean> onComplete) {
    try {
      Plugin plugin = FakePlayerPlugin.getInstance();
      if (plugin == null || world == null) return false;
      Method getRegionScheduler = Bukkit.getServer().getClass().getMethod("getRegionScheduler");
      Object regionScheduler = getRegionScheduler.invoke(Bukkit.getServer());
      if (regionScheduler == null) return false;

      int cx = ((int) Math.floor(x)) >> 4;
      int cz = ((int) Math.floor(z)) >> 4;
      Method execute =
          regionScheduler
              .getClass()
              .getMethod("execute", Plugin.class, World.class, int.class, int.class, Runnable.class);

      Runnable task =
          () -> {
            boolean ok = false;
            try {
              ok = placePlayer(minecraftServer, conn, serverPlayer, gameProfile, clientInfo);
            } catch (Throwable t) {
              FppLogger.warn(
                  "NmsPlayerSpawner: async region-thread placeNewPlayer failed: " + t.getMessage());
            }
            onComplete.accept(ok);
          };
      execute.invoke(regionScheduler, plugin, world, cx, cz, task);
      return true;
    } catch (NoSuchMethodException ignored) {
      return false;
    } catch (Exception e) {
      FppLogger.debug(
          "NmsPlayerSpawner: dispatchPlacePlayerAsync failed: " + e.getMessage());
      return false;
    }
  }

  /** True when running under Folia (RegionScheduler exists). */
  private static volatile Boolean cachedFoliaDetected;

  public static boolean isFolia() {
    Boolean cached = cachedFoliaDetected;
    if (cached != null) return cached;
    try {
      Bukkit.getServer().getClass().getMethod("getRegionScheduler");
      cachedFoliaDetected = Boolean.TRUE;
    } catch (NoSuchMethodException e) {
      cachedFoliaDetected = Boolean.FALSE;
    }
    return cachedFoliaDetected;
  }

  /**
   * Async spawn entry point for Folia. Dispatches placement to the destination chunk's region
   * thread and calls {@code callback} with the resulting Bukkit Player on the global scheduler
   * thread (or null on failure). On Paper/Spigot, runs fully synchronously and invokes the
   * callback directly.
   */
  public static void spawnFakePlayerAsync(
      UUID uuid,
      String name,
      SkinProfile skin,
      World world,
      double x,
      double y,
      double z,
      java.util.function.Consumer<Player> callback) {
    if (!isAvailable()) {
      FppLogger.warn("NmsPlayerSpawner not available - cannot spawn " + name);
      callback.accept(null);
      return;
    }
    if (!isFolia()) {
      callback.accept(spawnFakePlayer(uuid, name, skin, world, x, y, z));
      return;
    }

    try {
      Object gameProfile = gameProfileConstructor.newInstance(uuid, name);
      if (skin != null && skin.isValid()) {
        try {
          gameProfile = SkinProfileInjector.createGameProfile(gameProfileClass, uuid, name, skin);
        } catch (Exception e) {
          FppLogger.warn("NmsPlayerSpawner: skin injection failed: " + e.getMessage());
        }
      }

      Object minecraftServer = craftServerGetServerMethod.invoke(Bukkit.getServer());
      Object serverLevel = craftWorldGetHandleMethod.invoke(world);
      Object clientInfo = getClientInformation();

      Object serverPlayer =
          createServerPlayer(minecraftServer, serverLevel, gameProfile, clientInfo);
      if (serverPlayer == null) {
        FppLogger.warn("NmsPlayerSpawner: failed to create ServerPlayer for " + name);
        callback.accept(null);
        return;
      }

      if (setPosMethod != null) setPosMethod.invoke(serverPlayer, x, y, z);
      initPreviousPosition(serverPlayer, x, y, z);

      Object conn = createFakeConnection();
      if (conn == null) {
        FppLogger.warn("NmsPlayerSpawner: failed to create fake connection for " + name);
        callback.accept(null);
        return;
      }

      ensurePlayerDataExists(minecraftServer, serverPlayer, name, uuid);

      final Object fMinecraftServer = minecraftServer;
      final Object fConn = conn;
      final Object fServerPlayer = serverPlayer;
      final Object fGameProfile = gameProfile;
      final Object fClientInfo = clientInfo;

      boolean dispatched =
          dispatchPlacePlayerAsync(
              world,
              x,
              z,
              minecraftServer,
              conn,
              serverPlayer,
              gameProfile,
              clientInfo,
              placed -> {
                if (!placed) {
                  cleanupFailedSpawn(fMinecraftServer, fServerPlayer, name);
                  FppLogger.warn("NmsPlayerSpawner: placeNewPlayer failed for " + name);
                  callback.accept(null);
                  return;
                }
                try {
                  if (setPosMethod != null) setPosMethod.invoke(fServerPlayer, x, y, z);
                  initPreviousPosition(fServerPlayer, x, y, z);
                  injectFakeListener(
                      fMinecraftServer, fConn, fServerPlayer, fGameProfile, fClientInfo);
                  Method getBukkitEntity = serverPlayerClass.getMethod("getBukkitEntity");
                  Object entity = getBukkitEntity.invoke(fServerPlayer);
                  if (entity instanceof Player result) {
                    result.setGameMode(org.bukkit.GameMode.SURVIVAL);
                    setListed(result, true);
                    forceAllSkinParts(result);
                    firstTickSet.add(uuid);
                    FppLogger.debug(
                        "NmsPlayerSpawner: spawned " + name + " (" + uuid + ") async");
                    callback.accept(result);
                  } else {
                    callback.accept(null);
                  }
                } catch (Exception e) {
                  FppLogger.error(
                      "NmsPlayerSpawner: async post-placement failed for "
                          + name
                          + ": "
                          + e.getMessage());
                  callback.accept(null);
                }
              });

      if (!dispatched) {
        cleanupFailedSpawn(minecraftServer, serverPlayer, name);
        callback.accept(null);
      }
    } catch (Exception e) {
      FppLogger.error(
          "NmsPlayerSpawner.spawnFakePlayerAsync failed for " + name + ": " + e.getMessage());
      callback.accept(null);
    }
  }

  private static Object createCookieDynamic(Object gameProfile, Object clientInfo) {
    if (commonListenerCookieClass == null) return null;

    try {
      Method factory =
          commonListenerCookieClass.getMethod(
              "createInitial", gameProfile.getClass(), boolean.class);
      return factory.invoke(null, gameProfile, false);
    } catch (Exception ignored) {
    }

    for (Constructor<?> c : commonListenerCookieClass.getDeclaredConstructors()) {
      c.setAccessible(true);
      Class<?>[] p = c.getParameterTypes();
      if (p.length > 0 && p[p.length - 1].getSimpleName().contains("DefaultConstructorMarker")) {
        continue;
      }
      try {
        Object result =
            switch (p.length) {
              case 1 -> c.newInstance(gameProfile);
              case 2 -> c.newInstance(gameProfile, 0);
              case 3 -> c.newInstance(gameProfile, 0, clientInfo);
              case 4 -> c.newInstance(gameProfile, 0, clientInfo, false);
              case 5 -> c.newInstance(gameProfile, 0, clientInfo, false, false);
              case 7 ->
                  c.newInstance(
                      gameProfile, 0, clientInfo, false, null, Collections.emptySet(), null);
              default -> null;
            };
        if (result != null) return result;
      } catch (Exception ignored) {
      }
    }
    FppLogger.debug("NmsPlayerSpawner: no CommonListenerCookie constructor succeeded");
    return null;
  }

  private static void initPreviousPosition(Object nmsPlayer, double x, double y, double z) {
    try {
      if (xoField != null) xoField.setDouble(nmsPlayer, x);
      if (yoField != null) yoField.setDouble(nmsPlayer, y);
      if (zoField != null) zoField.setDouble(nmsPlayer, z);
    } catch (Exception ignored) {
    }
  }

  private static Method findMethod(Class<?> clazz, String name, int paramCount) {
    Class<?> cur = clazz;
    while (cur != null && cur != Object.class) {
      for (Method m : cur.getDeclaredMethods()) {
        if (m.getName().equals(name) && m.getParameterCount() == paramCount) {
          m.setAccessible(true);
          return m;
        }
      }
      cur = cur.getSuperclass();
    }
    return null;
  }

  private static Method findMethod(
      Class<?> clazz, String name, int paramCount, Class<?>... paramTypes) {
    Class<?> cur = clazz;
    while (cur != null && cur != Object.class) {
      for (Method m : cur.getDeclaredMethods()) {
        if (!m.getName().equals(name) || m.getParameterCount() != paramCount) continue;
        if (paramTypes.length == 0) {
          m.setAccessible(true);
          return m;
        }

        Class<?>[] mParams = m.getParameterTypes();
        boolean match = true;
        for (int i = 0; i < paramTypes.length && i < mParams.length; i++) {
          if (!mParams[i].isAssignableFrom(paramTypes[i])) {
            match = false;
            break;
          }
        }
        if (match) {
          m.setAccessible(true);
          return m;
        }
      }
      cur = cur.getSuperclass();
    }
    return null;
  }

  private static Field findFieldInHierarchy(Class<?> clazz, String name) {
    Class<?> cur = clazz;
    while (cur != null && cur != Object.class) {
      try {
        Field f = cur.getDeclaredField(name);
        f.setAccessible(true);
        return f;
      } catch (NoSuchFieldException ignored) {
        cur = cur.getSuperclass();
      }
    }
    return null;
  }

  private static Method findMethodBySignature(
      Class<?> clazz, int paramCount, Class<?>... paramTypes) {
    Class<?> cur = clazz;
    while (cur != null && cur != Object.class) {
      for (Method m : cur.getDeclaredMethods()) {
        if (m.getParameterCount() == paramCount
            && Arrays.equals(m.getParameterTypes(), paramTypes)) {
          m.setAccessible(true);
          return m;
        }
      }
      cur = cur.getSuperclass();
    }
    return null;
  }

  private static Field findFieldByName(Class<?> clazz, String name) {
    Class<?> cur = clazz;
    while (cur != null && cur != Object.class) {
      for (Field f : cur.getDeclaredFields()) {
        if (f.getName().equals(name)) {
          f.setAccessible(true);
          return f;
        }
      }
      cur = cur.getSuperclass();
    }
    return null;
  }

  private static List<Field> getAllDeclaredFields(Class<?> clazz) {
    List<Field> fields = new ArrayList<>();
    Class<?> cur = clazz;
    while (cur != null && cur != Object.class) {
      Collections.addAll(fields, cur.getDeclaredFields());
      cur = cur.getSuperclass();
    }
    return fields;
  }
}
