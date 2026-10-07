package com.arno.robotica.power.block;

import com.arno.robotica.power.PowerConfig;
import com.arno.robotica.power.PowerRegistry;
import net.minecraft.ChatFormatting;
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

/** Burns furnace fuel for FE. Age 1. */
public class CombustionGeneratorBlock extends PowerBlock {
    public static final BooleanProperty LIT = BlockStateProperties.LIT;

    public CombustionGeneratorBlock(Properties props) {
        super(props, PowerRegistry.COMBUSTION_GENERATOR_BE::get);
        registerDefaultState(stateDefinition.any().setValue(FACING, net.minecraft.core.Direction.NORTH).setValue(LIT, false));
    }

    @Override
    protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {
        builder.add(FACING, LIT);
    }

    @Override
    public void appendHoverText(ItemStack stack, Item.TooltipContext ctx, List<Component> tooltip, TooltipFlag flag) {
        tooltip.add(Component.translatable("tooltip.robotica.combustion_generator",
                com.arno.robotica.core.CoreConfig.scaleGeneration(PowerConfig.generatorOutput())).withStyle(ChatFormatting.GRAY));
        tooltip.add(Component.translatable("tooltip.robotica.generator_cards",
                Math.round(PowerConfig.generatorEfficiencyPerCard() * 100)).withStyle(ChatFormatting.DARK_GRAY));
    }
}
