package com.arno.robotica.exo.client;

import com.arno.robotica.core.CoreSounds;
import com.arno.robotica.core.module.ModuleKind;
import com.arno.robotica.exo.ExoActions;
import com.arno.robotica.exo.ExoData;
import com.arno.robotica.exo.ExoJet;
import com.arno.robotica.exo.ExoSuit;
import com.arno.robotica.exo.net.ExoActionPayload;
import com.arno.robotica.exo.net.ExoFxPayload;
import com.arno.robotica.exo.net.ExoJetHeatPayload;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.client.resources.sounds.AbstractTickableSoundInstance;
import net.minecraft.core.particles.ParticleOptions;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.network.protocol.game.ServerboundPlayerCommandPacket;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.Mth;
import net.minecraft.util.RandomSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.network.PacketDistributor;

import java.util.ArrayList;
import java.util.List;

/**
 * Client side of the Jet Assist. For the local player it reads the jump key and predicts thrust, hover and heat under
 * the server's rules ({@link ExoJet}); the server confirms or corrects with Jet Assist kinds of {@link ExoFxPayload}
 * (handed over by {@link ExoFx}) and sends the heat on a new player object
 * ({@link ExoJetHeatPayload}). For every firing pack in view (the local one and the ones the server reports) it draws
 * the nozzle flames and smoke, runs the thruster loop and gives the pod layer its glow.
 *
 * <p>Input: holding jump on the ground jumps as usual, and thrust takes over once it has been held
 * {@link ExoJet#HOLD_TICKS}. Jump in mid air thrusts at once. Letting go eases into a normal fall
 * ({@link ExoJet#settle}). With a Glider in the chestplate, a press while gliding ends the glide and thrusts, and the
 * Glider opens by itself a moment after thrust stopped in a fall. Creative and spectator players get none of it.
 */
final class ExoJetpack {
    private ExoJetpack() {}

    /** Smoothing of the pod glow per tick. */
    private static final float GLOW_STEP = 0.35F;
    /** Ticks a reported pack may be out of sight before it is forgotten. */
    private static final int FORGET = 40;
    /** Thruster loops play for packs this close, at most this many at once (like the Flight loops in {@link ExoFx}). */
    private static final double LOOP_RANGE = 24;
    private static final int MAX_LOOPS = 6;
    /** Ticks between two tries to open the Glider (the server may refuse one that crossed a stop in flight). */
    private static final int GLIDER_RETRY = 10;

    // ---- local prediction
    private static Player owner;
    private static boolean jumpWasDown;
    /** At the end of the last tick: standing on a block, grounded, flying (what this tick's vanilla jump started from). */
    private static boolean wasStanding, wasGrounded, wasFlying;
    /** The jump held now may thrust (it started in mid air, on the ground, or ended a glide). */
    private static boolean armed;
    private static boolean pressJumped;
    private static int pressTick;
    private static int mode = ExoJet.OFF;
    private static int jetLevel = 1;
    /** Heat in ticks of thrust, like the server's. */
    private static double heat;
    private static boolean overheated, hoverMode;
    /** The pack could fire now: a jump press goes to it, not to the Glider. */
    private static boolean ready;
    /** Thrust or hover stopped in mid air since the player last stood (the Glider may open), and when. */
    private static boolean thrustedAir;
    private static int offTick = -100;
    /** Ticks into the settle after thrust stopped in mid air ({@link ExoJet#settle}), -1 when not settling. */
    private static int settle = -1;
    /** When flying (Flight module, vanilla) last ended: hover mode catches the player for a moment after. */
    private static int flightEndTick = -100;
    private static final int HOVER_CATCH = 10;
    /** True while this class opens the Glider itself (the gate lets that through). */
    private static boolean opening;
    private static int coldTicks;
    private static int lastGliderTry = -100;
    private static double vy;

    // ---- packs in view
    private static final List<Jet> JETS = new ArrayList<>();

    private static final class Jet {
        final int id;
        int level = 1;
        int mode = ExoJet.OFF;
        boolean hot;
        int unseen;
        float glow, glowO;
        JetLoop loop;

        Jet(int id) {
            this.id = id;
        }
    }

    /** Local heat for the HUD, 0 to 1 of the fitted level's limit. */
    static double heat() {
        return Math.min(1.0, heat / ExoJet.maxHeat(jetLevel));
    }

    static boolean overheated() {
        return overheated;
    }

    static boolean hoverMode() {
        return hoverMode;
    }

    private static void send(int action) {
        PacketDistributor.sendToServer(new ExoActionPayload(action));
    }

    private static void reset() {
        jumpWasDown = false;
        wasStanding = false;
        wasGrounded = false;
        wasFlying = false;
        lastGliderTry = -100;
        offTick = -100;
        settle = -1;
        flightEndTick = -100;
        armed = false;
        mode = ExoJet.OFF;
        heat = 0;
        overheated = false;
        hoverMode = false;
        ready = false;
        thrustedAir = false;
        coldTicks = 0;
        for (Jet jet : JETS) {
            if (jet.loop != null) jet.loop.end();
        }
        JETS.clear();
    }

    /**
     * {@link ExoJet#gliderMayOpen} on the client: for the local player a jump press goes to the pack while it can fire,
     * so the Glider only opens by itself ({@link #glider}) or while the pack is overheated or out of energy.
     */
    static boolean gliderMayOpen(Player player) {
        if (player != owner) return true;
        return opening || !ready;
    }

    // ---------------------------------------------------------------- local player

    /** Once per client tick, after the player moved (ClientTickEvent.Post). {@code worn}: any Exo piece on. */
    static void tick(Minecraft mc, LocalPlayer player, boolean worn) {
        if (player != owner) {
            reset();
            owner = player;
        }
        if (ExoJetHeatPayload.take()) {
            heat = ExoJetHeatPayload.heatValue();
            overheated = ExoJetHeatPayload.overheatedValue();
            coldTicks = 0;
        }
        // A paused singleplayer server neither heats nor cools the pack.
        if (mc.isPaused()) return;
        int now = player.tickCount;
        boolean jumpDown = mc.screen == null && mc.options.keyJump.isDown();
        boolean press = jumpDown && !jumpWasDown;
        jumpWasDown = jumpDown;
        if (now < lastGliderTry) lastGliderTry = -100;
        if (now < offTick) offTick = -100;
        if (now < flightEndTick) flightEndTick = -100;
        if (wasFlying && !player.getAbilities().flying) flightEndTick = now;

        ExoSuit.Active active = worn ? ExoSuit.active(player) : null;
        int level = active == null ? 0 : active.level(ModuleKind.JET_ASSIST);
        if (level > 0) jetLevel = level;
        hoverMode = level >= ExoJet.HOVER_LEVEL && ExoData.hover(ExoSuit.piece(player, EquipmentSlot.CHEST));
        boolean usable = level > 0 && !ExoJet.creative(player);
        ready = usable && !overheated && ExoJet.canPay(player, active, false);

        // Gliding: a press with a ready pack ends the glide and thrusts. The thrust request ends it on the server too.
        boolean unglide = press && ready && player.isFallFlying();
        if (unglide) player.stopFallFlying();

        boolean grounded = ExoJet.grounded(player);
        boolean standing = ExoJet.standing(player);
        // On a ladder or scaffolding a press climbs: it does not start thrust.
        boolean climbing = player.onClimbable();
        if (grounded || climbing) thrustedAir = false;

        if (!jumpDown || (grounded && !standing)) armed = false;
        if (unglide) {
            armed = true;
            pressTick = now;
            pressJumped = false;
        } else if (usable && jumpDown && !climbing && (standing || (press && wasStanding))) {
            // Jump held on the ground: vanilla jumps, thrust takes over once it has been held a moment in the air.
            armed = true;
            pressTick = now;
            pressJumped = true;
        } else if (press && usable && !grounded && !wasGrounded && !climbing) {
            armed = true;
            pressTick = now;
            pressJumped = false;
        }

        int want = ExoJet.OFF;
        if (usable && !grounded && !overheated) {
            // Flight switched to hover in mid air (the Flight key cycle) hovers at once.
            boolean caught = now - flightEndTick <= HOVER_CATCH;
            if (armed && now - pressTick >= (pressJumped ? ExoJet.HOLD_TICKS : 0)) want = ExoJet.THRUST;
            else if ((mode != ExoJet.OFF || caught) && !jumpDown && hoverMode) want = ExoJet.HOVER;
            if (want != ExoJet.OFF && !ExoJet.canPay(player, active, want == ExoJet.HOVER)) want = ExoJet.OFF;
        }

        if (want != ExoJet.OFF) {
            heat = Math.min(ExoJet.maxHeat(level), heat + ExoJet.heatPerTick(ExoSuit.setBonus(player) == ExoData.Core.MAGMA, want == ExoJet.HOVER));
        } else if (heat > 0) {
            // Unworn, the server keeps the heat and cools it at the rate off the ground.
            heat = Math.max(0.0, heat - ExoJet.coolPerTick(worn && ExoJet.cooling(player)));
        }
        if (level > 0 && heat >= ExoJet.maxHeat(level)) {
            overheated = true;
            if (want != ExoJet.OFF) {
                armed = false;
                want = ExoJet.OFF;
            }
        }
        // The server's READY ends an overheat; a second of zero heat without it ends it too.
        coldTicks = overheated && heat <= 0 ? coldTicks + 1 : 0;
        if (coldTicks > 20) overheated = false;

        if (want != mode) {
            send(want == ExoJet.THRUST ? ExoActions.JET_THRUST : want == ExoJet.HOVER ? ExoActions.JET_HOVER : ExoActions.JET_STOP);
            if (mode == ExoJet.OFF) vy = player.getDeltaMovement().y;
            if (want == ExoJet.OFF && !grounded) {
                thrustedAir = true;
                offTick = now;
                settle = 0;
            }
            mode = want;
        }
        if (mode != ExoJet.OFF) {
            if (player.verticalCollision && !player.onGround() && vy > 0) vy = 0;
            vy = ExoJet.nextSpeed(vy, level, mode, player.isShiftKeyDown());
            Vec3 v = player.getDeltaMovement();
            player.setDeltaMovement(v.x, vy, v.z);
            player.resetFallDistance();
        }
        settle(player, grounded || climbing);
        glider(player, grounded, now);
        wasStanding = ExoJet.standing(player);
        wasGrounded = ExoJet.grounded(player);
        wasFlying = player.getAbilities().flying;

        Jet self = jet(player.getId(), worn && (mode != ExoJet.OFF || overheated));
        if (self != null) {
            self.level = jetLevel;
            if (overheated && !self.hot && self.mode != ExoJet.OFF) sputter(mc.level, player, jetLevel, true);
            self.mode = mode;
            self.hot = overheated;
        }
        effects(mc);
    }

    /** After thrust stopped in mid air: caps the fall speed for a few ticks, so the drop starts gently. */
    private static void settle(LocalPlayer player, boolean held) {
        if (mode != ExoJet.OFF || held) settle = -1;
        if (settle < 0) return;
        Vec3 v = player.getDeltaMovement();
        // Still rising: the climb coasts out as usual, the settle starts at the top.
        if (v.y > 0) return;
        player.setDeltaMovement(v.x, ExoJet.settle(v.y, settle), v.z);
        if (++settle >= ExoJet.SETTLE) settle = -1;
    }

    /**
     * Glider combo: once thrust stopped in mid air and the player falls (for {@link ExoJet#GLIDER_DELAY} ticks, or fast),
     * the Glider opens by itself, the same way a jump press opens it (vanilla {@code LocalPlayer.aiStep}); the server
     * checks it again. Short gaps between bursts stay jetpack.
     */
    private static void glider(LocalPlayer player, boolean grounded, int now) {
        if (grounded || mode != ExoJet.OFF || !thrustedAir) return;
        double dy = player.getDeltaMovement().y;
        if (!(dy < ExoJet.GLIDER_SINK || (dy < 0 && now - offTick >= ExoJet.GLIDER_DELAY))) return;
        if (now - lastGliderTry < GLIDER_RETRY || ExoSuit.pieceLevel(player, EquipmentSlot.CHEST, ModuleKind.GLIDER) <= 0) return;
        lastGliderTry = now;
        opening = true;
        try {
            if (player.tryToStartFallFlying()) {
                player.connection.send(new ServerboundPlayerCommandPacket(player, ServerboundPlayerCommandPacket.Action.START_FALL_FLYING));
            }
        } finally {
            opening = false;
        }
    }

    /** A Jet Assist kind of {@link ExoFxPayload} from the server: corrections for the local player, other players' packs. */
    static void onEvent(ClientLevel level, LocalPlayer player, int id, int kind) {
        if (player != owner) {
            reset();
            owner = player;
        }
        boolean particles = ExoClientConfig.particles();
        if (id == player.getId()) {
            boolean firing = mode != ExoJet.OFF;
            switch (kind) {
                case ExoFxPayload.JET_OVERHEAT, ExoFxPayload.JET_EMPTY, ExoFxPayload.JET_REFUSED -> {
                    mode = ExoJet.OFF;
                    armed = false;
                    if (firing && !ExoJet.grounded(player)) {
                        thrustedAir = true;
                        offTick = player.tickCount;
                        settle = 0;
                    }
                    boolean wasHot = overheated;
                    if (kind == ExoFxPayload.JET_OVERHEAT) {
                        overheated = true;
                        heat = Math.max(heat, ExoJet.maxHeat(jetLevel));
                    }
                    if (firing && particles && kind != ExoFxPayload.JET_REFUSED && !wasHot) {
                        sputter(level, player, jetLevel, kind == ExoFxPayload.JET_OVERHEAT);
                    }
                }
                case ExoFxPayload.JET_READY -> {
                    heat = 0;
                    overheated = false;
                }
                // Hover mode itself comes with the chestplate; a switch-off ends a predicted hover.
                case ExoFxPayload.HOVER_OFF -> {
                    if (mode == ExoJet.HOVER) mode = ExoJet.OFF;
                }
                default -> {
                }
            }
            Jet self = jet(id, false);
            if (self != null) {
                self.mode = mode;
                self.hot = overheated;
            }
            return;
        }
        Jet jet = jet(id, true);
        switch (kind) {
            case ExoFxPayload.JET_THRUST_1, ExoFxPayload.JET_THRUST_2, ExoFxPayload.JET_THRUST_3 -> {
                jet.mode = ExoJet.THRUST;
                jet.level = kind - ExoFxPayload.JET_THRUST_1 + 1;
            }
            case ExoFxPayload.JET_HOVER -> {
                jet.mode = ExoJet.HOVER;
                jet.level = ExoJet.HOVER_LEVEL;
            }
            case ExoFxPayload.JET_OFF -> jet.mode = ExoJet.OFF;
            case ExoFxPayload.JET_OVERHEAT, ExoFxPayload.JET_EMPTY -> {
                jet.mode = ExoJet.OFF;
                jet.hot = kind == ExoFxPayload.JET_OVERHEAT;
                if (particles && level.getEntity(id) instanceof Player p) sputter(level, p, jet.level, jet.hot);
            }
            case ExoFxPayload.JET_READY -> jet.hot = false;
            default -> {
            }
        }
    }

    private static Jet jet(int id, boolean create) {
        for (int i = 0; i < JETS.size(); i++) {
            if (JETS.get(i).id == id) return JETS.get(i);
        }
        if (!create) return null;
        Jet jet = new Jet(id);
        JETS.add(jet);
        return jet;
    }

    // ---------------------------------------------------------------- effects

    /** Glow of a player's pods for the render layer, 0 (dark) to 1 (full thrust). */
    static float glow(Player player, float partialTick) {
        Jet jet = jet(player.getId(), false);
        return jet == null ? 0 : Mth.lerp(partialTick, jet.glowO, jet.glow);
    }

    private static void effects(Minecraft mc) {
        ClientLevel level = mc.level;
        if (level == null) return;
        boolean particles = ExoClientConfig.particles();
        int loops = 0;
        for (Jet jet : JETS) {
            if (jet.loop != null && !jet.loop.isStopped()) loops++;
        }
        for (int i = JETS.size() - 1; i >= 0; i--) {
            Jet jet = JETS.get(i);
            Entity entity = level.getEntity(jet.id);
            if (!(entity instanceof Player p) || entity.isRemoved()) {
                if (++jet.unseen > FORGET) {
                    if (jet.loop != null) jet.loop.end();
                    JETS.remove(i);
                }
                continue;
            }
            jet.unseen = 0;
            float target = jet.mode == ExoJet.THRUST ? 1.0F : jet.mode == ExoJet.HOVER ? 0.65F : jet.hot ? 0.2F : 0.0F;
            jet.glowO = jet.glow;
            jet.glow += (target - jet.glow) * GLOW_STEP;
            if (jet.mode == ExoJet.OFF && !jet.hot && jet.glow < 0.01F && (jet.loop == null || jet.loop.isStopped())) {
                JETS.remove(i);
                continue;
            }
            if (p.isInvisible()) continue;
            if (jet.mode != ExoJet.OFF) {
                if (particles) flames(level, p, jet.level, jet.mode == ExoJet.HOVER);
                if (ExoClientConfig.flightSound() && (jet.loop == null || jet.loop.isStopped()) && loops < MAX_LOOPS
                        && mc.player != null && p.distanceToSqr(mc.player) <= LOOP_RANGE * LOOP_RANGE) {
                    jet.loop = new JetLoop(jet, p);
                    mc.getSoundManager().play(jet.loop);
                    loops++;
                }
            } else if (particles && jet.hot && (p.tickCount + jet.id) % 5 == 0) {
                shimmer(level, p);
            }
        }
    }

    /** World position of a nozzle exit: {@code side} -1 or 1. */
    private static double nozzleX(Player p, int side) {
        float yaw = p.yBodyRot * Mth.DEG_TO_RAD;
        double back = p.isCrouching() ? 0.55 : 0.32;
        return p.getX() + Mth.sin(yaw) * back - Mth.cos(yaw) * 0.19 * side;
    }

    private static double nozzleZ(Player p, int side) {
        float yaw = p.yBodyRot * Mth.DEG_TO_RAD;
        double back = p.isCrouching() ? 0.55 : 0.32;
        return p.getZ() - Mth.cos(yaw) * back - Mth.sin(yaw) * 0.19 * side;
    }

    private static double nozzleY(Player p) {
        return p.getY() + (p.isCrouching() ? 0.55 : 0.78);
    }

    /** Exhaust of both nozzles: bigger and brighter flames per level, smoke trailing behind. Hover burns softer. */
    private static void flames(ClientLevel level, Player p, int jetLevel, boolean hover) {
        RandomSource r = level.random;
        if (hover && (p.tickCount & 1) == 1) return;
        double mx = p.getX() - p.xo, my = p.getY() - p.yo, mz = p.getZ() - p.zo;
        double down = -(0.16 + 0.06 * jetLevel);
        for (int side = -1; side <= 1; side += 2) {
            double x = nozzleX(p, side), y = nozzleY(p), z = nozzleZ(p, side);
            ParticleOptions main = jetLevel >= 2 ? ParticleTypes.FLAME : ParticleTypes.SMALL_FLAME;
            int count = jetLevel >= 3 ? 2 : 1;
            for (int n = 0; n < count; n++) {
                level.addParticle(main, x + r.nextGaussian() * 0.025, y - n * 0.08, z + r.nextGaussian() * 0.025,
                        mx * 0.6 + r.nextGaussian() * 0.012, my * 0.4 + down, mz * 0.6 + r.nextGaussian() * 0.012);
            }
            if (jetLevel >= 2) {
                level.addParticle(ParticleTypes.SMALL_FLAME, x, y - 0.05, z, mx * 0.6, my * 0.4 + down * 1.3, mz * 0.6);
            }
            if (((p.tickCount + side) & 1) == 0) {
                level.addParticle(ParticleTypes.SMOKE, x + r.nextGaussian() * 0.04, y - 0.3, z + r.nextGaussian() * 0.04,
                        mx * 0.3 + r.nextGaussian() * 0.01, down * 0.5, mz * 0.3 + r.nextGaussian() * 0.01);
            }
            if (jetLevel >= 3 && !hover && (p.tickCount + side) % 3 == 0) {
                level.addParticle(ParticleTypes.LARGE_SMOKE, x, y - 0.45, z, mx * 0.2, down * 0.3, mz * 0.2);
            }
        }
    }

    /** Cut-out: sparks, a few lava pops and a burst of smoke from both nozzles. */
    private static void sputter(ClientLevel level, Player p, int jetLevel, boolean overheat) {
        if (level == null || !ExoClientConfig.particles()) return;
        RandomSource r = level.random;
        for (int side = -1; side <= 1; side += 2) {
            double x = nozzleX(p, side), y = nozzleY(p), z = nozzleZ(p, side);
            for (int n = 0; n < 3 + jetLevel; n++) {
                level.addParticle(ParticleTypes.FIREWORK, x, y, z, r.nextGaussian() * 0.08, -0.05 - r.nextDouble() * 0.12, r.nextGaussian() * 0.08);
            }
            for (int n = 0; n < 3; n++) {
                level.addParticle(ParticleTypes.LARGE_SMOKE, x + r.nextGaussian() * 0.08, y - 0.1, z + r.nextGaussian() * 0.08,
                        r.nextGaussian() * 0.02, 0.02, r.nextGaussian() * 0.02);
            }
            if (overheat) {
                level.addParticle(ParticleTypes.LAVA, x, y, z, 0, 0, 0);
                level.addParticle(ParticleTypes.WHITE_SMOKE, x, y + 0.5, z, 0, 0.06, 0);
            }
        }
    }

    /** Overheated: a heat haze of pale smoke rising off the pods. */
    private static void shimmer(ClientLevel level, Player p) {
        RandomSource r = level.random;
        for (int side = -1; side <= 1; side += 2) {
            level.addParticle(ParticleTypes.WHITE_SMOKE, nozzleX(p, side) + r.nextGaussian() * 0.04, nozzleY(p) + 0.45,
                    nozzleZ(p, side) + r.nextGaussian() * 0.04, r.nextGaussian() * 0.005, 0.035, r.nextGaussian() * 0.005);
        }
    }

    /** The thruster rush of one pack, following its player; fades out when it stops. A bit louder and higher per level. */
    private static final class JetLoop extends AbstractTickableSoundInstance {
        private final Jet jet;
        private final Player player;
        private int fade = 0;

        JetLoop(Jet jet, Player player) {
            super(CoreSounds.EXO_JET_LOOP.get(), SoundSource.PLAYERS, RandomSource.create());
            this.jet = jet;
            this.player = player;
            this.looping = true;
            this.delay = 0;
            this.volume = 0.01F;
            this.pitch = 1.0F;
            this.x = player.getX();
            this.y = player.getY() + 0.8;
            this.z = player.getZ();
        }

        void end() {
            stop();
        }

        @Override
        public boolean canStartSilent() {
            return true;
        }

        @Override
        public void tick() {
            boolean on = !player.isRemoved() && player.level() == Minecraft.getInstance().level && jet.mode != ExoJet.OFF
                    && ExoClientConfig.flightSound();
            fade = on ? Math.min(4, fade + 1) : fade - 1;
            if (fade <= 0 && !on) {
                stop();
                return;
            }
            this.x = player.getX();
            this.y = player.getY() + 0.8;
            this.z = player.getZ();
            boolean hover = jet.mode == ExoJet.HOVER;
            this.volume = (0.2F + 0.05F * jet.level) * (hover ? 0.6F : 1.0F) * fade / 4.0F;
            this.pitch = 0.9F + 0.05F * jet.level + (hover ? -0.05F : 0.0F);
        }
    }
}
