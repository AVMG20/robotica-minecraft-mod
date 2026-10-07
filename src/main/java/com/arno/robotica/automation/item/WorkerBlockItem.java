package com.arno.robotica.automation.item;

import com.arno.robotica.automation.AutomationConfig;
import com.arno.robotica.automation.block.AreaWorkerBlock;
import com.arno.robotica.automation.block.ExcavatorBlock;
import com.arno.robotica.automation.block.SurveyRigBlock;
import com.arno.robotica.core.CoreConfig;
import com.arno.robotica.core.item.HasDetails;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.level.block.Block;

import java.util.List;
import java.util.Locale;

/** Block item of the automation machines: shows the age, a one-line description and, for Mk'd machines, the Mk stats. */
public class WorkerBlockItem extends BlockItem implements HasDetails {
    private final int age;
    private final String descriptionKey;

    public WorkerBlockItem(Block block, Properties props, int age, String descriptionKey) {
        super(block, props);
        this.age = age;
        this.descriptionKey = descriptionKey;
    }

    @Override
    public void appendHoverText(ItemStack stack, TooltipContext ctx, List<Component> tooltip, TooltipFlag flag) {
        tooltip.add(Component.translatable("tooltip.robotica.age", age, Component.translatable("age.robotica." + age))
                .withStyle(ChatFormatting.DARK_GRAY));
        tooltip.add(Component.translatable(descriptionKey).withStyle(ChatFormatting.GRAY));
        int tier = getBlock() instanceof AreaWorkerBlock worker ? worker.mkTier() : 0;
        if (getBlock() instanceof ExcavatorBlock) {
            int size = AutomationConfig.excavatorSize(tier);
            tooltip.add(Component.translatable("tooltip.robotica.excavator_mk", tier, size, size,
                    CoreConfig.scaleInterval(AutomationConfig.excavatorInterval(tier)), com.arno.robotica.core.upgrade.UpgradeRules.mkSlots(tier))
                    .withStyle(ChatFormatting.DARK_GRAY));
        } else if (getBlock() instanceof SurveyRigBlock) {
            String speed = String.format(Locale.ROOT, "%.1f", AutomationConfig.surveySpeed(tier) / 100.0);
            tooltip.add(Component.translatable("tooltip.robotica.survey_rig_mk", tier, speed, AutomationConfig.surveyRareBonus(tier),
                    com.arno.robotica.core.upgrade.UpgradeRules.mkSlots(tier)).withStyle(ChatFormatting.DARK_GRAY));
        }
        if (tier > 1) tooltip.add(Component.translatable("tooltip.robotica.worker_upgrade", tier - 1).withStyle(ChatFormatting.DARK_GRAY));
    }

    @Override
    public void appendDetails(ItemStack stack, TooltipContext ctx, List<Component> lines) {
        if (getBlock() instanceof ExcavatorBlock) lines.add(HasDetails.line("tooltip.robotica.excavator_details", AutomationConfig.excavatorRangeStep()));
    }
}
