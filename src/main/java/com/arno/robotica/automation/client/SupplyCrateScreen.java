package com.arno.robotica.automation.client;

import com.arno.robotica.automation.menu.SupplyCrateMenu;
import com.arno.robotica.core.client.MachineScreen;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.player.Inventory;

/** Plain chest style GUI. */
public class SupplyCrateScreen extends MachineScreen<SupplyCrateMenu> {
    public SupplyCrateScreen(SupplyCrateMenu menu, Inventory inv, Component title) {
        super(menu, inv, title);
    }
}
