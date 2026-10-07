package com.arno.robotica.core.module;

import net.minecraft.world.item.ItemStack;
import org.jetbrains.annotations.Nullable;

/** An item that takes modules: power tools, FE weapons and Exo armor pieces. See {@link Modules}. */
public interface ModuleHolder {
    /** What this stack is for the fit rules, or null when it takes no modules (Age 0 tools). */
    @Nullable
    ModuleTarget moduleTarget(ItemStack stack);

    /** Age (tools and weapons) or Mk (armor), 1-4. Decides the module slots and which levels fit. */
    int moduleTier(ItemStack stack);
}
