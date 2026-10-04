package com.arno.robotica.core;

import com.arno.robotica.Robotica;
import com.mojang.serialization.Codec;
import net.minecraft.core.component.DataComponentType;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.codec.ByteBufCodecs;
import net.neoforged.neoforge.registries.DeferredHolder;
import net.neoforged.neoforge.registries.DeferredRegister;

/** Data components shared by all modules. Modules may register their own in their own DeferredRegister. */
public final class CoreComponents {
    private CoreComponents() {}

    public static final DeferredRegister.DataComponents REGISTER =
            DeferredRegister.createDataComponents(Registries.DATA_COMPONENT_TYPE, Robotica.MODID);

    /** Stored Forge Energy of an item. Read and write through {@link com.arno.robotica.core.energy.ItemEnergy}. */
    public static final DeferredHolder<DataComponentType<?>, DataComponentType<Integer>> ENERGY =
            REGISTER.registerComponentType("energy", b -> b.persistent(Codec.INT).networkSynchronized(ByteBufCodecs.VAR_INT));
}
