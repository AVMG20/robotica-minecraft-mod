package com.arno.robotica.warp.gate;

import com.arno.robotica.warp.WarpConfig;
import com.arno.robotica.warp.teleport.Teleporter;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.GlobalPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.phys.Vec3;
import org.jetbrains.annotations.Nullable;

import java.util.Optional;

/** Sends one entity from a projector to its linked projector: checks, safe spot, payment, move, effects, cooldown. */
public final class GateTransit {
    private GateTransit() {}

    public enum Result {
        SENT(null),
        NOT_ALLOWED(null),
        NO_PARTNER("message.robotica.warp.gate_no_partner"),
        PARTNER_BROKEN("message.robotica.warp.gate_partner_broken"),
        NO_SPOT("message.robotica.warp.no_safe_spot"),
        NO_ENERGY("message.robotica.warp.gate_no_energy"),
        NOT_GENERATED("message.robotica.warp.gate_not_generated"),
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

    /** Sends the entity through the portal. Nothing is paid unless the entity arrives. */
    public static Result send(PortalProjectorBlockEntity from, Entity entity) {
        if (!(from.getLevel() instanceof ServerLevel level)) return Result.NOT_ALLOWED;
        if (!Teleporter.canTeleport(entity)) return Result.NOT_ALLOWED;
        GlobalPos target = from.linked();
        if (target == null) return Result.NO_PARTNER;
        ServerLevel destLevel = level.getServer().getLevel(target.dimension());
        if (destLevel == null) return Result.PARTNER_BROKEN;
        if (destLevel != level && !entity.canChangeDimensions(level, destLevel)) return Result.NOT_ALLOWED;

        // cheapest checks first: a projector that can not pay refuses before any chunk or block work happens
        int cost = WarpConfig.gateEntityCost();
        if (from.energy.getEnergyStored() < cost) return Result.NO_ENERGY;

        // never generate terrain on the main thread for a portal trip
        ChunkPos partnerChunk = new ChunkPos(target.pos());
        if (!Teleporter.isGenerated(destLevel, partnerChunk.x, partnerChunk.z)) return Result.NOT_GENERATED;
        destLevel.getChunk(partnerChunk.x, partnerChunk.z);
        BlockEntity be = destLevel.getBlockEntity(target.pos());
        if (!(be instanceof PortalProjectorBlockEntity partner)) {
            GateLinks.get(level.getServer()).unlink(from.globalPos());
            from.setLinked(null);
            return Result.PARTNER_BROKEN;
        }

        // arrival: in front of the partner's portal, outside its own trigger volume, facing out
        Direction front = partner.front();
        Vec3 spot = null;
        boolean ungenerated = false;
        for (int i = 0; i < PortalGeometry.ARRIVAL_TRIES && spot == null; i++) {
            BlockPos cell = target.pos().relative(front, PortalGeometry.ARRIVAL_DISTANCE + i);
            if (!Teleporter.aroundGenerated(destLevel, cell)) {
                ungenerated = true;
                continue;
            }
            Optional<Vec3> found = Teleporter.prepareArrival(destLevel, cell);
            if (found.isPresent() && !PortalGeometry.inColumn(target.pos(), front, found.get())) spot = found.get();
        }
        if (spot == null) return ungenerated ? Result.NOT_GENERATED : Result.NO_SPOT;

        Vec3 origin = entity.position();
        Teleporter.departEffects(level, origin, true);
        float pitch = entity.getXRot();
        Entity moved = Teleporter.teleport(entity, destLevel, spot, front.toYRot(), pitch);
        if (moved == null) return Result.CANCELLED;
        from.energy.consume(cost);
        PortalProjectorBlockEntity.setCooldown(moved, destLevel.getGameTime(), WarpConfig.gateEntityCooldown());
        Teleporter.arriveEffects(destLevel, spot, true);
        return Result.SENT;
    }
}
