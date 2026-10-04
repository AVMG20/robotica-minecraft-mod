package com.arno.robotica.power.block;

import com.arno.robotica.core.energy.EnergyItem;
import com.arno.robotica.core.energy.ItemEnergy;
import com.arno.robotica.core.util.Fmt;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;

import java.util.List;

/**
 * Block item that remembers the stored energy (core ENERGY component). The FE capability is read-only
 * (no receive, no extract): other items cannot be charged or drained through it, it only shows the content.
 */
public class AccumulatorItem extends BlockItem implements EnergyItem {
    private final AccumulatorBlock.Tier tier;

    public AccumulatorItem(AccumulatorBlock block, Properties props) {
        super(block, props);
        this.tier = block.tier();
    }

    @Override
    public int getEnergyCapacity(ItemStack stack) {
        return tier.capacity;
    }

    @Override
    public int getMaxReceive(ItemStack stack) {
        return 0;
    }

    @Override
    public int getMaxExtract(ItemStack stack) {
        return 0;
    }

    @Override
    public boolean isBarVisible(ItemStack stack) {
        return ItemEnergy.get(stack) > 0;
    }

    @Override
    public int getBarWidth(ItemStack stack) {
        return ItemEnergy.barWidth(stack);
    }

    @Override
    public int getBarColor(ItemStack stack) {
        return ItemEnergy.BAR_COLOR;
    }

    @Override
    public void appendHoverText(ItemStack stack, TooltipContext ctx, List<Component> tooltip, TooltipFlag flag) {
        ItemEnergy.appendTooltip(stack, tooltip);
        tooltip.add(Component.translatable("tooltip.robotica.accumulator_io", Fmt.compact(tier.io)).withStyle(ChatFormatting.GRAY));
        tooltip.add(Component.translatable("tooltip.robotica.accumulator_faces").withStyle(ChatFormatting.DARK_GRAY));
        tooltip.add(Component.translatable("tooltip.robotica.accumulator_keeps").withStyle(ChatFormatting.DARK_GRAY));
    }
}
