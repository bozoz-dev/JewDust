package dev.axziom.mixin.render;

import com.mojang.blaze3d.vertex.PoseStack;
import dev.axziom.JewDust;
import dev.axziom.features.modules.render.SeeThroughModule;
import dev.axziom.util.render.SeeThroughRender;
import net.minecraft.client.renderer.SubmitNodeCollector;
import net.minecraft.client.renderer.entity.EntityRenderDispatcher;
import net.minecraft.client.renderer.entity.state.EntityRenderState;
import net.minecraft.client.renderer.state.CameraRenderState;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(value = EntityRenderDispatcher.class, priority = 1100)
public class MixinEntityRenderDispatcher {
    @Unique
    private boolean jewdust$seeThroughActive;

    @Inject(
            method = "submit(Lnet/minecraft/client/renderer/entity/state/EntityRenderState;Lnet/minecraft/client/renderer/state/CameraRenderState;DDDLcom/mojang/blaze3d/vertex/PoseStack;Lnet/minecraft/client/renderer/SubmitNodeCollector;)V",
            at = @At(
                    value = "INVOKE",
                    target = "Lnet/minecraft/client/renderer/entity/EntityRenderer;submit(Lnet/minecraft/client/renderer/entity/state/EntityRenderState;Lcom/mojang/blaze3d/vertex/PoseStack;Lnet/minecraft/client/renderer/SubmitNodeCollector;Lnet/minecraft/client/renderer/state/CameraRenderState;)V",
                    shift = At.Shift.BEFORE
            ),
            require = 0
    )
    private void jewdust$beginSeeThrough(
            EntityRenderState state,
            CameraRenderState camera,
            double x,
            double y,
            double z,
            PoseStack poseStack,
            SubmitNodeCollector collector,
            CallbackInfo ci
    ) {
        jewdust$seeThroughActive = false;

        SeeThroughModule seeThrough = JewDust.moduleManager == null
                ? null
                : JewDust.moduleManager.getModuleByClass(SeeThroughModule.class);

        if (seeThrough != null
                && seeThrough.isEnabled()
                && seeThrough.shouldSeeThrough(state)) {
            jewdust$seeThroughActive = true;
            SeeThroughRender.begin();
        }
    }

    @Inject(
            method = "submit(Lnet/minecraft/client/renderer/entity/state/EntityRenderState;Lnet/minecraft/client/renderer/state/CameraRenderState;DDDLcom/mojang/blaze3d/vertex/PoseStack;Lnet/minecraft/client/renderer/SubmitNodeCollector;)V",
            at = @At(
                    value = "INVOKE",
                    target = "Lnet/minecraft/client/renderer/entity/EntityRenderer;submit(Lnet/minecraft/client/renderer/entity/state/EntityRenderState;Lcom/mojang/blaze3d/vertex/PoseStack;Lnet/minecraft/client/renderer/SubmitNodeCollector;Lnet/minecraft/client/renderer/state/CameraRenderState;)V",
                    shift = At.Shift.AFTER
            ),
            require = 0
    )
    private void jewdust$endSeeThrough(
            EntityRenderState state,
            CameraRenderState camera,
            double x,
            double y,
            double z,
            PoseStack poseStack,
            SubmitNodeCollector collector,
            CallbackInfo ci
    ) {
        if (!jewdust$seeThroughActive) return;

        jewdust$seeThroughActive = false;
        SeeThroughRender.end();
    }
}