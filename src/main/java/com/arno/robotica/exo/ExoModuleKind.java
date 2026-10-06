package com.arno.robotica.exo;

import net.minecraft.world.entity.EquipmentSlot;

/**
 * Every Exo-Frame module kind. One item per kind and level (see {@link ExoItems}); the kind decides which armor pieces
 * accept it, the level which mark it needs at least ({@link #minMark}).
 *
 * <p>Rules: a kind works once per suit (the highest installed level counts; the module screen refuses a second one),
 * except {@link #CAPACITOR_PLATING}, which works per piece and may sit once in every piece.
 */
public enum ExoModuleKind {
    // helmet
    NIGHT_VISION("night_vision", Pieces.HEAD, Unit.SECOND, 1, 2, 3),
    REBREATHER("rebreather", Pieces.HEAD, Unit.SECOND, 2),
    ROBOT_HUD("robot_hud", Pieces.HEAD, Unit.SECOND, 2),
    AUTO_FEEDER("auto_feeder", Pieces.HEAD, Unit.FOOD, 1),
    SOLAR_WEAVE("solar_weave", Pieces.HEAD, Unit.SOLAR, 2),
    SONAR_PULSE("sonar_pulse", Pieces.HEAD, Unit.USE, 2, 3, 4),
    // chestplate
    JET_ASSIST("jet_assist", Pieces.CHEST, Unit.SECOND, 2, 3, 4),
    FLIGHT("flight", Pieces.CHEST, Unit.SECOND, 3),
    KINETIC_SHIELD("kinetic_shield", Pieces.CHEST, Unit.DAMAGE, 3, 4, 4),
    MED_INJECTOR("med_injector", Pieces.CHEST, Unit.USE, 2, 3, 4),
    HAZARD_SEAL("hazard_seal", Pieces.CHEST, Unit.EFFECT, 3),
    // leggings
    SERVO_STRIDE("servo_stride", Pieces.LEGS, Unit.SECOND, 1, 2, 3),
    KINETIC_GENERATOR("kinetic_generator", Pieces.LEGS, Unit.WALK, 1),
    DASH_THRUSTERS("dash_thrusters", Pieces.LEGS | Pieces.FEET, Unit.USE, 3),
    // boots
    STEP_ASSIST("step_assist", Pieces.FEET, Unit.SECOND, 1),
    SPRING_HEELS("spring_heels", Pieces.FEET, Unit.JUMP, 1, 2, 3),
    FALL_DAMPENER("fall_dampener", Pieces.FEET, Unit.BLOCK, 1, 2, 3),
    MAGNET("magnet", Pieces.FEET, Unit.SECOND, 1, 2, 3),
    HYDRO_FINS("hydro_fins", Pieces.FEET, Unit.SECOND, 2),
    // any piece
    CAPACITOR_PLATING("capacitor_plating", Pieces.ANY, Unit.PASSIVE, 1, 2, 3),
    POWER_REGULATOR("power_regulator", Pieces.ANY, Unit.PASSIVE, 2, 3, 4);

    /** Bit masks of the armor pieces (bit = piece index: 0 head, 1 chest, 2 legs, 3 feet). */
    public static final class Pieces {
        private Pieces() {}

        public static final int HEAD = 1, CHEST = 2, LEGS = 4, FEET = 8, ANY = 15;
    }

    /** What the configured cost is charged per (or, for SOLAR and WALK, what the module makes). */
    public enum Unit {
        SECOND, JUMP, BLOCK, DAMAGE, FOOD, USE, EFFECT, SOLAR, WALK, PASSIVE
    }

    public final String id;
    /** Bit set of the piece indices that accept this kind. */
    public final int pieces;
    public final Unit unit;
    /** Lowest Exo mark per level (index level - 1). Its length is the number of levels. */
    private final int[] minMarks;

    ExoModuleKind(String id, int pieces, Unit unit, int... minMarks) {
        this.id = id;
        this.pieces = pieces;
        this.unit = unit;
        this.minMarks = minMarks;
    }

    public int maxLevel() {
        return minMarks.length;
    }

    public boolean leveled() {
        return minMarks.length > 1;
    }

    /** Lowest mark (1-4) of the armor piece that may hold this kind at {@code level}. */
    public int minMark(int level) {
        return minMarks[Math.max(0, Math.min(minMarks.length, level) - 1)];
    }

    /** True when the piece in this equipment slot accepts the kind. */
    public boolean fits(EquipmentSlot slot) {
        return (pieces & (1 << slotIndex(slot))) != 0;
    }

    /** True when one module of this kind may sit in every piece (it acts on its own piece only). */
    public boolean perPiece() {
        return this == CAPACITOR_PLATING;
    }

    /** Registry name of the module item at a level (1 based). Level I keeps the old names. */
    public String itemName(int level) {
        if (this == SERVO_STRIDE) return "servo_stride_module_" + level;
        return level <= 1 ? id + "_module" : id + "_module_" + level;
    }

    /** Lang key of the kind without level (used in messages). */
    public String kindKey() {
        return "exo.robotica.kind." + id;
    }

    /** 0 head, 1 chest, 2 legs, 3 feet. */
    public static int slotIndex(EquipmentSlot slot) {
        return switch (slot) {
            case HEAD -> 0;
            case CHEST -> 1;
            case LEGS -> 2;
            case FEET -> 3;
            default -> throw new IllegalArgumentException("not an armor slot: " + slot);
        };
    }

    /** Roman numeral of a level 1-4. */
    public static String roman(int level) {
        return switch (level) {
            case 2 -> "II";
            case 3 -> "III";
            case 4 -> "IV";
            default -> "I";
        };
    }
}
