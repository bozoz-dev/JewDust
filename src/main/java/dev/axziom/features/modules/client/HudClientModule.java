package dev.axziom.features.modules.client;

import dev.axziom.event.impl.render.Render2DEvent;
import dev.axziom.features.modules.Module;
import dev.axziom.features.modules.hud.ActiveModulesHudModule;
import dev.axziom.features.modules.hud.ArmorHudModule;
import dev.axziom.features.modules.hud.CoordinatesHudModule;
import dev.axziom.features.modules.hud.CountsHudModule;
import dev.axziom.features.modules.hud.NotifierHudModule;
import dev.axziom.features.modules.hud.PingHudModule;
import dev.axziom.features.modules.hud.RadarHudModule;
import dev.axziom.features.modules.hud.SpeedHudModule;
import dev.axziom.features.modules.hud.TotemsHudModule;
import dev.axziom.features.settings.Setting;

import java.awt.Color;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

public class HudClientModule extends Module {

    private static final List<Class<? extends HudModule>> BOTTOM_STACK = List.of(
            CoordinatesHudModule.class,
            PingHudModule.class,
            RadarHudModule.class,
            ActiveModulesHudModule.class);

    private final Map<HudModule, Setting<Boolean>> elements = new LinkedHashMap<>();

    public final Setting<Color> radarEnemyColor  = color("Radar Enemy Color", 145, 79, 220, 255).setPage("Colors");
    public final Setting<Color> radarFriendColor = color("Radar Friend Color", 145, 79, 220, 255).setPage("Colors");
    public final Setting<Color> radarSelfColor   = color("Radar Self Color", 145, 79, 220, 255).setPage("Colors");
    public final Setting<Color> activeModuleColor = color("Active Module Color", 145, 79, 220, 255).setPage("Colors");

    public Setting<HudPosition> coordinatesPosition;
    public Setting<HudPosition> activeModulesPosition;

    public HudClientModule() {
        super("Hud", "Static-position HUD elements", Category.CLIENT);
        register(new TotemsHudModule(), true);
        register(new ArmorHudModule(), true);
        register(new CountsHudModule(), true);
        register(new CoordinatesHudModule(), true);
        coordinatesPosition = mode("Coordinates Position", HudPosition.BOTTOM_RIGHT).setPage("Elements");
        register(new PingHudModule(), true);
        register(new RadarHudModule(), true);
        register(new ActiveModulesHudModule(), true);
        activeModulesPosition = mode("ActiveModules Position", HudPosition.CENTER_RIGHT).setPage("Elements");
        register(new NotifierHudModule(), true);
        register(new SpeedHudModule(), false);
    }

    private void register(HudModule element, boolean defaultOn) {
        elements.put(element, bool(element.getName(), defaultOn).setPage("Elements"));
    }

    public HudPosition positionOf(HudModule element) {
        if (element instanceof CoordinatesHudModule) return coordinatesPosition.getValue();
        if (element instanceof ActiveModulesHudModule) return activeModulesPosition.getValue();
        return HudPosition.BOTTOM_RIGHT;
    }

    private int lineCount(HudModule element) {
        if (element instanceof RadarHudModule radar) return radar.renderedLineCount();
        if (element instanceof ActiveModulesHudModule active) return active.getEntries().size();
        return 1;
    }

    public int linesBelow(HudModule element) {
        HudPosition anchor = positionOf(element);
        if (!anchor.isBottom()) return 0;

        int lines = 0;
        for (Class<? extends HudModule> type : BOTTOM_STACK) {
            if (type.isInstance(element)) break;
            HudModule other = getElement(type);
            if (other == null || !isElementEnabled(type)) continue;
            if (positionOf(other) != anchor) continue;
            lines += lineCount(other);
        }
        return lines;
    }

    @SuppressWarnings("unchecked")
    public <T extends HudModule> T getElement(Class<T> type) {
        for (HudModule element : elements.keySet()) {
            if (type.isInstance(element)) return (T) element;
        }
        return null;
    }

    public boolean isElementEnabled(Class<? extends HudModule> type) {
        for (Map.Entry<HudModule, Setting<Boolean>> entry : elements.entrySet()) {
            if (type.isInstance(entry.getKey())) return entry.getValue().getValue();
        }
        return false;
    }

    @Override
    public void onRender2D(Render2DEvent event) {
        if (nullCheck()) return;
        if (mc.options.hideGui) return;
        dev.axziom.util.render.font.Fonts.beginHudPass();
        try {
            for (Map.Entry<HudModule, Setting<Boolean>> entry : elements.entrySet()) {
                if (!entry.getValue().getValue()) continue;
                entry.getKey().render(event);
            }
        } finally {
            dev.axziom.util.render.font.Fonts.endHudPass();
        }
    }
}
