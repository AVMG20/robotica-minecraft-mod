package com.arno.robotica.architect.matter;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import io.netty.buffer.ByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;

import java.util.Locale;

/** An amount of the two matter grades the Architect Table stores. */
public record Matter(int rustic, int refined) {
    public static final Matter ZERO = new Matter(0, 0);
    public static final Codec<Matter> CODEC = RecordCodecBuilder.create(i -> i.group(
            Codec.INT.fieldOf("rustic").forGetter(Matter::rustic),
            Codec.INT.fieldOf("refined").forGetter(Matter::refined)).apply(i, Matter::new));
    public static final StreamCodec<ByteBuf, Matter> STREAM_CODEC = StreamCodec.composite(
            ByteBufCodecs.VAR_INT, Matter::rustic, ByteBufCodecs.VAR_INT, Matter::refined, Matter::new);

    public boolean isZero() {
        return rustic == 0 && refined == 0;
    }

    public Matter times(int n) {
        return new Matter(rustic * n, refined * n);
    }

    public Matter plus(Matter o) {
        return new Matter(rustic + o.rustic, refined + o.refined);
    }

    public int get(Grade grade) {
        return switch (grade) {
            case RUSTIC -> rustic;
            case REFINED -> refined;
        };
    }

    public enum Grade {
        RUSTIC, REFINED;

        public String langKey() {
            return "gui.robotica.matter." + name().toLowerCase(Locale.ROOT);
        }
    }
}
