package com.arno.robotica.exo;

import com.arno.robotica.Robotica;
import com.arno.robotica.core.CoreSounds;
import com.arno.robotica.core.energy.ItemEnergy;
import com.arno.robotica.core.item.CellItem;
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
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.ai.attributes.Attribute;
import net.minecraft.world.entity.ai.attributes.AttributeInstance;
import net.minecraft.world.entity.ai.attributes.AttributeModifier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.entity.player.Abilities;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.GameType;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.common.util.FakePlayer;
import net.neoforged.neoforge.event.entity.living.LivingBreatheEvent;
import net.neoforged.neoforge.event.entity.living.LivingFallEvent;
import net.neoforged.neoforge.event.entity.living.LivingIncomingDamageEvent;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Server side logic of the Exo-Frame, run once per player tick (see {@link ExoEvents}).
 *
 * <p>Everything is evaluated from the worn items each tick, so removing a piece, a module, the energy or the toggle
 * undoes the effect by itself: attribute modifiers are transient and removed when no longer wanted, mob effects are
 * short and refreshed, flight is a granted {@code mayfly} that is revoked again (never touched in creative or
 * spectator). Energy costs are collected per piece and written to the items once every 10 ticks, so the armor stack
 * does not change (and is not re-sent to every nearby player) on every tick.
 */
public final class ExoTicker {
    private ExoTicker() {}

    private static final ResourceLocation SERVO_ID = Robotica.id("exo_servo_stride");
    private static final ResourceLocation STEP_ID = Robotica.id("exo_step_assist");
    private static final ResourceLocation JUMP_ID = Robotica.id("exo_spring_heels");
    private static final int FLUSH_INTERVAL = 10;
    private static final int HEAD = 0, CHEST = 1, LEGS = 2, FEET = 3;

    /** Per player runtime state, keyed by UUID and dropped on logout, death or when the suit comes off. */
    static final class State {
        final double[] pending = new double[4];
        final int[] energy = new int[4];
        boolean full;
        int mask;
        int tick;
        double lastX = Double.NaN, lastY, lastZ;
        boolean moving;
        double dy;
        boolean flightGranted;
        boolean nv;
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
        st.moving = !Double.isNaN(st.lastX) && dx * dx + dz * dz > 0.0004;
        st.dy = Double.isNaN(st.lastX) ? 0 : p.getY() - st.lastY;
        st.lastX = p.getX();
        st.lastY = p.getY();
        st.lastZ = p.getZ();

        nightVision(p, st);
        jetAssist(p, st);
        flight(p, st);
        speedAndSteps(p, st);
        if ((st.tick & 1) == 0) magnet(p, st);
        if (st.tick % 20 == 0 && ExoConfig.cellRecharge()) rechargeFromCells(p);
        if (st.tick % FLUSH_INTERVAL == 0) flush(p, st);
    }

    /** Writes the collected costs to the armor items. Called every 10 ticks; public for tests. */
    public static void flush(ServerPlayer p) {
        State st = STATES.get(p.getUUID());
        if (st != null) flush(p, st);
    }

    private static void flush(ServerPlayer p, State st) {
        boolean any = false;
        for (int i = 0; i < 4; i++) {
            int amount = (int) st.pending[i];
            if (amount <= 0) continue;
            ExoSuit.drain(p, ExoSuit.SLOTS[i], amount);
            st.pending[i] -= amount;
            any = true;
        }
        if (any) refresh(p, st);
    }

    private static void refresh(ServerPlayer p, State st) {
        st.mask = 0;
        st.full = true;
        for (int i = 0; i < 4; i++) {
            ItemStack piece = ExoSuit.piece(p, ExoSuit.SLOTS[i]);
            if (piece.isEmpty()) {
                st.full = false;
                st.energy[i] = 0;
                continue;
            }
            st.energy[i] = ItemEnergy.get(piece);
            st.mask |= ExoData.activeMask(piece);
        }
    }

    private static boolean has(State st, ExoModuleKind kind) {
        return (st.mask & kind.bit()) != 0;
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

    private static boolean afford(State st, ExoModuleKind kind, double fe) {
        return has(st, kind) && available(st, kind.pieceIndex()) >= Math.max(fe, 1);
    }

    private static void spend(State st, ExoModuleKind kind, double fe) {
        st.pending[kind.pieceIndex()] += fe;
    }

    // ---------------------------------------------------------------- modules

    private static void nightVision(ServerPlayer p, State st) {
        double cost = ExoConfig.perTick(ExoModuleKind.NIGHT_VISION);
        if (afford(st, ExoModuleKind.NIGHT_VISION, cost)) {
            spend(st, ExoModuleKind.NIGHT_VISION, cost);
            MobEffectInstance cur = p.getEffect(MobEffects.NIGHT_VISION);
            if (cur == null || cur.getDuration() < 240) {
                p.addEffect(new MobEffectInstance(MobEffects.NIGHT_VISION, 300, 0, true, false, false));
            }
            st.nv = true;
        } else if (st.nv) {
            st.nv = false;
            removeOurs(p, MobEffects.NIGHT_VISION);
        }
        // Robot HUD is a client display; it only draws power.
        double hud = ExoConfig.perTick(ExoModuleKind.ROBOT_HUD);
        if (afford(st, ExoModuleKind.ROBOT_HUD, hud)) spend(st, ExoModuleKind.ROBOT_HUD, hud);
    }

    /** An effect we applied is ambient, without particles and without an icon. A potion or beacon is never removed. */
    private static void removeOurs(ServerPlayer p, Holder<net.minecraft.world.effect.MobEffect> effect) {
        MobEffectInstance cur = p.getEffect(effect);
        if (cur != null && cur.isAmbient() && !cur.isVisible() && !cur.showIcon()) p.removeEffect(effect);
    }

    private static void jetAssist(ServerPlayer p, State st) {
        Abilities ab = p.getAbilities();
        boolean grounded = p.onGround() || p.isInWater() || p.isInLava() || ab.flying || p.isFallFlying() || p.isPassenger();
        if (grounded) st.airJumps = 0;
        double cost = ExoConfig.perTick(ExoModuleKind.JET_ASSIST);
        boolean glide = !grounded && afford(st, ExoModuleKind.JET_ASSIST, cost) && (st.gliding ? st.dy < -0.01 : st.dy < -0.5);
        if (glide) {
            spend(st, ExoModuleKind.JET_ASSIST, cost);
            MobEffectInstance cur = p.getEffect(MobEffects.SLOW_FALLING);
            if (cur == null || cur.getDuration() < 6) {
                p.addEffect(new MobEffectInstance(MobEffects.SLOW_FALLING, 12, 0, true, false, false));
            }
            p.resetFallDistance();
            st.gliding = true;
        } else if (st.gliding) {
            st.gliding = false;
            removeOurs(p, MobEffects.SLOW_FALLING);
        }
    }

    private static void flight(ServerPlayer p, State st) {
        boolean creativeLike = creativeLike(p);
        double cost = ExoConfig.perTick(ExoModuleKind.FLIGHT);
        boolean want = !creativeLike && afford(st, ExoModuleKind.FLIGHT, cost);
        Abilities ab = p.getAbilities();
        if (want) {
            if (!ab.mayfly) {
                ab.mayfly = true;
                st.flightGranted = true;
                p.onUpdateAbilities();
            }
            if (ab.flying) spend(st, ExoModuleKind.FLIGHT, cost);
        } else if (st.flightGranted) {
            ItemStack chest = ExoSuit.piece(p, EquipmentSlot.CHEST);
            boolean moduleStillWorn = !chest.isEmpty() && (ExoData.installedMask(chest) & ExoModuleKind.FLIGHT.bit()) != 0;
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

    private static void speedAndSteps(ServerPlayer p, State st) {
        ExoModuleKind servo = has(st, ExoModuleKind.SERVO_STRIDE_3) ? ExoModuleKind.SERVO_STRIDE_3
                : has(st, ExoModuleKind.SERVO_STRIDE_2) ? ExoModuleKind.SERVO_STRIDE_2
                : has(st, ExoModuleKind.SERVO_STRIDE_1) ? ExoModuleKind.SERVO_STRIDE_1 : null;
        boolean servoOn = false;
        if (servo != null) {
            double cost = ExoConfig.perTick(servo);
            servoOn = afford(st, servo, cost);
            if (servoOn && st.moving) spend(st, servo, cost);
        }
        setModifier(p, Attributes.MOVEMENT_SPEED, SERVO_ID, servoOn ? 0.2 * servo.level : 0, AttributeModifier.Operation.ADD_MULTIPLIED_TOTAL);

        double stepCost = ExoConfig.perTick(ExoModuleKind.STEP_ASSIST);
        boolean stepOn = afford(st, ExoModuleKind.STEP_ASSIST, stepCost);
        if (stepOn && st.moving) spend(st, ExoModuleKind.STEP_ASSIST, stepCost);
        setModifier(p, Attributes.STEP_HEIGHT, STEP_ID, stepOn ? 0.5 : 0, AttributeModifier.Operation.ADD_VALUE);

        boolean springOn = afford(st, ExoModuleKind.SPRING_HEELS, ExoConfig.cost(ExoModuleKind.SPRING_HEELS));
        setModifier(p, Attributes.JUMP_STRENGTH, JUMP_ID, springOn ? 0.35 : 0, AttributeModifier.Operation.ADD_MULTIPLIED_TOTAL);
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

    private static void magnet(ServerPlayer p, State st) {
        double cost = ExoConfig.perTick(ExoModuleKind.MAGNET) * 2;
        if (!afford(st, ExoModuleKind.MAGNET, cost)) return;
        int radius = ExoConfig.magnetRadius();
        Vec3 target = p.position().add(0, 0.4, 0);
        AABB box = p.getBoundingBox().inflate(radius);
        List<ItemEntity> items = ((ServerLevel) p.level()).getEntitiesOfClass(ItemEntity.class, box,
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
        double cost = ExoConfig.perTick(ExoModuleKind.REBREATHER);
        if (!afford(st, ExoModuleKind.REBREATHER, cost)) return;
        spend(st, ExoModuleKind.REBREATHER, cost);
        event.setCanBreathe(true);
    }

    /** Kinetic Shield: spends FE instead of health for up to {@link ExoConfig#shieldAbsorb} of each hit; a fully absorbed hit is cancelled (no knockback). */
    public static void onIncomingDamage(ServerPlayer p, LivingIncomingDamageEvent event) {
        State st = STATES.get(p.getUUID());
        if (st == null || !has(st, ExoModuleKind.KINETIC_SHIELD)) return;
        if (event.getSource().is(DamageTypeTags.BYPASSES_INVULNERABILITY)) return;
        float damage = event.getAmount();
        if (damage <= 0) return;
        int perPoint = ExoConfig.cost(ExoModuleKind.KINETIC_SHIELD);
        double have = available(st, CHEST);
        float cap = (float) (damage * ExoConfig.shieldAbsorb());
        float absorbed = perPoint <= 0 ? cap : (float) Math.min(cap, have / perPoint);
        if (absorbed <= 0) return;
        spend(st, ExoModuleKind.KINETIC_SHIELD, absorbed * perPoint);
        if (absorbed >= damage) event.setCanceled(true);
        else event.setAmount(damage - absorbed);
        CoreSounds.play(p, CoreSounds.SHOCK_ZAP, SoundSource.PLAYERS, 0.5F, 1.4F);
        ((ServerLevel) p.level()).sendParticles(ParticleTypes.ELECTRIC_SPARK, p.getX(), p.getY() + 1.0, p.getZ(), 12, 0.4, 0.6, 0.4, 0.1);
    }

    /** Fall Dampener: absorbs fall damage for FE per block fallen (beyond the 3 safe blocks). Partial with little energy. */
    public static void onFall(ServerPlayer p, LivingFallEvent event) {
        State st = STATES.get(p.getUUID());
        if (st == null || !has(st, ExoModuleKind.FALL_DAMPENER)) return;
        float blocks = event.getDistance() - 3.0F;
        if (blocks <= 0 || event.getDamageMultiplier() <= 0) return;
        double cost = (double) blocks * ExoConfig.cost(ExoModuleKind.FALL_DAMPENER);
        double have = available(st, FEET);
        double fraction = cost <= 0 ? 1.0 : Mth.clamp(have / cost, 0.0, 1.0);
        if (fraction <= 0) return;
        spend(st, ExoModuleKind.FALL_DAMPENER, cost * fraction);
        event.setDamageMultiplier((float) (event.getDamageMultiplier() * (1.0 - fraction)));
        CoreSounds.play(p, CoreSounds.MACHINE_STOP, SoundSource.PLAYERS, 0.5F, 1.5F);
        ((ServerLevel) p.level()).sendParticles(ParticleTypes.POOF, p.getX(), p.getY() + 0.1, p.getZ(), 8, 0.3, 0.05, 0.3, 0.02);
    }

    /** Spring Heels: the jump height itself is an attribute; this charges the jump. */
    public static void onJump(ServerPlayer p) {
        State st = STATES.get(p.getUUID());
        if (st == null || !has(st, ExoModuleKind.SPRING_HEELS)) return;
        double cost = ExoConfig.cost(ExoModuleKind.SPRING_HEELS);
        if (available(st, FEET) >= Math.max(cost, 1)) spend(st, ExoModuleKind.SPRING_HEELS, cost);
    }

    /** Jet Assist double jump, requested by the client in mid air. All checks are repeated here. Returns true when it fired. */
    public static boolean doubleJump(ServerPlayer p) {
        State st = STATES.get(p.getUUID());
        if (st == null || !has(st, ExoModuleKind.JET_ASSIST)) return false;
        Abilities ab = p.getAbilities();
        if (p.onGround() || p.isInWater() || p.isInLava() || ab.flying || p.isFallFlying() || p.isPassenger() || p.isSpectator()) return false;
        if (st.airJumps >= ExoConfig.airJumps() || p.tickCount - st.lastAirJumpTick < 6) return false;
        int cost = ExoConfig.doubleJumpCost();
        if (available(st, CHEST) < Math.max(cost, 1)) {
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
        ((ServerLevel) p.level()).sendParticles(ParticleTypes.CLOUD, p.getX(), p.getY() + 0.1, p.getZ(), 10, 0.2, 0.05, 0.2, 0.05);
        return true;
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
        setModifier(p, Attributes.JUMP_STRENGTH, JUMP_ID, 0, AttributeModifier.Operation.ADD_MULTIPLIED_TOTAL);
        if (st.nv) removeOurs(p, MobEffects.NIGHT_VISION);
        if (st.gliding) removeOurs(p, MobEffects.SLOW_FALLING);
        if (st.flightGranted) revokeFlight(p, st, soft);
        flush(p, st);
    }
}
