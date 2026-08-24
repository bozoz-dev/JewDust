package dev.axziom.mixin.client;

import dev.axziom.util.player.ChatUtil;
import net.minecraft.client.GuiMessage;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.ChatComponent;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import java.util.List;

@Mixin(ChatComponent.class)
public abstract class MixinChatComponent {

    @Unique private static final double jewdust$SPEED = 12.0;
    @Unique private static final int jewdust$MAX_LINES = 3;

    @Unique private boolean jewdust$pushed;
    @Unique private float jewdust$offset;
    @Unique private GuiMessage.Line jewdust$lastHead;
    @Unique private long jewdust$lastTimeMs;

    @Inject(
            method = "render(Lnet/minecraft/client/gui/GuiGraphics;Lnet/minecraft/client/gui/Font;IIIZZ)V",
            at = @At("HEAD")
    )
    private void jewdust$slideIn(GuiGraphics graphics, Font font, int tickCount, int mouseX, int mouseY,
                                  boolean focused, boolean bl, CallbackInfo ci) {
        jewdust$pushed = false;

        float dy = jewdust$update(((ChatComponentAccessor) this).jewdust$getTrimmedMessages());
        if (dy != 0f) {
            graphics.pose().pushMatrix();
            graphics.pose().translate(0f, dy);
            jewdust$pushed = true;
        }
    }

    @Inject(
            method = "render(Lnet/minecraft/client/gui/GuiGraphics;Lnet/minecraft/client/gui/Font;IIIZZ)V",
            at = @At("RETURN")
    )
    private void jewdust$slideInEnd(GuiGraphics graphics, Font font, int tickCount, int mouseX, int mouseY,
                                     boolean focused, boolean bl, CallbackInfo ci) {
        if (jewdust$pushed) {
            graphics.pose().popMatrix();
            jewdust$pushed = false;
        }
    }

    @Unique
    private float jewdust$update(List<GuiMessage.Line> lines) {
        long now = System.currentTimeMillis();

        float dt = Math.min((now - jewdust$lastTimeMs) / 1000f, 0.1f);
        jewdust$lastTimeMs = now;

        GuiMessage.Line head = lines.isEmpty() ? null : lines.get(0);
        if (head != jewdust$lastHead) {

            if (jewdust$lastHead != null && head != ChatUtil.noAnimateHead) {

                int newLines = 0;
                for (GuiMessage.Line line : lines) {
                    if (line == jewdust$lastHead) break;
                    newLines++;
                }
                float pitch = jewdust$linePitch();
                jewdust$offset = Math.min(jewdust$offset + newLines * pitch, jewdust$MAX_LINES * pitch);
            }
            jewdust$lastHead = head;
        }

        jewdust$offset *= (float) Math.exp(-jewdust$SPEED * dt);
        if (jewdust$offset < 0.5f) {
            jewdust$offset = 0f;
        }
        return jewdust$offset;
    }

    @Unique
    private float jewdust$linePitch() {
        Minecraft mc = Minecraft.getInstance();
        double scale = mc.options.chatScale().get();
        double spacing = mc.options.chatLineSpacing().get();
        return (float) (9.0 * (spacing + 1.0) * scale);
    }
}
