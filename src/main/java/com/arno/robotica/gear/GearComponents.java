package com.arno.robotica.gear;

import com.arno.robotica.Robotica;
import com.arno.robotica.gear.tool.AreaMode;
import com.mojang.serialization.Codec;
import net.minecraft.core.component.DataComponentType;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.world.item.component.ItemContainerContents;
import net.neoforged.neoforge.registries.DeferredHolder;
import net.neoforged.neoforge.registries.DeferredRegister;

/** Data components of the gear module. They survive smithing upgrades because vanilla copies the component patch. */
public final class GearComponents {
    private GearComponents() {}

    public static final DeferredRegister.DataComponents REGISTER =
            DeferredRegister.createDataComponents(Registries.DATA_COMPONENT_TYPE, Robotica.MODID);

    /** Selected {@link AreaMode}. */
    public static final DeferredHolder<DataComponentType<?>, DataComponentType<AreaMode>> MODE =
            REGISTER.registerComponentType("gear_mode", b -> b.persistent(AreaMode.CODEC).networkSynchronized(AreaMode.STREAM_CODEC));

    /** Bit set of {@link com.arno.robotica.gear.tool.ToggleKind}. Absent means the default flags. */
    public static final DeferredHolder<DataComponentType<?>, DataComponentType<Integer>> TOGGLES =
            REGISTER.registerComponentType("gear_toggles", b -> b.persistent(Codec.INT).networkSynchronized(ByteBufCodecs.VAR_INT));

    /**
     * Everything installed at the Tinker's Bench, by position: 0 = Auto-Pickup card, 1 = Void Filter card, 2.. = module
     * slots (see {@link com.arno.robotica.gear.module.GearModules}). Absent means nothing installed.
     */
    public static final DeferredHolder<DataComponentType<?>, DataComponentType<ItemContainerContents>> INSTALLED =
            REGISTER.registerComponentType("gear_installed", b -> b.persistent(ItemContainerContents.CODEC).networkSynchronized(ItemContainerContents.STREAM_CODEC));

    /** Bit set of the {@link com.arno.robotica.gear.module.GearModuleKind} ordinals switched off in the G screen. Absent means all on. */
    public static final DeferredHolder<DataComponentType<?>, DataComponentType<Integer>> MODULES_OFF =
            REGISTER.registerComponentType("gear_modules_off", b -> b.persistent(Codec.INT).networkSynchronized(ByteBufCodecs.VAR_INT));

    /**
     * Legacy (0.3): installed cards as the bits of the {@link com.arno.robotica.gear.tool.ToggleKind} they
     * unlock. Only read: old tools move it into {@link #INSTALLED} ({@code GearModules.migrate}).
     */
    public static final DeferredHolder<DataComponentType<?>, DataComponentType<Integer>> MODULES =
            REGISTER.registerComponentType("gear_modules", b -> b.persistent(Codec.INT).networkSynchronized(ByteBufCodecs.VAR_INT));

    /** 0 = none, 1 = fortune, 2 = silk touch. */
    public static final DeferredHolder<DataComponentType<?>, DataComponentType<Integer>> ENCHANT_MODE =
            REGISTER.registerComponentType("gear_enchant_mode", b -> b.persistent(Codec.INT).networkSynchronized(ByteBufCodecs.VAR_INT));

    /**
     * Silk Touch / Fortune levels the swap itself put on the stack: bit 0 = Silk Touch, bits 1+ = Fortune level.
     * Only these are ever removed again, so enchantments the player applied survive.
     */
    public static final DeferredHolder<DataComponentType<?>, DataComponentType<Integer>> INJECTED_ENCHANTS =
            REGISTER.registerComponentType("gear_injected_enchants", b -> b.persistent(Codec.INT).networkSynchronized(ByteBufCodecs.VAR_INT));
}
