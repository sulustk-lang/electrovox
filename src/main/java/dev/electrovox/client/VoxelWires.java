package dev.electrovox.client;

import com.george_vi.electroenergetics.foundation.nodes.InWorldNodeConnection;
import dev.electrovox.Electrovox;

import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Tracks which connections are drawn voxel-style.
 *
 * <p>Two rules decide it. A wire is voxel when the player placed it in voxel
 * mode, and always when it ends on an anchor connector. The second rule needs no
 * stored state: anchors are detached nodes, so a spliced or re-anchored wire is
 * voxel again after a relog. Everything else keeps the addon's Flywheel look.</p>
 */
public final class VoxelWires {
    private static final Set<InWorldNodeConnection> VOXEL = ConcurrentHashMap.newKeySet();

    /** Set when the player places a wire with the voxel key held. */
    private static volatile boolean pending;
    private static volatile long pendingAt;

    private VoxelWires() {
    }

    public static void markPending() {
        pending = true;
        pendingAt = System.currentTimeMillis();
    }

    public static boolean consumePending() {
        if (!pending) {
            return false;
        }
        pending = false;
        return System.currentTimeMillis() - pendingAt < 2000L;
    }

    public static void bake(InWorldNodeConnection connection) {
        if (connection != null && VOXEL.add(connection)) {
            Electrovox.LOGGER.debug("Wire baked as voxel: {}", connection);
        }
    }

    public static boolean isVoxel(InWorldNodeConnection connection) {
        if (connection == null) {
            return false;
        }
        if (ElectrovoxConfig.alwaysVoxel()) {
            return true;
        }
        if (VOXEL.contains(connection)) {
            return true;
        }
        // Anything attached to a connector is voxel, so spliced wires and wires
        // that outlive a relog keep the voxel look without extra bookkeeping.
        return VoxelAnchors.isDetached(connection);
    }

    public static void forget(InWorldNodeConnection connection) {
        if (connection != null) {
            VOXEL.remove(connection);
        }
    }

    public static void clear() {
        VOXEL.clear();
    }
}
