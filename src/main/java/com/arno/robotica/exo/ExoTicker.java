package com.arno.robotica.exo;

import com.arno.robotica.Robotica;
import com.arno.robotica.core.CoreSounds;
import com.arno.robotica.core.energy.ItemEnergy;
import com.arno.robotica.core.item.CellItem;
import com.arno.robotica.core.item.CoreItems;
import com.arno.robotica.exo.net.ExoSonarPayload;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Holder;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
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
import net.neoforged.neoforge.event.entity.living.LivingFallEvent;
import net.neoforged.neoforge.event.entity.living.LivingIncomingDamageEvent;
import net.neoforged.neoforge.network.PacketDistributor;

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
 * spectator). Energy costs (and what Solar Weave and the Kinetic Generator make) are collected per piece and written
 * to the items once every 10 ticks, so the armor stack does not change (and is not re-sent to every nearby player)
 * on every tick. Cooldowns (Dash, Sonar, Med Injector, Overclock) are vanilla item cooldowns on the module item (the
 * Servo Core for Overclock), so the client sees them without extra packets.
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
                release(p, st, false);
                STATES.remove(id);
            }
            return;
        }
        if (st == null) {
            st = new State();
            STATES.put(id, st);
        }
        st.tick++;
        refresh(p, st);

        double dx = p.getX() - st.lastX;
        double dz = p.getZ() - st.lastZ;
        boolean first = Double.isNaN(st.lastX);
        double dist = first ? 0 : Math.sqrt(dx * dx + dz * dz);
        st.moving = dist > 0.02;
        st.walked = Math.min(dist, 1.0);
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

    /** Writes the collected costs and production to the armor items. Called every 10 ticks; public for tests. */
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
        st.full = true;
        for (int i = 0; i < 4; i++) {
            ItemStack piece = ExoSuit.piece(p, ExoSuit.SLOTS[i]);
            if (piece.isEmpty()) st.full = false;
            st.energy[i] = piece.isEmpty() ? 0 : ItemEnergy.get(piece);
        }
        st.act = ExoSuit.active(p);
        st.core = ExoSuit.setBonus(p);
        st.factor = 1.0 - ExoConfig.regulatorSaving(st.act.level(ExoModuleKind.POWER_REGULATOR));
    }

    private static boolean has(State st, ExoModuleKind kind) {
        return st.act.has(kind);
    }

    private static int lvl(State st, ExoModuleKind kind) {
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
    private static boolean afford(State st, ExoModuleKind kind, double fe) {
        return has(st, kind) && available(st, st.act.piece(kind)) >= Math.max(fe * st.factor, 1);
    }

    /** Books {@code fe} (before the Power Regulator) against the module's piece. */
    private static void spend(State st, ExoModuleKind kind, double fe) {
        st.pending[st.act.piece(kind)] += fe * st.factor;
    }

    // ---------------------------------------------------------------- helmet

    private static void helmet(ServerPlayer p, State st) {
        int nvLevel = lvl(st, ExoModuleKind.NIGHT_VISION);
        double cost = nvLevel > 0 ? ExoConfig.perTick(ExoModuleKind.NIGHT_VISION, nvLevel) : 0;
        if (nvLevel > 0 && afford(st, ExoModuleKind.NIGHT_VISION, cost)) {
            spend(st, ExoModuleKind.NIGHT_VISION, cost);
            refreshEffect(p, MobEffects.NIGHT_VISION, 0, 300, 240);
            st.nv = true;
        } else if (st.nv) {
            st.nv = false;
            removeOurs(p, MobEffects.NIGHT_VISION);
        }
        // Robot HUD and thermal sight are client displays; they only draw power.
        double hud = ExoConfig.perTick(ExoModuleKind.ROBOT_HUD, 1);
        if (afford(st, ExoModuleKind.ROBOT_HUD, hud)) spend(st, ExoModuleKind.ROBOT_HUD, hud);
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

    /** Auto-Feeder: once a second, eats the best fitting plain food from the inventory when hungry. */
    private static void autoFeeder(ServerPlayer p, State st) {
        if (!has(st, ExoModuleKind.AUTO_FEEDER) || creativeLike(p)) return;
        var food = p.getFoodData();
        if (food.getFoodLevel() > ExoConfig.autoFeederHunger()) return;
        double cost = ExoConfig.cost(ExoModuleKind.AUTO_FEEDER, 1);
        if (!afford(st, ExoModuleKind.AUTO_FEEDER, cost)) return;
        int missing = 20 - food.getFoodLevel();
        var inv = p.getInventory();
        ItemStack best = ItemStack.EMPTY;
        FoodProperties bestFood = null;
        for (int i = 0; i < inv.items.size() + 1; i++) {
            ItemStack stack = i < inv.items.size() ? inv.items.get(i) : inv.offhand.get(0);
            if (stack.isEmpty() || stack.is(Items.CHORUS_FRUIT)) continue;
            FoodProperties props = stack.getFoodProperties(p);
            if (props == null || !props.effects().isEmpty() || props.nutrition() <= 0) continue;
            if (bestFood == null || better(props.nutrition(), bestFood.nutrition(), missing)) {
                best = stack;
                bestFood = props;
            }
        }
        if (bestFood == null) return;
        p.eat(p.level(), best, bestFood);
        spend(st, ExoModuleKind.AUTO_FEEDER, cost);
    }

    /** Prefers the biggest food that does not overfill, else the smallest one. */
    private static boolean better(int candidate, int current, int missing) {
        boolean fitsC = candidate <= missing, fitsB = current <= missing;
        if (fitsC != fitsB) return fitsC;
        return fitsC ? candidate > current : candidate < current;
    }

    /** Solar Weave: once a second in daylight under open sky, charges the suit. */
    private static void solarWeave(ServerPlayer p, State st) {
        if (!has(st, ExoModuleKind.SOLAR_WEAVE)) return;
        ServerLevel level = p.serverLevel();
        BlockPos eyes = BlockPos.containing(p.getEyePosition());
        if (!level.isDay() || !level.canSeeSky(eyes) || level.isRainingAt(eyes)) return;
        st.made[st.act.piece(ExoModuleKind.SOLAR_WEAVE)] += ExoConfig.cost(ExoModuleKind.SOLAR_WEAVE, 1);
    }

    // ---------------------------------------------------------------- chestplate

    private static void jetAssist(ServerPlayer p, State st) {
        Abilities ab = p.getAbilities();
        boolean grounded = p.onGround() || p.isInWater() || p.isInLava() || ab.flying || p.isFallFlying() || p.isPassenger();
        if (grounded) st.airJumps = 0;
        int level = lvl(st, ExoModuleKind.JET_ASSIST);
        double cost = level > 0 ? ExoConfig.perTick(ExoModuleKind.JET_ASSIST, level) : 0;
        boolean glide = level > 0 && !grounded && afford(st, ExoModuleKind.JET_ASSIST, cost) && (st.gliding ? st.dy < -0.01 : st.dy < -0.5);
        if (glide) {
            spend(st, ExoModuleKind.JET_ASSIST, cost);
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
        double cost = ExoConfig.perTick(ExoModuleKind.FLIGHT, 1);
        return st.core == ExoData.Core.ANTIGRAV ? cost * ExoConfig.antigravFlightFactor() : cost;
    }

    private static void flight(ServerPlayer p, State st) {
        boolean creativeLike = creativeLike(p);
        double cost = flightCost(st);
        boolean want = !creativeLike && has(st, ExoModuleKind.FLIGHT) && available(st, st.act.piece(ExoModuleKind.FLIGHT)) >= Math.max(cost * st.factor, 1);
        Abilities ab = p.getAbilities();
        if (want) {
            if (!ab.mayfly) {
                ab.mayfly = true;
                st.flightGranted = true;
                p.onUpdateAbilities();
            }
            if (ab.flying) {
                spend(st, ExoModuleKind.FLIGHT, cost);
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
            boolean moduleStillWorn = !chest.isEmpty() && ExoData.levelIn(chest, ExoModuleKind.FLIGHT) > 0;
            revokeFlight(p, st, moduleStillWorn);
        }
    }

    /** Creative and spectator players fly by themselves; the suit never grants or revokes anything for them. */
    private static boolean creativeLike(ServerPlayer p) {
        GameType type = p.gameMode.getGameModeForPlayer();
        return type == GameType.CREATIVE || type == GameType.SPECTATOR;
    }

    /** Takes back the flight we granted. Never touches creative or spectator abilities. {@code soft}: give a short slow fall. */
    private static void revokeFlight(ServerPlayer p, State st, boolean soft) {
        st.flightGranted = false;
        if (creativeLike(p)) return;
        Abilities ab = p.getAbilities();
        if (!ab.mayfly && !ab.flying) return;
        ab.mayfly = false;
        ab.flying = false;
        p.onUpdateAbilities();
        if (soft && !p.onGround()) {
            p.addEffect(new MobEffectInstance(MobEffects.SLOW_FALLING, 100, 0, true, false, true));
            CoreSounds.play(p, CoreSounds.ROBOT_ERROR, SoundSource.PLAYERS, 0.6F, 0.8F);
        }
        p.resetFallDistance();
    }

    /** Med Injector: heals once when health drops low, then waits out its cooldown. */
    private static void medInjector(ServerPlayer p, State st) {
        int level = lvl(st, ExoModuleKind.MED_INJECTOR);
        if (level <= 0 || !p.isAlive() || p.getHealth() > p.getMaxHealth() * ExoConfig.medThreshold()) return;
        Item item = ExoItems.module(ExoModuleKind.MED_INJECTOR, level).get();
        if (p.getCooldowns().isOnCooldown(item)) return;
        double cost = ExoConfig.cost(ExoModuleKind.MED_INJECTOR, level);
        if (!afford(st, ExoModuleKind.MED_INJECTOR, cost)) return;
        spend(st, ExoModuleKind.MED_INJECTOR, cost);
        p.heal(ExoConfig.medHeal(level));
        cooldownAll(p, ExoModuleKind.MED_INJECTOR, ExoConfig.medCooldown(level));
        p.level().playSound(null, p.getX(), p.getY(), p.getZ(), SoundEvents.FIRE_EXTINGUISH, SoundSource.PLAYERS, 0.5F, 1.8F);
        p.serverLevel().sendParticles(ParticleTypes.HEART, p.getX(), p.getY() + 1.2, p.getZ(), 4, 0.4, 0.4, 0.4, 0.0);
    }

    /** Puts every level of a module kind on cooldown (the HUD reads the installed one). */
    private static void cooldownAll(ServerPlayer p, ExoModuleKind kind, int ticks) {
        if (ticks <= 0) return;
        for (int l = 1; l <= kind.maxLevel(); l++) p.getCooldowns().addCooldown(ExoItems.module(kind, l).get(), ticks);
    }

    /** Hazard Seal: clears poison, wither, hunger, nausea and blindness, paying per effect. */
    private static void hazardSeal(ServerPlayer p, State st) {
        if (!has(st, ExoModuleKind.HAZARD_SEAL)) return;
        double cost = ExoConfig.cost(ExoModuleKind.HAZARD_SEAL, 1);
        boolean cleared = false;
        for (Holder<MobEffect> effect : HAZARDS) {
            if (!p.hasEffect(effect) || !afford(st, ExoModuleKind.HAZARD_SEAL, cost)) continue;
            p.removeEffect(effect);
            spend(st, ExoModuleKind.HAZARD_SEAL, cost);
            cleared = true;
        }
        if (cleared) {
            p.level().playSound(null, p.getX(), p.getY(), p.getZ(), SoundEvents.BREWING_STAND_BREW, SoundSource.PLAYERS, 0.4F, 1.6F);
            p.serverLevel().sendParticles(ParticleTypes.WAX_OFF, p.getX(), p.getY() + 1.0, p.getZ(), 6, 0.3, 0.5, 0.3, 0.0);
        }
    }

    // ---------------------------------------------------------------- legs and boots

    private static void attributes(ServerPlayer p, State st) {
        int servo = lvl(st, ExoModuleKind.SERVO_STRIDE);
        boolean servoOn = false;
        if (servo > 0) {
            double cost = ExoConfig.perTick(ExoModuleKind.SERVO_STRIDE, servo);
            servoOn = afford(st, ExoModuleKind.SERVO_STRIDE, cost);
            if (servoOn && st.moving) spend(st, ExoModuleKind.SERVO_STRIDE, cost);
        }
        setModifier(p, Attributes.MOVEMENT_SPEED, SERVO_ID, servoOn ? ExoConfig.servoSpeed(servo) : 0, AttributeModifier.Operation.ADD_MULTIPLIED_TOTAL);

        double stepCost = ExoConfig.perTick(ExoModuleKind.STEP_ASSIST, 1);
        boolean stepOn = afford(st, ExoModuleKind.STEP_ASSIST, stepCost);
        if (stepOn && st.moving) spend(st, ExoModuleKind.STEP_ASSIST, stepCost);
        setModifier(p, Attributes.STEP_HEIGHT, STEP_ID, stepOn ? ExoConfig.stepBonus() : 0, AttributeModifier.Operation.ADD_VALUE);

        int spring = lvl(st, ExoModuleKind.SPRING_HEELS);
        boolean springOn = spring > 0 && afford(st, ExoModuleKind.SPRING_HEELS, ExoConfig.cost(ExoModuleKind.SPRING_HEELS, spring));
        setModifier(p, Attributes.JUMP_STRENGTH, JUMP_ID, springOn ? ExoConfig.springBoost(spring) : 0, AttributeModifier.Operation.ADD_VALUE);

        double finsCost = ExoConfig.perTick(ExoModuleKind.HYDRO_FINS, 1);
        boolean fins = afford(st, ExoModuleKind.HYDRO_FINS, finsCost);
        if (fins && p.isInWater()) spend(st, ExoModuleKind.HYDRO_FINS, finsCost);
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

    /** Kinetic Generator: walking on the ground charges the suit a little. */
    private static void kineticGenerator(ServerPlayer p, State st) {
        if (!has(st, ExoModuleKind.KINETIC_GENERATOR) || st.walked <= 0.02) return;
        if (!p.onGround() || p.isPassenger() || p.getAbilities().flying || p.isFallFlying() || p.isSpectator()) return;
        int perBlock = p.isSprinting() ? ExoConfig.kineticSprintPerBlock() : ExoConfig.cost(ExoModuleKind.KINETIC_GENERATOR, 1);
        st.made[st.act.piece(ExoModuleKind.KINETIC_GENERATOR)] += st.walked * perBlock;
    }

    private static void magnet(ServerPlayer p, State st) {
        int level = lvl(st, ExoModuleKind.MAGNET);
        if (level <= 0) return;
        double cost = ExoConfig.perTick(ExoModuleKind.MAGNET, level) * 2;
        if (!afford(st, ExoModuleKind.MAGNET, cost)) return;
        int radius = ExoConfig.magnetRadius(level);
        Vec3 target = p.position().add(0, 0.4, 0);
        AABB box = p.getBoundingBox().inflate(radius);
        List<ItemEntity> items = p.serverLevel().getEntitiesOfClass(ItemEntity.class, box,
                e -> e.isAlive() && !e.hasPickUpDelay() && e.distanceToSqr(p) > 0.6 && e.distanceToSqr(p) <= (double) radius * radius);
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
        if (pulled > 0) spend(st, ExoModuleKind.MAGNET, cost);
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

    /** Jet Assist double jump, requested by the client in mid air. All checks are repeated here. Returns true when it fired. */
    public static boolean doubleJump(ServerPlayer p) {
        State st = state(p);
        int level = st == null ? 0 : lvl(st, ExoModuleKind.JET_ASSIST);
        if (level <= 0) return false;
        Abilities ab = p.getAbilities();
        if (p.onGround() || p.isInWater() || p.isInLava() || ab.flying || p.isFallFlying() || p.isPassenger() || p.isSpectator()) return false;
        if (st.airJumps >= ExoConfig.airJumps(level) || p.tickCount - st.lastAirJumpTick < 6) return false;
        int cost = ExoConfig.doubleJumpCost(level);
        if (!afford(st, ExoModuleKind.JET_ASSIST, cost)) {
            CoreSounds.play(p, CoreSounds.ROBOT_ERROR, SoundSource.PLAYERS, 0.4F, 1.2F);
            return false;
        }
        spend(st, ExoModuleKind.JET_ASSIST, cost);
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

    /** Dash Thrusters: a short horizontal burst where the player looks. Returns true when it fired. */
    public static boolean dash(ServerPlayer p) {
        State st = state(p);
        if (st == null || !has(st, ExoModuleKind.DASH_THRUSTERS) || p.isPassenger() || p.isSpectator() || p.isFallFlying()) return false;
        Item item = ExoItems.module(ExoModuleKind.DASH_THRUSTERS).get();
        if (p.getCooldowns().isOnCooldown(item)) return false;
        double cost = ExoConfig.cost(ExoModuleKind.DASH_THRUSTERS, 1);
        if (!afford(st, ExoModuleKind.DASH_THRUSTERS, cost)) {
            CoreSounds.play(p, CoreSounds.ROBOT_ERROR, SoundSource.PLAYERS, 0.4F, 1.2F);
            return false;
        }
        spend(st, ExoModuleKind.DASH_THRUSTERS, cost);
        Vec3 dir = dashDirection(p.getYRot());
        double speed = ExoConfig.dashSpeed();
        Vec3 v = p.getDeltaMovement();
        p.setDeltaMovement(dir.x * speed, Math.max(v.y, 0.1), dir.z * speed);
        p.hurtMarked = true;
        p.resetFallDistance();
        int cooldown = ExoConfig.dashCooldown();
        if (st.core == ExoData.Core.ANTIGRAV) cooldown = (int) Math.round(cooldown * ExoConfig.antigravDashFactor());
        if (cooldown > 0) p.getCooldowns().addCooldown(item, cooldown);
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
        int level = st == null ? 0 : lvl(st, ExoModuleKind.SONAR_PULSE);
        if (level <= 0) return false;
        Item item = ExoItems.module(ExoModuleKind.SONAR_PULSE, level).get();
        if (p.getCooldowns().isOnCooldown(item)) return false;
        double cost = ExoConfig.cost(ExoModuleKind.SONAR_PULSE, level);
        if (!afford(st, ExoModuleKind.SONAR_PULSE, cost)) {
            CoreSounds.play(p, CoreSounds.ROBOT_ERROR, SoundSource.PLAYERS, 0.4F, 1.2F);
            return false;
        }
        spend(st, ExoModuleKind.SONAR_PULSE, cost);
        cooldownAll(p, ExoModuleKind.SONAR_PULSE, ExoConfig.sonarCooldown());
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
            CoreSounds.play(p, CoreSounds.ROBOT_ERROR, SoundSource.PLAYERS, 0.4F, 1.2F);
            return false;
        }
        st.pending[CHEST] += cost;
        int duration = ExoConfig.overclockDuration();
        if (ExoConfig.overclockHaste() > 0) p.addEffect(new MobEffectInstance(MobEffects.DIG_SPEED, duration, ExoConfig.overclockHaste() - 1, false, false, true));
        if (ExoConfig.overclockSpeed() > 0) p.addEffect(new MobEffectInstance(MobEffects.MOVEMENT_SPEED, duration, ExoConfig.overclockSpeed() - 1, false, false, true));
        if (ExoConfig.overclockCooldown() > 0) p.getCooldowns().addCooldown(core, ExoConfig.overclockCooldown());
        CoreSounds.play(p, CoreSounds.MACHINE_START, SoundSource.PLAYERS, 0.8F, 1.5F);
        p.level().playSound(null, p.getX(), p.getY(), p.getZ(), SoundEvents.BEACON_POWER_SELECT, SoundSource.PLAYERS, 0.5F, 1.8F);
        p.serverLevel().sendParticles(ParticleTypes.ELECTRIC_SPARK, p.getX(), p.getY() + 1.0, p.getZ(), 20, 0.4, 0.6, 0.4, 0.2);
        return true;
    }

    // ---------------------------------------------------------------- event hooks

    /** Rebreather: lets a player with the module and energy breathe under water. Runs on both sides (the client needs it to keep its bubbles). */
    public static void onBreathe(net.minecraft.world.entity.player.Player player, LivingBreatheEvent event) {
        if (event.canBreathe() || !player.isEyeInFluid(FluidTags.WATER)) return;
        if (!(player instanceof ServerPlayer p)) {
            if (ExoSuit.isActive(player, ExoModuleKind.REBREATHER)) event.setCanBreathe(true);
            return;
        }
        State st = STATES.get(p.getUUID());
        if (st == null) return;
        double cost = ExoConfig.perTick(ExoModuleKind.REBREATHER, 1);
        if (!afford(st, ExoModuleKind.REBREATHER, cost)) return;
        spend(st, ExoModuleKind.REBREATHER, cost);
        event.setCanBreathe(true);
    }

    /**
     * Damage to the wearer. Magma Core set: fire damage is ignored. Kinetic Shield: spends FE instead of health for up to
     * {@link ExoConfig#shieldAbsorb} of each hit; a fully absorbed hit is cancelled (no knockback).
     */
    public static void onIncomingDamage(ServerPlayer p, LivingIncomingDamageEvent event) {
        State st = STATES.get(p.getUUID());
        if (st == null) return;
        if (st.core == ExoData.Core.MAGMA && event.getSource().is(DamageTypeTags.IS_FIRE)) {
            event.setCanceled(true);
            return;
        }
        int level = lvl(st, ExoModuleKind.KINETIC_SHIELD);
        if (level <= 0) return;
        if (event.getSource().is(DamageTypeTags.BYPASSES_INVULNERABILITY)) return;
        float damage = event.getAmount();
        if (damage <= 0) return;
        double perPoint = ExoConfig.cost(ExoModuleKind.KINETIC_SHIELD, level) * st.factor;
        double have = available(st, st.act.piece(ExoModuleKind.KINETIC_SHIELD));
        float cap = (float) (damage * ExoConfig.shieldAbsorb(level));
        float absorbed = perPoint <= 0 ? cap : (float) Math.min(cap, have / perPoint);
        if (absorbed <= 0) return;
        st.pending[st.act.piece(ExoModuleKind.KINETIC_SHIELD)] += absorbed * perPoint;
        if (absorbed >= damage) event.setCanceled(true);
        else event.setAmount(damage - absorbed);
        CoreSounds.play(p, CoreSounds.SHOCK_ZAP, SoundSource.PLAYERS, 0.5F, 1.4F);
        p.serverLevel().sendParticles(ParticleTypes.ELECTRIC_SPARK, p.getX(), p.getY() + 1.0, p.getZ(), 12, 0.4, 0.6, 0.4, 0.1);
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
        if (st.core == ExoData.Core.ANTIGRAV) {
            event.setDamageMultiplier(0.0F);
            return;
        }
        int level = lvl(st, ExoModuleKind.FALL_DAMPENER);
        if (level <= 0) return;
        float blocks = event.getDistance() - 3.0F;
        if (blocks <= 0 || event.getDamageMultiplier() <= 0) return;
        double cost = (double) blocks * ExoConfig.cost(ExoModuleKind.FALL_DAMPENER, level) * st.factor;
        double have = available(st, st.act.piece(ExoModuleKind.FALL_DAMPENER));
        double fraction = cost <= 0 ? 1.0 : Mth.clamp(have / cost, 0.0, 1.0);
        if (fraction <= 0) return;
        st.pending[st.act.piece(ExoModuleKind.FALL_DAMPENER)] += cost * fraction;
        event.setDamageMultiplier((float) (event.getDamageMultiplier() * (1.0 - fraction)));
        CoreSounds.play(p, CoreSounds.MACHINE_STOP, SoundSource.PLAYERS, 0.5F, 1.5F);
        p.serverLevel().sendParticles(ParticleTypes.POOF, p.getX(), p.getY() + 0.1, p.getZ(), 8, 0.3, 0.05, 0.3, 0.02);
    }

    /** Spring Heels: the jump height itself is an attribute; this charges the jump. */
    public static void onJump(ServerPlayer p) {
        State st = STATES.get(p.getUUID());
        int level = st == null ? 0 : lvl(st, ExoModuleKind.SPRING_HEELS);
        if (level <= 0) return;
        double cost = ExoConfig.cost(ExoModuleKind.SPRING_HEELS, level);
        if (afford(st, ExoModuleKind.SPRING_HEELS, cost)) spend(st, ExoModuleKind.SPRING_HEELS, cost);
    }

    // ---------------------------------------------------------------- lifecycle

    /** Any game mode change: vanilla resets the abilities itself, so forget what we granted (the next tick re-grants). */
    public static void onGameModeChange(ServerPlayer p) {
        State st = STATES.get(p.getUUID());
        if (st != null) st.flightGranted = false;
    }

    /** Logout or death: undo everything and forget the player. */
    public static void onLeave(ServerPlayer p) {
        State st = STATES.remove(p.getUUID());
        if (st != null) release(p, st, false);
    }

    /** Respawn: new player object with fresh abilities. */
    public static void onRespawn(ServerPlayer p) {
        STATES.remove(p.getUUID());
    }

    /** Removes every effect of the suit from the player (the suit came off, or the player leaves). */
    private static void release(ServerPlayer p, State st, boolean soft) {
        setModifier(p, Attributes.MOVEMENT_SPEED, SERVO_ID, 0, AttributeModifier.Operation.ADD_MULTIPLIED_TOTAL);
        setModifier(p, Attributes.STEP_HEIGHT, STEP_ID, 0, AttributeModifier.Operation.ADD_VALUE);
        setModifier(p, Attributes.JUMP_STRENGTH, JUMP_ID, 0, AttributeModifier.Operation.ADD_VALUE);
        setModifier(p, NeoForgeMod.SWIM_SPEED, SWIM_ID, 0, AttributeModifier.Operation.ADD_MULTIPLIED_BASE);
        setModifier(p, Attributes.SUBMERGED_MINING_SPEED, DIG_ID, 0, AttributeModifier.Operation.ADD_VALUE);
        if (st.nv) removeOurs(p, MobEffects.NIGHT_VISION);
        if (st.fireRes) removeOurs(p, MobEffects.FIRE_RESISTANCE);
        if (st.gliding) removeOurs(p, MobEffects.SLOW_FALLING);
        if (st.flightGranted) revokeFlight(p, st, soft);
        flush(p, st);
    }
}
