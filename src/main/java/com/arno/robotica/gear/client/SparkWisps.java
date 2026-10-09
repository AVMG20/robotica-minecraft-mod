package com.arno.robotica.gear.client;

import com.arno.robotica.core.CoreSounds;
import com.arno.robotica.gear.GearBlocks;
import com.arno.robotica.gear.GearClientConfig;
import com.arno.robotica.gear.lamp.SparkLampBlock;
import it.unimi.dsi.fastutil.longs.Long2ObjectOpenHashMap;
import it.unimi.dsi.fastutil.longs.LongArrayList;
import it.unimi.dsi.fastutil.longs.LongOpenHashSet;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.SectionPos;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.Mth;
import net.minecraft.util.RandomSource;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.chunk.ChunkAccess;
import net.minecraft.world.level.chunk.LevelChunk;
import net.minecraft.world.level.chunk.LevelChunkSection;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import net.minecraft.world.phys.shapes.VoxelShape;
import net.neoforged.neoforge.client.event.AddSectionGeometryEvent;
import net.neoforged.neoforge.client.event.ClientTickEvent;
import net.neoforged.neoforge.event.level.ChunkEvent;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Predicate;

/**
 * Client-side life of the Spark Lamp wisps. The lamp block has no model and no block entity: this class keeps a list of
 * the lamps in every rendered chunk section, refreshed whenever the section is (re)meshed ({@link AddSectionGeometryEvent},
 * a palette check skips sections without lamps) and dropped when the chunk unloads. Lamps that appear in a section
 * already seen zap in, lamps that vanish from one pop out.
 * <p>
 * Each client tick the lamps within the effect range (client config) get their events: arcs to a nearby wall, floor or
 * ceiling (or to a player standing close), a spark dripping from the core, a crackle or a faint hum. Every interval is
 * randomised per lamp and the sounds share one budget, so a field of lamps neither syncs up nor spams. Drawing is in
 * {@link SparkWispRenderer}. Render thread only.
 */
public final class SparkWisps {
    private SparkWisps() {}

    /** Ticks a removed lamp keeps drawing its pop-out. */
    static final int GHOST_TICKS = 12;
    /** Ticks of the zap-in after a lamp appears. */
    static final int BIRTH_TICKS = 12;
    /** Gravity of a dripping spark, blocks per tick squared. */
    static final float DRIP_G = 0.006F;
    /** How far an arc reaches for a surface or a player, in blocks. */
    private static final double REACH = 1.7;
    /** A player closer than this (to the chest) makes the wisp react. */
    private static final double REACT_RANGE = 3.2;

    /** One lamp: where it rests plus its animation state. Created when its section is meshed, kept while it stays. */
    static final class Wisp {
        final long pos;
        final double x, y, z;
        final int seed, fx, fy, fz;
        final float phase;
        long birth = Long.MIN_VALUE, death = Long.MIN_VALUE;
        // arc: start tick, length in ticks, end point, whether it hit something, the surface normal there
        long arcStart = Long.MIN_VALUE;
        int arcLen, arcSeed;
        boolean arcHit;
        double ax, ay, az;
        float nx, ny, nz;
        long nextArc, nextDrip, reactSoundAt, dripStart = Long.MIN_VALUE;
        float dripLife;
        // reaction to a player close by: 0..1, last tick's value (for interpolation) and the direction to them
        float react, reactPrev, leanX, leanY, leanZ;
        // set by the renderer every frame
        double rx, ry, rz;
        float glow, scale, distant;
        int lod;

        Wisp(long pos, Direction facing, long now, RandomSource random) {
            this.pos = pos;
            int bx = BlockPos.getX(pos), by = BlockPos.getY(pos), bz = BlockPos.getZ(pos);
            this.x = bx + 0.5 + facing.getStepX() * SparkLampBlock.CORE_OFFSET;
            this.y = by + 0.5 + facing.getStepY() * SparkLampBlock.CORE_OFFSET;
            this.z = bz + 0.5 + facing.getStepZ() * SparkLampBlock.CORE_OFFSET;
            this.fx = facing.getStepX();
            this.fy = facing.getStepY();
            this.fz = facing.getStepZ();
            this.seed = (int) (pos ^ (pos >>> 32)) * 0x9E3779B9;
            this.phase = SparkWispRenderer.hash(seed) * Mth.TWO_PI;
            this.nextArc = now + 20 + random.nextInt(200);
            this.nextDrip = now + 40 + random.nextInt(300);
        }
    }

    /** The lamps of one chunk section and the section's box (for frustum culling). */
    static final class Section {
        final AABB box;
        Wisp[] wisps;

        Section(int sx, int sy, int sz) {
            box = new AABB(sx << 4, sy << 4, sz << 4, (sx << 4) + 16, (sy << 4) + 16, (sz << 4) + 16).inflate(1);
        }

        Wisp find(long pos) {
            for (Wisp w : wisps) if (w.pos == pos) return w;
            return null;
        }
    }

    private static final Long2ObjectOpenHashMap<Section> BY_KEY = new Long2ObjectOpenHashMap<>();
    /** Same sections as {@link #BY_KEY}, for allocation-free iteration every frame. */
    static final List<Section> SECTIONS = new ArrayList<>();
    /** Sections meshed at least once: a lamp found in one of these again is new (zap in), elsewhere it was loaded. */
    private static final LongOpenHashSet SEEN = new LongOpenHashSet();
    /** Lamps just removed, drawing their pop-out. */
    static final List<Wisp> GHOSTS = new ArrayList<>();
    private static final int MAX_GHOSTS = 24;

    private static final Predicate<BlockState> IS_LAMP = s -> s.is(GearBlocks.SPARK_LAMP.get());
    private static final LongArrayList FOUND = new LongArrayList();
    private static final List<Direction> FOUND_FACING = new ArrayList<>();
    private static final BlockPos.MutableBlockPos CURSOR = new BlockPos.MutableBlockPos();

    /** Dev only (showcase frames): arcs come every second or so. */
    public static boolean demo;

    static ClientLevel level;
    private static long soundFreeAt, humAt;

    // ------------------------------------------------------------------ registry

    /** A section is about to be meshed: refresh its lamps (main thread, before the mesh task). */
    static void onSectionGeometry(AddSectionGeometryEvent event) {
        ClientLevel current = Minecraft.getInstance().level;
        if (current == null || event.getLevel() != current) return;
        sync(current);
        BlockPos origin = event.getSectionOrigin();
        int sx = SectionPos.blockToSectionCoord(origin.getX()), sy = SectionPos.blockToSectionCoord(origin.getY()),
                sz = SectionPos.blockToSectionCoord(origin.getZ());
        long key = SectionPos.asLong(sx, sy, sz);
        boolean seen = !SEEN.add(key);
        long now = current.getGameTime();
        Section old = BY_KEY.get(key);

        LevelChunk chunk = current.getChunkSource().getChunkNow(sx, sz);
        int index = current.getSectionIndexFromSectionY(sy);
        LevelChunkSection section = chunk == null || index < 0 || index >= chunk.getSectionsCount() ? null : chunk.getSection(index);
        FOUND.clear();
        FOUND_FACING.clear();
        if (section != null && !section.hasOnlyAir() && section.maybeHas(IS_LAMP)) {
            for (int y = 0; y < 16; y++) for (int z = 0; z < 16; z++) for (int x = 0; x < 16; x++) {
                BlockState state = section.getBlockState(x, y, z);
                if (!state.is(GearBlocks.SPARK_LAMP.get())) continue;
                FOUND.add(BlockPos.asLong((sx << 4) + x, (sy << 4) + y, (sz << 4) + z));
                FOUND_FACING.add(state.getValue(SparkLampBlock.FACING));
            }
        }
        if (FOUND.isEmpty()) {
            if (old != null) {
                BY_KEY.remove(key);
                SECTIONS.remove(old);
                if (seen) for (Wisp w : old.wisps) ghost(w, now);
            }
            return;
        }
        Wisp[] next = new Wisp[FOUND.size()];
        for (int i = 0; i < next.length; i++) {
            long pos = FOUND.getLong(i);
            Wisp w = old == null ? null : old.find(pos);
            if (w == null) {
                w = new Wisp(pos, FOUND_FACING.get(i), now, current.random);
                if (seen) w.birth = now;
            }
            next[i] = w;
        }
        if (old == null) {
            old = new Section(sx, sy, sz);
            BY_KEY.put(key, old);
            SECTIONS.add(old);
        } else if (seen) {
            for (Wisp w : old.wisps) {
                boolean kept = false;
                for (Wisp n : next) kept |= n == w;
                if (!kept) ghost(w, now);
            }
        }
        old.wisps = next;
    }

    private static void ghost(Wisp w, long now) {
        if (GHOSTS.size() >= MAX_GHOSTS) return;
        w.death = now;
        GHOSTS.add(w);
    }

    static void onChunkUnload(ChunkEvent.Unload event) {
        if (!event.getLevel().isClientSide() || event.getLevel() != level) return;
        ChunkAccess chunk = event.getChunk();
        ChunkPos cp = chunk.getPos();
        for (int sy = chunk.getMinSection(); sy < chunk.getMaxSection(); sy++) {
            long key = SectionPos.asLong(cp.x, sy, cp.z);
            SEEN.remove(key);
            Section s = BY_KEY.remove(key);
            if (s != null) SECTIONS.remove(s);
        }
    }

    /** Forgets everything when the client level changes (new world, other dimension). */
    private static void sync(ClientLevel current) {
        if (current == level) return;
        BY_KEY.clear();
        SECTIONS.clear();
        SEEN.clear();
        GHOSTS.clear();
        level = current;
    }

    // ------------------------------------------------------------------ events

    static void onClientTick(ClientTickEvent.Post event) {
        Minecraft mc = Minecraft.getInstance();
        ClientLevel current = mc.level;
        if (current == null) {
            if (level != null) sync(null);
            return;
        }
        sync(current);
        if (mc.isPaused()) return;
        long now = current.getGameTime();
        for (int i = GHOSTS.size() - 1; i >= 0; i--) {
            if (now - GHOSTS.get(i).death > GHOST_TICKS || now < GHOSTS.get(i).death) GHOSTS.remove(i);
        }
        if (SECTIONS.isEmpty()) return;
        boolean particles = GearClientConfig.lampParticles(), sounds = GearClientConfig.lampSounds(), animated = GearClientConfig.lampAnimated();
        Vec3 cam = mc.gameRenderer.getMainCamera().getPosition();
        int range = GearClientConfig.lampFxRange();
        double range2 = (double) range * range, sectionRange2 = (range + 14.0) * (range + 14.0);
        RandomSource random = current.random;
        Wisp hum = null;
        double humD2 = 36;
        for (int s = 0; s < SECTIONS.size(); s++) {
            Section section = SECTIONS.get(s);
            AABB b = section.box;
            if (cam.distanceToSqr((b.minX + b.maxX) * 0.5, (b.minY + b.maxY) * 0.5, (b.minZ + b.maxZ) * 0.5) > sectionRange2) continue;
            for (Wisp w : section.wisps) {
                double d2 = cam.distanceToSqr(w.x, w.y, w.z);
                if (d2 > range2) {
                    w.react = w.reactPrev = 0;
                    continue;
                }
                tick(current, w, now, random, animated, particles, sounds);
                if (d2 < humD2) {
                    humD2 = d2;
                    hum = w;
                }
            }
        }
        if (sounds && hum != null && now >= humAt) {
            humAt = now + 160 + random.nextInt(240);
            play(current, hum, CoreSounds.SPARK_LAMP_HUM.get(), 1.0F, 0.9F + random.nextFloat() * 0.2F);
        }
    }

    private static void tick(ClientLevel level, Wisp w, long now, RandomSource random, boolean animated, boolean particles, boolean sounds) {
        // the nearest player within reach (any player: the wisp reacts to everyone)
        Player near = null;
        double best = REACT_RANGE * REACT_RANGE;
        for (Player p : level.players()) {
            if (p.isSpectator()) continue;
            double d2 = p.distanceToSqr(w.x, w.y - p.getBbHeight() * 0.55, w.z);
            if (d2 < best) {
                best = d2;
                near = p;
            }
        }
        w.reactPrev = w.react;
        float target = 0;
        if (near != null && animated) {
            double d = Math.sqrt(best);
            target = (float) Mth.clamp((REACT_RANGE - d) / 2.0, 0, 1);
            double dx = near.getX() - w.x, dy = near.getY() + near.getBbHeight() * 0.55 - w.y, dz = near.getZ() - w.z;
            double len = Math.max(1.0E-3, Math.sqrt(dx * dx + dy * dy + dz * dz));
            w.leanX = (float) (dx / len);
            w.leanY = (float) (dy / len);
            w.leanZ = (float) (dz / len);
        }
        w.react += (target - w.react) * 0.25F;
        boolean born = w.birth == Long.MIN_VALUE || now - w.birth > BIRTH_TICKS;
        if (target > 0.45F && now >= w.reactSoundAt && born) {
            w.reactSoundAt = now + 60 + random.nextInt(100);
            w.nextArc = Math.min(w.nextArc, now + 1);
        }
        if (!particles) return;
        if (demo) w.nextArc = Math.min(w.nextArc, now + 24);
        if (now >= w.nextArc && born) {
            startArc(level, w, near, now, random);
            if (sounds && now >= soundFreeAt) {
                soundFreeAt = now + 3 + random.nextInt(4);
                play(level, w, CoreSounds.SPARK_LAMP_CRACKLE.get(), near != null ? 1.4F : 1.0F, 0.85F + random.nextFloat() * 0.35F);
            }
        }
        if (now >= w.nextDrip) startDrip(level, w, now, random);
    }

    /** An arc: toward a player close by (most of the time), else onto the nearest surface in a random direction, else a short fizzle in the air. */
    private static void startArc(Level level, Wisp w, Player near, long now, RandomSource random) {
        w.arcStart = now;
        w.arcLen = 3 + random.nextInt(4);
        w.arcSeed = random.nextInt();
        w.nextArc = now + (demo ? 8 + random.nextInt(16) : near != null ? 10 + random.nextInt(30) : 50 + random.nextInt(190));
        if (near != null && random.nextInt(10) < 7) {
            double tx = near.getX() + (random.nextDouble() - 0.5) * 0.4, ty = near.getY() + near.getBbHeight() * (0.35 + random.nextDouble() * 0.35),
                    tz = near.getZ() + (random.nextDouble() - 0.5) * 0.4;
            double dx = tx - w.x, dy = ty - w.y, dz = tz - w.z, len = Math.sqrt(dx * dx + dy * dy + dz * dz);
            double reach = Math.min(len, REACH);
            w.arcHit = len <= REACH;
            setArc(w, dx / len * reach, dy / len * reach, dz / len * reach, -dx / len, -dy / len, -dz / len);
            return;
        }
        for (int attempt = 0; attempt < 4; attempt++) {
            double dx = random.nextGaussian(), dy = random.nextGaussian(), dz = random.nextGaussian();
            if (attempt < 3) {
                // mostly along the surface the lamp hangs on, dipping into it: the arc crawls over the wall or floor
                double along = dx * w.fx + dy * w.fy + dz * w.fz;
                double tl = Math.sqrt(Math.max(1.0E-6, dx * dx + dy * dy + dz * dz - along * along));
                double dip = (0.35 + random.nextDouble() * 0.45) * tl;
                dx = dx - along * w.fx - dip * w.fx;
                dy = dy - along * w.fy - dip * w.fy;
                dz = dz - along * w.fz - dip * w.fz;
            }
            double len = Math.sqrt(dx * dx + dy * dy + dz * dz);
            if (len < 1.0E-3) continue;
            dx /= len;
            dy /= len;
            dz /= len;
            double prev = 0;
            for (double d = 0.2; d <= REACH; d += 0.12) {
                if (!solidAt(level, w.x + dx * d, w.y + dy * d, w.z + dz * d)) {
                    prev = d;
                    continue;
                }
                double lo = prev, hi = d;
                for (int i = 0; i < 5; i++) {
                    double mid = (lo + hi) * 0.5;
                    if (solidAt(level, w.x + dx * mid, w.y + dy * mid, w.z + dz * mid)) hi = mid;
                    else lo = mid;
                }
                // the face crossed: the axis on which the last free point and the hit point lie in different blocks
                double ox = w.x + dx * lo, oy = w.y + dy * lo, oz = w.z + dz * lo, hx = w.x + dx * hi, hy = w.y + dy * hi, hz = w.z + dz * hi;
                float nx = Mth.floor(ox) != Mth.floor(hx) ? Math.signum((float) -dx) : 0;
                float ny = Mth.floor(oy) != Mth.floor(hy) ? Math.signum((float) -dy) : 0;
                float nz = Mth.floor(oz) != Mth.floor(hz) ? Math.signum((float) -dz) : 0;
                if (nx == 0 && ny == 0 && nz == 0) {
                    nx = (float) -dx;
                    ny = (float) -dy;
                    nz = (float) -dz;
                }
                w.arcHit = true;
                setArc(w, dx * hi, dy * hi, dz * hi, nx, ny, nz);
                return;
            }
        }
        double dx = random.nextGaussian(), dy = random.nextGaussian(), dz = random.nextGaussian();
        double len = Math.max(1.0E-3, Math.sqrt(dx * dx + dy * dy + dz * dz)), reach = 0.3 + random.nextDouble() * 0.25;
        w.arcHit = false;
        setArc(w, dx / len * reach, dy / len * reach, dz / len * reach, 0, 1, 0);
    }

    private static void setArc(Wisp w, double dx, double dy, double dz, double nx, double ny, double nz) {
        w.ax = w.x + dx;
        w.ay = w.y + dy;
        w.az = w.z + dz;
        float n = (float) Math.max(1.0E-3, Math.sqrt(nx * nx + ny * ny + nz * nz));
        w.nx = (float) nx / n;
        w.ny = (float) ny / n;
        w.nz = (float) nz / n;
    }

    /** A spark falls from the core until it reaches the floor (at most ~1.5 s). */
    private static void startDrip(Level level, Wisp w, long now, RandomSource random) {
        w.nextDrip = now + 70 + random.nextInt(260);
        double floor = w.y - 3;
        for (double y = w.y - 0.1; y > w.y - 3; y -= 0.1) {
            if (solidAt(level, w.x, y, w.z)) {
                floor = y;
                break;
            }
        }
        double h = w.y - floor, v0 = 0.01;
        w.dripLife = (float) Math.min(30, (v0 + Math.sqrt(v0 * v0 + 2 * DRIP_G * h)) / DRIP_G);
        w.dripStart = now;
    }

    /** True when the point is inside a block's collision box (its bounds, which is close enough for a spark). */
    private static boolean solidAt(Level level, double x, double y, double z) {
        CURSOR.set(Mth.floor(x), Mth.floor(y), Mth.floor(z));
        BlockState state = level.getBlockState(CURSOR);
        if (state.isAir()) return false;
        VoxelShape shape = state.getCollisionShape(level, CURSOR);
        if (shape.isEmpty()) return false;
        double lx = x - CURSOR.getX(), ly = y - CURSOR.getY(), lz = z - CURSOR.getZ();
        return lx >= shape.min(Direction.Axis.X) && lx <= shape.max(Direction.Axis.X)
                && ly >= shape.min(Direction.Axis.Y) && ly <= shape.max(Direction.Axis.Y)
                && lz >= shape.min(Direction.Axis.Z) && lz <= shape.max(Direction.Axis.Z);
    }

    private static void play(ClientLevel level, Wisp w, SoundEvent sound, float volume, float pitch) {
        level.playLocalSound(w.x, w.y, w.z, sound, SoundSource.BLOCKS, volume, pitch, false);
    }
}
