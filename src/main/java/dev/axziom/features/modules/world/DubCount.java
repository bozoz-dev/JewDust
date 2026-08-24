package dev.axziom.features.modules.world;

import dev.axziom.features.commands.Command;
import dev.axziom.features.modules.Module;
import dev.axziom.features.settings.Setting;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.ChestBlockEntity;
import net.minecraft.world.level.chunk.LevelChunk;

import java.util.HashSet;
import java.util.Locale;
import java.util.Set;

public final class DubCount extends Module {
    public enum CountMode { LOADED, RENDERED }
    public enum CountingMode { DUBS, SINGLE_CHESTS }

    public final Setting<Boolean> autoUpdate = bool("AutoUpdate", true);
    public final Setting<Boolean> showNotifications = bool("ShowNotifications", false);
    public final Setting<Integer> updateInterval = num("UpdateIntervalSeconds", 55, 1, 60);
    public final Setting<Integer> loadTime = num("RenderedSampleSeconds", 1, 1, 60);
    public final Setting<CountMode> countMode = mode("CountMode", CountMode.LOADED);
    public final Setting<CountingMode> countingMode = mode("CountingMode", CountingMode.DUBS);

    private int ticks;
    private int currentChestCount;
    private double currentCount;

    public DubCount() {
        super("DubCount", "Counts double chests or individual chest blocks in loaded chunks.", Category.WORLD);
    }

    @Override
    public void onEnable() {
        ticks = 0;
        updateCount(true);
    }

    @Override
    public void onTick() {
        if (!autoUpdate.getValue() || nullCheck()) return;
        int seconds = countMode.getValue() == CountMode.RENDERED ? loadTime.getValue() : updateInterval.getValue();
        if (++ticks >= seconds * 20) {
            ticks = 0;
            updateCount(showNotifications.getValue());
        }
    }

    private void updateCount(boolean notify) {
        if (nullCheck()) return;
        Set<BlockPos> positions = new HashSet<>();
        int radius = mc.options.renderDistance().get() + 2;
        double maxSq = Double.POSITIVE_INFINITY;
        if (countMode.getValue() == CountMode.RENDERED) {
            double blocks = mc.options.renderDistance().get() * 16.0 + 16.0;
            maxSq = blocks * blocks;
        }
        ChunkPos center = mc.player.chunkPosition();
        for (int x = center.x - radius; x <= center.x + radius; x++) {
            for (int z = center.z - radius; z <= center.z + radius; z++) {
                LevelChunk chunk = mc.level.getChunkSource().getChunk(x, z, false);
                if (chunk == null) continue;
                for (BlockEntity entity : chunk.getBlockEntities().values()) {
                    if (entity instanceof ChestBlockEntity && mc.player.blockPosition().distSqr(entity.getBlockPos()) <= maxSq) {
                        positions.add(entity.getBlockPos().immutable());
                    }
                }
            }
        }
        currentChestCount = positions.size();
        currentCount = countingMode.getValue() == CountingMode.DUBS ? currentChestCount / 2.0 : currentChestCount;
        if (notify) {
            Command.sendMessage("There are roughly %s %s (%d chest blocks) %s.",
                    formattedCount(), countingMode.getValue() == CountingMode.DUBS ? "dubs" : "single chests",
                    currentChestCount, countMode.getValue().name().toLowerCase(Locale.ROOT));
        }
    }

    private String formattedCount() {
        return countingMode.getValue() == CountingMode.DUBS
                ? String.format(Locale.ROOT, "%.1f", currentCount)
                : Integer.toString((int) currentCount);
    }

    @Override
    public String getDisplayInfo() {
        return formattedCount();
    }
}
