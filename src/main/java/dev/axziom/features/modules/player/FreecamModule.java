package dev.axziom.features.modules.player;

import dev.axziom.features.modules.Module;
import dev.axziom.features.settings.Setting;
import net.minecraft.client.CameraType;
import net.minecraft.util.Mth;
import net.minecraft.world.phys.Vec3;

public final class FreecamModule extends Module {
    public final Setting<Double> speed = num("Speed", 1.0, 0.05, 10.0);
    public final Setting<Double> sprintMultiplier = num("SprintMultiplier", 2.0, 1.0, 10.0);
    public final Setting<Boolean> staySneaking = bool("StaySneaking", true);
    public final Setting<Boolean> renderHands = bool("RenderHands", true);

    private Vec3 position = Vec3.ZERO;
    private Vec3 previousPosition = Vec3.ZERO;
    private float yaw;
    private float pitch;
    private float previousYaw;
    private float previousPitch;
    private CameraType previousCameraType;
    private boolean wasSneaking;

    public FreecamModule() {
        super("Freecam", "Lets the camera fly away from the player without moving the player.", Category.RENDER);
    }

    @Override
    public void onEnable() {
        if (nullCheck() || !mc.gameRenderer.getMainCamera().isInitialized()) {
            disable();
            return;
        }

        position = mc.gameRenderer.getMainCamera().position();
        previousPosition = position;
        yaw = mc.player.getYRot();
        pitch = mc.player.getXRot();
        previousCameraType = mc.options.getCameraType();

        if (previousCameraType == CameraType.THIRD_PERSON_FRONT) {
            yaw += 180.0f;
            pitch *= -1.0f;
        }

        previousYaw = yaw;
        previousPitch = pitch;
        wasSneaking = mc.options.keyShift.isDown();
        mc.options.setCameraType(CameraType.FIRST_PERSON);
        if (mc.levelRenderer != null) mc.levelRenderer.allChanged();
    }

    @Override
    public void onDisable() {
        if (previousCameraType != null) mc.options.setCameraType(previousCameraType);
        previousCameraType = null;
        wasSneaking = false;
        if (mc.levelRenderer != null) mc.levelRenderer.allChanged();
    }

    @Override
    public void onTick() {
        if (nullCheck()) return;
        if (mc.options.getCameraType() != CameraType.FIRST_PERSON) {
            mc.options.setCameraType(CameraType.FIRST_PERSON);
        }

        previousPosition = position;
        if (mc.screen != null) return;

        double movementSpeed = speed.getValue();
        if (mc.options.keySprint.isDown()) movementSpeed *= sprintMultiplier.getValue();

        double forwardInput = axis(mc.options.keyUp.isDown(), mc.options.keyDown.isDown());
        double sideInput = axis(mc.options.keyRight.isDown(), mc.options.keyLeft.isDown());
        double verticalInput = axis(mc.options.keyJump.isDown(), mc.options.keyShift.isDown());

        double horizontalLength = Math.hypot(forwardInput, sideInput);
        if (horizontalLength > 1.0) {
            forwardInput /= horizontalLength;
            sideInput /= horizontalLength;
        }

        Vec3 forward = directionFromYaw(yaw);
        Vec3 right = directionFromYaw(yaw + 90.0f);
        Vec3 movement = forward.scale(forwardInput)
                .add(right.scale(sideInput))
                .add(0.0, verticalInput, 0.0)
                .scale(movementSpeed * 0.5);

        position = position.add(movement);
    }

    public void changeLookDirection(double deltaX, double deltaY) {
        previousYaw = yaw;
        previousPitch = pitch;
        yaw += (float) (deltaX * 0.15);
        pitch = Mth.clamp(pitch + (float) (deltaY * 0.15), -90.0f, 90.0f);
    }

    public boolean shouldStaySneaking() {
        return isEnabled() && staySneaking.getValue() && wasSneaking;
    }

    public boolean shouldRenderHands() {
        return !isEnabled() || renderHands.getValue();
    }

    public double getX(float partialTick) {
        return Mth.lerp(partialTick, previousPosition.x, position.x);
    }

    public double getY(float partialTick) {
        return Mth.lerp(partialTick, previousPosition.y, position.y);
    }

    public double getZ(float partialTick) {
        return Mth.lerp(partialTick, previousPosition.z, position.z);
    }

    public float getYaw(float partialTick) {
        return Mth.rotLerp(partialTick, previousYaw, yaw);
    }

    public float getPitch(float partialTick) {
        return Mth.lerp(partialTick, previousPitch, pitch);
    }

    private static double axis(boolean positive, boolean negative) {
        if (positive == negative) return 0.0;
        return positive ? 1.0 : -1.0;
    }

    private static Vec3 directionFromYaw(float yaw) {
        double radians = Math.toRadians(yaw);
        return new Vec3(-Math.sin(radians), 0.0, Math.cos(radians));
    }
}
