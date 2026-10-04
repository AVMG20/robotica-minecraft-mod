package com.arno.robotica.power.block;

import com.arno.robotica.power.PowerRegistry;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;

/**
 * Big FE battery. Accepts energy on every side except the front, gives it out on the front face (FACING).
 * Keeps its energy when broken, see {@link AccumulatorBlockEntity}. Comparators read the fill level.
 */
public class AccumulatorBlock extends PowerBlock {
    public enum Tier {
        I(1_000_000, 1_000),
        II(4_000_000, 4_000),
        III(16_000_000, 16_000);

        public final int capacity;
        public final int io;

        Tier(int capacity, int io) {
            this.capacity = capacity;
            this.io = io;
        }

        public String id() {
            return "accumulator_" + (ordinal() + 1);
        }
    }

    private final Tier tier;

    public AccumulatorBlock(Properties props, Tier tier) {
        super(props, () -> PowerRegistry.ACCUMULATOR_BE.get());
        this.tier = tier;
        registerDefaultState(stateDefinition.any().setValue(FACING, Direction.NORTH));
    }

    public Tier tier() {
        return tier;
    }

    @Override
    protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {
        builder.add(FACING);
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
