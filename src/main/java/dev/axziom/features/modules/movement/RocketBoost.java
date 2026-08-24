package dev.axziom.features.modules.movement;

import dev.axziom.features.commands.Command;
import dev.axziom.features.modules.Module;
import dev.axziom.features.settings.Setting;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;

public final class RocketBoost extends Module {
    private static final double EPSILON = 1.0e-6;
    private static final double ANTI_TICK_SKIPPING = 0.05;

    public final Setting<Double> speed = num("Speed", 10.0, 0.1, 10.0);
    public final Setting<Double> amount = num("Amount", 1.7, 0.05, 3.0);
    public final Setting<Double> alignment = num("Alignment", 30.0, 0.0, 90.0);
    public final Setting<Boolean> requireInput = bool("RequireInput", false);
    public final Setting<Boolean> debug = bool("Debug", false);
    public final Setting<Boolean> wallCheck = bool("WallCheck", false).setPage("Safety");
    public final Setting<Integer> lookahead = num("Lookahead", 2, 1, 10).setPage("Safety");
    public final Setting<Boolean> chunkCheck = bool("ChunkCheck", false).setPage("Safety");
    public final Setting<Boolean> pauseInFluid = bool("PauseInFluid", false).setPage("Safety");

    private Vec3 lastKnownClientVelocity = Vec3.ZERO;
    private Vec3 overridingFireworkVelocity;
    private float lastPitch;
    private float lastYaw;
    private boolean lastInWater;
    private boolean lastInLava;
    private boolean stateInitialized;
    private boolean prepared;
    private int debugTicks;

    public RocketBoost() {
        super("RocketBoost", "Replaces attached-firework velocity with a fast server-acceptable gliding velocity.", Category.MOVEMENT);
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
        prepare();
        if (nullCheck()) {
            stateInitialized = false;
            return;
        }
        lastKnownClientVelocity = mc.player.getDeltaMovement();
        lastPitch = mc.player.getXRot();
        lastYaw = mc.player.getYRot();
        lastInWater = mc.player.isInWater();
        lastInLava = mc.player.isInLava();
        stateInitialized = true;
        if (debugTicks > 0) debugTicks--;
    }

    public Vec3 overrideFireworkVelocity(Vec3 vanilla) {
        prepare();
        if (!isEnabled() || !prepared || overridingFireworkVelocity == null) return vanilla;
        Vec3 replacement = overridingFireworkVelocity;
        if (debug.getValue() && debugTicks <= 0) {
            Command.sendMessage("RocketBoost %.3f -> %.3f (%.3f, %.3f, %.3f)", vanilla.length(),
                    replacement.length(), replacement.x, replacement.y, replacement.z);
            debugTicks = 20;
        }
        return replacement;
    }

    private void prepare() {
        prepared = false;
        overridingFireworkVelocity = null;
        if (nullCheck() || !mc.player.isFallFlying()) return;
        if (pauseInFluid.getValue() && (mc.player.isInWater() || mc.player.isInLava())) return;
        if (requireInput.getValue() && !hasMovementInput()) return;

        Vec3 look = mc.player.getLookAngle();
        if (look.lengthSqr() < EPSILON || !isAligned(look)) return;
        computeOverride(look.normalize().scale(speed.getValue()), look.normalize());
        if (overridingFireworkVelocity != null && passesSafetyChecks(overridingFireworkVelocity)) {
            prepared = true;
        } else {
            overridingFireworkVelocity = null;
        }
    }

    private void computeOverride(Vec3 requested, Vec3 look) {
        if (!stateInitialized || requested.lengthSqr() < EPSILON) return;

        Vec3 previous = lastKnownClientVelocity;
        Vec3 predicted = lastInWater || lastInLava
                ? simulateFluid(previous, lastInWater, lastInLava)
                : calculateGlidingVelocity(previous, look);
        Vec3 previousLook = directionFromRotation(lastPitch, lastYaw);
        if (previousLook.lengthSqr() < EPSILON) previousLook = look;
        else previousLook = previousLook.normalize();

        double threshold = Math.min(amount.getValue(), requested.length());
        double minX = clamp((Math.min(-ANTI_TICK_SKIPPING, look.x) + Math.min(-ANTI_TICK_SKIPPING, previousLook.x)) * threshold, -threshold, threshold);
        double maxX = clamp((Math.max(ANTI_TICK_SKIPPING, look.x) + Math.max(ANTI_TICK_SKIPPING, previousLook.x)) * threshold, -threshold, threshold);
        double minY = clamp((Math.min(-ANTI_TICK_SKIPPING, look.y) + Math.min(-ANTI_TICK_SKIPPING, previousLook.y)) * threshold, -threshold, threshold);
        double maxY = clamp((Math.max(ANTI_TICK_SKIPPING, look.y) + Math.max(ANTI_TICK_SKIPPING, previousLook.y)) * threshold, -threshold, threshold);
        double minZ = clamp((Math.min(-ANTI_TICK_SKIPPING, look.z) + Math.min(-ANTI_TICK_SKIPPING, previousLook.z)) * threshold, -threshold, threshold);
        double maxZ = clamp((Math.max(ANTI_TICK_SKIPPING, look.z) + Math.max(ANTI_TICK_SKIPPING, previousLook.z)) * threshold, -threshold, threshold);

        double scaleX = axisScale(requested.x, predicted.x + Math.min(0.0, minX - previous.x), predicted.x + Math.max(0.0, maxX - previous.x));
        double scaleY = axisScale(requested.y, predicted.y + Math.min(0.0, minY - previous.y), predicted.y + Math.max(0.0, maxY - previous.y));
        double scaleZ = axisScale(requested.z, predicted.z + Math.min(0.0, minZ - previous.z), predicted.z + Math.max(0.0, maxZ - previous.z));
        double scale = Math.max(scaleX, Math.max(scaleY, scaleZ));
        if (!Double.isFinite(scale) || scale < EPSILON) return;

        Vec3 afterFlight = calculateGlidingVelocity(requested, look);
        if (afterFlight.length() < EPSILON) return;
        double limit = requested.length() / afterFlight.length();
        if (!Double.isFinite(limit) || scale >= limit) return;

        Vec3 clamped = requested.scale(1.0 / scale);
        if (finite(clamped)) overridingFireworkVelocity = clamped;
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
        double pitch = Math.asin(clamp(-look.y, -1.0, 1.0));
        double cosSquared = Math.cos(pitch) * Math.cos(pitch);
        Vec3 result = velocity.add(0.0, gravity * (-1.0 + cosSquared * 0.75), 0.0);

        if (result.y < 0.0 && horizontalLook > 0.0) {
            double lift = result.y * -0.1 * cosSquared;
            result = result.add(look.x * lift / horizontalLook, lift, look.z * lift / horizontalLook);
        }
        if (pitch < 0.0 && horizontalLook > 0.0) {
            double climb = horizontalVelocity * -Math.sin(pitch) * 0.04;
            result = result.add(-look.x * climb / horizontalLook, climb * 3.2, -look.z * climb / horizontalLook);
        }
        if (horizontalLook > 0.0) {
            result = result.add((look.x / horizontalLook * horizontalVelocity - result.x) * 0.1, 0.0,
                    (look.z / horizontalLook * horizontalVelocity - result.z) * 0.1);
        }
        return result.multiply(0.9900000095367432, 0.9800000190734863, 0.9900000095367432);
    }

    private Vec3 simulateFluid(Vec3 velocity, boolean water, boolean lava) {
        if (water) return velocity.scale(0.8).add(0.0, -0.005, 0.0);
        if (lava) return velocity.scale(0.5).add(0.0, -0.02, 0.0);
        return velocity;
    }

    private boolean hasMovementInput() {
        return mc.options.keyUp.isDown() || mc.options.keyDown.isDown() || mc.options.keyLeft.isDown()
                || mc.options.keyRight.isDown() || mc.options.keyJump.isDown() || mc.options.keyShift.isDown();
    }

    private boolean isAligned(Vec3 look) {
        Vec3 velocity = mc.player.getDeltaMovement();
        if (velocity.lengthSqr() < EPSILON) return true;
        double dot = clamp(velocity.normalize().dot(look.normalize()), -1.0, 1.0);
        return Math.toDegrees(Math.acos(dot)) <= alignment.getValue();
    }

    private boolean passesSafetyChecks(Vec3 velocity) {
        return (!chunkCheck.getValue() || projectedChunksLoaded(velocity))
                && (!wallCheck.getValue() || !projectedPathHitsBlock(velocity));
    }

    private boolean projectedChunksLoaded(Vec3 velocity) {
        Vec3 start = mc.player.position();
        for (int tick = 1; tick <= lookahead.getValue(); tick++) {
            Vec3 position = start.add(velocity.scale(tick));
            if (!mc.level.hasChunk(((int) Math.floor(position.x)) >> 4, ((int) Math.floor(position.z)) >> 4)) return false;
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
        overridingFireworkVelocity = null;
        lastPitch = 0.0f;
        lastYaw = 0.0f;
        lastInWater = false;
        lastInLava = false;
        stateInitialized = false;
        prepared = false;
        debugTicks = 0;
    }
}
