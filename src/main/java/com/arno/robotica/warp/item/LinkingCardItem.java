package com.arno.robotica.warp.item;

import com.arno.robotica.warp.WarpComponents;
import com.arno.robotica.warp.WarpRegistry;
import com.arno.robotica.warp.WarpTravel;
import com.arno.robotica.warp.gate.GateControllerBlockEntity;
import net.minecraft.ChatFormatting;
import net.minecraft.core.GlobalPos;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.InteractionResultHolder;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.item.context.UseOnContext;
import net.minecraft.world.level.Level;

import java.util.List;

/**
 * Links two Gate Controllers: sneak-right-click the first, then the second. Sneak-right-click in the air forgets
 * the first one. The linking itself is {@link GateControllerBlockEntity#link}.
 */
public class LinkingCardItem extends Item {
    public LinkingCardItem(Properties props) {
        super(props.stacksTo(1));
    }

    @Override
    public InteractionResult useOn(UseOnContext context) {
        Player player = context.getPlayer();
        Level level = context.getLevel();
        if (player == null || !player.isShiftKeyDown() || !level.getBlockState(context.getClickedPos()).is(WarpRegistry.GATE_CONTROLLER.get())) {
            return InteractionResult.PASS;
        }
        if (level.isClientSide) return InteractionResult.SUCCESS;
        if (player instanceof ServerPlayer serverPlayer) {
            WarpTravel.linkingCardUsed(serverPlayer, context.getItemInHand(), GlobalPos.of(level.dimension(), context.getClickedPos()));
        }
        return InteractionResult.CONSUME;
    }

    @Override
    public InteractionResultHolder<ItemStack> use(Level level, Player player, InteractionHand hand) {
        ItemStack stack = player.getItemInHand(hand);
        if (player.isShiftKeyDown() && stack.has(WarpComponents.LINK_SOURCE.get())) {
            if (!level.isClientSide) {
                stack.remove(WarpComponents.LINK_SOURCE.get());
                player.displayClientMessage(Component.translatable("message.robotica.warp.card_cleared"), true);
            }
            return InteractionResultHolder.sidedSuccess(stack, level.isClientSide);
        }
        return InteractionResultHolder.pass(stack);
    }

    @Override
    public boolean isFoil(ItemStack stack) {
        return stack.has(WarpComponents.LINK_SOURCE.get());
    }

    @Override
    public void appendHoverText(ItemStack stack, TooltipContext ctx, List<Component> tooltip, TooltipFlag flag) {
        tooltip.add(Component.translatable("tooltip.robotica.age", 4, Component.translatable("age.robotica.4")).withStyle(ChatFormatting.DARK_GRAY));
        tooltip.add(Component.translatable("tooltip.robotica.linking_card").withStyle(ChatFormatting.GRAY));
        GlobalPos source = stack.get(WarpComponents.LINK_SOURCE.get());
        if (source != null) {
            tooltip.add(Component.translatable("tooltip.robotica.linking_card_stored", source.pos().getX(), source.pos().getY(), source.pos().getZ(),
                    source.dimension().location().toString()).withStyle(ChatFormatting.GREEN));
        }
    }
}
