package dev.electrovox.client;

import com.george_vi.electroenergetics.foundation.nodes.InWorldNode;
import com.mojang.blaze3d.vertex.PoseStack;
import dev.electrovox.Electrovox;
import dev.electrovox.SableSupport;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.neoforge.client.event.RenderLevelStageEvent;

/**
 * Shows the prospective voxel wire while a connection is being made.
 *
 * <p>Draws only when the voxel key is held, the held spool has a node selected
 * and the player is aiming at a block, so the preview is always tied to a real
 * action.</p>
 */
public final class PreviewHook {
    private PreviewHook() {
    }

    @SubscribeEvent
    public static void onRenderLevel(RenderLevelStageEvent event) {
        if (event.getStage() != RenderLevelStageEvent.Stage.AFTER_ENTITIES) {
            return;
        }

        Minecraft minecraft = Minecraft.getInstance();
        if (minecraft.level == null || minecraft.player == null || minecraft.options.hideGui) {
            return;
        }
        if (!VoxelKey.isHeld()) {
            return;
        }

        ItemStack held = minecraft.player.getItemInHand(InteractionHand.MAIN_HAND);
        InWorldNode selected = WirePreview.selectedNode(held);
        if (selected == null) {
            return;
        }

        HitResult hit = minecraft.hitResult;
        if (!(hit instanceof BlockHitResult blockHit)) {
            return;
        }

        Vec3 start = selected.getPosition(minecraft.level);
        if (start == null) {
            return;
        }
        // A hit on a Sable contraption reports the block's plot position, which
        // is millions of blocks from the world. Projecting it out first keeps the
        // preview in one coordinate space; without this the sampler tried to
        // produce hundreds of millions of points and the JVM was killed.
        Vec3 end = SableSupport.projectOut(minecraft.level, blockHit.getLocation());
        if (end == null || start.distanceToSqr(end) < 1.0E-4D) {
            return;
        }

        MultiBufferSource.BufferSource buffers = minecraft.renderBuffers().bufferSource();
        try {
            WirePreview.render(event.getPoseStack(), buffers, start, end,
                    event.getCamera().getPosition());
        } catch (Throwable error) {
            Electrovox.LOGGER.debug("Wire preview skipped: {}", error.toString());
        }
    }
}
