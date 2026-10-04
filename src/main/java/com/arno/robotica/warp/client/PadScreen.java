package com.arno.robotica.warp.client;

import com.arno.robotica.core.client.FitButton;
import com.arno.robotica.core.client.IconButton;
import com.arno.robotica.core.client.MachineScreen;
import com.arno.robotica.warp.WarpPayloads;
import com.arno.robotica.warp.menu.PadMenu;
import com.arno.robotica.warp.pad.WarpPadBlockEntity;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.neoforged.neoforge.network.PacketDistributor;
import org.lwjgl.glfw.GLFW;

/** Owner GUI of a Warp Pad: name box (24 characters), save button, public / private toggle, FE and upgrade status. */
public class PadScreen extends MachineScreen<PadMenu> {
    private EditBox nameBox;
    private IconButton publicButton;

    public PadScreen(PadMenu menu, Inventory inv, Component title) {
        super(menu, inv, title);
        this.imageWidth = 176;
        this.imageHeight = 94;
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
        publicButton = addRenderableWidget(new IconButton(leftPos + 8, topPos + 42, 20, new ItemStack(Items.ENDER_EYE), true,
                b -> PacketDistributor.sendToServer(new WarpPayloads.SetPublic(menu.pos(), !menu.isPublic()))));
        updatePublic();
    }

    private void updatePublic() {
        publicButton.setOn(menu.isPublic());
        publicButton.hint(Component.translatable(menu.isPublic() ? "gui.robotica.warp.public_on" : "gui.robotica.warp.public_off"));
    }

    private void saveName() {
        PacketDistributor.sendToServer(new WarpPayloads.Rename(menu.pos(), nameBox.getValue()));
    }

    @Override
    protected void containerTick() {
        super.containerTick();
        updatePublic();
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
    protected int titleMaxWidth() {
        return 90;
    }

    @Override
    protected void renderMachine(GuiGraphics g, int x, int y, int mouseX, int mouseY) {
        String owner = menu.ownerName().isEmpty() ? "-" : menu.ownerName();
        drawFitted(g, font, Component.literal(owner), x + imageWidth - 8, y + 6, 70, TEXT_MUTED, 1, false, 1.0F);
        addTooltip(x + imageWidth - 80, y + 4, 72, 11, Component.translatable("gui.robotica.warp.owner", owner));
        drawLabel(g, Component.translatable(menu.isPublic() ? "gui.robotica.warp.public_short" : "gui.robotica.warp.private_short"), x + 62, y + 48, 60);
        drawSlot(g, x + 31, y + 43);
        ItemStack rift = icon("rift_upgrade");
        if (menu.hasRift()) g.renderItem(rift, x + 32, y + 44);
        else drawGhost(g, rift, x + 32, y + 44);
        addTooltip(x + 31, y + 43, 18, 18, Component.translatable(menu.hasRift() ? "gui.robotica.warp.rift_on" : "gui.robotica.warp.rift_off"));
        drawEnergyBarWide(g, x + 8, y + 70, 160, 11, menu.energy(), com.arno.robotica.warp.WarpConfig.padBuffer());
    }
}
