package dev.axziom.mixin.entity;

import net.minecraft.network.syncher.EntityDataAccessor;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.projectile.FireworkRocketEntity;
import net.minecraft.world.item.ItemStack;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

@Mixin(FireworkRocketEntity.class)
public interface FireworkRocketEntityAccessor {

    @Accessor("life")
    int jewdust$getLife();

    @Accessor("lifetime")
    int jewdust$getLifetime();

    @Accessor("attachedToEntity")
    LivingEntity jewdust$getAttachedToEntity();

    @Accessor("DATA_ID_FIREWORKS_ITEM")
    static EntityDataAccessor<ItemStack> jewdust$getFireworksItemData() {
        throw new AssertionError();
    }
}
