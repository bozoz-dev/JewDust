package dev.axziom.features.modules.render;

import dev.axziom.event.impl.render.Render3DEvent;
import dev.axziom.features.modules.Module;
import dev.axziom.features.settings.Setting;
import dev.axziom.util.render.PortRender;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.block.entity.BarrelBlockEntity;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.ChestBlockEntity;
import net.minecraft.world.level.block.entity.EnderChestBlockEntity;
import net.minecraft.world.level.block.entity.ShulkerBoxBlockEntity;
import net.minecraft.world.level.chunk.LevelChunk;

import java.awt.Color;
import java.util.HashMap;
import java.util.Map;

public final class StorageEspModule extends Module {
    public final Setting<Boolean> chests = bool("Chests", true);
    public final Setting<Boolean> barrels = bool("Barrels", true);
    public final Setting<Boolean> shulkers = bool("Shulkers", true);
    public final Setting<Boolean> enderChests = bool("EnderChests", true);
    public final Setting<Double> range = num("Range", 128.0, 16.0, 512.0);
    public final Setting<PortRender.ShapeMode> shapeMode = mode("ShapeMode", PortRender.ShapeMode.BOTH);

    private final Map<BlockPos, StorageType> found = new HashMap<>();
    private Object trackedLevel;
    private int scanTicks;

    public StorageEspModule() {
        super("StorageESP", "Highlights selected storage blocks in loaded chunks.", Category.RENDER);
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
        if (scanTicks-- > 0) return;
        scanTicks = 19;
        scan();
    }

    private void scan() {
        int requestedRadius = (int) Math.ceil(range.getValue() / 16.0);
        int loadedRadius = mc.options.renderDistance().get() + 2;
        int radius = Math.min(requestedRadius, loadedRadius);
        double maxSq = range.getValue() * range.getValue();
        ChunkPos center = mc.player.chunkPosition();
        Map<BlockPos, StorageType> next = new HashMap<>();

        for (int x = center.x - radius; x <= center.x + radius; x++) {
            for (int z = center.z - radius; z <= center.z + radius; z++) {
                LevelChunk chunk = mc.level.getChunkSource().getChunk(x, z, false);
                if (chunk == null) continue;
                for (BlockEntity blockEntity : chunk.getBlockEntities().values()) {
                    StorageType type = typeOf(blockEntity);
                    if (type == null || !enabled(type)) continue;
                    BlockPos pos = blockEntity.getBlockPos();
                    if (mc.player.blockPosition().distSqr(pos) <= maxSq) next.put(pos.immutable(), type);
                }
            }
        }
        found.clear();
        found.putAll(next);
    }

    @Override
    public void onRender3D(Render3DEvent event) {
        if (nullCheck()) return;
        double maxSq = range.getValue() * range.getValue();
        for (Map.Entry<BlockPos, StorageType> entry : found.entrySet()) {
            if (mc.player.blockPosition().distSqr(entry.getKey()) > maxSq) continue;
            Color line = entry.getValue().colour;
            Color side = new Color(line.getRed(), line.getGreen(), line.getBlue(), 55);
            PortRender.box(event.getMatrix(), entry.getKey(), side, line, shapeMode.getValue());
        }
    }

    @Override
    public String getDisplayInfo() {
        return Integer.toString(found.size());
    }

    private StorageType typeOf(BlockEntity blockEntity) {
        if (blockEntity instanceof ChestBlockEntity) return StorageType.CHEST;
        if (blockEntity instanceof BarrelBlockEntity) return StorageType.BARREL;
        if (blockEntity instanceof ShulkerBoxBlockEntity) return StorageType.SHULKER;
        if (blockEntity instanceof EnderChestBlockEntity) return StorageType.ENDER_CHEST;
        return null;
    }

    private boolean enabled(StorageType type) {
        return switch (type) {
            case CHEST -> chests.getValue();
            case BARREL -> barrels.getValue();
            case SHULKER -> shulkers.getValue();
            case ENDER_CHEST -> enderChests.getValue();
        };
    }

    private void clear() {
        found.clear();
        trackedLevel = null;
        scanTicks = 0;
    }

    private enum StorageType {
        CHEST(new Color(145, 79, 220, 255)),
        BARREL(new Color(145, 79, 220, 255)),
        SHULKER(new Color(145, 79, 220, 255)),
        ENDER_CHEST(new Color(145, 79, 220, 255));

        private final Color colour;

        StorageType(Color colour) {
            this.colour = colour;
        }
    }
}
