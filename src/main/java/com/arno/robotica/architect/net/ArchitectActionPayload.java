package com.arno.robotica.architect.net;

import com.arno.robotica.Robotica;
import com.arno.robotica.architect.block.ArchitectTableBlockEntity;
import com.arno.robotica.architect.menu.ArchitectMenu;
import io.netty.buffer.ByteBuf;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.neoforge.network.handling.IPayloadContext;

/**
 * Architect Table GUI request (queue a module, cancel, forget a plot, toggle clear terrain, pick a style).
 * The server only acts when the player has this table's menu open, is allowed to use the table (owner, same team
 * or operator) and is within reach; plot, module and style values are validated by the block entity.
 */
public record ArchitectActionPayload(BlockPos pos, int action, int a, int b) implements CustomPacketPayload {
    public static final Type<ArchitectActionPayload> TYPE = new Type<>(Robotica.id("architect_action"));
    public static final StreamCodec<ByteBuf, ArchitectActionPayload> STREAM_CODEC = StreamCodec.composite(
            BlockPos.STREAM_CODEC, ArchitectActionPayload::pos,
            ByteBufCodecs.VAR_INT, ArchitectActionPayload::action,
            ByteBufCodecs.VAR_INT, ArchitectActionPayload::a,
            ByteBufCodecs.VAR_INT, ArchitectActionPayload::b,
            ArchitectActionPayload::new);

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }

    public static void handle(ArchitectActionPayload payload, IPayloadContext context) {
        context.enqueueWork(() -> {
            if (!(context.player() instanceof ServerPlayer player)) return;
            if (!(player.containerMenu instanceof ArchitectMenu menu) || !menu.pos().equals(payload.pos())) return;
            if (player.distanceToSqr(payload.pos().getX() + 0.5, payload.pos().getY() + 0.5, payload.pos().getZ() + 0.5) > 64.0) return;
            if (!(player.level().getBlockEntity(payload.pos()) instanceof ArchitectTableBlockEntity table)) return;
            if (!table.canUse(player)) {
                player.displayClientMessage(Component.translatable("message.robotica.architect_not_owner", table.ownerName()), true);
                return;
            }
            Component feedback = table.handleAction(player, payload.action(), payload.a(), payload.b());
            if (feedback != null) player.displayClientMessage(feedback, true);
        });
    }
}
