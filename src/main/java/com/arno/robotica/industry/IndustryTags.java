package com.arno.robotica.industry;

import com.arno.robotica.Robotica;
import net.minecraft.core.registries.Registries;
import net.minecraft.tags.TagKey;
import net.minecraft.world.item.Item;

/** Item tags the industry module reads. The c: material tags live in src/main/fragments/industry. */
public final class IndustryTags {
    private IndustryTags() {}

    /** Pellets the RTG burns (thorium fuel pellet; packs may add their own). */
    public static final TagKey<Item> RTG_FUEL = TagKey.create(Registries.ITEM, Robotica.id("rtg_fuel"));
}
