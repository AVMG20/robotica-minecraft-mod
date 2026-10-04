package com.arno.robotica.warp.gate;

import com.arno.robotica.core.CoreSounds;
import com.arno.robotica.core.util.Fmt;
import com.arno.robotica.warp.WarpConfig;
import com.arno.robotica.warp.WarpRegistry;
import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.RandomSource;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.EntityBlock;
import net.minecraft.world.level.block.Mirror;
import net.minecraft.world.level.block.Rotation;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.BlockEntityTicker;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.level.block.state.properties.BooleanProperty;
import net.minecraft.world.level.block.state.properties.DirectionProperty;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.Vec3;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.VoxelShape;
import org.jetbrains.annotations.Nullable;

import java.util.List;

/**
 * Portal Projector (Age 4, registry id {@code gate_controller}): one squat emitter that, while linked and powered,
 * projects a floating portal about two blocks above itself (see {@link PortalGeometry}). Holds the FE buffer and the link.
 * FACING is the side the portal looks at and travellers arrive on; ACTIVE is true while the portal is projected.
 */
public class PortalProjectorBlock extends Block implements EntityBlock {
    public static final DirectionProperty FACING = BlockStateProperties.HORIZONTAL_FACING;
    public static final BooleanProperty ACTIVE = BooleanProperty.create("active");
    private static final VoxelShape SHAPE = Block.box(1, 0, 1, 15, 12, 15);

    public PortalProjectorBlock(Properties props) {
        super(props);
        registerDefaultState(stateDefinition.any().setValue(FACING, Direction.NORTH).setValue(ACTIVE, false));
    }

    @Override
    protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {
        builder.add(FACING, ACTIVE);
    }

    @Override
    protected VoxelShape getShape(BlockState state, BlockGetter level, BlockPos pos, CollisionContext context) {
        return SHAPE;
    }

    @Nullable
    @Override
    public BlockState getStateForPlacement(BlockPlaceContext context) {
        return defaultBlockState().setValue(FACING, context.getHorizontalDirection().getOpposite());
    }

    @Override
    protected BlockState rotate(BlockState state, Rotation rotation) {
        return state.setValue(FACING, rotation.rotate(state.getValue(FACING)));
    }

    @Override
    protected BlockState mirror(BlockState state, Mirror mirror) {
        return state.rotate(mirror.getRotation(state.getValue(FACING)));
    }

    @Nullable
    @Override
    public BlockEntity newBlockEntity(BlockPos pos, BlockState state) {
        return WarpRegistry.GATE_CONTROLLER_BE.get().create(pos, state);
    }

    @Nullable
    @Override
    @SuppressWarnings("unchecked")
    public <T extends BlockEntity> BlockEntityTicker<T> getTicker(Level level, BlockState state, BlockEntityType<T> type) {
        if (level.isClientSide || type != WarpRegistry.GATE_CONTROLLER_BE.get()) return null;
        return (lvl, pos, st, be) -> ((PortalProjectorBlockEntity) be).serverTick((ServerLevel) lvl);
    }

    /** Right-click: a short status line (linked, powered, FE). */
    @Override
    protected InteractionResult useWithoutItem(BlockState state, Level level, BlockPos pos, Player player, BlockHitResult hit) {
        if (level.isClientSide) return InteractionResult.SUCCESS;
        if (level.getBlockEntity(pos) instanceof PortalProjectorBlockEntity gate && level instanceof ServerLevel serverLevel) {
            gate.refresh(serverLevel); // status only, never pays idle FE
            String key = gate.linked() == null ? "message.robotica.warp.gate_status_unlinked"
                    : gate.isActive() ? "message.robotica.warp.gate_status_open" : "message.robotica.warp.gate_status_unpowered";
            player.displayClientMessage(Component.translatable(key, Fmt.energy(gate.energy.getEnergyStored()), Fmt.energy(gate.energy.getMaxEnergyStored())), true);
        }
        return InteractionResult.CONSUME;
    }

    @Override
    public void setPlacedBy(Level level, BlockPos pos, BlockState state, @Nullable LivingEntity placer, ItemStack stack) {
        super.setPlacedBy(level, pos, state, placer, stack);
        if (!level.isClientSide && level.getBlockEntity(pos) instanceof PortalProjectorBlockEntity gate) {
            gate.setOwner(placer instanceof Player player ? player : null);
        }
    }

    @Override
    protected void onRemove(BlockState state, Level level, BlockPos pos, BlockState newState, boolean movedByPiston) {
        if (!state.is(newState.getBlock()) && level instanceof ServerLevel serverLevel
                && level.getBlockEntity(pos) instanceof PortalProjectorBlockEntity gate) {
            gate.shutdown(serverLevel);
        }
        super.onRemove(state, level, pos, newState, movedByPiston);
    }

    /** Client only (called by the level renderer near the player): ambient hum, motes streaming into the portal, sparks at the lens. */
    @Override
    public void animateTick(BlockState state, Level level, BlockPos pos, RandomSource random) {
        if (!state.getValue(ACTIVE)) return;
        Vec3 c = PortalGeometry.center(pos);
        if (random.nextInt(100) == 0) {
            level.playLocalSound(c.x, c.y, c.z, CoreSounds.GATE_AMBIENT.get(), SoundSource.BLOCKS, 0.4F, random.nextFloat() * 0.4F + 0.8F, false);
        }
        Direction facing = state.getValue(FACING);
        // unit vectors in the portal plane: sideways (u) and the facing axis (normal)
        double nx = facing.getStepX(), nz = facing.getStepZ();
        double ux = nz != 0 ? 1.0 : 0.0, uz = nx != 0 ? 1.0 : 0.0;
        for (int i = 0; i < 2; i++) {
            // a point inside the ellipse
            double a = random.nextDouble() * Math.PI * 2.0;
            double r = Math.sqrt(random.nextDouble());
            double su = Math.cos(a) * r * PortalGeometry.WIDTH / 2.0;
            double sy = Math.sin(a) * r * PortalGeometry.HEIGHT / 2.0;
            double x = c.x + ux * su, y = c.y + sy, z = c.z + uz * su;
            double side = random.nextBoolean() ? 1.0 : -1.0;
            // PORTAL particles fly from (position + velocity) to the position: they stream into the portal from either side
            level.addParticle(random.nextBoolean() ? ParticleTypes.PORTAL : ParticleTypes.REVERSE_PORTAL, x, y, z,
                    nx * side * (0.6 + random.nextDouble() * 0.8), (random.nextDouble() - 0.5) * 0.4, nz * side * (0.6 + random.nextDouble() * 0.8));
        }
        if (random.nextInt(3) == 0) {
            // end rod motes drift from the lens up towards the portal
            double a = random.nextDouble() * Math.PI * 2.0;
            level.addParticle(ParticleTypes.END_ROD, pos.getX() + 0.5 + Math.cos(a) * 0.15, pos.getY() + PortalGeometry.TOP + 0.05,
                    pos.getZ() + 0.5 + Math.sin(a) * 0.15, (random.nextDouble() - 0.5) * 0.01, 0.045 + random.nextDouble() * 0.03,
                    (random.nextDouble() - 0.5) * 0.01);
        }
    }

    @Override
    public void appendHoverText(ItemStack stack, Item.TooltipContext ctx, List<Component> tooltip, TooltipFlag flag) {
        tooltip.add(Component.translatable("tooltip.robotica.age", 4, Component.translatable("age.robotica.4")).withStyle(ChatFormatting.DARK_GRAY));
        tooltip.add(Component.translatable("tooltip.robotica.gate_controller", WarpConfig.gateIdleCost(), Fmt.energy(WarpConfig.gateEntityCost()))
                .withStyle(ChatFormatting.GRAY));
        tooltip.add(Component.translatable("tooltip.robotica.gate_controller_build").withStyle(ChatFormatting.DARK_GRAY));
    }
}
