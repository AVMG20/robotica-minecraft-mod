package com.arno.robotica.exo;

import com.arno.robotica.core.module.ModuleKind;
import com.arno.robotica.core.module.ModuleText;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import org.jetbrains.annotations.Nullable;

/** Tooltip lines of the Exo modules, with their numbers from the exo config. */
final class ExoModuleText {
    private ExoModuleText() {}

    static void register() {
        for (ModuleKind kind : ModuleKind.values()) {
            if (!kind.armorOnly()) continue;
            ModuleText.register(kind, new ModuleText() {
                @Override
                public MutableComponent describe(int level) {
                    String key = "module.robotica." + kind.id + ".desc";
                    if (kind == ModuleKind.NIGHT_VISION && ExoConfig.thermalRadius(level) > 0) key += "_thermal";
                    return Component.translatable(key, ExoConfig.describeArgs(kind, level));
                }

                @Nullable
                @Override
                public Component cost(int level) {
                    String unit = unit(kind);
                    return unit == null ? null : Component.translatable("exo.robotica.unit." + unit, ExoConfig.cost(kind, level));
                }
            });
        }
    }

    /** What the configured cost is charged per (or, for solar and walk, what the module makes); null for passive modules. */
    @Nullable
    private static String unit(ModuleKind kind) {
        return switch (kind) {
            case NIGHT_VISION, REBREATHER, ROBOT_HUD, JET_ASSIST, FLIGHT, SERVO_STRIDE, STEP_ASSIST, MAGNET, HYDRO_FINS -> "second";
            case SPRING_HEELS -> "jump";
            case FALL_DAMPENER -> "block";
            case KINETIC_SHIELD -> "damage";
            case AUTO_FEEDER -> "food";
            case SONAR_PULSE, MED_INJECTOR, DASH_THRUSTERS -> "use";
            case HAZARD_SEAL -> "effect";
            case SOLAR_WEAVE -> "solar";
            case KINETIC_GENERATOR -> "walk";
            default -> null;
        };
    }
}
