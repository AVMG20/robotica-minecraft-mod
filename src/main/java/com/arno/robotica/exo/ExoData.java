package com.arno.robotica.exo;

import com.arno.robotica.core.item.CoreItems;
import com.arno.robotica.exo.item.ExoArmorItem;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.component.ItemContainerContents;
import org.jetbrains.annotations.Nullable;

import java.util.List;

/**
 * The chestplate's core socket and the mark of an Exo armor piece. Modules use the shared framework
 * ({@link com.arno.robotica.core.module.Modules}). Safe on both sides.
 */
public final class ExoData {
    private ExoData() {}

    /** The boss cores of the chestplate socket and their set bonus. */
    public enum Core {
        NONE, SERVO, MAGMA, ANTIGRAV
    }

    public static int mark(ItemStack piece) {
        return piece.getItem() instanceof ExoArmorItem a ? a.mk : 0;
    }

    public static ItemStack core(ItemStack piece) {
        ItemContainerContents contents = piece.get(ExoRegistry.CORE.get());
        return contents == null || contents.getSlots() == 0 ? ItemStack.EMPTY : contents.getStackInSlot(0);
    }

    public static void setCore(ItemStack piece, ItemStack core) {
        if (core.isEmpty()) piece.remove(ExoRegistry.CORE.get());
        else piece.set(ExoRegistry.CORE.get(), ItemContainerContents.fromItems(List.of(core.copyWithCount(1))));
    }

    public static Core coreKind(ItemStack core) {
        if (core.is(CoreItems.SERVO_CORE.get())) return Core.SERVO;
        if (core.is(CoreItems.MAGMA_CORE.get())) return Core.MAGMA;
        if (core.is(CoreItems.ANTIGRAV_CORE.get())) return Core.ANTIGRAV;
        return Core.NONE;
    }

    public static boolean isCore(ItemStack stack) {
        return coreKind(stack) != Core.NONE;
    }

    /** Why {@code stack} cannot go into the core socket of {@code piece}, or null when it may. */
    @Nullable
    public static Component coreRefusal(ItemStack piece, ItemStack stack) {
        if (!isCore(stack)) return Component.translatable("exo.robotica.refuse.not_core");
        if (!(piece.getItem() instanceof ExoArmorItem armor) || !armor.hasCoreSocket()) return Component.translatable("exo.robotica.refuse.no_socket");
        return null;
    }
}
