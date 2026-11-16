package dev.electrovox.client;

import com.george_vi.electroenergetics.CEEDataComponents;
import com.george_vi.electroenergetics.foundation.nodes.InWorldNode;
import com.mojang.blaze3d.vertex.PoseStack;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.phys.Vec3;

import java.util.List;

/**
 * Draws the prospective voxel wire before it is placed.
 *
 * <p>The preview goes through {@link VoxelWireRenderer#renderPreview}, the same
 * voxeliser the finished wire uses, on the same polyline from
 * {@link WireGeometry}. There is no second geometry path that could drift, which
 * is what made the old preview disagree with the result.</p>
 *
 * <p>It only draws when the held spool actually has a node selected, the same
 * condition Electro Energetics uses to start a connection.</p>
 */
public final class WirePreview {
    private static final int VALID_COLOR = 0x64D2FF;
    private static final int INVALID_COLOR = 0xFF5555;

    private WirePreview() {
    }

    public static InWorldNode selectedNode(ItemStack stack) {
        if (stack == null || stack.isEmpty()) {
            return null;
        }
        try {
            return stack.get(CEEDataComponents.SELECTED_NODE);
        } catch (Throwable ignored) {
            return null;
        }
    }

    /**
     * @param start the selected node's position
     * @param end   the point the player is aiming at
     */
    public static void render(PoseStack pose, MultiBufferSource buffers,
                              Vec3 start, Vec3 end, Vec3 camera) {
        if (start == null || end == null || camera == null) {
            return;
        }

        Minecraft minecraft = Minecraft.getInstance();
        if (minecraft.level == null) {
            return;
        }

        List<Vec3> curve = WireGeometry.polyline(minecraft.level, start, end);
        if (curve.size() < 2) {
            return;
        }

        boolean supported = WallRouter.isRoutable(minecraft.level, curve);
        int rgb = supported ? VALID_COLOR : INVALID_COLOR;

        pose.pushPose();
        try {
            pose.translate(-camera.x, -camera.y, -camera.z);
            VoxelWireRenderer.renderPreview(curve, pose, buffers, minecraft.level, rgb);
        } finally {
            pose.popPose();
        }
    }
}
