package dev.axziom.manager;

import dev.axziom.JewDust;
import dev.axziom.event.Stage;
import dev.axziom.event.impl.entity.DeathEvent;
import dev.axziom.event.impl.entity.player.TickEvent;
import dev.axziom.event.impl.entity.player.UpdateWalkingPlayerEvent;
import dev.axziom.event.impl.input.KeyInputEvent;
import dev.axziom.event.impl.input.MouseInputEvent;
import dev.axziom.event.impl.network.ChatEvent;
import dev.axziom.event.impl.render.Render2DEvent;
import dev.axziom.event.impl.render.Render3DEvent;
import dev.axziom.event.system.Subscribe;
import dev.axziom.features.Feature;
import net.minecraft.world.entity.player.Player;

public class EventManager extends Feature {
    public void init() {
        EVENT_BUS.register(this);
    }

    public void onUnload() {
        EVENT_BUS.unregister(this);
    }

    @Subscribe
    public void onTick(TickEvent event) {
        if (nullCheck())
            return;
        JewDust.moduleManager.onTick();
        for (Player player : mc.level.players()) {
            if (player == null || player.getHealth() > 0.0F)
                continue;
            EVENT_BUS.post(new DeathEvent(player));
        }
    }

    @Subscribe(priority = 100)
    public void onUpdateWalkingPlayer(UpdateWalkingPlayerEvent event) {
        if (nullCheck())
            return;
        if (event.getStage() == Stage.PRE) {
            JewDust.positionManager.updatePosition();
        }
        if (event.getStage() == Stage.POST) {
            JewDust.positionManager.restorePosition();
        }
    }

    @Subscribe
    public void onWorldRender(Render3DEvent event) {
        JewDust.moduleManager.onRender3D(event);
    }

    @Subscribe
    public void onRenderGameOverlayEvent(Render2DEvent event) {
        JewDust.moduleManager.onRender2D(event);
    }

    @Subscribe
    public void onKeyInput(KeyInputEvent event) {
        if (event.getAction() == 1) {
            JewDust.moduleManager.onKeyPressed(event.getKey());
        } else if (event.getAction() == 0) {
            JewDust.moduleManager.onKeyReleased(event.getKey());
        }
    }

    @Subscribe
    public void onMouseInput(MouseInputEvent event) {
        if (event.getAction() == 1) {
            JewDust.moduleManager.onMousePressed(event.getButton());
        } else if (event.getAction() == 0) {
            JewDust.moduleManager.onMouseReleased(event.getButton());
        }
    }

    @Subscribe
    public void onChatSent(ChatEvent event) {
        String message = event.getMessage();
        if (!message.startsWith(JewDust.commandManager.getCommandPrefix())) {
            return;
        }
        event.cancel();
        JewDust.commandManager.onChatSent(message);
    }
}
