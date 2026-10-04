package com.arno.robotica.architect.style;

import com.arno.robotica.architect.matter.Matter;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;

import java.util.Locale;
import java.util.function.Supplier;

/**
 * Building style families. Each has six block roles ({@link Role}), a matter price per fabricated block and the casing
 * level the table needs in its style slot (0 = always available). Registry names are {@code <id>_<role>}.
 */
public enum BuildStyle {
    TIMBERFRAME("timberframe", 0, 0, new Matter(2, 0, 0), 14, () -> Blocks.OAK_STAIRS),
    COPPER_WORKS("copper_works", 1, 1, new Matter(2, 1, 0), 14, () -> Blocks.CUT_COPPER_STAIRS),
    STEEL_LAB("steel_lab", 2, 2, new Matter(1, 2, 1), 15, () -> Blocks.QUARTZ_STAIRS),
    NULL_SPIRE("null_spire", 4, 4, new Matter(0, 2, 2), 15, () -> Blocks.POLISHED_BLACKSTONE_STAIRS);

    public final String id;
    public final int age;
    /** Casing level needed in the style slot: 1 iron, 2 reinforced, 3 blazing, 4 null casing. */
    public final int casingLevel;
    public final Matter cost;
    public final int lightLevel;
    private final Supplier<Block> stairs;

    BuildStyle(String id, int age, int casingLevel, Matter cost, int lightLevel, Supplier<Block> stairs) {
        this.id = id;
        this.age = age;
        this.casingLevel = casingLevel;
        this.cost = cost;
        this.lightLevel = lightLevel;
        this.stairs = stairs;
    }

    public Block stairs() {
        return stairs.get();
    }

    public String blockName(Role role) {
        return id + "_" + role.id();
    }

    public String langKey() {
        return "style.robotica." + id;
    }

    public static BuildStyle byOrdinal(int ordinal) {
        BuildStyle[] all = values();
        return all[Math.floorMod(ordinal, all.length)];
    }

    public static BuildStyle byId(String id) {
        for (BuildStyle s : values()) if (s.id.equals(id.toLowerCase(Locale.ROOT))) return s;
        return TIMBERFRAME;
    }
}
