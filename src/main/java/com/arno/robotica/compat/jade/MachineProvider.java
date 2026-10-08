package com.arno.robotica.compat.jade;

import com.arno.robotica.Robotica;
import com.arno.robotica.compat.MachineInfo;
import com.arno.robotica.compat.MachineInfoCollector;
import net.minecraft.ChatFormatting;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.block.entity.BlockEntity;
import snownee.jade.api.BlockAccessor;
import snownee.jade.api.IBlockComponentProvider;
import snownee.jade.api.IServerDataProvider;
import snownee.jade.api.ITooltip;
import snownee.jade.api.config.IPluginConfig;
import snownee.jade.api.ui.BoxStyle;
import snownee.jade.api.ui.IElementHelper;

/**
 * Status, progress, Mk tier, owner, spring charge, last ore and stored experience of any Robotica block entity. The server half builds a
 * {@link MachineInfo} (a few bytes); the client half draws it. Works on dedicated servers.
 */
enum MachineProvider implements IBlockComponentProvider, IServerDataProvider<BlockAccessor> {
    INSTANCE;

    static final ResourceLocation UID = Robotica.id("machine");
    private static final String TAG = "robotica_machine";
    private static final int PROGRESS_COLOR = 0xFFD89A3A;

    @Override
    public ResourceLocation getUid() {
        return UID;
    }

    @Override
    public boolean shouldRequestData(BlockAccessor accessor) {
        return isRobotica(accessor.getBlockEntity());
    }

    @Override
    public void appendServerData(CompoundTag data, BlockAccessor accessor) {
        BlockEntity be = accessor.getBlockEntity();
        if (!isRobotica(be) || !(accessor.getLevel() instanceof ServerLevel level)) return;
        MachineInfo info = MachineInfoCollector.collect(level, be);
        if (info == null) return;
        CompoundTag tag = new CompoundTag();
        info.write(tag);
        data.put(TAG, tag);
    }

    @Override
    public void appendTooltip(ITooltip tooltip, BlockAccessor accessor, IPluginConfig config) {
        CompoundTag data = accessor.getServerData();
        if (!data.contains(TAG)) return;
        MachineInfo info = MachineInfo.read(data.getCompound(TAG));

        if (info.status != null) {
            tooltip.add(Component.translatable("jade.robotica.status",
                    Component.translatable("gui.robotica.status." + info.status).withStyle(statusColor(info.status))));
        }
        if (info.tier > 0) {
            tooltip.add(Component.translatable("jade.robotica.tier", info.tier));
        }
        if (info.progress >= 0) {
            IElementHelper helper = IElementHelper.get();
            tooltip.add(helper.progress(info.progress / 100F, Component.translatable("jade.robotica.progress", info.progress),
                    helper.progressStyle().color(PROGRESS_COLOR), BoxStyle.getNestedBox(), true));
        }
        if (info.spring == MachineInfo.SPRING_NONE) {
            tooltip.add(Component.translatable("jade.robotica.no_spring"));
        } else if (info.spring >= 0) {
            tooltip.add(Component.translatable("jade.robotica.spring", info.spring));
        }
        if (info.lastOre != null) {
            net.minecraft.resources.ResourceLocation id = net.minecraft.resources.ResourceLocation.tryParse(info.lastOre);
            if (id != null && net.minecraft.core.registries.BuiltInRegistries.ITEM.containsKey(id)) {
                tooltip.add(Component.translatable("jade.robotica.last_ore",
                        Component.translatable(net.minecraft.core.registries.BuiltInRegistries.ITEM.get(id).getDescriptionId())));
            }
        }
        if (info.xp >= 0) {
            tooltip.add(Component.translatable("jade.robotica.xp", com.arno.robotica.core.util.Fmt.compact(info.xp)));
        }
        if (info.owner != null) {
            tooltip.add(Component.translatable("jade.robotica.owner", info.owner));
        }
    }

    private static ChatFormatting statusColor(String status) {
        return switch (status) {
            case "working" -> ChatFormatting.GREEN;
            case "no_energy", "output_full" -> ChatFormatting.RED;
            default -> ChatFormatting.GRAY;
        };
    }

    private static boolean isRobotica(BlockEntity be) {
        if (be == null) return false;
        // Another mod's block entity type may be unregistered: getKey is null then.
        ResourceLocation key = BuiltInRegistries.BLOCK_ENTITY_TYPE.getKey(be.getType());
        return key != null && Robotica.MODID.equals(key.getNamespace());
    }
}
