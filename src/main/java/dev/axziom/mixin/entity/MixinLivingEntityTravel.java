package dev.axziom.mixin.entity;

import com.llamalad7.mixinextras.injector.ModifyExpressionValue;
import com.llamalad7.mixinextras.injector.ModifyReturnValue;
import dev.axziom.JewDust;
import dev.axziom.features.modules.movement.RocketBoost;
import dev.axziom.manager.RotationManager;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.phys.Vec3;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import static dev.axziom.util.traits.Util.mc;

@Mixin(LivingEntity.class)
public class MixinLivingEntityTravel {
    @Unique private float jewdust$origYaw, jewdust$origPitch;
    @Unique private boolean jewdust$applied;

    @Inject(method = "travel", at = @At("HEAD"))
    private void jewdust$travelHead(Vec3 movementInput, CallbackInfo ci) {
        jewdust$applied = false;

        LivingEntity self = (LivingEntity) (Object) this;
        if (self != mc.player || !jewdust$spoofing()) return;

        jewdust$origYaw = self.getYRot();
        jewdust$origPitch = self.getXRot();
        self.setYRot(JewDust.rotationManager.getRotationYaw());
        self.setXRot(JewDust.rotationManager.getRotationPitch());
        jewdust$applied = true;
    }

    @Inject(method = "travel", at = @At("RETURN"))
    private void jewdust$travelReturn(Vec3 movementInput, CallbackInfo ci) {
        if (!jewdust$applied) return;
        jewdust$applied = false;

        LivingEntity self = (LivingEntity) (Object) this;
        self.setYRot(jewdust$origYaw);
        self.setXRot(jewdust$origPitch);
    }

    @ModifyReturnValue(method = "updateFallFlyingMovement", at = @At("RETURN"))
    private Vec3 jewdust$rocketBoostTravel(Vec3 vanillaVelocity) {
        if ((Object) this != mc.player || JewDust.moduleManager == null) return vanillaVelocity;
        RocketBoost rocketBoost = JewDust.moduleManager.getModuleByClass(RocketBoost.class);
        if (rocketBoost == null || !rocketBoost.isEnabled()) return vanillaVelocity;

        Vec3 override = rocketBoost.consumeTravelOverride();
        return override == null ? vanillaVelocity : override;
    }

    @ModifyExpressionValue(
            method = "jumpFromGround",
            at = @At(value = "INVOKE", target = "Lnet/minecraft/world/entity/LivingEntity;getYRot()F"))
    private float jewdust$jumpYaw(float original) {
        if ((Object) this != mc.player || !jewdust$spoofing()) return original;
        return JewDust.rotationManager.getRotationYaw();
    }

    @Unique
    private boolean jewdust$spoofing() {
        RotationManager rm = JewDust.rotationManager;
        return rm != null && rm.isMoveFixEnabled() && rm.isRotating();
    }

    @ModifyExpressionValue(
            method = "handleRelativeFrictionAndCalculateMovement",
            at = @At(value = "FIELD", target = "Lnet/minecraft/world/entity/LivingEntity;horizontalCollision:Z"))
    private boolean jewdust$noScaffoldClimb(boolean original) {
        if ((Object) this != mc.player || !original) return original;
        return !((LivingEntity) (Object) this).getInBlockState().is(Blocks.SCAFFOLDING);
    }
}
