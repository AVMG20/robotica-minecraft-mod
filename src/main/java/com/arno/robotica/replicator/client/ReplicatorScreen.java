package com.arno.robotica.replicator.client;

import com.arno.robotica.core.client.IconButton;
import com.arno.robotica.core.client.MachineScreen;
import com.arno.robotica.core.util.Fmt;
import com.arno.robotica.replicator.ReplicatorModePayload;
import com.arno.robotica.replicator.block.ReplicatorControllerBlockEntity.Mode;
import com.arno.robotica.replicator.block.ReplicatorControllerBlockEntity.Pause;
import com.arno.robotica.replicator.menu.ReplicatorMenu;
import net.minecraft.ChatFormatting;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.neoforged.neoforge.network.PacketDistributor;

/**
 * GUI of the Replicator Controller: energy bar, cycle progress, one short status word and a mode icon. Slots carry ghost icons;
 * cycle time, speed, FE/t and looting are in tooltips.
 */
public class ReplicatorScreen extends MachineScreen<ReplicatorMenu> {
    private IconButton modeButton;

    public ReplicatorScreen(ReplicatorMenu menu, Inventory inv, Component title) {
        super(menu, inv, title);
        this.imageHeight = ReplicatorMenu.IMAGE_HEIGHT;
        this.inventoryLabelY = this.imageHeight - 93;
    }

    @Override
    protected int titleMaxWidth() {
        return 150;
    }

    @Override
    protected void init() {
        super.init();
        modeButton = addRenderableWidget(new IconButton(leftPos + 64, topPos + 56, 20, modeIcon(menu.mode()), false, b -> {
            Mode next = menu.mode().next();
            PacketDistributor.sendToServer(new ReplicatorModePayload(menu.pos(), next.ordinal()));
        }));
        updateMode();
    }

    private static ItemStack modeIcon(Mode mode) {
        return new ItemStack(mode == Mode.HARVEST ? Items.IRON_SWORD : Items.ZOMBIE_SPAWN_EGG);
    }

    private void updateMode() {
        modeButton.setIcon(modeIcon(menu.mode()));
        modeButton.hint(Component.translatable("gui.robotica.replicator.mode." + menu.mode().name().toLowerCase())
                .append(Component.literal("\n")).append(Component.translatable("gui.robotica.replicator.mode_tip").withStyle(ChatFormatting.GRAY)));
    }

    @Override
    protected void containerTick() {
        super.containerTick();
        if (modeButton != null) updateMode();
    }

    @Override
    protected ItemStack ghostIcon(Slot slot) {
        return switch (slot.index) {
            case 0 -> icon("essence_vial");
            case 1 -> icon("plasma_actuator");
            case 2, 3, 4 -> icon("upgrade_speed_1");
            case 23 -> icon("magma_core");
            default -> ItemStack.EMPTY;
        };
    }

    @Override
    protected Component slotHint(Slot slot) {
        return switch (slot.index) {
            case 0 -> Component.translatable("gui.robotica.replicator.slot_vial");
            case 1 -> Component.translatable("gui.robotica.replicator.slot_boost");
            case 2, 3, 4 -> Component.translatable("gui.robotica.slot_upgrade");
            case 23 -> Component.translatable("gui.robotica.replicator.slot_core");
            default -> null;
        };
    }

    @Override
    protected void renderMachine(GuiGraphics g, int x, int y, int mouseX, int mouseY) {
        Component cost = Component.translatable("gui.robotica.replicator.cost", Fmt.energy(menu.energyPerTick())).withStyle(ChatFormatting.GRAY);
        drawEnergyBar(g, x + 12, y + 18, 14, 52, menu.energy(), menu.capacity(), false, cost);
        drawProgress(g, x + 64, y + 22, 66, 8, menu.progress());
        Component cycle = Component.translatable("gui.robotica.replicator.cycle", Fmt.duration(menu.cycleTicks()), menu.speedMultiplier());
        if (menu.looting() > 0) {
            addTooltip(x + 63, y + 21, 68, 10, cycle, cost, Component.translatable("gui.robotica.replicator.looting", menu.looting()).withStyle(ChatFormatting.GRAY));
        } else {
            addTooltip(x + 63, y + 21, 68, 10, cycle, cost);
        }

        Component shortText;
        Component longText;
        Tone tone;
        if (menu.formed()) {
            Pause pause = menu.pause();
            if (pause == Pause.NONE) {
                boolean running = menu.progress() > 0;
                shortText = Component.translatable(running ? "gui.robotica.replicator.pause.none" : "gui.robotica.replicator.status.ready");
                longText = Component.translatable("gui.robotica.replicator.status.formed");
                tone = Tone.GOOD;
            } else {
                String name = pause.name().toLowerCase();
                shortText = Component.translatable("gui.robotica.replicator.pause." + name + ".short");
                longText = Component.translatable("gui.robotica.replicator.pause." + name);
                boolean bad = pause == Pause.NO_ENERGY || pause == Pause.NO_VIAL || pause == Pause.NEEDS_MAGMA_CORE
                        || pause == Pause.NEEDS_ANTIGRAV_CORE;
                tone = bad ? Tone.BAD : Tone.WARN;
            }
        } else {
            String key = menu.structure().translationKey();
            shortText = Component.translatable(key + ".short");
            longText = Component.translatable(key);
            tone = Tone.BAD;
        }
        drawStatus(g, shortText, x + 64, y + 35, 70, 2, tone);
        addTooltip(x + 63, y + 33, 70, 18, longText);
    }
}
