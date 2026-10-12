package com.arno.robotica.warp.menu;

import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.level.Level;
import org.jetbrains.annotations.Nullable;

import java.util.UUID;

/**
 * One row of the Warp Remote list, built on the server when the GUI opens. {@code dimension} and {@code pos} are null
 * when the pad is gone and its snapshot has no position. {@code status}: {@link #OK}, {@link #GONE}, {@link #PRIVATE},
 * {@link #NEEDS_MK2} (a Mk1 pad in another dimension).
 */
public record RemoteEntry(UUID id, String name, @Nullable ResourceKey<Level> dimension, @Nullable BlockPos pos, int cost, int status) {
    public static final int OK = 0;
    public static final int GONE = 1;
    public static final int PRIVATE = 2;
    public static final int NEEDS_MK2 = 3;

    public boolean usable() {
        return status == OK;
    }

    public void write(FriendlyByteBuf buf) {
        buf.writeUUID(id);
        buf.writeUtf(name, 64);
        buf.writeBoolean(dimension != null && pos != null);
        if (dimension != null && pos != null) {
            buf.writeResourceLocation(dimension.location());
            buf.writeBlockPos(pos);
        }
        buf.writeVarInt(cost);
        buf.writeVarInt(status);
    }

    public static RemoteEntry read(FriendlyByteBuf buf) {
        UUID id = buf.readUUID();
        String name = buf.readUtf(64);
        ResourceKey<Level> dim = null;
        BlockPos pos = null;
        if (buf.readBoolean()) {
            dim = ResourceKey.create(Registries.DIMENSION, buf.readResourceLocation());
            pos = buf.readBlockPos();
        }
        return new RemoteEntry(id, name, dim, pos, buf.readVarInt(), buf.readVarInt());
    }
}
