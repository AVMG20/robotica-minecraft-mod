package com.arno.robotica.drones.client.screen;

import com.arno.robotica.core.client.FitButton;
import com.arno.robotica.core.client.IconButton;
import com.arno.robotica.core.client.MachineScreen;
import com.arno.robotica.drones.entity.MiningDrone;
import com.arno.robotica.drones.menu.MiningDroneMenu;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;

import java.util.Locale;

/** GUI of the Mining Drone: loot grid, battery and torch slot, status line, energy bar and one row of command buttons. */
public class MiningDroneScreen extends MachineScreen<MiningDroneMenu> {
    private IconButton tunnel;
    private IconButton follow;
    private IconButton stay;
    private IconButton ret;
    private FitButton turn;
    private FitButton length;
    private IconButton voidToggle;
    private final int rowB;
    private final int rowC;

    public MiningDroneScreen(MiningDroneMenu menu, Inventory inv, Component title) {
        super(menu, inv, title);
        int rows = menu.storageSlots / 9;
        this.rowB = 18 + rows * 18 + 4;
        this.rowC = rowB + 22;
        this.inventoryLabelY = rowC + 22;
        this.imageHeight = rowB + 54 + 58 + 18 + 8;
    }

    @Override
    protected void init() {
        super.init();
        int y = topPos + rowC;
        tunnel = addRenderableWidget(new IconButton(leftPos + 8, y, 20, new ItemStack(Items.IRON_PICKAXE), false, b -> press(MiningDroneMenu.BTN_TUNNEL)));
        follow = addRenderableWidget(new IconButton(leftPos + 30, y, 20, new ItemStack(Items.LEAD), true, b -> press(MiningDroneMenu.BTN_FOLLOW)));
        stay = addRenderableWidget(new IconButton(leftPos + 52, y, 20, new ItemStack(Items.ANVIL), true, b -> press(MiningDroneMenu.BTN_STAY)));
        ret = addRenderableWidget(new IconButton(leftPos + 74, y, 20, new ItemStack(Items.COMPASS), false, b -> press(MiningDroneMenu.BTN_RETURN)));
        turn = addRenderableWidget(new FitButton(leftPos + 104, y, 20, 20, Component.literal("N"), b -> press(MiningDroneMenu.BTN_TURN)));
        length = addRenderableWidget(new FitButton(leftPos + 126, y, 20, 20, Component.literal("64"), b -> press(MiningDroneMenu.BTN_LENGTH)));
        voidToggle = addRenderableWidget(new IconButton(leftPos + 148, y, 20, new ItemStack(Items.CACTUS), true, b -> press(MiningDroneMenu.BTN_VOID)));
        tunnel.hint(Component.translatable("gui.robotica.drone.tunnel"));
        follow.hint(Component.translatable("gui.robotica.drone.follow"));
        stay.hint(Component.translatable("gui.robotica.drone.stay"));
        ret.hint(Component.translatable("gui.robotica.drone.return"));
        voidToggle.hint(Component.translatable("gui.robotica.drone.void"));
        update();
    }

    private void press(int id) {
        if (minecraft != null && minecraft.gameMode != null) minecraft.gameMode.handleInventoryButtonClick(menu.containerId, id);
    }

    private void update() {
        MiningDrone.Mode mode = menu.mode();
        follow.setOn(mode == MiningDrone.Mode.FOLLOW);
        stay.setOn(mode == MiningDrone.Mode.STAY);
        voidToggle.setOn(menu.voidOn());
        String dir = switch (menu.heading()) {
            case NORTH -> "N";
            case EAST -> "E";
            case SOUTH -> "S";
            default -> "W";
        };
        turn.setMessage(Component.literal(dir));
        turn.setTooltip(net.minecraft.client.gui.components.Tooltip.create(Component.translatable("gui.robotica.drone.turn", Component.translatable("gui.robotica.dir." + dir.toLowerCase(Locale.ROOT)))));
        length.setMessage(Component.literal(Integer.toString(menu.length())));
        length.setTooltip(net.minecraft.client.gui.components.Tooltip.create(Component.translatable("gui.robotica.drone.length", menu.length())));
        tunnel.active = mode != MiningDrone.Mode.TUNNEL;
        turn.active = mode != MiningDrone.Mode.TUNNEL;
    }

    @Override
    protected void containerTick() {
        super.containerTick();
        update();
    }

    @Override
    protected ItemStack ghostIcon(Slot slot) {
        if (slot.index == menu.torchSlotIndex()) return new ItemStack(Items.TORCH);
        if (slot.index == menu.batterySlotIndex()) return icon("copper_cell");
        return ItemStack.EMPTY;
    }

    @Override
    protected Component slotHint(Slot slot) {
        if (slot.index == menu.torchSlotIndex()) return Component.translatable("gui.robotica.drone.slot_torch");
        if (slot.index == menu.batterySlotIndex()) return Component.translatable("gui.robotica.slot_battery");
        return null;
    }

    @Override
    protected void renderMachine(GuiGraphics g, int x, int y, int mouseX, int mouseY) {
        MiningDrone.Mode mode = menu.mode();
        Tone tone;
        Component text;
        if (mode == MiningDrone.Mode.TUNNEL) {
            tone = Tone.GOOD;
            text = Component.translatable("gui.robotica.drone.digging", menu.progress(), menu.jobLength());
        } else if (menu.lastStop() != null && mode != MiningDrone.Mode.FOLLOW && mode != MiningDrone.Mode.STAY) {
            tone = Tone.WARN;
            text = Component.translatable("gui.robotica.drone.mode.return");
        } else if (menu.lastStop() != null) {
            MiningDrone.Stop stop = menu.lastStop();
            tone = stop == MiningDrone.Stop.DONE ? Tone.GOOD : Tone.WARN;
            text = stop == MiningDrone.Stop.DONE ? Component.translatable("message.robotica.mining.done", menu.lastStopBlocks())
                    : Component.translatable("message.robotica.mining." + stop.key());
        } else {
            tone = Tone.WARN;
            text = Component.translatable("gui.robotica.drone.mode." + mode.name().toLowerCase(Locale.ROOT));
        }
        if (menu.capacity() > 0 && menu.energy() * 100L < menu.capacity() * 10L) tone = Tone.BAD;
        drawStatus(g, text, x + 48, y + rowB, 120, 1, tone);
        drawEnergyBarWide(g, x + 48, y + rowB + 12, 120, 6, menu.energy(), menu.capacity());
    }
}
