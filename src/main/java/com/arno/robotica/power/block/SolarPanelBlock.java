package com.arno.robotica.power.block;

import com.arno.robotica.power.PowerConfig;
import com.arno.robotica.power.PowerRegistry;
import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.VoxelShape;

import java.util.List;
import java.util.function.IntSupplier;

/** Daylight generator. Produces only while the sun is up and the block above has sky access. */
public class SolarPanelBlock extends PowerBlock {
    public enum Tier {
        MK1(PowerConfig::solarMk1, 4_000),
        MK2(PowerConfig::solarMk2, 16_000),
        MK3(PowerConfig::solarMk3, 40_000),
        MK4(PowerConfig::solarMk4, 100_000);

        private final IntSupplier output;
        public final int buffer;

        Tier(IntSupplier output, int buffer) {
            this.output = output;
            this.buffer = buffer;
        }

        /** FE/t in full daylight before the global generation multiplier. */
        public int output() {
            return output.getAsInt();
        }
    }

    private static final VoxelShape SHAPE = Block.box(0, 0, 0, 16, 6, 16);

    private final Tier tier;

    public SolarPanelBlock(Properties props, Tier tier) {
        super(props, () -> typeFor(tier));
        this.tier = tier;
    }

    public static net.minecraft.world.level.block.entity.BlockEntityType<SolarPanelBlockEntity> typeFor(Tier tier) {
        return switch (tier) {
            case MK1 -> PowerRegistry.SOLAR_MK1_BE.get();
            case MK2 -> PowerRegistry.SOLAR_MK2_BE.get();
            case MK3 -> PowerRegistry.SOLAR_MK3_BE.get();
            case MK4 -> PowerRegistry.SOLAR_MK4_BE.get();
        };
    }

    public Tier tier() {
        return tier;
    }

    @Override
    protected VoxelShape getShape(BlockState state, BlockGetter level, BlockPos pos, CollisionContext ctx) {
        return SHAPE;
    }

    @Override
    public void appendHoverText(ItemStack stack, Item.TooltipContext ctx, List<Component> tooltip, TooltipFlag flag) {
        tooltip.add(Component.translatable("tooltip.robotica.solar", com.arno.robotica.core.CoreConfig.scaleGeneration(tier.output())).withStyle(ChatFormatting.GRAY));
        tooltip.add(Component.translatable("tooltip.robotica.solar_push").withStyle(ChatFormatting.DARK_GRAY));
    }
}
