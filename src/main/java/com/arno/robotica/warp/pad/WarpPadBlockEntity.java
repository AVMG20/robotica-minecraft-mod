package com.arno.robotica.warp.pad;

import com.arno.robotica.core.block.SyncedBlockEntity;
import com.arno.robotica.core.energy.MachineEnergyStorage;
import com.arno.robotica.warp.WarpConfig;
import com.arno.robotica.warp.WarpRegistry;
import net.minecraft.core.BlockPos;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.block.state.BlockState;
import org.jetbrains.annotations.Nullable;

import java.util.UUID;

/**
 * Warp Pad data: identity, owner (UUID), name, visibility and the FE buffer. The registry ({@link WarpPads}) mirrors
 * everything but the energy; the rift flag lives in the block state.
 */
public class WarpPadBlockEntity extends SyncedBlockEntity {
    public static final int MAX_NAME = 24;
    public static final String DEFAULT_NAME = "Warp Pad";

    @Nullable
    private UUID padId;
    @Nullable
    private UUID owner;
    private String ownerName = "";
    private String name = DEFAULT_NAME;
    private boolean isPublic;

    public final MachineEnergyStorage energy = new MachineEnergyStorage(WarpConfig.padBuffer(), WarpConfig.padMaxReceive(), 0, this::setChanged);

    public WarpPadBlockEntity(BlockPos pos, BlockState state) {
        super(WarpRegistry.WARP_PAD_BE.get(), pos, state);
    }

    // ---- Accessors ----

    @Nullable
    public UUID padId() {
        return padId;
    }

    @Nullable
    public UUID owner() {
        return owner;
    }

    public String ownerName() {
        return ownerName;
    }

    public String padName() {
        return name;
    }

    public boolean isPublic() {
        return isPublic;
    }

    public boolean hasRift() {
        return getBlockState().hasProperty(WarpPadBlock.RIFT) && getBlockState().getValue(WarpPadBlock.RIFT);
    }

    /** The owner, operators, and everybody for pads placed without a player may change name, visibility and upgrades. */
    public boolean canEdit(Player player) {
        return owner == null || owner.equals(player.getUUID()) || player.hasPermissions(2);
    }

    // ---- Changes ----

    /** Called when a player (or null for dispensers and commands) places the pad. */
    public void initPlacement(@Nullable Player placer) {
        if (placer != null) {
            owner = placer.getUUID();
            ownerName = placer.getGameProfile().getName();
            name = defaultName(ownerName);
        } else {
            owner = null;
            ownerName = "";
            name = DEFAULT_NAME;
        }
        isPublic = false;
        padId = UUID.randomUUID();
        setChanged();
        ensureRegistered();
    }

    public static String defaultName(String ownerName) {
        String candidate = ownerName + "'s Pad";
        return candidate.length() <= MAX_NAME ? candidate : DEFAULT_NAME;
    }

    public void rename(String newName) {
        name = newName;
        setChangedAndSync();
        ensureRegistered();
    }

    public void setPublic(boolean value) {
        isPublic = value;
        setChangedAndSync();
        ensureRegistered();
    }

    public void refreshOwnerName(String value) {
        if (!value.equals(ownerName)) {
            ownerName = value;
            setChanged();
            ensureRegistered();
        }
    }

    public PadRecord buildRecord() {
        return new PadRecord(padId, worldPosition, level.dimension(), name, owner, ownerName, isPublic, hasRift());
    }

    /**
     * Makes sure the registry knows this pad with its current data (server only). Creates an id for pads that came from
     * a command and gives copies (clone, structures) a fresh identity. Returns the record, or null on the client.
     */
    @Nullable
    public PadRecord ensureRegistered() {
        if (!(level instanceof ServerLevel serverLevel)) return null;
        WarpPads pads = WarpPads.get(serverLevel.getServer());
        if (padId == null) {
            padId = UUID.randomUUID();
            setChanged();
        }
        PadRecord existing = pads.get(padId);
        if (existing != null && (!existing.pos().equals(worldPosition) || !existing.dimension().equals(serverLevel.dimension()))) {
            padId = UUID.randomUUID();
            existing = null;
            setChanged();
        }
        PadRecord rec = buildRecord();
        if (!rec.equals(existing)) pads.put(rec);
        return rec;
    }

    @Override
    public void onLoad() {
        super.onLoad();
        if (padId != null && level instanceof ServerLevel) ensureRegistered();
    }

    // ---- Persistence ----

    @Override
    protected void saveAdditional(CompoundTag tag, HolderLookup.Provider registries) {
        super.saveAdditional(tag, registries);
        if (padId != null) tag.putUUID("padId", padId);
        if (owner != null) tag.putUUID("owner", owner);
        tag.putString("ownerName", ownerName);
        tag.putString("name", name);
        tag.putBoolean("public", isPublic);
        tag.put("energy", energy.serializeNBT(registries));
    }

    @Override
    protected void loadAdditional(CompoundTag tag, HolderLookup.Provider registries) {
        super.loadAdditional(tag, registries);
        padId = tag.hasUUID("padId") ? tag.getUUID("padId") : null;
        owner = tag.hasUUID("owner") ? tag.getUUID("owner") : null;
        ownerName = tag.getString("ownerName");
        name = tag.contains("name") ? tag.getString("name") : DEFAULT_NAME;
        isPublic = tag.getBoolean("public");
        if (tag.contains("energy")) energy.deserializeNBT(registries, tag.get("energy"));
    }

    @Override
    protected void saveClientData(CompoundTag tag, HolderLookup.Provider registries) {
        tag.putString("name", name);
        tag.putBoolean("public", isPublic);
    }
}
