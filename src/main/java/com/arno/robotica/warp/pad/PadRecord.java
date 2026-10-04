package com.arno.robotica.warp.pad;

import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.Registries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.NbtUtils;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.level.Level;
import org.jetbrains.annotations.Nullable;

import java.util.UUID;

/** One Warp Pad as stored in the registry. {@code owner} is null for pads placed without a player (anyone may use them). */
public record PadRecord(UUID id, BlockPos pos, ResourceKey<Level> dimension, String name, @Nullable UUID owner,
                        String ownerName, boolean isPublic, boolean rift) {

    public PadRecord withName(String newName) {
        return new PadRecord(id, pos, dimension, newName, owner, ownerName, isPublic, rift);
    }

    public PadRecord withPublic(boolean value) {
        return new PadRecord(id, pos, dimension, name, owner, ownerName, value, rift);
    }

    public PadRecord withRift(boolean value) {
        return new PadRecord(id, pos, dimension, name, owner, ownerName, isPublic, value);
    }

    public PadRecord withOwnerName(String value) {
        return new PadRecord(id, pos, dimension, name, owner, value, isPublic, rift);
    }

    public CompoundTag save() {
        CompoundTag tag = new CompoundTag();
        tag.putUUID("id", id);
        tag.put("pos", NbtUtils.writeBlockPos(pos));
        tag.putString("dim", dimension.location().toString());
        tag.putString("name", name);
        if (owner != null) tag.putUUID("owner", owner);
        tag.putString("ownerName", ownerName);
        tag.putBoolean("public", isPublic);
        tag.putBoolean("rift", rift);
        return tag;
    }

    /** Returns null when the tag is damaged (missing id, position or dimension). */
    @Nullable
    public static PadRecord load(CompoundTag tag) {
        if (!tag.hasUUID("id") || !tag.contains("pos") || !tag.contains("dim")) return null;
        BlockPos pos = NbtUtils.readBlockPos(tag, "pos").orElse(null);
        ResourceLocation dim = ResourceLocation.tryParse(tag.getString("dim"));
        if (pos == null || dim == null) return null;
        return new PadRecord(tag.getUUID("id"), pos, ResourceKey.create(Registries.DIMENSION, dim), tag.getString("name"),
                tag.hasUUID("owner") ? tag.getUUID("owner") : null, tag.getString("ownerName"),
                tag.getBoolean("public"), tag.getBoolean("rift"));
    }
}
