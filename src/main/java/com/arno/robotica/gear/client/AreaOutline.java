package com.arno.robotica.gear.client;

import com.arno.robotica.gear.tool.AreaBreaker;
import com.arno.robotica.gear.tool.AreaMode;
import com.arno.robotica.gear.tool.GearToolItem;
import com.arno.robotica.gear.tool.ToolSettings;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import net.minecraft.client.Minecraft;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.client.renderer.LevelRenderer;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.client.event.RenderHighlightEvent;

import java.util.List;

/** Draws a box around every extra block the held tool would break, using the same math as the server. */
final class AreaOutline {
    private AreaOutline() {}

    private static final int MAX_BOXES = 600;
    private static final int RECOMPUTE_TICKS = 10;

    private static BlockPos cachedOrigin;
    private static Direction cachedFace;
    private static AreaMode cachedMode;
    private static int cachedFlags;
    private static int cachedFeetY;
    private static long cachedTime = Long.MIN_VALUE;
    private static List<BlockPos> cached = List.of();

    static void onHighlight(RenderHighlightEvent.Block event) {
        Minecraft mc = Minecraft.getInstance();
        LocalPlayer player = mc.player;
        if (player == null || mc.level == null) return;
        ItemStack stack = player.getMainHandItem();
        if (!(stack.getItem() instanceof GearToolItem tool)) return;
        AreaMode mode = tool.activeMode(stack, player);
        if (mode == AreaMode.SINGLE) return;

        BlockHitResult hit = event.getTarget();
        BlockPos origin = hit.getBlockPos();
        Direction face = hit.getDirection();
        int flags = ToolSettings.flags(stack);
        int feetY = player.blockPosition().getY();
        long time = mc.level.getGameTime();
        if (!origin.equals(cachedOrigin) || face != cachedFace || mode != cachedMode || flags != cachedFlags || feetY != cachedFeetY
                || time - cachedTime >= RECOMPUTE_TICKS || time < cachedTime) {
            cached = AreaBreaker.collect(mc.level, player, stack, tool, origin, face);
            cachedOrigin = origin.immutable();
            cachedFace = face;
            cachedMode = mode;
            cachedFlags = flags;
            cachedFeetY = feetY;
            cachedTime = time;
        }
        if (cached.isEmpty()) return;

        PoseStack poseStack = event.getPoseStack();
        VertexConsumer lines = event.getMultiBufferSource().getBuffer(RenderType.lines());
        Vec3 cam = event.getCamera().getPosition();
        int n = 0;
        for (BlockPos pos : cached) {
            if (n++ >= MAX_BOXES) break;
            AABB box = new AABB(pos).move(-cam.x, -cam.y, -cam.z).inflate(0.002);
            LevelRenderer.renderLineBox(poseStack, lines, box, 0.35F, 0.9F, 1.0F, 0.85F);
        }
    }
}
