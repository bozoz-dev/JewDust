package dev.axziom.mixin.client;

import net.minecraft.client.Options;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.Constant;
import org.spongepowered.asm.mixin.injection.ModifyConstant;

@Mixin(value = Options.class, priority = 900)
public class MixinOptions {
    @ModifyConstant(
            method = "<init>",
            constant = @Constant(intValue = 110),
            require = 0
    )
    private int jewdust$raiseFovCap(int originalMax) {
        return 160;
    }
}