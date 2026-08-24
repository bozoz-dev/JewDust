package dev.axziom.mixin.entity;

import dev.axziom.JewDust;
import dev.axziom.features.modules.movement.VelocityModule;
import dev.axziom.features.modules.movement.YawLockModule;
import dev.axziom.features.modules.player.FreeLookModule;
import dev.axziom.features.modules.player.FreecamModule;
import dev.axziom.features.modules.render.ShadersModule;
import dev.axziom.manager.RotationManager;
import net.minecraft.client.Minecraft;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.phys.Vec3;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.ModifyVariable;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(Entity.class)
public class MixinEntity {

    @Inject(method = "turn", at = @At("HEAD"), cancellable = true)
    private void jewdust$detachedCameraLook(double deltaX, double deltaY, CallbackInfo ci) {
        Entity self = (Entity) (Object) this;
        if (self != Minecraft.getInstance().player || JewDust.moduleManager == null) return;

        FreecamModule freecam = JewDust.moduleManager.getModuleByClass(FreecamModule.class);
        if (freecam != null && freecam.isEnabled()) {
            freecam.changeLookDirection(deltaX, deltaY);
            ci.cancel();
            return;
        }

        FreeLookModule freeLook = JewDust.moduleManager.getModuleByClass(FreeLookModule.class);
        if (freeLook != null && freeLook.cameraMode()) {
            freeLook.changeCameraLook(deltaX, deltaY);
            ci.cancel();
        }
    }

    @ModifyVariable(method = "turn", at = @At("HEAD"), argsOnly = true, ordinal = 0)
    private double jewdust$lockHorizontalMouse(double deltaX) {
        if ((Object) this != Minecraft.getInstance().player || JewDust.moduleManager == null) return deltaX;
        YawLockModule yawLock = JewDust.moduleManager.getModuleByClass(YawLockModule.class);
        return yawLock != null && yawLock.blocksClientYaw() ? 0.0 : deltaX;
    }

    @Inject(method = "getLookAngle", at = @At("HEAD"), cancellable = true)
    private void jewdust$spoofLookAngle(CallbackInfoReturnable<Vec3> cir) {
        Entity self = (Entity) (Object) this;
        if (self != Minecraft.getInstance().player) return;

        RotationManager rm = JewDust.rotationManager;
        if (rm == null || !rm.isMoveFixEnabled() || !rm.isRotating()) return;

        cir.setReturnValue(self.calculateViewVector(rm.getRotationPitch(), rm.getRotationYaw()));
    }
    @Inject(method = "isCurrentlyGlowing", at = @At("RETURN"), cancellable = true)
    private void onIsCurrentlyGlowing(CallbackInfoReturnable<Boolean> cir) {
        Entity self = (Entity) (Object) this;

        ShadersModule shaders = JewDust.moduleManager.getModuleByClass(ShadersModule.class);
        if (shaders != null && shaders.isEnabled() && shaders.shouldShader(self)) {
            cir.setReturnValue(true);
            return;
        }

    }

    @Inject(method = "getTeamColor", at = @At("RETURN"), cancellable = true)
    private void onGetTeamColor(CallbackInfoReturnable<Integer> cir) {
        Entity self = (Entity) (Object) this;

        ShadersModule shaders = JewDust.moduleManager.getModuleByClass(ShadersModule.class);
        if (shaders != null && shaders.isEnabled() && shaders.shouldShader(self)) {
            cir.setReturnValue(shaders.getRgbFor(self));
            return;
        }

    }

    @Inject(method = "push(Lnet/minecraft/world/entity/Entity;)V", at = @At("HEAD"), cancellable = true)
    private void cancelEntityPush(Entity other, CallbackInfo ci) {
        VelocityModule velocity = JewDust.moduleManager.getModuleByClass(VelocityModule.class);
        if (velocity == null || !velocity.isEnabled() || !velocity.entityPush.getValue() || !velocity.phaseConditionMet()) return;
        LocalPlayer localPlayer = Minecraft.getInstance().player;
        if (localPlayer == null) return;
        Object self = this;
        if (self == localPlayer || other == localPlayer) {
            ci.cancel();
        }
    }
}