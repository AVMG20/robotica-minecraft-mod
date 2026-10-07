package com.arno.robotica.gear.weapon;

import com.arno.robotica.core.energy.EnergyItem;
import com.arno.robotica.core.energy.ItemEnergy;
import com.arno.robotica.gear.GearConfig;
import com.arno.robotica.gear.module.GearModuleKind;
import com.arno.robotica.gear.module.GearModules;
import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;

import java.util.List;
import java.util.function.IntSupplier;

/** FE powered weapon. With less energy than one use it falls back to 1 damage (see GearEvents attribute hook). */
public abstract class EnergyWeaponItem extends Item implements EnergyItem {
    private final int capacity;
    private final IntSupplier cost;
    private final int age;

    protected EnergyWeaponItem(Properties props, int capacity, IntSupplier cost, int age) {
        super(props.stacksTo(1));
        this.capacity = capacity;
        this.cost = cost;
        this.age = age;
    }

    /** Base FE for one hit or shot, without modules. */
    public int cost() {
        return cost.getAsInt();
    }

    /** FE for one hit or shot of this stack: the base cost plus what its switched-on modules add per use. */
    public int cost(ItemStack stack) {
        return cost() + GearConfig.pierceCost(GearModules.active(stack, GearModuleKind.ARMOR_PIERCE))
                + GearConfig.ricochetCost(GearModules.active(stack, GearModuleKind.RICOCHET));
    }

    public int age() {
        return age;
    }

    /** True when a melee hit with this weapon costs FE (Shock Baton, Arc Blade); only those get module effects in melee. */
    public boolean paidMelee() {
        return false;
    }

    public boolean hasCharge(ItemStack stack) {
        return ItemEnergy.get(stack) >= cost(stack);
    }

    /** Pays one use. Creative players use no energy. Returns false (and pays nothing) when there is not enough. */
    protected boolean pay(ItemStack stack, LivingEntity user) {
        if (user instanceof Player p && p.getAbilities().instabuild) return true;
        return ItemEnergy.tryUse(stack, cost(stack));
    }

    @Override
    public int getEnergyCapacity(ItemStack stack) {
        return capacity;
    }

    @Override
    public int getMaxExtract(ItemStack stack) {
        return 0;
    }

    @Override
    public boolean isBarVisible(ItemStack stack) {
        return true;
    }

    @Override
    public int getBarWidth(ItemStack stack) {
        return ItemEnergy.barWidth(stack);
    }

    @Override
    public int getBarColor(ItemStack stack) {
        return ItemEnergy.BAR_COLOR;
    }

    /** Melee FE weapons (tag minecraft:enchantable/weapon) take Sharpness, Fire Aspect and friends; never Unbreaking or Mending. */
    @Override
    public boolean isEnchantable(ItemStack stack) {
        return stack.is(net.minecraft.tags.ItemTags.WEAPON_ENCHANTABLE);
    }

    @Override
    public int getEnchantmentValue() {
        return 12;
    }

    @Override
    public boolean supportsEnchantment(ItemStack stack, net.minecraft.core.Holder<net.minecraft.world.item.enchantment.Enchantment> enchantment) {
        if (enchantment.is(net.minecraft.world.item.enchantment.Enchantments.MENDING)
                || enchantment.is(net.minecraft.world.item.enchantment.Enchantments.UNBREAKING)) return false;
        return super.supportsEnchantment(stack, enchantment);
    }

    @Override
    public boolean canAttackBlock(BlockState state, Level level, BlockPos pos, Player player) {
        return !player.isCreative();
    }

    @Override
    public boolean hurtEnemy(ItemStack stack, LivingEntity target, LivingEntity attacker) {
        return true;
    }

    @Override
    public void appendHoverText(ItemStack stack, TooltipContext ctx, List<Component> tooltip, TooltipFlag flag) {
        tooltip.add(Component.translatable("tooltip.robotica.age", age, Component.translatable("age.robotica." + age))
                .withStyle(ChatFormatting.DARK_GRAY));
        tooltip.add(Component.translatable(getDescriptionId() + ".tooltip", cost(stack)).withStyle(ChatFormatting.GRAY));
        var modules = GearModules.describe(stack);
        tooltip.add(modules != null ? Component.translatable("tooltip.robotica.gear.modules", modules).withStyle(ChatFormatting.GRAY)
                : Component.translatable("tooltip.robotica.gear.weapon_modules", GearModules.slots(stack)).withStyle(ChatFormatting.DARK_GRAY));
        ItemEnergy.appendTooltip(stack, tooltip);
    }

    /** Charging from a cell in the inventory only changes the energy: no equip animation every few ticks. */
    @Override
    public boolean shouldCauseReequipAnimation(ItemStack oldStack, ItemStack newStack, boolean slotChanged) {
        return slotChanged || !ItemEnergy.onlyEnergyChanged(oldStack, newStack);
    }
}
