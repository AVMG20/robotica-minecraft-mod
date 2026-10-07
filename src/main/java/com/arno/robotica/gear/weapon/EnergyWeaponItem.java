package com.arno.robotica.gear.weapon;

import com.arno.robotica.core.energy.EnergyItem;
import com.arno.robotica.core.energy.ItemEnergy;
import com.arno.robotica.core.module.ModuleHolder;
import com.arno.robotica.core.module.ModuleKind;
import com.arno.robotica.core.module.ModuleTarget;
import com.arno.robotica.core.module.Modules;
import com.arno.robotica.gear.GearConfig;
import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Holder;
import net.minecraft.core.HolderLookup;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.item.enchantment.Enchantment;
import net.minecraft.world.item.enchantment.Enchantments;
import net.minecraft.world.item.enchantment.ItemEnchantments;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;

import java.util.List;
import java.util.function.IntSupplier;

/**
 * FE powered weapon. With less energy than one use it falls back to 1 damage (see GearEvents attribute hook). Takes
 * modules instead of enchantments (Sharpened Edge, Looting, Thermal Edge...).
 */
public abstract class EnergyWeaponItem extends Item implements EnergyItem, ModuleHolder {
    private final IntSupplier capacity;
    private final IntSupplier cost;
    private final int age;
    private final ModuleTarget target;

    protected EnergyWeaponItem(Properties props, IntSupplier capacity, IntSupplier cost, int age, ModuleTarget target) {
        super(props.stacksTo(1));
        this.capacity = capacity;
        this.cost = cost;
        this.age = age;
        this.target = target;
    }

    /** Base FE for one hit or shot, without modules. */
    public int cost() {
        return cost.getAsInt();
    }

    /** FE for one hit or shot of this stack: the base cost plus what its switched-on modules add, after the Power Regulator. */
    public int cost(ItemStack stack) {
        int fe = cost() + GearConfig.pierceCost(Modules.active(stack, ModuleKind.ARMOR_PIERCE))
                + GearConfig.ricochetCost(Modules.active(stack, ModuleKind.RICOCHET))
                + GearConfig.edgeCost(Modules.active(stack, ModuleKind.SHARPENED_EDGE))
                + GearConfig.thermalCost(Modules.active(stack, ModuleKind.THERMAL_EDGE));
        return Modules.regulated(stack, fe);
    }

    public int age() {
        return age;
    }

    @Override
    public ModuleTarget moduleTarget(ItemStack stack) {
        return target;
    }

    @Override
    public int moduleTier(ItemStack stack) {
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
        return capacity.getAsInt();
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

    // ---- no enchanting: FE weapons take modules ----

    @Override
    public boolean isEnchantable(ItemStack stack) {
        return false;
    }

    @Override
    public int getEnchantmentValue() {
        return 0;
    }

    @Override
    public boolean supportsEnchantment(ItemStack stack, Holder<Enchantment> enchantment) {
        return false;
    }

    @Override
    public boolean isBookEnchantable(ItemStack stack, ItemStack book) {
        return false;
    }

    @Override
    public boolean isPrimaryItemFor(ItemStack stack, Holder<Enchantment> enchantment) {
        return false;
    }

    /** The switched-on Looting module counts as Looting for mob drops while the weapon has FE. */
    @Override
    public int getEnchantmentLevel(ItemStack stack, Holder<Enchantment> enchantment) {
        // Only the Looting module counts; enchantments smithed over from the Gearblade do nothing.
        return enchantment.is(Enchantments.LOOTING) ? Modules.powered(stack, ModuleKind.LOOTING) : 0;
    }

    @Override
    public ItemEnchantments getAllEnchantments(ItemStack stack, HolderLookup.RegistryLookup<Enchantment> lookup) {
        int looting = Modules.powered(stack, ModuleKind.LOOTING);
        ItemEnchantments.Mutable all = new ItemEnchantments.Mutable(ItemEnchantments.EMPTY);
        if (looting > 0) all.set(lookup.getOrThrow(Enchantments.LOOTING), looting);
        return all.toImmutable();
    }

    @Override
    public void inventoryTick(ItemStack stack, Level level, net.minecraft.world.entity.Entity entity, int slot, boolean selected) {
        super.inventoryTick(stack, level, entity, slot, selected);
        // Enchantments smithed over from the Gearblade do nothing on FE weapons: clear them.
        if (!level.isClientSide && stack.has(net.minecraft.core.component.DataComponents.ENCHANTMENTS)) {
            stack.remove(net.minecraft.core.component.DataComponents.ENCHANTMENTS);
        }
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
        var modules = Modules.describe(stack);
        tooltip.add(modules != null ? Component.translatable("tooltip.robotica.gear.modules", modules).withStyle(ChatFormatting.GRAY)
                : Component.translatable("tooltip.robotica.gear.weapon_modules", Modules.slots(stack)).withStyle(ChatFormatting.DARK_GRAY));
        ItemEnergy.appendTooltip(stack, tooltip);
    }

    /** Charging from a cell in the inventory only changes the energy: no equip animation every few ticks. */
    @Override
    public boolean shouldCauseReequipAnimation(ItemStack oldStack, ItemStack newStack, boolean slotChanged) {
        return slotChanged || !ItemEnergy.onlyEnergyChanged(oldStack, newStack);
    }
}
