package dev.electrovox.client;

import com.george_vi.electroenergetics.client.WireRenderer;
import dev.electrovox.Electrovox;
import net.neoforged.fml.event.lifecycle.FMLClientSetupEvent;

import java.lang.reflect.Method;

/**
 * Verifies at startup that the mixin actually landed on the addon's renderer,
 * so a silent mixin failure cannot degrade into "wires vanished" at runtime.
 */
public final class ClientSetup {
    private ClientSetup() {
    }

    public static void onClientSetup(FMLClientSetupEvent event) {
        boolean applied = false;
        for (Method method : WireRenderer.class.getDeclaredMethods()) {
            if (method.getName().startsWith("electrovox$")) {
                applied = true;
                break;
            }
        }
        if (applied) {
            Electrovox.LOGGER.info("Voxel wire mixin verified on {}", WireRenderer.class.getName());
        } else {
            Electrovox.LOGGER.error("Voxel wire mixin did NOT apply to {} - wires will render with "
                    + "default geometry", WireRenderer.class.getName());
        }
    }
}
