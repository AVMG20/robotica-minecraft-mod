package com.arno.robotica.architect.entity;

import com.arno.robotica.architect.block.ArchitectTableBlockEntity;
import com.arno.robotica.core.CoreSounds;
import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.syncher.SynchedEntityData;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.Vec3;

/**
 * Cosmetic builder drone. Never saved, never interacts. The table spawns one while it builds; it hovers above the table
 * and flies to the block being placed. It removes itself a couple of seconds after the table stops building.
 */
public class BuilderDrone extends Entity {
    private BlockPos home = BlockPos.ZERO;
    private Vec3 target;
    private int idleTicks;

    public BuilderDrone(EntityType<? extends BuilderDrone> type, Level level) {
        super(type, level);
        this.noPhysics = true;
    }

    public void setHome(BlockPos home) {
        this.home = home;
    }

    /** Block position being placed; the drone hovers just above it. */
    public void setTarget(BlockPos pos) {
        this.target = Vec3.atCenterOf(pos).add(0, 1.1, 0);
    }

    @Override
    protected void defineSynchedData(SynchedEntityData.Builder builder) {
    }

    @Override
    public void tick() {
        super.tick();
        if (level().isClientSide) {
            // Follow the motion the server sends between position updates.
            Vec3 m = getDeltaMovement();
            setPos(getX() + m.x, getY() + m.y, getZ() + m.z);
            return;
        }
        boolean building = level().isLoaded(home)
                && level().getBlockEntity(home) instanceof ArchitectTableBlockEntity table && table.isBuilding();
        if (building) {
            idleTicks = 0;
        } else if (++idleTicks > 40) {
            discard();
            return;
        }
        if (building && tickCount % 50 == 0) {
            CoreSounds.play(this, CoreSounds.DRONE_BUZZ, SoundSource.NEUTRAL, 0.5F, 1.0F);
        }
        Vec3 goal = building && target != null ? target : Vec3.atCenterOf(home).add(0, 1.6, 0);
        Vec3 to = goal.subtract(position());
        double dist = to.length();
        Vec3 motion = dist < 0.05 ? Vec3.ZERO : to.scale(Math.min(0.35, 0.8 / Math.max(dist, 0.001)) * Math.min(1.0, dist * 0.5));
        setDeltaMovement(motion);
        setPos(getX() + motion.x, getY() + motion.y, getZ() + motion.z);
        if (motion.lengthSqr() > 1.0E-4) {
            setYRot((float) (Math.toDegrees(Math.atan2(-motion.x, motion.z))));
        }
        hasImpulse = true;
    }

    @Override
    public boolean isPickable() {
        return false;
    }

    @Override
    public boolean isPushedByFluid() {
        return false;
    }

    @Override
    public boolean isInvulnerableTo(DamageSource source) {
        return true;
    }

    @Override
    public boolean shouldBeSaved() {
        return false;
    }

    @Override
    protected void readAdditionalSaveData(CompoundTag tag) {
    }

    @Override
    protected void addAdditionalSaveData(CompoundTag tag) {
    }
}
