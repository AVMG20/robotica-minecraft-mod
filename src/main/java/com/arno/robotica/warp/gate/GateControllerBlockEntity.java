package com.arno.robotica.warp.gate;

import com.arno.robotica.core.CoreSounds;
import net.minecraft.sounds.SoundSource;
import com.arno.robotica.core.block.SyncedBlockEntity;
import com.arno.robotica.core.energy.MachineEnergyStorage;
import com.arno.robotica.warp.WarpConfig;
import com.arno.robotica.warp.WarpRegistry;
import com.arno.robotica.warp.pad.WarpPads;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.GlobalPos;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.NbtOps;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

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
    @Nullable
    private UUID owner;
    private String ownerName = "";
    /** Entities that touched the portal since the last tick. Drained at the top of every server tick, never inside a packet handler. */
    private final Set<Entity> pending = new LinkedHashSet<>();
    public static final int MAX_PENDING = 64;
    /** Cooldown after a failed attempt that is expensive to repeat (no energy, no spot, broken partner). */
    public static final int FAIL_COOLDOWN = 200;

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

    @Nullable
    public UUID owner() {
        return owner;
    }

    public String ownerName() {
        return ownerName;
    }

    /** Called when a player (or null for dispensers and commands) places the controller. */
    public void setOwner(@Nullable Player placer) {
        owner = placer == null ? null : placer.getUUID();
        ownerName = placer == null ? "" : placer.getGameProfile().getName();
        setChanged();
    }

    /** Owner, operators (level 2) and members of the owner's team may link the gate. Gates without an owner are free to claim. */
    public boolean canUse(Player player) {
        if (owner == null || owner.equals(player.getUUID()) || player.hasPermissions(2)) return true;
        if (!(level instanceof ServerLevel serverLevel) || player.getTeam() == null) return false;
        String current = WarpPads.currentName(serverLevel.getServer(), owner, ownerName);
        String ownerTeam = WarpPads.teamOfName(serverLevel.getServer(), current);
        return ownerTeam != null && ownerTeam.equals(player.getTeam().getName());
    }

    /** First linker claims a gate that has no owner yet. */
    public void claimIfUnowned(Player player) {
        if (owner == null) setOwner(player);
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
        drainPending(serverLevel);
        if (++timer < CHECK_INTERVAL) return;
        timer = 0;
        evaluate(serverLevel);
    }

    /** Re-reads frame and link without paying anything or touching the portal (right-click status). Returns the previous shape. */
    @Nullable
    public GateShape refresh(ServerLevel serverLevel) {
        Optional<GateShape> found = findShape();
        GateShape previous = shape;
        shape = found.orElse(null);

        GlobalPos partner = GateLinks.get(serverLevel.getServer()).partner(globalPos());
        if (!Objects.equals(partner, linked)) {
            linked = partner;
            setChanged();
        }
        return previous;
    }

    private long lastGateSound = Long.MIN_VALUE / 2;

    /** Checks frame, link and power, then opens or closes the portal. Only the server tick calls this: it pays the idle FE. */
    public void evaluate(ServerLevel serverLevel) {
        GateShape previous = refresh(serverLevel);

        boolean wasActive = isActive();
        boolean nowActive = shape != null && linked != null && energy.consume(WarpConfig.gateIdleCost() * CHECK_INTERVAL);
        if (nowActive) {
            fillPortal(serverLevel, shape);
        } else if (wasActive || previous != null) {
            removePortal(serverLevel, previous != null ? previous : shape);
        }
        if (nowActive != wasActive) {
            long now = serverLevel.getGameTime();
            if (now - lastGateSound >= 40) {
                lastGateSound = now;
                CoreSounds.play(serverLevel, worldPosition, nowActive ? CoreSounds.GATE_OPEN : CoreSounds.GATE_CLOSE, SoundSource.BLOCKS, 1.0F, 1.0F);
            }
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

    /** True when the entity may try the gate right now (cheap: a cooldown and a flag, no block access). */
    public boolean wouldAccept(Entity entity, long now) {
        return isActive() && shape != null && now >= readyAt(entity, now);
    }

    /**
     * An entity touched the portal (called from {@code entityInside}, possibly inside a packet handler). Only remembers
     * the entity; {@link #drainPending} does the transit at the start of the next block entity tick.
     */
    public void queueEntity(Entity entity) {
        if (!(level instanceof ServerLevel serverLevel) || !wouldAccept(entity, serverLevel.getGameTime())) return;
        if (pending.size() < MAX_PENDING) pending.add(entity);
    }

    private void drainPending(ServerLevel serverLevel) {
        if (pending.isEmpty()) return;
        List<Entity> batch = new ArrayList<>(pending);
        pending.clear();
        for (Entity entity : batch) {
            if (entity.isRemoved() || entity.level() != serverLevel) continue;
            onEntityEnter(entity);
        }
    }

    /** Sends the entity to the linked gate when it may travel and the gate can pay. */
    public void onEntityEnter(Entity entity) {
        if (!(level instanceof ServerLevel serverLevel) || !isActive() || shape == null) return;
        long now = serverLevel.getGameTime();
        if (now < readyAt(entity, now)) return; // cooldown first, before any shape or chunk work
        GateTransit.Result result = GateTransit.send(this, entity);
        if (result != GateTransit.Result.SENT) {
            boolean expensive = result == GateTransit.Result.NO_ENERGY || result == GateTransit.Result.NO_SPOT
                    || result == GateTransit.Result.PARTNER_BROKEN || result == GateTransit.Result.NOT_GENERATED;
            setCooldown(entity, now, expensive ? FAIL_COOLDOWN : 40);
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
        if (owner != null) tag.putUUID("owner", owner);
        tag.putString("ownerName", ownerName);
        if (linked != null) {
            tag.put("linked", GlobalPos.CODEC.encodeStart(NbtOps.INSTANCE, linked).getOrThrow());
        }
    }

    @Override
    protected void loadAdditional(CompoundTag tag, HolderLookup.Provider registries) {
        super.loadAdditional(tag, registries);
        if (tag.contains("energy")) energy.deserializeNBT(registries, tag.get("energy"));
        owner = tag.hasUUID("owner") ? tag.getUUID("owner") : null;
        ownerName = tag.getString("ownerName");
        linked = tag.contains("linked") ? GlobalPos.CODEC.parse(NbtOps.INSTANCE, tag.get("linked")).result().orElse(null) : null;
    }
}
