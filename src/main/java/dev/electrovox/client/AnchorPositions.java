package dev.electrovox.client;

import com.george_vi.electroenergetics.foundation.nodes.InWorldNode;
import com.george_vi.electroenergetics.simulation.infrastructure.detached_nodes.DetachedNodeHelper;
import dev.ryanhcode.sable.companion.ClientSubLevelAccess;
import dev.ryanhcode.sable.companion.SableCompanion;
import net.createmod.catnip.math.VecHelper;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.Vec3;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Client-side positions for anchors that ride on a Sable contraption.
 *
 * <p>The server moves such an anchor once per tick and ships the result, which
 * leaves the anchor a tick behind the contraption it is bolted to. That shows up
 * as the wire trailing the contraption and, when the contraption shakes, as a
 * visible wobble between the anchor and the blocks around it.</p>
 *
 * <p>The fix is to stop relying on the shipped position for rendering. The plot
 * position of the anchor is stored here instead, and the world position is
 * recomputed from the sub-level's render pose every frame, with the usual
 * partial-tick interpolation. Rendering then matches the contraption exactly.</p>
 */
public final class AnchorPositions {
    /** Detached node id to its position in Sable's plot grid. */
    private static final Map<Integer, Vec3> PLOTS = new ConcurrentHashMap<>();

    private AnchorPositions() {
    }

    public static void track(int nodeId, Vec3 plot) {
        if (plot != null) {
            PLOTS.put(nodeId, plot);
        }
    }

    public static void forget(int nodeId) {
        PLOTS.remove(nodeId);
    }

    public static void clear() {
        PLOTS.clear();
    }

    public static boolean isTracked(int nodeId) {
        return PLOTS.containsKey(nodeId);
    }

    /**
     * Position of a node for rendering.
     *
     * <p>Tracked anchors are resolved from the contraption's pose so they stay
     * glued to it; everything else falls back to the addon's own lookup.</p>
     */
    public static Vec3 resolve(Level level, InWorldNode node, float partialTicks) {
        if (level == null || node == null) {
            return null;
        }
        if (DetachedNodeHelper.isDetached(node)) {
            Vec3 plot = PLOTS.get(node.id());
            if (plot != null) {
                Vec3 pos = fromPlot(level, plot, partialTicks);
                if (pos != null) {
                    return pos;
                }
            }
        }
        return node.getPosition(level, partialTicks);
    }

    /** Convenience for callers that do not interpolate. */
    public static Vec3 resolve(Level level, InWorldNode node) {
        return resolve(level, node, 1.0F);
    }

    private static Vec3 fromPlot(Level level, Vec3 plot, float partialTicks) {
        try {
            var subLevel = SableCompanion.INSTANCE.getContaining(level, plot);
            if (subLevel == null) {
                return null;
            }
            // On the client the render pose is already smoothed between ticks,
            // which is exactly the interpolation the contraption itself is drawn
            // with. Using it removes the one-tick lag the shipped position has.
            if (subLevel instanceof ClientSubLevelAccess client) {
                return client.renderPose(partialTicks).transformPosition(plot);
            }
            Vec3 logical = subLevel.logicalPose().transformPosition(plot);
            Vec3 last = subLevel.lastPose().transformPosition(plot);
            return VecHelper.lerp(partialTicks, last, logical);
        } catch (Throwable ignored) {
            return null;
        }
    }
}
