package dev.axziom.features.modules.render;

import dev.axziom.features.modules.Module;
import dev.axziom.features.settings.Setting;

public final class SwingModule extends Module {
    public enum SwingAnimation {
        VANILLA,
        ONE_TWELVE,
        ONE_EIGHT
    }

    public enum EatingAnimation {
        VANILLA,
        STATIC
    }

    public final Setting<SwingAnimation> swingAnimation = mode("Animation", SwingAnimation.VANILLA)
            .setPage("Swing");
    public final Setting<Float> speed = num("Speed", 1.0f, 0.1f, 3.0f)
            .setPage("Swing");
    public final Setting<EatingAnimation> eatingAnimation = mode("Animation", EatingAnimation.VANILLA)
            .setPage("Eating");

    public SwingModule() {
        super("Swing", "Changes first-person swing and eating animations without changing mining speed.", Category.RENDER);
    }

    public boolean usesStaticEating() {
        return isEnabled() && eatingAnimation.getValue() == EatingAnimation.STATIC;
    }
}
