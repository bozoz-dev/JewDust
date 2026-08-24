package dev.axziom.mixin.entity;

import dev.axziom.JewDust;
import dev.axziom.manager.RotationManager;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.util.Mth;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import static dev.axziom.util.traits.Util.mc;

@Mixin(value = LocalPlayer.class, priority = Integer.MAX_VALUE)
public class MixinLocalPlayerRotation {
    @Shadow private float xRotLast;

    @Unique private float jewdust$savedYaw, jewdust$savedPitch;
    @Unique private boolean jewdust$spoofed;

    @Inject(method = "sendPosition", at = @At("HEAD"))
    private void jewdust$spoofRotationHead(CallbackInfo ci) {
        jewdust$spoofed = false;
        RotationManager rm = JewDust.rotationManager;
        if (rm == null) return;

        boolean motion = rm.isRotating();
        boolean silent = rm.isSilentSyncRequired();
        if (!motion && !silent) return;

        jewdust$savedYaw = mc.player.getYRot();
        jewdust$savedPitch = mc.player.getXRot();

        float outYaw = jewdust$savedYaw;
        float outPitch = jewdust$savedPitch;

        if (motion) {
            outYaw = rm.getRotationYaw();
            outPitch = rm.getRotationPitch();
            rm.setServerDeltaYaw(outYaw - rm.getServerYaw());
            rm.setServerYaw(outYaw);
            rm.setServerPitch(outPitch);
        } else {
            xRotLast -= 4;
            float f = (float) ((Math.random() * 2.0 - 1.0) * 0.001f);
            outPitch = Mth.clamp(outPitch + f, -90.0f, 90.0f);
        }

        mc.player.setYRot(outYaw);
        mc.player.setXRot(outPitch);
        jewdust$spoofed = true;
    }

    @Inject(method = "sendPosition", at = @At("TAIL"))
    private void jewdust$spoofRotationTail(CallbackInfo ci) {
        RotationManager rm = JewDust.rotationManager;
        if (rm == null) return;

        if (rm.isRotating()) {
            rm.setServerYaw(mc.player.getYRot());
            rm.setServerPitch(mc.player.getXRot());
        }

        if (jewdust$spoofed) {
            mc.player.setYRot(jewdust$savedYaw);
            mc.player.setXRot(jewdust$savedPitch);
            jewdust$spoofed = false;
        }

        rm.setSilentSyncRequired(false);
        rm.resetSilentTick();
    }
}
