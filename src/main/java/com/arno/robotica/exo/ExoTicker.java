package com.arno.robotica.exo;

import com.arno.robotica.Robotica;
import com.arno.robotica.core.CoreSounds;
import com.arno.robotica.core.module.ModuleItems;
import com.arno.robotica.core.module.ModuleKind;
import com.arno.robotica.core.module.Modules;
import com.arno.robotica.core.energy.ItemEnergy;
import com.arno.robotica.core.item.CellItem;
import com.arno.robotica.core.item.CoreItems;
import com.arno.robotica.exo.net.ExoSonarPayload;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Holder;
import net.minecraft.core.component.DataComponents;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.Tag;
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
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.GameType;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.common.NeoForgeMod;
import net.neoforged.neoforge.common.util.FakePlayer;
import net.neoforged.neoforge.event.entity.living.LivingBreatheEvent;
import net.neoforged.neoforge.event.entity.living.LivingDamageEvent;
import net.neoforged.neoforge.event.entity.living.LivingFallEvent;
import net.neoforged.neoforge.event.entity.living.LivingIncomingDamageEvent;
import net.neoforged.neoforge.event.entity.living.LivingKnockBackEvent;
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
    private static final ResourceLocation STEP_ID = Robotica.id("exo_step_assist");
    private static final ResourceLocation JUMP_ID = Robotica.id("exo_spring_heels");
    private static final ResourceLocation SWIM_ID = Robotica.id("exo_hydro_fins");
    private static final ResourceLocation DIG_ID = Robotica.id("exo_hydro_fins_mining");
    private static final int FLUSH_INTERVAL = 10;
    private static final int CHEST = 1, FEET = 3;
    /** Persistent data: true while the suit (not a game mode or another mod) granted the player {@code mayfly}. */
    public static final String FLIGHT_KEY = "robotica_exo_flight";
    /** Persistent data (inside PlayerPersisted, kept through death): cooldown end times in game ticks. */
    public static final String COOLDOWN_KEY = "robotica_exo_cooldowns";
    public static final String CD_DASH = "dash", CD_MED = "med_injector", CD_SONAR = "sonar", CD_OVERCLOCK = "overclock";
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
        double lastX = Double.NaN, lastY, lastZ;
        boolean moving;
        double dy;
        double walked;
        boolean flightGranted;
        boolean nv;
        boolean fireRes;
        boolean gliding;
        int airJumps;
        int lastAirJumpTick = -100;
        /** Tick of the last hit the Kinetic Shield absorbed completely (its knockback is cancelled). */
        int shieldTick = -100;
        int lastRefusal = -100;
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

    // ---------------------------------------------------------------- tick

    public static void tick(ServerPlayer p) {
        if (p instanceof FakePlayer) return;
        UUID id = p.getUUID();
        State st = STATES.get(id);
        if (!ExoSuit.wearingAny(p)) {
            if (st != null) {
                release(p, st, false, true);
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
        st.dy = first ? 0 : p.getY() - st.lastY;
        st.lastX = p.getX();
        st.lastY = p.getY();
        st.lastZ = p.getZ();

        helmet(p, st);
        jetAssist(p, st);
        flight(p, st);
        attributes(p, st);
        kineticGenerator(p, st);
        setBonus(p, st);
        medInjector(p, st);
        if ((st.tick & 1) == 0) magnet(p, st);
        if (st.tick % 10 == 0) hazardSeal(p, st);
        if (st.tick % 20 == 0) {
            autoFeeder(p, st);
            solarWeave(p, st);
            if (ExoConfig.cellRecharge()) rechargeFromCells(p);
        }
        if (st.tick % FLUSH_INTERVAL == 0) flush(p, st);
    }

    /** A fresh state; the flight grant comes from the persistent data so it survives a restart. */
    private static State newState(ServerPlayer p) {
        State st = new State();
        st.flightGranted = p.getPersistentData().getBoolean(FLIGHT_KEY);
        STATES.put(p.getUUID(), st);
        return st;
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
        Abilities ab = p.getAbilities();
        boolean grounded = p.onGround() || p.isInWater() || p.isInLava() || ab.flying || p.isFallFlying() || p.isPassenger();
        if (grounded) st.airJumps = 0;
        int level = lvl(st, ModuleKind.JET_ASSIST);
        double cost = level > 0 ? ExoConfig.perTick(ModuleKind.JET_ASSIST, level) : 0;
        boolean glide = level > 0 && !grounded && afford(st, ModuleKind.JET_ASSIST, cost) && (st.gliding ? st.dy < -0.01 : st.dy < -0.5);
        if (glide) {
            spend(st, ModuleKind.JET_ASSIST, cost);
            MobEffectInstance cur = p.getEffect(MobEffects.SLOW_FALLING);
            if (cur == null || (cur.isAmbient() && cur.getDuration() < 6)) {
                p.addEffect(new MobEffectInstance(MobEffects.SLOW_FALLING, 12, 0, true, false, false));
            }
            p.resetFallDistance();
            st.gliding = true;
            if (st.tick % 4 == 0) p.serverLevel().sendParticles(ParticleTypes.CLOUD, p.getX(), p.getY() - 0.1, p.getZ(), 2, 0.15, 0.02, 0.15, 0.01);
        } else if (st.gliding) {
            st.gliding = false;
            removeOurs(p, MobEffects.SLOW_FALLING);
        }
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
            if (ab.flying) {
                spend(st, ModuleKind.FLIGHT, cost);
                if ((st.tick & 1) == 0) {
                    ServerLevel level = p.serverLevel();
                    level.sendParticles(ParticleTypes.SMALL_FLAME, p.getX(), p.getY() - 0.05, p.getZ(), 2, 0.12, 0.02, 0.12, 0.005);
                    level.sendParticles(ParticleTypes.SMOKE, p.getX(), p.getY() - 0.2, p.getZ(), 1, 0.1, 0.05, 0.1, 0.01);
                }
                // The wearer hears a client loop; everyone else a soft burn now and then.
                if (st.tick % 20 == 0) {
                    p.level().playSound(p, p.getX(), p.getY(), p.getZ(), SoundEvents.BLAZE_BURN, SoundSource.PLAYERS, 0.25F, 1.6F);
                }
            }
        } else if (st.flightGranted) {
            ItemStack chest = ExoSuit.piece(p, EquipmentSlot.CHEST);
            boolean moduleStillWorn = !chest.isEmpty() && Modules.installed(chest, ModuleKind.FLIGHT) > 0;
            revokeFlight(p, st, moduleStillWorn);
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
        GameType type = p.gameMode.getGameModeForPlayer();
        return type == GameType.CREATIVE || type == GameType.SPECTATOR;
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
        p.level().playSound(null, p.getX(), p.getY(), p.getZ(), SoundEvents.FIRE_EXTINGUISH, SoundSource.PLAYERS, 0.5F, 1.8F);
        p.serverLevel().sendParticles(ParticleTypes.HEART, p.getX(), p.getY() + 1.2, p.getZ(), 4, 0.4, 0.4, 0.4, 0.0);
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
        boolean stepOn = afford(st, ModuleKind.STEP_ASSIST, stepCost);
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

    // ---------------------------------------------------------------- set bonus

    private static void setBonus(ServerPlayer p, State st) {
        if (st.core == ExoData.Core.MAGMA) {
            refreshEffect(p, MobEffects.FIRE_RESISTANCE, 0, 300, 240);
            st.fireRes = true;
            if (p.isOnFire() && st.tick % 10 == 0) p.clearFire();
        } else if (st.fireRes) {
            st.fireRes = false;
            removeOurs(p, MobEffects.FIRE_RESISTANCE);
        }
    }

    /** Cells and Mainsprings in the inventory top up the worn suit once per second (config cellsRechargeSuit). */
    private static void rechargeFromCells(ServerPlayer p) {
        ItemStack[] pieces = {ExoSuit.piece(p, EquipmentSlot.CHEST), ExoSuit.piece(p, EquipmentSlot.LEGS),
                ExoSuit.piece(p, EquipmentSlot.HEAD), ExoSuit.piece(p, EquipmentSlot.FEET)};
        boolean needs = false;
        for (ItemStack piece : pieces) {
            if (!piece.isEmpty() && ItemEnergy.get(piece) < ItemEnergy.capacity(piece)) needs = true;
        }
        if (!needs) return;
        var inv = p.getInventory();
        int budget = ExoConfig.cellRechargePerSecond();
        for (int i = 0; i < inv.items.size() + 1 && budget > 0; i++) {
            ItemStack cell = i < inv.items.size() ? inv.items.get(i) : inv.offhand.get(0);
            if (!(cell.getItem() instanceof CellItem item) || ItemEnergy.get(cell) <= 0) continue;
            int rate = (int) Math.min((long) item.getMaxExtract(cell) * 20, budget);
            int start = rate;
            for (ItemStack piece : pieces) {
                if (rate <= 0 || piece.isEmpty()) continue;
                int space = ItemEnergy.capacity(piece) - ItemEnergy.get(piece);
                int move = Math.min(Math.min(rate, space), ItemEnergy.get(cell));
                if (move <= 0) continue;
                ItemEnergy.drain(cell, move);
                ItemEnergy.addInternal(piece, move);
                rate -= move;
            }
            budget -= start - rate;
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
     * Jet Assist double jump, requested by the client in mid air. All checks are repeated here with the same rules the
     * client predicts with ({@link ExoSuit#canPayAirJump}); a refusal is silent. Returns true when it fired.
     */
    public static boolean doubleJump(ServerPlayer p) {
        State st = state(p);
        int level = st == null ? 0 : lvl(st, ModuleKind.JET_ASSIST);
        if (level <= 0) return false;
        Abilities ab = p.getAbilities();
        if (p.onGround() || p.isInWater() || p.isInLava() || ab.flying || p.isFallFlying() || p.isPassenger() || p.isSpectator()) return false;
        // Half the client's gap: packets can arrive bunched up.
        if (st.airJumps >= ExoConfig.airJumps(level) || p.tickCount - st.lastAirJumpTick < ExoSuit.AIR_JUMP_GAP / 2) return false;
        if (!ExoSuit.canPayAirJump(p, st.act)) return false;
        spendNow(p, st, ModuleKind.JET_ASSIST, ExoConfig.doubleJumpCost(level));
        st.airJumps++;
        st.lastAirJumpTick = p.tickCount;
        Vec3 v = p.getDeltaMovement();
        p.setDeltaMovement(v.x, 0.55, v.z);
        p.hurtMarked = true;
        p.resetFallDistance();
        p.level().playSound(null, p.getX(), p.getY(), p.getZ(), SoundEvents.FIREWORK_ROCKET_LAUNCH, SoundSource.PLAYERS, 0.35F, 1.6F);
        p.serverLevel().sendParticles(ParticleTypes.CLOUD, p.getX(), p.getY() + 0.1, p.getZ(), 10, 0.2, 0.05, 0.2, 0.05);
        p.serverLevel().sendParticles(ParticleTypes.SMALL_FLAME, p.getX(), p.getY(), p.getZ(), 6, 0.15, 0.05, 0.15, 0.02);
        return true;
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
        p.level().playSound(null, p.getX(), p.getY(), p.getZ(), SoundEvents.BREEZE_WIND_CHARGE_BURST.value(), SoundSource.PLAYERS, 0.6F, 1.4F);
        p.serverLevel().sendParticles(ParticleTypes.POOF, p.getX(), p.getY() + 0.3, p.getZ(), 10, 0.25, 0.1, 0.25, 0.04);
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
            p.displayClientMessage(net.minecraft.network.chat.Component.translatable("exo.robotica.no_overclock"), true);
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
        p.serverLevel().sendParticles(ParticleTypes.ELECTRIC_SPARK, p.getX(), p.getY() + 1.0, p.getZ(), 20, 0.4, 0.6, 0.4, 0.2);
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
        CompoundTag root = p.getPersistentData();
        if (!root.contains(Player.PERSISTED_NBT_TAG, Tag.TAG_COMPOUND)) root.put(Player.PERSISTED_NBT_TAG, new CompoundTag());
        CompoundTag persisted = root.getCompound(Player.PERSISTED_NBT_TAG);
        if (!persisted.contains(COOLDOWN_KEY, Tag.TAG_COMPOUND)) persisted.put(COOLDOWN_KEY, new CompoundTag());
        return persisted.getCompound(COOLDOWN_KEY);
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

    /** Incoming hit, before vanilla's checks. Magma Core set: fire damage is ignored (free, so i-frames do not matter). */
    public static void onIncomingDamage(ServerPlayer p, LivingIncomingDamageEvent event) {
        State st = STATES.get(p.getUUID());
        if (st == null) return;
        if (st.core == ExoData.Core.MAGMA && event.getSource().is(DamageTypeTags.IS_FIRE)) event.setCanceled(true);
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
        if (absorbed >= damage) {
            event.setNewDamage(0);
            st.shieldTick = p.tickCount;
        } else {
            event.setNewDamage(damage - absorbed);
        }
        CoreSounds.play(p, CoreSounds.SHOCK_ZAP, SoundSource.PLAYERS, 0.5F, 1.4F);
        p.serverLevel().sendParticles(ParticleTypes.ELECTRIC_SPARK, p.getX(), p.getY() + 1.0, p.getZ(), 12, 0.4, 0.6, 0.4, 0.1);
    }

    /** A hit the Kinetic Shield absorbed completely pushes nobody around. */
    public static void onKnockback(ServerPlayer p, LivingKnockBackEvent event) {
        State st = STATES.get(p.getUUID());
        if (st != null && st.shieldTick == p.tickCount) event.setCanceled(true);
    }

    /** Magma Core set: the wearer's melee hits set the target on fire. */
    public static void onAttack(ServerPlayer attacker, LivingEntity target, LivingIncomingDamageEvent event) {
        if (!event.getSource().is(DamageTypes.PLAYER_ATTACK) || event.getSource().getDirectEntity() != attacker) return;
        State st = STATES.get(attacker.getUUID());
        if (st == null || st.core != ExoData.Core.MAGMA || ExoConfig.magmaBurnSeconds() <= 0) return;
        target.igniteForSeconds(ExoConfig.magmaBurnSeconds());
    }

    /**
     * Antigrav Core set: no fall damage at all. Fall Dampener: absorbs fall damage for FE per block fallen (beyond the 3
     * safe blocks). Partial with little energy.
     */
    public static void onFall(ServerPlayer p, LivingFallEvent event) {
        State st = STATES.get(p.getUUID());
        if (st == null) return;
        refresh(p, st);
        if (st.core == ExoData.Core.ANTIGRAV) {
            event.setDamageMultiplier(0.0F);
            return;
        }
        int level = lvl(st, ModuleKind.FALL_DAMPENER);
        if (level <= 0) return;
        float blocks = event.getDistance() - 3.0F;
        if (blocks <= 0 || event.getDamageMultiplier() <= 0) return;
        int piece = st.act.piece(ModuleKind.FALL_DAMPENER);
        double cost = (double) blocks * ExoConfig.cost(ModuleKind.FALL_DAMPENER, level) * st.factor;
        double have = available(st, piece);
        double fraction = cost <= 0 ? 1.0 : Mth.clamp(have / cost, 0.0, 1.0);
        if (fraction <= 0) return;
        payNow(p, st, piece, cost * fraction);
        event.setDamageMultiplier((float) (event.getDamageMultiplier() * (1.0 - fraction)));
        CoreSounds.play(p, CoreSounds.MACHINE_STOP, SoundSource.PLAYERS, 0.5F, 1.5F);
        p.serverLevel().sendParticles(ParticleTypes.POOF, p.getX(), p.getY() + 0.1, p.getZ(), 8, 0.3, 0.05, 0.3, 0.02);
    }

    /** Spring Heels: the jump height itself is an attribute; this charges the jump. */
    public static void onJump(ServerPlayer p) {
        State st = STATES.get(p.getUUID());
        if (st == null) return;
        refresh(p, st);
        int level = lvl(st, ModuleKind.SPRING_HEELS);
        if (level <= 0) return;
        double cost = ExoConfig.cost(ModuleKind.SPRING_HEELS, level);
        if (afford(st, ModuleKind.SPRING_HEELS, cost)) spendNow(p, st, ModuleKind.SPRING_HEELS, cost);
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

    /** Logout: undo the effects and forget the player, but keep a flight grant (checked again on login). */
    public static void onLogout(ServerPlayer p) {
        State st = STATES.remove(p.getUUID());
        if (st != null) release(p, st, false, false);
    }

    /** Death: undo everything, flight included. */
    public static void onDeath(ServerPlayer p) {
        State st = STATES.remove(p.getUUID());
        if (st != null) release(p, st, false, true);
        p.getPersistentData().remove(FLIGHT_KEY);
    }

    /** Respawn: new player object with fresh abilities and cooldowns; the remembered cooldowns come back. */
    public static void onRespawn(ServerPlayer p) {
        STATES.remove(p.getUUID());
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
        if (st.nv) removeOurs(p, MobEffects.NIGHT_VISION);
        if (st.fireRes) removeOurs(p, MobEffects.FIRE_RESISTANCE);
        if (st.gliding) removeOurs(p, MobEffects.SLOW_FALLING);
        if (revoke && st.flightGranted) revokeFlight(p, st, soft);
        flush(p, st);
    }
}
