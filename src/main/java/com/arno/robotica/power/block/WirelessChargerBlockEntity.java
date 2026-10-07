package com.arno.robotica.power.block;

import com.arno.robotica.compat.InfoSource;
import com.arno.robotica.compat.MachineInfo;
import com.arno.robotica.compat.OwnerNames;
import com.arno.robotica.core.CoreSounds;
import com.arno.robotica.core.energy.MachineEnergyStorage;
import com.arno.robotica.core.upgrade.UpgradeKind;
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
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.MenuProvider;
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
import java.util.UUID;

/**
 * Wireless Charger: charges the FE items of players in range, worn armor first (the Exo-Frame), then the held items,
 * then the rest of the inventory, at a fixed rate per player. Only the owner and the owner's team unless the server
 * config allows everyone. Every FE that reaches an item costs {@code wirelessLoss} percent more from the buffer.
 * Range and rate come from range and speed cards. Fed by cables or a Tesla Coil link like any machine.
 *
 * <p>Players in range are collected every {@link #SCAN_INTERVAL} ticks; the list (entity ids and a state per player)
 * is synced to clients for the GUI and the arcs, only when it changed.
 */
public class WirelessChargerBlockEntity extends PowerBlockEntity implements MenuProvider, InfoSource {
    public static final int SCAN_INTERVAL = 10;
    public static final int UPGRADE_SLOTS = 2;
    /** Per player state: items took energy since the last scan / nothing to charge / not allowed / buffer empty. */
    public static final byte CHARGING = 0, IDLE = 1, DENIED = 2, NO_POWER = 3;

    /** Game tests set this to force the "charge anyone" rule; null reads the config. */
    @Nullable
    public static Boolean anyoneOverride;

    public final MachineEnergyStorage energy = new MachineEnergyStorage(PowerConfig.wirelessBuffer(), PowerConfig.wirelessInput(), 0, this::setChanged);
    public final Upgrades upgrades = new Upgrades(UPGRADE_SLOTS, java.util.EnumSet.of(UpgradeKind.SPEED, UpgradeKind.RANGE),
            kind -> kind == UpgradeKind.SPEED ? PowerConfig.wirelessSpeedCap() : PowerConfig.wirelessRangeCap(), this::setChanged);

    @Nullable
    private UUID owner;
    private String ownerName = "";

    /** Server: players in range at the last scan, and what they got since. */
    private final List<Player> targets = new ArrayList<>();
    private long[] deliveredSince = new long[0];
    private long window;
    private int fePerTick;
    private long totalDelivered;
    private long age;

    /** Synced (server and client): entity ids and states of the players in range, nearest first. */
    private int[] ids = new int[0];
    private byte[] states = new byte[0];

    public WirelessChargerBlockEntity(BlockPos pos, BlockState state) {
        super(PowerRegistry.WIRELESS_CHARGER_BE.get(), pos, state);
    }

    // ---- numbers ----

    public int range() {
        return PowerConfig.wirelessRange() + PowerConfig.wirelessRangePerCard() * upgrades.level(UpgradeKind.RANGE);
    }

    /** FE/t each player in range can get. */
    public int ratePerPlayer() {
        return (int) Math.min(Integer.MAX_VALUE, (long) PowerConfig.wirelessRate() * Upgrades.speedMultiplier(upgrades.level(UpgradeKind.SPEED)));
    }

    /** FE/t delivered into items, averaged over the last second. */
    public int fePerTick() {
        return fePerTick;
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

    /** True when at least one player took energy since the last scan. */
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

    /** Whether this charger feeds the player: the owner and the owner's team, or everyone when the config says so. */
    public boolean mayCharge(Player player) {
        boolean anyone = anyoneOverride != null ? anyoneOverride : PowerConfig.wirelessChargeAnyone();
        return anyone || Owners.allied(level, owner, ownerName, player);
    }

    // ---- tick ----

    @Override
    public void serverTick(ServerLevel level, BlockPos pos, BlockState state) {
        age++;
        if ((age + pos.asLong()) % SCAN_INTERVAL == 0) scan(level, pos);
        int rate = ratePerPlayer();
        long tickTotal = 0;
        for (int i = 0; i < targets.size(); i++) {
            Player player = targets.get(i);
            if (states[i] == DENIED || player.isRemoved() || !player.isAlive()) continue;
            long got = chargePlayer(player, rate);
            deliveredSince[i] += got;
            tickTotal += got;
        }
        window += tickTotal;
        totalDelivered += tickTotal;
        if (age % 20 == 0) {
            fePerTick = (int) Math.min(Integer.MAX_VALUE, window / 20);
            window = 0;
        }
        boolean charging = isCharging();
        setLit(charging);
        if (charging && CoreSounds.due(level, pos, 80)) CoreSounds.play(level, pos, CoreSounds.CHARGER_HUM, SoundSource.BLOCKS, 0.5F, 1.3F);
    }

    /** Rescans the players in range right away (game tests; the tick does it every {@link #SCAN_INTERVAL} ticks). */
    public void scanNow() {
        if (level instanceof ServerLevel sl) scan(sl, worldPosition);
    }

    /** State of a player in the last scan, or -1 when the player is not in range. */
    public int stateOf(Player player) {
        for (int i = 0; i < ids.length; i++) if (ids[i] == player.getId()) return states[i];
        return -1;
    }

    /** Collects the players in range (same level, not spectators), nearest first, and syncs the list when it changed. */
    private void scan(ServerLevel level, BlockPos pos) {
        // states for the players of the last scan, from what they got since
        byte[] lastStates = new byte[targets.size()];
        for (int i = 0; i < targets.size(); i++) {
            byte old = states.length > i ? states[i] : IDLE;
            lastStates[i] = old == DENIED ? DENIED : deliveredSince[i] > 0 ? CHARGING : energy.getEnergyStored() <= 0 ? NO_POWER : IDLE;
        }
        List<Player> previous = new ArrayList<>(targets);
        Vec3 center = Vec3.atCenterOf(pos);
        double r = range();
        List<Player> found = new ArrayList<>();
        for (Player player : level.getEntitiesOfClass(Player.class, new AABB(center, center).inflate(r), p -> !p.isSpectator() && p.isAlive())) {
            if (player.distanceToSqr(center) <= r * r) found.add(player);
        }
        found.sort((a, b) -> Double.compare(a.distanceToSqr(center), b.distanceToSqr(center)));
        int[] newIds = new int[found.size()];
        byte[] newStates = new byte[found.size()];
        for (int i = 0; i < found.size(); i++) {
            Player player = found.get(i);
            newIds[i] = player.getId();
            int at = previous.indexOf(player);
            if (!mayCharge(player)) newStates[i] = DENIED;
            else if (at >= 0 && lastStates[at] != DENIED) newStates[i] = lastStates[at];
            else newStates[i] = energy.getEnergyStored() <= 0 ? NO_POWER : IDLE;
            if (newStates[i] == CHARGING && (at < 0 || states.length <= at || states[at] != CHARGING) && player instanceof ServerPlayer sp) {
                // a little zap at the player when charging starts
                CoreSounds.play(level, sp.getX(), sp.getY() + 1.0, sp.getZ(), CoreSounds.SHOCK_ZAP, SoundSource.BLOCKS, 0.25F, 1.6F);
            }
        }
        targets.clear();
        targets.addAll(found);
        deliveredSince = new long[found.size()];
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

    /** Puts up to {@code max} FE into one item and takes it plus the loss from the buffer. Returns the FE the item got. */
    private int chargeItem(ItemStack stack, long max) {
        if (stack.isEmpty()) return 0;
        IEnergyStorage item = stack.getCapability(Capabilities.EnergyStorage.ITEM);
        if (item == null || !item.canReceive() || item.getEnergyStored() >= item.getMaxEnergyStored()) return 0;
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
        energy.consume((int) costOf(got, loss));
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
        return Component.translatable("block.robotica.wireless_charger");
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

    /** Range box for the renderer, so arcs still draw when the block itself is off screen. */
    public AABB renderBox() {
        return new AABB(worldPosition).inflate(range() + 2);
    }
}
