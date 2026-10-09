package com.arno.robotica.energy.block;

import com.arno.robotica.core.block.SyncedBlockEntity;
import com.arno.robotica.core.multiblock.CuboidScanner;
import com.arno.robotica.core.multiblock.CuboidSpec;
import com.arno.robotica.core.multiblock.CuboidVisitor;
import com.arno.robotica.core.multiblock.MultiblockWatcher;
import com.arno.robotica.core.multiblock.StructureProblem;
import com.arno.robotica.core.progress.Milestones;
import com.arno.robotica.energy.EnergyConfig;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.MenuProvider;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.levelgen.structure.BoundingBox;
import net.neoforged.neoforge.capabilities.Capabilities;
import net.neoforged.neoforge.energy.IEnergyStorage;
import net.neoforged.neoforge.items.IItemHandler;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import java.util.function.LongConsumer;
import java.util.function.LongSupplier;
import java.util.function.Predicate;

/**
 * Base of the energy multiblock controllers (Capacitor Bank, Core Reactor, Tesla Spire, Ring Collider). It owns the
 * structure state: it scans ({@link #scanStructure}, by default a {@link CuboidScanner} cuboid) when a block in or around the structure changed (reported by
 * {@link MultiblockWatcher}, throttled to one scan per {@code multiblockScanCooldown} ticks) and every
 * {@code multiblockRescanInterval} ticks as a safety net. On a successful scan it links the ports it found; the ports
 * forward their capabilities to {@link #portReceive} / {@link #portExtract} / {@link #portItems}.
 * Server ticking only; the GUI gets its numbers through {@link #writeSync}.
 */
public abstract class StructureControllerBlockEntity extends SyncedBlockEntity implements MenuProvider, com.arno.robotica.compat.InfoSource {
    public record Port(BlockPos pos, Direction outward, PortBlock.Kind kind) {}

    /** Collects the ports of the walls; structure visitors extend it. */
    public abstract static class Visitor implements CuboidVisitor {
        public final List<Port> ports = new ArrayList<>();

        @Override
        public StructureProblem wall(BlockPos pos, BlockState state, Direction outward) {
            if (state.getBlock() instanceof PortBlock port) ports.add(new Port(pos, outward, port.kind()));
            return null;
        }

        public long ports(PortBlock.Kind kind) {
            return ports.stream().filter(p -> p.kind() == kind).count();
        }
    }

    @Nullable
    private UUID owner;
    /** Owner's name at placement, for the team check while the owner is offline. */
    private String ownerName = "";
    private boolean formed;
    @Nullable
    private BoundingBox box;
    @Nullable
    private StructureProblem problem;
    private final List<Port> ports = new ArrayList<>();
    private final Set<BlockPos> portSet = new HashSet<>();
    private boolean dirty = true;
    private long nextScan, nextPeriodic;
    private boolean watching;
    /** The cuboid shell this controller dressed in its formed look, so the look comes off again (saved). */
    @Nullable
    private BoundingBox lookBox;

    protected StructureControllerBlockEntity(BlockEntityType<?> type, BlockPos pos, BlockState state) {
        super(type, pos, state);
    }

    // ---------------------------------------------------------------- structure

    /** Cuboid rules for the default {@link #scanStructure}; null for shapes that scan themselves. */
    @Nullable
    protected abstract CuboidSpec spec();

    /** Visitor of a cuboid scan; shapes that scan themselves may return a plain {@link Visitor}. */
    protected abstract Visitor newVisitor();

    /** After every successful scan (also re-scans of an already formed structure). */
    protected abstract void onFormed(Visitor visitor);

    /** The structure broke apart. */
    protected abstract void onUnformed();

    /** Every server tick, formed or not. */
    protected abstract void tickController(ServerLevel level, long now);

    /** Milestone awarded to the owner when the structure forms the first time, or null. */
    @Nullable
    protected String formedMilestone() {
        return null;
    }

    public boolean isFormed() {
        return formed;
    }

    @Nullable
    public BoundingBox box() {
        return box;
    }

    @Nullable
    public StructureProblem problem() {
        return problem;
    }

    public List<Port> ports() {
        return Collections.unmodifiableList(ports);
    }

    boolean hasPort(BlockPos pos) {
        return portSet.contains(pos);
    }

    public void setOwner(@Nullable UUID owner) {
        setOwner(owner, "");
    }

    public void setOwner(@Nullable UUID owner, String name) {
        this.owner = owner;
        this.ownerName = name == null ? "" : name;
        setChanged();
    }

    /**
     * Whether the player may change the controller (rods, reset, on/off): the owner, the owner's team or an operator.
     * Without an owner (placed by commands) everyone. Anyone in reach may still open the GUI and watch.
     */
    public boolean canControl(net.minecraft.world.entity.player.Player player) {
        if (player.hasPermissions(2)) return true;
        String name = ownerName;
        if (name.isEmpty() && owner != null && level instanceof ServerLevel sl) {
            String known = com.arno.robotica.compat.OwnerNames.name(sl.getServer(), owner);
            if (known != null) name = known;
        }
        return com.arno.robotica.power.util.Owners.allied(level, owner, name, player);
    }

    @Nullable
    public UUID owner() {
        return owner;
    }

    /** Jade: working while the controller glows, the owner, and {@link #infoProgress} when there is one. */
    @Override
    public void collectInfo(ServerLevel level, com.arno.robotica.compat.MachineInfo info) {
        if (owner != null) info.owner = com.arno.robotica.compat.OwnerNames.name(level.getServer(), owner);
        BlockState state = getBlockState();
        boolean lit = state.hasProperty(ControllerBlock.LIT) && state.getValue(ControllerBlock.LIT);
        info.status = isFormed() && lit ? "working" : "idle";
        if (isFormed()) {
            int progress = infoProgress();
            if (progress >= 0) info.progress = Math.min(100, progress);
        }
    }

    /** Percent for the Jade bar (bank fill, ignition charge), -1 for none. */
    protected int infoProgress() {
        return -1;
    }

    /** Something in or around the structure changed: re-scan on the next allowed tick. */
    public void markDirty() {
        dirty = true;
    }

    public void serverTick(ServerLevel level, BlockPos pos, BlockState state) {
        long now = level.getGameTime();
        if (!watching) watchArea();
        if ((dirty && now >= nextScan) || now >= nextPeriodic) scanNow();
        tickController(level, now);
    }

    /** Scans right away (tests and the first tick). */
    public void scanNow() {
        if (!(level instanceof ServerLevel serverLevel)) return;
        long now = level.getGameTime();
        dirty = false;
        nextScan = now + EnergyConfig.scanCooldown();
        nextPeriodic = now + EnergyConfig.rescanInterval();
        BlockState state = getBlockState();
        CuboidScanner.Result result = scanStructure(serverLevel);
        if (result.status() == CuboidScanner.Status.UNLOADED) {
            nextPeriodic = now + 20;
            return;
        }
        // the block state keeps FORMED over a reload, so a reactor that was already standing stays quiet on chunk load,
        // and one broken while unloaded still resets its saved run state
        boolean was = formed || (state.hasProperty(ControllerBlock.FORMED) && state.getValue(ControllerBlock.FORMED));
        if (result.formed() && result.visitor() instanceof Visitor visitor) {
            setPorts(visitor.ports);
            formed = true;
            problem = null;
            box = result.box();
            onFormed(visitor);
            if (spec() != null) {
                if (lookBox != null && !lookBox.equals(box)) dress(lookBox, false);
                dress(box, true);
                lookBox = box;
            }
            if (!was) {
                level.playSound(null, worldPosition, SoundEvents.BEACON_ACTIVATE, SoundSource.BLOCKS, 0.5F, 1.4F);
                String milestone = formedMilestone();
                if (milestone != null) Milestones.awardOwner(serverLevel, worldPosition, owner, milestone);
            }
        } else {
            if (was) {
                clearPorts();
                formed = false;
                onUnformed();
                level.playSound(null, worldPosition, SoundEvents.BEACON_DEACTIVATE, SoundSource.BLOCKS, 0.5F, 1.2F);
            }
            // a part in an unloaded chunk keeps its look: the box stays saved and the next scans take it off
            if (lookBox != null && dress(lookBox, false)) lookBox = null;
            problem = result.problem();
            box = result.box();
        }
        watchArea();
        setFormedState(formed);
        setChanged();
    }

    /**
     * Finds and checks the structure. Default: a cuboid by {@link #spec()} around the controller in a side wall. The
     * placement facing comes from where the player looked, so the two sideways directions are tried as well and the
     * controller turns to the one that forms (problems are still reported for its own facing). Shapes that are no
     * cuboid (the Tesla Spire's column, the Ring Collider's loop) override this.
     */
    protected CuboidScanner.Result scanStructure(ServerLevel level) {
        BlockState state = getBlockState();
        Direction facing = state.hasProperty(ControllerBlock.FACING) ? state.getValue(ControllerBlock.FACING) : Direction.NORTH;
        CuboidScanner.Result result = CuboidScanner.scan(level, worldPosition, facing, spec(), this::newVisitor);
        if (result.status() == CuboidScanner.Status.INVALID) {
            for (Direction side : new Direction[]{facing.getClockWise(), facing.getCounterClockWise()}) {
                CuboidScanner.Result alt = CuboidScanner.scan(level, worldPosition, side, spec(), this::newVisitor);
                if (alt.formed()) {
                    turnTowards(alt.box());
                    return alt;
                }
            }
        }
        return result;
    }

    /** Where block changes may affect an unformed structure (watched for re-scans). */
    protected BoundingBox searchArea() {
        int r = Math.max(spec().maxWidth().getAsInt(), spec().maxHeight().getAsInt());
        return new BoundingBox(worldPosition).inflatedBy(r);
    }

    /** Turns the controller's screen to the box face it sits in (outward). */
    private void turnTowards(@Nullable BoundingBox found) {
        if (found == null || level == null) return;
        Direction out = worldPosition.getX() == found.minX() ? Direction.WEST : worldPosition.getX() == found.maxX() ? Direction.EAST
                : worldPosition.getZ() == found.minZ() ? Direction.NORTH : worldPosition.getZ() == found.maxZ() ? Direction.SOUTH : null;
        BlockState state = getBlockState();
        if (out != null && state.hasProperty(ControllerBlock.FACING) && state.getValue(ControllerBlock.FACING) != out) {
            level.setBlock(worldPosition, state.setValue(ControllerBlock.FACING, out), Block.UPDATE_CLIENTS);
        }
    }

    private void watchArea() {
        if (level == null || level.isClientSide) return;
        BoundingBox area = box;
        if (area == null) area = searchArea();
        MultiblockWatcher.watch(level, worldPosition, area, this::markDirty);
        watching = true;
    }

    private void setPorts(List<Port> found) {
        Set<BlockPos> keep = new HashSet<>();
        for (Port p : found) keep.add(p.pos());
        for (Port old : ports) {
            if (!keep.contains(old.pos()) && level.isLoaded(old.pos()) && level.getBlockEntity(old.pos()) instanceof PortBlockEntity be) {
                be.unlink(worldPosition);
            }
        }
        ports.clear();
        portSet.clear();
        for (Port p : found) {
            if (level.getBlockEntity(p.pos()) instanceof PortBlockEntity be) {
                be.link(worldPosition);
                ports.add(p);
                portSet.add(p.pos());
            }
        }
    }

    private void clearPorts() {
        if (level != null) {
            for (Port p : ports) {
                if (level.isLoaded(p.pos()) && level.getBlockEntity(p.pos()) instanceof PortBlockEntity be) be.unlink(worldPosition);
            }
        }
        ports.clear();
        portSet.clear();
    }

    private void setFormedState(boolean value) {
        BlockState state = getBlockState();
        if (state.hasProperty(ControllerBlock.FORMED) && state.getValue(ControllerBlock.FORMED) != value) {
            BlockState next = state.setValue(ControllerBlock.FORMED, value);
            if (!value && next.hasProperty(ControllerBlock.LIT)) next = next.setValue(ControllerBlock.LIT, false);
            level.setBlock(worldPosition, next, Block.UPDATE_CLIENTS);
        }
    }

    /**
     * Formed look of a cuboid shell: frame beams, corner caps and wall panels on its {@link FramedPartBlock}s and
     * frameless {@link StructureGlassBlock}s, or back to the loose blocks. Sent to clients without neighbour updates, so
     * it never wakes the {@link MultiblockWatcher}. Scripts/scene_preview.py draws the same look. Returns false when a
     * part of the shell was not loaded (and so kept its state).
     */
    private boolean dress(BoundingBox shell, boolean on) {
        if (level == null) return false;
        boolean all = true;
        BlockPos.MutableBlockPos p = new BlockPos.MutableBlockPos();
        for (int x = shell.minX(); x <= shell.maxX(); x++) {
            boolean ex = x == shell.minX() || x == shell.maxX();
            for (int y = shell.minY(); y <= shell.maxY(); y++) {
                boolean ey = y == shell.minY() || y == shell.maxY();
                for (int z = shell.minZ(); z <= shell.maxZ(); z = ex || ey || z == shell.maxZ() ? z + 1 : shell.maxZ()) {
                    boolean ez = z == shell.minZ() || z == shell.maxZ();
                    p.set(x, y, z);
                    if (!level.isLoaded(p)) {
                        all = false;
                        continue;
                    }
                    BlockState state = level.getBlockState(p);
                    BlockState next = state;
                    if (state.getBlock() instanceof FramedPartBlock) {
                        next = state.setValue(FramedPartBlock.FRAME, on ? frameShape(ex, ey, ez) : FramedPartBlock.Shape.NONE);
                    } else if (state.getBlock() instanceof StructureGlassBlock) {
                        next = state.setValue(StructureGlassBlock.FORMED, on);
                    }
                    if (next != state) level.setBlock(p, next, Block.UPDATE_CLIENTS | Block.UPDATE_KNOWN_SHAPE);
                }
            }
        }
        return all;
    }

    /** Corner where three box faces meet, a beam along the one axis that is not on a face, else a wall. */
    static FramedPartBlock.Shape frameShape(boolean ex, boolean ey, boolean ez) {
        int faces = (ex ? 1 : 0) + (ey ? 1 : 0) + (ez ? 1 : 0);
        if (faces == 3) return FramedPartBlock.Shape.CORNER;
        if (faces == 2) return !ex ? FramedPartBlock.Shape.X : !ey ? FramedPartBlock.Shape.Y : FramedPartBlock.Shape.Z;
        return FramedPartBlock.Shape.WALL;
    }

    /** The controller block was broken: take the formed look off, unlink the ports and reset the structure. */
    public void onControllerRemoved() {
        if (lookBox != null) {
            dress(lookBox, false);
            lookBox = null;
        }
        if (formed) {
            formed = false;
            clearPorts();
            onUnformed();
        }
    }

    private long lastLitSound = Long.MIN_VALUE / 2;

    /** Shows the working glow on the controller (only sends an update when it changes). */
    protected void setLit(boolean lit) {
        BlockState state = getBlockState();
        if (level != null && state.hasProperty(ControllerBlock.LIT) && state.getValue(ControllerBlock.LIT) != lit) {
            level.setBlock(worldPosition, state.setValue(ControllerBlock.LIT, lit), Block.UPDATE_CLIENTS);
            // the same start / stop sounds as the other machines, at most every 2 s so a flickering load stays quiet
            long now = level.getGameTime();
            if (litSounds() && now - lastLitSound >= 40) {
                lastLitSound = now;
                com.arno.robotica.core.CoreSounds.play(level, worldPosition, lit ? com.arno.robotica.core.CoreSounds.MACHINE_START
                        : com.arno.robotica.core.CoreSounds.MACHINE_STOP, net.minecraft.sounds.SoundSource.BLOCKS, 0.7F, 0.8F);
            }
        }
    }

    /** Whether the glow turning on or off plays the machine start / stop sound (generators; the bank glows on every flow). */
    protected boolean litSounds() {
        return false;
    }

    @Override
    public void setRemoved() {
        super.setRemoved();
        if (level != null && !level.isClientSide) {
            MultiblockWatcher.unwatch(level, worldPosition);
            clearPorts();
        }
        watching = false;
    }

    // ---------------------------------------------------------------- ports (overridden per structure)

    public int portReceive(PortBlockEntity port, int amount, boolean simulate) {
        return 0;
    }

    public int portExtract(PortBlockEntity port, int amount, boolean simulate) {
        return 0;
    }

    public boolean portCanReceive(PortBlockEntity port) {
        return false;
    }

    public boolean portCanExtract(PortBlockEntity port) {
        return false;
    }

    public long portStored() {
        return 0;
    }

    public long portCapacity() {
        return 0;
    }

    @Nullable
    public IItemHandler portItems() {
        return null;
    }

    /**
     * Pushes FE out of every port {@code accept} lets through into the block outside it, through the standard FE
     * capability (cables, machines, Accumulators, other mods' blocks). At most {@code budget} in total and never more
     * than {@code available}; {@code drain} removes what was delivered. Returns the total sent.
     */
    protected long pushOut(ServerLevel level, Predicate<Port> accept, long budget, LongSupplier available, LongConsumer drain) {
        long sent = 0;
        for (Port port : ports) {
            if (!accept.test(port)) continue;
            long left = Math.min(budget - sent, available.getAsLong());
            if (left <= 0) break;
            BlockPos target = port.pos().relative(port.outward());
            if (!level.isLoaded(target)) continue;
            IEnergyStorage storage = level.getCapability(Capabilities.EnergyStorage.BLOCK, target, port.outward().getOpposite());
            if (storage == null || !storage.canReceive()) continue;
            int accepted = storage.receiveEnergy((int) Math.min(Integer.MAX_VALUE, left), false);
            if (accepted > 0) {
                drain.accept(accepted);
                sent += accepted;
            }
        }
        return sent;
    }

    // ---------------------------------------------------------------- GUI sync

    /** Numbers for the open GUI, sent a few times a second to players that have it open. */
    public void writeSync(CompoundTag tag, HolderLookup.Provider registries) {
        tag.putBoolean("formed", formed);
        if (problem != null) {
            tag.putString("problem", Component.Serializer.toJson(problem.message(), registries));
            if (problem.pos() != null) tag.putLong("problemPos", problem.pos().asLong());
        }
        if (box != null) tag.putIntArray("box", new int[]{box.minX(), box.minY(), box.minZ(), box.maxX(), box.maxY(), box.maxZ()});
        CuboidSpec spec = spec();
        if (spec != null) {
            tag.putInt("minW", spec.minWidth().getAsInt());
            tag.putInt("minH", spec.minHeight().getAsInt());
        }
        tag.putInt("ports", ports.size());
    }

    // ---------------------------------------------------------------- save

    @Override
    protected void saveAdditional(CompoundTag tag, HolderLookup.Provider registries) {
        super.saveAdditional(tag, registries);
        if (owner != null) tag.putUUID("owner", owner);
        if (!ownerName.isEmpty()) tag.putString("ownerName", ownerName);
        if (lookBox != null) {
            tag.putIntArray("look", new int[]{lookBox.minX(), lookBox.minY(), lookBox.minZ(), lookBox.maxX(), lookBox.maxY(), lookBox.maxZ()});
        }
    }

    @Override
    protected void loadAdditional(CompoundTag tag, HolderLookup.Provider registries) {
        super.loadAdditional(tag, registries);
        if (tag.hasUUID("owner")) owner = tag.getUUID("owner");
        ownerName = tag.getString("ownerName");
        int[] look = tag.getIntArray("look");
        lookBox = look.length == 6 ? new BoundingBox(look[0], look[1], look[2], look[3], look[4], look[5]) : null;
        // a controller moved with its data (a mod that carries blocks) must not strip the look off its old spot
        if (lookBox != null && !lookBox.isInside(worldPosition)) lookBox = null;
    }

    /** Called when the controller block is broken: drop what it holds. */
    public void dropContents(Level level, BlockPos pos) {
    }
}
