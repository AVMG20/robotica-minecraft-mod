package com.arno.robotica.automation.block;

import com.arno.robotica.automation.entity.SupplyCrateBlockEntity;
import com.mojang.serialization.MapCodec;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.BaseEntityBlock;
import net.minecraft.world.level.block.RenderShape;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.BlockHitResult;
import org.jetbrains.annotations.Nullable;

/** Plain 27-slot storage block. */
public class SupplyCrateBlock extends BaseEntityBlock {
    public static final MapCodec<SupplyCrateBlock> CODEC = simpleCodec(SupplyCrateBlock::new);

    public SupplyCrateBlock(Properties props) {
        super(props);
    }

    @Override
    protected MapCodec<SupplyCrateBlock> codec() {
        return CODEC;
    }

    @Override
    protected RenderShape getRenderShape(BlockState state) {
        return RenderShape.MODEL;
    }

    @Nullable
    @Override
    public BlockEntity newBlockEntity(BlockPos pos, BlockState state) {
        return new SupplyCrateBlockEntity(pos, state);
    }

    @Override
    protected InteractionResult useWithoutItem(BlockState state, Level level, BlockPos pos, Player player, BlockHitResult hit) {
        if (level.isClientSide) return InteractionResult.SUCCESS;
        if (level.getBlockEntity(pos) instanceof SupplyCrateBlockEntity crate && player instanceof ServerPlayer sp) {
            sp.openMenu(crate, buf -> buf.writeBlockPos(pos));
        }
        return InteractionResult.CONSUME;
    }

    @Override
    protected void onRemove(BlockState state, Level level, BlockPos pos, BlockState newState, boolean movedByPiston) {
        if (!state.is(newState.getBlock()) && level.getBlockEntity(pos) instanceof SupplyCrateBlockEntity crate) {
            crate.dropContents();
        }
        super.onRemove(state, level, pos, newState, movedByPiston);
    }

    @Override
    protected boolean hasAnalogOutputSignal(BlockState state) {
        return true;
    }

    @Override
    protected int getAnalogOutputSignal(BlockState state, Level level, BlockPos pos) {
        if (!(level.getBlockEntity(pos) instanceof SupplyCrateBlockEntity crate)) return 0;
        float fill = 0;
        for (int i = 0; i < crate.items.getSlots(); i++) {
            var stack = crate.items.getStackInSlot(i);
            if (!stack.isEmpty()) fill += (float) stack.getCount() / stack.getMaxStackSize();
        }
        fill /= crate.items.getSlots();
        return net.minecraft.util.Mth.lerpDiscrete(fill, 0, 15);
    }
}
