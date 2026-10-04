package com.arno.robotica.warp.client;

import com.arno.robotica.core.client.FitButton;
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
        nameBox = new EditBox(font, leftPos + 8, topPos + 20, 124, 18, Component.translatable("gui.robotica.warp.name"));
        nameBox.setMaxLength(WarpPadBlockEntity.MAX_NAME);
        nameBox.setValue(menu.name());
        nameBox.setHint(Component.translatable("gui.robotica.warp.name_hint"));
        addRenderableWidget(nameBox);
        setInitialFocus(nameBox);
        addRenderableWidget(new FitButton(leftPos + 136, topPos + 20, 32, 18, Component.translatable("gui.robotica.warp.save"), b -> saveName()));
        publicButton = addRenderableWidget(new FitButton(leftPos + 8, topPos + 42, 160, 18, publicLabel(), b -> PacketDistributor.sendToServer(
                new WarpPayloads.SetPublic(menu.pos(), !menu.isPublic()))));
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
        drawFitted(g, font, title, titleLabelX, titleLabelY, imageWidth - 16, TEXT, -1, false, 1.0F);
    }

    @Override
    protected void renderMachine(GuiGraphics g, int x, int y, int mouseX, int mouseY) {
        drawStatus(g, Component.translatable(menu.hasRift() ? "gui.robotica.warp.rift_on" : "gui.robotica.warp.rift_off"), x + 8, y + 66, 160, 1,
                menu.hasRift() ? Tone.GOOD : Tone.WARN);
        drawLabel(g, Component.translatable("gui.robotica.warp.owner", menu.ownerName().isEmpty() ? "-" : menu.ownerName()), x + 8, y + 78, 160);
        drawEnergyBarWide(g, x + 8, y + 92, 160, 11, menu.energy(), com.arno.robotica.warp.WarpConfig.padBuffer());
    }
}
