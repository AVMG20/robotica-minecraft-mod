package com.arno.robotica.automation.entity;

import com.arno.robotica.automation.AutomationConfig;
import com.arno.robotica.automation.block.AreaWorkerBlock;
import com.arno.robotica.automation.menu.AreaWorkerMenu;
import com.arno.robotica.compat.InfoSource;
import com.arno.robotica.compat.MachineInfo;
import com.arno.robotica.compat.OwnerNames;
import com.arno.robotica.core.side.SideConfig;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import com.arno.robotica.core.CoreConfig;
import com.arno.robotica.core.CoreSounds;
import com.arno.robotica.core.block.SyncedBlockEntity;
import com.arno.robotica.core.energy.EnergyUtil;
import com.arno.robotica.core.energy.MachineEnergyStorage;
import com.arno.robotica.core.upgrade.UpgradeKind;
import com.arno.robotica.core.upgrade.Upgrades;
import com.mojang.authlib.GameProfile;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.Containers;
import net.minecraft.world.MenuProvider;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.AABB;
import net.neoforged.neoforge.capabilities.Capabilities;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.common.util.FakePlayer;
import net.neoforged.neoforge.common.util.FakePlayerFactory;
import net.neoforged.neoforge.energy.IEnergyStorage;
import net.neoforged.neoforge.event.level.BlockEvent;
import net.neoforged.neoforge.items.IItemHandler;
import net.neoforged.neoforge.items.ItemHandlerHelper;
import net.neoforged.neoforge.items.ItemStackHandler;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import java.util.function.Supplier;

/**
 * Shared base of every area worker (Stumpy, Sprout, Excavator): battery slot, FE buffer that also accepts FE from
 * cables, upgrade slots, 9-slot buffer, output into adjacent item handlers, idle drain, owner and "show area" flag.
 * Subclasses implement {@link #work} which runs every server tick and returns the resulting status.
 */
public abstract class AreaWorkerBlockEntity extends SyncedBlockEntity implements MenuProvider, InfoSource {
    public enum Status {
        IDLE, WORKING, NO_ENERGY, OUTPUT_FULL;

        public static Status byOrdinal(int i) {
            Status[] v = values();
            return v[Math.max(0, Math.min(v.length - 1, i))];
        }
    }

    public static final int BUFFER_SLOTS = 9;
    /** Max FE pulled from the battery item per call. */
    private static final int MAX_PULL = 1000;
    private static final UUID FALLBACK_OWNER = new UUID(0L, 0xB07L);

    public final MachineEnergyStorage energy;
    /** Any FE item that can extract (cells, Mainspring). */
    public final ItemStackHandler battery = new ItemStackHandler(1) {
        @Override
        public boolean isItemValid(int slot, ItemStack stack) {
            return canUseAsBattery(stack);
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
    public final Upgrades upgrades;
    public final ItemStackHandler buffer = new ItemStackHandler(BUFFER_SLOTS) {
        @Override
        protected void onContentsChanged(int slot) {
            setChanged();
        }
    };
    /** Item and capability view of the buffer for pipes and hoppers: extract anything, insert only wanted items. */
    public final IItemHandler externalBuffer = new ExternalBuffer();
    /**
     * Per-face item config: the item capability per face follows it, and the worker only pushes its output into
     * inventories on faces that allow output. Every face starts as Input + Output (the old behaviour).
     */
    public final SideConfig sides = new SideConfig(this, () -> externalBuffer);
    /** Set while the block is swapped for its next Mk, so breaking the old one does not drop anything. */
    public boolean keepContents;
    /** Ticks the LIT state stays on after the last work tick, so a short pause does not flicker the model. */
    private static final int LIT_HOLD = 40;

    private final List<ItemStack> pending = new ArrayList<>();
    @Nullable
    private UUID owner;
    private boolean showArea;
    /** Game time until which the area outline shows on its own (after placing or upgrading). Synced. */
    private long previewUntil;
    /** Ticks a fresh preview lasts. */
    public static final int PREVIEW_TICKS = 200;
    protected int areaSize = 1;
    private Status status = Status.IDLE;
    protected long age;
    private boolean firstTick = true;
    private static final int BEEP_GAP = 200;
    private long lastWorking;
    private long lastBeep = -BEEP_GAP;
    private long lastWorkSound = -20;
    private final int upgradeSlots;

    protected AreaWorkerBlockEntity(BlockEntityType<?> type, BlockPos pos, BlockState state, Set<UpgradeKind> kinds, int upgradeSlots) {
        this(type, pos, state, kinds, upgradeSlots, AutomationConfig.energyBuffer(), 1000);
    }

    /** Same, with its own FE buffer and input rate (the Survey Rig needs far more than a robot). */
    protected AreaWorkerBlockEntity(BlockEntityType<?> type, BlockPos pos, BlockState state, Set<UpgradeKind> kinds, int upgradeSlots,
                                    int energyCapacity, int maxReceive) {
        super(type, pos, state);
        this.upgradeSlots = upgradeSlots;
        this.energy = new MachineEnergyStorage(energyCapacity, maxReceive, 0, this::setChanged);
        this.upgrades = new Upgrades(upgradeSlots, kinds, this::upgradeCap, this::onUpgradesChanged);
    }

    // ---- subclass hooks ----

    /** Runs every server tick when there is no pending output. Must return the status; consumes its own energy. */
    protected abstract Status work(ServerLevel level);

    /** How many cards of a kind this worker takes. Farm bots raise speed, range and growth with their Mk tier. */
    protected int upgradeCap(UpgradeKind kind) {
        return kind.maxStack;
    }

    /** Recomputes {@link #areaSize} (and anything cached) from tier and upgrades. */
    protected abstract void recalc();

    /** Lowest and highest world Y (inclusive-exclusive) of the work area. */
    protected abstract int areaMinY();

    protected abstract int areaMaxY();

    /** How many of this item the worker keeps in its buffer instead of passing it on. 0 = none. */
    protected int keepAmount(ItemStack stack) {
        return 0;
    }

    protected void saveExtra(CompoundTag tag, HolderLookup.Provider registries) {
    }

    protected void loadExtra(CompoundTag tag, HolderLookup.Provider registries) {
    }

    protected void saveClientExtra(CompoundTag tag) {
    }

    protected void loadClientExtra(CompoundTag tag) {
    }

    public abstract String blockKey();

    /** Extra number shown in the GUI info line (excavator depth). 0 = none. */
    public int guiExtra() {
        return 0;
    }

    /** Mk tier: the block's Mk for one-block-per-Mk workers (Excavator, Survey Rig), 1 otherwise (farm bots override). */
    public int tier() {
        return tierOf(getBlockState());
    }

    /** Mk of a worker block state, 1 for blocks without Mk blocks. Safe inside constructors. */
    public static int tierOf(BlockState state) {
        return state.getBlock() instanceof AreaWorkerBlock block && block.mkTier() > 0 ? block.mkTier() : 1;
    }

    /** Called after an in-place Mk upgrade loaded the old worker's data. */
    public void afterUpgrade() {
        upgrades.capsChanged();
        recalc();
        startPreview();
        setChangedAndSync();
    }

    /** Overlay info (Jade): status, progress, Mk and owner. */
    @Override
    public void collectInfo(ServerLevel level, MachineInfo info) {
        boolean finished = guiProgress() >= 100 && status == Status.IDLE;
        info.status = finished ? "finished" : status.name().toLowerCase(java.util.Locale.ROOT);
        info.progress = guiProgress();
        info.tier = tier();
        info.owner = OwnerNames.name(level.getServer(), owner);
    }

    // ---- common state ----

    public Status status() {
        return status;
    }

    public int areaSize() {
        return areaSize;
    }

    public boolean showArea() {
        return showArea;
    }

    /** True while the outline should be drawn: the toggle, or a few seconds after placing or upgrading. Client side. */
    public boolean outlineVisible() {
        return showArea || (level != null && level.getGameTime() < previewUntil);
    }

    /** Shows the work area for a few seconds. Server side. */
    public void startPreview() {
        if (level == null || level.isClientSide) return;
        previewUntil = level.getGameTime() + PREVIEW_TICKS;
        setChangedAndSync();
    }

    /** Percent of the job done, or -1 for workers without a finite job (farm bots). */
    public int guiProgress() {
        return -1;
    }

    public void toggleShowArea() {
        showArea = !showArea;
        setChangedAndSync();
    }

    @Nullable
    public UUID owner() {
        return owner;
    }

    public void setOwner(@Nullable UUID uuid) {
        this.owner = uuid;
        setChanged();
    }

    public int upgradeSlotCount() {
        return upgradeSlots;
    }

    public int level(UpgradeKind kind) {
        return upgrades.level(kind);
    }

    /** Horizontal min corner (inclusive) of the square area. */
    public int areaMinX() {
        return worldPosition.getX() - areaSize / 2;
    }

    public int areaMinZ() {
        return worldPosition.getZ() - areaSize / 2;
    }

    public AABB areaBox() {
        int minX = areaMinX();
        int minZ = areaMinZ();
        return new AABB(minX, areaMinY(), minZ, minX + areaSize, areaMaxY(), minZ + areaSize);
    }

    public boolean inArea(BlockPos pos) {
        int minX = areaMinX();
        int minZ = areaMinZ();
        return pos.getX() >= minX && pos.getX() < minX + areaSize && pos.getZ() >= minZ && pos.getZ() < minZ + areaSize;
    }

    protected void onUpgradesChanged() {
        if (level != null && !level.isClientSide) {
            int before = areaSize;
            recalc();
            if (areaSize != before) previewUntil = level.getGameTime() + PREVIEW_TICKS;
            setChangedAndSync();
        } else {
            setChanged();
        }
    }

    public static boolean canUseAsBattery(ItemStack stack) {
        if (stack.isEmpty()) return false;
        IEnergyStorage cap = stack.getCapability(Capabilities.EnergyStorage.ITEM);
        return cap != null && cap.canExtract();
    }

    // ---- tick ----

    public final void serverTick(ServerLevel sl) {
        if (firstTick) {
            firstTick = false;
            recalc();
        }
        age++;
        pullBattery();
        if ((age + worldPosition.asLong()) % 10 == 0) flushBuffer(sl);
        if (!pending.isEmpty()) flushPending(sl);
        Status next = pending.isEmpty() ? work(sl) : Status.OUTPUT_FULL;
        if (next != Status.WORKING && age % 5 == 0) energy.consume(1);
        if (next == Status.WORKING) lastWorking = age;
        if (next != status) {
            announce(sl, status, next);
            status = next;
            setChanged();
        }
        if ((status == Status.NO_ENERGY || status == Status.OUTPUT_FULL) && (age + worldPosition.asLong()) % 40 == 0) stallParticles(sl);
        if (next == Status.WORKING) litUntil = age + LIT_HOLD;
        if (age % 10 == 0) updateLit(sl, age < litUntil);
        sides.tick(sl);
    }

    private long litUntil;

    /** Working look (glowing drill light, scan band) through the block's LIT property, client update only. */
    private void updateLit(ServerLevel sl, boolean lit) {
        BlockState state = getBlockState();
        if (state.hasProperty(BlockStateProperties.LIT) && state.getValue(BlockStateProperties.LIT) != lit) {
            sl.setBlock(worldPosition, state.setValue(BlockStateProperties.LIT, lit), Block.UPDATE_CLIENTS);
        }
    }

    /** Puffs above a stalled robot so you can see from afar that it needs you: smoke without energy, a note when full. */
    private void stallParticles(ServerLevel sl) {
        double x = worldPosition.getX() + 0.5, y = worldPosition.getY() + 1.1, z = worldPosition.getZ() + 0.5;
        if (status == Status.NO_ENERGY) {
            sl.sendParticles(net.minecraft.core.particles.ParticleTypes.SMOKE, x, y, z, 6, 0.2, 0.1, 0.2, 0.01);
        } else {
            sl.sendParticles(net.minecraft.core.particles.ParticleTypes.ANGRY_VILLAGER, x, y + 0.2, z, 1, 0.1, 0.05, 0.1, 0.0);
        }
    }

    /** One-line status for the action bar: name, area, status hint and energy. */
    public Component statusLine() {
        int percent = energy.getMaxEnergyStored() <= 0 ? 0 : (int) (100L * energy.getEnergyStored() / energy.getMaxEnergyStored());
        ItemStack cell = battery.getStackInSlot(0);
        if (!cell.isEmpty()) {
            int cap = com.arno.robotica.core.energy.ItemEnergy.capacity(cell);
            if (cap > 0) percent = (int) (100L * com.arno.robotica.core.energy.ItemEnergy.get(cell) / cap);
        }
        return Component.translatable("message.robotica.robot_status", Component.translatable(blockKey()), areaSize, areaSize,
                Component.translatable("gui.robotica.status." + status.name().toLowerCase(java.util.Locale.ROOT)), percent);
    }

    /** Robot chatter on status changes: a beep when it starts after a long rest, a buzz when it stalls. Rate limited. */
    private void announce(ServerLevel sl, Status from, Status to) {
        boolean stalled = to == Status.NO_ENERGY || to == Status.OUTPUT_FULL;
        if (to == Status.WORKING) {
            com.arno.robotica.core.progress.Milestones.awardOwner(sl, worldPosition, owner, com.arno.robotica.core.progress.Milestones.ROBOT_WORKING);
        }
        if (stalled && from != to) {
            // Tell the owner once per stall when they are close enough to do something about it.
            var player = com.arno.robotica.core.progress.Milestones.nearbyOwner(sl, worldPosition, owner, 32);
            if (player != null) {
                player.displayClientMessage(Component.translatable(to == Status.NO_ENERGY ? "message.robotica.robot_no_energy" : "message.robotica.robot_full",
                        Component.translatable(blockKey())), true);
            }
        }
        if (to == Status.WORKING && age - lastWorking > BEEP_GAP && age - lastBeep > BEEP_GAP) {
            lastBeep = age;
            CoreSounds.play(sl, worldPosition, CoreSounds.ROBOT_BEEP, SoundSource.NEUTRAL, 0.7F, 1.0F);
        } else if (stalled && from != Status.NO_ENERGY && from != Status.OUTPUT_FULL && age - lastBeep > BEEP_GAP) {
            lastBeep = age;
            CoreSounds.play(sl, worldPosition, CoreSounds.ROBOT_ERROR, SoundSource.NEUTRAL, 0.7F, 1.0F);
        }
    }

    /** Plays a work sound at most once a second per robot (several actions can finish within a second with speed cards). */
    protected void workSound(ServerLevel sl, BlockPos at, Supplier<SoundEvent> sound, float volume, float pitch) {
        if (age - lastWorkSound < 20) return;
        lastWorkSound = age;
        CoreSounds.play(sl, at, sound, SoundSource.NEUTRAL, volume, pitch);
    }

    /** Powers-down beep for a robot that has finished its job for good (the excavator reaching bedrock). */
    protected void finishedSound(ServerLevel sl) {
        CoreSounds.play(sl, worldPosition, CoreSounds.ROBOT_BEEP_LOW, SoundSource.NEUTRAL, 0.7F, 1.0F);
    }

    private void pullBattery() {
        ItemStack stack = battery.getStackInSlot(0);
        if (stack.isEmpty() || energy.getSpace() <= 0) return;
        if (energy.getEnergyStored() > energy.getMaxEnergyStored() / 4 && age % 4 != 0) return;
        if (EnergyUtil.dischargeItem(stack, energy, batteryPullRate()) > 0) setChanged();
    }

    /** Max FE pulled from the battery item per call. */
    protected int batteryPullRate() {
        return MAX_PULL;
    }

    /** Energy per tick while working, before multipliers: base * speed multiplier * (speed/efficiency energy factor). */
    protected int scaledDrain(int baseFePerTick) {
        return scaledDrain(baseFePerTick, Upgrades.speedMultiplier(upgrades.level(UpgradeKind.SPEED)));
    }

    /** Same, with the speed multiplier that is really in effect (callers whose interval is floored pass less than the card gives). */
    protected int scaledDrain(int baseFePerTick, int effectiveSpeedMultiplier) {
        int speed = upgrades.level(UpgradeKind.SPEED);
        int eff = upgrades.level(UpgradeKind.EFFICIENCY);
        double value = CoreConfig.scaleEnergy(baseFePerTick) * effectiveSpeedMultiplier * Upgrades.energyMultiplier(speed, eff);
        return (int) Math.max(baseFePerTick == 0 ? 0 : 1, Math.round(value));
    }

    // ---- output routing ----

    /** Offers a stack to adjacent inventories, then the buffer; whatever does not fit waits in the pending queue. */
    public void output(ItemStack stack) {
        if (stack.isEmpty() || !(level instanceof ServerLevel sl)) return;
        stack = stack.copy();
        int keep = keepAmount(stack);
        if (keep > 0) {
            int toKeep = Math.min(stack.getCount(), keep - countInBuffer(stack));
            if (toKeep > 0) {
                ItemStack left = ItemHandlerHelper.insertItem(buffer, stack.copyWithCount(toKeep), false);
                stack.shrink(toKeep - left.getCount());
            }
        }
        if (stack.isEmpty()) return;
        stack = insertNeighbours(sl, stack, false);
        if (stack.isEmpty()) return;
        stack = ItemHandlerHelper.insertItem(buffer, stack, false);
        if (!stack.isEmpty()) {
            pending.add(stack);
            setChanged();
        }
    }

    /** True when the whole stack would fit into neighbours plus buffer right now. */
    public boolean canAcceptFully(ItemStack stack) {
        if (!(level instanceof ServerLevel sl)) return false;
        ItemStack left = insertNeighbours(sl, stack.copy(), true);
        if (left.isEmpty()) return true;
        return ItemHandlerHelper.insertItem(buffer, left, true).isEmpty();
    }

    public boolean hasPendingOutput() {
        return !pending.isEmpty();
    }

    private int countInBuffer(ItemStack stack) {
        int n = 0;
        for (int i = 0; i < buffer.getSlots(); i++) {
            ItemStack s = buffer.getStackInSlot(i);
            if (ItemStack.isSameItemSameComponents(s, stack)) n += s.getCount();
        }
        return n;
    }

    private ItemStack insertNeighbours(ServerLevel sl, ItemStack stack, boolean simulate) {
        for (Direction dir : Direction.values()) {
            if (stack.isEmpty()) break;
            if (!sides.mode(dir).output) continue;
            IItemHandler handler = sl.getCapability(Capabilities.ItemHandler.BLOCK, worldPosition.relative(dir), dir.getOpposite());
            if (handler == null) continue;
            stack = ItemHandlerHelper.insertItem(handler, stack, simulate);
        }
        return stack;
    }

    private void flushPending(ServerLevel sl) {
        boolean changed = false;
        for (int i = 0; i < pending.size(); i++) {
            ItemStack stack = insertNeighbours(sl, pending.get(i), false);
            if (!stack.isEmpty()) stack = ItemHandlerHelper.insertItem(buffer, stack, false);
            if (stack.isEmpty()) {
                pending.remove(i--);
                changed = true;
            } else {
                pending.set(i, stack);
            }
        }
        if (changed) setChanged();
    }

    /** Moves buffer contents (except the reserved amount per item) into adjacent inventories. */
    private void flushBuffer(ServerLevel sl) {
        List<ItemStack> reserveItems = new ArrayList<>();
        List<Integer> reserveLeft = new ArrayList<>();
        for (int i = 0; i < buffer.getSlots(); i++) {
            ItemStack stack = buffer.getStackInSlot(i);
            if (stack.isEmpty()) continue;
            int reserved = 0;
            int keep = keepAmount(stack);
            if (keep > 0) {
                int idx = -1;
                for (int k = 0; k < reserveItems.size(); k++) {
                    if (ItemStack.isSameItemSameComponents(reserveItems.get(k), stack)) {
                        idx = k;
                        break;
                    }
                }
                if (idx < 0) {
                    reserveItems.add(stack.copy());
                    reserveLeft.add(keep);
                    idx = reserveItems.size() - 1;
                }
                reserved = Math.min(stack.getCount(), reserveLeft.get(idx));
                reserveLeft.set(idx, reserveLeft.get(idx) - reserved);
            }
            int pushable = stack.getCount() - reserved;
            if (pushable <= 0) continue;
            ItemStack left = insertNeighbours(sl, stack.copyWithCount(pushable), false);
            int moved = pushable - left.getCount();
            if (moved > 0) buffer.extractItem(i, moved, false);
        }
    }

    // ---- block break protection ----

    protected GameProfile fakeProfile() {
        return new GameProfile(owner != null ? owner : FALLBACK_OWNER, "[Robotica]");
    }

    /** Fires a break event as the owner's fake player so claim and protection mods can veto. */
    protected boolean mayBreak(ServerLevel sl, BlockPos pos, BlockState state) {
        try {
            FakePlayer fake = FakePlayerFactory.get(sl, fakeProfile());
            BlockEvent.BreakEvent event = new BlockEvent.BreakEvent(sl, pos, state, fake);
            NeoForge.EVENT_BUS.post(event);
            return !event.isCanceled();
        } catch (RuntimeException e) {
            return true;
        }
    }

    // ---- items on removal ----

    public void dropContents() {
        if (level == null) return;
        for (int i = 0; i < battery.getSlots(); i++) Containers.dropItemStack(level, worldPosition.getX(), worldPosition.getY(), worldPosition.getZ(), battery.getStackInSlot(i));
        for (int i = 0; i < upgrades.getSlots(); i++) Containers.dropItemStack(level, worldPosition.getX(), worldPosition.getY(), worldPosition.getZ(), upgrades.getStackInSlot(i));
        for (int i = 0; i < buffer.getSlots(); i++) Containers.dropItemStack(level, worldPosition.getX(), worldPosition.getY(), worldPosition.getZ(), buffer.getStackInSlot(i));
        for (ItemStack stack : pending) Containers.dropItemStack(level, worldPosition.getX(), worldPosition.getY(), worldPosition.getZ(), stack);
        pending.clear();
    }

    // ---- menu ----

    @Override
    public Component getDisplayName() {
        return Component.translatable(blockKey());
    }

    @Nullable
    @Override
    public AbstractContainerMenu createMenu(int id, Inventory inv, Player player) {
        return new AreaWorkerMenu(id, inv, this);
    }

    // ---- persistence ----

    @Override
    protected void saveAdditional(CompoundTag tag, HolderLookup.Provider registries) {
        super.saveAdditional(tag, registries);
        tag.put("energy", energy.serializeNBT(registries));
        tag.put("battery", battery.serializeNBT(registries));
        tag.put("upgrades", upgrades.serializeNBT(registries));
        tag.put("buffer", buffer.serializeNBT(registries));
        ListTag list = new ListTag();
        for (ItemStack stack : pending) list.add(stack.save(registries));
        tag.put("pending", list);
        if (owner != null) tag.putUUID("owner", owner);
        tag.putBoolean("showArea", showArea);
        tag.put("sides", sides.save());
        saveExtra(tag, registries);
    }

    @Override
    protected void loadAdditional(CompoundTag tag, HolderLookup.Provider registries) {
        super.loadAdditional(tag, registries);
        if (tag.contains("energy")) {
            energy.deserializeNBT(registries, tag.get("energy"));
            energy.setEnergy(energy.getEnergyStored()); // clamp to this Mk's buffer
        }
        if (tag.contains("battery")) battery.deserializeNBT(registries, tag.getCompound("battery"));
        if (tag.contains("buffer")) buffer.deserializeNBT(registries, tag.getCompound("buffer"));
        if (tag.contains("pending")) {
            pending.clear();
            for (Tag t : tag.getList("pending", Tag.TAG_COMPOUND)) {
                ItemStack.parse(registries, t).ifPresent(pending::add);
            }
        }
        if (tag.contains("upgrades")) {
            // Slot by slot, so a different slot count (next Mk, or a save from before the Mk tiers) never resizes the
            // handler; cards that no longer have a slot come out like any other output.
            ItemStackHandler saved = new ItemStackHandler();
            saved.deserializeNBT(registries, tag.getCompound("upgrades"));
            for (int i = 0; i < Math.max(upgrades.getSlots(), saved.getSlots()); i++) {
                ItemStack stack = i < saved.getSlots() ? saved.getStackInSlot(i) : ItemStack.EMPTY;
                if (i < upgrades.getSlots()) upgrades.setStackInSlot(i, stack);
                else if (!stack.isEmpty()) pending.add(stack.copy());
            }
        }
        if (tag.contains("sides")) sides.load(tag.getCompound("sides"));
        if (tag.hasUUID("owner")) owner = tag.getUUID("owner");
        if (tag.contains("showArea")) showArea = tag.getBoolean("showArea");
        if (tag.contains("previewUntil")) previewUntil = tag.getLong("previewUntil");
        if (tag.contains("areaSize")) {
            // client update tag
            areaSize = tag.getInt("areaSize");
            loadClientExtra(tag);
        } else {
            loadExtra(tag, registries);
            recalc();
        }
    }

    @Override
    protected void saveClientData(CompoundTag tag, HolderLookup.Provider registries) {
        tag.putBoolean("showArea", showArea);
        tag.putLong("previewUntil", previewUntil);
        tag.putInt("areaSize", areaSize);
        saveClientExtra(tag);
    }

    // ---- external item view ----

    private final class ExternalBuffer implements IItemHandler {
        @Override
        public int getSlots() {
            return buffer.getSlots();
        }

        @Override
        public ItemStack getStackInSlot(int slot) {
            return buffer.getStackInSlot(slot);
        }

        @Override
        public ItemStack insertItem(int slot, ItemStack stack, boolean simulate) {
            if (keepAmount(stack) <= 0) return stack;
            return buffer.insertItem(slot, stack, simulate);
        }

        @Override
        public ItemStack extractItem(int slot, int amount, boolean simulate) {
            return buffer.extractItem(slot, amount, simulate);
        }

        @Override
        public int getSlotLimit(int slot) {
            return buffer.getSlotLimit(slot);
        }

        @Override
        public boolean isItemValid(int slot, ItemStack stack) {
            return keepAmount(stack) > 0 && buffer.isItemValid(slot, stack);
        }
    }
}
