package me.bill.fakePlayerPlugin.fakeplayer;

import me.bill.fakePlayerPlugin.fakeplayer.network.FakeChannel;
import me.bill.fakePlayerPlugin.fakeplayer.network.FakeServerGamePacketListenerImpl;
import me.bill.fakePlayerPlugin.util.FppLogger;
import org.bukkit.Bukkit;
import org.bukkit.World;
import org.bukkit.entity.Player;

import java.lang.reflect.Constructor;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.util.*;

/**
 * Reflection-based NMS {@code ServerPlayer} factory and physics driver.
 *
 * <h3>How physics work (matching the hello09x/fakeplayer reference plugin)</h3>
 * <ol>
 *   <li>Spawn: create a real {@code ServerPlayer} and call
 *       {@code PlayerList.placeNewPlayer()} with a {@link FakeChannel}-backed
 *       connection.  {@code placeNewPlayer()} creates a vanilla
 *       {@code ServerGamePacketListenerImpl} (SGPL) and sends a spawn-position
 *       packet — setting {@code awaitingPositionFromClient} on that SGPL.</li>
 *   <li>After {@code placeNewPlayer()}, create a fresh
 *       {@link FakeServerGamePacketListenerImpl} (a SGPL subclass whose
 *       {@code send()} is a no-op).  This fresh instance has
 *       {@code awaitingPositionFromClient == null} and it can never be set
 *       (because {@code send()} discards all packets).  Assign it to
 *       {@code ServerPlayer.connection} via reflection.</li>
 *   <li>Every tick: the server's PlayerList calls {@code handle.connection.tick()}.
 *       Since {@code awaitingPositionFromClient} is always null on our fake
 *       listener, no position correction runs.  We also call
 *       {@code ServerPlayer.doTick()} to drive gravity and collision.</li>
 * </ol>
 */
public final class NmsPlayerSpawner {

    // ── Initialisation state ───────────────────────────────────────────────────
    private static volatile boolean initialized = false;
    private static volatile boolean failed = false;

    // ── CraftBukkit helpers ────────────────────────────────────────────────────
    private static Method craftPlayerGetHandleMethod;
    private static Method craftServerGetServerMethod;
    private static Method craftWorldGetHandleMethod;

    // ── NMS classes ───────────────────────────────────────────────────────────
    private static Class<?> minecraftServerClass;
    private static Class<?> serverLevelClass;
    private static Class<?> serverPlayerClass;
    private static Class<?> clientInformationClass;
    private static Class<?> connectionClass;
    private static Class<?> commonListenerCookieClass;
    private static Class<?> serverGamePacketListenerClass;
    private static Class<?> packetFlowClass;

    // ── NMS constructors / methods ─────────────────────────────────────────────
    private static Constructor<?> gameProfileConstructor;
    private static Method setPosMethod;
    private static Method doTickMethod;
    private static Method getPlayerListMethod;

    // ── Previous-position fields (xo/yo/zo) ───────────────────────────────────
    private static Field xoField;
    private static Field yoField;
    private static Field zoField;

    // ── LivingEntity.jumping flag (for swim AI) ────────────────────────────────
    private static Field jumpingField;

    // ── LivingEntity.yHeadRot field (for head AI) ──────────────────────────────
    private static Field yHeadRotField;

    // ── ServerPlayer.connection field ─────────────────────────────────────────
    /** Used to replace the vanilla SGPL with {@link FakeServerGamePacketListenerImpl}. */
    private static Field connectionFieldInPlayer;

    // ── ClientInformation cache ────────────────────────────────────────────────
    private static Object clientInfoDefault;

    // ── First-tick tracking ────────────────────────────────────────────────────
    private static final Set<UUID> firstTickSet =
            Collections.synchronizedSet(new HashSet<>());

    // ── Skin-parts (DATA_PLAYER_MODE_CUSTOMISATION entity metadata) ────────────
    /** Cached {@code EntityDataAccessor<Byte>} for the skin-customisation metadata slot. */
    private static Object skinPartsDataAccessor     = null;
    /** Cached {@code SynchedEntityData.set(EntityDataAccessor, Object)} method. */
    private static Method synchedEntityDataSetMethod = null;
    /** Cached {@code Entity.entityData} field (holds the {@code SynchedEntityData} instance). */
    private static Field  entityDataFieldForSkinParts = null;

    private NmsPlayerSpawner() {}

    // ══════════════════════════════════════════════════════════════════════════
    //  Initialisation
    // ══════════════════════════════════════════════════════════════════════════

    /** Initialise NMS reflection. Safe to call multiple times; subsequent calls are no-ops. */
    public static synchronized void init() {
        if (initialized || failed) return;
        try {
            // ── CraftBukkit package ────────────────────────────────────────────
            String packageName = Bukkit.getServer().getClass().getPackage().getName();
            String ver = packageName.substring(packageName.lastIndexOf('.') + 1);
            String cbPkg = ver.equals("craftbukkit")
                    ? "org.bukkit.craftbukkit"
                    : "org.bukkit.craftbukkit." + ver;
            FppLogger.debug("NmsPlayerSpawner: CraftBukkit package = " + cbPkg);

            Class<?> craftServerClass = Class.forName(cbPkg + ".CraftServer");
            Class<?> craftWorldClass  = Class.forName(cbPkg + ".CraftWorld");
            Class<?> craftPlayerClass = Class.forName(cbPkg + ".entity.CraftPlayer");
            ClassLoader nmsLoader = craftServerClass.getClassLoader();

            // ── CraftBukkit accessors ──────────────────────────────────────────
            craftServerGetServerMethod = craftServerClass.getMethod("getServer");
            craftWorldGetHandleMethod  = craftWorldClass.getMethod("getHandle");
            craftPlayerGetHandleMethod = craftPlayerClass.getMethod("getHandle");

            // ── NMS server classes ─────────────────────────────────────────────
            minecraftServerClass = nmsLoader.loadClass("net.minecraft.server.MinecraftServer");
            try {
                serverLevelClass  = nmsLoader.loadClass("net.minecraft.server.level.ServerLevel");
                serverPlayerClass = nmsLoader.loadClass("net.minecraft.server.level.ServerPlayer");
                FppLogger.debug("NmsPlayerSpawner: using Mojang-mapped NMS names");
            } catch (ClassNotFoundException e) {
                serverLevelClass  = nmsLoader.loadClass("net.minecraft.server.level.WorldServer");
                serverPlayerClass = nmsLoader.loadClass("net.minecraft.server.level.EntityPlayer");
                FppLogger.debug("NmsPlayerSpawner: using Spigot-mapped NMS names");
            }

            // ── Network classes ────────────────────────────────────────────────
            try {
                connectionClass = nmsLoader.loadClass("net.minecraft.network.Connection");
            } catch (ClassNotFoundException e) {
                connectionClass = nmsLoader.loadClass("net.minecraft.network.NetworkManager");
            }
            try {
                commonListenerCookieClass =
                        nmsLoader.loadClass("net.minecraft.server.network.CommonListenerCookie");
            } catch (ClassNotFoundException ignored) {}
            try {
                serverGamePacketListenerClass =
                        nmsLoader.loadClass("net.minecraft.server.network.ServerGamePacketListenerImpl");
            } catch (ClassNotFoundException e) {
                try {
                    serverGamePacketListenerClass =
                            nmsLoader.loadClass("net.minecraft.server.network.PlayerConnection");
                } catch (ClassNotFoundException ignored) {}
            }
            try {
                packetFlowClass = nmsLoader.loadClass("net.minecraft.network.protocol.PacketFlow");
            } catch (ClassNotFoundException ignored) {}

            // ── ClientInformation ──────────────────────────────────────────────
            try {
                clientInformationClass =
                        nmsLoader.loadClass("net.minecraft.server.level.ClientInformation");
                try {
                    clientInfoDefault = clientInformationClass.getMethod("createDefault").invoke(null);
                    FppLogger.debug("NmsPlayerSpawner: ClientInformation.createDefault() cached");
                } catch (Exception ignored) {}
            } catch (ClassNotFoundException ignored) {}

            // ── GameProfile ────────────────────────────────────────────────────
            Class<?> gameProfileClass = Class.forName("com.mojang.authlib.GameProfile");
            gameProfileConstructor = gameProfileClass.getConstructor(UUID.class, String.class);

            // ── PlayerList accessor ────────────────────────────────────────────
            getPlayerListMethod = minecraftServerClass.getMethod("getPlayerList");

            // ── setPos(double, double, double) ────────────────────────────────
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
                setPosMethod = findMethodBySignature(serverPlayerClass, 3,
                        double.class, double.class, double.class);

            // ── doTick() ──────────────────────────────────────────────────────
            doTickMethod = findMethod(serverPlayerClass, "doTick", 0);
            if (doTickMethod == null) doTickMethod = findMethod(serverPlayerClass, "tick", 0);
            if (doTickMethod != null) {
                FppLogger.debug("NmsPlayerSpawner: doTick cached as " + doTickMethod.getName() + "()");
            } else {
                FppLogger.warn("NmsPlayerSpawner: doTick() not found — bots will have no physics");
            }

            // ── xo/yo/zo previous-position fields ─────────────────────────────
            Class<?> entityClass;
            try {
                entityClass = nmsLoader.loadClass("net.minecraft.world.entity.Entity");
            } catch (ClassNotFoundException e) {
                entityClass = serverPlayerClass;
            }
            xoField = findFieldByName(entityClass, "xo");
            yoField = findFieldByName(entityClass, "yo");
            zoField = findFieldByName(entityClass, "zo");
            FppLogger.debug("NmsPlayerSpawner: xo/yo/zo fields "
                    + (xoField != null ? "cached" : "not found"));

            // ── jumping field (LivingEntity) — used by swim AI ─────────────────
            try {
                Class<?> livingEntityClass =
                        nmsLoader.loadClass("net.minecraft.world.entity.LivingEntity");
                jumpingField = findFieldByName(livingEntityClass, "jumping");
            } catch (ClassNotFoundException ignored) {
                // Fallback: walk up from ServerPlayer (covers re-mapped jars)
                jumpingField = findFieldByName(serverPlayerClass, "jumping");
            }
            FppLogger.debug("NmsPlayerSpawner: jumping field "
                    + (jumpingField != null ? "cached" : "not found — swim AI inactive"));

            // ── yHeadRot field (LivingEntity) — used by head AI ───────────────
            // findFieldByName walks the whole superclass chain, so passing
            // serverPlayerClass is enough even though yHeadRot lives in LivingEntity.
            yHeadRotField = findFieldByName(serverPlayerClass, "yHeadRot");
            FppLogger.debug("NmsPlayerSpawner: yHeadRot field "
                    + (yHeadRotField != null ? "cached" : "not found — head AI will rely on setRotation only"));

            // ── ServerPlayer.connection field ─────────────────────────────────
            // Used after placeNewPlayer() to replace the vanilla SGPL with our
            // FakeServerGamePacketListenerImpl (fresh instance, no awaitingPositionFromClient).
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
                    FppLogger.debug("NmsPlayerSpawner: connection field = "
                            + connectionFieldInPlayer.getName());
                } else {
                    FppLogger.warn("NmsPlayerSpawner: ServerPlayer.connection field not found"
                            + " — fake listener injection will be skipped");
                }
            }

            initialized = true;
            FppLogger.info("NmsPlayerSpawner initialised (doTick=" + (doTickMethod != null)
                    + ", connectionField=" + (connectionFieldInPlayer != null) + ")");

            // ── Skin-parts entity metadata (DATA_PLAYER_MODE_CUSTOMISATION) ────
            // Done after initialized=true so forceAllSkinParts() can use the already-
            // cached craftPlayerGetHandleMethod if init() is somehow called twice.
            try {
                Class<?> playerNmsClass;
                try {
                    playerNmsClass = nmsLoader.loadClass("net.minecraft.world.entity.player.Player");
                } catch (ClassNotFoundException ignored) {
                    playerNmsClass = serverPlayerClass;
                }
                // Mojang-mapped field name for the skin-customisation entity data accessor
                Field spField = findFieldByName(playerNmsClass, "DATA_PLAYER_MODE_CUSTOMISATION");
                if (spField != null && java.lang.reflect.Modifier.isStatic(spField.getModifiers())) {
                    spField.setAccessible(true);
                    skinPartsDataAccessor = spField.get(null);
                }

                // SynchedEntityData.set(EntityDataAccessor, Object)
                Class<?> syncDataClass =
                        nmsLoader.loadClass("net.minecraft.network.syncher.SynchedEntityData");
                for (Method m : syncDataClass.getDeclaredMethods()) {
                    if ("set".equals(m.getName()) && m.getParameterCount() == 2) {
                        m.setAccessible(true);
                        synchedEntityDataSetMethod = m;
                        break;
                    }
                }

                // Entity.entityData field (walks full hierarchy via findFieldByName)
                entityDataFieldForSkinParts = findFieldByName(serverPlayerClass, "entityData");

                FppLogger.debug("NmsPlayerSpawner: skin-parts init — accessor="
                        + (skinPartsDataAccessor != null)
                        + " entityData=" + (entityDataFieldForSkinParts != null)
                        + " setMethod=" + (synchedEntityDataSetMethod != null));
            } catch (Exception e) {
                FppLogger.debug("NmsPlayerSpawner: skin-parts init failed (non-fatal): " + e.getMessage());
            }

        } catch (Exception e) {
            failed = true;
            FppLogger.error("NmsPlayerSpawner.init() failed: " + e.getMessage());
        }
    }

    /** Returns {@code true} when NMS spawning is available. */
    public static boolean isAvailable() {
        if (!initialized && !failed) init();
        return initialized;
    }

    // ══════════════════════════════════════════════════════════════════════════
    //  Spawn
    // ══════════════════════════════════════════════════════════════════════════

    /**
     * Spawns a fake {@code ServerPlayer} at the given location (no skin).
     *
     * @return the Bukkit {@link Player} wrapper, or {@code null} on failure
     */
    public static Player spawnFakePlayer(UUID uuid, String name,
                                         World world, double x, double y, double z) {
        return spawnFakePlayer(uuid, name, null, world, x, y, z);
    }

    /**
     * Spawns a fake {@code ServerPlayer} at the given location with an optional skin.
     *
     * @param skin optional skin to inject into the {@code GameProfile} (may be {@code null})
     * @return the Bukkit {@link Player} wrapper, or {@code null} on failure
     */
    public static Player spawnFakePlayer(UUID uuid, String name, SkinProfile skin,
                                         World world, double x, double y, double z) {
        if (!isAvailable()) {
            FppLogger.warn("NmsPlayerSpawner not available — cannot spawn " + name);
            return null;
        }
        try {
            // ── GameProfile ────────────────────────────────────────────────────
            Object gameProfile = gameProfileConstructor.newInstance(uuid, name);
            if (skin != null && skin.isValid()) {
                try {
                    SkinProfileInjector.apply(gameProfile, skin);
                    FppLogger.debug("NmsPlayerSpawner: injected skin for '" + name + "'");
                } catch (Exception e) {
                    FppLogger.warn("NmsPlayerSpawner: skin injection failed: " + e.getMessage());
                }
            }

            Object minecraftServer = craftServerGetServerMethod.invoke(Bukkit.getServer());
            Object serverLevel     = craftWorldGetHandleMethod.invoke(world);
            Object clientInfo      = getClientInformation();

            Object serverPlayer = createServerPlayer(minecraftServer, serverLevel, gameProfile, clientInfo);
            if (serverPlayer == null) {
                FppLogger.warn("NmsPlayerSpawner: failed to create ServerPlayer for " + name);
                return null;
            }

            if (setPosMethod != null) setPosMethod.invoke(serverPlayer, x, y, z);
            initPreviousPosition(serverPlayer, x, y, z);

            // ── Create FakeChannel-backed connection ───────────────────────────
            Object conn = createFakeConnection();
            if (conn == null) {
                FppLogger.warn("NmsPlayerSpawner: failed to create fake connection for " + name);
                return null;
            }

            // ── Place into world ───────────────────────────────────────────────
            // placeNewPlayer() creates a vanilla SGPL, assigns it to serverPlayer.connection,
            // and sends the spawn-position packet → sets awaitingPositionFromClient on SGPL.
            boolean placed = placePlayer(minecraftServer, conn, serverPlayer, gameProfile, clientInfo);
            if (!placed) {
                FppLogger.warn("NmsPlayerSpawner: placeNewPlayer failed for " + name);
                return null;
            }

            // Restore position (placeNewPlayer may teleport to spawn)
            if (setPosMethod != null) setPosMethod.invoke(serverPlayer, x, y, z);
            initPreviousPosition(serverPlayer, x, y, z);

            // ── Replace vanilla SGPL with FakeServerGamePacketListenerImpl ─────
            // The vanilla SGPL has awaitingPositionFromClient set; our fresh fake
            // listener starts with null and its send() is a no-op so it stays null.
            injectFakeListener(minecraftServer, conn, serverPlayer, gameProfile, clientInfo);

            Method getBukkitEntity = serverPlayerClass.getMethod("getBukkitEntity");
            Object entity = getBukkitEntity.invoke(serverPlayer);
            if (entity instanceof Player result) {
                result.setGameMode(org.bukkit.GameMode.SURVIVAL);
                // Force all skin-overlay layers visible (jacket, hat, sleeves, pants).
                // Ensures outer layers show even if ClientInformation.createDefault()
                // returns modelCustomisation = 0 on this server version.
                forceAllSkinParts(result);
                firstTickSet.add(uuid);
                FppLogger.info("NmsPlayerSpawner: spawned " + name + " (" + uuid + ")");
                return result;
            }

            FppLogger.warn("NmsPlayerSpawner: getBukkitEntity did not return a Player for " + name);
            return null;

        } catch (Exception e) {
            FppLogger.error("NmsPlayerSpawner.spawnFakePlayer failed for " + name + ": " + e.getMessage());
            FppLogger.debug(Arrays.toString(e.getStackTrace()));
            return null;
        }
    }

    // ══════════════════════════════════════════════════════════════════════════
    //  Physics tick
    // ══════════════════════════════════════════════════════════════════════════

    /**
     * Drives one game-tick of physics for the given bot via {@code ServerPlayer.doTick()}.
     *
     * <p>Since {@code handle.connection} is now a {@link FakeServerGamePacketListenerImpl}
     * with {@code awaitingPositionFromClient == null}, the server's own
     * {@code connection.tick()} (called by PlayerList) does not snap the bot back.
     * We call {@code doTick()} to run gravity, collision, fluid drag, etc.
     *
     * <p>On the first tick we pin {@code xo/yo/zo} to prevent phantom displacement,
     * and restore the position afterwards in case a plugin (ClearFog, Multiverse)
     * moved the bot during its first tick.
     */
    public static void tickPhysics(Player bot) {
        if (!initialized || doTickMethod == null || craftPlayerGetHandleMethod == null) return;
        if (!bot.isOnline() || !bot.isValid() || bot.isDead()) return;
        try {
            Object nmsPlayer = craftPlayerGetHandleMethod.invoke(bot);

            if (firstTickSet.remove(bot.getUniqueId())) {
                // ── First tick ─────────────────────────────────────────────────
                org.bukkit.Location loc = bot.getLocation();
                double x = loc.getX(), y = loc.getY(), z = loc.getZ();

                initPreviousPosition(nmsPlayer, x, y, z);
                doTickMethod.invoke(nmsPlayer);

                // Restore in case a plugin moved the bot on its first tick
                if (setPosMethod != null) setPosMethod.invoke(nmsPlayer, x, y, z);
                initPreviousPosition(nmsPlayer, x, y, z);

            } else {
                // ── Normal tick ────────────────────────────────────────────────
                doTickMethod.invoke(nmsPlayer);
            }

        } catch (Exception e) {
            FppLogger.debug("NmsPlayerSpawner.tickPhysics failed for "
                    + bot.getName() + ": " + e.getMessage());
        }
    }

    // ══════════════════════════════════════════════════════════════════════════
    //  Position helper
    // ══════════════════════════════════════════════════════════════════════════

    /**
     * Directly sets the NMS entity position via {@code ServerPlayer.setPos(x, y, z)}.
     * Used by head-AI and teleport commands to reposition bots server-side.
     */
    public static void setPosition(Player bot, double x, double y, double z) {
        if (!initialized || setPosMethod == null || craftPlayerGetHandleMethod == null) return;
        try {
            Object nmsPlayer = craftPlayerGetHandleMethod.invoke(bot);
            setPosMethod.invoke(nmsPlayer, x, y, z);
        } catch (Exception e) {
            FppLogger.debug("NmsPlayerSpawner.setPosition failed: " + e.getMessage());
        }
    }

    /**
     * Sets the NMS {@code LivingEntity.jumping} flag on the given bot.
     *
     * <p>When {@code true} the entity's next physics tick will apply the "swim up"
     * impulse (vanilla: +0.04 m/s per tick in water), exactly replicating a player
     * holding the spacebar / jump key while submerged.  The flag is transient — NMS
     * clears it after each tick unless we re-apply it, so this must be called every
     * tick the bot should continue swimming.
     *
     * @param bot     the bot whose jump state should be modified
     * @param jumping {@code true} to swim/jump, {@code false} to stop
     */
    public static void setJumping(Player bot, boolean jumping) {
        if (!initialized || jumpingField == null || craftPlayerGetHandleMethod == null) return;
        try {
            Object nmsPlayer = craftPlayerGetHandleMethod.invoke(bot);
            jumpingField.setBoolean(nmsPlayer, jumping);
        } catch (Exception e) {
            FppLogger.debug("NmsPlayerSpawner.setJumping failed: " + e.getMessage());
        }
    }

    /**
     * Sets the NMS {@code LivingEntity.yHeadRot} field on the given bot so that
     * the head rotates independently of the body yaw.  Called every tick by the
     * head-AI loop in {@code FakePlayerManager} after a smooth lerp step.
     *
     * @param bot the bot whose head yaw should be updated
     * @param yaw the target head yaw in degrees (same range as Bukkit yaw)
     */
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

    // ══════════════════════════════════════════════════════════════════════════
    //  Remove
    // ══════════════════════════════════════════════════════════════════════════

    /**
     * Removes a fake player from the server by kicking it, which triggers the
     * normal server-side despawn and cleanup path.
     *
     * <p>Uses {@code Component.empty()} as the kick reason so that
     * {@code FakePlayerKickListener} recognises this as an intentional removal
     * and does not cancel the event (non-empty reasons are cancelled to prevent
     * anti-idle/anti-AFK systems from kicking bots unintentionally).
     */
    public static void removeFakePlayer(Player player) {
        if (player == null) return;
        try {
            firstTickSet.remove(player.getUniqueId());
            if (player.isOnline()) {
                // Empty reason bypasses FakePlayerKickListener's "cancel all kicks" guard.
                player.kick(net.kyori.adventure.text.Component.empty());
            }
        } catch (Exception e) {
            FppLogger.debug("NmsPlayerSpawner.removeFakePlayer failed for "
                    + player.getName() + ": " + e.getMessage());
        }
    }

    // ══════════════════════════════════════════════════════════════════════════
    //  Skin-parts helper
    // ══════════════════════════════════════════════════════════════════════════

    /**
     * Forces the {@code DATA_PLAYER_MODE_CUSTOMISATION} entity-metadata byte to
     * {@code 0x7F} (all seven skin-overlay layers visible: cape, jacket, left/right
     * sleeve, left/right pants, hat) on the given bot entity.
     *
     * <p>This is a no-op when the reflection setup failed at startup (graceful
     * degradation: bots will still spawn, just potentially without the outer
     * skin layers).
     *
     * <p>Called:
     * <ul>
     *   <li>Right after {@code placeNewPlayer()} in {@link #spawnFakePlayer} to set
     *       the correct value before the initial entity-metadata packet is sent.</li>
     *   <li>After {@code Player.setPlayerProfile()} in
     *       {@link FakePlayerBody#applyPaperSkin} because Paper's profile-refresh
     *       may re-send entity metadata with default values.</li>
     * </ul>
     */
    public static void forceAllSkinParts(Player bot) {
        if (!initialized
                || skinPartsDataAccessor == null
                || entityDataFieldForSkinParts == null
                || synchedEntityDataSetMethod == null
                || craftPlayerGetHandleMethod == null) return;
        try {
            Object nmsPlayer = craftPlayerGetHandleMethod.invoke(bot);
            Object entityData = entityDataFieldForSkinParts.get(nmsPlayer);
            // 0x7F = 0111 1111 — all seven skin-overlay bits set
            synchedEntityDataSetMethod.invoke(entityData, skinPartsDataAccessor, (byte) 0x7F);
            FppLogger.debug("NmsPlayerSpawner: skin-parts forced to 0x7F for " + bot.getName());
        } catch (Exception e) {
            FppLogger.debug("NmsPlayerSpawner.forceAllSkinParts failed for "
                    + bot.getName() + ": " + e.getMessage());
        }
    }

    // ══════════════════════════════════════════════════════════════════════════
    //  Internal helpers
    // ══════════════════════════════════════════════════════════════════════════

    /**
     * Creates a fresh {@link FakeServerGamePacketListenerImpl} and assigns it to
     * {@code serverPlayer.connection}, replacing the vanilla SGPL created by
     * {@code placeNewPlayer()} (which has {@code awaitingPositionFromClient} set).
     *
     * <p>The fresh instance starts with {@code awaitingPositionFromClient == null}
     * and its {@code send()} override discards all packets, so that field can never
     * be populated.  This means the server's {@code connection.tick()} runs harmlessly
     * and bots have proper physics instead of floating.
     */
    private static void injectFakeListener(Object minecraftServer, Object conn,
                                           Object serverPlayer,
                                           Object gameProfile, Object clientInfo) {
        if (connectionFieldInPlayer == null) {
            FppLogger.warn("NmsPlayerSpawner: cannot inject fake listener — connection field not found");
            return;
        }
        try {
            Object cookie = createCookieDynamic(gameProfile, clientInfo);
            if (cookie == null) {
                FppLogger.warn("NmsPlayerSpawner: cannot inject fake listener — cookie creation failed");
                return;
            }

            FakeServerGamePacketListenerImpl fakeListener =
                    FakeServerGamePacketListenerImpl.create(
                            minecraftServer, conn, serverPlayer, cookie);

            connectionFieldInPlayer.set(serverPlayer, fakeListener);
            FppLogger.debug("NmsPlayerSpawner: FakeServerGamePacketListenerImpl injected");

        } catch (Exception e) {
            FppLogger.warn("NmsPlayerSpawner: fake listener injection failed: " + e.getMessage());
            FppLogger.debug(Arrays.toString(e.getStackTrace()));
        }
    }

    /**
     * Creates a {@link FakeChannel}-backed NMS {@code Connection} via reflection.
     * The channel silently discards all writes.
     */
    private static Object createFakeConnection() {
        if (connectionClass == null || packetFlowClass == null) {
            FppLogger.debug("NmsPlayerSpawner: connection classes unavailable");
            return null;
        }
        try {
            FakeChannel fakeChannel = new FakeChannel(InetAddress.getLoopbackAddress());

            Object serverbound = packetFlowClass.getEnumConstants()[0];
            Constructor<?> connCtor = connectionClass.getDeclaredConstructor(packetFlowClass);
            connCtor.setAccessible(true);
            Object conn = connCtor.newInstance(serverbound);

            // Inject FakeChannel into Connection.channel field
            for (Field f : getAllDeclaredFields(connectionClass)) {
                String typeName = f.getType().getName();
                if (typeName.equals("io.netty.channel.Channel")
                        || f.getType().getSimpleName().equals("Channel")) {
                    f.setAccessible(true);
                    f.set(conn, fakeChannel);
                    FppLogger.debug("NmsPlayerSpawner: FakeChannel injected into Connection." + f.getName());
                    break;
                }
            }

            // Inject loopback address into Connection.address field
            for (Field f : getAllDeclaredFields(connectionClass)) {
                if (java.net.SocketAddress.class.isAssignableFrom(f.getType())) {
                    try {
                        f.setAccessible(true);
                        if (f.get(conn) == null) {
                            f.set(conn, new InetSocketAddress(InetAddress.getLoopbackAddress(), 25565));
                        }
                    } catch (Exception ignored) {}
                    break;
                }
            }

            return conn;

        } catch (Exception e) {
            FppLogger.warn("NmsPlayerSpawner.createFakeConnection failed: " + e.getMessage());
            return null;
        }
    }

    /** Returns a cached or freshly created {@code ClientInformation} instance. */
    private static Object getClientInformation() {
        if (clientInfoDefault != null) return clientInfoDefault;
        if (clientInformationClass == null) return null;
        try {
            return clientInformationClass.getMethod("createDefault").invoke(null);
        } catch (Exception e) {
            return null;
        }
    }

    /** Creates a {@code ServerPlayer} using the best-matching available constructor. */
    private static Object createServerPlayer(Object minecraftServer, Object serverLevel,
                                             Object gameProfile, Object clientInfo) {
        // 4-arg: ServerPlayer(MinecraftServer, ServerLevel, GameProfile, ClientInformation)
        if (clientInfo != null && clientInformationClass != null) {
            try {
                Constructor<?> ctor = serverPlayerClass.getConstructor(
                        minecraftServerClass, serverLevelClass,
                        gameProfile.getClass(), clientInformationClass);
                return ctor.newInstance(minecraftServer, serverLevel, gameProfile, clientInfo);
            } catch (NoSuchMethodException ignored) {
            } catch (Exception e) {
                FppLogger.debug("4-arg ServerPlayer ctor failed: " + e.getMessage());
            }
        }
        // 3-arg fallback: ServerPlayer(MinecraftServer, ServerLevel, GameProfile)
        try {
            Constructor<?> ctor = serverPlayerClass.getConstructor(
                    minecraftServerClass, serverLevelClass, gameProfile.getClass());
            return ctor.newInstance(minecraftServer, serverLevel, gameProfile);
        } catch (Exception e) {
            FppLogger.error("NmsPlayerSpawner: no ServerPlayer constructor matched: " + e.getMessage());
            return null;
        }
    }

    /**
     * Adds the bot to the world via {@code PlayerList.placeNewPlayer(Connection, ServerPlayer, Cookie)}.
     *
     * @return {@code true} if the player was successfully placed
     */
    private static boolean placePlayer(Object minecraftServer, Object conn,
                                       Object serverPlayer, Object gameProfile, Object clientInfo) {
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
            FppLogger.warn("NmsPlayerSpawner.placePlayer failed: " + e.getMessage());
        }
        return false;
    }

    /**
     * Dynamically creates a {@code CommonListenerCookie} by trying the static factory
     * first, then constructors in ascending order of parameter count.
     */
    private static Object createCookieDynamic(Object gameProfile, Object clientInfo) {
        if (commonListenerCookieClass == null) return null;

        // Try static factory: CommonListenerCookie.createInitial(GameProfile, boolean)
        try {
            Method factory = commonListenerCookieClass.getMethod("createInitial",
                    gameProfile.getClass(), boolean.class);
            return factory.invoke(null, gameProfile, false);
        } catch (Exception ignored) {}

        // Fallback: try constructors
        for (Constructor<?> c : commonListenerCookieClass.getDeclaredConstructors()) {
            c.setAccessible(true);
            Class<?>[] p = c.getParameterTypes();
            if (p.length > 0
                    && p[p.length - 1].getSimpleName().contains("DefaultConstructorMarker")) {
                continue;
            }
            try {
                Object result = switch (p.length) {
                    case 1 -> c.newInstance(gameProfile);
                    case 2 -> c.newInstance(gameProfile, 0);
                    case 3 -> c.newInstance(gameProfile, 0, clientInfo);
                    case 4 -> c.newInstance(gameProfile, 0, clientInfo, false);
                    case 5 -> c.newInstance(gameProfile, 0, clientInfo, false, false);
                    case 7 -> c.newInstance(gameProfile, 0, clientInfo, false,
                            null, Collections.emptySet(), null);
                    default -> null;
                };
                if (result != null) return result;
            } catch (Exception ignored) {}
        }
        FppLogger.debug("NmsPlayerSpawner: no CommonListenerCookie constructor succeeded");
        return null;
    }

    /** Sets the entity's xo/yo/zo fields to suppress first-tick phantom movement. */
    private static void initPreviousPosition(Object nmsPlayer, double x, double y, double z) {
        try {
            if (xoField != null) xoField.setDouble(nmsPlayer, x);
            if (yoField != null) yoField.setDouble(nmsPlayer, y);
            if (zoField != null) zoField.setDouble(nmsPlayer, z);
        } catch (Exception ignored) {}
    }

    // ══════════════════════════════════════════════════════════════════════════
    //  Reflection utilities
    // ══════════════════════════════════════════════════════════════════════════

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

    private static Method findMethodBySignature(Class<?> clazz, int paramCount, Class<?>... paramTypes) {
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

