package com.arno.robotica.compat.jade;

import com.arno.robotica.Robotica;
import com.arno.robotica.automation.farmbot.FarmBot;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import snownee.jade.api.EntityAccessor;
import snownee.jade.api.IEntityComponentProvider;
import snownee.jade.api.IServerDataProvider;
import snownee.jade.api.ITooltip;
import snownee.jade.api.config.IPluginConfig;
import snownee.jade.api.ui.BoxStyle;
import snownee.jade.api.ui.IElementHelper;

/** Status, Mk and area, energy bar and owner of Stumpy and Sprout. */
enum FarmBotProvider implements IEntityComponentProvider, IServerDataProvider<EntityAccessor> {
    INSTANCE;

    static final ResourceLocation UID = Robotica.id("farm_bot");
    private static final int ENERGY_COLOR = 0xFF5CC8D8;

    @Override
    public ResourceLocation getUid() {
        return UID;
    }

    @Override
    public void appendServerData(CompoundTag data, EntityAccessor accessor) {
        if (!(accessor.getEntity() instanceof FarmBot bot)) return;
        data.putInt("robotica_energy", bot.energy.getEnergyStored());
        data.putInt("robotica_capacity", bot.energy.getMaxEnergyStored());
        data.putString("robotica_status", bot.status().key());
        String owner = bot.currentOwnerName();
        if (owner != null && !owner.isEmpty()) data.putString("robotica_owner", owner);
    }

    @Override
    public void appendTooltip(ITooltip tooltip, EntityAccessor accessor, IPluginConfig config) {
        if (!(accessor.getEntity() instanceof FarmBot bot)) return;
        CompoundTag data = accessor.getServerData();
        if (!data.contains("robotica_capacity")) return;
        tooltip.add(Component.translatable("jade.robotica.status", Component.translatable(data.getString("robotica_status"))));
        int size = bot.areaSize();
        tooltip.add(Component.translatable("gui.robotica.tier_area", bot.tier(), size, size));
        int capacity = data.getInt("robotica_capacity");
        if (capacity > 0) {
            int energy = data.getInt("robotica_energy");
            IElementHelper helper = IElementHelper.get();
            tooltip.add(helper.progress((float) energy / capacity,
                    Component.translatable("jade.robotica.energy", format(energy), format(capacity)),
                    helper.progressStyle().color(ENERGY_COLOR), BoxStyle.getNestedBox(), true));
        }
        if (data.contains("robotica_owner")) tooltip.add(Component.translatable("jade.robotica.owner", data.getString("robotica_owner")));
    }

    private static String format(int fe) {
        return fe >= 10_000 ? (fe / 1000) + "k" : String.valueOf(fe);
    }
}
