package dev.axziom.mixin.render.gui;

import dev.axziom.event.impl.render.Render2DEvent;
import dev.axziom.features.modules.render.NoRenderModule;
import net.minecraft.client.DeltaTracker;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Gui;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.resources.Identifier;
import net.minecraft.world.entity.Entity;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.Redirect;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import static dev.axziom.util.traits.Util.EVENT_BUS;

@Mixin(Gui.class)
public abstract class MixinInGameHud {
    @Shadow
    protected abstract void renderTextureOverlay(GuiGraphics context, Identifier texture, float alpha);

    @Shadow
    @Final
    private static Identifier POWDER_SNOW_OUTLINE_LOCATION;

    @Inject(method = "render", at = @At("RETURN"))
    public void render(GuiGraphics context, DeltaTracker tickCounter, CallbackInfo ci) {
        if (Minecraft.getInstance().gui.getDebugOverlay().showDebugScreen()) return;

        Render2DEvent event = new Render2DEvent(context, tickCounter.getGameTimeDeltaPartialTick(true));
        EVENT_BUS.post(event);
    }

    @Inject(method = "renderEffects", at = @At("HEAD"), cancellable = true)
    private void jewdust$noPotions(GuiGraphics context, DeltaTracker tickCounter, CallbackInfo ci) {
        if (NoRenderModule.isActive(m -> m.noPotIcon.getValue())) {
            ci.cancel();
        }
    }

    @Inject(method = "renderCrosshair", at = @At("HEAD"), cancellable = true)
    private void jewdust$noCrosshair(GuiGraphics context, DeltaTracker tickCounter, CallbackInfo ci) {
        if (NoRenderModule.isActive(m -> m.noCrosshair.getValue())) {
            ci.cancel();
        }
    }

    @Inject(method = "renderSelectedItemName", at = @At("HEAD"), cancellable = true)
    private void jewdust$noItemName(GuiGraphics context, CallbackInfo ci) {
        if (NoRenderModule.isActive(m -> m.noItemName.getValue())) {
            ci.cancel();
        }
    }

    @Inject(method = "renderPortalOverlay", at = @At("HEAD"), cancellable = true)
    private void jewdust$noPortal(GuiGraphics context, float alpha, CallbackInfo ci) {
        if (NoRenderModule.isActive(m -> m.noPortal.getValue())) {
            ci.cancel();
        }
    }

    @Inject(method = "renderVignette", at = @At("HEAD"), cancellable = true)
    private void jewdust$noVignette(GuiGraphics context, Entity entity, CallbackInfo ci) {
        if (NoRenderModule.isActive(m -> m.noVignette.getValue())) {
            ci.cancel();
        }
    }

    @Inject(method = "renderConfusionOverlay", at = @At("HEAD"), cancellable = true)
    private void jewdust$noNausea(GuiGraphics context, float distortionStrength, CallbackInfo ci) {
        if (NoRenderModule.isActive(m -> m.noNausea.getValue())) {
            ci.cancel();
        }
    }

    @Redirect(
        method = "renderCameraOverlays",
        at = @At(
            value = "INVOKE",
            target = "Lnet/minecraft/client/gui/Gui;renderTextureOverlay(Lnet/minecraft/client/gui/GuiGraphics;Lnet/minecraft/resources/Identifier;F)V"
        )
    )
    private void jewdust$noCameraOverlay(Gui instance, GuiGraphics context, Identifier texture, float alpha) {
        if (NoRenderModule.isActive(m -> m.noPowderedSnow.getValue()) && POWDER_SNOW_OUTLINE_LOCATION.equals(texture)) {
            return;
        }
        if (NoRenderModule.isActive(m -> m.noPumpkin.getValue()) && texture.getPath().contains("pumpkin")) {
            return;
        }
        renderTextureOverlay(context, texture, alpha);
    }
}
