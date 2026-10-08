package com.arno.robotica.processing.block;

import com.arno.robotica.core.upgrade.UpgradeKind;
import com.arno.robotica.core.upgrade.UpgradeRules;
import com.arno.robotica.core.CoreComponents;
import com.arno.robotica.core.item.HasDetails;
import com.arno.robotica.core.util.Fmt;
import com.arno.robotica.processing.ProcessingConfig;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;

import java.util.List;

/** Block item of a Grinder or Electric Furnace: age, what this Mk does, and what it carries. */
public class ProcessingMachineItem extends BlockItem implements HasDetails {
    public ProcessingMachineItem(ProcessingMachineBlock block, Properties props) {
        super(block, props);
    }

    private ProcessingMachineBlock machine() {
        return (ProcessingMachineBlock) getBlock();
    }

    @Override
    public void appendHoverText(ItemStack stack, TooltipContext ctx, List<Component> tooltip, TooltipFlag flag) {
        int tier = machine().tier();
        tooltip.add(Component.translatable("tooltip.robotica.age", tier, Component.translatable("age.robotica." + tier))
                .withStyle(ChatFormatting.DARK_GRAY));
        String speed = String.format(java.util.Locale.ROOT, "%.1f", ProcessingConfig.tierSpeed(tier));
        if (machine().kind() == ProcessingMachineBlock.Kind.GRINDER) {
            tooltip.add(Component.translatable("tooltip.robotica.grinder", ProcessingConfig.oreDustCount()).withStyle(ChatFormatting.GRAY));
            tooltip.add(Component.translatable("tooltip.robotica.processing_tier", speed, UpgradeRules.mkSlots(tier))
                    .withStyle(ChatFormatting.DARK_GRAY));
        } else {
            tooltip.add(Component.translatable("tooltip.robotica.electric_furnace", ProcessingConfig.lanes(tier)).withStyle(ChatFormatting.GRAY));
            tooltip.add(Component.translatable("tooltip.robotica.processing_tier", speed, UpgradeRules.mkSlots(tier))
                    .withStyle(ChatFormatting.DARK_GRAY));
        }
        Integer energy = stack.get(CoreComponents.ENERGY.get());
        if (energy != null && energy > 0) {
            tooltip.add(Component.translatable("tooltip.robotica.processing_energy", Fmt.energy(energy)).withStyle(ChatFormatting.AQUA));
        }
    }

    @Override
    public void appendDetails(ItemStack stack, TooltipContext ctx, List<Component> lines) {
        int tier = machine().tier();
        boolean grinder = machine().kind() == ProcessingMachineBlock.Kind.GRINDER;
        int base = grinder ? ProcessingConfig.grinderPower() : ProcessingConfig.furnacePower();
        lines.add(HasDetails.line("tooltip.robotica.processing_power", (int) Math.ceil(base * ProcessingConfig.tierSpeed(tier))));
        int speed = UpgradeRules.mkCap(tier, UpgradeKind.SPEED), efficiency = UpgradeRules.mkCap(tier, UpgradeKind.EFFICIENCY);
        lines.add(grinder
                ? HasDetails.line("tooltip.robotica.grinder_cards", speed, efficiency, UpgradeRules.mkCap(tier, UpgradeKind.FORTUNE))
                : HasDetails.line("tooltip.robotica.electric_furnace_cards", speed, efficiency, UpgradeRules.mkCap(tier, UpgradeKind.RANGE)));
        lines.add(HasDetails.line(grinder ? "tooltip.robotica.grinder_help" : "tooltip.robotica.electric_furnace_help"));
        lines.add(HasDetails.line("tooltip.robotica.processing_sides"));
        lines.add(HasDetails.line("tooltip.robotica.processing_upgrade"));
    }
}
