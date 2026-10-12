package com.arno.robotica.architect.pocket;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.world.item.Item;

import java.util.List;
import java.util.Optional;

/**
 * Settings of a Pocket Architect: the block right-click places ({@link PocketArchitectItem#blockIndex}, -1 none), whether
 * it eats junk, and the junk list (empty until the player edits it: then the tag {@code robotica:voidable} is the list).
 */
public record PocketSettings(int selected, boolean eatJunk, Optional<List<Item>> junk) {
    public static final int JUNK_SLOTS = 18;
    public static final PocketSettings DEFAULT = new PocketSettings(-1, true, Optional.empty());

    public static final Codec<PocketSettings> CODEC = RecordCodecBuilder.create(i -> i.group(
            Codec.INT.optionalFieldOf("selected", -1).forGetter(PocketSettings::selected),
            Codec.BOOL.optionalFieldOf("eat_junk", true).forGetter(PocketSettings::eatJunk),
            BuiltInRegistries.ITEM.byNameCodec().listOf().optionalFieldOf("junk").forGetter(PocketSettings::junk)
    ).apply(i, PocketSettings::new));

    public static final StreamCodec<RegistryFriendlyByteBuf, PocketSettings> STREAM_CODEC = StreamCodec.composite(
            ByteBufCodecs.VAR_INT, PocketSettings::selected,
            ByteBufCodecs.BOOL, PocketSettings::eatJunk,
            ByteBufCodecs.optional(ByteBufCodecs.registry(Registries.ITEM).apply(ByteBufCodecs.list(JUNK_SLOTS))), PocketSettings::junk,
            PocketSettings::new);

    public PocketSettings withSelected(int value) {
        return new PocketSettings(value, eatJunk, junk);
    }

    public PocketSettings withEatJunk(boolean value) {
        return new PocketSettings(selected, value, junk);
    }

    public PocketSettings withJunk(List<Item> value) {
        return new PocketSettings(selected, eatJunk, Optional.of(List.copyOf(value)));
    }
}
