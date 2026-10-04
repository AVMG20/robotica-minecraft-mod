package com.arno.robotica.warp.item;

import com.arno.robotica.core.energy.EnergyItem;
import com.arno.robotica.core.energy.ItemEnergy;
import com.arno.robotica.core.util.Fmt;
import com.arno.robotica.warp.WarpComponents;
import com.arno.robotica.warp.WarpConfig;
import com.arno.robotica.warp.WarpRegistry;
import com.arno.robotica.warp.WarpTravel;
import com.arno.robotica.warp.pad.WarpPadBlockEntity;
import com.arno.robotica.warp.teleport.WarpCooldowns;
import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
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
 * Recall Remote (Age 1) and Rift Remote (Age 3). Sneak-right-click a pad to bind it, hold right-click for 3 seconds
 * to travel there. Damage cancels the charge. The trip itself runs in {@link WarpTravel#recall}.
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
        boolean unusable = !stack.has(WarpComponents.BOUND_PAD.get())
                || player.getCooldowns().isOnCooldown(this)
                || (player instanceof ServerPlayer sp && cooldownLeft(sp, this) > 0)
                || (!player.getAbilities().instabuild && !ItemEnergy.has(stack, sameDimensionCost()));
        if (unusable) {
            if (!level.isClientSide && player instanceof ServerPlayer serverPlayer) WarpTravel.explainRemote(serverPlayer, stack, this);
            return InteractionResultHolder.fail(stack);
        }
        player.startUsingItem(hand);
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
        if (elapsed % 2 == 0) {
            double spread = 0.4 + 0.6 * (1.0 - (double) elapsed / CHARGE_TICKS);
            serverLevel.sendParticles(rift ? ParticleTypes.REVERSE_PORTAL : ParticleTypes.PORTAL,
                    user.getX(), user.getY() + 0.9, user.getZ(), 4 + elapsed / 6, spread, 0.8, spread, 0.15);
        }
        if (elapsed % 6 == 0) {
            float pitch = 0.6F + 1.2F * elapsed / CHARGE_TICKS;
            serverLevel.playSound(null, user.getX(), user.getY(), user.getZ(), SoundEvents.AMETHYST_BLOCK_CHIME, SoundSource.PLAYERS, 0.8F, pitch);
        }
    }

    @Override
    public ItemStack finishUsingItem(ItemStack stack, Level level, LivingEntity entity) {
        if (!level.isClientSide && entity instanceof ServerPlayer player) WarpTravel.recall(player, stack, this);
        return stack;
    }

    // ---- Tooltip ----

    @Override
    public void appendHoverText(ItemStack stack, TooltipContext ctx, List<Component> tooltip, TooltipFlag flag) {
        int age = rift ? 3 : 1;
        tooltip.add(Component.translatable("tooltip.robotica.age", age, Component.translatable("age.robotica." + age)).withStyle(ChatFormatting.DARK_GRAY));
        WarpComponents.BoundPad bound = stack.get(WarpComponents.BOUND_PAD.get());
        if (bound == null) {
            tooltip.add(Component.translatable("tooltip.robotica.remote_unbound").withStyle(ChatFormatting.YELLOW));
        } else {
            tooltip.add(Component.translatable("tooltip.robotica.remote_bound", bound.name()).withStyle(ChatFormatting.GREEN));
        }
        tooltip.add(Component.translatable(rift ? "tooltip.robotica.rift_remote" : "tooltip.robotica.recall_remote",
                Fmt.energy(sameDimensionCost()), Fmt.energy(crossDimensionCost())).withStyle(ChatFormatting.GRAY));
        tooltip.add(Component.translatable("tooltip.robotica.remote_use", WarpConfig.remoteCooldownTicks() / 20).withStyle(ChatFormatting.DARK_GRAY));
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
