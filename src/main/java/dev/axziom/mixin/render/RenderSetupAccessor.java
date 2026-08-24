package dev.axziom.mixin.render;

import net.minecraft.client.renderer.rendertype.LayeringTransform;
import net.minecraft.client.renderer.rendertype.OutputTarget;
import net.minecraft.client.renderer.rendertype.RenderSetup;
import net.minecraft.client.renderer.rendertype.TextureTransform;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

import java.util.Map;

@Mixin(RenderSetup.class)
public interface RenderSetupAccessor {
    @Accessor("textures")
    Map<String, RenderSetup.TextureBinding> jewdust$getTextures();

    @Accessor("textureTransform")
    TextureTransform jewdust$getTextureTransform();

    @Accessor("outputTarget")
    OutputTarget jewdust$getOutputTarget();

    @Accessor("outlineProperty")
    RenderSetup.OutlineProperty jewdust$getOutlineProperty();

    @Accessor("useLightmap")
    boolean jewdust$useLightmap();

    @Accessor("useOverlay")
    boolean jewdust$useOverlay();

    @Accessor("affectsCrumbling")
    boolean jewdust$affectsCrumbling();

    @Accessor("sortOnUpload")
    boolean jewdust$sortOnUpload();

    @Accessor("bufferSize")
    int jewdust$getBufferSize();

    @Accessor("layeringTransform")
    LayeringTransform jewdust$getLayeringTransform();
}
