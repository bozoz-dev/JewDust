package dev.axziom.mixin.render;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.math.Axis;
import dev.axziom.JewDust;
import dev.axziom.features.modules.render.SwingModule;
import net.minecraft.client.renderer.ItemInHandRenderer;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.HumanoidArm;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(ItemInHandRenderer.class)
public abstract class MixinSwingItemInHandRenderer {
    @Inject(method = "applyItemArmAttackTransform", at = @At("HEAD"), cancellable = true)
    private void jewdust$applyCustomSwing(PoseStack matrices, HumanoidArm arm, float progress, CallbackInfo ci) {
        SwingModule module = JewDust.moduleManager.getModuleByClass(SwingModule.class);
        if (module == null || !module.isEnabled()
                || module.swingAnimation.getValue() == SwingModule.SwingAnimation.VANILLA) {
            return;
        }

        float side = arm == HumanoidArm.RIGHT ? 1.0f : -1.0f;
        float root = Mth.sqrt(progress);
        float arc = Mth.sin(root * Mth.PI);
        float sweep = Mth.sin(progress * Mth.PI);

        if (module.swingAnimation.getValue() == dev.axziom.features.modules.render.SwingModule.SwingAnimation.ONE_EIGHT) {
            matrices.translate(-0.45f * side * arc, 0.18f * sweep, -0.25f * sweep);
            matrices.mulPose(Axis.YP.rotationDegrees(side * -50.0f * arc));
            matrices.mulPose(Axis.ZP.rotationDegrees(side * -35.0f * arc));
            matrices.mulPose(Axis.XP.rotationDegrees(-25.0f * sweep));
        } else {
            matrices.translate(-0.28f * side * arc, 0.10f * sweep, -0.12f * sweep);
            matrices.mulPose(Axis.YP.rotationDegrees(side * -32.0f * arc));
            matrices.mulPose(Axis.ZP.rotationDegrees(side * -18.0f * arc));
        }
        ci.cancel();
    }

    @Inject(method = "applyEatTransform", at = @At("HEAD"), cancellable = true)
    private void jewdust$applyStaticEating(PoseStack matrices, float partialTick, HumanoidArm arm,
                                             ItemStack stack, Player player, CallbackInfo ci) {
        SwingModule module = JewDust.moduleManager.getModuleByClass(SwingModule.class);
        if (module == null || !module.isEnabled() || !module.usesStaticEating()) return;

        float side = arm == HumanoidArm.RIGHT ? 1.0f : -1.0f;
        matrices.translate(0.12f * side, -0.08f, 0.08f);
        matrices.mulPose(Axis.YP.rotationDegrees(side * 8.0f));
        matrices.mulPose(Axis.XP.rotationDegrees(-10.0f));
        ci.cancel();
    }
}
