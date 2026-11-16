package dev.electrovox.client;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.LevelRenderer;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.core.BlockPos;
import net.minecraft.world.phys.Vec3;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.neoforge.client.event.RenderLevelStageEvent;

import java.util.List;

/**
 * Draws each anchor as a small white square, the visual stand-in for a wire
 * holder. The addon has no model for a free-standing node, so this keeps the
 * attachment point readable and consistent with the voxel wire look.
 */
public final class AnchorRenderer {
    /** Half the edge length of the square, in blocks. */
    private static final double HALF = 0.09D;
    private static final int WHITE = 0xFFFFFF;

    private AnchorRenderer() {
    }

    @SubscribeEvent
    public static void onRenderLevel(RenderLevelStageEvent event) {
        if (event.getStage() != RenderLevelStageEvent.Stage.AFTER_ENTITIES) {
            return;
        }
        List<Vec3> anchors = VoxelAnchors.positions();
        if (anchors.isEmpty()) {
            return;
        }

        Minecraft minecraft = Minecraft.getInstance();
        if (minecraft.level == null) {
            return;
        }

        Vec3 camera = event.getCamera().getPosition();
        PoseStack pose = event.getPoseStack();
        MultiBufferSource.BufferSource buffers = minecraft.renderBuffers().bufferSource();
        VertexConsumer consumer = buffers.getBuffer(RenderType.debugQuads());
        PoseStack.Pose entry = pose.last();

        for (Vec3 anchor : anchors) {
            if (anchor.distanceToSqr(camera) > 128.0D * 128.0D) {
                continue;
            }
            cube(consumer, entry, anchor.subtract(camera), minecraft, anchor);
        }
    }

    private static void cube(VertexConsumer consumer, PoseStack.Pose pose, Vec3 centre,
                             Minecraft minecraft, Vec3 worldPos) {
        double x0 = centre.x - HALF;
        double y0 = centre.y - HALF;
        double z0 = centre.z - HALF;
        double x1 = centre.x + HALF;
        double y1 = centre.y + HALF;
        double z1 = centre.z + HALF;

        // Same packed-light layout as the wire renderer: sky << 20 | block << 4.
        // Reading the raw int as 0..15 would saturate the shade on every anchor.
        int packed = LevelRenderer.getLightColor(minecraft.level, BlockPos.containing(worldPos));
        int block = (packed & 0xFFFF) >> 4;
        int sky = (packed >> 20) & 0xFFFF;
        int raw = Math.min(15, Math.max(block, sky));
        float brightness = 0.75F + 0.25F * (raw / 15.0F);

        float red = ((WHITE >> 16) & 0xFF) / 255.0F * brightness;
        float green = ((WHITE >> 8) & 0xFF) / 255.0F * brightness;
        float blue = (WHITE & 0xFF) / 255.0F * brightness;

        // Six quads, wound outwards so the square is solid from every side.
        quad(consumer, pose, x0, y0, z0, x1, y0, z0, x1, y0, z1, x0, y0, z1, red, green, blue);
        quad(consumer, pose, x0, y1, z1, x1, y1, z1, x1, y1, z0, x0, y1, z0, red, green, blue);
        quad(consumer, pose, x1, y0, z0, x0, y0, z0, x0, y1, z0, x1, y1, z0, red, green, blue);
        quad(consumer, pose, x0, y0, z1, x1, y0, z1, x1, y1, z1, x0, y1, z1, red, green, blue);
        quad(consumer, pose, x0, y0, z0, x0, y0, z1, x0, y1, z1, x0, y1, z0, red, green, blue);
        quad(consumer, pose, x1, y0, z1, x1, y0, z0, x1, y1, z0, x1, y1, z1, red, green, blue);
    }

    private static void quad(VertexConsumer consumer, PoseStack.Pose pose,
                             double ax, double ay, double az,
                             double bx, double by, double bz,
                             double cx, double cy, double cz,
                             double dx, double dy, double dz,
                             float red, float green, float blue) {
        vertex(consumer, pose, ax, ay, az, red, green, blue);
        vertex(consumer, pose, bx, by, bz, red, green, blue);
        vertex(consumer, pose, cx, cy, cz, red, green, blue);
        vertex(consumer, pose, dx, dy, dz, red, green, blue);
    }

    private static void vertex(VertexConsumer consumer, PoseStack.Pose pose,
                               double x, double y, double z,
                               float red, float green, float blue) {
        consumer.addVertex(pose, (float) x, (float) y, (float) z)
                .setColor(red, green, blue, 1.0F);
    }
}
