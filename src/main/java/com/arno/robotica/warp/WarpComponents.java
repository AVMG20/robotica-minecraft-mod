package com.arno.robotica.warp;

import com.arno.robotica.Robotica;
import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import net.minecraft.core.GlobalPos;
import net.minecraft.core.UUIDUtil;
import net.minecraft.core.component.DataComponentType;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.neoforged.neoforge.registries.DeferredHolder;
import net.neoforged.neoforge.registries.DeferredRegister;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

/** Data components of the warp module. Both survive smithing (vanilla copies the component patch). */
public final class WarpComponents {
    private WarpComponents() {}

    public static final DeferredRegister.DataComponents REGISTER =
            DeferredRegister.createDataComponents(Registries.DATA_COMPONENT_TYPE, Robotica.MODID);

    /**
     * A pad stored in a Warp Remote. Name and position are snapshots for tooltips and for pads that are gone; the current
     * values come from the pad registry.
     */
    public record BoundPad(UUID id, String name, Optional<GlobalPos> pos) {
        public static final Codec<BoundPad> CODEC = RecordCodecBuilder.create(i -> i.group(
                UUIDUtil.CODEC.fieldOf("id").forGetter(BoundPad::id),
                Codec.STRING.fieldOf("name").forGetter(BoundPad::name),
                GlobalPos.CODEC.optionalFieldOf("pos").forGetter(BoundPad::pos)).apply(i, BoundPad::new));
        public static final StreamCodec<RegistryFriendlyByteBuf, BoundPad> STREAM_CODEC = StreamCodec.composite(
                UUIDUtil.STREAM_CODEC, BoundPad::id,
                ByteBufCodecs.stringUtf8(64), BoundPad::name,
                ByteBufCodecs.optional(GlobalPos.STREAM_CODEC), BoundPad::pos,
                BoundPad::new);
    }

    /** Most pads a Warp Remote remembers. */
    public static final int MAX_PADS = 10;

    /** The pads of a Warp Remote, at most {@link #MAX_PADS}. The first one is home. */
    public static final DeferredHolder<DataComponentType<?>, DataComponentType<List<BoundPad>>> BOUND_PADS =
            REGISTER.registerComponentType("bound_pads", b -> b.persistent(BoundPad.CODEC.listOf())
                    .networkSynchronized(BoundPad.STREAM_CODEC.apply(ByteBufCodecs.list(MAX_PADS))));

    /** The first Portal Projector a Warp Remote is linking. */
    public static final DeferredHolder<DataComponentType<?>, DataComponentType<GlobalPos>> LINK_SOURCE =
            REGISTER.registerComponentType("link_source", b -> b.persistent(GlobalPos.CODEC).networkSynchronized(GlobalPos.STREAM_CODEC));
}
