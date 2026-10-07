package com.arno.robotica.gear.client;

import com.arno.robotica.core.client.FitButton;
import com.arno.robotica.core.client.MachineScreen;
import com.arno.robotica.core.module.ModuleKind;
import com.arno.robotica.core.module.ModuleText;
import com.arno.robotica.core.module.Modules;
import com.arno.robotica.gear.tool.GearActions;
import com.arno.robotica.gear.tool.GearToolItem;
import com.arno.robotica.gear.tool.ToggleKind;
import com.arno.robotica.gear.tool.ToolSettings;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.CommonComponents;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.ItemStack;

import java.util.ArrayList;
import java.util.List;
import java.util.function.BooleanSupplier;
import java.util.function.Supplier;

/** Small screen with one button per setting and installed module of the tool or FE weapon in the main hand. Every click is a server request. */
public class ToggleScreen extends Screen {
    private static final int WIDTH = 190;
    private static final int HEIGHT = 20;
    private static final int GAP = 2;

    private record Row(Button button, Supplier<Component> label, BooleanSupplier enabled) {}

    private final List<Row> rows = new ArrayList<>();
    private int panelX, panelY, panelW, panelH;

    public ToggleScreen() {
        super(Component.translatable("gear.robotica.screen.title"));
    }

    private static ItemStack held() {
        var player = Minecraft.getInstance().player;
        return player == null ? ItemStack.EMPTY : player.getMainHandItem();
    }

    @Override
    protected void init() {
        rows.clear();
        ItemStack stack = held();
        if (!usable(stack)) {
            onClose();
            return;
        }
        List<Supplier<Component>> labels = new ArrayList<>();
        List<Runnable> actions = new ArrayList<>();
        List<Component> hints = new ArrayList<>();
        List<BooleanSupplier> enabled = new ArrayList<>();
        if (stack.getItem() instanceof GearToolItem tool) toolRows(tool, labels, actions, hints, enabled);
        for (ModuleKind kind : ModuleKind.values()) {
            int level = Modules.level(stack, kind);
            if (level <= 0) continue;
            labels.add(() -> Component.empty().append(kind.displayName(Modules.level(held(), kind))).append(": ")
                    .append(Component.translatable(Modules.enabled(held(), kind) ? "gear.robotica.on" : "gear.robotica.off")));
            actions.add(() -> GearKeys.send(GearActions.MODULE_TOGGLE, kind.ordinal()));
            hints.add(ModuleText.describe(kind, level));
            enabled.add(() -> Modules.level(held(), kind) > 0);
        }
        if (labels.isEmpty()) {
            labels.add(() -> Component.translatable("gear.robotica.screen.no_modules"));
            actions.add(() -> {});
            hints.add(Component.translatable("gear.robotica.screen.no_modules.desc", Modules.slots(stack)));
            enabled.add(() -> false);
        }
        int total = labels.size() * (HEIGHT + GAP) - GAP;
        panelW = WIDTH + 24;
        panelH = 30 + total + 12 + HEIGHT + 12;
        panelX = (width - panelW) / 2;
        panelY = (height - panelH) / 2;
        int x = panelX + 12;
        int y = panelY + 26;
        for (int i = 0; i < labels.size(); i++) {
            Runnable action = actions.get(i);
            Button b = new FitButton(x, y + i * (HEIGHT + GAP), WIDTH, HEIGHT, labels.get(i).get(), btn -> action.run());
            b.setTooltip(net.minecraft.client.gui.components.Tooltip.create(hints.get(i)));
            addRenderableWidget(b);
            b.active = enabled.get(i).getAsBoolean();
            rows.add(new Row(b, labels.get(i), enabled.get(i)));
        }
        addRenderableWidget(new FitButton(x, y + total + 12, WIDTH, HEIGHT, CommonComponents.GUI_DONE, btn -> onClose()));
    }

    /** Tools with settings and FE weapons (they have module slots). */
    static boolean usable(ItemStack stack) {
        return stack.getItem() instanceof GearToolItem || (Modules.accepts(stack) && !Modules.target(stack).isArmor());
    }

    private void toolRows(GearToolItem tool, List<Supplier<Component>> labels, List<Runnable> actions, List<Component> hints,
                          List<BooleanSupplier> enabled) {
        if (tool.spec.modes.size() > 1) {
            labels.add(() -> Component.translatable("gear.robotica.screen.mode", tool.mode(held()).displayName()));
            actions.add(() -> GearKeys.send(GearActions.CYCLE_MODE, hasShiftDown() ? -1 : 1));
            hints.add(Component.translatable("gear.robotica.screen.mode.desc"));
            enabled.add(() -> true);
        }
        for (ToggleKind kind : ToggleKind.values()) {
            if (!tool.spec.toggles.contains(kind)) continue;
            labels.add(() -> Component.empty().append(kind.displayName()).append(": ")
                    .append(Component.translatable(ToolSettings.has(held(), kind) ? "gear.robotica.on" : "gear.robotica.off")));
            actions.add(() -> GearKeys.send(GearActions.TOGGLE, kind.ordinal()));
            hints.add(kind.description());
            enabled.add(() -> true);
        }
    }

    @Override
    public void tick() {
        if (!usable(held())) {
            onClose();
            return;
        }
        for (Row row : rows) {
            row.button.setMessage(row.label.get());
            row.button.active = row.enabled.getAsBoolean();
        }
    }

    @Override
    public void renderBackground(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        super.renderBackground(graphics, mouseX, mouseY, partialTick);
        MachineScreen.drawPanel(graphics, panelX, panelY, panelW, panelH);
        MachineScreen.drawFitted(graphics, font, title, width / 2, panelY + 10, panelW - 16, MachineScreen.TEXT, 0, false, 1.0F);
    }

    @Override
    public boolean isPauseScreen() {
        return false;
    }

    @Override
    public boolean keyPressed(int keyCode, int scanCode, int modifiers) {
        if (GearKeys.OPEN_TOGGLES.matches(keyCode, scanCode)) {
            onClose();
            return true;
        }
        return super.keyPressed(keyCode, scanCode, modifiers);
    }
}
