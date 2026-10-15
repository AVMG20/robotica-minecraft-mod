package com.arno.robotica.power.block;

import com.arno.robotica.compat.InfoSource;
import com.arno.robotica.compat.MachineInfo;
import com.arno.robotica.compat.OwnerNames;
import com.arno.robotica.core.CoreSounds;
import com.arno.robotica.core.energy.MachineEnergyStorage;
import com.arno.robotica.core.upgrade.UpgradeKind;
import com.arno.robotica.core.upgrade.UpgradeRules;
import com.arno.robotica.core.upgrade.Upgrades;
import com.arno.robotica.power.PowerConfig;
import com.arno.robotica.power.PowerRegistry;
import com.arno.robotica.power.menu.WirelessChargerMenu;
import com.arno.robotica.power.util.Owners;
import net.minecraft.core.BlockPos;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.MenuProvider;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.capabilities.Capabilities;
import net.neoforged.neoforge.energy.IEnergyStorage;
import net.neoforged.neoforge.items.IItemHandler;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Set;
import java.util.UUID;

/**
 * Wireless Charger: charges the FE items of players in range, worn armor first (the Exo-Frame), then the held items,
 * then the rest of the inventory, at its Mk's rate per player. Entities with an FE capability (Stumpy, Sprout, the
 * Rancher, drones) in range are charged at the same rate. Every player and robot in range is charged; only the owner, their
 * team or an operator may open it. Every FE that reaches an item costs {@code wirelessLoss} percent more from the buffer.
 * Range and rate come from range and speed cards. Fed by cables or a Tesla link like any machine.
 *
 * <p>Players and robots in range are collected every {@link #SCAN_INTERVAL} ticks; the list (entity ids and a state each)
 * is synced to clients for the GUI and the particles, only when it changed.
 */
public class WirelessChargerBlockEntity extends PowerBlockEntity implements MenuProvider, InfoSource {
    public static final int SCAN_INTERVAL = 10;
    /** Speed and range cards; slots and caps follow the Mk rule. */
    public static final Set<UpgradeKind> KINDS = Set.of(UpgradeKind.SPEED, UpgradeKind.RANGE);
    public static final int UPGRADE_SLOTS = UpgradeRules.kindSlots(KINDS);
    /** Per target state: took energy since the last scan / nothing to charge / buffer empty. */
    public static final byte CHARGING = 0, IDLE = 1, NO_POWER = 2;

    /** The buffer holds this many ticks of the base rate, and takes this many times the base rate per tick. */
    public static final int BUFFER_TICKS = 200, INPUT_PLAYERS = 20;

    public final MachineEnergyStorage energy;
    public final Upgrades upgrades = Upgrades.forMk(this::mk, KINDS, this::setChanged);

    @Nullable
    private UUID owner;
    private String ownerName = "";

    /** Server: players and robots in range at the last scan, the robots' FE storage (null for a player), and what they got since. */
    private final List<Entity> targets = new ArrayList<>();
    private final List<IEnergyStorage> storages = new ArrayList<>();
    private long[] deliveredSince = new long[0];
    /** Targets that took nothing this scan window (all full or none): skipped until the next scan. */
    private boolean[] satisfied = new boolean[0];
    private long window;
    private int fePerTick;
    /** FE taken from the buffer (delivered plus loss) this second, and its last per-tick average. */
    private long drawWindow;
    private int drawnPerTick;
    private long totalDelivered;
    private long age;

    /** Synced (server and client): entity ids and states of the players and robots in range, nearest first. */
    private int[] ids = new int[0];
    private byte[] states = new byte[0];

    private final int mk;

    public WirelessChargerBlockEntity(BlockPos pos, BlockState state) {
        super(PowerRegistry.WIRELESS_CHARGER_BE.get(), pos, state);
        mk = state.getBlock() instanceof WirelessChargerBlock b ? b.mk() : 1;
        long rate = PowerConfig.wirelessRate(mk);
        energy = new MachineEnergyStorage((int) Math.min(Integer.MAX_VALUE, rate * BUFFER_TICKS),
                (int) Math.min(Integer.MAX_VALUE, rate * INPUT_PLAYERS), 0, this::setChanged);
    }

    // ---- numbers ----

    /** 1-4 */
    public int mk() {
        return mk;
    }

    public int range() {
        return PowerConfig.wirelessRange(mk) + PowerConfig.wirelessRangePerCard() * upgrades.level(UpgradeKind.RANGE);
    }

    /** FE/t each player or robot in range can get. */
    public int ratePerPlayer() {
        return (int) Math.min(Integer.MAX_VALUE, (long) PowerConfig.wirelessRate(mk) * Upgrades.speedMultiplier(upgrades.level(UpgradeKind.SPEED)));
    }

    /** FE/t delivered into items, averaged over the last second. */
    public int fePerTick() {
        return fePerTick;
    }

    /** FE/t taken from the buffer (delivered plus the loss), averaged over the last second. */
    public int drawnPerTick() {
        return drawnPerTick;
    }

    /** FE that reached items since the block was placed (for tests and accounting). */
    public long totalDelivered() {
        return totalDelivered;
    }

    public int[] targetIds() {
        return ids;
    }

    public byte[] targetStates() {
        return states;
    }

    /** True when at least one player or robot took energy since the last scan. */
    public boolean isCharging() {
        for (byte s : states) if (s == CHARGING) return true;
        return false;
    }

    // ---- owner ----

    public void setOwner(Player player) {
        owner = player.getUUID();
        ownerName = player.getGameProfile().getName();
        setChanged();
    }

    @Nullable
    public UUID owner() {
        return owner;
    }

    /** Owner, the owner's team or an operator may open it and change its cards. Without an owner: everyone. */
    public boolean canUse(Player player) {
        return player.hasPermissions(2) || Owners.allied(level, owner, ownerName, player);
    }

    // ---- tick ----

    @Override
    public void serverTick(ServerLevel level, BlockPos pos, BlockState state) {
        age++;
        if ((age + pos.asLong()) % SCAN_INTERVAL == 0) scan(level, pos);
        int rate = ratePerPlayer();
        long tickTotal = 0;
        // an empty buffer charges nothing: skip the item lookups
        for (int i = 0; i < targets.size() && energy.getEnergyStored() > 0; i++) {
            Entity target = targets.get(i);
            if (satisfied[i] || target.isRemoved() || !target.isAlive()) continue;
            IEnergyStorage storage = storages.get(i);
            long got = storage != null ? charge(storage, rate) : target instanceof Player player ? chargePlayer(player, rate) : 0;
            if (got <= 0) satisfied[i] = true;
            deliveredSince[i] += got;
            tickTotal += got;
        }
        window += tickTotal;
        totalDelivered += tickTotal;
        if (age % 20 == 0) {
            fePerTick = (int) Math.min(Integer.MAX_VALUE, window / 20);
            drawnPerTick = (int) Math.min(Integer.MAX_VALUE, drawWindow / 20);
            window = 0;
            drawWindow = 0;
        }
        boolean charging = isCharging();
        setLit(charging);
        if (charging && CoreSounds.due(level, pos, 80)) CoreSounds.play(level, pos, CoreSounds.CHARGER_HUM, SoundSource.BLOCKS, 0.5F, 1.3F);
    }

    /** Rescans the players and robots in range right away (game tests; the tick does it every {@link #SCAN_INTERVAL} ticks). */
    public void scanNow() {
        if (level instanceof ServerLevel sl) scan(sl, worldPosition);
    }

    /** State of a player or robot in the last scan, or -1 when it is not in range. */
    public int stateOf(Entity target) {
        for (int i = 0; i < ids.length; i++) if (ids[i] == target.getId()) return states[i];
        return -1;
    }

    /**
     * Collects the players (same level, not spectators) and the entities with an FE capability in range, nearest first,
     * and syncs the list when it changed. The entity lookup only sees loaded chunks.
     */
    private void scan(ServerLevel level, BlockPos pos) {
        // states for the players of the last scan, from what they got since
        byte[] lastStates = new byte[targets.size()];
        for (int i = 0; i < targets.size(); i++) {
            lastStates[i] = deliveredSince[i] > 0 ? CHARGING : energy.getEnergyStored() <= 0 ? NO_POWER : IDLE;
        }
        List<Entity> previous = new ArrayList<>(targets);
        Vec3 center = Vec3.atCenterOf(pos);
        double r = range();
        List<Entity> found = new ArrayList<>();
        // the level's player list, not an entity section lookup: a player just moved into a chunk whose entities are
        // not loaded yet would be missed there
        for (Player player : level.players()) {
            if (!player.isSpectator() && player.isAlive() && player.distanceToSqr(center) <= r * r) found.add(player);
        }
        for (Entity entity : level.getEntities((Entity) null, new AABB(center, center).inflate(r),
                e -> !(e instanceof Player) && e.isAlive() && e.distanceToSqr(center) <= r * r)) {
            IEnergyStorage storage = entity.getCapability(Capabilities.EnergyStorage.ENTITY, null);
            if (storage != null && storage.canReceive()) found.add(entity);
        }
        found.sort((a, b) -> Double.compare(a.distanceToSqr(center), b.distanceToSqr(center)));
        int[] newIds = new int[found.size()];
        byte[] newStates = new byte[found.size()];
        storages.clear();
        for (int i = 0; i < found.size(); i++) {
            Entity target = found.get(i);
            newIds[i] = target.getId();
            storages.add(target instanceof Player ? null : target.getCapability(Capabilities.EnergyStorage.ENTITY, null));
            int at = previous.indexOf(target);
            if (at >= 0) newStates[i] = lastStates[at];
            else newStates[i] = energy.getEnergyStored() <= 0 ? NO_POWER : IDLE;
        }
        targets.clear();
        targets.addAll(found);
        deliveredSince = new long[found.size()];
        satisfied = new boolean[found.size()];
        boolean changed = !Arrays.equals(newIds, ids) || !Arrays.equals(newStates, states);
        ids = newIds;
        states = newStates;
        if (changed) setChangedAndSync();
    }

    /**
     * Charges one player's items within {@code budget} FE: worn armor (the Exo-Frame) first, then the main and off
     * hand, then the rest of the inventory. Returns the FE the items got; the buffer pays that plus the loss.
     */
    public long chargePlayer(Player player, int budget) {
        long left = budget;
        Inventory inv = player.getInventory();
        for (ItemStack stack : inv.armor) {
            if (left <= 0) break;
            left -= chargeItem(stack, left);
        }
        if (left > 0) left -= chargeItem(player.getMainHandItem(), left);
        if (left > 0) left -= chargeItem(player.getOffhandItem(), left);
        for (int slot = 0; slot < inv.items.size() && left > 0; slot++) {
            if (slot == inv.selected) continue;
            left -= chargeItem(inv.items.get(slot), left);
        }
        return budget - left;
    }

    private int chargeItem(ItemStack stack, long max) {
        if (stack.isEmpty()) return 0;
        IEnergyStorage item = stack.getCapability(Capabilities.EnergyStorage.ITEM);
        return item == null ? 0 : charge(item, max);
    }

    /** Puts up to {@code max} FE into an item or robot and takes it plus the loss from the buffer. Returns the FE it got. */
    private int charge(IEnergyStorage item, long max) {
        if (!item.canReceive() || item.getEnergyStored() >= item.getMaxEnergyStored()) return 0;
        int loss = PowerConfig.wirelessLoss();
        long stored = energy.getEnergyStored();
        long affordable = stored * 100L / (100L + loss);
        while (affordable > 0 && costOf(affordable, loss) > stored) affordable--;
        int offer = (int) Math.min(Math.min(max, affordable), Integer.MAX_VALUE);
        if (offer <= 0) return 0;
        // the item says what it takes first, then gets exactly that: never more than the buffer can pay for, loss included
        int accepted = Math.min(offer, item.receiveEnergy(offer, true));
        if (accepted <= 0) return 0;
        int got = Math.min(accepted, item.receiveEnergy(accepted, false));
        if (got <= 0) return 0;
        int cost = (int) costOf(got, loss);
        energy.consume(cost);
        drawWindow += cost;
        return got;
    }

    /** FE drawn from the buffer for {@code delivered} FE that reached items. */
    public static long costOf(long delivered, int lossPercent) {
        return delivered + (delivered * lossPercent + 99) / 100;
    }

    // ---- block entity plumbing ----

    @Override
    public IItemHandler quickUpgrades() {
        return upgrades;
    }

    @Override
    public void dropContents(Level level, BlockPos pos) {
        drop(level, pos, upgrades);
    }

    @Override
    public Component getDisplayName() {
        return getBlockState().getBlock().getName();
    }

    @Nullable
    @Override
    public AbstractContainerMenu createMenu(int id, Inventory inv, Player player) {
        return new WirelessChargerMenu(id, inv, this);
    }

    @Override
    public void collectInfo(ServerLevel level, MachineInfo info) {
        info.status = isCharging() ? "working" : energy.getEnergyStored() <= 0 && ids.length > 0 ? "no_energy" : "idle";
        info.owner = owner == null ? null : OwnerNames.name(level.getServer(), owner);
    }

    @Override
    protected void saveAdditional(CompoundTag tag, HolderLookup.Provider registries) {
        super.saveAdditional(tag, registries);
        tag.put("energy", energy.serializeNBT(registries));
        tag.put("upgrades", upgrades.serializeNBT(registries));
        if (owner != null) {
            tag.putUUID("owner", owner);
            tag.putString("ownerName", ownerName);
        }
    }

    @Override
    protected void loadAdditional(CompoundTag tag, HolderLookup.Provider registries) {
        super.loadAdditional(tag, registries);
        if (tag.contains("energy")) energy.deserializeNBT(registries, tag.get("energy"));
        if (tag.contains("upgrades")) upgrades.deserializeNBT(registries, tag.getCompound("upgrades"));
        if (tag.hasUUID("owner")) owner = tag.getUUID("owner");
        if (tag.contains("ownerName")) ownerName = tag.getString("ownerName");
        if (tag.contains("targetIds")) {
            ids = tag.getIntArray("targetIds");
            states = tag.getByteArray("targetStates");
            if (states.length != ids.length) states = new byte[ids.length];
        }
    }

    @Override
    protected void saveClientData(CompoundTag tag, HolderLookup.Provider registries) {
        tag.putIntArray("targetIds", ids);
        tag.putByteArray("targetStates", states);
    }
}
