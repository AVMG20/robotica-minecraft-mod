package com.arno.robotica.power.tesla;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.NbtUtils;
import org.jetbrains.annotations.Nullable;

/**
 * One link of a Tesla Coil. Either another coil ({@code coil}, no face) or an FE block, fed through {@code face}: the
 * face the player clicked, which is the side the energy enters (machines that only take power on some sides).
 */
public record TeslaLink(BlockPos pos, @Nullable Direction face, boolean coil) {
    public static TeslaLink toCoil(BlockPos pos) {
        return new TeslaLink(pos.immutable(), null, true);
    }

    public static TeslaLink toMachine(BlockPos pos, Direction face) {
        return new TeslaLink(pos.immutable(), face, false);
    }

    public CompoundTag save() {
        CompoundTag tag = new CompoundTag();
        tag.put("pos", NbtUtils.writeBlockPos(pos));
        if (coil) tag.putBoolean("coil", true);
        if (face != null) tag.putByte("face", (byte) face.get3DDataValue());
        return tag;
    }

    @Nullable
    public static TeslaLink load(CompoundTag tag) {
        BlockPos pos = NbtUtils.readBlockPos(tag, "pos").orElse(null);
        if (pos == null) return null;
        if (tag.getBoolean("coil")) return toCoil(pos);
        if (!tag.contains("face")) return null;
        return toMachine(pos, Direction.from3DDataValue(tag.getByte("face")));
    }
}
