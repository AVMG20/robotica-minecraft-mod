package com.arno.robotica.automation.client;

import com.arno.robotica.automation.entity.ChunkAnchorBlockEntity.State;
import com.arno.robotica.automation.menu.ChunkAnchorMenu;
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

/** Chunk Anchor GUI: energy and battery, an area toggle, the state, the loaded area, upkeep, the owner's chunks against the cap. */
public class ChunkAnchorScreen extends MachineScreen<ChunkAnchorMenu> {
    private static final int TEXT_X = 28;
    private static final int TEXT_W = 118;
    private IconButton areaButton;

    public ChunkAnchorScreen(ChunkAnchorMenu menu, Inventory inv, Component title) {
        super(menu, inv, title);
    }

    @Override
    protected void init() {
        super.init();
        areaButton = addRenderableWidget(new IconButton(leftPos + 150, topPos + 38, 20, new ItemStack(Items.SPYGLASS), true,
                b -> press(ChunkAnchorMenu.BUTTON_SHOW_AREA)));
        updateButtons();
    }

    private void press(int id) {
        if (minecraft != null && minecraft.gameMode != null) {
            minecraft.gameMode.handleInventoryButtonClick(menu.containerId, id);
        }
    }

    private void updateButtons() {
        areaButton.setOn(menu.showArea());
        areaButton.hint(Component.translatable(menu.showArea() ? "gui.robotica.area_on" : "gui.robotica.area_off"));
    }

    @Override
    protected void containerTick() {
        super.containerTick();
        updateButtons();
    }

    @Override
    protected ItemStack ghostIcon(Slot slot) {
        return slot.index == 0 ? icon("copper_cell") : ItemStack.EMPTY;
    }

    @Override
    protected Component slotHint(Slot slot) {
        return slot.index == 0 ? Component.translatable("gui.robotica.slot_battery") : null;
    }

    @Override
    protected int titleMaxWidth() {
        return 136;
    }

    private static Tone tone(State state) {
        return switch (state) {
            case LOADING -> Tone.GOOD;
            case NO_POWER, OVER_CAP -> Tone.BAD;
            default -> Tone.WARN;
        };
    }

    @Override
    protected void renderMachine(GuiGraphics g, int x, int y, int mouseX, int mouseY) {
        int upkeep = menu.upkeep();
        State state = menu.state();
        drawEnergyBar(g, x + 9, y + 18, 12, 52, menu.energy(), menu.capacity(), false,
                Component.translatable("gui.robotica.processing.using", Fmt.compact(state == State.LOADING ? upkeep : 0))
                        .withStyle(ChatFormatting.GRAY));

        int tx = x + TEXT_X;
        drawStatus(g, Component.translatable("gui.robotica.anchor.state." + state.key()), tx, y + 19, TEXT_W, 1, tone(state));
        addTooltip(tx, y + 17, TEXT_W, 11, Component.translatable("gui.robotica.anchor_hint." + state.key()));

        int side = menu.be.side();
        drawLabel(g, Component.translatable("gui.robotica.anchor.area", side, side), tx, y + 33, TEXT_W);
        drawLabel(g, Component.translatable("gui.robotica.anchor.upkeep", Fmt.energy(upkeep)), tx, y + 44, TEXT_W);

        int used = menu.used(), cap = menu.cap();
        Component use = Component.translatable("gui.robotica.anchor.owner_use", used, cap);
        boolean over = state == State.OVER_CAP;
        drawFitted(g, font, use, tx, y + 55, TEXT_W, over ? Tone.BAD.textColor() : TEXT, -1, false, 1.0F);
        addTooltip(tx, y + 53, TEXT_W, 11, Component.translatable("gui.robotica.anchor.owner_use_hint"));

        if (state == State.LOADING && upkeep > 0) {
            drawFitted(g, font, Component.translatable("gui.robotica.anchor.uptime", Fmt.duration(menu.loadedSeconds() * 20L)),
                    tx, y + 64, TEXT_W, TEXT_MUTED, -1, false, 0.75F);
            addTooltip(tx, y + 63, TEXT_W, 8, Component.translatable("gui.robotica.anchor.buffer",
                    Fmt.duration(menu.energy() / upkeep)));
        }
    }
}
