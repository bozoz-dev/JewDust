package dev.axziom.features.commands.impl;

import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import dev.axziom.JewDust;
import dev.axziom.features.Feature;
import dev.axziom.features.commands.Command;
import dev.axziom.features.gui.JewDustGui;
import dev.axziom.features.modules.Module;
import dev.axziom.manager.CommandManager;

public class FunnyCommand extends Command {
    public FunnyCommand() {
        super("funny");
        setDescription("Toggles visibility of the Funny category in the ClickGui");
    }

    @Override
    public void createArgumentBuilder(LiteralArgumentBuilder<CommandManager> builder) {
        builder.executes((ctx) -> {
            CommandManager cm = ctx.getSource();
            boolean nowVisible = !cm.isFunnyVisible();
            cm.setFunnyVisible(nowVisible);
            if (!nowVisible) {
                JewDust.moduleManager.stream()
                        .filter(m -> m.getCategory() == Module.Category.FUNNY)
                        .filter(Feature::isEnabled)
                        .forEach(Module::disable);
            }
            JewDustGui.getInstance().reload();
            return success("Funny category is now %s",
                    nowVisible ? "{green} visible" : "{red} hidden");
        });
    }
}
