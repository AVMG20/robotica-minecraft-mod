package com.arno.robotica.gear;

import com.arno.robotica.Robotica;
import net.minecraft.core.particles.ParticleType;
import net.minecraft.core.particles.SimpleParticleType;
import net.minecraft.core.registries.Registries;
import net.neoforged.neoforge.registries.DeferredHolder;
import net.neoforged.neoforge.registries.DeferredRegister;

/** Particle types of the gear module (providers in gear.client.LampSparkParticle). */
public final class GearParticles {
    private GearParticles() {}

    public static final DeferredRegister<ParticleType<?>> REGISTER = DeferredRegister.create(Registries.PARTICLE_TYPE, Robotica.MODID);

    /** A Spark Lamp spark: flies out, falls, white to gold. */
    public static final DeferredHolder<ParticleType<?>, SimpleParticleType> LAMP_SPARK = REGISTER.register("lamp_spark", () -> new SimpleParticleType(false));
    /** A Spark Lamp flicker: a white blink that stays put for a few ticks (the core and crackle arcs). */
    public static final DeferredHolder<ParticleType<?>, SimpleParticleType> LAMP_FLICK = REGISTER.register("lamp_flick", () -> new SimpleParticleType(false));
    /** A Spark Lamp ember: drifts up slowly and fades. */
    public static final DeferredHolder<ParticleType<?>, SimpleParticleType> LAMP_EMBER = REGISTER.register("lamp_ember", () -> new SimpleParticleType(false));
}
