package dev.axziom.features.modules.player;

import dev.axziom.JewDust;
import dev.axziom.event.impl.input.MouseInputEvent;
import dev.axziom.event.system.Subscribe;
import dev.axziom.features.modules.Module;
import dev.axziom.features.settings.Setting;
import dev.axziom.manager.RotationRequest;
import dev.axziom.util.inventory.SwapMode;
import dev.axziom.util.inventory.SwapPriority;
import dev.axziom.util.inventory.InventoryUtil;
import dev.axziom.util.inventory.Result;
import net.minecraft.world.item.Items;
import org.lwjgl.glfw.GLFW;

import static dev.axziom.util.inventory.InventoryUtil.FULL_SCOPE;

public class MiddleClickExtraModule extends Module {

    private static final int ROTATION_PRIORITY = 1000;

    private final Setting<Boolean> fireworkInAir = bool("FireworkInAir", true);

    public MiddleClickExtraModule() {
        super("MiddleClick", "Throws a pearl or firework on middle click.", Category.PLAYER);
    }

    @Subscribe
    private void onMouse(MouseInputEvent event) {
        if (nullCheck() || mc.screen != null) return;
        if (event.getButton() != GLFW.GLFW_MOUSE_BUTTON_MIDDLE) return;
        if (event.getAction() != GLFW.GLFW_PRESS) return;

        if (fireworkInAir.getValue() && mc.player.isFallFlying()) {
            Result firework = InventoryUtil.find(Items.FIREWORK_ROCKET, FULL_SCOPE);
            if (firework.found()) {
                JewDust.swapManager.withSwap(firework, SwapMode.ALTSILENT, SwapPriority.USER_ACTION,
                        () -> mc.gameMode.useItem(mc.player, firework.hand()));
            }
            return;
        }

        Result pearl = InventoryUtil.find(Items.ENDER_PEARL, FULL_SCOPE);
        if (pearl.found()) {

            JewDust.rotationManager.submit(new RotationRequest("MiddleClick.pearl", ROTATION_PRIORITY,
                    JewDust.rotationManager.getRealYaw(), JewDust.rotationManager.getRealPitch(),
                    RotationRequest.Mode.SILENT));
            JewDust.swapManager.withSwap(pearl, SwapMode.ALTSILENT, SwapPriority.USER_ACTION,
                    () -> {

                        JewDust.rotationManager.setBypassUseSpoof(true);
                        try {
                            mc.gameMode.useItem(mc.player, pearl.hand());
                        } finally {
                            JewDust.rotationManager.setBypassUseSpoof(false);
                        }
                    });
        }
    }
}
