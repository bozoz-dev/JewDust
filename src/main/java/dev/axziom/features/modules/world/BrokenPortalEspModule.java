package dev.axziom.features.modules.world;

import dev.axziom.event.impl.render.Render2DEvent;
import dev.axziom.event.impl.render.Render3DEvent;
import dev.axziom.features.commands.Command;
import dev.axziom.features.modules.Module;
import dev.axziom.features.settings.Setting;
import dev.axziom.util.render.MatrixCapture;
import dev.axziom.util.render.PortRender;
import dev.axziom.util.render.RenderUtil;
import dev.axziom.util.world.PortalTraceScanner;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.chunk.LevelChunk;

import java.awt.Color;
import java.util.ArrayDeque;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Set;

public final class BrokenPortalEspModule extends Module {
    private static final int STRUCTURE_MARGIN = 15;

    public final Setting<Boolean> caveAirDetection = bool("CaveAirDetection", true);
    public final Setting<Boolean> structureDetection = bool("StructureDetection", true);
    public final Setting<Boolean> chatFeedback = bool("ChatFeedback", true);
    public final Setting<Boolean> detectionSound = bool("DetectionSound", true);
    public final Setting<Boolean> dungeons = bool("Dungeons", true).setPage("Structures");
    public final Setting<Boolean> mineshafts = bool("Mineshafts", true).setPage("Structures");
    public final Setting<Boolean> trialChambers = bool("TrialChambers", true).setPage("Structures");
    public final Setting<Integer> minY = num("MinY", -60, -64, 320).setPage("Detection");
    public final Setting<Integer> maxY = num("MaxY", 180, -64, 320).setPage("Detection");
    public final Setting<Integer> scanRadius = num("ScanRadius", 4, 1, 10).setPage("Detection");
    public final Setting<Integer> chunksPerTick = num("ChunksPerTick", 1, 1, 8).setPage("Detection");
    public final Setting<Integer> rescanDelay = num("RescanDelay", 200, 20, 1200).setPage("Detection");
    public final Setting<Double> renderDistance = num("RenderDistance", 256.0, 16.0, 1024.0).setPage("Render");
    public final Setting<Double> beaconHeight = num("BeaconHeight", 128.0, 16.0, 384.0).setPage("Render");
    public final Setting<Boolean> tracers = bool("Tracers", true).setPage("Render");
    public final Setting<Boolean> beacon = bool("Beacon", true).setPage("Render");
    public final Setting<PortRender.ShapeMode> shapeMode = mode("ShapeMode", PortRender.ShapeMode.BOTH).setPage("Render");
    public final Setting<Color> sideColor = color("SideColour", 145, 79, 220, 255).setPage("Render");
    public final Setting<Color> lineColor = color("LineColour", 145, 79, 220, 255).setPage("Render");

    private final ArrayDeque<ChunkPos> pending = new ArrayDeque<>();
    private final Set<Long> queued = new HashSet<>();
    private final Map<Long, Integer> lastScan = new HashMap<>();
    private final Map<Long, Detection> detections = new LinkedHashMap<>();
    private Object trackedLevel;
    private int tick;

    public BrokenPortalEspModule() {
        super("BrokenPortalESP", "Finds portal-shaped air traces in the Nether near cave air or supported structures.", Category.WORLD);
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
        if (nullCheck()) return;
        if (trackedLevel != mc.level) clear();
        trackedLevel = mc.level;
        if (!Level.NETHER.equals(mc.level.dimension())) {
            clear();
            return;
        }

        tick++;
        queueAround();
        int budget = chunksPerTick.getValue();
        while (budget-- > 0) {
            ChunkPos pos = pending.poll();
            if (pos == null) break;
            queued.remove(pos.toLong());
            if (!mc.level.hasChunk(pos.x, pos.z)) continue;
            scan(mc.level.getChunk(pos.x, pos.z));
            lastScan.put(pos.toLong(), tick);
        }
        prune();
    }

    private void queueAround() {
        if (tick % 20 != 0) return;
        int radius = scanRadius.getValue();
        ChunkPos center = mc.player.chunkPosition();
        for (int dx = -radius; dx <= radius; dx++) {
            for (int dz = -radius; dz <= radius; dz++) {
                if (dx * dx + dz * dz > radius * radius) continue;
                int x = center.x + dx;
                int z = center.z + dz;
                if (mc.level.hasChunk(x, z)) queue(new ChunkPos(x, z));
            }
        }
    }

    private void queue(ChunkPos pos) {
        long key = pos.toLong();
        if (tick - lastScan.getOrDefault(key, Integer.MIN_VALUE) < rescanDelay.getValue()) return;
        if (queued.add(key)) pending.add(pos);
    }

    private void scan(LevelChunk chunk) {
        for (PortalTraceScanner.Trace trace : PortalTraceScanner.scan(mc.level, chunk, minY.getValue(),
                maxY.getValue(), 4, 2, 8)) {
            StructureType structure = findStructure(trace);
            if (!caveAirDetection.getValue() && !(structureDetection.getValue() && structure != StructureType.NONE)) continue;

            long key = (trace.origin().asLong() << 1) | (trace.xAligned() ? 1L : 0L);
            Detection previous = detections.put(key, new Detection(trace, structure));
            if (previous != null) continue;
            if (chatFeedback.getValue()) {
                Command.sendMessage("Broken portal trace at %d, %d, %d%s", trace.origin().getX(), trace.origin().getY(),
                        trace.origin().getZ(), structure == StructureType.NONE ? "" : " near " + structure.label);
            }
            if (detectionSound.getValue()) mc.player.playSound(SoundEvents.BEACON_ACTIVATE, 1.0f, 0.8f);
        }
    }

    private StructureType findStructure(PortalTraceScanner.Trace trace) {
        BlockPos center = trace.origin();
        int dungeon = 0;
        int mineshaft = 0;
        int trial = 0;
        for (int x = -STRUCTURE_MARGIN; x <= STRUCTURE_MARGIN; x++) {
            for (int y = -STRUCTURE_MARGIN; y <= STRUCTURE_MARGIN; y++) {
                for (int z = -STRUCTURE_MARGIN; z <= STRUCTURE_MARGIN; z++) {
                    BlockPos pos = center.offset(x, y, z);
                    if (!mc.level.hasChunk(pos.getX() >> 4, pos.getZ() >> 4)) continue;
                    Block block = mc.level.getBlockState(pos).getBlock();
                    String id = BuiltInRegistries.BLOCK.getKey(block).getPath();
                    if (dungeons.getValue() && (id.equals("spawner") || id.equals("mossy_cobblestone") || id.equals("cobblestone"))) dungeon++;
                    if (mineshafts.getValue() && (id.contains("rail") || id.endsWith("_fence") || id.endsWith("_planks") || id.equals("cobweb"))) mineshaft++;
                    if (trialChambers.getValue() && (id.contains("tuff") || id.contains("copper") || id.contains("trial_spawner") || id.equals("vault"))) trial++;
                    if (trial >= 8) return StructureType.TRIAL_CHAMBER;
                    if (mineshaft >= 8) return StructureType.MINESHAFT;
                    if (dungeon >= 8) return StructureType.DUNGEON;
                }
            }
        }
        return StructureType.NONE;
    }

    private void prune() {
        double max = renderDistance.getValue() + 64.0;
        double maxSq = max * max;
        detections.entrySet().removeIf(entry -> mc.player.blockPosition().distSqr(entry.getValue().trace.origin()) > maxSq);
    }

    @Override
    public void onRender3D(Render3DEvent event) {
        if (nullCheck() || !Level.NETHER.equals(mc.level.dimension())) return;
        double maxSq = renderDistance.getValue() * renderDistance.getValue();
        for (Detection detection : detections.values()) {
            PortalTraceScanner.Trace trace = detection.trace;
            if (mc.player.blockPosition().distSqr(trace.origin()) > maxSq) continue;
            Color line = colourFor(detection.structure, lineColor.getValue());
            Color side = colourFor(detection.structure, sideColor.getValue());
            PortRender.box(event.getMatrix(), trace.box(), side, line, shapeMode.getValue());
            if (beacon.getValue()) PortRender.beacon(trace.origin(), beaconHeight.getValue(), line);
        }
    }

    @Override
    public void onRender2D(Render2DEvent event) {
        if (nullCheck() || !tracers.getValue() || !Level.NETHER.equals(mc.level.dimension())) return;
        double maxSq = renderDistance.getValue() * renderDistance.getValue();
        float centerX = mc.getWindow().getGuiScaledWidth() * 0.5f;
        float centerY = mc.getWindow().getGuiScaledHeight() * 0.5f;
        for (Detection detection : detections.values()) {
            PortalTraceScanner.Trace trace = detection.trace;
            if (mc.player.blockPosition().distSqr(trace.origin()) > maxSq) continue;
            float[] screen = MatrixCapture.worldToScreenClamped(
                    trace.box().getCenter().x, trace.box().getCenter().y, trace.box().getCenter().z);
            if (screen != null) {
                RenderUtil.line2D(event.getContext(), centerX, centerY, screen[0], screen[1],
                        colourFor(detection.structure, lineColor.getValue()), 1.5f);
            }
        }
    }

    private static Color colourFor(StructureType structure, Color fallback) {
        Color rgb = switch (structure) {
            case DUNGEON -> new Color(145, 79, 220, 255);
            case MINESHAFT -> new Color(145, 79, 220, 255);
            case TRIAL_CHAMBER -> new Color(145, 79, 220, 255);
            case NONE -> fallback;
        };
        return new Color(rgb.getRed(), rgb.getGreen(), rgb.getBlue(), fallback.getAlpha());
    }

    @Override
    public String getDisplayInfo() {
        return Integer.toString(detections.size());
    }

    private void clear() {
        pending.clear();
        queued.clear();
        lastScan.clear();
        detections.clear();
        trackedLevel = null;
        tick = 0;
    }

    private enum StructureType {
        NONE("nothing"), DUNGEON("a dungeon"), MINESHAFT("a mineshaft"), TRIAL_CHAMBER("a trial chamber");

        private final String label;

        StructureType(String label) {
            this.label = label;
        }
    }

    private record Detection(PortalTraceScanner.Trace trace, StructureType structure) {
    }
}
