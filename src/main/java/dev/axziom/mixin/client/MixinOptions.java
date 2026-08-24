package dev.axziom.mixin.client;

import net.minecraft.client.Options;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.ModifyConstant;
import org.spongepowered.asm.mixin.injection.Constant;

@Mixin(Options.class)
public class MixinOptions {

    @ModifyConstant(method = "<init>", constant = @Constant(intValue = 110))
    private int jewdust$raiseFovCap(int originalMax) {
        return 160;
    }
}
