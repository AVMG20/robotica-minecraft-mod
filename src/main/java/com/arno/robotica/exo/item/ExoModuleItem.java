package com.arno.robotica.exo.item;

import com.arno.robotica.exo.ExoConfig;
import com.arno.robotica.exo.ExoModuleKind;
import com.arno.robotica.exo.ExoSuit;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;

import java.util.List;
import java.util.Locale;

/** A module for the Exo-Frame, one item per kind and level. Installed in the armor's module screen (J, or sneak-use a piece). */
public class ExoModuleItem extends Item {
    public final ExoModuleKind kind;
    /** 1 based. */
    public final int level;

    public ExoModuleItem(Properties props, ExoModuleKind kind, int level) {
        super(props.stacksTo(16));
        this.kind = kind;
        this.level = level;
    }

    public int minMark() {
        return kind.minMark(level);
    }

    @Override
    public void appendHoverText(ItemStack stack, TooltipContext ctx, List<Component> tooltip, TooltipFlag flag) {
        tooltip.add(descLine(kind, level).withStyle(ChatFormatting.GRAY));
        tooltip.add(fitsLine(kind).withStyle(ChatFormatting.DARK_AQUA));
        tooltip.add(Component.translatable("exo.robotica.needs_mark", minMark()).withStyle(ChatFormatting.DARK_AQUA));
        Component cost = costLine(kind, level);
        if (cost != null) tooltip.add(cost.copy().withStyle(ChatFormatting.AQUA));
    }

    /** What the module does at this level, with its numbers from the server config. */
    public static MutableComponent descLine(ExoModuleKind kind, int level) {
        String key = "exo.robotica.module." + kind.id + ".desc";
        if (kind == ExoModuleKind.NIGHT_VISION && ExoConfig.thermalRadius(level) > 0) key += "_thermal";
        return Component.translatable(key, ExoConfig.describeArgs(kind, level));
    }

    /** "Fits: Exo Helmet" or the list of pieces. */
    public static MutableComponent fitsLine(ExoModuleKind kind) {
        if (kind.pieces == ExoModuleKind.Pieces.ANY) return Component.translatable("exo.robotica.fits", Component.translatable("exo.robotica.piece.any"));
        MutableComponent list = null;
        for (int i = 0; i < 4; i++) {
            if ((kind.pieces & (1 << i)) == 0) continue;
            Component name = Component.translatable("exo.robotica.piece." + ExoSuit.SLOTS[i].getName());
            list = list == null ? name.copy() : list.append(Component.literal(" / ")).append(name);
        }
        return Component.translatable("exo.robotica.fits", list == null ? Component.empty() : list);
    }

    /** The energy line of a module (cost or production), or null for passive modules. */
    public static Component costLine(ExoModuleKind kind, int level) {
        if (kind.unit == ExoModuleKind.Unit.PASSIVE) return null;
        return Component.translatable("exo.robotica.unit." + kind.unit.name().toLowerCase(Locale.ROOT), ExoConfig.cost(kind, level));
    }
}
