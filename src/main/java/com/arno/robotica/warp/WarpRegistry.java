package com.arno.robotica.warp;

import com.arno.robotica.Robotica;
import com.arno.robotica.core.RoboticaTab;
import com.arno.robotica.warp.gate.PortalProjectorBlock;
import com.arno.robotica.warp.gate.PortalProjectorBlockEntity;
import com.arno.robotica.warp.item.LinkingCardItem;
import com.arno.robotica.warp.item.RemoteItem;
import com.arno.robotica.warp.item.RiftUpgradeItem;
import com.arno.robotica.warp.menu.DestinationMenu;
import com.arno.robotica.warp.menu.PadMenu;
import com.arno.robotica.warp.pad.WarpPadBlock;
import com.arno.robotica.warp.pad.WarpPadBlockEntity;
import net.minecraft.core.registries.Registries;
import net.minecraft.world.inventory.MenuType;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.Rarity;
import net.minecraft.world.level.block.SoundType;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.world.level.material.MapColor;
import net.minecraft.world.level.material.PushReaction;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.common.extensions.IMenuTypeExtension;
import net.neoforged.neoforge.registries.DeferredBlock;
import net.neoforged.neoforge.registries.DeferredHolder;
import net.neoforged.neoforge.registries.DeferredItem;
import net.neoforged.neoforge.registries.DeferredRegister;

/** Every registry entry of the warp module. Recipes: scripts/data/warp_data.py. */
public final class WarpRegistry {
    private WarpRegistry() {}

    public static final DeferredRegister.Blocks BLOCKS = DeferredRegister.createBlocks(Robotica.MODID);
    public static final DeferredRegister.Items ITEMS = DeferredRegister.createItems(Robotica.MODID);
    public static final DeferredRegister<BlockEntityType<?>> BLOCK_ENTITIES = DeferredRegister.create(Registries.BLOCK_ENTITY_TYPE, Robotica.MODID);
    public static final DeferredRegister<MenuType<?>> MENUS = DeferredRegister.create(Registries.MENU, Robotica.MODID);

    // ---- Blocks ----
    public static final DeferredBlock<WarpPadBlock> WARP_PAD = BLOCKS.registerBlock("warp_pad", WarpPadBlock::new,
            BlockBehaviour.Properties.of().mapColor(MapColor.COLOR_CYAN).strength(3.0F, 6.0F).sound(SoundType.METAL)
                    .requiresCorrectToolForDrops().noOcclusion().lightLevel(s -> 7).pushReaction(PushReaction.BLOCK));
    public static final DeferredBlock<PortalProjectorBlock> GATE_CONTROLLER = BLOCKS.registerBlock("gate_controller", PortalProjectorBlock::new,
            BlockBehaviour.Properties.of().mapColor(MapColor.COLOR_PURPLE).strength(5.0F, 12.0F).sound(SoundType.AMETHYST)
                    .requiresCorrectToolForDrops().noOcclusion().lightLevel(s -> s.getValue(PortalProjectorBlock.ACTIVE) ? 15 : 6).pushReaction(PushReaction.BLOCK));

    // ---- Items ----
    public static final DeferredItem<BlockItem> WARP_PAD_ITEM = ITEMS.registerSimpleBlockItem(WARP_PAD);
    public static final DeferredItem<BlockItem> GATE_CONTROLLER_ITEM = ITEMS.registerSimpleBlockItem(GATE_CONTROLLER);
    public static final DeferredItem<RiftUpgradeItem> RIFT_UPGRADE = ITEMS.registerItem("rift_upgrade",
            p -> new RiftUpgradeItem(p.rarity(Rarity.RARE)));
    public static final DeferredItem<RemoteItem> RECALL_REMOTE = ITEMS.registerItem("recall_remote",
            p -> new RemoteItem(p, false, 400_000));
    public static final DeferredItem<RemoteItem> RIFT_REMOTE = ITEMS.registerItem("rift_remote",
            p -> new RemoteItem(p.rarity(Rarity.RARE), true, 1_000_000));
    public static final DeferredItem<LinkingCardItem> LINKING_CARD = ITEMS.registerItem("linking_card",
            p -> new LinkingCardItem(p.rarity(Rarity.UNCOMMON)));

    // ---- Block entities ----
    public static final DeferredHolder<BlockEntityType<?>, BlockEntityType<WarpPadBlockEntity>> WARP_PAD_BE =
            BLOCK_ENTITIES.register("warp_pad", () -> BlockEntityType.Builder.of(WarpPadBlockEntity::new, WARP_PAD.get()).build(null));
    public static final DeferredHolder<BlockEntityType<?>, BlockEntityType<PortalProjectorBlockEntity>> GATE_CONTROLLER_BE =
            BLOCK_ENTITIES.register("gate_controller", () -> BlockEntityType.Builder.of(PortalProjectorBlockEntity::new, GATE_CONTROLLER.get()).build(null));

    // ---- Menus ----
    public static final DeferredHolder<MenuType<?>, MenuType<PadMenu>> PAD_MENU =
            MENUS.register("warp_pad", () -> IMenuTypeExtension.create((id, inv, buf) -> new PadMenu(id, inv, buf)));
    public static final DeferredHolder<MenuType<?>, MenuType<DestinationMenu>> DESTINATION_MENU =
            MENUS.register("warp_destinations", () -> IMenuTypeExtension.create((id, inv, buf) -> new DestinationMenu(id, inv, buf)));

    /** Registers everything and adds the items to the creative tab in progression order. */
    public static void register(IEventBus modBus) {
        BLOCKS.register(modBus);
        ITEMS.register(modBus);
        BLOCK_ENTITIES.register(modBus);
        MENUS.register(modBus);
        WarpComponents.REGISTER.register(modBus);

        RoboticaTab.add(RECALL_REMOTE);
        RoboticaTab.add(WARP_PAD_ITEM);
        RoboticaTab.add(RIFT_UPGRADE);
        RoboticaTab.add(RIFT_REMOTE);
        RoboticaTab.add(GATE_CONTROLLER_ITEM);
        RoboticaTab.add(LINKING_CARD);
    }
}
