package com.arno.robotica.exo;

import com.arno.robotica.core.module.ModuleKind;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.GameType;

import java.util.function.Predicate;

/**
 * Jet Assist thrust rules, shared by the server ({@link ExoTicker}) and the client prediction so both sides agree on
 * climb speed, heat and cost (like {@link ExoSuit#canPayAirJump}). Safe on both sides.
 *
 * <p>Heat counts ticks of thrust, the same for every level: a tick of thrust adds 1, the Magma Core set bonus and
 * hovering each halve that. The pack overheats once the heat reaches the fitted level's burn time ({@link #maxHeat}),
 * so a lower level put in while hot is overheated at once. Then thrust and hover stop until the heat is back at 0.
 * Without thrust it cools {@link #COOL} per tick, twice as fast on the ground or in water. The server keeps the heat
 * with the player (suit off, dimension change, respawn, relog), not with the chestplate.
 */
public final class ExoJet {
    private ExoJet() {}

    /** Climb speed in blocks per tick (I, II, III: 4, 6, 8 blocks per second). */
    private static final double[] CLIMB = {0.2, 0.3, 0.4};
    /** Most change of vertical speed per tick while the thrust speeds up or settles. */
    public static final double ACCEL = 0.05;
    /** Sink speed while hovering and sneaking, in blocks per tick. */
    public static final double HOVER_SINK = 0.12;
    /**
     * Ticks a jump press that jumped (from the ground, or an air jump) must be held before thrust takes over, so a tap
     * stays a jump.
     */
    public static final int HOLD_TICKS = 5;
    /** Heat lost per tick without thrust (a full III cools in its 20 s burn time, as fast as it heated). */
    public static final double COOL = 1.0;
    /** Ticks after thrust stops in mid air before a Glider opens by itself (short gaps between bursts stay jetpack). */
    public static final int GLIDER_DELAY = 10;
    /** Falling faster than this (blocks per tick) opens the Glider at once after thrust. */
    public static final double GLIDER_SINK = -0.5;
    /** Lowest level with hover. */
    public static final int HOVER_LEVEL = 3;
    /** Ticks of slow fall after thrust ends in mid air, so the top of the climb does not drop the player. */
    public static final int AFTER_THRUST = 20;
    /** Heat share from which the HUD bar turns red. */
    public static final double HOT = 0.8;

    /** What the pack does: nothing, thrust upward, or hold the height (hover). */
    public static final int OFF = 0, THRUST = 1, HOVER = 2;

    public static double climb(int level) {
        return CLIMB[Math.max(0, Math.min(CLIMB.length, level) - 1)];
    }

    /** Moves {@code v} toward {@code target} by at most {@link #ACCEL}. */
    public static double approach(double v, double target) {
        return v < target ? Math.min(target, v + ACCEL) : Math.max(target, v - ACCEL);
    }

    /** Vertical speed for the next tick: climb while thrusting, hold (or sink when sneaking) while hovering. */
    public static double nextSpeed(double v, int level, int mode, boolean sneaking) {
        double target = mode == THRUST ? climb(level) : sneaking ? -HOVER_SINK : 0.0;
        return approach(v, target);
    }

    /** Heat at which a pack of this level overheats: its burn time in ticks. */
    public static double maxHeat(int level) {
        return Math.max(1, ExoConfig.jetBurnSeconds(Math.max(1, level)) * 20);
    }

    /** Heat added per tick of thrust ({@code hover}: half). The Magma Core set bonus halves it again. */
    public static double heatPerTick(boolean magma, boolean hover) {
        double heat = 1.0;
        if (magma) heat *= 0.5;
        if (hover) heat *= 0.5;
        return heat;
    }

    /** Heat lost per tick without thrust; twice as much on the ground or in water. */
    public static double coolPerTick(boolean grounded) {
        return grounded ? 2 * COOL : COOL;
    }

    /** Heat left after {@code ticks} without thrust off the ground (the suit lay unworn, the player was offline). */
    public static double cooled(double heat, long ticks) {
        return ticks <= 0 ? heat : Math.max(0.0, heat - ticks * COOL);
    }

    /** FE per tick of thrust before the Power Regulator ({@code hover}: half). */
    public static double costPerTick(int level, boolean hover) {
        double cost = ExoConfig.jetThrustCost(level) / 20.0;
        return hover ? cost * 0.5 : cost;
    }

    /** True when the suit's Jet Assist can pay a tick of thrust (or hover) right now. Client and server check the same. */
    public static boolean canPay(LivingEntity entity, ExoSuit.Active a, boolean hover) {
        int level = a.level(ModuleKind.JET_ASSIST);
        if (level <= 0) return false;
        double need = Math.max(costPerTick(level, hover) * ExoSuit.costFactor(a), 1);
        return ExoSuit.energyFor(entity, ExoSuit.SLOTS[a.piece(ModuleKind.JET_ASSIST)]) >= need;
    }

    /**
     * Creative or spectator game mode: the jetpack stays out of the way (no air jumps, thrust, hover, cost or heat, and
     * the Glider opens as usual). A Flight module's {@code mayfly} does not count.
     */
    public static boolean creative(Player p) {
        if (p instanceof ServerPlayer sp) {
            // The game mode itself (test players report isCreative() whatever their mode).
            GameType type = sp.gameMode.getGameModeForPlayer();
            return type == GameType.CREATIVE || type == GameType.SPECTATOR;
        }
        return p.isCreative() || p.isSpectator();
    }

    /** Standing on a block: holding jump here lifts off once the jump has been held {@link #HOLD_TICKS}. */
    public static boolean standing(Player p) {
        return p.onGround() && !grounded(p, false);
    }

    /** Standing, swimming, flying, gliding or riding: no air jumps and no thrust. */
    public static boolean grounded(Player p) {
        return grounded(p, true);
    }

    private static boolean grounded(Player p, boolean ground) {
        return (ground && p.onGround()) || p.isInWater() || p.isInLava() || p.getAbilities().flying || p.isFallFlying() || p.isPassenger();
    }

    /** Where the pack cools twice as fast. */
    public static boolean cooling(Player p) {
        return p.onGround() || p.isInWater();
    }

    /**
     * The client's answer to {@link #gliderMayOpen} for the local player, from its prediction. Set by the client module;
     * common code never loads client classes.
     */
    public static Predicate<Player> clientGliderGate = p -> true;

    /**
     * Glider and Jet Assist share jump in mid air. While the Jet Assist can fire, a jump press fires the pack (and ends
     * a glide), so the Glider only opens by itself, a moment after thrust stopped in a fall; a press opens it only while
     * the pack is overheated or out of energy. Gliding that already runs is never stopped here. Asked by
     * {@code canElytraFly} on both sides.
     */
    public static boolean gliderMayOpen(LivingEntity entity) {
        if (!(entity instanceof Player p) || p.isFallFlying() || creative(p)) return true;
        if (ExoSuit.pieceLevel(p, EquipmentSlot.CHEST, ModuleKind.JET_ASSIST) <= 0) return true;
        if (p.level().isClientSide) return clientGliderGate.test(p);
        return !(p instanceof ServerPlayer sp) || ExoTicker.gliderMayOpen(sp);
    }
}
