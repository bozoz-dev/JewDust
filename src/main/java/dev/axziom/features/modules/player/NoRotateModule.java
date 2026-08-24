package dev.axziom.features.modules.player;

import dev.axziom.JewDust;
import dev.axziom.features.modules.Module;

public class NoRotateModule extends Module {

    public NoRotateModule() {
        super("NoRotate", "Ignores server-forced rotations.", Category.PLAYER);
    }

    public static boolean isActive() {
        if (JewDust.moduleManager == null) return false;
        NoRotateModule module = JewDust.moduleManager.getModuleByClass(NoRotateModule.class);
        return module != null && module.isEnabled();
    }
}
