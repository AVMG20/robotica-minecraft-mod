package com.arno.robotica.link.menu;

import com.arno.robotica.link.network.LinkNetwork;
import com.arno.robotica.link.network.Visibility;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.resources.ResourceLocation;

import java.util.UUID;

/**
 * One row of the network list, as sent to the client. {@code group}: 0 own, 1 a team mate's, 2 public.
 * {@code manage} is true when the viewer owns it (the server checks again).
 */
public record NetworkEntry(UUID id, String name, String ownerName, int color, Visibility visibility, ResourceLocation home,
                           int group, boolean manage) {
    public static final StreamCodec<FriendlyByteBuf, NetworkEntry> STREAM_CODEC = StreamCodec.of((buf, e) -> e.write(buf), NetworkEntry::read);

    public void write(FriendlyByteBuf buf) {
        buf.writeUUID(id);
        buf.writeUtf(name, LinkNetwork.MAX_NAME * 4);
        buf.writeUtf(ownerName, 64);
        buf.writeByte(color);
        buf.writeByte(visibility.ordinal());
        buf.writeResourceLocation(home);
        buf.writeByte(group);
        buf.writeBoolean(manage);
    }

    public static NetworkEntry read(FriendlyByteBuf buf) {
        UUID id = buf.readUUID();
        String name = buf.readUtf(LinkNetwork.MAX_NAME * 4);
        String owner = buf.readUtf(64);
        int color = buf.readByte();
        Visibility visibility = Visibility.byId(buf.readByte());
        ResourceLocation home = buf.readResourceLocation();
        int group = buf.readByte();
        boolean manage = buf.readBoolean();
        return new NetworkEntry(id, name, owner, color, visibility, home, group, manage);
    }
}
