package dev.axziom.mixin.entity;

import net.minecraft.client.player.LocalPlayer;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

@Mixin(LocalPlayer.class)
public interface EntityRotationAccessor {
    @Accessor("xBob")
    float jewdust$getXBob();

    @Accessor("xBob")
    void jewdust$setXBob(float value);

    @Accessor("xBobO")
    float jewdust$getXBobO();

    @Accessor("xBobO")
    void jewdust$setXBobO(float value);

    @Accessor("yBob")
    float jewdust$getYBob();

    @Accessor("yBob")
    void jewdust$setYBob(float value);

    @Accessor("yBobO")
    float jewdust$getYBobO();

    @Accessor("yBobO")
    void jewdust$setYBobO(float value);

    @Accessor("lastOnGround")
    boolean jewdust$getLastOnGround();
}
