package com.arno.robotica.exo;

import com.arno.robotica.core.CoreSounds;
import com.arno.robotica.exo.menu.ExoMenu;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.item.ItemStack;

/** Server side of the Exo-Frame key presses. The client only says what it wants; every condition is checked here. */
public final class ExoActions {
    private ExoActions() {}

    public static final int OPEN_MODULES = 0;
    public static final int TOGGLE_FLIGHT = 1;
    public static final int DOUBLE_JUMP = 2;
    public static final int DASH = 3;
    public static final int SONAR = 4;
    public static final int OVERCLOCK = 5;

    public static void apply(ServerPlayer player, int action) {
        if (player.isSpectator() || !ExoSuit.wearingAny(player)) return;
        switch (action) {
            case OPEN_MODULES -> ExoMenu.openWorn(player);
            case TOGGLE_FLIGHT -> toggleFlight(player);
            case DOUBLE_JUMP -> ExoTicker.doubleJump(player);
            case DASH -> ExoTicker.dash(player);
            case SONAR -> ExoTicker.sonar(player);
            case OVERCLOCK -> ExoTicker.overclock(player);
            default -> {
            }
        }
    }

    /** Flips the Flight module of the worn chestplate. Returns false when there is none. */
    public static boolean toggleFlight(ServerPlayer player) {
        ItemStack chest = ExoSuit.piece(player, EquipmentSlot.CHEST);
        int slot = chest.isEmpty() ? -1 : ExoData.slotOf(chest, ExoModuleKind.FLIGHT);
        if (slot < 0) {
            player.displayClientMessage(Component.translatable("exo.robotica.no_flight"), true);
            return false;
        }
        boolean on = !ExoData.isEnabled(chest, slot);
        ExoData.setEnabled(chest, slot, on);
        player.displayClientMessage(Component.translatable("exo.robotica.flight", Component.translatable(on ? "exo.robotica.on" : "exo.robotica.off")), true);
        CoreSounds.play(player, CoreSounds.TOOL_MODE, SoundSource.PLAYERS, 0.6F, on ? 1.3F : 0.8F);
        return true;
    }
}
