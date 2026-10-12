package com.arno.robotica.warp.pad;

import com.arno.robotica.warp.WarpConfig;

/** FE cost formulas for pad trips. */
public final class WarpCosts {
    private WarpCosts() {}

    /** Pure formula: flat cost across dimensions, otherwise base + perBlock * distance (rounded to blocks). */
    public static int padTrip(double distance, boolean crossDimension, int base, int perBlock, int crossCost) {
        if (crossDimension) return crossCost;
        long cost = (long) base + Math.round(Math.max(0.0, distance)) * perBlock;
        return (int) Math.min(Integer.MAX_VALUE, cost);
    }

    /** Cost with the values of the server config. */
    public static int padTrip(double distance, boolean crossDimension) {
        return padTrip(distance, crossDimension, WarpConfig.padBaseCost(), WarpConfig.padCostPerBlock(), WarpConfig.padCrossCost());
    }
}
