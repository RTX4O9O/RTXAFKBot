package me.bill.fakePlayerPlugin.fakeplayer;

import org.bukkit.Bukkit;

import java.lang.reflect.Constructor;
import java.lang.reflect.Field;
import java.lang.reflect.Method;

/**
 * NMS reflection helper - provides access to server internals without direct NMS imports.
 * Version-independent by using reflection to find classes at runtime.
 */
public final class NmsReflection {

    private static boolean initialized = false;
    private static boolean failed = false;

    // Cached classes
    private static Class<?> craftServerClass;
    private static Class<?> craftWorldClass;
    private static Class<?> minecraftServerClass;
    private static Class<?> serverLevelClass;
    private static Class<?> serverPlayerClass;
    private static Class<?> gameProfileClass;
    private static Class<?> clientInformationClass;
    private static Class<?> commonListenerCookieClass;
    
    // Cached methods
    private static Method craftServerGetHandle;
    private static Method craftWorldGetHandle;
    
    // Cached constructors
    private static Constructor<?> gameProfileConstructor;
    private static Constructor<?> serverPlayerConstructor;
    private static Constructor<?> clientInformationConstructor;
    private static Constructor<?> commonListenerCookieConstructor;

    private NmsReflection() {}

    /**
     * Initialize NMS reflection. Safe to call multiple times.
     */
    public static synchronized void init() {
        if (initialized || failed) return;

        try {
            // Get version string for NMS/CraftBukkit packages
            String version = Bukkit.getServer().getClass().getPackage().getName().split("\\.")[3];
            
            // Load CraftBukkit classes
            craftServerClass = Class.forName("org.bukkit.craftbukkit." + version + ".CraftServer");
            craftWorldClass = Class.forName("org.bukkit.craftbukkit." + version + ".CraftWorld");
            
            // Load NMS classes
            ClassLoader nmsLoader = craftServerClass.getClassLoader();
            minecraftServerClass = nmsLoader.loadClass("net.minecraft.server.MinecraftServer");
            serverLevelClass = nmsLoader.loadClass("net.minecraft.server.level.WorldServer"); // Spigot name
            serverPlayerClass = nmsLoader.loadClass("net.minecraft.server.level.EntityPlayer"); // Spigot name
            
            // Load Mojang authlib (should be available)
            gameProfileClass = Class.forName("com.mojang.authlib.GameProfile");
            
            // Cache methods
            craftServerGetHandle = craftServerClass.getMethod("getServer");
            craftWorldGetHandle = craftWorldClass.getMethod("getHandle");
            
            // Cache constructors
            gameProfileConstructor = gameProfileClass.getConstructor(java.util.UUID.class, String.class);
            
            initialized = true;
        } catch (Exception e) {
            failed = true;
            throw new RuntimeException("Failed to initialize NMS reflection", e);
        }
    }

    public static boolean isAvailable() {
        if (!initialized && !failed) init();
        return initialized;
    }

    public static Class<?> getServerPlayerClass() {
        if (!initialized) init();
        return serverPlayerClass;
    }

    public static Class<?> getGameProfileClass() {
        if (!initialized) init();
        return gameProfileClass;
    }

    public static Object createGameProfile(java.util.UUID uuid, String name) {
        try {
            if (!initialized) init();
            return gameProfileConstructor.newInstance(uuid, name);
        } catch (Exception e) {
            throw new RuntimeException("Failed to create GameProfile", e);
        }
    }

    public static Object getMinecraftServer() {
        try {
            if (!initialized) init();
            return craftServerGetHandle.invoke(Bukkit.getServer());
        } catch (Exception e) {
            throw new RuntimeException("Failed to get MinecraftServer", e);
        }
    }

    public static Object getServerLevel(org.bukkit.World world) {
        try {
            if (!initialized) init();
            Object craftWorld = craftWorldClass.cast(world);
            return craftWorldGetHandle.invoke(craftWorld);
        } catch (Exception e) {
            throw new RuntimeException("Failed to get ServerLevel", e);
        }
    }
}

