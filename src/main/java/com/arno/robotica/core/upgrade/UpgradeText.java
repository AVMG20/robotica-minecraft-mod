package com.arno.robotica.core.upgrade;

import com.arno.robotica.core.CoreConfig;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.IntSupplier;

/**
 * Card texts that show config numbers (tooltips, JEI pages). Numbers owned by other modules (the Excavator's range step,
 * the Wireless Charger's) are registered by name in their module init; before that the defaults show.
 */
public final class UpgradeText {
    private UpgradeText() {}

    public static final String EXCAVATOR_RANGE_STEP = "excavatorRangeStep";
    public static final String WIRELESS_RANGE_STEP = "wirelessRangePerCard";
    /** Percent less fuel per efficiency card in the Combustion Generator. */
    public static final String GENERATOR_EFFICIENCY = "generatorEfficiencyPerCard";
    /** Architect buildings without Height cards (floor, 4 wall layers, roof). */
    public static final int BASE_BUILDING_HEIGHT = 6;

    private static final Map<String, IntSupplier> VALUES = new ConcurrentHashMap<>();

    /** A module's config number a card text shows. */
    public static void register(String name, IntSupplier value) {
        VALUES.put(name, value);
    }

    private static int value(String name, int fallback) {
        IntSupplier v = VALUES.get(name);
        return v == null ? fallback : v.getAsInt();
    }

    /** Translation arguments of {@code tooltip.robotica.upgrade.<kind>.steps} and {@code jei.robotica.info.upgrade.<kind>}. */
    public static Object[] args(UpgradeKind kind) {
        return switch (kind) {
            case RANGE -> new Object[]{value(EXCAVATOR_RANGE_STEP, 10), value(WIRELESS_RANGE_STEP, 4)};
            case EFFICIENCY -> new Object[]{value(GENERATOR_EFFICIENCY, 10)};
            case HEIGHT -> {
                int cap = UpgradeRules.Fixed.ARCHITECT_TABLE.cap(UpgradeKind.HEIGHT);
                yield new Object[]{cap, BASE_BUILDING_HEIGHT + cap};
            }
            default -> new Object[0];
        };
    }

    public static MutableComponent steps(UpgradeKind kind) {
        return Component.translatable("tooltip.robotica.upgrade." + kind.id() + ".steps", args(kind));
    }

    /** "Machines with a Mk: Mk + 1 slots. Per Mk: 2 speed, 1 efficiency ..." from the core config. */
    public static MutableComponent caps() {
        return Component.translatable("tooltip.robotica.upgrade_caps", CoreConfig.speedCapPerMk(), CoreConfig.efficiencyCapPerMk(),
                CoreConfig.rangeCapPerMk(), CoreConfig.growthCapPerMk(), CoreConfig.fortuneCapMax());
    }
}
