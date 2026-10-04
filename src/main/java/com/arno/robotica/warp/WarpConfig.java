package com.arno.robotica.warp;

import com.arno.robotica.core.CoreConfig;
import net.neoforged.neoforge.common.ModConfigSpec;

/**
 * Balance values of the warp module (server config, file robotica-warp-server.toml).
 * FE costs go through {@link CoreConfig#scaleEnergy}. Every getter falls back to the default before the config is loaded.
 */
public final class WarpConfig {
    private WarpConfig() {}

    public static final ModConfigSpec SPEC;

    private static final ModConfigSpec.IntValue PAD_BASE_COST;
    private static final ModConfigSpec.IntValue PAD_COST_PER_BLOCK;
    private static final ModConfigSpec.IntValue PAD_RIFT_COST;
    private static final ModConfigSpec.IntValue PAD_BUFFER;
    private static final ModConfigSpec.IntValue PAD_MAX_RECEIVE;
    private static final ModConfigSpec.IntValue PAD_TRAVEL_COOLDOWN;
    private static final ModConfigSpec.IntValue REMOTE_COST;
    private static final ModConfigSpec.IntValue RIFT_REMOTE_COST;
    private static final ModConfigSpec.IntValue REMOTE_COOLDOWN_SECONDS;
    private static final ModConfigSpec.IntValue GATE_BUFFER;
    private static final ModConfigSpec.IntValue GATE_MAX_RECEIVE;
    private static final ModConfigSpec.IntValue GATE_IDLE_COST;
    private static final ModConfigSpec.IntValue GATE_ENTITY_COST;
    private static final ModConfigSpec.IntValue GATE_ENTITY_COOLDOWN;

    static {
        ModConfigSpec.Builder b = new ModConfigSpec.Builder();
        b.push("warp_pad");
        PAD_BASE_COST = b.comment("FE per trip between two Warp Pads of the same dimension (plus the distance part).")
                .defineInRange("padBaseCost", 5_000, 0, 100_000_000);
        PAD_COST_PER_BLOCK = b.comment("Extra FE per block of distance.")
                .defineInRange("padCostPerBlock", 20, 0, 1_000_000);
        PAD_RIFT_COST = b.comment("Flat FE for a trip to another dimension (needs the Rift Upgrade on the departure pad).")
                .defineInRange("padRiftCost", 100_000, 0, 1_000_000_000);
        PAD_BUFFER = b.comment("FE buffer of a Warp Pad.")
                .defineInRange("padBuffer", 1_000_000, 1_000, 1_000_000_000);
        PAD_MAX_RECEIVE = b.comment("FE per tick a Warp Pad accepts from cables and other mods.")
                .defineInRange("padMaxReceive", 50_000, 1, 1_000_000_000);
        PAD_TRAVEL_COOLDOWN = b.comment("Ticks a player has to wait between two pad trips.")
                .defineInRange("padTravelCooldown", 40, 0, 72_000);
        b.pop();
        b.push("warp_remote");
        REMOTE_COST = b.comment("FE per Recall Remote trip (also Rift Remote trips inside one dimension).")
                .defineInRange("remoteCost", 20_000, 0, 100_000_000);
        RIFT_REMOTE_COST = b.comment("FE per Rift Remote trip to another dimension.")
                .defineInRange("riftRemoteCost", 150_000, 0, 1_000_000_000);
        REMOTE_COOLDOWN_SECONDS = b.comment("Seconds a player has to wait between two remote trips.")
                .defineInRange("remoteCooldownSeconds", 30, 0, 3_600);
        b.pop();
        b.push("warp_gate");
        GATE_BUFFER = b.comment("FE buffer of a Portal Projector.")
                .defineInRange("gateBuffer", 4_000_000, 1_000, 2_000_000_000);
        GATE_MAX_RECEIVE = b.comment("FE per tick a Portal Projector accepts from cables and other mods.")
                .defineInRange("gateMaxReceive", 50_000, 1, 1_000_000_000);
        GATE_IDLE_COST = b.comment("FE per tick a Portal Projector uses while it projects its portal.")
                .defineInRange("gateIdleCost", 200, 0, 1_000_000);
        GATE_ENTITY_COST = b.comment("FE per entity that travels through a gate, taken from the departure projector.")
                .defineInRange("gateEntityCost", 10_000, 0, 100_000_000);
        GATE_ENTITY_COOLDOWN = b.comment("Ticks an entity can not use a gate again after it travelled (stops ping-pong).")
                .defineInRange("gateEntityCooldown", 60, 5, 72_000);
        b.pop();
        SPEC = b.build();
    }

    private static int get(ModConfigSpec.IntValue value) {
        return SPEC.isLoaded() ? value.get() : value.getDefault();
    }

    private static int fe(ModConfigSpec.IntValue value) {
        return CoreConfig.scaleEnergy(get(value));
    }

    public static int padBaseCost() { return fe(PAD_BASE_COST); }
    public static int padCostPerBlock() { return fe(PAD_COST_PER_BLOCK); }
    public static int padRiftCost() { return fe(PAD_RIFT_COST); }
    public static int padBuffer() { return get(PAD_BUFFER); }
    public static int padMaxReceive() { return get(PAD_MAX_RECEIVE); }
    public static int padTravelCooldown() { return get(PAD_TRAVEL_COOLDOWN); }
    public static int remoteCost() { return fe(REMOTE_COST); }
    public static int riftRemoteCost() { return fe(RIFT_REMOTE_COST); }
    public static int remoteCooldownTicks() { return get(REMOTE_COOLDOWN_SECONDS) * 20; }
    public static int gateBuffer() { return get(GATE_BUFFER); }
    public static int gateMaxReceive() { return get(GATE_MAX_RECEIVE); }
    public static int gateIdleCost() { return fe(GATE_IDLE_COST); }
    public static int gateEntityCost() { return fe(GATE_ENTITY_COST); }
    public static int gateEntityCooldown() { return get(GATE_ENTITY_COOLDOWN); }
}
