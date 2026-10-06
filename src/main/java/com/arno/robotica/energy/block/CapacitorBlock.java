package com.arno.robotica.energy.block;

import com.arno.robotica.core.util.Fmt;
import com.arno.robotica.energy.EnergyConfig;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;

import java.util.List;

/**
 * Inside block of a Capacitor Bank. {@code kind} CAPACITOR adds storage, COIL adds input and output rate.
 * Tier 0-2 (copper/redstone/ender, basic/advanced/elite); the numbers live in the server config.
 */
public class CapacitorBlock extends PartBlock {
    public enum Kind { CAPACITOR, COIL }

    private final Kind kind;
    private final int tier;

    public CapacitorBlock(Properties props, Kind kind, int tier) {
        super(props);
        this.kind = kind;
        this.tier = tier;
    }

    public Kind kind() {
        return kind;
    }

    public int tier() {
        return tier;
    }

    /** FE stored per block (capacitors), 0 for coils. */
    public long capacity() {
        return kind == Kind.CAPACITOR ? EnergyConfig.capacitor(tier) : 0L;
    }

    /** FE/t rate added (coils), 0 for capacitors. */
    public int rate() {
        return kind == Kind.COIL ? EnergyConfig.transferCoil(tier) : 0;
    }

    @Override
    public void appendHoverText(ItemStack stack, Item.TooltipContext ctx, List<Component> tooltip, TooltipFlag flag) {
        if (kind == Kind.CAPACITOR) {
            tooltip.add(Component.translatable("tooltip.robotica.capacitor", Fmt.energy(capacity())).withStyle(ChatFormatting.GRAY));
        } else {
            tooltip.add(Component.translatable("tooltip.robotica.transfer_coil", Fmt.compact(rate())).withStyle(ChatFormatting.GRAY));
        }
        tooltip.add(Component.translatable("tooltip.robotica.bank_inside").withStyle(ChatFormatting.DARK_GRAY));
    }
}
