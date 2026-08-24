package dev.axziom.features.modules.world;

import dev.axziom.event.impl.render.Render3DEvent;
import dev.axziom.features.commands.Command;
import dev.axziom.features.modules.Module;
import dev.axziom.features.settings.Setting;
import dev.axziom.util.render.PortRender;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.Identifier;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.level.chunk.LevelChunk;
import net.minecraft.world.level.chunk.LevelChunkSection;

import java.awt.Color;
import java.util.EnumMap;
import java.util.HashSet;
import java.util.Set;

public final class WeirdBlockEspModule extends Module {
    public final Setting<Double> maxDistance = num("MaxDistance", 64.0, 1.0, 128.0);
    public final Setting<Integer> updateInterval = num("UpdateInterval", 40, 5, 200);
    public final Setting<Boolean> performanceMode = bool("PerformanceMode", true);
    public final Setting<Integer> bedrockMaxY = num("BedrockMaxY", 5, -64, 320).setPage("Finds");
    public final Setting<String> customBlocks = str("CustomBlocks", "").setPage("Custom");
    public final Setting<Boolean> renderEsp = bool("RenderESP", true).setPage("Render");
    public final Setting<PortRender.ShapeMode> shapeMode = mode("ShapeMode", PortRender.ShapeMode.BOTH).setPage("Render");
    public final Setting<Boolean> chatOutput = bool("ChatOutput", false).setPage("Chat");
    public final Setting<Boolean> chatCoords = bool("ChatCoordinates", true).setPage("Chat");

    private final EnumMap<WeirdType, Setting<Boolean>> show = new EnumMap<>(WeirdType.class);
    private final EnumMap<WeirdType, Setting<Color>> colours = new EnumMap<>(WeirdType.class);
    private final EnumMap<WeirdType, Set<BlockPos>> found = new EnumMap<>(WeirdType.class);
    private final EnumMap<WeirdType, Set<BlockPos>> announced = new EnumMap<>(WeirdType.class);
    private int tickCounter;
    private Object trackedLevel;

    public WeirdBlockEspModule() {
        super("MisplaceESP", "Highlights blocks in orientations or positions that do not occur naturally.", Category.WORLD);
        for (WeirdType type : WeirdType.values()) {
            show.put(type, bool(type.settingName(), type.defaultEnabled).setPage("Finds"));
            colours.put(type, color(type.settingName() + "Colour", type.defaultColour).setPage("Render"));
            found.put(type, new HashSet<>());
            announced.put(type, new HashSet<>());
        }
    }

    @Override
    public void onEnable() {
        clearAll();
        tickCounter = 0;
    }

    @Override
    public void onDisable() {
        clearAll();
    }

    @Override
    public void onTick() {
        if (nullCheck()) return;
        if (trackedLevel != mc.level) {
            clearAll();
            trackedLevel = mc.level;
        }
        if (++tickCounter < updateInterval.getValue()) return;
        tickCounter = 0;
        detect();
    }

    private void detect() {
        found.values().forEach(Set::clear);
        BlockPos playerPos = mc.player.blockPosition();
        double maxSq = maxDistance.getValue() * maxDistance.getValue();
        int wanted = (int) Math.ceil(maxDistance.getValue() / 16.0);
        int radius = Math.min(wanted, performanceMode.getValue() ? 3 : 5);
        int centerX = playerPos.getX() >> 4;
        int centerZ = playerPos.getZ() >> 4;
        Set<Block> custom = resolveCustomBlocks();

        for (int cx = centerX - radius; cx <= centerX + radius; cx++) {
            for (int cz = centerZ - radius; cz <= centerZ + radius; cz++) {
                if (!mc.level.hasChunk(cx, cz)) continue;
                scanChunk(mc.level.getChunk(cx, cz), playerPos, maxSq, custom);
            }
        }
    }

    private void scanChunk(LevelChunk chunk, BlockPos playerPos, double maxSq, Set<Block> custom) {
        LevelChunkSection[] sections = chunk.getSections();
        for (int sectionIndex = 0; sectionIndex < sections.length; sectionIndex++) {
            LevelChunkSection section = sections[sectionIndex];
            if (section == null || section.hasOnlyAir()) continue;
            int baseY = chunk.getSectionYFromSectionIndex(sectionIndex) << 4;
            int baseX = chunk.getPos().x << 4;
            int baseZ = chunk.getPos().z << 4;

            for (int x = 0; x < 16; x++) {
                for (int y = 0; y < 16; y++) {
                    for (int z = 0; z < 16; z++) {
                        BlockState state = section.getBlockState(x, y, z);
                        if (state.isAir()) continue;
                        BlockPos pos = new BlockPos(baseX + x, baseY + y, baseZ + z);
                        if (playerPos.distSqr(pos) > maxSq) continue;
                        WeirdType type = classify(state, pos, custom);
                        if (type == null || !show.get(type).getValue()) continue;
                        found.get(type).add(pos);
                        if (chatOutput.getValue() && announced.get(type).add(pos)) {
                            if (chatCoords.getValue()) {
                                Command.sendMessage("Weird %s at %d, %d, %d", type.label, pos.getX(), pos.getY(), pos.getZ());
                            } else {
                                Command.sendMessage("Weird %s found", type.label);
                            }
                        }
                    }
                }
            }
        }
    }

    private WeirdType classify(BlockState state, BlockPos pos, Set<Block> custom) {
        Block block = state.getBlock();
        if (block == Blocks.DEEPSLATE && unnaturalAxis(state)) return WeirdType.DEEPSLATE;
        if (block == Blocks.INFESTED_DEEPSLATE && unnaturalAxis(state)) return WeirdType.INFESTED_DEEPSLATE;
        if (block == Blocks.BASALT && unnaturalAxis(state)) return WeirdType.BASALT;
        if (block == Blocks.POLISHED_BASALT && unnaturalAxis(state)) return WeirdType.POLISHED_BASALT;
        if (block == Blocks.BONE_BLOCK && unnaturalAxis(state)) return WeirdType.BONE_BLOCK;
        if (block == Blocks.HAY_BLOCK && unnaturalAxis(state)) return WeirdType.HAY;
        if (block == Blocks.QUARTZ_PILLAR && unnaturalAxis(state)) return WeirdType.QUARTZ_PILLAR;
        if (block == Blocks.PURPUR_PILLAR && unnaturalAxis(state)) return WeirdType.PURPUR_PILLAR;
        if (block == Blocks.BEDROCK && weirdBedrock(pos)) return WeirdType.BEDROCK;
        if (custom.contains(block) && unnaturalOrientation(state)) return WeirdType.CUSTOM;
        return null;
    }

    private boolean weirdBedrock(BlockPos pos) {
        ResourceKey<Level> dimension = mc.level.dimension();
        int y = pos.getY();
        if (Level.OVERWORLD.equals(dimension)) return y > bedrockMaxY.getValue();
        if (Level.NETHER.equals(dimension)) return y > bedrockMaxY.getValue() && y < 123;
        if (Level.END.equals(dimension)) return y < 50 || y > 70;
        return false;
    }

    private Set<Block> resolveCustomBlocks() {
        Set<Block> blocks = new HashSet<>();
        for (String raw : customBlocks.getValue().split(",")) {
            Identifier id = Identifier.tryParse(raw.trim());
            if (id != null && BuiltInRegistries.BLOCK.containsKey(id)) blocks.add(BuiltInRegistries.BLOCK.getValue(id));
        }
        return blocks;
    }

    private static boolean unnaturalAxis(BlockState state) {
        return state.hasProperty(BlockStateProperties.AXIS)
                && state.getValue(BlockStateProperties.AXIS) != Direction.Axis.Y;
    }

    private static boolean unnaturalOrientation(BlockState state) {
        if (unnaturalAxis(state)) return true;
        BlockState defaultState = state.getBlock().defaultBlockState();
        if (state.hasProperty(BlockStateProperties.FACING) && defaultState.hasProperty(BlockStateProperties.FACING)) {
            return state.getValue(BlockStateProperties.FACING) != defaultState.getValue(BlockStateProperties.FACING);
        }
        return state.hasProperty(BlockStateProperties.HORIZONTAL_FACING)
                && defaultState.hasProperty(BlockStateProperties.HORIZONTAL_FACING)
                && state.getValue(BlockStateProperties.HORIZONTAL_FACING)
                != defaultState.getValue(BlockStateProperties.HORIZONTAL_FACING);
    }

    @Override
    public void onRender3D(Render3DEvent event) {
        if (!renderEsp.getValue() || nullCheck()) return;
        double maxSq = maxDistance.getValue() * maxDistance.getValue();
        for (WeirdType type : WeirdType.values()) {
            if (!show.get(type).getValue()) continue;
            Color line = colours.get(type).getValue();
            Color side = new Color(line.getRed(), line.getGreen(), line.getBlue(), Math.min(90, line.getAlpha()));
            for (BlockPos pos : found.get(type)) {
                if (mc.player.blockPosition().distSqr(pos) <= maxSq) {
                    PortRender.box(event.getMatrix(), pos, side, line, shapeMode.getValue());
                }
            }
        }
    }

    @Override
    public String getDisplayInfo() {
        return Integer.toString(found.values().stream().mapToInt(Set::size).sum());
    }

    private void clearAll() {
        found.values().forEach(Set::clear);
        announced.values().forEach(Set::clear);
        trackedLevel = null;
    }

    private enum WeirdType {
        DEEPSLATE("Deepslate", true, new Color(145, 79, 220, 255)),
        INFESTED_DEEPSLATE("Infested Deepslate", true, new Color(145, 79, 220, 255)),
        BASALT("Basalt", true, new Color(145, 79, 220, 255)),
        POLISHED_BASALT("Polished Basalt", true, new Color(145, 79, 220, 255)),
        BONE_BLOCK("Bone Block", false, new Color(145, 79, 220, 255)),
        HAY("Hay", false, new Color(145, 79, 220, 255)),
        QUARTZ_PILLAR("Quartz Pillar", false, new Color(145, 79, 220, 255)),
        PURPUR_PILLAR("Purpur Pillar", false, new Color(145, 79, 220, 255)),
        BEDROCK("Bedrock", true, new Color(145, 79, 220, 255)),
        CUSTOM("Custom", true, new Color(145, 79, 220, 255));

        private final String label;
        private final boolean defaultEnabled;
        private final Color defaultColour;

        WeirdType(String label, boolean defaultEnabled, Color defaultColour) {
            this.label = label;
            this.defaultEnabled = defaultEnabled;
            this.defaultColour = defaultColour;
        }

        private String settingName() {
            return label.replace(" ", "");
        }
    }
}
