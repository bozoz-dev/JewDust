package dev.axziom.features.modules.movement;

import dev.axziom.features.modules.Module;
import dev.axziom.features.settings.Setting;
import dev.axziom.util.inventory.InventoryUtil;
import dev.axziom.util.inventory.Result;
import dev.axziom.util.inventory.ResultType;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.inventory.ClickType;
import net.minecraft.world.item.Items;

import static dev.axziom.util.inventory.InventoryUtil.FULL_SCOPE;

public final class Pitch40 extends Module {
    public final Setting<Boolean> controlPitch = bool("ControlPitch", true);
    public final Setting<Boolean> autoBoundAdjust = bool("AutoAdjustBounds", true);
    public final Setting<Boolean> autoFirework = bool("AutoFirework", true);
    public final Setting<Double> boundGap = num("BoundGap", 60.0, 20.0, 150.0);
    public final Setting<Double> velocityThreshold = num("VelocityThreshold", -0.05, -0.5, 1.0);
    public final Setting<Integer> fireworkCooldownTicks = num("CooldownTicks", 10, 0, 100);

    private double upperBound;
    private double lowerBound;
    private int fireworkCooldown;
    private boolean goingUp;
    private Object trackedLevel;

    public Pitch40() {
        super("Pitch40", "Maintains a pitch-40 elytra climb/dive cycle and automatically uses fireworks when needed.", Category.MOVEMENT);
    }

    @Override
    public void onEnable() {
        resetBounds();
        fireworkCooldown = 0;
        goingUp = true;
        trackedLevel = mc.level;
    }

    @Override
    public void onTick() {
        if (nullCheck()) return;
        if (trackedLevel != mc.level) {
            trackedLevel = mc.level;
            resetBounds();
        }

        if (!mc.player.isFallFlying()) return;

        if (fireworkCooldown > 0) fireworkCooldown--;

        if (autoBoundAdjust.getValue() && mc.player.getY() <= lowerBound - 10.0) {
            resetBounds();
            return;
        }

        if (controlPitch.getValue()) mc.player.setXRot(goingUp ? -40.0f : 40.0f);

        if (goingUp) {
            if (autoFirework.getValue()
                && mc.player.getDeltaMovement().y < velocityThreshold.getValue()
                && mc.player.getY() < upperBound
                && fireworkCooldown == 0
                && useFirework()) {
                fireworkCooldown = fireworkCooldownTicks.getValue();
            }

            if (mc.player.getDeltaMovement().y <= 0.0 || mc.player.getY() >= upperBound) {
                goingUp = false;
                if (autoBoundAdjust.getValue()) resetBoundsAtTop();
            }
        } else if (mc.player.getY() <= lowerBound || mc.player.getDeltaMovement().y > 0.05) {
            goingUp = true;
        }
    }

    private boolean useFirework() {
        if (mc.gameMode == null || mc.player.containerMenu != mc.player.inventoryMenu) return false;
        Result result = InventoryUtil.find(Items.FIREWORK_ROCKET, FULL_SCOPE);
        if (!result.found()) return false;

        if (result.type() == ResultType.OFFHAND) {
            mc.gameMode.useItem(mc.player, InteractionHand.OFF_HAND);
            return true;
        }

        if (result.type() == ResultType.HOTBAR && result.slot() == InventoryUtil.selected()) {
            mc.gameMode.useItem(mc.player, InteractionHand.MAIN_HAND);
            return true;
        }

        int containerSlot = result.slot() < 9 ? result.slot() + 36 : result.slot();
        int selected = InventoryUtil.selected();
        InventoryUtil.click(containerSlot, selected, ClickType.SWAP);
        try {
            mc.gameMode.useItem(mc.player, InteractionHand.MAIN_HAND);
        } finally {
            InventoryUtil.click(containerSlot, selected, ClickType.SWAP);
        }
        return true;
    }

    private void resetBounds() {
        if (mc.player == null) return;
        double half = boundGap.getValue() * 0.5;
        upperBound = mc.player.getY() + half;
        lowerBound = mc.player.getY() - half;
    }

    private void resetBoundsAtTop() {
        if (mc.player == null) return;
        upperBound = mc.player.getY();
        lowerBound = upperBound - boundGap.getValue();
    }

    public boolean isGoingUp() {
        return goingUp;
    }

    @Override
    public String getDisplayInfo() {
        return goingUp ? "Climb" : "Dive";
    }
}
