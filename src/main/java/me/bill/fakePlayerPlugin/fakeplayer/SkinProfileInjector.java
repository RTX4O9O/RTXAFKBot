package me.bill.fakePlayerPlugin.fakeplayer;

import java.lang.reflect.Constructor;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

/**
 * Shared reflection helper that injects a {@code textures} property into an
 * authlib {@code GameProfile}.
 *
 * <p>Both local NMS body spawns and raw tab-list packet profiles use the same
 * code path so a resolved {@link SkinProfile} is applied consistently
 * everywhere.
 */
public final class SkinProfileInjector {

    private static final String TEXTURES_KEY = "textures";

    private SkinProfileInjector() {}

    public static void apply(Object gameProfile, SkinProfile skin) throws Exception {
        if (skin == null) return;
        apply(gameProfile, skin.getValue(), skin.getSignature());
    }

    public static void apply(Object gameProfile, String value, String signature) throws Exception {
        if (gameProfile == null || value == null || value.isBlank()) return;

        ClassLoader cl = gameProfile.getClass().getClassLoader();
        Class<?> propertyClass = cl.loadClass("com.mojang.authlib.properties.Property");
        Object property = createProperty(propertyClass, value, signature);

        Object propertyMap = resolvePropertyMap(gameProfile);
        if (tryApply(propertyMap, property)) return;

        throw new IllegalStateException("Could not find a writable PropertyMap in GameProfile");
    }

    private static Object createProperty(Class<?> propertyClass, String value, String signature) throws Exception {
        if (signature == null || signature.isBlank()) {
            try {
                Constructor<?> ctor = propertyClass.getDeclaredConstructor(String.class, String.class);
                ctor.setAccessible(true);
                return ctor.newInstance(TEXTURES_KEY, value);
            } catch (NoSuchMethodException ignored) {
                Constructor<?> ctor = propertyClass.getDeclaredConstructor(String.class, String.class, String.class);
                ctor.setAccessible(true);
                return ctor.newInstance(TEXTURES_KEY, value, "");
            }
        }

        try {
            Constructor<?> ctor = propertyClass.getDeclaredConstructor(String.class, String.class, String.class);
            ctor.setAccessible(true);
            return ctor.newInstance(TEXTURES_KEY, value, signature);
        } catch (NoSuchMethodException ignored) {
            Constructor<?> ctor = propertyClass.getDeclaredConstructor(String.class, String.class);
            ctor.setAccessible(true);
            return ctor.newInstance(TEXTURES_KEY, value);
        }
    }

    private static Object resolvePropertyMap(Object gameProfile) throws Exception {
        Object propertyMap = invokeNoArg(gameProfile, "getProperties");
        if (propertyMap != null) return propertyMap;

        propertyMap = invokeNoArg(gameProfile, "properties");
        if (propertyMap != null) return propertyMap;

        for (Field field : getAllFields(gameProfile.getClass())) {
            if (!"properties".equals(field.getName())) continue;
            field.setAccessible(true);
            Object direct = field.get(gameProfile);
            if (direct != null) return direct;
        }

        throw new NoSuchMethodException(gameProfile.getClass().getName() + ".getProperties()");
    }

    private static Object invokeNoArg(Object target, String methodName) {
        for (Method method : getAllMethods(target.getClass())) {
            if (!method.getName().equals(methodName) || method.getParameterCount() != 0) continue;
            try {
                method.setAccessible(true);
                return method.invoke(target);
            } catch (Exception ignored) {
            }
        }
        return null;
    }

    private static boolean tryApply(Object candidate, Object property) {
        if (candidate == null) return false;

        clearExistingTextures(candidate);
        if (invokePut(candidate, property)) return true;

        for (Field field : getAllFields(candidate.getClass())) {
            if (Modifier.isStatic(field.getModifiers()) || field.isSynthetic()) continue;
            try {
                field.setAccessible(true);
                Object nested = field.get(candidate);
                if (nested == null || nested == candidate) continue;
                clearExistingTextures(nested);
                if (invokePut(nested, property)) return true;
            } catch (Exception ignored) {
            }
        }
        return false;
    }

    private static boolean invokePut(Object propertyMap, Object property) {
        for (Method method : getAllMethods(propertyMap.getClass())) {
            if (!"put".equals(method.getName()) || method.getParameterCount() != 2) continue;
            try {
                method.setAccessible(true);
                method.invoke(propertyMap, TEXTURES_KEY, property);
                return true;
            } catch (Exception ignored) {
            }
        }
        return false;
    }

    private static void clearExistingTextures(Object propertyMap) {
        for (Method method : getAllMethods(propertyMap.getClass())) {
            if (!"removeAll".equals(method.getName()) || method.getParameterCount() != 1) continue;
            try {
                method.setAccessible(true);
                method.invoke(propertyMap, TEXTURES_KEY);
                return;
            } catch (Exception ignored) {
            }
        }
    }

    private static List<Field> getAllFields(Class<?> type) {
        List<Field> fields = new ArrayList<>();
        for (Class<?> current = type; current != null && current != Object.class; current = current.getSuperclass()) {
            fields.addAll(Arrays.asList(current.getDeclaredFields()));
        }
        return fields;
    }

    private static List<Method> getAllMethods(Class<?> type) {
        List<Method> methods = new ArrayList<>();
        for (Class<?> current = type; current != null && current != Object.class; current = current.getSuperclass()) {
            methods.addAll(Arrays.asList(current.getDeclaredMethods()));
        }
        methods.addAll(Arrays.asList(type.getMethods()));
        return methods;
    }
}

