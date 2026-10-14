package com.arno.robotica.gear.tool;

import com.arno.robotica.core.CoreSounds;
import com.arno.robotica.core.module.ModuleKind;
import com.arno.robotica.core.module.Modules;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.item.ItemStack;

/** Server side handling of the client requests (keybinds, toggle screen). Everything is validated against the held item. */
public final class GearActions {
    private GearActions() {}

    public static final int CYCLE_MODE = 0;
    /** B: cycles the installed Fortune / Silk Touch modules and off. */
    public static final int CYCLE_DROPS = 1;
    /** Flips a plain tool setting; arg = ToggleKind ordinal. */
    public static final int TOGGLE = 2;
    /** Switches an installed module (tools and weapons) on or off; arg = ModuleKind ordinal. */
    public static final int MODULE_TOGGLE = 3;
    /** Left-click with the Rivet Gun in the main hand. */
    public static final int FIRE = 4;
    /** Next (arg >= 0) or previous sneak mode of the held tool. */
    public static final int CYCLE_SNEAK_MODE = 5;

    public static void apply(ServerPlayer player, int action, int arg) {
        ItemStack stack = player.getMainHandItem();
        if (action == FIRE) {
            com.arno.robotica.gear.weapon.RivetGunItem.trigger(player);
            return;
        }
        if (action == MODULE_TOGGLE) {
            toggleModule(player, stack, arg);
            return;
        }
        if (!(stack.getItem() instanceof GearToolItem tool)) return;
        switch (action) {
            case CYCLE_MODE -> cycleMode(player, stack, tool, arg);
            case CYCLE_SNEAK_MODE -> cycleSneakMode(player, stack, tool, arg);
            case CYCLE_DROPS -> cycleDrops(player, stack);
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

    public static void cycleSneakMode(ServerPlayer player, ItemStack stack, GearToolItem tool, int direction) {
        int n = tool.spec.modes.size();
        if (n <= 1) return;
        int index = tool.spec.modes.indexOf(tool.sneakMode(stack));
        AreaMode next = tool.spec.modes.get(Math.floorMod(index + (direction >= 0 ? 1 : -1), n));
        if (next == AreaMode.SINGLE) stack.remove(com.arno.robotica.gear.GearComponents.SNEAK_MODE.get());
        else stack.set(com.arno.robotica.gear.GearComponents.SNEAK_MODE.get(), next);
        click(player, next == AreaMode.SINGLE ? 0.85F : 1.2F);
        player.displayClientMessage(Component.translatable("gear.robotica.screen.sneak_mode", next.displayName()), true);
    }

    /** Off, Fortune, Silk Touch, off... over the drop modules the tool has. */
    public static void cycleDrops(ServerPlayer player, ItemStack stack) {
        if (!GearToolItem.hasDropModules(stack)) {
            player.displayClientMessage(Component.translatable("gear.robotica.enchant.needs_module"), true);
            return;
        }
        ModuleKind on = Modules.cycleGroup(stack, ModuleKind.Group.DROPS);
        click(player, on != null ? 1.2F : 0.85F);
        player.displayClientMessage(on == null ? Component.translatable("gear.robotica.enchant.none")
                : Component.translatable("gear.robotica.enchant.active", on.displayName(Modules.level(stack, on))), true);
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

    public static void toggleModule(ServerPlayer player, ItemStack stack, int ordinal) {
        ModuleKind kind = ModuleKind.byOrdinal(ordinal);
        if (kind == null || Modules.target(stack) == null || Modules.target(stack).isArmor() || Modules.level(stack, kind) <= 0) return;
        boolean on = !Modules.enabled(stack, kind);
        Modules.setEnabled(stack, kind, on);
        click(player, on ? 1.2F : 0.85F);
        player.displayClientMessage(Component.translatable("gear.robotica.toggle.changed", kind.displayName(Modules.level(stack, kind)),
                Component.translatable(on ? "gear.robotica.on" : "gear.robotica.off")), true);
    }
}
