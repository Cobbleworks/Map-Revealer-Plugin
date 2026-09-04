package com.github.maprevealer.util;

import org.bukkit.map.MapView;

import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.Arrays;

/**
 * Writes the vanilla map color buffer and marks only changed pixels dirty.
 */
public final class MapDataAccess {
    private static final int PIXELS = 128 * 128;

    private MapDataAccess() { }

    public static int write(MapView mapView, byte[] replacement) {
        try {
            return writeBuffer(findWorldMap(mapView), replacement);
        } catch (ReflectiveOperationException exception) {
            throw new IllegalStateException("The server's map data implementation is not supported.", exception);
        }
    }

    static int writeBuffer(Object worldMap, byte[] replacement) {
        if (replacement.length != PIXELS) {
            throw new IllegalArgumentException("A map color buffer must contain exactly " + PIXELS + " pixels.");
        }
        try {
            Field colorsField = findColorsField(worldMap);
            byte[] colors = (byte[]) colorsField.get(worldMap);
            Method dirty = findDirtyMethod(worldMap.getClass());
            if (dirty == null) throw new NoSuchMethodException("setColorsDirty(int, int)");
            int changed = 0;
            for (int index = 0; index < PIXELS; index++) {
                if (colors[index] == replacement[index]) continue;
                colors[index] = replacement[index];
                changed++;
                dirty.invoke(worldMap, index % 128, index / 128);
            }
            return changed;
        } catch (ReflectiveOperationException exception) {
            throw new IllegalStateException("The server's map data implementation is not supported.", exception);
        }
    }

    public static byte[] read(MapView mapView) {
        try {
            Object worldMap = findWorldMap(mapView);
            return Arrays.copyOf((byte[]) findColorsField(worldMap).get(worldMap), PIXELS);
        } catch (ReflectiveOperationException exception) {
            throw new IllegalStateException("The server's map data implementation is not supported.", exception);
        }
    }

    private static Object findWorldMap(MapView mapView) throws ReflectiveOperationException {
        Field named = findField(mapView.getClass(), "worldMap");
        if (named != null) return named.get(mapView);
        for (Class<?> type = mapView.getClass(); type != null; type = type.getSuperclass()) {
            for (Field field : type.getDeclaredFields()) {
                field.setAccessible(true);
                Object candidate = field.get(mapView);
                if (candidate != null && findColorsFieldOrNull(candidate) != null) return candidate;
            }
        }
        throw new NoSuchFieldException("worldMap");
    }

    private static Field findColorsField(Object worldMap) throws NoSuchFieldException {
        Field field = findColorsFieldOrNull(worldMap);
        if (field == null) throw new NoSuchFieldException("colors");
        return field;
    }

    private static Field findColorsFieldOrNull(Object worldMap) {
        Field named = findField(worldMap.getClass(), "colors");
        if (isMapBuffer(named, worldMap)) return named;
        for (Class<?> type = worldMap.getClass(); type != null; type = type.getSuperclass()) {
            for (Field field : type.getDeclaredFields()) {
                if (field.getType() != byte[].class) continue;
                field.setAccessible(true);
                if (isMapBuffer(field, worldMap)) return field;
            }
        }
        return null;
    }

    private static boolean isMapBuffer(Field field, Object owner) {
        if (field == null || field.getType() != byte[].class) return false;
        try {
            byte[] value = (byte[]) field.get(owner);
            return value != null && value.length >= PIXELS;
        } catch (IllegalAccessException ignored) {
            return false;
        }
    }

    private static Field findField(Class<?> type, String name) {
        for (Class<?> current = type; current != null; current = current.getSuperclass()) {
            try {
                Field field = current.getDeclaredField(name);
                field.setAccessible(true);
                return field;
            } catch (NoSuchFieldException ignored) { }
        }
        return null;
    }

    private static Method findDirtyMethod(Class<?> type) {
        for (Class<?> current = type; current != null; current = current.getSuperclass()) {
            for (String name : new String[]{"setColorsDirty", "a"}) {
                try {
                    Method method = current.getDeclaredMethod(name, int.class, int.class);
                    method.setAccessible(true);
                    return method;
                } catch (NoSuchMethodException ignored) { }
            }
        }
        return null;
    }
}
