package com.arno.robotica.codex.client.dev.trailer;

import com.arno.robotica.architect.block.ArchitectTableBlockEntity;
import com.arno.robotica.architect.matter.Matter;
import com.arno.robotica.architect.plan.Layout;
import com.arno.robotica.architect.plan.Plots;
import com.arno.robotica.architect.style.BuildStyle;
import com.arno.robotica.automation.block.FarmBotBlock;
import com.arno.robotica.automation.entity.AreaWorkerBlockEntity;
import com.arno.robotica.automation.entity.FarmBotBlockEntity;
import com.arno.robotica.automation.rancher.Rancher;
import com.arno.robotica.automation.rancher.RancherContent;
import com.arno.robotica.core.item.CoreItems;
import com.arno.robotica.core.upgrade.UpgradeKind;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.Container;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.animal.Sheep;
import net.minecraft.world.item.DyeColor;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.phys.Vec3;

import java.util.List;

/** Trailer scenes about the robots (Stumpy, Sprout, Rancher, Architect, Excavator). Dev-only. */
final class TrailerScenesRobots {
    private TrailerScenesRobots() {}

    static List<TrailerScene> scenes() {
        return List.of(stumpyForest(), sproutFarm(), rancherPen(), architectBuild(), excavatorDig());
    }

    // ------------------------------------------------------------------ helpers

    /** Fills the FE buffer of an area worker (Stumpy, Sprout, Excavator) directly. */
    private static void charge(ServerLevel level, BlockPos pos) {
        if (level.getBlockEntity(pos) instanceof AreaWorkerBlockEntity worker) worker.energy.setEnergy(worker.energy.getMaxEnergyStored());
    }

    /** Puts speed cards (and so on) into a worker's first card slots. */
    private static void cards(ServerLevel level, BlockPos pos, ItemStack... stacks) {
        if (!(level.getBlockEntity(pos) instanceof AreaWorkerBlockEntity worker)) return;
        for (int i = 0; i < stacks.length; i++) worker.upgrades.setStackInSlot(i, stacks[i]);
    }

    private static Vec3 v(BlockPos site, double x, double y, double z) {
        return new Vec3(site.getX() + 0.5 + x, site.getY() + y, site.getZ() + 0.5 + z);
    }

    /**
     * Orbit with a crane move: radius, camera height and look height ease from the first to the second value while the
     * camera yaw goes from {@code yaw0} to {@code yaw1}. Heights are relative to the site.
     */
    private static CameraPath crane(BlockPos site, double cx, double cz, double r0, double r1, double h0, double h1,
                                    double look0, double look1, double yaw0, double yaw1, int ticks, double fov0, double fov1) {
        CameraPath.Builder b = CameraPath.builder();
        int keys = Math.max(4, ticks / 8);
        for (int i = 0; i <= keys; i++) {
            double u = i / (double) keys, e = u * u * (3 - 2 * u);
            double yaw = Math.toRadians(yaw0 + (yaw1 - yaw0) * u), r = r0 + (r1 - r0) * e;
            Vec3 pos = v(site, cx + r * Math.sin(yaw), h0 + (h1 - h0) * e, cz - r * Math.cos(yaw));
            b.keyLookAt(u * ticks, pos, v(site, cx, look0 + (look1 - look0) * e, cz), fov0 + (fov1 - fov0) * e);
        }
        return b.build();
    }

    /** A mob with AI, persistent, placed on the ground at (x, z). */
    private static <T extends Mob> T spawn(ServerLevel level, EntityType<T> type, BlockPos site, double x, double z, float yaw) {
        T mob = type.create(level);
        if (mob == null) return null;
        mob.moveTo(site.getX() + 0.5 + x, site.getY(), site.getZ() + 0.5 + z, yaw, 0);
        mob.setYHeadRot(yaw);
        mob.setPersistenceRequired();
        level.addFreshEntity(mob);
        return mob;
    }

    // ------------------------------------------------------------------ scenes

    /** A powered Stumpy felling the oak grove around it. The camera pushes in low, looking up at Stumpy and the trees. */
    private static TrailerScene stumpyForest() {
        return TrailerScene.builder("stumpy_forest")
                .site(44, 36)
                .time(4000)
                .warmup(30)
                .duration(140)
                .setup((level, player, site) -> {
                    // three oaks north of Stumpy inside its 7x7 area (felled west to east), more behind as backdrop
                    int[][] trees = {{-3, -3}, {3, -3}, {-3, 0}, {-8, -9}, {7, -10}, {-12, -4}, {11, -5}, {8, 1}};
                    for (int[] t : trees) {
                        Trailer.run(level, "place feature minecraft:oak " + (site.getX() + t[0]) + " " + site.getY() + " " + (site.getZ() + t[1]));
                    }
                    Trailer.place(level, site, Trailer.block("stumpy"), Direction.SOUTH);
                    cards(level, site, CoreItems.cards(UpgradeKind.SPEED, 2));
                })
                // the energy arrives with the first recorded frame, so the first tree comes down early in the shot
                .at(0, (level, player, site) -> charge(level, site))
                .camera(site -> CameraPath.builder()
                        .keyLookAt(0, v(site, 0.8, 0.75, 4.8), v(site, -0.2, 1.7, -2.0), 58)
                        .keyLookAt(70, v(site, 0.3, 0.8, 3.9), v(site, -0.5, 2.0, -2.0), 56)
                        .keyLookAt(140, v(site, -0.3, 0.85, 3.0), v(site, -0.6, 2.3, -2.0), 54)
                        .easeInOut()
                        .build())
                .build();
    }

    /** A Mk3 Sprout harvesting and replanting a field of ripe wheat and carrots; a low camera slides along the edge. */
    private static TrailerScene sproutFarm() {
        return TrailerScene.builder("sprout_farm")
                .site(44, 36)
                .time(3500)
                .warmup(30)
                .duration(150)
                .setup((level, player, site) -> {
                    BlockState tier3 = Trailer.block("sprout").defaultBlockState()
                            .setValue(BlockStateProperties.HORIZONTAL_FACING, Direction.SOUTH).setValue(FarmBotBlock.TIER, 3);
                    level.setBlock(site, tier3, 3);
                    if (level.getBlockEntity(site) instanceof FarmBotBlockEntity bot) bot.onTierChanged();
                    // a ripe field around Sprout: wheat and carrots in rows, a water channel through the middle
                    for (int x = -6; x <= 6; x++) {
                        for (int z = 1; z <= 7; z++) {
                            BlockPos ground = site.offset(x, -1, z);
                            if (z == 1) {
                                // a path row in front of Sprout, so the robot stands clear of the crops
                                level.setBlock(ground, Blocks.DIRT_PATH.defaultBlockState(), 3);
                                continue;
                            }
                            boolean water = z == 5 && x % 6 != 0 || x == 3 && z > 5;
                            level.setBlock(ground, water ? Blocks.WATER.defaultBlockState() : Blocks.FARMLAND.defaultBlockState()
                                    .setValue(net.minecraft.world.level.block.FarmBlock.MOISTURE, 7), 3);
                            if (!water) {
                                BlockState crop = (z < 5 ? Blocks.WHEAT : Blocks.CARROTS).defaultBlockState().setValue(BlockStateProperties.AGE_7, 7);
                                level.setBlock(ground.above(), crop, 3);
                            }
                        }
                    }
                })
                .at(0, (level, player, site) -> charge(level, site))
                .camera(site -> CameraPath.builder()
                        .keyLookAt(0, v(site, -3.6, 1.7, 4.4), v(site, -0.3, 0.8, 1.2), 56)
                        .keyLookAt(75, v(site, 0.0, 1.6, 4.0), v(site, 0.0, 0.8, 1.0), 54)
                        .keyLookAt(150, v(site, 3.6, 1.7, 4.4), v(site, 0.3, 0.8, 1.2), 56)
                        .easeInOut()
                        .build())
                .build();
    }

    /** Two Ranchers working a fenced pen: shearing, milking and feeding sheep, cows, pigs and chickens. */
    private static TrailerScene rancherPen() {
        return TrailerScene.builder("rancher_pen")
                .site(40, 40)
                .time(2500)
                .warmup(40)
                .duration(150)
                .setup((level, player, site) -> {
                    for (int x = -5; x <= 5; x++) {
                        for (int z = -5; z <= 5; z++) {
                            if (Math.abs(x) == 5 || Math.abs(z) == 5) {
                                level.setBlock(site.offset(x, 0, z), (x == 0 && z == 5 ? Blocks.OAK_FENCE_GATE : Blocks.OAK_FENCE).defaultBlockState(), 3);
                            }
                        }
                    }
                    BlockPos chest = site.offset(2, 0, -2);
                    level.setBlock(chest, Blocks.CHEST.defaultBlockState(), 3);
                    if (level.getBlockEntity(chest) instanceof Container c) {
                        c.setItem(0, new ItemStack(Items.WHEAT, 64));
                        c.setItem(1, new ItemStack(Items.CARROT, 64));
                        c.setItem(2, new ItemStack(Items.WHEAT_SEEDS, 64));
                        c.setItem(3, new ItemStack(Items.BUCKET, 12));
                    }
                    level.setBlock(site.offset(-4, 0, -4), Blocks.HAY_BLOCK.defaultBlockState(), 3);
                    level.setBlock(site.offset(-4, 1, -4), Blocks.HAY_BLOCK.defaultBlockState(), 3);
                    level.setBlock(site.offset(4, 0, -4), Blocks.WATER_CAULDRON.defaultBlockState(), 3);
                    level.setBlock(site.offset(4, 0, 4), Blocks.COMPOSTER.defaultBlockState(), 3);

                    double[][] cows = {{-2.5, 2.0}, {2.5, -2.0}};
                    for (double[] c : cows) spawn(level, EntityType.COW, site, c[0], c[1], (float) (c[0] * 40));
                    double[][] sheep = {{1.0, 2.5}, {-1.0, 3.2}, {3.0, 0.5}, {-3.5, -1.0}, {0.5, -3.0}};
                    int i = 0;
                    for (double[] s : sheep) {
                        Sheep sh = spawn(level, EntityType.SHEEP, site, s[0], s[1], (float) (s[1] * 70));
                        if (sh != null && i++ == 1) sh.setColor(DyeColor.BROWN);
                    }
                    spawn(level, EntityType.PIG, site, -2.0, -0.5, 30);
                    spawn(level, EntityType.PIG, site, 3.2, 3.0, 200);
                    spawn(level, EntityType.CHICKEN, site, 1.5, 0.5, 90);
                    spawn(level, EntityType.CHICKEN, site, -0.5, -4.0, 10);
                    spawn(level, EntityType.CHICKEN, site, 2.5, 3.5, 140);

                    // Mk2 shears and milks, Mk1 feeds
                    Rancher shearer = spawn(level, RancherContent.RANCHER_ENTITY.get(), site, 0.5, 0.0, 0);
                    Rancher feeder = spawn(level, RancherContent.RANCHER_ENTITY.get(), site, -1.5, 1.0, 0);
                    if (shearer != null) {
                        shearer.setTier(2);
                        shearer.setOwner(player);
                        shearer.setHome(site);
                        shearer.setTarget(16);
                        shearer.setEnergy(shearer.getEnergyCapacity());
                    }
                    if (feeder != null) {
                        feeder.setOwner(player);
                        feeder.setHome(site);
                        feeder.setTarget(16);
                        feeder.setShear(false);
                        feeder.setMilk(false);
                        feeder.setEnergy(feeder.getEnergyCapacity());
                    }
                })
                .camera(site -> crane(site, 0, 0, 4.2, 3.4, 1.7, 1.3, 0.6, 0.6, 200, 330, 150, 64, 60))
                .build();
    }

    /** The Architect Table raising a building: the builder drone flies from block to block while the walls appear. */
    private static TrailerScene architectBuild() {
        return TrailerScene.builder("architect_build")
                .site(64, 64)
                .time(5000)
                .warmup(30)
                .duration(150)
                .setup((level, player, site) -> {
                    Trailer.place(level, site, Trailer.block("architect_table"), Direction.SOUTH);
                    if (!(level.getBlockEntity(site) instanceof ArchitectTableBlockEntity table)) return;
                    table.setMatter(new Matter(20_000, 20_000, 20_000));
                    table.energy.setEnergy(table.energy.getMaxEnergyStored());
                    table.styleSlot.setStackInSlot(0, new ItemStack(CoreItems.REINFORCED_CASING.get()));
                    table.upgrades.setStackInSlot(0, CoreItems.cards(UpgradeKind.SPEED, 5));
                    Layout layout = table.layout();
                    BuildStyle style = BuildStyle.STEEL_LAB;
                    layout.queue(Plots.CENTER, style);
                    layout.queue(Plots.index(1, 0), style);
                    table.setChanged();
                })
                .at(0, (level, player, site) -> {
                    if (level.getBlockEntity(site) instanceof ArchitectTableBlockEntity table) {
                        table.handleAction(null, ArchitectTableBlockEntity.ACTION_BUILD, 0, 0);
                    }
                })
                .camera(site -> CameraPath.builder()
                        .keyLookAt(0, v(site, 8, 1.4, 13), v(site, 4, 2.8, 0), 66)
                        .keyLookAt(75, v(site, 2, 4.5, 14.5), v(site, 4, 3.2, 0), 62)
                        .keyLookAt(150, v(site, -6, 12, 14), v(site, 4, 2.2, 0), 58)
                        .easeInOut()
                        .build())
                .build();
    }

    /** A Mk4 Excavator chewing down through real terrain; the camera cranes over the pit looking in. */
    private static TrailerScene excavatorDig() {
        return TrailerScene.builder("excavator_dig")
                .site(44, 44)
                .time(5500)
                .warmup(30)
                .duration(150)
                .setup((level, player, site) -> {
                    Trailer.place(level, site, Trailer.block("excavator_mk4"), Direction.SOUTH);
                    // already dug a few layers so the pit has depth when the shot starts
                    if (level.getBlockEntity(site) instanceof com.arno.robotica.automation.entity.ExcavatorBlockEntity ex) {
                        ex.setSizeOverride(8);
                    }
                    for (int y = 1; y <= 5; y++) {
                        for (int x = -4; x <= 3; x++) {
                            for (int z = -4; z <= 3; z++) level.setBlock(site.offset(x, -y, z), Blocks.AIR.defaultBlockState(), 2);
                        }
                    }
                    cards(level, site, CoreItems.cards(UpgradeKind.SPEED, 8), CoreItems.cards(UpgradeKind.VOID, 1));
                    charge(level, site);
                })
                .at(0, (level, player, site) -> charge(level, site))
                .camera(site -> crane(site, -0.5, -0.5, 6.0, 3.6, 2.5, 6.5, -2.5, -4.5, 35, 150, 150, 68, 72))
                .build();
    }
}
