package com.arno.robotica.gear.weapon;

import com.arno.robotica.core.energy.EnergyItem;
import com.arno.robotica.core.energy.ItemEnergy;
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

    /** FE for one hit or shot. */
    public int cost() {
        return cost.getAsInt();
    }

    public boolean hasCharge(ItemStack stack) {
        return ItemEnergy.get(stack) >= cost();
    }

    /** Pays one use. Creative players use no energy. Returns false (and pays nothing) when there is not enough. */
    protected boolean pay(ItemStack stack, LivingEntity user) {
        if (user instanceof Player p && p.getAbilities().instabuild) return true;
        return ItemEnergy.tryUse(stack, cost());
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
        tooltip.add(Component.translatable(getDescriptionId() + ".tooltip", cost()).withStyle(ChatFormatting.GRAY));
        ItemEnergy.appendTooltip(stack, tooltip);
    }
}
