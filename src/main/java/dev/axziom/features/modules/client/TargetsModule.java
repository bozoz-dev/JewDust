package dev.axziom.features.modules.client;

import dev.axziom.JewDust;
import dev.axziom.features.modules.Module;
import dev.axziom.features.settings.Setting;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.animal.bee.Bee;
import net.minecraft.world.entity.animal.goat.Goat;
import net.minecraft.world.entity.animal.golem.IronGolem;
import net.minecraft.world.entity.animal.polarbear.PolarBear;
import net.minecraft.world.entity.animal.wolf.Wolf;
import net.minecraft.world.entity.boss.enderdragon.EnderDragon;
import net.minecraft.world.entity.monster.*;
import net.minecraft.world.entity.monster.piglin.Piglin;
import net.minecraft.world.entity.monster.spider.CaveSpider;
import net.minecraft.world.entity.monster.spider.Spider;
import net.minecraft.world.entity.monster.zombie.*;
import net.minecraft.world.entity.player.Player;

public class TargetsModule extends Module {

    public final Setting<Boolean> players = bool("Players", true);
    public final Setting<Boolean> hostiles = bool("Hostiles", false);
    public final Setting<Boolean> neutrals = bool("Neutrals", false);
    public final Setting<Boolean> passives = bool("Passives", false);

    public TargetsModule() {
        super("Targets", "Configure which entities combat modules target.", Category.CLIENT);
    }

    @Override
    public void onLoad() {
        if (!isEnabled()) enable();
    }

    @Override
    public void onDisable() {
        enable();
    }

    public boolean isValidPlayerTarget(Entity entity) {
        if (!(entity instanceof Player)) return false;
        return isValidTarget(entity);
    }

    public boolean isValidTarget(Entity entity) {
        if (entity == mc.player) return false;
        if (!(entity instanceof LivingEntity living)) return false;
        if (living.isDeadOrDying()) return false;

        if (entity instanceof Player player) {
            return players.getValue() && !JewDust.friendManager.isFriend(player);
        }

        if (hostiles.getValue() && isHostile(entity)) return true;
        if (neutrals.getValue() && isNeutral(entity)) return true;
        if (passives.getValue() && isPassive(entity)) return true;

        return false;
    }

    public static boolean isHostile(Entity e) {
        if (isNeutralEntityType(e)) return isAggressiveNow(e);
        return e instanceof Enemy || e instanceof EnderDragon;
    }

    public static boolean isNeutral(Entity e) {
        return isNeutralEntityType(e) && !isAggressiveNow(e);
    }

    public static boolean isPassive(Entity e) {
        return e instanceof Mob && !isHostile(e) && !isNeutral(e);
    }

    private static boolean isNeutralEntityType(Entity e) {
        return e instanceof EnderMan
                || e instanceof Piglin
                || e instanceof ZombifiedPiglin
                || e instanceof Spider
                || e instanceof CaveSpider
                || e instanceof PolarBear
                || (e instanceof Wolf w && !w.isTame())
                || e instanceof Bee
                || e instanceof Goat
                || (e instanceof IronGolem g && !g.isPlayerCreated());
    }

    private static boolean isAggressiveNow(Entity e) {
        if (e instanceof EnderMan enderman) return enderman.isCreepy();
        if (e instanceof ZombifiedPiglin piglin) return piglin.isAggressive();
        if (e instanceof Piglin piglin) return piglin.isAggressive();
        if (e instanceof Spider spider) return spider.isAggressive();
        if (e instanceof CaveSpider) return true;
        if (e instanceof PolarBear bear) return bear.isAggressive();
        if (e instanceof Wolf wolf) return wolf.isAggressive();
        if (e instanceof Bee bee) return bee.isAngry();
        return false;
    }
}
