package dev.electrovox.mixin;

import com.george_vi.electroenergetics.simulation.infrastructure.InWorldNodeData;
import net.minecraft.world.phys.Vec3;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

/**
 * Exposes the detached node's world position.
 *
 * <p>The addon keeps it in a package-private field with no setter, and for a
 * detached node of type {@code FIXED} the position tick returns the stored value
 * unchanged. That is exactly why an anchor placed on a Sable contraption stays
 * behind when the contraption moves. Writing the field is the only way to make
 * the anchor follow the sub-level.</p>
 */
@Mixin(InWorldNodeData.class)
public interface InWorldNodeDataAccessor {
    @Accessor(value = "globalPos", remap = false)
    void electrovox$setGlobalPos(Vec3 pos);
}
