package dev.axziom.features.modules.combat;

import dev.axziom.JewDust;
import dev.axziom.event.impl.entity.player.TickEvent;
import dev.axziom.event.system.Subscribe;
import dev.axziom.features.modules.Module;
import dev.axziom.manager.RotationRequest;
import dev.axziom.util.inventory.SwapMode;
import dev.axziom.util.inventory.SwapPriority;
import dev.axziom.mixin.client.ClientLevelAccessor;
import dev.axziom.util.inventory.InventoryUtil;
import dev.axziom.util.inventory.Result;
import net.minecraft.network.protocol.game.ServerboundUseItemPacket;
import net.minecraft.util.Mth;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.phys.Vec3;

public class PhaseModule extends Module {

    private static final double CORNER_THRESHOLD = 0.5;
    private static final double CORNER_OFFSET = 0.5;

    private static final int PRIORITY = 150;

    public PhaseModule() {
        super("Phase", "Phases into walls", Category.COMBAT);
    }

    @Subscribe
    private void onTick(TickEvent event) {
        if (mc.player == null || mc.level == null) {
            disable();
            return;
        }

        if (JewDust.rotationManager.isSilentSyncRequiredAtLeast(PRIORITY)) return;

        Result pearl = InventoryUtil.find(Items.ENDER_PEARL, InventoryUtil.FULL_SCOPE);
        if (!pearl.found()) {
            disable();
            return;
        }

        if (mc.player.getCooldowns().isOnCooldown(new ItemStack(Items.ENDER_PEARL))) {
            disable();
            return;
        }

        if (mc.player.isCrouching()) {
            disable();
            return;
        }

        Vec3 target = calculateTargetPos();
        float yaw = calcYaw(target);
        float pitch = mc.player.getBlockY() > 4 ? 85f : 75f;

        JewDust.rotationManager.submit(new RotationRequest("phase", PRIORITY, yaw, pitch, RotationRequest.Mode.SILENT));

        mc.gameMode.ensureHasSentCarriedItem();
        boolean thrown = JewDust.swapManager.withSwap(pearl, SwapMode.ALTSILENT, SwapPriority.ESCAPE, () -> {
            try (var handler = ((ClientLevelAccessor) mc.level).jewdust$getBlockStatePredictionHandler().startPredicting()) {
                mc.getConnection().send(new ServerboundUseItemPacket(pearl.hand(), handler.currentSequence(), yaw, pitch));
            }
        });

        if (thrown) disable();
    }

    private Vec3 calculateTargetPos() {
        double playerX = mc.player.getX();
        double playerZ = mc.player.getZ();

        double nearestIntX = Math.round(playerX);
        double nearestIntZ = Math.round(playerZ);
        double dxCorner = nearestIntX - playerX;
        double dzCorner = nearestIntZ - playerZ;

        double threshold = CORNER_THRESHOLD;
        double offset = CORNER_OFFSET;
        if (Math.abs(dxCorner) <= threshold && Math.abs(dzCorner) <= threshold) {
            return new Vec3(
                playerX + Mth.clamp(dxCorner, -offset, offset),
                mc.player.getY() - 0.5,
                playerZ + Mth.clamp(dzCorner, -offset, offset)
            );
        }

        final double A = Math.PI / 13;
        final double B = Math.PI / 4;

        double x = playerX + Mth.clamp(
            toClosest(playerX, Math.floor(playerX) + A, Math.floor(playerX) + B) - playerX,
            -0.2, 0.2);
        double z = playerZ + Mth.clamp(
            toClosest(playerZ, Math.floor(playerZ) + A, Math.floor(playerZ) + B) - playerZ,
            -0.2, 0.2);

        return new Vec3(x, mc.player.getY() - 0.5, z);
    }

    private double toClosest(double num, double min, double max) {
        return (num - min) > (max - num) ? max : min;
    }

    private float calcYaw(Vec3 target) {
        Vec3 eye = mc.player.getEyePosition();
        Vec3 diff = target.subtract(eye);
        return (float) Math.toDegrees(Math.atan2(-diff.x, diff.z));
    }
}
