package dev.electrovox.mixin;

import com.george_vi.electroenergetics.client.WireEffect;
import com.george_vi.electroenergetics.client.WireRenderer;
import com.george_vi.electroenergetics.foundation.nodes.InWorldNodeConnection;
import com.george_vi.electroenergetics.simulation.WireType;
import com.george_vi.electroenergetics.simulation.infrastructure.WireData;
import com.mojang.blaze3d.vertex.PoseStack;
import dev.electrovox.client.ElectrovoxConfig;
import dev.electrovox.client.RouteCache;
import dev.electrovox.client.VoxelKey;
import dev.electrovox.client.VoxelWireRenderer;
import dev.electrovox.client.VoxelWires;
import dev.electrovox.client.WallRouter;
import dev.engine_room.flywheel.lib.visualization.VisualizationHelper;
import net.minecraft.client.renderer.LevelRenderer;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.world.level.BlockAndTintGetter;
import net.minecraft.world.phys.Vec3;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import java.util.List;
import java.util.Map;

/**
 * Keeps Flywheel and the voxel pass from drawing the same wire, and covers the
 * no-Flywheel fallback.
 *
 * <p>With Flywheel active the addon never calls {@code renderWire}, so voxel
 * wires are drawn by {@code VoxelWirePass}. Without Flywheel the addon draws
 * every wire itself through {@code renderWire}; there this mixin swaps the
 * geometry for voxel wires and lets everything else through untouched.</p>
 */
@Mixin(WireRenderer.class)
public abstract class WireRendererMixin {
    @Shadow
    protected static Map<InWorldNodeConnection, WireEffect> WIRE_EFFECTS;

    @Inject(method = "addConnection", at = @At("TAIL"))
    private static void electrovox$onConnectionAdded(InWorldNodeConnection connection,
                                                     WireData data, CallbackInfo ci) {
        if (connection == null) {
            return;
        }
        if (VoxelKey.isHeld() || VoxelWires.consumePending()) {
            VoxelWires.bake(connection);
        }
        // Retire the Flywheel effect now, not after the first frame. Flywheel
        // renders before this mod's pass, so leaving the effect alive means the
        // same wire is drawn twice and reads as a doubled cable.
        if (VoxelWires.isVoxel(connection)) {
            electrovox$dropEffect(connection);
        }
    }

    /**
     * Fallback when Flywheel cannot visualise: the addon then draws every wire
     * itself, so voxel wires are swapped here and the rest pass through.
     */
    @Inject(method = "renderWire", at = @At("HEAD"), cancellable = true)
    private static void electrovox$renderVoxels(List<Vec3> points, Vec3 start, Vec3 end,
                                                PoseStack pose, MultiBufferSource buffers,
                                                LevelRenderer levelRenderer, WireType wireType,
                                                BlockAndTintGetter level, CallbackInfo ci) {
        if (start == null || end == null) {
            return;
        }
        InWorldNodeConnection connection = electrovox$findConnection(start, end);
        if (connection == null || !VoxelWires.isVoxel(connection)) {
            return;
        }
        List<Vec3> hugged = ElectrovoxConfig.wallRouting()
                ? WallRouter.hug(level, points)
                : points;
        VoxelWireRenderer.render(hugged, pose, buffers, wireType, level);
        ci.cancel();
    }

    @Inject(method = "removeConnections", at = @At("HEAD"))
    private static void electrovox$forgetWire(InWorldNodeConnection connection, CallbackInfo ci) {
        VoxelWires.forget(connection);
        RouteCache.forget(connection);
    }

    @Inject(method = "clearAllWireConnections", at = @At("HEAD"))
    private static void electrovox$forgetAll(CallbackInfo ci) {
        VoxelWires.clear();
        RouteCache.clear();
    }

    /**
     * The fallback path only hands over endpoints, so the connection is looked up
     * by matching both ends in either order.
     */
    private static InWorldNodeConnection electrovox$findConnection(Vec3 start, Vec3 end) {
        try {
            net.minecraft.client.multiplayer.ClientLevel level =
                    net.minecraft.client.Minecraft.getInstance().level;
            if (level == null) {
                return null;
            }
            for (var pair : WireRenderer.getAllConnections()) {
                InWorldNodeConnection candidate = pair.getFirst();
                Vec3 a = candidate.node1().getPosition(level);
                Vec3 b = candidate.node2().getPosition(level);
                if (a == null || b == null) {
                    continue;
                }
                if (a.distanceToSqr(start) < 1.0E-4D && b.distanceToSqr(end) < 1.0E-4D) {
                    return candidate;
                }
                if (a.distanceToSqr(end) < 1.0E-4D && b.distanceToSqr(start) < 1.0E-4D) {
                    return candidate;
                }
            }
        } catch (Throwable ignored) {
            // Level or connection list unavailable.
        }
        return null;
    }

    /**
     * Removes a wire's Flywheel effect so the voxel pass is its only renderer.
     */
    private static void electrovox$dropEffect(InWorldNodeConnection connection) {
        if (WIRE_EFFECTS == null) {
            return;
        }
        try {
            WireEffect removed = WIRE_EFFECTS.remove(connection);
            if (removed != null) {
                VisualizationHelper.queueRemove(removed);
            }
        } catch (Throwable ignored) {
            // Flywheel not initialised.
        }
    }

}
