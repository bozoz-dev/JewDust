package dev.axziom.features.modules.player;

import dev.axziom.features.modules.Module;
import dev.axziom.features.settings.Setting;
import net.minecraft.client.CameraType;
import net.minecraft.util.Mth;

public final class FreeLookModule extends Module {
    public final Setting<Boolean> togglePerspective = bool("TogglePerspective", true);

    private float cameraYaw;
    private float cameraPitch;
    private CameraType previousCameraType;

    public FreeLookModule() {
        super("FreeLook", "Lets the mouse rotate only the client camera without rotating the player.", Category.PLAYER);
    }

    @Override
    public void onEnable() {
        if (nullCheck()) {
            disable();
            return;
        }

        cameraYaw = mc.player.getYRot();
        cameraPitch = mc.player.getXRot();
        previousCameraType = mc.options.getCameraType();

        if (togglePerspective.getValue() && previousCameraType != CameraType.THIRD_PERSON_BACK) {
            mc.options.setCameraType(CameraType.THIRD_PERSON_BACK);
        }
    }

    @Override
    public void onDisable() {
        if (previousCameraType != null && togglePerspective.getValue()) {
            mc.options.setCameraType(previousCameraType);
        }
        previousCameraType = null;
    }

    public void changeCameraLook(double deltaX, double deltaY) {
        cameraYaw += (float) (deltaX * 0.15);
        cameraPitch = Mth.clamp(cameraPitch + (float) (deltaY * 0.15), -90.0f, 90.0f);
    }

    public boolean cameraMode() {
        return isEnabled();
    }

    public float getCameraYaw() {
        return cameraYaw;
    }

    public float getCameraPitch() {
        return cameraPitch;
    }
}