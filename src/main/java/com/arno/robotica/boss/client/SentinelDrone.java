package com.arno.robotica.boss.client;

import com.arno.robotica.boss.entity.NullSentinel;
import net.minecraft.client.resources.sounds.AbstractTickableSoundInstance;
import net.minecraft.client.resources.sounds.SoundInstance;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.Mth;

/**
 * The low drone of an awake Null Sentinel: the beacon hum, slowed down, looping on the boss. It swells in as it wakes,
 * sinks while it recharges, climbs a little each phase and dies with it.
 */
final class SentinelDrone extends AbstractTickableSoundInstance {
    private final NullSentinel boss;
    private float level;

    SentinelDrone(NullSentinel boss) {
        super(SoundEvents.BEACON_AMBIENT, SoundSource.HOSTILE, SoundInstance.createUnseededRandom());
        this.boss = boss;
        this.looping = true;
        this.delay = 0;
        this.volume = 0.01F;
        this.pitch = 0.5F;
        this.x = boss.getX();
        this.y = boss.getY() + NullSentinel.CORE_HEIGHT;
        this.z = boss.getZ();
    }

    @Override
    public void tick() {
        if (boss.isRemoved() || boss.isDeadOrDying()) {
            stop();
            return;
        }
        x = boss.getX();
        y = boss.getY() + NullSentinel.CORE_HEIGHT;
        z = boss.getZ();
        NullSentinel.Action a = boss.action();
        float target = switch (a) {
            case INTRO -> Mth.clamp(boss.actionTicks() / (float) NullSentinel.INTRO_TICKS, 0.0F, 1.0F) * 1.4F;
            case RECHARGE -> 0.5F;
            case SHIFT, WELL, BEAM -> 1.8F;
            default -> 1.4F;
        };
        level += (target - level) * 0.08F;
        volume = Math.max(0.01F, level);
        pitch = 0.5F + 0.07F * (boss.phase() - 1) + (a == NullSentinel.Action.RECHARGE ? -0.05F : 0.0F);
    }

    @Override
    public boolean canStartSilent() {
        return true;
    }
}
