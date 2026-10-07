package com.arno.robotica.gear.module;

import com.arno.robotica.gear.GearConfig;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;

import java.util.List;

/** A tool or weapon module, one item per kind and level. Installed at the Tinker's Bench. */
public class GearModuleItem extends Item {
    public final GearModuleKind kind;
    /** 1 based. */
    public final int level;

    public GearModuleItem(Properties props, GearModuleKind kind, int level) {
        super(props.stacksTo(16));
        this.kind = kind;
        this.level = level;
    }

    public int minAge() {
        return kind.minAge(level);
    }

    @Override
    public void appendHoverText(ItemStack stack, TooltipContext ctx, List<Component> tooltip, TooltipFlag flag) {
        tooltip.add(descLine(kind, level).withStyle(ChatFormatting.GRAY));
        tooltip.add(kind.fitsLine().copy().withStyle(ChatFormatting.DARK_AQUA));
        tooltip.add(Component.translatable("gear.robotica.module.needs_age", minAge()).withStyle(ChatFormatting.DARK_AQUA));
        tooltip.add(costLine(kind, level).copy().withStyle(ChatFormatting.AQUA));
        tooltip.add(Component.translatable("gear.robotica.module.install").withStyle(ChatFormatting.DARK_GRAY));
    }

    /** What the module does at this level, with its numbers from the server config. */
    public static MutableComponent descLine(GearModuleKind kind, int level) {
        String key = "gear.robotica.module." + kind.id + ".desc";
        return switch (kind) {
            case TORCH_PLACER -> Component.translatable(key, GearConfig.torchLight());
            case ARMOR_PIERCE -> Component.translatable(key, pct(GearConfig.pierceShare(level)));
            case CHAIN_LIGHTNING -> Component.translatable(key, GearConfig.chainExtraArcs(level),
                    fmt(com.arno.robotica.gear.weapon.ArcBladeItem.CHAIN_RANGE + GearConfig.chainRangeBonus(level)));
            case RICOCHET -> Component.translatable(key, GearConfig.ricochetBounces(level), pct(GearConfig.ricochetDamage()));
            case LIFESTEAL -> Component.translatable(key, pct(GearConfig.lifestealShare()), fmt(GearConfig.lifestealMaxPerSecond()),
                    fmt(GearConfig.lifestealCooldown() / 20.0));
        };
    }

    /** The energy line of a module. */
    public static Component costLine(GearModuleKind kind, int level) {
        String key = "gear.robotica.module." + kind.id + ".cost";
        return switch (kind) {
            case TORCH_PLACER -> Component.translatable(key, GearConfig.torchCost());
            case ARMOR_PIERCE -> Component.translatable(key, GearConfig.pierceCost(level));
            case CHAIN_LIGHTNING -> Component.translatable(key, GearConfig.chainCostPerArc());
            case RICOCHET -> Component.translatable(key, GearConfig.ricochetCost(level));
            case LIFESTEAL -> Component.translatable(key, GearConfig.lifestealCost());
        };
    }

    private static int pct(double share) {
        return (int) Math.round(share * 100);
    }

    private static String fmt(double v) {
        return v == Math.rint(v) ? Integer.toString((int) v) : String.format(java.util.Locale.ROOT, "%.1f", v);
    }
}
