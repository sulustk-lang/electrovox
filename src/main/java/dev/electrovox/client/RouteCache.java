package dev.electrovox.client;

import com.george_vi.electroenergetics.foundation.nodes.InWorldNodeConnection;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.phys.Vec3;

import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Caches the geometry of each wire.
 *
 * <p>Snapping to block faces samples the world every 1/16 block, which is far too
 * expensive to redo for every wire on every frame. The cache key is the
 * connection plus its endpoints, so an entry is reused until the wire moves
 * noticeably or goes stale, and a wire on a moving Sable sub-level does not
 * re-route every tick.</p>
 */
public final class RouteCache {
    /** Re-route when an endpoint moved more than this. */
    private static final double MOVE_EPSILON = 0.02D;
    /** Re-route at least this often even if nothing moved. */
    private static final long MAX_AGE_MS = 2000L;

    private record Entry(Vec3 a, Vec3 b, List<Vec3> route, long stamp) {
        boolean matches(Vec3 otherA, Vec3 otherB) {
            return a.distanceToSqr(otherA) < MOVE_EPSILON * MOVE_EPSILON
                    && b.distanceToSqr(otherB) < MOVE_EPSILON * MOVE_EPSILON;
        }
    }

    private static final Map<InWorldNodeConnection, Entry> CACHE = new ConcurrentHashMap<>();

    private RouteCache() {
    }

    public static List<Vec3> route(BlockGetter level, InWorldNodeConnection key,
                                   Vec3 a, Vec3 b) {
        if (key == null || a == null || b == null) {
            return WireGeometry.polyline(level, a, b);
        }

        long now = System.currentTimeMillis();
        Entry cached = CACHE.get(key);
        if (cached != null && cached.matches(a, b) && now - cached.stamp() < MAX_AGE_MS) {
            return cached.route();
        }

        List<Vec3> route = WireGeometry.polyline(level, a, b);
        CACHE.put(key, new Entry(a, b, route, now));
        return route;
    }

    public static void forget(InWorldNodeConnection key) {
        if (key != null) {
            CACHE.remove(key);
        }
    }

    public static void clear() {
        CACHE.clear();
    }
}
