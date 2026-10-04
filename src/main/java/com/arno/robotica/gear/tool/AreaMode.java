package com.arno.robotica.gear.tool;

import com.mojang.serialization.Codec;
import io.netty.buffer.ByteBuf;
import net.minecraft.network.chat.Component;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.util.StringRepresentable;

/** Mining mode of a Robotica tool. Width/height lie in the plane of the hit face, depth goes into the block. */
public enum AreaMode implements StringRepresentable {
    SINGLE("single", 1, 1, 1),
    AREA_3("area_3", 3, 3, 1),
    AREA_5("area_5", 5, 5, 1),
    AREA_9("area_9", 9, 9, 1),
    AREA_12("area_12", 12, 12, 1),
    CUBE_3("cube_3", 3, 3, 3),
    CUBE_5("cube_5", 5, 5, 5),
    CUBE_12("cube_12", 12, 12, 12),
    VEIN("vein", 1, 1, 1),
    TREE("tree", 1, 1, 1);

    public static final Codec<AreaMode> CODEC = StringRepresentable.fromEnum(AreaMode::values);
    public static final StreamCodec<ByteBuf, AreaMode> STREAM_CODEC =
            ByteBufCodecs.VAR_INT.map(i -> values()[Math.floorMod(i, values().length)], AreaMode::ordinal);

    private final String id;
    public final int width;
    public final int height;
    public final int depth;

    AreaMode(String id, int width, int height, int depth) {
        this.id = id;
        this.width = width;
        this.height = height;
        this.depth = depth;
    }

    @Override
    public String getSerializedName() {
        return id;
    }

    /** True for the box shaped modes (everything except single, vein and tree). */
    public boolean isBox() {
        return width * height * depth > 1;
    }

    public Component displayName() {
        return Component.translatable("gear.robotica.mode." + id);
    }
}
