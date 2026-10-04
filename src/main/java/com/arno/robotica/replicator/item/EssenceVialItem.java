package com.arno.robotica.replicator.item;

import com.arno.robotica.core.CoreSounds;
import com.arno.robotica.replicator.logic.Essence;
import net.minecraft.ChatFormatting;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.util.Mth;

import java.util.List;

/**
 * Essence Vial. Right-click a hostile mob to take a sample (2 damage, 3 second cooldown per player). The first sample
 * binds the vial to the mob type (it stops stacking), eight samples complete it. Bosses can not be sampled.
 */
public class EssenceVialItem extends Item {
    public static final int COOLDOWN_TICKS = 60;
    public static final float SAMPLE_DAMAGE = 2.0F;
    private static final int BAR_COLOR = 0x3FE0C0;

    public EssenceVialItem(Properties props) {
        super(props);
    }

    @Override
    public InteractionResult interactLivingEntity(ItemStack stack, Player player, LivingEntity target, InteractionHand hand) {
        if (!Essence.isHostile(target)) return InteractionResult.PASS;
        boolean client = player.level().isClientSide;
        // The cooldown list is per player and synced to that player, so this is per player on any server.
        if (player.getCooldowns().isOnCooldown(this)) return InteractionResult.CONSUME;

        EntityType<?> type = target.getType();
        // The vial was only used on one mob at a time, so test on a single-vial copy first.
        ItemStack single = stack.copyWithCount(1);
        Essence.Result result = Essence.test(single, type);
        if (!result.success) {
            if (!client) player.displayClientMessage(refusal(result, type), true);
            return InteractionResult.CONSUME;
        }
        if (client) return InteractionResult.SUCCESS;

        ServerLevel level = (ServerLevel) player.level();
        if (stack.getCount() > 1) {
            // Binding takes one vial out of the stack, the rest stays empty and stackable.
            stack.shrink(1);
            Essence.sample(single, type);
            if (!player.getInventory().add(single)) player.drop(single, false);
        } else {
            Essence.sample(stack, type);
            single = stack;
        }
        player.getCooldowns().addCooldown(this, COOLDOWN_TICKS);
        target.hurt(level.damageSources().playerAttack(player), SAMPLE_DAMAGE);

        level.sendParticles(ParticleTypes.SCULK_SOUL, target.getX(), target.getY(0.6), target.getZ(), 6, 0.25, 0.4, 0.25, 0.02);
        if (result == Essence.Result.COMPLETED) {
            CoreSounds.play(level, target.blockPosition(), CoreSounds.ESSENCE_COMPLETE, SoundSource.PLAYERS, 1.0F, 1.0F);
            player.displayClientMessage(Component.translatable("message.robotica.vial_complete", type.getDescription()).withStyle(ChatFormatting.GREEN), true);
        } else {
            CoreSounds.play(level, target.blockPosition(), CoreSounds.ESSENCE_SAMPLE, SoundSource.PLAYERS, 1.0F, 0.9F + level.random.nextFloat() * 0.2F);
            player.displayClientMessage(Component.translatable("message.robotica.vial_sampled", type.getDescription(),
                    Essence.samples(single), Essence.SAMPLES_REQUIRED), true);
        }
        return InteractionResult.SUCCESS;
    }

    private static Component refusal(Essence.Result result, EntityType<?> type) {
        return switch (result) {
            case REFUSED -> Component.translatable("message.robotica.vial_refused", type.getDescription());
            case WRONG_TYPE -> Component.translatable("message.robotica.vial_wrong_type");
            default -> Component.translatable("message.robotica.vial_full");
        };
    }

    @Override
    public Component getName(ItemStack stack) {
        EntityType<?> type = Essence.type(stack);
        if (type != null) return Component.translatable("item.robotica.essence_vial.bound", type.getDescription());
        return super.getName(stack);
    }

    @Override
    public boolean isBarVisible(ItemStack stack) {
        return Essence.isBound(stack);
    }

    @Override
    public int getBarWidth(ItemStack stack) {
        return Mth.clamp(Math.round(13.0F * Essence.samples(stack) / Essence.SAMPLES_REQUIRED), 0, 13);
    }

    @Override
    public int getBarColor(ItemStack stack) {
        return BAR_COLOR;
    }

    @Override
    public boolean isFoil(ItemStack stack) {
        return Essence.isComplete(stack);
    }

    @Override
    public void appendHoverText(ItemStack stack, TooltipContext ctx, List<Component> tooltip, TooltipFlag flag) {
        if (!Essence.isBound(stack)) {
            tooltip.add(Component.translatable("tooltip.robotica.essence_vial").withStyle(ChatFormatting.GRAY));
            tooltip.add(Component.translatable("tooltip.robotica.essence_vial_bosses").withStyle(ChatFormatting.DARK_GRAY));
            return;
        }
        int samples = Essence.samples(stack);
        tooltip.add(Component.translatable("tooltip.robotica.essence_vial_samples", samples, Essence.SAMPLES_REQUIRED)
                .withStyle(samples >= Essence.SAMPLES_REQUIRED ? ChatFormatting.GREEN : ChatFormatting.AQUA));
        if (samples >= Essence.SAMPLES_REQUIRED) {
            tooltip.add(Component.translatable("tooltip.robotica.essence_vial_complete").withStyle(ChatFormatting.GRAY));
        }
    }
}
