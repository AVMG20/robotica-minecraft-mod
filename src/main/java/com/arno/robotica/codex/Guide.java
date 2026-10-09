package com.arno.robotica.codex;

import com.arno.robotica.Robotica;
import net.minecraft.ChatFormatting;
import net.minecraft.advancements.AdvancementHolder;
import net.minecraft.advancements.AdvancementNode;
import net.minecraft.advancements.DisplayInfo;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.neoforge.event.entity.player.AdvancementEvent;
import net.neoforged.neoforge.event.entity.player.PlayerEvent;
import net.neoforged.neoforge.network.PacketDistributor;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

/**
 * Server side of the guide (advancements {@code robotica:guide/*}, written by scripts/data/codex_guide.py): tells the
 * client which steps are done (for the Codex "Next steps" chapter) and, when a step is done, says in chat what comes
 * next. The steps also unlock the next recipes in the vanilla recipe book (advancement rewards).
 */
public final class Guide {
    private Guide() {}

    public static final String PREFIX = "guide/";

    /** Step order of the guide: tips name the next steps in this order. Unknown steps sort last. */
    static final List<String> ORDER = List.of("root", "copper_gear", "robot_built", "mainspring", "first_robot", "robot_working", "hammer",
            "first_iron", "generator", "charger", "copper_cell", "tesla", "power_tool", "basic_circuit", "metal_press",
            "upgrade_card", "farm_kit", "warp", "diamonds", "excavator", "survey_rig", "storage", "foundry", "age2", "colossus", "servo_core", "servo_drill", "vial",
            "replicator", "age3", "cinder_forge", "tyrant", "magma_core", "magma_drill", "age4", "antigrav_core", "null_drill", "portal");

    public static boolean isGuide(ResourceLocation id) {
        return Robotica.MODID.equals(id.getNamespace()) && id.getPath().startsWith(PREFIX);
    }

    private static int order(ResourceLocation id) {
        int i = ORDER.indexOf(id.getPath().substring(PREFIX.length()));
        return i < 0 ? Integer.MAX_VALUE : i;
    }

    /** Sends the player's finished guide steps to their client. */
    public static void sync(ServerPlayer player) {
        sync(player, false);
    }

    /** Same; {@code earned}: a step was just finished, so the client chimes. */
    private static void sync(ServerPlayer player, boolean earned) {
        MinecraftServer server = player.getServer();
        if (server == null) return;
        List<String> done = new ArrayList<>();
        for (AdvancementHolder holder : server.getAdvancements().getAllAdvancements()) {
            if (isGuide(holder.id()) && player.getAdvancements().getOrStartProgress(holder).isDone()) done.add(holder.id().toString());
        }
        // Fake and mock players (game tests, other mods) have no real connection or no Robotica channel.
        if (player instanceof net.neoforged.neoforge.common.util.FakePlayer || player.connection == null
                || !player.connection.hasChannel(GuideProgressPayload.TYPE)) return;
        PacketDistributor.sendToPlayer(player, new GuideProgressPayload(done, earned));
    }

    static void onLogin(PlayerEvent.PlayerLoggedInEvent event) {
        if (event.getEntity() instanceof ServerPlayer player) sync(player);
    }

    static void onEarn(AdvancementEvent.AdvancementEarnEvent event) {
        if (!(event.getEntity() instanceof ServerPlayer player)) return;
        AdvancementHolder earned = event.getAdvancement();
        if (!isGuide(earned.id())) return;
        sync(player, true);
        if (!CodexModule.guideTips()) return;
        MinecraftServer server = player.getServer();
        if (server == null) return;
        AdvancementNode node = server.getAdvancements().tree().get(earned.id());
        if (node == null) return;
        List<AdvancementNode> next = new ArrayList<>();
        for (AdvancementNode child : node.children()) {
            if (child.holder().value().display().isPresent() && !player.getAdvancements().getOrStartProgress(child.holder()).isDone()) {
                next.add(child);
            }
        }
        next.sort(Comparator.comparingInt(n -> order(n.holder().id())));
        for (int i = 0; i < Math.min(2, next.size()); i++) {
            DisplayInfo display = next.get(i).holder().value().display().get();
            player.sendSystemMessage(Component.translatable("message.robotica.guide.next",
                    display.getTitle().copy().withStyle(ChatFormatting.GOLD),
                    display.getDescription().copy().withStyle(ChatFormatting.GRAY)).withStyle(ChatFormatting.DARK_AQUA));
        }
    }
}
