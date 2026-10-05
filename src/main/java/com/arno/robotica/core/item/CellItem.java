package com.arno.robotica.core.item;

import com.arno.robotica.core.energy.EnergyItem;
import com.arno.robotica.core.energy.ItemEnergy;
import com.arno.robotica.core.util.Fmt;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;

import java.util.List;

/**
 * Robot battery. Robots and machines pull energy out of it, chargers fill it.
 * The Mainspring is a cell that only the Winding Crank can charge (maxReceive 0).
 */
public class CellItem extends Item implements EnergyItem {
    /** Reference drain used for the runtime tooltip (Stumpy while working). */
    public static final int REFERENCE_DRAIN = 4;

    private final int capacity;
    private final int maxReceive;
    private final int maxExtract;

    public CellItem(Properties props, int capacity, int maxReceive, int maxExtract) {
        super(props.stacksTo(1));
        this.capacity = capacity;
        this.maxReceive = maxReceive;
        this.maxExtract = maxExtract;
    }

    @Override
    public int getEnergyCapacity(ItemStack stack) {
        return capacity;
    }

    @Override
    public int getMaxReceive(ItemStack stack) {
        return maxReceive;
    }

    @Override
    public int getMaxExtract(ItemStack stack) {
        return maxExtract;
    }

    @Override
    public boolean isBarVisible(ItemStack stack) {
        return true;
    }

    @Override
    public int getBarWidth(ItemStack stack) {
        return ItemEnergy.barWidth(stack);
    }

    @Override
    public int getBarColor(ItemStack stack) {
        return ItemEnergy.BAR_COLOR;
    }

    /**
     * A cell in a player's inventory tops up the FE tool or weapon in their hands (anything that holds FE but gives
     * none out), at the cell's output rate. So a Copper Cell in the backpack keeps the Bore Drill going for hours.
     */
    @Override
    public void inventoryTick(ItemStack stack, net.minecraft.world.level.Level level, net.minecraft.world.entity.Entity entity, int slot, boolean selected) {
        if (level.isClientSide || !(entity instanceof net.minecraft.world.entity.player.Player player) || level.getGameTime() % 10 != 0) return;
        int stored = ItemEnergy.get(stack);
        if (stored <= 0 || maxExtract <= 0) return;
        for (ItemStack target : new ItemStack[]{player.getMainHandItem(), player.getOffhandItem()}) {
            if (target == stack || target.getItem() instanceof net.minecraft.world.item.BlockItem
                    || !(target.getItem() instanceof EnergyItem e) || e.getMaxExtract(target) > 0) continue;
            int space = e.getEnergyCapacity(target) - ItemEnergy.get(target);
            int moved = Math.min(Math.min(space, stored), maxExtract * 10);
            if (moved <= 0) continue;
            ItemEnergy.set(target, ItemEnergy.get(target) + moved);
            ItemEnergy.set(stack, stored - moved);
            stored -= moved;
        }
    }

    @Override
    public void appendHoverText(ItemStack stack, TooltipContext ctx, List<Component> tooltip, TooltipFlag flag) {
        ItemEnergy.appendTooltip(stack, tooltip);
        long ticks = (long) ItemEnergy.get(stack) / REFERENCE_DRAIN;
        tooltip.add(Component.translatable("tooltip.robotica.cell_runtime", Fmt.duration(ticks), REFERENCE_DRAIN)
                .withStyle(ChatFormatting.GRAY));
        if (maxReceive == 0) {
            tooltip.add(Component.translatable("tooltip.robotica.mainspring").withStyle(ChatFormatting.GOLD));
        }
    }
}
