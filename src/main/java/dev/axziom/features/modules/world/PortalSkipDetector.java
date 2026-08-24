package dev.axziom.features.modules.world;

import dev.axziom.features.commands.Command;
import dev.axziom.event.impl.render.Render3DEvent;
import dev.axziom.features.modules.Module;
import dev.axziom.features.settings.Setting;
import dev.axziom.util.render.PortRender;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.chunk.LevelChunk;
import net.minecraft.world.level.chunk.LevelChunkSection;
import net.minecraft.world.phys.AABB;

import java.awt.Color;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

public final class PortalSkipDetector extends Module {
    private static final int PORTAL_WIDTH = 4;
    private static final int PORTAL_HEIGHT = 5;
    private static final int MAX_CANDIDATES_PER_CHUNK = 6000;

    public final Setting<Integer> chunkRadius = num("ChunkRadius", 4, 1, 8);
    public final Setting<Integer> chunksPerTick = num("ChunksPerTick", 2, 1, 8);
    public final Setting<Integer> minBoundedSides = num("MinBoundedSides", 2, 0, 4);
    public final Setting<Integer> minCaveAirNearby = num("MinCaveAirNearby", 10, 0, 18);
    public final Setting<Boolean> notify = bool("Notify", true);
    public final Setting<Boolean> render = bool("Render", true).setPage("Render");
    public final Setting<PortRender.ShapeMode> shapeMode = mode("ShapeMode", PortRender.ShapeMode.BOTH).setPage("Render");
    public final Setting<Color> sideColor = color("SideColour", 145, 79, 220, 255).setPage("Render");
    public final Setting<Color> lineColor = color("LineColour", 145, 79, 220, 255).setPage("Render");

    private final Set<Long> scannedChunks = new HashSet<>();
    private final Set<Long> foundKeys = new HashSet<>();
    private final List<AABB> foundBoxes = new ArrayList<>();
    private final ArrayDeque<ChunkPos> pendingChunks = new ArrayDeque<>();
    private Object trackedLevel;
    private int refreshCounter;

    public PortalSkipDetector() {
        super("PortalSkipDetector", "Scans loaded chunks for portal-sized air pockets carved into solid terrain.", Category.WORLD);
    }

    @Override
    public void onEnable() {
        clear();
    }

    @Override
    public void onDisable() {
        pendingChunks.clear();
    }

    @Override
    public void onTick() {
        if (nullCheck()) return;
        if (trackedLevel != mc.level) clear();
        trackedLevel = mc.level;
        if (refreshCounter-- <= 0) {
            refreshCounter = 20;
            pruneFarChunks();
            queueNewChunks();
        }

        int budget = chunksPerTick.getValue();
        while (budget-- > 0) {
            ChunkPos pos = pendingChunks.poll();
            if (pos == null) break;
            if (!mc.level.hasChunk(pos.x, pos.z)) continue;
            scanChunk(mc.level.getChunk(pos.x, pos.z));
            scannedChunks.add(pos.toLong());
        }
    }

    private void queueNewChunks() {
        ChunkPos center = mc.player.chunkPosition();
        int radius = chunkRadius.getValue();
        Set<Long> queued = new HashSet<>();
        for (ChunkPos pos : pendingChunks) queued.add(pos.toLong());

        for (int dx = -radius; dx <= radius; dx++) {
            for (int dz = -radius; dz <= radius; dz++) {
                if (dx * dx + dz * dz > radius * radius) continue;
                ChunkPos pos = new ChunkPos(center.x + dx, center.z + dz);
                if (!scannedChunks.contains(pos.toLong()) && !queued.contains(pos.toLong())
                        && mc.level.hasChunk(pos.x, pos.z)) {
                    pendingChunks.add(pos);
                }
            }
        }
    }

    private void pruneFarChunks() {
        ChunkPos center = mc.player.chunkPosition();
        int keepRadius = chunkRadius.getValue() + 2;
        int keepSq = keepRadius * keepRadius;
        scannedChunks.removeIf(key -> {
            ChunkPos pos = new ChunkPos(key);
            int dx = pos.x - center.x;
            int dz = pos.z - center.z;
            return dx * dx + dz * dz > keepSq;
        });
    }

    private void scanChunk(LevelChunk chunk) {
        LevelChunkSection[] sections = chunk.getSections();
        int maxY = Level.NETHER.equals(mc.level.dimension()) ? 128 : Math.min(mc.level.getMaxY(), 180);
        int candidates = 0;

        for (int sectionIndex = 0; sectionIndex < sections.length; sectionIndex++) {
            LevelChunkSection section = sections[sectionIndex];
            if (section == null || section.hasOnlyAir()) continue;
            int baseY = chunk.getSectionYFromSectionIndex(sectionIndex) << 4;
            if (baseY > maxY) continue;
            int baseX = chunk.getPos().x << 4;
            int baseZ = chunk.getPos().z << 4;

            for (int x = 0; x < 16 && candidates < MAX_CANDIDATES_PER_CHUNK; x++) {
                for (int y = 0; y < 16 && candidates < MAX_CANDIDATES_PER_CHUNK; y++) {
                    for (int z = 0; z < 16 && candidates < MAX_CANDIDATES_PER_CHUNK; z++) {
                        int worldY = baseY + y;
                        if (worldY > maxY || !isEmpty(section.getBlockState(x, y, z))) continue;
                        BlockPos pos = new BlockPos(baseX + x, worldY, baseZ + z);
                        if (!hasSolidNeighbor(pos)) continue;
                        candidates++;
                        tryFitPortalNear(pos, true);
                        tryFitPortalNear(pos, false);
                    }
                }
            }
        }
    }

    private boolean hasSolidNeighbor(BlockPos pos) {
        for (Direction direction : Direction.values()) {
            if (!isEmpty(mc.level.getBlockState(pos.relative(direction)))) return true;
        }
        return false;
    }

    private void tryFitPortalNear(BlockPos anchor, boolean xAligned) {
        for (int width = 0; width < PORTAL_WIDTH; width++) {
            for (int height = 0; height < PORTAL_HEIGHT; height++) {
                checkFrame(xAligned ? anchor.offset(-width, -height, 0) : anchor.offset(0, -height, -width), xAligned);
            }
        }
    }

    private void checkFrame(BlockPos corner, boolean xAligned) {
        long key = (corner.asLong() << 1) | (xAligned ? 1L : 0L);
        if (foundKeys.contains(key)) return;

        int airBlocks = 0;
        for (int height = 0; height < PORTAL_HEIGHT; height++) {
            for (int width = 0; width < PORTAL_WIDTH; width++) {
                BlockPos pos = xAligned ? corner.offset(width, height, 0) : corner.offset(0, height, width);
                boolean empty = isEmpty(mc.level.getBlockState(pos));
                boolean cornerBlock = (height == 0 || height == PORTAL_HEIGHT - 1)
                        && (width == 0 || width == PORTAL_WIDTH - 1);
                if (empty) airBlocks++;
                else if (!cornerBlock) return;
            }
        }

        if (airBlocks < PORTAL_WIDTH * PORTAL_HEIGHT - 4 || !isPortalSkipContext(corner, xAligned)) return;
        foundKeys.add(key);
        BlockPos end = xAligned ? corner.offset(PORTAL_WIDTH - 1, PORTAL_HEIGHT - 1, 0)
                : corner.offset(0, PORTAL_HEIGHT - 1, PORTAL_WIDTH - 1);
        foundBoxes.add(new AABB(Math.min(corner.getX(), end.getX()), Math.min(corner.getY(), end.getY()),
                Math.min(corner.getZ(), end.getZ()), Math.max(corner.getX(), end.getX()) + 1,
                Math.max(corner.getY(), end.getY()) + 1, Math.max(corner.getZ(), end.getZ()) + 1));
        if (notify.getValue()) Command.sendMessage("Possible portal skip at %d, %d, %d", corner.getX(), corner.getY(), corner.getZ());
    }

    private boolean isPortalSkipContext(BlockPos corner, boolean xAligned) {
        int caveAir = 0;
        int boundedSides = 0;
        int[][] sides = {{-1, 0, 0, 0, 1}, {PORTAL_WIDTH, 0, 0, 0, 1},
                {0, -1, 0, 1, 0}, {0, PORTAL_HEIGHT, 0, 1, 0}};

        for (int[] side : sides) {
            boolean bounded = true;
            int length = side[3] == 1 ? PORTAL_WIDTH : PORTAL_HEIGHT;
            for (int i = 0; i < length; i++) {
                int width = side[3] == 1 ? i : side[0];
                int height = side[4] == 1 ? i : side[1];
                BlockPos pos = xAligned ? corner.offset(width, height, 0) : corner.offset(0, height, width);
                BlockState state = mc.level.getBlockState(pos);
                if (state.is(Blocks.AIR)) bounded = false;
                if (state.is(Blocks.CAVE_AIR)) caveAir++;
            }
            if (bounded) boundedSides++;
        }
        return boundedSides >= minBoundedSides.getValue() && caveAir >= minCaveAirNearby.getValue();
    }

    @Override
    public void onRender3D(Render3DEvent event) {
        if (!render.getValue() || nullCheck()) return;
        for (AABB box : foundBoxes) PortRender.box(event.getMatrix(), box, sideColor.getValue(), lineColor.getValue(), shapeMode.getValue());
    }

    @Override
    public String getDisplayInfo() {
        return foundBoxes.size() + " | " + scannedChunks.size();
    }

    private void clear() {
        scannedChunks.clear();
        foundKeys.clear();
        foundBoxes.clear();
        pendingChunks.clear();
        trackedLevel = null;
        refreshCounter = 0;
    }

    private static boolean isEmpty(BlockState state) {
        return state.is(Blocks.AIR) || state.is(Blocks.CAVE_AIR);
    }
}
