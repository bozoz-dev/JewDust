package dev.axziom.features.modules.movement;

import dev.axziom.JewDust;
import dev.axziom.features.commands.Command;
import dev.axziom.features.modules.Module;
import dev.axziom.features.modules.player.FreeLookModule;
import dev.axziom.features.settings.Setting;
import dev.axziom.mixin.entity.FireworkRocketEntityAccessor;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.projectile.FireworkRocketEntity;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;

public final class RocketBoost extends Module {
    private static final double EPSILON = 1.0e-6;
    private static final double ANTI_TICK_SKIPPING = 0.05;

    public final Setting<Double> boostSpeed = num("BoostSpeed", 1.7, 0.1, 10.0);
    public final Setting<Boolean> rescale = bool("GrimRescale", true);
    public final Setting<Double> rescaleAmount = num("RescaleAmount", 1.65, 0.05, 3.0);
    public final Setting<Double> upPitch = num("UpPitch", -45.0, -90.0, 90.0).setPage("Controls");
    public final Setting<Double> downPitch = num("DownPitch", 45.0, -90.0, 90.0).setPage("Controls");
    public final Setting<Boolean> debug = bool("Debug", false).setPage("Safety");
    public final Setting<Boolean> wallCheck = bool("WallCheck", false).setPage("Safety");
    public final Setting<Integer> lookahead = num("Lookahead", 2, 1, 10).setPage("Safety");
    public final Setting<Boolean> chunkCheck = bool("ChunkCheck", false).setPage("Safety");
    public final Setting<Boolean> pauseInFluid = bool("PauseInFluid", false).setPage("Safety");

    private Vec3 lastKnownClientVelocity = Vec3.ZERO;
    private Vec3 currentFireworkVelocity;
    private Vec3 nextTravelOverride;
    private Vec3 computedSafeOverride;
    private float lastPitch;
    private float lastYaw;
    private boolean lastInWater;
    private boolean lastInLava;
    private boolean stateInitialized;
    private boolean prepared;
    private int debugTicks;

    public RocketBoost() {
        super("RocketBoost", "Boosts attached-firework elytra velocity with silent Space/Shift pitch control.", Category.MOVEMENT);
    }

    @Override
    public void onEnable() {
        resetState();
    }

    @Override
    public void onDisable() {
        resetState();
    }

    @Override
    public void onTick() {
        currentFireworkVelocity = null;
        nextTravelOverride = null;
        computedSafeOverride = null;
        prepared = false;

        if (nullCheck()) {
            stateInitialized = false;
            return;
        }

        Vec3 velocityBeforeBoost = mc.player.getDeltaMovement();
        float controlledYaw = getControlledYaw();
        float controlledPitch = getControlledPitch();

        if (mc.player.isFallFlying()
                && hasAttachedFirework()
                && (!pauseInFluid.getValue() || (!mc.player.isInWater() && !mc.player.isInLava()))) {
            Vec3 look = directionFromRotation(controlledPitch, controlledYaw).normalize();
            Vec3 requested = look.scale(boostSpeed.getValue());

            if (rescale.getValue()) computeSafeOverride(requested, look);
            Vec3 output = computedSafeOverride != null ? computedSafeOverride : requested;

            if (finite(output) && passesSafetyChecks(output)) {
                mc.player.setDeltaMovement(requested);
                currentFireworkVelocity = output;
                nextTravelOverride = computedSafeOverride;
                prepared = true;
            }
        }

        lastKnownClientVelocity = velocityBeforeBoost;
        lastPitch = controlledPitch;
        lastYaw = controlledYaw;
        lastInWater = mc.player.isInWater();
        lastInLava = mc.player.isInLava();
        stateInitialized = true;
        if (debugTicks > 0) debugTicks--;
    }

    public Vec3 overrideFireworkVelocity(Vec3 vanilla) {
        if (!isEnabled() || !prepared || currentFireworkVelocity == null) return vanilla;

        if (debug.getValue() && debugTicks <= 0) {
            Command.sendMessage("RocketBoost %.3f -> %.3f (%.3f, %.3f, %.3f)", vanilla.length(),
                    currentFireworkVelocity.length(), currentFireworkVelocity.x,
                    currentFireworkVelocity.y, currentFireworkVelocity.z);
            debugTicks = 20;
        }
        return currentFireworkVelocity;
    }

    public Vec3 consumeTravelOverride() {
        Vec3 result = nextTravelOverride;
        nextTravelOverride = null;
        return result;
    }

    public boolean hasSilentPitchOverride() {
        if (!isEnabled() || nullCheck() || !mc.player.isFallFlying()) return false;
        return mc.options.keyJump.isDown() != mc.options.keyShift.isDown();
    }

    public float getControlledPitch() {
        if (nullCheck()) return 0.0f;
        boolean up = mc.options.keyJump.isDown();
        boolean down = mc.options.keyShift.isDown();
        if (up != down) return (up ? upPitch.getValue() : downPitch.getValue()).floatValue();
        return mc.player.getXRot();
    }

    public float getControlledYaw() {
        if (nullCheck()) return 0.0f;
        FreeLookModule freeLook = JewDust.moduleManager == null
                ? null : JewDust.moduleManager.getModuleByClass(FreeLookModule.class);
        if (freeLook != null && freeLook.cameraMode()) return freeLook.getCameraYaw();
        return mc.player.getYRot();
    }

    private boolean hasAttachedFirework() {
        for (Entity entity : mc.level.entitiesForRendering()) {
            if (!(entity instanceof FireworkRocketEntity rocket)) continue;
            if (((FireworkRocketEntityAccessor) rocket).jewdust$getAttachedToEntity() == mc.player) return true;
        }
        return false;
    }

    private void computeSafeOverride(Vec3 requested, Vec3 look) {
        if (!stateInitialized || requested.lengthSqr() < EPSILON) return;

        Vec3 previous = lastKnownClientVelocity;
        Vec3 predicted = lastInWater || lastInLava
                ? simulateFluid(previous, lastInWater, lastInLava)
                : calculateGlidingVelocity(previous, look);
        Vec3 previousLook = directionFromRotation(lastPitch, lastYaw);
        if (previousLook.lengthSqr() < EPSILON) previousLook = look;
        else previousLook = previousLook.normalize();

        double threshold = Math.min(rescaleAmount.getValue(), requested.length());
        double minX = clamp((Math.min(-ANTI_TICK_SKIPPING, look.x) + Math.min(-ANTI_TICK_SKIPPING, previousLook.x)) * threshold, -threshold, threshold);
        double maxX = clamp((Math.max(ANTI_TICK_SKIPPING, look.x) + Math.max(ANTI_TICK_SKIPPING, previousLook.x)) * threshold, -threshold, threshold);
        double minY = clamp((Math.min(-ANTI_TICK_SKIPPING, look.y) + Math.min(-ANTI_TICK_SKIPPING, previousLook.y)) * threshold, -threshold, threshold);
        double maxY = clamp((Math.max(ANTI_TICK_SKIPPING, look.y) + Math.max(ANTI_TICK_SKIPPING, previousLook.y)) * threshold, -threshold, threshold);
        double minZ = clamp((Math.min(-ANTI_TICK_SKIPPING, look.z) + Math.min(-ANTI_TICK_SKIPPING, previousLook.z)) * threshold, -threshold, threshold);
        double maxZ = clamp((Math.max(ANTI_TICK_SKIPPING, look.z) + Math.max(ANTI_TICK_SKIPPING, previousLook.z)) * threshold, -threshold, threshold);

        double scaleX = axisScale(requested.x, predicted.x + Math.min(0.0, minX - previous.x),
                predicted.x + Math.max(0.0, maxX - previous.x));
        double scaleY = axisScale(requested.y, predicted.y + Math.min(0.0, minY - previous.y),
                predicted.y + Math.max(0.0, maxY - previous.y));
        double scaleZ = axisScale(requested.z, predicted.z + Math.min(0.0, minZ - previous.z),
                predicted.z + Math.max(0.0, maxZ - previous.z));
        double scale = Math.max(scaleX, Math.max(scaleY, scaleZ));
        if (!Double.isFinite(scale) || scale < EPSILON) return;

        Vec3 afterFlight = calculateGlidingVelocity(requested, look);
        if (afterFlight.length() < EPSILON) return;
        double limit = requested.length() / afterFlight.length();
        if (!Double.isFinite(limit) || scale >= limit) return;

        Vec3 clamped = requested.scale(1.0 / scale);
        if (finite(clamped)) computedSafeOverride = clamped;
    }

    private double axisScale(double desired, double min, double max) {
        if (desired > 0.0) return max <= EPSILON ? Double.POSITIVE_INFINITY : desired / max;
        if (desired < 0.0) return min >= -EPSILON ? Double.POSITIVE_INFINITY : desired / min;
        return 0.0;
    }

    private Vec3 calculateGlidingVelocity(Vec3 velocity, Vec3 rotation) {
        Vec3 look = rotation.lengthSqr() < EPSILON ? mc.player.getLookAngle() : rotation.normalize();
        double horizontalLook = Math.sqrt(look.x * look.x + look.z * look.z);
        double horizontalVelocity = velocity.horizontalDistance();
        double gravity = mc.player.getAttributeValue(Attributes.GRAVITY);
        double pitchRadians = Math.asin(clamp(-look.y, -1.0, 1.0));
        double cosSquared = Math.cos(pitchRadians) * Math.cos(pitchRadians);
        Vec3 result = velocity.add(0.0, gravity * (-1.0 + cosSquared * 0.75), 0.0);

        if (result.y < 0.0 && horizontalLook > 0.0) {
            double lift = result.y * -0.1 * cosSquared;
            result = result.add(look.x * lift / horizontalLook, lift, look.z * lift / horizontalLook);
        }
        if (pitchRadians < 0.0 && horizontalLook > 0.0) {
            double climb = horizontalVelocity * -Math.sin(pitchRadians) * 0.04;
            result = result.add(-look.x * climb / horizontalLook, climb * 3.2,
                    -look.z * climb / horizontalLook);
        }
        if (horizontalLook > 0.0) {
            result = result.add((look.x / horizontalLook * horizontalVelocity - result.x) * 0.1, 0.0,
                    (look.z / horizontalLook * horizontalVelocity - result.z) * 0.1);
        }
        return result.multiply(0.9900000095367432, 0.9800000190734863, 0.9900000095367432);
    }

    private static Vec3 simulateFluid(Vec3 velocity, boolean water, boolean lava) {
        if (water) return velocity.scale(0.8).add(0.0, -0.005, 0.0);
        if (lava) return velocity.scale(0.5).add(0.0, -0.02, 0.0);
        return velocity;
    }

    private boolean passesSafetyChecks(Vec3 velocity) {
        return (!chunkCheck.getValue() || projectedChunksLoaded(velocity))
                && (!wallCheck.getValue() || !projectedPathHitsBlock(velocity));
    }

    private boolean projectedChunksLoaded(Vec3 velocity) {
        Vec3 start = mc.player.position();
        for (int tick = 1; tick <= lookahead.getValue(); tick++) {
            Vec3 position = start.add(velocity.scale(tick));
            if (!mc.level.hasChunk(((int) Math.floor(position.x)) >> 4,
                    ((int) Math.floor(position.z)) >> 4)) return false;
        }
        return true;
    }

    private boolean projectedPathHitsBlock(Vec3 velocity) {
        Vec3 start = mc.player.getEyePosition();
        Vec3 end = start.add(velocity.scale(lookahead.getValue()));
        HitResult hit = mc.level.clip(new ClipContext(start, end, ClipContext.Block.COLLIDER,
                ClipContext.Fluid.NONE, mc.player));
        return hit.getType() == HitResult.Type.BLOCK;
    }

    private static Vec3 directionFromRotation(float pitch, float yaw) {
        double y = Math.toRadians(yaw);
        double p = Math.toRadians(pitch);
        double cos = Math.cos(p);
        return new Vec3(-cos * Math.sin(y), -Math.sin(p), cos * Math.cos(y));
    }

    private static boolean finite(Vec3 value) {
        return Double.isFinite(value.x) && Double.isFinite(value.y) && Double.isFinite(value.z);
    }

    private static double clamp(double value, double min, double max) {
        return Math.max(min, Math.min(max, value));
    }

    private void resetState() {
        lastKnownClientVelocity = Vec3.ZERO;
        currentFireworkVelocity = null;
        nextTravelOverride = null;
        computedSafeOverride = null;
        lastPitch = 0.0f;
        lastYaw = 0.0f;
        lastInWater = false;
        lastInLava = false;
        stateInitialized = false;
        prepared = false;
        debugTicks = 0;
    }
}
