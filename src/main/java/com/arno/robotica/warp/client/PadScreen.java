package com.arno.robotica.warp.client;

import com.arno.robotica.core.client.MachineScreen;
import com.arno.robotica.warp.WarpPayloads;
import com.arno.robotica.warp.menu.PadMenu;
import com.arno.robotica.warp.pad.WarpPadBlockEntity;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.player.Inventory;
import net.neoforged.neoforge.network.PacketDistributor;
import org.lwjgl.glfw.GLFW;

/** Owner GUI of a Warp Pad: name box (24 characters), save button, public / private toggle, FE and upgrade status. */
public class PadScreen extends MachineScreen<PadMenu> {
    private EditBox nameBox;
    private Button publicButton;

    public PadScreen(PadMenu menu, Inventory inv, Component title) {
        super(menu, inv, title);
        this.imageWidth = 176;
        this.imageHeight = 112;
    }

    @Override
    protected void init() {
        super.init();
        nameBox = new EditBox(font, leftPos + 10, topPos + 28, 120, 18, Component.translatable("gui.robotica.warp.name"));
        nameBox.setMaxLength(WarpPadBlockEntity.MAX_NAME);
        nameBox.setValue(menu.name());
        nameBox.setHint(Component.translatable("gui.robotica.warp.name_hint"));
        addRenderableWidget(nameBox);
        setInitialFocus(nameBox);
        addRenderableWidget(Button.builder(Component.translatable("gui.robotica.warp.save"), b -> saveName())
                .bounds(leftPos + 134, topPos + 28, 32, 18).build());
        publicButton = addRenderableWidget(Button.builder(publicLabel(), b -> PacketDistributor.sendToServer(
                        new WarpPayloads.SetPublic(menu.pos(), !menu.isPublic())))
                .bounds(leftPos + 10, topPos + 54, 120, 20).build());
    }

    private Component publicLabel() {
        return Component.translatable(menu.isPublic() ? "gui.robotica.warp.public_on" : "gui.robotica.warp.public_off");
    }

    private void saveName() {
        PacketDistributor.sendToServer(new WarpPayloads.Rename(menu.pos(), nameBox.getValue()));
    }

    @Override
    protected void containerTick() {
        super.containerTick();
        publicButton.setMessage(publicLabel());
    }

    @Override
    public boolean keyPressed(int keyCode, int scanCode, int modifiers) {
        if (keyCode == GLFW.GLFW_KEY_ESCAPE) {
            onClose();
            return true;
        }
        if (nameBox.isFocused() && (keyCode == GLFW.GLFW_KEY_ENTER || keyCode == GLFW.GLFW_KEY_KP_ENTER)) {
            saveName();
            return true;
        }
        return nameBox.keyPressed(keyCode, scanCode, modifiers) || nameBox.canConsumeInput() || super.keyPressed(keyCode, scanCode, modifiers);
    }

    @Override
    protected void renderLabels(GuiGraphics g, int mouseX, int mouseY) {
        g.drawString(font, title, titleLabelX, titleLabelY, 0x404040, false);
    }

    @Override
    protected void renderMachine(GuiGraphics g, int x, int y, int mouseX, int mouseY) {
        drawEnergyBar(g, x + 150, y + 56, 14, 44, menu.energy(), com.arno.robotica.warp.WarpConfig.padBuffer());
        drawText(g, Component.translatable("gui.robotica.warp.owner", menu.ownerName().isEmpty() ? "-" : menu.ownerName()), x + 10, y + 82, 0xFF404040);
        drawText(g, Component.translatable(menu.hasRift() ? "gui.robotica.warp.rift_on" : "gui.robotica.warp.rift_off"), x + 10, y + 94,
                menu.hasRift() ? 0xFF6A3FB0 : 0xFF707070);
    }
}
