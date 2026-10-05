package com.arno.robotica.exo.item;

import com.arno.robotica.exo.ExoConfig;
import com.arno.robotica.exo.ExoModuleKind;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;

import java.util.List;

/** A module for the Exo-Frame. Installed in the armor piece's module slots (sneak + right-click the piece, or J while worn). */
public class ExoModuleItem extends Item {
    public final ExoModuleKind kind;

    public ExoModuleItem(Properties props, ExoModuleKind kind) {
        super(props.stacksTo(16));
        this.kind = kind;
    }

    @Override
    public void appendHoverText(ItemStack stack, TooltipContext ctx, List<Component> tooltip, TooltipFlag flag) {
        tooltip.add(Component.translatable("exo.robotica.module." + kind.id + ".desc").withStyle(ChatFormatting.GRAY));
        tooltip.add(Component.translatable("exo.robotica.fits", Component.translatable("exo.robotica.piece." + kind.slot.getName()))
                .withStyle(ChatFormatting.DARK_AQUA));
        tooltip.add(Component.translatable("exo.robotica.unit." + kind.unit.name().toLowerCase(java.util.Locale.ROOT), ExoConfig.cost(kind))
                .withStyle(ChatFormatting.AQUA));
    }
}
