package com.arno.robotica.power.tesla;

import com.arno.robotica.core.CoreSounds;
import com.arno.robotica.power.PowerRegistry;
import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.core.GlobalPos;
import net.minecraft.network.chat.Component;
import net.minecraft.sounds.SoundSource;
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
 * Links Tesla Coils. Sneak-right-click a coil to select it, then right-click machines (the clicked face is where the
 * power goes in) or other coils. Right-click a target again to unlink it. Sneak-right-click the air to clear the
 * selection. The logic runs in {@link #onItemUseFirst}, before the clicked block's own right-click (machine GUIs).
 */
public class TeslaLinkerItem extends Item {
    public TeslaLinkerItem(Properties props) {
        super(props.stacksTo(1));
    }

    @Override
    public InteractionResult onItemUseFirst(ItemStack stack, UseOnContext ctx) {
        Player player = ctx.getPlayer();
        Level level = ctx.getLevel();
        if (player == null) return InteractionResult.PASS;
        BlockPos pos = ctx.getClickedPos();
        boolean onCoil = level.getBlockState(pos).getBlock() instanceof TeslaCoilBlock;
        GlobalPos selected = stack.get(PowerRegistry.TESLA_SELECTION.get());
        if (!onCoil && selected == null) return InteractionResult.PASS;
        if (level.isClientSide) return InteractionResult.SUCCESS;

        if (onCoil && (player.isShiftKeyDown() || selected == null)) {
            if (!(level.getBlockEntity(pos) instanceof TeslaCoilBlockEntity coil)) return InteractionResult.CONSUME;
            if (!coil.canEdit(player)) {
                say(player, Component.translatable("message.robotica.tesla.not_yours").withStyle(ChatFormatting.RED));
                return InteractionResult.CONSUME;
            }
            stack.set(PowerRegistry.TESLA_SELECTION.get(), GlobalPos.of(level.dimension(), pos.immutable()));
            say(player, Component.translatable("message.robotica.tesla.selected", coil.getBlockState().getBlock().getName(),
                    coil.linkCount(), coil.maxLinks(), coil.tier().range()));
            CoreSounds.play(level, pos, CoreSounds.SHOCK_ZAP, SoundSource.PLAYERS, 0.25F, 1.8F);
            return InteractionResult.CONSUME;
        }

        if (!selected.dimension().equals(level.dimension())) {
            say(player, Component.translatable("message.robotica.tesla.other_dimension").withStyle(ChatFormatting.RED));
            return InteractionResult.CONSUME;
        }
        BlockPos coilPos = selected.pos();
        if (!level.isLoaded(coilPos) || !(level.getBlockEntity(coilPos) instanceof TeslaCoilBlockEntity coil)) {
            stack.remove(PowerRegistry.TESLA_SELECTION.get());
            say(player, Component.translatable("message.robotica.tesla.coil_gone").withStyle(ChatFormatting.RED));
            return InteractionResult.CONSUME;
        }
        if (!coil.canEdit(player)) {
            say(player, Component.translatable("message.robotica.tesla.not_yours").withStyle(ChatFormatting.RED));
            return InteractionResult.CONSUME;
        }
        if (pos.equals(coilPos)) {
            say(player, Component.translatable("message.robotica.tesla.status", coil.linkCount(), coil.maxLinks(), coil.sentRate()));
            return InteractionResult.CONSUME;
        }
        if (!level.mayInteract(player, pos)) {
            say(player, Component.translatable("message.robotica.tesla.protected").withStyle(ChatFormatting.RED));
            return InteractionResult.CONSUME;
        }
        TeslaNetwork.LinkResult result = TeslaNetwork.toggle(coil, pos, ctx.getClickedFace());
        Component face = Component.translatable("message.robotica.tesla.face." + ctx.getClickedFace().getName());
        Component msg = Component.translatable("message.robotica.tesla." + result.name().toLowerCase(java.util.Locale.ROOT),
                coil.linkCount(), coil.maxLinks(), coil.tier().range(), face);
        say(player, result.changed() ? msg.copy().withStyle(ChatFormatting.AQUA) : msg.copy().withStyle(ChatFormatting.RED));
        if (result == TeslaNetwork.LinkResult.LINKED || result == TeslaNetwork.LinkResult.FACE_CHANGED) {
            CoreSounds.play(level, pos, CoreSounds.SHOCK_ZAP, SoundSource.PLAYERS, 0.35F, 1.5F);
        } else if (result == TeslaNetwork.LinkResult.UNLINKED) {
            CoreSounds.play(level, pos, CoreSounds.SHOCK_ZAP, SoundSource.PLAYERS, 0.25F, 0.8F);
        }
        return InteractionResult.CONSUME;
    }

    @Override
    public InteractionResultHolder<ItemStack> use(Level level, Player player, InteractionHand hand) {
        ItemStack stack = player.getItemInHand(hand);
        GlobalPos selected = stack.get(PowerRegistry.TESLA_SELECTION.get());
        if (selected == null) return InteractionResultHolder.pass(stack);
        if (!level.isClientSide) {
            if (player.isShiftKeyDown()) {
                stack.remove(PowerRegistry.TESLA_SELECTION.get());
                say(player, Component.translatable("message.robotica.tesla.cleared"));
            } else if (selected.dimension().equals(level.dimension()) && level.isLoaded(selected.pos())
                    && level.getBlockEntity(selected.pos()) instanceof TeslaCoilBlockEntity coil) {
                say(player, Component.translatable("message.robotica.tesla.status", coil.linkCount(), coil.maxLinks(), coil.sentRate()));
            }
        }
        return InteractionResultHolder.sidedSuccess(stack, level.isClientSide);
    }

    private static void say(Player player, Component message) {
        player.displayClientMessage(message, true);
    }

    @Override
    public boolean isFoil(ItemStack stack) {
        return stack.has(PowerRegistry.TESLA_SELECTION.get());
    }

    @Override
    public void appendHoverText(ItemStack stack, TooltipContext ctx, List<Component> tooltip, TooltipFlag flag) {
        GlobalPos selected = stack.get(PowerRegistry.TESLA_SELECTION.get());
        if (selected == null) {
            tooltip.add(Component.translatable("tooltip.robotica.tesla_linker.none").withStyle(ChatFormatting.GRAY));
        } else {
            BlockPos p = selected.pos();
            tooltip.add(Component.translatable("tooltip.robotica.tesla_linker.selected", p.getX(), p.getY(), p.getZ())
                    .withStyle(ChatFormatting.AQUA));
        }
    }
}
