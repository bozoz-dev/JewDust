package dev.axziom.features.modules.hud;

import dev.axziom.JewDust;
import dev.axziom.event.impl.render.Render2DEvent;
import dev.axziom.features.commands.Command;
import dev.axziom.features.modules.client.HudClientModule;
import dev.axziom.features.modules.client.HudModule;
import dev.axziom.features.modules.client.HudPosition;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.Vec3;

public class CoordinatesHudModule extends HudModule {
    private static final int WHITE = 0xFFFFFFFF;
    private static final int GRAY = 0xFFAAAAAA;
    private static final double TELEPORT_DISTANCE_SQUARED = 100.0;

    private Vec3 lastPosition;

    public void onPlayerTick() {
        if (mc.player == null || mc.level == null) {
            resetTracking();
            return;
        }

        Vec3 currentPosition = mc.player.position();

        if (mc.player.isDeadOrDying()) {
            disableCoordinates("death");
            return;
        }

        if (lastPosition != null
                && currentPosition.distanceToSqr(lastPosition) > TELEPORT_DISTANCE_SQUARED) {
            disableCoordinates("teleport");
            return;
        }

        lastPosition = currentPosition;
    }

    public void onPlayerDeath(LivingEntity entity) {
        if (mc.player != null && entity == mc.player) {
            disableCoordinates("death");
        }
    }

    public void resetTracking() {
        lastPosition = null;
    }

    private void disableCoordinates(String reason) {
        HudClientModule hudClient = JewDust.moduleManager.getModuleByClass(HudClientModule.class);
        if (hudClient != null
                && hudClient.setElementEnabled(CoordinatesHudModule.class, false)) {
            Command.sendMessage("Coordinates disabled " + reason + " detected");
        }
        resetTracking();
    }

    public CoordinatesHudModule() {
        super("Coordinates");
    }

    @Override
    public void render(Render2DEvent event) {
        GuiGraphics ctx = event.getContext();

        int x = (int) Math.floor(mc.player.getX());
        int y = (int) Math.floor(mc.player.getY());
        int z = (int) Math.floor(mc.player.getZ());

        boolean nether = mc.level.dimension().equals(Level.NETHER);
        boolean end = mc.level.dimension().equals(Level.END);

        String main = x + ", " + y + ", " + z + (end ? "" : " ");
        String other;
        if (end) {
            other = "";
        } else if (nether) {
            other = "[" + (x * 8) + ", " + (z * 8) + "]";
        } else {
            other = "[" + Math.floorDiv(x, 8) + ", " + Math.floorDiv(z, 8) + "]";
        }

        int totalWidth = mc.font.width(main) + mc.font.width(other);
        HudClientModule hudClient = JewDust.moduleManager.getModuleByClass(HudClientModule.class);
        HudPosition pos = hudClient != null ? hudClient.positionOf(this) : HudPosition.BOTTOM_RIGHT;
        int linesBelow = hudClient != null ? hudClient.linesBelow(this) : 0;

        int rx = lineX(pos, totalWidth);
        int ry = blockTop(pos, 1, linesBelow, 0);

        ctx.drawString(mc.font, main, rx, ry, WHITE);
        if (!other.isEmpty()) {
            ctx.drawString(mc.font, other, rx + mc.font.width(main), ry, GRAY);
        }
    }
}