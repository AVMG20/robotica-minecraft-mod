package com.arno.robotica.gear.tool;

import net.minecraft.tags.BlockTags;
import net.minecraft.tags.TagKey;
import net.minecraft.world.item.Tier;
import net.minecraft.world.item.component.Tool;
import net.minecraft.world.level.block.Block;

import java.util.ArrayList;
import java.util.EnumSet;
import java.util.List;
import java.util.Set;
import java.util.function.IntSupplier;

/** Static description of a mining tool: harvest tier, effective block tags, modes, toggles and costs. */
public final class ToolSpec {
    public final Tier tier;
    public final float speed;
    public final List<TagKey<Block>> tags;
    public final List<AreaMode> modes;
    /** Mode of a new tool: the reason you crafted it (3x3 for drills and the hammer, whole tree for axes). */
    public final AreaMode defaultMode;
    /** Tree tools: also clear the natural leaves of a felled tree. */
    public final boolean cutsLeaves;
    /** Tree tools: plant a sapling from the inventory where the trunk stood. */
    public final boolean replants;
    /** 0 = durability based (vanilla damage), otherwise FE capacity. */
    public final int energyCapacity;
    public final IntSupplier costPerBlock;
    public final int maxLogs;
    public final Set<ToggleKind> toggles;
    /** Fortune level used by the silk/fortune swap, 0 = no swap. */
    public final int fortuneLevel;
    /**
     * Mining speed factor in box modes (3x3 and up). Early tools pay for the area with speed: the hammer and Bore Drill
     * dig a 3x3 at half speed, which is still four times faster than nine single blocks. Later drills lose less.
     */
    public final float areaSpeed;
    public final int age;

    private ToolSpec(Builder b) {
        this.tier = b.tier;
        this.speed = b.speed;
        this.tags = List.copyOf(b.tags);
        this.modes = List.copyOf(b.modes);
        this.defaultMode = b.defaultMode != null && b.modes.contains(b.defaultMode) ? b.defaultMode : b.modes.get(0);
        this.cutsLeaves = b.cutsLeaves;
        this.replants = b.replants;
        this.energyCapacity = b.energyCapacity;
        this.costPerBlock = b.cost;
        this.maxLogs = b.maxLogs;
        this.toggles = b.toggles.isEmpty() ? EnumSet.noneOf(ToggleKind.class) : EnumSet.copyOf(b.toggles);
        this.fortuneLevel = b.fortuneLevel;
        this.areaSpeed = b.areaSpeed;
        this.age = b.age;
    }

    public boolean isEnergy() {
        return energyCapacity > 0;
    }

    public boolean hasMode(AreaMode mode) {
        return modes.contains(mode);
    }

    /** True when the tool has more than plain 1x1 mining (an area, vein or tree mode). */
    public boolean hasAreaModes() {
        return modes.size() > 1 || modes.get(0) != AreaMode.SINGLE;
    }

    public boolean isAxe() {
        return tags.contains(BlockTags.MINEABLE_WITH_AXE);
    }

    /** Vanilla Tool component: tier speed on the tagged blocks, tier harvest level, no vanilla durability use for FE tools. */
    public Tool toolComponent() {
        List<Tool.Rule> rules = new ArrayList<>();
        rules.add(Tool.Rule.deniesDrops(tier.getIncorrectBlocksForDrops()));
        for (TagKey<Block> tag : tags) {
            rules.add(Tool.Rule.minesAndDrops(tag, speed));
        }
        return new Tool(rules, 1.0F, isEnergy() ? 0 : 1);
    }

    public static Builder builder(Tier tier, float speed) {
        return new Builder(tier, speed);
    }

    public static final class Builder {
        private final Tier tier;
        private final float speed;
        private final List<TagKey<Block>> tags = new ArrayList<>();
        private final List<AreaMode> modes = new ArrayList<>(List.of(AreaMode.SINGLE));
        private AreaMode defaultMode;
        private boolean cutsLeaves;
        private boolean replants;
        private int energyCapacity;
        private IntSupplier cost = () -> 0;
        private int maxLogs = 64;
        private final Set<ToggleKind> toggles = EnumSet.noneOf(ToggleKind.class);
        private int fortuneLevel;
        private float areaSpeed = 1.0F;
        private int age;

        private Builder(Tier tier, float speed) {
            this.tier = tier;
            this.speed = speed;
        }

        @SafeVarargs
        public final Builder tags(TagKey<Block>... t) {
            tags.addAll(List.of(t));
            return this;
        }

        public Builder modes(AreaMode... m) {
            modes.clear();
            modes.addAll(List.of(m));
            return this;
        }

        public Builder defaultMode(AreaMode m) {
            this.defaultMode = m;
            return this;
        }

        public Builder cutsLeaves() {
            this.cutsLeaves = true;
            return this;
        }

        public Builder replants() {
            this.replants = true;
            return this;
        }

        public Builder energy(int capacity, IntSupplier costPerBlock) {
            this.energyCapacity = capacity;
            this.cost = costPerBlock;
            return this;
        }

        public Builder maxLogs(int n) {
            this.maxLogs = n;
            return this;
        }

        public Builder toggles(ToggleKind... t) {
            toggles.addAll(List.of(t));
            return this;
        }

        public Builder fortune(int level) {
            this.fortuneLevel = level;
            return this;
        }

        public Builder areaSpeed(float factor) {
            this.areaSpeed = factor;
            return this;
        }

        public Builder age(int age) {
            this.age = age;
            return this;
        }

        public ToolSpec build() {
            if (tags.isEmpty()) throw new IllegalStateException("tool needs at least one effective tag");
            return new ToolSpec(this);
        }
    }
}
