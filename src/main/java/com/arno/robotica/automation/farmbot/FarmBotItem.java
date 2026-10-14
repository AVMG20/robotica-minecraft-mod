package com.arno.robotica.automation.farmbot;

import com.arno.robotica.automation.AutomationConfig;
import com.arno.robotica.automation.RobotArea;
import com.arno.robotica.core.CoreSounds;
import com.arno.robotica.core.energy.EnergyItem;
import com.arno.robotica.core.energy.ItemEnergy;
import com.arno.robotica.core.item.HasDetails;
import com.arno.robotica.core.progress.Milestones;
import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.item.context.UseOnContext;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.AABB;

import java.util.List;
import java.util.Locale;
import java.util.function.Supplier;

/** Stumpy or Sprout (one item per Mk) in item form: right-click a block to place it there (its home). Energy, cards and pouch travel in components. */
public class FarmBotItem extends Item implements EnergyItem, HasDetails {
    private final Supplier<? extends EntityType<? extends FarmBot>> type;
    private final int tier;
    private final int age;
    private final String descriptionKey;

    public FarmBotItem(Properties props, Supplier<? extends EntityType<? extends FarmBot>> type, int tier, int age, String descriptionKey) {
        super(props.stacksTo(1));
        this.type = type;
        this.tier = tier;
        this.age = age;
        this.descriptionKey = descriptionKey;
    }

    public int tier() {
        return tier;
    }

    public EntityType<? extends FarmBot> entityType() {
        return type.get();
    }

    /** Mk of a robot item, 0 for any other item. */
    public static int tier(ItemStack stack) {
        return stack.getItem() instanceof FarmBotItem item ? item.tier : 0;
    }

    /** A robot item that was never placed: no stored cards, pouch or battery. */
    public static boolean isFresh(ItemStack stack) {
        return !stack.has(FarmBotContent.STATE.get());
    }

    @Override
    public int getEnergyCapacity(ItemStack stack) {
        return AutomationConfig.energyBuffer();
    }

    @Override
    public int getMaxReceive(ItemStack stack) {
        return 1000;
    }

    @Override
    public boolean isBarVisible(ItemStack stack) {
        return ItemEnergy.get(stack) > 0;
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
    public InteractionResult useOn(UseOnContext context) {
        Level level = context.getLevel();
        if (!(level instanceof ServerLevel serverLevel)) return InteractionResult.SUCCESS;
        Player player = context.getPlayer();
        Direction face = context.getClickedFace();
        BlockPos pos = context.getClickedPos().relative(face);
        ItemStack stack = context.getItemInHand();
        if (player != null && (!player.mayUseItemAt(pos, face, stack) || !level.mayInteract(player, pos))) return InteractionResult.FAIL;
        EntityType<? extends FarmBot> entityType = type.get();
        double x = pos.getX() + 0.5, y = pos.getY(), z = pos.getZ() + 0.5;
        AABB box = entityType.getDimensions().makeBoundingBox(x, y, z);
        if (!level.noCollision(box)) return InteractionResult.FAIL;
        FarmBot bot = entityType.create(serverLevel);
        if (bot == null) return InteractionResult.FAIL;
        bot.moveTo(x, y, z, player != null ? player.getYRot() + 180.0F : 0.0F, 0.0F);
        bot.setYBodyRot(bot.getYRot());
        bot.setYHeadRot(bot.getYRot());
        bot.setHome(pos);
        bot.initFromStack(stack, player);
        serverLevel.addFreshEntity(bot);
        CoreSounds.play(bot, CoreSounds.ROBOT_BEEP, SoundSource.NEUTRAL, 0.7F, 1.0F);
        if (player instanceof ServerPlayer sp) Milestones.award(sp, Milestones.ROBOT_PLACED);
        if (player == null || !player.hasInfiniteMaterials()) stack.shrink(1);
        return InteractionResult.CONSUME;
    }

    @Override
    public void appendHoverText(ItemStack stack, TooltipContext ctx, List<Component> tooltip, TooltipFlag flag) {
        tooltip.add(Component.translatable("tooltip.robotica.age", age, Component.translatable("age.robotica." + age)).withStyle(ChatFormatting.DARK_GRAY));
        tooltip.add(Component.translatable(descriptionKey).withStyle(ChatFormatting.GRAY));
        CompoundTag state = stack.get(FarmBotContent.STATE.get());
        int size = RobotArea.size(RobotArea.rangeCards(state == null ? null : state.getCompound("Upgrades")));
        tooltip.add(Component.translatable("tooltip.robotica.farm_bot_stats", size, size, AutomationConfig.farmInterval(tier),
                String.format(Locale.ROOT, "%.1f", AutomationConfig.farmGrowth(tier))).withStyle(ChatFormatting.DARK_GRAY));
        if (ItemEnergy.get(stack) > 0) ItemEnergy.appendTooltip(stack, tooltip);
    }

    @Override
    public void appendDetails(ItemStack stack, TooltipContext ctx, List<Component> lines) {
        lines.add(HasDetails.line(descriptionKey + "_tip"));
        lines.add(HasDetails.line("tooltip.robotica.farm_bot.storage"));
        lines.add(HasDetails.line("tooltip.robotica.farm_bot.power"));
        lines.add(HasDetails.line("tooltip.robotica.farm_bot.controls"));
        if (tier > 1) lines.add(HasDetails.line("tooltip.robotica.farm_bot.upgrade", tier - 1));
    }
}
