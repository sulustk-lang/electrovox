package dev.electrovox.client;

import com.george_vi.electroenergetics.foundation.nodes.InWorldNode;
import com.george_vi.electroenergetics.foundation.nodes.InWorldNodeConnection;
import com.george_vi.electroenergetics.simulation.infrastructure.WireData;
import dev.electrovox.SableSupport;
import dev.electrovox.VoxelBalance;
import dev.electrovox.net.PlaceVoxelNodePayload;
import dev.electrovox.net.TapWirePayload;
import net.createmod.catnip.data.Pair;
import net.minecraft.client.Minecraft;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.neoforge.client.event.InputEvent;
import net.neoforged.neoforge.network.PacketDistributor;

import java.util.List;

/**
 * Turns a spool click into an anchor connector.
 *
 * <p>Clicking a voxel wire splices it: the server replaces the wire with two
 * wires meeting at a new anchor placed where the ray crossed the cable.
 * Clicking a plain block creates an anchor there instead, so a new voxel wire
 * can end on any surface.</p>
 *
 * <p>The click is taken from {@link InputEvent.InteractionKeyMappingTriggered}
 * rather than {@code RightClickBlock} on purpose. A voxel wire has no collision,
 * so the crosshair normally rests on a block far behind the cable, and aiming at
 * a cable against the sky yields no block hit at all. Neither case reaches
 * {@code RightClickBlock}, which is why clicking a wire used to do nothing. This
 * event fires for the use key regardless of what the crosshair found, so the
 * cable is hit-tested against the player's look ray instead.</p>
 */
public final class BlockAnchorHook {
    /**
     * How far the look ray may pass from a cable and still count as a hit. A
     * voxel is 1/16 of a block, so this is a little wider than the drawn cube
     * and still tight enough not to grab a wire the player is not looking at.
     */
    private static final double WIRE_HIT_RADIUS = 0.20D;

    private BlockAnchorHook() {
    }

    @SubscribeEvent
    public static void onUseInput(InputEvent.InteractionKeyMappingTriggered event) {
        if (!event.isUseItem() || event.getHand() != InteractionHand.MAIN_HAND) {
            return;
        }
        Minecraft minecraft = Minecraft.getInstance();
        if (minecraft.level == null || minecraft.player == null) {
            return;
        }
        if (!VoxelKey.isHeld() || minecraft.player.isShiftKeyDown()) {
            return;
        }
        ItemStack held = minecraft.player.getMainHandItem();
        if (!VoxelKey.isSpool(held)) {
            return;
        }

        BlockHitResult blockHit = minecraft.hitResult instanceof BlockHitResult hit
                && hit.getType() == HitResult.Type.BLOCK ? hit : null;
        // A hit on a Sable contraption reports the block's plot position, not
        // where the player sees it. Everything below works in world space, so
        // the hit point is projected out first.
        Vec3 hitPoint = blockHit == null ? null
                : SableSupport.projectOut(minecraft.level, blockHit.getLocation());
        double reach = minecraft.player.getAttributeValue(Attributes.BLOCK_INTERACTION_RANGE);
        double limit = hitPoint == null ? reach + 0.5D
                : Math.min(reach + 0.5D, minecraft.player.getEyePosition(1.0F)
                        .distanceTo(hitPoint) + 0.1D);

        Vec3 eye = minecraft.player.getEyePosition(1.0F);
        Vec3 look = minecraft.player.getViewVector(1.0F);

        Tap tap = findWire(eye, look, limit);
        if (tap != null) {
            if (!affordable(minecraft, false, 0.0D)) {
                return;
            }
            VoxelWires.markPending();
            InWorldNodeConnection connection = tap.connection();
            PacketDistributor.sendToServer(new TapWirePayload(
                    tap.point(),
                    connection.node1().id(), connection.node1().sourcePos().asLong(),
                    connection.node2().id(), connection.node2().sourcePos().asLong()));
            event.setSwingHand(true);
            event.setCanceled(true);
            return;
        }

        if (blockHit == null) {
            return;
        }
        BlockState state = minecraft.level.getBlockState(blockHit.getBlockPos());
        if (state.getBlock()
                instanceof com.george_vi.electroenergetics.foundation.device.ElectricalDeviceBlock) {
            return;
        }

        boolean connecting = WirePreview.selectedNode(held) != null;
        double length = connecting && hitPoint != null
                ? selectedLength(minecraft, held, hitPoint) : 0.0D;
        if (!affordable(minecraft, connecting, length)) {
            return;
        }

        VoxelWires.markPending();
        // The raw hit point is kept alongside the projected one: when it lies in
        // Sable's plot grid it identifies the contraption block, which is what
        // the server needs to keep the anchor attached to a moving contraption.
        PacketDistributor.sendToServer(new PlaceVoxelNodePayload(
                hitPoint == null ? blockHit.getLocation() : hitPoint,
                plotPoint(minecraft, blockHit.getLocation(), hitPoint)));
        event.setSwingHand(true);
        event.setCanceled(true);
    }

    /**
     * @return the hit point when it lies in a Sable plot grid, otherwise null
     */
    private static Vec3 plotPoint(Minecraft minecraft, Vec3 raw, Vec3 projected) {
        if (raw == null || projected == null || raw.distanceToSqr(projected) < 1.0E-4D) {
            return null;
        }
        try {
            return dev.ryanhcode.sable.companion.SableCompanion.INSTANCE
                    .getContaining(minecraft.level, raw) != null ? raw : null;
        } catch (Throwable ignored) {
            return null;
        }
    }

    /**
     * Prices the click the same way the server will. The server is still the
     * authority; this only stops a click that is known to be rejected from
     * swinging the hand and flashing a chat message.
     */
    private static boolean affordable(Minecraft minecraft, boolean connecting, double length) {
        if (!ElectrovoxConfig.survivalCosts()) {
            return true;
        }
        ItemStack held = minecraft.player.getMainHandItem();
        return VoxelBalance.quote(minecraft.player, connecting, held, length).affordable();
    }

    private static double selectedLength(Minecraft minecraft, ItemStack held, Vec3 target) {
        InWorldNode selected = WirePreview.selectedNode(held);
        if (selected == null || minecraft.level == null) {
            return 0.0D;
        }
        Vec3 from = selected.getPosition(minecraft.level);
        return from == null ? 0.0D : from.distanceTo(target);
    }

    private record Tap(InWorldNodeConnection connection, Vec3 point) {
    }

    /**
     * Walks the look ray against every voxel wire and returns the nearest hit.
     *
     * <p>Distance is measured perpendicular to the ray, so the result is the
     * cable under the crosshair, not the closest cable to the block face behind
     * it. The route is already sampled every 1/16 block, so testing its points is
     * enough and stays cheap.</p>
     */
    private static Tap findWire(Vec3 eye, Vec3 look, double limit) {
        Minecraft minecraft = Minecraft.getInstance();
        if (minecraft.level == null) {
            return null;
        }

        Vec3 dir = look.normalize();
        Tap best = null;
        double bestAlong = limit;
        double radiusSqr = WIRE_HIT_RADIUS * WIRE_HIT_RADIUS;

        for (Pair<InWorldNodeConnection, WireData> pair
                : com.george_vi.electroenergetics.client.WireRenderer.getAllConnections()) {
            InWorldNodeConnection connection = pair.getFirst();
            if (!VoxelWires.isVoxel(connection)) {
                continue;
            }
            Vec3 a = connection.node1().getPosition(minecraft.level);
            Vec3 b = connection.node2().getPosition(minecraft.level);
            if (a == null || b == null) {
                continue;
            }

            List<Vec3> route = RouteCache.route(minecraft.level, connection, a, b);
            for (Vec3 sample : route) {
                double along = sample.subtract(eye).dot(dir);
                if (along < 0.0D || along >= bestAlong) {
                    continue;
                }
                Vec3 onRay = eye.add(dir.scale(along));
                if (onRay.distanceToSqr(sample) > radiusSqr) {
                    continue;
                }
                bestAlong = along;
                best = new Tap(connection, sample);
            }
        }
        return best;
    }
}
