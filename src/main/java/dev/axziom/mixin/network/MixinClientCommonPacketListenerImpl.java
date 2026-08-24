package dev.axziom.mixin.network;

import dev.axziom.event.impl.network.DisconnectEvent;
import net.minecraft.client.multiplayer.ClientCommonPacketListenerImpl;
import net.minecraft.network.DisconnectionDetails;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import static dev.axziom.util.traits.Util.EVENT_BUS;

@Mixin(ClientCommonPacketListenerImpl.class)
public class MixinClientCommonPacketListenerImpl {

    @Inject(method = "onDisconnect", at = @At("HEAD"))
    private void onDisconnectHook(DisconnectionDetails details, CallbackInfo ci) {
        EVENT_BUS.post(new DisconnectEvent());
    }
}
