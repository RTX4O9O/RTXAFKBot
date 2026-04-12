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
 * Sends NMS packets via reflection - zero NMS imports, compiles against paper-api only.
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

    // ── sendPositionSync cache ─────────────────────────────────────────────────
    /** Cached constructor for the position-sync teleport packet - resolved once on first call. */
    private static volatile Constructor<?> cachedPosSyncCtor        = null;
    /** {@code true} when {@link #cachedPosSyncCtor} takes a single NMS Entity argument. */
    private static volatile boolean        posSyncUsesEntityArg     = false;
    /** Set to {@code true} once the constructor lookup has been attempted (success or failure). */
    private static volatile boolean        posSyncCtorLookupDone    = false;

    // ── sendRotation cache ────────────────────────────────────────────────────
    /** Cached {@code ClientboundMoveEntityPacket$Rot(int, byte, byte, bool)} ctor. */
    private static volatile Constructor<?> cachedMoveEntityRotCtor  = null;
    /** Cached {@code ClientboundRotateHeadPacket(int, byte)} ctor - takes entity-id directly. */
    private static volatile Constructor<?> cachedRotateHeadCtorInt  = null;
    /** Cached {@code ClientboundRotateHeadPacket(Entity, byte)} ctor - takes NMS Entity. */
    private static volatile Constructor<?> cachedRotateHeadCtorEntity = null;
    /** {@code true} once the rotation-constructor lookup has been attempted. */
    private static volatile boolean        rotCtorLookupDone        = false;

    // ── Tab-list packet caches ─────────────────────────────────────────────────
    /** Cached {@code EnumSet{UPDATE_DISPLAY_NAME}} used in the periodic refresh loop. */
    private static volatile Object         cachedUpdateDisplayNameActions = null;
    /** Cached winner constructor from {@link #buildEntry} - avoids per-call sort+scan. */
    private static volatile Constructor<?> cachedEntryCtorWinner    = null;
    /** Param types of {@link #cachedEntryCtorWinner} - avoids repeated reflection calls. */
    private static volatile Class<?>[]     cachedEntryCtorParamTypes = null;
    /**
     * Cached second-arg strategy for {@code playerInfoUpdateCtor}:
     * 0=unset, 1=direct Entry, 2=Entry[], 3=List.
     */
    private static volatile int            cachedInfoUpdateSecondArgStrategy = 0;
    /** Component type when strategy == 2 (array). */
    private static volatile Class<?>       cachedInfoUpdateArrayCompType = null;

    private static Constructor<?> gameProfileCtor;
    private static Method         componentLiteral;
    private static Method         craftPlayerGetHandle;
    private static Constructor<?> playerInfoUpdateCtor;

    /** Paper's PaperAdventure.asVanilla(Component) - converts Adventure → NMS Component. */
    private static Method paperAdventureAsVanilla;

    /** Cached {@code ServerPlayer.connection} Field - resolved on first {@link #sendPacket} call. */
    private static volatile Field  cachedConnectionField;
    /** Cached {@code connection.send(Packet)} Method - resolved on first {@link #sendPacket} call. */
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
                throw new IllegalStateException("Cannot find NMS classloader - join the server first.");

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

            // Resolve PaperAdventure.asVanilla(Component) - Paper's official Adventure→NMS bridge
            try {
                Class<?> paperAdventure = Class.forName("io.papermc.paper.adventure.PaperAdventure");
                paperAdventureAsVanilla = paperAdventure.getDeclaredMethod("asVanilla", Component.class);
                paperAdventureAsVanilla.setAccessible(true);
                Config.debugPackets("PaperAdventure.asVanilla found - colored tablist names supported.");
            } catch (Exception ex) {
                Config.debugPackets("PaperAdventure.asVanilla not found, will fall back to literal: " + ex.getMessage());
            }

            Config.debugPackets("PacketHelper ready.");
            ready = true;
        } catch (Exception e) {
            broken = true;
            FppLogger.warn("PacketHelper init failed: " + e.getMessage());
            if (Config.debugPackets()) FppLogger.warn("  → " + e);
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
                Config.debugPackets("PlayerInfoUpdatePacket ctor: second param = " + p[1].getName());
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

    // ── Pre-compiled patterns ─────────────────────────────────────────────────
    private static final Pattern PKT_HEX_OPEN  = Pattern.compile("<#([A-Fa-f0-9]{6})>");
    private static final Pattern PKT_HEX_CLOSE = Pattern.compile("</#([A-Fa-f0-9]{6})>");
    private static final Pattern PKT_3DIG_OPEN  = Pattern.compile("<#([0-9A-Fa-f]{3})>");
    private static final Pattern PKT_3DIG_CLOSE = Pattern.compile("</#([0-9A-Fa-f]{3})>");

    // ── Public API ────────────────────────────────────────────────────────────

    /**
     * Converts MiniMessage hex color tags to Minecraft legacy hex color codes for tab list display name.
     * Supports both 6-digit and 3-digit hex codes (3-digit expands to 6-digit).
     * Example: <#0079FF>text</#0079FF> → §x§0§0§7§9§F§Ftext§r
     * Example: <#000>text</#000> → §x§0§0§0§0§0§0text§r (black)
     */
    public static String convertHexColors(String input) {
        // First, expand 3-digit hex codes to 6-digit format
        input = expand3DigitHexCodesForPacket(input);
        
        // Now convert 6-digit hex codes to legacy format using pre-compiled pattern
        Matcher m = PKT_HEX_OPEN.matcher(input);
        StringBuffer sb = new StringBuffer();
        while (m.find()) {
            String hex = m.group(1);
            StringBuilder color = new StringBuilder("§x");
            for (char c : hex.toCharArray()) color.append('§').append(c);
            m.appendReplacement(sb, color.toString());
        }
        m.appendTail(sb);
        String result = sb.toString();
        // Replace closing tags using pre-compiled pattern
        result = PKT_HEX_CLOSE.matcher(result).replaceAll("§r");
        return result;
    }

    /**
     * Expands 3-digit hex codes to 6-digit format.
     * Example: <#abc> → <#aabbcc>, </#f0f> → </#f0f0f0>
     */
    private static String expand3DigitHexCodesForPacket(String s) {
        if (s == null || s.indexOf('#') < 0) return s;
        
        // Opening tags: <#RGB> - use pre-compiled pattern
        Matcher m = PKT_3DIG_OPEN.matcher(s);
        StringBuffer sb = new StringBuffer();
        while (m.find()) {
            String hex3 = m.group(1);
            String hex6 = String.format("%c%c%c%c%c%c",
                    hex3.charAt(0), hex3.charAt(0),
                    hex3.charAt(1), hex3.charAt(1),
                    hex3.charAt(2), hex3.charAt(2));
            m.appendReplacement(sb, "<#" + hex6 + ">");
        }
        m.appendTail(sb);
        s = sb.toString();
        
        // Closing tags: </#RGB> - use pre-compiled pattern
        m = PKT_3DIG_CLOSE.matcher(s);
        sb = new StringBuffer();
        while (m.find()) {
            String hex3 = m.group(1);
            String hex6 = String.format("%c%c%c%c%c%c",
                    hex3.charAt(0), hex3.charAt(0),
                    hex3.charAt(1), hex3.charAt(1),
                    hex3.charAt(2), hex3.charAt(2));
            m.appendReplacement(sb, "</#" + hex6 + ">");
        }
        m.appendTail(sb);
        
        return sb.toString();
    }

    public static void sendTabListAdd(Player receiver, FakePlayer fp) {
        if (!ensureReady()) return;
        try {
            Object nms = getHandle(receiver);

            // Build GameProfile with UUID + name, with skin injected if available
            // Build GameProfile with UUID + name (may include invisible sort prefix)
            Object profile = buildProfileWithSkin(fp);

            // Parse display name: MiniMessage → Adventure Component → NMS Component
            String dispStr = fp.getDisplayName();
            Object displayName = fp.getCachedNmsDisplayComponent();
            if (displayName == null || !dispStr.equals(fp.getCachedNmsDisplaySource())) {
                Component adv = MiniMessage.miniMessage().deserialize(dispStr);
                displayName   = adventureToNms(adv);
                fp.setCachedNmsDisplay(displayName, dispStr);
            }

            Object entry   = buildEntry(fp.getUuid(), profile, displayName);
            Object actions = buildActionSet();

            sendPacket(nms, playerInfoUpdateCtor.newInstance(actions, buildSecondArg(entry)));
            Config.debugPackets("Tab ADD → " + receiver.getName() + " for " + fp.getName());
        } catch (Exception e) {
            FppLogger.error("sendTabListAdd failed: " + e.getMessage());
            if (Config.debugPackets()) FppLogger.warn("  → " + e);
        }
    }

    /**
     * Builds a {@code GameProfile} for the fake player, injecting skin texture properties
     * when a resolved skin is available.
     *
     * <p>Uses {@link FakePlayer#getPacketProfileName()} for the profile name (may include
     * a non-visible sort prefix based on LuckPerms group weight).
     *
     * <p>Modern authlib PropertyMap delegates to a {@code HashMultimap} internally
     * which IS mutable - we just need to bypass the immutable wrapper that
     * may be returned by {@code getProperties()} in some builds.
     */
    private static Object buildProfileWithSkin(FakePlayer fp) throws Exception {
        // Use packet profile name (may include invisible sort prefix) when building the GameProfile.
        // Guard: a blank profile name causes the vanilla 1.20+ client to show "Anonymous Player".
        String profileName = fp.getPacketProfileName();
        if (profileName == null || profileName.isBlank()) profileName = fp.getName();
        Object profile = gameProfileCtor != null
                ? gameProfileCtor.newInstance(fp.getUuid(), profileName)
                : gameProfileClass.getDeclaredConstructors()[0].newInstance(fp.getUuid(), profileName);

        SkinProfile skin = fp.getResolvedSkin();
        if (skin == null || !skin.isValid()) return profile;

        try {
            injectProperty(profile, "textures", skin.getValue(), skin.getSignature());
        } catch (Exception e) {
            Config.debugPackets("buildProfileWithSkin: inject failed - " + e.getMessage());
        }
        return profile;
    }

    /**
     * Injects a named property into a {@code GameProfile}'s {@code PropertyMap} via reflection.
     *
     * <p>Strategy (most → least reliable):
     * <ol>
     *   <li>Create {@code Property(name, value, signature)} (3-arg, canonical authlib ctor).</li>
     *   <li>Fetch the profile's {@code PropertyMap} via {@code getProperties()}.</li>
     *   <li>Call {@code put(String, Property)} directly on the map.</li>
     *   <li>If the map is an immutable wrapper, find its delegate field and call {@code put} there.</li>
     *   <li>Last resort: create a fresh mutable {@code HashMultimap}, copy existing entries, add the
     *       new property, and swap the backing field via reflection.</li>
     * </ol>
     */
    private static void injectProperty(Object profile, String key, String value, String signature)
            throws Exception {

        // ── Step 1: Resolve Property class ───────────────────────────────────
        ClassLoader cl = profile.getClass().getClassLoader();
        Class<?> propertyClass = cl.loadClass("com.mojang.authlib.properties.Property");

        // Build Property(name, value, signature) - the canonical 3-arg constructor
        Object property;
        try {
            Constructor<?> c3 = propertyClass.getDeclaredConstructor(
                    String.class, String.class, String.class);
            c3.setAccessible(true);
            property = c3.newInstance(key, value, signature != null ? signature : "");
        } catch (NoSuchMethodException ex) {
            // Older authlib: 2-arg constructor (name, value) - no signature
            Constructor<?> c2 = propertyClass.getDeclaredConstructor(String.class, String.class);
            c2.setAccessible(true);
            property = c2.newInstance(key, value);
        }

        // ── Step 2: Get PropertyMap from getProperties() ─────────────────────
        Method getProps = null;
        for (Method m : profile.getClass().getMethods()) {
            if (m.getName().equals("getProperties") && m.getParameterCount() == 0) {
                getProps = m;
                break;
            }
        }
        if (getProps == null) throw new NoSuchMethodException("GameProfile.getProperties()");
        Object propertyMap = getProps.invoke(profile);

        // ── Step 3: Direct put on the PropertyMap (try public API first) ──────
        for (Method m : propertyMap.getClass().getMethods()) {
            if ("put".equals(m.getName()) && m.getParameterCount() == 2) {
                try {
                    m.invoke(propertyMap, key, property);
                    Config.debugPackets("injectProperty: direct put succeeded.");
                    return;
                } catch (Exception ignored) {
                    break; // likely immutable wrapper - fall through
                }
            }
        }

        // ── Step 4: Try calling put on a delegate (immutable wrapper pattern) ─
        for (Field f : propertyMap.getClass().getDeclaredFields()) {
            f.setAccessible(true);
            Object delegate = f.get(propertyMap);
            if (delegate == null) continue;
            for (Method m : delegate.getClass().getMethods()) {
                if ("put".equals(m.getName()) && m.getParameterCount() == 2) {
                    try {
                        m.invoke(delegate, key, property);
                        Config.debugPackets("injectProperty: put via delegate '" + f.getName() + "' succeeded.");
                        return;
                    } catch (Exception ignored) {}
                }
            }
        }

        // ── Step 5: Replace delegate with a fresh HashMultimap ───────────────
        Class<?> hashMultimapClass = cl.loadClass("com.google.common.collect.HashMultimap");
        Method create = hashMultimapClass.getMethod("create");
        Object mutableMap = create.invoke(null);

        // Copy existing properties into the new map
        Method valuesMethod = null;
        for (Method m : propertyMap.getClass().getMethods()) {
            if ("values".equals(m.getName()) && m.getParameterCount() == 0) { valuesMethod = m; break; }
        }
        if (valuesMethod != null) {
            Object existing = valuesMethod.invoke(propertyMap);
            if (existing instanceof Iterable<?> iter) {
                Method putMethod = null;
                for (Method m : mutableMap.getClass().getMethods()) {
                    if ("put".equals(m.getName()) && m.getParameterCount() == 2) { putMethod = m; break; }
                }
                if (putMethod != null) {
                    for (Object entry : iter) {
                        // entry is a Property; get its name via getName() or name()
                        String entryKey = null;
                        for (String getter : new String[]{"getName", "name"}) {
                            try {
                                entryKey = (String) entry.getClass().getMethod(getter).invoke(entry);
                                break;
                            } catch (NoSuchMethodException ignored) {}
                        }
                        if (entryKey != null) putMethod.invoke(mutableMap, entryKey, entry);
                    }
                }
            }
        }

        // Put the new property
        for (Method m : mutableMap.getClass().getMethods()) {
            if ("put".equals(m.getName()) && m.getParameterCount() == 2) {
                m.invoke(mutableMap, key, property);
                break;
            }
        }

        // Swap the backing field of the PropertyMap wrapper
        for (Field f : propertyMap.getClass().getDeclaredFields()) {
            f.setAccessible(true);
            Object delegate = f.get(propertyMap);
            if (delegate != null && delegate.getClass().getName().contains("Multimap")) {
                f.set(propertyMap, mutableMap);
                Config.debugPackets("injectProperty: replaced delegate multimap - succeeded.");
                return;
            }
        }

        FppLogger.warn("injectProperty: all strategies exhausted - skin may not appear in tab list.");
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
            Config.debugPackets("Tab REMOVE → " + receiver.getName() + " for " + fp.getName());
        } catch (Exception e) {
            FppLogger.error("sendTabListRemove failed: " + e.getMessage());
        }
    }

    /**
     * Sends a tab-list REMOVE packet for a bot identified by its UUID alone.
     * Used to remove remote-server bots (from {@link me.bill.fakePlayerPlugin.fakeplayer.RemoteBotCache})
     * from a player's tab list without needing a local {@link FakePlayer} object.
     */
    public static void sendTabListRemoveByUuid(Player receiver, UUID uuid) {
        if (!ensureReady()) return;
        try {
            Object nms = getHandle(receiver);
            Constructor<?> ctor = getConstructor(playerInfoRemovePacketClass, List.class);
            if (ctor == null) {
                ctor = playerInfoRemovePacketClass.getDeclaredConstructors()[0];
                ctor.setAccessible(true);
            }
            sendPacket(nms, ctor.newInstance(List.of(uuid)));
            Config.debugPackets("Tab REMOVE raw → " + receiver.getName() + " for " + uuid);
        } catch (Exception e) {
            FppLogger.error("sendTabListRemoveByUuid failed: " + e.getMessage());
        }
    }

    /**
     * Sends a tab-list ADD packet using raw fields instead of a {@link FakePlayer} object.
     *
     * <p>Used to add remote-server bots to the tab list on this server so players see
     * bots from all servers in the proxy network, not just the server they're connected to.
     *
     * @param packetProfileName profile name used for tab-list ordering (may have a sort prefix)
     * @param displayName       MiniMessage-formatted display name (includes LP prefix)
     * @param skinValue         base64-encoded texture value, or {@code null} / blank to skip skin
     * @param skinSignature     RSA signature from Mojang, or {@code null}
     */
    public static void sendTabListAddRaw(Player receiver, UUID uuid, String packetProfileName,
                                         String displayName, String skinValue, String skinSignature) {
        if (!ensureReady()) return;
        try {
            Object nms = getHandle(receiver);

            // Guard: a blank profile name causes the vanilla 1.20+ client to show "Anonymous Player".
            // Use the first 8 chars of the UUID as a safe fallback when no name is available.
            String safeProfileName = (packetProfileName == null || packetProfileName.isBlank())
                    ? uuid.toString().replace("-", "").substring(0, 8)
                    : packetProfileName;

            // Build GameProfile with the packet-profile name (includes sort prefix)
            Object profile = gameProfileCtor != null
                    ? gameProfileCtor.newInstance(uuid, safeProfileName)
                    : gameProfileClass.getDeclaredConstructors()[0].newInstance(uuid, safeProfileName);

            // Inject skin texture properties when present
            if (skinValue != null && !skinValue.isBlank()) {
                try {
                    injectProperty(profile, "textures", skinValue, skinSignature);
                } catch (Exception ex) {
                    Config.debugPackets("sendTabListAddRaw: skin inject failed - " + ex.getMessage());
                }
            }

            // Parse display name MiniMessage → Adventure → NMS
            Component adventureComponent = MiniMessage.miniMessage().deserialize(displayName);
            Object nmsDisplayName = adventureToNms(adventureComponent);

            Object entry   = buildEntry(uuid, profile, nmsDisplayName);
            Object actions = buildActionSet();

            sendPacket(nms, playerInfoUpdateCtor.newInstance(actions, buildSecondArg(entry)));
            Config.debugPackets("Tab ADD raw → " + receiver.getName() + " for " + safeProfileName
                    + (skinValue != null && !skinValue.isBlank() ? " [skinned]" : ""));
        } catch (Exception e) {
            FppLogger.error("sendTabListAddRaw failed: " + e.getMessage());
            if (Config.debugPackets()) FppLogger.warn("  → " + e);
        }
    }

    /**
     * Sends only the {@code UPDATE_DISPLAY_NAME} action for {@code fp} to {@code receiver}.
     * Lighter than {@link #sendTabListAdd} - use in the periodic refresh loop to override
     * TAB plugin resets without re-adding the player entry from scratch.
     *
     * <p><b>Performance:</b>
     * <ul>
     *   <li>The NMS {@code Component} for the display name is cached on {@code fp} and
     *       rebuilt only when {@link FakePlayer#setDisplayName(String)} is called.</li>
     *   <li>The {@code GameProfile(uuid, name)} object is cached on {@code fp} for lifetime.</li>
     *   <li>The {@code UPDATE_DISPLAY_NAME} action set and the packet-constructor's
     *       second-arg strategy are both cached after the first call.</li>
     * </ul>
     */
    @SuppressWarnings({"unchecked", "rawtypes"})
    public static void sendTabListDisplayNameUpdate(Player receiver, FakePlayer fp) {
        if (!ensureReady()) return;
        try {
            Object nms = getHandle(receiver);

            // ── Cached GameProfile(uuid, name) - created once per bot ─────────
            Object profile = fp.getCachedTabListGameProfile();
            if (profile == null) {
                profile = gameProfileCtor != null
                        ? gameProfileCtor.newInstance(fp.getUuid(), fp.getName())
                        : gameProfileClass.getDeclaredConstructors()[0].newInstance(fp.getUuid(), fp.getName());
                fp.setCachedTabListGameProfile(profile);
            }

            // ── Cached NMS display-name Component - rebuilt only on name change ─
            String dispStr = fp.getDisplayName();
            Object displayName = fp.getCachedNmsDisplayComponent();
            if (displayName == null || !dispStr.equals(fp.getCachedNmsDisplaySource())) {
                Component adv = MiniMessage.miniMessage().deserialize(dispStr);
                displayName   = adventureToNms(adv);
                fp.setCachedNmsDisplay(displayName, dispStr);
            }

            Object entry = buildEntry(fp.getUuid(), profile, displayName);

            // ── Cached UPDATE_DISPLAY_NAME action set ─────────────────────────
            if (cachedUpdateDisplayNameActions == null) {
                Class<? extends Enum> e = rawEnum(playerInfoUpdateActionClass);
                cachedUpdateDisplayNameActions = EnumSet.of(Enum.valueOf(e, "UPDATE_DISPLAY_NAME"));
            }
            Object actions = cachedUpdateDisplayNameActions;

            sendPacket(nms, playerInfoUpdateCtor.newInstance(actions, buildSecondArg(entry)));
        } catch (Exception e) {
            // Silent - this is a background refresh, don't spam logs
        }
    }

    /**
     * Sends only the {@code UPDATE_DISPLAY_NAME} action for a remote bot (identified by
     * raw UUID and MiniMessage display-name string) to {@code receiver}.
     * Used by {@link me.bill.fakePlayerPlugin.messaging.VelocityChannel} when a
     * {@code BOT_UPDATE} message arrives for a remote bot that is not locally active.
     */
    @SuppressWarnings({"unchecked", "rawtypes"})
    public static void sendTabListDisplayNameUpdate(Player receiver, UUID uuid, String rawDisplayName) {
        if (!ensureReady()) return;
        try {
            Object nms = getHandle(receiver);
            Object profile = gameProfileCtor != null
                    ? gameProfileCtor.newInstance(uuid, uuid.toString().substring(0, 8))
                    : gameProfileClass.getDeclaredConstructors()[0].newInstance(uuid, uuid.toString().substring(0, 8));

            Component adventureComponent = MiniMessage.miniMessage().deserialize(rawDisplayName);
            Object displayName = adventureToNms(adventureComponent);

            Object entry = buildEntry(uuid, profile, displayName);

            // Use cached action set
            if (cachedUpdateDisplayNameActions == null) {
                Class<? extends Enum> e = rawEnum(playerInfoUpdateActionClass);
                cachedUpdateDisplayNameActions = EnumSet.of(Enum.valueOf(e, "UPDATE_DISPLAY_NAME"));
            }
            sendPacket(nms, playerInfoUpdateCtor.newInstance(cachedUpdateDisplayNameActions, buildSecondArg(entry)));
        } catch (Exception e) {
            // Silent
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
                    fp.getPlayer().getEntityId(), fp.getUuid(),
                    loc.getX(), loc.getY(), loc.getZ(),
                    loc.getPitch(), loc.getYaw(),
                    entityTypePlayer, 0, vec3Zero, 0.0));
            Config.debugPackets("Spawn entity → " + receiver.getName() + " for " + fp.getName());
        } catch (Exception e) {
            FppLogger.error("spawnFakePlayer failed: " + e.getMessage());
            if (Config.debugPackets()) FppLogger.warn("  → " + e);
        }
    }

    public static void despawnFakePlayer(Player receiver, FakePlayer fp) {
        if (!ensureReady()) return;
        try {
            Object nms = getHandle(receiver);
            Constructor<?> ctor = removeEntitiesPacketClass.getConstructor(int[].class);
            sendPacket(nms, ctor.newInstance((Object) new int[]{fp.getPlayer().getEntityId()}));
            Config.debugPackets("Despawn entity → " + receiver.getName() + " for " + fp.getName());
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

    /**
     * Sends a main-hand swing animation (animation ID 0) for {@code fp} to {@code receiver}.
     * Broadcasts a realistic punch visual each time the PVP bot attacks.
     */
    public static void sendSwingArm(Player receiver, FakePlayer fp) {
        if (!ensureReady()) return;
        try {
            Object nms = getHandle(receiver);
            ClassLoader cl = nms.getClass().getClassLoader();
            Class<?> animClass = cl.loadClass("net.minecraft.network.protocol.game.ClientboundAnimatePacket");
            for (Constructor<?> c : animClass.getDeclaredConstructors()) {
                c.setAccessible(true);
                Class<?>[] pt = c.getParameterTypes();
                if (pt.length == 2 && pt[0] == int.class && pt[1] == int.class) {
                    sendPacket(nms, c.newInstance(fp.getEntityId(), 0)); // 0 = SWING_MAIN_ARM
                    return;
                }
            }
        } catch (Exception ignored) {}
    }

    /**
     * Sends a food eating event (entity event ID 9 = LIVING_EAT_FOOD) for {@code fp} to {@code receiver}.
     * This shows the eating animation (bob + nom nom) to nearby players.
     * Call every few ticks while the bot is eating.
     */
    public static void sendEatAnimation(Player receiver, FakePlayer fp) {
        if (!ensureReady()) return;
        try {
            Object nms = getHandle(receiver);
            ClassLoader cl = nms.getClass().getClassLoader();
            // ClientboundEntityEventPacket(Entity entity, byte eventId)
            // Event ID 9 = LIVING_EAT_FOOD - triggers the eating arm animation
            Class<?> entityEventClass = cl.loadClass(
                "net.minecraft.network.protocol.game.ClientboundEntityEventPacket");
            // Find the NMS entity for the bot
            Player botPlayer = fp.getPlayer();
            if (botPlayer == null) return;
            Object botNms = getHandle(botPlayer);
            for (java.lang.reflect.Constructor<?> c : entityEventClass.getDeclaredConstructors()) {
                c.setAccessible(true);
                Class<?>[] pt = c.getParameterTypes();
                if (pt.length == 2 && pt[1] == byte.class) {
                    sendPacket(nms, c.newInstance(botNms, (byte) 9));
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

            // ── One-time constructor lookup (cached after first attempt) ──────
            if (!rotCtorLookupDone) {
                synchronized (PacketHelper.class) {
                    if (!rotCtorLookupDone) {
                        for (Constructor<?> c : moveEntityRotPacketClass.getDeclaredConstructors()) {
                            Class<?>[] p = c.getParameterTypes();
                            if (p.length == 4 && p[0] == int.class && p[1] == byte.class) {
                                c.setAccessible(true);
                                cachedMoveEntityRotCtor = c;
                                break;
                            }
                        }
                        for (Constructor<?> c : rotateHeadPacketClass.getDeclaredConstructors()) {
                            c.setAccessible(true);
                            Class<?>[] p = c.getParameterTypes();
                            if (p.length == 2 && p[0] == int.class) {
                                cachedRotateHeadCtorInt = c;
                                break;
                            } else if (p.length == 2) {
                                cachedRotateHeadCtorEntity = c;
                            }
                        }
                        rotCtorLookupDone = true;
                    }
                }
            }

            // ── Send packets using cached constructors ─────────────────────────
            if (cachedMoveEntityRotCtor != null) {
                sendPacket(nms, cachedMoveEntityRotCtor.newInstance(entityId, encYaw, encPitch, true));
            }
            if (cachedRotateHeadCtorInt != null) {
                sendPacket(nms, cachedRotateHeadCtorInt.newInstance(entityId, encHead));
            } else if (cachedRotateHeadCtorEntity != null && fp.getPhysicsEntity() != null) {
                // Use the already-cached craftPlayerGetHandle method instead of re-scanning
                Object nmsEntity = craftPlayerGetHandle.invoke(fp.getPhysicsEntity());
                sendPacket(nms, cachedRotateHeadCtorEntity.newInstance(nmsEntity, encHead));
            }
        } catch (Exception e) {
            Config.debugPackets("sendRotation failed: " + e.getMessage());
        }
    }

    /**
     * Sends an entity position-sync (teleport) packet to {@code receiver} for {@code bot}.
     *
     * <p>The constructor is resolved <em>once</em> and cached; subsequent calls per tick pay
     * zero reflection-scanning overhead.  Two strategies are tried in order:
     * <ol>
     *   <li><b>7-param coordinate ctor</b> - {@code (int entityId, double x, double y, double z,
     *       float yaw, float pitch, boolean onGround)} - used by
     *       {@code ClientboundEntityPositionSyncPacket} (1.21.2+) and by older builds of
     *       {@code ClientboundTeleportEntityPacket}.</li>
     *   <li><b>1-param Entity ctor</b> - {@code (Entity entity)} - used by very old NMS builds.</li>
     * </ol>
     * Class-name candidates tried in preference order:
     * <ol>
     *   <li>{@code ClientboundEntityPositionSyncPacket} (1.21.2+, Leaf/Paper 1.21.11)</li>
     *   <li>{@code ClientboundTeleportEntityPacket} (pre-1.21.2 fallback)</li>
     * </ol>
     */
    public static void sendPositionSync(Player receiver, Player bot) {
        if (!ensureReady()) return;
        try {
            Object receiverNms = craftPlayerGetHandle.invoke(receiver);

            // ── One-time constructor lookup (cached after first call) ────────────
            if (!posSyncCtorLookupDone) {
                synchronized (PacketHelper.class) {
                    if (!posSyncCtorLookupDone) {
                        ClassLoader cl = receiverNms.getClass().getClassLoader();
                        String[] candidates = {
                            "net.minecraft.network.protocol.game.ClientboundEntityPositionSyncPacket",
                            "net.minecraft.network.protocol.game.ClientboundTeleportEntityPacket"
                        };
                        outer:
                        for (String className : candidates) {
                            try {
                                Class<?> pktClass = cl.loadClass(className);
                                // Prefer 7-param coordinate constructor
                                for (Constructor<?> c : pktClass.getDeclaredConstructors()) {
                                    c.setAccessible(true);
                                    Class<?>[] pt = c.getParameterTypes();
                                    if (pt.length == 7 && pt[0] == int.class && pt[1] == double.class) {
                                        cachedPosSyncCtor    = c;
                                        posSyncUsesEntityArg = false;
                                        Config.debugPackets("sendPositionSync: using 7-param ctor from " + className);
                                        break outer;
                                    }
                                }
                                // Fallback: 1-param Entity constructor (older NMS)
                                for (Constructor<?> c : pktClass.getDeclaredConstructors()) {
                                    c.setAccessible(true);
                                    if (c.getParameterCount() == 1) {
                                        cachedPosSyncCtor    = c;
                                        posSyncUsesEntityArg = true;
                                        Config.debugPackets("sendPositionSync: using 1-param (Entity) ctor from " + className);
                                        break outer;
                                    }
                                }
                            } catch (ClassNotFoundException ignored) {}
                        }
                        posSyncCtorLookupDone = true;
                        if (cachedPosSyncCtor == null) {
                            Config.debugPackets("sendPositionSync: no suitable position-sync packet constructor found - position sync disabled.");
                        }
                    }
                }
            }

            if (cachedPosSyncCtor == null) return;

            // ── Build and send packet ─────────────────────────────────────────────
            Object packet;
            if (posSyncUsesEntityArg) {
                Object nmsBot = craftPlayerGetHandle.invoke(bot);
                packet = cachedPosSyncCtor.newInstance(nmsBot);
            } else {
                Location loc = bot.getLocation();
                packet = cachedPosSyncCtor.newInstance(
                        bot.getEntityId(),
                        loc.getX(), loc.getY(), loc.getZ(),
                        loc.getYaw(), loc.getPitch(), true);
            }
            sendPacket(receiverNms, packet);

        } catch (Exception e) {
            Config.debugPackets("sendPositionSync failed: " + e.getMessage());
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
            // Walk up the class hierarchy SKIPPING our own FakeServerGamePacketListenerImpl
            // so the cached method is resolved from the NMS base class.
            // This is critical: if the first sendPacket call goes through a bot (whose
            // connection is FakeServerGamePacketListenerImpl), getMethods() would return
            // the override declared in FakeServerGamePacketListenerImpl.  Caching that
            // method and later invoking it on a real ServerGamePacketListenerImpl fails
            // with "object is not an instance of FakeServerGamePacketListenerImpl".
            // By skipping our own package we always land on the NMS base send(), which
            // FakeServerGamePacketListenerImpl IS-A of, so invoke() works for both.
            Class<?> cur = conn.getClass();
            outer:
            while (cur != null && cur != Object.class) {
                if (!cur.getName().startsWith("me.bill.")) {
                    for (Method m : cur.getDeclaredMethods()) {
                        if (m.getName().equals("send") && m.getParameterCount() == 1) {
                            m.setAccessible(true);
                            cachedSendMethod = m;
                            break outer;
                        }
                    }
                }
                cur = cur.getSuperclass();
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

    /** Scans declared fields for a static field - bypasses Paper's reflection rewriter. */
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
                Config.debugPackets("PaperAdventure.asVanilla failed: " + e.getMessage());
            }
        }
        // Fallback: plain text (no color) but at least avoids crashes
        if (componentLiteral != null) {
            try {
                String plain = PlainTextComponentSerializer.plainText().serialize(component);
                return componentLiteral.invoke(null, plain);
            } catch (Exception e) {
                Config.debugPackets("componentLiteral fallback failed: " + e.getMessage());
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

    /**
     * Builds a {@code PlayerInfoUpdatePacket.Entry} using a cached constructor winner.
     * The winning constructor is found once (descending param-count, first success)
     * and reused for every subsequent call to avoid repeated
     * {@code getDeclaredConstructors()} + {@code Arrays.sort()} + {@code setAccessible()}
     * overhead.
     */
    private static Object buildEntry(UUID uuid, Object profile, Object displayName) throws Exception {
        if (cachedEntryCtorWinner != null) {
            return cachedEntryCtorWinner.newInstance(
                    mapEntryArgs(cachedEntryCtorParamTypes, uuid, profile, displayName));
        }
        Constructor<?>[] ctors = playerInfoUpdateEntryClass.getDeclaredConstructors();
        Arrays.sort(ctors, (a, b) -> b.getParameterCount() - a.getParameterCount());
        Exception last = null;
        for (Constructor<?> ctor : ctors) {
            ctor.setAccessible(true);
            try {
                Object result = ctor.newInstance(mapEntryArgs(ctor.getParameterTypes(), uuid, profile, displayName));
                // Cache the winning constructor + its param types for all future calls
                cachedEntryCtorParamTypes = ctor.getParameterTypes();
                cachedEntryCtorWinner     = ctor;
                return result;
            } catch (Exception ex) {
                last = ex;
            }
        }
        throw new IllegalStateException("No Entry ctor matched. Last: " + (last != null ? last.getMessage() : "?"));
    }

    /**
     * Builds the second argument for {@code ClientboundPlayerInfoUpdatePacket(actions, ?)}
     * using a cached strategy so we never call {@code getParameterTypes()[1]} per-call.
     * Strategy is determined once from the ctor's second param type:
     * 1 = direct {@code Entry}, 2 = {@code Entry[]}, 3 = {@code List<Entry>}.
     */
    private static Object buildSecondArg(Object entry) throws Exception {
        if (cachedInfoUpdateSecondArgStrategy == 0) {
            Class<?> spt = playerInfoUpdateCtor.getParameterTypes()[1];
            if (spt == playerInfoUpdateEntryClass) {
                cachedInfoUpdateSecondArgStrategy = 1;
            } else if (spt.isArray()) {
                cachedInfoUpdateSecondArgStrategy = 2;
                cachedInfoUpdateArrayCompType     = spt.getComponentType();
            } else {
                cachedInfoUpdateSecondArgStrategy = 3;
            }
        }
        return switch (cachedInfoUpdateSecondArgStrategy) {
            case 1 -> entry;
            case 2 -> {
                Object arr = java.lang.reflect.Array.newInstance(cachedInfoUpdateArrayCompType, 1);
                java.lang.reflect.Array.set(arr, 0, entry);
                yield arr;
            }
            default -> List.of(entry);
        };
    }

    private static Object[] mapEntryArgs(Class<?>[] types, UUID uuid, Object profile, Object displayName) {
        Object[] args = new Object[types.length];
        for (int i = 0; i < types.length; i++) {
            args[i] = switch (types[i].getSimpleName()) {
                case "UUID"        -> uuid;
                case "GameProfile" -> profile;
                // All boolean flags should be true:
                //   listed    = true  (bot appears in tab list)
                //   showHat   = true  (outer head/hat skin layer visible - added in MC 1.21.4+)
                case "boolean"     -> true;
                case "int"         -> 0;
                case "GameType"    -> gameTypeSurvival;
                case "Component"   -> displayName;
                default            -> null;
            };
        }
        return args;
    }
}








