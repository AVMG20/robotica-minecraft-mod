package com.arno.robotica.architect.plan;

import com.arno.robotica.architect.style.BuildStyle;
import net.minecraft.nbt.CompoundTag;

/** What the table knows about one plot: its module, style, door mask (set when the build starts) and progress state. */
public final class PlotRecord {
    public static final int QUEUED = 1, BUILDING = 2, BUILT = 3;

    public ModuleType module;
    public BuildStyle style;
    public int status;
    public int mask;

    public PlotRecord(ModuleType module, BuildStyle style, int status, int mask) {
        this.module = module;
        this.style = style;
        this.status = status;
        this.mask = mask;
    }

    public CompoundTag save(int plot) {
        CompoundTag tag = new CompoundTag();
        tag.putInt("plot", plot);
        tag.putInt("module", module.id());
        tag.putInt("style", style.ordinal());
        tag.putInt("status", status);
        tag.putInt("mask", mask);
        return tag;
    }

    /** Returns null for corrupt data. */
    public static PlotRecord load(CompoundTag tag) {
        ModuleType module = ModuleType.byId(tag.getInt("module"));
        if (module == null || !Plots.valid(tag.getInt("plot"))) return null;
        int status = Math.max(QUEUED, Math.min(BUILT, tag.getInt("status")));
        return new PlotRecord(module, BuildStyle.byOrdinal(tag.getInt("style")), status, tag.getInt("mask"));
    }
}
