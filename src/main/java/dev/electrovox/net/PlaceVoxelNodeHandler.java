package dev.electrovox.net;

import com.george_vi.electroenergetics.CEEDataComponents;
import com.george_vi.electroenergetics.foundation.nodes.InWorldNode;
import com.george_vi.electroenergetics.simulation.WireType;
import com.george_vi.electroenergetics.simulation.infrastructure.InfrastructureSavedData;
import com.george_vi.electroenergetics.simulation.infrastructure.InWorldNodeData;
import com.george_vi.electroenergetics.simulation.infrastructure.detached_nodes.DetachedNodeType;
import dev.electrovox.AnchorTracking;
import dev.electrovox.Electrovox;
import dev.electrovox.VoxelBalance;
import dev.electrovox.WireTypes;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.network.PacketDistributor;
import net.neoforged.neoforge.network.handling.IPayloadContext;

/**
 * Server side of the voxel anchoring: creates a fixed detached node at the
 * clicked point and wires it up.
 *
 * <p>Electro Energetics refuses connections to anything that is not a device
 * node, so this fills the gap. If the spool already holds a selection, the new
 * node is connected to it and the selection is cleared, which mirrors the
 * two-click flow of the addon's own spool. With nothing selected, the new node
 * becomes the selection so the player can pick the other end next.</p>
 *
 * <p>The cost is charged here, never on the client, and follows the addon's own
 * economy: one connector block per anchor and one spool per run. Creative and
 * spectator pay nothing, exactly like {@code WireSpoolItem.useOn}. The node is
 * only created after the player is known to be able to pay, so a rejected click
 * leaves no stray node behind.</p>
 */
public final class PlaceVoxelNodeHandler {
    /** Squared distance beyond which a payload point cannot be a real click. */
    private static final double MAX_REACH_SQR = 64.0D * 64.0D;

    private PlaceVoxelNodeHandler() {
    }

    public static void handle(PlaceVoxelNodePayload payload, IPayloadContext context) {
        context.enqueueWork(() -> {
            if (!(context.player() instanceof ServerPlayer player)) {
                return;
            }
            ServerLevel level = player.serverLevel();
            InfrastructureSavedData data = InfrastructureSavedData.load(level);

            ItemStack held = player.getMainHandItem();
            InWorldNode selected = held.get(CEEDataComponents.SELECTED_NODE);

            Vec3 target = payload.to();
            WireType wireType = selected == null ? null : WireTypes.of(held);

            // Guard against a point that never got projected out of a Sable
            // sub-level. Such a point sits millions of blocks away, and a node
            // created there makes every later light lookup and chunk query walk
            // the plot grid. Rejecting it keeps the world sane.
            if (target.distanceToSqr(player.position()) > MAX_REACH_SQR) {
                Electrovox.LOGGER.debug("Rejected voxel anchor at {} (too far from player)", target);
                deny(player, "electrovox.msg.too_far");
                return;
            }

            double length = 0.0D;
            if (selected != null) {
                Vec3 from = selected.getPosition(level);
                if (from == null) {
                    deny(player, "electrovox.msg.stale_node");
                    return;
                }
                length = from.distanceTo(target);
            }

            VoxelBalance.Quote quote = VoxelBalance.quote(player, selected != null, held, length);
            if (quote.tooLong()) {
                deny(player, "electrovox.msg.too_long");
                return;
            }
            if (!quote.affordable()) {
                deny(player, "electrovox.msg.not_enough");
                return;
            }

            InWorldNodeData nodeData = data.createDetachedNode(DetachedNodeType.FIXED, target);
            if (nodeData == null) {
                Electrovox.LOGGER.warn("Could not create a detached node at {}", target);
                return;
            }
            InWorldNode node = nodeData.node;

            if (!VoxelBalance.pay(player, quote)) {
                data.removeNodeDefer(nodeData);
                deny(player, "electrovox.msg.not_enough");
                return;
            }

            // An anchor on a contraption has to ride along with it. The plot
            // point never changes for a given block, so remembering it is enough
            // to recompute the world position from the sub-level's pose later.
            if (payload.plot() != null) {
                AnchorTracking.get(level).track(node.id(), payload.plot());
                // The client renders this anchor from the contraption's own pose
                // rather than from the position shipped with the node, so it
                // needs the plot point too.
                PacketDistributor.sendToPlayer(player, new AnchorPlotPayload(node.id(), payload.plot()));
            }

            if (selected == null) {
                held.set(CEEDataComponents.SELECTED_NODE, node);
                return;
            }

            data.connect(selected, node, wireType);
            held.remove(CEEDataComponents.SELECTED_NODE);
        });
    }

    private static void deny(ServerPlayer player, String key) {
        player.displayClientMessage(Component.translatable(key), true);
    }
}
