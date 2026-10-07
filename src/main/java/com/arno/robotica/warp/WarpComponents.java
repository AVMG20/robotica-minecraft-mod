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
     * A pad a remote is bound to. Name and position are only snapshots for tooltips and for pads that are gone; the
     * current values come from the pad registry. The position is optional because older remotes did not store it.
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

        public BoundPad(UUID id, String name) {
            this(id, name, Optional.empty());
        }
    }

    /** Most pads a Rift Remote remembers. */
    public static final int MAX_RIFT_PADS = 10;

    /** The pad of the Recall Remote (and of Rift Remotes from before the list, moved into {@link #BOUND_PADS} on first use). */
    public static final DeferredHolder<DataComponentType<?>, DataComponentType<BoundPad>> BOUND_PAD =
            REGISTER.registerComponentType("bound_pad", b -> b.persistent(BoundPad.CODEC).networkSynchronized(BoundPad.STREAM_CODEC));

    /** The pads of a Rift Remote, in binding order, at most {@link #MAX_RIFT_PADS}. */
    public static final DeferredHolder<DataComponentType<?>, DataComponentType<List<BoundPad>>> BOUND_PADS =
            REGISTER.registerComponentType("bound_pads", b -> b.persistent(BoundPad.CODEC.listOf())
                    .networkSynchronized(BoundPad.STREAM_CODEC.apply(ByteBufCodecs.list(MAX_RIFT_PADS))));

    /** The first Gate Controller a Linking Card remembers. */
    public static final DeferredHolder<DataComponentType<?>, DataComponentType<GlobalPos>> LINK_SOURCE =
            REGISTER.registerComponentType("link_source", b -> b.persistent(GlobalPos.CODEC).networkSynchronized(GlobalPos.STREAM_CODEC));
}
