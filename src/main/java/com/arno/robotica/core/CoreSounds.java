package com.arno.robotica.core;

import com.arno.robotica.Robotica;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.Registries;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.level.Level;
import net.neoforged.neoforge.registries.DeferredHolder;
import net.neoforged.neoforge.registries.DeferredRegister;

import java.util.function.Supplier;

/**
 * Sound events of the whole mod. All of them are defined in {@code assets/robotica/sounds.json} from vanilla sound files
 * (pitched, quieted, in weighted variants), so no audio ships with the mod. Every event is variable range (volume above
 * 1.0 carries further) and has a subtitle {@code subtitles.robotica.<name>}.
 *
 * <p>Conventions: machines play on {@link SoundSource#BLOCKS}, tools and weapons on {@link SoundSource#PLAYERS}, robots
 * and drones on {@link SoundSource#NEUTRAL}. Always play server side (these helpers do nothing on the client) and keep
 * ambient or working sounds to about once per second at most, see {@link #due}.
 */
public final class CoreSounds {
    private CoreSounds() {}

    public static final DeferredRegister<SoundEvent> REGISTER = DeferredRegister.create(Registries.SOUND_EVENT, Robotica.MODID);

    private static DeferredHolder<SoundEvent, SoundEvent> reg(String name) {
        return REGISTER.register(name, () -> SoundEvent.createVariableRangeEvent(Robotica.id(name)));
    }

    public static final DeferredHolder<SoundEvent, SoundEvent> CRANK_WIND = reg("crank_wind");
    public static final DeferredHolder<SoundEvent, SoundEvent> CRANK_FULL = reg("crank_full");
    public static final DeferredHolder<SoundEvent, SoundEvent> SPRING_INSERT = reg("spring_insert");
    public static final DeferredHolder<SoundEvent, SoundEvent> SPRING_REMOVE = reg("spring_remove");
    public static final DeferredHolder<SoundEvent, SoundEvent> MACHINE_START = reg("machine_start");
    public static final DeferredHolder<SoundEvent, SoundEvent> MACHINE_STOP = reg("machine_stop");
    public static final DeferredHolder<SoundEvent, SoundEvent> GENERATOR_BURN = reg("generator_burn");
    public static final DeferredHolder<SoundEvent, SoundEvent> PRESS_STAMP = reg("press_stamp");
    public static final DeferredHolder<SoundEvent, SoundEvent> CHARGER_HUM = reg("charger_hum");
    public static final DeferredHolder<SoundEvent, SoundEvent> CHARGE_COMPLETE = reg("charge_complete");
    public static final DeferredHolder<SoundEvent, SoundEvent> ROBOT_BEEP = reg("robot_beep");
    public static final DeferredHolder<SoundEvent, SoundEvent> ROBOT_BEEP_LOW = reg("robot_beep_low");
    public static final DeferredHolder<SoundEvent, SoundEvent> ROBOT_ERROR = reg("robot_error");
    public static final DeferredHolder<SoundEvent, SoundEvent> UPGRADE_INSTALL = reg("upgrade_install");
    public static final DeferredHolder<SoundEvent, SoundEvent> STUMPY_CHOP = reg("stumpy_chop");
    public static final DeferredHolder<SoundEvent, SoundEvent> SAW_WHIR = reg("saw_whir");
    public static final DeferredHolder<SoundEvent, SoundEvent> SPROUT_SNIP = reg("sprout_snip");
    public static final DeferredHolder<SoundEvent, SoundEvent> EXCAVATOR_DIG = reg("excavator_dig");
    public static final DeferredHolder<SoundEvent, SoundEvent> DRILL_GRIND = reg("drill_grind");
    public static final DeferredHolder<SoundEvent, SoundEvent> TOOL_MODE = reg("tool_mode");
    public static final DeferredHolder<SoundEvent, SoundEvent> AREA_BREAK = reg("area_break");
    public static final DeferredHolder<SoundEvent, SoundEvent> CHAINSAW_REV = reg("chainsaw_rev");
    /** Layered on AREA_BREAK for 10-27 blocks (5x5, 3x3x3). */
    public static final DeferredHolder<SoundEvent, SoundEvent> AREA_CRUNCH = reg("area_crunch");
    /** Deep rumble of a big area break (more than 27 blocks). */
    public static final DeferredHolder<SoundEvent, SoundEvent> AREA_RUMBLE = reg("area_rumble");
    /** Stones clattering while a queued area break drains. */
    public static final DeferredHolder<SoundEvent, SoundEvent> AREA_DEBRIS = reg("area_debris");
    /** A drill winding up before a big break. */
    public static final DeferredHolder<SoundEvent, SoundEvent> DRILL_SPINUP = reg("drill_spinup");
    /** A whole tree coming down. */
    public static final DeferredHolder<SoundEvent, SoundEvent> TREE_FALL = reg("tree_fall");
    public static final DeferredHolder<SoundEvent, SoundEvent> SHOCK_ZAP = reg("shock_zap");
    public static final DeferredHolder<SoundEvent, SoundEvent> RIVET_SHOT = reg("rivet_shot");
    public static final DeferredHolder<SoundEvent, SoundEvent> RIVET_EMPTY = reg("rivet_empty");
    public static final DeferredHolder<SoundEvent, SoundEvent> ARC_STRIKE = reg("arc_strike");
    public static final DeferredHolder<SoundEvent, SoundEvent> LANCE_CHARGE = reg("lance_charge");
    public static final DeferredHolder<SoundEvent, SoundEvent> LANCE_READY = reg("lance_ready");
    public static final DeferredHolder<SoundEvent, SoundEvent> LANCE_FIRE = reg("lance_fire");
    public static final DeferredHolder<SoundEvent, SoundEvent> REPLICATOR_FORM = reg("replicator_form");
    public static final DeferredHolder<SoundEvent, SoundEvent> REPLICATOR_UNFORM = reg("replicator_unform");
    public static final DeferredHolder<SoundEvent, SoundEvent> REPLICATOR_HUM = reg("replicator_hum");
    public static final DeferredHolder<SoundEvent, SoundEvent> REPLICATOR_CYCLE = reg("replicator_cycle");
    public static final DeferredHolder<SoundEvent, SoundEvent> REPLICATOR_SPAWN = reg("replicator_spawn");
    public static final DeferredHolder<SoundEvent, SoundEvent> ESSENCE_SAMPLE = reg("essence_sample");
    public static final DeferredHolder<SoundEvent, SoundEvent> ESSENCE_COMPLETE = reg("essence_complete");
    public static final DeferredHolder<SoundEvent, SoundEvent> ARCHITECT_PLACE = reg("architect_place");
    public static final DeferredHolder<SoundEvent, SoundEvent> ARCHITECT_DONE = reg("architect_done");
    public static final DeferredHolder<SoundEvent, SoundEvent> DRONE_BUZZ = reg("drone_buzz");
    public static final DeferredHolder<SoundEvent, SoundEvent> MATTER_ABSORB = reg("matter_absorb");
    public static final DeferredHolder<SoundEvent, SoundEvent> WARP_START = reg("warp_start");
    public static final DeferredHolder<SoundEvent, SoundEvent> WARP_CHARGE = reg("warp_charge");
    public static final DeferredHolder<SoundEvent, SoundEvent> WARP_BIND = reg("warp_bind");
    public static final DeferredHolder<SoundEvent, SoundEvent> WARP_LINK = reg("warp_link");
    public static final DeferredHolder<SoundEvent, SoundEvent> WARP_RIFT = reg("warp_rift");
    public static final DeferredHolder<SoundEvent, SoundEvent> TELEPORT_DEPART = reg("teleport_depart");
    public static final DeferredHolder<SoundEvent, SoundEvent> TELEPORT_ARRIVE = reg("teleport_arrive");
    public static final DeferredHolder<SoundEvent, SoundEvent> TELEPORT_QUIET_DEPART = reg("teleport_quiet_depart");
    public static final DeferredHolder<SoundEvent, SoundEvent> TELEPORT_QUIET_ARRIVE = reg("teleport_quiet_arrive");
    public static final DeferredHolder<SoundEvent, SoundEvent> GATE_OPEN = reg("gate_open");
    public static final DeferredHolder<SoundEvent, SoundEvent> GATE_CLOSE = reg("gate_close");
    public static final DeferredHolder<SoundEvent, SoundEvent> GATE_AMBIENT = reg("gate_ambient");
    /** Spark Lamp lighting up / going out. */
    public static final DeferredHolder<SoundEvent, SoundEvent> SPARK_LAMP_ON = reg("spark_lamp_on");
    public static final DeferredHolder<SoundEvent, SoundEvent> SPARK_LAMP_OFF = reg("spark_lamp_off");
    /** Spark Lamp ambience, played client side by its block (quiet: the volumes live in sounds.json). */
    public static final DeferredHolder<SoundEvent, SoundEvent> SPARK_LAMP_CRACKLE = reg("spark_lamp_crackle");
    public static final DeferredHolder<SoundEvent, SoundEvent> SPARK_LAMP_HUM = reg("spark_lamp_hum");
    /** Hauler Drone: claw clamps on a mob, lets it go, winch hum while carrying. */
    public static final DeferredHolder<SoundEvent, SoundEvent> HAULER_GRAB = reg("hauler_grab");
    public static final DeferredHolder<SoundEvent, SoundEvent> HAULER_RELEASE = reg("hauler_release");
    public static final DeferredHolder<SoundEvent, SoundEvent> HAULER_WINCH = reg("hauler_winch");
    /** Rancher jobs, steps, servo hum, hurt and death. */
    public static final DeferredHolder<SoundEvent, SoundEvent> RANCHER_FEED = reg("rancher_feed");
    public static final DeferredHolder<SoundEvent, SoundEvent> RANCHER_SHEAR = reg("rancher_shear");
    public static final DeferredHolder<SoundEvent, SoundEvent> RANCHER_MILK = reg("rancher_milk");
    public static final DeferredHolder<SoundEvent, SoundEvent> RANCHER_CULL = reg("rancher_cull");
    public static final DeferredHolder<SoundEvent, SoundEvent> RANCHER_STEP = reg("rancher_step");
    public static final DeferredHolder<SoundEvent, SoundEvent> RANCHER_HUM = reg("rancher_hum");
    public static final DeferredHolder<SoundEvent, SoundEvent> RANCHER_HURT = reg("rancher_hurt");
    public static final DeferredHolder<SoundEvent, SoundEvent> RANCHER_DEATH = reg("rancher_death");
    /** Tesla Spire: crown hum while it runs, a lightning strike on the crown, a fuel item loads. */
    public static final DeferredHolder<SoundEvent, SoundEvent> SPIRE_HUM = reg("spire_hum");
    public static final DeferredHolder<SoundEvent, SoundEvent> SPIRE_STRIKE = reg("spire_strike");
    public static final DeferredHolder<SoundEvent, SoundEvent> SPIRE_FUEL = reg("spire_fuel");
    /** Core Reactor: a core loads, a core burns out, the chamber's throb while it runs. */
    public static final DeferredHolder<SoundEvent, SoundEvent> CORE_INSERT = reg("core_insert");
    public static final DeferredHolder<SoundEvent, SoundEvent> CORE_BURNOUT = reg("core_burnout");
    public static final DeferredHolder<SoundEvent, SoundEvent> CORE_REACTOR_HUM = reg("core_reactor_hum");
    /** Ring Collider: beam lights and dies, hum, collisions at full beam, a Strange Matter forming. */
    public static final DeferredHolder<SoundEvent, SoundEvent> COLLIDER_START = reg("collider_start");
    public static final DeferredHolder<SoundEvent, SoundEvent> COLLIDER_STOP = reg("collider_stop");
    public static final DeferredHolder<SoundEvent, SoundEvent> COLLIDER_HUM = reg("collider_hum");
    public static final DeferredHolder<SoundEvent, SoundEvent> COLLIDER_COLLIDE = reg("collider_collide");
    public static final DeferredHolder<SoundEvent, SoundEvent> COLLIDER_MATTER = reg("collider_matter");
    /** Multiblocks (Capacitor Bank, Core Reactor, Tesla Spire, Ring Collider) forming and breaking. */
    public static final DeferredHolder<SoundEvent, SoundEvent> STRUCTURE_FORM = reg("structure_form");
    public static final DeferredHolder<SoundEvent, SoundEvent> STRUCTURE_UNFORM = reg("structure_unform");
    /** Played client side where the light of a forming frame closes (also the Replicator's). */
    public static final DeferredHolder<SoundEvent, SoundEvent> STRUCTURE_SETTLE = reg("structure_settle");

    /** Exo-Frame Flight: thruster loop, played client side for every flying suit nearby. */
    public static final DeferredHolder<SoundEvent, SoundEvent> EXO_THRUSTER = reg("exo_thruster");
    /** Exo-Frame Flight ends on the ground. */
    public static final DeferredHolder<SoundEvent, SoundEvent> EXO_LAND = reg("exo_land");
    /** Med Injector: the injection hiss, layered with EXO_MED_CHIME. */
    public static final DeferredHolder<SoundEvent, SoundEvent> EXO_MED_HISS = reg("exo_med_hiss");
    public static final DeferredHolder<SoundEvent, SoundEvent> EXO_MED_CHIME = reg("exo_med_chime");
    /** Dash Thrusters whoosh. */
    public static final DeferredHolder<SoundEvent, SoundEvent> EXO_DASH = reg("exo_dash");
    /** Spring Heels: a charged jump. */
    public static final DeferredHolder<SoundEvent, SoundEvent> EXO_SPRING = reg("exo_spring");
    /** A cooldown ran out (client side, only the wearer hears it). */
    public static final DeferredHolder<SoundEvent, SoundEvent> EXO_READY = reg("exo_ready");
    /** A key pressed while its ability cools down (client side, only the wearer hears it). */
    public static final DeferredHolder<SoundEvent, SoundEvent> EXO_DENIED = reg("exo_denied");
    /** Kinetic Shield: a hit absorbed completely / partly. */
    public static final DeferredHolder<SoundEvent, SoundEvent> EXO_SHIELD_BLOCK = reg("exo_shield_block");
    public static final DeferredHolder<SoundEvent, SoundEvent> EXO_SHIELD_SPARK = reg("exo_shield_spark");

    /** Plays a sound at a block position for every nearby player. Server side only, ignored on the client. */
    public static void play(Level level, BlockPos pos, Supplier<SoundEvent> sound, SoundSource source, float volume, float pitch) {
        if (level.isClientSide) return;
        level.playSound(null, pos, sound.get(), source, volume, pitch);
    }

    /** Plays a sound at exact coordinates. Server side only, ignored on the client. */
    public static void play(Level level, double x, double y, double z, Supplier<SoundEvent> sound, SoundSource source, float volume, float pitch) {
        if (level.isClientSide) return;
        level.playSound(null, x, y, z, sound.get(), source, volume, pitch);
    }

    /** Plays a sound at an entity. Server side only, ignored on the client. */
    public static void play(Entity entity, Supplier<SoundEvent> sound, SoundSource source, float volume, float pitch) {
        play(entity.level(), entity.getX(), entity.getY(), entity.getZ(), sound, source, volume, pitch);
    }

    /**
     * Stateless rate limiter for repeating sounds: true once every {@code periodTicks} ticks for this position. The
     * position is mixed in so neighbouring machines do not all play on the same tick.
     */
    public static boolean due(Level level, BlockPos pos, int periodTicks) {
        return Math.floorMod(level.getGameTime() + pos.asLong(), (long) Math.max(1, periodTicks)) == 0;
    }
}
