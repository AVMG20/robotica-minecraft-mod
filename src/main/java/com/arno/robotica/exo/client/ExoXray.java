package com.arno.robotica.exo.client;

import com.arno.robotica.exo.ExoConfig;
import com.arno.robotica.exo.ExoModuleKind;
import com.arno.robotica.exo.ExoSuit;
import com.arno.robotica.exo.net.ExoSonarPayload;
import com.mojang.blaze3d.vertex.DefaultVertexFormat;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import com.mojang.blaze3d.vertex.VertexFormat;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.client.renderer.LevelRenderer;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderStateShard;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.core.BlockPos;
import net.minecraft.core.SectionPos;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.monster.Enemy;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.chunk.LevelChunk;
import net.minecraft.world.level.chunk.LevelChunkSection;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.client.event.ClientTickEvent;
import net.neoforged.neoforge.client.event.RenderLevelStageEvent;
import net.neoforged.neoforge.common.Tags;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.List;
import java.util.OptionalDouble;

/**
 * Client-side outlines drawn through walls: the Sonar Pulse (ores from {@code c:ores} and living mobs, after the server
 * charged a ping) and the thermal sight of Night Vision II/III (hostile mobs nearby). Sonar scans only the client's
 * loaded chunk sections, a few sections per tick, skipping sections whose palette holds no ore.
 */
final class ExoXray {
    private ExoXray() {}

    private static final int SECTIONS_PER_TICK = 12;
    private static final int MAX_ORES = 768;

    static final RenderType XRAY_LINES = RenderType.create("robotica_exo_xray", DefaultVertexFormat.POSITION_COLOR_NORMAL,
            VertexFormat.Mode.LINES, 4096, false, false,
            RenderType.CompositeState.builder()
                    .setShaderState(RenderStateShard.RENDERTYPE_LINES_SHADER)
                    .setLineState(new RenderStateShard.LineStateShard(OptionalDouble.of(2.0)))
                    .setLayeringState(RenderStateShard.VIEW_OFFSET_Z_LAYERING)
                    .setTransparencyState(RenderStateShard.TRANSLUCENT_TRANSPARENCY)
                    .setOutputState(RenderStateShard.ITEM_ENTITY_TARGET)
                    .setWriteMaskState(RenderStateShard.COLOR_WRITE)
                    .setCullState(RenderStateShard.NO_CULL)
                    .setDepthTestState(RenderStateShard.NO_DEPTH_TEST)
                    .createCompositeState(false));

    private record Ore(BlockPos pos, int color) {}

    // Sonar state
    private static final List<Ore> ores = new ArrayList<>();
    private static final List<Entity> mobs = new ArrayList<>();
    private static final Deque<SectionPos> queue = new ArrayDeque<>();
    private static BlockPos center = BlockPos.ZERO;
    private static int radius;
    private static long until;
    private static int duration = 1;
    // Thermal sight
    private static final List<Entity> thermal = new ArrayList<>();

    /** Ores found by the running sonar ping (HUD). */
    static int oreCount() {
        return ores.size();
    }

    static int mobCount() {
        return mobs.size();
    }

    /** Ticks the sonar outlines stay, 0 when none. */
    static int sonarTicksLeft() {
        ClientLevel level = Minecraft.getInstance().level;
        return level == null ? 0 : (int) Math.max(0, until - level.getGameTime());
    }

    static void onClientTick(ClientTickEvent.Post event) {
        Minecraft mc = Minecraft.getInstance();
        LocalPlayer player = mc.player;
        ClientLevel level = mc.level;
        if (player == null || level == null) {
            clear();
            return;
        }
        ExoSonarPayload ping = ExoSonarPayload.take();
        if (ping != null) start(player, level, ping);
        if (level.getGameTime() > until) {
            ores.clear();
            mobs.clear();
            queue.clear();
        } else {
            scan(level);
        }
        if (level.getGameTime() % 10 == 0) updateThermal(player, level);
    }

    private static void clear() {
        ores.clear();
        mobs.clear();
        queue.clear();
        thermal.clear();
        until = 0;
    }

    private static void start(LocalPlayer player, ClientLevel level, ExoSonarPayload ping) {
        ores.clear();
        mobs.clear();
        queue.clear();
        center = player.blockPosition();
        radius = ping.radius();
        duration = ping.duration();
        until = level.getGameTime() + duration;
        // Nearest sections first, so the ping spreads outward like a wave.
        SectionPos c = SectionPos.of(center);
        int r = (radius >> 4) + 1;
        List<SectionPos> list = new ArrayList<>();
        for (int sx = -r; sx <= r; sx++) {
            for (int sy = -r; sy <= r; sy++) {
                for (int sz = -r; sz <= r; sz++) list.add(c.offset(sx, sy, sz));
            }
        }
        list.sort((a, b) -> Integer.compare(dist2(a, c), dist2(b, c)));
        queue.addAll(list);
        AABB box = new AABB(center).inflate(radius);
        double r2 = (double) radius * radius;
        for (Entity e : level.getEntities(player, box, e -> e instanceof LivingEntity l && l.isAlive() && !(e instanceof Player))) {
            if (e.distanceToSqr(player) <= r2) mobs.add(e);
        }
    }

    private static int dist2(SectionPos a, SectionPos b) {
        int dx = a.x() - b.x(), dy = a.y() - b.y(), dz = a.z() - b.z();
        return dx * dx + dy * dy + dz * dz;
    }

    private static void scan(ClientLevel level) {
        long r2 = (long) radius * radius;
        for (int n = 0; n < SECTIONS_PER_TICK && !queue.isEmpty() && ores.size() < MAX_ORES; n++) {
            SectionPos sp = queue.poll();
            int index = level.getSectionIndexFromSectionY(sp.y());
            if (index < 0 || index >= level.getSectionsCount() || !level.hasChunk(sp.x(), sp.z())) continue;
            LevelChunk chunk = level.getChunk(sp.x(), sp.z());
            LevelChunkSection section = chunk.getSection(index);
            if (section.hasOnlyAir() || !section.maybeHas(s -> s.is(Tags.Blocks.ORES))) continue;
            for (int y = 0; y < 16; y++) {
                for (int z = 0; z < 16; z++) {
                    for (int x = 0; x < 16; x++) {
                        BlockState state = section.getBlockState(x, y, z);
                        if (!state.is(Tags.Blocks.ORES)) continue;
                        BlockPos pos = new BlockPos(sp.minBlockX() + x, sp.minBlockY() + y, sp.minBlockZ() + z);
                        if (pos.distSqr(center) > r2) continue;
                        ores.add(new Ore(pos, oreColor(state)));
                        if (ores.size() >= MAX_ORES) return;
                    }
                }
            }
        }
    }

    /** A rough colour per ore from its registry name. */
    private static int oreColor(BlockState state) {
        String name = BuiltInRegistries.BLOCK.getKey(state.getBlock()).getPath();
        if (name.contains("diamond")) return 0x5FE3F0;
        if (name.contains("emerald")) return 0x3DDB6A;
        if (name.contains("gold")) return 0xFFD23A;
        if (name.contains("redstone")) return 0xFF3B30;
        if (name.contains("lapis")) return 0x3A62FF;
        if (name.contains("copper")) return 0xE8803A;
        if (name.contains("iron")) return 0xD8B49A;
        if (name.contains("coal")) return 0x505050;
        if (name.contains("debris") || name.contains("netherite")) return 0x9A5A3A;
        if (name.contains("quartz")) return 0xF2EEE6;
        return 0xC77DFF;
    }

    private static void updateThermal(LocalPlayer player, ClientLevel level) {
        thermal.clear();
        if (!ExoClientConfig.outlines() || !ExoSuit.wearingAny(player)) return;
        int nv = ExoSuit.level(player, ExoModuleKind.NIGHT_VISION);
        if (nv < 2 || !ExoSuit.isActive(player, ExoModuleKind.NIGHT_VISION)) return;
        int r = ExoConfig.thermalRadius(nv);
        if (r <= 0) return;
        double r2 = (double) r * r;
        for (Entity e : level.getEntities(player, player.getBoundingBox().inflate(r), e -> e instanceof Enemy && e.isAlive())) {
            if (e.distanceToSqr(player) <= r2 && thermal.size() < 64) thermal.add(e);
        }
    }

    static void onRenderLevel(RenderLevelStageEvent event) {
        if (event.getStage() != RenderLevelStageEvent.Stage.AFTER_PARTICLES) return;
        if (!ExoClientConfig.outlines() || (ores.isEmpty() && mobs.isEmpty() && thermal.isEmpty())) return;
        Minecraft mc = Minecraft.getInstance();
        if (mc.level == null) return;
        float partial = event.getPartialTick().getGameTimeDeltaPartialTick(false);
        Vec3 cam = event.getCamera().getPosition();
        PoseStack pose = new PoseStack();
        pose.translate(-cam.x, -cam.y, -cam.z);
        MultiBufferSource.BufferSource buffers = mc.renderBuffers().bufferSource();
        VertexConsumer lines = buffers.getBuffer(XRAY_LINES);
        long left = until - mc.level.getGameTime();
        float fade = duration <= 0 ? 0 : Math.min(1.0F, Math.max(0.0F, (left - partial) / Math.min(40.0F, duration)));
        if (left > 0) {
            for (Ore ore : ores) {
                BlockPos p = ore.pos();
                box(pose, lines, new AABB(p).deflate(0.02), ore.color(), 0.9F * fade);
            }
            for (Entity e : mobs) {
                if (!e.isAlive()) continue;
                box(pose, lines, interpolated(e, partial), e instanceof Enemy ? 0xFF5040 : 0x80FF80, 0.9F * fade);
            }
        }
        for (Entity e : thermal) {
            if (!e.isAlive()) continue;
            box(pose, lines, interpolated(e, partial), 0xFF8A20, 0.85F);
        }
        buffers.endBatch(XRAY_LINES);
    }

    private static AABB interpolated(Entity e, float partial) {
        double x = e.xOld + (e.getX() - e.xOld) * partial - e.getX();
        double y = e.yOld + (e.getY() - e.yOld) * partial - e.getY();
        double z = e.zOld + (e.getZ() - e.zOld) * partial - e.getZ();
        return e.getBoundingBox().move(x, y, z);
    }

    private static void box(PoseStack pose, VertexConsumer lines, AABB box, int rgb, float alpha) {
        if (alpha <= 0.01F) return;
        LevelRenderer.renderLineBox(pose, lines, box, ((rgb >> 16) & 0xFF) / 255F, ((rgb >> 8) & 0xFF) / 255F, (rgb & 0xFF) / 255F, alpha);
    }
}
