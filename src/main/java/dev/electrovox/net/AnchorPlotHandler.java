package dev.electrovox.net;

import dev.electrovox.client.AnchorPositions;

import net.neoforged.neoforge.network.handling.IPayloadContext;

/**
 * Client side of the anchor plot sync: remembers the plot position so the
 * renderer can resolve the anchor from the contraption's own pose.
 */
public final class AnchorPlotHandler {
    private AnchorPlotHandler() {
    }

    public static void handle(AnchorPlotPayload payload, IPayloadContext context) {
        if (payload.plot() == null) {
            AnchorPositions.forget(payload.nodeId());
        } else {
            AnchorPositions.track(payload.nodeId(), payload.plot());
        }
    }
}
