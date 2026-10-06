package com.arno.robotica.exo;

import com.arno.robotica.exo.item.ExoArmorItem;
import com.arno.robotica.exo.item.ExoModuleItem;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.item.ItemStack;
import org.jetbrains.annotations.Nullable;

import java.util.List;

/**
 * Install rules of the module screen, shared by the menu (server and client) and the screen's explanation tooltip.
 * Safe on both sides.
 */
public final class ExoRules {
    private ExoRules() {}

    /**
     * Why {@code stack} cannot go into module slot {@code slot} of {@code piece}, or null when it may.
     *
     * @param others the other worn pieces of the suit (empty for a piece held in the hand)
     */
    @Nullable
    public static Component moduleRefusal(ItemStack piece, int slot, ItemStack stack, List<ItemStack> others) {
        if (!(stack.getItem() instanceof ExoModuleItem module)) return Component.translatable("exo.robotica.refuse.not_module");
        if (!(piece.getItem() instanceof ExoArmorItem armor)) return Component.translatable("exo.robotica.refuse.not_module");
        EquipmentSlot type = armor.getEquipmentSlot();
        ExoModuleKind kind = module.kind;
        if (!kind.fits(type)) return ExoModuleItem.fitsLine(kind);
        if (armor.mk < module.minMark()) return Component.translatable("exo.robotica.refuse.mark", module.minMark(), armor.mk);
        int n = ExoData.slotCount(piece);
        for (int i = 0; i < n; i++) {
            if (i != slot && ExoData.kind(piece, i) == kind) return Component.translatable("exo.robotica.refuse.duplicate_piece", Component.translatable(kind.kindKey()));
        }
        if (!kind.perPiece()) {
            for (ItemStack other : others) {
                if (other == piece || !(other.getItem() instanceof ExoArmorItem o)) continue;
                if (ExoData.levelIn(other, kind) > 0) {
                    return Component.translatable("exo.robotica.refuse.duplicate_suit", Component.translatable(kind.kindKey()),
                            Component.translatable("exo.robotica.piece." + o.getEquipmentSlot().getName()));
                }
            }
        }
        return null;
    }

    /** Why {@code stack} cannot go into the core socket of {@code piece}, or null when it may. */
    @Nullable
    public static Component coreRefusal(ItemStack piece, ItemStack stack) {
        if (!ExoData.isCore(stack)) return Component.translatable("exo.robotica.refuse.not_core");
        if (!(piece.getItem() instanceof ExoArmorItem armor) || !armor.hasCoreSocket()) return Component.translatable("exo.robotica.refuse.no_socket");
        return null;
    }
}
