package com.arno.robotica.exo.client;

import com.arno.robotica.Robotica;
import com.arno.robotica.core.CoreSounds;
import com.arno.robotica.core.item.CoreItems;
import com.arno.robotica.core.module.ModuleItems;
import com.arno.robotica.core.module.ModuleKind;
import com.arno.robotica.exo.ExoData;
import com.arno.robotica.exo.ExoSuit;
import com.arno.robotica.exo.net.ExoFxPayload;
import com.mojang.blaze3d.vertex.DefaultVertexFormat;
import com.mojang.blaze3d.vertex.VertexConsumer;
import com.mojang.blaze3d.vertex.VertexFormat;
import it.unimi.dsi.fastutil.ints.Int2ObjectOpenHashMap;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.client.renderer.LightTexture;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderStateShard;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.client.resources.sounds.AbstractTickableSoundInstance;
import net.minecraft.client.resources.sounds.SimpleSoundInstance;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.Mth;
import net.minecraft.util.RandomSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.client.event.ClientTickEvent;
import net.neoforged.neoforge.client.event.RenderLevelStageEvent;

/**
 * Client side Exo-Frame effects, for the local player and every suit nearby:
 * <ul>
 *   <li>Flight: a thruster loop (fades with distance, at most {@link #MAX_LOOPS}) and a light flame trail for each
 *   flying suit, from the flying state the server sends ({@link ExoFxPayload});</li>
 *   <li>one-shot moments from the server: landing and Spring Heels cloud rings, the Dash trail along the path over a
 *   few ticks, the Med Injector heart ring, the Kinetic Shield flash (full) or sparks (partial);</li>
 *   <li>soft glowing rings (Spring, Med Injector, full Kinetic Shield) and the Sonar Pulse front, all additive and
 *   allocation free;</li>
 *   <li>a quiet beep when an ability's cooldown runs out (local player only).</li>
 * </ul>
 * Decorative parts follow {@link ExoClientConfig#particles()}; vanilla's particle setting thins particles further.
 */
final class ExoFx {
    private ExoFx() {}

    private static final double FX_RANGE = 48, LOOP_RANGE = 24;
    private static final int MAX_LOOPS = 6, MAX_FLYERS = 64;

    // ---------------------------------------------------------------- state

    private static ClientLevel lastLevel;
    private static Player lastPlayer;
    /** Entity ids of other players whose suit flies them. */
    private static final int[] FLYERS = new int[MAX_FLYERS];
    private static int flyerCount;
    private static final Int2ObjectOpenHashMap<FlightLoop> LOOPS = new Int2ObjectOpenHashMap<>();

    /** Dash trails: entity id, ticks left, last position. */
    private static final int TRAILS = 8;
    private static final int[] trailId = new int[TRAILS], trailLeft = new int[TRAILS];
    private static final double[] trailX = new double[TRAILS], trailY = new double[TRAILS], trailZ = new double[TRAILS];

    /** Glow rings around an entity: id, start tick, length, radius from/to, height above the feet, colour, alpha. */
    private static final int RINGS = 16;
    private static final int[] ringId = new int[RINGS], ringLen = new int[RINGS], ringColor = new int[RINGS];
    private static final long[] ringStart = new long[RINGS];
    private static final float[] ringFrom = new float[RINGS], ringTo = new float[RINGS], ringDy = new float[RINGS], ringAlpha = new float[RINGS];
    private static int ringNext;

    /** Cooldown edges: Dash, Sonar, Med Injector, Overclock. */
    private static final boolean[] cooling = new boolean[4];
    private static final long[] readyAt = {Long.MIN_VALUE, Long.MIN_VALUE, Long.MIN_VALUE, Long.MIN_VALUE};
    private static long lastBeep = Long.MIN_VALUE, lastDeny = Long.MIN_VALUE;

    // ---------------------------------------------------------------- tick

    static void onClientTick(ClientTickEvent.Post event) {
        Minecraft mc = Minecraft.getInstance();
        LocalPlayer player = mc.player;
        ClientLevel level = mc.level;
        if (player == null || level == null) {
            reset();
            lastLevel = null;
            return;
        }
        if (level != lastLevel) {
            reset();
            lastLevel = level;
        }
        if (player != lastPlayer) {
            lastPlayer = player;
            java.util.Arrays.fill(cooling, false);
        }
        boolean particles = ExoClientConfig.particles();
        for (int i = 0; i < ExoFxPayload.queued(); i++) handle(level, player, ExoFxPayload.entity(i), ExoFxPayload.kind(i), particles);
        ExoFxPayload.clearQueue();

        long tick = level.getGameTime();
        boolean sound = ExoClientConfig.flightSound();
        if (localFlying(player)) flyer(level, player, player, tick, particles, sound);
        for (int i = flyerCount - 1; i >= 0; i--) {
            Entity e = level.getEntity(FLYERS[i]);
            if (!(e instanceof Player p) || e.isRemoved()) {
                removeFlyer(FLYERS[i]);
                continue;
            }
            if (p != player) flyer(level, player, p, tick, particles, sound);
        }
        if (particles) trails(level);
        cooldowns(player, tick);
    }

    private static void reset() {
        flyerCount = 0;
        LOOPS.clear();
        java.util.Arrays.fill(trailLeft, 0);
        java.util.Arrays.fill(ringLen, 0);
        java.util.Arrays.fill(cooling, false);
        ExoFxPayload.clearQueue();
    }

    /** The local player flies on the suit (read straight from the abilities, no round trip). */
    static boolean localFlying(LocalPlayer player) {
        return player.getAbilities().flying && !player.isCreative() && !player.isSpectator() && ExoSuit.isActive(player, ModuleKind.FLIGHT);
    }

    private static boolean isFlying(Player p) {
        if (p instanceof LocalPlayer local && local == Minecraft.getInstance().player) return localFlying(local);
        for (int i = 0; i < flyerCount; i++) if (FLYERS[i] == p.getId()) return true;
        return false;
    }

    private static void removeFlyer(int id) {
        LOOPS.remove(id);
        for (int i = 0; i < flyerCount; i++) {
            if (FLYERS[i] == id) {
                FLYERS[i] = FLYERS[--flyerCount];
                return;
            }
        }
    }

    private static void handle(ClientLevel level, LocalPlayer self, int id, int kind, boolean particles) {
        if (kind == ExoFxPayload.FLIGHT_ON) {
            if (id != self.getId() && !contains(id) && flyerCount < MAX_FLYERS) FLYERS[flyerCount++] = id;
            return;
        }
        if (kind == ExoFxPayload.FLIGHT_OFF || kind == ExoFxPayload.LANDED) removeFlyer(id);
        if (!particles) return;
        Entity e = level.getEntity(id);
        if (e == null || e.distanceToSqr(Minecraft.getInstance().gameRenderer.getMainCamera().getPosition()) > FX_RANGE * FX_RANGE) return;
        RandomSource r = level.random;
        double x = e.getX(), y = e.getY(), z = e.getZ();
        switch (kind) {
            case ExoFxPayload.LANDED -> cloudRing(level, x, y + 0.05, z, 12, 0.35, 0.09);
            case ExoFxPayload.SPRING -> {
                cloudRing(level, x, y + 0.05, z, 8, 0.25, 0.06);
                ring(id, level.getGameTime(), 7, 0.15F, 0.85F, 0.06F, 0x8CE6FF, 0.7F);
            }
            case ExoFxPayload.DASH -> {
                startTrail(e);
                for (int i = 0; i < 6; i++) {
                    level.addParticle(ParticleTypes.POOF, x + (r.nextDouble() - 0.5) * 0.5, y + 0.2 + r.nextDouble() * 0.9,
                            z + (r.nextDouble() - 0.5) * 0.5, 0, 0.01, 0);
                }
            }
            case ExoFxPayload.MED -> {
                float off = r.nextFloat() * Mth.TWO_PI;
                for (int i = 0; i < 6; i++) {
                    float a = off + i * Mth.TWO_PI / 6;
                    level.addParticle(ParticleTypes.HEART, x + Mth.cos(a) * 0.95, y + 1.1 + r.nextDouble() * 0.3, z + Mth.sin(a) * 0.95, 0, 0, 0);
                }
                for (int i = 0; i < 10; i++) {
                    level.addParticle(ParticleTypes.HAPPY_VILLAGER, x + (r.nextDouble() - 0.5) * 0.9, y + 0.2 + r.nextDouble() * 1.6,
                            z + (r.nextDouble() - 0.5) * 0.9, 0, 0.02, 0);
                }
                ring(id, level.getGameTime(), 12, 0.3F, 1.15F, 0.1F, 0xFF7A9A, 0.9F);
                ring(id, level.getGameTime(), 16, 0.2F, 0.9F, 1.0F, 0x9CFFB0, 0.45F);
            }
            case ExoFxPayload.SHIELD_FULL -> {
                ring(id, level.getGameTime(), 7, 0.45F, 1.35F, 1.0F, 0xA8F0FF, 0.9F);
                for (int i = 0; i < 10; i++) {
                    float a = i * Mth.TWO_PI / 10;
                    level.addParticle(ParticleTypes.ELECTRIC_SPARK, x + Mth.cos(a) * 0.6, y + 1.0, z + Mth.sin(a) * 0.6,
                            Mth.cos(a) * 0.12, 0.02, Mth.sin(a) * 0.12);
                }
            }
            case ExoFxPayload.SHIELD_PARTIAL -> {
                for (int i = 0; i < 4; i++) {
                    level.addParticle(ParticleTypes.ELECTRIC_SPARK, x + (r.nextDouble() - 0.5) * 0.8, y + 0.7 + r.nextDouble() * 0.7,
                            z + (r.nextDouble() - 0.5) * 0.8, (r.nextDouble() - 0.5) * 0.1, 0.03, (r.nextDouble() - 0.5) * 0.1);
                }
            }
            default -> {}
        }
    }

    private static boolean contains(int id) {
        for (int i = 0; i < flyerCount; i++) if (FLYERS[i] == id) return true;
        return false;
    }

    /** Puffs running out flat from the feet. */
    private static void cloudRing(ClientLevel level, double x, double y, double z, int count, double start, double speed) {
        float off = level.random.nextFloat() * Mth.TWO_PI;
        for (int i = 0; i < count; i++) {
            float a = off + i * Mth.TWO_PI / count;
            float c = Mth.cos(a), s = Mth.sin(a);
            level.addParticle(ParticleTypes.CLOUD, x + c * start, y, z + s * start, c * speed, 0.005, s * speed);
        }
    }

    /** Thruster loop and trail of one flying suit. */
    private static void flyer(ClientLevel level, LocalPlayer self, Player p, long tick, boolean particles, boolean sound) {
        double d2 = p.distanceToSqr(self);
        if (sound && d2 <= LOOP_RANGE * LOOP_RANGE) {
            FlightLoop loop = LOOPS.get(p.getId());
            if ((loop == null || loop.isStopped()) && activeLoops() < MAX_LOOPS) {
                loop = new FlightLoop(p);
                LOOPS.put(p.getId(), loop);
                Minecraft.getInstance().getSoundManager().play(loop);
            }
        }
        if (!particles || d2 > FX_RANGE * FX_RANGE) return;
        long t = tick + p.getId();
        RandomSource r = level.random;
        if ((t & 1) == 0) {
            level.addParticle(ParticleTypes.SMALL_FLAME, p.getX() + (r.nextDouble() - 0.5) * 0.24, p.getY() - 0.05,
                    p.getZ() + (r.nextDouble() - 0.5) * 0.24, 0, -0.02, 0);
        }
        if ((t & 3) == 0) {
            level.addParticle(ParticleTypes.SMOKE, p.getX() + (r.nextDouble() - 0.5) * 0.2, p.getY() - 0.2,
                    p.getZ() + (r.nextDouble() - 0.5) * 0.2, 0, -0.01, 0);
        }
    }

    private static int activeLoops() {
        int n = 0;
        for (FlightLoop loop : LOOPS.values()) if (!loop.isStopped()) n++;
        return n;
    }

    // ---------------------------------------------------------------- dash trail

    private static void startTrail(Entity e) {
        int slot = 0;
        for (int i = 0; i < TRAILS; i++) {
            if (trailLeft[i] <= 0 || trailId[i] == e.getId()) {
                slot = i;
                break;
            }
        }
        trailId[slot] = e.getId();
        trailLeft[slot] = 4;
        trailX[slot] = e.getX();
        trailY[slot] = e.getY();
        trailZ[slot] = e.getZ();
    }

    /** Puffs and streaks along the path the dash covered since the last tick. */
    private static void trails(ClientLevel level) {
        RandomSource r = level.random;
        for (int i = 0; i < TRAILS; i++) {
            if (trailLeft[i] <= 0) continue;
            trailLeft[i]--;
            Entity e = level.getEntity(trailId[i]);
            if (e == null) {
                trailLeft[i] = 0;
                continue;
            }
            double x0 = trailX[i], y0 = trailY[i], z0 = trailZ[i], x1 = e.getX(), y1 = e.getY(), z1 = e.getZ();
            double dx = x1 - x0, dy = y1 - y0, dz = z1 - z0, len = Math.sqrt(dx * dx + dy * dy + dz * dz);
            if (len > 0.05) {
                double bx = -dx / len * 0.06, bz = -dz / len * 0.06;
                for (int k = 0; k < 3; k++) {
                    double t = (k + r.nextDouble()) / 3;
                    level.addParticle(ParticleTypes.CLOUD, x0 + dx * t, y0 + dy * t + 0.25 + r.nextDouble() * 1.1, z0 + dz * t, bx, 0.005, bz);
                }
                level.addParticle(ParticleTypes.POOF, x0 + dx * 0.5, y0 + dy * 0.5 + 0.15, z0 + dz * 0.5, bx * 0.5, 0, bz * 0.5);
            }
            trailX[i] = x1;
            trailY[i] = y1;
            trailZ[i] = z1;
        }
    }

    // ---------------------------------------------------------------- cooldowns

    private static boolean onCooldown(LocalPlayer player, Item item) {
        return player.getCooldowns().isOnCooldown(item);
    }

    /** A quiet beep (and a HUD flash) when a cooldown of a worn ability runs out. */
    private static void cooldowns(LocalPlayer player, long tick) {
        if (!ExoSuit.wearingAny(player)) {
            java.util.Arrays.fill(cooling, false);
            return;
        }
        ExoSuit.Active act = ExoSuit.active(player);
        boolean beep = false;
        for (int k = 0; k < 4; k++) {
            boolean has, cd;
            if (k == 3) {
                has = ExoSuit.setBonus(player) == ExoData.Core.SERVO;
                cd = onCooldown(player, CoreItems.SERVO_CORE.get());
            } else {
                ModuleKind kind = k == 0 ? ModuleKind.DASH_THRUSTERS : k == 1 ? ModuleKind.SONAR_PULSE : ModuleKind.MED_INJECTOR;
                has = act.has(kind);
                cd = onCooldown(player, ModuleItems.get(kind, 1).get());
            }
            if (cooling[k] && !cd && has) {
                readyAt[k] = tick;
                beep = true;
            }
            cooling[k] = cd;
        }
        if (beep && ExoClientConfig.cooldownSounds() && tick - lastBeep >= 4) {
            lastBeep = tick;
            Minecraft.getInstance().getSoundManager().play(SimpleSoundInstance.forUI(CoreSounds.EXO_READY.get(), 1.0F, 1.0F));
        }
    }

    /** 1 just after the cooldown of {@code item} ran out, fading to 0 over 10 ticks (HUD flash). */
    static float readyFlash(Item item, float partial) {
        int k;
        if (item == CoreItems.SERVO_CORE.get()) k = 3;
        else if (item instanceof com.arno.robotica.core.module.ModuleItem m) {
            k = m.kind == ModuleKind.DASH_THRUSTERS ? 0 : m.kind == ModuleKind.SONAR_PULSE ? 1 : m.kind == ModuleKind.MED_INJECTOR ? 2 : -1;
        } else k = -1;
        ClientLevel level = Minecraft.getInstance().level;
        if (k < 0 || level == null || readyAt[k] == Long.MIN_VALUE) return 0;
        float age = (level.getGameTime() - readyAt[k]) + partial;
        return age < 0 || age >= 10 ? 0 : 1 - age / 10;
    }

    /** Dash pressed while it cools down: a soft local click, at most every few ticks; nothing is sent. */
    static void denied() {
        ClientLevel level = Minecraft.getInstance().level;
        if (level == null) return;
        long tick = level.getGameTime();
        if (tick - lastDeny < 6 && tick >= lastDeny) return;
        lastDeny = tick;
        Minecraft.getInstance().getSoundManager().play(SimpleSoundInstance.forUI(CoreSounds.EXO_DENIED.get(), 1.0F, 1.0F));
    }

    // ---------------------------------------------------------------- glow rings

    private static void ring(int id, long start, int len, float from, float to, float dy, int rgb, float alpha) {
        int i = ringNext;
        ringNext = (ringNext + 1) % RINGS;
        ringId[i] = id;
        ringStart[i] = start;
        ringLen[i] = len;
        ringFrom[i] = from;
        ringTo[i] = to;
        ringDy[i] = dy;
        ringColor[i] = rgb;
        ringAlpha[i] = alpha;
    }

    private static final ResourceLocation TEXTURE = Robotica.id("textures/misc/area_glow.png");
    private static final float T = 1 / 32.0F;
    /** Soft profile across v: a bright thread in a faint glow. */
    private static final float V0 = 0.5F * T, V1 = 15.5F * T, U0 = 8 * T, U1 = 24 * T;

    private static RenderType type(String name, boolean depth) {
        RenderType.CompositeState.CompositeStateBuilder b = RenderType.CompositeState.builder()
                .setShaderState(RenderStateShard.RENDERTYPE_EYES_SHADER)
                .setTextureState(new RenderStateShard.TextureStateShard(TEXTURE, true, false))
                .setTransparencyState(RenderStateShard.LIGHTNING_TRANSPARENCY)
                .setWriteMaskState(RenderStateShard.COLOR_WRITE)
                .setCullState(RenderStateShard.NO_CULL)
                .setOutputState(RenderStateShard.PARTICLES_TARGET);
        if (!depth) b.setDepthTestState(RenderStateShard.NO_DEPTH_TEST);
        return RenderType.create(name, DefaultVertexFormat.NEW_ENTITY, VertexFormat.Mode.QUADS, 4096, false, false, b.createCompositeState(false));
    }

    private static final RenderType GLOW = type("robotica_exo_glow", true);
    /** The sonar front shows through walls, like the outlines it brings. */
    private static final RenderType GLOW_XRAY = type("robotica_exo_glow_xray", false);

    private static double camX, camY, camZ;

    static void onRenderLevel(RenderLevelStageEvent event) {
        if (event.getStage() != RenderLevelStageEvent.Stage.AFTER_PARTICLES || !ExoClientConfig.particles()) return;
        Minecraft mc = Minecraft.getInstance();
        ClientLevel level = mc.level;
        if (level == null || level != lastLevel) return;
        float partial = event.getPartialTick().getGameTimeDeltaPartialTick(false);
        long tick = level.getGameTime();
        boolean sonar = ExoXray.sonarRingVisible();
        boolean any = false;
        for (int i = 0; i < RINGS && !any; i++) any = ringLen[i] > 0 && tick - ringStart[i] < ringLen[i];
        if (!any && !sonar) return;
        Vec3 cam = event.getCamera().getPosition();
        camX = cam.x;
        camY = cam.y;
        camZ = cam.z;
        MultiBufferSource.BufferSource buffers = mc.renderBuffers().bufferSource();
        if (any) {
            VertexConsumer vc = buffers.getBuffer(GLOW);
            for (int i = 0; i < RINGS; i++) {
                if (ringLen[i] <= 0) continue;
                float age = ((tick - ringStart[i]) + partial) / ringLen[i];
                if (age < 0 || age >= 1) continue;
                Entity e = level.getEntity(ringId[i]);
                if (e == null) continue;
                double x = Mth.lerp(partial, e.xo, e.getX()), y = Mth.lerp(partial, e.yo, e.getY()) + ringDy[i], z = Mth.lerp(partial, e.zo, e.getZ());
                float ease = 1 - (1 - age) * (1 - age);
                float r = Mth.lerp(ease, ringFrom[i], ringTo[i]);
                float fade = (1 - age) * Math.min(1, age * 6);
                int c = ringColor[i];
                ring(vc, x, y, z, r, 0.22F, false, c, ringAlpha[i] * 0.45F * fade);
                ring(vc, x, y, z, r, 0.06F, false, 0xFFFFFF, ringAlpha[i] * 0.5F * fade);
            }
            buffers.endBatch(GLOW);
        }
        if (sonar) {
            VertexConsumer vc = buffers.getBuffer(GLOW_XRAY);
            float r = ExoXray.sonarRingRadius(partial), a = ExoXray.sonarRingAlpha(partial);
            double x = ExoXray.sonarX(), y = ExoXray.sonarY(), z = ExoXray.sonarZ();
            ring(vc, x, y, z, r, 1.3F, true, 0x3C9CFF, 0.3F * a);
            ring(vc, x, y, z, r, 0.3F, true, 0xB4F4FF, 0.55F * a);
            ring(vc, x, y - 1.0, z, r, 0.25F, false, 0x78DCFF, 0.35F * a);
            buffers.endBatch(GLOW_XRAY);
        }
    }

    /**
     * A circle of radius {@code r} around a world point: flat (a band {@code w} to each side, in the ground plane) or a
     * wall (a band {@code w} up and down). The texture gives the band a bright middle line and soft edges.
     */
    private static void ring(VertexConsumer vc, double cx, double cy, double cz, float r, float w, boolean wall, int rgb, float alpha) {
        int a = (int) (Mth.clamp(alpha, 0, 1) * 255);
        if (a <= 1 || r <= 0) return;
        int red = rgb >> 16 & 0xFF, green = rgb >> 8 & 0xFF, blue = rgb & 0xFF;
        float x = (float) (cx - camX), y = (float) (cy - camY), z = (float) (cz - camZ);
        int n = Mth.clamp((int) (r * 6), 24, 128);
        float inner = wall ? r : Math.max(0, r - w), outer = wall ? r : r + w;
        float lo = wall ? y - w : y, hi = wall ? y + w : y;
        float pc = 1, ps = 0;
        for (int i = 1; i <= n; i++) {
            float ang = i * Mth.TWO_PI / n;
            float c = Mth.cos(ang), s = Mth.sin(ang);
            vertex(vc, x + pc * inner, lo, z + ps * inner, U0, V0, red, green, blue, a);
            vertex(vc, x + pc * outer, hi, z + ps * outer, U0, V1, red, green, blue, a);
            vertex(vc, x + c * outer, hi, z + s * outer, U1, V1, red, green, blue, a);
            vertex(vc, x + c * inner, lo, z + s * inner, U1, V0, red, green, blue, a);
            pc = c;
            ps = s;
        }
    }

    private static void vertex(VertexConsumer vc, float x, float y, float z, float u, float v, int r, int g, int b, int a) {
        vc.addVertex(x, y, z).setColor(r, g, b, a).setUv(u, v).setOverlay(OverlayTexture.NO_OVERLAY)
                .setLight(LightTexture.FULL_BRIGHT).setNormal(0, 1, 0);
    }

    // ---------------------------------------------------------------- thruster loop

    /** A soft jet hum that follows a flying suit and fades out when it stops; louder and higher with speed. */
    private static final class FlightLoop extends AbstractTickableSoundInstance {
        private final Player player;
        private int fade = 0;

        FlightLoop(Player player) {
            super(CoreSounds.EXO_THRUSTER.get(), SoundSource.PLAYERS, RandomSource.create());
            this.player = player;
            this.looping = true;
            this.delay = 0;
            this.volume = 0.01F;
            this.pitch = 1.3F;
            this.x = player.getX();
            this.y = player.getY();
            this.z = player.getZ();
        }

        @Override
        public boolean canStartSilent() {
            return true;
        }

        @Override
        public void tick() {
            boolean on = !player.isRemoved() && player.level() == Minecraft.getInstance().level && isFlying(player) && ExoClientConfig.flightSound();
            fade = on ? Math.min(10, fade + 1) : fade - 1;
            if (fade <= 0 && !on) {
                stop();
                return;
            }
            this.x = player.getX();
            this.y = player.getY();
            this.z = player.getZ();
            double mx = player.getX() - player.xo, my = player.getY() - player.yo, mz = player.getZ() - player.zo;
            double speed = Math.sqrt(mx * mx + my * my + mz * mz);
            this.volume = (0.12F + (float) Math.min(0.35, speed * 0.6)) * fade / 10.0F;
            this.pitch = 1.1F + (float) Math.min(0.5, speed * 0.8);
        }
    }
}
