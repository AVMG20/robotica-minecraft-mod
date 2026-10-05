package com.arno.robotica.exo;

import net.minecraft.world.entity.EquipmentSlot;

/** Every Exo-Frame module. One item per kind (see {@link ExoItems}); the kind decides which armor piece accepts it. */
public enum ExoModuleKind {
    NIGHT_VISION("night_vision", EquipmentSlot.HEAD, Unit.SECOND, 0),
    REBREATHER("rebreather", EquipmentSlot.HEAD, Unit.SECOND, 0),
    ROBOT_HUD("robot_hud", EquipmentSlot.HEAD, Unit.SECOND, 0),
    JET_ASSIST("jet_assist", EquipmentSlot.CHEST, Unit.SECOND, 0),
    FLIGHT("flight", EquipmentSlot.CHEST, Unit.SECOND, 0),
    KINETIC_SHIELD("kinetic_shield", EquipmentSlot.CHEST, Unit.DAMAGE, 0),
    SERVO_STRIDE_1("servo_stride_1", EquipmentSlot.LEGS, Unit.SECOND, 1),
    SERVO_STRIDE_2("servo_stride_2", EquipmentSlot.LEGS, Unit.SECOND, 2),
    SERVO_STRIDE_3("servo_stride_3", EquipmentSlot.LEGS, Unit.SECOND, 3),
    STEP_ASSIST("step_assist", EquipmentSlot.LEGS, Unit.SECOND, 0),
    SPRING_HEELS("spring_heels", EquipmentSlot.FEET, Unit.JUMP, 0),
    FALL_DAMPENER("fall_dampener", EquipmentSlot.FEET, Unit.BLOCK, 0),
    MAGNET("magnet", EquipmentSlot.FEET, Unit.SECOND, 0);

    /** What the configured cost is charged per. */
    public enum Unit {
        SECOND, JUMP, BLOCK, DAMAGE
    }

    public final String id;
    public final EquipmentSlot slot;
    public final Unit unit;
    /** Servo Stride tier (1-3), 0 for every other module. */
    public final int level;

    ExoModuleKind(String id, EquipmentSlot slot, Unit unit, int level) {
        this.id = id;
        this.slot = slot;
        this.unit = unit;
        this.level = level;
    }

    public int bit() {
        return 1 << ordinal();
    }

    /** Registry name of the module item. */
    public String itemName() {
        return switch (this) {
            case SERVO_STRIDE_1 -> "servo_stride_module_1";
            case SERVO_STRIDE_2 -> "servo_stride_module_2";
            case SERVO_STRIDE_3 -> "servo_stride_module_3";
            default -> id + "_module";
        };
    }

    public boolean isServo() {
        return level > 0;
    }

    /** Lang key of the module's display name (the item name). */
    public String nameKey() {
        return "item.robotica." + itemName();
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

    public int pieceIndex() {
        return slotIndex(slot);
    }
}
