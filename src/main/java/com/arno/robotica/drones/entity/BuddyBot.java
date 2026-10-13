package com.arno.robotica.drones.entity;

import com.arno.robotica.Robotica;
import com.arno.robotica.core.CoreConfig;
import com.arno.robotica.core.CoreSounds;
import com.arno.robotica.core.energy.ItemEnergy;
import com.arno.robotica.drones.DronesConfig;
import com.arno.robotica.drones.DronesRegistry;
import com.arno.robotica.drones.menu.BuddyBotMenu;
import com.arno.robotica.drones.net.SentryBoltPayload;
import com.arno.robotica.gear.GearConfig;
import com.arno.robotica.gear.lamp.SparkLamps;
import com.arno.robotica.gear.tool.LampPlacer;
import com.mojang.authlib.GameProfile;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.HolderLookup;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.core.registries.Registries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.network.syncher.EntityDataAccessor;
import net.minecraft.network.syncher.EntityDataSerializers;
import net.minecraft.network.syncher.SynchedEntityData;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.tags.FluidTags;
import net.minecraft.tags.TagKey;
import net.minecraft.util.Mth;
import net.minecraft.world.Containers;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.SimpleMenuProvider;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.damagesource.DamageType;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.NeutralMob;
import net.minecraft.world.entity.TamableAnimal;
import net.minecraft.world.entity.ai.attributes.AttributeSupplier;
import net.minecraft.world.entity.animal.Animal;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.entity.monster.Enemy;
import net.minecraft.world.entity.npc.Npc;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.enchantment.Enchantments;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.FallingBlock;
import net.minecraft.world.level.block.LiquidBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.material.FluidState;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.common.util.FakePlayer;
import net.neoforged.neoforge.common.util.FakePlayerFactory;
import net.neoforged.neoforge.event.level.BlockEvent;
import net.neoforged.neoforge.items.ItemHandlerHelper;
import net.neoforged.neoforge.items.ItemStackHandler;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

/**
 * Buddy Bot: the player's flying companion (Mk1 to Mk4). It follows, patrols a spot or stays, shoots hostiles by
 * stance, sweeps up drops into its cargo (a flying chest) and digs a tunnel where the owner looks (H). Spark Lamps light
 * its tunnels, and with light-up on, dark places it flies through. Tiers only add numbers and cargo space.
 * <p>
 * Blocks are broken through a fake player of the owner so protection and claim mods can veto; it never touches block
 * entities or unbreakable blocks and never loads chunks: an unloaded slice ends the tunnel. Emotes and reactions are
 * picked on the server and sent as entity events, so every client plays the same one.
 */
public class BuddyBot extends DroneBase {
    public static final TagKey<EntityType<?>> IGNORE = TagKey.create(Registries.ENTITY_TYPE, Robotica.id("sentry_ignore"));
    public static final TagKey<Item> VOIDABLE = TagKey.create(Registries.ITEM, Robotica.id("voidable"));
    public static final ResourceKey<DamageType> ENERGY_BOLT = ResourceKey.create(Registries.DAMAGE_TYPE, Robotica.id("energy_bolt"));
    private static final UUID FALLBACK_OWNER = UUID.fromString("5a0b6c52-3f2b-4a39-9d5e-0d6f5f0e7a11");

    /** Cargo slots per tier (index = tier). */
    private static final int[] CARGO = {0, 18, 27, 36, 54};
    public static final int MAX_CARGO = 54;
    /** Patrol circle around the spot it was set to patrol. */
    public static final int PATROL_RADIUS = 10;

    private static final EntityDataAccessor<Integer> DATA_STANCE = SynchedEntityData.defineId(BuddyBot.class, EntityDataSerializers.INT);
    private static final EntityDataAccessor<Byte> DATA_FLAGS = SynchedEntityData.defineId(BuddyBot.class, EntityDataSerializers.BYTE);
    private static final int FLAG_PICKUP = 1, FLAG_LIGHTS = 2, FLAG_VOID = 4, FLAG_DIGGING = 8, FLAG_LOW = 16, FLAG_OPEN = 32;

    /** Entity events (above the vanilla ids): a bite when it grabs loot, a recoil when it fires, then one per emote. */
    public static final byte EVENT_CHOMP = 100;
    public static final byte EVENT_RECOIL = 101;
    public static final byte EVENT_EMOTE = 110;

    /** Movement mode. Ids are stored and synced, never reorder. */
    public enum Mode {
        FOLLOW, PATROL, STAY;

        public static Mode byId(int id) {
            Mode[] all = values();
            return all[Math.floorMod(id, all.length)];
        }
    }

    /** Aggressive: every hostile in range. Defense: only what the owner is fighting. Passive: never fires. */
    public enum Stance {
        AGGRESSIVE, DEFENSE, PASSIVE;

        public static Stance byId(int id) {
            Stance[] all = values();
            return all[Math.floorMod(id, all.length)];
        }

        public Stance next() {
            return byId(ordinal() + 1);
        }
    }

    /** Why a tunnel ended. The lang key is {@code message.robotica.buddy.<lowercase name>}. */
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

    /** Animations the client plays; {@link #ticks} is how long one lasts. Ids travel as entity events, never reorder. */
    public enum Emote {
        NONE(0), SPIN(24), WAVE(40), HICCUP(18), SNEEZE(34), NOD(24), LOOK_AROUND(60), BOOP(40), CHEER(30), SHAKE(20), SLEEPY(60);

        public final int ticks;

        Emote(int ticks) {
            this.ticks = ticks;
        }

        public static Emote byId(int id) {
            Emote[] all = values();
            return id >= 0 && id < all.length ? all[id] : NONE;
        }
    }

    /** Bolt beam color by stance, the same as its eye. */
    private static final int[] BEAM_COLORS = {0xE83A2A, 0x2FB8CC, 0x3EB03A};

    /** Emotes it does on its own when nothing is going on. */
    private static final Emote[] IDLE = {Emote.SPIN, Emote.WAVE, Emote.HICCUP, Emote.SNEEZE, Emote.NOD, Emote.LOOK_AROUND, Emote.BOOP};

    /** Tunnel slice cells in dig order: the bot's own line first, then up and down, then the sides. */
    private static final int[][] CELLS = {{0, 0}, {0, 1}, {0, -1}, {1, 0}, {-1, 0}, {1, 1}, {-1, 1}, {1, -1}, {-1, -1}};
    /** The 5x5 slice (Mk3, Mk4): floor at foot level like the 3x3, so lateral -2..2 and vertical -1..3, nearest cells first. */
    private static final int[][] CELLS_5 = cells5();

    private static int[][] cells5() {
        List<int[]> list = new ArrayList<>();
        for (int vert = -1; vert <= 3; vert++) {
            for (int lat = -2; lat <= 2; lat++) list.add(new int[]{lat, vert});
        }
        list.sort(java.util.Comparator.<int[]>comparingInt(c -> Math.abs(c[0]) + Math.abs(c[1])).thenComparingInt(c -> Math.abs(c[0])));
        return list.toArray(new int[0][]);
    }

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
            return tunnel;
        }
    }

    /** The cargo. Slots above the tier's size accept nothing. */
    public final ItemStackHandler storage = new ItemStackHandler(MAX_CARGO) {
        @Override
        public boolean isItemValid(int slot, ItemStack stack) {
            return slot < cargoSlots();
        }
    };

    private BlockPos patrolCenter = BlockPos.ZERO;
    @Nullable
    private Tunnel tunnel;
    /** Flying back to the owner after a tunnel; turns into Follow on arrival. */
    private boolean returning;
    @Nullable
    private Stop lastStop;
    private int lastStopBlocks;
    private int minedTotal;
    private int drillTicks;
    private ItemStack tool = ItemStack.EMPTY;
    private int toolTier;
    /** Game tests set this to dig faster (0 = config value). */
    public int digTicksOverride;

    private int cooldown;
    private static final int SHOT_SOUND_GAP = 8;
    private int lastShotSound = -SHOT_SOUND_GAP;
    private boolean lowNotified;
    @Nullable
    private LivingEntity target;
    @Nullable
    private Vec3 patrolPoint;
    private int patrolTicks;
    private int lampCooldown;
    private int lastChomp = -20;
    /** Ticks the lid stays open after the GUI was last seen open. */
    private int openTicks;
    /** Bolts fired since the bot was created or loaded (tests, stats). */
    public int shots;

    /** Server: ticks until the next idle emote, the running emote and what it looks at. */
    private int emoteIn = 400;
    private Emote serverEmote = Emote.NONE;
    private int serverEmoteTicks;
    @Nullable
    private LivingEntity curious;
    private int lastOwnerHurt;

    /** Client: the emote playing and when it started, plus the bite and recoil timers (counting down). */
    private Emote clientEmote = Emote.NONE;
    private int clientEmoteAge;
    private int chompTicks;
    private int recoilTicks;
    private float lidOpen;
    private float lidOpenO;

    public BuddyBot(EntityType<? extends BuddyBot> type, Level level) {
        super(type, level);
    }

    public static AttributeSupplier.Builder createAttributes() {
        return createBaseAttributes();
    }

    @Override
    protected void defineSynchedData(SynchedEntityData.Builder builder) {
        super.defineSynchedData(builder);
        builder.define(DATA_STANCE, Stance.AGGRESSIVE.ordinal());
        builder.define(DATA_FLAGS, (byte) FLAG_PICKUP);
    }

    @Override
    public DronesRegistry.Kind kind() {
        return DronesRegistry.Kind.BUDDY;
    }

    @Override
    public Item baseItem() {
        return itemFor(tier());
    }

    public static Item itemFor(int tier) {
        return switch (Mth.clamp(tier, 1, 4)) {
            case 4 -> DronesRegistry.BUDDY_BOT_MK4.get();
            case 3 -> DronesRegistry.BUDDY_BOT_MK3.get();
            case 2 -> DronesRegistry.BUDDY_BOT_MK2.get();
            default -> DronesRegistry.BUDDY_BOT.get();
        };
    }

    @Override
    protected int maxHealthFor(int tier) {
        return DronesConfig.buddyHealth(tier);
    }

    @Override
    public int energyCapacityFor(int tier) {
        return DronesConfig.buddyBuffer(tier);
    }

    public static int cargoSlots(int tier) {
        return CARGO[Mth.clamp(tier, 1, 4)];
    }

    public int cargoSlots() {
        return cargoSlots(tier());
    }

    // ---------------------------------------------------------------- tier numbers

    /** Firing range in blocks: the config value, +4 per Mk. */
    public int range() {
        return DronesConfig.buddyRange() + 4 * (tier() - 1);
    }

    public float damage() {
        return DronesConfig.buddyDamage(tier());
    }

    public int cooldownTicks() {
        return DronesConfig.buddyCooldown(tier());
    }

    public int fePerShot() {
        return scaled(DronesConfig.buddyFePerShot());
    }

    /** Blocks around it that it sweeps for drops: 3, 4, 5, 6. */
    public int pickupRadius() {
        return 2 + tier();
    }

    /** Half width of the tunnel: 1 for 3x3 (Mk1, Mk2), 2 for 5x5 (Mk3, Mk4). */
    public int radius() {
        return tier() >= 3 ? 2 : 1;
    }

    private int[][] sliceCells() {
        return radius() >= 2 ? CELLS_5 : CELLS;
    }

    /** Highest vertical offset of a slice: 1 for 3x3, 3 for 5x5 (the floor stays at foot level). */
    private int top() {
        return 2 * radius() - 1;
    }

    /** The pickaxe it digs with: iron, diamond, netherite with Fortune I, netherite with Fortune II. Drops are rolled with it. */
    public ItemStack tool() {
        int t = tier();
        if (tool.isEmpty() || toolTier != t) {
            tool = new ItemStack(t >= 3 ? Items.NETHERITE_PICKAXE : t >= 2 ? Items.DIAMOND_PICKAXE : Items.IRON_PICKAXE);
            int fortune = t >= 4 ? 2 : t >= 3 ? 1 : 0;
            if (fortune > 0) tool.enchant(level().registryAccess().lookupOrThrow(Registries.ENCHANTMENT).getOrThrow(Enchantments.FORTUNE), fortune);
            toolTier = t;
        }
        return tool;
    }

    // ---------------------------------------------------------------- state access

    public Mode mode() {
        return Mode.byId(entityData.get(DATA_MODE));
    }

    public void setMode(Mode mode) {
        entityData.set(DATA_MODE, mode.ordinal());
        patrolPoint = null;
        if (mode == Mode.PATROL) patrolCenter = blockPosition();
    }

    public Stance stance() {
        return Stance.byId(entityData.get(DATA_STANCE));
    }

    public void setStance(Stance stance) {
        entityData.set(DATA_STANCE, stance.ordinal());
        if (stance == Stance.PASSIVE) target = null;
    }

    public void cycleStance() {
        setStance(stance().next());
    }

    private boolean flag(int f) {
        return (entityData.get(DATA_FLAGS) & f) != 0;
    }

    private void setFlag(int f, boolean on) {
        byte b = entityData.get(DATA_FLAGS);
        byte next = (byte) (on ? b | f : b & ~f);
        if (next != b) entityData.set(DATA_FLAGS, next);
    }

    public boolean pickupOn() {
        return flag(FLAG_PICKUP);
    }

    public void setPickup(boolean on) {
        setFlag(FLAG_PICKUP, on);
    }

    public boolean lightsOn() {
        return flag(FLAG_LIGHTS);
    }

    public void setLights(boolean on) {
        setFlag(FLAG_LIGHTS, on);
    }

    public boolean voidOn() {
        return flag(FLAG_VOID);
    }

    public void setVoid(boolean on) {
        setFlag(FLAG_VOID, on);
    }

    /** Digging a tunnel (synced, the drill spins). */
    public boolean isDigging() {
        return flag(FLAG_DIGGING);
    }

    /** Below the low energy line (synced, it looks sleepy). */
    public boolean isLowEnergy() {
        return flag(FLAG_LOW);
    }

    public BlockPos patrolCenter() {
        return patrolCenter;
    }

    public void setPatrolCenter(BlockPos pos) {
        patrolCenter = pos.immutable();
    }

    public boolean hasTunnel() {
        return tunnel != null;
    }

    public boolean isReturning() {
        return returning;
    }

    public int progress() {
        return tunnel != null ? tunnel.counted : 0;
    }

    public int jobLength() {
        return tunnel != null ? tunnel.length : DronesConfig.tunnelLength();
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

    @Nullable
    public LivingEntity currentTarget() {
        return target;
    }

    @Override
    public void onDeployed(@Nullable Player placer, BlockPos pos) {
        patrolCenter = pos.immutable();
        entityData.set(DATA_MODE, Mode.FOLLOW.ordinal());
    }

    // ---------------------------------------------------------------- commands (server)

    public void commandFollow() {
        if (tunnel != null) stopTunnelQuiet();
        returning = false;
        setMode(Mode.FOLLOW);
    }

    /** Stops a running tunnel and flies back to the owner. */
    public void commandReturn() {
        if (tunnel != null) {
            say(msg("buddy.abort"), false);
            stopTunnelQuiet();
        }
        returning = ownerHere() != null;
    }

    /** Tunnel along {@code dir} starting at the commander's feet. Returns false when it cannot start. */
    public boolean commandTunnel(@Nullable ServerPlayer commander, Direction dir) {
        if (tunnel != null || !dir.getAxis().isHorizontal()) return false;
        BlockPos feet = commander != null && commander.level() == level() ? commander.blockPosition() : blockPosition().below();
        return startTunnel(dir, DronesConfig.tunnelLength(), feet);
    }

    /** Starts a job: the slices run from {@code feet} in direction {@code dir}, 3 (Mk3+: 5) wide and high from foot level. */
    public boolean startTunnel(Direction dir, int length, BlockPos feet) {
        if (isEnergyLow() || getEnergy() + batteryReserve() < feeBlock() * sliceCells().length) {
            say(msg("buddy.energy"), false);
            reactError();
            return false;
        }
        if (freeSlots() == 0) {
            say(msg("buddy.full"), false);
            reactError();
            return false;
        }
        tunnel = new Tunnel(dir, feet.immutable(), Math.max(1, length));
        returning = false;
        target = null;
        lastStop = null;
        setFlag(FLAG_DIGGING, true);
        say(msg("buddy.start", tunnel.length), false);
        CoreSounds.play(this, CoreSounds.ROBOT_BEEP, SoundSource.NEUTRAL, 0.8F, 1.0F);
        return true;
    }

    private int batteryReserve() {
        ItemStack cell = battery.getStackInSlot(0);
        return cell.isEmpty() ? 0 : ItemEnergy.get(cell);
    }

    // ---------------------------------------------------------------- inventory

    public int freeSlots() {
        int free = 0;
        for (int i = 0; i < cargoSlots(); i++) {
            if (storage.getStackInSlot(i).isEmpty()) free++;
        }
        return free;
    }

    public int storedItems() {
        int n = 0;
        for (int i = 0; i < cargoSlots(); i++) n += storage.getStackInSlot(i).getCount();
        return n;
    }

    public int count(Item item) {
        int n = 0;
        for (int i = 0; i < cargoSlots(); i++) {
            ItemStack s = storage.getStackInSlot(i);
            if (s.is(item)) n += s.getCount();
        }
        return n;
    }

    private boolean takeOne(Item item) {
        for (int i = 0; i < cargoSlots(); i++) {
            if (storage.getStackInSlot(i).is(item)) {
                storage.extractItem(i, 1, false);
                return true;
            }
        }
        return false;
    }

    /** With void on, junk goes (a stack of cobblestone is kept for plugging fluids). */
    private boolean voids(ItemStack stack) {
        return voidOn() && stack.is(VOIDABLE) && !(stack.is(Items.COBBLESTONE) && count(Items.COBBLESTONE) < 64);
    }

    /** Stores a mined drop, unless it is voided. What does not fit drops. */
    public void collect(ItemStack drop) {
        if (drop.isEmpty() || voids(drop)) return;
        ItemStack rest = ItemHandlerHelper.insertItem(storage, drop.copy(), false);
        if (!rest.isEmpty()) Containers.dropItemStack(level(), getX(), getY(), getZ(), rest);
    }

    private boolean hasRoom() {
        for (int i = 0; i < cargoSlots(); i++) {
            ItemStack s = storage.getStackInSlot(i);
            if (s.isEmpty() || s.getCount() < s.getMaxStackSize()) return true;
        }
        return false;
    }

    private static boolean collectable(ItemEntity ie) {
        return ie.isAlive() && !ie.hasPickUpDelay() && !(ie.getOwner() instanceof Player);
    }

    /** Sweeps up drops lying around it (never what a player threw). */
    private void pickUpNearbyItems(ServerLevel sl) {
        if (!hasRoom()) return;
        int r = pickupRadius();
        List<ItemEntity> items = sl.getEntitiesOfClass(ItemEntity.class, getBoundingBox().inflate(r, 2.0, r), BuddyBot::collectable);
        boolean took = false;
        for (ItemEntity item : items) {
            ItemStack stack = item.getItem();
            if (voids(stack)) {
                item.discard();
                took = true;
                continue;
            }
            ItemStack rest = ItemHandlerHelper.insertItem(storage, stack.copy(), false);
            int taken = stack.getCount() - rest.getCount();
            if (taken <= 0) continue;
            if (rest.isEmpty()) item.discard();
            else item.setItem(rest);
            took = true;
        }
        if (took && tickCount - lastChomp >= 10) {
            lastChomp = tickCount;
            playSound(SoundEvents.ITEM_PICKUP, 0.25F, 1.3F + random.nextFloat() * 0.2F);
            level().broadcastEntityEvent(this, EVENT_CHOMP);
        }
    }

    // ---------------------------------------------------------------- targeting

    /** True when this entity is a legal target for the current stance. Players, pets, villagers, drones and ignored types never are. */
    public boolean canTarget(LivingEntity e) {
        if (e == this || !e.isAlive() || e.isRemoved() || e.isSpectator()) return false;
        if (e instanceof Player || e instanceof DroneBase || e instanceof Npc) return false;
        if (e.getType().is(IGNORE)) return false;
        if (e instanceof TamableAnimal tame && tame.isTame()) return false;
        return switch (stance()) {
            case PASSIVE -> false;
            case DEFENSE -> e instanceof Mob && isOwnersFight(e);
            case AGGRESSIVE -> e instanceof Enemy && (!(e instanceof NeutralMob) || targetsUs(e instanceof Mob m ? m : null)) || (e instanceof Mob && isOwnersFight(e));
        };
    }

    /** The mob the owner hit in the last 5 seconds. */
    private boolean isOwnersFight(LivingEntity e) {
        ServerPlayer o = ownerHere();
        return o != null && o.getLastHurtMob() == e && o.tickCount - o.getLastHurtMobTimestamp() < 100;
    }

    private boolean targetsUs(@Nullable Mob mob) {
        if (mob == null) return false;
        LivingEntity t = mob.getTarget();
        if (t == null) return false;
        if (t == this || t instanceof DroneBase d && owner != null && owner.equals(d.getOwnerUUID())) return true;
        return owner != null && t.getUUID().equals(owner);
    }

    private void updateTarget(ServerLevel sl) {
        double range = range();
        if (target != null && (!canTarget(target) || distanceToSqr(target) > range * range || !hasLineOfSight(target))) target = null;
        if (target != null && tickCount % 20 != 0) return;
        if (stance() == Stance.DEFENSE) {
            ServerPlayer o = ownerHere();
            LivingEntity fight = o != null ? o.getLastHurtMob() : null;
            target = fight != null && canTarget(fight) && distanceToSqr(fight) <= range * range && hasLineOfSight(fight) ? fight : null;
            return;
        }
        List<LivingEntity> candidates = sl.getEntitiesOfClass(LivingEntity.class, getBoundingBox().inflate(range), this::canTarget);
        LivingEntity best = null;
        double bestDist = range * range;
        for (LivingEntity e : candidates) {
            double d = distanceToSqr(e);
            if (d <= bestDist && hasLineOfSight(e)) {
                best = e;
                bestDist = d;
            }
        }
        target = best;
    }

    private DamageSource boltSource(ServerLevel sl) {
        ServerPlayer o = ownerHere();
        Optional<net.minecraft.core.Holder.Reference<DamageType>> holder = sl.registryAccess().registryOrThrow(Registries.DAMAGE_TYPE).getHolder(ENERGY_BOLT);
        if (holder.isEmpty()) return o != null ? sl.damageSources().mobProjectile(this, o) : sl.damageSources().mobAttack(this);
        // The owner is the causing entity, so kills count as theirs (experience, player-only drops).
        return new DamageSource(holder.get(), this, o);
    }

    private void fire(ServerLevel sl, LivingEntity t, Vec3 aim) {
        if (!hasLineOfSight(t)) return;
        if (!consume(fePerShot())) return;
        cooldown = cooldownTicks();
        shots++;
        Vec3 from = position().add(0, getBbHeight() * 0.45, 0);
        Vec3 dir = aim.subtract(from);
        double len = dir.length();
        if (len > 1.0E-3) from = from.add(dir.scale(0.45 / len));
        // one small packet; the clients draw the trail and the sparks
        SentryBoltPayload.send(this, from, aim, BEAM_COLORS[stance().ordinal()], tier());
        level().broadcastEntityEvent(this, EVENT_RECOIL);
        if (tickCount - lastShotSound >= SHOT_SOUND_GAP || tickCount < lastShotSound) {
            lastShotSound = tickCount;
            CoreSounds.play(this, CoreSounds.RIVET_SHOT, SoundSource.NEUTRAL, 0.6F, 1.3F + random.nextFloat() * 0.2F);
        }
        t.hurt(boltSource(sl), damage());
        if (!t.isAlive()) {
            target = null;
            emote(Emote.CHEER);
        }
    }

    // ---------------------------------------------------------------- tick

    @Override
    public void tick() {
        super.tick();
        if (level().isClientSide) {
            if (clientEmote == Emote.SNEEZE && clientEmoteAge == 20) sneeze();
            if (clientEmote != Emote.NONE && ++clientEmoteAge >= clientEmote.ticks) clientEmote = Emote.NONE;
            if (chompTicks > 0) chompTicks--;
            if (recoilTicks > 0) recoilTicks--;
            lidOpenO = lidOpen;
            lidOpen = Mth.clamp(lidOpen + (flag(FLAG_OPEN) ? 0.2F : -0.2F), 0.0F, 1.0F);
            if (!isInvisible()) clientParticles();
        }
    }

    /** Client: thruster puffs while it flies, sputtering smoke and sparks on low energy. */
    private void clientParticles() {
        Level level = level();
        double dx = getX() - xo, dy = getY() - yo, dz = getZ() - zo;
        if (dx * dx + dy * dy + dz * dz > 0.0016 && tickCount % 3 == 0) {
            level.addParticle(ParticleTypes.WHITE_SMOKE, getX() + (random.nextDouble() - 0.5) * 0.1, getY() + 0.02, getZ() + (random.nextDouble() - 0.5) * 0.1,
                    0.0, -0.04, 0.0);
        }
        if (isLowEnergy() && random.nextInt(14) == 0) {
            level.addParticle(ParticleTypes.SMOKE, getX() + (random.nextDouble() - 0.5) * 0.4, getY() + 0.55, getZ() + (random.nextDouble() - 0.5) * 0.4,
                    0.0, 0.03, 0.0);
            if (random.nextBoolean()) level.addParticle(ParticleTypes.ELECTRIC_SPARK, getX(), getY() + 0.3, getZ(),
                    (random.nextDouble() - 0.5) * 0.2, 0.1, (random.nextDouble() - 0.5) * 0.2);
        }
    }

    /** Client: the CHOO of a sneeze, in time with the animation. */
    private void sneeze() {
        level().playLocalSound(getX(), getY(), getZ(), SoundEvents.PANDA_SNEEZE, getSoundSource(), 0.6F, 1.6F, false);
        Vec3 nose = position().add(Vec3.directionFromRotation(0, getYRot()).scale(0.45)).add(0, 0.3, 0);
        for (int i = 0; i < 5; i++) {
            level().addParticle(ParticleTypes.POOF, nose.x, nose.y, nose.z,
                    (random.nextDouble() - 0.5) * 0.06, random.nextDouble() * 0.04, (random.nextDouble() - 0.5) * 0.06);
        }
    }

    /** Client: a few particles when an emote starts. */
    private void emoteParticles(Emote e) {
        double y = getY() + 0.9;
        switch (e) {
            case CHEER -> {
                for (int i = 0; i < 6; i++) {
                    level().addParticle(ParticleTypes.HAPPY_VILLAGER, getX() + (random.nextDouble() - 0.5) * 0.8, getY() + 0.3 + random.nextDouble() * 0.5,
                            getZ() + (random.nextDouble() - 0.5) * 0.8, 0.0, 0.0, 0.0);
                }
            }
            case WAVE, SPIN -> level().addParticle(ParticleTypes.NOTE, getX(), y, getZ(), random.nextInt(25) / 24.0, 0.0, 0.0);
            case BOOP -> level().addParticle(ParticleTypes.HEART, getX(), y, getZ(), 0.0, 0.0, 0.0);
            case HICCUP -> {
                for (int i = 0; i < 3; i++) {
                    level().addParticle(ParticleTypes.BUBBLE_POP, getX() + (random.nextDouble() - 0.5) * 0.3, getY() + 0.7, getZ() + (random.nextDouble() - 0.5) * 0.3,
                            0.0, 0.05, 0.0);
                }
            }
            case SHAKE -> {
                for (int i = 0; i < 2; i++) {
                    level().addParticle(ParticleTypes.FALLING_WATER, getX() + (random.nextBoolean() ? 0.3 : -0.3), getY() + 0.55, getZ(), 0.0, 0.0, 0.0);
                }
            }
            default -> {
            }
        }
    }

    @Override
    protected void droneTick(ServerLevel sl) {
        if (cooldown > 0) cooldown--;
        if (lampCooldown > 0) lampCooldown--;
        if (openTicks > 0 && --openTicks == 0) {
            setFlag(FLAG_OPEN, false);
            playSound(SoundEvents.CHEST_CLOSE, 0.4F, 1.5F);
        }
        if (drillTicks > 0 && --drillTicks == 0 && target == null) setActive(false);
        boolean low = isEnergyLow();
        setFlag(FLAG_LOW, low);
        if (low && !lowNotified) {
            lowNotified = true;
            say(msg("buddy.low"), true);
            CoreSounds.play(this, CoreSounds.ROBOT_BEEP_LOW, SoundSource.NEUTRAL, 0.8F, 1.0F);
        } else if (lowNotified && (long) energy * 100L > (long) getEnergyCapacity() * DronesConfig.lowEnergyPercent() * 2L) {
            lowNotified = false;
        }
        if (tunnel != null) {
            tickTunnel(sl);
            return;
        }
        setFlag(FLAG_DIGGING, false);
        if (low || stance() == Stance.PASSIVE) {
            target = null;
        } else if (tickCount % 5 == 0) {
            updateTarget(sl);
        }
        LivingEntity t = target;
        if (drillTicks <= 0) setActive(t != null);
        if (t != null) {
            Vec3 aim = t.position().add(0, t.getBbHeight() * 0.6, 0);
            faceYaw((float) (Mth.atan2(aim.z - getZ(), aim.x - getX()) * Mth.RAD_TO_DEG) - 90.0F);
            if (cooldown <= 0 && distanceToSqr(t) <= (double) range() * range()) fire(sl, t, aim);
        } else if (curious != null && serverEmote == Emote.LOOK_AROUND && curious.isAlive()) {
            faceYaw((float) (Mth.atan2(curious.getZ() - getZ(), curious.getX() - getX()) * Mth.RAD_TO_DEG) - 90.0F);
        } else {
            faceFree();
        }
        if (pickupOn() && tickCount % 4 == 0) pickUpNearbyItems(sl);
        if (lightsOn() && tickCount % 10 == 0) lightUp(sl);
        tickEmotes(sl, t != null);
        move(sl, low, t != null);
    }

    private void move(ServerLevel sl, boolean low, boolean engaged) {
        if (isHeld()) {
            navigation.stop();
            return;
        }
        ServerPlayer o = ownerHere();
        if (returning) {
            if (o == null) {
                returning = false;
                return;
            }
            chase(o.position().add(0, 1.9, 0).add(o.getLookAngle().multiply(1, 0, 1).scale(0.8)), 1.5, 96.0);
            if (position().distanceTo(o.position().add(0, 1.2, 0)) < 3.0) {
                returning = false;
                setMode(Mode.FOLLOW);
            }
            return;
        }
        switch (mode()) {
            case FOLLOW -> {
                if (o == null) return;
                if (serverEmote == Emote.BOOP) {
                    // pops right in front of the owner's face for a moment
                    Vec3 face = o.getEyePosition().add(o.getLookAngle().multiply(1, 0, 1).normalize().scale(1.3));
                    flyTo(face, 1.0);
                    faceYaw((float) (Mth.atan2(o.getZ() - getZ(), o.getX() - getX()) * Mth.RAD_TO_DEG) - 90.0F);
                    return;
                }
                followOwner(o, 1.9, 1.2);
            }
            case PATROL -> {
                if (low || engaged) {
                    navigation.stop();
                    return;
                }
                tickPatrol(sl);
            }
            case STAY -> navigation.stop();
        }
    }

    private void tickPatrol(ServerLevel sl) {
        Vec3 center = Vec3.atCenterOf(patrolCenter);
        double radius = PATROL_RADIUS;
        if (position().distanceToSqr(center) > (radius + 6) * (radius + 6)) {
            chase(center.add(0, 1.0, 0), 1.0, 128.0);
            return;
        }
        // drops inside the circle are worth a detour (farms)
        if (pickupOn() && tickCount % 20 == 0 && hasRoom()) {
            List<ItemEntity> drops = sl.getEntitiesOfClass(ItemEntity.class, new AABB(patrolCenter).inflate(radius, 4.0, radius), BuddyBot::collectable);
            ItemEntity nearest = null;
            for (ItemEntity ie : drops) {
                if (nearest == null || ie.distanceToSqr(this) < nearest.distanceToSqr(this)) nearest = ie;
            }
            if (nearest != null) {
                patrolPoint = nearest.position().add(0, 0.6, 0);
                patrolTicks = 60;
            }
        }
        if (patrolPoint == null || --patrolTicks <= 0 || position().distanceToSqr(patrolPoint) < 1.5) {
            patrolPoint = pickPatrolPoint(sl, center, radius);
            patrolTicks = 80 + random.nextInt(80);
        }
        flyTo(patrolPoint, 0.7);
    }

    private Vec3 pickPatrolPoint(ServerLevel sl, Vec3 center, double radius) {
        for (int i = 0; i < 8; i++) {
            double angle = random.nextDouble() * Math.PI * 2.0;
            double r = Math.sqrt(random.nextDouble()) * radius;
            Vec3 p = new Vec3(center.x + Math.cos(angle) * r, center.y + 1.0 + random.nextDouble() * 3.0, center.z + Math.sin(angle) * r);
            BlockPos bp = BlockPos.containing(p);
            if (sl.isLoaded(bp) && sl.getBlockState(bp).isAir() && sl.getBlockState(bp.above()).isAir()) return p;
        }
        return center.add(0, 1.5, 0);
    }

    // ---------------------------------------------------------------- sound

    /** No flight buzz: it follows the owner all day. */
    @Override
    protected @Nullable SoundEvent getAmbientSound() {
        return null;
    }

    // ---------------------------------------------------------------- lights

    private FakePlayer fakeOwner(ServerLevel sl) {
        return FakePlayerFactory.get(sl, new GameProfile(owner != null ? owner : FALLBACK_OWNER, "[Robotica]"));
    }

    /** Light-up mode: a Spark Lamp where it flies when that spot is dark. */
    private void lightUp(ServerLevel sl) {
        if (lampCooldown > 0 || isEnergyLow()) return;
        BlockPos spot = blockPosition();
        if (sl.isLoaded(spot) && LampPlacer.isDark(sl, spot)) placeLamp(sl, spot);
    }

    /** Places a Spark Lamp for FE as the owner (build rights, claims). Returns true when placed. */
    private boolean placeLamp(ServerLevel sl, BlockPos spot) {
        int cost = GearConfig.lampCost();
        if (!consume(cost)) return false;
        boolean placed;
        try {
            FakePlayer fake = fakeOwner(sl);
            placed = SparkLamps.canPlace(fake, sl, spot) && SparkLamps.place(fake, sl, spot, Direction.UP);
        } catch (RuntimeException e) {
            placed = false;
        }
        if (!placed) {
            receiveEnergy(cost);
            return false;
        }
        lampCooldown = Math.max(10, GearConfig.lampCooldown());
        return true;
    }

    // ---------------------------------------------------------------- emotes

    /** Starts an emote on every client (and the server's own bookkeeping), with its sound. */
    public void emote(Emote e) {
        serverEmote = e;
        serverEmoteTicks = e.ticks;
        level().broadcastEntityEvent(this, (byte) (EVENT_EMOTE + e.ordinal()));
        switch (e) {
            case SPIN, CHEER -> CoreSounds.play(this, CoreSounds.ROBOT_BEEP, SoundSource.NEUTRAL, 0.6F, 1.5F + random.nextFloat() * 0.3F);
            case WAVE, BOOP -> CoreSounds.play(this, CoreSounds.ROBOT_BEEP, SoundSource.NEUTRAL, 0.5F, 1.8F);
            case HICCUP -> CoreSounds.play(this, CoreSounds.ROBOT_BEEP_LOW, SoundSource.NEUTRAL, 0.5F, 2.0F);
            case NOD -> CoreSounds.play(this, CoreSounds.ROBOT_BEEP_LOW, SoundSource.NEUTRAL, 0.5F, 1.3F);
            case SHAKE -> CoreSounds.play(this, CoreSounds.ROBOT_ERROR, SoundSource.NEUTRAL, 0.35F, 1.2F);
            default -> {
            }
        }
    }

    private void reactError() {
        CoreSounds.play(this, CoreSounds.ROBOT_ERROR, SoundSource.NEUTRAL, 0.7F, 1.0F);
        level().broadcastEntityEvent(this, (byte) (EVENT_EMOTE + Emote.SHAKE.ordinal()));
    }

    private void tickEmotes(ServerLevel sl, boolean engaged) {
        if (serverEmoteTicks > 0 && --serverEmoteTicks == 0) {
            serverEmote = Emote.NONE;
            curious = null;
        }
        ServerPlayer o = ownerHere();
        // the owner got hurt: a worried shake
        if (o != null && o.hurtTime > 0 && o.hurtTime > lastOwnerHurt && distanceToSqr(o) < 256.0 && serverEmote != Emote.SHAKE) emote(Emote.SHAKE);
        lastOwnerHurt = o != null ? o.hurtTime : 0;
        if (engaged || isHeld() || serverEmote != Emote.NONE) return;
        if (--emoteIn > 0) return;
        emoteIn = 500 + random.nextInt(900);
        if (o == null || distanceToSqr(o) > 144.0) return;
        if (isLowEnergy()) {
            emote(Emote.SLEEPY);
            return;
        }
        Emote pick = IDLE[random.nextInt(IDLE.length)];
        if (pick == Emote.BOOP && mode() != Mode.FOLLOW) pick = Emote.NOD;
        if (pick == Emote.LOOK_AROUND) {
            // stares at a nearby animal, if there is one
            List<Animal> animals = sl.getEntitiesOfClass(Animal.class, getBoundingBox().inflate(8.0), LivingEntity::isAlive);
            curious = animals.isEmpty() ? null : animals.get(random.nextInt(animals.size()));
        }
        emote(pick);
    }

    @Override
    public void handleEntityEvent(byte id) {
        if (id == EVENT_CHOMP) {
            chompTicks = 8;
        } else if (id == EVENT_RECOIL) {
            recoilTicks = 5;
        } else if (id >= EVENT_EMOTE && id < EVENT_EMOTE + Emote.values().length) {
            clientEmote = Emote.byId(id - EVENT_EMOTE);
            clientEmoteAge = 0;
            emoteParticles(clientEmote);
        } else {
            super.handleEntityEvent(id);
        }
    }

    /** Client: the emote playing now (NONE when idle). */
    public Emote clientEmote() {
        return clientEmote;
    }

    /** Client: ticks since the emote started, with the partial tick, so animations are smooth. */
    public float emoteTime(float partialTick) {
        return clientEmote == Emote.NONE ? 0.0F : clientEmoteAge + partialTick;
    }

    /** Client: 1 right after it grabbed loot, falling to 0. */
    public float chomp(float partialTick) {
        return Math.max(0.0F, (chompTicks - partialTick) / 8.0F);
    }

    /** Client: how far the lid is open for the GUI, 0 to 1. */
    public float lidOpen(float partialTick) {
        return Mth.lerp(partialTick, lidOpenO, lidOpen);
    }

    /** Client: 1 right after a shot, falling to 0. */
    public float recoil(float partialTick) {
        return Math.max(0.0F, (recoilTicks - partialTick) / 5.0F);
    }

    // ---------------------------------------------------------------- tunnel

    private int feeBlock() {
        return CoreConfig.scaleEnergy(DronesConfig.buddyFePerBlock());
    }

    private int digInterval() {
        if (digTicksOverride > 0) return digTicksOverride;
        int base = CoreConfig.scaleInterval(DronesConfig.buddyDigTicks());
        return Math.max(1, (int) Math.round(base / DronesConfig.buddyDigSpeed(tier())));
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
        if (t == null) return;
        setFlag(FLAG_DIGGING, true);
        faceYaw(t.dir.toYRot());
        Vec3 hover = hoverPoint(t);
        if (t.phase != PHASE_MOVE) flyDirect(hover, 1.0);
        if (every(20)) CoreSounds.play(this, CoreSounds.DRILL_GRIND, SoundSource.NEUTRAL, 0.35F, 0.95F + random.nextFloat() * 0.1F);
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

    /** Positions whose fluids matter for a slice: the slice, the ring around it and the slice after it. */
    private List<BlockPos> hazardArea(Tunnel t, int k) {
        int r = radius();
        List<BlockPos> list = new ArrayList<>(100);
        for (int lat = -r - 1; lat <= r + 1; lat++) {
            for (int vert = -2; vert <= top() + 1; vert++) list.add(cell(t, k, lat, vert));
        }
        for (int[] c : sliceCells()) list.add(cell(t, k + 1, c[0], c[1]));
        return list;
    }

    private boolean sliceLoaded(ServerLevel sl, Tunnel t, int k) {
        for (BlockPos p : hazardArea(t, k)) {
            if (!sl.isLoaded(p)) return false;
        }
        return true;
    }

    private boolean sliceInBounds(ServerLevel sl, Tunnel t, int k) {
        for (int[] c : sliceCells()) {
            BlockPos p = cell(t, k, c[0], c[1]);
            if (!sl.getWorldBorder().isWithinBounds(p) || !sl.isInWorldBounds(p) || p.getY() <= sl.getMinBuildHeight() + 1) return false;
        }
        return true;
    }

    private boolean sliceAllAir(ServerLevel sl, Tunnel t, int k) {
        for (int[] c : sliceCells()) {
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

    /** Fires a break event as the owner's fake player so claim and protection mods can veto. */
    private boolean mayBreak(ServerLevel sl, BlockPos pos, BlockState state) {
        if (!sl.getWorldBorder().isWithinBounds(pos) || !sl.isInWorldBounds(pos)) return false;
        try {
            FakePlayer fake = fakeOwner(sl);
            if (sl.getServer().isUnderSpawnProtection(sl, pos, fake)) return false;
            BlockEvent.BreakEvent event = new BlockEvent.BreakEvent(sl, pos, state, fake);
            NeoForge.EVENT_BUS.post(event);
            return !event.isCanceled();
        } catch (RuntimeException e) {
            return true;
        }
    }

    private void mineSlice(ServerLevel sl, Tunnel t) {
        int[][] cells = sliceCells();
        while (t.cell < cells.length) {
            int[] c = cells[t.cell];
            BlockPos pos = cell(t, t.k, c[0], c[1]);
            BlockState state = sl.getBlockState(pos);
            CellKind kind = classify(sl, pos, state);
            if (kind == CellKind.AIR) {
                t.cell++;
                continue;
            }
            if (kind == CellKind.SKIP) {
                if (t.cell == 0) {
                    // Its own line is blocked (unbreakable, protected, a machine): it cannot go on.
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
        List<ItemStack> drops = Block.getDrops(state, sl, pos, null, fakeOwner(sl), tool());
        minedTotal++;
        if (minedTotal % 2 == 0) sl.levelEvent(2001, pos, Block.getId(state));
        sl.setBlock(pos, Blocks.AIR.defaultBlockState(), Block.UPDATE_ALL);
        state.spawnAfterBreak(sl, pos, tool(), false);
        for (ItemStack drop : drops) collect(drop);
        drillTicks = 8;
        setActive(true);
        sl.sendParticles(ParticleTypes.ELECTRIC_SPARK, pos.getX() + 0.5, pos.getY() + 0.5, pos.getZ() + 0.5, 2, 0.3, 0.3, 0.3, 0.05);
        if (minedTotal % 4 == 0) CoreSounds.play(sl, pos, CoreSounds.EXCAVATOR_DIG, SoundSource.NEUTRAL, 0.5F, 0.9F + random.nextFloat() * 0.2F);
    }

    /** End of a slice: wait for falling blocks, bridge the floor, light the slice behind it, step forward. */
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
        // two slices back the light engine has caught up: a lamp in a top corner when it is dark there
        if (t.counted >= 2) {
            int r = radius();
            BlockPos spot = cell(t, t.k - 2, (t.counted / 4) % 2 == 0 ? -r : r, top());
            if (sl.isLoaded(spot) && sl.getBlockState(spot).isAir() && LampPlacer.isDark(sl, spot)) {
                lampCooldown = 0;
                placeLamp(sl, spot);
            }
        }
        t.counted++;
        t.k++;
        t.phase = PHASE_MOVE;
        t.moveTicks = 0;
    }

    private boolean hasFallingAbove(ServerLevel sl, Tunnel t) {
        for (int lat = -radius(); lat <= radius(); lat++) {
            if (sl.getBlockState(cell(t, t.k, lat, top() + 1)).getBlock() instanceof FallingBlock) return true;
        }
        return false;
    }

    /** Gaps in the floor of the finished slice are bridged with cobblestone it mined, so the tunnel can be walked. */
    private void fillFloor(ServerLevel sl, Tunnel t) {
        for (int lat = -radius(); lat <= radius(); lat++) {
            BlockPos p = cell(t, t.k, lat, -2);
            if (!sl.isLoaded(p)) continue;
            if (sl.getBlockState(p).isAir() && takeOne(Items.COBBLESTONE)) {
                sl.setBlock(p, Blocks.COBBLESTONE.defaultBlockState(), Block.UPDATE_ALL);
            }
        }
    }

    /** Ends the job: the reason is stored, told to the owner, and it flies back (when the owner is around). */
    public void stopTunnel(Stop reason) {
        int blocks = tunnel != null ? tunnel.counted : 0;
        lastStop = reason;
        lastStopBlocks = blocks;
        stopTunnelQuiet();
        returning = ownerHere() != null;
        Component text = reason == Stop.DONE ? msg("buddy.done", blocks) : msg("buddy." + reason.key());
        say(text, true);
        if (reason == Stop.DONE) emote(Emote.CHEER);
        else reactError();
    }

    private void stopTunnelQuiet() {
        tunnel = null;
        drillTicks = 0;
        setActive(false);
        setFlag(FLAG_DIGGING, false);
    }

    // ---------------------------------------------------------------- gui

    /** Players whose client tracks this bot. Only they can open it with the backpack key: their client must know it. */
    private final Set<UUID> seenBy = new HashSet<>();

    @Override
    public void startSeenByPlayer(ServerPlayer player) {
        super.startSeenByPlayer(player);
        seenBy.add(player.getUUID());
    }

    @Override
    public void stopSeenByPlayer(ServerPlayer player) {
        super.stopSeenByPlayer(player);
        seenBy.remove(player.getUUID());
    }

    public boolean isSeenBy(Player player) {
        return seenBy.contains(player.getUUID());
    }

    /** The lid opens while its GUI is open. */
    @Override
    public void holdForGui() {
        super.holdForGui();
        if (level().isClientSide) return;
        if (openTicks == 0) {
            setFlag(FLAG_OPEN, true);
            playSound(SoundEvents.CHEST_OPEN, 0.4F, 1.5F);
        }
        openTicks = 3;
    }

    /** Charging it gets a happy spin. */
    @Override
    protected InteractionResult mobInteract(Player player, InteractionHand hand) {
        int fe = getEnergy();
        boolean noBattery = battery.getStackInSlot(0).isEmpty();
        InteractionResult result = super.mobInteract(player, hand);
        if (!level().isClientSide && !isRemoved() && serverEmote == Emote.NONE
                && (getEnergy() > fe || noBattery && !battery.getStackInSlot(0).isEmpty())) {
            emote(Emote.SPIN);
        }
        return result;
    }

    /** Back to full health: a happy spin. */
    @Override
    protected void onRepaired() {
        if (serverEmote == Emote.NONE) emote(Emote.SPIN);
    }

    @Override
    protected void openGui(ServerPlayer player) {
        player.openMenu(new SimpleMenuProvider((id, inv, p) -> new BuddyBotMenu(id, inv, this), getDisplayName()),
                buf -> buf.writeVarInt(getId()));
    }

    /** Backpack key: opens the GUI from up to {@code range} blocks away. */
    public void openRemote(ServerPlayer player, double range) {
        player.openMenu(new SimpleMenuProvider((id, inv, p) -> new BuddyBotMenu(id, inv, this).remote(range), getDisplayName()),
                buf -> buf.writeVarInt(getId()));
    }

    // ---------------------------------------------------------------- persistence

    @Override
    protected void writeSettings(CompoundTag tag, HolderLookup.Provider registries) {
        tag.put("Storage", storage.serializeNBT(registries));
        tag.put("Battery", battery.serializeNBT(registries));
        tag.putInt("Stance", stance().ordinal());
        tag.putBoolean("Pickup", pickupOn());
        tag.putBoolean("Lights", lightsOn());
        tag.putBoolean("Void", voidOn());
    }

    @Override
    protected void readSettings(CompoundTag tag, HolderLookup.Provider registries) {
        if (tag.contains("Storage")) storage.deserializeNBT(registries, tag.getCompound("Storage"));
        if (tag.contains("Battery")) battery.deserializeNBT(registries, tag.getCompound("Battery"));
        entityData.set(DATA_STANCE, Stance.byId(tag.getInt("Stance")).ordinal());
        setPickup(!tag.contains("Pickup") || tag.getBoolean("Pickup"));
        setLights(tag.getBoolean("Lights"));
        setVoid(tag.getBoolean("Void"));
    }

    @Override
    protected void writeRuntime(CompoundTag tag) {
        tag.putInt("Mode", mode().ordinal());
        tag.putLong("Patrol", patrolCenter.asLong());
        tag.putBoolean("Returning", returning);
        if (tunnel != null) tag.put("Tunnel", tunnel.save());
        if (lastStop != null) {
            tag.putInt("LastStop", lastStop.ordinal());
            tag.putInt("LastStopBlocks", lastStopBlocks);
        }
    }

    @Override
    protected void readRuntime(CompoundTag tag) {
        entityData.set(DATA_MODE, Mode.byId(tag.getInt("Mode")).ordinal());
        patrolCenter = BlockPos.of(tag.getLong("Patrol"));
        returning = tag.getBoolean("Returning");
        tunnel = tag.contains("Tunnel") ? Tunnel.load(tag.getCompound("Tunnel")) : null;
        setFlag(FLAG_DIGGING, tunnel != null);
        lastStop = tag.contains("LastStop") ? Stop.byId(tag.getInt("LastStop")) : null;
        lastStopBlocks = tag.getInt("LastStopBlocks");
    }

    @Override
    protected void onPickedUp() {
        tunnel = null;
    }
}
