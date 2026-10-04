package com.arno.robotica.warp.menu;

import net.minecraft.core.registries.Registries;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.level.Level;

import java.util.UUID;

/**
 * One row of the destination list, as sent to the client when the GUI opens.
 * {@code distance} is the block distance in the same dimension, or -1 for another dimension.
 * {@code available} is false when the trip needs a Rift Upgrade the departure pad does not have.
 */
public record DestinationEntry(UUID id, String name, String ownerName, ResourceKey<Level> dimension, int distance, int cost, boolean available) {

    public void write(FriendlyByteBuf buf) {
        buf.writeUUID(id);
        buf.writeUtf(name, 64);
        buf.writeUtf(ownerName, 64);
        buf.writeResourceLocation(dimension.location());
        buf.writeVarInt(distance + 1);
        buf.writeVarInt(cost);
        buf.writeBoolean(available);
    }

    public static DestinationEntry read(FriendlyByteBuf buf) {
        UUID id = buf.readUUID();
        String name = buf.readUtf(64);
        String owner = buf.readUtf(64);
        ResourceLocation dim = buf.readResourceLocation();
        int distance = buf.readVarInt() - 1;
        int cost = buf.readVarInt();
        boolean available = buf.readBoolean();
        return new DestinationEntry(id, name, owner, ResourceKey.create(Registries.DIMENSION, dim), distance, cost, available);
    }
}
