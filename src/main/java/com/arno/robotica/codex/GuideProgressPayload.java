package com.arno.robotica.codex;

import com.arno.robotica.Robotica;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.neoforged.neoforge.network.handling.IPayloadContext;

import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * Server to client: ids of the guide advancements this player has finished, read by the Codex "Next steps" chapter.
 * {@code earned} is set when a step was just finished (not on login), for the client's unlock chime.
 */
public record GuideProgressPayload(List<String> done, boolean earned) implements CustomPacketPayload {
    public static final Type<GuideProgressPayload> TYPE = new Type<>(Robotica.id("codex_guide_progress"));
    public static final StreamCodec<RegistryFriendlyByteBuf, GuideProgressPayload> STREAM_CODEC = StreamCodec.composite(
            ByteBufCodecs.STRING_UTF8.apply(ByteBufCodecs.list(512)), GuideProgressPayload::done,
            ByteBufCodecs.BOOL, GuideProgressPayload::earned,
            GuideProgressPayload::new);

    /** Last progress received by this client. Plain data, no client classes, so it can live in common code. */
    private static volatile Set<String> clientDone = Set.of();

    private static volatile boolean clientEarned;

    public static Set<String> clientDone() {
        return clientDone;
    }

    /** Client: whether a guide step was finished since the last call (several in one go count once). */
    public static boolean takeEarned() {
        boolean earned = clientEarned;
        clientEarned = false;
        return earned;
    }

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }

    static void handle(GuideProgressPayload payload, IPayloadContext context) {
        clientDone = Set.copyOf(new HashSet<>(payload.done()));
        if (payload.earned()) clientEarned = true;
    }
}
