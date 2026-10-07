package com.arno.robotica.gear;

import com.arno.robotica.Robotica;
import com.arno.robotica.gear.tool.AreaMode;
import com.mojang.serialization.Codec;
import net.minecraft.core.component.DataComponentType;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.codec.ByteBufCodecs;
import net.neoforged.neoforge.registries.DeferredHolder;
import net.neoforged.neoforge.registries.DeferredRegister;

/**
 * Data components of the gear module. They survive smithing upgrades because vanilla copies the component patch.
 * Installed modules use the shared {@link com.arno.robotica.core.module.ModuleComponents}.
 */
public final class GearComponents {
    private GearComponents() {}

    public static final DeferredRegister.DataComponents REGISTER =
            DeferredRegister.createDataComponents(Registries.DATA_COMPONENT_TYPE, Robotica.MODID);

    /** Selected {@link AreaMode}. */
    public static final DeferredHolder<DataComponentType<?>, DataComponentType<AreaMode>> MODE =
            REGISTER.registerComponentType("gear_mode", b -> b.persistent(AreaMode.CODEC).networkSynchronized(AreaMode.STREAM_CODEC));

    /** Bit set of {@link com.arno.robotica.gear.tool.ToggleKind}. Absent means all off. */
    public static final DeferredHolder<DataComponentType<?>, DataComponentType<Integer>> TOGGLES =
            REGISTER.registerComponentType("gear_toggles", b -> b.persistent(Codec.INT).networkSynchronized(ByteBufCodecs.VAR_INT));
}
