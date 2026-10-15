package com.arno.robotica.automation.entity;

import com.arno.robotica.automation.AutomationConfig;
import com.arno.robotica.automation.AutomationContent;
import com.arno.robotica.automation.block.ChunkAnchorBlock;
import com.arno.robotica.automation.menu.ChunkAnchorMenu;
import com.arno.robotica.compat.InfoSource;
import com.arno.robotica.compat.MachineInfo;
import com.arno.robotica.compat.OwnerNames;
import com.arno.robotica.core.CoreSounds;
import com.arno.robotica.core.block.SyncedBlockEntity;
import com.arno.robotica.core.chunk.ChunkClaims;
import com.arno.robotica.core.energy.EnergyUtil;
import com.arno.robotica.core.energy.MachineEnergyStorage;
import com.arno.robotica.core.progress.Milestones;
import com.mojang.authlib.GameProfile;
import net.minecraft.core.BlockPos;
import net.minecraft.core.HolderLookup;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.server.players.GameProfileCache;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.Containers;
import net.minecraft.world.MenuProvider;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.scores.Team;
import net.neoforged.neoforge.items.ItemStackHandler;
import org.jetbrains.annotations.Nullable;

import java.util.Locale;
import java.util.Optional;
import java.util.UUID;

/**
 * Chunk Anchor: holds the tickets of its square of chunks ({@link ChunkClaims}) while it has FE, its owner is within
 * their chunk cap (all anchors, all dimensions) and, with offline loading off, while the owner is online. Upkeep is
 * {@code anchorFePerChunk} per chunk per tick; the buffer holds {@link #BUFFER_TICKS} of it. Dry, it lets the chunks
 * go and takes them back once it holds {@link #ENGAGE_TICKS} of upkeep again. The rules are checked once a second.
 */
public class ChunkAnchorBlockEntity extends SyncedBlockEntity implements MenuProvider, InfoSource {
    /** 10 minutes of upkeep. */
    public static final int BUFFER_TICKS = 12_000;
    /** Upkeep it must hold before it takes its chunks (again): 5 seconds, so a weak generator does not flicker it. */
    public static final int ENGAGE_TICKS = 100;
    private static final int CHECK_INTERVAL = 20;
    public static final int PREVIEW_TICKS = 200;

    /** What the anchor is doing, for the screen and the block's look. */
    public enum State {
        LOADING, NO_POWER, OVER_CAP, DISABLED, OFFLINE, NO_OWNER;

        public static State byOrdinal(int i) {
            State[] v = values();
            return v[Math.max(0, Math.min(v.length - 1, i))];
        }

        public String key() {
            return name().toLowerCase(Locale.ROOT);
        }

        public ChunkAnchorBlock.Mode mode() {
            return switch (this) {
                case LOADING -> ChunkAnchorBlock.Mode.ACTIVE;
                case NO_POWER, OVER_CAP -> ChunkAnchorBlock.Mode.ALERT;
                default -> ChunkAnchorBlock.Mode.IDLE;
            };
        }
    }

    public final MachineEnergyStorage energy;
    /** Any FE item that can extract (cells, Mainspring). */
    public final ItemStackHandler battery = new ItemStackHandler(1) {
        @Override
        public boolean isItemValid(int slot, ItemStack stack) {
            return AreaWorkerBlockEntity.canUseAsBattery(stack);
        }

        @Override
        public int getSlotLimit(int slot) {
            return 1;
        }

        @Override
        protected void onContentsChanged(int slot) {
            setChanged();
        }
    };
    /** Set while the block is swapped for its next Mk, so the old one drops nothing. */
    public boolean keepContents;

    @Nullable
    private UUID owner;
    private State state = State.NO_POWER;
    @Nullable
    private ChunkClaims.Claim claim;
    private long age;
    /** Outline toggled on in the GUI. Synced. */
    private boolean showArea;
    /** Game time until which the loaded chunks are outlined. Synced. */
    private long previewUntil;
    @Nullable
    private AABB areaCache;
    @Nullable
    private AABB blockBox;

    public ChunkAnchorBlockEntity(BlockPos pos, BlockState state) {
        super(AutomationContent.CHUNK_ANCHOR_BE.get(), pos, state);
        int upkeep = upkeepOf(state);
        this.energy = new MachineEnergyStorage((int) Math.min(Integer.MAX_VALUE, Math.max(1_000L, (long) upkeep * BUFFER_TICKS)),
                Math.max(1_000, upkeep * 20), 0, this::setChanged);
    }

    // ---- numbers ----

    public int tier() {
        return ChunkAnchorBlock.tierOf(getBlockState());
    }

    public int radius() {
        return Math.max(0, tier() - 1);
    }

    /** Side of the square in chunks: 1, 3, 5. */
    public int side() {
        return 2 * radius() + 1;
    }

    public int chunkCount() {
        return ChunkClaims.chunks(radius());
    }

    /** FE per tick while it loads. */
    public int upkeep() {
        return upkeepOf(getBlockState());
    }

    private static int upkeepOf(BlockState state) {
        return ChunkClaims.chunks(Math.max(0, ChunkAnchorBlock.tierOf(state) - 1)) * AutomationConfig.anchorFePerChunk();
    }

    public State state() {
        return state;
    }

    public boolean isLoading() {
        return claim != null && claim.active();
    }

    /** Chunks the owner's anchors load right now (all dimensions). Server side. */
    public int ownerUsed() {
        if (owner == null || !(level instanceof ServerLevel sl)) return 0;
        return ChunkClaims.get(sl.getServer()).used(owner);
    }

    @Nullable
    public UUID owner() {
        return owner;
    }

    public void setOwner(@Nullable UUID uuid) {
        this.owner = uuid;
        setChanged();
    }

    // ---- tick ----

    public static void serverTick(Level level, BlockPos pos, BlockState state, ChunkAnchorBlockEntity be) {
        if (level instanceof ServerLevel sl) be.tick(sl);
    }

    private void tick(ServerLevel sl) {
        age++;
        pullBattery();
        if (claim != null && claim.active()) {
            if (energy.consume(upkeep())) {
                if (CoreSounds.due(sl, worldPosition, 80)) CoreSounds.play(sl, worldPosition, CoreSounds.CHUNK_ANCHOR_HUM, SoundSource.BLOCKS, 0.6F, 1.0F);
            } else {
                ChunkClaims.get(sl.getServer()).deactivate(sl, claim);
                setState(sl, State.NO_POWER);
            }
        }
        if (age == 1 || (age + worldPosition.asLong()) % CHECK_INTERVAL == 0) evaluate(sl);
    }

    /** Checks owner, cap, offline rule and power; takes or lets go of the chunks. */
    private void evaluate(ServerLevel sl) {
        ChunkClaims claims = ChunkClaims.get(sl.getServer());
        if (owner == null) {
            if (claim != null) {
                claims.remove(sl, worldPosition);
                claim = null;
            }
            setState(sl, State.NO_OWNER);
            return;
        }
        boolean fresh = claim == null;
        claim = claims.claim(sl, worldPosition, owner, radius());
        // After a restart NeoForge restored the tickets already; asking again is free and refreshes the mirror.
        if (fresh && claim.active()) claims.activate(sl, claim);
        int cap = ChunkClaims.cap();
        if (cap <= 0) {
            claims.deactivate(sl, claim);
            setState(sl, State.DISABLED);
            return;
        }
        if (!ChunkClaims.whileOffline() && sl.getServer().getPlayerList().getPlayer(owner) == null) {
            claims.pause(sl, claim);
            setState(sl, State.OFFLINE);
            return;
        }
        if (claim.active()) {
            if (claims.used(owner) > cap) {
                claims.deactivate(sl, claim);
                setState(sl, State.OVER_CAP);
            } else {
                setState(sl, State.LOADING);
            }
            return;
        }
        if (energy.getEnergyStored() < (long) upkeep() * ENGAGE_TICKS) {
            setState(sl, State.NO_POWER);
            return;
        }
        if (claims.used(owner) + claim.chunks() > cap) {
            setState(sl, State.OVER_CAP);
            return;
        }
        claims.activate(sl, claim);
        setState(sl, State.LOADING);
    }

    private void setState(ServerLevel sl, State next) {
        if (next != state) {
            State from = state;
            state = next;
            setChanged();
            if (next == State.LOADING) {
                CoreSounds.play(sl, worldPosition, CoreSounds.CHUNK_ANCHOR_ON, SoundSource.BLOCKS, 0.8F, 1.0F);
                sl.sendParticles(ParticleTypes.REVERSE_PORTAL, worldPosition.getX() + 0.5, worldPosition.getY() + 0.6,
                        worldPosition.getZ() + 0.5, 24, 0.3, 0.3, 0.3, 0.05);
            } else if (from == State.LOADING) {
                CoreSounds.play(sl, worldPosition, CoreSounds.CHUNK_ANCHOR_OFF, SoundSource.BLOCKS, 0.8F, 1.0F);
            }
            if (next == State.NO_POWER || next == State.OVER_CAP) {
                ServerPlayer near = Milestones.nearbyOwner(sl, worldPosition, owner, 32);
                if (near != null) near.displayClientMessage(Component.translatable("message.robotica.anchor_" + next.key(),
                        getBlockState().getBlock().getName()), true);
            }
        }
        BlockState bs = getBlockState();
        if (bs.hasProperty(ChunkAnchorBlock.MODE) && bs.getValue(ChunkAnchorBlock.MODE) != next.mode()) {
            sl.setBlock(worldPosition, bs.setValue(ChunkAnchorBlock.MODE, next.mode()), Block.UPDATE_CLIENTS);
        }
    }

    private void pullBattery() {
        ItemStack stack = battery.getStackInSlot(0);
        if (stack.isEmpty() || energy.getSpace() <= 0) return;
        if (energy.getEnergyStored() > energy.getMaxEnergyStored() / 4 && age % 4 != 0) return;
        if (EnergyUtil.dischargeItem(stack, energy, Math.max(1_000, upkeep() * 4)) > 0) setChanged();
    }

    /** Called after an in-place Mk upgrade loaded the old anchor's data. */
    public void afterUpgrade() {
        energy.setEnergy(energy.getEnergyStored());
        startPreview();
    }

    /**
     * The block is gone: tickets released, battery dropped. Swapped for the next Mk ({@link #keepContents}): the claim
     * stays, so the new anchor swaps the old square for its bigger one in one step, with no gap.
     */
    public void onBroken() {
        if (!(level instanceof ServerLevel sl)) return;
        if (keepContents) return;
        ChunkClaims.get(sl.getServer()).remove(sl, worldPosition);
        claim = null;
        Containers.dropItemStack(level, worldPosition.getX(), worldPosition.getY(), worldPosition.getZ(), battery.getStackInSlot(0));
        battery.setStackInSlot(0, ItemStack.EMPTY);
    }

    // ---- area preview ----

    /** Outlines the loaded chunks for 10 seconds. Server side. */
    public void startPreview() {
        if (level == null || level.isClientSide) return;
        previewUntil = level.getGameTime() + PREVIEW_TICKS;
        setChangedAndSync();
    }

    public boolean showArea() {
        return showArea;
    }

    public void toggleShowArea() {
        showArea = !showArea;
        setChangedAndSync();
    }

    /** Client side: true while the outline shows (the toggle, or a few seconds after placing or sneak-right-clicking). */
    public boolean outlineVisible() {
        return showArea || (level != null && level.getGameTime() < previewUntil);
    }

    /** The anchor's square of chunks, full world height. */
    public AABB area() {
        if (areaCache == null) {
            ChunkPos c = new ChunkPos(worldPosition);
            int r = radius();
            int minY = level != null ? level.getMinBuildHeight() : -64;
            int maxY = level != null ? level.getMaxBuildHeight() : 320;
            areaCache = new AABB((c.x - r) * 16, minY, (c.z - r) * 16, (c.x + r + 1) * 16, maxY, (c.z + r + 1) * 16);
        }
        return areaCache;
    }

    /** The block plus the spinning rings, for render culling. */
    public AABB blockBox() {
        if (blockBox == null) blockBox = new AABB(worldPosition).inflate(0.5);
        return blockBox;
    }

    /** One-line status for the action bar: name, area, state, energy. */
    public Component statusLine() {
        int percent = energy.getMaxEnergyStored() <= 0 ? 0 : (int) (100L * energy.getEnergyStored() / energy.getMaxEnergyStored());
        return Component.translatable("message.robotica.anchor_status", getBlockState().getBlock().getName(), side(), side(),
                Component.translatable("gui.robotica.anchor.state." + state.key()), percent);
    }

    // ---- access: owner, team, operators ----

    /** Owner, a player on the owner's team or an operator (no owner: anyone). */
    public boolean canUse(Player player) {
        if (owner == null || owner.equals(player.getUUID()) || player.hasPermissions(2)) return true;
        Team mine = player.getTeam();
        if (mine == null || !(level instanceof ServerLevel sl)) return false;
        String name = ownerName(sl.getServer());
        if (name == null) return false;
        Team theirs = level.getScoreboard().getPlayersTeam(name);
        return theirs != null && mine.isAlliedTo(theirs);
    }

    @Nullable
    private String ownerName(MinecraftServer server) {
        ServerPlayer online = server.getPlayerList().getPlayer(owner);
        if (online != null) return online.getGameProfile().getName();
        GameProfileCache cache = server.getProfileCache();
        if (cache == null) return null;
        Optional<GameProfile> profile = cache.get(owner);
        return profile.map(GameProfile::getName).filter(n -> !n.isEmpty()).orElse(null);
    }

    @Override
    public void collectInfo(ServerLevel level, MachineInfo info) {
        info.status = switch (state) {
            case LOADING -> "working";
            case NO_POWER -> "no_energy";
            default -> "idle";
        };
        info.tier = tier();
        info.owner = OwnerNames.name(level.getServer(), owner);
    }

    // ---- menu ----

    @Override
    public Component getDisplayName() {
        return getBlockState().getBlock().getName();
    }

    @Nullable
    @Override
    public AbstractContainerMenu createMenu(int id, Inventory inv, Player player) {
        return new ChunkAnchorMenu(id, inv, this);
    }

    // ---- persistence ----

    @Override
    protected void saveAdditional(CompoundTag tag, HolderLookup.Provider registries) {
        super.saveAdditional(tag, registries);
        tag.put("energy", energy.serializeNBT(registries));
        tag.put("battery", battery.serializeNBT(registries));
        if (owner != null) tag.putUUID("owner", owner);
        tag.putBoolean("showArea", showArea);
    }

    @Override
    protected void loadAdditional(CompoundTag tag, HolderLookup.Provider registries) {
        super.loadAdditional(tag, registries);
        if (tag.contains("energy")) {
            energy.deserializeNBT(registries, tag.get("energy"));
            energy.setEnergy(energy.getEnergyStored()); // clamp to this Mk's buffer
        }
        if (tag.contains("battery")) battery.deserializeNBT(registries, tag.getCompound("battery"));
        if (tag.hasUUID("owner")) owner = tag.getUUID("owner");
        if (tag.contains("showArea")) showArea = tag.getBoolean("showArea");
        if (tag.contains("previewUntil")) previewUntil = tag.getLong("previewUntil"); // client update tag
    }

    @Override
    protected void saveClientData(CompoundTag tag, HolderLookup.Provider registries) {
        tag.putBoolean("showArea", showArea);
        tag.putLong("previewUntil", previewUntil);
    }
}
