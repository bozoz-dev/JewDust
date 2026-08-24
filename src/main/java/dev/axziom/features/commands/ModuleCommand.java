package dev.axziom.features.commands;

import com.mojang.brigadier.arguments.BoolArgumentType;
import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import dev.axziom.features.commands.argument.ColorArgumentType;
import dev.axziom.features.modules.Module;
import dev.axziom.features.settings.Setting;
import dev.axziom.manager.CommandManager;

import java.awt.*;
import java.util.Collection;
import java.util.LinkedHashSet;
import java.util.Locale;

import static com.mojang.brigadier.arguments.BoolArgumentType.getBool;
import static com.mojang.brigadier.arguments.StringArgumentType.getString;
import static com.mojang.brigadier.arguments.StringArgumentType.greedyString;
import static dev.axziom.features.commands.argument.ColorArgumentType.getColor;
import static dev.axziom.features.commands.argument.EnumArgumentType._enum;
import static dev.axziom.features.commands.argument.EnumArgumentType.getEnum;
import static dev.axziom.features.commands.argument.NumberArgumentType.*;

public class ModuleCommand extends Command {
    private final Module module;

    public ModuleCommand(Module module) {
        super(module.getName().toLowerCase());
        setDescription("Command line configuration implementation for \"" + module.getName() + "\"");
        this.module = module;
    }

    @SuppressWarnings("unchecked")
    @Override
    public void createArgumentBuilder(LiteralArgumentBuilder<CommandManager> builder) {
        if (module instanceof TargetListCommandSource source) {
            registerTargetLists(builder, source);
        }

        for (Setting<?> setting : module.getSettings()) {
            Class<?> type = setting.getDefaultValue().getClass();

            if (Boolean.class.isAssignableFrom(type)) {
                registerBooleanArgument(builder, (Setting<Boolean>) setting);
            } else if (Number.class.isAssignableFrom(type)) {
                registerNumberArgument(builder, (Setting<? extends Number>) setting);
            } else if (Enum.class.isAssignableFrom(type)) {
                registerEnumArgument(builder, (Setting<Enum<?>>) setting);
            } else if (String.class.isAssignableFrom(type)) {
                registerStringArgument(builder, (Setting<String>) setting);
            } else if (Color.class.isAssignableFrom(type)) {
                registerColorArgument(builder, (Setting<Color>) setting);
            }
        }
    }

    private void registerTargetLists(LiteralArgumentBuilder<CommandManager> builder,
                                     TargetListCommandSource source) {
        for (TargetListCommandSource.TargetList list : source.getTargetLists()) {
            LiteralArgumentBuilder<CommandManager> listBuilder = literal(list.commandName())
                    .executes(ctx -> showTargets(list));

            listBuilder.then(literal("list").executes(ctx -> showTargets(list)));
            listBuilder.then(literal("clear").executes(ctx -> {
                list.setting().setValue("");
                source.onTargetListsChanged();
                return success("Cleared %s.%s", module.getName(), list.commandName());
            }));

            listBuilder.then(literal("add")
                    .then(targetArgument(list)
                            .executes(ctx -> addTarget(source, list, getString(ctx, "target")))));
            listBuilder.then(literal("del")
                    .then(targetArgument(list)
                            .executes(ctx -> removeTarget(source, list, getString(ctx, "target")))));
            listBuilder.then(literal("remove")
                    .then(targetArgument(list)
                            .executes(ctx -> removeTarget(source, list, getString(ctx, "target")))));

            builder.then(listBuilder);
        }
    }

    private com.mojang.brigadier.builder.RequiredArgumentBuilder<CommandManager, String> targetArgument(
            TargetListCommandSource.TargetList list) {
        return argument("target", com.mojang.brigadier.arguments.StringArgumentType.word())
                .suggests((ctx, suggestions) -> {
                    String remaining = suggestions.getRemainingLowerCase();
                    Collection<String> values = list.suggestions().get();
                    for (String value : values) {
                        if (value.toLowerCase(Locale.ROOT).startsWith(remaining)) suggestions.suggest(value);
                    }
                    return suggestions.buildFuture();
                });
    }

    private int showTargets(TargetListCommandSource.TargetList list) {
        LinkedHashSet<String> values = TargetListCommandSource.values(list.setting());
        if (values.isEmpty()) return success("%s.%s is empty", module.getName(), list.commandName());
        return success("%s.%s (%s): %s", module.getName(), list.commandName(), values.size(),
                String.join(", ", values));
    }

    private int addTarget(TargetListCommandSource source, TargetListCommandSource.TargetList list, String input) {
        String normalized = list.normalizer().apply(input);
        if (normalized == null) return fail("Unknown %s: %s", list.targetName(), input);

        LinkedHashSet<String> values = TargetListCommandSource.values(list.setting());
        if (!values.add(normalized)) return success("%s is already in %s.%s", normalized,
                module.getName(), list.commandName());

        list.setting().setValue(TargetListCommandSource.join(values));
        source.onTargetListsChanged();
        return success("Added %s to %s.%s", normalized, module.getName(), list.commandName());
    }

    private int removeTarget(TargetListCommandSource source, TargetListCommandSource.TargetList list, String input) {
        LinkedHashSet<String> values = TargetListCommandSource.values(list.setting());
        String normalized = list.normalizer().apply(input);
        if (normalized == null) {
            String raw = input.trim().toLowerCase(Locale.ROOT);
            normalized = raw.contains(":") ? raw : "minecraft:" + raw;
        }
        if (!values.remove(normalized)) return fail("%s is not in %s.%s", normalized,
                module.getName(), list.commandName());

        list.setting().setValue(TargetListCommandSource.join(values));
        source.onTargetListsChanged();
        return success("Removed %s from %s.%s", normalized, module.getName(), list.commandName());
    }

    private void registerColorArgument(LiteralArgumentBuilder<CommandManager> builder,
                                       Setting<Color> setting) {
        builder.then(literal(setting.getName().toLowerCase())
                .then(argument("value", ColorArgumentType.color())
                        .executes((ctx) -> {
                            setting.setValue(getColor(ctx, "value"));
                            Color value = setting.getValue();
                            return success("Set %s.%s to RGB(%s, %s, %s)",
                                    module.getName(),
                                    setting.getName(),
                                    value.getRed(),
                                    value.getGreen(),
                                    value.getBlue());
                        })));
    }

    private void registerStringArgument(LiteralArgumentBuilder<CommandManager> builder,
                                        Setting<String> setting) {
        builder.then(literal(setting.getName().toLowerCase())
                .then(argument("value", greedyString())
                        .executes((ctx) -> {
                            setting.setValue(getString(ctx, "value"));
                            return settingChangeReturn(setting);
                        })));
    }

    @SuppressWarnings("unchecked")
    private void registerEnumArgument(LiteralArgumentBuilder<CommandManager> builder,
                                      Setting<Enum<?>> setting) {
        Class<Enum<?>> type = (Class<Enum<?>>) setting.getDefaultValue().getClass();
        builder.then(literal(setting.getName().toLowerCase())
                .then(argument("value", _enum(type))
                        .executes((ctx) -> {
                            setting.setValue(getEnum(ctx, "value"));
                            return settingChangeReturn(setting);
                        })));
    }

    @SuppressWarnings("unchecked")
    private <T extends Number> void registerNumberArgument(LiteralArgumentBuilder<CommandManager> builder,
                                                           Setting<T> setting) {
        Class<T> type = (Class<T>) setting.getDefaultValue().getClass();
        builder.then(literal(setting.getName().toLowerCase())
                .then(argument("value", number(type, minMax(setting.getMin(), setting.getMax())))
                        .executes((ctx) -> {
                            setting.setValue(get(type, ctx, "value"));
                            return settingChangeReturn(setting);
                        })));
    }

    private void registerBooleanArgument(LiteralArgumentBuilder<CommandManager> builder,
                                         Setting<Boolean> setting) {
        builder.then(literal(setting.getName().toLowerCase())
                .then(argument("value", BoolArgumentType.bool())
                        .executes((ctx) -> {
                            setting.setValue(getBool(ctx, "value"));
                            return settingChangeReturn(setting);
                        })));
    }

    private int settingChangeReturn(final Setting<?> setting) {
        return success("Set %s.%s to %s", module.getName(), setting.getName(), setting.getValue());
    }

    @Override
    public boolean isShown() {
        return false;
    }
}