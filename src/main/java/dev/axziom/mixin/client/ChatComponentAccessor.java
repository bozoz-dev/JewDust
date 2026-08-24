package dev.axziom.mixin.client;

import net.minecraft.client.GuiMessage;
import net.minecraft.client.gui.components.ChatComponent;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;
import org.spongepowered.asm.mixin.gen.Invoker;

import java.util.List;

@Mixin(ChatComponent.class)
public interface ChatComponentAccessor {

    @Accessor("allMessages")
    List<GuiMessage> jewdust$getAllMessages();

    @Accessor("trimmedMessages")
    List<GuiMessage.Line> jewdust$getTrimmedMessages();

    @Invoker("refreshTrimmedMessages")
    void jewdust$refreshTrimmedMessages();
}
