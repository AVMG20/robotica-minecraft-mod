package com.arno.robotica.power.block;

import com.arno.robotica.core.CoreSounds;
import com.arno.robotica.power.PowerConfig;
import com.arno.robotica.power.PowerRegistry;
import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
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
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.level.block.state.properties.BooleanProperty;
import net.minecraft.world.phys.BlockHitResult;
import org.jetbrains.annotations.Nullable;

import java.util.List;
import java.util.Locale;

/**
 * Presses ingots into plates. Mk1 is Age 1, each Mk after it one Age later and faster ({@link PowerConfig#pressSpeed}).
 * Right-click a placed press with the next Mk to upgrade it in place.
 */
public class MetalPressBlock extends PowerBlock {
    public static final BooleanProperty LIT = BlockStateProperties.LIT;

    private final int mk;

    public MetalPressBlock(Properties props, int mk) {
        super(props, PowerRegistry.METAL_PRESS_BE::get);
        this.mk = mk;
        registerDefaultState(stateDefinition.any().setValue(FACING, Direction.NORTH).setValue(LIT, false));
    }

    /** 1-4 */
    public int mk() {
        return mk;
    }

    @Override
    protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {
        builder.add(FACING, LIT);
    }

    @Override
    protected ItemInteractionResult useItemOn(ItemStack stack, BlockState state, Level level, BlockPos pos, Player player,
                                              InteractionHand hand, BlockHitResult hit) {
        if (stack.getItem() instanceof BlockItem item && item.getBlock() instanceof MetalPressBlock next && next.mk == mk + 1) {
            // Swapping the block is building: adventure mode and protected spots may not.
            if (!player.mayBuild() || !level.mayInteract(player, pos)) return ItemInteractionResult.FAIL;
            if (!level.isClientSide && upgradeInPlace(level, pos, state, next, player) && !player.getAbilities().instabuild) stack.shrink(1);
            return ItemInteractionResult.sidedSuccess(level.isClientSide);
        }
        return super.useItemOn(stack, state, level, pos, player, hand, hit);
    }

    /**
     * Swaps the placed press for the next Mk, keeping input, output, cards, side config, energy and progress, and gives
     * the old press back (the next Mk's recipe consumed one of the previous Mk, so the player keeps the same total).
     */
    public static boolean upgradeInPlace(Level level, BlockPos pos, BlockState state, MetalPressBlock next, @Nullable Player player) {
        if (!(level.getBlockEntity(pos) instanceof MetalPressBlockEntity old)) return false;
        CompoundTag saved = old.saveWithoutMetadata(level.registryAccess());
        old.keepContents = true;
        if (!level.setBlock(pos, next.defaultBlockState().setValue(FACING, state.getValue(FACING)).setValue(LIT, state.getValue(LIT)), Block.UPDATE_ALL)) {
            old.keepContents = false;
            return false;
        }
        if (level.getBlockEntity(pos) instanceof MetalPressBlockEntity fresh) {
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
        double speed = PowerConfig.pressSpeed(mk);
        tooltip.add(Component.translatable("tooltip.robotica.metal_press", (int) Math.ceil(PowerConfig.pressPower() * speed))
                .withStyle(ChatFormatting.GRAY));
        if (mk > 1) {
            String x = speed == Math.rint(speed) ? String.valueOf((int) speed) : String.format(Locale.ROOT, "%.1f", speed);
            tooltip.add(Component.translatable("tooltip.robotica.metal_press_tier", mk, x).withStyle(ChatFormatting.DARK_GRAY));
        }
        tooltip.add(Component.translatable("tooltip.robotica.metal_press_upgrades").withStyle(ChatFormatting.DARK_GRAY));
        if (mk > 1) tooltip.add(Component.translatable("tooltip.robotica.industry_upgrade").withStyle(ChatFormatting.DARK_GRAY));
    }
}
