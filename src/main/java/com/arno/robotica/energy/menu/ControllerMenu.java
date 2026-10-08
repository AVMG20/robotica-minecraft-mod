package com.arno.robotica.energy.menu;

import com.arno.robotica.core.menu.MachineMenu;
import com.arno.robotica.energy.block.StructureControllerBlockEntity;
import com.arno.robotica.energy.net.ControllerSyncPayload;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.ContainerLevelAccess;
import net.minecraft.world.inventory.MenuType;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.levelgen.structure.BoundingBox;
import net.neoforged.neoforge.network.PacketDistributor;
import org.jetbrains.annotations.Nullable;

/**
 * Menu base of the energy controllers. Besides the usual slots, the server sends the controller's numbers as one small
 * tag ({@link StructureControllerBlockEntity#writeSync}) to the viewing player a few times a second, only when they
 * changed. That carries longs, the structure problem text and the box, which 16 bit data slots can not.
 */
public abstract class ControllerMenu extends MachineMenu {
    private static final int SYNC_INTERVAL = 5;
    private static final double MAX_DISTANCE_SQR = 16 * 16;

    private final BlockPos pos;
    private final ContainerLevelAccess access;
    private final Block block;
    @Nullable
    private final StructureControllerBlockEntity be;
    @Nullable
    private final ServerPlayer viewer;
    private final HolderLookup.Provider registries;
    private CompoundTag data = new CompoundTag();
    @Nullable
    private CompoundTag lastSent;
    private int ticks;

    protected ControllerMenu(MenuType<?> type, int id, Inventory inv, BlockPos pos, @Nullable StructureControllerBlockEntity be) {
        super(type, id);
        this.pos = pos;
        this.access = ContainerLevelAccess.create(inv.player.level(), pos);
        this.block = inv.player.level().getBlockState(pos).getBlock();
        this.be = be;
        this.viewer = inv.player instanceof ServerPlayer sp ? sp : null;
        this.registries = inv.player.level().registryAccess();
    }

    public BlockPos pos() {
        return pos;
    }

    @Nullable
    public StructureControllerBlockEntity blockEntity() {
        return be;
    }

    @Override
    public void broadcastChanges() {
        super.broadcastChanges();
        if (be == null || viewer == null || ticks++ % SYNC_INTERVAL != 0) return;
        CompoundTag tag = new CompoundTag();
        be.writeSync(tag, registries);
        tag.putBoolean("control", be.canControl(viewer));
        if (!tag.equals(lastSent)) {
            lastSent = tag;
            PacketDistributor.sendToPlayer(viewer, new ControllerSyncPayload(containerId, tag));
        }
    }

    /** Client: the latest numbers from the server. */
    public void receive(CompoundTag tag) {
        data = tag;
    }

    public CompoundTag data() {
        return data;
    }

    // ---- common readers (client) ----

    /** Whether this player may change the controller (owner, team or operator); others only watch. */
    public boolean canControl() {
        return data.getBoolean("control");
    }

    public boolean formed() {
        return data.getBoolean("formed");
    }

    @Nullable
    public Component problem() {
        if (!data.contains("problem")) return null;
        try {
            return Component.Serializer.fromJson(data.getString("problem"), registries);
        } catch (RuntimeException e) {
            return null;
        }
    }

    @Nullable
    public BlockPos problemPos() {
        return data.contains("problemPos") ? BlockPos.of(data.getLong("problemPos")) : null;
    }

    @Nullable
    public BoundingBox box() {
        int[] b = data.getIntArray("box");
        return b.length == 6 ? new BoundingBox(b[0], b[1], b[2], b[3], b[4], b[5]) : null;
    }

    /** The smallest legal shape behind the controller, for the preview outline when no box was found yet. */
    public BoundingBox previewBox(BlockState controllerState) {
        Direction out = controllerState.hasProperty(com.arno.robotica.energy.block.ControllerBlock.FACING)
                ? controllerState.getValue(com.arno.robotica.energy.block.ControllerBlock.FACING) : Direction.NORTH;
        int w = Math.max(3, data.getInt("minW")), h = Math.max(3, data.getInt("minH"));
        Direction right = out.getClockWise();
        BlockPos a = pos.relative(right.getOpposite(), (w - 1) / 2).below((h - 1) / 2);
        BlockPos b = pos.relative(right, w / 2).above(h / 2).relative(out.getOpposite(), w - 1);
        return BoundingBox.fromCorners(a, b);
    }

    /** Players may open it from any port of the structure, so the reach is wider than a chest's: 16 blocks. */
    @Override
    public boolean stillValid(Player player) {
        return access.evaluate((level, p) -> level.getBlockState(p).is(block) && player.distanceToSqr(p.getCenter()) <= MAX_DISTANCE_SQR, true);
    }
}
