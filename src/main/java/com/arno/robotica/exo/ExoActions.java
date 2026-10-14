package com.arno.robotica.exo;

import com.arno.robotica.core.CoreSounds;
import com.arno.robotica.core.module.ModuleItems;
import com.arno.robotica.core.module.ModuleKind;
import com.arno.robotica.core.module.Modules;
import com.arno.robotica.exo.menu.ExoMenu;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.item.ItemStack;

/** Server side of the Exo-Frame key presses. The client only says what it wants; every condition is checked here. */
public final class ExoActions {
    private ExoActions() {}

    public static final int OPEN_MODULES = 0;
    public static final int TOGGLE_FLIGHT = 1;
    public static final int DASH = 3;
    public static final int SONAR = 4;
    public static final int OVERCLOCK = 5;
    /** Jet Assist: jump held in mid air, thrust upward. */
    public static final int JET_THRUST = 6;
    /** Jet Assist III in hover mode: jump released, hold the height. */
    public static final int JET_HOVER = 7;
    /** Jet Assist: thrust and hover end (released, landed, cut out on the client). */
    public static final int JET_STOP = 8;

    public static void apply(ServerPlayer player, int action) {
        // Thrust intent only sets a flag (checked on the next tick; ending a glide is rate limited in jetWant); a stop acts at once
        // and is never dropped.
        if (action == JET_STOP) {
            ExoTicker.jetWant(player, ExoJet.OFF);
            return;
        }
        if (player.isSpectator() || !ExoSuit.wearingAny(player)) return;
        if (action == JET_THRUST || action == JET_HOVER) {
            ExoTicker.jetWant(player, action == JET_THRUST ? ExoJet.THRUST : ExoJet.HOVER);
            return;
        }
        if (!ExoTicker.actionAllowed(player, action)) return;
        switch (action) {
            case OPEN_MODULES -> ExoMenu.openWorn(player);
            case TOGGLE_FLIGHT -> toggleFlight(player);
            case DASH -> ExoTicker.dash(player);
            case SONAR -> ExoTicker.sonar(player);
            case OVERCLOCK -> ExoTicker.overclock(player);
            default -> {
            }
        }
    }

    /**
     * The Flight key. Flips the Flight module of the worn chestplate; without one, Jet Assist III switches hover mode
     * instead; with both it cycles Flight, hover, off. Returns false when there is neither.
     */
    public static boolean toggleFlight(ServerPlayer player) {
        ItemStack chest = ExoSuit.piece(player, EquipmentSlot.CHEST);
        int slot = chest.isEmpty() ? -1 : Modules.slotOf(chest, ModuleKind.FLIGHT);
        if (slot < 0) {
            if (ExoTicker.toggleHover(player)) return true;
            player.displayClientMessage(Component.translatable("exo.robotica.no_flight"), true);
            return false;
        }
        boolean flight = Modules.enabled(chest, slot);
        if (ExoTicker.hoverAvailable(player)) {
            if (flight) {
                Modules.setEnabled(chest, slot, false);
                return ExoTicker.setHover(player, true, true);
            }
            if (ExoData.hover(chest)) return ExoTicker.setHover(player, false, true);
        }
        setFlight(player, chest, slot, !flight);
        return true;
    }

    private static void setFlight(ServerPlayer player, ItemStack chest, int slot, boolean on) {
        Modules.setEnabled(chest, slot, on);
        player.displayClientMessage(Component.translatable("exo.robotica.flight", Component.translatable(on ? "exo.robotica.on" : "exo.robotica.off")), true);
        ExoTicker.notifySound(player, CoreSounds.TOOL_MODE.get(), 0.6F, on ? 1.3F : 0.8F);
    }
}
