package com.arno.robotica.gear.client;

import com.arno.robotica.core.CoreSounds;
import com.arno.robotica.core.particle.CoreParticles;
import com.arno.robotica.core.particle.TintParticleOptions;
import com.arno.robotica.gear.GearClientConfig;
import com.arno.robotica.gear.weapon.NullLanceItem;
import it.unimi.dsi.fastutil.ints.Int2ObjectOpenHashMap;
import net.minecraft.Util;
import net.minecraft.client.CameraType;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.player.AbstractClientPlayer;
import net.minecraft.client.resources.sounds.AbstractTickableSoundInstance;
import net.minecraft.client.resources.sounds.SoundInstance;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.Mth;
import net.minecraft.util.RandomSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.HumanoidArm;
import net.minecraft.world.entity.player.Player;
import net.neoforged.neoforge.client.event.ClientTickEvent;
import net.neoforged.neoforge.client.event.ViewportEvent;

import java.util.List;

/**
 * Null Lance effects on the client. For every lance charging in view (the own one and other players'): void motes
 * drawn into the muzzle, a pulse as each of the 4 coils lights (every quarter of {@link NullLanceItem#CHARGE_TICKS}),
 * the lock-in sound and flash at full charge, then a quiet hum loop and a slow glow at the muzzle while it is held.
 * The charge whine stops when the charge is let go. The server's {@link com.arno.robotica.gear.GearFxPayload} shots
 * are drawn by {@link #beam} and {@link #boltMuzzle}; the own beam shot also kicks the camera a little and flashes the
 * crosshair brackets ({@link LanceHud}).
 *
 * <p>In the own first-person view every effect sits at {@link #OWN_MUZZLE_FORWARD} and to the gun side and below the
 * crosshair, so nothing covers it; everyone else sees it at the rifle tip in the hand.
 */
final class LanceFx {
    private LanceFx() {}

    // ---- Muzzle: where the rifle tip is, at the hip or rest hold and shouldered (LanceClient, LanceArms, the model's
    // display transforms in scripts/data/gear_lance_model.py). The effects follow the blend between the two.
    /** Own first-person view at the hip: blocks along the look, to the gun side and below the crosshair (camera space). */
    static final double OWN_MUZZLE_FORWARD = 1.75;
    static final double OWN_MUZZLE_SIDE = 0.20;
    static final double OWN_MUZZLE_DOWN = 0.31;
    /** Own first-person view, shouldered. */
    static final double OWN_AIM_FORWARD = 1.61;
    static final double OWN_AIM_SIDE = 0.053;
    static final double OWN_AIM_DOWN = 0.21;
    /** Other players and the own third-person view, shouldered: blocks along the look, to the gun arm, down from the eyes. */
    static final double HELD_MUZZLE_FORWARD = 1.14;
    static final double HELD_MUZZLE_SIDE = 0.17;
    static final double HELD_MUZZLE_DOWN = 0.35;
    /** At rest (carried low across the body, barrel forward and down): blocks ahead of the body, to the gun arm, down. */
    static final double REST_MUZZLE_FORWARD = 1.40;
    static final double REST_MUZZLE_SIDE = 0.0;
    static final double REST_MUZZLE_DOWN = 0.67;

    // ---- Colours
    /** White-violet beam core. */
    static final int VOID_CORE = 0xF4ECFF;
    static final int VOID_BRIGHT = 0xD2B4FF;
    /** Dark purple of the spiral and the collapse (additive, so it glows deep violet). */
    static final int VOID_DEEP = 0x7A3CE0;
    /** Void smoke, lit by the world. */
    static final int VOID_SMOKE = 0x22103A;

    // ---- Charge
    private static final int COILS = 4;
    private static final int COIL_TICKS = NullLanceItem.CHARGE_TICKS / COILS;
    /** Charges further than this from the camera are not drawn or heard. */
    private static final double FX_RANGE = 32.0;
    private static final float CHARGE_VOLUME = 0.6F, READY_VOLUME = 0.8F, HUM_VOLUME = 0.2F;
    /** Charge whines only start this early in a charge (one seen later, e.g. a player walking into view, stays quiet). */
    private static final int LATE_START = 3;

    // ---- Beam
    private static final double MAX_BEAM = 48.0;
    private static final double CORE_STEP = 0.25;
    private static final double HELIX_RADIUS = 0.22, HELIX_TWIST = 2.2, HELIX_SPIN = 0.02;
    /** A ring every this many core steps along the beam, after the first {@link #RING_START} steps. */
    private static final int RING_EVERY = 12, RING_START = 8;
    /** The own view gets no spiral this close to the muzzle. */
    private static final double OWN_CLEAR = 1.5;

    // ---- Camera kick on the own beam shot (scaled by the Screen Effects option)
    private static final long KICK_MS = 190;
    private static final float KICK_PITCH = 1.8F, KICK_ROLL = 0.7F;

    private static final Int2ObjectOpenHashMap<Charge> CHARGES = new Int2ObjectOpenHashMap<>();
    /** Scratch: muzzle x, y, z then look x, y, z (client thread only). */
    private static final double[] MUZ = new double[6];
    /** Scratch: two unit vectors across a direction. */
    private static final double[] ACROSS = new double[6];
    private static ClientLevel lastLevel;
    private static int stamp;
    private static long kickStart;

    private static final class Charge {
        final Player player;
        int coils;
        int ticks;
        int seen;
        HumSound hum;

        Charge(Player player, int coils) {
            this.player = player;
            this.coils = coils;
        }
    }

    static boolean charging(Player p) {
        return !p.isRemoved() && p.isUsingItem() && p.getUseItem().getItem() instanceof NullLanceItem;
    }

    private static boolean ownView(Minecraft mc, Entity e) {
        return e == mc.getCameraEntity() && mc.options.getCameraType() == CameraType.FIRST_PERSON;
    }

    static void onClientTick(ClientTickEvent.Post event) {
        Minecraft mc = Minecraft.getInstance();
        ClientLevel level = mc.level;
        if (level != lastLevel) {
            CHARGES.clear();
            lastLevel = level;
            kickStart = 0;
        }
        Entity camera = mc.getCameraEntity();
        if (level == null || camera == null || mc.isPaused()) return;
        stamp++;
        List<AbstractClientPlayer> players = level.players();
        for (int i = 0; i < players.size(); i++) {
            AbstractClientPlayer p = players.get(i);
            if (!charging(p) || p.distanceToSqr(camera) > FX_RANGE * FX_RANGE) continue;
            int ticks = p.getTicksUsingItem();
            Charge c = CHARGES.get(p.getId());
            if (c == null || c.player != p || ticks < c.ticks) {
                if (c != null) endHum(c);
                c = new Charge(p, Math.min(COILS, ticks / COIL_TICKS));
                CHARGES.put(p.getId(), c);
                if (ticks <= LATE_START) mc.getSoundManager().play(new ChargeSound(p));
                if (c.coils == COILS) startHum(mc, c);
            }
            c.seen = stamp;
            c.ticks = ticks;
            tick(mc, level, c, ticks);
        }
        if (!CHARGES.isEmpty()) CHARGES.values().removeIf(LanceFx::stale);
    }

    /** A charge out of range or let go is dropped with its hum, so a player never has two hums. */
    private static boolean stale(Charge c) {
        if (c.seen == stamp) return false;
        endHum(c);
        return true;
    }

    private static void startHum(Minecraft mc, Charge c) {
        c.hum = new HumSound(c.player);
        mc.getSoundManager().play(c.hum);
    }

    private static void endHum(Charge c) {
        if (c.hum != null) c.hum.end();
        c.hum = null;
    }

    private static void tick(Minecraft mc, ClientLevel level, Charge c, int ticks) {
        Player p = c.player;
        int coils = Math.min(COILS, ticks / COIL_TICKS);
        boolean own = ownView(mc, p);
        boolean particles = GearClientConfig.weaponParticles();
        if (particles) muzzle(p, own, MUZ);
        while (c.coils < coils) {
            c.coils++;
            if (c.coils == COILS) {
                level.playLocalSound(p.getX(), p.getEyeY(), p.getZ(), CoreSounds.LANCE_READY.get(), SoundSource.PLAYERS, READY_VOLUME,
                        0.98F + level.random.nextFloat() * 0.04F, false);
                startHum(mc, c);
                if (particles) readyFlash(level, p, own);
            } else if (particles) {
                coilPulse(level, c.coils, own);
            }
        }
        if (!particles) return;
        if (coils < COILS) {
            if (ticks % 2 == 0) gather(level, p, coils, own);
        } else if (ticks % 4 == 0) {
            // ready and held: one soft mote at the tip
            RandomSource r = level.random;
            level.addParticle(chargeMote(own ? 0 : 1), MUZ[0], MUZ[1], MUZ[2],
                    p.getDeltaMovement().x + r.nextGaussian() * 0.004, p.getDeltaMovement().y * 0.5 + 0.004, p.getDeltaMovement().z + r.nextGaussian() * 0.004);
        }
    }

    /**
     * Muzzle of the lance held by {@code p} into {@code out} (x, y, z) and its look direction into out[3..5]. The own
     * first-person view offsets in camera space (it follows the screen); the held one hangs from the eyes, along the
     * look when shouldered and ahead of the body at rest.
     */
    static void muzzle(Player p, boolean own, double[] out) {
        float yaw = p.getYRot() * Mth.DEG_TO_RAD, pitch = p.getXRot() * Mth.DEG_TO_RAD;
        double cp = Mth.cos(pitch), sp = Mth.sin(pitch), cy = Mth.cos(yaw), sy = Mth.sin(yaw);
        double lx = -sy * cp, ly = -sp, lz = cy * cp;
        // right of the view, then up of the view (right x look)
        double rx = -cy, rz = -sy;
        int side = p.getMainArm() == HumanoidArm.RIGHT ? 1 : -1;
        double x = p.getX(), y = p.getEyeY(), z = p.getZ();
        if (own) {
            float a = p == Minecraft.getInstance().player ? LanceClient.aim(1.0F) : 0.0F;
            double forward = Mth.lerp(a, OWN_MUZZLE_FORWARD, OWN_AIM_FORWARD);
            double across = side * Mth.lerp(a, OWN_MUZZLE_SIDE, OWN_AIM_SIDE), down = Mth.lerp(a, OWN_MUZZLE_DOWN, OWN_AIM_DOWN);
            double ux = sy * ly, uy = -sy * lx + cy * lz, uz = -cy * ly;
            out[0] = x + lx * forward + rx * across - ux * down;
            out[1] = y + ly * forward - uy * down;
            out[2] = z + lz * forward + rz * across - uz * down;
        } else {
            float a = LanceArms.aim(p);
            float body = p.yBodyRot * Mth.DEG_TO_RAD;
            double bc = Mth.cos(body), bs = Mth.sin(body);
            double restX = x - bs * REST_MUZZLE_FORWARD - bc * side * REST_MUZZLE_SIDE;
            double restZ = z + bc * REST_MUZZLE_FORWARD - bs * side * REST_MUZZLE_SIDE;
            out[0] = Mth.lerp(a, restX, x + lx * HELD_MUZZLE_FORWARD + rx * side * HELD_MUZZLE_SIDE);
            out[1] = Mth.lerp(a, y - REST_MUZZLE_DOWN, y + ly * HELD_MUZZLE_FORWARD - HELD_MUZZLE_DOWN);
            out[2] = Mth.lerp(a, restZ, z + lz * HELD_MUZZLE_FORWARD + rz * side * HELD_MUZZLE_SIDE);
        }
        out[3] = lx;
        out[4] = ly;
        out[5] = lz;
    }

    /** Two unit vectors across the direction (dx, dy, dz, unit length) into {@link #ACROSS}. */
    private static void across(double dx, double dy, double dz) {
        // a = d x up (or d x east when d is nearly vertical), b = d x a
        double ax, ay, az;
        if (Math.abs(dy) > 0.95) {
            ax = 0;
            ay = dz;
            az = -dy;
        } else {
            ax = -dz;
            ay = 0;
            az = dx;
        }
        double an = Math.sqrt(ax * ax + ay * ay + az * az);
        ax /= an;
        ay /= an;
        az /= an;
        ACROSS[0] = ax;
        ACROSS[1] = ay;
        ACROSS[2] = az;
        ACROSS[3] = dy * az - dz * ay;
        ACROSS[4] = dz * ax - dx * az;
        ACROSS[5] = dx * ay - dy * ax;
    }

    /** The charge motes, made once: held tip (own, other view), then gathering bright and violet (own, other view). */
    private static TintParticleOptions[] chargeMotes;

    private static TintParticleOptions chargeMote(int i) {
        if (chargeMotes == null) {
            chargeMotes = new TintParticleOptions[]{
                    CoreParticles.mote(VOID_BRIGHT, 0.4F), CoreParticles.mote(VOID_BRIGHT, 0.6F),
                    CoreParticles.mote(VOID_BRIGHT, 0.4F), CoreParticles.mote(VOID_BRIGHT, 0.65F),
                    CoreParticles.mote(CoreParticles.WARP_VIOLET, 0.4F), CoreParticles.mote(CoreParticles.WARP_VIOLET, 0.65F)};
        }
        return chargeMotes[i];
    }

    /** Void motes drawn into the muzzle; more as the coils light. The own view only gets them from the outer side. */
    private static void gather(ClientLevel level, Player p, int coils, boolean own) {
        RandomSource r = level.random;
        double inX = 0, inY = 0, inZ = 0;
        if (own) {
            // from the muzzle toward the crosshair line: motes never come from that side
            inX = p.getX() + MUZ[3] * OWN_MUZZLE_FORWARD - MUZ[0];
            inY = p.getEyeY() + MUZ[4] * OWN_MUZZLE_FORWARD - MUZ[1];
            inZ = p.getZ() + MUZ[5] * OWN_MUZZLE_FORWARD - MUZ[2];
            double n = Math.sqrt(inX * inX + inY * inY + inZ * inZ);
            inX /= n;
            inY /= n;
            inZ /= n;
        }
        int count = own ? 1 : 1 + coils / 2;
        int motes = coils >= 2 ? 2 : 4;   // bright once two coils are lit, else violet
        double vx = p.getDeltaMovement().x, vz = p.getDeltaMovement().z;
        for (int i = 0; i < count; i++) {
            double ox = r.nextGaussian(), oy = r.nextGaussian(), oz = r.nextGaussian();
            double n = Math.max(1.0E-4, Math.sqrt(ox * ox + oy * oy + oz * oz));
            ox /= n;
            oy /= n;
            oz /= n;
            if (own) {
                double dot = ox * inX + oy * inY + oz * inZ;
                if (dot > 0) {
                    ox -= 2 * dot * inX;
                    oy -= 2 * dot * inY;
                    oz -= 2 * dot * inZ;
                }
            }
            double radius = own ? 0.22 + r.nextDouble() * 0.12 : 0.45 + r.nextDouble() * 0.3;
            ox *= radius;
            oy *= radius;
            oz *= radius;
            // a mote's speed decays by 0.93 a tick, so 1/14 of the offset carries it into the muzzle
            level.addParticle(chargeMote(motes + (own ? 0 : 1)), MUZ[0] + ox, MUZ[1] + oy, MUZ[2] + oz,
                    vx - ox / 14.0, -oy / 14.0, vz - oz / 14.0);
        }
    }

    /** A coil lit: a small violet pulse at the muzzle, a little stronger for each coil. */
    private static void coilPulse(ClientLevel level, int coil, boolean own) {
        float scale = own ? 0.1F + 0.03F * coil : 0.25F + 0.08F * coil;
        level.addParticle(CoreParticles.flash(CoreParticles.WARP_VIOLET, scale), MUZ[0], MUZ[1], MUZ[2], 0, 0, 0);
        if (!own) sparks(level, CoreParticles.spark(VOID_BRIGHT, 0.6F), MUZ[0], MUZ[1], MUZ[2], 2, 0.05);
    }

    /** Full charge: a white-violet flash and a small shock ring at the muzzle. */
    private static void readyFlash(ClientLevel level, Player p, boolean own) {
        if (own) {
            level.addParticle(CoreParticles.flash(VOID_CORE, 0.3F), MUZ[0], MUZ[1], MUZ[2], 0, 0, 0);
            sparks(level, CoreParticles.spark(VOID_BRIGHT, 0.6F), MUZ[0], MUZ[1], MUZ[2], 3, 0.04);
            return;
        }
        level.addParticle(CoreParticles.flash(VOID_CORE, 0.8F), MUZ[0], MUZ[1], MUZ[2], 0, 0, 0);
        level.addParticle(CoreParticles.flash(VOID_DEEP, 1.2F), MUZ[0], MUZ[1], MUZ[2], 0, 0, 0);
        across(MUZ[3], MUZ[4], MUZ[5]);
        ring(level, CoreParticles.mote(VOID_BRIGHT, 0.6F), MUZ[0], MUZ[1], MUZ[2], 12, 0.05, 0.05);
        sparks(level, CoreParticles.spark(VOID_BRIGHT, 0.8F), MUZ[0], MUZ[1], MUZ[2], 8, 0.1);
    }

    /** Motes on a circle across {@link #ACROSS} that fly outward at {@code speed}. */
    private static void ring(ClientLevel level, TintParticleOptions mote, double x, double y, double z, int count, double radius, double speed) {
        double phase = level.random.nextDouble() * Math.PI * 2;
        for (int i = 0; i < count; i++) {
            double a = phase + i * Math.PI * 2 / count, c = Math.cos(a), s = Math.sin(a);
            double ox = ACROSS[0] * c + ACROSS[3] * s, oy = ACROSS[1] * c + ACROSS[4] * s, oz = ACROSS[2] * c + ACROSS[5] * s;
            level.addParticle(mote, x + ox * radius, y + oy * radius, z + oz * radius, ox * speed, oy * speed, oz * speed);
        }
    }

    /** Sparks flying out and up from a point; they fall and bounce. */
    private static void sparks(ClientLevel level, TintParticleOptions spark, double x, double y, double z, int count, double speed) {
        RandomSource r = level.random;
        for (int i = 0; i < count; i++) {
            level.addParticle(spark, x, y, z, r.nextGaussian() * speed, 0.05 + r.nextDouble() * speed, r.nextGaussian() * speed);
        }
    }

    /** Motes on a sphere around a point, all pulled into it, with a flash and dark smoke: the void collapsing. */
    private static void implode(ClientLevel level, double x, double y, double z, double radius, int count, float flash, int smoke) {
        RandomSource r = level.random;
        level.addParticle(CoreParticles.flash(VOID_CORE, flash), x, y, z, 0, 0, 0);
        level.addParticle(CoreParticles.flash(VOID_DEEP, flash * 1.6F), x, y, z, 0, 0, 0);
        TintParticleOptions bright = CoreParticles.mote(VOID_BRIGHT, 0.9F), deep = CoreParticles.mote(VOID_DEEP, 1.1F);
        for (int i = 0; i < count; i++) {
            double ox = r.nextGaussian(), oy = r.nextGaussian(), oz = r.nextGaussian();
            double n = Math.max(1.0E-4, Math.sqrt(ox * ox + oy * oy + oz * oz)) / (radius * (0.8 + 0.4 * r.nextDouble()));
            ox /= n;
            oy /= n;
            oz /= n;
            level.addParticle((i & 1) == 0 ? bright : deep, x + ox, y + oy, z + oz, -ox / 14.0, -oy / 14.0, -oz / 14.0);
        }
        TintParticleOptions puff = CoreParticles.puff(VOID_SMOKE, 1.4F);
        for (int i = 0; i < smoke; i++) {
            level.addParticle(puff, x + r.nextGaussian() * 0.2, y + r.nextGaussian() * 0.2, z + r.nextGaussian() * 0.2,
                    r.nextGaussian() * 0.02, 0.01, r.nextGaussian() * 0.02);
        }
        sparks(level, CoreParticles.spark(VOID_BRIGHT, 1.0F), x, y, z, count / 2, 0.16);
    }

    /**
     * The beam ({@link com.arno.robotica.gear.GearFxPayload#LANCE}): {@code p} is eye, look, end, then the hit centres.
     * A dense white-violet core with a twisting dark spiral, small rings along it, a shock ring at the muzzle, and the
     * void collapsing at the impact and on every hit mob. The own shot also kicks the camera and flashes the HUD.
     */
    static void beam(ClientLevel level, float[] p, boolean hitBlock, int shooterId) {
        Minecraft mc = Minecraft.getInstance();
        Entity e = shooterId >= 0 ? level.getEntity(shooterId) : null;
        boolean own = false;
        if (e instanceof Player shooter) {
            own = ownView(mc, shooter);
            LanceArms.shot(shooterId, true);
            if (shooter == mc.player) {
                kickStart = Util.getMillis();
                LanceHud.fired();
                LanceClient.beamFired();
            }
            muzzle(shooter, own, MUZ);
        } else {
            MUZ[0] = p[0] + p[3] * HELD_MUZZLE_FORWARD;
            MUZ[1] = p[1] + p[4] * HELD_MUZZLE_FORWARD - HELD_MUZZLE_DOWN;
            MUZ[2] = p[2] + p[5] * HELD_MUZZLE_FORWARD;
        }
        if (!GearClientConfig.weaponParticles()) return;
        RandomSource r = level.random;
        double sx = MUZ[0], sy = MUZ[1], sz = MUZ[2], ex = p[6], ey = p[7], ez = p[8];
        double dx = ex - sx, dy = ey - sy, dz = ez - sz;
        double length = Math.sqrt(dx * dx + dy * dy + dz * dz);
        if (length < 1.0E-3) return;
        dx /= length;
        dy /= length;
        dz /= length;
        length = Math.min(length, MAX_BEAM);
        across(dx, dy, dz);

        // muzzle: a flash and a void shock ring (small in the own view, so it stays off the crosshair)
        level.addParticle(CoreParticles.flash(VOID_CORE, own ? 0.4F : 1.0F), sx, sy, sz, 0, 0, 0);
        if (own) {
            ring(level, CoreParticles.mote(VOID_BRIGHT, 0.4F), sx, sy, sz, 10, 0.04, 0.012);
        } else {
            level.addParticle(CoreParticles.flash(VOID_DEEP, 1.6F), sx, sy, sz, 0, 0, 0);
            ring(level, CoreParticles.mote(VOID_BRIGHT, 0.8F), sx, sy, sz, 18, 0.1, 0.07);
            sparks(level, CoreParticles.spark(VOID_BRIGHT, 0.8F), sx, sy, sz, 6, 0.12);
            TintParticleOptions puff = CoreParticles.puff(VOID_SMOKE, 1.0F);
            for (int i = 0; i < 3; i++) level.addParticle(puff, sx, sy, sz, dx * 0.04 + r.nextGaussian() * 0.02, 0.01, dz * 0.04 + r.nextGaussian() * 0.02);
        }

        TintParticleOptions core = CoreParticles.mote(VOID_CORE, 1.1F);
        TintParticleOptions halo = CoreParticles.mote(CoreParticles.WARP_VIOLET, 1.8F);
        TintParticleOptions spiral = CoreParticles.mote(VOID_DEEP, 0.75F);
        TintParticleOptions ringMote = CoreParticles.mote(VOID_BRIGHT, 0.6F);
        int steps = (int) (length / CORE_STEP);
        for (int i = 0; i <= steps; i++) {
            double s = i * CORE_STEP;
            double x = sx + dx * s, y = sy + dy * s, z = sz + dz * s;
            level.addParticle(core, x, y, z, 0, 0, 0);
            if (i % 3 == 0) level.addParticle(halo, x, y, z, 0, 0, 0);
            if (own && s < OWN_CLEAR) continue;
            for (int strand = 0; strand < 2; strand++) {
                double a = s * HELIX_TWIST + strand * Math.PI, c = Math.cos(a), sn = Math.sin(a);
                double ox = ACROSS[0] * c + ACROSS[3] * sn, oy = ACROSS[1] * c + ACROSS[4] * sn, oz = ACROSS[2] * c + ACROSS[5] * sn;
                // the tangent (d/da of the offset) twists the spiral on; a little outward drift widens it as it fades
                double tx = -ACROSS[0] * sn + ACROSS[3] * c, ty = -ACROSS[1] * sn + ACROSS[4] * c, tz = -ACROSS[2] * sn + ACROSS[5] * c;
                level.addParticle(spiral, x + ox * HELIX_RADIUS, y + oy * HELIX_RADIUS, z + oz * HELIX_RADIUS,
                        tx * HELIX_SPIN + ox * 0.008, ty * HELIX_SPIN + oy * 0.008, tz * HELIX_SPIN + oz * 0.008);
            }
            if (i >= RING_START && (i - RING_START) % RING_EVERY == 0) ring(level, ringMote, x, y, z, 10, 0.12, 0.03);
        }

        int shown = 0;
        for (int i = 9; i + 2 < p.length; i += 3) {
            implode(level, p[i], p[i + 1], p[i + 2], 0.9, 12, 1.3F, 2);
            shown++;
        }
        if (hitBlock) {
            implode(level, ex, ey, ez, 1.3, 28, 2.2F, 6);
            across(dx, dy, dz);
            ring(level, CoreParticles.mote(VOID_BRIGHT, 0.9F), ex - dx * 0.2, ey - dy * 0.2, ez - dz * 0.2, 20, 0.1, 0.09);
        }
    }

    /** A null bolt left the muzzle ({@link com.arno.robotica.gear.GearFxPayload#BOLT}): {@code p} is eye and look. */
    static void boltMuzzle(ClientLevel level, float[] p, int shooterId) {
        Minecraft mc = Minecraft.getInstance();
        Entity e = shooterId >= 0 ? level.getEntity(shooterId) : null;
        boolean own = false;
        if (e instanceof Player shooter) {
            own = ownView(mc, shooter);
            LanceArms.shot(shooterId, false);
            muzzle(shooter, own, MUZ);
        } else {
            MUZ[0] = p[0] + p[3] * HELD_MUZZLE_FORWARD;
            MUZ[1] = p[1] + p[4] * HELD_MUZZLE_FORWARD - HELD_MUZZLE_DOWN;
            MUZ[2] = p[2] + p[5] * HELD_MUZZLE_FORWARD;
            MUZ[3] = p[3];
            MUZ[4] = p[4];
            MUZ[5] = p[5];
        }
        if (!GearClientConfig.weaponParticles()) return;
        TintParticleOptions spark = CoreParticles.spark(VOID_BRIGHT, 0.8F);
        if (own) {
            level.addParticle(CoreParticles.flash(CoreParticles.WARP_VIOLET, 0.2F), MUZ[0], MUZ[1], MUZ[2], 0, 0, 0);
            sparks(level, spark, MUZ[0], MUZ[1], MUZ[2], 3, 0.06);
            return;
        }
        double len = Math.sqrt(MUZ[3] * MUZ[3] + MUZ[4] * MUZ[4] + MUZ[5] * MUZ[5]);
        if (len < 1.0E-3) return;
        level.addParticle(CoreParticles.flash(VOID_CORE, 0.45F), MUZ[0], MUZ[1], MUZ[2], 0, 0, 0);
        level.addParticle(CoreParticles.flash(VOID_DEEP, 0.75F), MUZ[0], MUZ[1], MUZ[2], 0, 0, 0);
        across(MUZ[3] / len, MUZ[4] / len, MUZ[5] / len);
        ring(level, CoreParticles.mote(VOID_BRIGHT, 0.55F), MUZ[0], MUZ[1], MUZ[2], 8, 0.05, 0.045);
        sparks(level, spark, MUZ[0], MUZ[1], MUZ[2], 4, 0.1);
        RandomSource r = level.random;
        TintParticleOptions puff = CoreParticles.puff(VOID_SMOKE, 0.7F);
        for (int i = 0; i < 2; i++) level.addParticle(puff, MUZ[0], MUZ[1], MUZ[2], r.nextGaussian() * 0.015, 0.01, r.nextGaussian() * 0.015);
    }

    /** A short upward kick and a small roll right after the own beam shot. */
    static void onCameraAngles(ViewportEvent.ComputeCameraAngles event) {
        if (kickStart == 0) return;
        float t = (Util.getMillis() - kickStart) / (float) KICK_MS;
        if (t < 0.0F || t >= 1.0F) {
            kickStart = 0;
            return;
        }
        Minecraft mc = Minecraft.getInstance();
        float scale = mc.options.screenEffectScale().get().floatValue();
        if (scale <= 0.0F || mc.options.getCameraType() != CameraType.FIRST_PERSON) return;
        // 30 ms rise, then ease out
        float k = t < 0.15F ? t / 0.15F : (1.0F - t) * (1.0F - t) / 0.7225F;
        event.setPitch(event.getPitch() - KICK_PITCH * k * scale);
        event.setRoll(event.getRoll() + KICK_ROLL * k * scale * Mth.sin(t * Mth.PI * 3.0F));
    }

    /** A sound at the player's chest that follows them. */
    private abstract static class FollowSound extends AbstractTickableSoundInstance {
        final Player player;

        FollowSound(SoundEvent sound, Player player, float volume) {
            super(sound, SoundSource.PLAYERS, SoundInstance.createUnseededRandom());
            this.player = player;
            this.volume = volume;
            this.pitch = 1.0F;
            follow();
        }

        final void follow() {
            x = player.getX();
            y = player.getEyeY() - 0.2;
            z = player.getZ();
        }
    }

    /** The charge whine; fades out fast when the charge is let go. */
    private static final class ChargeSound extends FollowSound {
        private int fade = -1;

        ChargeSound(Player player) {
            super(CoreSounds.LANCE_CHARGE.get(), player, CHARGE_VOLUME);
        }

        @Override
        public void tick() {
            if (fade < 0 && !charging(player)) fade = 2;
            if (fade >= 0) {
                if (fade == 0 || player.isRemoved()) {
                    stop();
                    return;
                }
                volume = CHARGE_VOLUME * fade / 3.0F;
                fade--;
            }
            follow();
        }
    }

    /** The quiet ready hum, looping while the charged lance is held. */
    private static final class HumSound extends FollowSound {
        private int fadeIn;

        HumSound(Player player) {
            super(CoreSounds.LANCE_HUM.get(), player, 0.01F);
            this.looping = true;
            this.delay = 0;
        }

        @Override
        public boolean canStartSilent() {
            return true;
        }

        void end() {
            stop();
        }

        @Override
        public void tick() {
            if (!charging(player) || player.getTicksUsingItem() < NullLanceItem.CHARGE_TICKS
                    || player.level() != Minecraft.getInstance().level) {
                stop();
                return;
            }
            fadeIn = Math.min(8, fadeIn + 1);
            volume = HUM_VOLUME * fadeIn / 8.0F;
            follow();
        }
    }
}
