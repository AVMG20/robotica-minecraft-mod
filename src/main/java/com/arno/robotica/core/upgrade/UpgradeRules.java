package com.arno.robotica.core.upgrade;

import com.arno.robotica.core.CoreConfig;

import java.util.EnumMap;
import java.util.Map;
import java.util.Set;

/**
 * Card slots and caps of every machine, in one place.
 *
 * <p>A machine with a Mk (Mk1-Mk4) follows one rule: {@link #mkSlots} = Mk + 1, caps from {@link #mkCap} (speed 2 x Mk,
 * efficiency Mk, range Mk, growth Mk, fortune min(Mk, 3), silk and void 1; per-Mk numbers in {@link CoreConfig}).
 * Machines without a Mk are listed in {@link Fixed}; their caps are server config too.
 */
public final class UpgradeRules {
    private UpgradeRules() {}

    public static final int MAX_MK = 4;
    /** Card slots of a Mk4 machine: every Mk machine keeps this many slots inside, the Mk opens {@link #mkSlots}. */
    public static final int MAX_SLOTS = MAX_MK + 1;

    private static int clampMk(int mk) {
        return Math.max(1, Math.min(MAX_MK, mk));
    }

    /** Card slots of a machine of this Mk: Mk + 1 (2 to 5). */
    public static int mkSlots(int mk) {
        return clampMk(mk) + 1;
    }

    /** Most cards of a kind that count in a machine of this Mk (before the machine accepts the kind at all). */
    public static int mkCap(int mk, UpgradeKind kind) {
        int m = clampMk(mk);
        int cap = switch (kind) {
            case SPEED -> CoreConfig.speedCapPerMk() * m;
            case EFFICIENCY -> CoreConfig.efficiencyCapPerMk() * m;
            case RANGE -> CoreConfig.rangeCapPerMk() * m;
            case FORTUNE -> Math.min(m, CoreConfig.fortuneCapMax());
            case GROWTH -> CoreConfig.growthCapPerMk() * m;
            default -> 1;
        };
        return Math.min(kind.maxStack, cap);
    }

    /** Machines without a Mk: fixed slots and caps. */
    public enum Fixed {
        METAL_PRESS(2, limit(UpgradeKind.SPEED, 4), limit(UpgradeKind.EFFICIENCY, 4)),
        COMBUSTION_GENERATOR(2, limit(UpgradeKind.SPEED, 3), limit(UpgradeKind.EFFICIENCY, 4)),
        WIRELESS_CHARGER(2, limit(UpgradeKind.SPEED, 4), limit(UpgradeKind.RANGE, 4)),
        REPLICATOR(3, limit(UpgradeKind.SPEED, 3), limit(UpgradeKind.FORTUNE, 3), limit(UpgradeKind.EFFICIENCY, 4)),
        ARCHITECT_TABLE(2, limit(UpgradeKind.SPEED, 8), limit(UpgradeKind.EFFICIENCY, 4), limit(UpgradeKind.HEIGHT, 6)),
        SENTRY_DRONE(2, limit(UpgradeKind.SPEED, 2), limit(UpgradeKind.RANGE, 4), limit(UpgradeKind.EFFICIENCY, 4)),
        COURIER_DRONE(2, limit(UpgradeKind.SPEED, 2), limit(UpgradeKind.RANGE, 4), limit(UpgradeKind.EFFICIENCY, 4));

        public final int slots;
        private final Map<UpgradeKind, Integer> caps = new EnumMap<>(UpgradeKind.class);

        Fixed(int slots, Cap... caps) {
            this.slots = slots;
            for (Cap c : caps) this.caps.put(c.kind(), Math.min(c.kind().maxStack, c.cap()));
        }

        public Set<UpgradeKind> kinds() {
            return caps.keySet();
        }

        /** Most cards of a kind that count (server config, default from this table). */
        public int cap(UpgradeKind kind) {
            Integer def = caps.get(kind);
            return def == null ? 0 : Math.min(kind.maxStack, CoreConfig.fixedCap(name(), kind.name(), def));
        }

        /** The built-in default cap (the config's default). */
        public int defaultCap(UpgradeKind kind) {
            return caps.getOrDefault(kind, 0);
        }
    }

    record Cap(UpgradeKind kind, int cap) {}

    private static Cap limit(UpgradeKind kind, int cap) {
        return new Cap(kind, cap);
    }
}
