package com.arno.robotica.architect.plan;

import com.arno.robotica.architect.style.BuildStyle;
import net.minecraft.nbt.CompoundTag;

/**
 * One queued build. The block list is regenerated from the plot record's door mask, so only the cursor is saved.
 * A patch job rebuilds an already built module (to open a door toward a new neighbour or to re-skin it).
 */
public final class BuildJob {
    public final int plot;
    public final ModuleType module;
    public final BuildStyle style;
    public final boolean clear;
    public final boolean patch;
    public boolean started;
    public int cursor;

    public BuildJob(int plot, ModuleType module, BuildStyle style, boolean clear, boolean patch) {
        this.plot = plot;
        this.module = module;
        this.style = style;
        this.clear = clear;
        this.patch = patch;
    }

    public CompoundTag save() {
        CompoundTag tag = new CompoundTag();
        tag.putInt("plot", plot);
        tag.putInt("module", module.id());
        tag.putInt("style", style.ordinal());
        tag.putBoolean("clear", clear);
        tag.putBoolean("patch", patch);
        tag.putBoolean("started", started);
        tag.putInt("cursor", cursor);
        return tag;
    }

    /** Returns null for corrupt data. */
    public static BuildJob load(CompoundTag tag) {
        ModuleType module = ModuleType.byId(tag.getInt("module"));
        int plot = tag.getInt("plot");
        if (module == null || !Plots.valid(plot)) return null;
        BuildJob job = new BuildJob(plot, module, BuildStyle.byOrdinal(tag.getInt("style")), tag.getBoolean("clear"), tag.getBoolean("patch"));
        job.started = tag.getBoolean("started");
        job.cursor = Math.max(0, tag.getInt("cursor"));
        return job;
    }
}
