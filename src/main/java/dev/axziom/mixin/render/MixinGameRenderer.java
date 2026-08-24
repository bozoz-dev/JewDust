package dev.axziom.mixin.render;

import com.mojang.blaze3d.vertex.PoseStack;
import dev.axziom.JewDust;
import dev.axziom.features.modules.render.NoRenderModule;
import dev.axziom.features.modules.player.FreecamModule;
import dev.axziom.features.modules.render.ShadersModule;
import dev.axziom.util.render.HandShaderChain;
import dev.axziom.util.render.HandShaderRender;
import net.minecraft.client.DeltaTracker;
import net.minecraft.client.Minecraft;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.client.renderer.GameRenderer;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.joml.Matrix4f;

@Mixin(GameRenderer.class)
public class MixinGameRenderer {
    @Unique private boolean jewdust$freecamPicking;

    @Inject(
            method = "renderLevel",
            at = @At(
                    value = "INVOKE",
                    target = "Lnet/minecraft/client/renderer/feature/FeatureRenderDispatcher;renderAllFeatures()V",
                    shift = At.Shift.AFTER,
                    ordinal = 0
            )
    )
    private void handShader$composite(DeltaTracker deltaTracker, CallbackInfo ci) {
        ShadersModule mod = JewDust.moduleManager.getModuleByClass(ShadersModule.class);
        if (mod == null || !mod.wantsHandShader()) return;
        HandShaderRender.composite(
                HandShaderChain.get(mod.handOutline.getValue(), mod.getHandThickness(), mod.handFill.getValue(),
                        mod.handGlow.getValue(), mod.getHandGlowRadius(), mod.getHandGlowIntensity()));
    }

    @Inject(method = "pick", at = @At("HEAD"), cancellable = true)
    private void jewdust$freecamPick(float partialTick, CallbackInfo ci) {
        Minecraft minecraft = Minecraft.getInstance();
        if (jewdust$freecamPicking || minecraft.player == null || JewDust.moduleManager == null) return;
        FreecamModule freecam = JewDust.moduleManager.getModuleByClass(FreecamModule.class);
        if (freecam == null || !freecam.isEnabled()) return;

        LocalPlayer player = minecraft.player;
        double x = player.getX();
        double y = player.getY();
        double z = player.getZ();
        double xo = player.xo;
        double yo = player.yo;
        double zo = player.zo;
        double xOld = player.xOld;
        double yOld = player.yOld;
        double zOld = player.zOld;
        float yaw = player.getYRot();
        float pitch = player.getXRot();
        float yawOld = player.yRotO;
        float pitchOld = player.xRotO;

        jewdust$freecamPicking = true;
        try {
            double eyeHeight = player.getEyeHeight();
            player.setPosRaw(freecam.getX(partialTick), freecam.getY(partialTick) - eyeHeight,
                    freecam.getZ(partialTick));
            player.xo = freecam.getX(0.0f);
            player.yo = freecam.getY(0.0f) - eyeHeight;
            player.zo = freecam.getZ(0.0f);
            player.xOld = player.xo;
            player.yOld = player.yo;
            player.zOld = player.zo;
            player.setYRot(freecam.getYaw(partialTick));
            player.setXRot(freecam.getPitch(partialTick));
            player.yRotO = freecam.getYaw(0.0f);
            player.xRotO = freecam.getPitch(0.0f);

            ((GameRenderer) (Object) this).pick(partialTick);
        } finally {
            player.setPosRaw(x, y, z);
            player.xo = xo;
            player.yo = yo;
            player.zo = zo;
            player.xOld = xOld;
            player.yOld = yOld;
            player.zOld = zOld;
            player.setYRot(yaw);
            player.setXRot(pitch);
            player.yRotO = yawOld;
            player.xRotO = pitchOld;
            jewdust$freecamPicking = false;
        }
        ci.cancel();
    }

    @Inject(method = "renderItemInHand", at = @At("HEAD"), cancellable = true)
    private void jewdust$freecamHand(float partialTick, boolean sleeping, Matrix4f matrix, CallbackInfo ci) {
        if (JewDust.moduleManager == null) return;
        FreecamModule freecam = JewDust.moduleManager.getModuleByClass(FreecamModule.class);
        if (freecam != null && !freecam.shouldRenderHands()) ci.cancel();
    }

    @Inject(method = "displayItemActivation", at = @At("HEAD"), cancellable = true)
    private void jewdust$noTotem(ItemStack floatingItem, CallbackInfo ci) {
        if (floatingItem.is(Items.TOTEM_OF_UNDYING) && NoRenderModule.isActive(m -> m.noTotem.getValue())) {
            ci.cancel();
        }
    }

    @Inject(method = "bobView", at = @At("HEAD"), cancellable = true)
    private void jewdust$noBob(PoseStack matrices, float tickDelta, CallbackInfo ci) {
        if (NoRenderModule.isActive(m -> m.noBob.getValue())) {
            ci.cancel();
        }
    }

    @Inject(method = "bobHurt", at = @At("HEAD"), cancellable = true)
    private void jewdust$noTilt(PoseStack matrices, float tickDelta, CallbackInfo ci) {
        if (NoRenderModule.isActive(m -> m.noTilt.getValue())) {
            ci.cancel();
        }
    }
}
