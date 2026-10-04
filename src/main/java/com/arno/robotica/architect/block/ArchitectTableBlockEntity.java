package com.arno.robotica.architect.block;

import com.arno.robotica.architect.ArchitectConfig;
import com.arno.robotica.architect.ArchitectRegistry;
import com.arno.robotica.architect.entity.BuilderDrone;
import com.arno.robotica.architect.matter.Matter;
import com.arno.robotica.architect.matter.MatterTable;
import com.arno.robotica.architect.menu.ArchitectMenu;
import com.arno.robotica.architect.plan.BlockOp;
import com.arno.robotica.architect.plan.BuildJob;
import com.arno.robotica.architect.plan.ModuleType;
import com.arno.robotica.architect.plan.PlotRecord;
import com.arno.robotica.architect.plan.Plots;
import com.arno.robotica.architect.style.BuildStyle;
import com.arno.robotica.core.CoreConfig;
import com.arno.robotica.core.energy.MachineEnergyStorage;
import com.arno.robotica.core.item.CoreItems;
import com.arno.robotica.core.upgrade.UpgradeKind;
import com.arno.robotica.core.upgrade.Upgrades;
import com.mojang.authlib.GameProfile;
import net.minecraft.core.BlockPos;
import net.minecraft.core.HolderLookup;
import net.minecraft.core.component.DataComponentMap;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.Containers;
import net.minecraft.world.MenuProvider;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.LiquidBlock;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.scores.Team;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.common.util.BlockSnapshot;
import net.neoforged.neoforge.common.util.FakePlayer;
import net.neoforged.neoforge.common.util.FakePlayerFactory;
import net.neoforged.neoforge.event.level.BlockEvent;
import net.neoforged.neoforge.items.IItemHandler;
import net.neoforged.neoforge.items.ItemStackHandler;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.UUID;

/**
 * Architect Table. Bulk items in the 27-slot input become matter, matter + FE become Robotica building blocks while the
 * table builds its queue, one block per interval. The queue, the plot records and the build cursor are saved, so
 * builds resume after a restart. Only the owner (or a player on the owner's scoreboard team) can use it.
 */
public class ArchitectTableBlockEntity extends BlockEntity implements MenuProvider {
    public static final int INPUT_SLOTS = 27;

    public static final int ST_IDLE = 0, ST_BUILDING = 1, ST_NO_RUSTIC = 2, ST_NO_REFINED = 3, ST_NO_EXOTIC = 4,
            ST_NO_ENERGY = 5, ST_LOCKED = 6, ST_UNLOADED = 7;

    // GUI actions (ArchitectActionPayload)
    public static final int ACTION_QUEUE = 0, ACTION_CANCEL = 1, ACTION_FORGET = 2, ACTION_CLEAR = 3, ACTION_STYLE = 4;

    private static final UUID FALLBACK_OWNER = UUID.fromString("c4d8a5e2-1f43-4a5e-9d7b-0a7a0b0b0a11");

    public final ItemStackHandler input = new ItemStackHandler(INPUT_SLOTS) {
        @Override
        public boolean isItemValid(int slot, ItemStack stack) {
            return MatterTable.hasValue(stack);
        }

        @Override
        protected void onContentsChanged(int slot) {
            setChanged();
        }
    };
    public final ItemStackHandler styleSlot = new ItemStackHandler(1) {
        @Override
        public boolean isItemValid(int slot, ItemStack stack) {
            return casingLevel(stack) > 0;
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
    public final Upgrades upgrades = new Upgrades(2, Set.of(UpgradeKind.SPEED, UpgradeKind.EFFICIENCY), this::setChanged);
    public final MachineEnergyStorage energy = new MachineEnergyStorage(ArchitectConfig.energyBuffer(), ArchitectConfig.energyReceive(), 0, this::setChanged);
    private final IItemHandler automation = new InputOnlyHandler(input);

    private int rustic;
    private int refined;
    private int exotic;
    @Nullable
    private UUID owner;
    private String ownerName = "";
    private BuildStyle selectedStyle = BuildStyle.TIMBERFRAME;
    private boolean clearTerrain;

    private final PlotRecord[] plots = new PlotRecord[Plots.COUNT];
    private final List<BuildJob> queue = new ArrayList<>();

    // transient
    private int status = ST_IDLE;
    private double budget;
    private int placedCount;
    @Nullable
    private BuildJob opsJob;
    private List<BlockOp> ops = List.of();
    @Nullable
    private BuilderDrone drone;

    public ArchitectTableBlockEntity(BlockPos pos, BlockState state) {
        super(ArchitectRegistry.ARCHITECT_TABLE_BE.get(), pos, state);
    }

    public IItemHandler automation() {
        return automation;
    }

    // ---------------------------------------------------------------- ownership

    public void setOwner(Player player) {
        owner = player.getUUID();
        ownerName = player.getGameProfile().getName();
        setChanged();
    }

    /** An ownerless table (placed by a dispenser or a mod) belongs to the first player who opens it. */
    public void claimIfFree(Player player) {
        if (owner == null) setOwner(player);
    }

    public String ownerName() {
        return ownerName.isEmpty() ? "?" : ownerName;
    }

    /** Owner, a player on the owner's scoreboard team, or an operator. */
    public boolean canUse(Player player) {
        if (owner == null || owner.equals(player.getUUID()) || player.hasPermissions(2)) return true;
        Team mine = player.getTeam();
        if (mine == null || level == null) return false;
        Team theirs = level.getScoreboard().getPlayersTeam(ownerName);
        return theirs != null && mine.isAlliedTo(theirs);
    }

    // ---------------------------------------------------------------- matter

    public int matter(Matter.Grade grade) {
        return switch (grade) {
            case RUSTIC -> rustic;
            case REFINED -> refined;
            case EXOTIC -> exotic;
        };
    }

    public Matter matter() {
        return new Matter(rustic, refined, exotic);
    }

    public void setMatter(Matter m) {
        rustic = m.rustic();
        refined = m.refined();
        exotic = m.exotic();
        setChanged();
    }

    public void addRustic(int amount) {
        rustic = Math.min(ArchitectConfig.matterCap(), rustic + Math.max(0, amount));
    }

    /** Turns input slot content into matter, a few slots per call. */
    private void convertMatter() {
        int cap = ArchitectConfig.matterCap();
        int budgetSlots = 4;
        for (int slot = 0; slot < INPUT_SLOTS && budgetSlots > 0; slot++) {
            ItemStack stack = input.getStackInSlot(slot);
            if (stack.isEmpty()) continue;
            Matter v = MatterTable.valueOf(stack);
            if (v.isZero()) continue;
            int fit = stack.getCount();
            if (v.rustic() > 0) fit = Math.min(fit, (cap - rustic) / v.rustic());
            if (v.refined() > 0) fit = Math.min(fit, (cap - refined) / v.refined());
            if (v.exotic() > 0) fit = Math.min(fit, (cap - exotic) / v.exotic());
            if (fit <= 0) continue;
            input.extractItem(slot, fit, false);
            rustic += v.rustic() * fit;
            refined += v.refined() * fit;
            exotic += v.exotic() * fit;
            budgetSlots--;
            setChanged();
        }
    }

    // ---------------------------------------------------------------- styles

    /** Casing level of a style slot item: 1 iron, 2 reinforced, 3 blazing, 4 null casing, 0 anything else. */
    public static int casingLevel(ItemStack stack) {
        if (stack.is(CoreItems.IRON_CASING.get())) return 1;
        if (stack.is(CoreItems.REINFORCED_CASING.get())) return 2;
        if (stack.is(CoreItems.BLAZING_CASING.get())) return 3;
        if (stack.is(CoreItems.NULL_CASING.get())) return 4;
        return 0;
    }

    public boolean unlocked(BuildStyle style) {
        return style.casingLevel <= casingLevel(styleSlot.getStackInSlot(0));
    }

    public int unlockedMask() {
        int mask = 0;
        for (BuildStyle s : BuildStyle.values()) if (unlocked(s)) mask |= 1 << s.ordinal();
        return mask;
    }

    public BuildStyle selectedStyle() {
        return unlocked(selectedStyle) ? selectedStyle : BuildStyle.TIMBERFRAME;
    }

    public boolean clearTerrain() {
        return clearTerrain;
    }

    public int status() {
        return status;
    }

    public boolean isBuilding() {
        return status == ST_BUILDING;
    }

    // ---------------------------------------------------------------- GUI state words

    public int progressPermille() {
        if (queue.isEmpty()) return 0;
        BuildJob job = queue.get(0);
        if (!job.started) return 0;
        PlotRecord rec = plots[job.plot];
        if (rec == null) return 0;
        int size = opsFor(job, rec).size();
        return size <= 0 ? 1000 : Math.min(1000, job.cursor * 1000 / size);
    }

    public int queueSize() {
        return queue.size();
    }

    /** Flags: bit 0 clear terrain, bits 1-2 selected style, bits 3-6 unlocked styles. */
    public int flags() {
        return (clearTerrain ? 1 : 0) | (selectedStyle().ordinal() << 1) | (unlockedMask() << 3);
    }

    /** Four plots per word, 8 bits each: module id (4 bits, 0 = empty), status 0-2 (2 bits), style (2 bits). */
    public int gridWord(int word) {
        int w = 0;
        for (int i = 0; i < 4; i++) {
            int plot = word * 4 + i;
            if (plot >= Plots.COUNT || plots[plot] == null) continue;
            PlotRecord r = plots[plot];
            int cell = r.module.id() | ((r.status - 1) << 4) | (r.style.ordinal() << 6);
            w |= (cell & 0xFF) << (8 * i);
        }
        return w;
    }

    /** Two queue entries per word, 16 bits each: plot (5), module (4), patch (1), valid (1), style (2). */
    public int queueWord(int word) {
        int w = 0;
        for (int i = 0; i < 2; i++) {
            int index = word * 2 + i;
            if (index >= queue.size()) continue;
            BuildJob j = queue.get(index);
            int entry = j.plot | (j.module.id() << 5) | ((j.patch ? 1 : 0) << 9) | (1 << 10) | (j.style.ordinal() << 11);
            w |= (entry & 0xFFFF) << (16 * i);
        }
        return w;
    }

    // ---------------------------------------------------------------- actions (called by the validated payload)

    /** Applies a GUI action from a player who passed {@link #canUse}. Returns a feedback message or null. */
    @Nullable
    public Component handleAction(Player player, int action, int a, int b) {
        switch (action) {
            case ACTION_QUEUE -> {
                return queueModule(a, ModuleType.byId(b));
            }
            case ACTION_CANCEL -> {
                if (a < 0) {
                    while (!queue.isEmpty()) cancel(0);
                } else if (a < queue.size()) {
                    cancel(a);
                }
            }
            case ACTION_FORGET -> {
                if (Plots.valid(a) && plots[a] != null && plots[a].status == PlotRecord.BUILT) {
                    plots[a] = null;
                    setChanged();
                }
            }
            case ACTION_CLEAR -> {
                clearTerrain = a != 0 && ArchitectConfig.allowClearTerrain();
                setChanged();
            }
            case ACTION_STYLE -> {
                if (a >= 0 && a < BuildStyle.values().length && unlocked(BuildStyle.values()[a])) {
                    selectedStyle = BuildStyle.values()[a];
                    setChanged();
                }
            }
            default -> {
            }
        }
        return null;
    }

    @Nullable
    private Component queueModule(int plot, @Nullable ModuleType module) {
        if (module == null || !Plots.valid(plot)) return Component.translatable("message.robotica.architect_bad_request");
        if (plots[plot] != null) return Component.translatable("message.robotica.architect_plot_taken");
        if (queue.size() >= ArchitectConfig.maxQueue()) return Component.translatable("message.robotica.architect_queue_full");
        BuildStyle style = selectedStyle();
        if (!unlocked(style)) return Component.translatable("message.robotica.architect_style_locked");
        BlockPos origin = Plots.origin(worldPosition, plot);
        if (level == null || origin.getY() < level.getMinBuildHeight() || origin.getY() + Plots.HEIGHT > level.getMaxBuildHeight()) {
            return Component.translatable("message.robotica.architect_out_of_world");
        }
        plots[plot] = new PlotRecord(module, style, PlotRecord.QUEUED, 0);
        queue.add(new BuildJob(plot, module, style, clearTerrain && ArchitectConfig.allowClearTerrain(), false));
        // Neighbours that are already being built or finished need a doorway toward the new module.
        for (int side = 0; side < 4; side++) {
            int n = Plots.neighbour(plot, side);
            if (n < 0 || plots[n] == null) continue;
            PlotRecord rec = plots[n];
            if (rec.status == PlotRecord.QUEUED) continue;
            int towardNew = Plots.bit(Plots.opposite(side));
            if (rec.module.effectiveMask(rec.mask | towardNew) == rec.mask) continue;
            boolean pending = false;
            for (BuildJob j : queue) if (j.plot == n && !j.started) pending = true;
            if (!pending) queue.add(new BuildJob(n, rec.module, rec.style, false, true));
        }
        setChanged();
        return null;
    }

    private void cancel(int index) {
        BuildJob job = queue.remove(index);
        PlotRecord rec = plots[job.plot];
        if (opsJob == job) {
            opsJob = null;
            ops = List.of();
        }
        if (rec != null) {
            if (job.patch) rec.status = PlotRecord.BUILT;
            else plots[job.plot] = null;
        }
        setChanged();
    }

    // ---------------------------------------------------------------- build loop

    public void serverTick(ServerLevel level, BlockPos pos, BlockState state) {
        if ((level.getGameTime() + pos.asLong()) % 4 == 0) convertMatter();
        if (queue.isEmpty()) {
            status = ST_IDLE;
            budget = 0;
            return;
        }
        build(level);
    }

    private List<BlockOp> opsFor(BuildJob job, PlotRecord rec) {
        if (opsJob != job) {
            ops = job.module.generate(rec.mask);
            opsJob = job;
        }
        return ops;
    }

    private void build(ServerLevel level) {
        BuildJob job = queue.get(0);
        PlotRecord rec = plots[job.plot];
        if (rec == null) {
            queue.remove(0);
            opsJob = null;
            return;
        }
        if (!unlocked(job.style)) {
            status = ST_LOCKED;
            budget = 0;
            return;
        }
        if (!job.started) start(job, rec);
        List<BlockOp> list = opsFor(job, rec);

        int speed = upgrades.level(UpgradeKind.SPEED);
        int efficiency = upgrades.level(UpgradeKind.EFFICIENCY);
        budget += Upgrades.speedMultiplier(speed) * CoreConfig.workSpeed() / ArchitectConfig.baseInterval();
        int actions = Math.min(16, (int) budget);
        budget -= actions;
        if (budget > 1) budget = 1;
        int energyCost = (int) Math.max(0, Math.round(CoreConfig.scaleEnergy(ArchitectConfig.fePerBlock()) * Upgrades.energyMultiplier(speed, efficiency)));

        status = ST_BUILDING;
        BlockOp last = null;
        int skips = 0;
        while (true) {
            if (job.cursor >= list.size()) {
                finish(level, job, rec);
                break;
            }
            if (actions <= 0 || skips > 512) break;
            BlockOp op = list.get(job.cursor);
            int result = step(level, job, op, energyCost);
            if (result == STEP_SKIPPED) {
                job.cursor++;
                skips++;
            } else if (result == STEP_DONE) {
                job.cursor++;
                actions--;
                last = op;
            } else {
                status = result;
                budget = 0;
                break;
            }
        }
        if (last != null) {
            BlockPos target = Plots.origin(worldPosition, job.plot).offset(last.x(), last.y(), last.z());
            effects(level, target);
            setChanged();
        }
    }

    private void start(BuildJob job, PlotRecord rec) {
        int wanted = 0;
        for (int side = 0; side < 4; side++) {
            int n = Plots.neighbour(job.plot, side);
            if (n >= 0 && plots[n] != null) wanted |= Plots.bit(side);
        }
        rec.mask = job.module.effectiveMask(wanted);
        rec.status = PlotRecord.BUILDING;
        job.started = true;
        job.cursor = 0;
        opsJob = null;
        setChanged();
    }

    private void finish(ServerLevel level, BuildJob job, PlotRecord rec) {
        queue.remove(0);
        rec.status = PlotRecord.BUILT;
        opsJob = null;
        ops = List.of();
        budget = 0;
        level.playSound(null, worldPosition, SoundEvents.AMETHYST_BLOCK_CHIME, SoundSource.BLOCKS, 1.0F, 1.2F);
        setChanged();
    }

    private static final int STEP_SKIPPED = -1;
    private static final int STEP_DONE = -2;

    /** One op: returns STEP_SKIPPED (nothing to do, free), STEP_DONE (changed the world) or a status code to pause on. */
    private int step(ServerLevel level, BuildJob job, BlockOp op, int energyCost) {
        if (Plots.isTableCell(job.plot, op.x(), op.y(), op.z())) return STEP_SKIPPED;
        BlockPos target = Plots.origin(worldPosition, job.plot).offset(op.x(), op.y(), op.z());
        if (target.equals(worldPosition) || !level.isInWorldBounds(target)) return STEP_SKIPPED;
        if (!level.isLoaded(target)) return ST_UNLOADED;

        BlockState desired = op.piece().resolve(job.style);
        BlockState current = level.getBlockState(target);
        if (current == desired || (desired.isAir() && current.isAir())) return STEP_SKIPPED;

        boolean replaceable = current.isAir() || current.canBeReplaced() || ArchitectRegistry.isBuildingBlock(current)
                || current.getBlock() instanceof LiquidBlock;
        boolean clearing = false;
        if (!replaceable) {
            if (!job.clear || level.getBlockEntity(target) != null || current.getDestroySpeed(level, target) < 0) return STEP_SKIPPED;
            clearing = true;
        }
        Matter cost = Matter.ZERO;
        if (!desired.isAir()) {
            cost = job.style.cost;
            if (rustic < cost.rustic()) return ST_NO_RUSTIC;
            if (refined < cost.refined()) return ST_NO_REFINED;
            if (exotic < cost.exotic()) return ST_NO_EXOTIC;
            if (energy.getEnergyStored() < energyCost) return ST_NO_ENERGY;
        }

        FakePlayer fake = null;
        if (!current.isAir()) {
            fake = fakePlayer(level);
            if (NeoForge.EVENT_BUS.post(new BlockEvent.BreakEvent(level, target, current, fake)).isCanceled()) return STEP_SKIPPED;
        }
        if (!desired.isAir()) {
            if (fake == null) fake = fakePlayer(level);
            BlockSnapshot snapshot = BlockSnapshot.create(level.dimension(), level, target);
            if (NeoForge.EVENT_BUS.post(new BlockEvent.EntityPlaceEvent(snapshot, level.getBlockState(target.below()), fake)).isCanceled()) {
                return STEP_SKIPPED;
            }
        }

        if (clearing) {
            level.removeBlock(target, false);
            addRustic(Math.max(1, MatterTable.valueOf(current.getBlock().asItem()).rustic()));
        }
        if (desired.isAir()) {
            level.setBlock(target, desired, Block.UPDATE_ALL);
            return STEP_DONE;
        }
        if (!level.setBlock(target, desired, Block.UPDATE_ALL)) return STEP_SKIPPED;
        rustic -= cost.rustic();
        refined -= cost.refined();
        exotic -= cost.exotic();
        energy.consume(energyCost);
        return STEP_DONE;
    }

    private FakePlayer fakePlayer(ServerLevel level) {
        GameProfile profile = new GameProfile(owner != null ? owner : FALLBACK_OWNER, ownerName.isEmpty() ? "[Robotica Architect]" : ownerName);
        return FakePlayerFactory.get(level, profile);
    }

    private void effects(ServerLevel level, BlockPos target) {
        placedCount++;
        int speed = upgrades.level(UpgradeKind.SPEED);
        if (speed <= 2 || (placedCount & 3) == 0) {
            level.sendParticles(ParticleTypes.ELECTRIC_SPARK, target.getX() + 0.5, target.getY() + 0.5, target.getZ() + 0.5, 3, 0.3, 0.3, 0.3, 0.02);
        }
        if ((placedCount & 3) == 0) {
            level.playSound(null, target, SoundEvents.COPPER_PLACE, SoundSource.BLOCKS, 0.5F, 1.0F + level.random.nextFloat() * 0.3F);
        }
        if (ArchitectConfig.builderDrones()) {
            if (drone == null || drone.isRemoved()) {
                drone = new BuilderDrone(ArchitectRegistry.BUILDER_DRONE.get(), level);
                drone.setHome(worldPosition);
                drone.setPos(worldPosition.getX() + 0.5, worldPosition.getY() + 1.6, worldPosition.getZ() + 0.5);
                level.addFreshEntity(drone);
            }
            drone.setTarget(target);
        }
    }

    // ---------------------------------------------------------------- items, menu, persistence

    public void dropContents(Level level, BlockPos pos) {
        drop(level, pos, input);
        drop(level, pos, styleSlot);
        drop(level, pos, upgrades);
        if (drone != null) drone.discard();
    }

    private static void drop(Level level, BlockPos pos, IItemHandler handler) {
        for (int i = 0; i < handler.getSlots(); i++) {
            ItemStack stack = handler.getStackInSlot(i);
            if (!stack.isEmpty()) Containers.dropItemStack(level, pos.getX(), pos.getY(), pos.getZ(), stack.copy());
        }
    }

    @Override
    public Component getDisplayName() {
        return Component.translatable("block.robotica.architect_table");
    }

    @Nullable
    @Override
    public AbstractContainerMenu createMenu(int id, Inventory inv, Player player) {
        return new ArchitectMenu(id, inv, this);
    }

    @Override
    protected void collectImplicitComponents(DataComponentMap.Builder components) {
        super.collectImplicitComponents(components);
        if (!matter().isZero()) components.set(ArchitectRegistry.MATTER_COMPONENT.get(), matter());
    }

    @Override
    protected void applyImplicitComponents(DataComponentInput input) {
        super.applyImplicitComponents(input);
        Matter m = input.get(ArchitectRegistry.MATTER_COMPONENT.get());
        if (m != null) setMatter(m);
    }

    @Override
    protected void saveAdditional(CompoundTag tag, HolderLookup.Provider registries) {
        super.saveAdditional(tag, registries);
        tag.put("input", input.serializeNBT(registries));
        tag.put("style_slot", styleSlot.serializeNBT(registries));
        tag.put("upgrades", upgrades.serializeNBT(registries));
        tag.put("energy", energy.serializeNBT(registries));
        tag.putInt("rustic", rustic);
        tag.putInt("refined", refined);
        tag.putInt("exotic", exotic);
        if (owner != null) tag.putUUID("owner", owner);
        tag.putString("owner_name", ownerName);
        tag.putInt("selected_style", selectedStyle.ordinal());
        tag.putBoolean("clear_terrain", clearTerrain);
        ListTag plotList = new ListTag();
        for (int i = 0; i < plots.length; i++) if (plots[i] != null) plotList.add(plots[i].save(i));
        tag.put("plots", plotList);
        ListTag jobs = new ListTag();
        for (BuildJob job : queue) jobs.add(job.save());
        tag.put("queue", jobs);
    }

    @Override
    protected void loadAdditional(CompoundTag tag, HolderLookup.Provider registries) {
        super.loadAdditional(tag, registries);
        if (tag.contains("input")) input.deserializeNBT(registries, tag.getCompound("input"));
        if (tag.contains("style_slot")) styleSlot.deserializeNBT(registries, tag.getCompound("style_slot"));
        if (tag.contains("upgrades")) upgrades.deserializeNBT(registries, tag.getCompound("upgrades"));
        if (tag.contains("energy")) energy.deserializeNBT(registries, tag.get("energy"));
        rustic = tag.getInt("rustic");
        refined = tag.getInt("refined");
        exotic = tag.getInt("exotic");
        owner = tag.hasUUID("owner") ? tag.getUUID("owner") : null;
        ownerName = tag.getString("owner_name");
        selectedStyle = BuildStyle.byOrdinal(tag.getInt("selected_style"));
        clearTerrain = tag.getBoolean("clear_terrain");
        java.util.Arrays.fill(plots, null);
        for (Tag t : tag.getList("plots", Tag.TAG_COMPOUND)) {
            CompoundTag c = (CompoundTag) t;
            PlotRecord rec = PlotRecord.load(c);
            if (rec != null) plots[c.getInt("plot")] = rec;
        }
        queue.clear();
        for (Tag t : tag.getList("queue", Tag.TAG_COMPOUND)) {
            BuildJob job = BuildJob.load((CompoundTag) t);
            if (job != null && plots[job.plot] != null) queue.add(job);
        }
        opsJob = null;
    }
}
