package com.arno.robotica.gear.lamp;

import com.arno.robotica.gear.GearBlocks;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
import net.neoforged.neoforge.common.util.BlockSnapshot;
import net.neoforged.neoforge.event.EventHooks;

/**
 * Puts down and takes away Spark Lamps for a player (Lamp Rod, Lamp Placer module). Placement checks what a player
 * placing a block would face: may build, spawn protection ({@code mayInteract}), the world border, and the normal place
 * event, so claim mods can refuse it. Server side only.
 */
public final class SparkLamps {
    private SparkLamps() {}

    /** True when {@code spot} is free (air or replaceable) and this player may put a lamp there. */
    public static boolean canPlace(ServerPlayer player, ServerLevel level, BlockPos spot) {
        if (!player.mayBuild() || !level.isLoaded(spot) || !level.getWorldBorder().isWithinBounds(spot)) return false;
        if (!level.mayInteract(player, spot)) return false;
        BlockState here = level.getBlockState(spot);
        return here.isAir() || (here.canBeReplaced() && here.getFluidState().isEmpty());
    }

    /**
     * Places a lamp facing {@code facing} at {@code spot} after {@link #canPlace}, fires the place event (undone when a
     * claim mod cancels it). Returns true when the lamp stays.
     */
    public static boolean place(ServerPlayer player, ServerLevel level, BlockPos spot, Direction facing) {
        if (!canPlace(player, level, spot)) return false;
        BlockSnapshot snapshot = BlockSnapshot.create(level.dimension(), level, spot);
        if (!level.setBlock(spot, GearBlocks.SPARK_LAMP.get().facing(facing), Block.UPDATE_ALL)) return false;
        if (EventHooks.onBlockPlace(player, snapshot, facing)) {
            snapshot.restore(Block.UPDATE_ALL);
            return false;
        }
        return true;
    }

    /** Removes the lamp at {@code pos} if this player may break it there (break event included). */
    public static boolean remove(ServerPlayer player, ServerLevel level, BlockPos pos) {
        if (!level.getBlockState(pos).is(GearBlocks.SPARK_LAMP.get()) || !player.mayBuild() || !level.mayInteract(player, pos)) return false;
        return player.gameMode.destroyBlock(pos);
    }
}
