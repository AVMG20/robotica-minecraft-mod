package com.arno.robotica.link.block;

import com.arno.robotica.core.CoreComponents;
import com.arno.robotica.core.CoreSounds;
import com.arno.robotica.core.util.Fmt;
import com.arno.robotica.link.LinkRegistry;
import com.arno.robotica.link.network.LinkNetwork;
import com.arno.robotica.link.network.LinkNetworks;
import com.arno.robotica.link.network.Visibility;
import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.ItemInteractionResult;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.LevelReader;
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
import net.minecraft.world.level.block.state.properties.DirectionProperty;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.Shapes;
import net.minecraft.world.phys.shapes.VoxelShape;
import org.jetbrains.annotations.Nullable;

import java.util.List;

/**
 * Quantum Link Mk1-3: a pedestal with a crystal floating above it (drawn by the block entity renderer). Right-click
 * opens its screen (owner, team, operators); right-click with the next Mk upgrades it in place, keeping network,
 * settings and energy. Breaking it keeps its network and settings in the item.
 */
public class QuantumLinkBlock extends Block implements EntityBlock {
    public static final DirectionProperty FACING = BlockStateProperties.HORIZONTAL_FACING;
    /** Height of the crystal's centre above the block's base, in blocks. */
    public static final double CRYSTAL_Y = 1.12;
    private static final VoxelShape SHAPE = Shapes.or(Block.box(0, 0, 0, 16, 3, 16), Block.box(1, 3, 1, 15, 11, 15),
            Block.box(1, 11, 1, 15, 13, 15));

    private final LinkTier tier;

    public QuantumLinkBlock(Properties props, LinkTier tier) {
        super(props);
        this.tier = tier;
        registerDefaultState(stateDefinition.any().setValue(FACING, Direction.NORTH));
    }

    public LinkTier tier() {
        return tier;
    }

    @Override
    protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {
        builder.add(FACING);
    }

    @Override
    protected VoxelShape getShape(BlockState state, BlockGetter level, BlockPos pos, CollisionContext context) {
        return SHAPE;
    }

    @Override
    protected boolean useShapeForLightOcclusion(BlockState state) {
        return true;
    }

    @Nullable
    @Override
    public BlockEntity newBlockEntity(BlockPos pos, BlockState state) {
        return new QuantumLinkBlockEntity(pos, state);
    }

    @Nullable
    @Override
    public <T extends BlockEntity> BlockEntityTicker<T> getTicker(Level level, BlockState state, BlockEntityType<T> type) {
        if (level.isClientSide || type != LinkRegistry.LINK_BE.get()) return null;
        return (lvl, pos, st, be) -> ((QuantumLinkBlockEntity) be).serverTick((net.minecraft.server.level.ServerLevel) lvl, pos, st);
    }

    /** A container next to it changed (comparator update): a receiver that backed off tries it again. */
    @Override
    public void onNeighborChange(BlockState state, LevelReader level, BlockPos pos, BlockPos neighbor) {
        if (!level.isClientSide() && level.getBlockEntity(pos) instanceof QuantumLinkBlockEntity link) link.resetItemBackoff();
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

    /**
     * Owner is the placer; a network the placer may not use is dropped (the item came from someone else). Placed without
     * a player (a machine), the Link has no owner, so it keeps only a public network.
     */
    @Override
    public void setPlacedBy(Level level, BlockPos pos, BlockState state, @Nullable LivingEntity placer, ItemStack stack) {
        super.setPlacedBy(level, pos, state, placer, stack);
        if (level.isClientSide || level.getServer() == null || !(level.getBlockEntity(pos) instanceof QuantumLinkBlockEntity be)) return;
        Player player = placer instanceof Player p ? p : null;
        if (player != null) be.setOwner(player);
        if (be.networkId() != null) {
            LinkNetwork net = LinkNetworks.get(level.getServer()).get(be.networkId());
            boolean keep = net != null && (player != null ? LinkNetworks.canUse(level.getServer(), net, player)
                    : net.visibility() == Visibility.PUBLIC);
            if (!keep) be.joinNetwork(null);
        }
    }

    @Override
    protected ItemInteractionResult useItemOn(ItemStack stack, BlockState state, Level level, BlockPos pos, Player player,
                                              InteractionHand hand, BlockHitResult hit) {
        if (stack.getItem() instanceof BlockItem item && item.getBlock() instanceof QuantumLinkBlock next && next.tier.mk == tier.mk + 1) {
            if (!player.mayBuild() || !level.mayInteract(player, pos)) return ItemInteractionResult.FAIL;
            if (!level.isClientSide && level.getBlockEntity(pos) instanceof QuantumLinkBlockEntity be) {
                if (!be.canOpen(player)) {
                    player.displayClientMessage(Component.translatable("message.robotica.link.locked"), true);
                    return ItemInteractionResult.CONSUME;
                }
                if (upgradeInPlace(level, pos, state, next, player, stack.getOrDefault(CoreComponents.ENERGY.get(), 0))
                        && !player.getAbilities().instabuild) stack.shrink(1);
            }
            return ItemInteractionResult.sidedSuccess(level.isClientSide);
        }
        return ItemInteractionResult.PASS_TO_DEFAULT_BLOCK_INTERACTION;
    }

    /**
     * Swaps the placed Link for the next Mk, keeping network, settings, sides, filter, owner and energy, and gives the
     * old one back. {@code heldEnergy} is what the new item carried.
     */
    public static boolean upgradeInPlace(Level level, BlockPos pos, BlockState state, QuantumLinkBlock next, @Nullable Player player, int heldEnergy) {
        if (!(level.getBlockEntity(pos) instanceof QuantumLinkBlockEntity old)) return false;
        CompoundTag saved = old.saveWithoutMetadata(level.registryAccess());
        old.keepContents = true;
        level.setBlock(pos, next.defaultBlockState().setValue(FACING, state.getValue(FACING)), Block.UPDATE_ALL);
        if (level.getBlockEntity(pos) instanceof QuantumLinkBlockEntity fresh) {
            fresh.loadCustomOnly(saved, level.registryAccess());
            if (heldEnergy > 0) fresh.energy.setEnergy((int) Math.min(Integer.MAX_VALUE, (long) fresh.energy.getEnergyStored() + heldEnergy));
            fresh.setChanged();
        }
        if (player != null) {
            ItemStack back = new ItemStack(state.getBlock().asItem());
            if (!player.getInventory().add(back)) player.drop(back, false);
            player.displayClientMessage(Component.translatable("message.robotica.machine_upgraded", next.getName()), true);
        }
        CoreSounds.play(level, pos, CoreSounds.UPGRADE_INSTALL, SoundSource.BLOCKS, 0.8F, 1.0F);
        return true;
    }

    @Override
    protected InteractionResult useWithoutItem(BlockState state, Level level, BlockPos pos, Player player, BlockHitResult hit) {
        if (level.isClientSide) return InteractionResult.SUCCESS;
        if (!(player instanceof ServerPlayer sp) || !(level.getBlockEntity(pos) instanceof QuantumLinkBlockEntity be)) return InteractionResult.PASS;
        if (!be.canOpen(player)) {
            player.displayClientMessage(Component.translatable("message.robotica.link.locked"), true);
            return InteractionResult.CONSUME;
        }
        be.openMenu(sp);
        return InteractionResult.CONSUME;
    }

    /** Broken (not swapped for the next Mk): leave the network's sender list. */
    @Override
    protected void onRemove(BlockState state, Level level, BlockPos pos, BlockState newState, boolean movedByPiston) {
        if (!level.isClientSide && !state.is(newState.getBlock()) && level.getBlockEntity(pos) instanceof QuantumLinkBlockEntity be && !be.keepContents) {
            be.onBroken();
        }
        super.onRemove(state, level, pos, newState, movedByPiston);
    }

    /** Block events (the crystal pulse) go to the block entity. */
    @Override
    protected boolean triggerEvent(BlockState state, Level level, BlockPos pos, int id, int param) {
        super.triggerEvent(state, level, pos, id, param);
        BlockEntity be = level.getBlockEntity(pos);
        return be != null && be.triggerEvent(id, param);
    }

    @Override
    public void appendHoverText(ItemStack stack, Item.TooltipContext ctx, List<Component> tooltip, TooltipFlag flag) {
        tooltip.add(Component.translatable("tooltip.robotica.age", tier.age(), Component.translatable("age.robotica." + tier.age()))
                .withStyle(ChatFormatting.DARK_GRAY));
        tooltip.add(Component.translatable("tooltip.robotica.quantum_link").withStyle(ChatFormatting.GRAY));
        tooltip.add(Component.translatable("tooltip.robotica.quantum_link_rate", Fmt.compact(tier.rate), tier.stacks,
                Fmt.compact(tier.upkeep())).withStyle(ChatFormatting.GRAY));
        tooltip.add(Component.translatable(tier.homeOnly ? "tooltip.robotica.quantum_link_home" : "tooltip.robotica.quantum_link_any")
                .withStyle(ChatFormatting.DARK_GRAY));
        CompoundTag settings = stack.get(LinkRegistry.LINK_SETTINGS.get());
        if (settings != null && settings.hasUUID("network")) {
            tooltip.add(Component.translatable("tooltip.robotica.quantum_link_network", settings.getString("networkName"))
                    .withStyle(ChatFormatting.AQUA));
        }
    }
}
