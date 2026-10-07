package com.arno.robotica.drones.entity;

import com.arno.robotica.core.CoreConfig;
import com.arno.robotica.core.CoreSounds;
import com.arno.robotica.core.upgrade.UpgradeKind;
import com.arno.robotica.core.upgrade.Upgrades;
import com.arno.robotica.drones.DronesConfig;
import com.arno.robotica.drones.DronesRegistry;
import com.arno.robotica.drones.item.CourierRemoteItem;
import com.arno.robotica.drones.menu.CourierDroneMenu;
import com.mojang.authlib.GameProfile;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.syncher.EntityDataAccessor;
import net.minecraft.network.syncher.EntityDataSerializers;
import net.minecraft.network.syncher.SynchedEntityData;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.Containers;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.SimpleMenuProvider;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.ai.attributes.AttributeSupplier;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.capabilities.Capabilities;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.common.util.FakePlayer;
import net.neoforged.neoforge.common.util.FakePlayerFactory;
import net.neoforged.neoforge.common.util.TriState;
import net.neoforged.neoforge.event.entity.player.PlayerInteractEvent;
import net.neoforged.neoforge.items.IItemHandler;
import net.neoforged.neoforge.items.ItemHandlerHelper;
import net.neoforged.neoforge.items.ItemStackHandler;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.EnumSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;

/**
 * Courier Drone: flies up to four routes between linked inventories and carries one stack per trip (three on Mk2). A
 * 9-slot ghost filter (whitelist or blacklist, optionally matching item tags) decides what it takes. It reaches inventories
 * through the item handler capability of the linked face, asks the owner's fake player whether the container may be used
 * (claim mods veto through the interaction event), and never loads chunks: a route in an unloaded chunk is skipped.
 */
public class CourierDrone extends DroneBase {
    private static final EntityDataAccessor<CompoundTag> DATA_ROUTES = SynchedEntityData.defineId(CourierDrone.class, EntityDataSerializers.COMPOUND_TAG);
    private static final UUID FALLBACK_OWNER = UUID.fromString("5a0b6c52-3f2b-4a39-9d5e-0d6f5f0e7a11");
    public static final int FILTER_SLOTS = 9;

    /** Ids are synced to the GUI, never reorder. */
    public enum State {
        IDLE, PICKUP, DELIVER, BACK, LOW;

        public static State byId(int id) {
            State[] all = values();
            return all[Math.floorMod(id, all.length)];
        }
    }

    public final ItemStackHandler filter = new ItemStackHandler(FILTER_SLOTS);
    public final ItemStackHandler cargo = new ItemStackHandler(3) {
        @Override
        public boolean isItemValid(int slot, ItemStack stack) {
            return slot < capacityStacks();
        }
    };
    public final Upgrades upgrades = Upgrades.fixed(com.arno.robotica.core.upgrade.UpgradeRules.Fixed.COURIER_DRONE, () -> {});

    private List<CourierRoute> routes = new ArrayList<>();
    private boolean whitelist = true;
    private boolean matchTags;
    private int routeIdx;
    @Nullable
    private CourierRoute current;
    private int scanCooldown;
    private int phaseTicks;
    private boolean lowNotified;
    /** Completed deliveries since creation or load (tests, stats). */
    public int trips;

    public CourierDrone(EntityType<? extends CourierDrone> type, Level level) {
        super(type, level);
    }

    public static AttributeSupplier.Builder createAttributes() {
        return createBaseAttributes();
    }

    @Override
    protected void defineSynchedData(SynchedEntityData.Builder builder) {
        super.defineSynchedData(builder);
        builder.define(DATA_ROUTES, new CompoundTag());
    }

    @Override
    public DronesRegistry.Kind kind() {
        return DronesRegistry.Kind.COURIER;
    }

    @Override
    public Item baseItem() {
        return tier() >= 2 ? DronesRegistry.COURIER_DRONE_MK2.get() : DronesRegistry.COURIER_DRONE.get();
    }

    @Override
    protected int maxHealthFor(int tier) {
        return DronesConfig.courierHealth(tier);
    }

    @Override
    public int energyCapacityFor(int tier) {
        return DronesConfig.courierBuffer(tier);
    }

    // ---------------------------------------------------------------- settings

    public State state() {
        return State.byId(entityData.get(DATA_MODE));
    }

    private void setState(State state) {
        entityData.set(DATA_MODE, state.ordinal());
        phaseTicks = 0;
    }

    /** Stacks carried per trip: 1, 3 on Mk2. */
    public int capacityStacks() {
        return tier() >= 2 ? 3 : 1;
    }

    public boolean whitelist() {
        return whitelist;
    }

    public void toggleWhitelist() {
        whitelist = !whitelist;
    }

    public boolean matchTags() {
        return matchTags;
    }

    public void toggleMatchTags() {
        matchTags = !matchTags;
    }

    public List<CourierRoute> routes() {
        return routes;
    }

    /** Routes as the client sees them (read from the synced data). */
    public List<CourierRoute> syncedRoutes() {
        return CourierRoute.loadList(entityData.get(DATA_ROUTES));
    }

    public void setRoutes(List<CourierRoute> list) {
        routes = new ArrayList<>(list.subList(0, Math.min(list.size(), CourierRoute.MAX_ROUTES)));
        syncRoutes();
    }

    public boolean addRoute(CourierRoute route) {
        if (routes.size() >= CourierRoute.MAX_ROUTES) return false;
        routes.add(route);
        syncRoutes();
        return true;
    }

    public void removeRoute(int index) {
        if (index < 0 || index >= routes.size()) return;
        CourierRoute removed = routes.remove(index);
        if (removed.equals(current)) current = null;
        routeIdx = 0;
        syncRoutes();
    }

    private void syncRoutes() {
        CompoundTag tag = new CompoundTag();
        CourierRoute.saveList(tag, routes);
        entityData.set(DATA_ROUTES, tag);
    }

    /** Longest allowed distance between the two ends of a route. */
    public int maxRoute() {
        return DronesConfig.courierMaxRoute() + 32 * upgrades.level(UpgradeKind.RANGE);
    }

    public int fePerTrip() {
        double m = Upgrades.energyMultiplier(upgrades.level(UpgradeKind.SPEED), upgrades.level(UpgradeKind.EFFICIENCY));
        return (int) Math.round(CoreConfig.scaleEnergy(DronesConfig.courierFePerTrip()) * m);
    }

    private double flightSpeed() {
        return Math.min(3.0, 1.1 * Upgrades.speedMultiplier(upgrades.level(UpgradeKind.SPEED)) * (tier() >= 2 ? 1.25 : 1.0));
    }

    // ---------------------------------------------------------------- filter

    /** Whitelist: only items matching a filter entry. Blacklist: everything except them. An empty filter lets everything through. */
    public boolean passes(ItemStack stack) {
        if (stack.isEmpty()) return false;
        boolean any = false;
        boolean match = false;
        for (int i = 0; i < FILTER_SLOTS; i++) {
            ItemStack f = filter.getStackInSlot(i);
            if (f.isEmpty()) continue;
            any = true;
            if (ItemStack.isSameItem(f, stack) || (matchTags && sharesTag(f, stack))) {
                match = true;
                break;
            }
        }
        if (!any) return true;
        return whitelist == match;
    }

    private static boolean sharesTag(ItemStack a, ItemStack b) {
        return a.getTags().anyMatch(tag -> b.is(tag));
    }

    // ---------------------------------------------------------------- tick

    @Override
    protected InteractionResult mobInteract(Player player, InteractionHand hand) {
        if (player.getItemInHand(hand).getItem() instanceof CourierRemoteItem) return InteractionResult.PASS;
        return super.mobInteract(player, hand);
    }

    @Override
    protected void droneTick(ServerLevel sl) {
        if (isEnergyLow() && !cargoNotEmpty()) {
            if (state() != State.LOW) setState(State.LOW);
            if (!lowNotified) {
                lowNotified = true;
                say(msg("courier.low"), true);
                CoreSounds.play(this, CoreSounds.ROBOT_BEEP_LOW, SoundSource.NEUTRAL, 0.8F, 1.0F);
            }
            return;
        }
        if (state() == State.LOW) setState(State.IDLE);
        if (lowNotified && (long) energy * 100L > (long) getEnergyCapacity() * DronesConfig.lowEnergyPercent() * 2L) lowNotified = false;
        phaseTicks++;
        switch (state()) {
            case IDLE, LOW -> tickIdle(sl);
            case PICKUP -> tickPickup(sl);
            case DELIVER -> tickDeliver(sl);
            case BACK -> tickBack(sl);
        }
    }

    private boolean cargoNotEmpty() {
        for (int i = 0; i < cargo.getSlots(); i++) {
            if (!cargo.getStackInSlot(i).isEmpty()) return true;
        }
        return false;
    }

    private void tickIdle(ServerLevel sl) {
        faceFree();
        if (cargoNotEmpty()) {
            // Cargo left over (picked up with items, or loaded from disk): finish the delivery first.
            current = routes.isEmpty() ? null : routes.get(Math.min(routeIdx, routes.size() - 1));
            setState(current == null ? State.IDLE : State.DELIVER);
            if (current == null) dropCargo();
            return;
        }
        if (routes.isEmpty() || isHeld()) return;
        if (--scanCooldown > 0) return;
        scanCooldown = 20;
        for (int n = 0; n < routes.size(); n++) {
            int i = (routeIdx + n) % routes.size();
            CourierRoute r = routes.get(i);
            if (planTrip(sl, r)) {
                routeIdx = i;
                current = r;
                setState(State.PICKUP);
                return;
            }
        }
        routeIdx = (routeIdx + 1) % routes.size();
    }

    private Vec3 above(BlockPos pos) {
        return Vec3.atCenterOf(pos).add(0, 1.3, 0);
    }

    private boolean arrived(Vec3 target) {
        return position().distanceToSqr(target) < 2.5;
    }

    private void tickPickup(ServerLevel sl) {
        CourierRoute r = current;
        if (r == null || !routes.contains(r) || !routeUsable(sl, r)) {
            setState(State.IDLE);
            return;
        }
        Vec3 spot = above(r.source());
        chase(spot, flightSpeed(), 160.0);
        if (!arrived(spot) && phaseTicks < 400) return;
        IItemHandler from = sl.getCapability(Capabilities.ItemHandler.BLOCK, r.source(), r.sourceFace());
        IItemHandler to = sl.getCapability(Capabilities.ItemHandler.BLOCK, r.target(), r.targetFace());
        if (from == null || to == null || !mayUse(sl, r.source(), r.sourceFace())) {
            setState(State.IDLE);
            return;
        }
        if (!load(from, to) || !consume(fePerTrip())) {
            returnCargoNow(from);
            setState(State.IDLE);
            return;
        }
        CoreSounds.play(this, CoreSounds.ROBOT_BEEP, SoundSource.NEUTRAL, 0.4F, 1.4F);
        setState(State.DELIVER);
    }

    private void tickDeliver(ServerLevel sl) {
        CourierRoute r = current;
        if (r == null || !routeUsable(sl, r)) {
            setState(State.BACK);
            return;
        }
        Vec3 spot = above(r.target());
        chase(spot, flightSpeed(), 160.0);
        if (!arrived(spot) && phaseTicks < 400) return;
        IItemHandler to = sl.getCapability(Capabilities.ItemHandler.BLOCK, r.target(), r.targetFace());
        if (to == null || !mayUse(sl, r.target(), r.targetFace())) {
            setState(State.BACK);
            return;
        }
        boolean any = false;
        for (int i = 0; i < cargo.getSlots(); i++) {
            ItemStack stack = cargo.getStackInSlot(i);
            if (stack.isEmpty()) continue;
            ItemStack rest = ItemHandlerHelper.insertItem(to, stack.copy(), false);
            if (rest.getCount() != stack.getCount()) any = true;
            cargo.setStackInSlot(i, rest);
        }
        if (any) {
            trips++;
            playSound(SoundEvents.ITEM_PICKUP, 0.3F, 1.2F);
        }
        setState(cargoNotEmpty() ? State.BACK : State.IDLE);
        scanCooldown = 5;
    }

    private void tickBack(ServerLevel sl) {
        CourierRoute r = current;
        if (r == null || !routeUsable(sl, r)) {
            dropCargo();
            setState(State.IDLE);
            return;
        }
        Vec3 spot = above(r.source());
        chase(spot, flightSpeed(), 160.0);
        if (!arrived(spot) && phaseTicks < 400) return;
        IItemHandler from = sl.getCapability(Capabilities.ItemHandler.BLOCK, r.source(), r.sourceFace());
        returnCargoNow(from);
        setState(State.IDLE);
        scanCooldown = 60;
    }

    private void returnCargoNow(@Nullable IItemHandler home) {
        for (int i = 0; i < cargo.getSlots(); i++) {
            ItemStack stack = cargo.getStackInSlot(i);
            if (stack.isEmpty()) continue;
            ItemStack rest = home == null ? stack : ItemHandlerHelper.insertItem(home, stack.copy(), false);
            cargo.setStackInSlot(i, ItemStack.EMPTY);
            if (!rest.isEmpty()) Containers.dropItemStack(level(), getX(), getY(), getZ(), rest);
        }
    }

    private void dropCargo() {
        returnCargoNow(null);
    }

    // ---------------------------------------------------------------- routes and containers

    /** Both ends loaded, in this dimension, in range, with item handlers and not vetoed by claims. Never loads a chunk. */
    private boolean routeUsable(ServerLevel sl, CourierRoute r) {
        if (!r.dim().equals(sl.dimension().location().toString())) return false;
        if (!sl.isLoaded(r.source()) || !sl.isLoaded(r.target())) return false;
        if (r.source().distSqr(r.target()) > (double) maxRoute() * maxRoute()) return false;
        return sl.getCapability(Capabilities.ItemHandler.BLOCK, r.source(), r.sourceFace()) != null
                && sl.getCapability(Capabilities.ItemHandler.BLOCK, r.target(), r.targetFace()) != null;
    }

    /** True when something in the source passes the filter and the target would take at least one item of it. */
    private boolean planTrip(ServerLevel sl, CourierRoute r) {
        if (!routeUsable(sl, r)) return false;
        IItemHandler from = sl.getCapability(Capabilities.ItemHandler.BLOCK, r.source(), r.sourceFace());
        IItemHandler to = sl.getCapability(Capabilities.ItemHandler.BLOCK, r.target(), r.targetFace());
        if (from == null || to == null || !mayUse(sl, r.source(), r.sourceFace()) || !mayUse(sl, r.target(), r.targetFace())) return false;
        for (int slot = 0; slot < from.getSlots(); slot++) {
            ItemStack seen = from.getStackInSlot(slot);
            if (seen.isEmpty() || !passes(seen)) continue;
            ItemStack sim = from.extractItem(slot, 1, true);
            if (sim.isEmpty()) continue;
            if (ItemHandlerHelper.insertItem(to, sim, true).isEmpty()) return !isEnergyLow() || energy >= fePerTrip();
        }
        return false;
    }

    /** Fills the cargo slots from the source with what passes the filter and fits into the target. */
    private boolean load(IItemHandler from, IItemHandler to) {
        int slotsUsed = 0;
        for (int slot = 0; slot < from.getSlots() && slotsUsed < capacityStacks(); slot++) {
            ItemStack seen = from.getStackInSlot(slot);
            if (seen.isEmpty() || !passes(seen)) continue;
            ItemStack sim = from.extractItem(slot, seen.getMaxStackSize(), true);
            if (sim.isEmpty()) continue;
            ItemStack fits = sim.copy();
            ItemStack leftover = ItemHandlerHelper.insertItem(to, sim.copy(), true);
            fits.setCount(sim.getCount() - leftover.getCount());
            if (fits.isEmpty()) continue;
            ItemStack taken = from.extractItem(slot, fits.getCount(), false);
            if (taken.isEmpty()) continue;
            ItemStack rest = ItemHandlerHelper.insertItem(cargo, taken, false);
            if (!rest.isEmpty()) ItemHandlerHelper.insertItem(from, rest, false);
            slotsUsed++;
        }
        return cargoNotEmpty();
    }

    /** Asks claim and protection mods through the interaction event of the owner's fake player (and spawn protection). */
    public boolean mayUse(ServerLevel sl, BlockPos pos, Direction face) {
        try {
            FakePlayer fake = FakePlayerFactory.get(sl, new GameProfile(owner != null ? owner : FALLBACK_OWNER, "[Robotica]"));
            if (sl.getServer().isUnderSpawnProtection(sl, pos, fake)) return false;
            BlockHitResult hit = new BlockHitResult(Vec3.atCenterOf(pos), face, pos, false);
            PlayerInteractEvent.RightClickBlock event = new PlayerInteractEvent.RightClickBlock(fake, InteractionHand.MAIN_HAND, pos, hit);
            NeoForge.EVENT_BUS.post(event);
            return !event.isCanceled() && event.getUseBlock() != TriState.FALSE;
        } catch (RuntimeException e) {
            return true;
        }
    }

    // ---------------------------------------------------------------- gui

    @Override
    protected void openGui(ServerPlayer player) {
        player.openMenu(new SimpleMenuProvider((id, inv, p) -> new CourierDroneMenu(id, inv, this), getDisplayName()),
                buf -> buf.writeVarInt(getId()));
    }

    // ---------------------------------------------------------------- persistence

    @Override
    protected void writeSettings(CompoundTag tag, HolderLookup.Provider registries) {
        tag.put("Filter", filter.serializeNBT(registries));
        tag.put("Cargo", cargo.serializeNBT(registries));
        tag.put("Upgrades", upgrades.serializeNBT(registries));
        tag.put("Battery", battery.serializeNBT(registries));
        tag.putBoolean("Whitelist", whitelist);
        tag.putBoolean("MatchTags", matchTags);
        CourierRoute.saveList(tag, routes);
    }

    @Override
    protected void readSettings(CompoundTag tag, HolderLookup.Provider registries) {
        if (tag.contains("Filter")) filter.deserializeNBT(registries, tag.getCompound("Filter"));
        if (tag.contains("Cargo")) cargo.deserializeNBT(registries, tag.getCompound("Cargo"));
        if (tag.contains("Upgrades")) upgrades.deserializeNBT(registries, tag.getCompound("Upgrades"));
        if (tag.contains("Battery")) battery.deserializeNBT(registries, tag.getCompound("Battery"));
        whitelist = !tag.contains("Whitelist") || tag.getBoolean("Whitelist");
        matchTags = tag.getBoolean("MatchTags");
        routes = CourierRoute.loadList(tag);
        entityDataSafeSync();
    }

    private void entityDataSafeSync() {
        CompoundTag t = new CompoundTag();
        CourierRoute.saveList(t, routes);
        entityData.set(DATA_ROUTES, t);
    }

    @Override
    protected void writeRuntime(CompoundTag tag) {
        tag.putInt("State", state().ordinal());
        tag.putInt("RouteIdx", routeIdx);
    }

    @Override
    protected void readRuntime(CompoundTag tag) {
        routeIdx = tag.getInt("RouteIdx");
        State s = State.byId(tag.getInt("State"));
        entityData.set(DATA_MODE, (s == State.LOW ? State.IDLE : s).ordinal());
        if (!routes.isEmpty()) current = routes.get(Math.min(routeIdx, routes.size() - 1));
    }

    @Override
    protected void onPickedUp() {
        // Cargo stays inside and is part of the item.
    }
}
