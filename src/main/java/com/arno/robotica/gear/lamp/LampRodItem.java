package com.arno.robotica.gear.lamp;

import com.arno.robotica.core.energy.EnergyItem;
import com.arno.robotica.core.energy.ItemEnergy;
import com.arno.robotica.gear.GearBlocks;
import com.arno.robotica.gear.GearConfig;
import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.item.context.UseOnContext;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;

import java.util.List;

/**
 * Lamp Rod (Age 1): a small FE tool with its own battery. Right-click a block face to put a Spark Lamp there for
 * {@link GearConfig#rodCost} FE; sneak-right-click a lamp to take it away (free). Charges like any FE item.
 */
public class LampRodItem extends Item implements EnergyItem {
    public LampRodItem(Properties props) {
        super(props.stacksTo(1));
    }

    @Override
    public InteractionResult useOn(UseOnContext ctx) {
        Level level = ctx.getLevel();
        BlockPos clicked = ctx.getClickedPos();
        BlockState state = level.getBlockState(clicked);
        boolean remove = ctx.isSecondaryUseActive() && state.is(GearBlocks.SPARK_LAMP.get());
        if (level.isClientSide) return InteractionResult.SUCCESS;
        if (!(ctx.getPlayer() instanceof ServerPlayer player) || !(level instanceof ServerLevel server)) return InteractionResult.PASS;
        ItemStack stack = ctx.getItemInHand();
        if (remove) return SparkLamps.remove(player, server, clicked) ? InteractionResult.CONSUME : InteractionResult.FAIL;
        return use(player, server, stack, clicked, ctx.getClickedFace()) ? InteractionResult.CONSUME : InteractionResult.FAIL;
    }

    /**
     * Places a lamp on face {@code face} of {@code clicked} (or in {@code clicked} itself when it is replaceable, like
     * grass) and pays for it. Creative players pay nothing. A refusal plays a dull click.
     */
    public boolean use(ServerPlayer player, ServerLevel level, ItemStack stack, BlockPos clicked, Direction face) {
        if (player.getCooldowns().isOnCooldown(this)) return false;
        BlockState state = level.getBlockState(clicked);
        BlockPos spot = state.canBeReplaced() && !state.is(GearBlocks.SPARK_LAMP.get()) && state.getFluidState().isEmpty()
                && !state.isAir() ? clicked : clicked.relative(face);
        boolean creative = player.getAbilities().instabuild;
        int cost = GearConfig.rodCost();
        boolean ok = (creative || ItemEnergy.get(stack) >= cost) && player.mayUseItemAt(spot, face, stack)
                && SparkLamps.place(player, level, spot, spot.equals(clicked) ? Direction.UP : face);
        if (!ok) {
            level.playSound(null, player.blockPosition(), SoundEvents.DISPENSER_FAIL, SoundSource.PLAYERS, 0.4F, 1.8F);
            return false;
        }
        if (!creative) ItemEnergy.drain(stack, cost);
        if (GearConfig.rodCooldown() > 0) player.getCooldowns().addCooldown(this, GearConfig.rodCooldown());
        return true;
    }

    @Override
    public int getEnergyCapacity(ItemStack stack) {
        return GearConfig.rodCapacity();
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

    /** Charging from a cell in the inventory only changes the energy: no equip animation every few ticks. */
    @Override
    public boolean shouldCauseReequipAnimation(ItemStack oldStack, ItemStack newStack, boolean slotChanged) {
        return slotChanged || !ItemEnergy.onlyEnergyChanged(oldStack, newStack);
    }

    @Override
    public void appendHoverText(ItemStack stack, TooltipContext ctx, List<Component> tooltip, TooltipFlag flag) {
        tooltip.add(Component.translatable("tooltip.robotica.age", 1, Component.translatable("age.robotica.1")).withStyle(ChatFormatting.DARK_GRAY));
        tooltip.add(Component.translatable("item.robotica.lamp_rod.tooltip", GearConfig.rodCost()).withStyle(ChatFormatting.GRAY));
        tooltip.add(Component.translatable("item.robotica.lamp_rod.tooltip.remove").withStyle(ChatFormatting.GRAY));
        ItemEnergy.appendTooltip(stack, tooltip);
    }
}
