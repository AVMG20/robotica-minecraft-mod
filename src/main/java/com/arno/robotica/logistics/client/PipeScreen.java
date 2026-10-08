package com.arno.robotica.logistics.client;

import com.arno.robotica.core.client.FitButton;
import com.arno.robotica.core.client.MachineScreen;
import com.arno.robotica.logistics.menu.PipeMenu;
import com.arno.robotica.logistics.pipe.ItemPipeBlockEntity;
import com.arno.robotica.logistics.pipe.PipeMode;
import com.arno.robotica.logistics.pipe.PipeOrder;
import com.arno.robotica.logistics.pipe.PipePriority;
import net.minecraft.client.gui.components.Tooltip;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;

/**
 * GUI of one pipe face: Insert / Extract / Off, nine ghost filter slots, whitelist or blacklist, the Extract order and
 * the Insert priority (click: lower, shift-click: higher).
 */
public class PipeScreen extends MachineScreen<PipeMenu> {
    private final FitButton[] modes = new FitButton[PipeMode.values().length];
    private FitButton list;
    private FitButton order;
    private FitButton priority;

    public PipeScreen(PipeMenu menu, Inventory inv, Component title) {
        super(menu, inv, title);
        this.imageHeight = 180;
        this.inventoryLabelY = 87;
    }

    @Override
    protected void init() {
        super.init();
        for (PipeMode mode : PipeMode.values()) {
            modes[mode.ordinal()] = addRenderableWidget(new FitButton(leftPos + 8 + mode.ordinal() * 54, topPos + 18, 52, 16,
                    Component.translatable(mode.translationKey()), b -> press(PipeMenu.BTN_MODE + mode.ordinal()),
                    Component.translatable("gui.robotica.pipe.mode." + mode.name().toLowerCase(java.util.Locale.ROOT))));
        }
        list = addRenderableWidget(new FitButton(leftPos + 8, topPos + 64, 79, 16, Component.empty(), b -> press(PipeMenu.BTN_LIST)));
        order = addRenderableWidget(new FitButton(leftPos + 89, topPos + 64, 79, 16, Component.empty(), b -> press(PipeMenu.BTN_ORDER)));
        priority = addRenderableWidget(new FitButton(leftPos + 89, topPos + 64, 79, 16, Component.empty(),
                b -> press(hasShiftDown() ? PipeMenu.BTN_PRIORITY_UP : PipeMenu.BTN_PRIORITY_DOWN)));
        update();
    }

    private void press(int id) {
        if (minecraft != null && minecraft.gameMode != null) minecraft.gameMode.handleInventoryButtonClick(menu.containerId, id);
    }

    private void update() {
        PipeMode current = menu.mode();
        for (PipeMode mode : PipeMode.values()) modes[mode.ordinal()].active = mode != current;
        boolean white = menu.whitelist();
        list.setMessage(Component.translatable(white ? "gui.robotica.pipe.whitelist" : "gui.robotica.pipe.blacklist"));
        list.setTooltip(Tooltip.create(Component.translatable(white ? "gui.robotica.pipe.whitelist.hint" : "gui.robotica.pipe.blacklist.hint")));
        PipeOrder o = menu.order();
        order.visible = current == PipeMode.EXTRACT;
        order.setMessage(Component.translatable(o.translationKey()));
        order.setTooltip(Tooltip.create(Component.translatable(o.translationKey() + ".hint")));
        PipePriority p = menu.priority();
        priority.visible = current == PipeMode.INSERT;
        priority.setMessage(Component.translatable("gui.robotica.pipe.priority", Component.translatable(p.translationKey())));
        priority.setTooltip(Tooltip.create(Component.translatable("gui.robotica.pipe.priority.hint")));
    }

    @Override
    protected void containerTick() {
        super.containerTick();
        update();
    }

    @Override
    protected ItemStack ghostIcon(Slot slot) {
        return slot.index < ItemPipeBlockEntity.FILTER_SLOTS ? new ItemStack(Items.HOPPER) : ItemStack.EMPTY;
    }

    @Override
    protected Component slotHint(Slot slot) {
        return slot.index < ItemPipeBlockEntity.FILTER_SLOTS ? Component.translatable("gui.robotica.pipe.slot_filter") : null;
    }
}
