package com.arno.robotica.storage.item;

import com.arno.robotica.core.CoreSounds;
import com.arno.robotica.core.energy.EnergyItem;
import com.arno.robotica.core.energy.ItemEnergy;
import com.arno.robotica.core.util.Fmt;
import com.arno.robotica.storage.StorageContent;
import com.arno.robotica.storage.block.StorageTerminalBlockEntity;
import com.arno.robotica.storage.remote.TerminalLinks;
import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.core.GlobalPos;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
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
 * Terminal Link Mk1 (Age 2, its terminal's dimension) and Mk2 (Age 3, any dimension). Sneak-right-click a Storage
 * Terminal to bind it; right-click opens that terminal's screen from anywhere ({@link TerminalLinks}). Runs on its own
 * battery while the screen is open.
 */
public class TerminalLinkItem extends Item implements EnergyItem {
    private final int mk;
    private final int capacity;
    private final int fePerTick;

    public TerminalLinkItem(Properties props, int mk, int capacity, int fePerTick) {
        super(props.stacksTo(1));
        this.mk = mk;
        this.capacity = capacity;
        this.fePerTick = fePerTick;
    }

    public int mk() {
        return mk;
    }

    /** Mk2 and up open terminals in other dimensions. */
    public boolean crossDimension() {
        return mk >= 2;
    }

    /** FE per tick while the remote screen is open. */
    public int fePerTick() {
        return fePerTick;
    }

    @Override
    public int getEnergyCapacity(ItemStack stack) {
        return capacity;
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

    /** Sneak-right-click a Storage Terminal: bind to it. */
    @Override
    public InteractionResult useOn(UseOnContext context) {
        Player player = context.getPlayer();
        Level level = context.getLevel();
        BlockPos pos = context.getClickedPos();
        if (player == null || !player.isShiftKeyDown() || !(level.getBlockEntity(pos) instanceof StorageTerminalBlockEntity)) {
            return InteractionResult.PASS;
        }
        if (level.isClientSide) return InteractionResult.SUCCESS;
        context.getItemInHand().set(StorageContent.LINKED_TERMINAL.get(), GlobalPos.of(level.dimension(), pos.immutable()));
        CoreSounds.play(level, pos, CoreSounds.TERMINAL_LINK_OPEN, SoundSource.PLAYERS, 0.7F, 1.3F);
        player.displayClientMessage(Component.translatable("message.robotica.terminal_link_bound", pos.getX(), pos.getY(), pos.getZ()), true);
        return InteractionResult.CONSUME;
    }

    /** Right-click: open the bound terminal. */
    @Override
    public InteractionResultHolder<ItemStack> use(Level level, Player player, InteractionHand hand) {
        ItemStack stack = player.getItemInHand(hand);
        if (player.isShiftKeyDown()) return InteractionResultHolder.pass(stack);
        if (player instanceof ServerPlayer sp) {
            return TerminalLinks.open(sp, hand) ? InteractionResultHolder.consume(stack) : InteractionResultHolder.fail(stack);
        }
        return InteractionResultHolder.success(stack);
    }

    @Override
    public void appendHoverText(ItemStack stack, TooltipContext ctx, List<Component> tooltip, TooltipFlag flag) {
        int age = mk + 1;
        tooltip.add(Component.translatable("tooltip.robotica.age", age, Component.translatable("age.robotica." + age)).withStyle(ChatFormatting.DARK_GRAY));
        GlobalPos bound = stack.get(StorageContent.LINKED_TERMINAL.get());
        if (bound == null) {
            tooltip.add(Component.translatable("tooltip.robotica.terminal_link_unbound").withStyle(ChatFormatting.YELLOW));
        } else {
            BlockPos p = bound.pos();
            tooltip.add(Component.translatable("tooltip.robotica.terminal_link_bound", p.getX(), p.getY(), p.getZ(),
                    bound.dimension().location().getPath()).withStyle(ChatFormatting.GREEN));
        }
        tooltip.add(Component.translatable(crossDimension() ? "tooltip.robotica.terminal_link_mk2" : "tooltip.robotica.terminal_link")
                .withStyle(ChatFormatting.GRAY));
        tooltip.add(Component.translatable("tooltip.robotica.terminal_link_use", Fmt.energy(fePerTick)).withStyle(ChatFormatting.DARK_GRAY));
        ItemEnergy.appendTooltip(stack, tooltip);
    }
}
