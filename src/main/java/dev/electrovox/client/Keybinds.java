package dev.electrovox.client;

import dev.electrovox.Electrovox;
import net.neoforged.neoforge.client.event.RegisterKeyMappingsEvent;

/**
 * Registers the voxel mode keybind on the mod event bus.
 */
public final class Keybinds {
    private Keybinds() {
    }

    public static void registerKeys(RegisterKeyMappingsEvent event) {
        event.register(VoxelKey.ALTERNATE_PATH);
        Electrovox.LOGGER.debug("Registered voxel wire keybind");
    }
}
