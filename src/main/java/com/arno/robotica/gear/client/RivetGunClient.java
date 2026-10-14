package com.arno.robotica.gear.client;

import com.arno.robotica.gear.GearConfig;
import com.arno.robotica.gear.GearItems;
import com.arno.robotica.gear.tool.GearActions;
import com.arno.robotica.gear.weapon.RivetGunItem;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.math.Axis;
import net.minecraft.Util;
import net.minecraft.client.Minecraft;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.HumanoidArm;
import net.minecraft.world.item.ItemStack;
import net.neoforged.neoforge.client.event.ClientTickEvent;
import net.neoforged.neoforge.client.event.InputEvent;
import net.neoforged.neoforge.client.extensions.common.IClientItemExtensions;
import net.neoforged.neoforge.client.extensions.common.RegisterClientExtensionsEvent;

/**
 * Rivet Gun on the client: the attack key fires it (hold to keep firing) instead of swinging, mining or hitting, and in
 * first person the gun kicks back and up on each shot instead of swinging.
 */
final class RivetGunClient {
    private RivetGunClient() {}

    private static final long RECOIL_MS = 220;
    private static long lastShot;

    /** Same hand position as a plain held item, plus the recoil of the last own shot; never the swing. */
    private static final IClientItemExtensions EXTENSIONS = new IClientItemExtensions() {
        @Override
        public boolean applyForgeHandTransform(PoseStack pose, LocalPlayer player, HumanoidArm arm, ItemStack stack,
                                               float partialTick, float equipProcess, float swingProcess) {
            int side = arm == HumanoidArm.RIGHT ? 1 : -1;
            pose.translate(side * 0.56F, -0.52F + equipProcess * -0.6F, -0.72F);
            float kick = arm == player.getMainArm() ? recoil() : 0.0F;
            if (kick > 0.0F) {
                pose.translate(0.0F, 0.04F * kick, 0.14F * kick);
                pose.mulPose(Axis.XP.rotationDegrees(12.0F * kick));
            }
            return true;
        }
    };

    static void register(RegisterClientExtensionsEvent event) {
        event.registerItem(EXTENSIONS, GearItems.RIVET_GUN.get());
    }

    /** 1 right after a shot, easing out to 0. */
    private static float recoil() {
        float t = (Util.getMillis() - lastShot) / (float) RECOIL_MS;
        if (t < 0.0F || t >= 1.0F) return 0.0F;
        return (1.0F - t) * (1.0F - t);
    }

    /** A click on air, a block or a mob: no swing and no vanilla attack, fire instead. */
    static void onClick(InputEvent.InteractionKeyMappingTriggered event) {
        LocalPlayer player = Minecraft.getInstance().player;
        if (!event.isAttack() || event.getHand() != InteractionHand.MAIN_HAND || player == null || !held(player)) return;
        event.setCanceled(true);
        event.setSwingHand(false);
        tryFire(player);
    }

    /** Holding the attack key keeps firing at the gun's rate. */
    static void onClientTick(ClientTickEvent.Post event) {
        Minecraft mc = Minecraft.getInstance();
        LocalPlayer player = mc.player;
        if (player == null || mc.screen != null || !mc.mouseHandler.isMouseGrabbed() || !mc.options.keyAttack.isDown()) return;
        if (held(player)) tryFire(player);
    }

    private static boolean held(LocalPlayer player) {
        return player.getMainHandItem().getItem() instanceof RivetGunItem;
    }

    private static void tryFire(LocalPlayer player) {
        ItemStack gun = player.getMainHandItem();
        RivetGunItem item = (RivetGunItem) gun.getItem();
        if (player.isSpectator() || player.isUsingItem() || player.isHandsBusy() || player.getCooldowns().isOnCooldown(item)) return;
        boolean loaded = item.hasCharge(gun) || player.getAbilities().instabuild;
        player.getCooldowns().addCooldown(item, loaded ? GearConfig.rivetCooldown() : RivetGunItem.EMPTY_TICKS);
        if (loaded) lastShot = Util.getMillis();
        GearKeys.send(GearActions.FIRE, 0);
    }
}
