package com.arno.robotica.processing.media;

import com.arno.robotica.Robotica;
import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import net.minecraft.core.registries.Registries;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.neoforged.neoforge.registries.datamaps.DataMapType;
import org.jetbrains.annotations.Nullable;

/**
 * Grinding media: the optional second input of the Grinder (item data map {@code robotica:grinding_media}).
 * While a media item sits in the media slot, every ore or raw ore ground gets {@code bonus} more main output
 * (0.25 = +25%, fractions are rolled) and a {@code secondary} chance at a byproduct dust; one media item wears out
 * after {@code uses} ores. It only fits a Grinder of at least Mk {@code tier} (0 and 1 fit every Grinder).
 *
 * <pre>{"values": {"minecraft:flint": {"bonus": 0.1, "secondary": 0.02, "uses": 8, "tier": 0}}}</pre>
 */
public record GrindingMedia(float bonus, float secondary, int uses, int tier) {
    public static final Codec<GrindingMedia> CODEC = RecordCodecBuilder.create(i -> i.group(
            Codec.floatRange(0.0F, 16.0F).fieldOf("bonus").forGetter(GrindingMedia::bonus),
            Codec.floatRange(0.0F, 1.0F).optionalFieldOf("secondary", 0.0F).forGetter(GrindingMedia::secondary),
            Codec.intRange(1, 1_000_000).fieldOf("uses").forGetter(GrindingMedia::uses),
            Codec.intRange(0, 4).optionalFieldOf("tier", 1).forGetter(GrindingMedia::tier)
    ).apply(i, GrindingMedia::new));

    /** Synced to clients so tooltips and the GUI can show the stats. */
    public static final DataMapType<Item, GrindingMedia> TYPE = DataMapType.builder(Robotica.id("grinding_media"), Registries.ITEM, CODEC)
            .synced(CODEC, false).build();

    @Nullable
    public static GrindingMedia of(ItemStack stack) {
        return stack.isEmpty() ? null : stack.getItemHolder().getData(TYPE);
    }

    /** True when this media fits a Grinder of the given Mk. */
    public boolean fits(int machineTier) {
        return tier <= machineTier;
    }
}
