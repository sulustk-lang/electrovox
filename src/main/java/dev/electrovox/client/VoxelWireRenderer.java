package dev.electrovox.client;

import com.george_vi.electroenergetics.simulation.WireType;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import it.unimi.dsi.fastutil.longs.LongOpenHashSet;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.LevelRenderer;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.BlockAndTintGetter;
import net.minecraft.world.phys.Vec3;

import java.util.List;

/**
 * Draws a wire polyline as a chain of axis-aligned voxel cubes.
 *
 * <p>The polyline is the wire's real curve, already pulled onto block faces by
 * {@link WallRouter}. Each segment is walked with a 3D DDA so the cells form a
 * connected 6-neighbour chain instead of a dotted diagonal. Cell size is
 * {@link #FINE_CELL} (1/16 block), or {@link #COARSE_CELL} (1/8 block) past the
 * LOD distance.</p>
 *
 * <p>Only faces that touch empty grid space are emitted, which keeps the vertex
 * count low while still reading as solid cubes.</p>
 *
 * <p>The grid origin is the wire's first sample rather than the world origin, so
 * the cube pattern rides along with a moving contraption instead of jittering
 * against a fixed grid.</p>
 */
public final class VoxelWireRenderer {
    /** Voxel edge length: one sixteenth of a block. */
    public static final double FINE_CELL = 1.0D / 16.0D;
    /** Coarser voxel edge used past the LOD distance. */
    public static final double COARSE_CELL = 1.0D / 8.0D;

    private static final int FACE_DOWN = 0;
    private static final int FACE_UP = 1;
    private static final int FACE_NORTH = 2;
    private static final int FACE_SOUTH = 3;
    private static final int FACE_WEST = 4;
    private static final int FACE_EAST = 5;

    /** Per-face brightness, mirroring vanilla block shading. */
    private static final float[] FACE_SHADE = {0.55F, 1.0F, 0.80F, 0.80F, 0.62F, 0.62F};

    /** Hard cap so a pathological wire cannot stall a frame. */
    private static final int MAX_CELLS = 12288;
    /** DDA safety valve for degenerate segments. */
    private static final int MAX_STEPS_PER_SEGMENT = 4096;

    private VoxelWireRenderer() {
    }

    public static void render(List<Vec3> points, PoseStack pose, MultiBufferSource buffers,
                              WireType wireType, BlockAndTintGetter level) {
        int rgb = WirePalette.colorFor(wireType);
        draw(points, pose, buffers, level, rgb, 1.0F, true);
    }

    /**
     * Draws the same geometry as a translucent preview, so what is shown before
     * clicking is exactly what will be built.
     */
    public static void renderPreview(List<Vec3> points, PoseStack pose, MultiBufferSource buffers,
                                     BlockAndTintGetter level, int rgb) {
        draw(points, pose, buffers, level, rgb, 0.45F, false);
    }

    private static void draw(List<Vec3> points, PoseStack pose, MultiBufferSource buffers,
                             BlockAndTintGetter level, int rgb, float alpha,
                             boolean distanceLod) {
        if (points == null || points.size() < 2 || buffers == null || pose == null) {
            return;
        }

        Vec3 origin = points.get(0);
        double cell = distanceLod ? cellSizeFor(points) : FINE_CELL;

        LongOpenHashSet cells = voxelize(points, origin, cell);
        if (cells.isEmpty()) {
            return;
        }

        float baseRed = ((rgb >> 16) & 0xFF) / 255.0F;
        float baseGreen = ((rgb >> 8) & 0xFF) / 255.0F;
        float baseBlue = (rgb & 0xFF) / 255.0F;

        VertexConsumer consumer = buffers.getBuffer(RenderType.debugQuads());
        PoseStack.Pose poseEntry = pose.last();

        for (long packed : cells) {
            int cx = unpackX(packed);
            int cy = unpackY(packed);
            int cz = unpackZ(packed);

            double x0 = origin.x + cx * cell;
            double y0 = origin.y + cy * cell;
            double z0 = origin.z + cz * cell;
            double x1 = x0 + cell;
            double y1 = y0 + cell;
            double z1 = z0 + cell;

            float light = lightAt(level, x0 + cell * 0.5D, y0 + cell * 0.5D, z0 + cell * 0.5D);
            float red = baseRed * light;
            float green = baseGreen * light;
            float blue = baseBlue * light;

            if (!cells.contains(pack(cx, cy - 1, cz))) {
                face(consumer, poseEntry, FACE_DOWN, x0, y0, z0, x1, y1, z1, red, green, blue, alpha);
            }
            if (!cells.contains(pack(cx, cy + 1, cz))) {
                face(consumer, poseEntry, FACE_UP, x0, y0, z0, x1, y1, z1, red, green, blue, alpha);
            }
            if (!cells.contains(pack(cx, cy, cz - 1))) {
                face(consumer, poseEntry, FACE_NORTH, x0, y0, z0, x1, y1, z1, red, green, blue, alpha);
            }
            if (!cells.contains(pack(cx, cy, cz + 1))) {
                face(consumer, poseEntry, FACE_SOUTH, x0, y0, z0, x1, y1, z1, red, green, blue, alpha);
            }
            if (!cells.contains(pack(cx - 1, cy, cz))) {
                face(consumer, poseEntry, FACE_WEST, x0, y0, z0, x1, y1, z1, red, green, blue, alpha);
            }
            if (!cells.contains(pack(cx + 1, cy, cz))) {
                face(consumer, poseEntry, FACE_EAST, x0, y0, z0, x1, y1, z1, red, green, blue, alpha);
            }
        }
    }

    private static double cellSizeFor(List<Vec3> points) {
        if (!ElectrovoxConfig.lodEnabled()) {
            return FINE_CELL;
        }
        Minecraft minecraft = Minecraft.getInstance();
        if (minecraft.gameRenderer == null) {
            return FINE_CELL;
        }
        Vec3 camera = minecraft.gameRenderer.getMainCamera().getPosition();
        Vec3 middle = points.get(points.size() / 2);
        return middle.distanceTo(camera) > ElectrovoxConfig.lodDistance() ? COARSE_CELL : FINE_CELL;
    }

    private static LongOpenHashSet voxelize(List<Vec3> points, Vec3 origin, double cell) {
        LongOpenHashSet cells = new LongOpenHashSet();

        for (int i = 0; i < points.size() - 1; i++) {
            walkSegment(points.get(i), points.get(i + 1), origin, cell, cells);
            if (cells.size() > MAX_CELLS) {
                break;
            }
        }
        return cells;
    }

    /**
     * Amanatides-Woo style traversal in grid space, so every cell the segment
     * actually passes through is added exactly once.
     */
    private static void walkSegment(Vec3 a, Vec3 b, Vec3 origin, double cell,
                                    LongOpenHashSet cells) {
        double ax = (a.x - origin.x) / cell;
        double ay = (a.y - origin.y) / cell;
        double az = (a.z - origin.z) / cell;
        double bx = (b.x - origin.x) / cell;
        double by = (b.y - origin.y) / cell;
        double bz = (b.z - origin.z) / cell;

        int x = (int) Math.floor(ax);
        int y = (int) Math.floor(ay);
        int z = (int) Math.floor(az);
        int endX = (int) Math.floor(bx);
        int endY = (int) Math.floor(by);
        int endZ = (int) Math.floor(bz);

        double dx = bx - ax;
        double dy = by - ay;
        double dz = bz - az;

        int stepX = dx > 0 ? 1 : (dx < 0 ? -1 : 0);
        int stepY = dy > 0 ? 1 : (dy < 0 ? -1 : 0);
        int stepZ = dz > 0 ? 1 : (dz < 0 ? -1 : 0);

        double tDeltaX = stepX == 0 ? Double.POSITIVE_INFINITY : Math.abs(1.0D / dx);
        double tDeltaY = stepY == 0 ? Double.POSITIVE_INFINITY : Math.abs(1.0D / dy);
        double tDeltaZ = stepZ == 0 ? Double.POSITIVE_INFINITY : Math.abs(1.0D / dz);

        double tMaxX = initialT(ax, x, dx, tDeltaX, stepX);
        double tMaxY = initialT(ay, y, dy, tDeltaY, stepY);
        double tMaxZ = initialT(az, z, dz, tDeltaZ, stepZ);

        cells.add(pack(x, y, z));

        int steps = 0;
        while (steps++ < MAX_STEPS_PER_SEGMENT) {
            if (x == endX && y == endY && z == endZ) {
                break;
            }
            if (tMaxX <= tMaxY && tMaxX <= tMaxZ) {
                if (tMaxX > 1.0D) {
                    break;
                }
                x += stepX;
                tMaxX += tDeltaX;
            } else if (tMaxY <= tMaxZ) {
                if (tMaxY > 1.0D) {
                    break;
                }
                y += stepY;
                tMaxY += tDeltaY;
            } else {
                if (tMaxZ > 1.0D) {
                    break;
                }
                z += stepZ;
                tMaxZ += tDeltaZ;
            }
            cells.add(pack(x, y, z));
            if (cells.size() > MAX_CELLS) {
                return;
            }
        }
    }

    private static double initialT(double start, int cellIndex, double delta,
                                   double tDelta, int step) {
        if (step == 0) {
            return Double.POSITIVE_INFINITY;
        }
        double boundary = step > 0 ? cellIndex + 1.0D : cellIndex;
        double t = (boundary - start) / delta;
        return t < 0 ? tDelta : t;
    }

    private static float lightAt(BlockAndTintGetter level, double x, double y, double z) {
        if (level == null) {
            return 1.0F;
        }
        try {
            int packed = LevelRenderer.getLightColor(level, BlockPos.containing(x, y, z));
            // 1.21.1 packs light as sky << 20 | block << 4, so block brightness
            // sits in bits 4..19 and sky brightness in bits 20..35. Reading the
            // low word straight gives values up to 240 instead of 0..15, which
            // multiplied the tint by ~12 and clipped every wire to white.
            int block = (packed & 0xFFFF) >> 4;
            int sky = (packed >> 20) & 0xFFFF;
            int raw = Math.min(15, Math.max(block, sky));
            return 0.28F + 0.72F * (raw / 15.0F);
        } catch (Throwable ignored) {
            return 1.0F;
        }
    }

    private static void face(VertexConsumer consumer, PoseStack.Pose pose, int face,
                             double x0, double y0, double z0,
                             double x1, double y1, double z1,
                             float red, float green, float blue, float alpha) {
        float shade = FACE_SHADE[face];
        float r = clamp01(red * shade);
        float g = clamp01(green * shade);
        float b = clamp01(blue * shade);

        switch (face) {
            case FACE_DOWN -> {
                vertex(consumer, pose, x0, y0, z0, r, g, b, alpha);
                vertex(consumer, pose, x1, y0, z0, r, g, b, alpha);
                vertex(consumer, pose, x1, y0, z1, r, g, b, alpha);
                vertex(consumer, pose, x0, y0, z1, r, g, b, alpha);
            }
            case FACE_UP -> {
                vertex(consumer, pose, x0, y1, z1, r, g, b, alpha);
                vertex(consumer, pose, x1, y1, z1, r, g, b, alpha);
                vertex(consumer, pose, x1, y1, z0, r, g, b, alpha);
                vertex(consumer, pose, x0, y1, z0, r, g, b, alpha);
            }
            case FACE_NORTH -> {
                vertex(consumer, pose, x1, y0, z0, r, g, b, alpha);
                vertex(consumer, pose, x0, y0, z0, r, g, b, alpha);
                vertex(consumer, pose, x0, y1, z0, r, g, b, alpha);
                vertex(consumer, pose, x1, y1, z0, r, g, b, alpha);
            }
            case FACE_SOUTH -> {
                vertex(consumer, pose, x0, y0, z1, r, g, b, alpha);
                vertex(consumer, pose, x1, y0, z1, r, g, b, alpha);
                vertex(consumer, pose, x1, y1, z1, r, g, b, alpha);
                vertex(consumer, pose, x0, y1, z1, r, g, b, alpha);
            }
            case FACE_WEST -> {
                vertex(consumer, pose, x0, y0, z0, r, g, b, alpha);
                vertex(consumer, pose, x0, y0, z1, r, g, b, alpha);
                vertex(consumer, pose, x0, y1, z1, r, g, b, alpha);
                vertex(consumer, pose, x0, y1, z0, r, g, b, alpha);
            }
            case FACE_EAST -> {
                vertex(consumer, pose, x1, y0, z1, r, g, b, alpha);
                vertex(consumer, pose, x1, y0, z0, r, g, b, alpha);
                vertex(consumer, pose, x1, y1, z0, r, g, b, alpha);
                vertex(consumer, pose, x1, y1, z1, r, g, b, alpha);
            }
            default -> {
            }
        }
    }

    private static void vertex(VertexConsumer consumer, PoseStack.Pose pose,
                               double x, double y, double z,
                               float r, float g, float b, float alpha) {
        consumer.addVertex(pose, (float) x, (float) y, (float) z)
                .setColor(r, g, b, alpha);
    }

    private static float clamp01(float value) {
        return value < 0.0F ? 0.0F : (value > 1.0F ? 1.0F : value);
    }

    private static long pack(int x, int y, int z) {
        return ((long) (x & 0x1FFFFF) << 42) | ((long) (y & 0x1FFFFF) << 21) | (z & 0x1FFFFF);
    }

    private static int unpackX(long packed) {
        return signExtend21((int) (packed >>> 42));
    }

    private static int unpackY(long packed) {
        return signExtend21((int) (packed >>> 21));
    }

    private static int unpackZ(long packed) {
        return signExtend21((int) packed);
    }

    private static int signExtend21(int value) {
        int masked = value & 0x1FFFFF;
        return (masked & 0x100000) != 0 ? masked | ~0x1FFFFF : masked;
    }
}
