package dev.electrovox;

import dev.ryanhcode.sable.companion.SableCompanion;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.Vec3;

/**
 * Bridges the gap between world coordinates and Sable sub-level coordinates.
 *
 * <p>Sable keeps a contraption's blocks at a far-away plot position (chunk
 * coordinates past 1 280 000) and renders them through a transform. A block hit
 * on such a contraption therefore reports its plot position, not where the
 * player sees it. Mixing the two spaces produces distances in the millions,
 * which is how a preview once tried to sample three hundred million points and
 * the JVM was killed for memory.</p>
 *
 * <p>Electro Energetics handles this itself in {@code InWorldNode.closestNode},
 * by projecting the clicked point out of the sub-level before comparing it with
 * node positions. This does the same, and stays a no-op when Sable is absent:
 * {@code SableCompanion.INSTANCE} is a pass-through implementation then.</p>
 */
public final class SableSupport {
    private SableSupport() {
    }

    /** Maps a sub-level point to the world position the player actually sees. */
    public static Vec3 projectOut(Level level, Vec3 point) {
        if (level == null || point == null) {
            return point;
        }
        try {
            Vec3 projected = SableCompanion.INSTANCE.projectOutOfSubLevel(level, point);
            return projected == null ? point : projected;
        } catch (Throwable ignored) {
            return point;
        }
    }
}
