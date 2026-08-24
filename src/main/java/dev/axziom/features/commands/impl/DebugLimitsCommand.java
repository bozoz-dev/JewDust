package dev.axziom.features.commands.impl;

import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import dev.axziom.features.commands.Command;
import dev.axziom.features.debug.MoveLimitDebug;
import dev.axziom.manager.CommandManager;

public class DebugLimitsCommand extends Command {
    public DebugLimitsCommand() {
        super("debuglimits");
        setDescription("Toggles the move-rate debug overlay");
    }

    @Override
    public void createArgumentBuilder(LiteralArgumentBuilder<CommandManager> builder) {
        builder.executes((ctx) -> {
            MoveLimitDebug debug = MoveLimitDebug.get();
            debug.toggle();
            return success("Move-rate debug overlay is now %s",
                    debug.isEnabled() ? "{green} enabled" : "{red} disabled");
        });
    }
}
