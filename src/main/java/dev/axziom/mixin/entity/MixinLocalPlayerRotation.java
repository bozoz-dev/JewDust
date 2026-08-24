package dev.axziom.mixin.entity;

import dev.axziom.JewDust;
import dev.axziom.features.modules.movement.RocketBoost;
import dev.axziom.features.modules.movement.YawLockModule;
import dev.axziom.manager.RotationManager;
import net.minecraft.client.player.LocalPlayer;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(value = LocalPlayer.class, priority = Integer.MAX_VALUE)
public class MixinLocalPlayerRotation {
    @Shadow private float xRotLast;

    @Unique private float jewdust$savedYaw;
    @Unique private float jewdust$savedPitch;
    @Unique private boolean jewdust$spoofed;

    @Inject(method = "sendPosition", at = @At("HEAD"))
    private void jewdust$spoofRotationHead(CallbackInfo ci) {
        jewdust$spoofed = false;
        LocalPlayer player = (LocalPlayer) (Object) this;
        RotationManager rotation = JewDust.rotationManager;

        boolean motionRotation = rotation != null && rotation.isRotating();
        boolean managerSilent = rotation != null && rotation.isSilentSyncRequired();
        boolean viewSilent = false;

        jewdust$savedYaw = player.getYRot();
        jewdust$savedPitch = player.getXRot();
        float outputYaw = jewdust$savedYaw;
        float outputPitch = jewdust$savedPitch;

        // Existing combat rotations keep priority over camera utility modules.
        if (motionRotation) {
            outputYaw = rotation.getRotationYaw();
            outputPitch = rotation.getRotationPitch();
        } else if (managerSilent) {
            // The old code restored the client rotation here, undoing every
            // silent request. Send the manager's actual server rotation instead.
            outputYaw = rotation.getServerYaw();
            outputPitch = rotation.getServerPitch();
        } else if (JewDust.moduleManager != null) {
            RocketBoost rocketBoost = JewDust.moduleManager.getModuleByClass(RocketBoost.class);
            if (rocketBoost != null && rocketBoost.hasSilentPitchOverride()) {
                outputYaw = rocketBoost.getControlledYaw();
                outputPitch = rocketBoost.getControlledPitch();
                viewSilent = true;
            }

            YawLockModule yawLock = JewDust.moduleManager.getModuleByClass(YawLockModule.class);
            if (yawLock != null && yawLock.hasSilentServerYaw()) {
                outputYaw = yawLock.getLockedYaw();
                viewSilent = true;
            }
        }

        if (!motionRotation && !managerSilent && !viewSilent) return;

        // Force sendPosition to include rotation even when the locked angle has
        // not changed since the previous movement packet.
        xRotLast -= 4.0f;
        player.setYRot(outputYaw);
        player.setXRot(outputPitch);
        jewdust$spoofed = true;

        if (rotation != null) {
            rotation.setServerDeltaYaw(outputYaw - rotation.getServerYaw());
            rotation.setServerYaw(outputYaw);
            rotation.setServerPitch(outputPitch);
        }
    }

    @Inject(method = "sendPosition", at = @At("TAIL"))
    private void jewdust$spoofRotationTail(CallbackInfo ci) {
        LocalPlayer player = (LocalPlayer) (Object) this;
        if (jewdust$spoofed) {
            player.setYRot(jewdust$savedYaw);
            player.setXRot(jewdust$savedPitch);
            jewdust$spoofed = false;
        }

        RotationManager rotation = JewDust.rotationManager;
        if (rotation != null) {
            rotation.setSilentSyncRequired(false);
            rotation.resetSilentTick();
        }
    }
}
