package com.arno.robotica.replicator.client;

import com.arno.robotica.core.client.FitButton;
import com.arno.robotica.core.client.MachineScreen;
import com.arno.robotica.core.util.Fmt;
import com.arno.robotica.replicator.ReplicatorModePayload;
import com.arno.robotica.replicator.block.ReplicatorControllerBlockEntity.Mode;
import com.arno.robotica.replicator.block.ReplicatorControllerBlockEntity.Pause;
import com.arno.robotica.replicator.menu.ReplicatorMenu;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.player.Inventory;
import net.neoforged.neoforge.network.PacketDistributor;

/** GUI of the Replicator Controller: energy, progress, formed status with reason, mode button, slots. */
public class ReplicatorScreen extends MachineScreen<ReplicatorMenu> {
    private Button modeButton;

    public ReplicatorScreen(ReplicatorMenu menu, Inventory inv, Component title) {
        super(menu, inv, title);
        this.imageHeight = ReplicatorMenu.IMAGE_HEIGHT;
        this.inventoryLabelY = this.imageHeight - 93;
    }

    @Override
    protected int titleMaxWidth() {
        return 108;
    }

    @Override
    protected void init() {
        super.init();
        modeButton = addRenderableWidget(new FitButton(leftPos + 64, topPos + 71, 66, 13, modeLabel(menu.mode()), b -> {
            Mode next = menu.mode().next();
            PacketDistributor.sendToServer(new ReplicatorModePayload(menu.pos(), next.ordinal()));
        }, Component.translatable("gui.robotica.replicator.mode_tip")));
    }

    private static Component modeLabel(Mode mode) {
        return Component.translatable("gui.robotica.replicator.mode." + mode.name().toLowerCase());
    }

    @Override
    protected void containerTick() {
        super.containerTick();
        if (modeButton != null) modeButton.setMessage(modeLabel(menu.mode()));
    }

    @Override
    protected void renderMachine(GuiGraphics g, int x, int y, int mouseX, int mouseY) {
        drawEnergyBar(g, x + 12, y + 18, 14, 46, menu.energy(), menu.capacity(), true,
                Component.translatable("gui.robotica.replicator.cost", Fmt.energy(menu.energyPerTick())).withStyle(net.minecraft.ChatFormatting.GRAY));
        drawProgress(g, x + 64, y + 20, 66, 8, menu.progress());

        if (menu.formed()) {
            Pause pause = menu.pause();
            if (pause == Pause.NONE) {
                drawStatus(g, Component.translatable("gui.robotica.replicator.status.formed"), x + 64, y + 32, 66, 2, Tone.GOOD);
            } else {
                boolean bad = pause == Pause.NO_ENERGY || pause == Pause.NO_VIAL || pause == Pause.NEEDS_MAGMA_CORE
                        || pause == Pause.NEEDS_ANTIGRAV_CORE;
                drawStatus(g, Component.translatable("gui.robotica.replicator.pause." + pause.name().toLowerCase()), x + 64, y + 32, 66, 2,
                        bad ? Tone.BAD : Tone.WARN);
            }
        } else {
            drawStatus(g, Component.translatable(menu.structure().translationKey()), x + 64, y + 32, 66, 2, Tone.BAD);
        }
        drawLabel(g, Component.translatable("gui.robotica.replicator.cycle", Fmt.duration(menu.cycleTicks()), menu.speedMultiplier()), x + 64, y + 52, 66);
        drawLabel(g, Component.translatable("gui.robotica.replicator.cost", Fmt.energy(menu.energyPerTick())), x + 64, y + 61, 66);

        drawLabelCentered(g, Component.translatable("gui.robotica.replicator.vial"), x + 46, y + 38, 34);
        drawLabelCentered(g, Component.translatable("gui.robotica.replicator.boost"), x + 46, y + 66, 34);
        drawLabelRight(g, Component.translatable("gui.robotica.upgrades"), x + 170, y + 8, 48);
        drawLabelRight(g, Component.translatable("gui.robotica.replicator.catalyst"), x + 170, y + 39, 18);
        if (menu.looting() > 0) {
            drawLabelRight(g, Component.translatable("gui.robotica.replicator.looting", menu.looting()), x + 170, y + 77, 50);
        }
        drawLabel(g, Component.translatable("gui.robotica.replicator.output"), x + 8, y + 77, 60);
    }
}
