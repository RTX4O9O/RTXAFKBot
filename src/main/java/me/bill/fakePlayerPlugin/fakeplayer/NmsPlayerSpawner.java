package me.bill.fakePlayerPlugin.fakeplayer;

import me.bill.fakePlayerPlugin.util.FppLogger;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.World;
import org.bukkit.block.Block;
import org.bukkit.block.data.BlockData;
import org.bukkit.block.data.Waterlogged;
import org.bukkit.entity.Player;

import java.lang.reflect.Constructor;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.UUID;

/**
 * NMS-based fake player spawner using reflection (version-independent).
 * Creates real NMS ServerPlayer entities that appear as genuine players.
 *
 * <p><b>Architecture:</b> Uses reflection to access NMS classes without direct imports,
 * making it compatible across Paper versions. Creates a true {@code ServerPlayer} instance
 * that bypasses Mojang authentication.
 *
 * <p><b>Auth bypass:</b> The player is created directly in JVM memory without a TCP connection,
 * so the Mojang session server handshake is never triggered.
 */
public final class NmsPlayerSpawner {

    private static boolean initialized = false;
    private static boolean failed = false;

    // Cached classes
    private static Class<?> minecraftServerClass;
    private static Class<?> serverLevelClass;
    private static Class<?> serverPlayerClass;
    private static Class<?> gameProfileClass;
    private static Class<?> clientInformationClass;
    private static Class<?> commonListenerCookieClass;
    private static Class<?> connectionClass;
    private static Class<?> serverGamePacketListenerClass;
    private static Class<?> chatVisibilityClass;
    private static Class<?> humanoidArmClass;
    private static Class<?> particleStatusClass;

    // Cached methods
    private static Method getServerMethod;
    private static Method getHandleMethod;
    private static Method craftPlayerGetHandleMethod; // CraftPlayer.getHandle() → NMS ServerPlayer
    private static Method getPlayerListMethod;
    private static Method setPosMethod;

    // Cached constructors  
    private static Constructor<?> gameProfileConstructor;
    private static Constructor<?> clientInformationConstructor;

    // Cached enum values
    private static Object chatVisibilityHidden;
    private static Object humanoidArmRight;
    private static Object particleStatusAll;

    private NmsPlayerSpawner() {}

    /**
     * Initialize NMS reflection. Safe to call multiple times.
     */
    private static synchronized void init() {
        if (initialized || failed) return;

        try {
            // Get CraftBukkit version (or detect Mojang-mapped Paper)
            String packageName = Bukkit.getServer().getClass().getPackage().getName();
            String version = packageName.substring(packageName.lastIndexOf('.') + 1);
            
            // On Mojang-mapped Paper, classes are in org.bukkit.craftbukkit directly (no version)
            String craftBukkitPackage = "org.bukkit.craftbukkit";
            if (!version.equals("craftbukkit")) {
                // Legacy/Spigot: org.bukkit.craftbukkit.v1_XX_RX
                craftBukkitPackage += "." + version;
            }
            
            FppLogger.debug("Detected CraftBukkit package: " + craftBukkitPackage);

            // Load CraftBukkit classes
            Class<?> craftServerClass = Class.forName(craftBukkitPackage + ".CraftServer");
            Class<?> craftWorldClass = Class.forName(craftBukkitPackage + ".CraftWorld");

            // Get NMS class loader
            ClassLoader nmsLoader = craftServerClass.getClassLoader();

            // Try Mojang-mapped names first, fall back to Spigot names
            // Load critical classes first (these determine Mojang vs Spigot)
            try {
                serverPlayerClass = nmsLoader.loadClass("net.minecraft.server.level.ServerPlayer");
                // If ServerPlayer loads, we're on Mojang mappings
                minecraftServerClass = nmsLoader.loadClass("net.minecraft.server.MinecraftServer");
                serverLevelClass = nmsLoader.loadClass("net.minecraft.server.level.ServerLevel");
                FppLogger.debug("Using Mojang-mapped NMS classes");
            } catch (ClassNotFoundException e) {
                // Fall back to Spigot-mapped names
                FppLogger.info("Mojang mappings not found, trying Spigot mappings...");
                minecraftServerClass = nmsLoader.loadClass("net.minecraft.server.MinecraftServer");
                serverLevelClass = nmsLoader.loadClass("net.minecraft.server.level.WorldServer");
                serverPlayerClass = nmsLoader.loadClass("net.minecraft.server.level.EntityPlayer");
                FppLogger.debug("Loaded Spigot-mapped NMS classes");
            }

            // Load optional classes individually (don't fail init if any are missing)
            try {
                clientInformationClass = nmsLoader.loadClass("net.minecraft.server.level.ClientInformation");
                FppLogger.debug("✓ Loaded ClientInformation class");
            } catch (ClassNotFoundException e) {
                FppLogger.debug("✗ ClientInformation class not found: " + e.getMessage());
            }
            
            try {
                chatVisibilityClass = nmsLoader.loadClass("net.minecraft.world.entity.player.ChatVisiblity");  // Note: typo in MC code
                FppLogger.debug("✓ Loaded ChatVisiblity class");
            } catch (ClassNotFoundException e) {
                FppLogger.debug("✗ ChatVisiblity class not found: " + e.getMessage());
            }
            
            try {
                humanoidArmClass = nmsLoader.loadClass("net.minecraft.world.entity.HumanoidArm");
                FppLogger.debug("✓ Loaded HumanoidArm class");
            } catch (ClassNotFoundException e) {
                FppLogger.debug("✗ HumanoidArm class not found: " + e.getMessage());
            }
            
            // Try multiple possible locations for ParticleStatus
            String[] particleStatusPaths = {
                "net.minecraft.world.level.GameRules$ParticleStatus",
                "net.minecraft.world.entity.player.ParticleStatus",
                "net.minecraft.core.particles.ParticleStatus",
                "net.minecraft.server.level.ParticleStatus"
            };
            
            for (String path : particleStatusPaths) {
                try {
                    particleStatusClass = nmsLoader.loadClass(path);
                    FppLogger.debug("✓ Loaded ParticleStatus class from: " + path);
                    break;
                } catch (ClassNotFoundException e) {
                    FppLogger.debug("✗ ParticleStatus not found at: " + path);
                }
            }
            
            if (particleStatusClass == null) {
                FppLogger.debug("✗ ParticleStatus class not found in any known location");
            }

            // Load other optional classes (currently unused but may be needed for future features)
            try {
                commonListenerCookieClass = nmsLoader.loadClass("net.minecraft.server.network.CommonListenerCookie");
                connectionClass = nmsLoader.loadClass("net.minecraft.network.Connection");
                serverGamePacketListenerClass = nmsLoader.loadClass("net.minecraft.server.network.ServerGamePacketListenerImpl");
            } catch (ClassNotFoundException e) {
                FppLogger.debug("Some optional network classes not available: " + e.getMessage());
            }

            // Load authlib (should be on classpath)
            gameProfileClass = Class.forName("com.mojang.authlib.GameProfile");

            // Cache methods
            getServerMethod = craftServerClass.getMethod("getServer");
            getHandleMethod = craftWorldClass.getMethod("getHandle");
            getPlayerListMethod = minecraftServerClass.getMethod("getPlayerList");
            
            // Find setPos method (various signatures across versions)
            for (Method m : serverPlayerClass.getMethods()) {
                if (m.getName().equals("setPos") && m.getParameterCount() == 3) {
                    Class<?>[] params = m.getParameterTypes();
                    if (params[0] == double.class && params[1] == double.class && params[2] == double.class) {
                        setPosMethod = m;
                        break;
                    }
                }
            }

            // Cache constructors
            gameProfileConstructor = gameProfileClass.getConstructor(UUID.class, String.class);

            // Cache CraftPlayer.getHandle() for setPosition() use
            Class<?> craftPlayerClass = Class.forName(craftBukkitPackage + ".entity.CraftPlayer");
            craftPlayerGetHandleMethod = craftPlayerClass.getMethod("getHandle");
            craftPlayerGetHandleMethod.setAccessible(true);

            initialized = true;
            FppLogger.info("NMS reflection initialized successfully");

        } catch (Exception e) {
            failed = true;
            FppLogger.error("Failed to initialize NMS reflection: " + e.getMessage());
            FppLogger.debug("Stack trace: " + java.util.Arrays.toString(e.getStackTrace()));
        }
    }

    /**
     * Check if NMS spawning is available.
     */
    public static boolean isAvailable() {
        if (!initialized && !failed) init();
        return initialized;
    }

    /**
     * Spawn a fake NMS ServerPlayer at the given location.
     *
     * <p><b>IMPORTANT:</b> This method creates the ServerPlayer with the bot's REAL name
     * (e.g., "BotName"), not the packet profile name (e.g., "{00_BotName").
     * The packet profile name with the sorting prefix is only used in tab-list packets
     * sent via {@code PacketHelper}, not in the actual NMS entity.
     *
     * @param uuid     Bot UUID (must match FakePlayer UUID so LP/TAB see the same user)
     * @param name     Bot name (REAL name, not packet profile name)
     * @param skin     Resolved skin profile to inject into the GameProfile, or {@code null}
     * @param world    World to spawn in
     * @param x        X coordinate
     * @param y        Y coordinate
     * @param z        Z coordinate
     * @return         Bukkit Player wrapper, or null if spawn failed
     */
    public static Player spawnFakePlayer(UUID uuid, String name, World world, double x, double y, double z) {
        return spawnFakePlayer(uuid, name, null, world, x, y, z);
    }

    public static Player spawnFakePlayer(UUID uuid, String name, SkinProfile skin,
                                         World world, double x, double y, double z) {
        if (!isAvailable()) {
            FppLogger.warn("NMS spawning not available, cannot spawn " + name);
            return null;
        }

        try {

            // Create GameProfile
            Object gameProfile = gameProfileConstructor.newInstance(uuid, name);
            if (skin != null && skin.isValid()) {
                try {
                    SkinProfileInjector.apply(gameProfile, skin);
                    FppLogger.debug("NmsPlayerSpawner: injected skin for '" + name + "' from " + skin.getSource() + ".");
                } catch (Exception e) {
                    FppLogger.warn("NmsPlayerSpawner: failed to inject skin for '" + name + "': " + e.getMessage());
                }
            }

            // Get MinecraftServer
            Object craftServer = Bukkit.getServer();
            Object minecraftServer = getServerMethod.invoke(craftServer);

            // Get ServerLevel
            Object serverLevel = getHandleMethod.invoke(world);

            // Create ClientInformation (if available)
            Object clientInfo = null;
            if (clientInformationClass != null) {
                clientInfo = createClientInformation();
            }

            // Create ServerPlayer
            // Constructor signature varies by version, try multiple approaches
            Object serverPlayer = createServerPlayer(minecraftServer, serverLevel, gameProfile, clientInfo);

            if (serverPlayer == null) {
                FppLogger.warn("Failed to create ServerPlayer for " + name);
                return null;
            }

            // Set position BEFORE adding to world
            if (setPosMethod != null) {
                setPosMethod.invoke(serverPlayer, x, y, z);
            }

            // CRITICAL: set up a fake network connection BEFORE adding to world.
            // ServerPlayer.tick() immediately calls this.connection.tickClientLoadTimeout(),
            // so connection must be non-null or the server crashes on the next tick.
            setupFakeConnection(minecraftServer, serverPlayer, gameProfile, clientInfo);

            // Add the ServerPlayer to the world via PlayerList.placeNewPlayer(),
            // which properly registers the player, fires join events, and sends
            // spawn packets to nearby players so they can see the bot.
            boolean addedViaPlayerList = false;
            try {
                Object playerList = getPlayerListMethod.invoke(minecraftServer);
                // placeNewPlayer(Connection, ServerPlayer, CommonListenerCookie)
                // The Connection is already set inside setupFakeConnection;
                // retrieve it from the player's connection field.
                Object sgpl = getConnectionField(serverPlayer);
                if (sgpl != null && commonListenerCookieClass != null) {
                    // Extract the Connection from the ServerGamePacketListenerImpl
                    Object conn = getConnectionFromSgpl(sgpl);
                    Object cookie = createCookieDynamic(gameProfile, clientInfo);
                    if (conn != null && cookie != null) {
                        Method placeMethod = findMethod(playerList.getClass(), "placeNewPlayer", 3);
                        if (placeMethod != null) {
                            placeMethod.setAccessible(true);
                            placeMethod.invoke(playerList, conn, serverPlayer, cookie);
                            addedViaPlayerList = true;
                            FppLogger.debug("Added ServerPlayer via PlayerList.placeNewPlayer()");
                        }
                    }
                }
            } catch (Exception e) {
                FppLogger.debug("placeNewPlayer failed (" + e.getMessage() + "), trying addNewPlayer fallback");
            }

            // Fallback: add directly to the ServerLevel entity list
            if (!addedViaPlayerList) {
                try {
                    Method addMethod = findMethod(serverLevel.getClass(), "addNewPlayer", 1);
                    if (addMethod != null) {
                        addMethod.setAccessible(true);
                        addMethod.invoke(serverLevel, serverPlayer);
                        FppLogger.debug("Added ServerPlayer via ServerLevel.addNewPlayer()");
                    }
                } catch (Exception e) {
                    FppLogger.debug("addNewPlayer also failed: " + e.getMessage());
                }
            }

            // Restore position after placeNewPlayer (it may have teleported to spawn)
            if (setPosMethod != null) {
                setPosMethod.invoke(serverPlayer, x, y, z);
            }

            // Get Bukkit wrapper
            Method getBukkitEntityMethod = serverPlayerClass.getMethod("getBukkitEntity");
            Object bukkitEntity = getBukkitEntityMethod.invoke(serverPlayer);

            if (bukkitEntity instanceof Player result) {
                // placeNewPlayer() may have reset the game mode to the server default
                // (often CREATIVE or ADVENTURE on test servers).  Force SURVIVAL so:
                //  • Mobs can target the bot (spectator/creative players are ignored)
                //  • Drowning works (creative players are immune)
                //  • Fall damage fires (creative players take no fall damage)
                //  • All other survival-specific mechanics work correctly
                result.setGameMode(org.bukkit.GameMode.SURVIVAL);

                FppLogger.info("NMS ServerPlayer spawned: " + name + " (UUID: " + result.getUniqueId() + ")");
                return result;
            }

            return null;

        } catch (Exception e) {
            FppLogger.error("Failed to spawn fake player " + name + ": " + e.getMessage());
            FppLogger.debug("Stack trace: " + java.util.Arrays.toString(e.getStackTrace()));
        }

        return null;
    }

    private static Object createClientInformation() {
        try {
            // Verify critical classes are loaded
            if (chatVisibilityClass == null || humanoidArmClass == null) {
                FppLogger.warn("Required classes not loaded for ClientInformation");
                return null;
            }
            
            if (clientInformationConstructor == null) {
                // Find the 9-parameter constructor and extract the actual ParticleStatus class
                Constructor<?>[] constructors = clientInformationClass.getConstructors();
                for (Constructor<?> ctor : constructors) {
                    if (ctor.getParameterCount() == 9) {
                        Class<?>[] params = ctor.getParameterTypes();
                        particleStatusClass = params[8]; // 9th parameter (ParticleStatus)
                        
                        // Get the constructor with the discovered class
                        clientInformationConstructor = clientInformationClass.getConstructor(
                            String.class, int.class, chatVisibilityClass, boolean.class,
                            int.class, humanoidArmClass, boolean.class, boolean.class,
                            particleStatusClass
                        );
                        
                        // Cache enum values
                        chatVisibilityHidden = chatVisibilityClass.getEnumConstants()[2]; // HIDDEN
                        humanoidArmRight = humanoidArmClass.getEnumConstants()[1]; // RIGHT
                        particleStatusAll = particleStatusClass.getEnumConstants()[0]; // ALL
                        
                        FppLogger.debug("ClientInformation constructor cached (ParticleStatus: " + particleStatusClass.getSimpleName() + ")");
                        break;
                    }
                }
                
                if (clientInformationConstructor == null) {
                    FppLogger.warn("Could not find 9-parameter ClientInformation constructor");
                    return null;
                }
            }

            return clientInformationConstructor.newInstance(
                "en", 2, chatVisibilityHidden, true, 0, humanoidArmRight, false, false, particleStatusAll
            );
        } catch (Exception e) {
            FppLogger.warn("Failed to create ClientInformation: " + e.getMessage());
            return null;
        }
    }

    private static Object createServerPlayer(Object minecraftServer, Object serverLevel, 
                                             Object gameProfile, Object clientInfo) {
        try {
            Constructor<?> serverPlayerConstructor;
            // Try modern constructor (1.19+): ServerPlayer(MinecraftServer, ServerLevel, GameProfile, ClientInformation)
            if (clientInfo != null) {
                try {
                    serverPlayerConstructor = serverPlayerClass.getConstructor(
                        minecraftServerClass, serverLevelClass, gameProfileClass, clientInformationClass
                    );
                    return serverPlayerConstructor.newInstance(minecraftServer, serverLevel, gameProfile, clientInfo);
                } catch (NoSuchMethodException e) {
                    FppLogger.debug("4-arg ServerPlayer constructor not found, trying 3-arg");
                }
            }

            // Try older constructor: ServerPlayer(MinecraftServer, ServerLevel, GameProfile)
            try {
                serverPlayerConstructor = serverPlayerClass.getConstructor(
                    minecraftServerClass, serverLevelClass, gameProfileClass
                );
                return serverPlayerConstructor.newInstance(minecraftServer, serverLevel, gameProfile);
            } catch (NoSuchMethodException e) {
                FppLogger.warn("No compatible ServerPlayer constructor found");
            }

            return null;

        } catch (Exception e) {
            FppLogger.error("Failed to create ServerPlayer instance: " + e.getMessage());
            return null;
        }
    }

    /**
     * Creates a fake network connection backed by a Netty {@code EmbeddedChannel}
     * and wires it into a new {@code ServerGamePacketListenerImpl} assigned to {@code serverPlayer.connection}.
     *
     * <p>The EmbeddedChannel is wrapped in a {@code java.lang.reflect.Proxy} that intercepts
     * {@code flush()} and {@code write*()} calls and discards them instead of forwarding to
     * the real outbound buffer.  This prevents the {@code ChannelOutboundBuffer.addFlush()}
     * {@code NullPointerException} (entry.promise == null) that Netty 4.2.x throws when the
     * Minecraft server calls {@code resumeFlushing()} on every connection every tick.
     */
    private static void setupFakeConnection(Object minecraftServer, Object serverPlayer,
                                            Object gameProfile, Object clientInfo) {
        if (connectionClass == null || commonListenerCookieClass == null
                || serverGamePacketListenerClass == null) {
            FppLogger.debug("Network classes not loaded — skipping fake connection setup");
            return;
        }
        try {
            ClassLoader nmsLoader = minecraftServerClass.getClassLoader();

            // 1. Real EmbeddedChannel — used for isOpen/isActive/pipeline/newPromise delegation
            Class<?> embeddedChannelClass = Class.forName("io.netty.channel.embedded.EmbeddedChannel");
            Object realChannel = embeddedChannelClass.getDeclaredConstructor().newInstance();

            // 2. Wrap with a discard-proxy to prevent flush → addFlush NPE
            Object fakeChannel = createDiscardChannel(realChannel, nmsLoader);

            // 3. PacketFlow.SERVERBOUND
            Class<?> packetFlowClass = nmsLoader.loadClass("net.minecraft.network.protocol.PacketFlow");
            Object serverbound = packetFlowClass.getEnumConstants()[0]; // SERVERBOUND

            // 4. Connection(PacketFlow)
            Constructor<?> connCtor = connectionClass.getDeclaredConstructor(packetFlowClass);
            connCtor.setAccessible(true);
            Object conn = connCtor.newInstance(serverbound);

            // 5. Inject fakeChannel (proxy) into the Connection's channel field
            boolean channelInjected = false;
            for (Field f : getAllDeclaredFields(connectionClass)) {
                if (f.getType().getSimpleName().equals("Channel")) {
                    f.setAccessible(true);
                    f.set(conn, fakeChannel);
                    channelInjected = true;
                    FppLogger.debug("Injected discard-proxy Channel into Connection." + f.getName());
                    break;
                }
            }
            if (!channelInjected) {
                // Fallback: inject the raw EmbeddedChannel if proxy injection failed
                for (Field f : getAllDeclaredFields(connectionClass)) {
                    if (f.getType().getSimpleName().equals("Channel")) {
                        f.setAccessible(true);
                        f.set(conn, realChannel);
                        FppLogger.debug("Injected raw EmbeddedChannel into Connection." + f.getName());
                        break;
                    }
                }
            }

            // 6. Inject a dummy InetSocketAddress so Connection.handleDisconnection() doesn't NPE
            for (Field f : getAllDeclaredFields(connectionClass)) {
                if (java.net.SocketAddress.class.isAssignableFrom(f.getType())) {
                    try {
                        f.setAccessible(true);
                        if (f.get(conn) == null) {
                            f.set(conn, new java.net.InetSocketAddress("127.0.0.1", 25565));
                            FppLogger.debug("Injected dummy InetSocketAddress into Connection." + f.getName());
                        }
                    } catch (Exception ignored) {}
                    break;
                }
            }

            // 7. CommonListenerCookie
            Object cookie = createCookieDynamic(gameProfile, clientInfo);
            if (cookie == null) {
                FppLogger.warn("Could not create CommonListenerCookie — fake connection incomplete");
                return;
            }

            // 8. ServerGamePacketListenerImpl(MinecraftServer, Connection, ServerPlayer, CommonListenerCookie)
            //    Constructor automatically sets serverPlayer.connection = this.
            for (Constructor<?> c : serverGamePacketListenerClass.getDeclaredConstructors()) {
                if (c.getParameterCount() == 4) {
                    c.setAccessible(true);
                    c.newInstance(minecraftServer, conn, serverPlayer, cookie);
                    FppLogger.debug("ServerGamePacketListenerImpl created; connection set on ServerPlayer");
                    return;
                }
            }
            FppLogger.warn("Could not find 4-arg ServerGamePacketListenerImpl constructor");

        } catch (Exception e) {
            FppLogger.warn("setupFakeConnection failed: " + e.getClass().getSimpleName() + ": " + e.getMessage());
        }
    }

    /**
     * Returns a {@link java.lang.reflect.Proxy} that implements {@code io.netty.channel.Channel}
     * and delegates every call to {@code realChannel} — EXCEPT:
     * <ul>
     *   <li>{@code flush()} → no-op, returns proxy (prevents addFlush NPE)</li>
     *   <li>{@code write*(msg [, promise])} → releases msg, succeeds promise, returns promise/void</li>
     * </ul>
     * All other calls ({@code isOpen}, {@code pipeline}, {@code newPromise}, etc.) pass through
     * to the real EmbeddedChannel so the connection health-check and promise creation still work.
     */
    private static Object createDiscardChannel(Object realChannel, ClassLoader loader) {
        try {
            Class<?> channelClass = loader.loadClass("io.netty.channel.Channel");
            Class<?> promiseClass  = loader.loadClass("io.netty.channel.ChannelPromise");

            return java.lang.reflect.Proxy.newProxyInstance(
                loader,
                new Class<?>[]{channelClass},
                (proxy, method, args) -> {
                    String n = method.getName();

                    // ── Discard flush ────────────────────────────────────────────
                    // Channel.flush() returns Channel — return proxy to allow chaining
                    if ("flush".equals(n)) {
                        return proxy;
                    }

                    // ── Discard write / writeAndFlush ────────────────────────────
                    if ("write".equals(n) || "writeAndFlush".equals(n)) {
                        // Release any ref-counted message to prevent memory leaks
                        if (args != null && args.length >= 1 && args[0] != null) {
                            try {
                                Class.forName("io.netty.util.ReferenceCountUtil")
                                     .getMethod("release", Object.class)
                                     .invoke(null, args[0]);
                            } catch (Exception ignored) {}
                        }
                        // Succeed any ChannelPromise provided in args
                        if (args != null) {
                            for (Object arg : args) {
                                if (promiseClass.isInstance(arg)) {
                                    try { arg.getClass().getMethod("trySuccess").invoke(arg); }
                                    catch (Exception ignored) {}
                                    return arg; // ChannelPromise is a ChannelFuture
                                }
                            }
                        }
                        // No promise provided — create one from the real channel and succeed it
                        try {
                            Object promise = realChannel.getClass()
                                    .getMethod("newPromise").invoke(realChannel);
                            promise.getClass().getMethod("setSuccess").invoke(promise);
                            return promise;
                        } catch (Exception ignored) {}
                        return null;
                    }

                    // ── Delegate everything else to real EmbeddedChannel ─────────
                    try {
                        return method.invoke(realChannel, args);
                    } catch (java.lang.reflect.InvocationTargetException e) {
                        Throwable cause = e.getCause();
                        if (cause instanceof Exception ex) throw ex;
                        if (cause instanceof Error err) throw err;
                        throw e;
                    }
                }
            );
        } catch (Exception e) {
            FppLogger.warn("createDiscardChannel failed (" + e.getMessage() + ") — using raw EmbeddedChannel");
            return realChannel; // fallback: NPE risk remains, but at least we tried
        }
    }

    /** Dynamically creates a {@code CommonListenerCookie} by iterating its constructors. */
    private static Object createCookieDynamic(Object gameProfile, Object clientInfo) {
        try {
            Constructor<?>[] ctors = commonListenerCookieClass.getDeclaredConstructors();

            // Log available constructors on first attempt (debug aids)
            FppLogger.info("CommonListenerCookie constructors (" + ctors.length + "):");
            for (Constructor<?> c : ctors) {
                StringBuilder sb = new StringBuilder("  [" + c.getParameterCount() + "] ");
                for (Class<?> p : c.getParameterTypes()) sb.append(p.getSimpleName()).append(", ");
                FppLogger.info(sb.toString());
            }

            for (Constructor<?> c : ctors) {
                c.setAccessible(true);
                Class<?>[] p = c.getParameterTypes();
                // Skip synthetic constructors added by the compiler (e.g. for records)
                if (p.length > 0 && p[p.length - 1].getSimpleName().contains("DefaultConstructorMarker")) continue;
                try {
                    switch (p.length) {
                        case 1 -> { return c.newInstance(gameProfile); }
                        case 2 -> { return c.newInstance(gameProfile, 0); }
                        case 3 -> { return c.newInstance(gameProfile, 0, clientInfo); }
                        case 4 -> { return c.newInstance(gameProfile, 0, clientInfo, false); }
                        case 5 -> { return c.newInstance(gameProfile, 0, clientInfo, false, false); }
                        case 7 -> {
                            // Paper 1.21.11+: (GameProfile, int, ClientInformation, boolean, String, Set, KeepAlive)
                            // Pass null for String (server address) and KeepAlive; empty Set for server cookies.
                            return c.newInstance(gameProfile, 0, clientInfo, false,
                                    null, java.util.Collections.emptySet(), null);
                        }
                    }
                } catch (Exception e) {
                    FppLogger.info("  → [" + p.length + "-arg] failed: " + e.getClass().getSimpleName() + ": " + e.getMessage());
                }
            }
        } catch (Exception e) {
            FppLogger.debug("createCookieDynamic failed: " + e.getMessage());
        }
        FppLogger.warn("createCookieDynamic: no constructor worked for CommonListenerCookie");
        return null;
    }

    /** Returns the value of {@code serverPlayer.connection} (the ServerGamePacketListenerImpl). */
    private static Object getConnectionField(Object serverPlayer) {
        try {
            for (Field f : getAllDeclaredFields(serverPlayerClass)) {
                if (serverGamePacketListenerClass.isAssignableFrom(f.getType())) {
                    f.setAccessible(true);
                    return f.get(serverPlayer);
                }
            }
        } catch (Exception ignored) {}
        return null;
    }

    /** Returns the {@code Connection} object held inside a {@code ServerGamePacketListenerImpl}. */
    private static Object getConnectionFromSgpl(Object sgpl) {
        try {
            for (Field f : getAllDeclaredFields(sgpl.getClass())) {
                if (connectionClass.isAssignableFrom(f.getType())) {
                    f.setAccessible(true);
                    return f.get(sgpl);
                }
            }
        } catch (Exception ignored) {}
        return null;
    }

    /** Finds a method on a class (or its supers) by name and parameter count. */
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

    /** Collects all declared fields from a class and all its superclasses. */
    private static java.util.List<Field> getAllDeclaredFields(Class<?> clazz) {
        java.util.List<Field> fields = new java.util.ArrayList<>();
        Class<?> cur = clazz;
        while (cur != null && cur != Object.class) {
            java.util.Collections.addAll(fields, cur.getDeclaredFields());
            cur = cur.getSuperclass();
        }
        return fields;
    }

    /**
     * Directly sets the NMS entity's position via {@code Entity.setPos(x, y, z)}.
     *
     * <p>This bypasses client-authoritative movement — the entity's server-side
     * coordinates are updated immediately, and the entity tracker then broadcasts
     * the position change to all nearby players on its next update cycle.
     *
     * <p>Used by the manual physics loop in {@code BotCollisionListener} to move
     * NMS fake-player bots that have a fake (EmbeddedChannel) connection that
     * discards all outgoing packets, including velocity and position packets.
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
     * Manual physics tick for a fake bot player.
     *
     * <p>NMS {@code ServerPlayer} movement is <em>client-authoritative</em>: the server
     * only updates position when the client sends {@code ServerboundMovePlayerPacket}.
     * Our fake connection discards every inbound packet, so without this method the bot
     * stays frozen in mid-air, ignores gravity, and cannot be knocked back.
     *
     * <p>Each tick this method:
     * <ol>
     *   <li>Reads current velocity from {@link Player#getVelocity()} (backed by NMS
     *       {@code Entity.deltaMovement}, so any impulse from {@code applyImpulse()} or
     *       vanilla knockback is already captured).</li>
     *   <li>Applies Minecraft gravity ({@code vy = (vy - 0.08) * 0.98}) when airborne.</li>
     *   <li>Integrates velocity into a new position, handling floor and ceiling collisions
     *       with a 5-point footprint check.</li>
     *   <li>Writes the new position via NMS {@code setPos()} so the entity tracker
     *       broadcasts {@code ClientboundTeleportEntityPacket} to nearby real players.</li>
     *   <li>Decays velocity (friction) and writes it back via {@link Player#setVelocity(org.bukkit.util.Vector)},
     *       which also sets {@code hurtMarked = true} so the tracker sends a
     *       {@code ClientboundSetEntityMotionPacket} to nearby players (visual knockback).</li>
     * </ol>
     */
    public static void tickPhysics(Player bot) {
        if (!initialized || craftPlayerGetHandleMethod == null || setPosMethod == null) return;
        try {
            Location loc = bot.getLocation();
            org.bukkit.World w = loc.getWorld();
            if (w == null) return;
            org.bukkit.util.Vector vel = bot.getVelocity();
            float fallDistance = bot.getFallDistance();
            boolean wasOnGround = checkOnGround(loc);
            boolean inWater = isTouchingWater(loc);
            boolean inLava  = !inWater && isTouchingLava(loc);
            boolean inFluid = inWater || inLava;

            double vx = vel.getX();
            double vy = vel.getY();
            double vz = vel.getZ();

            // Skip only when the bot is truly stationary AND still supported. If the block
            // below was broken, or the bot is floating in a fluid, we must continue so
            // gravity / buoyancy can start from an idle state.
            if (Math.abs(vx) < 1e-6 && Math.abs(vz) < 1e-6 && Math.abs(vy) < 1e-6 && wasOnGround && !inFluid) return;

            boolean onGround = wasOnGround;

            // Gravity / fluid buoyancy
            if (inWater) {
                // Water: make bots behave like they're holding jump. Deep in water they gain
                // a steady upward buoyancy, and near the surface they hover/bob instead of
                // slowly sinking like dead weight.
                if (isNearWaterSurface(loc)) {
                    vy = (vy + 0.03) * 0.8;
                    if (vy > 0.08) vy = 0.08;
                    if (vy < -0.02) vy = -0.02;
                } else {
                    vy = (vy + 0.045) * 0.8;
                    if (vy > 0.12) vy = 0.12;
                    if (vy < -0.08) vy = -0.08;
                }
            } else if (inLava) {
                // Lava: even heavier drag and slower sinking.
                vy = (vy - 0.02) * 0.5;
                if (vy < -0.12) vy = -0.12;
            } else if (!onGround) {
                vy = (vy - 0.08) * 0.98;
            } else if (vy <= 0) {
                vy = 0;
            }

            // Safety clamp
            vx = Math.max(-4.0, Math.min(4.0, vx));
            vy = Math.max(-4.0, Math.min(4.0, vy));
            vz = Math.max(-4.0, Math.min(4.0, vz));

            final double halfWidth = 0.30; // vanilla player hitbox: 0.6 wide
            final double height = 1.80;    // vanilla player hitbox: 1.8 tall

            double nx = loc.getX() + vx;
            double ny = loc.getY() + vy;
            double nz = loc.getZ() + vz;

            // Horizontal collision: resolve X then Z using a player-sized AABB.
            // This prevents bots from phasing through walls when pushed.
            if (vx != 0.0 && hasSolidCollision(w, nx, loc.getY(), loc.getZ(), halfWidth, height)) {
                nx = loc.getX();
                vx = 0.0;
            }
            if (vz != 0.0 && hasSolidCollision(w, nx, loc.getY(), nz, halfWidth, height)) {
                nz = loc.getZ();
                vz = 0.0;
            }

            // Floor collision when falling
            if (vy < 0) {
                if (checkOnGround(new Location(loc.getWorld(), nx, ny, nz))) {
                    ny = Math.floor(ny + 1.0);   // snap to block surface
                    vy = 0;
                    onGround = true;
                }
            }

            // Ceiling collision when jumping/flying up  (player is ~1.8 blocks tall)
            if (vy > 0) {
                if (hasSolidCollision(w, nx, ny, nz, halfWidth, height)) {
                    ny = loc.getY();
                    vy = 0;
                }
            }

            // Apply position via NMS setPos so entity tracker broadcasts it
            Object nmsPlayer = craftPlayerGetHandleMethod.invoke(bot);
            setPosMethod.invoke(nmsPlayer, nx, ny, nz);

            // Recompute ground state from the final resolved position. Using the stale state
            // from the start of the tick causes launched bots to be treated as grounded,
            // which immediately kills upward/player-hit knockback and leaves idle bots hovering.
            boolean finalOnGround = onGround || checkOnGround(new Location(w, nx, ny, nz));

            // Emulate vanilla fall-distance accumulation because fake players bypass the
            // normal client movement packet pipeline. Without this, landing never produces
            // fall damage even though the bot visibly falls.
            if (inFluid) {
                // Water/lava should break or heavily dampen a fall; vanilla players don't keep
                // accumulating fall damage while submerged.
                bot.setFallDistance(0.0f);
            } else if (!wasOnGround && !finalOnGround && ny < loc.getY()) {
                fallDistance += (float) (loc.getY() - ny);
                bot.setFallDistance(fallDistance);
            } else if (!wasOnGround && finalOnGround) {
                if (fallDistance > 3.0f) {
                    applyManualFallDamage(bot, fallDistance);
                }
                bot.setFallDistance(0.0f);
            } else if (finalOnGround) {
                bot.setFallDistance(0.0f);
            }

            // Friction
            double hFriction;
            if (inWater) {
                hFriction = 0.80;
            } else if (inLava) {
                hFriction = 0.50;
            } else {
                hFriction = finalOnGround ? 0.546 : 0.91;
            }
            vx *= hFriction;
            vz *= hFriction;
            if (finalOnGround) vy = 0;

            // Write velocity back; hurtMarked flag makes tracker broadcast it to nearby players
            bot.setVelocity(new org.bukkit.util.Vector(vx, vy, vz));

        } catch (Exception e) {
            FppLogger.debug("NmsPlayerSpawner.tickPhysics failed: " + e.getMessage());
        }
    }

    /**
     * Applies vanilla-like fall damage to a fake player after the custom physics loop
     * detects a landing. Bukkit does not generate fall damage automatically here because
     * the bot was moved via NMS setPos() instead of real inbound movement packets.
     *
     * <p>Uses the non-deprecated Bukkit {@code Damageable#damage(double)} path instead of
     * manually constructing an {@code EntityDamageEvent}, which is deprecated for removal
     * in modern Paper/Bukkit APIs.</p>
     */
    private static void applyManualFallDamage(Player bot, float fallDistance) {
        try {
            double damage = Math.ceil(fallDistance - 3.0f);
            if (damage <= 0.0) return;

            bot.damage(damage);
        } catch (Exception e) {
            FppLogger.debug("applyManualFallDamage failed: " + e.getMessage());
        }
    }

    /** Returns true when a player-sized AABB at (x,y,z) intersects any solid block. */
    private static boolean hasSolidCollision(org.bukkit.World world, double x, double y, double z,
                                             double halfWidth, double height) {
        int minX = (int) Math.floor(x - halfWidth);
        int maxX = (int) Math.floor(x + halfWidth);
        int minY = (int) Math.floor(y);
        int maxY = (int) Math.floor(y + height - 1e-6);
        int minZ = (int) Math.floor(z - halfWidth);
        int maxZ = (int) Math.floor(z + halfWidth);

        for (int bx = minX; bx <= maxX; bx++) {
            for (int by = minY; by <= maxY; by++) {
                for (int bz = minZ; bz <= maxZ; bz++) {
                    if (world.getBlockAt(bx, by, bz).getType().isSolid()) return true;
                }
            }
        }
        return false;
    }

    /**
     * Returns {@code true} if any of the five footprint sample points (centre + four
     * corners at ±0.29 m) is directly above a solid block, indicating the entity is
     * standing on or very close to the ground.
     */
    private static boolean checkOnGround(Location loc) {
        org.bukkit.World w = loc.getWorld();
        if (w == null) return true;
        double x = loc.getX(), y = loc.getY(), z = loc.getZ();
        double r = 0.29;
        double checkY = y - 0.05;
        return isSolid(w, x,     checkY, z    )
            || isSolid(w, x + r, checkY, z + r)
            || isSolid(w, x - r, checkY, z + r)
            || isSolid(w, x + r, checkY, z - r)
            || isSolid(w, x - r, checkY, z - r);
    }

    private static boolean isSolid(org.bukkit.World world, double x, double y, double z) {
        return world.getBlockAt(
                (int) Math.floor(x),
                (int) Math.floor(y),
                (int) Math.floor(z)
        ).getType().isSolid();
    }

    /** Returns true when the sampled player volume intersects water (including waterlogged blocks). */
    private static boolean isTouchingWater(Location loc) {
        return sampleFluid(loc, Material.WATER, true);
    }

    /**
     * Returns true when the bot is in water and close to the top surface, so it should
     * hover/bob instead of continuing to rise hard.
     */
    private static boolean isNearWaterSurface(Location loc) {
        World w = loc.getWorld();
        if (w == null) return false;

        // Mid-body is still in water, but upper head space is out of water → near surface.
        boolean bodyInWater = sampleFluid(new Location(w, loc.getX(), loc.getY() + 0.90, loc.getZ()), Material.WATER, true);
        boolean headInWater = sampleFluid(new Location(w, loc.getX(), loc.getY() + 1.65, loc.getZ()), Material.WATER, true);
        return bodyInWater && !headInWater;
    }

    /** Returns true when the sampled player volume intersects lava. */
    private static boolean isTouchingLava(Location loc) {
        return sampleFluid(loc, Material.LAVA, false);
    }

    /**
     * Samples the player body volume (feet / waist / head at center + corners) for a fluid.
     * This lets fake players react to water/lava like real players instead of treating fluids as air.
     */
    private static boolean sampleFluid(Location loc, Material fluidType, boolean allowWaterlogged) {
        World w = loc.getWorld();
        if (w == null) return false;

        double x = loc.getX();
        double y = loc.getY();
        double z = loc.getZ();
        double r = 0.29;
        double[] sampleY = { y + 0.10, y + 0.90, y + 1.45 };
        double[][] sampleXZ = {
                { x,     z     },
                { x + r, z + r },
                { x - r, z + r },
                { x + r, z - r },
                { x - r, z - r }
        };

        for (double sy : sampleY) {
            for (double[] p : sampleXZ) {
                Block block = w.getBlockAt(
                        (int) Math.floor(p[0]),
                        (int) Math.floor(sy),
                        (int) Math.floor(p[1]));
                Material type = block.getType();
                if (type == fluidType) return true;
                if (allowWaterlogged) {
                    BlockData data = block.getBlockData();
                    if (data instanceof Waterlogged waterlogged && waterlogged.isWaterlogged()) return true;
                    if (type == Material.BUBBLE_COLUMN || type == Material.KELP || type == Material.KELP_PLANT
                            || type == Material.SEAGRASS || type == Material.TALL_SEAGRASS) return true;
                }
            }
        }
        return false;
    }

    /**
     * Remove a fake player from the server.
     */
    public static void removeFakePlayer(Player player) {
        if (player == null) return;
        try {
            if (!initialized) init();
            if (craftPlayerGetHandleMethod == null || getServerMethod == null || getPlayerListMethod == null) {
                throw new IllegalStateException("NMS reflection not ready for fake-player removal");
            }

            // Mirror the spawn path (PlayerList.placeNewPlayer) with the normal
            // server-managed player removal path. This avoids Bukkit Player.remove(),
            // which Paper rejects for player entities, and also avoids kick()/disconnect,
            // which prints "lost connection:" in console.
            Object nmsPlayer = craftPlayerGetHandleMethod.invoke(player);
            Object craftServer = Bukkit.getServer();
            Object minecraftServer = getServerMethod.invoke(craftServer);
            Object playerList = getPlayerListMethod.invoke(minecraftServer);

            Method removeOneArg = null;
            Method removeTwoArg = null;
            for (Method m : playerList.getClass().getMethods()) {
                if (!m.getName().equals("remove")) continue;
                Class<?>[] params = m.getParameterTypes();
                if (params.length >= 1 && params[0].isAssignableFrom(nmsPlayer.getClass())) {
                    if (params.length == 1) removeOneArg = m;
                    else if (params.length == 2) removeTwoArg = m;
                }
            }

            if (removeOneArg != null) {
                removeOneArg.setAccessible(true);
                removeOneArg.invoke(playerList, nmsPlayer);
            } else if (removeTwoArg != null) {
                removeTwoArg.setAccessible(true);
                removeTwoArg.invoke(playerList, nmsPlayer, null);
            } else {
                throw new NoSuchMethodException("PlayerList.remove(ServerPlayer) not found");
            }
        } catch (Exception e) {
            FppLogger.error("Failed to remove fake player: " + e.getMessage());
        }
    }
}

