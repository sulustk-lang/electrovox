package dev.electrovox.client;

import com.george_vi.electroenergetics.foundation.nodes.InWorldNode;
import dev.electrovox.SableSupport;
import dev.electrovox.VoxelBalance;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.network.chat.Component;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.neoforge.client.event.RenderGuiEvent;

/**
 * Shows "Voxel mode" above the hotbar while the voxel key is held, together with
 * the survival price of the action under the crosshair.
 *
 * <p>The price line only appears outside creative, because that is the only
 * place anchoring costs anything. With no node selected it prices the anchor
 * about to be placed; with a node selected it prices the run and shows how much
 * of the wire type's maximum length the click would use. Both numbers come from
 * {@link VoxelBalance}, the same class the server charges with, so the HUD
 * cannot drift from what is actually deducted.</p>
 */
public final class ModeIndicator {
    private static final int ACCENT = 0xFF5BD6FF;
    private static final int BACKGROUND = 0x90000000;
    private static final int WARN = 0xFFFF7A6B;
    private static final int MUTED = 0xFFB9C3C9;

    private ModeIndicator() {
    }

    @SubscribeEvent
    public static void onRenderGui(RenderGuiEvent.Post event) {
        if (!ElectrovoxConfig.showModeIndicator() || !VoxelKey.isHeld()) {
            return;
        }
        Minecraft minecraft = Minecraft.getInstance();
        if (minecraft.player == null || minecraft.options.hideGui) {
            return;
        }

        GuiGraphics graphics = event.getGuiGraphics();
        Component label = Component.translatable("hud.electrovox.voxel_mode", VoxelKey.displayName());

        int width = minecraft.font.width(label);
        int x = (graphics.guiWidth() - width) / 2;
        int y = graphics.guiHeight() - 62;

        graphics.fill(x - 4, y - 3, x + width + 4, y + 10, BACKGROUND);
        graphics.fill(x - 4, y - 3, x + width + 4, y - 2, ACCENT);
        graphics.drawString(minecraft.font, label, x, y, ACCENT, true);

        Price price = price(minecraft);
        if (price == null) {
            return;
        }

        int lineWidth = minecraft.font.width(price.text());
        int lineX = (graphics.guiWidth() - lineWidth) / 2;
        int lineY = y + 13;

        graphics.fill(lineX - 4, lineY - 3, lineX + lineWidth + 4, lineY + 10, BACKGROUND);
        graphics.fill(lineX - 4, lineY + 9, lineX + lineWidth + 4, lineY + 10, price.tint());
        graphics.drawString(minecraft.font, price.text(), lineX, lineY, price.tint(), true);
    }

    private record Price(Component text, int tint) {
    }

    private static Price price(Minecraft minecraft) {
        if (VoxelBalance.freeFor(minecraft.player) || !ElectrovoxConfig.survivalCosts()) {
            return null;
        }

        ItemStack held = minecraft.player.getItemInHand(InteractionHand.MAIN_HAND);
        if (!VoxelKey.isSpool(held)) {
            return null;
        }

        InWorldNode selected = WirePreview.selectedNode(held);
        double length = selected == null ? 0.0D : runLength(minecraft, selected);
        VoxelBalance.Quote quote = VoxelBalance.quote(minecraft.player, selected != null, held, length);

        Component connectors = Component.translatable("hud.electrovox.connector_count",
                VoxelBalance.CONNECTORS_PER_ANCHOR);
        Component wire = VoxelBalance.wireName(held);
        Component wireName = wire == null ? held.getHoverName() : wire;

        if (quote.tooLong()) {
            return new Price(Component.translatable("hud.electrovox.cost_too_long",
                    format(length), format(VoxelBalance.maxLength(held))), WARN);
        }
        if (!quote.affordable()) {
            return new Price(selected == null
                    ? Component.translatable("hud.electrovox.need_connector", connectors)
                    : Component.translatable("hud.electrovox.need_run", connectors, wireName), WARN);
        }
        if (selected == null) {
            return new Price(Component.translatable("hud.electrovox.cost_anchor", connectors), MUTED);
        }
        return new Price(Component.translatable("hud.electrovox.cost_run",
                connectors, wireName, format(length)), MUTED);
    }

    private static String format(double value) {
        return String.format("%.1f", value);
    }

    private static double runLength(Minecraft minecraft, InWorldNode selected) {
        try {
            if (minecraft.level == null || minecraft.player == null) {
                return 0.0D;
            }
            Vec3 start = selected.getPosition(minecraft.level);
            if (start == null) {
                return 0.0D;
            }
            HitResult hit = minecraft.hitResult;
            Vec3 end = hit instanceof BlockHitResult blockHit
                    ? SableSupport.projectOut(minecraft.level, blockHit.getLocation())
                    : minecraft.player.getEyePosition(1.0F)
                            .add(minecraft.player.getViewVector(1.0F).scale(4.0D));
            if (end == null) {
                return 0.0D;
            }
            return start.distanceTo(end);
        } catch (Throwable ignored) {
            return 0.0D;
        }
    }
}
