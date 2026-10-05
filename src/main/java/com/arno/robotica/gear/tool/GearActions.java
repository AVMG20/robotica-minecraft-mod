package com.arno.robotica.gear.tool;

import com.arno.robotica.core.CoreSounds;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.item.ItemStack;

/** Server side handling of the client requests (keybinds, toggle screen). Everything is validated against the held item. */
public final class GearActions {
    private GearActions() {}

    public static final int CYCLE_MODE = 0;
    public static final int CYCLE_ENCHANT = 1;
    public static final int TOGGLE = 2;

    public static void apply(ServerPlayer player, int action, int arg) {
        ItemStack stack = player.getMainHandItem();
        if (!(stack.getItem() instanceof GearToolItem tool)) return;
        switch (action) {
            case CYCLE_MODE -> cycleMode(player, stack, tool, arg);
            case CYCLE_ENCHANT -> cycleEnchant(player, stack, tool);
            case TOGGLE -> toggle(player, stack, tool, arg);
            default -> {
            }
        }
    }

    /** UI-like click of the tool, heard by nearby players too. Player triggered, so no extra rate limit is needed. */
    private static void click(ServerPlayer player, float pitch) {
        CoreSounds.play(player, CoreSounds.TOOL_MODE, SoundSource.PLAYERS, 0.5F, pitch);
    }

    public static void cycleMode(ServerPlayer player, ItemStack stack, GearToolItem tool, int direction) {
        int n = tool.spec.modes.size();
        if (n <= 1) return;
        int index = tool.spec.modes.indexOf(tool.mode(stack));
        AreaMode next = tool.spec.modes.get(Math.floorMod(index + (direction >= 0 ? 1 : -1), n));
        stack.set(com.arno.robotica.gear.GearComponents.MODE.get(), next);
        GearSounds.modeSwitch(player, next);
        player.displayClientMessage(tool.modeStrip(next), true);
    }

    public static void cycleEnchant(ServerPlayer player, ItemStack stack, GearToolItem tool) {
        if (tool.spec.fortuneLevel <= 0) return;
        int next = ToolSettings.nextEnchantMode(ToolSettings.enchantMode(stack));
        ToolSettings.setEnchantMode(stack, next);
        ToolSettings.syncEnchantments(stack, tool, player.level().registryAccess());
        click(player, 1.0F);
        String key = switch (next) {
            case ToolSettings.ENCHANT_FORTUNE -> "gear.robotica.enchant.fortune";
            case ToolSettings.ENCHANT_SILK -> "gear.robotica.enchant.silk";
            default -> "gear.robotica.enchant.none";
        };
        player.displayClientMessage(Component.translatable(key), true);
    }

    public static void toggle(ServerPlayer player, ItemStack stack, GearToolItem tool, int ordinal) {
        ToggleKind kind = ToggleKind.byOrdinal(ordinal);
        if (kind == null || !tool.spec.toggles.contains(kind)) return;
        boolean on = !ToolSettings.has(stack, kind);
        ToolSettings.set(stack, kind, on);
        click(player, on ? 1.2F : 0.85F);
        player.displayClientMessage(Component.translatable("gear.robotica.toggle.changed", kind.displayName(),
                Component.translatable(on ? "gear.robotica.on" : "gear.robotica.off")), true);
    }
}
