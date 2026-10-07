package com.arno.robotica.replicator.block;

import com.arno.robotica.core.CoreConfig;
import com.arno.robotica.core.CoreSounds;
import com.arno.robotica.core.block.SyncedBlockEntity;
import com.arno.robotica.core.energy.MachineEnergyStorage;
import com.arno.robotica.core.item.CoreItems;
import com.arno.robotica.core.upgrade.UpgradeCardItem;
import com.arno.robotica.core.upgrade.UpgradeKind;
import com.arno.robotica.core.upgrade.UpgradeRules;
import com.arno.robotica.core.upgrade.Upgrades;
import com.arno.robotica.replicator.ReplicatorConfig;
import com.arno.robotica.replicator.ReplicatorRegistry;
import com.arno.robotica.replicator.logic.Essence;
import com.arno.robotica.replicator.logic.Harvest;
import com.arno.robotica.replicator.logic.ReplicatorStructure;
import com.arno.robotica.replicator.menu.ReplicatorMenu;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.HolderLookup;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.Containers;
import net.minecraft.world.Difficulty;
import net.minecraft.world.MenuProvider;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.ExperienceOrb;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.MobCategory;
import net.minecraft.world.entity.MobSpawnType;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.AABB;
import net.neoforged.neoforge.items.IItemHandler;
import net.neoforged.neoforge.items.ItemHandlerHelper;
import net.neoforged.neoforge.items.ItemStackHandler;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.UUID;

/**
 * Brain of the Mob Replicator. One vial slot, one boost slot, one catalyst slot, three upgrade slots, an 18 slot output and a 1M FE buffer.
 * Every cycle it either rolls the vial mob's loot table into the output (Harvest) or spawns the mob (Spawn).
 * Ticks on the server only.
 */
public class ReplicatorControllerBlockEntity extends SyncedBlockEntity implements MenuProvider {
    public static final int OUTPUT_SLOTS = 18;
    public static final int UPGRADE_SLOTS = UpgradeRules.Fixed.REPLICATOR.slots;
    /** Extra FE cost of the plasma boost, on top of the speed it gives. */
    private static final double BOOST_ENERGY_FACTOR = 1.25;
    private static final int MAX_XP = 1_000_000;
    private static final int VALIDATE_INTERVAL = 40;

    public enum Mode {
        HARVEST, SPAWN;

        public Mode next() {
            return values()[(ordinal() + 1) % values().length];
        }

        public static Mode byId(int id) {
            return id >= 0 && id < values().length ? values()[id] : HARVEST;
        }
    }

    /** Why the machine is not progressing right now (shown in the GUI). */
    public enum Pause {
        NONE, NOT_FORMED, NO_VIAL, NO_ENERGY, OUTPUT_FULL, SPAWN_CAP, NO_SPACE, PEACEFUL, SPAWN_DISABLED, NEEDS_MAGMA_CORE, NEEDS_ANTIGRAV_CORE;

        public static Pause byId(int id) {
            return id >= 0 && id < values().length ? values()[id] : NONE;
        }
    }

    // ---- inventories (static factories so the client menu can build identical dummies) ----

    public static ItemStackHandler newVialHandler(Runnable onChanged) {
        return new ItemStackHandler(1) {
            @Override
            public boolean isItemValid(int slot, ItemStack stack) {
                return Essence.isUsable(stack);
            }

            @Override
            public int getSlotLimit(int slot) {
                return 1;
            }

            @Override
            protected void onContentsChanged(int slot) {
                onChanged.run();
            }
        };
    }

    public static ItemStackHandler newBoostHandler(Runnable onChanged) {
        return new ItemStackHandler(1) {
            @Override
            public boolean isItemValid(int slot, ItemStack stack) {
                return stack.is(CoreItems.PLASMA_ACTUATOR.get());
            }

            @Override
            public int getSlotLimit(int slot) {
                return 1;
            }

            @Override
            protected void onContentsChanged(int slot) {
                onChanged.run();
            }
        };
    }

    /** Catalyst slot: a Magma Core (Age 3 mobs) or Antigrav Core (Age 4 mobs). Never consumed. */
    public static ItemStackHandler newCatalystHandler(Runnable onChanged) {
        return new ItemStackHandler(1) {
            @Override
            public boolean isItemValid(int slot, ItemStack stack) {
                return stack.is(CoreItems.MAGMA_CORE.get()) || stack.is(CoreItems.ANTIGRAV_CORE.get());
            }

            @Override
            public int getSlotLimit(int slot) {
                return 1;
            }

            @Override
            protected void onContentsChanged(int slot) {
                onChanged.run();
            }
        };
    }

    /** Caps in {@link UpgradeRules.Fixed#REPLICATOR}: speed 3 (x4, with the plasma boost x8), fortune 3 (Looting III). */
    public static Upgrades newUpgrades(Runnable onChanged) {
        return Upgrades.fixed(UpgradeRules.Fixed.REPLICATOR, onChanged);
    }

    public final ItemStackHandler vial = newVialHandler(this::setChangedAndSync);
    public final ItemStackHandler boost = newBoostHandler(this::setChanged);
    public final ItemStackHandler catalyst = newCatalystHandler(this::setChanged);
    public final Upgrades upgrades = newUpgrades(this::setChanged);
    public final ItemStackHandler output = new ItemStackHandler(OUTPUT_SLOTS) {
        @Override
        protected void onContentsChanged(int slot) {
            setChanged();
        }
    };
    public final MachineEnergyStorage energy = new MachineEnergyStorage(ReplicatorConfig.energyBuffer(), ReplicatorConfig.maxReceive(), 0, this::setChanged);

    private final IItemHandler automation = new OutputAccess(output);

    // ---- state ----
    @Nullable
    private UUID owner;
    private Mode mode = Mode.HARVEST;
    private int progress;
    private int needed = 1;
    private int cycles;
    private int xpStored;
    private final List<ItemStack> pending = new ArrayList<>();
    private ReplicatorStructure.Status status = ReplicatorStructure.Status.INCOMPLETE;
    private Pause pause = Pause.NOT_FORMED;
    private boolean revalidate = true;
    private int age;
    private int lastCycleSound = -20;
    @Nullable
    private EntityType<?> activeType;

    // client only mirror (see saveClientData)
    @Nullable
    private ResourceLocation clientVial;
    private int clientSeenCycles = -1;
    private long flashStart = Long.MIN_VALUE / 2;

    public ReplicatorControllerBlockEntity(BlockPos pos, BlockState state) {
        super(ReplicatorRegistry.CONTROLLER_BE.get(), pos, state);
    }

    // ---- accessors ----

    public IItemHandler automation() {
        return automation;
    }

    public MachineEnergyStorage energy() {
        return energy;
    }

    @Nullable
    public UUID owner() {
        return owner;
    }

    public void setOwner(@Nullable UUID owner) {
        this.owner = owner;
        setChanged();
    }

    public Mode mode() {
        return mode;
    }

    public void setMode(Mode mode) {
        if (this.mode == mode) return;
        this.mode = mode;
        setChangedAndSync();
    }

    public int progress() {
        return progress;
    }

    public int needed() {
        return needed;
    }

    public int cycles() {
        return cycles;
    }

    public int xpStored() {
        return xpStored;
    }

    public ReplicatorStructure.Status status() {
        return status;
    }

    public Pause pause() {
        return pause;
    }

    public boolean isFormed() {
        return status.formed();
    }

    public int pendingCount() {
        return pending.size();
    }

    /** Entity type of the vial in the slot, null when empty or unusable. */
    @Nullable
    public EntityType<?> boundType() {
        ItemStack stack = vial.getStackInSlot(0);
        return Essence.isUsable(stack) ? Essence.type(stack) : null;
    }

    /** Looting level 0-3 from the fortune card. */
    public int lootingLevel() {
        return Upgrades.fortuneEnchantLevel(upgrades.level(UpgradeKind.FORTUNE));
    }

    public int speedLevel() {
        return upgrades.level(UpgradeKind.SPEED);
    }

    /** Tier of the installed catalyst core: 0 none, 3 Magma Core, 4 Antigrav Core. */
    public int catalystTier() {
        ItemStack stack = catalyst.getStackInSlot(0);
        if (stack.is(CoreItems.ANTIGRAV_CORE.get())) return 4;
        if (stack.is(CoreItems.MAGMA_CORE.get())) return 3;
        return 0;
    }

    public boolean hasBoost() {
        return boost.getStackInSlot(0).is(CoreItems.PLASMA_ACTUATOR.get());
    }

    /** Total speed multiplier from speed card and plasma boost. */
    public int speedMultiplier() {
        return Upgrades.speedMultiplier(speedLevel()) * (hasBoost() ? ReplicatorConfig.boostMultiplier() : 1);
    }

    /** Ticks one cycle takes with the current cards. */
    public int cycleTicks() {
        return Math.max(1, CoreConfig.scaleInterval(ReplicatorConfig.cycleTicks()) / speedMultiplier());
    }

    /** FE per tick while working with the current cards. */
    public int energyPerTick() {
        int base = CoreConfig.scaleEnergy(ReplicatorConfig.energyPerTick());
        if (base == 0) return 0;
        double factor = Upgrades.energyMultiplier(speedLevel(), upgrades.level(UpgradeKind.EFFICIENCY)) * (hasBoost() ? BOOST_ENERGY_FACTOR : 1.0);
        return (int) Math.max(1, Math.round(base * speedMultiplier() * factor));
    }

    // ---- client mirror ----

    @Nullable
    public ResourceLocation clientVialType() {
        return clientVial;
    }

    /** Game time of the last finished cycle seen by this client (for the flash). */
    public long flashStart() {
        return flashStart;
    }

    public int clientCycles() {
        return cycles;
    }

    // ---- validation ----

    /** Ask for a structure check on the next tick (called on neighbour changes). */
    public void requestValidation() {
        revalidate = true;
    }

    /** Checks the 3x3x3 now and updates the blockstate. Server side. */
    public void validate(ServerLevel level, BlockPos pos, BlockState state) {
        revalidate = false;
        ReplicatorStructure.Status now = ReplicatorStructure.validate(level, pos, state.getValue(ReplicatorControllerBlock.FACING));
        if (now == ReplicatorStructure.Status.UNLOADED) return;
        boolean wasFormed = status.formed();
        status = now;
        if (wasFormed != now.formed()) {
            CoreSounds.play(level, pos, now.formed() ? CoreSounds.REPLICATOR_FORM : CoreSounds.REPLICATOR_UNFORM, SoundSource.BLOCKS, 1.0F, 1.0F);
            if (now.formed()) com.arno.robotica.core.progress.Milestones.awardOwner(level, pos, owner, com.arno.robotica.core.progress.Milestones.REPLICATOR_FORMED);
        }
        if (state.getValue(ReplicatorControllerBlock.FORMED) != now.formed()) {
            BlockState next = state.setValue(ReplicatorControllerBlock.FORMED, now.formed());
            if (!now.formed()) next = next.setValue(ReplicatorControllerBlock.LIT, false);
            level.setBlock(pos, next, Block.UPDATE_CLIENTS);
        }
    }

    // ---- server tick ----

    public void serverTick(ServerLevel level, BlockPos pos, BlockState state) {
        age++;
        if (revalidate || age % VALIDATE_INTERVAL == 0) {
            validate(level, pos, state);
            state = getBlockState();
        }
        needed = cycleTicks();

        boolean working = false;
        Pause nextPause = Pause.NONE;
        EntityType<?> type = boundType();
        if (!status.formed()) {
            nextPause = Pause.NOT_FORMED;
        } else if (type == null) {
            nextPause = Pause.NO_VIAL;
            activeType = null;
            progress = 0;
        } else {
            if (type != activeType) {
                activeType = type;
                progress = 0;
            }
            int required = Essence.requiredTier(type);
            if (required > catalystTier()) {
                nextPause = required >= 4 ? Pause.NEEDS_ANTIGRAV_CORE : Pause.NEEDS_MAGMA_CORE;
            } else if (!pending.isEmpty() && !flushPending()) {
                nextPause = Pause.OUTPUT_FULL;
            } else if (progress >= needed) {
                // Harvest always finishes at once. Spawn mode may be waiting for room, so it retries once a second.
                if (mode == Mode.HARVEST || age % 20 == 0) {
                    Pause blocked = complete(level, pos, state, type);
                    nextPause = blocked;
                    if (blocked == Pause.NONE) progress = 0;
                } else {
                    nextPause = pause == Pause.NONE ? Pause.SPAWN_CAP : pause;
                }
            } else if (energy.consume(energyPerTick())) {
                working = true;
                progress++;
                if (progress >= needed) {
                    Pause blocked = complete(level, pos, state, type);
                    if (blocked == Pause.NONE) progress = 0;
                    else nextPause = blocked;
                }
            } else {
                nextPause = Pause.NO_ENERGY;
            }
        }
        pause = nextPause;
        setWorking(level, pos, state, working);
        if (working && age % 80 == 0) {
            CoreSounds.play(level, pos, CoreSounds.REPLICATOR_HUM, SoundSource.BLOCKS, 0.8F, 1.0F);
        }
    }

    private void setWorking(ServerLevel level, BlockPos pos, BlockState state, boolean working) {
        if (state.getValue(ReplicatorControllerBlock.LIT) != working) {
            level.setBlock(pos, state.setValue(ReplicatorControllerBlock.LIT, working), Block.UPDATE_CLIENTS);
        }
    }

    /** Finishes a cycle. Returns NONE on success, otherwise why it has to wait. */
    private Pause complete(ServerLevel level, BlockPos pos, BlockState state, EntityType<?> type) {
        if (mode == Mode.SPAWN) {
            Pause blocked = spawn(level, pos, state, type);
            if (blocked != Pause.NONE) return blocked;
            cycleSound(level, pos, CoreSounds.REPLICATOR_SPAWN);
        } else {
            Harvest.Result result = Harvest.roll(level, type, pos.relative(state.getValue(ReplicatorControllerBlock.FACING).getOpposite()),
                    owner, lootingLevel());
            if (result != null) {
                for (ItemStack drop : result.drops()) {
                    ItemStack rest = ItemHandlerHelper.insertItemStacked(output, drop, false);
                    if (!rest.isEmpty()) pending.add(rest);
                }
                if (ReplicatorConfig.harvestXp()) xpStored = Math.min(MAX_XP, xpStored + result.xp());
            }
            cycleSound(level, pos, CoreSounds.REPLICATOR_CYCLE);
        }
        cycles++;
        setChangedAndSync();
        return Pause.NONE;
    }

    /** Cycle sound, at most once a second even when speed upgrades finish cycles faster. */
    private void cycleSound(ServerLevel level, BlockPos pos, java.util.function.Supplier<net.minecraft.sounds.SoundEvent> sound) {
        if (age - lastCycleSound < 20) return;
        lastCycleSound = age;
        CoreSounds.play(level, pos, sound, SoundSource.BLOCKS, 0.9F, 1.0F);
    }

    /** Moves leftovers into the output. True when nothing is left. */
    private boolean flushPending() {
        boolean changed = false;
        for (int i = pending.size() - 1; i >= 0; i--) {
            ItemStack rest = ItemHandlerHelper.insertItemStacked(output, pending.get(i), false);
            if (rest.getCount() != pending.get(i).getCount()) changed = true;
            if (rest.isEmpty()) pending.remove(i);
            else pending.set(i, rest);
        }
        if (changed) setChanged();
        return pending.isEmpty();
    }

    // ---- spawn mode ----

    private Pause spawn(ServerLevel level, BlockPos pos, BlockState state, EntityType<?> type) {
        if (!ReplicatorConfig.allowSpawnMode()) return Pause.SPAWN_DISABLED;
        if (level.getDifficulty() == Difficulty.PEACEFUL && type.getCategory() == MobCategory.MONSTER) return Pause.PEACEFUL;

        BlockPos center = ReplicatorStructure.center(pos, state.getValue(ReplicatorControllerBlock.FACING));
        AABB box = new AABB(pos).inflate(ReplicatorConfig.spawnRadius());
        List<Mob> nearby = level.getEntitiesOfClass(Mob.class, box);
        int same = 0;
        for (Mob mob : nearby) {
            if (mob.getType() == type) same++;
        }
        if (same >= ReplicatorConfig.spawnMaxSameType() || nearby.size() >= ReplicatorConfig.spawnMaxTotal()) return Pause.SPAWN_CAP;

        Direction facing = state.getValue(ReplicatorControllerBlock.FACING);
        BlockPos front = pos.relative(facing);
        for (BlockPos candidate : new BlockPos[]{front, front.above(), center.above(2)}) {
            if (!level.noCollision(type.getSpawnAABB(candidate.getX() + 0.5, candidate.getY(), candidate.getZ() + 0.5))) continue;
            if (!level.getFluidState(candidate).isEmpty()) continue;
            Entity entity = type.create(level, null, candidate, MobSpawnType.SPAWNER, false, false);
            if (entity == null) return Pause.NO_SPACE;
            level.addFreshEntityWithPassengers(entity);
            level.gameEvent(null, net.minecraft.world.level.gameevent.GameEvent.ENTITY_PLACE, candidate);
            return Pause.NONE;
        }
        return Pause.NO_SPACE;
    }

    // ---- xp ----

    /** Hands the stored experience to a player as an orb at their feet. Called when the GUI opens or the block breaks. */
    public void releaseXp(Level level, net.minecraft.world.phys.Vec3 at) {
        if (xpStored > 0 && level instanceof ServerLevel serverLevel) {
            ExperienceOrb.award(serverLevel, at, xpStored);
            xpStored = 0;
            setChanged();
        }
    }

    // ---- drops ----

    public void dropContents(Level level, BlockPos pos) {
        drop(level, pos, vial);
        drop(level, pos, boost);
        drop(level, pos, catalyst);
        drop(level, pos, upgrades);
        drop(level, pos, output);
        for (ItemStack stack : pending) {
            Containers.dropItemStack(level, pos.getX(), pos.getY(), pos.getZ(), stack);
        }
        pending.clear();
        releaseXp(level, net.minecraft.world.phys.Vec3.atCenterOf(pos));
    }

    private static void drop(Level level, BlockPos pos, IItemHandler handler) {
        for (int i = 0; i < handler.getSlots(); i++) {
            ItemStack stack = handler.getStackInSlot(i);
            if (!stack.isEmpty()) Containers.dropItemStack(level, pos.getX(), pos.getY(), pos.getZ(), stack.copy());
        }
    }

    // ---- menu ----

    @Override
    public Component getDisplayName() {
        return Component.translatable("block.robotica.replicator_controller");
    }

    @Nullable
    @Override
    public AbstractContainerMenu createMenu(int id, Inventory inv, Player player) {
        return new ReplicatorMenu(id, inv, this);
    }

    // ---- persistence ----

    @Override
    protected void saveAdditional(CompoundTag tag, HolderLookup.Provider registries) {
        super.saveAdditional(tag, registries);
        tag.put("vial", vial.serializeNBT(registries));
        tag.put("boost", boost.serializeNBT(registries));
        tag.put("catalyst", catalyst.serializeNBT(registries));
        tag.put("upgrades", upgrades.serializeNBT(registries));
        tag.put("output", output.serializeNBT(registries));
        tag.put("energy", energy.serializeNBT(registries));
        tag.putInt("progress", progress);
        tag.putInt("cycles", cycles);
        tag.putInt("xp", xpStored);
        tag.putInt("mode", mode.ordinal());
        if (owner != null) tag.putUUID("owner", owner);
        ListTag list = new ListTag();
        for (ItemStack stack : pending) list.add(stack.save(registries));
        tag.put("pending", list);
    }

    @Override
    protected void loadAdditional(CompoundTag tag, HolderLookup.Provider registries) {
        super.loadAdditional(tag, registries);
        if (tag.contains("vial")) vial.deserializeNBT(registries, tag.getCompound("vial"));
        if (tag.contains("boost")) boost.deserializeNBT(registries, tag.getCompound("boost"));
        if (tag.contains("catalyst")) catalyst.deserializeNBT(registries, tag.getCompound("catalyst"));
        if (tag.contains("upgrades")) upgrades.deserializeNBT(registries, tag.getCompound("upgrades"));
        if (tag.contains("output")) {
            output.deserializeNBT(registries, tag.getCompound("output"));
            // Saved by an older config with more or fewer slots: keep the handler at 18.
            if (output.getSlots() != OUTPUT_SLOTS) output.setSize(OUTPUT_SLOTS);
        }
        if (tag.contains("energy")) energy.deserializeNBT(registries, tag.get("energy"));
        if (tag.contains("progress")) progress = tag.getInt("progress");
        // activeType is transient: restore it from the vial, or the first tick after a reload would reset the progress.
        if (tag.contains("vial")) activeType = boundType();
        if (tag.contains("xp")) xpStored = tag.getInt("xp");
        if (tag.contains("mode")) mode = Mode.byId(tag.getInt("mode"));
        if (tag.hasUUID("owner")) owner = tag.getUUID("owner");
        if (tag.contains("pending", Tag.TAG_LIST)) {
            pending.clear();
            for (Tag entry : tag.getList("pending", Tag.TAG_COMPOUND)) {
                ItemStack.parse(registries, entry).ifPresent(stack -> {
                    if (!stack.isEmpty()) pending.add(stack);
                });
            }
        }
        if (tag.contains("cycles")) {
            int c = tag.getInt("cycles");
            if (level != null && level.isClientSide && clientSeenCycles >= 0 && c != clientSeenCycles) {
                flashStart = level.getGameTime();
            }
            clientSeenCycles = c;
            cycles = c;
        }
        if (tag.contains("clientVial")) {
            String id = tag.getString("clientVial");
            clientVial = id.isEmpty() ? null : ResourceLocation.tryParse(id);
        }
        revalidate = true;
    }

    @Override
    protected void saveClientData(CompoundTag tag, HolderLookup.Provider registries) {
        EntityType<?> type = boundType();
        tag.putString("clientVial", type == null ? "" : BuiltInRegistries.ENTITY_TYPE.getKey(type).toString());
        tag.putInt("cycles", cycles);
        tag.putInt("mode", mode.ordinal());
    }

    /** Extract-only view for hoppers, pipes and storage networks: they can empty the output but never insert. */
    private static final class OutputAccess implements IItemHandler {
        private final IItemHandler inner;

        OutputAccess(IItemHandler inner) {
            this.inner = inner;
        }

        @Override
        public int getSlots() {
            return inner.getSlots();
        }

        @Override
        public ItemStack getStackInSlot(int slot) {
            return inner.getStackInSlot(slot);
        }

        @Override
        public ItemStack insertItem(int slot, ItemStack stack, boolean simulate) {
            return stack;
        }

        @Override
        public ItemStack extractItem(int slot, int amount, boolean simulate) {
            return inner.extractItem(slot, amount, simulate);
        }

        @Override
        public int getSlotLimit(int slot) {
            return inner.getSlotLimit(slot);
        }

        @Override
        public boolean isItemValid(int slot, ItemStack stack) {
            return false;
        }
    }
}
