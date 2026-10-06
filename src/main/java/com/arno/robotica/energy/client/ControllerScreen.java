package com.arno.robotica.energy.client;

import com.arno.robotica.core.client.IconButton;
import com.arno.robotica.core.client.MachineScreen;
import com.arno.robotica.energy.menu.ControllerMenu;
import net.minecraft.ChatFormatting;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.levelgen.structure.BoundingBox;

import java.util.ArrayList;
import java.util.List;

/**
 * Shared parts of the controller GUIs: the structure line (formed and its size, or the first problem with its
 * position) and the "Show" button that outlines the structure and the wrong block in the world.
 */
public abstract class ControllerScreen<M extends ControllerMenu> extends MachineScreen<M> {
    protected IconButton showButton;

    protected ControllerScreen(M menu, Inventory inv, Component title) {
        super(menu, inv, title);
    }

    /** Adds the Show button at GUI-relative x/y (18x18). */
    protected void addShowButton(int rx, int ry) {
        showButton = addRenderableWidget(new IconButton(leftPos + rx, topPos + ry, 18, new ItemStack(Items.SPYGLASS), false, b -> showStructure()));
        showButton.hint(Component.translatable("gui.robotica.energy.show_tip"));
    }

    protected void showStructure() {
        if (minecraft == null || minecraft.level == null) return;
        BoundingBox box = menu.box();
        boolean preview = box == null;
        if (preview) box = menu.previewBox(minecraft.level.getBlockState(menu.pos()));
        StructureHighlight.show(menu.pos(), box, menu.problemPos(), preview);
        if (minecraft.player != null) {
            minecraft.player.displayClientMessage(Component.translatable(preview ? "gui.robotica.energy.show_preview" : "gui.robotica.energy.show_box"), true);
        }
        onClose();
    }

    /**
     * Structure status at absolute x/y, at most {@code lines} lines inside {@code width}. Formed: green with the size;
     * not formed: red with the problem, its position in the tooltip. Returns the height used.
     */
    protected int drawStructure(GuiGraphics g, int x, int y, int width, int lines) {
        BoundingBox box = menu.box();
        if (menu.data().isEmpty()) {
            drawStatus(g, Component.translatable("gui.robotica.energy.checking"), x, y, width, 1, Tone.WARN);
            return 9;
        }
        if (menu.formed() && box != null) {
            Component size = Component.translatable("gui.robotica.energy.formed", box.getXSpan(), box.getYSpan(), box.getZSpan());
            int h = drawStatus(g, size, x, y, width, lines, Tone.GOOD);
            addTooltip(x, y - 1, width, h + 2, Component.translatable("gui.robotica.energy.formed_tip"));
            return h;
        }
        Component problem = menu.problem();
        if (problem == null) problem = Component.translatable("gui.robotica.energy.not_formed");
        int h = drawStatus(g, problem, x, y, width, lines, Tone.BAD);
        List<Component> tip = new ArrayList<>();
        tip.add(problem);
        BlockPos at = menu.problemPos();
        if (at != null) tip.add(Component.translatable("gui.robotica.energy.problem_at", at.getX(), at.getY(), at.getZ()).withStyle(ChatFormatting.GRAY));
        tip.add(Component.translatable("gui.robotica.energy.show_hint").withStyle(ChatFormatting.DARK_GRAY));
        addTooltip(x, y - 1, width, h + 2, tip.toArray(Component[]::new));
        return h;
    }

    /** Small gray text, 0.75 scale, left aligned at absolute x/y. */
    protected void small(GuiGraphics g, Component text, int x, int y, int width) {
        drawFitted(g, font, text, x, y, width, TEXT, -1, false, 0.75F);
    }

    protected void smallRight(GuiGraphics g, Component text, int rightX, int y, int width) {
        drawFitted(g, font, text, rightX, y, width, TEXT, 1, false, 0.75F);
    }
}
