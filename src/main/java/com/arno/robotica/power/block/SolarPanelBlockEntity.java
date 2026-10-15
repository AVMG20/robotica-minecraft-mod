package com.arno.robotica.power.block;

import com.arno.robotica.core.CoreConfig;
import com.arno.robotica.core.energy.EnergyUtil;
import com.arno.robotica.core.energy.MachineEnergyStorage;
import com.arno.robotica.power.PowerConfig;
import net.minecraft.core.BlockPos;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.block.state.BlockState;

/**
 * Generates while it is day and the sky is visible (checked once per second), at {@link #RAIN_PERCENT}% while it rains or
 * thunders in a biome with rain or snow (Mekanism's rule). Pushes into its neighbours (one shared rate for all faces). Takes
 * no cards. A panel ({@link SolarArrayBlock}) has this block entity in its north-west block only: it makes its output
 * times the share of the panel's blocks that see the sky and pushes into the blocks around the whole panel.
 */
public class SolarPanelBlockEntity extends PowerBlockEntity implements net.minecraft.world.MenuProvider, com.arno.robotica.power.menu.EnergyInfoMenu.Source, com.arno.robotica.compat.InfoSource {
    public final MachineEnergyStorage energy;
    private final SolarPanelBlock.Tier tier;
    private final int pushRate;
    /** Output in rain or thunder, percent of the daylight output. */
    public static final int RAIN_PERCENT = 20;
    /** Daylight FE/t of an old single Mk1 (one block, saved before panels covered 3x3), before the generation multiplier. */
    public static final int OLD_MK1_OUTPUT = 20;
    private boolean sunny;
    private boolean raining;
    private boolean openSky;
    /** Blocks of the panel that see the sky (all of them for an old single panel that sees it). */
    private int openCells;
    /** One block on its own (an old single panel, or a block a mod moved alone): pushes into its own neighbours. */
    private boolean standalone;
    /** An old single panel ({@link SolarArrayBlock#SINGLE}). */
    private boolean single;
    /** Every block of the square has been there at once. A formed panel that loses a block removes itself. */
    private boolean formed;
    /**
     * Saved by an older version and not yet brought to the 3x3 layout ({@link SolarArrayBlock#migrate}): its old width
     * (4 for a 0.9.7 Mk4, 2 for a 0.9.7 Mk2, 1 for a one-block panel), 0 when done.
     */
    private int oldSize;
    /** The sky has been checked since the block entity loaded. */
    private boolean checked;
    private final com.arno.robotica.core.energy.EnergyNeighbors neighbors = new com.arno.robotica.core.energy.EnergyNeighbors();
    /** Cached FE neighbours around the panel, one per block and face that faces out of it. */
    @org.jetbrains.annotations.Nullable
    private net.neoforged.neoforge.capabilities.BlockCapabilityCache<net.neoforged.neoforge.energy.IEnergyStorage, net.minecraft.core.Direction>[] around;
    @org.jetbrains.annotations.Nullable
    private net.neoforged.neoforge.energy.IEnergyStorage[] targets;

    private int currentOutput() {
        if (!sunny) return 0;
        int cells = SolarArrayBlock.SIZE * SolarArrayBlock.SIZE;
        int base = single && tier == SolarPanelBlock.Tier.MK1 ? Math.min(tier.output(), OLD_MK1_OUTPUT) : tier.output();
        return weatherOutput((int) ((long) CoreConfig.scaleGeneration(base) * openCells / cells), raining);
    }

    /** The daylight output {@code out}, cut to {@link #RAIN_PERCENT}% in rain (at least 1 FE/t). */
    public static int weatherOutput(int out, boolean rain) {
        return rain ? Math.max(1, out * RAIN_PERCENT / 100) : out;
    }

    public SolarPanelBlockEntity(BlockPos pos, BlockState state) {
        super(typeFor(state), pos, state);
        this.tier = ((SolarPanelBlock) state.getBlock()).tier();
        this.pushRate = Math.max(64, tier.output() * 8);
        this.energy = new MachineEnergyStorage(tier.buffer, 0, pushRate, this::setChanged);
    }

    private static net.minecraft.world.level.block.entity.BlockEntityType<?> typeFor(BlockState state) {
        return SolarPanelBlock.typeFor(((SolarPanelBlock) state.getBlock()).tier());
    }

    public boolean isGenerating() {
        return sunny;
    }

    public boolean formed() {
        return formed;
    }

    /** Blocks of the panel that saw the sky at the last check. */
    public int openCells() {
        return openCells;
    }

    @Override
    public int stored() {
        return energy.getEnergyStored();
    }

    @Override
    public int capacity() {
        return energy.getMaxEnergyStored();
    }

    @Override
    public int rate() {
        return currentOutput();
    }

    @Override
    public int maxIo() {
        return pushRate;
    }

    @Override
    public int flag() {
        return !sunny ? 0 : raining ? 2 : 1;
    }

    @Override
    public net.minecraft.network.chat.Component getDisplayName() {
        return getBlockState().getBlock().getName();
    }

    @Override
    public net.minecraft.world.inventory.AbstractContainerMenu createMenu(int id, net.minecraft.world.entity.player.Inventory inv, net.minecraft.world.entity.player.Player player) {
        return new com.arno.robotica.power.menu.SolarPanelMenu(id, inv, this);
    }

    @Override
    public void serverTick(ServerLevel level, BlockPos pos, BlockState state) {
        if (!checked || (level.getGameTime() + pos.asLong()) % 20 == 0) {
            checked = true;
            int open = countOpenCells(level, pos);
            if (open < 0) return;   // the panel removed itself
            openCells = level.dimensionType().hasFixedTime() ? 0 : open;
            openSky = openCells > 0;
            // Daylight from the sun's angle (the clear-weather isDay() edge): isDay() itself turns false in thunderstorms.
            sunny = openSky && net.minecraft.util.Mth.cos(level.getTimeOfDay(1.0F) * net.minecraft.util.Mth.TWO_PI) > 0.0682F;
            raining = sunny && level.isRaining() && level.getBiome(pos).value().getPrecipitationAt(pos) != net.minecraft.world.level.biome.Biome.Precipitation.NONE;
        }
        int gen = currentOutput();
        if (gen > 0) energy.generate(gen);
        if (energy.getEnergyStored() <= 0) return;
        if (standalone) EnergyUtil.pushToNeighbors(level, pos, energy, pushRate, neighbors);
        else pushAround(level, pos);
    }

    /**
     * Blocks of the panel that see the sky, or -1 when the panel removed itself. Blocks in unloaded chunks count as open.
     * An old single panel counts all its blocks when it sees the sky. A block standing alone (moved on its own by another
     * mod) grows into its square when the square is empty, else only its own block counts. A formed panel that lost a
     * block, or whose block entity is no longer in the north-west block (rotated in place), removes itself without drops.
     */
    private int countOpenCells(ServerLevel level, BlockPos pos) {
        if (!(getBlockState().getBlock() instanceof SolarArrayBlock array)) return 0;
        if (oldSize > 0 && array.migrate(level, pos, oldSize)) {
            oldSize = 0;
            setChanged();
        }
        BlockState state = getBlockState();
        int n = SolarArrayBlock.SIZE;
        single = state.getValue(SolarArrayBlock.SINGLE);
        if (single) {
            standalone = true;
            return level.canSeeSky(pos.above()) ? n * n : 0;
        }
        if (!array.isCell(state, 0, 0)) {
            array.removePanel(level, pos);
            return -1;
        }
        int open = 0, present = 0;
        BlockPos.MutableBlockPos p = new BlockPos.MutableBlockPos();
        for (int x = 0; x < n; x++) {
            for (int z = 0; z < n; z++) {
                p.set(pos.getX() + x, pos.getY(), pos.getZ() + z);
                if (!level.isLoaded(p)) {
                    open++;
                    present++;
                } else if (array.isCell(level.getBlockState(p), x, z)) {
                    present++;
                    if (level.canSeeSky(p.move(net.minecraft.core.Direction.UP))) open++;
                }
            }
        }
        if (present == n * n) {
            standalone = false;
            if (!formed) {
                formed = true;
                setChanged();
            }
            return open;
        }
        if (formed && present > 1) {
            array.removePanel(level, pos);
            return -1;
        }
        if (formed) {
            // Only its own block left (moved on its own by another mod): grow again.
            formed = false;
            setChanged();
        }
        standalone = present == 1;
        if (standalone && array.grow(level, pos)) return countOpenCells(level, pos);
        return open;
    }

    /** {@link EnergyUtil#pushToNeighbors} for a panel: into the receivers around the whole panel, one shared rate. */
    @SuppressWarnings("unchecked")
    private void pushAround(ServerLevel level, BlockPos pos) {
        int n = SolarArrayBlock.SIZE;
        if (around == null) {
            java.util.List<net.neoforged.neoforge.capabilities.BlockCapabilityCache<net.neoforged.neoforge.energy.IEnergyStorage, net.minecraft.core.Direction>> list = new java.util.ArrayList<>();
            for (int x = 0; x < n; x++) {
                for (int z = 0; z < n; z++) {
                    BlockPos cell = pos.offset(x, 0, z);
                    for (net.minecraft.core.Direction dir : net.minecraft.core.Direction.values()) {
                        int nx = x + dir.getStepX(), nz = z + dir.getStepZ();
                        if (dir.getAxis().isHorizontal() && nx >= 0 && nx < n && nz >= 0 && nz < n) continue;   // inside the panel
                        list.add(net.neoforged.neoforge.capabilities.BlockCapabilityCache.create(
                                net.neoforged.neoforge.capabilities.Capabilities.EnergyStorage.BLOCK, level, cell.relative(dir), dir.getOpposite()));
                    }
                }
            }
            around = list.toArray(new net.neoforged.neoforge.capabilities.BlockCapabilityCache[0]);
            targets = new net.neoforged.neoforge.energy.IEnergyStorage[around.length];
        }
        int count = 0;
        int start = (int) Math.floorMod(level.getGameTime(), (long) around.length);
        for (int i = 0; i < around.length; i++) {
            net.neoforged.neoforge.energy.IEnergyStorage target = around[(start + i) % around.length].getCapability();
            if (target != null && target.canReceive()) targets[count++] = target;
        }
        EnergyUtil.pushTo(targets, count, energy, pushRate);
    }

    @Override
    protected void saveAdditional(CompoundTag tag, HolderLookup.Provider registries) {
        super.saveAdditional(tag, registries);
        tag.put("energy", energy.serializeNBT(registries));
        if (formed) tag.putBoolean("formed", true);
        tag.putInt("size", SolarArrayBlock.SIZE);
        if (oldSize > 0) tag.putInt("old_size", oldSize);
    }

    @Override
    protected void loadAdditional(CompoundTag tag, HolderLookup.Provider registries) {
        super.loadAdditional(tag, registries);
        if (tag.contains("energy")) energy.deserializeNBT(registries, tag.get("energy"));
        formed = tag.getBoolean("formed");
        if (tag.contains("size")) {
            oldSize = tag.getInt("old_size");
            return;
        }
        // Saved by 0.9.7 ("square": Mk2-Mk4 squares of 2x2 to 4x4) or older (one block per panel). A 0.9.7 square, formed
        // or not (migrate leaves a lone block alone), or one grown from an old single block, had the 0.9.7 width of its
        // Mk; anything else was a one-block panel.
        int width = tier.ordinal() + 1;
        if (tier != SolarPanelBlock.Tier.MK1 && (formed || tag.contains("square"))) oldSize = width == SolarArrayBlock.SIZE ? 0 : width;
        else oldSize = 1;
        if (oldSize > 0) formed = false;
    }

    @Override
    public void onLoad() {
        super.onLoad();
        invalidateCells();
    }

    @Override
    public void setRemoved() {
        super.setRemoved();
        invalidateCells();
    }

    @Override
    public void onChunkUnloaded() {
        super.onChunkUnloaded();
        invalidateCells();
    }

    /** The other blocks of the panel hand out this buffer, so their capability changes with this block entity. */
    private void invalidateCells() {
        if (level == null || level.isClientSide) return;
        BlockPos.MutableBlockPos p = new BlockPos.MutableBlockPos();
        for (int x = 0; x < SolarArrayBlock.SIZE; x++) {
            for (int z = 0; z < SolarArrayBlock.SIZE; z++) {
                if (x != 0 || z != 0) level.invalidateCapabilities(p.set(worldPosition.getX() + x, worldPosition.getY(), worldPosition.getZ() + z));
            }
        }
    }

    /** Jade: generating, generating into a full buffer, or idle (night, no sky). */
    @Override
    public void collectInfo(ServerLevel level, com.arno.robotica.compat.MachineInfo info) {
        if (!sunny) info.status = "idle";
        else info.status = energy.getEnergyStored() >= energy.getMaxEnergyStored() ? "output_full" : "working";
    }
}
