package com.arno.robotica.gear;

import com.arno.robotica.Robotica;
import com.arno.robotica.core.RoboticaTab;
import com.arno.robotica.core.module.ModuleItems;
import com.arno.robotica.gear.item.UpgradeKitItem;
import com.arno.robotica.gear.tool.AreaMode;
import com.arno.robotica.gear.tool.GearEnergyToolItem;
import com.arno.robotica.gear.tool.GearToolItem;
import com.arno.robotica.gear.tool.HammerItem;
import com.arno.robotica.gear.tool.ToggleKind;
import com.arno.robotica.gear.tool.ToolSpec;
import com.arno.robotica.gear.weapon.ArcBladeItem;
import com.arno.robotica.gear.weapon.NullLanceItem;
import com.arno.robotica.gear.weapon.RivetGunItem;
import com.arno.robotica.gear.weapon.ShockBatonItem;
import net.minecraft.core.registries.Registries;
import net.minecraft.tags.BlockTags;
import net.minecraft.tags.TagKey;
import net.minecraft.world.entity.EquipmentSlotGroup;
import net.minecraft.world.entity.ai.attributes.AttributeModifier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.item.DiggerItem;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.Rarity;
import net.minecraft.world.item.SwordItem;
import net.minecraft.world.item.Tiers;
import net.minecraft.world.item.component.ItemAttributeModifiers;
import net.neoforged.neoforge.registries.DeferredItem;
import net.neoforged.neoforge.registries.DeferredRegister;

import java.util.ArrayList;
import java.util.List;
import java.util.function.UnaryOperator;

/** Tools, weapons and upgrade kits (module items: core ModuleItems). Recipes: data/robotica/recipe (scripts/data/gear_recipes.py). */
public final class GearItems {
    private GearItems() {}

    public static final DeferredRegister.Items ITEMS = DeferredRegister.createItems(Robotica.MODID);
    private static final List<DeferredItem<? extends Item>> TAB_ORDER = new ArrayList<>();

    public static final TagKey<net.minecraft.world.item.Item> VOIDABLE =
            TagKey.create(Registries.ITEM, Robotica.id("voidable"));

    // Age 0: durability tools without modules (modules start with the FE tools)
    public static final DeferredItem<HammerItem> TINKERS_HAMMER = tool("tinkers_hammer",
            ToolSpec.builder(Tiers.STONE, 5.0F).tags(BlockTags.MINEABLE_WITH_PICKAXE, BlockTags.MINEABLE_WITH_SHOVEL)
                    .modes(AreaMode.SINGLE, AreaMode.AREA_3).defaultMode(AreaMode.AREA_3)
                    .toggles(ToggleKind.KEEP_FLOOR).areaSpeed(0.5F).age(0).build(),
            p -> p.durability(600), 5.0F, -3.0F, HammerItem::new);

    public static final DeferredItem<GearToolItem> FELLING_AXE = tool("felling_axe",
            ToolSpec.builder(Tiers.STONE, 5.0F).tags(BlockTags.MINEABLE_WITH_AXE)
                    .modes(AreaMode.TREE, AreaMode.SINGLE).maxLogs(64).replants().age(0).build(),
            p -> p.durability(500), 6.0F, -3.1F, GearToolItem::new);

    // Age 1
    public static final DeferredItem<GearEnergyToolItem> BORE_DRILL = tool("bore_drill",
            ToolSpec.builder(Tiers.IRON, 6.0F).tags(BlockTags.MINEABLE_WITH_PICKAXE, BlockTags.MINEABLE_WITH_SHOVEL)
                    .modes(AreaMode.SINGLE, AreaMode.AREA_3).defaultMode(AreaMode.AREA_3).areaSpeed(0.5F)
                    .energy(400_000, () -> GearConfig.fe(GearConfig.BORE_DRILL_COST, 40))
                    .toggles(ToggleKind.KEEP_FLOOR).age(1).build(),
            p -> p, 3.0F, -2.8F, GearEnergyToolItem::new);

    public static final DeferredItem<GearEnergyToolItem> CHAINSAW = tool("chainsaw",
            ToolSpec.builder(Tiers.IRON, 9.0F).tags(BlockTags.MINEABLE_WITH_AXE)
                    .modes(AreaMode.TREE, AreaMode.SINGLE).maxLogs(256).cutsLeaves().replants()
                    .energy(400_000, () -> GearConfig.fe(GearConfig.CHAINSAW_COST, 30)).age(1).build(),
            p -> p, 6.0F, -3.0F, GearEnergyToolItem::new);

    // Age 2
    public static final DeferredItem<GearEnergyToolItem> SERVO_DRILL = tool("servo_drill",
            ToolSpec.builder(Tiers.DIAMOND, 10.0F).tags(BlockTags.MINEABLE_WITH_PICKAXE, BlockTags.MINEABLE_WITH_SHOVEL)
                    .modes(AreaMode.SINGLE, AreaMode.AREA_3, AreaMode.AREA_5, AreaMode.VEIN).defaultMode(AreaMode.AREA_3).areaSpeed(0.7F)
                    .energy(2_000_000, () -> GearConfig.fe(GearConfig.SERVO_DRILL_COST, 50))
                    .toggles(ToggleKind.KEEP_FLOOR).age(2).build(),
            p -> p.rarity(Rarity.UNCOMMON), 3.5F, -2.8F, GearEnergyToolItem::new);

    // Age 3
    public static final DeferredItem<GearEnergyToolItem> MAGMA_DRILL = tool("magma_drill",
            ToolSpec.builder(Tiers.DIAMOND, 12.0F).tags(BlockTags.MINEABLE_WITH_PICKAXE, BlockTags.MINEABLE_WITH_SHOVEL)
                    .modes(AreaMode.SINGLE, AreaMode.AREA_3, AreaMode.AREA_5, AreaMode.CUBE_3, AreaMode.AREA_9, AreaMode.VEIN).defaultMode(AreaMode.AREA_3).areaSpeed(0.85F)
                    .energy(8_000_000, () -> GearConfig.fe(GearConfig.MAGMA_DRILL_COST, 60))
                    .toggles(ToggleKind.KEEP_FLOOR, ToggleKind.AUTO_SMELT).age(3).build(),
            p -> p.rarity(Rarity.RARE).fireResistant(), 4.0F, -2.8F, GearEnergyToolItem::new);

    // Age 4
    public static final DeferredItem<GearEnergyToolItem> NULL_DRILL = tool("null_drill",
            ToolSpec.builder(Tiers.NETHERITE, 15.0F).tags(BlockTags.MINEABLE_WITH_PICKAXE, BlockTags.MINEABLE_WITH_SHOVEL)
                    .modes(AreaMode.SINGLE, AreaMode.AREA_3, AreaMode.AREA_5, AreaMode.CUBE_3, AreaMode.AREA_9,
                            AreaMode.CUBE_5, AreaMode.AREA_12, AreaMode.CUBE_12, AreaMode.VEIN).defaultMode(AreaMode.AREA_3)
                    .energy(32_000_000, () -> GearConfig.fe(GearConfig.NULL_DRILL_COST, 80))
                    .toggles(ToggleKind.KEEP_FLOOR, ToggleKind.AUTO_SMELT).age(4).build(),
            p -> p.rarity(Rarity.EPIC).fireResistant(), 5.0F, -2.8F, GearEnergyToolItem::new);

    // Weapons
    public static final DeferredItem<SwordItem> GEARBLADE = ITEMS.registerItem("gearblade",
            p -> new SwordItem(Tiers.WOOD, p.durability(400).attributes(SwordItem.createAttributes(Tiers.WOOD, 5.0F, -2.0F))));
    public static final DeferredItem<ShockBatonItem> SHOCK_BATON = ITEMS.registerItem("shock_baton",
            p -> new ShockBatonItem(p.rarity(Rarity.COMMON).attributes(weaponAttributes(7.0, 1.8)), 200_000,
                    () -> GearConfig.fe(GearConfig.SHOCK_BATON_COST, 250)));
    public static final DeferredItem<RivetGunItem> RIVET_GUN = ITEMS.registerItem("rivet_gun",
            p -> new RivetGunItem(p.rarity(Rarity.UNCOMMON).attributes(weaponAttributes(3.0, 1.6)), 1_000_000,
                    () -> GearConfig.fe(GearConfig.RIVET_GUN_COST, 400)));
    public static final DeferredItem<ArcBladeItem> ARC_BLADE = ITEMS.registerItem("arc_blade",
            p -> new ArcBladeItem(p.rarity(Rarity.RARE).fireResistant().attributes(weaponAttributes(ArcBladeItem.BASE_DAMAGE, 1.6)), 4_000_000,
                    () -> GearConfig.fe(GearConfig.ARC_BLADE_COST, 800)));
    public static final DeferredItem<NullLanceItem> NULL_LANCE = ITEMS.registerItem("null_lance",
            p -> new NullLanceItem(p.rarity(Rarity.EPIC).fireResistant().attributes(weaponAttributes(6.0, 1.2)), 16_000_000,
                    () -> GearConfig.fe(GearConfig.NULL_LANCE_COST, 8_000)));

    // Smithing templates
    public static final DeferredItem<UpgradeKitItem> KIT_1 = kit(1, Rarity.COMMON);
    public static final DeferredItem<UpgradeKitItem> KIT_2 = kit(2, Rarity.UNCOMMON);
    public static final DeferredItem<UpgradeKitItem> KIT_3 = kit(3, Rarity.RARE);
    public static final DeferredItem<UpgradeKitItem> KIT_4 = kit(4, Rarity.EPIC);

    static {
        for (DeferredItem<? extends Item> item : List.of(TINKERS_HAMMER, FELLING_AXE, BORE_DRILL, CHAINSAW, SERVO_DRILL, MAGMA_DRILL,
                NULL_DRILL, GEARBLADE, SHOCK_BATON, RIVET_GUN, ARC_BLADE, NULL_LANCE, KIT_1, KIT_2, KIT_3, KIT_4)) {
            TAB_ORDER.add(item);
        }
    }

    /** Attack damage and speed as the player sees them (base damage is 1, base attack speed 4). */
    public static ItemAttributeModifiers weaponAttributes(double damage, double speed) {
        return ItemAttributeModifiers.builder()
                .add(Attributes.ATTACK_DAMAGE, new AttributeModifier(Item.BASE_ATTACK_DAMAGE_ID, damage - 1.0, AttributeModifier.Operation.ADD_VALUE),
                        EquipmentSlotGroup.MAINHAND)
                .add(Attributes.ATTACK_SPEED, new AttributeModifier(Item.BASE_ATTACK_SPEED_ID, speed - 4.0, AttributeModifier.Operation.ADD_VALUE),
                        EquipmentSlotGroup.MAINHAND)
                .build();
    }

    private interface ToolFactory<T extends GearToolItem> {
        T create(Item.Properties props, ToolSpec spec);
    }

    private static <T extends GearToolItem> DeferredItem<T> tool(String name, ToolSpec spec, UnaryOperator<Item.Properties> props,
                                                                  float damage, float speedModifier, ToolFactory<T> factory) {
        return ITEMS.registerItem(name, p -> factory.create(
                props.apply(p).attributes(DiggerItem.createAttributes(Tiers.WOOD, damage - 1.0F, speedModifier)), spec));
    }

    private static DeferredItem<UpgradeKitItem> kit(int age, Rarity rarity) {
        return ITEMS.registerItem("tool_upgrade_kit_" + age, p -> new UpgradeKitItem(p.rarity(rarity), age));
    }

    public static void addToTab() {
        TAB_ORDER.forEach(RoboticaTab::add);
        ModuleItems.addToTab(kind -> !kind.armorOnly());
    }
}
