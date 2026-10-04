package com.arno.robotica.replicator.client;

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
    private static final int TEXT = 0xFF404040;
    private static final int GOOD = 0xFF1E7A3A;
    private static final int BAD = 0xFFB02A20;
    private static final int WARN = 0xFF9A5A00;

    private Button modeButton;

    public ReplicatorScreen(ReplicatorMenu menu, Inventory inv, Component title) {
        super(menu, inv, title);
        this.imageHeight = ReplicatorMenu.IMAGE_HEIGHT;
        this.inventoryLabelY = this.imageHeight - 94;
    }

    @Override
    protected void init() {
        super.init();
        modeButton = addRenderableWidget(Button.builder(modeLabel(menu.mode()), b -> {
            Mode next = menu.mode().next();
            PacketDistributor.sendToServer(new ReplicatorModePayload(menu.pos(), next.ordinal()));
        }).bounds(leftPos + 62, topPos + 58, 66, 16).build());
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
        drawEnergyBar(g, x + 15, y + 17, 12, 56, menu.energy(), menu.capacity());
        drawProgress(g, x + 62, y + 20, 66, 8, menu.progress());

        if (menu.formed()) {
            Pause pause = menu.pause();
            if (pause == Pause.NONE) {
                drawText(g, Component.translatable("gui.robotica.replicator.status.formed"), x + 62, y + 32, GOOD);
            } else {
                drawText(g, Component.translatable("gui.robotica.replicator.pause." + pause.name().toLowerCase()), x + 62, y + 32,
                        pause == Pause.NO_ENERGY || pause == Pause.NO_VIAL ? BAD : WARN);
            }
        } else {
            drawText(g, Component.translatable(menu.structure().translationKey()), x + 62, y + 32, BAD);
        }
        int ticks = menu.cycleTicks();
        drawText(g, Component.translatable("gui.robotica.replicator.cycle", Fmt.duration(ticks), menu.speedMultiplier()),
                x + 62, y + 43, TEXT);
        drawText(g, Component.translatable("gui.robotica.replicator.cost", Fmt.energy(menu.energyPerTick())), x + 62, y + 76, TEXT);

        drawText(g, Component.translatable("gui.robotica.replicator.vial"), x + 38, y + 41, TEXT);
        drawText(g, Component.translatable("gui.robotica.replicator.boost"), x + 36, y + 71, TEXT);
        drawText(g, Component.translatable("gui.robotica.upgrades"), x + 124, y + 8, TEXT);
        if (menu.looting() > 0) {
            drawText(g, Component.translatable("gui.robotica.replicator.looting", menu.looting()), x + 124, y + 76, TEXT);
        }
        drawText(g, Component.translatable("gui.robotica.replicator.output"), x + 8, y + 79, TEXT);
    }
}
