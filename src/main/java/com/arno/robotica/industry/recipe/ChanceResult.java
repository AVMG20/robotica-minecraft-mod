package com.arno.robotica.industry.recipe;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.util.RandomSource;
import net.minecraft.world.item.ItemStack;

/**
 * One output of a processing recipe: {@code {"id": "robotica:thorium_dust", "count": 2, "chance": 0.25}}.
 * {@code count} defaults to 1, {@code chance} (0-1) to 1 (always).
 */
public record ChanceResult(ItemStack stack, float chance) {
    public static final Codec<ChanceResult> CODEC = RecordCodecBuilder.create(i -> i.group(
            ItemStack.ITEM_NON_AIR_CODEC.fieldOf("id").forGetter(r -> r.stack().getItemHolder()),
            Codec.intRange(1, 99).optionalFieldOf("count", 1).forGetter(r -> r.stack().getCount()),
            Codec.floatRange(0.0F, 1.0F).optionalFieldOf("chance", 1.0F).forGetter(ChanceResult::chance)
    ).apply(i, (item, count, chance) -> new ChanceResult(new ItemStack(item, count), chance)));

    public static final StreamCodec<RegistryFriendlyByteBuf, ChanceResult> STREAM_CODEC = StreamCodec.composite(
            ItemStack.STREAM_CODEC, ChanceResult::stack,
            ByteBufCodecs.FLOAT, ChanceResult::chance,
            ChanceResult::new);

    public boolean guaranteed() {
        return chance >= 1.0F;
    }

    /** The stack this roll produces, or empty when the chance failed. */
    public ItemStack roll(RandomSource random) {
        return guaranteed() || random.nextFloat() < chance ? stack.copy() : ItemStack.EMPTY;
    }
}
