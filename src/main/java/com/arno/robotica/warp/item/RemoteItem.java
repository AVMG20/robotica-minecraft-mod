package com.arno.robotica.warp.item;

import com.arno.robotica.core.CoreSounds;
import com.arno.robotica.core.energy.EnergyItem;
import com.arno.robotica.core.energy.ItemEnergy;
import com.arno.robotica.core.util.Fmt;
import com.arno.robotica.warp.WarpComponents;
import com.arno.robotica.warp.WarpConfig;
import com.arno.robotica.warp.WarpRegistry;
import com.arno.robotica.warp.WarpTravel;
import com.arno.robotica.warp.pad.PadRecord;
import com.arno.robotica.warp.pad.WarpPadBlockEntity;
import com.arno.robotica.warp.pad.WarpPads;
import com.arno.robotica.warp.teleport.WarpCooldowns;
import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.InteractionResultHolder;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.item.UseAnim;
import net.minecraft.world.item.context.UseOnContext;
import net.minecraft.world.level.Level;

import java.util.List;

/**
 * Recall Remote (Age 1) and Rift Remote (Age 3). Sneak-right-click a pad to bind it. The Recall Remote holds one pad:
 * hold right-click for 3 seconds to travel there. The Rift Remote stores up to 10 pads: right-click opens the list,
 * a click on a pad starts the same 3 second charge on the server ({@link com.arno.robotica.warp.teleport.RiftCharges}).
 * Damage cancels the charge. The trips run in {@link WarpTravel}.
 */
public class RemoteItem extends Item implements EnergyItem {
    public static final int CHARGE_TICKS = 60;

    private final boolean rift;
    private final int capacity;

    public RemoteItem(Properties props, boolean rift, int capacity) {
        super(props.stacksTo(1));
        this.rift = rift;
        this.capacity = capacity;
    }

    /** True for the Rift Remote: it also works across dimensions. */
    public boolean isRift() {
        return rift;
    }

    /** FE a trip to a pad in the same dimension costs. */
    public int sameDimensionCost() {
        return WarpConfig.remoteCost();
    }

    /** FE a trip to another dimension costs (only the Rift Remote can make it). */
    public int crossDimensionCost() {
        return WarpConfig.riftRemoteCost();
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

    // ---- Binding ----

    @Override
    public InteractionResult useOn(UseOnContext context) {
        Player player = context.getPlayer();
        Level level = context.getLevel();
        BlockPos pos = context.getClickedPos();
        if (player == null || !player.isShiftKeyDown() || !(level.getBlockEntity(pos) instanceof WarpPadBlockEntity pad)) {
            return InteractionResult.PASS;
        }
        if (level.isClientSide) return InteractionResult.SUCCESS;
        if (player instanceof ServerPlayer serverPlayer) WarpTravel.bindRemote(serverPlayer, context.getItemInHand(), pad);
        return InteractionResult.CONSUME;
    }

    // ---- Charging ----

    @Override
    public InteractionResultHolder<ItemStack> use(Level level, Player player, net.minecraft.world.InteractionHand hand) {
        ItemStack stack = player.getItemInHand(hand);
        if (player.isShiftKeyDown()) return InteractionResultHolder.pass(stack);
        if (rift) {
            if (player instanceof ServerPlayer serverPlayer) WarpTravel.openRiftRemote(serverPlayer, hand);
            return InteractionResultHolder.sidedSuccess(stack, level.isClientSide);
        }
        boolean unusable = !stack.has(WarpComponents.BOUND_PAD.get())
                || player.getCooldowns().isOnCooldown(this)
                || (player instanceof ServerPlayer sp && cooldownLeft(sp, this) > 0)
                || (!player.getAbilities().instabuild && !ItemEnergy.has(stack, sameDimensionCost()));
        if (unusable) {
            if (!level.isClientSide && player instanceof ServerPlayer serverPlayer) WarpTravel.explainRemote(serverPlayer, stack, this);
            return InteractionResultHolder.fail(stack);
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
            user.stopUsingItem();
            return;
        }
        chargeEffects(serverLevel, user, elapsed, rift);
    }

    /** Particles and the rising hum of a charging remote, {@code elapsed} ticks into the charge. */
    public static void chargeEffects(ServerLevel serverLevel, LivingEntity user, int elapsed, boolean rift) {
        if (elapsed % 2 == 0) {
            double spread = 0.4 + 0.6 * (1.0 - (double) elapsed / CHARGE_TICKS);
            serverLevel.sendParticles(rift ? ParticleTypes.REVERSE_PORTAL : ParticleTypes.PORTAL,
                    user.getX(), user.getY() + 0.9, user.getZ(), 4 + elapsed / 6, spread, 0.8, spread, 0.15);
        }
        if (elapsed % 6 == 0) {
            float pitch = 0.6F + 1.2F * elapsed / CHARGE_TICKS;
            CoreSounds.play(serverLevel, user.getX(), user.getY(), user.getZ(), CoreSounds.WARP_CHARGE, SoundSource.PLAYERS, 0.8F, pitch);
        }
    }

    @Override
    public ItemStack finishUsingItem(ItemStack stack, Level level, LivingEntity entity) {
        if (!level.isClientSide && entity instanceof ServerPlayer player) WarpTravel.recall(player, stack, this);
        return stack;
    }

    /** Server side: moves an old single pad into the Rift Remote's list and keeps the stored names current. */
    @Override
    public void inventoryTick(ItemStack stack, Level level, net.minecraft.world.entity.Entity entity, int slot, boolean selected) {
        if (level.isClientSide || level.getServer() == null) return;
        boolean legacy = rift && stack.has(WarpComponents.BOUND_PAD.get());
        if (!legacy && (level.getGameTime() + slot) % 40 != 0) return;
        WarpPads pads = WarpPads.get(level.getServer());
        if (rift) {
            RiftTargets.refresh(stack, pads);
            return;
        }
        WarpComponents.BoundPad bound = stack.get(WarpComponents.BOUND_PAD.get());
        if (bound == null) return;
        PadRecord rec = pads.get(bound.id());
        if (rec != null && !rec.name().equals(bound.name())) {
            stack.set(WarpComponents.BOUND_PAD.get(), new WarpComponents.BoundPad(bound.id(), rec.name(), bound.pos()));
        }
    }

    // ---- Tooltip ----

    @Override
    public void appendHoverText(ItemStack stack, TooltipContext ctx, List<Component> tooltip, TooltipFlag flag) {
        int age = rift ? 3 : 1;
        tooltip.add(Component.translatable("tooltip.robotica.age", age, Component.translatable("age.robotica." + age)).withStyle(ChatFormatting.DARK_GRAY));
        if (rift) {
            int stored = RiftTargets.list(stack).size();
            tooltip.add(stored == 0 ? Component.translatable("tooltip.robotica.remote_unbound").withStyle(ChatFormatting.YELLOW)
                    : Component.translatable("tooltip.robotica.rift_remote_stored", stored, WarpComponents.MAX_RIFT_PADS).withStyle(ChatFormatting.GREEN));
        } else {
            WarpComponents.BoundPad bound = stack.get(WarpComponents.BOUND_PAD.get());
            if (bound == null) {
                tooltip.add(Component.translatable("tooltip.robotica.remote_unbound").withStyle(ChatFormatting.YELLOW));
            } else {
                tooltip.add(Component.translatable("tooltip.robotica.remote_bound", bound.name()).withStyle(ChatFormatting.GREEN));
            }
        }
        tooltip.add(Component.translatable(rift ? "tooltip.robotica.rift_remote" : "tooltip.robotica.recall_remote",
                Fmt.energy(sameDimensionCost()), Fmt.energy(crossDimensionCost())).withStyle(ChatFormatting.GRAY));
        tooltip.add(Component.translatable(rift ? "tooltip.robotica.rift_remote_use" : "tooltip.robotica.remote_use",
                WarpConfig.remoteCooldownTicks() / 20).withStyle(ChatFormatting.DARK_GRAY));
        ItemEnergy.appendTooltip(stack, tooltip);
    }

    /** Both remotes share the cooldown: a player may not hop between them to skip the wait. Also persisted per UUID. */
    public static void startCooldown(Player player) {
        int ticks = WarpConfig.remoteCooldownTicks();
        if (ticks <= 0) return;
        player.getCooldowns().addCooldown(WarpRegistry.RECALL_REMOTE.get(), ticks);
        player.getCooldowns().addCooldown(WarpRegistry.RIFT_REMOTE.get(), ticks);
        if (player instanceof ServerPlayer serverPlayer) {
            WarpCooldowns.get(serverPlayer.server).startRemote(serverPlayer.getUUID(), serverPlayer.serverLevel().getGameTime(), ticks);
        }
    }

    /** Ticks of remote cooldown left: the larger of the item cooldown and the persisted one (which survives relog and death). */
    public static int cooldownLeft(ServerPlayer player, RemoteItem remote) {
        int persisted = WarpCooldowns.get(player.server).remoteRemaining(player.getUUID(), player.serverLevel().getGameTime());
        int item = player.getCooldowns().isOnCooldown(remote) ? 1 : 0;
        return Math.max(persisted, item);
    }

    /** Puts the persisted remaining time back on the item cooldown (login, respawn). */
    public static void restoreCooldown(ServerPlayer player) {
        int left = WarpCooldowns.get(player.server).remoteRemaining(player.getUUID(), player.serverLevel().getGameTime());
        if (left <= 0) return;
        player.getCooldowns().addCooldown(WarpRegistry.RECALL_REMOTE.get(), left);
        player.getCooldowns().addCooldown(WarpRegistry.RIFT_REMOTE.get(), left);
    }
}
