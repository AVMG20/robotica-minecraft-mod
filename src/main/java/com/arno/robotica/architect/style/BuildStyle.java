package com.arno.robotica.architect.style;

import com.arno.robotica.architect.matter.Matter;

import java.util.Locale;

/**
 * Building style families, two light/dark pairs with the same shapes. Each has six block roles ({@link Role}), a matter
 * price per fabricated block and the casing level the table needs in its style slot (0 = always available). Registry
 * names are {@code <id>_<role>}. At most four styles: plot records keep the style in two bits.
 */
public enum BuildStyle {
    TIMBERFRAME("timberframe", 0, 0, new Matter(2, 0, 0), 50, 14),
    DARK_STONE("dark_stone", 0, 0, new Matter(2, 0, 0), 50, 14),
    PEARL_PANEL("pearl_panel", 1, 1, new Matter(2, 1, 0), 100, 15),
    ONYX_PANEL("onyx_panel", 1, 1, new Matter(2, 1, 0), 100, 15);

    public final String id;
    public final int age;
    /** Casing level needed in the style slot: 0 none, 1 an Iron Casing or better. */
    public final int casingLevel;
    public final Matter cost;
    /** FE per block in percent of the configured base: Timberframe is cheap enough for a Mainspring on day one. */
    public final int energyPercent;
    public final int lightLevel;

    BuildStyle(String id, int age, int casingLevel, Matter cost, int energyPercent, int lightLevel) {
        this.id = id;
        this.age = age;
        this.casingLevel = casingLevel;
        this.cost = cost;
        this.energyPercent = energyPercent;
        this.lightLevel = lightLevel;
    }

    /** FE per block of this style for a base cost (before upgrade card modifiers). */
    public int energyPerBlock(int base) {
        return (int) Math.round(base * energyPercent / 100.0);
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
