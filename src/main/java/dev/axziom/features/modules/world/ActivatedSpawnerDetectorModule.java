package dev.axziom.features.modules.world;

import dev.axziom.event.impl.render.Render2DEvent;
import dev.axziom.event.impl.render.Render3DEvent;
import dev.axziom.features.modules.Module;
import dev.axziom.features.settings.Setting;
import dev.axziom.mixin.world.BaseSpawnerAccessor;
import dev.axziom.util.render.MatrixCapture;
import dev.axziom.util.render.PortRender;
import dev.axziom.util.render.RenderUtil;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.SpawnerBlockEntity;
import net.minecraft.world.level.chunk.LevelChunk;
import net.minecraft.world.phys.Vec3;

import java.awt.Color;
import java.util.HashSet;
import java.util.Set;

public final class ActivatedSpawnerDetectorModule extends Module {
    private static final int UNTOUCHED_DELAY = 20;

    public final Setting<Integer> scanRadius = num("ScanRadius", 16, 2, 32);
    public final Setting<Integer> scanInterval = num("ScanInterval", 10, 1, 40);
    public final Setting<Boolean> tracers = bool("Tracers", true).setPage("Render");
    public final Setting<Boolean> shader = bool("Shader", true).setPage("Render");
    public final Setting<Boolean> outline = bool("Outline", true).setPage("Render");
    public final Setting<Color> tracerColour = color("TracerColour", 145, 79, 220, 255).setPage("Render");
    public final Setting<Color> shaderColour = color("ShaderColour", 145, 79, 220, 255).setPage("Render");

    private final Set<BlockPos> detected = new HashSet<>();
    private Object trackedLevel;
    private int ticksUntilScan;

    public ActivatedSpawnerDetectorModule() {
        super("ActivatedSpawnerDetector", "Finds loaded mob spawners whose spawn delay changed from its untouched value.", Category.WORLD);
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
        if (ticksUntilScan-- > 0) return;
        ticksUntilScan = scanInterval.getValue() - 1;
        scan();
    }

    private void scan() {
        int radius = scanRadius.getValue();
        ChunkPos center = mc.player.chunkPosition();
        Set<BlockPos> found = new HashSet<>();
        for (int x = center.x - radius; x <= center.x + radius; x++) {
            for (int z = center.z - radius; z <= center.z + radius; z++) {
                LevelChunk chunk = mc.level.getChunkSource().getChunk(x, z, false);
                if (chunk == null) continue;
                for (BlockEntity blockEntity : chunk.getBlockEntities().values()) {
                    if (!(blockEntity instanceof SpawnerBlockEntity spawner)) continue;
                    int delay = ((BaseSpawnerAccessor) (Object) spawner.getSpawner()).jewdust$getSpawnDelay();
                    if (delay != UNTOUCHED_DELAY) found.add(spawner.getBlockPos().immutable());
                }
            }
        }
        detected.clear();
        detected.addAll(found);
    }

    @Override
    public void onRender3D(Render3DEvent event) {
        if (nullCheck()) return;
        PortRender.ShapeMode mode = shader.getValue() && outline.getValue()
                ? PortRender.ShapeMode.BOTH
                : shader.getValue() ? PortRender.ShapeMode.SIDES : PortRender.ShapeMode.LINES;
        for (BlockPos pos : detected) {
            if (shader.getValue() || outline.getValue()) {
                PortRender.box(event.getMatrix(), pos, shaderColour.getValue(), tracerColour.getValue(), mode);
            }
        }
    }

    @Override
    public void onRender2D(Render2DEvent event) {
        if (nullCheck() || !tracers.getValue()) return;
        float centerX = mc.getWindow().getGuiScaledWidth() * 0.5f;
        float centerY = mc.getWindow().getGuiScaledHeight() * 0.5f;
        for (BlockPos pos : detected) {
            Vec3 target = Vec3.atCenterOf(pos);
            float[] screen = MatrixCapture.worldToScreenClamped(target.x, target.y, target.z);
            if (screen != null) {
                RenderUtil.line2D(event.getContext(), centerX, centerY, screen[0], screen[1],
                        tracerColour.getValue(), 1.5f);
            }
        }
    }

    @Override
    public String getDisplayInfo() {
        return Integer.toString(detected.size());
    }

    private void clear() {
        detected.clear();
        trackedLevel = null;
        ticksUntilScan = 0;
    }
}
