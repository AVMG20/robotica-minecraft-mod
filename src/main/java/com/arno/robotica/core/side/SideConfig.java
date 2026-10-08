package com.arno.robotica.core.side;

import com.arno.robotica.core.CoreConfig;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.Containers;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.neoforged.neoforge.capabilities.Capabilities;
import net.neoforged.neoforge.items.IItemHandler;
import net.neoforged.neoforge.items.ItemHandlerHelper;
import org.jetbrains.annotations.Nullable;

import java.util.function.Supplier;

/**
 * Per-face item configuration of a machine (like Thermal's side config): every face, relative to the machine's FACING,
 * is None, Input, Output or Both, plus two toggles: auto-input (pull from inventories on Input faces) and auto-eject
 * (push outputs into inventories on Output faces), both throttled by the core config.
 *
 * <p>Opting a machine in takes a few lines:
 * <pre>{@code
 * public final SideConfig sides = new SideConfig(this, () -> automationRules).with(RelativeSide.BOTTOM, SideMode.OUTPUT);
 * // capability:   (be, side) -> be.sides.access(side)
 * // server tick:  sides.tick(level)
 * // save / load:  tag.put("sides", sides.save()) / sides.load(tag.getCompound("sides"))
 * // menu:         trackSides(be == null ? null : be.sides)
 * }</pre>
 * {@code automationRules} is the machine's normal automation view (inputs insert, outputs extract). A null side (probes)
 * always gets it unchanged.
 */
public final class SideConfig {
    public static final int SIDES = RelativeSide.values().length;
    /** Menu actions: 0-5 next mode of a face, 6-11 previous mode, then the two toggles. */
    public static final int ACTION_PREVIOUS = SIDES, ACTION_AUTO_INPUT = 2 * SIDES, ACTION_AUTO_EJECT = 2 * SIDES + 1;

    @Nullable
    private final BlockEntity owner;
    @Nullable
    private final Supplier<IItemHandler> rules;
    private final SideMode[] defaults = filled();
    private final SideMode[] modes = filled();
    private boolean autoInput;
    private boolean autoEject;
    @Nullable
    private IItemHandler inputView, outputView;

    /** Server side config of a machine; every face starts as {@link SideMode#BOTH}, toggles off. */
    public SideConfig(BlockEntity owner, Supplier<IItemHandler> rules) {
        this.owner = java.util.Objects.requireNonNull(owner);
        this.rules = rules;
    }

    private SideConfig() {
        this.owner = null;
        this.rules = null;
    }

    /** Client copy for a menu, filled by the menu's data slot. */
    public static SideConfig client() {
        return new SideConfig();
    }

    private static SideMode[] filled() {
        SideMode[] all = new SideMode[SIDES];
        java.util.Arrays.fill(all, SideMode.BOTH);
        return all;
    }

    /** Sets a face's default (and current) mode. Call while building the machine. */
    public SideConfig with(RelativeSide side, SideMode mode) {
        defaults[side.ordinal()] = mode;
        modes[side.ordinal()] = mode;
        return this;
    }

    // ---------------------------------------------------------------- reading

    public SideMode mode(RelativeSide side) {
        return modes[side.ordinal()];
    }

    /** Mode of the world face {@code side}. */
    public SideMode mode(Direction side) {
        return mode(RelativeSide.of(facing(), side));
    }

    public boolean autoInput() {
        return autoInput;
    }

    public boolean autoEject() {
        return autoEject;
    }

    /** The owner's front, NORTH without a horizontal FACING (and on the client copy and for up/down facing blocks). */
    public Direction facing() {
        return owner == null ? Direction.NORTH : facing(owner.getBlockState());
    }

    public static Direction facing(BlockState state) {
        return state.getOptionalValue(BlockStateProperties.HORIZONTAL_FACING)
                .or(() -> state.getOptionalValue(BlockStateProperties.FACING).filter(d -> d.getAxis().isHorizontal()))
                .orElse(Direction.NORTH);
    }

    // ---------------------------------------------------------------- changing

    public void set(RelativeSide side, SideMode mode) {
        if (modes[side.ordinal()] == mode) return;
        modes[side.ordinal()] = mode;
        changed(true);
    }

    public void setAutoInput(boolean on) {
        if (autoInput == on) return;
        autoInput = on;
        changed(false);
    }

    public void setAutoEject(boolean on) {
        if (autoEject == on) return;
        autoEject = on;
        changed(false);
    }

    /** Applies a menu action (see {@link #ACTION_PREVIOUS}). Server side, after the caller validated the player. */
    public void handleAction(int action) {
        if (action >= 0 && action < SIDES) {
            RelativeSide side = RelativeSide.byId(action);
            set(side, mode(side).next());
        } else if (action >= ACTION_PREVIOUS && action < ACTION_PREVIOUS + SIDES) {
            RelativeSide side = RelativeSide.byId(action - ACTION_PREVIOUS);
            set(side, mode(side).previous());
        } else if (action == ACTION_AUTO_INPUT) {
            setAutoInput(!autoInput);
        } else if (action == ACTION_AUTO_EJECT) {
            setAutoEject(!autoEject);
        }
    }

    /** True for the machine's own config, false for a menu's client copy. */
    public boolean isServer() {
        return owner != null;
    }

    private void changed(boolean faces) {
        if (owner == null) return;
        owner.setChanged();
        Level level = owner.getLevel();
        // Pipes and other mods cache capabilities: tell them this face changed.
        if (faces && level != null && !level.isClientSide) level.invalidateCapabilities(owner.getBlockPos());
    }

    // ---------------------------------------------------------------- capability

    /**
     * The item handler for a face: the normal rules for Both and for a null side, insert-only for Input, extract-only
     * for Output, and null for None (nothing connects there).
     */
    @Nullable
    public IItemHandler access(@Nullable Direction side) {
        if (rules == null) return null;
        IItemHandler all = rules.get();
        if (side == null) return all;
        SideMode mode = mode(side);
        if (mode == SideMode.NONE) return null;
        if (mode == SideMode.BOTH) return all;
        if (mode == SideMode.INPUT) {
            if (inputView == null) inputView = new Filtered(rules, true, false);
            return inputView;
        }
        if (outputView == null) outputView = new Filtered(rules, false, true);
        return outputView;
    }

    // ---------------------------------------------------------------- auto transfer

    /**
     * Auto-eject and auto-input, every {@code sideTransferInterval} ticks (spread over machines by position), up to
     * {@code sideTransferItems} items each way. Only touches loaded neighbours.
     */
    public void tick(ServerLevel level) {
        if (owner == null || rules == null || (!autoInput && !autoEject)) return;
        BlockPos pos = owner.getBlockPos();
        int interval = CoreConfig.sideTransferInterval();
        if (Math.floorMod(level.getGameTime() + pos.asLong(), interval) != 0) return;
        IItemHandler machine = rules.get();
        Direction facing = facing();
        if (autoEject) {
            int budget = CoreConfig.sideTransferItems();
            for (RelativeSide rel : RelativeSide.values()) {
                if (budget <= 0) break;
                if (!mode(rel).output) continue;
                IItemHandler target = neighbour(level, pos, rel.toWorld(facing));
                if (target != null) budget -= move(level, pos, machine, target, budget);
            }
        }
        if (autoInput) {
            int budget = CoreConfig.sideTransferItems();
            for (RelativeSide rel : RelativeSide.values()) {
                if (budget <= 0) break;
                if (!mode(rel).input) continue;
                IItemHandler source = neighbour(level, pos, rel.toWorld(facing));
                if (source != null) budget -= move(level, pos, source, machine, budget);
            }
        }
    }

    @Nullable
    private static IItemHandler neighbour(ServerLevel level, BlockPos pos, Direction dir) {
        BlockPos at = pos.relative(dir);
        if (!level.isLoaded(at)) return null;
        return level.getCapability(Capabilities.ItemHandler.BLOCK, at, dir.getOpposite());
    }

    /**
     * Moves up to {@code budget} items from one handler to another, slot by slot, simulating first so nothing is
     * pulled that can not be placed. Returns how many moved.
     */
    public static int move(Level level, BlockPos pos, IItemHandler from, IItemHandler to, int budget) {
        int moved = 0;
        for (int slot = 0; slot < from.getSlots() && moved < budget; slot++) {
            ItemStack sim = from.extractItem(slot, budget - moved, true);
            if (sim.isEmpty()) continue;
            ItemStack rest = ItemHandlerHelper.insertItemStacked(to, sim, true);
            int n = sim.getCount() - rest.getCount();
            if (n <= 0) continue;
            ItemStack real = from.extractItem(slot, n, false);
            if (real.isEmpty()) continue;
            ItemStack left = ItemHandlerHelper.insertItemStacked(to, real, false);
            moved += real.getCount() - left.getCount();
            if (!left.isEmpty()) left = from.insertItem(slot, left, false);
            if (!left.isEmpty()) Containers.dropItemStack(level, pos.getX() + 0.5, pos.getY() + 1.0, pos.getZ() + 0.5, left);
        }
        return moved;
    }

    // ---------------------------------------------------------------- sync and save

    /** Everything in 14 bits (2 per face, then the toggles), so one menu data slot carries it. */
    public int pack() {
        int bits = 0;
        for (int i = 0; i < SIDES; i++) bits |= modes[i].ordinal() << (2 * i);
        if (autoInput) bits |= 1 << 12;
        if (autoEject) bits |= 1 << 13;
        return bits;
    }

    /** Client copy only. */
    public void unpack(int bits) {
        for (int i = 0; i < SIDES; i++) modes[i] = SideMode.byId((bits >> (2 * i)) & 3);
        autoInput = (bits & (1 << 12)) != 0;
        autoEject = (bits & (1 << 13)) != 0;
    }

    public CompoundTag save() {
        CompoundTag tag = new CompoundTag();
        byte[] bytes = new byte[SIDES];
        for (int i = 0; i < SIDES; i++) bytes[i] = (byte) modes[i].ordinal();
        tag.putByteArray("modes", bytes);
        tag.putBoolean("autoInput", autoInput);
        tag.putBoolean("autoEject", autoEject);
        return tag;
    }

    /** An empty tag (older saves) keeps the defaults. */
    public void load(CompoundTag tag) {
        byte[] bytes = tag.getByteArray("modes");
        for (int i = 0; i < SIDES; i++) modes[i] = bytes.length == SIDES ? SideMode.byId(bytes[i]) : defaults[i];
        autoInput = tag.getBoolean("autoInput");
        autoEject = tag.getBoolean("autoEject");
    }

    /** Insert-only or extract-only view of the machine's rules. */
    private record Filtered(Supplier<IItemHandler> inner, boolean insert, boolean extract) implements IItemHandler {
        @Override
        public int getSlots() {
            return inner.get().getSlots();
        }

        @Override
        public ItemStack getStackInSlot(int slot) {
            return inner.get().getStackInSlot(slot);
        }

        @Override
        public ItemStack insertItem(int slot, ItemStack stack, boolean simulate) {
            return insert ? inner.get().insertItem(slot, stack, simulate) : stack;
        }

        @Override
        public ItemStack extractItem(int slot, int amount, boolean simulate) {
            return extract ? inner.get().extractItem(slot, amount, simulate) : ItemStack.EMPTY;
        }

        @Override
        public int getSlotLimit(int slot) {
            return inner.get().getSlotLimit(slot);
        }

        @Override
        public boolean isItemValid(int slot, ItemStack stack) {
            return insert && inner.get().isItemValid(slot, stack);
        }
    }
}
