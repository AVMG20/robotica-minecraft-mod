package com.arno.robotica.warp.item;

import com.arno.robotica.core.CoreSounds;
import com.arno.robotica.core.energy.EnergyItem;
import com.arno.robotica.core.energy.ItemEnergy;
import com.arno.robotica.core.util.Fmt;
import com.arno.robotica.warp.WarpComponents;
import com.arno.robotica.warp.WarpConfig;
import com.arno.robotica.warp.WarpFxPayload;
import com.arno.robotica.warp.WarpRegistry;
import com.arno.robotica.warp.WarpTravel;
import com.arno.robotica.warp.gate.PortalProjectorBlockEntity;
import com.arno.robotica.warp.pad.WarpPadBlockEntity;
import com.arno.robotica.warp.pad.WarpPads;
import com.arno.robotica.warp.teleport.WarpCooldowns;
import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.core.GlobalPos;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.InteractionResultHolder;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.item.UseAnim;
import net.minecraft.world.item.context.UseOnContext;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.entity.BlockEntity;

import java.util.List;

/**
 * Warp Remote (Age 1). Sneak-right-click a Warp Pad to store it (up to 10, the first is home), sneak-right-click two
 * Portal Projectors to link them. Hold right-click for 3 seconds to go home; sneak-right-click the air for the pad list,
 * where a click on a pad starts the same 3 second charge on the server ({@link com.arno.robotica.warp.teleport.WarpCharges}).
 * Other dimensions only lead to Mk2 pads. Damage cancels the charge. The trips run in {@link WarpTravel}.
 */
public class RemoteItem extends Item implements EnergyItem {
    public static final int CHARGE_TICKS = 60;

    private final int capacity;

    public RemoteItem(Properties props, int capacity) {
        super(props.stacksTo(1));
        this.capacity = capacity;
    }

    /** FE of a trip to a pad in the same dimension. */
    public static int sameDimensionCost() {
        return WarpConfig.remoteCost();
    }

    /** FE of a trip to a Mk2 pad in another dimension. */
    public static int crossDimensionCost() {
        return WarpConfig.remoteCrossCost();
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
    public boolean isFoil(ItemStack stack) {
        return stack.has(WarpComponents.LINK_SOURCE.get());
    }

    // ---- Storing pads and linking projectors ----

    @Override
    public InteractionResult useOn(UseOnContext context) {
        Player player = context.getPlayer();
        Level level = context.getLevel();
        BlockPos pos = context.getClickedPos();
        if (player == null || !player.isShiftKeyDown()) return InteractionResult.PASS;
        BlockEntity be = level.getBlockEntity(pos);
        if (!(be instanceof WarpPadBlockEntity) && !(be instanceof PortalProjectorBlockEntity)) return InteractionResult.PASS;
        if (level.isClientSide) return InteractionResult.SUCCESS;
        if (player instanceof ServerPlayer serverPlayer) {
            if (be instanceof WarpPadBlockEntity pad) WarpTravel.bindRemote(serverPlayer, context.getItemInHand(), pad);
            else WarpTravel.linkProjector(serverPlayer, context.getItemInHand(), GlobalPos.of(level.dimension(), pos));
        }
        return InteractionResult.CONSUME;
    }

    // ---- Going home, the pad list ----

    @Override
    public InteractionResultHolder<ItemStack> use(Level level, Player player, InteractionHand hand) {
        ItemStack stack = player.getItemInHand(hand);
        if (player.isShiftKeyDown()) {
            if (player instanceof ServerPlayer serverPlayer) WarpTravel.openRemote(serverPlayer, hand);
            return InteractionResultHolder.sidedSuccess(stack, level.isClientSide);
        }
        WarpComponents.BoundPad home = RemoteTargets.home(stack);
        if (home == null) {
            if (player instanceof ServerPlayer serverPlayer) WarpTravel.message(serverPlayer, Component.translatable("message.robotica.warp.remote_unbound"));
            return InteractionResultHolder.fail(stack);
        }
        if (player instanceof ServerPlayer serverPlayer) {
            // every check of the trip up front (and its message), so a charge never runs into a known refusal
            if (!WarpTravel.remoteListTrip(serverPlayer, stack, home.id(), true)) {
                // the client predicted success and started the hold: stop it there too
                WarpFxPayload.stopUse(serverPlayer);
                return InteractionResultHolder.fail(stack);
            }
        } else {
            boolean cross = home.pos().isPresent() && !home.pos().get().dimension().equals(level.dimension());
            if (player.getCooldowns().isOnCooldown(this)
                    || (!player.getAbilities().instabuild && !ItemEnergy.has(stack, cross ? crossDimensionCost() : sameDimensionCost()))) {
                return InteractionResultHolder.fail(stack);
            }
        }
        player.startUsingItem(hand);
        CoreSounds.play(player, CoreSounds.WARP_START, SoundSource.PLAYERS, 0.8F, 1.0F);
        return InteractionResultHolder.consume(stack);
    }

    @Override
    public int getUseDuration(ItemStack stack, LivingEntity entity) {
        return CHARGE_TICKS;
    }

    @Override
    public UseAnim getUseAnimation(ItemStack stack) {
        return UseAnim.BOW;
    }

    @Override
    public void onUseTick(Level level, LivingEntity user, ItemStack stack, int remaining) {
        if (!(level instanceof ServerLevel serverLevel)) return;
        int elapsed = CHARGE_TICKS - remaining;
        if (user.hurtTime > 0 && elapsed > 1) {
            // damage interrupts the charge (LivingIncomingDamageEvent handles it too, this catches anything that slipped by)
            if (user instanceof ServerPlayer player) WarpFxPayload.stopUse(player);
            else user.stopUsingItem();
            return;
        }
        WarpFxPayload.charge(serverLevel, user, elapsed, CHARGE_TICKS);
    }

    @Override
    public ItemStack finishUsingItem(ItemStack stack, Level level, LivingEntity entity) {
        if (!level.isClientSide && entity instanceof ServerPlayer player) WarpTravel.goHome(player, stack);
        return stack;
    }

    /** Server side: keeps the stored names current. */
    @Override
    public void inventoryTick(ItemStack stack, Level level, Entity entity, int slot, boolean selected) {
        if (level.isClientSide || level.getServer() == null || (level.getGameTime() + slot) % 40 != 0) return;
        RemoteTargets.refresh(stack, WarpPads.get(level.getServer()));
    }

    // ---- Tooltip ----

    @Override
    public void appendHoverText(ItemStack stack, TooltipContext ctx, List<Component> tooltip, TooltipFlag flag) {
        tooltip.add(Component.translatable("tooltip.robotica.age", 1, Component.translatable("age.robotica.1")).withStyle(ChatFormatting.DARK_GRAY));
        WarpComponents.BoundPad home = RemoteTargets.home(stack);
        if (home == null) {
            tooltip.add(Component.translatable("tooltip.robotica.remote_unbound").withStyle(ChatFormatting.YELLOW));
        } else {
            tooltip.add(Component.translatable("tooltip.robotica.remote_home", home.name(), RemoteTargets.list(stack).size(), WarpComponents.MAX_PADS)
                    .withStyle(ChatFormatting.GREEN));
        }
        GlobalPos source = stack.get(WarpComponents.LINK_SOURCE.get());
        if (source != null) {
            tooltip.add(Component.translatable("tooltip.robotica.remote_linking", source.pos().getX(), source.pos().getY(), source.pos().getZ())
                    .withStyle(ChatFormatting.LIGHT_PURPLE));
        }
        tooltip.add(Component.translatable("tooltip.robotica.warp_remote", Fmt.energy(sameDimensionCost()), Fmt.energy(crossDimensionCost()))
                .withStyle(ChatFormatting.GRAY));
        tooltip.add(Component.translatable("tooltip.robotica.warp_remote_use", WarpConfig.remoteCooldownTicks() / 20).withStyle(ChatFormatting.DARK_GRAY));
        tooltip.add(Component.translatable("tooltip.robotica.warp_remote_bind").withStyle(ChatFormatting.DARK_GRAY));
        ItemEnergy.appendTooltip(stack, tooltip);
    }

    // ---- Cooldown ----

    /** Starts the remote cooldown: on the item and persisted per UUID (survives relog and death). */
    public static void startCooldown(Player player) {
        int ticks = WarpConfig.remoteCooldownTicks();
        if (ticks <= 0) return;
        player.getCooldowns().addCooldown(WarpRegistry.WARP_REMOTE.get(), ticks);
        if (player instanceof ServerPlayer serverPlayer) {
            WarpCooldowns.get(serverPlayer.server).startRemote(serverPlayer.getUUID(), serverPlayer.serverLevel().getGameTime(), ticks);
        }
    }

    /** Ticks of remote cooldown left: the larger of the item cooldown and the persisted one. */
    public static int cooldownLeft(ServerPlayer player) {
        int persisted = WarpCooldowns.get(player.server).remoteRemaining(player.getUUID(), player.serverLevel().getGameTime());
        int item = player.getCooldowns().isOnCooldown(WarpRegistry.WARP_REMOTE.get()) ? 1 : 0;
        return Math.max(persisted, item);
    }

    /** Puts the persisted remaining time back on the item cooldown (login, respawn). */
    public static void restoreCooldown(ServerPlayer player) {
        int left = WarpCooldowns.get(player.server).remoteRemaining(player.getUUID(), player.serverLevel().getGameTime());
        if (left > 0) player.getCooldowns().addCooldown(WarpRegistry.WARP_REMOTE.get(), left);
    }
}
