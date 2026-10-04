package com.arno.robotica.automation.block;

import com.arno.robotica.automation.entity.AreaWorkerBlockEntity;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.BaseEntityBlock;
import net.minecraft.world.level.block.RenderShape;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.BlockEntityTicker;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.BlockHitResult;
import org.jetbrains.annotations.Nullable;

/** Common block behaviour of the area workers: ticking, owner on placement, GUI, dropping contents. */
public abstract class AreaWorkerBlock extends BaseEntityBlock {
    protected AreaWorkerBlock(Properties props) {
        super(props);
    }

    protected abstract BlockEntityType<? extends AreaWorkerBlockEntity> beType();

    @Override
    protected RenderShape getRenderShape(BlockState state) {
        return RenderShape.MODEL;
    }

    @Nullable
    @Override
    @SuppressWarnings("unchecked")
    public <T extends BlockEntity> BlockEntityTicker<T> getTicker(Level level, BlockState state, BlockEntityType<T> type) {
        if (level.isClientSide || type != beType()) return null;
        return (lvl, pos, st, be) -> ((AreaWorkerBlockEntity) be).serverTick((ServerLevel) lvl);
    }

    @Override
    public void setPlacedBy(Level level, BlockPos pos, BlockState state, @Nullable LivingEntity placer, ItemStack stack) {
        super.setPlacedBy(level, pos, state, placer, stack);
        if (placer != null && level.getBlockEntity(pos) instanceof AreaWorkerBlockEntity worker) {
            worker.setOwner(placer.getUUID());
        }
    }

    @Override
    protected InteractionResult useWithoutItem(BlockState state, Level level, BlockPos pos, Player player, BlockHitResult hit) {
        if (level.isClientSide) return InteractionResult.SUCCESS;
        if (level.getBlockEntity(pos) instanceof AreaWorkerBlockEntity worker && player instanceof ServerPlayer sp) {
            sp.openMenu(worker, buf -> buf.writeBlockPos(pos));
        }
        return InteractionResult.CONSUME;
    }

    @Override
    protected void onRemove(BlockState state, Level level, BlockPos pos, BlockState newState, boolean movedByPiston) {
        if (!state.is(newState.getBlock())) {
            if (level.getBlockEntity(pos) instanceof AreaWorkerBlockEntity worker) {
                worker.dropContents();
                dropExtras(level, pos, state);
            }
        }
        super.onRemove(state, level, pos, newState, movedByPiston);
    }

    /** Extra items to give back when broken (spent Mk kits). */
    protected void dropExtras(Level level, BlockPos pos, BlockState state) {
    }
}
