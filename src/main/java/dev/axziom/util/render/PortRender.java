package dev.axziom.util.render;

import com.mojang.blaze3d.vertex.PoseStack;
import net.minecraft.core.BlockPos;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

import java.awt.Color;

/** Shared renderer used by the Base Hunting ports. */
public final class PortRender {
    private PortRender() {
    }

    public static void box(PoseStack matrices, BlockPos pos, Color sides, Color lines, ShapeMode mode) {
        box(matrices, new AABB(pos), sides, lines, mode);
    }

    public static void box(PoseStack matrices, AABB box, Color sides, Color lines, ShapeMode mode) {
        if (mode != ShapeMode.LINES && sides.getAlpha() > 0) {
            RenderUtil.drawBoxFilled(matrices, box, sides);
        }
        if (mode != ShapeMode.SIDES && lines.getAlpha() > 0) {
            RenderUtil.drawBox(matrices, box, lines, 1.5f);
        }
    }

    public static void tracer(Vec3 from, Vec3 to, Color color) {
        RenderUtil.drawLine(from, to, color, 1.5f);
    }

    public static void beacon(BlockPos pos, double height, Color color) {
        Vec3 bottom = Vec3.atCenterOf(pos);
        RenderUtil.drawLine(bottom, bottom.add(0.0, height, 0.0), color, 2.0f);
    }

    public enum ShapeMode {
        LINES,
        SIDES,
        BOTH
    }
}
