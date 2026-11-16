package dev.electrovox.client;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.phys.Vec3;

import java.util.ArrayList;
import java.util.List;

/**
 * Pulls a wire polyline onto nearby block faces.
 *
 * <p>Two rules keep the result a single clean strand:</p>
 *
 * <ul>
 *   <li><b>Never snap along the wire.</b> Only faces perpendicular to the local
 *       direction are considered. Snapping on the axis the wire already travels
 *       only stretches it, and on a diagonal it makes neighbouring samples
 *       alternate between two faces, which is what produced a doubled wire.</li>
 *   <li><b>Stick to the face already in use.</b> Once a run is hugging a wall, a
 *       different face has to be clearly closer before it takes over. Without
 *       that hysteresis the choice flips back and forth along the run.</li>
 * </ul>
 *
 * <p>Samples with no usable face nearby are left exactly where they were, so a
 * wire crossing open air stays straight.</p>
 */
public final class WallRouter {
    /** How far a sample may be pulled sideways to reach a block face. */
    public static final double HUG_RADIUS = 0.55D;
    /** Gap kept between the wire centre and the block face. */
    private static final double FACE_OFFSET = 1.0D / 16.0D;
    /** Resampling step along the curve. */
    private static final double STEP = 1.0D / 16.0D;

    private WallRouter() {
    }

    /**
     * Resamples {@code curve} at a uniform step and snaps it to one block face.
     *
     * <p>The face is chosen once for the whole wire, from its midpoint, and then
     * used for every sample. Choosing per sample made the choice flip between two
     * equidistant faces along a diagonal, which split the wire into two parallel
     * strands. One face for the whole run makes alternation impossible.</p>
     */
    public static List<Vec3> hug(BlockGetter level, List<Vec3> curve) {
        if (curve == null || curve.size() < 2) {
            return curve == null ? List.of() : curve;
        }
        if (level == null) {
            return curve;
        }

        Vec3 first = curve.get(0);
        Vec3 last = curve.get(curve.size() - 1);
        Direction.Axis travelAxis = dominantAxis(last.subtract(first));

        Vec3 midpoint = first.add(last).scale(0.5D);
        Direction face = chooseFace(level, midpoint, travelAxis);
        BlockPos faceBlock = face == null ? null : BlockPos.containing(midpoint).relative(face);

        List<Vec3> result = new ArrayList<>(curve.size() * 2);
        for (int i = 0; i < curve.size() - 1; i++) {
            Vec3 a = curve.get(i);
            Vec3 b = curve.get(i + 1);
            double length = a.distanceTo(b);
            int steps = Math.max(1, (int) Math.ceil(length / STEP));

            for (int s = 0; s < steps; s++) {
                double t = (double) s / steps;
                Vec3 sample = new Vec3(
                        a.x + (b.x - a.x) * t,
                        a.y + (b.y - a.y) * t,
                        a.z + (b.z - a.z) * t);
                result.add(face == null ? sample : ontoFace(sample, faceBlock, face));
            }
        }

        result.add(face == null ? last : ontoFace(last, faceBlock, face));
        return result;
    }

    /** Picks the closest usable face around {@code point}, or null. */
    private static Direction chooseFace(BlockGetter level, Vec3 point, Direction.Axis travelAxis) {
        BlockPos centre = BlockPos.containing(point);

        Direction bestFace = null;
        double bestDistance = Double.MAX_VALUE;

        for (Direction direction : Direction.values()) {
            if (travelAxis != null && direction.getAxis() == travelAxis) {
                continue;
            }
            BlockPos neighbour = centre.relative(direction);
            if (!isSolid(level, neighbour)) {
                continue;
            }
            double distance = distanceToFace(point, neighbour, direction);
            if (distance > HUG_RADIUS) {
                continue;
            }
            if (distance < bestDistance) {
                bestDistance = distance;
                bestFace = direction;
            }
        }
        return bestFace;
    }

    /** Axis the segment mostly travels along, or null when it is a true diagonal. */
    private static Direction.Axis dominantAxis(Vec3 delta) {
        double ax = Math.abs(delta.x);
        double ay = Math.abs(delta.y);
        double az = Math.abs(delta.z);

        double max = Math.max(ax, Math.max(ay, az));
        if (max < 1.0E-6D) {
            return null;
        }
        // Only claim an axis when it clearly dominates, so diagonals snap freely.
        if (max < 1.5D * middle(ax, ay, az)) {
            return null;
        }
        if (max == ax) {
            return Direction.Axis.X;
        }
        return max == ay ? Direction.Axis.Y : Direction.Axis.Z;
    }

    private static double middle(double a, double b, double c) {
        return Math.max(Math.min(a, b), Math.min(Math.max(a, b), c));
    }

    /** True when the route rests on geometry for at least half its samples. */
    public static boolean isRoutable(BlockGetter level, List<Vec3> curve) {
        if (level == null || curve == null || curve.size() < 2) {
            return false;
        }
        int supported = 0;
        for (Vec3 point : curve) {
            if (hasSolidNeighbour(level, point)) {
                supported++;
            }
        }
        return supported * 2 >= curve.size();
    }

    private static boolean hasSolidNeighbour(BlockGetter level, Vec3 point) {
        BlockPos centre = BlockPos.containing(point);
        for (Direction direction : Direction.values()) {
            if (isSolid(level, centre.relative(direction))) {
                return true;
            }
        }
        return false;
    }

    private static boolean isSolid(BlockGetter level, BlockPos pos) {
        try {
            return level.getBlockState(pos).canOcclude();
        } catch (Throwable ignored) {
            return false;
        }
    }

    private static double distanceToFace(Vec3 point, BlockPos block, Direction face) {
        double faceCoordinate = switch (face) {
            case UP -> block.getY() + 1.0D;
            case DOWN -> block.getY();
            case NORTH -> block.getZ();
            case SOUTH -> block.getZ() + 1.0D;
            case WEST -> block.getX();
            case EAST -> block.getX() + 1.0D;
        };
        double pointCoordinate = switch (face.getAxis()) {
            case X -> point.x;
            case Y -> point.y;
            case Z -> point.z;
        };
        return Math.abs(pointCoordinate - faceCoordinate);
    }

    private static Vec3 ontoFace(Vec3 point, BlockPos block, Direction face) {
        double x = point.x;
        double y = point.y;
        double z = point.z;

        switch (face) {
            case UP -> y = block.getY() + 1.0D + FACE_OFFSET;
            case DOWN -> y = block.getY() - FACE_OFFSET;
            case NORTH -> z = block.getZ() - FACE_OFFSET;
            case SOUTH -> z = block.getZ() + 1.0D + FACE_OFFSET;
            case WEST -> x = block.getX() - FACE_OFFSET;
            case EAST -> x = block.getX() + 1.0D + FACE_OFFSET;
        }

        return new Vec3(x, y, z);
    }
}
