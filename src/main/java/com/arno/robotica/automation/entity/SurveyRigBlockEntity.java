package com.arno.robotica.automation.entity;

import com.arno.robotica.automation.AutomationConfig;
import com.arno.robotica.automation.AutomationContent;
import com.arno.robotica.automation.menu.SurveyRigMenu;
import com.arno.robotica.core.CoreConfig;
import com.arno.robotica.core.CoreSounds;
import com.arno.robotica.core.item.CoreItems;
import com.arno.robotica.core.upgrade.UpgradeKind;
import com.arno.robotica.core.upgrade.Upgrades;
import com.mojang.authlib.GameProfile;
import net.minecraft.core.BlockPos;
import net.minecraft.core.HolderLookup;
import net.minecraft.core.SectionPos;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.core.registries.Registries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.server.players.GameProfileCache;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.Containers;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.enchantment.Enchantments;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.chunk.LevelChunk;
import net.minecraft.world.level.chunk.LevelChunkSection;
import net.minecraft.world.level.levelgen.structure.BoundingBox;
import net.minecraft.world.scores.Team;
import net.neoforged.neoforge.common.Tags;
import net.neoforged.neoforge.items.ItemStackHandler;
import org.jetbrains.annotations.Nullable;

import java.util.EnumSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;

/**
 * A lag-free virtual quarry. On first power it scans its own chunk once, from its Y down to the world bottom, a few
 * sections per tick and never loading another chunk, into an ore ledger ({@link SurveyLedgers}). Then every operation
 * takes one ore off the ledger and rolls that ore's loot table with a pickaxe carrying the Fortune or Silk Touch of its
 * cards. The world is not dug: no holes, no block updates. With {@code stripOresFromWorld} the ledger's ores are swapped
 * for their host rock a few per tick, so they cannot also be mined by hand. A mined-out chunk is surveyed for good.
 */
public class SurveyRigBlockEntity extends AreaWorkerBlockEntity {
    public static final Set<UpgradeKind> KINDS = EnumSet.of(UpgradeKind.SPEED, UpgradeKind.EFFICIENCY, UpgradeKind.FORTUNE,
            UpgradeKind.SILK, UpgradeKind.VOID);
    public static final int UPGRADE_SLOTS = 4;

    /** What the rig is doing, for the GUI and the status line. */
    public enum RigState {
        WAITING, SCANNING, MINING, NEEDS_CORE, FINISHED, SURVEYED, BUSY;

        public static RigState byOrdinal(int i) {
            RigState[] v = values();
            return v[Math.max(0, Math.min(v.length - 1, i))];
        }
    }

    /** Magma Core slot: lets the rig mine ancient debris. Never consumed. */
    public final ItemStackHandler core = new ItemStackHandler(1) {
        @Override
        public boolean isItemValid(int slot, ItemStack stack) {
            return stack.is(CoreItems.MAGMA_CORE.get());
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

    private RigState rigState = RigState.WAITING;
    private boolean finishedHere;
    private int progress;
    private int ledgerLeft;
    private int ledgerTotal;
    private int scanPercent;
    private int toolKey = -1;
    private ItemStack toolStack = ItemStack.EMPTY;

    // ---- game test hooks (not saved) ----
    @Nullable
    private BoundingBox scanBounds;
    @Nullable
    private ChunkPos ledgerChunkOverride;
    @Nullable
    private Boolean stripOverride;

    public SurveyRigBlockEntity(BlockPos pos, BlockState state) {
        super(AutomationContent.SURVEY_RIG_BE.get(), pos, state, KINDS, UPGRADE_SLOTS,
                AutomationConfig.surveyEnergyBuffer(), AutomationConfig.surveyMaxInput());
    }

    @Override
    public String blockKey() {
        return "block.robotica.survey_rig";
    }

    /** Test hook: scan only this box (absolute coordinates, intersected with the chunk). */
    public void setScanBounds(@Nullable BoundingBox box) {
        scanBounds = box;
    }

    /** Test hook: keep the ledger under another chunk key so parallel tests in one chunk do not meet. */
    public void setLedgerChunk(@Nullable ChunkPos chunk) {
        ledgerChunkOverride = chunk;
    }

    /** Test hook: overrides the stripOresFromWorld config for this rig. */
    public void setStripOverride(@Nullable Boolean strip) {
        stripOverride = strip;
    }

    public ChunkPos ledgerChunk() {
        return ledgerChunkOverride != null ? ledgerChunkOverride : new ChunkPos(worldPosition);
    }

    private boolean strip() {
        return stripOverride != null ? stripOverride : AutomationConfig.stripOres();
    }

    // ---- area: the rig's own chunk ----

    @Override
    protected void recalc() {
        areaSize = 16;
        toolKey = -1;
    }

    @Override
    public int areaMinX() {
        return SectionPos.sectionToBlockCoord(SectionPos.blockToSectionCoord(worldPosition.getX()));
    }

    @Override
    public int areaMinZ() {
        return SectionPos.sectionToBlockCoord(SectionPos.blockToSectionCoord(worldPosition.getZ()));
    }

    @Override
    protected int areaMinY() {
        return level != null ? level.getMinBuildHeight() : worldPosition.getY() - 64;
    }

    @Override
    protected int areaMaxY() {
        return worldPosition.getY();
    }

    @Override
    protected int batteryPullRate() {
        return 8_000;
    }

    // ---- numbers ----

    public RigState rigState() {
        return rigState;
    }

    public int ledgerLeft() {
        return ledgerLeft;
    }

    public int ledgerTotal() {
        return ledgerTotal;
    }

    public int scanPercent() {
        return scanPercent;
    }

    @Override
    public int guiProgress() {
        if (rigState == RigState.SCANNING || rigState == RigState.WAITING) return 0;
        if (ledgerTotal <= 0) return 100;
        return (int) Math.min(100, 100L * (ledgerTotal - ledgerLeft) / ledgerTotal);
    }

    public int actionInterval() {
        int base = CoreConfig.scaleInterval(AutomationConfig.surveyInterval());
        return Math.max(1, base / Upgrades.speedMultiplier(upgrades.level(UpgradeKind.SPEED)));
    }

    /** FE per ore: steep like the Excavator, every speed card costs more per ore than the one before. */
    public int energyPerOre() {
        int speed = upgrades.level(UpgradeKind.SPEED);
        int eff = upgrades.level(UpgradeKind.EFFICIENCY);
        return (int) Math.round(CoreConfig.scaleEnergy(AutomationConfig.surveyFePerOre()) * Upgrades.steepEnergyMultiplier(speed, eff));
    }

    public static boolean isOre(BlockState state) {
        return state.is(Tags.Blocks.ORES) && !state.hasBlockEntity();
    }

    /** Ancient debris (and other netherite ores) need a Magma Core in the rig. */
    public static boolean needsCore(Block block) {
        return block == Blocks.ANCIENT_DEBRIS || block.defaultBlockState().is(Tags.Blocks.ORES_NETHERITE_SCRAP);
    }

    public boolean hasCore() {
        return !core.getStackInSlot(0).isEmpty();
    }

    /** The rock an ore sits in: what replaces it when stripped, and what filler drops come from. */
    public static BlockState hostFor(Level level, BlockState ore, BlockPos pos) {
        if (ore.is(Tags.Blocks.ORES_IN_GROUND_DEEPSLATE)) return Blocks.DEEPSLATE.defaultBlockState();
        if (ore.is(Tags.Blocks.ORES_IN_GROUND_NETHERRACK)) return Blocks.NETHERRACK.defaultBlockState();
        if (ore.is(Tags.Blocks.ORES_IN_GROUND_STONE)) return Blocks.STONE.defaultBlockState();
        if (level.dimension() == Level.NETHER) return Blocks.NETHERRACK.defaultBlockState();
        if (level.dimension() == Level.END) return Blocks.END_STONE.defaultBlockState();
        return pos.getY() < 0 ? Blocks.DEEPSLATE.defaultBlockState() : Blocks.STONE.defaultBlockState();
    }

    private void setRigState(RigState next) {
        if (next != rigState) {
            rigState = next;
            setChanged();
        }
    }

    private void cache(@Nullable SurveyLedgers.Entry e) {
        if (e == null) {
            ledgerLeft = ledgerTotal = scanPercent = 0;
            return;
        }
        ledgerLeft = e.left();
        ledgerTotal = e.total();
        scanPercent = Math.round(e.scanProgress() * 100);
    }

    /** True when another, live rig is working this rig's chunk. Server side. */
    public boolean chunkTakenByOther(ServerLevel sl, @Nullable SurveyLedgers.Entry e) {
        if (e == null || e.rig == null || e.rig.equals(worldPosition)) return false;
        return sl.isLoaded(e.rig) && sl.getBlockEntity(e.rig) instanceof SurveyRigBlockEntity other && !other.isRemoved();
    }

    // ---- work loop ----

    @Override
    protected Status work(ServerLevel sl) {
        SurveyLedgers data = SurveyLedgers.get(sl);
        ChunkPos key = ledgerChunk();
        SurveyLedgers.Entry e = data.get(key);
        if (e != null && e.phase == SurveyLedgers.Phase.DONE) {
            setRigState(finishedHere ? RigState.FINISHED : RigState.SURVEYED);
            ledgerLeft = 0;
            ledgerTotal = e.total();
            scanPercent = 100;
            return Status.IDLE;
        }
        if (chunkTakenByOther(sl, e)) {
            setRigState(RigState.BUSY);
            cache(e);
            return Status.IDLE;
        }
        if (e == null) {
            if (energy.getEnergyStored() <= 0) {
                setRigState(RigState.WAITING);
                cache(null);
                return Status.NO_ENERGY;
            }
            e = data.getOrCreate(key);
            startScan(sl, e);
            data.setDirty();
            CoreSounds.play(sl, worldPosition, CoreSounds.MACHINE_START, SoundSource.BLOCKS, 0.8F, 0.9F);
        }
        if (!worldPosition.equals(e.rig)) {
            e.rig = worldPosition.immutable();
            data.setDirty();
        }
        if (e.phase == SurveyLedgers.Phase.SCANNING) {
            if (energy.getEnergyStored() <= 0) {
                cache(e);
                return Status.NO_ENERGY;
            }
            setRigState(RigState.SCANNING);
            scan(sl, e);
            data.setDirty();
            cache(e);
            if (CoreSounds.due(sl, worldPosition, 40)) CoreSounds.play(sl, worldPosition, CoreSounds.CHARGER_HUM, SoundSource.BLOCKS, 0.5F, 1.4F);
            if (e.phase != SurveyLedgers.Phase.SCANNING) CoreSounds.play(sl, worldPosition, CoreSounds.ROBOT_BEEP, SoundSource.BLOCKS, 0.8F, 1.2F);
            return Status.WORKING;
        }
        // Mining the ledger.
        if (stripWorld(sl, e)) data.setDirty();
        cache(e);
        if (e.left() <= 0) {
            if (e.stripQueued() > 0) {
                setRigState(RigState.MINING);
                return Status.WORKING;
            }
            e.finish();
            data.setDirty();
            finishedHere = true;
            setRigState(RigState.FINISHED);
            cache(e);
            finishedSound(sl);
            setChanged();
            return Status.IDLE;
        }
        boolean core = hasCore();
        if (e.pick(sl.random, b -> core || !needsCore(b)) == null) {
            setRigState(RigState.NEEDS_CORE);
            return Status.IDLE;
        }
        setRigState(RigState.MINING);
        int cost = energyPerOre();
        int interval = actionInterval();
        if (progress < interval) {
            if (energy.getEnergyStored() < cost) return Status.NO_ENERGY;
            progress++;
            if (CoreSounds.due(sl, worldPosition, 60)) CoreSounds.play(sl, worldPosition, CoreSounds.DRILL_GRIND, SoundSource.BLOCKS, 0.35F, 1.3F);
            if (progress < interval) return Status.WORKING;
        }
        if (!energy.consume(cost)) return Status.NO_ENERGY;
        progress = 0;
        Block ore = e.pick(sl.random, b -> core || !needsCore(b));
        if (ore != null && e.take(e.key(ore))) {
            data.setDirty();
            mine(sl, ore);
        }
        cache(e);
        return Status.WORKING;
    }

    private void startScan(ServerLevel sl, SurveyLedgers.Entry e) {
        int top = Math.min(worldPosition.getY() - 1, sl.getMaxBuildHeight() - 1);
        int bottom = sl.getMinBuildHeight();
        if (scanBounds != null) {
            top = Math.min(top, scanBounds.maxY());
            bottom = Math.max(bottom, scanBounds.minY());
        }
        e.phase = SurveyLedgers.Phase.SCANNING;
        e.scanTop = top;
        e.scanBottom = bottom;
        e.scanCursor = top;
    }

    /** Reads up to N section slices of the rig's own chunk. Waits (keeps the cursor) while the chunk is not loaded. */
    private void scan(ServerLevel sl, SurveyLedgers.Entry e) {
        ChunkPos chunk = new ChunkPos(worldPosition);
        LevelChunk lc = sl.getChunkSource().getChunkNow(chunk.x, chunk.z);
        if (lc == null) return;
        boolean record = strip();
        int minX = chunk.getMinBlockX();
        int minZ = chunk.getMinBlockZ();
        int budget = AutomationConfig.surveySectionsPerTick();
        while (budget-- > 0 && e.scanCursor >= e.scanBottom) {
            int top = e.scanCursor;
            int bottom = Math.max(e.scanBottom, SectionPos.sectionToBlockCoord(SectionPos.blockToSectionCoord(top)));
            LevelChunkSection section = lc.getSection(lc.getSectionIndex(top));
            if (!section.hasOnlyAir() && section.maybeHas(SurveyRigBlockEntity::isOre)) {
                for (int y = top; y >= bottom; y--) {
                    for (int x = 0; x < 16; x++) {
                        for (int z = 0; z < 16; z++) {
                            int wx = minX + x, wz = minZ + z;
                            if (scanBounds != null && !scanBounds.isInside(wx, y, wz)) continue;
                            BlockState state = section.getBlockState(x, y & 15, z);
                            if (isOre(state)) e.add(state.getBlock(), record ? new BlockPos(wx, y, wz) : null);
                        }
                    }
                }
            }
            e.scanCursor = bottom - 1;
        }
        if (e.scanCursor < e.scanBottom) e.phase = SurveyLedgers.Phase.MINING;
    }

    /**
     * Swaps a few ledger ores in the world for their host rock. An ore that is gone (mined by hand, dug by an Excavator)
     * or protected by a claim comes off the ledger instead. Returns true when anything changed.
     */
    private boolean stripWorld(ServerLevel sl, SurveyLedgers.Entry e) {
        if (e.stripPos.isEmpty()) return false;
        if (!strip()) {
            e.stripPos.clear();
            e.stripKey.clear();
            return true;
        }
        boolean changed = false;
        for (int n = AutomationConfig.surveyStripPerTick(); n > 0 && !e.stripPos.isEmpty(); n--) {
            int last = e.stripPos.size() - 1;
            BlockPos pos = BlockPos.of(e.stripPos.getLong(last));
            if (!sl.isLoaded(pos)) break;
            int k = e.stripKey.getInt(last);
            e.stripPos.removeLong(last);
            e.stripKey.removeInt(last);
            changed = true;
            Block expected = k >= 0 && k < e.keys.size() ? e.keys.get(k) : Blocks.AIR;
            BlockState state = sl.getBlockState(pos);
            if (expected != Blocks.AIR && state.is(expected) && mayBreak(sl, pos, state)) {
                sl.setBlock(pos, hostFor(sl, state, pos), Block.UPDATE_CLIENTS);
            } else {
                e.take(k);
            }
        }
        return changed;
    }

    /** Rolls one ore's loot table as if mined with the card pickaxe; nothing in the world changes. */
    private void mine(ServerLevel sl, Block ore) {
        BlockState state = ore.defaultBlockState();
        ItemStack tool = tool(sl);
        boolean voiding = upgrades.level(UpgradeKind.VOID) > 0;
        for (ItemStack drop : Block.getDrops(state, sl, worldPosition, null, null, tool)) {
            if (voiding && drop.is(ExcavatorBlockEntity.VOIDABLE)) continue;
            output(drop);
        }
        int filler = AutomationConfig.surveyFillerPerOre();
        if (filler > 0) {
            BlockState host = hostFor(sl, state, worldPosition.below());
            for (int i = 0; i < filler; i++) {
                for (ItemStack drop : Block.getDrops(host, sl, worldPosition, null, null, tool)) {
                    if (voiding && drop.is(ExcavatorBlockEntity.VOIDABLE)) continue;
                    output(drop);
                }
            }
        }
        workSound(sl, worldPosition, CoreSounds.MATTER_ABSORB, 0.5F, 0.9F + sl.random.nextFloat() * 0.3F);
        sl.sendParticles(ParticleTypes.END_ROD, worldPosition.getX() + 0.5, worldPosition.getY() + 1.05, worldPosition.getZ() + 0.5,
                2, 0.15, 0.05, 0.15, 0.01);
    }

    /** A pickaxe carrying the Fortune or Silk Touch the cards stand for; used only as the loot tool. */
    private ItemStack tool(ServerLevel sl) {
        int silk = upgrades.level(UpgradeKind.SILK);
        int fortune = Upgrades.fortuneEnchantLevel(upgrades.level(UpgradeKind.FORTUNE));
        int key = silk > 0 ? 100 : fortune;
        if (key != toolKey || toolStack.isEmpty()) {
            toolKey = key;
            toolStack = new ItemStack(Items.NETHERITE_PICKAXE);
            var enchants = sl.registryAccess().lookupOrThrow(Registries.ENCHANTMENT);
            if (silk > 0) {
                toolStack.enchant(enchants.getOrThrow(Enchantments.SILK_TOUCH), 1);
            } else if (fortune > 0) {
                toolStack.enchant(enchants.getOrThrow(Enchantments.FORTUNE), fortune);
            }
        }
        return toolStack;
    }

    // ---- access: owner, team, operators ----

    public boolean canUse(Player player) {
        if (owner() == null || owner().equals(player.getUUID()) || player.hasPermissions(2)) return true;
        Team mine = player.getTeam();
        if (mine == null || !(level instanceof ServerLevel sl)) return false;
        String name = ownerName(sl.getServer());
        if (name == null) return false;
        Team theirs = level.getScoreboard().getPlayersTeam(name);
        return theirs != null && mine.isAlliedTo(theirs);
    }

    @Nullable
    private String ownerName(MinecraftServer server) {
        ServerPlayer online = server.getPlayerList().getPlayer(owner());
        if (online != null) return online.getGameProfile().getName();
        GameProfileCache cache = server.getProfileCache();
        if (cache == null) return null;
        Optional<GameProfile> profile = cache.get(owner());
        return profile.map(GameProfile::getName).filter(n -> !n.isEmpty()).orElse(null);
    }

    // ---- items, menu ----

    @Override
    public void dropContents() {
        super.dropContents();
        if (level != null) Containers.dropItemStack(level, worldPosition.getX(), worldPosition.getY(), worldPosition.getZ(), core.getStackInSlot(0));
    }

    @Nullable
    @Override
    public AbstractContainerMenu createMenu(int id, Inventory inv, Player player) {
        return new SurveyRigMenu(id, inv, this);
    }

    // ---- persistence ----

    @Override
    protected void saveExtra(CompoundTag tag, HolderLookup.Provider registries) {
        tag.put("core", core.serializeNBT(registries));
        tag.putInt("progress", progress);
        tag.putBoolean("finishedHere", finishedHere);
        tag.putInt("rigState", rigState.ordinal());
    }

    @Override
    protected void loadExtra(CompoundTag tag, HolderLookup.Provider registries) {
        if (tag.contains("core")) core.deserializeNBT(registries, tag.getCompound("core"));
        progress = tag.getInt("progress");
        finishedHere = tag.getBoolean("finishedHere");
        rigState = RigState.byOrdinal(tag.getInt("rigState"));
    }

    /** Ores of the ledger left in this rig's chunk, by block (game tests). */
    public int ledgerCount(ServerLevel sl, Block block) {
        SurveyLedgers.Entry e = SurveyLedgers.get(sl).get(ledgerChunk());
        return e == null ? 0 : e.count(block);
    }

    public List<ItemStack> bufferContents() {
        List<ItemStack> out = new java.util.ArrayList<>();
        for (int i = 0; i < buffer.getSlots(); i++) if (!buffer.getStackInSlot(i).isEmpty()) out.add(buffer.getStackInSlot(i));
        return out;
    }
}
