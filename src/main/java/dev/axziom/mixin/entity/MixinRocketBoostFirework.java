package dev.axziom.mixin.entity;

import dev.axziom.JewDust;
import dev.axziom.features.modules.movement.RocketBoost;
import net.minecraft.client.Minecraft;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.projectile.FireworkRocketEntity;
import net.minecraft.world.phys.Vec3;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.ModifyArg;

@Mixin(FireworkRocketEntity.class)
public abstract class MixinRocketBoostFirework {
    @Shadow
    private LivingEntity attachedToEntity;

    @ModifyArg(method = "tick", at = @At(value = "INVOKE",
            target = "Lnet/minecraft/world/entity/LivingEntity;setDeltaMovement(Lnet/minecraft/world/phys/Vec3;)V"), index = 0)
    private Vec3 jewdust$applyRocketBoost(Vec3 vanillaVelocity) {
        if (Minecraft.getInstance().player == null || attachedToEntity != Minecraft.getInstance().player) return vanillaVelocity;
        RocketBoost module = JewDust.moduleManager.getModuleByClass(RocketBoost.class);
        return module == null || !module.isEnabled() ? vanillaVelocity : module.overrideFireworkVelocity(vanillaVelocity);
    }
}
