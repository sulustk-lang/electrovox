package dev.electrovox.net;

import dev.electrovox.Electrovox;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.phys.Vec3;

/**
 * Asks the server to anchor a wire at an arbitrary point in the world.
 *
 * <p>Electro Energetics only accepts connections at device nodes: its spool's
 * {@code useOn} returns {@code PASS} for any block that is not an
 * {@code ElectricalDeviceBlock}. So a click on a plain block does nothing at
 * all. This payload carries the clicked point; the server creates a fixed
 * detached node there and either stores it as the selection or connects it to
 * the node already selected. That is what makes "wire to any block" possible,
 * the way Power Grid's placed wires work.</p>
 *
 * @param to   the clicked point in world coordinates
 * @param plot the same point in Sable's plot grid, or null when the click was
 *             not on a contraption. The server keeps it so the anchor can ride
 *             along with the contraption.
 */
public record PlaceVoxelNodePayload(Vec3 to, Vec3 plot) implements CustomPacketPayload {

    public static final Type<PlaceVoxelNodePayload> TYPE =
            new Type<>(ResourceLocation.fromNamespaceAndPath(Electrovox.MOD_ID, "place_voxel_node"));

    public static final StreamCodec<RegistryFriendlyByteBuf, PlaceVoxelNodePayload> STREAM_CODEC =
            StreamCodec.of(
                    (buf, payload) -> {
                        writeVec(buf, payload.to());
                        buf.writeBoolean(payload.plot() != null);
                        if (payload.plot() != null) {
                            writeVec(buf, payload.plot());
                        }
                    },
                    buf -> new PlaceVoxelNodePayload(
                            readVec(buf),
                            buf.readBoolean() ? readVec(buf) : null));

    private static void writeVec(RegistryFriendlyByteBuf buf, Vec3 vec) {
        buf.writeDouble(vec.x);
        buf.writeDouble(vec.y);
        buf.writeDouble(vec.z);
    }

    private static Vec3 readVec(RegistryFriendlyByteBuf buf) {
        return new Vec3(buf.readDouble(), buf.readDouble(), buf.readDouble());
    }

    @Override
    public Type<PlaceVoxelNodePayload> type() {
        return TYPE;
    }
}
