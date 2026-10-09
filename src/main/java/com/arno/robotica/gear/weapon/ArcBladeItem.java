package com.arno.robotica.gear.weapon;

import com.arno.robotica.core.module.ModuleTarget;
import com.arno.robotica.core.CoreSounds;
import com.arno.robotica.core.energy.ItemEnergy;
import com.arno.robotica.gear.GearConfig;
import com.arno.robotica.gear.GearFxPayload;
import com.arno.robotica.core.module.ModuleKind;
import com.arno.robotica.core.module.Modules;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.monster.Enemy;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.phys.Vec3;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.function.IntSupplier;

/**
 * Age 3 melee weapon: 11 damage, arcs 50% of it to up to 3 other hostile mobs, each arc jumping from the last target,
 * 800 FE per hit. The Chain Lightning module adds arcs (paid per extra arc that lands) and jump range.
 */
public class ArcBladeItem extends EnergyWeaponItem {
    public static final float BASE_DAMAGE = 11.0F;
    public static final int CHAIN_TARGETS = 3;
    public static final double CHAIN_RANGE = 6.0;

    public ArcBladeItem(Properties props, IntSupplier capacity, IntSupplier cost) {
        super(props, capacity, cost, 3, ModuleTarget.ARC_BLADE);
    }

    @Override
    public boolean paidMelee() {
        return true;
    }

    /** The blade whose paid swing is arcing right now (server thread), so its arcs count as paid whatever FE is left. */
    private static ItemStack arcing = ItemStack.EMPTY;

    static boolean arcing(ItemStack stack) {
        return !arcing.isEmpty() && arcing == stack;
    }

    @Override
    public boolean hurtEnemy(ItemStack stack, LivingEntity target, LivingEntity attacker) {
        if (!pay(stack, attacker) || !(attacker.level() instanceof ServerLevel level)) return true;
        int chain = Modules.active(stack, ModuleKind.CHAIN_LIGHTNING);
        int arcs = CHAIN_TARGETS + GearConfig.chainExtraArcs(chain);
        double range = CHAIN_RANGE + GearConfig.chainRangeBonus(chain);
        DamageSource source = attacker instanceof Player p ? level.damageSources().playerAttack(p) : level.damageSources().mobAttack(attacker);
        boolean creative = attacker instanceof Player p && p.getAbilities().instabuild;
        List<LivingEntity> done = new ArrayList<>();
        done.add(target);
        float[] struck = new float[3 * (arcs + 1)];
        put(struck, 0, target);
        LivingEntity from = target;
        int hit = 0;
        arcing = stack;
        try {
            while (hit < arcs) {
                LivingEntity next = nextTarget(level, from, attacker, done, range);
                if (next == null) break;
                // Arcs beyond the blade's own 3 come from Chain Lightning and cost extra.
                if (hit >= CHAIN_TARGETS && !creative && !ItemEnergy.tryUse(stack, Modules.regulated(stack, GearConfig.chainCostPerArc()))) break;
                done.add(next);
                next.hurt(source, BASE_DAMAGE * 0.5F);
                put(struck, hit + 1, next);
                from = next;
                hit++;
            }
        } finally {
            arcing = ItemStack.EMPTY;
        }
        // One packet for the whole chain (each client draws the bolts) and one strike sound that rises with each arc.
        float[] points = hit == arcs ? struck : java.util.Arrays.copyOf(struck, 3 * (hit + 1));
        GearFxPayload.send(level, target.getX(), target.getY(), target.getZ(), new GearFxPayload(GearFxPayload.ARC_CHAIN, points, new int[0]));
        CoreSounds.play(level, target.blockPosition(), CoreSounds.ARC_STRIKE, SoundSource.PLAYERS, 0.8F + 0.05F * hit,
                0.95F + 0.08F * hit + level.random.nextFloat() * 0.06F);
        return true;
    }

    private static void put(float[] chain, int i, LivingEntity e) {
        Vec3 c = e.getBoundingBox().getCenter();
        chain[i * 3] = (float) c.x;
        chain[i * 3 + 1] = (float) c.y;
        chain[i * 3 + 2] = (float) c.z;
    }

    /** The nearest living hostile within {@code range} of {@code from} that has not been hit yet, or null. */
    private static LivingEntity nextTarget(ServerLevel level, LivingEntity from, LivingEntity attacker, List<LivingEntity> done, double range) {
        double r2 = range * range;
        return level.getEntitiesOfClass(LivingEntity.class, from.getBoundingBox().inflate(range),
                        e -> e != attacker && !done.contains(e) && e.isAlive() && e instanceof Enemy && e.distanceToSqr(from) <= r2)
                .stream().min(Comparator.comparingDouble(e -> e.distanceToSqr(from))).orElse(null);
    }
}
