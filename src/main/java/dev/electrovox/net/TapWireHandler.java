package dev.electrovox.net;

import com.george_vi.electroenergetics.foundation.nodes.InWorldNode;
import com.george_vi.electroenergetics.foundation.nodes.InWorldNodeConnection;
import com.george_vi.electroenergetics.simulation.WireType;
import com.george_vi.electroenergetics.simulation.infrastructure.InfrastructureSavedData;
import com.george_vi.electroenergetics.simulation.infrastructure.InWorldNodeData;
import com.george_vi.electroenergetics.simulation.infrastructure.WireData;
import com.george_vi.electroenergetics.simulation.infrastructure.detached_nodes.DetachedNodeType;
import dev.electrovox.AnchorTracking;
import dev.electrovox.Electrovox;
import dev.electrovox.VoxelBalance;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.network.PacketDistributor;
import net.neoforged.neoforge.network.handling.IPayloadContext;

/**
 * Server side of tapping into a wire.
 *
 * <p>The addon cannot connect to the middle of a wire, so the wire is spliced:
 * the original connection is removed and replaced by two connections that meet
 * at a new anchor node at the clicked point. Electrically the result is the same
 * series path, and the anchor is a real node the player can branch from later.</p>
 *
 * <p>Tapping only adds hardware, not cable, so it costs one connector block and
 * no spool. The wire is split with its own type and its existing length, and the
 * length is not re-checked because the player is not making the run any longer.</p>
 */
public final class TapWireHandler {
    private TapWireHandler() {
    }

    public static void handle(TapWirePayload payload, IPayloadContext context) {
        context.enqueueWork(() -> {
            if (!(context.player() instanceof ServerPlayer player)) {
                return;
            }
            ServerLevel level = player.serverLevel();
            InfrastructureSavedData data = InfrastructureSavedData.load(level);

            InWorldNode node1 = new InWorldNode(payload.id1(), BlockPos.of(payload.pos1()));
            InWorldNode node2 = new InWorldNode(payload.id2(), BlockPos.of(payload.pos2()));
            InWorldNodeConnection original = new InWorldNodeConnection(node1, node2);

            VoxelBalance.Quote quote = VoxelBalance.quote(player, false, player.getMainHandItem(), 0.0D);
            if (!quote.affordable()) {
                player.displayClientMessage(Component.translatable("electrovox.msg.not_enough"), true);
                return;
            }

            WireData originalData = data.removeConnectionNoDrops(original);
            if (originalData == null) {
                player.displayClientMessage(Component.translatable("electrovox.msg.wire_gone"), true);
                return;
            }

            WireType wireType = originalData.wireType();
            double halfLength = Math.max(originalData.length, 0.0D) / 2.0D;

            InWorldNodeData anchorData = data.createDetachedNode(DetachedNodeType.FIXED, payload.point());
            if (anchorData == null) {
                Electrovox.LOGGER.warn("Could not create an anchor node at {}", payload.point());
                data.connect(node1, node2, originalData);
                return;
            }

            if (!VoxelBalance.pay(player, quote)) {
                data.removeNodeDefer(anchorData);
                data.connect(node1, node2, originalData);
                player.displayClientMessage(Component.translatable("electrovox.msg.not_enough"), true);
                return;
            }

            InWorldNode anchor = anchorData.node;
            // A tap into a wire that already rides a contraption has to ride it
            // too, otherwise the new connector stays behind while the rest of
            // the run flies off. The wire's endpoints know which contraption
            // they belong to, so the clicked point is converted back into that
            // contraption's plot space.
            trackOnContraption(level, anchor, payload.point(), node1, node2);

            data.connect(node1, anchor, WireData.ofLength(wireType, halfLength));
            data.connect(anchor, node2, WireData.ofLength(wireType, halfLength));
        });
    }

    /**
     * Records {@code anchor} as belonging to the same contraption as the wire it
     * was spliced into, when there is one.
     */
    private static void trackOnContraption(ServerLevel level, InWorldNode anchor, Vec3 point,
                                           InWorldNode node1, InWorldNode node2) {
        try {
            AnchorTracking tracking = AnchorTracking.get(level);
            Vec3 plot = tracking.plotOnContraption(level, node1, node2, point);
            if (plot == null) {
                return;
            }
            tracking.track(anchor.id(), plot);
            PacketDistributor.sendToPlayersInDimension(level,
                    new AnchorPlotPayload(anchor.id(), plot));
        } catch (Throwable ignored) {
            // Not on a contraption, or Sable is absent.
        }
    }
}
