package com.arno.robotica.architect.block;

import com.arno.robotica.Robotica;
import com.arno.robotica.architect.ArchitectConfig;
import com.arno.robotica.architect.ArchitectRegistry;
import com.arno.robotica.architect.entity.BuilderDrone;
import com.arno.robotica.architect.matter.Matter;
import com.arno.robotica.architect.matter.MatterTable;
import com.arno.robotica.architect.menu.ArchitectMenu;
import com.arno.robotica.architect.plan.BlockOp;
import com.arno.robotica.architect.plan.Layout;
import com.arno.robotica.architect.plan.Piece;
import com.arno.robotica.architect.plan.Plots;
import com.arno.robotica.architect.plan.Shell;
import com.arno.robotica.architect.style.BuildStyle;
import com.arno.robotica.core.CoreComponents;
import com.arno.robotica.core.CoreConfig;
import com.arno.robotica.core.CoreSounds;
import com.arno.robotica.core.energy.EnergyUtil;
import com.arno.robotica.core.energy.MachineEnergyStorage;
import com.arno.robotica.core.item.CoreItems;
import com.arno.robotica.core.upgrade.UpgradeKind;
import com.arno.robotica.core.upgrade.Upgrades;
import com.mojang.authlib.GameProfile;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.HolderLookup;
import net.minecraft.core.component.DataComponentMap;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.core.registries.Registries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.tags.TagKey;
import net.minecraft.server.players.GameProfileCache;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.Containers;
import net.minecraft.world.MenuProvider;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.LiquidBlock;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.scores.Team;
import net.neoforged.neoforge.capabilities.Capabilities;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.common.util.BlockSnapshot;
import net.neoforged.neoforge.common.util.FakePlayer;
import net.neoforged.neoforge.common.util.FakePlayerFactory;
import net.neoforged.neoforge.event.level.BlockEvent;
import net.neoforged.neoforge.items.IItemHandler;
import net.neoforged.neoforge.items.ItemHandlerHelper;
import net.neoforged.neoforge.items.ItemStackHandler;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

/**
 * Architect Table. Bulk items in the input become matter; matter + FE become building blocks while the table works
 * through its plan, one block per interval. The table is the middle floor block of its own plot and is never replaced.
 * Clear terrain breaks whatever is in the way (block entities too, their contents are saved), voids junk and stashes the
 * rest in a container touching the table. Picking the table up keeps everything: matter, energy, slots, the plan ({@link Layout}), the running flag and the cursor.
 * Only the owner (or a player on the owner's scoreboard team, or an operator) can use it.
 */
public class ArchitectTableBlockEntity extends BlockEntity implements MenuProvider {
    public static final int INPUT_SLOTS = 9;

    public static final int ST_IDLE = 0, ST_BUILDING = 1, ST_NO_RUSTIC = 2, ST_NO_REFINED = 3, ST_NO_EXOTIC = 4,
            ST_NO_ENERGY = 5, ST_LOCKED = 6, ST_UNLOADED = 7, ST_READY = 8;

    // GUI actions (ArchitectActionPayload): a and b are the arguments.
    /** a = plot, b = 1 to forget a built plot (shift-click). Empty plot: queue, queued plot: unqueue. */
    public static final int ACTION_TOGGLE = 0;
    /** a = plot, b = side. */
    public static final int ACTION_DOOR = 1;
    public static final int ACTION_BUILD = 2;
    public static final int ACTION_CANCEL = 3;
    /** a = 0 / 1. */
    public static final int ACTION_CLEAR = 4;
    /** a = style ordinal. */
    public static final int ACTION_STYLE = 5;

    /** Cleared junk (cobblestone, dirt, gravel and the like, tag robotica:voidable) is voided instead of stashed. */
    private static final TagKey<Item> JUNK = TagKey.create(Registries.ITEM, Robotica.id("voidable"));

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
    /** A wound Mainspring or any FE cell: the table runs without cables. */
    public final ItemStackHandler battery = new ItemStackHandler(1) {
        @Override
        public boolean isItemValid(int slot, ItemStack stack) {
            return EnergyUtil.isEnergyItem(stack);
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
    public final Upgrades upgrades = new Upgrades(2, Set.of(UpgradeKind.SPEED, UpgradeKind.EFFICIENCY, UpgradeKind.HEIGHT), this::setChanged);
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

    private final Layout layout = new Layout();
    /** Set by Build; cleared when nothing is left to do. */
    private boolean running;
    /** Plot being walked (-1 none), the signature its ops were made for and the position in them. */
    private int current = -1;
    private int currentSig;
    private int cursor;

    // transient
    private int status = ST_IDLE;
    private double budget;
    private int placedCount;
    private int opsSig;
    private int opsPlot = -1;
    private int opsWas;
    private List<BlockOp> ops = List.of();
    @Nullable
    private BuilderDrone drone;
    private long lastPlaceSound = Long.MIN_VALUE / 2;
    private long lastAbsorbSound = Long.MIN_VALUE / 2;
    /** Ticks until clear terrain may remove the next block. */
    private int clearCooldown;

    public ArchitectTableBlockEntity(BlockPos pos, BlockState state) {
        super(ArchitectRegistry.ARCHITECT_TABLE_BE.get(), pos, state);
    }

    public IItemHandler automation() {
        return automation;
    }

    public Layout layout() {
        return layout;
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
        Team theirs = level.getScoreboard().getPlayersTeam(currentOwnerName());
        return theirs != null && mine.isAlliedTo(theirs);
    }

    /** The owner's current name: online player first, then the profile cache (both by UUID), else the stored name. */
    private String currentOwnerName() {
        if (owner == null || !(level instanceof ServerLevel serverLevel)) return ownerName;
        MinecraftServer server = serverLevel.getServer();
        ServerPlayer online = server.getPlayerList().getPlayer(owner);
        if (online != null) return online.getGameProfile().getName();
        GameProfileCache cache = server.getProfileCache();
        if (cache != null) {
            Optional<GameProfile> profile = cache.get(owner);
            if (profile.isPresent() && !profile.get().getName().isEmpty()) return profile.get().getName();
        }
        return ownerName;
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
        boolean absorbed = false;
        for (int slot = 0; slot < input.getSlots() && budgetSlots > 0; slot++) {
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
            absorbed = true;
            setChanged();
        }
        if (absorbed && level != null && level.getGameTime() - lastAbsorbSound >= 20) {
            lastAbsorbSound = level.getGameTime();
            CoreSounds.play(level, worldPosition, CoreSounds.MATTER_ABSORB, SoundSource.BLOCKS, 0.6F, 1.0F);
        }
    }

    // ---------------------------------------------------------------- styles and settings

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

    /** Base FE per block (scaled by the global energy setting), before the style factor and the cards. */
    public static int baseEnergy() {
        return (int) Math.round(CoreConfig.scaleEnergy(ArchitectConfig.fePerBlock()));
    }

    public boolean running() {
        return running;
    }

    public int status() {
        return status;
    }

    public boolean isBuilding() {
        return status == ST_BUILDING;
    }

    // ---------------------------------------------------------------- GUI state words

    public int currentPlot() {
        return running ? current : -1;
    }

    public int progressPermille() {
        if (current < 0 || opsPlot != current || ops.isEmpty()) return 0;
        return Math.min(1000, cursor * 1000 / ops.size());
    }

    /** Bit 0 clear terrain, bit 1 running, bits 2-3 selected style, bits 4-7 unlocked styles. */
    public int flags() {
        return (clearTerrain ? 1 : 0) | (running ? 2 : 0) | (selectedStyle().ordinal() << 2) | (unlockedMask() << 4);
    }

    /** Three plots per word, 10 bits each (see {@link Layout#packed}). */
    public int planWord(int word) {
        int w = 0;
        for (int i = 0; i < 3; i++) {
            int plot = word * 3 + i;
            if (plot < Plots.COUNT) w |= (layout.packed(plot) & 0x3FF) << (10 * i);
        }
        return w;
    }

    // ---------------------------------------------------------------- actions (called by the validated payload)

    /** Applies a GUI action from a player who passed {@link #canUse}. Returns a feedback message or null. */
    @Nullable
    public Component handleAction(@Nullable Player player, int action, int a, int b) {
        Component feedback = null;
        switch (action) {
            case ACTION_TOGGLE -> feedback = toggle(a, b != 0);
            case ACTION_DOOR -> {
                int result = layout.toggleDoor(a, b);
                if (result == Layout.DOOR_LAST) feedback = Component.translatable("message.robotica.architect_last_door");
            }
            case ACTION_BUILD -> {
                if (layout.hasWork()) running = true;
            }
            case ACTION_CANCEL -> layout.unqueueAll();
            case ACTION_CLEAR -> clearTerrain = a != 0 && ArchitectConfig.allowClearTerrain();
            case ACTION_STYLE -> {
                if (a >= 0 && a < BuildStyle.values().length && unlocked(BuildStyle.values()[a])) selectedStyle = BuildStyle.values()[a];
            }
            default -> {
                return Component.translatable("message.robotica.architect_bad_request");
            }
        }
        setChanged();
        return feedback;
    }

    @Nullable
    private Component toggle(int plot, boolean forget) {
        if (!Plots.valid(plot)) return Component.translatable("message.robotica.architect_bad_request");
        switch (layout.state(plot)) {
            case Layout.QUEUED -> layout.unqueue(plot);
            case Layout.BUILT -> {
                if (forget) layout.forget(plot);
            }
            default -> {
                if (layout.queuedCount() >= ArchitectConfig.maxQueue()) return Component.translatable("message.robotica.architect_queue_full");
                BuildStyle style = selectedStyle();
                if (!unlocked(style)) return Component.translatable("message.robotica.architect_style_locked");
                BlockPos origin = Plots.origin(worldPosition, plot);
                if (level == null || origin.getY() < level.getMinBuildHeight() || origin.getY() + layout.height() > level.getMaxBuildHeight()) {
                    return Component.translatable("message.robotica.architect_out_of_world");
                }
                layout.queue(plot, style);
            }
        }
        return null;
    }

    // ---------------------------------------------------------------- build loop

    public void serverTick(ServerLevel level, BlockPos pos, BlockState state) {
        layout.setHeight(Math.min(Plots.HEIGHT + upgrades.level(UpgradeKind.HEIGHT), level.getMaxBuildHeight() - pos.getY()));
        if (clearCooldown > 0) clearCooldown--;
        if ((level.getGameTime() + pos.asLong()) % 4 == 0) convertMatter();
        if (energy.getSpace() > 0 && !battery.getStackInSlot(0).isEmpty()) {
            EnergyUtil.dischargeItem(battery.getStackInSlot(0), energy, ArchitectConfig.energyReceive());
        }
        if (!running) {
            status = layout.hasWork() ? ST_READY : ST_IDLE;
            budget = 0;
            return;
        }
        build(level);
    }

    private List<BlockOp> opsFor(int plot, int sig) {
        int was = layout.builtHeight(plot);
        if (opsPlot != plot || opsSig != sig || opsWas != was) {
            opsWas = was;
            Shell.Shape shape = layout.shape(plot);
            ops = Shell.generate(shape, plot == Plots.CENTER);
            if (was > shape.height()) {
                // the building got lower (Height cards taken out): take down its old top, building blocks only
                ops = new ArrayList<>(ops);
                for (int y = shape.height(); y < was; y++) {
                    for (int z = 0; z < Plots.SIZE; z++) for (int x = 0; x < Plots.SIZE; x++) ops.add(new BlockOp(x, y, z, Piece.AIR));
                }
            }
            opsPlot = plot;
            opsSig = sig;
        }
        return ops;
    }

    private void build(ServerLevel level) {
        if (current < 0 || !layout.needsWork(current)) {
            current = layout.nextWork();
            cursor = 0;
            currentSig = current < 0 ? 0 : layout.signature(current);
            setChanged();
        }
        if (current < 0) {
            running = false;
            status = ST_IDLE;
            budget = 0;
            setChanged();
            return;
        }
        int sig = layout.signature(current);
        if (sig != currentSig) {
            // The plan changed under this plot: walk it again, correct blocks are skipped for free.
            currentSig = sig;
            cursor = 0;
        }
        BuildStyle style = layout.style(current);
        if (!unlocked(style)) {
            status = ST_LOCKED;
            budget = 0;
            return;
        }
        List<BlockOp> list = opsFor(current, sig);

        int speed = upgrades.level(UpgradeKind.SPEED);
        int efficiency = upgrades.level(UpgradeKind.EFFICIENCY);
        budget += Upgrades.speedMultiplier(speed) * CoreConfig.workSpeed() / ArchitectConfig.baseInterval();
        int actions = Math.min(16, (int) budget);
        budget -= actions;
        if (budget > 1) budget = 1;
        int energyCost = (int) Math.max(0, Math.round(style.energyPerBlock(baseEnergy()) * Upgrades.energyMultiplier(speed, efficiency)));

        status = ST_BUILDING;
        BlockPos origin = Plots.origin(worldPosition, current);
        BlockPos last = null;
        int skips = 0;
        while (true) {
            if (cursor >= list.size()) {
                finish(level);
                break;
            }
            if (actions <= 0 || skips > 512) break;
            BlockOp op = list.get(cursor);
            int result = step(level, origin, op, style, energyCost, layout.height());
            if (result == STEP_SKIPPED) {
                cursor++;
                skips++;
            } else if (result == STEP_WAIT) {
                break;
            } else if (result == STEP_DONE) {
                layout.markChanging(current, layout.height());
                cursor++;
                actions--;
                last = origin.offset(op.x(), op.y(), op.z());
            } else {
                status = result;
                budget = 0;
                break;
            }
        }
        if (last != null) effects(level, last);
        setChanged();
    }

    private void finish(ServerLevel level) {
        layout.markBuilt(current, currentSig);
        current = -1;
        cursor = 0;
        budget = 0;
        CoreSounds.play(level, worldPosition, CoreSounds.ARCHITECT_DONE, SoundSource.BLOCKS, 1.0F, 1.0F);
        if (!layout.hasWork()) running = false;
        setChanged();
    }

    private static final int STEP_SKIPPED = -1;
    private static final int STEP_DONE = -2;
    /** Clear terrain waits for its cooldown: stop for this tick, nothing changes. */
    private static final int STEP_WAIT = -3;

    /** One op: returns STEP_SKIPPED (nothing to do, free), STEP_DONE (changed the world), STEP_WAIT or a status code to pause on. */
    private int step(ServerLevel level, BlockPos origin, BlockOp op, BuildStyle style, int energyCost, int height) {
        BlockPos target = origin.offset(op.x(), op.y(), op.z());
        if (target.equals(worldPosition) || !level.isInWorldBounds(target)) return STEP_SKIPPED;
        if (!level.isLoaded(target)) return ST_UNLOADED;

        BlockState desired = op.piece().resolve(style);
        BlockState current = level.getBlockState(target);
        if (current == desired || (desired.isAir() && current.isAir())) return STEP_SKIPPED;
        if (current.is(ArchitectRegistry.ARCHITECT_TABLE.get())) return STEP_SKIPPED;
        // above the building (it got lower): only its own old blocks go
        if (op.y() >= height && !ArchitectRegistry.isBuildingBlock(current)) return STEP_SKIPPED;
        // spawn protection, world border and other no-build areas: skip the block, spend nothing, never stall the build
        if (!mayBuildAt(level, target, fakePlayer(level))) return STEP_SKIPPED;

        boolean replaceable = current.isAir() || current.canBeReplaced() || ArchitectRegistry.isBuildingBlock(current)
                || current.getBlock() instanceof LiquidBlock;
        boolean clearing = false;
        if (!replaceable) {
            if (!clearTerrain || current.getDestroySpeed(level, target) < 0) return STEP_SKIPPED;
            // a container touching the table is where the drops go: leave it standing
            if (level.getBlockEntity(target) != null && target.distManhattan(worldPosition) == 1) return STEP_SKIPPED;
            clearing = true;
            // clearing is slow on purpose (speed cards do not help), so the table is no quarry
            if (clearCooldown > 0) return STEP_WAIT;
        }
        Matter cost = op.piece().cost(style);
        if (!desired.isAir()) {
            if (rustic < cost.rustic()) return ST_NO_RUSTIC;
            if (refined < cost.refined()) return ST_NO_REFINED;
            if (exotic < cost.exotic()) return ST_NO_EXOTIC;
            if (energy.getEnergyStored() < energyCost) return ST_NO_ENERGY;
        }

        FakePlayer fake = fakePlayer(level);
        if (!current.isAir() && NeoForge.EVENT_BUS.post(new BlockEvent.BreakEvent(level, target, current, fake)).isCanceled()) return STEP_SKIPPED;
        if (!desired.isAir()) {
            BlockSnapshot snapshot = BlockSnapshot.create(level.dimension(), level, target);
            if (NeoForge.EVENT_BUS.post(new BlockEvent.EntityPlaceEvent(snapshot, level.getBlockState(target.below()), fake)).isCanceled()) {
                return STEP_SKIPPED;
            }
        }

        if (clearing) {
            // chests, furnaces and the like in the way: their contents come along first, nothing spills
            BlockEntity in = level.getBlockEntity(target);
            IItemHandler inside = in == null ? null : level.getCapability(Capabilities.ItemHandler.BLOCK, target, null);
            if (inside != null) {
                for (int slot = 0; slot < inside.getSlots(); slot++) {
                    for (ItemStack taken = inside.extractItem(slot, 64, false); !taken.isEmpty(); taken = inside.extractItem(slot, 64, false)) {
                        stash(level, taken);
                    }
                }
            }
            List<ItemStack> drops = Block.getDrops(current, level, target, in, fake, new ItemStack(Items.DIAMOND_PICKAXE));
            level.removeBlock(target, false);
            for (ItemStack drop : drops) if (!drop.is(JUNK)) stash(level, drop);
            clearCooldown = ArchitectConfig.clearInterval();
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

    /** Cleared blocks go into a container touching the table (a chest on top of it, for example), else on top of the table. */
    private void stash(ServerLevel level, ItemStack stack) {
        for (Direction side : Direction.values()) {
            if (stack.isEmpty()) return;
            IItemHandler target = level.getCapability(Capabilities.ItemHandler.BLOCK, worldPosition.relative(side), side.getOpposite());
            if (target != null) stack = ItemHandlerHelper.insertItemStacked(target, stack, false);
        }
        if (!stack.isEmpty()) Containers.dropItemStack(level, worldPosition.getX() + 0.5, worldPosition.getY() + 1.2, worldPosition.getZ() + 0.5, stack);
    }

    /** False inside vanilla spawn protection, outside the world border, or where the player may not interact. */
    public static boolean mayBuildAt(ServerLevel level, BlockPos target, Player player) {
        if (!level.getWorldBorder().isWithinBounds(target)) return false;
        if (level.getServer().isUnderSpawnProtection(level, target, player)) return false;
        return level.mayInteract(player, target);
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
        long now = level.getGameTime();
        if ((placedCount & 3) == 0 && now - lastPlaceSound >= 8) {
            lastPlaceSound = now;
            CoreSounds.play(level, target, CoreSounds.ARCHITECT_PLACE, SoundSource.BLOCKS, 0.6F, 0.9F + level.random.nextFloat() * 0.3F);
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

    /** The table was removed. Its contents travel with the dropped item (see the loot table), only the drone goes. */
    public void onRemoved() {
        if (drone != null) drone.discard();
    }

    /** True when the table holds anything worth keeping (creative players get it as an item, like a shulker box). */
    public boolean hasContents() {
        return !matter().isZero() || !layout.isEmpty() || hasSlotsOrEnergy();
    }

    private boolean hasSlotsOrEnergy() {
        if (energy.getEnergyStored() > 0) return true;
        for (IItemHandler handler : List.of(input, styleSlot, battery, upgrades)) {
            for (int i = 0; i < handler.getSlots(); i++) if (!handler.getStackInSlot(i).isEmpty()) return true;
        }
        return false;
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
        if (!layout.isEmpty()) components.set(ArchitectRegistry.BUILD_STATE_COMPONENT.get(), buildStateTag());
        if (level != null && hasSlotsOrEnergy()) {
            CompoundTag contents = new CompoundTag();
            writeContents(contents, level.registryAccess());
            components.set(CoreComponents.CONTENTS.get(), contents);
        }
    }

    @Override
    protected void applyImplicitComponents(DataComponentInput input) {
        super.applyImplicitComponents(input);
        Matter m = input.get(ArchitectRegistry.MATTER_COMPONENT.get());
        if (m != null) setMatter(m);
        CompoundTag build = input.get(ArchitectRegistry.BUILD_STATE_COMPONENT.get());
        if (build != null) applyBuildState(build);
        CompoundTag contents = input.get(CoreComponents.CONTENTS.get());
        if (contents != null && level != null) readContents(contents, level.registryAccess());
    }

    @Override
    protected void saveAdditional(CompoundTag tag, HolderLookup.Provider registries) {
        super.saveAdditional(tag, registries);
        writeContents(tag, registries);
        tag.putInt("rustic", rustic);
        tag.putInt("refined", refined);
        tag.putInt("exotic", exotic);
        if (owner != null) tag.putUUID("owner", owner);
        tag.putString("owner_name", ownerName);
        writeBuild(tag);
    }

    /** Slots and energy: saved with the block and carried by the item when the table is picked up. */
    private void writeContents(CompoundTag tag, HolderLookup.Provider registries) {
        tag.put("input", input.serializeNBT(registries));
        tag.put("style_slot", styleSlot.serializeNBT(registries));
        tag.put("upgrades", upgrades.serializeNBT(registries));
        tag.put("battery", battery.serializeNBT(registries));
        tag.put("energy", energy.serializeNBT(registries));
    }

    private void readContents(CompoundTag tag, HolderLookup.Provider registries) {
        if (tag.contains("input")) input.deserializeNBT(registries, tag.getCompound("input"));
        if (tag.contains("style_slot")) styleSlot.deserializeNBT(registries, tag.getCompound("style_slot"));
        if (tag.contains("upgrades")) upgrades.deserializeNBT(registries, tag.getCompound("upgrades"));
        if (tag.contains("battery")) battery.deserializeNBT(registries, tag.getCompound("battery"));
        if (tag.contains("energy")) energy.deserializeNBT(registries, tag.get("energy"));
        layout.setHeight(Plots.HEIGHT + upgrades.level(UpgradeKind.HEIGHT));
    }

    private void writeBuild(CompoundTag tag) {
        tag.put("layout", layout.save());
        tag.putBoolean("running", running);
        tag.putInt("current", current);
        tag.putInt("current_sig", currentSig);
        tag.putInt("cursor", cursor);
        tag.putInt("selected_style", selectedStyle.ordinal());
        tag.putBoolean("clear_terrain", clearTerrain);
    }

    private void readBuild(CompoundTag tag) {
        layout.load(tag.getCompound("layout"));
        running = tag.getBoolean("running");
        current = tag.contains("current") ? tag.getInt("current") : -1;
        if (!Plots.valid(current)) current = -1;
        currentSig = tag.getInt("current_sig");
        cursor = Math.max(0, tag.getInt("cursor"));
        selectedStyle = BuildStyle.byOrdinal(tag.getInt("selected_style"));
        clearTerrain = tag.getBoolean("clear_terrain");
        opsPlot = -1;
    }

    /** Build state for the item (picking the table up): the plan, the cursor, settings and where it was standing. */
    private CompoundTag buildStateTag() {
        CompoundTag tag = new CompoundTag();
        writeBuild(tag);
        tag.putLong("origin", worldPosition.asLong());
        if (level != null) tag.putString("dim", level.dimension().location().toString());
        return tag;
    }

    /**
     * Restores the build state of a picked up table. Built plots stand where the table stood, so on any other spot
     * they are forgotten and the plot that was being built starts over; queued plots keep waiting.
     */
    private void applyBuildState(CompoundTag tag) {
        readBuild(tag);
        boolean sameSpot = tag.contains("origin") && tag.getLong("origin") == worldPosition.asLong()
                && (level == null || !tag.contains("dim") || tag.getString("dim").equals(level.dimension().location().toString()));
        if (!sameSpot) {
            layout.forgetBuilt();
            current = -1;
            cursor = 0;
            running = false;
        }
        setChanged();
    }

    @Override
    protected void loadAdditional(CompoundTag tag, HolderLookup.Provider registries) {
        super.loadAdditional(tag, registries);
        readContents(tag, registries);
        rustic = tag.getInt("rustic");
        refined = tag.getInt("refined");
        exotic = tag.getInt("exotic");
        owner = tag.hasUUID("owner") ? tag.getUUID("owner") : null;
        ownerName = tag.getString("owner_name");
        readBuild(tag);
    }
}
