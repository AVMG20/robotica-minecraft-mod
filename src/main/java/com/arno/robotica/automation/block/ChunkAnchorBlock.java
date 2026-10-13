package com.arno.robotica.automation.block;

import com.arno.robotica.automation.AutomationContent;
import com.arno.robotica.automation.entity.ChunkAnchorBlockEntity;
import com.arno.robotica.core.CoreSounds;
import com.mojang.serialization.MapCodec;
import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.DustParticleOptions;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.RandomSource;
import net.minecraft.util.StringRepresentable;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.ItemInteractionResult;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.BaseEntityBlock;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.RenderShape;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.BlockEntityTicker;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.properties.EnumProperty;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.Shapes;
import net.minecraft.world.phys.shapes.VoxelShape;
import org.joml.Vector3f;
import org.jetbrains.annotations.Nullable;

import java.util.Locale;

/**
 * Chunk Anchor Mk1-3: keeps its own chunk (Mk1), 3x3 (Mk2) or 5x5 chunks (Mk3) loaded and ticking for FE. MODE drives the
 * look: a glowing core and spinning ring while it loads, red when it is dry or over its owner's cap.
 * Right-click a placed one with the next Mk to upgrade it in place.
 */
public class ChunkAnchorBlock extends BaseEntityBlock {
    public static final MapCodec<ChunkAnchorBlock> CODEC = simpleCodec(p -> new ChunkAnchorBlock(p, 1));
    public static final EnumProperty<Mode> MODE = EnumProperty.create("mode", Mode.class);
    private static final VoxelShape SHAPE = Shapes.or(Block.box(0, 0, 0, 16, 4, 16), Block.box(1.5, 4, 1.5, 14.5, 14, 14.5),
            Block.box(3, 14, 3, 13, 16, 13));
    private static final DustParticleOptions ALERT_DUST = new DustParticleOptions(new Vector3f(1.0F, 0.28F, 0.18F), 0.9F);

    /** What the block shows: off, loading, or a problem (no power, over the cap). */
    public enum Mode implements StringRepresentable {
        IDLE, ACTIVE, ALERT;

        @Override
        public String getSerializedName() {
            return name().toLowerCase(Locale.ROOT);
        }
    }

    private final int tier;

    public ChunkAnchorBlock(Properties props, int tier) {
        super(props);
        this.tier = tier;
        registerDefaultState(stateDefinition.any().setValue(MODE, Mode.IDLE));
    }

    public int mkTier() {
        return tier;
    }

    /** Chunks around its own chunk: 0 (Mk1), 1 (Mk2, 3x3), 2 (Mk3, 5x5). */
    public int radius() {
        return Math.max(0, tier - 1);
    }

    public static int tierOf(BlockState state) {
        return state.getBlock() instanceof ChunkAnchorBlock anchor ? anchor.tier : 1;
    }

    @Override
    protected MapCodec<ChunkAnchorBlock> codec() {
        return CODEC;
    }

    @Override
    protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {
        builder.add(MODE);
    }

    @Override
    protected VoxelShape getShape(BlockState state, BlockGetter level, BlockPos pos, CollisionContext ctx) {
        return SHAPE;
    }

    @Override
    protected RenderShape getRenderShape(BlockState state) {
        return RenderShape.MODEL;
    }

    @Nullable
    @Override
    public BlockEntity newBlockEntity(BlockPos pos, BlockState state) {
        return new ChunkAnchorBlockEntity(pos, state);
    }

    @Nullable
    @Override
    public <T extends BlockEntity> BlockEntityTicker<T> getTicker(Level level, BlockState state, BlockEntityType<T> type) {
        if (level.isClientSide) return null;
        return createTickerHelper(type, AutomationContent.CHUNK_ANCHOR_BE.get(), ChunkAnchorBlockEntity::serverTick);
    }

    @Override
    public void setPlacedBy(Level level, BlockPos pos, BlockState state, @Nullable LivingEntity placer, ItemStack stack) {
        super.setPlacedBy(level, pos, state, placer, stack);
        if (!level.isClientSide && level.getBlockEntity(pos) instanceof ChunkAnchorBlockEntity anchor) {
            if (placer != null) anchor.setOwner(placer.getUUID());
            anchor.startPreview();
        }
    }

    /** True when {@code stack} is this anchor's next Mk. */
    public boolean isNextMk(ItemStack stack) {
        return stack.getItem() instanceof BlockItem item && item.getBlock() instanceof ChunkAnchorBlock next && next.tier == tier + 1;
    }

    /**
     * Swaps the placed anchor for the next Mk, keeping owner, energy and battery, and gives the old block back (the next
     * Mk's recipe consumed one). The new Mk takes up its bigger area on its first tick. Server side.
     */
    public static boolean upgradeInPlace(Level level, BlockPos pos, ChunkAnchorBlock next, @Nullable Player player) {
        if (!(level.getBlockEntity(pos) instanceof ChunkAnchorBlockEntity old)) return false;
        BlockState state = level.getBlockState(pos);
        CompoundTag saved = old.saveWithoutMetadata(level.registryAccess());
        old.keepContents = true;
        if (!level.setBlock(pos, next.defaultBlockState(), Block.UPDATE_ALL)) {
            old.keepContents = false;
            return false;
        }
        if (level.getBlockEntity(pos) instanceof ChunkAnchorBlockEntity fresh) {
            fresh.loadCustomOnly(saved, level.registryAccess());
            fresh.afterUpgrade();
        }
        if (player != null) {
            ItemStack back = new ItemStack(state.getBlock().asItem());
            if (!player.getInventory().add(back)) player.drop(back, false);
            player.displayClientMessage(Component.translatable("message.robotica.worker_upgraded", next.getName()), true);
        }
        CoreSounds.play(level, pos, CoreSounds.UPGRADE_INSTALL, SoundSource.BLOCKS, 0.8F, 1.0F);
        if (level instanceof ServerLevel sl) {
            sl.sendParticles(ParticleTypes.ELECTRIC_SPARK, pos.getX() + 0.5, pos.getY() + 0.8, pos.getZ() + 0.5, 12, 0.35, 0.3, 0.35, 0.05);
        }
        return true;
    }

    @Override
    protected ItemInteractionResult useItemOn(ItemStack stack, BlockState state, Level level, BlockPos pos, Player player,
                                              InteractionHand hand, BlockHitResult hit) {
        if (!(level.getBlockEntity(pos) instanceof ChunkAnchorBlockEntity anchor)) return ItemInteractionResult.PASS_TO_DEFAULT_BLOCK_INTERACTION;
        if (isNextMk(stack)) {
            if (!player.mayBuild() || !level.mayInteract(player, pos)) return ItemInteractionResult.FAIL;
            if (!level.isClientSide && !anchor.canUse(player)) {
                player.displayClientMessage(Component.translatable("message.robotica.worker_locked"), true);
                return ItemInteractionResult.CONSUME;
            }
            if (!level.isClientSide && upgradeInPlace(level, pos, (ChunkAnchorBlock) ((BlockItem) stack.getItem()).getBlock(), player)
                    && !player.getAbilities().instabuild) stack.shrink(1);
            return ItemInteractionResult.sidedSuccess(level.isClientSide);
        }
        if (stack.getItem() instanceof BlockItem item && item.getBlock() instanceof ChunkAnchorBlock other && other.tier > tier + 1) {
            if (!level.isClientSide) player.displayClientMessage(Component.translatable("message.robotica.worker_order", tier + 1), true);
            return ItemInteractionResult.sidedSuccess(level.isClientSide);
        }
        return ItemInteractionResult.PASS_TO_DEFAULT_BLOCK_INTERACTION;
    }

    /** Right-click opens the screen; sneak-right-click shows the loaded chunks for 10 seconds and a status line. */
    @Override
    protected InteractionResult useWithoutItem(BlockState state, Level level, BlockPos pos, Player player, BlockHitResult hit) {
        if (level.isClientSide) return InteractionResult.SUCCESS;
        if (level.getBlockEntity(pos) instanceof ChunkAnchorBlockEntity anchor && player instanceof ServerPlayer sp) {
            if (player.isShiftKeyDown()) {
                anchor.startPreview();
                sp.displayClientMessage(anchor.statusLine(), true);
            } else if (!anchor.canUse(player)) {
                sp.displayClientMessage(Component.translatable("message.robotica.anchor_locked"), true);
            } else {
                sp.openMenu(anchor, buf -> buf.writeBlockPos(pos));
            }
        }
        return InteractionResult.CONSUME;
    }

    @Override
    protected void onRemove(BlockState state, Level level, BlockPos pos, BlockState newState, boolean movedByPiston) {
        if (!state.is(newState.getBlock()) && level.getBlockEntity(pos) instanceof ChunkAnchorBlockEntity anchor) {
            anchor.onBroken();
        }
        super.onRemove(state, level, pos, newState, movedByPiston);
    }

    /** Client: motes rise from the core while it loads; red sparks and smoke when it has a problem. */
    @Override
    public void animateTick(BlockState state, Level level, BlockPos pos, RandomSource random) {
        double x = pos.getX() + 0.5, y = pos.getY() + 0.55, z = pos.getZ() + 0.5;
        switch (state.getValue(MODE)) {
            case ACTIVE -> {
                if (random.nextInt(2) == 0) {
                    level.addParticle(ParticleTypes.END_ROD, x + (random.nextDouble() - 0.5) * 0.25, y + 0.2,
                            z + (random.nextDouble() - 0.5) * 0.25, 0.0, 0.015 + random.nextDouble() * 0.02, 0.0);
                }
                if (random.nextInt(4) == 0) {
                    double a = random.nextDouble() * Math.PI * 2;
                    level.addParticle(ParticleTypes.ELECTRIC_SPARK, x + Math.cos(a) * 0.36, y, z + Math.sin(a) * 0.36, 0.0, 0.0, 0.0);
                }
                if (tier > 1 && random.nextInt(6) == 0) {
                    level.addParticle(ParticleTypes.REVERSE_PORTAL, x, pos.getY() + 1.05, z,
                            (random.nextDouble() - 0.5) * 0.04, 0.05, (random.nextDouble() - 0.5) * 0.04);
                }
            }
            case ALERT -> {
                if (random.nextInt(3) == 0) {
                    level.addParticle(ALERT_DUST, x + (random.nextDouble() - 0.5) * 0.3, y, z + (random.nextDouble() - 0.5) * 0.3, 0, 0, 0);
                }
                if (random.nextInt(10) == 0) level.addParticle(ParticleTypes.SMOKE, x, pos.getY() + 1.02, z, 0.0, 0.02, 0.0);
            }
            case IDLE -> {
            }
        }
    }
}
