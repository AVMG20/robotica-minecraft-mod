package com.arno.robotica.drones;

import net.neoforged.neoforge.common.ModConfigSpec;

/**
 * Balance values of the drones module (server config, file robotica-drones-server.toml).
 * Read through the static helpers: they fall back to the defaults before the config is loaded.
 */
public final class DronesConfig {
    private DronesConfig() {}

    public static final ModConfigSpec SPEC;

    private static final ModConfigSpec.IntValue MINING_FE_PER_BLOCK;
    private static final ModConfigSpec.IntValue MINING_DIG_TICKS;
    private static final ModConfigSpec.IntValue TUNNEL_MAX_LENGTH;
    private static final ModConfigSpec.IntValue TUNNEL_DEFAULT_LENGTH;
    private static final ModConfigSpec.IntValue TORCH_SPACING;
    private static final ModConfigSpec.IntValue MINING_BUFFER;
    private static final ModConfigSpec.IntValue MINING_BUFFER_MK2;
    private static final ModConfigSpec.IntValue MINING_HEALTH;

    private static final ModConfigSpec.DoubleValue SENTRY_DAMAGE;
    private static final ModConfigSpec.IntValue SENTRY_FE_PER_SHOT;
    private static final ModConfigSpec.IntValue SENTRY_COOLDOWN;
    private static final ModConfigSpec.IntValue SENTRY_RANGE;
    private static final ModConfigSpec.IntValue SENTRY_BUFFER;
    private static final ModConfigSpec.IntValue SENTRY_BUFFER_MK2;
    private static final ModConfigSpec.IntValue SENTRY_HEALTH;

    private static final ModConfigSpec.IntValue COURIER_FE_PER_TRIP;
    private static final ModConfigSpec.IntValue COURIER_BUFFER;
    private static final ModConfigSpec.IntValue COURIER_BUFFER_MK2;
    private static final ModConfigSpec.IntValue COURIER_MAX_ROUTE;
    private static final ModConfigSpec.IntValue COURIER_HEALTH;

    private static final ModConfigSpec.IntValue LOW_ENERGY_PERCENT;
    private static final ModConfigSpec.DoubleValue MK2_SPEED;
    private static final ModConfigSpec.DoubleValue MK2_DAMAGE;
    private static final ModConfigSpec.IntValue REPAIR_AMOUNT;
    private static final ModConfigSpec.IntValue COMMAND_DISTANCE;

    static {
        ModConfigSpec.Builder b = new ModConfigSpec.Builder();
        b.push("mining_drone");
        MINING_FE_PER_BLOCK = b.comment("FE per mined block.").defineInRange("miningFePerBlock", 80, 0, 1_000_000);
        MINING_DIG_TICKS = b.comment("Ticks between two mined blocks (Mk2 is faster).").defineInRange("miningDigTicks", 8, 1, 200);
        TUNNEL_MAX_LENGTH = b.comment("Longest tunnel a player can order (the GUI offers 16, 32, 64 and 128 up to this value).")
                .defineInRange("tunnelMaxLength", 128, 16, 512);
        TUNNEL_DEFAULT_LENGTH = b.comment("Tunnel length of a freshly crafted drone.").defineInRange("tunnelDefaultLength", 64, 1, 512);
        TORCH_SPACING = b.comment("A torch from the torch slot is placed every this many slices.").defineInRange("torchSpacing", 8, 2, 64);
        MINING_BUFFER = b.comment("Internal FE buffer of the Mining Drone.").defineInRange("miningBuffer", 500_000, 1_000, 100_000_000);
        MINING_BUFFER_MK2 = b.comment("Internal FE buffer of the Mining Drone Mk2.").defineInRange("miningBufferMk2", 2_000_000, 1_000, 100_000_000);
        MINING_HEALTH = b.comment("Hit points of the Mining Drone.").defineInRange("miningHealth", 20, 1, 1000);
        b.pop();
        b.push("sentry_drone");
        SENTRY_DAMAGE = b.comment("Damage of one energy bolt.").defineInRange("sentryDamage", 5.0, 0.5, 100.0);
        SENTRY_FE_PER_SHOT = b.comment("FE per shot.").defineInRange("sentryFePerShot", 200, 0, 1_000_000);
        SENTRY_COOLDOWN = b.comment("Ticks between two shots.").defineInRange("sentryCooldown", 20, 2, 400);
        SENTRY_RANGE = b.comment("Firing range in blocks.").defineInRange("sentryRange", 16, 4, 64);
        SENTRY_BUFFER = b.comment("Internal FE buffer of the Sentry Drone.").defineInRange("sentryBuffer", 400_000, 1_000, 100_000_000);
        SENTRY_BUFFER_MK2 = b.comment("Internal FE buffer of the Sentry Drone Mk2.").defineInRange("sentryBufferMk2", 1_600_000, 1_000, 100_000_000);
        SENTRY_HEALTH = b.comment("Hit points of the Sentry Drone.").defineInRange("sentryHealth", 30, 1, 1000);
        b.pop();
        b.push("courier_drone");
        COURIER_FE_PER_TRIP = b.comment("FE per trip (one pick-up and delivery).").defineInRange("courierFePerTrip", 100, 0, 1_000_000);
        COURIER_BUFFER = b.comment("Internal FE buffer of the Courier Drone.").defineInRange("courierBuffer", 300_000, 1_000, 100_000_000);
        COURIER_BUFFER_MK2 = b.comment("Internal FE buffer of the Courier Drone Mk2.").defineInRange("courierBufferMk2", 1_200_000, 1_000, 100_000_000);
        COURIER_MAX_ROUTE = b.comment("Longest route (distance between source and target) in blocks, before range cards.")
                .defineInRange("courierMaxRoute", 64, 4, 1024);
        COURIER_HEALTH = b.comment("Hit points of the Courier Drone.").defineInRange("courierHealth", 20, 1, 1000);
        b.pop();
        b.push("both");
        LOW_ENERGY_PERCENT = b.comment("A drone stops working and returns when its energy falls below this percentage.")
                .defineInRange("lowEnergyPercent", 10, 0, 90);
        MK2_SPEED = b.comment("Mk2 multiplier of the dig speed and the fire rate.").defineInRange("mk2Speed", 1.5, 1.0, 10.0);
        MK2_DAMAGE = b.comment("Mk2 multiplier of the bolt damage.").defineInRange("mk2Damage", 1.5, 1.0, 10.0);
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

    public static int miningFePerBlock() { return i(MINING_FE_PER_BLOCK); }
    public static int miningDigTicks() { return i(MINING_DIG_TICKS); }
    public static int tunnelMaxLength() { return i(TUNNEL_MAX_LENGTH); }
    public static int tunnelDefaultLength() { return Math.min(i(TUNNEL_DEFAULT_LENGTH), tunnelMaxLength()); }
    public static int torchSpacing() { return i(TORCH_SPACING); }
    public static int miningBuffer(int tier) { return tier >= 2 ? i(MINING_BUFFER_MK2) : i(MINING_BUFFER); }
    public static int miningHealth(int tier) { return Math.round(i(MINING_HEALTH) * (tier >= 2 ? 1.5F : 1.0F)); }

    public static float sentryDamage(int tier) { return (float) (d(SENTRY_DAMAGE) * (tier >= 2 ? d(MK2_DAMAGE) : 1.0)); }
    public static int sentryFePerShot() { return i(SENTRY_FE_PER_SHOT); }
    public static int sentryCooldown(int tier) { return Math.max(2, (int) Math.round(i(SENTRY_COOLDOWN) / (tier >= 2 ? d(MK2_SPEED) : 1.0))); }
    public static int sentryRange() { return i(SENTRY_RANGE); }
    public static int sentryBuffer(int tier) { return tier >= 2 ? i(SENTRY_BUFFER_MK2) : i(SENTRY_BUFFER); }
    public static int sentryHealth(int tier) { return Math.round(i(SENTRY_HEALTH) * (tier >= 2 ? 1.5F : 1.0F)); }

    public static int courierFePerTrip() { return i(COURIER_FE_PER_TRIP); }
    public static int courierBuffer(int tier) { return tier >= 2 ? i(COURIER_BUFFER_MK2) : i(COURIER_BUFFER); }
    public static int courierMaxRoute() { return i(COURIER_MAX_ROUTE); }
    public static int courierHealth(int tier) { return Math.round(i(COURIER_HEALTH) * (tier >= 2 ? 1.5F : 1.0F)); }

    public static int lowEnergyPercent() { return i(LOW_ENERGY_PERCENT); }
    public static double mk2Speed() { return d(MK2_SPEED); }
    public static int repairAmount() { return i(REPAIR_AMOUNT); }
    public static int commandDistance() { return i(COMMAND_DISTANCE); }
}
