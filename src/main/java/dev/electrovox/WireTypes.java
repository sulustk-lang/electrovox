package dev.electrovox;

import com.george_vi.electroenergetics.CEERegistries;
import com.george_vi.electroenergetics.simulation.WireType;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;

/**
 * Resolves the wire type a spool item belongs to.
 *
 * <p>Electro Energetics keeps the type in a package-private field on
 * {@code WireSpoolItem}, so the mapping goes the other way round: every
 * registered type knows which spool it is wound onto. That is also the only way
 * to tell a dyed or creative spool apart from its base type.</p>
 */
public final class WireTypes {
    private WireTypes() {
    }

    public static WireType of(ItemStack stack) {
        if (stack == null || stack.isEmpty()) {
            return null;
        }
        Item spool = stack.getItem();
        try {
            for (WireType type : CEERegistries.WIRE_TYPE) {
                if (type != null && type.getSpooledItem() == spool) {
                    return type;
                }
            }
        } catch (Throwable ignored) {
            // Registry not ready; the caller treats this as "unknown type".
        }
        return null;
    }
}
