package com.arno.robotica.power.tesla;

import com.arno.robotica.core.util.Fmt;
import com.arno.robotica.power.PowerClientConfig;
import com.arno.robotica.power.PowerRegistry;
import com.arno.robotica.power.block.PowerBlock;
import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.network.chat.Component;
import net.minecraft.util.RandomSource;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.ItemInteractionResult;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.LevelAccessor;
import net.minecraft.world.level.LevelReader;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.EntityBlock;
import net.minecraft.world.level.block.Mirror;
import net.minecraft.world.level.block.Rotation;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.level.block.state.properties.DirectionProperty;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.Vec3;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.Shapes;
import net.minecraft.world.phys.shapes.VoxelShape;
import org.jetbrains.annotations.Nullable;

import java.util.EnumMap;
import java.util.List;
import java.util.Map;

/**
 * Torch-sized wireless power emitter, placed on floors, walls and ceilings. FACING points away from the block it sits on.
 * A coil on a block that gives out FE (Accumulator, generator, any mod's battery) pulls from it and sends to its links;
 * any other coil is a relay that only forwards what other coils send it. Linking: {@link TeslaLinkerItem}.
 */
public class TeslaCoilBlock extends PowerBlock {
    public static final DirectionProperty FACING = BlockStateProperties.FACING;
    /** Tip of the coil, in blocks from the face it sits on (the glowing ball of the model ends at 15/16). */
    public static final double TIP = 13.5 / 16.0;

    private static final Map<Direction, VoxelShape> SHAPES = new EnumMap<>(Direction.class);

    static {
        for (Direction dir : Direction.values()) {
            SHAPES.put(dir, Shapes.or(box(dir, 4, 12, 0, 2), box(dir, 5, 11, 2, 15)));
        }
    }

    /** A box that is {@code lo..hi} wide on both lateral axes and {@code h0..h1} high along {@code dir}. */
    private static VoxelShape box(Direction dir, double lo, double hi, double h0, double h1) {
        return switch (dir) {
            case UP -> Block.box(lo, h0, lo, hi, h1, hi);
            case DOWN -> Block.box(lo, 16 - h1, lo, hi, 16 - h0, hi);
            case SOUTH -> Block.box(lo, lo, h0, hi, hi, h1);
            case NORTH -> Block.box(lo, lo, 16 - h1, hi, hi, 16 - h0);
            case EAST -> Block.box(h0, lo, lo, h1, hi, hi);
            case WEST -> Block.box(16 - h1, lo, lo, 16 - h0, hi, hi);
        };
    }

    private final TeslaTier tier;

    public TeslaCoilBlock(Properties props, TeslaTier tier) {
        super(props, PowerRegistry.TESLA_COIL_BE::get);
        this.tier = tier;
        registerDefaultState(stateDefinition.any().setValue(FACING, Direction.UP));
    }

    public TeslaTier tier() {
        return tier;
    }

    /** Where the arcs start: the glowing tip, relative to the block's corner. */
    public static Vec3 tipOffset(Direction facing) {
        double d = TIP - 0.5;
        return new Vec3(0.5 + facing.getStepX() * d, 0.5 + facing.getStepY() * d, 0.5 + facing.getStepZ() * d);
    }

    @Override
    protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {
        builder.add(FACING);
    }

    @Nullable
    @Override
    public BlockState getStateForPlacement(BlockPlaceContext context) {
        LevelReader level = context.getLevel();
        BlockPos pos = context.getClickedPos();
        BlockState state = defaultBlockState().setValue(FACING, context.getClickedFace());
        if (canSurvive(state, level, pos)) return state;
        for (Direction dir : context.getNearestLookingDirections()) {
            state = defaultBlockState().setValue(FACING, dir.getOpposite());
            if (canSurvive(state, level, pos)) return state;
        }
        return null;
    }

    @Override
    protected boolean canSurvive(BlockState state, LevelReader level, BlockPos pos) {
        Direction facing = state.getValue(FACING);
        BlockPos support = pos.relative(facing.getOpposite());
        Block below = level.getBlockState(support).getBlock();
        if (below instanceof TeslaCoilBlock) return false;
        // Odd-shaped machines (slabs, other mods' generators) still count: anything with a block entity.
        return Block.canSupportCenter(level, support, facing) || below instanceof EntityBlock;
    }

    @Override
    protected BlockState updateShape(BlockState state, Direction dir, BlockState neighbour, LevelAccessor level, BlockPos pos, BlockPos neighbourPos) {
        if (dir == state.getValue(FACING).getOpposite() && !canSurvive(state, level, pos)) return Blocks.AIR.defaultBlockState();
        return super.updateShape(state, dir, neighbour, level, pos, neighbourPos);
    }

    @Override
    protected VoxelShape getShape(BlockState state, BlockGetter level, BlockPos pos, CollisionContext ctx) {
        return SHAPES.get(state.getValue(FACING));
    }

    @Override
    protected BlockState rotate(BlockState state, Rotation rotation) {
        return state.setValue(FACING, rotation.rotate(state.getValue(FACING)));
    }

    @Override
    protected BlockState mirror(BlockState state, Mirror mirror) {
        return state.rotate(mirror.getRotation(state.getValue(FACING)));
    }

    /** Only an empty hand opens the status screen; with an item the click goes to the item (placing blocks, the Linker). */
    @Override
    protected ItemInteractionResult useItemOn(ItemStack stack, BlockState state, Level level, BlockPos pos, Player player,
                                              InteractionHand hand, BlockHitResult hit) {
        return stack.isEmpty() ? ItemInteractionResult.PASS_TO_DEFAULT_BLOCK_INTERACTION : ItemInteractionResult.SKIP_DEFAULT_BLOCK_INTERACTION;
    }

    @Override
    public void setPlacedBy(Level level, BlockPos pos, BlockState state, @Nullable LivingEntity placer, ItemStack stack) {
        super.setPlacedBy(level, pos, state, placer, stack);
        if (!level.isClientSide && placer instanceof Player player && level.getBlockEntity(pos) instanceof TeslaCoilBlockEntity be) {
            be.setOwner(player);
        }
    }

    /** Client only (called by the level renderer): sparks at the tip, and at a target while energy flows. */
    @Override
    public void animateTick(BlockState state, Level level, BlockPos pos, RandomSource random) {
        if (!PowerClientConfig.teslaParticles() || !(level.getBlockEntity(pos) instanceof TeslaCoilBlockEntity be)) return;
        List<TeslaLink> links = be.links();
        if (links.isEmpty()) return;
        boolean active = be.isActive();
        if (random.nextInt(active ? 2 : 6) == 0) {
            Vec3 tip = tipOffset(state.getValue(FACING)).add(pos.getX(), pos.getY(), pos.getZ());
            level.addParticle(ParticleTypes.ELECTRIC_SPARK, tip.x, tip.y, tip.z,
                    (random.nextDouble() - 0.5) * 0.1, (random.nextDouble() - 0.5) * 0.1, (random.nextDouble() - 0.5) * 0.1);
        }
        if (active && random.nextInt(3) == 0) {
            TeslaLink link = links.get(random.nextInt(links.size()));
            Vec3 end = TeslaCoilBlockEntity.endPoint(level, link);
            level.addParticle(ParticleTypes.ELECTRIC_SPARK, end.x, end.y, end.z,
                    (random.nextDouble() - 0.5) * 0.15, random.nextDouble() * 0.1, (random.nextDouble() - 0.5) * 0.15);
        }
    }

    @Override
    public void appendHoverText(ItemStack stack, Item.TooltipContext ctx, List<Component> tooltip, TooltipFlag flag) {
        tooltip.add(Component.translatable("tooltip.robotica.tesla_coil", tier.maxLinks, tier.range(), Fmt.compact(tier.rate()))
                .withStyle(ChatFormatting.GRAY));
        tooltip.add(Component.translatable("tooltip.robotica.tesla_coil_source").withStyle(ChatFormatting.DARK_GRAY));
    }
}
