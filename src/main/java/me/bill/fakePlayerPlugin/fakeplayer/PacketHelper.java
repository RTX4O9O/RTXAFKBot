package me.bill.fakePlayerPlugin.fakeplayer;

import me.bill.fakePlayerPlugin.config.Config;
import me.bill.fakePlayerPlugin.util.FppLogger;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.minimessage.MiniMessage;
import net.kyori.adventure.text.serializer.plain.PlainTextComponentSerializer;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.entity.Player;

import java.lang.reflect.Constructor;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.*;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Sends NMS packets via reflection — zero NMS imports, compiles against paper-api only.
 *
 * <p>Hot-path methods ({@link #sendTabListAdd}, {@link #sendTabListRemove}) use
 * {@link #ensureReady()} which bypasses the {@code synchronized} block after
 * first initialisation. The {@code ServerPlayer.connection} field and
 * {@code connection.send()} method are cached on first use to eliminate
 * per-call reflection scanning.
 */
@SuppressWarnings("unused") // Several methods are public API used by other subsystems
public final class PacketHelper {

    private PacketHelper() {}

    private static volatile boolean ready  = false;
    private static volatile boolean broken = false;

    private static Class<?> craftPlayerClass;
    private static Class<?> gameProfileClass;
    private static Class<?> playerInfoUpdatePacketClass;
    private static Class<?> playerInfoUpdateActionClass;
    private static Class<?> playerInfoUpdateEntryClass;
    private static Class<?> playerInfoRemovePacketClass;
    private static Class<?> addEntityPacketClass;
    private static Class<?> removeEntitiesPacketClass;
    private static Class<?> moveEntityRotPacketClass;
    private static Class<?> rotateHeadPacketClass;
    private static Class<?> vec3Class;
    private static Class<?> entityTypeClass;
    private static Object   vec3Zero;
    private static Object   gameTypeSurvival;
    private static Object   entityTypePlayer;

    private static Constructor<?> gameProfileCtor;
    private static Method         componentLiteral;
    private static Method         craftPlayerGetHandle;
    private static Constructor<?> playerInfoUpdateCtor;

    /** Paper's PaperAdventure.asVanilla(Component) — converts Adventure → NMS Component. */
    private static Method paperAdventureAsVanilla;

    /** Cached {@code ServerPlayer.connection} Field — resolved on first {@link #sendPacket} call. */
    private static volatile Field  cachedConnectionField;
    /** Cached {@code connection.send(Packet)} Method — resolved on first {@link #sendPacket} call. */
    private static volatile Method cachedSendMethod;

    // ── Initialisation ────────────────────────────────────────────────────────

    @SuppressWarnings({"unchecked", "rawtypes"})
    private static synchronized void init() {
        if (ready || broken) return;
        try {
            craftPlayerClass = getCraftPlayerClass();
            for (Method m : craftPlayerClass.getDeclaredMethods()) {
                if (m.getName().equals("getHandle") && m.getParameterCount() == 0) {
                    m.setAccessible(true);
                    craftPlayerGetHandle = m;
                    break;
                }
            }

            ClassLoader nmsLoader = findNmsClassLoader();
            if (nmsLoader == null)
                throw new IllegalStateException("Cannot find NMS classloader — join the server first.");

            gameProfileClass = nmsLoader.loadClass("com.mojang.authlib.GameProfile");
            for (Constructor<?> c : gameProfileClass.getDeclaredConstructors()) {
                Class<?>[] pt = c.getParameterTypes();
                if (pt.length == 2 && pt[0] == UUID.class && pt[1] == String.class) {
                    c.setAccessible(true);
                    gameProfileCtor = c;
                    break;
                }
            }

            String pkg = "net.minecraft.network.protocol.game.";
            playerInfoUpdatePacketClass = nmsLoader.loadClass(pkg + "ClientboundPlayerInfoUpdatePacket");
            playerInfoUpdateActionClass = nmsLoader.loadClass(pkg + "ClientboundPlayerInfoUpdatePacket$Action");
            playerInfoUpdateEntryClass  = nmsLoader.loadClass(pkg + "ClientboundPlayerInfoUpdatePacket$Entry");
            playerInfoRemovePacketClass = nmsLoader.loadClass(pkg + "ClientboundPlayerInfoRemovePacket");
            addEntityPacketClass        = nmsLoader.loadClass(pkg + "ClientboundAddEntityPacket");
            removeEntitiesPacketClass   = nmsLoader.loadClass(pkg + "ClientboundRemoveEntitiesPacket");
            moveEntityRotPacketClass    = nmsLoader.loadClass(pkg + "ClientboundMoveEntityPacket$Rot");
            rotateHeadPacketClass       = nmsLoader.loadClass(pkg + "ClientboundRotateHeadPacket");

            Class<?> gameTypeClass = nmsLoader.loadClass("net.minecraft.world.level.GameType");
            gameTypeSurvival = Enum.valueOf((Class<? extends Enum>) gameTypeClass, "SURVIVAL");

            Class<?> componentCls = nmsLoader.loadClass("net.minecraft.network.chat.Component");
            for (Method m : componentCls.getDeclaredMethods()) {
                if (m.getName().equals("literal") && m.getParameterCount() == 1
                        && m.getParameterTypes()[0] == String.class) {
                    m.setAccessible(true);
                    componentLiteral = m;
                    break;
                }
            }

            vec3Class = nmsLoader.loadClass("net.minecraft.world.phys.Vec3");
            vec3Zero  = scanStaticField(vec3Class, "ZERO");

            entityTypeClass  = nmsLoader.loadClass("net.minecraft.world.entity.EntityType");
            entityTypePlayer = scanStaticField(entityTypeClass, "PLAYER");

            playerInfoUpdateCtor = findPlayerInfoUpdateCtor();
            if (playerInfoUpdateCtor == null)
                throw new IllegalStateException("Cannot find ClientboundPlayerInfoUpdatePacket constructor.");

            // Resolve PaperAdventure.asVanilla(Component) — Paper's official Adventure→NMS bridge
            try {
                Class<?> paperAdventure = Class.forName("io.papermc.paper.adventure.PaperAdventure");
                paperAdventureAsVanilla = paperAdventure.getDeclaredMethod("asVanilla", Component.class);
                paperAdventureAsVanilla.setAccessible(true);
                Config.debug("PaperAdventure.asVanilla found — colored tablist names supported.");
            } catch (Exception ex) {
                Config.debug("PaperAdventure.asVanilla not found, will fall back to literal: " + ex.getMessage());
            }

            Config.debug("PacketHelper ready.");
            ready = true;
        } catch (Exception e) {
            broken = true;
            FppLogger.warn("PacketHelper init failed: " + e.getMessage());
            if (Config.isDebug()) FppLogger.warn("  → " + e);
        }
    }

    private static ClassLoader findNmsClassLoader() {
        return NmsHelper.findNmsClassLoader();
    }

    private static Constructor<?> findPlayerInfoUpdateCtor() {
        for (Constructor<?> c : playerInfoUpdatePacketClass.getDeclaredConstructors()) {
            Class<?>[] p = c.getParameterTypes();
            if (p.length == 2 && p[0] == EnumSet.class) {
                c.setAccessible(true);
                Config.debug("PlayerInfoUpdatePacket ctor: second param = " + p[1].getName());
                return c;
            }
        }
        return null;
    }

    /**
     * Fast non-synchronized ready check.
     * Calls {@link #init()} only when neither {@code ready} nor {@code broken} is set.
     */
    private static boolean ensureReady() {
        if (ready)  return true;
        if (broken) return false;
        init();
        return ready;
    }

    // ── Public API ────────────────────────────────────────────────────────────

    /**
     * Converts MiniMessage hex color tags to Minecraft legacy hex color codes for tab list display name.
     * Example: <#0079FF>text</#0079FF> → §x§0§0§7§9§F§Ftext§r
     */
    public static String convertHexColors(String input) {
        Pattern open = Pattern.compile("<#([A-Fa-f0-9]{6})>");
        Matcher m = open.matcher(input);
        StringBuffer sb = new StringBuffer();
        while (m.find()) {
            String hex = m.group(1);
            StringBuilder color = new StringBuilder("§x");
            for (char c : hex.toCharArray()) color.append('§').append(c);
            m.appendReplacement(sb, color.toString());
        }
        m.appendTail(sb);
        String result = sb.toString();
        // Replace closing tags
        result = result.replaceAll("</#([A-Fa-f0-9]{6})>", "§r");
        return result;
    }

    public static void sendTabListAdd(Player receiver, FakePlayer fp) {
        if (!ensureReady()) return;
        try {
            Object nms     = getHandle(receiver);
            Object profile = gameProfileCtor != null
                    ? gameProfileCtor.newInstance(fp.getUuid(), fp.getName())
                    : gameProfileClass.getDeclaredConstructors()[0].newInstance(fp.getUuid(), fp.getName());

            // Parse display name: MiniMessage → Adventure Component → NMS Component
            String displayNameMini = fp.getDisplayName();
            Component adventureComponent = MiniMessage.miniMessage().deserialize(displayNameMini);
            Object displayName = adventureToNms(adventureComponent);

            Object entry   = buildEntry(fp.getUuid(), profile, displayName);
            Object actions = buildActionSet();

            Object secondArg;
            Class<?> secondParamType = playerInfoUpdateCtor.getParameterTypes()[1];
            if (secondParamType == playerInfoUpdateEntryClass) {
                secondArg = entry;
            } else if (secondParamType.isArray()) {
                Object arr = java.lang.reflect.Array.newInstance(secondParamType.getComponentType(), 1);
                java.lang.reflect.Array.set(arr, 0, entry);
                secondArg = arr;
            } else {
                secondArg = List.of(entry);
            }

            sendPacket(nms, playerInfoUpdateCtor.newInstance(actions, secondArg));
            Config.debug("Tab ADD → " + receiver.getName() + " for " + fp.getName());
        } catch (Exception e) {
            FppLogger.error("sendTabListAdd failed: " + e.getMessage());
            if (Config.isDebug()) FppLogger.warn("  → " + e);
        }
    }

    public static void sendTabListRemove(Player receiver, FakePlayer fp) {
        if (!ensureReady()) return;
        try {
            Object nms = getHandle(receiver);
            Constructor<?> ctor = getConstructor(playerInfoRemovePacketClass, List.class);
            if (ctor == null) {
                ctor = playerInfoRemovePacketClass.getDeclaredConstructors()[0];
                ctor.setAccessible(true);
            }
            sendPacket(nms, ctor.newInstance(List.of(fp.getUuid())));
            Config.debug("Tab REMOVE → " + receiver.getName() + " for " + fp.getName());
        } catch (Exception e) {
            FppLogger.error("sendTabListRemove failed: " + e.getMessage());
        }
    }

    /**
     * Sends only the {@code UPDATE_DISPLAY_NAME} action for {@code fp} to {@code receiver}.
     * Lighter than {@link #sendTabListAdd} — use in the periodic refresh loop to override
     * TAB plugin resets without re-adding the player entry from scratch.
     */
    @SuppressWarnings({"unchecked", "rawtypes"})
    public static void sendTabListDisplayNameUpdate(Player receiver, FakePlayer fp) {
        if (!ensureReady()) return;
        try {
            Object nms = getHandle(receiver);
            Object profile = gameProfileCtor != null
                    ? gameProfileCtor.newInstance(fp.getUuid(), fp.getName())
                    : gameProfileClass.getDeclaredConstructors()[0].newInstance(fp.getUuid(), fp.getName());

            Component adventureComponent = MiniMessage.miniMessage().deserialize(fp.getDisplayName());
            Object displayName = adventureToNms(adventureComponent);

            Object entry = buildEntry(fp.getUuid(), profile, displayName);

            // Only UPDATE_DISPLAY_NAME action
            Class<? extends Enum> e = rawEnum(playerInfoUpdateActionClass);
            Object actions = EnumSet.of(Enum.valueOf(e, "UPDATE_DISPLAY_NAME"));

            Object secondArg;
            Class<?> secondParamType = playerInfoUpdateCtor.getParameterTypes()[1];
            if (secondParamType == playerInfoUpdateEntryClass) {
                secondArg = entry;
            } else if (secondParamType.isArray()) {
                Object arr = java.lang.reflect.Array.newInstance(secondParamType.getComponentType(), 1);
                java.lang.reflect.Array.set(arr, 0, entry);
                secondArg = arr;
            } else {
                secondArg = List.of(entry);
            }
            sendPacket(nms, playerInfoUpdateCtor.newInstance(actions, secondArg));
        } catch (Exception e) {
            // Silent — this is a background refresh, don't spam logs
        }
    }

    public static void spawnFakePlayer(Player receiver, FakePlayer fp, Location loc) {
        if (!ensureReady()) return;
        try {
            Object nms = getHandle(receiver);
            Constructor<?> ctor = addEntityPacketClass.getConstructor(
                    int.class, UUID.class,
                    double.class, double.class, double.class,
                    float.class, float.class,
                    entityTypeClass, int.class, vec3Class, double.class);
            sendPacket(nms, ctor.newInstance(
                    fp.getEntityId(), fp.getUuid(),
                    loc.getX(), loc.getY(), loc.getZ(),
                    loc.getPitch(), loc.getYaw(),
                    entityTypePlayer, 0, vec3Zero, 0.0));
            Config.debug("Spawn entity → " + receiver.getName() + " for " + fp.getName());
        } catch (Exception e) {
            FppLogger.error("spawnFakePlayer failed: " + e.getMessage());
            if (Config.isDebug()) FppLogger.warn("  → " + e);
        }
    }

    public static void despawnFakePlayer(Player receiver, FakePlayer fp) {
        if (!ensureReady()) return;
        try {
            Object nms = getHandle(receiver);
            Constructor<?> ctor = removeEntitiesPacketClass.getConstructor(int[].class);
            sendPacket(nms, ctor.newInstance((Object) new int[]{fp.getEntityId()}));
            Config.debug("Despawn entity → " + receiver.getName() + " for " + fp.getName());
        } catch (Exception e) {
            FppLogger.error("despawnFakePlayer failed: " + e.getMessage());
        }
    }

    public static void sendTeleport(Player receiver, FakePlayer fp, Location loc) {
        if (!ensureReady()) return;
        try {
            Object nms = getHandle(receiver);
            ClassLoader cl = nms.getClass().getClassLoader();
            String[] candidates = {
                "net.minecraft.network.protocol.game.ClientboundEntityPositionSyncPacket",
                "net.minecraft.network.protocol.game.ClientboundTeleportEntityPacket"
            };
            for (String className : candidates) {
                try {
                    Class<?> pktClass = cl.loadClass(className);
                    for (Constructor<?> c : pktClass.getDeclaredConstructors()) {
                        c.setAccessible(true);
                        Class<?>[] pt = c.getParameterTypes();
                        if (pt.length == 7 && pt[0] == int.class && pt[1] == double.class) {
                            sendPacket(nms, c.newInstance(fp.getEntityId(),
                                    loc.getX(), loc.getY(), loc.getZ(),
                                    loc.getYaw(), loc.getPitch(), true));
                            return;
                        }
                    }
                } catch (ClassNotFoundException ignored) {}
            }
        } catch (Exception ignored) {
            // Teleport failures are non-critical
        }
    }

    public static void sendHurtAnimation(Player receiver, FakePlayer fp) {
        if (!ensureReady()) return;
        try {
            Object nms = getHandle(receiver);
            ClassLoader cl = nms.getClass().getClassLoader();
            Class<?> animClass = cl.loadClass("net.minecraft.network.protocol.game.ClientboundAnimatePacket");
            for (Constructor<?> c : animClass.getDeclaredConstructors()) {
                c.setAccessible(true);
                Class<?>[] pt = c.getParameterTypes();
                if (pt.length == 2 && pt[0] == int.class && pt[1] == int.class) {
                    sendPacket(nms, c.newInstance(fp.getEntityId(), 1));
                    return;
                }
            }
        } catch (Exception ignored) {}
    }

    public static void sendRotation(Player receiver, FakePlayer fp, float yaw, float pitch, float headYaw) {
        if (!ensureReady() || moveEntityRotPacketClass == null || rotateHeadPacketClass == null) return;
        try {
            Object nms      = getHandle(receiver);
            int    entityId = fp.getEntityId();
            if (entityId == -1) return;

            byte encYaw   = angleToByte(yaw);
            byte encPitch = angleToByte(pitch);
            byte encHead  = angleToByte(headYaw);

            for (Constructor<?> c : moveEntityRotPacketClass.getDeclaredConstructors()) {
                Class<?>[] p = c.getParameterTypes();
                if (p.length == 4 && p[0] == int.class && p[1] == byte.class) {
                    c.setAccessible(true);
                    sendPacket(nms, c.newInstance(entityId, encYaw, encPitch, true));
                    break;
                }
            }
            for (Constructor<?> c : rotateHeadPacketClass.getDeclaredConstructors()) {
                c.setAccessible(true);
                Class<?>[] p = c.getParameterTypes();
                if (p.length == 2 && p[0] == int.class) {
                    sendPacket(nms, c.newInstance(entityId, encHead));
                } else if (p.length == 2 && fp.getPhysicsEntity() != null) {
                    Object nmsEntity = fp.getPhysicsEntity().getClass()
                            .getMethod("getHandle").invoke(fp.getPhysicsEntity());
                    sendPacket(nms, c.newInstance(nmsEntity, encHead));
                }
                break;
            }
        } catch (Exception e) {
            Config.debug("sendRotation failed: " + e.getMessage());
        }
    }

    // ── Reflection helpers ────────────────────────────────────────────────────

    private static Object getHandle(Player player) throws Exception {
        if (craftPlayerGetHandle != null)
            return craftPlayerGetHandle.invoke(craftPlayerClass.cast(player));
        for (Method m : craftPlayerClass.getDeclaredMethods()) {
            if (m.getName().equals("getHandle") && m.getParameterCount() == 0) {
                m.setAccessible(true);
                craftPlayerGetHandle = m;
                return m.invoke(craftPlayerClass.cast(player));
            }
        }
        throw new NoSuchMethodException("CraftPlayer.getHandle()");
    }

    /**
     * Sends a packet via the cached {@code ServerPlayer.connection.send()} path.
     * Both the field and the method are resolved once and reused on all subsequent calls.
     */
    private static void sendPacket(Object serverPlayer, Object packet) throws Exception {
        if (cachedConnectionField == null) {
            Field f = findFieldInHierarchy(serverPlayer.getClass(), "connection");
            if (f == null) throw new IllegalStateException("ServerPlayer.connection field not found");
            f.setAccessible(true);
            cachedConnectionField = f;
        }
        Object conn = cachedConnectionField.get(serverPlayer);
        if (conn == null) throw new IllegalStateException("ServerPlayer.connection is null");

        if (cachedSendMethod == null) {
            for (Method m : conn.getClass().getMethods()) {
                if (m.getName().equals("send") && m.getParameterCount() == 1) {
                    m.setAccessible(true);
                    cachedSendMethod = m;
                    break;
                }
            }
            if (cachedSendMethod == null)
                throw new IllegalStateException("connection.send(Packet) not found");
        }
        cachedSendMethod.invoke(conn, packet);
    }

    /** Walks the class hierarchy to find a declared field by name. */
    private static Field findFieldInHierarchy(Class<?> clazz, String name) {
        for (Class<?> c = clazz; c != null && c != Object.class; c = c.getSuperclass()) {
            try { return c.getDeclaredField(name); }
            catch (NoSuchFieldException ignored) {}
        }
        return null;
    }

    /** Scans declared fields for a static field — bypasses Paper's reflection rewriter. */
    private static Object scanStaticField(Class<?> clazz, String name) throws Exception {
        for (Class<?> c = clazz; c != null && c != Object.class; c = c.getSuperclass()) {
            for (Field f : c.getDeclaredFields()) {
                if (f.getName().equals(name)) {
                    f.setAccessible(true);
                    return f.get(null);
                }
            }
        }
        throw new NoSuchFieldException(name + " not found in " + clazz.getSimpleName());
    }

    private static Constructor<?> getConstructor(Class<?> clazz, Class<?>... params) {
        try {
            Constructor<?> c = clazz.getDeclaredConstructor(params);
            c.setAccessible(true);
            return c;
        } catch (NoSuchMethodException ignored) { return null; }
    }

    @SuppressWarnings({"unchecked", "rawtypes"})
    private static Class<? extends Enum> rawEnum(Class<?> c) { return (Class<? extends Enum>) c; }

    private static Class<?> getCraftPlayerClass() throws ClassNotFoundException {
        try { return Class.forName("org.bukkit.craftbukkit.entity.CraftPlayer"); }
        catch (ClassNotFoundException ignored) {}
        // Versioned package fallback (pre-1.21 builds)
        String pkg = Bukkit.getServer().getClass().getPackage().getName();
        String[] parts = pkg.split("\\.");
        String version = parts.length >= 4 ? parts[3] + "." : "";
        return Class.forName("org.bukkit.craftbukkit." + version + "entity.CraftPlayer");
    }

    private static byte angleToByte(float degrees) {
        return (byte) Math.floor(degrees * 256f / 360f);
    }

    // ── Packet builders ───────────────────────────────────────────────────────

    /**
     * Converts an Adventure {@link Component} to an NMS {@code net.minecraft.network.chat.Component}.
     * Uses {@code PaperAdventure.asVanilla} (Paper's official bridge) as the primary strategy,
     * falling back to plain-text literal if not available.
     */
    private static Object adventureToNms(Component component) {
        if (paperAdventureAsVanilla != null) {
            try {
                return paperAdventureAsVanilla.invoke(null, component);
            } catch (Exception e) {
                Config.debug("PaperAdventure.asVanilla failed: " + e.getMessage());
            }
        }
        // Fallback: plain text (no color) but at least avoids crashes
        if (componentLiteral != null) {
            try {
                String plain = PlainTextComponentSerializer.plainText().serialize(component);
                return componentLiteral.invoke(null, plain);
            } catch (Exception e) {
                Config.debug("componentLiteral fallback failed: " + e.getMessage());
            }
        }
        return null;
    }

    @SuppressWarnings({"unchecked", "rawtypes"})
    private static Object buildActionSet() {
        Class<? extends Enum> e = rawEnum(playerInfoUpdateActionClass);
        return EnumSet.of(
                Enum.valueOf(e, "ADD_PLAYER"),
                Enum.valueOf(e, "UPDATE_LISTED"),
                Enum.valueOf(e, "UPDATE_LATENCY"),
                Enum.valueOf(e, "UPDATE_GAME_MODE"),
                Enum.valueOf(e, "UPDATE_DISPLAY_NAME"));
    }

    private static Object buildEntry(UUID uuid, Object profile, Object displayName) throws Exception {
        Constructor<?>[] ctors = playerInfoUpdateEntryClass.getDeclaredConstructors();
        Arrays.sort(ctors, (a, b) -> b.getParameterCount() - a.getParameterCount());
        Exception last = null;
        for (Constructor<?> ctor : ctors) {
            ctor.setAccessible(true);
            try {
                return ctor.newInstance(mapEntryArgs(ctor.getParameterTypes(), uuid, profile, displayName));
            } catch (Exception ex) {
                last = ex;
            }
        }
        throw new IllegalStateException("No Entry ctor matched. Last: " + (last != null ? last.getMessage() : "?"));
    }

    private static Object[] mapEntryArgs(Class<?>[] types, UUID uuid, Object profile, Object displayName) {
        Object[] args    = new Object[types.length];
        int      boolIdx = 0;
        for (int i = 0; i < types.length; i++) {
            args[i] = switch (types[i].getSimpleName()) {
                case "UUID"        -> uuid;
                case "GameProfile" -> profile;
                case "boolean"     -> (boolIdx++ == 0);
                case "int"         -> 0;
                case "GameType"    -> gameTypeSurvival;
                case "Component"   -> displayName;
                default            -> null;
            };
        }
        return args;
    }
}

