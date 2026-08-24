package dev.axziom.mixin.network;

import dev.axziom.JewDust;
import dev.axziom.event.impl.network.AttackBlockEvent;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.MultiPlayerGameMode;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import static dev.axziom.util.traits.Util.EVENT_BUS;

@Mixin(MultiPlayerGameMode.class)
public class MixinMultiPlayerGameMode {

    @Unique private float jewdust$origUseYaw, jewdust$origUsePitch;

    @Inject(method = "useItem", at = @At("HEAD"))
    private void jewdust$useItemRotateHead(Player player, InteractionHand hand, CallbackInfoReturnable<InteractionResult> cir) {
        if (player != Minecraft.getInstance().player) return;
        if (JewDust.rotationManager == null || !JewDust.rotationManager.isRotating()) return;
        if (JewDust.rotationManager.isBypassUseSpoof()) return;

        jewdust$origUseYaw = player.getYRot();
        jewdust$origUsePitch = player.getXRot();
        player.setYRot(JewDust.rotationManager.getRotationYaw());
        player.setXRot(JewDust.rotationManager.getRotationPitch());
    }

    @Inject(method = "useItem", at = @At("RETURN"))
    private void jewdust$useItemRotateReturn(Player player, InteractionHand hand, CallbackInfoReturnable<InteractionResult> cir) {
        if (player != Minecraft.getInstance().player) return;
        if (JewDust.rotationManager == null || !JewDust.rotationManager.isRotating()) return;
        if (JewDust.rotationManager.isBypassUseSpoof()) return;

        player.setYRot(jewdust$origUseYaw);
        player.setXRot(jewdust$origUsePitch);
    }
    @Inject(method = "startDestroyBlock", at = @At("HEAD"), cancellable = true)
    private void jewdust$onStartDestroyBlock(BlockPos pos, Direction direction, CallbackInfoReturnable<Boolean> cir) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.level == null || mc.player == null) return;
        AttackBlockEvent event = new AttackBlockEvent(pos, mc.level.getBlockState(pos), direction);
        if (EVENT_BUS.post(event)) {
            cir.setReturnValue(false);
        }
    }

}
