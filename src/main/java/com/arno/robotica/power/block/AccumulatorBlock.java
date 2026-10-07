package com.arno.robotica.power.block;

import com.arno.robotica.power.PowerConfig;
import com.arno.robotica.power.PowerRegistry;
import com.arno.robotica.core.energy.ItemEnergy;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.properties.IntegerProperty;
import org.jetbrains.annotations.Nullable;

/**
 * Big FE battery. Accepts energy on every side except the front, gives it out on the front face (FACING).
 * Keeps its energy when broken, see {@link AccumulatorBlockEntity}. Comparators read the fill level, the gauge on the
 * front shows it: {@link #CHARGE} lit cells.
 */
public class AccumulatorBlock extends PowerBlock {
    /** Cells on the front gauge. */
    public static final int CHARGE_LEVELS = 5;
    public static final IntegerProperty CHARGE = IntegerProperty.create("charge", 0, CHARGE_LEVELS);

    public enum Tier {
        I, II, III;

        /** FE stored, from the server config. */
        public int capacity() {
            return PowerConfig.accumulatorCapacity(ordinal() + 1);
        }

        /** FE/t in and out, from the server config. */
        public int io() {
            return PowerConfig.accumulatorIo(ordinal() + 1);
        }

        public String id() {
            return "accumulator_" + (ordinal() + 1);
        }
    }

    private final Tier tier;

    public AccumulatorBlock(Properties props, Tier tier) {
        super(props, () -> PowerRegistry.ACCUMULATOR_BE.get());
        this.tier = tier;
        registerDefaultState(stateDefinition.any().setValue(FACING, Direction.NORTH).setValue(CHARGE, 0));
    }

    /** Lit gauge cells for a fill level: 0 only when empty, every cell only when (nearly) full. */
    public static int chargeLevel(long stored, long capacity) {
        if (stored <= 0 || capacity <= 0) return 0;
        return (int) Math.max(1, Math.min(CHARGE_LEVELS, Math.round((double) CHARGE_LEVELS * stored / capacity)));
    }

    public Tier tier() {
        return tier;
    }

    @Override
    protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {
        builder.add(FACING, CHARGE);
    }

    /** A placed accumulator shows the energy its item carried right away. */
    @Nullable
    @Override
    public BlockState getStateForPlacement(BlockPlaceContext context) {
        BlockState state = super.getStateForPlacement(context);
        if (state == null) return null;
        return state.setValue(CHARGE, chargeLevel(ItemEnergy.get(context.getItemInHand()), tier.capacity()));
    }

    @Override
    protected boolean hasAnalogOutputSignal(BlockState state) {
        return true;
    }

    @Override
    protected int getAnalogOutputSignal(BlockState state, Level level, BlockPos pos) {
        return level.getBlockEntity(pos) instanceof AccumulatorBlockEntity be ? be.comparatorSignal() : 0;
    }
}
