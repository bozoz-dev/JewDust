package dev.axziom.mixin.entity;

import dev.axziom.JewDust;
import dev.axziom.features.modules.render.SwingModule;
import net.minecraft.client.Minecraft;
import net.minecraft.world.entity.LivingEntity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(LivingEntity.class)
public abstract class MixinSwingLivingEntity {
    @Inject(method = "getCurrentSwingDuration", at = @At("RETURN"), cancellable = true)
    private void jewdust$changeSwingDuration(CallbackInfoReturnable<Integer> cir) {
        if ((Object) this != Minecraft.getInstance().player) return;
        SwingModule module = JewDust.moduleManager.getModuleByClass(SwingModule.class);
        if (module == null || !module.isEnabled()) return;
        cir.setReturnValue(Math.max(1, Math.round(cir.getReturnValue() / module.speed.getValue())));
    }
}
