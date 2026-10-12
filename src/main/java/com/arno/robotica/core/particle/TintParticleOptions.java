package com.arno.robotica.core.particle;

import com.mojang.serialization.Codec;
import com.mojang.serialization.MapCodec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import net.minecraft.core.particles.ParticleOptions;
import net.minecraft.core.particles.ParticleType;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;

/**
 * Options of every Robotica particle: an RGB tint and a size factor. Works with {@code /particle}, e.g.
 * {@code /particle robotica:spark{color:9496063,scale:1.5}}.
 */
public record TintParticleOptions(ParticleType<TintParticleOptions> type, int color, float scale) implements ParticleOptions {
    public static final float MIN_SCALE = 0.05F, MAX_SCALE = 8.0F;

    /** The packet codec takes any float: keep the size in range (NaN becomes 1) and the colour to RGB. */
    public TintParticleOptions {
        scale = Float.isNaN(scale) ? 1.0F : Math.max(MIN_SCALE, Math.min(MAX_SCALE, scale));
        color &= 0xFFFFFF;
    }

    @Override
    public ParticleType<TintParticleOptions> getType() {
        return type;
    }

    public float red() {
        return (color >> 16 & 0xFF) / 255.0F;
    }

    public float green() {
        return (color >> 8 & 0xFF) / 255.0F;
    }

    public float blue() {
        return (color & 0xFF) / 255.0F;
    }

    static MapCodec<TintParticleOptions> codec(ParticleType<TintParticleOptions> type) {
        return RecordCodecBuilder.mapCodec(i -> i.group(
                Codec.INT.optionalFieldOf("color", 0xFFFFFF).forGetter(TintParticleOptions::color),
                Codec.floatRange(MIN_SCALE, MAX_SCALE).optionalFieldOf("scale", 1.0F).forGetter(TintParticleOptions::scale)
        ).apply(i, (color, scale) -> new TintParticleOptions(type, color, scale)));
    }

    static StreamCodec<RegistryFriendlyByteBuf, TintParticleOptions> streamCodec(ParticleType<TintParticleOptions> type) {
        return StreamCodec.composite(
                ByteBufCodecs.INT, TintParticleOptions::color,
                ByteBufCodecs.FLOAT, TintParticleOptions::scale,
                (color, scale) -> new TintParticleOptions(type, color, scale));
    }
}
