package dev.electrovox.client;

import com.george_vi.electroenergetics.client.WireRenderer;
import com.george_vi.electroenergetics.foundation.nodes.InWorldNodeConnection;
import com.george_vi.electroenergetics.simulation.infrastructure.WireData;
import com.mojang.blaze3d.vertex.PoseStack;
import dev.electrovox.Electrovox;
import dev.electrovox.mixin.WireRendererAccessor;
import dev.engine_room.flywheel.api.visualization.VisualizationManager;
import dev.engine_room.flywheel.lib.visualization.VisualizationHelper;
import net.createmod.catnip.animation.AnimationTickHolder;
import net.createmod.catnip.data.Pair;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.world.phys.Vec3;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.neoforge.client.event.RenderLevelStageEvent;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * Draws voxel wires in an independent render pass.
 *
 * <p>Electro Energetics only reaches its own {@code renderWire} when Flywheel
 * visualisation is off, so with Flywheel enabled the addon never calls the
 * geometry hook at all. This pass walks the addon's client-side connection list
 * itself and draws the voxel wires directly, leaving Flywheel in charge of every
 * other wire.</p>
 *
 * <p>A wire's Flywheel effect is retired only after this pass has successfully
 * drawn it. Retiring it earlier made wires vanish completely whenever the voxel
 * pass could not run, which is exactly the failure that hid them before.</p>
 */
public final class VoxelWirePass {
    private VoxelWirePass() {
    }

    @SubscribeEvent
    public static void onRenderLevel(RenderLevelStageEvent event) {
        if (event.getStage() != RenderLevelStageEvent.Stage.AFTER_ENTITIES) {
            return;
        }
        Minecraft minecraft = Minecraft.getInstance();
        ClientLevel level = minecraft.level;
        if (level == null) {
            return;
        }
        // Exactly one path may draw a voxel wire. When the addon renders wires
        // itself the mixin owns them; otherwise this pass does. Running both
        // draws the same wire twice with different geometry.
        if (!VisualizationManager.supportsVisualization(level) || RenderMode.addonDrawsManually()) {
            return;
        }

        List<Pair<InWorldNodeConnection, WireData>> connections = WireRenderer.getAllConnections();
        if (connections.isEmpty()) {
            return;
        }

        float partialTicks = AnimationTickHolder.getPartialTicks();
        Vec3 camera = event.getCamera().getPosition();
        PoseStack pose = event.getPoseStack();
        MultiBufferSource.BufferSource buffers = minecraft.renderBuffers().bufferSource();

        List<InWorldNodeConnection> drawn = new ArrayList<>();

        pose.pushPose();
        pose.translate(-camera.x, -camera.y, -camera.z);
        try {
            for (Pair<InWorldNodeConnection, WireData> pair : connections) {
                if (renderOne(pair, level, partialTicks, pose, buffers)) {
                    drawn.add(pair.getFirst());
                }
            }
        } catch (Throwable error) {
            Electrovox.LOGGER.debug("Voxel wire pass skipped: {}", error.toString());
        } finally {
            pose.popPose();
        }

        retireFlywheel(level, drawn);

    }

    /**
     * @return true when the wire was drawn as voxels this frame
     */
    private static boolean renderOne(Pair<InWorldNodeConnection, WireData> pair, ClientLevel level,
                                     float partialTicks, PoseStack pose,
                                     MultiBufferSource buffers) {
        InWorldNodeConnection connection = pair.getFirst();
        WireData data = pair.getSecond();
        if (connection == null || data == null) {
            return false;
        }
        if (!VoxelWires.isVoxel(connection)) {
            return false;
        }

        Vec3 start = AnchorPositions.resolve(level, connection.node1(), partialTicks);
        Vec3 end = AnchorPositions.resolve(level, connection.node2(), partialTicks);
        if (start == null || end == null) {
            return false;
        }

        List<Vec3> points = RouteCache.route(level, connection, start, end);
        if (points.size() < 2) {
            return false;
        }

        try {
            VoxelWireRenderer.render(points, pose, buffers, data.wireType(), level);
            return true;
        } catch (Throwable error) {
            Electrovox.LOGGER.debug("Voxel render failed for {}: {}", connection, error.toString());
            return false;
        }
    }

    private static void retireFlywheel(ClientLevel level, List<InWorldNodeConnection> drawn) {
        if (drawn.isEmpty()) {
            return;
        }
        try {
            Map<InWorldNodeConnection, ?> effects = WireRendererAccessor.electrovox$wireEffects();
            if (effects == null) {
                return;
            }
            for (InWorldNodeConnection connection : drawn) {
                Object removed = effects.remove(connection);
                if (removed instanceof com.george_vi.electroenergetics.client.WireEffect effect) {
                    VisualizationHelper.queueRemove(effect);
                }
            }
        } catch (Throwable ignored) {
            // Flywheel not ready; the effect is retired on a later frame.
        }
    }
}
