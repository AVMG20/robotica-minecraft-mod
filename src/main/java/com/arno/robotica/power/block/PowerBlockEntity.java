package com.arno.robotica.power.block;

import com.arno.robotica.core.block.SyncedBlockEntity;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.Containers;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.neoforged.neoforge.items.IItemHandler;

/** Block entity base of the power module. Ticks on the server only. */
public abstract class PowerBlockEntity extends SyncedBlockEntity {
    protected PowerBlockEntity(BlockEntityType<?> type, BlockPos pos, BlockState state) {
        super(type, pos, state);
    }

    public void serverTick(ServerLevel level, BlockPos pos, BlockState state) {
    }

    /** Called when the block is broken: drop inventories here. */
    public void dropContents(Level level, BlockPos pos) {
    }

    protected static void drop(Level level, BlockPos pos, IItemHandler handler) {
        for (int i = 0; i < handler.getSlots(); i++) {
            ItemStack stack = handler.getStackInSlot(i);
            if (!stack.isEmpty()) {
                Containers.dropItemStack(level, pos.getX(), pos.getY(), pos.getZ(), stack.copy());
            }
        }
    }

    /** Sets the LIT property if the block has one and the value changed (client update only, no neighbour updates). */
    protected void setLit(boolean lit) {
        if (level == null || level.isClientSide) return;
        BlockState state = getBlockState();
        if (state.hasProperty(BlockStateProperties.LIT) && state.getValue(BlockStateProperties.LIT) != lit) {
            level.setBlock(worldPosition, state.setValue(BlockStateProperties.LIT, lit), Block.UPDATE_CLIENTS);
        }
    }
}
