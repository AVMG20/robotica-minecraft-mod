package com.arno.robotica.power.block;

import com.arno.robotica.core.particle.CoreParticles;
import com.arno.robotica.power.PowerClientConfig;
import com.arno.robotica.power.PowerConfig;
import com.arno.robotica.power.PowerRegistry;
import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
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
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.level.block.state.properties.AttachFace;
import net.minecraft.world.level.block.state.properties.BooleanProperty;
import net.minecraft.world.level.block.state.properties.EnumProperty;
import net.minecraft.world.phys.BlockHitResult;
import org.jetbrains.annotations.Nullable;

import java.util.List;

/** Charges the FE items of every player in range, without plugging anything in. Mk1 and Mk2 Age 2, Mk3 Age 3, Mk4 Age 4. */
public class WirelessChargerBlock extends PowerBlock {
    public static final BooleanProperty LIT = BlockStateProperties.LIT;
    /** Floor: mast up, ceiling: mast down, wall: mast out of the wall towards FACING. */
    public static final EnumProperty<AttachFace> FACE = BlockStateProperties.ATTACH_FACE;
    private static final int[] AGE = {2, 2, 3, 4};

    private final int mk;

    public WirelessChargerBlock(Properties props, int mk) {
        super(props, PowerRegistry.WIRELESS_CHARGER_BE::get);
        this.mk = mk;
        registerDefaultState(stateDefinition.any().setValue(FACE, AttachFace.FLOOR).setValue(FACING, Direction.NORTH).setValue(LIT, false));
    }

    /** 1-4 */
    public int mk() {
        return mk;
    }

    @Override
    protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {
        builder.add(FACE, FACING, LIT);
    }

    /** The mast points away from the face it was placed against; on a floor or ceiling the front faces the player. */
    @Nullable
    @Override
    public BlockState getStateForPlacement(BlockPlaceContext context) {
        Direction clicked = context.getClickedFace();
        BlockState state = defaultBlockState();
        if (clicked.getAxis().isHorizontal()) return state.setValue(FACE, AttachFace.WALL).setValue(FACING, clicked);
        return state.setValue(FACE, clicked == Direction.UP ? AttachFace.FLOOR : AttachFace.CEILING)
                .setValue(FACING, context.getHorizontalDirection().getOpposite());
    }

    /** Where the emitter mast points. */
    public static Direction mastDirection(BlockState state) {
        return switch (state.getValue(FACE)) {
            case FLOOR -> Direction.UP;
            case CEILING -> Direction.DOWN;
            case WALL -> state.getValue(FACING);
        };
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

    /** A faint violet mote now and then on the emitter while it works, and on the players it charges (client side, random display ticks). */
    @Override
    public void animateTick(BlockState state, Level level, BlockPos pos, RandomSource random) {
        if (!state.getValue(LIT) || !PowerClientConfig.teslaParticles()) return;
        if (random.nextInt(8) == 0) {
            Direction mast = mastDirection(state);
            level.addParticle(CoreParticles.mote(CoreParticles.WARP_VIOLET, 0.4F),
                    pos.getX() + 0.5 + mast.getStepX() * 0.8 + (random.nextDouble() - 0.5) * 0.2,
                    pos.getY() + 0.5 + mast.getStepY() * 0.8 + (random.nextDouble() - 0.5) * 0.2,
                    pos.getZ() + 0.5 + mast.getStepZ() * 0.8 + (random.nextDouble() - 0.5) * 0.2, 0.0, 0.01, 0.0);
        }
        if (!(level.getBlockEntity(pos) instanceof WirelessChargerBlockEntity be)) return;
        int[] ids = be.targetIds();
        byte[] states = be.targetStates();
        for (int i = 0; i < ids.length && i < states.length; i++) {
            if (states[i] != WirelessChargerBlockEntity.CHARGING || random.nextInt(3) != 0) continue;
            Entity target = level.getEntity(ids[i]);
            if (target == null) continue;
            level.addParticle(CoreParticles.mote(CoreParticles.WARP_VIOLET, 0.3F), target.getX() + (random.nextDouble() - 0.5) * 0.8,
                    target.getY() + 0.3 + random.nextDouble() * 1.2, target.getZ() + (random.nextDouble() - 0.5) * 0.8, 0.0, 0.01, 0.0);
        }
    }

    @Override
    public void appendHoverText(ItemStack stack, Item.TooltipContext ctx, List<Component> tooltip, TooltipFlag flag) {
        int age = AGE[mk - 1];
        tooltip.add(Component.translatable("tooltip.robotica.age", age, Component.translatable("age.robotica." + age)).withStyle(ChatFormatting.DARK_GRAY));
        tooltip.add(Component.translatable("tooltip.robotica.wireless_charger", PowerConfig.wirelessRate(mk), PowerConfig.wirelessRange(mk))
                .withStyle(ChatFormatting.GRAY));
    }
}
