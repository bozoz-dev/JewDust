package dev.axziom.features.modules.render;

import dev.axziom.event.impl.render.Render2DEvent;
import dev.axziom.event.impl.render.Render3DEvent;
import dev.axziom.features.commands.TargetListCommandSource;
import dev.axziom.features.modules.Module;
import dev.axziom.features.settings.Setting;
import dev.axziom.util.render.MatrixCapture;
import dev.axziom.util.render.PortRender;
import dev.axziom.util.render.RenderUtil;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Registry;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.Identifier;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.chunk.LevelChunk;
import net.minecraft.world.level.chunk.LevelChunkSection;
import net.minecraft.world.phys.AABB;

import java.awt.Color;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

public final class SearchModule extends Module implements TargetListCommandSource {
    public final Setting<String> blockTargets = str("BlockTargets", "").setPage("Targets");
    public final Setting<String> blockEntityTargets = str("BlockEntityTargets", "").setPage("Targets");
    public final Setting<String> entityTargets = str("EntityTargets", "").setPage("Targets");
    public final Setting<String> itemTargets = str("ItemTargets", "").setPage("Targets");

    public final Setting<Double> range = num("Range", 128.0, 16.0, 512.0).setPage("Scan");
    public final Setting<Integer> chunksPerTick = num("ChunksPerTick", 1, 1, 8).setPage("Scan");
    public final Setting<Integer> rescanDelay = num("RescanDelay", 200, 20, 1200).setPage("Scan");

    public final Setting<PortRender.ShapeMode> shapeMode = mode("ShapeMode", PortRender.ShapeMode.BOTH).setPage("Render");
    public final Setting<Boolean> tracers = bool("Tracers", false).setPage("Render");
    public final Setting<Integer> maxTracers = num("MaxTracers", 128, 1, 2048).setPage("Render");
    public final Setting<Color> blockColour = color("BlockColour", 145, 79, 220, 255).setPage("Render");
    public final Setting<Color> blockEntityColour = color("BlockEntityColour", 145, 79, 220, 255).setPage("Render");
    public final Setting<Color> entityColour = color("EntityColour", 145, 79, 220, 255).setPage("Render");
    public final Setting<Color> itemColour = color("ItemColour", 145, 79, 220, 255).setPage("Render");

    private final ArrayDeque<ChunkPos> pending = new ArrayDeque<>();
    private final Set<Long> queued = new HashSet<>();
    private final Map<Long, Integer> lastScan = new HashMap<>();
    private final Map<Long, List<FoundWorldTarget>> foundByChunk = new HashMap<>();

    private final Set<String> blocks = new LinkedHashSet<>();
    private final Set<String> blockEntities = new LinkedHashSet<>();
    private final Set<String> entities = new LinkedHashSet<>();
    private final Set<String> items = new LinkedHashSet<>();
    private final Set<Block> targetBlocks = new HashSet<>();

    private Object trackedLevel;
    private String targetFingerprint = "";
    private int tick;
    private boolean rescanRequested = true;

    public SearchModule() {
        super("Search", "Searches loaded blocks, block entities, entities, and dropped items.", Category.RENDER);
    }

    @Override
    public List<TargetList> getTargetLists() {
        return List.of(
                new TargetList("blocks", "block", blockTargets,
                        SearchModule::normalizeBlock, SearchModule::blockSuggestions),
                new TargetList("blockentities", "block entity type", blockEntityTargets,
                        input -> normalize(input, BuiltInRegistries.BLOCK_ENTITY_TYPE),
                        () -> suggestions(BuiltInRegistries.BLOCK_ENTITY_TYPE)),
                new TargetList("entities", "entity type", entityTargets,
                        input -> normalize(input, BuiltInRegistries.ENTITY_TYPE),
                        () -> suggestions(BuiltInRegistries.ENTITY_TYPE)),
                new TargetList("items", "item", itemTargets,
                        input -> normalize(input, BuiltInRegistries.ITEM),
                        () -> suggestions(BuiltInRegistries.ITEM))
        );
    }

    @Override
    public void onTargetListsChanged() {
        rescanRequested = true;
    }

    @Override
    public void onEnable() {
        clear();
    }

    @Override
    public void onDisable() {
        clear();
    }

    @Override
    public void onTick() {
        if (nullCheck()) {
            clear();
            return;
        }
        if (trackedLevel != mc.level) clear();
        trackedLevel = mc.level;

        if (!targetFingerprint.equals(fingerprint())) rescanRequested = true;
        if (rescanRequested) resetScan();

        tick++;
        if (tick == 1 || tick % 20 == 0) {
            queueAroundPlayer();
            pruneFarChunks();
        }

        int budget = chunksPerTick.getValue();
        while (budget-- > 0) {
            ChunkPos pos = pending.poll();
            if (pos == null) break;
            queued.remove(pos.toLong());
            if (!mc.level.hasChunk(pos.x, pos.z)) continue;
            scanChunk(mc.level.getChunk(pos.x, pos.z));
            lastScan.put(pos.toLong(), tick);
        }
    }

    private void resetScan() {
        pending.clear();
        queued.clear();
        lastScan.clear();
        foundByChunk.clear();
        blocks.clear();
        blockEntities.clear();
        entities.clear();
        items.clear();
        blocks.addAll(TargetListCommandSource.values(blockTargets));
        blockEntities.addAll(TargetListCommandSource.values(blockEntityTargets));
        entities.addAll(TargetListCommandSource.values(entityTargets));
        items.addAll(TargetListCommandSource.values(itemTargets));
        rebuildTargetBlocks();
        targetFingerprint = fingerprint();
        rescanRequested = false;
        tick = 0;
    }

    private String fingerprint() {
        return blockTargets.getValue() + '\u0000' + blockEntityTargets.getValue() + '\u0000'
                + entityTargets.getValue() + '\u0000' + itemTargets.getValue();
    }

    private void rebuildTargetBlocks() {
        targetBlocks.clear();
        for (String target : blocks) {
            Identifier id = Identifier.tryParse(target);
            if (id != null && BuiltInRegistries.BLOCK.containsKey(id)) {
                targetBlocks.add(BuiltInRegistries.BLOCK.getValue(id));
            }
        }
    }

    private void queueAroundPlayer() {
        int radius = scanChunkRadius();
        ChunkPos center = mc.player.chunkPosition();
        for (int distance = 0; distance <= radius; distance++) {
            for (int dx = -distance; dx <= distance; dx++) {
                for (int dz = -distance; dz <= distance; dz++) {
                    if (Math.max(Math.abs(dx), Math.abs(dz)) != distance) continue;
                    int x = center.x + dx;
                    int z = center.z + dz;
                    if (!mc.level.hasChunk(x, z)) continue;
                    queue(new ChunkPos(x, z));
                }
            }
        }
    }

    private void queue(ChunkPos pos) {
        long key = pos.toLong();
        Integer last = lastScan.get(key);
        if (last != null && tick - last < rescanDelay.getValue()) return;
        if (queued.add(key)) pending.add(pos);
    }

    private void scanChunk(LevelChunk chunk) {
        List<FoundWorldTarget> found = new ArrayList<>();
        scanBlocks(chunk, found);
        scanBlockEntities(chunk, found);
        foundByChunk.put(chunk.getPos().toLong(), found);
    }

    private void scanBlocks(LevelChunk chunk, List<FoundWorldTarget> found) {
        if (blocks.isEmpty()) return;
        LevelChunkSection[] sections = chunk.getSections();
        int baseX = chunk.getPos().x << 4;
        int baseZ = chunk.getPos().z << 4;

        for (int sectionIndex = 0; sectionIndex < sections.length; sectionIndex++) {
            LevelChunkSection section = sections[sectionIndex];
            if (section == null || section.hasOnlyAir() || !section.maybeHas(this::matchesBlock)) continue;
            int baseY = chunk.getSectionYFromSectionIndex(sectionIndex) << 4;

            for (int x = 0; x < 16; x++) {
                for (int y = 0; y < 16; y++) {
                    for (int z = 0; z < 16; z++) {
                        FoundKind kind = blockKind(section.getBlockState(x, y, z));
                        if (kind == null) continue;
                        found.add(new FoundWorldTarget(new BlockPos(baseX + x, baseY + y, baseZ + z), kind));
                    }
                }
            }
        }
    }

    private void scanBlockEntities(LevelChunk chunk, List<FoundWorldTarget> found) {
        if (blockEntities.isEmpty()) return;
        for (BlockEntity blockEntity : chunk.getBlockEntities().values()) {
            Identifier id = BuiltInRegistries.BLOCK_ENTITY_TYPE.getKey(blockEntity.getType());
            if (id != null && blockEntities.contains(id.toString())) {
                found.add(new FoundWorldTarget(blockEntity.getBlockPos().immutable(), FoundKind.BLOCK_ENTITY));
            }
        }
    }

    private boolean matchesBlock(BlockState state) {
        return blockKind(state) != null;
    }

    private FoundKind blockKind(BlockState state) {
        return targetBlocks.contains(state.getBlock()) ? FoundKind.BLOCK : null;
    }

    private int scanChunkRadius() {
        int wanted = (int) Math.ceil(range.getValue() / 16.0);
        return Math.min(wanted, mc.options.renderDistance().get() + 1);
    }

    private void pruneFarChunks() {
        ChunkPos center = mc.player.chunkPosition();
        int keepRadius = scanChunkRadius() + 2;
        foundByChunk.keySet().removeIf(key -> farFrom(center, key, keepRadius)
                || !mc.level.hasChunk(ChunkPos.getX(key), ChunkPos.getZ(key)));
        lastScan.keySet().removeIf(key -> farFrom(center, key, keepRadius));
        pending.removeIf(pos -> Math.abs(pos.x - center.x) > keepRadius
                || Math.abs(pos.z - center.z) > keepRadius);
        queued.removeIf(key -> farFrom(center, key, keepRadius));
    }

    private static boolean farFrom(ChunkPos center, long key, int radius) {
        return Math.abs(ChunkPos.getX(key) - center.x) > radius
                || Math.abs(ChunkPos.getZ(key) - center.z) > radius;
    }

    @Override
    public void onRender3D(Render3DEvent event) {
        if (nullCheck()) return;
        double maxSq = range.getValue() * range.getValue();
        BlockPos playerPos = mc.player.blockPosition();

        for (List<FoundWorldTarget> chunk : foundByChunk.values()) {
            for (FoundWorldTarget found : chunk) {
                if (playerPos.distSqr(found.pos) > maxSq) continue;
                Color colour = colourFor(found.kind);
                PortRender.box(event.getMatrix(), found.pos, colour, colour, shapeMode.getValue());
            }
        }

        for (EntityTarget target : currentEntityTargets(maxSq)) {
            Color colour = colourFor(target.kind);
            PortRender.box(event.getMatrix(), target.box, colour, colour, shapeMode.getValue());
        }
    }

    @Override
    public void onRender2D(Render2DEvent event) {
        if (nullCheck() || !tracers.getValue()) return;
        double maxSq = range.getValue() * range.getValue();
        BlockPos playerPos = mc.player.blockPosition();
        float centerX = mc.getWindow().getGuiScaledWidth() * 0.5f;
        float centerY = mc.getWindow().getGuiScaledHeight() * 0.5f;
        int rendered = 0;

        outer:
        for (List<FoundWorldTarget> chunk : foundByChunk.values()) {
            for (FoundWorldTarget found : chunk) {
                if (playerPos.distSqr(found.pos) > maxSq) continue;
                float[] screen = MatrixCapture.worldToScreenClamped(
                        found.pos.getX() + 0.5, found.pos.getY() + 0.5, found.pos.getZ() + 0.5);
                if (screen != null) {
                    RenderUtil.line2D(event.getContext(), centerX, centerY, screen[0], screen[1],
                            colourFor(found.kind), 1.5f);
                    if (++rendered >= maxTracers.getValue()) break outer;
                }
            }
        }

        if (rendered >= maxTracers.getValue()) return;
        for (EntityTarget target : currentEntityTargets(maxSq)) {
            float[] screen = MatrixCapture.worldToScreenClamped(
                    target.box.getCenter().x, target.box.getCenter().y, target.box.getCenter().z);
            if (screen != null) {
                RenderUtil.line2D(event.getContext(), centerX, centerY, screen[0], screen[1],
                        colourFor(target.kind), 1.5f);
                if (++rendered >= maxTracers.getValue()) break;
            }
        }
    }

    private List<EntityTarget> currentEntityTargets(double maxSq) {
        List<EntityTarget> found = new ArrayList<>();
        if (entities.isEmpty() && items.isEmpty()) return found;
        for (Entity entity : mc.level.entitiesForRendering()) {
            if (entity == mc.player || mc.player.distanceToSqr(entity) > maxSq) continue;
            FoundKind kind = null;
            if (entity instanceof ItemEntity itemEntity) {
                Identifier itemId = BuiltInRegistries.ITEM.getKey(itemEntity.getItem().getItem());
                if (itemId != null && items.contains(itemId.toString())) kind = FoundKind.ITEM;
            }
            if (kind == null) {
                Identifier entityId = BuiltInRegistries.ENTITY_TYPE.getKey(entity.getType());
                if (entityId != null && entities.contains(entityId.toString())) kind = FoundKind.ENTITY;
            }
            if (kind != null) found.add(new EntityTarget(entity.getBoundingBox(), kind));
        }
        return found;
    }

    private Color colourFor(FoundKind kind) {
        return switch (kind) {
            case BLOCK -> blockColour.getValue();
            case BLOCK_ENTITY -> blockEntityColour.getValue();
            case ENTITY -> entityColour.getValue();
            case ITEM -> itemColour.getValue();
        };
    }

    @Override
    public String getDisplayInfo() {
        int count = 0;
        for (List<FoundWorldTarget> chunk : foundByChunk.values()) count += chunk.size();
        if (!nullCheck()) count += currentEntityTargets(range.getValue() * range.getValue()).size();
        return Integer.toString(count);
    }

    private void clear() {
        pending.clear();
        queued.clear();
        lastScan.clear();
        foundByChunk.clear();
        blocks.clear();
        blockEntities.clear();
        entities.clear();
        items.clear();
        targetBlocks.clear();
        trackedLevel = null;
        targetFingerprint = "";
        tick = 0;
        rescanRequested = true;
    }

    private static String normalizeBlock(String input) {
        return normalize(input, BuiltInRegistries.BLOCK);
    }

    private static <T> String normalize(String input, Registry<T> registry) {
        String value = input.trim().toLowerCase(Locale.ROOT);
        if (!value.contains(":")) value = "minecraft:" + value;
        Identifier id = Identifier.tryParse(value);
        return id != null && registry.containsKey(id) ? id.toString() : null;
    }

    private static Collection<String> blockSuggestions() {
        return suggestions(BuiltInRegistries.BLOCK);
    }

    private static <T> Collection<String> suggestions(Registry<T> registry) {
        List<String> values = new ArrayList<>();
        registry.keySet().forEach(id -> values.add(id.toString()));
        values.sort(String::compareTo);
        return values;
    }

    private enum FoundKind {
        BLOCK, BLOCK_ENTITY, ENTITY, ITEM
    }

    private record FoundWorldTarget(BlockPos pos, FoundKind kind) {
    }

    private record EntityTarget(AABB box, FoundKind kind) {
    }
}

