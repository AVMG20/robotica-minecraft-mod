package com.arno.robotica.drones.entity;

import com.arno.robotica.Robotica;
import com.arno.robotica.core.CoreConfig;
import com.arno.robotica.core.CoreSounds;
import com.arno.robotica.drones.DronesConfig;
import com.arno.robotica.drones.DronesRegistry;
import com.arno.robotica.drones.menu.MiningDroneMenu;
import com.mojang.authlib.GameProfile;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.HolderLookup;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.core.registries.Registries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.tags.FluidTags;
import net.minecraft.tags.TagKey;
import net.minecraft.world.Containers;
import net.minecraft.world.SimpleMenuProvider;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.ai.attributes.AttributeSupplier;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.FallingBlock;
import net.minecraft.world.level.block.LiquidBlock;
import net.minecraft.world.level.block.WallTorchBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.material.FluidState;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.common.util.FakePlayer;
import net.neoforged.neoforge.common.util.FakePlayerFactory;
import net.neoforged.neoforge.event.level.BlockEvent;
import net.neoforged.neoforge.items.ItemHandlerHelper;
import net.neoforged.neoforge.items.ItemStackHandler;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/**
 * Mining Drone: digs a 3x3 tunnel in a straight line, collects the drops, lights the way with torches, bridges floor gaps
 * with cobblestone and flies back to its owner when it is done, when lava or water shows up, when it is full or low on
 * energy. It breaks blocks through a fake player of its owner so protection and claim mods can veto, never touches block
 * entities or unbreakable blocks, and never loads chunks: an unloaded slice ends the job.
 */
public class MiningDrone extends DroneBase {
    public static final TagKey<Item> VOIDABLE = TagKey.create(Registries.ITEM, Robotica.id("voidable"));
    /** Tunnel lengths the GUI cycles through (each one capped by the config maximum). */
    public static final int[] LENGTHS = {16, 32, 64, 128};
    public static final int STORAGE_MK1 = 18;
    public static final int STORAGE_MK2 = 27;
    private static final UUID FALLBACK_OWNER = UUID.fromString("5a0b6c52-3f2b-4a39-9d5e-0d6f5f0e7a11");

    /** What the drone is doing. Ids are stored and synced, never reorder. */
    public enum Mode {
        STAY, FOLLOW, TUNNEL, RETURN;

        public static Mode byId(int id) {
            Mode[] all = values();
            return all[Math.floorMod(id, all.length)];
        }
    }

    /** Why a tunnel ended. The lang key is {@code message.robotica.mining.<lowercase name>}. */
    public enum Stop {
        DONE, LAVA, WATER, FULL, ENERGY, UNLOADED, BLOCKED, EDGE, NOTHING;

        public static Stop byId(int id) {
            Stop[] all = values();
            return id >= 0 && id < all.length ? all[id] : null;
        }

        public String key() {
            return name().toLowerCase(java.util.Locale.ROOT);
        }
    }

    /** Tunnel slice cells in dig order: the drone's own line first, then up and down, then the sides. */
    private static final int[][] CELLS = {{0, 0}, {0, 1}, {0, -1}, {1, 0}, {-1, 0}, {1, 1}, {-1, 1}, {1, -1}, {-1, -1}};

    private static final int PHASE_MOVE = 0;
    private static final int PHASE_CHECK = 1;
    private static final int PHASE_MINE = 2;
    private static final int PHASE_FINISH = 3;

    /** One running tunnel job, saved with the entity so it resumes when the chunk loads again. */
    static final class Tunnel {
        Direction dir;
        BlockPos feet;
        int length;
        int k = 1;
        int counted;
        int cell;
        int phase = PHASE_MOVE;
        int wait;
        int recheck;
        int moveTicks;
        boolean started;
        int skipped;

        Tunnel(Direction dir, BlockPos feet, int length) {
            this.dir = dir;
            this.feet = feet;
            this.length = length;
        }

        CompoundTag save() {
            CompoundTag t = new CompoundTag();
            t.putInt("Dir", dir.get2DDataValue());
            t.putInt("X", feet.getX());
            t.putInt("Y", feet.getY());
            t.putInt("Z", feet.getZ());
            t.putInt("Length", length);
            t.putInt("K", k);
            t.putInt("Counted", counted);
            t.putInt("Cell", cell);
            t.putInt("Phase", phase);
            t.putInt("Recheck", recheck);
            t.putBoolean("Started", started);
            t.putInt("Skipped", skipped);
            return t;
        }

        static Tunnel load(CompoundTag t) {
            Tunnel tunnel = new Tunnel(Direction.from2DDataValue(t.getInt("Dir")), new BlockPos(t.getInt("X"), t.getInt("Y"), t.getInt("Z")), t.getInt("Length"));
            tunnel.k = Math.max(1, t.getInt("K"));
            tunnel.counted = t.getInt("Counted");
            tunnel.cell = t.getInt("Cell");
            tunnel.phase = t.getInt("Phase");
            tunnel.recheck = t.getInt("Recheck");
            tunnel.started = t.getBoolean("Started");
            tunnel.skipped = t.getInt("Skipped");
            return tunnel;
        }
    }

    /** Drops and the cobblestone reserve. Slots above the tier's size accept nothing. */
    public final ItemStackHandler storage = new ItemStackHandler(STORAGE_MK2) {
        @Override
        public boolean isItemValid(int slot, ItemStack stack) {
            return slot < storageSlots();
        }
    };

    /** Torches the drone places along the tunnel. */
    public final ItemStackHandler torch = new ItemStackHandler(1) {
        @Override
        public boolean isItemValid(int slot, ItemStack stack) {
            return isTorch(stack);
        }
    };

    private Direction heading = Direction.NORTH;
    private int tunnelLength = DronesConfig.tunnelDefaultLength();
    private boolean voidOn;
    @Nullable
    private Tunnel tunnel;
    @Nullable
    private Stop lastStop;
    private int lastStopBlocks;
    private int minedTotal;
    private int drillTicks;
    private ItemStack tool = ItemStack.EMPTY;
    /** Game tests set this to dig faster (0 = config value). */
    public int digTicksOverride;

    public MiningDrone(EntityType<? extends MiningDrone> type, Level level) {
        super(type, level);
    }

    public static AttributeSupplier.Builder createAttributes() {
        return createBaseAttributes();
    }

    @Override
    public DronesRegistry.Kind kind() {
        return DronesRegistry.Kind.MINING;
    }

    @Override
    public Item baseItem() {
        return tier() >= 2 ? DronesRegistry.MINING_DRONE_MK2.get() : DronesRegistry.MINING_DRONE.get();
    }

    @Override
    protected int maxHealthFor(int tier) {
        return DronesConfig.miningHealth(tier);
    }

    @Override
    public int energyCapacityFor(int tier) {
        return DronesConfig.miningBuffer(tier);
    }

    public int storageSlots() {
        return tier() >= 2 ? STORAGE_MK2 : STORAGE_MK1;
    }

    public static boolean isTorch(ItemStack stack) {
        return stack.is(Items.TORCH) || stack.is(Items.SOUL_TORCH);
    }

    // ---------------------------------------------------------------- state access

    public Mode mode() {
        return Mode.byId(entityData.get(DATA_MODE));
    }

    private void setMode(Mode mode) {
        entityData.set(DATA_MODE, mode.ordinal());
        if (mode != Mode.TUNNEL) tunnel = null;
        if (mode != Mode.TUNNEL) setActive(false);
    }

    public Direction heading() {
        return heading;
    }

    public int tunnelLength() {
        return tunnelLength;
    }

    public boolean voidOn() {
        return voidOn;
    }

    public int progress() {
        return tunnel != null ? tunnel.counted : 0;
    }

    public int jobLength() {
        return tunnel != null ? tunnel.length : tunnelLength;
    }

    @Nullable
    public Stop lastStop() {
        return lastStop;
    }

    public int lastStopBlocks() {
        return lastStopBlocks;
    }

    public int minedTotal() {
        return minedTotal;
    }

    public boolean hasTunnel() {
        return tunnel != null;
    }

    @Override
    public void onDeployed(@Nullable Player placer, BlockPos pos) {
        if (placer != null) heading = placer.getDirection();
        setMode(Mode.STAY);
    }

    // ---------------------------------------------------------------- commands (server)

    /** Cycles the tunnel length through 16, 32, 64, 128 (capped by the config maximum). */
    public void cycleLength() {
        int max = DronesConfig.tunnelMaxLength();
        int idx = 0;
        for (int i = 0; i < LENGTHS.length; i++) {
            if (LENGTHS[i] <= tunnelLength) idx = i;
        }
        for (int n = 1; n <= LENGTHS.length; n++) {
            int next = LENGTHS[(idx + n) % LENGTHS.length];
            if (next <= max) {
                tunnelLength = next;
                return;
            }
        }
        tunnelLength = Math.min(tunnelLength, max);
    }

    public void toggleVoid() {
        voidOn = !voidOn;
    }

    /** Turns the drill a quarter turn clockwise (only while the drone is not digging). */
    public void turn() {
        if (mode() != Mode.TUNNEL) heading = heading.getClockWise();
    }

    public void setHeading(Direction dir) {
        if (dir.getAxis().isHorizontal() && mode() != Mode.TUNNEL) heading = dir;
    }

    public void commandStay() {
        setMode(Mode.STAY);
    }

    public void commandFollow() {
        setMode(Mode.FOLLOW);
    }

    public void commandReturn() {
        if (mode() == Mode.TUNNEL) {
            say(msg("mining.abort"), false);
        }
        setMode(Mode.RETURN);
    }

    /** Tunnel along {@code dir} (null: the stored heading) starting at the commander's feet. Returns false when it cannot start. */
    public boolean commandTunnel(@Nullable ServerPlayer commander, @Nullable Direction dir) {
        if (mode() == Mode.TUNNEL) return false;
        if (dir != null && dir.getAxis().isHorizontal()) heading = dir;
        BlockPos feet = commander != null && commander.level() == level() ? commander.blockPosition() : blockPosition().below();
        return startTunnel(heading, Math.min(tunnelLength, DronesConfig.tunnelMaxLength()), feet);
    }

    /** Starts a job: the slices run from {@code feet} (the owner's feet) in direction {@code dir}, 3 wide and 3 high from foot level. */
    public boolean startTunnel(Direction dir, int length, BlockPos feet) {
        if (isEnergyLow() || getEnergy() + batteryReserve() < feeBlock() * 9) {
            say(msg("mining.energy"), false);
            CoreSounds.play(this, CoreSounds.ROBOT_ERROR, SoundSource.NEUTRAL, 0.7F, 1.0F);
            return false;
        }
        if (freeSlots() == 0) {
            say(msg("mining.full"), false);
            CoreSounds.play(this, CoreSounds.ROBOT_ERROR, SoundSource.NEUTRAL, 0.7F, 1.0F);
            return false;
        }
        heading = dir;
        tunnel = new Tunnel(dir, feet.immutable(), Math.max(1, length));
        entityData.set(DATA_MODE, Mode.TUNNEL.ordinal());
        lastStop = null;
        say(msg("mining.start", tunnel.length), false);
        CoreSounds.play(this, CoreSounds.ROBOT_BEEP, SoundSource.NEUTRAL, 0.8F, 1.0F);
        return true;
    }

    private int batteryReserve() {
        ItemStack cell = battery.getStackInSlot(0);
        return cell.isEmpty() ? 0 : com.arno.robotica.core.energy.ItemEnergy.get(cell);
    }

    // ---------------------------------------------------------------- inventory

    public int freeSlots() {
        int free = 0;
        for (int i = 0; i < storageSlots(); i++) {
            if (storage.getStackInSlot(i).isEmpty()) free++;
        }
        return free;
    }

    public int storedItems() {
        int n = 0;
        for (int i = 0; i < storageSlots(); i++) n += storage.getStackInSlot(i).getCount();
        return n;
    }

    public int count(Item item) {
        int n = 0;
        for (int i = 0; i < storageSlots(); i++) {
            ItemStack s = storage.getStackInSlot(i);
            if (s.is(item)) n += s.getCount();
        }
        return n;
    }

    private boolean takeOne(Item item) {
        for (int i = 0; i < storageSlots(); i++) {
            ItemStack s = storage.getStackInSlot(i);
            if (s.is(item)) {
                storage.extractItem(i, 1, false);
                return true;
            }
        }
        return false;
    }

    /** Stores a stack; what does not fit is dropped as an item so nothing is lost. */
    public void collect(ItemStack drop) {
        if (drop.isEmpty()) return;
        if (voidOn && drop.is(VOIDABLE) && !(drop.is(Items.COBBLESTONE) && count(Items.COBBLESTONE) < 64)) return;
        ItemStack rest = ItemHandlerHelper.insertItem(storage, drop.copy(), false);
        if (!rest.isEmpty()) Containers.dropItemStack(level(), getX(), getY(), getZ(), rest);
    }

    // ---------------------------------------------------------------- tick

    @Override
    protected void droneTick(ServerLevel sl) {
        if (drillTicks > 0 && --drillTicks == 0) setActive(false);
        Mode mode = mode();
        switch (mode) {
            case TUNNEL -> tickTunnel(sl);
            case FOLLOW -> tickFollow(sl);
            case RETURN -> tickReturn(sl);
            case STAY -> faceYaw(heading.toYRot());
        }
    }

    private void tickFollow(ServerLevel sl) {
        faceFree();
        ServerPlayer o = ownerHere();
        if (o == null) return;
        if (isHeld()) {
            navigation.stop();
        } else {
            Vec3 target = followPoint(o, 1.6, 0.9, 1.7);
            chase(target, 1.2, 32.0);
        }
        if (tickCount % 4 == 0) pickUpNearbyItems(sl);
    }

    /** Follow mode: sweeps up item drops lying around (never what a player threw). */
    private void pickUpNearbyItems(ServerLevel sl) {
        if (freeSlots() == 0 && !hasRoomFor(ItemStack.EMPTY)) return;
        List<ItemEntity> items = sl.getEntitiesOfClass(ItemEntity.class, getBoundingBox().inflate(3.0, 2.0, 3.0),
                ie -> ie.isAlive() && !ie.hasPickUpDelay() && !(ie.getOwner() instanceof Player));
        for (ItemEntity item : items) {
            ItemStack stack = item.getItem();
            ItemStack rest = ItemHandlerHelper.insertItem(storage, stack.copy(), false);
            int taken = stack.getCount() - rest.getCount();
            if (taken <= 0) continue;
            if (rest.isEmpty()) item.discard();
            else item.setItem(rest);
            if (tickCount % 8 == 0) playSound(SoundEvents.ITEM_PICKUP, 0.2F, 1.4F);
        }
    }

    private boolean hasRoomFor(ItemStack stack) {
        for (int i = 0; i < storageSlots(); i++) {
            ItemStack s = storage.getStackInSlot(i);
            if (s.isEmpty() || (s.getCount() < s.getMaxStackSize() && (stack.isEmpty() || ItemStack.isSameItemSameComponents(s, stack)))) return true;
        }
        return false;
    }

    private void tickReturn(ServerLevel sl) {
        faceFree();
        ServerPlayer o = ownerHere();
        if (o == null) {
            setMode(Mode.STAY);
            return;
        }
        Vec3 target = o.position().add(0, 1.9, 0).add(o.getLookAngle().multiply(1, 0, 1).scale(0.8));
        chase(target, 1.5, 96.0);
        if (position().distanceTo(o.position().add(0, 1.2, 0)) < 3.0) {
            int items = deliver(o);
            if (items > 0) {
                say(msg("mining.delivered", items), false);
                CoreSounds.play(this, CoreSounds.ROBOT_BEEP, SoundSource.NEUTRAL, 0.7F, 1.3F);
            }
            setMode(Mode.FOLLOW);
        }
    }

    /** Hands every mined item to the player (inventory first, the rest at their feet). Returns the item count. */
    public int deliver(ServerPlayer player) {
        int total = 0;
        for (int i = 0; i < storage.getSlots(); i++) {
            ItemStack stack = storage.getStackInSlot(i);
            if (stack.isEmpty()) continue;
            storage.setStackInSlot(i, ItemStack.EMPTY);
            total += stack.getCount();
            if (!player.getInventory().add(stack) && !stack.isEmpty()) {
                Containers.dropItemStack(level(), player.getX(), player.getY(), player.getZ(), stack);
            }
        }
        return total;
    }

    // ---------------------------------------------------------------- tunnel

    private int feeBlock() {
        return CoreConfig.scaleEnergy(DronesConfig.miningFePerBlock());
    }

    private int digInterval() {
        int base = digTicksOverride > 0 ? digTicksOverride : CoreConfig.scaleInterval(DronesConfig.miningDigTicks());
        if (digTicksOverride > 0) return Math.max(1, base);
        return Math.max(1, (int) Math.round(base / (tier() >= 2 ? DronesConfig.mk2Speed() : 1.0)));
    }

    private BlockPos axis(Tunnel t, int k) {
        return t.feet.above().relative(t.dir, k);
    }

    private BlockPos cell(Tunnel t, int k, int lat, int vert) {
        return axis(t, k).relative(t.dir.getClockWise(), lat).offset(0, vert, 0);
    }

    private Vec3 hoverPoint(Tunnel t) {
        BlockPos a = axis(t, Math.max(0, t.k - 1));
        return new Vec3(a.getX() + 0.5, a.getY() + 0.5 - getBbHeight() * 0.5, a.getZ() + 0.5);
    }

    private void tickTunnel(ServerLevel sl) {
        Tunnel t = tunnel;
        if (t == null) {
            setMode(Mode.STAY);
            return;
        }
        faceYaw(t.dir.toYRot());
        Vec3 hover = hoverPoint(t);
        if (t.phase != PHASE_MOVE) flyDirect(hover, 1.0);
        if (tickCount % 20 == 0) CoreSounds.play(this, CoreSounds.DRILL_GRIND, SoundSource.NEUTRAL, 0.35F, 1.0F);
        if (tickCount % 12 == 0) {
            Vec3 lamp = position().add(Vec3.atLowerCornerOf(t.dir.getNormal()).scale(0.5)).add(0, 0.35, 0);
            sl.sendParticles(ParticleTypes.END_ROD, lamp.x, lamp.y, lamp.z, 1, 0.02, 0.02, 0.02, 0.0);
        }
        if (t.wait > 0) {
            t.wait--;
            return;
        }
        switch (t.phase) {
            case PHASE_MOVE -> {
                t.moveTicks++;
                flyDirect(hover, 1.3);
                boolean there = position().distanceTo(hover) < 0.6;
                if (!there && t.moveTicks > 60) {
                    teleportNear(hover);
                    there = position().distanceTo(hover) < 1.5;
                }
                if (there || t.moveTicks > 120) {
                    t.moveTicks = 0;
                    t.phase = PHASE_CHECK;
                }
            }
            case PHASE_CHECK -> checkSlice(sl, t);
            case PHASE_MINE -> mineSlice(sl, t);
            case PHASE_FINISH -> finishSlice(sl, t);
            default -> t.phase = PHASE_MOVE;
        }
    }

    /** Start of a slice: end conditions, chunk and world checks, fluids. */
    private void checkSlice(ServerLevel sl, Tunnel t) {
        if (t.counted >= t.length) {
            stopTunnel(Stop.DONE);
            return;
        }
        if (!t.started) {
            // Open air before the rock: skip it without counting, up to a limit.
            int skipped = 0;
            while (!t.started && skipped <= 24) {
                if (!sliceLoaded(sl, t, t.k)) {
                    stopTunnel(Stop.UNLOADED);
                    return;
                }
                if (!sliceAllAir(sl, t, t.k)) {
                    t.started = true;
                    break;
                }
                skipped++;
                t.k++;
            }
            if (!t.started) {
                stopTunnel(Stop.NOTHING);
                return;
            }
            // The skipped air is flown through first.
            if (skipped > 0) {
                t.phase = PHASE_MOVE;
                t.moveTicks = 0;
                return;
            }
        }
        if (!sliceLoaded(sl, t, t.k)) {
            stopTunnel(Stop.UNLOADED);
            return;
        }
        if (!sliceInBounds(sl, t, t.k)) {
            stopTunnel(Stop.EDGE);
            return;
        }
        // Lava or water in the slice, around it or right in front of it: plug it with cobblestone and go home.
        List<BlockPos> fluids = new ArrayList<>();
        boolean lava = false;
        for (BlockPos p : hazardArea(t, t.k)) {
            FluidState fs = sl.getFluidState(p);
            if (!fs.isEmpty()) {
                fluids.add(p);
                if (fs.is(FluidTags.LAVA)) lava = true;
            }
        }
        if (!fluids.isEmpty()) {
            for (BlockPos p : fluids) {
                if (sl.getBlockState(p).getBlock() instanceof LiquidBlock && takeOne(Items.COBBLESTONE)) {
                    sl.setBlock(p, Blocks.COBBLESTONE.defaultBlockState(), Block.UPDATE_ALL);
                }
            }
            playSound(SoundEvents.LAVA_EXTINGUISH, 0.4F, 1.2F);
            stopTunnel(lava ? Stop.LAVA : Stop.WATER);
            return;
        }
        if (isEnergyLow() || getEnergy() + batteryReserve() < feeBlock()) {
            stopTunnel(Stop.ENERGY);
            return;
        }
        if (freeSlots() == 0) {
            stopTunnel(Stop.FULL);
            return;
        }
        t.cell = 0;
        t.phase = PHASE_MINE;
    }

    /** Positions whose fluids matter for a slice: its 3x3, the ring around it and the 3x3 of the next slice. */
    private List<BlockPos> hazardArea(Tunnel t, int k) {
        List<BlockPos> list = new ArrayList<>(50);
        for (int lat = -2; lat <= 2; lat++) {
            for (int vert = -2; vert <= 2; vert++) list.add(cell(t, k, lat, vert));
        }
        for (int lat = -1; lat <= 1; lat++) {
            for (int vert = -1; vert <= 1; vert++) list.add(cell(t, k + 1, lat, vert));
        }
        return list;
    }

    private boolean sliceLoaded(ServerLevel sl, Tunnel t, int k) {
        for (BlockPos p : hazardArea(t, k)) {
            if (!sl.isLoaded(p)) return false;
        }
        return true;
    }

    private boolean sliceInBounds(ServerLevel sl, Tunnel t, int k) {
        for (int lat = -1; lat <= 1; lat++) {
            for (int vert = -1; vert <= 1; vert++) {
                BlockPos p = cell(t, k, lat, vert);
                if (!sl.getWorldBorder().isWithinBounds(p) || !sl.isInWorldBounds(p) || p.getY() <= sl.getMinBuildHeight() + 1) return false;
            }
        }
        return true;
    }

    private boolean sliceAllAir(ServerLevel sl, Tunnel t, int k) {
        for (int[] c : CELLS) {
            if (!sl.getBlockState(cell(t, k, c[0], c[1])).isAir()) return false;
        }
        return true;
    }

    private enum CellKind {
        AIR, MINE, SKIP
    }

    private CellKind classify(ServerLevel sl, BlockPos pos, BlockState state) {
        if (state.isAir()) return CellKind.AIR;
        if (state.getBlock() instanceof LiquidBlock) return CellKind.SKIP;
        if (state.getDestroySpeed(sl, pos) < 0 || state.hasBlockEntity() || sl.getBlockEntity(pos) != null) return CellKind.SKIP;
        if (state.requiresCorrectToolForDrops() && !tool().isCorrectToolForDrops(state)) return CellKind.SKIP;
        return mayBreak(sl, pos, state) ? CellKind.MINE : CellKind.SKIP;
    }

    private ItemStack tool() {
        if (tool.isEmpty() || tool.is(Items.DIAMOND_PICKAXE) != tier() >= 2) {
            tool = new ItemStack(tier() >= 2 ? Items.DIAMOND_PICKAXE : Items.IRON_PICKAXE);
        }
        return tool;
    }

    /** Fires a break event as the owner's fake player so claim and protection mods can veto. */
    private boolean mayBreak(ServerLevel sl, BlockPos pos, BlockState state) {
        if (!sl.getWorldBorder().isWithinBounds(pos) || !sl.isInWorldBounds(pos)) return false;
        try {
            FakePlayer fake = FakePlayerFactory.get(sl, new GameProfile(owner != null ? owner : FALLBACK_OWNER, "[Robotica]"));
            if (sl.getServer().isUnderSpawnProtection(sl, pos, fake)) return false;
            BlockEvent.BreakEvent event = new BlockEvent.BreakEvent(sl, pos, state, fake);
            NeoForge.EVENT_BUS.post(event);
            return !event.isCanceled();
        } catch (RuntimeException e) {
            return true;
        }
    }

    private void mineSlice(ServerLevel sl, Tunnel t) {
        while (t.cell < CELLS.length) {
            int[] c = CELLS[t.cell];
            BlockPos pos = cell(t, t.k, c[0], c[1]);
            BlockState state = sl.getBlockState(pos);
            CellKind kind = classify(sl, pos, state);
            if (kind == CellKind.AIR) {
                t.cell++;
                continue;
            }
            if (kind == CellKind.SKIP) {
                t.skipped++;
                if (t.cell == 0) {
                    // The drone's own line is blocked (unbreakable, protected, a machine): it cannot go on.
                    stopTunnel(Stop.BLOCKED);
                    return;
                }
                t.cell++;
                continue;
            }
            if (freeSlots() == 0) {
                stopTunnel(Stop.FULL);
                return;
            }
            if (!consume(feeBlock())) {
                stopTunnel(Stop.ENERGY);
                return;
            }
            dig(sl, pos, state);
            t.cell++;
            t.wait = digInterval() - 1;
            return;
        }
        t.phase = PHASE_FINISH;
    }

    private void dig(ServerLevel sl, BlockPos pos, BlockState state) {
        FakePlayer fake = FakePlayerFactory.get(sl, new GameProfile(owner != null ? owner : FALLBACK_OWNER, "[Robotica]"));
        List<ItemStack> drops = Block.getDrops(state, sl, pos, null, fake, tool());
        minedTotal++;
        if (minedTotal % 2 == 0) sl.levelEvent(2001, pos, Block.getId(state));
        sl.setBlock(pos, Blocks.AIR.defaultBlockState(), Block.UPDATE_ALL);
        state.spawnAfterBreak(sl, pos, tool(), true);
        for (ItemStack drop : drops) collect(drop);
        drillTicks = 8;
        setActive(true);
        sl.sendParticles(ParticleTypes.ELECTRIC_SPARK, pos.getX() + 0.5, pos.getY() + 0.5, pos.getZ() + 0.5, 2, 0.3, 0.3, 0.3, 0.05);
        if (minedTotal % 4 == 0) CoreSounds.play(sl, pos, CoreSounds.EXCAVATOR_DIG, SoundSource.NEUTRAL, 0.5F, 0.9F + random.nextFloat() * 0.2F);
    }

    /** End of a slice: wait for falling blocks, bridge the floor, place a torch, step forward. */
    private void finishSlice(ServerLevel sl, Tunnel t) {
        if (t.recheck < 3 && hasFallingAbove(sl, t)) {
            t.recheck++;
            t.cell = 0;
            t.wait = 12;
            t.phase = PHASE_MINE;
            return;
        }
        t.recheck = 0;
        fillFloor(sl, t);
        int spacing = DronesConfig.torchSpacing();
        if (t.counted % spacing == spacing / 2) placeTorch(sl, t, (t.counted / spacing) % 2 == 0);
        t.counted++;
        t.k++;
        t.phase = PHASE_MOVE;
        t.moveTicks = 0;
    }

    private boolean hasFallingAbove(ServerLevel sl, Tunnel t) {
        for (int lat = -1; lat <= 1; lat++) {
            if (sl.getBlockState(cell(t, t.k, lat, 2)).getBlock() instanceof FallingBlock) return true;
        }
        return false;
    }

    /** Gaps in the floor of the finished slice are bridged with cobblestone the drone mined, so the tunnel can be walked. */
    private void fillFloor(ServerLevel sl, Tunnel t) {
        for (int lat = -1; lat <= 1; lat++) {
            BlockPos p = cell(t, t.k, lat, -2);
            if (!sl.isLoaded(p)) continue;
            BlockState s = sl.getBlockState(p);
            if (s.isAir() && count(Items.COBBLESTONE) > 0 && takeOne(Items.COBBLESTONE)) {
                sl.setBlock(p, Blocks.COBBLESTONE.defaultBlockState(), Block.UPDATE_ALL);
            }
        }
    }

    private void placeTorch(ServerLevel sl, Tunnel t, boolean left) {
        ItemStack torchStack = torch.getStackInSlot(0);
        if (torchStack.isEmpty() || !isTorch(torchStack)) return;
        Direction side = left ? t.dir.getCounterClockWise() : t.dir.getClockWise();
        BlockPos spot = cell(t, t.k, left ? -1 : 1, 0);
        BlockPos wall = cell(t, t.k, left ? -2 : 2, 0);
        if (!sl.getBlockState(spot).isAir()) return;
        boolean soul = torchStack.is(Items.SOUL_TORCH);
        BlockState placed = null;
        if (sl.getBlockState(wall).isFaceSturdy(sl, wall, side.getOpposite())) {
            BlockState wallTorch = (soul ? Blocks.SOUL_WALL_TORCH : Blocks.WALL_TORCH).defaultBlockState().setValue(WallTorchBlock.FACING, side.getOpposite());
            if (wallTorch.canSurvive(sl, spot)) placed = wallTorch;
        }
        if (placed == null) {
            BlockPos floor = spot.below();
            if (sl.getBlockState(floor).isFaceSturdy(sl, floor, Direction.UP)) {
                BlockState standing = (soul ? Blocks.SOUL_TORCH : Blocks.TORCH).defaultBlockState();
                if (standing.canSurvive(sl, spot)) placed = standing;
            }
        }
        if (placed == null) return;
        sl.setBlock(spot, placed, Block.UPDATE_ALL);
        torch.extractItem(0, 1, false);
        playSound(SoundEvents.WOOD_PLACE, 0.6F, 1.0F);
    }

    /** Ends the job: the reason is stored, told to the owner, and the drone flies home (when the owner is around). */
    public void stopTunnel(Stop reason) {
        int blocks = tunnel != null ? tunnel.counted : 0;
        lastStop = reason;
        lastStopBlocks = blocks;
        tunnel = null;
        setActive(false);
        boolean ownerHere = ownerHere() != null;
        entityData.set(DATA_MODE, (ownerHere ? Mode.RETURN : Mode.STAY).ordinal());
        Component text = reason == Stop.DONE ? msg("mining.done", blocks) : msg("mining." + reason.key());
        say(text, true);
        CoreSounds.play(this, reason == Stop.DONE ? CoreSounds.ROBOT_BEEP : CoreSounds.ROBOT_ERROR, SoundSource.NEUTRAL, 0.8F, 1.0F);
    }

    // ---------------------------------------------------------------- gui

    @Override
    protected void openGui(ServerPlayer player) {
        player.openMenu(new SimpleMenuProvider((id, inv, p) -> new MiningDroneMenu(id, inv, this), getDisplayName()),
                buf -> buf.writeVarInt(getId()));
    }

    // ---------------------------------------------------------------- persistence

    @Override
    protected void writeSettings(CompoundTag tag, HolderLookup.Provider registries) {
        tag.put("Storage", storage.serializeNBT(registries));
        tag.put("Torch", torch.serializeNBT(registries));
        tag.put("Battery", battery.serializeNBT(registries));
        tag.putInt("Heading", heading.get2DDataValue());
        tag.putInt("TunnelLength", tunnelLength);
        tag.putBoolean("Void", voidOn);
    }

    @Override
    protected void readSettings(CompoundTag tag, HolderLookup.Provider registries) {
        if (tag.contains("Storage")) storage.deserializeNBT(registries, tag.getCompound("Storage"));
        if (tag.contains("Torch")) torch.deserializeNBT(registries, tag.getCompound("Torch"));
        if (tag.contains("Battery")) battery.deserializeNBT(registries, tag.getCompound("Battery"));
        if (tag.contains("Heading")) heading = Direction.from2DDataValue(tag.getInt("Heading"));
        if (tag.contains("TunnelLength")) tunnelLength = Math.max(1, Math.min(tag.getInt("TunnelLength"), DronesConfig.tunnelMaxLength()));
        voidOn = tag.getBoolean("Void");
    }

    @Override
    protected void writeRuntime(CompoundTag tag) {
        tag.putInt("Mode", mode().ordinal());
        if (tunnel != null) tag.put("Tunnel", tunnel.save());
        if (lastStop != null) {
            tag.putInt("LastStop", lastStop.ordinal());
            tag.putInt("LastStopBlocks", lastStopBlocks);
        }
    }

    @Override
    protected void readRuntime(CompoundTag tag) {
        Mode mode = Mode.byId(tag.getInt("Mode"));
        tunnel = tag.contains("Tunnel") ? Tunnel.load(tag.getCompound("Tunnel")) : null;
        if (mode == Mode.TUNNEL && tunnel == null) mode = Mode.STAY;
        entityData.set(DATA_MODE, mode.ordinal());
        lastStop = tag.contains("LastStop") ? Stop.byId(tag.getInt("LastStop")) : null;
        lastStopBlocks = tag.getInt("LastStopBlocks");
    }

    @Override
    protected void onPickedUp() {
        tunnel = null;
    }
}
