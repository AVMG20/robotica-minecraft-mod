package com.arno.robotica.boss.item;

import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResultHolder;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.level.Level;

import java.util.List;

/**
 * A boss summon item (Signal Flare, Ignition Charge): right-click its altar with it to wake the boss. The altar does the
 * work (BossAltarBlock#useItemOn). {@code key} names the lang keys {@code tooltip.robotica.<key>} and
 * {@code message.robotica.boss.<key>.need_altar}.
 */
public class BossSummonItem extends Item {
    private final String key;

    public BossSummonItem(String key, Properties props) {
        super(props);
        this.key = key;
    }

    @Override
    public InteractionResultHolder<ItemStack> use(Level level, Player player, InteractionHand hand) {
        if (!level.isClientSide) player.displayClientMessage(Component.translatable("message.robotica.boss." + key + ".need_altar"), true);
        return InteractionResultHolder.pass(player.getItemInHand(hand));
    }

    @Override
    public void appendHoverText(ItemStack stack, TooltipContext context, List<Component> tooltip, TooltipFlag flag) {
        tooltip.add(Component.translatable("tooltip.robotica." + key).withStyle(ChatFormatting.GRAY));
    }
}
