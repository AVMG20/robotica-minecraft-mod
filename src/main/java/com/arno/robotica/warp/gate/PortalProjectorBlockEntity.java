package com.arno.robotica.warp.gate;

import com.arno.robotica.core.CoreSounds;
import com.arno.robotica.core.block.SyncedBlockEntity;
import com.arno.robotica.core.energy.MachineEnergyStorage;
import com.arno.robotica.warp.WarpConfig;
import com.arno.robotica.warp.WarpRegistry;
import com.arno.robotica.warp.pad.WarpPads;
import com.arno.robotica.warp.teleport.Teleporter;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.GlobalPos;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.NbtOps;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.AABB;
import org.jetbrains.annotations.Nullable;

import java.util.List;
import java.util.Objects;
import java.util.UUID;

/**
 * The brain of a Portal Projector. Every {@link #CHECK_INTERVAL} ticks it reads its link from {@link GateLinks} and, while
 * linked and powered, projects the portal (blockstate ACTIVE; the client draws it). The idle cost is paid per check. Every
 * {@link #SCAN_INTERVAL} ticks an active projector looks for entities inside the portal's volume
 * ({@link PortalGeometry#triggerBox}) with a single entity query and hands them to {@link GateTransit}, all inside its own
 * server tick.
 */
public class PortalProjectorBlockEntity extends SyncedBlockEntity implements com.arno.robotica.compat.InfoSource {
    public static final int CHECK_INTERVAL = 10;
    public static final int SCAN_INTERVAL = 2;
    public static final int MAX_PER_SCAN = 8;
    public static final String COOLDOWN_KEY = "robotica_gate_ready";

    public final MachineEnergyStorage energy = new MachineEnergyStorage(WarpConfig.gateBuffer(), WarpConfig.gateMaxReceive(), 0, this::setChanged);

    /** Cached copy of the link (source of truth: {@link GateLinks}). */
    @Nullable
    private GlobalPos linked;
    private int timer = (int) (Math.random() * CHECK_INTERVAL);
    private int scanTimer;
    /** Client only: game time the portal started to open (drives the opening animation); never saved. */
    public long clientOpenedAt = Long.MIN_VALUE;
    @Nullable
    private UUID owner;
    private String ownerName = "";
    /** Cooldown after a failed attempt that is expensive to repeat (no energy, no spot, broken partner). */
    public static final int FAIL_COOLDOWN = 200;

    public PortalProjectorBlockEntity(BlockPos pos, BlockState state) {
        super(WarpRegistry.GATE_CONTROLLER_BE.get(), pos, state);
    }

    // ---- State ----

    @Nullable
    public GlobalPos linked() {
        return linked;
    }

    public boolean isActive() {
        return getBlockState().hasProperty(PortalProjectorBlock.ACTIVE) && getBlockState().getValue(PortalProjectorBlock.ACTIVE);
    }

    @Nullable
    public UUID owner() {
        return owner;
    }

    /** Jade: projecting, waiting for power (linked but empty), or idle; and the owner. */
    @Override
    public void collectInfo(ServerLevel level, com.arno.robotica.compat.MachineInfo info) {
        info.status = isActive() ? "working" : linked != null && energy.getEnergyStored() < WarpConfig.gateIdleCost() ? "no_energy" : "idle";
        if (!ownerName.isEmpty()) info.owner = ownerName;
    }

    public String ownerName() {
        return ownerName;
    }

    /** Called when a player (or null for dispensers and commands) places the projector. */
    public void setOwner(@Nullable Player placer) {
        owner = placer == null ? null : placer.getUUID();
        ownerName = placer == null ? "" : placer.getGameProfile().getName();
        setChanged();
    }

    /** Owner, operators (level 2) and members of the owner's team may link the projector. Projectors without an owner are free to claim. */
    public boolean canUse(Player player) {
        if (owner == null || owner.equals(player.getUUID()) || player.hasPermissions(2)) return true;
        if (!(level instanceof ServerLevel serverLevel) || player.getTeam() == null) return false;
        String current = WarpPads.currentName(serverLevel.getServer(), owner, ownerName);
        String ownerTeam = WarpPads.teamOfName(serverLevel.getServer(), current);
        return ownerTeam != null && ownerTeam.equals(player.getTeam().getName());
    }

    /** First linker claims a projector that has no owner yet. */
    public void claimIfUnowned(Player player) {
        if (owner == null) setOwner(player);
    }

    public GlobalPos globalPos() {
        return GlobalPos.of(level.dimension(), worldPosition);
    }

    /** The side the portal faces: travellers arrive here and leave looking this way. */
    public Direction front() {
        return getBlockState().getValue(PortalProjectorBlock.FACING);
    }

    /** The volume that sends entities through. */
    public AABB portalBox() {
        return PortalGeometry.triggerBox(worldPosition, front());
    }

    // ---- Tick ----

    public void serverTick(ServerLevel serverLevel) {
        if (++timer >= CHECK_INTERVAL) {
            timer = 0;
            evaluate(serverLevel);
        }
        if (++scanTimer >= SCAN_INTERVAL) {
            scanTimer = 0;
            if (isActive()) scan(serverLevel);
        }
    }

    /** Re-reads the link without paying anything (right-click status). */
    public void refresh(ServerLevel serverLevel) {
        GlobalPos partner = GateLinks.get(serverLevel.getServer()).partner(globalPos());
        if (!Objects.equals(partner, linked)) {
            linked = partner;
            setChanged();
        }
    }

    private long lastGateSound = Long.MIN_VALUE / 2;

    /** Checks link and power, then projects or retracts the portal. Only the server tick calls this: it pays the idle FE. */
    public void evaluate(ServerLevel serverLevel) {
        refresh(serverLevel);
        boolean wasActive = isActive();
        boolean nowActive = linked != null && energy.consume(WarpConfig.gateIdleCost() * CHECK_INTERVAL);
        if (nowActive != wasActive) {
            long now = serverLevel.getGameTime();
            if (now - lastGateSound >= 40) {
                lastGateSound = now;
                CoreSounds.play(serverLevel, worldPosition, nowActive ? CoreSounds.GATE_OPEN : CoreSounds.GATE_CLOSE, SoundSource.BLOCKS, 1.0F, 1.0F);
            }
            serverLevel.setBlock(worldPosition, getBlockState().setValue(PortalProjectorBlock.ACTIVE, nowActive), Block.UPDATE_ALL);
        }
    }

    /** Called when the projector is broken: drops the link on both sides. */
    public void shutdown(ServerLevel serverLevel) {
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

    /** Entity cooldown (game time it may use a portal again) from its persistent data. */
    public static long readyAt(Entity entity, long now) {
        long ready = entity.getPersistentData().getLong(COOLDOWN_KEY);
        return ready - now > 72_000 ? 0 : ready; // a cooldown from a world with a later clock is void
    }

    public static void setCooldown(Entity entity, long now, int ticks) {
        entity.getPersistentData().putLong(COOLDOWN_KEY, now + ticks);
    }

    /** True when the entity may try the portal right now (cheap: a cooldown and a flag). */
    public boolean wouldAccept(Entity entity, long now) {
        return isActive() && now >= readyAt(entity, now);
    }

    /** Entities that are inside the portal's volume right now and may travel (one entity query). */
    public List<Entity> entitiesInPortal(ServerLevel serverLevel) {
        long now = serverLevel.getGameTime();
        return serverLevel.getEntities((Entity) null, portalBox(), e -> Teleporter.canTeleport(e) && now >= readyAt(e, now));
    }

    private void scan(ServerLevel serverLevel) {
        List<Entity> inside = entitiesInPortal(serverLevel);
        int handled = 0;
        for (Entity entity : inside) {
            if (handled++ >= MAX_PER_SCAN) break;
            if (!entity.isRemoved() && entity.level() == serverLevel) onEntityEnter(entity);
        }
    }

    /** Sends the entity to the linked projector when it may travel and this one can pay. */
    public void onEntityEnter(Entity entity) {
        if (!(level instanceof ServerLevel serverLevel) || !isActive()) return;
        long now = serverLevel.getGameTime();
        if (now < readyAt(entity, now)) return; // cooldown first, before any chunk work
        GateTransit.Result result = GateTransit.send(this, entity);
        if (result != GateTransit.Result.SENT) {
            boolean expensive = result == GateTransit.Result.NO_ENERGY || result == GateTransit.Result.NO_SPOT
                    || result == GateTransit.Result.PARTNER_BROKEN || result == GateTransit.Result.NOT_GENERATED;
            setCooldown(entity, now, expensive ? FAIL_COOLDOWN : 40);
            if (entity instanceof ServerPlayer player && result.messageKey() != null) {
                player.displayClientMessage(Component.translatable(result.messageKey()), true);
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
