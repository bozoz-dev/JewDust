package dev.axziom.features.modules.world.searcharea.modes;

import dev.axziom.features.modules.world.searcharea.SearchAreaMode;
import dev.axziom.features.modules.world.searcharea.SearchAreaModes;
import dev.axziom.features.modules.world.searcharea.SearchAreaModule;

public final class Rectangle extends SearchAreaMode {
    private static final double START_DISTANCE = 5.0;
    private static final double TURN_DISTANCE = 2.0;

    private PathDataRectangle data;
    private boolean goingToStart;

    public Rectangle(SearchAreaModule searchArea) {
        super(searchArea, SearchAreaModes.RECTANGLE);
    }

    @Override
    public void onActivate() {
        resetAutosaveTimer();
        PathDataRectangle loaded = load(PathDataRectangle.class);
        if (loaded != null) {
            data = loaded;
            goingToStart = true;
            searchArea.message("Loaded the saved Rectangle path; returning to the last position.");
        } else {
            data = createNewData();
            goingToStart = true;
        }
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
            if (isNear(data.currentX + 0.5, data.currentZ + 0.5, START_DISTANCE)) {
                goingToStart = false;
                stopHorizontalMovement();
                printEstimate();
            } else {
                face(data.currentX + 0.5, data.currentZ + 0.5);
                setForward(true);
            }
            return;
        }
        if (data.shiftingRow) tickRowShift();
        else tickMainRow();
    }

    private void tickMainRow() {
        int rowEndX = data.xDirection > 0 ? Math.max(data.startX, data.endX) : Math.min(data.startX, data.endX);
        data.yawDirection = data.xDirection > 0 ? -90.0f : 90.0f;
        client.player.setYRot(data.yawDirection);
        setForward(true);
        boolean reached = data.xDirection > 0 ? client.player.getX() >= rowEndX - TURN_DISTANCE
                : client.player.getX() <= rowEndX + TURN_DISTANCE;
        if (!reached) return;
        stopHorizontalMovement();
        if (hasReachedFinalRow()) {
            setForward(false);
            searchArea.completeRectangle();
            return;
        }
        int zDirection = Integer.compare(data.endZ, data.currentRowZ);
        int gap = 16 * searchArea.getRowGapChunks();
        data.nextRowZ = moveToward(data.currentRowZ, data.endZ, gap * zDirection);
        data.shiftingRow = true;
        data.mainPath = false;
        data.yawDirection = zDirection >= 0 ? 0.0f : 180.0f;
    }

    private void tickRowShift() {
        int direction = Integer.compare(data.nextRowZ, data.currentRowZ);
        data.yawDirection = direction >= 0 ? 0.0f : 180.0f;
        client.player.setYRot(data.yawDirection);
        setForward(true);
        boolean reached = direction >= 0 ? client.player.getZ() >= data.nextRowZ - TURN_DISTANCE
                : client.player.getZ() <= data.nextRowZ + TURN_DISTANCE;
        if (!reached) return;
        stopHorizontalMovement();
        data.currentRowZ = data.nextRowZ;
        data.xDirection *= -1;
        data.shiftingRow = false;
        data.mainPath = true;
    }

    private boolean hasReachedFinalRow() {
        int direction = Integer.compare(data.endZ, data.startZ);
        return direction == 0 || (direction > 0 ? data.currentRowZ >= data.endZ : data.currentRowZ <= data.endZ);
    }

    private PathDataRectangle createNewData() {
        PathDataRectangle result = new PathDataRectangle();
        result.startX = searchArea.resolveStartX();
        result.startZ = searchArea.resolveStartZ();
        result.endX = searchArea.getEndX();
        result.endZ = searchArea.getEndZ();
        result.currentX = result.startX;
        result.currentZ = result.startZ;
        result.currentRowZ = result.startZ;
        result.nextRowZ = result.startZ;
        result.xDirection = result.endX >= result.startX ? 1 : -1;
        result.yawDirection = result.xDirection > 0 ? -90.0f : 90.0f;
        result.mainPath = true;
        return result;
    }

    private void printEstimate() {
        double rowWidth = Math.abs((double) data.endX - data.startX);
        double zDistance = Math.abs((double) data.endZ - data.currentRowZ);
        int gap = 16 * searchArea.getRowGapChunks();
        long rows = Math.max(1L, (long) Math.ceil(zDistance / gap) + 1L);
        long seconds = (long) Math.ceil((rows * rowWidth + Math.max(0L, rows - 1L) * gap) / 10.0);
        searchArea.message(String.format("Estimated completion: %02d:%02d:%02d at roughly 10 blocks/second.",
                seconds / 3600L, seconds % 3600L / 60L, seconds % 60L));
    }

    private static int moveToward(int current, int target, int delta) {
        int proposed = current + delta;
        if (delta > 0) return Math.min(proposed, target);
        if (delta < 0) return Math.max(proposed, target);
        return target;
    }

    public static final class PathDataRectangle extends PathData {
        public int startX;
        public int startZ;
        public int endX;
        public int endZ;
        public int currentRowZ;
        public int nextRowZ;
        public int xDirection;
        public boolean shiftingRow;
    }
}
