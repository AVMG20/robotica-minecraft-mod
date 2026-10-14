package com.arno.robotica.gear.weapon;

import com.arno.robotica.core.module.ModuleTarget;
import com.arno.robotica.core.CoreSounds;
import com.arno.robotica.core.energy.ItemEnergy;
import com.arno.robotica.gear.GearConfig;
import com.arno.robotica.gear.entity.RivetEntity;
import com.arno.robotica.core.module.ModuleKind;
import com.arno.robotica.core.module.Modules;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.entity.HumanoidArm;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.phys.Vec3;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import java.util.function.IntSupplier;

/**
 * Age 2 ranged weapon. Left-click (hold to keep firing) fires a glowing steel rivet ({@link RivetEntity}) for
 * {@link GearConfig#rivetDamage} damage every {@link GearConfig#rivetCooldown} ticks, no ammo. The client turns the
 * attack key into GearActions.FIRE (RivetGunClient), so the gun never melees or breaks blocks. Muzzle flash (kept out of the shooter's view), a punchy layered shot sound. Armor Pierce and Ricochet Rivets ride
 * along on the rivet. Its upgrade, the {@link NullLanceItem}, fires the same way (null bolts) through {@link #trigger}.
 */
public class RivetGunItem extends EnergyWeaponItem {
    public RivetGunItem(Properties props, IntSupplier capacity, IntSupplier cost) {
        super(props, capacity, cost, 2, ModuleTarget.RIVET_GUN);
    }

    /** Ticks after a dry click before the gun clicks again. */
    public static final int EMPTY_TICKS = 8;
    /** A shot may arrive this many ticks early (client and server ticks drift); the next one still counts from the planned tick. */
    private static final int SLACK = 2;
    /** Game time of each player's next allowed shot. */
    private static final Map<UUID, Long> NEXT_SHOT = new HashMap<>();

    /** True for the items the attack key fires: the Rivet Gun and the Null Lance. */
    public static boolean firesOnClick(ItemStack stack) {
        return stack.getItem() instanceof RivetGunItem || stack.getItem() instanceof NullLanceItem;
    }

    /** FE for one left-click shot of this gun: a rivet, or a null bolt from the Null Lance. */
    public static int shotCost(ItemStack gun) {
        if (gun.getItem() instanceof NullLanceItem) return NullLanceItem.boltCost(gun);
        return gun.getItem() instanceof EnergyWeaponItem item ? item.cost(gun) : 0;
    }

    /**
     * A left-click from the client (GearActions.FIRE): fires the gun in the main hand when it is ready, or clicks when it
     * is empty. The client keeps its own cooldown for the hotbar; this check keeps the fire rate on the server. The
     * Null Lance fires nothing while it charges its beam or cools down from one.
     */
    public static void trigger(ServerPlayer player) {
        ItemStack gun = player.getMainHandItem();
        if (!firesOnClick(gun) || player.isSpectator() || !player.isAlive()) return;
        if (player.isUsingItem() || player.getCooldowns().isOnCooldown(gun.getItem())) return;
        long now = player.serverLevel().getGameTime();
        Long next = NEXT_SHOT.get(player.getUUID());
        if (next != null && now < next - SLACK) return;
        long start = next == null ? now : Math.max(now, next);
        boolean creative = player.getAbilities().instabuild;
        int fe = shotCost(gun);
        if (!creative && !ItemEnergy.tryUse(gun, fe)) {
            CoreSounds.play(player.level(), player.blockPosition(), CoreSounds.RIVET_EMPTY, SoundSource.PLAYERS, 0.6F, 1.0F);
            NEXT_SHOT.put(player.getUUID(), start + EMPTY_TICKS);
            return;
        }
        fire(player.serverLevel(), player, gun);
        NEXT_SHOT.put(player.getUUID(), start + GearConfig.rivetCooldown());
    }

    public static void forget(UUID player) {
        NEXT_SHOT.remove(player);
    }

    public static void clearAll() {
        NEXT_SHOT.clear();
    }

    /** Spawns one rivet from the player's eyes along the look direction, with the gun's modules. Returns it. */
    public static RivetEntity fire(ServerLevel level, Player player, ItemStack gun) {
        boolean bolt = gun.getItem() instanceof NullLanceItem;
        float pierce = (float) GearConfig.pierceShare(Modules.active(gun, ModuleKind.ARMOR_PIERCE));
        int bounces = GearConfig.ricochetBounces(Modules.active(gun, ModuleKind.RICOCHET));
        float base = bolt ? NullLanceItem.boltDamage() : GearConfig.rivetDamage();
        float damage = base * (float) (1.0 + GearConfig.edgeDamage(Modules.active(gun, ModuleKind.SHARPENED_EDGE)));
        RivetEntity rivet = new RivetEntity(level, player, damage, pierce, bounces);
        if (bolt) rivet.setNullBolt();
        if (Modules.active(gun, ModuleKind.THERMAL_EDGE) > 0) rivet.setFireSeconds(GearConfig.thermalSeconds());
        rivet.shootFromRotation(player, player.getXRot(), player.getYRot(), 0.0F, GearConfig.rivetSpeed(), 0.5F);
        level.addFreshEntity(rivet);
        muzzleFlash(level, player, gun, bolt ? com.arno.robotica.core.particle.CoreParticles.WARP_VIOLET : com.arno.robotica.core.particle.CoreParticles.EMBER);
        float jitter = 0.92F + level.random.nextFloat() * 0.16F;
        CoreSounds.play(level, player.blockPosition(), CoreSounds.RIVET_SHOT, SoundSource.PLAYERS, 0.8F, (bolt ? 0.8F : 1.0F) * jitter);
        if (bolt) {
            // a lower shot with a warped hum on top: same gun family, heavier
            level.playSound(null, player.getX(), player.getY(), player.getZ(), SoundEvents.FIREWORK_ROCKET_BLAST, SoundSource.PLAYERS, 0.35F, 1.3F * jitter);
            level.playSound(null, player.getX(), player.getY(), player.getZ(), SoundEvents.AMETHYST_BLOCK_RESONATE, SoundSource.PLAYERS, 0.5F, 1.8F * jitter);
        } else {
            level.playSound(null, player.getX(), player.getY(), player.getZ(), SoundEvents.FIREWORK_ROCKET_BLAST, SoundSource.PLAYERS, 0.35F, 1.7F * jitter);
            level.playSound(null, player.getX(), player.getY(), player.getZ(), SoundEvents.CROSSBOW_SHOOT, SoundSource.PLAYERS, 0.4F, 1.5F * jitter);
        }
        return rivet;
    }

    /**
     * Muzzle flash at the gun. Other players see flame, sparks and smoke at the gun in the hand; the shooter only gets
     * a few sparks out past their own gun, so nothing covers the crosshair.
     */
    private static void muzzleFlash(ServerLevel level, Player player, ItemStack gun, int color) {
        Vec3 look = player.getLookAngle();
        boolean rightArm = (player.getMainHandItem() == gun) == (player.getMainArm() == HumanoidArm.RIGHT);
        Vec3 side = Vec3.directionFromRotation(0, player.getYRot() + (rightArm ? 90 : -90));
        Vec3 held = player.getEyePosition().add(look.scale(0.8)).add(side.scale(0.35)).add(0, -0.45, 0);
        Vec3 own = player.getEyePosition().add(look.scale(1.8)).add(side.scale(0.3)).add(0, -0.3, 0);
        for (ServerPlayer viewer : level.players()) {
            if (viewer == player) {
                level.sendParticles(viewer, com.arno.robotica.core.particle.CoreParticles.spark(color, 0.8F), false, own.x, own.y, own.z, 3, 0.03, 0.03, 0.03, 0.08);
            } else if (viewer.distanceToSqr(player) < 64 * 64) {
                level.sendParticles(viewer, com.arno.robotica.core.particle.CoreParticles.flash(color, 0.5F), false, held.x, held.y, held.z, 1, 0, 0, 0, 0);
                level.sendParticles(viewer, com.arno.robotica.core.particle.CoreParticles.spark(color, 0.8F), false, held.x, held.y, held.z, 4, 0.05, 0.05, 0.05, 0.12);
                level.sendParticles(viewer, com.arno.robotica.core.particle.CoreParticles.puff(com.arno.robotica.core.particle.CoreParticles.SMOKE, 0.6F), false, held.x, held.y, held.z, 2, 0.03, 0.03, 0.03, 0.01);
            }
        }
    }
}
