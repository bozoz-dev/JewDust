package dev.axziom.features.modules.world.searcharea;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import net.minecraft.client.Minecraft;
import net.minecraft.util.Mth;

import java.io.IOException;
import java.io.Reader;
import java.io.Writer;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;

public abstract class SearchAreaMode {
    protected static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();
    protected static final long AUTOSAVE_INTERVAL_NANOS = 600_000_000_000L;

    protected final SearchAreaModule searchArea;
    protected final Minecraft client;
    protected final SearchAreaModes type;
    protected long lastAutosaveNanos;

    protected SearchAreaMode(SearchAreaModule searchArea, SearchAreaModes type) {
        this.searchArea = searchArea;
        this.client = Minecraft.getInstance();
        this.type = type;
    }

    public abstract void onActivate();

    public abstract void onTick();

    public void onDeactivate() {
        setForward(false);
    }

    protected final boolean autosaveDue() {
        long now = System.nanoTime();
        if (now - lastAutosaveNanos < AUTOSAVE_INTERVAL_NANOS) return false;
        lastAutosaveNanos = now;
        return true;
    }

    protected final void resetAutosaveTimer() {
        lastAutosaveNanos = System.nanoTime();
    }

    protected final <T> T load(Class<T> dataClass) {
        Path file = searchArea.getSaveFile(type);
        if (file == null || !Files.isRegularFile(file)) return null;
        try (Reader reader = Files.newBufferedReader(file)) {
            return GSON.fromJson(reader, dataClass);
        } catch (Exception exception) {
            searchArea.message("Could not load " + type + " path data; starting a new path.");
            return null;
        }
    }

    protected final void save(PathData data, boolean goingToStart) {
        if (data == null) return;
        Path file = searchArea.getSaveFile(type);
        if (file == null) return;
        if (!goingToStart && client.player != null) {
            data.currentX = client.player.getBlockX();
            data.currentZ = client.player.getBlockZ();
        }
        try {
            Files.createDirectories(file.getParent());
            Path temporary = file.resolveSibling(file.getFileName() + ".tmp");
            try (Writer writer = Files.newBufferedWriter(temporary)) {
                GSON.toJson(data, writer);
            }
            try {
                Files.move(temporary, file, StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE);
            } catch (IOException ignored) {
                Files.move(temporary, file, StandardCopyOption.REPLACE_EXISTING);
            }
        } catch (IOException exception) {
            searchArea.message("Could not save " + type + " path data.");
        }
    }

    public static void clearSave(SearchAreaModule module, SearchAreaModes type) {
        Path file = module.getSaveFile(type);
        if (file == null) {
            module.message("Set a Save Name before clearing saved path data.");
            return;
        }
        try {
            module.message(Files.deleteIfExists(file) ? "Cleared the saved " + type + " path."
                    : "No saved " + type + " path was found.");
        } catch (IOException exception) {
            module.message("Could not clear the saved " + type + " path.");
        }
    }

    protected final void setForward(boolean pressed) {
        searchArea.setForwardPressed(pressed);
    }

    protected final void face(double targetX, double targetZ) {
        if (client.player == null) return;
        double deltaX = targetX - client.player.getX();
        double deltaZ = targetZ - client.player.getZ();
        client.player.setYRot(Mth.wrapDegrees((float) (Math.toDegrees(Math.atan2(deltaZ, deltaX)) - 90.0)));
    }

    protected final boolean isNear(double x, double z, double distance) {
        if (client.player == null) return false;
        double dx = client.player.getX() - x;
        double dz = client.player.getZ() - z;
        return dx * dx + dz * dz <= distance * distance;
    }

    protected final void stopHorizontalMovement() {
        if (client.player == null) return;
        client.player.setDeltaMovement(0.0, client.player.getDeltaMovement().y, 0.0);
    }

    protected final float wrapYaw(float yaw) {
        return Mth.wrapDegrees(yaw);
    }

    protected static class PathData {
        public int currentX;
        public int currentZ;
        public float yawDirection;
        public boolean mainPath;
    }
}
