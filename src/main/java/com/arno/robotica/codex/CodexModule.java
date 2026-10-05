package com.arno.robotica.codex;

import com.arno.robotica.Robotica;
import com.arno.robotica.core.RoboticaTab;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Rarity;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.fml.ModContainer;
import net.neoforged.fml.config.ModConfig;
import net.neoforged.neoforge.common.ModConfigSpec;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.event.RegisterCommandsEvent;
import net.neoforged.neoforge.event.entity.player.PlayerEvent;
import net.neoforged.neoforge.network.event.RegisterPayloadHandlersEvent;
import net.neoforged.neoforge.registries.DeferredItem;
import net.neoforged.neoforge.registries.DeferredRegister;

/** The Robotica Codex (guide book every player gets once) and the OP-only Creative Lab. */
public final class CodexModule {
    private CodexModule() {}

    public static final DeferredRegister.Items ITEMS = DeferredRegister.createItems(Robotica.MODID);
    public static final DeferredItem<CodexItem> CODEX = ITEMS.registerItem("codex",
            p -> new CodexItem(p.stacksTo(1).rarity(Rarity.UNCOMMON)));

    public static final ModConfigSpec SPEC;
    public static final ModConfigSpec.BooleanValue GIVE_ON_FIRST_JOIN;
    public static final ModConfigSpec.BooleanValue LAB_ENABLED;
    public static final ModConfigSpec.BooleanValue GUIDE_TIPS;

    static {
        ModConfigSpec.Builder b = new ModConfigSpec.Builder();
        GIVE_ON_FIRST_JOIN = b.comment("Give every player a Robotica Codex the first time they join this world.")
                .define("giveCodexOnFirstJoin", true);
        LAB_ENABLED = b.comment("Allow operators (permission level 2) to use the Creative Lab page and /robotica test commands.")
                .define("creativeLabEnabled", true);
        GUIDE_TIPS = b.comment("When a player finishes a step of the Robotica guide (advancements), say in chat what to do next.")
                .define("guideChatTips", true);
        SPEC = b.build();
    }

    private static final String GIVEN_TAG = "robotica_codex_given";

    public static void init(IEventBus modBus, ModContainer container) {
        ITEMS.register(modBus);
        RoboticaTab.add(CODEX);
        container.registerConfig(ModConfig.Type.SERVER, SPEC, "robotica-codex-server.toml");
        modBus.addListener(CodexModule::registerPayloads);
        NeoForge.EVENT_BUS.addListener(CodexModule::onLogin);
        NeoForge.EVENT_BUS.addListener(Guide::onLogin);
        NeoForge.EVENT_BUS.addListener(Guide::onEarn);
        NeoForge.EVENT_BUS.addListener((RegisterCommandsEvent e) -> RoboticaCommands.register(e.getDispatcher()));
    }

    private static void registerPayloads(RegisterPayloadHandlersEvent event) {
        var registrar = event.registrar("1");
        registrar.playToServer(LabActionPayload.TYPE, LabActionPayload.STREAM_CODEC, LabActionPayload::handle);
        registrar.playToClient(GuideProgressPayload.TYPE, GuideProgressPayload.STREAM_CODEC, GuideProgressPayload::handle);
    }

    public static boolean labEnabled() {
        return !SPEC.isLoaded() || LAB_ENABLED.get();
    }

    public static boolean guideTips() {
        return !SPEC.isLoaded() || GUIDE_TIPS.get();
    }

    public static boolean canUseLab(Player player) {
        return labEnabled() && player.hasPermissions(2);
    }

    private static void onLogin(PlayerEvent.PlayerLoggedInEvent event) {
        if (!(event.getEntity() instanceof ServerPlayer player)) return;
        if (SPEC.isLoaded() && !GIVE_ON_FIRST_JOIN.get()) return;
        CompoundTag persisted = player.getPersistentData().getCompound(Player.PERSISTED_NBT_TAG);
        if (persisted.getBoolean(GIVEN_TAG)) return;
        persisted.putBoolean(GIVEN_TAG, true);
        player.getPersistentData().put(Player.PERSISTED_NBT_TAG, persisted);
        ItemStack book = new ItemStack(CODEX.get());
        if (!player.getInventory().add(book)) player.drop(book, false);
    }
}
