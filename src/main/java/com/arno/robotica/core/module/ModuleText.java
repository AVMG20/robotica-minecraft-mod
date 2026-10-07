package com.arno.robotica.core.module;

import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import org.jetbrains.annotations.Nullable;

import java.util.EnumMap;
import java.util.Map;

/**
 * Tooltip text of a module kind with the numbers from its module's server config. The gear and exo modules register
 * theirs during init; kinds without one show only their plain lang line ({@code module.robotica.<id>.desc}).
 */
public interface ModuleText {
    /** What the module does at this level. */
    MutableComponent describe(int level);

    /** Its energy line (cost or production), or null when it has none. */
    @Nullable
    default Component cost(int level) {
        return null;
    }

    Map<ModuleKind, ModuleText> TEXTS = new EnumMap<>(ModuleKind.class);

    static void register(ModuleKind kind, ModuleText text) {
        TEXTS.put(kind, text);
    }

    static MutableComponent describe(ModuleKind kind, int level) {
        ModuleText text = TEXTS.get(kind);
        return text != null ? text.describe(level) : Component.translatable("module.robotica." + kind.id + ".desc");
    }

    @Nullable
    static Component cost(ModuleKind kind, int level) {
        ModuleText text = TEXTS.get(kind);
        return text != null ? text.cost(level) : null;
    }
}
