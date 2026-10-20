package com.arno.robotica.replicator;

import net.neoforged.neoforge.common.ModConfigSpec;

/**
 * Balance values of the replicator module (server config, file robotica-replicator-server.toml).
 * Read through the static helpers: they fall back to the defaults before the config is loaded.
 */
public final class ReplicatorConfig {
    private ReplicatorConfig() {}

    public static final ModConfigSpec SPEC;

    private static final ModConfigSpec.IntValue ENERGY_PER_TICK;
    private static final ModConfigSpec.IntValue CYCLE_TICKS;
    private static final ModConfigSpec.IntValue ENERGY_BUFFER;
    private static final ModConfigSpec.IntValue MAX_RECEIVE;
    private static final ModConfigSpec.IntValue BOOST_MULTIPLIER;
    private static final ModConfigSpec.BooleanValue HARVEST_XP;
    private static final ModConfigSpec.IntValue MAX_XP;
    private static final ModConfigSpec.BooleanValue ALLOW_SPAWN_MODE;
    private static final ModConfigSpec.IntValue SPAWN_MAX_SAME_TYPE;
    private static final ModConfigSpec.IntValue SPAWN_MAX_TOTAL;
    private static final ModConfigSpec.IntValue SPAWN_RADIUS;
    private static final ModConfigSpec.IntValue GOLEM_ENERGY_PER_TICK;
    private static final ModConfigSpec.IntValue GOLEM_CYCLE_TICKS;
    private static final ModConfigSpec.IntValue WITHER_ENERGY_PER_TICK;
    private static final ModConfigSpec.IntValue WITHER_CYCLE_TICKS;
    private static final ModConfigSpec.IntValue FARM_BUFFER;
    private static final ModConfigSpec.IntValue FARM_MAX_RECEIVE;

    static {
        ModConfigSpec.Builder b = new ModConfigSpec.Builder();
        b.push("replicator_controller");
        ENERGY_PER_TICK = b.comment("Base FE/t while working (before speed and efficiency cards).")
                .defineInRange("replicatorEnergyPerTick", 160, 0, 10_000_000);
        CYCLE_TICKS = b.comment("Ticks per cycle at base speed (1200 = one minute).")
                .defineInRange("replicatorCycleTicks", 1_200, 1, 1_000_000);
        ENERGY_BUFFER = b.comment("Internal FE buffer.")
                .defineInRange("replicatorBuffer", 1_000_000, 1_000, 2_000_000_000);
        MAX_RECEIVE = b.comment("Most FE/t the controller accepts from outside (any side).")
                .defineInRange("replicatorMaxReceive", 20_000, 1, 100_000_000);
        BOOST_MULTIPLIER = b.comment("Speed multiplier of a Plasma Actuator in the boost slot.")
                .defineInRange("replicatorBoostMultiplier", 2, 1, 20);
        HARVEST_XP = b.comment("Harvest mode stores the experience of every cycle; players claim it in the GUI.")
                .define("replicatorHarvestXp", true);
        MAX_XP = b.comment("Most experience points the controller stores.")
                .defineInRange("replicatorMaxXp", 1_000_000, 0, 100_000_000);
        b.pop();
        b.push("replicator_spawn");
        ALLOW_SPAWN_MODE = b.comment("Allow Spawn mode (spawns real mobs). When false the controller waits in that mode.")
                .define("replicatorAllowSpawn", true);
        SPAWN_MAX_SAME_TYPE = b.comment("Spawn mode waits while this many mobs of the same type are within the radius.")
                .defineInRange("replicatorSpawnMaxSameType", 8, 1, 256);
        SPAWN_MAX_TOTAL = b.comment("Spawn mode also waits while this many mobs of any type are within the radius (local mob cap).")
                .defineInRange("replicatorSpawnMaxTotal", 32, 1, 1024);
        SPAWN_RADIUS = b.comment("Radius in blocks of the two limits above.")
                .defineInRange("replicatorSpawnRadius", 8, 1, 64);
        b.pop();
        b.push("mob_farm");
        GOLEM_ENERGY_PER_TICK = b.comment("Iron Golem Farmer: base FE/t while working (before speed and efficiency cards).")
                .defineInRange("golemFarmEnergyPerTick", 240, 0, 10_000_000);
        GOLEM_CYCLE_TICKS = b.comment("Iron Golem Farmer: ticks per golem at base speed (600 = 30 seconds).")
                .defineInRange("golemFarmCycleTicks", 600, 20, 1_000_000);
        WITHER_ENERGY_PER_TICK = b.comment("Wither Farmer: base FE/t while working (before speed and efficiency cards).")
                .defineInRange("witherFarmEnergyPerTick", 2_000, 0, 10_000_000);
        WITHER_CYCLE_TICKS = b.comment("Wither Farmer: ticks per Wither at base speed (6000 = 5 minutes).")
                .defineInRange("witherFarmCycleTicks", 6_000, 20, 1_000_000);
        FARM_BUFFER = b.comment("Internal FE buffer of both farmers.")
                .defineInRange("mobFarmBuffer", 4_000_000, 1_000, 2_000_000_000);
        FARM_MAX_RECEIVE = b.comment("Most FE/t a farmer accepts through its ports.")
                .defineInRange("mobFarmMaxReceive", 100_000, 1, 100_000_000);
        b.pop();
        SPEC = b.build();
    }

    private static int get(ModConfigSpec.IntValue value) {
        return SPEC.isLoaded() ? value.get() : value.getDefault();
    }

    private static boolean get(ModConfigSpec.BooleanValue value) {
        return SPEC.isLoaded() ? value.get() : value.getDefault();
    }

    public static int energyPerTick() { return get(ENERGY_PER_TICK); }
    public static int cycleTicks() { return get(CYCLE_TICKS); }
    public static int energyBuffer() { return get(ENERGY_BUFFER); }
    public static int maxReceive() { return get(MAX_RECEIVE); }
    public static int boostMultiplier() { return get(BOOST_MULTIPLIER); }
    public static boolean harvestXp() { return get(HARVEST_XP); }
    public static int maxXp() { return get(MAX_XP); }
    public static boolean allowSpawnMode() { return get(ALLOW_SPAWN_MODE); }
    public static int spawnMaxSameType() { return get(SPAWN_MAX_SAME_TYPE); }
    public static int spawnMaxTotal() { return get(SPAWN_MAX_TOTAL); }
    public static int spawnRadius() { return get(SPAWN_RADIUS); }
    public static int golemEnergyPerTick() { return get(GOLEM_ENERGY_PER_TICK); }
    public static int golemCycleTicks() { return get(GOLEM_CYCLE_TICKS); }
    public static int witherEnergyPerTick() { return get(WITHER_ENERGY_PER_TICK); }
    public static int witherCycleTicks() { return get(WITHER_CYCLE_TICKS); }
    public static int farmBuffer() { return get(FARM_BUFFER); }
    public static int farmMaxReceive() { return get(FARM_MAX_RECEIVE); }
}
