package dev.axziom.features.modules.movement;

import dev.axziom.features.modules.Module;
import dev.axziom.features.settings.Setting;
import net.minecraft.util.Mth;

public final class YawLockModule extends Module {
    public final Setting<Boolean> silent = bool("Silent", false);

    private float lockedYaw;

    public YawLockModule() {
        super("YawLock", "Locks horizontal rotation.", Category.MOVEMENT);
    }

    @Override
    public void onEnable() {
        if (nullCheck()) {
            disable();
            return;
        }
        lockedYaw = mc.player.getYRot();
    }

    @Override
    public void onTick() {
        if (nullCheck() || silent.getValue()) return;
        mc.player.setYRot(lockedYaw);
    }

    public boolean blocksClientYaw() {
        return isEnabled() && !silent.getValue();
    }

    public boolean hasSilentServerYaw() {
        return isEnabled() && silent.getValue();
    }

    public float getLockedYaw() {
        return lockedYaw;
    }

    @Override
    public String getDisplayInfo() {
        return String.format("%.1f%s", Mth.wrapDegrees(lockedYaw), silent.getValue() ?" Silent" : "");
    }
}