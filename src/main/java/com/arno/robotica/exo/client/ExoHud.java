package com.arno.robotica.exo.client;

import com.arno.robotica.Robotica;
import com.arno.robotica.core.item.CoreItems;
import com.arno.robotica.core.util.Fmt;
import com.arno.robotica.drones.entity.DroneBase;
import com.arno.robotica.exo.ExoData;
import com.arno.robotica.exo.ExoModuleKind;
import com.arno.robotica.exo.ExoSuit;
import com.arno.robotica.exo.item.ExoModuleItem;
import net.minecraft.client.DeltaTracker;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.network.chat.Component;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.neoforged.neoforge.client.event.RegisterGuiLayersEvent;

import java.util.ArrayList;
import java.util.List;

/**
 * Compact HUD while wearing Exo pieces (corner and offset in the client config, or off): suit energy bar, one icon per
 * installed module (dimmed when off, empty or a duplicate), cooldown rings (Dash, Med Injector, Sonar, Overclock),
 * sonar results and the Robot HUD's drone list.
 */
final class ExoHud {
    private ExoHud() {}

    private static final int BAR_W = 72;
    private static final int ICON = 13;

    private static List<DroneBase> drones = List.of();
    private static int lastScan = -100;

    static void register(RegisterGuiLayersEvent event) {
        event.registerAboveAll(Robotica.id("exo_hud"), ExoHud::render);
    }

    private record Icon(ItemStack stack, boolean on, float cooldown, boolean glow) {}

    private static void render(GuiGraphics g, DeltaTracker delta) {
        Minecraft mc = Minecraft.getInstance();
        LocalPlayer player = mc.player;
        if (player == null || mc.options.hideGui || mc.getDebugOverlay().showDebugScreen() || !ExoClientConfig.hud()
                || !ExoSuit.wearingAny(player)) return;
        float partial = delta.getGameTimeDeltaPartialTick(false);

        int stored = ExoSuit.totalEnergy(player);
        int capacity = ExoSuit.totalCapacity(player);
        ExoSuit.Active active = ExoSuit.active(player);
        List<Icon> icons = new ArrayList<>();
        for (int p = 0; p < 4; p++) {
            ItemStack piece = ExoSuit.piece(player, ExoSuit.SLOTS[p]);
            for (int i = 0; i < ExoData.slotCount(piece); i++) {
                if (!(ExoData.module(piece, i).getItem() instanceof ExoModuleItem m)) continue;
                boolean counts = ExoData.isEnabled(piece, i) && ExoData.fitsHere(piece, i) && (m.kind.perPiece() || (active.level(m.kind) == m.level && active.piece(m.kind) == p));
                float cd = player.getCooldowns().getCooldownPercent(m, partial);
                icons.add(new Icon(new ItemStack(m), counts && ExoSuit.energyFor(player, ExoSuit.SLOTS[p]) > 0, cd, false));
            }
        }
        ExoData.Core bonus = ExoSuit.setBonus(player);
        boolean servo = bonus == ExoData.Core.SERVO;
        if (bonus != ExoData.Core.NONE) {
            Item core = switch (bonus) {
                case SERVO -> CoreItems.SERVO_CORE.get();
                case MAGMA -> CoreItems.MAGMA_CORE.get();
                default -> CoreItems.ANTIGRAV_CORE.get();
            };
            float cd = servo ? player.getCooldowns().getCooldownPercent(core, partial) : 0;
            icons.add(new Icon(new ItemStack(core), true, cd, servo && player.hasEffect(MobEffects.DIG_SPEED)));
        }

        boolean robotHud = active.has(ExoModuleKind.ROBOT_HUD) && ExoSuit.isActive(player, ExoModuleKind.ROBOT_HUD);
        if (robotHud) scanDrones(mc);
        int sonarLeft = ExoXray.sonarTicksLeft();
        int droneLines = robotHud ? Math.min(4, (int) drones.stream().filter(DroneBase::isAlive).count()) : 0;

        int perRow = 8;
        int iconRows = (icons.size() + perRow - 1) / perRow;
        int width = Math.max(BAR_W + 40, Math.min(icons.size(), perRow) * ICON);
        int height = 8 + iconRows * ICON + (sonarLeft > 0 ? 9 : 0) + droneLines * 9;
        int sw = g.guiWidth(), sh = g.guiHeight();
        ExoClientConfig.Corner corner = ExoClientConfig.corner();
        boolean right = corner == ExoClientConfig.Corner.TOP_RIGHT || corner == ExoClientConfig.Corner.BOTTOM_RIGHT;
        boolean bottom = corner == ExoClientConfig.Corner.BOTTOM_LEFT || corner == ExoClientConfig.Corner.BOTTOM_RIGHT;
        int x = right ? sw - ExoClientConfig.offsetX() - width : ExoClientConfig.offsetX();
        int y = bottom ? sh - ExoClientConfig.offsetY() - height : ExoClientConfig.offsetY();

        // Energy bar.
        boolean low = capacity > 0 && stored * 100L < capacity * 15L;
        int filled = capacity <= 0 ? 0 : (int) ((long) BAR_W * stored / capacity);
        g.fill(x - 1, y - 1, x + BAR_W + 1, y + 6, 0xAA000000);
        g.fill(x, y, x + BAR_W, y + 5, 0xFF1C2A2E);
        g.fill(x, y, x + filled, y + 5, low ? 0xFFC9302A : 0xFF5CC8D8);
        g.fill(x, y, x + filled, y + 1, low ? 0xFFFF6B5E : 0xFF9BF0F8);
        small(g, mc, Component.literal(Fmt.compact(stored) + " FE"), x + BAR_W + 4, y, low ? 0xFFFF6B5E : 0xFFD8FBFF);

        // Module icons with cooldown rings.
        int iy = y + 8;
        for (int n = 0; n < icons.size(); n++) {
            Icon icon = icons.get(n);
            int ix = x + (n % perRow) * ICON;
            int ry = iy + (n / perRow) * ICON;
            g.pose().pushPose();
            g.pose().translate(ix, ry, 0);
            g.pose().scale(0.75F, 0.75F, 1.0F);
            g.renderItem(icon.stack(), 0, 0);
            g.pose().translate(0, 0, 200);
            if (!icon.on()) g.fill(0, 0, 16, 16, 0xA0101010);
            if (icon.cooldown() > 0) {
                int h = Math.round(16 * icon.cooldown());
                g.fill(0, 16 - h, 16, 16, 0x70000000);
            }
            g.pose().popPose();
            if (icon.cooldown() > 0) ring(g, ix - 1, ry - 1, 14, icon.cooldown(), 0xFFE8A020);
            else if (icon.glow()) ring(g, ix - 1, ry - 1, 14, 1.0F, 0xFF5CE07A);
        }
        int ty = iy + iconRows * ICON;
        if (sonarLeft > 0) {
            small(g, mc, Component.translatable("exo.robotica.hud.sonar", ExoXray.oreCount(), ExoXray.mobCount()), x, ty, 0xFFB5F6FB);
            ty += 9;
        }
        if (robotHud) renderDrones(g, mc, x, ty);
    }

    private static void small(GuiGraphics g, Minecraft mc, Component text, int x, int y, int color) {
        g.pose().pushPose();
        g.pose().translate(x, y - 0.5F, 0);
        g.pose().scale(0.75F, 0.75F, 1.0F);
        g.drawString(mc.font, text, 0, 0, color, true);
        g.pose().popPose();
    }

    /** A square ring around an icon, filled clockwise from the top left for {@code fraction} of its length. */
    private static void ring(GuiGraphics g, int x, int y, int size, float fraction, int color) {
        int side = size - 1;
        int total = Math.round(4 * side * Math.max(0, Math.min(1, fraction)));
        for (int i = 0; i < total; i++) {
            int px, py;
            if (i < side) {
                px = x + i;
                py = y;
            } else if (i < 2 * side) {
                px = x + side;
                py = y + (i - side);
            } else if (i < 3 * side) {
                px = x + side - (i - 2 * side);
                py = y + side;
            } else {
                px = x;
                py = y + side - (i - 3 * side);
            }
            g.fill(px, py, px + 1, py + 1, color);
        }
    }

    private static void scanDrones(Minecraft mc) {
        var player = mc.player;
        if (mc.level == null || player == null) return;
        if (player.tickCount - lastScan >= 10 || player.tickCount < lastScan) {
            lastScan = player.tickCount;
            drones = new ArrayList<>(mc.level.getEntitiesOfClass(DroneBase.class, player.getBoundingBox().inflate(24.0)));
            drones.sort((a, b) -> Double.compare(a.distanceToSqr(player), b.distanceToSqr(player)));
        }
    }

    private static void renderDrones(GuiGraphics g, Minecraft mc, int x, int y) {
        var player = mc.player;
        int shown = 0;
        for (DroneBase drone : drones) {
            if (!drone.isAlive()) continue;
            if (shown++ >= 4) break;
            int color = drone.isActive() ? 0xFF3DBE5A : 0xFFE8A020;
            g.fill(x, y + 1, x + 4, y + 5, color);
            int pct = (int) Math.round(100.0 * drone.getHealth() / Math.max(1.0F, drone.getMaxHealth()));
            small(g, mc, Component.translatable("exo.robotica.hud.drone", drone.getDisplayName(), (int) Math.sqrt(drone.distanceToSqr(player)), pct),
                    x + 7, y, 0xFFD8FBFF);
            y += 9;
        }
    }
}
