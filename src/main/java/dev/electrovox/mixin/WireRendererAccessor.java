package dev.electrovox.mixin;

import com.george_vi.electroenergetics.client.WireEffect;
import com.george_vi.electroenergetics.client.WireRenderer;
import com.george_vi.electroenergetics.foundation.nodes.InWorldNodeConnection;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

import java.util.Map;

/**
 * Exposes the addon's Flywheel effect map so the voxel pass can retire an effect
 * only after it has successfully drawn that wire itself.
 */
@Mixin(WireRenderer.class)
public interface WireRendererAccessor {
    @Accessor("WIRE_EFFECTS")
    static Map<InWorldNodeConnection, WireEffect> electrovox$wireEffects() {
        throw new AssertionError("mixin not applied");
    }
}
