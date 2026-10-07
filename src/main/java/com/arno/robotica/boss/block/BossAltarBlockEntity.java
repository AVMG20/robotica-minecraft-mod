package com.arno.robotica.boss.block;

import com.arno.robotica.boss.BossRegistry;
import net.minecraft.core.BlockPos;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import org.jetbrains.annotations.Nullable;

import java.util.UUID;

/** Per-altar state: when it can be awakened again and which boss it woke last (one at a time). */
public class BossAltarBlockEntity extends BlockEntity {
    private long readyAt;
    @Nullable
    private UUID bossId;

    public BossAltarBlockEntity(BlockPos pos, BlockState state) {
        super(BossRegistry.BOSS_ALTAR_BE.get(), pos, state);
    }

    /** Ticks until the altar can be used again, 0 when ready. */
    public long cooldownLeft(long gameTime) {
        return Math.max(0L, readyAt - gameTime);
    }

    /** The boss this altar woke, if it is still alive and loaded. */
    @Nullable
    public Mob boss(ServerLevel level) {
        if (bossId == null) return null;
        return level.getEntity(bossId) instanceof Mob boss && boss.isAlive() ? boss : null;
    }

    public void awakened(Mob boss, long gameTime, int cooldownTicks) {
        this.bossId = boss.getUUID();
        this.readyAt = gameTime + cooldownTicks;
        setChanged();
    }

    /** Clears the cooldown (creative players, tests). */
    public void resetCooldown() {
        this.readyAt = 0L;
        setChanged();
    }

    @Override
    protected void saveAdditional(CompoundTag tag, HolderLookup.Provider registries) {
        super.saveAdditional(tag, registries);
        tag.putLong("ReadyAt", readyAt);
        if (bossId != null) tag.putUUID("Boss", bossId);
    }

    @Override
    protected void loadAdditional(CompoundTag tag, HolderLookup.Provider registries) {
        super.loadAdditional(tag, registries);
        readyAt = tag.getLong("ReadyAt");
        bossId = tag.hasUUID("Boss") ? tag.getUUID("Boss") : null;
    }
}
