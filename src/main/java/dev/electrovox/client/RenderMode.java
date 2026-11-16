package dev.electrovox.client;

import com.george_vi.electroenergetics.config.CClient;
import com.george_vi.electroenergetics.config.CEEConfigs;
import net.createmod.catnip.config.ConfigBase;

/**
 * Tells which of the two voxel paths is currently responsible for drawing.
 *
 * <p>Electro Energetics has two rendering modes. With Flywheel active it draws
 * wires itself through Flywheel, and {@code VoxelWirePass} adds the voxel wires.
 * With {@code disableFlywheelWireRendering} on, the addon draws every wire
 * through its own {@code renderWire}, and the mixin swaps voxel wires there.</p>
 *
 * <p>Both paths must never run at once: they use different geometry, so a wire
 * drawn by both reads as two overlapping cables. That is exactly what happened
 * while a stale {@code disableFlywheelWireRendering = true} from an earlier build
 * was left in the config.</p>
 */
public final class RenderMode {
    private RenderMode() {
    }

    /** True when the addon draws wires itself instead of using Flywheel. */
    public static boolean addonDrawsManually() {
        try {
            CClient client = CEEConfigs.client();
            if (client == null || client.disableFlywheelWireRendering == null) {
                return false;
            }
            ConfigBase.ConfigBool flag = client.disableFlywheelWireRendering;
            return Boolean.TRUE.equals(flag.get());
        } catch (Throwable ignored) {
            return false;
        }
    }
}
