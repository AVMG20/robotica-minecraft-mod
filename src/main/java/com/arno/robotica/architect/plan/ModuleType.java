package com.arno.robotica.architect.plan;

import java.util.List;
import java.util.Locale;

/** The module catalogue. Ids 1-8 are used on the wire and in the table's NBT (0 means empty). */
public enum ModuleType {
    CORRIDOR(0b1111),
    HALL(0b1111),
    WORKSHOP(0b1111),
    STORAGE_ROOM(0b1111),
    MACHINE_HALL(0b1111),
    GREENHOUSE(0b1111),
    HANGAR(0b1111),
    /** The stairs fill the north strip, so there is no north door. */
    STAIRWELL(0b1110);

    private final int supportedMask;

    ModuleType(int supportedMask) {
        this.supportedMask = supportedMask;
    }

    public int id() {
        return ordinal() + 1;
    }

    public String key() {
        return name().toLowerCase(Locale.ROOT);
    }

    public String langKey() {
        return "module.robotica." + key();
    }

    public int supportedMask() {
        return supportedMask;
    }

    /** Module for a wire id, or null for 0 and unknown ids. */
    public static ModuleType byId(int id) {
        ModuleType[] all = values();
        return id >= 1 && id <= all.length ? all[id - 1] : null;
    }

    /**
     * The door mask this module is actually built with for a wanted set of sides: only supported sides, a lone entrance
     * on the south when nothing is wanted, and corridors always run straight through (both ends of each axis they touch).
     */
    public int effectiveMask(int wanted) {
        int m = wanted & supportedMask;
        if (this == CORRIDOR) {
            for (int side = 0; side < 4; side++) {
                if ((m & Plots.bit(side)) != 0) m |= Plots.bit(Plots.opposite(side));
            }
            return m == 0 ? Plots.bit(Plots.N) | Plots.bit(Plots.S) : m;
        }
        return m == 0 ? Plots.bit(Plots.S) : m;
    }

    /** All placements of this module for a door mask, bottom-up. Local coordinates, see {@link Plots}. */
    public List<BlockOp> generate(int doorMask) {
        return ModuleGenerator.generate(this, effectiveMask(doorMask));
    }
}
