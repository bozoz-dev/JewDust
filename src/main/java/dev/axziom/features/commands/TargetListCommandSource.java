package dev.axziom.features.commands;

import dev.axziom.features.settings.Setting;

import java.util.Collection;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.function.Function;
import java.util.function.Supplier;

public interface TargetListCommandSource {
    List<TargetList> getTargetLists();

    default void onTargetListsChanged() {}

    static LinkedHashSet<String> values (Setting<String> setting) {
        LinkedHashSet<String> values = new LinkedHashSet<>();
        for (String part : setting.getValue().split(",")) {
            String value = part.trim();
            if (!value.isEmpty()) values.add(value);
        }
        return values;
    }

    static String join(Collection<String> values) {
        return String.join(",", values);
    }

    record TargetList(
            String commandName,
            String targetName,
            Setting<String> setting,
            Function<String, String> normalizer,
            Supplier<Collection<String>> suggestions
    ) {}
}