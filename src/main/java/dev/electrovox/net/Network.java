package dev.electrovox.net;

import dev.electrovox.Electrovox;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.neoforge.network.event.RegisterPayloadHandlersEvent;

/**
 * Registers the voxel anchoring payloads.
 */
public final class Network {
    private Network() {
    }

    @SubscribeEvent
    public static void register(RegisterPayloadHandlersEvent event) {
        var registrar = event.registrar("1");
        registrar.playToServer(PlaceVoxelNodePayload.TYPE,
                PlaceVoxelNodePayload.STREAM_CODEC,
                PlaceVoxelNodeHandler::handle);
        registrar.playToServer(TapWirePayload.TYPE,
                TapWirePayload.STREAM_CODEC,
                TapWireHandler::handle);
        registrar.playToClient(AnchorPlotPayload.TYPE,
                AnchorPlotPayload.STREAM_CODEC,
                AnchorPlotHandler::handle);
        Electrovox.LOGGER.debug("Registered voxel wire payloads");
    }
}
