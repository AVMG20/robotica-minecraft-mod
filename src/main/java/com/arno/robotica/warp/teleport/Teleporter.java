package com.arno.robotica.warp.teleport;

import com.arno.robotica.warp.WarpRegistry;
import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.tags.BlockTags;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.portal.DimensionTransition;
import net.minecraft.world.phys.Vec3;
import net.minecraft.world.phys.shapes.VoxelShape;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Optional;

/** Teleport helper: safe spot search, chunk loading, the actual move and the effects at both ends. */
public final class Teleporter {
    private Teleporter() {}

    /** Search radius around the wanted position (blocks, every axis). */
    public static final int SAFE_RADIUS = 3;

    // ---- Safe spot ----

    private static final List<int[]> OFFSETS = new ArrayList<>();

    static {
        for (int dx = -SAFE_RADIUS; dx <= SAFE_RADIUS; dx++) {
            for (int dy = -SAFE_RADIUS; dy <= SAFE_RADIUS; dy++) {
                for (int dz = -SAFE_RADIUS; dz <= SAFE_RADIUS; dz++) {
                    OFFSETS.add(new int[]{dx, dy, dz});
                }
            }
        }
        // nearest first, horizontal moves before vertical ones on equal distance
        OFFSETS.sort(Comparator.<int[]>comparingInt(o -> o[0] * o[0] + o[1] * o[1] + o[2] * o[2]).thenComparingInt(o -> Math.abs(o[1])));
    }

    /** True when an entity can stand in the block: no collision, no fluid and nothing that hurts or traps. */
    public static boolean isPassable(BlockGetter level, BlockPos pos, BlockState state) {
        if (!state.getCollisionShape(level, pos).isEmpty() || !state.getFluidState().isEmpty()) return false;
        return !(state.is(BlockTags.FIRE) || state.is(Blocks.COBWEB) || state.is(Blocks.POWDER_SNOW) || state.is(Blocks.SWEET_BERRY_BUSH)
                || state.is(Blocks.NETHER_PORTAL) || state.is(Blocks.END_PORTAL) || state.is(Blocks.END_GATEWAY)
                || state.is(WarpRegistry.GATE_PORTAL.get()));
    }

    private static boolean isStandable(BlockGetter level, BlockPos pos, BlockState state) {
        if (state.is(Blocks.MAGMA_BLOCK) || state.is(Blocks.CACTUS) || state.is(BlockTags.CAMPFIRES) || state.is(Blocks.WITHER_ROSE)) return false;
        VoxelShape shape = state.getCollisionShape(level, pos);
        return !shape.isEmpty() && shape.max(net.minecraft.core.Direction.Axis.Y) <= 1.0 && state.getFluidState().isEmpty();
    }

    /** Feet position when standing in cell {@code feet} (two free blocks, solid ground below), or null. */
    @Nullable
    public static Vec3 standingSpot(BlockGetter level, BlockPos feet) {
        if (feet.getY() <= level.getMinBuildHeight() || feet.getY() + 1 >= level.getMaxBuildHeight()) return null;
        BlockState feetState = level.getBlockState(feet);
        BlockPos headPos = feet.above();
        BlockPos groundPos = feet.below();
        BlockState ground = level.getBlockState(groundPos);
        if (!isPassable(level, feet, feetState) || !isPassable(level, headPos, level.getBlockState(headPos))) return null;
        if (!isStandable(level, groundPos, ground)) return null;
        double top = ground.getCollisionShape(level, groundPos).max(net.minecraft.core.Direction.Axis.Y);
        return new Vec3(feet.getX() + 0.5, groundPos.getY() + top, feet.getZ() + 0.5);
    }

    /** Nearest safe 2-high spot with solid ground within {@link #SAFE_RADIUS} blocks of {@code center}. Centre of the block. */
    public static Optional<Vec3> findSafeSpot(BlockGetter level, BlockPos center) {
        for (int[] o : OFFSETS) {
            Vec3 spot = standingSpot(level, center.offset(o[0], o[1], o[2]));
            if (spot != null) return Optional.of(spot);
        }
        return Optional.empty();
    }

    /** Loads the chunks the safe spot search may touch, so the blocks it reads are real. */
    public static void loadAround(ServerLevel level, BlockPos center) {
        int minX = (center.getX() - SAFE_RADIUS) >> 4, maxX = (center.getX() + SAFE_RADIUS) >> 4;
        int minZ = (center.getZ() - SAFE_RADIUS) >> 4, maxZ = (center.getZ() + SAFE_RADIUS) >> 4;
        for (int cx = minX; cx <= maxX; cx++) {
            for (int cz = minZ; cz <= maxZ; cz++) {
                level.getChunk(cx, cz);
            }
        }
    }

    /** Loads the destination chunks and returns the safe spot near {@code center}, honouring the world border. */
    public static Optional<Vec3> prepareArrival(ServerLevel level, BlockPos center) {
        loadAround(level, center);
        Optional<Vec3> spot = findSafeSpot(level, center);
        if (spot.isPresent() && !level.getWorldBorder().isWithinBounds(BlockPos.containing(spot.get()))) return Optional.empty();
        return spot;
    }

    // ---- Moving ----

    /** True for entities that may be sent through a gate. Players in spectator mode and riders are skipped. */
    public static boolean canTeleport(Entity entity) {
        return entity.isAlive() && !entity.isRemoved() && !entity.isPassenger() && !entity.isVehicle() && !entity.isSpectator();
    }

    /**
     * Moves the entity. Players use {@code teleportTo}, everything else {@code changeDimension}. Returns the entity that
     * stands at the destination (a copy for other dimensions) or null when the move was cancelled.
     */
    @Nullable
    public static Entity teleport(Entity entity, ServerLevel destination, Vec3 pos, float yaw, float pitch) {
        if (entity instanceof ServerPlayer player) {
            if (player.connection == null) return null;
            player.teleportTo(destination, pos.x, pos.y, pos.z, yaw, pitch);
            player.resetFallDistance();
            boolean arrived = player.level() == destination && player.position().distanceToSqr(pos) < 4.0;
            return arrived ? player : null;
        }
        Entity moved = entity.changeDimension(new DimensionTransition(destination, pos, Vec3.ZERO, yaw, entity.getXRot(), DimensionTransition.DO_NOTHING));
        if (moved != null) moved.resetFallDistance();
        return moved;
    }

    // ---- Effects ----

    /** Ender sound and particles around an entity position. */
    public static void departEffects(ServerLevel level, Vec3 pos, boolean quiet) {
        level.sendParticles(ParticleTypes.PORTAL, pos.x, pos.y + 1.0, pos.z, 40, 0.3, 0.8, 0.3, 0.2);
        level.playSound(null, pos.x, pos.y, pos.z, quiet ? SoundEvents.PORTAL_TRAVEL : SoundEvents.ENDERMAN_TELEPORT, SoundSource.PLAYERS,
                quiet ? 0.25F : 1.0F, quiet ? 1.2F : 1.0F);
    }

    public static void arriveEffects(ServerLevel level, Vec3 pos, boolean quiet) {
        level.sendParticles(ParticleTypes.REVERSE_PORTAL, pos.x, pos.y + 1.0, pos.z, 40, 0.3, 0.8, 0.3, 0.1);
        level.playSound(null, pos.x, pos.y, pos.z, quiet ? SoundEvents.PORTAL_TRAVEL : SoundEvents.ENDERMAN_TELEPORT, SoundSource.PLAYERS,
                quiet ? 0.25F : 1.0F, quiet ? 1.4F : 1.2F);
    }
}
