package dev.axziom.features.modules.world.searcharea.modes;

import dev.axziom.features.modules.world.searcharea.SearchAreaMode;
import dev.axziom.features.modules.world.searcharea.SearchAreaModes;
import dev.axziom.features.modules.world.searcharea.SearchAreaModule;

public final class Spiral extends SearchAreaMode {
    private PathDataSpiral data;
    private boolean goingToStart;

    public Spiral(SearchAreaModule searchArea) {
        super(searchArea, SearchAreaModes.SPIRAL);
    }

    @Override
    public void onActivate() {
        resetAutosaveTimer();
        PathDataSpiral loaded = load(PathDataSpiral.class);
        if (loaded != null) {
            data = loaded;
            goingToStart = true;
            searchArea.message("Loaded the saved Spiral path; returning to the last position.");
            return;
        }
        if (client.player == null) {
            searchArea.disableFromMode("A player is required to start a Spiral path.");
            return;
        }
        int x = client.player.getBlockX();
        int z = client.player.getBlockZ();
        data = new PathDataSpiral();
        data.anchorX = x;
        data.anchorZ = z;
        data.currentX = x;
        data.currentZ = z;
        data.yawDirection = -90.0f;
        data.mainPath = true;
        goingToStart = false;
    }

    @Override
    public void onDeactivate() {
        super.onDeactivate();
        save(data, goingToStart);
    }

    @Override
    public void onTick() {
        if (client.player == null || data == null) return;
        if (autosaveDue()) save(data, goingToStart);
        if (goingToStart) {
            if (isNear(data.currentX + 0.5, data.currentZ + 0.5, 5.0)) {
                goingToStart = false;
                stopHorizontalMovement();
            } else {
                face(data.currentX + 0.5, data.currentZ + 0.5);
                setForward(true);
            }
            return;
        }

        setForward(true);
        client.player.setYRot(data.yawDirection);
        int gap = 16 * searchArea.getRowGapChunks();
        if (data.mainPath && Math.abs(client.player.getX() - data.anchorX) >= gap + data.spiralWidth) {
            data.yawDirection = wrapYaw(data.yawDirection + 90.0f);
            data.anchorX = client.player.getBlockX();
            data.spiralWidth += gap;
            data.mainPath = false;
            stopHorizontalMovement();
        } else if (!data.mainPath && Math.abs(client.player.getZ() - data.anchorZ) >= gap + data.spiralHeight) {
            data.yawDirection = wrapYaw(data.yawDirection + 90.0f);
            data.anchorZ = client.player.getBlockZ();
            data.spiralHeight += gap;
            data.mainPath = true;
            stopHorizontalMovement();
        }
    }

    public static final class PathDataSpiral extends PathData {
        public int anchorX;
        public int anchorZ;
        public int spiralWidth;
        public int spiralHeight;
    }
}
