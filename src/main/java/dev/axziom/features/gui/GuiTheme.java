package dev.axziom.features.gui;

import dev.axziom.features.modules.Module;
import dev.axziom.features.modules.client.ClickGuiModule;

import java.awt.Color;

public final class GuiTheme {
    private GuiTheme() {}

    /*
     * Compact, column-based ClickGUI sizing. These are the first values to edit
     * if you want the whole GUI to be wider/taller without touching render code.
     */
    public static final int PANEL_WIDTH    = 110;
    public static final int HEADER_HEIGHT  = 15;
    public static final int PANEL_SPACING  = 4;
    public static final int SCREEN_MARGIN  = 5;
    public static final int MODULE_HEIGHT  = 13;
    public static final int SETTING_HEIGHT = 12;
    public static final int SETTING_INDENT = 6;
    public static final int SETTING_RIGHT_PAD = 2;

    /*
     * The purple is the UI accent only. StorageESP and every other semantic
     * module colour still keep their own defaults.
     */
    public static final int HEADER_ALPHA       = 205;
    public static final int ACTIVE_ROW_ALPHA   = 145;
    public static final int HOVER_ROW_ALPHA    = 55;
    public static final int SETTING_ON_ALPHA   = 120;

    public static final int BODY_BG          = new Color(35, 18, 37, 188).getRGB();

    public static final int SETTINGS_TRAY_BG = new Color(24, 12, 27, 215).getRGB();

    public static final int SCREEN_DIM       = new Color(26, 6, 28, 58).getRGB();

    public static final int SEPARATOR        = new Color(255, 255, 255, 28).getRGB();
    public static final int OUTLINE          = new Color(9, 2, 10, 175).getRGB();
    public static final int OUTLINE_INNER    = new Color(0, 0, 0, 95).getRGB();
    public static final int HIGHLIGHT_TOP    = new Color(255, 255, 255, 34).getRGB();

    public static final int TEXT_HEADER         = 0xFFFFFFFF;
    public static final int TEXT_MODULE_ON      = 0xFFFFFFFF;
    public static final int TEXT_MODULE_OFF     = 0xFFD5CED8;
    public static final int TEXT_SETTING        = 0xFFE8E2EA;
    public static final int TEXT_SETTING_VALUE  = 0xFFBFB3C4;

    public static final int SLIDER_TRACK        = new Color(255, 255, 255, 36).getRGB();
    public static final int SLIDER_TRACK_HOVER  = new Color(255, 255, 255, 60).getRGB();
    public static final int CHECKBOX_OFF_BG     = new Color(255, 255, 255, 28).getRGB();
    public static final int CHECKBOX_BORDER     = new Color(0, 0, 0, 140).getRGB();

    public static Color categoryColor(Module.Category category) {
        return categoryColor(category, 0f);
    }

    public static Color categoryColor(Module.Category category, float yOffset) {
        ClickGuiModule m = ClickGuiModule.getInstance();
        return m == null ? new Color(145, 79, 220) : m.categoryAccent(category, yOffset);
    }

    public static Color moduleColor(Module.Category category, float yOffset) {
        ClickGuiModule m = ClickGuiModule.getInstance();
        return m == null ? new Color(145, 79, 220) : m.moduleAccent(category, yOffset);
    }

    public static Color darken(Color c, float f) {
        return new Color(
                (int) Math.max(0, c.getRed() * f),
                (int) Math.max(0, c.getGreen() * f),
                (int) Math.max(0, c.getBlue() * f),
                c.getAlpha());
    }

    public static Color brighten(Color c, float amount) {
        return new Color(
                (int) Math.min(255, c.getRed() + (255 - c.getRed()) * amount),
                (int) Math.min(255, c.getGreen() + (255 - c.getGreen()) * amount),
                (int) Math.min(255, c.getBlue() + (255 - c.getBlue()) * amount),
                c.getAlpha());
    }

    public static Color withAlpha(Color c, int alpha) {
        return new Color(c.getRed(), c.getGreen(), c.getBlue(), alpha);
    }
}
