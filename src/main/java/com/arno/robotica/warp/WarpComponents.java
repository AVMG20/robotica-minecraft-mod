package com.arno.robotica.warp;

import com.arno.robotica.Robotica;
import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import net.minecraft.core.GlobalPos;
import net.minecraft.core.UUIDUtil;
import net.minecraft.core.component.DataComponentType;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.neoforged.neoforge.registries.DeferredHolder;
import net.neoforged.neoforge.registries.DeferredRegister;

import java.util.UUID;

/** Data components of the warp module. Both survive smithing (vanilla copies the component patch). */
public final class WarpComponents {
    private WarpComponents() {}

    public static final DeferredRegister.DataComponents REGISTER =
            DeferredRegister.createDataComponents(Registries.DATA_COMPONENT_TYPE, Robotica.MODID);

    /** The pad a remote is bound to. The name is only a snapshot for the tooltip. */
    public record BoundPad(UUID id, String name) {
        public static final Codec<BoundPad> CODEC = RecordCodecBuilder.create(i -> i.group(
                UUIDUtil.CODEC.fieldOf("id").forGetter(BoundPad::id),
                Codec.STRING.fieldOf("name").forGetter(BoundPad::name)).apply(i, BoundPad::new));
        public static final StreamCodec<net.minecraft.network.RegistryFriendlyByteBuf, BoundPad> STREAM_CODEC = StreamCodec.composite(
                UUIDUtil.STREAM_CODEC, BoundPad::id,
                ByteBufCodecs.stringUtf8(64), BoundPad::name,
                BoundPad::new);
    }

    public static final DeferredHolder<DataComponentType<?>, DataComponentType<BoundPad>> BOUND_PAD =
            REGISTER.registerComponentType("bound_pad", b -> b.persistent(BoundPad.CODEC).networkSynchronized(BoundPad.STREAM_CODEC));

    /** The first Gate Controller a Linking Card remembers. */
    public static final DeferredHolder<DataComponentType<?>, DataComponentType<GlobalPos>> LINK_SOURCE =
            REGISTER.registerComponentType("link_source", b -> b.persistent(GlobalPos.CODEC).networkSynchronized(GlobalPos.STREAM_CODEC));
}
