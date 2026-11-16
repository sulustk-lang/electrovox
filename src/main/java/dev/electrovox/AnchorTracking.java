package dev.electrovox;

import com.george_vi.electroenergetics.foundation.nodes.InWorldNode;
import com.george_vi.electroenergetics.foundation.nodes.InWorldNodeConnection;
import com.george_vi.electroenergetics.simulation.infrastructure.InfrastructureSavedData;
import com.george_vi.electroenergetics.simulation.infrastructure.InWorldNodeData;
import com.george_vi.electroenergetics.simulation.infrastructure.WireData;
import com.george_vi.electroenergetics.simulation.infrastructure.detached_nodes.DetachedNodeHelper;
import dev.electrovox.mixin.InWorldNodeDataAccessor;
import dev.electrovox.net.AnchorPlotPayload;
import dev.ryanhcode.sable.companion.SableCompanion;
import dev.ryanhcode.sable.companion.SubLevelAccess;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.saveddata.SavedData;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.network.PacketDistributor;

import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;

/**
 * Keeps anchors placed on a Sable contraption attached to it.
 *
 * <p>A detached node of type {@code FIXED} is frozen: the addon's position tick
 * returns its stored position unchanged, so an anchor put on a contraption stays
 * in the air while the contraption flies away. The anchor cannot simply be made
 * a physics node either, because physics nodes are pulled by wire tension rather
 * than by the contraption.</p>
 *
 * <p>So the plot position of the click is remembered instead. The plot position
 * is the block coordinate inside Sable's far-away plot grid, which never changes
 * for a given block; the world position is that point pushed through the
 * sub-level's current pose. Recomputing it every tick makes the anchor ride
 * along with the contraption, through translation and rotation alike.</p>
 *
 * <p>Wires on a moving contraption must not snap. The addon's overload rule
 * treats a run that stretches by more than a block as a broken cable, which is
 * correct for two fixed ends but wrong for an anchor being dragged along: a
 * shaking contraption would shred its own wiring. Every tracked connection has
 * its stored length grown to the current span before the addon looks at it, so
 * the wire behaves like cable paying off a reel instead of snapping.</p>
 *
 * <p>The mapping is stored in its own saved data rather than on the node,
 * because {@code InWorldNodeData.localPos} is overwritten with zero by the
 * addon's own position update for detached nodes.</p>
 */
public final class AnchorTracking extends SavedData {
    private static final String DATA_NAME = "electrovox_anchors";
    /** Below this squared distance the anchor is considered already in place. */
    private static final double MOVE_EPSILON_SQR = 1.0E-4D;
    /**
     * Extra slack added to a stretched wire. The addon compares the stored
     * length against a span measured a moment later, so a little headroom keeps
     * rounding from looking like overload.
     */
    private static final double STRETCH_MARGIN = 0.25D;

    private final Map<Integer, Vec3> plotPoints = new HashMap<>();

    public static AnchorTracking get(ServerLevel level) {
        return level.getDataStorage().computeIfAbsent(
                new SavedData.Factory<AnchorTracking>(AnchorTracking::new, AnchorTracking::load),
                DATA_NAME);
    }

    private static AnchorTracking load(CompoundTag tag, HolderLookup.Provider provider) {
        AnchorTracking tracking = new AnchorTracking();
        ListTag list = tag.getList("Anchors", Tag.TAG_COMPOUND);
        for (int i = 0; i < list.size(); i++) {
            CompoundTag entry = list.getCompound(i);
            tracking.plotPoints.put(entry.getInt("Id"),
                    new Vec3(entry.getDouble("X"), entry.getDouble("Y"), entry.getDouble("Z")));
        }
        return tracking;
    }

    @Override
    public CompoundTag save(CompoundTag tag, HolderLookup.Provider provider) {
        ListTag list = new ListTag();
        plotPoints.forEach((id, plot) -> {
            CompoundTag entry = new CompoundTag();
            entry.putInt("Id", id);
            entry.putDouble("X", plot.x);
            entry.putDouble("Y", plot.y);
            entry.putDouble("Z", plot.z);
            list.add(entry);
        });
        tag.put("Anchors", list);
        return tag;
    }

    /**
     * Records that a detached node belongs to the contraption at {@code plot}.
     *
     * @param nodeId id of the detached node
     * @param plot   the click position in Sable's plot grid
     */
    public void track(int nodeId, Vec3 plot) {
        plotPoints.put(nodeId, plot);
        setDirty();
    }

    public void forget(int nodeId) {
        if (plotPoints.remove(nodeId) != null) {
            setDirty();
        }
    }

    public boolean isTracked(int nodeId) {
        return plotPoints.containsKey(nodeId);
    }

    /** Plot position of a tracked anchor, or null when it is not tracked. */
    public Vec3 plotOf(int nodeId) {
        return plotPoints.get(nodeId);
    }

    /**
     * True when both ends of {@code connection} sit on one and the same Sable
     * contraption.
     *
     * <p>Such a wire is carried by the contraption as a whole: both ends move
     * together, so the run can never actually be stretched. The addon's overload
     * rule cannot know that and would snap the wire whenever the contraption
     * shakes, so it is overridden for exactly this case.</p>
     *
     * <p>An end counts as being on a contraption when it is one of our tracked
     * anchors, or when it is an ordinary device node whose block position lies in
     * Sable's plot grid.</p>
     */
    public static boolean sameContraption(Level level, InWorldNodeConnection connection) {
        if (!(level instanceof ServerLevel serverLevel) || connection == null) {
            return false;
        }
        try {
            AnchorTracking tracking = get(serverLevel);
            SubLevelAccess first = tracking.subLevelOf(serverLevel, connection.node1());
            SubLevelAccess second = tracking.subLevelOf(serverLevel, connection.node2());
            return first != null && second != null
                    && first.getUniqueId().equals(second.getUniqueId());
        } catch (Throwable ignored) {
            return false;
        }
    }

    private SubLevelAccess subLevelOf(ServerLevel level, InWorldNode node) {
        Vec3 plot = plotOf(node.id());
        if (plot != null) {
            return subLevelAt(level, plot);
        }
        // A device bolted to a contraption keeps its plot coordinates in
        // sourcePos, so it can be resolved without any bookkeeping of ours.
        if (InWorldNode.isFromSubLevel(level, node.sourcePos())) {
            return subLevelAt(level, node.sourcePos().getCenter());
        }
        return null;
    }

    /**
     * Finds the contraption either endpoint of a wire belongs to.
     *
     * <p>Used when a connector is spliced into an existing wire: the new anchor
     * has no plot point of its own yet, but it must inherit the one the wire is
     * already riding.</p>
     */
    public SubLevelAccess contraptionOf(ServerLevel level, InWorldNode a, InWorldNode b) {
        SubLevelAccess first = subLevelOf(level, a);
        if (first != null) {
            return first;
        }
        return subLevelOf(level, b);
    }

    /**
     * Converts a world point on a wire back into the plot space of the
     * contraption that wire rides.
     *
     * @return the plot point, or null when neither endpoint is on a contraption
     */
    public Vec3 plotOnContraption(ServerLevel level, InWorldNode a, InWorldNode b, Vec3 worldPoint) {
        SubLevelAccess subLevel = contraptionOf(level, a, b);
        if (subLevel == null || worldPoint == null) {
            return null;
        }
        try {
            return subLevel.logicalPose().transformPositionInverse(worldPoint);
        } catch (Throwable ignored) {
            return null;
        }
    }

    /** Sends every tracked anchor to a player who just joined. */
    public void syncTo(ServerPlayer player) {
        plotPoints.forEach((nodeId, plot) ->
                PacketDistributor.sendToPlayer(player, new AnchorPlotPayload(nodeId, plot)));
    }

    /**
     * Moves every tracked anchor to where its contraption currently is.
     *
     * <p>A node whose sub-level cannot be found is left alone rather than
     * removed: it may simply be outside the plot grid for a tick while a
     * contraption is being assembled.</p>
     */
    public void tick(ServerLevel level) {
        if (plotPoints.isEmpty()) {
            return;
        }

        InfrastructureSavedData sd = InfrastructureSavedData.load(level);
        Iterator<Map.Entry<Integer, Vec3>> iterator = plotPoints.entrySet().iterator();
        boolean changed = false;

        while (iterator.hasNext()) {
            Map.Entry<Integer, Vec3> entry = iterator.next();
            int nodeId = entry.getKey();
            Vec3 plot = entry.getValue();

            InWorldNode node = DetachedNodeHelper.getFromId(nodeId);
            InWorldNodeData nodeData = sd.getNodeData(node);
            if (nodeData == null) {
                iterator.remove();
                changed = true;
                continue;
            }

            SubLevelAccess subLevel = subLevelAt(level, plot);
            if (subLevel == null) {
                continue;
            }

            Vec3 target;
            try {
                target = subLevel.logicalPose().transformPosition(plot);
            } catch (Throwable ignored) {
                continue;
            }
            if (target == null) {
                continue;
            }
            if (nodeData.getGlobalPos().distanceToSqr(target) < MOVE_EPSILON_SQR) {
                continue;
            }

            ((InWorldNodeDataAccessor) nodeData).electrovox$setGlobalPos(target);

            List<InWorldNodeConnection> connections = sd.getConnections(nodeData);
            sd.wireSync.handleNodeMoved(nodeData);
            for (InWorldNodeConnection connection : connections) {
                relocate(sd, node, nodeData, connection, target);
            }
        }

        if (changed) {
            setDirty();
        }
    }

    /**
     * Repositions one connection after its anchor moved, without ever letting
     * the addon drop the wire.
     */
    private static void relocate(InfrastructureSavedData sd, InWorldNode node,
                                 InWorldNodeData nodeData,
                                 InWorldNodeConnection connection, Vec3 anchorPos) {
        WireData wireData = sd.getConnectionData(connection);
        if (wireData == null) {
            return;
        }

        InWorldNode other = connection.node1().equals(node)
                ? connection.node2() : connection.node1();
        Vec3 otherPos = sd.getNodePositionOrCenter(other);
        if (otherPos == null) {
            return;
        }

        // Pay the cable out to the new span before the addon measures it. With
        // the stored length already long enough the overload check cannot fire,
        // so a shaking or accelerating contraption cannot snap its own wiring.
        double span = anchorPos.distanceTo(otherPos);
        if (wireData.length < span) {
            wireData.length = span + STRETCH_MARGIN;
        }

        if (sd.wireSimulationState.relocateConnection(connection, wireData)) {
            // Only reachable if the addon changed its overload rule. Keep the
            // wire anyway: the anchor is attached to moving hardware, so the run
            // is supposed to stretch.
            wireData.length = span + STRETCH_MARGIN;
            sd.wireSimulationState.relocateConnection(connection, wireData);
        }
        sd.wireSync.handleWireRepositioned(connection, wireData);
    }

    private static SubLevelAccess subLevelAt(ServerLevel level, Vec3 plot) {
        try {
            return SableCompanion.INSTANCE.getContaining(level, plot);
        } catch (Throwable ignored) {
            return null;
        }
    }
}
