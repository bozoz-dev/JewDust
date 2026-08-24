package dev.axziom.mixin.render;

import dev.axziom.JewDust;
import dev.axziom.features.modules.player.FreeLookModule;
import dev.axziom.features.modules.player.FreecamModule;
import net.minecraft.client.Camera;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.level.Level;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.ModifyArgs;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.invoke.arg.Args;

@Mixin(Camera.class)
public abstract class MixinCamera {
    @Shadow private boolean detached;

    @Shadow protected abstract void setRotation(float yaw, float pitch);
    @Shadow protected abstract void setPosition(double x, double y, double z);

    @ModifyArgs(
            method = "setup",
            at = @At(value = "INVOKE", target = "Lnet/minecraft/client/Camera;setRotation(FF)V")
    )
    private void jewdust$cameraRotation(Args args) {
        if (JewDust.moduleManager == null) return;

        FreecamModule freecam = JewDust.moduleManager.getModuleByClass(FreecamModule.class);
        if (freecam != null && freecam.isEnabled()) {
            args.set(0, freecam.getYaw(1.0f));
            args.set(1, freecam.getPitch(1.0f));
            return;
        }

        FreeLookModule freeLook = JewDust.moduleManager.getModuleByClass(FreeLookModule.class);
        if (freeLook != null && freeLook.isEnabled()) {
            args.set(0, freeLook.getCameraYaw());
            args.set(1, freeLook.getCameraPitch());
        }
    }

    @Inject(method = "setup", at = @At("TAIL"))
    private void jewdust$freecamPosition(Level level, Entity focusedEntity, boolean thirdPerson,
                                         boolean inverseView, float partialTick, CallbackInfo ci) {
        if (JewDust.moduleManager == null) return;
        FreecamModule freecam = JewDust.moduleManager.getModuleByClass(FreecamModule.class);
        if (freecam == null || !freecam.isEnabled()) return;

        setPosition(freecam.getX(partialTick), freecam.getY(partialTick), freecam.getZ(partialTick));
        setRotation(freecam.getYaw(partialTick), freecam.getPitch(partialTick));
        detached = true;
    }
}
