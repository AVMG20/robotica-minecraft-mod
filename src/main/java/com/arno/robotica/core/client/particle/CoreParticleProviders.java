package com.arno.robotica.core.client.particle;

import com.arno.robotica.core.particle.CoreParticles;
import com.arno.robotica.core.particle.TintParticleOptions;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.particle.Particle;
import net.minecraft.client.particle.ParticleProvider;
import net.minecraft.client.particle.SpriteSet;
import net.minecraft.core.particles.ParticleType;
import net.neoforged.neoforge.client.event.RegisterParticleProvidersEvent;

/** Hooks the Robotica particle types to their classes. Sprites: assets/robotica/particles/*.json. */
public final class CoreParticleProviders {
    private CoreParticleProviders() {}

    @FunctionalInterface
    private interface Factory {
        Particle create(ClientLevel level, double x, double y, double z, double vx, double vy, double vz, TintParticleOptions o, SpriteSet sprites);
    }

    public static void register(RegisterParticleProvidersEvent event) {
        add(event, CoreParticles.SPARK.get(), CoreParticleKinds.Spark::new);
        add(event, CoreParticles.ZAP.get(), CoreParticleKinds.Zap::new);
        add(event, CoreParticles.MOTE.get(), CoreParticleKinds.Mote::new);
        add(event, CoreParticles.PUFF.get(), CoreParticleKinds.Puff::new);
        add(event, CoreParticles.WARP.get(), CoreParticleKinds.Warp::new);
        add(event, CoreParticles.SHARD.get(), CoreParticleKinds.Shard::new);
        add(event, CoreParticles.FLASH.get(), CoreParticleKinds.Flash::new);
    }

    private static void add(RegisterParticleProvidersEvent event, ParticleType<TintParticleOptions> type, Factory factory) {
        event.registerSpriteSet(type, sprites -> (ParticleProvider<TintParticleOptions>) (o, level, x, y, z, vx, vy, vz) ->
                factory.create(level, x, y, z, vx, vy, vz, o, sprites));
    }
}
