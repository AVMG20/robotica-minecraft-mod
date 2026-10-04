package com.arno.robotica.warp.gate;

import com.arno.robotica.warp.WarpConfig;
import com.arno.robotica.warp.teleport.Teleporter;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.GlobalPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.phys.Vec3;
import org.jetbrains.annotations.Nullable;

import java.util.Optional;

/** Sends one entity from a gate to its linked gate: checks, safe spot, payment, move, effects, cooldown. */
public final class GateTransit {
    private GateTransit() {}

    public enum Result {
        SENT(null),
        NOT_ALLOWED(null),
        NO_PARTNER("message.robotica.warp.gate_no_partner"),
        PARTNER_BROKEN("message.robotica.warp.gate_partner_broken"),
        NO_SPOT("message.robotica.warp.no_safe_spot"),
        NO_ENERGY("message.robotica.warp.gate_no_energy"),
        CANCELLED(null);

        private final String messageKey;

        Result(String messageKey) {
            this.messageKey = messageKey;
        }

        @Nullable
        public String messageKey() {
            return messageKey;
        }
    }

    /** Sends the entity through the gate. Nothing is paid unless the entity arrives. */
    public static Result send(GateControllerBlockEntity from, Entity entity) {
        if (!(from.getLevel() instanceof ServerLevel level) || from.shape() == null) return Result.NOT_ALLOWED;
        if (!Teleporter.canTeleport(entity)) return Result.NOT_ALLOWED;
        GlobalPos target = from.linked();
        if (target == null) return Result.NO_PARTNER;
        ServerLevel destLevel = level.getServer().getLevel(target.dimension());
        if (destLevel == null) return Result.PARTNER_BROKEN;
        if (destLevel != level && !entity.canChangeDimensions(level, destLevel)) return Result.NOT_ALLOWED;

        destLevel.getChunk(target.pos());
        BlockEntity be = destLevel.getBlockEntity(target.pos());
        if (!(be instanceof GateControllerBlockEntity partner)) {
            GateLinks.get(level.getServer()).unlink(from.globalPos());
            from.setLinked(null);
            return Result.PARTNER_BROKEN;
        }
        Optional<GateShape> partnerShape = partner.findShape();
        if (partnerShape.isEmpty()) return Result.PARTNER_BROKEN;
        GateShape ds = partnerShape.get();

        Direction front = partner.front(ds);
        Vec3 spot = null;
        Direction out = front;
        for (Direction candidate : new Direction[]{front, front.getOpposite()}) {
            BlockPos cell = ds.inner(0, 0).relative(candidate);
            Optional<Vec3> found = Teleporter.prepareArrival(destLevel, cell);
            if (found.isPresent()) {
                spot = found.get();
                out = candidate;
                break;
            }
        }
        if (spot == null) return Result.NO_SPOT;

        int cost = WarpConfig.gateEntityCost();
        if (from.energy.getEnergyStored() < cost) return Result.NO_ENERGY;

        Vec3 origin = entity.position();
        Teleporter.departEffects(level, origin, true);
        float pitch = entity.getXRot();
        Entity moved = Teleporter.teleport(entity, destLevel, spot, out.toYRot(), pitch);
        if (moved == null) return Result.CANCELLED;
        from.energy.consume(cost);
        GateControllerBlockEntity.setCooldown(moved, destLevel.getGameTime(), WarpConfig.gateEntityCooldown());
        Teleporter.arriveEffects(destLevel, spot, true);
        return Result.SENT;
    }
}
