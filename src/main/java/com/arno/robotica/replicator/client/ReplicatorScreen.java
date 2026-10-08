package com.arno.robotica.replicator.client;

import com.arno.robotica.core.client.FitButton;
import com.arno.robotica.core.client.IconButton;
import com.arno.robotica.core.client.MachineScreen;
import com.arno.robotica.core.util.Fmt;
import com.arno.robotica.replicator.ReplicatorModePayload;
import com.arno.robotica.replicator.block.ReplicatorControllerBlockEntity;
import com.arno.robotica.replicator.block.ReplicatorControllerBlockEntity.Mode;
import com.arno.robotica.replicator.block.ReplicatorControllerBlockEntity.Pause;
import com.arno.robotica.replicator.menu.ReplicatorMenu;
import net.minecraft.ChatFormatting;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.Tooltip;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.neoforged.neoforge.network.PacketDistributor;

/**
 * GUI of the Replicator Controller: energy bar, cycle progress, one short status word and a mode icon. Slots carry ghost icons;
 * cycle time, speed, FE/t and looting are in tooltips. A row above the output claims the stored experience: up to a
 * target level (-/+, shift for 10) or all of it, through menu button clicks the server validates.
 */
public class ReplicatorScreen extends MachineScreen<ReplicatorMenu> {
    /** Target level, kept while the game runs. */
    private static int targetLevel = 30;

    private IconButton modeButton;
    private Button claimButton;
    private Button allButton;
    private int shownTarget = -1;

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

        int y = topPos + ReplicatorMenu.XP_ROW_Y;
        addRenderableWidget(new FitButton(leftPos + 62, y, 12, 12, Component.literal("-"), b -> changeTarget(-1),
                Component.translatable("gui.robotica.replicator.target_tip")));
        addRenderableWidget(new FitButton(leftPos + 106, y, 12, 12, Component.literal("+"), b -> changeTarget(1),
                Component.translatable("gui.robotica.replicator.target_tip")));
        claimButton = addRenderableWidget(new FitButton(leftPos + 120, y, 26, 12, Component.translatable("gui.robotica.replicator.claim"),
                b -> click(ReplicatorMenu.claimToLevel(targetLevel))));
        allButton = addRenderableWidget(new FitButton(leftPos + 148, y, 20, 12, Component.translatable("gui.robotica.replicator.claim_all"),
                b -> click(ReplicatorMenu.CLAIM_ALL), Component.translatable("gui.robotica.replicator.claim_all_tip")));
        shownTarget = -1;
        updateXp();
    }

    private void changeTarget(int dir) {
        int step = Screen.hasShiftDown() ? 10 : 1;
        targetLevel = Math.max(1, Math.min(ReplicatorControllerBlockEntity.MAX_CLAIM_LEVEL, targetLevel + dir * step));
        updateXp();
    }

    private void click(int id) {
        if (minecraft != null && minecraft.gameMode != null) minecraft.gameMode.handleInventoryButtonClick(menu.containerId, id);
    }

    private void updateXp() {
        boolean any = menu.xpStored() > 0;
        claimButton.active = any;
        allButton.active = any;
        if (shownTarget != targetLevel) {
            shownTarget = targetLevel;
            claimButton.setTooltip(Tooltip.create(Component.translatable("gui.robotica.replicator.claim_tip", targetLevel)));
        }
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
        if (claimButton != null) updateXp();
    }

    @Override
    protected ItemStack ghostIcon(Slot slot) {
        return switch (slot.index) {
            case 0 -> icon("essence_vial");
            case 1 -> icon("plasma_actuator");
            case 2, 3, 4 -> icon("upgrade_speed");
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

        int rowY = y + ReplicatorMenu.XP_ROW_Y;
        drawFitted(g, font, Component.translatable("gui.robotica.replicator.xp", Fmt.compact(menu.xpStored())), x + 8, rowY + 2, 52, TEXT, -1, false, 1.0F);
        addTooltip(x + 7, rowY, 54, 12, Component.translatable("gui.robotica.replicator.xp_tip", menu.xpStored()));
        drawFitted(g, font, Component.translatable("gui.robotica.replicator.target", targetLevel), x + 90, rowY + 2, 30, TEXT, 0, false, 1.0F);
    }
}
