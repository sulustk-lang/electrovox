package dev.electrovox.client;

import com.george_vi.electroenergetics.CEEWireTypes;
import com.george_vi.electroenergetics.simulation.WireType;
import net.minecraft.world.item.DyeColor;
import net.neoforged.neoforge.registries.DeferredHolder;

import java.util.IdentityHashMap;
import java.util.Map;

/**
 * Maps an Electro Energetics {@link WireType} to the tint used for its voxels.
 *
 * <p>Each wire family gets its own colour so a run is readable at a glance:
 * copper and electrum are warm metals, iron and its bus/rail variants are cool
 * greys, insulated types are dark, and dyed insulated wires use their dye. The
 * creative spool is deliberately garish so it is never mistaken for a real wire.</p>
 */
public final class WirePalette {
    /** Fallback when the type is unknown, matching the addon's own copper look. */
    public static final int DEFAULT = 0xC06A32;

    private static final int COPPER = 0xC06A32;
    private static final int ELECTRUM = 0xE3CF8A;
    private static final int IRON = 0xA8A8A8;
    private static final int IRON_BUS = 0x8A8A8A;
    private static final int IRON_RAIL = 0x74777C;
    private static final int INSULATED = 0x2B2B2B;
    private static final int HEAVY_INSULATED = 0x1A1A1A;
    private static final int BUNDLE = 0xB87333;
    private static final int GLASS = 0xC9E4EE;
    private static final int CREATIVE = 0xFF3BD4;

    private static final Map<WireType, Integer> OVERRIDES = new IdentityHashMap<>();

    private WirePalette() {
    }

    public static int colorFor(WireType type) {
        if (type == null) {
            return DEFAULT;
        }

        if (type instanceof WireType.Dyeable dyeable) {
            DyeColor dye = dyeable.getColor();
            if (dye != null) {
                return dyeColor(dye);
            }
        }

        synchronized (OVERRIDES) {
            if (OVERRIDES.isEmpty()) {
                register(OVERRIDES, CEEWireTypes.COPPER, COPPER);
                register(OVERRIDES, CEEWireTypes.ELECTRUM, ELECTRUM);
                register(OVERRIDES, CEEWireTypes.IRON, IRON);
                register(OVERRIDES, CEEWireTypes.IRON_BUS, IRON_BUS);
                register(OVERRIDES, CEEWireTypes.IRON_RAIL, IRON_RAIL);
                register(OVERRIDES, CEEWireTypes.STANDARD, INSULATED);
                register(OVERRIDES, CEEWireTypes.DUPLEX, INSULATED);
                register(OVERRIDES, CEEWireTypes.HEAVILY_INSULATED, HEAVY_INSULATED);
                register(OVERRIDES, CEEWireTypes.BUNDLE_CONDUCTOR, BUNDLE);
                register(OVERRIDES, CEEWireTypes.GLASS_INSULATOR, GLASS);
                register(OVERRIDES, CEEWireTypes.CREATIVE, CREATIVE);
            }
            Integer color = OVERRIDES.get(type);
            return color == null ? DEFAULT : color;
        }
    }

    private static void register(Map<WireType, Integer> target,
                                 DeferredHolder<WireType, WireType> holder, int color) {
        try {
            WireType type = holder.get();
            if (type != null) {
                target.put(type, color);
            }
        } catch (Throwable ignored) {
            // Registry not ready; the fallback colour is used instead.
        }
    }

    private static int dyeColor(DyeColor dye) {
        return switch (dye) {
            case WHITE -> 0xE9ECEC;
            case ORANGE -> 0xF07613;
            case MAGENTA -> 0xBD44B3;
            case LIGHT_BLUE -> 0x3AAFD9;
            case YELLOW -> 0xF8C627;
            case LIME -> 0x70B919;
            case PINK -> 0xED8DAC;
            case GRAY -> 0x3E4447;
            case LIGHT_GRAY -> 0x8E8E86;
            case CYAN -> 0x158991;
            case PURPLE -> 0x792AAC;
            case BLUE -> 0x35399D;
            case BROWN -> 0x724728;
            case GREEN -> 0x546D1B;
            case RED -> 0xA12722;
            case BLACK -> 0x141519;
        };
    }
}
