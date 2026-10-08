package com.arno.robotica.compat;

import com.arno.robotica.architect.block.ArchitectTableBlockEntity;
import com.arno.robotica.automation.entity.AreaWorkerBlockEntity;
import com.arno.robotica.automation.entity.FarmBotBlockEntity;
import com.arno.robotica.automation.entity.SurveyRigBlockEntity;
import com.arno.robotica.core.energy.ItemEnergy;
import com.arno.robotica.power.block.CombustionGeneratorBlockEntity;
import com.arno.robotica.power.block.MetalPressBlockEntity;
import com.arno.robotica.power.block.SolarPanelBlockEntity;
import com.arno.robotica.power.block.WindingCrankBlockEntity;
import com.arno.robotica.warp.gate.PortalProjectorBlockEntity;
import com.arno.robotica.warp.pad.WarpPadBlockEntity;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import org.jetbrains.annotations.Nullable;

import java.util.Locale;

/**
 * Builds a {@link MachineInfo} from the public getters of the Robotica block entities (server side). Energy is not
 * included: overlay mods read it from the FE capability, which every Robotica machine exposes.
 * A block entity that implements {@link InfoSource} describes itself and skips the lookup below.
 */
public final class MachineInfoCollector {
    private MachineInfoCollector() {}

    /** Null when there is nothing to show. */
    @Nullable
    public static MachineInfo collect(ServerLevel level, BlockEntity be) {
        MachineInfo info = new MachineInfo();
        if (be instanceof InfoSource source) {
            source.collectInfo(level, info);
        } else {
            describe(level, be, info);
        }
        return info.isEmpty() ? null : info;
    }

    private static void describe(ServerLevel level, BlockEntity be, MachineInfo info) {
        if (be instanceof AreaWorkerBlockEntity worker) {
            info.status = worker.status().name().toLowerCase(Locale.ROOT);
            info.progress = worker.guiProgress();
            info.owner = OwnerNames.name(level.getServer(), worker.owner());
            if (worker instanceof FarmBotBlockEntity bot) info.tier = bot.tier();
            if (worker instanceof SurveyRigBlockEntity rig && rig.lastOre() != null) {
                info.lastOre = net.minecraft.core.registries.BuiltInRegistries.ITEM.getKey(rig.lastOre()).toString();
            }
        } else if (be instanceof MetalPressBlockEntity press) {
            boolean lit = press.getBlockState().hasProperty(BlockStateProperties.LIT) && press.getBlockState().getValue(BlockStateProperties.LIT);
            boolean hasInput = !press.items.getStackInSlot(0).isEmpty();
            info.status = lit ? "working" : hasInput && press.energy.getEnergyStored() <= 0 ? "no_energy" : "idle";
            if (press.needed() > 0 && press.progress() > 0) info.progress = percent(press.progress(), press.needed());
        } else if (be instanceof WindingCrankBlockEntity crank) {
            ItemStack spring = crank.spring.getStackInSlot(0);
            int capacity = ItemEnergy.capacity(spring);
            info.spring = spring.isEmpty() ? MachineInfo.SPRING_NONE : capacity <= 0 ? 0 : percent(ItemEnergy.get(spring), capacity);
        } else if (be instanceof CombustionGeneratorBlockEntity generator) {
            info.status = generator.burnTime() > 0 ? "working" : "idle";
        } else if (be instanceof com.arno.robotica.power.tesla.TeslaCoilBlockEntity coil) {
            // No "Mk" line: the block name already says the tier (Tesla Coil I-V).
            info.status = coil.isActive() ? "working" : "idle";
        } else if (be instanceof SolarPanelBlockEntity solar) {
            info.status = solar.isGenerating() ? "working" : "idle";
        } else if (be instanceof ArchitectTableBlockEntity table) {
            int st = table.status();
            info.status = st == ArchitectTableBlockEntity.ST_BUILDING ? "working" : st == ArchitectTableBlockEntity.ST_NO_ENERGY ? "no_energy" : "idle";
            if (table.isBuilding()) info.progress = table.progressPermille() / 10;
            String name = table.ownerName();
            if (!"?".equals(name)) info.owner = name;
        } else if (be instanceof WarpPadBlockEntity pad) {
            if (!pad.ownerName().isEmpty()) info.owner = pad.ownerName();
        } else if (be instanceof PortalProjectorBlockEntity projector) {
            if (!projector.ownerName().isEmpty()) info.owner = projector.ownerName();
        }
    }

    private static int percent(long value, long max) {
        if (max <= 0) return 0;
        return (int) Math.max(0, Math.min(100, 100L * value / max));
    }
}
