package com.arno.robotica.industry.block;

import com.arno.robotica.core.CoreSounds;
import com.arno.robotica.industry.IndustryConfig;
import com.arno.robotica.industry.IndustryRegistry;
import com.arno.robotica.industry.recipe.Machine;
import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.ItemInteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.BlockHitResult;
import org.jetbrains.annotations.Nullable;

import java.util.List;
import java.util.Locale;

/** Alloy Smelter, Centrifuge or Assembler of one Mk. Right-click a placed machine with the next Mk to upgrade it in place. */
public class ProcessingBlock extends IndustryMachineBlock {
    private final Machine machine;
    private final int tier;

    public ProcessingBlock(Properties props, Machine machine, int tier) {
        super(props, () -> IndustryRegistry.machineBlockEntity(machine).get());
        this.machine = machine;
        this.tier = tier;
    }

    public Machine machine() {
        return machine;
    }

    public int tier() {
        return tier;
    }

    @Override
    protected ItemInteractionResult useItemOn(ItemStack stack, BlockState state, Level level, BlockPos pos, Player player,
                                              InteractionHand hand, BlockHitResult hit) {
        if (stack.getItem() instanceof BlockItem item && item.getBlock() instanceof ProcessingBlock next
                && next.machine == machine && next.tier == tier + 1) {
            if (!level.isClientSide && upgradeInPlace(level, pos, state, next, player) && !player.getAbilities().instabuild) stack.shrink(1);
            return ItemInteractionResult.sidedSuccess(level.isClientSide);
        }
        return super.useItemOn(stack, state, level, pos, player, hand, hit);
    }

    /**
     * Swaps the placed machine for the next Mk, keeping inputs, outputs, battery, cards, energy and progress, and gives
     * the old machine back (the next Mk's recipe consumed one of the previous Mk, so the player keeps the same total).
     */
    public static boolean upgradeInPlace(Level level, BlockPos pos, BlockState state, ProcessingBlock next, @Nullable Player player) {
        if (!(level.getBlockEntity(pos) instanceof ProcessingBlockEntity old)) return false;
        CompoundTag saved = old.saveWithoutMetadata(level.registryAccess());
        old.keepContents = true;
        level.setBlock(pos, next.defaultBlockState().setValue(FACING, state.getValue(FACING)), Block.UPDATE_ALL);
        if (level.getBlockEntity(pos) instanceof ProcessingBlockEntity fresh) {
            fresh.loadCustomOnly(saved, level.registryAccess());
            fresh.setChanged();
        }
        if (player != null) {
            ItemStack back = new ItemStack(state.getBlock().asItem());
            if (!player.getInventory().add(back)) player.drop(back, false);
            player.displayClientMessage(Component.translatable("message.robotica.machine_upgraded", next.getName()), true);
        }
        CoreSounds.play(level, pos, CoreSounds.UPGRADE_INSTALL, SoundSource.BLOCKS, 0.8F, 1.0F);
        return true;
    }

    @Override
    public void appendHoverText(ItemStack stack, Item.TooltipContext ctx, List<Component> tooltip, TooltipFlag flag) {
        tooltip.add(Component.translatable("tooltip.robotica." + machine.id()).withStyle(ChatFormatting.GRAY));
        double speed = IndustryConfig.tierSpeed(tier);
        String x = speed == Math.rint(speed) ? String.valueOf((int) speed) : String.format(Locale.ROOT, "%.1f", speed);
        tooltip.add(Component.translatable("tooltip.robotica.industry_tier", tier, x, tier + 1).withStyle(ChatFormatting.DARK_GRAY));
        if (tier < Machine.TIERS) tooltip.add(Component.translatable("tooltip.robotica.industry_upgrade").withStyle(ChatFormatting.DARK_GRAY));
    }
}
