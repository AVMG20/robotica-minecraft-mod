package com.arno.robotica.boss.entity;

import net.minecraft.core.BlockPos;
import org.jetbrains.annotations.Nullable;

/** A boss woken at an altar. Its loot is locked to the killer (BossModule#onDrops). */
public interface RoboticaBoss {
    /** Ties the boss to its altar: it stays within 20 blocks of it. */
    void setAltarPos(@Nullable BlockPos pos);

    @Nullable
    BlockPos altarPos();
}
