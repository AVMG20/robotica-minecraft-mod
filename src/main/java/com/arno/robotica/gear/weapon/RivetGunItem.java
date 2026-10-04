package com.arno.robotica.gear.weapon;

import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResultHolder;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.entity.projectile.AbstractArrow;
import net.minecraft.world.entity.projectile.Arrow;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;

import java.util.function.IntSupplier;

/** Age 2 ranged weapon. Right-click fires a fast vanilla arrow with 8 damage, no pickup, 4 shots per second, no ammo. */
public class RivetGunItem extends EnergyWeaponItem {
    public static final float VELOCITY = 4.5F;
    public static final double DAMAGE = 8.0;
    public static final int COOLDOWN_TICKS = 5;

    public RivetGunItem(Properties props, int capacity, IntSupplier cost) {
        super(props, capacity, cost, 2);
    }

    @Override
    public InteractionResultHolder<ItemStack> use(Level level, Player player, InteractionHand hand) {
        ItemStack gun = player.getItemInHand(hand);
        if (!hasCharge(gun) && !player.getAbilities().instabuild) {
            if (!level.isClientSide) {
                level.playSound(null, player.blockPosition(), SoundEvents.DISPENSER_FAIL, SoundSource.PLAYERS, 0.6F, 1.4F);
            }
            return InteractionResultHolder.fail(gun);
        }
        if (!level.isClientSide) {
            pay(gun, player);
            Arrow rivet = new Arrow(level, player, new ItemStack(Items.ARROW), null);
            rivet.pickup = AbstractArrow.Pickup.DISALLOWED;
            // Arrow damage is ceil(speed * baseDamage), so this lands at 8 right after leaving the barrel.
            rivet.setBaseDamage(DAMAGE / VELOCITY + 0.05);
            rivet.shootFromRotation(player, player.getXRot(), player.getYRot(), 0.0F, VELOCITY, 0.5F);
            level.addFreshEntity(rivet);
            level.playSound(null, player.blockPosition(), SoundEvents.CROSSBOW_SHOOT, SoundSource.PLAYERS, 0.8F, 1.7F);
            player.getCooldowns().addCooldown(this, COOLDOWN_TICKS);
        }
        player.swing(hand);
        return InteractionResultHolder.sidedSuccess(gun, level.isClientSide);
    }
}
