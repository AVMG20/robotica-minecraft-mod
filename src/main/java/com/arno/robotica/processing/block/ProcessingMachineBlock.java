package com.arno.robotica.processing.block;

import com.arno.robotica.core.CoreSounds;
import com.arno.robotica.core.upgrade.UpgradeCardItem;
import com.arno.robotica.processing.ProcessingRegistry;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.Containers;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.ItemInteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.context.BlockPlaceContext;
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
import net.neoforged.neoforge.items.IItemHandler;
import net.neoforged.neoforge.items.ItemHandlerHelper;
import org.jetbrains.annotations.Nullable;

/**
 * A Grinder or Electric Furnace of one Mk (1-4). Faces the player, lights up while working, opens its GUI on
 * right-click; right-click with an item puts it straight in (ore, media, battery, upgrade card).
 */
public class ProcessingMachineBlock extends Block implements EntityBlock {
    public static final DirectionProperty FACING = BlockStateProperties.HORIZONTAL_FACING;
    public static final BooleanProperty LIT = BlockStateProperties.LIT;

    public enum Kind { GRINDER, ELECTRIC_FURNACE }

    private final Kind kind;
    private final int tier;

    public ProcessingMachineBlock(Properties props, Kind kind, int tier) {
        super(props);
        this.kind = kind;
        this.tier = tier;
        registerDefaultState(stateDefinition.any().setValue(FACING, Direction.NORTH).setValue(LIT, false));
    }

    public Kind kind() {
        return kind;
    }

    /** Mk 1-4. */
    public int tier() {
        return tier;
    }

    private BlockEntityType<? extends ProcessingMachineBlockEntity> beType() {
        return kind == Kind.GRINDER ? ProcessingRegistry.GRINDER_BE.get() : ProcessingRegistry.ELECTRIC_FURNACE_BE.get();
    }

    @Override
    protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {
        builder.add(FACING, LIT);
    }

    @Nullable
    @Override
    public BlockEntity newBlockEntity(BlockPos pos, BlockState state) {
        return beType().create(pos, state);
    }

    @Nullable
    @Override
    public <T extends BlockEntity> BlockEntityTicker<T> getTicker(Level level, BlockState state, BlockEntityType<T> type) {
        if (level.isClientSide || type != beType()) return null;
        return (lvl, pos, st, be) -> ((ProcessingMachineBlockEntity) be).serverTick((ServerLevel) lvl, pos, st);
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

    @Override
    protected ItemInteractionResult useItemOn(ItemStack stack, BlockState state, Level level, BlockPos pos, Player player,
                                              InteractionHand hand, BlockHitResult hit) {
        if (stack.isEmpty() || player.isShiftKeyDown() || !(level.getBlockEntity(pos) instanceof ProcessingMachineBlockEntity be)) {
            return ItemInteractionResult.PASS_TO_DEFAULT_BLOCK_INTERACTION;
        }
        if (stack.getItem() instanceof UpgradeCardItem) {
            if (!be.upgrades.insertOne(stack, true)) return ItemInteractionResult.PASS_TO_DEFAULT_BLOCK_INTERACTION;
            if (!level.isClientSide) {
                be.upgrades.insertOne(stack);
                stack.consume(1, player);
                CoreSounds.play(level, pos, CoreSounds.UPGRADE_INSTALL, SoundSource.BLOCKS, 0.8F, 1.0F);
            }
            return ItemInteractionResult.sidedSuccess(level.isClientSide);
        }
        IItemHandler target = be.quickInsertTarget();
        ItemStack rest = ItemHandlerHelper.insertItem(target, stack.copy(), true);
        if (rest.getCount() >= stack.getCount()) return ItemInteractionResult.PASS_TO_DEFAULT_BLOCK_INTERACTION;
        if (!level.isClientSide) {
            ItemStack moved = stack.copy();
            rest = ItemHandlerHelper.insertItem(target, stack.copy(), false);
            moved.shrink(rest.getCount());
            if (!player.getAbilities().instabuild) player.setItemInHand(hand, rest);
            CoreSounds.play(level, pos, CoreSounds.SPRING_INSERT, SoundSource.BLOCKS, 0.6F, 1.2F);
            player.displayClientMessage(Component.translatable("message.robotica.machine_inserted",
                    Component.literal(moved.getCount() + "x ").append(moved.getHoverName())), true);
        }
        return ItemInteractionResult.sidedSuccess(level.isClientSide);
    }

    @Override
    protected InteractionResult useWithoutItem(BlockState state, Level level, BlockPos pos, Player player, BlockHitResult hit) {
        if (level.isClientSide) return InteractionResult.SUCCESS;
        if (level.getBlockEntity(pos) instanceof ProcessingMachineBlockEntity be && player instanceof ServerPlayer sp) {
            sp.openMenu(be, buf -> buf.writeBlockPos(pos));
            return InteractionResult.CONSUME;
        }
        return InteractionResult.PASS;
    }

    /** Creative players get no loot: with a Carry card, hand them the machine with everything in it. */
    @Override
    public BlockState playerWillDestroy(Level level, BlockPos pos, BlockState state, Player player) {
        if (!level.isClientSide && player.isCreative() && level.getBlockEntity(pos) instanceof ProcessingMachineBlockEntity be && be.hasCarry()) {
            ItemStack stack = new ItemStack(this);
            stack.applyComponents(be.collectComponents());
            Containers.dropItemStack(level, pos.getX() + 0.5, pos.getY() + 0.5, pos.getZ() + 0.5, stack);
        }
        return super.playerWillDestroy(level, pos, state, player);
    }

    @Override
    protected void onRemove(BlockState state, Level level, BlockPos pos, BlockState newState, boolean movedByPiston) {
        if (!state.is(newState.getBlock()) && level.getBlockEntity(pos) instanceof ProcessingMachineBlockEntity be) {
            be.dropContents(level, pos);
        }
        super.onRemove(state, level, pos, newState, movedByPiston);
    }
}
