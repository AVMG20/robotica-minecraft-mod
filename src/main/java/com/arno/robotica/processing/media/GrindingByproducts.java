package com.arno.robotica.processing.media;

import com.arno.robotica.Robotica;
import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import net.minecraft.core.registries.Registries;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.neoforged.neoforge.registries.datamaps.DataMapType;
import org.jetbrains.annotations.Nullable;

import java.util.List;

/**
 * What grinding media shakes loose from an input (item data map {@code robotica:grinding_byproducts}, keyed by the
 * ore, usually through its tag). {@code byproducts} lists item ids or {@code #tags} in order of preference; the first
 * one that exists in the pack is used. Inputs without an entry fall back to the config list {@code fallbackByproducts}.
 *
 * <pre>{"values": {"#c:ores/iron": {"byproducts": ["#c:dusts/nickel", "#c:dusts/gold"]}}}</pre>
 */
public record GrindingByproducts(List<String> byproducts) {
    public static final Codec<GrindingByproducts> CODEC = RecordCodecBuilder.create(i -> i.group(
            Codec.STRING.listOf().fieldOf("byproducts").forGetter(GrindingByproducts::byproducts)
    ).apply(i, GrindingByproducts::new));

    public static final DataMapType<Item, GrindingByproducts> TYPE = DataMapType.builder(Robotica.id("grinding_byproducts"), Registries.ITEM, CODEC)
            .synced(CODEC, false).build();

    @Nullable
    public static GrindingByproducts of(ItemStack stack) {
        return stack.isEmpty() ? null : stack.getItemHolder().getData(TYPE);
    }
}
