package dev.electrovox.net;

import dev.electrovox.Electrovox;
import io.netty.buffer.ByteBuf;
import net.minecraft.core.BlockPos;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.phys.Vec3;

/**
 * Asks the server to tap into an existing wire at a specific point.
 *
 * <p>Electro Energetics has no notion of connecting to the middle of a wire, so
 * the server side splices it: the original connection is replaced by two
 * connections through a new anchor node placed exactly where the player clicked.
 * That makes the connector appear on the wire and be electrically part of it
 * immediately.</p>
 *
 * @param point the world point on the wire
 * @param id1   first endpoint node id of the tapped connection
 * @param pos1  first endpoint block position
 * @param id2   second endpoint node id
 * @param pos2  second endpoint block position
 */
public record TapWirePayload(Vec3 point, int id1, long pos1, int id2, long pos2)
        implements CustomPacketPayload {

    public static final Type<TapWirePayload> TYPE =
            new Type<>(ResourceLocation.fromNamespaceAndPath(Electrovox.MOD_ID, "tap_wire"));

    public static final StreamCodec<RegistryFriendlyByteBuf, TapWirePayload> STREAM_CODEC =
            StreamCodec.of(
                    (buf, payload) -> {
                        writeVec(buf, payload.point());
                        buf.writeVarInt(payload.id1());
                        buf.writeLong(payload.pos1());
                        buf.writeVarInt(payload.id2());
                        buf.writeLong(payload.pos2());
                    },
                    buf -> new TapWirePayload(
                            readVec(buf),
                            buf.readVarInt(), buf.readLong(),
                            buf.readVarInt(), buf.readLong()));

    static BlockPos unpack(long packed) {
        return BlockPos.of(packed);
    }

    private static void writeVec(ByteBuf buf, Vec3 vec) {
        buf.writeDouble(vec.x);
        buf.writeDouble(vec.y);
        buf.writeDouble(vec.z);
    }

    private static Vec3 readVec(ByteBuf buf) {
        return new Vec3(buf.readDouble(), buf.readDouble(), buf.readDouble());
    }

    @Override
    public Type<TapWirePayload> type() {
        return TYPE;
    }
}
