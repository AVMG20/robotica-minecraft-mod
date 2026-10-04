package com.arno.robotica.warp.gate;

import com.arno.robotica.core.block.SyncedBlockEntity;
import com.arno.robotica.core.energy.MachineEnergyStorage;
import com.arno.robotica.warp.WarpConfig;
import com.arno.robotica.warp.WarpRegistry;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.GlobalPos;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.NbtOps;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
import org.jetbrains.annotations.Nullable;

import java.util.Objects;
import java.util.Optional;

/**
 * The brain of a Portal Gate. Every {@link #CHECK_INTERVAL} ticks it checks the frame, reads its link from
 * {@link GateLinks} and, while formed, linked and powered, keeps the Gate Portal blocks in the opening. The idle cost is
 * paid per check (500 FE/t by default). Entities that touch the portal are handed to {@link GateTransit}.
 */
public class GateControllerBlockEntity extends SyncedBlockEntity {
    public static final int CHECK_INTERVAL = 10;
    public static final String COOLDOWN_KEY = "robotica_gate_ready";

    public final MachineEnergyStorage energy = new MachineEnergyStorage(WarpConfig.gateBuffer(), WarpConfig.gateMaxReceive(), 0, this::setChanged);

    /** Cached copy of the link (source of truth: {@link GateLinks}). */
    @Nullable
    private GlobalPos linked;
    @Nullable
    private GateShape shape;
    private int timer = (int) (Math.random() * CHECK_INTERVAL);

    public GateControllerBlockEntity(BlockPos pos, BlockState state) {
        super(WarpRegistry.GATE_CONTROLLER_BE.get(), pos, state);
    }

    // ---- State ----

    @Nullable
    public GlobalPos linked() {
        return linked;
    }

    /** The frame found at the last check, or null when the gate is not formed. */
    @Nullable
    public GateShape shape() {
        return shape;
    }

    public boolean isActive() {
        return getBlockState().hasProperty(GateControllerBlock.ACTIVE) && getBlockState().getValue(GateControllerBlock.ACTIVE);
    }

    public GlobalPos globalPos() {
        return GlobalPos.of(level.dimension(), worldPosition);
    }

    /** Direction travellers are sent out in: the controller's facing when it is on the portal's axis, else the positive side. */
    public Direction front(GateShape forShape) {
        Direction facing = getBlockState().getValue(GateControllerBlock.FACING);
        return facing.getAxis() == forShape.normalAxis() ? facing : Direction.get(Direction.AxisDirection.POSITIVE, forShape.normalAxis());
    }

    // ---- Frame ----

    private static GateShape.Probe probe(Level level) {
        return new GateShape.Probe() {
            @Override
            public boolean isFrame(BlockPos pos) {
                return level.getBlockState(pos).is(WarpRegistry.GATE_FRAME.get());
            }

            @Override
            public boolean isOpen(BlockPos pos) {
                BlockState state = level.getBlockState(pos);
                return state.isAir() || state.is(WarpRegistry.GATE_PORTAL.get());
            }
        };
    }

    /** Searches the frame around this controller right now. */
    public Optional<GateShape> findShape() {
        Direction facing = getBlockState().getValue(GateControllerBlock.FACING);
        Direction.Axis preferred = facing.getAxis() == Direction.Axis.X ? Direction.Axis.Z : Direction.Axis.X;
        return GateShape.find(probe(level), worldPosition, preferred);
    }

    // ---- Tick ----

    public void serverTick(ServerLevel serverLevel) {
        if (++timer < CHECK_INTERVAL) return;
        timer = 0;
        evaluate(serverLevel);
    }

    /** Checks frame, link and power, then opens or closes the portal. */
    public void evaluate(ServerLevel serverLevel) {
        Optional<GateShape> found = findShape();
        GateShape previous = shape;
        shape = found.orElse(null);

        GlobalPos partner = GateLinks.get(serverLevel.getServer()).partner(globalPos());
        if (!Objects.equals(partner, linked)) {
            linked = partner;
            setChanged();
        }

        boolean wasActive = isActive();
        boolean nowActive = shape != null && linked != null && energy.consume(WarpConfig.gateIdleCost() * CHECK_INTERVAL);
        if (nowActive) {
            fillPortal(serverLevel, shape);
        } else if (wasActive || previous != null) {
            removePortal(serverLevel, previous != null ? previous : shape);
        }
        if (nowActive != wasActive) {
            serverLevel.setBlock(worldPosition, getBlockState().setValue(GateControllerBlock.ACTIVE, nowActive), Block.UPDATE_ALL);
        }
    }

    private void fillPortal(ServerLevel serverLevel, GateShape forShape) {
        BlockState portal = WarpRegistry.GATE_PORTAL.get().defaultBlockState().setValue(GatePortalBlock.AXIS, forShape.axis());
        for (BlockPos pos : forShape.innerPositions()) {
            BlockState current = serverLevel.getBlockState(pos);
            if (current.isAir()) serverLevel.setBlock(pos, portal, Block.UPDATE_CLIENTS);
        }
    }

    /** Removes the portal blocks of the given frame (no neighbour updates, they all go at once). */
    public void removePortal(ServerLevel serverLevel, @Nullable GateShape forShape) {
        if (forShape == null) return;
        for (BlockPos pos : forShape.innerPositions()) {
            if (serverLevel.getBlockState(pos).is(WarpRegistry.GATE_PORTAL.get())) {
                serverLevel.setBlock(pos, net.minecraft.world.level.block.Blocks.AIR.defaultBlockState(), Block.UPDATE_CLIENTS);
            }
        }
    }

    /** Called when the controller is broken: closes the portal and drops the link on both sides. */
    public void shutdown(ServerLevel serverLevel) {
        removePortal(serverLevel, shape != null ? shape : findShape().orElse(null));
        shape = null;
        GateLinks.get(serverLevel.getServer()).unlink(globalPos());
        linked = null;
    }

    // ---- Linking ----

    /** Sets the cached link (the {@link GateLinks} entry is changed by the caller). */
    public void setLinked(@Nullable GlobalPos target) {
        linked = target;
        setChanged();
    }

    // ---- Entities ----

    /** Entity cooldown (game time it may use a gate again) from its persistent data. */
    public static long readyAt(Entity entity, long now) {
        long ready = entity.getPersistentData().getLong(COOLDOWN_KEY);
        return ready - now > 72_000 ? 0 : ready; // a cooldown from a world with a later clock is void
    }

    public static void setCooldown(Entity entity, long now, int ticks) {
        entity.getPersistentData().putLong(COOLDOWN_KEY, now + ticks);
    }

    /** An entity touched the portal. Sends it to the linked gate when it may travel and the gate can pay. */
    public void onEntityEnter(Entity entity) {
        if (!(level instanceof ServerLevel serverLevel) || !isActive() || shape == null) return;
        long now = serverLevel.getGameTime();
        if (now < readyAt(entity, now)) return;
        GateTransit.Result result = GateTransit.send(this, entity);
        if (result != GateTransit.Result.SENT) {
            setCooldown(entity, now, 40);
            if (entity instanceof ServerPlayer player && result.messageKey() != null) {
                player.displayClientMessage(net.minecraft.network.chat.Component.translatable(result.messageKey()), true);
            }
        }
    }

    // ---- Persistence ----

    @Override
    protected void saveAdditional(CompoundTag tag, HolderLookup.Provider registries) {
        super.saveAdditional(tag, registries);
        tag.put("energy", energy.serializeNBT(registries));
        if (linked != null) {
            tag.put("linked", GlobalPos.CODEC.encodeStart(NbtOps.INSTANCE, linked).getOrThrow());
        }
    }

    @Override
    protected void loadAdditional(CompoundTag tag, HolderLookup.Provider registries) {
        super.loadAdditional(tag, registries);
        if (tag.contains("energy")) energy.deserializeNBT(registries, tag.get("energy"));
        linked = tag.contains("linked") ? GlobalPos.CODEC.parse(NbtOps.INSTANCE, tag.get("linked")).result().orElse(null) : null;
    }
}
