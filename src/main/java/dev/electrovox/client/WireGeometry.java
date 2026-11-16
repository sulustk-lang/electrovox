package dev.electrovox.client;

import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.phys.Vec3;

import java.util.ArrayList;
import java.util.List;

/**
 * Single source of truth for voxel wire geometry.
 *
 * <p>A voxel wire is not a copy of the addon's hanging wire: it does not sag.
 * It is a straight run from endpoint to endpoint, resampled on the 1/16 grid and
 * optionally pulled onto nearby block faces. That is what makes it read as a
 * solid, hand-laid cable rather than a drooping rope.</p>
 *
 * <p>The renderer and the preview both call this, so the line shown before
 * clicking is exactly the line that gets built.</p>
 */
public final class WireGeometry {
    /** Resampling step along the run. */
    private static final double STEP = 1.0D / 16.0D;
    /**
     * Upper bound on samples per run. 4096 steps is 256 blocks, well past the
     * longest legal wire, so this only ever trips on bad input.
     */
    private static final int MAX_STEPS = 4096;

    private WireGeometry() {
    }

    /**
     * Straight polyline between two points, snapped to the wire grid.
     */
    public static List<Vec3> polyline(BlockGetter level, Vec3 start, Vec3 end) {
        if (start == null || end == null) {
            return List.of();
        }

        List<Vec3> line = straightLine(start, end);
        if (line.size() < 2) {
            return List.of();
        }
        if (!ElectrovoxConfig.wallRouting()) {
            return line;
        }
        return WallRouter.hug(level, line);
    }

    /**
     * Samples the segment uniformly so the voxeliser gets a dense, even input.
     *
     * <p>The sample count is capped. A wire on a Sable sub-level used to mix a
     * world position with a plot position, which is a distance of about twenty
     * million blocks; resampling that at 1/16 produced hundreds of millions of
     * points and the JVM was killed for memory. No real wire comes close to the
     * cap, so a bogus distance now just yields a coarse line instead of a crash.</p>
     */
    private static List<Vec3> straightLine(Vec3 start, Vec3 end) {
        double length = start.distanceTo(end);
        if (length < 1.0E-6D) {
            return List.of(start);
        }

        int steps = Math.max(1, Math.min(MAX_STEPS, (int) Math.ceil(length / STEP)));
        List<Vec3> points = new ArrayList<>(steps + 1);
        for (int i = 0; i <= steps; i++) {
            double t = (double) i / steps;
            points.add(new Vec3(
                    start.x + (end.x - start.x) * t,
                    start.y + (end.y - start.y) * t,
                    start.z + (end.z - start.z) * t));
        }
        return points;
    }
}
