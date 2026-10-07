package com.arno.robotica.automation.entity;

import com.arno.robotica.automation.AutomationConfig;
import com.arno.robotica.automation.AutomationContent;
import com.arno.robotica.automation.menu.SurveyRigMenu;
import com.arno.robotica.core.CoreConfig;
import com.arno.robotica.core.CoreSounds;
import com.arno.robotica.core.item.CoreItems;
import com.arno.robotica.core.upgrade.UpgradeKind;
import com.arno.robotica.core.upgrade.Upgrades;
import net.minecraft.core.BlockPos;
import net.minecraft.core.HolderLookup;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.Containers;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.enchantment.Enchantments;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
import net.neoforged.neoforge.items.ItemStackHandler;
import org.jetbrains.annotations.Nullable;

import java.util.EnumSet;
import java.util.List;
import java.util.Set;

/**
 * Survey Rig: placed once and powered, it slowly turns a lot of FE into random ores. Every {@link #actionInterval}
 * ticks it rolls one ore kind of {@link SurveyOrePool} by weight (coal and iron often, diamonds rarely, ancient debris
 * only with a Magma Core) and rolls that ore's loot table with a pickaxe carrying the Fortune or Silk Touch of its cards.
 * The world is never touched. Speed cards cut the interval but raise the FE per tick steeply (speed x per-ore factor).
 */
public class SurveyRigBlockEntity extends AreaWorkerBlockEntity {
    public static final Set<UpgradeKind> KINDS = EnumSet.of(UpgradeKind.SPEED, UpgradeKind.EFFICIENCY, UpgradeKind.FORTUNE,
            UpgradeKind.SILK, UpgradeKind.VOID);

    /** What the rig is doing, for the GUI. */
    public enum RigState {
        WAITING, MINING, NO_ORES;

        public static RigState byOrdinal(int i) {
            RigState[] v = values();
            return v[Math.max(0, Math.min(v.length - 1, i))];
        }
    }

    /** Magma Core slot: lets the rig make ancient debris (and the other core ores of the config). Never consumed. */
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
    private int progress;
    /** Registry id of the last ore made, -1 for none (GUI). */
    private int lastOre = -1;
    private int toolKey = -1;
    private ItemStack toolStack = ItemStack.EMPTY;

    public SurveyRigBlockEntity(BlockPos pos, BlockState state) {
        super(AutomationContent.SURVEY_RIG_BE.get(), pos, state, KINDS,
                (int) Math.min(Integer.MAX_VALUE, (long) AutomationConfig.surveyEnergyBuffer() * tierOf(state)),
                (int) Math.min(Integer.MAX_VALUE, (long) AutomationConfig.surveyMaxInput() * tierOf(state)));
    }

    @Override
    public String blockKey() {
        return getBlockState().getBlock().getDescriptionId();
    }

    /** Percent more weight this Mk gives rare ore kinds. */
    public int rareBonus() {
        return AutomationConfig.surveyRareBonus(tier());
    }

    @Override
    public void collectInfo(ServerLevel level, com.arno.robotica.compat.MachineInfo info) {
        super.collectInfo(level, info);
        if (lastOre() != null) info.lastOre = BuiltInRegistries.ITEM.getKey(lastOre()).toString();
    }

    // ---- no area: the rig works in place ----

    @Override
    protected void recalc() {
        areaSize = 1;
        toolKey = -1;
    }

    @Override
    protected int areaMinY() {
        return worldPosition.getY();
    }

    @Override
    protected int areaMaxY() {
        return worldPosition.getY() + 1;
    }

    @Override
    protected int batteryPullRate() {
        return 20_000;
    }

    // ---- numbers ----

    public RigState rigState() {
        return rigState;
    }

    public int progressTicks() {
        return progress;
    }

    @Nullable
    public Item lastOre() {
        return lastOre < 0 ? null : BuiltInRegistries.ITEM.byId(lastOre);
    }

    public int lastOreId() {
        return lastOre;
    }

    @Override
    public int guiProgress() {
        int interval = actionInterval();
        return interval <= 0 ? 0 : (int) Math.min(100, 100L * progress / interval);
    }

    public int actionInterval() {
        long base = (long) CoreConfig.scaleInterval(AutomationConfig.surveyInterval()) * 100 / AutomationConfig.surveySpeed(tier());
        return (int) Math.max(1, base / Upgrades.speedMultiplier(upgrades.level(UpgradeKind.SPEED)));
    }

    /**
     * FE per tick while working: base x speed multiplier x the steep per-ore factor, so each speed card costs more than
     * the one before (1 card x3.5, 2 x9, 4 x42, 8 x420 FE/t). Efficiency cards take 15% each off the factor.
     */
    public int energyPerTick() {
        int speed = upgrades.level(UpgradeKind.SPEED);
        int eff = upgrades.level(UpgradeKind.EFFICIENCY);
        double value = CoreConfig.scaleEnergy(AutomationConfig.surveyFePerTick()) * (AutomationConfig.surveySpeed(tier()) / 100.0)
                * Upgrades.speedMultiplier(speed) * Upgrades.steepEnergyMultiplier(speed, eff);
        return (int) Math.min(Integer.MAX_VALUE, Math.round(value));
    }

    /** FE one ore costs at the current cards. */
    public long energyPerOre() {
        return (long) energyPerTick() * actionInterval();
    }

    public boolean hasCore() {
        return !core.getStackInSlot(0).isEmpty();
    }

    private void setRigState(RigState next) {
        if (next != rigState) {
            rigState = next;
            setChanged();
        }
    }

    // ---- work loop ----

    @Override
    protected Status work(ServerLevel sl) {
        SurveyOrePool pool = SurveyOrePool.get();
        boolean core = hasCore();
        if (pool.totalWeight(core) <= 0) {
            setRigState(RigState.NO_ORES);
            return Status.IDLE;
        }
        int cost = energyPerTick();
        if (energy.getEnergyStored() < cost) {
            setRigState(RigState.WAITING);
            return Status.NO_ENERGY;
        }
        energy.consume(cost);
        setRigState(RigState.MINING);
        if (++progress < actionInterval()) {
            if (CoreSounds.due(sl, worldPosition, 60)) CoreSounds.play(sl, worldPosition, CoreSounds.DRILL_GRIND, SoundSource.BLOCKS, 0.35F, 1.3F);
            return Status.WORKING;
        }
        progress = 0;
        Item ore = pool.roll(sl.random, core, rareBonus(), AutomationConfig.surveyRareWeight());
        if (ore != null) mine(sl, ore);
        setChanged();
        return Status.WORKING;
    }

    /** Rolls the ore's loot table as if mined with the card pickaxe (an ore item without a block comes out as is). */
    private void mine(ServerLevel sl, Item ore) {
        lastOre = BuiltInRegistries.ITEM.getId(ore);
        boolean voiding = upgrades.level(UpgradeKind.VOID) > 0;
        List<ItemStack> drops = List.of(new ItemStack(ore));
        if (ore instanceof BlockItem blockItem) {
            List<ItemStack> rolled = Block.getDrops(blockItem.getBlock().defaultBlockState(), sl, worldPosition, null, null, tool(sl));
            if (!rolled.isEmpty()) drops = rolled;
        }
        for (ItemStack drop : drops) {
            if (voiding && drop.is(ExcavatorBlockEntity.VOIDABLE)) continue;
            output(drop);
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
        if (lastOre >= 0) tag.putString("lastOre", BuiltInRegistries.ITEM.getKey(BuiltInRegistries.ITEM.byId(lastOre)).toString());
    }

    @Override
    protected void loadExtra(CompoundTag tag, HolderLookup.Provider registries) {
        if (tag.contains("core")) core.deserializeNBT(registries, tag.getCompound("core"));
        progress = Math.max(0, tag.getInt("progress"));
        lastOre = -1;
        if (tag.contains("lastOre")) {
            net.minecraft.resources.ResourceLocation id = net.minecraft.resources.ResourceLocation.tryParse(tag.getString("lastOre"));
            if (id != null && BuiltInRegistries.ITEM.containsKey(id)) lastOre = BuiltInRegistries.ITEM.getId(BuiltInRegistries.ITEM.get(id));
        }
        rigState = RigState.WAITING;
    }

    public List<ItemStack> bufferContents() {
        List<ItemStack> out = new java.util.ArrayList<>();
        for (int i = 0; i < buffer.getSlots(); i++) if (!buffer.getStackInSlot(i).isEmpty()) out.add(buffer.getStackInSlot(i));
        return out;
    }
}
