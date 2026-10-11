package com.arno.robotica.drones.client.screen;

import com.arno.robotica.core.client.IconButton;
import com.arno.robotica.core.client.MachineScreen;
import com.arno.robotica.drones.entity.BuddyBot;
import com.arno.robotica.drones.menu.BuddyBotMenu;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;

import java.util.Locale;

/** GUI of the Buddy Bot: cargo grid, battery slot, status line, energy bar and one row of buttons. */
public class BuddyBotScreen extends MachineScreen<BuddyBotMenu> {
    private static final ItemStack[] STANCE_ICONS = {new ItemStack(Items.IRON_SWORD), new ItemStack(Items.SHIELD), new ItemStack(Items.POPPY)};

    private IconButton follow;
    private IconButton patrol;
    private IconButton stay;
    private IconButton stance;
    private IconButton pickup;
    private IconButton lights;
    private IconButton voidToggle;
    private IconButton dig;
    private final int rowB;
    private final int rowC;

    public BuddyBotScreen(BuddyBotMenu menu, Inventory inv, Component title) {
        super(menu, inv, title);
        this.rowB = BuddyBotMenu.rowB(menu.cargoSlots);
        this.rowC = rowB + 21;
        this.inventoryLabelY = rowB + BuddyBotMenu.INVENTORY_OFFSET - 11;
        this.imageHeight = rowB + BuddyBotMenu.INVENTORY_OFFSET + 83;
    }

    @Override
    protected void init() {
        super.init();
        follow = button(0, new ItemStack(Items.LEAD), true, BuddyBotMenu.BTN_FOLLOW, "gui.robotica.buddy.follow");
        patrol = button(1, new ItemStack(Items.COMPASS), true, BuddyBotMenu.BTN_PATROL, "gui.robotica.buddy.patrol");
        stay = button(2, new ItemStack(Items.ANVIL), true, BuddyBotMenu.BTN_STAY, "gui.robotica.buddy.stay");
        stance = button(3, STANCE_ICONS[0], false, BuddyBotMenu.BTN_STANCE, "gui.robotica.buddy.stance.aggressive");
        pickup = button(4, new ItemStack(Items.HOPPER), true, BuddyBotMenu.BTN_PICKUP, "gui.robotica.buddy.pickup");
        lights = button(5, icon("lamp_rod"), true, BuddyBotMenu.BTN_LIGHTS, "gui.robotica.buddy.lights");
        voidToggle = button(6, new ItemStack(Items.CACTUS), true, BuddyBotMenu.BTN_VOID, "gui.robotica.buddy.void");
        dig = button(7, new ItemStack(Items.IRON_PICKAXE), false, BuddyBotMenu.BTN_DIG, "gui.robotica.buddy.dig");
        update();
    }

    private IconButton button(int index, ItemStack icon, boolean toggle, int id, String hint) {
        IconButton b = addRenderableWidget(new IconButton(leftPos + 8 + index * 20, topPos + rowC, 18, icon, toggle, btn -> press(id)));
        b.hint(Component.translatable(hint));
        return b;
    }

    private void press(int id) {
        if (minecraft != null && minecraft.gameMode != null) minecraft.gameMode.handleInventoryButtonClick(menu.containerId, id);
    }

    private BuddyBot.Stance shownStance;
    private boolean shownDigging;

    private void update() {
        BuddyBot.Mode mode = menu.mode();
        follow.setOn(mode == BuddyBot.Mode.FOLLOW);
        patrol.setOn(mode == BuddyBot.Mode.PATROL);
        stay.setOn(mode == BuddyBot.Mode.STAY);
        pickup.setOn(menu.pickupOn());
        lights.setOn(menu.lightsOn());
        voidToggle.setOn(menu.voidOn());
        BuddyBot.Stance s = menu.stance();
        if (s != shownStance) {
            shownStance = s;
            stance.setIcon(STANCE_ICONS[s.ordinal()]);
            stance.hint(Component.translatable("gui.robotica.buddy.stance." + s.name().toLowerCase(Locale.ROOT)));
        }
        if (menu.digging() != shownDigging) {
            shownDigging = menu.digging();
            dig.hint(Component.translatable(shownDigging ? "gui.robotica.buddy.dig_stop" : "gui.robotica.buddy.dig"));
        }
    }

    @Override
    protected void containerTick() {
        super.containerTick();
        update();
    }

    @Override
    protected ItemStack ghostIcon(Slot slot) {
        if (slot.index == menu.batterySlotIndex()) return icon("copper_cell");
        return ItemStack.EMPTY;
    }

    @Override
    protected Component slotHint(Slot slot) {
        if (slot.index == menu.batterySlotIndex()) return Component.translatable("gui.robotica.slot_battery");
        return null;
    }

    @Override
    protected int titleMaxWidth() {
        return 100;
    }

    @Override
    protected void renderMachine(GuiGraphics g, int x, int y, int mouseX, int mouseY) {
        drawLabelRight(g, Component.translatable("gui.robotica.drone.hp", menu.health(), menu.maxHealth()), x + imageWidth - 8, y + 6, 56);
        BuddyBot.Mode mode = menu.mode();
        Tone tone;
        Component text;
        if (menu.low()) {
            tone = Tone.BAD;
            text = Component.translatable("gui.robotica.buddy.low");
        } else if (menu.digging()) {
            tone = Tone.GOOD;
            text = Component.translatable("gui.robotica.buddy.digging", menu.progress(), menu.jobLength());
        } else if (mode == null) {
            tone = Tone.WARN;
            text = Component.translatable("gui.robotica.buddy.returning");
        } else if (menu.engaged()) {
            tone = Tone.GOOD;
            text = Component.translatable("gui.robotica.buddy.fighting");
        } else {
            tone = Tone.WARN;
            text = Component.translatable("gui.robotica.buddy.mode." + mode.name().toLowerCase(Locale.ROOT));
        }
        drawStatus(g, text, x + 28, y + rowB, 140, 1, tone);
        drawEnergyBarWide(g, x + 28, y + rowB + 11, 140, 6, menu.energy(), menu.capacity());
    }
}
