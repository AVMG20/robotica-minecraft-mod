package com.arno.robotica.gear.weapon;

import com.arno.robotica.Robotica;
import com.arno.robotica.gear.GearConfig;
import com.arno.robotica.gear.entity.RivetEntity;
import com.arno.robotica.gear.module.GearModuleKind;
import com.arno.robotica.gear.module.GearModules;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.decoration.ArmorStand;
import net.minecraft.world.item.ItemStack;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.common.damagesource.DamageContainer;
import net.neoforged.neoforge.event.entity.living.LivingDamageEvent;
import net.neoforged.neoforge.event.entity.living.LivingIncomingDamageEvent;

/**
 * Server hooks of the weapon modules: Armor Pierce scales the armor reduction of a paid hit down, Lifesteal heals after
 * a paid hit landed. "Paid" means a use that cost FE: melee of the Shock Baton and Arc Blade (and the arcs), a rivet,
 * the Null Lance beam. Unpaid bumps (Rivet Gun or Lance used as a club) get no module effects.
 */
@EventBusSubscriber(modid = Robotica.MODID)
public final class WeaponModuleEvents {
    private WeaponModuleEvents() {}

    @SubscribeEvent
    public static void onIncoming(LivingIncomingDamageEvent event) {
        float share = pierceShare(event.getSource());
        if (share <= 0.0F) return;
        event.getContainer().addModifier(DamageContainer.Reduction.ARMOR, (container, reduction) -> reduction * (1.0F - share));
    }

    @SubscribeEvent
    public static void onDamaged(LivingDamageEvent.Post event) {
        DamageSource source = event.getSource();
        if (!(source.getEntity() instanceof ServerPlayer player)) return;
        LivingEntity target = event.getEntity();
        if (target == player || target instanceof ArmorStand) return;
        ItemStack weapon = paidWeapon(source);
        if (weapon.isEmpty()) return;
        Lifesteal.onHit(player, weapon, target, event.getNewDamage());
    }

    /** Share of the armor reduction the hit ignores (0 when no Armor Pierce applies). */
    public static float pierceShare(DamageSource source) {
        if (source.getDirectEntity() instanceof RivetEntity rivet) return rivet.pierce();
        ItemStack weapon = paidWeapon(source);
        if (weapon.isEmpty()) return 0.0F;
        return (float) GearConfig.pierceShare(GearModules.active(weapon, GearModuleKind.ARMOR_PIERCE));
    }

    /** The FE weapon in the attacker's main hand when this damage came from a paid use of it, else EMPTY. */
    public static ItemStack paidWeapon(DamageSource source) {
        if (!(source.getEntity() instanceof LivingEntity attacker) || source.getDirectEntity() != attacker) return ItemStack.EMPTY;
        ItemStack weapon = attacker.getMainHandItem();
        if (!(weapon.getItem() instanceof EnergyWeaponItem item)) return ItemStack.EMPTY;
        if (item instanceof NullLanceItem) return NullLanceItem.firing() ? weapon : ItemStack.EMPTY;
        // the Arc Blade's arcs belong to the swing that paid for them, even when that payment emptied the blade
        if (ArcBladeItem.arcing(weapon)) return weapon;
        return item.paidMelee() && (item.hasCharge(weapon) || isCreative(attacker)) ? weapon : ItemStack.EMPTY;
    }

    private static boolean isCreative(LivingEntity entity) {
        return entity instanceof net.minecraft.world.entity.player.Player p && p.getAbilities().instabuild;
    }
}
