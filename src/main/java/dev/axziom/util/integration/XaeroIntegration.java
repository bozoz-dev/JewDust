package dev.axziom.util.integration;

import net.minecraft.resources.ResourceKey;
import net.minecraft.world.level.Level;

import java.lang.reflect.Constructor;
import java.lang.reflect.Field;
import java.lang.reflect.Method;

/** Optional Xaero/XaeroPlus bridge. It has no compile-time dependency on either mod. */
public final class XaeroIntegration {
    private XaeroIntegration() {
    }

    public static boolean xaeroPlusAvailable() {
        try {
            Class.forName("xaeroplus.module.ModuleManager");
            return true;
        } catch (Throwable ignored) {
            return false;
        }
    }

    public static boolean isNewChunk(int x, int z, ResourceKey<Level> dimension) {
        Object module = module("xaeroplus.module.impl.PaletteNewChunks");
        return module != null && invokeChunkPredicate(module, "isNewChunk", x, z, dimension);
    }

    public static boolean isOldChunk(int x, int z, ResourceKey<Level> dimension) {
        Object module = module("xaeroplus.module.impl.OldChunks");
        return module != null && invokeChunkPredicate(module, "isOldChunk", x, z, dimension);
    }

    public static boolean addWaypoint(int x, int y, int z, String name, String symbol, int colour) {
        try {
            Class<?> builtIns = Class.forName("xaero.hud.minimap.BuildInHudModules");
            Field minimapField = builtIns.getField("MINIMAP");
            Object minimap = minimapField.get(null);
            Object session = minimap.getClass().getMethod("getCurrentSession").invoke(minimap);
            Object worldManager = session.getClass().getMethod("getWorldManager").invoke(session);
            Object currentWorld = worldManager.getClass().getMethod("getCurrentWorld").invoke(worldManager);
            Object set = currentWorld.getClass().getMethod("getCurrentWaypointSet").invoke(currentWorld);
            if (set == null) return false;

            for (Object waypoint : (Iterable<?>) set.getClass().getMethod("getWaypoints").invoke(set)) {
                int wx = ((Number) waypoint.getClass().getMethod("getX").invoke(waypoint)).intValue();
                int wz = ((Number) waypoint.getClass().getMethod("getZ").invoke(waypoint)).intValue();
                if (wx == x && wz == z) return true;
            }

            Class<?> waypointClass = Class.forName("xaero.common.minimap.waypoints.Waypoint");
            Constructor<?> constructor = waypointClass.getConstructor(int.class, int.class, int.class,
                    String.class, String.class, int.class, int.class, boolean.class);
            Object waypoint = constructor.newInstance(x, y, z, name, symbol, colour, 0, false);
            Method add = findOneArg(set.getClass(), "add");
            if (add == null) return false;
            add.invoke(set, waypoint);
            requestRefresh();
            return true;
        } catch (Throwable ignored) {
            return false;
        }
    }

    private static void requestRefresh() {
        try {
            Class<?> supportMods = Class.forName("xaero.map.mods.SupportMods");
            Object minimapSupport = supportMods.getField("xaeroMinimap").get(null);
            minimapSupport.getClass().getMethod("requestWaypointsRefresh").invoke(minimapSupport);
        } catch (Throwable ignored) {
        }
    }

    private static Object module(String className) {
        try {
            Class<?> moduleClass = Class.forName(className);
            Class<?> manager = Class.forName("xaeroplus.module.ModuleManager");
            return manager.getMethod("getModule", Class.class).invoke(null, moduleClass);
        } catch (Throwable ignored) {
            return null;
        }
    }

    private static boolean invokeChunkPredicate(Object module, String name, int x, int z, ResourceKey<Level> dimension) {
        try {
            Object value = module.getClass().getMethod(name, int.class, int.class, ResourceKey.class)
                    .invoke(module, x, z, dimension);
            return value instanceof Boolean bool && bool;
        } catch (Throwable ignored) {
            return false;
        }
    }

    private static Method findOneArg(Class<?> type, String name) {
        for (Method method : type.getMethods()) {
            if (method.getName().equals(name) && method.getParameterCount() == 1) return method;
        }
        return null;
    }
}
