package com.arno.robotica.automation.client;

import com.arno.robotica.automation.farmbot.FarmBot;
import com.arno.robotica.automation.farmbot.FarmBotMenu;
import com.arno.robotica.automation.farmbot.Stumpy;
import com.arno.robotica.core.client.IconButton;
import com.arno.robotica.core.client.MachineScreen;
import com.arno.robotica.core.util.Fmt;
import net.minecraft.ChatFormatting;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;

/** Stumpy and Sprout GUI: energy bar and battery, area toggle, storage check, card slots (the Mk opens them), pouch, status. */
public class FarmBotScreen extends MachineScreen<FarmBotMenu> {
    private static final ItemStack CHEST = new ItemStack(Items.CHEST);
    private static final ItemStack SAPLING = new ItemStack(Items.OAK_SAPLING);
    private static final ItemStack SEEDS = new ItemStack(Items.WHEAT_SEEDS);
    private IconButton areaButton;

    public FarmBotScreen(FarmBotMenu menu, Inventory inv, Component title) {
        super(menu, inv, title);
        this.imageHeight = 176;
        this.inventoryLabelY = 83;
    }

    @Override
    protected void init() {
        super.init();
        areaButton = addRenderableWidget(new IconButton(leftPos + 48, topPos + 17, 20, new ItemStack(Items.SPYGLASS), true,
                b -> press(FarmBotMenu.BUTTON_SHOW_AREA)));
        update();
    }

    private void press(int id) {
        if (minecraft != null && minecraft.gameMode != null) minecraft.gameMode.handleInventoryButtonClick(menu.containerId, id);
    }

    private void update() {
        areaButton.setOn(menu.showArea());
        areaButton.hint(Component.translatable(menu.showArea() ? "gui.robotica.area_on" : "gui.robotica.area_off"));
    }

    @Override
    protected void containerTick() {
        super.containerTick();
        update();
    }

    private int cardSlots() {
        return menu.bot.upgrades.getSlots();
    }

    @Override
    protected ItemStack ghostIcon(Slot slot) {
        if (slot.index == 0) return icon("copper_cell");
        if (slot.index <= cardSlots()) return icon("upgrade_speed");
        // The pouch keeps saplings or seeds to replant.
        if (isMachineSlot(slot)) return menu.bot instanceof Stumpy ? SAPLING : SEEDS;
        return ItemStack.EMPTY;
    }

    @Override
    protected Component slotHint(Slot slot) {
        if (slot.index == 0) return Component.translatable("gui.robotica.slot_battery");
        if (slot.index <= cardSlots()) return Component.translatable("gui.robotica.slot_robot_card");
        if (isMachineSlot(slot)) return Component.translatable("gui.robotica.farm_bot.pouch");
        return null;
    }

    @Override
    protected int titleMaxWidth() {
        return 100;
    }

    @Override
    protected void renderMachine(GuiGraphics g, int x, int y, int mouseX, int mouseY) {
        drawEnergyBar(g, x + 8, y + 18, 12, 52, menu.energy(), menu.capacity(), false,
                Component.translatable("gui.robotica.processing.using", Fmt.compact(menu.usePerTick())).withStyle(ChatFormatting.GRAY));
        int size = menu.size();
        drawLabelRight(g, Component.translatable("gui.robotica.area", size, size), x + imageWidth - 8, y + 6, 40);
        addTooltip(x + imageWidth - 50, y + 4, 44, 11, Component.translatable("gui.robotica.tier_area", menu.tier(), size, size),
                Component.translatable("gui.robotica.robot_area_hint").withStyle(ChatFormatting.GRAY));

        // Storage check: the chest lights up once the robot found one near its home.
        int cx = x + 72, cy = y + 19;
        if (menu.hasStorage()) g.renderItem(CHEST, cx, cy);
        else drawGhost(g, CHEST, cx, cy);
        addTooltip(cx - 1, cy - 1, 18, 18, Component.translatable(menu.hasStorage() ? "gui.robotica.farm_bot.storage_found"
                : "gui.robotica.farm_bot.storage_missing"));

        FarmBot.Status status = menu.status();
        Tone tone = switch (status) {
            case WORKING -> Tone.GOOD;
            case NO_ENERGY -> Tone.BAD;
            case OUTPUT_FULL, IDLE -> Tone.WARN;
        };
        drawStatus(g, Component.translatable(status.key()), x + 8, y + 74, 104, 1, tone);
        if (status != FarmBot.Status.WORKING) addTooltip(x + 8, y + 72, 104, 11, Component.translatable(status.hintKey()));
    }
}
