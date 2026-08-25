package dev.axziom.features.modules.client;

import dev.axziom.JewDust;
import dev.axziom.event.impl.ClientEvent;
import dev.axziom.event.system.Subscribe;
import dev.axziom.features.commands.Command;
import dev.axziom.features.gui.JewDustGui;
import dev.axziom.features.modules.Module;
import dev.axziom.features.settings.Bind;
import dev.axziom.features.settings.Setting;
import org.lwjgl.glfw.GLFW;

import java.awt.*;

public class ClickGuiModule extends Module {
    private static ClickGuiModule INSTANCE;

    public Setting<String>  prefix           = str("Prefix", ";");
    public Setting<Theme>   theme            = mode("Theme", Theme.JEWDUST);
    public Setting<Color>   customColor      = color("Custom Color", 120, 170, 210, 220);
    public Setting<Boolean> smooth           = bool("Smooth", true);
    public Setting<Integer> rainbowHue       = num("Delay", 240, 0, 600);
    public Setting<Float>   rainbowBrightness = num("Brightness", 200.0f, 1.0f, 255.0f);
    public Setting<Float>   rainbowSaturation = num("Saturation", 140.0f, 1.0f, 255.0f);

    public Setting<Boolean> clickGuiFont = bool("ClickGUI Font", false);
    public Setting<Boolean> hudFont      = bool("HUD Font", false);
    public Setting<String>  fontName     = str("Font Name", "");

    private static final Color CAT_COMBAT   = new Color(196,  88,  90);
    private static final Color CAT_WORLD    = new Color(118, 168, 118);
    private static final Color CAT_RENDER   = new Color( 98, 166, 200);
    private static final Color CAT_MOVEMENT = new Color(212, 142,  78);
    private static final Color CAT_PLAYER   = new Color(202, 180,  92);
    private static final Color CAT_FUNNY    = new Color(198, 124, 178);
    private static final Color CAT_CLIENT   = new Color( 70,  75,  82);
    private static final Color CAT_HUD      = new Color( 96, 142, 200);

    private static final Color JEWDUST_ACCENT = new Color(145, 79, 220, 255);
    private static final Color JEWDUST_MODULE = new Color(145, 79, 220, 255);

    public Color categoryAccent(Module.Category cat) {
        return categoryAccent(cat, 0f);
    }

    public Color categoryAccent(Module.Category cat, float yOffset) {
        Theme t = theme.getValue();
        if (t == Theme.RAINBOW)  return rainbowAt(yOffset);
        if (t == Theme.CUSTOM)   return customColor.getValue();
        if (t == Theme.JEWDUST) return JEWDUST_ACCENT;
        if (cat == null) return CAT_CLIENT;
        switch (cat) {
            case COMBAT:   return CAT_COMBAT;
            case WORLD:    return CAT_WORLD;
            case RENDER:   return CAT_RENDER;
            case MOVEMENT: return CAT_MOVEMENT;
            case PLAYER:   return CAT_PLAYER;
            case FUNNY:    return CAT_FUNNY;
            case CLIENT:   return CAT_CLIENT;
            case HUD:      return CAT_HUD;
            default:       return CAT_CLIENT;
        }
    }

    public Color moduleAccent(Module.Category cat, float yOffset) {
        if (theme.getValue() == Theme.JEWDUST) return JEWDUST_MODULE;
        return categoryAccent(cat, yOffset);
    }

    public Color chatAccent() {
        Theme t = theme.getValue();
        if (t == Theme.RAINBOW)  return rainbowAt(0f);
        if (t == Theme.CUSTOM)   return customColor.getValue();
        if (t == Theme.JEWDUST) return JEWDUST_ACCENT;
        return Color.WHITE;
    }

    public Color rainbowAt(float yOffset) {
        return dev.axziom.util.ColorUtil.rainbow((int)(yOffset / 10f * rainbowHue.getValue()));
    }

    public float getExpandSpeed() {
        return smooth.getValue() ? 0.5f : 1f;
    }

    public ClickGuiModule() {
        super("ClickGui", "Opens the ClickGui", Module.Category.CLIENT);

        this.bind.setValue(new Bind(GLFW.GLFW_KEY_P));
        this.bindMode.setVisibility(v -> false);

        rainbowHue.setVisibility(v -> theme.getValue() == Theme.RAINBOW);
        rainbowBrightness.setVisibility(v -> theme.getValue() == Theme.RAINBOW);
        rainbowSaturation.setVisibility(v -> theme.getValue() == Theme.RAINBOW);
        customColor.setVisibility(v -> theme.getValue() == Theme.CUSTOM);
        fontName.setVisibility(v -> clickGuiFont.getValue() || hudFont.getValue());

        INSTANCE = this;
    }

    @Subscribe
    public void onSettingChange(ClientEvent event) {
        if (event.getType() == ClientEvent.Type.SETTING_UPDATE
                && event.getSetting().getFeature().equals(this)) {
            if (event.getSetting().equals(this.prefix)) {
                JewDust.commandManager.setCommandPrefix(this.prefix.getPlannedValue());
                Command.sendMessage("Prefix set to {global} %s",
                        JewDust.commandManager.getCommandPrefix());
            }
            if (event.getSetting().equals(this.clickGuiFont)
                    || event.getSetting().equals(this.hudFont)
                    || event.getSetting().equals(this.fontName)) {
                dev.axziom.util.render.font.Fonts.markDirty();
            }
        }
    }

    @Override
    public void onEnable() {
        if (nullCheck()) return;
        mc.setScreen(JewDustGui.getClickGui());
    }

    @Override
    public void onLoad() {
        Color fixedAccent = new Color(145, 79, 220, 255);
        JewDust.colorManager.register("ui",          () -> fixedAccent);
        JewDust.colorManager.register("chat",        this::chatAccent);
        JewDust.colorManager.register("chatBracket", this::chatAccent);
        JewDust.commandManager.setCommandPrefix(this.prefix.getValue());
    }

    @Override
    public void onTick() {
        if (!(mc.screen instanceof JewDustGui)) {
            this.disable();
        }
    }

    public static ClickGuiModule getInstance() {
        return INSTANCE;
    }

    public enum Theme {
        JEWDUST, COLORS, CUSTOM, RAINBOW
    }
}
