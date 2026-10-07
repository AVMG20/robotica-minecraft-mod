package com.arno.robotica.boss.entity;

import net.minecraft.world.entity.PathfinderMob;
import net.minecraft.world.entity.ai.goal.target.HurtByTargetGoal;
import net.minecraft.world.entity.player.Player;

/** Turns on whoever hurt the boss, but a mob only when no player is in range: players keep the boss's attention. */
public class BossHurtByTargetGoal extends HurtByTargetGoal {
    public BossHurtByTargetGoal(PathfinderMob mob, Class<?>... ignore) {
        super(mob, ignore);
    }

    @Override
    public boolean canUse() {
        if (!(mob.getLastHurtByMob() instanceof Player) && BossRules.playerNear(mob, getFollowDistance())) return false;
        return super.canUse();
    }
}
