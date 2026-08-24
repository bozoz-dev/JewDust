package dev.axziom.features.modules.combat;

import dev.axziom.JewDust;
import dev.axziom.event.system.Subscribe;
import dev.axziom.features.modules.Module;
import dev.axziom.features.settings.Setting;
import dev.axziom.manager.RotationRequest;
import dev.axziom.util.inventory.SwapMode;
import dev.axziom.util.inventory.SwapPriority;
import dev.axziom.mixin.client.ClientLevelAccessor;
import dev.axziom.util.inventory.InventoryUtil;
import dev.axziom.util.inventory.Result;
import net.minecraft.network.protocol.game.ServerboundUseItemPacket;
import net.minecraft.world.item.Items;

import static dev.axziom.util.inventory.InventoryUtil.FULL_SCOPE;

public class KeyPotionModule extends Module {

    private final Setting<Boolean> onGround = bool("OnGround", false);

    public KeyPotionModule() {
        super("KeyPotion", "Throws a splash potion on keybind press.", Category.PLAYER);
    }

    @Subscribe
    public void onEnable() {
        if (onGround.getValue() && !mc.player.onGround()) return;

        Result potion = InventoryUtil.find(Items.SPLASH_POTION, FULL_SCOPE);
        if (potion.found()) {
            float yaw = mc.player.getYRot();
            float pitch = 90f;
            JewDust.rotationManager.submit(new RotationRequest(
                    "KeyPotion", 20, yaw, pitch, RotationRequest.Mode.SILENT
            ));
            mc.gameMode.ensureHasSentCarriedItem();
            JewDust.swapManager.withSwap(potion, SwapMode.ALTSILENT, SwapPriority.USER_ACTION, () -> {
                try (var handler = ((ClientLevelAccessor) mc.level).jewdust$getBlockStatePredictionHandler().startPredicting()) {
                    mc.getConnection().send(new ServerboundUseItemPacket(potion.hand(), handler.currentSequence(), yaw, pitch));
                }
            });
        }
        disable();
    }
}
