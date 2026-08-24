package dev.axziom.features.modules.render;

import dev.axziom.JewDust;
import dev.axziom.event.impl.render.Render2DEvent;
import dev.axziom.features.modules.client.TargetsModule;
import dev.axziom.features.modules.Module;
import dev.axziom.features.settings.Setting;
import dev.axziom.util.render.MatrixCapture;
import dev.axziom.util.render.RenderUtil;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;

import java.awt.Color;

public final class TracersModule extends Module {
    public enum ColourMode {
        SINGLE,
        DISTANCE,
        RAINBOW
    }

    public final Setting<Boolean> showFriends = bool("ShowFriends", false);
    public final Setting<Double> maxDistance = num("MaxDistance", 512.0, 16.0, 2048.0);
    public final Setting<Float> lineWidth = num("LineWidth", 2.0f, 0.5f, 5.0f);
    public final Setting<ColourMode> colourMode = mode("ColourMode", ColourMode.SINGLE);
    public final Setting<Color> singleColour = color("Colour", 145, 79, 220, 255)
            .setVisibility(value -> colourMode.getValue() == ColourMode.SINGLE);

    private int renderedCount;

    public TracersModule() {
        super("Tracers", "Draws lines from the crosshair to entities selected by Targets.", Category.RENDER);
    }

    @Override
    public void onDisable() {
        renderedCount = 0;
    }

    @Override
    public void onRender2D(Render2DEvent event) {
        if (nullCheck()) {
            renderedCount = 0;
            return;
        }

        renderedCount = 0;
        TargetsModule targets = JewDust.moduleManager.getModuleByClass(TargetsModule.class);
        if (targets == null) return;

        float partial = event.getDelta();
        double maxSq = maxDistance.getValue() * maxDistance.getValue();
        float centerX = mc.getWindow().getGuiScaledWidth() * 0.5f;
        float centerY = mc.getWindow().getGuiScaledHeight() * 0.5f;

        for (Entity entity : mc.level.entitiesForRendering()) {
            if (!shouldTrace(entity, targets, maxSq)) continue;

            double x = Mth.lerp(partial, entity.xOld, entity.getX());
            double y = Mth.lerp(partial, entity.yOld, entity.getY()) + entity.getBbHeight() * 0.5;
            double z = Mth.lerp(partial, entity.zOld, entity.getZ());
            float[] target = MatrixCapture.worldToScreenClamped(x, y, z);
            if (target == null) continue;

            RenderUtil.line2D(event.getContext(), centerX, centerY, target[0], target[1],
                    entityColour(entity), lineWidth.getValue());
            renderedCount++;
        }
    }

    private boolean shouldTrace(Entity entity, TargetsModule targets, double maxSq) {
        if (!(entity instanceof LivingEntity living)
                || entity == mc.player
                || living.isDeadOrDying()
                || mc.player.distanceToSqr(entity) > maxSq) {
            return false;
        }

        if (entity instanceof Player player && JewDust.friendManager.isFriend(player)) {
            return targets.players.getValue() && showFriends.getValue();
        }

        return targets.isValidTarget(entity);
    }

    private Color entityColour(Entity entity) {
        return switch (colourMode.getValue()) {
            case SINGLE -> singleColour.getValue();
            case DISTANCE -> {
                double distance = mc.player.distanceTo(entity);
                float percentage = (float) Mth.clamp(distance / maxDistance.getValue(), 0.0, 1.0);
                yield new Color(Color.HSBtoRGB((1.0f - percentage) / 3.0f, 1.0f, 1.0f));
            }
            case RAINBOW -> {
                float offset = Math.floorMod(entity.getId() * 37, 360) / 360.0f;
                float time = System.currentTimeMillis() % 5000L / 5000.0f;
                yield new Color(Color.HSBtoRGB((time + offset) % 1.0f, 1.0f, 1.0f));
            }
        };
    }

    @Override
    public String getDisplayInfo() {
        return Integer.toString(renderedCount);
    }
}
