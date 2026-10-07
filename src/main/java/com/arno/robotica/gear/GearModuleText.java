package com.arno.robotica.gear;

import com.arno.robotica.core.item.HasDetails;
import com.arno.robotica.core.module.ModuleKind;
import com.arno.robotica.core.module.ModuleText;
import com.arno.robotica.gear.weapon.ArcBladeItem;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import org.jetbrains.annotations.Nullable;

import java.util.Locale;

/** Tooltip lines of the tool and weapon modules, with their numbers from the gear config. */
final class GearModuleText {
    private GearModuleText() {}

    static void register() {
        for (ModuleKind kind : ModuleKind.values()) {
            if (kind.armorOnly() || kind == ModuleKind.POWER_REGULATOR) continue;
            ModuleText.register(kind, new ModuleText() {
                @Override
                public MutableComponent describe(int level) {
                    return GearModuleText.describe(kind, level);
                }

                @Nullable
                @Override
                public Component cost(int level) {
                    return GearModuleText.cost(kind, level);
                }
            });
        }
    }

    private static MutableComponent describe(ModuleKind kind, int level) {
        String key = "module.robotica." + kind.id + ".desc";
        return switch (kind) {
            case OVERCLOCK -> Component.translatable(key, pct(GearConfig.overclockSpeed(level)));
            case FORTUNE -> Component.translatable(key, ModuleKind.roman(level), HasDetails.key("key.robotica.gear.swap_enchant"));
            case SILK_TOUCH -> Component.translatable(key, HasDetails.key("key.robotica.gear.swap_enchant"));
            case LOOTING -> Component.translatable(key, ModuleKind.roman(level));
            case LAMP_PLACER -> Component.translatable(key, GearConfig.lampLight());
            case SHARPENED_EDGE -> Component.translatable(key, pct(GearConfig.edgeDamage(level)));
            case THERMAL_EDGE -> Component.translatable(key, GearConfig.thermalSeconds());
            case ARMOR_PIERCE -> Component.translatable(key, pct(GearConfig.pierceShare(level)));
            case CHAIN_LIGHTNING -> Component.translatable(key, GearConfig.chainExtraArcs(level),
                    fmt(ArcBladeItem.CHAIN_RANGE + GearConfig.chainRangeBonus(level)));
            case RICOCHET -> Component.translatable(key, GearConfig.ricochetBounces(level), pct(GearConfig.ricochetDamage()));
            case LIFESTEAL -> Component.translatable(key, pct(GearConfig.lifestealShare()), fmt(GearConfig.lifestealMaxPerSecond()),
                    fmt(GearConfig.lifestealCooldown() / 20.0));
            default -> Component.translatable(key);
        };
    }

    @Nullable
    private static Component cost(ModuleKind kind, int level) {
        String key = "module.robotica." + kind.id + ".cost";
        return switch (kind) {
            case OVERCLOCK -> Component.translatable(key, pct(GearConfig.overclockCost(level)));
            case LAMP_PLACER -> Component.translatable(key, GearConfig.lampCost());
            case SHARPENED_EDGE -> Component.translatable("module.robotica.cost.per_hit", GearConfig.edgeCost(level));
            case THERMAL_EDGE -> Component.translatable("module.robotica.cost.per_hit", GearConfig.thermalCost(level));
            case ARMOR_PIERCE -> Component.translatable("module.robotica.cost.per_hit", GearConfig.pierceCost(level));
            case CHAIN_LIGHTNING -> Component.translatable(key, GearConfig.chainCostPerArc());
            case RICOCHET -> Component.translatable("module.robotica.cost.per_shot", GearConfig.ricochetCost(level));
            case LIFESTEAL -> Component.translatable(key, GearConfig.lifestealCost());
            default -> null;
        };
    }

    private static int pct(double share) {
        return (int) Math.round(share * 100);
    }

    private static String fmt(double v) {
        return v == Math.rint(v) ? Integer.toString((int) v) : String.format(Locale.ROOT, "%.1f", v);
    }
}
