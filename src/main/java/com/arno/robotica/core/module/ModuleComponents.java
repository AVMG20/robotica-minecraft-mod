package com.arno.robotica.core.module;

import com.arno.robotica.Robotica;
import com.mojang.serialization.Codec;
import net.minecraft.core.component.DataComponentType;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.world.item.component.ItemContainerContents;
import net.neoforged.neoforge.registries.DeferredHolder;
import net.neoforged.neoforge.registries.DeferredRegister;

/** Data components of the module framework. They survive smithing upgrades (vanilla copies the component patch). */
public final class ModuleComponents {
    private ModuleComponents() {}

    public static final DeferredRegister.DataComponents REGISTER =
            DeferredRegister.createDataComponents(Registries.DATA_COMPONENT_TYPE, Robotica.MODID);

    /** Installed module items by slot. Absent means nothing installed. */
    public static final DeferredHolder<DataComponentType<?>, DataComponentType<ItemContainerContents>> MODULES =
            REGISTER.registerComponentType("modules", b -> b.persistent(ItemContainerContents.CODEC).networkSynchronized(ItemContainerContents.STREAM_CODEC));

    /** Bit i set: the module in slot i is switched off. Absent means all on. */
    public static final DeferredHolder<DataComponentType<?>, DataComponentType<Integer>> MODULES_OFF =
            REGISTER.registerComponentType("modules_off", b -> b.persistent(Codec.INT).networkSynchronized(ByteBufCodecs.VAR_INT));
}
