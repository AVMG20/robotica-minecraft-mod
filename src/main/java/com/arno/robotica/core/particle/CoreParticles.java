package com.arno.robotica.core.particle;

import com.arno.robotica.Robotica;
import com.mojang.serialization.MapCodec;
import net.minecraft.core.particles.ParticleType;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.neoforged.neoforge.registries.DeferredHolder;
import net.neoforged.neoforge.registries.DeferredRegister;

/**
 * Robotica's own particles (common side: types and options; the client draws them in {@code core.client.particle}).
 * Every type takes {@link TintParticleOptions}. All of them go through vanilla's particle setting (fewer at
 * Decreased, none at Minimal). Velocity arguments mean what they do for vanilla particles, except for {@link #WARP}.
 * <ul>
 *   <li>{@link #SPARK}: white-hot streak that falls, bounces up to twice and cools to its tint. Full bright.</li>
 *   <li>{@link #ZAP}: tiny jagged flash that flickers for a few ticks. Full bright.</li>
 *   <li>{@link #MOTE}: soft light dot that drifts and fades. Full bright.</li>
 *   <li>{@link #PUFF}: soft dust or smoke puff in the tint, lit by the world.</li>
 *   <li>{@link #WARP}: vortex mote circling its spawn point. x speed = radius (positive: spirals in, negative: spirals
 *   out), y speed = rise per tick, z speed = turn per tick in radians (0: default). Full bright.</li>
 *   <li>{@link #SHARD}: metal fragment that tumbles, falls and bounces once, lit by the world.</li>
 *   <li>{@link #FLASH}: short bright burst. Full bright.</li>
 * </ul>
 */
public final class CoreParticles {
    private CoreParticles() {}

    public static final DeferredRegister<ParticleType<?>> REGISTER = DeferredRegister.create(Registries.PARTICLE_TYPE, Robotica.MODID);

    public static final DeferredHolder<ParticleType<?>, ParticleType<TintParticleOptions>> SPARK = tinted("spark");
    public static final DeferredHolder<ParticleType<?>, ParticleType<TintParticleOptions>> ZAP = tinted("zap");
    public static final DeferredHolder<ParticleType<?>, ParticleType<TintParticleOptions>> MOTE = tinted("mote");
    public static final DeferredHolder<ParticleType<?>, ParticleType<TintParticleOptions>> PUFF = tinted("puff");
    public static final DeferredHolder<ParticleType<?>, ParticleType<TintParticleOptions>> WARP = tinted("warp");
    public static final DeferredHolder<ParticleType<?>, ParticleType<TintParticleOptions>> SHARD = tinted("shard");
    public static final DeferredHolder<ParticleType<?>, ParticleType<TintParticleOptions>> FLASH = tinted("flash");

    // ---- Shared colours ----
    /** Electric cyan of arcs, Tesla and energy. */
    public static final int ELECTRIC = 0x8FE9FF;
    /** Violet of warp travel and the Null tier. */
    public static final int WARP_VIOLET = 0xB07CFF;
    /** Teal-cyan core of warp travel. */
    public static final int WARP_CYAN = 0x6FF2E0;
    /** Hot orange of rivets, flames and the Forge Tyrant. */
    public static final int EMBER = 0xFF9A3C;
    /** Bare steel for shards and sparks off metal. */
    public static final int STEEL = 0xB9C2C6;
    /** Dark smoke. */
    public static final int SMOKE = 0x4A4744;

    private static DeferredHolder<ParticleType<?>, ParticleType<TintParticleOptions>> tinted(String name) {
        return REGISTER.register(name, TintType::new);
    }

    private static final class TintType extends ParticleType<TintParticleOptions> {
        private final MapCodec<TintParticleOptions> codec = TintParticleOptions.codec(this);
        private final StreamCodec<RegistryFriendlyByteBuf, TintParticleOptions> streamCodec = TintParticleOptions.streamCodec(this);

        TintType() {
            super(false);
        }

        @Override
        public MapCodec<TintParticleOptions> codec() {
            return codec;
        }

        @Override
        public StreamCodec<? super RegistryFriendlyByteBuf, TintParticleOptions> streamCodec() {
            return streamCodec;
        }
    }

    // ---- Options ----

    public static TintParticleOptions spark(int color, float scale) {
        return new TintParticleOptions(SPARK.get(), color, scale);
    }

    public static TintParticleOptions zap(int color, float scale) {
        return new TintParticleOptions(ZAP.get(), color, scale);
    }

    public static TintParticleOptions mote(int color, float scale) {
        return new TintParticleOptions(MOTE.get(), color, scale);
    }

    public static TintParticleOptions puff(int color, float scale) {
        return new TintParticleOptions(PUFF.get(), color, scale);
    }

    public static TintParticleOptions warp(int color, float scale) {
        return new TintParticleOptions(WARP.get(), color, scale);
    }

    public static TintParticleOptions shard(int color, float scale) {
        return new TintParticleOptions(SHARD.get(), color, scale);
    }

    public static TintParticleOptions flash(int color, float scale) {
        return new TintParticleOptions(FLASH.get(), color, scale);
    }
}
