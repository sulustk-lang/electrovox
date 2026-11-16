package dev.electrovox.client;

import net.neoforged.neoforge.common.ModConfigSpec;

/**
 * Client-side knobs for the voxel wire renderer.
 */
public final class ElectrovoxConfig {
    private static final ModConfigSpec.Builder BUILDER = new ModConfigSpec.Builder();

    public static final ModConfigSpec.BooleanValue ALWAYS_VOXEL = BUILDER
            .comment("Draw every wire with voxel geometry instead of only the ones",
                    "placed while the voxel key was held.")
            .define("alwaysVoxel", false);

    public static final ModConfigSpec.BooleanValue LOD_ENABLED = BUILDER
            .comment("Use a coarser 1/8 block voxel grid for wires far away.")
            .define("voxelLod", true);

    public static final ModConfigSpec.DoubleValue LOD_DISTANCE = BUILDER
            .comment("Distance in blocks beyond which the coarse grid is used.")
            .defineInRange("voxelLodDistance", 32.0D, 1.0D, 512.0D);

    public static final ModConfigSpec.BooleanValue WALL_ROUTING = BUILDER
            .comment("Pull voxel wires onto nearby block faces.",
                    "Off by default: the plain straight run never splits into two strands.")
            .define("wallRouting", false);

    public static final ModConfigSpec.BooleanValue SHOW_MODE_INDICATOR = BUILDER
            .comment("Show the voxel mode indicator above the hotbar.")
            .define("showModeIndicator", true);

    public static final ModConfigSpec.BooleanValue SURVIVAL_COSTS = BUILDER
            .comment("Charge voxel anchoring like the addon charges wires:",
                    "one connector block per anchor and one spool per run.",
                    "Creative and spectator are always free.")
            .define("survivalCosts", true);

    public static final ModConfigSpec SPEC = BUILDER.build();

    private ElectrovoxConfig() {
    }

    public static boolean alwaysVoxel() {
        try {
            return ALWAYS_VOXEL.get();
        } catch (Throwable ignored) {
            return false;
        }
    }

    public static boolean lodEnabled() {
        try {
            return LOD_ENABLED.get();
        } catch (Throwable ignored) {
            return true;
        }
    }

    public static double lodDistance() {
        try {
            return LOD_DISTANCE.get();
        } catch (Throwable ignored) {
            return 32.0D;
        }
    }

    public static boolean wallRouting() {
        try {
            return WALL_ROUTING.get();
        } catch (Throwable ignored) {
            return false;
        }
    }

    public static boolean showModeIndicator() {
        try {
            return SHOW_MODE_INDICATOR.get();
        } catch (Throwable ignored) {
            return true;
        }
    }

    public static boolean survivalCosts() {
        try {
            return SURVIVAL_COSTS.get();
        } catch (Throwable ignored) {
            return true;
        }
    }
}
