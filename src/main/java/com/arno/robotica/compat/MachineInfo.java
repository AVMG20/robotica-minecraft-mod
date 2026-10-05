package com.arno.robotica.compat;

import net.minecraft.nbt.CompoundTag;
import org.jetbrains.annotations.Nullable;

/**
 * Small, side-neutral summary of a Robotica machine for overlay mods (Jade). Built on the server, sent as a tiny tag.
 * No mod API is referenced here, so it is safe to load with or without the integrations installed.
 */
public final class MachineInfo {
    /** Spring value of a Winding Crank without a Mainspring. */
    public static final int SPRING_NONE = -2;

    /** Suffix of {@code gui.robotica.status.<status>}: idle, working, no_energy, output_full, finished. */
    @Nullable
    public String status;
    /** Percent 0-100, -1 when the machine has no finite job. */
    public int progress = -1;
    /** Mk level, 0 when the machine has no tiers. */
    public int tier;
    @Nullable
    public String owner;
    /** Percent of the wound Mainspring, -1 not applicable, {@link #SPRING_NONE} when empty. */
    public int spring = -1;
    public int oresLeft = -1;
    public int oresTotal = -1;

    public boolean isEmpty() {
        return status == null && progress < 0 && tier <= 0 && owner == null && spring == -1 && oresTotal < 0;
    }

    public void write(CompoundTag tag) {
        if (status != null) tag.putString("st", status);
        if (progress >= 0) tag.putByte("pr", (byte) Math.min(100, progress));
        if (tier > 0) tag.putByte("mk", (byte) tier);
        if (owner != null && !owner.isEmpty()) tag.putString("ow", owner);
        if (spring != -1) tag.putByte("sp", (byte) spring);
        if (oresTotal >= 0) {
            tag.putInt("ol", oresLeft);
            tag.putInt("ot", oresTotal);
        }
    }

    public static MachineInfo read(CompoundTag tag) {
        MachineInfo info = new MachineInfo();
        if (tag.contains("st")) info.status = tag.getString("st");
        if (tag.contains("pr")) info.progress = tag.getByte("pr");
        info.tier = tag.getByte("mk");
        if (tag.contains("ow")) info.owner = tag.getString("ow");
        if (tag.contains("sp")) info.spring = tag.getByte("sp");
        if (tag.contains("ot")) {
            info.oresLeft = tag.getInt("ol");
            info.oresTotal = tag.getInt("ot");
        }
        return info;
    }
}
