package com.arno.robotica.power.block;

import com.arno.robotica.power.PowerClientConfig;
import com.arno.robotica.power.PowerConfig;
import com.arno.robotica.power.PowerRegistry;
import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.network.chat.Component;
import net.minecraft.util.RandomSource;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.ItemInteractionResult;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
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

/** Charges the FE items of the owner and their team in range, without plugging anything in. Age 2. */
public class WirelessChargerBlock extends PowerBlock {
    public static final BooleanProperty LIT = BlockStateProperties.LIT;

    public WirelessChargerBlock(Properties props) {
        super(props, PowerRegistry.WIRELESS_CHARGER_BE::get);
        registerDefaultState(stateDefinition.any().setValue(FACING, Direction.NORTH).setValue(LIT, false));
    }

    @Override
    protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {
        builder.add(FACING, LIT);
    }

    @Override
    public void setPlacedBy(Level level, BlockPos pos, BlockState state, @Nullable LivingEntity placer, ItemStack stack) {
        super.setPlacedBy(level, pos, state, placer, stack);
        if (!level.isClientSide && placer instanceof Player player && level.getBlockEntity(pos) instanceof WirelessChargerBlockEntity be) {
            be.setOwner(player);
        }
    }

    @Override
    protected ItemInteractionResult useItemOn(ItemStack stack, BlockState state, Level level, BlockPos pos, Player player,
                                              InteractionHand hand, BlockHitResult hit) {
        if (!level.isClientSide && level.getBlockEntity(pos) instanceof WirelessChargerBlockEntity be && !be.canUse(player)) {
            player.displayClientMessage(Component.translatable("message.robotica.wireless_locked"), true);
            return ItemInteractionResult.CONSUME;
        }
        return super.useItemOn(stack, state, level, pos, player, hand, hit);
    }

    @Override
    protected InteractionResult useWithoutItem(BlockState state, Level level, BlockPos pos, Player player, BlockHitResult hit) {
        if (!level.isClientSide && level.getBlockEntity(pos) instanceof WirelessChargerBlockEntity be && !be.canUse(player)) {
            player.displayClientMessage(Component.translatable("message.robotica.wireless_locked"), true);
            return InteractionResult.CONSUME;
        }
        return super.useWithoutItem(state, level, pos, player, hit);
    }

    /** Sparks on the emitter while it works, and on the players it charges (client side, random display ticks). */
    @Override
    public void animateTick(BlockState state, Level level, BlockPos pos, RandomSource random) {
        if (!state.getValue(LIT) || !PowerClientConfig.teslaParticles()) return;
        if (random.nextInt(3) == 0) {
            level.addParticle(ParticleTypes.ELECTRIC_SPARK, pos.getX() + 0.5 + (random.nextDouble() - 0.5) * 0.3, pos.getY() + 1.25,
                    pos.getZ() + 0.5 + (random.nextDouble() - 0.5) * 0.3, 0.0, 0.03, 0.0);
        }
        if (!(level.getBlockEntity(pos) instanceof WirelessChargerBlockEntity be)) return;
        int[] ids = be.targetIds();
        byte[] states = be.targetStates();
        for (int i = 0; i < ids.length && i < states.length; i++) {
            if (states[i] != WirelessChargerBlockEntity.CHARGING || random.nextInt(2) != 0) continue;
            Entity target = level.getEntity(ids[i]);
            if (target == null) continue;
            level.addParticle(ParticleTypes.ELECTRIC_SPARK, target.getX() + (random.nextDouble() - 0.5) * 0.6,
                    target.getY() + 0.4 + random.nextDouble() * 1.2, target.getZ() + (random.nextDouble() - 0.5) * 0.6, 0.0, 0.0, 0.0);
        }
    }

    @Override
    public void appendHoverText(ItemStack stack, Item.TooltipContext ctx, List<Component> tooltip, TooltipFlag flag) {
        tooltip.add(Component.translatable("tooltip.robotica.age", 2, Component.translatable("age.robotica.2")).withStyle(ChatFormatting.DARK_GRAY));
        tooltip.add(Component.translatable("tooltip.robotica.wireless_charger", PowerConfig.wirelessRate(), PowerConfig.wirelessRange())
                .withStyle(ChatFormatting.GRAY));
    }
}
