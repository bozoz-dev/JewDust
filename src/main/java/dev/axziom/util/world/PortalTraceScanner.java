package dev.axziom.util.world;

import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.chunk.LevelChunk;
import net.minecraft.world.phys.AABB;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/** Main-thread scanner for regular-air 4x5 portal traces bordered by cave air. */
public final class PortalTraceScanner {
    public static final int WIDTH = 4;
    public static final int HEIGHT = 5;

    private PortalTraceScanner() {
    }

    public static List<Trace> scan(ClientLevel world, LevelChunk chunk, int minY, int maxY,
                                   int minimumCaveAir, int minimumBorderSides, int maxMatches) {
        List<Trace> result = new ArrayList<>();
        Set<Long> seen = new HashSet<>();
        int startX = chunk.getPos().getMinBlockX();
        int startZ = chunk.getPos().getMinBlockZ();
        int low = Math.max(minY, world.getMinY());
        int high = Math.min(maxY, world.getMaxY() - HEIGHT);

        outer:
        for (int x = startX; x < startX + 16; x++) {
            for (int z = startZ; z < startZ + 16; z++) {
                for (int y = low; y <= high; y++) {
                    BlockPos corner = new BlockPos(x, y, z);
                    for (boolean xAligned : new boolean[]{true, false}) {
                        long key = (corner.asLong() << 1) | (xAligned ? 1L : 0L);
                        if (!seen.add(key)) continue;
                        Trace trace = test(world, corner, xAligned, minimumCaveAir, minimumBorderSides);
                        if (trace != null) {
                            result.add(trace);
                            if (result.size() >= maxMatches) break outer;
                        }
                    }
                }
            }
        }
        return result;
    }

    private static Trace test(ClientLevel world, BlockPos corner, boolean xAligned, int minCaveAir, int minSides) {
        for (int height = 0; height < HEIGHT; height++) {
            for (int width = 0; width < WIDTH; width++) {
                BlockPos pos = xAligned ? corner.offset(width, height, 0) : corner.offset(0, height, width);
                BlockState state = loadedState(world, pos);
                if (state == null || !state.is(Blocks.AIR)) return null;
            }
        }

        int cave = 0;
        int sides = 0;
        int count = caveAirOnSide(world, corner, xAligned, -1, 0, 0, 1, HEIGHT);
        cave += count;
        if (count > 0) sides++;
        count = caveAirOnSide(world, corner, xAligned, WIDTH, 0, 0, 1, HEIGHT);
        cave += count;
        if (count > 0) sides++;
        count = caveAirOnSide(world, corner, xAligned, 0, -1, 1, 0, WIDTH);
        cave += count;
        if (count > 0) sides++;
        count = caveAirOnSide(world, corner, xAligned, 0, HEIGHT, 1, 0, WIDTH);
        cave += count;
        if (count > 0) sides++;

        if (cave < minCaveAir || sides < minSides) return null;
        BlockPos end = xAligned ? corner.offset(WIDTH - 1, HEIGHT - 1, 0)
                : corner.offset(0, HEIGHT - 1, WIDTH - 1);
        AABB box = new AABB(Math.min(corner.getX(), end.getX()), corner.getY(), Math.min(corner.getZ(), end.getZ()),
                Math.max(corner.getX(), end.getX()) + 1, corner.getY() + HEIGHT, Math.max(corner.getZ(), end.getZ()) + 1);
        return new Trace(corner.immutable(), xAligned, box, cave, sides);
    }

    private static int caveAirOnSide(ClientLevel world, BlockPos corner, boolean xAligned,
                                     int width, int height, int widthStep, int heightStep, int length) {
        int result = 0;
        for (int index = 0; index < length; index++) {
            int w = width + widthStep * index;
            int h = height + heightStep * index;
            BlockPos pos = xAligned ? corner.offset(w, h, 0) : corner.offset(0, h, w);
            if (isCaveAir(world, pos)) result++;
        }
        return result;
    }

    private static boolean isCaveAir(ClientLevel world, BlockPos pos) {
        BlockState state = loadedState(world, pos);
        return state != null && state.is(Blocks.CAVE_AIR);
    }

    public static BlockState loadedState(ClientLevel world, BlockPos pos) {
        if (!world.hasChunk(pos.getX() >> 4, pos.getZ() >> 4)) return null;
        return world.getBlockState(pos);
    }

    public record Trace(BlockPos origin, boolean xAligned, AABB box, int caveAirBlocks, int caveAirSides) {
    }
}
