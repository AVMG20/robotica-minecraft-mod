package com.arno.robotica.drones;

import net.neoforged.neoforge.common.ModConfigSpec;

/**
 * Balance values of the drones module (server config, file robotica-drones-server.toml).
 * Read through the static helpers: they fall back to the defaults before the config is loaded.
 */
public final class DronesConfig {
    private DronesConfig() {}

    public static final ModConfigSpec SPEC;

    private static final ModConfigSpec.DoubleValue BUDDY_DAMAGE;
    private static final ModConfigSpec.IntValue BUDDY_FE_PER_SHOT;
    private static final ModConfigSpec.IntValue BUDDY_COOLDOWN;
    private static final ModConfigSpec.IntValue BUDDY_RANGE;
    private static final ModConfigSpec.IntValue BUDDY_FE_PER_BLOCK;
    private static final ModConfigSpec.IntValue BUDDY_DIG_TICKS;
    private static final ModConfigSpec.IntValue TUNNEL_LENGTH;
    private static final ModConfigSpec.IntValue BUDDY_BUFFER;
    private static final ModConfigSpec.IntValue BUDDY_HEALTH;

    private static final ModConfigSpec.IntValue COURIER_FE_PER_TRIP;
    private static final ModConfigSpec.IntValue COURIER_BUFFER;
    private static final ModConfigSpec.IntValue COURIER_BUFFER_MK2;
    private static final ModConfigSpec.IntValue COURIER_MAX_ROUTE;
    private static final ModConfigSpec.IntValue COURIER_HEALTH;

    private static final ModConfigSpec.IntValue HAULER_FE_PER_TICK;
    private static final ModConfigSpec.IntValue HAULER_BUFFER;
    private static final ModConfigSpec.IntValue HAULER_BUFFER_MK2;
    private static final ModConfigSpec.IntValue HAULER_HEALTH;


    private static final ModConfigSpec.IntValue LOW_ENERGY_PERCENT;
    private static final ModConfigSpec.DoubleValue MK2_SPEED;
    private static final ModConfigSpec.IntValue REPAIR_AMOUNT;
    private static final ModConfigSpec.IntValue COMMAND_DISTANCE;

    static {
        ModConfigSpec.Builder b = new ModConfigSpec.Builder();
        b.push("buddy_bot");
        BUDDY_DAMAGE = b.comment("Damage of one energy bolt (Mk1; Mk2 x1.5, Mk3 x2, Mk4 x2.4).").defineInRange("buddyDamage", 5.0, 0.5, 100.0);
        BUDDY_FE_PER_SHOT = b.comment("FE per shot.").defineInRange("buddyFePerShot", 200, 0, 1_000_000);
        BUDDY_COOLDOWN = b.comment("Ticks between two shots (Mk1; Mk2 /1.5, Mk3 /2, Mk4 /2.5).").defineInRange("buddyCooldown", 20, 2, 400);
        BUDDY_RANGE = b.comment("Firing range in blocks (Mk1; +4 per Mk).").defineInRange("buddyRange", 16, 4, 64);
        BUDDY_FE_PER_BLOCK = b.comment("FE per mined block.").defineInRange("buddyFePerBlock", 80, 0, 1_000_000);
        BUDDY_DIG_TICKS = b.comment("Ticks between two mined blocks (Mk1; Mk2 x1.5, Mk3 x2.5, Mk4 x3.5 as fast).").defineInRange("buddyDigTicks", 12, 1, 200);
        TUNNEL_LENGTH = b.comment("Length of a tunnel in blocks.").defineInRange("tunnelLength", 64, 4, 512);
        BUDDY_BUFFER = b.comment("Internal FE buffer of the Mk1 (each Mk holds 4 times more).").defineInRange("buddyBuffer", 500_000, 1_000, 100_000_000);
        BUDDY_HEALTH = b.comment("Hit points of the Mk1 (Mk2 x1.5, Mk3 x2, Mk4 x2.67).").defineInRange("buddyHealth", 30, 1, 1000);
        b.pop();
        b.push("courier_drone");
        COURIER_FE_PER_TRIP = b.comment("FE per trip (one pick-up and delivery).").defineInRange("courierFePerTrip", 100, 0, 1_000_000);
        COURIER_BUFFER = b.comment("Internal FE buffer of the Courier Drone.").defineInRange("courierBuffer", 300_000, 1_000, 100_000_000);
        COURIER_BUFFER_MK2 = b.comment("Internal FE buffer of the Courier Drone Mk2.").defineInRange("courierBufferMk2", 1_200_000, 1_000, 100_000_000);
        COURIER_MAX_ROUTE = b.comment("Longest route (distance between source and target) in blocks, before range cards.")
                .defineInRange("courierMaxRoute", 64, 4, 1024);
        COURIER_HEALTH = b.comment("Hit points of the Courier Drone.").defineInRange("courierHealth", 20, 1, 1000);
        b.pop();
        b.push("hauler_drone");
        HAULER_FE_PER_TICK = b.comment("FE per tick while carrying a mob (Mk2 x2).").defineInRange("haulerFePerTick", 2, 0, 100_000);
        HAULER_BUFFER = b.comment("Internal FE buffer of the Hauler Drone.").defineInRange("haulerBuffer", 100_000, 1_000, 100_000_000);
        HAULER_BUFFER_MK2 = b.comment("Internal FE buffer of the Hauler Drone Mk2.").defineInRange("haulerBufferMk2", 400_000, 1_000, 100_000_000);
        HAULER_HEALTH = b.comment("Hit points of the Hauler Drone (Mk2 x1.5).").defineInRange("haulerHealth", 20, 1, 1000);
        b.pop();
        b.push("both");
        LOW_ENERGY_PERCENT = b.comment("A drone stops working and returns when its energy falls below this percentage.")
                .defineInRange("lowEnergyPercent", 10, 0, 90);
        MK2_SPEED = b.comment("Mk2 multiplier of the Hauler Drone speed.").defineInRange("mk2Speed", 1.5, 1.0, 10.0);
        REPAIR_AMOUNT = b.comment("Hit points restored by one iron ingot or plate.").defineInRange("repairAmount", 8, 1, 1000);
        COMMAND_DISTANCE = b.comment("How far from the player a drone may be to obey the command key.").defineInRange("commandDistance", 48, 4, 256);
        b.pop();
        SPEC = b.build();
    }

    private static int i(ModConfigSpec.IntValue v) {
        return SPEC.isLoaded() ? v.get() : v.getDefault();
    }

    private static double d(ModConfigSpec.DoubleValue v) {
        return SPEC.isLoaded() ? v.get() : v.getDefault();
    }

    private static final double[] DAMAGE = {1.0, 1.0, 1.5, 2.0, 2.4};
    private static final double[] FIRE_RATE = {1.0, 1.0, 1.5, 2.0, 2.5};
    private static final double[] DIG_SPEED = {1.0, 1.0, 1.5, 2.5, 3.5};
    private static final double[] HEALTH = {1.0, 1.0, 1.5, 2.0, 8.0 / 3.0};

    private static int t(int tier) { return Math.max(1, Math.min(4, tier)); }

    public static float buddyDamage(int tier) { return (float) (d(BUDDY_DAMAGE) * DAMAGE[t(tier)]); }
    public static int buddyFePerShot() { return i(BUDDY_FE_PER_SHOT); }
    public static int buddyCooldown(int tier) { return Math.max(2, (int) Math.round(i(BUDDY_COOLDOWN) / FIRE_RATE[t(tier)])); }
    public static int buddyRange() { return i(BUDDY_RANGE); }
    public static int buddyFePerBlock() { return i(BUDDY_FE_PER_BLOCK); }
    public static int buddyDigTicks() { return i(BUDDY_DIG_TICKS); }
    /** Dig speed multiplier of a tier: 1, 1.5, 2.5, 3.5. */
    public static double buddyDigSpeed(int tier) { return DIG_SPEED[t(tier)]; }
    public static int tunnelLength() { return i(TUNNEL_LENGTH); }
    /** 500k, 2M, 8M, 32M by default. */
    public static int buddyBuffer(int tier) { return (int) Math.min(Integer.MAX_VALUE, (long) i(BUDDY_BUFFER) << (2 * (t(tier) - 1))); }
    public static int buddyHealth(int tier) { return (int) Math.round(i(BUDDY_HEALTH) * HEALTH[t(tier)]); }

    public static int courierFePerTrip() { return i(COURIER_FE_PER_TRIP); }
    public static int courierBuffer(int tier) { return tier >= 2 ? i(COURIER_BUFFER_MK2) : i(COURIER_BUFFER); }
    public static int courierMaxRoute() { return i(COURIER_MAX_ROUTE); }
    public static int courierHealth(int tier) { return Math.round(i(COURIER_HEALTH) * (tier >= 2 ? 1.5F : 1.0F)); }

    public static int haulerFePerTick() { return i(HAULER_FE_PER_TICK); }
    public static int haulerBuffer(int tier) { return tier >= 2 ? i(HAULER_BUFFER_MK2) : i(HAULER_BUFFER); }
    public static int haulerHealth(int tier) { return Math.round(i(HAULER_HEALTH) * (tier >= 2 ? 1.5F : 1.0F)); }


    public static int lowEnergyPercent() { return i(LOW_ENERGY_PERCENT); }
    public static double mk2Speed() { return d(MK2_SPEED); }
    public static int repairAmount() { return i(REPAIR_AMOUNT); }
    public static int commandDistance() { return i(COMMAND_DISTANCE); }
}
