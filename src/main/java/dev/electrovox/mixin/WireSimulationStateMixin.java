package dev.electrovox.mixin;

import com.george_vi.electroenergetics.foundation.nodes.InWorldNodeConnection;
import com.george_vi.electroenergetics.simulation.infrastructure.InfrastructureSavedData;
import com.george_vi.electroenergetics.simulation.infrastructure.WireData;
import com.george_vi.electroenergetics.simulation.infrastructure.WireSimulationState;
import dev.electrovox.AnchorTracking;
import net.minecraft.world.phys.Vec3;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * Makes it impossible for the addon to snap a wire that lies entirely on one
 * Sable contraption.
 *
 * <p>{@code relocateConnection} returns {@code true} when a run has stretched
 * past the addon's overload rule, and the caller then drops the wire. For two
 * fixed ends that is correct. For a wire carried by a single moving contraption
 * it is not: both ends travel together, so the run is never actually stretched.
 * A shaking or accelerating contraption would otherwise shred its own wiring.</p>
 *
 * <p>Two hooks cover it. The first pays the cable out to the current span before
 * the addon measures it, so the overload maths cannot fire in the first place.
 * The second is a hard stop: if the check still reports a break, the result is
 * overridden. Wires with ends on different contraptions, or on fixed blocks, are
 * left completely untouched.</p>
 */
@Mixin(WireSimulationState.class)
public abstract class WireSimulationStateMixin {
    @Shadow
    @Final
    public InfrastructureSavedData sd;

    @Inject(method = "relocateConnection", at = @At("HEAD"), remap = false)
    private void electrovox$payOutContraptionWire(InWorldNodeConnection connection,
                                                  WireData wireData,
                                                  CallbackInfoReturnable<Boolean> cir) {
        if (wireData == null) {
            return;
        }
        try {
            if (!AnchorTracking.sameContraption(sd.level, connection)) {
                return;
            }
            Vec3 a = sd.getNodePositionOrCenter(connection.node1());
            Vec3 b = sd.getNodePositionOrCenter(connection.node2());
            if (a == null || b == null) {
                return;
            }
            double span = a.distanceTo(b);
            if (wireData.length < span) {
                wireData.length = span + 0.25D;
            }
        } catch (Throwable ignored) {
            // Leave the addon's own numbers alone if the lookup fails.
        }
    }

    @Inject(method = "relocateConnection", at = @At("RETURN"), cancellable = true, remap = false)
    private void electrovox$neverBreakContraptionWire(InWorldNodeConnection connection,
                                                      WireData wireData,
                                                      CallbackInfoReturnable<Boolean> cir) {
        if (!Boolean.TRUE.equals(cir.getReturnValue())) {
            return;
        }
        try {
            if (AnchorTracking.sameContraption(sd.level, connection)) {
                cir.setReturnValue(false);
            }
        } catch (Throwable ignored) {
            // If the check itself fails, leave the addon's verdict alone.
        }
    }
}
