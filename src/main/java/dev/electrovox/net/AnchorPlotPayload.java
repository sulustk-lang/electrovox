package dev.electrovox.net;

import dev.electrovox.Electrovox;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.phys.Vec3;

/**
 * Tells the client where an anchor sits inside a Sable contraption.
 *
 * <p>The server owns the authoritative position, but it only recomputes it once
 * per tick and the result arrives a tick late. The client has the contraption's
 * own render pose, so sending the plot position lets it place the anchor exactly
 * where the contraption is drawn, with no lag and no shake.</p>
 *
 * @param nodeId id of the detached node
 * @param plot   its position in Sable's plot grid, or null to stop tracking
 */
public record AnchorPlotPayload(int nodeId, Vec3 plot) implements CustomPacketPayload {

    public static final Type<AnchorPlotPayload> TYPE =
            new Type<>(ResourceLocation.fromNamespaceAndPath(Electrovox.MOD_ID, "anchor_plot"));

    public static final StreamCodec<RegistryFriendlyByteBuf, AnchorPlotPayload> STREAM_CODEC =
            StreamCodec.of(
                    (buf, payload) -> {
                        buf.writeVarInt(payload.nodeId());
                        buf.writeBoolean(payload.plot() != null);
                        if (payload.plot() != null) {
                            buf.writeDouble(payload.plot().x);
                            buf.writeDouble(payload.plot().y);
                            buf.writeDouble(payload.plot().z);
                        }
                    },
                    buf -> new AnchorPlotPayload(
                            buf.readVarInt(),
                            buf.readBoolean()
                                    ? new Vec3(buf.readDouble(), buf.readDouble(), buf.readDouble())
                                    : null));

    @Override
    public Type<AnchorPlotPayload> type() {
        return TYPE;
    }
}
