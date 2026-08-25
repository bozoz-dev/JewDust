package dev.axziom.features.modules.combat;

import baritone.api.BaritoneAPI;
import baritone.api.IBaritone;
import com.mojang.blaze3d.platform.NativeImage;
import dev.axziom.JewDust;
import dev.axziom.features.modules.Module;
import dev.axziom.features.settings.Setting;
import net.minecraft.client.multiplayer.PlayerInfo;
import net.minecraft.client.renderer.texture.AbstractTexture;
import net.minecraft.client.renderer.texture.DynamicTexture;
import net.minecraft.resources.Identifier;
import net.minecraft.util.ARGB;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.player.Player;

import java.util.UUID;
import java.util.function.Predicate;

public class CrimeStopperModule extends Module {
    private final Setting<Double> requiredBlackPercent = num("BlackPercent", 25.0, 1.0, 100.0);
    private final Setting<Integer> blackThreshold = num("BlackThreshold", 30, 0, 255);
    private final Setting<Double> scanRange = num("ScanRange", 128.0, 8.0, 256.0);
    private final Setting<Integer> scanInterval = num("ScanInterval", 20, 1, 200);

    private UUID targetUuid;
    private String targetName;
    private double targetBlackPercent;

    private Predicate<Entity> followFilter;
    private boolean enabledAutoSword;
    private int scanTimer;

    public CrimeStopperModule() {
        super("Crime stopper", "Stops crime - checks if a player is a nigger and follows them and kills em if they are", Category.COMBAT);
    }

    @Override
    public void onEnable() {
        targetUuid = null;
        targetName = null;
        targetBlackPercent = 0.0;
        followFilter = null;
        enabledAutoSword = false;
        scanTimer = 0;
    }

    @Override
    public void onDisable() {
        stopFollowing();
    }

    @Override
    public void onTick() {
        if (nullCheck()) return;

        if (targetUuid != null) {
            Player target = findPlayer(targetUuid);

            if (target == null || !target.isAlive()) {
                stopFollowing();
                scanTimer = 0;
            } else {
                ensureAutoSword();
                return;
            }
        }

        if (scanTimer > 0) {
            scanTimer--;
            return;
        }

        scanTimer = scanInterval.getValue();

        Player bestTarget = null;
        double bestDistance = Double.MAX_VALUE;
        double bestPercentage = 0.0;
        double maximumDistanceSquared = scanRange.getValue() *  scanRange.getValue();

        for (Player player : mc.level.players()) {
            if (player == mc.player) continue;
            if (!player.isAlive()) continue;
            if (JewDust.friendManager.isFriend(player)) continue;

            double distance = mc.player.distanceToSqr(player);

            if (distance > maximumDistanceSquared) continue;

            double percentage = getBlackPixelPercentage(player);

            if (percentage < 0.0) continue;

            if (percentage <= requiredBlackPercent.getValue()) continue;

            if (distance < bestDistance) {
                bestDistance = distance;
                bestTarget = player;
                bestPercentage = percentage;
            }
        }
        if (bestTarget != null) {
            startFollowing(bestTarget, bestPercentage);
        }
    }

    private double getBlackPixelPercentage(Player player) {
        PlayerInfo playerInfo = mc.getConnection().getPlayerInfo(player.getUUID());

        if (playerInfo == null) return -1.0;

        Identifier skinIdentifier = playerInfo.getSkin().body().texturePath();
        AbstractTexture texture = mc.getTextureManager().getTexture(skinIdentifier);

        if (!(texture instanceof DynamicTexture dynamicTexture)) {
            return -1.0;
        }

        NativeImage image = dynamicTexture.getPixels();

        if (image == null) return -1.0;

        int visiblePixels = 0;
        int blackPixels = 0;
        int threshold = blackThreshold.getValue();

        for (int y = 0; y < image.getHeight(); y++) {
            for (int x = 0; x < image.getWidth(); x++) {
                int pixel = image.getPixel(x, y);

                if (ARGB.alpha(pixel) < 128) continue;

                visiblePixels++;

                if (ARGB.red(pixel) <= threshold
                        && ARGB.green(pixel) <= threshold
                        && ARGB.blue(pixel) <= threshold) {
                    blackPixels++;
                }
            }
        }

        if (visiblePixels == 0) return 0.0;

        return blackPixels * 100.0 / visiblePixels;
    }

    private void startFollowing(Player player, double percentage) {
        stopFollowing();

        targetUuid = player.getUUID();
        targetName = player.getName().getString();
        targetBlackPercent = percentage;

        UUID followedUuid = targetUuid;

        followFilter = entity ->
                entity instanceof Player
                    && entity.getUUID().equals(followedUuid);

        IBaritone baritone = BaritoneAPI.getProvider().getPrimaryBaritone();

        baritone.getFollowProcess().follow(followFilter);

        ensureAutoSword();
    }

    private void ensureAutoSword() {
        if (targetUuid == null) return;

        AutoSwordModule autoSword = JewDust.moduleManager.getModuleByClass(AutoSwordModule.class);

        if (autoSword == null) return;

        autoSword.forceTarget(targetUuid);

        if (!autoSword.isEnabled()) {
            autoSword.enable();
            enabledAutoSword = true;
        }
    }

    private void stopFollowing() {
        if (followFilter != null) {
            IBaritone baritone = BaritoneAPI.getProvider().getPrimaryBaritone();

            if (baritone.getFollowProcess().currentFilter() == followFilter) {
                baritone.getFollowProcess().cancel();
            }
        }

        AutoSwordModule autoSword = JewDust.moduleManager.getModuleByClass(AutoSwordModule.class);

        if (autoSword != null) {
            autoSword.clearForcedTarget(targetUuid);

            if (enabledAutoSword && autoSword.isEnabled()) {
                autoSword.disable();
            }
        }

        targetUuid = null;
        targetName = null;
        targetBlackPercent = 0.0;
        followFilter = null;
        enabledAutoSword = false;
    }

    private Player findPlayer(UUID uuid) {
        if (uuid == null || mc.level == null) return null;

        for (Player player : mc.level.players()) {
            if (uuid.equals(player.getUUID())) {
                return player;
            }
        }

        return null;
    }

    @Override
    public String getDisplayInfo() {
        if (targetName == null) return null;

        return targetName + " " + String.format("%.1f%%", targetBlackPercent);
    }
}
