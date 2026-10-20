package com.arno.robotica.replicator.farm;

import com.arno.robotica.core.multiblock.CuboidSpec;
import com.arno.robotica.core.multiblock.StructureProblem;
import com.arno.robotica.energy.block.PortBlock;
import com.arno.robotica.energy.block.StructureControllerBlockEntity;
import com.arno.robotica.replicator.ReplicatorRegistry;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.network.chat.Component;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.levelgen.structure.BoundingBox;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

/**
 * The 5x5x5 shell both mob farms share: Farm Casing on the frame; casing, Farm Glass and Farm Ports in the walls; three
 * Impact Rams in the middle of three side walls; the farm's matrix in the middle of the roof; the controller anywhere in
 * a side wall; the 3x3x3 inside empty. At least one port.
 */
public final class FarmStructure {
    private FarmStructure() {}

    public static final int SIZE = 5;
    public static final int RAMS = 3;

    public static final CuboidSpec SPEC = CuboidSpec.builder(Component.translatable("multiblock.robotica.mob_farm"))
            .frame(s -> s.is(ReplicatorRegistry.FARM_CASING.get()), Component.translatable("block.robotica.farm_casing"))
            .wall(s -> s.is(ReplicatorRegistry.FARM_CASING.get()) || s.is(ReplicatorRegistry.FARM_GLASS.get())
                            || s.is(ReplicatorRegistry.FARM_PORT.get()) || s.is(ReplicatorRegistry.IMPACT_RAM.get()) || isMatrix(s),
                    Component.translatable("multiblock.robotica.farm_wall"))
            .controller(s -> s.is(ReplicatorRegistry.IRON_GOLEM_FARMER.get()) || s.is(ReplicatorRegistry.WITHER_FARMER.get()))
            .width(() -> SIZE, () -> SIZE)
            .height(() -> SIZE, () -> SIZE)
            .build();

    public static boolean isMatrix(BlockState state) {
        return state.is(ReplicatorRegistry.GOLEM_MATRIX.get()) || state.is(ReplicatorRegistry.WITHER_MATRIX.get());
    }

    /** Where a ram may sit: the middle of a side wall. */
    public static boolean isRamSpot(BoundingBox box, BlockPos pos) {
        BlockPos c = box.getCenter();
        if (pos.getY() != c.getY()) return false;
        boolean xWall = (pos.getX() == box.minX() || pos.getX() == box.maxX()) && pos.getZ() == c.getZ();
        boolean zWall = (pos.getZ() == box.minZ() || pos.getZ() == box.maxZ()) && pos.getX() == c.getX();
        return xWall || zWall;
    }

    /** Where the matrix sits: the middle of the roof. */
    public static BlockPos matrixSpot(BoundingBox box) {
        BlockPos c = box.getCenter();
        return new BlockPos(c.getX(), box.maxY(), c.getZ());
    }

    /** The direction a ram at {@code ram} pushes in: from its wall towards the middle of the box. */
    public static Direction ramPush(BoundingBox box, BlockPos ram) {
        if (ram.getX() == box.minX()) return Direction.EAST;
        if (ram.getX() == box.maxX()) return Direction.WEST;
        if (ram.getZ() == box.minZ()) return Direction.SOUTH;
        return Direction.NORTH;
    }

    /** Collects the ports, rams and matrices of the walls; the inside must be air. */
    static final class Visitor extends StructureControllerBlockEntity.Visitor {
        private final FarmKind kind;
        final List<BlockPos> rams = new ArrayList<>();
        private final List<BlockPos> matrices = new ArrayList<>();
        private final List<BlockState> matrixStates = new ArrayList<>();

        Visitor(FarmKind kind) {
            this.kind = kind;
        }

        @Override
        public StructureProblem wall(BlockPos pos, BlockState state, Direction outward) {
            super.wall(pos, state, outward);
            if (state.is(ReplicatorRegistry.IMPACT_RAM.get())) rams.add(pos);
            else if (isMatrix(state)) {
                matrices.add(pos);
                matrixStates.add(state);
            }
            return null;
        }

        @Override
        public StructureProblem interior(BlockPos pos, BlockState state, BoundingBox box) {
            if (state.isAir()) return null;
            return StructureProblem.of(pos, "multiblock.robotica.farm_inside", StructureProblem.name(state), StructureProblem.at(pos));
        }

        @Override
        public StructureProblem finish(BoundingBox box) {
            for (BlockPos ram : rams) {
                if (!isRamSpot(box, ram)) return StructureProblem.of(ram, "multiblock.robotica.farm_ram_spot", StructureProblem.at(ram));
            }
            BlockPos spot = matrixSpot(box);
            for (int i = 0; i < matrices.size(); i++) {
                BlockPos pos = matrices.get(i);
                if (!pos.equals(spot)) return StructureProblem.of(pos, "multiblock.robotica.farm_matrix_spot", StructureProblem.at(pos));
                if (!matrixStates.get(i).is(kind.matrix())) {
                    return StructureProblem.of(pos, "multiblock.robotica.farm_matrix_wrong", StructureProblem.name(matrixStates.get(i)),
                            kind.matrix().getName());
                }
            }
            if (matrices.isEmpty()) {
                return StructureProblem.of(spot, "multiblock.robotica.farm_no_matrix", kind.matrix().getName(), StructureProblem.at(spot));
            }
            if (rams.size() != RAMS) return StructureProblem.of(null, "multiblock.robotica.farm_rams", rams.size(), RAMS);
            if (ports(PortBlock.Kind.FARM) == 0) return StructureProblem.of(null, "multiblock.robotica.farm_no_port");
            // the rams strike one after the other, always in the same order
            rams.sort(Comparator.comparingLong(BlockPos::asLong));
            return null;
        }
    }
}
