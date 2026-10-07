package com.arno.robotica.gear.lamp;

import com.arno.robotica.core.CoreSounds;
import com.arno.robotica.gear.GearBlocks;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundSource;
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

    /** True when {@code spot} is free (air or replaceable) and a lamp facing {@code facing} may hang there for this player. */
    public static boolean canPlace(ServerPlayer player, ServerLevel level, BlockPos spot, Direction facing) {
        if (!player.mayBuild() || !level.isLoaded(spot) || !level.getWorldBorder().isWithinBounds(spot)) return false;
        if (!level.mayInteract(player, spot)) return false;
        BlockState here = level.getBlockState(spot);
        if (!here.isAir() && !(here.canBeReplaced() && here.getFluidState().isEmpty())) return false;
        return GearBlocks.SPARK_LAMP.get().facing(facing).canSurvive(level, spot);
    }

    /**
     * Places a lamp facing {@code facing} at {@code spot} after {@link #canPlace}, fires the place event (undone when a
     * claim mod cancels it) and plays the zap. Returns true when the lamp stays.
     */
    public static boolean place(ServerPlayer player, ServerLevel level, BlockPos spot, Direction facing) {
        if (!canPlace(player, level, spot, facing)) return false;
        BlockSnapshot snapshot = BlockSnapshot.create(level.dimension(), level, spot);
        if (!level.setBlock(spot, GearBlocks.SPARK_LAMP.get().facing(facing), Block.UPDATE_ALL)) return false;
        if (EventHooks.onBlockPlace(player, snapshot, facing)) {
            snapshot.restore(Block.UPDATE_ALL);
            return false;
        }
        zap(level, spot, true);
        return true;
    }

    /** Removes the lamp at {@code pos} if this player may break it there (break event included). */
    public static boolean remove(ServerPlayer player, ServerLevel level, BlockPos pos) {
        if (!level.getBlockState(pos).is(GearBlocks.SPARK_LAMP.get()) || !player.mayBuild() || !level.mayInteract(player, pos)) return false;
        if (!player.gameMode.destroyBlock(pos)) return false;
        zap(level, pos, false);
        return true;
    }

    /** A rising charge and a burst of sparks (on) or a falling fizz and a few sparks (off). */
    public static void zap(ServerLevel level, BlockPos pos, boolean on) {
        double x = pos.getX() + 0.5, y = pos.getY() + 0.5, z = pos.getZ() + 0.5;
        if (on) {
            CoreSounds.play(level, pos, CoreSounds.SPARK_LAMP_ON, SoundSource.BLOCKS, 1.0F, 0.9F + level.random.nextFloat() * 0.2F);
            level.sendParticles(ParticleTypes.ELECTRIC_SPARK, x, y, z, 10, 0.12, 0.12, 0.12, 0.25);
        } else {
            CoreSounds.play(level, pos, CoreSounds.SPARK_LAMP_OFF, SoundSource.BLOCKS, 1.0F, 1.0F);
            level.sendParticles(ParticleTypes.ELECTRIC_SPARK, x, y, z, 4, 0.1, 0.1, 0.1, 0.1);
        }
    }
}
