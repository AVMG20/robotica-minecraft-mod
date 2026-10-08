package com.arno.robotica.codex.client.dev.trailer;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.phys.Vec3;

import java.util.List;

/** Trailer scenes about the robots (Stumpy, Sprout, ...). Dev-only. */
final class TrailerScenesRobots {
    private TrailerScenesRobots() {}

    static List<TrailerScene> scenes() {
        return List.of(stumpyForest());
    }

    /** A powered Stumpy felling the oak grove around it, a Sprout harvesting wheat next to it. */
    private static TrailerScene stumpyForest() {
        return TrailerScene.builder("stumpy_forest")
                .site(44, 36)
                .time(4000)
                .warmup(30)
                .duration(160)
                .setup((level, player, site) -> {
                    // three oaks north of Stumpy inside its 7x7 area (felled west to east), three behind as backdrop
                    int[][] trees = {{-3, -3}, {3, -3}, {0, -2}, {-8, -9}, {7, -10}, {-12, -3}};
                    for (int[] t : trees) {
                        Trailer.run(level, "place feature minecraft:oak " + (site.getX() + t[0]) + " " + site.getY() + " " + (site.getZ() + t[1]));
                    }
                    Trailer.place(level, site, Trailer.block("stumpy"), Direction.SOUTH);
                    Trailer.fillEnergy(level, site);
                    // Sprout with a small wheat farm in front of it
                    BlockPos sprout = site.offset(9, 0, 0);
                    Trailer.place(level, sprout, Trailer.block("sprout"), Direction.SOUTH);
                    Trailer.fillEnergy(level, sprout);
                    for (int x = 6; x <= 11; x++) {
                        for (int z = 1; z <= 4; z++) {
                            BlockPos p = site.offset(x, -1, z);
                            boolean water = x == 8 && z == 2;
                            level.setBlock(p, water ? Blocks.WATER.defaultBlockState() : Blocks.FARMLAND.defaultBlockState(), 3);
                            if (!water) {
                                level.setBlock(p.above(), Blocks.WHEAT.defaultBlockState().setValue(BlockStateProperties.AGE_7, 7), 3);
                            }
                        }
                    }
                })
                .camera(site -> CameraPath.orbit(Vec3.atCenterOf(site), 8, 1.3, 1.2, 140, 220, 160, 60))
                .build();
    }
}
