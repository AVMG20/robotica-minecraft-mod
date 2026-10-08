package com.arno.robotica.compat.jade;

import com.arno.robotica.Robotica;
import com.arno.robotica.drones.entity.CourierDrone;
import com.arno.robotica.drones.entity.DroneBase;
import com.arno.robotica.drones.entity.MiningDrone;
import com.arno.robotica.drones.entity.SentryDrone;
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

import java.util.Locale;

/** Energy bar, mode, tier and owner of Robotica drones (drones have no FE capability, so Jade shows nothing by itself). */
enum DroneProvider implements IEntityComponentProvider, IServerDataProvider<EntityAccessor> {
    INSTANCE;

    static final ResourceLocation UID = Robotica.id("drone");
    private static final int ENERGY_COLOR = 0xFF5CC8D8;

    @Override
    public ResourceLocation getUid() {
        return UID;
    }

    @Override
    public void appendServerData(CompoundTag data, EntityAccessor accessor) {
        if (!(accessor.getEntity() instanceof DroneBase drone)) return;
        data.putInt("robotica_energy", drone.getEnergy());
        data.putInt("robotica_capacity", drone.getEnergyCapacity());
        data.putByte("robotica_tier", (byte) drone.tier());
        String mode = modeKey(drone);
        if (mode != null) data.putString("robotica_mode", mode);
        String owner = drone.currentOwnerName();
        if (owner != null && !owner.isEmpty()) data.putString("robotica_owner", owner);
    }

    @Override
    public void appendTooltip(ITooltip tooltip, EntityAccessor accessor, IPluginConfig config) {
        CompoundTag data = accessor.getServerData();
        if (!data.contains("robotica_capacity")) return;
        if (data.contains("robotica_mode")) {
            tooltip.add(Component.translatable("jade.robotica.mode", Component.translatable(data.getString("robotica_mode"))));
        }
        int tier = data.getByte("robotica_tier");
        if (tier > 0) tooltip.add(Component.translatable("jade.robotica.tier", tier));
        int capacity = data.getInt("robotica_capacity");
        if (capacity > 0) {
            int energy = data.getInt("robotica_energy");
            IElementHelper helper = IElementHelper.get();
            tooltip.add(helper.progress((float) energy / capacity,
                    Component.translatable("jade.robotica.energy", format(energy), format(capacity)),
                    helper.progressStyle().color(ENERGY_COLOR), BoxStyle.getNestedBox(), true));
        }
        if (data.contains("robotica_owner")) {
            tooltip.add(Component.translatable("jade.robotica.owner", data.getString("robotica_owner")));
        }
    }

    /** Translation key of what the drone is doing, null when unknown. */
    private static String modeKey(DroneBase drone) {
        if (drone instanceof MiningDrone mining) return "gui.robotica.drone.mode." + mining.mode().name().toLowerCase(Locale.ROOT);
        if (drone instanceof SentryDrone sentry) return "gui.robotica.drone.mode." + sentry.mode().name().toLowerCase(Locale.ROOT);
        if (drone instanceof CourierDrone courier) return "gui.robotica.courier.state." + courier.state().name().toLowerCase(Locale.ROOT);
        if (drone instanceof com.arno.robotica.drones.entity.HaulerDrone hauler) return hauler.isCarrying() ? "gui.robotica.hauler.carrying" : "gui.robotica.drone.mode.follow";
        return null;
    }

    private static String format(int fe) {
        return fe >= 10_000 ? (fe / 1000) + "k" : String.valueOf(fe);
    }
}
