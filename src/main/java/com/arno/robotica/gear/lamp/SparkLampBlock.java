package com.arno.robotica.gear.lamp;

import com.arno.robotica.gear.GearClientConfig;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.LevelAccessor;
import net.minecraft.world.level.LevelReader;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.level.block.state.properties.DirectionProperty;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.VoxelShape;

import java.util.EnumMap;
import java.util.Map;

/**
 * Spark Lamp: a small electric lamp with torch light (14), no collision, breaks instantly and drops nothing. Only the
 * Lamp Rod and the Lamp Placer module put it down. {@link #FACING} points away from the block it hangs on (up = floor,
 * down = ceiling, a side = wall). Client flair in {@link #animateTick}: a rare spark and a soft buzz.
 */
public class SparkLampBlock extends Block {
    public static final DirectionProperty FACING = BlockStateProperties.FACING;
    public static final int LIGHT = 14;

    private static final Map<Direction, VoxelShape> SHAPES = new EnumMap<>(Direction.class);

    static {
        SHAPES.put(Direction.UP, Block.box(5, 0, 5, 11, 8, 11));
        SHAPES.put(Direction.DOWN, Block.box(5, 8, 5, 11, 16, 11));
        SHAPES.put(Direction.NORTH, Block.box(5, 5, 8, 11, 11, 16));
        SHAPES.put(Direction.SOUTH, Block.box(5, 5, 0, 11, 11, 8));
        SHAPES.put(Direction.WEST, Block.box(8, 5, 5, 16, 11, 11));
        SHAPES.put(Direction.EAST, Block.box(0, 5, 5, 8, 11, 11));
    }

    public SparkLampBlock(Properties props) {
        super(props);
        registerDefaultState(stateDefinition.any().setValue(FACING, Direction.UP));
    }

    @Override
    protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {
        builder.add(FACING);
    }

    public BlockState facing(Direction facing) {
        return defaultBlockState().setValue(FACING, facing);
    }

    @Override
    protected VoxelShape getShape(BlockState state, BlockGetter level, BlockPos pos, CollisionContext ctx) {
        return SHAPES.get(state.getValue(FACING));
    }

    @Override
    protected boolean canSurvive(BlockState state, LevelReader level, BlockPos pos) {
        Direction facing = state.getValue(FACING);
        BlockPos support = pos.relative(facing.getOpposite());
        BlockState on = level.getBlockState(support);
        if (facing == Direction.UP) return Block.canSupportCenter(level, support, Direction.UP);
        return on.isFaceSturdy(level, support, facing);
    }

    @Override
    protected BlockState updateShape(BlockState state, Direction dir, BlockState neighbor, LevelAccessor level, BlockPos pos, BlockPos neighborPos) {
        if (dir == state.getValue(FACING).getOpposite() && !canSurvive(state, level, pos)) return Blocks.AIR.defaultBlockState();
        return super.updateShape(state, dir, neighbor, level, pos, neighborPos);
    }

    /** Client only (called by the level renderer): now and then a tiny spark at the bulb, rarely a soft buzz. */
    @Override
    public void animateTick(BlockState state, Level level, BlockPos pos, RandomSource random) {
        Direction facing = state.getValue(FACING);
        double x = pos.getX() + 0.5 - facing.getStepX() * 0.15;
        double y = pos.getY() + 0.5 - facing.getStepY() * 0.15;
        double z = pos.getZ() + 0.5 - facing.getStepZ() * 0.15;
        if (GearClientConfig.lampParticles() && random.nextInt(14) == 0) {
            level.addParticle(ParticleTypes.ELECTRIC_SPARK, x + (random.nextDouble() - 0.5) * 0.15, y + (random.nextDouble() - 0.5) * 0.15,
                    z + (random.nextDouble() - 0.5) * 0.15, (random.nextDouble() - 0.5) * 0.04, 0.02, (random.nextDouble() - 0.5) * 0.04);
        }
        if (GearClientConfig.lampSounds() && random.nextInt(260) == 0) {
            level.playLocalSound(x, y, z, SoundEvents.BEACON_AMBIENT, SoundSource.BLOCKS, 0.06F, 1.8F + random.nextFloat() * 0.2F, false);
            if (GearClientConfig.lampParticles()) {
                for (int i = 0; i < 3; i++) {
                    level.addParticle(ParticleTypes.ELECTRIC_SPARK, x, y, z, (random.nextDouble() - 0.5) * 0.08, 0.03, (random.nextDouble() - 0.5) * 0.08);
                }
            }
        }
    }
}
