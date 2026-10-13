package com.arno.robotica.power.block;

import com.arno.robotica.power.PowerConfig;
import com.arno.robotica.power.PowerRegistry;
import net.minecraft.ChatFormatting;
import net.minecraft.core.Direction;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.level.block.state.properties.BooleanProperty;

import java.util.List;

/** Charges FE items (cells, tools). Mk1 is Age 1, each Mk after it one Age later and 4 times faster. */
public class ChargerBlock extends PowerBlock {
    public static final BooleanProperty LIT = BlockStateProperties.LIT;

    private final int mk;

    public ChargerBlock(Properties props, int mk) {
        super(props, PowerRegistry.CHARGER_BE::get);
        this.mk = mk;
        registerDefaultState(stateDefinition.any().setValue(FACING, Direction.NORTH).setValue(LIT, false));
    }

    /** 1-4 */
    public int mk() {
        return mk;
    }

    @Override
    protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {
        builder.add(FACING, LIT);
    }

    @Override
    public void appendHoverText(ItemStack stack, Item.TooltipContext ctx, List<Component> tooltip, TooltipFlag flag) {
        tooltip.add(Component.translatable("tooltip.robotica.charger", PowerConfig.chargerRate(mk)).withStyle(ChatFormatting.GRAY));
        tooltip.add(Component.translatable("tooltip.robotica.charger_mainspring").withStyle(ChatFormatting.DARK_GRAY));
    }
}
