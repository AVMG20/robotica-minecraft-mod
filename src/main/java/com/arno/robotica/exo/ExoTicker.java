package com.arno.robotica.exo;

import com.arno.robotica.Robotica;
import com.arno.robotica.core.CoreSounds;
import com.arno.robotica.core.module.ModuleItems;
import com.arno.robotica.core.module.ModuleKind;
import com.arno.robotica.core.module.Modules;
import com.arno.robotica.core.energy.ItemEnergy;
import com.arno.robotica.core.item.CellItem;
import com.arno.robotica.core.item.CoreItems;
import com.arno.robotica.exo.item.ExoArmorItem;
import com.arno.robotica.exo.net.ExoFxPayload;
import com.arno.robotica.exo.net.ExoJetHeatPayload;
import com.arno.robotica.exo.net.ExoSonarPayload;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Holder;
import net.minecraft.core.component.DataComponents;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.Tag;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.tags.DamageTypeTags;
import net.minecraft.tags.FluidTags;
import net.minecraft.util.Mth;
import net.minecraft.world.damagesource.DamageTypes;
import net.minecraft.world.effect.MobEffect;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.attributes.Attribute;
import net.minecraft.world.entity.ai.attributes.AttributeInstance;
import net.minecraft.world.entity.ai.attributes.AttributeModifier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.entity.player.Abilities;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.food.FoodProperties;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.capabilities.Capabilities;
import net.neoforged.neoforge.common.NeoForgeMod;
import net.neoforged.neoforge.common.util.FakePlayer;
import net.neoforged.neoforge.event.entity.living.LivingBreatheEvent;
import net.neoforged.neoforge.event.entity.living.LivingDamageEvent;
import net.neoforged.neoforge.event.entity.living.LivingFallEvent;
import net.neoforged.neoforge.event.entity.living.LivingIncomingDamageEvent;
import net.neoforged.neoforge.event.entity.living.LivingKnockBackEvent;
import net.neoforged.neoforge.energy.IEnergyStorage;
import net.neoforged.neoforge.network.PacketDistributor;

import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Server side logic of the Exo-Frame, run once per player tick (see {@link ExoEvents}).
 *
 * <p>Everything is evaluated from the worn items each tick, so removing a piece, a module, the energy or the toggle
 * undoes the effect by itself: attribute modifiers are transient and removed when no longer wanted, mob effects are
 * short, ambient and refreshed, flight is a granted {@code mayfly} that is revoked again (never touched in creative or
 * spectator). Running costs per tick (and what Solar Weave and the Kinetic Generator make) are collected per piece and
 * written to the items once every 10 ticks, so the armor stack does not change (and is not re-sent to every nearby
 * player) on every tick. One-shot costs (a dash, a jump, a hit, a fall, a heal, a meal, a ping) are drained from the
 * worn stacks at once, so swapping a piece cannot dodge them.
 *
 * <p>Cooldowns (Dash, Sonar, Med Injector, Overclock) are vanilla item cooldowns on the module item (the Servo Core for
 * Overclock), so the client sees them without extra packets. Their end times and the flight grant are also kept in the
 * player's persistent data, so a relog neither resets a cooldown nor drops a flying player, and a crash cannot leave a
 * stale {@code mayfly} behind (see {@link #onLogin}).
 */
public final class ExoTicker {
    private ExoTicker() {}

    private static final ResourceLocation SERVO_ID = Robotica.id("exo_servo_stride");
    public static final ResourceLocation STEP_ID = Robotica.id("exo_step_assist");
    private static final ResourceLocation JUMP_ID = Robotica.id("exo_spring_heels");
    private static final ResourceLocation SWIM_ID = Robotica.id("exo_hydro_fins");
    private static final ResourceLocation DIG_ID = Robotica.id("exo_hydro_fins_mining");
    private static final ResourceLocation REACH_ID = Robotica.id("exo_reach");
    /** Ticks between two Heat Shield I fire checks: a burning wearer is put out within a quarter second. */
    private static final int EXTINGUISH_GAP = 5;
    /** Radius in blocks of the water Cryo Treads freeze around the wearer. */
    private static final int CRYO_RADIUS = 3;
    private static final int FLUSH_INTERVAL = 10;
    private static final int CHEST = 1, FEET = 3;
    /** Persistent data: true while the suit (not a game mode or another mod) granted the player {@code mayfly}. */
    public static final String FLIGHT_KEY = "robotica_exo_flight";
    /** Persistent data (inside PlayerPersisted, kept through death): cooldown end times in game ticks. */
    public static final String COOLDOWN_KEY = "robotica_exo_cooldowns";
    public static final String CD_DASH = "dash", CD_MED = "med_injector", CD_SONAR = "sonar", CD_OVERCLOCK = "overclock";
    /**
     * Persistent data (inside PlayerPersisted, kept through death): Jet Assist heat, overheat and the game time they
     * were saved at, written when the runtime state goes (suit off, logout, death, respawn) and read when it comes back.
     */
    public static final String JET_KEY = "robotica_exo_jet";
    /** Fewest ticks between two handled key presses of the same action, and between two refusal beeps. */
    private static final int ACTION_GAP = 2, REFUSAL_GAP = 10;
    /** Most horizontal blocks per tick the Kinetic Generator counts (about a sprint jump). */
    private static final double KINETIC_MAX_STEP = 0.6;
    /** Effects the Hazard Seal clears. */
    private static final List<Holder<MobEffect>> HAZARDS = List.of(MobEffects.POISON, MobEffects.WITHER, MobEffects.HUNGER,
            MobEffects.CONFUSION, MobEffects.BLINDNESS);

    /** Per player runtime state, keyed by UUID and dropped on logout, death or when the suit comes off. */
    static final class State {
        final double[] pending = new double[4];
        final double[] made = new double[4];
        final int[] energy = new int[4];
        boolean full;
        ExoSuit.Active act = new ExoSuit.Active();
        ExoData.Core core = ExoData.Core.NONE;
        double factor = 1.0;
        int tick;
        double lastX = Double.NaN, lastZ;
        boolean moving;
        double walked;
        boolean flightGranted;
        boolean nv;
        boolean fireRes;
        /** Jet Assist: what the client asks for and what the pack does this tick ({@link ExoJet#OFF}, THRUST or HOVER). */
        int jetWant, jetMode;
        /** Level of the last worn Jet Assist (cooling and effects go on after it comes out). */
        int jetLevel = 1;
        /** Jet Assist heat in ticks of thrust (see {@link ExoJet}); kept with the player, not the chestplate. */
        double heat;
        boolean overheated;
        /** Jet Assist III: the Flight key switched hover on (read from the chestplate every tick). */
        boolean hoverMode;
        /** Thrust or hover stopped in mid air since the player last stood: a Glider may open now. */
        boolean thrustedAir;
        /** Tick of the last hit the Kinetic Shield absorbed completely (its knockback is cancelled). */
        int shieldTick = -100;
        int lastRefusal = -100;
        /** True while the suit flies the player (told to every client that tracks the player, for the thruster sound). */
        boolean exoFlying;
        int lastSpring = -100;
        final int[] lastAction = new int[8];

        State() {
            Arrays.fill(lastAction, -100);
        }
    }

    private static final Map<UUID, State> STATES = new HashMap<>();

    /** Test and debug access: true while the player has runtime state (wears any Exo piece). */
    public static boolean hasState(ServerPlayer player) {
        return STATES.containsKey(player.getUUID());
    }

    public static void clearAll() {
        STATES.clear();
    }

    /** Test and debug access: Jet Assist heat in ticks of thrust, 0 without state. */
    public static double jetHeat(ServerPlayer player) {
        State st = STATES.get(player.getUUID());
        return st == null ? 0 : st.heat;
    }

    /** Test and debug access: true while the Jet Assist cools down from an overheat. */
    public static boolean jetOverheated(ServerPlayer player) {
        State st = STATES.get(player.getUUID());
        return st != null && st.overheated;
    }

    /** Test and debug access: what the Jet Assist does now ({@link ExoJet#OFF}, THRUST or HOVER). */
    public static int jetMode(ServerPlayer player) {
        State st = STATES.get(player.getUUID());
        return st == null ? ExoJet.OFF : st.jetMode;
    }

    // ---------------------------------------------------------------- tick

    public static void tick(ServerPlayer p) {
        if (p instanceof FakePlayer) return;
        UUID id = p.getUUID();
        State st = STATES.get(id);
        if (!ExoSuit.wearingAny(p)) {
            if (st != null) {
                release(p, st, false, true);
                saveJet(p, st);
                STATES.remove(id);
            } else if (p.getPersistentData().getBoolean(FLIGHT_KEY)) {
                // A grant from before a restart, with no suit on any more.
                revokeFlight(p, new State(), false);
            }
            return;
        }
        if (st == null) st = newState(p);
        st.tick++;
        refresh(p, st);

        double dx = p.getX() - st.lastX;
        double dz = p.getZ() - st.lastZ;
        boolean first = Double.isNaN(st.lastX);
        double dist = first ? 0 : Math.sqrt(dx * dx + dz * dz);
        st.moving = dist > 0.02;
        st.walked = Math.min(dist, KINETIC_MAX_STEP);
        st.lastX = p.getX();
        st.lastZ = p.getZ();

        helmet(p, st);
        jetAssist(p, st);
        flight(p, st);
        glider(p, st);
        attributes(p, st);
        kineticGenerator(p, st);
        setBonus(p, st);
        medInjector(p, st);
        if ((st.tick & 1) == 0) {
            magnet(p, st);
            cryoTreads(p, st);
        }
        if (st.tick % 10 == 0) hazardSeal(p, st);
        if (st.tick % 20 == 0) {
            autoFeeder(p, st);
            solarWeave(p, st);
            chestCell(p);
            if (ExoConfig.cellRecharge()) rechargeFromCells(p);
        }
        if (st.tick % FLUSH_INTERVAL == 0) flush(p, st);
    }

    /**
     * A fresh state; the flight grant and the Jet Assist heat come from the persistent data so they survive a restart,
     * and the wearer's client learns the heat.
     */
    private static State newState(ServerPlayer p) {
        State st = new State();
        st.flightGranted = p.getPersistentData().getBoolean(FLIGHT_KEY);
        loadJet(p, st);
        STATES.put(p.getUUID(), st);
        sendHeat(p, st);
        return st;
    }

    /** Keeps the Jet Assist heat with the player while the runtime state is gone; a cold pack leaves nothing behind. */
    private static void saveJet(ServerPlayer p, State st) {
        CompoundTag persisted = persistedTag(p);
        if (st.heat <= 0 && !st.overheated) {
            persisted.remove(JET_KEY);
            return;
        }
        CompoundTag tag = new CompoundTag();
        tag.putDouble("heat", st.heat);
        tag.putBoolean("overheated", st.overheated);
        tag.putLong("time", gameTime(p));
        persisted.put(JET_KEY, tag);
    }

    /** The saved heat, cooled (off the ground rate) by the game time since it was saved. */
    private static void loadJet(ServerPlayer p, State st) {
        CompoundTag root = p.getPersistentData();
        if (!root.contains(Player.PERSISTED_NBT_TAG, Tag.TAG_COMPOUND)) return;
        CompoundTag persisted = root.getCompound(Player.PERSISTED_NBT_TAG);
        if (!persisted.contains(JET_KEY, Tag.TAG_COMPOUND)) return;
        CompoundTag tag = persisted.getCompound(JET_KEY);
        persisted.remove(JET_KEY);
        double heat = tag.getDouble("heat");
        if (!Double.isFinite(heat)) return;
        st.heat = ExoJet.cooled(Math.max(0, heat), gameTime(p) - tag.getLong("time"));
        st.overheated = tag.getBoolean("overheated") && st.heat > 0;
    }

    /** Tells the wearer's client the heat (its prediction starts cold on a new player object). */
    private static void sendHeat(ServerPlayer p, State st) {
        if (canReceive(p) && p.connection.hasChannel(ExoJetHeatPayload.TYPE)) {
            PacketDistributor.sendToPlayer(p, new ExoJetHeatPayload((float) st.heat, st.overheated));
        }
    }

    /** Writes the collected running costs and production to the armor items. Called every 10 ticks; public for tests. */
    public static void flush(ServerPlayer p) {
        State st = STATES.get(p.getUUID());
        if (st != null) flush(p, st);
    }

    private static void flush(ServerPlayer p, State st) {
        boolean any = false;
        for (int i = 0; i < 4; i++) {
            int amount = (int) st.pending[i];
            if (amount > 0) {
                ExoSuit.drain(p, ExoSuit.SLOTS[i], amount);
                st.pending[i] -= amount;
                any = true;
            }
            int made = (int) st.made[i];
            if (made > 0) {
                ExoSuit.charge(p, ExoSuit.SLOTS[i], made);
                st.made[i] -= made;
                any = true;
            }
        }
        if (any) refresh(p, st);
    }

    private static void refresh(ServerPlayer p, State st) {
        readEnergy(p, st);
        st.act = ExoSuit.active(p);
        st.core = ExoSuit.setBonus(p);
        st.factor = ExoSuit.costFactor(st.act);
    }

    private static void readEnergy(ServerPlayer p, State st) {
        st.full = true;
        for (int i = 0; i < 4; i++) {
            ItemStack piece = ExoSuit.piece(p, ExoSuit.SLOTS[i]);
            if (piece.isEmpty()) st.full = false;
            st.energy[i] = piece.isEmpty() ? 0 : ItemEnergy.get(piece);
        }
    }

    private static boolean has(State st, ModuleKind kind) {
        return st.act.has(kind);
    }

    private static int lvl(State st, ModuleKind kind) {
        return st.act.level(kind);
    }

    /** Energy (minus unwritten costs) a module in this piece slot can still use. */
    private static double available(State st, int piece) {
        if (st.full) {
            double e = 0, pend = 0;
            for (int i = 0; i < 4; i++) {
                e += st.energy[i];
                pend += st.pending[i];
            }
            return e - pend;
        }
        return st.energy[piece] - st.pending[piece];
    }

    /** True when the module is on and its energy covers {@code fe} (before the Power Regulator). */
    private static boolean afford(State st, ModuleKind kind, double fe) {
        return has(st, kind) && available(st, st.act.piece(kind)) >= Math.max(fe * st.factor, 1);
    }

    /** Books a running cost {@code fe} (before the Power Regulator) against the module's piece, written on the next flush. */
    private static void spend(State st, ModuleKind kind, double fe) {
        st.pending[st.act.piece(kind)] += fe * st.factor;
    }

    /** Pays a one-shot cost {@code fe} (before the Power Regulator) from the worn stacks right now. Returns the FE taken. */
    private static int spendNow(ServerPlayer p, State st, ModuleKind kind, double fe) {
        return payNow(p, st, st.act.piece(kind), fe * st.factor);
    }

    /** Drains {@code fe} (already final) for a module in piece slot {@code piece} from the worn stacks right now. */
    private static int payNow(ServerPlayer p, State st, int piece, double fe) {
        int amount = (int) Math.round(fe);
        if (amount <= 0) return 0;
        int taken = ExoSuit.drain(p, ExoSuit.SLOTS[piece], amount);
        readEnergy(p, st);
        return taken;
    }

    // ---------------------------------------------------------------- helmet

    private static void helmet(ServerPlayer p, State st) {
        int nvLevel = lvl(st, ModuleKind.NIGHT_VISION);
        double cost = nvLevel > 0 ? ExoConfig.perTick(ModuleKind.NIGHT_VISION, nvLevel) : 0;
        if (nvLevel > 0 && afford(st, ModuleKind.NIGHT_VISION, cost)) {
            spend(st, ModuleKind.NIGHT_VISION, cost);
            refreshEffect(p, MobEffects.NIGHT_VISION, 0, 300, 240);
            st.nv = true;
        } else if (st.nv) {
            st.nv = false;
            removeOurs(p, MobEffects.NIGHT_VISION);
        }
        // Robot HUD and thermal sight are client displays; they only draw power.
        double hud = ExoConfig.perTick(ModuleKind.ROBOT_HUD, 1);
        if (afford(st, ModuleKind.ROBOT_HUD, hud)) spend(st, ModuleKind.ROBOT_HUD, hud);
    }

    private static void refreshEffect(ServerPlayer p, Holder<MobEffect> effect, int amplifier, int duration, int below) {
        MobEffectInstance cur = p.getEffect(effect);
        if (cur == null || (cur.isAmbient() && cur.getDuration() < below && cur.getAmplifier() <= amplifier)) {
            p.addEffect(new MobEffectInstance(effect, duration, amplifier, true, false, false));
        }
    }

    /** An effect we applied is ambient, without particles and without an icon. A potion or beacon is never removed. */
    private static void removeOurs(ServerPlayer p, Holder<MobEffect> effect) {
        MobEffectInstance cur = p.getEffect(effect);
        if (cur != null && cur.isAmbient() && !cur.isVisible() && !cur.showIcon()) p.removeEffect(effect);
    }

    /**
     * Auto-Feeder: once a second, eats the best fitting plain food from the inventory when hungry. Foods with effects,
     * suspicious stew and chorus fruit are skipped; a bowl or bottle left over goes back into the inventory (or drops).
     */
    private static void autoFeeder(ServerPlayer p, State st) {
        if (!has(st, ModuleKind.AUTO_FEEDER) || creativeLike(p)) return;
        var food = p.getFoodData();
        if (food.getFoodLevel() > ExoConfig.autoFeederHunger()) return;
        double cost = ExoConfig.cost(ModuleKind.AUTO_FEEDER, 1);
        if (!afford(st, ModuleKind.AUTO_FEEDER, cost)) return;
        int missing = 20 - food.getFoodLevel();
        var inv = p.getInventory();
        ItemStack best = ItemStack.EMPTY;
        FoodProperties bestFood = null;
        for (int i = 0; i < inv.items.size() + 1; i++) {
            ItemStack stack = i < inv.items.size() ? inv.items.get(i) : inv.offhand.get(0);
            if (stack.isEmpty() || !plainFood(stack)) continue;
            FoodProperties props = stack.getFoodProperties(p);
            if (props == null || !props.effects().isEmpty() || props.nutrition() <= 0) continue;
            if (bestFood == null || better(props.nutrition(), bestFood.nutrition(), missing)) {
                best = stack;
                bestFood = props;
            }
        }
        if (bestFood == null) return;
        ItemStack left = p.eat(p.level(), best, bestFood);
        // Player.eat hands back the container (bowl, bottle) instead of the used up stack; never lose it.
        if (left != best && !left.isEmpty() && !p.getInventory().add(left)) p.drop(left, false);
        spendNow(p, st, ModuleKind.AUTO_FEEDER, cost);
    }

    /** Food the Auto-Feeder may eat: nothing whose use does more than feed (teleports, hidden effects). */
    private static boolean plainFood(ItemStack stack) {
        return !stack.is(Items.CHORUS_FRUIT) && !stack.is(Items.SUSPICIOUS_STEW) && !stack.has(DataComponents.SUSPICIOUS_STEW_EFFECTS);
    }

    /** Prefers the biggest food that does not overfill, else the smallest one. */
    private static boolean better(int candidate, int current, int missing) {
        boolean fitsC = candidate <= missing, fitsB = current <= missing;
        if (fitsC != fitsB) return fitsC;
        return fitsC ? candidate > current : candidate < current;
    }

    /** Solar Weave: once a second in daylight under open sky, charges the suit. */
    private static void solarWeave(ServerPlayer p, State st) {
        if (!has(st, ModuleKind.SOLAR_WEAVE)) return;
        ServerLevel level = p.serverLevel();
        BlockPos eyes = BlockPos.containing(p.getEyePosition());
        if (!level.isDay() || !level.canSeeSky(eyes) || level.isRainingAt(eyes)) return;
        st.made[st.act.piece(ModuleKind.SOLAR_WEAVE)] += ExoConfig.cost(ModuleKind.SOLAR_WEAVE, 1);
    }

    // ---------------------------------------------------------------- chestplate

    private static void jetAssist(ServerPlayer p, State st) {
        boolean grounded = ExoJet.grounded(p);
        if (grounded || p.onClimbable()) st.thrustedAir = false;
        // Creative and spectator: the pack is off (no thrust, hover or cost), it only cools.
        int level = ExoJet.creative(p) ? 0 : lvl(st, ModuleKind.JET_ASSIST);
        jetThrust(p, st, level, grounded);
    }

    /**
     * Jet Assist thrust and hover. The client moves the player (predicted under {@link ExoJet}'s rules) and says what
     * it wants with {@link ExoActions#JET_THRUST}, {@link ExoActions#JET_HOVER} and {@link ExoActions#JET_STOP}; this
     * checks it every tick, keeps the heat and the FE, resets the fall distance and the flying-kick counter, and cuts
     * out (telling the wearer's client) when the pack overheats or runs dry.
     */
    private static void jetThrust(ServerPlayer p, State st, int level, boolean grounded) {
        if (level > 0) st.jetLevel = level;
        st.hoverMode = level >= ExoJet.HOVER_LEVEL && ExoData.hover(ExoSuit.piece(p, EquipmentSlot.CHEST));
        int want = st.jetWant;
        boolean hover = want == ExoJet.HOVER;
        int mode = ExoJet.OFF;
        byte cut = ExoFxPayload.JET_OFF;
        if (want != ExoJet.OFF) {
            if (st.overheated) {
                st.jetWant = ExoJet.OFF;
                if (st.jetMode == ExoJet.OFF) jetFx(p, ExoFxPayload.JET_OVERHEAT, false, true);
            } else if (hover && level >= ExoJet.HOVER_LEVEL && !st.hoverMode) {
                st.jetWant = ExoJet.OFF;
                jetFx(p, ExoFxPayload.HOVER_OFF, false, true);
            } else if (level <= 0 || p.isSpectator() || (hover && level < ExoJet.HOVER_LEVEL)) {
                st.jetWant = ExoJet.OFF;
                jetFx(p, ExoFxPayload.JET_REFUSED, false, true);
            } else if (!afford(st, ModuleKind.JET_ASSIST, ExoJet.costPerTick(level, hover))) {
                st.jetWant = ExoJet.OFF;
                cut = ExoFxPayload.JET_EMPTY;
                if (st.jetMode == ExoJet.OFF) jetFx(p, ExoFxPayload.JET_REFUSED, false, true);
            } else if (grounded) {
                // Landing ends the hover; a thrust waits for the client's own stop (it lands at the same time).
                if (hover) st.jetWant = ExoJet.OFF;
            } else {
                mode = want;
            }
        }
        if (mode != ExoJet.OFF) {
            spend(st, ModuleKind.JET_ASSIST, ExoJet.costPerTick(level, hover));
            st.heat = Math.min(ExoJet.maxHeat(level), st.heat + ExoJet.heatPerTick(st.core == ExoData.Core.MAGMA, hover));
            p.resetFallDistance();
            // Thrust and hover hold the player up without mayfly: keep vanilla from kicking them for flying.
            if (p.connection != null) p.connection.aboveGroundTickCount = 0;
        } else if (st.heat > 0) {
            st.heat = Math.max(0.0, st.heat - ExoJet.coolPerTick(ExoJet.cooling(p)));
            if (st.heat <= 0 && st.overheated) {
                st.overheated = false;
                jetFx(p, ExoFxPayload.JET_READY, true, true);
                notifySound(p, CoreSounds.EXO_JET_READY.get(), 0.7F, 1.0F);
            }
        }
        // Full for the fitted level (also a lower level put in while hot): overheated until cold again.
        if (level > 0 && st.heat >= ExoJet.maxHeat(level)) {
            st.overheated = true;
            if (mode != ExoJet.OFF) {
                st.jetWant = ExoJet.OFF;
                mode = ExoJet.OFF;
                cut = ExoFxPayload.JET_OVERHEAT;
            }
        }
        setJetMode(p, st, mode, cut, grounded);
    }

    /**
     * Switches what the pack does and tells nearby players (rarely: only on a change). A cut-out also tells the wearer
     * and makes its sound. Thrust that stops in mid air lets a Glider open ({@link #gliderMayOpen}).
     */
    private static void setJetMode(ServerPlayer p, State st, int mode, byte cut, boolean grounded) {
        if (st.jetMode == mode) return;
        boolean wasOn = st.jetMode != ExoJet.OFF;
        st.jetMode = mode;
        if (mode != ExoJet.OFF) {
            jetFx(p, mode == ExoJet.THRUST ? ExoFxPayload.jetThrust(st.jetLevel) : ExoFxPayload.JET_HOVER, true, false);
            return;
        }
        if (!wasOn) return;
        if (!grounded) st.thrustedAir = true;
        if (cut == ExoFxPayload.JET_OFF) {
            jetFx(p, ExoFxPayload.JET_OFF, true, false);
            return;
        }
        jetFx(p, cut, true, true);
        if (cut == ExoFxPayload.JET_OVERHEAT) {
            CoreSounds.play(p, CoreSounds.EXO_JET_OVERHEAT, SoundSource.PLAYERS, 0.9F, 1.0F);
        } else {
            CoreSounds.play(p, CoreSounds.ROBOT_BEEP_LOW, SoundSource.PLAYERS, 0.6F, 1.2F);
        }
    }

    /**
     * A Jet Assist state change for the wearer ({@code self}) and the other players in the level ({@code others}); see
     * {@link ExoFxPayload}. Not range-limited: a watcher who saw the pack start must also see it stop.
     */
    private static void jetFx(ServerPlayer p, byte kind, boolean others, boolean self) {
        ExoFxPayload msg = null;
        for (ServerPlayer o : p.serverLevel().players()) {
            if (o == p ? !self : !others) continue;
            if (!canReceive(o)) continue;
            if (msg == null) msg = new ExoFxPayload(p.getId(), kind);
            PacketDistributor.sendToPlayer(o, msg);
        }
    }

    /**
     * The client's thrust intent ({@link ExoJet#OFF}, THRUST or HOVER), checked on the next tick. A stop takes effect at
     * once, so a Glider opening right after it finds the pack off. A thrust asked while gliding ends the glide at once
     * when the pack can fire (the client predicted the same); otherwise the glide goes on and the client is told.
     */
    public static void jetWant(ServerPlayer p, int mode) {
        State st = STATES.get(p.getUUID());
        if (st == null) {
            // The suit went on this tick: start its state now, so the thrust is checked (and corrected) like any other.
            if (mode == ExoJet.OFF || p instanceof FakePlayer || !ExoSuit.wearingAny(p)) return;
            st = newState(p);
        }
        if (mode == ExoJet.THRUST && p.isFallFlying()) {
            // Ending a glide does real work, so a flood of these is rate limited like a key press.
            if (!actionAllowed(p, ExoActions.JET_THRUST)) return;
            refresh(p, st);
            if (jetReady(p, st)) {
                p.stopFallFlying();
            } else {
                st.jetWant = ExoJet.OFF;
                // The client already stopped gliding: send the glide again (a plain set would not resend the same value).
                p.stopFallFlying();
                p.startFallFlying();
                jetFx(p, st.overheated ? ExoFxPayload.JET_OVERHEAT : ExoFxPayload.JET_REFUSED, false, true);
                return;
            }
        }
        // A burst that started and stopped within one server tick still counts as thrust in this fall (Glider gate).
        if (mode == ExoJet.OFF && st.jetWant != ExoJet.OFF && st.jetMode == ExoJet.OFF && !ExoJet.grounded(p)) st.thrustedAir = true;
        st.jetWant = mode;
        if (mode == ExoJet.OFF && st.jetMode != ExoJet.OFF) setJetMode(p, st, ExoJet.OFF, ExoFxPayload.JET_OFF, ExoJet.grounded(p));
    }

    /** True when the Jet Assist could thrust now (fitted, not creative, not overheated, energy for a tick). */
    private static boolean jetReady(ServerPlayer p, State st) {
        int level = lvl(st, ModuleKind.JET_ASSIST);
        return level > 0 && !ExoJet.creative(p) && !st.overheated && afford(st, ModuleKind.JET_ASSIST, ExoJet.costPerTick(level, false));
    }

    /**
     * True when a Glider may open now: the Jet Assist is off after thrust in this fall, or cannot fire (overheated, out
     * of energy). See {@link ExoJet#gliderMayOpen}.
     */
    public static boolean gliderMayOpen(ServerPlayer p) {
        State st = STATES.get(p.getUUID());
        return st == null || !jetReady(p, st) || (st.jetMode == ExoJet.OFF && st.thrustedAir);
    }

    /** True when the worn chestplate has Jet Assist III, so the Flight key can switch hover. */
    public static boolean hoverAvailable(ServerPlayer p) {
        State st = state(p);
        return st != null && lvl(st, ModuleKind.JET_ASSIST) >= ExoJet.HOVER_LEVEL;
    }

    /**
     * Switches Jet Assist III hover mode, kept on the chestplate. Returns false below level III. {@code tell}: show the
     * new mode and click.
     */
    public static boolean setHover(ServerPlayer p, boolean on, boolean tell) {
        if (!hoverAvailable(p)) return false;
        State st = STATES.get(p.getUUID());
        ExoData.setHover(ExoSuit.piece(p, EquipmentSlot.CHEST), on);
        st.hoverMode = on;
        if (!on && st.jetWant == ExoJet.HOVER) st.jetWant = ExoJet.OFF;
        jetFx(p, on ? ExoFxPayload.HOVER_ON : ExoFxPayload.HOVER_OFF, false, true);
        if (tell) {
            p.displayClientMessage(Component.translatable("exo.robotica.hover", Component.translatable(on ? "exo.robotica.on" : "exo.robotica.off")), true);
            notifySound(p, CoreSounds.TOOL_MODE.get(), 0.6F, on ? 1.3F : 0.8F);
        }
        return true;
    }

    /** Flight key without a Flight module: Jet Assist III switches hover mode. Returns false below level III. */
    public static boolean toggleHover(ServerPlayer p) {
        return hoverAvailable(p) && setHover(p, !ExoData.hover(ExoSuit.piece(p, EquipmentSlot.CHEST)), true);
    }

    /** Flight cost per tick after the Antigrav Core discount. */
    private static double flightCost(State st) {
        double cost = ExoConfig.perTick(ModuleKind.FLIGHT, 1);
        return st.core == ExoData.Core.ANTIGRAV ? cost * ExoConfig.antigravFlightFactor() : cost;
    }

    /** True when the worn suit may grant flight now: a switched-on Flight module with energy for a tick, not creative. */
    private static boolean flightValid(ServerPlayer p, State st) {
        return !creativeLike(p) && has(st, ModuleKind.FLIGHT)
                && available(st, st.act.piece(ModuleKind.FLIGHT)) >= Math.max(flightCost(st) * st.factor, 1);
    }

    private static void flight(ServerPlayer p, State st) {
        double cost = flightCost(st);
        Abilities ab = p.getAbilities();
        if (flightValid(p, st)) {
            if (!ab.mayfly) {
                ab.mayfly = true;
                setFlightGranted(p, st, true);
                p.onUpdateAbilities();
            }
            if (ab.flying) spend(st, ModuleKind.FLIGHT, cost);
            // Trail and thruster loop are drawn and played by every nearby client (ExoFx).
            setExoFlying(p, st, ab.flying);
        } else {
            if (st.flightGranted) {
                ItemStack chest = ExoSuit.piece(p, EquipmentSlot.CHEST);
                boolean moduleStillWorn = !chest.isEmpty() && Modules.installed(chest, ModuleKind.FLIGHT) > 0;
                revokeFlight(p, st, moduleStillWorn);
            }
            setExoFlying(p, st, false);
        }
    }

    /** Tells the clients when the suit starts or stops flying the player; stopping on the ground is a landing. */
    private static void setExoFlying(ServerPlayer p, State st, boolean flying) {
        if (st.exoFlying == flying) return;
        st.exoFlying = flying;
        boolean landed = !flying && p.onGround();
        fx(p, flying ? ExoFxPayload.FLIGHT_ON : landed ? ExoFxPayload.LANDED : ExoFxPayload.FLIGHT_OFF);
        if (landed) CoreSounds.play(p, CoreSounds.EXO_LAND, SoundSource.PLAYERS, 1.0F, 1.0F);
    }

    /** Players further away than this do not get Exo-Frame effects (they can hardly see them). */
    private static final double FX_RANGE = 96;

    /**
     * An effect for the wearer and every player near them; each client draws it (ExoFx). Flight state changes go to the
     * whole level, so a watcher never misses the end of a flight. Rare, so a plain loop.
     */
    private static void fx(ServerPlayer p, byte kind) {
        boolean state = kind == ExoFxPayload.FLIGHT_ON || kind == ExoFxPayload.FLIGHT_OFF || kind == ExoFxPayload.LANDED;
        ExoFxPayload msg = null;
        for (ServerPlayer o : p.serverLevel().players()) {
            if (!state && o.distanceToSqr(p) > FX_RANGE * FX_RANGE || !canReceive(o)) continue;
            if (msg == null) msg = new ExoFxPayload(p.getId(), kind);
            PacketDistributor.sendToPlayer(o, msg);
        }
    }

    private static boolean canReceive(ServerPlayer o) {
        return !(o instanceof FakePlayer) && o.connection != null && o.connection.hasChannel(ExoFxPayload.TYPE);
    }

    /** A player starts seeing {@code target}: tell them when the target's suit flies or its jetpack fires. */
    public static void onStartTracking(ServerPlayer watcher, ServerPlayer target) {
        State st = STATES.get(target.getUUID());
        if (st == null || !canReceive(watcher)) return;
        if (st.exoFlying) PacketDistributor.sendToPlayer(watcher, new ExoFxPayload(target.getId(), ExoFxPayload.FLIGHT_ON));
        if (st.jetMode != ExoJet.OFF) {
            byte kind = st.jetMode == ExoJet.THRUST ? ExoFxPayload.jetThrust(st.jetLevel) : ExoFxPayload.JET_HOVER;
            PacketDistributor.sendToPlayer(watcher, new ExoFxPayload(target.getId(), kind));
        }
    }

    /** Remembers (in the state and the player's saved data) whether the suit granted flight. */
    private static void setFlightGranted(ServerPlayer p, State st, boolean granted) {
        st.flightGranted = granted;
        if (granted) p.getPersistentData().putBoolean(FLIGHT_KEY, true);
        else p.getPersistentData().remove(FLIGHT_KEY);
    }

    /** Creative and spectator players fly by themselves; the suit never grants or revokes anything for them. */
    private static boolean creativeLike(ServerPlayer p) {
        return ExoJet.creative(p);
    }

    /** Takes back the flight we granted. Never touches creative or spectator abilities. {@code soft}: give a short slow fall. */
    private static void revokeFlight(ServerPlayer p, State st, boolean soft) {
        setFlightGranted(p, st, false);
        if (creativeLike(p)) return;
        Abilities ab = p.getAbilities();
        if (!ab.mayfly && !ab.flying) return;
        ab.mayfly = false;
        ab.flying = false;
        p.onUpdateAbilities();
        if (soft && !p.onGround()) {
            p.addEffect(new MobEffectInstance(MobEffects.SLOW_FALLING, 100, 0, true, false, true));
            notifySound(p, CoreSounds.ROBOT_ERROR.get(), 0.6F, 0.8F);
        }
        p.resetFallDistance();
    }

    /**
     * Glider: the chestplate glides like an Elytra ({@link com.arno.robotica.exo.item.ExoArmorItem#canElytraFly}); this
     * charges the time in the air.
     */
    private static void glider(ServerPlayer p, State st) {
        if (!p.isFallFlying() || !has(st, ModuleKind.GLIDER)) return;
        double cost = ExoConfig.perTick(ModuleKind.GLIDER, 1);
        if (afford(st, ModuleKind.GLIDER, cost)) spend(st, ModuleKind.GLIDER, cost);
        else p.stopFallFlying();
    }

    /** Med Injector: heals once when health drops low, then waits out its cooldown. */
    private static void medInjector(ServerPlayer p, State st) {
        int level = lvl(st, ModuleKind.MED_INJECTOR);
        if (level <= 0 || !p.isAlive() || p.getHealth() > p.getMaxHealth() * ExoConfig.medThreshold()) return;
        Item item = ModuleItems.get(ModuleKind.MED_INJECTOR, level).get();
        if (p.getCooldowns().isOnCooldown(item)) return;
        double cost = ExoConfig.cost(ModuleKind.MED_INJECTOR, level);
        if (!afford(st, ModuleKind.MED_INJECTOR, cost)) return;
        spendNow(p, st, ModuleKind.MED_INJECTOR, cost);
        p.heal(ExoConfig.medHeal(level));
        startCooldown(p, CD_MED, ExoConfig.medCooldown(level));
        CoreSounds.play(p, CoreSounds.EXO_MED_HISS, SoundSource.PLAYERS, 1.0F, 1.0F);
        CoreSounds.play(p, CoreSounds.EXO_MED_CHIME, SoundSource.PLAYERS, 1.0F, 1.0F);
        fx(p, ExoFxPayload.MED);
    }

    /** Hazard Seal: clears poison, wither, hunger, nausea and blindness, paying per effect. */
    private static void hazardSeal(ServerPlayer p, State st) {
        if (!has(st, ModuleKind.HAZARD_SEAL)) return;
        double cost = ExoConfig.cost(ModuleKind.HAZARD_SEAL, 1);
        boolean cleared = false;
        for (Holder<MobEffect> effect : HAZARDS) {
            if (!p.hasEffect(effect) || !afford(st, ModuleKind.HAZARD_SEAL, cost)) continue;
            p.removeEffect(effect);
            spendNow(p, st, ModuleKind.HAZARD_SEAL, cost);
            cleared = true;
        }
        if (cleared) {
            p.level().playSound(null, p.getX(), p.getY(), p.getZ(), SoundEvents.BREWING_STAND_BREW, SoundSource.PLAYERS, 0.4F, 1.6F);
            p.serverLevel().sendParticles(ParticleTypes.WAX_OFF, p.getX(), p.getY() + 1.0, p.getZ(), 6, 0.3, 0.5, 0.3, 0.0);
        }
    }

    // ---------------------------------------------------------------- legs and boots

    private static void attributes(ServerPlayer p, State st) {
        int servo = lvl(st, ModuleKind.SERVO_STRIDE);
        boolean servoOn = false;
        if (servo > 0) {
            double cost = ExoConfig.perTick(ModuleKind.SERVO_STRIDE, servo);
            servoOn = afford(st, ModuleKind.SERVO_STRIDE, cost);
            if (servoOn && st.moving) spend(st, ModuleKind.SERVO_STRIDE, cost);
        }
        setModifier(p, Attributes.MOVEMENT_SPEED, SERVO_ID, servoOn ? ExoConfig.servoSpeed(servo) : 0, AttributeModifier.Operation.ADD_MULTIPLIED_TOTAL);

        double stepCost = ExoConfig.perTick(ModuleKind.STEP_ASSIST, 1);
        // Off while sneaking: the edge check looks one step height down, so a full block would no longer count as an edge.
        boolean stepOn = !p.isShiftKeyDown() && afford(st, ModuleKind.STEP_ASSIST, stepCost);
        if (stepOn && st.moving) spend(st, ModuleKind.STEP_ASSIST, stepCost);
        setModifier(p, Attributes.STEP_HEIGHT, STEP_ID, stepOn ? ExoConfig.stepBonus() : 0, AttributeModifier.Operation.ADD_VALUE);

        int spring = lvl(st, ModuleKind.SPRING_HEELS);
        boolean springOn = spring > 0 && afford(st, ModuleKind.SPRING_HEELS, ExoConfig.cost(ModuleKind.SPRING_HEELS, spring));
        setModifier(p, Attributes.JUMP_STRENGTH, JUMP_ID, springOn ? ExoConfig.springBoost(spring) : 0, AttributeModifier.Operation.ADD_VALUE);

        double finsCost = ExoConfig.perTick(ModuleKind.HYDRO_FINS, 1);
        boolean fins = afford(st, ModuleKind.HYDRO_FINS, finsCost);
        if (fins && p.isInWater()) spend(st, ModuleKind.HYDRO_FINS, finsCost);
        setModifier(p, NeoForgeMod.SWIM_SPEED, SWIM_ID, fins ? ExoConfig.hydroSwim() : 0, AttributeModifier.Operation.ADD_MULTIPLIED_BASE);
        setModifier(p, Attributes.SUBMERGED_MINING_SPEED, DIG_ID, fins ? ExoConfig.hydroMining() : 0, AttributeModifier.Operation.ADD_VALUE);

        int reach = lvl(st, ModuleKind.REACH);
        boolean reachOn = false;
        if (reach > 0) {
            double cost = ExoConfig.perTick(ModuleKind.REACH, reach);
            reachOn = afford(st, ModuleKind.REACH, cost);
            if (reachOn) spend(st, ModuleKind.REACH, cost);
        }
        setModifier(p, Attributes.BLOCK_INTERACTION_RANGE, REACH_ID, reachOn ? ExoConfig.reachBlocks(reach) : 0, AttributeModifier.Operation.ADD_VALUE);
    }

    private static void setModifier(ServerPlayer p, Holder<Attribute> attribute, ResourceLocation id, double amount, AttributeModifier.Operation op) {
        AttributeInstance inst = p.getAttribute(attribute);
        if (inst == null) return;
        AttributeModifier cur = inst.getModifier(id);
        if (amount == 0) {
            if (cur != null) inst.removeModifier(id);
        } else if (cur == null || cur.amount() != amount) {
            inst.addOrUpdateTransientModifier(new AttributeModifier(id, amount, op));
        }
    }

    /**
     * Kinetic Generator: walking on the ground charges the suit a little. Only steps the player makes count: on solid
     * ground, out of any fluid (a water stream pushing the player does not), not riding, flying or gliding, and at
     * most about a sprint jump per tick.
     */
    private static void kineticGenerator(ServerPlayer p, State st) {
        if (!has(st, ModuleKind.KINETIC_GENERATOR) || st.walked <= 0.02) return;
        if (!p.onGround() || p.isPassenger() || p.getAbilities().flying || p.isFallFlying() || p.isSpectator()) return;
        if (p.isInWater() || p.isInLava() || p.isInFluidType() || p.isInPowderSnow) return;
        int perBlock = p.isSprinting() ? ExoConfig.kineticSprintPerBlock() : ExoConfig.cost(ModuleKind.KINETIC_GENERATOR, 1);
        st.made[st.act.piece(ModuleKind.KINETIC_GENERATOR)] += st.walked * perBlock;
    }

    private static void magnet(ServerPlayer p, State st) {
        int level = lvl(st, ModuleKind.MAGNET);
        if (level <= 0 || p.isSpectator()) return;
        double cost = ExoConfig.perTick(ModuleKind.MAGNET, level) * 2;
        if (!afford(st, ModuleKind.MAGNET, cost)) return;
        int radius = ExoConfig.magnetRadius(level);
        Vec3 target = p.position().add(0, 0.4, 0);
        AABB box = p.getBoundingBox().inflate(radius);
        List<ItemEntity> items = p.serverLevel().getEntitiesOfClass(ItemEntity.class, box,
                e -> e.isAlive() && !e.hasPickUpDelay() && e.distanceToSqr(p) > 0.6 && e.distanceToSqr(p) <= (double) radius * radius);
        // nearest first, so the cap of 32 per pass always takes the closest drops
        items.sort(java.util.Comparator.comparingDouble(e -> e.distanceToSqr(p)));
        int pulled = 0;
        for (ItemEntity item : items) {
            if (pulled >= 32) break;
            Vec3 dir = target.subtract(item.position());
            double len = dir.length();
            if (len < 1.0E-3) continue;
            Vec3 motion = item.getDeltaMovement().scale(0.6).add(dir.scale(0.2 / len));
            if (motion.lengthSqr() > 0.36) motion = motion.normalize().scale(0.6);
            item.setDeltaMovement(motion);
            item.hasImpulse = true;
            pulled++;
        }
        if (pulled > 0) spend(st, ModuleKind.MAGNET, cost);
    }

    /**
     * Cryo Treads: every other tick on the ground, still water sources around the feet turn into frosted ice that melts
     * again by itself (like Frost Walker). Charged only when something froze.
     */
    private static void cryoTreads(ServerPlayer p, State st) {
        if (!has(st, ModuleKind.CRYO_TREADS) || !p.onGround() || p.isPassenger() || p.isSpectator()) return;
        double cost = ExoConfig.perTick(ModuleKind.CRYO_TREADS, 1) * 2;
        if (!afford(st, ModuleKind.CRYO_TREADS, cost)) return;
        ServerLevel level = p.serverLevel();
        BlockState ice = Blocks.FROSTED_ICE.defaultBlockState();
        BlockPos below = p.blockPosition().below();
        BlockPos.MutableBlockPos pos = new BlockPos.MutableBlockPos();
        BlockPos.MutableBlockPos above = new BlockPos.MutableBlockPos();
        boolean froze = false;
        for (int dx = -CRYO_RADIUS; dx <= CRYO_RADIUS; dx++) {
            for (int dz = -CRYO_RADIUS; dz <= CRYO_RADIUS; dz++) {
                if (dx * dx + dz * dz > CRYO_RADIUS * CRYO_RADIUS) continue;
                pos.set(below.getX() + dx, below.getY(), below.getZ() + dz);
                BlockState state = level.getBlockState(pos);
                if (!state.is(Blocks.WATER) || !state.getFluidState().isSource()) continue;
                above.setWithOffset(pos, 0, 1, 0);
                if (!level.getBlockState(above).isAir() || !level.isUnobstructed(ice, pos, CollisionContext.empty())) continue;
                level.setBlockAndUpdate(pos, ice);
                level.scheduleTick(pos.immutable(), Blocks.FROSTED_ICE, Mth.nextInt(p.getRandom(), 60, 120));
                froze = true;
            }
        }
        if (froze) spend(st, ModuleKind.CRYO_TREADS, cost);
    }

    // ---------------------------------------------------------------- set bonus

    private static void setBonus(ServerPlayer p, State st) {
        boolean shield = heatShield(p, st);
        if (st.core == ExoData.Core.MAGMA || shield) {
            refreshEffect(p, MobEffects.FIRE_RESISTANCE, 0, 300, 240);
            st.fireRes = true;
            if (p.isOnFire() && st.tick % 10 == 0) p.clearFire();
        } else if (st.fireRes) {
            st.fireRes = false;
            removeOurs(p, MobEffects.FIRE_RESISTANCE);
        }
    }

    /**
     * Heat Shield, paid per second while it works. Level I puts a burning wearer out every {@link #EXTINGUISH_GAP} ticks
     * (lava and fire still hurt while you stand in them). Level II keeps fire and lava off entirely: returns true while
     * it does, and the caller gives fire resistance.
     */
    private static boolean heatShield(ServerPlayer p, State st) {
        int level = lvl(st, ModuleKind.HEAT_SHIELD);
        if (level <= 0) return false;
        double cost = ExoConfig.perTick(ModuleKind.HEAT_SHIELD, level);
        if (!afford(st, ModuleKind.HEAT_SHIELD, cost)) return false;
        boolean hot = p.isOnFire() || p.isInLava();
        if (level >= 2) {
            if (hot) spend(st, ModuleKind.HEAT_SHIELD, cost);
            return true;
        }
        if (hot) spend(st, ModuleKind.HEAT_SHIELD, cost);
        if (p.isOnFire() && st.tick % EXTINGUISH_GAP == 0) p.clearFire();
        return false;
    }

    /** The worn pieces in charging order (chestplate first), EMPTY for a missing one. */
    private static ItemStack[] chargeOrder(ServerPlayer p) {
        return new ItemStack[]{ExoSuit.piece(p, EquipmentSlot.CHEST), ExoSuit.piece(p, EquipmentSlot.LEGS),
                ExoSuit.piece(p, EquipmentSlot.HEAD), ExoSuit.piece(p, EquipmentSlot.FEET)};
    }

    private static boolean needsCharge(ItemStack[] pieces) {
        for (ItemStack piece : pieces) {
            if (!piece.isEmpty() && ItemEnergy.get(piece) < ItemEnergy.capacity(piece)) return true;
        }
        return false;
    }

    /** Moves up to {@code rate} FE from {@code cell} into the pieces, in order. Returns the FE moved. */
    private static int feed(ItemStack cell, ItemStack[] pieces, int rate) {
        int left = rate;
        for (ItemStack piece : pieces) {
            if (left <= 0 || piece.isEmpty()) continue;
            int space = ItemEnergy.capacity(piece) - ItemEnergy.get(piece);
            int move = Math.min(Math.min(left, space), ItemEnergy.get(cell));
            if (move <= 0) continue;
            ItemEnergy.drain(cell, move);
            ItemEnergy.addInternal(piece, move);
            left -= move;
        }
        return rate - left;
    }

    /**
     * The cell in the worn chestplate's cell slot tops up the suit once per second (config cellSlotPerSecond), then
     * with what is left of that rate the FE tools in the hands and the inventory (anything that holds FE but gives none
     * out, like {@link CellItem#inventoryTick}).
     */
    private static void chestCell(ServerPlayer p) {
        ItemStack chest = ExoSuit.piece(p, EquipmentSlot.CHEST);
        if (chest.isEmpty()) return;
        ItemStack cell = ExoData.cell(chest);
        if (!(cell.getItem() instanceof CellItem item) || ItemEnergy.get(cell) <= 0) return;
        int rate = (int) Math.min((long) item.getMaxExtract(cell) * 20, ExoConfig.cellSlotPerSecond());
        ItemStack[] pieces = chargeOrder(p);
        int moved = needsCharge(pieces) ? feed(cell, pieces, rate) : 0;
        var inv = p.getInventory();
        for (int i = -2; i < inv.items.size() && moved < rate && ItemEnergy.get(cell) > 0; i++) {
            if (i == inv.selected) continue;
            ItemStack tool = i == -2 ? p.getMainHandItem() : i == -1 ? p.getOffhandItem() : inv.items.get(i);
            moved += feedTool(cell, tool, rate - moved);
        }
        if (moved > 0) ExoData.setCell(chest, cell);
    }

    /** Moves up to {@code max} FE from {@code cell} into one FE tool. Returns the FE moved. */
    private static int feedTool(ItemStack cell, ItemStack tool, int max) {
        if (tool.isEmpty() || tool.getItem() instanceof BlockItem || tool.getItem() instanceof CellItem || tool.getItem() instanceof ExoArmorItem) return 0;
        IEnergyStorage storage = tool.getCapability(Capabilities.EnergyStorage.ITEM);
        if (storage == null || !storage.canReceive() || storage.canExtract()) return 0;
        int offer = Math.min(max, ItemEnergy.get(cell));
        int got = offer <= 0 ? 0 : storage.receiveEnergy(offer, false);
        if (got > 0) ItemEnergy.drain(cell, got);
        return Math.max(0, got);
    }

    /** Cells and Mainsprings in the inventory top up the worn suit once per second (config cellsRechargeSuit). */
    private static void rechargeFromCells(ServerPlayer p) {
        ItemStack[] pieces = chargeOrder(p);
        if (!needsCharge(pieces)) return;
        var inv = p.getInventory();
        int budget = ExoConfig.cellRechargePerSecond();
        for (int i = 0; i < inv.items.size() + 1 && budget > 0; i++) {
            ItemStack cell = i < inv.items.size() ? inv.items.get(i) : inv.offhand.get(0);
            if (!(cell.getItem() instanceof CellItem item) || ItemEnergy.get(cell) <= 0) continue;
            budget -= feed(cell, pieces, (int) Math.min((long) item.getMaxExtract(cell) * 20, budget));
        }
    }

    // ---------------------------------------------------------------- key actions

    private static State state(ServerPlayer p) {
        State st = STATES.get(p.getUUID());
        if (st != null) refresh(p, st);
        return st;
    }

    /**
     * Rate limit for key presses: true at most once every {@link #ACTION_GAP} ticks per player and action, so a flood
     * of packets costs next to nothing.
     */
    public static boolean actionAllowed(ServerPlayer p, int action) {
        State st = STATES.get(p.getUUID());
        if (st == null) return true;
        if (action < 0 || action >= st.lastAction.length) return false;
        if (p.tickCount - st.lastAction[action] < ACTION_GAP) return false;
        st.lastAction[action] = p.tickCount;
        return true;
    }

    /** A sound only the given player hears (menu clicks, refusals, toggles). */
    public static void notifySound(ServerPlayer p, SoundEvent sound, float volume, float pitch) {
        if (p.connection != null) p.playNotifySound(sound, SoundSource.PLAYERS, volume, pitch);
    }

    /** The refusal beep: only for the acting player, and at most every {@link #REFUSAL_GAP} ticks. */
    private static void refuse(ServerPlayer p, State st) {
        if (p.tickCount - st.lastRefusal < REFUSAL_GAP && p.tickCount >= st.lastRefusal) return;
        st.lastRefusal = p.tickCount;
        notifySound(p, CoreSounds.ROBOT_ERROR.get(), 0.4F, 1.2F);
    }

    /**
     * Dash Thrusters: a short horizontal burst where the player looks. Only the server moves the player (the client
     * does not predict it), so a refused dash moves nothing; refusals are silent. Returns true when it fired.
     */
    public static boolean dash(ServerPlayer p) {
        State st = state(p);
        if (st == null || !has(st, ModuleKind.DASH_THRUSTERS) || p.isPassenger() || p.isSpectator() || p.isFallFlying()) return false;
        Item item = ModuleItems.get(ModuleKind.DASH_THRUSTERS, 1).get();
        if (p.getCooldowns().isOnCooldown(item)) return false;
        double cost = ExoConfig.cost(ModuleKind.DASH_THRUSTERS, 1);
        if (!afford(st, ModuleKind.DASH_THRUSTERS, cost)) return false;
        spendNow(p, st, ModuleKind.DASH_THRUSTERS, cost);
        Vec3 dir = dashDirection(p.getYRot());
        double speed = ExoConfig.dashSpeed();
        Vec3 v = p.getDeltaMovement();
        p.setDeltaMovement(dir.x * speed, Math.max(v.y, 0.1), dir.z * speed);
        p.hurtMarked = true;
        p.resetFallDistance();
        int cooldown = ExoConfig.dashCooldown();
        if (st.core == ExoData.Core.ANTIGRAV) cooldown = (int) Math.round(cooldown * ExoConfig.antigravDashFactor());
        startCooldown(p, CD_DASH, cooldown);
        p.level().playSound(null, p.getX(), p.getY(), p.getZ(), SoundEvents.BREEZE_WIND_CHARGE_BURST.value(), SoundSource.PLAYERS, 0.35F, 1.4F);
        CoreSounds.play(p, CoreSounds.EXO_DASH, SoundSource.PLAYERS, 1.0F, 1.0F);
        fx(p, ExoFxPayload.DASH);
        return true;
    }

    /** Horizontal unit vector a player with this yaw looks along. */
    public static Vec3 dashDirection(float yRot) {
        float rad = yRot * Mth.DEG_TO_RAD;
        return new Vec3(-Mth.sin(rad), 0, Mth.cos(rad));
    }

    /** Sonar Pulse: charges the ping and tells the wearer's client to outline ores and mobs. Returns true when it fired. */
    public static boolean sonar(ServerPlayer p) {
        State st = state(p);
        int level = st == null ? 0 : lvl(st, ModuleKind.SONAR_PULSE);
        if (level <= 0) return false;
        Item item = ModuleItems.get(ModuleKind.SONAR_PULSE, level).get();
        if (p.getCooldowns().isOnCooldown(item)) return false;
        double cost = ExoConfig.cost(ModuleKind.SONAR_PULSE, level);
        if (!afford(st, ModuleKind.SONAR_PULSE, cost)) {
            refuse(p, st);
            return false;
        }
        spendNow(p, st, ModuleKind.SONAR_PULSE, cost);
        startCooldown(p, CD_SONAR, ExoConfig.sonarCooldown());
        p.level().playSound(null, p.getX(), p.getY(), p.getZ(), SoundEvents.AMETHYST_BLOCK_RESONATE, SoundSource.PLAYERS, 0.9F, 1.4F);
        p.level().playSound(null, p.getX(), p.getY(), p.getZ(), SoundEvents.NOTE_BLOCK_BELL.value(), SoundSource.PLAYERS, 0.5F, 2.0F);
        if (p.connection != null) PacketDistributor.sendToPlayer(p, new ExoSonarPayload(ExoConfig.sonarRadius(level), ExoConfig.sonarDuration()));
        return true;
    }

    /** Overclock (Servo Core set bonus): Haste and Speed for a while, then a long cooldown. Returns true when it fired. */
    public static boolean overclock(ServerPlayer p) {
        State st = state(p);
        if (st == null || st.core != ExoData.Core.SERVO) {
            p.displayClientMessage(net.minecraft.network.chat.Component.translatable("exo.robotica.no_overclock", ExoConfig.setBonusMinMark()), true);
            return false;
        }
        Item core = CoreItems.SERVO_CORE.get();
        if (p.getCooldowns().isOnCooldown(core)) return false;
        int cost = ExoConfig.overclockCost();
        if (available(st, CHEST) < Math.max(cost, 1)) {
            refuse(p, st);
            return false;
        }
        payNow(p, st, CHEST, cost);
        int duration = ExoConfig.overclockDuration();
        if (ExoConfig.overclockHaste() > 0) p.addEffect(new MobEffectInstance(MobEffects.DIG_SPEED, duration, ExoConfig.overclockHaste() - 1, false, false, true));
        if (ExoConfig.overclockSpeed() > 0) p.addEffect(new MobEffectInstance(MobEffects.MOVEMENT_SPEED, duration, ExoConfig.overclockSpeed() - 1, false, false, true));
        startCooldown(p, CD_OVERCLOCK, ExoConfig.overclockCooldown());
        CoreSounds.play(p, CoreSounds.MACHINE_START, SoundSource.PLAYERS, 0.8F, 1.5F);
        p.level().playSound(null, p.getX(), p.getY(), p.getZ(), SoundEvents.BEACON_POWER_SELECT, SoundSource.PLAYERS, 0.5F, 1.8F);
        p.serverLevel().sendParticles(com.arno.robotica.core.particle.CoreParticles.mote(com.arno.robotica.core.particle.CoreParticles.ELECTRIC, 1.0F), p.getX(), p.getY() + 1.0, p.getZ(), 8, 0.4, 0.6, 0.4, 0.04);
        p.serverLevel().sendParticles(com.arno.robotica.core.particle.CoreParticles.zap(com.arno.robotica.core.particle.CoreParticles.ELECTRIC, 1.0F), p.getX(), p.getY() + 1.0, p.getZ(), 4, 0.4, 0.6, 0.4, 0);
        return true;
    }

    // ---------------------------------------------------------------- cooldowns

    /** The items a named cooldown sits on (every level of the module, so the HUD shows it whichever is installed). */
    private static Item[] cooldownItems(String name) {
        ModuleKind kind = switch (name) {
            case CD_DASH -> ModuleKind.DASH_THRUSTERS;
            case CD_MED -> ModuleKind.MED_INJECTOR;
            case CD_SONAR -> ModuleKind.SONAR_PULSE;
            default -> null;
        };
        if (kind == null) return CD_OVERCLOCK.equals(name) ? new Item[]{CoreItems.SERVO_CORE.get()} : new Item[0];
        Item[] items = new Item[kind.maxLevel()];
        for (int l = 1; l <= kind.maxLevel(); l++) items[l - 1] = ModuleItems.get(kind, l).get();
        return items;
    }

    /** Starts a cooldown on its items and remembers its end (game time) in the player's persistent data. */
    private static void startCooldown(ServerPlayer p, String name, int ticks) {
        if (ticks <= 0) return;
        for (Item item : cooldownItems(name)) p.getCooldowns().addCooldown(item, ticks);
        cooldownTag(p).putLong(name, gameTime(p) + ticks);
    }

    /** Puts every remembered cooldown that has not run out yet back on its items (login and respawn). */
    public static void restoreCooldowns(ServerPlayer p) {
        CompoundTag tag = cooldownTag(p);
        long now = gameTime(p);
        for (String name : List.copyOf(tag.getAllKeys())) {
            long left = tag.getLong(name) - now;
            if (left <= 0) {
                tag.remove(name);
                continue;
            }
            int ticks = (int) Math.min(left, Integer.MAX_VALUE);
            for (Item item : cooldownItems(name)) p.getCooldowns().addCooldown(item, ticks);
        }
    }

    private static long gameTime(ServerPlayer p) {
        return p.server.overworld().getGameTime();
    }

    private static CompoundTag cooldownTag(ServerPlayer p) {
        CompoundTag persisted = persistedTag(p);
        if (!persisted.contains(COOLDOWN_KEY, Tag.TAG_COMPOUND)) persisted.put(COOLDOWN_KEY, new CompoundTag());
        return persisted.getCompound(COOLDOWN_KEY);
    }

    /** The player's PlayerPersisted tag (copied to the new player object on every respawn), made when missing. */
    private static CompoundTag persistedTag(ServerPlayer p) {
        CompoundTag root = p.getPersistentData();
        if (!root.contains(Player.PERSISTED_NBT_TAG, Tag.TAG_COMPOUND)) root.put(Player.PERSISTED_NBT_TAG, new CompoundTag());
        return root.getCompound(Player.PERSISTED_NBT_TAG);
    }

    // ---------------------------------------------------------------- event hooks

    /** Rebreather: lets a player with the module and energy breathe under water. Runs on both sides (the client needs it to keep its bubbles). */
    public static void onBreathe(Player player, LivingBreatheEvent event) {
        if (event.canBreathe() || !player.isEyeInFluid(FluidTags.WATER)) return;
        if (!(player instanceof ServerPlayer p)) {
            if (ExoSuit.isActive(player, ModuleKind.REBREATHER)) event.setCanBreathe(true);
            return;
        }
        State st = STATES.get(p.getUUID());
        if (st == null) return;
        double cost = ExoConfig.perTick(ModuleKind.REBREATHER, 1);
        if (!afford(st, ModuleKind.REBREATHER, cost)) return;
        spend(st, ModuleKind.REBREATHER, cost);
        event.setCanBreathe(true);
    }

    /**
     * Incoming hit, before vanilla's checks. Magma Core set and Heat Shield II: fire damage is ignored (i-frames do not
     * matter: Heat Shield II pays per second in the ticker).
     * Fall damage that skips the fall event (an Ender Pearl landing) and flying into a wall: the Antigrav Core ignores
     * the fall, the Fall Dampener cancels both like a fall of that many blocks. Cryo Treads: magma blocks do not burn.
     */
    public static void onIncomingDamage(ServerPlayer p, LivingIncomingDamageEvent event) {
        State st = STATES.get(p.getUUID());
        if (st == null) return;
        var source = event.getSource();
        if ((st.core == ExoData.Core.MAGMA || st.fireRes) && source.is(DamageTypeTags.IS_FIRE)) {
            event.setCanceled(true);
            return;
        }
        boolean fall = source.is(DamageTypeTags.IS_FALL);
        if (fall || source.is(DamageTypes.FLY_INTO_WALL)) {
            refresh(p, st);
            if (fall && st.core == ExoData.Core.ANTIGRAV) event.setCanceled(true);
            else if (dampen(p, st, event.getAmount())) event.setCanceled(true);
            return;
        }
        if (source.is(DamageTypes.HOT_FLOOR)) {
            refresh(p, st);
            if (afford(st, ModuleKind.CRYO_TREADS, 0)) event.setCanceled(true);
        }
    }

    /**
     * Kinetic Shield: spends FE instead of health for up to {@link ExoConfig#shieldAbsorb} of the damage that would
     * reach the health bar. It runs in {@link LivingDamageEvent.Pre}, after vanilla's invulnerability frames and armor:
     * a hit the i-frames block never gets here, so it costs nothing, and every hit that does get here starts i-frames
     * as usual (lava, cactus or a crowd bill once per i-frame window, not every tick). A completely absorbed hit
     * deals no damage and gives no knockback.
     */
    public static void onDamage(ServerPlayer p, LivingDamageEvent.Pre event) {
        State st = STATES.get(p.getUUID());
        if (st == null) return;
        refresh(p, st);
        int level = lvl(st, ModuleKind.KINETIC_SHIELD);
        if (level <= 0) return;
        if (event.getSource().is(DamageTypeTags.BYPASSES_INVULNERABILITY)) return;
        float damage = event.getNewDamage();
        if (damage <= 0) return;
        int piece = st.act.piece(ModuleKind.KINETIC_SHIELD);
        double perPoint = ExoConfig.cost(ModuleKind.KINETIC_SHIELD, level) * st.factor;
        double have = available(st, piece);
        float cap = (float) (damage * ExoConfig.shieldAbsorb(level));
        float absorbed = perPoint <= 0 ? cap : (float) Math.min(cap, have / perPoint);
        if (absorbed <= 0) return;
        payNow(p, st, piece, absorbed * perPoint);
        // i-frames keep this to about two a second, whatever hits the player
        if (absorbed >= damage) {
            event.setNewDamage(0);
            st.shieldTick = p.tickCount;
            CoreSounds.play(p, CoreSounds.EXO_SHIELD_BLOCK, SoundSource.PLAYERS, 1.0F, 1.0F);
            fx(p, ExoFxPayload.SHIELD_FULL);
        } else {
            event.setNewDamage(damage - absorbed);
            CoreSounds.play(p, CoreSounds.EXO_SHIELD_SPARK, SoundSource.PLAYERS, 1.0F, 1.0F);
            fx(p, ExoFxPayload.SHIELD_PARTIAL);
        }
    }

    /** A hit the Kinetic Shield absorbed completely pushes nobody around. Anchor Servos take a share off the rest. */
    public static void onKnockback(ServerPlayer p, LivingKnockBackEvent event) {
        State st = STATES.get(p.getUUID());
        if (st == null) return;
        if (st.shieldTick == p.tickCount) {
            event.setCanceled(true);
            return;
        }
        refresh(p, st);
        int level = lvl(st, ModuleKind.ANCHOR_SERVOS);
        if (level <= 0 || event.getStrength() <= 0) return;
        double cost = ExoConfig.cost(ModuleKind.ANCHOR_SERVOS, level);
        if (!afford(st, ModuleKind.ANCHOR_SERVOS, cost)) return;
        spendNow(p, st, ModuleKind.ANCHOR_SERVOS, cost);
        double resist = ExoConfig.anchorResist(level);
        if (resist >= 1.0) event.setCanceled(true);
        else event.setStrength((float) (event.getStrength() * (1.0 - resist)));
    }

    /** Magma Core set: the wearer's melee hits set the target on fire. */
    public static void onAttack(ServerPlayer attacker, LivingEntity target, LivingIncomingDamageEvent event) {
        if (!event.getSource().is(DamageTypes.PLAYER_ATTACK) || event.getSource().getDirectEntity() != attacker) return;
        State st = STATES.get(attacker.getUUID());
        if (st == null || st.core != ExoData.Core.MAGMA || ExoConfig.magmaBurnSeconds() <= 0) return;
        target.igniteForSeconds(ExoConfig.magmaBurnSeconds());
    }

    /**
     * Antigrav Core set: no fall damage at all. Fall Dampener: cancels all fall damage while its battery holds any
     * energy, for FE per block fallen beyond the safe fall distance (an emptying battery pays what it has left).
     */
    public static void onFall(ServerPlayer p, LivingFallEvent event) {
        State st = STATES.get(p.getUUID());
        if (st == null) return;
        refresh(p, st);
        if (st.core == ExoData.Core.ANTIGRAV) {
            event.setDamageMultiplier(0.0F);
            return;
        }
        double blocks = event.getDistance() - p.getAttributeValue(Attributes.SAFE_FALL_DISTANCE);
        if (blocks <= 0 || event.getDamageMultiplier() <= 0) return;
        if (dampen(p, st, blocks)) event.setDamageMultiplier(0.0F);
    }

    /** Client side of a fall: the same answer as the server, so a dampened landing plays no fall damage sound. */
    public static void onClientFall(Player player, LivingFallEvent event) {
        if (ExoSuit.setBonus(player) == ExoData.Core.ANTIGRAV || ExoSuit.pieceLevel(player, EquipmentSlot.FEET, ModuleKind.FALL_DAMPENER) > 0) {
            event.setDamageMultiplier(0.0F);
        }
    }

    /**
     * Fall Dampener: pays for {@code blocks} blocks of fall (or what is left in its battery) and returns true when the
     * damage is cancelled, false without a working, charged module.
     */
    private static boolean dampen(ServerPlayer p, State st, double blocks) {
        int level = lvl(st, ModuleKind.FALL_DAMPENER);
        if (level <= 0) return false;
        int piece = st.act.piece(ModuleKind.FALL_DAMPENER);
        double have = available(st, piece);
        if (have < 1) return false;
        double cost = blocks * ExoConfig.cost(ModuleKind.FALL_DAMPENER, level) * st.factor;
        payNow(p, st, piece, Math.min(cost, have));
        CoreSounds.play(p, CoreSounds.MACHINE_STOP, SoundSource.PLAYERS, 0.5F, 1.5F);
        p.serverLevel().sendParticles(ParticleTypes.POOF, p.getX(), p.getY() + 0.1, p.getZ(), 8, 0.3, 0.05, 0.3, 0.02);
        return true;
    }

    /** Spring Heels: the jump height itself is an attribute; this charges the jump. */
    public static void onJump(ServerPlayer p) {
        State st = STATES.get(p.getUUID());
        if (st == null) return;
        refresh(p, st);
        int level = lvl(st, ModuleKind.SPRING_HEELS);
        if (level <= 0) return;
        double cost = ExoConfig.cost(ModuleKind.SPRING_HEELS, level);
        if (!afford(st, ModuleKind.SPRING_HEELS, cost)) return;
        spendNow(p, st, ModuleKind.SPRING_HEELS, cost);
        if (p.tickCount - st.lastSpring >= 6 || p.tickCount < st.lastSpring) {
            st.lastSpring = p.tickCount;
            CoreSounds.play(p, CoreSounds.EXO_SPRING, SoundSource.PLAYERS, 1.0F, 1.0F);
            fx(p, ExoFxPayload.SPRING);
        }
    }

    // ---------------------------------------------------------------- lifecycle

    /**
     * Login: cooldowns come back, and a flight grant saved with the player is checked again. With a working Flight
     * module and energy the player keeps flying (no fall on rejoin); otherwise (suit gone, empty, creative, or a stale
     * grant from before a crash) flight is taken back.
     */
    public static void onLogin(ServerPlayer p) {
        if (p instanceof FakePlayer) return;
        restoreCooldowns(p);
        if (!p.getPersistentData().getBoolean(FLIGHT_KEY)) return;
        State st = STATES.get(p.getUUID());
        if (st == null) st = newState(p);
        st.flightGranted = true;
        refresh(p, st);
        if (flightValid(p, st)) {
            Abilities ab = p.getAbilities();
            if (!ab.mayfly) {
                ab.mayfly = true;
                p.onUpdateAbilities();
            }
        } else {
            revokeFlight(p, st, true);
        }
    }

    /** Any game mode change: vanilla resets the abilities itself, so forget what we granted (the next tick re-grants). */
    public static void onGameModeChange(ServerPlayer p) {
        State st = STATES.get(p.getUUID());
        if (st != null) st.flightGranted = false;
        p.getPersistentData().remove(FLIGHT_KEY);
    }

    /** Logout: undo the effects and forget the player, but keep a flight grant (checked again on login) and the jet heat. */
    public static void onLogout(ServerPlayer p) {
        State st = STATES.remove(p.getUUID());
        if (st == null) return;
        release(p, st, false, false);
        saveJet(p, st);
    }

    /** Death: undo everything, flight included; the jet heat stays with the player. */
    public static void onDeath(ServerPlayer p) {
        State st = STATES.remove(p.getUUID());
        if (st != null) {
            release(p, st, false, true);
            saveJet(p, st);
        }
        p.getPersistentData().remove(FLIGHT_KEY);
    }

    /**
     * Dimension change: the client starts a new player object with an idle Jet Assist, so the server's pack stops too
     * (otherwise a thrust held through a portal keeps burning FE and heat on the server only). The heat stays, and the
     * client is told.
     */
    public static void onChangedDimension(ServerPlayer p) {
        State st = STATES.get(p.getUUID());
        if (st == null) return;
        st.jetWant = ExoJet.OFF;
        st.jetMode = ExoJet.OFF;
        st.thrustedAir = false;
        sendHeat(p, st);
    }

    /**
     * Respawn: new player object with fresh abilities and cooldowns; the remembered cooldowns come back, and the jet
     * heat (left by a trip out of the End) is kept for the next state.
     */
    public static void onRespawn(ServerPlayer p) {
        State st = STATES.remove(p.getUUID());
        if (st != null) saveJet(p, st);
        p.getPersistentData().remove(FLIGHT_KEY);
        restoreCooldowns(p);
    }

    /** Removes every effect of the suit from the player (the suit came off, or the player leaves). */
    private static void release(ServerPlayer p, State st, boolean soft, boolean revoke) {
        setModifier(p, Attributes.MOVEMENT_SPEED, SERVO_ID, 0, AttributeModifier.Operation.ADD_MULTIPLIED_TOTAL);
        setModifier(p, Attributes.STEP_HEIGHT, STEP_ID, 0, AttributeModifier.Operation.ADD_VALUE);
        setModifier(p, Attributes.JUMP_STRENGTH, JUMP_ID, 0, AttributeModifier.Operation.ADD_VALUE);
        setModifier(p, NeoForgeMod.SWIM_SPEED, SWIM_ID, 0, AttributeModifier.Operation.ADD_MULTIPLIED_BASE);
        setModifier(p, Attributes.SUBMERGED_MINING_SPEED, DIG_ID, 0, AttributeModifier.Operation.ADD_VALUE);
        setModifier(p, Attributes.BLOCK_INTERACTION_RANGE, REACH_ID, 0, AttributeModifier.Operation.ADD_VALUE);
        if (st.nv) removeOurs(p, MobEffects.NIGHT_VISION);
        if (st.fireRes) removeOurs(p, MobEffects.FIRE_RESISTANCE);
        if (st.jetMode != ExoJet.OFF) {
            st.jetMode = ExoJet.OFF;
            jetFx(p, ExoFxPayload.JET_OFF, true, false);
        }
        if (revoke && st.flightGranted) revokeFlight(p, st, soft);
        if (revoke && st.exoFlying) {
            st.exoFlying = false;
            fx(p, ExoFxPayload.FLIGHT_OFF);
        }
        flush(p, st);
    }
}
