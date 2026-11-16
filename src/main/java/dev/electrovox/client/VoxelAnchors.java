package dev.electrovox.client;

import com.george_vi.electroenergetics.client.WireRenderer;
import com.george_vi.electroenergetics.foundation.nodes.InWorldNodeConnection;
import com.george_vi.electroenergetics.simulation.infrastructure.detached_nodes.DetachedNodeHelper;
import net.createmod.catnip.animation.AnimationTickHolder;
import net.minecraft.client.Minecraft;
import net.minecraft.world.phys.Vec3;

import java.util.ArrayList;
import java.util.List;

/**
 * Finds the anchor connectors currently in the world.
 *
 * <p>Anchors need no separate bookkeeping: they are detached nodes, which
 * Electro Energetics identifies by a sentinel source position, and they are
 * always an endpoint of a wire. So every detached endpoint of a wire is an
 * anchor. Deriving them this way means the connectors survive a relog and follow
 * the wire when it moves.</p>
 *
 * <p>The list is rebuilt every frame rather than cached. A cached list used to
 * hold positions up to a quarter second old, which on a moving contraption made
 * the connector visibly trail the wire it belongs to. Walking the connection list
 * is the same cost the voxel pass already pays each frame.</p>
 */
public final class VoxelAnchors {
    private VoxelAnchors() {
    }

    public static boolean isDetached(InWorldNodeConnection connection) {
        if (connection == null) {
            return false;
        }
        try {
            return DetachedNodeHelper.isDetached(connection.node1())
                    || DetachedNodeHelper.isDetached(connection.node2());
        } catch (Throwable ignored) {
            return false;
        }
    }

    /** World positions of all anchor connectors, one per detached endpoint. */
    public static List<Vec3> positions() {
        return positions(AnimationTickHolder.getPartialTicks());
    }

    /**
     * @param partialTicks frame interpolation, so an anchor on a moving
     *                     contraption is drawn where the contraption is drawn
     */
    public static List<Vec3> positions(float partialTicks) {
        Minecraft minecraft = Minecraft.getInstance();
        if (minecraft.level == null) {
            return List.of();
        }

        List<Vec3> result = new ArrayList<>();
        try {
            for (var pair : WireRenderer.getAllConnections()) {
                InWorldNodeConnection connection = pair.getFirst();
                if (connection == null) {
                    continue;
                }
                if (DetachedNodeHelper.isDetached(connection.node1())) {
                    add(result, AnchorPositions.resolve(minecraft.level, connection.node1(), partialTicks));
                }
                if (DetachedNodeHelper.isDetached(connection.node2())) {
                    add(result, AnchorPositions.resolve(minecraft.level, connection.node2(), partialTicks));
                }
            }
        } catch (Throwable ignored) {
            // Level changing; whatever was gathered so far is still usable.
        }
        return result;
    }

    private static void add(List<Vec3> target, Vec3 pos) {
        if (pos == null) {
            return;
        }
        for (Vec3 existing : target) {
            if (existing.distanceToSqr(pos) < 0.01D) {
                return;
            }
        }
        target.add(pos);
    }

    public static void invalidate() {
        // Nothing is cached any more; kept for callers that used to clear it.
    }

    /** True when {@code point} sits on an anchor connector. */
    public static boolean isAnchor(Vec3 point, double tolerance) {
        if (point == null) {
            return false;
        }
        double limit = tolerance * tolerance;
        for (Vec3 anchor : positions()) {
            if (anchor.distanceToSqr(point) <= limit) {
                return true;
            }
        }
        return false;
    }
}
