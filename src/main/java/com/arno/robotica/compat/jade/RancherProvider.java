package com.arno.robotica.compat.jade;

import com.arno.robotica.Robotica;
import com.arno.robotica.automation.rancher.Rancher;
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

/** Status, herd size, tier, energy bar and owner of the Rancher. */
enum RancherProvider implements IEntityComponentProvider, IServerDataProvider<EntityAccessor> {
    INSTANCE;

    static final ResourceLocation UID = Robotica.id("rancher");
    private static final int ENERGY_COLOR = 0xFF5CC8D8;

    @Override
    public ResourceLocation getUid() {
        return UID;
    }

    @Override
    public void appendServerData(CompoundTag data, EntityAccessor accessor) {
        if (!(accessor.getEntity() instanceof Rancher r)) return;
        data.putInt("robotica_energy", r.getEnergy());
        data.putInt("robotica_capacity", r.getEnergyCapacity());
        data.putByte("robotica_tier", (byte) r.tier());
        data.putString("robotica_status", r.status().key());
        data.putInt("robotica_adults", r.lastAdults());
        data.putInt("robotica_target", r.target());
        String owner = r.currentOwnerName();
        if (owner != null && !owner.isEmpty()) data.putString("robotica_owner", owner);
    }

    @Override
    public void appendTooltip(ITooltip tooltip, EntityAccessor accessor, IPluginConfig config) {
        CompoundTag data = accessor.getServerData();
        if (!data.contains("robotica_capacity")) return;
        tooltip.add(Component.translatable("jade.robotica.status", Component.translatable(data.getString("robotica_status"))));
        tooltip.add(Component.translatable("jade.robotica.herd", data.getInt("robotica_adults"), data.getInt("robotica_target")));
        tooltip.add(Component.translatable("jade.robotica.tier", data.getByte("robotica_tier")));
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
