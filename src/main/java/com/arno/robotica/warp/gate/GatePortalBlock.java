package com.arno.robotica.warp.gate;

import com.arno.robotica.warp.WarpRegistry;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.RandomSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.LevelAccessor;
import net.minecraft.world.level.LevelReader;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.properties.EnumProperty;
import net.minecraft.world.phys.shapes.BooleanOp;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.Shapes;
import net.minecraft.world.phys.shapes.VoxelShape;

/**
 * The shimmering surface inside an open gate. No collision; entities that overlap it are sent to the linked gate.
 * AXIS is the direction of the gate's width. The block removes itself when a neighbour in its plane is not part of the gate.
 */
public class GatePortalBlock extends Block {
    public static final EnumProperty<Direction.Axis> AXIS = EnumProperty.create("axis", Direction.Axis.class, Direction.Axis.X, Direction.Axis.Z);
    private static final VoxelShape SHAPE_X = Block.box(0, 0, 6, 16, 16, 10);
    private static final VoxelShape SHAPE_Z = Block.box(6, 0, 0, 10, 16, 16);

    public GatePortalBlock(Properties props) {
        super(props);
        registerDefaultState(stateDefinition.any().setValue(AXIS, Direction.Axis.X));
    }

    @Override
    protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {
        builder.add(AXIS);
    }

    @Override
    protected VoxelShape getShape(BlockState state, BlockGetter level, BlockPos pos, CollisionContext context) {
        return state.getValue(AXIS) == Direction.Axis.X ? SHAPE_X : SHAPE_Z;
    }

    @Override
    protected VoxelShape getCollisionShape(BlockState state, BlockGetter level, BlockPos pos, CollisionContext context) {
        return Shapes.empty();
    }

    @Override
    public ItemStack getCloneItemStack(LevelReader level, BlockPos pos, BlockState state) {
        return ItemStack.EMPTY;
    }

    private static boolean partOfGate(BlockState state) {
        return state.is(WarpRegistry.GATE_PORTAL.get()) || state.is(WarpRegistry.GATE_FRAME.get()) || state.is(WarpRegistry.GATE_CONTROLLER.get());
    }

    /** Neighbours in the portal's plane (sideways along the width, up and down) must belong to the gate. */
    @Override
    protected BlockState updateShape(BlockState state, Direction direction, BlockState neighborState, LevelAccessor level,
                                     BlockPos pos, BlockPos neighborPos) {
        Direction.Axis normal = state.getValue(AXIS) == Direction.Axis.X ? Direction.Axis.Z : Direction.Axis.X;
        if (direction.getAxis() != normal && !partOfGate(neighborState)) return Blocks.AIR.defaultBlockState();
        return super.updateShape(state, direction, neighborState, level, pos, neighborPos);
    }

    @Override
    protected void entityInside(BlockState state, Level level, BlockPos pos, Entity entity) {
        if (!(level instanceof ServerLevel serverLevel)) return;
        if (!Shapes.joinIsNotEmpty(Shapes.create(entity.getBoundingBox().move(-pos.getX(), -pos.getY(), -pos.getZ())),
                state.getShape(level, pos), BooleanOp.AND)) {
            return;
        }
        GateControllerBlockEntity controller = findController(serverLevel, pos, state);
        if (controller == null) {
            // orphan left behind (frame removed while unloaded)
            serverLevel.setBlock(pos, Blocks.AIR.defaultBlockState(), Block.UPDATE_CLIENTS);
            return;
        }
        if (controller.shape() != null && controller.shape().isInner(pos)) controller.queueEntity(entity);
    }

    /** Walks down to the bottom row, then along the width to find the controller. */
    public static GateControllerBlockEntity findController(ServerLevel level, BlockPos portalPos, BlockState state) {
        BlockPos p = portalPos;
        for (int i = 0; i < GateShape.OPEN_HEIGHT && level.getBlockState(p.below()).is(WarpRegistry.GATE_PORTAL.get()); i++) p = p.below();
        BlockPos sill = p.below();
        Direction along = state.getValue(AXIS) == Direction.Axis.X ? Direction.EAST : Direction.SOUTH;
        for (int offset = -(GateShape.WIDTH - 1); offset <= GateShape.WIDTH - 1; offset++) {
            BlockPos candidate = sill.relative(along, offset);
            if (level.getBlockState(candidate).is(WarpRegistry.GATE_CONTROLLER.get())
                    && level.getBlockEntity(candidate) instanceof GateControllerBlockEntity controller) {
                return controller;
            }
        }
        return null;
    }

    @Override
    public void animateTick(BlockState state, Level level, BlockPos pos, RandomSource random) {
        if (random.nextInt(100) == 0) {
            level.playLocalSound(pos.getX() + 0.5, pos.getY() + 0.5, pos.getZ() + 0.5, SoundEvents.PORTAL_AMBIENT, SoundSource.BLOCKS,
                    0.4F, random.nextFloat() * 0.4F + 0.8F, false);
        }
        for (int i = 0; i < 2; i++) {
            double x = pos.getX() + random.nextDouble();
            double y = pos.getY() + random.nextDouble();
            double z = pos.getZ() + random.nextDouble();
            double speed = (random.nextDouble() - 0.5) * 0.4;
            boolean alongX = state.getValue(AXIS) == Direction.Axis.X;
            level.addParticle(random.nextBoolean() ? ParticleTypes.PORTAL : ParticleTypes.REVERSE_PORTAL, x, y, z,
                    alongX ? 0.0 : speed, (random.nextDouble() - 0.5) * 0.2, alongX ? speed : 0.0);
        }
    }
}
