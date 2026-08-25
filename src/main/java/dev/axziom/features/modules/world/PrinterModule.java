package dev.axziom.features.modules.world;

import baritone.api.BaritoneAPI;
import baritone.api.IBaritone;
import baritone.api.pathing.goals.GoalNear;
import baritone.api.selection.ISelection;
import dev.axziom.JewDust;
import dev.axziom.event.impl.render.Render3DEvent;
import dev.axziom.features.commands.Command;
import dev.axziom.features.modules.Module;
import dev.axziom.features.settings.Setting;
import dev.axziom.util.PlaceUtil;
import dev.axziom.util.inventory.InventoryUtil;
import dev.axziom.util.inventory.Result;
import dev.axziom.util.inventory.ResultType;
import dev.axziom.util.render.RenderUtil;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.Identifier;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.FallingBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.Vec3;

import java.awt.Color;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashSet;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

public final class PrinterModule extends Module {
    public final Setting<String> blockId = str("Block", "minecraft:obsidian").setPage("Placement");
    public final Setting<Boolean> onlyAir = bool("OnlyAir", true).setPage("Placement");
    public final Setting<LayerType> layerType = mode("layerType", LayerType.All).setPage("Placement");
    public final Setting<Boolean> gravityCheck = bool("GravityCheck", true).setPage("Placement");
    public final Setting<Integer> placementsPerTick = num("PlacementsPerTick", 4, 1, 9)
            .setPage("Placement");
    public final Setting<Boolean> moveFromInventory = bool("MoveFromInventory", true)
            .setPage("Placement");
    public final Setting<Integer> stagingHotbarSlot = num("StagingHotbarSlot", 9, 1, 9)
            .setPage("Placement");

    public final Setting<Boolean> pauseWhileUsingItem = bool("PauseWhileUsingItem", true)
            .setPage("General");
    public final Setting<Integer> maximumTargets = num("MaximumTargets", 500000, 1000, 2000000)
            .setPage("General");
    public final Setting<Boolean> disableWhenFinished = bool("DisableWhenFinished", true)
            .setPage("General");

    public final Setting<Boolean> autoPathfinding = bool("AutoPathfinding", true)
            .setPage("Pathfinding");
    public final Setting<Integer> pathfindChunkRange = num("PathfindChunkRange", 8, 1, 64)
            .setPage("Pathfinding");
    public final Setting<Integer> goalRadius = num("GoalRadius", 3, 1, 5)
            .setPage("Pathfinding");

    public final Setting<Boolean> render = bool("Render", true).setPage("Render");
    public final Setting<Double> renderRadius = num("RenderRadius", 16.0, 2.0, 128.0)
            .setPage("Render");
    public final Setting<Integer> renderMaxResults = num("RenderMaxResults", 2000, 50, 10000)
            .setPage("Render");
    public final Setting<Color> fillColor = color("FillColor", 145, 79, 220, 45)
            .setPage("Render");
    public final Setting<Color> outlineColor = color("OutlineColor", 145, 79, 220, 255)
            .setPage("Render");

    private final Map<Long, LinkedHashSet<BlockPos>> targetsByChunk = new LinkedHashMap<>();
    private final Set<BlockPos> queuedByPrinter = new HashSet<>();
    private final List<BlockPos> renderQueue = new ArrayList<>();

    private IBaritone baritone;
    private Block targetBlock;
    private Item targetItem;
    private String loadedBlockId = "";
    private ResourceKey<Level> startingDimension;
    private int initialTargetCount;
    private int completedTargetCount;
    private int skippedTargetCount;
    private int inventoryMoveDelay;
    private int pathRefreshDelay;
    private int missingItemMessageDelay;
    private boolean printerStartedPathing;
    private boolean finishing;

    public PrinterModule() {
        super("Printer", "Fills Baritone selections with a chosen block.", Category.WORLD);
    }

    @Override
    public void onEnable() {
        if (nullCheck()) {
            Command.sendMessage("{red} Printer requires an active world.");
            disable();
            return;
        }

        baritone = BaritoneAPI.getProvider().getPrimaryBaritone();
        startingDimension = mc.level.dimension();
        finishing = false;
        if (!loadSelection()) disable();
    }

    @Override
    public void onDisable() {
        clearOwnedPlacements();
        stopPrinterPathing();
        renderQueue.clear();
        targetsByChunk.clear();
        targetBlock = null;
        targetItem = null;
        loadedBlockId = "";
        startingDimension = null;
        finishing = false;
    }

    @Override
    public void onTick() {
        if (nullCheck()) return;

        if (finishing) {
            if (!normalizeBlockId(blockId.getValue()).equals(loadedBlockId)) {
                finishing = false;
                if (!loadSelection()) disable();
            }
            return;
        }

        if (!mc.level.dimension().equals(startingDimension)) {
            Command.sendMessage("{red} Printer stopped because the dimension changed.");
            disable();
            return;
        }

        String currentBlockId = normalizeBlockId(blockId.getValue());
        if (!currentBlockId.equals(loadedBlockId)) {
            clearOwnedPlacements();
            stopPrinterPathing();
            if (!loadSelection()) disable();
            return;
        }

        if (inventoryMoveDelay > 0) inventoryMoveDelay--;
        if (pathRefreshDelay > 0) pathRefreshDelay--;
        if (missingItemMessageDelay > 0) missingItemMessageDelay--;

        pruneCompletedLoadedTargets();
        if (targetsByChunk.isEmpty()) {
            finishPrinting();
            return;
        }

        rebuildRenderQueue();

        if (pauseWhileUsingItem.getValue() && mc.player.isUsingItem()) return;
        if (!makeItemAvailable()) return;

        List<BlockPos> placeable = collectPlaceableTargets();
        if (!placeable.isEmpty()) {
            stopPrinterPathing();
            int sent = 0;
            for (BlockPos pos : placeable) {
                if (sent >= placementsPerTick.getValue()) break;
                if (JewDust.placementManager.enqueue(pos, targetItem)) {
                    queuedByPrinter.add(pos.immutable());
                    sent++;
                }
            }
            JewDust.placementManager.flushQueue();
            return;
        }

        updatePathfinding();
    }

    @Override
    public void onRender3D(Render3DEvent event) {
        if (!render.getValue() || nullCheck()) return;

        Color fill = fillColor.getValue();
        Color outline = outlineColor.getValue();
        for (BlockPos pos : renderQueue) {
            if (fill.getAlpha() > 0) RenderUtil.drawBoxFilled(event.getMatrix(), pos, fill);
            if (outline.getAlpha() > 0) RenderUtil.drawBox(event.getMatrix(), pos, outline, 1.0f);
        }
    }

    @Override
    public String getDisplayInfo() {
        int remaining = Math.max(0, initialTargetCount - completedTargetCount);
        return remaining + "/" + initialTargetCount;
    }

    private boolean loadSelection() {
        Block resolvedBlock = resolveBlock(blockId.getValue());
        if (resolvedBlock == null || resolvedBlock.asItem() == Items.AIR) {
            Command.sendMessage("{red} Printer block is invalid or has no placeable item: " + blockId.getValue());
            return false;
        }

        ISelection[] selections = baritone.getSelectionManager().getSelections();
        if (selections == null || selections.length == 0) {
            Command.sendMessage("{red} Printer could not find a Baritone selection.");
            return false;
        }

        long selectedVolume = 0L;
        for (ISelection selection : selections) {
            if (selection == null) continue;
            BlockPos min = selection.min();
            BlockPos max = selection.max();
            long sx = (long) max.getX() - min.getX() + 1L;
            long sy = (long) max.getY() - min.getY() + 1L;
            long sz = (long) max.getZ() - min.getZ() + 1L;
            selectedVolume = cappedAdd(selectedVolume, cappedMultiply(cappedMultiply(sx, sy), sz));
        }
        if (selectedVolume > maximumTargets.getValue()) {
            Command.sendMessage("{red} Printer selection contains " + selectedVolume
                    + " blocks; MaximumTargets is " + maximumTargets.getValue() + ".");
            return false;
        }

        clearOwnedPlacements();
        targetsByChunk.clear();
        renderQueue.clear();
        targetBlock = resolvedBlock;
        targetItem = targetBlock.asItem();
        loadedBlockId = normalizeBlockId(blockId.getValue());
        blockId.setValueNoEvent(loadedBlockId);
        initialTargetCount = 0;
        completedTargetCount = 0;
        skippedTargetCount = 0;
        inventoryMoveDelay = 0;
        pathRefreshDelay = 0;
        missingItemMessageDelay = 0;
        printerStartedPathing = false;

        for (ISelection selection : selections) addSelection(selection);

        if (initialTargetCount == 0) {
            Command.sendMessage("{red} Printer found no matching air/replaceable blocks in the selection.");
            return false;
        }

        Command.sendMessage("Printer loaded " + initialTargetCount + " targets using " + loadedBlockId + ".");
        return true;
    }

    private void addSelection(ISelection selection) {
        if (selection == null) return;

        BlockPos min = selection.min();
        BlockPos max = selection.max();
        int minBuildY = mc.level.getMinY();
        int maxBuildY = mc.level.getMaxY() - 1;

        for (int x = min.getX(); x <= max.getX(); x++) {
            for (int y = Math.max(min.getY(), minBuildY); y <= Math.min(max.getY(), maxBuildY); y++) {
                for (int z = min.getZ(); z <= max.getZ(); z++) {
                    BlockPos pos = new BlockPos(x, y, z);
                    if (mc.level.hasChunkAt(pos)) {
                        BlockState current = mc.level.getBlockState(pos);
                        if (current.is(targetBlock)) continue;
                        if (onlyAir.getValue() && !current.isAir()) continue;
                        if (!onlyAir.getValue() && !current.canBeReplaced()) continue;
                    }

                    long chunkKey = ChunkPos.asLong(x >> 4, z >> 4);
                    boolean added = targetsByChunk
                            .computeIfAbsent(chunkKey, ignored -> new LinkedHashSet<>())
                            .add(pos.immutable());
                    if (added) initialTargetCount++;
                }
            }
        }
    }

    private void pruneCompletedLoadedTargets() {
        Iterator<Map.Entry<Long, LinkedHashSet<BlockPos>>> chunks = targetsByChunk.entrySet().iterator();
        while (chunks.hasNext()) {
            Map.Entry<Long, LinkedHashSet<BlockPos>> chunkEntry = chunks.next();
            ChunkPos chunk = new ChunkPos(chunkEntry.getKey());
            if (!mc.level.hasChunk(chunk.x, chunk.z)) continue;

            Iterator<BlockPos> positions = chunkEntry.getValue().iterator();
            while (positions.hasNext()) {
                BlockPos pos = positions.next();
                BlockState current = mc.level.getBlockState(pos);
                boolean finished = current.is(targetBlock);
                boolean skipped = onlyAir.getValue() ? !current.isAir() : !current.canBeReplaced();
                if (finished || skipped) {
                    positions.remove();
                    queuedByPrinter.remove(pos);
                    completedTargetCount++;
                    if (skipped && !finished) skippedTargetCount++;
                }
            }
            if (chunkEntry.getValue().isEmpty()) chunks.remove();
        }
    }

    private List<BlockPos> collectPlaceableTargets() {
        List<BlockPos> placeable = new ArrayList<>();
        BlockPos playerPos = mc.player.blockPosition();
        ChunkPos playerChunk = new ChunkPos(playerPos);
        int chunkRange = Math.min(2, pathfindChunkRange.getValue());

        for (int cx = playerChunk.x - chunkRange; cx <= playerChunk.x + chunkRange; cx++) {
            for (int cz = playerChunk.z - chunkRange; cz <= playerChunk.z + chunkRange; cz++) {
                Set<BlockPos> positions = targetsByChunk.get(ChunkPos.asLong(cx, cz));
                if (positions == null) continue;
                for (BlockPos pos : positions) {
                    if (!passesLayer(pos)) continue;
                    if (!canPlaceTarget(pos)) continue;
                    placeable.add(pos);
                }
            }
        }

        Vec3 eye = mc.player.getEyePosition();
        placeable.sort(Comparator.comparingDouble(pos -> eye.distanceToSqr(Vec3.atCenterOf(pos))));
        return placeable;
    }

    private boolean canPlaceTarget(BlockPos pos) {
        if (!mc.level.hasChunkAt(pos)) return false;
        BlockState current = mc.level.getBlockState(pos);
        if (current.is(targetBlock)) return false;
        if (onlyAir.getValue() ? !current.isAir() : !current.canBeReplaced()) return false;
        if (!safeForBlock(pos)) return false;
        return PlaceUtil.canPlace(pos);
    }

    private boolean safeForBlock(BlockPos pos) {
        if (!gravityCheck.getValue() || !(targetBlock instanceof FallingBlock)) return true;
        BlockState below = mc.level.getBlockState(pos.below());
        return !below.isAir() && !below.canBeReplaced();
    }

    private boolean passesLayer(BlockPos pos) {
        return layerType.getValue() != LayerType.BelowFeet || pos.getY() < Math.floor(mc.player.getY());
    }

    private boolean makeItemAvailable() {
        Result result = InventoryUtil.find(targetItem, InventoryUtil.FULL_SCOPE);
        if (!result.found()) {
            stopPrinterPathing();
            if (missingItemMessageDelay == 0) {
                Command.sendMessage("{red} Printer is waiting for " + loadedBlockId + " in your inventory.");
                missingItemMessageDelay = 100;
            }
            return false;
        }

        if (result.type() == ResultType.INVENTORY) {
            if (!moveFromInventory.getValue() || inventoryMoveDelay > 0 || mc.screen != null) return false;
            InventoryUtil.swapToHotbarSlot(result.slot(), stagingHotbarSlot.getValue() - 1);
            inventoryMoveDelay = 2;
            return false;
        }

        if (result.type() == ResultType.OFFHAND) {
            stopPrinterPathing();
            if (missingItemMessageDelay == 0) {
                Command.sendMessage("{red} Printer needs at least one " + loadedBlockId
                        + " stack in the hotbar or main inventory, not only the offhand.");
                missingItemMessageDelay = 100;
            }
            return false;
        }

        return true;
    }

    private void rebuildRenderQueue() {
        renderQueue.clear();
        if (!render.getValue()) return;

        Vec3 eye = mc.player.getEyePosition();
        double maxDistanceSq = renderRadius.getValue() * renderRadius.getValue();
        int max = renderMaxResults.getValue();
        ChunkPos playerChunk = mc.player.chunkPosition();
        int chunkRange = (int) Math.ceil(renderRadius.getValue() / 16.0) + 1;

        for (int cx = playerChunk.x - chunkRange; cx <= playerChunk.x + chunkRange; cx++) {
            for (int cz = playerChunk.z - chunkRange; cz <= playerChunk.z + chunkRange; cz++) {
                Set<BlockPos> positions = targetsByChunk.get(ChunkPos.asLong(cx, cz));
                if (positions == null) continue;
                for (BlockPos pos : positions) {
                    if (renderQueue.size() >= max) return;
                    if (!passesLayer(pos) || !mc.level.hasChunkAt(pos)) continue;
                    BlockState current = mc.level.getBlockState(pos);
                    if (current.is(targetBlock)) continue;
                    if (onlyAir.getValue() ? !current.isAir() : !current.canBeReplaced()) continue;
                    if (eye.distanceToSqr(Vec3.atCenterOf(pos)) <= maxDistanceSq) renderQueue.add(pos);
                }
            }
        }
    }

    private void updatePathfinding() {
        if (!autoPathfinding.getValue()) {
            stopPrinterPathing();
            return;
        }
        if (pathRefreshDelay > 0) return;

        BlockPos goal = closestPathTarget();
        if (goal == null) {
            stopPrinterPathing();
            pathRefreshDelay = 20;
            return;
        }

        baritone.getCustomGoalProcess().setGoalAndPath(new GoalNear(goal, goalRadius.getValue()));
        printerStartedPathing = true;
        pathRefreshDelay = 20;
    }

    private BlockPos closestPathTarget() {
        ChunkPos playerChunk = mc.player.chunkPosition();
        int chunkRange = pathfindChunkRange.getValue();
        Vec3 player = mc.player.position();
        BlockPos closest = null;
        double closestDistanceSq = Double.MAX_VALUE;

        for (Map.Entry<Long, LinkedHashSet<BlockPos>> entry : targetsByChunk.entrySet()) {
            ChunkPos chunk = new ChunkPos(entry.getKey());
            if (Math.abs(chunk.x - playerChunk.x) > chunkRange
                    || Math.abs(chunk.z - playerChunk.z) > chunkRange) continue;

            for (BlockPos pos : entry.getValue()) {
                if (!passesLayer(pos)) continue;
                double distanceSq = player.distanceToSqr(Vec3.atCenterOf(pos));
                if (distanceSq < closestDistanceSq) {
                    closestDistanceSq = distanceSq;
                    closest = pos;
                }
            }
        }
        return closest;
    }

    private void stopPrinterPathing() {
        if (baritone != null && printerStartedPathing) {
            baritone.getPathingBehavior().cancelEverything();
        }
        printerStartedPathing = false;
    }

    private void finishPrinting() {
        if (finishing) return;
        finishing = true;
        clearOwnedPlacements();
        stopPrinterPathing();
        renderQueue.clear();
        int placed = Math.max(0, completedTargetCount - skippedTargetCount);
        String skipped = skippedTargetCount == 0 ? "" : ", skipped " + skippedTargetCount;
        Command.sendMessage("Printer finished " + loadedBlockId + " (placed " + placed + skipped + ").");
        if (disableWhenFinished.getValue()) disable();
    }

    private void clearOwnedPlacements() {
        if (!queuedByPrinter.isEmpty()) {
            JewDust.placementManager.removeQueuedFor(queuedByPrinter::contains);
            queuedByPrinter.clear();
        }
    }

    private Block resolveBlock(String rawId) {
        Identifier id = Identifier.tryParse(normalizeBlockId(rawId));
        if (id == null || !BuiltInRegistries.BLOCK.containsKey(id)) return null;
        return BuiltInRegistries.BLOCK.getValue(id);
    }

    private static String normalizeBlockId(String rawId) {
        if (rawId == null) return "";
        String id = rawId.trim().toLowerCase(Locale.ROOT);
        return id.isEmpty() || id.contains(":") ? id : "minecraft:" + id;
    }

    private static long cappedMultiply(long a, long b) {
        if (a <= 0L || b <= 0L) return 0L;
        if (a > Long.MAX_VALUE / b) return Long.MAX_VALUE;
        return a * b;
    }

    private static long cappedAdd(long a, long b) {
        if (Long.MAX_VALUE - a < b) return Long.MAX_VALUE;
        return a + b;
    }

    public enum LayerType {
        All,
        BelowFeet
    }
}
