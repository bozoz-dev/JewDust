package dev.axziom.features.modules.world.searcharea;

import dev.axziom.features.modules.Module;
import dev.axziom.features.modules.world.searcharea.modes.Rectangle;
import dev.axziom.features.modules.world.searcharea.modes.Spiral;
import dev.axziom.features.settings.Setting;
import net.minecraft.network.chat.Component;

import java.nio.file.Path;

public final class SearchAreaModule extends Module {
    public final Setting<SearchAreaModes> mode = mode("Mode", SearchAreaModes.SPIRAL);
    public final Setting<Double> rowGap = num("PathGap", 12.0, 1.0, 32.0);
    public final Setting<Double> startX = num("StartX", 0.0, -30_000_000.0, 30_000_000.0).setPage("Rectangle");
    public final Setting<Double> startZ = num("StartZ", 0.0, -30_000_000.0, 30_000_000.0).setPage("Rectangle");
    public final Setting<Double> endX = num("EndX", 0.0, -30_000_000.0, 30_000_000.0).setPage("Rectangle");
    public final Setting<Double> endZ = num("EndZ", 0.0, -30_000_000.0, 30_000_000.0).setPage("Rectangle");
    public final Setting<Boolean> disconnectOnCompletion = bool("DisconnectOnCompletion", false).setPage("Rectangle");
    public final Setting<String> saveName = str("SaveName", "").setPage("Saving");
    public final Setting<Boolean> clearCurrentSave = bool("ClearCurrentSave", false).setPage("Saving");
    public final Setting<Boolean> clearAllSaves = bool("ClearAllSaves", false).setPage("Saving");

    private SearchAreaMode currentMode;
    private SearchAreaModes currentModeType;

    public SearchAreaModule() {
        super("SearchArea", "Drives a rectangle path or an expanding spiral for systematic chunk searches.", Category.WORLD);
    }

    @Override
    public void onEnable() {
        if (nullCheck()) {
            message("Join a world before enabling SearchArea.");
            disable();
            return;
        }
        switchMode(mode.getValue(), false);
        currentMode.onActivate();
    }

    @Override
    public void onDisable() {
        if (currentMode != null) currentMode.onDeactivate();
        setForwardPressed(false);
    }

    @Override
    public void onTick() {
        if (clearCurrentSave.getValue()) {
            clearCurrentSave.setValue(false);
            SearchAreaMode.clearSave(this, mode.getValue());
        }
        if (clearAllSaves.getValue()) {
            clearAllSaves.setValue(false);
            for (SearchAreaModes value : SearchAreaModes.values()) SearchAreaMode.clearSave(this, value);
        }
        if (nullCheck()) {
            setForwardPressed(false);
            return;
        }
        if (mode.getValue() != currentModeType) switchMode(mode.getValue(), true);
        if (currentMode != null) currentMode.onTick();
    }

    private void switchMode(SearchAreaModes type, boolean activate) {
        if (currentMode != null) currentMode.onDeactivate();
        currentModeType = type;
        currentMode = type == SearchAreaModes.RECTANGLE ? new Rectangle(this) : new Spiral(this);
        if (activate && isEnabled()) currentMode.onActivate();
    }

    public int getRowGapChunks() {
        return Math.max(1, (int) Math.round(rowGap.getValue()));
    }

    public int resolveStartX() {
        if (startX.getValue() == 0.0 && startZ.getValue() == 0.0 && mc.player != null) return mc.player.getBlockX();
        return (int) Math.round(startX.getValue());
    }

    public int resolveStartZ() {
        if (startX.getValue() == 0.0 && startZ.getValue() == 0.0 && mc.player != null) return mc.player.getBlockZ();
        return (int) Math.round(startZ.getValue());
    }

    public int getEndX() {
        return (int) Math.round(endX.getValue());
    }

    public int getEndZ() {
        return (int) Math.round(endZ.getValue());
    }

    public Path getSaveFile(SearchAreaModes type) {
        String name = sanitizeSaveName(saveName.getValue());
        if (name.isEmpty()) return null;
        return Path.of(System.getProperty("user.dir"), "config", "jewdust", "search-area", name,
                type.name().toLowerCase() + ".json");
    }

    public void completeRectangle() {
        boolean disconnect = disconnectOnCompletion.getValue();
        message("Rectangle path complete.");
        disable();
        if (disconnect && mc.level != null) mc.disconnectFromWorld(Component.literal("[SearchArea] Rectangle path complete."));
    }

    public void disableFromMode(String reason) {
        message(reason);
        disable();
    }

    public void message(String text) {
        if (mc.player != null) mc.player.displayClientMessage(Component.literal("[SearchArea] " + text), false);
    }

    public void setForwardPressed(boolean pressed) {
        mc.options.keyUp.setDown(pressed);
    }

    private static String sanitizeSaveName(String value) {
        if (value == null || value.isBlank()) return "";
        String sanitized = value.trim().replaceAll("[^A-Za-z0-9._-]", "_");
        return sanitized.equals(".") || sanitized.equals("..") ? "_" : sanitized;
    }
}
